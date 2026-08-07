/* =====================================================================
 *  GOLF-TRAINING SYNC-WORKER v2  (Cloudflare Worker)
 *  ---------------------------------------------------------------
 *  WARUM v2: Vorher haben die Clients (Handy-PWA, Uhr) vor dem Schreiben
 *  gegen die GitHub-PAGES-Kopie der Datei gemerged. Das Pages-CDN hängt
 *  dem Repo bis zu ~10 Minuten hinterher -> wer als Zweites schrieb,
 *  mergte gegen eine veraltete Basis und ÜBERSCHRIEB die Änderungen des
 *  anderen Geräts (klassisches Lost Update).
 *
 *  v2-PRINZIP: Der Merge passiert HIER, gegen den FRISCHEN Repo-Stand
 *  (GitHub Contents API, kein CDN), abgesichert mit dem Datei-SHA als
 *  optimistische Schreibsperre: PUT nur mit aktuellem SHA; schreibt
 *  parallel jemand anderes, antwortet GitHub 409 -> neu lesen, neu
 *  mergen, neu versuchen (max. 5x). Damit kann KEIN Client mehr Daten
 *  eines anderen Geräts überschreiben - egal wie alt seine Basis ist.
 *
 *  ENDPUNKTE:
 *    GET  /?fresh=1      -> frischer trainingsdaten.json-Stand (no-store)
 *    POST /              -> Body {data, force?} oder {path, data}
 *         · trainingsdaten.json: SERVER-MERGE + SHA-Lock (s.o.)
 *           force:true = autoritativ, KEIN Merge (bewusstes Zurücksetzen)
 *         · andere Whitelist-Pfade (wissen-bilder.json): Write-Through
 *         Header: X-Write-Key muss WRITE_KEY entsprechen
 *
 *  KONFIGURATION: bevorzugt als Wrangler-Secrets/Vars (env.*), sonst
 *  die Konstanten hier unten füllen.
 *    wrangler secret put GITHUB_TOKEN
 *    wrangler secret put WRITE_KEY
 *
 *  WICHTIG FÜR KI-ASSISTENTEN & SPÄTERE ÄNDERUNGEN:
 *  mergeDB/_mergeArr hier sind ein 1:1-PORT aus index.html. Wer die
 *  Merge-Regeln in der App ändert, MUSS sie hier spiegeln (und den
 *  Äquivalenz-Harness laufen lassen). Regeln u. a.: Arrays per id/ts
 *  (neuerer gewinnt), _draftRound derselben Runde LOCH-GENAU vereinen,
 *  reference (Referenzstand) zeitlich-neuerer-gewinnt, Tombstone
 *  ui.draftDiscardedTs lässt verworfene Entwürfe nicht auferstehen.
 * ===================================================================== */

const CFG = {
  REPO: "golftraining-lars/Golftraining",   // owner/repo
  BRANCH: "main",
  GITHUB_TOKEN: "",                          // leer lassen, wenn per Secret gesetzt
  WRITE_KEY: "",                             // leer lassen, wenn per Secret gesetzt
  PATHS: ["trainingsdaten.json", "wissen-bilder.json"]  // Whitelist
};

/* ---------- 1:1-Port aus index.html (NICHT eigenständig ändern) ---------- */
function _mergeArr(a,b,keyFn){
  const m=new Map();
  (Array.isArray(a)?a:[]).forEach(x=>{ if(x!=null) m.set(keyFn(x), x); });
  (Array.isArray(b)?b:[]).forEach(x=>{ if(x==null) return; const k=keyFn(x), cur=m.get(k);
    if(cur===undefined) m.set(k,x);
    else if(JSON.stringify(x).length > JSON.stringify(cur).length) m.set(k,x); // reicheren/vollständigeren Eintrag behalten
  });
  return Array.from(m.values());
}

function mergeDB(localDB, repoDB){
  const L=localDB||{}, R=repoDB||{}, j=x=>JSON.stringify(x);
  const out=Object.assign({}, R, L); // Basis: lokale Skalar-/Objektfelder (Profil, Phasen, ui) gewinnen
  out.rounds         = _mergeArr(L.rounds, R.rounds, x=>x.id||(x.date+"|"+x.course));
  out.competitions   = _mergeArr(L.competitions, R.competitions, x=>x.id||j(x));
  out.tests          = _mergeArr(L.tests, R.tests, x=>x.id||((x.defKey||"")+"|"+(x.date||"")+"|"+j(x.inputs||"")));
  out.fitnessSessions= _mergeArr(L.fitnessSessions, R.fitnessSessions, x=>x.id||((x.date||"")+"|"+j(x)));
  out.lmSessions     = _mergeArr(L.lmSessions, R.lmSessions, x=>x.id||((x.date||"")+"|"+j(x)));
  out.playedCourses  = _mergeArr(L.playedCourses, R.playedCourses, x=>x.name||j(x));
  out.bucketList     = _mergeArr(L.bucketList, R.bucketList, x=>x.name||j(x));
  out.courses        = _mergeArr(L.courses, R.courses, x=>x.name||j(x));
  out.testDefs       = _mergeArr(L.testDefs, R.testDefs, x=>x.key||x.id||x.label||j(x));
  out.clubDistances  = _mergeArr(L.clubDistances, R.clubDistances, x=>x.club||j(x));
  out.notes          = _mergeArr(L.notes, R.notes, x=>x.id||j(x));
  out.notesTrash     = _mergeArr(L.notesTrash, R.notesTrash, x=>x.id||j(x));
  // Tombstones respektieren: gelöschte Notizen nicht wieder auferstehen lassen
  const trashIds=new Set((out.notesTrash||[]).map(n=>n.id).filter(Boolean));
  out.notes=(out.notes||[]).filter(n=>!(n.id && trashIds.has(n.id)));
  // pins: je Schlüssel den neueren (nach date) behalten
  out.pins={}; const lp=L.pins||{}, rp=R.pins||{};
  Object.keys(Object.assign({},rp,lp)).forEach(k=>{ const a=lp[k], b=rp[k];
    out.pins[k] = (a&&b) ? (((a.date||"")>=(b.date||""))?a:b) : (a||b); });
  // _draftRound: den zeitlich neueren Entwurf behalten – ABER Tombstone respektieren
  const _tomb=(function(){ const a=(L.ui&&L.ui.draftDiscardedTs)||"", b=(R.ui&&R.ui.draftDiscardedTs)||""; return a>=b?a:b; })();
  delete out._draftRound;
  { const a=L._draftRound, b=R._draftRound; let dr=null;
    const key=d=>(d&&d.round)?((d.round.date||"")+"|"+(d.round.course||"")+"|"+(d.round.side||"")):null;
    if(a&&b&&key(a)&&key(a)===key(b)){
      // GLEICHE Runde auf zwei Geräten (z. B. Uhr + Handy): Löcher feldweise vereinen.
      // Basis = älterer Entwurf, darüber alle GESETZTEN Felder des neueren (null löscht nichts).
      const nw=((a.ts||"")>=(b.ts||""))?a:b, old=(nw===a)?b:a;
      const holes={}; ((old.round.holes)||[]).forEach(h=>{ holes[h.hole]=Object.assign({},h); });
      ((nw.round.holes)||[]).forEach(h=>{ const t=holes[h.hole]||(holes[h.hole]={hole:h.hole});
        Object.keys(h).forEach(k=>{ if(h[k]!=null) t[k]=h[k]; }); });
      const r=Object.assign({}, old.round, nw.round);
      r.holes=Object.values(holes).sort((x,y)=>x.hole-y.hole);
      dr={round:r, ts:nw.ts};
    } else dr=(a&&b)?(((a.ts||"")>=(b.ts||""))?a:b):(a||b);
    if(dr && _tomb && (dr.ts||"")<=_tomb) dr=null;   // verworfenen (oder älteren) Entwurf nicht wieder auferstehen lassen
    if(dr) out._draftRound=dr; }
  // ui zusammenführen (lokal gewinnt bei Konflikt), Verworfen-Marke = spätere von beiden
  out.ui=Object.assign({}, R.ui||{}, L.ui||{}); if(_tomb) out.ui.draftDiscardedTs=_tomb;
  // Referenzstand-Marker: der zeitlich NEUERE gewinnt (geräteübergreifend eindeutig)
  { const a=L.reference, b=R.reference;
    const ref=(a&&b)?(((a.at||"")>=(b.at||""))?a:b):(a||b);
    if(ref) out.reference=ref; else delete out.reference; }
  // Wissen: Artikel nach ID vereinen (reicherer gewinnt), Kategorien vereinigen. Bilder liegen LOKAL (idb) und syncen hier nicht mit.
  { const lw=(L.wiki&&typeof L.wiki==="object")?L.wiki:{}, rw=(R.wiki&&typeof R.wiki==="object")?R.wiki:{};
    const arts=_mergeArr(lw.articles, rw.articles, x=>x.id||((x.title||"")+"|"+(x.cat||"")));
    // bei ID-Konflikt zusätzlich den zeitlich neueren bevorzugen (updated)
    const byId={}; (lw.articles||[]).concat(rw.articles||[]).forEach(a=>{ if(!a||!a.id) return; const c=byId[a.id]; if(!c||((a.updated||"")>(c.updated||""))) byId[a.id]=a; });
    const merged=arts.map(a=> (a.id&&byId[a.id])?byId[a.id]:a );
    const cats=Array.from(new Set([].concat(lw.cats||[], rw.cats||[])));
    out.wiki={cats, articles:merged}; }
  // EINZIGE bewusste Abweichung vom App-Port: die App pinnt hier auf ihre
  // SEED.version (Schema der laufenden App). Der Worker kennt kein SEED und
  // darf das Schema nicht absenken -> hoehere der beiden Versionen gewinnt.
  out.version = Math.max(L.version || 0, (R && R.version) || 0) || out.version;
  return out;
}

/* ---------- GitHub Contents API ---------- */
async function ghGet(env, path) {
  const r = await fetch(
    `https://api.github.com/repos/${cfg(env,"REPO")}/contents/${path}?ref=${cfg(env,"BRANCH")}`,
    { headers: ghHeaders(env, "application/vnd.github+json"),
      cf: { cacheTtl: 0 } }
  );
  if (r.status === 404) return { sha: null, json: null };
  if (!r.ok) throw new Error("GitHub GET " + r.status);
  const j = await r.json();
  const txt = atob((j.content || "").replace(/\n/g, ""));
  const bytes = Uint8Array.from(txt, ch => ch.charCodeAt(0));
  return { sha: j.sha, json: JSON.parse(new TextDecoder("utf-8").decode(bytes)) };
}

async function ghPut(env, path, obj, sha, msg) {
  const body = JSON.stringify(obj, null, 0);
  const bytes = new TextEncoder().encode(body);
  let bin = ""; for (let i = 0; i < bytes.length; i += 8192)
    bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
  const payload = { message: msg, content: btoa(bin), branch: cfg(env,"BRANCH") };
  if (sha) payload.sha = sha;
  return fetch(
    `https://api.github.com/repos/${cfg(env,"REPO")}/contents/${path}`,
    { method: "PUT",
      headers: ghHeaders(env, "application/vnd.github+json"),
      body: JSON.stringify(payload) }
  );
}

function cfg(env, k) { return (env && env[k]) || CFG[k]; }
function ghHeaders(env, accept) {
  return { "Authorization": "Bearer " + cfg(env, "GITHUB_TOKEN"),
           "Accept": accept,
           "User-Agent": "golftraining-sync-worker",
           "X-GitHub-Api-Version": "2022-11-28" };
}
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type,X-Write-Key"
};
function resp(status, obj) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json",
               "Cache-Control": "no-store", ...CORS }
  });
}

export default {
  async fetch(req, env) {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });

    const url = new URL(req.url);

    /* -------- GET ?fresh=1: frischer Stand, kein CDN -------- */
    if (req.method === "GET") {
      if (url.searchParams.get("fresh") !== "1")
        return resp(200, { ok: true, worker: "golftraining-sync v2" });
      try {
        const { json } = await ghGet(env, "trainingsdaten.json");
        if (!json) return resp(404, { ok: false, error: "no data" });
        return resp(200, json);
      } catch (e) { return resp(502, { ok: false, error: String(e) }); }
    }

    if (req.method !== "POST") return resp(405, { ok: false, error: "method" });
    if (req.headers.get("X-Write-Key") !== cfg(env, "WRITE_KEY") || !cfg(env, "WRITE_KEY"))
      return resp(401, { ok: false, error: "key" });

    let body; try { body = await req.json(); } catch (e) { return resp(400, { ok: false, error: "json" }); }
    const path = body.path || "trainingsdaten.json";
    if (!CFG.PATHS.includes(path)) return resp(403, { ok: false, error: "path" });
    if (!body.data || typeof body.data !== "object") return resp(400, { ok: false, error: "data" });

    /* -------- Nebendateien: Write-Through wie bisher -------- */
    if (path !== "trainingsdaten.json") {
      const cur = await ghGet(env, path).catch(() => ({ sha: null }));
      const r = await ghPut(env, path, body.data, cur.sha, "Update " + path);
      return resp(r.ok ? 200 : 502, { ok: r.ok, merged: false });
    }

    /* -------- Trainingsdaten: SERVER-MERGE + SHA-Lock -------- */
    const incoming = body.data;
    if (!incoming.testDefs || !incoming.tests)
      return resp(400, { ok: false, error: "invalid data" });

    for (let attempt = 0; attempt < 5; attempt++) {
      let cur;
      try { cur = await ghGet(env, "trainingsdaten.json"); }
      catch (e) { return resp(502, { ok: false, error: "read: " + String(e) }); }

      // force = autoritativ (bewusstes Zurücksetzen/Referenz): KEIN Merge.
      // Sonst: Einsender = "lokal" (gewinnt bei Gleichstand), Repo = "remote".
      const out = (body.force === true || !cur.json)
        ? incoming
        : mergeDB(incoming, cur.json);

      const r = await ghPut(env, "trainingsdaten.json", out, cur.sha,
        "Golf-Trainingsdaten aktualisiert" + (body.force ? " (autoritativ)" : ""));

      if (r.ok) return resp(200, { ok: true, merged: !body.force && !!cur.json, attempt });
      if (r.status !== 409 && r.status !== 422)
        return resp(502, { ok: false, error: "write " + r.status });
      // SHA-Konflikt: jemand hat parallel geschrieben -> frisch neu mergen
    }
    return resp(503, { ok: false, error: "conflict retries exhausted" });
  }
};
