package com.metaport.xr.devapi

/** Minimal JSON writer — MetaPort has no third-party dependencies. */
class Json {
    private val sb = StringBuilder(256)

    fun obj(body: Json.() -> Unit): String {
        sb.setLength(0)
        beginObj()
        body()
        endObj()
        return sb.toString()
    }

    fun beginObj() { sb.append('{'); first.add(true) }
    fun endObj() { first.removeAt(first.size - 1); sb.append('}') }
    fun beginArr() { sb.append('['); first.add(true) }
    fun endArr() { first.removeAt(first.size - 1); sb.append(']') }

    private val first = ArrayList<Boolean>()

    private fun sep() {
        if (first.isEmpty()) return
        val i = first.size - 1
        if (first[i]) first[i] = false else sb.append(',')
    }

    fun key(k: String) {
        sep()
        str(k)
        sb.append(':')
    }

    fun str(v: String) {
        sb.append('"')
        for (c in v) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    fun put(k: String, v: String) { key(k); str(v) }
    fun put(k: String, v: Number) { key(k); sb.append(v) }
    fun put(k: String, v: Boolean) { key(k); sb.append(v) }
    fun putNull(k: String) { key(k); sb.append("null") }

    fun putObj(k: String, body: Json.() -> Unit) {
        key(k)
        beginObj(); body(); endObj()
    }

    fun putArr(k: String, body: Json.() -> Unit) {
        key(k)
        beginArr(); body(); endArr()
    }

    fun arrItem(v: Number) { sep(); sb.append(v) }
    fun arrItem(v: String) { sep(); str(v) }
    fun arrObj(body: Json.() -> Unit) { sep(); beginObj(); body(); endObj() }

    companion object {
        /** One-shot map serialiser for the status endpoints. */
        fun from(map: Map<String, Any?>): String {
            val j = Json()
            j.sb.setLength(0)
            j.beginObj()
            writeMap(j, map)
            j.endObj()
            return j.sb.toString()
        }

        private fun writeMap(j: Json, map: Map<String, Any?>) {
            for ((k, v) in map) {
                when (v) {
                    null -> j.putNull(k)
                    is String -> j.put(k, v)
                    is Boolean -> j.put(k, v)
                    is Number -> j.put(k, v)
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        j.putObj(k) { writeMap(j, v as Map<String, Any?>) }
                    }
                    is List<*> -> {
                        j.putArr(k) {
                            for (item in v) {
                                when (item) {
                                    is Map<*, *> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        arrObj { writeMap(j, item as Map<String, Any?>) }
                                    }
                                    is Number -> arrItem(item)
                                    is String -> arrItem(item)
                                    else -> arrItem(item.toString())
                                }
                            }
                        }
                    }
                    else -> j.put(k, v.toString())
                }
            }
        }

        /** Extremely small flat JSON object parser for POST bodies. */
        fun parseFlat(body: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            var i = 0
            while (i < body.length) {
                while (i < body.length && body[i] != '"') i++
                if (i >= body.length) break
                val keyStart = ++i
                while (i < body.length && body[i] != '"') i++
                val key = body.substring(keyStart, i.coerceAtMost(body.length))
                i++
                while (i < body.length && body[i] != ':') i++
                i++
                while (i < body.length && (body[i] == ' ' || body[i] == '\t' || body[i] == '\n')) i++
                if (i >= body.length) break
                if (body[i] == '"') {
                    val vs = ++i
                    while (i < body.length && body[i] != '"') i++
                    out[key] = body.substring(vs, i.coerceAtMost(body.length))
                    i++
                } else {
                    val vs = i
                    while (i < body.length && body[i] != ',' && body[i] != '}') i++
                    out[key] = body.substring(vs, i).trim()
                }
            }
            return out
        }
    }
}
