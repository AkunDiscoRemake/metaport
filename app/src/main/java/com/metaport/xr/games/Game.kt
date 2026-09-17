package com.metaport.xr.games

import com.metaport.xr.ar.HandModel
import com.metaport.xr.audio.Sfx
import com.metaport.xr.input.InputState
import com.metaport.xr.render.Pipeline
import com.metaport.xr.scene.Scene

/** Everything a game can touch. Games never hold GL state of their own context. */
class GameContext {
    var dt = 0f
    var time = 0f
    lateinit var scene: Scene
    lateinit var pipeline: Pipeline
    lateinit var input: InputState

    var leftHand: HandModel? = null
    var rightHand: HandModel? = null

    /** Head transform, updated every frame by the host. */
    val headMatrix = FloatArray(16)
    val headPos = FloatArray(3)
    val headForward = FloatArray(3)
    val headRight = FloatArray(3)
    val headUp = FloatArray(3)

    /** Play-space origin (where the user is standing), world space. */
    val playOrigin = FloatArray(3)

    /** Gaze ray. */
    val gazeOrigin = FloatArray(3)
    val gazeDir = FloatArray(3)

    var handTracking = false
    var mrMode = false

    fun sfx(type: Int, gain: Float = 1f) = Sfx.play(type, 0f, gain)

    /** True when the player pressed the trigger / pinched this frame. */
    fun firePressed(): Boolean = input.triggerPressed || input.rightPinchEdge || input.leftPinchEdge

    fun fireHeld(): Boolean = input.triggerDown || input.rightPinch || input.leftPinch

    /** Right-hand fingertip world position, or the gaze point 1 m ahead. */
    fun aimPoint(out: FloatArray, fallbackDistance: Float = 1.2f) {
        val h = rightHand?.takeIf { it.visible } ?: leftHand?.takeIf { it.visible }
        if (h != null) {
            out[0] = h.joints[HandModel.INDEX_TIP * 3]
            out[1] = h.joints[HandModel.INDEX_TIP * 3 + 1]
            out[2] = h.joints[HandModel.INDEX_TIP * 3 + 2]
        } else {
            out[0] = gazeOrigin[0] + gazeDir[0] * fallbackDistance
            out[1] = gazeOrigin[1] + gazeDir[1] * fallbackDistance
            out[2] = gazeOrigin[2] + gazeDir[2] * fallbackDistance
        }
    }
}

/**
 * A MetaPort game.
 *
 * Every title here is an original design written for this app — its own name,
 * its own rules and procedurally generated content. Nothing is copied from any
 * commercial title.
 */
interface Game {
    val id: String
    val title: String
    val tagline: String
    val category: String
    val accentIndex: Int

    /** Optional minimum tracking requirement shown in the library card. */
    val requiresHands: Boolean get() = false

    fun onEnter(ctx: GameContext)
    fun onExit(ctx: GameContext)
    fun update(ctx: GameContext)
    fun render(ctx: GameContext)

    /** Scoreboard rows shown on the in-game HUD panel. */
    fun hud(): List<Pair<String, String>> = emptyList()

    /** Short control hints shown on the pause panel. */
    fun instructions(): List<String> = emptyList()

    /** True when the round ended, so the host can show the results panel. */
    fun isGameOver(): Boolean = false

    fun score(): Int = 0

    fun restart(ctx: GameContext)
}

/** Base class with shared helpers: procedural content seeds and simple physics. */
abstract class GameBase : Game {
    protected val model = FloatArray(16)
    protected var rng = java.util.Random()

    protected fun seed(s: Long) { rng = java.util.Random(s) }

    protected fun compose(x: Float, y: Float, z: Float, s: Float = 1f, yaw: Float = 0f) {
        com.metaport.xr.core.math.Mat4.compose(
            model, x, y, z, yaw, 0f, 0f, s, s, s
        )
    }

    protected fun composeS(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, yaw: Float = 0f) {
        com.metaport.xr.core.math.Mat4.compose(model, x, y, z, yaw, 0f, 0f, sx, sy, sz)
    }

    protected fun dist(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float =
        com.metaport.xr.core.math.Vec3.dist(ax, ay, az, bx, by, bz)

    override fun restart(ctx: GameContext) {
        onExit(ctx)
        onEnter(ctx)
    }
}

/** Registry of every playable title. */
object GameRegistry {
    private val games = ArrayList<Game>()

    fun register(g: Game) { games.add(g) }

    fun all(): List<Game> = games

    fun byId(id: String): Game? = games.firstOrNull { it.id == id }

    fun categories(): List<String> = games.map { it.category }.distinct()

    fun count(): Int = games.size
}
