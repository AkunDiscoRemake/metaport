package com.metaport.xr.devapi

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/**
 * MetaPort Dev API.
 *
 * A dependency-free HTTP/1.1 server running on the device (default port 8765)
 * that exposes the live runtime: status, telemetry, tracked planes, hand
 * skeletons, environment switching, game launching and remote input injection.
 *
 * It is a development tool, so it is opt-in and bound to the LAN only.
 */
class DevApiServer(private val port: Int = 8765) {

    companion object {
        private const val TAG = "MetaPort.DevAPI"
    }

    /** Implemented by the runtime so the API can read and drive the app. */
    interface Host {
        fun statusJson(): Map<String, Any?>
        fun telemetryJson(): Map<String, Any?>
        fun handsJson(): Map<String, Any?>
        fun planesJson(): Map<String, Any?>
        fun listGames(): List<Map<String, Any?>>
        fun listEnvironments(): List<Map<String, Any?>>
        fun launchGame(id: String): Boolean
        fun stopGame(): Boolean
        fun setEnvironment(id: String): Boolean
        fun injectInput(action: String, value: Float): Boolean
        fun setSetting(key: String, value: String): Boolean
    }

    var host: Host? = null
    var running = false
        private set
    var requestsServed: Long
        get() = served.get()
        private set

    private val served = AtomicLong()
    private var server: ServerSocket? = null
    private var thread: Thread? = null

    /** LAN addresses so the UI can show where to point a browser. */
    fun localAddresses(): List<String> {
        val out = ArrayList<String>()
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            for (nif in Collections.list(ifaces)) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in Collections.list(nif.inetAddresses)) {
                    val h = addr.hostAddress ?: continue
                    if (addr is InetAddress && h.contains(':')) continue
                    out.add("http://$h:$port")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "address scan failed", t)
        }
        return out
    }

    fun start(): Boolean {
        if (running) return true
        return try {
            val s = ServerSocket(port)
            server = s
            running = true
            thread = Thread({ acceptLoop(s) }, "MetaPort-DevAPI").apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "Dev API listening on $port")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "could not bind port $port: ${t.message}")
            running = false
            false
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (t: Throwable) {}
        server = null
        thread?.interrupt()
        thread = null
    }

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            val sock = try {
                s.accept()
            } catch (t: Throwable) {
                if (running) Log.w(TAG, "accept failed: ${t.message}")
                return
            }
            Thread({ handle(sock) }, "MetaPort-DevAPI-conn").apply { isDaemon = true }.start()
        }
    }

    private fun handle(sock: Socket) {
        sock.use { c ->
            try {
                c.soTimeout = 5000
                val input = BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8))
                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]

                var contentLength = 0
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                val body = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(buf, read, contentLength - read)
                        if (n < 0) break
                        read += n
                    }
                    String(buf, 0, read)
                } else ""

                served.incrementAndGet()
                val (status, payload, type) = route(method, path, body)
                respond(c.getOutputStream(), status, payload, type)
            } catch (t: Throwable) {
                Log.w(TAG, "connection error: ${t.message}")
            }
        }
    }

    private fun respond(out: OutputStream, status: Int, body: String, type: String) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = StringBuilder()
        head.append("HTTP/1.1 $status $reason\r\n")
        head.append("Content-Type: $type; charset=utf-8\r\n")
        head.append("Content-Length: ${bytes.size}\r\n")
        head.append("Access-Control-Allow-Origin: *\r\n")
        head.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        head.append("Access-Control-Allow-Headers: Content-Type\r\n")
        head.append("Cache-Control: no-store\r\n")
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private data class Result(val status: Int, val body: String, val type: String = "application/json")

    private fun route(method: String, path: String, body: String): Result {
        val h = host
        val clean = path.substringBefore('?')

        if (method == "OPTIONS") return Result(200, "{}")

        if (h == null) return Result(503, """{"error":"runtime not attached"}""")

        return when {
            clean == "/" && method == "GET" -> Result(200, INDEX_HTML, "text/html")
            clean == "/api/v1" && method == "GET" -> Result(200, Json.from(mapOf(
                "name" to "MetaPort Dev API",
                "version" to MetaPortSdk.VERSION,
                "endpoints" to listOf(
                    "/api/v1/status", "/api/v1/telemetry", "/api/v1/hands", "/api/v1/planes",
                    "/api/v1/games", "/api/v1/games/{id}/launch", "/api/v1/games/stop",
                    "/api/v1/environments", "/api/v1/environments/{id}",
                    "/api/v1/input", "/api/v1/settings"
                )
            )))

            clean == "/api/v1/status" && method == "GET" -> Result(200, Json.from(h.statusJson()))
            clean == "/api/v1/telemetry" && method == "GET" -> Result(200, Json.from(h.telemetryJson()))
            clean == "/api/v1/hands" && method == "GET" -> Result(200, Json.from(h.handsJson()))
            clean == "/api/v1/planes" && method == "GET" -> Result(200, Json.from(h.planesJson()))

            clean == "/api/v1/games" && method == "GET" ->
                Result(200, Json.from(mapOf("games" to h.listGames())))

            clean == "/api/v1/games/stop" && method == "POST" ->
                Result(200, Json.from(mapOf("stopped" to h.stopGame())))

            clean.startsWith("/api/v1/games/") && clean.endsWith("/launch") && method == "POST" -> {
                val id = clean.removePrefix("/api/v1/games/").removeSuffix("/launch")
                Result(200, Json.from(mapOf("launched" to h.launchGame(id), "id" to id)))
            }

            clean == "/api/v1/environments" && method == "GET" ->
                Result(200, Json.from(mapOf("environments" to h.listEnvironments())))

            clean.startsWith("/api/v1/environments/") && method == "POST" -> {
                val id = clean.removePrefix("/api/v1/environments/")
                Result(200, Json.from(mapOf("selected" to h.setEnvironment(id), "id" to id)))
            }

            clean == "/api/v1/input" && method == "POST" -> {
                val m = Json.parseFlat(body)
                val action = m["action"] ?: return Result(400, """{"error":"missing action"}""")
                val value = m["value"]?.toFloatOrNull() ?: 0f
                Result(200, Json.from(mapOf("accepted" to h.injectInput(action, value), "action" to action)))
            }

            clean == "/api/v1/settings" && method == "POST" -> {
                val m = Json.parseFlat(body)
                val key = m["key"] ?: return Result(400, """{"error":"missing key"}""")
                Result(200, Json.from(mapOf("applied" to h.setSetting(key, m["value"] ?: ""), "key" to key)))
            }

            else -> Result(404, """{"error":"not found","path":"$clean"}""")
        }
    }

    private val INDEX_HTML = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>MetaPort Dev API</title>
<style>
 :root{--c:#24e0ff;--m:#ff3da6}
 body{background:#05060e;color:#e8f2ff;font:14px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace;margin:0;padding:28px}
 h1{font-size:20px;letter-spacing:.14em;color:var(--c);text-transform:uppercase;margin:0 0 4px}
 p.sub{color:#8fa5c0;margin:0 0 22px}
 .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:14px}
 .card{border:1px solid #1b2740;border-radius:10px;padding:14px;background:#080c18}
 .card h2{font-size:12px;letter-spacing:.12em;color:var(--m);text-transform:uppercase;margin:0 0 10px}
 pre{white-space:pre-wrap;word-break:break-word;font-size:12px;color:#b9d3ee;margin:0;max-height:280px;overflow:auto}
 button{background:#0d1526;border:1px solid #24e0ff55;color:#cfeeff;border-radius:6px;padding:6px 10px;margin:3px 3px 0 0;cursor:pointer;font:inherit}
 button:hover{background:#12203a}
</style></head><body>
<h1>MetaPort Dev API</h1>
<p class="sub">Live runtime introspection &amp; remote control. Everything is JSON over HTTP.</p>
<div class="grid">
 <div class="card"><h2>Status</h2><pre id="status">loading…</pre></div>
 <div class="card"><h2>Telemetry</h2><pre id="telemetry">loading…</pre></div>
 <div class="card"><h2>Hands</h2><pre id="hands">loading…</pre></div>
 <div class="card"><h2>Planes</h2><pre id="planes">loading…</pre></div>
 <div class="card"><h2>Games</h2><pre id="games">loading…</pre><div id="gameBtns"></div></div>
 <div class="card"><h2>Environments</h2><pre id="envs">loading…</pre><div id="envBtns"></div></div>
 <div class="card"><h2>Remote input</h2>
  <button onclick="inp('trigger')">trigger</button>
  <button onclick="inp('snap_left')">snap left</button>
  <button onclick="inp('snap_right')">snap right</button>
  <button onclick="inp('back')">back</button>
 </div>
</div>
<script>
const j=(u)=>fetch(u).then(r=>r.json());
async function tick(){
 try{
  document.getElementById('status').textContent=JSON.stringify(await j('/api/v1/status'),null,1);
  document.getElementById('telemetry').textContent=JSON.stringify(await j('/api/v1/telemetry'),null,1);
  document.getElementById('hands').textContent=JSON.stringify(await j('/api/v1/hands'),null,1);
  document.getElementById('planes').textContent=JSON.stringify(await j('/api/v1/planes'),null,1);
  const g=await j('/api/v1/games');
  document.getElementById('games').textContent=JSON.stringify(g,null,1);
  document.getElementById('gameBtns').innerHTML=(g.games||[]).map(x=>
    `<button onclick="fetch('/api/v1/games/${'$'}{x.id}/launch',{method:'POST'})">${'$'}{x.title||x.id}</button>`).join('');
  const e=await j('/api/v1/environments');
  document.getElementById('envs').textContent=JSON.stringify(e,null,1);
  document.getElementById('envBtns').innerHTML=(e.environments||[]).map(x=>
    `<button onclick="fetch('/api/v1/environments/${'$'}{x.id}',{method:'POST'})">${'$'}{x.name||x.id}</button>`).join('');
 }catch(err){document.getElementById('status').textContent='offline: '+err}
}
function inp(a){fetch('/api/v1/input',{method:'POST',headers:{'Content-Type':'application/json'},
  body:JSON.stringify({action:a,value:1})})}
tick();setInterval(tick,1000);
</script></body></html>"""
}
