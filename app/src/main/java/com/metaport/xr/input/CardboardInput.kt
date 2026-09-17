package com.metaport.xr.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.KeyEvent
import com.metaport.xr.core.math.Mathf

/**
 * Cardboard trigger sources.
 *
 * A viewer gives you one button, and different viewers implement it differently,
 * so MetaPort listens to all three at once:
 *  - the magnetic ring (a magnetometer magnitude spike),
 *  - indirect touch (the finger pressing the screen through the foam),
 *  - the volume-down key, which many viewers wire to a Bluetooth remote.
 */
class CardboardInput(context: Context) : SensorEventListener {

    var magnetEnabled = true
    var touchEnabled = true
    var keyEnabled = true

    /** Debounce so one press is never counted twice by two sensors. */
    private var lockUntil = 0L

    var down = false
        private set
    var pressed = false
        private set
    var released = false
        private set

    var lastSource = "none"
        private set

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var magnet: Sensor? = null
    private var baseline = 0f
    private var samples = 0
    private var magnitude = 0f
    var threshold = 18f
    var calibration = 0f

    fun start() {
        magnet = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
            ?: sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        magnet?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensors.unregisterListener(this)
    }

    fun newFrame() {
        pressed = false
        released = false
    }

    fun onTouchDown() {
        if (touchEnabled) press("touch")
    }

    fun onTouchUp() {
        if (down) release()
    }

    fun onKey(event: KeyEvent): Boolean {
        if (!keyEnabled) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
            event.keyCode != KeyEvent.KEYCODE_DPAD_CENTER
        ) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> press("key")
            KeyEvent.ACTION_UP -> release()
        }
        return true
    }

    private fun press(source: String) {
        val now = System.currentTimeMillis()
        if (now < lockUntil) return
        if (!down) {
            down = true
            pressed = true
            lastSource = source
        }
    }

    private fun release() {
        if (down) {
            down = false
            released = true
            lockUntil = System.currentTimeMillis() + 90
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!magnetEnabled) return
        val v = event.values
        val m = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        magnitude = m
        // Learn the ambient field slowly so the trigger works anywhere on Earth.
        if (samples < 40) {
            baseline += (m - baseline) * 0.2f
            samples++
        } else {
            baseline += (m - baseline) * 0.004f
        }
        calibration = baseline
        val delta = m - baseline
        if (delta > threshold) {
            press("magnet")
        } else if (down && lastSource == "magnet" && delta < threshold * 0.45f) {
            release()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun statusMap(): LinkedHashMap<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m["down"] = down
        m["source"] = lastSource
        m["magnetEnabled"] = magnetEnabled
        m["magnetPresent"] = magnet != null
        m["fieldMagnitude"] = "%.2f".format(magnitude)
        m["fieldBaseline"] = "%.2f".format(calibration)
        m["threshold"] = threshold
        return m
    }

    /** Auto-calibrates the trigger threshold from observed ambient noise. */
    fun autoTune() {
        threshold = Mathf.clamp(kotlin.math.abs(calibration) * 0.18f + 8f, 8f, 60f)
    }
}
