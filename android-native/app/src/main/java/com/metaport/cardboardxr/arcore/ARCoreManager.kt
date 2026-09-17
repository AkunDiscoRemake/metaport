package com.metaport.cardboardxr.arcore

import android.content.Context
import android.util.Log
import com.google.ar.core.*
import com.google.ar.core.exceptions.*

/**
 * ARCore Manager - 6DOF SLAM + Environment Tracking
 * Crash-proof com checagem completa de runtime
 */
class ARCoreManager(private val context: Context) {

    private var session: Session? = null
    private var isSupported = false
    private var isTracking = false

    // 6DOF
    var slamPosition = FloatArray(3)
    var slamRotation = FloatArray(4)
    var trackingState = TrackingState.STOPPED

    init {
        checkSupport()
    }

    private fun checkSupport() {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            Log.d("MetaPort-ARCore", "Availability: $availability")
            isSupported = when (availability) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> true
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    try {
                        ArCoreApk.getInstance().requestInstall(context as android.app.Activity, true)
                        false
                    } catch (e: Exception) {
                        false
                    }
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("MetaPort-ARCore", "Erro checkSupport", e)
            isSupported = false
        }
    }

    fun isARCoreSupported(): Boolean {
        return try {
            isSupported
        } catch (e: Exception) {
            false
        }
    }

    fun createSession(): Boolean {
        if (!isSupported) {
            Log.w("MetaPort-ARCore", "ARCore não suportado, não cria session")
            return false
        }

        return try {
            session = Session(context)
            val config = Config(session).apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                focusMode = Config.FocusMode.AUTO
                try {
                    if (session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        depthMode = Config.DepthMode.AUTOMATIC
                        Log.d("MetaPort-ARCore", "Depth API ativado")
                    }
                } catch (e: Exception) {
                    Log.w("MetaPort-ARCore", "Depth não suportado", e)
                }
            }
            session?.configure(config)
            Log.d("MetaPort-ARCore", "✅ Session criada com 6DOF")
            true
        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e("MetaPort-ARCore", "ARCore não instalado", e)
            false
        } catch (e: UnavailableUserDeclinedInstallationException) {
            Log.e("MetaPort-ARCore", "Usuário recusou ARCore", e)
            false
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e("MetaPort-ARCore", "Device não compatível", e)
            false
        } catch (e: Exception) {
            Log.e("MetaPort-ARCore", "Erro genérico createSession", e)
            false
        }
    }

    fun onResume() {
        try {
            if (session == null) {
                if (!createSession()) {
                    Log.w("MetaPort-ARCore", "Não conseguiu criar session no onResume")
                    return
                }
            }
            session?.resume()
            Log.d("MetaPort-ARCore", "Session resume")
        } catch (e: CameraNotAvailableException) {
            Log.e("MetaPort-ARCore", "Câmera não disponível", e)
        } catch (e: Exception) {
            Log.e("MetaPort-ARCore", "Erro onResume", e)
        }
    }

    fun onPause() {
        try {
            session?.pause()
        } catch (e: Exception) {
            Log.e("MetaPort-ARCore", "Erro onPause", e)
        }
    }

    fun onDestroy() {
        try {
            session?.close()
            session = null
        } catch (e: Exception) {
            Log.e("MetaPort-ARCore", "Erro onDestroy", e)
        }
    }

    fun update(frame: Frame?): Boolean {
        if (frame == null) return false
        
        return try {
            val camera = frame.camera
            trackingState = camera.trackingState
            isTracking = trackingState == TrackingState.TRACKING

            if (isTracking) {
                val pose = camera.pose
                pose.translation.let {
                    slamPosition[0] = it[0]
                    slamPosition[1] = it[1]
                    slamPosition[2] = it[2]
                }
                pose.rotationQuaternion.let {
                    slamRotation[0] = it[0]
                    slamRotation[1] = it[1]
                    slamRotation[2] = it[2]
                    slamRotation[3] = it[3]
                }
            }
            isTracking
        } catch (e: Exception) {
            Log.e("MetaPort-ARCore", "Erro update", e)
            false
        }
    }

    fun getSession(): Session? = session
    fun isTracking(): Boolean = isTracking
}
