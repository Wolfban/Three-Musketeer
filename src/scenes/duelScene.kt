package scenes

import korlibs.image.color.*
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import model.*

private const val CARD_W = 70
private const val CARD_H = 100
private const val CARD_STEP = 76
private const val LEFT = 30
private const val RIGHT_COL = 620


private class FightResult(
    val outcome: DuelOutcome,
    val title: String,
    val scores: String,
    val note: String
)

class DuelScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        var duel = Duel()
        var result: FightResult? = null
        var showOverlay = false

        val enemyLayer = container()
        val playerLayer = container()
        val sideLayer = container()
        val overlayLayer = container()


        fun redraw() {
            enemyLayer.removeChildren()
            playerLayer.removeChildren()
            sideLayer.removeChildren()
            overlayLayer.removeChildren()


            val enemy = duel.enemyScore
            enemyLayer.text("RIVAL") { position(LEFT, 20); scale(1.3) }
            duel.enemyHand.forEachIndexed { i, card ->
                enemyLayer.cardView(card, LEFT + i * CARD_STEP, 55, card in enemy.scoringCards, false)
            }
            enemyLayer.text(scoreText(enemy)) { position(LEFT, 168) }


            val player = duel.playerScore()
            val beating = player.total > duel.targetScore
            playerLayer.text("TU MANO") { position(LEFT, 250); scale(1.3) }
            duel.playerHand.forEachIndexed { i, card ->
                val selected = i in duel.selected
                playerLayer.cardView(
                    card, LEFT + i * CARD_STEP, if (selected) 270 else 290,
                    card in player.scoringCards, selected
                ) {
                    duel.toggleSelect(i)
                    redraw()
                }
            }
            playerLayer.text(
                scoreText(player),
                color = if (beating) Colors["#81c784"] else Colors.WHITE
            ) { position(LEFT, 405) }
            playerLayer.text(
                "Descartes: ${duel.roundsLeft}   Marcadas: ${duel.selected.size}/${Duel.MAX_DISCARD_PER_ROUND}"
            ) { position(LEFT, 432) }


            sideLayer.text("Objetivo: ${duel.targetScore}") { position(RIGHT_COL, 45); scale(2.0) }
            sideLayer.text("Verde: suman puntos\nAmarillo: marcada") { position(RIGHT_COL, 105); scale(0.9) }


            val r = result
            if (r != null) {
                sideLayer.text(r.title) { position(RIGHT_COL, 150); scale(1.6) }
                sideLayer.text(r.scores) { position(RIGHT_COL, 182) }
                if (r.note.isNotEmpty()) sideLayer.text(r.note) { position(RIGHT_COL, 208) }
            }

            sideLayer.button("Descartar", RIGHT_COL, 300, 300, duel.canDiscard) {
                duel.discardSelected()
                redraw()
            }
            sideLayer.button("¡Duelo!", RIGHT_COL, 370, 300, !duel.isFinished) {
                result = fight(duel)
                showOverlay = true
                redraw()
            }
            sideLayer.button("Nueva partida", RIGHT_COL, 455, 300, true) {
                duel = Duel()
                result = null
                showOverlay = false
                redraw()
            }


            if (r != null && showOverlay) {
                val dim = overlayLayer.solidRect(960, 540, RGBA(0, 0, 0, 170))
                dim.onClick { }   // captura los toques para que no lleguen a lo que está debajo

                val panelColor = when (r.outcome) {
                    DuelOutcome.WIN -> Colors["#2e7d32"]
                    DuelOutcome.LOSE -> Colors["#b71c1c"]
                    DuelOutcome.DRAW -> Colors["#455a64"]
                }
                overlayLayer.solidRect(480, 290, panelColor).position(240, 130)


                overlayLayer.text(r.title) { position((960 - r.title.length * 26) / 2, 150); scale(3.0) }
                overlayLayer.text(r.scores) { position((960 - r.scores.length * 13) / 2, 228); scale(1.5) }
                if (r.note.isNotEmpty()) {
                    val widest = r.note.lines().maxOf { it.length }
                    overlayLayer.text(r.note) { position((960 - widest * 10) / 2, 275); scale(1.2) }
                }

                overlayLayer.button("Jugar de nuevo", 260, 345, 200, true) {
                    duel = Duel()
                    result = null
                    showOverlay = false
                    redraw()
                }
                overlayLayer.button("Ver manos", 480, 345, 200, true, color = Colors["#37474f"]) {
                    showOverlay = false
                    redraw()
                }
            }
        }

        redraw()
    }
}


private fun Container.cardView(
    card: Card, x: Int, y: Int, scoring: Boolean, selected: Boolean, onTap: (() -> Unit)? = null
): Container = container {
    position(x, y)
    val bg = when {
        selected -> Colors["#ffe082"]
        scoring -> Colors["#c8e6c9"]
        else -> Colors.WHITE
    }
    solidRect(CARD_W, CARD_H, bg)
    val ink = if (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) Colors["#c62828"] else Colors.BLACK
    text(cardText(card), color = ink) { position(10, 35); scale(1.7) }
    if (onTap != null) onClick { onTap() }
}

private fun Container.button(
    label: String, x: Int, y: Int, w: Int, enabled: Boolean,
    color: RGBA = Colors["#3949ab"], onTap: () -> Unit
): Container = container {
    position(x, y)
    solidRect(w, 56, if (enabled) color else Colors["#555555"])
    text(label) { position(14, 16); scale(1.3) }
    if (enabled) onClick { onTap() }
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
    return FightResult(outcome, title, "Tú: ${p.total}   vs   Rival: ${e.total}", note)
}
