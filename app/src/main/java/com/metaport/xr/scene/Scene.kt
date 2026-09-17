package com.metaport.xr.scene

import android.opengl.GLES30
import com.metaport.xr.core.gl.Mesh
import java.util.Arrays

/** Surface description for a draw call. Materials are pooled and reused. */
class Material {
    companion object {
        const val BLEND_OPAQUE = 0
        const val BLEND_ALPHA = 1
        const val BLEND_ADDITIVE = 2
    }

    var r = 1f; var g = 1f; var b = 1f; var a = 1f
    var emissiveR = 1f; var emissiveG = 1f; var emissiveB = 1f
    var emissive = 0f
    var roughness = 0.5f
    var metallic = 0f
    var rim = 0f
    var rimPower = 3f
    var alphaMul = 1f
    var texture = 0
    var useAlphaTex = false
    var blend = BLEND_OPAQUE
    var depthWrite = true
    var depthTest = true
    var cullBack = true
    var unlit = false
    var unlitMode = 0
    var glow = 0f

    fun rgb(r: Float, g: Float, b: Float, a: Float = 1f): Material {
        this.r = r; this.g = g; this.b = b; this.a = a
        return this
    }

    fun emissive(r: Float, g: Float, b: Float, strength: Float = 1f): Material {
        emissiveR = r; emissiveG = g; emissiveB = b; emissive = strength
        return this
    }

    fun copyFrom(o: Material): Material {
        r = o.r; g = o.g; b = o.b; a = o.a
        emissiveR = o.emissiveR; emissiveG = o.emissiveG; emissiveB = o.emissiveB
        emissive = o.emissive
        roughness = o.roughness; metallic = o.metallic
        rim = o.rim; rimPower = o.rimPower
        alphaMul = o.alphaMul
        texture = o.texture; useAlphaTex = o.useAlphaTex
        blend = o.blend; depthWrite = o.depthWrite; depthTest = o.depthTest
        cullBack = o.cullBack; unlit = o.unlit; unlitMode = o.unlitMode; glow = o.glow
        return this
    }
}

/** One pooled draw request. */
class DrawCmd {
    var mesh: Mesh? = null
    var material: Material? = null
    val model = FloatArray(16)
    var layer = 0
    var dist = 0f
    var valid = false
}

/**
 * Immediate-mode draw list. Everything MetaPort renders — environments, games,
 * hands, props — is submitted here each frame and dispatched in layer order:
 * 0 = opaque, 1 = transparent (depth sorted), 2 = overlay (no depth write).
 */
class Scene(private val poolSize: Int = 1024) {
    private val pool = Array(poolSize) { DrawCmd() }
    private var count = 0
    private var overflow = 0

    /** Layer constants. */
    companion object {
        const val LAYER_OPAQUE = 0
        const val LAYER_TRANSPARENT = 1
        const val LAYER_OVERLAY = 2
    }

    val drawCount: Int get() = count
    val overflowCount: Int get() = overflow

    fun clear() {
        for (i in 0 until count) {
            pool[i].mesh = null
            pool[i].material = null
            pool[i].valid = false
        }
        count = 0
        overflow = 0
    }

    fun push(mesh: Mesh?, material: Material?, model: FloatArray, layer: Int = LAYER_OPAQUE): DrawCmd? {
        val m = mesh ?: return null
        val mat = material ?: return null
        if (count >= poolSize) {
            overflow++
            return null
        }
        val c = pool[count++]
        c.mesh = m
        c.material = mat
        System.arraycopy(model, 0, c.model, 0, 16)
        c.layer = layer
        c.valid = true
        return c
    }

    /** Convenience for axis-aligned props placed at a position with uniform scale. */
    fun pushAt(
        mesh: Mesh?, material: Material?,
        x: Float, y: Float, z: Float,
        scale: Float = 1f,
        yawDeg: Float = 0f,
        layer: Int = LAYER_OPAQUE,
        scratch: FloatArray = tmpMat
    ): DrawCmd? {
        com.metaport.xr.core.math.Mat4.compose(
            scratch, x, y, z,
            yawDeg * com.metaport.xr.core.math.Mathf.DEG2RAD, 0f, 0f,
            scale, scale, scale
        )
        return push(mesh, material, scratch, layer)
    }

    private val mvp = FloatArray(16)
    private val order = IntArray(poolSize)

    fun render(pipeline: com.metaport.xr.render.Pipeline, view: FloatArray, proj: FloatArray) {
        for (layer in 0..2) {
            var n = 0
            for (i in 0 until count) {
                if (pool[i].layer == layer) {
                    order[n++] = i
                    val c = pool[i]
                    val dx = c.model[12] - pipeline.camX
                    val dy = c.model[13] - pipeline.camY
                    val dz = c.model[14] - pipeline.camZ
                    c.dist = dx * dx + dy * dy + dz * dz
                }
            }
            if (n == 0) continue

            if (layer == LAYER_TRANSPARENT) {
                // Back-to-front so additive/alpha content composites correctly.
                val keys = order.copyOf(n)
                val sorted = keys.sortedByDescending { pool[it].dist }
                for (i in sorted.indices) order[i] = sorted[i]
            }

            var currentMat: Material? = null
            for (k in 0 until n) {
                val c = pool[order[k]]
                val mat = c.material ?: continue
                if (mat !== currentMat) {
                    pipeline.applyMaterial(mat)
                    currentMat = mat
                }
                // mvp = (proj * view) * model, reusing the pair cached in the pipeline.
                com.metaport.xr.core.math.Mat4.multiply(mvp, 0, pipeline.viewProjection, 0, c.model, 0)
                pipeline.bindMatrices(mvp, c.model)
                c.mesh?.draw()
            }
        }
    }

    private val tmpMatHolder = FloatArray(16)
    private val tmpMat: FloatArray get() = tmpMatHolder
}

/** Small helper to reduce boilerplate when clearing GL state between passes. */
object GlState {
    var lastBlend = -1
    var lastDepthWrite = true
    var lastDepthTest = true
    var lastCull = true

    fun reset() {
        lastBlend = -1; lastDepthWrite = true; lastDepthTest = true; lastCull = true
    }

    fun blend(mode: Int) {
        if (lastBlend == mode) return
        lastBlend = mode
        when (mode) {
            Material.BLEND_OPAQUE -> GLES30.glDisable(GLES30.GL_BLEND)
            Material.BLEND_ALPHA -> {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFuncSeparate(
                    GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA,
                    GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA
                )
            }
            Material.BLEND_ADDITIVE -> {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFuncSeparate(
                    GLES30.GL_SRC_ALPHA, GLES30.GL_ONE,
                    GLES30.GL_ONE, GLES30.GL_ONE
                )
            }
        }
    }

    fun depthWrite(on: Boolean) {
        if (lastDepthWrite == on) return
        lastDepthWrite = on
        GLES30.glDepthMask(on)
    }

    fun depthTest(on: Boolean) {
        if (lastDepthTest == on) return
        lastDepthTest = on
        if (on) GLES30.glEnable(GLES30.GL_DEPTH_TEST) else GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    }

    fun cullBack(on: Boolean) {
        if (lastCull == on) return
        lastCull = on
        if (on) GLES30.glEnable(GLES30.GL_CULL_FACE) else GLES30.glDisable(GLES30.GL_CULL_FACE)
    }

    fun fill(arr: FloatArray, v: Float) = Arrays.fill(arr, v)
}
