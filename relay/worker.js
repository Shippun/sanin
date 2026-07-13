// Cloudflare Worker — OAuth relay for Sanin TV QR login
// Deploy: wrangler deploy
// Bind a KV namespace named SESSIONS in wrangler.toml:
//   [[kv_namespaces]]
//   binding = "SESSIONS"
//   id = "your-kv-id-here"
//
// After deploy, set this URL in the app:
//   SettingsAccountActivity.relayBaseUrl = "https://sanin-relay.your-name.workers.dev"

const CLIENT_ID = 14959
const DEPLOY_URL = "https://sanin-relay.your-name.workers.dev"
const REDIRECT_URI = DEPLOY_URL + "/callback"

async function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
  })
}

async function htmlResponse(body, status = 200) {
  return new Response(body, {
    status,
    headers: { "Content-Type": "text/html;charset=UTF-8" },
  })
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    const path = url.pathname

    // CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET,POST,OPTIONS", "Access-Control-Allow-Headers": "*" },
      })
    }

    // POST /api/session — create a new login session
    if (request.method === "POST" && path === "/api/session") {
      const sessionId = crypto.randomUUID()
      const expiresAt = Date.now() + 600_000 // 10 minutes
      await env.SESSIONS.put(sessionId, JSON.stringify({ status: "pending", token: null, expiresAt }), { expirationTtl: 600 })
      return jsonResponse({ sessionId, expiresAt })
    }

    // GET /api/poll/:sessionId — check if authorised
    if (request.method === "GET" && path.startsWith("/api/poll/")) {
      const sessionId = path.replace("/api/poll/", "")
      const raw = await env.SESSIONS.get(sessionId)
      if (!raw) return jsonResponse({ error: "session not found" }, 404)
      const session = JSON.parse(raw)
      if (session.status === "authorized") {
        return jsonResponse({ status: "authorized", token: session.token })
      }
      return jsonResponse({ status: "pending" })
    }

    // GET /auth/:sessionId — QR target, redirects to AniList OAuth
    if (request.method === "GET" && path.startsWith("/auth/")) {
      const sessionId = path.replace("/auth/", "")
      const oauthUrl = `https://anilist.co/api/v2/oauth/authorize?client_id=${CLIENT_ID}&response_type=token&redirect_uri=${encodeURIComponent(REDIRECT_URI + "?session=" + sessionId)}`
      return htmlResponse(`<!DOCTYPE html><html><body><script>window.location.href="${oauthUrl}"</script></body></html>`)
    }

    // GET /callback — OAuth redirect target, captures token from fragment
    if (request.method === "GET" && path === "/callback") {
      const sessionId = url.searchParams.get("session")
      // Token is in the URL fragment, which doesn't reach the server.
      // We serve a page that reads the fragment with JS and POSTs it.
      return htmlResponse(`<!DOCTYPE html>
<html>
<body>
<script>
const hash = window.location.hash.substring(1)
const params = new URLSearchParams(hash)
const token = params.get("access_token")
if (token && "${sessionId}") {
  fetch("/api/callback", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId: "${sessionId}", token })
  }).then(() => {
    document.body.innerHTML = "<h2>Logged in! You can close this page.</h2>"
  })
} else {
  document.body.innerHTML = "<h2>Authorization failed.</h2>"
}
</script>
</body>
</html>`)
    }

    // POST /api/callback — store the token
    if (request.method === "POST" && path === "/api/callback") {
      const { sessionId, token } = await request.json()
      if (!sessionId || !token) return jsonResponse({ error: "missing fields" }, 400)
      const raw = await env.SESSIONS.get(sessionId)
      if (!raw) return jsonResponse({ error: "session not found" }, 404)
      const session = JSON.parse(raw)
      session.status = "authorized"
      session.token = token
      await env.SESSIONS.put(sessionId, JSON.stringify(session))
      return jsonResponse({ status: "authorized" })
    }

    return new Response("Not found", { status: 404 })
  }
}
