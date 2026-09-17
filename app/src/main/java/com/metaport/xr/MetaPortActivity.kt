package com.metaport.xr

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.metaport.xr.audio.Sfx
import com.metaport.xr.core.math.Mathf
import com.metaport.xr.devapi.MetaPortSdk
import com.metaport.xr.games.CubeCascade
import com.metaport.xr.games.ChronoStrike
import com.metaport.xr.games.DeepReel
import com.metaport.xr.games.DrumForge
import com.metaport.xr.games.FirstSteps
import com.metaport.xr.games.GameRegistry
import com.metaport.xr.games.GravityLeap
import com.metaport.xr.games.HoverPutt
import com.metaport.xr.games.NebulaHoops
import com.metaport.xr.games.OrbKeeper
import com.metaport.xr.games.PulseBlade
import com.metaport.xr.games.VoxelGarden
import com.metaport.xr.input.CardboardInput
import com.metaport.xr.stereo.ViewerProfile

/**
 * MetaPort entry point.
 *
 * Locks to landscape (the only orientation that makes sense in a Cardboard
 * viewer), requests the camera for ARCore passthrough, and hosts the GL surface
 * that [MetaPortRuntime] renders into.
 */
class MetaPortActivity : Activity() {

    companion object {
        private const val TAG = "MetaPort.Activity"
        private const val REQ_CAMERA = 1001
    }

    private lateinit var glView: GLSurfaceView
    private lateinit var runtime: MetaPortRuntime
    private lateinit var profile: ViewerProfile
    private lateinit var settings: com.metaport.xr.ui.RuntimeSettings
    private lateinit var input: CardboardInput
    private var userRequestedAr = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        registerGames()

        profile = ViewerProfile()
        ViewerProfile.load(this, profile)
        ViewerProfile.fromDisplay(this, profile)

        settings = com.metaport.xr.ui.RuntimeSettings()
        settings.uiDistance = profile.screenToLens.coerceIn(1.0f, 2.6f).let { 1.55f }
        input = CardboardInput(this)

        runtime = MetaPortRuntime(this, profile, settings, input)

        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(3)
        glView.setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        glView.setPreserveEGLContextOnPause(true)
        glView.setRenderer(runtime)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        setContentView(glView)

        MetaPortSdk.setSetting("device", Build.MODEL)
        MetaPortSdk.setSetting("androidSdk", Build.VERSION.SDK_INT.toString())

        if (hasCameraPermission()) {
            userRequestedAr = true
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA
            )
        }
    }

    private fun registerGames() {
        if (GameRegistry.count() > 0) return
        GameRegistry.register(FirstSteps())
        GameRegistry.register(PulseBlade())
        GameRegistry.register(DrumForge())
        GameRegistry.register(ChronoStrike())
        GameRegistry.register(CubeCascade())
        GameRegistry.register(OrbKeeper())
        GameRegistry.register(NebulaHoops())
        GameRegistry.register(HoverPutt())
        GameRegistry.register(GravityLeap())
        GameRegistry.register(DeepReel())
        GameRegistry.register(VoxelGarden())
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            userRequestedAr = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (!userRequestedAr) Log.i(TAG, "Camera denied — MR and hand tracking disabled")
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()

        if (userRequestedAr && !runtime.arCore.available) {
            // Offer to install Google Play Services for AR, then re-check.
            try {
                val result = ArCoreApk.getInstance().requestInstall(this, true)
                if (result == ArCoreApk.InstallStatus.INSTALL_REQUESTED) return
            } catch (t: Throwable) {
                Log.w(TAG, "ARCore install prompt failed: ${t.message}")
            }
            runtime.arCore.checkAvailability()
            if (runtime.arCore.available && runtime.arCore.session == null) {
                // The GL surface creates the session on the GL thread; nudge it.
                glView.queueEvent {
                    if (!runtime.glSurfaceReady()) runtime.initArCoreOnGlThread()
                }
            }
        }

        glView.onResume()
        runtime.onResume()
    }

    override fun onPause() {
        runtime.onPause()
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        glView.queueEvent { runtime.release() }
        super.onDestroy()
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ------------------------------------------------------------------ input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                input.onTouchDown()
                lastTapTime.let {
                    val now = System.currentTimeMillis()
                    if (now - it < 280L) runtime.input.doubleTap = true
                    lastTapTime = now
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> input.onTouchUp()
        }
        return true
    }

    private var lastTapTime = 0L

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (input.onKey(event)) return true
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (runtime.currentGame != null) {
                        runtime.exitToHome()
                        return true
                    }
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    settings.volume = Mathf.clamp01(settings.volume + 0.1f)
                    Sfx.setStreamVolume(settings.volume)
                    Sfx.play(Sfx.TICK, 0.5f)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> { runtime.input.snapTurnRequest = -settings.snapTurn; return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { runtime.input.snapTurnRequest = settings.snapTurn; return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
