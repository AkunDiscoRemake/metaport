package com.metaport.xr.core.gl

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import com.metaport.xr.core.math.Mat4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private const val TAG = "MetaPort.GL"

/** Compiles/links a program and caches uniform + attribute locations. */
class GlProgram(vertexSrc: String, fragmentSrc: String, val name: String = "prog") {
    val id: Int
    private val uniforms = HashMap<String, Int>()
    private val attribs = HashMap<String, Int>()

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSrc, "$name.vert")
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc, "$name.frag")
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        val status = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES30.glGetProgramInfoLog(p)
            GLES30.glDeleteProgram(p)
            throw RuntimeException("Link failed for $name:\n$info")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        id = p
    }

    private fun compile(type: Int, src: String, label: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val status = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES30.glGetShaderInfoLog(s)
            GLES30.glDeleteShader(s)
            throw RuntimeException("Compile failed for $label:\n$info\n--- source ---\n$src")
        }
        return s
    }

    fun use(): GlProgram {
        GLES30.glUseProgram(id)
        return this
    }

    fun attrib(name: String): Int {
        var loc = attribs[name]
        if (loc == null) {
            loc = GLES30.glGetAttribLocation(id, name)
            attribs[name] = loc
        }
        return loc
    }

    fun uniform(name: String): Int {
        var loc = uniforms[name]
        if (loc == null) {
            loc = GLES30.glGetUniformLocation(id, name)
            uniforms[name] = loc
        }
        return loc
    }

    fun setMat4(name: String, v: FloatArray, off: Int = 0): GlProgram {
        val l = uniform(name)
        if (l >= 0) GLES30.glUniformMatrix4fv(l, 1, false, v, off)
        return this
    }

    fun setVec2(name: String, x: Float, y: Float): GlProgram {
        val l = uniform(name)
        if (l >= 0) GLES30.glUniform2f(l, x, y)
        return this
    }

    fun setVec3(name: String, x: Float, y: Float, z: Float): GlProgram {
        val l = uniform(name)
        if (l >= 0) GLES30.glUniform3f(l, x, y, z)
        return this
    }

    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float): GlProgram {
        val l = uniform(name)
        if (l >= 0) GLES30.glUniform4f(l, x, y, z, w)
        return this
    }

    fun setFloat(name: String, v: Float): GlProgram {
        val l = uniform(name)
        if (l >= 0) GLES30.glUniform1f(l, v)
        return this
    }

    fun setInt(name: String, v: Int): GlProgram {
        val l = uniform(name)
        if (l >= 0) GLES30.glUniform1i(l, v)
        return this
    }

    fun setFloatArray(name: String, v: FloatArray): GlProgram {
        val l = uniform(name)
        if (l >= 0) GLES30.glUniform1fv(l, v.size, v, 0)
        return this
    }

    /** Uploads a vec3 array uniform from a flat FloatArray (count * 3 floats). */
    fun setVec3Array(name: String, v: FloatArray, count: Int): GlProgram {
        val l = uniform(name)
        if (l >= 0) GLES30.glUniform3fv(l, count, v, 0)
        return this
    }

    fun release() {
        GLES30.glDeleteProgram(id)
    }

    companion object {
        fun checkGl(where: String) {
            var err = GLES30.glGetError()
            while (err != GLES30.GL_NO_ERROR) {
                Log.e(TAG, "GL error 0x${Integer.toHexString(err)} at $where")
                err = GLES30.glGetError()
            }
        }
    }
}

fun directFloatBuffer(size: Int): FloatBuffer =
    ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

fun FloatArray.toDirect(): FloatBuffer =
    ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(this@toDirect); position(0)
    }

/** 2D texture helper. */
object Tex {
    fun create(bitmap: Bitmap, mipmaps: Boolean = true, linear: Boolean = true): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val t = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val filter = if (linear) GLES30.GL_LINEAR else GLES30.GL_NEAREST
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        if (mipmaps) GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return t
    }

    fun createEmpty(w: Int, h: Int, wrap: Int = GLES30.GL_CLAMP_TO_EDGE, filter: Int = GLES30.GL_LINEAR): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val t = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return t
    }

    /** Camera background texture for ARCore passthrough (external OES). */
    fun createExternalOes(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val t = ids[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return t
    }

    fun delete(t: Int) {
        if (t != 0) GLES30.glDeleteTextures(1, intArrayOf(t), 0)
    }
}

/** Offscreen render target used by the stereo (Cardboard) pipeline. */
class FrameBuffer(val width: Int, val height: Int, val withDepth: Boolean = true) {
    val fbo: Int
    val colorTex: Int
    private var depthRb: Int = 0

    init {
        val f = IntArray(1); GLES30.glGenFramebuffers(1, f, 0); fbo = f[0]
        colorTex = Tex.createEmpty(width, height)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, colorTex, 0
        )
        if (withDepth) {
            val r = IntArray(1); GLES30.glGenRenderbuffers(1, r, 0); depthRb = r[0]
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRb)
            GLES30.glRenderbufferStorage(
                GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, width, height
            )
            GLES30.glFramebufferRenderbuffer(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
                GLES30.GL_RENDERBUFFER, depthRb
            )
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0)
        }
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO incomplete: 0x${Integer.toHexString(status)} (${width}x$height)")
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, width, height)
    }

    fun resize(w: Int, h: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTex)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        if (depthRb != 0) {
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRb)
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, w, h)
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0)
        }
    }

    fun release() {
        if (depthRb != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(depthRb), 0)
        GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        Tex.delete(colorTex)
    }
}

/** Identity matrix shortcut for shader uploads. */
val IDENTITY_M: FloatArray = Mat4.IDENTITY
