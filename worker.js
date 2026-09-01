/* =====================================================================
 *  GOLF-TRAINING SYNC-WORKER v2.12  (Cloudflare Worker)
 *  ---------------------------------------------------------------
 *  NEU IN v2.12 (2026-09-01): `?ics=<Adresse>` reicht einen oeffentlich
 *  freigegebenen Google-Kalender durch — die App kann ihn nicht direkt lesen,
 *  weil Google die dafuer noetige Freigabe (CORS) nicht setzt. AUSSCHLIESSLICH
 *  Google-Adressen, nur https, mit Groessengrenze: Ein Worker, der beliebige
 *  Adressen abruft, ist ein offener Weiterleiter — der wird gefunden und
 *  missbraucht. Die Beschraenkung ist keine Vorsicht, sondern Pflicht.
 *
 *  NEU IN v2.11 (2026-08-24): `watchlog.json` in der Whitelist — das
 *  Fehlerprotokoll der Uhr, auch ohne laufende Runde.
 *
 *  NEU IN v2.10 (2026-08-24): Die PUT-Antwort enthaelt die NEUE Kennung
 *  (`sha` im Rumpf und im Kopf `X-Repo-Sha`). Ohne sie kannte der Client nach
 *  jedem erfolgreichen Schreibvorgang nur noch die ALTE Kennung und lief beim
 *  naechsten Push in einen 409 — jedes Mal. Auf der Uhr erschoepfte das die
 *  Wiederholungsschleife, und der Abgleich setzte ganz aus.
 *
 *  NEU IN v2.9 (2026-08-24): DER ALT-MODUS IST ENTFERNT.
 *
 *  WARUM — und das ist der eigentliche Punkt dieser Fassung:
 *  Bis v2.8 gab es ZWEI Wege zu schreiben. Der NEU-Modus (SHA-Tuersteher,
 *  Client merged lokal) und der ALT-Modus, in dem der Worker eine EIGENE
 *  Kopie von `mergeDB` fuhr. Die Doku der App verlangte ausdruecklich, beide
 *  Fassungen aequivalent zu halten — und genau das ist ueber Jahre nicht
 *  geschehen. Am 24.08.2026 verglichen, fehlten dem Worker:
 *
 *    · GRABSTEINE (`_mergeTomb`/`_tombFor`) — geloeschte Runden, Turniere,
 *      Tests und Plaetze waeren wieder auferstanden.
 *    · ZEITSTEMPEL in `_mergeArr` — statt „juengerer gewinnt" galt hier
 *      „laengeres JSON gewinnt". Eine BEARBEITETE Runde haette gegen ihre
 *      aeltere Fassung verloren. Genau der Fehler, den die App in v2.41
 *      behoben hat.
 *    · `swingAnalyses`, `seasonGoals`, `tournaments`, `gear`, `tasks`,
 *      `periodization`, `equipment`, `fitPlan`, `settings`, `lmTargets` —
 *      alle ohne Regel, also „lokal gewinnt vollstaendig".
 *
 *  Aufgefallen ist davon nichts, weil der ALT-Modus im Normalbetrieb NIE
 *  laeuft: Die App spricht seit v2.1 den NEU-Modus. Der tote Code waere erst
 *  in einer Stoerung aufgewacht — im schlechtesten denkbaren Moment.
 *
 *  ZWEI FASSUNGEN DERSELBEN LOGIK SYNCHRON ZU HALTEN IST TEURER, ALS DEN
 *  ZWEITEN WEG ZU SCHLIESSEN. Deshalb ist er geschlossen. Ein Client, der ihn
 *  braucht, ist so alt, dass er aktualisiert gehoert — er bekommt jetzt eine
 *  klare Ansage (426) statt eines stillen Merges mit veralteten Regeln.
 *
 *  DAMIT ENTFAELLT auch die Pflicht, App-Aenderungen hier zu spiegeln. Es gibt
 *  nur noch EINE Merge-Logik, und die steht in index.html.
 *
 *  ARCHITEKTUR — "SHA-Tuersteher, Client merged gegen frische Basis":
 *   · GET  /?sha=1[&path=…]   -> nur die Dateikennung (wenige hundert Byte)
 *   · GET  /?fresh=1[&path=…] -> ROHER Dateiinhalt + Header X-Repo-Sha
 *   · POST mit X-Path, X-Base-Sha [, X-Force:1], Body = rohe Bytes
 *       Worker prueft Key + Whitelist und schreibt mit X-Base-Sha.
 *       Veralteter SHA -> 409; der CLIENT holt frisch, merged LOKAL, sendet
 *       erneut. Lost Updates sind damit unmoeglich, und der Worker parst NIE
 *       die 3 MB (CPU-sicher im Free-Tier).
 *
 *  KONFIG: Wrangler-Secrets GITHUB_TOKEN + WRITE_KEY (oder CFG unten).
 * ===================================================================== */

const CFG = {
  REPO: "golftraining-lars/Golftraining",
  BRANCH: "main",
  GITHUB_TOKEN: "",
  WRITE_KEY: "",
  /* `draft.json`  — laufender Rundenentwurf, wenige kB statt 3 MB je Eingabe.
     `watch.json`  — schlanke Fassung fuer die Uhr (Plaetze, Schlaeger, HCP).
     `probe.json`  — Kopplungstest App <-> Uhr, unter 1 kB, nie Spieldaten.
     Keine dieser Dateien muss angelegt werden: Das erste PUT ohne SHA erzeugt
     sie. */
  /* `watchlog.json` (v2.11) — das Fehlerprotokoll der Uhr, auch OHNE laufende
     Runde. Der Weg ueber den Rundenentwurf kostet nichts, gibt es aber nur
     waehrend einer Runde; Fehler beim Start oder beim Platzladen erreichten das
     Handy damit nie. Wenige kB, ein Schreiber je Geraet, keine Spieldaten. */
  PATHS: ["trainingsdaten.json", "wissen-bilder.json", "draft.json", "watch.json",
          "probe.json", "watchlog.json"]
};

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

/* SHA einer Datei ueber das Root-Listing (billig, KEIN Dateiinhalt) */
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
  return r;   // Response (Body noch nicht gelesen -> streamen moeglich)
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

    /* ==========================================================================
       GET ?ics=<Adresse>: EINEN GOOGLE-KALENDER DURCHREICHEN (v2.12)
       --------------------------------------------------------------------------
       GEWUENSCHT am 01.09.2026: Die App soll einen oeffentlich freigegebenen
       Google-Kalender anzeigen. Das geht nicht direkt aus dem Browser — Google
       liefert die ICS-Adresse OHNE die Freigabe, die eine fremde Seite zum
       Lesen braucht (CORS). Der Worker holt sie stellvertretend.
       KEIN OFFENER VERMITTLER: Erlaubt sind AUSSCHLIESSLICH Adressen von
       Google Kalender. Ein Worker, der beliebige Adressen abruft, ist ein
       offener Weiterleiter — der wird gefunden und missbraucht, und zwar
       zuverlaessig. Die Beschraenkung ist keine Vorsicht, sondern Pflicht.
       NUR LESEN, NUR ICS: keine Kopfzeilen des Aufrufers weiterreichen, kein
       POST, und die Antwort wird als Text zurueckgegeben. Es gibt nichts, was
       ein Aufrufer hier auesser einem Kalender bekommen koennte.
       GROESSENGRENZE: Ein Jahreskalender sind wenige Zehntausend Zeichen. Ein
       Megabyte ist so weit darueber, dass alles Groessere ein Fehler oder ein
       Missbrauchsversuch ist.
       ========================================================================== */
    if (req.method === "GET" && url.searchParams.get("ics")) {
      const ziel = url.searchParams.get("ics");
      let u;
      try { u = new URL(ziel); } catch (_) { return resp(400, { ok: false, error: "url" }); }
      const erlaubt = (u.protocol === "https:") &&
        (u.hostname === "calendar.google.com" || u.hostname === "www.google.com");
      if (!erlaubt) return resp(403, { ok: false, error: "nur Google Kalender" });
      try {
        const r = await fetch(u.toString(), { headers: { "User-Agent": "golftraining-sync" } });
        if (!r.ok) return resp(502, { ok: false, error: "HTTP " + r.status });
        const t = await r.text();
        if (t.length > 1000000) return resp(413, { ok: false, error: "zu gross" });
        return new Response(t, {
          status: 200,
          headers: Object.assign({}, CORS, { "Content-Type": "text/calendar; charset=utf-8" })
        });
      } catch (e) { return resp(502, { ok: false, error: String(e) }); }
    }

    /* -------- GET ?sha=1: NUR die Dateikennung --------
       Waehrend einer Runde fragt die App im Minutentakt, ob sich im Repo etwas
       geaendert hat. Vorher zog sie dafuer die ganze Datei (~3 MB) und las die
       Kennung aus dem Antwort-Header — auf vier Stunden rund 700 MB. Hier
       kostet dieselbe Auskunft ein paar hundert Byte, weil `ghSha` nur das
       Wurzel-Listing abruft und den Dateiinhalt NICHT anfasst. */
    if (req.method === "GET" && url.searchParams.get("sha") === "1") {
      const p = url.searchParams.get("path") || "trainingsdaten.json";
      if (!CFG.PATHS.includes(p)) return resp(403, { ok: false, error: "path" });
      try { return resp(200, { ok: true, sha: (await ghSha(env, p)) || "" }); }
      catch (e) { return resp(502, { ok: false, error: String(e) }); }
    }

    /* -------- GET ?fresh=1: roh + SHA-Header -------- */
    if (req.method === "GET") {
      if (url.searchParams.get("fresh") !== "1")
        return resp(200, { ok: true, worker: "golftraining-sync v2.12" });
      const p = url.searchParams.get("path") || "trainingsdaten.json";
      if (!CFG.PATHS.includes(p)) return resp(403, { ok: false, error: "path" });
      try {
        const [sha, raw] = await Promise.all([ ghSha(env, p), ghRaw(env, p) ]);
        if (!raw) return resp(404, { ok: false, error: "no data" });
        return new Response(raw.body, { status: 200, headers: {
          "Content-Type": "application/json", "Cache-Control": "no-store",
          "X-Repo-Sha": sha || "", ...CORS } });
      } catch (e) { return resp(502, { ok: false, error: String(e) }); }
    }

    if (req.method !== "POST") return resp(405, { ok: false, error: "method" });
    if (req.headers.get("X-Write-Key") !== cfg(env, "WRITE_KEY") || !cfg(env, "WRITE_KEY"))
      return resp(401, { ok: false, error: "key" });

    /* ================= EIN EINZIGER SCHREIBWEG =================
       Fehlt `X-Path`, spricht der Client den entfernten ALT-Modus. Frueher lief
       er dann in einen serverseitigen Merge mit VERALTETEN Regeln — ohne
       Grabsteine, ohne Zeitstempel. Ein stiller Datenverlust ist die schlechteste
       aller Antworten; deshalb gibt es hier eine klare Ansage.
       426 „Upgrade Required" ist der passende Status: Der Wunsch ist verstanden,
       die Fassung des Clients ist das Problem. */
    const hPath = req.headers.get("X-Path");
    if (!hPath) {
      return resp(426, { ok: false, error: "client-too-old",
        hinweis: "Dieser Worker schreibt nur noch im SHA-Modus (Header X-Path + X-Base-Sha). "
               + "Der serverseitige Merge wurde in v2.9 entfernt, weil seine Regeln von denen der "
               + "App abwichen (keine Grabsteine, keine Zeitstempel) und dabei Daten verloren "
               + "gegangen waeren. Bitte App bzw. Uhr aktualisieren." });
    }
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
      if (r.ok) {
        /* ==================================================================
           DIE NEUE KENNUNG ZURUECKGEBEN (v2.10, 2026-08-24)
           --------------------------------------------------------------------
           GEMELDET: „Nach einigen Eingaben bricht die Synchronisation ganz ein."
           URSACHE: Nach einem ERFOLGREICHEN Schreibvorgang hat die Datei eine
           NEUE Kennung — der Client kannte sie aber nicht und schickte beim
           naechsten Mal die alte. Ergebnis: 409, neu lesen, neu senden. Bei
           jedem Push. Auf der Uhr laeuft dann die Wiederholungsschleife (vier
           Versuche), und wenn parallel das Handy schreibt, ist sie irgendwann
           erschoepft: „4× Konflikt (409) — Abgleich ausgesetzt". Genau der
           gemeldete Einbruch.
           GitHub liefert die neue Kennung in der PUT-Antwort mit. Sie hier
           durchzureichen kostet nichts und erspart jedem Client den Umweg.
           Der Client DARF sie ignorieren — dann laeuft es wie bisher, nur mit
           dem Extra-Umlauf. */
        let neueSha = "";
        try { const j = await r.json(); neueSha = (j && j.content && j.content.sha) || ""; }
        catch (e) { /* Antwort nicht lesbar: dann eben ohne — der Client holt frisch. */ }
        return resp(200, { ok: true, mode: "sha-gate", sha: neueSha },
                    neueSha ? { "X-Repo-Sha": neueSha } : undefined);
      }
      if (r.status === 409 || r.status === 422)
        return resp(409, { ok: false, error: "stale base — fresh holen, neu mergen" });
      return resp(502, { ok: false, error: "write " + r.status });
    } catch (e) { return resp(502, { ok: false, error: String(e) }); }
  }
};
