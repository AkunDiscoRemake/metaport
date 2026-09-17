package com.metaport.cardboardxr.arcore

import android.content.Context
import android.util.Log
import com.google.ar.core.*
import com.google.ar.core.exceptions.*

class ARCoreManager(private val context: Context) {
    private var session: Session? = null
    private var isSupported = false
    private var isTracking = false
    var slamPosition = FloatArray(3)
    var slamRotation = FloatArray(4)
    var trackingState = TrackingState.STOPPED

    init { checkSupport() }

    private fun checkSupport() {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            isSupported = availability == ArCoreApk.Availability.SUPPORTED_INSTALLED
        } catch (e: Exception) { isSupported = false }
    }

    fun isARCoreSupported(): Boolean = try { isSupported } catch (e: Exception) { false }

    fun createSession(): Boolean {
        if (!isSupported) return false
        return try {
            session = Session(context)
            val config = Config(session).apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                focusMode = Config.FocusMode.AUTO
                try { if (session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) depthMode = Config.DepthMode.AUTOMATIC } catch (e: Exception) {}
            }
            session?.configure(config)
            true
        } catch (e: Exception) { false }
    }

    fun onResume() {
        try {
            if (session == null) { if (!createSession()) return }
            session?.resume()
        } catch (e: Exception) {}
    }

    fun onPause() { try { session?.pause() } catch (e: Exception) {} }
    fun onDestroy() { try { session?.close(); session = null } catch (e: Exception) {} }
    fun getSession(): Session? = session
    fun isTracking(): Boolean = isTracking
}
