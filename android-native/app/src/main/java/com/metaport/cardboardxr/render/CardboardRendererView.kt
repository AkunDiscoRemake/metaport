package com.metaport.cardboardxr.render

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.metaport.cardboardxr.arcore.ARCoreManager
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

/**
 * CardboardRendererView - Render 3D Spatial com OpenGL ES 3.0
 * Crash-proof com try-catch em tudo
 */
class CardboardRendererView(
    context: Context,
    private val arCoreManager: ARCoreManager?
) : GLSurfaceView(context) {

    private var renderer: MetaPortRenderer

    init {
        try {
            setEGLContextClientVersion(3)
            // Tenta config com depth
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            renderer = MetaPortRenderer(context, arCoreManager)
            setRenderer(renderer)
            renderMode = RENDERMODE_CONTINUOUSLY
            Log.d("MetaPort-Render", "✅ Renderer criado")
        } catch (e: Exception) {
            Log.e("MetaPort-Render", "Erro init renderer, usando fallback", e)
            renderer = MetaPortRenderer(context, null)
            setRenderer(renderer)
            renderMode = RENDERMODE_CONTINUOUSLY
        }
    }

    override fun onResume() {
        try {
            super.onResume()
        } catch (e: Exception) {
            Log.e("MetaPort-Render", "Erro onResume", e)
        }
    }

    override fun onPause() {
        try {
            super.onPause()
        } catch (e: Exception) {
            Log.e("MetaPort-Render", "Erro onPause", e)
        }
    }

    fun onDestroy() {
        try {
            renderer.onDestroy()
        } catch (e: Exception) {
            Log.e("MetaPort-Render", "Erro onDestroy", e)
        }
    }

    fun setEnvironment(envName: String) {
        try {
            renderer.setEnvironment(envName)
        } catch (e: Exception) {
            Log.e("MetaPort-Render", "Erro setEnvironment", e)
        }
    }

    fun launchGame(gameName: String) {
        try {
            renderer.launchGame(gameName)
        } catch (e: Exception) {
            Log.e("MetaPort-Render", "Erro launchGame", e)
        }
    }

    class MetaPortRenderer(
        private val context: Context,
        private val arCoreManager: ARCoreManager?
    ) : Renderer {

        private var currentEnv = "Tropical Island"
        private var currentGame = "Home"
        private var angle = 0f

        private val projectionMatrix = FloatArray(16)
        private val viewMatrix = FloatArray(16)
        private val modelMatrix = FloatArray(16)

        // Cores dos ambientes
        private val envColors = mapOf(
            "Tropical Island" to floatArrayOf(0.3f, 0.7f, 1f, 1f),
            "Cyber City" to floatArrayOf(0.1f, 0.05f, 0.3f, 1f),
            "Space Station" to floatArrayOf(0.05f, 0.05f, 0.08f, 1f),
            "Forest Valley" to floatArrayOf(0.2f, 0.6f, 0.3f, 1f),
            "Nebula Void" to floatArrayOf(0.4f, 0.1f, 0.6f, 1f),
            "Void Space" to floatArrayOf(0f, 0f, 0f, 1f),
            "Japanese Dojo" to floatArrayOf(1f, 0.5f, 0.2f, 1f),
            "Desert Oasis" to floatArrayOf(0.9f, 0.8f, 0.5f, 1f),
            "Passthrough" to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        )

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            try {
                GLES30.glClearColor(0.05f, 0.07f, 0.1f, 1.0f)
                GLES30.glEnable(GLES30.GL_DEPTH_TEST)
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                Log.d("MetaPort-Render", "✅ Surface created - OpenGL ES 3.0")
            } catch (e: Exception) {
                Log.e("MetaPort-Render", "Erro onSurfaceCreated", e)
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            try {
                GLES30.glViewport(0, 0, width, height)
                val ratio = width.toFloat() / height.toFloat()
                Matrix.perspectiveM(projectionMatrix, 0, 60f, ratio, 0.05f, 100f)
                Matrix.setLookAtM(viewMatrix, 0, 0f, 1.6f, 3f, 0f, 1.6f, -1f, 0f, 1f, 0f)
                Log.d("MetaPort-Render", "Surface changed: ${width}x${height}")
            } catch (e: Exception) {
                Log.e("MetaPort-Render", "Erro onSurfaceChanged", e)
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            try {
                // Cor baseada no ambiente atual
                val color = envColors[currentEnv] ?: floatArrayOf(0.05f, 0.07f, 0.1f, 1f)
                GLES30.glClearColor(color[0], color[1], color[2], color[3])
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

                angle += 0.3f

                // Renderiza ambiente (cor de fundo por enquanto - depois 3D real)
                // Em versão completa, renderizaria meshes 3D de cada ambiente

                // Simula 6DOF - se ARCore tracking, move câmera levemente
                try {
                    arCoreManager?.let { ar ->
                        if (ar.isTracking()) {
                            // Usa posição SLAM pra mover view
                            val pos = ar.slamPosition
                            Matrix.setLookAtM(
                                viewMatrix, 0,
                                pos[0], 1.6f + pos[1], 3f + pos[2],
                                pos[0], 1.6f + pos[1], -1f + pos[2],
                                0f, 1f, 0f
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Ignora erro ARCore no render
                }

                // Renderiza jogo atual - por enquanto só log
                if (currentGame != "Home") {
                    // Em versão completa, renderizaria jogo 3D
                }

            } catch (e: Exception) {
                Log.e("MetaPort-Render", "Erro onDrawFrame", e)
                try {
                    GLES30.glClearColor(0.1f, 0f, 0f, 1f)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                } catch (e2: Exception) {
                }
            }
        }

        fun setEnvironment(envName: String) {
            try {
                currentEnv = envName
                Log.d("MetaPort-Render", "Ambiente: $envName")
            } catch (e: Exception) {
                Log.e("MetaPort-Render", "Erro setEnvironment", e)
            }
        }

        fun launchGame(gameName: String) {
            try {
                currentGame = gameName
                Log.d("MetaPort-Render", "Jogo: $gameName")
            } catch (e: Exception) {
                Log.e("MetaPort-Render", "Erro launchGame", e)
            }
        }

        fun onDestroy() {
            try {
                Log.d("MetaPort-Render", "Renderer destroy")
            } catch (e: Exception) {
            }
        }
    }
}
