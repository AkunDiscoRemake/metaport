package com.metaport.xr.render

import com.metaport.xr.ar.ArCoreHost
import com.metaport.xr.ar.HandModel
import com.metaport.xr.core.gl.Mesh
import com.metaport.xr.core.gl.MeshGen
import com.metaport.xr.core.math.Mat4
import com.metaport.xr.scene.Material
import com.metaport.xr.scene.Scene
import kotlin.math.sqrt

/** Draws tracked hands as a glowing capsule skeleton plus palm plate. */
class HandRenderer {
    private var bone: Mesh? = null
    private var joint: Mesh? = null
    private var palm: Mesh? = null

    private val boneMat = Material().apply {
        blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 1.3f
    }
    private val jointMat = Material().apply {
        blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 1.8f
    }
    private val palmMat = Material().apply {
        blend = Material.BLEND_ALPHA; depthWrite = false; emissive = 0.5f; roughness = 0.4f
    }
    private val model = FloatArray(16)

    var visible = true
    var showSkeleton = true
    var showPalm = true

    fun init() {
        bone = MeshGen.cylinder(0.5f, 1f, 8, false)
        joint = MeshGen.sphere(0.5f, 10, 8)
        palm = MeshGen.curvedPanel(1f, 1f, 0.4f, segX = 8, segY = 3)
    }

    fun render(scene: Scene, hand: HandModel?, accent: FloatArray, time: Float) {
        if (!visible || hand == null || !hand.visible) return
        val b = bone ?: return
        val j = joint ?: return

        val pulse = 0.85f + 0.15f * kotlin.math.sin(time * 4f)
        val confidence = hand.confidence.coerceIn(0.25f, 1f)

        boneMat.rgb(accent[0] * pulse, accent[1] * pulse, accent[2] * pulse, 0.85f * confidence)
        jointMat.rgb(1f, 1f, 1f, 0.9f * confidence)

        if (showSkeleton) {
            for (pair in HandModel.BONES) {
                val a = pair[0] * 3
                val c = pair[1] * 3
                val isFinger = pair[1] != HandModel.WRIST && pair[0] != HandModel.WRIST
                val radius = if (isFinger) 0.0055f else 0.0085f
                alignY(
                    model,
                    hand.joints[a], hand.joints[a + 1], hand.joints[a + 2],
                    hand.joints[c], hand.joints[c + 1], hand.joints[c + 2],
                    radius
                )
                scene.push(b, boneMat, model, Scene.LAYER_TRANSPARENT)
            }
            for (i in 0 until HandModel.JOINT_COUNT) {
                val o = i * 3
                val isTip = HandModel.FINGER_TIPS.contains(i)
                val s = if (isTip) 0.017f else if (i == HandModel.WRIST) 0.022f else 0.012f
                Mat4.compose(
                    model, hand.joints[o], hand.joints[o + 1], hand.joints[o + 2],
                    0f, 0f, 0f, s, s, s
                )
                scene.push(j, jointMat, model, Scene.LAYER_TRANSPARENT)
            }
        }

        if (showPalm && palm != null) {
            palmMat.rgb(accent[0] * 0.25f, accent[1] * 0.25f, accent[2] * 0.3f, 0.22f * confidence)
            val s = hand.palmSize
            val m = hand.palmMatrix
            System.arraycopy(m, 0, model, 0, 16)
            model[0] *= s * 1.1f; model[1] *= s * 1.1f; model[2] *= s * 1.1f
            model[4] *= s * 1.25f; model[5] *= s * 1.25f; model[6] *= s * 1.25f
            model[8] *= s; model[9] *= s; model[10] *= s
            scene.push(palm, palmMat, model, Scene.LAYER_TRANSPARENT)
        }
    }

    /** Builds a matrix whose +Y axis runs from a to b, scaled by `radius`. */
    private fun alignY(out: FloatArray, ax: Float, ay: Float, az: Float,
                       bx: Float, by: Float, bz: Float, radius: Float) {
        var dx = bx - ax; var dy = by - ay; var dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-6f) {
            Mat4.compose(out, ax, ay, az, 0f, 0f, 0f, radius, radius, radius)
            return
        }
        dx /= len; dy /= len; dz /= len
        // Arbitrary perpendicular axis.
        var ux = 0f; var uy = 1f; var uz = 0f
        if (kotlin.math.abs(dy) > 0.95f) { ux = 1f; uy = 0f; uz = 0f }
        var zx = uy * dz - uz * dy
        var zy = uz * dx - ux * dz
        var zz = ux * dy - uy * dx
        var l = sqrt(zx * zx + zy * zy + zz * zz)
        if (l < 1e-6f) { zx = 1f; zy = 0f; zz = 0f; l = 1f }
        zx /= l; zy /= l; zz /= l
        val xx = dy * zz - dz * zy
        val xy = dz * zx - dx * zz
        val xz = dx * zy - dy * zx
        val mx = (ax + bx) * 0.5f
        val my = (ay + by) * 0.5f
        val mz = (az + bz) * 0.5f
        out[0] = xx * radius; out[1] = xy * radius; out[2] = xz * radius; out[3] = 0f
        out[4] = dx * len; out[5] = dy * len; out[6] = dz * len; out[7] = 0f
        out[8] = zx * radius; out[9] = zy * radius; out[10] = zz * radius; out[11] = 0f
        out[12] = mx; out[13] = my; out[14] = mz; out[15] = 1f
    }

    fun release() {
        bone?.release(); joint?.release(); palm?.release()
        bone = null; joint = null; palm = null
    }
}

/**
 * Mixed reality layer: renders the ARCore camera image as the background and
 * overlays the tracked plane geometry so the user can see what the SLAM system
 * has mapped.
 */
class MixedRealityLayer {
    private var planeMesh: Mesh? = null
    private var cornerMesh: Mesh? = null
    private val planeMat = Material().apply {
        blend = Material.BLEND_ALPHA; depthWrite = false; emissive = 0.7f; roughness = 0.5f; cullBack = false
    }
    private val cornerMat = Material().apply {
        blend = Material.BLEND_ADDITIVE; depthWrite = false; unlit = true; unlitMode = 1; glow = 1.6f
    }
    private val model = FloatArray(16)

    var showPlanes = true
    var planeOpacity = 0.28f

    fun init() {
        planeMesh = MeshGen.quadXZ(1f, 1f)
        cornerMesh = MeshGen.sphere(0.5f, 10, 8)
    }

    /** Draws the camera feed as a fullscreen background using ARCore's UV mapping. */
    fun renderBackground(pipeline: Pipeline, cameraTexture: Int, uvs: FloatArray,
                         tint: FloatArray, brightness: Float, contrast: Float,
                         vignette: Float, scanline: Float, time: Float) {
        if (cameraTexture == 0) return
        val p = pipeline.passthrough
        p.use()
        com.metaport.xr.scene.GlState.reset()
        com.metaport.xr.scene.GlState.blend(Material.BLEND_OPAQUE)
        com.metaport.xr.scene.GlState.depthTest(false)
        com.metaport.xr.scene.GlState.depthWrite(false)
        com.metaport.xr.scene.GlState.cullBack(false)

        android.opengl.GLES30.glActiveTexture(android.opengl.GLES30.GL_TEXTURE0)
        android.opengl.GLES30.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        p.setInt("uCameraTex", 0)
        p.setVec3("uTint", tint[0], tint[1], tint[2])
        p.setFloat("uBrightness", brightness)
        p.setFloat("uContrast", contrast)
        p.setFloat("uVignette", vignette)
        p.setFloat("uScanline", scanline)
        p.setFloat("uTime", time)

        // ARCore supplies four corner UVs for a fullscreen quad; upload them and draw.
        dynamicQuad(pipeline, uvs)
    }

    private var quad: com.metaport.xr.core.gl.DynamicMesh? = null
    private val qb = com.metaport.xr.core.gl.MeshBuilder(4)

    /** Uploads the ARCore-computed camera quad (NDC corners -> texture UVs). */
    private fun dynamicQuad(pipeline: Pipeline, uvs: FloatArray) {
        val q = quad ?: com.metaport.xr.core.gl.DynamicMesh(4).also { quad = it }
        qb.reset()
        qb.v(-1f, -1f, 0f, 0f, 0f, 1f, uvs[0], uvs[1])
        qb.v(-1f, 1f, 0f, 0f, 0f, 1f, uvs[2], uvs[3])
        qb.v(1f, -1f, 0f, 0f, 0f, 1f, uvs[4], uvs[5])
        qb.v(1f, 1f, 0f, 0f, 0f, 1f, uvs[6], uvs[7])
        qb.tri(0, 2, 3)
        qb.tri(0, 3, 1)
        q.upload(qb)
        val ident = Mat4.IDENTITY
        pipeline.passthrough.setMat4("uMVP", ident)
        pipeline.passthrough.setMat4("uModel", ident)
        q.draw()
    }

    /** Draws tracked planes as translucent quads with glowing edges. */
    fun renderPlanes(scene: Scene, ar: ArCoreHost, time: Float) {
        if (!showPlanes) return
        val mesh = planeMesh ?: return
        for (p in ar.planes) {
            if (!p.tracked) continue
            val ex = p.extentX.coerceIn(0.1f, 40f)
            val ez = p.extentZ.coerceIn(0.1f, 40f)
            val c = if (p.isWall) floatArrayOf(1f, 0.4f, 0.7f) else floatArrayOf(0.2f, 0.9f, 1f)
            planeMat.rgb(c[0] * 0.35f, c[1] * 0.35f, c[2] * 0.35f, planeOpacity)
            planeMat.emissive(c[0], c[1], c[2], 0.6f)
            Mat4.compose(model, p.center[12], p.center[13], p.center[14], 0f, 0f, 0f, ex, 1f, ez)
            scene.push(mesh, planeMat, model, Scene.LAYER_TRANSPARENT)

            // Corner markers so plane bounds are readable in stereo.
            cornerMat.rgb(c[0], c[1], c[2], 0.8f)
            for (sx in intArrayOf(-1, 1)) {
                for (sz in intArrayOf(-1, 1)) {
                    Mat4.compose(
                        model,
                        p.center[12] + sx * ex * 0.5f,
                        p.center[13] + 0.005f,
                        p.center[14] + sz * ez * 0.5f,
                        0f, 0f, 0f, 0.035f, 0.035f, 0.035f
                    )
                    scene.push(cornerMesh, cornerMat, model, Scene.LAYER_TRANSPARENT)
                }
            }
        }
    }

    fun release() {
        planeMesh?.release(); cornerMesh?.release(); quad?.release()
        planeMesh = null; cornerMesh = null; quad = null
    }
}
