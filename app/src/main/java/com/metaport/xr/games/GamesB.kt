package com.metaport.xr.games

import com.metaport.xr.core.math.Mathf
import com.metaport.xr.core.math.Mat4
import com.metaport.xr.scene.Material
import com.metaport.xr.scene.Scene

// =====================================================================================
// CHRONO STRIKE — time moves only while you move
// =====================================================================================

/**
 * Drones approach in slow motion; the world's clock runs faster the faster your
 * head moves, so standing still buys you aim time. Original design built around
 * ARCore's 6DOF position tracking.
 */
class ChronoStrike : GameBase() {
    override val id = "chrono-strike"
    override val title = "Chrono Strike"
    override val tagline = "Time flows only while you move."
    override val category = "Shooter"
    override val accentIndex = 2

    private class Drone {
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
        var alive = true
        var phase = 0f
    }

    private val drones = ArrayList<Drone>()
    private var spawnTimer = 0f
    private var timeScale = 0f
    private var kills = 0
    private var shots = 0
    private var hitsTaken = 0
    private var totalScore = 0
    private var over = false
    private val beams = ArrayList<FloatArray>()

    private val droneMat = Material().apply { emissive = 1.1f; roughness = 0.25f; metallic = 0.5f }
    private val beamMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 2; glow = 1.6f }
    private val muzzleMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 2f }

    override fun onEnter(ctx: GameContext) {
        drones.clear(); beams.clear()
        kills = 0; shots = 0; hitsTaken = 0; totalScore = 0; over = false
        spawnTimer = 0f
    }

    override fun onExit(ctx: GameContext) { drones.clear(); beams.clear() }

    override fun update(ctx: GameContext) {
        if (over) { if (ctx.firePressed()) restart(ctx); return }

        // Head speed drives the world clock.
        val speed = kotlin.math.sqrt(
            ctx.input.moveX * ctx.input.moveX + ctx.input.moveY * ctx.input.moveY
        )
        val headVel = headSpeed(ctx)
        val target = (0.06f + headVel * 1.9f + speed * 0.3f).coerceIn(0.05f, 1.6f)
        timeScale += (target - timeScale) * (1f - kotlin.math.exp(-9f * ctx.dt))
        val sdt = ctx.dt * timeScale

        spawnTimer -= sdt
        if (spawnTimer <= 0f && drones.size < 14) {
            spawnTimer = 1.6f - (kills * 0.02f).coerceAtMost(0.9f)
            val d = Drone()
            val ang = rng.nextFloat() * Mathf.PI * 2f
            val rad = 9f + rng.nextFloat() * 5f
            d.x = ctx.headPos[0] + kotlin.math.cos(ang) * rad
            d.y = 0.7f + rng.nextFloat() * 2.2f
            d.z = ctx.headPos[2] + kotlin.math.sin(ang) * rad
            d.phase = rng.nextFloat() * 6.28f
            drones.add(d)
        }

        for (d in drones) {
            if (!d.alive) continue
            val dx = ctx.headPos[0] - d.x
            val dy = (ctx.headPos[1] + 0.2f) - d.y
            val dz = ctx.headPos[2] - d.z
            val l = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001f)
            val sp = 1.5f + kills * 0.03f
            d.x += dx / l * sp * sdt + kotlin.math.sin(ctx.time * 1.3f + d.phase) * 0.35f * sdt
            d.y += dy / l * sp * 0.5f * sdt
            d.z += dz / l * sp * sdt
            if (l < 0.55f) {
                d.alive = false
                hitsTaken++
                ctx.sfx(com.metaport.xr.audio.Sfx.ERROR, 0.9f)
                if (hitsTaken >= 5) over = true
            }
        }

        // Shooting: gaze ray against drones.
        if (ctx.firePressed()) {
            shots++
            var best: Drone? = null
            var bestT = Float.MAX_VALUE
            for (d in drones) {
                if (!d.alive) continue
                val ox = d.x - ctx.gazeOrigin[0]
                val oy = d.y - ctx.gazeOrigin[1]
                val oz = d.z - ctx.gazeOrigin[2]
                val t = ox * ctx.gazeDir[0] + oy * ctx.gazeDir[1] + oz * ctx.gazeDir[2]
                if (t <= 0f) continue
                val cx = ox - ctx.gazeDir[0] * t
                val cy = oy - ctx.gazeDir[1] * t
                val cz = oz - ctx.gazeDir[2] * t
                if (cx * cx + cy * cy + cz * cz < 0.32f * 0.32f && t < bestT) { bestT = t; best = d }
            }
            if (best != null) {
                best.alive = false
                kills++
                totalScore += (120 + timeScale * 90).toInt()
                ctx.sfx(com.metaport.xr.audio.Sfx.SCORE, 0.8f)
                beams.add(floatArrayOf(best.x, best.y, best.z, ctx.time))
            } else {
                ctx.sfx(com.metaport.xr.audio.Sfx.TICK, 0.4f)
            }
        }

        var i = 0
        while (i < drones.size) { if (!drones[i].alive) drones.removeAt(i) else i++ }
        var b = 0
        while (b < beams.size) { if (ctx.time - beams[b][3] > 0.35f) beams.removeAt(b) else b++ }
    }

    private var lastX = 0f; private var lastY = 0f; private var lastZ = 0f
    private var headVelSmooth = 0f
    private fun headSpeed(ctx: GameContext): Float {
        if (lastX == 0f && lastY == 0f && lastZ == 0f) {
            lastX = ctx.headPos[0]; lastY = ctx.headPos[1]; lastZ = ctx.headPos[2]
        }
        val dx = ctx.headPos[0] - lastX
        val dy = ctx.headPos[1] - lastY
        val dz = ctx.headPos[2] - lastZ
        lastX = ctx.headPos[0]; lastY = ctx.headPos[1]; lastZ = ctx.headPos[2]
        val v = if (ctx.dt > 1e-4f) kotlin.math.sqrt(dx * dx + dy * dy + dz * dz) / ctx.dt else 0f
        headVelSmooth += (v.coerceAtMost(4f) - headVelSmooth) * (1f - kotlin.math.exp(-8f * ctx.dt))
        return headVelSmooth
    }

    override fun render(ctx: GameContext) {
        val orb = GameAssets.orb ?: return
        for (d in drones) {
            val pulse = 0.7f + 0.3f * kotlin.math.sin(ctx.time * 4f + d.phase)
            droneMat.rgb(0.8f * pulse, 0.25f, 1f)
            droneMat.emissive(0.7f, 0.2f, 1f, 1.2f)
            compose(d.x, d.y, d.z, 0.3f, ctx.time * 40f * Mathf.DEG2RAD)
            ctx.scene.push(orb, droneMat, model, Scene.LAYER_OPAQUE)
            // Core
            muzzleMat.rgb(1f, 0.5f, 0.9f, 0.8f)
            compose(d.x, d.y, d.z, 0.16f)
            ctx.scene.push(orb, muzzleMat, model, Scene.LAYER_TRANSPARENT)
        }
        for (b in beams) {
            val age = (ctx.time - b[3]) / 0.35f
            beamMat.rgb(0.3f, 1f, 0.8f, (1f - age) * 0.9f)
            compose(b[0], b[1], b[2], 0.25f + age * 1.6f)
            ctx.scene.push(orb, beamMat, model, Scene.LAYER_TRANSPARENT)
        }
    }

    override fun hud() = listOf(
        "Score" to totalScore.toString(),
        "Kills" to kills.toString(),
        "Accuracy" to (if (shots == 0) "-" else "${(kills * 100 / shots)}%"),
        "Time flow" to ("%.2fx".format(timeScale)),
        "Integrity" to "${5 - hitsTaken}/5"
    )

    override fun instructions() = listOf(
        "The world only moves while you move — freeze to line up a shot",
        "Look at a drone and pull the trigger to fire",
        "Five contacts reaching you ends the run"
    )

    override fun isGameOver() = over
    override fun score() = totalScore
}

// =====================================================================================
// DRUM FORGE — hand percussion
// =====================================================================================

/** Four floating pads; strike them in time with the generated beat. */
class DrumForge : GameBase() {
    override val id = "drum-forge"
    override val title = "Drum Forge"
    override val tagline = "Play four floating pads with your bare hands."
    override val category = "Rhythm"
    override val accentIndex = 3
    override val requiresHands = false

    private val padPos = arrayOf(
        floatArrayOf(-0.55f, 1.0f, -0.9f),
        floatArrayOf(-0.18f, 1.15f, -0.9f),
        floatArrayOf(0.18f, 1.15f, -0.9f),
        floatArrayOf(0.55f, 1.0f, -0.9f)
    )
    private val padColor = arrayOf(
        floatArrayOf(1f, 0.3f, 0.4f), floatArrayOf(1f, 0.75f, 0.25f),
        floatArrayOf(0.3f, 1f, 0.6f), floatArrayOf(0.3f, 0.7f, 1f)
    )
    private val padHit = FloatArray(4)
    private var beats = 0
    private var lastBeatTime = 0f
    private var bpm = 96f
    private var totalScore = 0
    private var combo = 0
    private var prevDist = FloatArray(2) { 9f }

    private val padMat = Material().apply { emissive = 0.8f; roughness = 0.3f }
    private val flashMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 2f }

    override fun onEnter(ctx: GameContext) {
        beats = 0; totalScore = 0; combo = 0
        bpm = 84f + rng.nextInt(30)
        padHit.fill(0f)
        lastBeatTime = ctx.time
    }

    override fun onExit(ctx: GameContext) {}

    override fun update(ctx: GameContext) {
        val beatDur = 60f / bpm
        if (ctx.time - lastBeatTime >= beatDur) {
            lastBeatTime += beatDur
            beats++
            if (beats % 4 == 0) ctx.sfx(com.metaport.xr.audio.Sfx.TICK, 0.25f)
        }
        for (i in 0 until 4) padHit[i] = (padHit[i] - ctx.dt * 3f).coerceAtLeast(0f)

        // Strike detection: fingertip crossing into a pad, or gaze+trigger.
        for (h in listOfNotNull(ctx.rightHand?.takeIf { it.visible }, ctx.leftHand?.takeIf { it.visible })) {
            for (i in 0 until 4) {
                val d = dist(
                    h.joints[8], h.joints[9], h.joints[10],
                    padPos[i][0], padPos[i][1], padPos[i][2]
                )
                if (d < 0.16f) strike(ctx, i, 1f - d / 0.16f)
            }
        }
        if (ctx.firePressed()) {
            val t = -0.9f
            val gx = ctx.gazeOrigin[0] + ctx.gazeDir[0] * t
            val gy = ctx.gazeOrigin[1] + ctx.gazeDir[1] * t
            var best = -1; var bd = 0.22f
            for (i in 0 until 4) {
                val d = dist(gx, gy, 0f, padPos[i][0], padPos[i][1], 0f)
                if (d < bd) { bd = d; best = i }
            }
            if (best >= 0) strike(ctx, best, 0.8f)
        }
    }

    private fun strike(ctx: GameContext, pad: Int, strength: Float) {
        if (padHit[pad] > 0.35f) return
        padHit[pad] = 1f
        combo++
        val beatDur = 60f / bpm
        val off = Mathf.repeat(ctx.time - lastBeatTime, beatDur)
        val onBeat = minOf(off, beatDur - off) < beatDur * 0.28f
        totalScore += (if (onBeat) 120 else 45) + (strength * 40).toInt()
        ctx.sfx(if (pad % 2 == 0) com.metaport.xr.audio.Sfx.HIT else com.metaport.xr.audio.Sfx.GRAB, 0.7f)
        if (!onBeat) combo = 0
    }

    override fun render(ctx: GameContext) {
        val pad = GameAssets.pad ?: return
        for (i in 0 until 4) {
            val h = padHit[i]
            val c = padColor[i]
            padMat.rgb(c[0] * (0.5f + h), c[1] * (0.5f + h), c[2] * (0.5f + h))
            padMat.emissive(c[0], c[1], c[2], 0.5f + h * 2f)
            val lift = h * 0.05f
            compose(padPos[i][0], padPos[i][1] - lift, padPos[i][2], 0.17f + h * 0.02f)
            ctx.scene.push(pad, padMat, model, Scene.LAYER_OPAQUE)

            if (h > 0.05f) {
                flashMat.rgb(c[0], c[1], c[2], h * 0.7f)
                compose(padPos[i][0], padPos[i][1], padPos[i][2], 0.2f + h * 0.5f)
                ctx.scene.push(GameAssets.orb, flashMat, model, Scene.LAYER_TRANSPARENT)
            }
        }
    }

    override fun hud() = listOf(
        "Score" to totalScore.toString(),
        "Combo" to "${combo}x",
        "BPM" to bpm.toInt().toString(),
        "Bars" to (beats / 4).toString()
    )

    override fun instructions() = listOf(
        "Tap the pads with a tracked fingertip",
        "Or look at a pad and pull the trigger",
        "Hits on the beat score triple"
    )

    override fun score() = totalScore
}

// =====================================================================================
// NEBULA HOOPS — zero gravity throws
// =====================================================================================

/** Throw orbs through drifting rings. Release timing sets the arc. */
class NebulaHoops : GameBase() {
    override val id = "nebula-hoops"
    override val title = "Nebula Hoops"
    override val tagline = "Throw light orbs through drifting rings."
    override val category = "Sport"
    override val accentIndex = 5

    private class Ring {
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f
        var scored = false
        var phase = 0f
    }

    private class Shot {
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
        var life = 0f
    }

    private val rings = ArrayList<Ring>()
    private val shots = ArrayList<Shot>()
    private var charging = 0f
    private var scored = 0
    private var thrown = 0
    private var totalScore = 0

    private val ringMat = Material().apply { emissive = 1.3f; blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 2; glow = 1.5f }
    private val ballMat = Material().apply { emissive = 1.5f; roughness = 0.2f }

    override fun onEnter(ctx: GameContext) {
        rings.clear(); shots.clear()
        scored = 0; thrown = 0; totalScore = 0; charging = 0f
        for (i in 0 until 5) spawnRing(ctx)
    }

    override fun onExit(ctx: GameContext) { rings.clear(); shots.clear() }

    private fun spawnRing(ctx: GameContext) {
        val r = Ring()
        val ang = -0.7f + rng.nextFloat() * 1.4f
        val d = 5f + rng.nextFloat() * 7f
        r.x = ctx.headPos[0] + kotlin.math.sin(ang) * d
        r.y = 0.9f + rng.nextFloat() * 2.4f
        r.z = ctx.headPos[2] - kotlin.math.cos(ang) * d
        r.vx = (rng.nextFloat() - 0.5f) * 0.5f
        r.vy = (rng.nextFloat() - 0.5f) * 0.3f
        r.phase = rng.nextFloat() * 6.28f
        rings.add(r)
    }

    override fun update(ctx: GameContext) {
        if (ctx.fireHeld()) charging = (charging + ctx.dt * 1.6f).coerceAtMost(1f)
        if (!ctx.fireHeld() && charging > 0f) {
            val power = 5f + charging * 9f
            val s = Shot()
            s.x = ctx.gazeOrigin[0]; s.y = ctx.gazeOrigin[1] - 0.1f; s.z = ctx.gazeOrigin[2]
            s.vx = ctx.gazeDir[0] * power
            s.vy = ctx.gazeDir[1] * power + 1.2f
            s.vz = ctx.gazeDir[2] * power
            shots.add(s)
            thrown++
            charging = 0f
            ctx.sfx(com.metaport.xr.audio.Sfx.WHOOSH, 0.7f)
        }

        for (r in rings) {
            r.x += r.vx * ctx.dt
            r.y += kotlin.math.sin(ctx.time * 0.6f + r.phase) * 0.25f * ctx.dt
            if (r.x > ctx.headPos[0] + 9f || r.x < ctx.headPos[0] - 9f) r.vx = -r.vx
        }

        var i = 0
        while (i < shots.size) {
            val s = shots[i]
            s.life += ctx.dt
            s.vy -= 2.4f * ctx.dt
            s.x += s.vx * ctx.dt; s.y += s.vy * ctx.dt; s.z += s.vz * ctx.dt
            for (r in rings) {
                if (r.scored) continue
                if (dist(s.x, s.y, s.z, r.x, r.y, r.z) < 0.75f) {
                    r.scored = true
                    scored++
                    totalScore += 250 + (charging * 100).toInt()
                    ctx.sfx(com.metaport.xr.audio.Sfx.SCORE, 0.9f)
                    rings.remove(r)
                    spawnRing(ctx)
                    break
                }
            }
            if (s.life > 6f || s.y < -3f) shots.removeAt(i) else i++
        }
    }

    override fun render(ctx: GameContext) {
        for (r in rings) {
            ringMat.rgb(0.2f, 0.9f, 1f, 0.85f)
            Mat4.compose(model, r.x, r.y, r.z, 0f, 0f, 0f, 0.75f, 0.75f, 0.75f)
            ctx.scene.push(GameAssets.ring, ringMat, model, Scene.LAYER_TRANSPARENT)
        }
        for (s in shots) {
            ballMat.rgb(1f, 0.85f, 0.35f)
            ballMat.emissive(1f, 0.7f, 0.2f, 1.8f)
            compose(s.x, s.y, s.z, 0.14f)
            ctx.scene.push(GameAssets.orb, ballMat, model, Scene.LAYER_OPAQUE)
        }
        if (charging > 0.01f) {
            ballMat.rgb(1f, 1f, 1f, 0.5f)
            compose(
                ctx.gazeOrigin[0] + ctx.gazeDir[0] * 0.35f,
                ctx.gazeOrigin[1] + ctx.gazeDir[1] * 0.35f - 0.08f,
                ctx.gazeOrigin[2] + ctx.gazeDir[2] * 0.35f,
                0.1f + charging * 0.09f
            )
            ctx.scene.push(GameAssets.orb, ballMat, model, Scene.LAYER_TRANSPARENT)
        }
    }

    override fun hud() = listOf(
        "Score" to totalScore.toString(),
        "Scored" to scored.toString(),
        "Throws" to thrown.toString(),
        "Accuracy" to (if (thrown == 0) "-" else "${scored * 100 / thrown}%")
    )

    override fun instructions() = listOf(
        "Hold the trigger to charge, release to throw",
        "Aim with your gaze; the arc follows your head",
        "Rings drift — lead your target"
    )

    override fun score() = totalScore
}

// =====================================================================================
// HOVER PUTT — spatial mini golf
// =====================================================================================

/** Aim with your gaze, charge with the trigger, sink the orb in the hole. */
class HoverPutt : GameBase() {
    override val id = "hover-putt"
    override val title = "Hover Putt"
    override val tagline = "Six floating greens, one putt each."
    override val category = "Sport"
    override val accentIndex = 4

    private var ballX = 0f; private var ballZ = -1.2f
    private var vx = 0f; private var vz = 0f
    private var holeX = 0f; private var holeZ = -6f
    private var charging = 0f
    private var strokes = 0
    private var holesDone = 0
    private var totalScore = 0
    private var rolling = false

    private val ballMat = Material().apply { emissive = 0.9f; roughness = 0.25f }
    private val greenMat = Material().apply { roughness = 0.8f }
    private val holeMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 1.8f }
    private val aimMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 2 }

    override fun onEnter(ctx: GameContext) {
        holesDone = 0; strokes = 0; totalScore = 0
        newHole(ctx)
    }

    override fun onExit(ctx: GameContext) {}

    private fun newHole(ctx: GameContext) {
        ballX = ctx.headPos[0]; ballZ = ctx.headPos[2] - 1.2f
        vx = 0f; vz = 0f; rolling = false; charging = 0f
        val ang = -0.8f + rng.nextFloat() * 1.6f
        val d = 5f + rng.nextFloat() * 6f
        holeX = ctx.headPos[0] + kotlin.math.sin(ang) * d
        holeZ = ctx.headPos[2] - kotlin.math.cos(ang) * d
    }

    override fun update(ctx: GameContext) {
        if (!rolling) {
            if (ctx.fireHeld()) charging = (charging + ctx.dt * 1.1f).coerceAtMost(1f)
            if (!ctx.fireHeld() && charging > 0f) {
                val power = 2.5f + charging * 7f
                val t = (holeZ - ballZ)
                val dz = ctx.gazeDir[2].let { if (kotlin.math.abs(it) < 1e-3f) -1f else it }
                val scale = t / dz
                vx = ctx.gazeDir[0] * scale * 0f + ctx.gazeDir[0] * power
                vz = dz.let { kotlin.math.sign(it) } * power
                strokes++
                rolling = true
                charging = 0f
                ctx.sfx(com.metaport.xr.audio.Sfx.GRAB, 0.8f)
            }
        } else {
            ballX += vx * ctx.dt
            ballZ += vz * ctx.dt
            val drag = kotlin.math.exp(-1.1f * ctx.dt)
            vx *= drag; vz *= drag
            if (dist(ballX, 0f, ballZ, holeX, 0f, holeZ) < 0.32f &&
                kotlin.math.sqrt(vx * vx + vz * vz) < 3.5f
            ) {
                holesDone++
                totalScore += (300 - strokes * 40).coerceAtLeast(50)
                ctx.sfx(com.metaport.xr.audio.Sfx.SCORE, 1f)
                if (holesDone >= 6) {
                    totalScore += 500
                } else {
                    newHole(ctx)
                }
            } else if (kotlin.math.sqrt(vx * vx + vz * vz) < 0.12f) {
                rolling = false
            }
        }
    }

    override fun render(ctx: GameContext) {
        val green = GameAssets.plane ?: return
        greenMat.rgb(0.14f, 0.35f, 0.24f)
        composeS((ballX + holeX) * 0.5f, 0.01f, (ballZ + holeZ) * 0.5f, 14f, 1f, 14f)
        ctx.scene.push(green, greenMat, model, Scene.LAYER_OPAQUE)

        holeMat.rgb(0.3f, 1f, 0.6f, 0.9f)
        composeS(holeX, 0.03f, holeZ, 0.65f, 1f, 0.65f)
        ctx.scene.push(green, holeMat, model, Scene.LAYER_TRANSPARENT)

        ballMat.rgb(1f, 1f, 1f)
        ballMat.emissive(0.8f, 0.9f, 1f, 1.1f)
        compose(ballX, 0.11f, ballZ, 0.22f)
        ctx.scene.push(GameAssets.orb, ballMat, model, Scene.LAYER_OPAQUE)

        if (!rolling && charging > 0.01f) {
            aimMat.rgb(1f, 0.8f, 0.3f, 0.75f)
            composeS(ballX, 0.05f, ballZ - 1f - charging * 2f, 0.08f, 1f, 2f + charging * 3f)
            ctx.scene.push(green, aimMat, model, Scene.LAYER_TRANSPARENT)
        }
    }

    override fun hud() = listOf(
        "Score" to totalScore.toString(),
        "Hole" to "${holesDone + 1}/6",
        "Strokes" to strokes.toString()
    )

    override fun instructions() = listOf(
        "Look where you want the orb to go",
        "Hold the trigger to charge power, release to putt",
        "Six holes; fewer strokes scores higher"
    )

    override fun score() = totalScore
}
