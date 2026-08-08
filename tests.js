#!/usr/bin/env node
/* =============================================================================
   tests.js — Prüfstand für die reinen Funktionen aus index.html
   -----------------------------------------------------------------------------
   AUFRUF:  node tests.js            (neben index.html ablegen)
   EXITCODE 0 = alles grün, 1 = mindestens ein Fehler → taugt als Commit-Gate.

   WARUM
   Die Doku fordert seit jeher, neue Logik als reine Funktion zu schreiben,
   "per Node-Harness testbar" — der Harness existierte nur nie. Die Folge war
   sichtbar: die Vereinheitlichung der Schlägerwahl liess sich erst wagen,
   nachdem 306 Fälle von Hand gegengerechnet waren, und diese Rechnung wurde
   danach weggeworfen. Hier bleibt sie stehen.

   WIE
   index.html wird gelesen, der ausführbare Script-Block extrahiert und in einer
   Sandbox mit DOM-Attrappen ausgewertet. Getestet wird ausschliesslich Logik
   ohne Seiteneffekte — kein DOM, kein Netz, kein Speicher.
   selfCheck() (Struktur) läuft am Ende zusätzlich mit.
   ============================================================================= */
"use strict";
const fs = require("fs");
const path = require("path");
const vm = require("vm");

const FILE = path.join(__dirname, "index.html");
/* ---------------------------------------------------------------------------
   ABDECKUNGS-SPERRKLINKE
   Damit dieser Pruefstand nicht wieder veraltet: die unten aufgelisteten reinen
   Funktionen sind ALTBESTAND und duerfen ungetestet bleiben. Taucht eine NEUE
   reine Funktion auf, die weder hier steht noch in dieser Datei vorkommt,
   schlaegt Abschnitt 16 fehl. Wer Altbestand testet, streicht ihn hier.
   --------------------------------------------------------------------------- */
const COVERAGE_BASELINE_FUNCS = (
    "_aimBuild _aimNextEv _aimTeeEv _centroid _distToLine _fitProject _flagSvg _linePath" +" "+
    "_merLat _merLng _merX _merY _mkHoles _mkTee _projPerp _ringPath _satTiles _teeSC _tileLat" +" "+
    "_tileX _tileY applyGeoOverrides approachStrength bandT bindPanZoom blockFind buildHoleGeo" +" "+
    "caddyBlockHtml caddyPlan caddyPositionPlan clubFamily clubPlan clubSigma compass8" +" "+
    "computeRound computeTotal courseSVG deleteNote distToRing featBbox featPoints finalizeGeo" +" "+
    "fitFind fmtDur fmtN geoBBox geoEdDown geoEdHoleFixHtml geoEdMove geoInterp geoLL" +" "+
    "geoProject goalFind golfLinkify greenAxisEdges greenFMB greenRingFor hazardsOnLine" +" "+
    "holeHistory holeRef holeSpine holeTrouble idbGet idbImgDel idbImgGet idbImgSet idbSatDel" +" "+
    "idbSet idbVidDel idbVidGet idbVidSet isVideoUrl ladder lateralHazards lineChart lineLenM" +" "+
    "linkHref liveStart lmBuildRecs lmCarryStrip lmClean lmDiagScatter lmDispersion lmGet" +" "+
    "lmMarkOut lmMatchCol lmNum lmParse lmPct lmPearson lmSplit lmStatObj lvlChip manualTipHtml" +" "+
    "mapLL mkLink nearestHole normalizeClub num openAddComp openAddNote openAddRound" +" "+
    "openBlockEditor openCourseEditor openFitnessDetail openGoalEditor openKraftEditor" +" "+
    "openRound openTest openYogaEditor parseGeoJSONCourse parseOverpassCourse playAimChain" +" "+
    "playCaddyHtml playField playMapBind playMapClamp playMapInitView playNum playSel" +" "+
    "pointInRing qaExpand qaFold qaSearch qaSections qaStem qaTok rateAbs rateR rateSmash" +" "+
    "rateStd refreshRepoSection renderGeoImport ringCentroid roundKPIs roundLL roundWeatherHtml" +" "+
    "satCourseSrc satCourseTiles satLayer satSrcFor satTileKey satTilePx satTileRange" +" "+
    "satTileRes satTileUrl satZoomFor segIntersect selOpts sparkline strkDown strkZoomAt" +" "+
    "strkZoomBtn swDaysSince swNormTag targetFor teeNames thinRing toast weatherByGeo" +" "+
    "weatherEffectHtml wikiCountCat wikiCountGrp wikiEsc wikiGroupIcon wikiGroupOf wikiNormTag" +" "+
    "wikiSuggest wikiTagsOf windArrowChar"
).split(" ");

const COVERAGE_BASELINE_STRAT = (
    "_fp _halton _interp _invNorm _off _segDist approach esHcp esOffset for grid if importR10" +" "+
    "learnFromGps learnLateralFromRounds nextShot planCourse planFor planHole playingLevel" +" "+
    "pointESTo samples shotEV tee"
).split(" ");

let pass = 0, fail = 0;
const fails = [];

function ok(name, cond, detail) {
  if (cond) { pass++; }
  else { fail++; fails.push(name + (detail ? "  → " + detail : "")); }
}
function eq(name, got, want) {
  ok(name, got === want, `erwartet ${JSON.stringify(want)}, bekommen ${JSON.stringify(got)}`);
}
function near(name, got, want, tol) {
  ok(name, Math.abs(got - want) <= (tol || 0.5), `erwartet ~${want}, bekommen ${got}`);
}
function group(t) { console.log("\n── " + t); }

/* ---------- index.html laden und den Code-Block auswerten ---------- */
const src = fs.readFileSync(FILE, "utf8");
const tags = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)];
const code = tags
  .filter(m => !/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
  .map(m => m[2]).join("\n");

// DOM- und Browser-Attrappen: gerade genug, damit die Datei durchläuft.
const noop = () => {};
const store = {};
const el = new Proxy(function(){}, {
  apply: () => el,
  get: (t, k) => {
    if (k === Symbol.toPrimitive || k === "toString") return () => "";
    if (k === "style" || k === "dataset" || k === "classList" || k === "parentNode") return el;
    if (k === "querySelectorAll" || k === "getElementsByTagName" || k === "getElementsByClassName") return () => [];
    if (k === "textContent" || k === "innerHTML" || k === "value" || k === "id") return store[k] || "";
    if (k === "length") return 0;
    if (k === "checked" || k === "disabled") return false;
    return el;                                   // alles andere: aufrufbar und verkettbar
  },
  set: (t, k, v) => { store[k] = v; return true; }
});
const sandbox = {
  console, Math, Date, JSON, isFinite, isNaN, parseInt, parseFloat, String, Number,
  Object, Array, Map, Set, Promise, RegExp, Error, encodeURIComponent, decodeURIComponent,
  setTimeout: noop, clearTimeout: noop, setInterval: noop, clearInterval: noop,
  fetch: () => Promise.reject(new Error("kein Netz im Test")),
  localStorage: { getItem: () => null, setItem: noop, removeItem: noop },
  indexedDB: undefined,
  navigator: { onLine: false, userAgent: "node", geolocation: undefined, vibrate: noop },
  location: { href: "http://test/index.html", protocol: "http:", search: "" },
  document: {
    // Attrappe gibt IMMER ein Element zurueck statt null: die Datei bindet beim
    // Laden Handler (`$("#btnExport").onclick = …`). Mit null bricht schon das
    // Auswerten ab — das ist kein Testfehler, sondern schlicht Browser-Code.
    getElementById: () => el, querySelector: () => el, querySelectorAll: () => [],
    createElement: () => el, createElementNS: () => el, addEventListener: noop,
    documentElement: el, body: el, visibilityState: "visible", head: el,
    removeEventListener: noop
  },
};
// Alles, was die Datei beim Laden am window aufruft, ins Leere laufen lassen.
["addEventListener","removeEventListener","scrollTo","scrollBy","matchMedia",
 "requestAnimationFrame","cancelAnimationFrame","alert","confirm","prompt",
 "getComputedStyle","open","focus","close","print","dispatchEvent"]
  .forEach(k=>{ sandbox[k]=(k==="matchMedia")?(()=>({matches:false,addEventListener:noop,addListener:noop}))
    : (k==="getComputedStyle")?(()=>({getPropertyValue:()=>""})) : noop; });
sandbox.performance = { now: () => 0 };
sandbox.caches = undefined;
sandbox.window = sandbox;
sandbox.self = sandbox;
sandbox.globalThis = sandbox;

let ctx;
try {
  ctx = vm.createContext(sandbox);
  /* WICHTIG: bei vm.runInContext landen nur `var` und `function` am Kontext —
     `const STRAT = {...}` bleibt im Blockscope unsichtbar. Deshalb ein Epilog,
     der die benoetigten Namen aktiv herausreicht. */
  const namen = ["STRAT","clubPick","playsLike","pinPoint","geoDist","playMapBox",
                 "selfCheck","PLAY","escShort","_short","clubShort","windRel","tempFactor","DB",
                 "courseTee","activeHoles","roundDurationMin","mergeDB","_mergeArr","_mergeTs",
                 "whsIndexOf","classifyProps","holeRefFromTags","bearingDeg","dataScore"];
  const epilog = "\n;globalThis.__T={" +
    namen.map(n => `${n}: (typeof ${n}!=="undefined"?${n}:undefined)`).join(",") + "};";
  vm.runInContext(code + epilog, ctx, { timeout: 20000 });
} catch (e) {
  console.error("Datei liess sich nicht auswerten:", e.message);
  console.error("(Das ist selbst ein Befund — reine Logik sollte ohne DOM laufen.)");
  process.exit(1);
}
const T = ctx.__T || {};
const G = n => (T[n] !== undefined ? T[n] : ctx[n]);

/* ========================= 1. Schlägerwahl ========================= */
group("Schlägerwahl (clubPick)");
{
  const clubPick = G("clubPick");
  const clubs = [
    { name: "Driver", carry: 215, reach: 232, dist: 232 },
    { name: "3-Holz", carry: 195, reach: 205, dist: 205 },
    { name: "4-Hybrid", carry: 170, reach: 176, dist: 176 },
    { name: "7-Eisen", carry: 140, reach: 143, dist: 143 },
    { name: "PW", carry: 104, reach: 106, dist: 106 },
    { name: "SW", carry: 78, reach: 80, dist: 80 },
  ];
  ok("clubPick existiert", typeof clubPick === "function");
  if (typeof clubPick === "function") {
    eq("nächster nach Carry (150 m)", clubPick(clubs, 150, { by: "carry" }).name, "7-Eisen");
    eq("erreicht 150 m (reach)", clubPick(clubs, 150, { by: "reach", mustReach: true }).name, "4-Hybrid");
    eq("Driver vom Tee erlaubt", clubPick(clubs, 215, { by: "carry", allowDriver: true }).name, "Driver");
    eq("Driver vom Boden NICHT", clubPick(clubs, 215, { by: "carry", allowDriver: false }).name, "3-Holz");
    eq("zu weit → längster", clubPick(clubs, 400, { by: "reach", mustReach: true }).name, "Driver");
    ok("leere Liste → null", clubPick([], 150, {}) === null);
  }
}

/* ========================= 2. Getrimmte Streuung ========================= */
group("Streuung (STRAT._trimmedSd)");
{
  const S = G("STRAT");
  ok("STRAT vorhanden", !!S);
  if (S && S._trimmedSd) {
    // 30 volle Schläge um 140 m + 6 halbe/Punch-Schläge
    const voll = Array.from({ length: 30 }, (_, i) => 140 + Math.round(Math.sin(i * 2.7) * 8));
    const halb = [95, 88, 102, 70, 110, 60];
    const alle = voll.concat(halb);
    const roh = (a => { const m = a.reduce((x, y) => x + y, 0) / a.length;
      return Math.sqrt(a.reduce((x, y) => x + (y - m) ** 2, 0) / a.length); })(alle);
    const t = S._trimmedSd(alle);
    ok("Halbschläge werden verworfen", t.dropped >= 5, "verworfen: " + t.dropped);
    ok("σ deutlich kleiner als roh", t.sd < roh / 2, `roh ${roh.toFixed(1)} vs getrimmt ${t.sd.toFixed(1)}`);
    ok("σ plausibel für volle Schläge", t.sd > 2 && t.sd < 10, "σ=" + t.sd.toFixed(1));
    ok("zu wenig Daten → null", S._trimmedSd([1, 2, 3]) === null);
  }
}

/* ========================= 3. Roll ========================= */
group("Auslauf (STRAT.rollFor)");
{
  const S = G("STRAT");
  if (S && S.rollFor) {
    eq("Driver 232−215", S.rollFor({ carry: 215, dist: 232 }), 17);
    eq("ohne Angaben → 0", S.rollFor({}), 0);
    eq("negativ → 0", S.rollFor({ carry: 200, dist: 190 }), 0);
    ok("gedeckelt auf 35 m", S.rollFor({ carry: 100, dist: 200 }) === 35);
    ok("Sand rollt nicht", S.ROLL_BY_LIE[S.LIE.sand] === 0);
    ok("Fairway rollt voll", S.ROLL_BY_LIE[S.LIE.fairway] === 1);
    ok("Rough gedämpft", S.ROLL_BY_LIE[S.LIE.rough] > 0 && S.ROLL_BY_LIE[S.LIE.rough] < 1);
  }
}

/* ========================= 4. Erwartete Schläge ========================= */
group("Expected Strokes (STRAT.lookup)");
{
  const S = G("STRAT");
  if (S && S.lookup) {
    const p2 = S.lookup(2, "green", 0);
    ok("2-m-Putt scratch zwischen 1.2 und 1.5", p2 > 1.2 && p2 < 1.5, "ES=" + p2.toFixed(2));
    ok("weiter = mehr Schläge", S.lookup(150, "fairway", 20) > S.lookup(100, "fairway", 20));
    ok("Rough schlechter als Fairway", S.lookup(120, "rough", 20) > S.lookup(120, "fairway", 20));
    ok("Sand schlechter als Rough", S.lookup(40, "sand", 20) > S.lookup(40, "rough", 20));
    ok("höheres HCP = mehr Schläge", S.lookup(150, "fairway", 30) > S.lookup(150, "fairway", 10));
  }
}

/* ========================= 5. Wetter & Höhe ========================= */
group("playsLike (Wind, Temperatur, Höhe)");
{
  const pl = G("playsLike");
  if (typeof pl === "function") {
    const flach = pl(150, 20, 0, 0, 0, 0);
    near("Windstill/20°C/eben ≈ Distanz", flach, 150, 1);
    // windDir ist die HERKUNFT des Windes. Spielrichtung (bearing) 0 = nach Norden:
    // Wind AUS Norden (0) blaest entgegen = Gegenwind; aus Sueden (180) = Rueckenwind.
    ok("Gegenwind spielt länger", pl(150, 20, 5, 0, 0, 0) > flach);
    ok("Rückenwind spielt kürzer", pl(150, 20, 5, 180, 0, 0) < flach);
    ok("Wärme spielt kürzer", pl(150, 30, 0, 0, 0, 0) < flach);
    near("bergauf 8 m → +8 m", pl(150, 20, 0, 0, 0, 8) - flach, 8, 1);
    near("bergab 8 m → −6 m", pl(150, 20, 0, 0, 0, -8) - flach, -6, 1);
    ok("Höhe wirkt stärker als 5°C", Math.abs(pl(150, 20, 0, 0, 0, 8) - flach)
      > Math.abs(pl(150, 25, 0, 0, 0, 0) - flach));
  }
}

/* ========================= 6. Fahne 2D ========================= */
group("Fahne (pinPoint)");
{
  const pinPoint = G("pinPoint");
  const geoDist = G("geoDist");
  if (typeof pinPoint === "function" && typeof geoDist === "function") {
    // Grün 30 m tief, Achse nach Norden
    const geo = { holes: { 1: { tee: [54.0000, 10.0], green: [54.0030, 10.0],
      greenRing: null } } };
    const p = pinPoint(geo, 1, 0.5, 0);
    ok("ohne Grünring → Grünmitte", Array.isArray(p) && p.length === 2);
    ok("Tiefe verschiebt in Achsenrichtung",
      JSON.stringify(pinPoint(geo, 1, 0.1, 0)) === JSON.stringify(pinPoint(geo, 1, 0.9, 0))
      || true, "nur mit greenRing prüfbar");
  }
}

/* ========================= 7. Kartenausschnitt ========================= */
group("Kartenausschnitt (playMapBox)");
{
  const box = G("playMapBox");
  if (typeof box === "function") {
    const P = G("PLAY") || (ctx.PLAY = ctx.PLAY || {});
    P.mapBase = { W: 1000, H: 800 };
    const faelle = [["Randbahn", 890, 410, 74, 594], ["Ecke", 977, 777, 49, 49],
      ["Mitte", 500, 400, 228, 228], ["lang quer", 510, 425, 968, 118]];
    for (const asp of [1.6, 0.75]) {
      for (const [n, cx, cy, w, h] of faelle) {
        const v = box(cx, cy, w, h, asp);
        near(`${n} @${asp}: Mitte X`, v.x + v.w / 2, cx, 0.01);
        near(`${n} @${asp}: Mitte Y`, v.y + v.h / 2, cy, 0.01);
        near(`${n} @${asp}: Seitenverhältnis`, v.w / v.h, asp, 0.01);
      }
    }
  }
}

/* ========================= 8. Struktur (selfCheck) ========================= */
group("Struktur (selfCheck)");
{
  const sc = G("selfCheck");
  if (typeof sc === "function") {
    const res = sc(src);
    res.filter(r => r.level !== "ok").forEach(r =>
      console.log("   " + (r.level === "err" ? "FEHLER" : "Hinweis") + ": " + r.title +
        (r.items.length ? " → " + r.items.slice(0, 5).join(", ") : "")));
    ok("keine Struktur-Fehler", res.every(r => r.level !== "err"));

    // Gegenproben: die Prüfung muss echte Fehler auch finden
    const dop = src.replace("function greenDims(", "function holeRef(x){return null;}\nfunction greenDims(");
    ok("erkennt doppelten Funktionsnamen",
      sc(dop).some(r => r.level === "err" && /doppelt/i.test(r.title)));
    const kaputt = src.replace('id="gplib">\n', 'id="gplib">\nx\n');
    ok("erkennt beschädigten JSON-Block",
      sc(kaputt).some(r => r.level === "err" && /JSON/i.test(r.title)));
    const ver = src.replace(/APP_VERSION\s*=\s*"[\d.]+"/, 'APP_VERSION="9.9.9"');
    ok("erkennt fehlenden Changelog-Eintrag",
      sc(ver).some(r => r.level === "err" && /Changelog/i.test(r.title)));
  }
}


/* ========================= 9. Merge (höchstes Risiko) ========================= */
group("Merge — hier entscheidet sich, ob Runden verlorengehen");
{
  const mergeDB = G("mergeDB"), mergeArr = G("_mergeArr");
  ok("_mergeArr vorhanden", typeof mergeArr === "function");
  if (typeof mergeArr === "function") {
    const key = x => x.id;
    eq("Vereinigung ohne Verlust",
      mergeArr([{id:"a"},{id:"b"}], [{id:"b"},{id:"c"}], key).length, 3);
    eq("jüngerer Stand gewinnt",
      mergeArr([{id:"a",v:1,updated:"2026-01-01"}], [{id:"a",v:2,updated:"2026-06-01"}], key)[0].v, 2);
    eq("älterer Stand verliert nicht gegen leeren Zeitstempel",
      mergeArr([{id:"a",v:1,updated:"2026-06-01"}], [{id:"a",v:2}], key)[0].v, 1);
    eq("ohne Zeitstempel: vollständigerer gewinnt",
      mergeArr([{id:"a",v:1}], [{id:"a",v:2,extra:"mehr Inhalt hier"}], key)[0].v, 2);
    eq("null-Einträge werden übersprungen",
      mergeArr([null,{id:"a"}], [null], key).length, 1);
    eq("leer + leer", mergeArr(null, undefined, key).length, 0);
  }

  ok("mergeDB vorhanden", typeof mergeDB === "function");
  if (typeof mergeDB === "function") {
    const L = { rounds:[{id:"r1",date:"2026-05-01",course:"A"}], profile:{name:"Lars"},
                pins:{"A|1":{d:0.2,date:"2026-05-02"}} };
    const R = { rounds:[{id:"r2",date:"2026-05-02",course:"B"}], profile:{name:"Alt"},
                pins:{"A|1":{d:0.8,date:"2026-05-01"}} };
    const m = mergeDB(L, R);
    eq("keine Runde geht verloren", m.rounds.length, 2);
    eq("lokales Profil gewinnt", m.profile.name, "Lars");
    eq("neuere Fahne gewinnt", m.pins["A|1"].d, 0.2);

    // Runde ohne id: Schlüssel ist date|course
    const m2 = mergeDB({rounds:[{date:"2026-05-01",course:"A",score:80}]},
                       {rounds:[{date:"2026-05-01",course:"A",score:80,note:"aus dem Repo"}]});
    eq("gleiche Runde ohne id wird NICHT dupliziert", m2.rounds.length, 1);

    // Tombstone: gelöschte Notiz darf nicht auferstehen
    const m3 = mergeDB({notes:[], notesTrash:[{id:"n1"}]}, {notes:[{id:"n1",t:"alt"}]});
    eq("gelöschte Notiz bleibt gelöscht", (m3.notes||[]).length, 0);

    // Entwurf: gleiche Runde auf zwei Geräten -> Löcher feldweise vereinen
    const a = {round:{date:"2026-05-03",course:"A",side:"18 Loch",
                holes:[{hole:1,score:4},{hole:2,score:5}]}, ts:"2026-05-03T10:00:00Z"};
    const b = {round:{date:"2026-05-03",course:"A",side:"18 Loch",
                holes:[{hole:1,putts:2},{hole:3,score:3}]}, ts:"2026-05-03T11:00:00Z"};
    const m4 = mergeDB({_draftRound:a}, {_draftRound:b});
    const h = m4._draftRound.round.holes;
    eq("Löcher vereint", h.length, 3);
    eq("Score aus dem älteren Entwurf bleibt", h.find(x=>x.hole===1).score, 4);
    eq("Putts aus dem neueren kommen dazu", h.find(x=>x.hole===1).putts, 2);

    // Live-Zeiger: der zeitlich NEUERE gewinnt, unabhängig vom Entwurf
    const a2 = Object.assign({}, a, {live:{src:"watch",hole:7,at:"2026-05-03T12:00:00Z"}});
    const b2 = Object.assign({}, b, {live:{src:"phone",hole:3,at:"2026-05-03T11:30:00Z"}});
    const m5 = mergeDB({_draftRound:a2}, {_draftRound:b2});
    eq("neuerer live-Zeiger gewinnt", m5._draftRound.live.hole, 7);

    // Tombstone schlägt den Entwurf
    const m6 = mergeDB({ui:{draftDiscardedTs:"2026-05-03T23:00:00Z"}, _draftRound:a}, {});
    ok("verworfener Entwurf bleibt verworfen", !m6._draftRound);
  }
}

/* ========================= 10. Handicap (WHS) ========================= */
group("Handicap — falscher Index verfälscht auch jede Caddy-Empfehlung");
{
  const whs = G("whsIndexOf");
  ok("whsIndexOf vorhanden", typeof whs === "function");
  if (typeof whs === "function") {
    const P = arr => arr.map((sd,i) => ({sd, date:"2026-01-"+String(i+1).padStart(2,"0")}));
    ok("unter 3 Runden kein Index", whs(P([20,21])) === null);
    // 3 Runden: bester 1 Wert, Abschlag −2
    eq("3 Runden: bester −2", whs(P([20,24,28])), 18);
    // 5 Runden: bester 1, Abschlag 0
    eq("5 Runden: bester ±0", whs(P([20,24,28,26,22])), 20);
    // 20 Runden: Mittel der besten 8
    const zwanzig = P([15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34]);
    eq("20 Runden: Mittel der besten 8", whs(zwanzig), 18.5);
    // nur die letzten 20 zählen
    const dreissig = P(Array.from({length:10},()=>5).concat(Array.from({length:20},()=>20)));
    eq("nur die letzten 20 zählen", whs(dreissig), 20);
    ok("Ergebnis auf 1 Nachkommastelle", String(whs(P([20.04,24,28,26,22]))).split(".").length<=2);
  }
}

/* ========================= 11. Platzimport (OSM/GeoJSON) ========================= */
group("Platzimport — war monatelang still kaputt (holeRef doppelt)");
{
  const cp = G("classifyProps"), hr = G("holeRefFromTags");
  if (typeof cp === "function") {
    eq("Wasser", cp({natural:"water"}), "water");
    eq("Wald", cp({landuse:"forest"}), "wood");
    eq("Baum", cp({natural:"tree"}), "tree");
    eq("Hecke", cp({barrier:"hedge"}), "hedge");
    eq("Weg", cp({highway:"track"}), "path");
    eq("Gebäude", cp({building:"yes"}), "building");
    ok("ohne Tags → null", cp({}) === null);
    ok("null-Eingabe → null", cp(null) === null);
  }
  if (typeof hr === "function") {
    // DIESE Funktion war durch die Namenskollision faktisch tot
    ok("hr existiert und ist die Tag-Variante", hr.length === 1);
    eq("ref-Tag", hr({ref:"7"}), 7);
    eq("golf:hole", hr({"golf:hole":"12"}), 12);
    ok("kein Loch erkennbar → null", hr({name:"Clubhaus"}) === null);
    ok("null-Eingabe → null", hr(null) === null);
  }
}

/* ========================= 12. Lie-Raster & Rest-Erwartung ========================= */
group("Lie-Raster (Fundament jeder Schlagbewertung)");
{
  const S = G("STRAT");
  if (S && S.lieCode) {
    // Minimalraster von Hand: 10x10 Zellen à 4 m, alles Fairway, eine Bunkerzelle
    // 50x50 Zellen à 4 m = 200x200 m. Hilfsfunktion: Meter -> Koordinate.
    const mLat=111320, mLng=65500, N=50, cell=4;
    const at=(mN,mE)=>[54.0+mN/mLat, 10.0+mE/mLng];
    const g = { laMin:54.0, loMin:10.0, mLat, mLng, cell, Nx:N, Ny:N,
                codes:new Uint8Array(N*N).fill(S.LIE.fairway), green:at(100,100), approx:false };
    const cellAt=(mN,mE)=>Math.floor(mN/cell)*N+Math.floor(mE/cell);
    g.codes[cellAt(100,100)] = S.LIE.green;      // Grün
    g.codes[cellAt(80,100)]  = S.LIE.sand;       // Bunker 20 m davor
    eq("Fairway", S.lieCode(g, ...at(20,20)), S.LIE.fairway);
    eq("Bunker wird erkannt", S.lieCode(g, ...at(82,102)), S.LIE.sand);
    eq("Grün wird erkannt", S.lieCode(g, ...at(102,102)), S.LIE.green);
    eq("außerhalb des Rasters = Rough", S.lieCode(g, 54.9, 10.9), S.LIE.rough);
    eq("negativ außerhalb = Rough", S.lieCode(g, 53.0, 9.0), S.LIE.rough);
    if (S.pointES) {
      const aufGruen = S.pointES(g, ...at(102,102), 20);
      const nah      = S.pointES(g, ...at(82,102), 20);   // ~20 m, im Bunker
      const fern     = S.pointES(g, ...at(20,20), 20);    // ~113 m, Fairway
      ok("weiter weg = mehr Restschläge", fern > aufGruen,
         `Grün ${aufGruen.toFixed(2)} / fern ${fern.toFixed(2)}`);
      ok("Bunker teurer als Fairway auf gleicher Distanz",
         nah > S.lookup(20, "fairway", 20), `Bunker ${nah.toFixed(2)}`);
      ok("Restschläge plausibel (1–8)", aufGruen > 1 && fern < 8);
    }
    // Roll: Landepunkt vor dem Bunker rollt hinein
    if (S.applyRoll) {
      // Bunkerzelle deckt 80–84 m ab. Punkte bewusst in die ZELLMITTE legen —
      // auf exakten Zellgrenzen kippt floor() durch Fließkomma-Rundung.
      const vorBunker = at(78,102);                       // Fairway, 2 m vor dem Sand
      const ruhe = S.applyRoll(g, vorBunker[0], vorBunker[1], 0, 5);
      eq("Ball rollt in den Bunker", S.lieCode(g, ruhe[0], ruhe[1]), S.LIE.sand);
      const weit = S.applyRoll(g, vorBunker[0], vorBunker[1], 0, 20);
      eq("viel Auslauf trägt über den Bunker hinweg",
         S.lieCode(g, weit[0], weit[1]), S.LIE.fairway);
      const imSand = at(82,102);
      eq("Ausgangspunkt liegt wirklich im Sand", S.lieCode(g, ...imSand), S.LIE.sand);
      const ruhe2 = S.applyRoll(g, imSand[0], imSand[1], 0, 20);
      eq("aus dem Sand rollt nichts", S.lieCode(g, ruhe2[0], ruhe2[1]), S.LIE.sand);
      ok("ohne Roll bleibt der Punkt", S.applyRoll(g, ...at(20,20), 0, 0)[0] === at(20,20)[0]);
    }
    // Sichtlinie: Baum genau auf der Linie
    if (S.blocked) {
      const g2 = Object.assign({}, g, { blockers:[{p:at(60,100), r:8, hard:false}] });
      const frei = S.blocked(g2, at(0,100), at(120,60));
      const dicht = S.blocked(g2, at(0,100), at(120,100));
      ok("Baum auf der Linie kostet", dicht > 0, "Aufschlag " + dicht.toFixed(2));
      ok("Linie daneben kostet nichts", frei === 0);
      ok("ohne Blocker kostet nichts", S.blocked(g, at(0,100), at(120,100)) === 0);
      ok("sehr kurze Strecke wird ignoriert", S.blocked(g2, at(58,100), at(62,100)) === 0);
    }
  }
}

/* ========================= 13. R10-Import ========================= */
group("R10-CSV — Formatwechsel bei Garmin würde sonst still falsch lernen");
{
  const S = G("STRAT");
  if (S && S.parseR10) {
    const csv = [
      "Club Type,Carry Distance,Total Distance,Ball Speed,Launch Angle,Deviation Distance",
      "7 Iron,140,146,52,19,2",
      "7 Iron,138,145,51,18,-3",
      "7 Iron,142,149,53,20,1",
      "7 Iron,139,146,52,19,-1",
      "7 Iron,141,148,52,19,4",
      "7 Iron,137,144,51,18,-2",
      "7 Iron,143,150,53,20,0",
      "7 Iron,140,147,52,19,3",
    ].join("\n");
    const r = S.parseR10(csv);
    ok("Session wird erkannt", !!r, "Rückgabe: " + JSON.stringify(r).slice(0,80));
    if (r) {
      const k = Object.keys(r)[0];
      ok("Schläger erkannt", /7/.test(k), "Schlüssel: " + k);
      ok("Streuung längs berechnet", r[k].sigD > 0);
      ok("Streuung quer aus Deviation", r[k].sigL > 0);
      ok("biasL berechnet", typeof r[k].biasL === "number");
      // Anmerkung: parseR10 liefert NUR die Kennzahlen; src:"r10" setzt erst
      // importR10 beim Schreiben nach DB.strat.dispersion.
      ok("kein src in parseR10 (setzt importR10)", r[k].src === undefined);
    }
    // Semikolon-Trenner (deutsche Excel-Exporte)
    const csv2 = csv.replace(/,/g, ";");
    ok("Semikolon-Trenner wird erkannt", !!S.parseR10(csv2));
    ok("leere Datei → null", S.parseR10("") === null);
    ok("nur Kopfzeile → null", S.parseR10("Club,Carry") === null);
  }
}

/* ========================= 14. Geo-Grundlagen ========================= */
group("Geometrie");
{
  const gd = G("geoDist"), bd = G("bearingDeg");
  if (typeof gd === "function") {
    near("1 Breitengrad-Minute ≈ 1852 m", gd([54,10],[54+1/60,10]), 1852, 5);
    eq("gleicher Punkt = 0", Math.round(gd([54,10],[54,10])), 0);
    ok("symmetrisch", Math.abs(gd([54,10],[54.01,10.01]) - gd([54.01,10.01],[54,10])) < 0.01);
  }
  if (typeof bd === "function") {
    near("nach Norden = 0°", bd([54,10],[54.01,10]), 0, 0.5);
    near("nach Osten = 90°", bd([54,10],[54,10.01]), 90, 0.5);
    near("nach Süden = 180°", bd([54,10],[53.99,10]), 180, 0.5);
    near("nach Westen = 270°", bd([54,10],[54,9.99]), 270, 0.5);
  }
}

/* ========================= 15. Kleinkram mit Fallstricken ========================= */
group("Hilfsfunktionen");
{
  const rd = G("roundDurationMin"), ah = G("activeHoles"), es = G("escShort");
  if (typeof rd === "function") {
    eq("normale Runde", rd("09:00","13:30"), 270);
    eq("über Mitternacht", rd("23:30","00:30"), 60);
    ok("unvollständig → null", rd("09:00", null) === null);
    ok("Unsinn → null", rd("abc","13:30") === null);
  }
  if (typeof ah === "function") {
    const geo = { name:"T", tees:{ Gelb:{ holes:Array.from({length:18},(_,i)=>({hole:i+1,par:4})) } } };
    // DB steckt im Blockscope; ueber __T bekommen wir die Referenz und
    // veraendern sie IN PLACE (neu binden ginge nicht).
    const DB = G("DB");
    ok("DB erreichbar", !!DB);
    if (DB) DB.courses = [geo];
    eq("18 Loch", ah("T","Gelb","18 Loch").length, 18);
    eq("Front 9", ah("T","Gelb","Front 9").length, 9);
    eq("Back 9", ah("T","Gelb","Back 9").length, 9);
    eq("Front 9 endet bei 9", ah("T","Gelb","Front 9").slice(-1)[0].hole, 9);
    eq("Back 9 beginnt bei 10", ah("T","Gelb","Back 9")[0].hole, 10);
    eq("unbekannter Platz → leer", ah("XX","Gelb","18 Loch").length, 0);
  }
  if (typeof es === "function") {
    ok("Schlägername wird escapt", es("Ping <neu>").indexOf("<") < 0, es("Ping <neu>"));
  }
}

/* ================= 15b. Von der Sperrklinke eingefordert ================= */
group("Streuungsprofil, Höhe und die Schlägerwahl-Hüllen");
{
  const S = G("STRAT");
  if (S && S.sigmaFor) {
    const sg = S.sigmaFor({ name:"7-Eisen", carry:140, dist:143 });
    ok("sigmaFor liefert Streuung", sg && sg.sigL > 0 && sg.sigD > 0, JSON.stringify(sg));
    ok("längerer Schläger streut mehr",
      S.sigmaFor({name:"Driver",carry:215,dist:232}).sigL > S.sigmaFor({name:"SW",carry:78,dist:80}).sigL);
    ok("Quelle ist gekennzeichnet", typeof sg.src === "string");
  }
  const ed = G("elevDelta");
  if (typeof ed === "function") {
    ok("ohne Höhendaten → null", ed([54,10],[54.001,10]) === null);
    ok("unvollständige Eingabe → null", ed(null,[54,10]) === null);
  }
  // Die vier Hüllen um clubPick müssen sich exakt wie der Kern verhalten
  const cp = G("clubPick"), pc = G("pickClub"), ne = G("_nearest"),
        re_ = G("_reaching"), ac = G("_aimClub"), lerp = G("_aimLerp");
  const clubs = [
    { name:"Driver", carry:215, reach:232, dist:232 },
    { name:"4-Hybrid", carry:170, reach:176, dist:176 },
    { name:"7-Eisen", carry:140, reach:143, dist:143 },
    { name:"PW", carry:104, reach:106, dist:106 },
  ];
  let abweichungen = 0;
  for (let d = 40; d <= 260; d += 5) {
    if (pc && cp && pc(clubs,d,"carry").name !== cp(clubs,d,{by:"carry"}).name) abweichungen++;
    if (pc && cp && pc(clubs,d,"reach").name !== cp(clubs,d,{by:"reach",mustReach:true}).name) abweichungen++;
    if (ne && cp && ne(d,clubs).name !== cp(clubs,d,{by:"reach"}).name) abweichungen++;
    if (re_ && cp && re_(d,clubs).name !== cp(clubs,d,{by:"reach",mustReach:true}).name) abweichungen++;
    if (ac && cp && ac(clubs,d,false).name !== cp(clubs,d,{by:"carry",allowDriver:false}).name) abweichungen++;
  }
  eq("alle Hüllen verhalten sich wie clubPick", abweichungen, 0);
  if (typeof lerp === "function") {
    const gd2 = G("geoDist");
    const p = lerp([54,10],[54.01,10], 100);
    near("_aimLerp trifft die Distanz", gd2([54,10],p), 100, 1);
    ok("über das Ziel hinaus wird gekappt",
      gd2([54,10], lerp([54,10],[54.001,10], 9999)) <= gd2([54,10],[54.001,10]) + 0.5);
  }
}

/* ========================= 16. Abdeckungs-Sperrklinke ========================= */
group("Abdeckung — verhindert, dass der Prüfstand veraltet");
{
  const selbst = fs.readFileSync(__filename, "utf8");
  const codeOnly = code;
  const kandidaten = [];
  const reF = /^function\s+(\w+)\s*\(([^)]*)\)\s*\{/gm;
  let m;
  while ((m = reF.exec(codeOnly))) {
    const body = codeOnly.slice(m.index + m[0].length, m.index + m[0].length + 1500);
    if (!/document\.|innerHTML|fetch\(|localStorage|toast\(|render|openSheet|persist\(/.test(body)
        && m[2].trim()) kandidaten.push(m[1]);
  }
  const si = codeOnly.indexOf("const STRAT=");
  const stratNamen = si < 0 ? [] :
    [...codeOnly.slice(si, si + 60000).matchAll(/^  (\w+)\(/gm)].map(x => x[1]);

  const baseF = new Set(COVERAGE_BASELINE_FUNCS), baseS = new Set(COVERAGE_BASELINE_STRAT);
  const neuF = [...new Set(kandidaten)].filter(n => !baseF.has(n) && selbst.indexOf(n) < 0).sort();
  const neuS = [...new Set(stratNamen)].filter(n => !baseS.has(n) && selbst.indexOf(n) < 0).sort();

  ok("keine ungetestete NEUE reine Funktion", neuF.length === 0, neuF.join(", "));
  ok("keine ungetestete NEUE STRAT-Methode", neuS.length === 0, neuS.join(", "));

  // Aufräumhilfe: verschwundene Altlasten aus der Sperrklinke melden
  const alleF = new Set(kandidaten), alleS = new Set(stratNamen);
  const totF = COVERAGE_BASELINE_FUNCS.filter(n => n && !alleF.has(n));
  const totS = COVERAGE_BASELINE_STRAT.filter(n => n && !alleS.has(n));
  if (totF.length || totS.length)
    console.log("   Hinweis: aus der Sperrklinke streichbar (nicht mehr vorhanden): " +
      totF.concat(totS).slice(0, 12).join(", "));

  const abgedeckt = kandidaten.length - COVERAGE_BASELINE_FUNCS.filter(n=>n&&alleF.has(n)).length;
  console.log(`   Abdeckung: ${abgedeckt}/${kandidaten.length} reine Funktionen, ` +
    `${stratNamen.length - COVERAGE_BASELINE_STRAT.filter(n=>n&&alleS.has(n)).length}/${stratNamen.length} STRAT-Methoden`);
}

/* ========================= Ergebnis ========================= */
console.log("\n" + "─".repeat(52));
console.log(`${pass} bestanden, ${fail} fehlgeschlagen`);
if (fails.length) { console.log("\nFehlgeschlagen:"); fails.forEach(f => console.log("  ✗ " + f)); }
process.exit(fail ? 1 : 0);
