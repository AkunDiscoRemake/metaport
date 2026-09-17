package com.metaport.xr.env

import com.metaport.xr.core.gl.Mesh
import com.metaport.xr.render.Pipeline
import com.metaport.xr.scene.Material
import com.metaport.xr.scene.Scene

/** Per-frame context handed to an environment while it renders. */
class EnvContext {
    var time = 0f
    var dt = 0f
    var headX = 0f; var headY = 0f; var headZ = 0f
    /** 0 = pure VR, 1 = full camera passthrough mixed reality. */
    var mrBlend = 0f
    var lightIntensity = 1f
    var quality = 1f
}

/**
 * A spatial environment. Environments are procedural: geometry is generated at
 * load time, lighting parameters are pushed into the shared pipeline, and the
 * sky is drawn by a shader so there is never a texture to ship.
 */
abstract class Environment3D {
    abstract val id: String
    abstract val name: String
    abstract val description: String
    abstract val accent: FloatArray

    /** Sky shader mode (see Shaders.SKY_FS). */
    abstract val skyMode: Int

    var skyTint = floatArrayOf(0.3f, 0.2f, 0.6f)
    var skyHorizon = floatArrayOf(0.1f, 0.3f, 0.5f)
    var fogColor = floatArrayOf(0.02f, 0.02f, 0.06f)
    var fogDensity = 0.006f
    var ambientTop = floatArrayOf(0.16f, 0.18f, 0.28f)
    var ambientBottom = floatArrayOf(0.04f, 0.04f, 0.07f)
    var lights = floatArrayOf(0.4f, -0.75f, -0.5f, -0.6f, -0.4f, 0.65f, 0.1f, -0.2f, 0.95f)
    var lightColors = floatArrayOf(0.95f, 0.92f, 1.0f, 0.28f, 0.35f, 0.75f, 0.5f, 0.25f, 0.45f)

    /** Grid floor parameters; set [gridEnabled] false for outdoor scenes. */
    var gridEnabled = true
    var gridCell = 1.0f
    var gridThickness = 0.5f
    var gridFade = 0.035f
    var gridPulse = 0.35f
    var gridColor = floatArrayOf(0.14f, 0.88f, 1f, 0.5f)
    var gridAccent = floatArrayOf(1f, 0.24f, 0.65f)
    var gridY = 0f

    var built = false

    open fun build() { built = true }
    open fun update(dt: Float, time: Float) {}
    open fun render(scene: Scene, pipeline: Pipeline, ctx: EnvContext) {}
    open fun release() {}

    fun applyLighting(pipeline: Pipeline) {
        pipeline.setLights(lights, lightColors)
        pipeline.setAmbient(
            ambientTop[0], ambientTop[1], ambientTop[2],
            ambientBottom[0], ambientBottom[1], ambientBottom[2]
        )
        pipeline.setFog(fogColor[0], fogColor[1], fogColor[2], fogDensity)
    }

    protected fun drawGrid(scene: Scene, pipeline: Pipeline, ctx: EnvContext) {
        if (!gridEnabled) return
        com.metaport.xr.core.math.Mat4.compose(
            gridModel, ctx.headX, gridY, ctx.headZ, 0f, 0f, 0f, 200f, 1f, 200f
        )
        pipeline.drawGrid(
            gridModel, currentView, currentProj,
            gridColor[0], gridColor[1], gridColor[2], gridColor[3],
            gridAccent[0], gridAccent[1], gridAccent[2],
            gridCell, gridThickness, gridFade, gridPulse
        )
    }

    private val gridModel = FloatArray(16)
    var currentView = FloatArray(16)
    var currentProj = FloatArray(16)

    fun infoMap(): LinkedHashMap<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m["id"] = id
        m["name"] = name
        m["description"] = description
        m["sky"] = skyMode
        return m
    }
}

/** Registry of every environment MetaPort ships. */
object Environments {
    private val list = ArrayList<Environment3D>()
    var current: Environment3D? = null
        private set

    fun register(e: Environment3D) {
        list.add(e)
    }

    fun all(): List<Environment3D> = list

    fun byId(id: String): Environment3D? = list.firstOrNull { it.id == id }

    fun select(id: String): Environment3D? {
        val e = byId(id) ?: return null
        if (current !== e) {
            current?.release()
            current = e
            if (!e.built) e.build()
        }
        return e
    }

    fun releaseAll() {
        for (e in list) e.release()
        list.clear()
        current = null
    }
}

/** Shared material factory so environments do not allocate per frame. */
object EnvMaterials {
    val opaque = Material().apply { roughness = 0.6f }
    val metal = Material().apply { metallic = 0.85f; roughness = 0.3f }
    val glow = Material().apply { blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 1.5f }
    val alpha = Material().apply { blend = Material.BLEND_ALPHA; depthWrite = false }
    val terrain = Material().apply { roughness = 0.95f }

    private val pool = ArrayList<Material>()

    fun make(): Material = Material().also { pool.add(it) }

    /** Per-instance material clone used when a prop needs its own colour. */
    fun clone(base: Material, r: Float, g: Float, b: Float, a: Float = 1f): Material =
        Material().apply { copyFrom(base); rgb(r, g, b, a) }

    fun release() {
        pool.clear()
    }
}
