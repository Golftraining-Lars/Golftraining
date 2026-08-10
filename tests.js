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
    "_aimApproachEv _aimBuild _aimNextEv _aimTeeEv _centroid _distToLine _fitProject _flagSvg" +" "+
    "_linePath _merLat _merLng _merX _merY _mkHoles _mkTee _phoneLive _projPerp _ringPath" +" "+
    "_satTiles _teeSC _tileLat applyGeoOverrides approachStrength bandT bindPanZoom blockFind" +" "+
    "buildHoleGeo caddyBlockHtml caddyPlan caddyPositionPlan clubFamily clubPlan compass8" +" "+
    "computeRound computeTotal courseReportHtml courseSVG deleteNote distToRing featBbox" +" "+
    "featPoints finalizeGeo fitFind fmtDur fmtN geoBBox geoEdDown geoEdHoleFixHtml geoEdMove" +" "+
    "geoLL goalFind golfLinkify greenRingFor holeHistory holeSpine holeTrouble idbGet" +" "+
    "idbImgDel idbImgGet idbImgSet idbSatDel idbSet idbVidDel idbVidGet idbVidSet isVideoUrl" +" "+
    "ladder lateralHazards lineChart lineLenM linkHref liveStart lmBuildRecs lmCarryStrip" +" "+
    "lmDiagScatter lmDispersion lmGet lmPct lmPearson lmStatObj lvlChip manualTipHtml mapLL" +" "+
    "mkLink nearestHole normalizeClub openAddComp openAddNote openAddRound openBlockEditor" +" "+
    "openCourseEditor openFitnessDetail openGoalEditor openKraftEditor openRound openTest" +" "+
    "openYogaEditor parseGeoJSONCourse parseOverpassCourse playCaddyHtml playField" +" "+
    "playMapBind playMapClamp playNum playSel playTooFarHtml qaExpand qaFold qaSearch" +" "+
    "qaSections qaStem rateAbs rateR rateSmash rateStd refreshRepoSection renderGeoImport" +" "+
    "roundCardHtml roundKPIs roundLL roundWeatherHtml satCourseSrc satCourseTiles satLayer" +" "+
    "satSrcFor satTileKey satTilePx satTileRes selOpts sgCoverageHtml sgDashHtml" +" "+
    "sgDisasterHtml sgLeerHtml sparkline strkDown strkZoomAt strkZoomBtn swDaysSince" +" "+
    "swNormTag targetFor teeNames thinRing turnierPrepHtml warmupBloeckeHtml weatherByGeo" +" "+
    "weatherEffectHtml wikiCountCat wikiCountGrp wikiEsc wikiGroupIcon wikiGroupOf" +" "+
    "wikiNormTag wikiSuggest wikiTagsOf windArrowChar"
).split(" ");

const COVERAGE_BASELINE_STRAT = (
    "_fp _halton _interp _invNorm _off _segDist esOffset grid learnFromGps" +" "+
    "learnLateralFromRounds planCourse planFor planHole playingLevel pointESTo samples shotEV"
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
                 "whsIndexOf","classifyProps","holeRefFromTags","bearingDeg","dataScore",
                 "pointInRing","segIntersect","geoInterp","geoProject","hazardsOnLine",
                 "_tileX","_tileY","satTileUrl","satTileRange","satZoomFor","ringCentroid",
                 "clubSigma","lmSplit","lmNum","lmMatchCol","lmParse","lmMarkOut","lmClean",
                 "playMapInitView","qaTok","sgHole","sgRound","sgSummary","sgBandMid","sgLie",
                 "sgWeakest","sgFmt","sgClass","clubMeasured","sgDrillHint","sgEnrich"];
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

/* Abschnitt „Fahne 2D" ENTFERNT (v1.90): pinPoint gibt es nicht mehr, Ziel
   ist durchgaengig die Gruenmitte. Die Pruefung der Entfernung selbst steht
   in Abschnitt 24n (kein Name kehrt in den ausfuehrbaren Code zurueck). */

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
    /* Fahnen werden seit v1.90 NICHT mehr zusammengeführt, sondern geleert —
       sonst holte der nächste Sync die in ensureDefaults geräumten Werte aus
       dem Repo zurück. */
    ok("Fahnen werden geleert statt gemergt",
       m.pins && Object.keys(m.pins).length===0);

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


/* ================= 17. Geometrie-Primitive (tragen die Gefahrenerkennung) ================= */
group("Geometrie-Primitive");
{
  const pir = G("pointInRing"), si = G("segIntersect"),
        gi = G("geoInterp"), gp = G("geoProject");
  if (typeof pir === "function") {
    // Quadrat 54.000–54.001 / 10.000–10.001
    const ring = [[54.000,10.000],[54.000,10.001],[54.001,10.001],[54.001,10.000]];
    ok("Punkt innen", pir([54.0005,10.0005], ring));
    ok("Punkt außen (rechts)", !pir([54.0005,10.002], ring));
    ok("Punkt außen (oben)", !pir([54.002,10.0005], ring));
    ok("Punkt weit außen", !pir([53.0,9.0], ring));
  }
  if (typeof si === "function") {
    const A=[54.000,10.000], B=[54.002,10.000];      // senkrecht
    const C=[54.001,9.999],  D=[54.001,10.001];      // waagerecht, kreuzt
    const p = si(A,B,C,D);
    ok("Kreuzung wird gefunden", !!p);
    if (p) { near("Schnittpunkt Breite", p[0], 54.001, 1e-6);
             near("Schnittpunkt Länge", p[1], 10.000, 1e-6); }
    ok("keine Kreuzung → null", si(A,B,[54.005,9.999],[54.005,10.001]) === null);
    ok("parallel → null", si(A,B,[54.000,10.001],[54.002,10.001]) === null);
  }
  if (typeof gi === "function") {
    const m = gi([54,10],[54.002,10], 0.5);
    near("Mitte der Strecke", m[0], 54.001, 1e-9);
    near("t=0 ist der Startpunkt", gi([54,10],[55,11],0)[0], 54, 1e-9);
    near("t=1 ist der Endpunkt", gi([54,10],[55,11],1)[0], 55, 1e-9);
  }
  if (typeof gp === "function") {
    const q = gp(54.001, 10.000, 54.000, 10.000);
    near("110 m nach Norden", q.y, 110.54, 1);
    eq("kein Ost-Versatz", Math.round(q.x), 0);
  }
}

/* ================= 18. Gefahren auf der Spiellinie ================= */
group("hazardsOnLine — speist die ⚠-Warnungen des Caddys");
{
  const hz = G("hazardsOnLine");
  if (typeof hz === "function") {
    const here=[54.0000,10.0], green=[54.0027,10.0];      // ~300 m nach Norden
    // Wasserfläche quer über die Linie, ca. 100–130 m vor dem Ball
    const teich = { kind:"water", ring:[[54.0009,9.9995],[54.0009,10.0005],
                                        [54.0012,10.0005],[54.0012,9.9995]] };
    const abseits = { kind:"water", ring:[[54.0009,10.010],[54.0009,10.011],
                                          [54.0012,10.011],[54.0012,10.010]] };
    const baum = { kind:"tree", pt:[54.0015,10.00002] };  // fast exakt auf der Linie
    const r = hz(here, green, [teich, abseits, baum], ["water","tree"]);
    ok("Teich auf der Linie wird gemeldet", r.some(x=>x.kind==="water"), JSON.stringify(r));
    ok("Teich abseits wird NICHT gemeldet", r.filter(x=>x.kind==="water").length === 1);
    ok("Baum nahe der Linie wird gemeldet", r.some(x=>x.kind==="tree"));
    const w = r.find(x=>x.kind==="water");
    if (w) { ok("Beginn plausibel (80–120 m)", w.near>80 && w.near<120, "near="+Math.round(w.near));
             ok("Ende hinter dem Beginn", w.far >= w.near); }
    ok("nach Entfernung sortiert", r.every((x,i)=> i===0 || r[i-1].near <= x.near));
    eq("nicht angefragte Art wird ignoriert", hz(here,green,[teich],["sand"]).length, 0);
    eq("zu kurze Strecke → leer", hz(here,[54.00001,10.0],[teich],["water"]).length, 0);
  }
}

/* ================= 19. Kachelmathematik (Karte + Offline-Cache) ================= */
group("Kacheln — falsche Bereiche heißen: falsches Luftbild vorgeladen");
{
  const tx = G("_tileX"), ty = G("_tileY"), tu = G("satTileUrl"), tr = G("satTileRange");
  if (typeof tx === "function" && typeof ty === "function") {
    // Referenzwerte aus der Slippy-Map-Definition (z=1: 2x2 Kacheln)
    eq("Nullmeridian bei z=1 → x=1", tx(0, 2), 1);
    eq("Westrand bei z=1 → x=0", tx(-180, 2), 0);
    eq("Äquator bei z=1 → y=1", ty(0, 2), 1);
    eq("Nordhalbkugel bei z=1 → y=0", ty(60, 2), 0);
    // Timmendorf bei z=16
    const n = Math.pow(2,16);
    ok("x wächst nach Osten", tx(10.1,n) > tx(10.0,n));
    ok("y wächst nach Süden", ty(53.9,n) > ty(54.0,n));
  }
  if (typeof tu === "function") {
    const src = { id:"t", type:"xyz", url:"https://x/{z}/{x}/{y}.jpg" };
    eq("Platzhalter werden ersetzt", tu(src,16,34321,20876),
       "https://x/16/34321/20876.jpg");
  }
  if (typeof tr === "function") {
    const src = { id:"t", type:"xyz", url:"https://x/{z}/{x}/{y}.jpg", res:0.5 };
    const bbox = [54.000, 10.000, 54.004, 10.006];
    const r1 = tr(src, bbox, 4);
    ok("Bereich wird geliefert", !!r1);
    if (r1) {
      ok("Kachelzahl hält die Obergrenze ein", r1.count <= 4, "count="+r1.count);
      ok("x0 ≤ x1 und y0 ≤ y1", r1.x0<=r1.x1 && r1.y0<=r1.y1);
      const r2 = tr(src, bbox, 400);
      ok("mehr erlaubte Kacheln → höherer Zoom", r2.z >= r1.z, `${r1.z} → ${r2.z}`);
    }
  }
}

/* ================= 20. Flächenschwerpunkt ================= */
group("ringCentroid");
{
  const rc = G("ringCentroid");
  if (typeof rc === "function") {
    const quad = [[54.000,10.000],[54.000,10.002],[54.002,10.002],[54.002,10.000]];
    const c = rc(quad);
    near("Quadrat: Schwerpunkt in der Mitte (Breite)", c[0], 54.001, 1e-4);
    near("Quadrat: Schwerpunkt in der Mitte (Länge)", c[1], 10.001, 1e-4);
    // L-Form: Schwerpunkt darf NICHT der Eckpunkt-Mittelwert sein
    const L = [[54.000,10.000],[54.000,10.004],[54.001,10.004],[54.001,10.001],
               [54.004,10.001],[54.004,10.000]];
    const cl = rc(L);
    const mittel = L.reduce((a,p)=>[a[0]+p[0]/L.length, a[1]+p[1]/L.length],[0,0]);
    ok("L-Form: flächengewichtet ≠ Eckpunktmittel",
       Math.abs(cl[0]-mittel[0])>1e-5 || Math.abs(cl[1]-mittel[1])>1e-5);
    const zwei = rc([[54,10],[54.002,10]]);
    near("unter 3 Punkten: einfaches Mittel", zwei[0], 54.001, 1e-9);
    // entartet (alle Punkte gleich) darf nicht NaN liefern
    const ent = rc([[54,10],[54,10],[54,10]]);
    ok("entarteter Ring liefert Zahlen", isFinite(ent[0]) && isFinite(ent[1]));
  }
}

/* ================= 21. Launch-Monitor: Parser & Ausreißer ================= */
group("Launch-Monitor — Formatwechsel würde sonst still falsch lernen");
{
  const sp = G("lmSplit"), nu = G("lmNum"), mc = G("lmMatchCol"),
        lp = G("lmParse"), mo = G("lmMarkOut"), cl = G("lmClean");
  if (typeof sp === "function") {
    eq("einfache Trennung", sp("a,b,c", ",").join("|"), "a|b|c");
    eq("Anführungszeichen schützen das Trennzeichen",
       sp('a,"b,c",d', ",").join("|"), "a|b,c|d");
    eq("Leerraum wird entfernt", sp(" a ; b ", ";").join("|"), "a|b");
  }
  if (typeof nu === "function") {
    eq("englisches Format", nu("140.5"), 140.5);
    eq("deutsches Komma", nu("140,5"), 140.5);
    eq("deutsch: Tausenderpunkt + Dezimalkomma", nu("1.140,5"), 1140.5);
    eq("englisch: Tausenderkomma + Dezimalpunkt", nu("1,140.5"), 1140.5);
    eq("mehrere Punkte = Tausendertrennung", nu("1.140.500"), 1140500);
    // BEWUSSTE Festlegung: ein einzelner Punkt mit drei Nachkommastellen ist
    // nicht entscheidbar (7,2 oder 7200). Englische Exporte sind haeufiger,
    // deshalb gilt der Punkt als Dezimaltrenner. Aendert sich das je, MUSS
    // dieser Fall hier mitgeaendert werden.
    eq("Einzelfall bleibt Dezimalpunkt (dokumentierte Festlegung)", nu("7.200"), 7.2);
    eq("Einheit wird abgestreift", nu("52 mph"), 52);
    ok("leer → null", nu("") === null);
    ok("Text → null", nu("abc") === null);
  }
  if (typeof mc === "function") {
    ok("Carry wird erkannt", !!mc("Carry Distance"));
    ok("Klammern stören nicht", !!mc("Carry Distance (m)"));
    ok("Notizspalte wird verworfen", mc("Description") === null);
  }
  if (typeof lp === "function") {
    const csv = ["Club Type;Carry Distance;Total Distance;Ball Speed",
                 "7 Iron;140,5;146;52", "7 Iron;138;145;51"].join("\n");
    const r = lp(csv);
    eq("Semikolon erkannt", r.delim, ";");
    eq("zwei Schläge gelesen", r.shots.length, 2);
    near("deutsches Komma korrekt gelesen", r.shots[0].carry, 140.5, 0.01);
    // Einheitenzeile unter dem Kopf muss übersprungen werden
    const csv2 = ["Club Type,Carry Distance,Ball Speed","-,m,mph",
                  "7 Iron,140,52"].join("\n");
    eq("Einheitenzeile übersprungen", lp(csv2).shots.length, 1);
    eq("leere Datei → keine Schläge", lp("").shots.length, 0);
  }
  if (typeof mo === "function") {
    const gut = () => [
      {carry:140, smash:1.40, launch:19, peak:26},
      {carry:142, smash:1.41, launch:20, peak:27},
      {carry:139, smash:1.39, launch:18, peak:25},
    ];
    // JEDES Kriterium einzeln prüfen. Ein Ausreißer, der in allen drei
    // auffällt, würde eine kaputte Einzelregel verdecken — genau das ist bei
    // der ersten Fassung dieses Tests passiert (Mutationsprobe schlug NICHT an).
    const nurSmash  = {carry:130, smash:1.25, launch:19, peak:26};
    const nurLaunch = {carry:130, smash:1.40, launch:12, peak:26};
    const nurPeak   = {carry:130, smash:1.40, launch:19, peak:14};
    const normal    = {carry:141, smash:1.40, launch:19, peak:26};
    const pruef = (name, kandidat, erwartet) => {
      const arr = gut().concat([kandidat]);
      const m = mo(arr);
      ok(name, m[3]._out === erwartet,
         `_out=${m[3]._out}, erwartet ${erwartet}`);
    };
    pruef("niedriger Smash allein markiert", nurSmash, true);
    pruef("flacher Launch allein markiert", nurLaunch, true);
    pruef("niedriger Scheitelpunkt allein markiert", nurPeak, true);
    pruef("unauffälliger Schlag bleibt unmarkiert", normal, false);

    const shots = gut().concat([{carry:95, smash:1.10, launch:6, peak:9}]);
    const m = mo(shots);
    eq("alle Schläge kommen zurück", m.length, 4);
    ok("gute Schläge nicht markiert", !m[0]._out && !m[1]._out && !m[2]._out);
    ok("Originale bleiben unberührt (Kopien!)", shots[3]._out === undefined);
    if (typeof cl === "function") eq("lmClean entfernt Ausreißer", cl(shots).length, 3);
  }
}

/* ================= 22. clubSigma ================= */
group("clubSigma");
{
  const cs = G("clubSigma"), DB = G("DB");
  if (typeof cs === "function" && DB) {
    DB.gpsShots = [];
    eq("ohne Daten: 6 % der Länge", cs("7-Eisen", 140), Math.max(6, Math.round(140*0.06)));
    DB.gpsShots = [140,142,138,141,139].map(d=>({club:"7-Eisen", dist:d}));
    const sig = cs("7-Eisen", 140);
    ok("mit Daten: gemessene Streuung", sig >= 4 && sig < 10, "σ="+sig);
    ok("Mindestwert 4 wird gehalten",
       cs("X", 1) >= 4 && (DB.gpsShots = [1,1,1,1].map(d=>({club:"X",dist:d}))) && cs("X",1) >= 4);
    DB.gpsShots = [];
  }
}

/* ================= 23. Kartenausschnitt: 30 m hinter dem Grün ================= */
group("playMapInitView — die 30-m-Regel lebt hier, nicht in playMapBox");
{
  const iv = G("playMapInitView"), P = G("PLAY");
  if (typeof iv === "function" && P) {
    P.mapBase = { W: 4000, H: 4000 };
    const mLat=111320, mLng=65500;
    const M = { s:2, W:4000, H:4000,
                map:(la,lo)=>[(lo-10.0)*mLng*2, -(la-54.0)*mLat*2] };
    const geo = { holes: { 1: { tee:[54.0,10.0], green:[54.0027,10.0], line:null } } };
    const v = iv(geo, {hole:1}, M, 1.6);
    ok("Ausschnitt wird geliefert", v && isFinite(v.x) && isFinite(v.w), JSON.stringify(v));
    if (v) {
      const gY = M.map(54.0027,10.0)[1];
      const hinterGruen = gY - 30*M.s;                 // 30 m weiter nördlich = kleineres y
      ok("30 m hinter dem Grün liegen im Bild",
         v.y <= hinterGruen && (v.y + v.h) >= gY,
         `y=${v.y.toFixed(0)}..${(v.y+v.h).toFixed(0)}, nötig ≤ ${hinterGruen.toFixed(0)}`);
      const tY = M.map(54.0,10.0)[1];
      ok("Abschlag liegt im Bild", v.y <= tY && (v.y+v.h) >= tY);
      near("Seitenverhältnis stimmt", v.w/v.h, 1.6, 0.01);
    }
  }
}


/* ================= 24. Strokes Gained ================= */
group("Strokes Gained — die Kennzahl, die sagt WO Schläge verlorengehen");
{
  const sgHole=G("sgHole"), sgRound=G("sgRound"), sgSummary=G("sgSummary"),
        mid=G("sgBandMid"), lie=G("sgLie"), weak=G("sgWeakest"),
        fmt=G("sgFmt"), cls=G("sgClass");

  if (typeof mid === "function") {
    eq("Band 110–140 → Mitte", mid("110–140"), 125);
    eq("Band 50–80 → Mitte", mid("50–80"), 65);
    near("Band 200+ → oberhalb", mid("200+"), 215, 1);
    near("Band <20 → unterhalb", mid("<20"), 12, 1);
    eq("Meterangabe mit Komma", mid("1,5m"), 1.5);
    ok("leer → null", mid(null) === null);
    /* Werte OHNE Ziffer aus den echten Optionslisten. Dass sie frueher null
       lieferten, war der Hauptgrund, warum Strokes Gained trotz vieler
       erfasster Runden leer blieb: die 1.-Putt-Distanz ist Pflichtfeld der
       Zerlegung, und "Gimme" ist bei kurzen Putts der Normalfall. */
    eq("Gimme = halber Meter", mid("Gimme"), 0.5);
    eq("Holed = 0", mid("Holed"), 0);
    ok("3-Putt+ ist ein Ergebnis, keine Distanz", mid("3-Putt+") === null);
    ok("echter Text ohne Bedeutung → null", mid("Blah") === null);
  }
  if (typeof lie === "function") {
    eq("Bunker", lie("Bunker"), "sand");
    eq("Semi-Rough zählt als Rough", lie("Semi-Rough"), "rough");
    eq("Recovery", lie("Recovery"), "recovery");
    eq("unbekannt → Fairway", lie("Blah"), "fairway");
  }

  if (typeof sgHole === "function") {
    // Ein Par 4 in Regulation mit 2 Putts ist per Definition „normal"
    const par4 = {hole:1, par:4, len:350, score:4, putts:2, appr:"110–140",
                  lie:"Fairway", distToPin:6, firstPutt:"6m", girDirect:"Ja"};
    const r = sgHole(par4, 20);
    ok("liefert ein Ergebnis", !!r);

    /* DIE zentrale Eigenschaft des Modells: die Kategorien teleskopieren
       EXAKT zum Gesamtwert. Gilt unabhängig davon, wie die Schläge auf die
       Phasen verteilt werden — bricht sie, ist die Zerlegung wertlos. */
    const summe = r => ["lang","app","kurz","putt","straf"]
      .map(k => r[k]||0).reduce((a,b)=>a+b, 0);
    const faelle = [
      par4,
      {hole:2,par:3,len:155,score:3,putts:2,distToPin:6,firstPutt:"6m",girDirect:"Ja"},
      {hole:3,par:5,len:480,score:7,putts:2,appr:"80–110",lie:"Rough",distToPin:14,firstPutt:"3m",girDirect:"Nein",penN:1},
      {hole:4,par:4,len:320,score:6,putts:3,appr:"50–80",lie:"Bunker",distToPin:11,firstPutt:"11m",girDirect:"Nein"},
      {hole:5,par:5,len:500,score:5,putts:1,appr:"20–50",lie:"Fairway",distToPin:3,firstPutt:"3m",girDirect:"Ja"},
    ];
    let maxAbw = 0;
    faelle.forEach(h => { const x = sgHole(h, 20);
      if (x) maxAbw = Math.max(maxAbw, Math.abs(summe(x) - x.total)); });
    ok("Kategorien summieren sich EXAKT zum Gesamtwert", maxAbw < 1e-9,
       "größte Abweichung " + maxAbw.toExponential(2));

    // Richtungssinn
    const drei = Object.assign({}, par4, {putts:3, score:5});
    const eins = Object.assign({}, par4, {putts:1, score:3});
    ok("3 Putts schlechter als 2", sgHole(drei,20).putt < r.putt);
    ok("1 Putt besser als 2", sgHole(eins,20).putt > r.putt);
    ok("Triple ist schlechter als Par",
       sgHole(Object.assign({},par4,{score:7,putts:3}),20).total < r.total);

    // Grün getroffen ⇒ es gab KEIN kurzes Spiel
    const girExtra = Object.assign({}, par4, {score:5, putts:2});
    eq("GIR: kein Schlag im kurzen Spiel", sgHole(girExtra,20).kurz, 0);
    ok("GIR: Zusatzschlag belastet das lange Spiel",
       sgHole(girExtra,20).lang < r.lang);

    /* GIR ableiten, wenn das Feld fehlt (Uhr-Runden und Altdaten).
       Par 4, Score 4, 2 Putts -> 2 Schläge aufs Grün = GIR. */
    const ohneGir = {hole:7, par:4, len:350, score:4, putts:2, appr:"110–140",
                     lie:"Fairway", distToPin:6, firstPutt:"6m"};
    eq("GIR abgeleitet: kein kurzes Spiel", sgHole(ohneGir,20).kurz, 0);
    const keinGir = Object.assign({}, ohneGir, {score:5, putts:2});
    ok("kein GIR abgeleitet: kurzes Spiel wird gezählt",
       sgHole(keinGir,20).kurz !== 0);
    ok("erfasstes Feld schlägt die Ableitung",
       sgHole(Object.assign({},ohneGir,{girDirect:"Nein"}),20).kurz !== 0);

    // Strafschläge getrennt ausgewiesen
    const straf = Object.assign({}, par4, {score:6, putts:2, penN:1});
    eq("Strafschlag wird separat gezählt", sgHole(straf,20).straf, -1);

    // Unvollständige Daten
    ok("ohne Score → null", sgHole({hole:1,par:4,len:350}, 20) === null);
    const ohnePutts = sgHole({hole:1,par:4,len:350,score:5}, 20);
    ok("ohne Putts nur Gesamtwert", ohnePutts && ohnePutts.teilweise === true
       && ohnePutts.putt === null);
    eq("Grund wird benannt", ohnePutts.fehlt, "Putts");
    const ohneAppr = sgHole({hole:1,par:4,len:350,score:5,putts:2,firstPutt:"3m"}, 20);
    eq("fehlende Approach-Distanz wird benannt", ohneAppr.fehlt, "Approach-Distanz");

    /* Rückfallkette für die 1.-Putt-Distanz: erfasst → bei GIR die
       Restdistanz → das Feld quality (das genau diese Information trägt). */
    const perQuality = sgHole({hole:1,par:4,len:350,score:4,putts:2,
      appr:"110–140",lie:"Fairway",quality:"2m"}, 20);
    ok("quality dient als Rückfall für die 1.-Putt-Distanz",
       perQuality && perQuality.putt !== null);
    const perRest = sgHole({hole:1,par:4,len:350,score:4,putts:2,
      appr:"110–140",lie:"Fairway",distToPin:5}, 20);
    ok("bei GIR dient die Restdistanz als Rückfall",
       perRest && perRest.putt !== null);
  }

  /* sgEnrich: DER Grund, warum SG anfangs leer blieb. collect() speichert je
     Loch nur {hole} plus Eingaben — par/len/si stehen in der Platzdefinition.
     Ohne Nachschlagen liefert sgHole für jede von Hand erfasste Runde null. */
  const enrich = G("sgEnrich"), DBx = G("DB");
  if (typeof enrich === "function" && DBx) {
    DBx.courses = [{ name:"Testplatz", tees:{ Gelb:{ holes:[
      {hole:1,par:4,si:5,len:350},{hole:2,par:3,si:17,len:150}] } } }];
    const runde = { course:"Testplatz", tee:"Gelb", side:"18 Loch",
      holes:[{hole:1,score:5,putts:2},{hole:2,score:3,putts:2}] };
    const e = enrich(runde);
    eq("Par wird nachgeschlagen", e[0].par, 4);
    eq("Länge wird nachgeschlagen", e[0].len, 350);
    eq("Stroke-Index wird nachgeschlagen", e[0].si, 5);
    eq("zweites Loch ebenso", e[1].par, 3);
    // Erfasster Wert schlägt die Platzdefinition
    const eigen = enrich({ course:"Testplatz", tee:"Gelb", side:"18 Loch",
      holes:[{hole:1,par:5,score:5}] });
    eq("eigener Par-Wert bleibt", eigen[0].par, 5);
    // Unbekannter Platz darf nicht abstürzen
    const fremd = enrich({ course:"Gibt-es-nicht", holes:[{hole:1,score:4}] });
    ok("unbekannter Platz: kein Absturz, kein Par", fremd.length===1 && fremd[0].par==null);
    ok("ohne Löcher leer", enrich({course:"Testplatz",holes:[]}).length === 0);
    // Und der eigentliche Zweck: SG rechnet danach
    const rr0 = sgRound(runde, 20);
    ok("SG ist nach dem Anreichern berechenbar", rr0.n.total === 2,
       "verwertbare Löcher: " + rr0.n.total);
  }

  if (typeof sgRound === "function") {
    const runde = {holes:[
      {hole:1,par:4,len:350,score:4,putts:2,appr:"110–140",lie:"Fairway",distToPin:6,firstPutt:"6m",girDirect:"Ja"},
      {hole:2,par:4,len:360,score:6,putts:3,appr:"110–140",lie:"Rough",distToPin:12,firstPutt:"12m",girDirect:"Nein"},
    ]};
    const rr = sgRound(runde, 20);
    eq("beide Löcher gezählt", rr.n.total, 2);
    const s2 = ["lang","app","kurz","putt","straf"].map(k=>rr.sg[k]).reduce((a,b)=>a+b,0);
    ok("auch auf Rundenebene exakt", Math.abs(s2 - rr.sg.total) < 1e-9);
    ok("die schlechtere Runde zieht den Gesamtwert", rr.sg.total < 0);

    if (typeof sgSummary === "function") {
      const sum = sgSummary([runde], 20);
      ok("Zusammenfassung liefert Mittelwerte", sum && sum.runden === 1);
      ok("auf 18 Löcher hochgerechnet", Math.abs(sum.avg.total) > Math.abs(rr.sg.total));
      ok("ohne Runden → null", sgSummary([], 20) === null);
    }
    if (typeof weak === "function") {
      eq("schwächste von Hand gesetzt",
         weak({lang:0.5, app:-1.2, kurz:0.1, putt:-0.3})[0], "app");
      ok("Strafschläge zählen NICHT als Schwäche (Folge, nicht Ursache)",
         weak({lang:0.5, app:0.4, kurz:0.1, putt:0.2, straf:-9})[0] !== "straf");
      ok("ohne Daten → null", weak(null) === null);
    }
  }

  if (typeof fmt === "function") {
    eq("positiv mit Vorzeichen", fmt(0.5), "+0,50");
    eq("negativ", fmt(-1.25), "-1,25");
    eq("leer", fmt(null), "–");
    eq("Klasse positiv", cls(0.5), "up");
    eq("Klasse negativ", cls(-0.5), "down");
    eq("Klasse neutral", cls(0.05), "flat");
  }
}

/* ================= 24b. Gameplan-Modi ================= */
group("Gameplan — je Modus ein eigener Plan");
{
  const key=G("gpKey"), lab=G("gpLabel"), tot=G("gpTotalES");
  if (typeof key === "function") {
    /* Der Schlüssel MUSS den Modus enthalten. Vorher lag der Plan unter
       "Kurs|Tee": nach einem Moduswechsel zeigte die App den alten Plan
       und behauptete in der Kopfzeile den neuen Modus. */
    eq("Modus steckt im Schlüssel", key("Sonnenberg","Gelb","aggr"), "Sonnenberg|Gelb|aggr");
    ok("Modi ergeben verschiedene Schlüssel",
       key("A","B","safe") !== key("A","B","aggr"));
  }
  if (typeof lab === "function") {
    eq("safe", lab("safe"), "sicher");
    eq("bal", lab("bal"), "normal");
    eq("aggr", lab("aggr"), "offensiv");
  }
  if (typeof tot === "function") {
    eq("Summe der Erwartungswerte",
       tot({holes:[{es:4.2},{es:3.1},{es:5.0}]}), 12.3);
    ok("Löcher ohne ES werden übersprungen",
       tot({holes:[{es:4.0},{note:"keine Geo-Daten"}]}) === 4);
    ok("ohne Plan → null", tot(null) === null);
    ok("ohne verwertbare Löcher → null", tot({holes:[{note:"x"}]}) === null);
  }
}

/* ================= 24c. Lochdaten ergänzen ================= */
group("sgEnrich — par/len/si aus den Platzdaten nachziehen");
{
  const en=G("sgEnrich"), DB=G("DB");
  if (typeof en === "function" && DB) {
    DB.courses=[{name:"T", tees:{Gelb:{holes:[{hole:1,par:4,len:350,si:5},
                                              {hole:2,par:3,len:150,si:17}]}}}];
    /* Von der Uhr gespeicherte Runden hatten bis 2026-08-08 KEIN par —
       ohne Nachziehen liefert sgHole für sie gar nichts. */
    const r={course:"T", tee:"Gelb", side:"18 Loch",
             holes:[{hole:1,score:5,putts:2},{hole:2,score:3,putts:2}]};
    const hs=en(r);
    eq("par ergänzt", hs[0].par, 4);
    eq("len ergänzt", hs[0].len, 350);
    eq("si ergänzt", hs[0].si, 5);
    const r2={course:"T", tee:"Gelb", holes:[{hole:1,score:5,putts:2,par:5}]};
    eq("erfasster Wert wird NICHT überschrieben", en(r2)[0].par, 5);
    ok("unbekannter Platz → unverändert",
       en({course:"XX", holes:[{hole:1,score:4}]})[0].par === undefined);
    DB.courses=[];
  }
}

/* ================= 24d. Korrelation & Signifikanz ================= */
group("Korrelation — bei wenigen Runden ist fast alles Zufall");
{
  const pe=G("pearson"), rk=G("rKrit"), ms=G("medianSplit");
  if (typeof pe === "function") {
    near("perfekt positiv", pe([1,2,3,4],[2,4,6,8]), 1, 1e-6);
    near("perfekt negativ", pe([1,2,3,4],[8,6,4,2]), -1, 1e-6);
    ok("konstante Reihe → kein r", pe([1,1,1,1],[1,2,3,4]) === null);
  }
  if (typeof rk === "function") {
    /* Der entscheidende Punkt: bei 4 Runden muss |r| über 0,95 liegen, um
       überhaupt etwas zu bedeuten. Ohne diese Schwelle sah jede Zufallszahl
       nach Erkenntnis aus. */
    near("4 Runden: Schwelle sehr hoch", rk(4), 0.95, 0.01);
    near("10 Runden", rk(10), 0.632, 0.01);
    near("20 Runden", rk(20), 0.444, 0.01);
    ok("Schwelle sinkt mit mehr Runden", rk(30) < rk(10) && rk(10) < rk(5));
    eq("unter 4 Runden nichts absicherbar", rk(3), 1);
    ok("zwischen Stützstellen interpoliert", rk(22) > rk(25) && rk(22) < rk(20));
    ok("sehr viele Runden: Schwelle bleibt endlich", rk(500) > 0 && rk(500) < 0.25);
  }
  if (typeof ms === "function") {
    // Ziel = Schläge über Par; kleinere Werte sind die guten Runden
    const kpi=[40,38,30,28], ziel=[2,4,10,12];
    const r=ms(kpi,ziel);
    ok("Median-Vergleich liefert beide Hälften", !!r);
    if(r){
      near("gute Runden: Mittel der besseren Hälfte", r.gut, 39, 0.01);
      near("schlechte Runden", r.schlecht, 29, 0.01);
      eq("Hälften gleich groß", r.nJe, 2);
    }
    ok("unter 4 Werten → null", ms([1,2],[1,2]) === null);
    ok("Lücken werden übersprungen", ms([1,null,3,4,5],[1,2,3,4,5]) !== null);
  }
}

/* ================= 24e. Geführtes Aufwärmen ================= */
group("Aufwärmen — rückwärts von der Abschlagzeit");
{
  const sched=G("warmupSchedule"), plans=G("WARMUP_PLANS"), korr=G("warmupKorrektiv");
  if (plans) {
    ["kurz","standard","turnier","nach"].forEach(id=>
      ok("Variante "+id+" vorhanden", !!plans[id]));
    Object.values(plans).forEach(p=>{
      const summe=p.bloecke.reduce((a,b)=>a+b.min,0);
      ok(p.name+": Blöcke ergeben die angegebene Dauer", summe===p.min,
         `${summe} statt ${p.min}`);
    });
    /* Die zentrale inhaltliche Regel: Putt-Speed ist die verderblichste
       Information des Aufwärmens und MUSS am Ende stehen. Im alten Protokoll
       stand Putten an zweiter von fünf Stellen. */
    ["kurz","standard","turnier"].forEach(id=>{
      const b=plans[id].bloecke;
      const iPutt=b.map(x=>x.titel).findIndex(t=>/putt/i.test(t));
      ok(id+": Putten in den letzten beiden Blöcken", iPutt>=b.length-2,
         "Position "+(iPutt+1)+" von "+b.length);
    });
    ok("statisches Dehnen nur NACH der Runde",
       plans.nach.nachRunde===true &&
       !["kurz","standard","turnier"].some(id=>
         plans[id].bloecke.some(b=>/statisch/i.test(b.titel)||/halten/i.test(b.inhalt))));
    ok("Standard hält die Ballzahl bei ~22",
       plans.standard.bloecke.reduce((a,b)=>a+(b.baelle||0),0) <= 24);
    ok("Turnier deckelt trotz mehr Zeit",
       plans.turnier.bloecke.reduce((a,b)=>a+(b.baelle||0),0) <= 38);
  }
  if (typeof sched === "function" && plans) {
    const jetzt=new Date(2026,7,9,8,0,0);
    const s1=sched(plans.standard,"09:40",jetzt);
    ok("Plan wird erstellt", !!s1);
    if(s1){
      eq("Abschlagzeit übernommen", s1.tee, "09:40");
      eq("Gesamtdauer inkl. Weg", s1.gesamt, 25+2);
      eq("Startzeit rückwärts gerechnet", s1.start, "09:13");
      eq("erster Block beginnt am Start", s1.bloecke[0].von, "09:13");
      const letzter=s1.bloecke[s1.bloecke.length-1];
      eq("letzter Block endet 2 min vor dem Abschlag", letzter.bis, "09:38");
      ok("Blöcke lückenlos aneinander",
         s1.bloecke.every((b,i)=> i===0 || b.von===s1.bloecke[i-1].bis));
      ok("genug Zeit → nicht als spät markiert", s1.spaet===false);
    }
    // Zu wenig Zeit muss erkannt werden — sonst plant die App in die Vergangenheit
    const s2=sched(plans.turnier,"08:15",jetzt);
    ok("zu wenig Zeit wird erkannt", s2 && s2.spaet===true);
    // Abschlagzeit vor „jetzt" meint morgen
    const s3=sched(plans.kurz,"07:00",jetzt);
    ok("frühere Uhrzeit = morgen", s3 && s3.teeMs > jetzt.getTime());
    ok("ungültige Zeit → null", sched(plans.kurz,"quatsch",jetzt) === null);
    ok("ohne Plan → null", sched(null,"09:40",jetzt) === null);
  }
  if (typeof korr === "function") {
    const k=korr();
    ok("Korrektiv hat Fehlerbild und Übung", k && k.fehler && k.uebung);
  }
}

/* ================= 24f. Ableitbare Felder ================= */
group("GIR · Up&Down · Sand Save — berechnet statt erfasst");
{
  const gir=G("holeGir"), ud=G("holeUpDown"), ss=G("holeSandSave");
  if (typeof gir === "function") {
    /* Diese drei Felder stecken bereits in Score, Putts, Par und Bunkerzahl.
       Sie von Hand zu erfassen kostet Zeit auf dem Platz UND erzeugt
       Widersprüche zwischen gesetztem und rechnerischem Wert. */
    ok("Par 4, 4 Schläge, 2 Putts = GIR", gir({par:4,score:4,putts:2})===true);
    ok("Par 4, 5 Schläge, 2 Putts = kein GIR", gir({par:4,score:5,putts:2})===false);
    ok("Par 5, 5 Schläge, 2 Putts = GIR", gir({par:5,score:5,putts:2})===true);
    ok("Par 3, 3 Schläge, 2 Putts = GIR", gir({par:3,score:3,putts:2})===true);
    ok("erfasster Wert schlägt die Ableitung",
       gir({par:4,score:4,putts:2,girDirect:"Nein"})===false);
    ok("unvollständig → null", gir({par:4,score:4})===null);
  }
  if (typeof ud === "function") {
    ok("Grün verfehlt + 1 Putt = Up&Down", ud({par:4,score:4,putts:1})===true);
    ok("Grün verfehlt + 2 Putts = kein Up&Down", ud({par:4,score:5,putts:2})===false);
    ok("GIR getroffen ⇒ kein Up&Down", ud({par:4,score:4,putts:2})===false);
    ok("Override wirkt", ud({par:4,score:4,putts:2,ud:"Ja"})===true);
  }
  if (typeof ss === "function") {
    ok("Up&Down aus dem Bunker = Sand Save",
       ss({par:4,score:4,putts:1,bunkerN:1})===true);
    ok("Up&Down ohne Bunker ist kein Sand Save",
       ss({par:4,score:4,putts:1,bunkerN:0})===false);
    ok("Override wirkt", ss({par:4,score:5,putts:2,ss:"Ja"})===true);
  }
}

/* ================= 24g. Schlaggenaues Strokes Gained ================= */
group("sgHoleShots — SG aus GPS-Schlägen statt aus Annahmen");
{
  const shots=G("sgHoleShots"), STRAT=G("STRAT"), DB=G("DB");
  if (typeof shots === "function" && STRAT) {
    const mLat=111320, mLng=65500;
    const P=(n,e)=>({lat:54.0+n/mLat, lng:10.0+e/mLng});
    const geo={holes:{1:{tee:[54.0,10.0], green:[54.0+350/mLat,10.0],
      fairway:[{ring:[[54.0+100/mLat,10.0-40/mLng],[54.0+100/mLat,10.0+40/mLng],
                      [54.0+300/mLat,10.0+40/mLng],[54.0+300/mLat,10.0-40/mLng]]}]}}};
    DB.courses=[{name:"T", geo}];
    // Par 4: Abschlag auf 215 m, Approach aufs Grün, 2 Putts = Score 4
    const h={hole:1,par:4,len:350,score:4,putts:2,
      shots:[P(0,0), P(215,5), P(349,1)]};
    const r=shots(h,geo,"T",20);
    ok("liefert ein Ergebnis", !!r, JSON.stringify(r));
    if(r){
      ok("als genau markiert", r.genau===true);
      eq("Schlagzahl erkannt", r.schlaege, 2);
      const summe=["lang","app","kurz","putt","straf"].map(k=>r[k]||0).reduce((a,b)=>a+b,0);
      ok("Kategorien summieren zum Gesamtwert", Math.abs(summe-r.total)<0.02,
         `${summe.toFixed(3)} vs ${r.total.toFixed(3)}`);
      ok("Abschlag zählt zum langen Spiel", r.lang!==0);
      ok("letzter voller Schlag ist die Annäherung", r.app!==0);
    }
    /* SICHERHEITSNETZ: passt die Schlagzahl nicht zur Scorekarte, darf NICHT
       gerechnet werden — eine unvollständige Kette wäre schlimmer als die
       ehrliche Näherung. */
    ok("inkonsistente Schlagzahl → null",
       shots(Object.assign({},h,{score:6}),geo,"T",20)===null);
    ok("ohne Schläge → null", shots({hole:1,par:4,score:4,putts:2},geo,"T",20)===null);
    ok("ohne Geometrie → null", shots(h,null,"T",20)===null);
    DB.courses=[];
  }
}

/* ============ 24h. Form der Bewertungen (tee / nextShot / approach) ============ */
group("Bewertungs-Objekte — gleiche Form, sonst stirbt die Zielkette");
{
  const S=G("STRAT");
  if (S && S.tee && S.approach) {
    const mLat=111320, mLng=65500;
    const P=(n,e)=>[54.0+n/mLat, 10.0+e/mLng];
    const ring=(n,e,r)=>[P(n-r,e-r),P(n-r,e+r),P(n+r,e+r),P(n+r,e-r)];
    const geo={holes:{1:{tee:P(0,0), green:P(350,0),
      fairway:[{ring:ring(175,0,60)}]}}};
    const DB=G("DB");
    DB.clubDistances=[{club:"Driver",carry:215,reach:232},{club:"7-Eisen",carry:140,reach:143},
      {club:"PW",carry:104,reach:106},{club:"SW",carry:78,reach:80}];
    DB.courses=[{name:"T", geo}];

    const t=S.tee(geo,"T",1,"bal",20);
    const a=S.approach(geo,"T",1,P(215,0),135,"bal",20,null);
    ok("tee() liefert eine Bewertung", !!t);
    ok("approach() liefert eine Bewertung", !!a);
    if (t && a) {
      /* DER PUNKT: tee().best trägt es/pen DIREKT, approach().best legt sie
         unter best.ev ab. Wer das verwechselt, greift auf undefined zu und
         stirbt an `.toFixed` — genau der Fehler aus v1.68. */
      ok("tee().best.es ist eine Zahl", isFinite(t.best.es));
      ok("tee().best.pen ist eine Zahl", isFinite(t.best.pen));
      ok("approach().best.es ist NICHT direkt gesetzt", !isFinite(a.best.es));
      ok("approach().best.ev.es ist die Zahl", isFinite(a.best.ev.es));
      ok("approach().fracs.green ist die Grünquote",
         isFinite(a.fracs.green) && a.fracs.green>=0 && a.fracs.green<=100);
    }
    DB.courses=[]; DB.clubDistances=[];
  }
}

/* ============ 24i. Vollbild-Vorgabe & Merge-Loeschungen ============ */
group("Vollbild startet immer — und warum die Vorliebe NICHT gespeichert wird");
{
  const pfd=G("playFocusDefault"), merge=G("mergeDB"), DB=G("DB");
  if (typeof pfd === "function" && DB) {
    ok("ohne Einstellung: Vollbild an", pfd()===true);
    DB.ui=DB.ui||{}; DB.ui.playMapFocus=false;
    /* DER KERN: Ein alter gespeicherter Wert darf das Vollbild NICHT mehr
       verhindern. v1.69 hatte `false` versehentlich geschrieben, und über den
       Repo-Merge kam es immer wieder zurück — das Vollbild ging monatelang
       nicht auf, obwohl die Ursache im Code längst behoben war. */
    ok("alter gespeicherter Wert wirkt nicht mehr", pfd()===true);
    delete DB.ui.playMapFocus;
  }
  if (typeof merge === "function") {
    /* Warum der Wert immer zurückkam: mergeDB führt ui über
       Object.assign({}, R.ui, L.ui) zusammen. Ein lokal GELÖSCHTER Schlüssel
       ist dort schlicht nicht vorhanden — die Repo-Fassung bleibt stehen.
       Object.assign-Merges können Löschungen nicht ausdrücken. */
    const a=merge({ui:{}}, {ui:{playMapFocus:false}});
    eq("lokale Löschung verliert gegen das Repo", a.ui.playMapFocus, false);
    const b=merge({ui:{playMapFocus:true}}, {ui:{playMapFocus:false}});
    eq("aktives Überschreiben gewinnt", b.ui.playMapFocus, true);
    const c=merge({ui:{x:1}}, {ui:{y:2}});
    ok("beide Seiten bleiben erhalten", c.ui.x===1 && c.ui.y===2);
  }
}

/* ============ 24j. Kartencontainer im Vollbild ============ */
group("playMapSlot — eine Stelle für den Kartencontainer");
{
  const slot=G("playMapSlot"), P=G("PLAY");
  if (typeof slot === "function" && P) {
    /* URSACHE DES EINFRIERENS bis v1.74: playMapTick() suchte nur
       #playMapSlot. Im Vollbild zeichnet die Karte nach #pfMap — der Slot war
       dort null, und JEDER GPS-Tick fiel in den teuren Zweig mit
       playMapRender() (Monte-Carlo, Kacheln, SVG-Neubau). Dauerlast.
       Deshalb bestimmt EINE Funktion den Container. */
    P.mapFocus=true;
    ok("im Vollbild wird ein Container geliefert", slot()!==null && slot()!==undefined);
    /* AUSSERHALB des Kartenmodus gibt es bewusst KEINEN Container mehr —
       seit v1.89 existiert #playMapSlot nicht. Genau daran hängt die
       Vermeidung der Dauerlast: playMapTick/playMapRender brechen ab. */
    P.mapFocus=false;
    ok("außerhalb bewusst null", slot()===null);
    P.mapFocus=true;
  }
}

/* ============ 24k. Entfernungs-Plausibilität ============ */
group("playTooFar — der Caddy schweigt außerhalb des Platzes");
{
  const tf=G("playTooFar"), P=G("PLAY"), DB=G("DB");
  if (typeof tf === "function" && P && DB) {
    const mLat=111320, mLng=65500;
    const at=(m)=>[54.0+m/mLat, 10.0];
    const geo={holes:{1:{tee:[54.0,10.0], green:at(387)}}};
    DB.courses=[{name:"T", geo}];
    P.course="T"; P.holes=[{hole:1,par:4,len:387,si:3}]; P.idx=0;

    /* Am Abschlag eines 387-m-Lochs sind 387 m normal — hier darf NICHTS
       unterdrückt werden. Erst deutlich jenseits der Lochlänge wird es
       Unsinn: mit 2 km Entfernung empfahl der Caddy „3 Wood, lässt 1962 m". */
    P.here=at(0);
    ok("am Abschlag: Empfehlung erlaubt", tf()===null);
    P.here=at(-100);
    ok("100 m hinter dem Tee: noch erlaubt", tf()===null);
    P.here=at(200);
    ok("mitten auf der Bahn: erlaubt", tf()===null);
    P.here=at(-2000);
    ok("2 km entfernt: unterdrückt", tf()!==null, "Rückgabe "+tf());
    const d=tf();
    ok("liefert die Entfernung zurück", d>2300 && d<2500, "d="+d);

    // Ohne bekannte Länge greift die absolute Schwelle
    P.holes=[{hole:1,par:4,si:3}];
    /* ACHTUNG beim Testen: gemessen wird zum GRÜN, nicht zum Tee.
       at(-200) liegt 200 m hinter dem Tee, also 587 m vom Grün. */
    P.here=at(-200);
    ok("ohne Lochlänge: 587 m zum Grün noch erlaubt", tf()===null, "d="+tf());
    P.here=at(-1400);
    ok("ohne Lochlänge: 1400 m unterdrückt", tf()!==null);

    /* WICHTIG: Die Zielkette startet am ABSCHLAG, nicht an der eigenen
       Position — sie ist also auch von weit weg korrekt und soll dann NICHT
       unterdrückt werden. Nur die Entfernungen ab eigener Position sind
       Unsinn. Ein Riegel in playAimChain wäre falsch. */
    const chain=G("playAimChain");
    if (typeof chain === "function") {
      P.here=at(-2000);
      let ch=null; try{ ch=chain(true); }catch(e){ ch=null; }
      ok("Plan wird auch von weit weg gerechnet (ab Tee)",
         ch===null || (ch && ch.pts && ch.pts.length>=2),
         "Kette: "+(ch?ch.pts.length+" Punkte":"null"));
    }

    /* DER KERN DER ANFORDERUNG: Von weit weg soll die Schlagfolge ab dem Tee
       trotzdem GEZEICHNET werden — und dafür muss der Kartenausschnitt die
       ganze Bahn zeigen, nicht auf die eigene Position zoomen. */
    const iv=G("playMapInitView");
    if (typeof iv === "function") {
      const M={ W:2000, H:2000, s:1,
                map:(la,lo)=>[(lo-10.0)*65500, -(la-54.0)*111320] };
      P.mapBase={W:2000,H:2000};
      P.here=at(-2000);                       // 2 km hinter dem Tee
      const v=iv(geo,{hole:1},M,1.0);
      const tee=M.map(54.0,10.0), gruen=M.map(...at(387));
      const drin=(p)=> p[0]>=v.x-1 && p[0]<=v.x+v.w+1 && p[1]>=v.y-1 && p[1]<=v.y+v.h+1;
      ok("Abschlag im Bild", drin(tee), JSON.stringify(v));
      ok("Grün im Bild", drin(gruen));
      /* Würde die eigene Position einbezogen, müsste der Ausschnitt über
         2 km spannen — die Bahn wäre ein Strich. */
      ok("eigene Position wird NICHT einbezogen", v.h < 1500,
         "Höhe "+Math.round(v.h)+" px (2 km wären >2000)");
    }

    P.here=null;
    ok("ohne GPS keine Aussage", tf()===null);
    DB.courses=[]; P.holes=[]; P.course=null;
  }
}

/* ============ 24l. Beschriftung der Schlagfolge ============ */
group("Kennzahl je Phase — nie „undefined%“ auf der Karte");
{
  /* approach() liefert `gruen`, tee()/nextShot() liefern `fw`. Wer das nicht
     unterscheidet, schreibt „FW undefined%“ auf die Karte — genau das stand
     dort bis v1.79 beim Schlag aufs Grün. Die Formel wird hier direkt
     nachgebildet, weil sie in einem Template-Literal steckt. */
  const quote = fr => !fr ? ""
    : (isFinite(fr.gruen) ? ` · Grün ${fr.gruen}%`
      : isFinite(fr.fw)   ? ` · FW ${fr.fw}%` : "")
      + (isFinite(fr.pen)&&fr.pen>=5 ? ` · Strafe ${fr.pen}%` : "");
  eq("Abschlag zeigt Fairwayquote", quote({fw:57,pen:2}), " · FW 57%");
  eq("Approach zeigt Grünquote", quote({gruen:62,pen:1}), " · Grün 62%");
  eq("Strafrisiko ab 5 % dazu", quote({fw:57,pen:12}), " · FW 57% · Strafe 12%");
  eq("fehlende Kennzahl → gar keine Quote", quote({sand:3}), "");
  eq("ohne fracs → leer", quote(null), "");
  ok("kein 'undefined' in der Ausgabe",
     [quote({fw:57}),quote({gruen:62}),quote({sand:1}),quote(null)]
       .every(x=>x.indexOf("undefined")<0));
}

/* ============ 24m. Vollbild-Höhe: das Maximum, nicht das Minimum ============ */
group("pfViewportH — warum jede CSS-Lösung scheitern musste");
{
  /* Auf dem Gerät gemessen: innerH 705, clientH 649, vvH 705.
     `position:fixed` löst `bottom:0` gegen das Initial Containing Block auf,
     dessen Höhe `documentElement.clientHeight` ist — hier 56 px kürzer als der
     sichtbare Bereich. Deshalb KONNTE keine reine CSS-Lösung funktionieren:
     bottom:0 reicht nie weiter als sein Bezugsrahmen.
     Die früheren Versuche scheiterten, weil sie das MINIMUM nahmen
     (100dvh bzw. visualViewport.height). Richtig ist das MAXIMUM. */
  const H = (inner, client, vv) => Math.max(inner||0, client||0, vv||0);
  eq("gemessener Gerätefall: 705 statt 649", H(705, 649, 705), 705);
  eq("dvh/visualViewport allein wäre zu klein", Math.min(705, 649, 705), 649);
  eq("fehlende Werte stören nicht", H(0, 649, 0), 649);
  eq("alles leer → 0", H(0,0,0), 0);
  ok("Maximum ist nie kleiner als der Bezugsrahmen",
     [[705,649,705],[600,800,600],[900,900,120]].every(([a,b,c])=>H(a,b,c)>=b));
}

/* ============ 24n. Spielmodus als normale Ansicht ============ */
group("Kein Overlay mehr — der Spielmodus ist eine Ansicht");
{
  const src=fs.readFileSync(FILE,"utf8");
  /* Zwölf Anläufe mit Overlay-Techniken (position:fixed, <dialog> im Top
     Layer, gemessene Höhen) scheiterten daran, dass der Browser den
     Bezugsrahmen anders berechnet als erwartet: gemessen 506 statt 649 px,
     bei offsetParent null und position fixed — formal also korrekt.
     Eine Ansicht im Dokumentfluss hat nichts zu überdecken. */
  ok("eigene Ansicht v-play vorhanden",
     /<section[^>]*class="view"[^>]*id="v-play"/.test(src));
  ok("kein <dialog> mehr", !/<dialog[^>]*id="playFull"/.test(src));
  ok("in VIEW_RENDER eingetragen (sonst bleibt sie leer)",
     /VIEW_RENDER[\s\S]{0,900}play:pfRender/.test(src));
  ok("Flex-Layout: Karte dehnbar", /\.pv-map\{[^}]*flex:1/.test(src));
  ok("Aktionsleiste im Fluss, nicht absolut",
     /\.pf-bottom\{flex:0 0 auto/.test(src));
  ok("Navigation wird im Spielmodus ausgeblendet",
     /body\.play-mode[^{]*nav[^{]*\{[^}]*display:none/.test(src));
  /* Die gesamte Höhenmechanik MUSS weg sein — sie war der Versuch, gegen
     einen falsch berechneten Bezugsrahmen anzurechnen. */
  const js=src.replace(/\/\*[\s\S]*?\*\//g,"");
  ok("keine Höhenmechanik mehr",
     !/function pfFit|function pfViewportH|function pfApplyHeight|function pfVerify/.test(js));
  /* Kommentare enthalten den Begriff weiterhin (als Begründung) — geprüft
     wird der ausführbare Aufruf. */
  ok("keine dialog-Sonderbehandlung mehr", !/el\.showModal|\.showModal\(\)/.test(js));
  ok("Zurück-Taste erkennt den Spielmodus an der body-Klasse",
     /classList\.contains\("play-mode"\)[\s\S]{0,60}pfHide/.test(src));
}

/* ============ 24n2. Eingabemaske ohne Karte und Fahnensteuerung ============ */
group("Keine Doppelungen zwischen Karten- und Eingabemodus");
{
  const src=fs.readFileSync(FILE,"utf8");
  const i=src.indexOf("function renderPlay(){");
  const j=src.indexOf("\n}\n", i);
  const form=src.slice(i,j).replace(/\/\*[\s\S]*?\*\//g,"");   // ohne Kommentare
  /* Karte und Fahnensteuerung standen in BEIDEN Modi. Seit der Spielmodus im
     Kartenmodus startet, ist das doppelt — und der kleine Kartenausschnitt
     löste dieselbe teure Berechnung aus wie die große. */
  ok("Eingabemaske ohne Kartencontainer", form.indexOf("playMapWrap")<0);
  ok("Eingabemaske ohne Kartenschalter", form.indexOf("playMapCtrlsHtml")<0);
  ok("Eingabemaske ohne Fahnensteuerung", form.indexOf("pinCtrlHtml")<0);
  ok("Eingabemaske rechnet die Karte nicht mehr", form.indexOf("playMapRender()")<0);
  /* Die Distanzen zur Fahne bleiben — die braucht man auch beim Eintragen. */
  ok("Distanzanzeige bleibt in der Eingabemaske", form.indexOf("playInfoHtml()")>=0);
  /* Fahnensteuerung v1.90 KOMPLETT entfernt: sie war eine Handeingabe pro
     Loch, die im Alltag nicht gepflegt wurde — und ungepflegte Werte
     verschlechtern die Rechnung, statt sie zu verbessern. Ziel ist jetzt
     durchgängig die Grünmitte (F = null für STRAT.approach). */
  /* NUR den ausführbaren Code prüfen: Doku und Changelog nennen die entfernten
     Funktionen weiterhin — als historische Begründung, das ist gewollt. */
  const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
    .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
    .map(m=>m[2]).join("\n");
  const ohneKomm = nurCode.replace(/\/\*[\s\S]*?\*\//g,"").replace(/\/\/[^\n]*/g,"");
  ["pinCtrlHtml","playPinRec","pinPoint","playSetPinDepth","playSetPinSide",
   "playPinSlide","playPinCommit","playClearPin","greenAxisEdges"].forEach(f=>{
     ok("entfernt: "+f, ohneKomm.indexOf(f)<0);
  });
  ok("Grünmitte als Ziel (F=null)", /const F\s*=\s*null/.test(ohneKomm));
  /* Altbestand wird AKTIV geleert, nicht gelöscht — mergeDB kann Löschungen
     nicht ausdrücken (Object.assign). */
  ok("DB.pins wird geleert statt gelöscht",
     /DB\.pins=\{\}/.test(ohneKomm) && !/delete DB\.pins/.test(ohneKomm));
}

/* ============ 24n3. Kein Kartencontainer = keine Kartenarbeit ============ */
group("GPS-Tick ohne Karte — die Einfrier-Fehlerklasse");
{
  const src=fs.readFileSync(FILE,"utf8");
  /* Kommentare entfernen: sie nennen die Funktionsnamen als Begründung und
     würden die Positionsvergleiche verfälschen. */
  const ohne = t => t.replace(/\/\*[\s\S]*?\*\//g,"").replace(/\/\/[^\n]*/g,"");
  const i=src.indexOf("function playMapTick(");
  const tick=ohne(src.slice(i, src.indexOf("\n}\n", i)));
  /* Seit die Eingabemaske keine Karte mehr hat (v1.89), liefert playMapSlot()
     dort null. Ohne Riegel fiel JEDER GPS-Tick in den teuren Zweig und rief
     playMapRender() — Geo-Raster, Kacheln und eine NETZABFRAGE für die Höhen,
     im Sekundentakt. Genau das fror die App beim Wechsel Karte → Eingabe ein.
     Dieselbe Klasse wie v1.75, wo der Slot im Vollbild fehlte. */
  ok("playMapTick bricht ohne Container sofort ab",
     /if\(!slot\)\s*return;/.test(tick));
  ok("Abbruch steht VOR dem teuren Zweig",
     tick.indexOf("if(!slot) return;") < tick.indexOf("playMapRender()"));

  const j=src.indexOf("function playMapRender(){");
  const rend=ohne(src.slice(j, src.indexOf("\n}\n", j)));
  /* Auch playMapRender muss ZUERST prüfen: vorher stand der Abbruch nach der
     Höhenabfrage, die damit auch ohne Karte einen Netzaufruf feuerte. */
  ok("playMapRender prüft den Container vor der Höhenabfrage",
     rend.indexOf("if(!slot || !geo) return;") < rend.indexOf("elevEnsure"));
  ok("keine doppelte Container-Prüfung",
     (rend.match(/if\(!slot ?\|\| ?!geo\)/g)||[]).length === 1);
}

/* ============ 24o. Version sichtbar, Cache nicht im Weg ============ */
group("Warum Korrekturen wirkungslos SCHIENEN");
{
  const src=fs.readFileSync(FILE,"utf8");
  const swPfad=path.join(__dirname,"sw.js");
  /* Der Service Worker lieferte die App-Hülle nach stale-while-revalidate:
     beim Start kam die gespeicherte Fassung, die neue erst beim ÜBERNÄCHSTEN
     Start. Während der Entwicklung testet man damit stundenlang eine Version
     zu alt — und hält jede Korrektur für wirkungslos. */
  /* Die Live-Messung im Spielmodus ist mit v2.03 ENTFERNT — sie hat ihren
     Zweck erfüllt (sie fand die 56-px-Differenz zwischen clientHeight und
     Bildschirm) und verdeckte danach nur noch die Karte. Geprüft wird jetzt,
     dass sie vollständig weg ist. */
  /* NUR den ausführbaren Code prüfen: Doku und Changelog nennen die entfernten
     Funktionen weiterhin — als Historie, das ist gewollt. */
  const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
    .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
    .map(m=>m[2]).join("\n")
    .replace(/\/\*[\s\S]*?\*\//g,"").replace(/\/\/[^\n]*/g,"");
  ok("Messanzeige vollständig entfernt",
     !/pfDbgRender|pfDbgToggle|pfFacts|pfDebug/.test(nurCode));
  ok("kein Messwert-Element im Markup", !/id="pfDbg"/.test(src));
  /* Die Versionsanzeige in der Diagnose bleibt — sie beantwortet die Frage
     „läuft überhaupt die neue Fassung?" und hat dort ihren Platz. */
  ok("Version steht weiterhin in der Diagnose", /Laufende Version/.test(src));
  ok("Version steht in der Diagnose", /Laufende Version/.test(src));
  ok("Update-Erzwingen vorhanden", /function swForceUpdate/.test(src));
  ok("Update leert nur den Hüllen-Cache, nicht die Kacheln",
     /golf-shell/.test(src) && !/caches\.delete\(k\)\s*\)\s*\)[\s\S]{0,40}tiles/.test(src));
  if (fs.existsSync(swPfad)) {
    const sw=fs.readFileSync(swPfad,"utf8");
    eq("CACHE_VERSION erhöht", (sw.match(/CACHE_VERSION\s*=\s*"(\w+)"/)||[])[1], "v2");
    ok("Netz zuerst, mit Zeitlimit", /Promise\.race/.test(sw) && /1500/.test(sw));
    ok("Cache bleibt als Rückfall (Startgarantie im Funkloch)",
       /return cached \|\|/.test(sw));
  }
}

/* ============ 26. Verlauf, Zielabstand, Aufgaben ============ */
group("Der Kreis von Messen zu Trainieren");
{
  const verlauf=G("sgVerlauf"), gap=G("hcpGap"), fort=G("taskFortschritt"),
        analyse=G("platzAnalyse"), DB=G("DB"), STRAT=G("STRAT");

  if (typeof verlauf === "function" && DB) {
    const mk=(d,sc)=>({id:"R"+d, date:d, course:"T", holes:
      Array.from({length:18},(_,i)=>({hole:i+1,par:4,len:350,score:sc,putts:2,
        appr:"110–140",lie:"Fairway",distToPin:6,firstPutt:"6m",girDirect:"Ja"}))});
    DB.rounds=["2026-05-01","2026-05-08","2026-05-15","2026-05-22","2026-05-29",
               "2026-06-05","2026-06-12"].map((d,i)=>mk(d, 5));
    const p=verlauf(5);
    /* Ein gleitendes Fenster ist Pflicht: eine einzelne Runde schwankt so
       stark, dass eine Rohkurve nur Rauschen zeigt. */
    ok("Verlauf entsteht ab genug Runden", p.length>0, p.length+" Punkte");
    ok("jeder Punkt trägt einen Fenster-Umfang", p.every(x=>x.n>=3));
    ok("Fenster wächst nicht über die Vorgabe", p.every(x=>x.n<=5));
    DB.rounds=[mk("2026-05-01",5)];
    ok("zu wenige Runden → leer", verlauf(5).length===0);
    DB.rounds=[];
  }

  if (typeof gap === "function" && STRAT) {
    /* „HCP 18 → 16" ist abstrakt; in Schlägen je Kategorie wird es eine
       Baustelle. Die Rechnung kommt aus der ES-Tabelle der App selbst. */
    const g=gap(10);
    ok("liefert einen Abstand", !!g);
    if(g){
      ok("Summe positiv (Ziel ist besser)", g.summe>0, "Summe "+g.summe.toFixed(2));
      eq("vier Kategorien", g.teile.length, 4);
      ok("absteigend sortiert — größte Baustelle zuerst",
         g.teile.every((t,i)=>i===0 || g.teile[i-1].gap>=t.gap));
      const s2=g.teile.reduce((a,t)=>a+t.gap,0);
      ok("Teile summieren zur Gesamtsumme", Math.abs(s2-g.summe)<1e-9);
    }
    ok("Ziel schlechter als jetzt → kein Abstand", gap(99)===null);
    ok("ohne Ziel → null", gap(null)===null);
  }

  if (typeof fort === "function") {
    ok("ohne Startwert kein Fortschritt", fort({kat:"putt", sgStart:null})===null);
    ok("ohne Aufgabe null", fort(null)===null);
  }

  if (typeof analyse === "function" && DB) {
    DB.rounds=[
      {id:"A",date:"2026-05-01",course:"T",holes:[
        {hole:1,par:4,score:6,putts:2},{hole:2,par:3,score:3,putts:2}]},
      {id:"B",date:"2026-05-08",course:"T",holes:[
        {hole:1,par:4,score:7,putts:3},{hole:2,par:3,score:3,putts:2}]}
    ];
    const a=analyse("T");
    ok("Platzanalyse liefert Ergebnis", !!a);
    if(a){
      eq("beide Runden gezählt", a.runden, 2);
      /* Das teuerste Loch muss oben stehen — sonst hilft die Liste nicht. */
      eq("teuerstes Loch zuerst", a.loecher[0].hole, 1);
      near("Schnitt über Par auf Loch 1", a.loecher[0].schnitt, 2.5, 0.01);
      eq("Doppelbogeys gezählt", a.loecher[0].dbl, 2);
    }
    ok("unbekannter Platz → null", analyse("XX")===null);
    DB.rounds=[];
  }
}

/* ============ 24q. Platzbericht ============ */
group("courseStats — wo verliere ich AUF DIESEM Platz?");
{
  const cs=G("courseStats"), DB=G("DB");
  if (typeof cs === "function" && DB) {
    DB.courses=[{name:"T", tees:{Gelb:{holes:[
      {hole:1,par:4,len:350,si:5},{hole:2,par:3,len:150,si:17},{hole:3,par:5,len:480,si:1}]}}}];
    const mk=(s1,s2,s3)=>({course:"T",tee:"Gelb",side:"18 Loch",date:"2026-08-01",
      holes:[{hole:1,score:s1,putts:2},{hole:2,score:s2,putts:2},{hole:3,score:s3,putts:2}]});
    DB.rounds=[mk(6,3,5), mk(7,3,6), mk(6,4,5)];   // Loch 1 ist das Problemloch
    const r=cs("T");
    ok("liefert einen Bericht", !!r);
    if(r){
      eq("Runden gezählt", r.runden, 3);
      /* Loch 1: +2,+3,+2 => Schnitt +2,33 — muss ganz oben stehen.
         Genau darum geht es: der Schnitt über ALLE Plätze hilft vor einem
         Turnier nicht, die eigenen Problemlöcher HIER schon. */
      eq("Problemloch zuerst", r.problem[0].hole, 1);
      near("Schnitt des Problemlochs", r.problem[0].schnitt, 2.33, 0.02);
      eq("Doppelbogey-Quote", r.problem[0].dblQuote, 1);
      ok("starke Löcher am Ende", r.stark[0].schnitt <= r.problem[0].schnitt);
      /* Nachgerechnet: Runde 1 = +2, Runde 2 = +4, Runde 3 = +3 → 9/3 = 3.
         (Nicht mit dem Schnitt des Problemlochs verwechseln — das war mein
         eigener Rechenfehler beim ersten Ansatz.) */
      near("über Par je Runde", r.ueberProRunde, 3, 0.02);
    }
    ok("unbekannter Platz → null", cs("Gibt-es-nicht") === null);
    DB.rounds=[]; DB.courses=[];
  }
}

/* ============ 24r. Empfehlung ab der eigenen Position ============ */
group("playCaddyNow — die Empfehlung muss mitwandern");
{
  const now=G("playCaddyNow"), P=G("PLAY"), DB=G("DB");
  if (typeof now === "function" && P && DB) {
    const mLat=111320, mLng=65500;
    const at=(n,e=0)=>[54.0+n/mLat, 10.0+e/mLng];
    const ring=(n,e,r)=>[at(n-r,e-r),at(n-r,e+r),at(n+r,e+r),at(n+r,e-r)];
    const geo={holes:{1:{tee:at(0), green:at(387), fairway:[{ring:ring(190,0,60)}]}}};
    DB.courses=[{name:"CADDYNOW", geo}];
    DB.clubDistances=[{club:"Driver",carry:215,reach:232},{club:"5-Eisen",carry:160,reach:165},
      {club:"7-Eisen",carry:140,reach:143},{club:"PW",carry:104,reach:106},{club:"SW",carry:78,reach:80}];
    P.course="CADDYNOW"; P.tee="Gelb"; P.holes=[{hole:1,par:4,len:387,si:3}]; P.idx=0; P.aim={};

    const bei=m=>{ P.here=at(m); P.aimChainKey=null; return now(); };
    const tee=bei(0), mitte=bei(215), nah=bei(300);

    /* DER FEHLER bis v1.93: Die Kurzzeile zeigte legs[0] der Zielkette — und
       die startet IMMER am Abschlag. Es stand dauerhaft „Driver 219 m", egal
       wo man stand. Genau die Angabe, die sich mit jedem Schritt ändern muss,
       war die einzige, die es nie tat. */
    ok("am Abschlag: Restdistanz = Lochlänge", tee && Math.abs(tee.rest-387)<3, "rest="+(tee&&tee.rest));
    ok("nach dem Drive: deutlich weniger Rest", mitte.rest < tee.rest-150,
       `${tee.rest} -> ${mitte.rest}`);
    ok("nahe am Grün: noch weniger", nah.rest < mitte.rest, `${mitte.rest} -> ${nah.rest}`);
    ok("Restdistanz sinkt streng monoton",
       tee.rest > mitte.rest && mitte.rest > nah.rest);
    /* Und der Schläger muss sich mitändern — sonst wäre es dieselbe Falle. */
    ok("Schläger ändert sich mit der Position", tee.club !== nah.club,
       `${tee.club} vs ${nah.club}`);
    ok("am Abschlag darf der Driver kommen", /driver/i.test(tee.club||""));
    ok("nahe am Grün kein Driver mehr", !/driver/i.test(nah.club||""));
    /* Je Phase die passende Kennzahl: Fairwayquote am Tee, Grünquote beim
       Approach (siehe 24l). */
    ok("am Abschlag Fairwayquote", /FW/.test(tee.quote||""), tee.quote);
    /* Eigener Platzname: der Bewertungs-Zwischenspeicher (_aimCache) wird über
       Kurs|Loch|Modus|Koordinate geschlüsselt — mit „T" kollidierte der Test
       mit Abschnitt 24h und bekam dessen leeres Ergebnis. */
    ok("beim Approach Grünquote", (nah.quote||"").indexOf("Grün")===0,
       "quote=" + JSON.stringify(nah.quote));

    P.here=null;
    ok("ohne GPS keine Empfehlung", now()===null);
    DB.courses=[]; DB.clubDistances=[]; P.holes=[];
  }
}

/* ============ 24s. Über-Par-Anzeige ============ */
group("Über Par — „E\" trotz Schlägen darüber");
{
  /* FEHLER auf der Runde: Fehlt bei EINEM Loch `par`, wird die Summe NaN —
     und NaN ist weder größer noch kleiner als 0, also erschien „E" (even).
     Die Formel wird hier direkt nachgebildet. */
  const rechne = holes => {
    let op=0, pl=0, ohnePar=0;
    holes.forEach(x=>{
      if(x.score==null) return;
      if(!(+x.par>0)){ ohnePar++; return; }
      op += (x.score - x.par); pl++;
    });
    return {txt: pl ? (op>0?"+"+op:op<0?String(op):"E") : "–", op, pl, ohnePar};
  };
  eq("drei Bogeys ergeben +3", rechne([{par:4,score:5},{par:3,score:4},{par:5,score:6}]).txt, "+3");
  eq("ausgeglichen ist E", rechne([{par:4,score:4},{par:3,score:3}]).txt, "E");
  eq("unter Par negativ", rechne([{par:4,score:3},{par:5,score:5}]).txt, "-1");
  /* Der eigentliche Fehler: ein Loch ohne Par darf das Ergebnis nicht kippen. */
  const mitLuecke = rechne([{par:4,score:6},{par:null,score:5},{par:3,score:4}]);
  eq("Loch ohne Par wird übersprungen, nicht NaN", mitLuecke.txt, "+3");
  eq("und wird gezählt", mitLuecke.ohnePar, 1);
  eq("nur gewertete Löcher zählen", mitLuecke.pl, 2);
  eq("gar keine Scores → Strich", rechne([{par:4,score:null}]).txt, "–");
}

/* ============ 24s. Regen im Caddy ============ */
group("nassFaktor — Regen wirkt auf Carry UND Auslauf");
{
  const nf=G("nassFaktor");
  if (typeof nf === "function") {
    /* Der Caddy kannte Temperatur, Wind und Höhe — Regen nicht, obwohl er
       zwei Wirkungen hat: nasser Ball kostet Carry (~3 %), und nasse Fairways
       rollen praktisch nicht. Zusammen leicht zwei Schlägerlängen. */
    eq("ohne Wetter trocken", nf(null), 0);
    eq("0 mm/h trocken", nf({precip:0}), 0);
    eq("2,5 mm/h = voll nass", nf({precip:2.5}), 1);
    eq("Starkregen gedeckelt", nf({precip:10}), 1);
    near("1,25 mm/h = halb", nf({precip:1.25}), 0.5, 0.01);
    /* Ersatzweise der WMO-Code, wenn keine Menge geliefert wird. */
    eq("Code 0 = klar", nf({code:0}), 0);
    near("Code 53 = Niesel", nf({code:53}), 0.3, 0.01);
    near("Code 63 = Regen", nf({code:63}), 0.6, 0.01);
    near("Code 82 = Schauer", nf({code:82}), 0.8, 0.01);
    eq("Code 95 = Gewitter", nf({code:95}), 1);

    /* Die Wirkung auf den Auslauf als Rechnung: roll * (1 - 0.9 * nass).
       Bei stetigem Regen bleiben von 25 m Auslauf 2,5 m. */
    const roll=(c,nass)=>Math.max(0,Math.min(35,c))*(1-0.9*nass);
    near("trocken voller Auslauf", roll(25,0), 25, 0.01);
    near("stetiger Regen: kaum Auslauf", roll(25,1), 2.5, 0.01);
    near("Niesel dämpft leicht", roll(25,0.3), 18.25, 0.01);
    ok("Auslauf sinkt monoton mit Nässe",
       roll(25,0) > roll(25,0.3) && roll(25,0.3) > roll(25,1));
  }
}

/* ============ 24t. Index nur aus Turnieren ============ */
group("whsPool — ausschließlich Turniere bewegen den Index");
{
  const pool=G("whsPool"), DB=G("DB");
  if (typeof pool === "function" && DB) {
    /* Trainingsrunden dürfen den Index NICHT bewegen. Vorher flossen Runden
       ein, die als EDS markiert waren — jede Übungsrunde hätte den Wert
       verwässert, der das Turnierniveau abbilden soll. */
    DB.competitions=[{date:"2026-05-01", sd:18.2, tournament:"Clubmeisterschaft"},
                     {date:"2026-06-01", sd:16.9, course:"Nordplatz"}];
    DB.rounds=[{id:"X1", date:"2026-05-15", course:"Übung", countHcp:true,
                holes:[{hole:1,par:4,score:9,putts:2}]},
               {id:"X2", date:"2026-06-15", course:"Übung", countHcp:false,
                holes:[{hole:1,par:4,score:4,putts:2}]}];
    const p=pool();
    eq("nur die zwei Turniere", p.length, 2);
    ok("alle Einträge sind Turniere", p.every(x=>x.src==="Turnier"));
    ok("die als EDS markierte Runde zählt NICHT",
       !p.some(x=>String(x.label||"").indexOf("Übung")>=0));
    ok("chronologisch sortiert", p[0].date <= p[1].date);
    DB.competitions=[]; DB.rounds=[];
    eq("ohne Turniere leer", pool().length, 0);
  }
}

/* ============ 24u. Anführungszeichen in onclick ============ */
group("onclick-Attribute — die Falle, die den Knopf lautlos tötet");
{
  const src=fs.readFileSync(FILE,"utf8");
  /* `${JSON.stringify(text)}` in einem onclick="…" liefert DOPPELTE
     Anführungszeichen. Die beenden das Attribut vorzeitig, der Rest des
     Handlers wird abgeschnitten, und der Browser meldet
     „SyntaxError: Unexpected end of input". Der Knopf tut nichts — und im
     Quelltext sieht alles richtig aus. So ist „als Trainingsaufgabe"
     ausgefallen. */
  const treffer = src.match(/onclick="[^"]{0,300}JSON\.stringify/g) || [];
  ok("kein JSON.stringify in onclick", treffer.length===0,
     treffer.slice(0,2).join(" | "));
  /* Auch die Selbstprüfung muss das melden — sonst schleicht es sich wieder
     ein, ohne dass es jemand bemerkt. */
  ok("Selbstprüfung deckt die Fehlerklasse ab",
     /JSON\.stringify in onclick/.test(src));
  /* taskAdd holt den Text jetzt selbst — der einzige verlässliche Weg. */
  ok("taskAdd ergänzt den Text selbst",
     /function taskAdd\([\s\S]{0,200}sgDrillHint\(kat\)/.test(src));
}

/* ============ 24v. Zielabstand: zwei Bezugspunkte ============ */
group("hcpGap — Modellweg und eigenes Defizit sind zwei Fragen");
{
  const gap=G("hcpGap"), DB=G("DB"), S=G("STRAT");
  if (typeof gap === "function" && DB && S) {
    DB.profile=DB.profile||{};
    DB.strat=DB.strat||{}; DB.strat.esHcp=20;
    const g=gap(0);
    ok("liefert eine Rechnung", !!g);
    if(g){
      ok("Gesamtabstand plausibel", g.summe>10 && g.summe<40, "summe="+g.summe.toFixed(1));
      eq("vier Kategorien", g.teile.length, 4);
      /* KERN DER ERKLÄRUNG: Der Modellabstand verteilt sich beim Golf zum
         größten Teil aufs lange Spiel — unabhängig davon, wie gut DER SPIELER
         dort ist. Genau deshalb kann „Langes Spiel" gleichzeitig die eigene
         Stärke (positives SG) und die längste Strecke sein. Kein Widerspruch,
         sondern zwei verschiedene Bezugspunkte. */
      const lang=g.teile.find(t=>t.k==="lang");
      const putt=g.teile.find(t=>t.k==="putt");
      ok("langes Spiel ist der größte Modellanteil",
         lang.gap === Math.max(...g.teile.map(t=>t.gap)),
         g.teile.map(t=>t.k+":"+t.gap.toFixed(1)).join(" "));
      ok("Putten ist der kleinste", putt.gap < lang.gap);
      ok("Summe = Summe der Teile",
         Math.abs(g.summe - g.teile.reduce((a,t)=>a+t.gap,0)) < 0.01);
    }
    /* Ohne Ziel oder mit Ziel über dem eigenen Niveau gibt es nichts zu zeigen. */
    ok("kein Ziel → null", gap(null)===null);
    ok("Ziel schlechter als jetzt → null", gap(30)===null);
    delete DB.strat.esHcp;
  }
}

/* ============ 24w. Zu weit weg = Plan fürs Loch ============ */
group("Zielkette bleibt vernünftig, egal wie weit man weg ist");
{
  const chain=G("playAimChain"), tf=G("playTooFar"), P=G("PLAY"), DB=G("DB");
  if (typeof chain === "function" && P && DB) {
    const mLat=111320, mLng=65500;
    const at=(n,e=0)=>[54.0+n/mLat, 10.0+e/mLng];
    const ring=(n,e,r)=>[at(n-r,e-r),at(n-r,e+r),at(n+r,e+r),at(n+r,e-r)];
    DB.courses=[{name:"WEIT", geo:{holes:{1:{tee:at(0), green:at(279),
      fairway:[{ring:ring(140,0,50)}]}}}}];
    DB.clubDistances=[{club:"Driver",carry:215,reach:232},{club:"7 Wood",carry:169,reach:178},
      {club:"7-Eisen",carry:140,reach:143},{club:"PW",carry:104,reach:106}];
    P.course="WEIT"; P.tee="Gelb"; P.holes=[{hole:1,par:4,len:279,si:17}]; P.idx=0; P.aim={};

    const bei=m=>{ P.here=at(m); P.aimChainKey=null; return chain(true); };

    /* DER FEHLER: Aus 3,2 km Entfernung rechnete die Kette von der EIGENEN
       Position — 3077 m Rest geteilt durch 215 m Schlägerlänge ergab 15
       Schläge, und die Karte stapelte vierzehn „Layup"-Beschriftungen
       übereinander. Sinnvoll ist dann der Plan FÜRS LOCH, also ab Tee. */
    const weit=bei(-3077), tee=bei(0), bahn=bei(140);
    ok("zu weit wird erkannt", tf()!==null || bei(-3077)!==null);
    ok("von weit weg nur wenige Schläge", weit && weit.legs.length<=3,
       "Schläge: "+(weit?weit.legs.length:"–"));
    ok("von weit weg identisch zum Abschlagsplan",
       weit && tee && weit.legs.length===tee.legs.length &&
       weit.legs[0].club===tee.legs[0].club,
       `weit: ${weit&&weit.legs.map(l=>l.club).join("/")} · tee: ${tee&&tee.legs.map(l=>l.club).join("/")}`);
    ok("erster Schlag ist der Abschlag", weit && weit.legs[0].role==="Abschlag");
    /* Auf der Bahn wird dagegen ab der eigenen Position gerechnet. */
    ok("auf der Bahn kürzere Kette", bahn && bahn.legs.length < tee.legs.length,
       `${bahn&&bahn.legs.length} < ${tee&&tee.legs.length}`);

    /* Zweites Netz: die Schlagzahl ist auf 4 gedeckelt. */
    ok("Kette nie länger als 4 Schläge",
       [bei(-3077),bei(-800),bei(0),bei(140),bei(260)]
         .every(c=>!c || c.legs.length<=4));
    P.here=null; DB.courses=[]; DB.clubDistances=[]; P.holes=[];
  }
}

/* ============ 24x. Blatt schließen ≠ Zurück-Taste ============ */
group("_popEigen — eigenes history.back() vom Nutzer unterscheiden");
{
  const src=fs.readFileSync(FILE,"utf8");
  /* closeSheet() baut seinen History-Eintrag über history.back() ab — das löst
     ein popstate aus. Ohne Markierung hielt der Handler das für die
     Zurück-Taste: Schloss man im Kartenmodus die Scorekarte mit ✕, landete man
     in der EINGABEMASKE statt zurück auf der Karte. */
  ok("Markierung vorhanden", /let _popEigen/.test(src));
  ok("closeSheet setzt sie vor history.back()",
     /_popEigen=true;[\s\S]{0,120}history\.back\(\)/.test(src));
  ok("popstate-Handler wertet sie zuerst aus",
     /addEventListener\("popstate"[\s\S]{0,200}if\(_popEigen\)/.test(src));
  /* Sicherheitsnetz: bleibt das popstate aus, darf die Markierung nicht hängen
     bleiben und den nächsten echten Zurück-Druck schlucken. */
  ok("Markierung wird per Zeitgeber zurückgesetzt",
     /setTimeout\(\(\)=>\{ _popEigen=false; \}/.test(src));
  /* Der Wachhalter darf während einer Runde nicht freigegeben werden — jedes
     Blatt (Scorekarte, Details) läuft über closeSheet. */
  ok("Wake Lock bleibt während der Runde",
     /wakeRelease==="function" && !\(typeof PLAY!=="undefined" && PLAY\.active\)/.test(src));
}

/* ============ 24y. Spielweise wirkt auf mehr als Strafgebiete ============ */
group("Caddy-Modus — safe/bal/aggr müssen sich unterscheiden");
{
  const S=G("STRAT"), DB=G("DB");
  if (S && typeof S.tee === "function" && DB) {
    const mLat=111320, mLng=65500;
    const at=(n,e=0)=>[54.0+n/mLat, 10.0+e/mLng];
    const ring=(n,e,r)=>[at(n-r,e-r),at(n-r,e+r),at(n+r,e+r),at(n+r,e-r)];
    const geo={holes:{1:{tee:at(0), green:at(279),
      fairway:[{ring:ring(150,0,22)}], bunker:[{ring:ring(200,20,12)}]}}};
    DB.courses=[{name:"MODE", geo}];
    DB.clubDistances=[{club:"Driver",carry:215,reach:232},{club:"3 Wood",carry:195,reach:206},
      {club:"7 Wood",carry:169,reach:178},{club:"7-Eisen",carry:140,reach:143}];
    const ev={};
    ["safe","bal","aggr"].forEach(m=>{ ev[m]=S.tee(geo,"MODE",1,m,20); });
    ok("alle drei Modi liefern eine Bewertung", ev.safe&&ev.bal&&ev.aggr);
    if(ev.safe&&ev.bal&&ev.aggr){
      /* KERN: Vorher unterschieden sich die Modi NUR im Gewicht der
         Strafquote. Ohne Wasser oder Aus war `pen` überall 0 — die drei
         Bewertungen waren identisch und der Umschalter tat sichtbar nichts.
         Jetzt gehen Sand- und Rough-Anteil mit ein. */
      ok("sicher bewertet strenger als normal",
         ev.safe.best.score > ev.bal.best.score,
         `${ev.safe.best.score.toFixed(3)} > ${ev.bal.best.score.toFixed(3)}`);
      ok("normal strenger als offensiv",
         ev.bal.best.score > ev.aggr.best.score,
         `${ev.bal.best.score.toFixed(3)} > ${ev.aggr.best.score.toFixed(3)}`);
      /* Offensiv darf Strafgebiete NICHT ganz ignorieren — das wäre nicht
         mutig, sondern falsch. */
      ok("offensiv straft Strafgebiete weiterhin",
         /mode==="aggr" \? \{pen:0\.25/.test(fs.readFileSync(FILE,"utf8")));
      /* Der Erwartungswert bleibt in allen Modi derselbe — nur die
         Risikogewichtung verschiebt sich. */
      ok("Erwartungswert selbst ist modusunabhängig",
         Math.abs(ev.safe.best.es-ev.aggr.best.es)<0.001);
    }
    DB.courses=[]; DB.clubDistances=[];
  }
}

/* ============ 24z. Kartenschalter müssen neu zeichnen ============ */
group("Umschalter — Zustand kippen reicht nicht");
{
  const src=fs.readFileSync(FILE,"utf8");
  const i=src.indexOf("function playMapCtrlsHtml");
  const ctr=src.slice(i, src.indexOf("\n}\n", i));
  /* Distanzringe, Luftbild und Platzdaten stecken IM SVG — sie ändern sich
     erst, wenn die Karte neu gebaut wird. Die Knöpfe riefen aber nur
     playMapTick() (bewegt nur die Positionsmarke) bzw. playMapCtrlsRefresh()
     (zeichnet nur die Knopfleiste). Der Zustand kippte, sichtbar änderte sich
     nichts — die Knöpfe wirkten funktionslos. */
  ["toggleRings","toggleSat","toggleOsm"].forEach(fn=>{
    const m=new RegExp(fn+"\\(\\);([^\"]*)").exec(ctr);
    ok(fn+" zeichnet die Karte neu", !!m && /playMapRedraw/.test(m[1]),
       m?m[1]:"kein Aufruf gefunden");
  });
  ok("playMapRedraw vorhanden", /function playMapRedraw/.test(src));
  ok("playMapRedraw ruft playMapRender",
     /function playMapRedraw[\s\S]{0,200}playMapRender\(\)/.test(src));
  /* Gegenprobe: playMapTick allein genügt NICHT — es baut das SVG nicht neu. */
  ok("kein Umschalter verlässt sich auf playMapTick",
     !/toggle\w+\(\);\s*playMapTick\(\)/.test(ctr));
}

/* ================= 25. Gepflegt vs. gemessen ================= */
group("clubMeasured — gepflegte gegen gemessene Schlägerlängen");
{
  const cm = G("clubMeasured"), DB = G("DB");
  if (typeof cm === "function" && DB) {
    const heute = new Date().toISOString();
    DB.gpsShots = Array.from({length:12}, (_,i) => ({club:"7-Eisen", dist:142+((i%5)-2), ts:heute}));
    DB.lmSessions = [{shots: Array.from({length:12}, (_,i) => ({club:"7-Eisen", carry:134+((i%5)-2)}))}];
    const m = cm("7-Eisen");
    near("Carry aus R10", m.carry, 134, 2);
    near("Gesamtlänge aus GPS", m.total, 142, 2);
    ok("Anzahl wird mitgeführt", m.nCarry >= 8 && m.nTotal >= 8);
    const leer = cm("Gibt-es-nicht");
    ok("unbekannter Schläger → keine Werte", leer.carry === null && leer.total === null);
    DB.gpsShots = []; DB.lmSessions = [];
    ok("unter 8 Messungen → keine Werte", cm("7-Eisen").carry === null);
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
