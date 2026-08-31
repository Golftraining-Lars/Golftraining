/* =============================================================================
   Service Worker — Golf Training PWA
   -----------------------------------------------------------------------------
   WARUM ES IHN GIBT
   Die App hiess bisher PWA, war aber keine: kein Manifest, kein Service Worker.
   Die DATEN lagen sicher (IndexedDB + localStorage) — die App-HUELLE aber nicht.
   Ob index.html im Funkloch startet, hing allein am HTTP-Cache des Browsers;
   GitHub Pages liefert ein kurzes max-age. Nach dessen Ablauf stand auf dem
   14. Loch eine Fehlerseite, waehrend alle Runden wohlbehalten im Geraet lagen.

   STRATEGIEN
   · App-Huelle (index.html):  stale-while-revalidate.
     Sofort aus dem Cache anzeigen, im Hintergrund erneuern. Damit startet die
     App IMMER, auch ohne Netz, und ist trotzdem nie mehr als einen Start alt.
   · Kartenkacheln (Luftbild):  cache-first, LRU-begrenzt.
     Genau die Bilder, die man auf dem Platz braucht und dort am schlechtesten
     nachladen kann. Begrenzt, damit der Speicher nicht unbemerkt volllaeuft.
   · Daten (trainingsdaten.json, Worker, Open-Meteo):  NIE cachen.
     Der Rundenstand muss frisch sein; ein gecachter Entwurf wuerde die
     Uhr-Kopplung und den SHA-Tuersteher in cloudSave unterlaufen.

   VERSION
   CACHE_VERSION bei jeder Aenderung an dieser Datei erhoehen — sonst behalten
   bereits installierte Geraete den alten Worker.
   ========================================================================== */

/* Bei jeder Aenderung erhoehen — sonst behalten installierte Geraete den alten
   Worker. v2: Die Huelle wird nicht mehr blind aus dem Cache geliefert, wenn
   das Netz erreichbar ist (siehe unten). */
/* v3 (29.08.2026): Die Erhoehung macht ALLE gespeicherten Huellen ungueltig —
   ein bewusster Bruch. Nach dem Vorfall vom 29.08. kann auf Geraeten eine
   ABGESCHNITTENE Huelle liegen, die sich von Start zu Start selbst
   weiterreicht; die Pruefung unten wirft sie zwar weg, aber ein sauberer
   Schnitt ist verlaesslicher als eine Reparatur im Betrieb. */
const CACHE_VERSION = "v4";
const SHELL_CACHE = "golf-shell-" + CACHE_VERSION;
const TILE_CACHE  = "golf-tiles-" + CACHE_VERSION;
const TILE_MAX    = 400;          // ca. 20–40 MB, reicht fuer mehrere Plaetze

// Hosts, deren Antworten NIEMALS aus dem Cache kommen duerfen
const NEVER_CACHE = [
  "workers.dev",                  // Cloudflare Worker (Lesen/Schreiben Repo)
  "api.open-meteo.com",           // Wetter und Hoehe
  "api.github.com",               // Commit-Historie
];
const isNeverCache = url =>
  NEVER_CACHE.some(h => url.hostname.endsWith(h)) ||
  url.pathname.endsWith("trainingsdaten.json") ||
  url.search.includes("fresh=1");

// Kartenkacheln erkennt man am Pfadmuster /z/x/y.ext
const isTile = url =>
  /\/\d+\/\d+\/\d+(@2x)?\.(png|jpe?g|webp)/.test(url.pathname) ||
  /tile|wmts|arcgis|basemap/i.test(url.hostname);

/* Vor dem Ablegen pruefen — plausible Groesse und Abschluss des Dokuments.
   Steht hier oben, weil BEIDE Stellen sie brauchen: das Vorwaermen bei der
   Installation und der Abruf im Betrieb. */
/* ==========================================================================
   DIE HUELLE WIRD EINMAL GELESEN, NICHT VIERMAL (v3.2, 31.08.2026)
   --------------------------------------------------------------------------
   GEMESSEN am 31.08.: „Start in 20361 ms · 0 kB ueber die Leitung" — also aus
   dem Speicher, ohne Netz, und trotzdem zwanzig Sekunden. Das Skript selbst
   erklaert das nicht: Uebersetzen kostet 25 ms, die oberste Ebene 10 ms;
   selbst mit Faktor 20 fuer ein langsames Geraet bleibt es unter einer
   Sekunde.
   DIE ZEIT GING HIER DRAUF. Vor jeder Auslieferung wurde die 2,7-MB-Huelle
   ZWEIMAL VOLLSTAENDIG IN TEXT VERWANDELT: einmal von `istGanz` (ist sie
   vollstaendig?) und einmal von `fassungAus` (welche Fassung liegt da?). Beide
   Male derselbe Puffer, beide Male vor dem ersten Bild — und `response.text()`
   auf einem Handy ist keine schnelle Sache.
   ZWEI PRUEFUNGEN AN DERSELBEN DATEI SIND EIN LESEVORGANG, NICHT ZWEI. Der
   Text wird jetzt EINMAL geholt und beiden Fragen gestellt.
   UND DAS ENDE GENUEGT: Ob die Huelle vollstaendig ist, steht in den letzten
   Zeichen — `</html>` am Schluss. Die Fassungsnummer steht zwar weiter oben,
   aber sie wird nur gebraucht, wenn ohnehin gelesen wird. Fuer den haeufigen
   Fall (Huelle ist ganz, Fassung unveraendert) faellt damit alles weg. */
async function huelleLesen(r){
  try{
    if (!r || !r.ok) return null;
    return await r.clone().text();
  }catch(_){ return null; }
}
function textIstGanz(t){
  return typeof t === "string" && t.length > 500000 && /<\/html>\s*$/.test(t);
}
async function istGanz(r){
  const t = await huelleLesen(r);
  return textIstGanz(t);
}

self.addEventListener("install", ev => {
  /* ==========================================================================
     AUCH DAS VORWAERMEN PRUEFT (v3.1, 29.08.2026)
     --------------------------------------------------------------------------
     Hier stand `c.addAll(["./", "./index.html"])`. `addAll` legt ab, was mit
     Status 200 zurueckkommt — auch einen Download, der mitten in den 2,7 MB
     abbricht. Damit hatte der frisch installierte Worker eine LUECKE AN GENAU
     DER STELLE, die v3 gerade geschlossen hatte: Die Pruefung sass im
     `fetch`-Zweig, das Vorwaermen ging daran vorbei.
     Und das Vorwaermen ist der GEFAEHRLICHERE Weg: Es laeuft bei der
     Installation, oft direkt nach einem Fassungswechsel, und legt die Huelle
     an, mit der die App danach startet.
     Jetzt selbst holen und pruefen. Misslingt es, wird NICHTS abgelegt — der
     Abruf im Betrieb holt die Huelle beim ersten Start nach. Eine fehlende
     Huelle kostet einen Ladevorgang; eine halbe kostet die App. */
  ev.waitUntil((async () => {
    try{
      const c = await caches.open(SHELL_CACHE);
      const r = await fetch("./index.html", {cache:"reload"});
      if (await istGanz(r)) await c.put("./index.html", r.clone());
      else await melde("vorwaermen",
        "Hülle beim Installieren unvollständig — nicht gespeichert");
    }catch(_){ /* offline beim ersten Start: kein Grund abzubrechen */ }
    await self.skipWaiting();
  })());
});

self.addEventListener("activate", ev => {
  ev.waitUntil(
    caches.keys()
      .then(ks => Promise.all(
        ks.filter(k => k.startsWith("golf-") && k !== SHELL_CACHE && k !== TILE_CACHE)
          .map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

// Kachel-Cache in Grenzen halten (FIFO — aeltester Eintrag zuerst raus)
async function trimTiles() {
  const c = await caches.open(TILE_CACHE);
  const keys = await c.keys();
  if (keys.length <= TILE_MAX) return;
  for (const k of keys.slice(0, keys.length - TILE_MAX)) await c.delete(k);
}

/* Nachricht an ALLE offenen Fenster. Kommt keines an (der haeufige Fall — die
   App laeuft ja gerade nicht), ist das kein Problem: Die Meldung wird
   zusaetzlich im Cache abgelegt und beim naechsten Start abgeholt. Ein
   Ereignis, das nur ankommt, wenn ohnehin alles laeuft, waere wertlos. */
async function melde(art, text){
  try{
    const rec = {art, text, at: new Date().toISOString()};
    try{
      const c = await caches.open(SHELL_CACHE);
      const alt = await c.match("./__swlog");
      const liste = alt ? (await alt.json()) : [];
      liste.push(rec);
      while(liste.length > 20) liste.shift();
      await c.put("./__swlog", new Response(JSON.stringify(liste),
        {headers:{"Content-Type":"application/json"}}));
    }catch(_){ /* ohne Cache eben nur die Live-Nachricht */ }
    const cs = await self.clients.matchAll({includeUncontrolled:true});
    cs.forEach(cl => cl.postMessage({type:"SW_LOG", rec}));
  }catch(_){ /* Ein Protokoll darf nie selbst zum Problem werden. */ }
}

self.addEventListener("fetch", ev => {
  const req = ev.request;
  if (req.method !== "GET") return;

  let url;
  try { url = new URL(req.url); } catch (_) { return; }

  if (isNeverCache(url)) return;                    // durchreichen, nicht anfassen

  // ---- App-Huelle: stale-while-revalidate ----
  const isShell =
    req.mode === "navigate" ||
    (url.origin === self.location.origin &&
      (url.pathname.endsWith("/") || url.pathname.endsWith("index.html")));

  if (isShell) {
    ev.respondWith((async () => {
      const c = await caches.open(SHELL_CACHE);
      /* ==================================================================
         EINE HALBE HUELLE IST SCHLIMMER ALS KEINE (v3, 29.08.2026)
         --------------------------------------------------------------------
         GEMELDET: Nach einer Neuinstallation blieb die App im Startbild
         haengen; in Chrome erschien nur das STATISCHE Geruest (Kopfzeile mit
         Platzhaltern, Navigationsleiste) — das Skript lief gar nicht.
         DIE URSACHE STECKT HIER: `if (r && r.ok) c.put(...)`. Ein Download,
         der mitten in den 2,7 MB abbricht, hat trotzdem Status 200 und gilt
         als `ok`. Die halbe Datei wandert in den Cache — und weil der
         Wettlauf unten fast immer der Cache gewinnt (2,7 MB kommen NIE in
         1,5 s an), bekommt man sie danach bei JEDEM Start wieder.
         Eine abgeschnittene Datei ist ein Syntaxfehler: Der Browser zeigt das
         Geruest und fuehrt nichts aus. Genau das war zu sehen.
         GEGENPROBE VOR DEM ABLEGEN UND VOR DEM AUSLIEFERN. `istGanz` prueft
         zweierlei: plausible Groesse und den Abschluss des Dokuments. Beides
         ist billig — der Text liegt ohnehin im Speicher.
         DIESELBE REGEL WIE IN `swForceUpdate` (App v5.01) und im
         Archiv-Skript: erst pruefen, dann uebernehmen. Sie wurde in dieser
         Woche viermal gebraucht. */
      let cached = await c.match("./index.html");
      /* EINMAL LESEN, BEIDE FRAGEN STELLEN (v3.2) — siehe `huelleLesen`. */
      const cachedText = cached ? await huelleLesen(cached) : null;
      /* EINE KAPUTTE HUELLE WIRD WEGGEWORFEN, NICHT AUSGELIEFERT. Sonst
         reicht sie sich selbst von Start zu Start weiter. */
      if (cached && !textIstGanz(cachedText)) {
        melde("huelle-kaputt", "Gespeicherte Hülle war unvollständig — verworfen");
        try { await c.delete("./index.html"); } catch (_) {}
        cached = null;
      }
      /* ==================================================================
         DIE NEUE FASSUNG MELDET SICH (v4, 29.08.2026)
         --------------------------------------------------------------------
         Der Kommentar unten beschreibt das Problem seit v2 selbst: „man testet
         stundenlang eine Version zu alt und haelt jede Korrektur fuer
         wirkungslos." Genau das ist am 28./29.08. passiert — im Protokoll
         steht dreimal „gespeicherte Fassung geliefert" und einmal sogar ein
         RUECKSCHRITT von 5.08.0 auf 5.07.0. Behebungen kamen tagelang nicht an,
         und beide Seiten haben an der falschen Stelle gesucht.
         DER WETTLAUF IST NICHT ZU GEWINNEN: 2,7 MB kommen nie in 1,5 s an. Der
         Cache MUSS gewinnen, sonst gibt es keine Startgarantie auf dem Platz.
         ALSO NICHT SCHNELLER WERDEN, SONDERN BESCHEID SAGEN. Der Abruf laeuft
         ohnehin im Hintergrund weiter und erneuert den Cache. Neu ist: Wenn
         die geholte Fassung eine ANDERE Fassungsnummer traegt als die gerade
         laufende, bekommt die App eine Nachricht — und zeigt einen Hinweis,
         den man antippen kann. Ein Neuladen genuegt dann; die neue Huelle
         liegt schon da.
         DIE FASSUNGSNUMMER STEHT IM TEXT (`APP_VERSION="x.y.z"`), also ist sie
         ohne Zusatzdatei ablesbar. Der Text liegt beim Pruefen ohnehin im
         Speicher (`istGanz` liest ihn), es kostet also nichts extra. */
      const fassungAus = t => {
        const m = /APP_VERSION="([\d.]+)"/.exec(t || "");
        return m ? m[1] : null;
      };
      const altFassung = cachedText ? fassungAus(cachedText) : null;
      const net = fetch(req)
        .then(async r => {
          /* AUCH HIER EINMAL LESEN (v3.2): `istGanz` und `fassungAus` fragten
             denselben Puffer zweimal ab. Dieser Zweig laeuft zwar im
             Hintergrund und blockiert nichts — aber zwei Lesevorgaenge ueber
             2,7 MB kosten auch dort Zeit und Akku, und die Regel ist dieselbe:
             zwei Pruefungen an derselben Datei sind ein Lesevorgang. */
          const txt = await huelleLesen(r);
          if (textIstGanz(txt)) {
            c.put("./index.html", r.clone());
            const neu = fassungAus(txt);
            if (neu && altFassung && neu !== altFassung)
              melde("neue-fassung", "Neue Fassung " + neu + " liegt bereit (läuft: "
                + altFassung + ") — Neuladen genügt");
          }
          else if (r && r.ok) melde("abbruch",
            "Unvollständige Antwort NICHT gespeichert — die bisherige Hülle bleibt");
          return r;
        })
        .catch(() => null);

      /* NETZ ZUERST, ABER MIT KURZEM ZEITLIMIT (v2).
         Reines stale-while-revalidate lieferte immer die gespeicherte Fassung —
         eine neue Version erschien erst beim UEBERNAECHSTEN Start. Waehrend der
         Entwicklung heisst das: man testet stundenlang eine Version zu alt und
         haelt jede Korrektur fuer wirkungslos.
         Jetzt: hoechstens 1,5 s auf das Netz warten. Kommt eine Antwort, ist sie
         aktuell. Kommt keine (Funkloch auf dem Platz), greift sofort der Cache —
         die Startgarantie bleibt also erhalten. */
      const frisch = await Promise.race([
        net,
        new Promise(res => setTimeout(() => res(null), 1500))
      ]);
      if (frisch && frisch.ok) return frisch;

      /* ==================================================================
         DER SERVICE WORKER MELDET SICH (v3, 2026-08-29)
         --------------------------------------------------------------------
         Bis hierher war er der groesste blinde Fleck der ganzen Diagnose: Er
         entscheidet ueber Start oder Nicht-Start und schrieb nie eine Zeile.
         Am 29.08. war die App auf dem Handy stundenlang unerreichbar — im
         Fehlerprotokoll stand davon NICHTS, weil alles hier drin passierte.
         `melde()` schickt den Grund an alle offenen Fenster. Die App schreibt
         ihn beim NAECHSTEN ERFOLGREICHEN START ins Protokoll: Der Ausfall wird
         nachtraeglich erzaehlt, und genau das ist die Antwort auf „warum steht
         davon nichts im Log".
         KEIN NETZ ZU IHM NOETIG, kein Fernprotokoll — die Nachricht bleibt auf
         dem Geraet. */
      if(!frisch) melde("huelle-aus-speicher",
        cached ? "Netz zu langsam oder nicht da — gespeicherte Fassung geliefert"
               : "Netz nicht erreichbar UND nichts gespeichert");
      if(!cached && !(await net)) melde("huelle-fehlt",
        "Weder Netz noch Speicher — die App kann nicht starten");
      return cached || (await net) || new Response(
        "<h1>Offline</h1><p>Die App wurde noch nicht für den Offline-Betrieb " +
        "gespeichert. Einmal mit Netz öffnen, danach geht es auch ohne.</p>",
        { headers: { "Content-Type": "text/html; charset=utf-8" }, status: 503 }
      );
    })());
    return;
  }

  // ---- Kartenkacheln: cache-first ----
  if (isTile(url)) {
    ev.respondWith((async () => {
      const c = await caches.open(TILE_CACHE);
      const hit = await c.match(req);
      if (hit) return hit;
      try {
        const r = await fetch(req);
        // opaque (no-cors) Antworten sind ok — sie lassen sich zwar nicht
        // pruefen, aber anzeigen; genau darum geht es beim Luftbild.
        if (r && (r.ok || r.type === "opaque")) { await c.put(req, r.clone()); trimTiles(); }
        return r;
      } catch (_) {
        return hit || Response.error();
      }
    })());
  }
});

// Von der App aus ausloesbar: Kacheln des aktuellen Platzes vorladen
self.addEventListener("message", ev => {
  const d = ev.data || {};
  if (d.type === "PRECACHE_TILES" && Array.isArray(d.urls)) {
    ev.waitUntil((async () => {
      const c = await caches.open(TILE_CACHE);
      let n = 0;
      for (const u of d.urls.slice(0, 300)) {
        try {
          if (await c.match(u)) continue;
          const r = await fetch(u, { mode: "no-cors" });
          if (r) { await c.put(u, r.clone()); n++; }
        } catch (_) { /* einzelne Kachel darf fehlschlagen */ }
      }
      await trimTiles();
      const cs = await self.clients.matchAll();
      cs.forEach(cl => cl.postMessage({ type: "PRECACHE_DONE", count: n }));
    })());
  }
  if (d.type === "CLEAR_TILES") {
    ev.waitUntil(caches.delete(TILE_CACHE));
  }
});
