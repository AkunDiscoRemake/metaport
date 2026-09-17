package com.metaport.xr.stereo

import android.content.Context
import com.metaport.xr.core.math.Mathf
import kotlin.math.max

/**
 * Cardboard viewer optics.
 *
 * Default values are Google's published Cardboard viewer parameters
 * (inter-lens 63.9 mm, tray-to-lens 35 mm, screen-to-lens 39.3 mm,
 * k1 = 0.33582564, k2 = 0.55348791). Users can retune them live in the
 * calibration panel, and presets cover the common "VR Box" style viewers.
 */
class ViewerProfile {
    var id: String = "cardboard-v2"
    var label: String = "Google Cardboard V2"

    /** Distance between the two lens centres, metres. */
    var interLensDistance = 0.0639f

    /** Distance from the tray bottom to the lens centre, metres. */
    var trayToLens = 0.035f

    /** Distance from the phone screen to the lens, metres. */
    var screenToLens = 0.0393f

    /** Radial distortion coefficients of the viewer lenses. */
    var distortionK1 = 0.33582564f
    var distortionK2 = 0.55348791f

    /** Physical screen size, metres (diagonal is derived from the device). */
    var screenWidth = 0.110f
    var screenHeight = 0.062f

    /** Vertical alignment of the screen inside the tray. */
    var bottomAligned = true

    /** Per-eye field of view, degrees. */
    var fovDegrees = 92f

    /** Extra rendering knobs. */
    var chromaticAberration = 1f
    var vignette = 0.55f

    /** <1 zooms the pre-distorted image in, hiding barrel edges. */
    var distortionScale = 0.82f

    /** User IPD trim, metres, added/subtracted from the lens spacing. */
    var ipdAdjust = 0f

    /** Render target scale (performance vs sharpness). */
    var renderScale = 1f

    val effectiveInterLens: Float get() = max(interLensDistance + ipdAdjust, 0.030f)

    /** Lens centre in eye-local texture space (0..1), per eye. */
    fun lensCenterU(eye: Int): Float {
        // Lens centres sit symmetric about the screen centre.
        val half = effectiveInterLens * 0.5f
        val screenHalf = screenWidth * 0.5f
        val ratio = if (screenHalf > 1e-5f) (half / screenWidth) else 0.25f
        val u = Mathf.clamp(0.5f + if (eye == 0) -ratio else ratio, 0.05f, 0.95f)
        return u
    }

    val lensCenterV: Float
        get() {
            if (!bottomAligned) return 0.5f
            val fromBottom = trayToLens / screenHeight
            return Mathf.clamp(1f - fromBottom, 0.05f, 0.95f)
        }

    fun copyFrom(o: ViewerProfile) {
        id = o.id; label = o.label
        interLensDistance = o.interLensDistance
        trayToLens = o.trayToLens
        screenToLens = o.screenToLens
        distortionK1 = o.distortionK1
        distortionK2 = o.distortionK2
        screenWidth = o.screenWidth
        screenHeight = o.screenHeight
        bottomAligned = o.bottomAligned
        fovDegrees = o.fovDegrees
        chromaticAberration = o.chromaticAberration
        vignette = o.vignette
        distortionScale = o.distortionScale
        ipdAdjust = o.ipdAdjust
        renderScale = o.renderScale
    }

    fun toMap(): LinkedHashMap<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m["id"] = id
        m["label"] = label
        m["interLensDistance"] = interLensDistance
        m["trayToLens"] = trayToLens
        m["screenToLens"] = screenToLens
        m["distortionK1"] = distortionK1
        m["distortionK2"] = distortionK2
        m["fovDegrees"] = fovDegrees
        m["ipdAdjust"] = ipdAdjust
        m["renderScale"] = renderScale
        return m
    }

    companion object {
        const val PREFS = "metaport_viewer"

        fun presets(): List<ViewerProfile> = listOf(
            ViewerProfile().apply {
                id = "cardboard-v2"; label = "Google Cardboard V2"
            },
            ViewerProfile().apply {
                id = "cardboard-v1"; label = "Google Cardboard V1 (2014)"
                interLensDistance = 0.060f
                trayToLens = 0.035f
                screenToLens = 0.042f
                distortionK1 = 0.441f
                distortionK2 = 0.156f
                fovDegrees = 84f
            },
            ViewerProfile().apply {
                id = "vrbox-2"; label = "VR Box 2.0"
                interLensDistance = 0.061f
                trayToLens = 0.033f
                screenToLens = 0.044f
                distortionK1 = 0.30f
                distortionK2 = 0.30f
                fovDegrees = 96f
                distortionScale = 0.78f
            },
            ViewerProfile().apply {
                id = "generic-wide"; label = "Generic wide FOV box"
                interLensDistance = 0.064f
                trayToLens = 0.036f
                screenToLens = 0.040f
                distortionK1 = 0.26f
                distortionK2 = 0.42f
                fovDegrees = 100f
                distortionScale = 0.76f
            },
            ViewerProfile().apply {
                id = "flat"; label = "Flat screen (no lenses)"
                distortionK1 = 0f
                distortionK2 = 0f
                chromaticAberration = 0f
                vignette = 0f
                fovDegrees = 80f
            }
        )

        /** Derives screen dimensions from the real device metrics. */
        fun fromDisplay(ctx: Context, profile: ViewerProfile): ViewerProfile {
            val dm = ctx.resources.displayMetrics
            val wMm = dm.widthPixels / dm.xdpi
            val hMm = dm.heightPixels / dm.ydpi
            // Landscape: width is the long edge.
            val long = max(wMm, hMm) / 1000f
            val short = (if (wMm > hMm) hMm else wMm) / 1000f
            if (long in 0.05f..0.4f) profile.screenWidth = long
            if (short in 0.03f..0.25f) profile.screenHeight = short
            return profile
        }

        fun save(ctx: Context, p: ViewerProfile) {
            val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            e.putString("id", p.id)
            e.putFloat("interLens", p.interLensDistance)
            e.putFloat("tray", p.trayToLens)
            e.putFloat("screenToLens", p.screenToLens)
            e.putFloat("k1", p.distortionK1)
            e.putFloat("k2", p.distortionK2)
            e.putFloat("fov", p.fovDegrees)
            e.putFloat("ipd", p.ipdAdjust)
            e.putFloat("scale", p.renderScale)
            e.putFloat("chroma", p.chromaticAberration)
            e.putFloat("vignette", p.vignette)
            e.putFloat("dscale", p.distortionScale)
            e.apply()
        }

        fun load(ctx: Context, into: ViewerProfile): ViewerProfile {
            val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            into.id = s.getString("id", into.id) ?: into.id
            into.interLensDistance = s.getFloat("interLens", into.interLensDistance)
            into.trayToLens = s.getFloat("tray", into.trayToLens)
            into.screenToLens = s.getFloat("screenToLens", into.screenToLens)
            into.distortionK1 = s.getFloat("k1", into.distortionK1)
            into.distortionK2 = s.getFloat("k2", into.distortionK2)
            into.fovDegrees = s.getFloat("fov", into.fovDegrees)
            into.ipdAdjust = s.getFloat("ipd", into.ipdAdjust)
            into.renderScale = s.getFloat("scale", into.renderScale)
            into.chromaticAberration = s.getFloat("chroma", into.chromaticAberration)
            into.vignette = s.getFloat("vignette", into.vignette)
            into.distortionScale = s.getFloat("dscale", into.distortionScale)
            return into
        }
    }
}
