#!/usr/bin/env node
/* =============================================================================
   changelog-archiv.js — das Changelog kuerzen, ohne Begruendungen zu verlieren
   -----------------------------------------------------------------------------
   AUFRUF:  node changelog-archiv.js            (neben index.html)
            node changelog-archiv.js --pruefen  (nur melden, nichts schreiben)
            node changelog-archiv.js --grenze 40

   WOZU
   Das Changelog steht im devdocs-Block der `index.html` — einer Datei, die bei
   JEDEM Start geladen und geparst wird. Es war einmal auf 283 kB gewachsen, ein
   Achtel der ganzen Datei. Die Pruefstand-Sperrklinke 24ct haelt es deshalb bei
   hoechstens 45 Eintraegen.
   Bis hierher hiess „kuerzen" von Hand ausschneiden und irgendwohin kopieren.
   ERGEBNIS NACH VIER WOCHEN: `changelog-archiv.md` gab es im Repo GAR NICHT.
   Die Sperrklinke verwies auf eine Datei, die nie angelegt wurde — 366
   Fassungen an Begruendungen waren weg und mussten am 27.08.2026 aus der
   Git-Historie zurueckgeholt werden.
   Ein Arbeitsschritt, den man von Hand macht, wird irgendwann nicht gemacht.
   Deshalb dieses Skript.

   WAS ES TUT
   1. Liest das Changelog aus dem devdocs-Block der `index.html`.
   2. Behaelt die neuesten `--grenze` Eintraege (Vorgabe 45, wie 24ct).
   3. Haengt alles Aeltere OBEN an `changelog-archiv.md` an — neueste zuerst,
      dieselbe Reihenfolge wie im Changelog selbst.
   4. Schreibt beide Dateien.

   DREI EIGENSCHAFTEN, DIE ES HABEN MUSS
   · WIEDERHOLBAR: Zweimal laufen lassen aendert beim zweiten Mal nichts. Ein
     Eintrag, dessen Fassungsnummer im Archiv schon steht, wird nicht doppelt
     angehaengt.
   · VERLUSTFREI: Es wird erst geschrieben, wenn BEIDE Ergebnisse fertig im
     Speicher stehen. Ein Abbruch zwischen „aus index.html entfernt" und „ins
     Archiv geschrieben" waere genau der Verlust, den es verhindern soll.
   · EHRLICH: `--pruefen` sagt, was passieren wuerde, und aendert nichts.

   ANLEGEN STATT VERLANGEN: Fehlt `changelog-archiv.md`, wird sie mit Kopf
   angelegt. Ein Werkzeug, das eine Datei voraussetzt, die es selbst erzeugen
   koennte, ist der Anfang derselben Luecke.
   ============================================================================= */

const fs = require("fs");
const path = require("path");

const ARG = process.argv.slice(2);
const NURPRUEFEN = ARG.includes("--pruefen") || ARG.includes("--dry-run");
const GRENZE = (() => {
  const i = ARG.indexOf("--grenze");
  const v = i >= 0 ? parseInt(ARG[i + 1], 10) : NaN;
  return Number.isFinite(v) && v > 0 ? v : 45;
})();

const HTML = path.join(__dirname, "index.html");
const ARCHIV = path.join(__dirname, "changelog-archiv.md");

const ARCHIV_KOPF = `# Changelog-Archiv — Golf-PWA

> **Was hier steht.** Die älteren Einträge des Changelogs. Das aktuelle Changelog steht im
> devdocs-Block in \`index.html\`; die Prüfstand-Sperrklinke 24ct hält es bei höchstens 45 Einträgen,
> weil die Datei bei jedem Start geladen und geparst wird. Was darüber hinausgeht, wandert hierher —
> **die Begründungen gehen nicht verloren, sie stehen nur woanders.**
>
> **Reihenfolge: neueste zuerst**, wie im Changelog selbst.
>
> **Wie es hierher kommt:** \`node changelog-archiv.js\`. Das Skript nimmt die ältesten Einträge aus
> \`index.html\`, hängt sie hier oben an und schreibt beide Dateien. Von Hand zusammenkopieren muss
> niemand etwas.

---
`;

function raus(txt) { console.log(txt); }
function fehler(txt) { console.error("✗ " + txt); process.exit(1); }

/* --- 1. Changelog aus dem devdocs-Block holen ---------------------------- */
if (!fs.existsSync(HTML)) fehler("index.html liegt nicht neben dieser Datei.");
const src = fs.readFileSync(HTML, "utf8");

/* Der devdocs-Block ist ein <script>-Element mit `devdocs` im Attributteil —
   dieselbe Erkennung wie in tests.js, damit beide dieselbe Stelle meinen. */
const doc = src.match(/<script[^>]*devdocs[^>]*>([\s\S]*?)<\/script>/);
if (!doc) fehler("Kein devdocs-Block in index.html gefunden.");

const clStart = src.indexOf("## Changelog", doc.index);
if (clStart < 0) fehler("Keine Überschrift „## Changelog\" im devdocs-Block.");
const clEnde = src.indexOf("</script>", clStart);

/* Ein Eintrag beginnt mit `\n- **vX.Y.Z` und reicht bis zum nächsten. Der
   LETZTE reicht bis zum Ende des devdocs-Blocks — dort steht sonst nichts
   mehr, das Changelog ist bewusst das letzte Kapitel. */
const block = src.slice(clStart, clEnde);
const marken = [...block.matchAll(/\n- \*\*(v[\d.]+)/g)];
if (!marken.length) fehler("Keine Changelog-Einträge gefunden.");

const eintraege = marken.map((m, k) => {
  const von = m.index;
  const bis = k + 1 < marken.length ? marken[k + 1].index : block.length;
  return { version: m[1], text: block.slice(von, bis).replace(/\s+$/, "") };
});

raus(`Changelog in index.html: ${eintraege.length} Einträge (Grenze ${GRENZE})`);

/* --- 2. Aufteilen -------------------------------------------------------- */
/* Die Einträge stehen NEUESTE ZUERST (Regel im devdocs-Kopf). Behalten wird
   also der Anfang der Liste; das Ende wandert ins Archiv. Bewusst NICHT nach
   Fassungsnummer sortiert: Steht dort etwas in falscher Reihenfolge, ist das
   eine eigene Meldung wert (24cs prüft es), aber kein Grund, hier stillschweigend
   umzusortieren und damit die Datei anders aussehen zu lassen als sie ist. */
const behalten = eintraege.slice(0, GRENZE);
const wandern = eintraege.slice(GRENZE);

if (!wandern.length) {
  raus(`✓ Nichts zu tun — ${eintraege.length} ≤ ${GRENZE}.`);
  process.exit(0);
}
raus(`Ins Archiv: ${wandern.length} Einträge (${wandern[0].version} … ${wandern[wandern.length - 1].version})`);

/* --- 3. Archiv lesen (oder anlegen) -------------------------------------- */
let archiv = fs.existsSync(ARCHIV) ? fs.readFileSync(ARCHIV, "utf8") : null;
if (archiv === null) {
  raus("changelog-archiv.md gibt es noch nicht — wird angelegt.");
  archiv = ARCHIV_KOPF;
}

/* WIEDERHOLBARKEIT: Was schon drinsteht, kommt nicht noch einmal hinein.
   Verglichen wird über die Fassungsnummer, nicht über den Text — ein später
   nachgebesserter Eintrag soll nicht als zweiter Eintrag erscheinen. */
const imArchiv = new Set([...archiv.matchAll(/\n- \*\*(v[\d.]+)/g)].map(m => m[1]));
const neu = wandern.filter(e => !imArchiv.has(e.version));
const schonDa = wandern.length - neu.length;
if (schonDa) raus(`${schonDa} davon stehen bereits im Archiv — werden nicht doppelt angehängt.`);

/* Anhängen OBEN, direkt nach dem Kopf (erste `---`-Trennlinie am Zeilenanfang).
   Neueste zuerst heißt: Der jüngste der wandernden Einträge steht oben. */
const trenn = archiv.indexOf("\n---\n");
const einfuegeAb = trenn >= 0 ? trenn + 5 : archiv.length;
const neuerArchivText =
  archiv.slice(0, einfuegeAb) +
  (neu.length ? "\n" + neu.map(e => e.text.replace(/^\n/, "")).join("\n\n") + "\n" : "") +
  archiv.slice(einfuegeAb);

/* --- 4. index.html neu bauen --------------------------------------------- */
const neuerBlock = block.slice(0, marken[GRENZE].index) + "\n";
const neuesHtml = src.slice(0, clStart) + neuerBlock + src.slice(clEnde);

/* GEGENPROBE VOR DEM SCHREIBEN. Ein Skript, das Begründungen wegwirft, muss
   vorher nachzählen — hier ist der Punkt ohne Wiederkehr. */
const probe = [...neuerBlock.matchAll(/\n- \*\*(v[\d.]+)/g)].map(m => m[1]);
if (probe.length !== behalten.length)
  fehler(`Gegenprobe: ${probe.length} statt ${behalten.length} Einträge übrig — nichts geschrieben.`);
const probeArchiv = new Set([...neuerArchivText.matchAll(/\n- \*\*(v[\d.]+)/g)].map(m => m[1]));
const verloren = wandern.filter(e => !probeArchiv.has(e.version)).map(e => e.version);
if (verloren.length)
  fehler(`Gegenprobe: ${verloren.length} Einträge wären verloren (${verloren.slice(0, 5).join(", ")}) — nichts geschrieben.`);

if (NURPRUEFEN) {
  raus(`\n— nur geprüft, nichts geschrieben —`);
  raus(`index.html behielte ${behalten.length}, Archiv bekäme ${neu.length} dazu (dann ${probeArchiv.size} gesamt).`);
  process.exit(0);
}

/* ERST DAS ARCHIV, DANN DIE KÜRZUNG. Bricht es dazwischen ab, steht ein
   Eintrag doppelt — lästig, aber nichts ist weg. Andersherum wäre er weg. */
fs.writeFileSync(ARCHIV, neuerArchivText, "utf8");
fs.writeFileSync(HTML, neuesHtml, "utf8");

raus(`\n✓ index.html: ${behalten.length} Einträge (${Math.round(neuesHtml.length / 1024)} kB)`);
raus(`✓ changelog-archiv.md: ${probeArchiv.size} Einträge (${Math.round(neuerArchivText.length / 1024)} kB)`);
raus(`\nBeide Dateien ins Repo legen.`);
