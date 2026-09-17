package com.metaport.xr

import android.app.Activity
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.metaport.xr.ar.ArCoreHost
import com.metaport.xr.ar.HandGestureResolver
import com.metaport.xr.ar.HandModel
import com.metaport.xr.ar.HandTracker
import com.metaport.xr.ar.HeadTracker
import com.metaport.xr.audio.Sfx
import com.metaport.xr.core.gl.MeshGen
import com.metaport.xr.core.math.Mathf
import com.metaport.xr.core.math.Mat4
import com.metaport.xr.devapi.DevApiServer
import com.metaport.xr.devapi.MetaPortSdk
import com.metaport.xr.devapi.Telemetry
import com.metaport.xr.env.EnvContext
import com.metaport.xr.env.Environments
import com.metaport.xr.games.Game
import com.metaport.xr.games.GameAssets
import com.metaport.xr.games.GameContext
import com.metaport.xr.games.GameRegistry
import com.metaport.xr.input.CardboardInput
import com.metaport.xr.input.InputState
import com.metaport.xr.render.HandRenderer
import com.metaport.xr.render.MixedRealityLayer
import com.metaport.xr.render.Pipeline
import com.metaport.xr.render.StereoPipeline
import com.metaport.xr.scene.GlState
import com.metaport.xr.scene.Material
import com.metaport.xr.scene.Scene
import com.metaport.xr.stereo.ViewerProfile
import com.metaport.xr.ui.GameHud
import com.metaport.xr.ui.HomeHub
import com.metaport.xr.ui.RuntimeApi
import com.metaport.xr.ui.RuntimeSettings
import com.metaport.xr.ui.UiRenderContext
import com.metaport.xr.ui.UiRoot
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * The MetaPort runtime: one GL surface, stereo Cardboard rendering, ARCore
 * 6DOF tracking, hand tracking, the spatial UI and the game host.
 *
 * Frame order per eye:
 *   camera passthrough (MR) -> sky dome -> environment -> scene -> hands -> UI -> reticle
 * then both eyes are composited through the viewer's lens distortion.
 */
class MetaPortRuntime(
    private val activity: Activity,
    override val profile: ViewerProfile,
    val settings: RuntimeSettings,
    val cardboardInput: CardboardInput
) : GLSurfaceView.Renderer, RuntimeApi, DevApiServer.Host {

    companion object {
        private const val TAG = "MetaPort.Runtime"
    }

    // ---- core systems -------------------------------------------------
    val pipeline = Pipeline()
    val stereo = StereoPipeline()
    val scene = Scene(2048)
    override val uiRoot = UiRoot()
    val arCore = ArCoreHost(activity)
    val headTracker = HeadTracker(
        activity.getSystemService(Activity.SENSOR_SERVICE) as android.hardware.SensorManager
    )
    val handTracker = HandTracker()
    val handRenderer = HandRenderer()
    val mrLayer = MixedRealityLayer()
    val telemetry = Telemetry()
    val devApi = DevApiServer(8765)

    val input = InputState()
    val gameCtx = GameContext()
    val envCtx = EnvContext()

    val leftHand = HandModel(false)
    val rightHand = HandModel(true)
    private val gestureResolver = HandGestureResolver()

    // ---- state --------------------------------------------------------
    private var home: HomeHub? = null
    private var hud: GameHud? = null
    var currentGame: Game? = null
        private set

    var stereoEnabled = true
    var mrEnabled = false
    var time = 0f
        private set
    private var lastNanos = 0L
    private var surfaceW = 0
    private var surfaceH = 0
    private var glReady = false
    private var cameraTexture = 0
    private var arFrame: com.google.ar.core.Frame? = null
    private val cameraUvs = FloatArray(8)
    private var uiCtx = UiRenderContext()
    private var hudYaw = 0f
    private var pendingGameId: String? = null
    private var pendingEnvId: String? = null
    private var ready = false

    // Scratch matrices
    private val viewHead = FloatArray(16)
    private val viewEye = FloatArray(16)
    private val viewEyeRot = FloatArray(16)
    private val projEye = FloatArray(16)
    private val eyeShift = FloatArray(16)
    private val tmp = FloatArray(16)

    init {
        devApi.host = this
        handTracker.swapHands = settings.swapHands
    }

    // ================================================================= GL lifecycle

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.01f, 0.015f, 0.04f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        pipeline.init()
        UiAssetsInit()
        uiRoot.init()
        GameAssets.init()
        handRenderer.init()
        mrLayer.init()

        cameraTexture = com.metaport.xr.core.gl.Tex.createExternalOes()
        arCore.checkAvailability()
        if (arCore.available) {
            arCore.create(cameraTexture)
        } else {
            Log.i(TAG, "ARCore unavailable — running 3DOF stereo VR")
        }
        headTracker.start()
        Sfx.init()
        Sfx.setStreamVolume(settings.volume)

        Environments.register(com.metaport.xr.env.NebulaVoid())
        Environments.register(com.metaport.xr.env.CyberCity())
        Environments.register(com.metaport.xr.env.SpaceStation())
        Environments.register(com.metaport.xr.env.ForestValley())
        Environments.register(com.metaport.xr.env.DesertOasis())
        Environments.register(com.metaport.xr.env.Dojo())
        Environments.register(com.metaport.xr.env.PassthroughMR())
        Environments.select(settings.environmentId)

        home = HomeHub(this).also { it.build(GameRegistry.all()) }
        glReady = true
    }

    private fun UiAssetsInit() = com.metaport.xr.ui.UiAssets.init()

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceW = width
        surfaceH = height
        val scale = profile.renderScale.coerceIn(0.4f, 1.5f)
        stereo.ensureSize((width * scale).toInt(), (height * scale).toInt())
        arCore.setDisplayGeometry(activity.windowManager.defaultDisplay.rotation, width, height)
        GLES30.glViewport(0, 0, width, height)
    }

    fun onResume() {
        arCore.resume()
        cardboardInput.start()
        if (settings.devApi) devApi.start()
    }

    fun onPause() {
        arCore.pause()
        cardboardInput.stop()
        devApi.stop()
    }

    fun release() {
        home?.dispose()
        hud?.dispose()
        uiRoot.release()
        handRenderer.release()
        mrLayer.release()
        GameAssets.release()
        com.metaport.xr.ui.UiAssets.release()
        pipeline.release()
        stereo.release()
        Environments.releaseAll()
        arCore.close()
        headTracker.stop()
        devApi.stop()
        Sfx.stop()
        glReady = false
    }

    // ================================================================= frame

    override fun onDrawFrame(gl: GL10?) {
        if (!glReady) return
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0.001f, 0.1f)
        lastNanos = now
        time += dt

        val tAr = System.nanoTime()
        arFrame = arCore.update()
        val arcoreMs = (System.nanoTime() - tAr) / 1e6f

        // Head pose: 6DOF from ARCore, else 3DOF sensors.
        headTracker.smoothing = settings.motionSmoothing
        headTracker.update(dt, arFrame != null, arCore.cameraMatrix, arCore.tracking)
        System.arraycopy(headTracker.viewMatrix, 0, viewHead, 0, 16)

        // Input
        cardboardInput.newFrame()
        input.newFrame()
        input.triggerDown = cardboardInput.down
        input.triggerPressed = cardboardInput.pressed
        input.triggerReleased = cardboardInput.released
        if (input.snapTurnRequest != 0f) {
            headTracker.requestSnapTurn(input.snapTurnRequest)
            input.snapTurnRequest = 0f
        }
        if (cardboardInput.released && cardboardInput.lastSource == "magnet") {
            // Magnet release with no press registered means the press was swallowed
            // by another source; nothing to do.
        }

        headTracker.position(gameCtx.headPos)
        headTracker.forward(gameCtx.headForward)
        System.arraycopy(headTracker.headMatrix, 0, gameCtx.headMatrix, 0, 16)
        gameCtx.headRight[0] = headTracker.headMatrix[0]
        gameCtx.headRight[1] = headTracker.headMatrix[1]
        gameCtx.headRight[2] = headTracker.headMatrix[2]
        gameCtx.headUp[0] = headTracker.headMatrix[4]
        gameCtx.headUp[1] = headTracker.headMatrix[5]
        gameCtx.headUp[2] = headTracker.headMatrix[6]

        // Gaze ray
        System.arraycopy(gameCtx.headPos, 0, input.gazeOrigin, 0, 3)
        System.arraycopy(gameCtx.headForward, 0, input.gazeDir, 0, 3)
        System.arraycopy(input.gazeOrigin, 0, gameCtx.gazeOrigin, 0, 3)
        System.arraycopy(input.gazeDir, 0, gameCtx.gazeDir, 0, 3)

        // Hand tracking
        val tHand = System.nanoTime()
        handTracker.enabled = settings.handTracking
        handTracker.swapHands = settings.swapHands
        val frame = arFrame
        if (frame != null && settings.handTracking) {
            val cam = arCore.acquireCameraImage(frame)
            val depth = arCore.acquireDepthImage(frame)
            try {
                handTracker.process(cam, depth, arCore.cameraMatrix, leftHand, rightHand, dt)
            } finally {
                try { depth?.close() } catch (t: Throwable) {}
                try { cam?.close() } catch (t: Throwable) {}
            }
        } else {
            leftHand.reset()
            rightHand.reset()
        }
        gestureResolver.resolve(leftHand, dt)
        gestureResolver.resolve(rightHand, dt)
        val handMs = (System.nanoTime() - tHand) / 1e6f

        updateHandInput()

        // Passthrough UVs for MR
        if (mrEnabled && frame != null) arCore.cameraBackgroundUvs(frame, cameraUvs)

        // Environment + game update
        envCtx.dt = dt
        envCtx.time = time
        envCtx.headX = gameCtx.headPos[0]
        envCtx.headY = gameCtx.headPos[1]
        envCtx.headZ = gameCtx.headPos[2]
        envCtx.mrBlend = if (mrEnabled) 1f else 0f
        envCtx.lightIntensity = if (arCore.lightValid) arCore.pixelIntensity else 1f

        val env = Environments.current
        env?.update(dt, time)

        gameCtx.dt = dt
        gameCtx.time = time
        gameCtx.leftHand = leftHand
        gameCtx.rightHand = rightHand
        gameCtx.handTracking = settings.handTracking && (leftHand.visible || rightHand.visible)
        gameCtx.mrMode = mrEnabled
        currentGame?.update(gameCtx)

        // UI update
        val tUi = System.nanoTime()
        uiRoot.dwellSeconds = settings.dwellSeconds
        System.arraycopy(input.gazeOrigin, 0, uiRoot.rayOrigin, 0, 3)
        System.arraycopy(input.gazeDir, 0, uiRoot.rayDir, 0, 3)
        input.leftHand = leftHand
        input.rightHand = rightHand
        input.handsEnabled = leftHand.visible || rightHand.visible
        uiRoot.update(dt, input, time)
        home?.update(time, quickStatus())
        if (hud != null) currentGame?.let { hud!!.update(it.hud()) }
        val uiMs = (System.nanoTime() - tUi) / 1e6f

        // Deferred commands from the Dev API thread.
        pendingGameId?.let { doLaunchGame(it); pendingGameId = null }
        pendingEnvId?.let { Environments.select(it)?.let { e -> settings.environmentId = e.id }; pendingEnvId = null }

        // ---------------------------------------------------------- render
        pipeline.time = time
        pipeline.camX = gameCtx.headPos[0]
        pipeline.camY = gameCtx.headPos[1]
        pipeline.camZ = gameCtx.headPos[2]
        env?.applyLighting(pipeline)

        scene.clear()
        env?.let {
            it.currentView = viewHead
            it.currentProj = projEye
            it.render(scene, pipeline, envCtx)
        }
        if (mrEnabled) mrLayer.renderPlanes(scene, arCore, time)
        currentGame?.render(gameCtx)
        handRenderer.visible = settings.showHands
        handRenderer.render(scene, leftHand, floatArrayOf(0.2f, 0.85f, 1f), time)
        handRenderer.render(scene, rightHand, floatArrayOf(1f, 0.35f, 0.7f), time)

        computeEyeMatrices()

        stereo.beginFrame(0.008f, 0.012f, 0.035f)
        for (eye in 0..1) {
            stereo.beginEye(eye)
            buildEyeMatrices(eye)

            if (mrEnabled && frame != null && cameraTexture != 0) {
                mrLayer.renderBackground(
                    pipeline, cameraTexture, cameraUvs,
                    floatArrayOf(1f, 1f, 1f), settings.mrBrightness, 1.05f,
                    0.25f, settings.mrScanline, time
                )
            }

            env?.let {
                if (!(mrEnabled && it is com.metaport.xr.env.PassthroughMR)) {
                    pipeline.drawSky(
                        it.skyMode, viewEyeRot, projEye,
                        it.skyTint[0], it.skyTint[1], it.skyTint[2],
                        it.skyHorizon[0], it.skyHorizon[1], it.skyHorizon[2]
                    )
                }
            }

            pipeline.setViewProjection(viewEye, projEye)
            scene.render(pipeline, viewEye, projEye)

            // Spatial UI, drawn per eye so it has true stereo depth.
            renderUi(eye)
            uiRoot.renderReticle(scene, pipeline, time)
            pipeline.setViewProjection(viewEye, projEye)
            scene.render(pipeline, viewEye, projEye)
            stereo.endEye()
        }
        stereo.endFrame()

        GLES30.glViewport(0, 0, surfaceW, surfaceH)
        stereo.present(pipeline, profile, surfaceW, surfaceH, stereoEnabled)
        GlState.reset()

        telemetry.frame(
            System.nanoTime(), scene.drawCount, scene.drawCount,
            handMs, arcoreMs, uiMs
        )
    }

    private fun renderUi(eye: Int) {
        uiCtx.pipeline = pipeline
        uiCtx.camX = pipeline.camX
        uiCtx.camY = pipeline.camY
        uiCtx.camZ = pipeline.camZ
        uiCtx.time = time
        uiCtx.globalAlpha = 1f

        for (panel in uiRoot.panels) {
            if (panel.appear <= 0.001f) continue
            panel.scale = settings.uiScale
            panel.updateTransform()
            panel.render(uiCtx, pipeline)
        }
    }

    // ================================================================= eye matrices

    private fun computeEyeMatrices() {
        // Nothing cached yet; matrices are built per eye in buildEyeMatrices.
    }

    /**
     * Builds a physically correct per-eye view/projection pair from the viewer
     * profile: the frustum is offset so each eye looks through its own lens.
     */
    private fun buildEyeMatrices(eye: Int) {
        val halfIpd = profile.effectiveInterLens * 0.5f
        val sign = if (eye == 0) -1f else 1f

        // view_eye = translate(-eyeOffset) * view_head
        Matrix.setIdentityM(eyeShift, 0)
        Matrix.translateM(eyeShift, 0, -sign * halfIpd, 0f, 0f)
        Matrix.multiplyMM(viewEye, 0, viewHead, 0, eyeShift, 0)

        // Rotation-only view for the sky dome.
        System.arraycopy(viewEye, 0, viewEyeRot, 0, 16)
        viewEyeRot[12] = 0f; viewEyeRot[13] = 0f; viewEyeRot[14] = 0f

        val screenToLens = profile.screenToLens.coerceAtLeast(0.01f)
        val halfW = profile.screenWidth * 0.5f
        val halfH = profile.screenHeight * 0.5f
        val lensX = sign * halfIpd
        val lensY = if (profile.bottomAligned) profile.trayToLens - halfH else 0f

        val near = 0.05f
        val far = 400f
        val k = near / screenToLens

        var left = (-halfW - lensX) * k
        var right = (halfW - lensX) * k
        var bottom = (-halfH - lensY) * k
        var top = (halfH - lensY) * k

        // Honour the user's FOV preference while preserving the asymmetry.
        val impliedHalfFov = kotlin.math.atan(((top - bottom) * 0.5f) / near)
        val targetHalfFov = profile.fovDegrees * 0.5f * Mathf.DEG2RAD
        if (impliedHalfFov > 1e-4f) {
            val s = kotlin.math.tan(targetHalfFov) / kotlin.math.tan(impliedHalfFov)
            left *= s; right *= s; bottom *= s; top *= s
        }
        Matrix.frustumM(projEye, 0, left, right, bottom, top, near, far)
    }

    // ================================================================= hand -> input

    private var leftPinchPrev = false
    private var rightPinchPrev = false

    private fun updateHandInput() {
        val lPinch = leftHand.visible && leftHand.gesture == HandModel.GESTURE_PINCH
        val rPinch = rightHand.visible && rightHand.gesture == HandModel.GESTURE_PINCH
        input.leftPinch = lPinch
        input.rightPinch = rPinch
        input.leftPinchEdge = lPinch && !leftPinchPrev
        input.rightPinchEdge = rPinch && !rightPinchPrev
        if (input.leftPinchEdge || input.rightPinchEdge) Sfx.play(Sfx.GRAB, 0.4f)
        leftPinchPrev = lPinch
        rightPinchPrev = rPinch
        input.leftOpen = leftHand.visible && leftHand.gesture == HandModel.GESTURE_OPEN
        input.rightOpen = rightHand.visible && rightHand.gesture == HandModel.GESTURE_OPEN
    }

    // ================================================================= RuntimeApi

    override fun startGame(id: String) {
        pendingGameId = id
    }

    private fun doLaunchGame(id: String) {
        if (id.isEmpty()) {
            // Library button: nothing else to do here, the hub already lists titles.
            return
        }
        val g = GameRegistry.byId(id) ?: return
        currentGame?.onExit(gameCtx)
        home?.panel?.visible = false
        currentGame = g
        hud?.dispose()
        hud = GameHud(this).also { it.attach(g.title) }
        gameCtx.scene = scene
        gameCtx.pipeline = pipeline
        gameCtx.input = input
        g.onEnter(gameCtx)
        Sfx.play(Sfx.SELECT)
        MetaPortSdk.emit(MetaPortSdk.EV_GAME_LAUNCHED, mapOf("id" to id))
    }

    override fun exitToHome() {
        currentGame?.onExit(gameCtx)
        currentGame = null
        hud?.dispose()
        hud = null
        home?.panel?.visible = true
        home?.closeOverlays()
        Sfx.play(Sfx.BACK)
        MetaPortSdk.emit(MetaPortSdk.EV_GAME_EXITED)
    }

    override fun selectEnvironment(id: String): Boolean {
        val e = Environments.byId(id) ?: return false
        pendingEnvId = id
        return true
    }

    override fun setMixedReality(on: Boolean) {
        mrEnabled = on && arCore.session != null
        settings.mixedReality = mrEnabled
        if (mrEnabled) {
            Environments.select("passthrough")
            settings.environmentId = "passthrough"
        }
    }

    override fun setHandTracking(on: Boolean) {
        settings.handTracking = on
        handTracker.enabled = on
    }

    override fun setStereo(on: Boolean) {
        stereoEnabled = on
        settings.stereoDistortion = on
    }

    override fun setDevApi(on: Boolean) {
        settings.devApi = on
        if (on) devApi.start() else devApi.stop()
    }

    override fun devApiUrls(): List<String> = devApi.localAddresses()

    override fun recentEvents(): List<Map<String, Any?>> = MetaPortSdk.recentEvents(12)

    override fun statusSnapshot(): Map<String, Any?> = fullStatus()

    override fun runtimeSettings(): RuntimeSettings = settings

    // ================================================================= DevApiServer.Host

    private fun fullStatus(): LinkedHashMap<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["app"] = "MetaPort XR"
        m["version"] = MetaPortSdk.VERSION
        m["uptimeSec"] = "%.1f".format(time)
        m["stereo"] = stereoEnabled
        m["mixedReality"] = mrEnabled
        m["handTracking"] = settings.handTracking
        m["environment"] = settings.environmentId
        m["game"] = currentGame?.id
        m["arcore"] = arCore.statusMap()
        m["input"] = cardboardInput.statusMap()
        m["viewer"] = profile.toMap()
        m["telemetry"] = telemetry.toMap()
        return m
    }

    private fun quickStatus(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["tracking"] = when {
            !arCore.tracking && headTracker.has6Dof -> "lost"
            headTracker.has6Dof -> "6DOF"
            headTracker.sensorActive -> "3DOF"
            else -> "off"
        }
        m["planes"] = arCore.planes.size.toString()
        val handsVisible = (if (leftHand.visible) 1 else 0) + (if (rightHand.visible) 1 else 0)
        m["hands"] = if (settings.handTracking) "$handsVisible/2" else "off"
        m["fps"] = "%.0f".format(telemetry.fps)
        m["mode"] = when {
            mrEnabled -> "MR"
            stereoEnabled -> "VR"
            else -> "FLAT"
        }
        return m
    }

    override fun statusJson(): Map<String, Any?> = fullStatus()
    override fun telemetryJson(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m.putAll(telemetry.toMap())
        m["uptimeSec"] = "%.1f".format(time)
        m["events"] = MetaPortSdk.recentEvents(8)
        return m
    }

    override fun handsJson(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["enabled"] = settings.handTracking
        m["processingMs"] = "%.2f".format(handTracker.lastProcessingMs)
        m["detected"] = handTracker.detectedCount
        m["left"] = leftHand.toMap()
        m["right"] = rightHand.toMap()
        return m
    }

    override fun planesJson(): Map<String, Any?> {
        val list = ArrayList<Map<String, Any?>>()
        for (p in arCore.planes) {
            val pm = LinkedHashMap<String, Any?>()
            pm["x"] = "%.3f".format(p.center[12])
            pm["y"] = "%.3f".format(p.center[13])
            pm["z"] = "%.3f".format(p.center[14])
            pm["extentX"] = "%.2f".format(p.extentX)
            pm["extentZ"] = "%.2f".format(p.extentZ)
            pm["horizontal"] = p.isHorizontalUp
            pm["wall"] = p.isWall
            pm["tracking"] = p.tracked
            list.add(pm)
        }
        val m = LinkedHashMap<String, Any?>()
        m["count"] = arCore.planes.size
        m["depthApi"] = arCore.depthSupported
        m["planes"] = list
        return m
    }

    override fun listGames(): List<Map<String, Any?>> = GameRegistry.all().map {
        linkedMapOf(
            "id" to it.id, "title" to it.title, "category" to it.category,
            "tagline" to it.tagline, "requiresHands" to it.requiresHands,
            "running" to (currentGame?.id == it.id)
        )
    }

    override fun listEnvironments(): List<Map<String, Any?>> = Environments.all().map {
        linkedMapOf(
            "id" to it.id, "name" to it.name, "description" to it.description,
            "active" to (settings.environmentId == it.id)
        )
    }

    override fun launchGame(id: String): Boolean {
        if (GameRegistry.byId(id) == null) return false
        pendingGameId = id
        return true
    }

    override fun stopGame(): Boolean {
        if (currentGame == null) return false
        pendingGameId = null
        activity.runOnUiThread { exitToHome() }
        return true
    }

    override fun setEnvironment(id: String): Boolean {
        if (Environments.byId(id) == null) return false
        pendingEnvId = id
        return true
    }

    override fun injectInput(action: String, value: Float): Boolean {
        return when (action) {
            "trigger" -> { input.triggerPressed = true; input.triggerDown = true; true }
            "trigger_release" -> { input.triggerDown = false; input.triggerReleased = true; true }
            "snap_left" -> { input.snapTurnRequest = -settings.snapTurn; true }
            "snap_right" -> { input.snapTurnRequest = settings.snapTurn; true }
            "back" -> { activity.runOnUiThread { exitToHome() }; true }
            "move" -> { input.moveX = value; true }
            "strafe" -> { input.moveY = value; true }
            "restart" -> { currentGame?.restart(gameCtx); true }
            else -> false
        }
    }

    override fun setSetting(key: String, value: String): Boolean {
        val f = value.toFloatOrNull()
        return when (key) {
            "handTracking" -> { setHandTracking(value == "true"); true }
            "stereo" -> { setStereo(value == "true"); true }
            "mixedReality" -> { setMixedReality(value == "true"); true }
            "devApi" -> { setDevApi(value == "true"); true }
            "showPlanes" -> { settings.showPlanes = value == "true"; true }
            "volume" -> { f?.let { settings.volume = it; Sfx.setStreamVolume(it) }; true }
            "uiScale" -> { f?.let { settings.uiScale = it.coerceIn(0.5f, 2f) }; true }
            "uiDistance" -> { f?.let { settings.uiDistance = it.coerceIn(0.8f, 3f) }; true }
            "snapTurn" -> { f?.let { settings.snapTurn = it.coerceIn(0f, 90f) }; true }
            "ipdAdjust" -> { f?.let { profile.ipdAdjust = it }; true }
            "fov" -> { f?.let { profile.fovDegrees = it.coerceIn(50f, 130f) }; true }
            "renderScale" -> {
                f?.let {
                    profile.renderScale = it.coerceIn(0.4f, 1.5f)
                    stereo.ensureSize(
                        (surfaceW * profile.renderScale).toInt(),
                        (surfaceH * profile.renderScale).toInt()
                    )
                }
                true
            }
            else -> false
        }
    }

    /** Called by the activity when the viewer profile is recalibrated. */
    fun onProfileChanged() {
        ViewerProfile.save(activity, profile)
        if (surfaceW > 0) {
            stereo.ensureSize(
                (surfaceW * profile.renderScale).toInt(),
                (surfaceH * profile.renderScale).toInt()
            )
        }
    }

    /** True once the GL thread has created all GPU resources. */
    fun glSurfaceReady(): Boolean = glReady

    /**
     * Creates the ARCore session on the GL thread after the user installs
     * Google Play Services for AR mid-session. Must be called from queueEvent.
     */
    fun initArCoreOnGlThread() {
        if (glReady && arCore.session == null) {
            if (arCore.available || arCore.checkAvailability()) {
                arCore.create(cameraTexture)
                arCore.setDisplayGeometry(
                    activity.windowManager.defaultDisplay.rotation, surfaceW, surfaceH
                )
                arCore.resume()
                Log.i(TAG, "ARCore session created after install")
            }
        }
    }
}
