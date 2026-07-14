// Cloudflare Worker — Anilist QR Login Relay
// Deploy: npx wrangler deploy anilist-relay.js
// Requires KV namespace named ANILIST_TOKENS bound in dashboard or wrangler.toml:
//   [[kv_namespaces]]
//   binding = "ANILIST_TOKENS"
//   id = "..."  (create with: npx wrangler kv:namespace create ANILIST_TOKENS)

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
    };

    if (request.method === 'OPTIONS')
      return new Response(null, { headers: corsHeaders });

    // POST /init  →  { sessionId }
    if (path === '/init' && request.method === 'POST') {
      const sessionId = crypto.randomUUID();
      await env.ANILIST_TOKENS.put(
        sessionId,
        JSON.stringify({ status: 'pending', token: null }),
        { expirationTtl: 300 },
      );
      return new Response(JSON.stringify({ sessionId }), {
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    }

    // GET /qr?session=…  →  HTML that redirects to Anilist OAuth
    if (path === '/qr') {
      const sessionId = url.searchParams.get('session');
      if (!sessionId)
        return new Response('Missing session', { status: 400 });
      const redirectBack = url.origin + '/callback?session=' + encodeURIComponent(sessionId);
      const authUrl = 'https://anilist.co/api/v2/oauth/authorize' +
        '?client_id=45857' +
        '&response_type=token' +
        '&redirect_url=' + encodeURIComponent(redirectBack);
      const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Redirecting to Anilist...</title>
</head>
<body>
<p>Redirecting to Anilist for authorization...</p>
<script>window.location.href=${JSON.stringify(authUrl)};</script>
</body>
</html>`;
      return new Response(html, {
        headers: { 'Content-Type': 'text/html; charset=utf-8', ...corsHeaders },
      });
    }

    // GET /callback?session=…  →  HTML that captures URL fragment
    if (path === '/callback') {
      const sessionId = url.searchParams.get('session') || '';
      const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Anilist Login</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#1a1a2e;color:#fff;padding:1rem}
.container{text-align:center;max-width:400px}
h2{margin-bottom:1rem}
.spinner{border:4px solid rgba(255,255,255,.1);border-top-color:#4fc3f7;border-radius:50%;width:40px;height:40px;animation:s 1s linear infinite;margin:1.5rem auto}
@keyframes s{to{transform:rotate(360deg)}}
.ok{color:#66bb6a;font-size:1.5rem}
.fail{color:#ef5350}
p{margin:.75rem 0;line-height:1.5}
</style>
</head>
<body>
<div class="container">
<h2>Anilist Authorization</h2>
<div id="s"><p>Processing&hellip;</p><div class="spinner"></div></div>
</div>
<script>
(async()=>{
  const p=new URLSearchParams(location.hash.slice(1)),t=p.get('access_token');
  const d=document.getElementById('s');
  if(t){
    try{
      const r=await fetch('/token?session=${sessionId}&token='+encodeURIComponent(t),{method:'POST'});
      d.innerHTML=r.ok
        ?'<p class="ok">&#10003; Login successful!</p><p>You can close this page.</p>'
        :'<p class="fail">Failed to save token. Try again.</p>'
    }catch(e){d.innerHTML='<p class="fail">Network error. Try again.</p>'}
  }else{d.innerHTML='<p class="fail">No token found in response.</p>'}
})()
</script>
</body>
</html>`;
      return new Response(html, {
        headers: { 'Content-Type': 'text/html; charset=utf-8', ...corsHeaders },
      });
    }

    // POST /token?session=…&token=…  →  store token
    if (path === '/token' && request.method === 'POST') {
      const sessionId = url.searchParams.get('session');
      const token = url.searchParams.get('token');
      if (sessionId && token) {
        await env.ANILIST_TOKENS.put(
          sessionId,
          JSON.stringify({ status: 'done', token }),
          { expirationTtl: 120 },
        );
        return new Response(JSON.stringify({ ok: true }), {
          headers: { 'Content-Type': 'application/json', ...corsHeaders },
        });
      }
      return new Response(JSON.stringify({ error: 'missing params' }), {
        status: 400,
        headers: corsHeaders,
      });
    }

    // GET /poll?session=…  →  { status, token? }
    if (path === '/poll') {
      const sessionId = url.searchParams.get('session');
      if (!sessionId)
        return new Response(JSON.stringify({ error: 'missing session' }), {
          status: 400,
          headers: corsHeaders,
        });
      const data = await env.ANILIST_TOKENS.get(sessionId);
      if (!data)
        return new Response(JSON.stringify({ status: 'expired' }), {
          headers: { 'Content-Type': 'application/json', ...corsHeaders },
        });
      return new Response(data, {
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    }

    return new Response('Not found', { status: 404 });
  },
};
