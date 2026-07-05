package ani.dantotsu.util

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalRelayServer(private val port: Int, private val localIp: String) {

    private var serverSocket: ServerSocket? = null
    private var running = false
    @Volatile var authorizedToken: String? = null
    private val clientId = 14959

    suspend fun start() = withContext(Dispatchers.IO) {
        serverSocket = ServerSocket(port)
        running = true
        while (running) {
            try {
                val client = serverSocket!!.accept()
                handleClient(client)
            } catch (_: Exception) {
                break
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private fun handleClient(client: java.net.Socket) {
        try {
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            // Read headers to find Content-Length
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            when {
                method == "GET" && path == "/auth" -> handleAuth(client)
                method == "GET" && path.startsWith("/callback") -> handleCallback(client, path)
                method == "POST" && path == "/api/callback" -> handleTokenSubmit(client, reader, contentLength)
                method == "GET" && path == "/api/poll" -> handlePoll(client)
                else -> respond(client.outputStream, 404, "text/plain", "Not found")
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleAuth(client: java.net.Socket) {
        val callbackUrl = "http://$localIp:$port/callback"
        val anilistUrl = "https://anilist.co/api/v2/oauth/authorize?client_id=$clientId&response_type=token&redirect_uri=${URLEncoder.encode(callbackUrl, "UTF-8")}"
        val html = """<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Dantotsu Login</title>
<style>
body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#121212;color:#fff;margin:0}
.card{background:#1e1e1e;border-radius:16px;padding:40px;text-align:center;max-width:400px;width:90%}
.btn{display:inline-block;padding:14px 32px;border-radius:50px;background:#87CEEB;color:#000;text-decoration:none;font-weight:600;margin-top:16px}
p{color:#aaa;font-size:14px}
</style></head>
<body><div class="card">
<h2>Login to Dantotsu</h2>
<p>Tap the button to authorize your TV</p>
<a class="btn" href="$anilistUrl">Continue with AniList</a>
</div></body></html>"""
        respond(client.outputStream, 200, "text/html", html)
    }

    private fun handleCallback(client: java.net.Socket, path: String) {
        val html = """<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Logged In</title>
<style>
body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#121212;color:#fff;margin:0}
.card{background:#1e1e1e;border-radius:16px;padding:40px;text-align:center;max-width:400px;width:90%}
.spinner{border:3px solid #333;border-top:3px solid #87CEEB;border-radius:50%;width:40px;height:40px;animation:spin 1s linear infinite;margin:20px auto}
@keyframes spin{to{transform:rotate(360deg)}}
</style></head>
<body><div class="card">
<div class="spinner"></div>
<h2>Logged in!</h2>
<p>Your TV will update shortly. You can close this page.</p>
</div>
<script>
try {
  var h = window.location.hash.substring(1);
  var p = new URLSearchParams(h);
  var t = p.get("access_token");
  if (t) {
    var x = new XMLHttpRequest();
    x.open("POST", "/api/callback", true);
    x.setRequestHeader("Content-Type","application/json");
    x.send(JSON.stringify({token:t}));
  }
} catch(e){}
</script></body></html>"""
        respond(client.outputStream, 200, "text/html", html)
    }

    private fun handleTokenSubmit(client: java.net.Socket, reader: java.io.BufferedReader, contentLength: Int) {
        if (contentLength > 0) {
            val body = CharArray(contentLength).let { reader.read(it); String(it) }
            try {
                val json = org.json.JSONObject(body)
                val token = json.optString("token")
                if (token.isNotEmpty()) {
                    authorizedToken = token
                }
            } catch (_: Exception) {}
        }
        respond(client.outputStream, 200, "application/json", """{"status":"authorized"}""")
    }

    private fun handlePoll(client: java.net.Socket) {
        val response = if (authorizedToken != null) {
            """{"status":"authorized","token":"$authorizedToken"}"""
        } else {
            """{"status":"pending"}"""
        }
        respond(client.outputStream, 200, "application/json", response)
    }

    private fun respond(out: OutputStream, status: Int, contentType: String, body: String) {
        val response = """HTTP/1.1 $status OK
Content-Type: $contentType
Content-Length: ${body.toByteArray().size}
Connection: close
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET,POST,OPTIONS
Access-Control-Allow-Headers: *

$body""".replace("\n", "\r\n")
        out.write(response.toByteArray())
        out.flush()
    }
}
