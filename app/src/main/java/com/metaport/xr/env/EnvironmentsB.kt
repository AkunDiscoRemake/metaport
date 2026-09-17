package com.metaport.xr.env

import com.metaport.xr.core.gl.Mesh
import com.metaport.xr.core.gl.MeshGen
import com.metaport.xr.core.math.Mathf
import com.metaport.xr.core.math.Mat4
import com.metaport.xr.render.Pipeline
import com.metaport.xr.scene.Material
import com.metaport.xr.scene.Scene

/** Temperate valley: generated terrain, procedural trees and drifting fireflies. */
class ForestValley : Environment3D() {
    override val id = "forest-valley"
    override val name = "Forest Valley"
    override val description = "Sunlit clearing with generated terrain and drifting fireflies."
    override val accent = floatArrayOf(0.29f, 1f, 0.62f)
    override val skyMode = 3

    private var terrain: Mesh? = null
    private var trunk: Mesh? = null
    private var canopy: Mesh? = null
    private var mote: Mesh? = null
    private val terrainMat = Material()
    private val trunkMat = Material()
    private val canopyMat = Material()
    private val moteMat = Material()
    private val model = FloatArray(16)

    private data class Tree(val x: Float, val z: Float, val s: Float, val yaw: Float, val tint: Float)
    private val trees = ArrayList<Tree>()
    private val motes = ArrayList<FloatArray>()

    override fun build() {
        skyTint = floatArrayOf(1f, 0.92f, 0.7f)
        skyHorizon = floatArrayOf(0.55f, 0.7f, 0.45f)
        fogColor = floatArrayOf(0.36f, 0.45f, 0.4f)
        fogDensity = 0.014f
        ambientTop = floatArrayOf(0.45f, 0.52f, 0.5f)
        ambientBottom = floatArrayOf(0.12f, 0.16f, 0.1f)
        lightColors = floatArrayOf(1f, 0.95f, 0.8f, 0.3f, 0.4f, 0.55f, 0.5f, 0.45f, 0.3f)
        gridEnabled = false

        terrain = MeshGen.terrain(80f, 40, 1.4f, seed = 21, octaves = 5)
        trunk = MeshGen.cylinder(1f, 1f, 8, false)
        canopy = MeshGen.sphere(1f, 12, 8)
        mote = MeshGen.sphere(1f, 8, 6)

        terrainMat.copyFrom(EnvMaterials.terrain).rgb(0.2f, 0.34f, 0.16f)
        trunkMat.copyFrom(EnvMaterials.opaque).rgb(0.28f, 0.19f, 0.12f)
        trunkMat.roughness = 0.9f
        canopyMat.copyFrom(EnvMaterials.opaque).rgb(0.16f, 0.42f, 0.18f)
        canopyMat.roughness = 0.85f
        moteMat.copyFrom(EnvMaterials.glow).rgb(1f, 0.9f, 0.4f, 0.9f)

        val rnd = java.util.Random(31337)
        for (i in 0 until 70) {
            val ang = rnd.nextFloat() * Mathf.PI * 2f
            val rad = 7f + rnd.nextFloat() * 30f
            trees.add(
                Tree(
                    kotlin.math.cos(ang) * rad, kotlin.math.sin(ang) * rad,
                    0.7f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 360f, rnd.nextFloat()
                )
            )
        }
        for (i in 0 until 40) {
            motes.add(floatArrayOf(
                -14f + rnd.nextFloat() * 28f, 0.4f + rnd.nextFloat() * 3.5f,
                -14f + rnd.nextFloat() * 28f, rnd.nextFloat() * 6.28f, 0.4f + rnd.nextFloat() * 1.2f
            ))
        }
        built = true
    }

    override fun render(scene: Scene, pipeline: Pipeline, ctx: EnvContext) {
        val t = terrain ?: return
        Mat4.compose(model, 0f, -0.4f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        scene.push(t, terrainMat, model, Scene.LAYER_OPAQUE)

        for (tr in trees) {
            val h = 4.5f * tr.s
            Mat4.compose(model, tr.x, h * 0.5f - 0.4f, tr.z, tr.yaw * Mathf.DEG2RAD, 0f, 0f,
                0.32f * tr.s, h, 0.32f * tr.s)
            scene.push(trunk, trunkMat, model, Scene.LAYER_OPAQUE)

            val cr = 2.1f * tr.s
            canopyMat.rgb(0.12f + tr.tint * 0.1f, 0.34f + tr.tint * 0.16f, 0.14f + tr.tint * 0.06f)
            Mat4.compose(model, tr.x, h - 0.4f + cr * 0.4f, tr.z, tr.yaw * Mathf.DEG2RAD, 0f, 0f,
                cr, cr * 1.25f, cr)
            scene.push(canopy, canopyMat, model, Scene.LAYER_OPAQUE)
        }

        for ((i, m) in motes.withIndex()) {
            val ph = m[3] + ctx.time * m[4]
            val x = m[0] + kotlin.math.sin(ph) * 1.6f
            val y = m[1] + kotlin.math.sin(ph * 1.7f) * 0.5f
            val z = m[2] + kotlin.math.cos(ph * 0.8f) * 1.6f
            val pulse = 0.4f + 0.6f * (0.5f + 0.5f * kotlin.math.sin(ctx.time * 3f + i))
            moteMat.rgb(1f * pulse, 0.85f * pulse, 0.35f * pulse, 0.85f)
            Mat4.compose(model, x, y, z, 0f, 0f, 0f, 0.035f, 0.035f, 0.035f)
            scene.push(mote, moteMat, model, Scene.LAYER_TRANSPARENT)
        }
    }

    override fun release() {
        terrain?.release(); terrain = null
        trunk?.release(); trunk = null
        canopy?.release(); canopy = null
        mote?.release(); mote = null
        trees.clear(); motes.clear()
        built = false
    }
}

/** Desert oasis: dune terrain, palms and a reflective water disc. */
class DesertOasis : Environment3D() {
    override val id = "desert-oasis"
    override val name = "Desert Oasis"
    override val description = "Dune field with palms and a reflective pool at the centre."
    override val accent = floatArrayOf(1f, 0.76f, 0.29f)
    override val skyMode = 4

    private var dunes: Mesh? = null
    private var water: Mesh? = null
    private var trunk: Mesh? = null
    private var frond: Mesh? = null
    private val duneMat = Material()
    private val waterMat = Material()
    private val trunkMat = Material()
    private val frondMat = Material()
    private val model = FloatArray(16)
    private val palms = ArrayList<FloatArray>()

    override fun build() {
        skyTint = floatArrayOf(1f, 0.95f, 0.75f)
        skyHorizon = floatArrayOf(1f, 0.6f, 0.3f)
        fogColor = floatArrayOf(0.85f, 0.68f, 0.45f)
        fogDensity = 0.012f
        ambientTop = floatArrayOf(0.7f, 0.6f, 0.42f)
        ambientBottom = floatArrayOf(0.25f, 0.18f, 0.1f)
        lightColors = floatArrayOf(1f, 0.9f, 0.7f, 0.35f, 0.4f, 0.6f, 0.6f, 0.4f, 0.2f)
        gridEnabled = false

        dunes = MeshGen.terrain(90f, 36, 2.6f, seed = 55, octaves = 4)
        water = MeshGen.ring(0.2f, 4.5f, 48)
        trunk = MeshGen.cylinder(1f, 1f, 10, false)
        frond = MeshGen.box(1f, 1f, 1f)

        duneMat.copyFrom(EnvMaterials.terrain).rgb(0.86f, 0.7f, 0.45f)
        waterMat.copyFrom(EnvMaterials.alpha).rgb(0.15f, 0.55f, 0.7f, 0.72f)
        waterMat.roughness = 0.05f
        waterMat.metallic = 0.4f
        trunkMat.copyFrom(EnvMaterials.opaque).rgb(0.4f, 0.3f, 0.2f)
        frondMat.copyFrom(EnvMaterials.opaque).rgb(0.18f, 0.45f, 0.2f)

        val rnd = java.util.Random(808)
        for (i in 0 until 14) {
            val ang = rnd.nextFloat() * Mathf.PI * 2f
            val rad = 5.5f + rnd.nextFloat() * 16f
            palms.add(floatArrayOf(kotlin.math.cos(ang) * rad, kotlin.math.sin(ang) * rad,
                0.8f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 360f))
        }
        built = true
    }

    override fun render(scene: Scene, pipeline: Pipeline, ctx: EnvContext) {
        val d = dunes ?: return
        Mat4.compose(model, 0f, -1.1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        scene.push(d, duneMat, model, Scene.LAYER_OPAQUE)

        val shimmer = 0.65f + 0.1f * kotlin.math.sin(ctx.time * 1.3f)
        waterMat.rgb(0.15f, 0.55f * shimmer + 0.2f, 0.7f, 0.7f)
        Mat4.compose(model, 0f, -0.55f, 0f, 0f, ctx.time * 3f * Mathf.DEG2RAD, 0f, 1f, 1f, 1f)
        scene.push(water, waterMat, model, Scene.LAYER_TRANSPARENT)

        for (p in palms) {
            val h = 5f * p[2]
            Mat4.compose(model, p[0], h * 0.5f - 0.6f, p[1], p[3] * Mathf.DEG2RAD, 0f, 0.06f,
                0.28f * p[2], h, 0.28f * p[2])
            scene.push(trunk, trunkMat, model, Scene.LAYER_OPAQUE)
            for (f in 0 until 7) {
                val a = f / 7f * Mathf.PI * 2f + p[3] * Mathf.DEG2RAD
                val sway = kotlin.math.sin(ctx.time * 1.1f + f) * 0.05f
                Mat4.compose(model, p[0] + kotlin.math.cos(a) * 1.4f, h - 0.5f + sway,
                    p[1] + kotlin.math.sin(a) * 1.4f, -a, 0f, 0.35f, 2.6f, 0.08f, 0.5f)
                scene.push(frond, frondMat, model, Scene.LAYER_OPAQUE)
            }
        }
    }

    override fun release() {
        dunes?.release(); dunes = null
        water?.release(); water = null
        trunk?.release(); trunk = null
        frond?.release(); frond = null
        palms.clear()
        built = false
    }
}

/** Traditional training hall: tatami floor, lanterns and a torii frame. */
class Dojo : Environment3D() {
    override val id = "dojo"
    override val name = "Paper Dojo"
    override val description = "Lantern-lit training hall for rhythm and precision games."
    override val accent = floatArrayOf(1f, 0.35f, 0.25f)
    override val skyMode = 5

    private var floor: Mesh? = null
    private var mat: Mesh? = null
    private var post: Mesh? = null
    private var lantern: Mesh? = null
    private var beam: Mesh? = null
    private val floorMat = Material()
    private val matMat = Material()
    private val postMat = Material()
    private val lanternMat = Material()
    private val beamMat = Material()
    private val model = FloatArray(16)

    override fun build() {
        skyTint = floatArrayOf(1f, 0.55f, 0.25f)
        skyHorizon = floatArrayOf(0.35f, 0.15f, 0.1f)
        fogColor = floatArrayOf(0.07f, 0.04f, 0.03f)
        fogDensity = 0.02f
        ambientTop = floatArrayOf(0.3f, 0.2f, 0.14f)
        ambientBottom = floatArrayOf(0.08f, 0.05f, 0.03f)
        lightColors = floatArrayOf(1f, 0.7f, 0.45f, 0.4f, 0.25f, 0.2f, 0.8f, 0.5f, 0.3f)

        gridEnabled = false

        floor = MeshGen.box(1f, 1f, 1f)
        mat = MeshGen.box(1f, 1f, 1f)
        post = MeshGen.cylinder(1f, 1f, 12, false)
        lantern = MeshGen.sphere(1f, 14, 10)
        beam = MeshGen.box(1f, 1f, 1f)

        floorMat.copyFrom(EnvMaterials.opaque).rgb(0.32f, 0.2f, 0.12f)
        floorMat.roughness = 0.7f
        matMat.copyFrom(EnvMaterials.opaque).rgb(0.55f, 0.58f, 0.3f)
        postMat.copyFrom(EnvMaterials.opaque).rgb(0.45f, 0.16f, 0.13f)
        lanternMat.copyFrom(EnvMaterials.glow).rgb(1f, 0.65f, 0.3f, 0.9f)
        beamMat.copyFrom(EnvMaterials.opaque).rgb(0.25f, 0.14f, 0.09f)
        built = true
    }

    override fun render(scene: Scene, pipeline: Pipeline, ctx: EnvContext) {
        val f = floor ?: return
        Mat4.compose(model, 0f, -0.1f, 0f, 0f, 0f, 0f, 18f, 0.2f, 18f)
        scene.push(f, floorMat, model, Scene.LAYER_OPAQUE)

        for (i in -3..3) {
            for (j in -3..3) {
                Mat4.compose(model, i * 1.9f, 0.012f, j * 1.9f, 0f, 0f, 0f, 1.75f, 0.03f, 1.75f)
                scene.push(mat, matMat, model, Scene.LAYER_OPAQUE)
            }
        }

        // Corner posts + lanterns.
        for (sx in intArrayOf(-1, 1)) {
            for (sz in intArrayOf(-1, 1)) {
                val x = sx * 5.5f; val z = sz * 5.5f
                Mat4.compose(model, x, 2.5f, z, 0f, 0f, 0f, 0.28f, 5f, 0.28f)
                scene.push(post, postMat, model, Scene.LAYER_OPAQUE)
                val pulse = 0.75f + 0.25f * kotlin.math.sin(ctx.time * 2f + x + z)
                lanternMat.rgb(1f * pulse, 0.6f * pulse, 0.25f * pulse, 0.85f)
                Mat4.compose(model, x, 4.1f, z, 0f, 0f, 0f, 0.35f, 0.5f, 0.35f)
                scene.push(lantern, lanternMat, model, Scene.LAYER_TRANSPARENT)
            }
        }

        // Torii frame at the far end.
        Mat4.compose(model, 0f, 4.6f, -7f, 0f, 0f, 0f, 8f, 0.45f, 0.45f)
        scene.push(beam, postMat, model, Scene.LAYER_OPAQUE)
        Mat4.compose(model, 0f, 3.4f, -7f, 0f, 0f, 0f, 6.6f, 0.32f, 0.32f)
        scene.push(beam, postMat, model, Scene.LAYER_OPAQUE)
        for (sx in intArrayOf(-1, 1)) {
            Mat4.compose(model, sx * 3.2f, 2.3f, -7f, 0f, 0f, 0f, 0.4f, 4.6f, 0.4f)
            scene.push(post, postMat, model, Scene.LAYER_OPAQUE)
        }
    }

    override fun release() {
        floor?.release(); floor = null
        mat?.release(); mat = null
        post?.release(); post = null
        lantern?.release(); lantern = null
        beam?.release(); beam = null
        built = false
    }
}

/**
 * Mixed reality: the real room, seen through the phone camera, with MetaPort's
 * spatial grid and UI composited on top. The environment contributes almost no
 * geometry — ARCore's tracked planes are drawn by [MixedRealityLayer] instead.
 */
class PassthroughMR : Environment3D() {
    override val id = "passthrough"
    override val name = "Passthrough MR"
    override val description = "Your real room through the camera, with spatial content anchored in it."
    override val accent = floatArrayOf(0.14f, 0.88f, 1f)
    override val skyMode = 6

    var passthroughActive = true

    override fun build() {
        fogColor = floatArrayOf(0f, 0f, 0f)
        fogDensity = 0f
        ambientTop = floatArrayOf(0.55f, 0.55f, 0.58f)
        ambientBottom = floatArrayOf(0.25f, 0.25f, 0.28f)
        lightColors = floatArrayOf(1f, 1f, 1f, 0.3f, 0.3f, 0.35f, 0.2f, 0.2f, 0.25f)
        gridEnabled = true
        gridColor = floatArrayOf(0.14f, 0.9f, 1f, 0.4f)
        gridAccent = floatArrayOf(1f, 0.24f, 0.65f)
        gridCell = 0.5f
        gridThickness = 0.6f
        gridFade = 0.12f
        gridPulse = 0.5f
        built = true
    }

    override fun render(scene: Scene, pipeline: Pipeline, ctx: EnvContext) {
        drawGrid(scene, pipeline, ctx)
    }
}
