package com.metaport.xr.core.math

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Column-major 4x4 matrix helpers, matching OpenGL / ARCore conventions
 * (right-handed, +X right, +Y up, -Z forward).
 */
class Mat4 {
    @JvmField
    val m = FloatArray(16)

    init {
        Matrix.setIdentityM(m, 0)
    }

    fun identity(): Mat4 { Matrix.setIdentityM(m, 0); return this }

    fun set(src: FloatArray, off: Int = 0): Mat4 { System.arraycopy(src, off, m, 0, 16); return this }

    fun copyTo(dst: FloatArray, off: Int = 0): Mat4 { System.arraycopy(m, 0, dst, off, 16); return this }

    fun translation(x: Float, y: Float, z: Float): Mat4 {
        Matrix.setIdentityM(m, 0)
        m[12] = x; m[13] = y; m[14] = z
        return this
    }

    fun scale(x: Float, y: Float, z: Float): Mat4 {
        Matrix.setIdentityM(m, 0)
        m[0] = x; m[5] = y; m[10] = z
        return this
    }

    fun rotateY(deg: Float): Mat4 {
        Matrix.setIdentityM(m, 0)
        Matrix.rotateM(m, 0, deg, 0f, 1f, 0f)
        return this
    }

    fun perspective(fovyDeg: Float, aspect: Float, near: Float, far: Float): Mat4 {
        Matrix.perspectiveM(m, 0, fovyDeg, aspect, near, far)
        return this
    }

    /** Asymmetric frustum — required for correct Cardboard per-eye stereo. */
    fun perspectiveAsym(l: Float, r: Float, b: Float, t: Float, n: Float, f: Float): Mat4 {
        Matrix.frustumM(m, 0, l, r, b, t, n, f)
        return this
    }

    fun lookAt(eye: FloatArray, center: FloatArray, up: FloatArray): Mat4 {
        Matrix.setLookAtM(m, 0, eye[0], eye[1], eye[2], center[0], center[1], center[2], up[0], up[1], up[2])
        return this
    }

    fun multiply(a: FloatArray, b: FloatArray): Mat4 {
        Matrix.multiplyMM(m, 0, a, 0, b, 0)
        return this
    }

    fun invert(src: FloatArray): Mat4 {
        Matrix.invertM(m, 0, src, 0)
        return this
    }

    /** Extracts the translation column. */
    fun position(out: FloatArray, off: Int = 0): Mat4 {
        out[off] = m[12]; out[off + 1] = m[13]; out[off + 2] = m[14]
        return this
    }

    /** Forward vector (-Z column) of this transform. */
    fun forward(out: FloatArray, off: Int = 0): Mat4 {
        out[off] = -m[8]; out[off + 1] = -m[9]; out[off + 2] = -m[10]
        val l = sqrt(out[off] * out[off] + out[off + 1] * out[off + 1] + out[off + 2] * out[off + 2])
        if (l > 1e-6f) { out[off] /= l; out[off + 1] /= l; out[off + 2] /= l }
        return this
    }

    fun right(out: FloatArray, off: Int = 0): Mat4 {
        out[off] = m[0]; out[off + 1] = m[1]; out[off + 2] = m[2]
        val l = sqrt(out[off] * out[off] + out[off + 1] * out[off + 1] + out[off + 2] * out[off + 2])
        if (l > 1e-6f) { out[off] /= l; out[off + 1] /= l; out[off + 2] /= l }
        return this
    }

    fun up(out: FloatArray, off: Int = 0): Mat4 {
        out[off] = m[4]; out[off + 1] = m[5]; out[off + 2] = m[6]
        val l = sqrt(out[off] * out[off] + out[off + 1] * out[off + 1] + out[off + 2] * out[off + 2])
        if (l > 1e-6f) { out[off] /= l; out[off + 1] /= l; out[off + 2] /= l }
        return this
    }

    companion object {
        val IDENTITY = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

        fun multiply(out: FloatArray, outOff: Int, a: FloatArray, aOff: Int, b: FloatArray, bOff: Int) {
            Matrix.multiplyMM(out, outOff, a, aOff, b, bOff)
        }

        fun translateInPlace(dst: FloatArray, x: Float, y: Float, z: Float) {
            Matrix.translateM(dst, 0, x, y, z)
        }

        fun transformPoint(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray, off: Int = 0) {
            val ox = m[0] * x + m[4] * y + m[8] * z + m[12]
            val oy = m[1] * x + m[5] * y + m[9] * z + m[13]
            val oz = m[2] * x + m[6] * y + m[10] * z + m[14]
            val w = m[3] * x + m[7] * y + m[11] * z + m[15]
            val iw = if (kotlin.math.abs(w) > 1e-8f) 1f / w else 1f
            out[off] = ox * iw; out[off + 1] = oy * iw; out[off + 2] = oz * iw
        }

        fun transformDir(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray, off: Int = 0) {
            out[off] = m[0] * x + m[4] * y + m[8] * z
            out[off + 1] = m[1] * x + m[5] * y + m[9] * z
            out[off + 2] = m[2] * x + m[6] * y + m[10] * z
        }

        /** Builds a world matrix from position, YXZ euler angles (radians) and scale. */
        fun compose(
            out: FloatArray,
            px: Float, py: Float, pz: Float,
            yaw: Float, pitch: Float, roll: Float,
            sx: Float = 1f, sy: Float = 1f, sz: Float = 1f
        ) {
            val cy = cos(yaw); val sy = sin(yaw)
            val cp = cos(pitch); val sp = sin(pitch)
            val cr = cos(roll); val sr = sin(roll)

            // R = Ry * Rx * Rz
            val m00 = cy * cr + sy * sp * sr
            val m01 = -cy * sr + sy * sp * cr
            val m02 = sy * cp
            val m10 = cp * sr
            val m11 = cp * cr
            val m12 = -sp
            val m20 = -sy * cr + cy * sp * sr
            val m21 = sy * sr + cy * sp * cr
            val m22 = cy * cp

            out[0] = m00 * sx; out[1] = m01 * sx; out[2] = m02 * sx; out[3] = 0f
            out[4] = m10 * sy; out[5] = m11 * sy; out[6] = m12 * sy; out[7] = 0f
            out[8] = m20 * sz; out[9] = m21 * sz; out[10] = m22 * sz; out[11] = 0f
            out[12] = px; out[13] = py; out[14] = pz; out[15] = 1f
        }

        fun composeQuat(
            out: FloatArray,
            px: Float, py: Float, pz: Float,
            q: Quat,
            sx: Float = 1f, sy: Float = 1f, sz: Float = 1f
        ) {
            q.toMat4(out, 0)
            out[0] *= sx; out[1] *= sx; out[2] *= sx
            out[4] *= sy; out[5] *= sy; out[6] *= sy
            out[8] *= sz; out[9] *= sz; out[10] *= sz
            out[12] = px; out[13] = py; out[14] = pz
        }

        /** Extracts an orthonormal basis; useful to orient objects along a direction. */
        fun lookRotation(out: FloatArray, eye: FloatArray, target: FloatArray, up: FloatArray) {
            var zx = eye[0] - target[0]; var zy = eye[1] - target[1]; var zz = eye[2] - target[2]
            var l = sqrt(zx * zx + zy * zy + zz * zz)
            if (l < 1e-6f) { zx = 0f; zy = 0f; zz = 1f; l = 1f }
            zx /= l; zy /= l; zz /= l
            var xx = up[1] * zz - up[2] * zy
            var xy = up[2] * zx - up[0] * zz
            var xz = up[0] * zy - up[1] * zx
            l = sqrt(xx * xx + xy * xy + xz * xz)
            if (l < 1e-6f) { xx = 1f; xy = 0f; xz = 0f; l = 1f }
            xx /= l; xy /= l; xz /= l
            val yx = zy * xz - zz * xy
            val yy = zz * xx - zx * xz
            val yz = zx * xy - zy * xx
            out[0] = xx; out[1] = xy; out[2] = xz; out[3] = 0f
            out[4] = yx; out[5] = yy; out[6] = yz; out[7] = 0f
            out[8] = zx; out[9] = zy; out[10] = zz; out[11] = 0f
            out[12] = eye[0]; out[13] = eye[1]; out[14] = eye[2]; out[15] = 1f
        }
    }
}
