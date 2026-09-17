package com.metaport.xr.games

import com.metaport.xr.core.gl.Mesh
import com.metaport.xr.core.gl.MeshGen
import com.metaport.xr.core.math.Mathf
import com.metaport.xr.core.math.Mat4
import com.metaport.xr.scene.Material
import com.metaport.xr.scene.Scene

/** Shared game geometry, created once and reused by every title. */
object GameAssets {
    var cube: Mesh? = null; private set
    var orb: Mesh? = null; private set
    var rod: Mesh? = null; private set
    var ring: Mesh? = null; private set
    var blade: Mesh? = null; private set
    var pad: Mesh? = null; private set
    var plane: Mesh? = null; private set
    var capsule: Mesh? = null; private set

    fun init() {
        cube = MeshGen.box(1f, 1f, 1f)
        orb = MeshGen.sphere(0.5f, 18, 12)
        rod = MeshGen.cylinder(0.5f, 1f, 12)
        ring = MeshGen.torus(1f, 0.05f, 40, 10)
        blade = MeshGen.box(0.06f, 1f, 0.06f)
        pad = MeshGen.cylinder(1f, 0.08f, 28)
        plane = MeshGen.quadXZ(1f, 1f)
        capsule = MeshGen.capsule(0.5f, 1f, 8)
    }

    val lit = Material()
    val glow = Material().apply {
        blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 1.4f
    }
    val solidGlow = Material().apply { emissive = 0.9f }

    fun release() {
        cube?.release(); orb?.release(); rod?.release(); ring?.release()
        blade?.release(); pad?.release(); plane?.release(); capsule?.release()
        cube = null; orb = null; rod = null; ring = null
        blade = null; pad = null; plane = null; capsule = null
    }
}

// =====================================================================================
// PULSE BLADE — rhythm slasher
// =====================================================================================

/**
 * A four-lane rhythm game. Note charts are generated procedurally from a seeded
 * pattern, so every round is a new (but reproducible) track.
 *
 * Slash by moving a tracked hand through the note, or look at a note and pull
 * the Cardboard trigger.
 */
class PulseBlade : GameBase() {
    override val id = "pulse-blade"
    override val title = "Pulse Blade"
    override val tagline = "Slash the beat on four neon lanes."
    override val category = "Rhythm"
    override val accentIndex = 1

    private class Note {
        var lane = 0; var y = 0f; var z = -12f; var t = 0f
        var active = true; var hit = false
    }

    private val notes = ArrayList<Note>()
    private val laneX = floatArrayOf(-0.84f, -0.28f, 0.28f, 0.84f)
    private val laneY = floatArrayOf(1.05f, 1.45f, 1.05f, 1.45f)
    private var bpm = 112f
    private var nextBeat = 0
    private var beatIndex = 0
    private var speed = 5.2f
    private val hitZ = -1.0f

    private var combo = 0
    private var maxCombo = 0
    private var hits = 0
    private var misses = 0
    private var totalScore = 0
    private var over = false

    private val noteMat = Material().apply { emissive = 1f; roughness = 0.2f }
    private val laneMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true }
    private val hitMat = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 2f }
    private val effects = ArrayList<FloatArray>()
    private val handTrail = ArrayList<FloatArray>()

    override fun onEnter(ctx: GameContext) {
        seed(System.currentTimeMillis() and 0xFFFF)
        notes.clear(); effects.clear(); handTrail.clear()
        combo = 0; maxCombo = 0; hits = 0; misses = 0; totalScore = 0
        over = false; beatIndex = 0; nextBeat = 0
        bpm = 104f + rng.nextInt(20)
        speed = 4.8f + bpm / 60f
    }

    override fun onExit(ctx: GameContext) {
        notes.clear(); effects.clear(); handTrail.clear()
    }

    override fun update(ctx: GameContext) {
        if (over) {
            if (ctx.firePressed()) restart(ctx)
            return
        }
        val beatDur = 60f / bpm
        // Spawn on eighth notes using a weighted pattern.
        while (nextBeat * beatDur * 0.5f < ctx.time + 3.0f) {
            val step = nextBeat
            nextBeat++
            val density = if (step % 16 < 8) 0.55f else 0.78f
            if (rng.nextFloat() < density) {
                val lane = rng.nextInt(4)
                val n = Note()
                n.lane = lane
                n.y = laneY[lane]
                n.z = -14f
                n.t = ctx.time
                notes.add(n)
                // Occasional double note for texture.
                if (rng.nextFloat() < 0.14f) {
                    val l2 = (lane + 2) % 4
                    val n2 = Note(); n2.lane = l2; n2.y = laneY[l2]; n2.z = -14f; n2.t = ctx.time
                    notes.add(n2)
                }
            }
        }

        // Hand trail for the sabres.
        val rh = ctx.rightHand?.takeIf { it.visible }
        val lh = ctx.leftHand?.takeIf { it.visible }
        if (rh != null) addTrail(rh.joints[8], rh.joints[9], rh.joints[10], 1f)
        if (lh != null) addTrail(lh.joints[8], lh.joints[9], lh.joints[10], 0f)

        val gazeX = ctx.gazeOrigin[0] + ctx.gazeDir[0] * -hitZ
        val gazeY = ctx.gazeOrigin[1] + ctx.gazeDir[1] * -hitZ

        var i = 0
        while (i < notes.size) {
            val n = notes[i]
            n.z += speed * ctx.dt
            if (n.active && n.z > hitZ + 0.55f) {
                n.active = false
                if (!n.hit) { misses++; combo = 0; ctx.sfx(com.metaport.xr.audio.Sfx.ERROR, 0.5f) }
            }
            if (n.active && !n.hit && n.z > hitZ - 0.45f && n.z < hitZ + 0.35f) {
                val nx = laneX[n.lane]; val ny = n.y
                var slashed = false
                // Hand slash: fingertip within 0.30 m of the note.
                for (h in listOfNotNull(rh, lh)) {
                    if (dist(h.joints[8], h.joints[9], h.joints[10], nx, ny, n.z) < 0.30f) slashed = true
                }
                // Gaze + trigger fallback.
                if (!slashed && ctx.firePressed() &&
                    dist(gazeX, gazeY, hitZ, nx, ny, hitZ) < 0.26f
                ) slashed = true

                if (slashed) {
                    n.hit = true; n.active = false
                    hits++; combo++
                    if (combo > maxCombo) maxCombo = combo
                    val perfect = 1f - Mathf.clamp01(kotlin.math.abs(n.z - hitZ) / 0.4f)
                    val gained = (100 + perfect * 150 + combo * 5).toInt()
                    totalScore += gained
                    effects.add(floatArrayOf(nx, ny, n.z, ctx.time, 1f))
                    ctx.sfx(com.metaport.xr.audio.Sfx.HIT, 0.8f)
                }
            }
            if (n.z > 3f) { notes.removeAt(i) } else i++
        }

        var e = 0
        while (e < effects.size) {
            if (ctx.time - effects[e][3] > 0.5f) effects.removeAt(e) else e++
        }
        var t = 0
        while (t < handTrail.size) {
            val tr = handTrail[t]
            tr[4] -= ctx.dt * 3.2f
            if (tr[4] <= 0f) handTrail.removeAt(t) else t++
        }

        if (misses > 12) over = true
    }

    private fun addTrail(x: Float, y: Float, z: Float, side: Float) {
        handTrail.add(floatArrayOf(x, y, z, side, 1f))
        if (handTrail.size > 90) handTrail.removeAt(0)
    }

    override fun render(ctx: GameContext) {
        val scene = ctx.scene
        val cube = GameAssets.cube ?: return

        // Lane rails.
        for (l in 0 until 4) {
            laneMat.rgb(0.1f, 0.5f, 0.9f, 0.22f)
            composeS(laneX[l], laneY[l] - 0.35f, -6.5f, 0.42f, 0.02f, 12f)
            scene.push(GameAssets.plane, laneMat, model, Scene.LAYER_TRANSPARENT)
        }

        // Hit line.
        hitMat.rgb(1f, 0.3f, 0.7f, 0.85f)
        composeS(0f, 1.25f, hitZ, 2.3f, 0.03f, 0.06f)
        scene.push(cube, hitMat, model, Scene.LAYER_TRANSPARENT)

        // Notes.
        for (n in notes) {
            if (!n.active) continue
            val near = 1f - Mathf.clamp01(kotlin.math.abs(n.z - hitZ) / 6f)
            val c = if (n.lane % 2 == 0) floatArrayOf(0.2f, 0.9f, 1f) else floatArrayOf(1f, 0.3f, 0.75f)
            noteMat.rgb(c[0], c[1], c[2])
            noteMat.emissive(c[0], c[1], c[2], 0.5f + near * 1.2f)
            compose(n.lane.let { laneX[it] }, n.y, n.z, 0.3f, ctx.time * 90f * Mathf.DEG2RAD)
            scene.push(cube, noteMat, model, Scene.LAYER_OPAQUE)
        }

        // Slash bursts.
        for (e in effects) {
            val age = ctx.time - e[3]
            val s = 0.2f + age * 2.4f
            hitMat.rgb(1f, 0.9f, 0.4f, (1f - age * 2f).coerceAtLeast(0f))
            compose(e[0], e[1], e[2], s)
            scene.push(GameAssets.orb, hitMat, model, Scene.LAYER_TRANSPARENT)
        }

        // Hand trails (sabres).
        for (tr in handTrail) {
            val a = tr[4]
            hitMat.rgb(if (tr[3] > 0.5f) 0.2f else 1f, if (tr[3] > 0.5f) 0.9f else 0.25f, 1f, a * 0.55f)
            compose(tr[0], tr[1], tr[2], 0.05f * a + 0.012f)
            scene.push(GameAssets.orb, hitMat, model, Scene.LAYER_TRANSPARENT)
        }
    }

    override fun hud(): List<Pair<String, String>> = listOf(
        "Score" to totalScore.toString(),
        "Combo" to "${combo}x",
        "Best" to "${maxCombo}x",
        "Hit / Miss" to "$hits / $misses",
        "BPM" to bpm.toInt().toString()
    )

    override fun instructions(): List<String> = listOf(
        "Move a tracked hand through a note as it crosses the pink line",
        "Or look at the note and pull the Cardboard trigger",
        "Twelve misses ends the run"
    )

    override fun isGameOver() = over
    override fun score() = totalScore
}

// =====================================================================================
// CUBE CASCADE — spatial block stacker
// =====================================================================================

/**
 * Blocks fall into a 3D well; clear full layers to score. Piece shapes are
 * MetaPort originals, generated from a seeded bag.
 */
class CubeCascade : GameBase() {
    override val id = "cube-cascade"
    override val title = "Cube Cascade"
    override val tagline = "Stack and clear layers in a 3D well."
    override val category = "Puzzle"
    override val accentIndex = 0

    private val cols = 6
    private val rows = 16
    private val cell = 0.26f
    private val grid = IntArray(cols * rows)

    private var piece = IntArray(8)
    private var pieceX = 2
    private var pieceY = rows - 2
    private var pieceRot = 0
    private var dropTimer = 0f
    private var dropInterval = 0.75f
    private var lines = 0
    private var totalScore = 0
    private var over = false
    private var moveCooldown = 0f

    private val shapes = arrayOf(
        intArrayOf(0, 0, 1, 0),                       // I (vertical pair)
        intArrayOf(0, 0, 0, 1),                       // L
        intArrayOf(0, 1, 1, 0),                       // S
        intArrayOf(0, 0, 1, 1),                       // O
        intArrayOf(1, 0, 0, 1)                        // Z
    )
    private val shapeColors = arrayOf(
        floatArrayOf(0.2f, 0.9f, 1f), floatArrayOf(1f, 0.6f, 0.2f),
        floatArrayOf(0.4f, 1f, 0.5f), floatArrayOf(1f, 0.3f, 0.7f),
        floatArrayOf(0.6f, 0.45f, 1f)
    )
    private var shapeIndex = 0
    private val cellMat = Material().apply { roughness = 0.35f; metallic = 0.2f }
    private val ghostMat = Material().apply { blend = Material.BLEND_ALPHA; depthWrite = false }

    override fun onEnter(ctx: GameContext) {
        grid.fill(0)
        lines = 0; totalScore = 0; over = false
        dropInterval = 0.75f
        newPiece()
    }

    override fun onExit(ctx: GameContext) {}

    private fun newPiece() {
        shapeIndex = rng.nextInt(shapes.size)
        piece = shapes[shapeIndex].copyOf()
        pieceX = cols / 2 - 1
        pieceY = rows - 2
        pieceRot = 0
        if (collides(pieceX, pieceY, piece)) over = true
    }

    private fun cellIndex(shape: IntArray, i: Int): Pair<Int, Int> {
        // Two horizontal cells, offset vertically by the encoded bits.
        val dx = i
        val dy = if (shape[i] == 1) 1 else 0
        return dx to dy
    }

    private fun collides(px: Int, py: Int, shape: IntArray): Boolean {
        for (i in 0 until 4) {
            val (dx, dy) = cellIndex(shape, i)
            val cx = px + dx
            val cy = py + dy
            if (cx < 0 || cx >= cols || cy < 0) return true
            if (cy < rows && grid[cy * cols + cx] != 0) return true
        }
        return false
    }

    private fun lock() {
        for (i in 0 until 4) {
            val (dx, dy) = cellIndex(piece, i)
            val cx = pieceX + dx
            val cy = pieceY + dy
            if (cy in 0 until rows && cx in 0 until cols) grid[cy * cols + cx] = shapeIndex + 1
        }
        clearLines()
        newPiece()
        dropInterval = (0.75f - lines * 0.012f).coerceAtLeast(0.18f)
    }

    private fun clearLines() {
        var cleared = 0
        var y = 0
        while (y < rows) {
            var full = true
            for (x in 0 until cols) if (grid[y * cols + x] == 0) { full = false; break }
            if (full) {
                cleared++
                for (yy in y until rows - 1) {
                    System.arraycopy(grid, (yy + 1) * cols, grid, yy * cols, cols)
                }
                for (x in 0 until cols) grid[(rows - 1) * cols + x] = 0
            } else y++
        }
        if (cleared > 0) {
            lines += cleared
            totalScore += cleared * cleared * 250
        }
    }

    override fun update(ctx: GameContext) {
        if (over) { if (ctx.firePressed()) restart(ctx); return }
        moveCooldown -= ctx.dt

        // Horizontal steering follows the gaze ray at the well's plane.
        if (moveCooldown <= 0f) {
            val gx = ctx.gazeOrigin[0] + ctx.gazeDir[0] * 2.2f
            val target = ((gx + cols * cell * 0.5f) / cell).toInt() - 1
            val clamped = target.coerceIn(0, cols - 2)
            if (clamped != pieceX && !collides(clamped, pieceY, piece)) {
                pieceX = clamped
                moveCooldown = 0.09f
            }
        }
        if (ctx.firePressed()) {
            val rotated = piece.copyOf()
            for (i in 0 until 4) rotated[i] = if (rotated[i] == 1) 0 else 1
            if (!collides(pieceX, pieceY, rotated)) piece = rotated
        }

        dropTimer += ctx.dt
        if (dropTimer >= dropInterval) {
            dropTimer = 0f
            if (!collides(pieceX, pieceY - 1, piece)) {
                pieceY--
            } else {
                lock()
                ctx.sfx(com.metaport.xr.audio.Sfx.GRAB, 0.6f)
            }
        }
    }

    override fun render(ctx: GameContext) {
        val cube = GameAssets.cube ?: return
        val ox = -cols * cell * 0.5f + cell * 0.5f
        val oy = 0.2f

        // Backdrop well.
        ghostMat.rgb(0.1f, 0.16f, 0.28f, 0.35f)
        composeS(0f, oy + rows * cell * 0.5f, -2.3f, cols * cell + 0.1f, rows * cell + 0.1f, 0.04f)
        ctx.scene.push(cube, ghostMat, model, Scene.LAYER_TRANSPARENT)

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val v = grid[y * cols + x]
                if (v == 0) continue
                val c = shapeColors[(v - 1).coerceIn(0, shapeColors.size - 1)]
                cellMat.rgb(c[0], c[1], c[2])
                cellMat.emissive(c[0], c[1], c[2], 0.35f)
                compose(ox + x * cell, oy + y * cell, -2.2f, cell * 0.92f)
                ctx.scene.push(cube, cellMat, model, Scene.LAYER_OPAQUE)
            }
        }

        val c = shapeColors[shapeIndex]
        for (i in 0 until 4) {
            val (dx, dy) = cellIndex(piece, i)
            cellMat.rgb(c[0] * 1.2f, c[1] * 1.2f, c[2] * 1.2f)
            cellMat.emissive(c[0], c[1], c[2], 1.1f)
            compose(ox + (pieceX + dx) * cell, oy + (pieceY + dy) * cell, -2.1f, cell * 0.95f)
            ctx.scene.push(cube, cellMat, model, Scene.LAYER_OPAQUE)
        }
    }

    override fun hud() = listOf(
        "Score" to totalScore.toString(),
        "Lines" to lines.toString(),
        "Speed" to ("%.2fs".format(dropInterval))
    )

    override fun instructions() = listOf(
        "Look left/right to steer the falling piece",
        "Pull the trigger to flip it",
        "Fill a full row to clear it"
    )

    override fun isGameOver() = over
    override fun score() = totalScore
}

// =====================================================================================
// ORB KEEPER — 3D pong
// =====================================================================================

/** Spatial paddle duel: your paddle tracks your hand or gaze. */
class OrbKeeper : GameBase() {
    override val id = "orb-keeper"
    override val title = "Orb Keeper"
    override val tagline = "Defend the near goal in a zero-gravity duel."
    override val category = "Arcade"
    override val accentIndex = 4

    private var bx = 0f; private var by = 1.3f; private var bz = -4f
    private var vx = 1.4f; private var vy = 0.6f; private var vz = 6f
    private var px = 0f; private var py = 1.3f
    private var ax = 0f; private var ay = 1.3f
    private var playerScore = 0
    private var aiScore = 0
    private val nearZ = -0.9f
    private val farZ = -8f
    private val bound = 1.6f

    private val orbMat = Material().apply { emissive = 1.4f; roughness = 0.2f }
    private val padMat = Material().apply { emissive = 0.7f; blend = Material.BLEND_ALPHA; depthWrite = false }
    private val trail = ArrayList<FloatArray>()
    private var over = false

    override fun onEnter(ctx: GameContext) {
        bx = 0f; by = 1.3f; bz = -4f
        vx = if (rng.nextBoolean()) 1.4f else -1.4f; vy = 0.5f; vz = 6f
        playerScore = 0; aiScore = 0; over = false
        trail.clear()
    }

    override fun onExit(ctx: GameContext) { trail.clear() }

    override fun update(ctx: GameContext) {
        if (over) { if (ctx.firePressed()) restart(ctx); return }

        // Player paddle follows the hand, or the gaze ray on the paddle plane.
        val h = ctx.rightHand?.takeIf { it.visible } ?: ctx.leftHand?.takeIf { it.visible }
        val tx: Float; val ty: Float
        if (h != null) {
            tx = h.palmPosition[0]; ty = h.palmPosition[1]
        } else {
            val t = (nearZ - ctx.gazeOrigin[2]) / ctx.gazeDir[2].let { if (kotlin.math.abs(it) < 1e-4f) -1f else it }
            tx = ctx.gazeOrigin[0] + ctx.gazeDir[0] * t
            ty = ctx.gazeOrigin[1] + ctx.gazeDir[1] * t
        }
        px += (tx.coerceIn(-bound, bound) - px) * (1f - kotlin.math.exp(-16f * ctx.dt))
        py += (ty.coerceIn(0.4f, 2.4f) - py) * (1f - kotlin.math.exp(-16f * ctx.dt))

        // AI paddle tracks the orb with a speed limit.
        val aiSpeed = 2.6f + playerScore * 0.12f
        ax += (bx - ax).coerceIn(-aiSpeed * ctx.dt, aiSpeed * ctx.dt)
        ay += (by - ay).coerceIn(-aiSpeed * ctx.dt, aiSpeed * ctx.dt)

        bx += vx * ctx.dt; by += vy * ctx.dt; bz += vz * ctx.dt

        if (bx < -bound || bx > bound) { vx = -vx; bx = bx.coerceIn(-bound, bound); ctx.sfx(com.metaport.xr.audio.Sfx.TICK, 0.5f) }
        if (by < 0.5f || by > 2.6f) { vy = -vy; by = by.coerceIn(0.5f, 2.6f); ctx.sfx(com.metaport.xr.audio.Sfx.TICK, 0.5f) }

        if (bz > nearZ && vz > 0f) {
            if (dist(bx, by, 0f, px, py, 0f) < 0.45f) {
                vz = -kotlin.math.abs(vz) * 1.04f
                vx += (bx - px) * 3.2f
                vy += (by - py) * 2.4f
                ctx.sfx(com.metaport.xr.audio.Sfx.HIT, 0.9f)
                playerScore += 10
            } else if (bz > nearZ + 0.5f) {
                aiScore++
                reset(true)
            }
        }
        if (bz < farZ && vz < 0f) {
            if (dist(bx, by, 0f, ax, ay, 0f) < 0.5f) {
                vz = kotlin.math.abs(vz) * 1.03f
                vx += (bx - ax) * 2.6f
                ctx.sfx(com.metaport.xr.audio.Sfx.HIT, 0.6f)
            } else if (bz < farZ - 0.5f) {
                playerScore += 100
                reset(false)
            }
        }
        vz = vz.coerceIn(-14f, 14f)

        trail.add(floatArrayOf(bx, by, bz, 1f))
        if (trail.size > 26) trail.removeAt(0)
        for (t in trail) t[3] -= ctx.dt * 2.6f

        if (aiScore >= 7 || playerScore >= 1000) over = true
    }

    private fun reset(towardsPlayer: Boolean) {
        bx = 0f; by = 1.3f; bz = -4f
        vx = if (rng.nextBoolean()) 1.2f else -1.2f
        vy = 0.4f
        vz = if (towardsPlayer) 6f else -6f
        trail.clear()
    }

    override fun render(ctx: GameContext) {
        val orb = GameAssets.orb ?: return
        orbMat.rgb(1f, 0.9f, 0.4f)
        orbMat.emissive(1f, 0.8f, 0.3f, 2f)
        compose(bx, by, bz, 0.22f)
        ctx.scene.push(orb, orbMat, model, Scene.LAYER_OPAQUE)

        for (t in trail) {
            if (t[3] <= 0f) continue
            orbMat.rgb(1f, 0.6f, 0.2f, t[3] * 0.35f)
            orbMat.blend = com.metaport.xr.scene.Material.BLEND_ADDITIVE
            compose(t[0], t[1], t[2], 0.2f * t[3])
            ctx.scene.push(orb, orbMat, model, Scene.LAYER_TRANSPARENT)
        }
        orbMat.blend = com.metaport.xr.scene.Material.BLEND_OPAQUE

        padMat.rgb(0.2f, 0.9f, 1f, 0.7f)
        compose(px, py, nearZ, 0.42f)
        ctx.scene.push(GameAssets.pad, padMat, model, Scene.LAYER_TRANSPARENT)

        padMat.rgb(1f, 0.3f, 0.6f, 0.6f)
        compose(ax, ay, farZ, 0.46f)
        ctx.scene.push(GameAssets.pad, padMat, model, Scene.LAYER_TRANSPARENT)
    }

    override fun hud() = listOf(
        "You" to playerScore.toString(),
        "Rival" to aiScore.toString(),
        "Speed" to ("%.1f".format(kotlin.math.abs(vz)))
    )

    override fun instructions() = listOf(
        "Move your open hand (or your gaze) to slide the cyan paddle",
        "Angle your return by hitting off-centre",
        "First to 7 conceded goals loses"
    )

    override fun isGameOver() = over
    override fun score() = playerScore
}
