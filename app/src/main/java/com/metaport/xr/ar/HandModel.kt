package com.metaport.xr.ar

import com.metaport.xr.core.math.Mat4

/**
 * 21-joint hand skeleton in world space, following the layout used by OpenXR
 * hand tracking so external tools can consume it directly.
 */
class HandModel(val isRight: Boolean) {

    companion object {
        const val JOINT_COUNT = 21
        const val WRIST = 0
        const val THUMB_CMC = 1
        const val THUMB_MCP = 2
        const val THUMB_IP = 3
        const val THUMB_TIP = 4
        const val INDEX_MCP = 5
        const val INDEX_PIP = 6
        const val INDEX_DIP = 7
        const val INDEX_TIP = 8
        const val MIDDLE_MCP = 9
        const val MIDDLE_PIP = 10
        const val MIDDLE_DIP = 11
        const val MIDDLE_TIP = 12
        const val RING_MCP = 13
        const val RING_PIP = 14
        const val RING_DIP = 15
        const val RING_TIP = 16
        const val PINKY_MCP = 17
        const val PINKY_PIP = 18
        const val PINKY_DIP = 19
        const val PINKY_TIP = 20

        val FINGER_TIPS = intArrayOf(THUMB_TIP, INDEX_TIP, MIDDLE_TIP, RING_TIP, PINKY_TIP)
        val FINGER_MCPS = intArrayOf(THUMB_MCP, INDEX_MCP, MIDDLE_MCP, RING_MCP, PINKY_MCP)

        /** Bone segments drawn as capsules between joints. */
        val BONES = arrayOf(
            intArrayOf(WRIST, THUMB_CMC), intArrayOf(THUMB_CMC, THUMB_MCP),
            intArrayOf(THUMB_MCP, THUMB_IP), intArrayOf(THUMB_IP, THUMB_TIP),
            intArrayOf(WRIST, INDEX_MCP), intArrayOf(INDEX_MCP, INDEX_PIP),
            intArrayOf(INDEX_PIP, INDEX_DIP), intArrayOf(INDEX_DIP, INDEX_TIP),
            intArrayOf(INDEX_MCP, MIDDLE_MCP),
            intArrayOf(MIDDLE_MCP, MIDDLE_PIP), intArrayOf(MIDDLE_PIP, MIDDLE_DIP),
            intArrayOf(MIDDLE_DIP, MIDDLE_TIP),
            intArrayOf(MIDDLE_MCP, RING_MCP),
            intArrayOf(RING_MCP, RING_PIP), intArrayOf(RING_PIP, RING_DIP),
            intArrayOf(RING_DIP, RING_TIP),
            intArrayOf(RING_MCP, PINKY_MCP),
            intArrayOf(PINKY_MCP, PINKY_PIP), intArrayOf(PINKY_PIP, PINKY_DIP),
            intArrayOf(PINKY_DIP, PINKY_TIP),
            intArrayOf(WRIST, PINKY_MCP)
        )

        const val GESTURE_NONE = 0
        const val GESTURE_PINCH = 1
        const val GESTURE_POINT = 2
        const val GESTURE_OPEN = 3
        const val GESTURE_FIST = 4
        const val GESTURE_VICTORY = 5
    }

    /** Flattened xyz per joint, world space. */
    val joints = FloatArray(JOINT_COUNT * 3)

    var visible = false
    var confidence = 0f
    var gesture = GESTURE_NONE
    var pinchAmount = 0f
    var spread = 0f

    /** Palm basis: origin + right/up/forward, used to attach tools and sabres. */
    val palmPosition = FloatArray(3)
    val palmRight = FloatArray(3)
    val palmUp = FloatArray(3)
    val palmForward = FloatArray(3)
    val palmMatrix = FloatArray(16)

    /** Palm width in metres — the scale reference for every gesture threshold. */
    var palmSize = 0.085f

    fun joint(i: Int, out: FloatArray, off: Int = 0) {
        out[off] = joints[i * 3]; out[off + 1] = joints[i * 3 + 1]; out[off + 2] = joints[i * 3 + 2]
    }

    fun jointDist(a: Int, b: Int): Float {
        val dx = joints[a * 3] - joints[b * 3]
        val dy = joints[a * 3 + 1] - joints[b * 3 + 1]
        val dz = joints[a * 3 + 2] - joints[b * 3 + 2]
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Pointing direction of the index finger. */
    fun indexDirection(out: FloatArray) {
        val ax = joints[INDEX_MCP * 3]; val ay = joints[INDEX_MCP * 3 + 1]; val az = joints[INDEX_MCP * 3 + 2]
        var dx = joints[INDEX_TIP * 3] - ax
        var dy = joints[INDEX_TIP * 3 + 1] - ay
        var dz = joints[INDEX_TIP * 3 + 2] - az
        val l = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (l > 1e-6f) { dx /= l; dy /= l; dz /= l }
        out[0] = dx; out[1] = dy; out[2] = dz
    }

    fun copyFrom(o: HandModel) {
        System.arraycopy(o.joints, 0, joints, 0, joints.size)
        visible = o.visible
        confidence = o.confidence
        gesture = o.gesture
        pinchAmount = o.pinchAmount
        spread = o.spread
        palmSize = o.palmSize
        System.arraycopy(o.palmPosition, 0, palmPosition, 0, 3)
        System.arraycopy(o.palmRight, 0, palmRight, 0, 3)
        System.arraycopy(o.palmUp, 0, palmUp, 0, 3)
        System.arraycopy(o.palmForward, 0, palmForward, 0, 3)
        System.arraycopy(o.palmMatrix, 0, palmMatrix, 0, 16)
    }

    fun gestureName(): String = when (gesture) {
        GESTURE_PINCH -> "Pinch"
        GESTURE_POINT -> "Point"
        GESTURE_OPEN -> "Open"
        GESTURE_FIST -> "Fist"
        GESTURE_VICTORY -> "Peace"
        else -> "None"
    }

    fun rebuildPalmBasis() {
        // Palm centre = average of wrist and the four finger MCPs.
        var cx = 0f; var cy = 0f; var cz = 0f
        cx += joints[WRIST * 3]; cy += joints[WRIST * 3 + 1]; cz += joints[WRIST * 3 + 2]
        for (f in 1..4) {
            val j = FINGER_MCPS[f]
            cx += joints[j * 3]; cy += joints[j * 3 + 1]; cz += joints[j * 3 + 2]
        }
        cx /= 5f; cy /= 5f; cz /= 5f
        palmPosition[0] = cx; palmPosition[1] = cy; palmPosition[2] = cz

        // "up" runs wrist -> middle MCP.
        var ux = joints[MIDDLE_MCP * 3] - joints[WRIST * 3]
        var uy = joints[MIDDLE_MCP * 3 + 1] - joints[WRIST * 3 + 1]
        var uz = joints[MIDDLE_MCP * 3 + 2] - joints[WRIST * 3 + 2]
        var l = kotlin.math.sqrt(ux * ux + uy * uy + uz * uz)
        if (l < 1e-6f) { ux = 0f; uy = 1f; uz = 0f; l = 1f }
        ux /= l; uy /= l; uz /= l

        // "right" runs index MCP -> pinky MCP (flipped for the right hand).
        var rx = joints[PINKY_MCP * 3] - joints[INDEX_MCP * 3]
        var ry = joints[PINKY_MCP * 3 + 1] - joints[INDEX_MCP * 3 + 1]
        var rz = joints[PINKY_MCP * 3 + 2] - joints[INDEX_MCP * 3 + 2]
        l = kotlin.math.sqrt(rx * rx + ry * ry + rz * rz)
        if (l < 1e-6f) { rx = 1f; ry = 0f; rz = 0f; l = 1f }
        rx /= l; ry /= l; rz /= l
        if (isRight) { rx = -rx; ry = -ry; rz = -rz }
        // Orthogonalise right against up.
        val d = rx * ux + ry * uy + rz * uz
        rx -= ux * d; ry -= uy * d; rz -= uz * d
        l = kotlin.math.sqrt(rx * rx + ry * ry + rz * rz)
        if (l > 1e-6f) { rx /= l; ry /= l; rz /= l }

        // forward = right x up (points out of the palm).
        val fx = ry * uz - rz * uy
        val fy = rz * ux - rx * uz
        val fz = rx * uy - ry * ux

        palmRight[0] = rx; palmRight[1] = ry; palmRight[2] = rz
        palmUp[0] = ux; palmUp[1] = uy; palmUp[2] = uz
        palmForward[0] = fx; palmForward[1] = fy; palmForward[2] = fz

        palmMatrix[0] = rx; palmMatrix[1] = ry; palmMatrix[2] = rz; palmMatrix[3] = 0f
        palmMatrix[4] = ux; palmMatrix[5] = uy; palmMatrix[6] = uz; palmMatrix[7] = 0f
        palmMatrix[8] = fx; palmMatrix[9] = fy; palmMatrix[10] = fz; palmMatrix[11] = 0f
        palmMatrix[12] = cx; palmMatrix[13] = cy; palmMatrix[14] = cz; palmMatrix[15] = 1f
    }

    fun toMap(): LinkedHashMap<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m["hand"] = if (isRight) "right" else "left"
        m["visible"] = visible
        m["confidence"] = confidence
        m["gesture"] = gestureName()
        m["pinch"] = pinchAmount
        m["palmSize"] = palmSize
        val arr = ArrayList<Double>(JOINT_COUNT * 3)
        for (v in joints) arr.add(v.toDouble())
        m["joints"] = arr
        return m
    }

    fun reset() {
        visible = false
        confidence = 0f
        gesture = GESTURE_NONE
        pinchAmount = 0f
        spread = 0f
    }
}

/** Smooths a hand over time and derives gestures from joint geometry. */
class HandGestureResolver {
    var pinchThreshold = 0.45f
    var openThreshold = 0.75f

    /**
     * A candidate gesture must hold for this long before it replaces the current
     * one. Without it a hand hovering on a boundary flickers between two states
     * every frame, which makes UI activation unreliable.
     */
    var switchDelay = 0.09f

    private var lastGesture = HandModel.GESTURE_NONE
    private var candidate = HandModel.GESTURE_NONE
    private var candidateTime = 0f

    fun resolve(h: HandModel, dt: Float): Int {
        if (!h.visible) { lastGesture = HandModel.GESTURE_NONE; return HandModel.GESTURE_NONE }

        val palm = h.palmSize.coerceAtLeast(0.03f)
        val pinch = h.jointDist(HandModel.THUMB_TIP, HandModel.INDEX_TIP) / palm
        val target = (1f - (pinch / 0.9f)).coerceIn(0f, 1f)
        h.pinchAmount += (target - h.pinchAmount) * (1f - kotlin.math.exp(-18f * dt))

        // Finger extension: tip-to-wrist distance relative to MCP-to-wrist.
        var extended = 0
        for (f in 0 until 5) {
            val tip = HandModel.FINGER_TIPS[f]
            val mcp = HandModel.FINGER_MCPS[f]
            val dTip = h.jointDist(tip, HandModel.WRIST)
            val dMcp = h.jointDist(mcp, HandModel.WRIST)
            if (dTip > dMcp * 1.12f) extended++
        }
        h.spread = extended / 5f

        val g = when {
            // A fist is unambiguous — every finger folded back toward the wrist —
            // and must win over pinch, because a fist also brings the thumb tip
            // close to the index tip.
            extended == 0 -> HandModel.GESTURE_FIST
            // Pinch depends only on thumb-index proximity. The other fingers stay
            // free, which is the natural pose and matches how OpenXR / MediaPipe /
            // ARCore hand tracking define a pinch.
            pinch < pinchThreshold * 0.6f -> HandModel.GESTURE_PINCH
            extended >= 4 -> HandModel.GESTURE_OPEN
            extended == 2 -> HandModel.GESTURE_VICTORY
            else -> HandModel.GESTURE_POINT
        }

        // Hysteresis: the first classification is immediate, later changes must
        // be stable for switchDelay seconds before they take effect.
        if (g == lastGesture) {
            candidate = g
            candidateTime = 0f
            h.gesture = g
        } else {
            if (g != candidate) { candidate = g; candidateTime = 0f }
            candidateTime += dt
            if (lastGesture == HandModel.GESTURE_NONE || candidateTime >= switchDelay) {
                lastGesture = g
                candidateTime = 0f
                h.gesture = g
            } else {
                h.gesture = lastGesture
            }
        }
        return h.gesture
    }
}
