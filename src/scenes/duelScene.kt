package scenes

import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.korge.view.animation.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.*
import kotlin.time.Duration.Companion.seconds

private const val CARD_W = 63
private const val CARD_H = 90
private const val CARD_STEP = 69

private const val ART_SCALE = 3.0

private const val SIDE_X = 27

private val SIDE_BUTTONS = mapOf("Descartar" to 88, "Duelo" to 114, "Nueva Partida" to 140)
private const val BUTTON_ART_X = 8
private const val BUTTON_ART_W = 43
private const val BUTTON_ART_H = 24
private const val BUTTON_CAP = 6
private const val SIDE_BUTTON_X = BUTTON_ART_X * 3
private const val DEATH_BUTTON_ART_W = 66
private const val PANEL_CAP = 13
private const val RESULT_PANEL_ART_W = 160
private const val RESULT_PANEL_ART_H = 116

private const val TABLE_X = 59 * 3
private const val TABLE_W = 261 * 3
private const val CARDS_X = TABLE_X + (TABLE_W - (6 * CARD_STEP + CARD_W)) / 2
private const val ENEMY_Y = 240

private const val ENEMY_CARD_W = 50
private const val ENEMY_CARD_H = 72
private const val ENEMY_STEP = 55
private const val ENEMY_X = TABLE_X + (TABLE_W - (6 * ENEMY_STEP + ENEMY_CARD_W)) / 2
private const val PLAYER_Y = 400

private val RIVALS = listOf("Mosquetero 1", "Cobrador de impuestos", "Princesa incognita")

private val DEATH_MOTTOS = listOf(
    "Todos para uno y nada para ti",
    "Touché",
    "Sin honor, sin cartas, sin sombrero",
    "Adieu, mosquetero",
    "La partie est finie"
)

private lateinit var pixelFont: TtfFont


private class FightResult(
    val outcome: DuelOutcome,
    val title: String,
    val scores: String,
    val note: String,
    val playerTotal: Int,
    val enemyTotal: Int
)

class DuelScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        var duel = Duel()
        var result: FightResult? = null
        var showOverlay = false

        pixelFont = resourcesVfs["PixelifySans.ttf"].readTtfFont()

        val sceneArt = resourcesVfs["escena_mosquetero.ase"]
            .readImageData(ASE.toProps().also { it.onlyReadVisibleLayers = false })
        val background = imageDataView(sceneArt, playing = true, smoothing = false) { scale(ART_SCALE) }

        fun pickRival() {
            val rival = RIVALS.random()
            RIVALS.forEach { background.getLayer(it)?.visible = it == rival }
        }
        pickRival()

        val buttonArt = SIDE_BUTTONS.mapValues { (name, artY) ->
            background.getLayer(name)?.visible = false
            val layer = sceneArt.frames.first().layerData.first { it.layer.name == name }
            layer.bitmap.sliceWithSize(BUTTON_ART_X - layer.targetX, artY - layer.targetY, BUTTON_ART_W, BUTTON_ART_H).extract()
        }

        val panelArt = sceneArt.frames.first().layerData.first { it.layer.name == "Barra Lateral" }.bitmap

        val deathArt =resourcesVfs["escena_muerte.ase"].readImageData(ASE.toProps())

        val enemyLayer = container()
        val playerLayer = container()
        val sideLayer = container()
        val overlayLayer = container()
        val fadeLayer = container()

        lateinit var fadeToDeath: suspend () -> Unit


        fun redraw() {
            enemyLayer.removeChildren()
            playerLayer.removeChildren()
            sideLayer.removeChildren()
            overlayLayer.removeChildren()


            val enemy = duel.enemyScore
            duel.enemyHand.forEachIndexed { i, card ->
                enemyLayer.cardView(
                    card, ENEMY_X + i * ENEMY_STEP, ENEMY_Y, card in enemy.scoringCards, false,
                    w = ENEMY_CARD_W, h = ENEMY_CARD_H, textSize = 22
                )
            }
            enemyLayer.plate("Rival - ${scoreText(enemy)}", ENEMY_X, ENEMY_Y + ENEMY_CARD_H + 8)


            val player = duel.playerScore()
            val beating = player.total > duel.targetScore
            duel.playerHand.forEachIndexed { i, card ->
                val selected = i in duel.selected
                playerLayer.cardView(
                    card, CARDS_X + i * CARD_STEP, if (selected) PLAYER_Y - 20 else PLAYER_Y,
                    card in player.scoringCards, selected
                ) {
                    duel.toggleSelect(i)
                    redraw()
                }
            }
            playerLayer.plate(
                "Tú - ${scoreText(player)}", CARDS_X, PLAYER_Y + CARD_H + 8,
                color = if (beating) Colors["#81c784"] else Colors.WHITE
            )


            sideLayer.pixelText("Objetivo", 16) { position(SIDE_X, 31) }
            sideLayer.pixelText("${duel.targetScore}", 33) { position(SIDE_X, 50) }
            sideLayer.pixelText("Descartes: ${duel.roundsLeft}", 16) { position(SIDE_X, 96) }
            sideLayer.pixelText("Marcadas: ${duel.selected.size}/${Duel.MAX_DISCARD_PER_ROUND}", 16) { position(SIDE_X, 115) }


            val r = result
            if (r != null) {
                sideLayer.pixelText(r.title, 22) { position(SIDE_X, 145) }
                sideLayer.pixelText("Tú: ${r.playerTotal}\nRival: ${r.enemyTotal}", 16) { position(SIDE_X, 177) }
            } else {
                sideLayer.pixelText("Verde:\nsuman", 16) { position(SIDE_X, 145) }
                sideLayer.pixelText("Amarillo:\nmarcada", 16) { position(SIDE_X, 195) }
            }

            sideLayer.artButton("Descartar", buttonArt.getValue("Descartar"), SIDE_BUTTON_X, SIDE_BUTTONS.getValue("Descartar") * 3, duel.canDiscard) {
                duel.discardSelected()
                redraw()
            }
            sideLayer.artButton("¡Duelo!", buttonArt.getValue("Duelo"), SIDE_BUTTON_X, SIDE_BUTTONS.getValue("Duelo") * 3, !duel.isFinished) {
                val fought = fight(duel)
                result = fought
                if (fought.outcome == DuelOutcome.LOSE) {
                    redraw()
                    this@DuelScene.launch { fadeToDeath() }
                } else {
                    showOverlay = true
                    redraw()
                }
            }
            sideLayer.artButton("Nueva\npartida", buttonArt.getValue("Nueva Partida"), SIDE_BUTTON_X, SIDE_BUTTONS.getValue("Nueva Partida") * 3, true) {
                duel = Duel()
                result = null
                showOverlay = false
                pickRival()
                redraw()
            }


            if (r != null && showOverlay) {
                val dim = overlayLayer.solidRect(960, 540, RGBA(0, 0, 0, 170))
                dim.onClick { }   // captura los toques para que no lleguen a lo que está debajo

                val playAgain = {
                    duel = Duel()
                    result = null
                    showOverlay = false
                    pickRival()
                    redraw()
                }
                val showHands = {
                    showOverlay = false
                    redraw()
                }

                if (r.outcome == DuelOutcome.LOSE) {
                    overlayLayer.imageDataView(deathArt, playing = true, smoothing = false) { scale(ART_SCALE) }
                    overlayLayer.pixelText(r.title, 44) { position(501, 40) }
                    overlayLayer.plate(r.scores, 507, 104, size = 22)
                    if (r.note.isNotEmpty()) overlayLayer.plate(r.note, 507, 237)
                    overlayLayer.artButton(
                        "Jugar de nuevo", buttonArt.getValue("Duelo"), 501, 147, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = playAgain
                    )
                    overlayLayer.artButton(
                        "Ver manos", buttonArt.getValue("Nueva Partida"), 711, 147, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = showHands
                    )
                } else {
                    overlayLayer.framePanel(panelArt, RESULT_PANEL_ART_W, RESULT_PANEL_ART_H).position(240, 108)


                    overlayLayer.pixelText(r.title, 44) { position((960 - width.toInt()) / 2, 132) }
                    overlayLayer.pixelText(r.scores, 22) { position((960 - width.toInt()) / 2, 198) }
                    if (r.note.isNotEmpty()) {
                        overlayLayer.pixelText(r.note, 22) { position((960 - width.toInt()) / 2, 234) }
                    }

                    overlayLayer.artButton(
                        "Continuar", buttonArt.getValue("Duelo"), 276, 318, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = playAgain
                    )
                    overlayLayer.artButton(
                        "Ver manos", buttonArt.getValue("Nueva Partida"), 486, 318, true,
                        artWidth = DEATH_BUTTON_ART_W, labelSize = 22, onTap = showHands
                    )
                }
            }
        }

        fadeToDeath = {
            val black = fadeLayer.solidRect(960, 540, Colors.BLACK) { alpha = 0.0 }
            black.onClick { }
            val motto = fadeLayer.pixelText(DEATH_MOTTOS.random(), 33) {
                position((960 - width.toInt()) / 2, (540 - height.toInt()) / 2)
                alpha = 0.0
            }
            black.tween(black::alpha[1.0], time = 1.0.seconds)
            motto.tween(motto::alpha[1.0], time = 0.8.seconds)
            delay(2200)
            motto.tween(motto::alpha[0.0], time = 0.8.seconds)
            showOverlay = true
            redraw()
            black.tween(black::alpha[0.0], time = 1.0.seconds)
            fadeLayer.removeChildren()
        }

        redraw()
    }
}


private fun Container.cardView(
    card: Card, x: Int, y: Int, scoring: Boolean, selected: Boolean,
    w: Int = CARD_W, h: Int = CARD_H, textSize: Int = 33, onTap: (() -> Unit)? = null
): Container = container {
    position(x, y)
    val bg = when {
        selected -> Colors["#ffe082"]
        scoring -> Colors["#c8e6c9"]
        else -> Colors.WHITE
    }
    solidRect(w + 4, h + 4, Colors["#2b1608"]).position(-2, -2)
    solidRect(w, h, bg)
    val ink = if (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) Colors["#c62828"] else Colors.BLACK
    pixelText(cardText(card), textSize, color = ink) { position((w - width.toInt()) / 2, (h - height.toInt()) / 2) }
    if (onTap != null) onClick { onTap() }
}

private fun Container.artButton(
    label: String, art: Bitmap, x: Int, y: Int, enabled: Boolean,
    artWidth: Int = art.width, labelSize: Int = 16, onTap: () -> Unit
): Container = container {
    position(x, y)
    container {
        val mid = art.width / 2
        image(art.sliceWithSize(0, 0, BUTTON_CAP, art.height)) { smoothing = false }
        image(art.sliceWithSize(mid, 0, 1, art.height)) {
            smoothing = false
            position(BUTTON_CAP, 0)
            scaleX = (artWidth - 2 * BUTTON_CAP).toDouble()
        }
        image(art.sliceWithSize(art.width - BUTTON_CAP, 0, BUTTON_CAP, art.height)) {
            smoothing = false
            position(artWidth - BUTTON_CAP, 0)
        }
        scale(ART_SCALE)
        if (!enabled) colorMul = Colors["#808080"]
    }
    val w = (artWidth * ART_SCALE).toInt()
    val h = (art.height * ART_SCALE).toInt()
    val lines = label.lines().map { pixelText(it, labelSize, if (enabled) Colors.WHITE else Colors["#bbbbbb"]) }
    val lineH = lines.first().height.toInt()
    val top = (h - lineH * lines.size) / 2
    lines.forEachIndexed { i, line -> line.position((w - line.width.toInt()) / 2, top + i * lineH) }
    if (enabled) onClick { onTap() }
}

private fun Container.framePanel(art: Bitmap, artW: Int, artH: Int): Container = container {
    val cols = listOf(Triple(0, PANEL_CAP, 0), Triple(art.width / 2, 1, PANEL_CAP), Triple(art.width - PANEL_CAP, PANEL_CAP, artW - PANEL_CAP))
    val rows = listOf(Triple(0, PANEL_CAP, 0), Triple(art.height / 2, 1, PANEL_CAP), Triple(art.height - PANEL_CAP, PANEL_CAP, artH - PANEL_CAP))
    for ((sx, sw, dx) in cols) for ((sy, sh, dy) in rows) {
        image(art.sliceWithSize(sx, sy, sw, sh)) {
            smoothing = false
            position(dx, dy)
            scaleX = if (sw == 1) (artW - 2 * PANEL_CAP).toDouble() else 1.0
            scaleY = if (sh == 1) (artH - 2 * PANEL_CAP).toDouble() else 1.0
        }
    }
    scale(ART_SCALE)
}

private val pixelFonts =mutableMapOf<Int, BitmapFont>()

private fun pixelFontOf(size: Int): BitmapFont = pixelFonts.getOrPut(size) {
    pixelFont.toBitmapFont(size, CharacterSet.LATIN_ALL, mipmaps = false).also { font ->
        (font.baseBmp as Bitmap32).updateColors { if (it.a >= 128) Colors.WHITE else Colors.TRANSPARENT }
    }
}

private fun Container.pixelText(
    label: String, size: Int = 16, color: RGBA = Colors.WHITE, block: Text.() -> Unit = {}
): Text = text(label, size, color, pixelFontOf(size)) {
    smoothing = false
    block()
}

private fun Container.plate(label: String, x: Int, y: Int, color: RGBA = Colors.WHITE, size: Int = 16): Container = container {
    position(x, y)
    val t = Text(label, size, color, pixelFontOf(size)).apply { smoothing = false }
    solidRect(t.width + 12, t.height + 6, RGBA(0, 0, 0, 160)).position(-6, -3)
    addChild(t)
}

private fun fight(duel: Duel): FightResult {
    val outcome = duel.fight()
    val p = duel.playerScore()
    val e = duel.enemyScore
    val title = when (outcome) {
        DuelOutcome.WIN -> "¡GANASTE!"
        DuelOutcome.LOSE -> "PERDISTE"
        DuelOutcome.DRAW -> "EMPATE"
    }
    val note = when {
        outcome == DuelOutcome.WIN && p.category < e.category ->
            "Tu mano es de categoría menor,\npero tus cartas sumaron más fichas."
        outcome == DuelOutcome.LOSE && p.category > e.category ->
            "Tu mano es de mayor categoría,\npero las cartas del rival\nsumaron más fichas."
        else -> ""
    }
    return FightResult(outcome, title, "Tú: ${p.total}   vs   Rival: ${e.total}", note, p.total, e.total)
}
