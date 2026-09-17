package com.metaport.xr.devapi

/**
 * MetaPort SDK surface.
 *
 * This is the in-process dev API: third-party code (or a future plugin loader)
 * registers games and environments here, subscribes to runtime events, and reads
 * or writes settings. The HTTP server in [DevApiServer] is a thin remote view of
 * the same object graph.
 */
object MetaPortSdk {
    const val VERSION = "3.0.0"
    const val API_LEVEL = 1

    /** Runtime event names published on the bus. */
    const val EV_GAME_LAUNCHED = "game.launched"
    const val EV_GAME_EXITED = "game.exited"
    const val EV_ENV_CHANGED = "environment.changed"
    const val EV_HAND_LOST = "hand.lost"
    const val EV_HAND_FOUND = "hand.found"
    const val EV_TRACKING_CHANGED = "tracking.changed"
    const val EV_PLANE_ADDED = "plane.added"

    fun interface Listener {
        fun onEvent(name: String, payload: Map<String, Any?>)
    }

    private val listeners = ArrayList<Listener>()
    private val settings = LinkedHashMap<String, String>()
    private val eventLog = ArrayDeque<Pair<Long, String>>()

    fun addListener(l: Listener) { synchronized(listeners) { listeners.add(l) } }
    fun removeListener(l: Listener) { synchronized(listeners) { listeners.remove(l) } }

    fun emit(name: String, payload: Map<String, Any?> = emptyMap()) {
        synchronized(listeners) {
            for (l in listeners) {
                try { l.onEvent(name, payload) } catch (t: Throwable) { /* isolate plugins */ }
            }
        }
        synchronized(eventLog) {
            eventLog.addLast(System.currentTimeMillis() to name)
            while (eventLog.size > 64) eventLog.removeFirst()
        }
    }

    fun recentEvents(limit: Int = 24): List<Map<String, Any?>> = synchronized(eventLog) {
        eventLog.toList().takeLast(limit).map {
            linkedMapOf<String, Any?>("t" to it.first, "event" to it.second)
        }
    }

    fun setSetting(key: String, value: String) { synchronized(settings) { settings[key] = value } }
    fun getSetting(key: String): String? = synchronized(settings) { settings[key] }
    fun allSettings(): Map<String, String> = synchronized(settings) { LinkedHashMap(settings) }
}

/** Ring buffer of frame statistics exposed through `/api/v1/telemetry`. */
class Telemetry(private val capacity: Int = 240) {
    private val stamps = LongArray(capacity)
    private var head = 0
    private var filled = 0

    var fps = 0f
        private set
    var frameMs = 0f
        private set
    var drawCalls = 0
    var sceneCommands = 0
    var handMs = 0f
    var arcoreMs = 0f
    var uiMs = 0f
    var totalMs = 0f

    fun frame(nanos: Long, drawCalls: Int, sceneCommands: Int,
              handMs: Float, arcoreMs: Float, uiMs: Float) {
        this.drawCalls = drawCalls
        this.sceneCommands = sceneCommands
        this.handMs = handMs
        this.arcoreMs = arcoreMs
        this.uiMs = uiMs

        if (filled > 0) {
            val prev = stamps[(head - 1 + capacity) % capacity]
            frameMs = (nanos - prev) / 1e6f
        }
        stamps[head] = nanos
        head = (head + 1) % capacity
        if (filled < capacity) filled++

        if (filled > 8) {
            val oldest = stamps[head % capacity]
            val span = (nanos - oldest) / 1e9f
            if (span > 0.05f) fps = (filled - 1) / span
        }
        totalMs = frameMs
    }

    fun toMap(): LinkedHashMap<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m["fps"] = "%.1f".format(fps)
        m["frameMs"] = "%.2f".format(frameMs)
        m["arcoreMs"] = "%.2f".format(arcoreMs)
        m["handTrackingMs"] = "%.2f".format(handMs)
        m["uiMs"] = "%.2f".format(uiMs)
        m["drawCalls"] = drawCalls
        m["sceneCommands"] = sceneCommands
        return m
    }
}
