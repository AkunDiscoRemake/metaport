package com.metaport.xr.env

import com.metaport.xr.core.gl.Mesh
import com.metaport.xr.core.gl.MeshGen
import com.metaport.xr.core.math.Mathf
import com.metaport.xr.core.math.Mat4
import com.metaport.xr.render.Pipeline
import com.metaport.xr.scene.Material
import com.metaport.xr.scene.Scene

/**
 * Deep-space arena: drifting crystal shards, glowing orbital rings and a slow
 * nebula. The default home environment.
 */
class NebulaVoid : Environment3D() {
    override val id = "nebula-void"
    override val name = "Nebula Void"
    override val description = "Deep-space arena with drifting crystal shards and orbital rings."
    override val accent = floatArrayOf(0.48f, 0.36f, 1f)
    override val skyMode = 0

    private var shards: Mesh? = null
    private var ringBig: Mesh? = null
    private var ringSmall: Mesh? = null
    private var core: Mesh? = null
    private val shardMat = Material()
    private val ringMat = Material()
    private val coreMat = Material()
    private val model = FloatArray(16)

    private val shardsData = ArrayList<FloatArray>()

    override fun build() {
        skyTint = floatArrayOf(0.45f, 0.25f, 0.9f)
        skyHorizon = floatArrayOf(0.1f, 0.4f, 0.7f)
        fogColor = floatArrayOf(0.03f, 0.02f, 0.09f)
        fogDensity = 0.0075f
        ambientTop = floatArrayOf(0.22f, 0.18f, 0.42f)
        ambientBottom = floatArrayOf(0.05f, 0.03f, 0.12f)
        lightColors = floatArrayOf(0.85f, 0.8f, 1f, 0.5f, 0.3f, 0.85f, 0.2f, 0.55f, 0.9f)

        gridColor = floatArrayOf(0.45f, 0.3f, 1f, 0.42f)
        gridAccent = floatArrayOf(0.1f, 0.85f, 1f)
        gridCell = 1.2f
        gridFade = 0.03f
        gridY = -0.02f

        shards = MeshGen.box(1f, 1f, 1f)
        ringBig = MeshGen.torus(1f, 0.012f, 64, 8)
        ringSmall = MeshGen.torus(1f, 0.008f, 48, 6)
        core = MeshGen.sphere(1f, 24, 16)

        shardMat.copyFrom(EnvMaterials.metal).rgb(0.55f, 0.5f, 0.9f)
        shardMat.emissive(0.4f, 0.3f, 1f, 0.25f)
        shardMat.roughness = 0.25f
        ringMat.copyFrom(EnvMaterials.glow).rgb(0.2f, 0.85f, 1f, 0.85f)
        coreMat.copyFrom(EnvMaterials.glow).rgb(0.7f, 0.45f, 1f, 0.6f)

        val rnd = java.util.Random(90210)
        for (i in 0 until 46) {
            val ang = rnd.nextFloat() * Mathf.PI * 2f
            val rad = 6f + rnd.nextFloat() * 22f
            val arr = FloatArray(9)
            arr[0] = kotlin.math.cos(ang) * rad
            arr[1] = -3f + rnd.nextFloat() * 12f
            arr[2] = kotlin.math.sin(ang) * rad
            arr[3] = 0.25f + rnd.nextFloat() * 1.6f            // scale
            arr[4] = rnd.nextFloat() * 360f                     // yaw
            arr[5] = rnd.nextFloat() * 360f                     // pitch
            arr[6] = 0.02f + rnd.nextFloat() * 0.09f            // spin
            arr[7] = 0.6f + rnd.nextFloat() * 1.8f              // stretch
            arr[8] = rnd.nextFloat()                            // phase
            shardsData.add(arr)
        }
        built = true
    }

    override fun update(dt: Float, time: Float) {}

    override fun render(scene: Scene, pipeline: Pipeline, ctx: EnvContext) {
        val mesh = shards ?: return
        for (s in shardsData) {
            val t = ctx.time * s[6] + s[8] * 6.28f
            val bob = kotlin.math.sin(ctx.time * 0.35f + s[8] * 9f) * 0.4f
            val yaw = (s[4] + t * 57.3f) * Mathf.DEG2RAD
            val pitch = (s[5] + t * 31.4f) * Mathf.DEG2RAD
            Mat4.compose(
                model, s[0], s[1] + bob, s[2], yaw, pitch, t * 0.4f,
                s[3], s[3] * s[7], s[3] * 0.8f
            )
            scene.push(mesh, shardMat, model, Scene.LAYER_OPAQUE)
        }

        // Orbital rings around the play space.
        Mat4.compose(model, ctx.headX, 1.4f, ctx.headZ, 0f, 0f, 0f, 9f, 9f, 9f)
        Mat4.multiply(model, 0, model, 0, tiltA)
        scene.push(ringBig, ringMat, model, Scene.LAYER_TRANSPARENT)

        Mat4.compose(model, ctx.headX, 1.4f, ctx.headZ, 0f, 0f, 0f, 13f, 13f, 13f)
        Mat4.multiply(model, 0, model, 0, tiltB)
        scene.push(ringSmall, ringMat, model, Scene.LAYER_TRANSPARENT)

        // Central light core far below, giving the void a focal point.
        Mat4.compose(model, ctx.headX, -14f, ctx.headZ - 6f, 0f, 0f, 0f, 6f, 6f, 6f)
        scene.push(core, coreMat, model, Scene.LAYER_TRANSPARENT)

        drawGrid(scene, pipeline, ctx)
    }

    private val tiltA = FloatArray(16).also {
        Mat4.compose(it, 0f, 0f, 0f, 0f, 0f, 0.42f, 1f, 1f, 1f)
    }
    private val tiltB = FloatArray(16).also {
        Mat4.compose(it, 0f, 0f, 0f, 0f, 0f, -0.31f, 1f, 1f, 1f)
    }

    override fun release() {
        shards?.release(); shards = null
        ringBig?.release(); ringBig = null
        ringSmall?.release(); ringSmall = null
        core?.release(); core = null
        shardsData.clear()
        built = false
    }
}

/**
 * Neon megacity: a reflective grid plane surrounded by extruded towers whose
 * window grids are generated from hashed noise.
 */
class CyberCity : Environment3D() {
    override val id = "cyber-city"
    override val name = "Neon District"
    override val description = "Rain-slick streets and holographic towers under a magenta sky."
    override val accent = floatArrayOf(1f, 0.24f, 0.65f)
    override val skyMode = 1

    private var tower: Mesh? = null
    private var windowBand: Mesh? = null
    private var spire: Mesh? = null
    private val towerMat = Material()
    private val windowMat = Material()
    private val spireMat = Material()
    private val model = FloatArray(16)
    private data class Tower(val x: Float, val z: Float, val w: Float, val h: Float,
                             val yaw: Float, val hue: Float, val phase: Float)
    private val towers = ArrayList<Tower>()

    override fun build() {
        skyTint = floatArrayOf(1f, 0.2f, 0.6f)
        skyHorizon = floatArrayOf(0.25f, 0.1f, 0.5f)
        fogColor = floatArrayOf(0.05f, 0.015f, 0.09f)
        fogDensity = 0.011f
        ambientTop = floatArrayOf(0.18f, 0.1f, 0.28f)
        ambientBottom = floatArrayOf(0.06f, 0.02f, 0.1f)
        lightColors = floatArrayOf(0.9f, 0.75f, 1f, 1f, 0.25f, 0.6f, 0.2f, 0.7f, 1f)

        gridColor = floatArrayOf(1f, 0.24f, 0.65f, 0.5f)
        gridAccent = floatArrayOf(0.14f, 0.9f, 1f)
        gridCell = 1.0f
        gridFade = 0.02f
        gridPulse = 0.6f

        tower = MeshGen.box(1f, 1f, 1f)
        windowBand = MeshGen.box(1f, 1f, 1f)
        spire = MeshGen.cylinder(1f, 1f, 8, false)

        towerMat.copyFrom(EnvMaterials.metal).rgb(0.09f, 0.08f, 0.16f)
        towerMat.roughness = 0.4f
        windowMat.copyFrom(EnvMaterials.glow).rgb(0.2f, 0.9f, 1f, 0.9f)
        spireMat.copyFrom(EnvMaterials.glow).rgb(1f, 0.25f, 0.6f, 0.8f)

        val rnd = java.util.Random(4242)
        for (i in 0 until 60) {
            val ang = rnd.nextFloat() * Mathf.PI * 2f
            val rad = 14f + rnd.nextFloat() * 46f
            towers.add(
                Tower(
                    kotlin.math.cos(ang) * rad,
                    kotlin.math.sin(ang) * rad,
                    2.5f + rnd.nextFloat() * 6f,
                    8f + rnd.nextFloat() * 44f,
                    rnd.nextFloat() * 90f,
                    rnd.nextFloat(),
                    rnd.nextFloat() * 6.28f
                )
            )
        }
        built = true
    }

    override fun render(scene: Scene, pipeline: Pipeline, ctx: EnvContext) {
        val t = tower ?: return
        for (tw in towers) {
            Mat4.compose(model, tw.x, tw.h * 0.5f, tw.z, tw.yaw * Mathf.DEG2RAD, 0f, 0f, tw.w, tw.h, tw.w)
            scene.push(t, towerMat, model, Scene.LAYER_OPAQUE)

            // Emissive window bands.
            val bands = (tw.h / 3.2f).toInt().coerceIn(2, 14)
            for (b in 0 until bands) {
                val y = 1.6f + b * 3.2f
                if (y > tw.h - 1f) break
                val flicker = 0.55f + 0.45f * Mathf.valueNoise2(b * 1.7f + tw.phase, ctx.time * 0.15f)
                val c = if (tw.hue > 0.5f) floatArrayOf(0.15f, 0.85f, 1f) else floatArrayOf(1f, 0.3f, 0.75f)
                windowMat.rgb(c[0] * flicker, c[1] * flicker, c[2] * flicker, 0.75f)
                Mat4.compose(
                    model, tw.x, y, tw.z, tw.yaw * Mathf.DEG2RAD, 0f, 0f,
                    tw.w * 1.01f, 0.55f, tw.w * 1.01f
                )
                scene.push(windowBand, windowMat, model, Scene.LAYER_TRANSPARENT)
            }

            // Antenna spire.
            Mat4.compose(model, tw.x, tw.h + 2.5f, tw.z, 0f, 0f, 0f, 0.12f, 5f, 0.12f)
            scene.push(spire, spireMat, model, Scene.LAYER_TRANSPARENT)
        }
        drawGrid(scene, pipeline, ctx)
    }

    override fun release() {
        tower?.release(); tower = null
        windowBand?.release(); windowBand = null
        spire?.release(); spire = null
        towers.clear()
        built = false
    }
}

/** Orbital station interior: deck plating, structural struts and a planet view. */
class SpaceStation : Environment3D() {
    override val id = "space-station"
    override val name = "Orbital Deck"
    override val description = "Pressurised deck ring with a live view of the planet below."
    override val accent = floatArrayOf(0.29f, 1f, 0.62f)
    override val skyMode = 2

    private var deck: Mesh? = null
    private var strut: Mesh? = null
    private var panel: Mesh? = null
    private var strip: Mesh? = null
    private val deckMat = Material()
    private val strutMat = Material()
    private val panelMat = Material()
    private val stripMat = Material()
    private val model = FloatArray(16)

    override fun build() {
        skyTint = floatArrayOf(0.35f, 0.7f, 1f)
        skyHorizon = floatArrayOf(0.05f, 0.12f, 0.22f)
        fogColor = floatArrayOf(0.01f, 0.015f, 0.03f)
        fogDensity = 0.008f
        ambientTop = floatArrayOf(0.14f, 0.18f, 0.24f)
        ambientBottom = floatArrayOf(0.03f, 0.05f, 0.08f)
        lightColors = floatArrayOf(0.9f, 0.95f, 1f, 0.35f, 0.6f, 0.45f, 0.6f, 0.4f, 0.2f)

        gridEnabled = false

        deck = MeshGen.terrain(60f, 24, 0.35f, seed = 7)
        strut = MeshGen.box(1f, 1f, 1f)
        panel = MeshGen.box(1f, 1f, 1f)
        strip = MeshGen.box(1f, 1f, 1f)

        deckMat.copyFrom(EnvMaterials.metal).rgb(0.16f, 0.18f, 0.21f)
        deckMat.roughness = 0.55f
        strutMat.copyFrom(EnvMaterials.metal).rgb(0.28f, 0.3f, 0.34f)
        strutMat.roughness = 0.35f
        panelMat.copyFrom(EnvMaterials.opaque).rgb(0.1f, 0.12f, 0.15f)
        panelMat.roughness = 0.8f
        stripMat.copyFrom(EnvMaterials.glow).rgb(0.29f, 1f, 0.62f, 0.85f)
        built = true
    }

    override fun render(scene: Scene, pipeline: Pipeline, ctx: EnvContext) {
        val d = deck ?: return
        Mat4.compose(model, 0f, -0.05f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        scene.push(d, deckMat, model, Scene.LAYER_OPAQUE)

        // Structural ribs around the user.
        for (i in 0 until 16) {
            val ang = i / 16f * Mathf.PI * 2f
            val r = 9f
            val x = kotlin.math.cos(ang) * r
            val z = kotlin.math.sin(ang) * r
            Mat4.compose(model, x, 3f, z, -ang, 0f, 0f, 0.5f, 6.5f, 0.5f)
            scene.push(strut, strutMat, model, Scene.LAYER_OPAQUE)
            Mat4.compose(model, x, 6.2f, z, -ang, 0f, 0f, 1.2f, 0.25f, 0.25f)
            scene.push(strut, strutMat, model, Scene.LAYER_OPAQUE)
            // Floor light strip
            Mat4.compose(model, x * 0.98f, 0.03f, z * 0.98f, -ang, 0f, 0f, 2.4f, 0.05f, 0.12f)
            val pulse = 0.6f + 0.4f * kotlin.math.sin(ctx.time * 1.6f + i)
            stripMat.rgb(0.29f * pulse, 1f * pulse, 0.62f * pulse, 0.9f)
            scene.push(strip, stripMat, model, Scene.LAYER_TRANSPARENT)
        }

        // Ceiling panels
        for (i in 0 until 8) {
            val ang = i / 8f * Mathf.PI * 2f + 0.2f
            Mat4.compose(
                model, kotlin.math.cos(ang) * 4.5f, 6.6f, kotlin.math.sin(ang) * 4.5f,
                -ang, 0f, 0f, 3.4f, 0.18f, 1.6f
            )
            scene.push(panel, panelMat, model, Scene.LAYER_OPAQUE)
        }
    }

    override fun release() {
        deck?.release(); deck = null
        strut?.release(); strut = null
        panel?.release(); panel = null
        strip?.release(); strip = null
        built = false
    }
}
