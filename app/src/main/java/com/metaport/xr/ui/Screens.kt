package com.metaport.xr.ui

import com.metaport.xr.core.gl.FontAtlas
import com.metaport.xr.core.math.Mathf
import com.metaport.xr.games.Game
import com.metaport.xr.stereo.ViewerProfile

/** What a screen is allowed to do to the runtime. Keeps the UI decoupled. */
interface RuntimeApi {
    val uiRoot: UiRoot
    val profile: ViewerProfile
    fun startGame(id: String)
    fun exitToHome()
    fun selectEnvironment(id: String): Boolean
    fun setMixedReality(on: Boolean)
    fun setHandTracking(on: Boolean)
    fun setStereo(on: Boolean)
    fun setDevApi(on: Boolean)
    fun devApiUrls(): List<String>
    fun recentEvents(): List<Map<String, Any?>>
    fun statusSnapshot(): Map<String, Any?>
    fun runtimeSettings(): RuntimeSettings
}

/** User-adjustable runtime settings. */
class RuntimeSettings {
    var handTracking = true
    var stereoDistortion = true
    var mixedReality = false
    var devApi = true
    var showPlanes = true
    var comfortVignette = 0.35f
    var snapTurn = 30f
    var volume = 0.7f
    var motionSmoothing = 22f
    var uiScale = 1f
    var uiDistance = 1.55f
    var environmentId = "nebula-void"
    var swapHands = false
    var dwellSeconds = 1.05f
    var showHands = true
    var mrScanline = 0.25f
    var mrBrightness = 1.05f
}

/**
 * The MetaPort home hub: a wide curved glass console floating in front of the
 * user, with a live status strip, an arc of game cards and side panels.
 */
class HomeHub(private val rt: RuntimeApi) {
    val panel = Panel3D("MetaPort", width = 1.5f, height = 0.92f, curvature = 0.42f)
    private val statusLabels = ArrayList<Label>()
    private val clock = Label("", Theme.TEXT_TINY, FontAtlas.ALIGN_RIGHT, Theme.textSecondary.clone())
    private val gameCards = ArrayList<Button>()
    private val versionLabel = Label("", Theme.TEXT_TINY, FontAtlas.ALIGN_LEFT, Theme.textDim.clone())

    fun build(games: List<Game>) {
        panel.posY = 1.42f
        panel.posZ = -rt.runtimeSettings().uiDistance
        panel.accentIndex = Theme.CYAN
        panel.fill = floatArrayOf(0.03f, 0.05f, 0.12f, 0.55f)
        panel.subtitle = "Spatial VR + mixed reality runtime — Cardboard stereo, ARCore 6DOF SLAM, hand tracking"

        // Hero wordmark.
        panel.add(Label("METAPORT", 0.15f, FontAtlas.ALIGN_LEFT,
            floatArrayOf(0.9f, 0.97f, 1f, 1f), 0.35f).also {
            it.x = -0.42f; it.y = 0.20f; it.width = 0.9f; it.height = 0.16f
            it.letterSpacing = 0.02f
        })
        panel.add(Label("SPATIAL RUNTIME v${com.metaport.xr.devapi.MetaPortSdk.VERSION}", Theme.TEXT_TINY,
            FontAtlas.ALIGN_LEFT, Theme.textDim.clone()).also {
            it.x = -0.62f; it.y = 0.10f; it.width = 1.1f; it.height = 0.03f
            it.letterSpacing = 0.008f
        })

        // Live status strip.
        val statNames = arrayOf("TRACKING", "PLANES", "HANDS", "FPS", "MODE")
        for (i in statNames.indices) {
            val x = -0.60f + i * 0.245f
            panel.add(Label(statNames[i], Theme.TEXT_TINY, FontAtlas.ALIGN_CENTER,
                floatArrayOf(0.45f, 0.6f, 0.8f, 1f)).also {
                it.x = x + 0.09f; it.y = 0.02f; it.width = 0.22f; it.height = 0.025f
                it.letterSpacing = 0.006f
            })
            val v = Label("--", Theme.TEXT_SMALL, FontAtlas.ALIGN_CENTER,
                floatArrayOf(0.14f, 0.9f, 1f, 1f), 0.4f)
            v.x = x + 0.09f; v.y = -0.035f; v.width = 0.22f; v.height = 0.04f
            panel.add(v)
            statusLabels.add(v)
        }

        // Game cards in an arc along the bottom half.
        val cols = 4
        for ((i, g) in games.take(8).withIndex()) {
            val col = i % cols
            val row = i / cols
            val b = Button(g.title, Theme.TEXT_BODY) { rt.startGame(g.id) }
            b.subtitle = "${g.category} · ${g.tagline}"
            b.accentIndex = g.accentIndex
            b.width = 0.335f
            b.height = 0.135f
            b.x = -0.52f + col * 0.348f
            b.y = -0.185f - row * 0.155f
            b.tag = g.id
            panel.add(b)
            gameCards.add(b)
        }

        // Bottom row: quick actions.
        val actionLabels = arrayOf("Library", "Settings", "Dev API", "MR Camera")
        val actionAccents = intArrayOf(Theme.VIOLET, Theme.AMBER, Theme.LIME, Theme.ICE)
        val actionHandlers = arrayOf<() -> Unit>(
            { showLibrary() },
            { SettingsScreen(rt).show() },
            { DevConsoleScreen(rt).show() },
            { rt.setMixedReality(!rt.runtimeSettings().mixedReality) }
        )
        for (i in actionLabels.indices) {
            val b = Button(actionLabels[i], Theme.TEXT_SMALL, actionHandlers[i])
            b.accentIndex = actionAccents[i]
            b.width = 0.215f
            b.height = 0.062f
            b.x = -0.575f + i * 0.245f
            b.y = -0.40f
            b.showAccentBar = false
            panel.add(b)
        }

        panel.add(clock.also {
            it.x = 0.68f; it.y = 0.36f; it.width = 0.26f; it.height = 0.03f
        })
        versionLabel.text = "ARCore 6DOF · Cardboard optics · ${games.size} games"
        panel.add(versionLabel.also {
            it.x = -0.71f; it.y = -0.42f; it.width = 0.8f; it.height = 0.03f
        })

        rt.uiRoot.add(panel)
    }

    private var library: LibraryScreen? = null
    private var settings: SettingsScreen? = null
    private var dev: DevConsoleScreen? = null

    private fun showLibrary() {
        library = LibraryScreen(rt).also { it.build(com.metaport.xr.games.GameRegistry.all()) }
    }

    private fun openSettings() {
        settings = SettingsScreen(rt).also { it.show() }
    }

    private fun openDev() {
        dev = DevConsoleScreen(rt).also { it.show() }
    }

    /** Closes any overlay panel opened from the hub. */
    fun closeOverlays() {
        library?.dispose(); library = null
        settings?.dispose(); settings = null
        dev?.dispose(); dev = null
    }

    fun update(time: Float, status: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        val vals = listOf(
            (status["tracking"] as? String) ?: "--",
            (status["planes"] as? String) ?: "--",
            (status["hands"] as? String) ?: "--",
            (status["fps"] as? String) ?: "--",
            (status["mode"] as? String) ?: "--"
        )
        for (i in statusLabels.indices) {
            statusLabels[i].text = vals[i]
            val ok = vals[i] != "--" && vals[i] != "off" && vals[i] != "0"
            val c = statusLabels[i].color
            c[0] = if (ok) 0.14f else 0.5f
            c[1] = if (ok) 0.9f else 0.5f
            c[2] = if (ok) 1f else 0.55f
        }
        val total = (time % 86400f)
        val h = (total / 3600f).toInt() % 24
        val m = (total / 60f).toInt() % 60
        val s = total.toInt() % 60
        clock.text = "%02d:%02d:%02d".format(h, m, s)
    }

    fun dispose() {
        rt.uiRoot.remove(panel)
        panel.release()
    }
}

/** Scrollable list of every registered title. */
class LibraryScreen(private val rt: RuntimeApi) {
    val panel = Panel3D("Game Library", width = 1.35f, height = 1.0f, curvature = 0.46f)
    private val rows = ArrayList<Button>()
    private var scroll = 0f

    fun build(games: List<Game>) {
        panel.posY = 1.42f
        panel.posZ = -rt.runtimeSettings().uiDistance - 0.05f
        panel.accentIndex = Theme.VIOLET
        panel.subtitle = "${games.size} original titles — procedurally generated, no assets required"

        val perPage = 6
        for ((i, g) in games.withIndex()) {
            val b = Button(g.title, Theme.TEXT_BODY) { rt.launchGame(g.id) }
            b.subtitle = "${g.category} · ${g.tagline}" + if (g.requiresHands) " · hands recommended" else ""
            b.accentIndex = g.accentIndex
            b.width = 1.18f
            b.height = 0.105f
            b.x = 0f
            b.y = 0.26f - (i % perPage) * 0.118f
            b.tag = g.id
            panel.add(b)
            rows.add(b)
        }

        val back = Button("Back", Theme.TEXT_BODY) { rt.exitToHome() }
        back.width = 0.2f; back.height = 0.062f
        back.x = -0.55f; back.y = -0.44f
        back.accentIndex = Theme.ICE
        panel.add(back)

        val next = Button("Page", Theme.TEXT_BODY) { scroll = (scroll + 1) % ((games.size + perPage - 1) / perPage).coerceAtLeast(1) }
        next.width = 0.2f; next.height = 0.062f
        next.x = 0.55f; next.y = -0.44f
        next.accentIndex = Theme.VIOLET
        panel.add(next)

        rt.uiRoot.add(panel)
        applyScroll(games.size, perPage)
    }

    private fun applyScroll(count: Int, perPage: Int) {
        val start = scroll * perPage
        for ((i, b) in rows.withIndex()) b.visible = i >= start && i < start + perPage
    }

    fun dispose() {
        rt.uiRoot.remove(panel)
        panel.release()
    }
}

/** Calibration + comfort + system toggles. */
class SettingsScreen(private val rt: RuntimeApi) {
    val panel = Panel3D("Settings", width = 1.4f, height = 1.0f, curvature = 0.44f)

    fun show() {
        val s = rt.runtimeSettings()
        panel.posY = 1.42f
        panel.posZ = -s.uiDistance - 0.02f
        panel.accentIndex = Theme.AMBER
        panel.subtitle = "Viewer optics, comfort and system options. Changes apply immediately."

        var y = 0.28f
        fun row() = y.also { y -= 0.088f }

        // Optics
        val ipd = Slider("Inter-lens distance", rt.profile.interLensDistance * 1000f, 50f, 80f) {
            rt.profile.interLensDistance = it / 1000f
        }
        ipd.format = { "%.1f mm".format(it) }
        ipd.x = -0.33f; ipd.y = row(); ipd.width = 0.6f
        panel.add(ipd)

        val fov = Slider("Field of view", rt.profile.fovDegrees, 60f, 120f) {
            rt.profile.fovDegrees = it
        }
        fov.format = { "%.0f°".format(it) }
        fov.x = -0.33f; fov.y = row(); fov.width = 0.6f
        panel.add(fov)

        val scale = Slider("Render scale", rt.profile.renderScale, 0.5f, 1.4f) {
            rt.profile.renderScale = it
        }
        scale.x = -0.33f; scale.y = row(); scale.width = 0.6f
        panel.add(scale)

        val k1 = Slider("Lens distortion k1", rt.profile.distortionK1, -0.2f, 0.9f) {
            rt.profile.distortionK1 = it
        }
        k1.x = -0.33f; k1.y = row(); k1.width = 0.6f
        panel.add(k1)

        // Comfort
        val vig = Slider("Comfort vignette", s.comfortVignette, 0f, 1f) { s.comfortVignette = it }
        vig.x = 0.34f; vig.y = 0.28f; vig.width = 0.6f
        panel.add(vig)

        val smooth = Slider("Motion smoothing", s.motionSmoothing, 6f, 40f) { s.motionSmoothing = it }
        smooth.format = { "%.0f".format(it) }
        smooth.x = 0.34f; smooth.y = 0.192f; smooth.width = 0.6f
        panel.add(smooth)

        val snap = Slider("Snap turn", s.snapTurn, 0f, 90f) { s.snapTurn = it }
        snap.format = { "%.0f°".format(it) }
        snap.x = 0.34f; snap.y = 0.104f; snap.width = 0.6f
        panel.add(snap)

        val vol = Slider("Volume", s.volume, 0f, 1f) {
            s.volume = it
            com.metaport.xr.audio.Sfx.setStreamVolume(it)
        }
        vol.x = 0.34f; vol.y = 0.016f; vol.width = 0.6f
        panel.add(vol)

        val dwell = Slider("Dwell select time", s.dwellSeconds, 0.4f, 2.5f) { s.dwellSeconds = it }
        dwell.format = { "%.2f s".format(it) }
        dwell.x = 0.34f; dwell.y = -0.072f; dwell.width = 0.6f
        panel.add(dwell)

        val uiScale = Slider("UI scale", s.uiScale, 0.7f, 1.5f) { s.uiScale = it }
        uiScale.x = -0.33f; uiScale.y = -0.072f; uiScale.width = 0.6f
        panel.add(uiScale)

        val uiDist = Slider("UI distance", s.uiDistance, 1.0f, 2.6f) { s.uiDistance = it }
        uiDist.format = { "%.2f m".format(it) }
        uiDist.x = -0.33f; uiDist.y = -0.16f; uiDist.width = 0.6f
        panel.add(uiDist)

        // Toggles
        fun toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit, tx: Float, ty: Float) {
            val t = Toggle(label, value, onChange)
            t.x = tx; t.y = ty; t.width = 0.6f
            panel.add(t)
        }
        toggle("Hand tracking", s.handTracking, { rt.setHandTracking(it) }, -0.33f, -0.26f)
        toggle("Stereo lens distortion", s.stereoDistortion, { rt.setStereo(it) }, 0.34f, -0.16f)
        toggle("Mixed reality camera", s.mixedReality, { rt.setMixedReality(it) }, 0.34f, -0.26f)
        toggle("Show tracked planes", s.showPlanes, { s.showPlanes = it }, -0.33f, -0.35f)
        toggle("Swap left/right hands", s.swapHands, { s.swapHands = it }, 0.34f, -0.35f)
        toggle("Dev API server", s.devApi, { rt.setDevApi(it) }, -0.33f, -0.44f)
        toggle("Draw hand skeletons", s.showHands, { s.showHands = it }, 0.34f, -0.44f)

        val back = Button("Back", Theme.TEXT_BODY) { rt.exitToHome() }
        back.width = 0.22f; back.height = 0.062f
        back.x = 0f; back.y = -0.455f
        back.accentIndex = Theme.ICE
        panel.add(back)

        rt.uiRoot.add(panel)
    }

    fun dispose() {
        rt.uiRoot.remove(panel)
        panel.release()
    }
}

/** Live Dev API console inside the headset. */
class DevConsoleScreen(private val rt: RuntimeApi) {
    val panel = Panel3D("Dev API", width = 1.4f, height = 1.0f, curvature = 0.44f)
    private val urlLabel = Label("", Theme.TEXT_TINY, FontAtlas.ALIGN_LEFT, floatArrayOf(0.3f, 1f, 0.65f, 1f), 0.2f)
    private val eventLabel = Label("", Theme.TEXT_TINY, FontAtlas.ALIGN_LEFT, Theme.textSecondary.clone())

    fun show() {
        val s = rt.runtimeSettings()
        panel.posY = 1.42f
        panel.posZ = -s.uiDistance - 0.02f
        panel.accentIndex = Theme.LIME
        panel.subtitle = "HTTP control plane running on the device. Point a browser or curl at any address below."

        urlLabel.wrapWidth = 1.2f
        urlLabel.maxLines = 4
        panel.add(urlLabel.also { it.x = -0.66f; it.y = 0.26f; it.width = 1.3f; it.height = 0.14f })

        eventLabel.wrapWidth = 1.24f
        eventLabel.maxLines = 10
        panel.add(eventLabel.also { it.x = -0.66f; it.y = 0.02f; it.width = 1.3f; it.height = 0.4f })

        val b = Button("Refresh", Theme.TEXT_SMALL) { refresh() }
        b.width = 0.22f; b.height = 0.06f; b.x = -0.45f; b.y = -0.42f
        b.accentIndex = Theme.LIME
        panel.add(b)

        val back = Button("Back", Theme.TEXT_BODY) { rt.exitToHome() }
        back.width = 0.22f; back.height = 0.062f
        back.x = 0.45f; back.y = -0.42f
        back.accentIndex = Theme.ICE
        panel.add(back)

        rt.uiRoot.add(panel)
        refresh()
    }

    fun refresh() {
        val urls = rt.devApiUrls()
        urlLabel.text = if (urls.isEmpty()) "Dev API offline — enable it in Settings"
        else urls.joinToString("\n")
        val ev = rt.recentEvents()
        eventLabel.text = if (ev.isEmpty()) "No events yet." else
            ev.takeLast(10).reversed().joinToString("\n") { m ->
                "${m["event"]}"
            }
    }

    fun dispose() {
        rt.uiRoot.remove(panel)
        panel.release()
    }
}

/** In-game HUD: scoreboard plus a pause card. */
class GameHud(private val rt: RuntimeApi) {
    val panel = Panel3D("", width = 0.62f, height = 0.42f, curvature = 0.22f)
    private val rows = ArrayList<Label>()
    private val keys = ArrayList<Label>()
    private var pauseButton: Button? = null
    private var title = ""

    fun attach(gameTitle: String) {
        title = gameTitle
        panel.title = gameTitle
        panel.posY = 1.95f
        panel.posZ = -1.35f
        panel.accentIndex = Theme.AMBER
        panel.showHeader = true
        panel.fill = floatArrayOf(0.02f, 0.04f, 0.1f, 0.5f)

        for (i in 0 until 6) {
            val k = Label("", Theme.TEXT_TINY, FontAtlas.ALIGN_LEFT, floatArrayOf(0.5f, 0.65f, 0.85f, 1f))
            k.x = -0.16f; k.y = 0.11f - i * 0.062f; k.width = 0.26f; k.height = 0.025f
            panel.add(k); keys.add(k)
            val v = Label("", Theme.TEXT_SMALL, FontAtlas.ALIGN_RIGHT, floatArrayOf(0.95f, 0.98f, 1f, 1f), 0.3f)
            v.x = 0.29f; v.y = 0.11f - i * 0.062f; v.width = 0.3f; v.height = 0.03f
            panel.add(v); rows.add(v)
        }

        val pb = Button("Exit", Theme.TEXT_SMALL) { rt.exitToHome() }
        pb.width = 0.18f; pb.height = 0.05f; pb.x = 0f; pb.y = -0.165f
        pb.accentIndex = Theme.MAGENTA
        pb.showAccentBar = false
        panel.add(pb)
        pauseButton = pb

        rt.uiRoot.add(panel)
    }

    fun update(hud: List<Pair<String, String>>) {
        for (i in rows.indices) {
            if (i < hud.size) {
                keys[i].text = hud[i].first
                rows[i].text = hud[i].second
                keys[i].visible = true
                rows[i].visible = true
            } else {
                keys[i].visible = false
                rows[i].visible = false
            }
        }
    }

    fun reposition(yaw: Float, headX: Float, headY: Float, headZ: Float) {
        val d = 1.35f
        panel.posX = headX - kotlin.math.sin(yaw) * d
        panel.posZ = headZ - kotlin.math.cos(yaw) * d
        panel.posY = headY + 0.55f
        panel.yaw = yaw
    }

    fun dispose() {
        rt.uiRoot.remove(panel)
        panel.release()
        rows.clear(); keys.clear(); pauseButton = null
    }
}

/** Comfort overlay helpers shared by the screens. */
object UiHelpers {
    fun clampScale(v: Float) = Mathf.clamp(v, 0.5f, 2f)
}
