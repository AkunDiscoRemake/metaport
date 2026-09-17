package com.metaport.xr

import com.metaport.xr.ar.HandGestureResolver
import com.metaport.xr.ar.HandModel
import com.metaport.xr.core.math.Mathf
import com.metaport.xr.core.math.Vec3
import com.metaport.xr.devapi.Json
import com.metaport.xr.devapi.MetaPortSdk
import com.metaport.xr.devapi.Telemetry
import com.metaport.xr.stereo.ViewerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Unit tests for the pure-logic layers of MetaPort.
 *
 * These deliberately avoid android.* classes so they run on the JVM without
 * Robolectric; GL and ARCore paths are exercised on device instead.
 */
class MetaPortLogicTest {

    // ------------------------------------------------------------------ Json

    @Test
    fun `json writer escapes and nests correctly`() {
        val out = Json.from(
            linkedMapOf(
                "name" to "MetaPort",
                "fps" to 72.5,
                "ok" to true,
                "missing" to null,
                "list" to listOf(1, 2, 3),
                "nested" to linkedMapOf("a" to "b\"c\nd")
            )
        )
        assertTrue(out.startsWith("{"))
        assertTrue(out.contains("\"name\":\"MetaPort\""))
        assertTrue(out.contains("\"fps\":72.5"))
        assertTrue(out.contains("\"ok\":true"))
        assertTrue(out.contains("\"missing\":null"))
        assertTrue(out.contains("\"list\":[1,2,3]"))
        assertTrue("quote must be escaped", out.contains("\\\"c"))
        assertTrue("newline must be escaped", out.contains("\\n"))
        assertEquals("braces must balance", out.count { it == '{' }, out.count { it == '}' })
    }

    @Test
    fun `json writer emits no stray commas in empty containers`() {
        val out = Json.from(linkedMapOf("games" to emptyList<String>(), "m" to emptyMap<String, Any>()))
        assertEquals("""{"games":[],"m":{}}""", out)
    }

    @Test
    fun `dev api post body parser reads strings and numbers`() {
        val m = Json.parseFlat("""{"action":"trigger","value":1,"label":"snap left"}""")
        assertEquals("trigger", m["action"])
        assertEquals(1f, m["value"]?.toFloatOrNull() ?: -1f, 1e-6f)
        assertEquals("snap left", m["label"])
    }

    @Test
    fun `json strings containing braces survive a round trip`() {
        val out = Json.from(linkedMapOf("path" to "/api/v1/games/{id}/launch"))
        assertTrue(out.contains("/api/v1/games/{id}/launch"))
    }

    // ------------------------------------------------------------------ math

    @Test
    fun `fbm noise stays in range and is deterministic`() {
        val a = Mathf.fbm2(3.25f, 7.75f, 4)
        val b = Mathf.fbm2(3.25f, 7.75f, 4)
        assertEquals(a, b, 0f)
        assertTrue("fbm out of range: $a", a in 0f..1f)
    }

    @Test
    fun `damp converges towards the target`() {
        var v = 0f
        repeat(60) { v = Mathf.damp(v, 10f, 12f, 1f / 60f) }
        assertTrue("damp did not converge: $v", v > 9.9f)
    }

    @Test
    fun `vec3 normalise and cross product are right handed`() {
        val x = Vec3(3f, 0f, 0f).normalize()
        val y = Vec3(0f, 4f, 0f).normalize()
        val z = Vec3().cross(x, y)
        assertEquals(1f, x.length(), 1e-5f)
        assertEquals(1f, z.z, 1e-5f)
        assertEquals(0f, z.x, 1e-5f)
    }

    @Test
    fun `smoothstep is clamped at both ends`() {
        assertEquals(0f, Mathf.smoothstep(1f, 2f, 0f), 1e-6f)
        assertEquals(1f, Mathf.smoothstep(1f, 2f, 5f), 1e-6f)
        assertEquals(0.5f, Mathf.smoothstep(0f, 1f, 0.5f), 1e-4f)
    }

    // ------------------------------------------------------------------ viewer optics

    @Test
    fun `cardboard defaults match Google published viewer parameters`() {
        val p = ViewerProfile()
        assertEquals(0.0639f, p.interLensDistance, 1e-5f)
        assertEquals(0.035f, p.trayToLens, 1e-5f)
        assertEquals(0.0393f, p.screenToLens, 1e-5f)
        assertEquals(0.33582564f, p.distortionK1, 1e-7f)
        assertEquals(0.55348791f, p.distortionK2, 1e-7f)
    }

    @Test
    fun `lens centres are symmetric and stay inside the eye rectangle`() {
        val p = ViewerProfile()
        val l = p.lensCenterU(0)
        val r = p.lensCenterU(1)
        assertTrue("left lens centre out of range: $l", l in 0.05f..0.95f)
        assertTrue("right lens centre out of range: $r", r in 0.05f..0.95f)
        assertTrue("lens centres must be mirrored", r > l)
        assertEquals(1f, (l + r), 1e-4f)
    }

    @Test
    fun `ipd adjustment is clamped to a sane minimum`() {
        val p = ViewerProfile()
        p.ipdAdjust = -1f
        assertEquals(0.030f, p.effectiveInterLens, 1e-5f)
        p.ipdAdjust = 0.01f
        assertEquals(0.0739f, p.effectiveInterLens, 1e-4f)
    }

    // ------------------------------------------------------------------ hand tracking

    private fun syntheticHand(open: Boolean): HandModel {
        val h = HandModel(isRight = true)
        // Wrist at the origin, fingers extending along +Y, thumb offset to +X.
        h.joints[HandModel.WRIST * 3 + 1] = 0f
        fun set(j: Int, x: Float, y: Float, z: Float) {
            h.joints[j * 3] = x; h.joints[j * 3 + 1] = y; h.joints[j * 3 + 2] = z
        }
        val spread = if (open) 0.055f else 0.030f
        set(HandModel.THUMB_CMC, spread * 0.6f, 0.030f, 0f)
        set(HandModel.THUMB_MCP, spread * 1.1f, 0.060f, 0f)
        set(HandModel.THUMB_IP, spread * 1.4f, if (open) 0.090f else 0.045f, 0f)
        set(HandModel.THUMB_TIP, spread * 1.6f, if (open) 0.120f else 0.020f, 0f)
        val fingers = listOf(HandModel.INDEX_MCP to -spread, HandModel.MIDDLE_MCP to 0f,
            HandModel.RING_MCP to spread, HandModel.PINKY_MCP to spread * 2f)
        for ((mcp, off) in fingers) {
            val base = when (mcp) {
                HandModel.INDEX_MCP -> 0.10f
                HandModel.MIDDLE_MCP -> 0.11f
                HandModel.RING_MCP -> 0.10f
                else -> 0.085f
            }
            val mcpY = base * 0.85f
            set(mcp, off, mcpY, 0f)
            if (open) {
                set(mcp + 1, off, mcpY + 0.040f, 0f)
                set(mcp + 2, off, mcpY + 0.070f, 0f)
                set(mcp + 3, off, mcpY + 0.100f, 0f)
            } else {
                // Folded back: the tips end up closer to the wrist than the MCPs,
                // which is what actually distinguishes a fist from an open hand.
                set(mcp + 1, off, mcpY - 0.010f, 0f)
                set(mcp + 2, off, mcpY - 0.030f, 0f)
                set(mcp + 3, off, mcpY - 0.055f, 0f)
            }
        }
        h.visible = true
        h.confidence = 1f
        h.palmSize = 0.085f
        h.rebuildPalmBasis()
        return h
    }

    @Test
    fun `open hand resolves to the open gesture`() {
        val h = syntheticHand(open = true)
        val g = HandGestureResolver().resolve(h, 0.016f)
        assertEquals(HandModel.GESTURE_OPEN, g)
        assertTrue("spread should be high for an open hand: ${h.spread}", h.spread >= 0.6f)
    }

    @Test
    fun `thumb touching the index resolves to a pinch`() {
        val h = syntheticHand(open = true)
        // Move the thumb tip onto the index tip.
        h.joints[HandModel.THUMB_TIP * 3] = h.joints[HandModel.INDEX_TIP * 3]
        h.joints[HandModel.THUMB_TIP * 3 + 1] = h.joints[HandModel.INDEX_TIP * 3 + 1]
        h.joints[HandModel.THUMB_TIP * 3 + 2] = h.joints[HandModel.INDEX_TIP * 3 + 2]
        val g = HandGestureResolver().resolve(h, 0.016f)
        assertEquals(HandModel.GESTURE_PINCH, g)
        assertTrue("pinch amount should be high: ${h.pinchAmount}", h.pinchAmount > 0.5f)
    }

    @Test
    fun `a gesture change must hold before it replaces the previous one`() {
        val h = syntheticHand(open = true)
        val r = HandGestureResolver()
        assertEquals("first classification is immediate", HandModel.GESTURE_OPEN, r.resolve(h, 0.016f))

        // Fold into a fist: the switch must not land on the very next frame.
        val fist = syntheticHand(open = false)
        fist.visible = true
        fist.confidence = 1f
        fist.palmSize = h.palmSize
        fist.rebuildPalmBasis()
        assertEquals("still holds the previous gesture", HandModel.GESTURE_OPEN, r.resolve(fist, 0.016f))

        var g = HandModel.GESTURE_OPEN
        for (i in 0 until 12) g = r.resolve(fist, 0.016f)
        assertEquals("switches once the new pose is stable", HandModel.GESTURE_FIST, g)
    }

    @Test
    fun `curled fingers resolve to a fist`() {
        val h = syntheticHand(open = false)
        val g = HandGestureResolver().resolve(h, 0.016f)
        assertEquals(HandModel.GESTURE_FIST, g)
    }

    @Test
    fun `palm basis is orthonormal and forward points out of the palm`() {
        val h = syntheticHand(open = true)
        val dot = h.palmRight[0] * h.palmUp[0] + h.palmRight[1] * h.palmUp[1] + h.palmRight[2] * h.palmUp[2]
        assertEquals("palm axes must be perpendicular", 0f, dot, 1e-4f)
        val len = sqrt(
            h.palmForward[0] * h.palmForward[0] +
                h.palmForward[1] * h.palmForward[1] + h.palmForward[2] * h.palmForward[2]
        )
        assertEquals(1f, len, 1e-4f)
    }

    @Test
    fun `hand map exposes all 21 joints for the dev api`() {
        val h = syntheticHand(open = true)
        val m = h.toMap()
        assertEquals("right", m["hand"])
        @Suppress("UNCHECKED_CAST")
        val joints = m["joints"] as List<Double>
        assertEquals(HandModel.JOINT_COUNT * 3, joints.size)
    }

    // ------------------------------------------------------------------ sdk / telemetry

    @Test
    fun `event bus delivers to listeners and keeps a bounded log`() {
        val seen = ArrayList<String>()
        val l = MetaPortSdk.Listener { name, _ -> seen.add(name) }
        MetaPortSdk.addListener(l)
        try {
            repeat(80) { MetaPortSdk.emit("test.event.$it") }
        } finally {
            MetaPortSdk.removeListener(l)
        }
        assertEquals(80, seen.size)
        assertTrue("event log must be bounded", MetaPortSdk.recentEvents(1000).size <= 64)
    }

    @Test
    fun `settings round trip`() {
        MetaPortSdk.setSetting("unit", "test")
        assertEquals("test", MetaPortSdk.getSetting("unit"))
        assertEquals("test", MetaPortSdk.allSettings()["unit"])
    }

    @Test
    fun `telemetry computes a plausible fps from frame stamps`() {
        val t = Telemetry(capacity = 64)
        var nanos = 0L
        // 60 fps for 64 frames.
        for (i in 0 until 64) {
            nanos += 16_666_667L
            t.frame(nanos, 120, 90, 0.4f, 1.2f, 0.3f)
        }
        assertTrue("fps should be near 60, was ${t.fps}", abs(t.fps - 60f) < 3f)
        assertEquals(120, t.drawCalls)
        val m = t.toMap()
        assertTrue(m.containsKey("fps"))
        assertTrue(m.containsKey("handTrackingMs"))
        assertFalse("fps must not be reported as NaN", m["fps"].toString().contains("NaN"))
    }
}
