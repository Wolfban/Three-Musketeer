package scenes

import korlibs.event.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.korge.view.animation.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val MENU_ROW_Y = 75
private const val MENU_ROW_STEP = 21
private const val MENU_LABEL_SIZE = 33
private const val MENU_BUTTON_CENTER_Y = 8
private const val MENU_HOVER_SCALE = 1.1

private class MenuRow(val box: View, val normal: View, val highlighted: View, val x: Double, val y: Double)

class MenuScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        loadPixelFont()

        val menuArt = resourcesVfs["escena_menu.ase"].readImageData(ASE.toProps())
        val background = imageDataView(menuArt, playing = true, smoothing = false) { scale(ART_SCALE) }
        background.getLayer("BtnSeleccionado")?.visible = false
        background.getLayer("Btn")?.visible = false

        val selectedArt = menuArt.frames.first().layerData.first { it.layer.name == "BtnSeleccionado" }
        val normalArt = menuArt.frames.first().layerData.first { it.layer.name == "Btn" }
        val normalOffsetY = normalArt.targetY - (MENU_ROW_Y + MENU_ROW_STEP)

        var selected = 0
        var leaving = false

        fun play() {
            if (leaving) return
            leaving = true
            views.launch {
                val black = views.stage.solidRect(960, 540, Colors.BLACK) { alpha = 0.0 }
                black.onClick { }
                black.tween(black::alpha[1.0], time = 1.0.seconds)
                sceneContainer.changeTo { DuelScene() }
                black.tween(black::alpha[0.0], time = 1.0.seconds)
                black.removeFromParent()
            }
        }

        val options = listOf<Pair<String, () -> Unit>>(
            "Jugar" to ::play,
            "Mazos" to {},
            "Salir" to { views.gameWindow.close() }
        )

        lateinit var rows: List<MenuRow>

        val buttonW = selectedArt.width * ART_SCALE
        val buttonH = selectedArt.height * ART_SCALE

        fun select(index: Int) {
            selected = index
            rows.forEachIndexed { i, row ->
                val active = i == index
                val s = if (active) MENU_HOVER_SCALE else 1.0
                row.highlighted.visible = active
                row.normal.visible = !active
                row.box.scale = s
                row.box.position(row.x - buttonW * (s - 1) / 2, row.y - buttonH * (s - 1) / 2)
            }
        }

        rows = options.mapIndexed { i, (label, action) ->
            val rowY = MENU_ROW_Y + i * MENU_ROW_STEP
            val x = selectedArt.targetX * ART_SCALE
            val y = rowY * ART_SCALE
            val box = container()
            val normal = box.image(normalArt.slice) {
                smoothing = false
                scale(ART_SCALE)
                position((normalArt.targetX - selectedArt.targetX) * ART_SCALE, normalOffsetY * ART_SCALE)
            }
            val highlighted = box.image(selectedArt.slice) {
                smoothing = false
                scale(ART_SCALE)
            }
            box.pixelText(label, MENU_LABEL_SIZE) {
                position(
                    (buttonW / 2 - width / 2).toInt(),
                    (MENU_BUTTON_CENTER_Y * ART_SCALE - height / 2).toInt()
                )
            }
            box.onOver { select(i) }
            box.onClick { action() }
            MenuRow(box, normal, highlighted, x, y)
        }

        keys {
            down(Key.UP) { select((selected + rows.size - 1) % rows.size) }
            down(Key.DOWN) { select((selected + 1) % rows.size) }
            down(Key.ENTER, Key.RETURN, Key.SPACE) { options[selected].second() }
        }

        select(0)
    }
}
