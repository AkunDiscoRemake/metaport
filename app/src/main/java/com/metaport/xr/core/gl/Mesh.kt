package com.metaport.xr.core.gl

import android.opengl.GLES30
import com.metaport.xr.core.math.Mathf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Interleaved vertex layout used by every MetaPort mesh:
 * pos(3) normal(3) uv(2) color(4) = 12 floats = 48 bytes.
 */
object VertexLayout {
    const val FLOATS = 12
    const val STRIDE = FLOATS * 4
    const val POS_OFF = 0
    const val NORMAL_OFF = 3
    const val UV_OFF = 6
    const val COLOR_OFF = 8
}

/**
 * GPU mesh with a VAO. Meshes are immutable after upload; dynamic content
 * (particles, hand skeletons, text) rebuilds small meshes or uses [DynamicMesh].
 */
class Mesh private constructor(
    val vao: Int,
    val indexCount: Int,
    val vertexCount: Int,
    val mode: Int,
    private val vbo: Int,
    private val ebo: Int
) {
    fun draw() {
        GLES30.glBindVertexArray(vao)
        if (indexCount > 0) {
            GLES30.glDrawElements(mode, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        } else {
            GLES30.glDrawArrays(mode, 0, vertexCount)
        }
        GLES30.glBindVertexArray(0)
    }

    fun drawInstanced(instances: Int) {
        GLES30.glBindVertexArray(vao)
        if (indexCount > 0) {
            GLES30.glDrawElementsInstanced(mode, indexCount, GLES30.GL_UNSIGNED_INT, 0, instances)
        } else {
            GLES30.glDrawArraysInstanced(mode, 0, vertexCount, instances)
        }
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        GLES30.glDeleteBuffers(1, intArrayOf(vbo, ebo), 0)
    }

    companion object {
        fun upload(vb: MeshBuilder, mode: Int = GLES30.GL_TRIANGLES): Mesh {
            val verts = vb.vertexData()
            val idx = vb.indexData()

            val vaoIds = IntArray(1); GLES30.glGenVertexArrays(1, vaoIds, 0)
            val bufIds = IntArray(2); GLES30.glGenBuffers(2, bufIds, 0)
            val vao = vaoIds[0]; val vbo = bufIds[0]; val ebo = bufIds[1]

            GLES30.glBindVertexArray(vao)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER, verts.capacity() * 4, verts, GLES30.GL_STATIC_DRAW
            )
            val s = VertexLayout.STRIDE
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, s, VertexLayout.POS_OFF * 4L)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, s, VertexLayout.NORMAL_OFF * 4L)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, s, VertexLayout.UV_OFF * 4L)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, s, VertexLayout.COLOR_OFF * 4L)

            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.capacity() * 4, idx, GLES30.GL_STATIC_DRAW
            )

            GLES30.glBindVertexArray(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

            return Mesh(vao, idx.capacity(), vb.vertexCount(), mode, vbo, ebo)
        }

        /** Streams a new vertex/index payload into an existing mesh (same or smaller size). */
        fun uploadDynamic(vao: Int, vbo: Int, ebo: Int, vb: MeshBuilder) {
            val verts = vb.vertexData()
            val idx = vb.indexData()
            GLES30.glBindVertexArray(vao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER, verts.capacity() * 4, verts, GLES30.GL_DYNAMIC_DRAW
            )
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.capacity() * 4, idx, GLES30.GL_DYNAMIC_DRAW
            )
            GLES30.glBindVertexArray(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        }

        fun createBuffers(): IntArray {
            val vaoIds = IntArray(1); GLES30.glGenVertexArrays(1, vaoIds, 0)
            val bufIds = IntArray(2); GLES30.glGenBuffers(2, bufIds, 0)
            val vao = vaoIds[0]; val vbo = bufIds[0]; val ebo = bufIds[1]
            GLES30.glBindVertexArray(vao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            val s = VertexLayout.STRIDE
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, s, VertexLayout.POS_OFF * 4L)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, s, VertexLayout.NORMAL_OFF * 4L)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, s, VertexLayout.UV_OFF * 4L)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, s, VertexLayout.COLOR_OFF * 4L)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
            GLES30.glBindVertexArray(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            return intArrayOf(vao, vbo, ebo)
        }
    }
}

/** Mutable mesh whose GPU buffers can be re-uploaded every frame. */
class DynamicMesh(initialVerts: Int = 256, val mode: Int = GLES30.GL_TRIANGLES) {
    private val bufs = Mesh.createBuffers()
    val vao: Int get() = bufs[0]
    var indexCount: Int = 0
        private set
    var vertexCount: Int = 0
        private set
    private var capacity = initialVerts

    fun upload(vb: MeshBuilder) {
        val needed = max(vb.vertexCount(), 16)
        if (needed > capacity) capacity = needed
        Mesh.uploadDynamic(vao, bufs[1], bufs[2], vb)
        indexCount = vb.indexCount()
        vertexCount = vb.vertexCount()
    }

    fun draw() {
        if (indexCount <= 0) return
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(mode, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(bufs[0]), 0)
        GLES30.glDeleteBuffers(1, intArrayOf(bufs[1], bufs[2]), 0)
    }
}

/** Accumulates vertices/indices on the CPU, then uploads once. */
class MeshBuilder(initialVerts: Int = 64) {
    private var verts = FloatArray(initialVerts * VertexLayout.FLOATS)
    private var idx = IntArray(initialVerts)
    private var vc = 0
    private var ic = 0

    var cr = 1f; var cg = 1f; var cb = 1f; var ca = 1f

    fun vertexCount(): Int = vc
    fun indexCount(): Int = ic

    fun reset(): MeshBuilder { vc = 0; ic = 0; return this }

    fun color(r: Float, g: Float, b: Float, a: Float = 1f): MeshBuilder {
        cr = r; cg = g; cb = b; ca = a; return this
    }

    private fun ensureVerts(n: Int) {
        if (n <= verts.size) return
        var s = verts.size
        while (s < n) s *= 2
        verts = verts.copyOf(s)
    }

    private fun ensureIdx(n: Int) {
        if (n <= idx.size) return
        var s = idx.size
        while (s < n) s *= 2
        idx = idx.copyOf(s)
    }

    fun v(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float, u: Float, w: Float): Int {
        ensureVerts(vc + 1)
        val o = vc * VertexLayout.FLOATS
        verts[o] = px; verts[o + 1] = py; verts[o + 2] = pz
        verts[o + 3] = nx; verts[o + 4] = ny; verts[o + 5] = nz
        verts[o + 6] = u; verts[o + 7] = w
        verts[o + 8] = cr; verts[o + 9] = cg; verts[o + 10] = cb; verts[o + 11] = ca
        return vc++
    }

    fun tri(a: Int, b: Int, c: Int): MeshBuilder {
        ensureIdx(ic + 3)
        idx[ic++] = a; idx[ic++] = b; idx[ic++] = c
        return this
    }

    fun quad(a: Int, b: Int, c: Int, d: Int): MeshBuilder {
        tri(a, b, c); tri(a, c, d); return this
    }

    fun line(a: Int, b: Int): MeshBuilder {
        ensureIdx(ic + 2)
        idx[ic++] = a; idx[ic++] = b
        return this
    }

    fun vertexData(): java.nio.FloatBuffer =
        ByteBuffer.allocateDirect(vc * VertexLayout.STRIDE).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(verts, 0, vc * VertexLayout.FLOATS); position(0) }

    fun indexData(): IntBuffer =
        ByteBuffer.allocateDirect(max(ic, 1) * 4).order(ByteOrder.nativeOrder())
            .asIntBuffer().apply { put(idx, 0, ic); position(0) }
}

/** Procedural geometry — MetaPort ships no binary assets, everything is generated. */
object MeshGen {

    fun quadXY(w: Float, h: Float, u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f): Mesh {
        val b = MeshBuilder()
        val hw = w * 0.5f; val hh = h * 0.5f
        val a = b.v(-hw, hh, 0f, 0f, 0f, 1f, u0, v0)
        val bb = b.v(hw, hh, 0f, 0f, 0f, 1f, u1, v0)
        val c = b.v(hw, -hh, 0f, 0f, 0f, 1f, u1, v1)
        val d = b.v(-hw, -hh, 0f, 0f, 0f, 1f, u0, v1)
        b.quad(a, bb, c, d)
        return Mesh.upload(b)
    }

    fun quadXZ(w: Float, d: Float): Mesh {
        val b = MeshBuilder()
        val hw = w * 0.5f; val hd = d * 0.5f
        val a = b.v(-hw, 0f, hd, 0f, 1f, 0f, 0f, 0f)
        val bb = b.v(hw, 0f, hd, 0f, 1f, 0f, 1f, 0f)
        val c = b.v(hw, 0f, -hd, 0f, 1f, 0f, 1f, 1f)
        val dd = b.v(-hw, 0f, -hd, 0f, 1f, 0f, 0f, 1f)
        b.quad(a, bb, c, dd)
        return Mesh.upload(b)
    }

    /** Fullscreen-ish quad for post passes (distortion, passthrough). */
    fun screenQuad(): Mesh {
        val b = MeshBuilder()
        val a = b.v(-1f, -1f, 0f, 0f, 0f, 1f, 0f, 0f)
        val bb = b.v(1f, -1f, 0f, 0f, 0f, 1f, 1f, 0f)
        val c = b.v(1f, 1f, 0f, 0f, 0f, 1f, 1f, 1f)
        val d = b.v(-1f, 1f, 0f, 0f, 0f, 1f, 0f, 1f)
        b.quad(a, bb, c, d)
        return Mesh.upload(b)
    }

    fun box(w: Float, h: Float, d: Float): Mesh {
        val b = MeshBuilder()
        val x = w * 0.5f; val y = h * 0.5f; val z = d * 0.5f
        // +Z
        addFace(b, floatArrayOf(-x, -y, z, x, -y, z, x, y, z, -x, y, z), 0f, 0f, 1f)
        // -Z
        addFace(b, floatArrayOf(x, -y, -z, -x, -y, -z, -x, y, -z, x, y, -z), 0f, 0f, -1f)
        // +X
        addFace(b, floatArrayOf(x, -y, z, x, -y, -z, x, y, -z, x, y, z), 1f, 0f, 0f)
        // -X
        addFace(b, floatArrayOf(-x, -y, -z, -x, -y, z, -x, y, z, -x, y, -z), -1f, 0f, 0f)
        // +Y
        addFace(b, floatArrayOf(-x, y, z, x, y, z, x, y, -z, -x, y, -z), 0f, 1f, 0f)
        // -Y
        addFace(b, floatArrayOf(-x, -y, -z, x, -y, -z, x, -y, z, -x, -y, z), 0f, -1f, 0f)
        return Mesh.upload(b)
    }

    private fun addFace(b: MeshBuilder, p: FloatArray, nx: Float, ny: Float, nz: Float) {
        val uv = floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)
        val base = b.vertexCount()
        for (i in 0 until 4) {
            b.v(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], nx, ny, nz, uv[i * 2], uv[i * 2 + 1])
        }
        b.quad(base, base + 1, base + 2, base + 3)
    }

    fun sphere(radius: Float, segU: Int = 20, segV: Int = 14,
               u0: Float = 0f, u1: Float = 1f, v0: Float = 0f, v1: Float = 1f): Mesh {
        val b = MeshBuilder()
        val su = max(segU, 3); val sv = max(segV, 2)
        for (j in 0..sv) {
            val vv = j.toFloat() / sv
            val phi = (v0 + (v1 - v0) * vv) * PI.toFloat()
            for (i in 0..su) {
                val uu = i.toFloat() / su
                val theta = (u0 + (u1 - u0) * uu) * 2f * PI.toFloat()
                val nx = sin(phi) * cos(theta)
                val ny = cos(phi)
                val nz = sin(phi) * sin(theta)
                b.v(nx * radius, ny * radius, nz * radius, nx, ny, nz, uu, vv)
            }
        }
        for (j in 0 until sv) {
            for (i in 0 until su) {
                val a = j * (su + 1) + i
                val bb = a + su + 1
                b.quad(a, bb, bb + 1, a + 1)
            }
        }
        return Mesh.upload(b)
    }

    fun cylinder(radius: Float, height: Float, seg: Int = 18, capped: Boolean = true): Mesh {
        val b = MeshBuilder()
        val s = max(seg, 3)
        val hy = height * 0.5f
        val ringBottom = b.vertexCount()
        for (i in 0..s) {
            val a = i.toFloat() / s * 2f * PI.toFloat()
            val nx = cos(a); val nz = sin(a)
            b.v(nx * radius, -hy, nz * radius, nx, 0f, nz, i.toFloat() / s, 1f)
        }
        for (i in 0..s) {
            val a = i.toFloat() / s * 2f * PI.toFloat()
            val nx = cos(a); val nz = sin(a)
            b.v(nx * radius, hy, nz * radius, nx, 0f, nz, i.toFloat() / s, 0f)
        }
        for (i in 0 until s) {
            val a = ringBottom + i
            val bb = a + s + 1
            b.quad(a, bb, bb + 1, a + 1)
        }
        if (capped) {
            val topCenter = b.v(0f, hy, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
            for (i in 0..s) {
                val ang = i.toFloat() / s * 2f * PI.toFloat()
                b.v(cos(ang) * radius, hy, sin(ang) * radius, 0f, 1f, 0f, 0.5f, 0.5f)
            }
            for (i in 0 until s) b.tri(topCenter, topCenter + 1 + i + 1, topCenter + 1 + i)
            val botCenter = b.v(0f, -hy, 0f, 0f, -1f, 0f, 0.5f, 0.5f)
            for (i in 0..s) {
                val ang = i.toFloat() / s * 2f * PI.toFloat()
                b.v(cos(ang) * radius, -hy, sin(ang) * radius, 0f, -1f, 0f, 0.5f, 0.5f)
            }
            for (i in 0 until s) b.tri(botCenter, botCenter + 1 + i, botCenter + 1 + i + 1)
        }
        return Mesh.upload(b)
    }

    fun capsule(radius: Float, length: Float, seg: Int = 10): Mesh {
        val b = MeshBuilder()
        val s = max(seg, 4)
        val half = length * 0.5f
        for (j in 0..s) {
            val phi = j.toFloat() / s * PI.toFloat()
            val yy = cos(phi) * radius
            val rr = sin(phi) * radius
            val ny = cos(phi)
            val offset = if (phi < PI.toFloat() * 0.5f) half else -half
            for (i in 0..s * 2) {
                val theta = i.toFloat() / (s * 2) * 2f * PI.toFloat()
                val nx = sin(phi) * cos(theta); val nz = sin(phi) * sin(theta)
                b.v(nx * rr + 0f, yy + offset, nz * rr, nx, ny, nz, i.toFloat() / (s * 2), j.toFloat() / s)
            }
        }
        val ring = s * 2 + 1
        for (j in 0 until s) {
            for (i in 0 until ring - 1) {
                val a = j * ring + i
                val bb = a + ring
                b.quad(a, bb, bb + 1, a + 1)
            }
        }
        return Mesh.upload(b)
    }

    fun torus(radius: Float, tube: Float, segU: Int = 28, segV: Int = 12): Mesh {
        val b = MeshBuilder()
        val su = max(segU, 4); val sv = max(segV, 3)
        for (i in 0..su) {
            val u = i.toFloat() / su * 2f * PI.toFloat()
            for (j in 0..sv) {
                val v = j.toFloat() / sv * 2f * PI.toFloat()
                val nx = cos(v) * cos(u); val ny = sin(v); val nz = cos(v) * sin(u)
                b.v(
                    (radius + tube * cos(v)) * cos(u),
                    tube * sin(v),
                    (radius + tube * cos(v)) * sin(u),
                    nx, ny, nz, i.toFloat() / su, j.toFloat() / sv
                )
            }
        }
        for (i in 0 until su) {
            for (j in 0 until sv) {
                val a = i * (sv + 1) + j
                val bb = a + sv + 1
                b.quad(a, bb, bb + 1, a + 1)
            }
        }
        return Mesh.upload(b)
    }

    /** Flat ring on the XZ plane (reticles, landing pads, portals). */
    fun ring(inner: Float, outer: Float, seg: Int = 48): Mesh {
        val b = MeshBuilder()
        val s = max(seg, 6)
        for (i in 0..s) {
            val a = i.toFloat() / s * 2f * PI.toFloat()
            val c = cos(a); val sn = sin(a)
            b.v(c * inner, 0f, sn * inner, 0f, 1f, 0f, i.toFloat() / s, 0f)
            b.v(c * outer, 0f, sn * outer, 0f, 1f, 0f, i.toFloat() / s, 1f)
        }
        for (i in 0 until s) {
            val a = i * 2
            b.quad(a, a + 2, a + 3, a + 1)
        }
        return Mesh.upload(b)
    }

    /**
     * Curved panel — MetaPort UI wraps around the user on a cylinder so every
     * element sits at the same optical distance. `curvature` = arc angle in radians.
     */
    fun curvedPanel(width: Float, height: Float, curvature: Float,
                    segX: Int = 24, segY: Int = 4, u0: Float = 0f, u1: Float = 1f): Mesh {
        val b = MeshBuilder()
        val sx = max(segX, 1); val sy = max(segY, 1)
        val arc = if (kotlin.math.abs(curvature) < 1e-4f) 0f else curvature
        for (j in 0..sy) {
            val yy = height * 0.5f - height * j / sy
            for (i in 0..sx) {
                val t = i.toFloat() / sx
                val u = u0 + (u1 - u0) * t
                val vv = j.toFloat() / sy
                val px: Float; val pz: Float; val nx: Float; val nz: Float
                if (arc == 0f) {
                    px = -width * 0.5f + width * t
                    pz = 0f; nx = 0f; nz = 1f
                } else {
                    val radius = width / arc
                    val ang = (t - 0.5f) * arc
                    px = sin(ang) * radius
                    pz = cos(ang) * radius - radius
                    nx = sin(ang); nz = cos(ang)
                }
                b.v(px, yy, pz, nx, 0f, nz, u, vv)
            }
        }
        for (j in 0 until sy) {
            for (i in 0 until sx) {
                val a = j * (sx + 1) + i
                val bb = a + sx + 1
                b.quad(a, a + 1, bb + 1, bb)
            }
        }
        return Mesh.upload(b)
    }

    /** Subdivided plane displaced by fbm noise — terrain for outdoor environments. */
    fun terrain(size: Float, seg: Int, amplitude: Float, seed: Int = 1, octaves: Int = 4): Mesh {
        val b = MeshBuilder()
        val s = max(seg, 2)
        val half = size * 0.5f
        val heights = FloatArray((s + 1) * (s + 1))
        for (j in 0..s) {
            for (i in 0..s) {
                val x = i.toFloat() / s; val z = j.toFloat() / s
                val n = Mathf.fbm2(x * 4f + seed * 13.7f, z * 4f + seed * 5.3f, octaves)
                heights[j * (s + 1) + i] = (n - 0.5f) * 2f * amplitude
            }
        }
        for (j in 0..s) {
            for (i in 0..s) {
                val px = -half + size * i / s
                val pz = -half + size * j / s
                val h = heights[j * (s + 1) + i]
                val hl = heights[j * (s + 1) + max(i - 1, 0)]
                val hr = heights[j * (s + 1) + min(i + 1, s)]
                val hd = heights[max(j - 1, 0) * (s + 1) + i]
                val hu = heights[min(j + 1, s) * (s + 1) + i]
                val step = size / s
                val nx = (hl - hr) / (2f * step)
                val nz = (hd - hu) / (2f * step)
                val l = kotlin.math.sqrt(nx * nx + 1f + nz * nz)
                b.v(px, h, pz, nx / l, 1f / l, nz / l, i.toFloat() / s, j.toFloat() / s)
            }
        }
        for (j in 0 until s) {
            for (i in 0 until s) {
                val a = j * (s + 1) + i
                val bb = a + s + 1
                b.quad(a, bb, bb + 1, a + 1)
            }
        }
        return Mesh.upload(b)
    }

    private fun min(a: Int, b: Int) = if (a < b) a else b
}
