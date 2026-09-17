package com.metaport.xr.input

import com.metaport.xr.ar.HandModel

/**
 * Unified input snapshot for a frame.
 *
 * Cardboard viewers give a single trigger (magnet or indirect touch); MetaPort
 * also accepts on-screen touch, and — when hand tracking is on — pinch and
 * point gestures from both hands.
 */
class InputState {
    // Gaze
    val gazeOrigin = FloatArray(3)
    val gazeDir = FloatArray(3)

    // Cardboard trigger / screen tap
    var triggerDown = false
    var triggerPressed = false   // edge: pressed this frame
    var triggerReleased = false  // edge: released this frame

    // Touch
    var touchDown = false
    var touchX = 0f
    var touchY = 0f
    var doubleTap = false
    var longPress = false

    // Hands
    var handsEnabled = false
    var leftPinch = false
    var rightPinch = false
    var leftPinchEdge = false
    var rightPinchEdge = false
    var leftOpen = false
    var rightOpen = false

    /** Raw per-frame hand models (null when a hand is not visible). */
    var leftHand: HandModel? = null
    var rightHand: HandModel? = null

    // Dev API / remote control injection
    var remoteAction: String? = null
    var remoteValue: Float = 0f

    /** Locomotion axes from the dock pad or Dev API (-1..1). */
    var moveX = 0f
    var moveY = 0f

    var snapTurnRequest = 0f

    fun newFrame() {
        triggerPressed = false
        triggerReleased = false
        leftPinchEdge = false
        rightPinchEdge = false
        doubleTap = false
        longPress = false
        remoteAction = null
        snapTurnRequest = 0f
        moveX = 0f
        moveY = 0f
    }
}
