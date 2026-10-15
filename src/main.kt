import korlibs.image.color.*
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.math.geom.*
import scenes.DuelScene

suspend fun main() = Korge(windowSize = Size(960, 540), backgroundColor = Colors["#1b1b2f"]) {
    val sceneContainer = sceneContainer()
    sceneContainer.changeTo { DuelScene() }
}
