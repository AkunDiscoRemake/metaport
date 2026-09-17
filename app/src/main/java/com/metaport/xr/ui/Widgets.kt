package com.metaport.xr.ui

import com.metaport.xr.core.gl.FontAtlas

/**
 * MetaPort visual language: dark glass panels, neon accent edges, deep spatial
 * layering. Everything is procedural — no image assets.
 */
object Theme {
    // Accents
    const val CYAN = 0
    const val MAGENTA = 1
    const val VIOLET = 2
    const val AMBER = 3
    const val LIME = 4
    const val ICE = 5

    val accents = arrayOf(
        floatArrayOf(0.14f, 0.88f, 1.00f),   // cyan
        floatArrayOf(1.00f, 0.24f, 0.65f),   // magenta
        floatArrayOf(0.48f, 0.36f, 1.00f),   // violet
        floatArrayOf(1.00f, 0.76f, 0.29f),   // amber
        floatArrayOf(0.29f, 1.00f, 0.62f),   // lime
        floatArrayOf(0.72f, 0.86f, 1.00f)    // ice
    )

    fun accent(i: Int): FloatArray = accents[i.coerceIn(0, accents.size - 1)]

    val panelFill = floatArrayOf(0.035f, 0.055f, 0.115f, 0.62f)
    val panelFillStrong = floatArrayOf(0.05f, 0.08f, 0.16f, 0.82f)
    val panelEdge = floatArrayOf(0.14f, 0.88f, 1.0f, 0.9f)
    val textPrimary = floatArrayOf(0.93f, 0.97f, 1.0f, 1f)
    val textSecondary = floatArrayOf(0.62f, 0.72f, 0.86f, 1f)
    val textDim = floatArrayOf(0.42f, 0.5f, 0.62f, 1f)
    val danger = floatArrayOf(1f, 0.32f, 0.38f)
    val success = floatArrayOf(0.29f, 1f, 0.62f)

    // Panel geometry
    const val DEFAULT_RADIUS = 0.035f
    const val DEFAULT_BORDER = 0.0035f
    const val DEFAULT_CURVATURE = 0.30f

    // Typography, metres
    const val TEXT_TITLE = 0.115f
    const val TEXT_H1 = 0.075f
    const val TEXT_H2 = 0.052f
    const val TEXT_BODY = 0.038f
    const val TEXT_SMALL = 0.028f
    const val TEXT_TINY = 0.021f

    // Motion
    const val HOVER_SPEED = 14f
    const val PRESS_SPEED = 26f
    const val DWELL_SECONDS = 1.05f
}

/** Base class for everything that can be placed on a spatial panel. */
abstract class Widget {
    /** Local position in metres, origin at the panel centre, +Y up, +X right. */
    var x = 0f
    var y = 0f
    var width = 0.2f
    var height = 0.08f
    var visible = true
    var enabled = true
    var alpha = 1f
    var hover = 0f
    var active = 0f
    var accentIndex = Theme.CYAN
    var tag: String = ""

    /** Set when the reticle or a hand ray is over this widget. */
    var focused = false
    var focusedPrev = false

    open fun update(dt: Float) {
        val target = if (focused && enabled) 1f else 0f
        hover += (target - hover) * (1f - kotlin.math.exp(-Theme.HOVER_SPEED * dt))
        val aTarget = if (active > 0f) 1f else 0f
        active += (aTarget - active) * (1f - kotlin.math.exp(-Theme.PRESS_SPEED * dt))
        if (active > 0.001f && aTarget == 0f) active = 0f
    }

    /** Local hit test; `lx`,`ly` are metres relative to the panel centre. */
    open fun hitTest(lx: Float, ly: Float): Boolean {
        if (!visible || !enabled) return false
        return lx >= x - width * 0.5f && lx <= x + width * 0.5f &&
            ly >= y - height * 0.5f && ly <= y + height * 0.5f
    }

    open fun onSelect() {}
    open fun onHoverEnter() {}
    open fun onHoverExit() {}
    open fun onFocusHeld(dt: Float): Boolean = false

    abstract fun render(ctx: UiRenderContext)
}

/** Everything a widget needs to draw itself. */
class UiRenderContext {
    lateinit var pipeline: com.metaport.xr.render.Pipeline
    var panelMatrix = FloatArray(16)
    var mvp = FloatArray(16)
    var model = FloatArray(16)
    var camX = 0f
    var camY = 0f
    var camZ = 0f
    var time = 0f
    var globalAlpha = 1f
    var depthOffset = 0f
    val scratch = FloatArray(16)
}

/** Text label. */
class Label(
    var text: String = "",
    var size: Float = Theme.TEXT_BODY,
    var align: Int = FontAtlas.ALIGN_LEFT,
    var color: FloatArray = Theme.textPrimary.clone(),
    var glow: Float = 0f
) : Widget() {
    var letterSpacing = 0f
    var wrapWidth = Float.MAX_VALUE
    var maxLines = Int.MAX_VALUE
    /** When true the label measures itself and centres inside `width`. */
    var verticalCenter = true

    private var mesh: com.metaport.xr.core.gl.TextMesh? = null

    override fun render(ctx: UiRenderContext) {
        if (!visible) return
        val font = ctx.pipeline.font ?: return
        val m = mesh ?: com.metaport.xr.core.gl.TextMesh(font, size).also { mesh = it }
        m.size = size
        m.align = align
        m.color = color
        m.letterSpacing = letterSpacing
        m.wrapWidth = wrapWidth
        m.maxLines = maxLines
        m.set(text)

        val lines = if (wrapWidth < Float.MAX_VALUE) {
            kotlin.math.max(1, kotlin.math.ceil(font.measure(text, size) / kotlin.math.max(wrapWidth, 0.001f)).toInt())
        } else 1

        val totalH = lines * size * FontAtlas.LINE_SPACING
        val startY = if (verticalCenter) y + totalH * 0.5f else y
        val startX = when (align) {
            FontAtlas.ALIGN_CENTER -> x
            FontAtlas.ALIGN_RIGHT -> x + width * 0.5f
            else -> x - width * 0.5f
        }

        com.metaport.xr.core.math.Mat4.compose(
            ctx.model, startX, startY, ctx.depthOffset,
            0f, 0f, 0f, 1f, 1f, 1f
        )
        com.metaport.xr.core.math.Mat4.multiply(ctx.model, 0, ctx.panelMatrix, 0, ctx.model, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.model, 0)

        ctx.pipeline.drawTextMesh(ctx.mvp, color[0], color[1], color[2], color[3] * alpha * ctx.globalAlpha, glow)
        m.draw()
    }

    override fun update(dt: Float) {
        super.update(dt)
    }

    fun release() { mesh?.release(); mesh = null }
}

/** Rounded glass button with an accent bar and optional value suffix. */
class Button(
    var text: String = "",
    var size: Float = Theme.TEXT_BODY,
    var action: (() -> Unit)? = null
) : Widget() {
    var subtitle: String? = null
    var showAccentBar = true
    var fill: FloatArray = Theme.panelFill.clone()
    var radius = Theme.DEFAULT_RADIUS
    var border = Theme.DEFAULT_BORDER
    var icon: String? = null

    init {
        height = 0.075f
        width = 0.30f
    }

    override fun onSelect() {
        if (!enabled) return
        action?.invoke()
    }

    override fun render(ctx: UiRenderContext) {
        if (!visible) return
        val p = ctx.pipeline
        val acc = Theme.accent(accentIndex)

        com.metaport.xr.core.math.Mat4.compose(
            ctx.model, x, y, ctx.depthOffset + hover * 0.006f,
            0f, 0f, 0f, 1f, 1f, 1f
        )
        com.metaport.xr.core.math.Mat4.multiply(ctx.model, 0, ctx.panelMatrix, 0, ctx.model, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.model, 0)

        // Panel body.
        p.ui.use()
        com.metaport.xr.scene.GlState.blend(com.metaport.xr.scene.Material.BLEND_ALPHA)
        com.metaport.xr.scene.GlState.depthWrite(false)
        com.metaport.xr.scene.GlState.depthTest(true)
        com.metaport.xr.scene.GlState.cullBack(false)
        p.ui.setMat4("uMVP", ctx.mvp)
        p.ui.setMat4("uModel", ctx.model)
        p.ui.setVec4("uFillColor", fill[0], fill[1], fill[2], fill[3] * alpha * ctx.globalAlpha)
        p.ui.setVec4("uEdgeColor", acc[0], acc[1], acc[2], (0.55f + hover * 0.45f) * alpha)
        p.ui.setVec2("uSize", width, height)
        p.ui.setFloat("uRadius", radius)
        p.ui.setFloat("uBorder", border)
        p.ui.setFloat("uOpacity", (0.85f + hover * 0.15f) * alpha * ctx.globalAlpha)
        p.ui.setFloat("uHover", hover)
        p.ui.setFloat("uActive", active)
        p.ui.setFloat("uTime", ctx.time)
        p.ui.setFloat("uGradient", 0.25f)
        p.ui.setVec3("uCamPos", ctx.camX, ctx.camY, ctx.camZ)
        UiAssets.buttonMesh(width, height).draw()

        // Accent bar on the left edge.
        if (showAccentBar) {
            com.metaport.xr.core.math.Mat4.compose(
                ctx.scratch, x - width * 0.5f + 0.008f, y, ctx.depthOffset + 0.0025f,
                0f, 0f, 0f, 1f, 1f, 1f
            )
            com.metaport.xr.core.math.Mat4.multiply(ctx.scratch, 0, ctx.panelMatrix, 0, ctx.scratch, 0)
            com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.scratch, 0)
            p.ui.setMat4("uMVP", ctx.mvp)
            p.ui.setMat4("uModel", ctx.scratch)
            p.ui.setVec2("uSize", 0.0075f, height * 0.55f)
            p.ui.setFloat("uRadius", 0.0038f)
            p.ui.setVec4("uFillColor", acc[0], acc[1], acc[2], 0.9f * alpha)
            p.ui.setVec4("uEdgeColor", acc[0], acc[1], acc[2], 1f)
            p.ui.setFloat("uOpacity", (0.5f + hover * 0.5f) * alpha)
            p.ui.setFloat("uHover", hover)
            UiAssets.buttonMesh(0.0075f, height * 0.55f).draw()
        }

        // Text.
        val font = p.font ?: return
        p.beginText(font.texture)
        val lbl = textMesh(font)
        lbl.size = size
        lbl.align = FontAtlas.ALIGN_LEFT
        lbl.color = if (enabled) Theme.textPrimary else Theme.textDim
        lbl.set(text)
        val sub = subtitle
        val textX = x - width * 0.5f + (if (showAccentBar) 0.022f else 0.014f)
        val textY = if (sub == null) y + size * 0.42f else y + size * 0.85f
        com.metaport.xr.core.math.Mat4.compose(ctx.model, textX, textY, ctx.depthOffset + 0.004f, 0f, 0f, 0f, 1f, 1f, 1f)
        com.metaport.xr.core.math.Mat4.multiply(ctx.model, 0, ctx.panelMatrix, 0, ctx.model, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.model, 0)
        p.drawTextMesh(ctx.mvp, 0.94f, 0.97f, 1f, alpha * ctx.globalAlpha, hover * 0.35f)
        lbl.draw()

        if (sub != null) {
            val sl = subMesh(font)
            sl.size = Theme.TEXT_TINY
            sl.align = FontAtlas.ALIGN_LEFT
            sl.color = Theme.textSecondary
            sl.set(sub)
            com.metaport.xr.core.math.Mat4.compose(
                ctx.model, textX, y - size * 0.35f, ctx.depthOffset + 0.004f, 0f, 0f, 0f, 1f, 1f, 1f
            )
            com.metaport.xr.core.math.Mat4.multiply(ctx.model, 0, ctx.panelMatrix, 0, ctx.model, 0)
            com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.model, 0)
            p.drawTextMesh(ctx.mvp, 0.62f, 0.72f, 0.86f, alpha * ctx.globalAlpha * 0.9f, 0f)
            sl.draw()
        }
        p.endText()
    }

    private var tm: com.metaport.xr.core.gl.TextMesh? = null
    private var sm: com.metaport.xr.core.gl.TextMesh? = null
    private fun textMesh(f: FontAtlas) = tm ?: com.metaport.xr.core.gl.TextMesh(f, size).also { tm = it }
    private fun subMesh(f: FontAtlas) = sm ?: com.metaport.xr.core.gl.TextMesh(f, Theme.TEXT_TINY).also { sm = it }
}

/** Horizontal slider with a glowing knob; adjustable by gaze-dwell drag or trigger. */
class Slider(
    var label: String = "",
    var value: Float = 0.5f,
    var min: Float = 0f,
    var max: Float = 1f,
    var onChange: ((Float) -> Unit)? = null
) : Widget() {
    var format: ((Float) -> String) = { "%.2f".format(it) }
    var dragging = false

    init { width = 0.44f; height = 0.07f }

    fun setValue(v: Float, notify: Boolean = true) {
        val nv = v.coerceIn(min, max)
        if (kotlin.math.abs(nv - value) > 1e-5f) {
            value = nv
            if (notify) onChange?.invoke(value)
        }
    }

    fun nudge(dir: Float) {
        setValue(value + dir * (max - min) * 0.02f)
    }

    override fun render(ctx: UiRenderContext) {
        if (!visible) return
        val p = ctx.pipeline
        val acc = Theme.accent(accentIndex)
        val t = ((value - min) / (max - min)).coerceIn(0f, 1f)

        com.metaport.xr.core.math.Mat4.compose(ctx.model, x, y, ctx.depthOffset, 0f, 0f, 0f, 1f, 1f, 1f)
        com.metaport.xr.core.math.Mat4.multiply(ctx.model, 0, ctx.panelMatrix, 0, ctx.model, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.model, 0)

        p.ui.use()
        com.metaport.xr.scene.GlState.blend(com.metaport.xr.scene.Material.BLEND_ALPHA)
        com.metaport.xr.scene.GlState.depthWrite(false)
        com.metaport.xr.scene.GlState.cullBack(false)
        p.ui.setMat4("uMVP", ctx.mvp)
        p.ui.setMat4("uModel", ctx.model)
        p.ui.setVec2("uSize", width, height)
        p.ui.setFloat("uRadius", Theme.DEFAULT_RADIUS)
        p.ui.setFloat("uBorder", Theme.DEFAULT_BORDER)
        p.ui.setVec4("uFillColor", Theme.panelFill[0], Theme.panelFill[1], Theme.panelFill[2], 0.5f * alpha)
        p.ui.setVec4("uEdgeColor", acc[0], acc[1], acc[2], 0.5f * alpha)
        p.ui.setFloat("uOpacity", 0.8f * alpha * ctx.globalAlpha)
        p.ui.setFloat("uHover", hover)
        p.ui.setFloat("uActive", 0f)
        p.ui.setFloat("uTime", ctx.time)
        p.ui.setFloat("uGradient", 0.2f)
        p.ui.setVec3("uCamPos", ctx.camX, ctx.camY, ctx.camZ)
        UiAssets.buttonMesh(width, height).draw()

        // Track
        com.metaport.xr.core.math.Mat4.compose(
            ctx.scratch, x, y - 0.008f, ctx.depthOffset + 0.002f, 0f, 0f, 0f, 1f, 1f, 1f
        )
        com.metaport.xr.core.math.Mat4.multiply(ctx.scratch, 0, ctx.panelMatrix, 0, ctx.scratch, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.scratch, 0)
        p.ui.setMat4("uMVP", ctx.mvp)
        p.ui.setMat4("uModel", ctx.scratch)
        p.ui.setVec2("uSize", width - 0.05f, 0.008f)
        p.ui.setFloat("uRadius", 0.004f)
        p.ui.setVec4("uFillColor", 0.08f, 0.11f, 0.18f, 0.9f)
        p.ui.setVec4("uEdgeColor", 0.2f, 0.3f, 0.45f, 0.6f)
        p.ui.setFloat("uOpacity", 0.9f * alpha)
        p.ui.setFloat("uHover", 0f)
        UiAssets.buttonMesh(width - 0.05f, 0.008f).draw()

        // Filled portion
        val fw = (width - 0.05f) * t
        if (fw > 0.004f) {
            com.metaport.xr.core.math.Mat4.compose(
                ctx.scratch, x - (width - 0.05f) * 0.5f + fw * 0.5f, y - 0.008f, ctx.depthOffset + 0.003f,
                0f, 0f, 0f, 1f, 1f, 1f
            )
            com.metaport.xr.core.math.Mat4.multiply(ctx.scratch, 0, ctx.panelMatrix, 0, ctx.scratch, 0)
            com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.scratch, 0)
            p.ui.setMat4("uMVP", ctx.mvp)
            p.ui.setMat4("uModel", ctx.scratch)
            p.ui.setVec2("uSize", fw, 0.008f)
            p.ui.setFloat("uRadius", 0.004f)
            p.ui.setVec4("uFillColor", acc[0], acc[1], acc[2], 0.95f)
            p.ui.setVec4("uEdgeColor", acc[0], acc[1], acc[2], 1f)
            p.ui.setFloat("uOpacity", alpha)
            p.ui.setFloat("uHover", 0.4f)
            UiAssets.buttonMesh(fw, 0.008f).draw()
        }

        // Knob
        com.metaport.xr.core.math.Mat4.compose(
            ctx.scratch, x - (width - 0.05f) * 0.5f + fw, y - 0.008f, ctx.depthOffset + 0.004f,
            0f, 0f, 0f, 1f, 1f, 1f
        )
        com.metaport.xr.core.math.Mat4.multiply(ctx.scratch, 0, ctx.panelMatrix, 0, ctx.scratch, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.scratch, 0)
        p.ui.setMat4("uMVP", ctx.mvp)
        p.ui.setMat4("uModel", ctx.scratch)
        p.ui.setVec2("uSize", 0.02f, 0.02f)
        p.ui.setFloat("uRadius", 0.01f)
        p.ui.setVec4("uFillColor", 1f, 1f, 1f, 0.95f)
        p.ui.setVec4("uEdgeColor", acc[0], acc[1], acc[2], 1f)
        p.ui.setFloat("uOpacity", alpha)
        p.ui.setFloat("uHover", hover)
        UiAssets.buttonMesh(0.02f, 0.02f).draw()

        // Labels
        val font = p.font ?: return
        p.beginText(font.texture)
        val lm = labelMesh(font)
        lm.size = Theme.TEXT_SMALL
        lm.align = FontAtlas.ALIGN_LEFT
        lm.color = Theme.textSecondary
        lm.set(label)
        com.metaport.xr.core.math.Mat4.compose(
            ctx.model, x - width * 0.5f + 0.014f, y + 0.02f, ctx.depthOffset + 0.004f, 0f, 0f, 0f, 1f, 1f, 1f
        )
        com.metaport.xr.core.math.Mat4.multiply(ctx.model, 0, ctx.panelMatrix, 0, ctx.model, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.model, 0)
        p.drawTextMesh(ctx.mvp, 0.62f, 0.72f, 0.86f, alpha * ctx.globalAlpha, 0f)
        lm.draw()

        val vm = valueMesh(font)
        vm.size = Theme.TEXT_SMALL
        vm.align = FontAtlas.ALIGN_RIGHT
        vm.color = Theme.textPrimary
        vm.set(format(value))
        com.metaport.xr.core.math.Mat4.compose(
            ctx.model, x + width * 0.5f - 0.014f, y + 0.02f, ctx.depthOffset + 0.004f, 0f, 0f, 0f, 1f, 1f, 1f
        )
        com.metaport.xr.core.math.Mat4.multiply(ctx.model, 0, ctx.panelMatrix, 0, ctx.model, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.model, 0)
        p.drawTextMesh(ctx.mvp, acc[0], acc[1], acc[2], alpha * ctx.globalAlpha, 0.3f)
        vm.draw()
        p.endText()
    }

    private var lm: com.metaport.xr.core.gl.TextMesh? = null
    private var vm: com.metaport.xr.core.gl.TextMesh? = null
    private fun labelMesh(f: FontAtlas) = lm ?: com.metaport.xr.core.gl.TextMesh(f, Theme.TEXT_SMALL).also { lm = it }
    private fun valueMesh(f: FontAtlas) = vm ?: com.metaport.xr.core.gl.TextMesh(f, Theme.TEXT_SMALL).also { vm = it }
}

/** On/off pill switch. */
class Toggle(
    var label: String = "",
    var on: Boolean = false,
    var onChange: ((Boolean) -> Unit)? = null
) : Widget() {
    init { width = 0.44f; height = 0.062f }

    override fun onSelect() {
        on = !on
        onChange?.invoke(on)
    }

    override fun render(ctx: UiRenderContext) {
        if (!visible) return
        val p = ctx.pipeline
        val acc = if (on) Theme.accent(accentIndex) else floatArrayOf(0.25f, 0.3f, 0.4f)

        com.metaport.xr.core.math.Mat4.compose(ctx.model, x, y, ctx.depthOffset, 0f, 0f, 0f, 1f, 1f, 1f)
        com.metaport.xr.core.math.Mat4.multiply(ctx.model, 0, ctx.panelMatrix, 0, ctx.model, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.model, 0)

        p.ui.use()
        com.metaport.xr.scene.GlState.blend(com.metaport.xr.scene.Material.BLEND_ALPHA)
        com.metaport.xr.scene.GlState.depthWrite(false)
        com.metaport.xr.scene.GlState.cullBack(false)
        p.ui.setMat4("uMVP", ctx.mvp)
        p.ui.setMat4("uModel", ctx.model)
        p.ui.setVec2("uSize", width, height)
        p.ui.setFloat("uRadius", Theme.DEFAULT_RADIUS)
        p.ui.setFloat("uBorder", Theme.DEFAULT_BORDER)
        p.ui.setVec4("uFillColor", Theme.panelFill[0], Theme.panelFill[1], Theme.panelFill[2], 0.45f)
        p.ui.setVec4("uEdgeColor", acc[0], acc[1], acc[2], (if (on) 0.95f else 0.4f) * alpha)
        p.ui.setFloat("uOpacity", 0.85f * alpha * ctx.globalAlpha)
        p.ui.setFloat("uHover", hover)
        p.ui.setFloat("uActive", 0f)
        p.ui.setFloat("uTime", ctx.time)
        p.ui.setFloat("uGradient", 0.2f)
        p.ui.setVec3("uCamPos", ctx.camX, ctx.camY, ctx.camZ)
        UiAssets.buttonMesh(width, height).draw()

        // Pill track + knob
        val tw = 0.052f
        com.metaport.xr.core.math.Mat4.compose(
            ctx.scratch, x + width * 0.5f - 0.045f, y, ctx.depthOffset + 0.002f, 0f, 0f, 0f, 1f, 1f, 1f
        )
        com.metaport.xr.core.math.Mat4.multiply(ctx.scratch, 0, ctx.panelMatrix, 0, ctx.scratch, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.scratch, 0)
        p.ui.setMat4("uMVP", ctx.mvp)
        p.ui.setMat4("uModel", ctx.scratch)
        p.ui.setVec2("uSize", tw, 0.022f)
        p.ui.setFloat("uRadius", 0.011f)
        p.ui.setVec4("uFillColor", 0.06f, 0.09f, 0.14f, 0.9f)
        p.ui.setVec4("uEdgeColor", acc[0], acc[1], acc[2], 0.8f)
        p.ui.setFloat("uOpacity", 0.95f * alpha)
        p.ui.setFloat("uHover", 0f)
        UiAssets.buttonMesh(tw, 0.022f).draw()

        val kx = if (on) 0.013f else -0.013f
        com.metaport.xr.core.math.Mat4.compose(
            ctx.scratch, x + width * 0.5f - 0.045f + kx, y, ctx.depthOffset + 0.004f, 0f, 0f, 0f, 1f, 1f, 1f
        )
        com.metaport.xr.core.math.Mat4.multiply(ctx.scratch, 0, ctx.panelMatrix, 0, ctx.scratch, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.scratch, 0)
        p.ui.setMat4("uMVP", ctx.mvp)
        p.ui.setMat4("uModel", ctx.scratch)
        p.ui.setVec2("uSize", 0.017f, 0.017f)
        p.ui.setFloat("uRadius", 0.0085f)
        p.ui.setVec4("uFillColor", acc[0], acc[1], acc[2], 1f)
        p.ui.setVec4("uEdgeColor", 1f, 1f, 1f, 0.9f)
        p.ui.setFloat("uOpacity", alpha)
        p.ui.setFloat("uHover", if (on) 0.5f else 0f)
        UiAssets.buttonMesh(0.017f, 0.017f).draw()

        val font = p.font ?: return
        p.beginText(font.texture)
        val lm = mesh(font)
        lm.size = Theme.TEXT_SMALL
        lm.align = FontAtlas.ALIGN_LEFT
        lm.color = Theme.textSecondary
        lm.set(label)
        com.metaport.xr.core.math.Mat4.compose(
            ctx.model, x - width * 0.5f + 0.014f, y + Theme.TEXT_SMALL * 0.38f, ctx.depthOffset + 0.004f,
            0f, 0f, 0f, 1f, 1f, 1f
        )
        com.metaport.xr.core.math.Mat4.multiply(ctx.model, 0, ctx.panelMatrix, 0, ctx.model, 0)
        com.metaport.xr.core.math.Mat4.multiply(ctx.mvp, 0, ctx.pipeline.viewProjection, 0, ctx.model, 0)
        p.drawTextMesh(ctx.mvp, 0.72f, 0.8f, 0.92f, alpha * ctx.globalAlpha, 0f)
        lm.draw()
        p.endText()
    }

    private var tm: com.metaport.xr.core.gl.TextMesh? = null
    private fun mesh(f: FontAtlas) = tm ?: com.metaport.xr.core.gl.TextMesh(f, Theme.TEXT_SMALL).also { tm = it }
}
