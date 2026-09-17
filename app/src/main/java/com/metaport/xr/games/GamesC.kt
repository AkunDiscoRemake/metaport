package com.metaport.xr.games

import com.metaport.xr.core.math.Mathf
import com.metaport.xr.core.math.Mat4
import com.metaport.xr.scene.Material
import com.metaport.xr.scene.Scene

// =====================================================================================
// DEEP REEL — spatial fishing
// =====================================================================================

/** Cast into a glowing pool, feel the bite, reel in. Timing based. */
class DeepReel : GameBase() {
    override val id = "deep-reel"
    override val title = "Deep Reel"
    override val tagline = "Cast for light-fish in a bioluminescent pool."
    override val category = "Relax"
    override val accentIndex = 0

    private enum class Phase { IDLE, CASTING, WAITING, BITE, REELING, CAUGHT, LOST }

    private var phase = Phase.IDLE
    private var timer = 0f
    private var tension = 0f
    private var progress = 0f
    private var catches = 0
    private var totalScore = 0
    private var hookX = 0f; private var hookY = 0.2f; private var hookZ = -3f
    private var fishX = 0f; private var fishZ = -3f
    private var castPower = 0f
    private var rarity = 1

    private val waterMat = Material().apply { blend = Material.BLEND_ALPHA; depthWrite = false; roughness = 0.05f; metallic = 0.5f }
    private val hookMat = Material().apply { emissive = 1.4f }
    private val fishMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 1.8f }
    private val lineMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true }
    private val fishies = ArrayList<FloatArray>()

    override fun onEnter(ctx: GameContext) {
        phase = Phase.IDLE
        catches = 0; totalScore = 0; tension = 0f; progress = 0f
        fishies.clear()
        for (i in 0 until 18) {
            val a = rng.nextFloat() * Mathf.PI * 2f
            val r = 1.5f + rng.nextFloat() * 7f
            fishies.add(floatArrayOf(kotlin.math.cos(a) * r, -0.15f - rng.nextFloat() * 0.35f,
                kotlin.math.sin(a) * r - 3f, a, 0.3f + rng.nextFloat() * 0.7f, rng.nextFloat() * 6.28f))
        }
    }

    override fun onExit(ctx: GameContext) { fishies.clear() }

    override fun update(ctx: GameContext) {
        for (f in fishies) {
            f[3] += ctx.dt * f[4] * 0.6f
            f[0] += kotlin.math.cos(f[3]) * ctx.dt * f[4] * 0.8f
            f[2] += kotlin.math.sin(f[3]) * ctx.dt * f[4] * 0.8f
            if (f[0] > 9f) f[0] = -9f
            if (f[0] < -9f) f[0] = 9f
            if (f[2] > 2f) f[2] = -9f
            if (f[2] < -10f) f[2] = 2f
        }

        when (phase) {
            Phase.IDLE -> {
                if (ctx.fireHeld()) castPower = (castPower + ctx.dt * 1.2f).coerceAtMost(1f)
                if (!ctx.fireHeld() && castPower > 0.05f) {
                    phase = Phase.CASTING
                    timer = 0f
                    hookX = ctx.gazeOrigin[0]
                    hookZ = ctx.gazeOrigin[2] - 1.5f
                    ctx.sfx(com.metaport.xr.audio.Sfx.WHOOSH, 0.6f)
                }
            }
            Phase.CASTING -> {
                timer += ctx.dt
                hookZ -= (6f + castPower * 8f) * ctx.dt
                hookY = 0.2f + kotlin.math.sin(timer * 3.14f) * 0.8f
                if (hookZ < ctx.gazeOrigin[2] - 2f - castPower * 8f) {
                    phase = Phase.WAITING
                    timer = 0f
                    hookY = -0.1f
                    fishX = hookX; fishZ = hookZ
                    ctx.sfx(com.metaport.xr.audio.Sfx.GRAB, 0.5f)
                }
            }
            Phase.WAITING -> {
                timer += ctx.dt
                if (timer > 1.5f + rng.nextFloat() * 2.5f) {
                    phase = Phase.BITE
                    timer = 0f
                    rarity = 1 + rng.nextInt(4)
                    ctx.sfx(com.metaport.xr.audio.Sfx.TICK, 0.9f)
                }
            }
            Phase.BITE -> {
                timer += ctx.dt
                if (ctx.firePressed()) {
                    phase = Phase.REELING
                    progress = 0f; tension = 0.5f
                    ctx.sfx(com.metaport.xr.audio.Sfx.GRAB, 0.8f)
                } else if (timer > 1.4f) {
                    phase = Phase.LOST
                    timer = 0f
                    ctx.sfx(com.metaport.xr.audio.Sfx.ERROR, 0.6f)
                }
            }
            Phase.REELING -> {
                val pull = if (ctx.fireHeld()) 1f else 0f
                tension += (pull * 1.5f - 0.55f) * ctx.dt
                tension = tension.coerceIn(0f, 1f)
                progress += (pull * 0.55f - 0.18f) * ctx.dt
                hookZ += pull * 1.6f * ctx.dt
                if (tension >= 1f) { phase = Phase.LOST; timer = 0f; ctx.sfx(com.metaport.xr.audio.Sfx.ERROR) }
                if (progress >= 1f) {
                    phase = Phase.CAUGHT
                    timer = 0f
                    catches++
                    totalScore += rarity * 180
                    ctx.sfx(com.metaport.xr.audio.Sfx.SCORE, 1f)
                }
            }
            Phase.CAUGHT, Phase.LOST -> {
                timer += ctx.dt
                if (timer > 2.2f || ctx.firePressed()) {
                    phase = Phase.IDLE
                    castPower = 0f
                    hookY = 0.2f
                }
            }
        }
    }

    override fun render(ctx: GameContext) {
        val plane = GameAssets.plane ?: return
        waterMat.rgb(0.05f, 0.25f, 0.35f, 0.72f)
        composeS(0f, -0.25f, -3f, 22f, 1f, 22f)
        ctx.scene.push(plane, waterMat, model, Scene.LAYER_TRANSPARENT)

        for (f in fishies) {
            val pulse = 0.5f + 0.5f * kotlin.math.sin(ctx.time * 2f + f[5])
            fishMat.rgb(0.2f * pulse, 0.9f * pulse, 1f * pulse, 0.75f)
            compose(f[0], f[1], f[2], 0.07f + f[4] * 0.04f)
            ctx.scene.push(GameAssets.orb, fishMat, model, Scene.LAYER_TRANSPARENT)
        }

        hookMat.rgb(1f, 0.9f, 0.4f)
        hookMat.emissive(1f, 0.8f, 0.3f, 1.6f)
        compose(hookX, hookY, hookZ, 0.09f)
        ctx.scene.push(GameAssets.orb, hookMat, model, Scene.LAYER_OPAQUE)

        if (phase == Phase.BITE) {
            fishMat.rgb(1f, 0.4f, 0.9f, 0.9f)
            compose(fishX, -0.05f, fishZ, 0.28f + kotlin.math.sin(ctx.time * 18f) * 0.05f)
            ctx.scene.push(GameAssets.orb, fishMat, model, Scene.LAYER_TRANSPARENT)
        }
        if (phase == Phase.REELING) {
            lineMat.rgb(1f - tension, tension * 0.4f + 0.4f, 0.4f, 0.85f)
            composeS((hookX + ctx.headPos[0]) * 0.5f, (hookY + ctx.headPos[1]) * 0.5f,
                (hookZ + ctx.headPos[2]) * 0.5f, 0.012f, 0.012f,
                dist(hookX, hookY, hookZ, ctx.headPos[0], ctx.headPos[1], ctx.headPos[2]))
            ctx.scene.push(GameAssets.cube, lineMat, model, Scene.LAYER_TRANSPARENT)
        }
    }

    override fun hud() = listOf(
        "Score" to totalScore.toString(),
        "Catches" to catches.toString(),
        "State" to phase.name.lowercase().replaceFirstChar { it.uppercase() },
        "Tension" to ("%.0f%%".format(tension * 100))
    )

    override fun instructions() = listOf(
        "Hold the trigger to charge the cast, release to throw",
        "When the pink orb appears, tap the trigger to hook",
        "Reel with short holds — keep tension below 100%"
    )

    override fun score() = totalScore
}

// =====================================================================================
// GRAVITY LEAP — hand locomotion climb
// =====================================================================================

/**
 * Grab holds and pull yourself up a vertical shaft. Uses both tracked hands when
 * available; otherwise gaze + trigger grabs the hold you are looking at.
 */
class GravityLeap : GameBase() {
    override val id = "gravity-leap"
    override val title = "Gravity Leap"
    override val tagline = "Pull yourself up an endless neon shaft."
    override val category = "Movement"
    override val accentIndex = 2
    override val requiresHands = true

    private class Hold {
        var x = 0f; var y = 0f; var z = 0f
        var grabbed = 0
    }

    private val holds = ArrayList<Hold>()
    private var climbY = 0f
    private var bestY = 0f
    private var grabbedLeft = -1
    private var grabbedRight = -1
    private var prevHandL = FloatArray(3)
    private var prevHandR = FloatArray(3)
    private var over = false
    private var falling = 0f

    private val holdMat = Material().apply { emissive = 0.9f; roughness = 0.3f }
    private val shaftMat = Material().apply { blend = Material.BLEND_ALPHA; depthWrite = false }
    private val sparkMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 2f }

    override fun onEnter(ctx: GameContext) {
        holds.clear()
        climbY = 0f; bestY = 0f; over = false; falling = 0f
        grabbedLeft = -1; grabbedRight = -1
        for (i in 0 until 40) addHold(i * 1.05f)
    }

    override fun onExit(ctx: GameContext) { holds.clear() }

    private fun addHold(y: Float) {
        val h = Hold()
        val ang = rng.nextFloat() * Mathf.PI * 2f
        val r = 1.1f + rng.nextFloat() * 1.6f
        h.x = kotlin.math.cos(ang) * r
        h.y = y + 1.2f
        h.z = kotlin.math.sin(ang) * r
        holds.add(h)
    }

    override fun update(ctx: GameContext) {
        if (over) { if (ctx.firePressed()) restart(ctx); return }

        val lh = ctx.leftHand?.takeIf { it.visible }
        val rh = ctx.rightHand?.takeIf { it.visible }

        // Grab detection per hand.
        grabbedLeft = nearestHold(lh, prevHandL)
        grabbedRight = nearestHold(rh, prevHandR)

        var pulled = 0f
        if (lh != null) {
            val dy = prevHandL[1] - lh.joints[HandModelIdx.TIP_Y]
            if (grabbedLeft >= 0 && dy > 0f) pulled += dy
        }
        if (rh != null) {
            val dy = prevHandR[1] - rh.joints[HandModelIdx.TIP_Y]
            if (grabbedRight >= 0 && dy > 0f) pulled += dy
        }

        // Fallback for viewers without hand tracking: gaze + trigger climbs.
        if (lh == null && rh == null) {
            if (ctx.fireHeld()) pulled += 1.6f * ctx.dt
        }

        if (pulled > 0f) {
            climbY += pulled
            falling = 0f
            if (pulled > 0.004f) ctx.sfx(com.metaport.xr.audio.Sfx.TICK, 0.25f)
        } else {
            falling += ctx.dt
            climbY -= (falling * 1.4f).coerceAtMost(2.5f) * ctx.dt
        }

        if (lh != null) { prevHandL[0] = lh.joints[HandModelIdx.TIP_X]; prevHandL[1] = lh.joints[HandModelIdx.TIP_Y]; prevHandL[2] = lh.joints[HandModelIdx.TIP_Z] }
        if (rh != null) { prevHandR[0] = rh.joints[HandModelIdx.TIP_X]; prevHandR[1] = rh.joints[HandModelIdx.TIP_Y]; prevHandR[2] = rh.joints[HandModelIdx.TIP_Z] }

        if (climbY > bestY) bestY = climbY
        if (climbY < -2.5f) over = true

        // Recycle holds above the climber.
        while (holds.size > 0 && holds[0].y < climbY - 3f) {
            holds.removeAt(0)
            addHold(holds.last().y + 1.05f)
        }
    }

    private fun nearestHold(h: com.metaport.xr.ar.HandModel?, prev: FloatArray): Int {
        if (h == null) return -1
        val tx = h.joints[HandModelIdx.TIP_X]
        val ty = h.joints[HandModelIdx.TIP_Y]
        val tz = h.joints[HandModelIdx.TIP_Z]
        var best = -1; var bd = 0.22f
        for ((i, hd) in holds.withIndex()) {
            val d = dist(tx, ty, tz, hd.x, hd.y - climbY, hd.z)
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    override fun render(ctx: GameContext) {
        for ((i, h) in holds.withIndex()) {
            val grabbed = i == grabbedLeft || i == grabbedRight
            val c = if (grabbed) floatArrayOf(1f, 0.85f, 0.3f) else floatArrayOf(0.4f, 0.3f, 1f)
            holdMat.rgb(c[0], c[1], c[2])
            holdMat.emissive(c[0], c[1], c[2], if (grabbed) 1.8f else 0.8f)
            compose(h.x, h.y - climbY, h.z, if (grabbed) 0.17f else 0.13f)
            ctx.scene.push(GameAssets.orb, holdMat, model, Scene.LAYER_OPAQUE)
        }

        // Shaft rings for a sense of vertical speed.
        val base = kotlin.math.floor(climbY / 2f) * 2f
        for (i in 0 until 10) {
            val y = base + i * 2f - climbY
            shaftMat.rgb(0.3f, 0.2f, 0.8f, 0.35f)
            Mat4.compose(model, 0f, y, 0f, 0f, 0f, 0f, 3.4f, 3.4f, 3.4f)
            ctx.scene.push(GameAssets.ring, shaftMat, model, Scene.LAYER_TRANSPARENT)
        }
    }

    override fun hud() = listOf(
        "Height" to ("%.1f m".format(bestY)),
        "Now" to ("%.1f m".format(climbY)),
        "Grip L" to (if (grabbedLeft >= 0) "ON" else "--"),
        "Grip R" to (if (grabbedRight >= 0) "ON" else "--")
    )

    override fun instructions() = listOf(
        "Put a fingertip on a glowing hold to grip it",
        "Pull a gripped hand down to climb",
        "Without hand tracking, hold the trigger to climb"
    )

    override fun isGameOver() = over
    override fun score() = (bestY * 100).toInt()
}

/** Index shorthands so game code stays readable. */
private object HandModelIdx {
    const val TIP_X = 8 * 3
    const val TIP_Y = 8 * 3 + 1
    const val TIP_Z = 8 * 3 + 2
}

// =====================================================================================
// VOXEL GARDEN — build mode
// =====================================================================================

/** Free-form voxel building with a gaze cursor anchored to ARCore planes. */
class VoxelGarden : GameBase() {
    override val id = "voxel-garden"
    override val title = "Voxel Garden"
    override val tagline = "Build freely in the space around you."
    override val category = "Creative"
    override val accentIndex = 4

    private class Voxel(val x: Int, val y: Int, val z: Int, val color: Int)

    private val voxels = ArrayList<Voxel>()
    private var cursorX = 0f; private var cursorY = 0f; private var cursorZ = -1.5f
    private var colorIndex = 0
    private var palette = arrayOf(
        floatArrayOf(0.2f, 0.9f, 1f), floatArrayOf(1f, 0.3f, 0.7f),
        floatArrayOf(0.4f, 1f, 0.5f), floatArrayOf(1f, 0.8f, 0.3f),
        floatArrayOf(0.6f, 0.45f, 1f), floatArrayOf(1f, 1f, 1f)
    )
    private var lastPlace = 0f
    private val cubeMat = Material().apply { roughness = 0.4f; emissive = 0.4f }
    private val cursorMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 2 }

    override fun onEnter(ctx: GameContext) {
        voxels.clear()
        colorIndex = 0
    }

    override fun onExit(ctx: GameContext) { voxels.clear() }

    override fun update(ctx: GameContext) {
        val d = 1.6f
        cursorX = kotlin.math.round((ctx.gazeOrigin[0] + ctx.gazeDir[0] * d) * 4f) / 4f
        cursorY = kotlin.math.round((ctx.gazeOrigin[1] + ctx.gazeDir[1] * d) * 4f) / 4f
        cursorZ = kotlin.math.round((ctx.gazeOrigin[2] + ctx.gazeDir[2] * d) * 4f) / 4f

        if (ctx.input.triggerPressed && ctx.time - lastPlace > 0.12f) {
            lastPlace = ctx.time
            val existing = voxels.firstOrNull {
                kotlin.math.abs(it.x / 4f - cursorX) < 0.1f &&
                    kotlin.math.abs(it.y / 4f - cursorY) < 0.1f &&
                    kotlin.math.abs(it.z / 4f - cursorZ) < 0.1f
            }
            if (existing != null) {
                voxels.remove(existing)
                ctx.sfx(com.metaport.xr.audio.Sfx.BACK, 0.5f)
            } else {
                voxels.add(Voxel((cursorX * 4).toInt(), (cursorY * 4).toInt(), (cursorZ * 4).toInt(), colorIndex))
                ctx.sfx(com.metaport.xr.audio.Sfx.GRAB, 0.5f)
            }
            if (voxels.size > 900) voxels.removeAt(0)
        }
        if (ctx.input.rightPinchEdge) colorIndex = (colorIndex + 1) % palette.size
    }

    override fun render(ctx: GameContext) {
        val cube = GameAssets.cube ?: return
        for (v in voxels) {
            val c = palette[v.color % palette.size]
            cubeMat.rgb(c[0], c[1], c[2])
            cubeMat.emissive(c[0], c[1], c[2], 0.45f)
            compose(v.x / 4f, v.y / 4f, v.z / 4f, 0.23f)
            ctx.scene.push(cube, cubeMat, model, Scene.LAYER_OPAQUE)
        }
        val c = palette[colorIndex]
        cursorMat.rgb(c[0], c[1], c[2], 0.75f)
        compose(cursorX, cursorY, cursorZ, 0.26f)
        ctx.scene.push(cube, cursorMat, model, Scene.LAYER_TRANSPARENT)
    }

    override fun hud() = listOf(
        "Voxels" to voxels.size.toString(),
        "Colour" to (colorIndex + 1).toString() + "/" + palette.size
    )

    override fun instructions() = listOf(
        "Look where you want a block and tap the trigger",
        "Tap an existing block to remove it",
        "Pinch with your right hand to cycle colours"
    )

    override fun score() = voxels.size
}

// =====================================================================================
// FIRST STEPS — onboarding
// =====================================================================================

/** Guided tutorial that verifies tracking, gaze and hand input in sequence. */
class FirstSteps : GameBase() {
    override val id = "first-steps"
    override val title = "First Steps"
    override val tagline = "Two-minute calibration and control tour."
    override val category = "Learn"
    override val accentIndex = 5

    private var step = 0
    private var stepTime = 0f
    private var done = false
    private val targets = ArrayList<FloatArray>()
    private var hit = BooleanArray(4)
    private var totalScore = 0

    private val targetMat = Material().apply { emissive = 1.2f; blend = Material.BLEND_ALPHA }
    private val flashMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 2f }

    override fun onEnter(ctx: GameContext) {
        step = 0; stepTime = 0f; done = false; totalScore = 0
        targets.clear()
        hit = BooleanArray(4)
        for (i in 0 until 4) {
            val a = i / 4f * Mathf.PI * 2f
            targets.add(floatArrayOf(kotlin.math.sin(a) * 1.4f, 1.35f, -1.6f + kotlin.math.cos(a) * 0.4f, 0f))
        }
    }

    override fun onExit(ctx: GameContext) { targets.clear() }

    override fun update(ctx: GameContext) {
        stepTime += ctx.dt
        when (step) {
            0 -> if (stepTime > 3.5f || ctx.firePressed()) { step = 1; stepTime = 0f }
            1 -> if (stepTime > 4.5f || ctx.firePressed()) { step = 2; stepTime = 0f }
            2 -> {
                for ((i, t) in targets.withIndex()) {
                    t[3] += ctx.dt
                    if (hit[i]) continue
                    val gx = ctx.gazeOrigin[0] + ctx.gazeDir[0] * 1.8f
                    val gy = ctx.gazeOrigin[1] + ctx.gazeDir[1] * 1.8f
                    val gz = ctx.gazeOrigin[2] + ctx.gazeDir[2] * 1.8f
                    if (dist(gx, gy, gz, t[0], t[1], t[2]) < 0.3f && ctx.firePressed()) {
                        hit[i] = true
                        totalScore += 100
                        ctx.sfx(com.metaport.xr.audio.Sfx.SCORE, 0.8f)
                    }
                }
                if (hit.all { it }) { step = 3; stepTime = 0f }
            }
            3 -> if (stepTime > 3f) done = true
        }
    }

    override fun render(ctx: GameContext) {
        if (step < 2) return
        for ((i, t) in targets.withIndex()) {
            val s = 0.22f + 0.03f * kotlin.math.sin(t[3] * 4f)
            if (hit[i]) {
                flashMat.rgb(0.3f, 1f, 0.6f, 0.6f)
                compose(t[0], t[1], t[2], s * 1.4f)
                ctx.scene.push(GameAssets.orb, flashMat, model, Scene.LAYER_TRANSPARENT)
            } else {
                targetMat.rgb(0.2f, 0.9f, 1f, 0.9f)
                targetMat.emissive(0.2f, 0.9f, 1f, 1.4f)
                compose(t[0], t[1], t[2], s)
                ctx.scene.push(GameAssets.orb, targetMat, model, Scene.LAYER_OPAQUE)
            }
        }
    }

    override fun hud(): List<Pair<String, String>> {
        val msg = when (step) {
            0 -> "Turn your head slowly — look left, right, up, down"
            1 -> "Hold your hands up in front of the camera and open them"
            2 -> "Look at each cyan orb and pull the trigger"
            else -> "All systems nominal. Welcome to MetaPort."
        }
        return listOf("Step" to "${step + 1}/4", "Task" to msg, "Score" to totalScore.toString())
    }

    override fun instructions() = listOf(
        "Follow the four on-screen prompts",
        "Tap the trigger to skip a step"
    )

    override fun isGameOver() = done
    override fun score() = totalScore
}
