package com.metaport.xr.ar

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.metaport.xr.core.math.Mathf
import com.metaport.xr.core.math.Mat4

/**
 * Head pose source.
 *
 * - With ARCore: full 6DOF (ARCore's visual-inertial odometry feeds position
 *   and orientation directly from the SLAM estimate).
 * - Without ARCore: 3DOF from the rotation-vector sensor, which still gives a
 *   correct Cardboard experience on phones without Google Play Services for AR.
 */
class HeadTracker(private val sensors: SensorManager) : SensorEventListener {

    /** World-space camera matrix (position + orientation). */
    val headMatrix = FloatArray(16)

    /** Rotation only, for sky domes. */
    val headRotation = FloatArray(16)

    /** View matrix = inverse(headMatrix). */
    val viewMatrix = FloatArray(16)

    var posX = 0f; var posY = 0f; var posZ = 0f
    var yaw = 0f; var pitch = 0f; var roll = 0f

    var has6Dof = false
    var tracking = false
    var sensorActive = false
        private set

    /** Comfort: yaw snap increment in degrees and the smoothing factor. */
    var snapTurnDegrees = 0f
    var smoothing = 22f

    private val rot = FloatArray(9)
    private val orient = FloatArray(4)
    private val rawMatrix = FloatArray(16)
    private var yawOffset = 0f
    private var pendingSnap = 0f

    private var smoothPos = FloatArray(3)
    private var initialised = false

    init {
        Mat4.multiply(headMatrix, 0, Mat4.IDENTITY, 0, Mat4.IDENTITY, 0)
        Mat4.multiply(headRotation, 0, Mat4.IDENTITY, 0, Mat4.IDENTITY, 0)
    }

    fun start() {
        val s = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (s != null) {
            sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
            sensorActive = true
        }
    }

    fun stop() {
        sensors.unregisterListener(this)
        sensorActive = false
    }

    /** Called by ARCore when a tracked camera pose is available (6DOF). */
    fun setPoseFromArCore(matrix: FloatArray, isTracking: Boolean) {
        has6Dof = true
        tracking = isTracking
        if (!isTracking) return
        System.arraycopy(matrix, 0, rawMatrix, 0, 16)
        applyPose()
    }

    /** Called when ARCore is unavailable so 3DOF takes over. */
    fun use3Dof() {
        has6Dof = false
    }

    fun requestSnapTurn(degrees: Float) {
        pendingSnap += degrees
    }

    fun resetYaw() {
        yawOffset = -yaw
    }

    private fun applyPose() {
        // Extract yaw/pitch/roll from the 3x3 rotation for comfort + UI facing.
        val m = rawMatrix
        pitch = kotlin.math.asin(Mathf.clamp(-m[6], -1f, 1f))
        yaw = kotlin.math.atan2(m[2], m[10])
        roll = kotlin.math.atan2(m[4], m[5])

        if (!initialised) {
            smoothPos[0] = m[12]; smoothPos[1] = m[13]; smoothPos[2] = m[14]
            initialised = true
        }

        // Rebuild with the user's yaw offset (snap turn + recentre).
        val finalYaw = yaw + yawOffset + snapTurnDegrees * Mathf.DEG2RAD + pendingSnap * Mathf.DEG2RAD
        if (pendingSnap != 0f) {
            snapTurnDegrees += pendingSnap
            pendingSnap = 0f
        }
        Mat4.compose(
            headMatrix,
            smoothPos[0], smoothPos[1], smoothPos[2],
            finalYaw, pitch, roll, 1f, 1f, 1f
        )
        System.arraycopy(headMatrix, 0, headRotation, 0, 16)
        headRotation[12] = 0f; headRotation[13] = 0f; headRotation[14] = 0f
        Mat4.multiply(viewMatrix, 0, inverseOf(headMatrix), 0, Mat4.IDENTITY, 0)
        posX = smoothPos[0]; posY = smoothPos[1]; posZ = smoothPos[2]
    }

    fun update(dt: Float, arPoseAvailable: Boolean, arMatrix: FloatArray?, arTracking: Boolean) {
        if (arPoseAvailable && arTracking && arMatrix != null) {
            has6Dof = true
            tracking = true
            System.arraycopy(arMatrix, 0, rawMatrix, 0, 16)
            if (!initialised) {
                smoothPos[0] = rawMatrix[12]; smoothPos[1] = rawMatrix[13]; smoothPos[2] = rawMatrix[14]
                initialised = true
            } else {
                val k = 1f - kotlin.math.exp(-smoothing * dt)
                smoothPos[0] += (rawMatrix[12] - smoothPos[0]) * k
                smoothPos[1] += (rawMatrix[13] - smoothPos[1]) * k
                smoothPos[2] += (rawMatrix[14] - smoothPos[2]) * k
            }
            applyPose()
        } else if (!has6Dof) {
            tracking = sensorActive
            applyPose()
        } else {
            tracking = false
        }
    }

    private val inv = FloatArray(16)
    private fun inverseOf(src: FloatArray): FloatArray {
        Mat4.multiply(inv, 0, src, 0, Mat4.IDENTITY, 0) // copy
        val tmp = FloatArray(16)
        android.opengl.Matrix.invertM(tmp, 0, inv, 0)
        return tmp
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        if (has6Dof && tracking) return
        SensorManager.getRotationMatrixFromVector(rot, event.values)
        SensorManager.getOrientation(rot, orient)
        yaw = orient[0]
        pitch = orient[1]
        roll = orient[2]
        // Position stays at the origin in 3DOF; height comes from calibration.
        rawMatrix[12] = 0f; rawMatrix[13] = posY; rawMatrix[14] = 0f
        Mat4.compose(rawMatrix, 0f, posY, 0f, yaw, pitch, roll, 1f, 1f, 1f)
        smoothPos[0] = 0f; smoothPos[1] = posY; smoothPos[2] = 0f
        initialised = true
        applyPose()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Forward vector of the head, used for gaze rays. */
    fun forward(out: FloatArray) {
        out[0] = -headMatrix[8]; out[1] = -headMatrix[9]; out[2] = -headMatrix[10]
    }

    fun position(out: FloatArray) {
        out[0] = headMatrix[12]; out[1] = headMatrix[13]; out[2] = headMatrix[14]
    }
}
