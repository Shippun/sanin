// Cloudflare Worker — Anilist QR Login Relay
// Deploy: npx wrangler deploy anilist-relay.js
// Requires KV namespace named ANILIST_TOKENS bound in dashboard or wrangler.toml:
//   [[kv_namespaces]]
//   binding = "ANILIST_TOKENS"
//   id = "..."

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

function generateCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) code += chars[Math.floor(Math.random() * chars.length)];
  return code;
}

function htmlPage(title, body) {
  return `<!DOCTYPE html>
<html lang="en">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title}</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#1a1a2e;color:#fff;padding:1rem;flex-direction:column}
.container{text-align:center;max-width:420px;width:100%}
h2{margin-bottom:.5rem;font-size:1.5rem}
p{margin:.75rem 0;line-height:1.5;color:#ccc}
input,button{width:100%;padding:14px;border-radius:8px;border:none;font-size:1rem;margin:.3rem 0}
input{background:#2a2a4a;color:#fff;text-align:center;letter-spacing:4px;font-size:1.4rem;text-transform:uppercase}
button{background:#4fc3f7;color:#1a1a2e;font-weight:bold;cursor:pointer}
button:hover{background:#39b0e4}
button:disabled{opacity:.5}
.spinner{border:4px solid rgba(255,255,255,.1);border-top-color:#4fc3f7;border-radius:50%;width:40px;height:40px;animation:s 1s linear infinite;margin:1.5rem auto}
@keyframes s{to{transform:rotate(360deg)}}
.ok{color:#66bb6a;font-size:1.5rem}
.fail{color:#ef5350}
</style>
</head>
<body><div class="container">${body}</div></body>
</html>`;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'OPTIONS')
      return new Response(null, { headers: CORS });

    // POST /init  →  { sessionId, code }
    if (path === '/init' && request.method === 'POST') {
      const sessionId = crypto.randomUUID();
      const code = generateCode();
      await env.ANILIST_TOKENS.put(
        'session:' + sessionId,
        JSON.stringify({ status: 'pending', token: null }),
        { expirationTtl: 300 },
      );
      await env.ANILIST_TOKENS.put(
        'code:' + code,
        JSON.stringify({ sessionId }),
        { expirationTtl: 300 },
      );
      return new Response(JSON.stringify({ sessionId, code }), {
        headers: { 'Content-Type': 'application/json', ...CORS },
      });
    }

    // GET /  →  landing page with code input
    if (path === '/') {
      return new Response(htmlPage('Login', `
        <h2>AniList Login</h2>
        <p>Enter the code shown on your TV.</p>
        <input id="c" type="text" maxlength="6" placeholder="TV CODE" autocomplete="off">
        <button id="b" onclick="l()">Continue</button>
        <p id="e" class="fail" style="display:none"></p>
        <script>
        document.getElementById('c').addEventListener('keydown',e=>{if(e.key==='Enter')l()});
        async function l(){
          const c=document.getElementById('c'),b=document.getElementById('b'),e=document.getElementById('e');
          b.disabled=true;e.style.display='none';
          const r=await fetch('/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:c.value})});
          if(r.redirected){window.location.href=r.url}
          else{
            const j=await r.json();
            e.textContent=j.error||'Invalid code';
            e.style.display='block';b.disabled=false;
          }
        }
        </script>`), {
        headers: { 'Content-Type': 'text/html; charset=utf-8', ...CORS },
      });
    }

    // POST /login  →  looks up code, redirects to Anilist OAuth
    if (path === '/login' && request.method === 'POST') {
      let body;
      try { body = await request.json(); } catch { body = {}; }
      const code = (body.code || '').toUpperCase().trim();
      if (!code) return new Response(JSON.stringify({ error: 'Missing code' }), { status: 400, headers: CORS });

      const entry = await env.ANILIST_TOKENS.get('code:' + code);
      if (!entry) return new Response(JSON.stringify({ error: 'Invalid code' }), { status: 400, headers: CORS });

      const { sessionId } = JSON.parse(entry);
      const redirectBack = url.origin + '/callback?session=' + encodeURIComponent(sessionId);
      const authUrl = 'https://anilist.co/api/v2/oauth/authorize' +
        '?client_id=45857' +
        '&response_type=token' +
        '&redirect_uri=' + encodeURIComponent(redirectBack);

      return new Response(null, {
        status: 302,
        headers: { 'Location': authUrl, ...CORS },
      });
    }

    // GET /callback?session=…  →  HTML that captures URL fragment
    if (path === '/callback') {
      const sessionId = url.searchParams.get('session') || '';
      return new Response(htmlPage('Anilist Login', `
        <h2>Anilist Authorization</h2>
        <div id="s"><p>Processing&hellip;</p><div class="spinner"></div></div>
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
        </script>`), {
        headers: { 'Content-Type': 'text/html; charset=utf-8', ...CORS },
      });
    }

    // POST /token?session=…&token=…  →  store token
    if (path === '/token' && request.method === 'POST') {
      const sessionId = url.searchParams.get('session');
      const token = url.searchParams.get('token');
      if (sessionId && token) {
        await env.ANILIST_TOKENS.put(
          'session:' + sessionId,
          JSON.stringify({ status: 'done', token }),
          { expirationTtl: 120 },
        );
        return new Response(JSON.stringify({ ok: true }), {
          headers: { 'Content-Type': 'application/json', ...CORS },
        });
      }
      return new Response(JSON.stringify({ error: 'missing params' }), {
        status: 400, headers: CORS,
      });
    }

    // GET /poll?session=…  →  { status, token? }
    if (path === '/poll') {
      const sessionId = url.searchParams.get('session');
      if (!sessionId)
        return new Response(JSON.stringify({ error: 'missing session' }), {
          status: 400, headers: CORS,
        });
      const data = await env.ANILIST_TOKENS.get('session:' + sessionId);
      if (!data)
        return new Response(JSON.stringify({ status: 'expired' }), {
          headers: { 'Content-Type': 'application/json', ...CORS },
        });
      return new Response(data, {
        headers: { 'Content-Type': 'application/json', ...CORS },
      });
    }

    return new Response('Not found', { status: 404 });
  },
};
