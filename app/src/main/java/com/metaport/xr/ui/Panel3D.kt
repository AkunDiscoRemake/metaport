package com.metaport.xr.ui

import com.metaport.xr.core.gl.Mesh
import com.metaport.xr.core.gl.MeshGen
import com.metaport.xr.core.gl.TextMesh
import com.metaport.xr.core.math.Mat4
import com.metaport.xr.render.Pipeline
import com.metaport.xr.scene.GlState
import com.metaport.xr.scene.Material
import java.util.HashMap

/** Lazily built, cached UI geometry. MetaPort reuses one mesh per distinct size. */
object UiAssets {
    private val cache = HashMap<Long, Mesh>()
    private val curvedCache = HashMap<Long, Mesh>()
    var unitQuad: Mesh? = null
        private set
    var reticleRing: Mesh? = null
        private set
    var reticleDot: Mesh? = null
        private set

    fun init() {
        unitQuad = MeshGen.quadXY(1f, 1f)
        reticleRing = MeshGen.ring(0.85f, 1f, 48)
        reticleDot = MeshGen.sphere(1f, 14, 10)
    }

    fun buttonMesh(w: Float, h: Float): Mesh {
        val key = ((w * 2000).toInt().toLong() shl 32) or (h * 2000).toInt().toLong()
        cache[key]?.let { return it }
        // A single quad serves every button: the shader does the rounded corners,
        // and the model matrix supplies the size.
        val m = unitQuad ?: MeshGen.quadXY(1f, 1f).also { unitQuad = it }
        return m
    }

    /** Curved backing panel mesh, cached per size + curvature bucket. */
    fun panelMesh(w: Float, h: Float, curvature: Float): Mesh {
        val key = (((w * 100).toInt().toLong() shl 40)) or
            ((h * 100).toInt().toLong() shl 20) or
            ((curvature * 100).toInt().toLong() and 0xFFFFF)
        curvedCache[key]?.let { return it }
        val m = MeshGen.curvedPanel(w, h, curvature, segX = 20, segY = 3)
        curvedCache[key] = m
        return m
    }

    fun release() {
        for (m in cache.values) m.release()
        for (m in curvedCache.values) m.release()
        cache.clear(); curvedCache.clear()
        unitQuad?.release(); unitQuad = null
        reticleRing?.release(); reticleRing = null
        reticleDot?.release(); reticleDot = null
    }
}

/**
 * A floating spatial window. Panels are curved so their whole surface sits at a
 * constant optical distance from the eye — the single most important trick for
 * comfortable text in a Cardboard viewer.
 */
class Panel3D(
    var title: String = "",
    var width: Float = 1.1f,
    var height: Float = 0.8f,
    var curvature: Float = Theme.DEFAULT_CURVATURE
) {
    var posX = 0f; var posY = 0f; var posZ = -1.6f
    var yaw = 0f; var pitch = 0f; var roll = 0f
    var visible = true
    var accentIndex = Theme.CYAN
    var showHeader = true
    var fill: FloatArray = Theme.panelFill.clone()
    var opacity = 1f
    var radius = Theme.DEFAULT_RADIUS
    var border = Theme.DEFAULT_BORDER

    /** Entrance animation state. */
    var appear = 0f
    var appearSpeed = 4.5f

    val widgets = ArrayList<Widget>()

    val worldMatrix = FloatArray(16)
    val right = FloatArray(3)
    val up = FloatArray(3)
    val normal = FloatArray(3)

    private var titleMesh: TextMesh? = null
    private var subMesh: TextMesh? = null
    var subtitle: String? = null

    /** Depth offset applied to widgets so text sits proud of the glass. */
    var widgetZ = 0.001f

    fun add(w: Widget): Widget {
        widgets.add(w)
        return w
    }

    fun addAt(w: Widget, x: Float, y: Float): Widget {
        w.x = x; w.y = y
        widgets.add(w)
        return w
    }

    fun clear() {
        for (w in widgets) {
            if (w is Label) w.release()
        }
        widgets.clear()
    }

    fun updateTransform() {
        Mat4.compose(worldMatrix, posX, posY, posZ, yaw, pitch, roll, 1f, 1f, 1f)
        right[0] = worldMatrix[0]; right[1] = worldMatrix[1]; right[2] = worldMatrix[2]
        up[0] = worldMatrix[4]; up[1] = worldMatrix[5]; up[2] = worldMatrix[6]
        normal[0] = worldMatrix[8]; normal[1] = worldMatrix[9]; normal[2] = worldMatrix[10]
    }

    /** Converts a world point into panel-local metres (x right, y up). */
    fun toLocal(wx: Float, wy: Float, wz: Float, out: FloatArray) {
        val dx = wx - posX
        val dy = wy - posY
        val dz = wz - posZ
        out[0] = dx * right[0] + dy * right[1] + dz * right[2]
        out[1] = dx * up[0] + dy * up[1] + dz * up[2]
        out[2] = dx * normal[0] + dy * normal[1] + dz * normal[2]
    }

    /** Ray/plane intersection against the panel plane; returns distance or -1. */
    fun intersectRay(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float): Float {
        val denom = dx * normal[0] + dy * normal[1] + dz * normal[2]
        if (kotlin.math.abs(denom) < 1e-6f) return -1f
        val t = ((posX - ox) * normal[0] + (posY - oy) * normal[1] + (posZ - oz) * normal[2]) / denom
        if (t <= 0.01f || t > 60f) return -1f
        return t
    }

    fun update(dt: Float) {
        if (visible && appear < 1f) appear = kotlin.math.min(1f, appear + dt * appearSpeed)
        if (!visible && appear > 0f) appear = kotlin.math.max(0f, appear - dt * appearSpeed * 1.6f)
        for (w in widgets) if (w.visible) w.update(dt)
    }

    fun render(ctx: UiRenderContext, pipeline: Pipeline) {
        if (appear <= 0.001f) return
        ctx.pipeline = pipeline
        ctx.panelMatrix = worldMatrix
        ctx.globalAlpha = opacity * appear

        val acc = Theme.accent(accentIndex)
        val s = 0.92f + 0.08f * appear

        // Slight scale-in around the panel centre.
        val scaled = ctx.scratch
        System.arraycopy(worldMatrix, 0, scaled, 0, 16)
        for (i in 0..10) scaled[i] *= s

        val mvp = FloatArray(16)
        Mat4.multiply(mvp, 0, pipeline.viewProjection, 0, scaled, 0)

        // Glass body.
        pipeline.ui.use()
        GlState.blend(Material.BLEND_ALPHA)
        GlState.depthWrite(false)
        GlState.depthTest(true)
        GlState.cullBack(false)
        pipeline.ui.setMat4("uMVP", mvp)
        pipeline.ui.setMat4("uModel", scaled)
        pipeline.ui.setVec2("uSize", width, height)
        pipeline.ui.setFloat("uRadius", radius * kotlin.math.max(width, height) / 1.2f)
        pipeline.ui.setFloat("uBorder", border)
        pipeline.ui.setVec4("uFillColor", fill[0], fill[1], fill[2], fill[3] * ctx.globalAlpha)
        pipeline.ui.setVec4("uEdgeColor", acc[0], acc[1], acc[2], 0.85f * ctx.globalAlpha)
        pipeline.ui.setFloat("uOpacity", (0.55f + 0.45f * appear) * opacity)
        pipeline.ui.setFloat("uHover", 0f)
        pipeline.ui.setFloat("uActive", 0f)
        pipeline.ui.setFloat("uTime", ctx.time)
        pipeline.ui.setFloat("uGradient", 0.35f)
        pipeline.ui.setVec3("uCamPos", ctx.camX, ctx.camY, ctx.camZ)
        UiAssets.panelMesh(width, height, curvature).draw()

        // Header bar + title.
        if (showHeader) {
            val headerH = 0.062f
            val hy = height * 0.5f - headerH * 0.5f - 0.012f
            Mat4.compose(scaled, 0f, hy, widgetZ, 0f, 0f, 0f, headerH, width * 0.98f, 1f, )
            // Build in panel space then to world.
            val local = FloatArray(16)
            Mat4.compose(local, 0f, hy, widgetZ, 0f, 0f, 0f, width * 0.98f, headerH, 1f)
            Mat4.multiply(scaled, 0, worldMatrix, 0, local, 0)
            Mat4.multiply(mvp, 0, pipeline.viewProjection, 0, scaled, 0)
            pipeline.ui.setMat4("uMVP", mvp)
            pipeline.ui.setMat4("uModel", scaled)
            pipeline.ui.setVec2("uSize", width * 0.98f, headerH)
            pipeline.ui.setFloat("uRadius", 0.018f)
            pipeline.ui.setFloat("uBorder", 0.0018f)
            pipeline.ui.setVec4("uFillColor", acc[0] * 0.12f, acc[1] * 0.12f, acc[2] * 0.12f, 0.55f * ctx.globalAlpha)
            pipeline.ui.setVec4("uEdgeColor", acc[0], acc[1], acc[2], 0.5f * ctx.globalAlpha)
            pipeline.ui.setFloat("uOpacity", 0.75f * opacity * appear)
            UiAssets.buttonMesh(width * 0.98f, headerH).draw()

            val font = pipeline.font
            if (font != null) {
                pipeline.beginText(font.texture)
                val tm = titleMesh ?: TextMesh(font, Theme.TEXT_H2).also { titleMesh = it }
                tm.size = Theme.TEXT_H2
                tm.align = com.metaport.xr.core.gl.FontAtlas.ALIGN_LEFT
                tm.color = floatArrayOf(acc[0] * 0.35f + 0.6f, acc[1] * 0.35f + 0.6f, acc[2] * 0.35f + 0.6f, 1f)
                tm.letterSpacing = 0.012f
                tm.set(title.uppercase())
                Mat4.compose(local, -width * 0.47f, hy + Theme.TEXT_H2 * 0.38f, widgetZ + 0.003f, 0f, 0f, 0f, 1f, 1f, 1f)
                Mat4.multiply(scaled, 0, worldMatrix, 0, local, 0)
                Mat4.multiply(mvp, 0, pipeline.viewProjection, 0, scaled, 0)
                pipeline.drawTextMesh(mvp, tm.color[0], tm.color[1], tm.color[2], ctx.globalAlpha, 0.25f)
                tm.draw()
                pipeline.endText()
            }
        }

        val sub = subtitle
        if (sub != null) {
            val font = pipeline.font
            if (font != null) {
                pipeline.beginText(font.texture)
                val sm = subMesh ?: TextMesh(font, Theme.TEXT_TINY).also { subMesh = it }
                sm.size = Theme.TEXT_TINY
                sm.align = com.metaport.xr.core.gl.FontAtlas.ALIGN_LEFT
                sm.color = Theme.textSecondary
                sm.wrapWidth = width * 0.92f
                sm.maxLines = 3
                sm.set(sub)
                val local = FloatArray(16)
                Mat4.compose(
                    local, -width * 0.46f, height * 0.5f - 0.09f, widgetZ + 0.003f,
                    0f, 0f, 0f, 1f, 1f, 1f
                )
                Mat4.multiply(scaled, 0, worldMatrix, 0, local, 0)
                Mat4.multiply(mvp, 0, pipeline.viewProjection, 0, scaled, 0)
                pipeline.drawTextMesh(mvp, 0.62f, 0.72f, 0.86f, ctx.globalAlpha * 0.9f, 0f)
                sm.draw()
                pipeline.endText()
            }
        }

        // Widgets, in reverse so later-added widgets draw on top.
        ctx.depthOffset = widgetZ
        for (i in widgets.indices.reversed()) {
            val w = widgets[i]
            if (!w.visible) continue
            if (w.y + w.height * 0.5f < -height * 0.5f || w.y - w.height * 0.5f > height * 0.5f) continue
            w.render(ctx)
        }
        ctx.depthOffset = 0f
    }

    fun release() {
        titleMesh?.release(); titleMesh = null
        subMesh?.release(); subMesh = null
        for (w in widgets) if (w is Label) w.release()
    }
}
