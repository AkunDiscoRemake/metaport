package com.metaport.xr.ar

import android.app.Activity
import android.media.Image
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.Coordinates2d
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException

/** Snapshot of one tracked ARCore plane, consumed by the environment renderer. */
class PlaneInfo {
    val center = FloatArray(16)
    var extentX = 0f
    var extentZ = 0f
    var isHorizontalUp = true
    var isWall = false
    var tracked = false
    var polygon: FloatArray = FloatArray(0)
    var lastSeenMs = 0L
}

/**
 * Owns the ARCore session: visual-inertial 6DOF SLAM, horizontal + vertical
 * plane tracking, anchors, the Depth API and the camera feed used for mixed
 * reality passthrough.
 *
 * ARCore is optional at runtime — if Google Play Services for AR is missing the
 * app falls back to 3DOF stereo VR through [HeadTracker].
 */
class ArCoreHost(private val activity: Activity) {

    companion object {
        private const val TAG = "MetaPort.ARCore"
        const val MAX_PLANES = 24
    }

    var session: Session? = null
        private set

    var installed = false
        private set
    var available = false
        private set
    var running = false
        private set

    var tracking = false
        private set
    var depthSupported = false
        private set
    var lastError: String? = null

    /** World matrix of the camera for the current frame. */
    val cameraMatrix = FloatArray(16)

    /** Environment brightness (0..1+) from ARCore's light estimate. */
    var pixelIntensity = 1f
        private set
    var lightValid = false
        private set

    val planes = ArrayList<PlaneInfo>(MAX_PLANES)
    val anchors = ArrayList<Anchor>()

    private val ndcQuad = FloatArray(8)
    private val uvQuad = FloatArray(8)
    private var frameCount = 0

    init {
        android.opengl.Matrix.setIdentityM(cameraMatrix, 0)
    }

    /** True when this device can run ARCore at all. */
    fun checkAvailability(): Boolean {
        val a = ArCoreApk.getInstance().checkAvailability(activity)
        available = a.isSupported
        return available
    }

    /**
     * Creates and configures the session. Returns false (with [lastError] set)
     * when ARCore cannot start, in which case the app stays in 3DOF mode.
     */
    fun create(cameraTextureName: Int): Boolean {
        if (session != null) return true
        return try {
            val s = Session(activity)
            val config = Config(s)
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            config.focusMode = Config.FocusMode.AUTO
            depthSupported = s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            if (depthSupported) config.depthMode = Config.DepthMode.AUTOMATIC
            s.configure(config)
            s.setCameraTextureNames(intArrayOf(cameraTextureName))
            session = s
            installed = true
            lastError = null
            Log.i(TAG, "ARCore session created (depth=$depthSupported)")
            true
        } catch (e: UnavailableException) {
            val msg = "ARCore unavailable: ${e.javaClass.simpleName}"
            lastError = msg
            Log.w(TAG, msg)
            false
        } catch (t: Throwable) {
            lastError = "ARCore init failed: ${t.message}"
            Log.w(TAG, lastError, t)
            false
        }
    }

    fun resume() {
        val s = session ?: return
        try {
            s.resume()
            running = true
        } catch (e: CameraNotAvailableException) {
            lastError = "Camera not available: ${e.message}"
            running = false
        } catch (t: Throwable) {
            lastError = "ARCore resume failed: ${t.message}"
            running = false
        }
    }

    fun pause() {
        try {
            session?.pause()
        } catch (t: Throwable) {
            Log.w(TAG, "pause failed", t)
        }
        running = false
        tracking = false
    }

    fun close() {
        try {
            session?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "close failed", t)
        }
        session = null
        running = false
        tracking = false
        planes.clear()
    }

    fun setDisplayGeometry(rotation: Int, widthPx: Int, heightPx: Int) {
        try {
            session?.setDisplayGeometry(rotation, widthPx, heightPx)
        } catch (t: Throwable) {
            Log.w(TAG, "setDisplayGeometry failed", t)
        }
    }

    /**
     * Advances ARCore by one frame. Must be called once per rendered frame on
     * the GL thread. Returns the frame, or null when ARCore is not running.
     */
    fun update(): Frame? {
        val s = session ?: return null
        if (!running) return null
        val f = try {
            s.update()
        } catch (e: CameraNotAvailableException) {
            lastError = "Camera not available during update"
            return null
        } catch (t: Throwable) {
            lastError = "ARCore update failed: ${t.message}"
            return null
        }
        frameCount++

        val cam = f.camera
        tracking = cam.trackingState == TrackingState.TRACKING
        cam.pose.toMatrix(cameraMatrix, 0)

        val le = f.lightEstimate
        if (le != null && le.state == com.google.ar.core.LightEstimate.State.VALID) {
            pixelIntensity = le.pixelIntensity
            lightValid = true
        } else {
            lightValid = false
        }

        updatePlanes(f)
        return f
    }

    private fun updatePlanes(f: Frame) {
        val updated = try {
            f.getUpdatedTrackables(Plane::class.java)
        } catch (t: Throwable) {
            emptyList<Plane>()
        }
        for (p in updated) {
            if (p.trackingState == TrackingState.STOPPED) continue
            var info = planes.firstOrNull { it.center[15] == 1f && samePlane(it, p) }
            if (info == null) {
                if (planes.size >= MAX_PLANES) planes.removeAt(0)
                info = PlaneInfo()
                planes.add(info)
            }
            p.centerPose.toMatrix(info.center, 0)
            info.extentX = p.extentX
            info.extentZ = p.extentZ
            info.isHorizontalUp = p.type == Plane.Type.HORIZONTAL_UPWARD_FACING
            info.isWall = p.type == Plane.Type.VERTICAL
            info.tracked = p.trackingState == TrackingState.TRACKING
            val poly = p.polygon
            if (poly != null) {
                poly.rewind()
                val arr = FloatArray(poly.remaining())
                poly.get(arr)
                info.polygon = arr
            }
            info.lastSeenMs = System.currentTimeMillis()
        }
    }

    private fun samePlane(info: PlaneInfo, p: Plane): Boolean {
        val pose = p.centerPose
        val tx = pose.tx(); val ty = pose.ty(); val tz = pose.tz()
        return kotlin.math.abs(info.center[12] - tx) < 0.01f &&
            kotlin.math.abs(info.center[13] - ty) < 0.01f &&
            kotlin.math.abs(info.center[14] - tz) < 0.01f
    }

    /** Full-resolution YUV camera image for hand tracking. Caller must close it. */
    fun acquireCameraImage(frame: Frame): Image? {
        return try {
            frame.acquireCameraImage()
        } catch (t: Throwable) {
            null
        }
    }

    /** 16-bit depth image (millimetres) when the device supports the Depth API. */
    fun acquireDepthImage(frame: Frame): Image? {
        if (!depthSupported) return null
        return try {
            frame.acquireDepthImage16Bits()
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Fills [out] with normalised texture coordinates for a fullscreen quad that
     * samples the camera texture with the correct display orientation.
     */
    fun cameraBackgroundUvs(frame: Frame, out: FloatArray) {
        // Counter-clockwise NDC corners of the fullscreen quad.
        ndcQuad[0] = -1f; ndcQuad[1] = -1f
        ndcQuad[2] = -1f; ndcQuad[3] = 1f
        ndcQuad[4] = 1f; ndcQuad[5] = -1f
        ndcQuad[6] = 1f; ndcQuad[7] = 1f
        try {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, ndcQuad,
                Coordinates2d.TEXTURE_NORMALIZED, uvQuad
            )
            System.arraycopy(uvQuad, 0, out, 0, 8)
        } catch (t: Throwable) {
            out[0] = 0f; out[1] = 0f
            out[2] = 0f; out[3] = 1f
            out[4] = 1f; out[5] = 0f
            out[6] = 1f; out[7] = 1f
        }
    }

    /** Raycasts the gaze ray against tracked planes / feature points. */
    fun hitTest(frame: Frame, xPx: Float, yPx: Float): HitResult? {
        val results = try {
            frame.hitTest(xPx, yPx)
        } catch (t: Throwable) {
            return null
        }
        for (r in results) {
            val trackable = r.trackable
            if (trackable is Plane &&
                trackable.isPoseInPolygon(r.hitPose) &&
                trackable.trackingState == TrackingState.TRACKING
            ) {
                return r
            }
        }
        return results.firstOrNull()
    }

    fun createAnchor(pose: Pose): Anchor? {
        val s = session ?: return null
        return try {
            s.createAnchor(pose).also { anchors.add(it) }
        } catch (t: Throwable) {
            null
        }
    }

    fun clearAnchors() {
        for (a in anchors) {
            try { a.detach() } catch (t: Throwable) {}
        }
        anchors.clear()
    }

    fun statusMap(): LinkedHashMap<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m["available"] = available
        m["sessionActive"] = session != null
        m["running"] = running
        m["tracking"] = tracking
        m["depthApi"] = depthSupported
        m["planes"] = planes.size
        m["anchors"] = anchors.size
        m["frames"] = frameCount
        m["lightIntensity"] = pixelIntensity
        lastError?.let { m["error"] = it }
        return m
    }
}
