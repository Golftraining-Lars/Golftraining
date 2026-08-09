package de.lars.golfwatch.presentation

import android.Manifest
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
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
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
 *  0. ZWECK
 *     Wear-OS-Companion zur HTML-/PWA-Golf-App. Erfasst eine laufende Runde
 *     Loch für Loch (volle Feld-Parität) MIT Live-GPS, virtuellem Caddy und
 *     Schlagtracking und schreibt sie als Entwurf (_draftRound) über den
 *     bestehenden Cloudflare-Worker ins GitHub-Repo.
 *     Abschluss/Auswertung passiert am Handy in der PWA.
 *
 *  1. UNVERHANDELBARE REGELN
 *     - JEDE Eingabe wird SOFORT lokal gesichert (persist() -> saveLocal),
 *       damit nichts verloren geht, wenn die App beendet wird.
 *     - Repo-Push NIE als Bedingung fürs Weiterarbeiten (offline muss gehen).
 *       Das gilt auch für Live/Caddy: Geo wird lokal gecacht, Wetter ist optional.
 *     - Kein Ganz-Überschreiben unkontrolliert: pushDraft setzt nur _draftRound,
 *       gpsShots (additiv, dedupliziert nach id) + exportedAt im geladenen
 *       DB-Objekt (Rest bleibt unangetastet).
 *     - WRITE_KEY/WORKER_URL/DATA_URL sind Konfiguration (oben) — vor Build prüfen.
 *     - Koordinaten IMMER [lat, lng], Distanzen IMMER Meter (wie in der PWA).
 *
 *  2. ARCHITEKTUR / DATENFLUSS
 *     a) State: GolfWatchApp() hält allen Zustand (course, geo, tee, hi, entries,
 *        idx, roundStart, status, syncJob, rec). entries = Map<hole, HoleEntry>.
 *     b) Lokale Persistenz: persist() ruft saveLocal(ctx, …) (SharedPreferences).
 *        Gesichert werden Kurs, Einträge, SCHLÄGE (shots) und der Geo-Rohstring
 *        des Platzes -> Fortsetzen funktioniert komplett offline.
 *     c) Repo-Sync: syncNow() lädt das Repo-JSON, setzt _draftRound, ergänzt
 *        gpsShots und pusht via Worker (Net.pushDraft). scheduleSync() ist ein
 *        ENTPRELLTER Aufruf (1,5 s nach der letzten Eingabe).
 *     d) Laden: Net.fetchData() holt Kurse (inkl. geo-Rohstring), Optionen,
 *        Schlägerlängen (clubDistances), Fahnen (pins) und HI aus DATA_URL.
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
 *  3. ANZEIGE
 *     - „über Par ±X · N Loch" (overPar()) steht bei den LOCH-INFOS (Loch/Par,
 *       Länge, HCP) — NICHT mehr ganz oben unter der Uhr, dort war die Zeile
 *       auf dem runden Display kaum sichtbar. Farbe: rot über Par, grün unter
 *       Par, gold bei E.
 *     - Reihenfolge Loch-Screen: Navigation · Loch/Par · Länge+HCP · über Par ·
 *       Platz · LIVE · CADDY · KERN · DETAILS · SCHLÄGE · RUNDE.
 *     - KERN enthält: Score, Putts, Tee-Ergebnis, Approach, Pin-Distanz
 *       (+ „aus GPS"), Penalty-Anzahl. GIR und alles Weitere unter DETAILS.
 *     - Darunter der LIVE-BLOCK: F/M/B zum Grün (bzw. Fahne), GRÜN-MASSE
 *       (Tiefe entlang der Spiellinie x Breite, Geo.greenDims), GPS-Genauigkeit.
 *     - Danach der CADDY (Schlägerempfehlung + „spielt wie" + Gefahren).
 *     - Danach das SCHLAGTRACKING (Schlag hier -> Schläger -> Ball erreicht).
 *     - KEIN Rundentimer (bewusst entfernt); roundStart wird intern gespeichert.
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
 *  4. LIVE-TRACKING (immer an)
 *     Live (Singleton) hält fix/err/running als Compose-State. Der Service
 *     abonniert GPS_PROVIDER (2 s) + NETWORK_PROVIDER als Fallback.
 *     Auf dem Loch-Screen: greenFMB() -> Front/Mitte/Back, pinPoint() nutzt die
 *     in der PWA gesetzte Fahnentiefe (DB.pins["<course>|<hole>"].d).
 *     „Auto-Loch": springt nur weiter, wenn (a) eingeschaltet, (b) kein Schlag
 *     in Aufnahme, (c) das aktuelle Loch einen Score hat und (d) man näher als
 *     40 m am Abschlag des FOLGENDEN Lochs steht.
 *
 *  5. VIRTUELLER CADDY
 *     Caddy.plan(...) — bewusst die schlanke Variante der PWA-Logik.
 *     SEIT UHR-SYNC-UPDATE (Spiegel der PWA-"Caddy-Grundregeln", index.html devdocs):
 *       · Driver ist reiner Tee-Schläger: nur im Pool, wenn Position <30 m am
 *         Tee-Punkt (teePt-Parameter) — vom Fairway/Rough NIE.
 *       · Modus-Parameter MP: layMargin 18/12/8 m vor Gefahren, plus ("ein
 *         Schläger mehr" bei sicher), wedgeLay (sicher legt auf volle Wedge-Zahl).
 *       · Wasser-Carry: sicher legt IMMER · offensiv geht bei Machbarkeit ·
 *         normal nur, wenn Grün-Schläger sicher trägt UND Gefahr schmal (<22 m).
 *     COCKPIT (PlayScreen, seit UI-Umbau): fester Haupt-Screen OHNE Scrollen —
 *       Kopf (Loch·Par·±·⛳), große Grünmitte + F/B/⛳, "spielt wie" mit
 *       EINZEL-Einflüssen (Plan.windM/tempM/lieM + windArrow relativ zur
 *       Spiellinie; berechnet in der plan()-Hülle um planCore()), Caddy-Zeile
 *       (Tap = Modus wechseln), Momenteingaben-Chips (📐 Schlagmessung,
 *       Tee-Ergebnis, ···), unten ✓-Loch + ‹›. "···" öffnet den KOMPLETTEN
 *       alten Screen (PlayDetailScreen) — dort lebt weiterhin ALLES
 *       (Details, Pin-Distanz, Schlagliste, Sync). Abschluss-Wizard:
 *       Score-Raster (Par vorbelegt; LONG-PRESS = Score + 2 Putts fertig)
 *       -> Putts-Raster (2 hervorgehoben; erscheint nur wenn Putts fehlen
 *       oder ein fertiges Loch korrigiert wird) -> Tee-Ergebnis (nur Par 4/5
 *       und nur wenn leer; Werte = opts.teeResults) -> Auto-Weiter zum
 *       nächsten Loch; am letzten Loch "fin"-Seite mit Runden-Abschluss.
 *       Der Wizard schreibt über onScoreSet/onPuttsSet/onTeeSet in dieselben
 *       HoleEntry-Felder wie der Detail-Screen — Sync unverändert.
 *
 *     GAMEPLAN (Phase 5): fetchData parst DB.strat.gameplans (parsePlans) zu
 *       "Kurs|Tee" -> Loch -> PlanHole(club, targetDesc). HolePage (Seite 0)
 *       zeigt die 📋-Zeile über der Caddy-Zeile. Bewusst schlank: NUR
 *       club+targetDesc. Unabhängig vom Caddy-Plan sichtbar.
 *
 *     SYNC v2: Der Worker merged jetzt SERVER-SEITIG gegen den frischen
 *       Repo-Stand (SHA-Lock, worker.js) — die Uhr kann Handy-Daten damit
 *       selbst mit veralteter Basis nicht mehr überschreiben. Alle Reads
 *       laufen bevorzugt über FRESH_URL (Worker ?fresh=1), Fallback Pages.
 *
 *     DRAFT-SYNC (gleichzeitig Uhr + Handy) — beide Richtungen:
 *       · START: "Neue Runde" prüft das Repo auf einen _draftRound von HEUTE.
 *         Läuft am Handy eine Runde, übernimmt die Uhr sie automatisch
 *         (Platz, Fahnen, Löcher via adoptHoles) und springt zum ersten Loch
 *         ohne Score — "gleichzeitig angehen" ohne Doppel-Anlage.
 *       · EMPFANG: Im Play-Screen zieht die Uhr alle 90 s den Repo-Entwurf
 *         (Net.fetchDraft, leichter GET) und übernimmt Handy-Eingaben in
 *         leere Felder. Latenz durch Pages-CDN: Minuten, kein Echtzeit-Kanal.
 *     ALT-DRAFT-SYNC:
 *       · pushDraft vereint den Repo-Entwurf DERSELBEN Runde loch-genau
 *         (Basis Repo, gesetzte Uhr-Felder darüber; null löscht nie) statt zu
 *         überschreiben, und liefert die gemergten Löcher zurück.
 *       · adoptHoles übernimmt daraus NUR lokal leere Felder (eigene Eingaben
 *         gewinnen) — identische Regel wie playAdoptDraft/mergeDB in der PWA.
 *       · Putts: UI zeigt Standard 2; fest wird die 2 erst mit dem ersten Score
 *         (kein Draft-Müll für unbespielte Löcher, kein Überschreiben echter
 *         Werte des anderen Geräts durch bloßes Anzeigen).
 *     ALT:
 *     (caddyPositionPlan): Zieldistanz -> playsLike (Temperatur/Wind, Open-Meteo,
 *     20-min-Cache, optional) -> Gefahren auf der Spiellinie (hazardsOnLine)
 *     -> Schlägerwahl aus clubDistances. OB/Gebäude = „nie darüber" (Layup),
 *     Wasser/Wald = je Modus tragen oder ablegen, Bunker = Landegefahr.
 *     Modus safe|bal|aggr über Chip umschaltbar (Sicherheitsmarge 12/6/0 m).
 *
 *  6. SCHLAGTRACKING (PWA-kompatibel!)
 *     Datenmodell wie in der PWA: je Loch eine Liste von POSITIONEN
 *     shots=[{lat,lng,club}], Schlagzahl = Positionen − 1.
 *     Ablauf: „Schlag hier" (Startpunkt = aktuelle Position) -> Schläger wählen
 *     -> laufen (Live-Meter werden angezeigt) -> „Ball erreicht" (Endpunkt).
 *     Der Endpunkt ist automatisch der Startpunkt des nächsten Schlags
 *     (Kette, exakt wie playRecStop). Zusätzlich landet jeder fertige Schlag
 *     als Messung in gpsShots {id,ts,club,dist,latA/lngA/latB/lngB,hole} —
 *     damit füttert die Uhr die Schlägerlängen-Datenbank der PWA.
 *
 *  7. WICHTIGE FUNKTIONEN
 *     RoundService (svcStart/svcStop/svcNote) · Live · MainActivity (Permissions,
 *     Keep-Screen-On) · GolfWatchApp (State) · change() · syncNow()/scheduleSync()
 *     · overPar() · finishAndClose() · buildRoundJson() · saveLocal()/loadLocal()
 *     · Net.fetchData()/pushDraft()/fetchWeather() · Geo.* (haversine, bearing,
 *     pointInRing, greenRingFor, greenFMB, pinPoint, hazardsOnLine, lieAt)
 *     · Caddy.plan() · recBegin()/recClub()/recStop().
 *
 *  8. ÄNDERUNGS-/KI-REGELN
 *     - Additiv arbeiten, bestehende Feld-Parität zur PWA erhalten.
 *     - Neue Eingaben immer über change() führen (sichert + synct automatisch).
 *     - Netzwerk nie im Main-Thread (withContext(Dispatchers.IO)).
 *     - Geo-Rechnungen sind teuer: Caddy nur neu rechnen, wenn sich die Position
 *       um >5 m geändert hat (siehe caddyTick) — nicht bei jedem GPS-Tick.
 *     - Nach Änderung: diese Doku + CHANGELOG fortschreiben.
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
 *  … innerhalb von <application>:
 *  <service
 *      android:name=".presentation.RoundService"
 *      android:exported="false"
 *      android:foregroundServiceType="location"/>
 * =========================================================================== */

private const val WORKER_URL = "https://golftraining-save.larsdohrmann24.workers.dev"
private const val DATA_URL = "https://golftraining-lars.github.io/Golftraining/trainingsdaten.json"
// Frischer Stand über den Sync-Worker v2 (GitHub Contents API, KEIN Pages-CDN
// mit ~10 min Verzögerung). Fallback bleibt DATA_URL (offline / alter Worker).
private const val FRESH_URL = "$WORKER_URL/?fresh=1"
private const val WRITE_KEY = "@Hallo"

// ================= Design (an HTML-App angelehnt: Pine-Grün / Gold) =================

private val Pine = Color(0xFF2E7D52)
private val PineDeep = Color(0xFF1C4F36)
private val PineText = Color(0xFF6FE3A6)   // helles Grün: gut lesbar auf dunklem Grund
private val Gold = Color(0xFFCBA23A)
private val GoldText = Color(0xFFE7C56A)    // helleres Gold für Text/Labels
private val GoldDeep = Color(0xFF8A6A1E)
private val RedC = Color(0xFFC65B4E)
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

data class CourseDef(
    val name: String,
    val tee: String,
    val holes: List<HoleDef>,
    val geoRaw: String? = null   // Roh-JSON der Platzgeometrie (erst bei Auswahl geparst)
)

data class Options(
    val teeResults: List<String>,
    val approachBuckets: List<String>,
    val teeClubs: List<String>,
    val approachLies: List<String>,
    val firstPuttDist: List<String>,
    val qualityOpts: List<String>,
    val bunkerTypes: List<String>,
    val penaltyTypes: List<String>
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
    // round.id — NUR das Handy vergibt eine roundId. Sie ist damit zugleich
    // das Erkennungsmerkmal "diese Runde kommt vom Handy" und der Schlüssel,
    // unter dem die PWA Commits/Schläge ablegt.
    val roundId: String? = null,
    val tee: String? = null
) {
    val fromPhone: Boolean get() = roundId != null || liveSrc == "phone"
}

// Gameplan-Zeile eines Lochs (aus DB.strat.gameplans der PWA, windneutral)
data class PlanHole(val club: String, val desc: String)

data class AppData(
    val courses: List<CourseDef>,
    val opts: Options,
    val hi: Double?,
    val clubs: List<ClubDist>,
    val pins: Map<String, Double>,  // "<Platz>|<Loch>" -> Fahnentiefe 0..1
    val draft: RepoDraft? = null,   // laufende Handy-Runde (falls vorhanden)
    // "<Platz>|<Tee>" -> Loch -> Plan (nur club + targetDesc, bewusst schlank)
    val plans: Map<String, Map<Int, PlanHole>> = emptyMap()
)

// Eine getrackte Position (PWA-Schema: shots = Liste von Positionen)
data class ShotPt(
    val lat: Double,
    val lng: Double,
    val club: String = ""
)

data class HoleEntry(
    val score: Int? = null,
    val putts: Int? = null,
    val tee: String? = null,
    val appr: String? = null,
    val penN: Int? = null,
    val firstPutt: String? = null,
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

data class GeoFeature(
    val kind: String,
    val ring: List<LL>? = null,
    val line: List<LL>? = null,
    val pt: LL? = null,
    // Bounding-Box zur schnellen Vorfilterung (minLat,minLng,maxLat,maxLng)
    val b0: Double = 0.0,
    val b1: Double = 0.0,
    val b2: Double = 0.0,
    val b3: Double = 0.0
)

data class HoleGeo(
    val tee: LL?,
    val green: LL?,
    val distM: Int
)

data class CourseGeo(
    val holes: Map<Int, HoleGeo>,
    val features: List<GeoFeature>
)

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

    fun bearing(a: LL, b: LL): Double {
        val toR = Math.PI / 180.0
        val toD = 180.0 / Math.PI
        val y = sin((b.lng - a.lng) * toR) * cos(b.lat * toR)
        val x = cos(a.lat * toR) * sin(b.lat * toR) -
                sin(a.lat * toR) * cos(b.lat * toR) * cos((b.lng - a.lng) * toR)
        return (atan2(y, x) * toD + 360.0) % 360.0
    }

    private val COMPASS8 = listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")

    fun compass8(deg: Double): String =
        COMPASS8[(Math.round((deg % 360.0) / 45.0).toInt()) % 8]

    // lokale Meter-Projektion um (lat0,lng0)
    private fun projX(lng: Double, lat0: Double, lng0: Double) =
        (lng - lng0) * cos(lat0 * Math.PI / 180.0) * 111320.0

    private fun projY(lat: Double, lat0: Double) =
        (lat - lat0) * 110540.0

    fun interp(a: LL, b: LL, t: Double) =
        LL(
            a.lat + (b.lat - a.lat) * t,
            a.lng + (b.lng - a.lng) * t
        )

    fun pointInRing(p: LL, ring: List<LL>): Boolean {
        var inside = false
        val x = p.lng
        val y = p.lat
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i].lng
            val yi = ring[i].lat
            val xj = ring[j].lng
            val yj = ring[j].lat
            if (((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    fun ringCentroid(ring: List<LL>): LL {
        if (ring.size < 3) {
            var la = 0.0
            var ln = 0.0
            ring.forEach { la += it.lat; ln += it.lng }
            val n = max(1, ring.size)
            return LL(la / n, ln / n)
        }
        val lat0 = ring[0].lat
        val lng0 = ring[0].lng
        var A = 0.0
        var cx = 0.0
        var cy = 0.0
        var j = ring.size - 1
        for (i in ring.indices) {
            val px = projX(ring[i].lng, lat0, lng0)
            val py = projY(ring[i].lat, lat0)
            val qx = projX(ring[j].lng, lat0, lng0)
            val qy = projY(ring[j].lat, lat0)
            val f = qx * py - px * qy
            A += f
            cx += (px + qx) * f
            cy += (py + qy) * f
            j = i
        }
        if (abs(A) < 1e-9) {
            var la = 0.0
            var ln = 0.0
            ring.forEach { la += it.lat; ln += it.lng }
            return LL(la / ring.size, ln / ring.size)
        }
        A *= 0.5
        cx /= (6 * A)
        cy /= (6 * A)
        return LL(
            lat0 + cy / 110540.0,
            lng0 + cx / (cos(lat0 * Math.PI / 180.0) * 111320.0)
        )
    }

    // Grünfläche zu Loch n: erst „Grünmitte liegt drin", sonst nächstgelegene <30 m
    fun greenRingFor(
        geo: CourseGeo,
        n: Int,
        cache: MutableMap<Int, List<LL>?>
    ): List<LL>? {

        if (cache.containsKey(n)) {
            return cache[n]
        }

        var ring: List<LL>? = null
        val center = geo.holes[n]?.green

        if (center != null) {
            val greens = geo.features.filter {
                it.kind == "green" && it.ring != null
            }
            val inside = greens.firstOrNull {
                pointInRing(center, it.ring!!)
            }
            if (inside != null) {
                ring = inside.ring
            } else {
                var best = Double.MAX_VALUE
                greens.forEach { f ->
                    val d = dist(center, ringCentroid(f.ring!!))
                    if (d < best && d < 30.0) {
                        best = d
                        ring = f.ring
                    }
                }
            }
        }

        cache[n] = ring
        return ring
    }

    data class FMB(
        val front: Int?,
        val mid: Int,
        val back: Int?
    )

    fun greenFMB(
        here: LL,
        geo: CourseGeo,
        n: Int,
        cache: MutableMap<Int, List<LL>?>
    ): FMB? {

        val center = geo.holes[n]?.green ?: return null
        val mid = dist(here, center).roundToInt()
        val ring = greenRingFor(geo, n, cache)

        if (ring != null && ring.size > 2) {
            var mn = Double.MAX_VALUE
            var mx = 0.0
            ring.forEach {
                val d = dist(here, it)
                if (d < mn) mn = d
                if (d > mx) mx = d
            }
            return FMB(mn.roundToInt(), mid, mx.roundToInt())
        }

        return FMB(null, mid, null)
    }

    // Grün-Maße wie greenDims in der PWA:
    // TIEFE = entlang der Spiellinie (Abschlag -> Grünmitte),
    // BREITE = senkrecht dazu. Beides in Metern.
    fun greenDims(
        geo: CourseGeo,
        n: Int,
        cache: MutableMap<Int, List<LL>?>
    ): Pair<Int, Int>? {

        val ring = greenRingFor(geo, n, cache) ?: return null
        if (ring.size < 3) return null

        val hg = geo.holes[n] ?: return null
        val center = hg.green ?: return null
        val from = hg.tee ?: return null

        val lat0 = center.lat
        val lng0 = center.lng

        val ox = projX(center.lng, lat0, lng0)
        val oy = projY(center.lat, lat0)
        val fx = projX(from.lng, lat0, lng0)
        val fy = projY(from.lat, lat0)

        var ax = ox - fx
        var ay = oy - fy
        val len = hypot(ax, ay).let { if (it == 0.0) 1.0 else it }
        ax /= len
        ay /= len

        // Senkrechte zur Spiellinie
        val px = -ay
        val py = ax

        var dMin = Double.MAX_VALUE
        var dMax = -Double.MAX_VALUE
        var wMin = Double.MAX_VALUE
        var wMax = -Double.MAX_VALUE

        ring.forEach { p ->
            val x = projX(p.lng, lat0, lng0)
            val y = projY(p.lat, lat0)
            val alongAxis = x * ax + y * ay
            val alongPerp = x * px + y * py
            if (alongAxis < dMin) dMin = alongAxis
            if (alongAxis > dMax) dMax = alongAxis
            if (alongPerp < wMin) wMin = alongPerp
            if (alongPerp > wMax) wMax = alongPerp
        }

        val depth = (dMax - dMin).roundToInt()
        val width = (wMax - wMin).roundToInt()

        return if (depth <= 0 || width <= 0) null else depth to width
    }

    // Fahnenposition: d = Tiefe 0 (vordere Kante) .. 1 (hintere Kante),
    // gemessen entlang der Achse Abschlag -> Grünmitte (wie pinPoint in der PWA).
    fun pinPoint(
        geo: CourseGeo,
        n: Int,
        d: Double,
        cache: MutableMap<Int, List<LL>?>
    ): LL? {

        val hg = geo.holes[n] ?: return null
        val center = hg.green ?: return null
        val ring = greenRingFor(geo, n, cache) ?: return center
        if (ring.size < 3) return center

        val from = hg.tee ?: return center

        val lat0 = center.lat
        val lng0 = center.lng

        val ox = projX(center.lng, lat0, lng0)
        val oy = projY(center.lat, lat0)
        val fx = projX(from.lng, lat0, lng0)
        val fy = projY(from.lat, lat0)

        var ax = ox - fx
        var ay = oy - fy
        val len = hypot(ax, ay).let { if (it == 0.0) 1.0 else it }
        ax /= len
        ay /= len

        var mn = Double.MAX_VALUE
        var mx = -Double.MAX_VALUE
        var fp: LL? = null
        var bp: LL? = null

        ring.forEach { p ->
            val px = projX(p.lng, lat0, lng0)
            val py = projY(p.lat, lat0)
            val proj = px * ax + py * ay
            if (proj < mn) {
                mn = proj
                fp = p
            }
            if (proj > mx) {
                mx = proj
                bp = p
            }
        }

        val f = fp ?: return center
        val b = bp ?: return center
        val t = d.coerceIn(0.0, 1.0)

        return LL(
            f.lat + (b.lat - f.lat) * t,
            f.lng + (b.lng - f.lng) * t
        )
    }

    // Gefahr auf der Spiellinie: near/far = Entfernung vom Standpunkt bis
    // Eintritt/Austritt in die Fläche (Meter).
    data class Hazard(
        val kind: String,
        val near: Int,
        val far: Int,
        val point: Boolean = false
    )

    private fun bboxHit(
        f: GeoFeature,
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double
    ): Boolean =
        !(f.b2 < minLat || f.b0 > maxLat || f.b3 < minLng || f.b1 > maxLng)

    fun hazardsOnLine(
        here: LL,
        target: LL,
        feats: List<GeoFeature>,
        kinds: Set<String>
    ): List<Hazard> {

        val total = dist(here, target)
        if (total < 5) return emptyList()

        // Vorfilter: nur Features, deren Bounding-Box den Korridor berührt (~30 m Puffer)
        val padLat = 30.0 / 110540.0
        val padLng = 30.0 / (cos(here.lat * Math.PI / 180.0) * 111320.0)
        val minLat = min(here.lat, target.lat) - padLat
        val maxLat = max(here.lat, target.lat) + padLat
        val minLng = min(here.lng, target.lng) - padLng
        val maxLng = max(here.lng, target.lng) + padLng

        val rings = feats.filter {
            it.ring != null &&
                    kinds.contains(it.kind) &&
                    bboxHit(it, minLat, minLng, maxLat, maxLng)
        }

        val out = ArrayList<Hazard>()
        val step = 4.0
        val n = Math.ceil(total / step).toInt()
        val active = HashMap<Int, IntArray>()   // index -> [near, far]

        for (i in 0..n) {
            val d = min(total, i * step)
            val p = interp(here, target, d / total)

            rings.forEachIndexed { fi, f ->
                val inside = pointInRing(p, f.ring!!)
                val a = active[fi]
                if (inside) {
                    if (a == null) {
                        active[fi] = intArrayOf(d.roundToInt(), d.roundToInt())
                    } else {
                        a[1] = d.roundToInt()
                    }
                } else if (a != null) {
                    out.add(Hazard(f.kind, a[0], a[1]))
                    active.remove(fi)
                }
            }
        }

        active.forEach { (fi, a) ->
            out.add(Hazard(rings[fi].kind, a[0], a[1]))
        }

        // Punkt-Features (Bäume) nahe der Spiellinie
        feats.filter {
            it.pt != null &&
                    kinds.contains(it.kind) &&
                    bboxHit(it, minLat, minLng, maxLat, maxLng)
        }.forEach { f ->
            val lat0 = here.lat
            val lng0 = here.lng
            val bx = projX(target.lng, lat0, lng0)
            val by = projY(target.lat, lat0)
            val px = projX(f.pt!!.lng, lat0, lng0)
            val py = projY(f.pt.lat, lat0)
            val l2 = (bx * bx + by * by).let { if (it == 0.0) 1.0 else it }
            val t = (px * bx + py * by) / l2
            if (t > 0.03 && t < 0.97) {
                val along = t * sqrt(l2)
                val perp = hypot(t * bx - px, t * by - py)
                if (perp < 8) {
                    out.add(
                        Hazard(
                            f.kind,
                            along.roundToInt(),
                            along.roundToInt(),
                            true
                        )
                    )
                }
            }
        }

        return out.sortedBy { it.near }
    }

    // Lage automatisch erkennen (wie lieAt in der PWA)
    fun lieAt(here: LL, feats: List<GeoFeature>): String {
        var res = "rough"
        for (f in feats) {
            val r = f.ring ?: continue
            when (f.kind) {
                "bunker" -> if (pointInRing(here, r)) return "bunker"
                "wood", "scrub" -> if (pointInRing(here, r)) return "recovery"
                "green" -> if (pointInRing(here, r)) return "green"
                "fairway" -> if (pointInRing(here, r)) res = "fairway"
            }
        }
        return res
    }

    // Lage-Faktoren wie in der PWA (LIE_F)
    fun lieFactor(lie: String): Double =
        when (lie) {
            "fairway", "green" -> 1.0
            "rough" -> 0.90
            "highrough" -> 0.78
            "bunker" -> 0.72
            "recovery" -> 0.58
            else -> 0.95
        }

    fun lieLabel(lie: String): String =
        when (lie) {
            "fairway" -> "Fairway"
            "green" -> "Grün"
            "rough" -> "Rough"
            "highrough" -> "hohes Rough"
            "bunker" -> "Bunker"
            "recovery" -> "Recovery"
            else -> lie
        }
}

// ===========================================================================
//  WETTER-PHYSIK (identisch zur PWA: tempFactor / windRel / playsLike)
// ===========================================================================

object Wx {

    fun tempFactor(temp: Double?): Double =
        if (temp == null) 1.0 else 1.0 + (temp - 20.0) * 0.0022

    data class Rel(
        val head: Double,       // + Rückenwind, − Gegenwind (m/s)
        val crossMag: Double,
        val crossFrom: String
    )

    // windDir = Richtung, AUS der der Wind kommt (meteorologisch)
    fun windRel(
        windMs: Double?,
        windDir: Double?,
        bearing: Double?
    ): Rel? {

        if (windMs == null || windDir == null || bearing == null) return null

        val toR = Math.PI / 180.0
        val blowTo = (windDir + 180.0) % 360.0
        val relBlow = ((blowTo - bearing + 540.0) % 360.0) - 180.0
        val head = windMs * cos(relBlow * toR)
        val crossMag = abs(windMs * sin(relBlow * toR))
        val srcRel = ((windDir - bearing + 540.0) % 360.0) - 180.0

        return Rel(
            head,
            crossMag,
            if (srcRel < 0) "links" else "rechts"
        )
    }

    // Windpfeil aus Sicht der Spiellinie: ↑ = Rückenwind (bläst in Spielrichtung),
    // ↓ = Gegenwind, → = bläst nach rechts usw.
    fun arrowRel(
        windDir: Double?,
        bearing: Double?
    ): String? {
        if (windDir == null || bearing == null) return null
        val blowTo = (windDir + 180.0) % 360.0
        val rel = ((blowTo - bearing + 540.0) % 360.0) - 180.0
        val arrows = arrayOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
        val idx = (((rel + 360.0) % 360.0) / 45.0).roundToInt() % 8
        return arrows[idx]
    }

    fun playsLike(
        distM: Double,
        w: Weather?,
        bearing: Double?
    ): Int {

        if (w == null) return distM.roundToInt()

        var d = distM / tempFactor(w.temp)
        val rel = windRel(w.windMs, w.windDir, bearing)

        if (rel != null) {
            d += if (rel.head < 0) {
                distM * (-rel.head) * 0.014
            } else {
                -(distM * rel.head * 0.008)
            }
        }

        return d.roundToInt()
    }

    fun line(w: Weather?): String? {
        if (w == null) return null
        val t = w.temp?.roundToInt()
        val ms = w.windMs
        val dir = w.windDir
        val parts = ArrayList<String>()
        if (t != null) parts.add("$t°C")
        if (ms != null && dir != null) {
            parts.add("${(ms * 3.6).roundToInt()} km/h aus ${Geo.compass8(dir)}")
        }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }
}

// ===========================================================================
//  VIRTUELLER CADDY (schlanke Wear-Variante von caddyPositionPlan)
// ===========================================================================

object Caddy {

    val HAZARD_KINDS = setOf(
        "water", "penalty", "ob", "bunker", "wood", "tree", "building"
    )

    // Modus-Parameter (Spiegel der PWA-Regeln, siehe index.html devdocs "Caddy-Grundregeln"):
    //  layMargin: Abstand, den ein Layup VOR einer Gefahr hält (sicher 18 / normal 12 / offensiv 8 m)
    //  plus:      Zuschlag auf die Zieldistanz ins Grün ("ein Schläger mehr" bei sicher)
    //  wedgeLay:  sicher legt beim Vorlegen bewusst auf die volle Wedge-Zahl statt Maximallänge
    private data class MP(
        val layMargin: Int,
        val plus: Int,
        val wedgeLay: Boolean
    )

    private fun mp(mode: String) =
        when (mode) {
            "safe" -> MP(18, 5, true)
            "aggr" -> MP(8, 0, false)
            else -> MP(12, 2, false)
        }

    fun modeLabel(mode: String) =
        when (mode) {
            "safe" -> "sicher"
            "aggr" -> "offensiv"
            else -> "normal"
        }

    fun kindLabel(kind: String) =
        when (kind) {
            "water" -> "Wasser"
            "penalty" -> "Penalty"
            "ob" -> "Out of Bounds"
            "bunker" -> "Bunker"
            "wood" -> "Wald"
            "tree" -> "Baum"
            "building" -> "Gebäude"
            else -> kind
        }

    data class Plan(
        val headline: String,        // z. B. "PW · 118 m"
        val club: String?,
        val target: Int,             // echte Distanz (m)
        val plays: Int,              // „spielt wie" (m)
        val lines: List<String>,     // Begründung / Hinweise (max. 3)
        val warn: String?,           // rot hervorgehobene Warnung
        // Einfluss-Aufschlüsselung fürs Cockpit — WICHTIG: Zusatzfelder mit
        // Defaults MÜSSEN am ENDE stehen, weil planCore() den Plan an mehreren
        // Stellen mit POSITIONS-Argumenten baut (headline..warn).
        val windM: Int = 0,          // Windanteil (+ = spielt länger)
        val tempM: Int = 0,          // Temperaturanteil
        val lieM: Int = 0,           // Lage-Anteil (need - plays)
        val windArrow: String? = null, // Pfeil RELATIV zur Spiellinie (↑ = Rückenwind)
        val windKmh: Int? = null
    )

    private fun pick(
        clubs: List<ClubDist>,
        d: Int,
        carryMode: Boolean
    ): ClubDist? {

        if (clubs.isEmpty()) return null

        return if (carryMode) {
            clubs.minByOrNull { abs(it.carryOrReach - d) }
        } else {
            clubs.filter { it.reach >= d }.minByOrNull { it.reach }
                ?: clubs.maxByOrNull { it.reach }
        }
    }

    private fun short(name: String): String =
        name
            .replace(Regex("\\s*\\d+([,.]\\d+)?\\s*°"), "")
            .replace("Iron", "Eisen")
            .replace("Wood", "Holz")
            .trim()

    /**
     * @param here      aktuelle Position
     * @param target    Zielpunkt (Fahne/Grünmitte)
     * @param par       Par des Lochs
     * @param clubs     Schlägerlängen (absteigend sortiert)
     * @param feats     Platz-Features für Gefahren
     * @param w         Wetter (optional)
     * @param mode      safe|bal|aggr
     * @param lie       erkannte Lage
     */
    fun plan(
        here: LL,
        target: LL,
        par: Int,
        clubs: List<ClubDist>,
        feats: List<GeoFeature>,
        w: Weather?,
        mode: String,
        lie: String,
        teePt: LL? = null
    ): Plan {
        // Hülle: Kernplan rechnen und mit der Einfluss-Aufschlüsselung
        // (Wind/Temperatur/Lage einzeln, in Metern) anreichern — das Cockpit
        // zeigt nicht nur DASS es anders spielt, sondern WARUM.
        val p = planCore(here, target, par, clubs, feats, w, mode, lie, teePt)
        val raw = Geo.dist(here, target)
        val bearing = Geo.bearing(here, target)
        val tempM = (raw / Wx.tempFactor(w?.temp) - raw).roundToInt()
        val windM = p.plays - raw.roundToInt() - tempM   // Rest = Windanteil (rundungssicher)
        val lieM = ((p.plays / Geo.lieFactor(lie)).roundToInt() - p.plays)
        return p.copy(
            windM = windM,
            tempM = tempM,
            lieM = lieM,
            windArrow = Wx.arrowRel(w?.windDir, bearing),
            windKmh = w?.windMs?.let { (it * 3.6).roundToInt() }
        )
    }

    private fun planCore(
        here: LL,
        target: LL,
        par: Int,
        clubs: List<ClubDist>,
        feats: List<GeoFeature>,
        w: Weather?,
        mode: String,
        lie: String,
        teePt: LL? = null
    ): Plan {

        val m = mp(mode)

        // HARTE REGEL (wie PWA caddyPositionPlan): Der Driver ist ein reiner
        // Tee-Schläger. Nur wenn wir nachweislich AM ABSCHLAG stehen (<30 m zum
        // Tee-Punkt der Karte), bleibt er im Pool — sonst fliegt er raus.
        val onTee = teePt != null && Geo.dist(here, teePt) < 30.0
        val ground =
            if (onTee) {
                clubs
            } else {
                clubs
                    .filter { !it.club.contains("driver", ignoreCase = true) }
                    .ifEmpty { clubs }
            }

        val raw = Geo.dist(here, target)
        val dist = raw.roundToInt()
        val bearing = Geo.bearing(here, target)
        val plays = Wx.playsLike(raw, w, bearing)
        val lines = ArrayList<String>()
        var warn: String? = null

        // Lage verkürzt den Schlag -> nötige Schlägerlänge steigt
        val lf = Geo.lieFactor(lie)
        val need = (plays / lf).roundToInt()

        if (lf < 0.99) {
            lines.add("${Geo.lieLabel(lie)} → wie ${need} m")
        }

        // Auf dem Grün: kein Caddy nötig
        if (lie == "green" || dist < 12) {
            return Plan(
                if (dist < 12) "Putten · $dist m" else "Grün · $dist m",
                null,
                dist,
                plays,
                emptyList(),
                null
            )
        }

        val haz = Geo.hazardsOnLine(here, target, feats, HAZARD_KINDS)

        // relevante Gefahren: liegen VOR dem Ziel und noch nicht hinter uns
        val ahead = haz.filter { it.near > 15 && it.near < dist - 5 }

        val hard = ahead.firstOrNull { it.kind == "ob" || it.kind == "building" }
        val carry = ahead.firstOrNull {
            it.kind == "water" || it.kind == "penalty" || it.kind == "wood"
        }
        val land = ahead.filter { it.kind == "bunker" }
        val trees = ahead.filter { it.kind == "tree" }

        var club: ClubDist?
        var headline: String

        if (hard != null) {
            // „Nie darüber": Layup mit Modus-Abstand davor (sicher hält mehr Abstand)
            val layup = max(30, hard.near - (m.layMargin + 4))
            club = pick(ground, layup, true)
            warn = "${kindLabel(hard.kind)} ab ${hard.near} m — nicht drüber"
            lines.add("Layup auf ~$layup m")
            headline = club?.let { "${short(it.club)} · $layup m" } ?: "Layup $layup m"

            return Plan(headline, club?.club, dist, plays, lines.take(3), warn)
        }

        if (carry != null) {
            val needCarry = carry.far + 6
            val best = ground.maxByOrNull { it.carryOrReach }
            val canCarry = (best?.carryOrReach ?: 0) >= needCarry
            val greenClub = pick(ground, need, true)
            val greenClears = (greenClub?.carryOrReach ?: 0) >= needCarry
            val narrow = (carry.far - carry.near) < 22

            // sicher: legt IMMER · offensiv: geht, wenn machbar ·
            // normal: nur wenn der Grün-Schläger sicher trägt UND die Gefahr schmal ist
            val goForIt =
                canCarry &&
                        when (mode) {
                            "aggr" -> true
                            "safe" -> false
                            else -> greenClears && narrow
                        }

            if (!goForIt) {
                val layup = max(30, carry.near - m.layMargin)
                club = pick(ground, layup, true)
                warn = "${kindLabel(carry.kind)} ${carry.near}–${carry.far} m"
                lines.add(
                    if (canCarry) {
                        "${modeLabel(mode)}: davor ablegen auf ~$layup m"
                    } else {
                        "Ablegen auf ~$layup m (Carry ${needCarry} m nicht sicher)"
                    }
                )
                headline = club?.let { "${short(it.club)} · $layup m" } ?: "Layup $layup m"
                return Plan(headline, club?.club, dist, plays, lines.take(3), warn)
            }

            lines.add("${kindLabel(carry.kind)} bis ${carry.far} m → Carry ${needCarry} m")
        }

        // Normale Schlägerwahl: ins Grün nach Carry, sonst nach Reichweite — MODUSABHÄNGIG
        val approach = par == 3 || need <= (ground.maxOfOrNull { it.carryOrReach } ?: 0)
        val frontBunker = land.any { need - 16 <= it.far && it.far <= need + 6 }
        val needAdj = need + m.plus + (if (mode == "safe" && frontBunker) 4 else 0)

        club =
            if (approach) {
                when (mode) {
                    // offensiv: die nackte Zahl attackieren (nächster Schläger nach Carry)
                    "aggr" -> ground.minByOrNull { abs(it.carryOrReach - need) }
                    // sicher/normal: kleinster Schläger, der die (ggf. erhöhte) Zahl sicher trägt
                    else ->
                        ground
                            .filter { it.carryOrReach >= needAdj }
                            .minByOrNull { it.carryOrReach }
                            ?: ground.maxByOrNull { it.carryOrReach }
                }
            } else if (m.wedgeLay) {
                // sicher: beim Vorlegen bewusst die volle Wedge-Zahl (70–140 m Rest, Ziel ~100)
                ground
                    .filter { (need - it.reach) in 70..140 }
                    .minByOrNull { abs((need - it.reach) - 100) }
                    ?: ground.maxByOrNull { it.reach }
            } else {
                ground.maxByOrNull { it.reach }
            }

        if (!approach) {
            club?.let { c ->
                val left = need - c.reach
                if (m.wedgeLay && left in 70..140) {
                    lines.add("Sicher: bewusst auf ~$left m — deine volle Zahl")
                } else if (left > 0) {
                    lines.add("Vorlegen — lässt ~$left m")
                }
            }
        } else if (mode == "safe" && m.plus > 0) {
            lines.add("Sicher: ein Schläger mehr — Grünmitte")
        } else if (mode == "aggr") {
            lines.add("Offensiv: die Zahl attackieren")
        }

        // Bunker als Landegefahr: liegt der gewählte Schläger in einem Bunker?
        val chosen = club
        if (chosen != null) {
            val reach = if (approach) chosen.carryOrReach else chosen.reach
            val inBunker = land.firstOrNull {
                reach >= it.near - 8 && reach <= it.far + 8
            }
            if (inBunker != null) {
                val alt = ground
                    .filter { c ->
                        val r = if (approach) c.carryOrReach else c.reach
                        r < inBunker.near - 10 && r > inBunker.near - 45
                    }
                    .maxByOrNull { it.reach }
                if (alt != null && mode != "aggr") {
                    lines.add("Bunker ${inBunker.near}–${inBunker.far} m → davor bleiben")
                    club = alt
                } else {
                    lines.add("Bunker ${inBunker.near}–${inBunker.far} m in Landezone")
                }
            }
        }

        if (trees.isNotEmpty()) {
            lines.add("Baum bei ${trees.first().near} m in der Linie")
        }

        // Deutlich kürzer als der kleinste Schläger -> Teilschlag ausweisen
        club?.let { c ->
            val full = if (approach) c.carryOrReach else c.reach
            if (full - need > 15) {
                lines.add("Teilschlag · ${short(c.club)} ≈ $full m")
            }
        }

        val rel = Wx.windRel(w?.windMs, w?.windDir, bearing)
        if (rel != null) {
            if (rel.head < -0.8) {
                lines.add("Gegenwind ${abs(rel.head).roundToInt()} m/s")
            } else if (rel.head > 0.8) {
                lines.add("Rückenwind ${rel.head.roundToInt()} m/s")
            }
            if (rel.crossMag > 1.5) {
                lines.add("Seitenwind von ${rel.crossFrom} ${rel.crossMag.roundToInt()} m/s")
            }
        }

        headline = club?.let {
            "${short(it.club)} · $need m"
        } ?: "$need m"

        return Plan(headline, club?.club, dist, plays, lines.take(3), warn)
    }
}

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

// ---- Geo-Parser: aus dem Roh-JSON eines Platzes die spielrelevante Geometrie ----
// Es werden bewusst NUR die Arten geladen, die Live-Anzeige und Caddy brauchen —
// das spart auf der Uhr Speicher (statt ~300 Features nur die Hälfte).
private val GEO_KEEP = setOf(
    "green", "bunker", "water", "penalty", "ob",
    "wood", "tree", "building", "fairway", "scrub"
)

private fun llArray(a: JSONArray?): List<LL>? {
    if (a == null || a.length() == 0) return null
    val out = ArrayList<LL>(a.length())
    for (i in 0 until a.length()) {
        val p = a.optJSONArray(i) ?: continue
        if (p.length() < 2) continue
        out.add(LL(p.optDouble(0), p.optDouble(1)))
    }
    return if (out.isEmpty()) null else out
}

private fun llPoint(a: JSONArray?): LL? {
    if (a == null || a.length() < 2) return null
    return LL(a.optDouble(0), a.optDouble(1))
}

private fun feature(
    kind: String,
    ring: List<LL>?,
    line: List<LL>?,
    pt: LL?
): GeoFeature {

    val pts = ring ?: line ?: pt?.let { listOf(it) } ?: emptyList()

    var m0 = Double.MAX_VALUE
    var m1 = Double.MAX_VALUE
    var m2 = -Double.MAX_VALUE
    var m3 = -Double.MAX_VALUE

    pts.forEach {
        if (it.lat < m0) m0 = it.lat
        if (it.lng < m1) m1 = it.lng
        if (it.lat > m2) m2 = it.lat
        if (it.lng > m3) m3 = it.lng
    }

    return GeoFeature(kind, ring, line, pt, m0, m1, m2, m3)
}

fun parseGeo(raw: String?): CourseGeo? {

    if (raw.isNullOrBlank()) return null

    return try {

        val g = JSONObject(raw)
        val holes = HashMap<Int, HoleGeo>()

        g.optJSONObject("holes")?.let { ho ->
            ho.keys().forEach { k ->
                val n = k.toIntOrNull()
                val o = ho.optJSONObject(k)
                if (n != null && o != null) {
                    holes[n] = HoleGeo(
                        llPoint(o.optJSONArray("tee")),
                        llPoint(o.optJSONArray("green")),
                        o.optInt("distM", 0)
                    )
                }
            }
        }

        val feats = ArrayList<GeoFeature>()

        fun addAll(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val kind = f.optString("kind")
                if (!GEO_KEEP.contains(kind)) continue
                val ring = llArray(f.optJSONArray("ring"))
                val line = llArray(f.optJSONArray("line"))
                val pt = llPoint(f.optJSONArray("pt"))
                if (ring == null && line == null && pt == null) continue
                feats.add(feature(kind, ring, line, pt))
            }
        }

        addAll(g.optJSONArray("features"))
        addAll(g.optJSONArray("mine"))   // selbst eingezeichnete Gefahren (OB, Bäume)

        if (holes.isEmpty() && feats.isEmpty()) {
            null
        } else {
            CourseGeo(holes, feats)
        }

    } catch (e: Exception) {
        null
    }
}

private object Net {

    // _draftRound aus dem Repo-JSON ziehen (laufende Runde eines anderen Geräts)
    private fun parseDraft(db: JSONObject): RepoDraft? {
        val d = db.optJSONObject("_draftRound") ?: return null
        val r = d.optJSONObject("round") ?: return null
        val course = r.optString("course")
        val date = r.optString("date")
        if (course.isEmpty() || date.isEmpty()) return null
        val lv = d.optJSONObject("live")
        return RepoDraft(
            course,
            date,
            r.optString("side", "18 Loch"),
            d.optString("ts"),
            r.optJSONArray("holes") ?: JSONArray(),
            lv?.optString("src")?.ifEmpty { null },
            lv?.optInt("hole", 0)?.takeIf { it > 0 },
            lv?.optString("at")?.ifEmpty { null },
            r.optString("id").ifEmpty { null },
            r.optString("tee").ifEmpty { null }
        )
    }

    // Leichter GET nur für den laufenden Entwurf (Pull-Abgleich während der Runde)
    fun fetchDraft(): RepoDraft? = parseDraft(JSONObject(readData()))

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
    private fun readData(): String {
        try {
            val f = openRead(FRESH_URL)
            if (f.responseCode in 200..299) {
                val t = f.inputStream.bufferedReader().use { it.readText() }
                f.disconnect()
                return t
            }
            f.disconnect()
        } catch (e: Exception) {
        }
        val c = openRead(DATA_URL)
        val t = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        return t
    }

    // DB.strat.gameplans -> je "Kurs|Tee" eine Loch->Plan-Map (Phase 5, Watch)
    private fun parsePlans(db: JSONObject): Map<String, Map<Int, PlanHole>> {
        val strat = db.optJSONObject("strat") ?: return emptyMap()
        val gps = strat.optJSONObject("gameplans") ?: return emptyMap()
        val out = HashMap<String, Map<Int, PlanHole>>()
        for (key in gps.keys()) {
            val p = gps.optJSONObject(key) ?: continue
            val holes = p.optJSONArray("holes") ?: continue
            val m = HashMap<Int, PlanHole>()
            for (i in 0 until holes.length()) {
                val h = holes.optJSONObject(i) ?: continue
                val club = h.optString("club")
                if (club.isEmpty()) continue
                m[h.optInt("hole")] = PlanHole(club, h.optString("targetDesc"))
            }
            if (m.isNotEmpty()) out[key] = m
        }
        return out
    }

    // Rohtext holen — getrennt vom Parsen, damit er in den Cache kann.
    fun fetchRaw(): String = readData()

    fun fetchData(): AppData = parseData(JSONObject(fetchRaw()))

    fun parseData(db: JSONObject): AppData {

        val courses = ArrayList<CourseDef>()
        val ca = db.optJSONArray("courses") ?: JSONArray()

        for (i in 0 until ca.length()) {
            val co = ca.getJSONObject(i)
            val name = co.optString("name")

            val tees = co.optJSONObject("tees") ?: continue

            // JEDEN Tee als eigenen Eintrag anlegen. Vorher wurde nur der
            // erste Schlüssel genommen — auf der Uhr war der Abschlag damit
            // nicht wählbar, sondern schlicht der, der im JSON zuerst stand.
            val geoRaw = co.optJSONObject("geo")?.toString()

            for (teeName in tees.keys()) {

                val ha = tees
                    .optJSONObject(teeName)
                    ?.optJSONArray("holes") ?: continue

                val holes = ArrayList<HoleDef>()

                for (j in 0 until ha.length()) {
                    val h = ha.getJSONObject(j)

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
                            holes,
                            geoRaw
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

        // Fahnenpositionen aus der PWA: "<Platz>|<Loch>" -> {d,date}
        val pins = HashMap<String, Double>()
        db.optJSONObject("pins")?.let { po ->
            po.keys().forEach { k ->
                val o = po.optJSONObject(k)
                if (o != null && o.has("d")) {
                    pins[k] = o.optDouble("d", 0.5)
                }
            }
        }

        return AppData(
            courses,
            opts,
            hi,
            clubs,
            pins,
            parseDraft(db),
            parsePlans(db)
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

            val j = JSONObject(
                c.inputStream.bufferedReader().use { it.readText() }
            )

            c.disconnect()

            val cur = j.optJSONObject("current") ?: return null

            Weather(
                if (cur.has("temperature_2m")) cur.optDouble("temperature_2m") else null,
                if (cur.has("wind_speed_10m")) cur.optDouble("wind_speed_10m") else null,
                if (cur.has("wind_direction_10m")) cur.optDouble("wind_direction_10m") else null,
                if (cur.has("wind_gusts_10m")) cur.optDouble("wind_gusts_10m") else null,
                System.currentTimeMillis()
            )

        } catch (e: Exception) {
            null
        }
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

    fun lastOwnLiveAt(): String = ownLiveAt

    fun pushDraft(
        round: JSONObject,
        shotMeasurements: List<JSONObject>,
        currentHole: Int? = null,
        courseName: String? = null,
        teeName: String? = null
    ): PushResult {

        val db = JSONObject(readData())

        // Gleiche Runde bereits als Entwurf im Repo (z. B. vom Handy)?
        // -> loch-genau vereinen statt zu überschreiben.
        val prevRound = db.optJSONObject("_draftRound")?.optJSONObject("round")
        val key = { r: JSONObject ->
            r.optString("date") + "|" + r.optString("course") + "|" + r.optString("side")
        }
        if (prevRound != null && key(prevRound) == key(round)) {
            mergeDraftHoles(prevRound, round)
        }

        // Live-Zeiger: welches Gerät steht auf welchem Loch. Das Handy wertet
        // ihn aus, um den Spielmodus zu öffnen und mitzublättern.
        val prevLive = db.optJSONObject("_draftRound")?.optJSONObject("live")
        var remoteHole: Int? = null

        if (prevLive != null && prevLive.optString("src") != "watch") {
            val at = prevLive.optString("at")
            val h = prevLive.optInt("hole", 0)
            if (h > 0 && at > ownLiveAt && h != currentHole) {
                remoteHole = h
            }
        }

        val draft = JSONObject()
            .put("round", round)
            .put("ts", isoNow())

        if (currentHole != null) {
            // Ein vom Handy übernommenes Loch NICHT sofort überschreiben —
            // sonst kämpfen beide Geräte gegeneinander.
            val hole = remoteHole ?: currentHole
            val now = isoNow()
            ownLiveAt = now
            draft.put(
                "live",
                JSONObject()
                    .put("src", "watch")
                    .put("hole", hole)
                    .put("at", now)
                    .put("course", courseName ?: round.optString("course"))
                    .put("tee", teeName ?: round.optString("tee"))
                    .put("date", round.optString("date"))
                    .put("side", round.optString("side"))
            )
        }

        db.put("_draftRound", draft)

        if (shotMeasurements.isNotEmpty()) {

            val arr = db.optJSONArray("gpsShots") ?: JSONArray()
            val have = HashSet<String>()

            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("id")?.let { have.add(it) }
            }

            shotMeasurements.forEach { s ->
                val id = s.optString("id")
                if (id.isNotEmpty() && !have.contains(id)) {
                    arr.put(s)
                    have.add(id)
                }
            }

            db.put("gpsShots", arr)
        }

        db.put(
            "exportedAt",
            isoNow()
        )

        val p = URL(WORKER_URL).openConnection() as HttpURLConnection

        p.requestMethod = "POST"
        p.doOutput = true
        p.connectTimeout = 20000
        p.readTimeout = 20000

        p.setRequestProperty(
            "Content-Type",
            "application/json"
        )

        p.setRequestProperty(
            "X-Write-Key",
            WRITE_KEY
        )

        p.outputStream.use {
            it.write(
                JSONObject()
                    .put("data", db)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
            )
        }

        val code = p.responseCode

        p.disconnect()

        val ok = code in 200..299

        return PushResult(
            ok,
            round.optJSONArray("holes"),
            // Nur bei erfolgreichem Schreiben melden — sonst würde die Uhr
            // einem Loch folgen, das sie gar nicht bestätigt hat.
            if (ok) remoteHole else null
        )
    }
}

private fun jn(b: Boolean?) =
    if (b == null) null
    else if (b) "Ja"
    else "Nein"

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

        e.score?.let {
            h.put("score", it)
        }

        e.putts?.let {
            h.put("putts", it)
        }

        e.tee?.let {
            h.put("tee", it)
        }

        e.appr?.let {
            h.put("appr", it)
        }

        e.penN?.let {
            h.put("penN", it)
        }

        e.firstPutt?.let {
            h.put("firstPutt", it)
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

    e.score?.let {
        put("score", it)
    }

    e.putts?.let {
        put("putts", it)
    }

    e.tee?.let {
        put("tee", it)
    }

    e.appr?.let {
        put("appr", it)
    }

    e.penN?.let {
        put("penN", it)
    }

    e.firstPutt?.let {
        put("firstPutt", it)
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

    e.ud?.let {
        put("ud", it)
    }

    e.ss?.let {
        put("ss", it)
    }

    e.recovery?.let {
        put("recovery", it)
    }

    // PWA-Feldname ist girDirect — auch im Entwurf (playRound liefert
    // dieselben Hole-Objekte). Vorher stand hier "gir": beim Fortsetzen
    // einer Watch-Runde am Handy fiel der Wert still weg.
    e.gir?.let {
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
    if (
        o.has(k) &&
        !o.isNull(k)
    ) {
        o.getInt(k)
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

private fun jsonToEntry(
    o: JSONObject
) = HoleEntry(
    optI(o, "score"),
    optI(o, "putts"),
    optS(o, "tee"),
    optS(o, "appr"),
    optI(o, "penN"),
    optS(o, "firstPutt"),
    optS(o, "quality"),
    optS(o, "club"),
    optS(o, "lie"),
    optI(o, "distToPin"),
    optI(o, "bunkerN"),
    optS(o, "b1"),
    optS(o, "penType"),
    optB(o, "ud"),
    optB(o, "ss"),
    optB(o, "recovery"),
    optB(o, "gir"),
    jsonToShots(o.optJSONArray("shots"))
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
    pinDepth: Map<Int, Double>,
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

    // Platzgeometrie mitsichern -> Live-Distanzen & Caddy funktionieren offline
    course.geoRaw?.let {
        c.put("geo", it)
    }

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
    val pd = JSONObject()
    pinDepth.forEach { (k, v) -> pd.put(k.toString(), v) }
    o.put("pins", pd)

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
    val pinDepth: Map<Int, Double>,
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

        val course = CourseDef(
            c.getString("name"),
            teeN,
            holes,
            if (c.has("geo") && !c.isNull("geo")) c.optString("geo") else null
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

        val pins = HashMap<Int, Double>()
        o.optJSONObject("pins")?.let { p ->
            p.keys().forEach { k ->
                k.toIntOrNull()?.let { n -> pins[n] = p.optDouble(k, 0.5) }
            }
        }

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
                pins,
                ms,
                o.optString("roundId").ifEmpty { null },
                o.optString("side", "18 Loch").ifEmpty { "18 Loch" }
            )
        }

    } catch (e: Exception) {
        null
    }
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
    }
}

private fun cacheRead(ctx: Context): AppData? =
    try {
        val f = java.io.File(ctx.filesDir, DATA_CACHE)
        if (f.exists()) Net.parseData(JSONObject(f.readText())) else null
    } catch (e: Exception) {
        null
    }

// Netz zuerst, Cache als Rückfall. Gibt zusätzlich zurück, ob die Daten
// frisch sind — die UI sagt dem Nutzer, womit er gerade arbeitet.
// Letzter Ladefehler im Klartext. Vorher wurde jede Exception stumm
// geschluckt — ein defekter Lesepfad sah damit genauso aus wie "kein Netz".
@Volatile
private var lastLoadError: String? = null

private fun loadData(ctx: Context): Pair<AppData?, Boolean> {

    val raw = try {
        lastLoadError = null
        Net.fetchRaw()
    } catch (e: Exception) {
        lastLoadError = e.javaClass.simpleName +
                (e.message?.take(40)?.let { ": $it" } ?: "")
        null
    }

    if (raw != null) {
        try {
            val d = Net.parseData(JSONObject(raw))
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

private fun prefSetB(ctx: Context, k: String, v: Boolean) =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(k, v)
        .apply()

// ===========================================================================
//  LIVE-GPS — Singleton, das der Foreground-Service füttert und die UI liest
// ===========================================================================

object Live {

    // Compose-State: jede Zuweisung löst automatisch eine Neuzeichnung aus
    var fix: Fix? by mutableStateOf(null)
    var running: Boolean by mutableStateOf(false)
    var err: String? by mutableStateOf(null)
    var src: String by mutableStateOf("")   // aktive GPS-Quelle: "⌚ Uhr" / "📱 Handy"

    // Text für die Dauer-Notification (Loch/Stand)
    var note: String by mutableStateOf("Runde läuft")

    fun reset() {
        fix = null
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

    private val listener = object : LocationListener {

        override fun onLocationChanged(loc: Location) {
            Live.err = null
            Live.fix = Fix(
                loc.latitude,
                loc.longitude,
                if (loc.hasAccuracy()) loc.accuracy else 99f,
                System.currentTimeMillis()
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

        val b =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        return b
            .setContentTitle("⛳ Golf-Runde")
            .setContentText(Live.note)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
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
                    Live.fix = Fix(
                        it.latitude,
                        it.longitude,
                        if (it.hasAccuracy()) it.accuracy else 99f,
                        System.currentTimeMillis()
                    )
                }
            }

        } catch (e: SecurityException) {
            Live.err = "Standort-Freigabe fehlt"
        } catch (e: Exception) {
            Live.err = "GPS-Fehler"
        }
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
                    Live.fix = Fix(
                        l.latitude,
                        l.longitude,
                        if (l.hasAccuracy()) l.accuracy else 99f,
                        System.currentTimeMillis()
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
                    Live.fix = Fix(
                        l.latitude,
                        l.longitude,
                        if (l.hasAccuracy()) l.accuracy else 99f,
                        System.currentTimeMillis()
                    )
                }
            }

            true
        } catch (e: SecurityException) {
            Live.err = "Standort-Freigabe fehlt"
            false
        } catch (e: Exception) {
            false
        }

    // Nur die GPS-Quellen lösen (für Quellen-Wechsel) — Foreground-Status,
    // Notification und WakeLock bleiben unangetastet.
    private fun stopGpsOnly() {
        try {
            lm?.removeUpdates(listener)
        } catch (e: Exception) {
        }
        lm = null
        try {
            fusedCb?.let {
                LocationServices
                    .getFusedLocationProviderClient(this)
                    .removeLocationUpdates(it)
            }
        } catch (e: Exception) {
        }
        fusedCb = null
    }

    private fun stopTracking() {
        stopGpsOnly()
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (e: Exception) {
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
    }
}

fun svcStop(ctx: Context) {

    val i = Intent(ctx, RoundService::class.java)
        .setAction(RoundService.ACTION_STOP)

    try {
        ctx.startService(i)
    } catch (e: Exception) {
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
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {}

    private val ambientObserver =
        AmbientLifecycleObserver(this, ambientCallback)

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(ambientObserver)

        // Zurück wird NICHT mehr hier abgefangen. Die Ebenen-Logik
        // (Seite -> Loch-Screen -> Übersicht -> App zu) sitzt als BackHandler
        // in GolfWatchApp, weil nur dort der aktuelle Zustand bekannt ist.

        // Komfort: Bildschirm bleibt an, solange die App sichtbar ist.
        // (Während einer aktiven Runde erzwingt GolfWatchApp das zusätzlich
        // dynamisch über LocalView.keepScreenOn — unabhängig vom Toggle.)
        if (prefGetB(applicationContext, "keepScreen", true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

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
    val start: LL?
)

// Alles, was der Loch-Screen an Live-Werten anzeigt
private data class PlayLive(
    val hasFix: Boolean,
    val acc: Int?,
    val front: Int?,
    val mid: Int?,
    val back: Int?,
    val pin: Int?,
    val err: String?,
    // Grün-Maße: Tiefe (entlang Spiellinie) x Breite — unabhängig von der Position
    val greenDepth: Int? = null,
    val greenWidth: Int? = null
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

    LaunchedEffect(Live.running, keepPref) {
        rootView.keepScreenOn = Live.running || keepPref
    }

    val scope = rememberCoroutineScope()

    var screen by remember {
        mutableStateOf("home")
    }

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

    var geo by remember {
        mutableStateOf<CourseGeo?>(null)
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

    var idx by remember {
        mutableStateOf(0)
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

    // Rundenumfang: "18 Loch" | "Front 9" | "Back 9" (Vokabular der PWA)
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

    var pinDepth by remember {
        mutableStateOf<Map<Int, Double>>(emptyMap())
    }

    val measurements = remember {
        mutableStateListOf<JSONObject>()
    }

    var weather by remember {
        mutableStateOf<Weather?>(null)
    }

    var caddyMode by remember {
        mutableStateOf(prefGet(ctx, "caddyMode", "bal"))
    }

    var autoHole by remember {
        mutableStateOf(prefGetB(ctx, "autoHole", true))
    }

    var plan by remember {
        mutableStateOf<Caddy.Plan?>(null)
    }

    var rec by remember {
        mutableStateOf<Rec?>(null)
    }

    val ringCache = remember {
        HashMap<Int, List<LL>?>()
    }

    val activity = LocalContext.current as? Activity

    // Veränderbar! Vorher war das ein reines remember{} — "Verwerfen" löschte
    // zwar die Datei, der Zustand blieb aber stehen, also blieb der
    // Fortsetzen-Button sichtbar und arbeitete mit veralteten Daten.
    var resume by remember {
        mutableStateOf(loadLocal(ctx))
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

    fun persist() {
        course?.let {
            saveLocal(
                ctx,
                it,
                tee,
                hi,
                false,
                roundStart,
                entries,
                clubs,
                pinDepth,
                measurements,
                roundId,
                side
            )
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

            val merged = cur.copy(
                score = cur.score ?: inc.score,
                putts = cur.putts ?: inc.putts,
                tee = cur.tee ?: inc.tee,
                appr = cur.appr ?: inc.appr,
                penN = cur.penN ?: inc.penN,
                firstPutt = cur.firstPutt ?: inc.firstPutt,
                quality = cur.quality ?: inc.quality,
                club = cur.club ?: inc.club,
                lie = cur.lie ?: inc.lie,
                distToPin = cur.distToPin ?: inc.distToPin,
                bunkerN = cur.bunkerN ?: inc.bunkerN,
                b1 = cur.b1 ?: inc.b1,
                penType = cur.penType ?: inc.penType,
                ud = cur.ud ?: inc.ud,
                ss = cur.ss ?: inc.ss,
                recovery = cur.recovery ?: inc.recovery,
                gir = cur.gir ?: inc.gir,
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

    fun syncNow() {

        val cs = course ?: return

        status = "sichere…"

        val pending = measurements.toList()

        scope.launch {

            val res = try {

                withContext(Dispatchers.IO) {
                    Net.pushDraft(
                        buildRoundJson(
                            cs,
                            tee,
                            hi,
                            false,
                            entries,
                            weather,
                            roundId,
                            side
                        ),
                        pending,
                        cs.holes.getOrNull(idx)?.hole,
                        cs.name,
                        tee
                    )
                }

            } catch (e: Exception) {
                Net.PushResult(false)
            }

            if (res.ok) {
                // gepushte Messungen sind im Repo -> lokal nicht mehr nötig
                measurements.removeAll(pending)
                res.mergedHoles?.let { adoptHoles(it) }

                // Das Handy hat weitergeblättert -> mitziehen.
                res.remoteHole?.let { h ->
                    val t = cs.holes.indexOfFirst { it.hole == h }
                    if (t >= 0 && t != idx) {
                        idx = t
                        status = "📱 Handy: Loch $h"
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

        syncJob = scope.launch {
            delay(1500)
            syncNow()
        }
    }

    // Speichern und – bei Erfolg – die App schließen
    fun finishAndClose() {

        val cs = course ?: return

        status = "sichere…"

        val pending = measurements.toList()

        scope.launch {

            val res = try {
                withContext(Dispatchers.IO) {
                    Net.pushDraft(
                        buildRoundJson(
                            cs,
                            tee,
                            hi,
                            false,
                            entries,
                            weather,
                            roundId,
                            side
                        ),
                        pending
                    )
                }
            } catch (e: Exception) {
                Net.PushResult(false)
            }

            if (res.ok) {
                status = "✓ gespeichert – schließe…"
                svcStop(ctx)
                delay(700)
                activity?.finish()
            } else {
                status =
                    "⚠ Sync-Fehler – nicht geschlossen"
            }
        }
    }

    fun change(
        hole: Int,
        t: (HoleEntry) -> HoleEntry
    ) {

        entries[hole] =
            t(
                entries[hole]
                    ?: HoleEntry()
            )

        persist()          // lokal SOFORT sichern (jede Eingabe)
        scheduleSync()     // Repo-Sync entprellt anstoßen (jede Eingabe)
    }

    // ---------------- Live-Tracking ----------------

    // WICHTIG für die Reaktionsfähigkeit: Compose merkt sich, welche States
    // gelesen wurden. Ein unbedingtes Live.fix hier hieß, dass JEDER GPS-Tick
    // (1x/s) das komplette GolfWatchApp neu zusammensetzt — auch auf dem
    // Startbildschirm. Genau das waren die stockenden Buttons.
    val fix = if (screen == "play") Live.fix else null

    // Zielpunkt des aktuellen Lochs: Fahne (falls Tiefe bekannt), sonst Grünmitte
    fun targetOf(hole: Int): LL? {
        val g = geo ?: return null
        val d = pinDepth[hole]
        return if (d != null) {
            Geo.pinPoint(g, hole, d, ringCache)
        } else {
            g.holes[hole]?.green
        }
    }

    fun liveOf(hole: Int): PlayLive {

        // Grün-Tiefe/-Breite hängen nur an der Platzkarte, nicht am GPS —
        // deshalb zuerst rechnen und in jedem Fall mitgeben.
        val dims = geo?.let { Geo.greenDims(it, hole, ringCache) }

        val f = fix
            ?: return PlayLive(
                false, null, null, null, null, null, Live.err,
                dims?.first, dims?.second
            )

        val g = geo
            ?: return PlayLive(
                true,
                f.acc.roundToInt(),
                null, null, null, null,
                "keine Platzkarte",
                null, null
            )

        val fmb = Geo.greenFMB(f.ll(), g, hole, ringCache)
        val pinD = pinDepth[hole]

        val pin = if (pinD != null) {
            targetOf(hole)?.let { Geo.dist(f.ll(), it).roundToInt() }
        } else {
            null
        }

        return PlayLive(
            true,
            f.acc.roundToInt(),
            fmb?.front,
            fmb?.mid,
            fmb?.back,
            pin,
            Live.err,
            dims?.first,
            dims?.second
        )
    }

    // Service starten, sobald gespielt wird (Doku 2b) — und beim Verlassen stoppen
    LaunchedEffect(screen) {
        if (screen == "play") {
            askPerms()
            if (!Live.running) {
                svcStart(ctx, "Runde läuft")
            }
        }
    }

    // Notification mit Loch + Stand aktuell halten
    LaunchedEffect(idx, entries.size, screen) {
        if (screen == "play") {
            val cs = course
            if (cs != null) {
                val (op, thru) = overPar()
                val opTxt =
                    if (thru == 0) "±0"
                    else if (op == 0) "E"
                    else if (op > 0) "+$op"
                    else "$op"
                svcNote(
                    ctx,
                    "Loch ${cs.holes[idx].hole} · $opTxt ($thru)"
                )
            }
        }
    }

    // Herzschlag: solange die Runde läuft, alle 3 Minuten pushen — auch ohne
    // Eingabe. Der Push trägt den live-Zeiger, und NUR daran erkennt das Handy,
    // dass überhaupt eine Runde läuft. Ohne diesen Takt erfährt es davon erst,
    // wenn das erste Loch einen Score hat (buildRoundJson lässt leere Löcher weg).
    LaunchedEffect(screen) {
        if (screen == "play") {
            syncNow()                       // sofort beim Betreten der Runde
            while (screen == "play") {
                delay(180_000)
                if (rec == null) syncNow()  // laufende Messung nicht stören
            }
        }
    }

    // Wetter (Open-Meteo) — beim ersten Fix und danach alle 20 Minuten
    LaunchedEffect(screen) {
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
    }

    // Handy -> Uhr: während der Runde alle 90 s den Repo-Entwurf ziehen und
    // fremde Eingaben in LEERE Felder übernehmen (adoptHoles; eigene Eingaben
    // gewinnen immer). Der Push-Weg (syncNow) läuft ohnehin nach jeder Eingabe.
    // Hinweis: Pages-CDN cached die Datei — Latenz realistisch Minuten.
    LaunchedEffect(screen) {
        while (screen == "play") {
            delay(90_000)
            if (rec != null) continue
            val cn = course?.name ?: continue
            val dr = try {
                withContext(Dispatchers.IO) { Net.fetchDraft() }
            } catch (e: Exception) {
                null
            }
            if (dr != null && dr.course == cn && dr.date == today()) {
                adoptHoles(dr.holes)

                // Handy hat weitergeblättert -> mitziehen. Nur ein Zeiger,
                // der jünger ist als der zuletzt selbst gesendete, zählt;
                // sonst würde das eigene Echo die Uhr zurückwerfen.
                val at = dr.liveAt
                val h = dr.liveHole
                if (
                    dr.liveSrc != null && dr.liveSrc != "watch" &&
                    at != null && h != null && at > Net.lastOwnLiveAt()
                ) {
                    val t = course?.holes?.indexOfFirst { it.hole == h } ?: -1
                    if (t >= 0 && t != idx) {
                        idx = t
                        rec = null
                        status = "📱 Handy: Loch $h"
                    }
                }
            }
        }
    }

    // Caddy neu rechnen: nur bei nennenswerter Bewegung (~11 m Raster),
    // Lochwechsel, Moduswechsel oder neuem Wetter — nicht bei jedem GPS-Tick.
    val gridLat = fix?.let { (it.lat * 1e4).roundToInt() }
    val gridLng = fix?.let { (it.lng * 1e4).roundToInt() }

    LaunchedEffect(gridLat, gridLng, idx, caddyMode, weather, clubs.size, geo) {

        val cs = course
        val f = Live.fix
        val g = geo

        if (cs == null || f == null || g == null || clubs.isEmpty()) {
            plan = null
            return@LaunchedEffect
        }

        val hd = cs.holes.getOrNull(idx)
        val target = hd?.let { targetOf(it.hole) }

        if (hd == null || target == null) {
            plan = null
            return@LaunchedEffect
        }

        val p = withContext(Dispatchers.Default) {
            Caddy.plan(
                f.ll(),
                target,
                hd.par,
                clubs,
                g.features,
                weather,
                caddyMode,
                Geo.lieAt(f.ll(), g.features),
                g.holes[hd.hole]?.tee
            )
        }

        plan = p
    }

    // Auto-Loch: nur weiter, wenn das aktuelle Loch einen Score hat, keine
    // Schlagaufnahme läuft und man am Abschlag des FOLGENDEN Lochs steht.
    LaunchedEffect(gridLat, gridLng, autoHole, idx) {

        if (!autoHole || rec != null || screen != "play") return@LaunchedEffect

        val cs = course ?: return@LaunchedEffect
        val g = geo ?: return@LaunchedEffect
        val f = Live.fix ?: return@LaunchedEffect

        val cur = cs.holes.getOrNull(idx) ?: return@LaunchedEffect
        if (entries[cur.hole]?.score == null) return@LaunchedEffect

        val next = cs.holes.getOrNull(idx + 1) ?: return@LaunchedEffect
        val teePt = g.holes[next.hole]?.tee ?: return@LaunchedEffect

        if (Geo.dist(f.ll(), teePt) < 40) {
            idx += 1
            status = "→ Loch ${next.hole}"
            syncNow()
        }
    }

    // ---------------- Schlagtracking ----------------

    fun recBegin() {
        val f = Live.fix
        if (f == null) {
            status = "warte auf GPS…"
            return
        }
        rec = Rec(null, f.ll())
    }

    fun recClub(c: String?) {
        val r = rec ?: return
        rec = Rec(c, Live.fix?.ll() ?: r.start)
    }

    fun recCancel() {
        rec = null
    }

    // Entspricht playRecStop der PWA: Startpunkt wiederverwenden, wenn er <12 m
    // vom letzten Punkt entfernt liegt, danach Endpunkt anhängen (Kette).
    fun recStop() {

        val cs = course ?: return
        val r = rec ?: return
        val hd = cs.holes.getOrNull(idx) ?: return
        val f = Live.fix

        val startP = r.start

        if (startP == null || f == null) {
            status = "warte auf GPS…"
            return
        }

        val endP = f.ll()

        val club = r.club ?: ""
        val cur = entries[hd.hole] ?: HoleEntry()
        val arr = ArrayList(cur.shots)

        if (arr.isEmpty() ||
            Geo.dist(LL(arr.last().lat, arr.last().lng), startP) > 12
        ) {
            arr.add(ShotPt(startP.lat, startP.lng, club))
        } else if (club.isNotEmpty()) {
            arr[arr.size - 1] = arr.last().copy(club = club)
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
                    .put("accA", f.acc.roundToInt())
                    .put("accB", f.acc.roundToInt())
                    .put("latA", round6(startP.lat))
                    .put("lngA", round6(startP.lng))
                    .put("latB", round6(endP.lat))
                    .put("lngB", round6(endP.lng))
                    .put("hole", hd.hole)
            )
        }

        rec = null
        status = "Schlag $len m" + (if (club.isNotEmpty()) " · $club" else "")
        persist()
    }

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

    // ---------------- UI ----------------

    // List-States GEHOISTET (nicht mehr in den Screens selbst):
    // -> Scroll-Position des Loch-Screens bleibt erhalten, wenn ein Picker
    //    aufgeht (vorher sprang die Liste nach jeder Auswahl an den Anfang),
    // -> der PositionIndicator im Scaffold kann den aktiven State anzeigen,
    // -> Drehkrone/Lünette scrollen über rotaryScrollModifier().
    val homeListState = rememberScalingLazyListState()
    val pickListState = rememberScalingLazyListState()
    val playListState = rememberScalingLazyListState()
    val pickerListState = rememberScalingLazyListState()

    // Score-Seite und Detail-Seite scrollen unabhängig voneinander.
    val scoreListState = rememberScalingLazyListState()

    // Seite 0 = Loch/Distanz, 1 = Score, 2 = Details.
    // Der Zustand liegt HIER und nicht in PlayScreen, damit das Öffnen eines
    // Pickers (der die Composition verlässt) die Seite nicht zurücksetzt.
    val pagerState = rememberPagerState(initialPage = 0) { 3 }

    // ---- Zurück: eine Ebene nach oben, statt die App zu schließen ----
    // (activity ist weiter oben in dieser Funktion bereits deklariert)
    var lastBackAt by remember { mutableStateOf(0L) }

    BackHandler {
        when {
            // 1. Picker abbrechen, ohne den Wert anzufassen
            picker != null -> picker = null

            // 2. Auf einer Nebenseite: zurück zur Loch-/Distanzseite
            screen == "play" && pagerState.currentPage != 0 ->
                scope.launch { pagerState.animateScrollToPage(0) }

            // 3. Laufende Runde verlassen: zwei Wischer binnen 2 s.
            //    Die Runde läuft im Service weiter und steht auf dem
            //    Startbildschirm als "Fortsetzen" bereit.
            screen == "play" -> {
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2000L) {
                    lastBackAt = 0L
                    screen = "home"
                } else {
                    lastBackAt = now
                    status = "Nochmal für Übersicht"
                }
            }

            // 4. Platzauswahl -> Startbildschirm
            screen == "pick" -> screen = "home"

            // 5. Startbildschirm -> App schließen
            else -> activity?.finish()
        }
    }

    // Bei Lochwechsel nach oben springen (Loch-Infos + Live sofort sichtbar)
    // und zurück auf die Distanzseite — neues Loch heißt: erst mal schauen.
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
        if (screen == "pick") pickListState.scrollToItem(0)
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
                    screen == "pick" -> pickListState
                    screen == "play" && pagerState.currentPage == 1 -> scoreListState
                    screen == "play" -> playListState
                    else -> homeListState
                }
            )
        }
    ) {

        if (picker != null) {

            val req = picker!!

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

                loading = loading,
                status = status,
                keepScreen = keepPref,
                gpsSource = gpsSource,
                awaitingPhone = awaitingPhone,

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

                            if (d != null && dr != null &&
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

                                    ringCache.clear()
                                    geo = parseGeo(dc.geoRaw)

                                    val pd = HashMap<Int, Double>()
                                    d.pins.forEach { (k, v) ->
                                        val parts = k.split("|")
                                        if (parts.size == 2 && parts[0] == dc.name) {
                                            parts[1].toIntOrNull()?.let { n -> pd[n] = v }
                                        }
                                    }
                                    pinDepth = pd

                                    entries.clear()
                                    measurements.clear()
                                    adoptHoles(dr.holes)

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
                    }
                },

                onCancelFetch = {
                    awaitingPhone = false
                    loading = false
                    status = ""
                },

                // ---- Weg 2: Alleinstart ohne Handy ----
                // Geht IMMER direkt zur Platzauswahl. Kein Blick auf Entwürfe.
                onNew = {

                    askPerms()
                    roundId = null
                    status = ""

                    if (data == null) {
                        loading = true
                        scope.launch {
                            val (d, fresh) =
                                withContext(Dispatchers.IO) { loadData(ctx) }
                            loading = false
                            if (d == null || d.courses.isEmpty()) {
                                status = lastLoadError
                                    ?: "Keine Plätze — einmal mit Netz starten"
                            } else {
                                data = d
                                dataFresh = fresh
                                hi = d.hi
                                if (d.clubs.isNotEmpty()) clubs = d.clubs
                                screen = "pick"
                            }
                        }
                    } else {
                        screen = "pick"
                    }
                },

                onResume = {

                    resume?.let {

                        course = it.course
                        tee = it.tee
                        hi = it.hi
                        clubs = it.clubs
                        pinDepth = it.pinDepth
                        roundId = it.roundId
                        side = it.side

                        measurements.clear()
                        measurements.addAll(it.measurements)

                        ringCache.clear()
                        geo = parseGeo(it.course.geoRaw)

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

                                val (d, fresh) =
                                    withContext(Dispatchers.IO) { loadData(ctx) }

                                dataFresh = fresh

                                if (d != null) {
                                    data = d
                                    if (d.clubs.isNotEmpty()) clubs = d.clubs
                                    val cn = course?.name
                                    if (cn != null) {
                                        val pd = HashMap<Int, Double>()
                                        d.pins.forEach { (k, v) ->
                                            val parts = k.split("|")
                                            if (parts.size == 2 && parts[0] == cn) {
                                                parts[1].toIntOrNull()
                                                    ?.let { n -> pd[n] = v }
                                            }
                                        }
                                        if (pd.isNotEmpty()) pinDepth = pd
                                    }
                                }
                            }
                        }

                        screen = "play"
                    }
                },

                onDiscard = {
                    clearLocal(ctx)
                    svcStop(ctx)
                    // Ohne diese Zeile blieb der Fortsetzen-Button stehen und
                    // arbeitete weiter mit der gerade gelöschten Runde.
                    resume = null
                    entries.clear()
                    measurements.clear()
                    course = null
                    geo = null
                    idx = 0
                    roundStart = null
                    status = "Runde verworfen"
                },

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

            "pick" -> PickScreen(
                data?.courses
                    ?: emptyList(),

                listState = pickListState,

                side = side,

                onSide = {
                    side = when (side) {
                        "18 Loch" -> "Front 9"
                        "Front 9" -> "Back 9"
                        else -> "18 Loch"
                    }
                },

                onPick = { c ->

                    // Rundenumfang anwenden: die Löcher werden gefiltert,
                    // damit Zähler, Fortschritt und Abschluss stimmen.
                    // Filter identisch zu activeHoles() der PWA
                    val picked = when (side) {
                        "Front 9" -> c.copy(holes = c.holes.filter { it.hole <= 9 })
                        "Back 9" -> c.copy(holes = c.holes.filter { it.hole >= 10 })
                        else -> c
                    }

                    course = if (picked.holes.isEmpty()) c else picked
                    tee = c.tee

                    ringCache.clear()
                    geo = parseGeo(c.geoRaw)

                    // Fahnentiefen dieses Platzes übernehmen
                    val pd = HashMap<Int, Double>()
                    data?.pins?.forEach { (k, v) ->
                        val parts = k.split("|")
                        if (parts.size == 2 && parts[0] == c.name) {
                            parts[1].toIntOrNull()?.let { n -> pd[n] = v }
                        }
                    }
                    pinDepth = pd

                    entries.clear()
                    measurements.clear()

                    idx = 0
                    roundStart = System.currentTimeMillis()

                    clearLocal(ctx)
                    resume = null

                    screen = "play"
                },

                onBack = {
                    screen = "home"
                }
            )

            "play" -> {

                val cs = course

                LaunchedEffect(cs) {
                    if (cs == null) screen = "home"
                }

                if (cs != null) {

                    val hd = cs.holes[idx]
                    val e =
                        entries[hd.hole]
                            ?: HoleEntry()

                    val opts = data?.opts
                    val (opNow, thruNow) = overPar()

                    val live = liveOf(hd.hole)

                    val recDist =
                        if (rec?.start != null && fix != null) {
                            Geo.dist(rec!!.start!!, fix.ll()).roundToInt()
                        } else {
                            null
                        }

                    PlayPager(

                        pagerState = pagerState,
                        planHole = data?.plans
                            ?.get("${cs.name}|${cs.tee}")
                            ?.get(hd.hole),
                        detailListState = playListState,
                        scoreListState = scoreListState,
                        course = cs,
                        hd = hd,
                        entry = e,
                        idx = idx,
                        total = cs.holes.size,
                        status = status,
                        opts = opts,
                        toPar = opNow,
                        thru = thruNow,

                        onHome = { screen = "home" },

                        live = live,
                        plan = plan,
                        weatherLine = Wx.line(weather),
                        caddyMode = caddyMode,
                        autoHole = autoHole,

                        recActive = rec != null,
                        recClubName = rec?.club,
                        recDist = recDist,
                        shotCount = max(0, e.shots.size - 1),

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

                        onDistFromGps = {
                            val m = live.pin ?: live.mid
                            if (m != null) {
                                change(hd.hole) {
                                    it.copy(distToPin = m.coerceIn(0, 250))
                                }
                                status = "Pin-Distanz $m m"
                            } else {
                                status = "keine Live-Distanz"
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

                        onCaddyMode = {
                            caddyMode =
                                when (caddyMode) {
                                    "safe" -> "bal"
                                    "bal" -> "aggr"
                                    else -> "safe"
                                }
                            prefSet(ctx, "caddyMode", caddyMode)
                        },

                        onAutoHole = {
                            autoHole = !autoHole
                            prefSetB(ctx, "autoHole", autoHole)
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

                        onShotStop = { recStop() },
                        onShotCancel = { recCancel() },
                        onShotUndo = { recUndo() },

                        onPrev = {
                            if (idx > 0) {
                                idx--
                                rec = null
                                syncNow()
                            }
                        },

                        onNext = {
                            if (
                                idx <
                                cs.holes.size - 1
                            ) {
                                idx++
                                rec = null
                                syncNow()
                            }
                        },

                        onFinish = {
                            finishAndClose()
                        }
                    )
                }
            }
        }
    }
}

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

    val scope = rememberCoroutineScope()

    val focus = remember {
        FocusRequester()
    }

    // Im Pager sind mehrere Seiten gleichzeitig komponiert. Ohne die
    // active-Bedingung fordern alle den Fokus an und die Krone scrollt
    // eine Liste, die man gar nicht sieht.
    LaunchedEffect(active) {
        if (active) focus.requestFocus()
    }

    return Modifier
        .onRotaryScrollEvent { ev ->
            scope.launch {
                state.scrollBy(ev.verticalScrollPixels)
            }
            true
        }
        .focusRequester(focus)
        .focusable()
}

// Gleiche Mechanik für eine einfache Column mit verticalScroll.
@Composable
private fun rotaryScrollModifier(
    state: ScrollState,
    active: Boolean = true
): Modifier {

    val scope = rememberCoroutineScope()

    val focus = remember {
        FocusRequester()
    }

    LaunchedEffect(active) {
        if (active) focus.requestFocus()
    }

    return Modifier
        .onRotaryScrollEvent { ev ->
            scope.launch {
                state.scrollBy(ev.verticalScrollPixels)
            }
            true
        }
        .focusRequester(focus)
        .focusable()
}

@Composable
private fun HomeScreen(
    listState: ScalingLazyListState,
    hasResume: Boolean,
    resumeLabel: String,
    loading: Boolean,
    status: String,
    keepScreen: Boolean,
    gpsSource: String,
    awaitingPhone: Boolean,
    onFetchPhone: () -> Unit,
    onCancelFetch: () -> Unit,
    onNew: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onKeepScreen: (Boolean) -> Unit,
    onGpsSource: (String) -> Unit
) {

    var keep by remember {
        mutableStateOf(keepScreen)
    }

    var gps by remember {
        mutableStateOf(gpsSource)
    }

    // „Verwerfen" nur mit Bestätigung (zweiter Tap innerhalb von 4 s) —
    // schützt die lokal gesicherte Runde vor einem versehentlichen Tipper.
    var confirmDiscard by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(confirmDiscard) {
        if (confirmDiscard) {
            delay(4000)
            confirmDiscard = false
        }
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

        item {

            Text(
                "⛳ Golf-Runde",
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme.typography.title2
            )
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

            item {

                Chip(
                    onClick = {
                        if (confirmDiscard) {
                            confirmDiscard = false
                            onDiscard()
                        } else {
                            confirmDiscard = true
                        }
                    },
                    label = {
                        Text(
                            if (confirmDiscard)
                                "Wirklich verwerfen?"
                            else
                                "Verwerfen",
                            color =
                                if (confirmDiscard)
                                    RedC
                                else
                                    Color.Unspecified
                        )
                    },
                    secondaryLabel = {
                        Text(
                            if (confirmDiscard)
                                "erneut tippen = endgültig löschen"
                            else
                                "gespeicherte Runde löschen",
                            maxLines = 1
                        )
                    },
                    colors =
                        ChipDefaults.secondaryChipColors(),
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
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
                        if (awaitingPhone)
                            "Suche abbrechen"
                        else
                            "📱 Runde vom Handy"
                    )
                },
                secondaryLabel = {
                    Text(
                        if (awaitingPhone)
                            "sucht bis zu 2 Minuten"
                        else
                            "am Handy starten, hier holen",
                        maxLines = 1
                    )
                },
                colors =
                    if (awaitingPhone)
                        ChipDefaults.secondaryChipColors()
                    else if (hasResume)
                        ChipDefaults.secondaryChipColors()
                    else
                        ChipDefaults.primaryChipColors(),
                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        // Notnagel: Handy vergessen oder leer. Bewusst kleiner und
        // sekundär — mit den Einschränkungen im Nebentext.
        item {

            CompactChip(
                onClick = onNew,
                label = {
                    Text("⌚ ohne Handy starten", fontSize = 12.sp)
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {

            Chip(
                onClick = {
                    keep = !keep
                    onKeepScreen(keep)
                },
                label = {
                    Text("Display an")
                },
                secondaryLabel = {
                    Text(if (keep) "immer" else "normal")
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
                    Text("GPS-Quelle")
                },
                secondaryLabel = {
                    Text(
                        if (gps == "phone")
                            "📱 Handy (spart Uhr-Akku)"
                        else
                            "⌚ Uhr (eigenes GPS)"
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
    }
}

@Composable
private fun PickScreen(
    courses: List<CourseDef>,
    listState: ScalingLazyListState,
    side: String,
    onSide: () -> Unit,
    onPick: (CourseDef) -> Unit,
    onBack: () -> Unit
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
                "Platz & Tee",
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme.typography.title3
            )
        }

        // Rundenumfang: beim Alleinstart die einzige Stelle, an der er
        // gesetzt werden kann. Vorher war "18 Loch" hart verdrahtet.
        item {

            CompactChip(
                onClick = onSide,
                label = { Text(side, fontSize = 12.sp) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(courses) { c ->

            Chip(
                onClick = {
                    onPick(c)
                },
                label = {
                    Text(
                        c.name,
                        maxLines = 2
                    )
                },
                secondaryLabel = {
                    Text(
                        "${c.tee} · ${c.holes.size} Loch" +
                                (if (c.geoRaw != null) " · Karte" else "")
                    )
                },
                colors =
                    ChipDefaults.primaryChipColors(),
                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        item {

            Chip(
                onClick = onBack,
                label = {
                    Text("Zurück")
                },
                colors =
                    ChipDefaults.secondaryChipColors(),
                modifier =
                    Modifier.fillMaxWidth()
            )
        }
    }
}

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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WizBtn(
    label: String,
    sub: String? = null,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) Pine else MaterialTheme.colors.surface)
            .combinedClickable(
                onClick = {
                    haptics.performHapticFeedback(
                        HapticFeedbackType.LongPress
                    )
                    onClick()
                },
                onLongClick = onLongClick?.let { lc ->
                    {
                        haptics.performHapticFeedback(
                            HapticFeedbackType.LongPress
                        )
                        lc()
                    }
                }
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (primary) Color.White else MaterialTheme.colors.onSurface
        )
        if (sub != null) {
            Text(
                sub,
                fontSize = 10.sp,
                color = if (primary) Color(0xCCFFFFFF) else InkFaint
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlayPager(
    pagerState: PagerState,
    planHole: PlanHole?,
    detailListState: ScalingLazyListState,
    scoreListState: ScalingLazyListState,
    course: CourseDef,
    hd: HoleDef,
    entry: HoleEntry,
    idx: Int,
    total: Int,
    status: String,
    opts: Options?,
    toPar: Int,
    thru: Int,
    onHome: () -> Unit,
    live: PlayLive,
    plan: Caddy.Plan?,
    weatherLine: String?,
    caddyMode: String,
    autoHole: Boolean,
    recActive: Boolean,
    recClubName: String?,
    recDist: Int?,
    shotCount: Int,
    onScore: (Int) -> Unit,
    onPutts: (Int) -> Unit,
    onPen: (Int) -> Unit,
    onDistFromGps: () -> Unit,
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
    onCaddyMode: () -> Unit,
    onAutoHole: () -> Unit,
    onShotBegin: () -> Unit,
    onShotClub: () -> Unit,
    onShotStop: () -> Unit,
    onShotCancel: () -> Unit,
    onShotUndo: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
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

                0 -> HolePage(
                    hd = hd,
                    entry = entry,
                    planHole = planHole,
                    toPar = toPar,
                    thru = thru,
                    live = live,
                    plan = plan,
                    weatherLine = weatherLine,
                    caddyMode = caddyMode,
                    recActive = recActive,
                    recClubName = recClubName,
                    recDist = recDist,
                    shotCount = shotCount,
                    onCaddyMode = onCaddyMode,
                    onShotBegin = onShotBegin,
                    onShotClub = onShotClub,
                    onShotStop = onShotStop,
                    onShotCancel = onShotCancel,
                    onShotUndo = onShotUndo,
                    active = pagerState.currentPage == 0
                )

                1 -> ScorePage(
                    active = pagerState.currentPage == 1,
                    listState = scoreListState,
                    course = course,
                    hd = hd,
                    entry = entry,
                    idx = idx,
                    total = total,
                    status = status,
                    opts = opts,
                    toPar = toPar,
                    thru = thru,
                    autoHole = autoHole,
                    onScore = onScore,
                    onPutts = onPutts,
                    onDistFromGps = onDistFromGps,
                    onPick = onPick,
                    onAutoHole = onAutoHole,
                    onPrev = onPrev,
                    onNext = onNext,
                    onFinish = onFinish,
                    onHome = onHome
                )

                else -> DetailPage(
                    active = pagerState.currentPage == 2,
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
                    get() = 3
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
//  SEITE 0 — LOCH: schauen, nicht tippen.
//  Die Mitteldistanz ist die eine Zahl, auf die man 18-mal pro
//  Runde im Vorbeigehen schaut. Sie bekommt deshalb den Platz.
// ============================================================

@Composable
private fun HolePage(
    hd: HoleDef,
    entry: HoleEntry,
    planHole: PlanHole?,
    toPar: Int,
    thru: Int,
    live: PlayLive,
    plan: Caddy.Plan?,
    weatherLine: String?,
    caddyMode: String,
    recActive: Boolean,
    recClubName: String?,
    recDist: Int?,
    shotCount: Int,
    onCaddyMode: () -> Unit,
    onShotBegin: () -> Unit,
    onShotClub: () -> Unit,
    onShotStop: () -> Unit,
    onShotCancel: () -> Unit,
    onShotUndo: () -> Unit,
    active: Boolean
) {

    val op =
        if (thru == 0) "±0"
        else if (toPar == 0) "E"
        else if (toPar > 0) "+$toPar"
        else "$toPar"

    // Der Inhalt kann je nach Wetter-/Plan-Lage länger werden als das
    // Display. Ohne Scroll rutschte die Schlag-Zeile unten heraus.
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .then(rotaryScrollModifier(scroll, active))
            .padding(top = 22.dp, bottom = 16.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            "L${hd.hole} · Par ${hd.par}" +
                    (if (hd.len > 0) " · ${hd.len}m" else "") +
                    " · $op" +
                    // Score des Lochs hier statt als eigener Chip unten —
                    // der kostete die Höhe, die dem Schlagtracking fehlte.
                    (entry.score?.let { " · ✓$it" } ?: ""),
            fontSize = 12.sp,
            color = GoldText,
            maxLines = 1
        )

        Spacer(Modifier.height(2.dp))

        if (live.hasFix && live.mid != null) {
            Text(
                "${live.mid}",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "m Mitte",
                fontSize = 10.sp,
                color = InkFaint
            )
            Text(
                (live.front?.let { "F $it" } ?: "F –") +
                        "    " +
                        (live.back?.let { "B $it" } ?: "B –") +
                        (live.pin?.let { "    ⛳ $it" } ?: ""),
                fontSize = 12.sp,
                color = InkC,
                maxLines = 1
            )

            // Grünmaße wie in der PWA ("Grün ca. X m tief · Y m breit").
            // Beim Umbau auf den Pager verlorengegangen, hier wiederhergestellt:
            // ohne die Tiefe sagt der Abstand F/B nichts über die Fahnenlage.
            if (live.greenDepth != null && live.greenWidth != null) {
                Text(
                    "Grün ${live.greenDepth} m tief · ${live.greenWidth} m breit",
                    fontSize = 10.sp,
                    color = InkFaint,
                    maxLines = 1
                )
            }
        } else {
            Text(
                "– –",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = InkFaint
            )
            Text(
                live.err ?: "warte auf GPS…",
                fontSize = 10.sp,
                color = InkFaint
            )
        }

        Spacer(Modifier.height(4.dp))

        // Gameplan aus der PWA (📋, windneutral) — steht ÜBER der Caddy-Zeile,
        // weil er die vorab gefasste Absicht ist; der Caddy rechnet nur die
        // Tagesbedingungen darauf. Bewusst NICHT an plan != null gekoppelt:
        // ohne GPS-Fix ist der Gameplan die einzige Empfehlung, die es gibt.
        if (!recActive && planHole != null) {
            Text(
                "📋 ${planHole.club}" +
                        (if (planHole.desc.isNotEmpty()) " · ${planHole.desc}" else ""),
                fontSize = 11.sp,
                color = PineText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // Laufende Schlagmessung verdrängt den Caddy — in dem Moment
        // interessiert nur die eine Zahl.
        if (recActive) {
            Text(
                "📐 ${recDist ?: 0} m" + (recClubName?.let { " · $it" } ?: ""),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GoldText
            )
            Text(
                "beim Ball ■ tippen zum Speichern",
                fontSize = 9.sp,
                color = InkFaint,
                textAlign = TextAlign.Center
            )
        } else if (plan != null) {

            val sgn = { v: Int -> if (v > 0) "+$v" else "$v" }

            // Die RECHNUNG statt nur des Ergebnisses: gemessen -> spielt wie.
            // Ohne den Pfeil sieht man nicht, dass 148 und 152 zusammengehören,
            // und die Korrektur wirkt wie eine zweite, konkurrierende Zahl.
            // Rechnung und Aufschlüsselung in EINER Zeile — zwei Zeilen
            // kosteten den Platz, den die Schlag-Zeile unten braucht.
            // Anteile erscheinen AUCH bei 0 (gedimmt), sonst sind
            // "kein Einfluss" und "keine Daten" nicht unterscheidbar.
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${plan.target}→${plan.plays}m",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    (plan.windArrow ?: "≈") +
                            " " + sgn(plan.windM) +
                            (plan.windKmh?.let { " (${it})" } ?: ""),
                    fontSize = 11.sp,
                    color = if (plan.windM == 0) InkFaint else InkC,
                    maxLines = 1
                )
                Text(
                    "🌡 ${sgn(plan.tempM)}",
                    fontSize = 11.sp,
                    color = if (plan.tempM == 0) InkFaint else InkC,
                    maxLines = 1
                )
                if (plan.lieM != 0) {
                    Text(
                        "Lage ${sgn(plan.lieM)}",
                        fontSize = 11.sp,
                        color = InkC,
                        maxLines = 1
                    )
                }
            }

            Text(
                "🎯 ${plan.headline} · ${Caddy.modeLabel(caddyMode)}",
                fontSize = 11.sp,
                color = GoldText,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCaddyMode() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            if (plan.warn != null) {
                Text(
                    "⚠ ${plan.warn}",
                    fontSize = 10.sp,
                    color = RedC,
                    maxLines = 1
                )
            }
        } else if (weatherLine != null) {
            Text(
                weatherLine,
                fontSize = 11.sp,
                color = InkFaint,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(6.dp))

        // Schlagaufnahme: die einzige Eingabe, die auf diese Seite gehört,
        // weil sie beim Ball passiert und nicht nach dem Einlochen.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            CompactChip(
                onClick = { if (recActive) onShotStop() else onShotBegin() },
                label = {
                    Text(
                        if (recActive) "■ stop" else "📐 $shotCount",
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                },
                colors =
                    if (recActive) ChipDefaults.primaryChipColors()
                    else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.weight(1f)
            )
            CompactChip(
                onClick = { if (recActive) onShotCancel() else onShotUndo() },
                label = {
                    Text(
                        if (recActive) "✕" else "↶",
                        fontSize = 13.sp
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.weight(0.5f)
            )
            CompactChip(
                onClick = onShotClub,
                label = {
                    Text(
                        recClubName?.take(5) ?: "Schl.",
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.weight(1f)
            )
        }

    }
}

// ============================================================
//  SEITE 1 — SCORE: eintragen, was nach dem Einlochen feststeht.
//  Hier liegen auch die Rundenaktionen (Lochwechsel, Abschluss,
//  Übersicht), weil das die „verwalten"-Seite ist.
// ============================================================

@Composable
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
    toPar: Int,
    thru: Int,
    autoHole: Boolean,
    onScore: (Int) -> Unit,
    onPutts: (Int) -> Unit,
    onDistFromGps: () -> Unit,
    onPick: (
        String,
        List<String>,
        String?,
        (HoleEntry, String?) -> HoleEntry
    ) -> Unit,
    onAutoHole: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onHome: () -> Unit
) {

    var confirmFinish by remember { mutableStateOf(false) }

    LaunchedEffect(confirmFinish) {
        if (confirmFinish) {
            delay(4000)
            confirmFinish = false
        }
    }

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
                    "Loch ${hd.hole} · Par ${hd.par}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.title3,
                    color = PineText
                )
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

            item {
                SelectRow("Tee-Schläger", entry.club) {
                    onPick(
                        "Tee-Schläger",
                        opts.teeClubs,
                        entry.club
                    ) { e, s -> e.copy(club = s) }
                }
            }

            item {
                SelectRow("Approach-Distanz", entry.appr) {
                    onPick(
                        "Approach-Distanz",
                        opts.approachBuckets,
                        entry.appr
                    ) { e, s -> e.copy(appr = s) }
                }
            }
        }

        // Entspricht "Pin-Dist. nach Approach (m)" der PWA. Dort ein freies
        // Zahlenfeld — auf der Uhr nicht tippbar, deshalb eine Auswahl.
        // Feintritt bis 20 m (dort entscheidet sich Up&Down), darüber grob.
        item {
            SelectRow("Rest z. Fahne", entry.distToPin?.let { "$it m" }) {
                onPick(
                    "Rest zur Fahne (m)",
                    DIST_TO_PIN_CHOICES,
                    entry.distToPin?.let { "$it m" }
                ) { e, sel ->
                    e.copy(
                        distToPin = sel?.removeSuffix(" m")?.trim()?.toIntOrNull()
                    )
                }
            }
        }

        item {
            CompactChip(
                onClick = onDistFromGps,
                label = { Text("aus GPS übernehmen", fontSize = 12.sp) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Score per Stepper. Der Startwert beim ersten Tipp ist Par —
        // das ist der häufigste Fall und spart Klicks in beide Richtungen.
        item {
            Stepper(
                "Score",
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
                fontSize = 11.sp,
                color =
                    if (d == null) InkFaint
                    else if (d < 0) PineText
                    else if (d == 0) GoldText
                    else RedC
            )
        }

        item {
            Stepper(
                "Putts",
                (entry.putts ?: 2).toString(),
                { onPutts(-1) },
                { onPutts(1) }
            )
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

        item {
            Chip(
                onClick = onAutoHole,
                label = { Text("Auto-Loch") },
                secondaryLabel = { Text(if (autoHole) "an" else "aus") },
                colors =
                    if (autoHole) ChipDefaults.primaryChipColors()
                    else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Chip(
                onClick = {
                    if (confirmFinish) {
                        confirmFinish = false
                        onFinish()
                    } else {
                        confirmFinish = true
                    }
                },
                label = {
                    Text(
                        if (confirmFinish) "Wirklich abschließen?"
                        else "Sichern & abschließen"
                    )
                },
                secondaryLabel = {
                    Text(
                        if (confirmFinish) "erneut tippen — App schließt sich"
                        else "$thru von $total Löchern erfasst",
                        maxLines = 1
                    )
                },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Rückweg zur Übersicht — die Runde läuft im Service weiter
        // und steht dort als „Fortsetzen" bereit.
        item {
            CompactChip(
                onClick = onHome,
                label = { Text("‹ Übersicht", fontSize = 12.sp) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
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
    }
}

// ============================================================
//  SEITE 2 — DETAILS: Trainingsdaten, die beim Spielen warten
//  können. Alles optional, nichts blockiert den Rundenablauf.
// ============================================================

@Composable
private fun DetailPage(
    active: Boolean,
    listState: ScalingLazyListState,
    hd: HoleDef,
    entry: HoleEntry,
    opts: Options?,
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

    val detailCount = listOf<Any?>(
        entry.gir, entry.firstPutt, entry.quality, entry.club,
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
                    fontSize = 11.sp,
                    color = InkFaint
                )
            }
        }

        if (opts != null) {
            item {
                SelectRow("1. Putt", entry.firstPutt) {
                    onPick(
                        "1.-Putt-Distanz",
                        opts.firstPuttDist,
                        entry.firstPutt
                    ) { e, s -> e.copy(firstPutt = s) }
                }
            }

            item {
                SelectRow("Approach-Lage", entry.lie) {
                    onPick(
                        "Approach-Lage",
                        opts.approachLies,
                        entry.lie
                    ) { e, s -> e.copy(lie = s) }
                }
            }

            item {
                SelectRow("Quality", entry.quality) {
                    onPick(
                        "Quality",
                        opts.qualityOpts,
                        entry.quality
                    ) { e, s -> e.copy(quality = s) }
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

        item {
            Stepper(
                "Penalty Anzahl",
                entry.penN?.toString() ?: "–",
                { onPen(-1) },
                { onPen(1) }
            )
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

        item { SectionLabel("Kurzes Spiel") }

        item { ToggleRow("Up & Down", entry.ud, onUd) }
        item { ToggleRow("Sand Save", entry.ss, onSs) }
        item { ToggleRow("Recovery", entry.recovery, onRec) }

        item { Spacer(Modifier.height(12.dp)) }
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
