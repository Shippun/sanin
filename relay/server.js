const express = require('express')
const crypto = require('crypto')
const app = express()
app.use(express.json())

const CLIENT_ID = 14959
const PORT = process.env.PORT || 3000
const BASE_URL = process.env.BASE_URL || `http://localhost:${PORT}`

const sessions = new Map()
const TTL = 600_000

function cleanup() {
  const now = Date.now()
  for (const [id, s] of sessions) {
    if (now > s.expiresAt) sessions.delete(id)
  }
}
setInterval(cleanup, 60_000)

// Create session (TV calls this)
app.post('/api/session', (req, res) => {
  const sessionId = crypto.randomUUID()
  sessions.set(sessionId, { status: 'pending', token: null, expiresAt: Date.now() + TTL })
  res.json({ sessionId, expiresAt: Date.now() + TTL })
})

// Poll session status (TV calls this every 2s)
app.get('/api/poll/:sessionId', (req, res) => {
  const session = sessions.get(req.params.sessionId)
  if (!session) return res.status(404).json({ error: 'session not found' })
  if (session.status === 'authorized') {
    return res.json({ status: 'authorized', token: session.token })
  }
  res.json({ status: 'pending' })
})

// Auth page — redirect to AniList OAuth (phone scans QR, lands here)
app.get('/auth/:sessionId', (req, res) => {
  const sessionId = req.params.sessionId
  if (!sessions.has(sessionId)) {
    return res.send(`<h2>Session expired or invalid. Please scan the QR code again on your TV.</h2>`)
  }
  const callbackUrl = `${BASE_URL}/callback?session=${sessionId}`
  const anilistUrl = `https://anilist.co/api/v2/oauth/authorize?client_id=${CLIENT_ID}&response_type=token&redirect_uri=${encodeURIComponent(callbackUrl)}`
  res.send(`<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Dantotsu — Login</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      display: flex; justify-content: center; align-items: center;
      min-height: 100vh; background: #121212; color: #fff;
    }
    .card {
      background: #1e1e1e; border-radius: 16px; padding: 40px;
      text-align: center; max-width: 400px; width: 90%;
      box-shadow: 0 8px 32px rgba(0,0,0,0.4);
    }
    h1 { font-size: 24px; margin-bottom: 8px; }
    p { color: #aaa; margin-bottom: 24px; font-size: 14px; }
    .btn {
      display: inline-block; padding: 14px 32px; border-radius: 50px;
      background: #87CEEB; color: #000; text-decoration: none;
      font-weight: 600; font-size: 16px; transition: opacity .2s;
    }
    .btn:hover { opacity: .85; }
    .steps { text-align: left; margin-top: 24px; }
    .steps li { color: #aaa; margin: 8px 0; font-size: 13px; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Login to Dantotsu</h1>
    <p>Tap the button below to authorize your TV</p>
    <a class="btn" href="${anilistUrl}">Continue with AniList</a>
    <ol class="steps">
      <li>Tap the button above</li>
      <li>Log in to AniList and authorize</li>
      <li>This page will update automatically</li>
    </ol>
  </div>
</body>
</html>`)
})

// OAuth callback (AniList redirects here with token in URL fragment)
app.get('/callback', (req, res) => {
  const sessionId = req.query.session
  if (!sessionId || !sessions.has(sessionId)) {
    return res.send(`<h2>Invalid session. Go back and scan the QR code again.</h2>`)
  }
  res.send(`<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Dantotsu — Login Successful</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      display: flex; justify-content: center; align-items: center;
      min-height: 100vh; background: #121212; color: #fff;
    }
    .card { background: #1e1e1e; border-radius: 16px; padding: 40px; text-align: center; max-width: 400px; width: 90%; }
    h1 { font-size: 24px; margin-bottom: 8px; }
    p { color: #aaa; margin-bottom: 8px; font-size: 14px; }
    .spinner { border: 3px solid #333; border-top: 3px solid #87CEEB; border-radius: 50%; width: 40px; height: 40px; animation: spin 1s linear infinite; margin: 20px auto; }
    @keyframes spin { to { transform: rotate(360deg); } }
  </style>
</head>
<body>
  <div class="card">
    <div class="spinner"></div>
    <h1>Logged in!</h1>
    <p>Your TV should update shortly. You can close this page.</p>
  </div>
  <script>
  (function() {
    const hash = window.location.hash.substring(1)
    const params = new URLSearchParams(hash)
    const token = params.get('access_token')
    if (token && '${sessionId}') {
      fetch('/api/callback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId: '${sessionId}', token })
      })
    }
  })()
  </script>
</body>
</html>`)
})

// Store token (called by JS fragment capture on /callback page)
app.post('/api/callback', (req, res) => {
  const { sessionId, token } = req.body
  if (!sessionId || !token) return res.status(400).json({ error: 'missing fields' })
  const session = sessions.get(sessionId)
  if (!session) return res.status(404).json({ error: 'session not found' })
  session.status = 'authorized'
  session.token = token
  res.json({ status: 'authorized' })
})

// Submit token manually (fallback if auto-capture fails)
app.post('/api/submit', (req, res) => {
  const { sessionId, token } = req.body
  if (!sessionId || !token) return res.status(400).json({ error: 'missing fields' })
  const session = sessions.get(sessionId)
  if (!session) return res.status(404).json({ error: 'session not found' })
  session.status = 'authorized'
  session.token = token
  res.json({ status: 'authorized' })
})

app.listen(PORT, () => console.log(`Relay running on ${BASE_URL}`))
