package com.metaport.xr.core.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.metaport.xr.core.math.Mathf

/**
 * Generates a signed-glyph atlas on the CPU with Android's own text rasterizer,
 * so MetaPort renders crisp proportional text fully in 3D without shipping a
 * single font or texture file.
 *
 * Atlas: 1024x1024, 16x16 cells of 64px, covering code points 0..255
 * (ASCII + Latin-1, which covers Portuguese accents).
 */
class FontAtlas(bold: Boolean = true, private val typeface: Typeface? = null) {

    private class Glyph(
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
        val advance: Float, val bearingX: Float
    )

    private val glyphs = arrayOfNulls<Glyph>(256)
    val texture: Int

    /** World height of one text cell when `size` == 1. */
    val cellAspect: Float = CELL.toFloat() / FONT_PX

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.WHITE
        textSize = FONT_PX
        typeface = this@FontAtlas.typeface
            ?: Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        isLinearText = true
    }

    init {
        val bmp = Bitmap.createBitmap(ATLAS, ATLAS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val fm = paint.fontMetrics
        val baseline = PAD + -fm.ascent

        for (code in 0 until 256) {
            if (code < 32) continue
            val ch = code.toChar()
            val col = code % COLS
            val row = code / COLS
            if (row >= ROWS) break
            val x = col * CELL.toFloat()
            val y = row * CELL.toFloat()

            val s = ch.toString()
            val adv = paint.measureText(s)
            val bounds = android.graphics.Rect()
            paint.getTextBounds(s, 0, 1, bounds)

            canvas.drawText(s, x + PAD - bounds.left, y + baseline, paint)

            glyphs[code] = Glyph(
                u0 = x / ATLAS,
                v0 = y / ATLAS,
                u1 = (x + CELL) / ATLAS,
                v1 = (y + CELL) / ATLAS,
                advance = adv,
                bearingX = bounds.left.toFloat()
            )
        }
        texture = Tex.create(bmp, mipmaps = true, linear = true)
        bmp.recycle()
    }

    fun glyph(code: Int): Glyph? {
        if (code in 0..255) glyphs[code]?.let { return it }
        return glyphs['?'.code]
    }

    /** Width of `text` in world units for a given cell size. */
    fun measure(text: String, size: Float): Float {
        val s = size / FONT_PX
        var w = 0f
        for (ch in text) {
            val g = glyph(ch.code) ?: continue
            w += g.advance * s
        }
        return w
    }

    fun lineHeight(size: Float): Float = size

    /**
     * Appends textured quads for [text] into [b]. `x`,`y` is the top-left of the
     * first line's cell box; lines advance downward.
     */
    fun fill(
        b: MeshBuilder,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        align: Int = ALIGN_LEFT,
        r: Float = 1f, g: Float = 1f, bl: Float = 1f, a: Float = 1f,
        letterSpacing: Float = 0f,
        maxLines: Int = Int.MAX_VALUE,
        wrapWidth: Float = Float.MAX_VALUE
    ): MeshBuilder {
        val s = size / FONT_PX
        val lines = if (wrapWidth < Float.MAX_VALUE) wrap(text, wrapWidth, size) else listOf(text)
        b.color(r, g, bl, a)
        var lineIndex = 0
        for (line in lines) {
            if (lineIndex >= maxLines) break
            val w = measure(line, size) + letterSpacing * maxOf(line.length - 1, 0) * s
            var cx = when (align) {
                ALIGN_CENTER -> x - w * 0.5f
                ALIGN_RIGHT -> x - w
                else -> x
            }
            val cy = y - lineIndex * size * LINE_SPACING
            for (ch in line) {
                val gl = glyph(ch.code) ?: continue
                val gx = cx + (gl.bearingX - PAD) * s
                val gy = cy
                val gw = CELL * s
                val gh = CELL * s
                val i0 = b.v(gx, gy, 0f, 0f, 0f, 1f, gl.u0, gl.v0)
                val i1 = b.v(gx + gw, gy, 0f, 0f, 0f, 1f, gl.u1, gl.v0)
                val i2 = b.v(gx + gw, gy - gh, 0f, 0f, 0f, 1f, gl.u1, gl.v1)
                val i3 = b.v(gx, gy - gh, 0f, 0f, 0f, 1f, gl.u0, gl.v1)
                b.quad(i0, i1, i2, i3)
                cx += (gl.advance + letterSpacing) * s
            }
            lineIndex++
        }
        return b
    }

    private fun wrap(text: String, width: Float, size: Float): List<String> {
        val out = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            val words = paragraph.split(' ')
            var cur = StringBuilder()
            for (word in words) {
                val candidate = if (cur.isEmpty()) word else cur.toString() + " " + word
                if (measure(candidate, size) <= width || cur.isEmpty()) {
                    cur = StringBuilder(candidate)
                } else {
                    out.add(cur.toString())
                    cur = StringBuilder(word)
                }
            }
            out.add(cur.toString())
        }
        if (out.isEmpty()) out.add("")
        return out
    }

    fun release() = Tex.delete(texture)

    companion object {
        const val ATLAS = 1024
        const val CELL = 64
        const val COLS = 16
        const val ROWS = 16
        const val PAD = 8f
        const val FONT_PX = 42f
        const val LINE_SPACING = 1.22f

        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2

        /** Frame-coherent clamp helper for text sizes. */
        fun clampSize(v: Float): Float = Mathf.clamp(v, 0.002f, 2f)
    }
}

/**
 * A text string rendered as a [DynamicMesh]; re-uploads only when the string,
 * size, colour or alignment changes. Used by every spatial label in MetaPort.
 */
class TextMesh(private val font: FontAtlas, initialSize: Float = 0.1f) {
    private val mesh = DynamicMesh(64)
    private var cacheKey = "\u0000\u0000\u0000\u0000"
    private val builder = MeshBuilder(64)

    var size: Float = initialSize
    var align: Int = FontAtlas.ALIGN_LEFT
    var color: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)
    var letterSpacing: Float = 0f
    var wrapWidth: Float = Float.MAX_VALUE
    var maxLines: Int = Int.MAX_VALUE
    var text: String = ""

    val lineHeight: Float get() = size

    fun set(newText: String): TextMesh {
        if (newText != text) {
            text = newText
            cacheKey = "\u0000\u0000\u0000\u0000"
        }
        return this
    }

    private fun rebuild() {
        val key = "$text|$size|$align|${color[0]},${color[1]},${color[2]},${color[3]}|$letterSpacing|$wrapWidth|$maxLines"
        if (key == cacheKey) return
        cacheKey = key
        builder.reset()
        font.fill(
            builder, text, 0f, 0f, size, align,
            color[0], color[1], color[2], color[3], letterSpacing, maxLines, wrapWidth
        )
        if (builder.indexCount() == 0) {
            // Degenerate strings still need one valid draw call buffer.
            val i0 = builder.v(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f)
            val i1 = builder.v(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f)
            val i2 = builder.v(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f)
            builder.tri(i0, i1, i2)
        }
        mesh.upload(builder)
    }

    fun draw() {
        rebuild()
        mesh.draw()
    }

    fun isEmpty(): Boolean = builder.indexCount() == 0

    fun release() = mesh.release()
}
