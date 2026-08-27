/* =============================================================================
   RUNDENSIMULATION — WAS SIE PRUEFT UND WARUM SIE NEBEN `tests.js` STEHT
   -----------------------------------------------------------------------------
   `tests.js` prueft FUNKTIONEN: Man gibt etwas hinein, vergleicht, was
   herauskommt. Diese Datei prueft die KETTE: eine Runde von der ersten
   Eingabe bis zum Grabstein, ueber die echten Wege — `playBegin`,
   `playSaveDraft`, `draftPush`, `draftPull`, `playFinish` — gegen einen
   nachgebauten Worker mit echtem Kennungs-Tuersteher.
   DER UNTERSCHIED IST NICHT AKADEMISCH: Die teuersten Fehler dieses Projekts
   waren nie falsche Funktionen, sondern richtige Funktionen an der falschen
   Stelle der Kette — ein Zeitstempel, der nach dem Kopieren gesetzt wurde
   (v3.07), ein Feld, das beim Abschreiben von PLAY vergessen wurde (v4.84),
   ein Push, der die Messungen des anderen Geraets ueberschrieb.
   AUSBAU 27.08.2026 — was dazugekommen ist und warum:
     · SCHLAGMESSUNG DER UHR (roh -> Neutralwert -> Lern-Basis). Seit
       Uhr-Fassung 38/40 ist das der EINZIGE Zweck der Uhr, und es war hier
       ueberhaupt nicht abgedeckt: geprueft wurden Distanzen und Caddy, also
       genau das, was die Uhr nicht mehr tut.
     · SCHREIBKONFLIKT (409). Uhr und Handy schreiben im Sekundenabstand;
       „vereinen statt ueberschreiben" war nur im glatten Fall geprueft.
     · NETZAUSFALL. Regel 1 der Architektur lautet „Repo-Push NIE als
       Bedingung fuers Weiterarbeiten" — ungeprueft ist das eine Absichts-
       erklaerung.
     · RUNDENABSCHLUSS. Die Lage, die man achtzehnmal von zwanzig erreicht,
       war ungeprueft; das Verwerfen dagegen doppelt.
     · ENTWURFS-MERGE MIT LEEREN LISTEN. Haelt eine Schwaeche fest, die beim
       Bau auffiel (siehe dort).
   ZWEI FALLEN, die beim Bau je eine halbe Stunde gekostet haben und deshalb
   hier oben stehen:
     1. KEIN ZEITSTEMPEL IN DER ZUKUNFT. Ein Entwurf, der spaeter datiert ist
        als „jetzt", gewinnt JEDEN folgenden Merge und schleppt seinen Inhalt
        durch alle weiteren Abschnitte. Der Fehlschlag erscheint dann an einer
        Stelle, die nichts damit zu tun hat.
     2. ASYNCHRONES AUSTRUDELN LASSEN. `playSaveDraft` stoesst ueber
        `maybeCheckpointDraft` einen Abgleich an, der spaeter zu Ende laeuft.
        Wer den naechsten Abschnitt beginnt, bevor er durch ist, bekommt dort
        seine Werte ueberschrieben.
   ============================================================================= */
/* Aufruf:  node runde-simulation.js   (im selben Ordner wie index.html)
   BRAUCHT `runde-harness.js` DANEBEN — ohne sie bricht der Lauf sofort mit
   MODULE_NOT_FOUND ab, und das sieht aus wie „hier nicht pruefbar".
   ES IST ABER NUR EINE FEHLENDE DATEI: Alle aktuellen Dateien liegen im Repo
   und werden bei Bedarf dort abgerufen (Arbeitsregel 0, 27.08.2026):
     https://raw.githubusercontent.com/golftraining-lars/Golftraining/main/runde-harness.js
   ANLASS DER REGEL: Genau dieser Abbruch hat am 27.08. ZWEI echte rote
   Pruefungen verdeckt — die Simulation forderte die Platzkarte in
   `watch.json`, die PWA v4.84 gerade entfernt hatte. Ein Tor, das nicht
   faehrt, meldet nichts; das ist schlimmer als ein rotes. */
const {G,R,pruef,kopf,REPO,SHAS,sandbox,bilanz}=require("./runde-harness.js");

/* ---------- Platz anlegen: Nordplatz Timmendorfer Strand ---------- */
kopf("Platz und Karte aufbauen");
const mLat=110540, mLng=111320*Math.cos(54.0*Math.PI/180);
const PARS=[4,3,5,4,4,3,4,5,4, 4,4,3,5,4,3,4,4,5];
const LEN =[279,151,468,332,345,168,356,472,301, 318,362,142,489,340,158,371,326,455];
function ring(lat,lng,r){ const o=[]; for(let k=0;k<10;k++){const t=k/10*2*Math.PI;
  o.push([+(lat+Math.cos(t)*r/mLat).toFixed(6), +(lng+Math.sin(t)*r/mLng).toFixed(6)]);} o.push(o[0]); return o; }
const holes={}, feats=[];
for(let i=0;i<18;i++){
  const n=i+1;
  const tLat=54.00+i*0.004, tLng=10.77+i*0.0015;
  const gLat=tLat+LEN[i]/mLat, gLng=tLng+((i%3)-1)*20/mLng;
  const vertauscht = (n===4 || n===11);            // zwei Loecher mit swap
  holes[n]= vertauscht
    ? {tee:[gLat,gLng], green:[tLat,tLng], swap:true, line:[[tLat,tLng],[gLat,gLng]], distM:LEN[i]}
    : {tee:[tLat,tLng], green:[gLat,gLng], line:[[tLat,tLng],[gLat,gLng]], distM:LEN[i]};
  feats.push({kind:"green", ring:ring(gLat,gLng,14)});
  feats.push({kind:"fairway", ring:ring((tLat+gLat)/2,(tLng+gLng)/2,45)});
  if(n%4===0) feats.push({kind:"bunker", ring:ring(gLat-30/mLat,gLng+12/mLng,7)});
  if(n%5===0) feats.push({kind:"water", ring:ring((tLat+gLat)/2+20/mLat,gLng-25/mLng,18)});
}
const tees={Gelb:{holes:PARS.map((p,i)=>({hole:i+1,par:p,si:i+1,len:LEN[i]}))}};
R(`DB.courses=[{name:"Nordplatz Timmendorfer Strand", tees:${JSON.stringify(tees)},
   geo:${JSON.stringify({holes,features:feats})}, geoAt:new Date().toISOString()}];
   DB.profile=DB.profile||{}; DB.profile.hcpIndex=20.0;
   DB.clubDistances=[{club:"Driver",carry:211},{club:"3 Wood",carry:201},{club:"5 Wood",carry:192},
     {club:"2 Iron",carry:187},{club:"7 Wood",carry:172},{club:"4 Iron",carry:161},{club:"5 Iron",carry:155},
     {club:"6 Iron",carry:146},{club:"7 Iron",carry:140},{club:"8 Iron",carry:133},{club:"9 Iron",carry:119},
     {club:"PW",carry:113},{club:"GW",carry:100},{club:"SW",carry:80},{club:"LW",carry:65}];
   DB.rounds=DB.rounds||[]; "ok"`);
pruef("Platz mit 18 Löchern angelegt", Object.keys(G("DB.courses[0].geo.holes")).length===18);
pruef("zwei Löcher mit vertauschtem Tee/Grün (4, 11)", !!G('DB.courses[0].geo.holes[4].swap'));

/* ---------- Cloud einrichten ---------- */
R(`localStorage.setItem("golfdb_workerUrl","https://w.example");
   localStorage.setItem("golfdb_writeKey","geheim");
   localStorage.setItem("golfdb_cloudAuto","1"); "ok"`);
pruef("Cloud konfiguriert", G("cloudConfigured()")===true);

/* ---------- Runde starten ---------- */
kopf("Runde starten");
const start=R(`playBegin("Nordplatz Timmendorfer Strand","Gelb",0); PLAY.active`);
pruef("Runde läuft", start===true, JSON.stringify(start));
pruef("18 Löcher geladen", G("PLAY.holes.length")===18);
pruef("Loch 1 ist Par 4, 279 m", G("PLAY.holes[0].par")===4 && G("PLAY.holes[0].len")===279);
pruef("Entwurf angelegt", !!G("DB._draftRound && DB._draftRound.round"));

console.log(JSON.stringify(bilanz()));

/* ---------- GPS: am Abschlag von Loch 1 ---------- */
kopf("GPS und Distanzen — Loch 1");
function stelleAuf(lat,lng,acc){ R(`PLAY.here=[${lat},${lng}]; PLAY.hereAcc=${acc||6}; "ok"`); }
const h1=G("DB.courses[0].geo.holes[1]");
stelleAuf(h1.tee[0], h1.tee[1], 5);
const d1=R(`(function(){ const geo=playGeo(), hr=holeRef(geo,1);
  return {mitte:Math.round(geoDist(PLAY.here,hr.green)), tee:Math.round(geoDist(PLAY.here,hr.tee))}; })()`);
pruef("Distanz zur Grünmitte ≈ 279 m", Math.abs(d1.mitte-279)<=5, JSON.stringify(d1));

/* ---------- dasselbe für ein VERTAUSCHTES Loch ---------- */
kopf("Vertauschtes Loch 4 — hier lag der Feldfehler");
const h4=G("DB.courses[0].geo.holes[4]");
const hr4=R(`(function(){const hr=holeRef(playGeo(),4); return {tee:hr.tee, green:hr.green};})()`);
stelleAuf(hr4.tee[0], hr4.tee[1], 5);
const d4=R(`(function(){ const hr=holeRef(playGeo(),4);
  return {mitte:Math.round(geoDist(PLAY.here,hr.green)),
          roh:Math.round(geoDist(PLAY.here, playGeo().holes[4].green))}; })()`);
pruef("App: Distanz zum Grün ≈ 332 m", Math.abs(d4.mitte-332)<=5, JSON.stringify(d4));
pruef("ROH gelesen wären es nur wenige Meter (der alte Uhr-Fehler)", d4.roh<20, "roh="+d4.roh+" m");

/* ---------- watch.json: was die Uhr bekommt ---------- */
/* ==========================================================================
   KEINE KARTE MEHR (PWA v4.84 / Uhr-Fassung 40)
   --------------------------------------------------------------------------
   Hier wurde geprueft, dass die Uhr die AUFGELOESTE Karte bekommt: Loch 4 mit
   dem echten Gruen statt des vertauschten, `swap` angewendet und entfernt,
   `distM` neu gerechnet, Gruen-Ringe fuer F/M/B dabei. Das war die Antwort auf
   einen echten Feldfehler — die Uhr las die Rohwerte und zeigte auf einem
   vertauschten Loch wenige Meter statt 332.
   Die Uhr zeigt seit Fassung 38 keine Distanzen mehr und hat seit 40 weder
   Geometrie noch Karten-Parser. Eine Karte zu schicken, die niemand liest,
   kostet bei JEDEM Push eine Serialisierung des groessten Datenteils.
   Die Pruefung ist deshalb GEDREHT, nicht geloescht: Sie haelt jetzt fest,
   dass die Karte NICHT mitreist — und faengt damit den Rueckfall, bei dem sie
   unbemerkt wieder in die Datei rutscht.
   Die Aufloesung selbst (`watchGeo`, `holeRef`) bleibt geprueft, wo sie noch
   gebraucht wird: in tests.js, Abschnitt 24cq — `schlagNeutral` rechnet die
   Hoehe ueber dieselben Profile. Der Feldfehler von damals ist also weiter
   abgedeckt, nur an der Stelle, wo er heute noch auftreten kann. */
kopf("watch.json — was die Uhr bekommt");
const wp=R(`watchPayload()`);
pruef("Platz enthalten", (wp.courses||[]).length===1);
const wgeo=(wp.courses[0]||{}).geo;
pruef("Karte NICHT mehr mitgegeben", !wgeo, wgeo?"immer noch da":"richtig weg");
pruef("aber Name und Tees bleiben",
  !!(wp.courses[0]||{}).name && !!(wp.courses[0]||{}).tees);
/* Ohne die Listen steht ein auf der Uhr gewaehlter Wert am Handy in keiner
   Auswahl — es sieht aus, als wuerde nichts uebertragen (Lehre aus v2.99).
   Ohne Schlaeger kann die Aufnahmezeile keinen zuordnen, und eine Messung
   ohne Schlaeger ist fuer die gelernten Laengen wertlos. */
pruef("Auswahllisten dabei", Array.isArray(wp.approachBuckets)&&Array.isArray(wp.firstPuttDist));
pruef("Schläger dabei", (wp.clubDistances||[]).length>10);
pruef("keine Gameplans mehr", !("strat" in wp));
/* DER ZWECK DER SCHLANKEN DATEI IST IHRE GROESSE. Mit der Karte lag sie bei
   einigen hundert kB; ohne sie muss sie klein bleiben, sonst hat der Rueckbau
   nichts gebracht. */
pruef("Datei bleibt klein", JSON.stringify(wp).length < 60000,
  Math.round(JSON.stringify(wp).length/1024)+" kB");
console.log(JSON.stringify(bilanz()));

/* ---------- Caddy ---------- */
kopf("Caddy — Empfehlung am Abschlag und vom Fairway");
stelleAuf(h1.tee[0], h1.tee[1], 5);
R(`PLAY.idx=0; "ok"`);
const cad=R(`(function(){ const geo=playGeo(), h=PLAY.holes[0];
  const ev=STRAT.tee(geo,PLAY.course,1,caddyMode(),STRAT.esHcp(),_caddyVon());
  return ev?{club:ev.best&&ev.best.club, rest:ev.best&&Math.round(ev.best.rest||0), ziel:!!ev.target}:null; })()`);
pruef("Tee-Empfehlung vorhanden", !!cad && !!cad.club, JSON.stringify(cad));
pruef("Empfehlung ist kein Putter", cad && cad.club!=="Putter", cad&&cad.club);
// vom Fairway, 120 m vor dem Grün
const hr1=R(`(function(){const hr=holeRef(playGeo(),1);return hr;})()`);
stelleAuf(hr1.green[0]-120/mLat, hr1.green[1], 5);
const cad2=R(`(function(){ var geo=playGeo(); var ev=_aimNextEv(geo,PLAY.holes[0],PLAY.here);
  if(!ev) return {leer:true};
  return {club:(ev.best&&ev.best.club)||ev.club||null, keys:Object.keys(ev).join(",")}; })()`);
pruef("Empfehlung vom Fairway vorhanden", !!(cad2&&cad2.club), JSON.stringify(cad2).slice(0,140));
const cName = cad2 && cad2.club && (cad2.club.name||cad2.club);
const plausibel = ["9 Iron","PW","GW","8 Iron","7 Iron","6 Iron"].indexOf(cName)>=0;
pruef("aus 120 m ein plausibler Schläger ("+cName+")", !!plausibel, JSON.stringify(cad2).slice(0,120));

/* ---------- Eingaben auf Loch 1 ---------- */
kopf("Eingaben und Entwurf");
R(`PLAY.idx=0; PLAY.holes[0].tee="Fairway"; PLAY.holes[0].appr=(DB.approachBuckets||[])[2]||"110-140";
   PLAY.holes[0].score=5; PLAY.holes[0].putts=2; playSaveDraft(); "ok"`);
const dr=G("DB._draftRound.round.holes");
const l1=(dr||[]).find(h=>h.hole===1);
pruef("Loch 1 im Entwurf", !!l1, JSON.stringify(l1&&Object.keys(l1)));
pruef("Score 5 gespeichert", l1&&l1.score===5);
pruef("Loch trägt einen Zeitstempel", !!(l1&&l1.ts), l1&&l1.ts);
console.log(JSON.stringify(bilanz()));

/* ---------- Abgleich: Handy schreibt, „Uhr" liest ---------- */
kopf("draft.json — Handy → Uhr");
(async()=>{
  const ok1=await R(`draftPush()`);
  await new Promise(r=>setImmediate(r));
  pruef("draft.json geschrieben", !!REPO["draft.json"], Object.keys(REPO).join(", "));
  let dj=null; try{ dj=JSON.parse(REPO["draft.json"]||"{}"); }catch(e){}
  pruef("Entwurf enthält die Runde", !!(dj&&dj.round), JSON.stringify(Object.keys(dj||{})));
  pruef("Loch 1 mit Score 5 drin", !!(dj&&(dj.round.holes||[]).some(h=>h.hole===1&&h.score===5)));
  pruef("Loch-Zeitstempel gesendet", !!(dj&&(dj.round.holes||[]).some(h=>h.ts)));
  pruef("live-Zeiger für die Uhr", !!(dj&&dj.live&&dj.live.hole), JSON.stringify(dj&&dj.live));

  /* ---------- die „Uhr" trägt Loch 2 ein und wechselt das Loch ---------- */
  kopf("Uhr → Handy: Eingabe und Lochwechsel");
  const uhr=JSON.parse(JSON.stringify(dj));
  const spaeter=new Date(Date.now()+60000).toISOString();
  uhr.ts=spaeter;
  uhr.round.holes=(uhr.round.holes||[]).filter(h=>h.hole!==2);
  uhr.round.holes.push({hole:2,par:3,score:4,putts:2,tee:"Grün getroffen",ts:spaeter});
  uhr.live={src:"watch",hole:2,at:spaeter,course:uhr.round.course,date:uhr.round.date,side:uhr.round.side};
  REPO["draft.json"]=JSON.stringify(uhr); SHAS["draft.json"]="sha-uhr";

  const p=await R(`draftPull()`);
  pruef("Handy liest den Uhr-Entwurf", !!(p&&p.draft), JSON.stringify(p&&Object.keys(p)));
  const merged=R(`(function(){ var dr=mergeDraft(DB._draftRound, ${JSON.stringify(uhr)}, (DB.ui||{}).draftDiscardedTs||"");
     DB._draftRound=dr; var v=playAdoptDraft(); var h2=(PLAY.holes||[]).find(function(x){return x.hole===2;});
     return {uebernommen:v, score2:h2&&h2.score, putts2:h2&&h2.putts, tee2:h2&&h2.tee,
             score1:(PLAY.holes[0]||{}).score}; })()`);

  pruef("Score von Loch 2 übernommen", merged.score2===4, JSON.stringify(merged));
  pruef("Tee-Ergebnis von Loch 2 übernommen", merged.tee2==="Grün getroffen", String(merged.tee2));
  pruef("eigene Eingabe auf Loch 1 bleibt", merged.score1===5, String(merged.score1));
  const zeiger=R(`playAdoptRemoteHole()`);
  pruef("Lochzeiger der Uhr übernommen", zeiger===true || R(`PLAY.idx`)===1, "idx="+R(`PLAY.idx`));


  /* ---------- Korrektur am Handy muss auf der Uhr ankommen ---------- */
  kopf("Änderung am Handy → Uhr (der Fall aus der Praxis)");
  R(`PLAY.idx=1; PLAY.holes[1].tee="Rough links"; playSaveDraft(); "ok"`);
  await R(`draftPush()`);
  let dj2=JSON.parse(REPO["draft.json"]||"{}");
  const l2=(dj2.round.holes||[]).find(h=>h.hole===2);
  pruef("korrigiertes Tee-Ergebnis steht im Repo", l2 && l2.tee==="Rough links", JSON.stringify(l2&&l2.tee));
  pruef("und trägt einen NEUEREN Zeitstempel als die Uhr-Eingabe",
    !!(l2 && l2.ts && l2.ts>=spaeter), (l2&&l2.ts)+" vs "+spaeter);

  /* ---------- ÄNDERUNG eines bereits gesendeten Wertes ---------- */
  kopf("Korrektur eines Wertes — kommt sie im Repo an?");
  R(`PLAY.idx=0; PLAY.holes[0].score=5; PLAY.holes[0].tee="Hit"; playSaveDraft(); "ok"`);
  await R(`draftPush()`); await new Promise(r=>setImmediate(r));
  let dA=JSON.parse(REPO["draft.json"]||"{}");
  let hA=((dA.round&&dA.round.holes)||[]).find(h=>h.hole===1)||{};
  pruef("erste Eingabe steht im Repo", hA.score===5 && hA.tee==="Hit", JSON.stringify([hA.score,hA.tee]));
  const tsA=dA.ts, htsA=hA.ts;
  await new Promise(r=>setTimeout(r,10));
  R(`PLAY.holes[0].score=6; PLAY.holes[0].tee="Links"; playSaveDraft(); "ok"`);
  await R(`draftPush()`); await new Promise(r=>setImmediate(r));
  const dB=JSON.parse(REPO["draft.json"]||"{}");
  const hB=((dB.round&&dB.round.holes)||[]).find(h=>h.hole===1)||{};
  /* DER FEHLER BIS v3.31: `playSaveDraft` erneuerte nur `live`, nicht `round`
     und `ts` — gesendet wurde weiter der Stand vom Rundenstart. Der ERSTE Wert
     kam an (die Uhr füllte ein leeres Feld), jede ÄNDERUNG nie. */
  pruef("Korrektur steht im Repo", hB.score===6 && hB.tee==="Links", JSON.stringify([hB.score,hB.tee]));
  pruef("Entwurfs-Zeitstempel erneuert", dB.ts!==tsA, tsA+" → "+dB.ts);
  pruef("Loch-Zeitstempel erneuert", hB.ts!==htsA, htsA+" → "+hB.ts);
  /* Und die Regel der Uhr: Nur ein JÜNGERER fremder Stand wird übernommen. */
  pruef("Uhr würde die Korrektur übernehmen", !!(hB.ts && htsA && hB.ts>htsA));

  /* ---------- Simulationsmodus ---------- */
  kopf("Simulationsmodus");
  const sim = JSON.parse(R(`(function(){
    var r={};
    var hr=holeRef(playGeo(), PLAY.holes[0].hole);
    r.vorher=simAktiv();
    simStart();
    r.an=simAktiv();
    r.startAmTee=Math.round(geoDist(PLAY.here, hr.tee));
    /* Position 150 m vor dem Gruen setzen — wie ein Tipp auf die Karte. */
    var mLat=110540;
    var ziel=[hr.green[0]-150/mLat, hr.green[1]];
    simSetzePosition(ziel);
    r.restNachTipp=Math.round(geoDist(PLAY.here, hr.green));
    /* REGEL 1: nichts schreiben. */
    var vorEntwurf=JSON.stringify(DB._draftRound||null);
    playSaveDraft();
    r.entwurfUnberuehrt = JSON.stringify(DB._draftRound||null)===vorEntwurf;
    /* Der Caddy MUSS auf die simulierte Position reagieren. */
    var c=playCaddyNow();
    r.caddyRest = c && c.rest;
    r.caddyClub = !!(c && c.club);
    simStop();
    r.aus=!simAktiv();
    r.positionBleibt=!!PLAY.here;
    return JSON.stringify(r);
  })()`));
  pruef("Simulation lässt sich starten", sim.vorher===false && sim.an===true);
  pruef("startet am Abschlag", sim.startAmTee<5, sim.startAmTee+" m");
  /* Der eigentliche Zweck: Ein Tipp verschiebt die Position, und der Caddy
     rechnet FUER DIESEN PUNKT — sonst waere der Modus nur Zierde. */
  pruef("Tipp setzt die Position", Math.abs(sim.restNachTipp-150)<6, sim.restNachTipp+" m zum Grün");
  pruef("Caddy rechnet für die simulierte Position",
    sim.caddyClub && Math.abs(sim.caddyRest-150)<6, String(sim.caddyRest));
  /* REGEL 1: Eine Fingeruebung darf nicht in den Daten landen. */
  pruef("Entwurf bleibt unberührt", sim.entwurfUnberuehrt===true);
  pruef("Simulation lässt sich beenden", sim.aus===true);
  pruef("Position bleibt danach stehen", sim.positionBleibt===true);

  /* ---------- Streuungsoval: kein Rest vom vorigen Zustand ---------- */
  kopf("Streuungsoval");
  R(`PLAY.stratOval={center:[54.9,10.9],brg:0,sigL:7,sigD:9,biasL:0}; "ok"`);
  const weitWeg = R(`(function(){
    var alt=PLAY.here;
    PLAY.here=[PLAY.holes[0]&&0||54.0-0.03, 10.77];
    var tf=playTooFar(); pfCaddyKurz();
    var ov=PLAY.stratOval; PLAY.here=alt;
    return JSON.stringify({tf:tf, ovalDanach:!!ov});
  })()`);
  const w = JSON.parse(weitWeg);
  pruef("Position gilt als zu weit", w.tf!=null && w.tf>1000, String(w.tf));
  /* DER FEHLER BIS v3.48: Das Oval blieb stehen, wo es beim letzten Mal
     gerechnet wurde — und sah aus wie eine Empfehlung, 50 m hinter dem Grün. */
  pruef("altes Oval ist gelöscht", w.ovalDanach===false);

  /* ---------- Lochwechsel: sofort und klein ---------- */
  kopf("Lochwechsel");
  const shaVor=SHAS["draft.json"];
  R(`playNext(); "ok"`);
  /* Etwas mehr Luft als ein Tick: Seit v3.32 kann ein Push mehrere Anlaeufe
     mit Pause brauchen. Ein einzelnes setImmediate prueft sonst, bevor der
     Schreibvorgang ueberhaupt begonnen hat. */
  await new Promise(r=>setTimeout(r,50));
  pruef("Lochwechsel schreibt sofort", SHAS["draft.json"]!==shaVor, "sha "+shaVor+" -> "+SHAS["draft.json"]);
  /* Nach der Korrekturpruefung steht der Zeiger auf Loch 1, also fuehrt der
     Wechsel auf 2 — die Zahl haengt an der Reihenfolge der Pruefungen, nicht
     an der Sache. Geprueft wird deshalb, dass er WEITER gewandert ist. */
  pruef("Zeiger ist weitergewandert", R(`PLAY.holes[PLAY.idx].hole`)===2, String(R(`PLAY.holes[PLAY.idx].hole`)));

  /* ---------- watch.json wird geschrieben ---------- */
  kopf("watch.json im Repo");
  await R(`watchFilePush(true)`);
  await new Promise(r=>setImmediate(r));
  pruef("watch.json geschrieben", !!REPO["watch.json"]);
  let wj=null; try{ wj=JSON.parse(REPO["watch.json"]||"{}"); }catch(e){}
  /* Der gespielte Platz MUSS drin sein — ohne ihn hat die Uhr keine Lochliste.
     Seine Karte darf es seit v4.84 NICHT mehr sein (siehe oben). */
  pruef("enthält den gespielten Platz",
    !!(wj && (wj.courses||[]).some(c=>c.name===R(`PLAY.course`))),
    (wj&&(wj.courses||[]).map(c=>c.name).join(", "))||"-");
  pruef("und zwar ohne Karte",
    !!(wj && !(wj.courses||[]).some(c=>c.geo)),
    (wj&&(wj.courses||[]).filter(c=>c.geo).map(c=>c.name).join(", "))||"keiner");

  /* ==========================================================================
     ALLE 18 LOECHER DURCHFAHREN (v3.53)
     --------------------------------------------------------------------------
     WOZU: Die Pruefungen davor sind Einzelfaelle — ein Loch, eine Position. Die
     Fehler der letzten Tage waren aber KEINE Sonderfaelle, sie traten auf jedem
     Loch auf und fielen nur am Bild auf: der Massstabsfehler (Karte gegen
     Zielkette), der Layup-Stumpf, das klebende Oval. Alle drei haetten hier
     angeschlagen.
     JE LOCH FUENF POSITIONEN, weil die Fehler sich mit der Entfernung
     unterscheiden: am Abschlag stimmte der Massstab fast, am Gruen lag er 60 m
     daneben. Genommen werden Tee, Landezone, 150 m, 100 m und Gruenrand.
     WAS GEPRUEFT WIRD, sind INVARIANTEN — Aussagen, die auf JEDEM Loch und aus
     JEDER Lage gelten muessen. Kein Soll-Wert, den man nachtraeglich anpasst,
     sondern Saetze, deren Verletzung immer ein Fehler ist:
       1. Die Zielkette endet auf dem Gruen (< 5 m).
       2. Sie beginnt am Startpunkt (< 5 m).
       3. Kein Einzelschlag ist laenger als der laengste Schlaeger + 15 %.
       4. Kein Zwischenschlag laesst weniger als 60 m uebrig (Layup-Stumpf).
       5. Jede Empfehlung nennt einen Schlaeger, der im Beutel liegt.
       6. Distanzen sind endlich und positiv (kein NaN, kein Unendlich).
       7. Die Restdistanz nimmt entlang der Kette ab.
     GEZAEHLT WIRD PRO REGEL, nicht pro Fall: 18 Loecher x 5 Lagen sind 90
     Pruefungen je Regel — einzeln gemeldet waere das eine unlesbare Wand. Eine
     Regel gilt als bestanden, wenn sie NIRGENDS verletzt wird, und die erste
     Verletzung steht mit Loch und Lage dabei. */
  /* ---------- HOEHENRASTER FUER DEN DURCHLAUF (2026-08-21, PWA v4.5) ----------
     Die Hoehenkette (PWA v3.95–v4.3) war bisher nur in `tests.js` an gebauten
     Einzelfaellen geprueft. Hier bekommt sie 90 echte Lagen. Gebaut wird ein
     Gelaende mit ausgepraegtem Profil — eine Senke in der Mitte jeder Bahn,
     ein hoeher liegendes Gruen —, weil ein flacher Platz nichts beweisen
     wuerde: Ohne Hoehenunterschiede sehen „gerechnet und eben" und „gar nicht
     gerechnet" identisch aus, und genau diese Verwechslung war der Fehler
     v3.97/v4.2. */
  R(`(function(){
    function WETTER_SIM(){ /* 6 m/s: die Bedingungen aus der Meldung vom 21.08. und an der Ostsee
         voellig gewoehnlich. Mit 3,9 m/s blieb die gespielte Distanz zu nah an
         der gemessenen — die Kettenregel haette den Fehler nicht gefangen,
         fuer den sie geschrieben wurde. */
      WEATHER={temp:15, windMs:6.0, windDir:225, gustMs:9.5, at:Date.now()}; }
    var geo=playGeo(); if(!geo) return "0";
    var R2=dgmRahmen(geo); if(!R2) return "0";
    var rec={course:PLAY.course, la0:R2.la0, lo0:R2.lo0, dLa:R2.dLa, dLo:R2.dLo,
             mLat:R2.mLat, mLng:R2.mLng, nx:R2.nx, ny:R2.ny,
             h:new Int16Array(R2.nx*R2.ny).fill(DGM_LEER), quelle:"DGM1", stand:"test"};
    /* Wellenfoermiges Gelaende: rund 12 m Amplitude ueber die Bbox, damit auf
       jeder Bahn sowohl Anstiege als auch Gefaelle vorkommen. */
    for(var y=0;y<R2.ny;y++) for(var x=0;x<R2.nx;x++){
      var hh=100 + 6*Math.sin(y/9) + 5*Math.cos(x/7) + y*0.02;
      rec.h[y*R2.nx+x]=Math.round(hh*10);
    }
    dgmSetzen(rec);
    /* WETTER MITGEBEN. Ohne Wetter greift die Schweigeschwelle, die v3.97
       entfernt hat, nie — die Gegenprobe (Schwelle wieder einbauen) lief
       durch, und die Regel waere fuer genau diesen Rueckfall blind gewesen.
       Werte wie am Meldetag: 14 km/h Grundwind aus Suedwest, 18 Grad. */
    try{ WETTER_SIM(); }catch(e){}
    return "1";
  })()`);

  kopf("Alle 18 Löcher, fünf Lagen je Loch");
  const durchlauf = JSON.parse(R(`(function(){
    var geo=playGeo(), beutel={}, reichweite={}, maxCarry=0;
    (DB.clubDistances||[]).forEach(function(c){
      beutel[c.club]=1; var d=(c.carry!=null?c.carry:c.reach)||0; reichweite[c.club]=d; if(d>maxCarry) maxCarry=d; });
    var regeln={endeAufGruen:0, startAmPunkt:0, schlagZuLang:0, layupStumpf:0,
                schlaegerFremd:0, zahlKaputt:0, restSteigt:0,
                /* v4.5 — die Hoehenkette auf echten Lagen: */
                zeileFehlt:0, hoeheUnbekannt:0, hoeheUnsinn:0,
                kettePlays:0, ketteZuKurz:0, zweiMeinungen:0, zweiZahlen:0};
    var ersteVerletzung={};
    var faelle=0;
    function merke(regel, text){
      regeln[regel]++;
      if(!ersteVerletzung[regel]) ersteVerletzung[regel]=text;
    }
    for(var i=0;i<PLAY.holes.length;i++){
      PLAY.idx=i;
      var h=PLAY.holes[i], hr=holeRef(geo,h.hole);
      if(!hr||!hr.tee||!hr.green) continue;
      var gesamt=geoDist(hr.tee,hr.green);
      /* Fuenf Lagen: Tee, Landezone, 150 m, 100 m, Gruenrand. Ueber den
         Anteil gerechnet, damit es auf einem Par 3 wie auf einem Par 5 passt. */
      var lagen=[
        {name:"Tee", p:hr.tee},
        {name:"Landezone", p:_aimLerp(hr.tee,hr.green,Math.min(gesamt*0.55,220))},
        {name:"150 m", p:_aimLerp(hr.tee,hr.green,Math.max(0,gesamt-150))},
        {name:"100 m", p:_aimLerp(hr.tee,hr.green,Math.max(0,gesamt-100))},
        {name:"Grünrand", p:_aimLerp(hr.tee,hr.green,Math.max(0,gesamt-18))}
      ];
      for(var L=0;L<lagen.length;L++){
        var lage=lagen[L];
        if(!lage.p) continue;
        PLAY.here=lage.p.slice(); PLAY.aimChainKey=null; PLAY.stratOval=null;
        var wo="Loch "+h.hole+" ("+lage.name+")";
        faelle++;
        var ch=null;
        try{ ch=playAimChain(true); }catch(e){ merke("zahlKaputt", wo+": Ausnahme "+e.message); continue; }
        if(!ch||!ch.pts||ch.pts.length<2){ merke("zahlKaputt", wo+": keine Kette"); continue; }
        var pts=ch.pts, legs=ch.legs||[];
        /* 1 + 2 */
        var abEnde=geoDist(pts[pts.length-1], hr.green);
        if(!(abEnde<5)) merke("endeAufGruen", wo+": Ende "+Math.round(abEnde)+" m vom Grün");
        var abStart=geoDist(pts[0], PLAY.here);
        /* Ab 1 km plant die App bewusst AB TEE (CADDY_TEE_AB_M) — dann ist der
           Abschlag der richtige Startpunkt, nicht die eigene Position. */
        var sollStart=(geoDist(PLAY.here,hr.green)>1000)?hr.tee:PLAY.here;
        if(!(geoDist(pts[0], sollStart)<5))
          merke("startAmPunkt", wo+": Start "+Math.round(geoDist(pts[0],sollStart))+" m daneben");
        /* 3 + 4 + 7 */
        var vorherRest=geoDist(pts[0],hr.green);
        for(var k=0;k<pts.length-1;k++){
          var laenge=geoDist(pts[k],pts[k+1]);
          if(!(isFinite(laenge)&&laenge>=0)) merke("zahlKaputt", wo+": Schlaglänge "+laenge);
          else if(laenge>maxCarry*1.15)
            merke("schlagZuLang", wo+": Schlag "+Math.round(laenge)+" m > "+Math.round(maxCarry*1.15));
          var restDanach=geoDist(pts[k+1],hr.green);
          if(restDanach>vorherRest+2)
            merke("restSteigt", wo+": Rest wächst "+Math.round(vorherRest)+" → "+Math.round(restDanach));
          vorherRest=restDanach;
          /* Zwischenschlag (nicht der letzte): Stumpf? */
          /* SCHWELLE 35 m, wie die Regel selbst (v3.57): Sie raeumt Stuempfe
             weg — ein Layup, der 13 m vor dem Gruen endet und danach einen
             Chip erzwingt —, sie optimiert NICHT auf einen „vollen" Wedge.
             Nach Broadie ist naeher fast immer besser; 49 m sind ein normaler
             Schlag. Eine Invariante, die strenger ist als die Regel, meldet
             deshalb Absicht als Fehler. */
          if(k<pts.length-2 && restDanach<35)
            merke("layupStumpf", wo+": nur "+Math.round(restDanach)+" m Rest nach Schlag "+(k+1));
        }
        /* 8 — DIE BEDINGUNGSZEILE STEHT IMMER (PWA v3.97/v4.2).
           Sie ist dreimal verschwunden, zuletzt im Annaeherungs-Zweig, und
           jedes Mal war die Meldung dieselbe: „ich sehe kein spielt wie".
           Eine Regel ueber 90 Lagen faengt das, eine Prosa-Pruefung nicht. */
        /* GERENDERT, NICHT GERUFEN. Erster Versuch rief condZeile() direkt —
           und fiel prompt durch die Gegenprobe: Die Zeile war ja da, sie wurde
           nur in EINEM der drei Caddy-Zweige nicht eingebaut. Eine Regel, die
           die Bausteine prueft statt das Ergebnis, sieht genau den Fehler
           nicht, fuer den sie geschrieben wurde. Deshalb hier der echte
           Ausgabeweg. */
        var zeile="", html="";
        try{
          var mid=Math.round(geoDist(PLAY.here,hr.green));
          html=playCaddyHtml(mid, h.par)||"";
          /* AUF DIE SACHE PRUEFEN, NICHT AUF DAS MARKUP. Erster Versuch suchte
             nach der Klasse pc-why — und meldete 24 Fehler, die keine waren:
             Der Regel-Zweig baut dieselbe Auskunft in eine wx-line. Eine Regel,
             die an einer CSS-Klasse haengt, prueft die Schreibweise statt die
             Sache; genau davon gibt es hier schon zu viele.
             Verlangt wird deshalb nur, dass die AUSSAGE dasteht: eine
             spielt-wie-Angabe UND ein Hoehenzeichen.
             (Keine Regex: Dieser Block steht in einem Template-Literal, in dem
             Backslash-s seinen Backslash verliert.) */
          var tief=html.toLowerCase();
          zeile=(tief.indexOf("spielt wie")>=0) ? "ja" : "";
          if(zeile && html.indexOf("⛰")<0) zeile="";
        }catch(e2){ merke("zahlKaputt", wo+": playCaddyHtml "+e2.message); }
        if(!zeile) merke("zeileFehlt", wo+": keine spielt-wie/Höhen-Angabe · "+(html?html.slice(0,110):"LEERE Ausgabe"));

        /* 9 — MIT RASTER IST DIE HOEHE BEKANNT.
           Der Durchlauf legt oben eins an, das den ganzen Streifen abdeckt.
           Steht hier „unbekannt", ist entweder das Raster nicht geladen (v4.2)
           oder die Lage faellt aus dem Streifen (v4.3) — beides waren echte
           Fehler, beide sahen fuer den Benutzer gleich aus. */
        var dEl=null;
        try{ dEl=elevDelta(PLAY.here, hr.green); }catch(e3){ dEl=null; }
        if(dEl===null) merke("hoeheUnbekannt", wo+": elevDelta null trotz Raster");
        else if(!isFinite(dEl)) merke("hoeheUnsinn", wo+": elevDelta "+dEl);
        else if(Math.abs(dEl)>60) merke("hoeheUnsinn", wo+": "+Math.round(dEl)+" m Hoehenunterschied");

        /* 13 — EINE EMPFEHLUNG, NICHT ZWEI (v4.16).
           Kopfzeile und Karte nannten verschiedene Schlaeger fuer denselben
           Schlag — bei identischer Bewertung. Geprueft wird auf jeder Lage:
           Was playCaddyNow() sagt, muss dem entsprechen, was die Kette
           zeichnet. Nicht „aehnlich", sondern gleich; alles andere ist fuer
           den Leser ein Widerspruch, egal wie gut begruendet. */
        try{
          var nw=playCaddyNow(), L0=legs[0];
          if(nw && nw.club && L0 && L0.club && nw.club!==L0.club)
            merke("zweiMeinungen", wo+": Kopfzeile "+nw.club+" · Karte "+L0.club);
          if(nw && nw.spieltWie!=null && L0 && L0.spielt!=null
             && Math.abs(nw.spieltWie-L0.spielt)>=5)
            merke("zweiZahlen", wo+": Kopfzeile "+nw.spieltWie+" m · Karte "+L0.spielt+" m");
          /* DIE ECHTE PRUEFUNG (v4.63): spieltWie wird aus der Kette
             uebernommen — die Zeile darueber vergleicht also eine Zahl mit
             sich selbst und kann nie ausschlagen. Im Feld lief die Warnung
             „spielt-wie uneinig" trotzdem dutzendfach: Sie verglich die
             EIGENE Rechnung der Kopfzeile (spieltWieKopf) mit der Kette.
             Genau die gehoert geprueft — sonst ist die Invariante Zierde.
             OFFEN: Der Testplatz hat keine Doglegs und keine gezogenen
             Wegpunkte, deshalb fallen Bewertungsziel und Kettenziel hier
             ohnehin zusammen — die Invariante schlaegt also (noch) nicht aus,
             auch wenn man die Vereinheitlichung zurueckbaut. Sie greift erst
             auf einem Platz mit Knick. Das ist eine Luecke des PLATZES, nicht
             der Pruefung; sie gehoert benannt statt uebersehen.
             KEINE RUECKWAERTS-ANFUEHRUNGSZEICHEN in diesem Block: Er steht in
             einem Vorlagentext, und ein einzelnes beendet ihn. Derselbe Fehler
             wie in v3.96 und v4.5 — der Pruefstand warnt seit v4.17 davor. */
          if(nw && nw.spieltWieKopf!=null && L0 && L0.spielt!=null
             && Math.abs(nw.spieltWieKopf-L0.spielt)>=5)
            merke("zweiZiele", wo+": Kopfzeile rechnet "+nw.spieltWieKopf
              +" m, Kette "+L0.spielt+" m — verschiedene Zielpunkte");
        }catch(e4){ merke("zahlKaputt", wo+": playCaddyNow "+e4.message); }

        /* 11 — DIE KARTE MUSS MIT DER KOPFZEILE UEBEREINSTIMMEN (v4.15).
           Die Kette waehlte ihren Schlaeger nach der GEOMETRISCHEN Strecke,
           die Kopfzeile rechnete Wind und Hoehe mit. Auf der Karte stand dann
           „3 Wood · 217 m", darueber „spielt wie 240 m" — mit dem 3 Wood
           kommt man an diesen Punkt nie. Geprueft wird deshalb auf jeder Lage:
           Traegt der eingezeichnete Schlaeger die GESPIELTE Distanz? */
        for(var q=0;q<legs.length;q++){
          var Lg=legs[q]; if(!Lg || !Lg.club || Lg.role==="Grün") continue;
          if(Lg.spielt==null){ merke("kettePlays", wo+": Teilstrecke ohne spielt-wie"); continue; }
          var reach2=reichweite[Lg.club]||0;
          /* 5 % Reserve: Ein Schlaeger darf knapp sein, aber nicht chancenlos.
             12 % waren zu lasch — 6 m/s Gegenwind machen rund 9 %, der
             gemeldete Fall waere durchgerutscht. Die Schwelle muss unter der
             Wirkung liegen, die sie fangen soll. */
          if(reach2>0 && Lg.spielt > reach2*1.05)
            merke("ketteZuKurz", wo+": "+Lg.club+" ("+Math.round(reach2)+" m) für "
              +Lg.spielt+" m gespielt ("+Lg.dist+" m gemessen)");
        }

        /* 5 + 6 */
        for(var q=0;q<legs.length;q++){
          var l=legs[q];
          if(l.club && !beutel[l.club]) merke("schlaegerFremd", wo+": „"+l.club+"“ nicht im Bag");
          if(l.dist!=null && !(isFinite(l.dist)&&l.dist>0)) merke("zahlKaputt", wo+": dist "+l.dist);
        }
      }
    }
    return JSON.stringify({faelle:faelle, regeln:regeln, erste:ersteVerletzung});
  })()`));

  pruef("90 Lagen durchgefahren", durchlauf.faelle>=85, durchlauf.faelle+" Fälle");
  /* Der erste Befund dieses Durchlaufs war echt: Auf Loch 3 (Par 5) plante die
     Kette aus der Landezone einen Schlag von 259 m — laenger als jeder
     Schlaeger — und die Restdistanz WUCHS dabei. Zur Diagnose wird der Fall
     einmal ausgeschrieben. */
  if((durchlauf.regeln.schlagZuLang||0)>0 || (durchlauf.regeln.restSteigt||0)>0){
    const fall=JSON.parse(R(`(function(){
      var geo=playGeo();
      for(var i=0;i<PLAY.holes.length;i++){
        PLAY.idx=i; var h=PLAY.holes[i], hr=holeRef(geo,h.hole);
        if(!hr||!hr.tee||!hr.green) continue;
        var gesamt=geoDist(hr.tee,hr.green);
        PLAY.here=_aimLerp(hr.tee,hr.green,Math.min(gesamt*0.55,220));
        PLAY.aimChainKey=null;
        var ch=playAimChain(true); if(!ch||!ch.pts) continue;
        for(var k=0;k<ch.pts.length-1;k++){
          if(geoDist(ch.pts[k],ch.pts[k+1])>240 || geoDist(ch.pts[k+1],hr.green)>geoDist(ch.pts[k],hr.green)+2){
            return JSON.stringify({loch:h.hole, par:h.par, lochlaenge:Math.round(gesamt),
              restVonHier:Math.round(geoDist(PLAY.here,hr.green)),
              schlaege:ch.pts.slice(1).map(function(p,idx){
                return {laenge:Math.round(geoDist(ch.pts[idx],p)), restDanach:Math.round(geoDist(p,hr.green))};
              }),
              rollen:(ch.legs||[]).map(function(l){ return l.role+":"+(l.club||"?"); })});
          }
        }
      }
      return JSON.stringify(null);
    })()`));
    if(fall) console.log("   Befund:", JSON.stringify(fall));
  }
  const RG={
    endeAufGruen:"Zielkette endet auf dem Grün",
    startAmPunkt:"Zielkette beginnt am richtigen Startpunkt",
    schlagZuLang:"kein Schlag über die längste Schlägerlänge",
    layupStumpf:"kein Layup mit Reststummel",
    schlaegerFremd:"nur Schläger aus dem Bag",
    zahlKaputt:"keine kaputten Zahlen (NaN/Unendlich/Ausnahme)",
    restSteigt:"Restdistanz nimmt entlang der Kette ab",
    zeileFehlt:"„Spielt wie\" steht auf jeder Lage — mit Höhenangabe",
    hoeheUnbekannt:"Höhe ist mit geladenem Raster nie unbekannt",
    hoeheUnsinn:"Höhenunterschiede bleiben endlich und plausibel",
    kettePlays:"jede Teilstrecke kennt ihre gespielte Distanz",
    ketteZuKurz:"der eingezeichnete Schläger trägt die GESPIELTE Distanz",
    zweiMeinungen:"Kopfzeile und Karte nennen denselben Schläger",
    zweiZahlen:"Kopfzeile und Karte nennen dieselbe gespielte Distanz"
  };
  Object.keys(RG).forEach(k=>{
    const n=durchlauf.regeln[k]||0;
    pruef(RG[k], n===0, n?`${n}× verletzt · zuerst: ${durchlauf.erste[k]}`:"");
  });

  /* ---------- Leitplanken: wirken sie auf echten Lagen? ---------- */
  kopf("Leitplanken über den ganzen Platz");
  const lp = JSON.parse(R(`(function(){
    var geo=playGeo(), kippt=0, faelle=0, teeIron=0, teeDriver=0, aufschlaege=0, liste=[];
    for(var i=0;i<PLAY.holes.length;i++){
      PLAY.idx=i;
      var h=PLAY.holes[i], hr=holeRef(geo,h.hole);
      if(!hr||!hr.tee||!hr.green) continue;
      var gesamt=geoDist(hr.tee,hr.green);
      /* Lagen, aus denen das Gruen NICHT erreichbar ist — dort entscheidet die
         Layup-Regel. */
      [0.35,0.5].forEach(function(a){
        var p=_aimLerp(hr.tee,hr.green,gesamt*a);
        if(!p) return;
        var rest=geoDist(p,hr.green);
        var ev=null;
        try{ ev=STRAT.nextShot(geo,PLAY.course,h.hole,p,'safe',20); }catch(e){ return; }
        if(!ev||!ev.best) return;
        faelle++;
        if(ev.best.lpAuf) aufschlaege++;
        /* Ein Reststummel darf nicht mehr gewinnen, wenn das Gruen ausser
           Reichweite liegt. */
        var maxC=0; (DB.clubDistances||[]).forEach(function(c){ var d=(c.carry!=null?c.carry:c.reach)||0; if(d>maxC) maxC=d; });
        /* 25 m, nicht 60 (v3.63): Zwischen 25 und 85 m entscheidet die RECHNUNG —
           naeher ist dort messbar besser. Nur der abgebrochene
           Annaeherungsschlag unter 25 m ist unsinnig. */
        /* KIPPT der Aufschlag eine klare Rechnung? Das waere der Fehler:
           Ein Kandidat, der OHNE Leitplanke deutlich besser ist (mehr als
           0,20 Schlaege), darf nicht durch einen Aufschlag verlieren. */
        if(ev._top && ev._top.length>1){
          const roh=ev._top.slice().sort((a,b)=>(a.score-(a.lpAuf||0))-(b.score-(b.lpAuf||0)));
          const bestRoh=roh[0];
          if(bestRoh && bestRoh.club.name!==ev.best.club.name){
            const dRoh=(ev.best.score-(ev.best.lpAuf||0))-(bestRoh.score-(bestRoh.lpAuf||0));
            if(dRoh>0.20){ kippt++;
              liste.push("Loch "+h.hole+": "+bestRoh.club.name+" war roh um "+
                dRoh.toFixed(2)+" besser, verlor gegen "+ev.best.club.name); }
          }
        }
        /* Und vom Boden nie ein Driving Iron. */
        if(/driving[\\s-]?iron|2[\\s-]?(iron|eisen)/i.test(ev.best.club.name||"")) teeIron++;
        /* Seit v4.81.2 auch nie ein DRIVER — die Leitplanke kennt drei
           Zustaende; hier ist die Position nachweislich vom Boden. */
        if(/driver/i.test(ev.best.club.name||"")) teeDriver++;
      });
    }
    return JSON.stringify({teeDriver:teeDriver, faelle:faelle, kippt:kippt, teeIron:teeIron, aufschlaege:aufschlaege, liste:liste});
  })()`));
  pruef("Lagen ohne Grün-Reichweite geprüft", lp.faelle>=20, lp.faelle+" Fälle");
  /* DIE REGEL, DIE DEN AUSSCHLAG GAB: Vorher gewann aus 240 m der 3 Wood mit
     41 m Reststummel; jetzt das 8 Iron mit 109 m Rest. */
  /* ==========================================================================
     DIESE PRUEFUNG WAR FALSCH GESTELLT (v3.64)
     --------------------------------------------------------------------------
     Sie verlangte, dass NIE ein Schlag mit Reststummel gewinnt — also ein VETO.
     Die Leitplanke ist aber ausdruecklich ein ANSTOSS: „schwach genug, um eine
     echte Differenz nie zu kippen" steht in ihrer eigenen Begruendung.
     Nachgerechnet an den zwei uebrig gebliebenen Faellen:
       Loch 17, 213 m Rest: 3 Wood laesst 12 m, das 5 Iron ~40 m.
     Aus 213 m ist das Gruen unerreichbar, also entscheidet, von wo man den
     naechsten Schlag spielt — und 12 m schlagen 40 m deutlich. Die Rechnung hat
     recht, meine Trainerregel nicht. Haette ich den Aufschlag erhoeht, bis die
     Pruefung gruen wird, haette ich eine ausgedachte Zahl gegen eine gemessene
     durchgesetzt. Genau davor warnt der Kommentar an der Leitplanke.
     GEPRUEFT WIRD JETZT DIE ARCHITEKTUR, nicht mein Bauchgefuehl:
       · Der Aufschlag wird ueberhaupt angewandt (sonst ist die Tabelle Zierde).
       · Er kippt keine deutliche Differenz (sonst waere er ein Veto).
       · Er traegt einen nachlesbaren Grund. */
  pruef("Aufschläge werden angewandt", lp.aufschlaege>0, lp.aufschlaege+" Fälle");
  pruef("Aufschlag kippt keine klare Rechnung", lp.kippt===0,
    lp.kippt?`${lp.kippt}× · ${(lp.liste||[]).join(" | ")}`:"");
  pruef("vom Boden nie ein Driving Iron", lp.teeIron===0, String(lp.teeIron));
  pruef("vom Boden nie ein Driver (v4.81.2)", lp.teeDriver===0, lp.teeDriver+" Fälle");

  /* ---------- Verwerfen wirkt auf beiden Seiten ---------- */

  /* ======================================================================
     SCHLAGMESSUNG DER UHR — DER GANZE WEG (neu 27.08.2026)
     ----------------------------------------------------------------------
     WARUM DAS HIER FEHLTE UND DIE GROESSTE LUECKE WAR: Seit Uhr-Fassung 38/40
     ist das Schlagtracken der EINZIGE Zweck der Uhr. Sie misst rohe Meter und
     schickt sie herueber; die gesamte Anpassungslogik sitzt im Handy
     (`schlagNeutral`, `gpsShotsNachziehen`, v4.80.1). Diese Kette war in der
     Simulation ueberhaupt nicht abgedeckt — geprueft wurden Distanzen, Caddy
     und Eingaben, also genau das, was die Uhr NICHT mehr tut.
     GEPRUEFT WIRD DER ECHTE WEG: ein Schlag in EXAKT der Form, die
     `MainActivity.kt` schreibt (id/ts/club/dist/accA/accB/latA..lngB/hole),
     liegt in `draft.json` — und dann laeuft `draftPull()`, nicht ein direkter
     Aufruf. Ein Test, der die Zwischenfunktion aufruft, prueft die Funktion;
     ein Test, der die Datei fuellt, prueft die KETTE. */
  kopf("Schlagmessung der Uhr — roh rein, neutral raus");

  /* Wetter setzen: GEGENWIND aus Norden auf eine Bahn, die nach Norden
     spielt. Ein Schlag gegen den Wind war MEHR wert, als er aussah — sein
     Neutralwert muss also GROESSER sein als die gemessene Strecke. Das ist
     die Richtung, die man beim Vorzeichen am leichtesten verdreht, und ein
     verdrehtes Vorzeichen lehrt die Schlaegerlaengen systematisch falsch. */
  R(`wetterSetzen({temp:12, windMs:8, windDir:0, gustMs:10, at:Date.now()}); "ok"`);
  const hr1=R(`(function(){ const hr=holeRef(playGeo(),1); return {tee:hr.tee, green:hr.green}; })()`);
  /* Startpunkt Tee, Endpunkt 150 m Richtung Gruen — dieselbe Achse wie die
     Bahn, damit die Windrichtung eindeutig Gegenwind ist. */
  const pA=hr1.tee;
  const pB=[hr1.tee[0]+150/mLat, hr1.tee[1]];
  const uhrSchlag={ id:"W1", ts:new Date().toISOString(), club:"7 Iron", dist:150,
                    accA:3.2, accB:2.8, latA:pA[0], lngA:pA[1], latB:pB[0], lngB:pB[1], hole:1 };

  /* DIE UHR TRAEGT IHRE MESSUNGEN MIT, BIS SIE QUITTIERT SIND — sie schickt
     also nicht „den neuen Schlag", sondern IHRE GANZE OFFENE LISTE. Genau
     deshalb muss der Puffer hier kumulieren: Ein Test, der jedes Mal nur den
     letzten Schlag legt, prueft einen Ablauf, den es nicht gibt, und die
     Quittung (`shotAck`) sieht dann immer nur einen einzigen Namen.
     UND DER ZEITSTEMPEL DARF NICHT IN DER ZUKUNFT LIEGEN. Mein erster Anlauf
     stempelte `Date.now()+1000` — damit gewann der Repo-Entwurf jeden Merge
     und ueberschrieb spaeter die gerade eingetragenen Mitspieler. Das sah aus
     wie ein Fehler in `playSaveDraft` und war einer im Pruefaufbau; ein Test,
     der die Uhr unrealistisch schnell macht, findet Fehler, die es nicht
     gibt, und verdeckt die echten. */
  const uhrPuffer=[];
  async function uhrSchickt(neue, mitRunde){
    (neue||[]).forEach(x=>{ if(!uhrPuffer.some(y=>y.id===x.id)) uhrPuffer.push(x); });
    const d=JSON.parse(REPO["draft.json"]||"{}");
    d.gpsShots=uhrPuffer.slice();
    d.ts=new Date().toISOString();
    if(mitRunde===false) delete d.round;
    REPO["draft.json"]=JSON.stringify(d); SHAS["draft.json"]="sha-uhr-"+Math.random();
    await R(`draftPull()`); await new Promise(r=>setImmediate(r));
  }

  await R(`draftPush()`); await new Promise(r=>setImmediate(r));
  const vorher=R(`(DB.gpsShots||[]).length`);
  await uhrSchickt([uhrSchlag]);
  const g1=R(`(DB.gpsShots||[]).find(x=>x.id==="W1")||null`);
  pruef("die Messung der Uhr kommt an", !!g1, JSON.stringify(g1&&g1.id));
  pruef("die rohe Strecke bleibt unangetastet", !!(g1&&g1.dist===150), String(g1&&g1.dist));
  pruef("Neutralwert wird beim Eintreffen gerechnet",
    !!(g1&&g1.distNeutral!=null&&isFinite(g1.distNeutral)), String(g1&&g1.distNeutral));
  /* DIE RICHTUNG IST DIE AUSSAGE: gegen den Wind gemessen heisst „war mehr
     wert" — der Neutralwert liegt UEBER der gemessenen Strecke. */
  pruef("gegen den Wind: Neutralwert größer als gemessen",
    !!(g1&&g1.distNeutral>150), (g1&&g1.distNeutral)+" vs 150 m");

  /* Rueckenwind muss das Vorzeichen umdrehen — sonst waere die Pruefung oben
     auch mit einer Rechnung gruen, die IMMER nach oben korrigiert. */
  R(`wetterSetzen({temp:12, windMs:8, windDir:180, gustMs:10, at:Date.now()}); "ok"`);
  const rueck={...uhrSchlag, id:"W2"};
  await uhrSchickt([rueck]);
  const g2=R(`(DB.gpsShots||[]).find(x=>x.id==="W2")||null`);
  pruef("mit dem Wind: Neutralwert kleiner als gemessen",
    !!(g2&&g2.distNeutral<150), (g2&&g2.distNeutral)+" vs 150 m");

  /* IDEMPOTENZ: Dieselbe Messung zweimal geschickt darf sie nicht verdoppeln.
     Die Uhr trägt ihre Schläge bis zur Quittung (`shotAck`) mit — sie kommen
     also regelmäßig mehrfach an. Eine Verdopplung wäre unsichtbar und würde
     die gelernte Länge in Richtung dieses einen Schlags ziehen. */
  await uhrSchickt([uhrSchlag, rueck]);
  const n1=R(`(DB.gpsShots||[]).filter(x=>x.id==="W1").length`);
  pruef("dieselbe Messung zweimal ändert nichts", n1===1, String(n1));
  pruef("beide Messungen sind da", R(`(DB.gpsShots||[]).length`)===vorher+2,
    R(`(DB.gpsShots||[]).length`)+" statt "+(vorher+2));

  /* EINMAL GERECHNET, DANN FEST: `gpsShotsNachziehen` überspringt, was schon
     einen Neutralwert hat. Sonst würde derselbe Schlag bei jedem Pull mit dem
     Wetter von JETZT neu gerechnet — und der Lernwert wanderte mit dem
     Wetter, statt den Moment des Schlags festzuhalten. */
  const festVor=R(`(DB.gpsShots||[]).find(x=>x.id==="W1").distNeutral`);
  R(`wetterSetzen({temp:30, windMs:0, windDir:0, at:Date.now()}); "ok"`);
  await uhrSchickt([]);
  pruef("ein gerechneter Neutralwert wird nicht neu gerechnet",
    R(`(DB.gpsShots||[]).find(x=>x.id==="W1").distNeutral`)===festVor,
    festVor+" -> "+R(`(DB.gpsShots||[]).find(x=>x.id==="W1").distNeutral`));

  /* SPAETE ANKUNFT — die 3-Stunden-Sperre. Ein Schlag von gestern darf nicht
     mit dem Wetter von heute gerechnet werden; dann bleibt nur die Hoehe.
     Ohne Hoehenraster und ohne frisches Wetter ist die ehrliche Antwort
     `null` — KEIN Neutralwert, nicht etwa der Rohwert unter falschem Namen. */
  R(`wetterSetzen({temp:12, windMs:8, windDir:0, at:Date.now()}); "ok"`);
  const alt={...uhrSchlag, id:"W3", ts:new Date(Date.now()-5*3600*1000).toISOString()};
  await uhrSchickt([alt]);
  const g3=R(`(DB.gpsShots||[]).find(x=>x.id==="W3")||null`);
  const hatRaster=R(`typeof elevDelta==="function" && elevDelta([${pA[0]},${pA[1]}],[${pB[0]},${pB[1]}])!=null`);
  pruef("ein Schlag von vor Stunden nimmt das Wetter von jetzt NICHT",
    !!(g3 && (hatRaster ? g3.distNeutral!==g1.distNeutral : g3.distNeutral==null)),
    "Raster="+hatRaster+" · neutral="+(g3&&g3.distNeutral));
  pruef("und seine rohe Strecke bleibt trotzdem stehen", !!(g3&&g3.dist===150));

  /* WAS DAS HANDY DARAUS LERNT. `clubMeasured` ist die Stelle, an der ein
     Vorzeichenfehler oder ein mitgezaehlter Teilschwung sichtbar wird — und
     zwar erst Wochen spaeter an einer zu kurzen Empfehlung. */
  pruef("die Messung landet in der Lern-Basis",
    R(`neutralBasis((DB.gpsShots||[]).find(x=>x.id==="W1"))`)===festVor,
    String(R(`neutralBasis((DB.gpsShots||[]).find(x=>x.id==="W1"))`)));
  /* Ein TEILSCHWUNG darf die gelernte Laenge nicht nach unten ziehen: ein
     halber Wedge fliegt 55 statt 92 m. Die Uhr setzt `swing` nur, wenn es
     KEIN voller Schwung war — fehlt das Feld, gilt „Voll". */
  const halb={...uhrSchlag, id:"W4", swing:"Halb", dist:70,
              latB:hr1.tee[0]+70/mLat, lngB:hr1.tee[1]};
  await uhrSchickt([halb]);
  pruef("der Teilschwung wird gespeichert",
    !!R(`(DB.gpsShots||[]).some(x=>x.id==="W4")`));
  /* ABER ER ZAEHLT NICHT MIT. `shotsProKlasse` wirft ihn heraus, und
     `clubMeasured` sieht ihn nie. Das ist der Sinn des Schwung-Chips auf der
     Uhr: Ein halber Wedge fliegt 55 statt 92 m; zaehlte er mit, zoege er die
     gelernte Laenge nach unten und der Caddy empfaehle systematisch zu kurz.
     MEIN ERSTER ANLAUF PRUEFTE DAS FALSCHHERUM — ich erwartete W4 in der
     Lern-Liste „als Teilschwung markiert". Er steht dort gar nicht, und das
     ist richtig. Notiert, weil die falsche Erwartung beim naechsten Lesen
     sonst wiederkommt. */
  const lernListe=R(`(function(){ const k=clubNorm("7 Iron");
     return ((shotsProKlasse().gps[k])||[]).map(x=>x.id); })()`);
  pruef("aber er zählt nicht für die gelernte Länge",
    Array.isArray(lernListe) && lernListe.indexOf("W4")<0, JSON.stringify(lernListe));
  pruef("die vollen Schwünge dagegen schon",
    Array.isArray(lernListe) && lernListe.indexOf("W1")>=0, JSON.stringify(lernListe));

  /* QUITTUNG: Die Uhr traegt ihre Schlaege mit, bis das Handy sie bestaetigt.
     Ohne `shotAck` funkt sie die ganze Runde dieselben vierzig Messungen. */
  await R(`draftPush()`); await new Promise(r=>setImmediate(r));
  let djA=null; try{ djA=JSON.parse(REPO["draft.json"]||"{}"); }catch(e){}
  pruef("das Handy quittiert die übernommenen Messungen",
    !!(djA && Array.isArray(djA.shotAck) && djA.shotAck.indexOf("W1")>=0),
    JSON.stringify(djA&&djA.shotAck));
  /* UND ES DARF SIE NICHT LOESCHEN: Beim eigenen Push gibt `DRAFT_SHOTS` die
     Uhr-Messungen unveraendert zurueck. Schriebe das Handy hier eine leere
     Liste, waeren die Messungen weg, bevor die Uhr sie quittiert bekommt. */
  pruef("und schreibt die Messungen der Uhr nicht weg",
    !!(djA && Array.isArray(djA.gpsShots) && djA.gpsShots.some(x=>x.id==="W1")),
    JSON.stringify((djA&&djA.gpsShots||[]).map(x=>x.id)));

  /* ======================================================================
     SCHREIBKONFLIKT — BEIDE GERAETE GLEICHZEITIG (neu 27.08.2026)
     ----------------------------------------------------------------------
     Der Worker antwortet mit 409, wenn sich die Kennung seit dem Lesen
     geaendert hat. Das ist der Alltag auf der Bahn: Uhr und Handy schreiben
     im Sekundenabstand. Die Regel lautet VEREINEN, nicht ueberschreiben —
     und geprueft wurde bisher nur der glatte Fall.
     DER VERLUST WAERE STILL: Wer den 409 mit „nochmal draufschreiben"
     beantwortet, loescht die Messungen des anderen Geraets, ohne dass es
     jemand meldet. */
  kopf("Schreibkonflikt (409) — vereinen statt überschreiben");
  {
    const fremd=JSON.parse(REPO["draft.json"]||"{}");
    fremd.gpsShots=(fremd.gpsShots||[]).concat([{...uhrSchlag, id:"W9"}]);
    /* KEIN ZEITSTEMPEL IN DER ZUKUNFT — siehe die Begruendung bei
       `uhrSchickt`. Ein Entwurf, der spaeter datiert ist als „jetzt", gewinnt
       JEDEN folgenden Merge und schleppt seinen Inhalt durch alle weiteren
       Abschnitte. Beim Bau kostete genau das eine halbe Stunde Suche an einer
       Mitspieler-Pruefung, die nichts damit zu tun hatte. */
    fremd.ts=new Date().toISOString();
    REPO["draft.json"]=JSON.stringify(fremd);
    SHAS["draft.json"]="sha-fremd-neu";        // Kennung wandert -> naechster Push bekommt 409
    R(`PLAY.holes[2].score=5; if(typeof playTouchHole==="function") playTouchHole(PLAY.holes[2]);
       if(typeof playSaveDraft==="function") playSaveDraft(); "ok"`);
    await R(`draftPush()`); await new Promise(r=>setImmediate(r));
    let dk=null; try{ dk=JSON.parse(REPO["draft.json"]||"{}"); }catch(e){}
    pruef("der eigene Eintrag ist im Repo angekommen",
      !!(dk&&dk.round&&(dk.round.holes||[]).some(h=>h.hole===3&&h.score===5)));
    pruef("die Messung des anderen Geräts überlebt den Konflikt",
      !!(dk&&(dk.gpsShots||[]).some(x=>x.id==="W9")),
      JSON.stringify((dk&&dk.gpsShots||[]).map(x=>x.id)));
  }

  /* ======================================================================
     NETZAUSFALL — WEITERSPIELEN GEHT IMMER (neu 27.08.2026)
     ----------------------------------------------------------------------
     Regel 1 der Architektur, in beiden Doku-Koepfen: „Repo-Push NIE als
     Bedingung fuers Weiterarbeiten." Auf dem Platz ist Funkloch der
     Normalfall, nicht die Ausnahme — und ein Eingabefeld, das ohne Netz
     klemmt, kostet die Runde.
     Geprueft wird beides: dass Eingaben ohne Netz durchgehen, UND dass sie
     nach der Rueckkehr von selbst im Repo landen. Nur das zweite macht den
     Ausfall harmlos. */
  kopf("Netzausfall — Eingaben laufen weiter, Nachschub kommt später");
  {
    const echtesFetch=sandbox.fetch;
    sandbox.fetch=()=>Promise.reject(new Error("offline"));
    const vorOffline=SHAS["draft.json"];
    R(`PLAY.holes[3].score=6; PLAY.holes[3].putts=2;
       if(typeof playTouchHole==="function") playTouchHole(PLAY.holes[3]);
       if(typeof playSaveDraft==="function") playSaveDraft(); "ok"`);
    let krachte=false;
    try{ await R(`draftPush()`); await new Promise(r=>setImmediate(r)); }
    catch(e){ krachte=true; }
    pruef("ein Push ohne Netz reisst nichts um", !krachte);
    pruef("die Eingabe steht trotzdem im Zustand",
      R(`PLAY.holes[3].score`)===6, String(R(`PLAY.holes[3].score`)));
    pruef("sie steht auch im lokalen Entwurf",
      !!R(`(((DB._draftRound||{}).round||{}).holes||[]).some(h=>h.hole===4&&h.score===6)`));
    pruef("und im Repo ist nichts Halbes gelandet", SHAS["draft.json"]===vorOffline);
    sandbox.fetch=echtesFetch;
    await R(`draftPush()`); await new Promise(r=>setImmediate(r));
    let dn=null; try{ dn=JSON.parse(REPO["draft.json"]||"{}"); }catch(e){}
    pruef("nach der Rückkehr kommt die Eingabe an",
      !!(dn&&dn.round&&(dn.round.holes||[]).some(h=>h.hole===4&&h.score===6)));
  }

  /* AUSTRUDELN LASSEN, bevor der naechste Abschnitt beginnt. `playSaveDraft`
     stoesst ueber `maybeCheckpointDraft` einen Abgleich an, der die Datei
     liest und den Entwurf VEREINT — laeuft der erst mitten im naechsten
     Abschnitt zu Ende, ueberschreibt er dort gerade gesetzte Werte, und der
     Fehlschlag steht an einer Stelle, die nichts damit zu tun hat. Genau das
     ist beim Bau dieses Abschnitts passiert: Die Mitspieler-Pruefung wurde
     rot, obwohl an ihr nichts falsch war. */
  for(let i=0;i<8;i++) await new Promise(r=>setImmediate(r));

  /* ---------- Mitspieler (v4.81): Namen und Endscores reisen mit ---------- */
  kopf("Mitspieler — Namen und Endscores über den Entwurf");
  const ms=R(`(function(){
     PLAY.mitspieler=["Jan","Ute"];
     var h=PLAY.holes[0]; h.msc1=6; if(typeof playTouchHole==="function") playTouchHole(h);
     /* Der echte Pfad: mitspielerAdd()/playAdj() rufen playSaveDraft() —
        erst das hebt den Stand in DB._draftRound, den draftPush sendet. */
     if(typeof playSaveDraft==="function") playSaveDraft();
     var r=playRound();
     return {namen:r.mitspieler, msc:(r.holes[0]||{}).msc1,
             }; })()`);
  pruef("Namen stehen im Runden-Objekt", !!(ms&&ms.namen&&ms.namen.length===2&&ms.namen[0]==="Jan"),
        JSON.stringify(ms&&ms.namen));
  pruef("Endscore reist als Lochfeld", !!(ms&&ms.msc===6), String(ms&&ms.msc));
  await R(`draftPush()`); await new Promise(r=>setImmediate(r));
  let djM=null; try{ djM=JSON.parse(REPO["draft.json"]||"{}"); }catch(e){}
  pruef("beides steht im Entwurf", !!(djM&&djM.round&&(djM.round.mitspieler||[]).length===2
        && (djM.round.holes||[]).some(h=>h.hole===1&&h.msc1===6)),
        JSON.stringify(djM&&djM.round&&djM.round.mitspieler));
  /* Die "Uhr" traegt fuer Ute (msc2) nach — das Handy uebernimmt per Loch-ts,
     ohne den eigenen msc1 zu verlieren (nimm-Regel, null loescht nichts). */
  const uhrM=JSON.parse(REPO["draft.json"]);
  const spaeterM=new Date(Date.now()+120000).toISOString();
  uhrM.ts=spaeterM;
  uhrM.round.holes=(uhrM.round.holes||[]).map(h=>h.hole===1?Object.assign({},h,{msc2:5,ts:spaeterM}):h);
  REPO["draft.json"]=JSON.stringify(uhrM); SHAS["draft.json"]="sha-uhr-ms";
  const msZ=R("(function(){ var dr=mergeDraft(DB._draftRound, "+JSON.stringify(uhrM)+", (DB.ui||{}).draftDiscardedTs||\"\");"+
     " DB._draftRound=dr; playAdoptDraft();"+
     " var h=(PLAY.holes||[])[0]||{}; return {m1:h.msc1, m2:h.msc2}; })()");
  pruef("Uhr-Eintrag kommt an, eigener bleibt", !!(msZ&&msZ.m1===6&&msZ.m2===5), JSON.stringify(msZ));

  /* ACHTUNG FUER SPAETERE ABSCHNITTE: Die Pruefung darueber datiert den
     Uhr-Entwurf ABSICHTLICH zwei Minuten in die Zukunft — anders liesse sich
     „die Uhr hat spaeter geschrieben" nicht nachstellen. Der Wert bleibt
     danach am lokalen Entwurf UND in `REPO["draft.json"]` haengen und gewinnt
     jeden folgenden Merge.
     Ich habe zuerst versucht, ihn hier zurueckzudrehen — das verschob nur den
     Schaden: Die Kennung der Datei wanderte, und die Verwerfen-Pruefung
     darunter wurde rot. WER NACH DIESEM ABSCHNITT ETWAS AM ENTWURF PRUEFEN
     WILL, faengt besser mit einer FRISCHEN Runde an (siehe „Runde
     abschliessen") statt an Zeitstempeln zu drehen. */

  kopf("Runde verwerfen");
  R(`draftFinalize(); "ok"`);
  await new Promise(r=>setImmediate(r));
  let dj3=null; try{ dj3=JSON.parse(REPO["draft.json"]||"{}"); }catch(e){}
  pruef("Grabstein statt Runde im Repo", !!(dj3&&dj3.discardedTs&&!dj3.round), JSON.stringify(Object.keys(dj3||{})));
  /* Und ein Gerät OHNE eigene Runde darf jetzt nichts mehr überschreiben. */
  const shaNach=SHAS["draft.json"];
  await R(`draftPush()`);
  await new Promise(r=>setImmediate(r));
  pruef("ohne eigenen Entwurf wird nicht geschrieben", SHAS["draft.json"]===shaNach,
    "sha "+shaNach+" -> "+SHAS["draft.json"]);

  /* ======================================================================
     RUNDE ABSCHLIESSEN — DER WEG, DEN JEDE RUNDE GEHT (neu 27.08.2026)
     ----------------------------------------------------------------------
     Bis hierher prueft die Simulation das VERWERFEN, aber nie das Beenden.
     Das ist die Lage, die man achtzehnmal von zwanzig erreicht — und sie war
     ungeprueft, obwohl an ihr schon einmal etwas hing („Runde beenden geht
     nicht", Uhr-Fassungen 34/35).
     Geprueft wird die ganze Kette nach dem letzten Putt: Landet die Runde in
     `DB.rounds`? Rechnet `computeRound` die Summen? Steht der Grabstein in
     `draft.json`, damit die Uhr aufhoert zu funken? Und: Traegt die
     SCOREKARTE die Mitspieler (v4.83) — das war bis dahin nur an der reinen
     Funktion geprueft, nie am Weg dorthin.
     EIGENE RUNDE, weil die vorige verworfen wurde. Damit prueft dieser
     Abschnitt nebenbei mit, dass nach einem Verwerfen ueberhaupt wieder eine
     Runde beginnen kann. */
  kopf("Runde abschließen — Ergebnis, Scorekarte, Grabstein");
  {
    R(`DB.ui=DB.ui||{}; DB.ui.draftDiscardedTs=""; "ok"`);
    const neu2=R(`playBegin("Nordplatz Timmendorfer Strand","Gelb",0); PLAY.active`);
    pruef("nach dem Verwerfen startet eine neue Runde", neu2===true, JSON.stringify(neu2));

    /* Neun Loecher spielen, die zweite Haelfte leer lassen. Die PWA deutet
       das beim Abschluss zu „Front 9" um (v3.34) — aber nur bei einem KLAREN
       Bild, und genau die Grenze ist es wert, festgehalten zu werden. */
    R(`(function(){
       PLAY.mitspieler=["Jan","Ute"];
       for(var i=0;i<9;i++){ var h=PLAY.holes[i];
         h.score=h.par+(i%3===0?1:0); h.putts=2;
         h.msc1=h.par+1; h.msc2=h.par;
         if(typeof playTouchHole==="function") playTouchHole(h); }
       if(typeof playSaveDraft==="function") playSaveDraft(); return "ok"; })()`);

    /* ENTFERNEN MUSS AUCH REISEN (27.08.2026, Uhr-Fassung 42).
       GEMELDET: Auf der Uhr standen mehr Mitspieler als in der PWA. Eine der
       beiden Ursachen sass genau hier: Das Handy schreibt die Namensliste
       IMMER, auch leer — die Uhr uebernahm sie aber nur, wenn sie NICHT leer
       war. Entfernen war damit die einzige Aenderung, die nicht ankam.
       Geprueft wird, dass das Handy das Entfernen ueberhaupt AUSSPRICHT:
       Steht `mitspieler` als leeres Feld im Entwurf, kann die Uhr es
       befolgen; fehlte das Feld, wuesste sie nur „nicht gesagt" — und genau
       diesen Unterschied traegt `RepoDraft.mitspieler: List<String>?`. */
    await R(`draftPush()`); await new Promise(r=>setImmediate(r));
    R(`PLAY.mitspieler=[]; if(typeof playSaveDraft==="function") playSaveDraft(); "ok"`);
    await R(`draftPush()`); await new Promise(r=>setImmediate(r));
    let djL=null; try{ djL=JSON.parse(REPO["draft.json"]||"{}"); }catch(e2){}
    pruef("das Entfernen des letzten Mitspielers steht im Entwurf",
      !!(djL && djL.round && Array.isArray(djL.round.mitspieler) && djL.round.mitspieler.length===0),
      JSON.stringify(djL&&djL.round&&djL.round.mitspieler));
    /* Und die Loch-Scores bleiben stehen: Das Aufraeumen macht
       `mitspielerName` ueber die Maske, nicht der Abgleich. Ein Merge, der
       nebenbei Zahlen loescht, waere nicht nachvollziehbar. */
    pruef("die Loch-Scores werden dabei nicht nebenbei gelöscht",
      !!(djL && (djL.round.holes||[]).some(h=>h.hole===1 && h.msc1!=null)));
    R(`PLAY.mitspieler=["Jan","Ute"]; if(typeof playSaveDraft==="function") playSaveDraft(); "ok"`);
    await R(`draftPush()`); await new Promise(r=>setImmediate(r));

    /* Die Scorekarte VOR dem Abschluss — im Spielmodus liest sie `PLAY`, und
       genau dort fehlte bis v4.83 das Feld `mitspieler` beim Abschreiben. */
    const karte=R(`playCardHtml()`);
    pruef("die Scorekarte im Spielmodus nennt die Mitspieler",
      typeof karte==="string" && karte.indexOf("Jan")>=0 && karte.indexOf("Ute")>=0);
    pruef("und färbt fremde Scores nicht wie eigene",
      typeof karte==="string" &&
      !/<tr class="sc-ms">[\s\S]*?(sc-birdie|sc-bogey|sc-eagle|sc-dbl)[\s\S]*?<\/tr>/.test(karte));

    const idVor=R(`PLAY.roundId`);
    const nVor=R(`(DB.rounds||[]).length`);
    R(`playFinish(); "ok"`);
    await new Promise(r=>setImmediate(r));

    pruef("die Runde liegt im Bestand", R(`(DB.rounds||[]).length`)===nVor+1,
      nVor+" -> "+R(`(DB.rounds||[]).length`));
    pruef("der Spielmodus ist beendet", R(`PLAY.active`)===false);
    const rr=R(`(DB.rounds||[]).find(x=>x.id===${JSON.stringify(idVor)})||null`);
    pruef("und zwar unter derselben Kennung", !!rr, String(idVor));
    pruef("mit neun gewerteten Löchern",
      !!(rr && (rr.holes||[]).filter(h=>h.score!=null).length===9));
    /* ALS 18 GESTARTET, NEUN GESPIELT: Die Umdeutung ist ausdruecklich
       gewollt und wird dem Spieler auch gesagt — sonst findet er spaeter
       „Front 9" in der Liste und weiss nicht, warum. */
    pruef("als „Front 9\" gewertet", !!(rr && rr.side==="Front 9" && rr.type==="9 Loch"),
      JSON.stringify(rr&&{side:rr.side,type:rr.type}));
    pruef("die Mitspieler sind mit gespeichert",
      !!(rr && (rr.mitspieler||[]).length===2), JSON.stringify(rr&&rr.mitspieler));
    pruef("und ihre Loch-Scores auch",
      !!(rr && (rr.holes||[]).some(h=>h.hole===1 && h.msc1!=null && h.msc2!=null)));

    const e=R(`computeRound((DB.rounds||[]).find(x=>x.id===${JSON.stringify(idVor)}))`);
    /* `computeRound` nennt die Schlagsumme `gross`, nicht `total` — mein
       erster Anlauf griff ins Leere und meldete einen Fehler, den es nicht
       gab. Notiert, damit die falsche Erwartung nicht wiederkommt. */
    pruef("die Auswertung rechnet eine Summe", !!(e && e.gross>0), JSON.stringify(e&&e.gross));
    pruef("und zählt die Putts", !!(e && e.putts===18), JSON.stringify(e&&e.putts));
    pruef("nur die neun gespielten Löcher zählen", !!(e && e.counted===9), String(e&&e.counted));
    /* Stableford aus neun Loechern — die Umdeutung zu „Front 9" ist genau
       dafuer da: Ohne sie faellt die Runde aus allen Vergleichen heraus. */
    pruef("und die Neun ist auswertbar", !!(e && e.stblN>0), String(e&&e.stblN));

    /* Die gespeicherte Karte — derselbe Bauplan wie im Spielmodus (24da). */
    const karte2=R(`roundCardHtml((DB.rounds||[]).find(x=>x.id===${JSON.stringify(idVor)}))`);
    pruef("die gespeicherte Scorekarte trägt die Mitspieler",
      typeof karte2==="string" && karte2.indexOf("Jan")>=0 && karte2.indexOf("Ute")>=0);
    const txt=R(`roundShareText((DB.rounds||[]).find(x=>x.id===${JSON.stringify(idVor)}))`);
    pruef("und der Teilen-Text nennt sie mit Summe",
      typeof txt==="string" && /Jan: \d+/.test(txt) && /Ute: \d+/.test(txt),
      typeof txt==="string"?txt.split("\n").filter(z=>/Jan|Ute/.test(z)).join(" | "):"");

    /* DER GRABSTEIN IST DER TEIL, DER DIE UHR BETRIFFT: Ohne ihn funkt sie
       weiter Herzschlaege fuer eine Runde, die es nicht mehr gibt — und der
       naechste Merge holt den Entwurf aus dem Repo zurueck. */
    await new Promise(r=>setImmediate(r));
    let de=null; try{ de=JSON.parse(REPO["draft.json"]||"{}"); }catch(e2){}
    pruef("im Repo steht der Grabstein, nicht die Runde",
      !!(de && de.discardedTs && !de.round), JSON.stringify(Object.keys(de||{})));
  }

  /* ======================================================================
     WAS DER ENTWURFS-MERGE MIT LEEREN LISTEN MACHT (neu 27.08.2026)
     ----------------------------------------------------------------------
     BEFUND BEIM BAU DIESES ABSCHNITTS, festgehalten statt stillschweigend
     geaendert: Auf LOCH-Ebene gilt seit v2.98 „null loescht nichts" — ein
     leeres Feld der Gegenseite ueberschreibt keinen gesetzten Wert. Auf
     RUNDEN-Ebene gibt es diese Regel NICHT: `Object.assign({}, old.round,
     nw.round)` kopiert auch ein LEERES ARRAY, und damit loescht ein
     `mitspieler: []` des juengeren Entwurfs die Namen des aelteren.
     EIN FEHLENDES Feld ist dagegen harmlos — `Object.assign` uebergeht es.
     Die Uhr schreibt `mitspieler` nur, wenn Namen da sind (`buildRoundJson`),
     ist also KEIN Ueberträger. Erreichbar ist der Fall ueber zwei
     PWA-Instanzen (Handy und Browser).
     WARUM HIER NICHT EINFACH EIN RIEGEL: Er haette einen Preis — dann liesse
     sich der letzte Mitspieler von der anderen Seite nicht mehr entfernen.
     Das ist eine Abwaegung, keine Fehlerbehebung, und sie gehoert dem
     Besitzer der Daten. Bis dahin haelt diese Pruefung das Verhalten fest,
     damit es nicht unbemerkt kippt — in die eine wie die andere Richtung. */
  kopf("Entwurfs-Merge — was ein leeres Feld anrichtet");
  {
    const mitN={ts:"2026-08-27T10:00:00.000Z", round:{date:"2026-08-27",course:"X",
      side:"18 Loch", mitspieler:["Jan","Ute"], holes:[{hole:1,score:5}]}};
    const leerJuenger={ts:"2026-08-27T10:00:01.000Z", round:{date:"2026-08-27",course:"X",
      side:"18 Loch", mitspieler:[], holes:[{hole:1}]}};
    const ohneFeld={ts:"2026-08-27T10:00:01.000Z", round:{date:"2026-08-27",course:"X",
      side:"18 Loch", holes:[{hole:1}]}};
    const a=R(`mergeDraft(${JSON.stringify(mitN)},${JSON.stringify(leerJuenger)},"")`);
    const b=R(`mergeDraft(${JSON.stringify(mitN)},${JSON.stringify(ohneFeld)},"")`);
    pruef("ein FEHLENDES Feld der Gegenseite löscht nichts",
      !!(b && b.round && (b.round.mitspieler||[]).length===2),
      JSON.stringify(b&&b.round&&b.round.mitspieler));
    pruef("ein LEERES Feld des jüngeren Entwurfs löscht — Stand heute, bewusst festgehalten",
      !!(a && a.round && (a.round.mitspieler||[]).length===0),
      JSON.stringify(a&&a.round&&a.round.mitspieler));
    /* Und die Loch-Ebene haelt dagegen, wie sie soll. */
    const lochLeer={ts:"2026-08-27T10:00:01.000Z", round:{date:"2026-08-27",course:"X",
      side:"18 Loch", holes:[{hole:1,score:null,putts:2}]}};
    const c=R(`mergeDraft(${JSON.stringify(mitN)},${JSON.stringify(lochLeer)},"")`);
    pruef("auf Loch-Ebene löscht null weiterhin nichts",
      !!(c && (c.round.holes||[]).some(h=>h.hole===1&&h.score===5&&h.putts===2)),
      JSON.stringify(c&&c.round&&c.round.holes));
  }

  console.log("\n"+JSON.stringify(bilanz()));
})();
