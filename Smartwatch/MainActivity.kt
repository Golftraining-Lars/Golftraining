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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
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
 *     - Kopf des Loch-Screens zeigt IMMER „über Par ±X · N Loch" (overPar()).
 *       Farbe: rot über Par, grün unter Par, gold bei E.
 *     - Darunter der LIVE-BLOCK: F/M/B zum Grün (bzw. Fahne), GPS-Genauigkeit.
 *     - Danach der CADDY (Schlägerempfehlung + „spielt wie" + Gefahren).
 *     - Danach das SCHLAGTRACKING (Schlag hier -> Schläger -> Ball erreicht).
 *     - KEIN Rundentimer (bewusst entfernt); roundStart wird intern gespeichert.
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
 *     Caddy.plan(...) — bewusst die schlanke Variante der PWA-Logik
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

data class AppData(
    val courses: List<CourseDef>,
    val opts: Options,
    val hi: Double?,
    val clubs: List<ClubDist>,
    val pins: Map<String, Double>   // "<Platz>|<Loch>" -> Fahnentiefe 0..1
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

    // Sicherheitsmarge über die nötige Carry-Distanz je Modus
    private fun margin(mode: String) =
        when (mode) {
            "safe" -> 12
            "aggr" -> 0
            else -> 6
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
        val warn: String?            // rot hervorgehobene Warnung
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
        lie: String
    ): Plan {

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
            // „Nie darüber": Layup deutlich davor
            val layup = max(30, hard.near - 20)
            club = pick(clubs, layup, true)
            warn = "${kindLabel(hard.kind)} ab ${hard.near} m — nicht drüber"
            lines.add("Layup auf ~$layup m")
            headline = club?.let { "${short(it.club)} · $layup m" } ?: "Layup $layup m"

            return Plan(headline, club?.club, dist, plays, lines.take(3), warn)
        }

        if (carry != null) {
            val needCarry = carry.far + margin(mode)
            val best = clubs.maxByOrNull { it.carryOrReach }
            val canCarry = (best?.carryOrReach ?: 0) >= needCarry

            if (!canCarry || (mode == "safe" && needCarry > need)) {
                val layup = max(30, carry.near - 15)
                club = pick(clubs, layup, true)
                warn = "${kindLabel(carry.kind)} ${carry.near}–${carry.far} m"
                lines.add("Ablegen auf ~$layup m (Carry ${needCarry} m nicht sicher)")
                headline = club?.let { "${short(it.club)} · $layup m" } ?: "Layup $layup m"
                return Plan(headline, club?.club, dist, plays, lines.take(3), warn)
            }

            lines.add("${kindLabel(carry.kind)} bis ${carry.far} m → Carry ${needCarry} m")
        }

        // Normale Schlägerwahl: ins Grün nach Carry, sonst nach Reichweite
        val approach = par == 3 || need <= (clubs.maxOfOrNull { it.carryOrReach } ?: 0)
        club = pick(clubs, need, approach)

        // Bunker als Landegefahr: liegt der gewählte Schläger in einem Bunker?
        val chosen = club
        if (chosen != null) {
            val reach = if (approach) chosen.carryOrReach else chosen.reach
            val inBunker = land.firstOrNull {
                reach >= it.near - 8 && reach <= it.far + 8
            }
            if (inBunker != null) {
                val alt = clubs
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

    fun fetchData(): AppData {
        val c = URL(DATA_URL).openConnection() as HttpURLConnection

        c.requestMethod = "GET"
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("Cache-Control", "no-cache")

        val db = JSONObject(
            c.inputStream.bufferedReader().use { it.readText() }
        )

        c.disconnect()

        val courses = ArrayList<CourseDef>()
        val ca = db.optJSONArray("courses") ?: JSONArray()

        for (i in 0 until ca.length()) {
            val co = ca.getJSONObject(i)
            val name = co.optString("name")

            val tees = co.optJSONObject("tees") ?: continue
            val teeName = tees.keys().asSequence().firstOrNull() ?: continue

            val ha = tees
                .getJSONObject(teeName)
                .optJSONArray("holes") ?: continue

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
                        co.optJSONObject("geo")?.toString()
                    )
                )
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
            pins
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
    fun pushDraft(
        round: JSONObject,
        shotMeasurements: List<JSONObject>
    ): Boolean {

        val g = URL(DATA_URL).openConnection() as HttpURLConnection

        g.requestMethod = "GET"
        g.connectTimeout = 15000
        g.readTimeout = 15000
        g.setRequestProperty("Cache-Control", "no-cache")

        val db = JSONObject(
            g.inputStream.bufferedReader().use { it.readText() }
        )

        g.disconnect()

        db.put(
            "_draftRound",
            JSONObject()
                .put("round", round)
                .put("ts", isoNow())
        )

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

        return code in 200..299
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
    weather: Weather?
): JSONObject {

    val r = JSONObject()

    r.put("date", today())
    r.put("course", course.name)
    r.put("tee", tee)
    r.put("side", "18 Loch")
    r.put("type", "18 Loch")
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

    e.gir?.let {
        put("gir", it)
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
    measurements: List<JSONObject>
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
    val measurements: List<JSONObject>
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
                ms
            )
        }

    } catch (e: Exception) {
        null
    }
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
        const val EXTRA_NOTE = "note"

        private const val CHANNEL = "golfround"
        private const val NOTIF_ID = 4711
    }

    private var lm: LocationManager? = null
    private var wake: PowerManager.WakeLock? = null
    private var started = false

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
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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

    private fun stopTracking() {
        try {
            lm?.removeUpdates(listener)
        } catch (e: Exception) {
        }
        lm = null
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (e: Exception) {
        }
        wake = null
        started = false
        Live.running = false
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

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        // Komfort: Bildschirm bleibt an, solange die App sichtbar ist.
        // Die eigentliche Absicherung gegen „App geht in den Hintergrund"
        // ist jetzt der Foreground-Service (siehe Doku 2b) — dieser Flag ist
        // nur noch Bequemlichkeit und über die Einstellung abschaltbar.
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
    val err: String?
)

@Composable
fun GolfWatchApp(
    ctx: Context
) {

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

    val resume = remember {
        loadLocal(ctx)
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
                measurements
            )
        }
    }

    // Repo-Sync SOFORT (nutzt den bereits lokal gesicherten Stand). Lokal ist durch
    // persist() bei jeder Eingabe ohnehin schon gesichert; dieser Push bringt den
    // Entwurf zusätzlich ins Repo.
    fun syncNow() {

        val cs = course ?: return

        status = "sichere…"

        val pending = measurements.toList()

        scope.launch {

            val ok = try {

                withContext(Dispatchers.IO) {
                    Net.pushDraft(
                        buildRoundJson(
                            cs,
                            tee,
                            hi,
                            false,
                            entries,
                            weather
                        ),
                        pending
                    )
                }

            } catch (e: Exception) {
                false
            }

            if (ok) {
                // gepushte Messungen sind im Repo -> lokal nicht mehr nötig
                measurements.removeAll(pending)
                persist()
                status = "✓ gesichert (${scored()})"
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

            val ok = try {
                withContext(Dispatchers.IO) {
                    Net.pushDraft(
                        buildRoundJson(
                            cs,
                            tee,
                            hi,
                            false,
                            entries,
                            weather
                        ),
                        pending
                    )
                }
            } catch (e: Exception) {
                false
            }

            if (ok) {
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

    val fix = Live.fix

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

        val f = fix
            ?: return PlayLive(false, null, null, null, null, null, Live.err)

        val g = geo
            ?: return PlayLive(
                true,
                f.acc.roundToInt(),
                null, null, null, null,
                "keine Platzkarte"
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
            Live.err
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
                Geo.lieAt(f.ll(), g.features)
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

    Scaffold(
        timeText = {
            TimeText()
        },
        vignette = {
            Vignette(
                vignettePosition =
                    VignettePosition.TopAndBottom
            )
        }
    ) {

        if (picker != null) {

            val req = picker!!

            PickerScreen(
                req.title,
                req.options,
                req.current
            ) { sel ->
                req.onSelect(sel)
                picker = null
            }

            return@Scaffold
        }

        when (screen) {

            "home" -> HomeScreen(
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
                keepScreen = prefGetB(ctx, "keepScreen", true),

                onNew = {

                    loading = true
                    status = ""
                    askPerms()

                    scope.launch {

                        val d =
                            try {
                                withContext(Dispatchers.IO) {
                                    Net.fetchData()
                                }
                            } catch (e: Exception) {
                                null
                            }

                        loading = false

                        if (
                            d == null ||
                            d.courses.isEmpty()
                        ) {

                            status =
                                "Keine Daten geladen (Netz?)"

                        } else {

                            data = d
                            hi = d.hi
                            clubs = d.clubs
                            screen = "pick"
                        }
                    }
                },

                onResume = {

                    resume?.let {

                        course = it.course
                        tee = it.tee
                        hi = it.hi
                        clubs = it.clubs
                        pinDepth = it.pinDepth

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

                        // Frische Daten (Schläger, Fahnen, HI) nachladen, falls Netz da ist
                        if (data == null) {

                            scope.launch {

                                val d =
                                    try {
                                        withContext(
                                            Dispatchers.IO
                                        ) {
                                            Net.fetchData()
                                        }
                                    } catch (e: Exception) {
                                        null
                                    }

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
                    status = "Verworfen"
                },

                onKeepScreen = { v ->
                    prefSetB(ctx, "keepScreen", v)
                    status = "Display-Einstellung gilt ab Neustart"
                }
            )

            "pick" -> PickScreen(
                data?.courses
                    ?: emptyList(),

                onPick = { c ->

                    course = c
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

                    screen = "play"
                },

                onBack = {
                    screen = "home"
                }
            )

            "play" -> {

                val cs = course

                if (cs == null) {

                    screen = "home"

                } else {

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

                    PlayScreen(

                        course = cs,
                        hd = hd,
                        entry = e,
                        idx = idx,
                        total = cs.holes.size,
                        status = status,
                        opts = opts,
                        toPar = opNow,
                        thru = thruNow,

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
                                            )
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

                        onDist = { d ->
                            change(hd.hole) {
                                it.copy(
                                    distToPin =
                                        (
                                                it.distToPin
                                                    ?: 0
                                                )
                                            .plus(d)
                                            .coerceIn(
                                                0,
                                                250
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

                        onGir = {
                            change(hd.hole) {
                                it.copy(
                                    gir = cycle(it.gir)
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

@Composable
private fun HomeScreen(
    hasResume: Boolean,
    resumeLabel: String,
    loading: Boolean,
    status: String,
    keepScreen: Boolean,
    onNew: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onKeepScreen: (Boolean) -> Unit
) {

    var keep by remember {
        mutableStateOf(keepScreen)
    }

    ScalingLazyColumn(
        state =
            rememberScalingLazyListState(),
        modifier =
            Modifier.fillMaxSize(),
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
                    onClick = onDiscard,
                    label = {
                        Text("Verwerfen")
                    },
                    colors =
                        ChipDefaults.secondaryChipColors(),
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
        }

        item {

            Chip(
                onClick = onNew,
                label = {
                    Text(
                        if (hasResume)
                            "Neue Runde"
                        else
                            "Runde starten"
                    )
                },
                colors =
                    if (hasResume)
                        ChipDefaults.secondaryChipColors()
                    else
                        ChipDefaults.primaryChipColors(),
                modifier =
                    Modifier.fillMaxWidth()
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
    onPick: (CourseDef) -> Unit,
    onBack: () -> Unit
) {

    ScalingLazyColumn(
        state =
            rememberScalingLazyListState(),
        modifier =
            Modifier.fillMaxSize(),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        item {

            Text(
                "Platz wählen",
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme.typography.title3
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
    onSelect: (String?) -> Unit
) {

    ScalingLazyColumn(
        state =
            rememberScalingLazyListState(),
        modifier =
            Modifier.fillMaxSize(),
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

// ---- Live-Block: Front/Mitte/Back bzw. Fahne + GPS-Güte ----

@Composable
private fun LiveBlock(
    live: PlayLive
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {

        if (!live.hasFix) {
            Text(
                live.err ?: "GPS sucht…",
                style = MaterialTheme.typography.caption1,
                color = GoldText
            )
            return@Column
        }

        val main = live.pin ?: live.mid

        if (main != null) {
            Text(
                "$main m",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = InkC
            )
            Text(
                if (live.pin != null) "zur Fahne" else "zur Grünmitte",
                style = MaterialTheme.typography.caption2,
                color = InkFaint
            )
        }

        if (live.front != null && live.back != null) {
            Text(
                "F ${live.front} · M ${live.mid} · B ${live.back}",
                style = MaterialTheme.typography.caption1,
                fontWeight = FontWeight.SemiBold,
                color = PineText
            )
        }

        val accTxt =
            live.acc?.let {
                val q = if (it <= 8) "gut" else if (it <= 15) "ok" else "schwach"
                "GPS ±$it m · $q"
            } ?: "GPS"

        Text(
            accTxt + (if (live.err != null) " · ${live.err}" else ""),
            style = MaterialTheme.typography.caption3,
            color = if ((live.acc ?: 99) <= 15) InkFaint else RedC
        )
    }
}

// ---- Caddy-Block ----

@Composable
private fun CaddyBlock(
    plan: Caddy.Plan?,
    weatherLine: String?,
    mode: String,
    onMode: () -> Unit
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {

        if (plan == null) {
            Text(
                "Caddy: warte auf GPS / Schlägerlängen",
                style = MaterialTheme.typography.caption2,
                color = InkFaint,
                textAlign = TextAlign.Center
            )
        } else {

            Text(
                plan.headline,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GoldText,
                textAlign = TextAlign.Center
            )

            if (plan.plays != plan.target) {
                val diff = plan.plays - plan.target
                Text(
                    "${plan.target} m → spielt wie ${plan.plays} m " +
                            "(${if (diff >= 0) "+" else ""}$diff)",
                    style = MaterialTheme.typography.caption2,
                    color = InkFaint,
                    textAlign = TextAlign.Center
                )
            }

            plan.warn?.let {
                Text(
                    "⚠ $it",
                    style = MaterialTheme.typography.caption1,
                    color = RedC,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            plan.lines.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.caption2,
                    color = InkC,
                    textAlign = TextAlign.Center
                )
            }
        }

        weatherLine?.let {
            Text(
                "🌤 $it",
                style = MaterialTheme.typography.caption3,
                color = InkFaint,
                textAlign = TextAlign.Center
            )
        }

        CompactChip(
            onClick = onMode,
            label = {
                Text("Caddy: ${Caddy.modeLabel(mode)}")
            },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

// ---- Schlagtracking-Block ----

@Composable
private fun ShotBlock(
    active: Boolean,
    club: String?,
    dist: Int?,
    shotCount: Int,
    onBegin: () -> Unit,
    onClub: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {

        if (!active) {

            Chip(
                onClick = onBegin,
                label = {
                    Text("🎯 Schlag hier")
                },
                secondaryLabel = {
                    Text(
                        if (shotCount > 0) "$shotCount getrackt" else "Startpunkt setzen"
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )

            if (shotCount > 0) {
                CompactChip(
                    onClick = onUndo,
                    label = {
                        Text("letzten Punkt löschen")
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

        } else {

            Text(
                dist?.let { "$it m gelaufen" } ?: "warte auf GPS…",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = GoldText
            )

            Chip(
                onClick = onClub,
                label = {
                    Text("Schläger")
                },
                secondaryLabel = {
                    Text(club ?: "—", maxLines = 1)
                },
                colors =
                    if (club != null)
                        ChipDefaults.primaryChipColors()
                    else
                        ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Chip(
                onClick = onStop,
                label = {
                    Text("✅ Ball erreicht")
                },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )

            CompactChip(
                onClick = onCancel,
                label = {
                    Text("Abbrechen")
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun PlayScreen(
    course: CourseDef,
    hd: HoleDef,
    entry: HoleEntry,
    idx: Int,
    total: Int,
    status: String,
    opts: Options?,
    toPar: Int,
    thru: Int,
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
    onDist: (Int) -> Unit,
    onDistFromGps: () -> Unit,
    onBunkerN: (Int) -> Unit,
    onGir: () -> Unit,
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

    ScalingLazyColumn(
        state =
            rememberScalingLazyListState(),
        modifier =
            Modifier.fillMaxSize(),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        item {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                // Gesamt über/unter Par – immer sichtbar, direkt unter der Uhr
                run {
                    val opTxt =
                        if (thru == 0) "±0"
                        else if (toPar == 0) "E"
                        else if (toPar > 0) "+$toPar"
                        else "$toPar"
                    val opColor =
                        if (toPar > 0) RedC
                        else if (toPar < 0) PineText
                        else GoldText
                    Text(
                        "über Par $opTxt · $thru Loch",
                        fontWeight =
                            FontWeight.Bold,
                        style =
                            MaterialTheme.typography.title3,
                        color = opColor
                    )
                }

                // Loch-Navigation: Pfeile ganz oben
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    CompactButton(
                        onClick = onPrev,
                        enabled = idx > 0,
                        colors =
                            ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text(
                            "‹",
                            fontSize = 20.sp
                        )
                    }

                    Text(
                        "${idx + 1}/$total",
                        style =
                            MaterialTheme.typography.caption1,
                        color = InkC
                    )

                    CompactButton(
                        onClick = onNext,
                        enabled = idx < total - 1,
                        colors =
                            ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text(
                            "›",
                            fontSize = 20.sp
                        )
                    }
                }

                Text(
                    "Loch ${hd.hole} · Par ${hd.par}",
                    fontWeight =
                        FontWeight.Bold,
                    style =
                        MaterialTheme.typography.title3,
                    color = PineText
                )

                // Länge (m) und Loch-Handicap oben anzeigen
                val holeInfo = buildString {
                    if (hd.len > 0) {
                        append("${hd.len} m")
                    }
                    if (hd.si > 0) {
                        if (isNotEmpty()) {
                            append("   ·   ")
                        }
                        append("HCP ${hd.si}")
                    }
                }

                if (holeInfo.isNotEmpty()) {
                    Text(
                        holeInfo,
                        fontWeight =
                            FontWeight.SemiBold,
                        style =
                            MaterialTheme.typography.caption1,
                        color = GoldText
                    )
                }

                Text(
                    course.name,
                    style =
                        MaterialTheme.typography.caption2,
                    color = InkFaint,
                    maxLines = 1
                )
            }
        }

        // ---------- LIVE ----------

        item {
            SectionLabel("Live")
        }

        item {
            LiveBlock(live)
        }

        // ---------- CADDY ----------

        item {
            SectionLabel("Caddy")
        }

        item {
            CaddyBlock(
                plan,
                weatherLine,
                caddyMode,
                onCaddyMode
            )
        }

        // ---------- SCHLAGTRACKING ----------

        item {
            SectionLabel("Schläge")
        }

        item {
            ShotBlock(
                recActive,
                recClubName,
                recDist,
                shotCount,
                onShotBegin,
                onShotClub,
                onShotStop,
                onShotCancel,
                onShotUndo
            )
        }

        item {
            SectionLabel("Kern")
        }

        item {

            Stepper(
                "Score",
                entry.score?.toString()
                    ?: "–",
                {
                    onScore(-1)
                },
                {
                    onScore(1)
                }
            )
        }

        item {

            Stepper(
                "Putts",
                entry.putts?.toString()
                    ?: "–",
                {
                    onPutts(-1)
                },
                {
                    onPutts(1)
                }
            )
        }

        if (opts != null) {

            item {

                SelectRow(
                    "Tee-Ergebnis",
                    entry.tee
                ) {

                    onPick(
                        "Tee-Ergebnis",
                        opts.teeResults,
                        entry.tee
                    ) { e, s ->
                        e.copy(
                            tee = s
                        )
                    }
                }
            }

            item {

                SelectRow(
                    "Approach",
                    entry.appr
                ) {

                    onPick(
                        "Approach-Distanz",
                        opts.approachBuckets,
                        entry.appr
                    ) { e, s ->
                        e.copy(
                            appr = s
                        )
                    }
                }
            }
        }

        item {

            ToggleRow(
                "GIR",
                entry.gir,
                onGir
            )
        }

        item {
            SectionLabel("Details")
        }

        if (opts != null) {

            item {

                SelectRow(
                    "1. Putt",
                    entry.firstPutt
                ) {

                    onPick(
                        "1.-Putt-Distanz",
                        opts.firstPuttDist,
                        entry.firstPutt
                    ) { e, s ->
                        e.copy(
                            firstPutt = s
                        )
                    }
                }
            }

            item {

                SelectRow(
                    "Quality",
                    entry.quality
                ) {

                    onPick(
                        "Quality",
                        opts.qualityOpts,
                        entry.quality
                    ) { e, s ->
                        e.copy(
                            quality = s
                        )
                    }
                }
            }

            item {

                SelectRow(
                    "Tee-Schläger",
                    entry.club
                ) {

                    onPick(
                        "Tee-Schläger",
                        opts.teeClubs,
                        entry.club
                    ) { e, s ->
                        e.copy(
                            club = s
                        )
                    }
                }
            }

            item {

                SelectRow(
                    "Approach-Lage",
                    entry.lie
                ) {

                    onPick(
                        "Approach-Lage",
                        opts.approachLies,
                        entry.lie
                    ) { e, s ->
                        e.copy(
                            lie = s
                        )
                    }
                }
            }
        }

        item {

            Stepper(
                "Pin-Distanz m",
                entry.distToPin?.toString()
                    ?: "–",
                {
                    onDist(-1)
                },
                {
                    onDist(1)
                }
            )
        }

        item {

            CompactChip(
                onClick = onDistFromGps,
                label = {
                    Text("Pin-Distanz aus GPS")
                },
                colors = ChipDefaults.secondaryChipColors()
            )
        }

        item {

            Stepper(
                "Bunker Anzahl",
                entry.bunkerN?.toString()
                    ?: "–",
                {
                    onBunkerN(-1)
                },
                {
                    onBunkerN(1)
                }
            )
        }

        if (opts != null) {

            item {

                SelectRow(
                    "Bunker Typ",
                    entry.b1
                ) {

                    onPick(
                        "Bunker Typ",
                        opts.bunkerTypes,
                        entry.b1
                    ) { e, s ->
                        e.copy(
                            b1 = s
                        )
                    }
                }
            }
        }

        item {

            Stepper(
                "Penalty Anzahl",
                entry.penN?.toString()
                    ?: "–",
                {
                    onPen(-1)
                },
                {
                    onPen(1)
                }
            )
        }

        if (opts != null) {

            item {

                SelectRow(
                    "Penalty Typ",
                    entry.penType
                ) {

                    onPick(
                        "Penalty Typ",
                        opts.penaltyTypes,
                        entry.penType
                    ) { e, s ->
                        e.copy(
                            penType = s
                        )
                    }
                }
            }
        }

        item {

            ToggleRow(
                "Up & Down",
                entry.ud,
                onUd
            )
        }

        item {

            ToggleRow(
                "Sand Save",
                entry.ss,
                onSs
            )
        }

        item {

            ToggleRow(
                "Recovery",
                entry.recovery,
                onRec
            )
        }

        item {
            SectionLabel("Runde")
        }

        item {

            Chip(
                onClick = onAutoHole,
                label = {
                    Text("Auto-Loch")
                },
                secondaryLabel = {
                    Text(if (autoHole) "an" else "aus")
                },
                colors =
                    if (autoHole)
                        ChipDefaults.primaryChipColors()
                    else
                        ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {

            Chip(
                onClick = onFinish,
                label = {
                    Text(
                        "Sichern & am Handy abschließen"
                    )
                },
                colors =
                    ChipDefaults.primaryChipColors(),
                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        if (status.isNotEmpty()) {

            item {

                Text(
                    status,
                    textAlign =
                        TextAlign.Center,
                    style =
                        MaterialTheme.typography.caption2,
                    color = InkFaint
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(
    t: String
) {

    Text(
        t.uppercase(),
        style =
            MaterialTheme.typography.caption2,
        color = GoldText,
        fontWeight =
            FontWeight.Bold,
        modifier =
            Modifier.padding(top = 6.dp)
    )
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
    onPlus: () -> Unit
) {

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
                onClick = onMinus,
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
                modifier =
                    Modifier.widthIn(
                        min = 46.dp
                    ),
                textAlign =
                    TextAlign.Center
            )

            Button(
                onClick = onPlus,
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
