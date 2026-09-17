package com.metaport.xr.render

import android.opengl.GLES30
import com.metaport.xr.core.gl.FontAtlas
import com.metaport.xr.core.gl.FrameBuffer
import com.metaport.xr.core.gl.GlProgram
import com.metaport.xr.core.gl.Mesh
import com.metaport.xr.core.gl.MeshGen
import com.metaport.xr.core.gl.Shaders
import com.metaport.xr.core.math.Mat4
import com.metaport.xr.scene.GlState
import com.metaport.xr.scene.Material

/**
 * Owns every GL program, the shared lighting environment and the fullscreen
 * meshes used by the post passes. One instance per GL context.
 */
class Pipeline {
    lateinit var lit: GlProgram
    lateinit var unlit: GlProgram
    lateinit var ui: GlProgram
    lateinit var text: GlProgram
    lateinit var sky: GlProgram
    lateinit var grid: GlProgram
    lateinit var passthrough: GlProgram
    lateinit var distort: GlProgram

    lateinit var screenQuad: Mesh
    lateinit var skyDome: Mesh
    lateinit var gridPlane: Mesh

    var font: FontAtlas? = null

    // Lighting environment (world space, direction light travels towards).
    val lightDir = FloatArray(9)
    val lightColor = FloatArray(9)
    var ambTopR = 0.16f; var ambTopG = 0.18f; var ambTopB = 0.28f
    var ambBotR = 0.04f; var ambBotG = 0.04f; var ambBotB = 0.07f
    var fogR = 0.02f; var fogG = 0.02f; var fogB = 0.06f; var fogDensity = 0.006f

    var camX = 0f; var camY = 0f; var camZ = 0f
    var time = 0f

    private var currentProgram: GlProgram? = null
    private val viewProj = FloatArray(16)
    private val mvp = FloatArray(16)

    fun init() {
        lit = GlProgram(Shaders.LIT_VS, Shaders.LIT_FS, "lit")
        unlit = GlProgram(Shaders.UNLIT_VS, Shaders.UNLIT_FS, "unlit")
        ui = GlProgram(Shaders.UI_VS, Shaders.UI_FS, "ui")
        text = GlProgram(Shaders.TEXT_VS, Shaders.TEXT_FS, "text")
        sky = GlProgram(Shaders.SKY_VS, Shaders.SKY_FS, "sky")
        grid = GlProgram(Shaders.GRID_VS, Shaders.GRID_FS, "grid")
        passthrough = GlProgram(Shaders.PASSTHROUGH_VS, Shaders.PASSTHROUGH_FS, "passthrough")
        distort = GlProgram(Shaders.DISTORT_VS, Shaders.DISTORT_FS, "distort")

        screenQuad = MeshGen.screenQuad()
        skyDome = MeshGen.sphere(1f, 32, 20)
        gridPlane = MeshGen.quadXZ(1f, 1f)
        font = FontAtlas(bold = true)

        setLights(
            floatArrayOf(0.4f, -0.75f, -0.5f, -0.6f, -0.4f, 0.65f, 0.1f, -0.2f, 0.95f),
            floatArrayOf(0.95f, 0.92f, 1.0f, 0.28f, 0.35f, 0.75f, 0.5f, 0.25f, 0.45f)
        )
    }

    fun setLights(dirs: FloatArray, colors: FloatArray) {
        System.arraycopy(dirs, 0, lightDir, 0, 9)
        System.arraycopy(colors, 0, lightColor, 0, 9)
        // Normalise light directions.
        for (i in 0 until 3) {
            val o = i * 3
            val l = kotlin.math.sqrt(
                lightDir[o] * lightDir[o] + lightDir[o + 1] * lightDir[o + 1] + lightDir[o + 2] * lightDir[o + 2]
            )
            if (l > 1e-6f) {
                lightDir[o] /= l; lightDir[o + 1] /= l; lightDir[o + 2] /= l
            }
        }
    }

    fun setAmbient(topR: Float, topG: Float, topB: Float, botR: Float, botG: Float, botB: Float) {
        ambTopR = topR; ambTopG = topG; ambTopB = topB
        ambBotR = botR; ambBotG = botG; ambBotB = botB
    }

    fun setFog(r: Float, g: Float, b: Float, density: Float) {
        fogR = r; fogG = g; fogB = b; fogDensity = density
    }

    fun applyMaterial(m: Material) {
        val p = if (m.unlit) unlit else lit
        if (p !== currentProgram) {
            p.use()
            currentProgram = p
            // Shared uniforms for both programs.
            p.setVec3("uCamPos", camX, camY, camZ)
            p.setFloat("uTime", time)
            p.setVec4("uBaseColor", m.r, m.g, m.b, m.a)
        }
        p.setFloat("uAlphaMul", m.alphaMul)
        p.setVec4("uBaseColor", m.r, m.g, m.b, m.a)

        if (m.unlit) {
            p.setInt("uMode", m.unlitMode)
            p.setFloat("uGlow", m.glow)
        } else {
            p.setVec3("uEmissiveColor", m.emissiveR, m.emissiveG, m.emissiveB)
            p.setFloat("uEmissive", m.emissive)
            p.setFloat("uRoughness", m.roughness)
            p.setFloat("uMetallic", m.metallic)
            p.setFloat("uRimStrength", m.rim)
            p.setFloat("uRimPower", m.rimPower)
            p.setVec3Array("uLightDir", lightDir, 3)
            p.setVec3Array("uLightColor", lightColor, 3)
            p.setVec3("uAmbientTop", ambTopR, ambTopG, ambTopB)
            p.setVec3("uAmbientBottom", ambBotR, ambBotG, ambBotB)
            p.setVec4("uFog", fogR, fogG, fogB, fogDensity)
            p.setInt("uUseTex", if (m.texture != 0) 1 else 0)
            p.setFloat("uAlphaTex", if (m.useAlphaTex) 1f else 0f)
            if (m.texture != 0) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, m.texture)
                p.setInt("uTex", 0)
            }
        }

        GlState.blend(m.blend)
        GlState.depthWrite(m.depthWrite)
        GlState.depthTest(m.depthTest)
        GlState.cullBack(m.cullBack)
    }

    fun bindMatrices(mvp: FloatArray, model: FloatArray) {
        val p = currentProgram ?: return
        p.setMat4("uMVP", mvp)
        p.setMat4("uModel", model)
    }

    /** Prepares a view/projection pair; call before [Scene.render]. */
    fun setViewProjection(view: FloatArray, proj: FloatArray) {
        Mat4.multiply(viewProj, 0, proj, 0, view, 0)
    }

    val viewProjection: FloatArray get() = viewProj

    fun computeMvp(model: FloatArray, out: FloatArray) {
        Mat4.multiply(out, 0, viewProj, 0, model, 0)
    }

    // ------------------------------------------------------------------ sky dome
    fun drawSky(mode: Int, viewRotOnly: FloatArray, proj: FloatArray,
                tintR: Float, tintG: Float, tintB: Float,
                horR: Float, horG: Float, horB: Float, radius: Float = 400f) {
        sky.use()
        currentProgram = sky
        GlState.blend(Material.BLEND_OPAQUE)
        GlState.depthWrite(false)
        GlState.depthTest(true)
        GlState.cullBack(false)

        // Keep the dome centred on the camera, ignoring translation.
        val m = tmpModel
        Mat4.multiply(m, 0, viewRotOnly, 0, Mat4.IDENTITY, 0)
        m[12] = camX; m[13] = camY; m[14] = camZ
        m[0] *= radius; m[1] *= radius; m[2] *= radius
        m[4] *= radius; m[5] *= radius; m[6] *= radius
        m[8] *= radius; m[9] *= radius; m[10] *= radius

        Mat4.multiply(mvp, 0, proj, 0, viewRotOnly, 0)
        val modelNoTrans = tmpModel2
        System.arraycopy(m, 0, modelNoTrans, 0, 16)

        sky.setInt("uMode", mode)
        sky.setFloat("uTime", time)
        sky.setVec3("uTint", tintR, tintG, tintB)
        sky.setVec3("uHorizon", horR, horG, horB)
        sky.setMat4("uMVP", mvp)
        sky.setMat4("uModel", modelNoTrans)
        skyDome.draw()
        GlState.depthWrite(true)
        GlState.cullBack(true)
    }

    // ------------------------------------------------------------------ grid floor
    fun drawGrid(
        model: FloatArray, view: FloatArray, proj: FloatArray,
        lineR: Float, lineG: Float, lineB: Float, lineA: Float,
        accentR: Float, accentG: Float, accentB: Float,
        cell: Float, thickness: Float, fade: Float, pulse: Float
    ) {
        grid.use()
        currentProgram = grid
        GlState.blend(Material.BLEND_ALPHA)
        GlState.depthWrite(false)
        GlState.depthTest(true)
        GlState.cullBack(false)

        Mat4.multiply(mvp, 0, proj, 0, view, 0)
        Mat4.multiply(tmpMvp2, 0, mvp, 0, model, 0)

        grid.setMat4("uMVP", tmpMvp2)
        grid.setMat4("uModel", model)
        grid.setVec4("uLineColor", lineR, lineG, lineB, lineA)
        grid.setVec3("uLineColor2", accentR, accentG, accentB)
        grid.setFloat("uCell", cell)
        grid.setFloat("uThickness", thickness)
        grid.setFloat("uFade", fade)
        grid.setFloat("uPulse", pulse)
        grid.setFloat("uTime", time)
        grid.setVec3("uCamPos", camX, camY, camZ)
        gridPlane.draw()
        GlState.depthWrite(true)
        GlState.cullBack(true)
    }

    // ------------------------------------------------------------------ text pass
    fun beginText(atlasTexture: Int) {
        text.use()
        currentProgram = text
        GlState.blend(Material.BLEND_ALPHA)
        GlState.depthWrite(false)
        GlState.depthTest(true)
        GlState.cullBack(false)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasTexture)
        text.setInt("uAtlas", 0)
        text.setFloat("uAlphaMul", 1f)
        text.setFloat("uGlow", 0f)
    }

    fun drawTextMesh(mvp: FloatArray, r: Float, g: Float, b: Float, a: Float, glow: Float) {
        text.setMat4("uMVP", mvp)
        text.setVec4("uColor", r, g, b, a)
        text.setFloat("uGlow", glow)
    }

    fun endText() {
        GlState.depthWrite(true)
        GlState.cullBack(true)
    }

    fun release() {
        if (::lit.isInitialized) lit.release()
        if (::unlit.isInitialized) unlit.release()
        if (::ui.isInitialized) ui.release()
        if (::text.isInitialized) text.release()
        if (::sky.isInitialized) sky.release()
        if (::grid.isInitialized) grid.release()
        if (::passthrough.isInitialized) passthrough.release()
        if (::distort.isInitialized) distort.release()
        font?.release()
    }

    private val tmpModel = FloatArray(16)
    private val tmpModel2 = FloatArray(16)
    private val tmpMvp2 = FloatArray(16)
}

/**
 * Cardboard stereo pipeline: renders the scene twice into one wide offscreen
 * target, then presents it through Google's official viewer optics
 * (radial lens distortion + chromatic aberration correction).
 */
class StereoPipeline {
    private var fbo: FrameBuffer? = null
    var eyeWidth = 0
        private set
    var eyeHeight = 0
        private set

    fun ensureSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (eyeWidth == w && eyeHeight == h) return
        eyeWidth = w; eyeHeight = h
        fbo?.release()
        fbo = FrameBuffer(w * 2, h, withDepth = true)
    }

    val colorTexture: Int get() = fbo?.colorTex ?: 0

    fun beginFrame(clearR: Float, clearG: Float, clearB: Float) {
        val f = fbo ?: return
        f.bind()
        GLES30.glViewport(0, 0, f.width, f.height)
        GLES30.glClearColor(clearR, clearG, clearB, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }

    fun beginEye(eye: Int) {
        GLES30.glViewport(eye * eyeWidth, 0, eyeWidth, eyeHeight)
        GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
        GLES30.glScissor(eye * eyeWidth, 0, eyeWidth, eyeHeight)
    }

    fun endEye() {
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
    }

    fun endFrame() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /**
     * Presents both eyes through the lens distortion shader.
     * `enabled` = false bypasses distortion (flat/monoscopic debug view).
     */
    fun present(pipeline: Pipeline, profile: com.metaport.xr.stereo.ViewerProfile,
                screenWidth: Int, screenHeight: Int, enabled: Boolean) {
        val tex = colorTexture
        if (tex == 0) return
        val p = pipeline.distort
        p.use()
        GlState.reset()
        GlState.blend(Material.BLEND_OPAQUE)
        GlState.depthTest(false)
        GlState.depthWrite(false)
        GlState.cullBack(false)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        p.setInt("uScene", 0)
        p.setInt("uEnabled", if (enabled) 1 else 0)
        p.setFloat("uChroma", profile.chromaticAberration)
        p.setFloat("uVignette", profile.vignette)

        val eyeSizeU = 0.5f
        val profileScale = profile.distortionScale

        for (eye in 0..1) {
            val offsetX = eye * eyeSizeU
            // Lens centre inside the eye sub-rectangle, in 0..1 eye-local uv.
            val lensU = profile.lensCenterU(eye)
            val lensV = profile.lensCenterV

            val vpX = eye * (screenWidth / 2)
            GLES30.glViewport(vpX, 0, screenWidth / 2, screenHeight)
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
            GLES30.glScissor(vpX, 0, screenWidth / 2, screenHeight)

            p.setVec2("uLensCenter", lensU, lensV)
            p.setVec2("uScale", 1f / profileScale, 1f / profileScale)
            p.setVec2("uScaleIn", profileScale, profileScale)
            p.setVec2("uEyeOffset", offsetX, 0f)
            p.setVec2("uEyeSize", eyeSizeU, 1f)
            p.setVec2("uK", profile.distortionK1, profile.distortionK2)
            pipeline.screenQuad.draw()
        }
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GlState.depthTest(true)
        GlState.depthWrite(true)
        GlState.cullBack(true)
    }

    fun release() {
        fbo?.release()
        fbo = null
        eyeWidth = 0; eyeHeight = 0
    }
}
