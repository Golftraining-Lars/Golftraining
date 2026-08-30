package de.lars.golfwatch.presentation

import android.Manifest
import de.lars.golfwatch.R
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/* ===========================================================================
 *  GOLF-TRAINING · WEAR-OS-APP (MainActivity.kt) — ENTWICKLER- & KI-DOKUMENTATION
 * ===========================================================================
 *  DIES IST DIE VERBINDLICHE ARBEITSGRUNDLAGE FÜR DIESE DATEI.
 *  Vor jeder Änderung zuerst lesen; nach jeder Änderung den passenden Abschnitt
 *  UND das CHANGELOG (unten) fortschreiben. (Analog zur devdocs der HTML-App.)
 *
 *  0. ZWECK  (NEU GEFASST 2026-08-26 (38) — DIESER ABSATZ IST DIE MESSLATTE)
 *     DIE UHR IST SENSOR UND EINGABEMASKE. Sie MISST (GPS-Punkte fuer das
 *     Schlagtracking) und sie NIMMT ENTGEGEN (Lochdaten). Sie RECHNET NICHTS
 *     und sie EMPFIEHLT NICHTS.
 *     Jede Groesse, die aus einer Rechnung entsteht — Entfernung zum Gruen,
 *     „spielt wie", Wind, Hoehe, Temperatur, Schlaegerempfehlung, Gefahren —
 *     gehoert ausschliesslich aufs HANDY. Das ist keine Stilfrage: Zwei
 *     Geraete, die dieselbe Frage verschieden beantworten, kosten das
 *     Vertrauen in BEIDE (siehe (16 (3)) und den Gleichlauf-Abschnitt im
 *     Pruefstand).
 *     WER HIER EINE ZAHL EINBAUEN WILL, DIE GERECHNET IST, BAUT SIE INS
 *     HANDY. Ohne Ausnahme.
 *     Erfasst wird eine laufende Runde Loch fuer Loch (volle Feld-Paritaet)
 *     und als Entwurf (_draftRound) ueber den Cloudflare-Worker ins
 *     GitHub-Repo geschrieben. Abschluss und Auswertung passieren am Handy.
 *
 *  1. UNVERHANDELBARE REGELN
 *     - JEDE Eingabe wird SOFORT lokal gesichert (persist() -> saveLocal),
 *       damit nichts verloren geht, wenn die App beendet wird.
 *     - Repo-Push NIE als Bedingung fürs Weiterarbeiten (offline muss gehen).
 *       Das gilt erst recht fürs Schlagtracking: Es ist der einzige Zweck
 *       dieser App, der Funk voraussetzt — und tut es NICHT. Gemessen wird
 *       lokal, gesendet wird, wenn es geht.
 *     - Kein Ganz-Überschreiben unkontrolliert: pushDraft setzt nur _draftRound,
 *       gpsShots (additiv, dedupliziert nach id) + exportedAt im geladenen
 *       DB-Objekt (Rest bleibt unangetastet).
 *     - WRITE_KEY/WORKER_URL/DATA_URL sind Konfiguration (oben) — vor Build prüfen.
 *     - Koordinaten IMMER [lat, lng], Distanzen IMMER Meter (wie in der PWA).
 *
 *  2. ARCHITEKTUR / DATENFLUSS
 *     a) State: GolfWatchApp() hält allen Zustand (course, tee, hi, entries,
 *        idx, roundStart, status, syncJob, rec). entries = Map<hole, HoleEntry>.
 *        KEIN `geo` mehr seit (40) — die Uhr fuehrt keine Platzkarte.
 *     b) Lokale Persistenz: persist() ruft saveLocal(ctx, …) (SharedPreferences).
 *        Gesichert werden Kurs, Einträge und SCHLÄGE (shots) -> Fortsetzen
 *        funktioniert komplett offline. Der Geo-Rohstring ist mit (40)
 *        entfallen: ein paar hundert kB je Platz fuer Distanzen, die es nicht
 *        mehr gibt. Aeltere Staende duerfen ihn enthalten, er wird beim Lesen
 *        uebergangen.
 *     c) Repo-Sync: syncNow() lädt das Repo-JSON, setzt _draftRound, ergänzt
 *        gpsShots und pusht via Worker (Net.pushDraft). scheduleSync() ist ein
 *        ENTPRELLTER Aufruf (1,5 s nach der letzten Eingabe).
 *     d) Laden: `Net.fetchWatchRaw()` holt `watch.json` — Kurse (Name, Tees),
 *        Optionen, Schlaegerlaengen und HI. KEIN Rueckfall auf die grosse
 *        Datei mehr (49); scheitert der Abruf, greift der lokale
 *        Zwischenspeicher. Frueher: Net.fetchData() holt Kurse, Optionen,
 *        Schlägerlängen (clubDistances) und HI aus DATA_URL. Ein `geo`-
 *        Schluessel in der Datei wird uebergangen; die PWA streicht ihn mit
 *        v4.84 aus `watchPayload()`.
 *     e) Runden-JSON: buildRoundJson() erzeugt das Runden-Objekt inkl.
 *        holes[].shots (PWA-kompatibel, siehe 6.) und conditions (Wetter).
 *
 *  2b. FOREGROUND-SERVICE („App geht in den Hintergrund" — GELÖST)
 *     RoundService ist ein echter Foreground-Service (Typ location) mit
 *     Dauer-Notification + PARTIAL_WAKE_LOCK. Er wird beim Start/Fortsetzen
 *     einer Runde gestartet (svcStart) und beim Abschluss/Verwerfen gestoppt
 *     (svcStop). Solange er läuft:
 *       - wird der Prozess vom System nicht weggeräumt,
 *       - laufen die GPS-Updates weiter, auch wenn der Bildschirm aus ist
 *         oder die Uhr auf das Zifferblatt zurückspringt,
 *       - zeigt die Notification Loch + Stand („Loch 7 · +4").
 *     Der Service schreibt die Position in das Singleton Live (Compose-State),
 *     die UI liest sie einfach mit. FLAG_KEEP_SCREEN_ON bleibt zusätzlich als
 *     Komfort erhalten, ist aber NICHT mehr die Absicherung.
 *     WICHTIG: AndroidManifest braucht die Permissions + <service …
 *     android:foregroundServiceType="location"> (siehe Kommentar unten).
 *
 *  3. ANZEIGE  (NEU GEFASST 2026-08-26 (38))
 *     ZWEI WEGE IN EINE RUNDE, BEIDE VOM HANDY (44): „Runde vom Handy holen"
 *     und „Fortsetzen". Einen Alleinstart auf der Uhr gibt es nicht.
 *     ZWEI SEITEN, gewischt: Seite 0 SCORE, Seite 1 DETAILS. Eine dritte
 *     Seite mit Entfernungen und Caddy gab es bis (37); sie ist entfallen,
 *     siehe 0. ZWECK.
 *     SEITE 0 — SCORE (ScorePage), von oben nach unten:
 *       · Kopfzeile: ◀ „Loch 7 · Par 4" ▶ — die LOCHPFEILE sitzen hier, weil
 *         der Lochwechsel die haeufigste Handlung der Runde ist und kein
 *         Scrollen kosten darf. Am Rand ausgegraut statt entfernt.
 *       · „über Par ±X · N Loch" (overPar()) — rot über Par, grün darunter,
 *         gold bei E.
 *       · GPS-GUETE („±4 m", ab 10 m rot) und, NUR WENN ER STOCKT, der
 *         Abgleich („⟳12m"). Die Guete ist seit (38) die einzige
 *         Qualitaetsgroesse der Uhr: Start- und Endpunkt einer Messung gehen
 *         direkt in die gelernten Schlaegerlaengen des Handys ein.
 *       · KEIN „Sichern & abschliessen" und kein Verwerfen (44) — beides
 *         passiert am Handy. Der Fortschritt steht als Auskunft da.
 *       · Score, Putts, Tee-Ergebnis, Tee-Schlaeger, Approach-Block,
 *         Rest zur Fahne, Putt-Felder, Bunker/Strafen, Mitspieler (je
 *         EROEFFNETEM Platz eine Zeile, Beschriftung vom Handy oder
 *         „Mitspieler 2" — siehe `object Mitspieler`),
 *         Rubrik „Runde" (Lochpfeile, „+ Mitspieler", ↶ letzter Schlag,
 *         Abschluss, Übersicht).
 *       · OBEN LINKS EIN RUNDER SCHLAG-KNOPF (44, 48 dp): grau „📐" bereit,
 *         gruen „■" waehrend der Aufnahme, Langdruck bricht ab. Er liegt UEBER
 *         der Liste und kostet keine Freihaltung. Die gelaufene Strecke steht
 *         waehrend der Aufnahme in der Kopfzeile.
 *         SCHLAEGER UND SCHWUNG stehen waehrend einer Aufnahme GANZ OBEN in
 *         der Liste; sie springt beim Aufnahmestart dorthin.
 *         ÜBERHOLT — so war es in (43):
 *         „📐 Schlag 4" bzw. „■ 47 m", mittig auf 72 % der Breite. Er scrollt
 *         NICHT mit: Man tippt ihn beim Ball, mit Handschuh, ohne hinzusehen.
 *         LANGDRUCK bricht eine laufende Aufnahme ab.
 *         NICHT ueber die volle Breite und NICHT mehrere Knoepfe — auf einem
 *         runden Display ist die nutzbare Breite dort rund 60 %; (38) hatte
 *         vier Chips nebeneinander, von denen nur Stummel uebrig blieben.
 *         SCHLAEGER UND SCHWUNG stehen waehrend einer Aufnahme GANZ OBEN in
 *         der Liste, wo eine Zeile die volle Breite hat; die Liste springt
 *         beim Aufnahmestart dorthin.
 *         Der Platz fuer den Knopf wird als letztes Listenelement
 *         freigehalten (Spacer, 78 dp), nicht ueber contentPadding —
 *         `autoCentering` wuerde den ueberdecken.
 *     SEITE 1 — DETAILS: Trainingsfelder, die warten koennen.
 *     ZURUECK-GESTE waehrend einer Runde: Auf der Detailseite fuehrt sie zur
 *     Score-Seite. Auf der Score-Seite fuehrt sie NIRGENDWOHIN (46) — eine
 *     laufende Runde laesst sich nicht wegwischen. Heim kommt die Uhr, wenn
 *     das Handy die Runde beendet oder verwirft.
 *     HAPTIK: `buzzStart` (langer Stups), `buzzEnde` (Doppelschlag),
 *     `buzzNein` (drei unruhige). Am Ball schaut man nicht hin — die
 *     Rueckmeldung muss durchs Handgelenk ankommen UND zuzuordnen sein.
 *     AMBIENT (AmbientPlayScreen): Loch, Stand als grosse Zahl, laufende
 *       Aufnahme mit roher Streckenlaenge, sonst „kein GPS-Fix". Kein
 *       F/M/B, kein „spielt wie" mehr.
 *     KEIN Rundentimer (bewusst entfernt); roundStart wird intern gespeichert.
 *
 *  3b. BEDIENUNG / UX (Stand 2026-08-06 (4))
 *     - SCHNELL-SCORE: Reihe mit 4 runden Buttons (Par−1 … Par+2) über dem
 *       Score-Stepper — 1 Tap setzt den Score (QuickScoreRow, onScoreSet).
 *       Stepper bleibt für Eagle/hohe Scores; Score-Wert farbig relativ zu Par.
 *     - DETAILS EINGEKLAPPT: selten gebrauchte Felder (GIR, 1. Putt, Quality,
 *       Tee-Schläger, Approach-Lage, Bunker, Penalty-Typ, Toggles) öffnen erst
 *       per Chip „Details ▸" (zeigt Anzahl ausgefüllter Felder). Zustand liegt
 *       in GolfWatchApp (showDetails), damit er Picker-Aufrufe überlebt.
 *     - SCROLL: ListStates sind in GolfWatchApp GEHOISTET — die Scroll-Position
 *       des Loch-Screens bleibt beim Öffnen/Schließen eines Pickers erhalten
 *       (vorher Sprung an den Anfang). PositionIndicator im Scaffold zeigt die
 *       Position; Drehkrone/Lünette scrollen (rotaryScrollModifier). Bei
 *       Lochwechsel springt die Liste nach oben.
 *     - SCHUTZ: „Verwerfen" (Home) und „Sichern & abschließen" (Runde) brauchen
 *       einen zweiten Bestätigungs-Tap innerhalb von 4 s.
 *     - HAPTIK: Stepper und Schnell-Score bestätigen jeden Tap spürbar.
 *
 *  4. LIVE-TRACKING  (NEU GEFASST 2026-08-26 (38))
 *     Live (Singleton) haelt fix/err/running als Compose-State. Der Service
 *     abonniert GPS_PROVIDER (2 s) + NETWORK_PROVIDER als Fallback. Das ist
 *     seit (38) die KERNFUNKTION der App und nicht mehr Zulieferung fuer eine
 *     Anzeige.
 *     SENDETAKT (47): 10 s, wenn eine AUFNAHME LAEUFT, zuletzt getippt wurde
 *     (<2 min) oder man sich >15 m bewegt hat — sonst 60 s. Zustandswechsel
 *     beim Schlagtracken (Start, Ende, Abbruch) warten NICHT auf den Takt,
 *     sondern rufen `syncNow()`: Was das Handy anzeigen soll, ist ein Ereignis
 *     und kein Zustand, der irgendwann mitkommt.
 *     WOFUER DIE POSITION NOCH GEBRAUCHT WIRD, vollstaendig:
 *       a) Start- und Endpunkt einer Schlagmessung (recBegin/recStop),
 *       b) `live.pos` im Zeiger — die Uhr MELDET dem Handy, wo sie steht.
 *          Das Handy rechnet fuer diesen Punkt (`caddyFuerPunkt(_watchPos())`,
 *          PWA v3.18). Nach (38) ist das der EINZIGE Weg, auf dem das Handy
 *          die Position am Ball erfaehrt — tragend statt bloss hilfreich.
 *          Fehlt sie, rechnet das Handy still mit seiner eigenen Position,
 *          und die liegt oft im Trolley.
 *       c) `liveOf()` fuer die angezeigte Genauigkeit.
 *     KEINE Distanzrechnung mehr: greenFMB, greenDims, hazardsOnLine und lieAt
 *     sind mit (40) GELOESCHT, nicht nur unbenutzt. Von `object Geo` ist allein
 *     `dist` uebrig — die Luftlinie zwischen Start und Ende eines Schlags.
 *     „Auto-Loch" gibt es seit 2026-08-10 nicht mehr; gewechselt wird von Hand
 *     oder vom Handy.
 *
 *  5. VIRTUELLER CADDY — GIBT ES AUF DER UHR NICHT MEHR (2026-08-26 (38))
 *     Hier standen rund 120 Zeilen ueber `Caddy.plan`, Modus-Parameter,
 *     Wasser-Carry, das COCKPIT auf Seite 0 und die GAMEPLAN-Zeile. Alles
 *     davon ist ausser Betrieb: Die Rechenschleife ist gestrichen, die
 *     Composable, die es anzeigte, ist nicht mehr im Pager.
 *     WAS AN IHRE STELLE TRITT: nichts auf der Uhr. Das Handy rechnet mit
 *     seiner EV-Engine (Monte Carlo ueber die Streuung, Lie-Raster, zwei
 *     Zuege voraus) — ein Modell, das die Uhr ohnehin nie rechnen konnte;
 *     ihr fehlen Streuungsdaten und Rechenzeit. (16 (3)) hatte deshalb
 *     schon einmal versucht, den Widerspruch zu entschaerfen, indem die Uhr
 *     das Ergebnis des Handys uebernahm und mit „📱" statt „⌚" kennzeichnete.
 *     Das war die halbe Loesung; die ganze ist, gar nicht erst zu fragen.
 *     GESCHICHTE steht im Changelog und in der PWA-devdocs — sie wird hier
 *     nicht konserviert, weil ein Kapitel im Praesens beschreibt, was es
 *     GIBT. Die Regel dazu ist Pruefstand-Abschnitt 24cs (4): „Kein
 *     Entferntes als vorhanden."
 *     `object Caddy`, `object Wx` und die Geometrie in `object Geo` stehen
 *     EINE Fassung lang unbenutzt im Quelltext; Abbau in (39).
 *
 *     WAS BLEIBT UND WARUM (beides KEINE Rechnung):
 *       · WETTER (`Net.fetchWeather`, 20-min-Takt): eine MESSUNG der
 *         Bedingungen, die `buildRoundJson` als `conditions` mit der Runde
 *         speichert. Startet die Runde auf der Uhr und liegt das Handy im
 *         Auto, ist sie die einzige Quelle dafuer. `Wx.playsLike` wird davon
 *         NICHT mehr aufgerufen.
 *       · `live.pos` im Zeiger: die Uhr meldet, WO sie steht — siehe 4.
 *
 *  6. SCHLAGTRACKING — DER KERN DIESER APP (PWA-kompatibel!)
 *     Seit (38) ist das der Hauptzweck der Uhr; alles andere ist Eingabe.
 *     DATENMODELL wie in der PWA: je Loch eine Liste von POSITIONEN
 *     shots=[{lat,lng,club}], Schlagzahl = Positionen − 1.
 *     ABLAUF: „📐" (Startpunkt = gemittelte aktuelle Position) -> Schlaeger
 *     waehlen, bei Bedarf Schwunglaenge -> laufen -> beim Ball „■"
 *     (Endpunkt). Der Endpunkt ist automatisch der Startpunkt des naechsten
 *     Schlags (Kette, exakt wie playRecStop). Die Chips sitzen fest am
 *     unteren Rand der Score-Seite (siehe 3.).
 *     MESSGENAUIGKEIT ist hier keine Nebensache: `FixQuality.collect()`
 *     sammelt rund 3 s und mittelt invers-varianz-gewichtet (Gewicht 1/acc²),
 *     mit Bewegungssperre ueber MOVE_LIMIT_M. Ist der Fix schlechter als
 *     MAX_ACC, wird der Punkt NICHT genommen — und die Uhr VIBRIERT dabei
 *     (seit (38)): Ein Erfolg vibrierte immer schon, ein Misserfolg nicht,
 *     und am Handgelenk waren „nichts passiert" und „alles gut" damit nicht
 *     unterscheidbar. Bei abgelehntem Stop bleibt `rec` STEHEN, damit nicht
 *     der ganze Schlag verloren geht — ein paar Schritte weiter geht es
 *     meist.
 *     WAS DIE UHR SCHREIBT, IST ROH: `dist` ist die Luftlinie zwischen A und
 *     B, sonst nichts. Jeder fertige Schlag landet zusaetzlich als Messung in
 *     gpsShots {id,ts,club,swing,dist,accA,accB,latA/lngA/latB/lngB,hole}.
 *     WAS DAS HANDY DARAUS MACHT: `gpsShotsNachziehen` ruft beim EINTREFFEN
 *     `schlagNeutral` (PWA v4.80.1) — Wind, Temperatur, Hoehe (DGM) und Regen
 *     werden herausgerechnet, das Ergebnis (`distNeutral`) persistiert am
 *     Schlag und fuettert die Schlaegerlaengen. Der richtige Moment ist das
 *     Eintreffen, weil das Wetter von JETZT das Wetter des Schlags ist;
 *     trifft eine Messung Stunden spaeter ein, greift dort die 3-h-Sperre und
 *     nur die Hoehe wird gerechnet.
 *     HIER NICHTS DAVON NACHBAUEN. Die Zahl auf der Uhr darf sich von der im
 *     Handy unterscheiden — sie misst etwas anderes (rohe Strecke gegen
 *     neutrale Schlagdistanz), und genau deshalb steht auf der Uhr auch kein
 *     „spielt wie" daneben.
 *
 *  7. WICHTIGE FUNKTIONEN
 *     RoundService (svcStart/svcStop/svcNote) · Live · MainActivity (Permissions,
 *     Keep-Screen-On) · GolfWatchApp (State) · change() · syncNow()/scheduleSync()
 *     · overPar() · finishAndClose() · buildRoundJson() · saveLocal()/loadLocal()
 *     · Net.fetchData()/pushDraft()/fetchWeather() · Geo.dist() (Haversine —
 *     seit (40) die EINZIGE Funktion in `object Geo`) · FixQuality.collect()
 *     · recBegin()/recClub()/recSwing()/recStop()/recUndo().
 *
 *  8. ÄNDERUNGS-/KI-REGELN
 *     - ALLE AKTUELLEN DATEIEN LIEGEN IM REPO (Regel vom 27.08.2026) und
 *       koennen dort abgerufen werden, wenn nur ein Teil vorliegt:
 *         https://raw.githubusercontent.com/golftraining-lars/Golftraining/main/<datei>
 *       Dort liegen index.html, MainActivity.kt, tests.js,
 *       runde-simulation.js, runde-harness.js und worker.js.
 *       ANLASS: Am 27.08. brach `runde-simulation.js` mit MODULE_NOT_FOUND ab,
 *       weil `runde-harness.js` fehlte — und ZWEI echte rote Pruefungen blieben
 *       unentdeckt. Eine fehlende Datei heisst nicht „nicht pruefbar", sondern
 *       „holen".
 *       NICHT BLIND ZIEHEN: erst `APP_VERSION`/`WATCH_APP` vergleichen. Liegt
 *       hochgeladen ein neuerer Stand vor, gilt DIESER — am 26.08. stand im
 *       Repo noch Fassung (13), hochgeladen war (37). Die GitHub-API laeuft
 *       ohne Token schnell ins Rate-Limit, die Rohdateien gehen problemlos.
 *     - Additiv arbeiten, bestehende Feld-Parität zur PWA erhalten.
 *     - Neue Eingaben immer über change() führen (sichert + synct automatisch).
 *     - Netzwerk nie im Main-Thread (withContext(Dispatchers.IO)).
 *     - KEINE GERECHNETEN GROESSEN EINBAUEN. Siehe 0. ZWECK: Entfernungen,
 *       „spielt wie", Wind, Hoehe, Schlaegerempfehlungen und Gefahren gehoeren
 *       ins Handy. Diese Regel hat mit (40) eine technische Klammer bekommen —
 *       Pruefstand-Abschnitt 24cp prueft auf ABWESENHEIT von `Caddy`, `Wx`,
 *       Geometrie und Karten-Parser. Ein Wiedereinbau faellt sofort durch.
 *     - Nach Änderung: diese Doku + CHANGELOG fortschreiben.
 *
 *  8b. DRAFT-DATEI (2026-08-14) — der heisse Teil liegt getrennt
 *     `draft.json` im Repo enthaelt NUR {round, ts, live, gpsShots}: wenige kB
 *     statt 3 MB. Waehrend der Runde lesen und schreiben Uhr UND Handy nur noch
 *     diese Datei; die grosse bleibt unberuehrt, bis die Runde abgeschlossen
 *     wird. Vorher kostete jede Eingabe 3 MB runter + 3 MB rauf, dazu der
 *     Pull-Takt mit weiteren 3 MB — ueber ein halbes Gigabyte je Runde.
 *     · Net.fetchDraftFile() / Net.pushDraftFile()  (SHA-Tuersteher, X-Path)
 *     · Net.fetchDraft() nimmt sie zuerst, faellt auf die grosse Datei zurueck
 *     · Net.pushDraft() ebenso; der ALTE Weg bleibt vollstaendig als Netz
 *     · gpsShots reisen MIT (winzig, und sie duerfen nicht bis zum Rundenende
 *       liegenbleiben — bei einem Absturz waeren sie sonst weg)
 *     · 409 = das andere Geraet war schneller: frisch lesen, VEREINEN, neu
 *       senden. Nicht ueberschreiben — der andere steht gerade auf der Bahn.
 *     WORKER ab v2.6 noetig (draft.json in CFG.PATHS + path-Parameter bei GET).
 *     Bei aelterem Worker antwortet er 403 und alles laeuft wie zuvor.
 *
 *  9. HINWEIS PWA-SEITE (einmalig nachziehen!)
 *     mergeDB() in der PWA mergt gpsShots NICHT (Object.assign -> lokal gewinnt).
 *     Empfohlene Ergänzung dort, damit Uhr-Messungen nie verloren gehen:
 *       out.gpsShots = _mergeArr(L.gpsShots, R.gpsShots, x=>x.id||j(x));
 *     Die SCHLÄGE der Runde (holes[].shots) sind davon nicht betroffen —
 *     sie kommen über _draftRound und werden von openAddRound/RTRACK gelesen.
 *
 *  ------------------------------------------------------------------------
 *  CHANGELOG (neueste zuerst — bei JEDER Änderung ergänzen: Datum · was · wo)
 *  ------------------------------------------------------------------------
 *  2026-08-30 (55) · DER TURNIERMODUS ZAEHLT MIT, STATT AUSZUWAEHLEN.
 *     KORRIGIERT auf Nachfrage, einen Tag nach (54): „Ich moechte, dass er auf
 *     1 startet, da ich die Uhr benutzen will, um JEDEN SCHLAG MITZUZAEHLEN.
 *     Ich will also im Laufe des Loches den Score immer weiter hochzaehlen,
 *     bis dann am Ende der Endscore feststeht."
 *     IN (54) SETZTE DER ERSTE TIPP PAR. Das war fuer eine ANDERE Benutzung
 *     gedacht — den Endscore NACH dem Loch eintragen, und dort ist Par
 *     tatsaechlich der haeufigste Wert.
 *     FUER DAS MITZAEHLEN IST ES GENAU FALSCH: Wer beim ersten Schlag tippt
 *     und eine 4 sieht, muss dreimal zurueck. Jetzt beginnt die Zaehlung bei
 *     1 — jeder Tipp ein Schlag, der erste Tipp der erste Schlag. Am Ende des
 *     Lochs steht die Zahl, die man ohnehin im Kopf mitgefuehrt hat.
 *     DAS IST DER UNTERSCHIED ZWISCHEN EINEM ZAEHLER UND EINER AUSWAHL, und er
 *     entscheidet ueber die ganze Bedienung: Ein Zaehler wird WAEHREND des
 *     Lochs benutzt, eine Auswahl danach. Meine Annahme in (54) war die
 *     falsche — und sie stand sogar begruendet im Quelltext, was sie nicht
 *     richtiger gemacht hat.
 *     `par` ALS PARAMETER ENTFALLEN: Es diente nur dem Startwert. Ein Wert,
 *     den niemand liest, laesst beim naechsten Lesen fragen, wo er einfliesst.
 *     „−" BLEIBT und ist hier wichtiger als vorher: Beim Mitzaehlen vertippt
 *     man sich mitten im Loch. Ein Zaehler ohne Rueckweg waere auf der Bahn
 *     unbrauchbar.
 *
 *  2026-08-30 (54) · TURNIERMODUS — ZWEI ZAHLEN, SONST NICHTS.
 *     GEWUENSCHT: „Fuehre auf der Uhr noch einen Turniermodus ein, den ich auf
 *     der Startseite auswaehlen kann. Da erfasse ich dann auf einem Loch nur
 *     den Gesamtscore von mir und einem Mitspieler. Nichts anderes."
 *     WOZU: Im Turnier zaehlt man fuer sich UND fuer einen Mitspieler, unter
 *     Zeitdruck und oft mit Handschuh. Alles, was die normale Maske sonst kann
 *     — Putts, Lage, Schlaeger, Strafschlaege —, ist dort nicht nur
 *     ueberfluessig, sondern IM WEG: Jede zusaetzliche Zeile ist eine
 *     Gelegenheit, das Falsche zu tippen.
 *     NICHTS ANDERES HEISST NICHTS ANDERES. Zwei Zahlen, ein Loch, weiter.
 *     EIGENE SEITE STATT ABGESPECKTER `ScorePage`: Wer dieselbe Seite mit
 *     einem Schalter halbiert, hat zwei Seiten in einer, und jede spaetere
 *     Aenderung muss beide Faelle bedenken. `TurnierPage` hat einen einzigen
 *     Zweck und passt auf einen Bildschirm.
 *     NUR DIE ERSTE SEITE WIRD GETAUSCHT, nicht der Pager: Karte, Caddy und
 *     Wetter bleiben erreichbar. Wer im Turnier doch einmal eine Entfernung
 *     braucht, wischt weiter.
 *     DER MITSPIELER KOMMT VOM HANDY — Regel aus (42): Die `index.html` ist
 *     dort fuehrend, die Uhr fuehrt keine eigene Liste. Ohne angelegten
 *     Mitspieler bleibt der Modus verschlossen, aber der Knopf SAGT, was zu
 *     tun ist. Ein gesperrter Knopf ohne Grund erzeugt genau die
 *     Ratlosigkeit, die diese App vermeiden soll.
 *     DER ERSTE TIPP SETZT PAR, nicht 1: Im Turnier ist Par der haeufigste
 *     Wert; wer eine 5 braucht, tippt einmal weiter statt fuenfmal von null.
 *     KEIN EIGENER SPEICHERWEG: Die Eingaben gehen durch dasselbe `change()`
 *     wie sonst und landen im selben Entwurf. `msc1` reist seit Langem zum
 *     Handy — es ist dieselbe Runde, nur eine andere Ansicht.
 *
 *  2026-08-30 (53) · DAS PROTOKOLL SAGT JETZT, WARUM DER SCHLAG NICHT GING.
 *     GEMELDET am 30.08.: „Das Starten des GPS-Trackings von der Uhr
 *     funktioniert weiterhin nicht gut. Bitte detailliere das Eventlog der Uhr
 *     hierzu deutlich."
 *     ZWEI LUECKEN IM PROTOKOLL, und beide betreffen dieselbe Frage:
 *     1. DIE SCHLAG-ZEILEN KANNTEN DEN GPS-ZUSTAND NICHT. Sie sagten, WAS
 *        passierte und WIE LANGE es dauerte — aber nicht, worauf die
 *        Entscheidung beruhte. „Start abgelehnt · GPS zu ungenau" nannte nicht,
 *        WIE ungenau. Jetzt traegt jede Zeile Genauigkeit, Alter des Fixes, ob
 *        er nach `FixQuality.usable` brauchbar waere, und ob die Ortung
 *        ueberhaupt laeuft (`Diagnose.gpsLage()`).
 *     2. DIE UHR SCHWIEG ZU IHREN EIGENEN GPS-LUECKEN. Im Handy-Protokoll
 *        derselben Runde steht zweimal „Uhr meldet seit ueber 90 s keine
 *        Position" — das HANDY bemerkt die Luecke, die UHR sagt nichts dazu.
 *        Dabei ist sie die einzige, die weiss, woran es lag.
 *        EINE LUECKE KANN SICH NICHT MELDEN, WAEHREND SIE LAEUFT — es kommt ja
 *        nichts. Sie meldet sich jetzt, wenn sie ENDET: Der erste Fix danach
 *        traegt die Dauer, die Genauigkeit und ob er vom Satelliten oder nur
 *        vom Netzwerk kam. Ab 20 Sekunden, damit ein normales Sammelfenster
 *        nicht als Luecke erscheint.
 *     WOZU DAS GUT IST: „Laesst sich nicht starten" und „GPS war weg" sehen
 *     auf der Uhr gleich aus. Ab dieser Fassung stehen sie als zwei
 *     verschiedene Zeilen im Protokoll.
 *
 *  2026-08-29 (52) · DIE UHR SAGT, WELCHE FASSUNG SIE IST.
 *     BEFUND AUS DEM AUDIT vom 29.08.: Ob auf dem Geraet wirklich die neueste
 *     Fassung laeuft, wusste niemand. Der Pruefstand vergleicht nur Dateien,
 *     die ohnehin zusammen geschrieben werden; die Fassung der Uhr stand
 *     allein auf ihrem eigenen Bildschirm.
 *     DAS HAT IN DER WOCHE VOM 24.–29.08. MEHRFACH STUNDEN GEKOSTET:
 *     Behebungen wurden auf dem Platz geprueft, BEVOR sie auf dem Handgelenk
 *     waren — und beide Seiten hielten die Korrektur fuer wirkungslos. Genau
 *     dasselbe ist der PWA passiert, bis sie seit v5.02 ihren Startvermerk
 *     schreibt.
 *     `app` STEHT JETZT IM LIVE-ZEIGER, den die Uhr ohnehin im Takt schickt:
 *     kein zusaetzlicher Abruf, keine zusaetzliche Datei, rund 20 Byte. Das
 *     Handy protokolliert sie beim ersten Sehen und bei jedem Wechsel (PWA
 *     v5.13, `watchFassungPruefen`) — damit steht im Fehlerprotokoll, WELCHE
 *     Fassungen wirklich miteinander geredet haben.
 *     NICHT IN `note`: Das ist ein Freitext, der sich mit jeder Fassung
 *     aendert. Eine Fassungsnummer muss man VERGLEICHEN koennen, nicht lesen.
 *
 *  2026-08-28 (51) · DER KNOPF ZEIGT, OB ER UEBERHAUPT KANN.
 *     ZWEITE MELDUNG ZUM SELBEN THEMA: „Der Button startet das Schlagtracken
 *     nicht." (50) hat eine ECHTE Ursache behoben — ein verklemmtes
 *     `measuring`. Aber im mitgeschickten Protokoll stand die WAHRSCHEINLICHERE
 *     Antwort woertlich, und ich habe sie beim ersten Lesen uebersehen:
 *       „Uhr meldet seit ueber 90 s keine Position"
 *     OHNE BRAUCHBAREN FIX LEHNT `recBegin` AB. `FixQuality.usable` verlangt
 *     hoechstens 15 m Streuung und einen nicht zu alten Fix; fehlt beides,
 *     setzt die Funktion eine Statuszeile und vibriert kurz.
 *     BEIDES REICHT NICHT. Die Statuszeile steht auf einer Seite, die man beim
 *     Ball nicht liest, und ein kurzer Stups geht im Gehen unter. Aus Sicht des
 *     Benutzers PASSIERT NICHTS — und er tippt weiter, statt zu warten oder
 *     weiterzugehen.
 *     DIE PRECONDITION GEHOERT AN DEN KNOPF, NICHT IN DIE FEHLERMELDUNG. Ein
 *     Knopf, der nicht kann, soll das VORHER zeigen, nicht hinterher melden.
 *     Drei Zustaende:
 *       bereit        dunkel, „📐"
 *       nicht bereit  gedaempft mit goldenem Rand, „GPS"
 *       Aufnahme      gruen, die gelaufenen Meter
 *     DIESELBE SCHWELLE WIE DIE MESSUNG (`FixQuality.MAX_ACC`, 15 m). Zwei
 *     Zahlen fuer dieselbe Frage laufen auseinander — und dann zeigt der Knopf
 *     „bereit", waehrend die Messung ablehnt. Das waere schlimmer als gar keine
 *     Anzeige. Es ist dasselbe Muster wie bei `MAX_ACC`/`GPS_MAX_ACC` im
 *     Folge-Audit: zwei Wahrheiten ueber dieselbe Sache.
 *     ANTIPPEN BLEIBT ERLAUBT, auch wenn er nicht bereit ist: Die Ablehnung
 *     sagt dann, WORAN es liegt (Genauigkeit in Metern). Das ist mehr wert als
 *     ein gesperrter Knopf, der gar nichts erklaert — wer gesperrt wird, weiss
 *     nicht, warum.
 *     WAS DAS FUER DIE MELDUNG HEISST: (50) und (51) beheben ZWEI
 *     verschiedene Ursachen desselben Symptoms. Welche zugeschlagen hat, sagt
 *     ab (48) die Schlag-Spur im Protokoll — „Tipp ins Leere" fuer die eine,
 *     „Start abgelehnt · GPS zu ungenau" fuer die andere.
 *
 *  2026-08-28 (50) · DER SCHLAG-KNOPF KONNTE STUMM VERKLEMMEN.
 *     GEMELDET: „Wenn ich das Schlagtracken auf der Uhr ueber den Button
 *     aktiviere, passiert nichts. Der Button startet das Schlagtracken leider
 *     nicht."
 *     URSACHE: `recBegin` begann mit `if (measuring) return` — einem LAUTLOSEN
 *     Ruecksprung. Und `measuring` wurde NUR IM ERFOLGSFALL freigegeben: Die
 *     Zeile `measuring = false` sass MITTEN im `scope.launch`, hinter
 *     `FixQuality.collect`. Endete die Nebenlaeufigkeit vorher — Bildschirm
 *     aus, Ambient-Wechsel, Neuaufbau der Oberflaeche — oder warf `collect`,
 *     blieb `measuring` FUER IMMER `true`.
 *     DANACH WAR DER KNOPF TOT: kein Zeichen, kein Ton, keine Zeile im
 *     Protokoll. Jeder weitere Tipp sprang stumm zurueck, bis jemand die App
 *     neu startete. Auf der Runde merkt man das erst am Loch danach.
 *     DREI RIEGEL:
 *     1. FREIGABE IM `finally`. Es gibt keinen Weg mehr aus dem Block, der die
 *        Sperre gesetzt laesst — auch nicht den Abbruch, und der ist auf einer
 *        Uhr der haeufigste Fall. Der Abbruch wird weiterhin weitergeworfen
 *        (Regel aus (10)); aufgeraeumt wird trotzdem.
 *     2. EIN WAECHTER: Haengt die Sperre laenger als 20 s, gilt sie als
 *        verklemmt und wird freigegeben — mit Protokolleintrag, damit man
 *        sieht, DASS es passiert ist. Das Sammelfenster dauert wenige
 *        Sekunden; alles darueber ist ein Fehler und kein Warten.
 *     3. KEIN RUECKSPRUNG OHNE ANTWORT. Wer tippt, bekommt Vibration, Status
 *        und eine Protokollzeile. EIN KNOPF, DER NICHTS TUT UND NICHTS SAGT,
 *        IST SCHLIMMER ALS EINER MIT EINER FEHLERMELDUNG: Man tippt weiter und
 *        verliert die Runde.
 *     DASSELBE IN `recStop` — dort stand derselbe stumme Ruecksprung, und ein
 *     verklemmtes `measuring` haette zusaetzlich die laufende Messung
 *     unbeendbar gemacht.
 *     ANMERKUNG ZUR DIAGNOSE: Das eingereichte Protokoll stammt vom 26.08. und
 *     von PWA 4.94 — es enthaelt keine Schlag-Spur, weil die erst mit (48)
 *     eingefuehrt wurde. Der Befund stammt daher aus dem Quelltext, nicht aus
 *     dem Protokoll. Mit (48) oder neuer stuende „Schlag: Tipp ins Leere" dort,
 *     und die Ursache waere in einer Zeile sichtbar gewesen.
 *
 *  2026-08-28 (49) · DER LETZTE GROSSE ABRUF IST WEG.
 *     ENTSCHIEDEN AM 28.08. auf Nachfrage: Der Big-File-Rueckfall soll nicht
 *     bleiben. (48) hatte ihn aus `pushDraft` und `fetchDraft` entfernt; in
 *     `loadData` stand er noch — war `watch.json` nicht erreichbar, holte die
 *     Uhr die grosse `trainingsdaten.json` (rund 3 MB) und parste sie.
 *     WARUM ER AUCH DORT WEG MUSSTE: `leseBegrenzt` deckelte ihn seit (48),
 *     aber der Spitzenbedarf beim Parsen liegt beim Mehrfachen des Rohtexts —
 *     auf einer Uhr mit 128 MB Grenze ist das kein Rueckfall, sondern ein
 *     zweites Risiko. UND ER HILFT NICHT: Fehlt `watch.json`, ist entweder die
 *     Leitung weg — dann gelingt eine tausendmal groessere Datei erst recht
 *     nicht — oder das Handy hat sie nie geschrieben, und dann ist DAS zu
 *     beheben. Ein Rueckfall, der nur bei gutem Funk gelingt, hilft genau
 *     dann nicht, wenn man ihn braucht.
 *     WAS STATTDESSEN GREIFT: der lokale Zwischenspeicher (`cacheRead`). Er
 *     war immer die bessere Antwort; der grosse Rueckfall stand nur davor.
 *     MITGEGANGEN, weil ohne Aufrufer: `readData`, `fetchRaw`, `fetchData`,
 *     `fullSha`, `DATA_URL`, `FRESH_URL`. DIE UHR LIEST JETZT NUR NOCH
 *     `watch.json`, `draft.json` und `probe.json` — zusammen wenige Kilobyte.
 *     `leseBegrenzt` WURDE NICHT MITGELOESCHT, sondern auf ALLE VIER
 *     verbliebenen Lesestellen gesetzt (dazu die Wetterabfrage). Ein Riegel,
 *     der nur an der Stelle sitzt, an der es einmal knallte, schuetzt nur vor
 *     der Wiederholung.
 *     GEGENSTUECK AM HANDY (PWA v4.94): `MAX_ACC` hier und `GPS_MAX_ACC` dort
 *     beantworten dieselbe Frage — ab welcher Streuung ist ein Punkt fuer eine
 *     Schlagmessung unbrauchbar. Beide stehen auf 15 m, und bis jetzt hielt
 *     sie NICHTS zusammen; der Pruefstand vergleicht sie ab v4.94.
 *
 *  2026-08-28 (48) · DER RUECKFALLWEG HAT DIE APP GETOETET — ER IST WEG.
 *     GEMELDET am 28.08.: „Das Schlagtracken dauert sehr lange bis es startet,
 *     auch nach mehreren Anlaeufen nicht. Dann bricht es zwischendurch ab und
 *     die ganze App schliesst sich."
 *     DAS UHR-PROTOKOLL NANNTE DIE URSACHE WOERTLICH:
 *       07:48:29 ABSTURZ OutOfMemoryError: Failed to allocate a 14784520 byte
 *       allocation with 3204560 free bytes @Net.readData < Net.pushDraft
 *     IN `pushDraft` STAND EIN RUECKFALLWEG: Gelang der schlanke Schreibvorgang
 *     in `draft.json` nicht, holte er die GROSSE `trainingsdaten.json` —
 *     mehrere Megabyte —, baute daraus ein JSONObject, aenderte den Entwurf
 *     darin und schickte alles zurueck.
 *     DREI GRUENDE FUER SEIN ENDE, jeder allein ausreichend:
 *     1. ER KONNTE SEIT WORKER v2.9 GAR NICHT MEHR GELINGEN. Der ALT-Modus ist
 *        dort geschlossen und antwortet mit 426. Seit Wochen ein garantierter
 *        Fehlschlag — nur ein sehr teurer.
 *     2. ER SPRENGTE DEN SPEICHER. Rohtext, JSONObject und Sende-Abzug liegen
 *        gleichzeitig im Heap; bei 128 MB Grenze reicht das fuer den Absturz.
 *     3. ER LIEF GENAU DANN, WENN ES OHNEHIN KLEMMTE — ausgeloest vom
 *        Fehlschlag des schlanken Weges, also bei schlechtem Funk. Auf eine
 *        ueberlastete Leitung legte er Megabyte obendrauf. Das Protokoll zeigt
 *        die Folge: 37 von 60 Vorgaengen misslungen.
 *     EIN SICHERHEITSNETZ, DAS BEI JEDEM AUFFANGEN REISST UND DEN SPRINGENDEN
 *     MITNIMMT, IST KEINES.
 *     EBENFALLS ENTFERNT: der Rueckfall in `fetchDraft`, der die grosse Datei
 *     holte, um einen Entwurf zu FINDEN. Ist `draft.json` nicht lesbar, ist es
 *     die Leitung — dann gelingt eine tausendmal groessere Datei erst recht
 *     nicht.
 *     NEU `leseBegrenzt()`: Vor dem Lesen wird die angekuendigte Groesse
 *     geprueft und beim Lesen mitgezaehlt; ueber 6 MB wird abgebrochen und
 *     GEMELDET. Ein Absturz ist die schlechteste Art, eine Grenze zu erfahren.
 *     DIAGNOSE ERWEITERT — die drei Groessen, die am 28.08. gefehlt haben:
 *       · SPEICHER (`speicherText`, `speicherPruefen`): steht im Puls und im
 *         Abzug, warnt einmalig unter 20 MB Rest. Eine Groesse, die einen
 *         umbringen kann, gehoert ins Protokoll, BEVOR sie es tut.
 *       · FEHLERARTEN (`fehlerArt`, `fehlerBilanz`): „37 misslungen" ist eine
 *         Zahl ohne Richtung. Zeitablauf, Verbindungsabriss und Konflikt haben
 *         drei verschiedene Ursachen und drei verschiedene Antworten. Ich habe
 *         sie am 28.08. von Hand ausgezaehlt — das macht die Uhr jetzt selbst.
 *       · SCHLAG-SPUR (`Diagnose.schlag`): Das Schlagtracken ist der einzige
 *         Zweck dieser Uhr und kam im Protokoll UEBERHAUPT NICHT VOR. Jetzt
 *         bekommt jeder Schritt eine Zeile mit DAUER — Tipp, Sammelfenster,
 *         Startpunkt, Ergebnis, Abbruch. Erst daran sieht man, ob das Warten am
 *         GPS liegt oder am Funk. Was nicht protokolliert ist, laesst sich
 *         nicht untersuchen.
 *     OFFEN, im Protokoll sichtbar und NICHT in dieser Fassung behoben: Der
 *     Lochzeiger pendelte am 28.08. zwischen Uhr und Handy (Loch 8 ⇄ 1 ⇄ 2,
 *     „Handy-Loch verworfen" auf der einen, „ÜBERNOMMEN" auf der anderen
 *     Seite). Das ist ein eigener Befund und gehoert in eine eigene Fassung.
 *
 *  2026-08-27 (47) · DER AUFNAHMESTART MELDET SICH SOFORT.
 *     GEMELDET: „Die Uebertragung von der Uhr an die App, dass ein
 *     Schlagtracking gestartet wurde, dauert sehr lange und klappt manchmal
 *     nicht."
 *     ES WAR KEIN FUNKPROBLEM, sondern zwei Bedingungen in der Sendeschleife.
 *     (1) `if (rec == null)` UMSCHLOSS DEN GANZEN VORGANG. Solange eine
 *         Aufnahme lief, hat diese Schleife UEBERHAUPT NICHT GESENDET. Der
 *         Kommentar dazu lautete „laufende Messung nicht stoeren" — gemeint
 *         war Ruecksicht, gewirkt hat das Gegenteil: Der Aufnahmestart
 *         erreichte das Handy nur, wenn zufaellig etwas ANDERES einen Vorgang
 *         ausloeste, etwa ein eingetippter Score. Das ist das gemeldete
 *         „klappt manchmal nicht", und es sah wie schlechter Empfang aus.
 *         DIE LAUFENDE MESSUNG IST GERADE DAS, WAS DAS HANDY SEHEN SOLL
 *         (Regel vom 27.08.). Sie zu verschweigen, um sie nicht zu stoeren,
 *         verfehlt den Zweck.
 *     (2) DER TAKT WUSSTE NICHTS VON IHR. `sollWarten` richtete sich nur
 *         danach, ob zuletzt GETIPPT (`frisch`) oder GEGANGEN (`bewegt`)
 *         wurde. Beim Aufnahmestart steht man still und hat nichts getippt —
 *         also 60 Sekunden, im ungeduldigsten Moment der ganzen Runde.
 *         Jetzt: `rec != null || frisch || bewegt`.
 *     UND DER EIGENTLICHE PUNKT: EIN ZUSTANDSWECHSEL, DEN DAS ANDERE GERAET
 *     ANZEIGEN SOLL, IST EIN EREIGNIS — kein Zustand, der beim naechsten Takt
 *     mitkommt. `recBegin`, `recFinish` und `recCancel` rufen deshalb `syncNow()`.
 *     Dieselbe Lehre wie bei den Eingaben in (5), nur eine Ebene weiter.
 *     Der Abbruch gehoert ausdruecklich dazu: Ohne Meldung stuende das Band am
 *     Handy weiter, obwohl auf der Uhr nichts mehr laeuft — ein Zustand, den
 *     nur eine Seite kennt, ist schlimmer als gar keiner.
 *     `letztePos` wird waehrend einer Aufnahme WEITERHIN nicht fortgeschrieben:
 *     Die Bewegungsschwelle soll von der Stelle aus messen, an der zuletzt
 *     gemeldet wurde; schoebe sich der Bezugspunkt mit, bliebe `bewegt` fuer
 *     immer falsch.
 *     DER RIEGEL GEGEN DOPPELSENDUNGEN BLEIBT (`letzterPushMs > 5 s`): Wer
 *     sofort sendet UND den Takt nicht bremst, bezahlt das Tempo mit Funk und
 *     Akku.
 *     ERWARTETE LAUFZEIT danach: Uhr sendet sofort, das Handy zieht im
 *     Vordergrund alle 2 s (`SYNC_MS_VORN`) — das Band sollte in ein bis drei
 *     Sekunden stehen statt in bis zu einer Minute.
 *     PRUEFSTAND: 24cp haelt fest, dass waehrend einer Aufnahme gesendet wird,
 *     dass der Takt eilig wird, dass Start, Ende und Abbruch sofort melden,
 *     dass der Doppelsende-Riegel bleibt und die Bewegungsschwelle ihren
 *     Bezugspunkt behaelt.
 *
 *  2026-08-27 (46) · DREI VERSCHIEDENE VIBRATIONEN · KEIN WEGWISCHEN MEHR.
 *     ZWEI VORGABEN VOM 27.08.
 *     (1) „DIE UHR SOLL VIBRIEREN, WENN MAN DAS SCHLAGTRACKEN STARTET, UND
 *         VIBRIEREN, WENN MAN ES BEENDET."
 *         SIE TAT ES BEREITS — und trotzdem war die Meldung berechtigt: Start,
 *         Ende und Ablehnung gaben ALLE DASSELBE, einen 40-ms-Stups. Am
 *         Handgelenk, im Gehen, mit Handschuh ist das kaum wahrnehmbar und
 *         schon gar nicht unterscheidbar. Eine Rueckmeldung, die man nicht
 *         zuordnen kann, ist keine — dann fragt man doch wieder auf den
 *         Bildschirm, und genau das soll sie ersparen.
 *         DREI MUSTER, unterschieden im RHYTHMUS und nicht in der Staerke
 *         (Staerke spuert man durch einen Aermel schlecht):
 *           `buzzStart` ein LANGER Stups (90 ms) — „es laeuft".
 *           `buzzEnde`  ZWEI kurze (50-70-50) — der Doppelschlag heisst
 *                       „fertig", wie bei jedem Timer.
 *           `buzzNein`  DREI kurze, schnelle (30-50 x3) — unruhig, und das
 *                       soll es sein: Hier ist gerade NICHTS aufgenommen.
 *         `buzz(c, muster)` nimmt jetzt eine Wellenform; der alte Stups bleibt
 *         die Vorgabe und wird noch von der Akkuwarnung benutzt.
 *     (2) „WENN ICH AUF SEITE 1 ODER 2 NACH LINKS WISCHE, LANDE ICH IM
 *         STARTBILDSCHIRM. DAS DARF NICHT PASSIEREN."
 *         Bis (45) fuehrten zwei Wischer binnen zwei Sekunden dorthin, mit
 *         einem Hinweis in der Statuszeile dazwischen. Gedacht war das als
 *         Schutz — aber auf einem runden Display ist die Wischgeste die
 *         haeufigste Fehlbedienung ueberhaupt: Sie liegt genau dort, wo man
 *         die Seite wechselt. Zwei davon passieren beilaeufig, und die
 *         Statuszeile liest man beim Gehen nicht.
 *         ES GAB DORT AUCH NICHTS ZU HOLEN: Seit (44) kann man auf dem
 *         Startbildschirm waehrend einer Runde nichts tun, was die Runde
 *         betrifft — Anlegen, Abschliessen und Verwerfen passieren am Handy.
 *         Der Weg fuehrte also aus Versehen an einen Ort ohne Zweck.
 *         JETZT: `screen == "play"` beantwortet die Zurueck-Geste nur noch mit
 *         „Runde laeuft · Ende am Handy". `lastBackAt` ist entfallen.
 *         DER RUECKWEG BLEIBT, ER KOMMT NUR VOM HANDY: Wird dort beendet oder
 *         verworfen, raeumt die Uhr auf (`clearLocal`, `svcStop`) und steht
 *         von selbst wieder auf dem Startbildschirm. Das ist der einzige Weg,
 *         und er ist der richtige.
 *         WAS DAS KOSTET, ausdruecklich: Solange eine Runde laeuft, kommt man
 *         mit der Wischgeste nicht mehr aus der App. Wer sie verlassen will,
 *         nimmt die Systemgeste. Faellt das Handy aus, laeuft die Runde weiter
 *         und wird beim naechsten Abgleich beendet — verloren geht nichts,
 *         alles liegt im Entwurf.
 *     Die Seitennavigation ist unberuehrt: Ein Wisch auf der Detailseite
 *     fuehrt weiter zurueck auf die Score-Seite.
 *     PRUEFSTAND: drei Muster mit ihren Wellenformen, ihre Aufrufstellen, die
 *     gedrehte Pruefung „jede Ablehnung vibriert" (sucht jetzt `buzzNein`),
 *     und als Gegenprobe zur Sperre: Ein Ende vom Handy MUSS heimfuehren und
 *     dabei aufraeumen.
 *
 *  2026-08-27 (45) · DER SCHLAG-KNOPF ZEIGT METER, NICHT „STOPP".
 *     VORGABE VOM 27.08.: „Bei einem Schlagtracken soll in dem Button die
 *     Meterzahl angezeigt werden und nicht das Stopp-Rechteck."
 *     (44) hatte hier zu knapp gedacht. Das „■" sagte nur, DASS eine Aufnahme
 *     laeuft — und genau das sagt die gruene Flaeche ohnehin, aus dem
 *     Augenwinkel und ohne Lesen. Das Zeichen war also die zweite Auskunft
 *     ueber denselben Zustand, waehrend die einzige Zahl, die man im Gehen
 *     wirklich will, in der Kopfzeile der Liste stand — dort, wo man beim
 *     Laufen nicht hinsieht.
 *     EINE FLAECHE, ZWEI AUSSAGEN: Farbe = Zustand, Zahl = Messwert.
 *     OHNE EINHEIT: Bei 48 dp Durchmesser kostet jedes zusaetzliche Zeichen
 *     eine Schriftgroesse, und dass Meter gemeint sind, weiss man.
 *     DIE SCHRIFT SCHRUMPFT MIT DER STELLENZAHL — bis 99 gross (18 sp), ab
 *     100 kleiner (15 sp). Drei grosse Ziffern passen nicht in den Kreis, und
 *     ein abgeschnittener Messwert waere schlimmer als gar keiner.
 *     Die Strecke steht zusaetzlich weiter in der Kopfzeile; dort liest man
 *     sie im Stehen, im Knopf im Gehen.
 *     PRUEFSTAND: 24cp haelt fest, dass im Knopf die Strecke steht und NICHT
 *     mehr das Stopp-Zeichen, und dass die Schrift ab dreistellig schrumpft.
 *
 *  2026-08-27 (44) · DAS HANDY BESITZT DIE RUNDE. SCHLAG-KNOPF OBEN LINKS.
 *     VIER VORGABEN VOM 27.08., mit drei Fotos belegt:
 *     (1) SCHLAG-KNOPF ALS RUNDER BUTTON OBEN LINKS — „da behindert er am
 *         wenigsten". Dritter Anlauf, und die Reihenfolge ist die Lehre:
 *           (38) vier Chips ueber die volle Breite unten -> auf einem RUNDEN
 *                Display blieben Stummel („1", „✕ S", „Pu", „Sch").
 *           (43) EIN breiter Knopf unten, 72 % -> lesbar, kostete aber 78 dp
 *                Freihaltung und lag im Weg.
 *           (44) EIN RUNDER Knopf, 48 dp, oben links, UEBER der Liste.
 *         WARUM RUND DIE RICHTIGE FORM IST: Ein Kreis hat keine Ecken, die
 *         ueber die Bildrundung hinausragen koennen — genau das war der
 *         Fehler von (38). Und er kostet KEINE Freihaltung: Die Liste laeuft
 *         unter ihm durch, im Ruhezustand steht das erste Element wegen
 *         `autoCentering` ohnehin tiefer. Die 78 dp am Listenende sind wieder
 *         frei.
 *         KEINE BESCHRIFTUNG, nur ein Zeichen: grau „📐" heisst bereit, gruen
 *         „■" heisst Aufnahme laeuft. Bei 48 dp ist jeder Text ein Stummel;
 *         die gelaufene Strecke steht waehrend der Aufnahme in der KOPFZEILE,
 *         wo Platz dafuer ist. Langdruck bricht ab (unveraendert aus (43)).
 *     (2) KEIN „SICHERN & ABSCHLIESSEN" AUF DER UHR. `finishAndClose`, der
 *         Chip in der Rubrik „Runde" und `onFinish` sind weg. Der Fortschritt
 *         bleibt als Auskunft stehen („12 von 18 Loechern erfasst ·
 *         Abschliessen am Handy") — er war der Grund, warum man dorthin
 *         scrollt.
 *     (3) KEIN VERWERFEN UND KEIN ALLEINSTART. `onDiscard`, `pushDiscarded`,
 *         `onNew`, `PickScreen`, der Bildschirm „pick" und `onSide` sind
 *         entfernt. Es bleiben ZWEI Wege in eine Runde, beide vom Handy aus:
 *         „Runde vom Handy holen" und „Fortsetzen".
 *         WARUM DAS MEHR IST ALS AUFRAEUMEN: Solange eine Runde an ZWEI Orten
 *         entstehen und enden konnte, gab es zwei Zustaende ueber dieselbe
 *         Sache. Genau daran hingen die Mitspieler-Meldung von heute (die Uhr
 *         fuehrte eine eigene Liste) und die offene Frage, wer bei
 *         Rundenumfang und EDS fuehrt. Ein Weg weniger ist kein Verlust an
 *         Funktion, sondern einer an Widerspruchsmoeglichkeit.
 *         LESEN UND SCHREIBEN BLEIBEN GETRENNT: Die Uhr LIEST die
 *         Verworfen-Marke weiter (`discardedTs`) und beendet ihre Runde, wenn
 *         das Handy sie setzt — nur schreiben tut sie sie nicht mehr.
 *         `side` bleibt als Zustand, wird aber nur noch UEBERNOMMEN
 *         (`side = dr.side`), nie auf der Uhr gesetzt.
 *     (4) EINE AUF DER UHR BEGONNENE MESSUNG ERSCHEINT AM HANDY.
 *         `recLiveJson()` schickte den Live-Zeiger erst, wenn ein SCHLAEGER
 *         gewaehlt war — mit der Begruendung „vorher hat das Handy nichts
 *         anzuzeigen". Das war falsch: Anzuzeigen ist, DASS eine Messung
 *         laeuft und WO sie begann. Und die Bedingung traf ausgerechnet den
 *         wichtigsten Moment — man tippt „Schlag hier", geht los und waehlt
 *         den Schlaeger unterwegs; seit (43) faellt beides in dieselben
 *         Sekunden. `club` und `swing` reisen jetzt NUR MIT, WENN SIE SCHON
 *         DA SIND.
 *         DIE ZWEITE URSACHE LAG AM HANDY: Das Band fuer die Uhr-Aufnahme gibt
 *         es dort seit PWA v1.68 und hatte KEINEN EINZIGEN Aufrufer — nur das
 *         Vollbild baute sich ein eigenes. In der Eingabemaske, wo man die
 *         halbe Runde verbringt, stand nichts. Behoben in PWA v4.86.
 *     PRUEFSTAND: neue Riegel in 24cp — kein Alleinstart, kein Abschliessen,
 *     kein Verwerfen auf der Uhr, aber die Verworfen-Marke wird weiter
 *     befolgt; Rundenumfang nur uebernommen; Live-Zeiger ohne
 *     Schlaeger-Bedingung; Knopf oben links, rund, ohne `fillMaxWidth()`.
 *
 *  2026-08-27 (43) · SCHLAGTRACKEN: EIN KNOPF STATT VIER STUMMEL.
 *     GEMELDET mit Foto: „Die Art wie das Schlagtracken auf der Uhr
 *     integriert ist, ist nicht gut. Ich kann kaum was erkennen." Auf dem Bild
 *     stehen unten „1", „✕ S", „Pu", „Sch" — Reste von vier Chips, die ueber
 *     die Rundung hinauslaufen und auf den Seitenpunkten des Pagers liegen.
 *     MEIN FEHLER AUS (38), und zwar ein rechnerischer: Ich habe eine Reihe
 *     aus bis zu VIER Tippflaechen ueber `fillMaxWidth()` an den unteren Rand
 *     eines RUNDEN Displays gesetzt.
 *     DIE RECHNUNG, die ich nicht gemacht habe: Auf einem Kreis mit Radius r
 *     ist die nutzbare Breite in der Hoehe y ueber der Bildmitte 2·√(r²−y²).
 *     Ein Streifen, dessen Mitte 0,8 r unter der Mitte liegt, hat noch rund
 *     60 % der Bildbreite — nicht 100 %. Vier Flaechen passen dort nicht,
 *     egal wie man sie anordnet. `fillMaxWidth()` ist auf einem runden
 *     Display am oberen und unteren Rand schlicht gelogen.
 *     (Am 14.08. war dieselbe Verankerung schon einmal zurueckgenommen
 *     worden. Ich habe in (38) den DAMALIGEN Einwand geprueft — „verdeckt
 *     Inhalt", der stimmte nicht mehr — und den eigentlichen uebersehen.)
 *     NEU:
 *       · EIN Chip, mittig, `fillMaxWidth(0.72f)`, 52 dp hoch, 18 dp ueber
 *         den Seitenpunkten. Beschriftung kurz genug, um auch bei 60 %
 *         nutzbarer Breite ganz dazustehen: „📐 Schlag 4" bzw. „■ 47 m".
 *       · SCHLAEGER UND SCHWUNG ZIEHEN IN DIE LISTE, wo der Kreis am
 *         breitesten ist und eine Zeile die volle Breite hat — als normale
 *         `SelectRow`, NUR waehrend einer Aufnahme, ganz oben. Die Liste
 *         springt beim Aufnahmestart dorthin (`LaunchedEffect(recActive)`),
 *         also stehen sie genau dann unter dem Daumen, wenn man sie braucht:
 *         „Schlag hier" tippen, Schlaeger waehlen, losgehen.
 *       · ABBRECHEN auf dem LANGDRUCK desselben Knopfes, mit Vibration. Ein
 *         fuenfter Chip waere wieder ein Stummel; und Abbrechen ist selten,
 *         Stoppen der Normalfall — die haeufige Handlung bekommt den kurzen
 *         Weg. Zuruecknehmen (↶) bleibt wie seit dem 14.08. unten in der
 *         Rubrik „Runde": zwei zerstoerende Aktionen an EINEM Ort, nur durch
 *         einen Zustand unterschieden, waeren eine Falle.
 *       · Freihaltung am Listenende 56 -> 78 dp (52 + 18 + Luft).
 *     DIESELBE ZAHL AN HANDGRIFFEN wie vorher, nur mit Flaechen, die man mit
 *     Handschuh trifft.
 *     PRUEFSTAND: neue Riegel in 24cp — der Knopf darf am unteren Rand NICHT
 *     `fillMaxWidth()` ohne Anteil nehmen, es muss EIN Knopf sein und keine
 *     `Row`, Abbrechen liegt auf dem Langdruck, Schlaeger/Schwung stehen in
 *     der Liste und die Liste springt beim Start nach oben. Die Rechnung
 *     steht im Kommentar daneben, damit der naechste Umbau sie nicht wieder
 *     uebergeht.
 *
 *  2026-08-27 (42) · MITSPIELER: DAS HANDY FUEHRT, DIE UHR SPIEGELT.
 *     GEMELDET: „Auf der Uhr werden mehr Mitspieler angezeigt als in der
 *     index.html." Stimmte, und zwar aus ZWEI Gruenden — beide stammten aus
 *     (39), wo die Uhr selbst Plaetze eroeffnen durfte:
 *       1. `Mitspieler.plaetze` lag in den Prefs und WUCHS NUR. Wer einmal
 *          drei Plaetze aufgemacht hatte, sah drei Zeilen — auch auf der
 *          naechsten Runde, auch wenn das Handy niemanden mehr kannte. Ein
 *          Zaehler, der nur eine Richtung kennt, ist kein Zustand, sondern
 *          eine Hochwassermarke.
 *       2. Uebernommen wurde nur eine NICHT LEERE Liste
 *          (`if (dr.mitspieler.isNotEmpty())`). Ein am Handy ENTFERNTER
 *          Mitspieler kam damit NIE auf der Uhr an: Entfernen war die einzige
 *          Aenderung, die nicht reiste. Fuer sich genommen schon ein Fehler,
 *          zusammen mit (1) die gemeldete Beobachtung.
 *     VORGABE VOM 27.08., und sie ist die ganze Fassung: NUR DIE IN DER PWA
 *     FUER DIESE RUNDE HINTERLEGTEN SPIELER ERSCHEINEN AUF DER UHR.
 *     UMSETZUNG:
 *       · `Mitspieler.plaetze`, `plaetzeSetzen` und `label` sind weg; die
 *         Zeilen entstehen wieder aus `namen`. Die Pref `mitspielerN` wird
 *         nicht mehr gelesen (sie darf auf alten Geraeten liegen bleiben).
 *       · Der Chip „+ Mitspieler" in der Rubrik „Runde" ist entfernt.
 *       · `RepoDraft.mitspieler` ist `List<String>?` statt `List<String>`.
 *         DAS IST DER KERN: `null` heisst „nicht gesagt" — der Schluessel
 *         fehlte, der Entwurf stammt von der Uhr selbst; eine LEERE LISTE
 *         heisst „ausdruecklich keine Mitspieler" und wird uebernommen. Das
 *         alte `?: emptyList()` machte aus dem einen das andere und haette
 *         beim naiven Beheben jede Namensliste geloescht.
 *       · Der Compose-Spiegel traegt jetzt die NAMEN (`mitspielerNamen`)
 *         statt einer Platzzahl.
 *       · `buildRoundJson` ECHOT DIE NAMEN NICHT MEHR. (37) tat es aus Sorge,
 *         der Merge am Handy koennte sie verlieren — nachgemessen in der
 *         Rundensimulation (v4.84.2, Abschnitt „Entwurfs-Merge") stimmt das
 *         Gegenteil: Ein FEHLENDER Schluessel wird von `Object.assign`
 *         uebergangen und die Namen bleiben; ein mitgeschickter, aber
 *         VERALTETER Stand ueberschreibt sie. Schweigen ist hier der sichere
 *         Weg. Die Loch-Scores `msc1..msc3` reisen unveraendert weiter — die
 *         traegt die Uhr ein, sie sind ihre eigene Aussage.
 *     DER PREIS, ausdruecklich benannt: (39) ist damit wieder weg. Wer die
 *     Runde auf der Uhr beginnt und das Handy im Bag laesst, kann keinen
 *     Mitspieler erfassen — die Zeile entsteht erst, wenn das Handy einen
 *     Namen vergeben hat. Das ist die Kehrseite von „eine Wahrheit", und sie
 *     ist billiger als zwei Listen, die auseinanderlaufen. Die Meldung vom
 *     27.08. ist der Beleg dafuer.
 *     PRUEFSTAND: Abschnitt 24db gedreht — die Pruefungen aus (39) verlangten
 *     genau das Gegenteil und halten jetzt fest, dass die Umkehr gewollt war.
 *     Neu geprueft: leere Liste kommt an, fehlender Schluessel nicht, die Uhr
 *     echot keine Namen, die Loch-Scores reisen weiter.
 *
 *  2026-08-27 (41) · NACHGEREICHT: VIER COMPILERFEHLER AUS (40).
 *     GEMELDET mit Bildschirmfoto: `:app:compileDebugKotlin` bricht mit vier
 *     Fehlern ab, alle an derselben Stelle:
 *         var plan by remember { mutableStateOf<Caddy.Plan?>(null) }
 *     „Unresolved reference 'Caddy'". Der Zustand, den die geloeschte
 *     Caddy-Schleife fuellte und die geloeschte Seite 0 anzeigte, stand noch da.
 *     WARUM ES DURCHRUTSCHTE — und das ist die eigentliche Lehre: Beim Abbau
 *     wurde nach AUFRUFEN gesucht (`Caddy.plan(`, `GameplanScreen(`). Eine
 *     TYP-ANGABE ist kein Aufruf und faellt durch dieses Raster; ein Zustand,
 *     den niemand mehr liest, meldet sich beim Compiler nur als WARNUNG — bis
 *     sein Typ verschwindet, dann als Fehler. Der Pruefstand war gruen, weil er
 *     Node ist und kein Kotlin uebersetzt.
 *     EBENFALLS VERWAIST GEFUNDEN, mit derselben Suche nach Typen statt
 *     Aufrufen:
 *       · `gpKurs` — merkte sich den im Gameplan-Bildschirm gewaehlten Platz.
 *       · `alterSek(iso)` — gab das Alter eines Zeitstempels in Sekunden und
 *         hatte GENAU EINEN Aufrufer: die Pruefung, ob die Caddy-Empfehlung
 *         des Handys noch frisch genug ist (≤ 90 s).
 *       · fuenf Importe, die nur `HolePage` brauchte: `border`,
 *         `RoundedCornerShape`, `rememberScrollState`, `verticalScroll`,
 *         `clip`. (`android.widget.Toast` war schon vor (38) unbenutzt und
 *         bleibt liegen — nicht meine Baustelle.)
 *     Dazu zwei Kommentare im Praesens ueber Verschwundenes: der Erklaertext
 *     zur automatischen Schlagerfassung (Funktion weg seit (35), der genannte
 *     Schalter seit (38)) und der Kopf des Kopplungstests, der noch „Caddy"
 *     unter den Aufgaben fuehrte.
 *     PRUEFSTAND: neue Sperrklinke in „Gleichlauf Uhr ↔ App" — sie sucht nach
 *     TYPEN (`Caddy.Plan`, `CourseGeo?`, `HoleGeo?`, `GeoFeature`,
 *     `ElevProfil`, `PlanHole`) und nach Zustand fuer geloeschte Bildschirme,
 *     nicht nach Aufrufen. Dazu Arbeitsregel 2b im Kopf von `tests.js`:
 *     Bei einem Kotlin-Rueckbau NACH TYPEN SUCHEN, und einmal am Rechner
 *     uebersetzen, bevor man ihn fuer fertig haelt. Ein gruener Pruefstand ist
 *     hier kein Beweis — er kann diese Fehlerklasse gar nicht sehen.
 *
 *  2026-08-26 (40) · ABBAU: DAS RECHENWERK IST GELOESCHT, NICHT NUR STILLGELEGT.
 *     Die zweite Haelfte der Vorgabe vom 26.08. (38) hatte die AUFRUFSTELLEN
 *     entfernt und den Quelltext bewusst stehenlassen — damit eine
 *     Verhaltensaenderung und ein Rueckbau nicht in derselben Fassung liegen
 *     und ein fehlendes Stueck am Handgelenk zuzuordnen bleibt. Es fehlte
 *     nichts. Also weg damit.
 *     WARUM NICHT „FUER SPAETER" STEHENLASSEN: Solange der Quelltext dasteht,
 *     ist der naechste Einbau ein Einzeiler — und dann gibt es wieder zwei
 *     Antworten auf dieselbe Frage. Genau der Zustand, den (16 (3)) zu
 *     entschaerfen versuchte und den die Vorgabe vom 26.08. beendet hat.
 *     GELOESCHT:
 *       · `object Caddy` (plan, planCore, pick, mp, modeLabel) und
 *         `object Wx` (tempFactor, windRel, playsLike, arrowRel, elevLabel,
 *         line) — zusammen rund 480 Zeilen.
 *       · Aus `object Geo` ALLES ausser `dist`: bearing, compass8, interp,
 *         pointInRing, ringCentroid, greenRingFor, greenFMB, greenDims,
 *         greenDimsCompute, bboxHit, hazardsOnLine, lieAt, lieFactor,
 *         lieLabel — rund 400 Zeilen.
 *       · Der Karten-Parser (`GEO_KEEP`, `llArray`, `feature`, beide
 *         `parseGeo`, `parseGeoObj`) und die Datentypen `CourseGeo`,
 *         `HoleGeo`, `GeoFeature`, `ElevProfil` — rund 235 Zeilen.
 *       · `HolePage` (rund 530 Zeilen), `targetOf`, `loadGeoAsync`,
 *         `ringCache`.
 *       · `object Swing` samt `recBeginAuto`/`recStopAuto`, `autoShot` und
 *         den vier `android.hardware.Sensor*`-Importen. Ausser Betrieb seit
 *         (35): Putts erkannte sie prinzipbedingt nicht, die meisten Chips
 *         ebenso wenig, und ein ERFUNDENER Schlag faellt erst auf, wenn die
 *         gelernten Schlaegerlaengen falsch sind.
 *       · `GameplanScreen` samt Chip im Startmenue, `hatPlaene`/`onGameplan`,
 *         dem `"gameplan"`-Zweig in `when(screen)` und im `BackHandler`,
 *         dazu `PlanHole`, `AppData.plans` und `Repo.parsePlans`.
 *         AUSDRUECKLICHER WUNSCH VOM 26.08. Sie rechnete nie etwas — die
 *         Plaene kamen fertig vom Handy. Trotzdem raus: Nach 0. ZWECK zeigt
 *         die Uhr keine EMPFEHLUNG mehr, und ein vorab gefasster Plan ist
 *         eine, nur aelteren Datums. Dass die Rechnung woanders stattfand,
 *         macht die Anzeige nicht zu etwas anderem.
 *     MainActivity.kt: 13.470 -> rund 11.500 Zeilen.
 *     WEITER GEGANGEN ALS ANGEKUENDIGT, zweimal, und beides mit Absicht:
 *       · `geoRaw`/`geoObj` sind aus `CourseDef` RAUS. Die Uhr sicherte bei
 *         jedem lokalen Speichern das komplette Roh-JSON der Platzkarte als
 *         Text in die SharedPreferences — ein paar hundert kB je Platz,
 *         serialisiert fuer Distanzen, die es nicht mehr gibt. Eine
 *         gesicherte Runde wird damit um Groessenordnungen kleiner. Aeltere
 *         Staende duerfen den Schluessel behalten; er wird beim Lesen
 *         uebergangen.
 *       · `PlayLive` traegt nur noch `hasFix`, `acc`, `err`. Die sechs
 *         Distanzfelder wurden seit (38) ohnehin nicht mehr gefuellt.
 *     KOPPLUNGSTEST auf BEIDEN Seiten gekuerzt: Die Aufgaben „geo", „caddy"
 *     und „lie" sind weg. Sie verglichen Rechnungen, und ein Vergleich mit
 *     einer Seite ist keiner — die Aufgaben waeren unbeantwortet geblieben
 *     und der Prueflauf haette drei Abweichungen gemeldet, wo keine sind. Ein
 *     Pruefstand, der grundlos Alarm schlaegt, bringt einem bei, den Alarm zu
 *     ignorieren. `quelle` meldet `karte: false` — das Feld bleibt, damit ein
 *     aelteres Handy eine Antwort bekommt statt keiner, und weil „nein" hier
 *     die richtige Auskunft ist. Uebrig bleiben `club`, `clubs`, `liste`,
 *     `quelle`: WELCHE DATEN hat die Uhr. Genau das entscheidet noch, ob eine
 *     Runde auf ihr brauchbar wird.
 *     GEGENSTUECK AM HANDY (PWA v4.84, zwingend zeitgleich): `watchPayload()`
 *     schickt kein `geo` und kein `strat.gameplans` mehr — `watch.json` faellt
 *     von einigen hundert kB auf wenige, und der Push kostet nicht mehr bei
 *     jeder Aenderung eine Serialisierung des groessten Datenteils.
 *     Der Pruefplan der PWA (`probePlan()`, dort) stellt keine Rechenaufgaben
 *     mehr und braucht keinen eingezeichneten Platz.
 *     PRUEFSTAND: Abschnitt 24cp ist GEDREHT. Die elf Gleichlauf-Vergleiche
 *     (vier `playsLike`-Konstanten, sieben Lagefaktoren) sind ersetzt — sie
 *     meldeten zuletzt „App 0.0022 / Uhr null" und haetten das fuer einen
 *     Fehler gehalten. An ihrer Stelle steht die Pruefung auf ABWESENHEIT:
 *     kein Rechenwerk, keine Geometrie, kein Parser, keine Platzkarte im
 *     Datenmodell, keine Schwungerkennung, kein Gameplan — aber `dist` MUSS
 *     bleiben, und das wird ausdruecklich geprueft. Gedreht statt geloescht,
 *     weil eine geloeschte Pruefung nichts sagt und eine gedrehte festhaelt,
 *     dass die Abwesenheit GEWOLLT ist.
 *     WER ETWAS DAVON SUCHT: Es lebt in der PWA. Dort wird gerechnet, dort
 *     wird geplant, dort steht es.
 *     NACHTRAG 27.08. (Doku, kein Code): ARBEITSREGEL 0 in Kapitel 8 — alle
 *     aktuellen Dateien liegen im Repo und werden bei Bedarf dort abgerufen.
 *     Anlass war der Rueckbau selbst: `runde-simulation.js` liess sich ohne
 *     `runde-harness.js` nicht fahren, und die Simulation hatte ZWEI echte
 *     rote Pruefungen (sie forderte die Platzkarte in `watch.json`, die
 *     PWA v4.84 gerade entfernt hatte). Beide sind nachgeholt und gedreht;
 *     `runde-simulation.js` steht bei 81 ok, 0 fail. Siehe PWA v4.84.1.
 *
 *  2026-08-26 (39) · MITSPIELER OHNE NAMEN: DIE UHR EROEFFNET PLAETZE.
 *     GEMELDET am 26.08.: „Auf der Smartwatch kann man keinen Mitspieler
 *     angeben." Stimmte — und zwar prinzipiell, nicht wegen eines Fehlers.
 *     (37) band die Zeilen an `Mitspieler.namen`, und Namen vergibt nach der
 *     Regel vom 26.08. allein das Handy. Wer die Runde auf der Uhr begann
 *     (Handy im Bag, im Auto, leer), bekam deshalb GAR KEINE Zeile. Eine
 *     Tastatur auf dem Handgelenk ist keine Antwort — mit Handschuh im Wind
 *     tippt niemand einen Namen.
 *     DER PUNKT, an dem sich das aufloest: `msc1..msc3` sind Zahlen an FESTEN
 *     PLAETZEN. Gespeichert wird nach PLATZ, nicht nach Person. Um Platz 1 zu
 *     fuellen, braucht die Uhr keinen Namen, sondern eine ZEILE; der Name ist
 *     Beschriftung und darf nachtraeglich kommen — er wirkt dann RUECKWIRKEND
 *     auf alle schon eingetragenen Loecher, ohne dass ein einziger Wert
 *     angefasst werden muss.
 *     DIE UHR EROEFFNET ALSO PLAETZE UND VERGIBT KEINE NAMEN. Die Regel vom
 *     26.08. bleibt damit unangetastet.
 *     UMSETZUNG:
 *       · `Mitspieler.plaetze` (0-3) neben `namen`, in den Prefs
 *         (`mitspielerN`). `setzen` hebt die Platzzahl mit an, wenn das Handy
 *         mehr Namen liefert — ein benannter Mitspieler ohne Zeile waere ein
 *         Widerspruch, den niemand aufloesen kann.
 *       · `Mitspieler.label(i)` gibt den Namen oder „Mitspieler 2". Das ist
 *         keine Notloesung, sondern die ehrliche Auskunft: Die Zahlen stehen
 *         richtig, nur der Name fehlt noch.
 *       · Chip „+ Mitspieler" in der Rubrik „Runde" der Score-Seite. Tipp
 *         macht einen Platz auf, LANGDRUCK schliesst den letzten. Dort und
 *         nicht bei den Zeilen: Man macht den Platz einmal je RUNDE auf,
 *         nicht einmal je Loch.
 *       · SCHLIESSEN LOESCHT NICHTS. Die Zahlen bleiben in `msc*` stehen —
 *         sonst waere ein Fehlgriff mit Handschuh eine geloeschte Runde.
 *     KEIN ZWEITES DATENFELD, ausdruecklich: `plaetze` geht NICHT in den
 *     Entwurf. Das Handy sieht die Belegung an den Daten — gibt es irgendwo
 *     ein `msc2`, ist Platz 2 in Gebrauch. Ein eigenes Feld waere eine zweite
 *     Wahrheit ueber denselben Sachverhalt, und zwei Wahrheiten laufen
 *     auseinander, sobald beide Seiten schreiben. Dieselbe Lehre wie beim
 *     Lochzeiger, nur billiger zu haben.
 *     COMPOSE-SPIEGEL: `Mitspieler` ist ein Singleton mit `@Volatile` —
 *     Compose abonniert das nicht. Ohne den State `mitspielerN` in
 *     `GolfWatchApp` wuerde die Liste beim Eroeffnen eines Platzes nicht neu
 *     gezeichnet. Genau dieser Fehler hat beim Lochzeiger fuenf Fassungen
 *     gekostet; das Singleton bleibt der Speicher, die Oberflaeche liest nur
 *     den Spiegel.
 *     GEGENSTUECK AM HANDY (PWA v4.83, noetig): Mitspieler stehen jetzt auf
 *     der Scorekarte (`cardBlock`, je belegtem Platz eine Zeile, ohne Namen
 *     „Mitspieler 2"), im Teilen-Text, und `mitspielerName` fragt beim
 *     Entfernen nach — dort ruecken die Plaetze auf, und traegt die Uhr in
 *     derselben Minute auf Platz 2 ein, landen ihre Werte danach lautlos
 *     unter dem Namen des bisherigen Spielers 3.
 *     NICHT IN DIESER FASSUNG: der Abbau aus (38) — `Caddy`, `Wx`, die
 *     Geometrie in `Geo`, `HolePage` und die `Swing`-Reste stehen weiterhin
 *     unbenutzt im Quelltext. Er bleibt eine eigene Fassung; eine
 *     Verhaltensaenderung daneben zu legen, verwischt die Ursache, falls am
 *     Handgelenk etwas fehlt.
 *     OFFEN, bewusst spaeter: Namensliste der haeufigen Mitspieler in
 *     `watch.json` (wie `clubDistances`), damit man auf der Uhr aus einer
 *     Liste waehlt statt „Mitspieler 1" zu bekommen. Das ist ein Aufsatz auf
 *     die Platz-Loesung, keine Alternative dazu — deshalb danach.
 *
 *  2026-08-26 (38) · DIE UHR RECHNET NICHT MEHR. SEITE 0 IST ENTFALLEN.
 *     VORGABE VOM 26.08.: Keine Entfernungsangaben mehr auf der Uhr, kein
 *     „spielt wie", keine Wettereinfluesse. Die Uhr dient rein der Eingabe
 *     der Lochdaten und dem Setzen von Start und Ende beim Schlagtracken.
 *     Fuer Entfernungen und „spielt wie" ist das HANDY fuehrend; die Uhr
 *     misst per eigenem GPS und schickt die Rohwerte hinueber.
 *     DAS WAR LAENGST BESCHLOSSEN, NUR NICHT GEBAUT: Der Kommentar an
 *     `gpsShotsNachziehen` in der PWA (v4.80.1, 26.08.) sagt woertlich, die
 *     Uhr enthalte „keinerlei Anpassungslogik, nicht einmal `playsLike`".
 *     Das stimmte nicht — `Wx.playsLike` und `object Caddy` liefen hier
 *     weiter. Diese Fassung bringt den Code zur Regel, nicht umgekehrt.
 *     WAS ENTFAELLT (Verhalten):
 *       · Der Pager hat ZWEI Seiten: 0 = Score, 1 = Details. `HolePage` ist
 *         nicht mehr im `when(page)` — mit ihr F/M/B, „spielt wie",
 *         Pin-Distanz, Gruenmasse, die Rechenzeile target→plays mit
 *         Wind-/Temp-/Lage-Anteilen, die Caddy-Zeile samt Modusumschaltung,
 *         die Gefahrenwarnung, die Wetterzeile und die Gameplan-Zeile 📋.
 *       · Die Caddy-Schleife (LaunchedEffect ueber das 11-m-Raster) ist
 *         GESTRICHEN. Eine Rechnung ohne Leser ist Akku, den man auf der
 *         18. Bahn braucht.
 *       · `liveOf()` rechnet keine Ringgeometrie mehr, sondern liefert Fix,
 *         Genauigkeit und Fehlertext. Die Felder front/mid/back/pin/
 *         greenDepth/greenWidth in `PlayLive` bleiben eine Fassung lang
 *         stehen (die unbenutzte `HolePage` liest sie noch), werden aber
 *         nicht mehr gefuellt.
 *       · `AmbientPlayScreen` zeigt Loch, Stand als grosse Zahl und eine
 *         LAUFENDE AUFNAHME mit roher Streckenlaenge. Letzteres ist neu und
 *         der eigentliche Gewinn dort: Eine vergessene Aufnahme haengt den
 *         naechsten Startpunkt an die falsche Stelle, und im Ambient sah man
 *         sie bisher ueberhaupt nicht.
 *       · Der Erklaertext zur Schlag-Automatik auf der Detailseite ist raus,
 *         dazu `autoShot`/`onAutoShot` aus DetailPage und PlayPager. (35)
 *         hatte die Automatik entfernt und den Text stehenlassen — er
 *         beschrieb einen Knopf auf einer Seite, die es nicht mehr gibt.
 *     WAS UMZIEHT:
 *       · LOCHPFEILE ◀ ▶ in die Kopfzeile der Score-Seite. Der Lochwechsel
 *         ist die haeufigste Handlung der Runde und darf kein Scrollen
 *         kosten. Sie gehen ueber denselben `onHoleDelta` wie vorher auf
 *         Seite 0; die Pfeile unten in der Rubrik „Runde" bleiben zusaetzlich
 *         (derselbe Zustand, kein zweites Feld).
 *       · GPS-GUETE („±4 m", ab 10 m rot) unter „ueber Par". Sie hing bis
 *         (37) unter der Mitteldistanz — also an einer Zahl, die es nicht
 *         mehr gibt. Ohne sie sieht eine Messung mit 12 m Streuung genauso
 *         souveraen aus wie eine mit 3 m, und man merkt den Unterschied erst
 *         Wochen spaeter an den gelernten Schlaegerlaengen.
 *       · SCHLAGZEILE fest an den unteren Rand der Score-Seite. Am 12.08.
 *         schon einmal verankert, am 14.08. wieder geloest — sie verdeckte
 *         damals Mitteldistanz, Gameplan und Caddy-Zeile. GENAU DIESE DREI
 *         GIBT ES NICHT MEHR; der Grund fuer die Ruecknahme ist mit ihnen
 *         entfallen. Der Platz wird als letztes Listenelement freigehalten
 *         (Spacer), NICHT ueber `contentPadding` — `autoCentering` rechnet
 *         oben und unten eigenen Platz dazu und wuerde den Wert ueberdecken.
 *     WAS NEU IST:
 *       · ABLEHNUNG VIBRIERT. `recBegin`/`recStop` gaben bei zu schlechtem
 *         GPS nur einen Statustext aus — auf einer Seite, die man beim Ball
 *         nicht liest, mit Handschuh schon gar nicht. Ein Erfolg vibrierte
 *         seit jeher; ein Misserfolg nicht, und damit waren „nichts
 *         passiert" und „alles gut" am Handgelenk nicht unterscheidbar.
 *       · Bei abgelehntem STOP bleibt `rec` STEHEN statt verworfen zu
 *         werden: Wer beim Ball keinen brauchbaren Fix hat, soll in ein paar
 *         Schritten erneut tippen koennen, statt den ganzen Schlag zu
 *         verlieren.
 *     WAS BLEIBT, OBWOHL ES NACH RECHNUNG AUSSIEHT:
 *       · DIE WETTERABFRAGE (`Net.fetchWeather`, 20-min-Takt). Sie ist keine
 *         Anpassung, sondern eine MESSUNG der Bedingungen, die
 *         `buildRoundJson` als `conditions` mit der Runde speichert. Startet
 *         die Runde auf der Uhr und liegt das Handy im Auto, ist sie die
 *         einzige Quelle dafuer. `Wx.playsLike` wird davon nicht aufgerufen.
 *       · `live.pos` im Zeiger. Die Uhr meldet weiter, WO sie steht; das
 *         Handy rechnet fuer diesen Punkt (`caddyFuerPunkt(_watchPos())`).
 *         Nach diesem Umbau ist das der EINZIGE Weg, auf dem das Handy die
 *         Position am Ball erfaehrt — tragend statt bloss hilfreich.
 *     NICHT IN DIESER FASSUNG, ausdruecklich: der ABBAU. `object Caddy`,
 *     `object Wx`, die Geometrie in `object Geo`, `parseGeo`/`parseGeoObj`,
 *     `CourseGeo`/`HoleGeo`/`GeoFeature`/`ElevProfil`, `HolePage`,
 *     `targetOf`, `loadGeoAsync` und die Reste von `object Swing` stehen
 *     unbenutzt im Quelltext. [NACHTRAG: geloescht in (40), nicht (39) — (39)
 *     kam die Mitspieler-Meldung dazwischen.] Rund 2000 Zeilen —
 *     dieselbe Regel wie beim Ausbau der Schlag-Automatik in (35):
 *     Verhaltensaenderung und Rueckbau nicht in derselben Fassung, sonst
 *     verwischt der Rueckbau die Ursache, falls am Handgelenk etwas fehlt.
 *     DIE EINE ECHTE EINBUSSE, benannt: Das HANDY braucht ab jetzt die ganze
 *     Runde ueber GPS. Im Trolley liegen lassen und auf der Uhr nachsehen
 *     geht nicht mehr.
 *     BEREITS DA GEWESEN, beim Bau festgestellt: Die 3-s-Mittelung, die als
 *     Neuerung geplant war, macht `FixQuality.collect()` seit 2026-08-12.
 *     Und „Auto-Loch" ist seit 2026-08-10 draussen — die Entscheidung vom
 *     26.08., es zu entfernen, bestaetigt den Stand und kostet keine
 *     Aenderung. Beides gehoert hierher, damit niemand danach sucht.
 *
 *  2026-08-26 (37) · MITSPIELER: ENDSCORE JE LOCH, BIS ZU DREI, NUR DIE ZAHL.
 *     Wunsch vom 26.08.: bis zu drei weitere Spieler erfassen — je Loch NUR
 *     deren Endscore, nichts weiter, mit vergebbaren Namen.
 *     ARBEITSTEILUNG: Namen vergibt das HANDY (Tastatur; PWA v4.81, Bereich
 *     "Mitspieler" in der Eingabemaske). Die Uhr uebernimmt sie aus dem
 *     Entwurf (`round.mitspieler`), zeigt je Name eine Zeile auf Seite 2
 *     unter den Strafschlaegen und merkt sie in den Prefs (`Mitspieler`,
 *     global statt durch drei Signaturen gereicht).
 *     DATEN: `msc1..msc3` je Loch — HoleEntry, jsonToEntry, entryToJson,
 *     buildRoundJson, adoptHoles (nimm-Regel, Loch-Zeitstempel entscheidet
 *     wie bei jedem Feld; null loescht nichts). Der eigene Entwurf ECHOT die
 *     Namen im Runden-JSON — mergeDraft am Handy nimmt bei gleicher Runde
 *     Object.assign(alt, neu), ohne Echo hinge der Namenserhalt am Zweig.
 *     UI: SelectRow statt Stepper — ein Tipp, Raster 1-12, "–" loescht.
 *     BEWUSST NICHT: Putts, Statistik, SG fuer Mitspieler — "nur der
 *     Endscore" ist die Vorgabe, und alles Weitere waere Pflegelast auf der
 *     Bahn.
 *
 *  2026-08-26 (36) · TEE-SCHLAEGER AUF SEITE 2 · DREI FELDER PRAEZISER BENANNT.
 *     TEE-SCHLAEGER (Wunsch vom 26.08.): von Seite 3 (Details) auf Seite 2,
 *     direkt UNTER das Tee-Ergebnis. Dieselbe Lehre wie bei der
 *     1.-Putt-Distanz (2026-08-14 (5)): Was auf Seite 3 steht, bleibt auf der
 *     Runde regelmaessig leer — und der Tee-Schlaeger wird im selben Moment
 *     gewusst wie das Tee-Ergebnis. Auf Seite 3 ist er RAUS (ein Feld an zwei
 *     Orten hiesse zwei Wahrheiten); die Rubrik dort heisst nun "Kurzspiel".
 *     Am Par 3 bewusst dabei: dort ist der Tee-Schlag der Approach.
 *     DREI UMBENENNUNGEN, gleiche Felder, gleiche Datenpfade — nur die
 *     Beschriftung sagt jetzt, was das Feld schon immer meinte:
 *       "1. Putt"       -> "Länge des 1. Putts" (⭐ bleibt — traegt SG)
 *       "Rest z. Fahne" -> "Rest z. Fahne nach Appr."
 *       "Rest danach"   -> "Rest nach 1. Putt"
 *     Die Auswahl-Titel ziehen mit, wo sie abwichen. Keine Aenderung an
 *     Speicherung oder PWA-Abgleich — reine Anzeige.
 *
 *  2026-08-26 (35) · RUNDENENDE: DAS HANDY FUEHRT · SCHLAG-AUTOMATIK AUSGEBAUT.
 *     NEUE REGEL (Vorgabe vom 26.08.): Fuer das Beenden einer Runde ist das
 *     Handy fuehrend, und nur das Handy. Bisher gehorchte die Uhr der
 *     `roundDone`-Marke nur, wenn sie juenger war als die letzte EIGENE
 *     Eingabe — derselbe Geraete-Uhrenvergleich, der beim Lochzeiger fuenf
 *     Fassungen gekostet hat, nur am Rundenende. Jeder Tipp nach dem Beenden
 *     (frueher auch jeder automatisch erfasste Schlag) liess die Marke
 *     veralten; dazu raeumte der Gehorsam nicht auf: `screen = "home"` ohne
 *     `svcStop` — der Dienst funkte weiter und belebte den beendeten Entwurf
 *     neu. Beides zusammen war das „Uhr uebersteuert das Beenden".
 *     JETZT: Marke gilt bei gleichem Platz, sobald sie juenger ist als der
 *     RUNDENBEGINN (Schutz vor Alt-Marken aus (9) bleibt), egal was hier
 *     zuletzt getippt wurde — und der Gehorsam raeumt vollstaendig auf, wie
 *     beim Verwerfen: clearLocal, svcStop, Eintraege, rec, resume, Spur-Zeile
 *     "Runde <= Handy beendet". Das Verwerfen folgt derselben Regel
 *     (Vergleich gegen Rundenbeginn statt letzte Eingabe).
 *     SCHLAG-AUTOMATIK (object Swing) AUSGEBAUT, auf Wunsch: kein
 *     `Swing.start` mehr, kein Schalter "Schlaege erkennen" im Menue. Der
 *     Schwung-Chip (`recSwing`) bleibt — Teilschlaege ("¾", "Halb", ...)
 *     lassen sich weiter von Hand taggen; die automatische ¾-Einstufung
 *     ueber die Drehrate entfaellt mit der Automatik. `Swing`,
 *     `recBeginAuto`, `recStopAuto`, `autoShot`-Leitungen bleiben als toter
 *     Code stehen; Abbau gesondert, nicht in derselben Fassung wie eine
 *     Verhaltensaenderung.
 *
 *  2026-08-25 (34) · ZAEHLER STATT UHRZEIT — DIE KLASSE, NICHT DIE GESTALT.
 *     (4), (19), (29), (32), (33): fuenfmal derselbe Fehler in neuer Gestalt —
 *     ein Abgleich in beide Richtungen, entschieden ueber ZEITSTEMPEL zweier
 *     Geraete, dazwischen Echos, Drosselung, CDN. Jede Reparatur schloss ein
 *     Rennen, das naechste trat hervor. Diese Fassung beseitigt die KLASSE:
 *     DER LOCHWAHL-ZAEHLER `holeSeq`. Nur eine BENUTZERHANDLUNG erhoeht ihn —
 *     auf der Uhr wie in der PWA (v4.79: playPrev/Next; Uebernahmen und der
 *     Automatik-Sprung beim Fortsetzen NICHT). Es gewinnt die hoehere Nummer.
 *     Ein Echo traegt nie eine hoehere Nummer als die, die es gesehen hat —
 *     wirkungslos, egal wie spaet oder wie frisch gestempelt. Drosselung,
 *     Zeitversatz und CDN-Latenz sind damit KEINE Fehlerquellen mehr.
 *     Das ist das Prinzip, das hier laengst funktioniert: Die Eingabespur
 *     (23) meldet mit laufenden Nummern seit Tagen "keine Luecke" — nur der
 *     Lochzeiger verglich noch Uhren.
 *     UMSETZUNG: `ownHoleSeq` in Net; `holeGewechselt` zaehlt hoch (Blaettern
 *     UND Eingabe — beides ist ein Bekenntnis zum Loch, (19)); fremde Nummern
 *     heben den Stand nur an (`holeSeqGesehen` — Uebernahme ist keine
 *     Handlung). Der Zeiger traegt `holeSeq` in beiden Push-Zweigen. Die EINE
 *     Regel `fremderZeigerZaehlt` entscheidet an allen drei Stellen: Zaehler
 *     vor wahlAt (v4.78-Netz) vor at (Alt-Netz). Strikt groesser — bei
 *     Gleichstand bleibt das eigene Loch, die naechste Handlung loest auf.
 *     Nach Neustart/Fortsetzen uebernimmt die Uhr den Stand aus dem Entwurf.
 *     ERFORDERT PWA v4.79 fuer den Zaehler; mit aelterer PWA greifen die
 *     Netze aus (33) bzw. (4) unveraendert.
 *     NACHTRAG BEIM BAU ENTDECKT: `WATCH_APP` stand seit (31) still —
 *     (32) und (33) liefen mit falscher Kennung. Der Beweis, dass sie
 *     ueberhaupt liefen, kam nur zufaellig aus der Spur ("Loch <= Handy").
 *     Genau dafuer ist Ritual-Punkt 1 da; er gilt auch fuer mich.
 *     ------------------------------------------------------------------
 *     PRUEFRITUAL (vor jedem Urteil "geht/geht nicht" — 2 Minuten):
 *     1. FASSUNGEN: PWA-Startseite zeigt 4.79.0 (Pages-CDN cached minutenlang
 *        — URL mit frischem ?v= laden); Uhr-Puls traegt die Fassung im Zeiger.
 *     2. SKRIPT (60 s): Uhr auf Loch 1. Auf der UHR 1->2->3 blaettern
 *        (~2 s Abstand). 10 s warten -> Handy MUSS Loch 3 zeigen. Am HANDY
 *        auf 4 blaettern. 10 s warten -> Uhr MUSS Loch 4 zeigen. Auf der Uhr
 *        Score fuer Loch 4 eintragen.
 *     3. PROTOKOLL exportieren, drei Punkte:
 *        (a) Spur: "Loch -> 2", "Loch -> 3", genau EIN "Loch <= Handy 4",
 *            danach "Eingabe L4" — nichts davon auf einem anderen Loch.
 *        (b) KEIN "Loch <= Handy n" mit einem n, das niemand gewaehlt hat.
 *        (c) Puls: "Eingaben bis #N, Handy sah #N (OK)".
 *     4. Versagt (a): Uhr-Seite (Handler/Spur). Versagt (b): Zeiger-Regel — die
 *        "verworfen"-Zeile nennt seq/eigen, wahl und at zum Nachrechnen.
 *        Versagt (c): Transport. EIN Durchgang, EIN eindeutiges Urteil.
 *
 *  2026-08-25 (33) · (32) HAT DEN RUECKSPRUNG SICHTBAR GEMACHT — UND NICHT
 *     BEENDET. HANDLUNG GEGEN HANDLUNG STATT GEGEN SCHREIBZEIT.
 *     IM PROTOKOLL VOM 25.08., 20:31, dank der neuen Spur-Eintraege aus (32):
 *       „#18 Loch ⇐ Handy 2 · #19 Loch ⇐ Handy 3 · #20 Loch ⇐ Handy 14"
 *     #18/#19 sind VERSPAETETE ECHOS der eigenen Schritte: Der Handy-Tab war
 *     gedrosselt (Bilanz: Median 47 s), uebernahm „Loch 2" erst eine Minute
 *     nach dem Blaettern — und stempelte die Uebernahme FRISCH. Ein frischer
 *     Stempel auf altem Inhalt schlaegt jede at-Regel. #20 war ein
 *     AUTOMATISCHER Sprung des Handys (Fortsetzen -> „erstes Loch ohne
 *     Score", Restdaten), von niemandem gewaehlt, ebenfalls frisch gestempelt.
 *     (32) hat also genau das getan, was Diagnose kann: den unsichtbaren
 *     Schritt benennen. Beendet hat es ihn nicht, weil die VERGLEICHSGROESSE
 *     falsch war — ein Zeitstempel sagt, WANN geschrieben wurde, nicht, wie
 *     alt die INFORMATION ist. Zwischen „neu erfahren" und „spaet
 *     weitergesagt" kann keine Schreibzeit unterscheiden.
 *     DIE REGEL VERGLEICHT JETZT HANDLUNG MIT HANDLUNG: Die PWA schreibt ihre
 *     letzte EIGENE Lochwahl (`PLAY.holeAt` — im Puls laengst als „eigene
 *     Wahl" sichtbar) als `wahlAt` in den Zeiger (v4.78). Die Uhr uebernimmt
 *     nur, wenn `wahlAt > ownHoleAt` — der Handy-BENUTZER hat NACH dem
 *     Uhr-Benutzer gehandelt. Echos, Uebernahmen und Automatik tragen keine
 *     oder eine alte Wahl und sind damit wirkungslos, egal wie frisch ihr
 *     Schreibstempel ist.
 *     EINE REGEL, DREI STELLEN: `Net.fremderZeigerZaehlt` gilt fuer beide
 *     Push-Filter und den Pull — (32) scheiterte auch daran, dass dieselbe
 *     Entscheidung dreimal einzeln formuliert war.
 *     OHNE `wahlAt` (aeltere PWA) bleibt die bisherige at-Regel als Netz —
 *     dasselbe Muster wie beim X-Repo-Sha-Kopf: neuer Weg, altes Netz.
 *     ERFORDERT PWA v4.78 fuer die neue Regel; mit aelterer PWA laeuft alles
 *     wie bisher (einschliesslich des Fehlers — er wohnt im Zusammenspiel).
 *     ZUM MITNEHMEN: Wer Zeitstempel vergleicht, vergleicht Uhren. Wer wissen
 *     will, wessen Wille juenger ist, muss den WILLEN stempeln.
 *
 *  2026-08-25 (32) · GEFUNDEN: DER LESE-TAKT HOLTE DAS VORIGE LOCH ZURUECK.
 *     DIE FRAGE DER UEBERGABE — „welches idx veraendern die Pfeile?" — ist
 *     beantwortbar, ohne die App zu starten: ALLE drei Rueckrufe (onHoleDelta,
 *     onPrev, onNext) sind an der AUFRUFSTELLE von `PlayPager` definiert, und
 *     dort gibt es genau EIN `idx` — den Zustand. Die Parameter in `PlayPager`
 *     und `ScorePage` sind `val`; eine Zuweisung dort UEBERSETZT NICHT — das
 *     hat der Compiler am 24.08. (3) selbst bewiesen. Verdacht 1 entfaellt.
 *     DIE URSACHE SITZT EINE EBENE TIEFER, in der Lese-Schleife:
 *     Der PUSH-Weg prueft seit (4) `at > ownLiveAt && at > ownHoleAt`. Der
 *     PULL-Weg pruefte NUR `at > ownLiveAt` — den letzten PUSH, nicht die
 *     letzte HANDLUNG. Zwei Stellen, eine Regel, nur einmal eingebaut.
 *     WARUM DAS ERST JETZT TRAEGT: Uebernimmt das Handy ein Loch der Uhr,
 *     stempelt es seinen Zeiger FRISCH („Uebernahme = neue Aussage"). Dieser
 *     Stempel schlaegt `ownLiveAt`, bis die Uhr das naechste Mal sendet. Wer in
 *     diesem Fenster weiterblaettert — Entprellung 600 ms plus Netz —, dem
 *     stellt der naechste Lesevorgang das VORIGE Loch zurueck. Mit dem
 *     2-s-Lesetakt aus (5a)/v4.77-Zeit trifft das Fenster fast jeden Wechsel:
 *     Genau seither „geht Handy -> Uhr, aber Uhr -> Handy nicht".
 *     DAS ERKLAERT AUCH „EINGABE L1": Nach dem stillen Ruecksprung schreibt
 *     jede Eingabe auf das zurueckgeholte Loch. Und es erklaert, warum die
 *     Spur nichts zeigte, WO man es suchte: Der Ruecksprung selbst hatte
 *     KEINEN Spur-Eintrag — er war das einzige unsichtbare Glied der Kette.
 *     DREI AENDERUNGEN: (1) `lastOwnHoleAt()` als Zugriff, (2) der Pull-Weg
 *     prueft dieselbe Bedingung wie der Push-Weg, (3) JEDE Lochwahl von aussen
 *     — Pull wie Push — schreibt „Loch <= Handy n" in die Spur. Ein Zustand,
 *     der von zwei Seiten geschrieben wird, braucht auf BEIDEN Seiten dieselbe
 *     Regel und dieselbe Sichtbarkeit.
 *     UNSCHAEDLICH FUERS ECHO: Die Uebernahme des Handys traegt dasselbe Loch,
 *     auf dem die Uhr steht (t == idx) — sie aendert nichts. Ein ECHTES
 *     Blaettern am Handy ist juenger als jede Uhr-Handlung und kommt durch.
 *     OFFEN: Beide Marken vergleichen Uhr-Zeit mit Handy-Zeit; ab ~30 s
 *     Versatz kippt jeder solche Vergleich (siehe `versatzAus`). Das galt fuer
 *     den Push-Weg schon vorher und ist hier nicht schlechter geworden.
 *
 *  2026-08-24 (5a) · ABGLEICH BESCHLEUNIGT — dort, wo es nichts kostet.
 *     [KENNUNG KORRIGIERT 2026-08-25 (22): stand als „(5)" doppelt. Der zweite
 *      (5)-Eintrag weiter unten ist der spaetere; dieser hier bekommt „5a", weil
 *      Umnummerieren aeltere Verweise brechen wuerde.]
 *     GEMESSEN: Uhr -> Handy bis zu 6,5 s (1,5 s Entprellung hier + bis zu
 *     5 s Takt drueben), Handy -> Uhr bis zu 6,5 s (2 s Entprellung drueben +
 *     5 s Takt hier). Zusammen fuehlt sich das an wie „reagiert nicht": Man
 *     blaettert, schaut aufs andere Geraet und sieht noch das alte Loch.
 *     · Entprellung `scheduleSync` 1500 -> 600 ms. Sie fasst schnelle Taps
 *       zusammen, das bleibt richtig — aber ein Lochwechsel ist kein schneller
 *       Tap: Man drueckt einmal und schaut dann hin. 600 ms fangen ein
 *       versehentliches Doppeltippen weiterhin ab.
 *     · Entwurf lesen: 5 s -> 2 s, SOLANGE DER ARM OBEN IST. Im Ambientmodus
 *       bleiben 30 s. Der Akku merkt den Unterschied dort, wo die Uhr die
 *       meiste Zeit ist, und das ist der Arm unten.
 *     NICHT SCHNELLER: Der Push des Handys ist 2 s entprellt. Wer haeufiger
 *     abfragt als geschrieben wird, erzeugt nur Verkehr — und im unguenstigen
 *     Fall genau die Konflikte, die beide Seiten danach aussitzen muessen.
 *     ERWARTUNG: Lochwechsel kommt in rund 1–3 s an statt in 5–7 s.
 *
 *  2026-08-25 (31) · DER RUECKFALL SCHRIEB STILL AUF LOCH 1.
 *     GEMELDET: „Er erfasst immer nur die Eingaben bei Loch 1."
 *     IN DER SPUR STEHT ES SCHWARZ AUF WEISS: „Eingabe L1" in jeder Zeile,
 *     obwohl zwischendurch „Loch → 2" und „Loch → 3" protokolliert wurde. Die
 *     Anzeige blaetterte, die Eintraege blieben.
 *     URSACHE: `val hd = cs.holes.getOrNull(idx) ?: cs.holes.firstOrNull()`.
 *     Zeigt `idx` neben die Lochliste, faellt der Ausdruck auf LOCH 1 zurueck —
 *     und zwar LAUTLOS. Genau dieses `hd` bestimmt, auf welches Loch `change()`
 *     schreibt.
 *     EIN RUECKFALL DARF EINE ANZEIGE RETTEN. Er darf niemals still
 *     entscheiden, WOHIN Daten geschrieben werden — das ist kein Notbehelf,
 *     das ist eine falsche Zuordnung.
 *     Jetzt wird er gemeldet, mit beiden Zahlen („idx 4 neben 18 Loechern —
 *     Eingaben landen auf Loch 1"). WARUM `idx` danebenzeigt, ist die naechste
 *     Frage — sie war ohne diese Zeile nicht einmal zu stellen.
 *
 *  2026-08-25 (30) · DIE DIAGNOSE HAT GELOGEN — GLAUBWUERDIG.
 *     Im Puls stand „Loch 4/18 · … · eigenes Loch 1", und ich habe daraus
 *     geschlossen, die Uhr sende das falsche Loch. DAS WAR FALSCH.
 *     `Diagnose.pulsLoch` wurde INNERHALB von
 *     `if (prevLiveK != null && src != "watch")` gesetzt — also nur dann, wenn
 *     im Entwurf gerade der Zeiger des HANDYS lag. Das ist der seltene Fall;
 *     die Uhr sendet um ein Vielfaches oefter. In allen anderen Durchlaeufen
 *     behielt `pulsLoch` seinen ALTEN Wert: Loch 1, vom allerersten Push.
 *     GESENDET WURDE DIE GANZE ZEIT DAS RICHTIGE. Angezeigt wurde ein Wert von
 *     vorhin. Die Reparatur in (29) — den Zeiger spaet lesen — bleibt richtig
 *     und war trotzdem eine Antwort auf eine Frage, die es nicht gab.
 *     ZUM MITNEHMEN: EINE DIAGNOSE, DIE NUR MANCHMAL AKTUALISIERT WIRD, LUEGT
 *     IN ALLEN UEBRIGEN FAELLEN — und zwar glaubwuerdig, weil das Format
 *     stimmt. Ich habe ihr zwei Fassungen lang geglaubt.
 *     Jeder Wert, der eine Lage beschreibt, gehoert dorthin, wo er BEI JEDEM
 *     DURCHLAUF gesetzt wird.
 *
 *  2026-08-25 (29) · GEFUNDEN: DER LOCHZEIGER WAR ZU ALT.
 *     IM PULS VOM 25.08., 17:43:14, STAND DER WIDERSPRUCH IN EINER ZEILE:
 *         „Loch 5/18 · 49 Vorgaenge, 3 misslungen · HTTP 200 · eigenes Loch 1"
 *     Der Kontext wusste also Loch 5, gesendet wurde Loch 1 — und die Liste der
 *     Abgleiche zeigte lueckenlose HTTP 200 im Sekundentakt. DIE UHR SENDET
 *     EINWANDFREI. SIE SENDET NUR DAS FALSCHE.
 *     URSACHE: `snapHole` wurde am ANFANG von `syncNow` gelesen — vor der
 *     Entprellung (600 ms), vor dem Netzaufbau und vor bis zu vier
 *     Wiederholungen bei 409. Zwischen Aufnahme und Absenden liegen so leicht
 *     Sekunden. Wer in dieser Zeit weiterblaettert, sendet ein Loch, auf dem er
 *     nicht mehr steht — und bei einer REIHE schneller Eingaben, genau dem
 *     gemeldeten Fall, verschiebt sich JEDER Zeiger um einen Schritt nach
 *     hinten. Das Handy folgte deshalb immer dem vorletzten Stand und wirkte,
 *     als folge es gar nicht.
 *     Der Zeiger wird jetzt unmittelbar vor dem Absenden gelesen. Die uebrigen
 *     Aufnahmen bleiben, wo sie sind: Bei ihnen ist der fruehe Stand richtig,
 *     denn sie beschreiben, WAS eingegeben wurde — der Zeiger dagegen
 *     beschreibt, WO man JETZT steht. Zwei verschiedene Fragen, zwei
 *     verschiedene Zeitpunkte.
 *     ZUM MITNEHMEN: Eine Momentaufnahme ist nur so gut wie der Abstand zu
 *     ihrer Verwendung.
 *
 *  2026-08-25 (28) · DER SENDE-AUFTRAG HING AN DER ANZEIGE.
 *     GEMESSEN am 25.08. mit der Bilanz aus App v4.72:
 *         Waehrend man auf die Uhr schaut:  4–5 s bis zum Handy.
 *         Arm herunter:                     119–208 s (Median 168).
 *     Dazu „keine Luecke" — es geht NICHTS verloren, es kommt nur zu spaet.
 *     URSACHE: `scheduleSync()` startete seinen Auftrag auf
 *     `rememberCoroutineScope()`. DIESER BEREICH GEHOERT DER KOMPOSITION.
 *     Verlaesst der Bildschirm die Anzeige, wird er ABGEBROCHEN — samt dem
 *     Sende-Auftrag und seiner 600-ms-Entprellung. Erst der Herzschlag holt es
 *     nach, und daher die Minuten.
 *     (25) war richtig und reichte nicht: Der WakeLock haelt den PROZESS am
 *     Leben (28 Minuten wurden zu drei), aber der AUFTRAG starb weiterhin, weil
 *     er an der ANZEIGE hing und nicht am Prozess. Zwei verschiedene
 *     Lebensdauern, die ich fuer dieselbe gehalten habe.
 *     `syncScope`: eigener Bereich mit `SupervisorJob`, ueberlebt jeden
 *     Bildschirmwechsel. `SupervisorJob`, damit ein gescheiterter Vorgang nicht
 *     die folgenden mitreisst.
 *     ZUM MITNEHMEN: Ein Auftrag, der etwas SENDEN soll, gehoert an die
 *     Lebensdauer des Prozesses — nicht an die eines Bildes.
 *
 *  2026-08-25 (27) · DIE SCHLEIFE MISST SICH SELBST.
 *     Der Verdacht aus (25) — Android friert den Prozess ein, wenn der
 *     Bildschirm ausgeht — liess sich bisher nur aus ANKUNFTSZEITEN
 *     erschliessen. Das ist zweideutig: „spaet angekommen" kann auch Netz sein.
 *     JETZT WIRD ES DIREKT GEMESSEN: Wir wissen, wie lange ein Durchlauf
 *     schlafen SOLL (10 oder 60 s). Dauert er laenger als das Doppelte, hat
 *     jemand anders die Schleife angehalten — und das kann nur der Prozess
 *     sein. Gemeldet mit Zahlen („Schleife stand 340 s statt 10 s"), gezaehlt
 *     im Puls („Schleife stand 3×").
 *     DAS UNTERSCHEIDET „NETZ WAR WEG" VON „PROZESS WAR EINGEFROREN" — genau
 *     diese Unterscheidung hat mir zwei Tage gefehlt, und ohne sie habe ich
 *     zwoelf Fassungen an der falschen Stelle repariert.
 *     Erst ab dem Doppelten: Ein Durchlauf, der 200 ms zu spaet kommt, ist
 *     normal und keine Meldung wert.
 *
 *  2026-08-25 (26) · DIE SPUR NENNT JETZT DAS FELD.
 *     Im Protokoll stand dreimal „Eingabe L3 score=–". Daraus war NICHT zu
 *     entscheiden, ob dort ein anderes Feld gefuellt wurde (Putts, Lage,
 *     Abschlag) oder ob ein Score verlorenging — genau diese Zweideutigkeit
 *     sollte die Spur beseitigen.
 *     Verglichen wird jetzt der Eintrag VOR und NACH der Aenderung: Die
 *     Datenklasse gibt ihre Felder in `toString()` preis, und was verschieden
 *     ist, wird benannt. OHNE EINE LISTE VON FELDNAMEN — eine solche Liste
 *     veraltet beim naechsten neuen Feld, und zwar lautlos.
 *     Faellt der Vergleich aus, bleibt es beim alten „score=…"; eine Diagnose
 *     darf nie das sein, woran eine Eingabe scheitert.
 *
 *  2026-08-25 (25) · GEFUNDEN: DER DIENST STARTETE OFT GAR NICHT.
 *     GEMESSEN mit der Eingabespur aus (23) — zum ersten Mal mit Zahlen statt
 *     Vermutung: Die Aktionen #1–#10 entstanden um 14:34:55 bis 14:35:45 und
 *     kamen um 15:03:11 beim Handy an. 28 MINUTEN spaeter, alle auf einmal.
 *     Die Uhr zeichnet also alles auf und SENDET NICHT: Sobald man wegschaut,
 *     steht der Takt; beim Aufwachen laeuft alles in einem Schwung raus. Das
 *     ist ein anderer Fehler als alle, die ich zwei Tage lang gesucht habe —
 *     es geht nichts verloren, es kommt zu spaet.
 *     URSACHE: `svcStart` lief nur `if (!Live.running)`. Diese Bedingung
 *     verwechselt ZWEI Dinge — „laeuft die Ortung" und „laeuft der Dienst".
 *     Der Dienst haelt aber nicht nur GPS, sondern den PARTIAL_WAKE_LOCK, und
 *     der haelt den PROZESS am Leben. Ohne ihn friert Android die App ein,
 *     sobald der Bildschirm ausgeht — samt der `LaunchedEffect`-Schleife, die
 *     den Abgleich sendet.
 *     BESONDERS TUECKISCH BEI GPS-QUELLE „HANDY": Dann laeuft drueben die
 *     Ortung, `Live.running` ist gesetzt, und der Dienst startet NIE.
 *     Jetzt immer, solange gespielt wird. `svcStart` ist mehrfach aufrufbar:
 *     `startForeground` aktualisiert nur die Meldung, der Lock wird nur einmal
 *     geholt (`wake == null`).
 *     ZUM MITNEHMEN: Ein Dienst, der den PROZESS am Leben haelt, darf nicht an
 *     einer Bedingung haengen, die von etwas ANDEREM handelt.
 *
 *  2026-08-25 (24) · UEBERSETZUNGSFEHLER AUS (23): FALSCHER NAME.
 *     `prevLive?.optInt("seenAktion", -1)` — der Zeiger heisst in DIESEM Zweig
 *     aber `prevLiveK`; `prevLive` gibt es nur im Voll-Push weiter unten.
 *     Der Compiler meldete „Unresolved reference" und drei Folgefehler.
 *     DAS IST DER DRITTE NAMENSFEHLER IN DREI TAGEN (nach `isoVon`/`roundStart`
 *     und `optJSONArray` auf `RepoDraft`). Alle drei haetten hier auffallen
 *     koennen — die Klammernbilanz stimmte jedes Mal, Namensaufloesung prueft
 *     sie nicht. Bei Kotlin-Aenderungen ist der Compiler die Pruefung, alles
 *     davor ist Vorarbeit.
 *
 *  2026-08-25 (23) · DIE EINGABESPUR.
 *     GEMELDET: „Von Loch zu Loch gewechselt, jedes Mal Score 6 — weder
 *     Lochwechsel noch Scores kamen an."
 *     BIS HIERHER KONNTE ICH NUR ZUSTAENDE VERGLEICHEN: Die Uhr steht auf 9,
 *     das Handy auch. Das sagt aber NICHTS darueber, ob die sechs Schritte
 *     dazwischen angekommen sind — ein Endstand kann auch zufaellig
 *     uebereinstimmen. Genau daran bin ich zehn Fassungen lang gescheitert.
 *     JEDE HANDLUNG BEKOMMT EINE LAUFENDE NUMMER: Lochwechsel (beide Pfeilpaare)
 *     und jede Score-Eingabe, mit Uhrzeit und Inhalt
 *     („#7 13:42:11 Loch → 3", „#8 13:42:19 Eingabe L3 score=6").
 *     Die letzten zwanzig reisen mit dem Entwurf. Das Handy schreibt
 *     `seenAktion` in seinen Zeiger zurueck, und der Puls zeigt beides:
 *     „Eingaben bis #12, Handy sah #8 ⚠ 4 offen".
 *     DAMIT IST DIE LUECKE AUF DEN SCHRITT GENAU SICHTBAR statt „irgendwas
 *     kommt nicht an". Zwanzig Eintraege sind rund 1 kB — der Entwurf geht
 *     ohnehin im Minutentakt raus.
 *
 *  2026-08-25 (22) · CHANGELOG-REGELN · FASSUNGSNOTIZ REIST MIT.
 *     (A) DIE UHR HATTE DIE DOKU, ABER NICHT DEN PRUEFSTAND. 1993 Zeilen Kopf,
 *         99 Changelog-Eintraege — inhaltlich derselbe Standard wie in der
 *         index.html. Was fehlte, war die Kontrolle darueber. Prompt fanden
 *         sich beim ersten Blick VIER doppelte Kennungen
 *         (24.08. (5), 14.08. (2) dreifach, 09.08. (11)) und drei Eintraege
 *         ausserhalb der Reihenfolge. In der index.html waere das seit v4.21.1
 *         sofort aufgefallen.
 *         KENNUNGEN BEREINIGT, POSITIONEN NICHT: „(5a)", „(2b)", „(2c)",
 *         „(1b)", „(11b)". Umnummerieren oder Verschieben wuerde Verweise
 *         brechen — und Verweise wie „siehe (9)/(10) vom 15.08." sind unser
 *         Gedaechtnis. Jede Abweichung traegt jetzt eine Notiz an Ort und
 *         Stelle.
 *         VIER REGELN im Pruefstand, dieselben wie drueben: keine Kennung
 *         doppelt, Eintrag fuer die laufende Fassung, Abweichungen
 *         angeschrieben, keine Funktion beschrieben, die es nicht gibt.
 *         Kostet kein Byte auf der Uhr.
 *     (B) `WATCH_NOTE` — ein Absatz, der mit jedem Zeiger und jedem Protokoll
 *         mitreist. Die Kennung sagt WELCHE Fassung laeuft, die Notiz WAS sie
 *         geaendert hat; das war diese Woche mehrfach die zweite Frage.
 *         VON HAND GEPFLEGT, ausdruecklich: Automatisch aus dem Kopf zu
 *         schneiden waere verlockend und falsch — ein Programm, das seine
 *         eigenen Kommentare liest, bricht beim naechsten Umbau lautlos.
 *
 *  2026-08-25 (21) · MEIN RIEGEL AUS (14) HAT AUSGESPERRT.
 *     GEMELDET: „Aus der laufenden Runde geflogen und konnte mich nicht mehr
 *     verbinden." Auf dem Schirm: „Nordplatz · NOCH KEINE LOECHER" und eine
 *     Suche, die nie endet. Im Protokoll: „laeuft bereits — Uebernahme
 *     uebersprungen".
 *     URSACHE ist der Riegel aus (14). Er verhindert, dass dieselbe Runde
 *     zweimal uebernommen wird — richtig, denn die Uebernahme LOESCHT Eingaben.
 *     Er prueft aber nur, ob ein Platz mit passendem NAMEN geladen ist. Nach
 *     einem Neustart ist genau das der Fall: Der Platz ist da, seine Lochliste
 *     aber LEER. Der Riegel sprang an, die Uebernahme unterblieb — und man sass
 *     auf dem Startbildschirm fest.
 *     DREI BEDINGUNGEN STATT EINER: derselbe Platz, er hat LOECHER, und man ist
 *     im Spielbildschirm. Fehlt eines, laeuft nicht dieselbe Runde, sondern
 *     eine halbe — und die gehoert vervollstaendigt.
 *     Die Uebersprungen-Meldung nennt jetzt Lochzahl und Bildschirm; ohne diese
 *     zwei Angaben war ihr nicht anzusehen, dass sie das Problem WAR.
 *     ZUM MITNEHMEN: Ein Riegel gegen Datenverlust darf nie die einzige Tuer
 *     versperren. Er braucht immer die Frage „und was, wenn der Zustand kaputt
 *     ist?" — sonst schuetzt er Daten, die man nicht mehr erreicht.
 *
 *  2026-08-25 (20) · FASSUNGSNUMMER OBEN · BEENDEN-KNOPF UNTEN.
 *     (A) Die Fassung stand nur im Protokoll — man musste blaettern und suchen,
 *         um zu wissen, welche laeuft. Diese Woche war das mehrfach die erste
 *         Frage, und jedes Mal hat sie einen Umlauf gekostet. Jetzt ganz oben,
 *         ganz klein und gedaempft: eine Auskunft, keine Ansage. Auf einem
 *         runden Display darf sie nichts von dem verdecken, wofuer man den
 *         Bildschirm oeffnet — den Zustand der Runde direkt darunter.
 *     (B) BEENDEN. Bisher kam man nur ueber den Wisch nach rechts oder die
 *         Seitentaste heraus, und beides beendet die App NICHT: Es schiebt sie
 *         in den Hintergrund, wo GPS und Abgleich weiterlaufen. Auf einer Uhr
 *         ist das der Unterschied zwischen einer und drei Stunden Akku.
 *         ERST `svcStop`, DANN `finish()` — ohne das laeuft der
 *         Vordergrunddienst weiter und haelt die Ortung am Leben. Die App ist
 *         zu, das GPS nicht; genau das ist am 15.08. schon einmal passiert.
 *         GANZ UNTEN und ohne Farbe: die seltenste Handlung auf diesem
 *         Bildschirm. Ein auffaelliger Knopf hier wuerde versehentlich
 *         getroffen — wer beenden will, scrollt.
 *         `ctx` und `activity` gibt es in `HomeScreen` nicht; deshalb ein
 *         Rueckruf, wie beim Lochwechsel gelernt (2026-08-24 (3)).
 *
 *  2026-08-25 (19) · EINE HALBE REGEL IST KEINE.
 *     GEMELDET: „Von Loch 1 bis 6 durchgeblaettert, jeweils Score 6 — nur der
 *     erste kam an, und das Handy folgte dem Lochwechsel nicht."
 *     IM PULS: Kontext „Loch 2/18", gesendet aber „eigenes Loch 1". Das
 *     Blaettern hielt also nicht einmal auf der Uhr selbst.
 *     URSACHE: `ownHoleAt` — die Marke, die verhindert, dass das Handy den
 *     Lochzeiger ueberstimmt — wurde NUR von den Pfeilen auf Seite 1 gesetzt
 *     (2026-08-24 (4)). Wer auf der SCORE-Seite blaettert (`onPrev`/`onNext`)
 *     oder einfach einen Score eintraegt, hinterliess keine Marke. Der naechste
 *     Abgleich sah einen Handy-Zeiger, der juenger war als die LEERE Marke, und
 *     holte das Loch des Handys zurueck. Sekunden spaeter stand man wieder auf
 *     Loch 1 — und alle weiteren Scores landeten dort. Deshalb kam genau einer
 *     an.
 *     JETZT STEMPELT JEDE Stelle, an der der Benutzer sich zu einem Loch
 *     bekennt: beide Pfeilpaare und `change()` (jede Score-Eingabe). Bei der
 *     Eingabe heisst `holeGewechselt` nicht „gewechselt", sondern „der Benutzer
 *     ist HIER" — dieselbe Aussage, derselbe Schutz.
 *     ZUM MITNEHMEN: Eine Regel, die nur an EINER von vier Stellen gilt, ist
 *     keine Regel. Sie sieht im Code aus wie ein Schutz und ist im Feld ein
 *     Zufall.
 *
 *  2026-08-25 (18) · GEFUNDEN: EIN LEERER LOCHZEIGER LEGT ALLES LAHM.
 *     IM PULS STAND: „1 Vorgänge, 0 misslungen · HTTP 200 · eigenes Loch ? ·
 *     Handy-Loch —". Das FRAGEZEICHEN ist die Antwort: `snapHole` war NULL.
 *     Und weil der ganze Live-Block in `pushDraft` hinter
 *     `if (currentHole != null)` steht, wurde er uebersprungen — kein
 *     Lochzeiger, kein Vergleich mit dem Handy, nichts. Der Push lief und
 *     meldete HTTP 200, trug aber nichts bei. Von aussen sieht das aus wie
 *     „die Uhr sendet nicht" — und genau das war die Meldung, sechs Fassungen
 *     lang.
 *     WIE ES DAZU KOMMT: `idx` zeigt auf einen Platz in der Lochliste. Liegt er
 *     ausserhalb — im Protokoll „18 Loch · 5 Löcher · Loch 13" —, liefert
 *     `getOrNull` null, und zwar STILL.
 *     DREI SACHEN: `idx` wird in die Liste zurueckgeholt statt aufzugeben, der
 *     Fall wird gemeldet, und der Puls nennt jetzt, OB der Zeiger gesetzt
 *     wurde („Zeiger gesetzt/uebersprungen"). „HTTP 200" allein hat uns in die
 *     Irre gefuehrt: Der Vorgang gelingt, die Wirkung bleibt aus.
 *     ZUM MITNEHMEN: Ein Zustand, der alles Weitere lahmlegt, darf nicht
 *     lautlos sein. Und eine Erfolgsmeldung fuer den TRANSPORT sagt nichts
 *     ueber den INHALT.
 *
 *  2026-08-25 (17) · FRONT UND BACK AUCH IM STANDBY · „SPIELT WIE" NACH OBEN.
 *     (A) STANDBY (Ambient) zeigte nur die Mitte. Genau dort schaut man aber
 *         im Gehen hin, um die Lage abzuschaetzen — und dafuer sind Front und
 *         Back so wichtig wie die Mitte, weil die Fahne selten mittig steht.
 *         Jetzt dieselbe Ordnung wie auf Seite 1 (drei Zahlen, Mitte groesser),
 *         damit man nicht zweimal lesen lernen muss. Bewusst SCHMALER (34/20
 *         statt 40): Das Display ist im Ambient dunkelgesteuert, jedes Pixel
 *         weniger spart Strom.
 *     (B) „SPIELT WIE" steht jetzt direkt unter den drei Zahlen — vorher weiter
 *         unten in der Caddy-Zeile, wo man es erst nach dem Scrollen liest.
 *         Es ist aber die Zahl, nach der man den Schlaeger zieht: Bei
 *         Gegenwind und bergauf sind 150 m eben 165.
 *         NUR BEI ABWEICHUNG ab 2 m — „spielt wie 150" neben „150" ist auf
 *         einem runden Display verschenkter Platz.
 *
 *  2026-08-25 (16) · DER PULS ZAEHLT JETZT.
 *     Das Protokoll vom 25.08. zeigte zwei Pulszeilen im Abstand von zwei
 *     Minuten — beide HTTP 200, beide mit uebereinstimmenden Loechern
 *     (eigenes 3 / Handy 3). DER ABGLEICH LAEUFT ALSO. Was NICHT zu sehen war:
 *     ob dazwischen zehn Vorgaenge liefen oder keiner, denn der Puls ersetzt
 *     sich selbst und nennt nur den letzten.
 *     Zwei Zahlen beenden das: Vorgaenge insgesamt und davon misslungen.
 *     „138 Vorgaenge, 0 misslungen" beweist Kontinuitaet; „3 Vorgaenge" nach
 *     einer Stunde beweist das Gegenteil. Eine Momentaufnahme sagt nichts ueber
 *     einen Verlauf — dafuer braucht es einen Zaehler.
 *
 *  2026-08-25 (15) · DAS PROTOKOLL KAM NUR AM ANFANG — ZWEI URSACHEN, UND
 *     EINE ZEILE, DIE DIE ZWEIDEUTIGKEIT BEENDET.
 *     (A) DER VERSAND HING AN DER AKKU-SCHLEIFE. Die steht hinter
 *         `if (screen != "play" || akkuGewarnt) return`. Zwei Bedingungen, die
 *         beide nichts mit dem Protokoll zu tun haben:
 *         · AUSSERHALB EINER RUNDE lief er GAR NICHT — meine Behauptung in (8),
 *           er laufe „auch ohne Runde", war schlicht falsch. Ich habe sie
 *           aufgeschrieben, ohne nachzusehen, wo die Schleife haengt.
 *         · AB DER ERSTEN AKKUWARNUNG hoerte er dauerhaft auf.
 *         Jetzt eigene `LaunchedEffect(Unit)`-Schleife alle zwei Minuten,
 *         unabhaengig von Bildschirm, Runde und Akku. `logPut` schreibt
 *         weiterhin NUR bei Aenderung — bei fehlerfreiem Betrieb also kein
 *         einziger Vorgang.
 *         MERKSATZ: Ein Weg, der IMMER funktionieren soll, darf nicht an einer
 *         Bedingung haengen, die fuer etwas anderes gedacht ist.
 *     (B) DER PULS. Das Protokoll vom 25.08. endet um 08:40:48, das Handy lief
 *         bis 08:44. Daraus laesst sich NICHT ablesen, ob die Uhr nicht mehr
 *         sendete oder nur nichts mehr zu melden hatte — und genau diese
 *         Zweideutigkeit hat mich diese Woche mehrfach falsch abbiegen lassen.
 *         `Diagnose.pulsSchreiben()` haelt EINE Zeile, die bei jedem
 *         Sendevorgang ERSETZT wird: wann zuletzt gesendet, mit welchem
 *         Ergebnis, auf welchem Loch die Uhr steht, welches Loch das Handy
 *         meldet (mit dessen Zeitstempel).
 *         Sie waechst nicht und verdraengt nichts. Mit ihr heisst „keine
 *         Meldung" endlich „alles gut" statt „vielleicht tot".
 *
 *  2026-08-25 (14) · DIESELBE RUNDE WURDE IMMER WIEDER UEBERNOMMEN.
 *     GEMELDET: „Bei Loch 1 geht die Eingabe, beim Wechsel auf Loch 2 bricht
 *     alles ab."
 *     IM PROTOKOLL stand „Runde uebernommen · Loch 1" MEHRFACH — 08:06:32,
 *     08:07:34, davor 07:30:16 und 07:32:03 (×2). Die Uhr uebernahm dieselbe
 *     Runde also immer wieder.
 *     UND DIESER ZWEIG IST DESTRUKTIV: Er macht `entries.clear()`,
 *     `measurements.clear()` und setzt `idx` auf das Loch des HANDYS. Wer auf
 *     der Uhr zu Loch 2 wechselt und etwas eintraegt, verliert beim naechsten
 *     Durchlauf beides — Eingabe weg, Loch zurueck auf 1. Von aussen sieht das
 *     aus, als sei der Abgleich tot. Genau das war die Meldung.
 *     DER RIEGEL: Laeuft bereits DIESELBE Runde (gleiche `roundId` oder
 *     gleicher Platz + Seite) und ist ein Platz geladen, wird nicht erneut
 *     uebernommen. Der laufende Abgleich haelt sie ohnehin aktuell — dafuer
 *     ist er da. Eine einmalige Spur im Protokoll haelt fest, dass
 *     uebersprungen wurde; ohne sie sucht man beim naechsten Mal wieder an der
 *     falschen Stelle.
 *     ZUM MITNEHMEN: Ein Zweig, der Daten LOESCHT, gehoert hinter eine
 *     Bedingung, die genau einmal wahr ist. Dieser hier stand hinter einer, die
 *     bei jedem Durchlauf wahr war.
 *
 *  2026-08-25 (13) · DER BERICHT STEHT NEBEN DEM PROTOKOLL, NICHT DARIN.
 *     GEMELDET: Nach dem Knopfdruck kam beim Handy nur „Runde uebernommen" an,
 *     keine Diagnose.
 *     URSACHE IST DER AUFBAU, nicht ein einzelner Fehler: Die Diagnose schrieb
 *     IN den Ringpuffer — und raeumte sich darin selbst auf (12), damit sie ihn
 *     nicht verstopft. Ihre Zustellung hing damit an einem Puffer, den
 *     gleichzeitig Fehler, der Rundenentwurf und das Aufraeumen bewegen. Wer
 *     beides mischt, hat DREI Stellen, an denen ein Bericht verschwinden kann.
 *     Nach vier Fassungen an derselben Sache war klar: Das ist kein Fehler zum
 *     Reparieren, das ist ein Aufbau zum Aendern.
 *     `Diagnose.letzterBericht` ist jetzt eine EIGENE Groesse, die nur der
 *     Knopf setzt und die nichts anderes anfasst. Sie reist als eigenes Feld
 *     `bericht` — auf BEIDEN Wegen (Rundenentwurf und `watchlog.json`) — und
 *     ist damit unabhaengig davon, was im Puffer gerade passiert.
 *     Im Puffer bleibt EINE Zeile als Spur, dass geprueft wurde.
 *     `logPut` vergleicht ausserdem den Berichtszeitpunkt mit: Sonst gilt
 *     „nichts Neues", obwohl gerade ein frischer Bericht entstanden ist — und
 *     genau der soll ja raus.
 *     ZWEI DINGE, ZWEI WEGE. Das ist der ganze Unterschied.
 *
 *  2026-08-25 (12) · DIE DIAGNOSE SENDET SOFORT — UND VERSTOPFT SICH NICHT.
 *     Beides aus dem echten Protokoll gelernt, nicht vermutet.
 *     (A) SOFORT SENDEN. Gemeldet: „Die Ergebnisse kommen erst, wenn ich eine
 *         Runde starte." Genau so war es — geschrieben wurde in den Puffer,
 *         und der reiste nur mit dem Rundenentwurf oder im Fuenf-Minuten-Takt.
 *         Wer auf Diagnose drueckt, will die Antwort JETZT, meistens weil
 *         gerade etwas klemmt. Auf die naechste Runde zu warten ist dann genau
 *         das Falsche. Nach dem Selbsttest laeuft `Net.logPut()`, und das
 *         Ergebnis steht als Zeile dabei („an das Handy gesendet").
 *     (B) DIE DIAGNOSE ERSETZT SICH SELBST. Im Protokoll vom 25.08. waren nach
 *         fuenf Knopfdruecken 35 von 60 Zeilen Diagnose — und prompt meldete
 *         der Selbsttest „Protokoll fast voll". Sie verdraengte also genau das,
 *         wozu sie da ist. `Fehler.entferneTags` raeumt die vorigen Diagnose-
 *         und Selbsttest-Zeilen weg, bevor neue kommen: Es gibt immer GENAU
 *         EINEN Stand. Ein aelterer beschreibt eine Lage, die vorbei ist.
 *         Echte Fehlermeldungen bleiben unangetastet.
 *     WAS DER ERSTE DURCHLAUF SONST ZEIGTE: Worker v2.11 ✓, Zeitversatz -2 s ✓,
 *     Schreibschluessel ✓, keine Konflikte ✓. Der Verdacht auf Zeitdrift ist
 *     damit ausgeraeumt — und das ist auch ein Ergebnis.
 *
 *  2026-08-25 (11) · DIAGNOSE AUF DER UHR.
 *     WARUM: Die letzten fuenf Fehlersuchen liefen gleich ab — Symptom
 *     gemeldet, geraten, danebengelegen, naechste Fassung. Erst als das
 *     Protokoll beim Handy ankam, war die Ursache in zwei Minuten klar.
 *     Die Lehre ist nicht „mehr protokollieren", sondern: EINE ZEILE, DIE DEN
 *     GANZEN ZUSTAND ZEIGT, ist mehr wert als fuenfzig Einzelmeldungen.
 *     `object Diagnose` mit vier Bausteinen:
 *     (A) ZEITVERSATZ gegen die Serverzeit — der wichtigste, und er hat mir
 *         bei den letzten Fehlern gefehlt. Der ganze Abgleich haengt an
 *         Vergleichen wie `at > ownHoleAt`. Geht die Uhr auch nur zwei Minuten
 *         vor, gewinnt sie JEDEN Vergleich — und das sieht exakt aus wie „das
 *         Handy wird ignoriert". Auf einer Uhr ohne Mobilfunk keine Seltenheit.
 *         Gemessen aus dem `Date`-Kopf JEDER Antwort, kostet also keinen
 *         eigenen Abruf. Ab 30 s einmalige Warnung.
 *     (B) `syncVerlauf` — die letzten zwoelf Abgleiche mit Ergebnis. Ein
 *         Muster („409, 409, 409") sieht man nur in der Reihe.
 *     (C) `abzug()` — Fassung, Android, Ort, Versatz, Puffergroesse in EINER
 *         Zeile.
 *     (D) `selbsttest()` — beantwortet genau die Fragen, die diese Woche
 *         mehrfach offen waren: Welche Worker-Fassung? Schickt er die Kennung?
 *         Geht die Uhr richtig? Ist der Schreibschluessel da? Haeufen sich
 *         Konflikte?
 *         JEDE ZEILE NENNT BEFUND UND FOLGE — „Worker v2.8" allein sagt
 *         niemandem etwas, „Worker v2.8 — ab v2.11 reist das Protokoll auch
 *         ohne Runde" schon.
 *     ALLES GEHT INS PROTOKOLL und reist damit zum Handy. Eine Diagnose, die
 *     man nur auf dem runden Display lesen kann, wird nicht gelesen.
 *     Der Block steht OBEN im Protokollbereich, nicht unten: Wer scrollen muss,
 *     liest ihn nicht.
 *
 *  2026-08-25 (10) · ABBRUCH IST KEIN FEHLER — DIE URSACHE AUS DEM PROTOKOLL.
 *     Endlich Daten statt Vermutungen. Im Protokoll vom 25.08. steht 27-mal:
 *         „Sync-Schleife · [main] LeftCompositionCancellationException:
 *          The coroutine scope left the composition"
 *     dasselbe fuer Uhr-Push, Dienst, Akku-Warnung, Karten-Raster — jedes Mal
 *     unmittelbar nach „Runde uebernommen".
 *     WAS PASSIERT: Wechselt der Bildschirm, verlaesst der alte
 *     `LaunchedEffect` die Komposition, und Compose BRICHT seine Coroutine ab.
 *     Der Abbruch kommt als `CancellationException` — und die ist in Kotlin
 *     eine ganz normale `Exception`. Jedes `catch (e: Exception)` fing sie mit.
 *     ZWEI FOLGEN, beide schlimm:
 *     1. DAS PROTOKOLL LIEF VOLL: 27 von 60 Zeilen waren dieser eine Vorgang.
 *        Genau die Zeilen, die man sucht, wurden verdraengt — deshalb stand in
 *        dem Protokoll auch keine einzige der Zeiger-Auskuenfte aus (9).
 *     2. STRUKTURIERTE NEBENLAEUFIGKEIT BRACH: Wer einen Abbruch faengt und
 *        NICHT weiterwirft, sagt dem System „ich mache weiter" — die Schleife
 *        laeuft im Zweifel weiter, waehrend Compose sie fuer beendet haelt.
 *        Zwei Schleifen, die dasselbe schreiben, sind genau der Zustand, in dem
 *        „einmal geht eine Eingabe durch, danach nichts mehr" entsteht.
 *     ZWEI RIEGEL: `Fehler.add` nimmt einen Abbruch gar nicht erst auf (EINE
 *     Stelle, damit keine der 33 Fangstellen ihn einzeln kennen muss), und
 *     alle 19 Schleifen-Fangstellen werfen ihn ausdruecklich WEITER
 *     (`if (e.istAbbruch()) throw e`).
 *     MERKSATZ: `CancellationException` wird NIE protokolliert und IMMER
 *     weitergeworfen. Sie ist kein Fehler, sondern die normale Art, wie eine
 *     Coroutine endet.
 *
 *  2026-08-24 (9) · F · MITTE · B IN EINER REIHE · UND: MESSEN STATT RATEN.
 *     (A) ANZEIGE. Vorher stand die Mitte riesig (44 sp) und Front/Back als
 *         12er-Zeile darunter. Auf der Bahn braucht man ALLE DREI — die Fahne
 *         steht selten in der Mitte, und ohne Front/Back sagt die Mitte nichts
 *         ueber die Fahnenlage. Jetzt nebeneinander, links nach rechts.
 *         DIE MITTE BLEIBT GROESSER (32 gegen 24 sp) und weiss, die Aussenwerte
 *         gedaempft: Sie ist die Zahl, nach der man den Schlaeger waehlt, die
 *         anderen beiden sind die Spanne. Drei gleich grosse Zahlen zwingen zum
 *         Suchen — genau das will auf dem runden Display niemand.
 *         Darunter einmal klein „Front · Mitte · Back", damit man beim ERSTEN
 *         Blick nicht raet, welche Zahl welche ist. Die Fahne (⛳) steht separat:
 *         Sie ist eine ANDERE Groesse (gemessene Position statt Gruengeometrie).
 *     (B) DIE ZEIGER-ENTSCHEIDUNG SCHREIBT AUF, WAS SIE TUT.
 *         GEMELDET: „Einmal geht eine Eingabe durch, danach nichts mehr."
 *         Diese Stelle wurde viermal aus dem Kopf repariert, dreimal daneben.
 *         Ab sofort protokolliert sie einen verworfenen Handy-Zeiger MIT den
 *         drei Zeitstempeln, die die Entscheidung tragen (`at`, `ownLiveAt`,
 *         `ownHoleAt`). Und ein Worker, der `X-Repo-Sha` NICHT mitschickt, wird
 *         ausdruecklich gemeldet — dann laeuft der naechste Push zwangslaeufig
 *         in einen 409, und das koennte genau „einmal, dann nicht mehr" sein.
 *         NUR BEI ABWEICHUNG, nicht je Herzschlag: sonst waere der Puffer in
 *         zehn Minuten voll.
 *         `Fehler.warn`, nicht `add` — es ist eine Auskunft, kein Fehler.
 *
 *  2026-08-24 (8) · DAS FEHLERPROTOKOLL KAM NIE AN — DREI URSACHEN.
 *     (A) FALSCHER ENTWURF. Es hing an `val draft` — dem Aufbau fuer die GROSSE
 *         Datei, also dem Notweg, der praktisch nie laeuft. Das Handy las
 *         `_draftRound.watchLog` aus `draft.json` und fand deshalb NIE etwas.
 *         Eingebaut und wirkungslos, zum wiederholten Mal. Jetzt haengt es an
 *         `bauen`, dem Weg, der bei jedem Herzschlag laeuft.
 *     (B) NUR WAEHREND EINER RUNDE. `draft.json` gibt es sonst nicht — Fehler
 *         beim Start oder beim Platzladen erreichten das Handy also selbst nach
 *         (A) nicht. Genau die sucht man aber beim Einrichten.
 *         Neu `Net.logPut()` -> `watchlog.json` (Worker v2.11), im
 *         Fuenf-Minuten-Takt, der ohnehin laeuft und AUCH OHNE Runde laeuft.
 *         NUR BEI AENDERUNG: Bei fehlerfreiem Betrieb entsteht kein einziger
 *         zusaetzlicher Vorgang.
 *     (C) DER PUFFER WAR ZU KLEIN UND ZAEHLTE NICHT. 30 Zeilen fuellte EIN
 *         Vorgang — die 409-Schleife vom 24.08. schrieb vier je Versuch, und
 *         alles davor war weg. Die Vorgeschichte ist aber das, was man sucht.
 *         Jetzt 60 Zeilen, und Wiederholungen werden gezaehlt statt gesammelt
 *         („… (×5)"), wie es die App seit je tut. Verglichen wird OHNE
 *         Zeitstempel: zwei gleiche Meldungen sind dieselbe Sache.
 *     ERFORDERT WORKER v2.11 fuer (B). Ohne ihn bleibt (A) und (C) wirksam.
 *
 *  2026-08-24 (7) · UEBERSETZUNGSFEHLER AUS (6) BEHOBEN.
 *     `dr?.optJSONArray("shotAck")` und `dr?.optJSONObject("roundDone")` — aber
 *     `dr` ist ein `RepoDraft`, KEIN `JSONObject`. Der Compiler meldete
 *     „Unresolved reference 'optJSONArray'" und in der Folge ein Dutzend
 *     Nachfolgefehler.
 *     RICHTIG IST: `parseDraft` liest den JSON EINMAL und macht daraus
 *     getippte Felder. Genau dafuer gibt es die Datenklasse. Wer daneben noch
 *     einmal roh liest, hat ZWEI Stellen, an denen ein Feldname stehen kann —
 *     und irgendwann stehen dort zwei verschiedene.
 *     Neu in `RepoDraft`: `shotAck`, `doneAt`, `doneCourse`.
 *     WARUM ES NICHT AUFFIEL: Hier laesst sich kein Kotlin uebersetzen. Die
 *     Klammernbilanz stimmte (1226/1226) — Typen prueft sie nicht. Das ist
 *     derselbe Griff wie am 24.08. (3), nur eine Ebene tiefer: dort ein `val`,
 *     das ich zuweisen wollte, hier ein Typ, den ich verwechselt habe.
 *     ZUM MITNEHMEN: Bei Kotlin-Aenderungen ist der Compiler die Pruefung.
 *     Alles, was ich hier tue, ist Vorarbeit.
 *
 *  2026-08-24 (6) · ZUSAMMENARBEIT MIT DEM HANDY: DREI LUECKEN GESCHLOSSEN.
 *     (A) FASSUNG IM LIVE-ZEIGER. `WATCH_APP` stand nur im Fehlerprotokoll —
 *         also nur dann sichtbar, wenn es Fehler gab. Das Handy konnte nie
 *         sagen, welche Uhr-Fassung laeuft, und wir haben mehrfach geraten, ob
 *         eine Reparatur schon drueben ist. Der Zeiger geht bei jedem
 *         Herzschlag raus; dort kostet die Angabe nichts und ist immer aktuell.
 *         NEBENBEI: Die Kennung stand noch auf „2026-08-15 (13)", obwohl
 *         seither fuenfmal geaendert wurde. Jetzt gepflegt.
 *     (B) QUITTIERTE SCHLAGMESSUNGEN AUSRAEUMEN. Die Uhr schickte ihre
 *         Messungen bei JEDEM Vorgang mit, weil sie nie erfuhr, ob sie
 *         angekommen sind. Das Handy nennt jetzt in `shotAck` die Kennungen,
 *         die es sicher hat; nur die werden entfernt.
 *         NUR KENNUNGEN, keine Messwerte: Geht die Quittung verloren, schickt
 *         die Uhr noch einmal — der harmlose Fall. Umgekehrt waere es
 *         Datenverlust.
 *     (C) AM HANDY BEENDET -> HIER AUCH. Bis hierher kannte die Uhr nur
 *         „verworfen" (`draftDiscardedTs`). Eine normal BEENDETE Runde sah fuer
 *         sie aus wie eine laufende, die nur nichts mehr meldet: Sie funkte
 *         weiter Herzschlaege fuer etwas, das es nicht mehr gab, und zeigte
 *         Loch 18, waehrend am Handy die Karte schon gespeichert war.
 *         Uebernommen wird `roundDone` NUR bei gleichem Platz und nur, wenn die
 *         Marke juenger ist als die letzte eigene Eingabe — beides Lehren aus
 *         (9) und (10) vom 15.08., wo eine alte Marke jede neue Runde sofort
 *         wieder beendet hat.
 *     GEPRUEFT UND ENTWARNT: Die Uhr holt ihr Wetter SELBST (`Net.fetchWeather`,
 *     alle 20 min bei Bedarf) — sie haengt dafuer nicht am Handy. Und die
 *     Schlagmessungen laufen ueber `draft.json`, nicht ueber den stillgelegten
 *     Voll-Push.
 *     STATISCH GEPRUEFT: Klammernbilanz 1226/1226, 3291/3291, 69/69. Die
 *     Namen `isoOf` und `roundStart` sind die BESTEHENDEN — der erste Versuch
 *     hatte `isoVon`/`roundStartMs` erfunden, was hier niemand haette
 *     bemerken koennen (siehe (3)).
 *
 *  2026-08-24 (5) · UHR -> HANDY BESCHLEUNIGT (7,5 s -> rund 2 s).
 *     GEMESSEN, nicht geschaetzt: Das HANDY sendet bei einer Eingabe SOFORT
 *     (`playLivePush` ruft `draftPush()` direkt, ausdruecklich „nicht
 *     entprellt"). Die UHR wartete auf ihren Takt — 10 Sekunden, auch direkt
 *     nach einem Lochwechsel. Daher die Schieflage:
 *         Handy -> Uhr   im Mittel 2,5 s, schlimmstenfalls 5 s
 *         Uhr -> Handy   im Mittel 7,5 s, schlimmstenfalls 15 s
 *     Das war kein Funkproblem, sondern eine fehlende Regel: EINE HANDLUNG DES
 *     BENUTZERS SENDET SOFORT, sie wartet nicht auf den Takt. Dieselbe Regel,
 *     die beim Zeiger-Vorrang gefehlt hat (siehe (4)) — nur beim SENDEN statt
 *     beim Uebernehmen.
 *     (A) Der Lochwechsel setzt `lastEditMs` und ruft `scheduleSync()`.
 *     (B) Entprellung 1500 -> 600 ms. Sie fasst schnelle Taps weiter zusammen,
 *         aber ein Lochwechsel ist kein schneller Tap: Man drueckt einmal und
 *         schaut dann aufs Handy.
 *     (C) DOPPEL-SPERRE, damit der Gewinn nicht mit Akku bezahlt wird: Der
 *         Herzschlag laesst einen Durchlauf aus, wenn der letzte Vorgang
 *         weniger als 5 s zurueckliegt (`Net.letzterPushMs`). Ohne sie schickte
 *         jede Eingabe zweimal — einmal sofort, einmal im naechsten Takt.
 *     NICHT GEAENDERT: die Takte selbst. 5 s im Vordergrund sind schon knapp,
 *     jeder Umlauf kostet auf der Uhr messbar Akku, und eine Runde dauert vier
 *     Stunden. Der Gewinn laege bei zwei Sekunden, der Preis waere spuerbar.
 *
 *  2026-08-24 (4) · DAS HANDY UEBERSTIMMTE IMMER · ABGLEICH BRACH EIN.
 *     (A) LOCHZEIGER: `ownLiveAt` wurde bei JEDEM Push gesetzt, verglichen
 *         wurde aber der Wert von VORHER. Die Uhr sendet im Minutentakt, das
 *         Handy schreibt seinen Zeiger alle paar Sekunden — also war
 *         `at > ownLiveAt` praktisch immer wahr, und die Uhr uebernahm das
 *         Handy-Loch. Die Bedingung `h != currentHole` machte es vollends
 *         verkehrt: Sie traf ausgerechnet dann zu, wenn die Uhr-Eingabe FRISCH
 *         war — also wurde genau die verworfen.
 *         Neu `ownHoleAt`: Zeitpunkt der EINGABE, gesetzt beim Blaettern
 *         (`Net.holeGewechselt()`). Das Handy uebernimmt nur noch, wenn sein
 *         Zeiger juenger ist als die letzte Handlung auf der Uhr.
 *         EINE HANDLUNG DES BENUTZERS WIEGT SCHWERER ALS EIN AUTOMATISCHER
 *         ZEIGER — das ist die Regel, die hier gefehlt hat.
 *     (B) ABGLEICH BRACH EIN: Nach einem ERFOLGREICHEN Schreibvorgang hat
 *         `draft.json` eine NEUE Kennung. Die Uhr kannte sie nicht und schickte
 *         beim naechsten Mal die alte — 409, neu lesen, neu senden. Bei JEDEM
 *         Push. Schreibt parallel das Handy, ist die Wiederholungsschleife nach
 *         vier Versuchen erschoepft: „4× Konflikt (409) — Abgleich ausgesetzt".
 *         Worker v2.10 gibt die neue Kennung zurueck (Kopf `X-Repo-Sha`), die
 *         Uhr uebernimmt sie. Fehlt der Kopf (aelterer Worker), bleibt es beim
 *         alten Verhalten: ein Umlauf mehr, aber kein Abbruch.
 *     ERFORDERT WORKER v2.10 fuer (B). Ohne ihn laeuft alles wie bisher.
 *
 *  2026-08-24 (3) · UEBERSETZUNGSFEHLER AUS (2) BEHOBEN.
 *     `onHolePrev = { if (idx > 0) idx -= 1 }` stand IN `PlayPager` — dort ist
 *     `idx` aber ein `val`-Parameter und `cs` existiert gar nicht. Der Compiler
 *     meldete „'val' cannot be reassigned" und „Unresolved reference 'cs'".
 *     Beides gehoert der aufrufenden Composable. Jetzt reicht `PlayPager` einen
 *     Rueckruf `onHoleDelta(+1/-1)` durch; die Grenzen prueft die Stelle, die
 *     den Zustand auch BESITZT — ein Zielindex von der Anzeige aus waere eine
 *     zweite Stelle, an der man sich verrechnen kann.
 *     `hasNext` rechnet mit `total - 1` statt mit `cs.holes.lastIndex`.
 *     WARUM ES NICHT AUFFIEL: Hier laesst sich kein Kotlin uebersetzen. Die
 *     statische Durchsicht prueft Klammern, und die stimmten. Sichtbarkeit von
 *     Namen und Veraenderbarkeit prueft sie NICHT — genau davor warnt der
 *     Eintrag vom 22.08. („Was das nicht ersetzt: Typpruefung, Namensaufloesung,
 *     Nullbarkeit"). Der Pruefstand der App haelt jetzt wenigstens die STRUKTUR
 *     fest: kein Schreibzugriff auf fremden Zustand aus `PlayPager` heraus.
 *
 *  2026-08-24 (2) · LOCHWECHSEL AUF SEITE 1 · PROTOKOLL REIST MIT ·
 *     LAGEFAKTOREN AN DIE APP ANGEGLICHEN.
 *     (1) LOCH VOR/ZURUECK auf der ersten Seite. Bisher ging das nur ueber die
 *         Score-Seite — ein Wisch zu viel, wenn man mit Handschuh und Trolley
 *         dasteht und ohnehin auf Entfernung und Schlaeger schaut.
 *         Die UEBERTRAGUNG ANS HANDY brauchte keinen eigenen Weg: `idx` ist
 *         derselbe Zustand, den `pushDraft` als `live.hole` mitschickt. Wer
 *         hier blaettert, blaettert das Handy mit — wie umgekehrt seit Langem.
 *         Die Grenzen werden an der AUFRUFSTELLE geprueft, nicht in der
 *         Composable: Ein `idx` ausserhalb der Lochliste beendet die App beim
 *         naechsten Zeichnen (siehe „Lochzeiger" weiter unten).
 *         Am ersten/letzten Loch wird der Pfeil ausgegraut statt entfernt —
 *         eine Schaltflaeche, die verschwindet, laesst den Daumen ins Leere
 *         greifen und die Zeile springen.
 *     (2) DAS FEHLERPROTOKOLL REIST MIT. Es war bisher NUR auf der Uhr lesbar
 *         — rundes Display, kein Kopieren, mitten auf der Bahn. Ausgewertet
 *         wurde es damit praktisch nie. Jetzt haengt es als `watchLog` am
 *         Rundenentwurf: keine neue Datei, kein neuer Worker-Pfad, kein
 *         zusaetzlicher Funkverkehr (der Entwurf geht ohnehin im Minutentakt
 *         raus, 30 Zeilen sind rund 3 kB). Die App zeigt es unter
 *         Mehr → Daten → Diagnose als eigenen Block — mit Geraet und Stand,
 *         und ausdruecklich NICHT in ihr eigenes Protokoll gemischt.
 *     (3) LAGEFAKTOREN ANGEGLICHEN. Nachgemessen gegen `STRAT.LAGE_FAKTOR`:
 *         Sand 0,72 gegen 0,75 und Recovery 0,58 gegen 0,80 — bei Recovery
 *         22 Prozentpunkte. Aus demselben Erholungsschlag wurde auf der Uhr
 *         ein deutlich kuerzerer Schlaeger empfohlen als im Handy. Zwei
 *         Antworten auf dieselbe Frage sind schlimmer als eine falsche, weil
 *         sie das Vertrauen in BEIDE kosten.
 *     GEPRUEFT UND UNVERAENDERT: `Wx.playsLike` stimmt mit der App auf die
 *     Stelle ueberein (Temperatur 0,0022/°C, Gegenwind 0,014, Rueckenwind
 *     0,008, bergab 0,75). Die EV-Rechnung (Erwartungstabellen, `sigmaHang`,
 *     `sigmaLage`) gibt es hier bewusst NICHT — die Uhr zeigt den vom Handy
 *     berechneten Gameplan und rechnet nur die Regel-Variante selbst.
 *     STATISCH GEPRUEFT: Klammernbilanz 1207/1207, 3232/3232, 69/69.
 *  2026-08-24 · SYNC AUF DEN SHA-MODUS UMGESTELLT (Worker v2.9).
 *     DRINGEND, weil der ALT-Modus im Worker entfernt wurde: Das
 *     Sicherheitsnetz in `pushDraft` — es greift, wenn `draft.json` nicht
 *     geschrieben werden kann — schickte `{"data": db}` OHNE `X-Path`. Genau
 *     das war der ALT-Modus, in dem der WORKER serverseitig gemerged hat.
 *     Seine Regeln wichen von denen der App ab: keine Grabsteine (geloeschte
 *     Runden waeren wieder auferstanden), keine Zeitstempel (eine bearbeitete
 *     Runde haette gegen die aeltere Fassung verloren). Der Worker antwortet
 *     darauf jetzt mit 426; ohne diese Aenderung waere das Netz zerrissen.
 *     · `readData()` merkt sich die Kennung aus dem Kopf `X-Repo-Sha`
 *       (`fullSha`). Bis hierher wurde sie weggeworfen — deshalb blieb nur
 *       der ALT-Weg.
 *     · Der Rueckfall auf `DATA_URL` (roh von GitHub Pages) liefert KEINE
 *       Kennung. Von dort gelesene Daten taugen zum ANZEIGEN, nicht zum
 *       Zurueckschreiben; `fullSha` wird dann auf `null` gesetzt.
 *     · Ohne Kennung wird NICHT geschrieben. Ein leeres `X-Base-Sha` heisst
 *       fuer die GitHub-API „Datei neu anlegen" — bei bestehender Datei
 *       scheitert das, und im schlimmsten Fall ersetzt es sie. Lieber diesen
 *       Durchlauf auslassen; der naechste liest frisch.
 *     · KEIN `X-Force`. Das waere genau der Datenverlust, den der
 *       SHA-Tuersteher verhindern soll. Ein 409 heisst „jemand war schneller"
 *       und ist keine Stoerung, sondern eine Aufforderung: frisch holen, neu
 *       einarbeiten, erneut senden — das erledigt der naechste Durchlauf.
 *     STATISCH GEPRUEFT (kein JDK verfuegbar): Klammernbilanz 1202/1202
 *     geschweift, 3214/3214 rund, 69/69 eckig. Ein `./gradlew assembleDebug`
 *     steht weiterhin aus.
 *  2026-08-22 · STATISCHE DURCHSICHT der Aenderung vom 21.08. (PWA v4.18).
 *     Uebersetzt werden konnte die Datei bei der Aenderung nicht — kein JDK,
 *     kein Android-SDK. Deshalb hier nachgeholt, was ohne Compiler moeglich
 *     ist, damit die offene Baustelle wenigstens vermessen ist:
 *     · Klammernbilanz mit einem Scanner, der Zeichenketten, Zeichenliterale
 *       und beide Kommentararten ueberspringt: 1201/1201 geschweift,
 *       3207/3207 rund, 69/69 eckig.
 *     · Beide `Caddy.plan`-Aufrufe gegen die Signatur gezaehlt: 10 Parameter,
 *       10 Argumente, Reihenfolge stimmt (`dElev` steht VOR `lie`).
 *     · `Locale` ist importiert (Zeile 98) — `elevLabel` nutzt `Locale.US`.
 *     · `ElevProfil` steht vor `HoleGeo`, `Geo.dist` existiert mit der
 *       benutzten Signatur.
 *     KORRIGIERT: In `HoleGeo.dElev` berechnete die innere Funktion `aufAchse`
 *     die Achsenlaenge ein zweites Mal (`val ax = Geo.dist(t, g)`), obwohl sie
 *     als `achse` bereits vorlag. Kein Fehler, aber zwei Rechnungen derselben
 *     Groesse — und genau daraus entstehen spaeter Abweichungen.
 *     WAS DAS NICHT ERSETZT: Typpruefung, Namensaufloesung, Nullbarkeit. Ein
 *     `./gradlew assembleDebug` steht weiterhin aus.
 *  2026-08-21 · HOEHE AUF DER UHR (zu PWA v4.1). `Wx.playsLike` rechnete mit
 *     Temperatur und Wind, aber OHNE Hoehe und OHNE Regen — die PWA hat beides
 *     seit langem. Zwei Rechnungen fuer dieselbe Sache: Handy und Uhr nannten
 *     fuer denselben Schlag verschiedene Zahlen, und am Ende glaubt man keiner
 *     von beiden. Das ist der Fehler, der sich hier am laengsten gehalten hat.
 *     Die Uhr hatte nie Hoehendaten; seit PWA v3.95 liegt am Handy ein
 *     amtliches 1-m-Gelaendemodell (DGM1). Das GANZE Raster mitzuschicken waere
 *     unsinnig (rund 100 kB je Platz) — und unnoetig: Die Uhr braucht keine
 *     Neigung, sondern EINE Zahl, naemlich wie viel hoeher das Ziel liegt.
 *     Deshalb kommt je Bahn ein PROFIL in `watch.json`: alle ~20 m ein Wert,
 *     in ganzen DEZIMETERN RELATIV ZUM ABSCHLAG. Ein 480-m-Loch sind 25 kleine
 *     Zahlen, 18 Loecher unter 2 kB.
 *     · NEU `ElevProfil` (+`beiMeter`) und `HoleGeo.elev`/`HoleGeo.dElev`.
 *       Beide Punkte werden per Kosinussatz auf die Tee-Gruen-Achse projiziert;
 *       das Profil kennt nur EINE Dimension, und quer zur Bahn ist der
 *       Unterschied auf einem Golfplatz klein gegen den entlang.
 *     · `Wx.playsLike(dist, w, bearing, dElev, nass)` — bergauf voll, bergab
 *       gedaempft (0,75), Regen 3 %. Wortgleich zur PWA.
 *     · `Caddy.plan`/`planCore` nehmen `dElev` entgegen; beide Aufrufer holen
 *       es aus `HoleGeo.dElev(here, target)`.
 *     · `Wx.elevLabel` und eine Zeile im Plan, die IMMER steht — auch mit 0
 *       und auch wenn nichts vorliegt („⛰ eben (±0 m)" gegen „⛰ Höhe
 *       unbekannt"). Wie in der PWA seit v3.97: Wer nichts anzeigt, laesst den
 *       Leser raten, ob gerechnet wurde. Auf der Uhr wiegt das schwerer — der
 *       Bildschirm ist klein, man sieht nur die eine Zahl.
 *     · KEIN ERSATZWERT: Fehlt das Profil, ist `dElev` null und es rechnet wie
 *       vorher. Ein halbes Profil wird gar nicht erst uebernommen — die Uhr
 *       koennte nicht erkennen, wo es endet, und wuerde den Rand fortschreiben.
 *     Rauschsperre 0,3 m statt 1,5 m: Die Quelle ist dezimetergenau; die alte
 *     Schwelle stammte vom groben globalen Raster.
 *  2026-08-14 (2c) · KORREKTUR: Leere/fehlende `draft.json` galt als „keine
 *     [KENNUNG KORRIGIERT 2026-08-25 (22): „(2)" war dreifach vergeben.]
 *     Runde" — dadurch stand der Abgleich still, solange die Datei nicht
 *     existierte (also bis zum allerersten Push). Sie ist jetzt nur noch die
 *     Auskunft „hier steht nichts"; es geht dann ueber die grosse Datei weiter.
 *  2026-08-14 (2b) · FIX zum Draft-Umbau: Ein Worker VOR v2.6 kennt den
 *     [KENNUNG KORRIGIERT 2026-08-25 (22): „(2)" war doppelt vergeben.]
 *     `path`-Parameter nicht — er ignoriert ihn und liefert mit Status 200 die
 *     GROSSE Datei. fetchDraftFile hielt das fuer einen Erfolg, fand kein
 *     `round` und fetchDraft gab null zurueck OHNE auf die grosse Datei
 *     zurueckzufallen: Die Uhr uebernahm keine Handy-Eingaben mehr.
 *     Jetzt wird der INHALT geprueft (testDefs/rounds/_draftRound => grosse
 *     Datei => null => alter Weg). Ein Statuscode sagt nicht, WAS man bekommen
 *     hat.
 *  2026-08-14 (1b) · SCHLAGZEILE MITSCROLLEND (HolePage): Die Chip-Reihe war unten
 *     [KENNUNG KORRIGIERT 2026-08-25 (22): stand ohne Nummer und kollidierte
 *      damit mit dem anderen unnummerierten Eintrag desselben Tages.]
 *     VERANKERT (Box/BottomCenter) und lag damit UEBER dem Inhalt — 48 dp
 *     Chips plus 54 dp Freihaltung sind auf dem runden Display fast ein
 *     Drittel der Hoehe und verdeckten Mitteldistanz, Gameplan und
 *     Caddy-Zeile. Jetzt steht sie am ENDE des scrollenden Inhalts: ein
 *     Kronendreh mehr fuers Aufnehmen, dafuer ein freier Bildschirm fuers
 *     Schauen — und die Seite heisst „schauen", nicht „aufnehmen".
 *  2026-08-16 (15) · WARNUNGEN NEBEN FEHLERN (`Fehler.warn`, `warnEinmal`):
 *     Ein Fehler sagt „etwas ist schiefgegangen", eine WARNUNG „es lief, aber
 *     das Ergebnis kann falsch sein" — auf der Bahn die wichtigere Auskunft.
 *     Gemeldet werden jetzt: GPS-GENAUIGKEIT schlechter als 25 m (eine Distanz
 *     aus einer 40-m-Position sieht aus wie eine gute und liegt zwei Schlaeger
 *     daneben — der haeufigste Grund fuer „die Uhr zeigt Unsinn"), Platz OHNE
 *     KARTE, LEERER Schlaegerbeutel, VERALTETER Datenstand, GPS stumm,
 *     Abgleich stumm.
 *     Im Protokoll drei Stufen: Absturz rot, Warnung gold, Rest ruhig — ohne
 *     Unterscheidung liest man dreissig gleich aussehende Zeilen und findet die
 *     eine nicht.
 *
 *  2026-08-16 (14) · VIEL MEHR WIRD MITGESCHRIEBEN:
 *     19 stumme `catch`-Zweige melden jetzt (Karte lesen, draft/probe/watch.json,
 *     Wetter, Runde laden, Zwischenspeicher, Dienst, GPS starten/stoppen/neu,
 *     Handy-GPS, Sync, Rundenende). Vorher gaben sie null zurueck und schwiegen.
 *     Beim LESEN wird der HTTP-Code genannt: Netzausfall, falscher Schluessel
 *     (401/403) und Worker-Fehler (5xx) sehen sonst gleich aus, brauchen aber
 *     drei verschiedene Abhilfen.
 *     Neu ausserdem eine ZUSTANDSWACHE fuer die Stoerungen, die GAR KEINE
 *     Ausnahme werfen und deshalb wie Normalbetrieb aussehen: GPS liefert seit
 *     drei Minuten keine Position, der Abgleich hat seit fuenf Minuten nichts
 *     uebertragen. Gemeldet wird jeweils NUR BEIM WECHSEL des Zustands — im
 *     Takt waere das Protokoll nach zehn Minuten voll mit derselben Zeile, und
 *     die eine Meldung, auf die es ankommt, waere verdraengt.
 *     Dazu: „Runde uebernommen" mit Platz, Umfang, Lochzahl und Startloch.
 *
 *  2026-08-16 (13) · FEHLERPROTOKOLL AUSFUEHRLICH UND AUF DEM STARTBILDSCHIRM:
 *     Der Startbildschirm ist der wichtigere Ort — nach einem Absturz landet
 *     man dort und will wissen, was war, ohne erst eine Runde zu starten.
 *     Eine gemeinsame Funktion `fehlerBlock()` fuer beide Stellen (dieselbe
 *     Sache zweimal gebaut laeuft auseinander).
 *     AUSFUEHRLICHER: 30 statt 12 Eintraege · Ort (`Fehler.kontext`: Bildschirm,
 *     Platz, Loch) · Faden-Name · beim Absturz fuenf eigene Stellen mit
 *     Klassenname, die erste fremde Stelle und die URSACHENKETTE (bei Compose
 *     steckt der wahre Fehler regelmaessig zwei `cause` tiefer) · Startzeile
 *     mit Uhr-Fassung, Geraet und Android-Version · keine Kuerzung der Zeilen,
 *     weil der Aufrufstapel am ENDE steht.
 *
 *  2026-08-16 (11) · ABSTUERZE WAEHREND DER RUNDE — DREI URSACHEN:
 *     (1) `rec!!.start!!` nach `if (rec?.start != null)`. Das sieht sicher aus,
 *         ist es aber nicht: `rec` ist veraenderlicher Zustand, und seit der
 *         automatischen Schlagerfassung setzt ihn ein SENSOR-RUECKRUF, also ein
 *         anderer Faden. Faellt die Erkennung zwischen Pruefung und Zugriff,
 *         beendet `!!` die App — selten, aber waehrend jeder Runde staendig
 *         neu, und es sieht nach einem zufaelligen Absturz aus. Jetzt ueber
 *         eine lokale Groesse, die niemand mehr aendert. Dasselbe bei `picker`.
 *     (2) `cs.holes[idx]` ohne Pruefung, an zwei Stellen, die bei JEDER
 *         Neuzeichnung laufen. Wird der Umfang waehrend der Runde auf Front 9
 *         gestellt (Uebernahme vom Handy), schrumpft die Lochliste auf neun —
 *         `idx` bleibt aber stehen. Auf Loch 13 ist der naechste Zeichenlauf
 *         der letzte. Jetzt wird der Zeiger zurechtgerueckt und gemeldet.
 *     (3) `arr.last()` im zweiten Zweig, ohne eigene Pruefung.
 *
 *  2026-08-16 (10) · Das Fehlerprotokoll blieb nach einem ABSTURZ leer, weil
 *     `apply()` im Hintergrund schreibt — der Prozess stirbt vorher. Der
 *     Absturzweg schreibt jetzt mit `commit()` (blockierend; der Prozess geht
 *     ohnehin unter) und notiert bis zu drei eigene Stellen aus dem
 *     Aufrufstapel statt nur einer.
 *
 *  2026-08-16 (9) · DER ABGLEICH RISS NACH WENIGEN LOECHERN AB — LAUTLOS.
 *     `pushDraftFile` versuchte bei einem Schreibkonflikt (409) GENAU EINMAL
 *     erneut und gab dann still auf. Solange selten geschrieben wurde, fiel
 *     das nicht auf; seit das Handy waehrend der Runde alle 10 s schreibt
 *     (PWA v3.19), kollidiert fast jeder Versuch — und danach lief die Uhr
 *     fuer den Rest der Runde ohne Abgleich weiter.
 *     Jetzt vier Versuche mit ZUFAELLIGER Pause (zwei Geraete, die nach einem
 *     Konflikt gleichzeitig neu schicken, kollidieren wieder — synchron, also
 *     immer), und jedes Aufgeben landet im Fehlerprotokoll. Ein lautloses
 *     Scheitern ist der schlimmste Fall: Es sieht aus wie Erfolg.
 *
 *  2026-08-16 (8) · FEHLERPROTOKOLL + ABSTURZ BEIM SUCHEN DER HANDY-RUNDE:
 *     Eine Ausnahme INNERHALB von `scope.launch` beendet die ganze App — sie
 *     landet beim globalen Auffang, nicht in einem catch-Zweig. Beim Suchen
 *     der Handy-Runde reichen dafuer ein Netzfehler, eine unerwartete Antwort
 *     oder ein Platz ohne Loecher. Der Block ist jetzt abgesichert; der
 *     Wartezustand wird dabei aufgeloest, sonst stuende „suche…" fuer immer da.
 *     Neu `object Fehler`: haelt die letzten 12 Meldungen in den Einstellungen
 *     (also ueber Neustart und Absturz hinweg) und zeigt sie am ENDE von
 *     Seite 3 — nur wenn es welche gibt. Der globale Auffang notiert und reicht
 *     DANACH weiter: Die App soll trotzdem abstuerzen, statt in einem Zustand
 *     weiterzulaufen, dem man nicht mehr trauen kann.
 *
 *  2026-08-16 (7) · BAUFEHLER: `Caddy.lieFactor` gibt es nicht — die Funktion
 *     liegt in `Geo` (so rufen sie auch `planCore` und `Caddy.plan` auf).
 *
 *  2026-08-16 (6) · BAUFEHLER: 10 × „Argument type mismatch" in `parseDraft`.
 *     Die neuen `caddy*`-Felder standen in der MITTE von `RepoDraft` — und
 *     `parseDraft` baute den Entwurf mit POSITIONS-Argumenten. Damit rutschte
 *     jeder nachfolgende Wert eine Stelle weiter.
 *     ZWEI KONSEQUENZEN: (1) Die Felder stehen jetzt am ENDE der Datenklasse,
 *     wie es bei `Caddy.Plan` schon vermerkt war. (2) Wichtiger: Der Aufruf
 *     ist auf BENANNTE Argumente umgestellt. Der Compiler hat den Fehler hier
 *     nur gefunden, weil die Typen zufaellig kollidierten — bei String nach
 *     String waere er stillschweigend durchgelaufen und haette falsche Daten
 *     geliefert. Mit Namen ist die Reihenfolge gleichgueltig.
 *
 *  2026-08-16 (5) · BEWEGUNG LOEST EINE MELDUNG AUS: Der Push-Takt hing allein
 *     an EINGABEN (10 s kurz danach, sonst 60 s). Wer nur geht, tippt nichts —
 *     die gemeldete Position war damit bis zu eine Minute alt, und das Handy
 *     rechnete seine Empfehlung fuer einen Punkt, an dem man laengst nicht mehr
 *     stand. Jetzt gilt auch „mehr als 15 m seit der letzten Meldung" als
 *     Ereignis. 15 m, weil darunter die Schlaegerwahl gleich bleibt.
 *
 *  2026-08-16 (4) · UHR MELDET IHRE POSITION (`live.pos`): Das Handy ist beim
 *     Caddy fuehrend, rechnete aber mit SEINER Position — und es liegt oft im
 *     Trolley, waehrend man am Ball steht. Zwanzig Meter sind ein halber
 *     Schlaeger. Die Uhr meldet jetzt ihren Standort (nur mit Genauigkeit
 *     besser als FixQuality.MAX_ACC), das Handy rechnet FUER DIESEN PUNKT
 *     (PWA v3.18) und schickt das Ergebnis zurueck.
 *     Damit gilt beides: bestes Modell UND richtige Position.
 *
 *  2026-08-16 (3) · DAS HANDY IST FUEHREND BEIM CADDY:
 *     Beide Geraete hatten einen EIGENEN Caddy — und zwar zwei verschiedene
 *     Modelle: Das Handy rechnet mit der EV-Engine (Monte Carlo ueber die
 *     Streuung, Lie-Raster, zwei Zuege voraus), die Uhr mit dem einfacheren
 *     „spielt-wie"-Modell. Auf demselben Ball konnten sie verschiedene
 *     Schlaeger nennen; dann glaubt man auf der Bahn keinem von beiden.
 *     Die Uhr kann die EV-Engine nicht rechnen (keine Streuungsdaten, keine
 *     Rechenzeit) — sie uebernimmt jetzt das ERGEBNIS, das mit dem Live-Zeiger
 *     ohnehin jede Sekunde reist (`live.caddy` aus PWA v3.17).
 *     VERWORFEN wird es, wenn es nicht mehr gilt: anderes Loch, aelter als
 *     90 s, oder weiter als 20 m von der eigenen Position. Dann bleibt die
 *     eigene Rechnung — sichtbar an „🎯" statt „📱". Eine stille Vertauschung
 *     waere schlimmer als der Unterschied selbst.
 *
 *  2026-08-16 (2) · GAMEPLAN-BILDSCHIRM: Der Plan wird auf dem HANDY gerechnet
 *     (STRAT/EV-Engine) und reist als `AppData.plans` mit — die Uhr hatte ihn
 *     laengst dabei, konnte ihn aber nur WAEHREND einer Runde zeigen. Zum
 *     Nachsehen am Vorabend musste man eine Runde starten.
 *     Neu `GameplanScreen` (Knopf „📋 Gameplan ansehen" auf dem Startbildschirm,
 *     nur wenn Plaene vorliegen): Platz waehlen, dann Loch fuer Loch Schlaeger
 *     und Ziel. GERECHNET WIRD NICHTS — zwei Rechenwege waeren zwei Wahrheiten,
 *     und die Uhr hat weder die Streuungsdaten noch die Rechenzeit dafuer.
 *     Bei nur EINEM Platz mit Plan entfaellt die Auswahl.
 *
 *  2026-08-16 · KOPPLUNGSTEST prueft jetzt auch die CADDY-KONSTANTEN: Die Uhr
 *     meldet ihre Lage-Faktoren (`Caddy.lieFactor`), das Handy vergleicht sie
 *     mit `LIE_F`. Beide Seiten haben einen eigenen Caddy — dieselben Zahlen an
 *     zwei Orten laufen auseinander, sobald eine Seite geaendert wird, und das
 *     faellt sonst erst auf der Bahn auf.
 *
 *  2026-08-15 (17) · Der Kopplungstest-Beantworter lief DAUERHAFT (alle 5 s
 *     eine Netzanfrage, auch waehrend der Runde und im Ambientmodus). Fuer ein
 *     Werkzeug, das man einmal in der Woche benutzt, ist das der falsche Preis.
 *     Jetzt nur auf dem Startbildschirm und nicht im Ambientmodus — der Test
 *     wird ohnehin zu Hause gefahren, mit der Uhr in der Hand.
 *
 *  2026-08-15 (16) · STARTBILDSCHIRM: Der oberste Knopf war eine ANWEISUNG,
 *     keine Handlung — „📱 Runde vom Handy · am Handy starten, hier holen".
 *     Wer ihn tippt, ohne dass drueben eine Runde laeuft, wartet zwei Minuten
 *     auf nichts. Und der Bildschirm sagte nirgends, ob ueberhaupt etwas
 *     bereitliegt.
 *     Jetzt steht oben der ZUSTAND (welche Runde liegt beim Handy, wie alt
 *     sind die Daten) statt des Titels „⛳ Golf-Runde" — der kostete eine ganze
 *     Zeile und sagte, was ohnehin auf dem Zifferblatt steht.
 *     Der Knopf heisst „📱 Runde holen" MIT Platznamen, wenn etwas bereitliegt,
 *     sonst „📱 Auf Handy warten · erst am Handy starten"; nur im ersten Fall
 *     ist er hervorgehoben.
 *     „Display an" und „GPS-Quelle" stehen unter einer Trennzeile
 *     „Einstellungen" — beides stellt man einmal ein, nahm aber je ein Drittel
 *     der Bildschirmhoehe zwischen den Handlungen ein.
 *     Nebenwirkung behoben: Die Nebenzeilen liefen auf dem runden Rand aus dem
 *     Bild („mmer" statt „immer") — jetzt 11 sp und maxLines = 1.
 *
 *  2026-08-15 (15) · KOPPLUNGSTEST erweitert: Statt einer Frage arbeitet die
 *     Uhr jetzt einen PRUEFPLAN ab — mehrere Loecher (vertauscht, laengstes,
 *     kuerzestes) mit je drei Positionen, dazu Schlaegertabelle, Auswahllisten,
 *     eine Caddy-Empfehlung und die Quelle der eigenen Daten
 *     (`Net.lastWatchFile`: watch.json oder grosse Datei).
 *     Eine einzelne Distanz kann auf einem harmlosen Loch zufaellig stimmen;
 *     der Plan prueft dort, wo es weh tut.
 *
 *  2026-08-15 (14) · BUILD: `data?.approachBuckets` gibt es nicht — die Listen
 *     liegen in `Options` (`data?.opts?.approachBuckets`). AppData haelt sie
 *     nicht flach, sondern im Options-Objekt.
 *
 *  2026-08-15 (13) · KOPPLUNGSTEST: Die PWA legt eine Frage in `probe.json`
 *     (Platz, Loch, Testposition), die Uhr antwortet mit dem, was SIE daraus
 *     rechnet — Distanz zur Gruenmitte, Front/Back, Schlaegerzahl, Listen,
 *     Fassung. Die PWA zeigt beide Zahlen nebeneinander.
 *     WOZU: Ob beide Geraete dieselbe Karte sehen, zeigte bisher erst die
 *     Bahn — dort ist es aufgefallen (40 m statt 300 m). Jetzt zu Hause in
 *     zehn Sekunden pruefbar.
 *     Der Beantworter laeuft, solange die App offen ist, braucht keine Runde
 *     und ruehrt keine Spieldaten an. Er laedt die Karte des GEFRAGTEN Platzes,
 *     nicht die gerade geladene — sonst waere der Vergleich wertlos.
 *     `WATCH_APP` ist die Fassungskennung der Uhr; bei Aenderungen mitziehen.
 *     Worker ab v2.8 (probe.json in CFG.PATHS).
 *
 *  2026-08-15 (12) · AUTOMATIK erfasste nichts — der Grund ist die Messung:
 *     `recBegin`/`recStop` sammeln 3 s NACH dem Aufruf und verwerfen das
 *     Ergebnis, wenn der Spieler sich dabei bewegt (MOVE_LIMIT_M = 8 m). Von
 *     Hand stimmt das — man tippt am Ball und wartet. Bei der Automatik faellt
 *     der Aufruf aber in den TREFFMOMENT, und danach geht man sofort los. Die
 *     Streuung sprengte damit systematisch die Grenze: ausgeloest hat sie,
 *     erfasst wurde nichts.
 *     Neu `Live.verlauf` (die letzten 30 Fixes) und `FixQuality.ausVerlauf()`:
 *     Die Automatik misst aus den Sekunden VOR dem Treffer — da stand man am
 *     Ball. Das ist nicht nur moeglich, sondern die bessere Messung.
 *     Ausserdem: Vibration und „Schlag erkannt" kamen frueher auch dann, wenn
 *     nichts gemessen wurde. Jetzt meldet die Uhr „erkannt — aber GPS zu
 *     ungenau", wenn es so ist.
 *
 *  2026-08-15 (10) · Die Verwerfen-Marke darf die eigene Runde nicht kippen:
 *     Verglichen wurde mit dem RUNDENBEGINN — jede Marke, die waehrend der
 *     Runde entsteht, war damit „juenger" und beendete sie. Zusammen mit einem
 *     Fehler in PWA v3.02 (das Handy schrieb den Grabstein im Sekundentakt,
 *     auch ohne eigene Runde) hat das die Uhr auf der Bahn lahmgelegt.
 *     Jetzt zaehlt die letzte EIGENE Eingabe (`lastEditMs`): Wer gerade tippt,
 *     hat die juengere Aussage.
 *
 *  2026-08-15 (9) · VERWERFEN gilt jetzt auf BEIDEN Geraeten:
 *     Eine leere `draft.json` heisst nur „gerade keine Runde im Repo" — nicht
 *     „diese Runde ist verworfen". Das Handy spielte deshalb weiter, sein
 *     naechster Push legte die Runde wieder an, und weil der juenger war, kam
 *     sie auch auf der Uhr zurueck. Ein Fehlen laesst sich nicht uebertragen,
 *     ein DATUM schon (dieselbe Lehre wie bei den geloeschten Platzkarten).
 *     `onDiscard` schreibt jetzt `{discardedTs}` ins Repo (Net.pushDiscarded);
 *     die Pull-Schleife beendet die eigene Runde, wenn die Marke JUENGER ist
 *     als der eigene Rundenbeginn — sonst beendete eine alte Marke jede neue
 *     Runde sofort wieder.
 *     Ohne Rueckfrage: Die Entscheidung ist auf dem anderen Geraet gefallen.
 *     Gegenstueck in der PWA: v3.02.
 *
 *  2026-08-15 (8) · Lokales Sichern vollstaendig:
 *     `entryToJson` (SharedPreferences) schrieb `apprMiss` und `apprClub`
 *     nicht — `buildRoundJson` (Repo) schon. Nach einem Neustart der Uhr waren
 *     die beiden Felder lokal weg, obwohl sie im Repo standen; beim Start
 *     gewinnt aber der lokale Stand. Ausserdem schrieb es die vier
 *     Ja/Nein-Felder als JSON-Boolean statt in der Sprache der PWA (`jn`).
 *     GRUNDMUSTER: Zwei Schreibwege fuer dieselben Daten laufen auseinander,
 *     wenn ein Feld dazukommt. Wer hier eines ergaenzt, ergaenzt BEIDE.
 *
 *  2026-08-15 (7) · AENDERUNGEN kommen jetzt auf dem anderen Geraet an:
 *     `adoptHoles` fuellte nur LEERE Felder („wer schon etwas drinstehen hat,
 *     behaelt es"). Das verhindert gegenseitiges Ueberschreiben — machte aber
 *     das AENDERN unmoeglich: Ein am Handy korrigierter Tee-Schlaeger kam auf
 *     der Uhr nie an, und umgekehrt.
 *     Neu traegt JEDES Loch einen Zeitstempel (`HoleEntry.ts`, gesetzt in
 *     `change()`, mitgeschrieben in entryToJson/jsonToEntry). Traegt der fremde
 *     Stand fuer DIESES Loch den juengeren, gewinnen seine gesetzten Felder;
 *     sonst bleibt es beim alten Verhalten. Fehlt der Zeitstempel (Entwurf von
 *     vor dieser Fassung), ebenfalls altes Verhalten.
 *     JE LOCH und nicht je Entwurf: Der Entwurfs-Zeitstempel sagt nur, welches
 *     GERAET zuletzt etwas getan hat. Wer auf Loch 7 tippt, waehrend das andere
 *     Geraet Loch 3 korrigiert hat, wuerde sonst dessen Korrektur ueberschreiben.
 *     `null` loescht in KEINEM Fall etwas — wer ein Feld leert, muss es am
 *     selben Geraet tun.
 *     Gegenstueck in der PWA: v2.98 (playTouchHole, playAdoptDraft, mergeDraft).
 *
 *  2026-08-15 (6) · SCHWUNGLAENGE bei der Automatik:
 *     Jeder erkannte Schlag ging bisher als VOLL in die Daten (`swing = null`),
 *     denn die Automatik konnte es nicht wissen. `clubMeasured` in der PWA
 *     lernt die Schlaegerlaengen aber NUR aus vollen Schwuengen — ein
 *     kontrollierter Dreiviertel-Wedge haette die gelernte Laenge nach unten
 *     gezogen und die Caddy-Empfehlung systematisch zu kurz gemacht.
 *     `Swing` fuehrt jetzt die SPITZENDREHRATE des Schwungs mit und gibt sie
 *     an den Handler weiter; unter FULL_W (16 rad/s) wird der Schlag als „¾"
 *     gebucht und faellt damit aus dem Lernen heraus.
 *     RICHTUNG DES IRRTUMS: Ein faelschlich als „¾" gebuchter voller Schlag
 *     kostet EINEN Lernwert. Ein faelschlich als voll gebuchter halber Wedge
 *     verfaelscht die Schlaegerlaenge dauerhaft. Also lieber zu vorsichtig.
 *
 *  2026-08-15 (5) · Der LETZTE Schlag eines Lochs ging verloren:
 *     Die Automatik BEGINNT eine Aufnahme beim Treffmoment und BEENDET sie beim
 *     naechsten. Beim letzten vollen Schlag eines Lochs — meist der Annaeherung
 *     — kommt der naechste Treffer aber erst auf dem naechsten Loch, und
 *     dazwischen liegt der Lochwechsel, der `rec` bisher ersatzlos verwarf.
 *     Jetzt wird eine offene Aufnahme beim Wechsel GESCHLOSSEN (recStop): Man
 *     steht dann am Gruen, also dort, wo der Ball lag.
 *     REIHENFOLGE: erst schliessen, dann `idx` aendern — `recStop` schreibt den
 *     Schlag auf das gerade aktive Loch.
 *
 *  2026-08-15 (4) · REGRESSION + BEDIENKONZEPT der Automatik:
 *     (1) „Runde vom Handy laden" war weg. `parseData` liest die laufende
 *         Runde aus `_draftRound` — die steht in der GROSSEN Datei, und
 *         `watch.json` enthaelt sie bewusst nicht (sie ist heiss, die schlanke
 *         Datei wird nur bei Aenderungen geschrieben). Beides fuer sich
 *         richtig; falsch war, sie nicht wieder zusammenzufuehren. `loadData`
 *         haengt den Entwurf jetzt aus `draft.json` an, wenn keiner drin ist.
 *     (2) Die Automatik war NICHT ZU FINDEN: ein Schalter auf Seite 3, und auf
 *         Seite 1 kein Hinweis, ob sie ueberhaupt laeuft. Jetzt sagt der
 *         Schlag-Knopf selbst, worin er steht — `🏌 3` (Automatik) gegen
 *         `📐 3` (Hand) —, LANGDRUCK darauf schaltet um, und beim Betreten der
 *         Runde steht der Modus einmal in der Statuszeile. Der Schalter auf
 *         Seite 3 bleibt als der beschriftete, auffindbare Weg.
 *
 *  2026-08-15 (3) · `Live.setFix` -> `Live.neuerFix`: `var fix` erzeugt auf
 *     der JVM bereits einen Setter `setFix(Fix?)`. Gleiche Signatur, gleicher
 *     Name — „Platform declaration clash", der Bau brach ab. Der Name der
 *     Funktion ist also nicht frei waehlbar, solange das Feld `fix` heisst.
 *
 *  2026-08-15 (2) · BAUFEHLER der Automatik behoben:
 *     · Der Einhaenge-Block stand bei den uebrigen LaunchedEffects — also VOR
 *       `recBegin`/`recStop`/`recClub`. Lokale Funktionen sind in Kotlin erst
 *       NACH ihrer Deklaration sichtbar; der Bau brach mit „Unresolved
 *       reference" ab. Der Block sitzt jetzt direkt hinter `recUndo`.
 *     · `plan?.club?.let { recClub(it) }` konnte den Lambda-Parameter nicht
 *       ableiten. Jetzt mit ausdruecklichem Typ (`val c: String?`).
 *
 *  2026-08-15 · AUTOMATISCHE SCHLAGERFASSUNG (object Swing)
 *     Erkannt wird der TREFFMOMENT, nicht die Schwungbewegung: Ein
 *     Probeschwung sieht im Gyroskop praktisch gleich aus, hat aber keinen
 *     Aufprall. Bedingung ist deshalb ein Zweiklang — Drehrate ueber
 *     SWING_W (10 rad/s), und innerhalb IMPACT_MS (700 ms) danach ein Ruck
 *     ueber IMPACT_JERK (45 m/s² zwischen zwei Messungen). Danach REARM_MS
 *     (6 s) Ruhe, weil ein Schlag mehrere Stoesse erzeugt (Ball, Boden,
 *     Divot-Ende).
 *     KEIN eigener Datenweg: Beim Treffer laufen dieselben Funktionen wie bei
 *     der Hand — recStop() (der Punkt ist das Ende des vorigen Schlags) und
 *     recBegin() (und der Anfang des naechsten). Damit schreiben Automatik und
 *     Hand ueber recFinish in dieselben Felder, und „↶ letzten Schlag" holt
 *     beides gleich zurueck.
 *     Der SCHLAEGER wird mit der Caddy-Empfehlung vorbelegt: ohne Schlaeger
 *     waere die Messung fuer die gelernten Laengen wertlos, und Korrigieren
 *     kostet einen Tipp — Nachtragen kostet die Erinnerung.
 *     NICHT ERKANNT werden Putts (keine Drehrate, kein Stoss) und die meisten
 *     Chips. Absicht: Lieber ein Schlag zu wenig als ein erfundener.
 *     Schalter „Automatik → Schlaege erkennen" auf der Details-Seite,
 *     vorbelegt AN, gespeichert in `autoShot`.
 *     AKKU: SENSOR_DELAY_GAME (~50 Hz), aber `maxReportLatency` 2 s — der
 *     Sensor-Hub sammelt, die CPU wacht selten auf.
 *
 *  2026-08-14 (7) · OBERFLAECHE:
 *     · `WizBtn` ENTFERNT — gehoerte zum abgeschafften Abschluss-Wizard und
 *       stand seither ungenutzt herum. Toter Code sieht beim naechsten Lesen
 *       wie eine Zusage aus, die niemand einloest. Import `combinedClickable`
 *       mit weg; die Haptik lebt im `Stepper` weiter.
 *     · TIPPFLAECHEN: „⌚ ohne Handy starten", die Seiten-Auswahl,
 *       „‹ Uebersicht" und „↶ letzten Schlag" waren CompactChips (32 dp).
 *       Jetzt Chips mit 48 dp Mindesthoehe — Wear-Mindestmass. Ausgerechnet
 *       eine ZERSTOERENDE Aktion (Schlag zuruecknehmen) war das kleinste Ziel
 *       der Seite. Nur „‹ Abbrechen" im Picker bleibt klein: Es ist die
 *       harmloseste Aktion und ueber die Wischgeste ohnehin erreichbar.
 *     · KOPFZEILE: Der Abgleich-Marker erscheint nur noch, wenn er STOCKT.
 *       Fuenf Angaben bei 12 sp auf rundem Display waren zu viel, und eine
 *       Angabe, die immer da ist, sieht man irgendwann nicht mehr.
 *
 *  2026-08-14 (6) · Approach-Lage steht jetzt direkt nach dem Tee-Ergebnis:
 *     Sie ist das ERSTE, was am Ball feststeht — vor Entfernung und Schlaeger.
 *     Vorher stand sie hinter beiden und wurde nachgetragen oder vergessen.
 *     Fachlich haengt daran mehr als es aussieht: Ohne Angabe nimmt die
 *     SG-Rechnung FAIRWAY an; ein Approach aus dem Rough zaehlt dann gegen die
 *     Annaeherung statt gegen die Lage.
 *
 *  2026-08-14 (5) · SEITE 2 AUFGERAEUMT:
 *     Von Seite 2 (Score) nach Seite 3 (Details) gewandert: Tee-Schlaeger,
 *     Shortsided, „1. Putt ging …". Sie gehoeren fachlich zum Loch, werden
 *     aber selten gepflegt — und jede Zeile, die man auf dem runden Display
 *     ueberscrollt, kostet die Zeilen darunter.
 *     AUF SEITE 2 BLEIBT, was die SG-Rechnung TRAEGT: 1.-Putt-Distanz,
 *     Approach-Distanz und -Lage. Ohne sie lassen sich Putten, Kurzspiel und
 *     Annaeherung nicht trennen.
 *     „aus GPS uebernehmen" ENTFAELLT samt Handler (`onDistFromGps`) — die
 *     Rest-zur-Fahne-Zeile bleibt, sie wird von Hand gesetzt.
 *     STRAFSCHLAEGE stehen jetzt direkt UNTER den Putts statt weit oben: Man
 *     traegt sie am Ende des Lochs ein, zusammen mit Score und Putts.
 *
 *  2026-08-14 (4) · LADEN UND ZEICHNEN:
 *     (1) GEO-UMWEG WEG: `parseData` machte aus der Platzgeometrie jedes
 *         Platzes einen String (`toString()`), den `parseGeo` spaeter wieder
 *         parste — parsen, serialisieren, erneut parsen, und das fuer ALLE
 *         Plaetze, auch die nie gewaehlten. Jetzt bleibt das JSONObject liegen
 *         (`CourseDef.geoObj`), `parseGeo` hat eine Ueberladung dafuer, und
 *         serialisiert wird nur noch beim lokalen Sichern des EINEN Platzes.
 *     (2) SCHLANKE DATEI: `Net.fetchWatchRaw()` holt `watch.json` (rund
 *         200 kB) statt `trainingsdaten.json` (rund 3 MB); Rueckfall auf die
 *         grosse Datei bei 403/404. Geschrieben wird sie von der PWA (v2.96),
 *         Worker ab v2.7.
 *     (3) KEIN SEKUNDENTAKT fuer die Alters-Anzeige des Abgleichs — die
 *         naheliegende Loesung haette sekuendlich den halben Bildschirm neu
 *         zusammengesetzt; Begruendung steht an der Stelle selbst.
 *
 *  2026-08-14 (3) · RUCKELN: Ursache war die Zahl der NEUZEICHNUNGEN, nicht
 *     die Rechenlast. `Live.fix` ist Compose-State und wurde im GPS-Takt
 *     gesetzt; die Loch-Seite liest ihn, also wurde bis zu einmal pro Sekunde
 *     der halbe Bildschirm neu zusammengesetzt — samt `liveOf()` mit der
 *     Ring-Geometrie fuer Front/Mitte/Back.
 *     Neu `Live.fixUi`: dieselbe Position, aber nur weitergereicht, wenn sie
 *     um mehr als 1,5 m gewandert ist oder die Genauigkeit um mehr als 3 m
 *     springt. Darunter kann sich die angezeigte Meterzahl gar nicht aendern.
 *     Alle vier Zuweisungen laufen jetzt ueber `Live.neuerFix()` — EINE Stelle
 *     fuer die Regel. Die Oberflaeche liest `fixUi`, Messung und Caddy
 *     weiterhin `fix` (roh, ausserhalb der Composition).
 *     Dazu `liveOf()` in `remember(fix, hole, geo)`.
 *
 *  2026-08-14 (2) · TAKT: Der Abgleich ist schnell geworden, weil er klein
 *     geworden ist. Push-Schleife 30/180 s -> 10/60 s, Pull-Schleife
 *     20/120 s -> 5/30 s. Massstab fuer „schnell" ist beim Pull der
 *     BILDSCHIRM (AmbientState.isAmbient), nicht die letzte Eingabe: Wer auf
 *     die Uhr schaut, erwartet Gleichlauf mit dem Handy — auch wenn der
 *     Lochwechsel dort passiert ist und man selbst seit zehn Minuten nichts
 *     eingetragen hat. Lochwechsel setzt jetzt `lastEditMs`, sonst bliebe die
 *     Antwort des Handys im Sparbetrieb haengen.
 *     Moeglich ist das nur ueber `draft.json`: Bei 3 MB je Vorgang waeren
 *     5-Sekunden-Takte unbezahlbar gewesen.
 *
 *  2026-08-14 · DRAFT-DATEI: Runden-Sync laeuft ueber `draft.json` (wenige kB)
 *     statt ueber die 3-MB-Datei. Net.fetchDraftFile/pushDraftFile neu;
 *     fetchDraft und pushDraft nehmen sie zuerst und fallen bei aeltererm
 *     Worker (403) auf den alten Weg zurueck. gpsShots reisen mit.
 *     GEMESSEN vorher: 3 MB runter + 3 MB rauf je Eingabe (1,5 s entprellt),
 *     dazu der Pull-Takt alle 20-120 s mit 3 MB — ueber ein halbes Gigabyte je
 *     Runde und dutzendfaches Parsen von 3 MB auf der Uhr-CPU. Zusaetzlich
 *     entfaellt das serverseitige Mergen der 3 MB im Worker (Free-Tier, 10 ms
 *     CPU — ein latenter 502, bei dem ein Push still verlorenging).
 *     Erfordert Worker v2.6. Punkt 9 unten ist mit PWA v2.90 erledigt:
 *     mergeDB vereinigt gpsShots jetzt ueber die Schlag-ID (mit Grabsteinen).
 *
 *  2026-08-09 (11b) · FAHNENSTEUERUNG ENTFERNT — Gleichzug mit PWA v1.90.
 *     [KENNUNG KORRIGIERT 2026-08-25 (22): „(11)" war doppelt vergeben, und
 *      dieser Eintrag stand zwischen dem 14. und dem 11. — also ausserhalb der
 *      Reihenfolge. Die POSITION bleibt, damit Verweise stimmen; die
 *      Abweichung ist hier benannt statt stillschweigend verschoben.]
 *     Die PWA hat die tagesgenaue Fahnenlage ersatzlos gestrichen: eine
 *     Handeingabe pro Loch, die im Alltag nicht gepflegt wurde — und
 *     ungepflegte Werte verschlechtern die Rechnung, statt sie zu verbessern.
 *     Die Uhr hat die Tiefe ohnehin nur GELESEN, nie gesetzt; ohne Pflege in
 *     der PWA gaebe es hier nichts mehr zu lesen.
 *     Entfallen: Geo.pinPoint(), AppData.pins, der Zustand `pinDepth` samt
 *     Parameter in buildRoundJson/Loaded, das Feld "pins" im Runden-JSON und
 *     die drei Parser-Bloecke.
 *     targetOf() liefert jetzt IMMER die Gruenmitte. In liveOf() entfaellt die
 *     gesonderte „Fahne"-Distanz — sie waere identisch mit `mid` aus F/M/B und
 *     damit nur Rauschen auf einem kleinen Display.
 *
 *  2026-08-11 (24) · PLATZ ZUR FAHNE · Score und Putts ans Ende.
 *     (1) Neues Feld `kurzseitig`. Der groesste einzelne Score-Verlust bei
 *         mittleren Handicaps ist nicht das verfehlte Gruen, sondern die
 *         FALSCHE SEITE davon: Liegt zwischen Ball und Fahne kaum Gruen, muss
 *         der Chip punktgenau sein. Mit Platz dahinter darf er durchlaufen —
 *         ein voellig anderer Schwierigkeitsgrad.
 *         BENENNUNG: Das Feld heisst „Shortsided" — der Fachbegriff. Das ist
 *         vertretbar, weil die AUSWAHLWERTE beschreiben, was gemeint ist:
 *         „Gruen getroffen" / „Viel Platz zur Fahne" /
 *         „Wenig Platz — Fahne nah am Rand".
 *         Steht VOR den Putt-Feldern: Die Lage entscheidet sich beim
 *         Annaeherungsschlag, nicht auf dem Gruen.
 *         Angebunden: HoleEntry, Options (ANS ENDE — positionell!), Lesen,
 *         Schreiben (beide Stellen), Mergen, „leer"-Pruefung, detailCount.
 *
 *     (2) SCORE UND PUTTS stehen jetzt GANZ UNTEN auf Seite 2. Sie entstehen
 *         in der Reihenfolge des Spiels zuletzt: Tee, Annaeherung, Kurzspiel,
 *         Putts — und erst dann steht der Score fest. Vorher standen sie
 *         mittendrin, und man musste beim Eintragen zwischen den Bloecken hin
 *         und her. Wer NUR den Score erfassen will, scrollt einmal ans Ende und
 *         findet dort beides beieinander.
 *
 *  2026-08-10 (23) · PUTT-DIAGNOSE: zwei neue Felder je Loch.
 *     Bei Approaches gibt es `apprMiss` seit langem — beim Putten fehlte die
 *     Entsprechung, obwohl dort die groesste Luecke liegt. Erfassen allein
 *     haette nicht geholfen; die beiden Felder beantworten zusammen die
 *     einzige Frage, die beim Putten zaehlt: WORAN liegt es?
 *       · `puttMiss` — wohin ging der erste Putt daneben? Ueberwiegend KURZ
 *         heisst Laengenkontrolle oder zu zaghaft (in zwei Einheiten
 *         aenderbar); systematisch EINE SEITE heisst Startlinie oder
 *         Aim-Point (Technik, Tor-Drill). Zwei verschiedene Uebungen.
 *       · `puttRest` — was blieb NACH dem ersten Putt liegen? Erst damit
 *         laesst sich ein Dreiputt zuordnen: aus 12 m auf 3 m liegen gelassen
 *         ist ein Lag-Problem, aus 12 m auf 1 m und dann verfehlt ein
 *         Kurzputt-Problem. Ohne dieses Feld nicht unterscheidbar.
 *     Angebunden: HoleEntry, Lesen (optS), Schreiben (beide Stellen),
 *     Zusammenfuehren, „leer"-Pruefung, detailCount und zwei SelectRows direkt
 *     unter der Puttlaenge — dort traegt man beides im selben Moment ein.
 *     Die PWA wertet sie in `puttDiagnose()` aus (Dashboard).
 *
 *     ACHTUNG BEI `Options`: Die Klasse wird POSITIONELL konstruiert. Die
 *     beiden neuen Listen stehen deshalb AM ENDE — eine Liste in der Mitte
 *     verschoebe alle folgenden still gegeneinander.
 *
 *  2026-08-10 (22) · BUILD-FEHLER: „Unresolved reference: gps".
 *     Die Akku-Warnung las `gps` — den gibt es in GolfWatchApp aber nicht.
 *     `gps` ist der Zustand INNERHALB von HomeScreen (Z5415) und ausserdem
 *     eine lokale Variable in parsePlans (Z2162). In GolfWatchApp heisst die
 *     Quelle `gpsSource` (Z3788). Korrigiert.
 *
 *     PRUEFUNG DAGEGEN (ktcheck.py, Punkt 5c): Bezeichner, die im Rumpf
 *     benutzt werden, aber weder Parameter noch lokal noch top-level sind —
 *     UND in einer anderen Funktion vorkommen. Nur diese Einschraenkung macht
 *     die Pruefung rauschfrei; sonst meldet sie jede Bibliotheksfunktion.
 *     Mit Gegenprobe verifiziert: der Fehler wird gemeldet.
 *
 *     DREI FALLEN beim Bau dieser Pruefung — sie erzeugte zunaechst 10
 *     Fehlalarme, und eine Pruefung, die Falsches meldet, ist schlimmer als
 *     keine:
 *       · Funktionen mit AUSDRUCKSRUMPF (`fun today(): String = …`) haben
 *         keinen Block. Die Rumpfsuche griff dann auf den naechsten fremden
 *         Block zu — `today` „enthielt" plötzlich `geo`.
 *       · Parameter EINZEILIGER Signaturen (`fun svcStart(ctx: Context, …)`)
 *         wurden nicht erfasst; svcStart „benutzte" dann ein fremdes `ctx`.
 *       · Parameter VERSCHACHTELTER lokaler Funktionen, Lambda-Parameter
 *         (`{ arr -> }`) und for-Schleifenvariablen fehlten ebenfalls.
 *
 *  2026-08-10 (21) · BUILD-FEHLER: „Unresolved reference: lastSyncMs".
 *     Die Zustaende `lastEditMs`/`lastSyncMs` und die Hilfsfunktionen
 *     `buzz()`/`syncAlter()` standen NACH `syncNow()`, das sie benutzt.
 *     Lokale Deklarationen sind in Kotlin erst AB ihrer Zeile sichtbar.
 *     Verschoben: der ganze Block steht jetzt VOR syncNow().
 *     Dieselbe Falle wie bei recLiveJson (2026-08-09) — diesmal mit einer
 *     Variablen statt einer Funktion.
 *
 *     WICHTIGER NEBENFUND: Die Pruefung dagegen (ktcheck.py, Punkt 5) hatte
 *     GolfWatchApp nie angesehen. Sie suchte das Ende einer Funktion als
 *     „naechste Zeile, die auf Spalte 0 beginnt" — bei
 *         fun GolfWatchApp(
 *             ctx: Context
 *         ) {
 *     ist das die Zeile „) {". Der GANZE Rumpf wurde uebersprungen, und die
 *     Pruefung meldete stets „sauber", ohne je etwas angesehen zu haben —
 *     ausgerechnet bei der Funktion, in der beide Fehler steckten.
 *     Jetzt echte Klammerpaarung, und die Pruefung deckt `var`/`val` mit ab.
 *     Gegenprobe mit absichtlich eingebautem Fehler: wird gemeldet.
 *
 *  2026-08-10 (20) · VIER VERBESSERUNGEN AUS DER PRAXIS.
 *
 *     (2) SCHWUNGLAENGE bei der Schlagaufnahme. Die PWA lernt Schlaegerlaengen
 *         NUR aus vollen Schwuengen (`clubMeasured` filtert `swing`). Ohne das
 *         Feld zaehlte jeder auf der Uhr getrackte Schlag als voll — ein halber
 *         Wedge mit 55 statt 92 m zog die gelernte Laenge nach unten und machte
 *         die Caddy-Empfehlung systematisch ZU KURZ. Neu: `Rec.swing`,
 *         `recSwing()`, ein Chip in der Aufnahmezeile. Ein Tipp schaltet weiter
 *         (Voll -> 3/4 -> Halb -> Punch); ein Auswahlmenue waere hier ein Tipp
 *         zu viel — man steht beim Ball. „Voll" wird als null gespeichert, so
 *         wie alle Altdaten gemeint waren.
 *
 *     (3) ALTER DES LETZTEN ABGLEICHS in der Kopfzeile („⟳20s", „⟳4min").
 *         Die Uhr zieht im Sparbetrieb alle zwei Minuten, ueber das CDN koennen
 *         daraus mehr werden. Ohne Anzeige weiss man nie, ob die Zahlen von
 *         jetzt sind — und haelt einen veralteten Score fuer einen Fehler.
 *         Ab 5 Minuten in Rot: dann stimmt etwas nicht.
 *
 *     (5) ADAPTIVER SYNC-TAKT. Vorher starr 180 s (senden) und 90 s (holen),
 *         egal ob gerade etwas passierte. Jetzt: 30 s bzw. 20 s, solange eine
 *         Aufnahme laeuft oder die letzte Eingabe unter zwei Minuten her ist
 *         (`lastEditMs`), sonst 180 s bzw. 120 s. Das spart Akku UND
 *         beschleunigt genau die Momente, auf die es ankommt.
 *
 *     (6) AKKU-WARNUNG unter 20 %, EINMALIG. Achtzehn Loecher mit Dauer-GPS
 *         zehren; geht die Uhr auf Loch 15 aus, ist die halbe Runde weg. Die
 *         Meldung nennt den konkreten Ausweg (GPS-Quelle auf Handy) und
 *         wiederholt sich NICHT — eine wiederkehrende Warnung wird weggetippt
 *         und dann ganz uebersehen.
 *
 *     Dazu `buzz()`: 40 ms haptische Rueckmeldung beim Speichern eines Schlags
 *     und bei der Akku-Warnung. Auf dem Platz schaut man nicht hin; laenger als
 *     40 ms wirkt wie eine Fehlermeldung.
 *
 *     Nach dem Umbau geprueft: Klammerbilanz 0/0, HolePage 24 und PlayPager 45
 *     Parameter vollstaendig versorgt, ktcheck.py ohne Fehler.
 *
 *  2026-08-10 (19) · QUALITY-EINGABE ENTFERNT (doppelte Erfassung).
 *     `quality` trug KEINE eigene Information: In sgHole dient es nur als
 *     DRITTER Rueckfall fuer die 1.-Putt-Distanz
 *     (erfasste 1.-Putt-Distanz -> bei GIR die Restdistanz -> quality).
 *     Auf einem Loch mit Gruentreffer sind „Abstand nach dem Approach" und
 *     „Laenge des ersten Putts" dieselbe Zahl — man tippte sie zweimal ein,
 *     auf einem Bildschirm, auf dem jeder Tipp zaehlt.
 *     Entfernt: die SelectRow auf der Detailseite und der Eintrag im
 *     `detailCount` (ein Zaehler, der etwas mitzaehlt, wozu es keine Eingabe
 *     gibt, schickt den Nutzer auf die Suche).
 *     BEWUSST GEBLIEBEN: das FELD im Datenmodell samt Lesen und Schreiben —
 *     Altrunden enthalten es, und der Rueckfall in sgHole soll weiter greifen.
 *     Gepflegt wird es bei Bedarf am Handy.
 *
 *  2026-08-10 (18) · AUTO-LOCH VOLLSTAENDIG ENTFERNT.
 *     Der automatische Lochwechsel per Positionsnaehe stoerte auf dem Platz
 *     mehr als er half: beim Warten am naechsten Tee, beim Ballsuchen und auf
 *     dem Rueckweg sprang die Anzeige um — mitten in der Eingabe. Die PWA hat
 *     ihn mit v1.98 aufgegeben, die Uhr zieht nach.
 *     Entfernt an acht Stellen: der LaunchedEffect mit der 40-m-Pruefung am
 *     naechsten Abschlag, der remember-Zustand samt Einstellung
 *     `prefGetB/prefSetB("autoHole")`, die Rueckruf-Definition, der Chip
 *     „Auto-Loch" auf der Detailseite sowie Parameter und Argumente in
 *     PlayPager, ScorePage und HolePage.
 *     Gewechselt wird jetzt ausschliesslich von Hand (◀ / ▶).
 *     Nach dem Entfernen geprueft: Klammerbilanz 0/0, alle Signaturen
 *     vollstaendig versorgt (PlayPager 41, ScorePage 21, DetailPage 11,
 *     HolePage 20, HomeScreen 15 Parameter), ktcheck.py ohne Fehler.
 *
 *  2026-08-10 (17) · GRUENDLICHE PRUEFUNG der ganzen Datei (ktcheck.py).
 *     Anlass: In dieser Datei sind wiederholt Fehler entstanden, die erst der
 *     Compiler fand. Geprueft wurden genau die aufgetretenen Fehlerklassen
 *     plus ihre Nachbarn — 6848 Zeilen, 49 Funktionen, 26 Datenklassen:
 *
 *       1. Klammerbilanz, kontextsicher (Strings, VERSCHACHTELTE ${}-Vorlagen,
 *          Roh-Strings, Kommentare uebersprungen)          -> 0 / 0, sauber
 *       2. Doppelte Funktionen mit gleicher SIGNATUR       -> keine
 *       3. Rueckrufe im Rumpf ohne Deklaration            -> keine
 *          (das war der onPen-Fehler)
 *       4. Aufrufe: alle Pflichtparameter versorgt        -> keine Luecke
 *       5. Lokale Funktionen vor ihrem Aufruf             -> alle 17 korrekt
 *          (das war der recLiveJson-Fehler)
 *       6. Feldzugriffe gegen die Datenklassen            -> keine
 *          (das war der it.name-Fehler)
 *       7. JSON-Schluessel geschrieben/gelesen            -> 63 / 54, deckungsgleich
 *       8. Upload ersetzt das Repo-JSON nicht             -> korrekt
 *
 *     VIER FALLEN, in die eine naive Pruefung tappt (und die mich beim ersten
 *     Durchgang je einmal erwischt haben) — hier festgehalten, damit die
 *     naechste Pruefung sie nicht erneut als Fehler meldet:
 *       · `rotaryScrollModifier` gibt es ZWEIMAL — gueltige Ueberladung mit
 *         verschiedenen TYPEN (ScalingLazyListState / ScrollState). Ein
 *         Vergleich der Parameterzahl allein meldet sie faelschlich.
 *       · `req.onSelect(...)` ist ein FELD einer Datenklasse, kein freier
 *         Rueckruf. Punkt-Zugriffe muessen ausgeschlossen werden.
 *       · GEMISCHTE Aufrufe (positionell + ein benanntes Argument, oft
 *         `valueColor =` oder eine abschliessende Lambda) sind gueltiges
 *         Kotlin. Nur wenn ALLE Argumente benannt sind, laesst sich
 *         Vollstaendigkeit beurteilen.
 *       · `!!` ist durchweg abgesichert (if (x != null), .filter { it.ring !=
 *         null }, rec?.start != null) — kein blindes Zusichern.
 *
 *     BEWUSST LEERE catch-Bloecke: WakeLock acquire/release, removeUpdates,
 *     startService. Dort ist ein Fehlschlag folgenlos; es gibt nichts
 *     Sinnvolles zu tun. readData/cacheWrite haben einen echten Rueckfall.
 *
 *  2026-08-10 (16) · BUILD-FEHLER: „Unresolved reference: onPen".
 *     Die Strafschlaege wurden von der Detail- auf die SCORE-Seite verschoben
 *     (sie sind in der SG-Rechnung eine eigene Kategorie und gehoeren nach
 *     oben). Der Stepper-Aufruf `onPen(-1)` wanderte mit — der PARAMETER
 *     `onPen` blieb aber in der Signatur von ScorePage aus. `PlayPager` hatte
 *     ihn bereits und reichte ihn nur an DetailPage weiter.
 *     Ergaenzt: `onPen: (Int) -> Unit` in ScorePage, `onPen = onPen` am Aufruf.
 *
 *     PRUEFUNG dagegen (laeuft jetzt ueber die ganze Datei): In JEDEM
 *     Composable die im Rumpf benutzten `on…(`-Rueckrufe gegen die
 *     Parameterliste abgleichen. Ausserdem je Aufruf mit benannten Argumenten
 *     pruefen, ob alle Parameter ohne Vorgabewert versorgt sind. Beides ohne
 *     Befund — die drei gemeldeten Stellen (PickScreen, PickerScreen, Stepper)
 *     rufen POSITIONELL auf, was gueltig ist.
 *
 *  2026-08-10 (15) · ABGLEICH mit PWA v2.19. KEINE Aenderung noetig — geprueft
 *     und hier festgehalten, damit der naechste Durchgang nicht danach sucht:
 *
 *     · GRABSTEINE (PWA v2.11/2.12): Das Handy fuehrt `DB.tomb`, damit
 *       Loeschungen den Sync ueberleben. Die Uhr kennt das Feld NICHT — sie
 *       muss es auch nicht: `pushDraft` liest mit `JSONObject(readData())` das
 *       VOLLSTAENDIGE Repo-JSON, aendert nur `_draftRound`/`gpsShots` und
 *       schreibt dasselbe Objekt zurueck. Unbekannte Felder bleiben dabei
 *       unveraendert erhalten. `readData()` holt vorher ueber FRESH_URL einen
 *       frischen Stand, faellt nur im Fehlerfall auf DATA_URL zurueck.
 *       WICHTIG FUER KUENFTIGE AENDERUNGEN: Niemals ein NEUES JSONObject
 *       aufbauen und hochladen — damit waeren Grabsteine und alles andere
 *       Unbekannte weg.
 *
 *     · SCHLAEGERLISTE: Die Uhr hat KEINE eigene. Sie liest `clubDistances`
 *       und ueberspringt Eintraege ohne jede Distanz
 *       (`if (carry == null && total == null) continue`). Das betrifft auch die
 *       Auswahl beim Schlagtracken — ein frisch angelegter Schlaeger fehlt auf
 *       der Uhr, bis Carry oder Gesamtlaenge gesetzt ist. Die PWA weist seit
 *       v2.15 darauf hin.
 *       Die Namens-Vereinheitlichung `clubNorm` (PWA v2.17, „7 Iron" findet
 *       „7-Eisen") laeuft NUR im Handy — die Uhr zeigt die gepflegten Namen,
 *       und das ist richtig so.
 *
 *     · SCHWUNGLAENGE (PWA v1.98): `gpsShots[].swing` (Voll/3-4/Halb/Punch …)
 *       entscheidet, ob ein Schlag fuer die gelernte Schlaegerlaenge zaehlt.
 *       Die Uhr schreibt das Feld nicht — ihre Schlaege gelten damit als VOLL,
 *       so wie alle Altdaten. Wer auf der Uhr einen halben Wedge trackt,
 *       sollte ihn am Handy nachtragen.
 *
 *     · HOEHENRASTER (PWA v2.19) ist rein lokal (localStorage) und beruehrt
 *       den Sync nicht.
 *
 *     · Alle 23 Lochfelder werden weiterhin geschrieben (geprueft), Struktur
 *       geprueft: Klammerbilanz 0, 17 lokale Funktionen in korrekter
 *       Reihenfolge, Feldzugriffe gegen die Datenklassen sauber.
 *
 *  2026-08-09 (14) · BUILD-FEHLER: „Unresolved reference 'name'".
 *     `clubs.map { it.name }` — ClubDist heisst das Feld aber `club`:
 *         data class ClubDist(val club: String, val carry: Int?, val total: Int?)
 *     Korrigiert zu `clubs.map { it.club }`.
 *
 *     PRUEFUNG dagegen: Feldzugriffe der Form `<sammlung>….{ it.X }` gegen die
 *     Felder der zugehoerigen data class abgleichen (25 Datenklassen erfasst).
 *     Damit waere auch dieser Fehler vor dem Build aufgefallen.
 *
 *  2026-08-09 (13) · BUILD-FEHLER: „No value passed for parameter 'onCancelFetch'".
 *     ZWEI zusammenhaengende Klammerfehler, die sich gegenseitig kaschiert
 *     haben — deshalb war die Datei global ausgeglichen und der Compiler
 *     meldete etwas voellig anderes:
 *
 *     (a) Im onFetchPhone-Lambda schloss eine `}` nach `geo = parseGeo(...)`
 *         den else-Zweig ZU FRUEH. Die folgenden Zeilen benutzen aber `dc`
 *         (dc.holes, dc.name) — das geht nur dort, wo dc nicht null ist.
 *         Folge: alles danach rutschte eine Ebene heraus, und onCancelFetch
 *         samt vier weiteren Argumenten landete AUSSERHALB des
 *         HomeScreen-Aufrufs. Der Compiler meldete den Fehler an der
 *         Aufrufzeile — 150 Zeilen ueber der Ursache.
 *
 *     (b) Am ENDE von GolfWatchApp fehlte eine `}`. Die falsch platzierte
 *         Klammer aus (a) hat sie ersetzt, weshalb die Gesamtbilanz stimmte.
 *         Erst nach Behebung von (a) wurde (b) sichtbar.
 *
 *     PRUEFUNG, die das findet: eine kontextsichere Klammerbilanz, die
 *     Strings, VERSCHACHTELTE ${}-Vorlagen, Roh-Strings und Kommentare
 *     ueberspringt. Eine naive Zaehlung scheitert an Zeilen wie
 *         "${it.course.name} · ${ it.entries.values.count { e -> ... } } Loecher"
 *     weil sie die schliessende Klammer des inneren Lambdas fuer das Ende der
 *     Vorlage haelt. Zusaetzlich pruefen: liegt JEDES benannte Argument eines
 *     mehrzeiligen Aufrufs auf Klammertiefe 0? 108 Aufrufe geprueft.
 *
 *  2026-08-09 (12) · BUILD-FEHLER behoben: „Unresolved reference 'recLiveJson'".
 *     recLiveJson() stand bei recBegin() — also WEITER UNTEN als der erste
 *     Aufrufer in syncNow(). Lokale Funktionen in Kotlin sind erst AB ihrer
 *     Deklaration sichtbar; anders als Methoden eines object/class, wo die
 *     Reihenfolge egal ist. Jetzt direkt vor syncNow(), unterhalb des
 *     remember-Zustands `rec`.
 *
 *     REGEL FUER GolfWatchApp: Jede lokale `fun` MUSS oberhalb ihres ersten
 *     Aufrufers stehen. Alle 17 lokalen Funktionen wurden dagegen geprueft.
 *     Wer eine neue einfuegt, setzt sie moeglichst weit oben — direkt nach den
 *     remember-Zustaenden, die sie liest.
 *
 *  2026-08-09 (11) · ABGLEICH mit PWA v1.91. KEINE Aenderung noetig — hier
 *     festgehalten, damit der naechste Durchgang nicht danach sucht:
 *     · Der Umbau des Spielmodus auf Vollbild und dann auf eine normale
 *       Ansicht (PWA v1.69-1.91) betraf ausschliesslich die Darstellung am
 *       HANDY. Die Schnittstelle ist unveraendert: Entwurf `_draftRound`,
 *       Live-Zeiger `live` inkl. `rec` (Schlagaufnahme), Tombstone
 *       `ui.draftDiscardedTs`, Gameplan unter `DB.strat.gameplans["Kurs|Tee"]`.
 *     · Alle Lochfelder der PWA werden geschrieben (geprueft): hole, par, si,
 *       len, score, putts, tee, appr, apprMiss, apprClub, lie, distToPin,
 *       firstPutt, quality, club, bunkerN, b1, penType, ud, ss, recovery,
 *       girDirect, shots.
 *     · FAHNENSTEUERUNG: in der PWA mit v1.90 komplett entfernt (DB.pins,
 *       pinPoint, greenAxisEdges). Die Uhr hatte das bereits am 2026-08-08
 *       aufgegeben (siehe Eintrag „Entfallen: Geo.pinPoint(), AppData.pins")
 *       — beide Seiten zielen jetzt einheitlich auf die GRUENMITTE. Es gibt
 *       nichts mehr abzugleichen.
 *
 *  2026-08-09 (10) · NEUES FELD apprClub (Approach-Schlaeger).
 *     Bisher wurde nur der TEE-Schlaeger erfasst. Damit liess sich die
 *     Streuung je Schlaeger nur fuer den Abschlag lernen — nicht fuer Eisen
 *     und Wedges, aus denen die meisten Schlaege verlorengehen.
 *     Durchgezogen: HoleEntry · jsonToEntry · buildRoundJson · adoptHoles ·
 *     Auswahlzeile auf Seite 2 direkt nach der Approach-Distanz.
 *     NEUER PARAMETER `clubNames` in PlayPager und ScorePage: opts.teeClubs
 *     enthaelt nur KATEGORIEN (Driver/Holz/Hybrid/Eisen), fuer den
 *     Approach-Schlaeger braucht es die echten Namen aus der Bag
 *     (clubs.map { it.club } — das Feld heisst `club`, NICHT `name`).
 *
 *  2026-08-09 (9) · SCHLAGAUFNAHME GERAETEUEBERGREIFEND.
 *     Der Live-Zeiger `_draftRound.live` traegt jetzt zusaetzlich eine
 *     laufende Aufnahme:
 *         rec = {src, club, at, lat, lng}
 *     · Schreiben: recLiveJson() -> pushDraft(..., recLive). pushDraft kennt
 *       den Compose-Zustand nicht, deshalb als Parameter. Nur wenn bereits ein
 *       Schlaeger gewaehlt ist — vorher hat das Handy nichts anzuzeigen.
 *     · Lesen: RepoDraft.recSrc/recClub. Laeuft hier eine Aufnahme, waehrend
 *       der Zeiger vom HANDY kommt und KEIN rec mehr enthaelt, hat das Handy
 *       den Schlag abgeschlossen -> eigene Aufnahme beenden. Ohne das wuerde
 *       derselbe Schlag ZWEIMAL erfasst.
 *     · Rec traegt jetzt `at` (Startzeit) — das Handy zeigt „seit 40 s" und
 *       verwirft eine vergessene Aufnahme nach 30 Minuten.
 *     Gegenstueck in der PWA: watchRec()/watchRecBanner()/watchRecFinish().
 *     Der Startpunkt kommt von der Uhr, der Endpunkt vom Handy — beide sind
 *     am selben Ball, also ist es dieselbe Messung.
 *
 *  2026-08-08 (8) · Erfassung nach WICHTIGKEIT sortiert, nicht nach Rubrik.
 *     Seite 2 (Score) traegt jetzt alles, was die Auswertung braucht:
 *       Tee-Ergebnis · Tee-Schlaeger · Approach-Distanz ⭐ · Approach-Lage ⭐ ·
 *       Approach-Fehler ⭐ · Rest zur Fahne · Score ⭐ · Strafschlaege ·
 *       Putts ⭐ · 1. Putt ⭐
 *     Begruendung je Feld:
 *       · Approach-LAGE behebt die Fairway-Annahme der SG-Rechnung — ohne sie
 *         ist der Erwartungswert am Approach-Start zu gut angesetzt.
 *       · Approach-FEHLER aendert an SG nichts, ist aber das trainings-
 *         relevanteste Feld ueberhaupt ("systematisch zu kurz").
 *       · Strafschlaege sind eine EIGENE SG-Kategorie und werden aus dem
 *         kurzen Spiel herausgerechnet; in den Details belasteten sie es.
 *     Seite 3 enthaelt nur noch Selten-Felder und Overrides. Up&Down und
 *     Sand Save rechnet die PWA aus Score/Putts/Par/Bunkerzahl ab
 *     (holeUpDown/holeSandSave) — auf der Uhr stehen sie unter „Meist
 *     berechnet" mit Hinweis, nur noch als Korrektur.
 *
 *  2026-08-08 (7) · 1.-Putt-Distanz von Seite 3 auf Seite 2.
 *     Sie TRAEGT die Strokes-Gained-Rechnung: ohne sie liefert sgHole() nur
 *     den Gesamtwert, weil sich Putten, Kurzspiel und Annaeherung nicht
 *     trennen lassen. In den Details blieb sie auf der Runde regelmaessig
 *     leer. Steht jetzt direkt nach dem Putts-Stepper — dort, wo man sie
 *     ohnehin gerade eintraegt. Die vier SG-Pflichtfelder auf Seite 2 sind
 *     mit ⭐ markiert: Score, Putts, Approach-Distanz, 1. Putt.
 *
 *  2026-08-08 (6) · Gleichzug mit der PWA v1.57 (Strokes Gained).
 *     Drei Luecken, die den Datenaustausch still beschaedigt haetten:
 *
 *     1. par/len/si wurden je Loch GAR NICHT geschrieben. Die PWA rechnet
 *        Strokes Gained ueber sgHole(), und die Funktion liefert OHNE par
 *        nichts zurueck. Bei einer am Handy gestarteten Runde fiel das nicht
 *        auf (die PWA hat eigene Lochdaten) — eine Runde, die allein auf der
 *        Uhr laeuft, war damit fuer die gesamte SG-Auswertung wertlos.
 *        Ebenso litten Netto-Wertung (si) und Erwartungswert (len).
 *     2. NEUES FELD apprMiss (Approach-Fehlerrichtung, PWA v1.57):
 *        HoleEntry, jsonToEntry, buildRoundJson, adoptHoles und eine
 *        Auswahlzeile auf Seite 3. Ohne das haette die Uhr einen am Handy
 *        gesetzten Wert beim naechsten Push STILL geloescht —
 *        buildRoundJson schreibt nur, was das Datenmodell kennt.
 *     3. BUGFIX gir: jsonToEntry las "gir", buildRoundJson schreibt aber
 *        "girDirect" (so heisst das Feld in der PWA). Beim Uebernehmen einer
 *        Runde vom Handy ging GIR still verloren — dieselbe Fehlerklasse wie
 *        der Entwurfs-Bug aus (4). Liest jetzt girDirect mit gir als Rueckfall.
 *
 *     ZWEI FALLEN, die dabei fast zugeschnappt waeren und die man kennen muss:
 *     · jsonToEntry stand auf POSITIONSARGUMENTEN. Ein neues Feld in HoleEntry
 *       haette alle folgenden Werte um eine Stelle verschoben (Putts als
 *       Tee-Ergebnis) — ohne Absturz, ohne Warnung. Jetzt benannte Argumente.
 *     · Options wird EBENFALLS positionell konstruiert. Ein neues Feld dort
 *       braucht zwingend einen strList-Aufruf an derselben Stelle in
 *       parseData. Beim Aendern nachzaehlen: Felder == strList-Aufrufe.
 *
 *     GIR bleibt auf der Uhr bewusst OHNE Eingabe (v1.55). Die PWA leitet es
 *     ab: Score − Putts <= Par − 2. Das repariert zugleich alle Altrunden.
 *
 *  2026-08-08 (5) · KRITISCH: alle Lesezugriffe der Uhr waren defekt.
 *     openData() gab eine BEREITS VERBUNDENE HttpURLConnection zurück, weil
 *     f.responseCode den Verbindungsaufbau auslöst. Alle drei Aufrufer setzten
 *     danach requestMethod/setRequestProperty — das wirft:
 *       IllegalStateException: Cannot set request property after connection
 *     Sobald der Worker also mit 200 antwortete (Schreibtisch, guter Empfang!),
 *     schlug JEDER Lesevorgang fehl:
 *       fetchData  -> data == null  -> keine Plätze, keine Optionen
 *                     ("Runde ohne Handy" konnte nicht starten, und auf
 *                      Seite 2 fehlten Tee-Ergebnis/Tee-Schläger/Approach)
 *       fetchDraft -> Pull-Abgleich während der Runde tot
 *       pushDraft  -> der GET vor dem PUT warf, es wurde NIE geschrieben
 *                     ("Runde vom Handy" fand nichts, Uhr-Runden kamen nie an)
 *     Neu: openRead(url) setzt ALLE Eigenschaften VOR dem Verbinden, readData()
 *     liest frisch über den Worker mit Pages-CDN als Fallback. fetchRaw,
 *     fetchDraft und der GET in pushDraft nutzen ausschließlich readData().
 *
 *     Zusätzlich: loadData() verschluckte jede Exception — genau deshalb war
 *     der Defekt unsichtbar (sah aus wie "kein Netz"). Der Fehler landet jetzt
 *     in lastLoadError und wird in der Statuszeile angezeigt.
 *
 *  2026-08-08 (4) · Übergabe Handy -> Uhr reparierT (fand nichts).
 *     HAUPTFEHLER LAG IN DER PWA: maybeCheckpointDraft hatte die Bedingung
 *     "n>0 &&" — der Entwurf ging erst ab dem ERSTEN erfassten Loch ins Repo.
 *     Eine frisch gestartete Runde stand dort schlicht nicht. Fix in
 *     index.html v1.44.0 (playPublishStart bei playBegin/playContinue).
 *
 *     Auf der Uhr zusätzlich behoben:
 *     - onFetchPhone griff auf d.courses/d.pins eines NULLABLE d zu — das
 *       hätte gar nicht kompiliert. Jetzt sauber unter d != null.
 *     - side-Vokabular an ROUND_KINDS der PWA angeglichen:
 *       "18 Loch" | "Front 9" | "Back 9" (vorher "Vorne 9"/"Hinten 9",
 *       womit activeHoles() am Handy ins Leere gelaufen wäre), und
 *       type = "9 Loch" statt einer Kopie von side.
 *     - Lochfilter identisch zu activeHoles(): <=9 bzw. >=10.
 *     - Übernahme vom Handy grenzt die Lochliste jetzt auf side ein und
 *       sucht den Index gegen die GEFILTERTE Liste (sonst falscher Einstieg
 *       bei einer Back-9-Runde).
 *     - Statuszeile sagt beim Suchen, woran es liegt (kein Netz / keine
 *       Runde im Repo / nur eigener Entwurf / Platz unbekannt) statt zwei
 *       Minuten stumm "suche…" zu zeigen.
 *
 *  2026-08-08 (3) · ROLLENTAUSCH: Handy startet, Uhr übernimmt.
 *     REGRESSION AUS (2) BEHOBEN: der neue Heartbeat schreibt _draftRound
 *     schon beim Betreten der Runde — auch ohne erfasstes Loch. onNew prüfte
 *     aber nur "Entwurf von heute vorhanden?" ohne Score- oder Herkunfts-
 *     kriterium und sprang deshalb bei JEDEM Start still in diese Altrunde.
 *     Von außen: "Neue Runde tut nichts".
 *
 *     - Zwei getrennte Wege auf dem Startbildschirm:
 *       onFetchPhone  = Runde vom Handy holen (Normalfall). Sucht NUR nach
 *                       Entwürfen mit RepoDraft.fromPhone (roundId gesetzt
 *                       ODER live.src == "phone"), pollt sofort und dann alle
 *                       10 s für höchstens 2 Minuten, abbrechbar. Übernimmt
 *                       Platz, Tee, Umfang, roundId und das Loch der Uhr.
 *       onNew         = Alleinstart. Geht IMMER direkt zur Platzauswahl und
 *                       sieht sich überhaupt keine Entwürfe an.
 *     - roundId: nur die PWA vergibt sie. Die Uhr übernimmt sie beim Holen,
 *       hält sie in saveLocal/loadLocal und schreibt sie in buildRoundJson
 *       zurück — sonst legt die PWA beim Speichern eine ZWEITE Runde an.
 *       Damit ist zugleich die Voraussetzung für die Commit-Chips geschaffen.
 *     - Alleinstart ist kein Rumpf mehr:
 *       * parseData legt jetzt JE TEE einen CourseDef an (vorher nur der
 *         erste Schlüssel aus tees{} — der Abschlag war nicht wählbar).
 *       * Rundenumfang 18 Loch / Vorne 9 / Hinten 9 als Chip in PickScreen;
 *         die Löcher werden gefiltert und side wandert in buildRoundJson
 *         (war vorher hart auf "18 Loch" verdrahtet).
 *     EDS/countHcp bleibt beim Alleinstart false — dafür fehlt der Uhr das
 *     Setup der PWA. Wer eine EDS-Runde spielt, startet sie am Handy.
 *
 *  2026-08-08 (2) · Startbildschirm: Trägheit + falsche Zustände behoben.
 *     Alle vier Symptome hingen an zwei Ursachen.
 *
 *     URSACHE 1 — Stammdaten kamen NUR aus dem Netz, ohne Cache:
 *       - Ohne Empfang war data == null, also opts == null. Damit fehlten
 *         Tee-Ergebnis, Tee-Schläger und Approach-Distanz auf Seite 2
 *         KOMPLETT (der if(opts != null)-Block wurde übersprungen).
 *       - "Neue Runde" endete bei fehlendem Netz in einer Statuszeile,
 *         die Runde startete gar nicht.
 *       - "Fortsetzen" wartete erst einen Netz-Timeout ab.
 *       FIX: fetchData() aufgeteilt in fetchRaw() + parseData(). Der Rohtext
 *       liegt als Datei in filesDir (DATA_CACHE); loadData(ctx) versucht Netz
 *       und fällt sonst auf den Cache zurück. Beim Start werden die Daten
 *       SOFORT aus dem Cache gesetzt und danach im Hintergrund aktualisiert.
 *       Neues Flag dataFresh unterscheidet frisch/offline in der Statuszeile.
 *
 *     URSACHE 2 — unnötige Recompositions und toter Zustand:
 *       - "val fix = Live.fix" wurde UNBEDINGT gelesen. Jeder GPS-Tick (1/s)
 *         setzte damit ganz GolfWatchApp neu zusammen, auch auf dem
 *         Startbildschirm -> stockende Anzeige, träge Buttons.
 *         FIX: nur noch lesen, wenn screen == "play".
 *       - keepScreen/gpsSource wurden bei JEDER Recomposition frisch aus den
 *         SharedPreferences gelesen. Jetzt als remember-State gehalten.
 *       - resume war "val resume = remember{ loadLocal(ctx) }" — einmalig und
 *         nie invalidiert. "Verwerfen" löschte die Datei, der Fortsetzen-
 *         Button blieb aber stehen und arbeitete mit der gelöschten Runde.
 *         FIX: mutableStateOf; onDiscard setzt resume = null und räumt
 *         entries/measurements/course/geo/idx/roundStart mit auf. Ebenso bei
 *         Rundenstart (Platzauswahl und Übernahme vom Handy).
 *
 *  2026-08-08 · LIVE-ZEIGER Uhr <-> Handy (Spielmodus parallel):
 *     Gemeinsamer Vertrag in _draftRound.live:
 *       {src:"watch"|"phone", hole, at, course, tee, date, side}
 *     - pushDraft(round, shots, currentHole, courseName, teeName) schreibt ihn
 *       bei JEDEM Push und liest zugleich den Zeiger des Handys. Ist dessen
 *       "at" jünger als der zuletzt SELBST gesendete (ownLiveAt), kommt das
 *       Loch als PushResult.remoteHole zurück und syncNow() stellt idx um.
 *     - Die 90-s-Pull-Schleife wertet ihn ebenfalls aus (RepoDraft.liveSrc/
 *       liveHole/liveAt), damit die Uhr dem Handy auch ohne eigenen Push folgt.
 *     - HERZSCHLAG: beim Betreten der Runde sofort und danach alle 3 Minuten
 *       ein Push, auch ohne Eingabe. NUR daran erkennt das Handy überhaupt,
 *       dass eine Runde läuft — buildRoundJson lässt leere Löcher weg, also
 *       war der Entwurf vor dem ersten Score komplett leer.
 *     - ownLiveAt verhindert, dass das eigene Echo die Uhr zurückwirft.
 *
 *     GEGENSTÜCK in index.html (v1.32.0): playLivePush beim Blättern,
 *     playAdoptRemoteHole in playSyncTick, watchLiveMaybeOpen öffnet den
 *     Spielmodus automatisch, mergeDB behält den live-Zeiger nach .at.
 *
 *  2026-08-07 (5) · Bedienprobleme aus dem Feldtest:
 *     - Seite 1 war zu hoch für das Display, die Schlag-Zeile rutschte
 *       unten heraus. Column jetzt verticalScroll + Krone; zusätzlich
 *       kompakter: große Zahl 52->44sp, "target→plays" und die Einfluss-
 *       Anteile in EINER Zeile, Score-Chip unten entfernt (der Score steht
 *       jetzt in der Kopfzeile, zur Score-Seite kommt man per Wisch).
 *       ColumnScope.weight() ist in einer scrollbaren Column unzulässig ->
 *       durch feste Spacer ersetzt.
 *     - Wisch von Seite 1 landete oft auf Seite 3: HorizontalPager bekommt
 *       PagerDefaults.flingBehavior mit PagerSnapDistance.atMost(1), damit
 *       eine Geste genau einen Seitenschritt macht.
 *     - Seite 2 schien ohne Tee-Ergebnis/Tee-Schläger zu starten: die Liste
 *       behielt die Scrollposition vom letzten Besuch. Neuer LaunchedEffect
 *       auf pagerState.currentPage springt beim Betreten an den Anfang.
 *     - Tee-Ergebnis wird auf Par 3 nicht mehr ausgeblendet, sondern als
 *       "– (Par 3)" gezeigt (wie die PWA). Verschwinden wirkte wie ein Bug.
 *     - rotaryScrollModifier bekommt ein active-Flag und eine Überladung
 *       für ScrollState. Im Pager sind mehrere Seiten gleichzeitig
 *       komponiert; vorher forderten alle den Fokus an und die Krone
 *       scrollte eine Liste, die man gar nicht sah.
 *
 *  2026-08-07 (4) · Abgleich mit index.html (PWA v1.31):
 *     - BUGFIX Sync: der Entwurf schrieb "gir", die fertige Runde aber
 *       "girDirect". Die PWA kennt nur girDirect (playDetailsHtml) — beim
 *       Fortsetzen einer Watch-Runde am Handy fiel der Wert still weg.
 *     - Seite 2 in der Reihenfolge der PWA-Erfassung: Tee-Ergebnis,
 *       Tee-Schläger, Approach-Distanz, Rest zur Fahne, Score, Putts.
 *     - "Rest zur Fahne" ist jetzt eine Auswahl (DIST_TO_PIN_CHOICES)
 *       statt Stepper. Die PWA hat dort ein freies Zahlenfeld (playNum);
 *       auf der Uhr nicht tippbar, deshalb Stützstellen — der gespeicherte
 *       Wert bleibt ein Integer im selben Feld distToPin.
 *     - GIR (girDirect) und Tee-Schläger raus aus DetailPage.
 *     - Grünmaße auf Seite 1 wiederhergestellt (waren beim Pager-Umbau
 *       verlorengegangen; die PWA zeigt "Grün ca. X m tief · Y m breit").
 *     - onDist entfallen (Stepper weg), onGir entfallen (GIR weg).
 *
 *     NOCH OFFEN gegenüber der PWA — beides braucht neue Sync-Strukturen:
 *       (a) Commit-Chips (stratCommitHtml: Plan ✓/✗ + 😐/🙂/💪) schreiben
 *           nach DB.strat.commits mit Schlüssel roundId|hole|shot. Die Watch
 *           kennt KEINE roundId — ohne die passen die Einträge nicht zu der
 *           Runde, die die PWA speichert.
 *       (b) "Fahne heute" (pinCtrlHtml: Vorne/Mitte/Hinten + Slider). Die
 *           Watch LIEST DB.pins bereits (AppData.pins), kann sie aber nicht
 *           setzen; dafür fehlt ein Schreibpfad analog zu gpsShots.
 *     ABWEICHUNG (bewusst): Penalty-Anzahl steht in der PWA in der
 *       Schnelleingabe neben Score/Putts, auf der Uhr in den Details.
 *
 *  2026-08-07 (3) · Seitenaufteilung geschärft + Einfluss-Transparenz:
 *     - SwipeToDismissBox um den Pager; HorizontalPager bekommt
 *       edgeSwipeToDismiss(dismissState). Die Wear-Zurück-Geste greift damit
 *       nur noch am LINKEN Displayrand (mit Mitlauf-Animation), der Rest der
 *       Fläche blättert. Randwisch führt OHNE Nachfrage zur Übersicht — die
 *       Geste ist bewusst; die Doppelabfrage gilt weiter für die Seitentaste.
 *     - Seite 1 (HolePage): die ≈-Zeile ist jetzt eine RECHNUNG
 *       "target → plays m" mit Aufschlüsselung Wind / 🌡 / Lage darunter.
 *       Wind- und Temperaturanteil werden AUCH bei 0 angezeigt (gedimmt),
 *       weil sonst "kein Einfluss" und "keine Daten" gleich aussehen.
 *       Vorher war tempM/lieM gar nicht sichtbar und windM nur als km/h.
 *     - Seite 2 (ScorePage): 2×3-Score-Raster ENTFERNT, stattdessen
 *       Score-Stepper (+/−) mit Par-Relation als Klartext darunter.
 *       NEU auf dieser Seite: Approach-Länge und "Rest zur Fahne m"
 *       (+ GPS-Übernahme) — beides gehört zum Kern der SG-Auswertung.
 *     - Seite 3 (DetailPage): Dopplungen raus — Approach-Länge,
 *       Pin-Distanz, GPS-Chip und die Score-Korrektur entfallen hier.
 *       Approach-LAGE bleibt (anderes Feld), SectionLabel jetzt
 *       "Bunker & Strafen".
 *     - Entfallene Callbacks: onScoreSet, onPuttsSet, onTeeSet (nur vom
 *       gelöschten Raster/Wizard gebraucht). ScorePage bekommt dafür
 *       onScore, onDist, onDistFromGps.
 *     - NEUE IMPORTS: androidx.wear.compose.foundation.{SwipeToDismissValue,
 *       edgeSwipeToDismiss, rememberSwipeToDismissBoxState}
 *       (wear-compose >= 1.2; SwipeToDismissBox kommt aus material.*).
 *
 *  2026-08-07 (2) · GAMEPLAN (Phase 5) aus dem Parallelstand übernommen:
 *     - PlanHole(club, desc) + AppData.plans ("<Kurs>|<Tee>" -> Loch -> Plan).
 *     - Repo.parsePlans(db) liest DB.strat.gameplans; fetchData reicht es durch.
 *     - HolePage zeigt die 📋-Zeile ÜBER der Caddy-Zeile (🎯).
 *       ABWEICHUNG zum alten Stand: die Zeile hängt NICHT mehr an
 *       plan != null. Ohne GPS-Fix gibt es keinen Caddy-Plan — und genau
 *       dann (auf dem Abschlag) ist der Gameplan die einzige Empfehlung.
 *       Farbe PineText statt GoldText, damit Gameplan und Caddy sich
 *       auf einen Blick unterscheiden.
 *
 *  2026-08-07 · NAVIGATION: Zurück-Hierarchie + 3-Seiten-Pager
 *     - MainActivity: globaler OnBackPressedCallback ENTFERNT. Er war immer
 *       aktiv und rief finish() — deshalb schloss Wischen die App aus JEDER
 *       Ebene und man kam aus der Detailansicht nicht zurück.
 *     - Neu: zentraler BackHandler in GolfWatchApp mit echter Hierarchie:
 *       Picker -> Seite 0 -> (2× wischen) Übersicht -> App schließen.
 *       Die Runde läuft beim Verlassen im Service weiter und steht auf dem
 *       Startbildschirm als „Fortsetzen" bereit.
 *     - PlayScreen + PlayDetailScreen ERSETZT durch PlayPager mit drei
 *       horizontal wischbaren Seiten: HolePage / ScorePage / DetailPage.
 *       pagerState + scoreListState liegen in GolfWatchApp, damit ein Picker
 *       die Seite nicht zurücksetzt (gleicher Fehler wie vorher showDetails).
 *     - showDetails ENTFALLEN: die Variable schaltete gleichzeitig den Screen
 *       um UND den Feldblock auf/zu, weshalb der „Details ▾"-Chip einen
 *       aus dem Screen warf statt zuzuklappen.
 *     - PickerScreen: „‹ Abbrechen" ergänzt (onCancel). Vorher war „(leer)"
 *       der einzige Ausgang — der löscht aber den Wert, statt ihn zu behalten.
 *     - screen = "home" bei course == null wandert in LaunchedEffect
 *       (war ein State-Write während der Composition).
 *     - Lochwechsel bleibt auf Buttons (ScorePage), NICHT auf der Wischgeste:
 *       zwei Bedeutungen pro Richtung sind mit Handschuh nicht lernbar.
 *     - WizBtn: Haptik bei Tap und Long-Press.
 *     - Entfallen (in den Seiten aufgegangen): LiveBlock, CaddyBlock,
 *       ShotBlock, QuickScoreRow, Wizard-States wiz/wizAskPutts.
 *     - NEUE ABHÄNGIGKEIT: androidx.compose.foundation.pager.HorizontalPager
 *       (compose-foundation >= 1.6, für rememberPagerState{ pageCount })
 *       und androidx.wear.compose.material.HorizontalPageIndicator.
 *
 *  2026-08-06 (4) · Usability- & Design-Optimierung (siehe Doku 3b):
 *     - QuickScoreRow: 1-Tap-Score (Par−1…Par+2) + neuer Callback onScoreSet
 *       (führt über change()); Score-Stepper-Wert farbig relativ zu Par.
 *     - DETAILS einklappbar (Chip mit Zähler); showDetails in GolfWatchApp.
 *     - ListStates gehoistet (home/pick/play/picker): Scroll-Position bleibt
 *       bei Picker-Nutzung erhalten; PositionIndicator im Scaffold;
 *       rotaryScrollModifier() für Drehkrone/Lünette; Scroll-to-top bei
 *       Lochwechsel; Picker/Kursliste starten oben.
 *     - Bestätigungs-Tap (4-s-Fenster) für „Verwerfen" und „Abschließen";
 *       Abschluss-Chip zeigt „N von M Löchern erfasst".
 *     - Haptik in Stepper/QuickScoreRow; SectionLabel mit Zierlinien.
 *     - Keine neuen Permissions/Abhängigkeiten; Datenmodell & Sync unverändert.
 *  2026-08-06 (3) · Anordnung:
 *     - GIR von KERN nach DETAILS (erste Zeile dort).
 *     - Abschnitt SCHLÄGE ans Ende verschoben, direkt oberhalb von RUNDE.
 *  2026-08-06 (2) · UI-Feinschliff:
 *     - „über Par" von ganz oben zu den Loch-Infos verschoben (war am runden
 *       Displayrand praktisch unsichtbar). (PlayScreen-Kopf)
 *     - Grün-Maße neu: Geo.greenDims() = Tiefe (entlang Spiellinie) x Breite,
 *       angezeigt im Live-Block — auch ohne GPS-Fix, da nur kartenabhängig.
 *       (PlayLive.greenDepth/greenWidth, LiveBlock)
 *     - Pin-Distanz (inkl. „aus GPS") und Penalty-Anzahl von DETAILS nach KERN.
 *  2026-08-06 · Großer Ausbau:
 *     - FOREGROUND-SERVICE RoundService (Typ location, Notification, WakeLock):
 *       App/Erfassung läuft im Hintergrund weiter. (svcStart/svcStop/svcNote)
 *     - LIVE-TRACKING auf der Uhr, während der Runde immer an: Live-Singleton,
 *       F/M/B zum Grün, Fahnentiefe aus DB.pins, GPS-Genauigkeit, Auto-Loch.
 *     - VIRTUELLER CADDY: Caddy.plan() mit Wetter (Open-Meteo, „spielt wie"),
 *       Gefahren auf der Spiellinie, Schlägerwahl aus clubDistances, Modi.
 *     - SCHLAGTRACKING: shots je Loch (PWA-Schema {lat,lng,club}), Kette,
 *       Live-Meter, zusätzlich gpsShots-Messungen für die Schlägerlängen-DB.
 *     - Geo-Daten je Platz werden lokal gecacht -> Live/Caddy offline nutzbar.
 *     - Neue Permissions/Manifest-Einträge nötig (siehe Block „MANIFEST").
 *  2026-08-05 · Umbau:
 *     - Jede Eingabe sichert sofort lokal + entprellter Repo-Sync (statt nur
 *       alle 3 Löcher). Loch-Wechsel/Abschluss pushen sofort. (change/syncNow/
 *       scheduleSync; „checkpoint %3" entfernt.)
 *     - IMMER-AN (3 Schichten): (1) AmbientLifecycleObserver — App bleibt beim
 *       Handgelenk-Senken gedimmt SICHTBAR statt geschlossen (androidx.wear).
 *       (2) keepScreenOn dynamisch: während Live.running IMMER erzwungen
 *       (GolfWatchApp/LocalView), Toggle wirkt nur ohne Runde. (3) Wisch-nach-
 *       rechts während einer Runde = erst Toast, zweiter Wisch binnen 2 s
 *       schließt (OnBackPressedCallback in MainActivity).
 *     - GPS-QUELLE (Home-Chip "GPS-Quelle", pref "gpsSource" watch|phone):
 *       watch = LocationManager der Uhr (wie bisher). phone = Fused Location
 *       (Play Services) -> nutzt über Bluetooth das Handy-GPS, spart Uhr-Akku,
 *       fällt ohne Handy selbstständig auf die Uhr zurück. Umschalten wirkt
 *       sofort (svcGpsRestart -> ACTION_GPS -> stopGpsOnly + startTracking;
 *       Foreground/WakeLock bleiben stehen). Live.src zeigt die aktive Quelle.
 *     - FLAG_KEEP_SCREEN_ON in onCreate -> App bleibt im Vordergrund.
 *     - Kopf zeigt immer „über Par" (overPar()); Rundentimer entfernt (fmtDur weg).
 *     - Diese eingebettete Doku + Changelog eingeführt.
 *  (frühere Historie: erste Wear-Version mit voller Feld-Parität, Draft-Push
 *   alle 3 Löcher, Live-Rundentimer.)
 *
 *  ------------------------------------------------------------------------
 *  MANIFEST (AndroidManifest.xml — MUSS so ergänzt sein, sonst Absturz/kein GPS)
 *  ------------------------------------------------------------------------
 *  <uses-permission android:name="android.permission.INTERNET"/>
 *  NEUE GRADLE-ABHÄNGIGKEITEN (build.gradle[.kts], Modul "app") — PFLICHT:
 *      implementation("androidx.wear:wear:1.3.0")                      // Ambient/Always-on
 *      implementation("com.google.android.gms:play-services-location:21.3.0")  // Handy-GPS
 *  EMPFOHLEN im Manifest an der Activity: android:launchMode="singleTask"
 *  (Notification-Tipp kehrt dann immer in die laufende Instanz zurück.)
 *
 *  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
 *  <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
 *  <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
 *  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
 *  <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
 *  <uses-permission android:name="android.permission.WAKE_LOCK"/>
 *  <uses-feature android:name="android.hardware.location.gps" android:required="false"/>
 *  <!-- Schwungerkennung (2026-08-15): Beschleunigung und Gyroskop brauchen
 *       KEINE Laufzeitberechtigung. `HIGH_SAMPLING_RATE_SENSORS` erst noetig,
 *       wenn ueber 200 Hz abgetastet wird — SENSOR_DELAY_GAME liegt darunter.
 *       Die Uhr ohne Gyroskop faellt still auf Handbetrieb zurueck. -->
 *  (Die beiden Sensor-Eintraege fuer Beschleunigung und Gyroskop koennen mit
 *   Fassung 40 aus dem Manifest, wenn sie dort noch stehen — die
 *   Schwungerkennung ist geloescht.)
 *  … innerhalb von <application>:
 *  <service
 *      android:name=".presentation.RoundService"
 *      android:exported="false"
 *      android:foregroundServiceType="location"/>
 * =========================================================================== */

/* Fassungskennung der Uhr-App — steht im Kopplungstest neben der der PWA.
   Bei JEDER Aenderung hier mitziehen; sonst vergleicht man zwei Staende und
   glaubt, sie seien gleich (2026-08-15 (13)). */
private const val WATCH_APP = "2026-08-30 (55)"
/* ==========================================================================
   WAS HAT DIESE FASSUNG GEAENDERT? (2026-08-25 (22))
   --------------------------------------------------------------------------
   Die Uhr schickt ihre Kennung seit (6) mit. Sie beantwortet „WELCHE Fassung
   laeuft", nicht „WAS hat sie geaendert" — und das war diese Woche mehrfach
   die zweite Frage, jedes Mal mit einem Umlauf verbunden.
   HIER STEHT DER EINE ABSATZ, nicht die 1993 Zeilen des Kopfkommentars: Der
   ganze Changelog gehoert in die Datei, wo man ihn durchsuchen kann; auf die
   Reise geht, was die aktuelle Fassung ausmacht.
   VON HAND GEPFLEGT, ausdruecklich. Automatisch aus dem Kopf zu schneiden
   waere verlockend und falsch: Der Kopf ist ein Kommentar, und ein Programm,
   das seine eigenen Kommentare liest, bricht beim naechsten Umbau lautlos.
   Der Pruefstand haelt stattdessen fest, dass beides zusammenpasst. */
private const val WATCH_NOTE =
    "Runder Schlag-Knopf oben links. Runde anlegen, abschliessen und " +
    "verwerfen nur noch am Handy (braucht PWA 4.86)."

private const val WORKER_URL = "https://golftraining-save.larsdohrmann24.workers.dev"
/* DER RUNDENENTWURF ALS EIGENE, KLEINE DATEI (2026-08-14, Worker ab v2.6)
   ---------------------------------------------------------------------
   In trainingsdaten.json liegen zwei Sorten Daten: die Trainingsdatenbank ist
   KALT und GROSS (~3 MB, aendert sich selten), der Rundenentwurf HEISS und
   WINZIG (wenige kB, aendert sich alle paar Sekunden). Solange beide in
   derselben Datei liegen, muss jede heisse Aenderung die ganze kalte Menge
   bewegen: 3 MB lesen, mergen, 3 MB schreiben — nach JEDER Eingabe, dazu ein
   Pull-Takt mit weiteren 3 MB. Eine aktive Runde lag damit ueber einem halben
   Gigabyte durch das Uhrmodem, und der Worker musste 3 MB serverseitig parsen
   (Free-Tier, 10 ms CPU — ein latenter 502, bei dem ein Push still verloren
   geht).
   draft.json enthaelt NUR {round, ts, live, gpsShots} — wenige kB. Angelegt
   werden muss nichts: Das erste PUT ohne SHA erzeugt die Datei.
   RUECKFALL: Kennt der Worker den Pfad nicht (403) oder gibt es die Datei noch
   nicht (404), gehen beide Funktionen den alten Weg ueber die grosse Datei.
   Worker, PWA und Uhr werden getrennt ausgerollt — jede Reihenfolge muss
   funktionieren. */
private const val DRAFT_PATH = "draft.json"
private const val DRAFT_FRESH_URL = "$WORKER_URL/?fresh=1&path=draft.json"
/* `DATA_URL` und `FRESH_URL` entfernt (2026-08-28 (49)) — beide zeigten auf die
   GROSSE `trainingsdaten.json`, und die liest die Uhr nicht mehr. Sie war der
   Kern des OutOfMemory-Absturzes vom 28.08.; Begruendung in (48) und (49).
   Die Uhr liest nur noch `watch.json`, `draft.json` und `probe.json`. */
private const val WRITE_KEY = "@Hallo"

// ================= Design (an HTML-App angelehnt: Pine-Grün / Gold) =================

private val Pine = Color(0xFF2E7D52)
private val PineDeep = Color(0xFF1C4F36)
private val PineText = Color(0xFF6FE3A6)   // helles Grün: gut lesbar auf dunklem Grund
private val Gold = Color(0xFFCBA23A)
private val GoldText = Color(0xFFE7C56A)    // helleres Gold für Text/Labels
private val GoldDeep = Color(0xFF8A6A1E)
/* WARNFARBE (2026-08-12 aufgehellt). #C65B4E kam auf Schwarz nur auf etwa
   3,3:1 Kontrast — unter den 4,5:1, die fuer kleinen Text gefordert sind. Genau
   diese Farbe traegt „⚠ warn" und den veralteten Sync-Hinweis, also die zwei
   Dinge, die einen bei Sonnenlicht auf dem Fairway erreichen MUESSEN. Der
   neue Ton liegt bei rund 8:1 und bleibt eindeutig als Warnung erkennbar. */
private val RedC = Color(0xFFFF8A7A)
private val BgC = Color(0xFF0E1411)
private val SurfaceC = Color(0xFF18211C)
private val InkC = Color(0xFFEAF1EC)
private val InkFaint = Color(0xFFB6C7BC)    // etwas heller für kleine Beschriftungen

private val GolfColors = Colors(
    primary = Pine,
    primaryVariant = PineDeep,
    secondary = Gold,
    secondaryVariant = GoldDeep,
    background = BgC,
    surface = SurfaceC,
    error = RedC,
    onPrimary = Color.White,
    onSecondary = Color(0xFF201A00),
    onBackground = InkC,
    onSurface = InkC,
    onError = Color.White
)

// ================= Datenmodelle =================

data class HoleDef(
    val hole: Int,
    val par: Int,
    val si: Int = 0,     // Stroke-Index / Loch-Handicap
    val len: Int = 0     // Länge in Metern
)

/* KEINE PLATZGEOMETRIE MEHR IM DATENMODELL (2026-08-26 (40)).
   `geoRaw` und `geoObj` sind entfallen. Sie trugen das Roh-JSON der Platzkarte
   — ein paar hundert kB je Platz, gesichert in den SharedPreferences, damit
   Live-Distanzen und Caddy offline funktionieren. Beides gibt es nicht mehr.
   Eine Karte zu speichern, die niemand liest, kostet Speicher, Startzeit und
   jedes Mal beim Sichern eine Serialisierung des groessten Datenteils. */
data class CourseDef(
    val name: String,
    val tee: String,
    val holes: List<HoleDef>
)

data class Options(
    val teeResults: List<String>,
    val approachBuckets: List<String>,
    val teeClubs: List<String>,
    val approachLies: List<String>,
    /* Fehlerrichtung des Approach (PWA v1.57). ACHTUNG: Options wird
       POSITIONELL konstruiert — ein neues Feld hier braucht zwingend einen
       strList-Aufruf an DERSELBEN Stelle in parseData, sonst verschieben sich
       alle folgenden Listen still gegeneinander. */
    val approachMiss: List<String>,
    val firstPuttDist: List<String>,
    val qualityOpts: List<String>,
    val bunkerTypes: List<String>,
    val penaltyTypes: List<String>,
    /* ANS ENDE ANGEHAENGT. Options wird POSITIONELL konstruiert — eine neue
       Liste in der Mitte verschoebe alle folgenden still gegeneinander. */
    val puttMissOpts: List<String>,
    val puttRestOpts: List<String>,
    /* ANS ENDE — Options wird POSITIONELL konstruiert. */
    val kurzseitigOpts: List<String>
)

// Schlägerlänge aus DB.clubDistances (carry/total in Metern)
data class ClubDist(
    val club: String,
    val carry: Int?,
    val total: Int?
) {
    val reach: Int get() = total ?: carry ?: 0
    val carryOrReach: Int get() = carry ?: total ?: 0
}

// Laufender Runden-Entwurf aus dem Repo (z. B. am Handy gestartete Runde)
data class RepoDraft(
    val course: String,
    val date: String,
    val side: String,
    val ts: String,
    val holes: org.json.JSONArray,
    // Live-Zeiger aus _draftRound.live: welches Gerät steht auf welchem Loch
    val liveSrc: String? = null,
    val liveHole: Int? = null,
    val liveAt: String? = null,
    /* Letzte EIGENE Lochwahl des Handy-Benutzers (PLAY.holeAt, PWA ab v4.78).
       Fehlt bei aelterer PWA — dann greift die alte at-Regel (siehe
       Net.fremderZeigerZaehlt). */
    val liveWahlAt: String? = null,
    /* Lochwahl-ZAEHLER (PWA ab v4.79, siehe (34)): hoehere Nummer = juengerer
       Benutzerwille. Fehlt er, greifen wahlAt bzw. at als Netz. */
    val liveHoleSeq: Int? = null,
    /* Namen der Mitspieler — vergibt das Handy, die Uhr uebernimmt (37/42).
       `null` UND LEERE LISTE SIND VERSCHIEDENE AUSKUENFTE, und der Unterschied
       traegt seit (42) die ganze Regel:
         null        = der Schluessel stand nicht im Entwurf. Keine Aussage —
                       die Uhr behaelt, was sie hat.
         emptyList() = das Handy sagt ausdruecklich „keine Mitspieler".
                       Die Uhr raeumt ihre Zeilen weg.
       Die PWA schreibt das Feld IMMER (`playRound()` -> `mitspieler:[...]`),
       auch leer. Ein Entwurf ohne den Schluessel stammt also von der Uhr
       selbst oder aus einer aelteren Fassung. */
    val mitspieler: List<String>? = null,
    // round.id — NUR das Handy vergibt eine roundId. Sie ist damit zugleich
    // das Erkennungsmerkmal "diese Runde kommt vom Handy" und der Schlüssel,
    // unter dem die PWA Commits/Schläge ablegt.
    val roundId: String? = null,
    val tee: String? = null,
    /* Laufende Schlagaufnahme des ANDEREN Geraets (live.rec). Fehlt sie,
       obwohl die Uhr gerade aufnimmt, hat das Handy den Schlag gespeichert —
       dann muss die Uhr ihre eigene Aufnahme beenden, sonst wird derselbe
       Schlag doppelt erfasst. */
    val recSrc: String? = null,
    val recClub: String? = null,
    /* ==================================================================
       QUITTIERTE SCHLAEGE UND RUNDENENDE (2026-08-24 (7))
       --------------------------------------------------------------------
       Der erste Versuch las diese Felder direkt mit `dr?.optJSONArray(...)` —
       aber `dr` ist ein `RepoDraft`, KEIN `JSONObject`. Der Compiler meldete
       „Unresolved reference 'optJSONArray'".
       Hier gehoeren sie hin: `parseDraft` liest den JSON EINMAL und macht
       daraus getippte Felder. Wer daneben noch einmal roh liest, hat zwei
       Stellen, an denen ein Feldname stehen kann — und irgendwann stehen dort
       zwei verschiedene. */
    val shotAck: List<String> = emptyList(),
    val doneAt: String? = null,
    val doneCourse: String? = null,
    /* ==========================================================================
       EMPFEHLUNG DES HANDYS (2026-08-16 (3)) — samt Ort und Zeit, fuer die sie
       gilt. Ohne beides waere sie gefaehrlich: Nach dreissig Schritten stimmt
       sie nicht mehr.
       AM ENDE, UND ZWAR ZWINGEND (Korrektur 2026-08-16 (6)): `parseDraft` baut
       den Entwurf mit POSITIONS-Argumenten. Diese Felder standen zuerst in der
       Mitte — damit rutschte jeder nachfolgende Wert eine Stelle weiter, und
       der Bau brach mit zehn „Argument type mismatch" ab. Dieselbe Falle ist
       bei `Caddy.Plan` bereits vermerkt; sie gilt fuer JEDE Datenklasse, die
       irgendwo positional gebaut wird. Neue Felder deshalb IMMER ans Ende. */
    val caddyClub: String? = null,
    val caddyRest: Int? = null,
    val caddyPlays: Int? = null,
    val caddyHole: Int? = null,
    val caddyLat: Double? = null,
    val caddyLng: Double? = null,
    val caddyAt: String? = null
) {
    val fromPhone: Boolean get() = roundId != null || liveSrc == "phone"
}

/* `PlanHole` entfernt (40) — mit der Gameplan-Ansicht. Die Plaene stehen in
   der PWA, wo sie auch gefasst werden. */

data class AppData(
    val courses: List<CourseDef>,
    val opts: Options,
    val hi: Double?,
    val clubs: List<ClubDist>,
    val draft: RepoDraft? = null,   // laufende Handy-Runde (falls vorhanden)
    // "<Platz>|<Tee>" -> Loch -> Plan (nur club + targetDesc, bewusst schlank)
)

// Eine getrackte Position (PWA-Schema: shots = Liste von Positionen)
data class ShotPt(
    val lat: Double,
    val lng: Double,
    val club: String = ""
)

/* ==========================================================================
   MITSPIELER (2026-08-26 (37))
   --------------------------------------------------------------------------
   Bis zu drei weitere Spieler, je Loch NUR der Endscore — bewusst nichts
   weiter (Wunsch vom 26.08.). Die NAMEN vergibt das Handy (Tastatur); die Uhr
   uebernimmt sie aus dem Entwurf, zeigt sie als Zeilen auf Seite 2 und
   merkt sie sich in den Prefs, damit die Zeilen einen Neustart ueberleben.
   Global statt durchgereicht: erspart drei Signaturen (PlayPager, ScorePage,
   DetailPage) einen Parameter, den nur eine Zeile braucht. */
object Mitspieler {
    /* ==========================================================================
       DAS HANDY IST FUEHREND — AUSNAHMSLOS (2026-08-27 (42))
       --------------------------------------------------------------------------
       GEMELDET: Auf der Uhr standen MEHR Mitspieler als in der PWA.
       ZWEI URSACHEN, beide hier:
         1. (39) liess die Uhr PLAETZE eroeffnen (`plaetze`, Chip „+ Mitspieler"),
            gespeichert in den Prefs. Die Zahl wuchs, sank aber nie: Wer einmal
            drei Plaetze aufgemacht hatte, sah drei Zeilen — auch auf der
            naechsten Runde, auch wenn das Handy niemanden mehr kannte.
         2. Uebernommen wurde nur, wenn die Liste des Handys NICHT LEER war
            (`if (dr.mitspieler.isNotEmpty())`). Ein am Handy ENTFERNTER
            Mitspieler kam damit nie auf der Uhr an — Entfernen war die einzige
            Aenderung, die nicht reiste.
       VORGABE VOM 27.08.: Nur die in der PWA fuer DIESE Runde hinterlegten
       Spieler erscheinen auf der Uhr. Die Uhr fuehrt keine eigene Liste mehr,
       weder Namen noch Plaetze — sie zeigt, was das Handy sagt, und sonst
       nichts.
       DAMIT FAELLT (39) WIEDER WEG, und das ist der Preis, ausdruecklich
       benannt: Wer die Runde auf der Uhr beginnt und das Handy im Bag laesst,
       kann keinen Mitspieler erfassen — die Zeile entsteht erst, wenn das
       Handy einen Namen vergeben hat. Das ist die Kehrseite von „eine
       Wahrheit", und sie ist billiger als zwei Listen, die auseinanderlaufen.
       DIE PREF `mitspielerN` WIRD NICHT MEHR GELESEN. Sie darf auf alten
       Geraeten liegen bleiben; ein Wert dort hat keine Wirkung mehr.
       ========================================================================== */
    @Volatile var namen: List<String> = emptyList()

    /* `neu` ist die Liste des HANDYS, wie sie im Entwurf steht — auch eine
       LEERE Liste ist eine Aussage („keine Mitspieler") und wird uebernommen.
       Wer hier wieder ein `isNotEmpty()` einbaut, baut Ursache 2 von oben
       wieder ein. */
    fun setzen(ctx: Context, neu: List<String>) {
        val n = neu.map { it.trim() }.filter { it.isNotBlank() }.take(3)
        if (n != namen) { namen = n; prefSetS(ctx, "mitspieler", n.joinToString("|")) }
    }

    fun laden(ctx: Context) {
        namen = prefGetS(ctx, "mitspieler", "").split("|").filter { it.isNotBlank() }.take(3)
    }
}

data class HoleEntry(
    /* ZEITSTEMPEL DER LETZTEN AENDERUNG AN DIESEM LOCH (2026-08-15 (7)).
       Bis hierher fuellte `adoptHoles` nur LEERE Felder — „wer schon etwas
       drinstehen hat, behaelt es". Das verhindert gegenseitiges Ueberschreiben,
       macht aber das AENDERN unmoeglich: Ein am Handy korrigierter
       Tee-Schlaeger kam auf der Uhr nie an.
       Mit diesem Datum laesst sich entscheiden, wessen Wert gilt — und zwar JE
       LOCH. Der Zeitstempel des ganzen Entwurfs waere zu grob: Er sagt nur,
       welches GERAET zuletzt etwas getan hat, nicht wer DIESES Loch bearbeitet
       hat. */
    val ts: String? = null,
    val score: Int? = null,
    val putts: Int? = null,
    val tee: String? = null,
    val appr: String? = null,
    // Fehlerrichtung des Approach (PWA v1.57). Ohne dieses Feld wuerde die Uhr
    // einen am Handy gesetzten Wert beim naechsten Push STILL loeschen —
    // buildRoundJson schreibt nur, was das Datenmodell kennt.
    val apprMiss: String? = null,
    /* Welcher Schlaeger den APPROACH gespielt hat (PWA v1.72). Bisher wurde
       nur der Tee-Schlaeger erfasst — damit liess sich die Streuung je
       Schlaeger nur fuer den Abschlag lernen, nicht fuer Eisen und Wedges,
       aus denen die meisten Schlaege verlorengehen. */
    val apprClub: String? = null,
    val penN: Int? = null,
    val firstPutt: String? = null,
    /* PUTT-DIAGNOSE (2026-08-10). Bei Approaches gibt es `apprMiss` seit
       langem — beim Putten fehlte die Entsprechung, obwohl dort die groesste
       Luecke liegt. Zusammen beantworten die beiden Felder die einzige Frage,
       die beim Putten zaehlt: WORAN liegt es?
         · puttMiss  — ueberwiegend KURZ heisst Laengenkontrolle, systematisch
           eine SEITE heisst Startlinie. Zwei verschiedene Uebungen.
         · puttRest  — trennt Dreiputts nach Ursache: langer Rest = Lag,
           kurzer Rest = Kurzputt. Ohne dieses Feld nicht unterscheidbar. */
    val puttMiss: String? = null,
    val puttRest: String? = null,
    /* PLATZ ZWISCHEN BALL UND FAHNE (2026-08-11). Der groesste einzelne
       Score-Verlust bei mittleren Handicaps ist nicht das verfehlte Gruen,
       sondern die FALSCHE SEITE davon: Liegt zwischen Ball und Fahne kaum
       Gruen, muss der Chip punktgenau sein. Mit Platz dahinter darf er
       durchlaufen — ein voellig anderer Schwierigkeitsgrad.
       BENENNUNG: „short-sided" oder „kurzseitig" versteht kaum jemand.
       Die Auswahl beschreibt deshalb, was man SIEHT. */
    val kurzseitig: String? = null,
    val quality: String? = null,
    val club: String? = null,
    val lie: String? = null,
    val distToPin: Int? = null,
    val bunkerN: Int? = null,
    val b1: String? = null,
    val penType: String? = null,
    val ud: Boolean? = null,
    val ss: Boolean? = null,
    val recovery: Boolean? = null,
    val gir: Boolean? = null,
    /* Endscores der Mitspieler 1-3 (37) — nur die Zahl, sonst nichts. */
    val msc1: Int? = null,
    val msc2: Int? = null,
    val msc3: Int? = null,
    val shots: List<ShotPt> = emptyList()
) {
    fun empty() =
        score == null &&
                putts == null &&
                tee == null &&
                appr == null &&
                penN == null &&
                firstPutt == null &&
                quality == null &&
                club == null &&
                lie == null &&
                distToPin == null &&
                bunkerN == null &&
                b1 == null &&
                penType == null &&
                ud == null &&
                ss == null &&
                recovery == null &&
                gir == null &&
                shots.isEmpty()
}

// ================= Geo-Datenmodelle =================

data class LL(
    val lat: Double,
    val lng: Double
)

/* ==========================================================================
   `GeoFeature`, `ElevProfil`, `HoleGeo`, `CourseGeo` SIND ENTFERNT (40)
   --------------------------------------------------------------------------
   Rund 85 Zeilen Datentypen fuer Gruenringe, Gefahren, Hoehenprofile und die
   Tee-Gruen-Achse. Kein Aufrufer seit (38); ihr Parser ist mit dieser Fassung
   ebenfalls weg.
   `ElevProfil.beiMeter` und `HoleGeo.dElev` waren die Hoehenrechnung der Uhr.
   Sie leben in der PWA weiter (`schlagNeutral` rechnet die Hoehe aus dem
   DGM1 heraus) — und dort GEHOEREN sie hin: Die Uhr liefert die zwei
   Messpunkte, das Handy weiss, wie hoch sie liegen.
   `LL` BLEIBT: Ein Punktepaar ist keine Geometrie, sondern die Form, in der
   ein GPS-Fix und ein Schlag-Endpunkt aufgeschrieben werden.
   ========================================================================== */

// Eine GPS-Position samt Genauigkeit
data class Fix(
    val lat: Double,
    val lng: Double,
    val acc: Float,
    val ts: Long
) {
    fun ll() = LL(lat, lng)
}

data class Weather(
    val temp: Double?,
    val windMs: Double?,
    val windDir: Double?,
    val gustMs: Double?,
    val at: Long
)

// ================= Zeit =================

private fun isoNow(): String {
    val f = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        Locale.US
    )
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date())
}

/* Zeitstempel EINES Zeitpunkts im selben Format wie `isoNow` — gebraucht, um
   den Rundenbeginn mit der Verworfen-Marke zu vergleichen (2026-08-15 (9)). */
/* `alterSek` entfernt (40, nachgereicht). Sie gab das Alter eines
   ISO-Zeitstempels in Sekunden und hatte GENAU EINEN Aufrufer: die Pruefung,
   ob die Caddy-Empfehlung des Handys noch frisch genug ist (≤ 90 s), bevor die
   Uhr sie der eigenen vorzieht. Mit der Caddy-Schleife ist der Aufrufer weg.
   Wer wieder ein Alter braucht: `Fehler`/`Net` fuehren ihre eigenen
   Zeitvergleiche ueber `isoNow`/`isoOf`. */

private fun isoOf(ms: Long): String {
    val f = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        Locale.US
    )
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date(ms))
}

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

// ===========================================================================
//  GEO — Platzgeometrie (1:1 die Formeln der PWA, nur in Kotlin)
//  Koordinaten immer [lat, lng], Ergebnisse immer Meter.
// ===========================================================================

object Geo {

    private const val R = 6371000.0

    fun dist(a: LL, b: LL): Double {
        val toR = Math.PI / 180.0
        val dLat = (b.lat - a.lat) * toR
        val dLng = (b.lng - a.lng) * toR
        val la1 = a.lat * toR
        val la2 = b.lat * toR
        val x = sin(dLat / 2) * sin(dLat / 2) +
                cos(la1) * cos(la2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * R * asin(min(1.0, sqrt(x)))
    }

    /* HIER STANDEN 400 ZEILEN GEOMETRIE (Abbau 2026-08-26 (40)).
       bearing, compass8, projX/projY, interp, pointInRing, ringCentroid,
       greenRingFor, greenFMB, greenDims, greenDimsCompute, bboxHit,
       hazardsOnLine, lieAt, lieFactor, lieLabel.
       Sie beantworteten Fragen, die die Uhr seit (38) nicht mehr stellt:
       Wie weit ist es aufs Gruen, wie liegt der Ball, was liegt auf der
       Linie. Das rechnet das Handy (siehe 0. ZWECK).
       `dist` bleibt als EINZIGE — sie misst die Luftlinie zwischen Start und
       Ende eines Schlags und ist damit keine Anpassung, sondern der Messwert
       selbst. */
}

/* ==========================================================================
   `object Wx` UND `object Caddy` SIND ENTFERNT (2026-08-26 (40))
   --------------------------------------------------------------------------
   Wx trug tempFactor, windRel, playsLike, arrowRel, elevLabel und line;
   Caddy trug plan, planCore, pick, mp und modeLabel — zusammen rund 480
   Zeilen. Sie waren seit (38) ohne Aufrufer: Die Seite, die ihre Ergebnisse
   zeigte, ist entfallen, und die Rechenschleife dazu gestrichen.
   WARUM SIE NICHT „FUER SPAETER" STEHENBLEIBEN: Ein zweites Rechenwerk auf
   der Uhr ist genau der Zustand, den die Vorgabe vom 26.08. beendet hat.
   Solange der Quelltext dasteht, ist der naechste Einbau ein Einzeiler — und
   dann gibt es wieder zwei Antworten auf dieselbe Frage. Das Handy rechnet
   mit Streuungsdaten und Monte Carlo; die Uhr konnte das nie und soll es
   nicht koennen.
   WER HIER ETWAS SUCHT: Die Logik lebt in der PWA (`playsLike`,
   `caddyFuerPunkt`, STRAT). Die Geschichte steht im Changelog unter (38).
   ========================================================================== */

// ================= Netzwerk =================

private fun strList(
    db: JSONObject,
    key: String,
    fallback: List<String>
): List<String> {
    val a = db.optJSONArray(key) ?: return fallback
    val out = ArrayList<String>()

    for (i in 0 until a.length()) {
        out.add(a.optString(i))
    }

    return if (out.isEmpty()) fallback else out
}

/* ==========================================================================
   DER KARTEN-PARSER IST ENTFERNT (2026-08-26 (40))
   --------------------------------------------------------------------------
   `GEO_KEEP`, `llArray`, `feature`, beide `parseGeo` und `parseGeoObj` — rund
   150 Zeilen, die aus dem Roh-JSON eines Platzes Gruenringe, Gefahren und
   Hoehenprofile lasen. Ihr einziger Abnehmer waren `Geo` und `Caddy`.
   FOLGE FUER `watch.json`: Die Uhr braucht KEINE Platzgeometrie mehr. Das
   Handy kann `geo` (und `strat`) aus `watchPayload()` streichen; die Datei
   faellt damit von einigen hundert kB auf wenige. Das ist die Gegenseite
   dieser Fassung, PWA v4.84 — bis dahin schickt das Handy Daten, die hier
   niemand mehr liest. Das kostet Bandbreite, aber nichts Schlimmeres:
   `parseGeo` verschwindet, `geoRaw` wird schlicht ignoriert.
   ========================================================================== */

private object Net {

    // _draftRound aus dem Repo-JSON ziehen (laufende Runde eines anderen Geräts)
    /* NICHT mehr private (2026-08-15 (4)): `loadData` haengt den Entwurf aus
       `draft.json` an die schlanke Datei an — siehe dort. */
    fun parseDraft(db: JSONObject): RepoDraft? {
        val d = db.optJSONObject("_draftRound") ?: return null
        val r = d.optJSONObject("round") ?: return null
        val course = r.optString("course")
        val date = r.optString("date")
        if (course.isEmpty() || date.isEmpty()) return null
        val lv = d.optJSONObject("live")
        /* ==========================================================================
           BENANNTE ARGUMENTE (2026-08-16 (6)) — nicht Kosmetik, sondern die
           Behebung einer wiederkehrenden Fehlerklasse.
           Vorher stand hier eine Liste aus 19 Werten in fester Reihenfolge. Wer
           ein Feld in die MITTE der Datenklasse einfuegt, verschiebt damit jeden
           nachfolgenden Wert um eine Stelle — der Bau brach mit zehn „Argument
           type mismatch" ab, und nur weil die Typen zufaellig kollidierten. Bei
           gleichen Typen (String nach String) waere es ohne Fehlermeldung
           durchgelaufen und haette stillschweigend falsche Daten geliefert.
           Mit Namen ist die Reihenfolge gleichgueltig. */
        return RepoDraft(
            course = course,
            date = date,
            side = r.optString("side", "18 Loch"),
            ts = d.optString("ts"),
            holes = r.optJSONArray("holes") ?: JSONArray(),
            shotAck = d.optJSONArray("shotAck")?.let { a2 ->
                (0 until a2.length()).mapNotNull { i2 ->
                    a2.optString(i2)?.ifEmpty { null } } } ?: emptyList(),
            doneAt = d.optJSONObject("roundDone")?.optString("at")?.ifEmpty { null },
            doneCourse = d.optJSONObject("roundDone")?.optString("course")?.ifEmpty { null },
            liveSrc = lv?.optString("src")?.ifEmpty { null },
            liveHole = lv?.optInt("hole", 0)?.takeIf { it > 0 },
            liveAt = lv?.optString("at")?.ifEmpty { null },
            liveWahlAt = lv?.optString("wahlAt")?.ifEmpty { null },
            liveHoleSeq = lv?.optInt("holeSeq", 0)?.takeIf { it > 0 },
            /* KEIN `?: emptyList()` MEHR (42): Das machte aus „nicht gesagt"
               ein „ausdruecklich keine" und haette beim Uebernehmen jede
               Namensliste geloescht, sobald ein Entwurf ohne den Schluessel
               kam — etwa der eigene der Uhr. */
            mitspieler = r.optJSONArray("mitspieler")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } }.take(3)
            },
            roundId = r.optString("id").ifEmpty { null },
            tee = r.optString("tee").ifEmpty { null },
            recSrc = lv?.optJSONObject("rec")?.optString("src")?.ifEmpty { null },
            recClub = lv?.optJSONObject("rec")?.optString("club")?.ifEmpty { null },
            caddyClub = lv?.optJSONObject("caddy")?.optString("club")?.ifEmpty { null },
            caddyRest = lv?.optJSONObject("caddy")?.optInt("rest", 0)?.takeIf { it > 0 },
            caddyPlays = lv?.optJSONObject("caddy")?.optInt("plays", 0)?.takeIf { it > 0 },
            caddyHole = lv?.optJSONObject("caddy")?.optInt("hole", 0)?.takeIf { it > 0 },
            caddyLat = lv?.optJSONObject("caddy")?.optDouble("lat", Double.NaN)?.takeIf { !it.isNaN() },
            caddyLng = lv?.optJSONObject("caddy")?.optDouble("lng", Double.NaN)?.takeIf { !it.isNaN() },
            caddyAt = lv?.optJSONObject("caddy")?.optString("at")?.ifEmpty { null }
        )
    }

    /* ---------- DRAFT-DATEI (siehe Kommentar bei DRAFT_PATH) ---------- */
    private var draftSha: String? = null

    /** null = nicht verfuegbar (alter Worker/Netzfehler) · leeres Objekt = keine Runde */
    fun fetchDraftFile(): JSONObject? {
        return try {
            val c = openRead(DRAFT_FRESH_URL)
            val code = c.responseCode
            if (code == 404) { draftSha = null; c.disconnect(); return JSONObject() }
            if (code !in 200..299) {
                /* MIT CODE MELDEN (2026-08-16 (14)): „nichts gelesen" kann
                   Netzausfall, falscher Schluessel (401/403) oder ein Fehler
                   im Worker (5xx) sein — drei ganz verschiedene Ursachen mit
                   drei verschiedenen Abhilfen. Ohne die Zahl raet man. */
                Fehler.add("draft.json lesen", "HTTP $code")
                c.disconnect(); return null
            }
            draftSha = c.getHeaderField("X-Repo-Sha")
            val t = leseBegrenzt(c, "draft.json")
            c.disconnect()
            if (t.isBlank()) return JSONObject()
            val o = JSONObject(t)
            /* DEN ALTEN WORKER AM INHALT ERKENNEN (2026-08-14, Fix):
               Ein Worker VOR v2.6 kennt den `path`-Parameter nicht — er
               ignoriert ihn und liefert mit Status 200 die GROSSE Datei. Das
               sieht wie ein Erfolg aus, enthaelt aber kein `round`; die Uhr
               hielt das fuer „keine Runde" und fiel NICHT auf den alten Weg
               zurueck. Ergebnis: Der Abgleich stand still. Ein Statuscode sagt
               eben nicht, WAS man bekommen hat — deshalb der Inhaltstest. */
            if (o.has("testDefs") || o.has("rounds") || o.has("_draftRound")) return null
            o
        } catch (e: Exception) { if (e.istAbbruch()) throw e; Fehler.add("draft.json lesen", e); null }
    }

    /** Schreibt die kleine Datei. Bei 409 (jemand war schneller) EINMAL frisch
     *  lesen, VEREINEN (nicht ueberschreiben — der andere steht gerade auf der
     *  Bahn) und neu senden. Mehr Anlaeufe lohnen nicht; der naechste Takt
     *  kommt in Sekunden. */
    /* ==========================================================================
       SCHREIBKONFLIKTE AUSSITZEN (2026-08-16 (9)) — HIER RISS DER ABGLEICH AB
       --------------------------------------------------------------------------
       Vorher: EIN Versuch, bei 409 einmal neu lesen, neu bauen, nochmal — und
       dann STILL aufgeben. Solange nur alle 60 s geschrieben wurde, fiel das
       nicht auf. Seit das Handy waehrend der Runde alle 10 s schreibt (PWA
       v3.19), kollidiert fast jeder Versuch: Die Uhr liest frisch, baut neu,
       schickt — und in der Zwischenzeit hat das Handy erneut geschrieben. Zwei
       Versuche reichen dafuer nicht, und danach lief die Uhr fuer den Rest der
       Runde ohne Abgleich weiter. Genau das gemeldete Bild: Die ersten Loecher
       gehen, dann laufen die Geraete auseinander.
       JETZT VIER VERSUCHE MIT ZUFAELLIGER PAUSE. Die Pause ist wichtig und
       muss ZUFAELLIG sein: Zwei Geraete, die nach einem Konflikt beide sofort
       wieder schicken, kollidieren erneut — und zwar synchron, also immer.
       UND GEMELDET WIRD ES: Ein lautloses Aufgeben ist der schlimmste Fall,
       weil dann alles normal aussieht. Das Fehlerprotokoll haelt es fest. */
    fun pushDraftFile(bauen: (JSONObject?) -> JSONObject): Boolean {
        var body = bauen(null)
        var letzterCode = 0
        repeat(4) { versuch ->
            try {
                val p = URL(WORKER_URL).openConnection() as HttpURLConnection
                p.requestMethod = "POST"
                p.doOutput = true
                p.connectTimeout = 20000
                p.readTimeout = 20000
                p.setRequestProperty("Content-Type", "application/json")
                p.setRequestProperty("X-Write-Key", WRITE_KEY)
                p.setRequestProperty("X-Path", DRAFT_PATH)
                p.setRequestProperty("X-Base-Sha", draftSha ?: "")
                p.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = p.responseCode
                /* DIE NEUE KENNUNG UEBERNEHMEN (2026-08-24 (4), Worker v2.10).
                   Ohne sie kannte die Uhr nach jedem erfolgreichen Schreiben
                   nur noch die ALTE Kennung und lief beim naechsten Push in
                   einen 409 — jedes Mal. Vier davon, und der Abgleich setzte
                   aus („4× Konflikt — Abgleich ausgesetzt"). Genau der
                   gemeldete Einbruch.
                   Fehlt der Kopf (aelterer Worker), bleibt es beim alten
                   Verhalten: ein Umlauf mehr, aber kein Abbruch. */
                /* Der `Date`-Kopf JEDER Antwort traegt die Serverzeit — der
                   Zeitversatz kostet also keinen eigenen Abruf (2026-08-25 (11)). */
                Diagnose.versatzAus(p.getHeaderField("Date"))
                Diagnose.syncNotiz("Entwurf", "HTTP $code" + (if (versuch > 0) " (Versuch ${versuch + 1})" else ""))
                if (code in 200..299) {
                    val neu = p.getHeaderField("X-Repo-Sha")?.takeIf { it.isNotBlank() }
                    /* WENN DER WORKER DIE KENNUNG NICHT MITSCHICKT, laeuft der
                       naechste Push zwangslaeufig in einen 409 — und wenn parallel
                       das Handy schreibt, sind die vier Versuche schnell weg.
                       Genau das koennte „einmal geht es, dann nicht mehr" erklaeren.
                       Die Auskunft steht ab sofort im Protokoll, statt dass man sie
                       erraten muss (2026-08-24 (9)). */
                    if (neu == null) Fehler.warn("Entwurf senden",
                        "Worker ohne X-Repo-Sha — naechster Push laeuft in 409 (Worker aktualisieren?)")
                    neu?.let { draftSha = it }
                    pushGemeldet()      // fuer die Doppel-Sperre des Herzschlags
                }
                p.disconnect()
                letzterCode = code
                if (code in 200..299) return true
                if (code != 409) { Fehler.add("Entwurf senden", "HTTP $code"); return false }
                if (versuch == 3) { Fehler.add("Entwurf senden", "4× Konflikt (409) — Abgleich ausgesetzt"); return false }
                Thread.sleep((300L + (Math.random() * 700).toLong()))
                val frisch = fetchDraftFile()
                    ?: run { Fehler.add("Entwurf senden", "Konflikt, aber Datei nicht lesbar"); return false }
                body = bauen(frisch)
            } catch (e: Exception) {
                Fehler.add("Entwurf senden", e)
                return false
            }
        }
        Fehler.add("Entwurf senden", "aufgegeben (letzter Code $letzterCode)")
        return false
    }

    // Leichter GET nur für den laufenden Entwurf (Pull-Abgleich während der Runde)
    fun fetchDraft(): RepoDraft? {
        /* KORREKTUR 2026-08-14 (2): Eine LEERE oder fehlende `draft.json` ist
           KEINE Antwort, sondern nur die Auskunft „hier steht noch nichts". Sie
           galt vorher als „keine Runde", und damit sah die Uhr die Eingaben des
           Handys nicht mehr — solange die Datei nicht existierte, war der
           Abgleich komplett tot. Jetzt: nur ein Entwurf IN der Datei zaehlt,
           sonst weiter ueber die grosse Datei wie frueher. */
        val f = fetchDraftFile()
        /* VERWORFEN-MARKE (2026-08-15 (9)): Eine leere Datei heisst nur „gerade
           keine Runde im Repo" — dass eine Runde VERWORFEN wurde, sagt erst
           dieses Datum. Ein Fehlen laesst sich nicht uebertragen, ein Datum
           schon (dieselbe Lehre wie bei den geloeschten Platzkarten). */
        lastDiscardedTs = f?.optString("discardedTs")?.takeIf { it.isNotBlank() }
        if (f != null && f.has("round")) {
            return parseDraft(JSONObject().put("_draftRound", f))
        }
        if (f != null) return null                        // Datei da, aber ohne Runde
        /* KEIN RUECKFALL AUF DIE GROSSE DATEI MEHR (2026-08-28 (48)).
           Hier stand `parseDraft(JSONObject(readData()))` — um einen Entwurf zu
           FINDEN, wurden mehrere Megabyte geladen und geparst. Aus zwei
           Gruenden weg:
           1. WENN `draft.json` NICHT LESBAR IST, ist es die Leitung — und dann
              gelingt eine Datei, die tausendmal groesser ist, erst recht
              nicht. Der Rueckfall trat also genau dann an, wenn er nicht
              helfen konnte, und machte die Lage schlimmer.
           2. Es ist dieselbe Speicherfalle, die am 28.08. die App mit
              OutOfMemory beendet hat (siehe `pushDraft`).
           `null` heisst „kein Entwurf gefunden" — der naechste Takt fragt neu.
           Das Handy schreibt `draft.json` bei jeder Aenderung; ein Entwurf, der
           dort nicht steht, ist keiner. */
        return null
    }

    var lastDiscardedTs: String? = null
        private set

    /* ==========================================================================
       KOPPLUNGSTEST (2026-08-15 (13))
       --------------------------------------------------------------------------
       Das Handy legt eine Frage in `probe.json`: Platz, Loch, Testposition.
       Die Uhr antwortet mit dem, was SIE daraus macht — Distanz zur Gruenmitte,
       Front/Back, Schlaegerzahl, Auswahllisten, eigene Fassung.
       WOZU: Die Rundensimulation der PWA prueft nur die PWA. Ob die Uhr
       dieselben Zahlen rechnet, zeigte bisher erst die Bahn — und genau dort
       ist es aufgefallen (40 m statt 300 m). Jetzt zu Hause pruefbar, in
       zehn Sekunden.
       Der Test laeuft, solange die App offen ist, unabhaengig vom Bildschirm;
       er ruehrt keine Spieldaten an. */
    fun probeGet(): JSONObject? {
        return try {
            val c = openRead("$WORKER_URL/?fresh=1&path=probe.json")
            val code = c.responseCode
            if (code !in 200..299) { c.disconnect(); return null }
            val t = leseBegrenzt(c, "probe.json")
            c.disconnect()
            if (t.isBlank()) null else JSONObject(t)
        } catch (e: Exception) { if (e.istAbbruch()) throw e; Fehler.add("probe.json lesen", e); null }
    }

    /* ==========================================================================
       DAS PROTOKOLL AUCH OHNE RUNDE (2026-08-24 (8))
       --------------------------------------------------------------------------
       Der Weg ueber den Rundenentwurf kostet nichts — aber `draft.json` gibt es
       nur WAEHREND einer Runde. Ein Fehler beim Start, beim Platzladen oder auf
       der Uebersicht erreichte das Handy damit nie. Das sind aber genau die
       Fehler, die man beim Einrichten sucht.
       `probe.json` liegt bereits in der Whitelist des Workers, ist winzig und
       an keine Runde gebunden. Geschrieben wird NUR BEI AENDERUNG: Bei
       fehlerfreiem Betrieb entsteht kein einziger zusaetzlicher Vorgang.
       KEIN X-Base-Sha, sondern `X-Force`: Diese Datei hat genau einen Schreiber
       je Geraet und keinen Wert, den man verlieren koennte — ein Konflikt waere
       hier nur Aufwand ohne Nutzen. */
    @Volatile private var letzterLogStand: String = ""
    fun logPut(): Boolean {
        val zeilen = Fehler.liste
        if (zeilen.isEmpty()) return false
        /* Der Bericht gehoert in den Vergleich: Sonst gilt „nichts Neues",
           obwohl gerade ein frischer Bericht entstanden ist — und genau der
           soll ja raus (2026-08-25 (13)). */
        val stand = zeilen.joinToString("\n") + "|" + Diagnose.berichtAt
        if (stand == letzterLogStand) return false          // nichts Neues
        val o = JSONObject()
            .put("at", isoNow())
            .put("app", WATCH_APP)
            .put("note", WATCH_NOTE)
            .put("geraet", Build.MANUFACTURER + " " + Build.MODEL)
            .put("zeilen", JSONArray(zeilen))
        /* Der Diagnosebericht reist als EIGENES Feld — unabhaengig davon, was
           im Ringpuffer gerade passiert (2026-08-25 (13)). */
        if (Diagnose.letzterBericht.isNotBlank()) {
            o.put("bericht", Diagnose.letzterBericht)
            o.put("berichtAt", Diagnose.berichtAt)
        }
        return try {
            val p = URL(WORKER_URL).openConnection() as HttpURLConnection
            p.requestMethod = "POST"
            p.doOutput = true
            p.connectTimeout = 15000
            p.readTimeout = 15000
            p.setRequestProperty("Content-Type", "application/json")
            p.setRequestProperty("X-Write-Key", WRITE_KEY)
            p.setRequestProperty("X-Path", "watchlog.json")
            p.setRequestProperty("X-Base-Sha", "")
            p.setRequestProperty("X-Force", "1")
            p.outputStream.use { it.write(o.toString().toByteArray(Charsets.UTF_8)) }
            val code = p.responseCode
            p.disconnect()
            val ok = code in 200..299
            if (ok) letzterLogStand = stand
            ok
        } catch (e: Exception) {
            /* STILL: Scheitert das Uebertragen des Protokolls, ist das Protokoll
               selbst noch da — auf der Uhr. Eine Meldung darueber wuerde es nur
               weiter fuellen. */
            false
        }
    }

    fun probePut(o: JSONObject): Boolean {
        return try {
            val p = URL(WORKER_URL).openConnection() as HttpURLConnection
            p.requestMethod = "POST"
            p.doOutput = true
            p.connectTimeout = 20000
            p.readTimeout = 20000
            p.setRequestProperty("Content-Type", "application/json")
            p.setRequestProperty("X-Write-Key", WRITE_KEY)
            p.setRequestProperty("X-Path", "probe.json")
            p.setRequestProperty("X-Base-Sha", "")     // Force: letzte Antwort darf ueberschrieben werden
            p.setRequestProperty("X-Force", "1")
            p.outputStream.use { it.write(o.toString().toByteArray(Charsets.UTF_8)) }
            val code = p.responseCode
            p.disconnect()
            code in 200..299
        } catch (e: Exception) { if (e.istAbbruch()) throw e; Fehler.add("probe.json schreiben", e); false }
    }

    /* `pushDiscarded` entfernt (2026-08-27 (44)) — sie schrieb die
       Verworfen-Marke ins Repo. Verwerfen passiert nur noch am Handy.
       DIE UHR LIEST DIE MARKE WEITERHIN (`RepoDraft.discardedTs`) und beendet
       ihre Runde, wenn das Handy sie setzt — nur schreiben tut sie sie nicht
       mehr. Lesen und Schreiben sind hier zwei verschiedene Rechte. */

    /* ------------------------------------------------------------------
       KRITISCHER FIX (2026-08-08): openData() hat vorher eine BEREITS
       VERBUNDENE Connection zurückgegeben — f.responseCode verbindet. Alle
       drei Aufrufer setzten danach requestMethod und setRequestProperty,
       und das wirft auf einer verbundenen Connection:
         IllegalStateException: Cannot set request property after connection
       Ergebnis: sobald der Worker mit 200 antwortete, schlug JEDER Lesevorgang
       der Uhr fehl — fetchData (keine Plätze, keine Optionen), fetchDraft
       (Pull-Abgleich) und der GET in pushDraft (kein Schreiben).
       Deshalb ging weder "Runde vom Handy" noch "ohne Handy".
       Jetzt werden alle Eigenschaften VOR dem Verbinden gesetzt.
       ------------------------------------------------------------------ */
    private fun openRead(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "GET"
        c.connectTimeout = 12000
        c.readTimeout = 20000
        c.setRequestProperty("Cache-Control", "no-cache")
        c.setRequestProperty("Accept", "application/json")
        return c
    }

    // Frischen Stand über den Worker lesen, Pages-CDN nur als Fallback.
    /* DIE KENNUNG DER GELESENEN FASSUNG (2026-08-24).
       Der Worker liefert sie im Kopf `X-Repo-Sha`; sie wird gebraucht, um mit
       genau dieser Basis zurueckzuschreiben (SHA-Tuersteher). Bis hierher warf
       `readData` sie weg — und der Rueckweg lief deshalb ueber den ALT-Modus,
       der serverseitig mit veralteten Regeln merged hat.
       `null` heisst: unbekannt. Dann wird NICHT geschrieben, statt blind zu
       ueberschreiben — ein Schreibvorgang ohne bekannte Basis ist genau das,
       was der Tuersteher verhindern soll.
       Der Rueckfall auf `DATA_URL` (roh von GitHub Pages) liefert keine
       Kennung; von dort gelesene Daten taugen zum ANZEIGEN, nicht zum
       Zurueckschreiben. */
    /* `fullSha` entfernt (49): Sie merkte sich die Kennung der GROSSEN Datei
       fuers Zurueckschreiben — beides gibt es nicht mehr. */

    /* ==========================================================================
       EINE GRENZE, BEVOR DER SPEICHER SIE SETZT (2026-08-28 (48))
       --------------------------------------------------------------------------
       Am 28.08. hat eine Zuteilung von 14,8 MB die App beendet:
       `OutOfMemoryError @Net.readData`. Der Heap der Uhr endet bei 128 MB, und
       Rohtext plus JSONObject plus Sende-Abzug liegen gleichzeitig darin.
       EIN ABSTURZ IST DIE SCHLECHTESTE ART, EINE GRENZE ZU ERFAHREN: Er nimmt
       die laufende Runde mit und hinterlaesst keine Auskunft ausser einem
       Stapelabzug. Deshalb wird die Groesse VORHER geprueft — angekuendigt
       ueber `Content-Length`, und falls die fehlt, beim Lesen mitgezaehlt.
       Was zu gross ist, wird abgelehnt und GEMELDET. Eine Fehlermeldung, die
       man lesen kann, ist einem Absturz in jeder Hinsicht ueberlegen.
       6 MB: Alles, was die Uhr seit (49) noch liest — `watch.json`,
       `draft.json`, `probe.json`, Wetter — liegt bei wenigen Kilobyte. Wer
       hier anschlaegt, hat ein Datenproblem und kein Speicherproblem, und soll
       das erfahren.
       SEIT (49) AN JEDER LESESTELLE. Die Funktion entstand fuer die grosse
       Datei; die gibt es nicht mehr. Statt sie mit ihr zu loeschen, bewacht
       sie jetzt die verbliebenen vier — ein Riegel, der nur an der Stelle
       sitzt, an der es einmal knallte, schuetzt nur vor der Wiederholung. */
    private const val MAX_LESEN = 6 * 1024 * 1024

    private fun leseBegrenzt(c: HttpURLConnection, was: String): String {
        val angekuendigt = c.contentLengthLong
        if (angekuendigt > MAX_LESEN) {
            throw IllegalStateException(
                "$was: ${angekuendigt / 1024} kB angekuendigt, Grenze ${MAX_LESEN / 1024} kB"
            )
        }
        val sb = StringBuilder()
        val puffer = CharArray(16 * 1024)
        c.inputStream.bufferedReader().use { r ->
            while (true) {
                val n = r.read(puffer)
                if (n < 0) break
                sb.append(puffer, 0, n)
                if (sb.length > MAX_LESEN) {
                    throw IllegalStateException(
                        "$was: ueber ${MAX_LESEN / 1024} kB und kein Ende — abgebrochen"
                    )
                }
            }
        }
        return sb.toString()
    }

    /* `readData()` ist entfernt (2026-08-28 (49)) — mit ihr die einzige Stelle,
       die je die GROSSE `trainingsdaten.json` gelesen hat. Sie war der Kern des
       OutOfMemory-Absturzes vom 28.08.; (48) hat ihre Aufrufer in `pushDraft`
       und `fetchDraft` entfernt, (49) den letzten in `loadData`. Damit faellt
       auch `fullSha`, `FRESH_URL` und `DATA_URL` weg.
       DIE UHR LIEST NUR NOCH `watch.json`, `draft.json` und `probe.json` —
       zusammen wenige Kilobyte. Wer hier wieder eine grosse Datei einbaut,
       liest zuerst (48). */

    /* `parsePlans` entfernt (40). Sie las `DB.strat.gameplans` — der einzige
       Abnehmer war die Gameplan-Ansicht. Das Handy kann `strat` damit aus
       `watchPayload()` streichen (PWA v4.84). */

    // Rohtext holen — getrennt vom Parsen, damit er in den Cache kann.
    /* ==========================================================================
       SCHLANKE DATEI ZUERST (2026-08-14 (4))
       --------------------------------------------------------------------------
       `trainingsdaten.json` ist rund 3 MB, gebraucht wird davon ein Bruchteil:
       Plaetze mit Geometrie, Schlaeger, Optionen, Handicap, Gameplans. Runden,
       Tests, Fitness, Launch-Monitor, Notizen und die Wissensdatenbank machen
       den Grossteil aus und interessieren die Uhr nie.
       Die PWA schreibt deshalb `watch.json` (rund 200 kB, ab PWA v2.96) — im
       selben FORMAT, denn `parseData` liest ohnehin nur diese Felder.
       RUECKFALL: Fehlt sie (404), kennt der Worker den Pfad nicht (403) oder
       ist sie leer, wird die grosse Datei geholt wie bisher. */
    /* Woraus hat die Uhr zuletzt geladen? Steht im Kopplungstest — ohne diese
       Angabe raet man, ob die Uhr die schlanke oder die grosse Datei sieht
       (2026-08-15 (15)). */
    var lastWatchFile: Boolean = false
        private set

    fun fetchWatchRaw(): String? {
        return try {
            val c = openRead("$WORKER_URL/?fresh=1&path=watch.json")
            val code = c.responseCode
            if (code !in 200..299) { c.disconnect(); return null }
            val t = leseBegrenzt(c, "watch.json")
            c.disconnect()
            lastWatchFile = t.isNotBlank()
            if (t.isBlank()) null else t
        } catch (e: Exception) { if (e.istAbbruch()) throw e; Fehler.add("watch.json lesen", e); lastWatchFile = false; null }
    }

    /* `fetchRaw`/`fetchData` entfernt (49) — sie waren die Huelle um
       `readData`. `parseData` bleibt: Es wertet die schlanke `watch.json`
       ebenso aus wie einen alten Zwischenspeicher. */

    fun parseData(db: JSONObject): AppData {

        val courses = ArrayList<CourseDef>()
        val ca = db.optJSONArray("courses") ?: JSONArray()

        for (i in 0 until ca.length()) {
            /* `getJSONObject` WIRFT, wenn an der Stelle etwas anderes steht —
               ein `null` im Feld reicht. Die Datei kommt vom Handy; ein
               unerwarteter Eintrag darf die Uhr nicht beenden, sondern nur
               diesen einen Platz kosten (2026-08-16 (12)). */
            val co = ca.optJSONObject(i) ?: continue
            val name = co.optString("name")

            val tees = co.optJSONObject("tees") ?: continue

            // JEDEN Tee als eigenen Eintrag anlegen. Vorher wurde nur der
            // erste Schlüssel genommen — auf der Uhr war der Abschlag damit
            // nicht wählbar, sondern schlicht der, der im JSON zuerst stand.
            /* KEIN toString() MEHR (2026-08-14 (4)).
               Vorher: JSON parsen -> dasselbe Objekt wieder zu einem String
               serialisieren -> beim Rundenstart erneut parsen. Drei volle
               Durchgaenge ueber den groessten Teil der Nutzlast, und zwar fuer
               JEDEN Platz — auch fuer die, die man nie waehlt. Bei einem Platz
               mit erkannten Baeumen sind das mehrere hundert Features; auf
               einer Wear-CPU kostet das den spuerbaren Teil der Startzeit.
               Mit (40) ist die Frage gegenstandslos: Die Uhr liest `geo` gar
               nicht mehr. Der Schluessel darf in der Datei stehen bleiben — er
               wird hier schlicht uebergangen, bis das Handy ihn mit
               PWA v4.84 aus `watchPayload()` streicht. */

            for (teeName in tees.keys()) {

                val ha = tees
                    .optJSONObject(teeName)
                    ?.optJSONArray("holes") ?: continue

                val holes = ArrayList<HoleDef>()

                for (j in 0 until ha.length()) {
                    val h = ha.optJSONObject(j) ?: continue

                    holes.add(
                        HoleDef(
                            h.optInt("hole", j + 1),
                            h.optInt("par", 4),
                            h.optInt("si", 0),
                            h.optInt("len", 0)
                        )
                    )
                }

                if (holes.isNotEmpty()) {
                    courses.add(
                        CourseDef(
                            name,
                            teeName,
                            holes
                        )
                    )
                }
            }
        }

        val opts = Options(
            strList(
                db,
                "teeResults",
                listOf(
                    "Fairway",
                    "Rough",
                    "Bunker",
                    "Wasser",
                    "Out",
                    "Bäume"
                )
            ),
            strList(
                db,
                "approachBuckets",
                listOf(
                    "<50m",
                    "50–100m",
                    "100–150m",
                    "150–200m",
                    ">200m"
                )
            ),
            strList(
                db,
                "teeClubs",
                listOf(
                    "Driver",
                    "Holz",
                    "Hybrid",
                    "Eisen"
                )
            ),
            strList(
                db,
                "approachLies",
                listOf(
                    "Fairway",
                    "Rough",
                    "Semi",
                    "Bunker"
                )
            ),
            strList(
                db,
                "approachMiss",
                listOf(
                    "Grün getroffen",
                    "Kurz",
                    "Lang",
                    "Links",
                    "Rechts",
                    "Kurz links",
                    "Kurz rechts",
                    "Lang links",
                    "Lang rechts"
                )
            ),
            strList(
                db,
                "firstPuttDist",
                listOf(
                    "<1m",
                    "1–2m",
                    "2–4m",
                    "4–8m",
                    ">8m"
                )
            ),
            strList(
                db,
                "qualityOpts",
                listOf(
                    "gut",
                    "ok",
                    "schlecht"
                )
            ),
            strList(
                db,
                "bunkerTypes",
                listOf(
                    "Fairway Bunker",
                    "Green Side"
                )
            ),
            strList(
                db,
                "penaltyTypes",
                listOf(
                    "Wasser",
                    "Out",
                    "Unspielbar"
                )
            ),
            strList(
                db,
                "puttMiss",
                listOf(
                    "Gelocht",
                    "Kurz",
                    "Lang",
                    "Links",
                    "Rechts",
                    "Kurz links",
                    "Kurz rechts",
                    "Lang links",
                    "Lang rechts"
                )
            ),
            strList(
                db,
                "puttRest",
                listOf(
                    "Gelocht",
                    "Gimme",
                    "<0,5m",
                    "1m",
                    "1,5m",
                    "2m",
                    "3m",
                    ">3m"
                )
            ),
            strList(
                db,
                "kurzseitig",
                listOf(
                    "Grün getroffen",
                    "Viel Platz zur Fahne",
                    "Wenig Platz — Fahne nah am Rand"
                )
            )
        )

        val prof = db.optJSONObject("profile")

        val hi = prof?.let {
            if (
                it.has("hcpIndex") &&
                !it.isNull("hcpIndex")
            ) {
                it.optDouble("hcpIndex")
            } else {
                null
            }
        }

        // Schlägerlängen für den Caddy (absteigend nach Reichweite)
        val clubs = ArrayList<ClubDist>()
        val cd = db.optJSONArray("clubDistances") ?: JSONArray()

        for (i in 0 until cd.length()) {
            val o = cd.optJSONObject(i) ?: continue
            val nm = o.optString("club")
            if (nm.isNullOrBlank()) continue
            val carry = if (o.has("carry") && !o.isNull("carry")) o.optInt("carry") else null
            val total = if (o.has("total") && !o.isNull("total")) o.optInt("total") else null
            if (carry == null && total == null) continue
            clubs.add(ClubDist(nm, carry, total))
        }

        clubs.sortByDescending { it.reach }

        return AppData(
            courses,
            opts,
            hi,
            clubs,
            parseDraft(db)
        )
    }

    // Open-Meteo (kein API-Key) — exakt die Felder, die die PWA nutzt
    fun fetchWeather(lat: Double, lng: Double): Weather? {

        return try {

            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=" + String.format(Locale.US, "%.3f", lat) +
                        "&longitude=" + String.format(Locale.US, "%.3f", lng) +
                        "&current=temperature_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                        "&wind_speed_unit=ms"
            )

            val c = url.openConnection() as HttpURLConnection
            c.requestMethod = "GET"
            c.connectTimeout = 8000
            c.readTimeout = 8000

            val j = JSONObject(leseBegrenzt(c, "Wetter"))

            c.disconnect()

            val cur = j.optJSONObject("current") ?: return null

            Weather(
                if (cur.has("temperature_2m")) cur.optDouble("temperature_2m") else null,
                if (cur.has("wind_speed_10m")) cur.optDouble("wind_speed_10m") else null,
                if (cur.has("wind_direction_10m")) cur.optDouble("wind_direction_10m") else null,
                if (cur.has("wind_gusts_10m")) cur.optDouble("wind_gusts_10m") else null,
                System.currentTimeMillis()
            )

        } catch (e: Exception) { if (e.istAbbruch()) throw e; Fehler.add("Wetter", e); null }
    }

    /**
     * Pusht den Runden-Entwurf. Zusätzlich werden die auf der Uhr gemessenen
     * Schläge additiv in DB.gpsShots gemergt (Dedupe über id) — der Rest des
     * Datenbestands bleibt unangetastet.
     */
    // Ergebnis eines Draft-Pushes: ok + der loch-genau GEMERGTE Rundenstand
    // (Uhr-Eingaben über Repo-Stand), damit der Aufrufer fremde Eingaben
    // (z. B. vom Handy) in die lokalen entries übernehmen kann.
    data class PushResult(
        val ok: Boolean,
        val mergedHoles: JSONArray? = null,
        // Loch, auf dem das HANDY steht (aus _draftRound.live), falls neuer
        // als der eigene Zeiger. null = Handy gibt nichts vor.
        val remoteHole: Int? = null
    )

    // Löcher zweier Entwürfe DERSELBEN Runde feldweise vereinen:
    // Basis = Repo-Entwurf (älter), darüber alle GESETZTEN Felder der Uhr (neuer).
    // null löscht nie — exakt die Regel aus mergeDB der PWA (index.html devdocs).
    private fun mergeDraftHoles(
        repoRound: JSONObject,
        watchRound: JSONObject
    ) {
        val map = LinkedHashMap<Int, JSONObject>()

        repoRound.optJSONArray("holes")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { h ->
                    map[h.optInt("hole")] = JSONObject(h.toString())
                }
            }
        }

        watchRound.optJSONArray("holes")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { h ->
                    val n = h.optInt("hole")
                    val t = map.getOrPut(n) { JSONObject().put("hole", n) }
                    for (k in h.keys()) {
                        if (!h.isNull(k)) t.put(k, h.get(k))
                    }
                }
            }
        }

        val merged = JSONArray()
        map.values
            .sortedBy { it.optInt("hole") }
            .forEach { merged.put(it) }

        watchRound.put("holes", merged)
    }

    // Zeitstempel des zuletzt SELBST geschriebenen live-Zeigers. Nur ein
    // Fremdzeiger, der jünger ist, darf das Loch der Uhr umstellen — sonst
    // würde die Uhr ihr eigenes Blättern per Echo wieder zurücknehmen.
    @Volatile
    private var ownLiveAt: String = ""
    /* ==================================================================
       WANN HAT DER BENUTZER ZULETZT SELBST GEBLAETTERT? (2026-08-24 (4))
       --------------------------------------------------------------------
       GEMELDET: „Die Lochanzeige des Handys ueberstimmt immer die der Uhr."
       Genau so war es, und zwar zwangslaeufig:
       `ownLiveAt` wurde bei JEDEM Push gesetzt (`ownLiveAt = now`). Verglichen
       wurde aber der WERT VON VORHER. Die Uhr sendet etwa im Minutentakt, das
       Handy schreibt seinen Zeiger alle paar Sekunden — also ist der Zeiger
       des Handys beim naechsten Uhr-Push praktisch immer juenger, und
       `at > ownLiveAt` traf jedes Mal zu. Die Uhr uebernahm das Handy-Loch,
       selbst wenn der Benutzer gerade eben auf der Uhr geblaettert hatte.
       Schlimmer noch: `h != currentHole` machte es zur Bedingung, dass sich
       die beiden UNTERSCHEIDEN — also genau dann, wenn die Uhr-Eingabe frisch
       war, wurde sie verworfen.
       `ownHoleAt` merkt sich stattdessen die EINGABE. Das Handy uebernimmt nur
       noch, wenn sein Zeiger juenger ist als die letzte Handlung auf der Uhr.
       Eine Handlung des Benutzers wiegt schwerer als ein automatischer Zeiger. */
    @Volatile private var ownHoleAt: String = ""
    /* ==================================================================
       DER LOCHWAHL-ZAEHLER (2026-08-25 (34))
       --------------------------------------------------------------------
       Zeitstempel vergleichen Uhren zweier Geraete — (29), (32) und (33)
       sind daran nacheinander gescheitert. Der Zaehler vergleicht WILLEN:
       Nur eine BENUTZERHANDLUNG erhoeht ihn (hier wie in der PWA), es
       gewinnt schlicht die hoehere Nummer. Ein Echo traegt nie eine
       hoehere Nummer als die, die es gesehen hat — es ist wirkungslos,
       egal wie spaet es kommt oder wie frisch es gestempelt ist. Genau
       dieses Prinzip meldet in der Eingabespur seit Tagen "keine Luecke";
       der Lochzeiger bekommt es jetzt auch.
       `holeSeqGesehen` hebt den eigenen Stand auf fremde Nummern an
       (Uebernahme ist keine Handlung, sie zaehlt nicht selbst hoch) —
       so setzt die naechste eigene Handlung auf dem Maximum auf. */
    @Volatile private var ownHoleSeq: Int = 0
    fun holeSeqStand(): Int = ownHoleSeq
    fun holeSeqGesehen(seq: Int) { if (seq > ownHoleSeq) ownHoleSeq = seq }
    /* Heisst weiterhin `holeGewechselt`, meint aber seit (19): "der Benutzer
       hat sich zu diesem Loch bekannt" — Blaettern UND Eingabe. Beides
       erhoeht jetzt auch den Zaehler; die at-Marke bleibt fuers alte Netz. */
    fun holeGewechselt() { ownHoleAt = isoNow(); ownHoleSeq += 1 }
    /* Wann ging zuletzt etwas raus? Der Herzschlag laesst einen Durchlauf aus,
       wenn eine Benutzerhandlung gerade eben gesendet hat (2026-08-24 (5)). */
    @Volatile var letzterPushMs: Long = 0L
        private set
    fun pushGemeldet() { letzterPushMs = System.currentTimeMillis() }

    fun lastOwnLiveAt(): String = ownLiveAt
    fun lastOwnHoleAt(): String = ownHoleAt
    /* ==================================================================
       ZAEHLT EIN FREMDER ZEIGER? — EINE REGEL, DREI STELLEN (2026-08-25 (33))
       --------------------------------------------------------------------
       (32) verglich Schreibzeit gegen Handlungszeit — und ein SPAETES ECHO
       hat eine junge Schreibzeit bei altem Inhalt. Verglichen wird jetzt
       HANDLUNG GEGEN HANDLUNG: `wahlAt` ist die letzte eigene Lochwahl des
       HANDY-Benutzers (PLAY.holeAt, ab PWA v4.78 im Zeiger). Uebernahmen,
       Echos und automatische Sprünge des Handys tragen keine oder eine alte
       Wahl — sie koennen die Uhr nicht mehr umstellen, egal wie frisch ihr
       Schreibstempel ist.
       OHNE `wahlAt` (aeltere PWA) gilt die bisherige Bedingung weiter —
       dasselbe Muster wie beim X-Repo-Sha-Kopf: neuer Weg, altes Netz. */
    fun fremderZeigerZaehlt(at: String?, wahlAt: String?, holeSeq: Int? = null): Boolean =
        when {
            /* (34): Zaehler schlaegt alles. Strikt groesser — bei Gleichstand
               (beide Benutzer handeln im selben Moment) bleibt das eigene
               Loch stehen; die naechste Handlung loest es auf. */
            holeSeq != null -> holeSeq > ownHoleSeq
            !wahlAt.isNullOrEmpty() -> wahlAt > ownHoleAt
            else -> !at.isNullOrEmpty() && at > ownLiveAt && at > ownHoleAt
        }

    fun pushDraft(
        round: JSONObject,
        shotMeasurements: List<JSONObject>,
        currentHole: Int? = null,
        courseName: String? = null,
        teeName: String? = null,
        // Laufende Schlagaufnahme fuer den Live-Zeiger. pushDraft kennt den
        // Compose-Zustand nicht, deshalb als Parameter.
        recLive: JSONObject? = null
    ): PushResult {

        /* ---------- NEUER WEG: nur die kleine Datei (draft.json) ----------
           Kennt der Worker sie nicht, liefert fetchDraftFile() null und es geht
           unveraendert ueber die grosse Datei weiter (Block darunter).
           Die Schlagmessungen reisen MIT in der kleinen Datei: Sie sind winzig
           (rund 150 Byte je Schlag) und duerfen nicht bis zum Rundenende
           liegenbleiben — sonst waeren sie bei einem Absturz weg. */
        val klein = fetchDraftFile()
        if (klein != null) {
            var remoteHoleK: Int? = null
            val bauen = { frisch: JSONObject? ->
                val basis = frisch ?: klein
                val prevRoundK = basis.optJSONObject("round")
                val keyK = { r: JSONObject ->
                    r.optString("date") + "|" + r.optString("course") + "|" + r.optString("side")
                }
                if (prevRoundK != null && keyK(prevRoundK) == keyK(round)) {
                    mergeDraftHoles(prevRoundK, round)
                }
                /* Diesen Vorgang beschreiben, unabhaengig davon, wessen Zeiger
                   gerade im Entwurf liegt (2026-08-25 (30)). */
                Diagnose.pulsLoch = currentHole
                Diagnose.pulsZeiger = if (currentHole != null) "gesetzt" else "übersprungen"
                val prevLiveK = basis.optJSONObject("live")
                if (prevLiveK != null && prevLiveK.optString("src") != "watch") {
                    val at = prevLiveK.optString("at")
                    val h = prevLiveK.optInt("hole", 0)
                    /* ==================================================================
                   MESSEN STATT RATEN (2026-08-24 (9))
                   --------------------------------------------------------------------
                   GEMELDET: „Einmal geht eine Eingabe durch, danach nichts mehr."
                   Ich habe diese Stelle jetzt viermal aus dem Kopf repariert und
                   dreimal danebengelegen. Deshalb schreibt sie ab sofort auf, WAS
                   sie entschieden hat — einmal je Vorgang, in den Ringpuffer, der
                   seit (8) auch beim Handy ankommt.
                   `Fehler.warn` und nicht `add`: Es ist kein Fehler, sondern eine
                   Auskunft. Genau das ist die Trennung, die der Puffer seit
                   2026-08-16 (15) kennt.
                   NUR BEI ABWEICHUNG protokollieren — eine Zeile je Herzschlag
                   waere in zehn Minuten der ganze Puffer. */
                /* Bis wohin ist das Handy gekommen? Es schreibt die Nummer in
                   seinen Zeiger zurueck (2026-08-25 (23)). Bleibt sie stehen,
                   waehrend die Uhr weiterzaehlt, ist die Luecke auf den Schritt
                   genau sichtbar. */
                /* Der Zeiger heisst in DIESEM Zweig `prevLiveK` — `prevLive` gibt
                   es nur im Voll-Push weiter unten. Der erste Versuch nahm den
                   falschen Namen; Kotlin faengt das, aber erst beim Bauen. */
                /* OBERSTE EBENE ZUERST (2026-08-25 (29)): Im Zeiger steht die
                   Quittung fast nie, weil ihn beide Seiten ueberschreiben und
                   die Uhr um ein Vielfaches oefter sendet. Deshalb stand im
                   Puls „Handy sah #13", waehrend das Handy bei #27 war. */
                /* ==================================================================
                   DIE ANZEIGE DER DIAGNOSE STAND IM FALSCHEN BLOCK
                   (2026-08-25 (30))
                   --------------------------------------------------------------------
                   IM PULS STAND „Loch 4/18 · … · eigenes Loch 1" — und ich habe
                   daraus geschlossen, die Uhr sende das falsche Loch. FALSCH:
                   `Diagnose.pulsLoch` wurde INNERHALB von
                   `if (prevLiveK != null && src != "watch")` gesetzt, also nur
                   dann, wenn im Entwurf gerade der Zeiger des HANDYS lag. Das
                   ist der seltene Fall — die Uhr sendet um ein Vielfaches
                   oefter. In allen anderen Durchlaeufen behielt `pulsLoch`
                   seinen ALTEN Wert: Loch 1, vom allerersten Push.
                   GESENDET WURDE DIE GANZE ZEIT DAS RICHTIGE. Angezeigt wurde
                   ein Wert von vorhin.
                   Jetzt stehen `pulsLoch` und `pulsZeiger` VOR der Bedingung —
                   sie beschreiben diesen Vorgang und nicht den Zeiger des
                   anderen Geraets.
                   ZUM MITNEHMEN: Eine Diagnose, die nur manchmal aktualisiert
                   wird, luegt in allen uebrigen Faellen — und zwar
                   glaubwuerdig. Ich habe ihr zwei Fassungen lang geglaubt und
                   an der falschen Stelle repariert. */
                val sahOben = basis.optInt("seenAktion", -1)
                if (sahOben >= 0) Diagnose.handySah = sahOben
                else prevLiveK?.let { pl ->
                    val sah = pl.optInt("seenAktion", -1)
                    if (sah >= 0) Diagnose.handySah = sah
                }
                Diagnose.pulsFremd = if (h > 0) "$h (at=$at)" else "keines"
                val wahlK = prevLiveK.optString("wahlAt").ifEmpty { null }
                val seqK = prevLiveK.optInt("holeSeq", 0).takeIf { it > 0 }
                /* ERST entscheiden, DANN den Stand anheben — sonst macht die
                   Beobachtung die eigene Entscheidung zunichte (34). */
                val zaehltK = fremderZeigerZaehlt(at, wahlK, seqK)
                seqK?.let { holeSeqGesehen(it) }
                if (h > 0 && zaehltK && h != currentHole) remoteHoleK = h
                else if (h > 0 && h != currentHole) {
                    Fehler.warn("Zeiger",
                        "Handy-Loch $h verworfen · seq=${seqK ?: "—"}/eigen $ownHoleSeq · " +
                        "at=$at · wahl=${wahlK ?: "—"} · ownLive=$ownLiveAt · ownHole=$ownHoleAt")
                }
                }
                val d = JSONObject().put("round", round).put("ts", isoNow())
                if (currentHole != null) {
                    val hole = remoteHoleK ?: currentHole
                    val now = isoNow()
                    ownLiveAt = now
                    d.put(
                        "live",
                        JSONObject()
                            .put("src", "watch")
                    /* ==============================================================
                       DIE UHR SAGT, WELCHE FASSUNG SIE IST (2026-08-29 (52))
                       --------------------------------------------------------------
                       BEFUND AUS DEM AUDIT vom 29.08.: Ob auf dem Geraet wirklich
                       die neueste Fassung laeuft, wusste niemand. Der Pruefstand
                       vergleicht nur Dateien, die ohnehin zusammen geschrieben
                       werden; die Fassung der Uhr stand allein auf ihrem eigenen
                       Bildschirm.
                       DAS HAT IN DER WOCHE VOM 24.–29.08. MEHRFACH STUNDEN
                       GEKOSTET: Behebungen wurden auf dem Platz geprueft, bevor sie
                       auf dem Handgelenk waren — und beide Seiten hielten die
                       Korrektur fuer wirkungslos. Genau dasselbe ist der PWA
                       passiert, bis sie seit v5.02 ihren Startvermerk schreibt.
                       `app` STEHT JETZT IM ZEIGER, den die Uhr ohnehin im Takt
                       schickt: kein zusaetzlicher Abruf, keine zusaetzliche Datei,
                       rund 20 Byte. Das Handy protokolliert sie beim ersten Sehen
                       und bei jedem Wechsel — damit steht im Fehlerprotokoll,
                       WELCHE Fassungen wirklich miteinander geredet haben.
                       WARUM NICHT IN `note`: Das ist ein Freitext, der sich mit
                       jeder Fassung aendert. Eine Fassungsnummer muss man
                       VERGLEICHEN koennen, nicht lesen. */
                    .put("app", WATCH_APP)
                    .put("note", WATCH_NOTE)
                            .put("hole", hole)
                            .put("at", now)
                            .put("holeSeq", ownHoleSeq)
                            .put("course", courseName ?: round.optString("course"))
                            .put("tee", teeName ?: round.optString("tee"))
                            .put("date", round.optString("date"))
                            .put("side", round.optString("side"))
                            .also { lv ->
                                if (recLive != null) lv.put("rec", recLive)
                        /* EIGENE POSITION MELDEN (2026-08-16 (4)).
                           Das Handy ist beim Caddy fuehrend, rechnet aber mit
                           SEINER Position — und das Handy liegt oft im Trolley,
                           waehrend man am Ball steht. Zwanzig Meter Unterschied
                           sind ein halber Schlaeger.
                           Die Uhr meldet deshalb, wo sie steht; das Handy
                           rechnet FUER DIESEN PUNKT und schickt das Ergebnis
                           zurueck. Die Uhr hat den richtigen Ort, das Handy die
                           bessere Rechnung.
                           NUR MIT BRAUCHBARER GENAUIGKEIT: Eine Position mit
                           30 m Streuung wuerde die Rechnung verschlechtern
                           statt sie zu verbessern. */
                        Live.fixUi?.let { f ->
                            if (f.acc <= FixQuality.MAX_ACC) {
                                lv.put(
                                    "pos",
                                    JSONObject()
                                        .put("src", "watch")
                    .put("note", WATCH_NOTE)
                                        .put("lat", f.lat)
                                        .put("lng", f.lng)
                                        .put("acc", f.acc.toDouble())
                                        .put("at", isoNow())
                                )
                            }
                        }
                            }
                    )
                }
                /* gpsShots vereinen: was in der Datei steht, bleibt; eigene
                   Messungen kommen nach ID dazu. Beide Geraete schreiben in
                   dieselbe Liste, deshalb NIE ersetzen. */
                val arr = basis.optJSONArray("gpsShots") ?: JSONArray()
                val have = HashSet<String>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.optString("id")?.let { have.add(it) }
                }
                shotMeasurements.forEach { sm ->
                    val id = sm.optString("id")
                    if (id.isNotEmpty() && !have.contains(id)) { arr.put(sm); have.add(id) }
                }
                if (arr.length() > 0) d.put("gpsShots", arr)
                /* ==================================================================
                   DAS PROTOKOLL GEHOERT HIERHIN (2026-08-24 (8))
                   --------------------------------------------------------------------
                   Bis hierher hing es an `val draft` weiter unten — das ist der
                   Aufbau fuer die GROSSE Datei, also den Notweg, der praktisch nie
                   laeuft. Das Handy las `_draftRound.watchLog` aus `draft.json` und
                   fand deshalb NIE etwas: eingebaut und wirkungslos.
                   Hier ist der Weg, der bei jedem Herzschlag laeuft. */
                /* DIE SPUR REIST MIT (2026-08-25 (23)). Rund 1 kB neben dem
                   Rundenentwurf — der geht ohnehin im Minutentakt raus. */
                d.put("aktionNr", Diagnose.aktionNr)
                if (Diagnose.aktionen.isNotEmpty())
                    d.put("aktionen", JSONArray(Diagnose.aktionen))
                if (Fehler.liste.isNotEmpty()) {
                    d.put(
                        "watchLog",
                        JSONObject()
                            .put("at", isoNow())
                            .put("app", WATCH_APP)
                            .put("note", WATCH_NOTE)
                            .put("geraet", Build.MANUFACTURER + " " + Build.MODEL)
                            .put("zeilen", JSONArray(Fehler.liste))
                        .apply {
                            if (Diagnose.letzterBericht.isNotBlank()) {
                                put("bericht", Diagnose.letzterBericht)
                                put("berichtAt", Diagnose.berichtAt)
                            }
                        }
                    )
                }
                d
            }
            val ok = pushDraftFile(bauen)
            if (ok) {
                return PushResult(true, round.optJSONArray("holes"), remoteHoleK)
            }
            // Schreiben fehlgeschlagen -> alter Weg als Sicherheitsnetz
        }

        /* ======================================================================
           DER ALTE WEG IST ENTFERNT — ER HAT DIE APP GETOETET (2026-08-28 (48))
           ----------------------------------------------------------------------
           GEMELDET am 28.08.: „Das Schlagtracken dauert sehr lange bis es
           startet, auch nach mehreren Anlaeufen nicht. Dann bricht es
           zwischendurch ab und die ganze App schliesst sich."
           DAS UHR-PROTOKOLL NENNT DIE URSACHE WOERTLICH:

             07:48:29 ABSTURZ OutOfMemoryError: Failed to allocate a
             14784520 byte allocation with 3204560 free bytes
             @Net.readData:3818 < Net.pushDraft:4466

           HIER STAND DER RUECKFALLWEG: Gelang der schlanke Schreibvorgang in
           `draft.json` nicht, holte diese Stelle die GROSSE
           `trainingsdaten.json` — mehrere Megabyte —, baute daraus ein
           JSONObject, aenderte darin den Entwurf und schickte alles zurueck.
           DREI GRUENDE, WARUM DAS WEG MUSSTE, jeder fuer sich ausreichend:
           1. ER KANN SEIT WORKER v2.9 GAR NICHT MEHR GELINGEN. Der ALT-Modus
              ist dort geschlossen und antwortet mit 426 „Upgrade Required".
              Der Weg war also seit Wochen ein garantierter Fehlschlag — nur
              ein sehr teurer.
           2. ER SPRENGTE DEN SPEICHER. Rohtext, JSONObject und die
              `toString()`-Ausgabe zum Senden liegen gleichzeitig im Heap; bei
              128 MB Grenze auf der Uhr reicht das fuer den Absturz. Und ein
              Absturz mitten in der Runde ist der teuerste Fehler ueberhaupt.
           3. ER LIEF GENAU DANN, WENN ES OHNEHIN KLEMMTE. Ausgeloest wurde er
              vom Fehlschlag des schlanken Weges — also bei schlechtem Funk auf
              dem Platz. Auf eine ueberlastete Leitung legte er mehrere
              Megabyte obendrauf. Das Protokoll vom 28.08. zeigt die Folge:
              Zeitueberschreitung an Zeitueberschreitung, 37 von 60 Vorgaengen
              misslungen.
           EIN SICHERHEITSNETZ, DAS BEI JEDEM AUFFANGEN REISST UND DEN
           SPRINGENDEN MITNIMMT, IST KEINES.
           WAS STATTDESSEN PASSIERT: Der Fehlschlag wird gemeldet und der
           Durchlauf endet. Der naechste Takt versucht es erneut — mit dem
           schlanken Weg, der wenige Kilobyte kostet. Nichts geht verloren:
           Alles liegt weiter lokal (`persist`), und der Entwurf wird beim
           naechsten gelungenen Vorgang vollstaendig uebertragen. */
        Fehler.warnEinmal(
            "draftNurSchlank",
            "Entwurf senden",
            "Schlanker Weg misslungen — der naechste Takt versucht es erneut. " +
            "Der alte Weg ueber die grosse Datei ist mit (48) entfernt: Er " +
            "konnte seit Worker v2.9 nicht mehr gelingen (426) und hat die App " +
            "mit OutOfMemory beendet."
        )
        return PushResult(false, round.optJSONArray("holes"), null)
    }
}

private fun jn(b: Boolean?) =
    if (b == null) null
    else if (b) "Ja"
    else "Nein"

// Genauigkeitswerte auf eine Nachkommastelle — reicht fuer die Gewichtung
// und haelt den JSON klein.
private fun round1(f: Float): Double =
    Math.round(f * 10.0) / 10.0

private fun round6(d: Double): Double =
    Math.round(d * 1e6) / 1e6

private fun shotsToJson(shots: List<ShotPt>): JSONArray {
    val a = JSONArray()
    shots.forEach {
        a.put(
            JSONObject()
                .put("lat", round6(it.lat))
                .put("lng", round6(it.lng))
                .put("club", it.club)
        )
    }
    return a
}

private fun jsonToShots(a: JSONArray?): List<ShotPt> {
    if (a == null) return emptyList()
    val out = ArrayList<ShotPt>(a.length())
    for (i in 0 until a.length()) {
        val o = a.optJSONObject(i) ?: continue
        out.add(
            ShotPt(
                o.optDouble("lat"),
                o.optDouble("lng"),
                o.optString("club", "")
            )
        )
    }
    return out
}

private fun buildRoundJson(
    course: CourseDef,
    tee: String,
    hi: Double?,
    eds: Boolean,
    entries: Map<Int, HoleEntry>,
    weather: Weather?,
    // Vom Handy übernommene Runde: dessen id MUSS zurückgeschrieben werden,
    // sonst legt die PWA beim Speichern eine zweite Runde an.
    roundId: String? = null,
    side: String = "18 Loch"
): JSONObject {

    val r = JSONObject()

    roundId?.let { r.put("id", it) }
    /* DIE UHR ECHOT DIE NAMEN NICHT MEHR (42). (37) tat es aus Sorge, der
       Merge am Handy koennte sie sonst verlieren — nachgemessen in der
       Rundensimulation stimmt das Gegenteil: `Object.assign(alt, neu)`
       UEBERGEHT einen fehlenden Schluessel und behaelt die Namen; ein
       MITGESCHICKTER, aber veralteter Stand ueberschreibt sie dagegen.
       Schweigen ist hier also der sichere Weg — und die Regel vom 27.08.
       verlangt ihn ohnehin: Das Handy fuehrt, die Uhr sagt zu Namen nichts.
       (Die Loch-Scores `msc1..msc3` reisen unveraendert weiter — die trägt
       die Uhr ein, und sie sind ihre eigene Aussage.) */
    r.put("date", today())
    r.put("course", course.name)
    r.put("tee", tee)
    // Vokabular EXAKT wie ROUND_KINDS der PWA:
    //   side: "18 Loch" | "Front 9" | "Back 9"
    //   type: "18 Loch" | "9 Loch"
    // Andere Strings lassen activeHoles() am Handy ins Leere laufen.
    r.put("side", side)
    r.put("type", if (side == "18 Loch") "18 Loch" else "9 Loch")
    r.put("countHcp", eds)

    if (hi != null) {
        r.put("hi", hi)
    }

    // Bedingungen wie in der PWA (round.conditions)
    if (weather != null) {
        val c = JSONObject()
        weather.temp?.let { c.put("temp", Math.round(it * 10) / 10.0) }
        weather.windMs?.let { c.put("windMs", Math.round(it * 10) / 10.0) }
        weather.windDir?.let { c.put("windDir", it.roundToInt()) }
        if (c.length() > 0) r.put("conditions", c)
    }

    val holes = JSONArray()

    for (hd in course.holes) {

        val e = entries[hd.hole] ?: continue

        if (e.empty()) {
            continue
        }

        val h = JSONObject()
            .put("hole", hd.hole)
            /* par/len/si MUESSEN mit. Ohne par liefert die Strokes-Gained-
               Rechnung der PWA (sgHole) fuer dieses Loch GAR NICHTS zurueck,
               und ohne len/si stimmen Netto-Wertung und Erwartungswert nicht.
               Bei einer am Handy gestarteten Runde faellt das nicht auf, weil
               die PWA ihre eigenen Lochdaten hat — bei einer Runde, die allein
               auf der Uhr laeuft, fehlten sie bisher vollstaendig. */
            .put("par", hd.par)
            .put("len", hd.len)
            .put("si", hd.si)

        e.score?.let {
            h.put("score", it)
        }

        e.putts?.let {
            h.put("putts", it)
        }

        e.msc1?.let { h.put("msc1", it) }
        e.msc2?.let { h.put("msc2", it) }
        e.msc3?.let { h.put("msc3", it) }

        /* Der Loch-Zeitstempel MUSS mit ins Repo — auf ihm beruht die
           Entscheidung, wessen Aenderung gilt (2026-08-15 (7)). Ohne ihn
           faellt der Abgleich auf „nur leere Felder fuellen" zurueck. */
        e.ts?.let {
            h.put("ts", it)
        }

        e.tee?.let {
            h.put("tee", it)
        }

        e.appr?.let {
            h.put("appr", it)
        }

        e.apprMiss?.let {
            h.put("apprMiss", it)
        }

        e.apprClub?.let {
            h.put("apprClub", it)
        }

        e.penN?.let {
            h.put("penN", it)
        }

        e.firstPutt?.let {
            h.put("firstPutt", it)
        }

        e.puttMiss?.let {
            h.put("puttMiss", it)
        }

        e.puttRest?.let {
            h.put("puttRest", it)
        }

        e.kurzseitig?.let {
            h.put("kurzseitig", it)
        }

        e.quality?.let {
            h.put("quality", it)
        }

        e.club?.let {
            h.put("club", it)
        }

        e.lie?.let {
            h.put("lie", it)
        }

        e.distToPin?.let {
            h.put("distToPin", it)
        }

        e.bunkerN?.let {
            h.put("bunkerN", it)
        }

        e.b1?.let {
            h.put("b1", it)
        }

        e.penType?.let {
            h.put("penType", it)
        }

        jn(e.ud)?.let {
            h.put("ud", it)
        }

        jn(e.ss)?.let {
            h.put("ss", it)
        }

        jn(e.recovery)?.let {
            h.put("recovery", it)
        }

        jn(e.gir)?.let {
            h.put("girDirect", it)
        }

        // Schlagtracking — exakt das Format, das die PWA in RTRACK erwartet
        if (e.shots.isNotEmpty()) {
            h.put("shots", shotsToJson(e.shots))
        }

        holes.put(h)
    }

    r.put("holes", holes)

    return r
}

// ================= lokale Sicherung =================

private const val PREFS = "golfwatch"

private fun entryToJson(
    k: Int,
    e: HoleEntry
) = JSONObject().apply {

    put("hole", k)

    e.ts?.let { put("ts", it) }

    e.score?.let {
        put("score", it)
    }

    e.putts?.let {
        put("putts", it)
    }

    e.msc1?.let { put("msc1", it) }
    e.msc2?.let { put("msc2", it) }
    e.msc3?.let { put("msc3", it) }

    e.tee?.let {
        put("tee", it)
    }

    e.appr?.let {
        put("appr", it)
    }

    /* FEHLTEN beim lokalen Sichern (2026-08-15 (8)): `buildRoundJson` schrieb
       beide, `entryToJson` nicht. Nach einem Neustart der Uhr waren sie lokal
       weg — im Repo standen sie, aber der lokale Stand gewinnt beim Start. */
    e.apprMiss?.let {
        put("apprMiss", it)
    }

    e.apprClub?.let {
        put("apprClub", it)
    }

    e.penN?.let {
        put("penN", it)
    }

    e.firstPutt?.let {
        put("firstPutt", it)
    }

    e.puttMiss?.let {
        put("puttMiss", it)
    }

    e.puttRest?.let {
        put("puttRest", it)
    }

    e.kurzseitig?.let {
        put("kurzseitig", it)
    }

    e.quality?.let {
        put("quality", it)
    }

    e.club?.let {
        put("club", it)
    }

    e.lie?.let {
        put("lie", it)
    }

    e.distToPin?.let {
        put("distToPin", it)
    }

    e.bunkerN?.let {
        put("bunkerN", it)
    }

    e.b1?.let {
        put("b1", it)
    }

    e.penType?.let {
        put("penType", it)
    }

    jn(e.ud)?.let {
        put("ud", it)
    }

    jn(e.ss)?.let {
        put("ss", it)
    }

    jn(e.recovery)?.let {
        put("recovery", it)
    }

    // PWA-Feldname ist girDirect — auch im Entwurf (playRound liefert
    // dieselben Hole-Objekte). Vorher stand hier "gir": beim Fortsetzen
    // einer Watch-Runde am Handy fiel der Wert still weg.
    jn(e.gir)?.let {
        put("girDirect", it)
    }

    if (e.shots.isNotEmpty()) {
        put("shots", shotsToJson(e.shots))
    }
}

private fun optS(
    o: JSONObject,
    k: String
): String? =
    if (
        o.has(k) &&
        !o.isNull(k)
    ) {
        o.optString(k)
    } else {
        null
    }

private fun optI(
    o: JSONObject,
    k: String
): Int? =
    /* `getInt` wirft, wenn der Wert ein nicht zahlenfoermiger Text ist —
       „""" statt 5 reicht. `optInt` mit Wachwert kann das nicht (2026-08-16 (12)). */
    if (
        o.has(k) &&
        !o.isNull(k)
    ) {
        o.optInt(k, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
    } else {
        null
    }

private fun optB(
    o: JSONObject,
    k: String
): Boolean? =
    if (
        o.has(k) &&
        !o.isNull(k)
    ) {
        o.getBoolean(k)
    } else {
        null
    }

/* BENANNTE Argumente, nicht positionelle: Diese Funktion stand frueher auf
   Positionen. Ein neues Feld in HoleEntry (hier apprMiss nach appr) haette
   damit ALLE folgenden Werte still um eine Stelle verschoben — Putts waeren
   als Tee-Ergebnis gelandet, ohne dass irgendetwas abgestuerzt waere.
   ZUSAETZLICH BEHOBEN: gelesen wurde "gir", geschrieben aber "girDirect"
   (so heisst das Feld in der PWA). Beim Uebernehmen einer Runde vom Handy
   ging GIR deshalb still verloren — dieselbe Fehlerklasse wie im Entwurf. */
private fun jsonToEntry(
    o: JSONObject
) = HoleEntry(
    ts = optS(o, "ts"),
    score = optI(o, "score"),
    putts = optI(o, "putts"),
    tee = optS(o, "tee"),
    appr = optS(o, "appr"),
    apprMiss = optS(o, "apprMiss"),
    apprClub = optS(o, "apprClub"),
    penN = optI(o, "penN"),
    firstPutt = optS(o, "firstPutt"),
    puttMiss = optS(o, "puttMiss"),
    puttRest = optS(o, "puttRest"),
    kurzseitig = optS(o, "kurzseitig"),
    quality = optS(o, "quality"),
    club = optS(o, "club"),
    lie = optS(o, "lie"),
    distToPin = optI(o, "distToPin"),
    bunkerN = optI(o, "bunkerN"),
    b1 = optS(o, "b1"),
    penType = optS(o, "penType"),
    ud = optB(o, "ud"),
    ss = optB(o, "ss"),
    recovery = optB(o, "recovery"),
    gir = optB(o, "girDirect") ?: optB(o, "gir"),
    msc1 = optI(o, "msc1"),
    msc2 = optI(o, "msc2"),
    msc3 = optI(o, "msc3"),
    shots = jsonToShots(o.optJSONArray("shots"))
)

private fun saveLocal(
    ctx: Context,
    course: CourseDef,
    tee: String,
    hi: Double?,
    eds: Boolean,
    roundStart: Long?,
    entries: Map<Int, HoleEntry>,
    clubs: List<ClubDist>,
    measurements: List<JSONObject>,
    roundId: String? = null,
    side: String = "18 Loch"
) {

    val o = JSONObject()

    val c = JSONObject()
        .put("name", course.name)
        .put("tee", tee)

    val ha = JSONArray()

    course.holes.forEach {
        ha.put(
            JSONObject()
                .put("hole", it.hole)
                .put("par", it.par)
                .put("si", it.si)
                .put("len", it.len)
        )
    }

    c.put("holes", ha)

    /* KEINE GEOMETRIE MEHR IM LOKALEN STAND (40). Hier wurde die Platzkarte des
       gewaehlten Platzes als Text gesichert — der mit Abstand groesste Teil der
       gespeicherten Runde. Ohne Distanzen und Caddy braucht sie niemand, und
       eine gesicherte Runde wird damit um Groessenordnungen kleiner. Ein
       AELTERER Stand darf den Schluessel weiterhin enthalten; er wird beim
       Lesen uebergangen. */

    o.put("course", c)
    roundId?.let { o.put("roundId", it) }
    o.put("side", side)

    if (hi != null) {
        o.put("hi", hi)
    }

    o.put("eds", eds)

    if (roundStart != null) {
        o.put("roundStart", roundStart)
    }

    val ea = JSONArray()

    entries.forEach { (k, e) ->
        ea.put(
            entryToJson(k, e)
        )
    }

    o.put("entries", ea)

    // Schlägerlängen (für den Caddy ohne Netz)
    val cl = JSONArray()
    clubs.forEach {
        val j = JSONObject().put("club", it.club)
        it.carry?.let { v -> j.put("carry", v) }
        it.total?.let { v -> j.put("total", v) }
        cl.put(j)
    }
    o.put("clubs", cl)

    // Fahnentiefen dieses Platzes
    // Fahnentiefen entfallen (2026-08-09): Ziel ist die Gruenmitte.

    // noch nicht gepushte Schlag-Messungen
    val ms = JSONArray()
    measurements.forEach { ms.put(it) }
    o.put("measurements", ms)

    ctx.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )
        .edit()
        .putString("round", o.toString())
        .apply()
}

private data class Loaded(
    val course: CourseDef,
    val tee: String,
    val hi: Double?,
    val eds: Boolean,
    val roundStart: Long?,
    val entries: MutableMap<Int, HoleEntry>,
    val clubs: List<ClubDist>,
    val measurements: List<JSONObject>,
    // Ohne diese beiden ging beim Fortsetzen verloren, dass die Runde vom
    // Handy stammt (-> die PWA legte beim Speichern eine zweite an) und
    // welchen Umfang sie hat.
    val roundId: String? = null,
    val side: String = "18 Loch"
)

private fun loadLocal(
    ctx: Context
): Loaded? {

    val s = ctx.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )
        .getString("round", null)
        ?: return null

    return try {

        val o = JSONObject(s)

        val c = o.getJSONObject("course")
        val ha = c.getJSONArray("holes")

        val holes = ArrayList<HoleDef>()

        for (i in 0 until ha.length()) {

            val h = ha.getJSONObject(i)

            holes.add(
                HoleDef(
                    h.getInt("hole"),
                    h.getInt("par"),
                    h.optInt("si", 0),
                    h.optInt("len", 0)
                )
            )
        }

        val teeN = c.optString(
            "tee",
            "Gelb"
        )

        // `geo` in aelteren Staenden wird uebergangen (40).
        val course = CourseDef(
            c.getString("name"),
            teeN,
            holes
        )

        val map = HashMap<Int, HoleEntry>()

        val ea = o.getJSONArray("entries")

        for (i in 0 until ea.length()) {

            val e = ea.getJSONObject(i)

            map[e.getInt("hole")] =
                jsonToEntry(e)
        }

        val clubs = ArrayList<ClubDist>()
        o.optJSONArray("clubs")?.let { a ->
            for (i in 0 until a.length()) {
                val j = a.optJSONObject(i) ?: continue
                clubs.add(
                    ClubDist(
                        j.optString("club"),
                        if (j.has("carry")) j.optInt("carry") else null,
                        if (j.has("total")) j.optInt("total") else null
                    )
                )
            }
        }
        clubs.sortByDescending { it.reach }


        val ms = ArrayList<JSONObject>()
        o.optJSONArray("measurements")?.let { a ->
            for (i in 0 until a.length()) {
                a.optJSONObject(i)?.let { ms.add(it) }
            }
        }

        if (map.values.all { it.empty() }) {
            null
        } else {
            Loaded(
                course,
                teeN,
                if (o.has("hi")) {
                    o.optDouble("hi")
                } else {
                    null
                },
                o.optBoolean("eds", false),
                if (o.has("roundStart")) {
                    o.optLong("roundStart")
                } else {
                    null
                },
                map,
                clubs,
                ms,
                o.optString("roundId").ifEmpty { null },
                o.optString("side", "18 Loch").ifEmpty { "18 Loch" }
            )
        }

    } catch (e: Exception) { if (e.istAbbruch()) throw e; Fehler.add("Runde laden", e); null }
}

// ---------------------------------------------------------------------------
//  STAMMDATEN-CACHE (Plätze, Optionen, Schläger, Fahnen, Gameplans)
//
//  Vorher wurden diese Daten NUR aus dem Netz geholt. Auf dem Platz ohne
//  Empfang bedeutete das: opts == null -> die Auswahlfelder (Tee-Ergebnis,
//  Tee-Schläger, Approach) verschwanden komplett, "Neue Runde" tat nichts,
//  und "Fortsetzen" wartete erst auf einen Netz-Timeout. Der Cache liegt als
//  Datei (nicht in SharedPreferences — die Datei ist ~1 MB groß).
// ---------------------------------------------------------------------------

private const val DATA_CACHE = "data_cache.json"

private fun cacheWrite(ctx: Context, raw: String) {
    try {
        java.io.File(ctx.filesDir, DATA_CACHE).writeText(raw)
    } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("Zwischenspeicher schreiben", e)
        }
}

private fun cacheRead(ctx: Context): AppData? =
    try {
        val f = java.io.File(ctx.filesDir, DATA_CACHE)
        if (f.exists()) Net.parseData(JSONObject(f.readText())) else null
    } catch (e: Exception) { if (e.istAbbruch()) throw e; Fehler.add("Zwischenspeicher lesen", e); null }

// Netz zuerst, Cache als Rückfall. Gibt zusätzlich zurück, ob die Daten
// frisch sind — die UI sagt dem Nutzer, womit er gerade arbeitet.
// Letzter Ladefehler im Klartext. Vorher wurde jede Exception stumm
// geschluckt — ein defekter Lesepfad sah damit genauso aus wie "kein Netz".
@Volatile
private var lastLoadError: String? = null

private fun loadData(ctx: Context): Pair<AppData?, Boolean> {

    val raw = try {
        lastLoadError = null
        /* ==================================================================
           NUR NOCH DIE SCHLANKE DATEI (2026-08-28 (49))
           --------------------------------------------------------------------
           Hier stand `Net.fetchWatchRaw() ?: Net.fetchRaw()` — war `watch.json`
           nicht erreichbar, holte die Uhr die GROSSE `trainingsdaten.json`
           (rund 3 MB) und parste sie zu einem JSONObject.
           DAS WAR DER LETZTE REST DES WEGES, DER AM 28.08. DIE APP GETOETET HAT
           (OutOfMemory in `Net.readData`, siehe (48)). `leseBegrenzt` deckelt
           ihn seither, aber der Spitzenbedarf beim Parsen liegt beim
           Mehrfachen des Rohtexts — auf einer Uhr mit 128 MB Grenze ist das
           kein Rueckfall, sondern ein zweites Risiko.
           UND ER HILFT NICHT: Fehlt `watch.json`, ist entweder die Leitung weg
           — dann gelingt eine tausendmal groessere Datei erst recht nicht —
           oder das Handy hat sie nie geschrieben, und dann ist DAS zu beheben.
           Ein Rueckfall, der nur bei gutem Funk gelingt, hilft genau dann
           nicht, wenn man ihn braucht.
           WAS STATTDESSEN PASSIERT: `raw` bleibt null, und die Zeile ganz
           unten greift — der lokale Zwischenspeicher (`cacheRead`). Der ist
           schon da, kostet nichts und enthaelt denselben Stand vom letzten
           gelungenen Abruf. Er war immer die bessere Antwort; der grosse
           Rueckfall stand nur davor.
           ENTSCHIEDEN AM 28.08. auf Nachfrage: Der Rueckfall soll nicht
           bleiben. */
        Net.fetchWatchRaw()
    } catch (e: Exception) {
        lastLoadError = e.javaClass.simpleName +
                (e.message?.take(40)?.let { ": $it" } ?: "")
        null
    }

    if (raw != null) {
        try {
            var d = Net.parseData(JSONObject(raw))
            /* ==================================================================
               DIE LAUFENDE HANDY-RUNDE NACHLADEN (2026-08-15 (4)) — REGRESSION.
               `parseData` liest die Runde aus `_draftRound`. Die steht in der
               GROSSEN Datei; `watch.json` enthaelt sie bewusst NICHT, weil sie
               heiss ist und die schlanke Datei nur bei Aenderungen geschrieben
               wird. Folge: Seit dem Umstieg auf watch.json war `draft` immer
               null — „Runde vom Handy laden" verschwand vom Startbildschirm.
               Beides ist fuer sich richtig; falsch war nur, sie nicht wieder
               zusammenzufuehren. Der Entwurf kommt jetzt aus `draft.json`
               (wenige kB) und wird angehaengt.
               Die Bedingung `d.draft == null` bleibt: Ein aelterer
               Zwischenspeicher kann noch einen Entwurf enthalten. */
            if (d.draft == null) {
                val f = Net.fetchDraftFile()
                if (f != null && f.has("round")) {
                    Net.parseDraft(JSONObject().put("_draftRound", f))?.let {
                        d = d.copy(draft = it)
                    }
                }
            }
            cacheWrite(ctx, raw)
            return d to true
        } catch (e: Exception) {
            lastLoadError = "Parse: " + e.javaClass.simpleName
        }
    }

    return cacheRead(ctx) to false
}

private fun clearLocal(
    ctx: Context
) =
    ctx.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )
        .edit()
        .remove("round")
        .apply()

// Kleine Einstellungen (Caddy-Modus, Auto-Loch, Display an) getrennt sichern
private fun prefGet(ctx: Context, k: String, def: String): String =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(k, def) ?: def

private fun prefSet(ctx: Context, k: String, v: String) =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(k, v)
        .apply()

private fun prefGetB(ctx: Context, k: String, def: Boolean): Boolean =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(k, def)

private fun prefGetS(ctx: Context, k: String, def: String): String =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(k, def) ?: def

private fun prefSetS(ctx: Context, k: String, v: String) =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(k, v)
        .apply()

private fun prefSetB(ctx: Context, k: String, v: Boolean) =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(k, v)
        .apply()

// ===========================================================================
//  LIVE-GPS — Singleton, das der Foreground-Service füttert und die UI liest
// ===========================================================================

/* Scope fuer Sicherungen, die NICHT an die Composition gebunden sein duerfen.
   rememberCoroutineScope() wird beim Verlassen der Composition abgebrochen —
   eine dort gestartete Sicherung koennte also mitten im Schreiben sterben,
   ausgerechnet beim Rundenende. Dieser Scope lebt so lange wie der Prozess. */
private val diskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// ===========================================================================
//  FIX-QUALITAET — Filter und Mittelung fuer das Schlagtracking
//
//  BEWUSST NICHT im Listener: Live.fix speist auch die Distanzanzeige. Wer
//  dort hart bei 15 m verwirft, steht unter Baeumen ploetzlich ohne Zahl da —
//  obwohl 20 m Genauigkeit fuer "137 m zur Mitte" voellig reichen. Gefiltert
//  wird deshalb erst bei der VERWENDUNG, also beim Setzen von Anfangs- und
//  Endpunkt eines Schlags. Dort geht der Fehler direkt in die gelernte
//  Schlaegerlaenge ein und muss streng sein.
// ===========================================================================

object FixQuality {

    const val MAX_ACC = 15f          // schlechter -> fuer Messung unbrauchbar
    const val MAX_AGE_MS = 5_000L    // aelter -> veraltet (Bluetooth-Latenz)
    const val MOVE_LIMIT_M = 8.0     // Streuung darueber = der Spieler geht
    const val COLLECT_MS = 3_000L    // Sammelfenster am Ball

    fun usable(f: Fix?): Boolean =
        f != null &&
                f.acc <= MAX_ACC &&
                System.currentTimeMillis() - f.ts <= MAX_AGE_MS

    /* Sammelt ~3 s lang Fixes und mittelt sie INVERS-VARIANZ-GEWICHTET
       (Gewicht 1/acc²). Das schlaegt "bester Einzelfix" messbar: die Streuung
       ist weitgehend zufaellig, der gewichtete Mittelwert liegt naeher an der
       Wahrheit als jeder einzelne Wert — und ein 3-m-Sample zaehlt dabei
       ueber 16x so viel wie ein 12-m-Sample.

       Bewegungssperre: liegen die Samples weiter als MOVE_LIMIT_M
       auseinander, steht der Spieler nicht still. Mitteln waere dann falsch
       (es zoege den Punkt Richtung Startpunkt des Gehens), also nur den
       juengsten Wert nehmen. */
    /* ==========================================================================
       AUS DEM VERLAUF STATT AUS DER ZUKUNFT (2026-08-15 (12))
       --------------------------------------------------------------------------
       `collect()` sammelt 3 s NACH dem Aufruf. Von Hand passt das: Man tippt am
       Ball und wartet. Bei der AUTOMATIK passt es nicht — der Aufruf faellt in
       den Treffmoment, und danach geht man sofort los. Die Streuung
       ueberschreitet dann MOVE_LIMIT_M, und `collect` liefert nur den letzten
       Einzelfix oder gar nichts. Genau deshalb hat die automatische Erfassung
       auf der Runde nichts geliefert.
       Vor dem Treffer stand man dagegen still: beim Ansprechen, beim
       Probeschwung. Diese Fixes liegen in `Live.verlauf` — sie sind die
       besseren Messwerte, und sie sind bereits da. */
    fun ausVerlauf(fensterMs: Long = 8_000L): Fix? {
        val jetzt = System.currentTimeMillis()
        val gut = Live.verlauf.filter {
            it.acc <= MAX_ACC && jetzt - it.ts <= fensterMs
        }
        if (gut.isEmpty()) return null
        var wLat = 0.0; var wLng = 0.0; var wSum = 0.0; var accBest = Float.MAX_VALUE
        for (s in gut) {
            val a = max(1.0, s.acc.toDouble())
            val w = 1.0 / (a * a)
            wLat += s.lat * w; wLng += s.lng * w; wSum += w
            if (s.acc < accBest) accBest = s.acc
        }
        if (wSum <= 0.0) return null
        return Fix(wLat / wSum, wLng / wSum, accBest, jetzt)
    }

    suspend fun collect(onProgress: (Int) -> Unit = {}): Fix? {

        val samples = ArrayList<Fix>()
        val until = System.currentTimeMillis() + COLLECT_MS
        var lastTs = 0L

        while (System.currentTimeMillis() < until) {
            val f = Live.fix
            if (f != null && f.ts != lastTs) {
                lastTs = f.ts
                if (usable(f)) {
                    samples.add(f)
                    onProgress(samples.size)
                }
            }
            delay(200)
        }

        if (samples.isEmpty()) return null

        val newest = samples.last()

        val spread = samples.maxOf { Geo.dist(it.ll(), newest.ll()) }
        if (spread > MOVE_LIMIT_M) return newest

        var wLat = 0.0
        var wLng = 0.0
        var wSum = 0.0

        for (s in samples) {
            val a = max(1.0, s.acc.toDouble())
            val w = 1.0 / (a * a)
            wLat += s.lat * w
            wLng += s.lng * w
            wSum += w
        }

        /* Kombinierte Genauigkeit. Die reine Formel sqrt(1/wSum) waere zu
           optimistisch, weil GPS-Fehler ueber wenige Sekunden stark
           korreliert sind (Mehrwegempfang, Satellitengeometrie aendern sich
           nicht). Deshalb der Deckel: nie besser als die Haelfte des besten
           Einzelwerts. Lieber ehrlich zu schlecht als geschoent — der Wert
           steuert spaeter in der PWA die Gewichtung beim Lernen. */
        val bestAcc = samples.minOf { it.acc }
        val combined = sqrt(1.0 / wSum)

        return Fix(
            wLat / wSum,
            wLng / wSum,
            max(combined, bestAcc / 2.0).toFloat(),
            newest.ts
        )
    }
}

/* ==========================================================================
   `object Swing` IST ENTFERNT (2026-08-26 (40))
   --------------------------------------------------------------------------
   Die Schwungerkennung ueber Gyroskop und Beschleunigungssensor: rund 120
   Zeilen Sensorlogik samt Schwellen, Ruhezeit und Sammelfenster.
   AUSSER BETRIEB SEIT (35) — sie erkannte Putts prinzipbedingt nicht, die
   meisten Chips ebenso wenig, und ein erfundener Schlag faellt erst auf, wenn
   die gelernten Schlaegerlaengen falsch sind. Seither hing sie als tote
   Leitung im Quelltext; hier faellt sie mit dem uebrigen Abbau.
   WER SIE WIEDER WILL: Das Problem war nie die Physik, sondern dass ein
   fehlender Schlag mit einem Tipp nachzutragen ist und ein erfundener nicht.
   ========================================================================== */

object AmbientState {

    // true, sobald die Uhr in den gedimmten Always-On-Zustand wechselt
    var isAmbient: Boolean by mutableStateOf(false)

    // Gerät verlangt Einbrennschutz -> Inhalt minutlich leicht verschieben
    var burnIn: Boolean by mutableStateOf(false)

    // Low-Bit-Display: nur reines Schwarz/Weiß, keine Graustufen.
    // AMOLED (Watch 2R) meldet hier false; der Zweig kostet nichts.
    var lowBit: Boolean by mutableStateOf(false)

    /* MINUTENTAKT. Wear OS ruft onUpdateAmbient() rund 1x/min auf. Der Zähler
       ist der EINZIGE Weg, im Ambient eine Neuzeichnung auszulösen: Compose
       zeichnet nur neu, wenn ein gelesener State sich ändert. Wird `tick`
       nicht gelesen, bleibt die Distanzanzeige auf dem Wert stehen, der beim
       Eintritt in den Ambient galt. Deshalb liest AmbientPlayScreen ihn
       bedingungslos — auch wenn burnIn false ist. */
    var tick: Int by mutableIntStateOf(0)
}

/* ==========================================================================
   FEHLERPROTOKOLL (2026-08-16 (8))
   --------------------------------------------------------------------------
   WARUM ES DAS BRAUCHT: Stuerzt die App auf der Bahn ab, ist der Grund weg,
   sobald man sie neu startet — Logcat gibt es dort nicht. Was bleibt, ist
   „sie ist abgestuerzt", und damit ist nichts anzufangen.
   Dieses Protokoll haelt die letzten Eintraege in den Einstellungen fest, also
   ueber Neustart und Absturz hinweg, und zeigt sie auf der Detailseite.
   ZWEI QUELLEN:
     · `Fehler.add(...)` aus `catch`-Zweigen — erwartete Stoerungen (Netz,
       kaputte Antwort), die nicht zum Absturz fuehren, aber erklaeren, warum
       etwas nicht ankam.
     · Der globale Auffang fuer NICHT gefangene Ausnahmen. Er notiert und gibt
       DANACH an den urspruenglichen Handler weiter — die App darf trotzdem
       abstuerzen, sonst bleibt sie in einem kaputten Zustand stehen.
   NUR 12 EINTRAEGE, jeweils gekuerzt: Das Protokoll soll auf einem runden
   Display lesbar bleiben, und die letzten Minuten vor dem Absturz sind das,
   was zaehlt. */
/* ==========================================================================
   ABBRUCH IST KEIN FEHLER (2026-08-24 (10)) — DIE URSACHE AUS DEM PROTOKOLL
   --------------------------------------------------------------------------
   IM PROTOKOLL VOM 25.08. steht 27-mal dieselbe Zeile:
       „Sync-Schleife · [main] LeftCompositionCancellationException:
        The coroutine scope left the composition"
   und dasselbe fuer Uhr-Push, Dienst, Akku-Warnung, Karten-Raster — jedes Mal
   unmittelbar nach „Runde uebernommen".
   WAS DA PASSIERT: Wechselt der Bildschirm, verlaesst der alte
   `LaunchedEffect` die Komposition, und Compose BRICHT seine Coroutine AB.
   Der Abbruch kommt als `CancellationException` — und die ist in Kotlin eine
   ganz normale `Exception`. Jedes `catch (e: Exception)` faengt sie also mit.
   ZWEI FOLGEN, beide schlimm:
   1. DAS PROTOKOLL LAEUFT VOLL. 27 von 60 Zeilen sind dieser eine Vorgang.
      Genau die Zeilen, die man sucht, werden dadurch verdraengt — deshalb
      steht in dem Protokoll auch keine einzige meiner neuen Zeiger-Auskuenfte.
   2. STRUKTURIERTE NEBENLAEUFIGKEIT BRICHT. Wer einen Abbruch faengt und
      NICHT weiterwirft, sagt dem System „ich mache weiter" — die Schleife
      laeuft im Zweifel im Hintergrund weiter, waehrend Compose sie fuer
      beendet haelt. Zwei Schleifen, die dasselbe schreiben, sind genau der
      Zustand, in dem „einmal geht es, dann nicht mehr" entsteht.
   DIE REGEL: `CancellationException` wird NIE protokolliert und IMMER
   weitergeworfen. Sie ist kein Fehler, sondern die normale Art, wie eine
   Coroutine endet. */
fun Throwable.istAbbruch(): Boolean =
    this is kotlinx.coroutines.CancellationException

/* ==========================================================================
   DIAGNOSE (2026-08-25 (11))
   --------------------------------------------------------------------------
   WARUM: Die letzten fuenf Fehlersuchen liefen alle gleich ab — Symptom
   gemeldet, geraten, danebengelegen, naechste Fassung. Erst als das Protokoll
   endlich beim Handy ankam, war die Ursache in zwei Minuten klar
   (`CancellationException`). Die Lehre ist nicht „mehr protokollieren",
   sondern: EINE ZEILE, DIE DEN GANZEN ZUSTAND ZEIGT, ist mehr wert als
   fuenfzig Einzelmeldungen.
   Drei Bausteine:
   1. `abzug()` — wo steht alles gerade? Bildschirm, Loch, GPS-Guete, letzter
      Abgleich, Kennung, offene Messungen, Alter der Daten.
   2. `syncVerlauf` — die letzten Abgleiche mit Ergebnis. Ein Muster
      („409, 409, 409") sieht man nur in der Reihe, nie in Einzelzeilen.
   3. `zeitversatz` — die Uhr gegen die Uhrzeit des Servers. DAS IST DER
      WICHTIGSTE, und er hat mir bei den letzten Fehlern gefehlt: Der ganze
      Abgleich haengt an Vergleichen wie `at > ownHoleAt`. Geht die Uhr auch
      nur zwei Minuten vor, gewinnt sie JEDEN Vergleich — und das sieht exakt
      aus wie „das Handy wird ignoriert". Ein Versatz ist auf einer Uhr ohne
      Mobilfunk keine Seltenheit.
   ALLES LANDET IM PROTOKOLL und reist damit zum Handy. Eine Diagnose, die man
   nur auf dem runden Display lesen kann, wird nicht gelesen. */
object Diagnose {
    /* Die letzten Abgleiche: Zeit, Art, Ergebnis. Klein gehalten — es geht um
       das Muster, nicht um die Vollstaendigkeit. */
    private const val VERLAUF_MAX = 12
    var syncVerlauf: List<String> by mutableStateOf(emptyList())
        private set

    /* ==================================================================
       JEDE EINGABE BEKOMMT EINE NUMMER (2026-08-25 (23))
       --------------------------------------------------------------------
       GEMELDET: „Von Loch zu Loch gewechselt, jedes Mal Score 6 — weder
       Lochwechsel noch Scores kamen an."
       Bis hierher konnte ich nur ZUSTAENDE vergleichen: Die Uhr steht auf 9,
       das Handy auch — sagt aber nichts darueber, ob die sechs Schritte
       DAZWISCHEN angekommen sind. Ein Endstand kann auch zufaellig
       uebereinstimmen.
       DIE SPUR loest das: Jede Handlung des Benutzers bekommt eine laufende
       Nummer und eine Zeile. Sie reist mit dem Entwurf, und das Handy meldet
       zurueck, bis zu welcher Nummer es gekommen ist. Bleibt diese Nummer
       stehen, waehrend die Uhr weiterzaehlt, ist die Luecke auf den Schritt
       genau sichtbar — statt „irgendwas kommt nicht an".
       ZWANZIG EINTRAEGE: genug fuer ein paar Loecher, klein genug, dass der
       Entwurf schlank bleibt (rund 1 kB). */
    private const val AKTION_MAX = 20
    @Volatile var aktionNr: Int = 0
        private set
    var aktionen: List<String> by mutableStateOf(emptyList())
        private set
    /* Bis zu welcher Nummer ist das Handy gekommen? Kommt von drueben zurueck. */
    @Volatile var handySah: Int = -1

    fun aktion(text: String) {
        aktionNr++
        val z = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())
        aktionen = (aktionen + "#$aktionNr $z $text").takeLast(AKTION_MAX)
    }

    fun syncNotiz(art: String, ergebnis: String) {
        val z = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())
        syncVerlauf = (listOf("$z $art → $ergebnis") + syncVerlauf).take(VERLAUF_MAX)
        letzterPushZeit = z
        letzterPushErgebnis = ergebnis
        pulsAnzahl++
        if (!ergebnis.contains("HTTP 2")) pulsFehler++
        pulsSchreiben()
    }

    /* ==================================================================
       EINE ZEILE, DIE IMMER DA IST (2026-08-25 (15))
       --------------------------------------------------------------------
       Das Protokoll vom 25.08. endet um 08:40:48 — danach keine Zeile mehr,
       obwohl das Handy bis 08:44 lief. Daraus laesst sich NICHT ablesen, ob
       die Uhr nicht mehr sendete oder nur nichts mehr zu melden hatte. Genau
       diese Zweideutigkeit hat mich diese Woche mehrfach falsch abbiegen
       lassen.
       DER PULS behebt sie: EINE Zeile, die bei jedem Sendevorgang ERSETZT
       wird und festhaelt, wann zuletzt gesendet wurde, mit welchem Ergebnis,
       auf welchem Loch die Uhr steht und ob sie ein fremdes Loch gesehen hat.
       Sie waechst nicht, sie verdraengt nichts — und sie sagt beim naechsten
       Protokoll in einer Zeile, ob ueberhaupt gesendet wird.
       „Keine Meldung" heisst mit ihr endlich „alles gut" statt „vielleicht
       tot". */
    @Volatile var letzterPushZeit: String = ""
        private set
    @Volatile var letzterPushErgebnis: String = ""
        private set
    @Volatile var pulsLoch: Int? = null
    @Volatile var pulsFremd: String = "—"
    /* Ob der Live-Block ueberhaupt lief. „uebersprungen" heisst: kein Loch,
       also auch kein Zeiger fuer das Handy — der Push traegt dann nichts bei,
       obwohl er mit HTTP 200 antwortet (2026-08-25 (18)). */
    @Volatile var pulsZeiger: String = "übersprungen"
    /* Wie oft stand die Schleife laenger als erlaubt? Die Zahl im Puls
       unterscheidet „Netz war weg" von „Prozess war eingefroren". */
    @Volatile var taktStand: Int = 0
    /* ZAEHLER (2026-08-25 (16)). Der Puls sagte bisher nur, WANN zuletzt
       gesendet wurde — nicht, ob durchgehend gesendet wird. Zwischen zwei
       Pulszeilen lagen im Protokoll vom 25.08. zwei Minuten, und ob dazwischen
       zehn Vorgaenge liefen oder keiner, war nicht zu sehen.
       Zwei Zahlen beenden das: Vorgaenge insgesamt und davon misslungen. Eine
       Zeile, die „138 Vorgaenge, 0 misslungen" sagt, beweist Kontinuitaet —
       „3 Vorgaenge" nach einer Stunde beweist das Gegenteil. */
    @Volatile private var pulsAnzahl: Int = 0
    @Volatile private var pulsFehler: Int = 0

    private fun pulsSchreiben() {
        try {
            /* Bei jedem Puls einmal nachsehen (48): Der Speicher ist die
               Groesse, die am 28.08. ohne Vorwarnung zugeschlagen hat. */
            speicherPruefen()
            Fehler.entferneTags(listOf("⚠ Puls"))
            Fehler.warn("Puls",
                "$pulsAnzahl Vorgänge, $pulsFehler misslungen (${fehlerBilanz()}) · " +
                "${speicherText()} · " +
                "zuletzt $letzterPushZeit → $letzterPushErgebnis · " +
                "eigenes Loch ${pulsLoch ?: "?"} · Handy-Loch $pulsFremd · Zeiger $pulsZeiger · " +
                (if (taktStand > 0) "Schleife stand ${taktStand}× · " else "") +
                "Eingaben bis #$aktionNr, Handy sah #" +
                (if (handySah >= 0) "$handySah" + (if (handySah < aktionNr) " ⚠ ${aktionNr - handySah} offen" else " ✓")
                 else "—"))
        } catch (e: Exception) {
            if (e.istAbbruch()) throw e
        }
    }

    /* ZEITVERSATZ gegen den Server. Der `Date`-Kopf jeder HTTP-Antwort traegt
       die Serverzeit; die Differenz zur eigenen ist der Versatz.
       `null` = noch nicht gemessen. Positiv = die Uhr geht VOR. */
    @Volatile var versatzMs: Long? = null
        private set
    @Volatile var versatzGemessen: Long = 0L
        private set

    fun versatzAus(serverDatumKopf: String?) {
        if (serverDatumKopf.isNullOrBlank()) return
        try {
            val f = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            val server = f.parse(serverDatumKopf)?.time ?: return
            val v = System.currentTimeMillis() - server
            versatzMs = v
            versatzGemessen = System.currentTimeMillis()
            /* AB 30 SEKUNDEN wird es gefaehrlich: Der Abgleich vergleicht
               Zeitstempel auf die Sekunde. Einmalig melden, nicht bei jeder
               Messung — sonst ist der Puffer voll. */
            if (kotlin.math.abs(v) > 30_000) {
                Fehler.warnEinmal("zeitversatz", "Zeit",
                    "Uhr geht ${if (v > 0) "VOR" else "NACH"} um ${kotlin.math.abs(v) / 1000} s — "
                        + "Abgleich vergleicht Zeitstempel, das verfaelscht ihn")
            }
        } catch (e: Exception) {
            if (e.istAbbruch()) throw e
            /* Kopf unlesbar: kein Grund zur Meldung, nur keine Messung. */
        }
    }

    /* ==================================================================
       SPEICHER — DIE GROESSE, DIE AM 28.08. GEFEHLT HAT (2026-08-28 (48))
       --------------------------------------------------------------------
       An dem Tag hat eine Zuteilung von 14,8 MB die App beendet. Im Protokoll
       stand davor NICHTS ueber den Speicher — der Absturz kam ohne Vorwarnung,
       und ohne den Stapelabzug waere die Ursache nicht zu finden gewesen.
       EINE GROESSE, DIE EINEN UMBRINGEN KANN, GEHOERT INS PROTOKOLL, BEVOR SIE
       ES TUT. Drei Zahlen aus der Laufzeit: belegt, zugeteilt, Grenze.
       Gewarnt wird EINMAL, wenn weniger als 20 MB bis zur Grenze bleiben —
       frueh genug, um es einer Handlung zuzuordnen, und selten genug, um den
       Puffer nicht zu fluten. */
    fun speicherText(): String {
        val rt = Runtime.getRuntime()
        val belegt = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val grenze = rt.maxMemory() / (1024 * 1024)
        return "Heap ${belegt}/${grenze} MB"
    }
    fun speicherPruefen() {
        try {
            val rt = Runtime.getRuntime()
            val frei = (rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())) / (1024 * 1024)
            if (frei < 20) {
                Fehler.warnEinmal("heapKnapp", "Speicher",
                    "nur noch ${frei} MB bis zur Grenze (${speicherText()}) — " +
                    "grosse Abrufe werden jetzt gefaehrlich")
            }
        } catch (e: Exception) { if (e.istAbbruch()) throw e }
    }

    /* ==================================================================
       WORAN GENAU SCHEITERN DIE VORGAENGE? (2026-08-28 (48))
       --------------------------------------------------------------------
       Der Puls sagte „37 misslungen" — eine Zahl ohne Richtung. Zeitablauf,
       abgerissene Verbindung und Schreibkonflikt haben aber DREI VERSCHIEDENE
       Ursachen und drei verschiedene Antworten: schlechter Empfang, Gegenseite
       weg, zwei Geraete gleichzeitig. Das Protokoll vom 28.08. liess sich nur
       deshalb deuten, weil ich die Einzelzeilen von Hand ausgezaehlt habe.
       Jetzt zaehlt die Uhr selbst mit. */
    @Volatile private var fehlZeit: Int = 0      // Zeitablauf
    @Volatile private var fehlAbriss: Int = 0    // Verbindung weg
    @Volatile private var fehlKonflikt: Int = 0  // 409
    @Volatile private var fehlSonst: Int = 0

    fun fehlerArt(e: Throwable?, text: String) {
        val t = ((e?.javaClass?.simpleName ?: "") + " " + text).lowercase()
        when {
            t.contains("timeout") || t.contains("timed out") -> fehlZeit++
            t.contains("reset") || t.contains("broken pipe") ||
                t.contains("end of stream") || t.contains("unreachable") -> fehlAbriss++
            t.contains("409") || t.contains("konflikt") -> fehlKonflikt++
            else -> fehlSonst++
        }
    }
    fun fehlerBilanz(): String {
        if (fehlZeit + fehlAbriss + fehlKonflikt + fehlSonst == 0) return "keine Störungen"
        val t = mutableListOf<String>()
        if (fehlZeit > 0) t += "${fehlZeit}× Zeitablauf"
        if (fehlAbriss > 0) t += "${fehlAbriss}× Verbindung weg"
        if (fehlKonflikt > 0) t += "${fehlKonflikt}× Konflikt"
        if (fehlSonst > 0) t += "${fehlSonst}× sonstiges"
        return t.joinToString(" · ")
    }

    /* ==================================================================
       SCHLAGTRACKEN ALS EIGENE SPUR (2026-08-28 (48))
       --------------------------------------------------------------------
       Es ist der einzige Zweck dieser Uhr — und im Protokoll kam es
       ueberhaupt nicht vor. Als am 28.08. gemeldet wurde, der Start dauere
       lange und misslinge, gab es keine einzige Zeile darueber: kein Tipp,
       kein abgelehnter Fix, kein Startpunkt. Man konnte nur aus dem
       Ausbleiben schliessen.
       WAS NICHT PROTOKOLLIERT IST, LAESST SICH NICHT UNTERSUCHEN. Jeder
       Schritt bekommt jetzt eine Zeile mit DAUER — Tipp, Sammelfenster,
       Ergebnis. Erst daran sieht man, ob das Warten am GPS liegt (langes
       Sammelfenster) oder am Funk (langer Vorgang danach). */
    @Volatile var schlagBeginnMs: Long = 0L
    /* ==================================================================
       JEDE SCHLAG-ZEILE TRAEGT DEN GPS-ZUSTAND (2026-08-30 (53))
       --------------------------------------------------------------------
       GEMELDET am 30.08.: „Das Starten des GPS-Trackings von der Uhr
       funktioniert weiterhin nicht gut. Bitte detailliere das Eventlog der
       Uhr hierzu deutlich."
       BISHER stand in der Zeile nur, WAS passierte und WIE LANGE es dauerte.
       Die Frage, die man danach immer stellt — WARUM ging es nicht —, blieb
       offen, weil der Zustand fehlte, der die Entscheidung getroffen hat.
       Im Handy-Protokoll vom 30.08. steht zweimal „Uhr meldet seit ueber 90 s
       keine Position". Das ist der Verdacht; belegen konnte ihn niemand, weil
       die Schlag-Zeilen die Genauigkeit nicht mitfuehrten.
       JETZT STEHT BEI JEDEM SCHRITT: Genauigkeit des letzten Fixes, sein
       Alter, ob er nach `FixQuality.usable` brauchbar waere, und ob die
       Ortung ueberhaupt laeuft. Damit beantwortet EINE Zeile die Frage, fuer
       die es bisher drei Vermutungen brauchte.
       WARUM NICHT ALLES IMMER: Der Puls laeuft im Sekundentakt und wuerde das
       Protokoll fluten. Der GPS-Zustand haengt deshalb NUR an den
       Schlag-Zeilen — dort, wo er die Entscheidung erklaert. */
    fun gpsLage(): String {
        return try {
            val f = Live.fix
            if (f == null) "kein Fix" + (if (Live.running) "" else " · Ortung AUS")
            else {
                val alter = (System.currentTimeMillis() - f.ts) / 1000
                val brauchbar = FixQuality.usable(f)
                "±${f.acc.toInt()} m, ${alter}s alt, " +
                    (if (brauchbar) "brauchbar" else "ZU UNGENAU (Grenze ${FixQuality.MAX_ACC.toInt()} m)") +
                    (if (Live.running) "" else " · Ortung AUS")
            }
        } catch (e: Exception) { if (e.istAbbruch()) throw e; "Lage unbekannt" }
    }
    fun schlag(was: String, zusatz: String = "") {
        try {
            val dauer = if (schlagBeginnMs > 0)
                " nach ${(System.currentTimeMillis() - schlagBeginnMs) / 100 / 10.0} s" else ""
            aktion("Schlag: $was$dauer · GPS ${gpsLage()}" +
                (if (zusatz.isNotBlank()) " · $zusatz" else ""))
        } catch (e: Exception) { if (e.istAbbruch()) throw e }
    }

    /* Alles auf einen Blick. Bewusst EINE Zeichenkette und nicht zwanzig
       Felder: Sie geht so, wie sie ist, ins Protokoll und aufs Handy. */
    fun abzug(): String {
        val v = versatzMs
        val teile = mutableListOf<String>()
        teile += "Uhr $WATCH_APP"
        teile += "Android ${Build.VERSION.RELEASE}"
        teile += if (Fehler.kontext.isNotBlank()) Fehler.kontext else "kein Kontext"
        teile += "Versatz " + (v?.let { "${it / 1000} s" } ?: "ungemessen")
        teile += speicherText()
        teile += fehlerBilanz()
        teile += "Protokoll ${Fehler.liste.size}"
        return teile.joinToString(" · ")
    }

    /* ==================================================================
       SELBSTTEST (2026-08-25 (11))
       --------------------------------------------------------------------
       Beantwortet genau die Fragen, die diese Woche mehrfach offen waren und
       jedes Mal einen Umlauf gekostet haben: Welche Worker-Fassung laeuft?
       Schickt er die Kennung mit? Geht die Uhr richtig? Sind Schlaeger da?
       Jede Zeile nennt BEFUND UND FOLGE — „Worker v2.8" allein sagt niemandem
       etwas, „Worker v2.8 — kein watchlog.json, Protokoll bleibt hier" schon.
       Laeuft im Hintergrund (Netz), deshalb `suspend`. */
    suspend fun selbsttest(): List<String> {
        val z = mutableListOf<String>()
        z += "Uhr $WATCH_APP · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"

        /* 1. Worker: erreichbar, welche Fassung, schickt er die Kennung? */
        try {
            val c = URL(WORKER_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            val code = c.responseCode
            versatzAus(c.getHeaderField("Date"))
            val txt = if (code in 200..299)
                c.inputStream.bufferedReader().use { it.readText() }.take(200) else ""
            c.disconnect()
            val fassung = Regex("golftraining-sync v([\\d.]+)").find(txt)?.groupValues?.get(1)
            z += when {
                code !in 200..299 -> "✗ Worker antwortet mit $code — kein Abgleich moeglich"
                fassung == null   -> "⚠ Worker erreichbar, Fassung unbekannt"
                fassung < "2.11"  -> "⚠ Worker v$fassung — ab v2.11 reist das Protokoll auch ohne Runde"
                else              -> "✓ Worker v$fassung"
            }
        } catch (e: Exception) {
            if (e.istAbbruch()) throw e
            z += "✗ Worker nicht erreichbar: ${e.javaClass.simpleName} — kein Netz oder falsche Adresse"
        }

        /* 2. Zeitversatz — der stille Verfaelscher. */
        val v = versatzMs
        z += when {
            v == null -> "⚠ Zeitversatz ungemessen"
            kotlin.math.abs(v) > 30_000 ->
                "✗ Uhr geht ${if (v > 0) "vor" else "nach"} um ${kotlin.math.abs(v) / 1000} s — " +
                "der Abgleich vergleicht Zeitstempel und wird dadurch falsch"
            else -> "✓ Zeit stimmt (${v / 1000} s)"
        }

        /* 3. Schreibschluessel — ohne ihn liest die Uhr nur. */
        z += if (WRITE_KEY.isBlank()) "✗ Kein Schreibschluessel — die Uhr kann nichts senden"
             else "✓ Schreibschluessel gesetzt"

        /* 4. Der Ringpuffer selbst: Ist er voll, sind die aeltesten Zeilen weg
              — und die erklaeren oft, WIE es dazu kam. */
        z += if (Fehler.liste.size >= 55)
            "⚠ Protokoll fast voll (${Fehler.liste.size}) — aeltere Meldungen fallen heraus"
        else "✓ Protokoll ${Fehler.liste.size} Zeilen"

        /* 5. Der Verlauf: haeufen sich Konflikte? */
        val konflikte = syncVerlauf.count { it.contains("409") }
        z += if (konflikte >= 3)
            "✗ $konflikte Konflikte in den letzten ${syncVerlauf.size} Abgleichen — " +
            "sendet der Worker die Kennung (X-Repo-Sha) zurueck?"
        else "✓ Abgleiche ohne Haeufung ($konflikte Konflikte)"

        return z
    }

    /* ==================================================================
       DIE DIAGNOSE ERSETZT SICH SELBST (2026-08-25 (12))
       --------------------------------------------------------------------
       GEMESSEN am echten Protokoll: Fuenfmal auf den Knopf gedrueckt hat 35
       von 60 Zeilen belegt — und prompt meldete der Selbsttest „Protokoll
       fast voll". Die Diagnose verdraengte also genau das, wozu sie da ist.
       DESHALB: Vor jedem neuen Durchlauf die vorigen Diagnose- und
       Selbsttest-Zeilen entfernen. Es gibt immer GENAU EINEN Stand — einen
       aelteren braucht niemand, er beschreibt eine Lage, die vorbei ist.
       Echte Fehlermeldungen bleiben unangetastet. */
    /* ==================================================================
       DER BERICHT STEHT NEBEN DEM PROTOKOLL, NICHT DARIN (2026-08-25 (13))
       --------------------------------------------------------------------
       GEMELDET: Nach dem Knopfdruck kam beim Handy nur „Runde uebernommen" an,
       keine Diagnose. URSACHE ist der Aufbau, nicht ein einzelner Fehler:
       Die Diagnose schrieb IN den Ringpuffer — und raeumte sich darin selbst
       auf (2026-08-25 (12)), damit sie ihn nicht verstopft. Damit haengt ihre
       Zustellung an einem Puffer, der gleichzeitig von Fehlern, vom
       Rundenentwurf und vom Aufraeumen bewegt wird. Wer beides mischt, hat
       drei Stellen, an denen ein Bericht verschwinden kann.
       JETZT LIEGT ER SEPARAT: `letzterBericht` ist eine eigene Groesse, die
       nur der Knopf setzt und die nichts anderes anfasst. Sie reist als
       eigenes Feld `bericht` und ist damit unabhaengig davon, was im Puffer
       gerade passiert.
       Der Puffer bleibt, was er war: die Fehlerhistorie. Zwei Dinge, zwei
       Wege — das ist der ganze Unterschied. */
    @Volatile var letzterBericht: String = ""
        private set
    @Volatile var berichtAt: String = ""
        private set

    fun berichtSetzen(zeilen: List<String>) {
        val kopf = mutableListOf(abzug())
        if (syncVerlauf.isNotEmpty()) kopf += "Abgleiche: " + syncVerlauf.joinToString(" | ")
        letzterBericht = (kopf + zeilen).joinToString("\n")
        berichtAt = isoNow()
    }

    fun inProtokoll() {
        /* Nur noch EINE Zeile im Puffer — als Spur, dass geprueft wurde. Der
           Inhalt steht im Bericht. */
        Fehler.entferneTags(listOf("⚠ Diagnose"))
        Fehler.warn("Diagnose", "Selbsttest ausgeführt · " + abzug())
    }
}

object Fehler {
    private const val KEY = "fehlerlog"
    /* MEHR EINTRAEGE (2026-08-16 (13)): Zwoelf reichten nicht — bei einem
       Absturz stehen die entscheidenden Meldungen oft DAVOR, und zwei
       Sync-Fehlschlaege verdraengten sie. 30 Eintraege sind auf einem runden
       Display noch scrollbar und decken eine ganze Bahn ab. */
    /* 30 -> 60 (2026-08-24 (8)): Bei einer Stoerung fuellt EIN Vorgang den
       Puffer — die 409-Schleife vom 24.08. schrieb vier Zeilen je Versuch, und
       alles davor war weg. Genau die Zeilen davor erklaeren aber, WIE es dazu
       kam. 60 Eintraege sind rund 6 kB; im Rundenentwurf faellt das neben den
       Loechern nicht ins Gewicht. */
    private const val MAX = 60
    var liste: List<String> by mutableStateOf(emptyList())
        private set
    private var ctxRef: Context? = null

    /* WO WAR MAN GERADE? (2026-08-16 (13))
       Eine Meldung wie „NullPointerException" ohne Ort ist fast wertlos. Die
       App schreibt hier laufend hinein, wo sie steht — Bildschirm, Platz, Loch.
       Kostet nichts (eine Zuweisung) und macht aus „irgendwann abgestuerzt"
       ein „auf Loch 7 beim Zeichnen abgestuerzt". */
    var kontext: String = ""

    /* Einmal beim Start: Geraet und Fassung. Ohne das raet man bei jeder
       Meldung mit, ob ueberhaupt die neue Uhr-Datei laeuft. */
    fun start() {
        add("Start", "Uhr $WATCH_APP · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
    }

    fun init(ctx: Context) {
        ctxRef = ctx.applicationContext
        liste = try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    /* Abbrueche gar nicht erst aufnehmen — siehe `istAbbruch` oben. Der Riegel
       sitzt HIER, damit keine der 33 Fangstellen ihn einzeln kennen muss. */
    fun add(tag: String, e: Throwable?) {
        if (e != null && e.istAbbruch()) return
        addRoh(tag, e)
    }
    private fun addRoh(tag: String, e: Throwable?) = add(tag, e?.let {
        (it.javaClass.simpleName + ": " + (it.message ?: "")).take(120)
    } ?: "?")

    /* `sofort` = synchron schreiben (2026-08-16 (10)) — DAS WAR DER GRUND, WARUM
       DAS PROTOKOLL NACH EINEM ABSTURZ LEER BLIEB.
       `apply()` schreibt im Hintergrund. Bei einer gefangenen Stoerung ist das
       richtig (kein Ruckeln auf dem UI-Faden). Beim ABSTURZ stirbt der Prozess
       aber, bevor der Hintergrundschreiber fertig ist — die Meldung war also
       genau in dem Fall weg, fuer den das Protokoll gebaut wurde.
       `commit()` blockiert, und das ist hier ausdruecklich erwuenscht: Der
       Prozess geht ohnehin unter, ein paar Millisekunden aendern daran nichts. */
    fun add(tag: String, text: String, sofort: Boolean = false) {
        try {
            val z = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY).format(Date())
            /* Faden mitschreiben: „main" oder ein Sensor-/Netzfaden zu
               unterscheiden hat schon zwei Fehler erklaert, die sonst gleich
               ausgesehen haetten (2026-08-16 (13)). */
            val fd = Thread.currentThread().name.take(14)
            val ort = if (kontext.isNotBlank()) " · $kontext" else ""
            /* ==================================================================
               WIEDERHOLUNGEN ZAEHLEN STATT SAMMELN (2026-08-24 (8))
               --------------------------------------------------------------------
               Die App fasst „4× derselbe Fehler" seit je zusammen, die Uhr
               schrieb vier Zeilen. Bei 60 Plaetzen ist das teuer: Eine
               Schleife, die zwanzigmal dasselbe meldet, loescht die Vorgeschichte
               — und die Vorgeschichte ist das, was man sucht.
               Verglichen wird OHNE Zeitstempel, also nur Ort und Text: Zwei
               gleiche Meldungen in derselben Sekunde sind dieselbe Sache.
               Der Zaehler steht am ENDE, damit die Zeile vorne unveraendert
               lesbar bleibt. */
            val kern = "$tag$ort · [$fd] ${text.take(220)}"
            val ersteRoh = liste.firstOrNull()?.substringAfter(" · ", "")?.substringBefore(" (×")
            var neu: List<String>
            if (ersteRoh != null && ersteRoh == kern) {
                val alt = liste.first()
                val n = Regex(" \\(×(\\d+)\\)$").find(alt)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                neu = listOf("$z · $kern (×${n + 1})") + liste.drop(1)
            } else {
                neu = (listOf("$z · $kern") + liste).take(MAX)
            }
            liste = neu
            val ed = ctxRef?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putString(KEY, neu.joinToString("\n"))
            if (sofort) ed?.commit() else ed?.apply()
        } catch (ignore: Exception) {
            /* STILL, UND ZWAR MIT ABSICHT: Das hier IST das Fehlerprotokoll. Wer den Fehlschlag des
               Protokollierens protokolliert, dreht sich im Kreis. */
        }
    }

    /* ==========================================================================
       WARNUNGEN (2026-08-16 (15))
       --------------------------------------------------------------------------
       Ein Fehler sagt „etwas ist schiefgegangen". Eine WARNUNG sagt „es lief,
       aber das Ergebnis kann falsch sein" — und das ist auf der Bahn die
       wichtigere Auskunft. Eine Distanz, die mit 40 m GPS-Genauigkeit
       gerechnet wurde, sieht genauso aus wie eine gute.
       Gleicher Speicher, eigene Stufe: Man liest EINE Liste und sieht die
       zeitliche Abfolge — ein Absturz nach drei Warnungen erklaert sich oft
       von selbst.
       `warnEinmal` fuer Erkenntnisse, die sich nicht aendern (kein Beutel,
       keine Karte): Im Takt gemeldet wuerden sie das Protokoll fuellen und die
       eine wichtige Zeile verdraengen. */
    private val warnGesehen = HashSet<String>()

    fun warn(tag: String, text: String) = add("⚠ $tag", text)

    /* Zeilen mit bestimmten Kennungen entfernen (2026-08-25 (12)).
       Gebraucht fuer die Diagnose, die sich selbst ersetzen soll statt den
       Puffer zu fuellen. Bewusst KEINE allgemeine Loeschfunktion: Sie nimmt
       eine Liste von Kennungen und ruehrt nichts anderes an. */
    fun entferneTags(tags: List<String>) {
        try {
            val neu = liste.filterNot { z -> tags.any { z.contains(" · $it · ") || z.contains(" · $it ") } }
            if (neu.size == liste.size) return
            liste = neu
            ctxRef?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putString(KEY, neu.joinToString("\n"))?.apply()
        } catch (e: Exception) {
            if (e.istAbbruch()) throw e
            /* Aufraeumen misslungen: Der Puffer bleibt, wie er war — harmlos. */
        }
    }

    fun warnEinmal(schluessel: String, tag: String, text: String) {
        if (!warnGesehen.add(schluessel)) return
        warn(tag, text)
    }

    fun clear() {
        warnGesehen.clear()
        liste = emptyList()
        try {
            ctxRef?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.remove(KEY)?.apply()
        } catch (ignore: Exception) {
            /* STILL, UND ZWAR MIT ABSICHT: Siehe oben: Leeren des Protokolls, nicht protokollierbar. */
        }
    }

    /* Muss FRUEH aufgerufen werden — was vorher abstuerzt, faengt niemand. */
    fun fangeAlles(ctx: Context) {
        init(ctx)
        val vorher = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                /* Bis zu DREI eigene Stellen aus dem Aufrufstapel: Eine
                   allein sagt oft nur „Compose", die dritte nennt die
                   Funktion, in der es wirklich passiert ist. */
                /* AUSFUEHRLICH (2026-08-16 (13)): fuenf eigene Stellen statt
                   drei, die erste FREMDE Stelle dazu (oft die eigentliche
                   Bibliothek), und die URSACHENKETTE — bei Compose steckt der
                   wahre Fehler regelmaessig zwei `cause` tiefer. */
                val eigen = e.stackTrace.filter { it.className.contains("golfwatch") }
                    .take(5).joinToString(" < ") {
                        "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                    }
                val fremd = e.stackTrace.firstOrNull { !it.className.contains("golfwatch") }
                    ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" } ?: ""
                var ursache = e.cause; var kette = ""; var tiefe = 0
                while (ursache != null && tiefe < 3) {
                    kette += " ← ${ursache.javaClass.simpleName}: ${ursache.message ?: ""}"
                    ursache = ursache.cause; tiefe++
                }
                add("ABSTURZ", e.javaClass.simpleName + ": " + (e.message ?: "") + kette +
                        (if (eigen.isNotBlank()) " @$eigen" else "") +
                        (if (fremd.isNotBlank()) " (via $fremd)" else ""), true)
            } catch (ignore: Exception) {
                /* STILL, UND ZWAR MIT ABSICHT: Anzeige einer Meldung; scheitert sie, ist die Meldung selbst
               schon im Protokoll. */
            }
            /* WEITERREICHEN: Die App soll trotzdem abstuerzen. Ein abgefangener
               Absturz laesst sie in einem Zustand weiterlaufen, dem man nicht
               mehr trauen kann — und der naechste Fehler waere dann noch
               schwerer zuzuordnen. */
            vorher?.uncaughtException(t, e)
        }
    }
}

object Live {

    /* ==========================================================================
       ZWEI POSITIONEN: EINE ZUM RECHNEN, EINE ZUM ZEICHNEN (2026-08-14 (3))
       --------------------------------------------------------------------------
       DAS RUCKELN kommt nicht von zu viel Rechnung, sondern von zu vielen
       NEUZEICHNUNGEN. `fix` ist Compose-State und wird im GPS-Takt (bis 1/s)
       neu gesetzt — jede Composable, die ihn liest, wird dabei ungueltig. Auf
       der Loch-Seite haengt fast alles daran, also wurde sekuendlich der halbe
       Bildschirm neu zusammengesetzt, inklusive `liveOf()` mit der
       Ring-Geometrie fuer Front/Mitte/Back.
       Dabei aendert sich in dieser Sekunde meist NICHTS, was man sehen kann:
       Die Position wandert im Stand um ein bis zwei Meter (GPS-Rauschen), die
       angezeigten Meter bleiben gleich.
       DESHALB ZWEI ZUSTAENDE:
         · `fix`   — roh, jeder Tick. Fuer Messung, Caddy und alles, was rechnet.
                     Wird NICHT mehr von der Oberflaeche gelesen.
         · `fixUi` — nur wenn sich etwas SEHENSWERTES geaendert hat: Position um
                     mehr als 1,5 m gewandert oder Genauigkeit um mehr als 3 m
                     gesprungen. Daran haengt die Anzeige.
       1,5 m ist bewusst knapp unter der Anzeigeschwelle: Distanzen stehen in
       ganzen Metern, und darunter kann sich die Zahl gar nicht aendern. */
    var fix: Fix? by mutableStateOf(null)
    var fixUi: Fix? by mutableStateOf(null)

    /* Setzt beide Zustaende — die Anzeige aber nur bei sichtbarer Aenderung.
       EINE Stelle, damit die Regel nicht an drei Orten auseinanderlaeuft.
       NAME: NICHT `setFix` — `var fix` erzeugt auf der JVM bereits einen
       Setter dieses Namens mit derselben Signatur, und der Bau bricht mit
       „Platform declaration clash" ab. */
    /* VERLAUF DER LETZTEN FIXES (2026-08-15 (12)).
       Fuer die AUTOMATISCHE Schlagerfassung ist die Position VOR dem Treffer
       die richtige: Da stand man am Ball. Danach geht man los — und genau
       daran ist die Automatik gescheitert (siehe recBeginAuto). */
    val verlauf = ArrayList<Fix>()

    fun neuerFix(f: Fix?) {
        fix = f
        if (f != null) {
            verlauf.add(f)
            while (verlauf.size > 30) verlauf.removeAt(0)
        }
        val u = fixUi
        if (f == null || u == null) { fixUi = f; return }
        val gewandert = Geo.dist(u.ll(), f.ll()) >= 1.5
        val genauer = kotlin.math.abs(u.acc - f.acc) >= 3f
        if (gewandert || genauer) fixUi = f
    }
    var running: Boolean by mutableStateOf(false)
    /* Wann kam der letzte Fix? Fuer die Luecken-Meldung (53) — bewusst ein
       schlichtes Feld und kein Zustand: Es zeichnet nichts neu. */
    @Volatile var letzterFixMs: Long = 0L
    var err: String? by mutableStateOf(null)
    var src: String by mutableStateOf("")   // aktive GPS-Quelle: "⌚ Uhr" / "📱 Handy"

    // Text für die Dauer-Notification (Loch/Stand)
    var note: String by mutableStateOf("Runde läuft")

    fun reset() {
        fix = null
        fixUi = null
        verlauf.clear()
        err = null
    }
}

// ===========================================================================
//  FOREGROUND-SERVICE — hält die Runde am Leben (Doku 2b)
//  · Dauer-Notification (Pflicht) + WakeLock -> Prozess wird nicht gekillt
//  · GPS-Updates laufen weiter, auch wenn der Bildschirm aus ist
// ===========================================================================

class RoundService : Service() {

    companion object {
        const val ACTION_START = "de.lars.golfwatch.START"
        const val ACTION_STOP = "de.lars.golfwatch.STOP"
        const val ACTION_NOTE = "de.lars.golfwatch.NOTE"
        const val ACTION_GPS = "de.lars.golfwatch.GPS"     // GPS-Quelle wurde umgeschaltet
        const val EXTRA_NOTE = "note"

        private const val CHANNEL = "golfround"
        private const val NOTIF_ID = 4711
    }

    private var lm: LocationManager? = null
    private var wake: PowerManager.WakeLock? = null
    private var started = false

    // Fused Location (Play Services): nutzt auf Wear OS automatisch das GPS des
    // GEKOPPELTEN HANDYS, solange die Bluetooth-Verbindung steht (spart massiv
    // Uhr-Akku) — ohne Handy fällt es selbstständig auf die Uhr-Sensoren zurück.
    private var fusedCb: LocationCallback? = null

    /* Zeitpunkt des letzten echten GPS-Fixes. Ohne diesen Merker kippte ein
       NETWORK-Fix (WLAN/Mobilfunk, oft 40–800 m) alle 10 s einen sauberen
       GPS-Fix mit 4 m weg — beide Provider melden an denselben Listener, und
       der hat den Absender nie geprüft. Auf dem Platz mit Clubhaus-WLAN in
       Reichweite war das der größte einzelne Genauigkeitsverlust. */
    private var lastGpsMs = 0L

    private val listener = object : LocationListener {

        override fun onLocationChanged(loc: Location) {

            val fromGps = loc.provider == LocationManager.GPS_PROVIDER
            val now = System.currentTimeMillis()

            /* ==============================================================
               EINE GPS-LUECKE MELDET SICH, WENN SIE ENDET (2026-08-30 (53))
               --------------------------------------------------------------
               GEMELDET am 30.08.: „Das Starten des GPS-Trackings von der Uhr
               funktioniert weiterhin nicht gut."
               IM HANDY-PROTOKOLL derselben Runde steht zweimal „Uhr meldet
               seit ueber 90 s keine Position" — das HANDY bemerkt die Luecke
               also, die UHR selbst schwieg dazu. Und die Uhr ist die einzige,
               die weiss, WORAN es lag: kein Satellitenfix, nur Netzwerk-Fixe,
               oder Ortung gar nicht gestartet.
               EINE LUECKE KANN SICH NICHT MELDEN, WAEHREND SIE LAEUFT — es
               kommt ja nichts. Sie meldet sich, wenn sie ENDET: Der erste Fix
               danach traegt, wie lange nichts kam und wie gut er ist. Genau
               diese Zeile fehlte, um „laesst sich nicht starten" von „GPS war
               weg" zu unterscheiden.
               AB 20 SEKUNDEN, nicht ab jeder Sekunde: Das Sammelfenster einer
               Messung dauert wenige Sekunden; darunter ist eine Pause normal
               und im Protokoll nur Rauschen. */
            try {
                val letzte = Live.letzterFixMs
                if (letzte > 0L && now - letzte >= 20_000L) {
                    Diagnose.aktion("GPS-Lücke: ${(now - letzte) / 1000} s ohne Position — " +
                        "wieder da mit ±${(if (loc.hasAccuracy()) loc.accuracy else 99f).toInt()} m" +
                        (if (fromGps) " (Satellit)" else " (nur Netzwerk)"))
                }
                Live.letzterFixMs = now
            } catch (e: Exception) { if (e.istAbbruch()) throw e }

            if (fromGps) {
                lastGpsMs = now
            } else if (now - lastGpsMs < 10_000L) {
                // GPS liefert gerade — Netzwerk-Fix ist hier nur Rauschen.
                // Er bleibt als Sofort-Anzeige beim Rundenstart nützlich,
                // solange noch kein Satellitenfix da ist.
                return
            }

            Live.err = null
            Live.neuerFix(
                Fix(
                loc.latitude,
                loc.longitude,
                if (loc.hasAccuracy()) loc.accuracy else 99f,
                /* loc.time statt currentTimeMillis: der Zeitstempel muss sagen,
                   wann die Position GEMESSEN wurde, nicht wann sie bei uns
                   ankam. Sonst sieht ein verspäteter Fix taufrisch aus und die
                   Stale-Prüfung beim Schlagtracking greift nie. */
                if (loc.time > 0L) loc.time else now
            )
            )
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                Live.err = "GPS ist ausgeschaltet"
            }
        }

        override fun onProviderEnabled(provider: String) {
            Live.err = null
        }

        @Deprecated("nur für ältere Systeme nötig")
        override fun onStatusChanged(
            provider: String?,
            status: Int,
            extras: Bundle?
        ) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                stopTracking()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_GPS -> {
                if (started) {
                    stopGpsOnly()
                    startTracking()
                }
                return START_STICKY
            }

            ACTION_NOTE -> {
                intent.getStringExtra(EXTRA_NOTE)?.let {
                    Live.note = it
                }
                if (started) {
                    notifManager().notify(NOTIF_ID, buildNotification())
                }
                return START_STICKY
            }

            else -> {
                intent?.getStringExtra(EXTRA_NOTE)?.let { Live.note = it }
                startAsForeground()
                startTracking()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    // ---- Notification ----

    private fun notifManager() =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL,
                "Laufende Runde",
                NotificationManager.IMPORTANCE_LOW
            )
            ch.setShowBadge(false)
            ch.enableVibration(false)
            notifManager().createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /* NotificationCompat statt android.app.Notification.Builder:
           OngoingActivity.Builder akzeptiert AUSSCHLIESSLICH einen
           NotificationCompat.Builder. Der SDK-Zweig für < O entfällt damit,
           NotificationCompat behandelt den Channel selbst. */
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("⛳ Golf-Runde")
            .setContentText(Live.note)
            .setSmallIcon(R.drawable.ic_stat_golf)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)

        /* ONGOING ACTIVITY: meldet die Runde als laufende Aktivität an. Wear OS
           legt daraufhin einen Chip aufs Zifferblatt, über den man in einem Tipp
           zurück in die Runde kommt — statt die App in der Liste zu suchen.
           `Live.note` trägt bereits "Loch 7 · +3 (6)", deshalb reicht ein
           Template mit einem Textteil. WICHTIG: apply() muss VOR build()
           laufen, sonst landen die Ongoing-Extras nicht in der Notification. */
        val status = Status.Builder()
            .addTemplate("#note#")
            .addPart("note", Status.TextPart(Live.note))
            .build()

        OngoingActivity.Builder(applicationContext, NOTIF_ID, b)
            .setStaticIcon(R.drawable.ic_stat_golf)
            .setTouchIntent(open)
            .setStatus(status)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
            .apply(applicationContext)

        return b.build()
    }

    private fun startAsForeground() {

        if (started) {
            notifManager().notify(NOTIF_ID, buildNotification())
            return
        }

        ensureChannel()

        val n = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIF_ID, n)
        }

        if (wake == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "golfwatch:round"
            )
            wake?.setReferenceCounted(false)
            try {
                // 8 h Deckel: selbst wenn der Stopp mal ausbleibt, endet der Lock
                wake?.acquire(8 * 60 * 60 * 1000L)
            } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("Dienst starten", e)
        }
        }

        started = true
        Live.running = true
    }

    // ---- GPS ----

    private fun startTracking() {

        if (!hasLocPerm(this)) {
            Live.err = "Standort-Freigabe fehlt"
            return
        }

        // GPS-Quelle laut Einstellung: "watch" (Uhr-GPS) oder "phone"
        // (Fused Location -> Handy-GPS über Bluetooth, Fallback Uhr).
        if (prefGet(applicationContext, "gpsSource", "watch") == "phone") {
            if (startPhoneTracking()) return
            // Play Services nicht verfügbar o. Ä. -> transparent auf Uhr wechseln
            Live.err = "Handy-GPS nicht verfügbar — nutze Uhr-GPS"
        }

        Live.src = "⌚ Uhr"

        val manager =
            getSystemService(LOCATION_SERVICE) as LocationManager

        lm = manager

        try {

            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            } else {
                Live.err = "GPS ist ausgeschaltet"
            }

            // Fallback, damit sofort etwas da ist (WLAN/Mobilfunk)
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10000L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }

            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                if (Live.fix == null) {
                    Live.neuerFix(
                Fix(
                        it.latitude,
                        it.longitude,
                        if (it.hasAccuracy()) it.accuracy else 99f,
                        // Ehrlicher Zeitstempel: ein "last known" kann Stunden
                        // alt sein. Als Sofortanzeige ok, fuers Schlagtracking
                        // faellt er ueber die Stale-Pruefung von selbst raus.
                        if (it.time > 0L) it.time else System.currentTimeMillis()
                    )
            )
                }
            }

        } catch (e: SecurityException) {
            Live.err = "Standort-Freigabe fehlt"
        } catch (e: Exception) { if (e.istAbbruch()) throw e; Fehler.add("GPS starten", e); Live.err = "GPS-Fehler" }
    }

    // Fused-Location-Pfad („Handy"). true = erfolgreich gestartet.
    private fun startPhoneTracking(): Boolean =
        try {
            val fused =
                LocationServices.getFusedLocationProviderClient(this)

            val req =
                LocationRequest
                    .Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                    .setMinUpdateDistanceMeters(0f)
                    .build()

            val cb = object : LocationCallback() {
                override fun onLocationResult(r: LocationResult) {
                    val l = r.lastLocation ?: return
                    /* Handy-GPS kommt ueber Bluetooth mit spuerbarer Latenz an.
                       Mit currentTimeMillis sah ein 6 s alter Fix taufrisch aus
                       — wer im Gehen den Endpunkt setzt, mass dadurch
                       systematisch zu kurz. */
                    Live.neuerFix(
                Fix(
                        l.latitude,
                        l.longitude,
                        if (l.hasAccuracy()) l.accuracy else 99f,
                        if (l.time > 0L) l.time else System.currentTimeMillis()
                    )
            )
                }
            }

            fused.requestLocationUpdates(
                req,
                cb,
                Looper.getMainLooper()
            )

            fusedCb = cb
            Live.src = "📱 Handy"

            fused.lastLocation.addOnSuccessListener { l ->
                if (l != null && Live.fix == null) {
                    Live.neuerFix(
                Fix(
                        l.latitude,
                        l.longitude,
                        if (l.hasAccuracy()) l.accuracy else 99f,
                        if (l.time > 0L) l.time else System.currentTimeMillis()
                    )
            )
                }
            }

            true
        } catch (e: SecurityException) {
            Live.err = "Standort-Freigabe fehlt"
            false
        } catch (e: Exception) { if (e.istAbbruch()) throw e; Fehler.add("Handy-GPS", e); false }

    // Nur die GPS-Quellen lösen (für Quellen-Wechsel) — Foreground-Status,
    // Notification und WakeLock bleiben unangetastet.
    private fun stopGpsOnly() {
        try {
            lm?.removeUpdates(listener)
        } catch (e: Exception) {
            /* STILL, UND ZWAR MIT ABSICHT: Abmelden misslingt nur, wenn nie
               angemeldet war — dann ist das Ziel bereits erreicht. */
        }
        lm = null
        try {
            fusedCb?.let {
                LocationServices
                    .getFusedLocationProviderClient(this)
                    .removeLocationUpdates(it)
            }
        } catch (e: Exception) {
            /* STILL, UND ZWAR MIT ABSICHT: wie oben — abmelden von etwas, das
               nicht laeuft, ist harmlos. */
        }
        fusedCb = null
    }

    private fun stopTracking() {
        stopGpsOnly()
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("GPS stoppen", e)
        }
        wake = null
        started = false
        Live.running = false
        Live.src = ""
    }
}

// ---- Service-Steuerung von der UI aus ----

fun hasLocPerm(ctx: Context): Boolean =
    ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

fun svcStart(ctx: Context, note: String) {

    val i = Intent(ctx, RoundService::class.java)
        .setAction(RoundService.ACTION_START)
        .putExtra(RoundService.EXTRA_NOTE, note)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ctx.startForegroundService(i)
    } else {
        ctx.startService(i)
    }
}

fun svcNote(ctx: Context, note: String) {

    if (!Live.running) return

    val i = Intent(ctx, RoundService::class.java)
        .setAction(RoundService.ACTION_NOTE)
        .putExtra(RoundService.EXTRA_NOTE, note)

    try {
        ctx.startService(i)
    } catch (e: Exception) {
        /* STILL, UND ZWAR MIT ABSICHT: Der Dienst nimmt nur eine Notiz
           entgegen. Scheitert der Start, laeuft die Runde ohne sie weiter —
           kein Datenverlust, keine Meldung wert. Der Grund gehoert trotzdem
           hierhin: „still" darf nicht wie „vergessen" aussehen. */
    }
}

// GPS-Quelle wurde umgeschaltet: laufendes Tracking auf die neue Quelle drehen
fun svcGpsRestart(ctx: Context) {
    try {
        ctx.startService(
            Intent(ctx, RoundService::class.java)
                .setAction(RoundService.ACTION_GPS)
        )
    } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("GPS neu starten", e)
        }
}

fun svcStop(ctx: Context) {

    val i = Intent(ctx, RoundService::class.java)
        .setAction(RoundService.ACTION_STOP)

    try {
        ctx.startService(i)
    } catch (e: Exception) {
        /* STILL, UND ZWAR MIT ABSICHT: Beenden eines Dienstes, den es
           womoeglich gar nicht mehr gibt — das Ziel ist dann erreicht. */
    }

    Live.running = false
    Live.reset()
}

// ================= Activity =================

class MainActivity : ComponentActivity() {

    // Ambient-/Always-on-Unterstützung (androidx.wear:wear): Damit bleibt die
    // App beim Senken des Handgelenks im GEDIMMTEN Zustand SICHTBAR, statt dass
    // Wear OS sie schließt und zum Zifferblatt zurückfällt. Das ist der
    // eigentliche Fix für "App schließt sich immer".
    private val ambientCallback =
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {

            override fun onEnterAmbient(
                ambientDetails: AmbientLifecycleObserver.AmbientDetails
            ) {
                AmbientState.burnIn =
                    ambientDetails.burnInProtectionRequired
                AmbientState.lowBit =
                    ambientDetails.deviceHasLowBitAmbient
                AmbientState.isAmbient = true
            }

            override fun onExitAmbient() {
                AmbientState.isAmbient = false
            }

            // ~1x pro Minute — löst die Neuzeichnung im Ambient aus
            override fun onUpdateAmbient() {
                AmbientState.tick++
            }
        }

    private val ambientObserver by lazy {
        AmbientLifecycleObserver(this, ambientCallback)
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        /* SO FRUEH WIE MOEGLICH (2026-08-16 (8)): Was vor dieser Zeile
           abstuerzt, faengt niemand — und genau der Start ist die Phase, in
           der es zuletzt passiert ist. */
        Fehler.fangeAlles(this)
        /* Eine Startzeile ins Protokoll: Ohne sie raet man bei jeder Meldung
           mit, ob ueberhaupt die neue Uhr-Datei laeuft — und man sieht nicht,
           wo eine Sitzung anfaengt (2026-08-16 (13)). */
        Fehler.start()

        lifecycle.addObserver(ambientObserver)

        // Zurück wird NICHT mehr hier abgefangen. Die Ebenen-Logik
        // (Seite -> Loch-Screen -> Übersicht -> App zu) sitzt als BackHandler
        // in GolfWatchApp, weil nur dort der aktuelle Zustand bekannt ist.

        /* KEIN FLAG_KEEP_SCREEN_ON mehr auf dem Fenster (2026-08-12).
           Das Fenster-Flag hat Vorrang vor View.keepScreenOn und hätte den
           Always-On-Modus komplett verhindert: solange es gesetzt ist, geht
           die Uhr nie in den Ambient, der neue Callback feuert nie. Den
           Komfort-Wunsch "Bildschirm an" erledigt jetzt ausschließlich
           GolfWatchApp über LocalView.keepScreenOn — und zwar bewusst NUR
           außerhalb einer laufenden Runde. Während der Runde übernimmt der
           gedimmte Ambient-Screen. */

        setContent {
            MaterialTheme(
                colors = GolfColors
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(GolfColors.background)
                ) {
                    GolfWatchApp(
                        applicationContext
                    )
                }
            }
        }
    }
}

private data class PickerReq(
    val title: String,
    val options: List<String>,
    val current: String?,
    val onSelect: (String?) -> Unit
)

// Aktuell laufende Schlagaufnahme
private data class Rec(
    val club: String?,
    val start: LL?,
    // Startzeit: geht in den Live-Zeiger, damit das Handy „seit 40 s" anzeigen
    // und eine vergessene Aufnahme nach 30 min als abgelaufen erkennen kann.
    val at: String = isoNow(),
    /* SCHWUNGLAENGE (2026-08-10). Die PWA lernt die Schlaegerlaengen NUR aus
       vollen Schwuengen (clubMeasured filtert `swing`). Ohne dieses Feld
       zaehlte jeder auf der Uhr getrackte Schlag als voll — ein halber Wedge
       mit 55 statt 92 m zoege die gelernte Laenge nach unten und machte die
       Caddy-Empfehlung systematisch zu kurz. `null` bedeutet „Voll", so wie
       alle Altdaten gemeint waren. */
    val swing: String? = null,
    /* GENAUIGKEIT DES STARTPUNKTS (2026-08-12). Vorher schrieb recStop in
       accA UND accB dieselbe Zahl: die Genauigkeit des ENDpunkts. Die des
       Startpunkts wurde nirgends festgehalten. Damit war die Gewichtung nach
       accA/accB in der PWA wirkungslos — ein Wert war nur eine Kopie des
       anderen. */
    val startAcc: Float? = null
)

// Alles, was der Loch-Screen an Live-Werten anzeigt
/* WAS DIE UHR UEBER IHRE POSITION WEISS — mehr nicht (2026-08-26 (40)).
   `front`, `mid`, `back`, `pin`, `greenDepth`, `greenWidth` sind weg. Sie
   waren gerechnete Groessen und wurden seit (38) nicht mehr gefuellt.
   Was bleibt, ist gemessen: Gibt es einen Fix, wie genau ist er, und woran
   hakt es sonst. */
private data class PlayLive(
    val hasFix: Boolean,
    val acc: Int?,
    val err: String?
)

@Composable
fun GolfWatchApp(
    ctx: Context
) {

    // "Immer an" während einer Runde: erzwingt keepScreenOn dynamisch,
    // unabhängig vom Toggle — und zwar SOFORT (nicht erst ab Neustart).
    val rootView = LocalView.current

    // Als Zustand halten statt bei JEDER Recomposition von der Platte lesen.
    var keepPref by remember { mutableStateOf(prefGetB(ctx, "keepScreen", true)) }
    var gpsSource by remember { mutableStateOf(prefGet(ctx, "gpsSource", "watch")) }

    /* UMGEKEHRT gegenüber früher (2026-08-12): Vorher galt
       `Live.running || keepPref` — die laufende Runde ERZWANG einen dauerhaft
       hellen Bildschirm. Genau das schließt den Ambient-Modus aus, denn Wear
       OS wechselt nicht in Always-On, solange keepScreenOn gesetzt ist. Jetzt
       gilt: während der Runde geht die Uhr in den gedimmten Ambient-Screen
       (sichtbar bleibt sie trotzdem, dafür sorgt der AmbientLifecycleObserver),
       außerhalb der Runde entscheidet weiter der Nutzer-Toggle. */
    LaunchedEffect(Live.running, keepPref) {
        rootView.keepScreenOn = keepPref && !Live.running
    }

    /* ==========================================================================
       DER SENDE-AUFTRAG DARF NICHT AN DER ANZEIGE HAENGEN (2026-08-25 (28))
       --------------------------------------------------------------------------
       GEMESSEN am 25.08.: Waehrend man auf die Uhr schaut, kommen Eingaben in
       4–5 s beim Handy an. Legt man den Arm herunter, werden daraus 119–208 s
       (Median 168) — bei „keine Luecke": Es geht nichts verloren, es kommt
       nur zu spaet.
       URSACHE: `scheduleSync()` startet seinen Auftrag auf
       `rememberCoroutineScope()`. DIESER BEREICH GEHOERT DER KOMPOSITION —
       verlaesst der Bildschirm die Anzeige, wird er ABGEBROCHEN, und mit ihm
       der Sende-Auftrag mitsamt seiner 600-ms-Entprellung. Der Herzschlag holt
       es spaeter nach, daher die Minuten.
       (25) hat den WakeLock repariert und die Verzoegerung von 28 Minuten auf
       drei gedrueckt — der Prozess lebt jetzt. Der AUFTRAG starb trotzdem,
       weil er an der Anzeige hing und nicht am Prozess.
       `syncScope` ist ein eigener Bereich mit `SupervisorJob`: Er ueberlebt
       jeden Bildschirmwechsel und jedes Einschlafen der Anzeige. Ein
       `SupervisorJob`, damit ein gescheiterter Vorgang nicht die folgenden
       mitreisst — genau das war der Fehler von (10) in anderer Gestalt.
       ZUM MITNEHMEN: Ein Auftrag, der etwas SENDEN soll, gehoert an die
       Lebensdauer des Prozesses — nicht an die eines Bildes. */
    val syncScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    DisposableEffect(Unit) { onDispose { syncScope.cancel() } }

    val scope = rememberCoroutineScope()

    var screen by remember {
        mutableStateOf("home")
    }

    /* ==================================================================
       TURNIERMODUS (2026-08-30 (54))
       --------------------------------------------------------------------
       GEWUENSCHT am 30.08.: „Fuehre auf der Uhr noch einen Turniermodus ein,
       den ich auf der Startseite auswaehlen kann. Da erfasse ich dann auf
       einem Loch nur den Gesamtscore von mir und einem Mitspieler. Nichts
       anderes."
       WOZU: Im Turnier zaehlt man fuer sich UND fuer einen Mitspieler, und
       zwar unter Zeitdruck und mit Handschuh. Alles, was die normale
       Eingabemaske sonst kann — Putts, Lage, Schlaeger, Strafschlaege —, ist
       dort nicht nur ueberfluessig, sondern im Weg: Jede zusaetzliche Zeile
       ist eine Gelegenheit, das Falsche zu tippen.
       NICHTS ANDERES HEISST NICHTS ANDERES. Zwei Zahlen, ein Loch, weiter.
       DER MITSPIELER KOMMT VOM HANDY, wie alle Mitspieler seit (42): Die
       `index.html` ist dort fuehrend, die Uhr fuehrt keine eigene Liste. Ohne
       angelegten Mitspieler bleibt der Modus verschlossen — mit einem Satz,
       der sagt, was zu tun ist, statt eines gesperrten Knopfes ohne Grund.
       ES IST EIN ZUSTAND, KEIN ZWEITER ABLAUF: Die Runde kommt weiterhin vom
       Handy, wird dort beendet, und die Eingaben gehen denselben Weg in den
       Entwurf. Nur die MASKE ist eine andere. Ein zweiter Ablauf waere ein
       zweiter Ort fuer dieselben Fehler. */
    var turnier by remember { mutableStateOf(false) }

    var data by remember {
        mutableStateOf<AppData?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var status by remember {
        mutableStateOf("")
    }

    var course by remember {
        mutableStateOf<CourseDef?>(null)
    }

    var tee by remember {
        mutableStateOf("Gelb")
    }

    var hi by remember {
        mutableStateOf<Double?>(null)
    }

    val entries = remember {
        mutableStateMapOf<Int, HoleEntry>()
    }

    // mutableIntStateOf statt mutableStateOf: kein Autoboxing bei jedem
    // Lochwechsel. Einzeln winzig, in Summe ueber Timer und Ticks spuerbar.
    var idx by remember {
        mutableIntStateOf(0)
    }

    var syncJob by remember {
        mutableStateOf<Job?>(null)
    }

    var picker by remember {
        mutableStateOf<PickerReq?>(null)
    }

    var roundStart by remember {
        mutableStateOf<Long?>(null)
    }

    // id der Runde, wenn sie vom Handy stammt (sonst null = Alleinstart)
    var roundId by remember {
        mutableStateOf<String?>(null)
    }

    /* Rundenumfang: "18 Loch" | "Front 9" | "Back 9" (Vokabular der PWA).
       SEIT (44) NUR NOCH UEBERNOMMEN, nie auf der Uhr gesetzt — er steht in
       der Runde des Handys (`dr.side`) und wird beim Holen mitgezogen. */
    var side by remember {
        mutableStateOf("18 Loch")
    }

    // Wartet der Startbildschirm gerade auf eine Runde vom Handy?
    var awaitingPhone by remember {
        mutableStateOf(false)
    }

    // --- Live / Caddy / Schlagtracking ---

    var clubs by remember {
        mutableStateOf<List<ClubDist>>(emptyList())
    }

    val measurements = remember {
        mutableStateListOf<JSONObject>()
    }

    var weather by remember {
        mutableStateOf<Weather?>(null)
    }

    /* `caddyMode` entfallen (40) — der Modus safe/bal/aggr steuerte den Caddy
       der Uhr, den es nicht mehr gibt. Der Pref-Schluessel bleibt liegen und
       schadet nicht; das Handy fuehrt seinen eigenen. */

    /* `plan` UND DER ERKLAERTEXT ZUR AUTOMATIK SIND WEG (40, nachgereicht).
       Hier stand `var plan by remember { mutableStateOf<Caddy.Plan?>(null) }` —
       der Zustand, den die geloeschte Caddy-Schleife fuellte und die geloeschte
       Seite 0 anzeigte. Beim Abbau uebersehen, weil er von keiner
       Aufrufstelle mehr gelesen wurde: `Caddy.Plan` als TYP-Angabe faellt bei
       einer Suche nach `Caddy.plan(` durch das Raster.
       Darueber stand noch der Erklaertext zur automatischen Schlagerfassung —
       ein Absatz im Praesens ueber eine Funktion, die es seit (35) nicht mehr
       gibt, und ueber einen Schalter auf einer Seite, die es seit (38) nicht
       mehr gibt. */

    var rec by remember {
        mutableStateOf<Rec?>(null)
    }

    // laeuft gerade ein 3-Sekunden-Messfenster? Sperrt Doppelausloesung.
    /* Mitspieler-Namen und Platzzahl aus den Prefs, bevor der erste Pull sie
       liefert (37/39). */
    /* SPIEGEL DES SINGLETONS ALS COMPOSE-STATE (39/42): `Mitspieler.namen` ist
       ein `@Volatile var` — Compose abonniert es nicht und zeichnet beim
       Eintreffen neuer Namen nicht neu. Dieser State ist die einzige Groesse,
       die die Oberflaeche liest; das Singleton bleibt der Speicher.
       Seit (42) sind es die NAMEN und nicht mehr eine Platzzahl: Die Uhr
       fuehrt keine eigene Liste, sie spiegelt die des Handys. */
    var mitspielerNamen by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        Mitspieler.laden(ctx)
        mitspielerNamen = Mitspieler.namen
    }

    var measuring by remember {
        mutableStateOf(false)
    }
    /* Wann wurde `measuring` gesetzt? Der Waechter in `recBegin` braucht das,
       um ein Haengenbleiben von einem laufenden Sammelfenster zu unterscheiden
       (2026-08-28 (50)). */
    var measuringSeit by remember { mutableLongStateOf(0L) }

    val activity = LocalContext.current as? Activity

    // Veränderbar! Vorher war das ein reines remember{} — "Verwerfen" löschte
    // zwar die Datei, der Zustand blieb aber stehen, also blieb der
    // Fortsetzen-Button sichtbar und arbeitete mit veralteten Daten.
    /* STARTBILDSCHIRM-RUCKLER (2026-08-12). Vorher stand hier
       `mutableStateOf(loadLocal(ctx))` — also SharedPreferences oeffnen UND
       den kompletten JSON der gesicherten Runde parsen (18 Loecher mit allen
       Schlagpunkten), synchron auf dem Main-Thread, mitten in der ERSTEN
       Composition. Genau das war der Haenger beim App-Start: der erste Frame
       konnte erst gezeichnet werden, wenn Datei-I/O und Parser fertig waren.

       Jetzt startet der Bildschirm sofort leer und der Fortsetzen-Chip
       erscheint, sobald der Hintergrund-Ladevorgang durch ist — meist so
       schnell, dass man es nicht sieht. */
    var resume by remember {
        mutableStateOf<Loaded?>(null)
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { loadLocal(ctx) }
        if (loaded != null) resume = loaded
    }

    // Stammdaten SOFORT aus dem Cache — ohne auf das Netz zu warten. Danach
    // im Hintergrund aktualisieren. Das ist der Unterschied zwischen
    // "Startbildschirm ist sofort da" und "wartet auf einen Timeout".
    var dataFresh by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cached = withContext(Dispatchers.IO) { cacheRead(ctx) }
        if (cached != null && data == null) {
            data = cached
            hi = cached.hi
            if (cached.clubs.isNotEmpty()) clubs = cached.clubs
        }
        val (d, fresh) = withContext(Dispatchers.IO) { loadData(ctx) }
        if (d != null) {
            data = d
            dataFresh = fresh
            hi = d.hi
            if (d.clubs.isNotEmpty()) clubs = d.clubs
        }
    }

    // Berechtigungen: Standort ist Pflicht fürs Live-Tracking,
    // Notification ab Android 13 für die Dauer-Anzeige des Service.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun askPerms() {
        val want = ArrayList<String>()
        if (!hasLocPerm(ctx)) {
            want.add(Manifest.permission.ACCESS_FINE_LOCATION)
            want.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            want.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (want.isNotEmpty()) {
            permLauncher.launch(want.toTypedArray())
        }
    }

    fun scored() =
        entries.values.count {
            it.score != null
        }

    // Gesamt über/unter Par über alle erfassten Löcher + Anzahl gespielter Löcher
    fun overPar(): Pair<Int, Int> {
        val cs = course ?: return 0 to 0
        var op = 0
        var thru = 0
        cs.holes.forEach { hd ->
            entries[hd.hole]?.score?.let { sc ->
                op += sc - hd.par
                thru++
            }
        }
        return op to thru
    }

    /* LOKALE SICHERUNG (2026-08-12 vom Main-Thread genommen).

       Vorher lief hier alles synchron in der UI: JSONObject fuer 18 Loecher,
       alle Schlaeger und die komplette measurements-Liste aufbauen, dann
       `o.toString()`. Das waechst mit jedem erfassten Schlag — gegen Ende
       einer Runde sind das zweistellige Millisekunden am Stueck. Ausgeloest
       wird es bei JEDER Eingabe und zusaetzlich aus `adoptHoles()`, also
       unregelmaessig alle 20 bis 120 Sekunden aus der Sync-Schleife heraus.
       Genau dieses Muster erzeugt Ruckler, die "zwischendurch" auftreten und
       im Lauf der Runde haeufiger werden.

       ZWEITER PUNKT — Nebenlaeufigkeit: `entries` ist eine
       mutableStateMapOf, `measurements`/`clubs` sind Compose-Listen. Sie im
       Hintergrund zu durchlaufen, waehrend der Main-Thread sie aendert, endet
       frueher oder spaeter in einer ConcurrentModificationException. Deshalb
       wird hier auf dem Main-Thread eine flache Kopie gezogen (billig, nur
       Referenzen) und ausschliesslich die serialisiert. */
    fun persist() {

        val cs = course ?: return

        val snapEntries = HashMap(entries)
        val snapClubs = clubs.toList()
        val snapMeas = measurements.toList()
        val snapTee = tee
        val snapHi = hi
        val snapStart = roundStart
        val snapId = roundId
        val snapSide = side

        diskScope.launch {
            try {
                saveLocal(
                    ctx,
                    cs,
                    snapTee,
                    snapHi,
                    false,
                    snapStart,
                    snapEntries,
                    snapClubs,
                    snapMeas,
                    snapId,
                    snapSide
                )
            } catch (e: Exception) {
                // Eine fehlgeschlagene Sicherung darf die Runde nicht abreissen;
                // der naechste Tap versucht es ohnehin erneut.
            }
        }
    }

    // Repo-Sync SOFORT (nutzt den bereits lokal gesicherten Stand). Lokal ist durch
    // persist() bei jeder Eingabe ohnehin schon gesichert; dieser Push bringt den
    // Entwurf zusätzlich ins Repo.
    // Eingaben vom anderen Gerät (aus dem gemergten Repo-Entwurf) in die
    // lokalen entries übernehmen — NUR Felder, die hier noch leer sind.
    // Eigene Eingaben gewinnen immer (gleiche Regel wie playAdoptDraft in der PWA).
    fun adoptHoles(arr: JSONArray) {

        var changed = false

        for (i in 0 until arr.length()) {

            val o = arr.optJSONObject(i) ?: continue
            val n = o.optInt("hole")
            if (n <= 0) continue

            val inc = jsonToEntry(o)
            val cur = entries[n] ?: HoleEntry()

            /* WESSEN WERT GILT (2026-08-15 (7)): Traegt der fremde Stand fuer
               DIESES Loch den juengeren Zeitstempel, gewinnen seine GESETZTEN
               Felder — sonst bleibt es beim alten, sicheren Verhalten „nur
               leere Felder fuellen". Fehlt ein Zeitstempel (Entwurf von vor
               dieser Fassung), gilt ebenfalls das alte Verhalten.
               `null` loescht in KEINEM Fall etwas: Wer ein Feld leert, muss es
               am selben Geraet tun. */
            val fremdNeuer = inc.ts != null && (cur.ts == null || inc.ts > cur.ts)
            fun <T> nimm(eigen: T?, fremd: T?): T? =
                if (fremdNeuer && fremd != null) fremd else eigen ?: fremd

            val merged = cur.copy(
                score = nimm(cur.score, inc.score),
                putts = nimm(cur.putts, inc.putts),
                tee = nimm(cur.tee, inc.tee),
                appr = nimm(cur.appr, inc.appr),
                apprMiss = nimm(cur.apprMiss, inc.apprMiss),
                apprClub = nimm(cur.apprClub, inc.apprClub),
                penN = nimm(cur.penN, inc.penN),
                firstPutt = nimm(cur.firstPutt, inc.firstPutt),
                puttMiss = nimm(cur.puttMiss, inc.puttMiss),
                puttRest = nimm(cur.puttRest, inc.puttRest),
                kurzseitig = nimm(cur.kurzseitig, inc.kurzseitig),
                quality = nimm(cur.quality, inc.quality),
                club = nimm(cur.club, inc.club),
                lie = nimm(cur.lie, inc.lie),
                distToPin = nimm(cur.distToPin, inc.distToPin),
                bunkerN = nimm(cur.bunkerN, inc.bunkerN),
                b1 = nimm(cur.b1, inc.b1),
                penType = nimm(cur.penType, inc.penType),
                ud = nimm(cur.ud, inc.ud),
                ss = nimm(cur.ss, inc.ss),
                recovery = nimm(cur.recovery, inc.recovery),
                gir = nimm(cur.gir, inc.gir),
                msc1 = nimm(cur.msc1, inc.msc1),
                msc2 = nimm(cur.msc2, inc.msc2),
                msc3 = nimm(cur.msc3, inc.msc3),
                ts = if (fremdNeuer) inc.ts else cur.ts,
                shots = if (cur.shots.isEmpty()) inc.shots else cur.shots
            )

            if (merged != cur) {
                entries[n] = merged
                changed = true
            }
        }

        if (changed) {
            persist()
            status = "⌚↔📱 abgeglichen"
        }
    }

    /* Laufende Schlagaufnahme fuer den Live-Zeiger.

       WICHTIG — REIHENFOLGE: Diese Funktion MUSS oberhalb ihres ersten
       Aufrufers stehen. Lokale Funktionen in Kotlin sind erst AB ihrer
       Deklaration sichtbar; stand sie weiter unten (bei recBegin), brach der
       Build mit „Unresolved reference 'recLiveJson'" in syncNow() ab.
       `rec` ist weiter oben als remember-Zustand deklariert, passt also.

       KEIN SCHLAEGER MEHR VORAUSGESETZT (2026-08-27 (44)).
       Hier stand `val c = r.club ?: return null` mit der Begruendung „vorher
       hat das Handy nichts anzuzeigen". Das war falsch: Das Handy hat sehr
       wohl etwas anzuzeigen, naemlich DASS eine Aufnahme laeuft und WO sie
       begonnen hat. Genau das war die Meldung vom 27.08. — eine auf der Uhr
       eingeleitete Messung soll am Handy erscheinen.
       Und die Bedingung traf ausgerechnet den wichtigsten Moment: Man tippt
       „Schlag hier", geht los und waehlt den Schlaeger unterwegs; bis dahin
       wusste das Handy nichts. Mit Fassung (43) faellt die Schlaegerwahl in
       dieselben Sekunden, also blieb der Zeiger oft die halbe Strecke leer.
       `club` reist jetzt NUR MIT, WENN ES SCHON DA IST. Das Handy kommt damit
       zurecht (`watchRecBanner` zeigt „Schläger offen") und bietet das
       Abschliessen erst an, wenn ein Schlaeger feststeht — ein Schlag ohne
       Schlaeger ist fuer die gelernten Laengen wertlos. */
    fun recLiveJson(): JSONObject? {
        val r = rec ?: return null
        val st = r.start ?: return null
        return JSONObject()
            .put("src", "watch")
            .put("at", r.at)
            .put("lat", st.lat)
            .put("lng", st.lng)
            .also { o -> r.club?.let { o.put("club", it) } }
            .also { o -> r.swing?.let { o.put("swing", it) } }
    }

    /* ==========================================================================
       REIHENFOLGE BEACHTEN: Diese Zustaende und Hilfsfunktionen stehen
       ABSICHTLICH vor syncNow(). Lokale Deklarationen in Kotlin sind erst AB
       ihrer Zeile sichtbar — standen sie weiter unten, brach der Build mit
       „Unresolved reference: lastSyncMs" mitten in syncNow() ab. Dieselbe
       Falle wie bei recLiveJson (Eintrag vom 2026-08-09).
       ========================================================================== */
    /* Kurze haptische Rueckmeldung. Auf dem Platz schaut man nicht hin — ein
       Impuls bestaetigt, dass der Tipp angekommen ist. Bewusst kurz (40 ms):
       laenger wirkt wie eine Fehlermeldung. */
    /* ==========================================================================
       DREI SPUERBAR VERSCHIEDENE RUECKMELDUNGEN (2026-08-27 (46))
       --------------------------------------------------------------------------
       VORGABE VOM 27.08.: „Die Uhr soll vibrieren, wenn man das Schlagtracken
       startet, und vibrieren, wenn man es beendet."
       SIE TAT ES BEREITS — und trotzdem war die Meldung berechtigt: Start,
       Ende und Ablehnung gaben ALLE DASSELBE, einen 40-ms-Stups. Am
       Handgelenk, im Gehen, mit Handschuh ist das kaum wahrnehmbar und schon
       gar nicht unterscheidbar. Eine Rueckmeldung, die man nicht zuordnen
       kann, ist keine.
       JETZT DREI MUSTER, die sich im Rhythmus unterscheiden — nicht in der
       Staerke, denn Staerke spuert man durch einen Aermel schlecht:
         START     ein LANGER Stups (90 ms). „Es laeuft."
         ENDE      ZWEI kurze (50-70-50). Der Doppelschlag heisst „fertig" —
                   dasselbe Muster, das man von Timern kennt.
         ABLEHNUNG DREI kurze, schnelle (30-50 x3). Unruhig, und das soll es
                   auch sein: Hier ist gerade NICHTS aufgenommen worden.
       WARUM UEBERHAUPT UNTERSCHEIDBAR: Beim Ball sieht man nicht hin. Ob der
       Schlag steht oder verworfen wurde, muss durchs Handgelenk ankommen —
       sonst geht man weiter und merkt es am Loch danach.
       ========================================================================== */
    fun buzz(c: Context, muster: LongArray = longArrayOf(0L, 40L)) {
        try {
            val v = c.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            v?.vibrate(
                android.os.VibrationEffect.createWaveform(muster, -1)
            )
        } catch (e: Exception) {
            // Ohne Vibrationsmotor ist nichts zu tun.
        }
    }
    fun buzzStart(c: Context) = buzz(c, longArrayOf(0L, 90L))
    fun buzzEnde(c: Context) = buzz(c, longArrayOf(0L, 50L, 70L, 50L))
    fun buzzNein(c: Context) = buzz(c, longArrayOf(0L, 30L, 50L, 30L, 50L, 30L))

    var lastEditMs by remember { mutableLongStateOf(0L) }

    /* Zeitpunkt des letzten ERFOLGREICHEN Abgleichs. Die Uhr zieht im
       Sparbetrieb alle zwei Minuten, ueber das CDN koennen daraus mehr werden.
       Ohne Anzeige weiss man nie, ob die Zahlen von jetzt oder von vor zehn
       Minuten sind — und haelt einen veralteten Score fuer einen Fehler. */
    var lastSyncMs by remember { mutableLongStateOf(0L) }

    /* Alter des letzten Abgleichs als kurzer Text. Ab 5 Minuten in Rot —
       dann stimmt etwas nicht (Funkloch, Worker weg), und man sollte sich
       nicht auf die Zahlen verlassen. */
    fun syncAlter(): Pair<String, Boolean> {
        if (lastSyncMs == 0L) return Pair("—", false)
        val s = ((System.currentTimeMillis() - lastSyncMs) / 1000).toInt()
        return when {
            s < 60  -> Pair("${s}s", false)
            s < 300 -> Pair("${s / 60}min", false)
            else    -> Pair("${s / 60}min", true)
        }
    }

    fun syncNow() {

        val cs = course ?: return

        status = "sichere…"

        val pending = measurements.toList()

        /* Momentaufnahme auf dem Main-Thread. buildRoundJson lief bisher
           INNERHALB von withContext(Dispatchers.IO) und durchlief dort direkt
           die lebende `entries`-StateMap — waehrend nebenan getippt wurde.
           Das war eine ConcurrentModificationException, die nur darauf
           gewartet hat, mitten in der Runde aufzutreten. */
        val snapEntries = HashMap(entries)
        val snapTee = tee
        val snapHi = hi
        val snapWeather = weather
        val snapId = roundId
        val snapSide = side
        /* ==================================================================
           EIN LOCH AUSSERHALB DER LISTE LEGT DEN ABGLEICH LAHM (2026-08-25 (18))
           --------------------------------------------------------------------
           IM PULS STAND: „1 Vorgänge · eigenes Loch ? · Handy-Loch —".
           Das Fragezeichen ist die Antwort: `snapHole` war NULL. Und weil der
           ganze Live-Block in `pushDraft` hinter `if (currentHole != null)`
           steht, wurde er uebersprungen — kein Lochzeiger, kein Vergleich mit
           dem Handy, nichts. Der Push lief (HTTP 200), trug aber nichts bei.
           Von aussen sieht das aus wie „die Uhr sendet nicht".
           WIE ES DAZU KOMMT: `idx` zeigt auf einen Platz in der Lochliste. Ist
           er ausserhalb — etwa weil eine Runde mit anderer Lochzahl uebernommen
           wurde (im Protokoll: „18 Loch · 5 Löcher · Loch 13") —, liefert
           `getOrNull` null, und zwar STILL.
           ZWEI DINGE: In die Liste zurueckholen statt aufgeben, und den Fall
           melden. Ein Zustand, der alles Weitere lahmlegt, darf nicht lautlos
           sein — genau daran haben wir sechs Fassungen lang gesucht. */
        if (cs.holes.isNotEmpty() && (idx < 0 || idx >= cs.holes.size)) {
            val alt = idx
            idx = idx.coerceIn(0, cs.holes.size - 1)
            Fehler.warn("Lochzeiger",
                "idx $alt lag ausserhalb von ${cs.holes.size} Loechern — auf $idx gesetzt")
        }
        val snapHole = cs.holes.getOrNull(idx)?.hole
        if (snapHole == null) {
            Fehler.warnEinmal("snapHoleNull", "Lochzeiger",
                "kein Loch zu idx $idx (${cs.holes.size} in der Liste) — " +
                "der Live-Zeiger bleibt leer und das Handy sieht die Uhr nicht")
        }
        val snapRecLive = recLiveJson()

        scope.launch {

            val res = try {

                withContext(Dispatchers.IO) {
                    Net.pushDraft(
                        buildRoundJson(
                            cs,
                            snapTee,
                            snapHi,
                            false,
                            snapEntries,
                            snapWeather,
                            snapId,
                            snapSide
                        ),
                        pending,
                        /* ==================================================
                           DEN LOCHZEIGER SPAET LESEN (2026-08-25 (29))
                           ----------------------------------------------------
                           IM PULS VOM 25.08. STAND DER WIDERSPRUCH:
                             „Loch 5/18 · … · eigenes Loch 1"
                           Der Kontext wusste also Loch 5, gesendet wurde Loch 1.
                           URSACHE: `snapHole` wurde am ANFANG von `syncNow`
                           gelesen — vor dem Entprellen (600 ms), vor dem
                           Netzaufbau und vor bis zu vier Wiederholungen bei 409.
                           Zwischen Aufnahme und Absenden liegen so leicht
                           Sekunden, und wer in dieser Zeit weiterblaettert,
                           sendet ein Loch, auf dem er nicht mehr steht.
                           Bei einer Reihe schneller Eingaben — genau der Fall,
                           der gemeldet wurde — verschiebt sich dadurch JEDER
                           Zeiger um einen Schritt nach hinten.
                           JETZT WIRD ER HIER GELESEN, unmittelbar vor dem
                           Absenden. Die uebrigen Aufnahmen bleiben, wo sie sind:
                           Bei ihnen ist der fruehe Stand richtig, denn sie
                           beschreiben, WAS eingegeben wurde — der Zeiger dagegen
                           beschreibt, WO man JETZT steht.
                           ZUM MITNEHMEN: Eine Momentaufnahme ist nur so gut wie
                           der Abstand zu ihrer Verwendung. */
                        cs.holes.getOrNull(idx)?.hole ?: snapHole,
                        cs.name,
                        snapTee,
                        snapRecLive
                    )
                }

            } catch (e: Exception) { if (e.istAbbruch()) throw e; Fehler.add("Sync", e); Net.PushResult(false) }

            if (res.ok) {
                lastSyncMs = System.currentTimeMillis()
                // gepushte Messungen sind im Repo -> lokal nicht mehr nötig
                measurements.removeAll(pending)
                res.mergedHoles?.let { adoptHoles(it) }

                // Das Handy hat weitergeblättert -> mitziehen.
                res.remoteHole?.let { h ->
                    val t = cs.holes.indexOfFirst { it.hole == h }
                    if (t >= 0 && t != idx) {
                        idx = t
                        status = "📱 Handy: Loch $h"
                        // Sichtbar in der Spur — siehe (32): Lochwahl von aussen
                        // ist eine Handlung am Zustand.
                        Diagnose.aktion("Loch ⇐ Handy $h")
                    }
                }

                persist()
                if (res.remoteHole == null) status = "✓ gesichert (${scored()})"
            } else {
                status = "⚠ Repo-Sync später – lokal gesichert"
            }
        }
    }

    // Entprellter Repo-Sync: nach jeder Eingabe geplant, führt kurz nach der letzten
    // Aktion EINEN Push aus (fasst schnelle Taps zusammen, statt bei jedem Tap zu senden).
    fun scheduleSync() {

        syncJob?.cancel()

        /* NICHT `scope` — der gehoert der Komposition und stirbt mit ihr
           (2026-08-25 (28)). */
        syncJob = syncScope.launch {
            /* ENTPRELLUNG 1500 -> 600 ms (2026-08-24 (5)).
               Sie fasst schnelle Taps zusammen — das bleibt richtig. Aber
               1,5 s waren der GROESSTE einzelne Posten auf dem Weg Uhr ->
               Handy, und ein Lochwechsel ist kein schneller Tap: Man drueckt
               einmal und schaut dann aufs Handy.
               600 ms fassen ein versehentliches Doppeltippen immer noch
               zusammen und sparen fast eine Sekunde. */
            delay(600)
            syncNow()
        }
    }

    // Speichern und – bei Erfolg – die App schließen
    /* ==========================================================================
       `finishAndClose` IST ENTFERNT (2026-08-27 (44))
       --------------------------------------------------------------------------
       VORGABE VOM 27.08.: Sichern, Abschliessen und Verwerfen einer Runde
       passieren AUSSCHLIESSLICH am Handy.
       Die Uhr schrieb hier die fertige Runde ins Repo und schloss sich
       danach selbst. Das war der zweite Weg, auf dem eine Runde entstehen und
       enden konnte — und ein zweiter Weg heisst zwei Zustaende, die
       auseinanderlaufen koennen. Die Runde liegt ohnehin vollstaendig im
       Entwurf (`draft.json`); das Handy macht daraus die gespeicherte Runde,
       mit Auswertung, Scorekarte und Handicap.
       WAS DIE UHR STATTDESSEN TUT: nichts. Sie hoert auf zu funken, wenn das
       Handy den Grabstein setzt (`discardedTs`) oder die Runde abschliesst —
       genau wie bisher.
       ========================================================================== */

    /* ==========================================================================
       EIGENE SCHLEIFE FUER DAS PROTOKOLL (2026-08-25 (15))
       --------------------------------------------------------------------------
       Vorher hing der Versand an der Akku-Schleife, und die steht hinter
       `screen != "play" || akkuGewarnt`. Ausserhalb einer Runde lief er also
       gar nicht, und ab der ersten Akkuwarnung dauerhaft nicht mehr.
       `LaunchedEffect(Unit)` laeuft, solange die App laeuft — unabhaengig von
       Bildschirm, Runde und Akku. `logPut` schreibt weiterhin NUR bei
       Aenderung; bei fehlerfreiem Betrieb entsteht also kein einziger Vorgang.
       EIN WEG, DER IMMER FUNKTIONIEREN SOLL, DARF NICHT AN EINER BEDINGUNG
       HAENGEN, DIE FUER ETWAS ANDERES GEDACHT IST. */
    LaunchedEffect(Unit) {
        while (true) {
            delay(120_000)          // alle 2 min, aber nur bei Aenderung ein Vorgang
            try { withContext(Dispatchers.IO) { Net.logPut() } }
            catch (e: Exception) {
                if (e.istAbbruch()) throw e
                /* still: siehe logPut — ein misslungener Versand darf das
                   Protokoll nicht weiter fuellen. */
            }
        }
    }

    /* Zeitpunkt der letzten Eingabe — steuert den adaptiven Sync-Takt.
       Bewusst KEIN remember-Zustand: Der Wert soll keine Neuzeichnung
       ausloesen, er wird nur gelesen, wenn die Schleife ohnehin laeuft. */
    fun change(
        hole: Int,
        t: (HoleEntry) -> HoleEntry
    ) {

        /* Jede Eingabe stempelt das Loch — nur so kann die Gegenseite
           entscheiden, wessen Wert der juengere ist (2026-08-15 (7)). */
        /* Vor der Aenderung merken — nachher laesst sich sonst nicht sagen, WAS
           sich geaendert hat (2026-08-25 (26)). */
        val vorherStr = entries[hole]?.toString() ?: ""
        entries[hole] =
            t(
                entries[hole]
                    ?: HoleEntry()
            ).copy(ts = isoNow())

        lastEditMs = System.currentTimeMillis()
        /* ==================================================================
           EINE EINGABE SCHUETZT AUCH DAS LOCH (2026-08-25 (19))
           --------------------------------------------------------------------
           Wer hier einen Score eintraegt, ist unzweifelhaft auf DIESEM Loch.
           Ohne Stempel konnte der naechste Abgleich das Loch des Handys
           zurueckholen — mitten in der Eingabereihe. Genau das ist passiert:
           sechs Scores eingetippt, fuenf davon auf demselben Loch gelandet.
           `holeGewechselt` heisst hier nicht „gewechselt", sondern „der
           Benutzer hat sich zu diesem Loch bekannt". Das ist dieselbe Aussage
           und derselbe Schutz. */
        Net.holeGewechselt()
        /* Was genau eingetragen wurde — Loch und Score. Ohne beides ist eine
           Zeile „Eingabe" nicht mit dem zu vergleichen, was drueben ankommt. */
        /* ==================================================================
           WELCHES FELD? (2026-08-25 (26))
           --------------------------------------------------------------------
           Die Spur schrieb „Eingabe L3 score=–" — dreimal hintereinander. Daraus
           war NICHT zu entscheiden, ob dort ein anderes Feld gefuellt wurde
           (Putts, Lage, Abschlag) oder ob ein Score verlorenging. Genau diese
           Zweideutigkeit hat die Spur eigentlich beseitigen sollen.
           JETZT WIRD VERGLICHEN, was sich tatsaechlich geaendert hat: Die
           Datenklasse gibt ihre Felder in `toString()` preis; was vorher und
           nachher verschieden ist, wird benannt. Das kommt ohne eine Liste von
           Feldnamen aus — eine solche Liste veraltet beim naechsten neuen Feld,
           und zwar lautlos. */
        val nachher = entries[hole]
        val geaendert = try {
            val a = vorherStr.substringAfter("(", "").removeSuffix(")").split(", ")
            val b = (nachher?.toString() ?: "").substringAfter("(", "").removeSuffix(")").split(", ")
            b.filterIndexed { i, w -> i < a.size && w != a[i] }
                .joinToString(", ") { it.take(28) }
                .ifBlank { "nichts" }
        } catch (e: Exception) {
            if (e.istAbbruch()) throw e
            "score=" + (nachher?.score?.toString() ?: "–")
        }
        Diagnose.aktion("Eingabe L$hole $geaendert")
        persist()          // lokal SOFORT sichern (jede Eingabe)
        scheduleSync()     // Repo-Sync entprellt anstoßen (jede Eingabe)
    }

    /* ==========================================================================
       AKKU-WARNUNG (2026-08-10)
       Achtzehn Loecher mit Dauer-GPS zehren. Geht die Uhr auf Loch 15 aus, ist
       die halbe Runde weg — und man merkt es erst, wenn man hinsieht.
       EINMALIGE Warnung unter 20 %, mit dem konkreten Ausweg: auf Handy-GPS
       umschalten spart am meisten. Danach nicht mehr melden; eine Warnung, die
       sich wiederholt, wird weggetippt und dann ganz uebersehen.
       ========================================================================== */
    var akkuGewarnt by remember { mutableStateOf(false) }
    LaunchedEffect(screen, akkuGewarnt) {
        /* SICHERHEITSNETZ (2026-08-16 (12)): Eine Ausnahme in einer
           Nebenlaeufigkeit landet beim globalen Auffang und beendet die
           GANZE App — nicht nur diesen Ablauf. Auf der Bahn ist das der
           schlimmste Fall. Notiert und weiterleben statt abstuerzen. */
        try {
        if (screen != "play" || akkuGewarnt) return@LaunchedEffect
        while (screen == "play" && !akkuGewarnt) {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val pct = bm?.getIntProperty(
                android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
            ) ?: -1
            if (pct in 1..19) {
                akkuGewarnt = true
                /* In GolfWatchApp heisst die Quelle `gpsSource` (Z3788).
                   `gps` gibt es hier nicht — das ist der Zustand INNERHALB von
                   HomeScreen und eine lokale Variable in parsePlans. */
                status = if (gpsSource == "phone") "🔋 $pct % — Runde bald sichern"
                else "🔋 $pct % — GPS-Quelle auf Handy spart Akku"
                buzz(ctx)
            }
            /* DAS PROTOKOLL WIRD HIER NICHT MEHR GESCHICKT (2026-08-25 (15)).
               Es hing an dieser Schleife — und die steht hinter
               `if (screen != "play" || akkuGewarnt) return`. Zwei Bedingungen,
               die beide nichts mit dem Protokoll zu tun haben: Ausserhalb einer
               Runde lief es GAR NICHT (meine Behauptung „laeuft auch ohne
               Runde" war schlicht falsch), und ab der ersten Akkuwarnung hoerte
               es dauerhaft auf.
               Es hat jetzt seine EIGENE Schleife weiter unten. Ein Weg, der
               immer funktionieren soll, darf nicht an einer Bedingung haengen,
               die fuer etwas anderes gedacht ist. */
            delay(300_000)          // alle 5 min genuegt
        }
    
        } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("Akku-Warnung", e)
        }
    }

    // ---------------- Live-Tracking ----------------

    // WICHTIG für die Reaktionsfähigkeit: Compose merkt sich, welche States
    // gelesen wurden. Ein unbedingtes Live.fix hier hieß, dass JEDER GPS-Tick
    // (1x/s) das komplette GolfWatchApp neu zusammensetzt — auch auf dem
    // Startbildschirm. Genau das waren die stockenden Buttons.
    /* BERUHIGTE Position fuer die Anzeige (2026-08-14 (3)) — siehe `Live.neuerFix`.
       Vorher stand hier `Live.fix`, also der rohe GPS-Takt: Jede Sekunde wurde
       das gesamte GolfWatchApp ungueltig, samt Pager, Listen und der
       Ring-Geometrie in `liveOf()`. Sichtbar aendert sich dabei fast nie etwas.
       Wer die ROHE Position braucht (Messung, Caddy), liest weiterhin
       `Live.fix` — aber ausserhalb der Composition. */
    val fix = if (screen == "play") Live.fixUi else null

    /* `targetOf` und `loadGeoAsync` entfernt (40). Beide bedienten die
       Platzgeometrie: Zielpunkt des Lochs und das Einlesen der Karte im
       Hintergrund. Ohne `parseGeo` gibt es nichts mehr einzulesen, und ohne
       Caddy nichts mehr zu zielen. Die Uhr braucht die Karte nicht — sie misst
       zwei Punkte und schickt sie hinueber. */

    /* ==========================================================================
       `liveOf` MISST NUR NOCH (2026-08-26 (38))
       --------------------------------------------------------------------------
       Bis (37) rechnete diese Funktion bei JEDEM GPS-Takt Front/Mitte/Back
       ueber die Ringgeometrie des Gruens und dazu die Gruenmasse. Beides las
       ausschliesslich Seite 0, und die ist entfallen.
       Uebrig bleibt, was die Uhr tatsaechlich WEISS statt errechnet: ob ein
       Fix da ist, wie genau er ist, und woran es sonst hakt. Genau diese drei
       Angaben braucht die neue Uhr — die Genauigkeit ist ab jetzt ihre
       einzige Qualitaetsgroesse, weil Start- und Endpunkt einer Messung
       direkt in die gelernten Schlaegerlaengen des Handys eingehen.
       Mit (40) sind auch die leeren Felder aus `PlayLive` verschwunden: Es
       traegt jetzt genau die drei Angaben, die es liefert. `hole` bleibt als
       Parameter, damit die Aufrufstelle unveraendert lesbar bleibt — welches
       Loch gemessen wird, gehoert zur Frage, auch wenn die Antwort es nicht
       mehr braucht. */
    @Suppress("UNUSED_PARAMETER")
    fun liveOf(hole: Int): PlayLive {

        val f = fix ?: return PlayLive(false, null, Live.err)

        return PlayLive(true, f.acc.roundToInt(), Live.err)
    }

    /* ==========================================================================
       KOPPLUNGSTEST BEANTWORTEN (2026-08-15 (15))
       --------------------------------------------------------------------------
       Das Handy legt einen PRUEFPLAN in `probe.json` — mehrere Aufgaben, jede
       mit der Erwartung der PWA. Die Uhr rechnet jede mit IHRER Karte und
       IHREN Formeln und legt die Ergebnisse zurueck; das Handy vergleicht.
       WOZU: Eine einzelne Distanz kann auf einem harmlosen Loch zufaellig
       stimmen. Der Plan prueft dort, wo es weh tut — vertauschtes Loch,
       Schlaeger, Auswahllisten und die Quelle der eigenen Daten — also WELCHE
       DATEN die Uhr hat. Die Rechenaufgaben („geo", „caddy", „lie") sind mit
       (40)/PWA v4.84 auf beiden Seiten entfallen.
       Alles ohne laufende Runde und ohne Spieldaten anzufassen. */
    /* `gpKurs` entfaellt (40, nachgereicht) — er merkte sich den im
       Gameplan-Bildschirm gewaehlten Platz, und den Bildschirm gibt es nicht
       mehr. */

    /* Der zuletzt gelesene Handy-Entwurf. */
    var repoDraft by remember { mutableStateOf<RepoDraft?>(null) }

    /* Ort fuer das Fehlerprotokoll: Bei jedem Bildschirmwechsel neu (2026-08-16
       (13)). Ein eigener Effekt statt eines Ausdrucks in `when` — dort waere er
       eine Zuweisung mitten in einem Wertausdruck und damit schwer zu lesen. */
    LaunchedEffect(screen) { Fehler.kontext = "Bildschirm $screen" }

    /* ==========================================================================
       STILLE STOERUNGEN MITSCHREIBEN (2026-08-16 (14))
       --------------------------------------------------------------------------
       Die gefaehrlichsten Fehler werfen gar keine Ausnahme — sie sehen aus wie
       Normalbetrieb:
         · GPS liefert seit Minuten keine Position (Ortung aus, kein Empfang),
         · der Abgleich hat seit Minuten nichts geschrieben,
         · die Daten sind alt, obwohl das Handy laufen sollte.
       Ohne Eintrag steht man auf der Bahn und weiss nur „irgendwas stimmt
       nicht". Gemeldet wird jeweils NUR BEIM WECHSEL des Zustands, nicht im
       Takt — sonst ist das Protokoll nach zehn Minuten voll mit derselben
       Zeile, und die eine Meldung, auf die es ankommt, ist verdraengt. */
    var gpsStumm by remember { mutableStateOf(false) }
    var gpsGrob by remember { mutableStateOf(false) }
    var syncStumm by remember { mutableStateOf(false) }
    LaunchedEffect(screen) {
        if (screen != "play") return@LaunchedEffect
        while (screen == "play") {
            delay(60_000)
            try {
                /* Feld heisst `ts`, nicht `at` — in der Fix-Klasse nachgesehen. */
                val fixAlter = Live.fix?.let { System.currentTimeMillis() - it.ts } ?: Long.MAX_VALUE
                val stumm = fixAlter > 180_000
                if (stumm != gpsStumm) {
                    gpsStumm = stumm
                    if (stumm) Fehler.warn("GPS",
                        "seit ${fixAlter / 1000} s keine Position (Quelle ${if (gpsSource == "phone") "Handy" else "Uhr"})")
                    else Fehler.add("GPS", "Position wieder da")
                }
                val syncAlter = System.currentTimeMillis() - lastSyncMs
                val sStumm = lastSyncMs > 0 && syncAlter > 300_000
                if (sStumm != syncStumm) {
                    syncStumm = sStumm
                    if (sStumm) Fehler.warn("Abgleich", "seit ${syncAlter / 1000} s nichts übertragen")
                    else Fehler.add("Abgleich", "läuft wieder")
                }
                /* GENAUIGKEIT: Eine Position mit 40 m Streuung ergibt eine
                   Distanz, die genauso aussieht wie eine gute — und zwei
                   Schlaeger danebenliegt. Der haeufigste Grund fuer
                   „die Uhr zeigt Unsinn" (2026-08-16 (15)). */
                Live.fix?.let { f ->
                    val schlecht = f.acc > 25f
                    if (schlecht != gpsGrob) {
                        gpsGrob = schlecht
                        if (schlecht) Fehler.warn("GPS-Genauigkeit",
                            "±${f.acc.toInt()} m — Distanzen und automatische Schläge unzuverlässig")
                        else Fehler.add("GPS-Genauigkeit", "wieder brauchbar (±${f.acc.toInt()} m)")
                    }
                }
                /* DATENALTER: Mit Schlaegerdistanzen von vor drei Wochen
                   rechnet der Caddy sauber — nur eben mit veralteten Zahlen. */
                if (!dataFresh) Fehler.warnEinmal("datenAlt", "Daten",
                    "Stand nicht aktualisiert — Schlägerdistanzen und Pläne können veraltet sein")
            } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("Zustandswache", e)
        }
        }
    }

    /* Wo stand die Uhr bei der letzten Meldung? Daran haengt, ob Bewegung
       allein eine neue Meldung rechtfertigt (2026-08-16 (5)). */
    var letztePos by remember { mutableStateOf<LL?>(null) }

    var probeBeantwortet by remember { mutableStateOf("") }
    LaunchedEffect(screen) {
        /* SICHERHEITSNETZ (2026-08-16 (12)): Eine Ausnahme in einer
           Nebenlaeufigkeit landet beim globalen Auffang und beendet die
           GANZE App — nicht nur diesen Ablauf. Auf der Bahn ist das der
           schlimmste Fall. Notiert und weiterleben statt abstuerzen. */
        try {
        /* NUR AUF DEM STARTBILDSCHIRM (2026-08-15 (17)).
           Vorher lief diese Schleife dauerhaft — alle 5 s eine Netzanfrage,
           auch waehrend einer Runde und auch mit dem Arm unten. Fuer ein
           Werkzeug, das man einmal in der Woche benutzt, ist das der falsche
           Preis: Es haette genau den Akku gekostet, den die Runde braucht.
           Der Test wird ohnehin zu Hause gefahren, mit der Uhr in der Hand. */
        while (screen == "home") {
            delay(5000)
            if (AmbientState.isAmbient) continue      // Arm unten: nichts tun
            val q = withContext(Dispatchers.IO) { Net.probeGet() } ?: continue
            val ping = q.optString("ping")
            if (ping.isBlank() || ping == probeBeantwortet) continue
            val auf = q.optJSONArray("aufgaben") ?: continue

            /* ==========================================================
               DER KOPPLUNGSTEST FRAGT NICHT MEHR NACH RECHNUNGEN (40)
               ------------------------------------------------------------
               Die Aufgaben „geo", „caddy" und „lie" sind entfallen. Sie
               verglichen die Distanzen, die Schlaegerwahl und die sieben
               Lagefaktoren der Uhr mit denen des Handys — sinnvoll, solange
               BEIDE rechneten. Seit (38) rechnet nur noch das Handy; ein
               Vergleich mit einer Seite ist keiner.
               Die Gegenseite muss ZEITGLEICH fallen (PWA v4.84): Ein Prueflauf,
               der eine Aufgabe stellt, die niemand beantwortet, meldet eine
               Abweichung, wo keine ist — und ein Pruefstand, der grundlos
               Alarm schlaegt, bringt einem bei, den Alarm zu ignorieren.
               WAS BLEIBT: „club", „clubs", „liste" und „quelle" — Auskuenfte
               darueber, WELCHE DATEN die Uhr hat. Genau das ist nach (38) die
               Frage, die sich noch stellt. */

            val erg = JSONObject()
            for (i2 in 0 until auf.length()) {
                val a = auf.optJSONObject(i2) ?: continue
                val r = JSONObject()
                when (a.optString("k")) {
                    "club" -> {
                        val name = a.optString("club")
                        val cd = clubs.firstOrNull { it.club == name }
                        val v = cd?.carry ?: cd?.total
                        if (v != null) r.put("wert", v)
                    }
                    "clubs" -> r.put("anzahl", clubs.size)
                    "liste" -> {
                        val l = when (a.optString("name")) {
                            "approachBuckets" -> data?.opts?.approachBuckets
                            "firstPuttDist" -> data?.opts?.firstPuttDist
                            "teeResults" -> data?.opts?.teeResults
                            else -> null
                        }
                        if (l != null) { r.put("anzahl", l.size); if (l.isNotEmpty()) r.put("erste", l[0]) }
                    }
                    "quelle" -> {
                        /* `karte` meldet ab (40) immer `false`: Die Uhr laedt
                           keine Platzgeometrie mehr. Das Feld bleibt, damit ein
                           aelteres Handy eine Antwort bekommt statt keiner —
                           und weil „nein" hier die richtige Auskunft ist. */
                        r.put("karte", false)
                        r.put("quelle", if (Net.lastWatchFile) "watch.json" else "trainingsdaten.json")
                    }
                }
                erg.put(i2.toString(), r)
            }

            val antwort = JSONObject()
                .put("pong", ping)
                .put("at", isoNow())
                .put("watchApp", WATCH_APP)
                .put("ergebnisse", erg)
            val ok = withContext(Dispatchers.IO) { Net.probePut(antwort) }
            if (ok) probeBeantwortet = ping
            else Fehler.add("Kopplungstest", "Antwort nicht schreibbar")
        }
    
        } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("Sync-Schleife", e)
        }
    }

    // Service starten, sobald gespielt wird (Doku 2b) — und beim Verlassen stoppen
    LaunchedEffect(screen) {
        if (screen == "play") {
            askPerms()
            /* ==================================================================
               DER DIENST HAELT MEHR ALS DIE ORTUNG (2026-08-25 (25))
               --------------------------------------------------------------------
               GEMESSEN mit der Eingabespur: Die Aktionen #1–#10 entstanden um
               14:34:55–14:35:45 und kamen um 15:03:11 beim Handy an — 28 Minuten
               spaeter, alle auf einmal. Die Uhr zeichnet also auf und sendet
               nicht: Sobald man wegschaut, steht der Takt; beim Aufwachen laeuft
               alles in einem Schwung raus.
               URSACHE: `svcStart` lief nur `if (!Live.running)`. Diese Bedingung
               verwechselt ZWEI Dinge — „laeuft die Ortung" und „laeuft der
               Dienst". Der Dienst haelt aber nicht nur GPS, sondern den
               PARTIAL_WAKE_LOCK, und der ist es, der den PROZESS am Leben haelt.
               Ohne ihn friert Android die App ein, sobald der Bildschirm ausgeht
               — samt der `LaunchedEffect`-Schleife, die den Abgleich sendet.
               Besonders tueckisch bei GPS-Quelle „Handy": Dann laeuft drueben
               die Ortung, `Live.running` ist gesetzt, und der Dienst startet NIE.
               JETZT IMMER, solange gespielt wird. `svcStart` ist mehrfach
               aufrufbar — `startForeground` auf einen bestehenden Dienst
               aktualisiert nur die Meldung, und der Lock wird nur einmal geholt
               (`wake == null`).
               EIN DIENST, DER DEN PROZESS AM LEBEN HAELT, DARF NICHT AN EINER
               BEDINGUNG HAENGEN, DIE VON ETWAS ANDEREM HANDELT. */
            svcStart(ctx, "Runde läuft")
        }
    }



    // Notification mit Loch + Stand aktuell halten
    LaunchedEffect(idx, entries.size, screen) {
        /* SICHERHEITSNETZ (2026-08-16 (12)): Eine Ausnahme in einer
           Nebenlaeufigkeit landet beim globalen Auffang und beendet die
           GANZE App — nicht nur diesen Ablauf. Auf der Bahn ist das der
           schlimmste Fall. Notiert und weiterleben statt abstuerzen. */
        try {
        if (screen == "play") {
            val cs = course
            if (cs != null) {
                val (op, thru) = overPar()
                val opTxt =
                    if (thru == 0) "±0"
                    else if (op == 0) "E"
                    else if (op > 0) "+$op"
                    else "$op"
                /* Auch hier kein direkter Index (2026-08-16 (11)): Die
                   Notification wird bei jeder Aenderung neu gebaut, auch
                   waehrend der Umfang gerade wechselt. */
                svcNote(
                    ctx,
                    "Loch ${cs.holes.getOrNull(idx)?.hole ?: "-"} · $opTxt ($thru)"
                )
            }
        }
    
        } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("Notification", e)
        }
    }

    // Herzschlag: solange die Runde läuft, alle 3 Minuten pushen — auch ohne
    // Eingabe. Der Push trägt den live-Zeiger, und NUR daran erkennt das Handy,
    // dass überhaupt eine Runde läuft. Ohne diesen Takt erfährt es davon erst,
    // wenn das erste Loch einen Score hat (buildRoundJson lässt leere Löcher weg).
    /* ADAPTIVER TAKT (2026-08-10). Vorher galten starr 180 s, egal ob gerade
       etwas passierte. Wer einen Score eintraegt, will ihn zeitnah am Handy
       sehen; wer bei Loch 7 auf den Flight wartet, braucht kein Dauerfunken.
       Deshalb: kurz nach einer Eingabe schnell, danach zurueck auf den
       Sparbetrieb. Das spart Akku UND beschleunigt genau die Momente, auf die
       es ankommt. */
    LaunchedEffect(screen) {
        /* SICHERHEITSNETZ (2026-08-16 (12)): Eine Ausnahme in einer
           Nebenlaeufigkeit landet beim globalen Auffang und beendet die
           GANZE App — nicht nur diesen Ablauf. Auf der Bahn ist das der
           schlimmste Fall. Notiert und weiterleben statt abstuerzen. */
        try {
        if (screen == "play") {
            syncNow()                       // sofort beim Betreten der Runde
            while (screen == "play") {
                /* TAKT NACH DEM UMBAU AUF draft.json (2026-08-14 (2)):
                   Ein Vorgang kostet nicht mehr 3 MB, sondern wenige Kilobyte —
                   damit ist der alte Sparbetrieb unnoetig langsam. Kurz nach
                   einer Eingabe alle 10 s, sonst alle 60 s. Der Herzschlag ist
                   das Signal, an dem das Handy die laufende Runde erkennt. */
                /* ==========================================================
                   BEWEGUNG IST AUCH EIN EREIGNIS (2026-08-16 (5))
                   ----------------------------------------------------------
                   Bisher haing der Takt allein an EINGABEN: kurz nach einer
                   Eingabe alle 10 s, sonst alle 60 s. Wer aber nur GEHT — vom
                   Abschlag zum Ball —, tippt nichts. Die Position der Uhr war
                   damit bis zu einer Minute alt, und das Handy rechnete seine
                   Empfehlung fuer einen Punkt, an dem man laengst nicht mehr
                   stand. Genau die Kette, die seit v3.18 die Empfehlung traegt.
                   Jetzt zaehlt auch, dass man sich BEWEGT hat: mehr als 15 m
                   seit der letzten Meldung heisst „neue Lage". 15 m, weil
                   darunter die Schlaegerwahl gleich bleibt und jede Meldung
                   Funk kostet. */
                val jetzt = System.currentTimeMillis()
                val frisch = jetzt - lastEditMs < 120_000
                val f = Live.fixUi
                val bewegt = f != null && letztePos?.let { Geo.dist(it, f.ll()) > 15.0 } ?: true
                /* ==================================================================
                   STAND DIE SCHLEIFE? (2026-08-25 (27))
                   --------------------------------------------------------------------
                   Der Verdacht aus (25) — Android friert den Prozess ein, wenn der
                   Bildschirm ausgeht — laesst sich HIER direkt messen, statt ihn aus
                   Ankunftszeiten zu erschliessen: Wir wissen, wie lange dieser
                   Durchlauf schlafen SOLLTE. Dauert er deutlich laenger, hat jemand
                   anders die Schleife angehalten.
                   Das ist der Unterschied zwischen „die Daten kamen spaet an" (koennte
                   auch Netz sein) und „die Schleife lief nicht" (kann nur der Prozess
                   sein). Genau diese Unterscheidung hat mir zwei Tage gefehlt.
                   AB DEM DOPPELTEN der erwarteten Zeit, und nur dann — ein Durchlauf,
                   der 200 ms zu spaet kommt, ist normal. */
                /* ==================================================================
                   EINE LAUFENDE MESSUNG IST DER EILIGSTE ZUSTAND (2026-08-27 (47))
                   --------------------------------------------------------------------
                   GEMELDET: „Die Uebertragung von der Uhr an die App, dass ein
                   Schlagtracking gestartet wurde, dauert sehr lange und klappt
                   manchmal nicht."
                   `rec != null` gehoert deshalb in die Eilbedingung. Vorher
                   entschied nur, ob zuletzt GETIPPT (`frisch`) oder GEGANGEN
                   (`bewegt`) wurde — und beim Aufnahmestart steht man still und
                   hat nichts getippt. Der Takt blieb also auf 60 Sekunden, genau
                   im ungeduldigsten Moment. */
                val sollWarten = if (rec != null || frisch || bewegt) 10_000L else 60_000L
                val vorSchlaf = System.currentTimeMillis()
                delay(sollWarten)
                val tatsaechlich = System.currentTimeMillis() - vorSchlaf
                if (tatsaechlich > sollWarten * 2) {
                    Fehler.warn("Takt",
                        "Schleife stand ${tatsaechlich / 1000} s statt ${sollWarten / 1000} s — " +
                        "der Prozess war eingefroren (Bildschirm aus?)")
                    Diagnose.taktStand++
                }
                /* ==================================================================
                   FRUEHER STAND HIER `if (rec == null)` — DER FEHLER (47)
                   --------------------------------------------------------------------
                   Der Kommentar dazu lautete „laufende Messung nicht stoeren".
                   Gemeint war Ruecksicht, gewirkt hat das Gegenteil: Solange eine
                   Aufnahme lief, hat diese Schleife GAR NICHT GESENDET. Der
                   Aufnahmestart erreichte das Handy damit ueberhaupt nur, wenn
                   zufaellig etwas anderes einen Vorgang ausloeste — ein
                   eingetippter Score etwa. Das ist das gemeldete „klappt manchmal
                   nicht", und es war kein Funkproblem, sondern eine Bedingung.
                   DIE LAUFENDE MESSUNG IST GERADE DAS, WAS DAS HANDY SEHEN SOLL
                   (Regel vom 27.08.: ein auf der Uhr eingeleitetes Schlagtracking
                   wird am Handy angezeigt). Sie zu verschweigen, um sie nicht zu
                   stoeren, verfehlt den Zweck.
                   `letztePos` wird waehrend einer Aufnahme NICHT fortgeschrieben:
                   Die Bewegungsschwelle soll weiter von der Stelle aus messen, an
                   der zuletzt gemeldet wurde — sonst schoebe sich der Bezugspunkt
                   waehrend des Gehens mit und `bewegt` bliebe ewig falsch. */
                if (rec == null) {
                    Live.fixUi?.let { letztePos = it.ll() }
                }
                run {
                    /* NICHT DOPPELT SENDEN (2026-08-24 (5)).
                       Seit eine Benutzerhandlung SOFORT sendet, kann dieser
                       Takt kurz danach ein zweites Mal dasselbe schicken —
                       der Gewinn an Tempo waere mit Funk und Akku bezahlt.
                       Lag der letzte Vorgang weniger als 5 s zurueck, wird
                       dieser Durchlauf ausgelassen; der naechste kommt in
                       10 s ohnehin. */
                    if (System.currentTimeMillis() - Net.letzterPushMs > 5_000) {
                        syncNow()
                    }
                }
            }
        }
    
        } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Diagnose.fehlerArt(e, e.message ?: "")
            Fehler.add("Uhr-Push", e)
        }
    }

    // Wetter (Open-Meteo) — beim ersten Fix und danach alle 20 Minuten
    LaunchedEffect(screen) {
        /* SICHERHEITSNETZ (2026-08-16 (12)): Eine Ausnahme in einer
           Nebenlaeufigkeit landet beim globalen Auffang und beendet die
           GANZE App — nicht nur diesen Ablauf. Auf der Bahn ist das der
           schlimmste Fall. Notiert und weiterleben statt abstuerzen. */
        try {
        while (screen == "play") {
            val f = Live.fix
            val stale =
                weather == null ||
                        System.currentTimeMillis() - (weather?.at ?: 0L) > 20 * 60 * 1000L
            if (f != null && stale) {
                val w = withContext(Dispatchers.IO) {
                    Net.fetchWeather(f.lat, f.lng)
                }
                if (w != null) weather = w
            }
            delay(60_000)
        }
    
        } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("Dienst", e)
        }
    }

    // Handy -> Uhr: während der Runde alle 90 s den Repo-Entwurf ziehen und
    // fremde Eingaben in LEERE Felder übernehmen (adoptHoles; eigene Eingaben
    // gewinnen immer). Der Push-Weg (syncNow) läuft ohnehin nach jeder Eingabe.
    // Hinweis: Pages-CDN cached die Datei — Latenz realistisch Minuten.
    LaunchedEffect(screen) {
        while (screen == "play") {
            /* Waehrend einer Schlagaufnahme oder kurz nach einer Eingabe
               haeufiger nachsehen — dann arbeitet meist auch das Handy. */
            /* Waehrend einer Schlagaufnahme oder kurz nach einer Eingabe
               haeufiger nachsehen — dann arbeitet meist auch das Handy.
               2026-08-14 (2): 20/120 s stammten aus der Zeit, als jeder Abruf
               die 3-MB-Datei zog. Ueber `draft.json` sind es wenige Kilobyte;
               damit ist der Lochwechsel am Handy in Sekunden auf der Uhr. */
            /* MASSSTAB IST DER BILDSCHIRM, nicht die letzte Eingabe: Wer auf
               die Uhr schaut, erwartet Gleichlauf mit dem Handy — auch wenn er
               seit zehn Minuten nichts eingetragen hat (etwa weil der Wechsel
               am HANDY passiert). Im Ambientmodus liegt der Arm unten; dort
               zaehlt nur, dass nichts verlorengeht. */
            /* ==================================================================
               SCHNELLER, SOLANGE DER ARM OBEN IST (2026-08-24 (5))
               --------------------------------------------------------------------
               GEMESSEN am 24.08.: Der Weg Handy -> Uhr dauerte bis zu ~6,5 s
               (5 s Takt + Netz), der Weg Uhr -> Handy bis zu ~6,5 s (1,5 s
               Entprellung + 5 s Takt des Handys). Zusammen fuehlt sich das an
               wie „reagiert nicht" — man blaettert, schaut auf das andere
               Geraet und sieht noch das alte Loch.
               DER TAKT DARF NUR DORT SCHNELLER WERDEN, WO ER NICHTS KOSTET:
               Bei ANGEHOBENEM Arm (kein Ambientmodus) schaut man hin und
               erwartet eine Antwort — 2 s. Im Ambientmodus liegt der Arm unten,
               dort zaehlt nur, dass nichts verlorengeht: 30 s bleiben.
               DAS IST KEIN FREIBRIEF: 2 s heisst 30 Anfragen je Minute, aber
               nur solange man tatsaechlich hinsieht — im Ambientmodus sind es
               weiterhin 2 je Minute. Der Akku merkt den Unterschied dort, wo
               die Uhr die meiste Zeit ist, und das ist der Arm unten. */
            val eilig = !AmbientState.isAmbient || rec != null ||
                    System.currentTimeMillis() - lastEditMs < 120_000
            delay(if (!AmbientState.isAmbient) 2_000 else if (eilig) 5_000 else 30_000)
            if (rec != null) continue
            val cn = course?.name ?: continue
            val dr = try {
                withContext(Dispatchers.IO) { Net.fetchDraft() }
            } catch (e: Exception) {
                Fehler.add("Entwurf lesen", e)   // 2026-08-16 (8)
                null
            }
            repoDraft = dr        // fuer die Caddy-Uebernahme (2026-08-16 (3))

            /* ==================================================================
               (A) QUITTIERTE SCHLAGMESSUNGEN AUSRAEUMEN (2026-08-24 (6))
               --------------------------------------------------------------------
               Die Uhr schickte ihre Messungen bei JEDEM Vorgang mit — sie
               erfuhr nie, ob sie angekommen sind, und trug sie die ganze Runde
               mit sich. `shotAck` nennt die Kennungen, die das Handy sicher
               hat; nur die werden entfernt.
               NUR KENNUNGEN, keine Messwerte: Geht die Quittung verloren,
               schickt die Uhr eben noch einmal. Das ist der harmlose Fall —
               umgekehrt waere es Datenverlust. */
            if (dr != null && dr.shotAck.isNotEmpty()) {
                val quittiert = dr.shotAck.toHashSet()
                val weg = measurements.filter { quittiert.contains(it.optString("id")) }
                if (weg.isNotEmpty()) measurements.removeAll(weg)
            }

            /* ==================================================================
               (B) AM HANDY BEENDET -> HIER AUCH (2026-08-24 (6))
               --------------------------------------------------------------------
               Bis hierher kannte die Uhr nur „verworfen" (`draftDiscardedTs`).
               Eine normal BEENDETE Runde sah fuer sie aus wie eine laufende,
               die nur nichts mehr meldet: Sie funkte weiter Herzschlaege fuer
               etwas, das es nicht mehr gab, und zeigte Loch 18, waehrend am
               Handy die Karte schon gespeichert war.
               NUR BEI DERSELBEN RUNDE: Platz und Datum muessen uebereinstimmen,
               sonst beendet eine fremde Meldung die eigene Runde. Und nur, wenn
               die Marke JUENGER ist als die letzte eigene Eingabe — sonst
               beendet eine alte Marke jede neue Runde sofort wieder. Beides
               sind Lehren aus (9) und (10) vom 15.08. */
            if (dr?.doneAt != null) {
                val at = dr.doneAt
                val gleichePlatz = dr.doneCourse == (course?.name ?: "")
                /* ==========================================================
                   FUERS RUNDENENDE FUEHRT DAS HANDY — UND NUR DAS HANDY (35)
                   ------------------------------------------------------------
                   Bisher galt die Marke nur, wenn sie JUENGER war als die
                   letzte eigene Eingabe (`maxOf(roundStart, lastEditMs)`) —
                   wieder ein Uhrenvergleich zweier Geraete, dieselbe Klasse,
                   die beim Lochzeiger fuenf Fassungen gekostet hat. Jede
                   Eingabe NACH dem Beenden (ein Tipp, frueher auch die
                   Schlag-Automatik) liess die Marke veralten; die Uhr spielte
                   weiter, ihr Dienst funkte weiter, der Entwurf stand wieder
                   auf — „Runde beenden funktioniert nicht zuverlaessig".
                   NEUE REGEL (Vorgabe vom 26.08.): Das Ende einer Runde
                   entscheidet das Handy, bedingungslos. Verglichen wird nur
                   noch gegen den RUNDENBEGINN — eine Marke, die aelter ist
                   als die eigene Runde, gehoert zu einer frueheren und wird
                   ignoriert (Schutz aus (9) bleibt); alles Juengere gilt,
                   egal was hier zuletzt getippt wurde.
                   UND AUFRAEUMEN WIE BEIM VERWERFEN: `screen = "home"` allein
                   liess Runde, Dienst und Wiederaufnahme stehen — die Uhr
                   funkte weiter und belebte den beendeten Entwurf neu. */
                val juenger = at.isNotEmpty() && roundStart != null &&
                    at > isoOf(roundStart ?: 0L)
                if (gleichePlatz && juenger) {
                    Diagnose.aktion("Runde ⇐ Handy beendet")
                    clearLocal(ctx)
                    svcStop(ctx)
                    entries.clear()
                    measurements.clear()
                    rec = null
                    resume = null
                    screen = "home"
                    status = "📱 Runde am Handy beendet"
                    continue
                }
            }
            /* AM HANDY VERWORFEN -> hier auch beenden (2026-08-15 (9)).
               Ohne Rueckfrage: Die Entscheidung ist drueben gefallen; eine
               zweite Frage waere nur eine Falle. Nur wenn die Marke JUENGER ist
               als der Beginn der eigenen Runde — sonst beendete eine alte Marke
               jede neue Runde sofort wieder. */
            /* STRENGER (2026-08-15 (10)): Nicht der Rundenbeginn zaehlt, sondern
               die letzte EIGENE Eingabe. Sonst beendet jede Marke, die waehrend
               der Runde entsteht, die eigene Runde — und genau das ist passiert:
               Das Handy schrieb (Fehler in PWA v3.02) im Sekundentakt einen
               Grabstein, die Uhr warf daraufhin die laufende Runde weg.
               Wer selbst gerade eingetragen hat, hat die juengere Aussage. */
            val disc = Net.lastDiscardedTs
            /* AUCH HIER FUEHRT DAS HANDY (35): Vergleich nur noch gegen den
               Rundenbeginn, nicht gegen die letzte Eingabe — Begruendung im
               roundDone-Block direkt darueber. Der Schutz aus (10) galt einem
               Handy-Fehler (v3.02, Grabstein im Sekundentakt), der seit v3.02
               behoben ist; die Regel „das Handy entscheidet" wiegt schwerer. */
            val eigenIso = isoOf(roundStart ?: 0L)
            if (disc != null && roundStart != null && disc > eigenIso) {
                clearLocal(ctx)
                svcStop(ctx)
                entries.clear()
                measurements.clear()
                rec = null
                resume = null
                screen = "home"
                status = "📱 Runde am Handy verworfen"
                continue
            }
            if (dr != null && dr.course == cn && dr.date == today()) {
                /* Namen kommen vom Handy — hier uebernehmen und merken.
                   `?.let` STATT `isNotEmpty()` (42): Eine leere Liste ist die
                   Aussage „keine Mitspieler" und muss ankommen, sonst laesst
                   sich am Handy kein Spieler mehr entfernen. Uebergangen wird
                   nur ein Entwurf, in dem der Schluessel GAR NICHT steht —
                   der stammt von der Uhr selbst. */
                dr.mitspieler?.let {
                    Mitspieler.setzen(ctx, it)
                    if (Mitspieler.namen != mitspielerNamen) mitspielerNamen = Mitspieler.namen
                }
                adoptHoles(dr.holes)

                // Handy hat weitergeblättert -> mitziehen. Nur ein Zeiger,
                // der jünger ist als der zuletzt selbst gesendete, zählt;
                // sonst würde das eigene Echo die Uhr zurückwerfen.
                /* Hat das Handy unsere Aufnahme abgeschlossen? Erkennbar
                   daran, dass der Live-Zeiger vom Handy stammt und KEIN rec
                   mehr enthaelt, waehrend hier noch eines laeuft. Ohne das
                   wuerde derselbe Schlag zweimal erfasst. */
                if (rec != null && dr.liveSrc == "phone" && dr.recSrc == null) {
                    rec = null
                    status = "📱 Schlag am Handy gespeichert"
                }

                val at = dr.liveAt
                val h = dr.liveHole
                /* ==================================================================
                   HANDLUNG GEGEN HANDLUNG, NICHT GEGEN SCHREIBZEIT (2026-08-25 (33))
                   --------------------------------------------------------------------
                   (32) hat hier `at > ownHoleAt` ergaenzt und behauptet, das Echo
                   der eigenen Uebernahme sei unschaedlich, weil es dasselbe Loch
                   trage. IM PROTOKOLL VOM 25.08., 20:31, STEHT DAS GEGENTEIL:
                   „Loch ⇐ Handy 2" und „Loch ⇐ Handy 3" — verspaetete Echos der
                   eigenen Schritte (Median 47 s, Tab gedrosselt), jedes mit
                   FRISCHEM Stempel und ALTEM Inhalt. Und „Loch ⇐ Handy 14":
                   ein automatischer Sprung des Handys beim Fortsetzen, von
                   keinem Benutzer gewaehlt, ebenfalls frisch gestempelt.
                   EIN ZEITSTEMPEL SAGT, WANN GESCHRIEBEN WURDE — NICHT, WIE ALT
                   DIE INFORMATION IST. Deshalb vergleicht die Regel jetzt die
                   HANDLUNG des Handy-Benutzers (`wahlAt` = PLAY.holeAt, im
                   Zeiger ab PWA v4.78) mit der HANDLUNG des Uhr-Benutzers
                   (`ownHoleAt`): Echos, Uebernahmen und Automatik tragen keine
                   oder eine alte Wahl und stellen die Uhr nicht mehr um. Ein
                   ECHTES Blaettern am Handy stempelt `wahlAt` frisch und kommt
                   durch — es sei denn, die Uhr-Handlung ist die juengere; dann
                   gewinnt sie, wie es die Regel seit (4) verspricht.
                   Die Regel steht EINMAL in `Net.fremderZeigerZaehlt` und gilt
                   fuer alle drei Stellen (zwei Push-Filter, dieser Pull). */
                /* (34): erst entscheiden, dann den Stand anheben — die
                   Beobachtung darf die Entscheidung nicht kippen. */
                val fremdZaehlt = dr.liveSrc != null && dr.liveSrc != "watch" &&
                    h != null &&
                    Net.fremderZeigerZaehlt(at, dr.liveWahlAt, dr.liveHoleSeq)
                if (dr.liveSrc != null && dr.liveSrc != "watch")
                    dr.liveHoleSeq?.let { Net.holeSeqGesehen(it) }
                if (fremdZaehlt) {
                    val t = course?.holes?.indexOfFirst { it.hole == h } ?: -1
                    if (t >= 0 && t != idx) {
                        idx = t
                        rec = null
                        status = "📱 Handy: Loch $h"
                        /* IN DIE SPUR — eine Lochwahl von aussen ist eine Handlung
                           am Zustand und gehoert nummeriert, sonst ist ein
                           Ruecksprung im Protokoll UNSICHTBAR (genau das hat die
                           Suche nach (32) gekostet). */
                        Diagnose.aktion("Loch ⇐ Handy $h")
                    }
                }
            }
        }
    }

    /* ==========================================================================
       DER CADDY DER UHR IST ABGESCHALTET (2026-08-26 (38))
       --------------------------------------------------------------------------
       Hier stand eine LaunchedEffect, die bei jeder nennenswerten Bewegung
       (~11-m-Raster) `Caddy.plan` ueber die Platzgeometrie rechnete und das
       Ergebnis gegen die Empfehlung des Handys stellte.
       SIE IST ERSATZLOS WEG, aus zwei Gruenden:
         1. Es gibt niemanden mehr, der das Ergebnis anzeigt. Seite 0 ist
            entfallen; eine Rechnung ohne Leser ist Akku, den man auf der
            18. Bahn braucht.
         2. Die Aufgabenteilung vom 26.08. sagt es ausdruecklich: Die gesamte
            Logik zur Anpassung der Laengen sitzt im Handy. Solange die Uhr
            mitrechnete, gab es zwei Antworten auf dieselbe Frage — genau der
            Zustand, den (16 (3)) schon einmal zu entschaerfen versuchte,
            indem die Uhr das Handy-Ergebnis uebernahm. Jetzt fragt sie gar
            nicht mehr.
       `Caddy`, `Wx.playsLike` und die Geometrie in `Geo` bleiben EINE Fassung
       lang unbenutzt im Quelltext stehen; der Abbau ist (39).
       WAS BLEIBT: die Wetterabfrage darueber. Sie ist keine Rechnung, sondern
       eine MESSUNG der Bedingungen, die mit der Runde gespeichert wird
       (`buildRoundJson` -> `conditions`). Startet die Runde auf der Uhr und
       liegt das Handy im Auto, ist sie die einzige Quelle dafuer.
       WAS EBENFALLS BLEIBT: `live.pos` im Zeiger. Die Uhr meldet weiter, WO
       sie steht — das Handy rechnet fuer diesen Punkt. Nach diesem Umbau ist
       das der einzige Weg, auf dem das Handy die Position am Ball erfaehrt,
       und damit tragend statt bloss hilfreich. */

    /* AUTO-LOCH ENTFERNT (2026-08-10). Der automatische Lochwechsel per
       Positionsnaehe stoerte auf dem Platz mehr als er half: beim Warten am
       naechsten Tee, beim Ballsuchen und auf dem Rueckweg sprang die Anzeige
       um — mitten in der Eingabe. Die PWA hat ihn mit v1.98 aufgegeben, die
       Uhr zieht nach. Gewechselt wird ausschliesslich von Hand. */

    // ---------------- Schlagtracking ----------------

    /* MESSUNG AM BALL (2026-08-12). Frueher wurde der erste beste Live.fix
       genommen — auch einer mit 25 m Genauigkeit oder ein Sekunden alter
       Handy-Fix. Jetzt sammelt FixQuality.collect() rund 3 s und mittelt
       invers-varianz-gewichtet. Die Wartezeit ist genau dort investiert, wo
       sie sich auszahlt: Anfangs- und Endpunkt gehen direkt in die gelernte
       Schlaegerlaenge ein. `measuring` verhindert doppelte Ausloesung,
       solange das Fenster laeuft. */
    fun recBegin() {

        /* ==================================================================
           EIN STUMMER KNOPF IST DER SCHLIMMSTE KNOPF (2026-08-28 (50))
           --------------------------------------------------------------------
           GEMELDET: „Wenn ich das Schlagtracken auf der Uhr ueber den Button
           aktiviere, passiert nichts. Der Button startet das Schlagtracken
           leider nicht."
           HIER STAND `if (measuring) return` — ein LAUTLOSER Ruecksprung. Und
           `measuring` wurde NUR IM ERFOLGSFALL freigegeben: Die Zeile
           `measuring = false` sass MITTEN im `scope.launch`, hinter
           `FixQuality.collect`. Endete die Nebenlaeufigkeit vorher
           (Bildschirm aus, Ambient-Wechsel, Neuaufbau der Oberflaeche) oder
           warf `collect`, blieb `measuring` FUER IMMER `true`.
           DANACH WAR DER KNOPF TOT: kein Zeichen, kein Ton, keine Zeile im
           Protokoll. Jeder weitere Tipp sprang stumm zurueck, bis jemand die
           App neu startete. Genau das ist die Meldung.
           DREI AENDERUNGEN, und die dritte ist die eigentliche:
           1. `measuring` wird im `finally` freigegeben — es gibt keinen Weg
              mehr aus dem Block, der es gesetzt laesst.
           2. EIN WAECHTER: Haengt es laenger als 20 s, gilt es als verklemmt
              und wird freigegeben. Das Sammelfenster dauert wenige Sekunden;
              alles darueber ist ein Fehler und kein Warten.
           3. KEIN STUMMER RUECKSPRUNG. Wer tippt, bekommt eine Antwort —
              Vibration, Status, Protokollzeile. Ein Knopf, der nichts tut und
              nichts sagt, ist schlimmer als einer, der eine Fehlermeldung
              zeigt: Man tippt weiter und verliert die Runde. */
        if (measuring) {
            val her = System.currentTimeMillis() - measuringSeit
            if (measuringSeit > 0L && her > 20_000L) {
                Diagnose.schlag("Messung war verklemmt", "${her / 1000} s — freigegeben")
                Fehler.add("Schlagmessung", IllegalStateException(
                    "measuring hing ${her / 1000} s — automatisch freigegeben"))
                measuring = false                 // und regulaer weiter
            } else {
                status = "messe gerade…"
                buzzNein(ctx)
                Diagnose.schlag("Tipp ins Leere", "Messung läuft bereits")
                return
            }
        }

        /* ABLEHNUNG MUSS MAN SPUEREN (2026-08-26 (38)).
           Bisher stand hier nur ein Text — auf einer Seite, die man beim Ball
           nicht liest, und mit Handschuh schon gar nicht. Man tippte, ging
           weiter und merkte am Loch danach, dass nichts aufgenommen war.
           Ein Erfolg vibriert seit jeher (`buzz`); ein MISSERFOLG muss es
           genauso, sonst ist „nichts passiert" und „alles gut" am Handgelenk
           nicht unterscheidbar. Das gilt seit dieser Fassung doppelt: Die
           Messung ist der einzige Zweck der Uhr geworden. */
        Diagnose.schlagBeginnMs = System.currentTimeMillis()
        Diagnose.schlag("Start getippt",
            "Fix " + (Live.fix?.acc?.roundToInt()?.let { "±$it m" } ?: "keiner"))
        if (!FixQuality.usable(Live.fix)) {
            status =
                if (Live.fix == null) "warte auf GPS…"
                else "GPS zu ungenau (${Live.fix?.acc?.roundToInt()} m)"
            buzzNein(ctx)
            Diagnose.schlag("Start abgelehnt", "GPS zu ungenau")
            return
        }

        measuring = true
        measuringSeit = System.currentTimeMillis()
        status = "messe…"

        scope.launch {
            /* SICHERHEITSNETZ (2026-08-16 (12)): Eine Ausnahme in einer
               Nebenlaeufigkeit landet beim globalen Auffang und beendet die
               GANZE App — nicht nur diesen Ablauf. Auf der Bahn ist das der
               schlimmste Fall. Notiert und weiterleben statt abstuerzen. */
            try {
            val f = FixQuality.collect { n -> status = "messe… ($n)" }
            if (f == null) {
                status = "GPS zu ungenau — nicht gestartet"
                buzzNein(ctx)
                Diagnose.schlag("Start abgelehnt", "Sammelfenster ohne brauchbaren Fix")
                return@launch
            }
            rec = Rec(null, f.ll(), startAcc = f.acc)
            status = "Aufnahme laeuft · ±${f.acc.roundToInt()} m"
            buzzStart(ctx)
            Diagnose.schlag("Startpunkt steht", "±${f.acc.roundToInt()} m")
            /* ==================================================================
               SOFORT MELDEN, NICHT BEIM NAECHSTEN TAKT (2026-08-27 (47))
               --------------------------------------------------------------------
               GEMELDET: Der Aufnahmestart kam „sehr spaet und manchmal gar nicht"
               am Handy an. Er wurde hier naemlich UEBERHAUPT NICHT gesendet — er
               wartete auf den Sendetakt, und der stand im Ruhezustand auf 60 s
               (man hat gerade nichts getippt und sich nicht bewegt) und
               uebersprang laufende Aufnahmen zudem ganz.
               EIN ZUSTANDSWECHSEL, DEN DAS ANDERE GERAET ANZEIGEN SOLL, IST EIN
               EREIGNIS — kein Zustand, der beim naechsten Takt mitkommt. Genau
               dieselbe Lehre wie bei den Eingaben (5): Eine Benutzerhandlung
               sendet sofort.
               `letzterPushMs` bleibt der Riegel gegen Doppelsendungen; die
               Schleife laesst ihren Durchlauf aus, wenn dieser hier gerade lief. */
            syncNow()
        
            } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("Schlag ablegen", e)
            } finally {
                /* HIER UND NUR HIER (2026-08-28 (50)). `measuring = false` sass
                   frueher MITTEN im Ablauf, hinter `FixQuality.collect` — jeder
                   Weg daran vorbei (Ausnahme, Abbruch der Nebenlaeufigkeit,
                   frueher `return@launch`) liess die Sperre stehen, und der
                   Knopf war danach stumm tot.
                   `finally` LAEUFT AUCH BEI ABBRUCH, und genau der ist der Fall,
                   der auf der Uhr staendig vorkommt: Bildschirm aus, Ambient,
                   Neuaufbau. Der Abbruch wird oben weitergeworfen — aufgeraeumt
                   wird trotzdem. */
                measuring = false
                measuringSeit = 0L
            }
        }
    }

    fun recClub(c: String?) {
        val r = rec ?: return
        // Startpunkt NICHT stillschweigend durch einen ungeprueften Live.fix
        // ersetzen — sonst haette die Schlaegerwahl die gemessene Position
        // wieder verworfen. Nur uebernehmen, wenn er wirklich brauchbar ist.
        val f = Live.fix
        if (FixQuality.usable(f) && f != null) {
            rec = Rec(c, f.ll(), r.at, r.swing, f.acc)
        } else {
            rec = Rec(c, r.start, r.at, r.swing, r.startAcc)
        }
    }

    /* Schwunglaenge waehlen. „Voll" wird als null gespeichert — das haelt die
       Daten klein und entspricht der Bedeutung in der PWA. */
    fun recSwing(v: String?) {
        val r = rec ?: return
        rec = Rec(
            r.club,
            r.start,
            r.at,
            if (v == null || v == "Voll") null else v,
            r.startAcc
        )
    }

    fun recCancel() {
        rec = null
        Diagnose.schlag("abgebrochen")
        Diagnose.schlagBeginnMs = 0L
        /* SOFORT MELDEN (47): Ein Abbruch ist derselbe Zustandswechsel wie ein
           Ende — ohne Meldung stuende das Band am Handy weiter, obwohl auf der
           Uhr nichts mehr laeuft. Ein Zustand, den nur eine Seite kennt, ist
           schlimmer als gar keiner: Man verlaesst sich auf eine Anzeige, die
           nicht mehr stimmt. */
        syncNow()
    }

    /* Der eigentliche Abschluss, aufgerufen mit dem GEMITTELTEN Endpunkt.
       Entspricht playRecStop der PWA: Startpunkt wiederverwenden, wenn er
       <12 m vom letzten Punkt entfernt liegt, danach Endpunkt anhaengen
       (Kette). Steht VOR recStop, weil Kotlin lokale Funktionen nur nach
       ihrer Deklaration sichtbar macht. */
    fun recFinish(r: Rec, f: Fix) {

        val cs = course ?: return
        val hd = cs.holes.getOrNull(idx) ?: return

        val startP = r.start ?: return

        val endP = f.ll()

        val club = r.club ?: ""
        val cur = entries[hd.hole] ?: HoleEntry()
        val arr = ArrayList(cur.shots)

        /* `last()` auf einer leeren Liste wirft. Die Reihenfolge der
           Bedingungen faengt das zwar ab (isEmpty zuerst), aber der zweite
           Zweig lief bisher ohne eigene Pruefung — und Kotlin wertet dort
           erneut `arr.last()` aus. Mit `lastOrNull` ist beides gleichgueltig
           (2026-08-16 (11)). */
        val letzter = arr.lastOrNull()
        if (letzter == null ||
            Geo.dist(LL(letzter.lat, letzter.lng), startP) > 12
        ) {
            arr.add(ShotPt(startP.lat, startP.lng, club))
        } else if (club.isNotEmpty()) {
            arr[arr.size - 1] = letzter.copy(club = club)
        }

        arr.add(ShotPt(endP.lat, endP.lng, ""))

        val len = Geo.dist(startP, endP).roundToInt()

        change(hd.hole) { it.copy(shots = arr) }

        // zusätzlich als Messung für die Schlägerlängen-DB der PWA (gpsShots)
        if (club.isNotEmpty() && len >= 5) {
            measurements.add(
                JSONObject()
                    .put("id", "W" + System.currentTimeMillis())
                    .put("ts", isoNow())
                    .put("club", club)
                    .put("dist", len)
                    // Nur setzen, wenn es KEIN voller Schwung war — sonst
                    // bleibt das Feld weg und gilt als „Voll".
                    .apply { r.swing?.let { put("swing", it) } }
                    /* accA ist jetzt wirklich der Startpunkt (frueher eine
                       Kopie von accB). Und NICHT mehr auf Ganzzahl gerundet:
                       bei einer 1/acc²-Gewichtung macht der Unterschied
                       zwischen 3,0 und 3,4 m bereits ~28 % Gewicht aus. */
                    .put("accA", round1(r.startAcc ?: f.acc))
                    .put("accB", round1(f.acc))
                    .put("latA", round6(startP.lat))
                    .put("lngA", round6(startP.lng))
                    .put("latB", round6(endP.lat))
                    .put("lngB", round6(endP.lng))
                    .put("hole", hd.hole)
            )
        }

        rec = null
        status = "Schlag $len m" + (if (club.isNotEmpty()) " · $club" else "") +
                (r.swing?.let { " ($it)" } ?: "")
        /* Haptisch bestaetigen: beim Ball schaut man nicht auf die Uhr.
           DOPPELSCHLAG (46) — unterscheidbar vom langen Stups des Starts. */
        buzzEnde(ctx)
        Diagnose.schlag("Schlag erfasst", "$len m" + (if (club.isNotEmpty()) " · $club" else ""))
        Diagnose.schlagBeginnMs = 0L
        persist()
        /* SOFORT MELDEN (47) — wie der Start. Das Handy blendet sein Band erst
           aus, wenn es die Nachricht hat; bis dahin steht dort „Uhr trackt",
           obwohl der Schlag laengst im Kasten ist. Und die Messung selbst soll
           frueh genug ankommen, dass `schlagNeutral` sie mit dem Wetter DIESES
           Schlags rechnet (3-h-Sperre). */
        syncNow()
    }

    fun recStop() {

        /* AUCH HIER KEIN STUMMER RUECKSPRUNG (2026-08-28 (50)). Derselbe Fall
           wie in `recBegin`: Wer den Stopp tippt, waehrend das Sammelfenster
           laeuft, bekam gar nichts — und tippte weiter. Der Waechter greift
           ebenso, damit ein verklemmtes `measuring` nicht auch noch den Stopp
           blockiert und die laufende Messung unbeendbar macht. */
        if (measuring) {
            val her = System.currentTimeMillis() - measuringSeit
            if (measuringSeit > 0L && her > 20_000L) {
                Diagnose.schlag("Messung war verklemmt", "${her / 1000} s — freigegeben")
                measuring = false
            } else {
                status = "messe gerade…"
                buzzNein(ctx)
                Diagnose.schlag("Stopp ins Leere", "Messung läuft bereits")
                return
            }
        }

        val r = rec ?: return

        if (r.start == null) {
            status = "kein Startpunkt"
            buzzNein(ctx)
            return
        }

        // Siehe recBegin: eine Ablehnung ohne Vibration merkt man beim Ball nicht.
        if (!FixQuality.usable(Live.fix)) {
            status =
                if (Live.fix == null) "warte auf GPS…"
                else "GPS zu ungenau (${Live.fix?.acc?.roundToInt()} m)"
            buzzNein(ctx)
            return
        }

        measuring = true
        status = "messe…"

        scope.launch {
            /* SICHERHEITSNETZ (2026-08-16 (12)): Eine Ausnahme in einer
               Nebenlaeufigkeit landet beim globalen Auffang und beendet die
               GANZE App — nicht nur diesen Ablauf. Auf der Bahn ist das der
               schlimmste Fall. Notiert und weiterleben statt abstuerzen. */
            try {
            val f = FixQuality.collect { n -> status = "messe… ($n)" }
            if (f == null) {
                /* Die Aufnahme bleibt STEHEN — `rec` wird nicht geloescht.
                   Wer beim Ball steht und keinen brauchbaren Fix hat, soll in
                   ein paar Schritten erneut tippen koennen, statt den ganzen
                   Schlag zu verlieren. */
                status = "GPS zu ungenau — Schlag nicht erfasst"
                buzzNein(ctx)
                Diagnose.schlag("Stopp abgelehnt", "Sammelfenster ohne brauchbaren Fix")
                return@launch
            }
            recFinish(r, f)
        
            } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("Uhr-Eingabe", e)
            } finally {
                /* WIE IN `recBegin` (50): Freigabe NUR hier. Vorher sass
                   `measuring = false` mitten im Ablauf, hinter
                   `FixQuality.collect` — ein Abbruch der Nebenlaeufigkeit
                   liess die Sperre stehen, und danach war auch der STOPP
                   blockiert: eine laufende Messung, die sich nicht mehr
                   beenden liess. */
                measuring = false
                measuringSeit = 0L
            }
        }
    }

    /* HIER, hinter `recStop`: Lokale Funktionen sind in Kotlin erst NACH ihrer
       Deklaration sichtbar — `recFinish` steht weiter unten, und weiter oben
       kennt es niemand. */
    /* `recBeginAuto` und `recStopAuto` entfernt (40) — die beiden Eingaenge der
       Schwungerkennung. Sie nahmen die Fixes VOR dem Treffer aus dem Verlauf,
       statt danach zu sammeln; das war richtig gedacht und ist mit der
       Automatik selbst gegenstandslos geworden. `FixQuality.ausVerlauf()`
       bleibt: Sie liefert weiterhin den gemittelten Punkt. */

    fun recUndo() {
        val cs = course ?: return
        val hd = cs.holes.getOrNull(idx) ?: return
        val cur = entries[hd.hole] ?: return
        if (cur.shots.isEmpty()) return
        change(hd.hole) {
            it.copy(shots = it.shots.dropLast(1))
        }
        status = "letzter Punkt gelöscht"
    }

    /* HIER und nicht weiter oben: Lokale Funktionen sind in Kotlin erst NACH
       ihrer Deklaration sichtbar. Der Block stand zuerst bei den uebrigen
       LaunchedEffects — dort kennt niemand `recBegin`, `recStop` und
       `recClub`, und der Bau brach mit „Unresolved reference" ab. */
    /* ==========================================================================
       SCHLAG AUTOMATISCH ERFASSEN (2026-08-15)
       --------------------------------------------------------------------------
       Beim erkannten Treffmoment steht man AM BALL. Dieser Punkt ist zweierlei:
       das ENDE des vorigen Schlags und der ANFANG des neuen. Genau das macht die
       bestehende Kette schon — deshalb ruft die Automatik dieselben Funktionen
       wie die Hand: laeuft eine Aufnahme, wird sie hier beendet und sofort die
       naechste begonnen; laeuft keine, beginnt eine.
       KEIN eigener Datenweg, keine zweite Wahrheit: `recStop`/`recBegin` messen
       weiterhin ueber `FixQuality.collect` (rund 3 s gemittelt) und schreiben
       ueber `recFinish` in `shots` und `gpsShots`. Das ist auch der Grund, warum
       die Automatik nichts kaputtmachen kann, was die Hand nicht auch koennte —
       und warum „↶ letzten Schlag" unveraendert zurueckholt.
       SCHLAEGER: Vorbelegt mit dem, was der Caddy gerade empfiehlt. Das ist in
       der Mehrzahl der Faelle der gespielte, und ohne Schlaeger waere die
       Messung fuer die gelernten Laengen wertlos. Falsch vorbelegt ist besser
       als leer: Korrigieren kostet einen Tipp, Nachtragen kostet die Erinnerung.
       WAEHREND EINER LAUFENDEN MESSUNG passiert nichts — `measuring` sperrt.
       Sonst wuerde der Bodenkontakt des Divots die eigene Aufnahme abbrechen. */
    /* ==========================================================================
       AUTOMATISCHE SCHLAGERFASSUNG AUSGEBAUT (2026-08-26 (35))
       --------------------------------------------------------------------------
       Auf Wunsch vom 26.08. entfernt. Hier stand `LaunchedEffect(screen,
       autoShot)` mit `Swing.start { ... }`: Bewegungserkennung, die bei jedem
       erkannten Schwung eine Messung begann und einen Eintrag erzeugte.
       Nebenwirkung, die mit wegfaellt: Jeder automatische Eintrag stempelte
       `lastEditMs` — und liess damit (bis Fassung 35) die Beenden-Marke des
       Handys veralten; die Automatik hat also auch beim „Runde beenden geht
       nicht" mitgespielt. `object Swing`, `recBeginAuto`, `recStopAuto` bleiben
       sind mit (40) geloescht; der Schwung-Chip in der Aufnahmezeile
       (`recSwing`) bleibt — von Hand taggen geht weiter. */

    // ---------------- UI ----------------

    // List-States GEHOISTET (nicht mehr in den Screens selbst):
    // -> Scroll-Position des Loch-Screens bleibt erhalten, wenn ein Picker
    //    aufgeht (vorher sprang die Liste nach jeder Auswahl an den Anfang),
    // -> der PositionIndicator im Scaffold kann den aktiven State anzeigen,
    // -> Drehkrone/Lünette scrollen über rotaryScrollModifier().
    val homeListState = rememberScalingLazyListState()
    val playListState = rememberScalingLazyListState()
    val pickerListState = rememberScalingLazyListState()

    // Score-Seite und Detail-Seite scrollen unabhängig voneinander.
    val scoreListState = rememberScalingLazyListState()

    /* ZWEI SEITEN STATT DREI (2026-08-26 (38)). Seite 0 = Score (mit der
       Schlagzeile fest am unteren Rand), Seite 1 = Details. Die alte
       Loch-/Distanzseite ist entfallen: Sie zeigte ausschliesslich Ergebnisse
       von Rechnungen (F/M/B, „spielt wie", Caddy, Wetter), und die gehoeren
       ab dieser Fassung ausnahmslos aufs Handy.
       Der Zustand liegt HIER und nicht in PlayScreen, damit das Öffnen eines
       Pickers (der die Composition verlässt) die Seite nicht zurücksetzt. */
    val pagerState = rememberPagerState(initialPage = 0) { 2 }

    // ---- Zurück: eine Ebene nach oben, statt die App zu schließen ----
    // (activity ist weiter oben in dieser Funktion bereits deklariert)
    /* `lastBackAt` entfaellt (46) — es zaehlte die zwei Wischer, mit denen man
       eine laufende Runde verlassen konnte. Das gibt es nicht mehr. */

    BackHandler {
        when {
            // 1. Picker abbrechen, ohne den Wert anzufassen
            picker != null -> picker = null

            // 2. Auf einer Nebenseite: zurück zur Score-Seite
            screen == "play" && pagerState.currentPage != 0 ->
                scope.launch { pagerState.animateScrollToPage(0) }

            /* ==================================================================
               EINE LAUFENDE RUNDE LAESST SICH NICHT WEGWISCHEN (46)
               --------------------------------------------------------------------
               VORGABE VOM 27.08.: „Wenn ich auf Seite 1 oder Seite 2 nach links
               wische, lande ich im Startbildschirm. Das darf nicht passieren.
               Der soll nur beim Start gezeigt werden. Wenn die Runde laeuft,
               will ich da nicht hin."
               BIS (45) fuehrten zwei Wischer binnen zwei Sekunden zum
               Startbildschirm. Gedacht war das als Schutz — auf einem runden
               Display ist die Wischgeste aber die haeufigste Fehlbedienung
               ueberhaupt: Sie liegt genau dort, wo man die Seite wechselt.
               Zwei davon hintereinander passieren beilaeufig, und der
               „Nochmal fuer Uebersicht"-Hinweis steht in einer Statuszeile,
               die man beim Gehen nicht liest.
               ES GIBT NICHTS ZU HOLEN: Seit (44) kann man auf dem
               Startbildschirm waehrend einer Runde ohnehin nichts tun, was die
               Runde betrifft — Anlegen, Abschliessen und Verwerfen passieren
               am Handy. Der Weg dorthin fuehrte also aus Versehen an einen
               Ort ohne Zweck.
               DER RUECKWEG BLEIBT, ER KOMMT NUR VOM HANDY: Beendet oder
               verwirft man dort, raeumt die Uhr auf und steht von selbst
               wieder auf dem Startbildschirm (siehe „Runde ⇐ Handy beendet").
               Das ist der einzige Weg, und er ist der richtige.
               WAS DAS KOSTET, ausdruecklich: Solange eine Runde laeuft, kommt
               man mit der Wischgeste nicht mehr aus der App. Wer sie wirklich
               verlassen will, nimmt die Systemgeste. Faellt das Handy aus,
               laeuft die Runde auf der Uhr weiter und wird beim naechsten
               Abgleich beendet — verloren geht dabei nichts, alles liegt im
               Entwurf. */
            screen == "play" -> {
                status = "Runde läuft · Ende am Handy"
            }

            // 4. Platzauswahl und Gameplan -> Startbildschirm

            // 5. Startbildschirm -> App schließen
            else -> activity?.finish()
        }
    }

    /* Bei Lochwechsel nach oben springen und zurueck auf die Score-Seite.
       Frueher hiess „neues Loch" erst mal schauen — es gibt hier nichts mehr
       zu schauen, aber der Sprung nach oben bleibt richtig: Oben steht die
       Kopfzeile mit Loch, Par und den Lochpfeilen. */
    LaunchedEffect(idx) {
        playListState.scrollToItem(0)
        scoreListState.scrollToItem(0)
        if (pagerState.currentPage != 0) pagerState.animateScrollToPage(0)
    }

    // Beim Betreten einer Seite an den Anfang springen. Ohne das behält die
    // Liste die Position vom letzten Besuch — man wischt auf Seite 2 und
    // steht mitten drin, die obersten Zeilen scheinen zu fehlen.
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            1 -> scoreListState.scrollToItem(0)
            2 -> playListState.scrollToItem(0)
        }
    }

    // Kurs-Liste und Picker starten immer oben
    LaunchedEffect(screen) {
    }
    LaunchedEffect(picker) {
        if (picker != null) pickerListState.scrollToItem(0)
    }

    Scaffold(
        timeText = {
            TimeText()
        },
        vignette = {
            Vignette(
                vignettePosition =
                    VignettePosition.TopAndBottom
            )
        },
        positionIndicator = {
            PositionIndicator(
                scalingLazyListState = when {
                    picker != null -> pickerListState
                    screen == "play" && pagerState.currentPage == 1 -> scoreListState
                    screen == "play" -> playListState
                    else -> homeListState
                }
            )
        }
    ) {

        /* Auch hier: erst in eine lokale Groesse, dann pruefen (2026-08-16 (11)).
           `picker` ist veraenderlicher Zustand; wird er zwischen Pruefung und
           `!!` geleert — etwa weil die Zurueck-Geste den Waehler schliesst —
           endet die App. Seltener als beim Schlagtracking, aber dasselbe
           Muster, und die Behebung kostet eine Zeile. */
        val req = picker
        if (req != null) {

            PickerScreen(
                req.title,
                req.options,
                req.current,
                pickerListState,
                onCancel = { picker = null }
            ) { sel ->
                req.onSelect(sel)
                picker = null
            }

            return@Scaffold
        }

        when (screen) {

            /* ==========================================================================
               GAMEPLAN AUF DER UHR (2026-08-16)
               --------------------------------------------------------------------------
               Der Gameplan wird auf dem HANDY gerechnet (STRAT/EV-Engine) und
               reist als `plans` mit — Schlaeger und Ziel je Loch. Die Uhr hat
               ihn damit laengst dabei, konnte ihn aber nur WAEHREND einer Runde
               zeigen. Zum Nachsehen am Vorabend oder auf dem Weg zum Platz
               musste man eine Runde starten.
               Dieser Bildschirm zeigt ihn ohne Runde, fuer jeden Platz mit
               Plan. GERECHNET WIRD HIER NICHTS — es ist eine Anzeige dessen,
               was das Handy geplant hat; zwei Rechenwege waeren zwei
               Wahrheiten. */
            "home" -> HomeScreen(
                listState = homeListState,
                hasResume = resume != null,

                resumeLabel =
                    resume?.let {
                        "${it.course.name} · ${
                            it.entries.values.count { e ->
                                e.score != null
                            }
                        } Löcher"
                    } ?: "",

                /* Was liegt beim Handy? `data.draft` traegt den laufenden
                   Entwurf — seit 2026-08-15 (4) auch aus `draft.json`. */
                phoneRunde = data?.draft?.let { d ->
                    val n = (0 until d.holes.length()).count { i ->
                        d.holes.optJSONObject(i)?.has("score") == true
                    }
                    d.course + (if (n > 0) " · $n Löcher" else " · noch keine Löcher")
                },
                datenAlter = data?.let { syncAlter().first.takeIf { a -> a != "—" } },

                loading = loading,
                status = status,
                keepScreen = keepPref,
                gpsSource = gpsSource,
                awaitingPhone = awaitingPhone,

                turnier = turnier,
                turnierName = data?.draft?.mitspieler?.firstOrNull(),
                onTurnier = { turnier = !turnier },

                // ---- Weg 1: Runde vom Handy übernehmen (der Normalfall) ----
                // Sucht AUSSCHLIESSLICH nach Entwürfen, die das Handy angelegt
                // hat (roundId oder live.src == "phone"). Ein eigener Entwurf
                // der Uhr zählt hier nicht — genau das hat vorher jeden
                // Rundenstart abgefangen und still in eine Altrunde geführt.
                onFetchPhone = {

                    awaitingPhone = true
                    loading = true
                    status = "suche Runde vom Handy…"
                    askPerms()

                    scope.launch {
                      /* ==========================================================
                         ABSICHERUNG (2026-08-16 (8))
                         ----------------------------------------------------------
                         Eine Ausnahme INNERHALB von `launch` beendet die ganze App —
                         sie landet beim globalen Auffang, nicht in einem
                         catch-Zweig. Genau das ist beim Suchen einer Handy-Runde
                         aufgetreten: Netzfehler, unerwartete Antwort oder ein
                         Platz ohne Loecher reichen aus.
                         Jetzt wird die Stoerung notiert und angezeigt, statt die
                         App mitzureissen. Der Wartezustand MUSS dabei aufgeloest
                         werden — sonst steht „suche…" fuer immer da. */
                      try {

                        var done = false
                        var lastNote = ""
                        val until = System.currentTimeMillis() + 120_000L

                        while (!done && System.currentTimeMillis() < until) {

                            val (fetched, fresh) =
                                withContext(Dispatchers.IO) { loadData(ctx) }

                            if (fetched != null) {
                                data = fetched
                                dataFresh = fresh
                                hi = fetched.hi
                                if (fetched.clubs.isNotEmpty()) clubs = fetched.clubs
                            }

                            val d = fetched ?: data
                            val dr = d?.draft

                            /* ==================================================
                               DIESELBE RUNDE NICHT ZWEIMAL UEBERNEHMEN
                               (2026-08-25 (14)) — DIE URSACHE AUS DEM PROTOKOLL
                               ----------------------------------------------------
                               GEMELDET: „Bei Loch 1 geht die Eingabe, beim Wechsel
                               auf Loch 2 bricht alles ab."
                               IM PROTOKOLL steht „Runde uebernommen · Loch 1"
                               MEHRFACH — 08:06:32, 08:07:34, davor 07:30:16 und
                               07:32:03 (×2). Die Uhr uebernimmt also dieselbe Runde
                               immer wieder.
                               UND DAS IST FATAL: Der Zweig macht `entries.clear()`,
                               `measurements.clear()` und setzt `idx` auf das Loch des
                               HANDYS. Wer auf der Uhr zu Loch 2 wechselt und etwas
                               eintraegt, verliert beim naechsten Durchlauf beides —
                               Eingabe weg, Loch zurueck auf 1. Von aussen sieht das
                               aus, als sei der Abgleich tot.
                               DER RIEGEL: Laeuft bereits DIESELBE Runde (gleiche
                               `roundId` oder gleicher Platz+Datum+Seite) und ist ein
                               Platz geladen, wird nicht erneut uebernommen. Der
                               laufende Abgleich haelt sie danach ohnehin aktuell —
                               dafuer ist er da. */
                            /* ==================================================
                               DER RIEGEL DARF NICHT AUSSPERREN (2026-08-25 (21))
                               ----------------------------------------------------
                               GEMELDET: „Aus der laufenden Runde geflogen und
                               konnte mich nicht mehr verbinden." Auf dem Schirm
                               stand „Nordplatz · NOCH KEINE LOECHER", darunter
                               die endlose Suche.
                               URSACHE ist mein eigener Riegel aus (14). Er
                               verhindert, dass dieselbe Runde zweimal uebernommen
                               wird — richtig, denn die Uebernahme LOESCHT
                               Eingaben. Er prueft aber nur, ob ein Platz mit
                               passendem NAMEN geladen ist. Nach einem Neustart
                               ist genau das der Fall: Der Platz ist da, seine
                               Lochliste aber LEER. Der Riegel sprang an, die
                               Uebernahme unterblieb — und der Benutzer sass auf
                               dem Startbildschirm fest, mit einem Platz ohne
                               Loecher.
                               DREI BEDINGUNGEN STATT EINER: Es gilt nur als
                               „laeuft bereits", wenn (1) derselbe Platz geladen
                               ist, (2) er tatsaechlich LOECHER hat und (3) man
                               auch im Spielbildschirm ist. Fehlt eines davon,
                               laeuft eben NICHT dieselbe Runde, sondern eine
                               halbe — und die gehoert vervollstaendigt.
                               ZUM MITNEHMEN: Ein Riegel gegen Datenverlust darf
                               nie die einzige Tuer versperren. Er braucht immer
                               die Frage „und was, wenn der Zustand kaputt ist?" */
                            val schonDa = dr != null && course != null &&
                                (course?.holes?.isNotEmpty() == true) &&
                                screen == "play" && (
                                (dr.roundId != null && dr.roundId == roundId) ||
                                (dr.course == course?.name && dr.side == side)
                            )
                            if (schonDa) {
                                /* Nichts tun — aber EINMAL sagen, dass es
                                   uebersprungen wurde. Ohne diese Spur sucht man
                                   beim naechsten Mal wieder an der falschen Stelle. */
                                Fehler.warnEinmal("adoptSkip", "Runde",
                                    "laeuft bereits (Platz mit ${course?.holes?.size ?: 0} Loechern, "
                                        + "Bildschirm $screen) — Uebernahme uebersprungen")
                                done = true
                                loading = false
                                awaitingPhone = false
                            } else if (d != null && dr != null &&
                                dr.fromPhone && dr.date == today()
                            ) {

                                // Genauer Tee zuerst, sonst irgendein Tee des Platzes
                                val dc =
                                    d.courses.firstOrNull {
                                        it.name == dr.course &&
                                                dr.tee != null && it.tee == dr.tee
                                    }
                                        ?: d.courses.firstOrNull { it.name == dr.course }

                                if (dc == null) {

                                    // Runde gefunden, Platz aber unbekannt. Ohne diese
                                    // Unterscheidung sähe es aus wie "nichts gefunden".
                                    done = true
                                    loading = false
                                    awaitingPhone = false
                                    status = "Platz \"${dr.course}\" nicht in den Uhr-Daten"

                                } else {

                                    done = true

                                    // Umfang des Handys mitziehen — sonst zeigt die
                                    // Uhr 18 Löcher für eine 9-Loch-Runde.
                                    side = dr.side
                                    course = when (side) {
                                        "Front 9" ->
                                            dc.copy(holes = dc.holes.filter { it.hole <= 9 })
                                        "Back 9" ->
                                            dc.copy(holes = dc.holes.filter { it.hole >= 10 })
                                        else -> dc
                                    }.let { if (it.holes.isEmpty()) dc else it }

                                    tee = dr.tee ?: dc.tee
                                    roundId = dr.roundId

                                    /* HIER STAND EINE `}` ZU VIEL — sie schloss den
                                       else-Zweig zu frueh. Die folgenden Zeilen
                                       benutzen `dc` (dc.holes, dc.name); das geht nur
                                       INNERHALB dieses Zweigs, wo dc nicht null ist.
                                       Folge der Fehlstellung: alles danach rutschte
                                       eine Ebene heraus, und `onCancelFetch` samt vier
                                       weiteren Argumenten landete AUSSERHALB des
                                       HomeScreen-Aufrufs — der Compiler meldete
                                       „No value passed for parameter 'onCancelFetch'"
                                       an einer Stelle 150 Zeilen weiter oben. */

                                    entries.clear()
                                    measurements.clear()
                                    adoptHoles(dr.holes)
                                    /* Was genau kam an? Fehlen spaeter Werte,
                                       will man wissen, ob sie ueberhaupt
                                       uebernommen wurden (2026-08-16 (14)). */
                                    /* Die Warnung „ohne Karte" ist mit (40)
                                       entfallen — es gibt keine Karte mehr und
                                       nichts, was sie vermissen wuerde.
                                       Die Schlaeger-Warnung bleibt: Ohne sie
                                       hat die Aufnahmezeile keine Liste, und
                                       eine Messung ohne Schlaeger ist fuer die
                                       gelernten Laengen wertlos. */
                                    if (clubs.isEmpty())
                                        Fehler.warn("Schläger", "Keine Schläger auf der Uhr — die Aufnahme kann keinen zuordnen")
                                    Fehler.add("Runde übernommen",
                                        "${dc.name} · ${dr.side} · ${dr.holes.length()} Löcher · Loch ${dr.liveHole ?: "?"}")

                                    // Loch des Handys übernehmen, sonst erstes ohne Score
                                    // WICHTIG: gegen die GEFILTERTE Lochliste suchen,
                                    // sonst zeigt eine Back-9-Runde auf den falschen Index.
                                    val hs = course?.holes ?: dc.holes

                                    val fromLive = dr.liveHole
                                        ?.let { h -> hs.indexOfFirst { it.hole == h } }
                                        ?.takeIf { it >= 0 }

                                    idx = fromLive
                                        ?: hs
                                            .indexOfFirst { entries[it.hole]?.score == null }
                                            .let { if (it < 0) 0 else it }
                                    /* (34): Zaehlerstand der Runde uebernehmen,
                                       sonst begaenne die Uhr nach Neustart bei 0
                                       und ihre erste Handlung verloere gegen
                                       jeden alten Zeiger. */
                                    dr.liveHoleSeq?.let { Net.holeSeqGesehen(it) }

                                    roundStart = System.currentTimeMillis()
                                    clearLocal(ctx)
                                    resume = null

                                    loading = false
                                    awaitingPhone = false
                                    status = "▶ ${dc.name} · $tee · Loch " +
                                            (course?.holes?.getOrNull(idx)?.hole ?: 1)
                                    screen = "play"

                                }

                            } else {

                                // Sichtbar machen, woran es gerade liegt — sonst
                                // steht man zwei Minuten vor "suche…" ohne Hinweis.
                                lastNote = when {
                                    d == null ->
                                        lastLoadError ?: "kein Netz / keine Daten"
                                    dr == null -> "noch keine Runde im Repo"
                                    !dr.fromPhone -> "nur eigener Entwurf der Uhr"
                                    else -> "Entwurf ist von ${dr.date}"
                                }
                                status = "suche… ($lastNote)"

                                // Kurz getaktet warten: am Abschlag will man nicht
                                // 60 s stehen, aber auch nicht dauerfunken.
                                delay(10_000)
                            }
                        }

                        if (!done) {
                            loading = false
                            awaitingPhone = false
                            status = "Keine Handy-Runde — $lastNote"
                        }

                      } catch (e: Exception) {
                        Fehler.add("Handy-Runde", e)
                        loading = false
                        awaitingPhone = false
                        status = "Fehler bei der Suche — siehe Protokoll (Seite 3)"
                      }
                    }
                },

                onCancelFetch = {
                    awaitingPhone = false
                    loading = false
                    status = ""
                },

                /* ERST DEN DIENST STOPPEN, DANN SCHLIESSEN (2026-08-25 (20)).
                   Ohne `svcStop` laeuft der Vordergrunddienst weiter und haelt
                   die Ortung am Leben — die App ist zu, das GPS nicht. Genau
                   das ist am 15.08. schon einmal passiert. */
                onQuit = {
                    svcStop(ctx)
                    activity?.finish()
                },

                /* ==========================================================
                   KEIN ALLEINSTART MEHR (2026-08-27 (44))
                   ------------------------------------------------------------
                   VORGABE VOM 27.08.: „Eine Runde nur mit Uhr und ohne Handy
                   gibt es nicht."
                   Hier stand `onNew` — Platzauswahl auf der Uhr, Rundenumfang
                   waehlen, Runde beginnen. Damit gab es ZWEI Orte, an denen
                   eine Runde entstehen konnte, und zwei Orte heissen zwei
                   Zustaende, die auseinanderlaufen. Genau daran hingen die
                   Mitspieler-Meldung vom 27.08. und die Frage, wer bei
                   Rundenumfang und Namen fuehrt.
                   ES BLEIBEN ZWEI WEGE, und beide beginnen am Handy:
                     · „Runde vom Handy holen" (`onFetchPhone`) — der Normalfall,
                     · „Fortsetzen" (`onResume`) — eine bereits uebernommene
                       Runde nach App-Neustart oder Akkuwechsel.
                   `side`/`onSide` (Rundenumfang) faellt mit: Der Umfang steht
                   in der Runde des Handys und wird uebernommen. */

                onResume = {

                    resume?.let {

                        course = it.course
                        tee = it.tee
                        hi = it.hi
                        clubs = it.clubs
                        roundId = it.roundId
                        side = it.side

                        measurements.clear()
                        measurements.addAll(it.measurements)


                        entries.clear()
                        entries.putAll(it.entries)

                        idx = 0
                        roundStart =
                            it.roundStart
                                ?: System.currentTimeMillis()

                        // Der Spielbildschirm ist SOFORT da (screen = "play" unten).
                        // Frische Daten kommen im Hintergrund nach — vorher
                        // hing das Fortsetzen an einem Netz-Timeout.
                        if (data == null || !dataFresh) {

                            scope.launch {
                                /* SICHERHEITSNETZ (2026-08-16 (12)): Eine Ausnahme in einer
                                   Nebenlaeufigkeit landet beim globalen Auffang und beendet die
                                   GANZE App — nicht nur diesen Ablauf. Auf der Bahn ist das der
                                   schlimmste Fall. Notiert und weiterleben statt abstuerzen. */
                                try {

                                val (d, fresh) =
                                    withContext(Dispatchers.IO) { loadData(ctx) }

                                dataFresh = fresh

                                if (d != null) {
                                    data = d
                                    if (d.clubs.isNotEmpty()) clubs = d.clubs
                                }
                            
                                } catch (e: Exception) {
            /* ABBRUCH DURCHLASSEN (2026-08-24 (10)): Wer ihn faengt und nicht
               weiterwirft, sagt dem System „ich mache weiter" — waehrend Compose
               die Schleife fuer beendet haelt. Zwei Schleifen, die dasselbe
               schreiben, sind genau der Zustand, in dem „einmal geht es, dann
               nicht mehr" entsteht. */
            if (e.istAbbruch()) throw e
            Fehler.add("Verwerfen", e)
        }
                            }
                        }

                        screen = "play"
                    }
                },

                /* KEIN VERWERFEN AUF DER UHR (44). Es schrieb einen
                   Grabstein ins Repo und raeumte den lokalen Stand — die
                   zerstoerendste Handlung der App, ausgeloest mit zwei Tippern
                   an einem Handgelenk. Verwerfen passiert am Handy, wo man
                   sieht, was man wegwirft. */

                onKeepScreen = { v ->
                    prefSetB(ctx, "keepScreen", v)
                    keepPref = v
                    status = "Display-Einstellung übernommen"
                },

                onGpsSource = { v ->
                    prefSet(ctx, "gpsSource", v)
                    gpsSource = v
                    svcGpsRestart(ctx)   // läuft eine Runde, wechselt sie sofort
                    status =
                        if (v == "phone") "GPS: Handy (via Bluetooth)"
                        else "GPS: Uhr"
                }
            )

            /* KEIN BILDSCHIRM „pick" MEHR (2026-08-27 (44)) — Platzauswahl und
               Rundenumfang auf der Uhr sind entfallen. Beides kommt mit der
               Runde des Handys (`dr.side`, `dr.course`, `dr.tee`); `side`
               bleibt als Zustand erhalten, WIRD ABER NUR NOCH UEBERNOMMEN,
               nicht mehr gesetzt. */

            "play" -> {

                val cs = course

                LaunchedEffect(cs) {
                    if (cs == null) screen = "home"
                }

                if (cs != null) {

                    /* ==========================================================
                       DIREKTER INDEXZUGRIFF WAR EIN ABSTURZ (2026-08-16 (11))
                       ----------------------------------------------------------
                       `cs.holes[idx]` wirft, sobald `idx` groesser ist als die
                       Lochliste. Genau das passiert im Betrieb: Wird waehrend
                       einer laufenden 18er der Umfang auf Front 9 gestellt
                       (Uebernahme vom Handy, `side = dr.side`), schrumpft
                       `course.holes` auf neun — `idx` bleibt aber, wo es war.
                       Steht man dann auf Loch 13, ist der naechste Zeichenlauf
                       der letzte. Diese Zeile laeuft bei JEDER Neuzeichnung des
                       Spielbildschirms; ein Fehler hier beendet die App sofort
                       und ohne erkennbaren Zusammenhang.
                       Statt abzustuerzen: auf das letzte gueltige Loch
                       zurueckfallen und den Zeiger korrigieren. */
                    if (idx >= cs.holes.size || idx < 0) {
                        val neuIdx = (cs.holes.size - 1).coerceAtLeast(0)
                        Fehler.add("Lochzeiger", "idx $idx bei ${cs.holes.size} Löchern → $neuIdx")
                        idx = neuIdx
                    }
                    /* Kein vorzeitiges `return` aus einer Composable-Lambda —
                       das waere je nach umgebendem Block ein Uebersetzungs-
                       fehler und im besten Fall ein halb gezeichneter
                       Bildschirm. Nach dem Zurechtruecken oben kann `getOrNull`
                       nur noch null sein, wenn die Lochliste LEER ist; dann
                       traegt ein Ersatzloch die Anzeige, bis der Zustand
                       wieder stimmt. Sichtbar wird es ueber das Protokoll. */
                    /* Ort mitschreiben — jede Meldung traegt ihn dann bei
                       sich (2026-08-16 (13)). */
                    Fehler.kontext = "Loch ${cs.holes.getOrNull(idx)?.hole ?: "?"}/${cs.holes.size} · ${cs.name.take(18)}"
                    /* ==================================================================
                       DER RUECKFALL SCHRIEB STILL AUF LOCH 1 (2026-08-25 (31))
                       --------------------------------------------------------------------
                       GEMELDET: „Er erfasst immer nur die Eingaben bei Loch 1."
                       IN DER SPUR steht es: „Eingabe L1" bei jeder Zeile, obwohl
                       zwischendurch „Loch → 2" und „Loch → 3" protokolliert wurde.
                       URSACHE: Liefert `getOrNull(idx)` null — weil `idx` neben die
                       Lochliste zeigt —, faellt dieser Ausdruck auf
                       `cs.holes.firstOrNull()` zurueck, also auf LOCH 1. Und zwar
                       LAUTLOS. Alle Eintraege landeten dort, waehrend die Anzeige
                       weiterblaetterte.
                       Ein Rueckfall darf eine ANZEIGE retten. Er darf niemals still
                       entscheiden, WOHIN Daten geschrieben werden — das ist kein
                       Notbehelf mehr, das ist eine falsche Zuordnung.
                       JETZT WIRD ER GEMELDET, mit beiden Zahlen: Wer im Protokoll
                       „idx 4 neben 18 Loechern — Eingaben landen auf Loch 1" liest,
                       weiss in einer Zeile Bescheid. Warum `idx` ueberhaupt
                       danebenzeigt, ist die naechste Frage — aber sie ist ohne diese
                       Zeile nicht zu stellen. */
                    val hd = cs.holes.getOrNull(idx)
                        ?: cs.holes.firstOrNull()?.also { ers ->
                            Fehler.warnEinmal("hdRueckfall", "Lochzeiger",
                                "idx $idx neben ${cs.holes.size} Loechern — " +
                                "Eingaben landen auf Loch ${ers.hole}")
                        }
                        ?: HoleDef(1, 4).also { Fehler.add("Lochliste", "leer — Ersatzloch") }
                    val e =
                        entries[hd.hole]
                            ?: HoleEntry()

                    val opts = data?.opts
                    val (opNow, thruNow) = overPar()

                    /* `liveOf` rechnet Front/Mitte/Back ueber die Ringgeometrie
                       des Gruens — das gehoert nicht in jede Neuzeichnung.
                       Schluessel ist die BERUHIGTE Position (Live.fixUi),
                       zusaetzlich Loch und Platzkarte. Ohne `remember` lief es
                       auch dann, wenn nur ein Chip seine Farbe wechselte. */
                    /* `Live.err` MUSS als Schluessel dabeistehen: Es wird im
                       Rumpf von `liveOf` gelesen, und ein Lesen INNERHALB von
                       `remember` abonniert den Zustand nicht. Ohne den
                       Schluessel bliebe „GPS ist ausgeschaltet" unsichtbar,
                       solange sich die Position nicht aendert — also genau
                       dann, wenn es keine mehr gibt. */
                    /* `geo` faellt als Schluessel weg (40) — es gibt keine
                       Platzkarte mehr, auf die sich das Ergebnis stuetzen
                       koennte. Uebrig bleiben die Groessen, die `liveOf`
                       tatsaechlich liest. */
                    val live = remember(fix, hd.hole, Live.err) { liveOf(hd.hole) }

                    /* ==========================================================
                       WETTLAUF ZWISCHEN PRUEFUNG UND ZUGRIFF (2026-08-16 (11))
                       ----------------------------------------------------------
                       `if (rec?.start != null) … rec!!.start!!` sieht sicher
                       aus, ist es aber nicht: `rec` ist veraenderlicher
                       Zustand, und seit der automatischen Schlagerfassung setzt
                       ihn ein SENSOR-RUECKRUF — also ein anderer Faden. Faellt
                       die Erkennung genau zwischen Pruefung und Zugriff, ist
                       `rec` null und `!!` beendet die App. Das passiert selten,
                       aber waehrend jeder Runde staendig neu, und es sieht nach
                       einem zufaelligen Absturz aus.
                       Genau dafuer schreibt man den Wert in eine LOKALE Grosse:
                       Die aendert niemand mehr. */
                    val recJetzt = rec
                    val recStart = recJetzt?.start
                    val recDist =
                        if (recStart != null && fix != null) {
                            Geo.dist(recStart, fix.ll()).roundToInt()
                        } else {
                            null
                        }

                    /* ALWAYS-ON: im gedimmten Zustand den vollen Pager gar
                       nicht erst zusammenbauen. Spart Akku und vermeidet, dass
                       farbige Flächen minutenlang stehen. Die Weiche sitzt hier
                       und nicht um setContent, weil erst an dieser Stelle Loch,
                       Distanz und Stand bekannt sind. */
                    if (AmbientState.isAmbient) {
                        AmbientPlayScreen(
                            hole = hd.hole,
                            scoreLabel =
                                if (thruNow == 0) "±0"
                                else if (opNow == 0) "E ($thruNow)"
                                else if (opNow > 0) "+$opNow ($thruNow)"
                                else "$opNow ($thruNow)",
                            recActive = rec != null,
                            recDist = recDist,
                            hasFix = live.hasFix
                        )
                        return@Scaffold
                    }

                    PlayPager(
                        turnier = turnier,

                        pagerState = pagerState,
                        detailListState = playListState,
                        scoreListState = scoreListState,
                        course = cs,
                        hd = hd,
                        entry = e,
                        idx = idx,
                        total = cs.holes.size,
                        /* Grenzen HIER, wo `idx` veraenderlich ist und die
                           Lochliste bekannt: Ein `idx` ausserhalb beendet die
                           App beim naechsten Zeichnen (siehe „Lochzeiger"). */
                        onHoleDelta = { d ->
                            val n = idx + d
                            if (n >= 0 && n < cs.holes.size) {
                                idx = n
                                /* Die Eingabe stempeln, BEVOR der naechste Push
                                   laeuft — sonst ueberstimmt der Zeiger des
                                   Handys sie wieder (2026-08-24 (4)). */
                                Net.holeGewechselt()
                                Diagnose.aktion("Loch → " + (cs.holes.getOrNull(n)?.hole ?: "?"))
                                /* ==================================================
                                   UND SOFORT SENDEN (2026-08-24 (5))
                                   ----------------------------------------------------
                                   GEMESSEN: Das HANDY sendet bei einer Eingabe sofort
                                   (`playLivePush` ruft `draftPush()` direkt). Die UHR
                                   wartete auf ihren Takt — 10 Sekunden, auch direkt
                                   nach einem Lochwechsel. Daher die Schieflage:
                                   Handy -> Uhr im Mittel 2,5 s, Uhr -> Handy 7,5 s.
                                   Das war kein Funkproblem, sondern eine fehlende
                                   Regel: EINE HANDLUNG DES BENUTZERS SENDET SOFORT,
                                   sie wartet nicht auf den Takt. Dieselbe Regel, die
                                   beim Zeiger-Vorrang gefehlt hat, nur beim Senden.
                                   KEIN ZUSAETZLICHER FUNKVERKEHR: `lastEditMs` setzt
                                   den Takt ohnehin auf „frisch", der naechste regulaere
                                   Vorgang faellt dafuer weg. Und `scheduleSync` ist
                                   entprellt — wer dreimal blaettert, sendet einmal. */
                                lastEditMs = System.currentTimeMillis()
                                scheduleSync()
                            }
                        },
                        status = status,
                        opts = opts,
                        // ClubDist heisst das Feld `club`, NICHT `name`.
                        clubNames = clubs.map { it.club },
                        toPar = opNow,
                        thru = thruNow,

                        onHome = { screen = "home" },

                        gpsAcc = live.acc,
                        gpsErr = live.err,

                        recActive = rec != null,
                        recClubName = rec?.club,
                        recSwingName = rec?.swing,
                        /* KEIN SEKUNDENTAKT (2026-08-14 (4)) — bewusst.
                           Naheliegend waere ein Zaehler, der die Zahl „⟳12s"
                           jede Sekunde hochzaehlt. Er wuerde aber im
                           Hauptcomposable gelesen und damit sekuendlich den
                           halben Bildschirm neu zusammensetzen — genau das
                           Ruckeln, das der beruhigte GPS-Takt gerade beseitigt
                           hat. Das waere ein schlechter Tausch fuer eine Zahl,
                           die niemand sekundengenau braucht.
                           Der Wert erneuert sich ohnehin bei jedem Abgleich
                           (`lastSyncMs` ist Zustand), also alle 10-60 s — und
                           genau dann aendert er sich sprunghaft auf „0s".
                           Dazwischen zaehlt er nur hoch, und wofuer man ihn
                           liest — „ist der Abgleich haengengeblieben?" — ist
                           die Minutenskala das richtige Mass. */
                        syncAge = syncAlter().first.takeIf { it != "—" },
                        syncStale = syncAlter().second,
                        recDist = recDist,
                        shotCount = max(0, e.shots.size - 1),

                        /* Compose-State fuer die Namen (39/42). `Mitspieler` ist
                           ein Singleton mit @Volatile — davon erfaehrt die
                           Neuzeichnung nichts. Deshalb hier ein echter State,
                           der das Singleton nur SPIEGELT.
                           KEIN `onMitspielerN` MEHR: Die Uhr eroeffnet keine
                           Plaetze; das Handy fuehrt (42). */
                        mitspielerNamen = mitspielerNamen,

                        onScore = { d ->
                            change(hd.hole) {
                                it.copy(
                                    score =
                                        (
                                                it.score
                                                    ?: hd.par
                                                )
                                            .plus(d)
                                            .coerceIn(
                                                1,
                                                15
                                            ),
                                    // Putts-Standard 2 wird beim ersten Score fest
                                    putts = it.putts ?: 2
                                )
                            }
                        },

                        onPutts = { d ->
                            change(hd.hole) {
                                it.copy(
                                    putts =
                                        (
                                                it.putts
                                                    ?: 2
                                                )
                                            .plus(d)
                                            .coerceIn(
                                                0,
                                                10
                                            )
                                )
                            }
                        },

                        onPen = { d ->
                            change(hd.hole) {
                                it.copy(
                                    penN =
                                        (
                                                it.penN
                                                    ?: 0
                                                )
                                            .plus(d)
                                            .coerceIn(
                                                0,
                                                6
                                            )
                                )
                            }
                        },

                        onBunkerN = { d ->
                            change(hd.hole) {
                                it.copy(
                                    bunkerN =
                                        (
                                                it.bunkerN
                                                    ?: 0
                                                )
                                            .plus(d)
                                            .coerceIn(
                                                0,
                                                6
                                            )
                                )
                            }
                        },

                        onUd = {
                            change(hd.hole) {
                                it.copy(
                                    ud = cycle(it.ud)
                                )
                            }
                        },

                        onSs = {
                            change(hd.hole) {
                                it.copy(
                                    ss = cycle(it.ss)
                                )
                            }
                        },

                        onRec = {
                            change(hd.hole) {
                                it.copy(
                                    recovery =
                                        cycle(
                                            it.recovery
                                        )
                                )
                            }
                        },

                        onPick = {
                                title,
                                list,
                                cur,
                                set ->

                            picker =
                                PickerReq(
                                    title,
                                    list,
                                    cur
                                ) { sel ->

                                    change(hd.hole) {
                                        set(
                                            it,
                                            sel
                                        )
                                    }
                                }
                        },

                        onShotBegin = { recBegin() },

                        onShotClub = {
                            val list =
                                if (clubs.isNotEmpty()) {
                                    clubs.map { c -> c.club }
                                } else {
                                    opts?.teeClubs ?: emptyList()
                                }
                            picker =
                                PickerReq(
                                    "Schläger",
                                    list,
                                    rec?.club
                                ) { sel ->
                                    recClub(sel)
                                }
                        },

                        /* Ein Tipp schaltet die Schwunglaenge weiter:
                           Voll -> 3/4 -> Halb -> Punch -> Voll. Kein Menue —
                           man steht beim Ball und will weiterspielen. */
                        onShotSwing = {
                            val folge = listOf(null, "3/4", "Halb", "Punch")
                            val jetzt = folge.indexOf(rec?.swing)
                            recSwing(folge[(if (jetzt < 0) 0 else jetzt + 1) % folge.size])
                        },

                        onShotStop = { recStop() },
                        onShotCancel = { recCancel() },
                        onShotUndo = { recUndo() },

                        onPrev = {
                            if (idx > 0) {
                                /* OFFENE AUFNAHME ABSCHLIESSEN, NICHT WEGWERFEN
                                   (2026-08-15 (5)). Der letzte volle Schlag
                                   eines Lochs — meist die Annaeherung — wird von
                                   der Automatik BEGONNEN, aber nie beendet: Der
                                   naechste Treffer waere der Abschlag des
                                   naechsten Lochs, und dazwischen liegt der
                                   Lochwechsel. Frueher fiel dieser Schlag hier
                                   ersatzlos raus. Jetzt wird er an der aktuellen
                                   Position geschlossen — man steht beim
                                   Lochwechsel am Gruen, also genau dort, wo der
                                   Ball lag.
                                   REIHENFOLGE: erst schliessen, DANN das Loch
                                   wechseln — `recStop` schreibt den Schlag auf
                                   das Loch, das gerade aktiv ist. */
                                if (rec?.start != null) recStop() else rec = null
                                idx--
                                Diagnose.aktion("Loch ← " + (cs.holes.getOrNull(idx)?.hole ?: "?"))
                                /* ==================================================
                                   DIE EIGENE LOCHWAHL STEMPELN (2026-08-25 (19))
                                   ----------------------------------------------------
                                   GEMELDET: „Von Loch 1 bis 6 durchgeblaettert, nur
                                   der erste Score kam an."
                                   IM PULS: Kontext „Loch 2/18", gesendet aber
                                   „eigenes Loch 1". Das Blaettern hielt also nicht.
                                   URSACHE: `ownHoleAt` wurde NUR von den Pfeilen auf
                                   Seite 1 gesetzt (2026-08-24 (4)). Wer hier auf der
                                   Score-Seite blaettert, hinterliess keine Marke —
                                   und der naechste Abgleich sah einen Handy-Zeiger,
                                   der juenger war als die (leere) Marke, und holte
                                   das Loch des Handys zurueck. Sekunden spaeter stand
                                   man wieder auf Loch 1, und alle weiteren Scores
                                   landeten dort.
                                   JEDE Stelle, an der der BENUTZER das Loch waehlt,
                                   muss stempeln. Eine halbe Regel ist keine. */
                                Net.holeGewechselt()
                                /* Lochwechsel gilt als Eingabe: sonst bliebe der
                                   Pull-Takt im Sparbetrieb, und die Antwort des
                                   Handys kaeme erst eine halbe Minute spaeter. */
                                lastEditMs = System.currentTimeMillis()
                                syncNow()
                            }
                        },

                        onNext = {
                            if (
                                idx <
                                cs.holes.size - 1
                            ) {
                                // Offene Aufnahme abschliessen — siehe onPrev.
                                // Erst schliessen, DANN wechseln.
                                if (rec?.start != null) recStop() else rec = null
                                idx++
                                Diagnose.aktion("Loch → " + (cs.holes.getOrNull(idx)?.hole ?: "?"))
                                /* ==================================================
                                   DIE EIGENE LOCHWAHL STEMPELN (2026-08-25 (19))
                                   ----------------------------------------------------
                                   GEMELDET: „Von Loch 1 bis 6 durchgeblaettert, nur
                                   der erste Score kam an."
                                   IM PULS: Kontext „Loch 2/18", gesendet aber
                                   „eigenes Loch 1". Das Blaettern hielt also nicht.
                                   URSACHE: `ownHoleAt` wurde NUR von den Pfeilen auf
                                   Seite 1 gesetzt (2026-08-24 (4)). Wer hier auf der
                                   Score-Seite blaettert, hinterliess keine Marke —
                                   und der naechste Abgleich sah einen Handy-Zeiger,
                                   der juenger war als die (leere) Marke, und holte
                                   das Loch des Handys zurueck. Sekunden spaeter stand
                                   man wieder auf Loch 1, und alle weiteren Scores
                                   landeten dort.
                                   JEDE Stelle, an der der BENUTZER das Loch waehlt,
                                   muss stempeln. Eine halbe Regel ist keine. */
                                Net.holeGewechselt()
                                /* Lochwechsel gilt als Eingabe: sonst bliebe der
                                   Pull-Takt im Sparbetrieb, und die Antwort des
                                   Handys kaeme erst eine halbe Minute spaeter. */
                                lastEditMs = System.currentTimeMillis()
                                syncNow()
                            }
                        }
                    )
                }
            }
        }
    
}
}

// Endscore-Auswahl der Mitspieler (37): 1-12 deckt jedes realistische Loch.
private val MITSPIELER_SCORES = (1..12).map { it.toString() }

// Auswahlwerte für "Rest zur Fahne". Die PWA speichert eine freie ganze
// Zahl (numF in playField), deshalb sind das nur bequeme Stützstellen —
// jeder Wert bleibt ein sauberer Integer im selben Feld distToPin.
private val DIST_TO_PIN_CHOICES: List<String> =
    ((0..20).map { "$it m" } +
            listOf(25, 30, 35, 40, 50, 60, 80, 100).map { "$it m" })

private fun cycle(
    b: Boolean?
): Boolean? =
    when (b) {
        null -> true
        true -> false
        else -> null
    }

private fun jnLabel(
    b: Boolean?
) =
    when (b) {
        null -> "—"
        true -> "Ja"
        else -> "Nein"
    }

// ================= Screens =================

// Drehkrone/Lünette scrollt die aktive Liste. Der Fokus wird beim Anzeigen
// des Screens angefordert (LaunchedEffect läuft bei jedem Wieder-Eintritt in
// die Composition, z. B. nach dem Schließen eines Pickers).
@Composable
private fun rotaryScrollModifier(
    state: ScalingLazyListState,
    active: Boolean = true
): Modifier {

    val focus = remember {
        FocusRequester()
    }

    /* DREHKRONE — der wichtigste Ruckel-Fix (2026-08-12).

       Vorher startete JEDES Kronen-Ereignis ein eigenes
       `scope.launch { state.scrollBy(...) }`. Ein zuegiger Dreh erzeugt aber
       Dutzende Ereignisse pro Sekunde, und jeder scrollBy-Aufruf reisst das
       Scroll-Mutex der Liste an sich und BRICHT den vorherigen ab. Das Ergebnis
       war genau das beobachtete Haken: die Liste rueckt, stockt, rueckt.

       Jetzt sammelt ein Channel die Deltas, und EIN einziger Konsument scrollt.
       Was waehrend eines Frames anfaellt, wird vorher aufaddiert — statt
       zwanzig konkurrierender Mikro-Scrolls gibt es einen sauberen grossen. */
    val deltas = remember {
        Channel<Float>(Channel.UNLIMITED)
    }

    LaunchedEffect(state) {
        while (true) {
            var d = deltas.receive()
            // alles, was bereits anliegt, zu einem Scroll zusammenfassen
            while (true) {
                d += deltas.tryReceive().getOrNull() ?: break
            }
            state.scrollBy(d)
        }
    }

    // Im Pager sind mehrere Seiten gleichzeitig komponiert. Ohne die
    // active-Bedingung fordern alle den Fokus an und die Krone scrollt
    // eine Liste, die man gar nicht sieht.
    LaunchedEffect(active) {
        if (active) {
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                // FocusRequester noch nicht angeheftet — beim naechsten
                // Durchlauf klappt es. Frueher flog das als Absturz hoch.
            }
        }
    }

    /* remember: ohne das entstand bei JEDER Recomposition eine neue
       Modifier-Kette. Compose sieht dann einen geaenderten Modifier am
       ScalingLazyColumn und misst die komplette Liste neu. */
    return remember(state) {
        Modifier
            .onRotaryScrollEvent { ev ->
                deltas.trySend(ev.verticalScrollPixels)
                true
            }
            .focusRequester(focus)
            .focusable()
    }
}

// Gleiche Mechanik für eine einfache Column mit verticalScroll.
@Composable
private fun rotaryScrollModifier(
    state: ScrollState,
    active: Boolean = true
): Modifier {

    val focus = remember {
        FocusRequester()
    }

    // Gleiche Sammel-Mechanik wie oben — siehe Kommentar dort.
    val deltas = remember {
        Channel<Float>(Channel.UNLIMITED)
    }

    LaunchedEffect(state) {
        while (true) {
            var d = deltas.receive()
            while (true) {
                d += deltas.tryReceive().getOrNull() ?: break
            }
            state.scrollBy(d)
        }
    }

    LaunchedEffect(active) {
        if (active) {
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                /* STILL, UND ZWAR MIT ABSICHT: Fokus fuer die Krone. Klappt
                   er nicht, dreht man mit dem Finger. */
            }
        }
    }

    return remember(state) {
        Modifier
            .onRotaryScrollEvent { ev ->
                deltas.trySend(ev.verticalScrollPixels)
                true
            }
            .focusRequester(focus)
            .focusable()
    }
}

/* ==========================================================================
   DIE GAMEPLAN-ANSICHT IST ENTFERNT (2026-08-26 (40))
   --------------------------------------------------------------------------
   Sie zeigte je Loch den am Handy geplanten Schlaeger und die Zielbeschreibung
   — gerechnet wurde hier nie etwas, die Plaene kamen fertig aus `plans`.
   TROTZDEM RAUS, auf ausdruecklichen Wunsch vom 26.08.: Nach 0. ZWECK zeigt
   die Uhr keine EMPFEHLUNG mehr, und ein vorab gefasster Plan ist eine —
   nur eine aelteren Datums. Dass die Rechnung woanders stattfand, macht die
   Anzeige nicht zu etwas anderem.
   Die Zeile 📋 im Spiel war bereits mit (38) entfallen (sie sass auf der
   Loch-Seite); dies ist das Gegenstueck im Startmenue.
   WO ER WEITERLEBT: in der PWA. Dort wird er geplant, dort steht er.
   ========================================================================== */

@Composable
private fun HomeScreen(
    listState: ScalingLazyListState,
    hasResume: Boolean,
    resumeLabel: String,
    /* Was liegt beim Handy? Ohne diese Angabe war der oberste Knopf eine
       ANWEISUNG („am Handy starten, hier holen") statt einer Handlung — man
       tippt ihn, und die Uhr sucht zwei Minuten lang etwas, das es nicht gibt
       (2026-08-15 (16)). */
    phoneRunde: String?,
    datenAlter: String?,
    loading: Boolean,
    status: String,
    keepScreen: Boolean,
    gpsSource: String,
    awaitingPhone: Boolean,
    /* TURNIERMODUS (54): Zustand und Umschalter. `turnierName` ist der
       Mitspieler VOM HANDY — ist er leer, gibt es nichts zu erfassen, und der
       Knopf sagt das, statt gesperrt und unerklaert dazustehen. */
    turnier: Boolean,
    turnierName: String?,
    onTurnier: () -> Unit,
    onFetchPhone: () -> Unit,
    onCancelFetch: () -> Unit,
    /* App wirklich beenden (2026-08-25 (20)). Der Wisch nach rechts schiebt sie
       nur in den Hintergrund, wo GPS und Abgleich weiterlaufen. */
    onQuit: () -> Unit = {},
    /* KEIN `onNew` und KEIN `onDiscard` MEHR (44) — Runden entstehen und enden
       am Handy. */
    onResume: () -> Unit,
    onKeepScreen: (Boolean) -> Unit,
    onGpsSource: (String) -> Unit
) {

    var keep by remember {
        mutableStateOf(keepScreen)
    }

    var gps by remember {
        mutableStateOf(gpsSource)
    }


    ScalingLazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .then(rotaryScrollModifier(listState)),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        /* KEIN TITEL MEHR (2026-08-15 (16)).
           „⛳ Golf-Runde" kostete auf einem runden Display eine ganze Zeile und
           sagte, was ohnehin auf dem Zifferblatt stand. An seiner Stelle steht
           jetzt der ZUSTAND: Was liegt beim Handy, wie alt sind die Daten.
           Das ist die Frage, mit der man diesen Bildschirm aufruft. */
        /* ==================================================================
           FASSUNGSNUMMER GANZ OBEN (2026-08-25 (20))
           --------------------------------------------------------------------
           Sie stand bisher nur im Protokoll — man musste also erst blaettern
           und suchen, um zu wissen, welche Fassung laeuft. Diese Woche war das
           mehrfach die erste Frage, und jedes Mal hat sie einen Umlauf
           gekostet.
           GANZ KLEIN UND GEDAEMPFT: Sie ist eine Auskunft, keine Ansage. Auf
           einem runden Display darf sie nichts von dem verdecken, wofuer man
           den Bildschirm oeffnet — das ist der Zustand der Runde direkt
           darunter. */
        item {
            Text(
                WATCH_APP,
                fontSize = 9.sp,
                color = InkFaint,
                maxLines = 1
            )
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    phoneRunde ?: "keine Runde am Handy",
                    fontSize = 13.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    color = if (phoneRunde != null) GoldText else InkFaint
                )
                if (datenAlter != null) Text(
                    "Daten " + datenAlter,
                    fontSize = 10.sp,
                    maxLines = 1,
                    color = InkFaint
                )
            }
        }

        if (loading) {

            item {
                CircularProgressIndicator(
                    Modifier.padding(8.dp)
                )
            }
        }

        if (hasResume) {

            item {

                Chip(
                    onClick = onResume,
                    label = {
                        Text("Fortsetzen")
                    },
                    secondaryLabel = {
                        Text(
                            resumeLabel,
                            maxLines = 1
                        )
                    },
                    colors =
                        ChipDefaults.primaryChipColors(),
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            /* KEIN „VERWERFEN" MEHR (2026-08-27 (44)). Es war die
               zerstoerendste Handlung der App, ausgeloest mit zwei Tippern am
               Handgelenk — und es schrieb einen Grabstein ins Repo, der die
               Runde auch am Handy beendete. Verwerfen passiert dort, wo man
               sieht, was man wegwirft. */
        }

        /* ==============================================================
           TURNIERMODUS — UMSCHALTER, KEIN ZWEITER RUNDENSTART (54)
           --------------------------------------------------------------
           Er steht VOR dem Handy-Knopf, weil er die Frage „wie erfasse ich"
           beantwortet und nicht „welche Runde". Die Runde kommt danach wie
           immer vom Handy.
           OHNE MITSPIELER KEIN TURNIERMODUS: Der Knopf bleibt sichtbar und
           sagt, was fehlt. Ein gesperrter Knopf ohne Grund erzeugt genau die
           Ratlosigkeit, die diese App vermeiden soll. */
        item {

            Chip(
                onClick = { if (!turnierName.isNullOrBlank()) onTurnier() },
                label = {
                    Text(if (turnier) "Turniermodus AN" else "Turniermodus")
                },
                secondaryLabel = {
                    Text(
                        if (turnierName.isNullOrBlank())
                            "Mitspieler am Handy anlegen"
                        else if (turnier) "nur Score · mit $turnierName"
                        else "nur Score · $turnierName",
                        maxLines = 1
                    )
                },
                colors =
                    if (turnier) ChipDefaults.primaryChipColors()
                    else ChipDefaults.secondaryChipColors(),
                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        // Der Normalweg: Runde am Handy anlegen (Platz, Tee, Umfang, EDS)
        // und hier nur noch übernehmen. Die Uhr kann beim Alleinstart
        // weder EDS noch Rundenumfang aus dem Handy-Setup abbilden.
        item {

            Chip(
                onClick =
                    if (awaitingPhone) onCancelFetch else onFetchPhone,
                label = {
                    Text(
                        if (awaitingPhone) "Suche abbrechen"
                        else if (phoneRunde != null) "📱 Runde holen"
                        else "📱 Auf Handy warten",
                        maxLines = 1
                    )
                },
                secondaryLabel = {
                    /* SAGEN, WAS PASSIERT: Liegt eine Runde bereit, steht sie
                       hier beim Namen. Liegt keine, ist der Knopf kein Fehler,
                       sondern eine Wartestellung — und das steht auch da,
                       statt einer Anweisung, die wie ein Ziel aussieht. */
                    Text(
                        if (awaitingPhone) "sucht bis zu 2 Minuten"
                        else if (phoneRunde != null) phoneRunde
                        else "erst am Handy starten",
                        maxLines = 1,
                        fontSize = 11.sp
                    )
                },
                colors =
                    if (awaitingPhone || hasResume || phoneRunde == null)
                        ChipDefaults.secondaryChipColors()
                    else
                        ChipDefaults.primaryChipColors(),
                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        /* KEIN „⌚ OHNE HANDY STARTEN" MEHR (2026-08-27 (44)).
           VORGABE VOM 27.08.: „Eine Runde nur mit Uhr und ohne Handy gibt es
           nicht." Der Chip war als Notnagel gedacht und stand mit seinen
           Einschraenkungen im Nebentext — aber ein zweiter Weg, auf dem eine
           Runde entsteht, ist ein zweiter Zustand, der auseinanderlaufen kann.
           Genau daran hingen die Mitspieler-Meldung vom 27.08. und die Frage,
           wer bei Rundenumfang und EDS fuehrt.
           ES BLEIBEN ZWEI WEGE, beide vom Handy aus: „Runde vom Handy holen"
           und „Fortsetzen". */

        /* EINSTELLUNGEN NACH UNTEN (2026-08-15 (16)).
           „Display an" und „GPS-Quelle" standen zwischen den Handlungen — auf
           einem runden Display nimmt jeder Chip ein Drittel der Hoehe, und
           beides stellt man einmal ein und nie wieder. Sie stehen jetzt unter
           einer Trennzeile, damit oben die drei Wege in eine Runde sichtbar
           sind, ohne zu scrollen. */
        item {
            Text(
                "Einstellungen",
                fontSize = 11.sp,
                color = InkFaint,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
            )
        }

        item {

            Chip(
                onClick = {
                    keep = !keep
                    onKeepScreen(keep)
                },
                label = {
                    Text("Display an", fontSize = 13.sp, maxLines = 1)
                },
                secondaryLabel = {
                    /* fontSize + maxLines: Ohne beides lief „normal" auf dem
                       runden Rand aus dem Bild (im Foto stand nur „mmer"). */
                    Text(if (keep) "immer" else "normal", fontSize = 11.sp, maxLines = 1)
                },
                colors =
                    if (keep)
                        ChipDefaults.primaryChipColors()
                    else
                        ChipDefaults.secondaryChipColors(),
                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        item {

            Chip(
                onClick = {
                    gps = if (gps == "phone") "watch" else "phone"
                    onGpsSource(gps)
                },
                label = {
                    Text("GPS-Quelle", fontSize = 13.sp, maxLines = 1)
                },
                secondaryLabel = {
                    Text(
                        if (gps == "phone") "📱 Handy · spart Uhr-Akku"
                        else "⌚ Uhr · eigenes GPS",
                        fontSize = 11.sp, maxLines = 1
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (gps == "phone") {

            item {

                Text(
                    "Handy-Modus nutzt die Standortdienste des gekoppelten " +
                            "Handys über Bluetooth. Ohne Verbindung wechselt die " +
                            "Uhr automatisch auf ihre eigenen Sensoren.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption3,
                    color = InkFaint,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        item {

            Text(
                "Während der Runde läuft ein Hintergrund-Dienst mit GPS " +
                        "(Notification „Golf-Runde\") — die Erfassung läuft weiter, " +
                        "auch wenn die Uhr das Zifferblatt zeigt.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption3,
                color = InkFaint,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        if (status.isNotEmpty()) {

            item {

                Text(
                    status,
                    textAlign =
                        TextAlign.Center,
                    style =
                        MaterialTheme.typography.caption2
                )
            }
        }

        /* GANZ UNTEN AUF DEM STARTBILDSCHIRM (2026-08-16 (13)).
           Der wichtigste Ort fuer das Protokoll: Nach einem Absturz landet man
           HIER — und will wissen, was passiert ist, ohne erst eine Runde zu
           starten. Ganz unten, weil man es selten braucht; wenn doch, sucht man
           gezielt.
           `zeigeLeer = true` nur hier: Eine Zeile „keine Meldungen" ist auf dem
           Startbildschirm eine ANTWORT (die Uhr laeuft sauber), waehrend sie in
           der Rundenansicht nur Platz kostet. */
        item { Spacer(Modifier.height(6.dp)) }
        fehlerBlock(zeigeLeer = true)

        /* ==================================================================
           BEENDEN GANZ AM ENDE (2026-08-25 (20))
           --------------------------------------------------------------------
           Bisher kam man nur ueber den Wisch nach rechts oder die
           Seitentaste heraus — beides beendet die App NICHT, es schiebt sie in
           den Hintergrund, wo GPS und Abgleich weiterlaufen. Auf einer Uhr ist
           das der Unterschied zwischen einer und drei Stunden Akku.
           GANZ UNTEN und ohne Farbe: Es ist die seltenste Handlung auf diesem
           Bildschirm, und ein auffaelliger Knopf hier wuerde versehentlich
           getroffen. Wer beenden will, scrollt.
           ERST DIENST STOPPEN, DANN SCHLIESSEN — sonst laeuft der
           Vordergrunddienst weiter und haelt die Ortung am Leben. Genau das ist
           bei `finish()` ohne `svcStop` am 15.08. passiert. */
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Chip(
                /* `ctx` und `activity` gibt es in dieser Composable NICHT —
                   sie gehoeren der aufrufenden. Deshalb ein Rueckruf, wie beim
                   Lochwechsel (2026-08-24 (3)): Wer den Zustand besitzt,
                   handelt auch. */
                onClick = onQuit,
                label = {
                    Text("App beenden", fontSize = 13.sp, maxLines = 1)
                },
                secondaryLabel = {
                    Text("stoppt GPS und Abgleich", fontSize = 10.sp, maxLines = 1)
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

/* `PickScreen` ist entfernt (2026-08-27 (44)) — Platzliste, Tee und
   Rundenumfang auf der Uhr. Eine Runde entsteht nur noch am Handy. */

@Composable
private fun PickerScreen(
    title: String,
    options: List<String>,
    current: String?,
    listState: ScalingLazyListState,
    onCancel: () -> Unit,
    onSelect: (String?) -> Unit
) {

    ScalingLazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .then(rotaryScrollModifier(listState)),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        item {

            Text(
                title,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme.typography.title3,
                textAlign =
                    TextAlign.Center
            )
        }

        // Abbrechen lässt den bisherigen Wert unberührt — im Gegensatz
        // zu „(leer)", das ihn löscht. Zwei verschiedene Absichten.
        item {

            CompactChip(
                onClick = onCancel,
                label = {
                    Text("‹ Abbrechen", fontSize = 12.sp)
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {

            Chip(
                onClick = {
                    onSelect(null)
                },
                label = {
                    Text("—  (leer)")
                },
                colors =
                    if (current == null)
                        ChipDefaults.primaryChipColors()
                    else
                        ChipDefaults.secondaryChipColors(),
                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        items(options) { o ->

            Chip(
                onClick = {
                    onSelect(o)
                },
                label = {
                    Text(
                        o,
                        maxLines = 2
                    )
                },
                colors =
                    if (o == current)
                        ChipDefaults.primaryChipColors()
                    else
                        ChipDefaults.secondaryChipColors(),
                modifier =
                    Modifier.fillMaxWidth()
            )
        }
    }
}

/* =====================================================================
 *  SPIELBEREICH — drei Seiten, horizontal wischbar (PlayPager)
 *
 *  Die Seiten folgen dem ABLAUF eines Lochs, nicht der Datenstruktur:
 *
 *    0  HolePage    schauen  — Mitteldistanz groß, F/B, Caddy, Schlagmessung
 *    1  ScorePage   eintragen — Score-Raster, Putts, Tee, Lochwechsel, Runde
 *    2  DetailPage  vertiefen — GIR, Lage, Bunker, Strafen … alles optional
 *
 *  Merksatz für die Bedienung: HORIZONTAL = Phase im Loch,
 *  VERTIKAL = mehr vom Gleichen (Krone scrollt innerhalb einer Seite).
 *
 *  Lochwechsel liegt bewusst NICHT auf der Wischgeste, sondern auf den
 *  Pfeil-Buttons der Score-Seite: zwei Bedeutungen für dieselbe Richtung
 *  lernt man mit Handschuh nie sicher. Seiten wechselt man hundertfach,
 *  das Loch 18-mal.
 *
 *  Zurück (Wisch von rechts / Seitentaste) wird zentral im BackHandler in
 *  GolfWatchApp behandelt: Picker -> Seite 0 -> Übersicht -> App zu.
 * ===================================================================== */
/* `WizBtn` ENTFERNT (2026-08-14 (7)).
   Der Knopf gehoerte zum Abschluss-Wizard, den es seit dem Umbau nicht mehr
   gibt — er stand seither ungenutzt in der Datei. Toter Code ist nicht nur
   Ballast: Er sieht beim naechsten Lesen wie eine Zusage aus („es gibt hier
   einen Langdruck-Knopf"), die niemand einloest. Die Haptik-Mechanik lebt in
   `Stepper` weiter; wer wieder einen Langdruck braucht, baut ihn dort, wo er
   benutzt wird. */

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
/* ===========================================================================
   AMBIENT-SCREEN — was die Uhr im Always-On zeigt

   Bewusst minimal: schwarzer Grund (AMOLED schaltet schwarze Pixel ab),
   drei Zeilen, keine Grafik. Alles, was man beim Blick aufs Handgelenk
   zwischen zwei Schlägen wirklich braucht.
   =========================================================================== */
@Composable
private fun AmbientPlayScreen(
    hole: Int,
    scoreLabel: String,
    /* ==================================================================
       IM AMBIENT STEHT NUR NOCH, WAS GEMESSEN IST (2026-08-26 (38))
       --------------------------------------------------------------------
       Bis (37) trug diese Ansicht Front/Mitte/Back und „spielt wie" — vier
       Zahlen aus Rechnungen, die jetzt allein das Handy anstellt.
       Was bleibt, ist das, wofuer man im Gehen aufs Handgelenk schaut:
       auf welchem Loch man steht, wie man liegt, und ob gerade eine
       Schlagmessung laeuft. Letzteres ist der wichtigste Punkt dieser
       Fassung: Eine vergessene laufende Aufnahme haengt den naechsten
       Startpunkt an die falsche Stelle, und im Ambient sah man es bisher
       ueberhaupt nicht.
       `hasFix` bleibt, weil eine Uhr ohne Fix nicht messen kann — das
       gehoert gesagt, bevor man beim Ball vergeblich tippt. */
    recActive: Boolean,
    recDist: Int?,
    hasFix: Boolean
) {

    /* Dieser Lesezugriff ist der ganze Trick — NICHT entfernen, auch wenn die
       IDE `t` als ungenutzt markiert. Compose zeichnet nur neu, wenn ein
       gelesener State sich ändert; ohne diese Zeile bliebe die Anzeige auf dem
       Stand vom Eintritt in den Ambient stehen. */
    val t = AmbientState.tick

    // Einbrennschutz: Inhalt wandert im Minutentakt über ein kleines Kreuz
    val shift = if (AmbientState.burnIn) {
        when (t % 4) {
            0 -> 0.dp to (-6).dp
            1 -> 6.dp to 0.dp
            2 -> 0.dp to 6.dp
            else -> (-6).dp to 0.dp
        }
    } else {
        0.dp to 0.dp
    }

    // Low-Bit-Displays können keine Graustufen -> dort reines Weiß
    val dim =
        if (AmbientState.lowBit) Color.White else Color(0xFFAAAAAA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.offset(x = shift.first, y = shift.second),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Loch $hole",
                color = dim,
                fontSize = 15.sp
            )

            /* DER STAND IST JETZT DIE GROSSE ZAHL. Vorher war es die
               Mitteldistanz; nach deren Wegfall ist der Stand das Einzige,
               was man im Vorbeigehen wissen will. */
            Text(
                text = scoreLabel,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                text = "über Par",
                color = dim,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            /* Laufende Messung sichtbar machen — mit der rohen Strecke, die
               bereits im Kasten ist. Keine Anpassung, keine Umrechnung: Die
               Uhr misst Meter, das Handy rechnet daraus die neutrale
               Schlagdistanz (`schlagNeutral`, PWA v4.80.1). */
            if (recActive) {
                Text(
                    text = "■ Aufnahme · ${recDist ?: 0} m",
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            } else if (!hasFix) {
                Text(
                    text = "kein GPS-Fix",
                    color = dim,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PlayPager(
    /* TURNIERMODUS (54): Er entscheidet nur, welche Seite die ERSTE ist —
       alles dahinter bleibt erreichbar. Als Parameter und nicht als globaler
       Zustand, damit die Seite ohne die ganze App pruefbar bleibt. */
    turnier: Boolean = false,
    pagerState: PagerState,
    detailListState: ScalingLazyListState,
    scoreListState: ScalingLazyListState,
    course: CourseDef,
    hd: HoleDef,
    entry: HoleEntry,
    idx: Int,
    total: Int,
    status: String,
    opts: Options?,
    // Namen aus der Bag fuer den Approach-Schlaeger (opts.teeClubs sind nur
    // Kategorien, hier braucht es die echten Schlaeger).
    clubNames: List<String>,
    toPar: Int,
    thru: Int,
    onHome: () -> Unit,
    /* KEIN `live`, `plan`, `weatherLine`, `caddyMode` MEHR (2026-08-26 (38)).
       Alles vier war ausschliesslich Futter fuer die entfallene Seite 0.
       Die Uhr zeigt keine gerechneten Groessen mehr — Entfernungen, „spielt
       wie" und Schlaegerempfehlungen kommen vom Handy und bleiben dort.
       Von `PlayLive` bleiben ZWEI SKALARE uebrig, und zwar mit Absicht als
       Zahl und Text statt als Objekt: Die Genauigkeit ist ab dieser Fassung
       die einzige Qualitaetsgroesse der Uhr — Start- und Endpunkt einer
       Messung gehen direkt in die gelernten Schlaegerlaengen ein. Wer sie
       als Feld eines groesseren Objekts durchreicht, laedt dazu ein, spaeter
       wieder Gerechnetes danebenzulegen. */
    gpsAcc: Int?,
    gpsErr: String?,
    recActive: Boolean,
    recClubName: String?,
    recSwingName: String?,
    syncAge: String?,
    syncStale: Boolean,
    recDist: Int?,
    shotCount: Int,
    onScore: (Int) -> Unit,
    onPutts: (Int) -> Unit,
    onPen: (Int) -> Unit,
    onBunkerN: (Int) -> Unit,
    onUd: () -> Unit,
    onSs: () -> Unit,
    onRec: () -> Unit,
    onPick: (
        String,
        List<String>,
        String?,
        (HoleEntry, String?) -> HoleEntry
    ) -> Unit,
    /* Loch weiterblaettern (2026-08-24). `+1` / `-1`, nicht der Zielindex:
       Die aufrufende Composable besitzt den Zustand und kennt die Lochliste —
       sie prueft die Grenzen. Ein Zielindex von hier waere eine zweite Stelle,
       an der man sich verrechnen kann. */
    onHoleDelta: (Int) -> Unit = {},
    onShotBegin: () -> Unit,
    onShotClub: () -> Unit,
    onShotSwing: () -> Unit,
    onShotStop: () -> Unit,
    onShotCancel: () -> Unit,
    onShotUndo: () -> Unit,
    mitspielerNamen: List<String> = emptyList(),
    onPrev: () -> Unit,
    onNext: () -> Unit
    /* KEIN `onFinish` MEHR (44) — Abschliessen passiert am Handy. */
) {

    val scope = rememberCoroutineScope()

    // Der Pager frisst normale Horizontalwischer. Damit die Wear-Geste
    // "von rechts wischen = zurück" nicht verloren geht, greift sie über
    // edgeSwipeToDismiss nur noch am LINKEN Displayrand — mit der üblichen
    // Animation, bei der der Screen mitwandert.
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
            // Randwisch ist eine bewusste Geste (Handschuh trifft den Rand
            // nicht zufällig), deshalb ohne Nachfrage. Die Runde läuft im
            // Service weiter und steht auf der Übersicht als Fortsetzen bereit.
            onHome()
            dismissState.snapTo(SwipeToDismissValue.Default)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.fillMaxSize()
    ) { isBackground ->

        if (isBackground) {

            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
            )

        } else {

            Box(modifier = Modifier.fillMaxSize()) {

                // Wischen nach links/rechts = Phase im Loch wechseln.
                // Lochwechsel liegt bewusst NICHT auf der Geste, sondern auf Buttons:
                // zwei Bedeutungen für dieselbe Wischrichtung lernt man mit Handschuh nie.
                HorizontalPager(
                    state = pagerState,
                    // Ohne diese Begrenzung trägt ein zügiger Wisch über zwei Seiten
                    // hinweg — man will auf 2 und landet auf 3. atMost(1) erlaubt
                    // pro Geste genau einen Seitenschritt, egal wie schnell gewischt.
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        pagerSnapDistance = PagerSnapDistance.atMost(1)
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .edgeSwipeToDismiss(dismissState)
                ) { page ->

                    when (page) {

                        /* SEITE 0 = SCORE (2026-08-26 (38)). Hier stand bis
                           (37) `HolePage` — Entfernungen, „spielt wie", Caddy,
                           Wetter. Die Composable bleibt in dieser Fassung
                           unaufgerufen im Quelltext stehen; der Abbau ist
                           Fassung (39), damit eine Verhaltensaenderung und ein
                           Rueckbau nicht in derselben Fassung liegen (siehe
                           (35), gleiche Regel). */
                        /* ==================================================
                           TURNIERMODUS: eine andere MASKE, dieselbe Runde (54)
                           --------------------------------------------------
                           Der Umschalter von der Startseite entscheidet hier,
                           welche Seite die erste ist. Alles dahinter — Karte,
                           Caddy, Wetter — bleibt erreichbar; wer im Turnier
                           doch einmal eine Entfernung braucht, wischt weiter.
                           NUR DIE ERSTE SEITE WIRD GETAUSCHT, nicht der
                           Pager: Ein zweiter Bildschirmaufbau waere ein
                           zweiter Ort fuer dieselben Fehler.
                           ABSOLUTWERT STATT DELTA: `onScore` der normalen
                           Maske erwartet eine AENDERUNG (+1/−1); die
                           Turnierzeile kennt den Zielwert. Deshalb eigene
                           Rueckrufe, die denselben `change()`-Weg gehen. */
                        0 -> if (turnier) TurnierPage(
                            listState = scoreListState,
                            hd = hd,
                            entry = entry,
                            idx = idx,
                            total = total,
                            mitName = mitspielerNamen.firstOrNull() ?: "Mitspieler",
                            toPar = toPar,
                            thru = thru,
                            onScore = { v ->
                                change(hd.hole) {
                                    it.copy(score = v.coerceIn(1, 15), putts = it.putts ?: 2)
                                }
                            },
                            onMsc = { v ->
                                change(hd.hole) { it.copy(msc1 = v.coerceIn(1, 15)) }
                            },
                            onPrev = onPrev,
                            onNext = onNext,
                            onHome = onHome
                        ) else ScorePage(
                            active = pagerState.currentPage == 0,
                            listState = scoreListState,
                            course = course,
                            hd = hd,
                            entry = entry,
                            idx = idx,
                            total = total,
                            status = status,
                            opts = opts,
                            clubNames = clubNames,
                            toPar = toPar,
                            thru = thru,
                            onScore = onScore,
                            onPutts = onPutts,
                            onPen = onPen,
                            onPick = onPick,
                            onPrev = onPrev,
                            onNext = onNext,
                            onHome = onHome,
                            shotCount = shotCount,
                            onShotUndo = onShotUndo,
                            mitspielerNamen = mitspielerNamen,
                            /* Die Lochpfeile der Kopfzeile gehen ueber
                               denselben Rueckruf wie frueher auf Seite 1:
                               Die Grenzen prueft die aufrufende Composable,
                               die den Zustand auch besitzt. */
                            onHolePrev = { onHoleDelta(-1) },
                            onHoleNext = { onHoleDelta(+1) },
                            hasPrev = idx > 0,
                            hasNext = idx < total - 1,
                            gpsAcc = gpsAcc,
                            gpsErr = gpsErr,
                            syncAge = syncAge,
                            syncStale = syncStale,
                            // Schlagzeile, fest am unteren Rand
                            recActive = recActive,
                            recClubName = recClubName,
                            recSwingName = recSwingName,
                            recDist = recDist,
                            onShotBegin = onShotBegin,
                            onShotClub = onShotClub,
                            onShotSwing = onShotSwing,
                            onShotStop = onShotStop,
                            onShotCancel = onShotCancel
                        )

                        else -> DetailPage(
                            active = pagerState.currentPage == 1,
                            listState = detailListState,
                            hd = hd,
                            entry = entry,
                            opts = opts,
                            onPen = onPen,
                            onBunkerN = onBunkerN,
                            onUd = onUd,
                            onSs = onSs,
                            onRec = onRec,
                            onPick = onPick
                        )
                    }
                }

                // Seitenpunkte am unteren Rand — die einzige Stelle, die verrät,
                // dass es links und rechts noch etwas gibt.
                val indicatorState = remember(pagerState) {
                    object : PageIndicatorState {
                        override val pageOffset: Float
                            get() = pagerState.currentPageOffsetFraction
                        override val selectedPage: Int
                            get() = pagerState.currentPage
                        override val pageCount: Int
                            get() = 2
                    }
                }

                HorizontalPageIndicator(
                    pageIndicatorState = indicatorState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

        } // Ende else (Vordergrund)
    } // Ende SwipeToDismissBox
}

// ============================================================
//  `HolePage` IST ENTFERNT (2026-08-26 (40)) — rund 530 Zeilen.
//  Sie WAR Seite 0: Front/Mitte/Back, „spielt wie", Gruenmasse,
//  Pin-Distanz, Caddy-Zeile samt Modusumschaltung, Gefahrenwarnung,
//  Wetterzeile, Gameplan. Seit (38) nicht mehr im Pager, jetzt weg.
//  WER SIE ZURUECKHOLEN WILL, liest zuerst 0. ZWECK. Die Uhr misst
//  und nimmt entgegen; sie rechnet nichts und empfiehlt nichts.
// ============================================================

// ============================================================
//  SEITE 0 — SCORE: die Hauptseite (seit (38)).
//  Eintragen, was nach dem Einlochen feststeht; oben die Kopfzeile
//  mit den Lochpfeilen, unten fest verankert die Schlagzeile, und
//  dazwischen die Rundenaktionen (Abschluss, Übersicht).
// ============================================================

@Composable
/* ==========================================================================
   TURNIERSEITE — ZWEI ZAHLEN, SONST NICHTS (2026-08-30 (54))
   --------------------------------------------------------------------------
   GEWUENSCHT: „Da erfasse ich dann auf einem Loch nur den Gesamtscore von mir
   und einem Mitspieler. Nichts anderes."
   EIGENE SEITE STATT EINER ABGESPECKTEN `ScorePage`. Das ist bewusst: Wer
   dieselbe Seite mit einem Schalter halbiert, hat zwei Seiten in einer, und
   jede spaetere Aenderung muss beide Faelle bedenken. Diese hier hat einen
   einzigen Zweck und passt auf einen Bildschirm.
   WAS DRAUFSTEHT: Lochnummer und Par oben, darunter zwei Zeilen — meine und
   die des Mitspielers, jede mit Minus, Zahl, Plus. Unten Loch zurueck und
   weiter. Kein Putt, keine Lage, kein Schlaeger, kein Strafschlag.
   GROSSE FLAECHEN: Im Turnier zaehlt man unter Zeitdruck und oft mit
   Handschuh. Die Tippflaechen sind deshalb so gross, wie der Bildschirm
   hergibt — lieber drei Zeilen als sechs kleine Knoepfe.
   KEIN EIGENER SPEICHERWEG: Die Eingaben gehen durch dieselben Rueckrufe wie
   sonst (`onScore`, `onMsc`) und landen im selben Entwurf. Ein zweiter
   Speicherweg waere ein zweiter Ort fuer dieselben Fehler. */
@Composable
private fun TurnierPage(
    listState: ScalingLazyListState,
    hd: HoleDef,
    entry: HoleEntry,
    idx: Int,
    total: Int,
    mitName: String,
    toPar: Int,
    thru: Int,
    onScore: (Int) -> Unit,
    onMsc: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onHome: () -> Unit
) {
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Loch ${hd.hole} · Par ${hd.par}",
                    fontSize = 15.sp,
                    color = PineText
                )
                /* Der Stand der Runde — die einzige Zahl ausser den beiden
                   Scores, und die einzige, die man im Turnier wirklich
                   dauernd im Kopf hat. */
                Text(
                    (if (toPar > 0) "+$toPar" else if (toPar < 0) "$toPar" else "E") +
                        " nach $thru",
                    fontSize = 12.sp,
                    color = GoldText
                )
            }
        }

        /* MEINE ZAHL. `entry.score` ist dieselbe Groesse wie in der normalen
           Maske — es ist dieselbe Runde, nur eine andere Ansicht. */
        item { TurnierZeile("Ich", entry.score, onScore) }

        /* DIE DES MITSPIELERS. Der Name kommt vom Handy (Regel aus (42)):
           Die `index.html` ist bei Mitspielern fuehrend, die Uhr fuehrt keine
           eigene Liste. */
        item { TurnierZeile(mitName, entry.msc1, onMsc) }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                CompactChip(
                    onClick = onPrev,
                    label = { Text("‹", fontSize = 18.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f)
                )
                CompactChip(
                    onClick = onHome,
                    label = { Text("⌂", fontSize = 15.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f)
                )
                CompactChip(
                    onClick = onNext,
                    label = { Text("›", fontSize = 18.sp) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

/* ==========================================================================
   EIN ZAEHLER, KEINE SCORE-AUSWAHL (2026-08-30 (55))
   --------------------------------------------------------------------------
   KORRIGIERT auf Nachfrage: „Ich moechte, dass er auf 1 startet, da ich die
   Uhr benutzen will, um JEDEN SCHLAG MITZUZAEHLEN. Ich will also im Laufe des
   Loches den Score immer weiter hochzaehlen, bis dann am Ende der Endscore
   feststeht."
   IN (54) SETZTE DER ERSTE TIPP PAR. Das war fuer eine ANDERE Benutzung
   gedacht — Endscore am Ende des Lochs eintragen, und dort ist Par der
   haeufigste Wert. Fuer das MITZAEHLEN ist es genau falsch: Wer beim ersten
   Schlag tippt und eine 4 sieht, muss dreimal zurueck.
   JETZT BEGINNT DIE ZAEHLUNG BEI 1. Jeder Tipp ist ein Schlag — der erste
   Tipp der erste Schlag. Am Ende des Lochs steht die Zahl, die man ohnehin im
   Kopf mitgefuehrt hat, und niemand muss sie noch einmal umrechnen.
   DAS IST DER UNTERSCHIED ZWISCHEN EINEM ZAEHLER UND EINER AUSWAHL, und er
   entscheidet ueber die ganze Bedienung: Ein Zaehler wird waehrend des Lochs
   benutzt, eine Auswahl danach. Meine Annahme in (54) war die falsche.
   „−" BLEIBT, und es ist hier wichtiger als vorher: Beim Mitzaehlen tippt man
   sich irgendwann einmal vertan, und zwar mitten im Loch. Ein Zaehler ohne
   Rueckweg waere auf der Bahn unbrauchbar.
   ========================================================================== */
@Composable
private fun TurnierZeile(
    name: String,
    wert: Int?,
    /* `par` ist mit (55) entfallen — es diente nur dem Startwert, und der ist
       jetzt 1. Ein Parameter, den niemand liest, ist eine Zusage, die niemand
       einloest: Beim naechsten Lesen fragt man sich, wo das Par einfliesst. */
    onSet: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(name, fontSize = 12.sp, color = PineText, maxLines = 1)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactChip(
                onClick = { if ((wert ?: 0) > 1) onSet((wert ?: 0) - 1) },
                label = { Text("−", fontSize = 20.sp) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.weight(1f)
            )
            Text(
                wert?.toString() ?: "–",
                fontSize = 26.sp,
                color = if (wert == null) PineText else GoldText,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            CompactChip(
                /* BEI 1 BEGINNEN (55): jeder Tipp ein Schlag. Siehe oben. */
                onClick = { onSet(if (wert == null) 1 else wert + 1) },
                label = { Text("+", fontSize = 20.sp) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun ScorePage(
    active: Boolean,
    listState: ScalingLazyListState,
    course: CourseDef,
    hd: HoleDef,
    entry: HoleEntry,
    idx: Int,
    total: Int,
    status: String,
    opts: Options?,
    // Namen aus der Bag — fuer den Approach-Schlaeger. opts.teeClubs enthaelt
    // nur Kategorien (Driver/Holz/Hybrid/Eisen), hier braucht es die echten
    // Schlaeger.
    clubNames: List<String>,
    toPar: Int,
    thru: Int,
    onScore: (Int) -> Unit,
    onPutts: (Int) -> Unit,
    /* Strafschlaege stehen seit dem Umbau OBEN auf der Score-Seite und nicht
       mehr in den Details — der Rueckruf muss also hier ankommen. Beim
       Verschieben wurde er in der Signatur vergessen, der Aufruf im Rumpf blieb
       stehen: „Unresolved reference: onPen". */
    onPen: (Int) -> Unit,
    onPick: (
        String,
        List<String>,
        String?,
        (HoleEntry, String?) -> HoleEntry
    ) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    /* KEIN `onFinish` MEHR (44) — Abschliessen passiert am Handy. */
    onHome: () -> Unit,
    /* ZURUECKNEHMEN sitzt jetzt HIER statt auf der Loch-Seite. Dort teilte es
       sich den Platz mit „Aufnahme abbrechen" — zwei zerstoerende Aktionen an
       einer Stelle, unterschieden nur durch einen Zustand, den man beim Ball
       nicht nachsieht. Auf dieser Seite hat man Zeit hinzuschauen, und ein
       zweistufiger Tipp sichert zusaetzlich ab. */
    shotCount: Int,
    onShotUndo: () -> Unit,
    /* ==================================================================
       LOCHPFEILE UND SCHLAGZEILE (2026-08-26 (38))
       --------------------------------------------------------------------
       Beides sass bis (37) auf Seite 0. Die ist entfallen, und beides
       darf nicht mit ihr verschwinden: Der Lochwechsel ist die haeufigste
       Handlung der Runde, das Schlagtracken die einzige, die beim Ball
       passiert.
       Die Pfeile gehen ueber `onHoleDelta` der aufrufenden Composable —
       dieselbe Leitung wie frueher, nur ein anderer Ort. Die Pfeile unten
       in der Rubrik „Runde" bleiben zusaetzlich bestehen: Sie sind
       DERSELBE Zustand, kein zweites Feld, und wer ohnehin unten steht,
       soll nicht erst hochscrollen. */
    onHolePrev: (() -> Unit)? = null,
    onHoleNext: (() -> Unit)? = null,
    hasPrev: Boolean = false,
    hasNext: Boolean = false,
    /* GPS-GUETE STEHT JETZT HIER (2026-08-26 (38)). Sie hing bis (37) unter
       der Mitteldistanz auf Seite 0 — also an einer Zahl, die es nicht mehr
       gibt. Sie darf nicht mit ihr verschwinden: Ohne sie sieht eine Messung
       mit 12 m Streuung genauso souveraen aus wie eine mit 3 m, und man
       merkt den Unterschied erst Wochen spaeter an den gelernten
       Schlaegerlaengen. Ab 10 m in Warnfarbe. */
    gpsAcc: Int? = null,
    gpsErr: String? = null,
    /* Der Abgleich erscheint NUR, WENN ER STOCKT (Regel aus 2026-08-14 (7)):
       Eine Angabe, die immer da ist, sieht man irgendwann nicht mehr. */
    syncAge: String? = null,
    syncStale: Boolean = false,
    recActive: Boolean = false,
    recClubName: String? = null,
    recSwingName: String? = null,
    recDist: Int? = null,
    onShotBegin: () -> Unit = {},
    onShotClub: () -> Unit = {},
    onShotSwing: () -> Unit = {},
    onShotStop: () -> Unit = {},
    onShotCancel: () -> Unit = {},
    /* Namen der Mitspieler, wie das HANDY sie fuer diese Runde fuehrt (42).
       Als Parameter und nicht direkt aus `Mitspieler.namen` gelesen: Ein
       `@Volatile var` ist kein Compose-State — die Liste wuerde beim
       Eintreffen neuer Namen nicht neu gezeichnet. Denselben Fehler hat der
       Lochzeiger schon einmal gekostet.
       LEERE LISTE HEISST KEINE ZEILE. Das ist die ganze Regel vom 27.08.:
       Was in der PWA nicht steht, steht auch hier nicht. */
    mitspielerNamen: List<String> = emptyList()
) {

    val haptics = LocalHapticFeedback.current

    /* BEIM AUFNAHMESTART NACH OBEN SPRINGEN (43). Dort stehen dann Schlaeger
       und Schwung — die zwei Angaben, die in den Sekunden nach „Schlag hier"
       fallen. Ohne den Sprung muesste man sie suchen, und beim Ball sucht
       niemand.
       NUR BEIM START, nicht beim Stopp: Nach dem Stopp ist man am Ball und
       traegt oft direkt den Score ein — dann waere ein Sprung ein Ruck, den
       man nicht bestellt hat. */
    LaunchedEffect(recActive) {
        if (recActive) listState.scrollToItem(0)
    }

    /* `confirmFinish` entfaellt (44) — es gibt nichts mehr zu bestaetigen. */

    var confirmUndo by remember { mutableStateOf(false) }

    LaunchedEffect(confirmUndo) {
        if (confirmUndo) {
            delay(4000)
            confirmUndo = false
        }
    }

    /* DIE SCHLAGZEILE LIEGT UEBER DER LISTE, NICHT IN IHR (2026-08-26 (38)).
       Am 12.08. wurde sie schon einmal verankert und am 14.08. wieder
       geloest — sie verdeckte damals Mitteldistanz, Gameplan und
       Caddy-Zeile. GENAU DIESE DREI GIBT ES NICHT MEHR. Der Grund fuer die
       Ruecknahme ist mit ihnen entfallen, und auf einer Seite, die man
       ohnehin durchscrollt, kostet eine feste Aktionsleiste eine Zeile
       statt einer Information.
       Sie MUSS fest sein: Man tippt sie beim Ball, mit Handschuh, ohne
       hinzusehen. Eine Schaltflaeche, die man erst suchen muss, wird auf
       der Runde nicht benutzt — und dann fehlt die Messung. */
    Box(modifier = Modifier.fillMaxSize()) {

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(rotaryScrollModifier(listState, active)),
        /* KEIN `contentPadding` FUER DIE FREIHALTUNG UNTEN — die Vorgabe von
           `autoCentering` rechnet oben und unten ohnehin eigenen Platz dazu
           und wuerde den Wert ueberdecken. Der Platz wird stattdessen als
           letztes Listenelement reserviert (Spacer am Ende), und das wirkt
           unabhaengig davon, was `autoCentering` gerade tut. */
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        /* ======================================================================
           WAEHREND EINER AUFNAHME STEHEN SCHLAEGER UND SCHWUNG GANZ OBEN (43)
           ----------------------------------------------------------------------
           Sie sassen bis (42) unten im Streifen und waren dort nicht lesbar
           (siehe die Rechnung an der Schlagzeile). HIER hat eine Zeile die
           volle Breite, weil der Kreis auf halber Hoehe am breitesten ist.
           GANZ OBEN und nicht bei den Score-Feldern: Man waehlt den Schlaeger
           in den Sekunden nach „Schlag hier", nicht nach dem Einlochen. Die
           Liste springt beim Aufnahmestart nach oben (siehe LaunchedEffect
           weiter unten), also steht die Zeile genau dann unter dem Daumen,
           wenn man sie braucht.
           NUR WAEHREND DER AUFNAHME: Ausserhalb waeren es zwei Zeilen ohne
           Bezug, die man achtzehnmal je Runde ueberscrollt. */
        if (recActive) {
            item {
                SelectRow("⛳ Schläger", recClubName) { onShotClub() }
            }
            /* Die PWA lernt Schlaegerlaengen NUR aus vollen Schwuengen; ohne
               diese Angabe zoege ein halber Wedge (55 statt 92 m) die gelernte
               Laenge nach unten. Ein Tipp schaltet weiter:
               Voll -> 3/4 -> Halb -> Punch -> Voll. */
            item {
                SelectRow("↗ Schwung", recSwingName ?: "Voll") { onShotSwing() }
            }
        }

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                /* LOCHPFEILE IN DER KOPFZEILE (2026-08-26 (38)) — bis (37)
                   sassen sie auf Seite 0. Aussen, wo der Daumen ohnehin
                   liegt; die Mitte behaelt ihre volle Breite.
                   AM RAND AUSGEGRAUT STATT ENTFERNT: Eine Schaltflaeche, die
                   verschwindet, laesst den Daumen ins Leere greifen und die
                   Zeile springen — auf einem runden Display faellt das
                   doppelt auf. (Gleiche Begruendung wie 2026-08-24.) */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "\u25C0",
                        fontSize = 20.sp,
                        color = if (hasPrev) GoldText else GoldText.copy(alpha = 0.25f),
                        modifier = Modifier
                            .clickable(enabled = hasPrev) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHolePrev?.invoke()
                            }
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                    Text(
                        "Loch ${hd.hole} · Par ${hd.par}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.title3,
                        color = PineText,
                        maxLines = 1
                    )
                    Text(
                        "\u25B6",
                        fontSize = 20.sp,
                        color = if (hasNext) GoldText else GoldText.copy(alpha = 0.25f),
                        modifier = Modifier
                            .clickable(enabled = hasNext) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHoleNext?.invoke()
                            }
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
                val opTxt =
                    if (thru == 0) "±0"
                    else if (toPar == 0) "E"
                    else if (toPar > 0) "+$toPar"
                    else "$toPar"
                Text(
                    "über Par $opTxt · $thru Loch",
                    fontSize = 12.sp,
                    color =
                        if (toPar > 0) RedC
                        else if (toPar < 0) PineText
                        else GoldText
                )
                /* WAEHREND EINER AUFNAHME STEHT HIER DIE GELAUFENE STRECKE (44).
                   Der runde Knopf oben links ist zu klein fuer eine Zahl; die
                   Kopfzeile hat den Platz, und man schaut dort ohnehin hin.
                   Sonst wie bisher die GPS-Guete — die einzige
                   Qualitaetsgroesse der Uhr. */
                Text(
                    if (recActive)
                        "■ ${recDist ?: 0} m" +
                                (recClubName?.let { " · $it" } ?: " · Schläger?")
                    else
                        (gpsAcc?.let { "±$it m" } ?: (gpsErr ?: "warte auf GPS…")) +
                                (if (syncStale) syncAge?.let { " · ⟳$it" } ?: "" else ""),
                    fontSize = 11.sp,
                    color =
                        if (recActive) PineText
                        else if (syncStale) RedC
                        else if (gpsAcc == null || gpsAcc >= 10) RedC
                        else InkFaint,
                    maxLines = 1
                )
            }
        }

        // Reihenfolge folgt dem Ablauf des Lochs: erst was am Abschlag
        // passiert ist, dann der Approach, dann das Ergebnis.
        if (opts != null) {

            if (opts.teeResults.isNotEmpty()) {
                item {
                    if (hd.par >= 4) {
                        SelectRow("Tee-Ergebnis", entry.tee) {
                            onPick(
                                "Tee-Ergebnis",
                                opts.teeResults,
                                entry.tee
                            ) { e, s -> e.copy(tee = s) }
                        }
                    } else {
                        // Par 3 hat keinen Tee-Schlag ins Fairway. Die PWA
                        // zeigt hier ein deaktiviertes Feld — die Zeile ganz
                        // wegzulassen sieht aus, als fehle etwas.
                        SelectRow("Tee-Ergebnis", "– (Par 3)") { }
                    }
                }
            }

            /* TEE-SCHLAEGER DIREKT UNTER DEM TEE-ERGEBNIS (36, Wunsch vom
               26.08.): Er stand auf Seite 3 und blieb dort — wie zuvor die
               1.-Putt-Distanz — auf der Runde regelmaessig leer. Eingetragen
               wird er im selben Moment wie das Tee-Ergebnis, also gehoeren
               beide untereinander. Auf Seite 3 ist er dafuer RAUS (ein Feld
               an zwei Orten hiesse zwei Wahrheiten). Auch am Par 3 sinnvoll:
               dort IST der Tee-Schlag der Approach. */
            item {
                SelectRow("Tee-Schläger", entry.club) {
                    onPick(
                        "Tee-Schläger",
                        opts.teeClubs,
                        entry.club
                    ) { e, s -> e.copy(club = s) }
                }
            }


            /* LAGE DIREKT NACH DEM TEE-ERGEBNIS (2026-08-14 (6)).
               Sie ist das ERSTE, was am Ball feststeht — noch bevor man
               Entfernung und Schlaeger waehlt. Vorher stand sie hinter beiden
               und wurde deshalb oft nachgetragen oder vergessen.
               Fachlich haengt daran mehr als es aussieht: Die SG-Rechnung
               nimmt ohne Angabe FAIRWAY an. Ein Approach aus dem Rough zaehlt
               damit gegen die Annaeherung statt gegen die Lage — die Kategorie
               sieht schlechter aus, als sie ist. */
            item {
                SelectRow("Approach-Lage ⭐", entry.lie) {
                    onPick(
                        "Approach-Lage",
                        opts.approachLies,
                        entry.lie
                    ) { e, s -> e.copy(lie = s) }
                }
            }

            item {
                SelectRow("Approach-Distanz ⭐", entry.appr) {
                    onPick(
                        "Approach-Distanz",
                        opts.approachBuckets,
                        entry.appr
                    ) { e, s -> e.copy(appr = s) }
                }
            }

            /* Approach-SCHLAEGER direkt nach der Approach-Distanz: beides
               traegt man im selben Moment ein, wenn man am Ball steht. */
            item {
                SelectRow("Approach-Schläger ⭐", entry.apprClub) {
                    onPick(
                        "Approach-Schläger",
                        clubNames,
                        entry.apprClub
                    ) { e, s -> e.copy(apprClub = s) }
                }
            }

            /* WICHTIG-Block: nicht auf Seite 3 verstecken. Der Approach-Fehler
               ist das trainingsrelevanteste Feld ueberhaupt ("systematisch zu
               kurz"). Beide werden direkt nach dem Approach eingetragen —
               dort, wo man ohnehin gerade ist. */

            item {
                SelectRow("Approach-Fehler ⭐", entry.apprMiss) {
                    onPick(
                        "Approach-Fehler",
                        opts.approachMiss,
                        entry.apprMiss
                    ) { e, s -> e.copy(apprMiss = s) }
                }
            }
        }

        // Entspricht "Pin-Dist. nach Approach (m)" der PWA. Dort ein freies
        // Zahlenfeld — auf der Uhr nicht tippbar, deshalb eine Auswahl.
        // Feintritt bis 20 m (dort entscheidet sich Up&Down), darüber grob.
        item {
            SelectRow("Rest z. Fahne nach Appr.", entry.distToPin?.let { "$it m" }) {
                onPick(
                    "Rest z. Fahne nach Appr. (m)",
                    DIST_TO_PIN_CHOICES,
                    entry.distToPin?.let { "$it m" }
                ) { e, sel ->
                    e.copy(
                        distToPin = sel?.removeSuffix(" m")?.trim()?.toIntOrNull()
                    )
                }
            }
        }



        /* Die 1.-Putt-Distanz TRAEGT die Strokes-Gained-Rechnung: ohne sie
           liefert sgHole() nur den Gesamtwert, weil sich Putten, Kurzspiel und
           Annaeherung nicht trennen lassen. Auf Seite 3 (Details) blieb sie auf
           der Runde regelmaessig leer — deshalb steht sie hier.
           Ihre Nachbarn „Shortsided" und „1. Putt ging …" sind mit
           2026-08-14 (5) nach Seite 3 gewandert: Sie beschreiben das Loch,
           tragen die Rechnung aber nicht. */
        if (opts != null) {

            item {
                SelectRow("Länge des 1. Putts ⭐", entry.firstPutt) {
                    onPick(
                        "Länge des 1. Putts",
                        opts.firstPuttDist,
                        entry.firstPutt
                    ) { e, s -> e.copy(firstPutt = s) }
                }
            }

            /* PUTT-DIAGNOSE. Steht direkt unter der Puttlaenge, weil man beide
               im selben Moment eintraegt — beim Verlassen des Gruens.
               Zusammen beantworten sie die einzige Frage, die beim Putten
               zaehlt: WORAN liegt es? Ueberwiegend kurz heisst
               Laengenkontrolle, systematisch eine Seite heisst Startlinie —
               zwei voellig verschiedene Uebungen. Und der Rest nach dem ersten
               Putt trennt Dreiputts nach Ursache: langer Rest = Lag,
               kurzer Rest = Kurzputt. */

            item {
                SelectRow("Rest nach 1. Putt", entry.puttRest) {
                    onPick(
                        "Rest nach 1. Putt",
                        opts.puttRestOpts,
                        entry.puttRest
                    ) { e, s -> e.copy(puttRest = s) }
                }
            }
        }



        /* SCORE UND PUTTS GANZ UNTEN (2026-08-11).
           Sie stehen jetzt am Ende der Seite, weil sie in der Reihenfolge des
           SPIELS zuletzt entstehen: Tee, Annaeherung, Kurzspiel, Putts — und
           erst dann steht der Score fest. Vorher standen sie mittendrin, und
           man musste beim Eintragen zwischen den Bloecken hin und her.
           Der Weg nach unten ist kein Nachteil: Wer NUR den Score erfassen
           will, scrollt einmal ans Ende und findet dort beides beieinander. */
        // Score per Stepper. Der Startwert beim ersten Tipp ist Par —
        // das ist der häufigste Fall und spart Klicks in beide Richtungen.
        item {
            Stepper(
                "Score ⭐",
                entry.score?.toString() ?: "–",
                { onScore(-1) },
                { onScore(1) },
                valueColor = when {
                    entry.score == null -> InkC
                    entry.score < hd.par -> PineText
                    entry.score == hd.par -> GoldText
                    else -> RedC
                }
            )
        }

        // Relation zu Par direkt unter dem Score — sonst muss man
        // im Kopf rechnen, während man am Grün steht.
        item {
            val d = entry.score?.let { it - hd.par }
            Text(
                when {
                    d == null -> "noch kein Score"
                    d < -1 -> "$d unter Par"
                    d == -1 -> "Birdie"
                    d == 0 -> "Par"
                    d == 1 -> "Bogey"
                    d == 2 -> "Doppelbogey"
                    else -> "+$d"
                },
                fontSize = 12.sp,
                color =
                    if (d == null) InkFaint
                    else if (d < 0) PineText
                    else if (d == 0) GoldText
                    else RedC
            )
        }

        item {
            Stepper(
                "Putts ⭐",
                (entry.putts ?: 2).toString(),
                { onPutts(-1) },
                { onPutts(1) }
            )
        }

        /* STRAFSCHLAEGE DIREKT UNTER DEN PUTTS (2026-08-14 (5)).
           Sie bleiben auf Seite 2 — in der SG-Rechnung sind sie eine EIGENE
           Kategorie und werden aus dem kurzen Spiel herausgerechnet; wer sie
           in den Details vergisst, verzerrt das Kurzspiel dauerhaft.
           Die Stelle aendert sich aber: Frueher standen sie WEIT OBEN, noch vor
           dem Kurzspiel — man traegt sie aber am Ende des Lochs ein, zusammen
           mit Score und Putts. Jetzt stehen die drei Zahlen beieinander, die
           das Loch abschliessen. */
        item {
            Stepper(
                "Strafschläge",
                entry.penN?.toString() ?: "0",
                { onPen(-1) },
                { onPen(1) }
            )
        }

        /* MITSPIELER: je Zeile NUR der Endscore — bewusst nichts weiter.
           SEIT (39) haengen die Zeilen an den PLAETZEN, nicht an den Namen
           (Begruendung am `object Mitspieler`). Beschriftung ist der Name des
           Handys, ersatzweise „Mitspieler 2".
           Auswahl statt Stepper: ein Tipp, ein Raster, fertig — und "–"
           loescht, ohne dass ein Stepper bei 1 haengenbleibt. */
        if (mitspielerNamen.isNotEmpty()) {
            mitspielerNamen.take(3).forEachIndexed { mi, name -> item {
                val wert = when (mi) { 0 -> entry.msc1; 1 -> entry.msc2; else -> entry.msc3 }
                SelectRow("⛳ $name", wert?.toString()) {
                    onPick(
                        "Score $name",
                        MITSPIELER_SCORES,
                        wert?.toString()
                    ) { e, sel ->
                        val v = sel?.toIntOrNull()
                        when (mi) {
                            0 -> e.copy(msc1 = v)
                            1 -> e.copy(msc2 = v)
                            else -> e.copy(msc3 = v)
                        }
                    }
                }
            } }
        }


        // Lochwechsel: bewusst als Buttons, nicht als Wischgeste.
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactButton(
                    onClick = onPrev,
                    enabled = idx > 0,
                    colors = ButtonDefaults.secondaryButtonColors()
                ) { Text("‹", fontSize = 20.sp) }

                Text(
                    "${idx + 1} / $total",
                    style = MaterialTheme.typography.caption1,
                    color = InkC
                )

                CompactButton(
                    onClick = onNext,
                    enabled = idx < total - 1,
                    colors = ButtonDefaults.secondaryButtonColors()
                ) { Text("›", fontSize = 20.sp) }
            }
        }

        item { SectionLabel("Runde") }

        /* KEIN „+ MITSPIELER"-CHIP MEHR (2026-08-27 (42)). (39) liess die Uhr
           hier Plaetze eroeffnen; das Handy fuehrt seit (42) allein. Wer einen
           Mitspieler erfassen will, legt ihn in der PWA an — die Zeile steht
           dann beim naechsten Abgleich auf der Uhr.
           NICHT WIEDER EINBAUEN, ohne die Meldung vom 27.08. zu lesen: Eine
           auf der Uhr gepflegte Platzzahl wuchs, sank aber nie, und danach
           standen dort mehr Mitspieler als in der PWA. */

        // Letzten gemessenen Schlag zuruecknehmen. Nur sichtbar, wenn es
        // ueberhaupt etwas zurueckzunehmen gibt — ein Knopf ohne Wirkung ist
        // eine Falle.
        if (shotCount > 0) {
            item {
                Chip(
                    onClick = {
                        if (confirmUndo) {
                            confirmUndo = false
                            onShotUndo()
                        } else {
                            confirmUndo = true
                        }
                    },
                    label = {
                        Text(
                            if (confirmUndo) "Wirklich löschen?"
                            else "↶ letzten Schlag ($shotCount)",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    /* 48 dp — Wear-Mindestmass (2026-08-14 (7)). Ausgerechnet
                       eine ZERSTOERENDE Aktion war das kleinste Ziel der Seite. */
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                )
            }
        }

        /* KEIN „SICHERN & ABSCHLIESSEN" MEHR (2026-08-27 (44)).
           VORGABE VOM 27.08.: Sichern, Abschliessen und Verwerfen passieren
           AUSSCHLIESSLICH am Handy.
           Der Fortschritt bleibt aber sichtbar — er war der eigentliche Grund,
           warum man hier hinunterscrollt. Er steht jetzt als reine Auskunft
           da, ohne Knopf dahinter. */
        item {
            Text(
                "$thru von $total Löchern erfasst\nAbschließen am Handy",
                fontSize = 11.sp,
                color = InkFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }

        // Rückweg zur Übersicht — die Runde läuft im Service weiter
        // und steht dort als „Fortsetzen" bereit.
        item {
            Chip(
                onClick = onHome,
                label = { Text("‹ Übersicht", fontSize = 12.sp, maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                // 48 dp — Wear-Mindestmass, siehe oben (2026-08-14 (7))
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            )
        }

        item {
            Text(
                course.name,
                style = MaterialTheme.typography.caption2,
                color = InkFaint,
                maxLines = 1
            )
        }

        if (status.isNotEmpty()) {
            item {
                Text(
                    status,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption2,
                    color = InkFaint
                )
            }
        }

        /* KEINE FREIHALTUNG MEHR NOETIG (44): Der Schlag-Knopf sitzt oben
           links ueber der Liste und verdeckt unten nichts. Eine kleine
           Reserve bleibt, damit die letzte Zeile nicht am Rand klebt. */
        item { Spacer(Modifier.height(12.dp)) }
    }

        /* ======================================================================
           RUNDER KNOPF OBEN LINKS (2026-08-27 (44))
           ----------------------------------------------------------------------
           (43) hatte den Knopf unten breit gemacht — lesbar, aber er nahm dort
           78 dp Freihaltung und lag im Weg. VORGABE VOM 27.08.: „oben links
           als runder Button, da behindert er am wenigsten."
           DIE GEOMETRIE STIMMT DORT BESSER, als sie klingt: Ein Kreis von
           48 dp, dessen Mittelpunkt rund 0,26 der Bildbreite von links und
           0,24 der Hoehe von oben liegt, hat vom Bildmittelpunkt etwa 0,74 r
           Abstand und bleibt mit seinem Rand innerhalb von 0,85 r — also
           vollstaendig auf der Scheibe. Ein RUNDER Knopf ist hier genau
           richtig: Er hat keine Ecken, die ueber die Rundung hinausragen
           koennen, und das war der Fehler von (38).
           ER LIEGT UEBER DER LISTE. Das ist gewollt und der Grund, warum er
           „am wenigsten behindert": Er kostet KEINE Freihaltung mehr (die
           78 dp am Listenende sind wieder frei), und die Liste laeuft unter
           ihm durch. Im Ruhezustand steht das erste Listenelement wegen
           `autoCentering` ohnehin tiefer.
           IM RUHEZUSTAND NUR EIN ZEICHEN, WAEHREND DER AUFNAHME DIE ZAHL (45):
           grau „📐" heisst bereit; gruen mit der gelaufenen Strecke heisst,
           dass gemessen wird. Die Farbe traegt den Zustand, die Zahl den
           Messwert — eine Flaeche, zwei Auskuenfte. Die Strecke steht
           zusaetzlich in der Kopfzeile der Liste, aber dorthin sieht man beim
           Laufen nicht.
           LANGDRUCK BRICHT AB, mit Vibration — unveraendert aus (43). */
        /* DIESELBE SCHWELLE WIE `FixQuality.usable` (15 m) — zwei Zahlen fuer
           dieselbe Frage laufen auseinander, und dann zeigt der Knopf „bereit",
           waehrend die Messung ablehnt. Das waere schlimmer als gar keine
           Anzeige. `gpsAcc` kommt aus demselben Live-Zustand, den `recBegin`
           prueft; das Alter deckt der Live-Zustand selbst ab (er setzt `acc`
           auf null, wenn nichts mehr kommt). */
        val gpsBereit = (gpsAcc != null && gpsAcc <= FixQuality.MAX_ACC.toInt())
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 18.dp)
                .size(48.dp)
                .clip(CircleShape)
                /* ==============================================================
                   DER KNOPF ZEIGT, OB ER KANN (2026-08-28 (51))
                   --------------------------------------------------------------
                   ZWEITE MELDUNG ZUM SELBEN THEMA: „Der Button startet das
                   Schlagtracken nicht." Im mitgeschickten Protokoll steht die
                   Antwort woertlich — „Uhr meldet seit ueber 90 s keine
                   Position". OHNE BRAUCHBAREN FIX LEHNT `recBegin` AB
                   (`FixQuality.usable`: hoechstens 15 m Streuung und nicht zu
                   alt), setzt eine Statuszeile und vibriert kurz.
                   BEIDES REICHT NICHT. Die Statuszeile steht auf einer Seite,
                   die man beim Ball nicht liest, und ein kurzer Stups im
                   Gehen geht unter. Aus Sicht des Benutzers passiert nichts —
                   und er tippt weiter, statt zu warten oder weiterzugehen.
                   DIE PRECONDITION GEHOERT AN DEN KNOPF, NICHT IN DIE
                   FEHLERMELDUNG. Ein Knopf, der nicht kann, soll das VORHER
                   zeigen, nicht hinterher melden. Drei Zustaende:
                     bereit        dunkel, „📐"
                     nicht bereit  gedaempft mit goldenem Rand, „GPS"
                     Aufnahme      gruen, die gelaufenen Meter
                   ANTIPPEN BLEIBT ERLAUBT, auch wenn er nicht bereit ist: Die
                   Ablehnung sagt dann, WORAN es liegt (Genauigkeit in Metern),
                   und das ist mehr wert als ein gesperrter Knopf, der gar
                   nichts erklaert. Wer gesperrt wird, weiss nicht, warum. */
                .background(
                    when {
                        recActive -> PineText
                        !gpsBereit -> Color(0xFF3A3226)
                        else -> Color(0xFF2A2A2A)
                    }
                )
                .then(
                    if (!recActive && !gpsBereit)
                        Modifier.border(2.dp, GoldText, CircleShape)
                    else Modifier
                )
                .combinedClickable(
                    onClick = { if (recActive) onShotStop() else onShotBegin() },
                    onLongClick = {
                        if (recActive) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShotCancel()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            /* ==============================================================
               WAEHREND DER AUFNAHME STEHT DIE METERZAHL IM KNOPF (45)
               --------------------------------------------------------------
               VORGABE VOM 27.08.: „Bei einem Schlagtracken soll in dem Button
               die Meterzahl angezeigt werden und nicht das Stopp-Rechteck."
               Richtig, und (44) hatte hier zu knapp gedacht: Das „■" sagte
               nur, DASS eine Aufnahme laeuft — das sagt die gruene Flaeche
               ohnehin, und zwar aus dem Augenwinkel. Die Zahl dagegen ist die
               einzige Auskunft, die man im Gehen wirklich will, und sie stand
               in der Kopfzeile, also dort, wo man beim Laufen nicht hinsieht.
               EINE FLAECHE, ZWEI AUSSAGEN: Farbe = Zustand, Zahl = Messwert.
               Ohne Einheit — bei 48 dp ist jedes zusaetzliche Zeichen eine
               Schriftgroesse weniger, und dass Meter gemeint sind, weiss man.
               DIE SCHRIFT SCHRUMPFT MIT DER STELLENZAHL, damit auch ein Drive
               ueber 200 m ganz dasteht: bis 99 gross, ab 100 kleiner. Ein
               abgeschnittener Messwert waere schlimmer als gar keiner. */
            val meter = recDist ?: 0
            Text(
                if (recActive) meter.toString() else if (gpsBereit) "📐" else "GPS",
                fontSize = when {
                    !recActive && !gpsBereit -> 13.sp
                    !recActive -> 20.sp
                    meter >= 100 -> 15.sp
                    else -> 18.sp
                },
                fontWeight = if (recActive) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    recActive -> Color.Black
                    !gpsBereit -> GoldText
                    else -> GoldText
                },
                maxLines = 1
            )
        }
    }
}

// ============================================================
//  SEITE 1 — DETAILS: Trainingsdaten, die beim Spielen warten
//  können. Alles optional, nichts blockiert den Rundenablauf.
// ============================================================

@Composable
private fun DetailPage(
    active: Boolean,
    listState: ScalingLazyListState,
    hd: HoleDef,
    entry: HoleEntry,
    opts: Options?,
    /* `autoShot`/`onAutoShot` sind mit (38) ganz raus. (35) hatte die
       Automatik entfernt und den Erklaertext stehenlassen — er beschrieb
       einen Knopf auf einer Seite, die es nicht mehr gibt. Ein Text ueber
       eine Funktion, die es nicht gibt, ist schlimmer als kein Text. */
    onPen: (Int) -> Unit,
    onBunkerN: (Int) -> Unit,
    onUd: () -> Unit,
    onSs: () -> Unit,
    onRec: () -> Unit,
    onPick: (
        String,
        List<String>,
        String?,
        (HoleEntry, String?) -> HoleEntry
    ) -> Unit
) {

    /* `quality` zaehlt hier NICHT mehr mit: Es ist auf der Uhr nicht mehr
       eingebbar (siehe unten), und ein Zaehler, der etwas mitzaehlt, wozu es
       keine Eingabe gibt, sendet den Nutzer auf die Suche. */
    val detailCount = listOf<Any?>(
        entry.gir, entry.firstPutt, entry.puttMiss, entry.puttRest, entry.kurzseitig, entry.club,
        entry.lie, entry.bunkerN, entry.b1, entry.penType,
        entry.ud, entry.ss, entry.recovery
    ).count { it != null }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(rotaryScrollModifier(listState, active)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Details · Loch ${hd.hole}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.title3,
                    color = PineText
                )
                Text(
                    if (detailCount > 0) "$detailCount ausgefüllt"
                    else "alles optional",
                    fontSize = 12.sp,
                    color = InkFaint
                )
            }
        }

        /* QUALITY-EINGABE ENTFERNT (2026-08-10).
           Das Feld trug KEINE eigene Information: In der SG-Rechnung dient es
           nur als DRITTER Rueckfall fuer die 1.-Putt-Distanz
           (erfasste 1.-Putt-Distanz -> bei GIR die Restdistanz -> quality).
           Auf einem Gruen-in-Regulation-Loch ist „Abstand nach dem Approach"
           und „Laenge des ersten Putts" ohnehin dieselbe Zahl — man tippte sie
           auf der Uhr zweimal ein, auf einem Bildschirm, auf dem jeder Tipp
           zaehlt.

           Das FELD bleibt im Datenmodell und wird weiterhin gelesen und
           geschrieben: Altrunden enthalten es, und der Rueckfall in sgHole soll
           dafuer weiter greifen. Nur die Eingabe auf der Uhr entfaellt.
           Gepflegt wird es bei Bedarf am Handy. */

        /* VON SEITE 2 HIERHER (2026-08-14 (5)).
           Seite 2 ist der Weg durch das Loch: Tee-Ergebnis, Annaeherung,
           Kurzspiel, Score, Putts, Strafschlaege. Diese drei Felder gehoeren
           fachlich dazu, werden aber selten gepflegt — und auf einem runden
           Display kostet jede Zeile, die man ueberscrollt, die Zeilen darunter.
           Sie stehen jetzt hier, wo das Optionale hingehoert.
           WAS BLEIBT AUF SEITE 2: alles, was die Strokes-Gained-Rechnung
           TRAEGT — die 1.-Putt-Distanz, Approach-Distanz und -Lage. Ohne die
           laesst sich Putten, Kurzspiel und Annaeherung nicht trennen. */
        /* "Tee & Kurzspiel" -> "Kurzspiel" (36): Der Tee-Schlaeger ist nach
           Seite 2 unter das Tee-Ergebnis gezogen — Begruendung dort. */
        item { SectionLabel("Kurzspiel") }

        if (opts != null) {
            item {
                SelectRow("Shortsided", entry.kurzseitig) {
                    onPick(
                        "Shortsided",
                        opts.kurzseitigOpts,
                        entry.kurzseitig
                    ) { e, s -> e.copy(kurzseitig = s) }
                }
            }

            /* Putt-Diagnose: Ueberwiegend kurz heisst Laengenkontrolle,
               systematisch eine Seite heisst Startlinie — zwei voellig
               verschiedene Uebungen. Die LAENGE des ersten Putts bleibt auf
               Seite 2, sie traegt die SG-Rechnung. */
            item {
                SelectRow("1. Putt ging …", entry.puttMiss) {
                    onPick(
                        "1. Putt ging …",
                        opts.puttMissOpts,
                        entry.puttMiss
                    ) { e, s -> e.copy(puttMiss = s) }
                }
            }
        }

        item { SectionLabel("Bunker & Strafen") }

        item {
            Stepper(
                "Bunker Anzahl",
                entry.bunkerN?.toString() ?: "–",
                { onBunkerN(-1) },
                { onBunkerN(1) }
            )
        }

        if (opts != null) {
            item {
                SelectRow("Bunker Typ", entry.b1) {
                    onPick(
                        "Bunker Typ",
                        opts.bunkerTypes,
                        entry.b1
                    ) { e, s -> e.copy(b1 = s) }
                }
            }
        }


        if (opts != null) {
            item {
                SelectRow("Penalty Typ", entry.penType) {
                    onPick(
                        "Penalty Typ",
                        opts.penaltyTypes,
                        entry.penType
                    ) { e, s -> e.copy(penType = s) }
                }
            }
        }

        /* Up&Down und Sand Save leitet die PWA aus Score, Putts, Par und
           Bunkerzahl ab (holeUpDown/holeSandSave). Hier stehen sie nur noch
           als Korrektur fuer die Faelle, in denen die Regel danebenliegt —
           deshalb ganz unten und mit Hinweis. */
        item { SectionLabel("Meist berechnet") }

        item {
            Text(
                "Up & Down und Sand Save rechnet das Handy aus Score, Putts und " +
                        "Bunkerzahl. Nur eintragen, wenn das nicht stimmt.",
                fontSize = 12.sp,
                color = InkFaint,
                textAlign = TextAlign.Center
            )
        }

        item { ToggleRow("Up & Down", entry.ud, onUd) }
        item { ToggleRow("Sand Save", entry.ss, onSs) }
        item { ToggleRow("Recovery", entry.recovery, onRec) }

        /* ==========================================================================
           FEHLERPROTOKOLL AM ENDE DER DETAILSEITE (2026-08-16 (8))
           --------------------------------------------------------------------------
           AM ENDE, nicht oben: Man braucht es selten, und wenn, dann sucht man
           es gezielt. Oben stuende es jeder Eingabe im Weg.
           NUR WENN ES EINTRAEGE GIBT — ein leeres Protokoll ist eine Zeile
           Rauschen; sein Fehlen ist die gute Nachricht. */
        fehlerBlock()

        item { Spacer(Modifier.height(12.dp)) }
    }
}

/* ==========================================================================
   FEHLERPROTOKOLL ALS BAUSTEIN (2026-08-16 (13))
   --------------------------------------------------------------------------
   Steht jetzt an ZWEI Stellen: unten auf der Detailseite einer Runde UND unten
   auf dem Startbildschirm. Der Startbildschirm ist der wichtigere Ort — nach
   einem Absturz landet man dort, und genau dann will man wissen, was passiert
   ist, ohne erst eine Runde zu starten.
   EINE Funktion fuer beide, weil dieselbe Sache zweimal gebaut auseinander
   laeuft (die Scorekarte in der PWA war genau dieser Fall).
   `ScalingLazyListScope`-Erweiterung statt @Composable: Eintraege muessen
   EINZELNE `items` sein, sonst scrollt der ganze Block als ein Stueck und wird
   auf einem runden Display unlesbar. */
private fun ScalingLazyListScope.fehlerBlock(zeigeLeer: Boolean = false) {
    val eintraege = Fehler.liste
    if (eintraege.isEmpty()) {
        if (zeigeLeer) {
            item {
                Text(
                    "Fehlerprotokoll: keine Meldungen",
                    fontSize = 10.sp, color = InkFaint, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)
                )
            }
        }
        return
    }
    /* ==================================================================
       DIAGNOSE VOR DEM PROTOKOLL (2026-08-25 (11))
       --------------------------------------------------------------------
       Der Zustandsabzug steht OBEN, nicht unten: Er beantwortet die meisten
       Fragen in einer Zeile, und wer scrollen muss, liest ihn nicht.
       Der ZEITVERSATZ steht zuerst und in Warnfarbe, sobald er ueber 30 s
       liegt — der ganze Abgleich vergleicht Zeitstempel, ein Versatz
       verfaelscht ihn, und das sieht aus wie „das andere Geraet wird
       ignoriert". Genau der Fehler, den ich viermal woanders gesucht habe. */
    item { SectionLabel("Diagnose") }
    item {
        val v = Diagnose.versatzMs
        Text(
            if (v == null) "Zeitversatz: noch nicht gemessen"
            else "Zeitversatz: ${v / 1000} s" +
                    (if (kotlin.math.abs(v) > 30_000) "  ⚠ verfälscht den Abgleich" else "  ✓"),
            fontSize = 11.sp,
            color = if (v != null && kotlin.math.abs(v) > 30_000) RedC else InkC,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
    item {
        Text(
            Diagnose.abzug(),
            fontSize = 10.sp, color = InkFaint, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
    if (Diagnose.syncVerlauf.isNotEmpty()) {
        item { SectionLabel("Letzte Abgleiche") }
        items(Diagnose.syncVerlauf) { z ->
            Text(
                z,
                fontSize = 10.sp, color = InkFaint,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
    }
    item {
        /* Ein Knopf, der Abzug und Verlauf INS PROTOKOLL schreibt — von dort
           nehmen sie den Weg zum Handy. Eine Diagnose, die man nur auf dem
           runden Display lesen kann, wird nicht gelesen. */
        val bereich = rememberCoroutineScope()
        Chip(
            onClick = {
                Diagnose.inProtokoll()
                /* Der Selbsttest braucht das Netz, also im Hintergrund — und
                   sein Ergebnis geht ins Protokoll, damit es das Handy
                   erreicht. Eine Diagnose, die man nur auf dem runden Display
                   lesen kann, wird nicht gelesen. */
                bereich.launch {
                    val z = try { withContext(Dispatchers.IO) { Diagnose.selbsttest() } }
                    catch (e: Exception) {
                        if (e.istAbbruch()) throw e
                        listOf("Selbsttest abgebrochen: ${e.javaClass.simpleName}")
                    }
                    /* Der Bericht steht separat — nicht im Ringpuffer, der
                       gleichzeitig von Fehlern und vom Aufraeumen bewegt wird
                       (2026-08-25 (13)). */
                    Diagnose.berichtSetzen(z)
                    /* ==================================================
                       SOFORT SENDEN (2026-08-25 (12))
                       ----------------------------------------------------
                       GEMELDET: „Die Ergebnisse kommen erst, wenn ich eine
                       Runde starte." Genau so war es: Geschrieben wurde in
                       den Puffer, und der reiste nur mit dem Rundenentwurf
                       oder im Fuenf-Minuten-Takt.
                       Wer auf Diagnose drueckt, will die Antwort JETZT —
                       meistens, weil gerade etwas klemmt. Auf die naechste
                       Runde zu warten ist genau dann das Falsche.
                       `logPut` schreibt ohnehin nur bei Aenderung; hier hat
                       sich gerade etwas geaendert, also geht es raus. */
                    val ok = try { withContext(Dispatchers.IO) { Net.logPut() } }
                    catch (e: Exception) {
                        if (e.istAbbruch()) throw e
                        false
                    }
                    Fehler.warn("Diagnose",
                        if (ok) "an das Handy gesendet" else "Senden fehlgeschlagen — bleibt hier")
                }
            },
            label = { Text("Diagnose + Selbsttest", fontSize = 12.sp) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
    }
    item { SectionLabel("Fehlerprotokoll") }
    item {
        Text(
            "Letzte " + eintraege.size + " Meldungen · bleiben über Neustarts erhalten",
            fontSize = 10.sp, color = InkFaint, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
    items(eintraege) { z ->
        /* KEINE Kuerzung (maxLines): Der Aufrufstapel steht am ENDE der Zeile —
           genau der Teil, wegen dem man hinsieht. Lieber vier Zeilen umbrechen
           als die Antwort abschneiden. */
        Text(
            z,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            /* Drei Stufen sichtbar: Absturz rot, Warnung gold, Rest ruhig.
               Ohne Unterscheidung liest man dreissig gleich aussehende Zeilen
               und findet die eine nicht (2026-08-16 (15)). */
            color = when {
                z.contains("ABSTURZ") -> RedC
                z.contains("⚠") -> GoldText
                else -> InkFaint
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
    item {
        Chip(
            onClick = { Fehler.clear() },
            label = { Text("Protokoll leeren", fontSize = 12.sp, maxLines = 1) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        )
    }
}

@Composable
private fun SectionLabel(
    t: String
) {

    // Kleine Zierlinien links/rechts: Abschnitte sind beim schnellen
    // Scrollen deutlich leichter zu erkennen als reine Text-Labels.
    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(6.dp),
        modifier =
            Modifier.padding(top = 8.dp)
    ) {

        Box(
            Modifier
                .width(16.dp)
                .height(1.dp)
                .background(GoldDeep)
        )

        Text(
            t.uppercase(),
            style =
                MaterialTheme.typography.caption2,
            color = GoldText,
            fontWeight =
                FontWeight.Bold
        )

        Box(
            Modifier
                .width(16.dp)
                .height(1.dp)
                .background(GoldDeep)
        )
    }
}

@Composable
private fun SelectRow(
    label: String,
    value: String?,
    onClick: () -> Unit
) {

    Chip(
        onClick = onClick,
        label = {
            Text(label)
        },
        secondaryLabel = {
            Text(
                value ?: "—",
                maxLines = 1
            )
        },
        colors =
            if (value != null)
                ChipDefaults.primaryChipColors()
            else
                ChipDefaults.secondaryChipColors(),
        modifier =
            Modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleRow(
    label: String,
    state: Boolean?,
    onClick: () -> Unit
) {

    Chip(
        onClick = onClick,
        label = {
            Text(label)
        },
        secondaryLabel = {
            Text(
                jnLabel(state)
            )
        },
        colors =
            if (state == true)
                ChipDefaults.primaryChipColors()
            else
                ChipDefaults.secondaryChipColors(),
        modifier =
            Modifier.fillMaxWidth()
    )
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    valueColor: Color = InkC
) {

    val haptics = LocalHapticFeedback.current

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
    ) {

        Text(
            label,
            style =
                MaterialTheme.typography.caption1,
            color = InkFaint
        )

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Button(
                onClick = {
                    haptics.performHapticFeedback(
                        HapticFeedbackType.LongPress
                    )
                    onMinus()
                },
                colors =
                    ButtonDefaults.secondaryButtonColors()
            ) {
                Text(
                    "−",
                    fontSize = 22.sp
                )
            }

            Text(
                value,
                fontSize = 24.sp,
                fontWeight =
                    FontWeight.Bold,
                color = valueColor,
                modifier =
                    Modifier.widthIn(
                        min = 46.dp
                    ),
                textAlign =
                    TextAlign.Center
            )

            Button(
                onClick = {
                    haptics.performHapticFeedback(
                        HapticFeedbackType.LongPress
                    )
                    onPlus()
                },
                colors =
                    ButtonDefaults.primaryButtonColors()
            ) {
                Text(
                    "+",
                    fontSize = 22.sp
                )
            }
        }
    }
}