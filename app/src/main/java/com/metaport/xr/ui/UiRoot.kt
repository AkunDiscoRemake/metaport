package com.metaport.xr.ui

import com.metaport.xr.ar.HandModel
import com.metaport.xr.audio.Sfx
import com.metaport.xr.core.gl.MeshGen
import com.metaport.xr.core.gl.TextMesh
import com.metaport.xr.core.math.Mat4
import com.metaport.xr.input.InputState
import com.metaport.xr.render.Pipeline
import com.metaport.xr.scene.GlState
import com.metaport.xr.scene.Material
import com.metaport.xr.scene.Scene

/**
 * Focus, dwell and activation for the whole spatial UI.
 *
 * Three ray sources are merged, in priority order:
 *   1. hand pinch rays (index fingertip) when hand tracking is active,
 *   2. the gaze ray from the head — always available, this is the Cardboard path.
 *
 * Selection happens on the Cardboard trigger *or* after a dwell timer, which is
 * what makes the app usable in a viewer that has no controller at all.
 */
class UiRoot {
    val panels = ArrayList<Panel3D>()

    var focused: Widget? = null
        private set
    var focusedPanel: Panel3D? = null
        private set

    var dwellTime = 0f
    var dwellProgress = 0f
        private set
    var dwellSeconds = Theme.DWELL_SECONDS
    var dwellEnabled = true

    /** World position of the current focus point (for the reticle + audio pan). */
    val focusPoint = FloatArray(3)
    var focusDistance = 3f
    var hasFocus = false

    /** Set by the renderer each frame. */
    var rayOrigin = FloatArray(3)
    var rayDir = FloatArray(3)

    private val local = FloatArray(3)
    private val worldHit = FloatArray(3)
    private var lastFocused: Widget? = null
    private var suppressUntil = 0f

    private val reticleMat = Material().apply {
        blend = Material.BLEND_ADDITIVE
        depthWrite = false
        cullBack = false
        unlit = true
        unlitMode = 1
        glow = 1.2f
    }
    private val reticleModel = FloatArray(16)
    private var reticleMesh: com.metaport.xr.core.gl.Mesh? = null
    private var dwellMesh: com.metaport.xr.core.gl.Mesh? = null
    private var dwellFillMesh: com.metaport.xr.core.gl.DynamicMesh? = null

    fun add(p: Panel3D): Panel3D {
        panels.add(p)
        return p
    }

    fun remove(p: Panel3D) {
        panels.remove(p)
        if (focusedPanel === p) { focused = null; focusedPanel = null }
    }

    fun clear() {
        for (p in panels) p.release()
        panels.clear()
        focused = null
        focusedPanel = null
    }

    fun init() {
        reticleMesh = MeshGen.sphere(1f, 16, 12)
        dwellMesh = MeshGen.ring(0.72f, 1f, 48)
        dwellFillMesh = com.metaport.xr.core.gl.DynamicMesh(96)
    }

    /** Rebuilds the dwell progress arc. */
    private fun rebuildDwell(progress: Float) {
        val dm = dwellFillMesh ?: return
        val b = com.metaport.xr.core.gl.MeshBuilder(64)
        val seg = 48
        val p = progress.coerceIn(0f, 1f)
        if (p > 0.001f) {
            val steps = (seg * p).toInt().coerceAtLeast(2)
            for (i in 0..steps) {
                val a = i.toFloat() / seg * (2f * kotlin.math.PI.toFloat())
                val c = kotlin.math.cos(a); val s = kotlin.math.sin(a)
                b.color(0.14f, 0.9f, 1f, 1f)
                b.v(c * 0.72f, 0f, s * 0.72f, 0f, 1f, 0f, 0f, 0f)
                b.v(c * 1.0f, 0f, s * 1.0f, 0f, 1f, 0f, 1f, 0f)
            }
            for (i in 0 until steps) {
                val a = i * 2
                b.quad(a, a + 2, a + 3, a + 1)
            }
        } else {
            b.v(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
            b.v(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
            b.v(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
            b.tri(0, 1, 2)
        }
        dm.upload(b)
    }

    /**
     * Runs the raycast for this frame.
     * @return true if something was activated this frame.
     */
    fun update(dt: Float, input: InputState, time: Float): Boolean {
        var activated = false

        // --- choose the strongest ray source -------------------------------
        var ox = rayOrigin[0]; var oy = rayOrigin[1]; var oz = rayOrigin[2]
        var dx = rayDir[0]; var dy = rayDir[1]; var dz = rayDir[2]

        val hand = input.rightHand?.takeIf { it.visible } ?: input.leftHand?.takeIf { it.visible }
        var usingHand = false
        if (hand != null && hand.gesture == HandModel.GESTURE_POINT) {
            val dir = tmpDir
            hand.indexDirection(dir)
            ox = hand.joints[HandModel.INDEX_TIP * 3]
            oy = hand.joints[HandModel.INDEX_TIP * 3 + 1]
            oz = hand.joints[HandModel.INDEX_TIP * 3 + 2]
            dx = dir[0]; dy = dir[1]; dz = dir[2]
            usingHand = true
        }

        // --- raycast panels ------------------------------------------------
        var bestT = Float.MAX_VALUE
        var bestPanel: Panel3D? = null
        for (p in panels) {
            if (p.appear <= 0.05f || !p.visible) continue
            p.updateTransform()
            val t = p.intersectRay(ox, oy, oz, dx, dy, dz)
            if (t > 0f && t < bestT) {
                val lx = ox + dx * t
                val ly = oy + dy * t
                val lz = oz + dz * t
                p.toLocal(lx, ly, lz, local)
                if (kotlin.math.abs(local[0]) <= p.width * 0.5f && kotlin.math.abs(local[1]) <= p.height * 0.5f) {
                    bestT = t
                    bestPanel = p
                    worldHit[0] = lx; worldHit[1] = ly; worldHit[2] = lz
                }
            }
        }

        var hitWidget: Widget? = null
        if (bestPanel != null) {
            bestPanel.toLocal(worldHit[0], worldHit[1], worldHit[2], local)
            for (i in bestPanel.widgets.indices.reversed()) {
                val w = bestPanel.widgets[i]
                if (w.hitTest(local[0], local[1])) { hitWidget = w; break }
            }
        }

        // --- focus transitions --------------------------------------------
        if (hitWidget !== focused) {
            focused?.focused = false
            focused?.onHoverExit()
            focused = hitWidget
            focused?.focused = true
            focused?.onHoverEnter()
            if (hitWidget != null && hitWidget !== lastFocused) Sfx.play(Sfx.HOVER)
            lastFocused = hitWidget
            dwellTime = 0f
        }
        focusedPanel = bestPanel

        hasFocus = bestPanel != null
        if (bestPanel != null) {
            focusPoint[0] = worldHit[0]; focusPoint[1] = worldHit[1]; focusPoint[2] = worldHit[2]
            focusDistance = bestT
        } else {
            focusPoint[0] = ox + dx * 4f
            focusPoint[1] = oy + dy * 4f
            focusPoint[2] = oz + dz * 4f
            focusDistance = 4f
        }

        // --- dwell ---------------------------------------------------------
        dwellProgress = 0f
        if (dwellEnabled && hitWidget != null && hitWidget.enabled) {
            dwellTime += dt
            dwellProgress = (dwellTime / dwellSeconds).coerceIn(0f, 1f)
            if (dwellProgress >= 1f) {
                activate(hitWidget)
                dwellTime = 0f
                dwellProgress = 0f
                activated = true
            }
        } else {
            dwellTime = 0f
        }

        // --- explicit activation -------------------------------------------
        val pinch = input.rightPinchEdge || input.leftPinchEdge
        if ((input.triggerPressed || pinch) && time > suppressUntil) {
            val target = focused ?: focusedSliderTarget(bestPanel, local)
            if (target != null) {
                activate(target)
                activated = true
            } else if (bestPanel != null) {
                Sfx.play(Sfx.TICK)
            }
        }

        // Slider dragging with the trigger held.
        if (input.triggerDown || input.rightPinch || input.leftPinch) {
            val sl = focused as? Slider
            if (sl != null && bestPanel != null) {
                val halfTrack = (sl.width - 0.05f) * 0.5f
                val rel = ((local[0] - sl.x) / halfTrack * 0.5f + 0.5f).coerceIn(0f, 1f)
                sl.setValue(sl.min + rel * (sl.max - sl.min))
                sl.dragging = true
            }
        } else {
            (focused as? Slider)?.dragging = false
        }

        if (usingHand) {
            // Hand ray keeps the reticle attached to the fingertip.
            focusPoint[0] = ox + dx * focusDistance
            focusPoint[1] = oy + dy * focusDistance
            focusPoint[2] = oz + dz * focusDistance
        }

        if (activated) suppressUntil = time + 0.18f
        return activated
    }

    private fun focusedSliderTarget(panel: Panel3D?, local: FloatArray): Widget? {
        val p = panel ?: return null
        for (w in p.widgets) if (w is Slider && w.hitTest(local[0], local[1])) return w
        return null
    }

    private val tmpDir = FloatArray(3)

    private fun activate(w: Widget) {
        w.active = 1f
        w.onSelect()
        Sfx.play(if (w is Button) Sfx.SELECT else Sfx.TICK)
    }

    /** Draws the reticle + dwell arc at the focus point. */
    fun renderReticle(scene: Scene, pipeline: Pipeline, time: Float) {
        val mesh = reticleMesh ?: return
        val d = focusDistance.coerceIn(0.4f, 20f)
        val scale = d * 0.016f * (if (hasFocus) 1.25f else 1f)
        val pulse = 1f + 0.12f * kotlin.math.sin(time * 3.4f)

        Mat4.compose(reticleModel, focusPoint[0], focusPoint[1], focusPoint[2], 0f, 0f, 0f,
            scale * pulse, scale * pulse, scale * pulse)

        val acc = if (focused != null) Theme.accent(focused!!.accentIndex) else floatArrayOf(0.55f, 0.8f, 1f)
        reticleMat.rgb(acc[0], acc[1], acc[2], 0.95f)
        reticleMat.emissive(acc[0], acc[1], acc[2], 1.4f)
        scene.push(mesh, reticleMat, reticleModel, Scene.LAYER_OVERLAY)

        if (dwellProgress > 0.01f) {
            rebuildDwell(dwellProgress)
            // Face the ring towards the head so the arc reads correctly in stereo.
            Mat4.lookRotation(
                reticleModel,
                focusPoint,
                floatArrayOf(pipeline.camX, pipeline.camY, pipeline.camZ),
                floatArrayOf(0f, 1f, 0f)
            )
            val rs = d * 0.03f
            reticleModel[0] *= rs; reticleModel[1] *= rs; reticleModel[2] *= rs
            reticleModel[4] *= rs; reticleModel[5] *= rs; reticleModel[6] *= rs
            reticleModel[8] *= rs; reticleModel[9] *= rs; reticleModel[10] *= rs

            val mvp = FloatArray(16)
            Mat4.multiply(mvp, 0, pipeline.viewProjection, 0, reticleModel, 0)
            pipeline.unlit.use()
            GlState.blend(Material.BLEND_ADDITIVE)
            GlState.depthWrite(false)
            GlState.depthTest(true)
            GlState.cullBack(false)
            pipeline.unlit.setMat4("uMVP", mvp)
            pipeline.unlit.setMat4("uModel", reticleModel)
            pipeline.unlit.setVec4("uBaseColor", acc[0], acc[1], acc[2], 0.9f)
            pipeline.unlit.setFloat("uAlphaMul", 1f)
            pipeline.unlit.setInt("uMode", 0)
            pipeline.unlit.setFloat("uGlow", 1f)
            pipeline.unlit.setFloat("uTime", time)
            dwellFillMesh?.draw()
        }
    }

    fun release() {
        reticleMesh?.release(); reticleMesh = null
        dwellMesh?.release(); dwellMesh = null
        dwellFillMesh?.release(); dwellFillMesh = null
        clear()
    }
}
