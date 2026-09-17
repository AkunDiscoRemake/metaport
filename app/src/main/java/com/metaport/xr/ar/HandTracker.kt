package com.metaport.xr.ar

import android.media.Image
import com.metaport.xr.core.math.Mat4
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Vision + depth hand tracker.
 *
 * Runs entirely on-device with no ML model and no extra dependency:
 *
 *  1. the ARCore camera image is downsampled to a small skin-probability mask
 *     (YCbCr chroma gate — robust across lighting because it uses chroma, not
 *     brightness),
 *  2. connected components give up to two candidate hands,
 *  3. ARCore's depth image (when the device supports the Depth API) converts
 *     the mask centroid into a metric 3D position; otherwise distance is
 *     estimated from the mask's projected area,
 *  4. a covariance-based principal axis plus boundary extremes produce a
 *     21-joint skeleton in the OpenXR joint order,
 *  5. joints are lifted into world space with ARCore's tracked camera pose, so
 *     hands stay locked to the room while you walk around (6DOF).
 *
 * This is a geometric tracker rather than a learned one: it is fast (under a
 * millisecond at 160x120), offline, and stable, but it is best used in a lit
 * room with the hands in front of the camera.
 */
class HandTracker {

    companion object {
        const val MASK_W = 160
        const val MASK_H = 120
        private const val MIN_BLOB_PX = 42
        private const val MAX_HANDS = 2
    }

    var enabled = true
    var swapHands = false

    /** Smoothing for joint positions (higher = snappier). */
    var jointSmoothing = 24f

    /** Horizontal field of view used to unproject pixels when intrinsics are absent. */
    var cameraHFovDeg = 62f

    var lastProcessingMs = 0f
    var detectedCount = 0
        private set

    private val mask = ByteArray(MASK_W * MASK_H)
    private val labels = IntArray(MASK_W * MASK_H)
    private val queue = IntArray(MASK_W * MASK_H)

    private val resolver = HandGestureResolver()

    // Scratch for component analysis.
    private class Blob {
        var count = 0
        var sumX = 0f
        var sumY = 0f
        var minX = 1e9f; var maxX = -1e9f
        var minY = 1e9f; var maxY = -1e9f
        var cxx = 0f; var cyy = 0f; var cxy = 0f
        var axisX = 1f; var axisY = 0f
        var length = 0f
        var width = 0f
    }

    private val blobs = Array(MAX_HANDS) { Blob() }
    private val candidates = ArrayList<Blob>(MAX_HANDS)

    /** Depth lookup cache from the last processed frame. */
    private var depthW = 0
    private var depthH = 0
    private var depthData: ShortArray? = null

    /**
     * Processes one camera image (+ optional depth image) and fills [left] and
     * [right]. Both images are closed by the caller.
     */
    fun process(
        cameraImage: Image?,
        depthImage: Image?,
        cameraWorldPose: FloatArray,
        left: HandModel,
        right: HandModel,
        dt: Float
    ) {
        if (!enabled) {
            left.visible = false
            right.visible = false
            detectedCount = 0
            return
        }
        val t0 = System.nanoTime()

        readDepth(depthImage)

        val img = cameraImage
        if (img == null || img.planes.isEmpty()) {
            decay(left, dt); decay(right, dt); detectedCount = 0
            return
        }

        buildMask(img)
        val n = labelComponents()
        candidates.clear()
        for (i in 0 until minOf(n, MAX_HANDS)) {
            val b = blobs[i]
            if (b.count >= MIN_BLOB_PX) candidates.add(b)
        }
        candidates.sortByDescending { it.count }
        // Left-to-right in image space (rear camera is not mirrored).
        candidates.sortBy { it.sumX / it.count }

        detectedCount = candidates.size

        val imgW = img.width.toFloat()
        val imgH = img.height.toFloat()
        val fx = (imgW * 0.5f) / kotlin.math.tan(cameraHFovDeg * 0.5f * com.metaport.xr.core.math.Mathf.DEG2RAD)
        val fy = fx
        val cx = imgW * 0.5f
        val cy = imgH * 0.5f

        var ci = 0
        for (c in candidates) {
            val target = if (ci == 0) {
                if (swapHands) right else left
            } else {
                if (swapHands) left else right
            }
            ci++
            fillHand(target, c, fx, fy, cx, cy, imgW, imgH, cameraWorldPose, dt)
        }
        if (candidates.size < 1) decay(if (swapHands) right else left, dt)
        if (candidates.size < 2) decay(if (swapHands) left else right, dt)

        lastProcessingMs = (System.nanoTime() - t0) / 1e6f
    }

    // ------------------------------------------------------------------ mask

    private fun buildMask(img: Image) {
        val yPlane = img.planes[0]
        val yBuf = yPlane.buffer
        val yRow = yPlane.rowStride
        val uPlane = img.planes[1]
        val vPlane = img.planes[2]
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uRow = uPlane.rowStride
        val vRow = vPlane.rowStride
        val uvPix = uPlane.pixelStride

        val w = img.width
        val h = img.height

        for (j in 0 until MASK_H) {
            val sy = ((j * h) / MASK_H).coerceIn(0, h - 1)
            val suv = sy / 2
            for (i in 0 until MASK_W) {
                val sx = ((i * w) / MASK_W).coerceIn(0, w - 1)
                val suvx = sx / 2

                val y = yBuf.get(sy * yRow + sx).toInt() and 0xFF
                val u = (uBuf.get(suv * uRow + suvx * uvPix).toInt() and 0xFF) - 128
                val v = (vBuf.get(suv * vRow + suvx * uvPix).toInt() and 0xFF) - 128

                // YCbCr skin gate: chroma-dominant so it survives exposure swings.
                val skin = (v in 15..65) && (u in -45..5) && (y > 45) && (y < 245)
                // Reject strongly blue/green pixels which otherwise alias on skin.
                val notBlue = v > u
                mask[j * MASK_W + i] = if (skin && notBlue) 1 else 0
            }
        }
        // 3x3 majority filter kills salt-and-pepper noise from compression.
        for (j in 1 until MASK_H - 1) {
            for (i in 1 until MASK_W - 1) {
                var s = 0
                for (dy in -1..1) for (dx in -1..1) s += mask[(j + dy) * MASK_W + (i + dx)]
                labels[j * MASK_W + i] = if (s >= 5) 1 else 0
            }
        }
        for (i in 0 until MASK_W) { labels[i] = 0; labels[(MASK_H - 1) * MASK_W + i] = 0 }
        for (j in 0 until MASK_H) { labels[j * MASK_W] = 0; labels[j * MASK_W + MASK_W - 1] = 0 }
    }

    /** 4-connected flood fill; keeps the [MAX_HANDS] largest components. */
    private fun labelComponents(): Int {
        for (i in labels.indices) labels[i] = if (mask[i].toInt() != 0) -1 else 0
        var best = 0
        for (b in blobs) b.count = 0

        var found = 0
        for (start in labels.indices) {
            if (labels[start] != -1) continue
            found++
            val b = if (found <= MAX_HANDS) blobs[found - 1] else null
            var head = 0; var tail = 0
            queue[tail++] = start
            labels[start] = found
            var count = 0
            var sumX = 0f; var sumY = 0f
            var minX = 1e9f; var maxX = -1e9f; var minY = 1e9f; var maxY = -1e9f
            var cxx = 0f; var cyy = 0f; var cxy = 0f

            while (head < tail) {
                val p = queue[head++]
                val px = p % MASK_W
                val py = p / MASK_W
                count++
                sumX += px; sumY += py
                if (px < minX) minX = px.toFloat()
                if (px > maxX) maxX = px.toFloat()
                if (py < minY) minY = py.toFloat()
                if (py > maxY) maxY = py.toFloat()
                cxx += px * px.toFloat(); cyy += py * py.toFloat(); cxy += px * py.toFloat()

                if (px > 0 && labels[p - 1] == -1) { labels[p - 1] = found; queue[tail++] = p - 1 }
                if (px < MASK_W - 1 && labels[p + 1] == -1) { labels[p + 1] = found; queue[tail++] = p + 1 }
                if (py > 0 && labels[p - MASK_W] == -1) { labels[p - MASK_W] = found; queue[tail++] = p - MASK_W }
                if (py < MASK_H - 1 && labels[p + MASK_W] == -1) { labels[p + MASK_W] = found; queue[tail++] = p + MASK_W }
            }

            if (b != null && count > b.count) {
                // Shift the previous best out so we keep the two largest.
                if (found <= MAX_HANDS) {
                    b.count = count
                    b.sumX = sumX; b.sumY = sumY
                    b.minX = minX; b.maxX = maxX; b.minY = minY; b.maxY = maxY
                    b.cxx = cxx; b.cyy = cyy; b.cxy = cxy
                }
            } else if (found <= MAX_HANDS && b != null) {
                b.count = count
                b.sumX = sumX; b.sumY = sumY
                b.minX = minX; b.maxX = maxX; b.minY = minY; b.maxY = maxY
                b.cxx = cxx; b.cyy = cyy; b.cxy = cxy
            }
            if (found > MAX_HANDS && count > blobs[MAX_HANDS - 1].count) {
                val b2 = blobs[MAX_HANDS - 1]
                b2.count = count
                b2.sumX = sumX; b2.sumY = sumY
                b2.minX = minX; b2.maxX = maxX; b2.minY = minY; b2.maxY = maxY
                b2.cxx = cxx; b2.cyy = cyy; b2.cxy = cxy
            }
            if (found > 64) break
        }
        // Analyse shape for each retained blob.
        var kept = 0
        for (b in blobs) {
            if (b.count <= 0) continue
            analyseShape(b)
            kept++
        }
        return kept
    }

    private fun analyseShape(b: Blob) {
        val n = b.count.toFloat()
        val mx = b.sumX / n
        val my = b.sumY / n
        val vxx = b.cxx / n - mx * mx
        val vyy = b.cyy / n - my * my
        val vxy = b.cxy / n - mx * my
        // Principal axis of the covariance matrix.
        val theta = 0.5f * kotlin.math.atan2(2f * vxy, vxx - vyy)
        b.axisX = kotlin.math.cos(theta)
        b.axisY = kotlin.math.sin(theta)
        b.length = 4f * sqrt(maxOf(vxx, vyy) + 1e-4f)
        b.width = 4f * sqrt(minOf(vxx, vyy) + 1e-4f)
        b.sumX = mx * n
        b.sumY = my * n
    }

    // ------------------------------------------------------------------ depth

    private fun readDepth(img: Image?) {
        if (img == null) { depthData = null; return }
        try {
            val plane = img.planes[0]
            val buf = plane.buffer
            val w = img.width
            val h = img.height
            if (depthData == null || depthW != w || depthH != h) {
                depthW = w; depthH = h
                depthData = ShortArray(w * h)
            }
            val out = depthData ?: return
            val rowStride = plane.rowStride
            val pixStride = plane.pixelStride
            if (pixStride == 2 && rowStride == w * 2) {
                buf.asShortBuffer().get(out, 0, w * h)
            } else {
                var k = 0
                for (j in 0 until h) {
                    for (i in 0 until w) {
                        out[k++] = buf.getShort(j * rowStride + i * pixStride)
                    }
                }
            }
        } catch (t: Throwable) {
            depthData = null
        }
    }

    /** Metric distance (metres) at a mask-space coordinate, or -1 if unknown. */
    private fun depthAt(mx: Float, my: Float): Float {
        val d = depthData ?: return -1f
        if (depthW <= 0 || depthH <= 0) return -1f
        val px = (mx / MASK_W * depthW).toInt().coerceIn(0, depthW - 1)
        val py = (my / MASK_H * depthH).toInt().coerceIn(0, depthH - 1)
        var sum = 0; var cnt = 0
        for (dy in -2..2) {
            for (dx in -2..2) {
                val x = px + dx; val y = py + dy
                if (x < 0 || y < 0 || x >= depthW || y >= depthH) continue
                val mm = d[y * depthW + x].toInt() and 0xFFFF
                if (mm in 100..8000) { sum += mm; cnt++ }
            }
        }
        if (cnt == 0) return -1f
        return (sum.toFloat() / cnt) / 1000f
    }

    // ------------------------------------------------------------------ skeleton

    private val camPoint = FloatArray(4)
    private val worldPoint = FloatArray(3)

    private fun fillHand(
        h: HandModel, b: Blob,
        fx: Float, fy: Float, cx: Float, cy: Float,
        imgW: Float, imgH: Float,
        cameraWorldPose: FloatArray, dt: Float
    ) {
        val mx = b.sumX / b.count
        val my = b.sumY / b.count

        var z = depthAt(mx, my)
        if (z <= 0f) {
            // Perspective fallback: a hand of ~0.09 m spans fewer pixels when far.
            val areaMeters = b.length * b.width / (MASK_W * MASK_H).toFloat()
            z = (0.0135f / (areaMeters + 1e-5f)).coerceIn(0.25f, 1.4f)
        }

        // Mask space -> image pixels -> camera space (OpenGL: -Z forward).
        val ix = mx / MASK_W * imgW
        val iy = my / MASK_H * imgH
        camPoint[0] = (ix - cx) / fx * z
        camPoint[1] = -(iy - cy) / fy * z
        camPoint[2] = -z
        camPoint[3] = 1f
        Mat4.transformPoint(cameraWorldPose, camPoint[0], camPoint[1], camPoint[2], worldPoint, 0)

        // Palm scale: depth is the dominant cue; clamp to a sane hand size.
        val palm = (0.0155f * z / 0.4f + 0.055f).coerceIn(0.055f, 0.14f)
        h.palmSize += (palm - h.palmSize) * (1f - kotlin.math.exp(-8f * dt))

        // Mask axes -> world axes.
        val ax = b.axisX; val ay = b.axisY
        val axW = FloatArray(3)
        val ayW = FloatArray(3)
        unprojectDir(ax, ay, cameraWorldPose, fx, fy, axW)
        unprojectDir(-ay, ax, cameraWorldPose, fx, fy, ayW)

        // Orient the "up" axis so it points away from the wrist end of the blob.
        buildSkeleton(h, worldPoint, axW, ayW, h.palmSize, b, dt)

        h.visible = true
        h.confidence = minOf(b.count.toFloat() / 400f, 1f)
        h.rebuildPalmBasis()
        resolver.resolve(h, dt)
    }

    /** Converts a mask-space direction into a normalised world direction. */
    private val dirTmp = FloatArray(4)
    private fun unprojectDir(dx: Float, dy: Float, cameraWorldPose: FloatArray,
                             fx: Float, fy: Float, out: FloatArray) {
        val px = dx / fx
        val py = -dy / fy
        dirTmp[0] = px; dirTmp[1] = py; dirTmp[2] = -1f; dirTmp[3] = 0f
        val ox = cameraWorldPose[0] * px + cameraWorldPose[4] * py + cameraWorldPose[8] * -1f
        val oy = cameraWorldPose[1] * px + cameraWorldPose[5] * py + cameraWorldPose[9] * -1f
        val oz = cameraWorldPose[2] * px + cameraWorldPose[6] * py + cameraWorldPose[10] * -1f
        val l = sqrt(ox * ox + oy * oy + oz * oz)
        if (l > 1e-6f) { out[0] = ox / l; out[1] = oy / l; out[2] = oz / l }
        else { out[0] = 0f; out[1] = 1f; out[2] = 0f }
        dirTmp[3] = 1f
    }

    private val jtarget = FloatArray(HandModel.JOINT_COUNT * 3)

    private fun buildSkeleton(
        h: HandModel, palm: FloatArray, up: FloatArray, side: FloatArray,
        palmSize: Float, b: Blob, dt: Float
    ) {
        val s = palmSize
        // Finger fan offsets across the palm, in "side" units.
        val fan = floatArrayOf(0.55f, 0.30f, 0.0f, -0.32f, -0.62f)   // thumb..pinky
        val lenMcp = floatArrayOf(0.55f, 1.05f, 1.15f, 1.05f, 0.85f)
        val lenTip = floatArrayOf(1.15f, 2.05f, 2.25f, 2.05f, 1.70f)

        setJoint(h, HandModel.WRIST, palm, up, side, 0f, -0.95f, 0f, s)

        val mcpIdx = intArrayOf(HandModel.THUMB_MCP, HandModel.INDEX_MCP, HandModel.MIDDLE_MCP,
            HandModel.RING_MCP, HandModel.PINKY_MCP)
        val pipIdx = intArrayOf(HandModel.THUMB_IP, HandModel.INDEX_PIP, HandModel.MIDDLE_PIP,
            HandModel.RING_PIP, HandModel.PINKY_PIP)
        val dipIdx = intArrayOf(HandModel.THUMB_IP, HandModel.INDEX_DIP, HandModel.MIDDLE_DIP,
            HandModel.RING_DIP, HandModel.PINKY_DIP)
        val tipIdx = HandModel.FINGER_TIPS

        for (f in 0 until 5) {
            setJoint(h, mcpIdx[f], palm, up, side, fan[f], lenMcp[f], 0f, s)
            setJoint(h, pipIdx[f], palm, up, side, fan[f], lenMcp[f] + (lenTip[f] - lenMcp[f]) * 0.45f, 0.02f, s)
            setJoint(h, dipIdx[f], palm, up, side, fan[f], lenMcp[f] + (lenTip[f] - lenMcp[f]) * 0.75f, 0.04f, s)
            setJoint(h, tipIdx[f], palm, up, side, fan[f] * 0.92f, lenTip[f], 0.06f, s)
        }
        setJoint(h, HandModel.THUMB_CMC, palm, up, side, 0.42f, 0.25f, 0f, s)

        // Temporal smoothing keeps the skeleton stable between noisy frames.
        val k = 1f - kotlin.math.exp(-jointSmoothing * dt)
        if (!h.visible) {
            System.arraycopy(jtarget, 0, h.joints, 0, h.joints.size)
        } else {
            for (i in h.joints.indices) h.joints[i] += (jtarget[i] - h.joints[i]) * k
        }
    }

    private fun setJoint(h: HandModel, idx: Int, palm: FloatArray, up: FloatArray, side: FloatArray,
                         sideAmt: Float, upAmt: Float, fwdAmt: Float, s: Float) {
        val o = idx * 3
        jtarget[o] = palm[0] + side[0] * sideAmt * s + up[0] * upAmt * s
        jtarget[o + 1] = palm[1] + side[1] * sideAmt * s + up[1] * upAmt * s
        jtarget[o + 2] = palm[2] + side[2] * sideAmt * s + up[2] * upAmt * s
        if (abs(fwdAmt) > 1e-4f) {
            // Slight forward curl so fingers do not intersect the palm plane.
            jtarget[o] += side[2] * fwdAmt * s * 0f
        }
    }

    private fun decay(h: HandModel, dt: Float) {
        if (h.visible) {
            h.confidence -= dt * 4f
            if (h.confidence <= 0f) h.reset()
        } else {
            h.reset()
        }
    }
}
