/* =====================================================================
 *  GOLF-TRAINING SYNC-WORKER v2.1  (Cloudflare Worker)
 *  ---------------------------------------------------------------
 *  WARUM v2.1: trainingsdaten.json ist >1 MB (aktuell ~3 MB). Die GitHub-
 *  Contents-API liefert bei JSON-Accept dann KEIN content-Feld -> v2 lief
 *  in einen Parse-Fehler (502). Außerdem ist server-seitiges Parsen/Mergen
 *  von 3 MB im Workers-FREE-Tier (10 ms CPU) nicht zuverlässig.
 *
 *  ARCHITEKTUR v2.1 — "SHA-Türsteher, Client merged gegen frische Basis":
 *   · GET  /?fresh=1  -> ROHER Dateiinhalt (raw media type, bis 100 MB,
 *                        kein Parse im Worker) + Header X-Repo-Sha.
 *   · POST (NEU-Modus): Body = ROHE Daten-Bytes; Header X-Path, X-Base-Sha,
 *     optional X-Force:1. Worker prüft Key + Whitelist, PUT mit X-Base-Sha.
 *     Ist der SHA veraltet (parallel geschrieben) -> 409 zurück; der CLIENT
 *     holt frisch (?fresh=1 inkl. neuem SHA), merged LOKAL und sendet erneut.
 *     => Lost Updates unmöglich (kein Schreiben ohne aktuellen SHA), und
 *     der Worker parst NIE die 3-MB-Daten (CPU-sicher im Free-Tier).
 *   · POST (ALT-Modus, Fallback für alte Clients): JSON-Body {data, force}
 *     ohne X-Path-Header -> server-seitiger mergeDB-Port wie in v2.
 *     ACHTUNG: bei großen Dateien Free-Tier-CPU-Risiko — alte Clients
 *     zügig aktualisieren.
 *
 *  KONFIG: Wrangler-Secrets GITHUB_TOKEN + WRITE_KEY (oder CFG unten).
 *  mergeDB/_mergeArr = 1:1-Port aus index.html (Äquivalenz-Harness!) —
 *  App-Merge-Änderungen HIER spiegeln (inkl. reference-Regel: gleiche .at
 *  -> Objekte vereinen + acks-UNION; sonst zeitlich neuerer gewinnt).
 * ===================================================================== */

const CFG = {
  REPO: "golftraining-lars/Golftraining",
  BRANCH: "main",
  GITHUB_TOKEN: "",
  WRITE_KEY: "",
  PATHS: ["trainingsdaten.json", "wissen-bilder.json"]
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
  // Referenzstand-Marker: zeitlich NEUERER gewinnt; bei GLEICHEM Zeitpunkt werden
  // die Objekte vereint und die Geräte-Bestätigungen (acks) per UNION zusammengeführt
  // — sonst würden sich parallel ack-ende Geräte gegenseitig aus der Liste werfen.
  { const a=L.reference, b=R.reference; let ref=null;
    if(a&&b){
      if((a.at||"")===(b.at||"")){ ref=Object.assign({},b,a); ref.acks=Object.assign({},(b.acks||{}),(a.acks||{})); }
      else ref=((a.at||"")>=(b.at||""))?a:b;
    } else ref=a||b;
    if(ref) out.reference=ref; else delete out.reference; }
  // Wissen: Artikel nach ID vereinen (reicherer gewinnt), Kategorien vereinigen. Bilder liegen LOKAL (idb) und syncen hier nicht mit.
  { const lw=(L.wiki&&typeof L.wiki==="object")?L.wiki:{}, rw=(R.wiki&&typeof R.wiki==="object")?R.wiki:{};
    const arts=_mergeArr(lw.articles, rw.articles, x=>x.id||((x.title||"")+"|"+(x.cat||"")));
    // bei ID-Konflikt zusätzlich den zeitlich neueren bevorzugen (updated)
    const byId={}; (lw.articles||[]).concat(rw.articles||[]).forEach(a=>{ if(!a||!a.id) return; const c=byId[a.id]; if(!c||((a.updated||"")>(c.updated||""))) byId[a.id]=a; });
    const merged=arts.map(a=> (a.id&&byId[a.id])?byId[a.id]:a );
    const cats=Array.from(new Set([].concat(lw.cats||[], rw.cats||[])));
    out.wiki={cats, articles:merged}; }
  // EINZIGE bewusste Abweichung vom App-Port (kein SEED im Worker):
  out.version = Math.max(L.version || 0, (R && R.version) || 0) || out.version;
  return out;
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
  "Access-Control-Allow-Headers": "Content-Type,X-Write-Key,X-Path,X-Base-Sha,X-Force",
  "Access-Control-Expose-Headers": "X-Repo-Sha"
};
function resp(status, obj, extra) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json",
               "Cache-Control": "no-store", ...CORS, ...(extra || {}) }
  });
}

/* SHA einer Datei über das Root-Listing (billig, KEIN Dateiinhalt) */
async function ghSha(env, path) {
  const r = await fetch(
    `https://api.github.com/repos/${cfg(env,"REPO")}/contents/?ref=${cfg(env,"BRANCH")}`,
    { headers: ghHeaders(env, "application/vnd.github+json"), cf: { cacheTtl: 0 } }
  );
  if (!r.ok) throw new Error("GitHub LIST " + r.status);
  const arr = await r.json();
  const e = (arr || []).find(x => x.path === path || x.name === path);
  return e ? e.sha : null;
}

/* Roh-Inhalt (raw media type: funktioniert auch >1 MB, kein Base64) */
async function ghRaw(env, path) {
  const r = await fetch(
    `https://api.github.com/repos/${cfg(env,"REPO")}/contents/${path}?ref=${cfg(env,"BRANCH")}`,
    { headers: ghHeaders(env, "application/vnd.github.raw"), cf: { cacheTtl: 0 } }
  );
  if (r.status === 404) return null;
  if (!r.ok) throw new Error("GitHub RAW " + r.status);
  return r;   // Response (Body noch nicht gelesen -> streamen möglich)
}

function b64(bytes) {
  let bin = "";
  for (let i = 0; i < bytes.length; i += 8192)
    bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
  return btoa(bin);
}

async function ghPutRaw(env, path, bytes, sha, msg) {
  const payload = { message: msg, content: b64(bytes), branch: cfg(env, "BRANCH") };
  if (sha) payload.sha = sha;
  return fetch(
    `https://api.github.com/repos/${cfg(env,"REPO")}/contents/${path}`,
    { method: "PUT",
      headers: ghHeaders(env, "application/vnd.github+json"),
      body: JSON.stringify(payload) }
  );
}

export default {
  async fetch(req, env) {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    const url = new URL(req.url);

    /* -------- GET ?fresh=1: roh + SHA-Header -------- */
    if (req.method === "GET") {
      if (url.searchParams.get("fresh") !== "1")
        return resp(200, { ok: true, worker: "golftraining-sync v2.1" });
      try {
        const [sha, raw] = await Promise.all([
          ghSha(env, "trainingsdaten.json"),
          ghRaw(env, "trainingsdaten.json")
        ]);
        if (!raw) return resp(404, { ok: false, error: "no data" });
        return new Response(raw.body, { status: 200, headers: {
          "Content-Type": "application/json", "Cache-Control": "no-store",
          "X-Repo-Sha": sha || "", ...CORS } });
      } catch (e) { return resp(502, { ok: false, error: String(e) }); }
    }

    if (req.method !== "POST") return resp(405, { ok: false, error: "method" });
    if (req.headers.get("X-Write-Key") !== cfg(env, "WRITE_KEY") || !cfg(env, "WRITE_KEY"))
      return resp(401, { ok: false, error: "key" });

    /* ================= NEU-Modus: SHA-Türsteher, keine Parse-Last ================= */
    const hPath = req.headers.get("X-Path");
    if (hPath) {
      if (!CFG.PATHS.includes(hPath)) return resp(403, { ok: false, error: "path" });
      const force = req.headers.get("X-Force") === "1";
      const baseSha = req.headers.get("X-Base-Sha") || null;
      const bytes = new Uint8Array(await req.arrayBuffer());
      if (bytes.length < 2) return resp(400, { ok: false, error: "empty" });
      try {
        const sha = force ? await ghSha(env, hPath) : baseSha;
        const r = await ghPutRaw(env, hPath, bytes, sha,
          hPath === "trainingsdaten.json"
            ? "Golf-Trainingsdaten aktualisiert" + (force ? " (autoritativ)" : "")
            : "Update " + hPath);
        if (r.ok) return resp(200, { ok: true, mode: "sha-gate" });
        if (r.status === 409 || r.status === 422)
          return resp(409, { ok: false, error: "stale base — fresh holen, neu mergen" });
        return resp(502, { ok: false, error: "write " + r.status });
      } catch (e) { return resp(502, { ok: false, error: String(e) }); }
    }

    /* ================= ALT-Modus (Fallback): server-seitiger Merge =================
       Für Clients, die noch {data,force} als JSON senden (alte App/Uhr).
       CPU-Risiko bei großen Dateien im Free-Tier — Clients aktualisieren! */
    let body; try { body = await req.json(); } catch (e) { return resp(400, { ok: false, error: "json" }); }
    const path = body.path || "trainingsdaten.json";
    if (!CFG.PATHS.includes(path)) return resp(403, { ok: false, error: "path" });
    if (!body.data || typeof body.data !== "object") return resp(400, { ok: false, error: "data" });

    const encode = o => new TextEncoder().encode(JSON.stringify(o));

    if (path !== "trainingsdaten.json") {
      const sha = await ghSha(env, path).catch(() => null);
      const r = await ghPutRaw(env, path, encode(body.data), sha, "Update " + path);
      return resp(r.ok ? 200 : 502, { ok: r.ok });
    }
    if (!body.data.testDefs || !body.data.tests) return resp(400, { ok: false, error: "invalid data" });

    for (let attempt = 0; attempt < 4; attempt++) {
      let sha = null, cur = null;
      try {
        sha = await ghSha(env, "trainingsdaten.json");
        const raw = await ghRaw(env, "trainingsdaten.json");
        cur = raw ? await raw.json() : null;
      } catch (e) { return resp(502, { ok: false, error: "read: " + String(e) }); }

      const out = (body.force === true || !cur) ? body.data : mergeDB(body.data, cur);
      const r = await ghPutRaw(env, "trainingsdaten.json", encode(out), sha,
        "Golf-Trainingsdaten aktualisiert" + (body.force ? " (autoritativ)" : ""));
      if (r.ok) return resp(200, { ok: true, mode: "server-merge", attempt });
      if (r.status !== 409 && r.status !== 422) return resp(502, { ok: false, error: "write " + r.status });
    }
    return resp(503, { ok: false, error: "conflict retries exhausted" });
  }
};
