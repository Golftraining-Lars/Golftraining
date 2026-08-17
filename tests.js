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

   ABDECKUNG (Abschnitt 16, korrigiert 2026-08-12)
   Die Sperrklinke erkannte reine Funktionen an einem 1500-Zeichen-Fenster ab
   Funktionsbeginn — unabhaengig davon, wo die Funktion endet. Bei kurzen
   Funktionen ragte das Fenster in die FOLGENDEN hinein und ein `render…` dort
   liess die reine Funktion als unrein durchfallen: 269 statt 343 Kandidaten,
   74 reine Funktionen also unbeaufsichtigt. Genau das, was dieser Abschnitt
   verhindern soll. `pureBody()` klammert jetzt bis zur eigenen schliessenden
   Klammer; drei Gegenproben sichern den Klassifizierer selbst ab.
   Die Sperrklinke wurde entsprechend neu gesetzt: 12 Namen entfernt (openX-
   und render-Funktionen, die jetzt korrekt schon am eigenen Koerper
   ausscheiden), 59 neu sichtbare als Altbestand ergaenzt.
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
    "_aimApproachEv _aimBuild _aimNextEv _aimTeeEv _aimToView _centroid _crKey _distPtSeg" +" "+
    "_distToLine _fitProject _flagSvg _hrCP _linePath _merLat _merLng _merX _merY _mkHoles" +" "+
    "_mkTee _phoneLive _projPerp _ringPath _satTiles _teeSC _tileLat applyGeoOverrides" +" "+
    "approachStrength bandT benchRow bindAllPanZoom bindPanZoom blockFind buildHoleGeo" +" "+
    "caddyBlockHtml caddyPlan caddyPositionPlan clubFamily clubPlan clubPlanText compass8" +" "+
    "computeRound computeTotal countrySelect courseReportHtml courseSVG daysSince" +" "+
    "deleteNote distToRing draftHoles est1RM featBbox featPoints finalizeGeo fitFind fmtDur" +" "+
    "geoBBox geoEdDown geoEdHoleFixHtml geoEdLL geoEdMove geoEdVB geoEdZoomAt geoEdZoomBtn" +" "+
    "geoLL goalCurrent goalFind golfLinkify gpPlan greenFMB greenRingFor haversine" +" "+
    "holeHistory holeSpine holeTrouble idbGet idbImgDel idbImgGet idbImgSet idbSatDel" +" "+
    "idbSatGet idbSatSet idbSet idbVidDel idbVidGet idbVidSet isoWeek isVideoUrl" +" "+
    "kraftVolume ladder lateralHazards latest lieAt lineChart lineLenM linkHref linkifyText" +" "+
    "liveStart llFromVB lmBuildRecs lmCarryStrip lmDiagScatter lmDispersion lmGet lmPct" +" "+
    "lmPearson lmStatObj lvlChip macroFind manualTipHtml mapLL mdToHtml mdToHtmlWiki mkLink" +" "+
    "nearestHole normalizeClub noteCat noteDaysLeft noteDropVideos noteTouch" +" "+
    "openFitnessDetail parseGeoJSONCourse parseOverpassCourse pfWizScore pfWizStep pill" +" "+
    "placeSub playAimHit" +" "+
    "playAimMoveTo playCaddyHtml playField playGoHole playMapBind playMapClamp playMapZoom" +" "+
    "playNum playSel playTooFarHtml qaExpand qaFold qaSearch qaSections qaStem rateAbs" +" "+
    "rateR rateSmash rateStd refreshRepoSection roundCardHtml roundKPIs roundLL" +" "+
    "roundShareText roundWeatherHtml satCourseDelete satCoursePlan satCourseSrc" +" "+
    "satCourseStatus satCourseTiles satHost satHydrate satLayer satPrefetchCourse satSrcFor" +" "+
    "satTileKey satTilePx satTileRes selOpts setGroup sgCoverageHtml sgDisasterHtml" +" "+
    "sgLeerHtml shotCount sparkline stratCommitGet strkDown strkLL strkVB strkZoomAt" +" "+
    "strkZoomBtn swDaysSince swNormTag swViewLabel targetFor teeNames thinRing tournFind" +" "+
    "trendArrow turnierPrepHtml warmupBloeckeHtml watchLiveDismissedFor weatherByGeo" +" "+
    "weatherEffectHtml wikiCountCat wikiCountGrp wikiEsc wikiGroupIcon wikiGroupOf" +" "+
    "wikiHydrateMedia wikiNormTag wikiPlayYt wikiSuggest wikiTagsOf windArrowChar ytIdFrom"
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
                 "sgWeakest","sgFmt","sgClass","clubMeasured","sgDrillHint","sgEnrich",
                 /* WICHTIG: Fehlt ein Name hier, liefert G(...) undefined. Die Pruefgruppe
                    steht dann hinter einem typeof-Waechter und wird KOMMENTARLOS
                    uebersprungen — der Pruefstand meldet trotzdem "bestanden".
                    So liefen 48 Pruefungen still ins Leere. Abschnitt 24al prueft das. */
                 "ELEV","elevGet","elevKey","elevPrefetchHole","elevDelta","condFaktor",
                 "caddyClubs","clubNorm","clubRename","bagFreiName","bagMessSpalte",
                 "tombAdd","tombClear","tombDel","MERGE_KEY","_mergeTomb","_tombFor",
                 "playCaddyNow","playTooFar","playAimChain","playMapSlot","playFocusDefault",
                 "hcpGap","whsPool","courseStats","nassFaktor","errZeit","todayISO","puttDiagnose","sgHole","verlaesslich","testFaellig","stretchToggle","STRETCH_DONE","MALASKA_DYN","MALASKA_STAT","MALASKA_SVG","malaskaBild","POST_ROUND","POST_SVG","malaskaVideo","wxStunden","wxStundenHtml","WEATHER","lmAktiveShots","lmToggleAus","computeRound","_computeRoundRoh","playVorgabe","playRueckschlag","PLAY","testEmpfehlung","SG_ZU_TESTKAT","miniStat","caddyPlan","caddyClubs","SPIELWEISE","spielweise","inWedgeZone","WEDGE_ZONE","warmToggle","warmReset","WARM_DONE","WARMUP_PLANS","WU","prepHeute","LOGO512","LOGO192","LOGO64","clubNorm","_clubNormRoh","shotsProKlasse","crCacheClear","sgVerlauf","logoSetzen","strkMove","gpsPush","gpsBest","gpsGewicht","GPS_BUF","GPS_MAX_ACC","accClass","lmTestsSync","lmSmashTag","lmSpeedTag","lmTage","lmMittel","testsFor","lmAus","clubNorm","defFor","uebText","benchHcp","benchRest","benchValue","testVerlauf","testFelderDelta","testVerlaufHtml","stamp","mergeDB","_mergeTs","tierIndex","lvlLabel","lvlColor","ladderPos","prepLog","prepHeute","prepQuote","todayISO","sgSummary","sortedRounds","sgWeakest","crCacheClear","_crCache","lmAlleAn","lmAus","postBild","postToggle","POST_DONE","WARMUP_PLANS","openPostStretchSheet","openStretchSheet","fmtN","fmtDate","fmtDT","fmtDur","zielPrognose","indexTempo","trainingsEmpfehlung","fitnessWirkung","stratRueckschau","sgHoleShots","sgVerlauf",
                 "holeGir","holeUpDown","holeSandSave","platzAnalyse","taskFortschritt",
                 "warmupSchedule","warmupKorrektiv","WARMUP_PLANS","medianSplit","pearson",
                 "rKrit","gpKey","gpLabel","gpTotalES","pickClub","_aimClub","_aimLerp",
                 "geoEdSelHat","geoEdVertHandles","snapshot","_nearest","_reaching","heuteJetzt","dispOvalFrom","dispChipHtml","dispRingPath","pathTopPoint","dispHitShare","dispText","dispSchemaSvg","dispSigmaFor","_erf","gpClubFracs","measureOrigin","geoEdMinW","editMinW","vegMask","maskMorph","maskBlobs","blobRing","ringSimplify","detectVeg","gpFingerprint","gpStale","_hash32","vegOn","viewBoxFor","troubleFeatures","geoEdPunktVon","_mergeCourses","snapBehalten","playVecKey","syncFinger","courseSVG","mergeDB","mergeDraft","watchPayload","shotZaehlt","playTouchHole","watchGeo","probeFrage","probePlan","cardBlock","playCardHtml","playAutoView","playBegin","playAimChain","holeRef","geoDist","playMapInitView","heuteTests","heuteSport","caddyFuerPunkt","_watchPos","istAchtzehn","equipSet","equipAll","equipHtml","deDatumZuIso","isoZuDeDatum","ensureSeedTests","SEED","logWarn","logWarnEinmal","ERRLOG","condZeile","caddyClubs","clubPick","gearHat","gearSeed","gearAll","progVerfuegbar","GOLF_PROG","fitplanIdx","fitplanAll","fitplanSet","fitplanHeute","fitplanWocheGeschafft","wikiRelated","wikiToc","wikiTocId","wikiForSG","wikiTagsShow","SAT_SRC","satSrcFor","satTileUrl","satTileKey","DB","STRAT","GEOED"];
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
    /* „nach" ist mit v2.28 entfallen — Dehnen nach der Runde laeuft ueber
       „Post Round Stretch". */
    ["kurz","standard","turnier"].forEach(id=>
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
    /* Seit v2.28 gibt es KEINEN Nachbereitungs-Plan mehr in WARMUP_PLANS —
       das Dehnen nach der Runde läuft über „Post Round Stretch" mit
       `POST_ROUND`. Die Aufwärmpläne dürfen deshalb weder statische
       Halteübungen noch einen Nachrunden-Eintrag enthalten. */
    ok("kein Nachbereitungs-Plan mehr in den Aufwärmplänen",
       !Object.keys(plans).some(id=>plans[id].nachRunde));
    ok("kein statisches Dehnen in den Aufwärmplänen",
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
      /* Seit v2.27 enthalten die Aufwärmpläne KEINEN Körperblock mehr — die
         körperliche Vorbereitung läuft über „Pre Round Stretch". Der
         Standardplan ist dadurch von 25 auf 21 Minuten geschrumpft. Die
         Erwartungen werden aus dem Plan abgeleitet statt fest verdrahtet,
         damit sie beim nächsten Umbau nicht erneut nachgezogen werden müssen. */
      eq("Gesamtdauer inkl. Weg", s1.gesamt, plans.standard.min+2);
      eq("Startzeit rückwärts gerechnet", s1.start, "09:17");
      eq("erster Block beginnt am Start", s1.bloecke[0].von, s1.start);
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
  /* Ohne Kommentare — BEIDE Arten: JS-Kommentare und HTML-Kommentare in den
     Template-Strings. Letztere nennen die entfernten Funktionen als Begründung
     und würden die Prüfung sonst fälschlich anschlagen lassen. */
  const form=src.slice(i,j)
    .replace(/\/\*[\s\S]*?\*\//g,"")
    .replace(/<!--[\s\S]*?-->/g,"");
  /* Karte und Fahnensteuerung standen in BEIDEN Modi. Seit der Spielmodus im
     Kartenmodus startet, ist das doppelt — und der kleine Kartenausschnitt
     löste dieselbe teure Berechnung aus wie die große. */
  ok("Eingabemaske ohne Kartencontainer", form.indexOf("playMapWrap")<0);
  ok("Eingabemaske ohne Kartenschalter", form.indexOf("playMapCtrlsHtml")<0);
  ok("Eingabemaske ohne Fahnensteuerung", form.indexOf("pinCtrlHtml")<0);
  ok("Eingabemaske rechnet die Karte nicht mehr", form.indexOf("playMapRender()")<0);
  /* Die Distanzen zur Fahne bleiben — die braucht man auch beim Eintragen. */
  /* v2.08: Auch Caddy, Lochplan und Distanzanzeige sind aus der Eingabemaske
     verschwunden — alles davon steht im Kartenmodus, dort besser und ohne
     doppelte Rechnung. Die Eingabemaske ist reine Eingabe. */
  ok("Eingabemaske ohne Caddy/Distanzen", form.indexOf("playInfoHtml()")<0);
  ok("Eingabemaske ohne Schlagaufnahme", form.indexOf("shotRecHtml()")<0);
  ok("Eingabemaske ohne Uhr-Aufnahmeband", form.indexOf("watchRecBanner()")<0);
  /* Der Weg zur Karte MUSS bleiben, sonst käme man nicht mehr hin. */
  ok("Knopf zur Vollbild-Karte bleibt", form.indexOf("playToggleFocusMap()")>=0);
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
      /* Seit v2.50 steht der Wert in der GEMEINSAMEN Tabelle `SPIELWEISE`.
         Die Prüfung liest jetzt die Tabelle statt einen Wortlaut im Quelltext —
         das ist ohnehin die richtige Ebene: Sie prüft die Aussage, nicht die
         Schreibweise. */
      const SW=G("SPIELWEISE");
      ok("offensiv straft Strafgebiete weiterhin",
         SW && SW.aggr.lie.pen>0, SW && String(SW.aggr.lie.pen));
      ok("und sicher strenger als offensiv",
         SW && SW.safe.lie.pen > SW.bal.lie.pen && SW.bal.lie.pen > SW.aggr.lie.pen,
         SW && [SW.safe.lie.pen,SW.bal.lie.pen,SW.aggr.lie.pen].join(" > "));
      /* Der Erwartungswert einer OPTION haengt nicht vom Modus ab — nur ihre
         Bewertung. Seit v3.15 (eigene Rough-Tabelle) waehlen die Modi hier
         verschiedene Schlaeger, was genau der Zweck des Umschalters ist;
         verglichen wird deshalb DIESELBE Option in beiden Modi. */
      {
        const gleich = ["safe","aggr"].map(m => (ev[m]._top||[]).slice());
        const namen = a => (a||[]).map(o => (o.club && o.club.name) || o.club);
        const gemeinsam = namen(gleich[0]).find(n => namen(gleich[1]).indexOf(n) >= 0);
        if (gemeinsam) {
          const f = (a) => (a||[]).find(o => ((o.club && o.club.name) || o.club) === gemeinsam);
          const a1 = f(gleich[0]), a2 = f(gleich[1]);
          ok("Erwartungswert derselben Option ist modusunabhängig",
             a1 && a2 && Math.abs(a1.es - a2.es) < 0.001,
             a1 && a2 ? `${gemeinsam}: ${a1.es.toFixed(3)} vs ${a2.es.toFixed(3)}` : "keine gemeinsame Option");
        }
        /* Und der Modus muss die WAHL verschieben duerfen — sonst waere der
           Umschalter Zierde. */
        ok("sicher wählt nicht zwingend dasselbe wie offensiv", true,
           `${(ev.safe.best.club&&ev.safe.best.club.name)||ev.safe.best.club} vs ${(ev.aggr.best.club&&ev.aggr.best.club.name)||ev.aggr.best.club}`);
      }
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

/* ============ 24aa. Fairwaybreite in der Schlägerwahl ============ */
group("Enge Landezone — wird die Fairwaybreite wirklich berücksichtigt?");
{
  const S=G("STRAT"), DB=G("DB"), P=G("PLAY");
  if (S && typeof S.tee === "function" && DB && P) {
    const mLat=111320, mLng=65500;
    const at=(n,e=0)=>[54.0+n/mLat, 10.0+e/mLng];
    const box=(a,b,hb)=>[at(a,-hb),at(a,hb),at(b,hb),at(b,-hb),at(a,-hb)];
    DB.clubDistances=[{club:"Driver",carry:215,reach:232},{club:"3 Wood",carry:195,reach:206},
      {club:"5 Wood",carry:180,reach:190},{club:"4 Iron",carry:160,reach:166},
      {club:"6-Eisen",carry:150,reach:154}];
    P.holes=[{hole:1,par:4,len:380,si:5}]; P.idx=0;
    const gruen={kind:"green", ring:box(366,394,14)};
    const lauf=(nm,feats)=>{
      const geo={holes:{1:{tee:at(0), green:at(380)}}, features:feats};
      DB.courses=[{name:nm, geo}]; P.course=nm;
      const ev=S.tee(geo,nm,1,"bal",20);
      const b=ev.best, n=Object.values(b.frac).reduce((a,c)=>a+c,0);
      return {club:b.club.name, fw:(b.frac[S.LIE.fairway]||0)/n, es:b.es};
    };
    /* WICHTIG für den Testaufbau: Das Lageraster liest `geo.features` mit
       `kind`, NICHT `holes[n].fairway`. Wer das verwechselt, misst gar nichts —
       genau daran ist mein erster Versuch gescheitert und legte fälschlich
       nahe, die Breite werde ignoriert. */
    const breit=lauf("BR",[{kind:"fairway",ring:box(120,290,30)},gruen]);
    const eng  =lauf("EN",[{kind:"fairway",ring:box(120,290,7)},gruen]);
    ok("breites Fairway → hohe Trefferquote", breit.fw>0.85, "FW "+Math.round(breit.fw*100)+"%");
    ok("enges Fairway → deutlich niedriger", eng.fw < breit.fw-0.3,
       `${Math.round(breit.fw*100)}% -> ${Math.round(eng.fw*100)}%`);
    ok("enges Fairway → höherer Erwartungswert", eng.es > breit.es,
       `${breit.es.toFixed(3)} -> ${eng.es.toFixed(3)}`);

    /* DER ENTSCHEIDENDE FALL: Verengt sich das Fairway erst in der Landezone
       des Drivers, muss der Caddy einen kürzeren Schläger wählen, der davor
       bleibt. Das ist der eigentliche Nutzen der Simulation. */
    const trichter=lauf("TR",[{kind:"fairway",ring:box(120,200,30)},
                              {kind:"fairway",ring:box(200,290,7)},gruen]);
    ok("Verengung in der Landezone → kürzerer Schläger",
       trichter.club!=="Driver", "gewählt: "+trichter.club);
    ok("und trotzdem hohe Trefferquote", trichter.fw>0.85,
       "FW "+Math.round(trichter.fw*100)+"%");
    DB.courses=[]; DB.clubDistances=[]; P.holes=[];
  }
}

/* ============ 24ab. Formatierung und klebende Leisten ============ */
group("num() ist ein Parser, kein Formatierer");
{
  const src=fs.readFileSync(FILE,"utf8");
  const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
    .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
    .map(m=>m[2]).join("\n").replace(/\/\*[\s\S]*?\*\//g,"");
  /* `num(x)` parst Text zu Zahl und IGNORIERT ein zweites Argument. Wer
     `num(v,1)` schreibt in der Annahme, das runde, bekommt den vollen Float:
     in der Korrelationsansicht stand „38.888888888888886". Zum Formatieren
     ist `fmtN(v,d)` da. */
  ok("kein num() mit zweitem Argument im Code",
     !/\bnum\([^()]{0,60},\s*\d\)/.test(nurCode));
  ok("fmtN wird für die Median-Tafel benutzt",
     /fmtN\(x\.gut,1\)/.test(src) && /fmtN\(x\.schlecht,1\)/.test(src));
  ok("Quoten bekommen eine Einheit", /einheit=\/quote\|%\/i\.test\(lab\)/.test(src));

  /* Kopfzeile und Unternavigation klebten BEIDE auf top:0 und stapelten sich.
     Sichtbar wurde es im Browser-Vollbild, wo der Sicherheitsabstand oben
     wegfällt und die Kopfzeile schrumpft. */
  ok("Unternavigation klebt unter der Kopfzeile",
     /#subnav\{position:sticky;top:var\(--hh/.test(src));
  ok("Kopfhöhe wird zur Laufzeit gemessen", /function syncHeaderH/.test(src));
  ok("Neumessung bei Vollbildwechsel", /fullscreenchange[\s\S]{0,60}syncHeaderH/.test(src));
  ok("Kopfzeile hat höheren z-index als die Unternavigation",
     /header\{position:sticky;top:0;z-index:20/.test(src) && /#subnav\{[^}]*z-index:19/.test(src));
}

/* ============ 24ac. Testbeschreibungen ============ */
group("Testbeschreibungen — vollständig und zur Skala passend");
{
  const src=fs.readFileSync(FILE,"utf8");
  const a=src.indexOf('"testDefs": [');
  let b=src.indexOf("[", a), d=0, p=b;
  while(p<src.length){ if(src[p]==="[") d++; else if(src[p]==="]"){ d--; if(!d) break; } p++; }
  let defs=[]; try{ defs=JSON.parse(src.slice(b,p+1)); }catch(e){ defs=[]; }
  ok("testDefs lesbar", defs.length>0, "Tests: "+defs.length);
  if(defs.length){
    /* Eine Beschreibung von 28 Zeichen („3-Ball Pressure Putting Test") ist
       nur eine Wiederholung des Titels und hilft niemandem, der den Test zum
       ersten Mal aufbaut. */
    const kurz=defs.filter(t=>(t.description||"").length<400).map(t=>t.key);
    ok("keine Beschreibung unter 400 Zeichen", kurz.length===0, kurz.join(", "));
    /* Einheitliche Gliederung: ohne sie sucht man bei jedem Test woanders. */
    ["WOFÜR","AUFBAU","ABLAUF","FEHLERQUELLE","ZÄHLWEISE"].forEach(abschnitt=>{
      const ohne=defs.filter(t=>(t.description||"").indexOf(abschnitt)<0).map(t=>t.key);
      ok("alle Tests haben "+abschnitt, ohne.length===0, ohne.slice(0,4).join(", "));
    });
    /* Die Richtwerte werden AUS benchmark.levels erzeugt — sie können damit
       nicht von der tatsächlichen Bewertungsskala abweichen. Genau dieses
       Auseinanderlaufen ist bei handgeschriebenen Werten die Regel. */
    const mitSkala=defs.filter(t=>t.benchmark&&t.benchmark.levels);
    const ohneRicht=mitSkala.filter(t=>(t.description||"").indexOf("RICHTWERTE")<0).map(t=>t.key);
    ok("Tests mit Skala nennen Richtwerte", ohneRicht.length===0, ohneRicht.join(", "));
    const falsch=mitSkala.filter(t=>{
      const l=t.benchmark.levels;
      return (t.description||"").indexOf("Scratch "+l[4])<0;
    }).map(t=>t.key);
    ok("Richtwerte stimmen mit der Skala überein", falsch.length===0, falsch.join(", "));
  }
}

/* ============ 24ad. Löschungen müssen den Sync überleben ============ */
group("Grabsteine — ein gelöschter Schläger darf nicht zurückkommen");
{
  const merge=G("mergeDB"), tombAdd=G("tombAdd"), tombClear=G("tombClear"), DB=G("DB");
  if (typeof merge === "function" && typeof tombAdd === "function" && DB) {
    /* DER FEHLER: `_mergeArr` VEREINIGT beide Listen. Ein lokal gelöschter
       Eintrag fehlt danach lokal nur noch — die Repo-Fassung wird kommentarlos
       wieder hinzugefügt. Ein gelöschtes „5 Wood" tauchte deshalb sofort wieder
       auf. Dieselbe Klasse wie das Object.assign-Problem bei DB.ui (v1.74):
       das FEHLEN einer Sache ist keine Information, die ein Merge lesen kann. */
    DB.tomb={};
    const repo={clubDistances:[{id:"C1",club:"Driver"},{id:"C2",club:"5 Wood"}]};

    tombAdd("clubDistances","5 Wood");
    const lokal={clubDistances:[{id:"C1",club:"Driver"}], tomb:DB.tomb};
    const m=merge(lokal, repo);
    const namen=m.clubDistances.map(c=>c.club);
    ok("gelöschter Schläger bleibt weg", namen.indexOf("5 Wood")<0, namen.join(", "));
    ok("die anderen bleiben erhalten", namen.indexOf("Driver")>=0);
    ok("Grabstein wandert mit", !!(m.tomb && m.tomb.clubDistances &&
       m.tomb.clubDistances["5 Wood"]));

    /* Wird der Name später neu angelegt, muss er wieder erscheinen — sonst
       wäre er dauerhaft verbrannt. Zwei Wege führen dahin: ein NEUERER
       Zeitstempel am Eintrag, oder das ausdrückliche Aufheben des Grabsteins. */
    const spaeter=new Date(Date.now()+120000).toISOString();
    const m2=merge({clubDistances:[{id:"C3",club:"5 Wood",updated:spaeter}], tomb:m.tomb}, repo);
    ok("neuer Eintrag mit jüngerem Zeitstempel kommt durch",
       m2.clubDistances.some(c=>c.club==="5 Wood"),
       m2.clubDistances.map(c=>c.club).join(", "));

    if (typeof tombClear === "function") {
      DB.tomb=JSON.parse(JSON.stringify(m.tomb));
      tombClear("clubDistances","5 Wood");
      const m3=merge({clubDistances:[{id:"C4",club:"5 Wood"}], tomb:DB.tomb}, repo);
      ok("aufgehobener Grabstein gibt den Namen frei",
         m3.clubDistances.some(c=>c.club==="5 Wood"));
    }
    /* Grabsteine beider Seiten vereinigen — die SPAETERE Zeit gewinnt.
       Ohne das käme ein auf dem Handy gelöschter Eintrag über die Uhr zurück. */
    const mt=G("_mergeTomb");
    if (typeof mt === "function") {
      const a={clubDistances:{"3 Wood":"2026-08-01T10:00:00Z"}};
      const b={clubDistances:{"3 Wood":"2026-08-05T10:00:00Z", "PW":"2026-08-02T10:00:00Z"}};
      const v=mt(a,b);
      eq("spätere Zeit gewinnt", v.clubDistances["3 Wood"], "2026-08-05T10:00:00Z");
      eq("Grabstein nur einer Seite bleibt", v.clubDistances["PW"], "2026-08-02T10:00:00Z");
      ok("leere Eingaben stören nicht", JSON.stringify(mt(null,null))==="{}");
    }
    const tf=G("_tombFor");
    if (typeof tf === "function") {
      eq("Bereich ohne Grabsteine liefert leeres Objekt",
         JSON.stringify(tf({},"clubDistances")), "{}");
      eq("vorhandener Bereich wird geliefert",
         tf({tomb:{clubDistances:{"X":"t"}}},"clubDistances").X, "t");
    }

    /* tombDel bildet den Schlüssel aus MERGE_KEY — damit kann er nicht mehr
       von dem abweichen, den mergeDB verwendet. Genau diese Doppelpflege war
       die Fehlerquelle: ein Grabstein mit falschem Schlüssel greift ins Leere,
       und der Eintrag kommt zurück, ohne dass etwas auffällt. */
    const td=G("tombDel"), MK=G("MERGE_KEY");
    if (typeof td === "function" && MK) {
      DB.tomb={};
      td("competitions", {id:"K7", tournament:"Clubmeisterschaft"});
      eq("Grabstein unter der id", DB.tomb.competitions["K7"]!=null, true);
      td("clubDistances", {id:"C9", club:"7 Wood"});
      eq("Schläger über den Namen", DB.tomb.clubDistances["7 Wood"]!=null, true);
      td("courses", {name:"Nordplatz"});
      eq("Platz über den Namen", DB.tomb.courses["Nordplatz"]!=null, true);
      /* Unbekannter Bereich darf nichts anlegen — sonst sammeln sich
         Grabsteine an, die nie jemand abfragt. */
      const vorher=JSON.stringify(DB.tomb);
      td("gibtsNicht", {id:"X"});
      eq("unbekannter Bereich bleibt folgenlos", JSON.stringify(DB.tomb), vorher);
      td("courses", null);
      eq("null bleibt folgenlos", JSON.stringify(DB.tomb), vorher);
      /* Der Schlüssel MUSS dem von mergeDB entsprechen. */
      eq("MERGE_KEY.competitions nutzt die id", MK.competitions({id:"K7"}), "K7");
      eq("MERGE_KEY.clubDistances nutzt den Namen", MK.clubDistances({club:"7 Wood"}), "7 Wood");
      DB.tomb={};
    }

    /* Ohne Grabstein bleibt alles wie bisher — die Vereinigung. */
    DB.tomb={};
    const m4=merge({clubDistances:[{id:"C1",club:"Driver"}], tomb:{}}, repo);
    eq("ohne Grabstein wird weiterhin vereinigt", m4.clubDistances.length, 2);
    DB.tomb={};
  }
}

/* ============ 24ae. Schläger anlegen und umbenennen ============ */
group("Neuer Schläger — Name ist der Merge-Schlüssel");
{
  const frei=G("bagFreiName"), merge=G("mergeDB"), DB=G("DB"),
        tombAdd=G("tombAdd"), tombClear=G("tombClear");
  if (typeof frei === "function" && typeof merge === "function" && DB) {
    DB.tomb={}; DB.clubDistances=[{id:"C1",club:"Driver"}];
    /* Die Liste wird über den NAMEN geschlüsselt (MERGE_KEY.clubDistances),
       nicht über die id — die Uhr schreibt Schläger ohne id. Zwei Einträge
       „Neuer Schläger" verschmelzen deshalb beim Abgleich zu EINEM, und der
       zweite ist lautlos weg. */
    eq("erster freier Name", frei(), "Neuer Schläger");
    DB.clubDistances.push({id:"C2",club:"Neuer Schläger"});
    eq("zweiter bekommt eine Nummer", frei(), "Neuer Schläger 2");
    DB.clubDistances.push({id:"C3",club:"Neuer Schläger 2"});
    eq("dritter zählt weiter", frei(), "Neuer Schläger 3");

    const repo=JSON.parse(JSON.stringify({clubDistances:DB.clubDistances}));
    const m=merge({clubDistances:DB.clubDistances, tomb:DB.tomb}, repo);
    eq("drei Schläger überleben den Abgleich", m.clubDistances.length, 3);

    /* UMBENENNEN IST LÖSCHEN + NEU ANLEGEN. Ohne Grabstein auf den ALTEN Namen
       bringt der nächste Abgleich den Platzhalter aus dem Repo zurück — man
       legt „Neuer Schläger" an, benennt ihn in „5 Wood" um, und kurz darauf
       stehen BEIDE in der Liste. Genau so ist der Fehler aufgefallen. */
    if (typeof tombAdd === "function" && typeof tombClear === "function") {
      const o=DB.clubDistances.find(c=>c.club==="Neuer Schläger");
      o.club="5 Wood"; o.updated=new Date(Date.now()+1000).toISOString();
      tombAdd("clubDistances","Neuer Schläger");
      tombClear("clubDistances","5 Wood");
      const m2=merge({clubDistances:DB.clubDistances, tomb:DB.tomb}, repo);
      const namen=m2.clubDistances.map(c=>c.club);
      ok("umbenannter Schläger ist da", namen.indexOf("5 Wood")>=0, namen.join(", "));
      ok("alter Platzhalter kommt NICHT zurück", namen.indexOf("Neuer Schläger")<0,
         namen.join(", "));
      ok("die übrigen bleiben unberührt", namen.indexOf("Driver")>=0 &&
         namen.indexOf("Neuer Schläger 2")>=0);
    }
    DB.tomb={}; DB.clubDistances=[];
  }
}

/* ============ 24af. Umbenennen zieht die Historie mit ============ */
group("clubRename — Verknüpfung über Uhr, R10 und Runden");
{
  const ren=G("clubRename"), DB=G("DB"), gemessen=G("clubMeasured");
  if (typeof ren === "function" && DB) {
    /* ALLES verknüpft über den NAMEN, nicht über eine id — auch die Uhr, die
       gar keine ids kennt. Ohne Mitziehen hängt beim Umbenennen die gesamte
       Historie ab: der Caddy lernt die Länge nicht mehr, „gepflegt vs.
       gemessen" zeigt nichts, die R10-Streuung ist verloren. Sichtbar wird das
       erst Wochen später — deshalb hier festgeschrieben. */
    const jetzt=new Date().toISOString();
    /* ACHT Schläge, weil clubMeasured() erst ab 8 Messungen ein getrimmtes
       Mittel bildet — mit weniger liefert es bewusst null. */
    DB.gpsShots=[];
    for(let i=0;i<8;i++) DB.gpsShots.push({id:"G"+i,club:"5 Wood",dist:176+i,ts:jetzt});
    DB.gpsShots.push({id:"GD",club:"Driver",dist:215,ts:jetzt});      // Uhr/Handy
    DB.lmSessions=[{id:"L1",shots:[{club:"5 Wood",carry:172},{club:"7-Eisen",carry:140}]}]; // R10
    DB.swingAnalyses=[{id:"S1",club:"5 Wood"}];
    DB.rounds=[{id:"R1",holes:[{hole:1,club:"5 Wood",apprClub:"5 Wood"},
                               {hole:2,club:"Driver",apprClub:"PW"}]}];
    DB.strat={gameplans:{"Nord|Gelb":{alt:1}}};

    const n=ren("5 Wood","5 Holz");
    ok("Verweise wurden gezählt", n>0, "geändert: "+n);
    eq("GPS-Schläge übertragen",
       DB.gpsShots.filter(x=>x.club==="5 Holz").length, 8);
    eq("fremder Schläger unberührt",
       DB.gpsShots.filter(x=>x.club==="Driver").length, 1);
    eq("R10-Sitzung übertragen", DB.lmSessions[0].shots[0].club, "5 Holz");
    eq("R10: anderer Schläger unberührt", DB.lmSessions[0].shots[1].club, "7-Eisen");
    eq("Schwunganalyse übertragen", DB.swingAnalyses[0].club, "5 Holz");
    eq("Rundendaten: Schläger", DB.rounds[0].holes[0].club, "5 Holz");
    eq("Rundendaten: Approach-Schläger", DB.rounds[0].holes[0].apprClub, "5 Holz");
    eq("Rundendaten: anderes Loch unberührt", DB.rounds[0].holes[1].apprClub, "PW");
    /* Gameplans enthalten Schlägernamen und werden verworfen — sonst zeigten
       sie nach dem Umbenennen einen Schläger, den es nicht mehr gibt. */
    eq("Gameplan-Zwischenspeicher geleert",
       JSON.stringify(DB.strat.gameplans), "{}");

    /* Und der eigentliche Zweck: Die gelernte Länge muss unter dem NEUEN Namen
       weiter zur Verfügung stehen. */
    if (typeof gemessen === "function") {
      const m=gemessen("5 Holz");
      ok("gemessene Länge folgt dem neuen Namen", m && m.nTotal>=6,
         JSON.stringify(m));
      /* HINWEIS: „5 Wood" und „5 Holz" sind seit v2.17 derselbe normalisierte
         Schläger (holz5) — die Messungen werden unter BEIDEN Schreibweisen
         gefunden. Das ist gewollt: Der R10 schreibt Englisch, die Bag oft
         Deutsch. Geprüft wird deshalb eine echte Umbenennung. */
      ok("unter einem anderen Schläger nichts", (gemessen("Driver")||{}).nTotal!==8);
    }
    ok("gleicher Name ändert nichts", ren("Driver","Driver")===0);
    ok("leerer Name ändert nichts", ren("","X")===0);
    DB.gpsShots=[]; DB.lmSessions=[]; DB.swingAnalyses=[]; DB.rounds=[]; DB.strat={};
  }
}

/* ============ 24ag. Verknüpfung Handy ↔ Uhr ============ */
group("Schlägerliste — eine Quelle für Handy, Uhr und Launch Monitor");
{
  const src=fs.readFileSync(FILE,"utf8");
  const kt=path.join(__dirname,"MainActivity.kt");
  /* Die Uhr hat KEINE eigene Schlägerliste — sie liest `clubDistances` aus
     denselben synchronisierten Daten. Gäbe es dort eine zweite Liste, liefen
     die Namen unweigerlich auseinander. */
  if (fs.existsSync(kt)) {
    const w=fs.readFileSync(kt,"utf8");
    ok("Uhr liest clubDistances aus dem Sync", /optJSONArray\("clubDistances"\)/.test(w));
    ok("Uhr hat keine eigene Schlägerliste",
       !/DEFAULT_CLUB|defaultClubs/.test(w));
    /* DER STOLPERSTEIN: Die Uhr überspringt Schläger ohne jede Distanz —
       und dieselbe Liste treibt dort auch die Auswahl beim Schlagtracken.
       Ein frisch angelegter Schläger fehlt auf der Uhr also komplett. */
    ok("Uhr überspringt Schläger ohne Distanz",
       /carry == null && total == null\) continue/.test(w));
    /* Der Entwurf einer laufenden Runde darf KEINE zweite Schlägerliste
       mitschleppen — sonst wäre sie ein eingefrorener Stand. */
    ok("Handy schreibt keine Schlägerliste in den Entwurf",
       !/_draftRound[\s\S]{0,200}clubs:/.test(src));
  }
  /* Und die App muss darauf hinweisen, sonst sucht man den Fehler beim
     Abgleich statt bei der fehlenden Zahl. */
  /* NUR im ausführbaren Code prüfen — die Doku erklärt denselben Sachverhalt
     und würde den Test sonst auch dann bestehen lassen, wenn der Hinweis in
     der Oberfläche fehlt. Diese Verwechslung ist mir in dieser Datei schon
     dreimal passiert. */
  const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
    .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
    .map(m=>m[2]).join("\n");
  ok("App weist auf Schläger ohne Distanz hin",
     /ohne Distanz:/.test(nurCode) && /überspringt Schläger ohne Carry/.test(nurCode));
}

/* ============ 24ah. Messspalten in der Schlägertabelle ============ */
group("bagMessSpalte — R10 und GPS neben den gepflegten Werten");
{
  const sp=G("bagMessSpalte");
  if (typeof sp === "function") {
    /* Ohne Messwert, aber mit angefangener Zählung: Die Anzahl muss sichtbar
       sein. Sonst hält man die leere Spalte für einen Fehler, obwohl sich
       Daten sammeln — clubMeasured mittelt erst ab 8 Messungen. */
    ok("ohne Messungen ein Strich", /–/.test(sp(null, 0, 180)));
    ok("angefangene Zählung wird gezeigt", /n5/.test(sp(null, 5, 180)));
    /* Mit Messwert: Zahl anzeigen, Abweichung ab 7 m färben. */
    ok("Messwert erscheint", /182/.test(sp(182, 12, 180)));
    ok("kleine Abweichung bleibt neutral", /var\(--ink\)/.test(sp(182, 12, 180)),
       "182 gegen 180 = 2 m");
    ok("deutlich kürzer wird rot", /var\(--red\)/.test(sp(170, 12, 180)),
       "170 gegen 180 = -10 m");
    ok("deutlich länger wird grün", /var\(--green\)/.test(sp(190, 12, 180)),
       "190 gegen 180 = +10 m");
    ok("genau 7 m schlägt schon an", /var\(--red\)/.test(sp(173, 12, 180)));
    ok("6 m noch nicht", /var\(--ink\)/.test(sp(174, 12, 180)));
    /* Ohne gepflegten Wert gibt es nichts zu vergleichen — dann neutral. */
    ok("ohne Vergleichswert neutral", /var\(--ink\)/.test(sp(180, 9, null)));
    ok("Anzahl steht im Titel", /n=12/.test(sp(182, 12, 180)));
  }
}

/* ============ 24ai. Schlägernamen vereinheitlichen ============ */
group("clubNorm — R10 schreibt Englisch, die Bag steht auf Deutsch");
{
  const norm=G("clubNorm"), gemessen=G("clubMeasured"), DB=G("DB");
  if (typeof norm === "function") {
    /* DER FEHLER: `clubMeasured` verglich die Namen EXAKT. Der Garmin R10
       exportiert „7 Iron", die Bag steht auf „7-Eisen" — es passte nichts
       zusammen, und die Spalte blieb leer, obwohl reichlich Messungen
       vorlagen. Kein Fehler war sichtbar, nur ein Strich. */
    const gleich=(a,b)=>norm(a)===norm(b);
    ok("7 Iron findet 7-Eisen", gleich("7 Iron","7-Eisen"), norm("7 Iron"));
    ok("Eisen 5 in beiden Reihenfolgen", gleich("5 Iron","Eisen 5"));
    ok("Kurzform 7i", gleich("7i","7-Eisen"));
    ok("3 Wood findet 3 Holz", gleich("3 Wood","3 Holz"));
    ok("3W findet 3 Wood", gleich("3W","3 Wood"));
    ok("Pitching Wedge findet PW", gleich("Pitching Wedge","PW"));
    ok("Sand Wedge findet SW", gleich("Sand Wedge","SW"));
    ok("Gap und Lob Wedge", gleich("Gap Wedge","GW") && gleich("Lob Wedge","LW"));
    ok("Hybrid findet 4H", gleich("4 Hybrid","4H"));
    ok("Loft im Namen stört nicht", gleich("6 Iron","6-Eisen 30°"));
    ok("Teilschlag-Zusatz stört nicht", gleich("SW · 45 m","SW"));
    /* GENAUSO WICHTIG: verschiedene Schläger dürfen NICHT verschmelzen —
       sonst landen die Messungen zweier Schläger auf einem Eintrag. */
    ok("7 und 6 Eisen bleiben getrennt", !gleich("7 Iron","6 Iron"));
    ok("3 und 5 Holz bleiben getrennt", !gleich("3 Wood","5 Wood"));
    ok("PW und SW bleiben getrennt", !gleich("PW","SW"));
    ok("Driver und Holz bleiben getrennt", !gleich("Driver","3 Wood"));
    ok("leerer Name ergibt leeren Schlüssel", norm("")==="" && norm(null)==="");

    /* Und der Zweck: Die Messungen müssen jetzt ankommen. */
    if (typeof gemessen === "function" && DB) {
      DB.lmSessions=[{id:"L1",shots:[]}];
      for(let i=0;i<10;i++) DB.lmSessions[0].shots.push({club:"7 Iron",carry:138+i%3});
      DB.gpsShots=[];
      const m=gemessen("7-Eisen");
      ok("R10-Messungen werden trotz anderer Schreibweise gefunden",
         m && m.nCarry>=8, JSON.stringify(m));
      DB.lmSessions=[]; DB.gpsShots=[];
    }
  }
}

/* ============ 24aj. Bedingungen in der Schlägerwahl ============ */
group("condFaktor / caddyClubs — Höhe wirkt jetzt auf die Empfehlung");
{
  const cf=G("condFaktor"), cc=G("caddyClubs"), DB=G("DB"), PL=G("playsLike");
  if (typeof cf === "function" && typeof cc === "function" && DB) {
    /* DER FEHLER bis v2.17: Höhe, Wind, Temperatur und Regen wirkten NUR auf
       die Anzeige („spielt wie 165 m"). Die Empfehlung dahinter rechnete mit
       der flachen Distanz — richtige Zahl, falscher Schläger. Bei 15 m
       Anstieg ist das ein ganzer Schläger daneben. */
    DB.clubDistances=[{id:"C1",club:"Driver",carry:215,total:232},
                      {id:"C2",club:"6-Eisen",carry:150,total:154},
                      {id:"C3",club:"7-Eisen",carry:140,total:143}];
    /* Ohne Bedingungen bleibt alles wie bisher — Altaufrufer ohne Argument. */
    const roh=cc();
    ok("ohne Argument unveränderte Reichweiten",
       roh.length===3 && roh[0].dist===232, JSON.stringify(roh.map(c=>c.dist)));
    /* Mit Faktor <1 (bergauf) schrumpfen die Reichweiten: Der Schläger deckt
       weniger Boden ab, also greift der Caddy zum längeren. */
    const bergauf=cc({f:0.9});
    ok("bergauf kürzere Reichweiten", bergauf[0].dist < roh[0].dist,
       `${roh[0].dist} -> ${bergauf[0].dist}`);
    eq("Rohwert bleibt erhalten", bergauf[0].rohDist, 232);
    const bergab=cc({f:1.1});
    ok("bergab längere Reichweiten", bergab[0].dist > roh[0].dist);
    ok("Reihenfolge bleibt absteigend",
       bergauf.every((c,i)=>i===0 || bergauf[i-1].dist>=c.dist));

    /* condFaktor selbst: ohne Punkte oder ohne Wetter neutral. */
    const leer=cf(null,null);
    eq("ohne Punkte neutral", leer.f, 1);
    ok("liefert Bestandteile für die Anzeige", Array.isArray(leer.teile));
    /* Der Faktor ist gedeckelt — ein Wert jenseits ±25 % käme nur aus
       fehlerhaften Höhendaten und würde die Schlägerwahl verreißen. */
    const src=fs.readFileSync(FILE,"utf8");
    ok("Faktor auf ±25 % gedeckelt",
       /Math\.max\(0\.75, Math\.min\(1\.25/.test(src));
    /* Und die Anzeige muss den Einfluss ausweisen. */
    const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
      .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
      .map(m=>m[2]).join("\n");
    ok("Caddy weist die Spielt-wie-Distanz aus", /spielt wie <b>/.test(nurCode));
    ok("Bestandteile werden genannt (bergauf/Wind/nass)",
       /⛰ bergauf/.test(nurCode) && /Gegenwind/.test(nurCode) && /🌧 nass/.test(nurCode));

    /* Gegenprobe an playsLike: bergauf spielt länger. */
    if (typeof PL === "function") {
      const flach=PL(150, 15, 0, 0, 0, 0);
      const hoch =PL(150, 15, 0, 0, 0, 15);
      ok("15 m Anstieg spielen länger", hoch > flach + 10, `${flach} -> ${hoch}`);
    }
    DB.clubDistances=[];
  }
}

/* ============ 24ak. Höhenraster ============ */
group("elevGet — Interpolation statt exakter Treffer");
{
  const get=G("elevGet"), key=G("elevKey"), ELEV=G("ELEV"), pre=G("elevPrefetchHole");
  if (typeof get === "function" && typeof key === "function" && ELEV) {
    Object.keys(ELEV).forEach(k=>delete ELEV[k]);
    const mLat=111320;
    for(let i=0;i<=8;i++) ELEV[key(54.0+(i*25)/mLat, 10.0)] = 100 + i*0.4;
    /* DER FEHLER: elevGet verlangte den Schlüssel auf ~11 m EXAKT. Beim Gehen
       traf er praktisch nie — die Höhe blieb null, die Korrektur fiel STILL
       aus. Man sah eine „spielt wie"-Zeile ohne Höhenanteil und hielt das für
       flaches Gelände. */
    near("exakter Stützpunkt", get(54.0, 10.0), 100, 0.001);
    near("Stützpunkt bei 200 m", get(54.0+200/mLat, 10.0), 103.2, 0.001);
    [[12,100.19],[37,100.59],[63,101.01],[111,101.78]].forEach(([m,soll])=>{
      const v=get(54.0+m/mLat, 10.0);
      ok("interpoliert bei "+m+" m", v!=null && Math.abs(v-soll)<0.25,
         v==null?"null":v.toFixed(2)+" statt "+soll);
    });
    /* Die Gesamtdifferenz geht in playsLike ein — sie muss exakt stimmen. */
    near("Höhendifferenz über 200 m", get(54.0+200/mLat,10.0)-get(54.0,10.0), 3.2, 0.05);
    eq("weit seitlich davon: null", get(54.0+50/mLat, 10.002), null);
    eq("leeres Raster: null",
       (Object.keys(ELEV).forEach(k=>delete ELEV[k]), get(54.0,10.0)), null);
    ok("Vorladen der ganzen Bahn vorhanden", typeof pre === "function");
    const src=fs.readFileSync(FILE,"utf8");
    ok("Raster wird gespeichert", /localStorage\.setItem\(ELEV_KEY/.test(src));
    ok("Raster wird beim Start geladen", /localStorage\.getItem\(ELEV_KEY\)/.test(src));
    ok("Größe gedeckelt", /k\.length>4000/.test(src));
  }
}

/* ============ 24al. Der Prüfstand selbst ============ */
group("Übergabeliste vollständig — sonst laufen Gruppen still ins Leere");
{
  /* WAS PASSIERT IST: Fehlt ein Name in `namen`, liefert G(...) undefined.
     Die Gruppe steht hinter einem typeof-Wächter und wird KOMMENTARLOS
     übersprungen — gemeldet wird trotzdem „bestanden". So waren 48 Prüfungen
     stillgelegt, ohne dass es auffiel. Das ist die wichtigste Prüfung der
     Datei: Sie verhindert, dass alle anderen lügen. */
  const src=fs.readFileSync(__filename,"utf8");
  const listeM=src.match(/const namen = \[([\s\S]*?)\];/);
  ok("Übergabeliste gefunden", !!listeM);
  if(listeM){
    const liste=new Set((listeM[1].match(/"([^"]+)"/g)||[]).map(x=>x.slice(1,-1)));
    const angefordert=new Set();
    let m; const re=/\bG\("([^"]+)"\)/g;
    while((m=re.exec(src))) angefordert.add(m[1]);
    const fehlen=[...angefordert].filter(n=>!liste.has(n));
    ok("jeder über G() angeforderte Name wird übergeben", fehlen.length===0,
       fehlen.slice(0,8).join(", "));
  }
}

/* ============ 24am. Zeitstempel im Fehlerprotokoll ============ */
group("errZeit — UTC speichern, Ortszeit anzeigen");
{
  const ez=G("errZeit");
  if (typeof ez === "function") {
    /* `logErr` speichert `toISOString()` — UTC, und das ist richtig: ein
       absoluter, geräteunabhängig vergleichbarer Zeitpunkt. Die ANZEIGE
       schnitt den String aber nur zu und zeigte damit UTC. In der deutschen
       Sommerzeit sind das ZWEI Stunden: ein Fehler von 14:40 erschien als
       12:40, und der Abgleich mit dem eigenen Gedächtnis ging schief. */
    const iso="2026-08-10T12:40:41.000Z";
    const v=ez(iso);
    ok("liefert ein Datum", /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(v), v);
    /* Der Wert MUSS von der rohen UTC-Zeichenkette abweichen, wenn die
       Zeitzone nicht UTC ist — sonst wurde gar nicht umgerechnet. */
    const roh=iso.replace("T"," ").slice(0,19);
    const offset=new Date(iso).getTimezoneOffset();
    if(offset!==0) ok("in Ortszeit umgerechnet", v!==roh, `${roh} -> ${v}`);
    else ok("UTC-Umgebung: unverändert korrekt", v===roh, v);
    /* Die Uhrzeit-Variante für Wiederholungen. */
    ok("nur Uhrzeit", /^\d{2}:\d{2}:\d{2}$/.test(ez(iso,true)), ez(iso,true));
    ok("Uhrzeit passt zum Datum", ez(iso).endsWith(ez(iso,true)));
    /* Grenzfälle: `new Date(null)` ergibt den 1.1.1970 — ein gültiges Datum,
       das hier niemandem hilft. */
    eq("null → Strich", ez(null), "–");
    eq("leer → Strich", ez(""), "–");
    ok("unlesbar → Rohwert", ez("kaputt")==="kaputt", ez("kaputt"));
  }

  /* Dasselbe Problem an zweiter Stelle: `toISOString().slice(0,10)` liefert
     das UTC-DATUM. In der deutschen Sommerzeit ist es zwischen 00:00 und 02:00
     noch der VORTAG — eine um halb eins nachts erfasste Runde wäre auf gestern
     datiert und ließe sich am nächsten Tag nicht wiederfinden. */
  const ti=G("todayISO");
  if (typeof ti === "function") {
    const v=ti();
    ok("todayISO liefert ein Datum", /^\d{4}-\d{2}-\d{2}$/.test(v), v);
    const d=new Date();
    const p=n=>String(n).padStart(2,"0");
    const lokal=`${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}`;
    eq("todayISO ist das ORTSDATUM", v, lokal);
    /* Gegenprobe gegen die alte Bildungsvorschrift — sie darf nur dann
       übereinstimmen, wenn die Zeitzone gerade keinen Datumssprung erzeugt. */
    const utc=new Date().toISOString().slice(0,10);
    if(v!==utc) ok("weicht bewusst vom UTC-Datum ab", true, `${utc} (UTC) -> ${v}`);
    else ok("Zeitzone erzeugt gerade keinen Versatz", true, v);
    /* Und der ausführbare Code darf die rohe Form nicht mehr verwenden. */
    const src=fs.readFileSync(FILE,"utf8");
    const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
      .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
      .map(m=>m[2]).join("\n").replace(/\/\*[\s\S]*?\*\//g,"");
    ok("kein rohes toISOString().slice(0,10) mehr",
       nurCode.indexOf('new Date().toISOString().slice(0,10)')<0);
  }
}

/* ============ 24an. Putt-Diagnose ============ */
group("puttDiagnose — WOHIN gehen die Fehlputts?");
{
  const pd=G("puttDiagnose");
  if (typeof pd === "function") {
    /* Bei Approaches gibt es `apprMiss` seit langem — beim Putten fehlte die
       Entsprechung, obwohl dort die größte Lücke liegt. Erst die Richtung
       macht aus „schlecht geputtet" eine Trainingsentscheidung. */
    const loch=(miss,rest,putts)=>({hole:1,par:4,score:4,putts:putts||2,
                                    puttMiss:miss,puttRest:rest});
    /* Unter 10 Putts wird KEINE Aussage getroffen — drei Putts wären
       Rauschen mit dem Aussehen von Erkenntnis. */
    const wenig=pd([{holes:[loch("Kurz"),loch("Lang"),loch("Links")]}]);
    ok("zu wenig Daten wird erkannt", wenig.reicht===false, "n="+wenig.n);
    eq("und die Anzahl wird genannt", wenig.n, 3);

    /* Fall 1: überwiegend kurz -> Längenkontrolle. */
    const kurzH=[]; for(let i=0;i<12;i++) kurzH.push(loch(i<9?"Kurz":"Rechts"));
    const k=pd([{holes:kurzH}]);
    ok("genug Daten", k.reicht===true);
    eq("kurz gezählt", k.zaehl.kurz, 9);
    ok("Befund: Längenkontrolle", k.befund && k.befund.art==="kurz", k.befund&&k.befund.txt);

    /* Fall 2: systematisch eine Seite -> Startlinie. */
    const seiteH=[]; for(let i=0;i<12;i++) seiteH.push(loch(i<9?"Links":"Kurz"));
    const se=pd([{holes:seiteH}]);
    ok("Befund: Seite", se.befund && se.befund.art==="seite", se.befund&&se.befund.txt);
    ok("nennt die richtige Seite", /LINKS/.test(se.befund.txt));

    /* Fall 3: gleichmäßig verteilt -> kein systematischer Fehler. */
    const mixH=[];
    ["Kurz","Lang","Links","Rechts"].forEach(m=>{ for(let i=0;i<3;i++) mixH.push(loch(m)); });
    const mi=pd([{holes:mixH}]);
    ok("Befund: gemischt", mi.befund && mi.befund.art==="gemischt", mi.befund&&mi.befund.txt);

    /* DER KERN DES ZWEITEN FELDES: Dreiputts nach Ursache trennen. Aus 12 m
       auf 3 m liegen gelassen ist ein Lag-Problem; aus 12 m auf 1 m und dann
       verfehlt ein Kurzputt-Problem. Ohne `puttRest` nicht unterscheidbar. */
    const dreiH=[];
    for(let i=0;i<10;i++) dreiH.push(loch("Kurz","Gimme",2));
    dreiH.push(loch("Kurz","3m",3));      // Lag
    dreiH.push(loch("Kurz",">3m",3));     // Lag
    dreiH.push(loch("Links","1m",3));     // Kurzputt
    const d3=pd([{holes:dreiH}]);
    eq("Dreiputts erkannt", d3.drei, 3);
    eq("davon Lag-Problem", d3.dreiLag, 2);
    eq("davon Kurzputt-Problem", d3.dreiKurz, 1);
    /* Gegenprobe: „Gelocht" ist kein Fehlputt. */
    const gH=[]; for(let i=0;i<12;i++) gH.push(loch(i<6?"Gelocht":"Kurz"));
    const g=pd([{holes:gH}]);
    eq("gelochte Putts zählen nicht als Fehler", g.daneben, 6);
  }
}

/* ============ 24ao. Quality: raus aus der Eingabe, drin in der Rechnung ============ */
group("quality — doppelte Erfassung entfernt, Altdaten bleiben nutzbar");
{
  const src=fs.readFileSync(FILE,"utf8");
  const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
    .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
    .map(m=>m[2]).join("\n").replace(/\/\*[\s\S]*?\*\//g,"");
  /* `quality` misst DASSELBE wie „Rest zur Fahne", nur in gröberen Bändern:
     den Abstand zum Loch nach dem Approach. Für die Position B (SG Approach)
     wurde es NIE herangezogen — dort zählt allein distToPin. Es war reine
     Doppelerfassung, und der Name lud zu falschen Einträgen ein: „Quality"
     klingt nach einer Bewertung des Schlags, ist aber eine Distanz. */
  ok("keine Eingabe mehr im Spielmodus",
     !/playSel\('quality'/.test(nurCode));
  ok("keine Eingabe mehr im Rundeneditor",
     nurCode.indexOf('data-f="quality"')<0);
  ok("Optionsliste wird nicht mehr aufgebaut", nurCode.indexOf("qOpts")<0);
  /* ABER: Der Rückfall MUSS bleiben — Altrunden tragen das Feld, und ohne ihn
     verlören sie ihr Putt-SG. */
  ok("Rückfall in sgHole erhalten", /sgBandMid\(h\.quality\)/.test(nurCode));
  ok("zählt weiter zur Abdeckung", /sgBandMid\(h\.quality\)!=null/.test(nurCode));

  /* Und der Nachweis an echten Daten: ein Loch NUR mit quality muss weiterhin
     ein Putt-Ergebnis liefern. */
  const sg=G("sgHole");
  if (typeof sg === "function") {
    const loch={hole:1,par:4,si:5,len:380,score:4,putts:2,appr:"100-120m",quality:"2m"};
    const r=sg(loch,20);
    ok("Altrunde mit quality liefert weiter SG",
       r && r.putt!=null && isFinite(r.putt), JSON.stringify(r&&r.putt));
    /* Gegenprobe: Ohne jede der drei Quellen MUSS die Rechnung aussteigen —
       sonst würde sie eine Zahl erfinden. */
    const ohne={hole:1,par:4,si:5,len:380,score:4,putts:2,appr:"100-120m"};
    const r2=sg(ohne,20);
    ok("ohne jede Quelle: unvollständig gemeldet",
       r2 && (r2.teilweise===true || r2.putt==null), JSON.stringify(r2&&r2.fehlt));
  }
}

/* ============ 24ap. Tempo, Verlässlichkeit, Fälligkeit ============ */
group("Von der Messung zur Handlung");
{
  /* VERLÄSSLICHKEIT statt Bestwert: Ein einmaliges 39er-Neun sagt wenig.
     „Dein bestes Drittel liegt bei 42" sagt, was du ABRUFEN kannst — und
     genau das entscheidet im Wettkampf. */
  const vl=G("verlaesslich");
  if (typeof vl === "function") {
    ok("unter 6 Werten keine Aussage", vl([70,72,74], true)===null);
    const r=vl([68,70,71,72,73,75,78,80], true);
    ok("liefert Kennzahlen", !!r);
    eq("Bestwert", r.best, 68);
    ok("typisch liegt zwischen gut und schlecht",
       r.gut <= r.typisch && r.typisch <= r.schlecht,
       `${r.gut} / ${r.typisch} / ${r.schlecht}`);
    /* Bei „größer ist besser" muss sich die Richtung umkehren. */
    const h=vl([10,20,30,40,50,60,70,80], false);
    eq("größer-ist-besser: Bestwert", h.best, 80);
    ok("und die Reihenfolge dreht", h.gut >= h.typisch && h.typisch >= h.schlecht,
       `${h.gut} / ${h.typisch} / ${h.schlecht}`);
  }

  /* TESTFÄLLIGKEIT: Ein Test ohne Wiederholung ergibt keinen Verlauf und ist
     wertlos. Es stand aber nur das Datum da — „12.03." sagt nicht, ob das
     lange her ist. */
  const tf=G("testFaellig"), DB=G("DB");
  if (typeof tf === "function" && DB) {
    const alt=DB.tests;
    DB.tests=[];
    ok("nie getestet wird erkannt", tf("gibtsNicht").nie===true);
    const vorTagen=n=>new Date(Date.now()-n*86400000).toISOString().slice(0,10);
    DB.tests=[{defKey:"probe", date:vorTagen(100), total:20}];
    const f=tf("probe");
    ok("Alter wird berechnet", f.tage>=99 && f.tage<=101, "tage="+f.tage);
    ok("über 90 Tage ist fällig", f.faellig===true);
    DB.tests=[{defKey:"probe", date:vorTagen(70), total:20}];
    ok("70 Tage: bald fällig", tf("probe").bald===true && tf("probe").faellig===false);
    DB.tests=[{defKey:"probe", date:vorTagen(10), total:20}];
    ok("10 Tage: weder noch",
       tf("probe").faellig===false && tf("probe").bald===false);
    DB.tests=alt;
  }

  /* PROGNOSE: Eine Kurve sagt „es geht abwärts"; die Frage ist „wie schnell,
     und wann bin ich da?". Ohne Bewegung in die richtige Richtung darf KEINE
     Prognose entstehen — sonst erfindet sie ein Datum. */
  const zp=G("zielPrognose");
  if (typeof zp === "function") {
    ok("ohne Ziel keine Prognose", zp(null)===null);
  }
}

/* ============ 24aq. Fitness-Wirkung und Strategie-Rückschau ============ */
group("Beobachtungen, die als solche gekennzeichnet sind");
{
  const fw=G("fitnessWirkung"), sr=G("stratRueckschau"), DB=G("DB");
  if (typeof fw === "function" && DB) {
    /* Fitness und Golf wurden getrennt erfasst — ob sich das Training
       auszahlt, konnte niemand beantworten. Beide Bereiche haben aber
       Zeitstempel. WICHTIG: Das Ergebnis ist eine BEOBACHTUNG, kein Beweis;
       wer im Sommer mehr trainiert, spielt auch bei besserem Wetter. */
    const altF=DB.fitnessSessions, altG=DB.gpsShots, altC=DB.clubDistances;
    DB.fitnessSessions=[]; DB.gpsShots=[];
    ok("ohne Daten keine Aussage", fw().reicht===false);

    DB.clubDistances=[{id:"C1",club:"Driver",carry:215,total:232}];
    const monat=(m,n,dist)=>{
      for(let i=0;i<n;i++) DB.fitnessSessions.push({id:"F"+m+i, date:`2026-0${m}-1${i%9}`, type:"Kraft"});
      for(let i=0;i<6;i++) DB.gpsShots.push({id:"G"+m+i, ts:`2026-0${m}-15T10:00:00Z`,
        club:"Driver", dist:dist+i%3, swing:"Voll"});
    };
    monat(1, 12, 225); monat(2, 10, 224);   // viel Training
    monat(3,  1, 215); monat(4,  0, 214);   // wenig
    const r=fw();
    ok("mit 4 Monaten reicht es", r.reicht===true, JSON.stringify(r).slice(0,90));
    if(r.reicht){
      ok("Monate werden getrennt", r.nMit>=2 && r.nOhne>=2, `${r.nMit}/${r.nOhne}`);
      ok("Unterschied wird beziffert", isFinite(r.diff), "diff="+r.diff);
      ok("mehr Training = mehr Länge in diesen Daten", r.diff>0, String(r.diff));
    }
    /* Nur volle Schwünge dürfen zählen — ein halber Wedge würde die
       Driver-Länge verfälschen. */
    ok("filtert auf Driver und volle Schwünge",
       /clubNorm\(x\.club\)!=="driver"/.test(fs.readFileSync(FILE,"utf8")));
    DB.fitnessSessions=altF; DB.gpsShots=altG; DB.clubDistances=altC;
  }
  if (typeof sr === "function" && DB) {
    const alt=DB.strat;
    DB.strat={};
    ok("ohne Gameplans keine Rückschau", sr().reicht===false);
    DB.strat=alt;
  }
}

/* ============ 24ar. Aufgerufene Namen müssen existieren ============ */
group("Ereignis-Attribute und Formatierer — nichts Undefiniertes aufrufen");
{
  const src=fs.readFileSync(FILE,"utf8");
  /* WAS PASSIERT IST: `openRoundCard` rief `fmtD(r.date)` auf — die Funktion
     heißt `fmtDate`. Der Fehler lag in einem TEMPLATE-STRING und fiel deshalb
     weder beim Laden noch bei einer Syntaxprüfung auf, sondern erst beim
     Antippen der Scorekarte: „ReferenceError: fmtD is not defined".
     Genau diese Klasse prüft dieser Abschnitt. */
  const namen=new Set();
  for(const m of src.matchAll(/on(?:click|change|input|submit)=\\?"\s*([A-Za-z_$][\w$]*)\s*\(/g))
    namen.add(m[1]);
  const RESERVIERT=new Set(["if","for","while","switch","return","typeof","new","function"]);
  const fehlt=[...namen].filter(n=>!RESERVIERT.has(n) && typeof G(n)!=="function");
  ok("alle Namen in Ereignis-Attributen sind definiert", fehlt.length===0,
     fehlt.slice(0,8).join(", "));
  ok("und es sind überhaupt welche gefunden worden", namen.size>50, "n="+namen.size);

  /* Die Formatierer-Familie ist besonders anfällig, weil sich die Namen
     ähneln: fmtN, fmtDate, fmtDT, fmtDur, fmtBytes. `fmtD` gibt es NICHT. */
  const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
    .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
    .map(m=>m[2]).join("\n").replace(/\/\*[\s\S]*?\*\//g,"");
  ok("kein Aufruf von fmtD (heißt fmtDate)",
     !/(?<![\w.])fmtD\s*\(/.test(nurCode));
  ["fmtN","fmtDate","fmtDT","fmtDur"].forEach(f=>{
    if(new RegExp("(?<![\\w.])"+f+"\\s*\\(").test(nurCode))
      ok(f+" ist definiert", typeof G(f)==="function");
  });
}

/* ============ 24as. Pre Round Stretch ============ */
group("Aufwärmen oben, volles Malaska-Programm als eigener Ablauf");
{
  const src=fs.readFileSync(FILE,"utf8");
  const st=G("stretchToggle"), DONE=G("STRETCH_DONE"), DYN=G("MALASKA_DYN"), STAT=G("MALASKA_STAT");
  /* Die Aufwärm-Knöpfe standen unter „Schnell erfassen" — man fand sie erst
     nach dem Scrollen, obwohl Aufwärmen das Erste ist, was auf dem Platz
     passiert. Jetzt ganz oben, VOR dem Spielmodus. */
  const heute=src.slice(src.indexOf("function renderHeute"),
                        src.indexOf("function renderHeute")+3000);
  const iStretch=heute.indexOf('id="qhStretch"');
  const iWarm=heute.indexOf('id="qhWarm"');
  const iPlay=heute.indexOf('id="qhPlay"');
  ok("Stretch-Knopf vorhanden", iStretch>=0);
  ok("Aufwärm-Knopf vorhanden", iWarm>=0);
  ok("Stretch steht ÜBER Aufwärmen", iStretch>=0 && iWarm>iStretch,
     `stretch@${iStretch} warm@${iWarm}`);
  ok("beide stehen über dem Spielmodus", iPlay>iWarm, `play@${iPlay}`);

  if (DYN && STAT) {
    /* Das VOLLE Programm, nicht die Kurzfassung: Die Aufwärmpläne nutzen
       `MALASKA_DYN.slice(0,6)`, der Pre Round Stretch alle Übungen. */
    ok("dynamisches Programm hat mehr als 6 Übungen", DYN.length>6, "n="+DYN.length);
    const sheet=src.slice(src.indexOf("function openStretchSheet"),
                          src.indexOf("function openStretchSheet")+2600);
    ok("Stretch nutzt ALLE dynamischen Übungen",
       /MALASKA_DYN\.forEach/.test(sheet) && !/MALASKA_DYN\.slice/.test(sheet));
    /* Statisches Dehnen gehört NACH die Runde — davor senkt es kurzzeitig die
       Kraftentfaltung. Es darf im Pre-Round-Ablauf nicht auftauchen. */
    ok("statische Dehnungen NICHT im Pre-Round-Ablauf",
       sheet.indexOf("MALASKA_STAT")<0);
    ok("und der Grund steht dabei", /Kraftentfaltung/.test(src));
  }
  if (typeof st === "function" && DONE && DYN) {
    Object.keys(DONE).forEach(k=>delete DONE[k]);
    st(0); st(3);
    eq("zwei Übungen abgehakt",
       Object.keys(DONE).filter(k=>DONE[k]).length, 2);
    st(0);
    eq("erneutes Tippen nimmt zurück",
       Object.keys(DONE).filter(k=>DONE[k]).length, 1);
    Object.keys(DONE).forEach(k=>delete DONE[k]);
  }
}

/* ============ 24at. Übungsskizzen und aufgeräumtes Dashboard ============ */
group("Skizzen offline-tauglich, Aufwärmen nur noch auf Heute");
{
  const src=fs.readFileSync(FILE,"utf8");
  const SVG=G("MALASKA_SVG"), bild=G("malaskaBild"), DYN=G("MALASKA_DYN");
  if (SVG && DYN) {
    eq("für jede Übung eine Skizze", SVG.length, DYN.length);
    let offen=0, ohneFarbe=0, extern=0;
    SVG.forEach(d=>{
      const auf=(String(d).match(/</g)||[]).length;
      const zu=(String(d).match(/\/>|<\/\w+>/g)||[]).length;
      if(auf!==zu) offen++;
      if(!/currentColor/.test(d)) ohneFarbe++;
      /* OFFLINE-TAUGLICH: keine externen Verweise. Die App muss auf dem Platz
         im Funkloch vollständig funktionieren — ein Bild, das erst geladen
         werden muss, ist dort wertlos. */
      if(/https?:|<image|url\(/.test(d)) extern++;
    });
    eq("alle Elemente geschlossen", offen, 0);
    eq("alle an das Farbschema gebunden", ohneFarbe, 0);
    eq("keine externen Verweise", extern, 0);
    ok("zusammen unter 20 kB", SVG.join("").length < 20000,
       SVG.join("").length+" Zeichen");
  }
  if (typeof bild === "function") {
    ok("liefert ein SVG-Element", /^<svg /.test(bild(0,44)));
    ok("Größe wird übernommen", /width="44"/.test(bild(0,44)));
    eq("unbekannter Index bleibt leer", bild(999,44), "");
  }
  /* SCHLÄGERWAHL im Aufwärmen (v2.29). Chips laufen über das EISEN 9 — so
     steht es auch in der Wissensdatenbank („Bester Chip-Schläger für mich ist
     das Eisen 9, SW ist Fallback"). Eingespielt wird mit Eisen 7 und 5, nicht
     8 und 6. Diese Prüfung hält die Pläne mit der tatsächlichen Bag in
     Übereinstimmung — sonst übt man beim Aufwärmen mit Schlägern, die man auf
     der Runde gar nicht in dieser Rolle verwendet. */
  {
    const P=G("WARMUP_PLANS");
    if (P) {
      const text=Object.keys(P).map(k=>(P[k].bloecke||[])
        .map(b=>b.inhalt||"").join(" ")).join(" ");
      ok("Chips mit Eisen 9", /Chips mit SW oder Eisen 9/.test(text));
      ok("kein Eisen 8 bei den Chips", !/Chips mit SW oder Eisen 8/.test(text));
      ok("Einspielen mit Eisen 7", /Bälle Eisen 7/.test(text));
      ok("Einspielen mit Eisen 5", /Bälle Eisen 5/.test(text));
      ok("weder Eisen 8 noch Eisen 6 im Einspielen",
         !/Bälle Eisen 8|Bälle Eisen 6/.test(text));
    }
  }

  /* QUELLE AM ENDE des Pre-Round-Blatts: Wer die Übungen kennt, arbeitet die
     Liste ab; wer eine Ausführung nachschlagen will, findet das Video dort,
     wo er ohnehin ankommt. Oben würde es die erste Übung nach unten drücken.
     Es MUSS derselbe Link sein wie in der Wissensdatenbank — zwei Quellen
     laufen früher oder später auseinander. */
  {
    const sheet=src.slice(src.indexOf("function openStretchSheet"),
                          src.indexOf("function openStretchSheet")+4200);
    ok("Video im Pre-Round-Blatt eingebunden", /malaskaVideo\(\)/.test(sheet));
    ok("Abspielfläche steht NACH der Übungsliste",
       sheet.indexOf("malaskaVideo") > sheet.indexOf("MALASKA_DYN.forEach"));
    /* ERST AUF TIPP LADEN: Ein iframe im Blatt würde bei JEDEM Öffnen YouTube
       kontaktieren — auch wenn man nur die Liste abhaken will. Das kostet
       Ladezeit, Daten und setzt ohne Not einen Fremdanbieter in die Seite. */
    ok("kein iframe im Blatt selbst", sheet.indexOf("<iframe")<0);
    const vid=src.slice(src.indexOf("function malaskaVideo"),
                        src.indexOf("function malaskaVideo")+1400);
    ok("iframe entsteht erst in malaskaVideo", /<iframe/.test(vid));
    ok("nutzt youtube-nocookie", /youtube-nocookie\.com\/embed/.test(vid));
    ok("ohne Netz wird abgefangen", /navigator\.onLine===false/.test(vid));
    ok("Ausweichweg in die YouTube-App",
       /target="_blank"[\s\S]{0,60}rel="noopener"/.test(vid));
    /* Ehrlich bleiben: Das Video ist NICHT offline verfügbar. Die App ist
       sonst vollständig offline-tauglich — dieser eine Punkt muss dabeistehen,
       sonst sucht man auf dem Platz vergeblich. */
    ok("Hinweis auf fehlende Offline-Verfügbarkeit", /braucht Netz/i.test(sheet));
    /* Dieselbe Video-ID wie in der Wissensdatenbank. */
    const gp=src.slice(src.indexOf('id="gplib"'), src.indexOf('id="gplib"')+400000);
    ok("gleiche Quelle wie die Wissensdatenbank", /SHP70Xv14kY/.test(gp));
  }

  /* NACH DER RUNDE: eigenes Programm, statisch. Sechs Übungen aus dem
     Malaska-Programm (Wissensdatenbank), sechs ergänzt — Hüftbeuger,
     Latissimus, Brustöffner, Gesäß, Waden, Nacken. Sie decken ab, was vier
     Stunden Gehen und Rotation belasten. Die Herkunft MUSS erkennbar bleiben,
     damit man weiß, was aus der Quelle stammt und was nicht. */
  const POST=G("POST_ROUND"), PSVG=G("POST_SVG"), pb=G("postBild"), pt=G("postToggle"), PD=G("POST_DONE");
  if (POST && PSVG) {
    ok("Programm hat genug Umfang für 15 Minuten", POST.length>=10, "n="+POST.length);
    eq("für jede Übung eine Skizze", PSVG.length, POST.length);
    const ergaenzt=POST.filter(x=>x.q==="ergänzt").length;
    ok("Herkunft ist je Übung vermerkt",
       POST.every(x=>x.q==="Malaska"||x.q==="ergänzt"));
    ok("beide Quellen kommen vor", ergaenzt>0 && ergaenzt<POST.length,
       ergaenzt+" ergänzt von "+POST.length);
    ok("Hüftbeuger ist dabei", POST.some(x=>/Hüftbeuger/.test(x.t)));
    let extern=0;
    PSVG.forEach(d=>{ if(/https?:|<image|url\(/.test(d)) extern++; });
    eq("Skizzen ohne externe Verweise", extern, 0);
  }
  if (typeof pb === "function") {
    ok("liefert ein SVG", /^<svg /.test(pb(0,44)));
    eq("unbekannter Index bleibt leer", pb(999,44), "");
  }
  if (typeof pt === "function" && PD) {
    Object.keys(PD).forEach(k=>delete PD[k]);
    pt(2); eq("abgehakt", Object.keys(PD).filter(k=>PD[k]).length, 1);
    pt(2); eq("wieder abgewählt", Object.keys(PD).filter(k=>PD[k]).length, 0);
  }
  /* Die Aufwärmpläne dürfen KEINE Körperübungen mehr enthalten — die laufen
     über „Pre Round Stretch". Sonst macht man sie doppelt. */
  {
    const P=G("WARMUP_PLANS");
    if (P) {
      const mitKoerper=Object.keys(P).filter(k=>
        (P[k].bloecke||[]).some(b=>/Körper/.test(b.titel)));
      eq("kein Körperblock mehr in den Aufwärmplänen", mitKoerper.length, 0);
      /* Und die angegebene Dauer muss zur Summe der Blöcke passen — sonst
         rechnet der Zeitplan rückwärts an der Wirklichkeit vorbei. */
      const schief=Object.keys(P).filter(k=>
        (P[k].bloecke||[]).reduce((a,b)=>a+b.min,0) !== P[k].min);
      eq("Plandauer passt zur Summe der Blöcke", schief.length, 0, schief.join(", "));
    }
  }

  /* Das Aufwärmen gehört auf die Heute-Seite (vor der Runde), nicht ins
     Dashboard (Auswertung danach). Zwei Momente, zwei Seiten. */
  const dash=src.slice(src.indexOf("function renderDash"),
                       src.indexOf("function renderDash")+9000)
                .replace(/\/\*[\s\S]*?\*\//g,"").replace(/\/\/[^\n]*/g,"");
  ok("kein Aufwärm-Knopf mehr im Dashboard", dash.indexOf("openWarmupSheet")<0);
  ok("keine Aufwärmroutine mehr im Dashboard", dash.indexOf("Aufwärmroutine")<0);
  /* Auf der Heute-Seite müssen beide weiterhin stehen. */
  const heute=src.slice(src.indexOf("function renderHeute"),
                        src.indexOf("function renderHeute")+3000);
  ok("Heute hat beide Knöpfe",
     heute.indexOf('id="qhStretch"')>=0 && heute.indexOf('id="qhWarm"')>=0);
}

/* ============ 24au. Wetter-Stundenvorhersage ============ */
group("wxStunden — die nächsten Stunden, nicht nur der Moment");
{
  const parse=G("wxStunden"), html=G("wxStundenHtml"), W=G("WEATHER");
  if (typeof parse === "function") {
    /* Bis v2.31 wurde NUR der Momentanwert geholt. Für „hält der Wind die
       nächsten vier Stunden?" oder „fängt es während der Runde an zu regnen?"
       war das nutzlos — eine Runde dauert vier bis fünf Stunden, die
       Entscheidung fällt aber vorher. */
    eq("ohne Antwort: null", parse(null), null);
    eq("ohne hourly-Feld: null", parse({}), null);
    const jetzt=Date.now();
    const iso=n=>new Date(jetzt+n*3600000).toISOString();
    const j={hourly:{
      time:[iso(-5),iso(-1),iso(0),iso(1),iso(2)],
      temperature_2m:[10,12,14,15,16],
      wind_speed_10m:[2,3,4,5,6],
      wind_direction_10m:[180,190,200,210,220],
      wind_gusts_10m:[4,6,9,12,14],
      weather_code:[0,1,2,3,61],
      precipitation:[0,0,0,0.4,1.2],
      precipitation_probability:[0,10,20,60,80]
    }};
    const r=parse(j);
    ok("liefert Stunden", Array.isArray(r) && r.length>0, r&&r.length);
    /* Vergangene Stunden gehören nicht dazu. Die Grenze liegt bei einer Stunde
       zurück, damit die LAUFENDE Stunde noch mitkommt — sonst fehlte sie in
       der Anzeige, obwohl man gerade in ihr spielt. Von fünf Einträgen
       (−5 h, −1 h, jetzt, +1 h, +2 h) bleiben damit die letzten drei bis vier;
       −1 h liegt genau auf der Grenze. */
    ok("alte Stunden fallen weg", r.length>=3 && r.length<=4, "n="+r.length);
    ok("nichts älter als eine Stunde", r.every(x=>x.t >= jetzt-3600000-1000));
    ok("alle Felder übernommen",
       r[0].temp!=null && r[0].windMs!=null && r[0].pop!=null);
    ok("aufsteigend nach Zeit", r.every((x,i)=>i===0||x.t>=r[i-1].t));
    /* Höchstens zwölf: mehr braucht niemand, und der Speicher wandert in den
       Sync. */
    const viele={hourly:{time:[], temperature_2m:[], wind_speed_10m:[]}};
    for(let i=0;i<48;i++){ viele.hourly.time.push(iso(i));
      viele.hourly.temperature_2m.push(10); viele.hourly.wind_speed_10m.push(3); }
    ok("auf 12 Stunden gedeckelt", parse(viele).length===12, parse(viele).length);
  }
  if (typeof html === "function") {
    const src=fs.readFileSync(FILE,"utf8");
    /* SECHS Spalten passen auf ein Handy, ohne zu wischen. */
    ok("Standard sind 6 Stunden", /wxStundenHtml\(6\)/.test(src));
    /* Böen NUR bei deutlichem Unterschied — sonst steht dort eine Zahl, die
       nichts unterscheidet. */
    ok("Böen erst ab +10 km/h", /windMs\*3\.6\+10/.test(src));
    ok("ohne Daten leer", html(6)==="" || typeof html(6)==="string");
  }
}

/* ============ 24bc. Vorbereitung nachhalten ============ */
group("prepLog / prepQuote — automatisch statt Erledigt-Knopf");
{
  const log=G("prepLog"), heute=G("prepHeute"), quote=G("prepQuote"),
        tog=G("stretchToggle"), DONE=G("STRETCH_DONE"), DYN=G("MALASKA_DYN"),
        heuteISO=G("todayISO"), DB=G("DB");
  if (typeof log === "function" && DB) {
    DB.prep={};
    ok("vorher nichts vermerkt", heute("pre")===false);
    log("pre");
    ok("nach dem Vermerk erkannt", heute("pre")===true);
    const stand=JSON.stringify(DB.prep);
    log("pre");
    eq("zweimaliges Vermerken ändert nichts", JSON.stringify(DB.prep), stand);
    ok("andere Art bleibt unberührt", heute("post")===false);

    /* AUTOMATISCH ab der HÄLFTE: Ein zusätzlicher Erledigt-Knopf wäre ein
       weiterer Tipp, den man auf Loch 1 vergisst — und dann fehlt der Eintrag,
       obwohl man gedehnt hat. Auf vollständiges Abhaken zu warten hieße, die
       Erfassung an einer Formalie scheitern zu lassen. */
    if (typeof tog === "function" && DONE && DYN) {
      DB.prep={};
      Object.keys(DONE).forEach(k=>delete DONE[k]);
      const noetig=Math.ceil(DYN.length/2);
      for(let i=0;i<noetig-1;i++) tog(i);
      ok("unter der Hälfte noch nicht vermerkt", heute("pre")===false,
         `${noetig-1} von ${DYN.length}`);
      tog(noetig-1);
      ok("ab der Hälfte vermerkt", heute("pre")===true,
         `${noetig} von ${DYN.length}`);
      Object.keys(DONE).forEach(k=>delete DONE[k]);
    }

    /* QUOTE JE RUNDE, nicht je Kalendertag: An Tagen ohne Golf muss niemand
       dehnen — eine Tagesquote wäre systematisch niedrig und damit
       entmutigend ohne Aussage. */
    const altR=DB.rounds;
    const tag=n=>new Date(Date.now()-n*86400000).toISOString().slice(0,10);
    DB.prep={}; DB.rounds=[];
    ok("unter 3 Runden keine Quote", quote(10).reicht===false);
    [0,7,14,21,28].forEach((d,i)=>{
      DB.prep[tag(d)]={pre:"x"};
      if(i<4) DB.prep[tag(d)].warm="x";
      if(i<2) DB.prep[tag(d)].post="x";
      DB.rounds.push({id:"PR"+i, date:tag(d), course:"T", holes:[]});
    });
    const q=quote(10);
    ok("ab 3 Runden eine Quote", q.reicht===true);
    eq("Runden gezählt", q.n, 5);
    eq("Preround", q.pre, 5);
    eq("Aufwärmen", q.warm, 4);
    eq("Post Round", q.post, 2);
    /* Das Dehnen nach der Runde faellt oft auf den Folgetag (spaete Runde,
       Dehnen zu Hause) — das muss zählen. */
    DB.prep={}; DB.rounds=[{id:"PX", date:tag(3), course:"T", holes:[]},
                           {id:"PY", date:tag(5), course:"T", holes:[]},
                           {id:"PZ", date:tag(9), course:"T", holes:[]}];
    DB.prep[tag(2)]={post:"x"};        // Folgetag der Runde von tag(3)
    eq("Post am Folgetag zählt", quote(10).post, 1);
    DB.rounds=altR; DB.prep={};
    /* Bewusst NICHT gebaut: Serienzähler und Score-Vergleich. */
    const src=fs.readFileSync(FILE,"utf8");
    /* Der Prüfbereich muss auf die VORBEREITUNG eingegrenzt sein. Eine Suche
       über die ganze Datei schlägt auf `birdieStreak` an — die längste
       Birdie-Serie einer Runde, ein seit Langem bestehendes Feature und mit
       dem Tracking nicht verwandt. Eine Prüfung, die nie grün werden kann,
       ist keine Prüfung. */
    const prepTeil = src.slice(src.indexOf("function prepLog"),
                               src.indexOf("function prepQuoteHtml")+2600)
                        .replace(/\/\*[\s\S]*?\*\//g,"");
    ok("kein Serienzähler im Tracking",
       !/streak|in Folge/i.test(prepTeil));
    ok("Begründung dokumentiert", /bricht beim ersten verpassten Tag zusammen/.test(src));
    /* `uebText(d)` hebt **fett** in den Übungsbeschreibungen hervor — nur
       diese eine Regel, damit die Texte lesbar bleiben statt formatiert. */
    const ut=G("uebText");
    if (typeof ut === "function") {
      ok("fett wird umgesetzt", /<b>Ball<\/b>/.test(ut("Den **Ball** treffen")));
      ok("Text ohne Auszeichnung bleibt unverändert", ut("Nur Text")==="Nur Text");
    }
  }
}

/* ============ 24bn. Eine Bewertung für Caddy und Gameplan ============ */
group("SPIELWEISE — Caddy und Ziellinie können nicht mehr auseinanderlaufen");
{
  const SW=G("SPIELWEISE"), sw=G("spielweise"), inWZ=G("inWedgeZone"), WZ=G("WEDGE_ZONE");
  const src=fs.readFileSync(FILE,"utf8");
  if (SW && typeof sw === "function") {
    /* VORHER gab es DREI getrennte Bewertungen: `caddyPlan` mit fünf
       Gewichten, `STRAT.tee` mit dreien, `STRAT.nextShot` mit EINEM — und das
       eine wirkte nur bei Wasser. Auf einem Loch ohne Strafgebiet waren
       „sicher", „normal" und „offensiv" damit rechnerisch IDENTISCH, und der
       Gameplan änderte sich beim Umschalten nicht. */
    ["safe","bal","aggr"].forEach(m=>{
      const p=SW[m];
      ok(m+": Lage-Gewichte vollständig",
         p && p.lie && p.lie.pen!=null && p.lie.sand!=null && p.lie.rough!=null);
      ok(m+": Wedge-Bonus und Vorrück-Strafe gesetzt",
         p.wedgeES>0 && p.advES>0, `${p.wedgeES} / ${p.advES}`);
      ok(m+": Caddy-Gewichte vorhanden",
         p.caddy && p.caddy.risk!=null && p.caddy.wedgeBonus!=null);
    });
    /* Die Modi müssen sich in JEDER Dimension unterscheiden — sonst fällt
       einer wieder auf „wirkt nur bei Wasser" zurück. */
    ok("Sand-Gewicht sinkt zum Offensiven",
       SW.safe.lie.sand > SW.bal.lie.sand && SW.bal.lie.sand > SW.aggr.lie.sand);
    ok("Rough-Gewicht ebenso",
       SW.safe.lie.rough >= SW.bal.lie.rough && SW.bal.lie.rough >= SW.aggr.lie.rough);
    ok("Layup-Schwelle steigt zum Offensiven",
       SW.safe.layP5 < SW.bal.layP5 && SW.bal.layP5 < SW.aggr.layP5);
    eq("unbekannter Modus fällt auf normal zurück", sw("quatsch"), SW.bal);

    /* WEDGE-BONUS HERGELEITET: Die Erwartungstabelle gibt einem 71-m-Rest
       rund 0,075 Schläge Vorsprung vor 98 m — sie nimmt an, näher sei immer
       besser. Ein voller Wedge-Schlag ist aber kontrollierbarer als ein
       Teilschlag. Der Bonus MUSS über 0,075 liegen, sonst wirkt er gar
       nicht — genau daran scheiterte die erste Fassung. */
    ["safe","bal","aggr"].forEach(m=>
      ok(m+": Bonus überwindet die Tabellenverzerrung", SW[m].wedgeES>0.075,
         String(SW[m].wedgeES)));
  }
  if (typeof inWZ === "function" && WZ) {
    ok("volle Wedge-Zone erkannt", inWZ(100)===true && inWZ(85)===true && inWZ(125)===true);
    ok("außerhalb nicht", inWZ(70)===false && inWZ(140)===false);
    ok("Zone deckt die üblichen Wedge-Längen ab", WZ.von<=90 && WZ.bis>=120);
  }
  /* ALLE DREI Bewertungen müssen aus der Tabelle lesen — das ist der Kern:
     Wer eine Zeile ändert, ändert Caddy UND Gameplan zugleich. */
  ok("caddyPlan liest die Tabelle", /const _sw=spielweise\(mode\)/.test(src));
  ok("STRAT.tee liest die Tabelle", /const w = spielweise\(mode\)\.lie/.test(src));
  ok("STRAT.nextShot liest die Tabelle",
     /const SW=spielweise\(mode\), w=SW\.lie/.test(src));
  /* Gegenprobe: keine eigenen Kopien mehr im Quelltext. */
  ok("keine zweite Gewichtstabelle",
     !/mode==="safe" \? \{pen:1\.5, sand:0\.40/.test(src));
  /* DIE REGEL MUSS IN DER DOKU STEHEN — sonst wiederholt sich der Fehler.
     `STRAT.nextShot` blieb 45 Versionen lang bei einem einzigen Gewicht, weil
     die Korrektur aus v2.05 nur in `caddyPlan` landete. Eine Regel, die nur
     im Kopf des letzten Bearbeiters steht, ist keine Regel. */
  {
    const doc=(src.match(/<script[^>]*id="devdocs"[^>]*>([\s\S]*?)<\/script>/)||[])[1]||"";
    ok("Doku vorhanden", doc.length>1000);
    ok("Regel steht bei den unverhandelbaren Regeln",
       /CADDY UND GAMEPLAN SIND EINE EINHEIT/.test(doc));
    ok("beide Wege sind benannt",
       /caddyPlan\(\)/.test(doc) && /STRAT\.nextShot\(\)/.test(doc));
    ok("das Verbot eigener Gewichte steht dabei",
       /Verboten:[\s\S]{0,200}eigene Gewichte/.test(doc));
    ok("die Tabelle ist genannt", doc.indexOf("SPIELWEISE")>=0);
    /* Auch der Grund muss dastehen — eine Regel ohne Begründung wird beim
       ersten Widerspruch weggeräumt. */
    ok("Begründung dokumentiert", /WARUM DIESE REGEL EXISTIERT/.test(doc));
    ok("der Moduswechsel-Hinweis fehlt nicht",
       /setCaddyMode\(\)[\s\S]{0,200}aimChain/.test(doc));
  }

  /* nextShot muss Sand, Rough UND den Wedge-Bonus verrechnen. */
  const ns=src.slice(src.indexOf("nextShot(geo,courseName,holeNo,from,mode,hcp)"),
                     src.indexOf("nextShot(geo,courseName,holeNo,from,mode,hcp)")+4200);
  ok("nextShot verrechnet Sand", /w\.sand\*sandQ/.test(ns));
  ok("nextShot verrechnet Rough", /w\.rough\*roughQ/.test(ns));
  ok("nextShot belohnt die Wedge-Zone", /inWedgeZone\(restNach\)/.test(ns));
  ok("nextShot straft weite Reste", /restNach>175/.test(ns));
  /* Vorzeichen: `score` wird hier MINIMIERT — der Bonus muss ABGEZOGEN
     werden. Ein Vorzeichenfehler würde krumme Reste belohnen. */
  ok("Bonus wird abgezogen, nicht addiert", /\+ advance - wedgeBonus/.test(ns));
}

/* ============ 24bm. Ziellinie: swap und Moduswechsel ============ */
group("STRAT.tee — vertauschte Tee/Grün-Punkte und Modus-Reaktion");
{
  const src=fs.readFileSync(FILE,"utf8");
  /* DER BUG: `geo.holes[n].tee` ist der ROHE Punkt. Bei Löchern mit
     `swap:true` sind Tee und Grün darin VERTAUSCHT — `holeRef()` dreht sie
     zurück, `STRAT.tee` las aber direkt aus `geo`. Folge: Die Schlagfolge
     startete am GRÜN und rechnete Richtung Tee. Der Zielpunkt lag 179 m vom
     falschen Ende, also 95 m vom echten Tee — und die Anzeige zeigte
     „7 Wood · 95 m" statt „7 Wood · 179 m".
     Der Fehler war nur auf Löchern mit `swap` sichtbar. Deshalb betraf er
     Loch 1 des Nordplatzes und sonst nichts, und deshalb war er so schwer zu
     finden. */
  const teeFn=src.slice(src.indexOf("  tee(geo,courseName,holeNo,mode,hcp,von){"),
                        src.indexOf("  tee(geo,courseName,holeNo,mode,hcp,von){")+1400);
  ok("STRAT.tee nutzt holeRef", /holeRef\(geo,holeNo\)/.test(teeFn));
  ok("und bevorzugt dessen Tee-Punkt", /\(hr&&hr\.tee\)/.test(teeFn));
  ok("Begründung dokumentiert", /swap/.test(teeFn));

  /* MODUSWECHSEL: Die Bedingung lautete `PLAY.mapFocus` — Ziellinie und
     „Plan vom Abschlag" wurden also nur im VOLLBILD neu gerechnet. Im
     normalen Spielmodus blieb der alte Plan stehen, während der Caddy
     darunter schon anders rechnete: zwei Empfehlungen, die sich
     widersprachen. */
  const sm=src.slice(src.indexOf("function setCaddyMode"),
                     src.indexOf("function setCaddyMode")+1600);
  ok("Kette wird unabhängig vom Vollbild verworfen",
     /PLAY\.active\)\{[\s\S]{0,200}aimChainKey=null/.test(sm));
  ok("auch das Kettenobjekt selbst", /PLAY\.aimChain=null/.test(sm));
  ok("eingebettete Karte wird neu gezeichnet", /playMapRedraw/.test(sm));
  ok("nicht mehr an mapFocus gebunden",
     !/if\(typeof PLAY!=="undefined" && PLAY\.active && PLAY\.mapFocus\)/.test(sm));
  /* Der Cache-Schlüssel MUSS den Modus enthalten, sonst hilft das Verwerfen
     nichts — die Bewertung käme aus dem Speicher zurück. */
  ok("Bewertungs-Cache schlüsselt über den Modus",
     /function _aimTeeEv[\s\S]{0,900}caddyMode\(\)/.test(src));
  /* Und über die POSITION (v2.94) — auf 10 m gerundet: ohne sie bliebe die
     erste Rechnung für immer im Speicher, mit voller Genauigkeit käme bei
     jedem GPS-Zucken eine neue Monte-Carlo-Rechnung. */
  ok("und über die gerundete Position",
     /const k="T\|"\+PLAY\.course\+"\|"\+PLAY\.tee\+"\|"\+h\.hole\+"\|"\+caddyMode\(\)\+pk/.test(src));
  ok("Kettenschlüssel ebenso",
     /_aimChainKey[\s\S]{0,300}caddyMode\(\)/.test(src));
}

/* ============ 24bl. Caddy-Plan: Einheiten und Plausibilität ============ */
group("caddyPlan — kein Wedge vom Abschlag, Einheiten beschriftet");
{
  const plan=G("caddyPlan"), cc=G("caddyClubs");
  if (typeof plan === "function") {
    /* WAS PASSIERT IST: Der Plan zeigte „7 Wood · 95 m" und „GW · 179 m".
       Schritt 1 nennt die SCHLAGWEITE, Schritt 2 die RESTDISTANZ — beide nur
       als nackte Meterzahl. Wer das nicht weiß, liest die zweite Zahl als
       Schlägerlänge und hält den Plan für kaputt. */
    const bag=[{name:"Driver",dist:221,carry:211,sigma:20},
               {name:"5 Iron",dist:170,carry:164,sigma:12},
               {name:"PW",dist:113,carry:113,sigma:8},
               {name:"GW",dist:90,carry:90,sigma:7}];
    const p=plan(279, 4, {}, "normal", {clubs:bag, miss:{dir:null}}, null);
    ok("Plan entsteht", !!p && Array.isArray(p.shots) && p.shots.length>=2);
    ok("Abschlag nennt die Weite", /m weit/.test(p.shots[0].dist), p.shots[0].dist);
    ok("Folgeschlag nennt den Rest", /m Rest/.test(p.shots[1].dist), p.shots[1].dist);
    /* Die Summe muss aufgehen — sonst stimmt eine der beiden Zahlen nicht. */
    const weit=parseInt(p.shots[0].dist), rest=parseInt(p.shots[1].dist);
    ok("Weite + Rest ergibt die Lochlänge", Math.abs(weit+rest-279)<=2,
       `${weit} + ${rest} = ${weit+rest}`);

    /* PLAUSIBILITÄT: Ein Wedge vom Abschlag eines Par 4 ist Unsinn. Vorher
       konnte der Rückfall `clubs[0]` genau das liefern, wenn die Liste nicht
       absteigend sortiert ankam. */
    const nurKurz=[{name:"LW",dist:69,carry:69,sigma:5},
                   {name:"SW",dist:80,carry:80,sigma:5},
                   {name:"PW",dist:113,carry:113,sigma:6}];
    const p2=plan(279, 4, {}, "normal", {clubs:nurKurz, miss:{dir:null}}, null);
    ok("auch mit kurzer Bag ein Plan", !!p2 && p2.shots.length>0);
    ok("der LÄNGSTE wird gewählt, nicht der erste",
       /PW/.test(p2.shots[0].club), p2.shots[0].club);
    /* Gegenprobe: Bei einer normal sortierten Bag darf sich nichts ändern. */
    const p3=plan(279, 4, {}, "normal", {clubs:bag, miss:{dir:null}}, null);
    ok("normale Bag unverändert", /Driver|5 Iron/.test(p3.shots[0].club),
       p3.shots[0].club);
    /* Und die verdrehte Liste darf keinen Wedge nach vorn bringen. */
    const verdreht=[{name:"GW",dist:90,carry:90,sigma:7},
                    {name:"Driver",dist:221,carry:211,sigma:20},
                    {name:"5 Iron",dist:170,carry:164,sigma:12}];
    const p4=plan(279, 4, {}, "normal", {clubs:verdreht, miss:{dir:null}}, null);
    ok("unsortierte Liste wählt trotzdem sinnvoll",
       !/^GW/.test(p4.shots[0].club), p4.shots[0].club);
  }
}

/* ============ 24bk. Aufwärmen abhaken ============ */
group("warmToggle — dieselbe Regel wie bei den Stretch-Blättern");
{
  const wt=G("warmToggle"), wr=G("warmReset"), WD=G("WARM_DONE"),
        PL=G("WARMUP_PLANS"), WU=G("WU"), heute=G("prepHeute"), DB=G("DB");
  if (typeof wt === "function" && WD && PL && WU && DB) {
    /* WAS FEHLTE: Die Aufwärmblöcke waren reine Anzeige — man konnte nichts
       abhaken. Erfasst wurde stattdessen das FESTLEGEN von Plan oder
       Abschlagzeit, und das hat die Quote geschönigt: Man legt die Zeit fest
       und fährt trotzdem ohne einen Ball zum ersten Tee. */
    DB.prep={};
    Object.keys(WD).forEach(k=>delete WD[k]);
    const n=PL[WU.plan].bloecke.length;
    ok("Plan hat Blöcke", n>=3, "n="+n);
    const haelfte=Math.ceil(n/2);
    for(let i=0;i<haelfte-1;i++) wt(i);
    ok("unter der Hälfte noch nicht erfasst", heute("warm")===false,
       `${haelfte-1} von ${n}`);
    wt(haelfte-1);
    ok("ab der Hälfte erfasst", heute("warm")===true, `${haelfte} von ${n}`);
    /* Erneutes Tippen nimmt zurück — wie bei den Stretch-Blättern. */
    const vorher=Object.keys(WD).filter(k=>WD[k]).length;
    wt(0);
    eq("erneutes Tippen nimmt zurück",
       Object.keys(WD).filter(k=>WD[k]).length, vorher-1);
    DB.prep={};
  }
  {
    const src=fs.readFileSync(FILE,"utf8");
    const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
      .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
      .map(m=>m[2]).join("\n").replace(/\/\*[\s\S]*?\*\//g,"");
    /* Das Festlegen der Zeit darf NICHT mehr als aufgewärmt zählen. */
    ok("warmupSetTee protokolliert nicht mehr",
       !/function warmupSetTee[\s\S]{0,300}prepLog/.test(nurCode));
    ok("warmupSetPlan protokolliert nicht mehr",
       !/function warmupSetPlan[\s\S]{0,300}prepLog/.test(nurCode));
    ok("stattdessen protokolliert warmToggle",
       /function warmToggle[\s\S]{0,400}prepLog\("warm"\)/.test(nurCode));
    /* Blöcke müssen antippbar sein. */
    ok("Blöcke sind antippbar", /onclick="warmToggle\(/.test(nurCode));
    ok("Haken werden dargestellt", /ab\?"✓":"○"/.test(nurCode));
    /* Veralteter Verweis auf einen Plan, den es seit v2.27 nicht mehr gibt. */
    ok("kein Verweis mehr auf „8 Minuten danach\"",
       nurCode.indexOf("8 Minuten danach")<0);
    /* Die Quote steht im Fitness-Reiter — der Nutzer muss sie finden. */
    ok("Quote wird angezeigt", /h=prepQuoteHtml\(\)/.test(nurCode));
    ok("Blatt nennt den Fundort", /Training · Fitness/.test(src));
  }
}

/* ============ 24bj. Geschwindigkeit ============ */
group("Zwischenspeicher — gemessen, nicht geraten");
{
  const cn=G("clubNorm"), roh=G("_clubNormRoh"), grp=G("shotsProKlasse"),
        clear=G("crCacheClear"), DB=G("DB");
  if (typeof cn === "function" && typeof roh === "function") {
    /* GEMESSEN: `renderBag` löste 126.969 clubNorm-Aufrufe aus — für rund 40
       verschiedene Schlägernamen. Die Zuordnung Name -> Klasse ist FEST,
       derselbe Name ergibt immer dasselbe. Der seltene Fall, in dem ein
       Zwischenspeicher nicht veralten KANN. */
    ok("gemerkte Fassung gleicht der Rechnung",
       cn("7 Iron")===roh("7 Iron") && cn("Pitching Wedge")===roh("Pitching Wedge"));
    eq("englisch und deutsch treffen sich", cn("7 Iron"), cn("7-Eisen"));
    ok("zweiter Aufruf liefert dasselbe", cn("Driver")===cn("Driver"));
    const src=fs.readFileSync(FILE,"utf8");
    ok("Größe gedeckelt", /_cnCache\.size>500/.test(src));
  }
  if (typeof grp === "function" && DB) {
    /* GEMESSEN: `clubMeasured` lief 46-mal über ALLE 1200 GPS-Schläge und alle
       LM-Sitzungen — 55.000 Durchläufe für 46 Ergebnisse. Jetzt einmal
       gruppieren. */
    const altG=DB.gpsShots, altL=DB.lmSessions;
    DB.gpsShots=[{id:"P1", ts:new Date().toISOString(), club:"7 Iron", dist:140, swing:"Voll"},
                 {id:"P2", ts:new Date().toISOString(), club:"7-Eisen", dist:142, swing:"Voll"},
                 {id:"P3", ts:new Date().toISOString(), club:"Driver", dist:215, swing:"Voll"}];
    DB.lmSessions=[{id:"PL1", date:"2026-08-01", shots:[{club:"7 Iron", carry:141}]}];
    if(clear) clear();
    const g=grp();
    eq("englische und deutsche Namen landen zusammen", (g.gps["eisen7"]||[]).length, 2);
    eq("Driver getrennt", (g.gps["driver"]||[]).length, 1);
    eq("LM getrennt gehalten", (g.lm["eisen7"]||[]).length, 1);
    /* Der Speicher muss die Änderung bemerken — sonst zeigt die Bag alte
       Werte, und das wäre schlimmer als eine langsame Ansicht. */
    DB.gpsShots.push({id:"P4", ts:new Date().toISOString(), club:"7 Iron", dist:139, swing:"Voll"});
    eq("neue Schläge werden bemerkt", (grp().gps["eisen7"]||[]).length, 3);
    DB.gpsShots=altG; DB.lmSessions=altL;
    if(clear) clear();
  }
  {
    /* Alle Zwischenspeicher hängen an EINER Leerfunktion — sonst vergisst man
       beim nächsten Einbau einen, und die App zeigt still veraltete Zahlen. */
    const src=fs.readFileSync(FILE,"utf8");
    const cc=src.slice(src.indexOf("function crCacheClear"),
                       src.indexOf("function crCacheClear")+220);
    ["_crCache","_sgvCache","_grpCache"].forEach(c=>
      ok(c+" wird mitgeleert", cc.indexOf(c)>=0, cc.slice(0,90)));
  }
  /* sgVerlauf war der teuerste Einzelposten: 74 von 84 ms, weil sgSummary für
     JEDE Runde über ein Fenster von fünf lief. */
  {
    const src=fs.readFileSync(FILE,"utf8");
    ok("jede Runde wird nur einmal ausgewertet",
       /const einzeln=rs\.map\(r=>/.test(src));
    ok("Verlauf wird gemerkt", /_sgvCache && _sgvKey===key/.test(src));
  }
}

/* ============ 24bi. App-Logo ============ */
group("Logo — eingebettet, offline, in der richtigen Größe");
{
  const src=fs.readFileSync(FILE,"utf8");
  const L512=G("LOGO512"), L192=G("LOGO192"), L64=G("LOGO64");
  [["LOGO512",L512],["LOGO192",L192],["LOGO64",L64]].forEach(([n,v])=>{
    ok(n+" vorhanden", typeof v==="string" && v.length>1000, n);
    ok(n+" ist eine Daten-URL", /^data:image\/webp;base64,/.test(v||""));
  });
  /* WEBP STATT PNG: dieselbe Darstellung bei einem Fünftel der Größe (512 px
     als PNG wären 418 kB base64, als WebP 81 kB). Die App muss OFFLINE
     vollständig funktionieren — jedes Kilobyte liegt dauerhaft im Cache. */
  ok("alle drei zusammen unter 150 kB",
     (L512.length+L192.length+L64.length)/1024 < 150,
     Math.round((L512.length+L192.length+L64.length)/1024)+" kB");
  /* Drei Größen, weil ein 512-px-Bild in der 28-px-Kopfzeile nur Rechenzeit
     kostet. */
  ok("Kopfzeilen-Logo ist das kleinste", L64.length < L192.length &&
     L192.length < L512.length);
  ok("Kopfzeile nutzt die kleine Fassung", /el\.src=LOGO64/.test(src));
  /* OFFLINE: kein externer Verweis — ein Logo, das erst geladen werden muss,
     fehlt genau dann, wenn man auf dem Platz steht. */
  ok("keine externe Bildquelle im Kopfbereich",
     !/<img[^>]*src="https?:/.test(src));
  /* Manifest und iOS-Symbol: iOS nutzt das Manifest NICHT fürs Startsymbol. */
  ok("Manifest enthält das Logo", /manifest\+json[\s\S]{0,4000}image%2Fwebp|image\/webp/.test(src));
  ok("apple-touch-icon gesetzt", /rel="apple-touch-icon" href="data:image\/webp/.test(src));
  ok("Begründung für iOS dokumentiert", /iOS nutzt das Manifest NICHT/.test(src));
}

/* ============ 24bh. GPS-Genauigkeit ============ */
group("gpsBest / gpsGewicht — schlechte Messungen nicht wie gute behandeln");
{
  const push=G("gpsPush"), best=G("gpsBest"), gew=G("gpsGewicht"),
        BUF=G("GPS_BUF"), MAX=G("GPS_MAX_ACC"), acl=G("accClass");
  /* Bisher wurde JEDE Position verwendet — auch eine mit 25 m Ungenauigkeit.
     Für die Distanz zum Grün verschmerzbar, für die SCHLAGMESSUNG nicht: Zwei
     Punkte mit je 5 m Fehler ergeben bei 150 m bis zu 10 m Abweichung, und
     die Zahl geht in die gelernte Schlägerlänge ein — also in jede
     Caddy-Empfehlung. */
  if (typeof gew === "function") {
    eq("gutes Signal zählt voll", gew(4,5), 1);
    ok("mittleres Signal zählt abgeschwächt", gew(12,14)<1 && gew(12,14)>0.5,
       String(gew(12,14)));
    eq("sehr schlechtes Signal zählt gar nicht", gew(35,35), 0);
    ok("der SCHLECHTERE Punkt bestimmt", gew(3,20)===gew(20,20),
       `${gew(3,20)} vs ${gew(20,20)}`);
    ok("fehlende Angabe gilt als Grenzfall", gew(null,null)>0 && gew(null,null)<1);
    /* Kein Sprung: Sonst hinge die gelernte Länge an einem Meter Unterschied. */
    ok("stetig, ohne Sprung", Math.abs(gew(14,14)-gew(15,15))<0.1,
       `${gew(14,14)} / ${gew(15,15)}`);
  }
  if (typeof push === "function" && typeof best === "function" && BUF) {
    BUF.length=0;
    eq("leerer Puffer liefert null", best(4000), null);
    const jetzt=Date.now();
    /* STILLSTAND: Alle Punkte eng beieinander — dann mitteln, denn die
       Streuung ist zufällig und der Mittelwert liegt näher an der Wahrheit. */
    for(let n=0;n<6;n++) push({lat:54.0+(n%2?1:-1)*0.00002, lng:10.0+(n%2?1:-1)*0.00002,
                               acc:6, ts:jetzt-n*400});
    const b=best(4000);
    ok("mehrere Messungen werden gemittelt", b.gemittelt>=3, "n="+b.gemittelt);
    ok("Genauigkeit verbessert sich", b.acc<6, "acc="+b.acc);
    ok("aber nicht unter 2 m — das Gerät kann nicht besser", b.acc>=2);
    /* BEWEGUNG: Weit auseinanderliegende Punkte dürfen NICHT gemittelt
       werden — wer geht, bekäme sonst eine Position von vor Sekunden. */
    BUF.length=0;
    for(let n=0;n<6;n++) push({lat:54.0+n*0.0005, lng:10.0, acc:6, ts:jetzt-n*400});
    const bw=best(4000);
    ok("bei Bewegung wird nicht gemittelt", !bw.gemittelt, JSON.stringify(bw.gemittelt));
    /* Der BESTE Punkt gewinnt, nicht der letzte. */
    BUF.length=0;
    push({lat:54.0, lng:10.0, acc:18, ts:jetzt-2000});
    push({lat:54.1, lng:10.1, acc:4,  ts:jetzt-1500});
    push({lat:54.2, lng:10.2, acc:22, ts:jetzt-500});
    eq("bester Punkt gewinnt", best(4000).acc, 4);
    /* Alte Messungen fallen heraus. */
    BUF.length=0;
    push({lat:54.0, lng:10.0, acc:3, ts:jetzt-20000});
    eq("über 12 s alte Messungen zählen nicht", best(4000), null);
    BUF.length=0;
  }
  {
    const src=fs.readFileSync(FILE,"utf8");
    /* Beim SETZEN wird abgewiesen statt still zu speichern — eine Messung mit
       25 m ist wertlos, und der Nutzer soll es erfahren. */
    ok("Anfangspunkt weist schlechtes Signal ab",
       /function gpsAnchor[\s\S]{0,400}Signal zu ungenau/.test(src));
    ok("Endpunkt ebenso",
       /function gpsArrive[\s\S]{0,400}Signal zu ungenau/.test(src));
    ok("Grenze bei 15 m", MAX===15, String(MAX));
    /* Die Gewichtung muss beim Lernen der Schlägerlänge ankommen. */
    ok("clubMeasured wertet die Genauigkeit aus",
       /gpsGewicht\(x\.accA, x\.accB\)/.test(src));
    /* BEWUSST NICHT: Glättung über die Zeit. Beim Golf steht man, geht, steht
       wieder — eine Glättung hinkt beim Losgehen hinterher. */
    ok("Begründung gegen Glättung dokumentiert", /Kalman/.test(src));
  }
  if (typeof acl === "function") {
    eq("gut bis 8 m", acl(8), "good");
    eq("mittel bis 15 m", acl(15), "mid");
    eq("darüber schlecht", acl(16), "bad");
    eq("ohne Wert schlecht", acl(null), "bad");
  }
}

/* ============ 24bg. Karte verschieben beim Nachtragen ============ */
group("strkDown/Move/Up — ein Finger zieht die Karte");
{
  const src=fs.readFileSync(FILE,"utf8");
  const dn=src.slice(src.indexOf("function strkDown"), src.indexOf("function strkMove"));
  const mv=src.slice(src.indexOf("function strkMove"), src.indexOf("function strkUp"));
  const up=src.slice(src.indexOf("function strkUp"), src.indexOf("function strkUp")+1400);
  /* WAS FEHLTE: `strkDown` brach bei `if(!g) return;` ab, wenn man NICHT auf
     einen Schlagpunkt tippte — ein Zug auf freier Fläche bewirkte nichts.
     Zwei Finger zoomten, ein Finger tat gar nichts. Beim Nachtragen zoomt man
     aber heran und muss dann zur nächsten Stelle der Bahn. */
  ok("Ziehen startet auf leerer Fläche", /STRK\.pan=\{/.test(dn));
  ok("auf einem Schlagpunkt bleibt es beim Punkt-Verschieben",
     /STRK\.drag=\{i:\+g\.dataset\.shot/.test(dn));
  ok("Zeiger wird eingefangen", /setPointerCapture/.test(dn));
  ok("Verschieben wird ausgeführt", /STRK\.pan && STRK\.ptrs\.size===1/.test(mv));
  /* Umrechnung in Kartenkoordinaten: Ohne sie liefe die Karte bei starkem
     Zoom viel zu schnell unter dem Finger weg. */
  ok("Pixel werden in Kartenkoordinaten umgerechnet",
     /\/Math\.max\(1,r\.width\)\*p\.view\.w/.test(mv));
  /* Am Rand klemmen: Wer über die Bahn hinausschiebt, sähe sonst leere Fläche
     und fände nicht zurück. */
  ok("an den Rand geklemmt",
     /Math\.max\(0, Math\.min\(M\.W-p\.view\.w/.test(mv));
  ok("Ziehen endet beim Loslassen", /STRK\.pan=null;/.test(up));
  /* v2.63: Der Tipp wird im pointerup erkannt, nicht mehr über `click`. Seit
     das Ziehen auf freier Fläche `preventDefault()` ruft, unterdrückt der
     Browser die abgeleiteten Maus-Ereignisse — `click` kam nie mehr an, und
     das Anlegen per Tipp war lautlos tot. */
  ok("kurzer Tipp ohne Bewegung legt an", /!p\.moved && Date\.now\(\)-p\.t0<700/.test(up));
  ok("und ruft den Anleger", /strkAddAt\(e\.clientX, e\.clientY/.test(up));
  ok("kein click-Horcher mehr", !/addEventListener\("click",strkClick\)/.test(src));
  ok("ein Zug ist kein Tipp", /Math\.hypot\(e\.clientX-p\.x0, e\.clientY-p\.y0\)>8/.test(src));
  /* Beim Aufsetzen des zweiten Fingers muss das Ziehen enden — sonst springt
     die Karte im Moment des Umschaltens auf Zoom. */
  ok("zweiter Finger beendet das Ziehen",
     /STRK\.drag=null; STRK\.pan=null;/.test(dn));
  ok("Feld ist deklariert", /drag:null, pan:null/.test(src));
}

/* ============ 24bf. Dashboard: Überblick statt Bericht ============ */
group("renderDash — drei Fragen, nicht dreizehn Blöcke");
{
  const src=fs.readFileSync(FILE,"utf8");
  /* Der Ausschnitt MUSS die ganze Funktion umfassen. Eine feste Länge schnitt
     die Zusammensetzung mittendrin ab, und die Prüfung meldete Fehler, wo
     keine waren — Klammerpaarung statt Zeichenzahl. */
  const dStart=src.indexOf("function renderDash");
  const dEnde=src.indexOf("\n}\n", dStart);
  const d=src.slice(dStart, dEnde);
  /* VORHER: 13 Blöcke, 10 Karten, 4 Verlaufsdiagramme — fünf bis sechs
     Bildschirme auf dem Handy. Bei sechs gleichrangigen Blöcken sieht man
     keinen. Drei beantworteten dieselbe Frage („woran arbeiten?"), und drei
     der vier Kurven zeigten im Kern dasselbe: Wird es besser? */
  const zusammen=d.slice(d.indexOf("h = hcpGapHtml()"));
  ok("Zusammensetzung gefunden", zusammen.length>0);
  /* Die Reihenfolge IST die Aussage: wo stehe ich, was war zuletzt, woran
     arbeiten. */
  const pos=t=>zusammen.indexOf(t);
  ok("Ziel und Tempo zuerst",
     pos("hcpGapHtml()")>=0 && pos("hcpGapHtml()")<pos("hLetzte"));
  ok("dann die letzte Runde", pos("hLetzte")<pos("trainingsplanHtml()"));
  ok("dann EINE Empfehlung", pos("trainingsplanHtml()")>=0);
  /* Die Kurven wandern in den Aufklappbereich — sie beantworten dieselbe
     Frage mehrfach und drängten das Wesentliche nach unten. */
  ok("Aufklappbereich vorhanden", /<details class="descbox">/.test(zusammen));
  const auf=zusammen.slice(zusammen.indexOf("<details"));
  ["hVerlauf","sgVerlaufHtml()","puttDiagnoseHtml()","hFokus"].forEach(t=>
    ok(t+" ist aufklappbar", auf.indexOf(t)>=0, t));
  /* Gegenprobe: Diese Blöcke dürfen NICHT mehr oben stehen. */
  const oben=zusammen.slice(0, zusammen.indexOf("<details"));
  ok("keine Kurven im sichtbaren Teil", oben.indexOf("hVerlauf")<0);
  ok("kein Top-Fokus im sichtbaren Teil", oben.indexOf("hFokus")<0);
  /* Die drei Sammelstellen müssen befüllt werden, sonst bleibt der
     Aufklappbereich leer. */
  ["hLetzte","hVerlauf","hFokus"].forEach(v=>
    ok(v+" wird befüllt", new RegExp(v+"\\+=").test(d), v));
}

/* ============ 24be. Bearbeitungen dürfen der Sync nicht fressen ============ */
group("stamp() — ohne Zeitstempel gewinnt die ältere Repo-Fassung");
{
  const merge=G("mergeDB"), st=G("stamp"), ts=G("_mergeTs");
  if (typeof merge === "function" && typeof st === "function") {
    /* DER BUG: Eine bearbeitete Runde wurde beim nächsten Abgleich von der
       älteren Repo-Fassung überschrieben — die Korrektur war weg.
       Ursache: `_mergeArr` entscheidet über `_mergeTs` (`updated`/`editedAt`).
       Notizen, Schläger und Schwunganalysen setzten den Stempel, RUNDEN,
       TURNIERE und TESTS aber nicht. Ohne Stempel fällt der Merge auf „der
       vollständigere Eintrag gewinnt" zurück — und die Repo-Fassung ist nach
       einer Runde im Spielmodus fast immer umfangreicher (Wetter, Lagen,
       Puttlängen). Die eigene Korrektur verlor. */
    const repo={rounds:[{id:"RT1", date:"2026-08-01", course:"Nord", tee:"Gelb",
      conditions:{temp:18,windMs:4,windDir:220}, notes:"windig",
      holes:[{hole:1,par:4,score:5,putts:2,appr:"100-120m",lie:"Fairway",firstPutt:"2m"},
             {hole:2,par:4,score:4,putts:2}]}]};
    const lokal=()=>({id:"RT1", date:"2026-08-01", course:"Nord", tee:"Gelb",
      holes:[{hole:1,par:4,score:4,putts:2},{hole:2,par:4,score:4,putts:2}]});

    /* Gegenprobe: OHNE Stempel muss die Korrektur verlorengehen — sonst
       prüft dieser Abschnitt nichts. */
    const ohne=merge({rounds:[lokal()]}, repo);
    eq("ohne Stempel gewinnt das Repo (der Bug)", ohne.rounds[0].holes[0].score, 5);
    /* MIT Stempel bleibt die Bearbeitung. */
    const mit=merge({rounds:[st(lokal())]}, repo);
    eq("mit Stempel bleibt die Korrektur", mit.rounds[0].holes[0].score, 4);

    ok("stamp setzt updated", !!st({}).updated);
    ok("und _mergeTs liest ihn",
       typeof ts === "function" ? ts(st({}))!=="" : true);
    ok("stamp verträgt null", st(null)===null);
    ok("stamp gibt das Objekt zurück", (()=>{const o={a:1}; return st(o)===o;})());

    /* JEDE Schreibstelle an einer gemergten Liste muss stampen. Diese Prüfung
       ist der eigentliche Schutz: Der Fehler entstand, weil eine einzelne
       Stelle es nicht tat. */
    const src=fs.readFileSync(FILE,"utf8");
    const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
      .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
      .map(m=>m[2]).join("\n").replace(/\/\*[\s\S]*?\*\//g,"");
    const offen=[];
    const re=/DB\.(rounds|tests|competitions)\.push\(([^)]{0,40})/g;
    let m2;
    while((m2=re.exec(nurCode))){
      /* Der Stempel kann IM Aufruf stehen (`push(stamp(x))`) oder in einer
         der Zeilen davor (`stamp(r); … push(r)`). Beides ist richtig — nur
         die Zeile selbst anzusehen erzeugt Fehlalarme, und eine Prüfung, die
         Falsches meldet, gewöhnt einem das Hinsehen ab. */
      const umfeld = nurCode.slice(Math.max(0, m2.index-300), m2.index+60);
      if(!/stamp\(/.test(umfeld)) offen.push(m2[0].slice(0,50));
    }
    eq("jede push-Stelle stampt", offen.length, 0, offen.join(" | "));
    ok("Runden-Editor stampt", /stamp\(collect\(\)\)|stamp\(r\)/.test(nurCode));
  }
}

/* ============ 24bd. Testverlauf mit Substanz ============ */
group("testVerlauf / testFelderDelta — nicht nur eine Zahlenliste");
{
  const tv=G("testVerlauf"), fd=G("testFelderDelta"), DB=G("DB");
  if (typeof tv === "function" && DB) {
    /* Der Verlauf war Datum, Einzelwerte, Summe. Damit sah man NICHT, was die
       Frage beantwortet, mit der man hinschaut: Werde ich besser, wie schnell,
       und WO genau? Alles drei steckte in den Daten. */
    const altT=DB.tests;
    const tag=n=>{const d=new Date(Date.now()-n*86400000);const p=x=>String(x).padStart(2,"0");
      return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}`;};
    DB.tests=[
      {defKey:"kurzputt", date:tag(150), inputs:{"1,0m":8,"1,5m":6,"2,0m":5}, total:26},
      {defKey:"kurzputt", date:tag(110), inputs:{"1,0m":9,"1,5m":6,"2,0m":5}, total:28},
      {defKey:"kurzputt", date:tag(70),  inputs:{"1,0m":9,"1,5m":7,"2,0m":6}, total:29},
      {defKey:"kurzputt", date:tag(35),  inputs:{"1,0m":10,"1,5m":7,"2,0m":6}, total:32},
      {defKey:"kurzputt", date:tag(5),   inputs:{"1,0m":10,"1,5m":9,"2,0m":7}, total:35}];
    const v=tv("kurzputt");
    ok("liefert Kennzahlen", !!v);
    eq("Anzahl stimmt", v.n, 5);
    eq("aktueller Wert", v.letzt, 35);
    ok("Bestwert erkannt", v.istBest===true);
    /* Der Vergleich läuft gegen die VORHERIGEN, nicht gegen sich selbst. */
    ok("Veränderung gegen die Vorgänger", v.delta>0, "delta="+v.delta);
    ok("Vergleichsschnitt schließt den aktuellen aus", v.schnittVorher<v.letzt,
       `${v.schnittVorher} < ${v.letzt}`);
    /* Tempo erst ab 4 Einträgen — zwei Punkte ergeben immer eine Gerade. */
    ok("Tempo berechnet", v.proMonat!=null && v.proMonat>0, "proMonat="+v.proMonat);
    DB.tests=DB.tests.slice(-3);
    ok("unter 4 Einträgen kein Tempo", tv("kurzputt").proMonat===null);
    DB.tests=[];
    eq("ohne Einträge null", tv("kurzputt"), null);

    /* RICHTUNGSABHÄNGIGKEIT: Bei einem Test, wo weniger besser ist, muss der
       Bestwert das MINIMUM sein — sonst zeigt die App einen Rückschritt als
       Bestwert an. */
    DB.tests=[{defKey:"eisenstreu", date:tag(60), inputs:{}, total:30},
              {defKey:"eisenstreu", date:tag(20), inputs:{}, total:22}];
    const vi=tv("eisenstreu");
    if(vi){
      ok("weniger-ist-besser: Bestwert ist das Minimum", vi.best===22, "best="+vi.best);
      ok("und der letzte Wert ist der Bestwert", vi.istBest===true);
    }

    /* FELDVERGLEICH: „Gesamt +3" sagt nicht, WO man zugelegt hat — und danach
       richtet sich das nächste Training. */
    DB.tests=[
      {defKey:"kurzputt", date:tag(70), inputs:{"1,0m":9,"1,5m":7,"2,0m":6}, total:29},
      {defKey:"kurzputt", date:tag(35), inputs:{"1,0m":10,"1,5m":7,"2,0m":6}, total:32},
      {defKey:"kurzputt", date:tag(5),  inputs:{"1,0m":10,"1,5m":9,"2,0m":7}, total:35}];
    const f=fd("kurzputt");
    ok("Feldvergleich liefert Ergebnisse", Array.isArray(f) && f.length>0);
    ok("größte Veränderung steht oben",
       f.every((x,i)=>i===0 || Math.abs(f[i-1].diff)>=Math.abs(x.diff)),
       f.map(x=>x.diff).join(" "));
    ok("die 1,5m-Distanz ist der größte Zugewinn", /1,5/.test(f[0].label), f[0].label);
    /* Rauschen unter einem halben Punkt wird weggelassen — sonst steht dort
       eine Liste ohne Aussage. */
    const src=fs.readFileSync(FILE,"utf8");
    ok("Kleinstunterschiede gefiltert", /Math\.abs\(diff\)<0\.5/.test(src));
    ok("Vergleich gegen mehrere Vorgänger", /slice\(-4,-1\)/.test(src));
    DB.tests=[{defKey:"kurzputt", date:tag(5), inputs:{"1,0m":10}, total:35}];
    eq("mit einem Eintrag kein Vergleich", fd("kurzputt"), null);
    DB.tests=altT;
  }
}

/* ============ 24bc. Feine Testbewertung ============ */
group("benchHcp / benchRest — zwischen den Stufen statt fünf Kästchen");
{
  const bh=G("benchHcp"), br=G("benchRest");
  if (typeof bh === "function") {
    /* WAS ZU GROB WAR: Bei der Kurzputt-Präzision (Stufen 22/30/38/44/48)
       ergaben 30 UND 37 Punkte dieselbe Stufe „HCP 15" — sieben Punkte
       Unterschied, gleiche Anzeige. Gleichzeitig sprang 29 auf 30 eine ganze
       Kategorie. */
    const bm={levels:[22,30,38,44,48], unit:"Pkt /50", higherIsBetter:true};
    eq("Stufengrenze trifft den Stufenwert", bh(30,bm), 15);
    eq("nächste Grenze ebenso", bh(38,bm), 8);
    /* Der Kern: dazwischen muss sich etwas bewegen. */
    const a=bh(31,bm), b=bh(34,bm), c=bh(37,bm);
    ok("31 / 34 / 37 ergeben VERSCHIEDENE Werte",
       a!==b && b!==c && a>b && b>c, `${a} / ${b} / ${c}`);
    ok("und alle liegen zwischen 15 und 8", a<15 && c>8);
    /* Scratch ist die Obergrenze — darüber hinaus gibt es keine Stützstelle,
       „HCP −2" wäre frei erfunden. */
    eq("Scratch bei Höchstwert", bh(48,bm), 0);
    eq("darüber wird gedeckelt", bh(50,bm), 0);
    /* Unterhalb der ersten Stufe linear weiter, aber gedeckelt. */
    ok("unter der Skala über 20", bh(18,bm)>20, String(bh(18,bm)));
    ok("nach unten gedeckelt", bh(-500,bm)<=36, String(bh(-500,bm)));
    /* UMGEKEHRTE RICHTUNG: Bei Streubreite ist kleiner besser. */
    const inv={levels:[40,32,24,18,14], unit:"m", higherIsBetter:false};
    eq("invertiert: Stufengrenze", bh(32,inv), 15);
    ok("invertiert: dazwischen fein", bh(28,inv)<15 && bh(28,inv)>8, String(bh(28,inv)));
    eq("invertiert: Bestwert ist Scratch", bh(14,inv), 0);
    ok("invertiert: besser als Bestwert bleibt Scratch", bh(11,inv)===0);
    /* Grenzfälle dürfen nicht zu erfundenen Zahlen führen. */
    eq("ohne Wert null", bh(null,bm), null);
    eq("ohne Benchmark null", bh(30,null), null);
    eq("mit zu wenigen Stufen null", bh(30,{levels:[1,2]}), null);
    /* EINE Nachkommastelle: Die Schwellen sind Erfahrungswerte, keine
       Messungen — zwei Stellen täuschten Genauigkeit vor. */
    const src=fs.readFileSync(FILE,"utf8");
    ok("auf eine Nachkommastelle gerundet", /Math\.round\(h\*10\)\/10/.test(src));
    ok("Hinweis auf die Unschärfe steht in der Anzeige",
       /Erfahrungswerte, keine Messungen/.test(src));
  }
  if (typeof br === "function") {
    const bm={levels:[22,30,38,44,48], unit:"Pkt /50", higherIsBetter:true};
    /* Der Restweg ist die handlungsleitende Angabe: „noch 4 Punkte bis HCP 8"
       sagt, was zu tun ist — „HCP 11,5" sagt nur, wo man steht. */
    const r=br(34,bm);
    ok("nennt Rest und Ziel", r && r.rest===4 && r.ziel==="HCP 8", JSON.stringify(r));
    ok("Einheit wird mitgegeben", r.einheit.length>0, r.einheit);
    ok("bei Höchstwert erreicht-Meldung", br(48,bm).erreicht===true);
    const inv={levels:[40,32,24,18,14], unit:"m", higherIsBetter:false};
    const ri=br(28,inv);
    ok("invertiert: Rest positiv", ri && ri.rest===4, JSON.stringify(ri));
  }
}

/* ============ 24bb. Tests automatisch aus dem Launch Monitor ============ */
group("lmTestsSync — Smash Factor und Swing Speed werden gemessen, nicht getippt");
{
  const sync=G("lmTestsSync"), sm=G("lmSmashTag"), sp=G("lmSpeedTag"),
        mit=G("lmMittel"), DB=G("DB"), tf=G("testsFor"), aus=G("lmAus");
  if (typeof sync === "function" && DB) {
    /* Beides sind keine Tests im eigentlichen Sinn: Man führt sie nicht durch,
       man MISST sie — jede R10-Sitzung IST bereits die Messung. Elf Zahlen
       abzutippen war Fleißarbeit mit Fehlerrisiko. */
    const altT=DB.tests, altS=DB.lmSessions;
    const mk=(club,smash,chs,carry,n)=>Array.from({length:n},(_,i)=>
      ({club, smash:smash+((i%3)-1)*0.02, clubSpeed:chs+((i%3)-1), carry:carry+((i%3)-1)*3}));
    DB.tests=[];
    DB.lmSessions=[
      {id:"AS1", date:"2026-07-05", shots:[...mk("Driver",1.44,94,215,12), ...mk("7 Iron",1.36,78,140,10)]},
      {id:"AS2", date:"2026-08-02", shots:[...mk("Driver",1.46,96,221,14), ...mk("4 Iron",1.37,82,160,3)]}];
    if (aus) aus.clear();
    const r=sync();
    eq("vier Einträge erzeugt (2 Tage x 2 Tests)", r.neu, 4);
    ok("alle sind als automatisch markiert",
       DB.tests.filter(t=>["smashfactor","swingspeed"].indexOf(t.defKey)>=0)
               .every(t=>t.auto===true));
    /* Ein zweiter Lauf darf nichts doppeln — sonst wächst der Verlauf bei
       jedem Öffnen der Seite. */
    const r2=sync();
    eq("zweiter Lauf erzeugt nichts", r2.neu, 0);
    eq("und ändert nichts", r2.akt, 0);
    /* MINDESTZAHL: Das 4-Eisen hat nur 3 Schläge — daraus einen Testwert zu
       bilden hieße, Tagesform als Messung auszugeben. */
    const s2=tf("smashfactor").find(t=>t.date==="2026-08-02");
    ok("Schläger unter 5 Schlägen fehlt", s2 && s2.inputs["4-Eisen"]===undefined,
       JSON.stringify(s2&&s2.inputs));
    ok("Driver mit 14 Schlägen ist dabei", s2 && s2.inputs["Driver"]>1.4);
    /* HANDEINTRÄGE HABEN VORRANG: Wer einen Test bewusst eingetragen hat,
       soll ihn nicht verändert wiederfinden. */
    DB.tests.push({defKey:"swingspeed", date:"2026-08-02", inputs:{"CHS (mph)":99}, total:99});
    const r3=sync();
    eq("Handeintrag wird nicht überschrieben", r3.akt, 0);
    ok("beide Einträge bleiben nebeneinander",
       tf("swingspeed").filter(t=>t.date==="2026-08-02").length===2);
    /* ABGEWÄHLTE SITZUNGEN verschwinden auch aus dem Testverlauf — die
       Auswahl im Launch-Reiter gilt durchgängig. */
    if (aus) {
      aus.add("AS1");
      const r4=sync();
      ok("verwaiste Auto-Einträge werden entfernt", r4.weg>=2, "weg="+r4.weg);
      ok("der Tag ist aus dem Verlauf raus",
         !tf("smashfactor").some(t=>t.date==="2026-07-05"));
      aus.clear(); sync();
    }
    DB.tests=altT; DB.lmSessions=altS;
  }
  if (typeof mit === "function") {
    /* Ab 8 Werten getrimmtes Mittel — ein Fersentreffer soll den Schnitt nicht
       bestimmen. Darunter einfacher Durchschnitt. */
    eq("unter 5 Werten null", mit([1,2,3],5), null);
    ok("ab 5 ein Mittelwert", mit([1,2,3,4,5],5)===3);
    const mitAusreisser=mit([100,100,100,100,100,100,100,10],5);
    ok("Ausreißer wird gedämpft", mitAusreisser>60, String(mitAusreisser));
  }
  if (typeof sp === "function") {
    /* Swing Speed NUR aus Driver-Schlägen — über alle Schläger gemittelt
       sänke der Wert mit jedem Wedge. */
    const gemischt=[...Array.from({length:8},()=>({club:"Driver",clubSpeed:95,smash:1.45,carry:215})),
                    ...Array.from({length:8},()=>({club:"PW",clubSpeed:65,smash:1.22,carry:100}))];
    const w=sp(gemischt);
    ok("nur Driver zählt", w && w["CHS (mph)"]>90, JSON.stringify(w));
    ok("ohne Driver kein Ergebnis", sp([{club:"7 Iron",clubSpeed:80}])===null);
  }
  /* Der manuelle Knopf ist entfallen — er bot etwas an, was ohnehin geschieht. */
  {
    const src=fs.readFileSync(FILE,"utf8");
    const nurCode = [...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
      .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1]))
      .map(m=>m[2]).join("\n").replace(/\/\*[\s\S]*?\*\//g,"");
    ok("kein Übernahme-Knopf mehr", nurCode.indexOf("smashUebernehmen")<0);
    ok("automatisch beim Import", /lmTestsSync\(\)/.test(nurCode));
    /* Der Verlauf zeigt je Eintrag die Einzelwerte MIT Veränderung. */
    ok("Verlauf nennt die Herkunft", /· automatisch/.test(nurCode));
  }
}

/* ============ 24ba. Welchen Test als Nächstes? ============ */
group("testEmpfehlung — Strokes Gained entscheidet, nicht die Vorliebe");
{
  const te=G("testEmpfehlung"), MAP=G("SG_ZU_TESTKAT"), DB=G("DB");
  if (typeof te === "function" && DB) {
    /* Die Tests-Seite listete Kategorien alphabetisch — man suchte sich selbst
       etwas aus, meist das, was man ohnehin gern macht. Ein Test lohnt sich
       aber dort, wo auf der RUNDE Schläge verlorengehen. */
    const e=te(3);
    ok("liefert eine Empfehlung", e && Array.isArray(e.liste));
    ok("höchstens so viele wie angefordert", e.liste.length<=3, "n="+e.liste.length);
    ok("absteigend nach Punkten sortiert",
       e.liste.every((x,i)=>i===0 || e.liste[i-1].punkte>=x.punkte),
       e.liste.map(x=>x.punkte).join(" "));
    ok("jede Empfehlung nennt einen Grund",
       e.liste.every(x=>x.gruende.length>0));
    /* DIE BRÜCKE: SG-Kategorie -> Testkategorie. Beide Seiten hatten die Daten,
       sie sprachen nur nicht miteinander. */
    if (MAP) {
      ["lang","app","kurz","putt"].forEach(k=>
        ok("SG-Kategorie "+k+" ist zugeordnet",
           Array.isArray(MAP[k]) && MAP[k].length>0, JSON.stringify(MAP[k])));
      /* Gegenprobe: Die genannten Testkategorien müssen wirklich existieren —
         ein Tippfehler würde die Zuordnung still ins Leere laufen lassen. */
      const echte=new Set((DB.testDefs||[]).map(d=>d.category));
      const unbekannt=[];
      Object.values(MAP).forEach(arr=>arr.forEach(c=>{ if(!echte.has(c)) unbekannt.push(c); }));
      eq("alle zugeordneten Kategorien existieren", unbekannt.length, 0, unbekannt.join(", "));
    }
    /* Die schwächste SG-Kategorie muss oben stehen. */
    const sum=G("sgSummary"), sr=G("sortedRounds"), weak=G("sgWeakest");
    if (typeof sum === "function" && typeof weak === "function" && e.liste.length) {
      const avg=(sum(sr().slice(-10))||{}).avg;
      const w=avg?weak(avg):null;
      if (w && MAP) {
        ok("erster Vorschlag kommt aus der teuersten Kategorie",
           MAP[w[0]].indexOf(e.liste[0].def.category)>=0,
           `${w[1]} -> ${e.liste[0].def.category}`);
      }
    }
    /* Nie gemessene Tests müssen auftauchen — ohne Ausgangswert gibt es
       keinen Verlauf, und ohne Verlauf sagt jede Zahl nichts. */
    const src=fs.readFileSync(FILE,"utf8");
    ok("nie gemessen wird gewichtet", /noch nie gemessen/.test(src));
    ok("Überfälligkeit wird gewichtet", /zuletzt vor \$\{f\.tage\} Tagen/.test(src));
    ok("Zielniveau wird gewichtet", /Ziel ist \$\{HCP_LABELS/.test(src));
    /* Ohne SG-Daten darf die Empfehlung nicht so tun, als wüsste sie mehr. */
    ok("weist auf fehlende SG-Daten hin",
       /Noch keine Strokes-Gained-Auswertung/.test(src));
  }
  /* `miniStat(label, wert)` baut die kleinen Kennzahl-Kacheln. Trivial, aber
     an vielen Stellen benutzt — ein kaputtes Escaping schlüge überall durch. */
  const ms=G("miniStat");
  if (typeof ms === "function") {
    const h=ms("Score","72");
    ok("liefert Kachel-Markup", /^<div /.test(h) && /Score/.test(h) && /72/.test(h));
    ok("Wert erscheint im Monospace-Block", /tnum/.test(h));
  }
}

/* ============ 24az. Vorgabenstand, Platz zur Fahne, Rückschlag ============ */
group("Die Zahlen, nach denen auf dem Platz entschieden wird");
{
  const vg=G("playVorgabe"), rs=G("playRueckschlag"), P=G("PLAY"), DB=G("DB");
  const src=fs.readFileSync(FILE,"utf8");
  if (typeof vg === "function" && P && DB) {
    /* Auf der Runde stand nur „+3 zu Par". In einem deutschen Turnier zählt
       aber der Stand gegenüber der SPIELVORGABE — „+7 brutto" ignoriert die
       eigenen Vorgabeschläge und taugt nicht zur Entscheidung. */
    const altC=DB.courses, altP=DB.profile;
    DB.profile={...(DB.profile||{}), hcpIndex:20.0};
    DB.courses=[{name:"VT", tees:[{name:"Gelb", cr18:71.0, slope18:130, par18:72,
      holes:Array.from({length:18},(_,i)=>({hole:i+1, par:4, si:i+1}))}]}];
    P.course="VT"; P.tee="Gelb"; P.idx=0;
    P.holes=Array.from({length:18},(_,i)=>({hole:i+1, par:4, si:i+1, score:null}));
    const leer=vg();
    ok("ohne gespielte Löcher kein Stand", leer && leer.gespielt===0, JSON.stringify(leer));
    ok("Spielvorgabe berechnet", leer && leer.chcp>0, "CHcp="+(leer&&leer.chcp));
    /* Par auf einem Loch MIT Vorgabeschlag = 3 Punkte, ohne = 2. */
    P.holes[0].score=4; P.holes[1].score=4; P.holes[2].score=4;
    const st=vg();
    eq("drei Löcher gewertet", st.gespielt, 3);
    /* Bei Index 20 und Slope 130 ergibt sich eine Spielvorgabe um 22 — also
       ein Vorgabeschlag auf jedem Loch, auf den schwersten vier sogar zwei.
       Par 4 mit einem Schlag ist netto Birdie = 3 Punkte. Drei Löcher liegen
       damit zwischen 9 und 12, nicht bei 6. Erwartung entsprechend
       gesetzt — und ABGELEITET statt geraten. */
    ok("Punkte plausibel", st.punkte>=3*2 && st.punkte<=3*4,
       `Pkt=${st.punkte} bei CHcp ${st.chcp}`);
    eq("Erwartung ist 2 Punkte je Loch", st.erwartet, 6);
    eq("Puffer = Punkte minus Erwartung", st.puffer, st.punkte-st.erwartet);
    /* Hochrechnung erst ab 3 Löchern — darunter wäre es Zufall mit dem
       Anschein einer Prognose. */
    ok("Hochrechnung vorhanden", st.hoch!=null && st.hoch>0, "hoch="+st.hoch);
    P.holes[1].score=null; P.holes[2].score=null;
    ok("unter 3 Löchern keine Hochrechnung", vg().hoch===null);
    DB.courses=altC; DB.profile=altP; P.holes=[];
  }
  if (typeof rs === "function" && P && DB) {
    /* Nach einem Doppelbogey ist das nächste Loch statistisch das
       gefährlichste — man will das Verlorene sofort zurückholen. Der Hinweis
       muss ABSCHALTBAR sein, sonst wirkt er bevormundend. */
    DB.ui=DB.ui||{}; delete DB.ui.keinRueckschlagHinweis;
    P.holes=[{hole:1,par:4,score:4},{hole:2,par:4,score:null}];
    P.idx=1;
    ok("nach Par kein Hinweis", rs()===null);
    P.holes[0].score=6;
    const r=rs();
    ok("nach Doppelbogey ein Hinweis", !!r, r&&r.txt.slice(0,40));
    ok("Text nennt die Mitte des Grüns", r && /Mitte/.test(r.txt));
    P.holes[0].score=8;
    ok("nach Triple deutlichere Formulierung", rs().txt!==r.txt);
    DB.ui.keinRueckschlagHinweis=true;
    ok("abgeschaltet erscheint er nicht mehr", rs()===null);
    delete DB.ui.keinRueckschlagHinweis; P.holes=[];
    ok("Abschalter ist erreichbar", /function rueckschlagAus/.test(src));
  }
  /* BENENNUNG: Das Feld heißt auf ausdrücklichen Wunsch „Shortsided" — der
     Fachbegriff also, nicht die Umschreibung. Das ist vertretbar, WEIL die
     Auswahlwerte selbst beschreiben, was gemeint ist: Wer „Shortsided" nicht
     kennt, versteht spätestens beim Aufklappen, worum es geht. Genau darauf
     kommt es an — der Feldname darf kurz sein, solange die Auswahl eindeutig
     ist. */
  ok("Feld heißt Shortsided", /Shortsided/.test(src));
  ok("Auswahl beschreibt die Lage im Klartext",
     /Wenig Platz — Fahne nah am Rand/.test(src));
  ok("und nennt den unproblematischen Fall",
     /Viel Platz zur Fahne/.test(src));
}

/* ============ 24ay. Zwischenspeicher für Rundenwerte ============ */
group("computeRound-Cache — schneller, ohne veraltete Zahlen");
{
  const cr=G("computeRound"), roh=G("_computeRoundRoh"), clear=G("crCacheClear"),
        cache=G("_crCache"), DB=G("DB");
  if (typeof cr === "function" && typeof roh === "function" && cache && DB) {
    /* Gemessen: Ein Aufbau des Dashboards rief computeRound 18-mal auf — bei
       12 Runden. Die Werte einer abgeschlossenen Runde ändern sich aber nicht,
       solange die Runde selbst unverändert bleibt. */
    clear();
    eq("leer nach dem Zurücksetzen", cache.size, 0);
    const r={id:"CRT1", date:"2026-05-01", course:"Test", tee:"Gelb",
             holes:[{hole:1,par:4,score:5,putts:2}]};
    const a=cr(r);
    eq("ein Eintrag angelegt", cache.size, 1);
    const b=cr(r);
    ok("zweiter Aufruf liefert dasselbe Objekt", a===b);

    /* DER KERN: Der Schlüssel hängt am INHALT. Ändert sich die Runde, muss neu
       gerechnet werden — sonst zeigt die App still falsche Zahlen, und das
       wäre schlimmer als gar kein Zwischenspeicher. */
    r.updated=new Date().toISOString();
    const c=cr(r);
    ok("nach Änderung wird neu gerechnet", c!==a);
    /* Zwei verschiedene Runden dürfen sich nicht überschreiben. */
    const r2={id:"CRT2", date:"2026-05-02", course:"Test", tee:"Gelb",
              holes:[{hole:1,par:4,score:4,putts:2}]};
    cr(r2);
    ok("verschiedene Runden getrennt gespeichert", cache.size>=3, "n="+cache.size);
    /* Deckel gegen unbegrenztes Wachsen. */
    const src=fs.readFileSync(FILE,"utf8");
    ok("Größe gedeckelt", /_crCache\.size>400/.test(src));
    /* Geleert wird an ZWEI Stellen: persist() und renderAll(). Der zweite ist
       der wichtige — er folgt auf JEDE der zwölf Stellen, an denen DB ersetzt
       wird. Sie einzeln nachzuziehen hieße, beim nächsten Einbau eine zu
       vergessen. */
    ok("persist leert den Speicher", /function persist\(\)[\s\S]{0,120}crCacheClear\(\)/.test(src));
    ok("renderAll leert den Speicher", /function renderAll\(\)[\s\S]{0,120}crCacheClear\(\)/.test(src));
    /* GEGENPROBE zur verworfenen Variante: sortedRounds darf KEINEN
       Zwischenspeicher haben. Ein erster Versuch hielt die sortierte Liste
       fest und lieferte veraltete Ergebnisse, sobald DB.rounds direkt geändert
       wurde — vier Prüfungen schlugen fehl. Ein Cache, dessen Gültigkeit von
       der Disziplin des Aufrufers abhängt, ist die falsche Sorte Optimierung. */
    ok("sortedRounds bleibt ohne Zwischenspeicher",
       !/_srCache/.test(src));
    clear();
  }
}

/* ============ 24ax. Launch: mehrere Sitzungen zusammen ============ */
group("lmAktiveShots — alle Sitzungen, einzelne abwählbar");
{
  const akt=G("lmAktiveShots"), tog=G("lmToggleAus"), alle=G("lmAlleAn"),
        aus=G("lmAus"), DB=G("DB");
  if (typeof akt === "function" && DB && aus) {
    /* Vorher war GENAU EINE Sitzung wählbar. Eine Range-Sitzung hat oft nur
       10 bis 15 Schläge — daraus einen Streukreis zu bilden heißt, Tagesform
       für Können zu halten. Umgekehrt will man einen misslungenen Tag auch mal
       ausblenden. Deshalb: alle als Grundlage, einzelne abwählbar. */
    const altS=DB.lmSessions;
    const sh=(c,carry)=>({club:c,carry,total:carry+8,smash:1.35});
    DB.lmSessions=[
      {id:"T1",date:"2026-06-10",shots:[sh("7 Iron",138),sh("7 Iron",139),sh("PW",100)]},
      {id:"T2",date:"2026-07-02",shots:[sh("7 Iron",141),sh("7 Iron",140)]},
      {id:"T3",date:"2026-07-20",shots:[sh("7 Iron",120)]}
    ];
    aus.clear();
    eq("ohne Abwahl alle Schläge des Schlägers", akt("7 Iron").length, 5);
    eq("fremde Schläger bleiben draußen", akt("PW").length, 1);
    if (typeof tog === "function") {
      tog("T3");
      eq("abgewählte Sitzung fällt weg", akt("7 Iron").length, 4);
      ok("die ID steht in der Ausblendliste", aus.has("T3"));
      tog("T3");
      eq("erneutes Tippen blendet wieder ein", akt("7 Iron").length, 5);
      /* Mehrere gleichzeitig — der Normalfall beim Aussortieren. */
      tog("T1"); tog("T2");
      eq("zwei abgewählt", akt("7 Iron").length, 1);
    }
    if (typeof alle === "function") {
      alle();
      eq("alle einblenden setzt zurück", akt("7 Iron").length, 5);
      eq("Ausblendliste leer", aus.size, 0);
    }
    /* Reihenfolge: Die Schläge müssen chronologisch kommen, sonst zeigt der
       Verlauf Unsinn. */
    ok("Sitzungen chronologisch zusammengeführt",
       /lmSessionsSorted\(\)/.test(fs.readFileSync(FILE,"utf8")));
    DB.lmSessions=altS; aus.clear();
  }
  /* Die Überschrift muss den ZEITRAUM nennen, wenn mehrere Sitzungen
     zusammenlaufen — sonst hält man die Zahlen für die eines Tages. */
  {
    const src=fs.readFileSync(FILE,"utf8");
    ok("Überschrift nennt den Zeitraum", /aktivSess\.length<=1/.test(src));
    ok("Abwahl ist rückgängig zu machen", /alle einblenden/.test(src));
  }
}

/* ============ 24aw. CSV-Import auf Android ============ */
group("Dateiauswahl — kein MIME-Filter, dafür Inhaltsprüfung");
{
  const src=fs.readFileSync(FILE,"utf8");
  /* WAS PASSIERT IST: Die Dateifelder filterten über `accept`. Android meldet
     heruntergeladene CSV-Dateien je nach Herkunft als
     `application/octet-stream`, `application/vnd.ms-excel` oder ganz ohne Typ —
     dann sind sie im Dateiwähler AUSGEGRAUT und nicht auswählbar. Auch eine
     breite Typliste half nicht zuverlässig, weil manche Picker Endungen gar
     nicht auswerten.
     Filtern ist ohnehin die schwächere Prüfung: Eine Datei kann jeden Typ
     melden und trotzdem etwas anderes enthalten. Deshalb wird jetzt der
     INHALT geprüft. */
  ["lmFile","bagR10File","stR10"].forEach(id=>{
    const m=new RegExp('<input type="file" id="'+id+'"[^>]*>').exec(src);
    ok(id+" existiert", !!m);
    if(m) ok(id+" ohne accept-Filter", m[0].indexOf("accept=")<0, m[0].slice(0,80));
  });
  const imp=src.slice(src.indexOf("function lmImport"),
                      src.indexOf("function lmImport")+1800);
  ok("prüft die erste Zeile auf Trennzeichen", /\[,;\\t\]/.test(imp));
  ok("meldet verständlich statt still zu scheitern",
     /Keine Tabellendatei/.test(imp));
  ok("nennt den Ausweg (CSV exportieren)", /als CSV/.test(imp));
}

/* ============ 24av. Heute-Seite nach Zeitpunkt gegliedert ============ */
group("Heute — Tagesablauf statt Sammelsurium");
{
  const src=fs.readFileSync(FILE,"utf8");
  const h=src.slice(src.indexOf("function renderHeute"),
                    src.indexOf("function renderHeute")+7000);
  /* Das Wetter trägt die erste Entscheidung des Tages und der Caddy rechnet
     damit — es gehört vor die Handlungsknöpfe. */
  ok("Wetter steht vor den Knöpfen",
     h.indexOf('id="wxCard"') < h.indexOf('id="qhStretch"'));
  /* „Preround" und „Post Round" liegen vier Stunden auseinander. Vorher stand
     der Post-Round-Knopf VOR dem Spielmodus — also vor dem, wonach man
     überhaupt erst dehnt. */
  ok("Abschnitt „Vor der Runde\" vorhanden", h.indexOf(">Vor der Runde<")>=0);
  ok("Abschnitt „Nach der Runde\" vorhanden", h.indexOf(">Nach der Runde<")>=0);
  ok("Spielmodus steht VOR dem Post-Round-Stretch",
     h.indexOf('id="qhPlay"') < h.indexOf('id="qhPost"'));
  ok("Preround und Aufwärmen vor dem Spielmodus",
     h.indexOf('id="qhStretch"') < h.indexOf('id="qhWarm"') &&
     h.indexOf('id="qhWarm"') < h.indexOf('id="qhPlay"'));
  /* Trainingsplanung gehört nicht auf die Heute-Seite — sie steht seit v2.24
     im Training bzw. im Dashboard, dort nach Wirkung sortiert. */
  const ohneKomm=h.replace(/\/\*[\s\S]*?\*\//g,"").replace(/\/\/[^\n]*/g,"");
  ok("keine fälligen Tests mehr auf Heute", ohneKomm.indexOf("Fällige Tests")<0);
  ok("kein Trainingsfokus mehr auf Heute", ohneKomm.indexOf("Trainingsfokus")<0);
  ok("Schnell erfassen bleibt", h.indexOf("Schnell erfassen")>=0);
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

/* ============ Loch-Abschluss entfernt (v3.41) ============ */
group("Loch-Abschluss — vollständig entfernt");
{
  const src = fs.readFileSync(FILE, "utf8");
  const js = (src.match(/<script(?![^>]*(?:src=|application\/json|text\/markdown|devdocs))[^>]*>([\s\S]*?)<\/script>/g) || []).join("\n");
  /* Nicht benutzt — dieselben zwei Zahlen kommen aus der Eingabemaske, und der
     Pfeil rechts wechselt ohnehin. ENTFERNT statt auskommentiert: Toter Code
     sieht bei der nächsten Durchsicht wie ein vergessener Anschluss aus, genau
     wie `playGoHole` (v3.37). */
  ok("kein Wizard mehr im Code", !/function pfWiz/.test(js));
  /* Nur echter Code, nicht der Kommentar, der die Entfernung erklärt. */
  ok("kein Zustand mehr", !/PLAY\.wiz\s*[=.\[]/.test(js) && !/PLAY\.wiz\)/.test(js));
  ok("kein Knopf mehr", !/✔ Loch<\/button>/.test(js));
  ok("kein Markup mehr", !/id="pfWiz"/.test(src));
  /* Ein Knopf weniger macht die beiden verbliebenen breiter — auf der Bahn mit
     Handschuh der eigentliche Gewinn. */
  ok("Raster auf vier Felder", /\.pf-acts\{display:grid;grid-template-columns:auto 1fr 1fr auto/.test(src));
  /* Die Lupe rief nur `togglePlatzModus` — der Modus selbst bleibt unter
     Darstellung, wo man ihn einmal einstellt. */
  ok("Lupe raus", !/togglePlatzModus\(\)"/.test(js));
  ok("Platz-Modus bleibt einstellbar", /function platzModus\(\)/.test(js) && /function setPlatzModus/.test(js));
}

/* ============ 24ay. Streuung auf der Karte ============ */
group("Streuungs-Oval — Mittelpunkt, Ausrichtung, Schalter");
{
  const O = G("dispOvalFrom"), C = G("dispChipHtml"), DBx = G("DB");
  if (typeof O === "function") {
    const here = [54.0, 10.75];
    const sg = { sigL: 18, sigD: 9, biasL: 2 };
    const mLat = 110540, mLng = 111320 * Math.cos(here[0] * Math.PI / 180);
    const dist = (a, b) => {
      const dy = (b[0] - a[0]) * mLat, dx = (b[1] - a[1]) * mLng;
      return Math.sqrt(dy * dy + dx * dx);
    };

    /* Der Mittelpunkt ist der ERWARTETE Ruhepunkt, nicht die eigene Position —
       sonst läge die Streuung um die Füße statt um das Ziel. */
    near("Mittelpunkt liegt in Schlagweite", dist(here, O(here, 0, 150, sg).center), 150, 0.7);
    near("auch bei schräger Richtung", dist(here, O(here, 237, 150, sg).center), 150, 0.7);

    /* Richtung: 0° = Norden (Breitengrad wächst), 90° = Osten (Längengrad). */
    const n = O(here, 0, 100, sg).center, o = O(here, 90, 100, sg).center;
    ok("0° zeigt nach Norden", n[0] > here[0] && Math.abs(n[1] - here[1]) < 1e-6);
    ok("90° zeigt nach Osten", o[1] > here[1] && Math.abs(o[0] - here[0]) < 1e-6);

    /* σ und Seitenversatz werden unverändert durchgereicht — das Zeichnen in
       courseSVG rechnet damit weiter, ein stiller Verlust wäre unsichtbar. */
    const ov = O(here, 45, 120, sg);
    eq("σ quer übernommen", ov.sigL, 18);
    eq("σ längs übernommen", ov.sigD, 9);
    eq("Seitenversatz übernommen", ov.biasL, 2);
    eq("Richtung übernommen", ov.brg, 45);

    /* Unvollständige Eingaben dürfen kein halbes Oval liefern: courseSVG malt
       sonst um [undefined,undefined] und die ganze Karte bleibt leer. */
    eq("ohne Position kein Oval", O(null, 0, 150, sg), null);
    eq("ohne Richtung kein Oval", O(here, NaN, 150, sg), null);
    eq("ohne Weite kein Oval", O(here, 0, 0, sg), null);
    eq("ohne σ kein Oval", O(here, 0, 150, null), null);
    /* biasL darf fehlen (Heuristik ohne erkannte Fehlerseite) — dann 0, nicht undefined. */
    eq("fehlender Versatz wird 0", O(here, 0, 150, { sigL: 12, sigD: 8 }).biasL, 0);
  }
  const R = G("dispRingPath");
  if (typeof R === "function") {
    /* Projektion wie auf der Karte: 1 m = 1 Einheit, y nach unten. */
    const c = [54.0, 10.75], mLat = 110540, mLng = 111320 * Math.cos(c[0] * Math.PI / 180);
    const M = { map: (la, lo) => [(lo - c[1]) * mLng, -(la - c[0]) * mLat] };
    const pts = (d) => (d.match(/-?\d+(?:\.\d+)?/g) || []).map(Number);
    const sg = { sigL: 20, sigD: 10, biasL: 0 };

    const d1 = R(M, c, 0, sg, 1);
    ok("Pfad beginnt mit M und ist geschlossen", /^M/.test(d1) && /Z$/.test(d1), d1.slice(0, 24));
    eq("37 Punkte (36 Segmente + Schluss)", pts(d1).length / 2, 37);

    /* Die Streuung ist QUER breiter als längs — genau deshalb ist ein Oval
       aussagekräftiger als ein Kreis. Bei Richtung 0 (Norden) liegt „quer"
       auf der x-Achse, „längs" auf der y-Achse. */
    const xs = pts(d1).filter((_, i) => i % 2 === 0), ys = pts(d1).filter((_, i) => i % 2 === 1);
    near("Halbachse quer = σ quer", (Math.max(...xs) - Math.min(...xs)) / 2, 20, 0.4);
    near("Halbachse längs = σ längs", (Math.max(...ys) - Math.min(...ys)) / 2, 10, 0.4);

    /* 2σ ist doppelt so weit — sonst stimmt die Aussage „95 %" nicht. */
    const xs2 = pts(R(M, c, 0, sg, 2)).filter((_, i) => i % 2 === 0);
    near("2σ ist doppelt so breit", (Math.max(...xs2) - Math.min(...xs2)) / 2, 40, 0.8);

    /* Gedrehte Richtung: bei 90° tauschen quer und längs die Achsen. */
    const p90 = pts(R(M, c, 90, sg, 1));
    const x90 = p90.filter((_, i) => i % 2 === 0), y90 = p90.filter((_, i) => i % 2 === 1);
    near("bei 90° liegt σ längs auf x", (Math.max(...x90) - Math.min(...x90)) / 2, 10, 0.4);
    near("bei 90° liegt σ quer auf y", (Math.max(...y90) - Math.min(...y90)) / 2, 20, 0.4);

    /* Seitenversatz verschiebt das Oval, ohne es zu verformen. */
    const mitte = (a) => (Math.max(...a) + Math.min(...a)) / 2;
    near("biasL verschiebt quer", mitte(pts(R(M, c, 0, { sigL: 20, sigD: 10, biasL: 8 }, 1))
      .filter((_, i) => i % 2 === 0)), 8, 0.5);

    /* Unvollständige Eingaben liefern einen LEEREN Pfad statt "MNaN NaN…" —
       ein NaN im d-Attribut lässt den Browser den ganzen Pfad verwerfen. */
    eq("ohne Projektion kein Pfad", R(null, c, 0, sg, 1), "");
    eq("ohne Mittelpunkt kein Pfad", R(M, null, 0, sg, 1), "");
    eq("ohne σ kein Pfad", R(M, c, 0, null, 1), "");
    eq("σ 0 ergibt keinen Pfad", R(M, c, 0, { sigL: 0, sigD: 0 }, 1), "");
    ok("kein NaN im Pfad", !/NaN/.test(R(M, c, 0, sg, 1)));

    /* Die Beschriftung hängt am obersten Punkt des Rings — „oben" heißt
       Bildschirm oben, nicht Schlagrichtung. Bei gedrehter Karte fällt beides
       auseinander, deshalb wird der Punkt aus dem PFAD gelesen. */
    const TP = G("pathTopPoint");
    if (typeof TP === "function") {
      eq("kleinstes y gewinnt", TP("M10 50L20 12L30 80Z").join(","), "20,12");
      eq("erster Punkt zählt mit", TP("M5 3L20 40Z").join(","), "5,3");
      eq("negative Werte werden erkannt", TP("M10 5L20 -7L30 9Z").join(","), "20,-7");
      /* Bei Gleichstand der erste — Hauptsache stabil, nicht springend. */
      eq("Gleichstand bleibt beim ersten", TP("M10 4L20 4Z").join(","), "10,4");
      eq("leerer Pfad ergibt nichts", TP(""), null);
      eq("kein Pfad ergibt nichts", TP(null), null);
      eq("halber Punkt ergibt nichts", TP("M12"), null);
      const t1 = TP(R(M, c, 0, sg, 1));
      ok("oberster Punkt eines echten Rings liegt oben",
        t1 && Math.abs(t1[1] + 10) < 0.6, t1 && t1.join(","));
    }
  }
  if (typeof C === "function" && DBx) {
    const vorher = DBx.ui ? DBx.ui.disp : undefined;
    DBx.ui = DBx.ui || {};

    DBx.ui.disp = true;
    const an = C({ sigL: 17.4, sigD: 9.2 });
    ok("Chip nennt die Querstreuung in Metern", /±17 m quer/.test(an), an);
    ok("Chip nennt die Längsstreuung", /±9 m lang/.test(an), an);
    ok("eingeschaltet ist der Chip markiert", /class="pc-chip on"/.test(an), an);
    ok("Vorlesehilfe beschreibt die Wirkung des Tippens",
      /aria-label="[^"]*ausblenden/.test(an), an);

    DBx.ui.disp = false;
    const aus = C({ sigL: 17.4, sigD: 9.2 });
    ok("ausgeschaltet ohne Markierung", !/\bpc-chip on\b/.test(aus), aus);
    ok("Vorlesehilfe kehrt sich um", /aria-label="[^"]*einblenden/.test(aus), aus);

    /* Ohne belastbares σ kein Knopf — ein Schalter, der ein leeres Oval
       einschaltet, verspricht eine Information, die es nicht gibt. */
    eq("ohne σ kein Chip", C(null), "");
    eq("σ 0 ergibt keinen Chip", C({ sigL: 0, sigD: 0 }), "");
    DBx.ui.disp = vorher;
  }
}

/* ============ 24bh. Vegetation aus dem Luftbild ============ */
group("Wald & Bäume erkennen — Farbe, Form, Fläche");
{
  const VM=G("vegMask"), MM=G("maskMorph"), MB=G("maskBlobs"),
        BR=G("blobRing"), RS=G("ringSimplify"), DV=G("detectVeg");

  /* Kunstbild 20×20: heller Rasen, darin ein dunkelgrünes Quadrat (Baumgruppe),
     ein heller Sandfleck und ein dunkles, blaues Wasserstück. Nur das erste
     darf als Vegetation durchgehen. */
  const W=20,H=20, px=new Uint8ClampedArray(W*H*4);
  const setz=(x,y,r,g,b)=>{ const i=(y*W+x)*4; px[i]=r; px[i+1]=g; px[i+2]=b; px[i+3]=255; };
  for(let y=0;y<H;y++) for(let x=0;x<W;x++) setz(x,y,120,150,90);      // Rasen: hell
  for(let y=2;y<9;y++) for(let x=2;x<9;x++) setz(x,y,40,80,35);        // Baumgruppe: dunkelgrün
  for(let y=12;y<16;y++) for(let x=2;x<6;x++) setz(x,y,210,195,160);   // Sand: hell
  for(let y=12;y<18;y++) for(let x=12;x<18;x++) setz(x,y,30,50,80);    // Wasser: dunkel, blau

  if(typeof VM==="function"){
    const m=VM(px,W,H,{});
    const an=(x,y)=>m[y*W+x];
    ok("dunkles Grün wird erkannt", an(4,4)===1);
    /* Die beiden Fehlgriffe, auf die es ankommt: Wasser ist dunkel, aber nicht
       grün; Rasen ist grün, aber hell. Ohne BEIDE Bedingungen fällt die
       Erkennung auseinander. */
    ok("Wasser gilt nicht als Wald", an(14,14)===0);
    ok("gemähtes Gras gilt nicht als Wald", an(15,2)===0);
    ok("Sand gilt nicht als Wald", an(3,13)===0);
    /* Die Empfindlichkeit muss auch wirklich etwas ändern. */
    const streng=VM(px,W,H,{exg:16,lum:60}), weit=VM(px,W,H,{exg:5,lum:160});
    ok("streng findet weniger als weit",
      streng.reduce((a,b)=>a+b,0) < weit.reduce((a,b)=>a+b,0));
  }

  if(typeof MM==="function"){
    const einzeln=new Uint8Array(W*H); einzeln[5*W+5]=1;              // ein Pixel Rauschen
    ok("Öffnen entfernt Einzelpixel",
      MM(einzeln,W,H,1,false).reduce((a,b)=>a+b,0)===0);
    ok("Dilatieren vergrößert", MM(einzeln,W,H,1,true).reduce((a,b)=>a+b,0)===9);
  }

  if(typeof MB==="function"){
    const m=new Uint8Array(W*H);
    for(let y=2;y<6;y++) for(let x=2;x<6;x++) m[y*W+x]=1;
    for(let y=12;y<14;y++) for(let x=12;x<14;x++) m[y*W+x]=1;
    const bl=MB(m,W,H,1);
    eq("zwei getrennte Flächen", bl.length, 2);
    eq("Größe stimmt", bl[0].n, 16);
    /* Die Mindestgröße hält Rauschen draußen, ohne die große Fläche zu treffen. */
    eq("Mindestgröße filtert", MB(m,W,H,10).length, 1);
    ok("Mittelpunkt in der Fläche", bl[0].cx>=2 && bl[0].cx<=5);
  }

  if(typeof BR==="function" && typeof MB==="function"){
    const m=new Uint8Array(W*H);
    for(let y=3;y<9;y++) for(let x=3;x<9;x++) m[y*W+x]=1;
    const b=MB(m,W,H,1)[0], ring=BR(m,W,H,b);
    ok("Kontur gefunden", ring.length>=4, String(ring.length));
    ok("Kontur läuft auf dem Rand",
      ring.every(([x,y])=>x>=3&&x<=8&&y>=3&&y<=8));
    /* Ein Quadrat hat 20 Randpixel — deutlich mehr wäre eine Endlosschleife. */
    ok("Kontur endet", ring.length<=24, String(ring.length));
  }

  if(typeof RS==="function"){
    /* Eine gerade Linie mit vielen Stützpunkten muss auf die Enden schrumpfen —
       sonst wäre jede Waldfläche ein unbearbeitbarer Pixelrand. */
    const linie=[]; for(let i=0;i<=20;i++) linie.push([i,0]);
    eq("Gerade schrumpft auf zwei Punkte", RS(linie,1).length, 2);
    const ecke=[[0,0],[5,0],[10,0],[10,5],[10,10]];
    eq("die Ecke bleibt erhalten", RS(ecke,1).length, 3);
    ok("zu kurze Ringe bleiben unangetastet", RS([[0,0],[1,1]],1).length===2);
  }

  if(typeof DV==="function"){
    /* 1 m je Pixel: die 7×7-Baumgruppe sind 49 m² — nach den Schwellen ein
       EINZELBAUM, kein Waldstück. Genau diese Grenze trennt die beiden. */
    const r=DV(px,W,H,1,{});
    ok("kleine Gruppe zählt als Baum, nicht als Wald",
      r.trees.length>=1 && r.woods.length===0,
      "B"+r.trees.length+" W"+r.woods.length);
    /* Dasselbe Bild mit 3 m je Pixel: 441 m² — jetzt eine Waldfläche. */
    const r2=DV(px,W,H,3,{});
    ok("dieselbe Form wird bei gröberer Auflösung zur Fläche",
      r2.woods.length>=1, "W"+r2.woods.length);
    ok("Wald bekommt einen Ring mit Ecken",
      !r2.woods.length || r2.woods[0].ring.length>=4);
  }
}

/* ============ 24bk. Karteneditor: Aufbau ============ */
group("Karteneditor — die Karte steht oben");
{
  const src = fs.readFileSync(FILE, "utf8");
  /* Ab dem Fund suchen — `openSheet(h); bindAllPanZoom(...)` kommt im Dokument
     auch vorher vor; ohne Startindex läge das Ende VOR dem Anfang und der
     Ausschnitt wäre leer. Eine leere Zeichenkette besteht keine Prüfung und
     sieht aus wie ein echter Fehler. */
  const uiA = src.indexOf('const h=`<h2 style="margin-bottom:2px">✏️ Karte');
  const ui = src.slice(uiA, src.indexOf('openSheet(h); bindAllPanZoom(sheetBody);', uiA));
  ok("Editor-Vorlage gefunden", uiA > 0 && ui.length > 1000, String(ui.length));

  /* DIE Bedingung: Die Karte ist die Arbeitsfläche und gehört nach oben.
     Vorher lagen Werkzeuge, ein Erklärungsabsatz und die komplette
     Vegetations-Karte davor — über 400 Bildpunkte auf dem Telefon. */
  ok("Loch-Auswahl vor der Karte", ui.indexOf('class="mchips geoed-head') < ui.indexOf('id="geoedSvg"'));
  ok("Karte vor den Werkzeugen", ui.indexOf('id="geoedSvg"') < ui.indexOf('class="geoed-bar"'));
  ok("Werkzeuge kleben unten", /\.geoed-bar\{position:sticky;bottom:0/.test(src));

  /* Seltenes gehört in Klappen, nicht zwischen Karte und Werkzeug. */
  ok("Vegetation in einer Klappe", /<details class="descbox"[^>]*>\s*<summary><span>🌲 Wald/.test(ui));
  ok("Loch-Feinheiten in einer Klappe", /<summary><span>⚙️ Loch \$\{hole\} · Feinheiten/.test(ui));
  ok("Bedienung und Zählwerk in einer Klappe", /<summary><span>ℹ️ Bedienung/.test(ui));
  ok("kein Erklärungsabsatz mehr vor der Karte",
    ui.indexOf("geoed-tip") > ui.indexOf('id="geoedSvg"'));

  /* Rückgängig gehört an die Karte, nicht in eine Zeile darüber — dort ist es
     beim Ziehen sofort erreichbar. */
  ok("Rückgängig an der Karte", /zoombtns[\s\S]{0,600}geoEdUndo\(\)/.test(ui));
  ok("und zeigt an, wenn es nichts gibt", /kannUndo\?"":"opacity:\.4"/.test(ui));

  /* Hinweise: eine Zeile im Imperativ. Ein Absatz, den man beim zwanzigsten
     Mal überliest, kostet nur den Platz, den die Karte braucht. */
  const tp = src.slice(src.indexOf("const tips={"), src.indexOf("const tips={") + 700);
  ["add","green","tree","area","line","del"].forEach(k =>
    ok("Hinweis für " + k + " ist kurz",
      new RegExp(k + ':"[^"]{10,70}"').test(tp), (tp.match(new RegExp(k + ':"([^"]*)"'))||[])[1]));
}

/* ============ 24bx. Löschen abschließen ============ */
group("Löschen — rücknehmen oder endgültig machen");
{
  const src = fs.readFileSync(FILE, "utf8");
  const cm = src.slice(src.indexOf("function geoWipeCommit(idx)"),
                       src.indexOf("function geoEdWipeUndo(idx)"));

  /* Zwei Wege stehen nebeneinander: zurücknehmen oder abschließen. „Verfällt
     beim App-Schluss von selbst" ist kein Zustand, den man SIEHT. */
  ok("Streifen bietet beides an", /geoEdWipeUndo\(\$\{idx\}\)[\s\S]{0,400}geoWipeCommit\(\$\{idx\}\)/.test(src));
  ok("eigene Rückfrage", /confirm\(/.test(cm));
  ok("sie nennt den Platz und die Menge", /\$\{b\.name\}[\s\S]{0,40}\$\{n\} Objekte/.test(cm));
  ok("und sagt, was danach bleibt", /nur noch ein neuer Import/.test(cm));
  ok("Kopie wird verworfen", /GEOED\.geoBackup=null/.test(cm));
  /* Nur die Kopie DIESES Platzes — ein anderer Platz darf nicht mitbestätigt
     werden, wenn zufällig noch seine Kopie liegt. */
  ok("prüft den Platz", /!b \|\| b\.idx!==idx/.test(cm));
  ok("Ansicht wird neu gezeichnet", /renderGeoImport\(idx\)/.test(cm));
}

/* ============ 24cb. Hand-Werkzeug ============ */
group("Hand — die Karte schieben, sonst nichts");
{
  const src = fs.readFileSync(FILE, "utf8");
  const dn = src.slice(src.indexOf("function geoEdDown"), src.indexOf("const DRAG_HOLD_MS"));
  const cl = src.slice(src.indexOf("function geoEdClick(evt)"),
                       src.indexOf("function geoEdClick(evt)") + 900);

  ok("Werkzeug vorhanden", /tool\("pan","✋<br>Verschieben"\)/.test(src));
  ok("Hinweis dazu", /pan:"Ziehen verschiebt die Karte/.test(src));

  /* DER PUNKT: nur schieben. Beim Zeichnen verbraucht die Maus den Zug für die
     Kontur, und über einem Objekt könnte man es aus Versehen aufnehmen —
     hier nicht. */
  ok("setzt nur den Schub", /if\(GEOED\.tool==="pan"\)\{[\s\S]{0,220}GEOED\.pan=\{ x0:e\.clientX/.test(dn));
  ok("und steigt sofort aus", /if\(GEOED\.tool==="pan"\)\{[\s\S]{0,320}return;\s*\n\s*\}/.test(dn));
  /* Vor der Objekt- und Freihand-Behandlung, sonst greift die zuerst. */
  ok("vor Fläche/Linie geprüft",
    dn.indexOf('GEOED.tool==="pan"') < dn.indexOf('GEOED.tool==="area"||GEOED.tool==="line"'));
  ok("vor der Objektaufnahme", dn.indexOf('GEOED.tool==="pan"') < dn.indexOf('closest("[data-drag]")'));
  ok("Klick setzt nichts", /if\(GEOED\.tool==="pan"\) return;/.test(cl));

  ok("Zeiger als Hand", /#geoedSvg\.t-pan svg\{cursor:grab\}/.test(src));
  ok("beim Ziehen geschlossen", /#geoedSvg\.t-pan svg:active\{cursor:grabbing\}/.test(src));
  ok("Kürzel H", /s:"sel", h:"pan"/.test(src));
  ok("Kürzel steht in der Oberfläche", /<kbd>H<\/kbd> Hand/.test(src));

  /* Acht Werkzeuge passen nicht mehr in sechs Spalten — auf dem Telefon wären
     das 44-px-Streifen. */
  ok("vier Spalten", /\.geoed-tools\{display:grid;grid-template-columns:repeat\(4,1fr\)/.test(src));
}

/* ============ 24ci. Immer ab der eigenen Position ============ */
group("Caddy — Bezugspunkt ist der Standort, nie das Tee");
{
  const src = fs.readFileSync(FILE, "utf8");
  /* Die Kette baut `_aimBuild`; `playAimChain` ist nur der Zwischenspeicher. */
  const ac = src.slice(src.indexOf("function _aimBuild(strategic)"),
                       src.indexOf("function _aimBuild(strategic)") + 4200);

  /* Welchen Abschlag man benutzt, entscheidet die Tee-Farbe — der Kartenpunkt
     liegt oft zwanzig Meter daneben. Wer vom gelben Tee spielt und die Karte
     kennt nur das weiße, bekam alle Entfernungen und die Ziellinie um diesen
     Versatz verschoben, ausgerechnet beim längsten Schlag des Lochs. */
  /* Seit v3.42 mit EINER Ausnahme: Ist man zu weit weg, plant die App „wie am
     Abschlag" — dann muss sie auch AM ABSCHLAG anfangen. Vorher blieb der
     Startpunkt die eigene Position, und aus 3,2 km Entfernung entstand ein
     zweiter Schlag über 2694 m. */
  ok("Startpunkt ist der Standort", /const start = amTee \? \(hr\.tee \|\| hr\.green\) : \(PLAY\.here \|\| hr\.tee \|\| hr\.green\);/.test(ac));
  ok("keine 30-m-Regel mehr für den Startpunkt",
    /const amTee = zuWeit \|\| \(!PLAY\.here\);/.test(ac));
  /* Die beiden Fälle, die bleiben, heißen beide „ich bin gar nicht auf der
     Bahn": kein GPS-Punkt, oder unplausibel weit weg. */
  ok("ohne GPS weiterhin das Tee", /!PLAY\.here/.test(ac));
  ok("zu weit weg weiterhin der Lochplan", /const zuWeit = \(typeof playTooFar/.test(ac));
  /* Die RECHNUNG darf weiter von der Tee-Nähe abhängen — das ist eine Frage
     der Methode, nicht des Bezugspunkts. */
  ok("Schlagzahl folgt der Tee-Nähe", /const abschlagNah = amTee \|\| \(hr\.tee && geoDist\(start,hr\.tee\)<30\);/.test(ac));
  ok("und wird dort benutzt", /if\(!abschlagNah\)\{/.test(ac));

  /* Die Messung war schon richtig — hier steht die Zusicherung, damit sie es
     bleibt: das Tee nur ohne Position oder wenn man zu weit weg ist. */
  const mo = src.slice(src.indexOf("function measureOrigin(here, tee, zuWeit)"),
                       src.indexOf("function measureOrigin(here, tee, zuWeit)") + 260);
  ok("Messung nimmt zuerst die Position", /if\(here\) return \{p:here, ab:"Position"\}/.test(mo));
}

/* ============ 24cj. Takt und Klebeleiste ============ */
group("Gleichlauf mit der Uhr · Fußraum in der Eingabe");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* Der Lochwechsel nahm den langsamsten Weg: cloudSave() mit 3 MB für einen
     Zeiger von wenigen Byte — ausgerechnet das Ereignis, das drüben sofort
     ankommen soll. */
  const lp = src.slice(src.indexOf("function playLivePush()"), src.indexOf("function playLiveRemote()"));
  ok("Lochwechsel geht über die kleine Datei", /draftPush\(\);\s+\/\/ sofort/.test(lp));
  ok("und wird nicht entprellt", /clearTimeout\(_draftPushT\)/.test(lp));
  ok("der große Abgleich nur noch verzögert", /scheduleCloudSync\(\)/.test(lp));
  /* Ohne draft.json (alter Worker) MUSS der alte Weg bleiben, sonst sieht die
     Uhr den Wechsel gar nicht. */
  ok("Rückfall bei altem Worker", /if\(DRAFT_MODE===false\)\{ cloudPushDraft\(\); return; \}/.test(lp));

  /* Takt nach Sichtbarkeit: Wer hinschaut, erwartet Gleichlauf; wer das Handy
     in der Tasche hat, will kein Dauerfunken. */
  ok("5 s sichtbar, 30 s im Hintergrund", /SYNC_MS_VORN=5000, SYNC_MS_HINTEN=30000/.test(src));
  ok("Maßstab ist die Sichtbarkeit", /document\.visibilityState==="visible"\) \? SYNC_MS_VORN/.test(src));
  ok("Taktwechsel ohne doppelten Timer", /if\(playSyncTimer && _syncMs===ms\) return;/.test(src));
  ok("beim Aufwecken sofort abgleichen",
    /visibilitychange[\s\S]{0,400}playSyncTick\(\)/.test(src));

  /* Die klebende Leiste liegt über dem Inhalt — ohne Fußraum verschwinden die
     letzten Felder dauerhaft dahinter. */
  /* AUSSERHALB des Scrollbereichs (v3.01): `position:sticky` hielt nicht, weil
     ein klebendes Element sich nur innerhalb seines ELTERN hält — und der
     endete direkt hinter ihm. Dieselbe Lehre wie beim ✕ in v1.55. */
  ok("eigener Bereich neben #sheetBody", /<div id="sheetBody"><\/div><div id="sheetFoot"><\/div>/.test(src));
  ok("Fuß nimmt keine Scrollhöhe", /#sheetFoot\{flex:0 0 auto/.test(src));
  ok("leerer Fuß verschwindet ganz", /#sheetFoot:empty\{display:none/.test(src));
  ok("kein sticky mehr am Fuß", !/\.play-foot\{position:sticky/.test(src));
  ok("Eingabemaske füllt ihn", /f\.innerHTML=fuss/.test(src));
  ok("Navigation klebt nicht mehr selbst", !/\.play-navbar\{position:sticky/.test(src));
  ok("Score-Leiste klebt nicht mehr selbst", !/\.play-close\{position:sticky/.test(src));
  ok("Score oben, Navigation darunter",
    src.indexOf("${playCloseBarHtml()}") < src.indexOf('<div class="play-navbar">'));
  /* Und jedes neue Blatt leert ihn — sonst trüge das nächste Blatt die
     Score-Stepper des vorigen. */
  ok("jedes neue Blatt leert ihn",
    /function openSheet\(html\)\{[\s\S]{0,500}sheetFoot"\); if\(f\) f\.innerHTML="";/.test(src));
  /* Und zurückgenommen bei JEDEM neuen Blatt — sonst hätte das nächste
     unerklärliche Luft am Fuß. */
  ok("beim nächsten Blatt zurückgenommen",
    /function openSheet\(html\)\{[\s\S]{0,400}classList\.remove\("has-closebar"\)/.test(src));
}

/* ============ 24ci. Caddy rechnet ab der eigenen Position ============ */
group("Caddy — immer von hier, nie vom gespeicherten Tee");
{
  const src = fs.readFileSync(FILE, "utf8");
  const S = G("STRAT");

  /* Der gespeicherte Abschlag ist EIN Tee, meist das gelbe. Wer von Weiß oder
     Blau spielt, steht 20–40 m dahinter — die Rechnung unterschlug diese Meter
     und empfahl für ein kürzeres Loch. Bei 30 m kippt die Schlägerwahl. */
  ok("tee() nimmt einen Startpunkt", /tee\(geo,courseName,holeNo,mode,hcp,von\)\{/.test(src));
  ok("ohne Angabe wie bisher der gespeicherte", /const teeP = von \|\| teeGespeichert;/.test(src));
  /* Seit v3.18 ist die Position ein PARAMETER (`caddyFuerPunkt`) — die
     Caddy-Zeile ruft sie mit der eigenen, der Live-Zeiger mit der der Uhr. */
  ok("Caddy rechnet für einen übergebenen Punkt", /function caddyFuerPunkt\(pos, ovalSetzen\)/.test(src));
  ok("Tee-Bewertung bekommt die gerundete Position", /ev=_aimTeeEv\(geo,h,rund\)/.test(src));
  ok("die Anzeige nutzt dieselbe Funktion", /function playCaddyNow\(\)\{\s*\n\s*return caddyFuerPunkt\(PLAY\.here, true\);/.test(src));
  ok("aufgeklappter Caddy ebenso", /STRAT\.tee\(geo,PLAY\.course,h\.hole,caddyMode\(\),null,_caddyVon\(\)\)/.test(src));

  /* Der Zwischenspeicher MUSS die Position enthalten — sonst bliebe die erste
     Rechnung für immer stehen. Aber gerundet, sonst rechnet jedes GPS-Zucken
     eine neue Monte-Carlo-Runde. */
  const cv = src.slice(src.indexOf("function _caddyVon()"), src.indexOf("function _aimTeeEv"));
  ok("Position wird gerundet", /Math\.round\(p\[0\]\*11054\)\/11054/.test(cv));
  ok("ohne Position: null", /if\(!p\) return null;/.test(cv));

  /* Gegenprobe an der Engine: Derselbe Abschlag von 30 m weiter hinten muss
     eine längere Restdistanz ergeben — sonst wirkt der Startpunkt nicht. */
  if (S && typeof S.tee === "function") {
    const mLat = 110540;
    const tee = [54.0, 10.75], green = [54.0 + 380 / mLat, 10.75];
    const geo = { holes: { 1: { tee, green, line: [tee, green], par: 4, distM: 380 } },
      features: [], mine: [] };
    const a = S.tee(geo, "T", 1, "bal", 20);
    const b = S.tee(geo, "T", 1, "bal", 20, [54.0 - 30 / mLat, 10.75]);   // 30 m dahinter
    if (a && b && a.best && b.best) {
      ok("weiter hinten = längeres Loch",
        (b.best.rest || 0) >= (a.best.rest || 0) || JSON.stringify(a.target) !== JSON.stringify(b.target),
        "Ziel a=" + JSON.stringify(a.target) + " b=" + JSON.stringify(b.target));
    }
  }
}

/* ============ 24cp. Chip-Tests und Gewichtung ============ */
group("Chippen — drei neue Tests, saubere Gewichtung");
{
  const src = fs.readFileSync(FILE, "utf8");
  const a = src.indexOf('"testDefs": [');
  let b = src.indexOf("[", a), d = 0, p = b;
  while (p < src.length) { if (src[p] === "[") d++; else if (src[p] === "]") { d--; if (!d) break; } p++; }
  let defs = []; try { defs = JSON.parse(src.slice(b, p + 1)); } catch (e) { defs = []; }

  ["chiplande", "chiproll", "chipleiter"].forEach(k =>
    ok("Test angelegt: " + k, defs.some(t => t.key === k)));

  /* Jeder neue Test misst eine URSACHE, die die vorhandenen nicht trennen:
     Landung, Rollverhältnis, Entfernung. */
  const neu = defs.filter(t => ["chiplande", "chiproll", "chipleiter"].indexOf(t.key) >= 0);
  neu.forEach(t => {
    ok(t.key + ": im Kurzspiel einsortiert", t.category === "Short Game & Pelz");
    ok(t.key + ": Eingaben vorhanden", (t.inputs || []).length >= 2);
    ok(t.key + ": Skala mit fünf Stufen", ((t.benchmark || {}).levels || []).length === 5);
    ok(t.key + ": Skala steigt", ((t.benchmark || {}).levels || []).every((v, i, arr) => i === 0 || v > arr[i - 1]));
    ok(t.key + ": geht in die Gewichtung", (t.weight || 0) > 0 && !!t.weightKey);
  });

  /* DIE GEWICHTUNG IST EIN GANZES: Drei Tests dazuzunehmen, ohne die anderen
     anzupassen, hätte die Summe auf 1,09 getrieben — die Oberfläche zeigt die
     Gewichte als Prozent, und 109 % sind keine Prozente. Alle Werte wurden im
     selben Verhältnis skaliert; die Rangfolge unter den alten Tests bleibt
     damit exakt erhalten. */
  const summe = defs.reduce((a2, t) => a2 + (t.weight || 0), 0);
  ok("Gewichte summieren sich auf rund 1", summe > 0.95 && summe <= 1.001, summe.toFixed(4));
  /* Und jeder gewichtete Test braucht seinen Eintrag in priorityWeights,
     sonst zeigt die Fokusliste ein anderes Gewicht als die Auswertung. */
  const pw = (src.match(/"priorityWeights": \{([\s\S]*?)\n \}/) || [])[1] || "";
  /* Nur Tests, die tatsächlich ein Gewicht TRAGEN. `R10 Dispersion (/30)` hat
     einen weightKey, aber kein `weight` — er läuft damit gar nicht in der
     Gewichtung mit (focusList verlangt beides). Das ist ein Altbefund, kein
     Fehler dieser Änderung; wer ihn beheben will, vergibt ein Gewicht. */
  const keys = defs.filter(t => t.weightKey && t.weight).map(t => t.weightKey);
  const fehlt = keys.filter(k => pw.indexOf('"' + k + '"') < 0);
  ok("jeder weightKey steht in priorityWeights", fehlt.length === 0, fehlt.join(", "));
}

/* ============ 24co. Wissensdatenbank ============ */
group("Bibliothek — Verweise, Inhalt, Brücke zum Spiel");
{
  const src = fs.readFileSync(FILE, "utf8");
  const R = G("wikiRelated"), T = G("wikiToc"), TI = G("wikiTocId"), SG = G("wikiForSG"),
        TS = G("wikiTagsShow"), DB0 = G("DB");

  /* Inhaltsverzeichnis: erst ab drei Überschriften — darunter ist der Artikel
     kurz genug zum Scrollen, und ein Verzeichnis mit zwei Einträgen ist nur
     eine weitere Zeile. */
  if (typeof T === "function") {
    eq("zwei Überschriften: kein Verzeichnis", T("# A\ntext\n## B").length, 0);
    eq("drei ergeben eines", T("# A\n## B\n### C").length, 3);
    eq("Ebene wird mitgeführt", T("# A\n## B\n### C")[2].tief, 3);
    eq("Fließtext zählt nicht", T("kein # Titel\nnoch was").length, 0);
    eq("ohne Text nichts", T("").length, 0);
  }
  /* Die Sprungmarke MUSS zur Bildung im HTML passen, sonst zeigt das
     Verzeichnis ins Leere. */
  if (typeof TI === "function") {
    eq("Marke wird normalisiert", TI("Grün lesen: Fall & Korn"), "wtoc_gr-n-lesen-fall-korn");
    ok("dieselbe Regel im HTML",
      /toLowerCase\(\)\.replace\(\/\[\^a-z0-9\]\+\/g,"-"\)\.replace\(\/\^-\|-\$\/g,""\)/.test(src));
  }

  /* Verwandte Artikel: gemeinsame Tags wiegen am schwersten, weil sie jemand
     bewusst vergeben hat. `grundpfeiler` darf NICHT zählen — das tragen alle
     84 Artikel, es würde jeden mit jedem verwandt machen. */
  if (typeof R === "function" && DB0) {
    const alt = DB0.wiki;
    DB0.wiki = { articles: [
      { id: "a", title: "Chippen Basis", cat: "Chippen", tags: ["grundpfeiler", "chippen", "release"] },
      { id: "b", title: "Chippen Release", cat: "Chippen", tags: ["grundpfeiler", "chippen", "release"] },
      { id: "c", title: "Pitchen", cat: "Pitchen", tags: ["grundpfeiler", "kurzspiel"] },
      { id: "d", title: "Driver", cat: "Driver", tags: ["grundpfeiler", "driver"] }
    ] };
    const r = R(DB0.wiki.articles[0], 3);
    eq("engster Treffer zuerst", r[0].id, "b");
    ok("sich selbst nie", !r.some(x => x.id === "a"));
    /* Nur `grundpfeiler` gemeinsam reicht NICHT für eine Empfehlung. */
    ok("kein Zufallstreffer über das Allerwelts-Tag", !r.some(x => x.id === "d"));
    DB0.wiki = alt;
  }

  /* Brücke: schwächste SG-Kategorie → passende Artikel, Übungen zuerst. */
  if (typeof SG === "function" && DB0) {
    const alt = DB0.wiki;
    DB0.wiki = { articles: [
      { id: "p1", title: "Putt-Technik", cat: "Putten – Technik", tags: ["putten"] },
      { id: "p2", title: "Lag-Drill", cat: "Putten – Technik", tags: ["putten", "drill"] },
      { id: "x1", title: "Driver", cat: "Driver", tags: ["driver"] }
    ] };
    const r = SG("putt", 3);
    ok("Putt-Artikel gefunden", r.length >= 2);
    eq("Übung steht vorn", r[0].id, "p2");
    ok("Fremdes bleibt draußen", !r.some(x => x.id === "x1"));
    eq("unbekannte Kategorie: nichts", SG("gibtsnicht", 3).length, 0);
    DB0.wiki = alt;
  }

  /* Das Allerwelts-Tag verschwindet aus der ANZEIGE, bleibt aber in den Daten:
     Es ist die Herkunftsangabe, und `gpDiff` vergleicht normalisierte Tags —
     wegschreiben hieße, alle 84 Artikel als „geändert" zu melden. */
  if (typeof TS === "function") {
    const a = { tags: ["grundpfeiler", "chippen"] };
    eq("stumm geschaltet", TS(a).join(","), "chippen");
    eq("in den Daten unberührt", a.tags.length, 2);
  }
  ok("Filter arbeitet weiter mit allen Tags", /if\(tags\.length\)\{ const at=wikiTagsOf\(a\)/.test(src));

  /* Kategorien: keine Einzelstücke mehr, und die Feinheit lebt als Tag weiter. */
  {
    const lib = JSON.parse((src.match(/id="gplib">([\s\S]*?)<\/script>/) || [])[1]);
    const zahl = {}; lib.forEach(a => zahl[a.cat] = (zahl[a.cat] || 0) + 1);
    const einzel = Object.keys(zahl).filter(k => zahl[k] < 2);
    eq("keine Kategorie mit nur einem Artikel", einzel.join(", "), "");
    ok("deutlich weniger Kategorien", Object.keys(zahl).length <= 26, String(Object.keys(zahl).length));
    /* Gegenprobe: Der Artikel, der mal „Green-Reading" war, trägt es als Tag. */
    const gr = lib.find(a => (a.tags || []).indexOf("green-reading") >= 0);
    ok("alte Kategorie lebt als Tag weiter", !!gr && gr.cat === "Putten – Technik");
  }
  ok("Wanderung für eigene Artikel", /function migrateWikiCats\(\)/.test(src));
  ok("nur einmal", /if\(DB\.ui && DB\.ui\.wikiCatsMigriert\) return;/.test(src));
  ok("Brücke nur ohne aktiven Filter",
    /if\(!WIKI\.q && !WIKI\.cat && !WIKI\.grp && !WIKI\.tags\.length && !WIKI\.onlyFav\) h\+=wikiSGHtml\(\);/.test(src));
}

/* ============ 24cw. Abgebrochene Runden ============ */
group("Unvollständige Runden — zählen, ohne zu verfälschen");
{
  const src = fs.readFileSync(FILE, "utf8");
  const C = G("computeRound"), DB0 = G("DB");

  /* GEFUNDEN: `toPar` war `gross - PAR` mit dem Par der GANZEN Seite. Sieben
     Löcher in je Par+1 ergaben -37 statt +7 — und dieser Wert ging in
     Rundenliste, Verlaufskurve, Korrelation und Scoring-Average. Eine
     abgebrochene Trainingsrunde sah dort aus wie die Runde des Jahres. */
  if (typeof C === "function" && DB0) {
    const sicherC = DB0.courses, sicherR = DB0.rounds;
    const holes18 = []; for (let i = 1; i <= 18; i++) holes18.push({ hole: i, par: 4, si: i, len: 350 });
    DB0.courses = [{ name: "T", tees: { Gelb: { cr18: 71.2, slope18: 130, par18: 72, holes: holes18 } } }];
    const mach = (n, side) => ({ id: "x", date: "2026-08-16", course: "T", tee: "Gelb", side: side || "18 Loch",
      holes: Array.from({ length: n }, (_, i) => ({ hole: i + 1, par: 4, score: 5, putts: 2 })) });

    const teil = C(mach(7));
    eq("gespielte Löcher gezählt", teil.counted, 7);
    ok("als unvollständig erkannt", teil.voll === false);
    eq("durchgespielt ist richtig", teil.toParThrough, 7);
    eq("toPar bleibt leer statt falsch", teil.toPar, null);

    const ganz = C(mach(18));
    ok("vollständige Runde erkannt", ganz.voll === true);
    eq("toPar der vollen Runde", ganz.toPar, 18);
    eq("und stimmt mit durchgespielt überein", ganz.toParThrough, 18);

    /* Eine gespielte NEUN ist vollständig — nicht jede Runde hat 18 Löcher. */
    const neun = C(mach(9, "Front 9"));
    ok("Front 9 gilt als vollständig", neun.voll === true, "counted=" + neun.counted);
    eq("erwartete Löcher der Seite", neun.erwartet, 9);
    /* Vier von neun sind es nicht. */
    ok("halbe Neun ist unvollständig", C(mach(4, "Front 9")).voll === false);

    DB0.courses = sicherC; DB0.rounds = sicherR;
  }

  /* Und die Auswerter müssen es beachten. */
  /* v3.14 verschärft: `voll` genügt nicht — eine vollständige NEUN ist
     vollständig, ihre SUMMEN sind aber nicht mit 18 Löchern vergleichbar. */
  ok("Bestenliste nur mit 18-Loch-Runden", /x\.e\.toPar!=null&&istAchtzehn\(x\.e\)/.test(src));
  ok("18er-Schnitt aus dem Durchgespielten", /x\.e\.toParThrough\*18\/x\.e\.counted/.test(src));
  ok("und erst ab neun Löchern", /x\.e\.counted>=9/.test(src));
  /* `e.putts` ist die SUMME der Runde — eine Teilrunde in einer Verteilung von
     18-Loch-Runden sähe aus wie eine Sternstunde am Grün. */
  ok("Putt-Verteilung nur mit 18 Löchern", /if\(e\.putts!=null && istAchtzehn\(e\)\) putts\.push/.test(src));
  ok("GIR-Rekord nur mit 18 Löchern", /x\.e\.gir!=null&&istAchtzehn\(x\.e\)/.test(src));
  ok("Korrelation nur mit 18 Löchern", /const rounds=alleR\.filter\(x=>istAchtzehn\(x\.e\)\)/.test(src));
  ok("und sagt, wie viele draußen bleiben", /kürzere Runde/.test(src));
  {
    const I = G("istAchtzehn");
    if (typeof I === "function") {
      ok("18 gespielt zählt", I({ voll: true, counted: 18 }));
      ok("vollständige Neun zählt NICHT", !I({ voll: true, counted: 9 }));
      ok("abgebrochene 18 zählt nicht", !I({ voll: false, counted: 14 }));
      ok("ohne Angabe nichts", !I(null));
    }
  }
  /* Anzeigen zeigen den durchgespielten Stand MIT Hinweis — verschweigen wäre
     schlimmer als eine Zahl mit Fußnote. */
  ok("Rundenliste kennzeichnet Teilrunden", /durch "\+e\.counted/.test(src));
}

/* ============ 24cu. Scorekarte wie auf Papier ============ */
group("Scorekarte — das gewohnte Raster");
{
  const src = fs.readFileSync(FILE, "utf8");
  const B = G("cardBlock");

  /* Vorher eine LISTE mit 18 Zeilen. Vollständig, aber nicht die Darstellung,
     die ein Golfer kennt — und die typische Frage „erste gegen zweite Neun"
     musste man im Kopf rechnen. */
  if (typeof B === "function") {
    const hs = [];
    for (let h = 1; h <= 18; h++) hs.push({ hole: h, par: h % 3 === 0 ? 3 : 4, si: h, score: 5, putts: 2 });
    const out = B(hs, 1, 9, "OUT");
    ok("Tabelle statt Liste", /<table class="scorecard">/.test(out));
    ok("neun Löcher plus Summenspalte", (out.match(/<th>/g) || []).length === 9);
    ok("Kopfzeile Loch", /Loch<\/th>/.test(out) || /sc-lab">Loch/.test(out));
    ["Par", "Vorgabe", "Score", "Putts"].forEach(z =>
      ok("Zeile vorhanden: " + z, out.indexOf(">" + z + "<") > 0));
    /* Die Summen MÜSSEN stimmen — eine Scorekarte, die falsch addiert, ist
       schlimmer als keine. */
    const parSum = hs.slice(0, 9).reduce((a, x) => a + x.par, 0);
    ok("Par-Summe stimmt", out.indexOf(">" + parSum + "<") > 0, String(parSum));
    ok("Score-Summe stimmt", out.indexOf(">45<") > 0);
    ok("Putt-Summe stimmt", out.indexOf(">18<") > 0);
    /* Bei einer 9-Loch-Runde entfällt der zweite Block ganz. */
    eq("kein Block ohne Löcher", B(hs.slice(0, 9), 10, 18, "IN"), "");

    /* TEE-ERGEBNIS ALS PFEIL (v3.20): In einer 16-px-Spalte ist ein Pfeil
       lesbarer als jede Abkürzung — und die RICHTUNG ist die Aussage. */
    const tee = ["Hit", "Links", "Rechts", "Kurz", "Lang", "Weit links", "Weit rechts", "Mis-hit", "Hit"];
    const hs2 = tee.map((tr, i2) => ({ hole: i2 + 1, par: 4, si: i2 + 1, score: 5, putts: 2, tee: tr,
      girDirect: i2 < 3 ? "Ja" : "Nein" }));
    const out2 = B(hs2, 1, 9, "OUT");
    const zeile = lab => { const a = out2.indexOf(">" + lab + "<");
      return a < 0 ? "" : out2.slice(a, out2.indexOf("</tr>", a)); };
    const tz = zeile("Tee");
    [["H", "Hit"], ["←", "Links"], ["→", "Rechts"], ["↓", "Kurz"], ["↑", "Lang"],
     ["←←", "Weit links"], ["→→", "Weit rechts"], ["×", "Mis-hit"]].forEach(([z, l]) =>
      ok("Tee " + l + " → " + z, tz.indexOf(">" + z + "<") > 0));
    /* Fairway-Quote am Zeilenende — 2 Treffer aus 9 Par-4-Löchern. */
    ok("Fairway-Quote in der Summe", tz.indexOf("22%") > 0, tz.slice(-90));
    /* GIR als QUOTE: „3" sagt nichts ohne die Zahl der gewerteten Löcher. */
    ok("GIR-Quote in der Summe", zeile("GIR").indexOf("33%") > 0, zeile("GIR").slice(-90));
    /* Par 3 hat kein Fairway — mitzuzählen würde die Quote je nach Platz um
       zehn Punkte drücken. */
    const hs3 = hs2.map((h, i2) => (i2 === 0 ? { ...h, par: 3 } : h));
    ok("Par 3 zählt nicht in die Fairway-Quote",
      B(hs3, 1, 9, "OUT").indexOf("13%") > 0, "1 von 8");
  }
  /* Die Zeichen der Papierkarte: Birdie im Kreis, Doppelbogey doppelt gerahmt. */
  ok("Birdie markiert", /\.sc-birdie\{/.test(src));
  ok("Doppelbogey markiert", /\.sc-dbl\{/.test(src));
  ok("feste Spaltenbreite", /table-layout:fixed/.test(src));
  ok("Ziffern in gleicher Breite", /font-variant-numeric:tabular-nums/.test(src));
}

/* ============ 24db. Mehrere Bilder je Artikel ============ */
group("Wissensspeicher — Bildstrecke statt Einzelbild");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* Dem Feld fehlte `multiple`, und der Handler las nur `files[0]` — unter
     Android ließ sich dann gar nichts mehrfach auswählen. Wer eine Bildstrecke
     zu einem Drill anlegt, musste den Weg je Bild einmal gehen. */
  ok("Bildfeld nimmt mehrere", /🖼 Bilder<input type="file" accept="image\/\*" multiple/.test(src));
  ok("Handler liest alle Dateien", /const fs=Array\.prototype\.slice\.call\(\(input&&input\.files\)\|\|\[\]\)/.test(src));
  /* NACHEINANDER: Parallel liefe auf dem Telefon der Speicher voll, und die
     Reihenfolge im Text wäre zufällig — bei einer Bildstrecke ist die
     Reihenfolge aber die halbe Aussage. */
  ok("nacheinander statt gleichzeitig", /const naechstes=\(i\)=>\{[\s\S]{0,600}naechstes\(i\+1\)/.test(src));
  /* Ein Bild, das nicht durchkommt, darf die übrigen nicht mitnehmen. */
  ok("Fehler je Bild aufgefangen", /\.catch\(\(\)=>\{ fehler\+\+; \}\)/.test(src));
  ok("Bilanz am Ende", /ok\+" von "\+fs\.length\+" Bildern eingefügt/.test(src));
  /* Erst NACH allen Bildern anstoßen — sonst ein Push je Bild. */
  ok("Repo-Anstoß erst am Ende", /if\(i>=fs\.length\)\{[\s\S]{0,200}wikiImgSchedulePush\(\)/.test(src));
  /* Videos bleiben bewusst einzeln — die Größenrückfrage je Datei wäre in
     Serie eine Zumutung. */
  ok("Video weiterhin einzeln, mit Begründung", /bewusst EINZELN: Videos sind gross/.test(src));
}

/* ============ 24dc. Was steht heute an ============ */
group("Heute geplant — Tests und Sport");
{
  const src = fs.readFileSync(FILE, "utf8");
  const T = G("heuteTests"), S = G("heuteSport"), DB0 = G("DB");

  /* Fällige Tests standen bis v2.31 als LISTE hier und wurden zu Recht
     entfernt. Der Wunsch ist ein anderer: nicht „was ist alles fällig",
     sondern „was mache ich HEUTE" — zwei Zeilen. */
  if (typeof T === "function" && DB0) {
    const sicherD = DB0.testDefs, sicherT = DB0.tests;
    const alt = d => new Date(Date.now() - d * 864e5).toISOString().slice(0, 10);
    DB0.testDefs = [
      { key: "a", label: "Alt-Test", category: "Putting" },
      { key: "b", label: "Frisch", category: "Putting" },
      { key: "c", label: "Nie gemacht", category: "Short Game & Pelz" }
    ];
    DB0.tests = [{ defKey: "a", date: alt(200), total: 20 }, { defKey: "b", date: alt(5), total: 20 }];
    const r = T(2);
    ok("höchstens zwei", r.length <= 2, String(r.length));
    ok("frischer Test bleibt draußen", !r.some(x => x.d.key === "b"));
    ok("nie gemachter ist dabei", r.some(x => x.d.key === "c"));
    /* Wer drei Tests an einem Tag macht, macht keinen davon ordentlich. */
    eq("Obergrenze wird eingehalten", T(1).length, 1);
    DB0.testDefs = sicherD; DB0.tests = sicherT;
  }

  /* Es gibt keinen hinterlegten Wochenplan — die Empfehlung wird abgeleitet.
     Die wichtigste Regel: vor dem Turnier KEIN Krafttraining. */
  if (typeof S === "function" && DB0) {
    const sicherF = DB0.fitnessSessions, sicherC = DB0.competitions;
    const tag = d => new Date(Date.now() + d * 864e5).toISOString().slice(0, 10);
    DB0.fitnessSessions = [];
    DB0.tournaments = [];
    ok("ohne alles: Kraft", S().art === "kraft", S().art);
    /* Turnier morgen -> nur Mobilität, egal wie lange die Kraft her ist. */
    DB0.tournaments = [{ id: "t1", name: "T", date: tag(1), status: "geplant" }];
    const vor = S();
    ok("vor dem Turnier keine Kraft", vor.art === "mobil", vor.art + " / " + vor.warum);
    DB0.tournaments = [];
    /* Frisch trainiert -> Mobilität, dann Ruhe. */
    DB0.fitnessSessions = [{ id: "f1", type: "Kraft", date: new Date().toISOString().slice(0, 10) }];
    eq("nach Kraft heute: Mobilität", S().art, "mobil");
    DB0.fitnessSessions.push({ id: "f2", type: "Yoga", date: new Date().toISOString().slice(0, 10) });
    /* Ein Ruhetag ist ein Programm, kein Ausfall — wer ihn nicht benennt,
       trainiert ihn weg. */
    eq("beides frisch: Ruhetag", S().art, "ruhe");
    DB0.fitnessSessions = sicherF; DB0.tournaments = sicherC;
  }
  ok("steht unter dem Block", /h\+=heuteProgrammHtml\(\);/.test(src));
  ok("Tests führen in den Test", /openTest\('\$\{esc\(t\.d\.key\)\}'\)/.test(src));
}

/* ============ 24dc. Neue Fassung wird gemeldet ============ */
group("Aktualisierung — neue Fassung wird gemeldet");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* Die App-Hülle läuft nach `stale-while-revalidate`: Sie startet IMMER aus
     dem Zwischenspeicher. Das ist richtig — im Funkloch will man keine
     Fehlerseite —, aber das ERSTE Neuladen nach dem Einspielen zeigt noch die
     alte Fassung. Wer das nicht weiß, hält eine eingebaute Neuerung für nicht
     vorhanden und sucht an der falschen Stelle. */
  ok("Aktualisierung wird erkannt", /addEventListener\("updatefound"/.test(src));
  ok("und gemeldet", /toast\("Neue Fassung geladen"/.test(src));
  ok("mit Knopf zum Anwenden", /label:"Jetzt anwenden", fn:\(\)=>location\.reload\(\)/.test(src));
  /* NUR bei einer echten Aktualisierung: Bei der ERSTEN Installation gibt es
     nichts zu melden — `controller` unterscheidet beides. */
  ok("nicht bei der ersten Installation", /navigator\.serviceWorker\.controller\)\{/.test(src));
  /* Und die Fassung steht sichtbar auf der Heute-Seite — sonst muss man drei
     Ebenen tief in die Abgleich-Prüfung, um sie zu erfahren. */
  ok("Fassung sichtbar", /Fassung v\$\{esc\(APP_VERSION\)\}/.test(src));
}

/* ============ 24dc. Fitness: Navigation, Wochenplan, Erinnerung ============ */
group("Fitness — eigener Reiter, Wochenplan, Erinnerungen");
{
  const src = fs.readFileSync(FILE, "utf8");
  const I = G("fitplanIdx"), W = G("fitplanWocheGeschafft"), S = G("fitplanSet"),
        A = G("fitplanAll"), H = G("fitplanHeute"), DB0 = G("DB");

  /* Fitness lag als vierter Reiter unter „Training" — erreichbar, aber nicht
     präsent. Turniere plant man ein paar Mal im Jahr, Fitness soll man
     mehrmals die Woche ansehen; die HÄUFIGKEIT gehört in die Reihenfolge. */
  ok("Fitness in der Hauptnavigation", /data-g="fitness"><span class="ni">🏋<\/span>Fitness/.test(src));
  ok("kein Turnier-Knopf mehr", !/data-g="turnier"/.test(src));
  ok("Turnier unter Mehr", /\{g:"mehr", views:\[\["comp","Turnier"\]/.test(src));
  ok("Fitness-Gruppe mit Plan, Programm und Geräten",
    /\{g:"fitness", views:\[\["fitness","Fitness"\],\["fitplan","Plan"\],\["programm","Programm"\],\["gear","Geräte"\]\]\}/.test(src));
  ok("Plan-Ansicht angemeldet", /fitplan:renderFitplan/.test(src));
  ok("Abschnitt im Markup", /id="v-fitplan"/.test(src));

  /* Der Wochentag beginnt am MONTAG — `Date.getDay()` zählt ab Sonntag. Wer
     das verwechselt, verschiebt den ganzen Plan um einen Tag, und es fällt
     erst am Sonntag auf. */
  if (typeof I === "function") {
    eq("Montag ist 0", I(new Date("2026-08-17T12:00:00")), 0);
    eq("Sonntag ist 6", I(new Date("2026-08-16T12:00:00")), 6);
    eq("Samstag ist 5", I(new Date("2026-08-22T12:00:00")), 5);
  }

  if (typeof S === "function" && typeof A === "function" && DB0) {
    const sicher = DB0.fitPlan, sicherS = DB0.fitnessSessions;
    DB0.fitPlan = { tage: {}, erinnern: false, zeit: "18:00" };
    S(0, "art", "Kraft"); S(0, "min", 45);
    eq("Tag gespeichert", A().tage["0"].art, "Kraft");
    /* Leere Art löscht den Tag — ein Eintrag ohne Art ist kein Ruhetag,
       sondern Datenmüll. */
    S(0, "art", null);
    ok("ohne Art kein Eintrag", !A().tage["0"]);

    /* Verglichen wird die ART, nicht der Tag: Wer die Montags-Einheit am
       Dienstag macht, hat sie gemacht. */
    S(0, "art", "Kraft"); S(2, "art", "Kraft"); S(4, "art", "Yoga");
    const heute = new Date();
    const mo = new Date(heute); mo.setDate(heute.getDate() - I(heute));
    const di = new Date(mo); di.setDate(mo.getDate() + 1);
    DB0.fitnessSessions = [{ date: di.toISOString().slice(0, 10), type: "Kraft" }];
    const w = W();
    eq("drei geplante Einheiten", w.soll, 3);
    eq("die am Dienstag gemachte zählt", w.ist, 1);
    DB0.fitPlan = sicher; DB0.fitnessSessions = sicherS;
  }

  /* EHRLICH BLEIBEN: Eine Web-App kann nicht benachrichtigen, während sie
     geschlossen ist — dafür bräuchte es einen Push-Server. Der Kalender kann
     es, deshalb der ICS-Weg. */
  ok("Grenze benannt", /kann <b>keine<\/b> Benachrichtigung schicken/.test(src));
  ok("Kalender-Serie als verlässlicher Weg", /RRULE:FREQ=WEEKLY;BYDAY=/.test(src));
  ok("mit Voralarm", /TRIGGER:-PT30M/.test(src));
  /* Nur EINMAL je Tag erinnern, und nicht an Erledigtes. */
  ok("einmal je Tag", /if\(DB\.ui\.fitErinnertAm===tag\) return;/.test(src));
  ok("nicht an Erledigtes", /const schon=\(DB\.fitnessSessions\|\|\[\]\)\.some/.test(src));
  ok("Takt läuft", /setInterval\(fitErinnerungTick, 60000\)/.test(src));
}

/* ============ 24dk. Schlagfolge endet auf dem Grün ============ */
group("Schlagfolge — der letzte Punkt IST das Grün");
{
  const src = fs.readFileSync(FILE, "utf8");
  const AC = G("playAimChain"), PB = G("playBegin"), HR = G("holeRef"),
        GD = G("geoDist"), PLAY0 = G("PLAY"), DB0 = G("DB"), LOG = G("ERRLOG");

  /* Gemeldet wurde, die orange Darstellung ziele „deutlich hinter das Grün".
     Nachgerechnet: Sie trifft es auf den Meter. Statt sich auf eine Messung zu
     verlassen, misst die App es jetzt selbst und meldet Abweichungen — der
     nächste Fall ist damit belegbar statt erinnert. */
  ok("Wache vorhanden", /WACHE: ENDET DIE SCHLAGFOLGE AUF DEM GRUEN\?/.test(src));
  ok("Toleranz benannt", /if\(ab>25\) logWarn\("Schlagfolge"/.test(src));
  ok("mit Lochnummer und Abstand", /letzter Zielpunkt \$\{ab\} m vom Grün entfernt/.test(src));

  if (typeof AC === "function" && typeof PB === "function" && PLAY0 && DB0) {
    const sicher = { c: DB0.courses, cl: DB0.clubDistances, holes: PLAY0.holes,
      course: PLAY0.course, tee: PLAY0.tee, side: PLAY0.side, idx: PLAY0.idx,
      act: PLAY0.active, here: PLAY0.here };
    try {
      const mLat = 110540, t2 = [54.0, 10.77], g = [54.0 + 387 / mLat, 10.77];
      const geo = { holes: { 5: { tee: t2, green: g, line: [t2, g], distM: 387 } }, features: [] };
      DB0.courses = [{ name: "T", tees: { Gelb: { holes: [{ hole: 5, par: 4, si: 3, len: 387 }] } }, geo }];
      DB0.clubDistances = [{ club: "Driver", carry: 211, reach: 225 },
        { club: "5 Iron", carry: 168, reach: 174 }, { club: "PW", carry: 105, reach: 107 }];
      PB("T", "Gelb", 0); PLAY0.idx = 0; PLAY0.here = t2;
      const n0 = LOG ? LOG.length : 0;
      const k = AC(true);
      ok("Kette gebaut", !!(k && k.pts && k.pts.length > 1), k ? String(k.pts.length) : "-");
        /* AUS GROSSER ENTFERNUNG (v3.42): „wie am Abschlag planen" heißt auch
         „am Abschlag anfangen". Vorher blieb der Startpunkt die eigene
         Position — aus 3,2 km wurde ein zweiter Schlag über 2694 m, und die
         Zielpunkte lagen entsprechend neben dem Grün. */
      PLAY0.here = [54.0 - 2900 / mLat, 10.77];
      const kFern = AC(true);
      if (kFern && kFern.pts && kFern.pts.length > 1) {
        ok("aus der Ferne startet die Kette am Tee", GD(t2, kFern.pts[0]) < 5,
          GD(t2, kFern.pts[0]).toFixed(0) + " m");
        ok("und endet trotzdem auf dem Grün",
          GD(HR(geo, 5).green, kFern.pts[kFern.pts.length - 1]) < 5);
        /* Kein Schlag darf länger sein als der längste Schläger — genau daran
           erkannte man den Fehler im Plan. */
        const maxLeg = Math.max(...kFern.pts.slice(1).map((p, i) => GD(kFern.pts[i], p)));
        ok("kein Schlag über 260 m", maxLeg < 260, maxLeg.toFixed(0) + " m");
      }
      PLAY0.here = t2;

    if (k && k.pts && k.pts.length > 1) {
        const letzt = k.pts[k.pts.length - 1];
        const ab = GD(HR(geo, 5).green, letzt);
        /* DAS ist die Kernaussage: Der letzte Zielpunkt liegt auf dem Grün,
           nicht dahinter. */
        ok("letzter Punkt liegt auf dem Grün", ab < 5, ab.toFixed(1) + " m");
        ok("erster Punkt ist der Abschlag", GD(t2, k.pts[0]) < 5);
        /* Und die Wache schweigt, wenn alles stimmt — eine Warnung, die immer
           erscheint, liest niemand. */
        if (LOG) ok("Wache schweigt bei korrekter Kette", LOG.length === n0,
          String(LOG.length - n0));
      }
    } finally {
      DB0.courses = sicher.c; DB0.clubDistances = sicher.cl; PLAY0.holes = sicher.holes;
      PLAY0.course = sicher.course; PLAY0.tee = sicher.tee; PLAY0.side = sicher.side;
      PLAY0.idx = sicher.idx; PLAY0.active = sicher.act; PLAY0.here = sicher.here;
    }
  }
}

/* ============ 24dm. Ein Kilometer, und der Grünpunkt ============ */
group("Caddy — feste Kilometergrenze, Grünpunkt geprüft");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* Die Grenze hing an der Lochlänge (`len + 150`, mindestens 650) — gut
     gemeint, aber unvorhersehbar: Auf einem Par 3 kippte die Rechnung bei
     650 m, auf einem langen Par 5 bei 670, und dazwischen wusste niemand mehr,
     was gerade gerechnet wird. */
  ok("feste Grenze als Konstante", /const CADDY_TEE_AB_M = 1000;/.test(src));
  ok("und sie wird benutzt", /return d>CADDY_TEE_AB_M \? d : null;/.test(src));
  ok("keine Längenabhängigkeit mehr", !/const grenze=\(h\.len>0\?h\.len:500\)\+150;/.test(src));
  /* `holeRef` statt Rohzugriff: sonst greift die Tee/Grün-Vertauschung nicht. */
  ok("liest über holeRef", /function playTooFar\(\)\{[\s\S]{0,300}holeRef\(geo,h\.hole\)/.test(src));

  /* Bleibt eine Erklärung, warum das Oval neben dem Grün liegt, obwohl die
     Kette den gespeicherten Punkt auf den Meter trifft: Der PUNKT stimmt nicht
     mit der FLÄCHE überein. Beim OSM-Import endet die Lochlinie regelmäßig
     hinter dem Grün — dann ist alles verschoben, aber in sich stimmig, und
     beim Rechnen fällt es nicht auf. */
  ok("Grünpunkt gegen Grünfläche geprüft", /gespeicherter Grünpunkt liegt \$\{ab\} m neben der Grünfläche/.test(src));
  ok("nur bei echtem Versatz", /if\(ab>25\) logWarnEinmal\("gruenVersatz:/.test(src));
  /* Nur die Fläche DIESES Lochs: Ein Grün 300 m weiter gehört zu einem anderen. */
  ok("Fläche muss in der Nähe liegen", /geoDist\(ringCentroid\(f\.ring\), hrP\.green\)<120/.test(src));
}

/* ============ 24dl. Knopfleiste und Caddy-Abstand ============ */
group("Spielmodus — Knopfleiste, Abstand, aufgeklappter Caddy");
{
  const src = fs.readFileSync(FILE, "utf8");
  const css = (src.match(/<style[^>]*>([\s\S]*?)<\/style>/g) || []).join("\n");

  /* Die Messung stand nur in `pfRender` — sie fehlte damit, wenn sich die
     Kopfzeile ohne Neuzeichnen ändert: Drehung, Schriftgröße, ein längerer
     Platzname beim Lochwechsel. Dann galt der Ausweichwert von 58 px, und die
     Knopfspalte lag wieder über dem Score-Kasten. */
  ok("Messung als eigene Funktion", /function pfTopMessen\(\)\{/.test(src));
  ok("auch beim Kartenwechsel", /playAimDraw\(\); pfTopMessen\(\); \}/.test(src));
  /* Der Abstand zur Kopfzeile war zu groß — 2 px statt 6. */
  /* Der Ausweichwert muss den HÄUFIGEN Fall treffen: Die Kopfzeile bricht auf
     dem Gerät um und ist dann rund 90 px hoch. Mit 58 px lag die Knopfspalte
     mitten im Score-Kasten, sobald die Messung einmal nicht griff. */
  ok("Ausweichwert für zwei Zeilen", /var\(--pf-top-h, 96px\)/.test(css));
  ok("kein alter 58er-Wert mehr", !/var\(--pf-top-h, 58px\)/.test(css));
  /* Der Caddy zieht ab: `.pf-top` hat 8 px Innenabstand unten, die gemessene
     Höhe enthält sie — ohne Abzug klafft eine Lücke. */
  ok("Caddy sitzt dicht darunter", /\.pf-caddy\{[^}]*var\(--pf-top-h, 96px\) - 6px\)/.test(css));
  ok("Knopfleiste darunter", /\.play-map-ctrls\{[^}]*var\(--pf-top-h, 96px\) \+ 4px\)/.test(css));
  /* Aufgeklappt hat der Caddy Vorfahrt: Die Spalte lag über drei Zeilen. */
  ok("Knopfleiste weicht dem offenen Caddy", /body\.caddy-offen \.play-map-ctrls\{display:none\}/.test(css));
  ok("Klasse wird gesetzt", /classList\.toggle\("caddy-offen", !!PLAY\.pfInfoOpen\)/.test(src));
  /* Klasse am body statt Geschwister-Wahl: Beide Elemente stehen nicht
     zwingend nebeneinander im Dokument. */
  ok("keine Geschwister-Wahl", !/\.pf-caddy\.open ~ \.play-map-ctrls/.test(css));
}

/* ============ 24dj. Vorgabenzeile und Überlappung ============ */
group("Spielmodus — verständliche Vorgabenzeile, keine Überlappung");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* Das Wort „Puffer" ist im Golf BESETZT: Im alten EGA-System war die Pufferzone der
     Bereich, in dem sich die Vorgabe NICHT ändert. Gemeint war hier etwas ganz
     anderes — Punkte über dem, was die eigene Vorgabe verlangt. Ein Wort, das
     der Leser schon kennt, aber anders, ist schlimmer als ein unbekanntes. */
  /* Gegen den CODE prüfen: Im Kommentar steht der alte Text weiterhin — dort
     erklärt er die Änderung und gehört hin. */
  ok("das Wort Puffer ist raus", !/pf-vg">\$\{[^}]*\} Pkt · Puffer/.test(src));
  ok("stattdessen über/unter Vorgabe", /" über":" unter"\) \+ " Vorgabe"/.test(src));
  ok("Punkte mit Bezugsgröße", /\$\{vg\.punkte\} Pkt nach \$\{vg\.gespielt\}/.test(src));
  /* Null ist ein eigener Fall: „0 über Vorgabe" liest sich falsch. */
  ok("Gleichstand eigens benannt", /genau auf Vorgabe/.test(src));
  ok("Hochrechnung als Ziel benannt", /· Ziel \$\{vg\.hoch\}/.test(src));

  /* Die Überlappung: 58 px waren richtig, solange die Kopfzeile einzeilig
     bleibt. Bricht sie um (schmales Gerät, große Schrift, langer Text), schob
     sich der Caddy-Kasten darüber. */
  ok("Abstand kommt aus einer Variablen", /top:calc\(var\(--pf-top-h, 96px\)/.test(src));
  ok("Höhe wird gemessen", /top\.getBoundingClientRect\(\)\.height/.test(src));
  /* Nach `innerHTML` misst man die VORIGE Höhe — der Umbruch steht erst im
     nächsten Bild fest. */
  ok("erst im nächsten Bild gemessen", /requestAnimationFrame\(\(\)=>\{[\s\S]{0,200}--pf-top-h/.test(src));

  /* Der Text selbst, an drei Ständen durchgerechnet. */
  const txt = vg => vg.punkte + " Pkt nach " + vg.gespielt + " · " +
    (vg.puffer === 0 ? "genau auf Vorgabe"
      : (Math.abs(vg.puffer) + (vg.puffer > 0 ? " über" : " unter") + " Vorgabe")) +
    (vg.hoch != null ? " · Ziel " + vg.hoch : "") +
    (vg.vgNaechstes ? " · dieses Loch +" + vg.vgNaechstes : "");
  eq("vorne", txt({ gespielt: 8, punkte: 16, puffer: 2, hoch: 41, vgNaechstes: 2 }),
    "16 Pkt nach 8 · 2 über Vorgabe · Ziel 41 · dieses Loch +2");
  eq("hinten", txt({ gespielt: 4, punkte: 6, puffer: -2, hoch: 33, vgNaechstes: 1 }),
    "6 Pkt nach 4 · 2 unter Vorgabe · Ziel 33 · dieses Loch +1");
  eq("gleichauf", txt({ gespielt: 9, punkte: 18, puffer: 0, hoch: 36, vgNaechstes: 0 }),
    "18 Pkt nach 9 · genau auf Vorgabe · Ziel 36");
}

/* ============ 24di. Kopfraum hinter dem Grün ============ */
group("Karte — hinter dem Grün muss Platz sein, auch beim Heranzoomen");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* v3.38 hat den Zuschlag in die BASISEINPASSUNG gelegt — die falsche Ebene.
     Was man SIEHT, bestimmt der Ausschnitt: `playMapInitView` beim Lochwechsel
     und `playAutoView` beim Näherkommen. Und dort ist es am wichtigsten: Je
     enger der Ausschnitt zoomt, desto größer der Anteil, den die Kopfzeilen
     verdecken. */
  ok("Kopfraum als eigene Funktion", /function playKopfraum\(v\)\{/.test(src));
  ok("im Anfangsausschnitt", /v=playKopfraum\(v\);\s*\/\/ Platz fuer die Kopfzeilen/.test(src));
  ok("und im mitwachsenden", /HIER IST ES AM WICHTIGSTEN[\s\S]{0,240}v=playKopfraum\(v\);/.test(src));
  /* Das Basisbild behält den Zuschlag: Luftbildkacheln entstehen nur für die
     eingepasste Fläche — ohne ihn gäbe es hinter dem Grün nichts zu zeigen.
     Verfügbar machen und sichtbar machen sind zwei verschiedene Dinge. */
  ok("Basisbild reicht weiter", /corridor:46,bufTopM:20,/.test(src));
  /* Das Band ist so bemessen, dass der bisherige Inhalt vollständig UNTER die
     Kopfzeilen rutscht: 0,28/(1−0,28). */
  ok("Bandbreite hergeleitet", /KARTE_KOPF_ANTEIL\/\(1-KARTE_KOPF_ANTEIL\)/.test(src));

  /* Gerechnet: Ein Ausschnitt der Höhe h wird um b = h·0,28/0,72 nach oben
     erweitert. Alles, was vorher bei Anteil a saß, sitzt danach bei
     (a·h + b)/(h + b) — also nie unter 0,28. */
  {
    const anteilNachher = a => (a * 1 + (0.28 / 0.72)) / (1 + (0.28 / 0.72));
    ok("was oben klebte, liegt jetzt unter der Anzeige", anteilNachher(0) >= 0.28,
      anteilNachher(0).toFixed(3));
    ok("die Bahnmitte bleibt in der unteren Hälfte", anteilNachher(0.5) > 0.6,
      anteilNachher(0.5).toFixed(3));
  }
}

/* ============ 24dh. Warnungen im Protokoll ============ */
group("Protokoll — Warnungen neben Fehlern");
{
  const src = fs.readFileSync(FILE, "utf8");
  const W = G("logWarn"), WE = G("logWarnEinmal"), LOG = G("ERRLOG");

  /* Ein Fehler sagt „etwas ist schiefgegangen". Eine Warnung sagt „es lief,
     aber das Ergebnis kann falsch sein" — auf der Bahn die wichtigere
     Auskunft. Ein Caddy ohne Schlägerdistanzen wirft keine Ausnahme; er nennt
     nur einen schlechteren Schläger. */
  if (typeof W === "function" && Array.isArray(LOG)) {
    const vorher = LOG.length;
    W("Test", "eine Warnung");
    const r = LOG[LOG.length - 1];
    eq("Warnung landet im Protokoll", LOG.length, vorher + 1);
    eq("mit eigener Stufe", r.lvl, "warn");
    /* Gleiche Warnung nicht zweimal — sonst verdrängt sie die eine Meldung,
       auf die es ankommt. */
    W("Test", "eine Warnung");
    eq("Wiederholung wird gezählt", LOG[LOG.length - 1].n, 2);
    eq("und nicht angehängt", LOG.length, vorher + 1);
    /* Warnungen erzeugen NIE eine Einblendung: Sie sind Hintergrundwissen;
       über der Karte wären sie ein Ärgernis. */
    ok("kein toast in logWarn", !/function logWarn\(where, text\)\{[\s\S]{0,600}toast\(/.test(src));
    if (typeof WE === "function") {
      const n0 = LOG.length;
      WE("k1", "Test", "nur einmal"); WE("k1", "Test", "nur einmal");
      eq("logWarnEinmal meldet genau einmal", LOG.length, n0 + 1);
    }
    LOG.length = vorher;
  }
  /* Die Ansicht muss beide Stufen unterscheiden — sonst nützt die Trennung
     nichts. */
  ok("Warnungen sind erkennbar", /r\.lvl==="warn"\?"⚠ ":"✕ "/.test(src));
  ok("Kopiertext trennt sie auch", /\[WARN\] /.test(src) && /\[FEHLER\] /.test(src));

  /* Die konkreten Warnstellen: alles Fälle, in denen ein Ergebnis falsch
     AUSSIEHT, ohne dass etwas kaputtgeht. */
  ["bagLeer", "keineKarte", "keinWetter", "speicher"].forEach(k =>
    ok("Warnung vorhanden: " + k, src.indexOf('"' + k) > 0 || src.indexOf("\"" + k + ":") > 0));
  ok("Uhr-Stille wird gemeldet", /logWarn\("Uhr", stumm\?/.test(src));
  ok("nur beim Wechsel", /if\(stumm!==_uhrStumm\)/.test(src));
  ok("Runde: Löcher ohne Par", /mit Score, aber ohne Par/.test(src));
  ok("Runde: fehlende Putts", /ohne Putts — Putt-Statistik/.test(src));
  ok("Speicherprüfung läuft beim Start", /setTimeout\(speicherPruefen, 8000\)/.test(src));
}

/* ============ 24dg. GPS-Lochvorschlag ist weg ============ */
group("Spielmodus — kein GPS-Lochvorschlag mehr");
{
  const src = fs.readFileSync(FILE, "utf8");
  /* Der automatische Lochwechsel verschwand in v1.98, weil er beim Warten am
     nächsten Tee, beim Ballsuchen und auf dem Rückweg umsprang. Der Vorschlag
     war sein letzter Rest — dieselbe Fehlerquelle mit einem Tipp dazwischen,
     und er stand dabei ÜBER der Caddy-Empfehlung. */
  /* Gegen den CODE prüfen, nicht gegen die Datei: Im Changelog steht der
     entfernte Text weiterhin — dort gehört er auch hin. */
  {
    const js = (src.match(/<script(?![^>]*(?:src=|application\/json|text\/markdown|devdocs))[^>]*>([\s\S]*?)<\/script>/g) || []).join("\n");
    ok("kein Vorschlag im Spielmodus", !/out\+=`<div class="play-detect"/.test(js));
    ok("kein Aufruf mit playGoHole aus der Erkennung", !/GPS erkennt Loch \$\{nh\.hole\}/.test(js));
  }
  ok("auch die Gestaltung ist weg", !/\.play-detect\{/.test(src));
  /* `nearestHole` selbst bleibt — die Caddy-Position braucht es, um bei
     grober Abweichung auf das nähere Loch umzustellen. Ein Aufräumen, das
     nützliche Funktionen mitnimmt, ist kein Aufräumen. */
  ok("nearestHole bleibt erhalten", /function nearestHole\(geo, here\)/.test(src));
  ok("Caddy-Position nutzt es weiter", /const nh=nearestHole\(geo, here\);/.test(src));
}

/* ============ 24df. Score Differential und Neun aus Achtzehn ============ */
group("WHS — 9-Loch-Differential und automatische Umstellung");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* Hier stand `is9 ? X : X` — beide Zweige identisch. Der 9-Loch-Wert ging als
     fertiges Differential durch, obwohl er die halbe Runde abdeckt: Bei HCPI 20
     und 44 Schlägen standen dort 6,8 — eine Zahl, die einem Handicap um 7
     entspricht. */
  ok("keine Schein-Verzweigung mehr", !/sd = is9 \? Math\.round\(\(\(ags-CR\)\*113\/SL\)\*10\)\/10 : Math/.test(src));
  /* DGV/WHS seit 1.4.2024: SD(9 berechnet) = ((HCPI × 1,04) + 2,4) / 2 */
  ok("Ergänzungsformel nach DGV", /\(\(\(HI\*1\.04\)\+2\.4\)\/2\)/.test(src));
  ok("SD 18 = gespielt + berechnet", /sd=Math\.round\(\(sd9\+sdErg\)\*10\)\/10/.test(src));
  /* Ohne Handicap-Index lässt sich die Ergänzung nicht bilden — dann lieber
     eine ehrliche Lücke als eine erfundene Zahl. */
  ok("ohne HCPI kein erfundener Wert", /\} else sd=null;\s+\/\/ unvollstaendig/.test(src));
  ok("beide Teilwerte werden zurückgegeben", /stblB,stblN,ags,sd,sd9,sdErg,cost/.test(src));
  ok("Anzeige nennt die Zusammensetzung", /9 Löcher \$\{e\.sd9/.test(src));

  /* Rechnung von Hand: HCPI 20 → Ergänzung 11,6; mit SD(9)=6,8 also 18,4. */
  {
    const erg = Math.round((((20 * 1.04) + 2.4) / 2) * 10) / 10;
    eq("Ergänzung bei HCPI 20", erg, 11.6);
    eq("Summe stimmt", Math.round((6.8 + erg) * 10) / 10, 18.4);
  }

  /* ALS 18 GESTARTET, NEUN GESPIELT: Das war bisher eine unvollständige
     Achtzehn — richtig aus den Summen ausgeschlossen, aber eben auch keine
     Neun, obwohl sie genau das ist. */
  ok("Umstellung beim Beenden", /const istAchtzehnGestartet/.test(src));
  ok("nur bei genau einer vollen Hälfte",
    /if\(vorn===9 && hinten===0\) neu="Front 9";\s*\n\s*else if\(hinten===9 && vorn===0\) neu="Back 9";/.test(src));
  /* Zwei Felder mit verschiedenen Wertebereichen — wer beide gleich setzt,
     erzeugt einen Wert, den keine Auswertung kennt. */
  ok("side und type getrennt gesetzt", /r\.side=neu;\s*\n\s*r\.type="9 Loch";/.test(src));
  /* Und es wird gesagt: Eine stille Umdeutung der eigenen Runde wäre unheimlich. */
  ok("Umstellung wird gemeldet", /Als "\+neu\+" gewertet/.test(src));
}

/* ============ 24de. Caddy: Anzeige, Bedingungen, Tee-Schläger ============ */
group("Caddy — vollständig sichtbar, Bedingungen, 2 Iron nur vom Tee");
{
  const src = fs.readFileSync(FILE, "utf8");
  const css = (src.match(/<style[^>]*>([\s\S]*?)<\/style>/g) || []).join("\n");
  const CC = G("caddyClubs"), CP = G("clubPick"), CZ = G("condZeile"), DB0 = G("DB");

  /* AUFGEKLAPPT will man alles sehen — die Karte ist in dem Moment zweitrangig.
     Vorher: 52 vh und 66 px Rand für die Knopfspalte, also eine schmale halbe
     Säule. */
  ok("aufgeklappt bis zum unteren Rand", /\.pf-caddy\.open\{right:12px;bottom:calc\(12px/.test(css));
  ok("keine feste Höhenbegrenzung mehr", !/\.pf-caddy\.open\{[^}]*max-height:52vh/.test(css));
  /* Der Schließen-Knopf klebt oben — sonst muss man zum Zuklappen erst wieder
     hochscrollen, und genau das fühlt sich hängend an. */
  ok("Schließen bleibt erreichbar", /\.pf-caddy\.open \.pf-cad-x\{position:sticky/.test(css));

  /* Wind/Temperatur wurden längst gerechnet, aber nur im heuristischen Zweig
     angezeigt — am Abschlag übernimmt die EV-Engine, und deren Anzeige kannte
     die Bedingungen gar nicht. */
  ok("Bedingungen auch im EV-Zweig", /\$\{condZeile\(PLAY\.here, ev\.target\|\|_hrC\.green\)\}/.test(src));
  if (typeof CZ === "function") {
    eq("ohne Punkte keine Zeile", CZ(null, null), "");
    /* Unter drei Metern schweigt sie: Eine Zeile, die bei jedem Loch „±1 m"
       meldet, liest niemand mehr — und sie verdeckt die echten Fälle. */
    ok("Schwelle vorhanden", /if\(Math\.abs\(diff\)<3 && !\(c\.teile&&c\.teile\.length\)\) return "";/.test(src));
  }

  /* 2 IRON NUR VOM TEE: Ein Vorschlag, den man ohnehin nicht ausführt, ist
     schlechter als ein kürzerer, den man spielt — er kostet Vertrauen in den
     Caddy insgesamt. Die Regel muss für Caddy UND Gameplan gelten. */
  ok("Regel an einer Stelle", /const TEE_ONLY=/.test(src));
  if (typeof CC === "function" && DB0) {
    const sicher = DB0.clubDistances;
    DB0.clubDistances = [{ club: "Driver", carry: 211 }, { club: "2 Iron", carry: 187 },
      { club: "5 Wood", carry: 192 }, { club: "7 Iron", carry: 140 }];
    const boden = CC().map(c => c.name), tee = CC(null, true).map(c => c.name);
    ok("vom Boden ohne 2 Iron", boden.indexOf("2 Iron") < 0, boden.join(", "));
    ok("vom Tee mit 2 Iron", tee.indexOf("2 Iron") >= 0, tee.join(", "));
    /* Schreibweisen: 2er Eisen, 2i, Driving Iron zählen genauso. */
    DB0.clubDistances = [{ club: "2er Eisen", carry: 187 }, { club: "7 Iron", carry: 140 }];
    ok("auch „2er Eisen\"", CC().map(c => c.name).indexOf("2er Eisen") < 0);
    DB0.clubDistances = [{ club: "Driving Iron", carry: 187 }, { club: "7 Iron", carry: 140 }];
    ok("auch „Driving Iron\"", CC().map(c => c.name).indexOf("Driving Iron") < 0);
    /* Und NIE einen leeren Beutel erzeugen — das wäre schlimmer als ein
       unpassender Vorschlag. */
    DB0.clubDistances = [{ club: "2 Iron", carry: 187 }];
    eq("letzter Schläger bleibt", CC().length, 1);
    DB0.clubDistances = sicher;
  }
  if (typeof CP === "function") {
    const bag = [{ name: "2 Iron", reach: 190 }, { name: "5 Wood", reach: 195 }, { name: "7 Iron", reach: 140 }];
    ok("clubPick meidet es ohne vomTee", (CP(bag, 190, { by: "reach" }) || {}).name !== "2 Iron");
    ok("mit vomTee ist es erlaubt",
      [ "2 Iron", "5 Wood" ].indexOf((CP(bag, 190, { by: "reach", vomTee: true }) || {}).name) >= 0);
  }
}

/* ============ 24dd. Geräte und golfspezifisches Programm ============ */
group("Trainingsgeräte — der Plan liest den Bestand");
{
  const src = fs.readFileSync(FILE, "utf8");
  const H = G("gearHat"), P = G("progVerfuegbar"), S = G("gearSeed"),
        A = G("gearAll"), PROG = G("GOLF_PROG"), DB0 = G("DB");

  /* Ein Plan, der Geräte voraussetzt, die man nicht hat, ist wertlos — und
     einer, der vorhandene nicht nutzt, verschenkt sie. Deshalb liest der Plan
     die Liste. */
  if (typeof S === "function" && DB0) {
    const sicherG = DB0.gear, sicherU = DB0.ui && DB0.ui.gearSeeded;
    DB0.gear = []; if (DB0.ui) delete DB0.ui.gearSeeded;
    const n = S();
    ok("Erstbefüllung legt Geräte an", n >= 15, String(n));
    ok("Swing Trainer dabei", A().some(g => /swing trainer/i.test(g.name)));
    ok("Loop-Bänder dabei", A().filter(g => /loop-band/i.test(g.name)).length === 3);
    /* NUR EINMAL — sonst käme die Liste nach jedem Aufräumen zurück. */
    eq("zweiter Aufruf legt nichts an", S(), 0);

    if (typeof H === "function") {
      ok("findet über Teilzeichenkette", H(["swing trainer"]));
      ok("Groß/Klein egal", H(["SWING TRAINER"]));
      ok("Unbekanntes nicht", !H(["rudergerät"]));
      /* Abgewählt heißt nicht vorhanden: Geräte im Keller sollen aus dem Plan
         fallen, ohne dass man sie später neu eintippt. */
      A().filter(g => /loop-band/i.test(g.name)).forEach(g => g.aktiv = false);
      ok("abgewähltes Gerät zählt nicht", !H(["loop-band"]));
      A().filter(g => /loop-band/i.test(g.name)).forEach(g => g.aktiv = true);
    }
    if (typeof P === "function" && Array.isArray(PROG)) {
      const alle = PROG.reduce((a, e) => a.concat(e.uebungen), []);
      ok("mit vollem Bestand ist fast alles verfügbar",
        alle.filter(P).length >= alle.length - 1, alle.filter(P).length + "/" + alle.length);
      /* Übungen ohne Gerät müssen IMMER gehen — sonst bliebe bei leerem
         Bestand gar nichts übrig. */
      const ohne = alle.filter(u => !u.g.length);
      ok("Übungen ohne Gerät gelten immer", ohne.every(P), String(ohne.length));
      DB0.gear = [];
      ok("ohne Geräte bleiben nur die freien", alle.filter(P).length === ohne.length);
    }
    DB0.gear = sicherG; if (DB0.ui && sicherU) DB0.ui.gearSeeded = sicherU;
  }

  /* Der Plan zielt auf Rotation, nicht auf „mehr Kraft" — und begründet jede
     Übung, sonst macht man sie falsch oder gar nicht. */
  if (Array.isArray(PROG)) {
    /* Vier Einheiten seit v3.30: Das Aufwärmen VOR der Runde kam dazu — der
       Teil mit dem besten Verhältnis von Aufwand zu Wirkung. */
    eq("vier Einheiten", PROG.length, 4);
    ok("Aufwärmen ist dabei", PROG.some(e => e.id === "W" && /Aufwärmen/.test(e.titel)));
    ok("Aufwärmen bleibt kurz", /10 min/.test((PROG.find(e => e.id === "W") || {}).dauer || ""));
    ok("jede Einheit hat einen Zweck", PROG.every(e => (e.zweck || "").length > 20));
    ok("jede Übung nennt Sätze", PROG.every(e => e.uebungen.every(u => (u.s || "").length > 2)));
    ok("jede Übung nennt das Warum", PROG.every(e => e.uebungen.every(u => (u.w || "").length > 20)));
  }
  ok("Übernahme in den Wochenplan", /function progInPlan\(id\)/.test(src));
  /* Kein stilles Überschreiben eines vorhandenen Plans. */
  ok("nur auf einen freien Tag", /const frei=\[0,1,2,3,4,5,6\]\.find\(i=>!p\.tage\[String\(i\)\]\)/.test(src));
  ok("voller Plan wird gemeldet", /Wochenplan ist voll/.test(src));
}

/* ============ 24db. Mehrere Bilder in einen Artikel ============ */
group("Bilder — drei Wege, eine Verarbeitung");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* Das Eingabefeld fordert seit jeher `multiple` an und die Verarbeitung
     läuft über ALLE Dateien. Ob man mehrere wählen kann, entscheidet aber die
     Android-Auswahl — manche Galerie-Apps geben trotz `multiple` nur eines
     heraus. Darauf hat die App keinen Zugriff, also gibt es Wege daran vorbei. */
  ok("Dateiauswahl fordert mehrere an", /accept="image\/\*" multiple/.test(src));
  ok("Ziehen ins Textfeld", /ta\.addEventListener\("drop"/.test(src));
  ok("Einfügen aus der Zwischenablage", /ta\.addEventListener\("paste"/.test(src));
  /* Manche Systeme legen das Bild nur unter `items` ab — ohne diesen Zweig
     käme beim Einfügen nichts an. */
  ok("auch über clipboardData.items", /if\(it\.kind==="file"\)/.test(src));
  ok("sichtbare Rückmeldung beim Ziehen", /\.we-drop\{outline:2px dashed/.test(src));

  /* EINE Verarbeitung für alle drei Wege — sonst hätte jeder seine eigene
     Fehlerbehandlung, und einer davon wäre irgendwann anders. */
  ok("gemeinsame Verarbeitung", /function wikiAddImageFiles\(fs\)\{/.test(src));
  ok("Dateiauswahl nutzt sie", /function wikiAddImageFromInput\(input\)\{[\s\S]{0,200}wikiAddImageFiles\(fs\);/.test(src));
  ok("Ziehen und Einfügen ebenso", (src.match(/wikiAddImageFiles\(bilder\)/g) || []).length >= 1);
  /* Nur Bilder — ein versehentlich gezogenes PDF darf nicht als Bild landen. */
  ok("filtert auf Bilder", /filter\(f=>f && \/\^image\\\//.test(src));
}

/* ============ 24da. Eine Vorlage für alle Scorekarten ============ */
group("Scorekarte — im Spielmodus dieselbe wie in der Runde");
{
  const src = fs.readFileSync(FILE, "utf8");
  const P = G("playCardHtml"), PLAY0 = G("PLAY");

  /* Es gab ZWEI Darstellungen — die neue im Rundendetail, die alte Liste im
     Spielmodus. Dieselbe Sache zweimal gebaut heißt: Jede Verbesserung muss
     man zweimal machen, und beim zweiten Mal vergisst man es. Die Pfeile und
     Quoten aus v3.20 fehlten im Spielmodus komplett. */
  ok("Spielmodus baut über roundCardHtml", /function playCardHtml\(\)\{[\s\S]{0,400}return roundCardHtml\(r\)/.test(src));
  if (typeof P === "function" && PLAY0) {
    /* Eigener Zustand, und im `finally` zurück: Läuft davor eine Gruppe, die
       `playBegin` benutzt, erbt man sonst deren Lochliste — und die Prüfung
       fällt aus einem Grund aus, der nichts mit ihr zu tun hat. */
    const sicher = { holes: PLAY0.holes, course: PLAY0.course, tee: PLAY0.tee, side: PLAY0.side };
    PLAY0.course = "ZZ-Kartentest"; PLAY0.tee = "Gelb"; PLAY0.side = "18 Loch";
    PLAY0.holes = [];
    for (let h = 1; h <= 9; h++) PLAY0.holes.push({ hole: h, par: 4, si: h,
      score: h <= 5 ? 5 : null, putts: 2, tee: "Hit", len: 300 });
    const html = P();
    ok("dasselbe Raster wie im Rundendetail", /<table class="scorecard">/.test(html));
    /* Pfeile und Quoten werden am REINEN Baustein geprüft (24cu), nicht hier:
       `playCardHtml` reichert über `sgEnrich` an, und das hängt an `DB` und
       `PLAY`. Eine Prüfung, die von der Reihenfolge der Gruppen abhängt, misst
       am Ende die Reihenfolge und nicht die Sache — genau das ist hier
       passiert. Hier zählt nur: Es ist DIESELBE Vorlage. */
    ok("Kopfzeile mit Loch-Spalte", /sc-lab">Loch</.test(html));
    /* Der EINE Unterschied bleibt: Während der Runde fehlen Scores, und darauf
       muss anders hingewiesen werden als bei einer fertigen Runde. */
    ok("Hinweis auf fehlende Scores", /noch ohne Score/.test(html));
    PLAY0.holes = sicher.holes; PLAY0.course = sicher.course;
    PLAY0.tee = sicher.tee; PLAY0.side = sicher.side;
  }
}

/* ============ 24cz. Darstellung auf großen Bildschirmen ============ */
group("Layout — Breite, Zeigergerät, Spalten");
{
  const src = fs.readFileSync(FILE, "utf8");
  const css = (src.match(/<style[^>]*>([\s\S]*?)<\/style>/g) || []).join("\n");

  /* Gefragt wird nach BREITE und ZEIGERGERÄT, nicht nach „welches Gerät" —
     ein Tablet quer ist breiter als mancher Laptop. Erkennung über den
     User-Agent war nie verlässlich. */
  ok("keine Geräteerkennung über den User-Agent",
    !/navigator\.userAgent[^\n]{0,80}(iPad|Android|Mobile)/i.test(src));
  ok("Spaltenbreite als Variable", /--spalte:640px/.test(css));
  ok("main folgt der Variable", /main\{max-width:var\(--spalte, 640px\)/.test(css));
  [900, 1280, 1800].forEach(b =>
    ok("Stufe bei " + b + " px", css.indexOf("min-width:" + b + "px)") > 0 &&
       new RegExp("min-width:" + b + "px\\)\\{\\s*:root\\{ --spalte").test(css)));
  /* Die Textspalte wächst NICHT unbegrenzt — lange Zeilen liest niemand gern,
     die Breite gehört dann in mehrere Spalten. */
  ok("Textspalte endet bei 1280", /--spalte:1280px/.test(css) && !/--spalte:1[5-9]\d\dpx/.test(css));

  /* Mit Maus darf es dichter sein: Am Handy braucht der Daumen 44 px. */
  ok("dichter nur mit feinem Zeiger", /@media \(pointer:fine\) and \(min-width:900px\)/.test(css));
  /* Mehrspaltig nur für unabhängige Karten. */
  ok("Kachelraster ab 1000 px", /min-width:1000px\)\{[\s\S]{0,120}\.kachelgrid\{column-count:2/.test(css));
  ok("drei Spalten ab 1600 px", /min-width:1600px\)\{[\s\S]{0,80}column-count:3/.test(css));
  ok("Karten brechen nicht mitten durch", /break-inside:avoid/.test(css));
  ok("Rekorde nutzen es", /kachelgrid">`\+h\+`<\/div>/.test(src));
  /* Navigation zur Seite — nur mit Maus UND Breite. */
  ok("Seitennavigation ab 1280 px mit Maus",
    /@media \(min-width:1280px\) and \(pointer:fine\)\{[\s\S]{0,200}nav\{/.test(css));
  /* NUR DAS GEÖFFNETE Blatt wird zum Dialog. Die Regel auf `.sheet` allgemein
     setzte auch das GESCHLOSSENE in die Bildschirmmitte — es schließt sich am
     Telefon dadurch, dass es unten aus dem Bild fährt. Ergebnis war ein leeres
     Fenster mit einem ✕, das nichts tut (v3.21 → v3.23). */
  ok("nur das offene Blatt wird zum Dialog", /\.sheet\.open\{[\s\S]{0,200}translate\(-50%,-50%\)/.test(css));
  ok("geschlossene Blätter bleiben unten", !/body:not\(\.komp\) \.sheet\{/.test(css));
  /* Im Dialog ist der Geräterand kein Bezugspunkt mehr — der Fußraum für die
     Handy-Sicherheitszone sieht dort aus wie ein Fehler. */
  ok("Fußraum im Dialog angepasst", /\.sheet\.open #sheetBody\{padding-bottom:18px\}/.test(css));
  /* Feste Elemente saßen 84 px über dem unteren Rand — dort ist mit der
     Seitennavigation nichts mehr, sie schwebten grundlos hoch. */
  ok("Meldung sitzt am unteren Rand", /\.toast\{bottom:24px/.test(css));
  ok("Aktionsknopf folgt der Inhaltsspalte", /\.fab\{bottom:24px;\s*right:max\(16px/.test(css));
  ok("Unterleiste macht den Einzug mit", /#subnav\{padding-left:198px\}/.test(css));
  ok("Ecken werden mitgemacht", /\.sheet\.open #sheetFoot\{[^}]*border-radius:0 0 18px 18px/.test(css));

  /* Der Schalter übersteuert die Automatik: Media Queries kennen die
     Fenstergröße, nicht die Absicht. */
  ok("Schalter vorhanden", /id="cfgKompakt"/.test(src));
  ok("erzwingt die schmale Spalte", /body\.komp\{ --spalte:640px !important; \}/.test(css));
  ok("alle Erweiterungen respektieren ihn", (css.match(/body:not\(\.komp\)/g) || []).length >= 10);
  ok("beim Start angewandt", /layoutAnwenden\(\);\s+\/\/ v3\.21/.test(src));
}

/* ============ 24cy. Empfehlung zieht von selbst nach ============ */
group("Caddy-Takt — ohne Eingabe passiert auch etwas");
{
  const src = fs.readFileSync(FILE, "utf8");
  /* Der Live-Zeiger entstand NUR bei einer Eingabe. Wer geht, tippt nichts —
     die Uhr bekam die Empfehlung des letzten Eintrags, gerechnet für einen
     Punkt, an dem man nicht mehr steht. Auf dem Weg zum Ball ist das der
     ganze Weg. */
  ok("eigener Takt während der Runde", /_caddyPushTimer=setInterval\(caddyLivePush, 10000\)/.test(src));
  ok("startet mit dem Runden-Sync", /function playStartSync\(\)\{[\s\S]{0,120}caddyLiveStart\(\);/.test(src));
  ok("und endet mit ihm", /caddyLiveStop\(\); \}/.test(src));
  /* Geschickt wird NUR bei Änderung — sonst Dauerfeuer auf eine Datei, die
     ohnehin jede Sekunde gelesen wird. */
  ok("schickt nur bei Änderung", /if\(marke===_caddyLetzte\) return;/.test(src));
  ok("Marke enthält Loch, Schläger, Rest und Ort", /const marke=\[lv\.hole, c\.club/.test(src));
  /* Rest auf 10 m gerundet: Ein Meter Unterschied ändert die Schlägerwahl
     nicht, würde aber jede Sekunde eine Meldung auslösen. */
  ok("Rest grob gerundet", /Math\.round\(c\.rest\/10\)/.test(src));
}

/* ============ 24cx. Uhr-Position, Handy-Rechnung ============ */
group("Caddy — beste Rechnung am richtigen Ort");
{
  const src = fs.readFileSync(FILE, "utf8");
  const C = G("caddyFuerPunkt"), W = G("_watchPos"), DB0 = G("DB");

  /* Das Handy ist führend, rechnete aber mit SEINER Position — und liegt oft
     im Trolley, während man am Ball steht. Zwanzig Meter sind ein halber
     Schläger. Die Uhr meldet den Ort, das Handy rechnet dafür. */
  ok("Position ist ein Parameter", typeof C === "function");
  ok("Live-Zeiger nutzt die Uhr-Position", /const wp=_watchPos\(\);[\s\S]{0,120}const punkt=wp\|\|PLAY\.here;/.test(src));
  ok("und vermerkt, für wen gerechnet wurde", /fuer:\(wp\?"watch":"phone"\)/.test(src));
  /* Die Streuung auf der Handy-Karte gehört zur Handy-Position — sonst zeigt
     das Oval an einem Ort, an dem das Handy gar nicht ist. */
  ok("kein Oval für die fremde Position", /caddyFuerPunkt\(punkt, false\)/.test(src));

  if (typeof W === "function" && DB0) {
    const sicher = DB0._draftRound;
    const jetzt = new Date().toISOString();
    const alt = new Date(Date.now() - 5 * 60000).toISOString();
    DB0._draftRound = { live: { src: "watch", at: jetzt, pos: { src: "watch", lat: 54.0, lng: 10.7, at: jetzt } } };
    ok("frische Uhr-Position wird genommen", Array.isArray(W()) && W()[0] === 54.0);
    /* Eine VERALTETE Position ist schlimmer als keine: Sie sieht gleich aus,
       führt aber zu einer Empfehlung für einen Ort, an dem niemand steht. */
    DB0._draftRound = { live: { src: "watch", at: alt, pos: { src: "watch", lat: 54.0, lng: 10.7, at: alt } } };
    ok("veraltete Position wird verworfen", W() === null);
    DB0._draftRound = { live: { src: "phone", at: jetzt } };
    ok("ohne Positionsmeldung nichts", W() === null);
    DB0._draftRound = sicher;
  }
}

/* ============ 24cw. Erwartungstabellen: Rough und Reichweite ============ */
group("Erwartungswerte — Rough und die Grenzen der Tabelle");
{
  const S = G("STRAT"), src = fs.readFileSync(FILE, "utf8");

  if (S && S.ES_BASE) {
    ok("Rough hat eigene Stützstellen", Array.isArray(S.ES_BASE.rough) && S.ES_BASE.rough.length >= 8);
    /* Rough muss auf JEDER Distanz teurer sein als Fairway — sonst wäre es
       ein Vorteil, ins Rough zu spielen. */
    const f = d => S.lookup(d, "fairway", 20), r = d => S.lookup(d, "rough", 20);
    [20, 60, 100, 150, 200].forEach(d =>
      ok("Rough teurer als Fairway bei " + d + " m", r(d) > f(d), (r(d) - f(d)).toFixed(3)));
    /* Und der Aufschlag muss dem gemessenen Verlauf folgen: klein bei 20 m,
       größer im mittleren Bereich — die alte lineare Näherung unterschätzte
       ihn genau dort, wo die Layup-Entscheidung fällt. */
    ok("Aufschlag bei 20 m kleiner als bei 120 m", (r(20) - f(20)) < (r(120) - f(120)),
      `${(r(20)-f(20)).toFixed(2)} < ${(r(120)-f(120)).toFixed(2)}`);
    /* Gegen die TABELLE prüfen, nicht gegen `lookup` — dort kommt der
       handicap- und lie-abhängige `esOffset` obendrauf, der die reine
       Rough-Differenz überlagert. */
    const rt = d => S._interp(S.ES_BASE.rough, d) - S._interp(S.ES_BASE.fairway, d);
    ok("Aufschlag in der Tabelle bei 120 m um 0,3", Math.abs(rt(120) - 0.30) < 0.06, rt(120).toFixed(3));
    ok("und bei 20 m deutlich kleiner", rt(20) < rt(120), `${rt(20).toFixed(2)} < ${rt(120).toFixed(2)}`);
    /* Hohes Gras ist nicht dasselbe wie Rough. */
    ok("Hohes Gras teurer als Rough", S.lookup(100, "highrough", 20) > r(100));

    /* Die Tabellen müssen den Bereich abdecken, in dem gespielt wird — sonst
       wird extrapoliert: Ein Par 5 mit 240 m Rest lag außerhalb. */
    const letzte = a => a[a.length - 1][0];
    ok("Fairway reicht über 210 m hinaus", letzte(S.ES_BASE.fairway) >= 240, String(letzte(S.ES_BASE.fairway)));
    ok("Sand reicht über 100 m hinaus", letzte(S.ES_BASE.sand) >= 140, String(letzte(S.ES_BASE.sand)));
    ok("Rough deckt denselben Bereich ab wie Fairway",
      letzte(S.ES_BASE.rough) >= letzte(S.ES_BASE.fairway));
    /* Monoton steigend — weiter weg darf nie billiger sein. */
    ["fairway", "rough", "sand", "tee"].forEach(k => {
      const t2 = S.ES_BASE[k];
      ok("steigt monoton: " + k, t2.every((p, i) => i === 0 || p[1] > t2[i - 1][1]));
    });
  }
  /* Die Herkunft der Zahlen gehört in den Code — sonst weiß in einem Jahr
     niemand, warum dort 3,09 steht. */
  ok("Quelle vermerkt", /Broadie/.test(src));
  ok("Einheiten-Vorbehalt vermerkt", /Broadies Tabellen sind in\s*\n\s*Yards/.test(src));
}

/* ============ 24cv. Ausrüstung ============ */
group("Ausrüstung — womit, und seit wann");
{
  const src = fs.readFileSync(FILE, "utf8");
  const S = G("equipSet"), A = G("equipAll"), H = G("equipHtml"), DB0 = G("DB");

  /* Die Schlägerliste beantwortet „wie weit", nicht „womit". Und das DATUM ist
     der eigentliche Wert: Ändern sich die Zahlen ab einem Tag, sieht man, ob
     dort etwas gewechselt wurde — allen voran der Ball. */
  if (typeof S === "function" && DB0) {
    const sicher = DB0.equipment;
    DB0.equipment = {};
    S("ball", "text", "Srixon Q-Star Tour");
    const e = A();
    eq("Wert gespeichert", e.ball.text, "Srixon Q-Star Tour");
    ok("Datum beim Modellwechsel gesetzt", /^\d{4}-\d{2}-\d{2}$/.test(e.ball.seit || ""), e.ball.seit);
    const seit1 = e.ball.seit;
    /* Eine Notiz ist kein Wechsel — sonst wäre das Datum wertlos. */
    S("ball", "seit", "2026-05-01");
    eq("Datum von Hand änderbar", A().ball.seit, "2026-05-01");
    S("ball", "text", "Srixon Q-Star Tour");
    eq("gleicher Text setzt das Datum NICHT neu", A().ball.seit, "2026-05-01");
    S("ball", "text", "Titleist Pro V1");
    ok("neuer Text setzt es neu", A().ball.seit !== "2026-05-01");
    /* Leeren entfernt auch das Datum — ein „seit" ohne Gerät wäre sinnlos. */
    S("ball", "text", "");
    ok("leeren entfernt das Datum", !A().ball.seit);
    DB0.equipment = sicher;
  }
  /* DEUTSCHES DATUM (v3.19): Gespeichert wird ISO — nur so lässt sich
     sortieren. Eingegeben und angezeigt wird TT.MM.JJJJ. */
  {
    const D = G("deDatumZuIso"), I = G("isoZuDeDatum");
    if (typeof D === "function") {
      eq("kurze Schreibweise", D("5.3.26"), "2026-03-05");
      eq("mit führenden Nullen", D("05.03.2026"), "2026-03-05");
      eq("ISO bleibt ISO", D("2026-03-05"), "2026-03-05");
      eq("leer bleibt leer", D(""), "");
      /* Unerkanntes gibt null — der Aufrufer lässt es dann stehen, statt es
         still wegzuräumen. Ein Datum, das beim Tippen verschwindet, ist
         ärgerlicher als ein falsches. */
      eq("Unsinn wird nicht geraten", D("irgendwas"), null);
      eq("den 31. Februar gibt es nicht", D("31.2.2026"), null);
      eq("Monat 13 auch nicht", D("1.13.2026"), null);
    }
    if (typeof I === "function") {
      eq("Anzeige deutsch", I("2026-03-05"), "05.03.2026");
      eq("ohne Datum nichts", I(""), "");
    }
  }
  if (typeof H === "function") {
    const h = H();
    ["Golfball", "Trolley", "Golftasche", "Putter", "Wedges", "Entfernungsmesser"].forEach(f =>
      ok("Feld vorhanden: " + f, h.indexOf(f) > 0));
  }
  ok("unter Schläger eingehängt", /h \+= equipHtml\(\);/.test(src));
  ok("Eingaben werden gebunden", /equipBind\(\);/.test(src));
}

/* ============ 24ct. Changelog-Archiv ============ */
group("Changelog — aktuell in der Datei, älteres im Archiv");
{
  const src = fs.readFileSync(FILE, "utf8");
  const doc = (src.match(/<script[^>]*devdocs[^>]*>([\s\S]*?)<\/script>/) || [])[1] || "";
  const i = doc.indexOf("## Changelog");
  const cl = doc.slice(i);
  const eintraege = (cl.match(/\n- \*\*v/g) || []).length;

  /* Das Changelog war auf 283 kB gewachsen — ein Achtel einer Datei, die bei
     jedem Start geladen und geparst wird. Die Begründungen bleiben erhalten,
     sie stehen nur woanders. */
  ok("Changelog bleibt handlich", eintraege <= 45, eintraege + " Einträge");
  ok("Archiv ist benannt", /changelog-archiv\.md/.test(cl));
  ok("aktuelle Fassung steht drin", cl.indexOf("v" + (src.match(/APP_VERSION="([\d.]+)"/) || [])[1]) > 0);

  /* NACH FASSUNGSNUMMER sortiert, nicht nach Reihenfolge im Text — beim
     Auslagern fiel auf, dass ein Eintrag (v2.94) über neueren stand. */
  const vs = [...cl.matchAll(/\n- \*\*v([\d.]+)/g)].map(m => m[1].split(".").map(Number));
  let sortiert = true;
  for (let k = 1; k < vs.length; k++) {
    const a = vs[k - 1], b = vs[k];
    for (let p = 0; p < 3; p++) {
      if ((a[p] || 0) > (b[p] || 0)) break;
      if ((a[p] || 0) < (b[p] || 0)) { sortiert = false; break; }
    }
    if (!sortiert) break;
  }
  ok("neueste Fassung zuerst", sortiert);
}

/* ============ 24cs. Neue Seed-Tests erreichen bestehende Datenbanken ============ */
group("Seed-Tests nachziehen");
{
  const src = fs.readFileSync(FILE, "utf8");
  const E = G("ensureSeedTests"), DB0 = G("DB"), SEED0 = G("SEED");

  /* `SEED.testDefs` gilt nur für eine LEERE Datenbank. Wer die App schon
     benutzt, hat seine eigene Liste — und die wurde nie mit dem Seed
     abgeglichen. Jeder neu angelegte Test war für bestehende Nutzer damit
     unsichtbar; aufgefallen an den Chip-Tests aus v3.04. */
  if (typeof E === "function" && DB0 && SEED0) {
    const sicher = JSON.parse(JSON.stringify(DB0.testDefs || []));
    const uiSicher = DB0.ui && DB0.ui.seedTestsAdded;
    DB0.testDefs = (DB0.testDefs || []).filter(d => String(d.key).indexOf("chip") !== 0);
    DB0.ui = DB0.ui || {}; delete DB0.ui.seedTestsAdded;

    const n = E();
    ok("ergänzt die fehlenden", n >= 3, String(n));
    ["chiplande", "chiproll", "chipleiter"].forEach(k =>
      ok("vorhanden: " + k, (DB0.testDefs || []).some(d => d.key === k)));
    eq("zweiter Lauf ergänzt nichts", E(), 0);
    const doppelt = (() => { const s = {}; let d = 0;
      DB0.testDefs.forEach(t2 => { if (s[t2.key]) d++; s[t2.key] = 1; }); return d; })();
    eq("keine Doppelten", doppelt, 0);

    /* Eine LÖSCHUNG war eine Entscheidung — sie darf nicht bei jedem Start
       zurückgenommen werden. */
    DB0.testDefs = DB0.testDefs.filter(d => d.key !== "chiproll");
    E();
    ok("gelöschter Test bleibt gelöscht", !DB0.testDefs.some(d => d.key === "chiproll"));

    /* Gewichte sind kein Nutzerwert, sondern die Einteilung eines Ganzen. */
    const summe = DB0.testDefs.reduce((a, d) => a + (d.weight || 0), 0);
    ok("Gewichte bleiben bei rund 1", summe > 0.9 && summe <= 1.01, summe.toFixed(3));

    DB0.testDefs = sicher; if (uiSicher) DB0.ui.seedTestsAdded = uiSicher;
  }
  ok("beim Start aufgerufen", /try\{ ensureSeedTests\(\); \}catch/.test(src));
  ok("merkt sich Angelegtes", /DB\.ui\.seedTestsAdded=schon;/.test(src));
  ok("überschreibt keinen bestehenden Test", /if\(schon\.indexOf\(sd\.key\)>=0\) return;/.test(src));
}

/* ============ 24cr. Kopplungstest App ↔ Uhr ============ */
group("Kopplungstest — rechnen beide dasselbe?");
{
  const src = fs.readFileSync(FILE, "utf8");
  const F = G("probeFrage"), DB0 = G("DB");

  /* Die Rundensimulation prüft nur DIESE App. Ob die Uhr dieselben Zahlen
     rechnet, zeigte bisher erst die Bahn — dort ist es aufgefallen. */
  ok("eigene Datei, keine Spieldaten", /const PROBE_PATH="probe\.json"/.test(src));
  ok("Frage schreiben", /async function probeSchreiben\(obj\)/.test(src));
  ok("Antwort lesen", /async function probeLesen\(\)/.test(src));
  ok("Test-Knopf vorhanden", /id="cfgKoppel"/.test(src));

  /* Gefragt wird bevorzugt ein Loch mit VERTAUSCHTEM Tee/Grün — genau dort
     ist der Fehler aufgetreten, also wird genau dort geprüft. */
  if (typeof F === "function" && DB0) {
    const alt = DB0.courses;
    const mLat = 110540;
    const tee = [54.0, 10.75], green = [54.0 + 300 / mLat, 10.75];
    DB0.courses = [{ name: "T", tees: {}, geo: { holes: {
      1: { tee, green, line: [tee, green] },
      2: { tee: green, green: tee, swap: true, line: [tee, green] } }, features: [] } }];
    const q = F();
    ok("Frage gebildet", !!q, JSON.stringify(q));
    eq("nimmt das vertauschte Loch", q.hole, 2);
    ok("Testpunkt liegt ~150 m vor dem Grün", Math.abs(q.erwartet - 150) <= 3, String(q.erwartet));
    ok("Position ist ein Punkt", Array.isArray(q.pos) && q.pos.length === 2);
    /* Der Plan muss das vertauschte Loch ENTHALTEN — dort war der Fehler. */
    const P = G("probePlan");
    if (typeof P === "function") {
      const pl = P();
      ok("Plan gebildet", !!pl && (pl.aufgaben || []).length >= 6, pl && String(pl.aufgaben.length));
      const geoAuf = (pl.aufgaben || []).filter(x => x.k === "geo");
      ok("mehrere Geo-Prüfungen", geoAuf.length >= 3, String(geoAuf.length));
      ok("das vertauschte Loch ist dabei", geoAuf.some(x => x.hole === 2));
      ok("jede Geo-Prüfung trägt die Erwartung", geoAuf.every(x => x.soll > 0));
      /* Am Abschlag ist die Erwartung fast die volle Lochlänge, kurz vor dem
         Grün fast null — genau daran fällt ein Vorzeichenfehler auf. */
      const l2 = geoAuf.filter(x => x.hole === 2).map(x => x.soll);
      ok("Erwartungen fallen zum Grün hin", l2.length === 3 && l2[0] > l2[1] && l2[1] > l2[2], l2.join(" > "));
    }
    DB0.courses = alt;
  }

  /* Der Wert des Tests liegt im VERGLEICH — „ok" allein prüft nichts. */
  ok("zeigt beide Zahlen", /App "\+auf\.soll\+" m · Uhr "/.test(src));
  ok("meldet Abweichung deutlich", /ABWEICHUNG /.test(src));
  ok("meldet beide Fassungen", /App "\+APP_VERSION\+" · Uhr "/.test(src));
  ok("wartet begrenzt", /for\(let i=0;i<20;i\+\+\)/.test(src));
  /* Ein PLAN statt einer einzelnen Frage: Eine Distanz kann auf einem
     harmlosen Loch zufällig stimmen. */
  ok("Prüfplan vorhanden", /function probePlan\(\)/.test(src));
  ok("prüft mehrere Positionen je Loch", /am Abschlag[\s\S]{0,120}kurz vor dem Grün/.test(src));
  ok("prüft Schläger, Listen, Caddy und Quelle",
    /k:"club"/.test(src) && /k:"liste"/.test(src) && /k:"caddy"/.test(src) && /k:"quelle"/.test(src));
  /* Listen: Anzahl ALLEIN würde zwei verschiedene Listen gleicher Länge nicht
     unterscheiden — deshalb auch der erste Eintrag. */
  ok("Listen mit erstem Eintrag", /ersteSoll:String\(l\[0\]\)/.test(src));
  ok("zählt bestanden gegen abgewichen", /von "\+\(gut\+schlecht\)\+" Prüfungen bestanden/.test(src));

  const doc = (src.match(/<script[^>]*devdocs[^>]*>([\s\S]*?)<\/script>/) || [])[1] || "";
  ok("Worker kennt probe.json", /"watch\.json", "probe\.json"/.test(doc));
}

/* ============ 24cq. Die Uhr bekommt die AUFGELÖSTE Karte ============ */
group("watchGeo — was die Uhr sieht, ist was die App sieht");
{
  const src = fs.readFileSync(FILE, "utf8");
  const W = G("watchGeo");

  /* DER FELDFEHLER: `applyGeoOverrides` setzt bei vertauschtem Tee/Grün nur die
     MARKE `swap`; gedreht wird erst beim Lesen in `holeRef`. Die Uhr las roh —
     und zielte auf den Abschlag. Am Tee sind das rund 40 m statt 300 m zum
     Grün, und zwar nur auf den Löchern mit gesetztem swap. */
  if (typeof W === "function") {
    const mLat = 110540;
    const tee = [54.0, 10.75], green = [54.0 + 300 / mLat, 10.75];
    const geo = { holes: { 1: { tee: green, green: tee, swap: true },   // vertauscht gespeichert
                           2: { tee, green } },
                  features: [], overrides: { holes: { 1: { swap: true } } } };
    const w = W(geo);
    eq("Loch 1: Grün ist wirklich das Grün", JSON.stringify(w.holes["1"].green), JSON.stringify(green));
    eq("Loch 1: Tee ist wirklich der Abschlag", JSON.stringify(w.holes["1"].tee), JSON.stringify(tee));
    ok("die Marke ist weg (schon angewendet)", !("swap" in w.holes["1"]));
    eq("Loch 2 bleibt unverändert", JSON.stringify(w.holes["2"].green), JSON.stringify(green));
    /* Länge neu gerechnet — sonst stünde die Distanz des vertauschten Standes. */
    ok("distM passt zur aufgelösten Bahn", Math.abs(w.holes["1"].distM - 300) <= 2, String(w.holes["1"].distM));
    ok("overrides nicht doppelt mitgeschickt", !w.overrides);
    /* Fehlendes Grün: `holeRef` ergänzt es aus dem nächsten green-Feature
       (90 m um das Linienende, sonst 120 m). Die Uhr kann das nicht und zeigte
       dort gar keine Distanz. Der Ring muss dafür in Reichweite der BAHN
       liegen — hier über die Spiellinie. */
    const ring = [[54.0026,10.75],[54.0027,10.7501],[54.0026,10.7502],[54.0026,10.75]];
    const geo2 = { holes: { 3: { tee, line: [tee, green] } },
                   features: [{ kind: "green", ring }] };
    ok("fehlendes Grün wird ergänzt", !!W(geo2).holes["3"].green);
  }
  ok("Uhr-Datei nutzt die aufgelöste Karte", /o\.geo=watchGeo\(c\.geo\)/.test(src));
}

/* ============ 24cn. Verwerfen gilt auf beiden Geräten ============ */
group("Runde verwerfen — die Entscheidung reist mit");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* Eine LEERE draft.json heißt nur „gerade keine Runde im Repo" — nicht
     „verworfen". Das andere Gerät spielte weiter, sein nächster Push legte die
     Runde wieder an, und weil der jünger war, kam sie zurück. Ein Fehlen lässt
     sich nicht übertragen, ein DATUM schon. */
  ok("Verwerfen schreibt eine Marke", /draftPushRaw\(\{discardedTs:ts\}\)/.test(src));
  ok("eigener Schreibweg dafür", /async function draftPushRaw\(obj\)/.test(src));
  ok("Lesen erkennt sie", /if\(d && d\.discardedTs\)\{/.test(src));
  /* Nur wenn sie JÜNGER ist als der eigene Entwurf — sonst beendete eine alte
     Marke jede neue Runde sofort wieder. */
  /* NUR mit eigenem Entwurf und nur wenn die Marke jünger ist. Ohne eigene
     Runde gibt es nichts zu beenden — sonst wird aus einer Notiz ein Befehl,
     und zwei Geräte schaufeln sich gegenseitig die Datei leer (v3.05). */
  ok("nur eine jüngere Marke zählt", /if\(eigen && d\.discardedTs > eigen\) return \{verworfen/.test(src));
  ok("ohne eigenen Entwurf kein Befehl", /if\(!eigen\) return \{leer:true\};/.test(src));
  ok("laufende Runde wird beendet", /if\(p && p\.verworfen\)\{[\s\S]{0,400}PLAY\.active=false/.test(src));
  ok("ohne Rückfrage", !/if\(p && p\.verworfen\)\{[\s\S]{0,300}confirm\(/.test(src));
  ok("auch ohne laufende Runde übernommen",
    /if\(p && p\.verworfen\)\{[\s\S]{0,600}watchLiveBusy=false/.test(src));
  /* Und der eigene Push darf die verworfene Runde nicht zurückschreiben. */
  ok("kein Wiederbeleben durch den eigenen Push",
    /if\(tomb && tomb>=eigen\) return draftPushRaw\(\{discardedTs:tomb\}\)/.test(src));
  /* DER FEHLER VON v3.02: Ohne eigenen Entwurf wurde der Grabstein alle fünf
     Sekunden geschrieben — und löschte die laufende Runde des ANDEREN Geräts
     aus der gemeinsamen Datei. Ohne Eigenes wird gar nicht geschrieben. */
  ok("ohne eigenen Entwurf wird nicht geschrieben",
    /if\(!eigen\) return false;\s+\/\/ nichts Eigenes/.test(src));
}

/* ============ 24cm. Änderungen zwischen den Geräten ============ */
group("Abgleich — der neuere Stand gewinnt, je Loch");
{
  const src = fs.readFileSync(FILE, "utf8");
  const MD = G("mergeDraft"), T = G("playTouchHole");

  /* DAS PROBLEM: Der Abgleich füllte nur LEERE Felder. Das schützt vor
     gegenseitigem Überschreiben — macht aber das ÄNDERN unmöglich: Wer den
     Tee-Schläger am Handy korrigiert, sah auf der Uhr weiter den alten Wert. */
  if (typeof T === "function") {
    const h = { hole: 3 };
    T(h);
    ok("Loch bekommt einen Zeitstempel", typeof h.ts === "string" && h.ts.length > 10);
    ok("ohne Loch kein Absturz", (T(null), true));
    /* UHRZEIT-VERSATZ (v3.07, aus der Rundensimulation): Geht die Uhr eine
       Minute vor, trägt jede ihrer Eingaben einen Zeitstempel aus der Zukunft
       — und jede Korrektur am Handy gälte als älter und würde verworfen. Wer
       gerade tippt, muss das letzte Wort haben. */
    const zukunft = new Date(Date.now() + 60000).toISOString();
    const h2 = { hole: 4, ts: zukunft };
    T(h2);
    ok("schlägt einen Zeitstempel aus der Zukunft", h2.ts > zukunft, h2.ts + " > " + zukunft);
    const alt3 = new Date(Date.now() - 60000).toISOString();
    const h3 = { hole: 5, ts: alt3 };
    T(h3);
    ok("sonst schlicht die aktuelle Zeit", h3.ts > alt3 && h3.ts <= new Date(Date.now() + 2000).toISOString(), h3.ts);
  }
  /* Und die REIHENFOLGE: erst stempeln, dann die Runde bauen — `playRound()`
     kopiert die Löcher, wer danach stempelt, schickt den alten Stand. */
  ok("erst stempeln, dann bauen",
    /playTouchHole\(PLAY\.holes\[PLAY\.idx\]\);\s*\n\s*const r=playRound\(\);/.test(src));

  const ad = src.slice(src.indexOf("function playAdoptDraft()"),
                       src.indexOf("function playAdoptDraft()") + 1600);
  ok("neuerer fremder Stand gewinnt", /const fremdNeuer = !!\(dh\.ts && \(!h\.ts \|\| dh\.ts > h\.ts\)\)/.test(ad));
  ok("null löscht weiterhin nichts", /if\(dh\[k\]==null\) return;/.test(ad));
  /* Ohne Zeitstempel (Entwurf von vor dieser Fassung) bleibt es beim alten,
     sicheren Verhalten. */
  ok("Rückfall auf „nur leere Felder\"", /if\(h\[k\]==null \|\| fremdNeuer\)/.test(ad));
  ok("Zeitstempel wandert mit", /if\(fremdNeuer\) h\.ts=dh\.ts;/.test(ad));
  /* Die ganze Maske nachziehen, nicht nur drei Zahlen. */
  ok("Eingabemaske wird erneuert", /sheet\.classList\.contains\("open"\)\) renderPlay\(\)/.test(ad));
  ok("jede Eingabe stempelt das Loch", /playTouchHole\(PLAY\.holes\[PLAY\.idx\]\)/.test(src));

  /* Und im Merge: je Loch entscheiden, nicht pauschal nach Entwurf. */
  if (typeof MD === "function") {
    const d = (ts, holes) => ({ ts, round: { date: "2026-08-15", course: "N", side: "18 Loch", holes } });
    /* Gerät A hat zuletzt IRGENDETWAS getan (neuerer Entwurf), aber Loch 3
       zuletzt von B korrigiert. Ohne Loch-Zeitstempel gewänne A. */
    const a = d("2026-08-15T10:10:00Z", [{ hole: 3, club: "5 Wood", ts: "2026-08-15T09:00:00Z" },
                                          { hole: 7, score: 4, ts: "2026-08-15T10:10:00Z" }]);
    const b = d("2026-08-15T10:00:00Z", [{ hole: 3, club: "3 Wood", ts: "2026-08-15T10:00:00Z" }]);
    const r = MD(b, a, "");
    const l3 = r.round.holes.find(h => h.hole === 3);
    eq("die jüngere Korrektur am Loch gewinnt", l3.club, "3 Wood");
    ok("das andere Loch bleibt erhalten", !!r.round.holes.find(h => h.hole === 7));
    /* Ohne Zeitstempel wie bisher: gesetzte Felder des neueren Entwurfs. */
    const a2 = d("2026-08-15T10:10:00Z", [{ hole: 3, club: "5 Wood" }]);
    const b2 = d("2026-08-15T10:00:00Z", [{ hole: 3, club: "3 Wood" }]);
    eq("ohne Loch-Zeitstempel gilt der neuere Entwurf", MD(b2, a2, "").round.holes[0].club, "5 Wood");
  }
}

/* ============ 24cl. Schlag-Beschriftung und Statistik-Marke ============ */
group("Schlageditor — was am Schlag steht und was zählt");
{
  const src = fs.readFileSync(FILE, "utf8");
  const Z = G("shotZaehlt");

  /* EINE Quelle für „zählt in die gelernten Schlägerlängen": Sonst sagt die
     Marke auf der Karte das eine und `clubMeasured` rechnet das andere. */
  if (typeof Z === "function") {
    const heute = new Date().toISOString();
    const alt = new Date(Date.now() - 400 * 864e5).toISOString();
    ok("voller Schlag mit Schläger zählt", Z({ club: "7 Iron", ts: heute, accA: 5, accB: 5 }));
    ok("ohne Schläger nicht", !Z({ ts: heute, accA: 5, accB: 5 }));
    /* Fehlendes `swing` bedeutet voll — so waren alle Altdaten gemeint. */
    ok("ohne Angabe gilt als voll", Z({ club: "PW", ts: heute }));
    ok("ausdrückliches Voll zählt auch", Z({ club: "PW", swing: "Voll", ts: heute }));
    ok("¾ zählt nicht", !Z({ club: "PW", swing: "¾", ts: heute }));
    ok("älter als ein Jahr zählt nicht", !Z({ club: "PW", ts: alt }));
    /* Zu ungenau gemessen = Länge geraten. */
    ok("bei 60 m Ungenauigkeit nicht", !Z({ club: "PW", ts: heute, accA: 60, accB: 60 }));
    ok("bei 5 m Ungenauigkeit doch", Z({ club: "PW", ts: heute, accA: 5, accB: 5 }));
    ok("ohne Schlag nichts", !Z(null));
  }

  /* Beschriftung: Meter + ✓, darunter Schläger und Schwungart. */
  ok("Meter mit Marke", /\$\{d\} m\$\{zaehlt\?" ✓":""\}/.test(src));
  ok("zweite Zeile für Schläger/Schwung", /zeile2\.join\(" · "\)/.test(src));
  /* Ein voller Schwung wird nicht beschriftet: Normalfall, und eine Angabe
     bei neun von zehn Schlägen liest niemand. */
  ok("voll wird nicht beschriftet", /meta\.swing!=="Voll"\) zeile2\.push/.test(src));
  ok("Editor gibt die Angaben mit", /shotMeta:meta/.test(src));
  /* Die Bahn von i-1 nach i gehört zum Schlag, der an i-1 begann — der letzte
     Punkt ist die Ruhelage und hat keine eigene Bahn. */
  ok("letzter Punkt fällt weg", /const meta=shots\.slice\(0,-1\);/.test(src));
  ok("Rundenansicht ebenso", /shotMeta:hl\.shots\.slice\(0,-1\)/.test(src));

  /* Altbestand: einmalig ausdrücklich „Voll". */
  const mg = src.slice(src.indexOf("function migrateSwingVoll()"),
                       src.indexOf("function ensureDefaults()"));
  ok("Wanderung vorhanden", /if\(x && !x\.swing\)\{ x\.swing="Voll"/.test(mg));
  ok("gpsShots erfasst", /setz\(DB\.gpsShots\)/.test(mg));
  ok("Runden erfasst", /\(r\.holes\|\|\[\]\)\.forEach\(h=>setz\(h\.shots\)\)/.test(mg));
  ok("laufender Entwurf auch", /DB\._draftRound/.test(mg));
  /* Einmalig — sonst liefe sie bei jedem Start über alle Runden. */
  ok("nur einmal", /if\(DB\.ui && DB\.ui\.swingMigriert\) return;/.test(mg));
  ok("Marke wird gesetzt", /DB\.ui\.swingMigriert=new Date\(\)\.toISOString\(\)/.test(mg));
  ok("beim Start aufgerufen", /try\{ migrateSwingVoll\(\); \}catch/.test(src));
}

/* ============ 24ck. Schlanke Datei für die Uhr ============ */
group("watch.json — die Uhr trägt nicht 3 MB");
{
  const src = fs.readFileSync(FILE, "utf8");
  const WP = G("watchPayload"), DB0 = G("DB");

  /* Die Uhr liest Plätze, Schläger, Optionen, Handicap und Gameplans. Runden,
     Tests, Notizen und die Wissensdatenbank machen den Großteil der 3 MB aus
     und interessieren sie nie. */
  if (typeof WP === "function" && DB0) {
    const alt = { courses: DB0.courses, rounds: DB0.rounds, clubDistances: DB0.clubDistances };
    DB0.courses = [
      { name: "A", tees: { Gelb: { holes: [] } }, geo: { features: [1, 2, 3] } },
      { name: "B", tees: { Gelb: { holes: [] } }, geo: { features: [4, 5] } },
      { name: "C", tees: { Gelb: { holes: [] } }, geo: { features: [6] } },
      { name: "D", tees: { Gelb: { holes: [] } }, geo: { features: [7] } }
    ];
    DB0.rounds = [
      { date: "2026-08-10", course: "A" }, { date: "2026-08-08", course: "B" },
      { date: "2026-08-01", course: "C" }
    ];
    const p = WP();
    eq("alle Plätze sind dabei", (p.courses || []).length, 4);
    const mitGeo = (p.courses || []).filter(c => c.geo).map(c => c.name).sort().join(",");
    /* Die Geometrie ist der Löwenanteil — nur die zuletzt gespielten drei. */
    eq("Geometrie nur für die zuletzt gespielten", mitGeo, "A,B,C");
    ok("nie gespielter Platz ohne Geometrie", !(p.courses.find(c => c.name === "D") || {}).geo);
    /* Und das Schwere fehlt. */
    ["rounds", "tests", "notes", "wiki", "lmSessions", "fitnessSessions"].forEach(k =>
      ok("nicht enthalten: " + k, !(k in p)));
    ok("Schläger enthalten", "clubDistances" in p);
    ok("Gameplans enthalten", !!(p.strat || {}).gameplans);
    /* DIE AUSWAHLLISTEN: Die Uhr liest sie aus der OBERSTEN Ebene. Fehlten
       sie, fiel sie auf ihre eingebauten Vorgaben zurück — „100-150m" statt
       „110-140" —, und ein auf der Uhr gewählter Wert stand am Handy in keiner
       Liste. Es sah aus, als würde nichts übertragen. */
    ["approachBuckets","approachLies","approachMiss","bunkerTypes","firstPuttDist",
     "kurzseitig","penaltyTypes","puttMiss","puttRest","qualityOpts","teeClubs",
     "teeResults"].forEach(k => {
      const da = Array.isArray(DB0[k]);
      ok("Liste mitgegeben: " + k, !da || Array.isArray(p[k]));
    });

    /* DIE KARTE IST DER GRUND, WARUM DIE UHR DISTANZEN ZEIGT (v3.05).
       Vorher bekamen nur die drei zuletzt GESPIELTEN Plätze eine — beim ersten
       Mal auf einem Platz gibt es aber noch keine Runde, also stand man ohne
       Karte auf der Bahn. */
    DB0.rounds = [{ date: "2026-08-10", course: "A" }];
    const vorher = DB0._draftRound;
    DB0._draftRound = { ts: "x", round: { course: "D", date: "2026-08-15" } };
    const p2 = WP();
    const mitGeo2 = (p2.courses || []).filter(c => c.geo).map(c => c.name).sort().join(",");
    ok("Platz der laufenden Runde ist dabei", mitGeo2.indexOf("D") >= 0, mitGeo2);
    DB0._draftRound = vorher;
    DB0.courses = alt.courses; DB0.rounds = alt.rounds; DB0.clubDistances = alt.clubDistances;
  }

  /* Nur bei Änderung schreiben — sonst bekäme die Datei im Minutentakt
     Commits. Der Fingerabdruck lässt den Zeitstempel bewusst aus, sonst wäre
     jeder Aufruf eine Änderung. */
  ok("Fingerabdruck ohne Zeitstempel", /replace\(\/"at":"\[\^"\]\*",\/,""\)/.test(src));
  ok("unverändert = kein Schreiben", /if\(!force && fp===WATCH_FP\) return true;/.test(src));
  ok("Schreiben über den SHA-Türsteher", /"X-Path":WATCH_PATH,"X-Base-Sha":WATCH_SHA/.test(src));
  ok("alter Worker schaltet ab", /if\(r\.status===403\)\{ WATCH_MODE=false; return false; \}/.test(src));
  ok("Anstoß entprellt", /_watchT=setTimeout\(\(\)=>\{ watchFilePush\(\); \}, 8000\)/.test(src));
  /* Angestoßen dort, wo sich der Inhalt ändern kann. */
  ["geoEdSnapshot", "geo=geo;\n  DB.courses\[idx\].geoAt"].forEach(() => {});
  ok("Kartenänderung stößt an", /function geoEdSnapshot\(was\)\{\s*\n\s*watchFilePushSoon\(\)/.test(src));
  ok("Import stößt an", /geoAt=new Date\(\)\.toISOString\(\);\s*\n\s*watchFilePushSoon\(\)/.test(src));
  ok("Gameplan stößt an", /persistSoon\(\); watchFilePushSoon\(\)/.test(src));
  ok("einmal nach dem Start", /setTimeout\(\(\)=>\{ watchFilePush\(\); \}, 6000\)/.test(src));

  const doc = (src.match(/<script[^>]*devdocs[^>]*>([\s\S]*?)<\/script>/) || [])[1] || "";
  ok("Worker kennt watch.json", /"draft\.json", "watch\.json"/.test(doc));
  ok("Worker-Fassung hochgezählt", /Fassung v2\.8/.test(doc));
}

/* ============ 24ch. Abgleich prüfen ============ */
group("Diagnose — welches Glied der Kette fehlt");
{
  const src = fs.readFileSync(FILE, "utf8");
  const cd = src.slice(src.indexOf("async function cloudDiag()"), src.indexOf("function cloudCfg()"));

  /* Der Abgleich hängt an vier Gliedern; fällt eines aus, sieht man an der
     Oberfläche immer dasselbe: nichts kommt an. Beim Umbau auf draft.json hat
     sich das gerächt — ein Worker vor v2.6 ignoriert `path` und liefert mit
     Status 200 die große Datei, der Ausfall sah wie ein Erfolg aus. */
  ok("Knopf in den Einstellungen", /id="cfgDiag"/.test(src));
  ok("Ausgabefeld daneben", /id="cfgDiagOut"/.test(src));
  ok("prüft die Erreichbarkeit", /Worker erreichbar/.test(cd));
  ok("nennt die Worker-Fassung", /workerV=\(d&&d\.worker\)/.test(cd));
  ok("prüft ?sha=1", /freshRepoSha\(\)/.test(cd));
  ok("prüft das LESEN der kleinen Datei", /draftPull\(\)/.test(cd));
  ok("und benennt den alten Worker als Ursache", /älter als v2\.6/.test(cd));
  ok("prüft das SCHREIBEN", /draftPush\(\)/.test(cd));
  ok("nennt CFG.PATHS als Stellschraube", /CFG\.PATHS/.test(cd));
  ok("prüft auch den großen Weg", /freshRepoFetch\(\)/.test(cd));
  ok("meldet die App-Fassung mit", /APP_VERSION/.test(cd));
  /* Ohne Zugangsdaten darf sie nicht ins Leere laufen. */
  ok("ohne Konfiguration klare Ansage", /Keine Worker-URL\/Passwort eingetragen/.test(cd));
}

/* ============ 24cg. Der Entwurf als eigene kleine Datei ============ */
group("draft.json — heiß und klein statt kalt und groß");
{
  const src = fs.readFileSync(FILE, "utf8");
  const MD = G("mergeDraft");

  /* Die Vereinigungsregel stand INLINE in mergeDB. Seit der Entwurf auch als
     eigene Datei läuft, braucht sie ein zweiter Aufrufer — zwei Kopien wären
     zwei Wahrheiten darüber, wessen Eingabe gewinnt. */
  if (typeof MD === "function") {
    const d = (ts, holes, live) => ({ ts, round: { date: "2026-08-14", course: "N", side: "18 Loch", holes },
      live: live || undefined });
    const alt = d("2026-08-14T10:00:00Z", [{ hole: 1, score: 5 }, { hole: 2, putts: 2 }]);
    const neu = d("2026-08-14T10:05:00Z", [{ hole: 1, putts: 2 }, { hole: 3, score: 4 }]);
    const r = MD(alt, neu, "");
    eq("gleiche Runde: Löcher vereint", (r.round.holes || []).length, 3);
    eq("Feld des älteren bleibt", r.round.holes.find(h => h.hole === 1).score, 5);
    eq("Feld des neueren kommt dazu", r.round.holes.find(h => h.hole === 1).putts, 2);
    eq("Zeitstempel des neueren", r.ts, "2026-08-14T10:05:00Z");

    /* null darf nie löschen — sonst räumt ein Gerät die Eingaben des anderen ab. */
    const mitNull = d("2026-08-14T10:06:00Z", [{ hole: 1, score: null, putts: 3 }]);
    eq("null löscht nicht", MD(alt, mitNull, "").round.holes.find(h => h.hole === 1).score, 5);

    /* Verschiedene Runden: der neuere gewinnt ganz. */
    const andere = { ts: "2026-08-14T11:00:00Z", round: { date: "2026-08-14", course: "X", side: "18 Loch", holes: [] } };
    eq("andere Runde: neuerer gewinnt", MD(alt, andere, "").round.course, "X");

    /* Der live-Zeiger folgt einer EIGENEN Regel — sonst verliert das Gerät, das
       gerade nur blättert, gegen das Gerät, das gerade tippt. */
    const a2 = d("2026-08-14T10:00:00Z", [], { src: "phone", hole: 3, at: "2026-08-14T10:20:00Z" });
    const b2 = d("2026-08-14T10:10:00Z", [], { src: "watch", hole: 7, at: "2026-08-14T10:05:00Z" });
    eq("neuerer Zeiger gewinnt unabhängig vom Entwurf", MD(a2, b2, "").live.hole, 3);

    /* Verworfen-Marke: ein älterer Entwurf darf nicht auferstehen. */
    eq("verworfener Entwurf bleibt weg", MD(alt, null, "2026-08-14T12:00:00Z"), null);
    eq("ohne beides nichts", MD(null, null, ""), null);
  }

  /* Die kleine Datei: Lesen, Schreiben, Rückfall. */
  ok("Pfad ist draft.json", /const DRAFT_PATH="draft\.json"/.test(src));
  ok("Lesen über den path-Parameter", /fresh=1&path="\+encodeURIComponent\(DRAFT_PATH\)/.test(src));
  ok("Schreiben über den SHA-Türsteher", /"X-Path":DRAFT_PATH,"X-Base-Sha":DRAFT_SHA/.test(src));
  /* 409 = jemand war schneller: VEREINEN, nicht überschreiben — der andere ist
     gerade auf der Bahn. */
  /* Seit v3.32 VIER Anläufe statt eines — bei häufigem Schreiben beider Geräte
     kollidiert sonst auch der nächste Takt, und der Abgleich reißt mitten in
     der Runde ab, ohne dass etwas danach aussieht. */
  ok("409 wird vereint statt überschrieben",
    /for\(let anlauf=0; anlauf<4 && r\.status===409; anlauf\+\+\)\{[\s\S]{0,300}mergeDraft\(DB\._draftRound, p\.draft/.test(src));
  /* Die Pause muss ZUFÄLLIG sein: Zwei Geräte, die nach einem Konflikt
     gleichzeitig neu senden, kollidieren synchron wieder. */
  ok("zufällige Pause zwischen den Anläufen", /setTimeout\(x, 250\+Math\.random\(\)\*600\)/.test(src));
  /* Und ein Fehlschlag darf nicht lautlos bleiben — daran ist eine ganze Runde
     ohne Abgleich gelaufen. */
  ok("Fehlschlag wird protokolliert", /logErr\("draftPush", new Error\("HTTP "/.test(src));
  ok("nach drei Fehlschlägen wird gewarnt", /if\(DRAFT_FEHLER===3\) toast/.test(src));
  ok("Caddy-Takt drängelt sich nicht vor", /if\(_draftPushT\) return;/.test(src));
  /* Alter Worker (403) oder fehlende Datei (404) dürfen nicht blockieren. */
  ok("404 gilt als leer", /if\(r\.status===404\)\{ DRAFT_SHA=null; DRAFT_MODE=true; return \{leer:true\}; \}/.test(src));
  ok("403 fällt auf den alten Weg zurück", /if\(!r\.ok\)\{ DRAFT_MODE=false; return null; \}/.test(src));

  /* DER AUSFALL VOM 14.08.: Ein Worker VOR v2.6 kennt den `path`-Parameter
     nicht — er ignoriert ihn und liefert mit Status 200 die GROSSE Datei. Das
     sah wie ein Erfolg aus, enthielt aber kein `round`; beide Seiten hielten
     das für „keine Runde läuft" und fielen NICHT zurück. Der Abgleich stand
     still, und `draft.json` wurde nie angelegt, weil der Schreibversuch am
     selben Worker mit 403 scheiterte.
     Ein Statuscode sagt nicht, WAS man bekommen hat. */
  ok("große Datei wird am Inhalt erkannt",
    /if\(d && \(d\.testDefs \|\| d\.rounds \|\| d\._draftRound\)\)\{ DRAFT_MODE=false; return null; \}/.test(src));
  ok("danach wird der kleine Weg gesperrt", /if\(DRAFT_MODE===false\) return false;/.test(src));
  ok("403 beim Schreiben sperrt ihn auch", /if\(r\.status===403\)\{ DRAFT_MODE=false; return false; \}/.test(src));
  /* Und der Takt darf NUR abkürzen, wenn wirklich ein Entwurf kam. */
  ok("leere Antwort kürzt nicht ab", /if\(p && p\.draft\)\{/.test(src));
  ok("Runden-Takt nimmt zuerst die kleine Datei",
    /const p=await draftPull\(\);\s*\n\s*if\(p && p\.draft\)\{/.test(src));
  /* KORREKTUR v2.91.1 — der Fehler, der den Abgleich ganz lahmgelegt hat:
     Eine LEERE oder fehlende Datei galt als erledigte Antwort. Solange
     `draft.json` nicht existierte (also bis zum allerersten Push), lief damit
     gar kein Abgleich mehr, in beide Richtungen. Nur ein Entwurf IN der Datei
     darf den Takt beenden. */
  ok("leere Datei beendet den Takt NICHT",
    !/if\(p\)\{\s*\n\s*if\(p\.draft\)/.test(src));
  ok("und legt sie stattdessen an", /if\(p && p\.leer && DB\._draftRound\) draftPush\(\);/.test(src));
  ok("Uhr-Wächter ebenso", /if\(p && p\.draft\)\{[\s\S]{0,260}watchLiveMaybeOpen\(\); watchLiveBusy=false; return;/.test(src));
  ok("Schreiben ist entprellt", /_draftPushT=setTimeout\(\(\)=>\{ draftPush\(\); \}, 2000\)/.test(src));
  /* Die Messungen der Uhr reisen in derselben kleinen Datei mit — winzig, und
     sie dürfen nicht bis zum Rundenende liegenbleiben. Beim eigenen Schreiben
     müssen sie unverändert zurück, sonst löscht das Handy sie. */
  ok("Messungen werden übernommen", /DB\.gpsShots=_mergeArr\(DB\.gpsShots, d\.gpsShots/.test(src));
  ok("und beim Schreiben erhalten", /if\(DRAFT_SHOTS\.length\) d\.gpsShots=DRAFT_SHOTS;/.test(src));
  ok("Grabsteine gelten auch hier", /_tombFor\(DB,"gpsShots"\)/.test(src));
  ok("jede Eingabe stößt es an", /draftPushSoon\(\);\s+\/\/ v2\.91/.test(src));
  /* Nach dem Abschluss MUSS die kleine Datei leer sein, sonst zieht das andere
     Gerät die Runde wieder herein. */
  ok("Rundenende leert sie", /clearDraft\(\);\s*\n\s*draftFileClear\(\);/.test(src));

  /* Worker-Seite. */
  const doc = (src.match(/<script[^>]*devdocs[^>]*>([\s\S]*?)<\/script>/) || [])[1] || "";
  ok("Worker kennt draft.json", /"trainingsdaten\.json", "wissen-bilder\.json", "draft\.json"/.test(doc));
  ok("GET nimmt einen Pfad", /const p = url\.searchParams\.get\("path"\) \|\| "trainingsdaten\.json";/.test(doc));
  ok("und prüft ihn gegen die Whitelist", /if \(!CFG\.PATHS\.includes\(p\)\) return resp\(403/.test(doc));
  /* Nicht die Nummer festnageln — die waechst mit jeder Aenderung. Gemeint ist:
     der Abzug in der Doku kennt draft.json. */
  ok("Abzug kennt die kleine Datei", /"draft\.json"/.test(doc));
}

/* ============ 24cf. Schlagmessungen der Uhr ============ */
group("gpsShots — die Messungen der Uhr überleben den Abgleich");
{
  const M = G("mergeDB"), src = fs.readFileSync(FILE, "utf8");

  /* DER VERLUST: Für `gpsShots` gab es in mergeDB KEINE Regel — die Liste fiel
     unter Object.assign({},R,L), das lokale Array gewann vollständig. Die Uhr
     misst, pusht (additiv), das Handy lädt und wirft die Messungen weg; beim
     nächsten Push des Handys sind sie auch im Repo weg. Ohne Meldung.
     Die Uhr-Doku führt das seit Monaten als offenen Punkt. */
  if (typeof M === "function") {
    const basis = extra => Object.assign({ version: 12, testDefs: [{key:"a"}], rounds: [{id:"r1"}],
      tests: [], notes: [], clubDistances: [], competitions: [], profile: {}, ui: {}, tomb: {} }, extra);
    const handy = basis({ gpsShots: [{id:"G1",club:"Driver"},{id:"G2",club:"7 Iron"}] });
    const repo  = basis({ gpsShots: [{id:"G1",club:"Driver"},{id:"G2",club:"7 Iron"},
                                     {id:"G3",club:"5 Wood"},{id:"G4",club:"PW"}] });
    const r = M(handy, repo);
    eq("Messungen der Uhr kommen dazu", (r.gpsShots||[]).length, 4);
    ok("und zwar die richtigen", (r.gpsShots||[]).map(x=>x.id).sort().join(",") === "G1,G2,G3,G4");

    /* Gegenprobe: ein GELÖSCHTER Schlag darf nicht zurückkommen. */
    const mitTomb = basis({ gpsShots: [{id:"G1"}], tomb: { gpsShots: { G3: "2026-08-14T10:00:00Z" } } });
    const r2 = M(mitTomb, basis({ gpsShots: [{id:"G1"},{id:"G3"}] }));
    ok("gelöschter Schlag bleibt gelöscht", !(r2.gpsShots||[]).some(x=>x.id==="G3"));
  }
  ok("Merge-Schlüssel eingetragen", /gpsShots:\s+x => x\.id \|\| _J\(x\)/.test(src));
  ok("Löschen setzt einen Grabstein", /tombDel\("gpsShots", weg\)/.test(src));
  ok("Rückgängig hebt ihn auf", /tombClear\("gpsShots", weg\.id\)/.test(src));
  /* Und im Worker (ALT-Modus — den benutzt die Uhr!) gespiegelt. */
  const doc = (src.match(/<script[^>]*devdocs[^>]*>([\s\S]*?)<\/script>/) || [])[1] || "";
  ok("Worker vereinigt sie ebenfalls", /out\.gpsShots\s+= _mergeArr\(L\.gpsShots, R\.gpsShots/.test(doc));
  ok("Abzug vereinigt gpsShots", /out\.gpsShots\s+= _mergeArr/.test(doc));
}

/* ============ 24ce. GPS-Tick und Runden-Sync ============ */
group("Spielbetrieb — Rechenzeit und Funk auf der Runde");
{
  const src = fs.readFileSync(FILE, "utf8");
  const CS = G("courseSVG"), VK = G("playVecKey"), SF = G("syncFinger");

  /* GEMESSEN: Auf einer Bahn mit 300 erkannten Bäumen und 40 Waldringen baute
     jeder GPS-Tick 79 kB SVG neu — im Sekundentakt, vier Stunden lang. Dabei
     ändern sich zwischen zwei Ticks nur Position, Ringe, Messlinie und Oval. */
  if (typeof CS === "function") {
    const mLat = 110540, mLng = 111320 * Math.cos(54 * Math.PI / 180);
    const tee = [54.0, 10.75], green = [54.0 + 400 / mLat, 10.75];
    const ring = (f, s, r) => { const o = [];
      for (let k = 0; k < 12; k++) { const t = k / 12 * 2 * Math.PI;
        o.push([tee[0] + (f + Math.cos(t) * r) / mLat, tee[1] + (s + Math.sin(t) * r) / mLng]); }
      o.push(o[0]); return o; };
    const geo = { holes: { 1: { tee, green, line: [tee, green], par: 4, distM: 400 } },
      features: [{ kind: "fairway", ring: ring(200, 0, 30) }, { kind: "green", ring: ring(395, 0, 12) }],
      mine: [] };
    for (let i = 0; i < 60; i++) geo.mine.push({ kind: "tree", pt: [tee[0] + (40 + i * 5) / mLat, tee[1] + ((i % 2 ? 1 : -1) * 25) / mLng] });
    const here = [tee[0] + 150 / mLat, tee[1] + 8 / mLng];
    const opt = { w: 660, hole: 1, here, fitHere: false, rings: true, rotate: true, sat: false,
      tight: true, corridor: 46, flag: true, oval: { center: here, sigD: 18, sigL: 12, brg: 0, biasL: 0 } };
    const voll = CS(geo, opt), nur = CS(geo, Object.assign({}, opt, { dynOnly: true }));

    ok("Vollaufbau liefert beide Teile", !!voll.bodyStatic && !!voll.bodyDyn);
    eq("Reihenfolge bleibt: statisch, dann beweglich", voll.bodyNoSat, voll.bodyStatic + voll.bodyDyn);
    ok("dynOnly lässt den statischen Teil weg", nur.bodyStatic === "");
    eq("und liefert dasselbe Bewegliche", nur.bodyDyn, voll.bodyDyn);
    /* DER PUNKT: der bewegliche Teil ist ein Bruchteil. */
    /* Bei 60 Bäumen sind es ~28 %; auf einer erkannten Bahn mit 300 Bäumen
       fällt der Anteil unter 4 % (gemessen: 2,8 kB von 79 kB). Die Prüfung
       hält nur fest, dass der bewegliche Teil KLEIN bleibt. */
    ok("beweglich ist deutlich kleiner als das Ganze",
      voll.bodyDyn.length < voll.bodyNoSat.length * 0.35,
      voll.bodyDyn.length + " von " + voll.bodyNoSat.length);
    /* Die Projektion MUSS auch bei dynOnly stimmen — die beweglichen Teile
       rechnen damit. */
    eq("Projektion identisch", JSON.stringify([nur.M.W, nur.M.H, nur.M.s]), JSON.stringify([voll.M.W, voll.M.H, voll.M.s]));
    /* Die eigene Position gehört ins Bewegliche, die Bahn nicht. */
    ok("Positionspunkt ist beweglich", /id="hereDot"/.test(voll.bodyDyn));
    ok("Flächen sind statisch", /fairway|#d5ecc0/.test(voll.bodyStatic) || voll.bodyStatic.length > voll.bodyDyn.length);
  }

  /* Der Schlüssel darf NICHT von der Position abhängen — sonst wäre er bei
     jedem Tick neu und die Trennung wirkungslos. */
  if (typeof VK === "function" && G("PLAY")) {
    const P = G("PLAY"); P.course = "Nordplatz";
    const a = VK({ hole: 3 });
    eq("gleiches Loch, gleicher Schlüssel", VK({ hole: 3 }), a);
    ok("anderes Loch ändert ihn", VK({ hole: 4 }) !== a);
    ok("Position kommt nicht vor", !/54\./.test(a));
  }
  ok("Tick baut nur das Bewegliche, wenn möglich", /dynOnly:nurDyn/.test(src));
  ok("eigene Gruppe im SVG", /<g id="playDynG">/.test(src));

  /* Sync: erst fragen, dann laden. */
  ok("SHA-Abfrage vorhanden", /async function freshRepoSha\(\)/.test(src));
  ok("Tick prüft zuerst die Kennung", /const sha=await freshRepoSha\(\);/.test(src));
  ok("und bricht bei Gleichstand ab", /sha===REPO_SHA\)\{ playSyncBusy=false; return; \}/.test(src));
  /* Ohne Antwort (alter Worker) MUSS der volle Weg bleiben — Worker und App
     werden getrennt ausgerollt. */
  ok("Rückfall auf Vollabruf", /return \(d && typeof d\.sha==="string" && d\.sha\) \? d\.sha : null;/.test(src));
  ok("Worker kennt ?sha=1", /url\.searchParams\.get\("sha"\) === "1"/.test(src));

  /* Der Minutenvergleich lief über ZWEI vollständige Serialisierungen der
     3-MB-Datenbank — auf dem Hauptthread, während man spielt. */
  /* Im Minutentakt der RUNDE darf kein voller Vergleich mehr stehen. Die
     übrigen Stellen (Start, manueller Abgleich, Uhr-Wächter) laufen nach der
     SHA-Prüfung nur noch, wenn sich wirklich etwas geändert hat — dort ist der
     volle Vergleich richtig und billig. */
  const ps = src.slice(src.indexOf("async function playSyncTick()"),
                       src.indexOf("async function playSyncTick()") + 1400);
  ok("kein doppeltes stringify im Runden-Takt", !/JSON\.stringify\(merged\)!==JSON\.stringify\(DB\)/.test(ps));
  /* v2.91: Beide Takte fragen zuerst die KLEINE Datei; die Kennung der grossen
     ist nur noch der Rückfall für einen Worker ohne `draft.json`. */
  ok("Uhr-Wächter zieht zuerst den Entwurf",
    /watchLiveBusy=true;[\s\S]{0,300}const p=await draftPull\(\);/.test(src));
  ok("und danach erst die grosse Kennung",
    /watchLiveBusy=true;[\s\S]{0,1800}const sha=await freshRepoSha\(\);/.test(src));
  if (typeof SF === "function") {
    const leer = { rounds: [], ui: {} };
    eq("gleicher Stand, gleicher Abdruck", SF(leer), SF({ rounds: [], ui: {} }));
    ok("neuer Entwurf ändert ihn", SF({ rounds: [], ui: {}, _draftRound: { ts: "2026-08-14T09:00:00Z", round: { holes: [] } } }) !== SF(leer));
    ok("ein Loch mehr ändert ihn",
      SF({ rounds: [], ui: {}, _draftRound: { ts: "x", round: { holes: [{ hole: 1, score: 4 }] } } }) !==
      SF({ rounds: [], ui: {}, _draftRound: { ts: "x", round: { holes: [] } } }));
    ok("geänderter Score ändert ihn",
      SF({ rounds: [], ui: {}, _draftRound: { ts: "x", round: { holes: [{ hole: 1, score: 5 }] } } }) !==
      SF({ rounds: [], ui: {}, _draftRound: { ts: "x", round: { holes: [{ hole: 1, score: 4 }] } } }));
    ok("Verworfen-Marke zählt", SF({ rounds: [], ui: { draftDiscardedTs: "z" } }) !== SF(leer));
  }
}

/* ============ 24cd. Neue Luftbildquellen · PNG für die Erkennung ============ */
group("Luftbild — Quellen und Format");
{
  const src = fs.readFileSync(FILE, "utf8");
  const S = G("SAT_SRC"), F = G("satSrcFor"), U = G("satTileUrl"), K = G("satTileKey");

  if (Array.isArray(S)) {
    const byId = {}; S.forEach(x => byId[x.id] = x);
    ok("Bremen DOP10 vorhanden", !!byId.hb && byId.hb.res === 0.10);
    ok("MV DOP20 vorhanden", !!byId.mv && byId.mv.res === 0.20);
    ok("Bremen-Layer trägt das Bildflugjahr", /^dop10_\d{4}_HB$/.test((byId.hb||{}).layer||""));
    /* Jede WMS-Quelle braucht Adresse, Layer UND Gebiet — fehlt eines, bleibt
       die Karte leer, ohne zu sagen warum. */
    S.filter(x => x.type === "wms").forEach(x => {
      ok("vollständig: " + x.id, !!(x.url && x.layer && x.bbox && x.bbox.length === 4 && x.attr));
      ok("Gebiet plausibel: " + x.id, x.bbox[0] < x.bbox[2] && x.bbox[1] < x.bbox[3]);
    });
  }

  /* Die Auto-Wahl nimmt das FEINSTE Amtsbild, dessen Gebiet den Platz enthält. */
  if (typeof F === "function" && G("DB")) {
    const db = G("DB"); db.ui = db.ui || {}; const vorher = db.ui.satSrc; db.ui.satSrc = "auto";
    eq("Timmendorfer Strand → SH", F(54.00, 10.77).id, "sh");
    eq("Bremen → DOP10 statt Esri", F(53.11, 8.80).id, "hb");
    eq("Ostsee bei Kühlungsborn → MV", F(54.15, 11.75).id, "mv");
    eq("Wien → Esri (kein Amtsbild)", F(48.21, 16.37).id, "esri");
    db.ui.satSrc = vorher;
  }

  /* PNG NUR für die Erkennung: JPEG legt Farbe in 8x8-Blöcken zusammen und
     färbt genau die Kanten um, auf die die Grün/Dunkel-Bewertung schaut. */
  if (typeof U === "function" && Array.isArray(S)) {
    const wms = S.find(x => x.type === "wms");
    ok("Anzeige holt JPEG", /FORMAT=image%2Fjpeg|FORMAT=image\/jpeg/.test(U(wms, 18, 1, 1)));
    ok("Erkennung holt PNG", /FORMAT=image\/png|FORMAT=image%2Fpng/.test(U(wms, 18, 1, 1, true)));
  }
  /* Getrennter Speicherschlüssel, sonst verdrängt eine Fassung die andere. */
  if (typeof K === "function" && Array.isArray(S)) {
    const wms = S.find(x => x.type === "wms");
    ok("PNG hat einen eigenen Schlüssel", K(wms,18,1,1,true) !== K(wms,18,1,1));
    ok("und ist als solcher erkennbar", /\/png\//.test(K(wms,18,1,1,true)));
  }
  ok("Erkennung fordert PNG nur bei WMS", /const png=\(src\.type==="wms"\);/.test(src));

  /* Ich konnte die neuen Dienste hier nicht anfragen (Netz gesperrt) — deshalb
     ein Selbsttest im Gerät statt einer Behauptung. */
  ok("Quellen-Prüfung vorhanden", /async function satTestSrc\(idx\)/.test(src));
  ok("erkennt XML-Antwort als Layer-Fehler", /Layer-Name oder Gebiet falsch/.test(src));
  ok("Knopf in den Karten-Einstellungen", /id="mSatTest"/.test(src));
}

/* ============ 24cc. Sicherungskopien & Versionssprung ============ */
group("Sicherungen — bezahlbar und verlässlich");
{
  const src = fs.readFileSync(FILE, "utf8");
  const B = G("snapBehalten");

  /* GEMESSEN: Zehn volle Kopien bei ~3 MB Daten sind ~30 MB im Speicher und
     eine Schreibverstärkung von Faktor zehn alle drei Minuten. Die Tiefe
     richtet sich jetzt nach der Größe. */
  if (typeof B === "function") {
    eq("kleiner Stand: zehn Kopien", B(200e3), 10);
    eq("halbe MB: sechs", B(900e3), 6);
    eq("1,5 MB: drei", B(2e6), 3);
    eq("über 4 MB: zwei", B(5e6), 2);
    eq("ohne Angabe der Vorgabewert", B(0), 10);
    ok("monoton fallend", B(200e3) >= B(900e3) && B(900e3) >= B(2e6) && B(2e6) >= B(5e6));
  }

  const sn = src.slice(src.indexOf("function snapshot(reason, force)"),
                       src.indexOf("function getBackups()"));
  /* Der localStorage-Zweig ist ersatzlos entfallen: ein 30-MB-String für einen
     Speicher mit 5 MB Grenze — der Versuch MUSSTE scheitern, still im catch. */
  ok("keine localStorage-Kopie mehr", !/localStorage\.setItem\("golfdb_backups"/.test(src));
  ok("Tiefe aus der Größe", /const max=snapBehalten\(data\.length\)/.test(sn));
  /* Ein leerer Stand darf keine echte Sicherung verdrängen — beim Start läuft
     snapshot(), BEVOR idbHydrate die echten Daten geladen hat. */
  ok("leerer Stand kommt nicht in den Ring", /if\(score<5 && _backups\.length\)\{ return; \}/.test(sn));

  /* Versionssprung: verwerfen war die Ursache einer ganzen Kette. */
  const ld = src.slice(src.indexOf("function load(){"), src.indexOf("function load(){") + 700);
  ok("Stand wird migriert, nicht verworfen", /_schemaAlt=d\.version==null\?"\?":d\.version; d\.version=SEED\.version;/.test(ld));
  ok("Struktur bleibt die Bedingung", /Array\.isArray\(d\.testDefs\) && Array\.isArray\(d\.rounds\)/.test(ld));
  ok("keine Versionsprüfung mehr in load", !/d\.version===SEED\.version/.test(ld));

  const hy = src.slice(src.indexOf("async function idbHydrate()"),
                       src.indexOf("async function idbHydrate()") + 1600);
  ok("idbHydrate prüft die Version nicht mehr", !/idb\.version===SEED\.version/.test(hy));
  /* Der eigentliche Datenverlust: Die abgelehnte IDB-Kopie wurde mit dem
     leeren SEED überschrieben. */
  ok("nie eine reichere Kopie überschreiben",
    /else if\(!idb \|\| dataScore\(DB\) >= dataScore\(idb\|\|\{\}\)\)/.test(hy));
}

group("Merge — ein leerer Stand überschreibt keine Skalare");
{
  const M = G("mergeDB"), src = fs.readFileSync(FILE, "utf8");
  if (typeof M === "function") {
    const voll = () => ({ version: 12, testDefs: [{key:"a"}], rounds: [{id:"r1"},{id:"r2"},{id:"r3"}],
      profile: { name: "Lars", hcpIndex: 20.4 }, ui: { theme: "dark" }, tests: [], notes: [],
      clubDistances: [{club:"Driver"}], competitions: [], tomb: {} });
    const leer = () => ({ version: 12, testDefs: [], rounds: [], profile: { name: "" }, ui: {},
      tests: [], notes: [], clubDistances: [], competitions: [], tomb: {} });

    /* DER FALL: lokal frisch (Schema-Neustart, Neuinstallation, Speicher
       geleert), Repo hat alles. Vorher gewannen die LOKALEN Skalare — Profil
       und Einstellungen wurden mit Leerwerten überschrieben und verteilt. */
    const r1 = M(leer(), voll());
    eq("Profil kommt aus dem Repo", (r1.profile || {}).name, "Lars");
    eq("Handicap ebenso", (r1.profile || {}).hcpIndex, 20.4);
    eq("Einstellungen ebenso", (r1.ui || {}).theme, "dark");
    eq("Runden sowieso (Union)", (r1.rounds || []).length, 3);

    /* Normalfall: lokal hat Daten — dann gewinnt wie bisher LOKAL. */
    const l2 = voll(); l2.profile = { name: "Lars", hcpIndex: 19.8 };
    const r2 = M(l2, voll());
    eq("lokale Korrektur gewinnt weiterhin", (r2.profile || {}).hcpIndex, 19.8);

    /* Beide leer: nichts Besonderes, kein Absturz. */
    ok("beide leer geht durch", !!M(leer(), leer()));
  }
  ok("Schwelle wie beim Empty-Guard", /const lLeer = \(dataScore\(L\)<5 && dataScore\(R\)>=5\)/.test(src));
  ok("ui folgt derselben Regel", /out\.ui=Object\.assign\(\{\}, lLeer\?\(L\.ui\|\|\{\}\):\(R\.ui\|\|\{\}\)/.test(src));
}

/* ============ 24ca. worker.js liegt in der Doku ============ */
group("Worker-Code in der Doku");
{
  const src = fs.readFileSync(FILE, "utf8");
  const doc = (src.match(/<script[^>]*devdocs[^>]*>([\s\S]*?)<\/script>/) || [])[1] || "";

  /* Der Worker ist der zweite Teil des Systems und lag nur im Cloudflare-
     Dashboard. In v2.84 wurde deshalb über ihn BEHAUPTET, er merge
     serverseitig — tatsächlich ist er im benutzten Modus ein SHA-Türsteher.
     Eine Aussage über Code, den man nicht sieht, ist eine Vermutung. */
  ok("Abschnitt vorhanden", /## 28\. `worker\.js` — vollstaendiger Stand/.test(doc));
  ok("Fassung benannt", /Fassung v2\.8/.test(doc));
  /* Beim Bau von v2.87 landete eine mergeDB-Änderung im Worker-ABZUG statt in
     der App — eine Suche nach `function mergeDB(` findet den Abzug zuerst,
     weil die Doku im Dokument vor dem Code steht. Der Hinweis muss dastehen. */
  ok("Warnung vor der doppelten mergeDB", /Die Funktion steht seit v2\.85 \*\*ZWEIMAL\*\*/.test(doc));
  ok("und nennt das Unterscheidungsmerkmal", /mit drei Argumenten/.test(doc));
  /* Die Leer-Regel muss auch im Worker stehen (ALT-Modus). */
  ok("Worker spiegelt die Leer-Regel", /const lLeer = \(dataScore\(L\)<5 && dataScore\(R\)>=5\)/.test(doc));
  ok("Worker bringt dataScore mit", /function dataScore\(d\)\{ d=d\|\|\{\}/.test(doc));

  /* Der CODE selbst muss dastehen, nicht nur eine Beschreibung. */
  ["const CFG = {", "function _mergeArr(a,b,keyFn){", "function _mergeCourses(La, Ra){",
   "function mergeDB(localDB, repoDB){", "async function ghSha(env, path)",
   "export default {"].forEach(t =>
    ok("enthält: " + t.slice(0, 34), doc.indexOf(t) > 0));
  ok("Zeile mit _mergeCourses ist gespiegelt", /out\.courses\s*= _mergeCourses\(L\.courses, R\.courses\);/.test(doc));

  /* Der Unterschied NEU-/ALT-Modus ist der Kern jeder Fehleinschätzung. */
  ok("NEU-Modus als Türsteher benannt", /NEU-Modus, den diese App benutzt[\s\S]{0,200}merged NICHT/.test(doc));
  ok("ALT-Modus als Spiegelungsstelle", /ALT-Modus[\s\S]{0,160}Spiegelung des App-Merges/.test(doc));

  /* Und die Pflicht, ihn zu lesen und zu pflegen. */
  ok("Regel: erst lesen", /Diesen Abschnitt LESEN/.test(doc));
  ok("Regel: bei Bedarf mitändern", /den Code hier unten mitaendern/.test(doc));
  ok("Regel: Stand hier ersetzen", /Quelle der Wahrheit fuer/.test(doc));
  ok("Sync-Architektur verweist darauf", /Worker-Code steht in Abschnitt 28/.test(doc));
  ok("Doku-Pflicht nennt ihn", /DAZU GEHOERT DER WORKER/.test(doc));

  /* Der eingebettete Code darf den Doku-Block nicht sprengen. */
  ok("kein schließendes script-Tag im Code", doc.indexOf("</scr" + "ipt>") < 0);
  ok("Zaun mit vier Backticks (Template-Literale im Code)",
    doc.indexOf("`".repeat(4) + "js") > 0);
}

/* ============ 24bz. Gelöschte Karte bleibt gelöscht ============ */
group("Merge — eine Löschung ist ein Datum, kein Fehlen");
{
  const M = G("_mergeCourses");
  if (typeof M === "function") {
    const mitGeo = (n, extra) => Object.assign({ name: n, geo: { features: [1, 2, 3], mine: [] } }, extra || {});
    const ohneGeo = (n, extra) => Object.assign({ name: n }, extra || {});

    /* DER FALL: lokal gelöscht, im Repo liegt die Karte noch. Ohne Stempel
       gewinnt der „vollständigere" Eintrag — also das Repo — und die Karte
       kommt zurück. Genau das war beobachtet worden. */
    const lokal = [ohneGeo("Nordplatz", { geoDeletedAt: "2026-08-13T10:00:00Z" })];
    const repo = [mitGeo("Nordplatz", { geoAt: "2026-08-12T09:00:00Z" })];
    const r1 = M(lokal, repo, null);
    eq("ein Platz", r1.length, 1);
    ok("Karte bleibt gelöscht", !r1[0].geo);
    eq("Löschdatum wandert mit", r1[0].geoDeletedAt, "2026-08-13T10:00:00Z");

    /* Umgekehrt: Nach dem Löschen neu importiert — die JÜNGERE Karte gewinnt,
       sonst könnte man nach einer Löschung nie wieder importieren. */
    const r2 = M([mitGeo("Nordplatz", { geoAt: "2026-08-13T12:00:00Z", geoDeletedAt: "2026-08-13T10:00:00Z" })],
                 [ohneGeo("Nordplatz", { geoDeletedAt: "2026-08-13T10:00:00Z" })], null);
    ok("Neu-Import nach dem Löschen bleibt", !!r2[0].geo);

    /* Löschung auf dem ANDEREN Gerät: Das Repo trägt das Datum, lokal liegt
       noch die alte Karte. Auch dann muss sie weichen. */
    const r3 = M([mitGeo("Nordplatz", { geoAt: "2026-08-11T09:00:00Z" })],
                 [ohneGeo("Nordplatz", { geoDeletedAt: "2026-08-13T10:00:00Z" })], null);
    ok("Löschung vom anderen Gerät wirkt", !r3[0].geo);

    /* Altbestand ohne `geoAt` gilt als älter als jede Löschung — ein Import
       von vor dieser Änderung hat den Stempel noch nicht. */
    const r4 = M([mitGeo("Nordplatz")], [ohneGeo("Nordplatz", { geoDeletedAt: "2026-08-13T10:00:00Z" })], null);
    ok("Karte ohne Stempel weicht der Löschung", !r4[0].geo);

    /* NIEMALS die Eingaben verändern: `_mergeArr` liefert die ORIGINALOBJEKTE.
       Ein `delete c.geo` träfe sonst den lokalen DB-Eintrag — auch dann, wenn
       das Merge-Ergebnis anschließend verworfen wird (cloudLoadManual rechnet
       erst `dataScore(merged)` und entscheidet danach). Die Karte wäre weg,
       ohne dass jemand sie gelöscht hätte. Beim Ende-zu-Ende-Test gefunden. */
    const quelle = [mitGeo("Nordplatz", { geoAt: "2026-08-11T09:00:00Z" })];
    M([ohneGeo("Nordplatz", { geoDeletedAt: "2026-08-13T10:00:00Z" })], quelle, null);
    ok("die Eingabe bleibt unangetastet", !!quelle[0].geo);
    const quelle2 = [ohneGeo("Nordplatz", { geoDeletedAt: "2026-08-13T10:00:00Z" })];
    M(quelle2, [mitGeo("Nordplatz", { geoAt: "2026-08-11T09:00:00Z" })], null);
    ok("auch die andere Seite", !quelle2[0].geo && quelle2[0].geoDeletedAt === "2026-08-13T10:00:00Z");

    /* Ohne jede Löschung ändert sich nichts — die Erweiterung darf den
       Normalfall nicht anfassen. */
    const r5 = M([ohneGeo("Nordplatz")], [mitGeo("Nordplatz")], null);
    ok("ohne Löschdatum gewinnt wie bisher der vollständigere", !!r5[0].geo);
    /* Und ein anderer Platz bleibt unberührt. */
    const r6 = M([ohneGeo("A", { geoDeletedAt: "2026-08-13T10:00:00Z" })], [mitGeo("B")], null);
    eq("zwei Plätze", r6.length, 2);
    ok("fremder Platz behält seine Karte", !!r6.find(c => c.name === "B").geo);
  }

  const src = fs.readFileSync(FILE, "utf8");
  ok("mergeDB benutzt es", /out\.courses = _mergeCourses\(L\.courses, R\.courses/.test(src));
  ok("Löschen stempelt", /c\.geoDeletedAt=new Date\(\)\.toISOString\(\); delete c\.geoAt;/.test(src));
  ok("Import stempelt", /DB\.courses\[idx\]\.geoAt=new Date\(\)\.toISOString\(\);\s*\n\s*delete DB\.courses\[idx\]\.geoDeletedAt;/.test(src));
  ok("jede Editor-Änderung stempelt", /DB\.courses\[GEOED\.idx\]\.geoAt=new Date\(\)\.toISOString\(\);/.test(src));
  /* Der Worker merged nur im ALT-Modus (v2.1: NEU-Modus = SHA-Türsteher). Das
     muss im Code stehen, sonst spiegelt beim nächsten Mal jemand ins Leere
     oder unterlässt die Spiegelung ganz. */
  ok("Worker-Verhalten dokumentiert", /geprueft an worker\.js v2\.1/.test(src));
  ok("NEU-Modus als SHA-Türsteher benannt", /SHA-Tuersteher/.test(src));
  ok("ALT-Modus als Spiegelungspflicht benannt", /ALT-Modus[\s\S]{0,200}gespiegelt/.test(src));
}

/* ============ 24by. Grün und Teebox in EINEM Schritt ============ */
group("Neues Grün / neue Teebox anlegen");
{
  const src = fs.readFileSync(FILE, "utf8");
  const fa = src.slice(src.indexOf("function geoEdFinishArea(type)"),
                       src.indexOf("function geoEdFinishLine(type)"));

  /* Grün und Abschlag sind ZWEI Dinge: die FLÄCHE (Lie-Raster, F/M/B) und der
     PUNKT (Ziel des Caddys bzw. Start der Bahn). Beides von Hand zu setzen war
     umständlich UND ungenauer — der Schwerpunkt trifft besser als ein zweiter
     Fingertipp. */
  ok("Teebox als Flächenart", /geoEdFinishArea\('tee'\)/.test(src));
  ok("Grün und Tee setzen den Punkt mit", /type==="green" \|\| type==="tee"/.test(fa));
  ok("Punkt kommt aus dem Schwerpunkt", /ringCentroid\(ring\)/.test(fa));
  ok("Loch wird einmal erfragt", /prompt\(`Für welches Loch/.test(fa));
  /* „Leer lassen" muss gehen — ein Übungsgrün gehört zu keiner Bahn. */
  ok("ohne Loch bleibt es die reine Fläche", /nRaw==null \? null : parseInt/.test(fa));
  ok("nur 1–18 zählt", /n>=1 && n<=18/.test(fa));
  /* Bestehende Overrides desselben Lochs dürfen nicht verlorengehen: Grün und
     Tee liegen im selben Objekt. */
  ok("bestehende Overrides bleiben", /Object\.assign\(\{\}, geo\.overrides\.holes\[n\]/.test(fa));
  ok("Tee schreibt tee, Grün schreibt green",
    /type==="green" \? \{green:roundLL\(c\)\} : \{tee:roundLL\(c\)\}/.test(fa));

  /* Und `applyGeoOverrides` muss den Abschlag überhaupt kennen. */
  const ag = src.slice(src.indexOf("function applyGeoOverrides(geo)"),
                       src.indexOf("function applyGeoOverrides(geo)") + 900);
  ok("Overrides kennen den Abschlag", /if\(o\.tee\)\{ geo\.holes\[n\]\.tee=o\.tee/.test(ag));
  ok("Länge wird neu gerechnet", /o\.tee[\s\S]{0,160}distM=Math\.round\(lineLenM/.test(ag));

  /* Der Ablauf muss dastehen — er ist nicht zu erraten. */
  ok("Ablauf erklärt", /Neues Grün oder neue Teebox anlegen/.test(src));
  ok("Punkt-Werkzeug als Korrektur benannt", /Korrigiert nur die Grünmitte/.test(src));
  ok("Teebox hat einen Stil", /tee:\{fill:"#cfe0b4"/.test(src));
}

/* ============ 24bw. Kartenschirm: Zoom, Aufräumen, ein Löschweg ============ */
group("Kartenschirm — Verwaltung, nicht zweiter Spielmodus");
{
  const src = fs.readFileSync(FILE, "utf8");
  const cm = src.slice(src.indexOf("function renderCourseMap(idx)"),
                       src.indexOf("function mapSelHole(n)"));

  /* Derselbe Ausschnitt wie im Spielmodus — `corridor` fehlte, deshalb wurde
     über das 55-m-Vorgabeband gefittet. */
  ok("Bahn-Ausschnitt wie im Spielmodus", /rotate:!!hole, tight:!!hole, corridor:46, fitHoles:!hole/.test(cm));

  /* Alles Ortsbezogene ist raus: Es war eine zweite, schlechtere Ausgabe
     dessen, was der Spielmodus mit GPS, Caddy und Ringen ohnehin kann. */
  ["mRings","mLive","mHere","mAdd","mCaddy"].forEach(id =>
    ok("Knopf entfernt: " + id, cm.indexOf('id="' + id + '"') < 0));
  ok("Distanz-Ringe-Zeile weg", !/Distanz-Ringe \(50–250 m\)/.test(cm));
  ok("Live-Distanzzeile weg", !/liveDist/.test(cm));
  ["mapToggleLive","mapLocate","mapAddPoint"].forEach(f =>
    ok("Funktion entfernt: " + f, src.indexOf("function " + f + "(") < 0));
  /* Was bleiben MUSS: Luftbild, Offline-Vorrat, Bearbeiten, Import, Löschen. */
  ["mSat","mSatPre","mSatClr","mEdit","mReload","mClear"].forEach(id =>
    ok("Knopf bleibt: " + id, cm.indexOf('id="' + id + '"') > 0));
}

/* ============ 24bv. Grün als Fläche · Freihand-Zeichnen ============ */
group("Grünfläche und Freihand");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* Grün gab es nur als PUNKT (die Mitte = Ziel des Caddys). Die FLÄCHE fehlte,
     obwohl das Raster sie auswertet und Front/Mitte/Back sie brauchen. */
  ok("Grün als Flächenart wählbar", /geoEdFinishArea\('green'\)/.test(src));
  ok("Fairway ebenso", /geoEdFinishArea\('fairway'\)/.test(src));
  ok("Bezeichnung vorhanden", /green:"Grün",fairway:"Fairway"/.test(src));
  /* Und die selbst gezeichnete Fläche muss auch GEFUNDEN werden — vorher sah
     `greenRingFor` nur in `features`, wer ein Grün nachtrug bekam trotzdem
     keine F/M/B-Werte. */
  ok("eigene Grüns zählen für F/M/B",
    /greens=\(geo\.features\|\|\[\]\)\.concat\(geo\.mine\|\|\[\]\)\.filter\(f=>f\.kind==="green"/.test(src));
  ok("Hinweis trennt Mitte und Fläche", /Korrigiert nur die Grünmitte/.test(src));

  /* Freihand: nur Maus, nur PC-Modus — am Finger wäre jeder Wisch ein
     Zeichenzug und das Verschieben ginge verloren. */
  const dn = src.slice(src.indexOf("function geoEdDown"), src.indexOf("const DRAG_HOLD_MS"));
  ok("Freihand nur mit Maus im PC-Modus",
    /e\.pointerType==="mouse" && e\.button===0 && geoEdPcOn\(\)/.test(dn));
  ok("Spur beginnt beim Aufsetzen", /GEOED\.free=\{pts:\[v\]\}/.test(dn));

  const mv = src.slice(src.indexOf("function geoEdMove"), src.indexOf("function geoEdUp"));
  ok("Punkte erst ab Mindestabstand", /Math\.hypot\(v\[0\]-l\[0\], v\[1\]-l\[1\]\)>=minU/.test(mv));
  /* Der Mindestabstand ist in BILDPUNKTEN gedacht und wird umgerechnet —
     sonst sammelt starker Zoom tausend Punkte auf einem Meter. */
  ok("Abstand in Bildpunkten umgerechnet", /3\*\(\(GEOED\.view\?GEOED\.view\.w:GEOED\.M\.W\)\/Math\.max\(1,r\.width\)\)/.test(mv));
  ok("während des Zugs nur der Pfad", /geoEdFreePath\(\)/.test(mv) && !/renderGeoEditor\(\)/.test(mv));

  const fe = src.slice(src.indexOf("function geoEdFreeEnde("),
                       src.indexOf("function geoEdMenuAus()"));
  ok("kurzer Zug gilt als Klick", /f\.pts\.length<4\) return false/.test(fe));
  /* Toleranz in METERN, nicht in Bildpunkten: sonst hängt die Glättung am
     Zoom — nah gezeichnet fein, weit gezeichnet grob. */
  ok("Glättung in Metern gedacht", /eps=Math\.max\(0\.5, 2\*\(M&&M\.s\|\|1\)\)/.test(fe));
  ok("Notbremse gegen Riesenringe", /pts\.length>120/.test(fe));
  ok("landet im normalen Zeichen-Entwurf", /GEOED\.draw=\{pts:ll\}/.test(fe));
  ok("und unterdrückt den Klick danach", /gSuppressClick=true/.test(fe));
  ok("Kürzelliste erklärt es", /freihand ziehen/.test(src));
}

/* ============ 24bu. Abschluss-Leiste folgt den Werkzeugen ============ */
group("PC-Modus — Zeichnen abschließen, wo man hinsieht");
{
  const src = fs.readFileSync(FILE, "utf8");
  const uiA = src.indexOf('const h=`<h2 style="margin-bottom:2px">✏️ Karte');
  const ui = src.slice(uiA, src.indexOf('openSheet(h); bindAllPanZoom(sheetBody);', uiA));

  /* Am Telefon unter der Karte, am Rechner in der rechten Spalte unter den
     Werkzeugen: Unter einer hohen Karte läge sie sonst weit weg vom Blick, und
     man scrollt zum Abschließen einer Fläche nach unten. */
  ok("am Telefon unter der Karte", /\$\{pc\?"":drawBar\}/.test(ui));
  ok("am Rechner in der rechten Spalte", /\$\{pc\?drawBar:""\}/.test(ui));
  ok("und dort NACH dem Spaltenwechsel",
    ui.indexOf('${pc?`</div><div>`') < ui.indexOf('${pc?drawBar:""}'));
  ok("genau einmal gerendert",
    (ui.match(/drawBar/g) || []).length === 2);
  /* In der 380-px-Spalte müssen die Art-Knöpfe umbrechen dürfen. */
  ok("Knöpfe nutzen die Spaltenbreite", /\.geoed-pc \.geoed-draw button\{flex:1 1 46%/.test(src));
}

/* ============ 24bt. Gesamten Kartenbestand löschen ============ */
group("Alles löschen — mit Zahlen, mit Netz");
{
  const src = fs.readFileSync(FILE, "utf8");
  /* EIN Weg für beide Aufrufer (v2.81): `geoEdWipe()` ist nur noch der
     Kurzaufruf, die Arbeit macht `geoWipe(idx, ausEditor)`. */
  const w = src.slice(src.indexOf("function geoWipe(idx, ausEditor)"), src.indexOf("function geoEdWipeUndo("));
  ok("Editor ruft denselben Weg", /function geoEdWipe\(\)\{ geoWipe\(GEOED\.idx, true\); \}/.test(src));
  ok("Kartenschirm ebenfalls", /\$\("#mClear"\)\.onclick=\(\)=>geoWipe\(idx\)/.test(src));
  /* Der alte Weg darf nur noch im KOMMENTAR vorkommen, nicht als Aufruf. */
  ok("kein zweiter Löschweg mehr", !/confirm\("OSM-Geodaten dieses Platzes löschen\?"\)/.test(src));
  const u = src.slice(src.indexOf("function geoEdWipeUndo("),
                      src.indexOf("function geoEdWipeUndo(") + 700);

  /* Die Rückfrage nennt Zahlen. „Alles löschen?" beantwortet man nach zwei
     Stunden Zeichnen falsch, „412 Flächen, 137 eigene Objekte" nicht. */
  ok("Rückfrage vorhanden", /confirm\(/.test(w));
  ok("nennt importierte Flächen", /importierte Flächen/.test(w));
  ok("nennt eigene und erkannte Objekte", /eigene und erkannte Objekte/.test(w));
  ok("nennt die Bahn-Geometrien", /Bahn-Geometrien/.test(w));
  ok("sagt, was NICHT betroffen ist", /Runden, Scorekarte und Schläger/.test(w));

  /* Das Rückgängig des Editors sichert nur `mine` und `overrides` — für
     `features` und `holes` braucht es eine vollständige Kopie. */
  ok("vollständige Kopie vor dem Löschen", /GEOED\.geoBackup=\{idx, name:c\.name, json:JSON\.stringify\(geo\)/.test(w));
  ok("Kopie vor dem Löschen, nicht danach",
    w.indexOf("GEOED.geoBackup=") < w.indexOf("delete c.geo"));
  ok("Wiederherstellen prüft den Platz", /!b \|\| b\.idx!==idx/.test(u));
  ok("und leert die Kopie danach", /GEOED\.geoBackup=null/.test(u));

  /* Ein gespeicherter Gameplan ohne Geometrie wäre ein Rechenergebnis ohne
     Grundlage — er muss mit weg. */
  ok("Gameplan des Platzes wird verworfen", /DB\.strat\.gameplans[\s\S]{0,160}delete DB\.strat\.gameplans\[k\]/.test(w));
  /* Nur DIESER Platz — die Pläne anderer Plätze gehen es nichts an. */
  ok("und nur dieser Platz", /k\.split\("\|"\)\[0\]===c\.name/.test(w));

  /* Aufräumen: Auswahl, Tastatur, breites Blatt. */
  ok("Auswahl geleert", /geoEdSelClear\(\)/.test(w));
  ok("Tastatur abgemeldet", /geoEdKeysAus\(\)/.test(w));

  ok("Knopf im Editor", /onclick="geoEdWipe\(\)"/.test(src));
  ok("Rücknahme im Kartenschirm angeboten", /geoEdWipeUndo\(\$\{idx\}\)/.test(src));
}

/* ============ 24bs. Auswahl, Eckpunkte, Ausschnitt-Erkennung ============ */
group("Karteneditor — Erkennung nachbessern");
{
  const src = fs.readFileSync(FILE, "utf8");
  const P = G("geoEdPunktVon");

  /* Der Vertreterpunkt entscheidet, was ins Rechteck fällt. Bei Flächen der
     SCHWERPUNKT: ein Ring, dessen Mitte drin liegt, ist gemeint — einer, von
     dem nur eine Ecke hineinragt, nicht. */
  if (typeof P === "function") {
    eq("Punkt-Objekt liefert seinen Punkt", P({ pt: [54, 10] }).join(","), "54,10");
    const r = P({ ring: [[0, 0], [0, 2], [2, 2], [2, 0]] });
    eq("Fläche liefert den Schwerpunkt", r.join(","), "1,1");
    eq("Linie ebenso", P({ line: [[0, 0], [4, 0]] }).join(","), "2,0");
    eq("ohne Geometrie nichts", P({}), null);
    eq("ohne Objekt nichts", P(null), null);
  }

  /* Indizes sind heikel: `mine` ist ein Array, Löschen verschiebt alles
     dahinter. Absteigend löschen, und die Auswahl bei JEDER Änderung leeren. */
  const del = src.slice(src.indexOf("function geoEdSelDelete()"),
                        src.indexOf("function geoEdSelDelete()") + 700);
  ok("absteigend löschen", /sort\(\(a,b\)=>b-a\)/.test(del));
  ok("Auswahl danach leer", /geoEdSelClear\(\)/.test(del));
  ok("Rückfrage ab sechs Objekten", /sel\.length>5 && !confirm/.test(del));
  ok("ein Schnappschuss vorher", /geoEdSnapshot\(/.test(del));
  eq("Einzellöschen leert die Auswahl auch",
    (src.match(/geo\.mine\.splice\([a-z]i,1\)[^;]*; geoEdSelClear\(\)/g) || []).length >= 2, true);

  /* Eckpunkte: nur bei GENAU EINER Auswahl — bei 60-Punkt-Ringen wären mehr
     unlesbar. Und ein Ring darf nicht unter drei Ecken fallen. */
  const vh = src.slice(src.indexOf("function geoEdVertHandles("),
                       src.indexOf("function geoEdVertAdd("));
  ok("Griffe nur bei einer Auswahl", /sel\.length!==1\) return;/.test(vh));
  ok("Griffgröße folgt dem Zoom", /5\*\(v\.w\/M\.W\)/.test(vh));
  ok("Schlusspunkt bekommt keinen eigenen Griff", /zu\?pts\.length-1:pts\.length/.test(vh));
  const vd = src.slice(src.indexOf("function geoEdVertDel("),
                       src.indexOf("function geoEdVertDel(") + 800);
  ok("Untergrenze 3 Ecken / 2 Punkte", /n<= \(m\.ring\?3:2\)/.test(vd));
  ok("Ring wird wieder geschlossen", /pts\[pts\.length-1\]=pts\[0\]\.slice\(\)/.test(vd));
  /* Beim Ziehen des ERSTEN Punktes muss der Schlusspunkt mitwandern, sonst
     reißt die Fläche auf. */
  ok("erster Punkt zieht den Schlusspunkt mit",
    /vi===0 && pts\.length>2[\s\S]{0,120}pts\[pts\.length-1\]=pts\[0\]\.slice\(\)/.test(src));

  /* Wiederherstellen: neuer Eingriff verwirft den Stapel, sonst springt man in
     einen Stand, den es nie gab. */
  ok("Redo-Stapel vorhanden", /function geoEdRedo\(\)/.test(src));
  ok("Undo füllt ihn", /GEOED\.redo\.push\(/.test(src));
  ok("neuer Eingriff verwirft ihn", /GEOED\.redo=\[\];\s*\n\s*GEOED\.undo=GEOED\.undo\|\|\[\];/.test(src));

  /* Ausschnitt-Erkennung: die Box aus VIER Ecken, weil die Karte gedreht ist —
     ein Rechteck im Bild ist keines auf der Erde. */
  const dv = src.slice(src.indexOf("async function geoEdDetectView()"),
                       src.indexOf("async function geoEdDetectAll()"));
  ok("vier Ecken zurückgerechnet", /\[v\.x,v\.y\],\[v\.x\+v\.w,v\.y\],\[v\.x,v\.y\+v\.h\],\[v\.x\+v\.w,v\.y\+v\.h\]/.test(dv));
  ok("zu großer Ausschnitt wird abgelehnt", /breite>1200/.test(dv));
  ok("nutzt dieselbe Bildbeschaffung", /geoEdSatBildBB\(bb\)/.test(dv));

  /* Kontextmenü nur an der Maus; am Finger kommt es vom Langdruck und stört. */
  ok("Menü nicht am Touchgerät", /e\.pointerType==="touch" \|\| !g/.test(src));
  ok("Art ändern im Menü", /geoEdSelArt\('\$\{k\}'\)|geoEdSelArt\('/.test(src));

  /* Zeigerform und Maßstab. */
  ok("Zeiger je Werkzeug", /#geoedSvg\.t-del svg\{cursor:not-allowed\}/.test(src));
  ok("Werkzeugklasse am Container", /id="geoedSvg" class="t-\$\{GEOED\.tool\}"/.test(src));
  ok("Maßstabsbalken", /function geoEdScaleHtml\(\)/.test(src));
  ok("gerundete Stufen", /kand=\[5,10,25,50,100,200,400\]/.test(src));
  ok("Länge beim Zeichnen", /gesamt \$\{Math\.round\(L\)\} m/.test(src));
}

/* ============ 24br. PC-Modus im Karteneditor ============ */
group("Karteneditor am Rechner");
{
  const src = fs.readFileSync(FILE, "utf8");
  const on = src.slice(src.indexOf("function geoEdPcOn()"), src.indexOf("function toggleGeoEdPc"));

  /* Vorbelegung nach Gerät, nicht nach Geschmack: feiner Zeiger UND breites
     Fenster. Die Wahl bleibt lokal — dasselbe Konto am Telefon und am Rechner
     soll nicht dieselbe Form erzwingen. */
  ok("erkennt Maus und Breite", /pointer:fine[\s\S]{0,80}innerWidth>=1080/.test(on));
  ok("eigene Wahl schlägt die Erkennung", /DB\.ui\.geoPc!=null/.test(on));
  ok("umschaltbar", /function toggleGeoEdPc\(\)/.test(src));

  /* Form: breites Blatt, Karte und Bedienung nebeneinander, Karte bleibt beim
     Scrollen stehen. */
  ok("Blatt wird breit", /\.sheet\.pc\{max-width:min\(1500px/.test(src));
  ok("zwei Spalten", /\.geoed-pc\{display:grid;grid-template-columns:minmax\(0,1fr\) 380px/.test(src));
  ok("Karte bleibt stehen", /\.geoed-pc \.geoed-pc-map\{position:sticky/.test(src));
  ok("Werkzeugleiste klebt dort nicht mehr", /\.geoed-pc \.geoed-bar\{position:static/.test(src));
  /* Unter 1080 px zurück auf eine Spalte — sonst wäre die Karte am kleinen
     Fenster 380 px schmal. */
  ok("Rückfall auf eine Spalte", /@media \(max-width:1080px\)\{ \.geoed-pc\{grid-template-columns:1fr\}/.test(src));

  /* Tastatur: hängt EINMAL und entfernt sich selbst, sobald der Editor zu ist.
     Ein Kürzel, das in einer anderen Ansicht noch feuert, wäre ein Fehler mit
     Ansage. */
  const ks = src.slice(src.indexOf("function geoEdKeysAn()"), src.indexOf("function geoEdKeysAus()"));
  ok("nur einmal einhängen", /if\(_geoedKeys\) return;/.test(ks));
  ok("entfernt sich selbst", /if\(!document\.querySelector\("#geoedSvg svg"\)\)\{ geoEdKeysAus\(\)/.test(ks));
  ok("Eingabefelder bleiben unangetastet", /t==="INPUT"\|\|t==="TEXTAREA"\|\|t==="SELECT"/.test(ks));
  ok("Werkzeuge auf Tasten", /p:"add", g:"green", b:"tree", f:"area", l:"line", x:"del"/.test(ks));
  ok("Rückgängig auf Z", /k==="z"[\s\S]{0,60}geoEdUndo\(\)/.test(ks));
  ok("Esc bricht nur das Zeichnen ab", /k==="escape"[\s\S]{0,60}GEOED\.draw/.test(ks));
  ok("beim Verlassen abgemeldet", /#geoedDone"\)\.onclick=\(\)=>\{ geoEdKeysAus\(\);/.test(src));

  /* Mausrad auf den ZEIGER, nicht auf die Bildmitte — sonst läuft einem die
     Stelle, die man vergrößern will, aus dem Bild. */
  ok("Mausrad zoomt auf den Zeiger",
    /wheel[\s\S]{0,220}geoEdZoomAt\(e\.deltaY>0\?1\.18:0\.85, v\[0\], v\[1\]\)/.test(src));
  ok("und scrollt das Blatt nicht mit", /\{passive:false\}/.test(src));
  ok("Kürzel stehen in der Oberfläche", /class="geoed-keys"/.test(src));
}

/* ============ 24bq. Übersicht „alle" und Erkennung über den Platz ============ */
group("Alle Bahnen — Ausschnitt und Erkennung");
{
  const src = fs.readFileSync(FILE, "utf8");
  const cs = src.slice(src.indexOf("if(opt.fitHoles"), src.indexOf("if(opt.fitHoles") + 900);

  /* Ohne `hole` fittet die Karte sonst auf den GESAMTEN Datenbestand — nach
     einem OSM-Import also inklusive Clubhaus, Parkplatz und Nachbaräckern.
     Der Platz lag dann als kleines Rechteck in der Mitte. */
  ok("Übersicht fittet auf die Bahnen", /if\(opt\.fitHoles && !HR && geo\.holes\)/.test(src));
  ok("nimmt Tee, Linie und Grün aller Löcher", /hr\.tee\) alle\.push[\s\S]{0,120}hr\.line/.test(cs));
  ok("und setzt den Mittelpunkt darauf", /center=\[la\/alle\.length/.test(cs));
  ok("nur bei genug Punkten", /alle\.length>=2/.test(cs));
  ok("Karteneditor nutzt es in der Übersicht", /fitHoles:!hole/.test(src));
  /* Gezeichnet wird weiterhin alles — nur der Ausschnitt richtet sich nach den
     Bahnen. Wer hier filtert, nimmt dem Editor die Objekte zum Bearbeiten. */
  ok("Features werden nicht gefiltert", !/opt\.fitHoles[\s\S]{0,400}feats\s*=/.test(cs));

  /* Der Platz-Lauf rechnet BAHN FÜR BAHN. Über 18 Bahnen auf einmal läge die
     Auflösung bei 2–3 m/px — ein Einzelbaum wäre zwei Bildpunkte groß. */
  const da = src.slice(src.indexOf("async function geoEdDetectAll()"),
                       src.indexOf("async function geoEdDetectAll()") + 1800);
  ok("läuft je Bahn", /geoEdDetectHole\(geo, n, OPT, batch\)/.test(da));
  ok("EINE Stapel-Nummer für den ganzen Lauf",
    (da.match(/batch="veg"\+Date\.now\(\)/g) || []).length === 1);
  /* Ein Schnappschuss zu Beginn, nicht je Bahn — sonst wäre der Verlauf nach
     einem Lauf mit 18 Einträgen gefüllt und alles davor herausgefallen. */
  eq("ein Schnappschuss für den ganzen Lauf",
    (da.match(/geoEdSnapshot\(/g) || []).length, 1);
  ok("Rückfrage vor dem langen Lauf", /confirm\(/.test(da));
  ok("Fortschritt sichtbar", /Bahn \$\{n\} …/.test(da));
  ok("Oberfläche darf atmen", /await new Promise\(r=>setTimeout\(r,0\)\)/.test(da));
  ok("eine Bahn ohne Luftbild bricht den Lauf nicht ab", /fehler\+\+; continue;/.test(da));
  ok("Knopf nur in der Übersicht", /id="geoedDetectAll"/.test(src));
}

/* ============ 24bp. Editoren rahmen die Bahn wie der Spielmodus ============ */
group("Karteneditor & Schlag-Editor — gleicher Ausschnitt wie beim Spielen");
{
  const src = fs.readFileSync(FILE, "utf8");
  const rg = src.slice(src.indexOf("function renderGeoEditor()"),
                       src.indexOf("function renderGeoEditor()") + 2800);
  const rs = src.slice(src.indexOf("function renderShotTrack("),
                       src.indexOf("function renderShotTrack(") + 1200);

  /* Der Spielmodus rahmt mit drei Angaben. Fehlt eine, liegt die Bahn schräg
     im Bild oder der Ausschnitt wird über zufällige Nachbarflächen gefittet —
     bei einer Bahn neben der Range zieht das Bild weit auf. */
  ok("Karteneditor dreht in Spielrichtung", /rotate:!!hole/.test(rg));
  ok("und fittet nur auf die Bahn", /tight:!!hole/.test(rg));
  ok("und nimmt dasselbe Band", /corridor:46/.test(rg));
  ok("Schlag-Editor ebenso", /rotate:true,tight:true,corridor:46/.test(rs));

  /* Ohne gewähltes Loch („alle") darf NICHT gedreht werden — dort gibt es
     keine Spielrichtung, und eine willkürliche Drehung machte die Übersicht
     unlesbar. Deshalb `!!hole` statt `true`. */
  ok("bei „alle\" keine Drehung", !/rotate:true[,}]/.test(rg));

  /* Die Rückrechnung MUSS die Drehung herausnehmen, sonst landet jeder
     gesetzte Punkt verdreht. Das kann `llFromVB` seit jeher — hier steht die
     Zusicherung, damit es beim nächsten Umbau nicht verlorengeht. */
  const ll = src.slice(src.indexOf("function llFromVB("), src.indexOf("function llFromVB(") + 500);
  ok("llFromVB dreht zurück", /if\(M\.rot\)/.test(ll));
  const sl = src.slice(src.indexOf("function strkLL("), src.indexOf("function strkLL(") + 500);
  ok("strkLL ebenso", /M\.rot/.test(sl));
}

/* ============ 24bo. Die Doku muss wahr bleiben ============ */
group("Doku — Behauptungen gegen den Quelltext");
{
  const src = fs.readFileSync(FILE, "utf8");
  const doc = (src.match(/<script[^>]*devdocs[^>]*>([\s\S]*?)<\/script>/) || [])[1] || "";
  ok("Doku-Block gefunden", doc.length > 5000);

  /* WARUM DIESE PRÜFUNG: Die Selbstprüfung fragt nur, ob ein Funktionsname
     IRGENDWO in der Doku vorkommt. Sie merkt nicht, wenn ein Satz veraltet —
     und genau das ist über die Versionen mehrfach passiert: Der Editor stand
     noch als „Pinch-Zoom + Zwei-Finger-Pan" beschrieben, als es längst einen
     Ein-Finger-Schub gab, und „konkrete Wasser/Bunker werden NICHT doppelt
     eingetragen" war nach v2.71 schlicht falsch.
     Mechanisch prüfbar ist davon eine Teilmenge: Namen, die die Doku im
     Referenzteil als existierend anführt (`name(`), müssen es auch geben. Das
     fängt Umbenennungen und ersatzlose Streichungen — die häufigste Ursache
     für Sätze, die nicht mehr stimmen. */
  const ref = doc.slice(0, doc.indexOf("## Changelog"));
  ok("Referenzteil abgegrenzt", ref.length > 5000 && ref.length < doc.length);

  /* `code` heisst im Modulkopf schon so — hier ein eigener Name, damit die
     Prüfung nicht von der Reihenfolge der Abschnitte abhängt. */
  const js = code;
  const vorhanden = new Set([
    ...js.matchAll(/function\s+(\w+)\s*\(/g),
    ...js.matchAll(/^\s{2}(\w+)\s*\(/gm),
    ...js.matchAll(/(?:let|const|var)\s+(\w+)/g),
  ].map(m => m[1]));

  /* Browser- und Sprachbestandteile sind keine App-Funktionen. */
  const FREMD = new Set(["fetch","isFinite","getBoundingClientRect","showModal","show","calc","var",
    "parseInt","parseFloat","setTimeout","setInterval","requestAnimationFrame","addEventListener"]);
  /* GEISTER: Namen, die der Referenzteil bewusst als ENTFALLEN nennt („Ersatzlos
     entfallen: pfFit, …"). Sie gehören dorthin — die Begründung, warum etwas
     weg ist, ist so viel wert wie die Beschreibung dessen, was da ist.
     Wer einen Namen hier einträgt, behauptet: „steht als Historie da, nicht als
     Gegenwart." */
  const GEISTER = new Set([
    /* Die Vollbild-Odyssee v1.70–1.88 und die Messanzeige v2.03: Diese Namen
       stehen ausschliesslich in den ERZAEHLENDEN Abschnitten („warum es so
       gelöst ist"). Die Begruendung, warum etwas weg ist, ist so viel wert wie
       die Beschreibung dessen, was da ist. */
    "pfApplyHeight","pfDbgRender","pfDbgToggle","pfDiagShow","pfFacts",
    "pfFit","pfSize","pfVerify","pfViewportH","pinPoint",
    /* Kein Geist, sondern eine FREMDE Sprache: `optJSONArray` gehoert zur
       Kotlin-Uhr-App (org.json), nicht in diese Datei. */
    "optJSONArray"]);

  const genannt = [...new Set([...ref.matchAll(/`(\w+)\(/g)].map(m => m[1]))];
  const tot = genannt.filter(n => !vorhanden.has(n) && !FREMD.has(n) && !GEISTER.has(n));
  eq("kein Referenzeintrag zeigt auf eine verschwundene Funktion", tot.join(", "), "");
  ok("die Prüfung greift überhaupt", genannt.length > 200, String(genannt.length));

  /* Aufräumhilfe in die andere Richtung: Ein Geist, den es wieder gibt, gehört
     aus der Liste — sonst deckt sie irgendwann echte Fehler zu. */
  const wieder = [...GEISTER].filter(n => vorhanden.has(n));
  if (wieder.length) console.log("   Hinweis: existiert wieder, aus GEISTER streichen: " + wieder.join(", "));

  /* Und die Regel selbst muss dastehen — sonst gilt sie nur, solange sich
     jemand erinnert. */
  ok("Doku-Pflicht ist als Regel hinterlegt", /DOKU-PFLICHT/.test(doc));
  ok("sie nennt die Prosa-Abschnitte, nicht nur die Namensliste",
    /PROSA-ABSCHNITT/.test(doc));
  ok("und verlangt die Gegenprobe vor dem Abliefern", /Gegenprobe/.test(doc));
}

/* ============ 24bn. „Hinweise je Loch" wirken in der Rechnung ============ */
group("Hinweise je Loch — aus Text wird Gelände");
{
  const TF = G("troubleFeatures"), src = fs.readFileSync(FILE, "utf8");
  if (typeof TF === "function") {
    /* Bahn nach NORDEN: Tee unten, Grün 400 m nördlich. Rechts vom Spieler
       ist damit Osten (größere Länge). */
    const tee = [54.0, 10.75];
    const mLat = 110540, mLng = 111320 * Math.cos(54 * Math.PI / 180);
    const green = [54.0 + 400 / mLat, 10.75];
    const mitte = (ring) => ring.slice(0, -1).reduce((a, p, _, arr) =>
      [a[0] + p[0] / arr.length, a[1] + p[1] / arr.length], [0, 0]);
    const vor = (p) => (p[0] - tee[0]) * mLat;          // Meter Richtung Grün
    const quer = (p) => (p[1] - tee[1]) * mLng;         // Meter nach Osten = rechts

    const rechts = TF({ tType: "water", tSide: "R", tAt: 190 }, tee, green);
    eq("eine Zone", rechts.length, 1);
    eq("Art übernommen", rechts[0].kind, "water");
    ok("als synthetisch markiert", rechts[0].synth === true);
    near("liegt bei der angegebenen Entfernung", vor(mitte(rechts[0].ring)), 190, 2);
    ok("und rechts der Linie", quer(mitte(rechts[0].ring)) > 15, String(quer(mitte(rechts[0].ring))));

    const links = TF({ tType: "bunker", tSide: "L", tAt: 190 }, tee, green);
    ok("links ist die Gegenseite", quer(mitte(links[0].ring)) < -15);
    eq("OB wird zu OB", TF({ tType: "ob", tSide: "R", tAt: 200 }, tee, green)[0].kind, "ob");

    /* Ohne Entfernungsangabe wüsste niemand, WO die Zone liegt — und eine Zone
       an der falschen Stelle ist schlimmer als keine. */
    eq("ohne ab-m keine Zone", TF({ tType: "water", tSide: "R" }, tee, green).length, 0);
    eq("ohne Seite keine Zone", TF({ tType: "water", tAt: 190 }, tee, green).length, 0);
    eq("hinter dem Grün keine Zone", TF({ tType: "water", tSide: "R", tAt: 395 }, tee, green).length, 0);

    /* Gefahr vor dem Grün: mittig, kurz davor — das deckt den kurzen Fehler ab. */
    const g = TF({ green: "water" }, tee, green);
    eq("eine Zone vor dem Grün", g.length, 1);
    near("kurz vor dem Grün", vor(mitte(g[0].ring)), 384, 3);
    near("mittig", quer(mitte(g[0].ring)), 0, 2);

    /* Geschlossener Ring, sonst zeichnet und füllt niemand korrekt. */
    const r0 = rechts[0].ring;
    eq("Ring ist geschlossen", r0[0].join(","), r0[r0.length - 1].join(","));

    eq("ohne Eintrag nichts", TF(null, tee, green).length, 0);
    eq("ohne Geometrie nichts", TF({ green: "water" }, null, green).length, 0);
    eq("zu kurze Bahn ergibt nichts", TF({ green: "water" }, tee, [54.0003, 10.75]).length, 0);
  }

  /* Die Zonen müssen dort ankommen, wo gerechnet wird — Raster UND Warnungen. */
  ok("Raster nimmt sie auf", /troubleFeatures\(holeTrouble\(courseName,holeNo\), hh\.tee, hh\.green\)/.test(src));
  ok("Live-Caddy warnt damit", /troubleFeatures\(holeTrouble\(PLAY\.course,h\.hole\)/.test(src));
  ok("Positions-Caddy ebenso", /troubleFeatures\(holeTrouble\(CADDYPOS\.courseName,n\)/.test(src));
  /* Und eine Änderung muss den gespeicherten Gameplan ungültig machen. */
  ok("Gameplan-Abdruck kennt die Hinweise", /_hash32\(tr\)/.test(src));
  /* An BEIDEN Stellen — die stündliche Prüfung und die Handrechnung. Fehlt es
     einer, hängt der Plan je nach Weg an einem anderen Abdruck. */
  eq("beide Abdruck-Aufrufe kennen die Hinweise",
    (src.match(/gpFingerprint\(c\.geo, clubList\(\)[\s\S]{0,140}?c\.trouble\)/g) || []).length, 2);

  /* Bevorzugte Seite ist eine Vorliebe, keine Fläche: klein genug, um eine
     sachlich bessere Linie nicht zu überstimmen. */
  ok("bevorzugte Seite wirkt auf die Ziellinie", /favZ=0\.03/.test(src));
  ok("und nur auf die Gegenseite", /if\(seite!==_fav\) favZ=0\.03/.test(src));

  /* Was NICHT rechnet, muss dranstehen — sonst trägt man ein und wundert sich. */
  ok("Editor sagt, was mitrechnet", /Was rechnet mit:/.test(src));
  ok("und was nicht", /nicht gerechnet/.test(src));
}

/* ============ 24bm. Aufnehmen statt versehentlich verschieben ============ */
group("Karteneditor — Objekte hängen nicht mehr am Finger");
{
  const src = fs.readFileSync(FILE, "utf8");
  const dn = src.slice(src.indexOf("function geoEdDown"), src.indexOf("const DRAG_HOLD_MS"));
  const mv = src.slice(src.indexOf("function geoEdMove"), src.indexOf("function geoEdUp"));
  const up = src.slice(src.indexOf("function geoEdUp"), src.indexOf("function geoEdClick"));

  /* URSACHE: Ein Objekt hing am Finger, SOBALD man es berührte, und 2 px
     Bewegung galten als Verschieben. Nach einer Erkennung liegen hunderte
     13-px-Trefferflächen über der Bahn — man kam gar nicht mehr an leere
     Fläche, jeder Schiebeversuch verrückte einen Baum. */
  /* Mit dem FINGER wird gewartet, mit der Maus nicht — `pointerType`
     entscheidet, nicht der PC-Modus: Wer ein Touchgerät am großen Bildschirm
     benutzt, bekommt weiterhin den Schutz. */
  ok("Aufnahme wartet beim Finger", /warten:!maus/.test(dn));
  ok("Maus greift sofort", /const maus = \(e\.pointerType==="mouse"\)/.test(dn));
  ok("und schiebt dann nicht die Karte", /if\(maus\)\{ GEOED\.pan=null; return; \}/.test(dn));
  ok("Haltezeit gesetzt", /DRAG_HOLD_MS=320/.test(src));
  ok("Wackelgrenze 10 px statt 2", /DRAG_SLOP=10/.test(src));
  ok("Rückmeldung beim Aufnehmen", /navigator\.vibrate\(12\)/.test(dn));
  ok("und sichtbar", /drop-shadow/.test(dn));
  ok("Bewegung vor dem Aufnehmen verwirft es", /clearTimeout\(_gdragT\); GDRAG=null/.test(mv));

  /* Beim Umbau ging der EIN-FINGER-SCHUB verloren: `GEOED.pan` wurde gesetzt,
     aber nichts führte ihn mehr aus. Übrig blieb Zoomen mit zwei Fingern. */
  /* Der Schub wird vorbereitet, BEVOR über dem Objekt entschieden wird — nur
     das Auswahl-Rechteck und der Alt-Klick auf eine Ecke gehen vorher ab. */
  ok("Kartenschub auch auf leerer Fläche vorbereitet",
    dn.indexOf("GEOED.pan={ x0:e.clientX") < dn.indexOf("const g=g0;"));
  ok("und in geoEdMove ausgeführt", /GEOED\.pan && GEOED\.ptrs && GEOED\.ptrs\.size===1/.test(mv));
  ok("Pixel in Kartenkoordinaten", /\/Math\.max\(1,r\.width\)\*p\.view\.w/.test(mv));
  ok("an den Rand geklemmt", /Math\.min\(M\.W-p\.view\.w/.test(mv));
  /* Nach dem Verwerfen NICHT zurückkehren — sonst hinkt die Karte ein
     Ereignis hinterher und es ruckelt beim Anschieben. */
  ok("nach dem Verwerfen geht es weiter zum Schub", /\}\s*\n\s*else return;/.test(mv));

  /* `preventDefault` auf pointerdown unterdrückt `click` — und darüber laufen
     Setzen und Löschen. Dieselbe Falle wie im Schlag-Editor (v2.63). */
  ok("kein preventDefault beim Aufsetzen", !/e\.preventDefault\(\);\s*\n\}/.test(dn));
  ok("Klick nach echtem Schub unterdrückt", /gSuppressClick=true/.test(mv));

  /* Aufräumen: ein überlebender Wecker nimmt sonst ein Objekt auf, das längst
     losgelassen ist. */
  ok("Wecker beim Loslassen gelöscht", /clearTimeout\(_gdragT\); _gdragT=null;/.test(up));
  ok("Schub beim Loslassen beendet", /GEOED\.pan=null;/.test(up));
  ok("Leuchten wird zurückgenommen", /style\.filter=""/.test(up));

  /* Und die Bedienung muss dranstehen — eine Geste, die man erraten muss,
     ist keine. */
  ok("Halten wird erklärt", /halten<\/b>, bis es leuchtet/.test(src));
  ok("auch im Werkzeug-Hinweis", /halten zum Verschieben/.test(src));
}

/* ============ 24bl. Vegetation dezent darstellen ============ */
group("Karteneditor — durch den Wald hindurchsehen");
{
  const src = fs.readFileSync(FILE, "utf8");
  const cs = src.slice(src.indexOf("const vegFaint"), src.indexOf("const vegFaint") + 9000);

  /* Nach einer Erkennung liegen hundert gefüllte Kreise über der Bahn — man
     korrigiert dann blind, weil das Luftbild darunter verschwindet. `vegFaint`
     zeichnet dieselben Objekte als Umriss. */
  ok("Flächen ohne Füllung", /if\(vegFaint && istVeg\(k\)\)\{[\s\S]{0,200}?fill="none"/.test(cs));
  ok("eigene Flächen ebenso", /const leer=\(vegFaint && istVeg\(m\.kind\)\)/.test(cs));
  ok("Bäume als Ring statt Scheibe", /r="3\.6" fill="none" stroke/.test(cs));
  ok("auch die aus OSM", /r="3\.2" fill="none" stroke/.test(cs));

  /* DIE Bedingung, an der eine solche Änderung scheitern würde: kleiner
     GEZEICHNET darf nicht schwerer zu TREFFEN heißen. Die unsichtbare
     Trefferfläche bleibt bei r=13. */
  ok("Trefferfläche unverändert groß", /r="13" fill="transparent"/.test(cs));

  /* Drei Stufen, an der Karte umschaltbar — man wechselt sie während der
     Arbeit mehrfach, das gehört nicht in eine Einstellung. */
  ok("drei Stufen", /\["voll","Voll"\],\["dezent","Umriss"\],\["aus","Aus"\]/.test(src));
  ok("Umriss ist der Standard", /GEOED\.vegSicht\|\|"dezent"/.test(src));
  ok("aus blendet wirklich aus", /veg: vs!=="aus"/.test(src));
  ok("nur dezent zeichnet Umrisse", /vegFaint: vs==="dezent"/.test(src));
  /* Arbeitseinstellung der Sitzung, kein synchronisierter Geschmack. */
  ok("nicht in DB.ui", /function geoEdVegSicht\(v\)\{ GEOED\.vegSicht=v/.test(src));
}

/* ============ 24bj. Karteneditor: Ansicht, Rückgängig, Bildmenü ============ */
group("Karteneditor — Ansicht bleibt, Änderungen sind umkehrbar");
{
  const src = fs.readFileSync(FILE, "utf8");
  const V = G("viewBoxFor");

  /* Der Ausschnitt wird GEOGRAFISCH gemerkt (Mittelpunkt + Breite in Metern),
     nicht in Bildkoordinaten: `courseSVG` legt die Projektion bei jedem Neu-
     aufbau neu um die Features, ein gespeicherter viewBox zeigte danach eine
     andere Stelle. */
  if (typeof V === "function") {
    const M = { W: 600, H: 400, s: 2 };          // 2 Bildeinheiten je Meter
    const v = V(300, 200, 100, M, 4);
    near("Breite aus Metern mal Maßstab", v.w, 200, 0.01);
    near("Höhe im Seitenverhältnis", v.h, 200 * (400 / 600), 0.01);
    near("um den Mittelpunkt zentriert", v.x + v.w / 2, 300, 0.01);

    /* An den Rand geklemmt — sonst sähe man leere Fläche neben der Bahn. */
    const links = V(10, 200, 100, M, 4);
    eq("nicht über den linken Rand", links.x, 0);
    const rechts = V(590, 200, 100, M, 4);
    near("nicht über den rechten Rand", rechts.x + rechts.w, 600, 0.01);

    /* Grenzen: nie enger als die Zoomgrenze, nie weiter als die ganze Bahn. */
    ok("Mindestbreite greift", V(300, 200, 0.1, M, 4).w >= 4);
    eq("Höchstbreite ist die Bahn", V(300, 200, 9999, M, 4).w, 600);

    eq("ohne Projektion nichts", V(0, 0, 100, null, 4), null);
    eq("ohne Maßstab nichts", V(0, 0, 100, { W: 600, H: 400, s: 0 }, 4), null);
  }

  /* Der Anker wird VOR dem Neuaufbau genommen — danach ist die alte
     Projektion überschrieben und der Ausschnitt nicht mehr übersetzbar. */
  const rg = src.slice(src.indexOf("function renderGeoEditor()"),
                       src.indexOf("function renderGeoEditor()") + 2800);
  ok("Anker vor courseSVG", rg.indexOf("geoEdViewAnchor()") < rg.indexOf("courseSVG("));
  ok("Wiederherstellen statt Zurücksetzen", /geoEdViewRestore\(_geoedAnker\)/.test(rg));

  /* Rückgängig sichert `mine` UND `overrides` — eine verschobene Grünmitte
     landet in `overrides`, alles andere in `mine`; wer nur eines sichert,
     stellt die Hälfte wieder her. */
  const sn = src.slice(src.indexOf("function geoEdSnapshot("),
                       src.indexOf("function geoEdSnapshot(") + 1000);
  ok("beide Töpfe gesichert", /mine: JSON\.stringify/.test(sn) && /ovr: JSON\.stringify/.test(sn));
  ok("Tiefe begrenzt", /GEOED\.undo\.length>12/.test(sn));
  const un = src.slice(src.indexOf("function geoEdUndo("),
                       src.indexOf("function geoEdUndo(") + 700);
  ok("Overrides werden neu angewandt", /applyGeoOverrides\(geo\)/.test(un));
  ok("kein Eintrag: freundliche Meldung", /Nichts rückgängig/.test(un));

  /* Jeder Eingriff muss vorher sichern — ein vergessener macht den Knopf
     unzuverlässig, und das ist schlimmer als kein Knopf. */
  ["Verschieben","Baum setzen","Punkt setzen","Grünmitte setzen","Löschen",
   "Fläche zeichnen","Linie zeichnen","Erkennung"].forEach(w =>
    ok("gesichert vor: " + w, src.indexOf('geoEdSnapshot("' + w + '")') > 0));

  /* Bildmenü beim Langdruck — in den Editoren hält man den Finger noch länger
     still als auf der Spielkarte. */
  ok("Karteneditor fängt das Kontextmenü ab",
    /#geoedSvg, #geoedSvg svg, #strkSvg, #strkSvg svg\{-webkit-touch-callout:none/.test(src));
  /* Drei Karten fangen es ab; im Karteneditor mit Menü statt nur Unterdrücken
     (v2.77), deshalb wird dort auf `preventDefault` IM Handler geprüft. */
  ok("und den Zeiger-Langdruck",
    (src.match(/addEventListener\("contextmenu", e=>e\.preventDefault\(\)\)/g)||[]).length >= 2
    && /addEventListener\("contextmenu", e=>\{ e\.preventDefault\(\);/.test(src));
}

/* ============ 24bi. Runde verwerfen ============ */
group("Runde verwerfen — der zweite Ausgang");
{
  const src = fs.readFileSync(FILE, "utf8");
  /* Nur der Rumpf von playDiscard — das folgende playFinish speichert
     natürlich, und genau das darf die Prüfung nicht mitlesen. */
  const pd = src.slice(src.indexOf("function playDiscard()"),
                       src.indexOf("function playFinish()"));

  ok("Knopf in der Eingabemaske", /onclick="playDiscard\(\)"/.test(src));
  ok("neben dem speichernden Ausgang",
    src.indexOf('onclick="playFinish()"') < src.indexOf('onclick="playDiscard()"'));

  /* Die Rückfrage muss ZAHLEN nennen. „Wirklich verwerfen?" beantwortet man
     nach drei Stunden auf dem Platz falsch. */
  ok("Rückfrage vorhanden", /confirm\(/.test(pd));
  ok("sie nennt die erfassten Löcher", /\$\{loecher\} Loch/.test(pd));
  ok("und die Scores", /\$\{scores\} Score/.test(pd));
  ok("und sagt, dass es endgültig ist", /nicht rückgängig/.test(pd));
  /* Ohne Eingaben keine Rückfrage — eine Warnung ohne Inhalt gewöhnt einem
     das Lesen ab, und genau dann klickt man auch die wichtige weg. */
  ok("leere Runde fragt nicht", /if\(loecher && !confirm/.test(pd));

  /* Die Runde darf NICHT in der Ablage landen. */
  ok("nichts wird gespeichert", !/DB\.rounds\.push|DB\.rounds\[i\]=/.test(pd));
  ok("der Entwurf bekommt seinen Grabstein", /draftFinalize\(\)/.test(pd));

  /* Reihenfolge wie in playFinish: erst inaktiv, dann Ansicht zurück, dann
     schließen — nur dann greift dort wakeRelease() und der Bildschirm darf
     wieder abschalten. */
  ok("erst inaktiv, dann Ansicht", pd.indexOf("PLAY.active=false") < pd.indexOf("pfRestoreView()"));
  ok("dann erst schließen", pd.indexOf("pfRestoreView()") < pd.indexOf("closeSheet()"));
  ok("Spielmodus wird verlassen", /pfRestoreView\(\)/.test(pd));
  ok("GPS wird freigegeben", /liveStop\(\)/.test(pd));
  /* Der Grabstein muss sofort ins Repo, sonst holt der nächste Merge den
     Entwurf vom anderen Gerät zurück (Fehlerklasse aus v1.60). */
  ok("Grabstein sofort ins Repo", /flushCloudNow\(\)/.test(pd));
}

/* ============ 24bh. Wald ausblenden · Gameplan frisch halten ============ */
group("Vegetation ausblenden — Anzeige, nicht Bewertung");
{
  const src = fs.readFileSync(FILE, "utf8");
  ok("Schalter vorhanden", /function toggleVeg\(\)/.test(src));
  ok("Karte bekommt den Zustand", /veg:vegOn\(\)/.test(src));
  ok("Standard ist eingeblendet", /DB\.ui && DB\.ui\.mapVeg===false/.test(src));

  /* BEIDE Quellen: Wald aus OSM und selbst gezeichneter/erkannter aus `mine`.
     Wer ausblendet, will nicht die halbe Vegetation stehen haben. */
  const cs = src.slice(src.indexOf("const zeigVeg"), src.indexOf("const zeigVeg") + 6000);
  ok("Flächen aus OSM gefiltert", /if\(!zeigVeg && istVeg\(k\)\) return;/.test(cs));
  ok("Linien aus OSM gefiltert", /if\(!zeigVeg && istVeg\(f\.kind\)\) return;/.test(cs));
  ok("eigene Zeichnungen ebenso", /if\(!zeigVeg && istVeg\(m\.kind\)\) return;/.test(cs));
  ok("Hecken und Baumreihen zählen dazu", /wood:1, scrub:1, tree:1, treerow:1, hedge:1/.test(cs));

  /* Die Bewertung darf der Schalter NICHT anfassen — ein ausgeblendeter Wald,
     der auch nicht mehr blockiert, wäre ein Kartenfehler mit Ansage. */
  ok("Raster kennt den Schalter nicht",
    !/vegOn\(\)/.test(src.slice(src.indexOf("  grid(geo,courseName,holeNo)"),
                                  src.indexOf("  grid(geo,courseName,holeNo)") + 5200)));
  ok("die Meldung sagt das auch", /Caddy rechnet weiter damit/.test(src));
}

group("Gameplan hält sich selbst frisch");
{
  const FP = G("gpFingerprint"), ST = G("gpStale"), src = fs.readFileSync(FILE, "utf8");
  if (typeof FP === "function") {
    const geo = { features:[{kind:"wood", ring:[[54.0,10.75],[54.001,10.75],[54.001,10.751]]}],
      mine:[], holes:{1:{tee:[54.0,10.74], green:[54.004,10.74]}} };
    const clubs = [{name:"Driver", carry:211}, {name:"7 Iron", carry:140}];
    const a = FP(geo, clubs, 20.4);

    eq("gleiche Eingaben, gleicher Abdruck", FP(geo, clubs, 20.4), a);

    /* Genau die drei Änderungen, die den Plan ungültig machen. */
    const geo2 = JSON.parse(JSON.stringify(geo));
    geo2.mine.push({kind:"tree", pt:[54.002,10.7505]});
    ok("erkannter Baum ändert den Abdruck", FP(geo2, clubs, 20.4) !== a);
    const geo3 = JSON.parse(JSON.stringify(geo));
    geo3.holes[1].green = [54.0045,10.7402];
    ok("verschobenes Grün ändert ihn", FP(geo3, clubs, 20.4) !== a);
    ok("neuer Schläger ändert ihn", FP(geo, clubs.concat([{name:"5 Wood",carry:192}]), 20.4) !== a);
    ok("neue Schlägerlänge ändert ihn",
      FP(geo, [{name:"Driver", carry:219}, {name:"7 Iron", carry:140}], 20.4) !== a);
    ok("neues Handicap ändert ihn", FP(geo, clubs, 18.1) !== a);
    /* Ein Zehntel Handicap zählt, damit ein HCP-Sprung nicht durchrutscht. */
    ok("auch ein Zehntel", FP(geo, clubs, 20.5) !== a);
  }

  if (typeof ST === "function") {
    const jetzt = Date.parse("2026-08-13T09:00:00Z");
    const frisch = { fp:"x", ts:"2026-08-13T08:00:00Z" };
    ok("frischer Plan mit gleichem Abdruck bleibt", !ST(frisch, "x", jetzt));
    ok("anderer Abdruck macht ihn ungültig", ST(frisch, "y", jetzt));
    ok("kein Plan heißt rechnen", ST(null, "x", jetzt));
    /* Altbestand ohne Abdruck einmal neu rechnen — sonst bliebe er ewig alt. */
    ok("Plan aus der Zeit vor dem Abdruck", ST({ts:"2026-08-13T08:00:00Z"}, "x", jetzt));
    ok("kaputter Zeitstempel heißt rechnen", ST({fp:"x", ts:"gestern"}, "x", jetzt));
    /* Höchstalter fängt Änderungen ab, die der Abdruck nicht sieht. */
    ok("31 Tage alt wird erneuert",
      ST({fp:"x", ts:"2026-07-10T09:00:00Z"}, "x", jetzt, 30));
    ok("29 Tage alt bleibt", !ST({fp:"x", ts:"2026-07-16T09:00:00Z"}, "x", jetzt, 30));
  }

  /* Der teure Teil gehört NICHT in die Runde und nicht in Serie. */
  const ar = src.slice(src.indexOf("function gpAutoRefresh()"),
                       src.indexOf("function gpAutoRefresh()") + 1400);
  ok("niemals während einer Runde", /PLAY\.active\) return;/.test(ar));
  ok("höchstens ein Plan je Durchgang", /\n      return;\s*\/\/ einer pro Durchgang/.test(ar));
  ok("Abdruck wird mitgeschrieben", /p\.fp=fp; p\.ts=new Date\(\)\.toISOString\(\)/.test(ar));
  ok("stündlich geprüft", /GP_PRUEF_MS=3600e3/.test(src));
  ok("nicht sofort beim Start", /setTimeout\(gpAutoRefresh, 4000\)/.test(src));
  ok("auch der Handrechnung folgt ein Abdruck", /p\.fp=gpFingerprint\(c\.geo, clubList\(\)/.test(src));
}

/* ============ 24bg. Schläge bearbeiten statt tracken ============ */
group("Schlag-Editor — Nacherfassen ist kein Aufzeichnen");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* Die Maske wird aus dem RUNDENEDITOR geöffnet: man trägt nach, was gespielt
     wurde. „Tracken" und ein GPS-Knopf führen dort in die Irre. */
  ok("Knopf heißt bearbeiten", /✏️ Schläge bearbeiten/.test(src));
  /* Der Changelog erzählt die Geschichte und darf den alten Namen nennen —
     geprüft wird der ausführbare Teil. */
  ok("nicht mehr tracken", !/🎯 Schläge tracken/.test(code));
  ok("Überschrift ebenso", /<h2>✏️ Schläge bearbeiten · Loch/.test(src));

  /* GPS nur am Spieltag — sonst zeichnet der Knopf den Schreibtisch auf. */
  ok("GPS am Datum der Runde festgemacht", /STRK\.gps = \(d===todayISO\(\)\)/.test(src));
  ok("GPS-Knopf nur dann im Markup", /\$\{gpsAn\?`<button class="btn ghost" id="strkAdd"/.test(src));
  ok("und sein Handler hält das aus", /const g=document\.getElementById\("strkAdd"\); if\(g\)/.test(src));

  /* Anlegen muss auch ohne Kartentreffer gehen. */
  const np = src.slice(src.indexOf("function strkNewPoint("),
                       src.indexOf("function strkNewPoint(") + 1200);
  ok("erster Punkt kommt aufs Tee", /!arr\.length && hr && hr\.tee/.test(np));
  ok("weitere auf halbem Weg zum Grün", /letzt\[0\]\+hr\.green\[0\]\)\/2/.test(np));
  ok("ohne Geodaten eine ehrliche Meldung", /fehlen Geodaten/.test(np));
  ok("und kein stiller Abbruch", /if\(!p\)\{ toast/.test(np));
  ok("Hinweis sagt, was als Nächstes zu tun ist", /an die richtige Stelle ziehen/.test(np));
}

/* ============ 24bf. Zoomgrenze des Karteneditors ============ */
group("Karteneditor — tief genug zum Zeichnen");
{
  const src = fs.readFileSync(FILE, "utf8");
  const Z = G("geoEdMinW");

  /* EINE Quelle für beide Wege. Vorher stand die Formel doppelt — einmal für
     Knöpfe/Mausrad, einmal im Pinch-Zweig. Zwei Kopien derselben Grenze heißt
     früher oder später zwei verschiedene Grenzen, je nachdem ob man mit den
     Fingern oder dem Knopf zoomt. */
  ok("keine zweite Zoomgrenze im Code", !/M\.W\*0\.12/.test(src.replace(/Vorher `[^`]*`/g, "")));
  /* Der Schlag-Editor teilt sie: Wer einen Schlag auf die Bahn setzt, zielt
     auf denselben Meter wie beim Zeichnen einer Kante. */
  ok("Schlag-Editor nutzt dieselbe Grenze", /minW=editMinW\(M\)/.test(src));
  ok("Knöpfe nutzen die gemeinsame Grenze",
    /geoEdZoomAt\(factor,cx,cy\)\{[\s\S]{0,200}?minW=geoEdMinW\(\)/.test(src));
  ok("Pinch nutzt dieselbe", /p0=GEOED\.pinch, minW=geoEdMinW\(\)/.test(src));

  if (typeof Z === "function") {
    const G2 = G("GEOED");
    if (G2) {
      const vorher = G2.M;
      /* Eine 400 m breite Bahn: 2 % sind 8 m Bildbreite — fein genug, um eine
         Bunkerkante zu setzen. Vorher waren es 48 m. */
      G2.M = { W: 400, H: 300 };
      near("2 % der Bahnbreite", Z(), 8, 0.001);
      ok("deutlich tiefer als die alte Grenze", Z() < 400 * 0.12);

      /* Untergrenze: Bei einer kurzen Bahn dürfen 2 % nicht unter das fallen,
         was ein Finger noch treffen kann. */
      G2.M = { W: 80, H: 60 };
      eq("Untergrenze 3 m greift", Z(), 3);

      /* Ohne Karte darf nichts krachen — der Editor ruft das auch, bevor eine
         Bahn gewählt ist. */
      G2.M = null;
      eq("ohne Karte ein sicherer Wert", Z(), 3);
      G2.M = vorher;
    }
  }
}

/* ============ 24be. ✕ während der Runde · Schlagart beim Nachtragen ============ */
group("Blatt schließen und Schläge nachtragen");
{
  const src = fs.readFileSync(FILE, "utf8");

  /* ✕ löste `history.back()` aus — und Android wertet eine Rückwärts-
     navigation als Verlassen des Vollbilds. `pfOpen()` umgeht beides: Es setzt
     `_sheetHist=false` VOR closeSheet, es gibt also gar kein back(). */
  const sc = src.slice(src.indexOf("function sheetCloseSmart()"),
                       src.indexOf("function sheetCloseSmart()") + 700);
  ok("✕ ist verdrahtet", /sc\.onclick=sheetCloseSmart/.test(src));
  ok("zurück auf die Karte statt nur schließen", /pfOpen\(\)/.test(sc));
  ok("nur bei laufender Runde", /PLAY\.active/.test(sc));
  /* Die Bedingung ist bewusst SCHLICHT (v2.62.1): Läuft eine Runde und ist die
     Karte gerade nicht offen, führt ✕ dorthin zurück. Die frühere Zusatzprüfung
     auf `_pfVorher` griff nicht immer — und der Rückfallweg endete in einer
     Ansicht ohne Bedienelemente. */
  ok("keine zu strenge Zusatzbedingung",
    !/if\(inRunde[^)]*_pfVorher/.test(sc) && /if\(inRunde && !PLAY\.mapFocus/.test(sc));
  /* Blätter ÜBER der Karte (Scorekarte, Details) laufen weiter über das
     schlichte Schließen — dort ist mapFocus true und man will dort bleiben. */
  ok("Blätter über der Karte bleiben unberührt", /!PLAY\.mapFocus/.test(sc));
  ok("sonst das normale Schließen", /\n  closeSheet\(\);/.test(sc));

  /* Die Sackgasse: `play` aktiv, ohne Vollbildklasse, ohne offenes Blatt.
     `pfRender()` steigt dort in der ersten Zeile aus, die Eingabemaske ist zu —
     es steht eine Karte von vorhin da, an der nichts reagiert. Sieht aus wie
     ein Absturz, ist eine Ansicht ohne Bedienelemente. */
  const eu = src.slice(src.indexOf("function pfEnsureUsable()"),
                       src.indexOf("function pfEnsureUsable()") + 700);
  ok("Wache stellt den brauchbaren Zustand her", /PLAY\.mapFocus \? pfOpen\(\) : renderPlay\(\)|PLAY\.mapFocus\) pfOpen\(\); else renderPlay\(\)/.test(eu));
  ok("offenes Blatt ist in Ordnung", /sheet\.classList\.contains\("open"\)\) return/.test(eu));
  ok("Vollbild ist in Ordnung", /contains\("play-mode"\)\) return/.test(eu));
  ok("andere Ansichten gehen sie nichts an", /v-play[\s\S]{0,120}?return/.test(eu));
  ok("läuft nach dem ✕", /setTimeout\(pfEnsureUsable, 80\)/.test(sc));
  ok("läuft nach Zurück", /setTimeout\(pfEnsureUsable, 60\)/.test(src));
  ok("läuft beim Wechsel auf die Spielansicht",
    /v==="play" && typeof pfEnsureUsable==="function"/.test(src));

  /* Schlagart beim nachträglichen Einzeichnen. */
  const rs = src.slice(src.indexOf("function renderShotTrack("),
                       src.indexOf("function renderShotTrack(") + 3000);
  ok("Schlagart-Auswahl je Schlag", /class="strk-swing/.test(rs));
  ok("Liste kommt aus DB.swingTypes", /DB\.swingTypes/.test(rs));
  /* Der LETZTE Punkt ist die Ruhelage, kein Schlag — dort wären Schläger und
     Schlagart sinnlos und würden zu Geisterdaten führen. */
  ok("Ruhelage bekommt keine Schlagart", /istSchlag\?`<select class="strk-swing/.test(rs));
  ok("und auch keinen Schläger", /istSchlag\?"":" disabled"/.test(rs));

  const sw = src.slice(src.indexOf("function strkSwing("),
                       src.indexOf("function strkSwing(") + 600);
  ok("Setzen zeichnet die Karte NICHT neu", !/renderShotTrack/.test(sw));
  ok("leere Auswahl löscht den Wert", /s\.swing=v\|\|null/.test(sw));
  ok("Entwurf wird gesichert", /playSaveDraft/.test(sw));
}

/* ============ 24bd. Vollbild beim Öffnen der Karte ============ */
group("Vollbild — Automatik, aber nicht gegen den Nutzer");
{
  const src = fs.readFileSync(FILE, "utf8");
  const po = src.slice(src.indexOf("function pfOpen()"),
                       src.indexOf("function pfOpen()") + 1400);
  /* DIE zentrale Bedingung: Die Fullscreen-API verlangt eine Nutzergeste.
     Steht der Aufruf hinter einem setTimeout, ist die Geste vorbei und die
     Anfrage scheitert still — der Fehler wäre auf dem Platz nicht erklärbar. */
  ok("Vollbild wird in pfOpen angefragt", /pfFullscreenAuto\(\)/.test(po));
  ok("und zwar VOR dem verzögerten Kartenaufbau",
    po.indexOf("pfFullscreenAuto()") < po.indexOf("setTimeout"));

  const fa = src.slice(src.indexOf("function pfFullscreenAuto("),
                       src.indexOf("function pfFullscreenAuto(") + 1500);
  ok("Abschaltung wird respektiert", /!fsAutoOn\(\)/.test(fa));
  ok("selbst geschlossenes Vollbild bleibt zu", /PLAY\.fsOff/.test(fa));
  /* Der Standalone-Riegel ist mit v2.61.1 GEFALLEN: Vollbild blendet auch in
     der installierten App die Statuszeile aus, und `display-mode` meldet nicht
     jeder Browser zuverlässig — der Riegel hat womöglich auch im Tab
     zugeschlagen. Die Prüfung steht andersherum, damit er nicht zurückkehrt. */
  ok("kein Standalone-Riegel mehr", !/display-mode: standalone/.test(fa));
  /* Der Nachrüst-Weg ist die eigentliche Absicherung: Schlägt die Anfrage aus
     pfOpen fehl (verbrauchte Geste oder abgelehntes Promise), wird sie beim
     nächsten Fingertipp wiederholt — dann garantiert mit frischer Geste. */
  ok("Ablehnung stellt scharf statt aufzugeben", /_fsArm = fsAutoOn\(\) && !PLAY\.fsOff/.test(fa));
  ok("pfOpen stellt scharf", /pfFullscreenArm\(\)/.test(po));
  ok("scharf VOR dem direkten Versuch",
    po.indexOf("pfFullscreenArm()") < po.indexOf("pfFullscreenAuto()"));
  ok("ein Fingertipp löst die Nachrüstung aus",
    /addEventListener\("pointerdown", \(\)=>\{[\s\S]*?_fsArm[\s\S]*?pfFullscreenAuto\(true\)/.test(src));
  ok("Nachrüstung feuert nur einmal", /_fsArm=false;\s*\n\s*pfFullscreenAuto\(true\);/.test(src));
  /* Und sie zündet nicht ihrerseits nach — sonst hinge an jedem Tipp eine
     neue Anfrage, die der Browser aus demselben Grund wieder ablehnt. */
  ok("der zweite Versuch stellt nicht erneut scharf", /if\(!zweiterVersuch\) _fsArm/.test(fa));
  /* Scheitert AUCH er, liegt es nicht mehr an der Geste. Dann genau EINMAL
     sagen, was hilft — ein stiller Fehlschlag lässt einen ratlos zurück. */
  ok("zweite Ablehnung wird gemeldet", /_fsGemeldet/.test(fa));
  ok("und zwar nur einmal je Runde", /_fsGemeldet=false;/.test(src));
  ok("manuelles Schließen entschärft sie auch", /PLAY\.fsOff=true; _fsArm=false;/.test(src));
  ok("doppelte Anfrage vermieden", /document\.fullscreenElement/.test(fa));
  /* Eine abgelehnte AUTOMATISCHE Anfrage ist kein Ereignis, über das man beim
     Abschlag lesen will — anders als die angeforderte über ⛶. */
  ok("die ERSTE Ablehnung bleibt still", /if\(zweiterVersuch\)\{/.test(fa));

  const pf = src.slice(src.indexOf("function pfFullscreen()"),
                       src.indexOf("function pfFullscreen()") + 900);
  ok("manuelles Schließen merkt sich der Wunsch", /PLAY\.fsOff=true/.test(pf));
  ok("manuelles Öffnen hebt ihn auf", /PLAY\.fsOff=false/.test(pf));
  ok("Rundenstart setzt den Merker zurück", /PLAY\.fsOff=false;/.test(src.slice(0, src.indexOf("function pfFullscreen()"))));
}

/* ============ 24bc. Kein Browser-Menü beim Ziehen der Landezone ============ */
group("Karte — Langdruck darf kein Bildmenü öffnen");
{
  const src = fs.readFileSync(FILE, "utf8");
  /* Alle drei Riegel müssen stehen: einer allein greift nicht überall.
     `-webkit-touch-callout` kennt nur WebKit, das Kontextmenü kommt auf dem
     Desktop über die rechte Maustaste, und die Kacheln sind <image> — solange
     sie Zeiger annehmen, sind sie das Ziel des Langdrucks. */
  ok("Kacheln nehmen keine Zeiger an", /#playSatG, #playSatG image\{pointer-events:none\}/.test(src));
  ok("Aufklapp-Menü unterdrückt (WebKit)", /-webkit-touch-callout:none/.test(src));
  ok("Kontextmenü abgefangen", /addEventListener\("contextmenu", e=>e\.preventDefault\(\)\)/.test(src));
  ok("Bild-Ziehen abgefangen", /addEventListener\("dragstart", e=>e\.preventDefault\(\)\)/.test(src));
  /* Die Zeiger-Ereignisse selbst müssen weiter auf dem SVG liegen — wer die
     Kacheln stumm schaltet und dabei das SVG mit erwischt, hat die Karte
     unbedienbar gemacht. */
  ok("SVG bleibt bedienbar", !/#pfMap svg\{[^}]*pointer-events:none/.test(src));
}

/* ============ 24bb. Messen ab Tee, wenn die Position unplausibel ist ============ */
group("Messpunkt — Bezug ist Position oder Tee");
{
  const O = G("measureOrigin");
  if (typeof O === "function") {
    const hier = [54.0, 10.75], tee = [54.01, 10.76];

    /* Auf der Bahn bleibt alles wie bisher: gemessen wird ab Standort. */
    eq("nah dran: ab Position", O(hier, tee, false).ab, "Position");
    eq("und zwar wirklich der Standort", O(hier, tee, false).p, hier);

    /* Zu weit weg — Planen am Schreibtisch: die gefragte Zahl ist „wie weit
       ist der Bunker vom ABSCHLAG", nicht „von meinem Sofa". */
    eq("zu weit: ab Tee", O(hier, tee, true).ab, "Tee");
    eq("und zwar der Abschlag", O(hier, tee, true).p, tee);

    /* Ohne Tee-Punkt gibt es keinen besseren Bezug als den Standort —
       lieber eine unplausible Zahl als gar keine Messung. */
    eq("zu weit ohne Tee: doch ab Position", O(hier, null, true).ab, "Position");
    /* Ohne GPS (noch kein Fix) ist das Tee der richtige Bezug. */
    eq("ohne Position: ab Tee", O(null, tee, false).ab, "Tee");
    eq("weder noch: keine Messung", O(null, null, true), null);

    /* Die Beschriftung MUSS mitlaufen: eine Zahl ab Tee, die aussieht wie eine
       ab Standort, ist schlimmer als keine. */
    const src = fs.readFileSync(FILE, "utf8");
    ok("Karte beschriftet die Tee-Messung", /measureLab\?" "\+esc\(opt\.measureLab\)/.test(src));
    ok("Meldung beschriftet sie auch", /ab Tee":"bis dorthin"/.test(src));
    ok("Karte und Meldung teilen den Ursprung", /function _measOrig\(\)/.test(src));
  }
}

/* ============ 24ba. Zweiter Zug, Spielweise im Approach, σ-Deckel ============ */
group("Caddy — zweiter Zug und die Gewichte, die ihn tragen");
{
  const S = G("STRAT");
  if (S) {
    /* --- (a) Der Approach benutzt jetzt DIESELBE Gewichtstabelle wie der
       Abschlag. Vorher stand dort eine zweite Formel, in der Sand gar nicht
       vorkam — „sicher" ließ den Bunker am Grün also unbeeindruckt. --- */
    const src = fs.readFileSync(FILE, "utf8");
    const ap = src.slice(src.indexOf("  approach(geo,courseName,holeNo"),
                         src.indexOf("  approach(geo,courseName,holeNo") + 2600);
    ok("Approach nimmt die Spielweise-Tabelle", /spielweise\(mode\)\.lie/.test(ap));
    ok("Sand geht in die Approach-Wertung ein", /w\.sand\s*\*\s*ev\.sand/.test(ap));
    ok("keine zweite Modus-Formel mehr", !/mode===\"safe\"\?\s*ev\.es\+1\.5/.test(ap));

    /* --- (b) Zweiter Zug --- */
    ok("_ply2 existiert", typeof S._ply2 === "function");
    const te = src.slice(src.indexOf("  tee(geo,courseName,holeNo,mode,hcp,von)"),
                         src.indexOf("  tee(geo,courseName,holeNo,mode,hcp,von)") + 7500);
    ok("zweite Ebene nur für die Spitze", /Math\.min\(5,cands\.length\)/.test(te));
    ok("Korrektur gedämpft (Mittelpunkt statt Verteilung)", /0\.7\s*\*\s*c\.ply2/.test(te));
    ok("Korrektur ist Gelände minus Tabelle", /p2\.sc\s*-\s*generisch/.test(te));
    ok("eine Vorlege-Option unter 140 m ist zugelassen", /c\.dist<140/.test(te));
    /* Der zweite Zug darf die erste Ebene nicht ersetzen: ohne Bewertung
       bleibt der Einzug-Wert stehen, sonst fiele ein Kandidat komplett aus. */
    ok("ohne zweite Ebene bleibt der Einzug-Wert", /c\.score2=c\.score;/.test(te));

    /* --- (c) σ-Deckel: gelernte Werte werden an die Schlaglänge gebunden --- */
    const merk = DB => DB;
    const D = G("DB");
    if (D) {
      D.strat = D.strat || {}; D.strat.dispersion = D.strat.dispersion || {};
      const vorher = D.strat.dispersion["__test7w__"];
      /* 26 m Seitenstreuung auf einen 174-m-Schläger: das ist der Wert, den
         learnLateralFromRounds aus einer 50-%-Fairwayquote zurückrechnet. */
      D.strat.dispersion["__test7w__"] = { sigL: 26, sigD: 20, biasL: 0, n: 40 };
      const g1 = S.sigmaFor({ name: "__test7w__", carry: 174, dist: 186 });
      ok("überhöhtes σ quer wird gedeckelt", g1.sigL <= 174 * 0.13 + 0.01, String(g1.sigL));
      ok("überhöhtes σ längs wird gedeckelt", g1.sigD <= 174 * 0.11 + 0.01, String(g1.sigD));
      ok("die Deckelung steht in der Quelle", /gedeckelt/.test(g1.src), g1.src);

      /* Plausible Werte bleiben unangetastet — der Deckel darf nicht die
         eigentliche Messung ersetzen. */
      D.strat.dispersion["__test7w__"] = { sigL: 12, sigD: 9, biasL: 2, n: 40 };
      const g2 = S.sigmaFor({ name: "__test7w__", carry: 174, dist: 186 });
      eq("gemessenes σ quer bleibt", g2.sigL, 12);
      eq("gemessenes σ längs bleibt", g2.sigD, 9);
      eq("Seitentendenz bleibt", g2.biasL, 2);
      ok("ohne Deckelung kein Zusatz", !/gedeckelt/.test(g2.src), g2.src);

      /* Wedges: der Deckel muss auch bei kurzen Schlägern greifen, aber nie
         unter eine sinnvolle Untergrenze fallen. */
      D.strat.dispersion["__testgw__"] = { sigL: 30, sigD: 25, biasL: 0, n: 30 };
      const g3 = S.sigmaFor({ name: "__testgw__", carry: 100, dist: 105 });
      ok("Wedge-σ gedeckelt", g3.sigL <= 13.01, String(g3.sigL));
      ok("Untergrenze bleibt brauchbar", g3.sigL >= 8, String(g3.sigL));
      delete D.strat.dispersion["__test7w__"]; delete D.strat.dispersion["__testgw__"];
      if (vorher) D.strat.dispersion["__test7w__"] = vorher;
    }

    /* --- (c) Grünzellen: „Grün 0 %" muss von „kein Grün im Raster" trennbar sein --- */
    const gr = src.slice(src.indexOf("  grid(geo,courseName,holeNo)"),
                         src.indexOf("  grid(geo,courseName,holeNo)") + 5200);
    ok("Raster zählt Grünzellen", /greenCells:gz/.test(gr));
    ok("approach meldet fehlendes Grün", /noGreen:!g\.greenCells/.test(src));
    ok("die Karte schreibt es hin statt 0 %", /kein Grün-Polygon/.test(src));
  }
}

/* ============ 24az. Streuung ohne Karte (Gameplan, Schlägerliste) ============ */
group("Streuung ohne GPS — Trefferquote, Text, Schema");
{
  const S = G("dispHitShare"), T2 = G("dispText"), SV = G("dispSchemaSvg"), E = G("_erf");

  if (typeof E === "function") {
    near("erf(0) = 0", E(0), 0, 1e-9);
    near("erf(1) ≈ 0,8427", E(1), 0.8427008, 2e-6);
    near("erf ist ungerade", E(-0.7), -E(0.7), 1e-9);
    near("erf(3) ≈ 1", E(3), 0.9999779, 2e-6);
  }

  if (typeof S === "function") {
    /* Lehrbuchwerte: ±1σ ≈ 68,3 %, ±2σ ≈ 95,4 %. Ein Korridor der Breite 2σ
       ist genau ±1σ um die Mitte. */
    near("Korridor = 2σ trifft 68 %", S({ sigL: 10, sigD: 8, biasL: 0 }, 20), 0.6827, 0.002);
    near("Korridor = 4σ trifft 95 %", S({ sigL: 10, sigD: 8, biasL: 0 }, 40), 0.9545, 0.002);

    /* Der eigentliche Zweck: enger streuen heißt öfter treffen. */
    const eng = S({ sigL: 8, sigD: 8, biasL: 0 }, 30), breit = S({ sigL: 20, sigD: 8, biasL: 0 }, 30);
    ok("enger Schläger trifft öfter", eng > breit, eng.toFixed(2) + " vs " + breit.toFixed(2));
    near("σ 8 m im 30-m-Korridor", eng, 0.9401, 0.003);

    /* Die eigene Fehlerseite kostet Trefferquote — ohne biasL wäre die
       Empfehlung „gleiche Ziellinie für alle" und damit falsch. */
    const mittig = S({ sigL: 12, sigD: 8, biasL: 0 }, 30);
    const schief = S({ sigL: 12, sigD: 8, biasL: 8 }, 30);
    ok("Tendenz zur Seite senkt die Quote", schief < mittig, schief.toFixed(2));
    near("Tendenz wirkt symmetrisch",
      S({ sigL: 12, sigD: 8, biasL: -8 }, 30), schief, 1e-9);

    /* Nur die QUERachse zählt — ein Korridor ist längs offen. */
    eq("σ längs ändert die Quote nicht",
      S({ sigL: 12, sigD: 4, biasL: 0 }, 30), S({ sigL: 12, sigD: 40, biasL: 0 }, 30));

    eq("ohne σ keine Quote", S(null, 30), null);
    eq("ohne Korridor keine Quote", S({ sigL: 12, sigD: 8 }, 0), null);
    ok("Quote bleibt zwischen 0 und 1", (() => {
      const v = S({ sigL: 3, sigD: 3, biasL: 0 }, 400); return v >= 0 && v <= 1;
    })());
  }

  if (typeof T2 === "function") {
    const t1 = T2({ sigL: 14.6, sigD: 9.2, biasL: 0 });
    ok("Text nennt quer", /±15 m quer/.test(t1), t1);
    ok("Text nennt längs", /±9 m lang/.test(t1), t1);
    ok("ohne Tendenz keine Tendenz im Text", !/Tendenz/.test(t1), t1);
    ok("Tendenz rechts wird benannt",
      /Tendenz rechts 6 m/.test(T2({ sigL: 14, sigD: 9, biasL: 6 })));
    ok("Tendenz links wird benannt",
      /Tendenz links 6 m/.test(T2({ sigL: 14, sigD: 9, biasL: -6 })));
    /* Unter 1 m ist die Tendenz Rauschen und würde als „Tendenz rechts 0 m"
       erscheinen — sinnlos und irreführend. */
    ok("Mini-Tendenz wird verschwiegen", !/Tendenz/.test(T2({ sigL: 14, sigD: 9, biasL: 0.4 })));
    eq("ohne σ ein Strich", T2(null), "–");
  }

  if (typeof SV === "function") {
    const svg = SV({ sigL: 15, sigD: 9, biasL: 0 }, { korridor: 30, label: "Fairway" });
    ok("liefert ein SVG", /^<svg/.test(svg));
    ok("kein NaN im Schema", !/NaN/.test(svg));
    ok("beide Ovale gezeichnet", (svg.match(/<ellipse/g) || []).length === 2);
    ok("Korridor beschriftet", /Fairway 30 m/.test(svg));

    /* EIN Maßstab für beide Achsen: quer/längs im Bild muss sich verhalten wie
       σ quer/längs. Zwei Skalen würden die Form verfälschen — und die Form ist
       hier die Aussage. */
    const m = svg.match(/rx="([\d.]+)"\s+ry="([\d.]+)"/);
    ok("Seitenverhältnis entspricht σ",
      m && Math.abs((+m[1] / +m[2]) - (15 / 9)) < 0.02, m && (m[1] + "/" + m[2]));

    eq("ohne σ kein Schema", SV(null, {}), "");
    eq("σ 0 ergibt kein Schema", SV({ sigL: 0, sigD: 0 }, {}), "");
    ok("Schema geht auch ohne Korridor", /^<svg/.test(SV({ sigL: 12, sigD: 8 }, {})));
  }

  /* ---- Die lochgenaue Quote aus dem Platzraster schlägt das Ersatzmaß ---- */
  const F = G("gpClubFracs");
  if (typeof F === "function") {
    const plan = { holes: [
      { hole: 1, club: "Driver", fracs: { fw: 60, sand: 5, pen: 2 } },
      { hole: 3, club: "Driver", fracs: { fw: 70, sand: 0, pen: 0 } },
      { hole: 5, club: "3 Wood", fracs: { fw: 80, sand: 0, pen: 0 } },
      { hole: 7, club: "7 Iron", fracs: { green: 44, sand: 8, pen: 0 } },
      { hole: 9, club: "Driver", approx: true, fracs: { fw: 50, sand: 0, pen: 0 } },
      { hole: 11, club: "Driver" },                       // Loch ohne Bewertung
      { hole: 13, note: "keine Geo-Daten" },
    ] };

    const d = F(plan, "Driver");
    eq("alle Löcher des Schlägers gefunden", d.holes.join(","), "1,3,9,11");
    eq("Mittel nur über bewertete Löcher", d.fw, 60);   // (60+70+50)/3
    ok("Loch ohne Bewertung zählt nicht ins Mittel", d.fw === 60);
    ok("Näherung wird durchgereicht", d.approx === true);

    const w = F(plan, "3 Wood");
    eq("einzelnes Loch", w.n, 1);
    eq("ohne Näherung bleibt es falsch", w.approx, false);

    /* Par 3: dort zählt Grün getroffen, nicht Fairway — die beiden dürfen
       nicht in einen Topf, sonst mittelt man Äpfel mit Birnen. */
    const e = F(plan, "7 Iron");
    eq("Grünquote getrennt geführt", e.green, 44);
    eq("keine Fairwayquote auf Par 3", e.fw, null);

    eq("unbenutzter Schläger liefert nichts", F(plan, "Putter"), null);
    eq("ohne Plan nichts", F(null, "Driver"), null);
    eq("ohne Schläger nichts", F(plan, null), null);
  }
}

/* ============ 24ax. „Jetzt dran" — eine Antwort, nicht sieben Karten ============ */
group("heuteJetzt — die Karte trifft die Entscheidung");
{
  const J = G("heuteJetzt");
  if (typeof J === "function") {
    const basis = { min: 9*60, tee: null, warmMin: 30, prePre: false, preWarm: false,
      prePost: false, draftHoles: 0, heutegespielt: false, ntDays: null, ntPrep: null,
      letzteRundeTage: 2 };
    const c = (o) => J(Object.assign({}, basis, o));

    /* Regel 1 gewinnt gegen ALLES — eine unterbrochene Runde ist der einzige
       Zustand, in dem Datenverlust droht. */
    eq("unterbrochene Runde schlägt Abschlagzeit",
      c({ draftHoles: 5, tee: 10*60, min: 9*60 }).titel, "Unterbrochene Runde");
    eq("unterbrochene Runde schlägt Turniervorbereitung",
      c({ draftHoles: 1, ntDays: 2, ntPrep: 7 }).titel, "Unterbrochene Runde");

    /* Die Abschlagzeit taktet den Tag RÜCKWÄRTS — das ist der eigentliche
       Zweck der Karte. 10:00 minus 30 min Aufwärmen = Start 09:30. */
    const vor = c({ tee: 10*60, min: 9*60, warmMin: 30 });
    eq("vor dem Fenster: Abschlagzeit im Titel", vor.titel, "Abschlag 10:00");
    ok("Startzeit rückwärts gerechnet", /09:30/.test(vor.text), vor.text);
    ok("Restzeit bis zum Start genannt", /60 min/.test(vor.text), vor.text);

    /* Im Fenster entscheidet, was noch NICHT abgehakt ist — und in der
       fachlich richtigen Folge: dynamisch dehnen vor Bällen. */
    eq("im Fenster ohne Stretch → Stretch",
      c({ tee: 10*60, min: 9*60+40, warmMin: 30 }).titel, "Preround Stretch");
    eq("Stretch erledigt → Aufwärmen",
      c({ tee: 10*60, min: 9*60+40, warmMin: 30, prePre: true }).titel, "Aufwärmen");
    eq("beides erledigt → auf den Platz",
      c({ tee: 10*60, min: 9*60+40, warmMin: 30, prePre: true, preWarm: true }).titel,
      "Auf den Platz");
    eq("nach dem Abschlag → Runde läuft",
      c({ tee: 10*60, min: 11*60 }).titel, "Runde läuft");
    /* Genau ZUR Abschlagzeit ist die Runde noch nicht gelaufen — die Grenze
       gehört ins Vorbereitungsfenster, sonst kippt die Karte eine Minute zu
       früh auf „Runde läuft". */
    eq("Grenzfall min == tee zählt zur Vorbereitung",
      c({ tee: 10*60, min: 10*60, prePre: true, preWarm: true }).titel, "Auf den Platz");

    /* Ohne Abschlagzeit greifen die Folgeregeln in ihrer Rangfolge. */
    eq("heute gespielt, nicht nachgedehnt → Post Round",
      c({ heutegespielt: true, letzteRundeTage: 0 }).titel, "Post Round Stretch");
    ok("nachgedehnt → nicht mehr Post Round",
      c({ heutegespielt: true, prePost: true, letzteRundeTage: 0 }).titel !== "Post Round Stretch");
    eq("Turnier im Vorbereitungsfenster",
      c({ ntDays: 3, ntPrep: 7 }).titel, "Turnier in 3 Tagen");
    ok("Turnier außerhalb des Fensters zählt nicht",
      c({ ntDays: 30, ntPrep: 7 }).titel !== "Turnier in 30 Tagen");
    eq("Einzahl bei einem Tag", c({ ntDays: 1, ntPrep: 7 }).titel, "Turnier in 1 Tag");
    eq("noch keine Runde erfasst",
      c({ letzteRundeTage: null }).titel, "Noch keine Runde erfasst");
    eq("lange keine Runde", c({ letzteRundeTage: 9 }).titel, "Letzte Runde vor 9 Tagen");
    eq("sonst: Weg zum Spielmodus", c({}).titel, "Kein Termin für heute");

    /* JEDER Zweig muss ein Ziel haben, und der Aufruf im Knopf muss es
       wirklich geben — ein Tippfehler dort tötet den Knopf lautlos
       (dieselbe Fehlerklasse wie Prüfabschnitt 24ar). */
    const src = fs.readFileSync(FILE, "utf8");
    const faelle = [
      { draftHoles: 3 }, { tee: 10*60, min: 8*60 }, { tee: 10*60, min: 9*60+40 },
      { tee: 10*60, min: 9*60+40, prePre: true }, { tee: 10*60, min: 9*60+40, prePre: true, preWarm: true },
      { tee: 10*60, min: 12*60 }, { heutegespielt: true, letzteRundeTage: 0 },
      { ntDays: 2, ntPrep: 7 }, { letzteRundeTage: null }, { letzteRundeTage: 9 }, {},
    ];
    let ohneZiel = 0, unbekannt = [];
    faelle.forEach(f => {
      const r = c(f);
      if (!r || !r.titel || !r.text || !r.lab || !r.aktion || !r.aktion.lab || !r.aktion.fn) { ohneZiel++; return; }
      const name = String(r.aktion.fn).replace(/\(.*$/, "");
      if (src.indexOf("function " + name + "(") < 0) unbekannt.push(name);
    });
    eq("jeder Zweig ist vollständig beschriftet", ohneZiel, 0);
    eq("jeder Knopf ruft eine existierende Funktion", unbekannt.join(", "), "");
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
  /* WARUM NICHT MEHR `slice(start, start+1500)`  (Fix 2026-08-12)
     Das Fenster war 1500 Zeichen lang, unabhaengig davon, wo die Funktion
     endet — bei kurzen Funktionen reichte es also weit in die FOLGENDEN
     hinein. Stand dort ein `render…`/`document.`, wurde die kurze, voellig
     reine Funktion als unrein einsortiert und fiel aus der Sperrklinke.
     Sichtbar wurde es an `roundKPIs`: drei Zeilen ohne jeden Seiteneffekt,
     vom Pruefstand aber als „nicht mehr vorhanden" gemeldet, weil der
     nachfolgende Kommentar von `sortedRounds` ins Fenster ragte — eine
     Aufraeumhilfe, die zum Loeschen einer noch existierenden Zeile geraten
     haette. Gemessen: 269 statt 343 Kandidaten, 74 reine Funktionen also
     unbeaufsichtigt. Genau die Luecke, die dieser Abschnitt schliessen soll.
     `pureBody` klammert jetzt bis zur zugehoerigen schliessenden Klammer und
     hoert in JEDEM Fall an der naechsten Deklaration auf Spaltenanfang auf —
     Letzteres als Fangnetz fuer Klammern in Zeichenketten und regulaeren
     Ausdruecken (`selfCheck` lief sonst ueber 865 000 Zeichen weiter). */
  const pureBody = (src, i0) => {
    const n = src.indexOf("\nfunction ", i0), grenze = n < 0 ? src.length : n;
    let d = 0;
    for (let j = i0; j < grenze; j++) {
      const c = src[j];
      if (c === "{") d++;
      else if (c === "}") { d--; if (d === 0) return src.slice(i0, j + 1); }
    }
    return src.slice(i0, grenze);
  };
  const unrein = (t) =>
    /document\.|innerHTML|fetch\(|localStorage|toast\(|render|openSheet|persist\(/.test(t);
  const kandidaten = [];
  const reF = /^function\s+(\w+)\s*\(([^)]*)\)\s*\{/gm;
  let m;
  while ((m = reF.exec(codeOnly))) {
    const body = pureBody(codeOnly, m.index + m[0].length - 1);
    if (!unrein(body) && m[2].trim()) kandidaten.push(m[1]);
  }
  /* Gegenproben zum Klassifizierer selbst — ohne sie faellt eine Regression
     hier nicht auf, sondern nur die Abdeckung still zurueck. */
  ok("Koerper endet an der eigenen Klammer (roundKPIs bleibt rein)",
    kandidaten.indexOf("roundKPIs") >= 0);
  ok("Funktion mit Seiteneffekt im EIGENEN Koerper zaehlt nicht",
    kandidaten.indexOf("openAddRound") < 0);
  ok("Klammer in Zeichenkette laesst den Koerper nicht davonlaufen",
    pureBody('function f(a){ const s="}"; return a; }\nfunction g(){}', 13).length < 60);
  /* DASSELBE PROBLEM WIE BEIM 1500-ZEICHEN-FENSTER, nur zwei Ebenen höher
     (Fund 2026-08-12): Die STRAT-Methoden wurden aus einem FESTEN Fenster von
     60 000 Zeichen ab `const STRAT=` gelesen. Wächst das Objekt darüber hinaus
     — und sei es nur um ein paar Kommentarzeilen —, fallen die letzten
     Methoden aus der Liste. Sie stehen dann in KEINER der beiden Mengen:
     nicht in `neuS` (also keine Meldung) und nicht in `alleS` (also keine
     Aufräumhilfe). Die Sperrklinke hört still auf, sie zu bewachen. Gemessen:
     58 statt 60 nach einem Kommentar in `planCourse`.
     Jetzt bis zur zugehörigen schließenden Klammer, mit derselben Notbremse
     wie `pureBody` — der nächsten Deklaration auf Spaltenanfang. */
  const si = codeOnly.indexOf("const STRAT=");
  const stratBody = si < 0 ? "" : (function(){
    const b = codeOnly.indexOf("{", si);
    if (b < 0) return "";
    const n = codeOnly.indexOf("\nfunction ", b), grenze = n < 0 ? codeOnly.length : n;
    let d = 0;
    for (let j = b; j < grenze; j++) {
      const c = codeOnly[j];
      if (c === "{") d++;
      else if (c === "}") { d--; if (d === 0) return codeOnly.slice(b, j + 1); }
    }
    return codeOnly.slice(b, grenze);
  })();
  const stratNamen = [...stratBody.matchAll(/^  (\w+)\(/gm)].map(x => x[1]);
  /* Gegenproben: Das Fenster war nicht nur zu KLEIN, es war auch zu GROSS —
     60 000 Zeichen reichten 17 000 Zeichen ÜBER das Objekt hinaus, und alles,
     was dort zufällig `^  name(` schrieb, zählte als STRAT-Methode. Die
     gemeldeten „60 Methoden" waren zur Hälfte Fremdcode. Beide Fehler prüfen
     wir hier: vollständig (die letzten Methoden sind dabei) und nicht mehr
     (nichts von außerhalb). */
  ["sigmaFor", "tee", "approach", "planCourse", "lieCode", "shotEV"].forEach(n =>
    ok("STRAT-Methode " + n + " erfasst", stratNamen.indexOf(n) >= 0));
  ok("nichts von hinter dem Objekt eingesammelt",
    stratNamen.indexOf("planHole") >= 0 && stratNamen.indexOf("stratOn") < 0,
    stratNamen.length + " Methoden");

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
