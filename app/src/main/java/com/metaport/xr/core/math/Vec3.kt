package com.metaport.xr.core.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal allocation-light 3D vector. MetaPort runs at 60-90 fps on mobile GPUs,
 * so hot loops reuse instances instead of allocating.
 */
class Vec3(
    @JvmField var x: Float = 0f,
    @JvmField var y: Float = 0f,
    @JvmField var z: Float = 0f
) {
    fun set(x: Float, y: Float, z: Float): Vec3 {
        this.x = x; this.y = y; this.z = z
        return this
    }

    fun set(o: Vec3): Vec3 = set(o.x, o.y, o.z)

    fun set(a: FloatArray, off: Int = 0): Vec3 = set(a[off], a[off + 1], a[off + 2])

    fun copy(): Vec3 = Vec3(x, y, z)

    fun add(o: Vec3): Vec3 { x += o.x; y += o.y; z += o.z; return this }

    fun addScaled(o: Vec3, s: Float): Vec3 {
        x += o.x * s; y += o.y * s; z += o.z * s
        return this
    }

    fun sub(o: Vec3): Vec3 { x -= o.x; y -= o.y; z -= o.z; return this }

    fun mul(s: Float): Vec3 { x *= s; y *= s; z *= s; return this }

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun sqrLength(): Float = x * x + y * y + z * z

    fun distance(o: Vec3): Float {
        val dx = x - o.x; val dy = y - o.y; val dz = z - o.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun normalize(): Vec3 {
        val l = length()
        if (l > 1e-6f) { x /= l; y /= l; z /= l } else { x = 0f; y = 0f; z = -1f }
        return this
    }

    fun normalized(out: Vec3): Vec3 {
        val l = length()
        return if (l > 1e-6f) out.set(x / l, y / l, z / l) else out.set(0f, 0f, -1f)
    }

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun cross(a: Vec3, b: Vec3): Vec3 = set(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )

    fun lerp(o: Vec3, t: Float): Vec3 {
        x += (o.x - x) * t; y += (o.y - y) * t; z += (o.z - z) * t
        return this
    }

    fun clampLen(maxLen: Float): Vec3 {
        val l = length()
        if (l > maxLen && l > 1e-6f) mul(maxLen / l)
        return this
    }

    override fun toString(): String = "(%.2f, %.2f, %.2f)".format(x, y, z)

    companion object {
        val ZERO = Vec3()
        val UP = Vec3(0f, 1f, 0f)
        val FORWARD = Vec3(0f, 0f, -1f)
        val RIGHT = Vec3(1f, 0f, 0f)

        fun dist(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float {
            val dx = ax - bx; val dy = ay - by; val dz = az - bz
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
    }
}

/** Quaternion (w, x, y, z) used for smooth head / hand orientation. */
class Quat(
    @JvmField var x: Float = 0f,
    @JvmField var y: Float = 0f,
    @JvmField var z: Float = 0f,
    @JvmField var w: Float = 1f
) {
    fun identity(): Quat { x = 0f; y = 0f; z = 0f; w = 1f; return this }

    fun set(x: Float, y: Float, z: Float, w: Float): Quat {
        this.x = x; this.y = y; this.z = z; this.w = w
        return this
    }

    fun normalize(): Quat {
        val l = sqrt(x * x + y * y + z * z + w * w)
        if (l > 1e-6f) { x /= l; y /= l; z /= l; w /= l } else identity()
        return this
    }

    /** Spherical linear interpolation, falls back to lerp for tiny angles. */
    fun slerp(a: Quat, b: Quat, t: Float): Quat {
        var bx = b.x; var by = b.y; var bz = b.z; var bw = b.w
        var d = a.x * bx + a.y * by + a.z * bz + a.w * bw
        if (d < 0f) { bx = -bx; by = -by; bz = -bz; bw = -bw; d = -d }
        if (d > 0.9995f) {
            x = a.x + (bx - a.x) * t; y = a.y + (by - a.y) * t
            z = a.z + (bz - a.z) * t; w = a.w + (bw - a.w) * t
            return normalize()
        }
        val theta = acos(min(1f, max(-1f, d)))
        val s = sin(theta)
        val wa = sin((1f - t) * theta) / s
        val wb = sin(t * theta) / s
        x = a.x * wa + bx * wb; y = a.y * wa + by * wb
        z = a.z * wa + bz * wb; w = a.w * wa + bw * wb
        return normalize()
    }

    /** Writes the 3x3 rotation into the upper-left of a column-major 4x4 matrix. */
    fun toMat4(out: FloatArray, off: Int = 0) {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        out[off + 0] = 1f - 2f * (yy + zz)
        out[off + 1] = 2f * (xy + wz)
        out[off + 2] = 2f * (xz - wy)
        out[off + 3] = 0f
        out[off + 4] = 2f * (xy - wz)
        out[off + 5] = 1f - 2f * (xx + zz)
        out[off + 6] = 2f * (yz + wx)
        out[off + 7] = 0f
        out[off + 8] = 2f * (xz + wy)
        out[off + 9] = 2f * (yz - wx)
        out[off + 10] = 1f - 2f * (xx + yy)
        out[off + 11] = 0f
        out[off + 12] = 0f; out[off + 13] = 0f; out[off + 14] = 0f; out[off + 15] = 1f
    }

    companion object {
        val IDENTITY = Quat()

        fun fromAxisAngle(ax: Float, ay: Float, az: Float, rad: Float, out: Quat): Quat {
            val h = rad * 0.5f
            val s = sin(h)
            val l = sqrt(ax * ax + ay * ay + az * az)
            if (l < 1e-6f) return out.identity()
            return out.set(ax / l * s, ay / l * s, az / l * s, cos(h))
        }
    }
}

object Mathf {
    const val PI = 3.14159265358979f
    const val DEG2RAD = PI / 180f
    const val RAD2DEG = 180f / PI

    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v

    fun clamp01(v: Float): Float = clamp(v, 0f, 1f)

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Frame-rate independent exponential smoothing. */
    fun damp(a: Float, b: Float, lambda: Float, dt: Float): Float =
        lerp(a, b, 1f - kotlin.math.exp(-lambda * dt))

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp01((x - edge0) / (edge1 - edge0 + 1e-8f))
        return t * t * (3f - 2f * t)
    }

    fun sign(v: Float): Float = if (v < 0f) -1f else 1f

    fun repeat(t: Float, length: Float): Float {
        val r = t % length
        return if (r < 0f) r + length else r
    }

    /** Deterministic hash noise, used by procedural environments and game content. */
    fun hash1(n: Int): Float {
        var x = n * 747796405 + 2891336453.toInt()
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x.toFloat() / 2147483648f) * 0.5f + 0.5f
    }

    fun hash2(x: Int, y: Int): Float = hash1(x * 1619 + y * 31337)

    fun hash3(x: Int, y: Int, z: Int): Float = hash1(x * 1619 + y * 31337 + z * 6971)

    fun valueNoise2(x: Float, y: Float): Float {
        val xi = kotlin.math.floor(x).toInt(); val yi = kotlin.math.floor(y).toInt()
        val xf = x - xi; val yf = y - yi
        val u = xf * xf * (3f - 2f * xf); val v = yf * yf * (3f - 2f * yf)
        val a = hash2(xi, yi); val b = hash2(xi + 1, yi)
        val c = hash2(xi, yi + 1); val d = hash2(xi + 1, yi + 1)
        return lerp(lerp(a, b, u), lerp(c, d, u), v)
    }

    fun fbm2(x: Float, y: Float, octaves: Int = 4): Float {
        var sum = 0f; var amp = 0.5f; var freq = 1f; var norm = 0f
        for (i in 0 until octaves) {
            sum += valueNoise2(x * freq, y * freq) * amp
            norm += amp
            amp *= 0.5f; freq *= 2f
        }
        return sum / max(norm, 1e-6f)
    }

    fun abs(v: Float): Float = abs(v)
}
