package de.lars.golfwatch.presentation

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

/*
 * Golf-Training – Wear-OS-Runden-Eingabe (volle Feld-Parität zur HTML-App)
 * -----------------------------------------------------------------------
 * Erfasst alle Loch-Felder wie die HTML-App und schreibt die laufende Runde
 * als Entwurf (_draftRound) über deinen bestehenden Worker ins Repo.
 *
 * >>> Vor dem Bauen nur WRITE_KEY eintragen: <<<
 */

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
    val holes: List<HoleDef>
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

data class AppData(
    val courses: List<CourseDef>,
    val opts: Options,
    val hi: Double?
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
    val gir: Boolean? = null
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
                gir == null
}

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

// Rundendauer als H:MM:SS bzw. M:SS
private fun fmtDur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    } else {
        String.format(Locale.US, "%d:%02d", m, sec)
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
                        holes
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

        return AppData(
            courses,
            opts,
            hi
        )
    }

    fun pushDraft(round: JSONObject): Boolean {

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

private fun buildRoundJson(
    course: CourseDef,
    tee: String,
    hi: Double?,
    eds: Boolean,
    entries: Map<Int, HoleEntry>
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
    optB(o, "gir")
)

private fun saveLocal(
    ctx: Context,
    course: CourseDef,
    tee: String,
    hi: Double?,
    eds: Boolean,
    roundStart: Long?,
    entries: Map<Int, HoleEntry>
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
    val entries: MutableMap<Int, HoleEntry>
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
            holes
        )

        val map = HashMap<Int, HoleEntry>()

        val ea = o.getJSONArray("entries")

        for (i in 0 until ea.length()) {

            val e = ea.getJSONObject(i)

            map[e.getInt("hole")] =
                jsonToEntry(e)
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
                map
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

// ================= Activity =================

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

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

    var lastPush by remember {
        mutableStateOf(-1)
    }

    var picker by remember {
        mutableStateOf<PickerReq?>(null)
    }

    var roundStart by remember {
        mutableStateOf<Long?>(null)
    }

    val activity = LocalContext.current as? Activity

    val resume = remember {
        loadLocal(ctx)
    }

    fun scored() =
        entries.values.count {
            it.score != null
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
                entries
            )
        }
    }

    fun checkpoint(
        force: Boolean
    ) {

        val cs = course ?: return
        val n = scored()

        if (
            !force &&
            (
                    n == 0 ||
                            n % 3 != 0 ||
                            n == lastPush
                    )
        ) {
            return
        }

        lastPush = n
        status = "sichere…"

        scope.launch {

            val ok = try {

                withContext(Dispatchers.IO) {
                    Net.pushDraft(
                        buildRoundJson(
                            cs,
                            tee,
                            hi,
                            false,
                            entries
                        )
                    )
                }

            } catch (e: Exception) {
                false
            }

            status =
                if (ok) {
                    "✓ gesichert ($n)"
                } else {
                    "⚠ Sync-Fehler"
                }
        }
    }

    // Speichern und – bei Erfolg – die App schließen
    fun finishAndClose() {

        val cs = course ?: return

        status = "sichere…"

        scope.launch {

            val ok = try {
                withContext(Dispatchers.IO) {
                    Net.pushDraft(
                        buildRoundJson(
                            cs,
                            tee,
                            hi,
                            false,
                            entries
                        )
                    )
                }
            } catch (e: Exception) {
                false
            }

            if (ok) {
                status = "✓ gespeichert – schließe…"
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

        persist()
        checkpoint(false)
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

                onNew = {

                    loading = true
                    status = ""

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
                            screen = "pick"
                        }
                    }
                },

                onResume = {

                    resume?.let {

                        course = it.course
                        tee = it.tee
                        hi = it.hi

                        entries.clear()
                        entries.putAll(it.entries)

                        idx = 0
                        lastPush = -1
                        roundStart =
                            it.roundStart
                                ?: System.currentTimeMillis()

                        if (data == null) {

                            scope.launch {

                                data =
                                    try {
                                        withContext(
                                            Dispatchers.IO
                                        ) {
                                            Net.fetchData()
                                        }
                                    } catch (e: Exception) {
                                        null
                                    }
                            }
                        }

                        screen = "play"
                    }
                },

                onDiscard = {
                    clearLocal(ctx)
                    status = "Verworfen"
                }
            )

            "pick" -> PickScreen(
                data?.courses
                    ?: emptyList(),

                onPick = { c ->

                    course = c
                    tee = c.tee

                    entries.clear()

                    idx = 0
                    lastPush = -1
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

                    PlayScreen(

                        course = cs,
                        hd = hd,
                        entry = e,
                        idx = idx,
                        total = cs.holes.size,
                        status = status,
                        opts = opts,
                        roundStartMs = roundStart,

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

                        onPrev = {
                            if (idx > 0) {
                                idx--
                            }
                        },

                        onNext = {
                            if (
                                idx <
                                cs.holes.size - 1
                            ) {
                                idx++
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
    onNew: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit
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
                        "${c.tee} · ${c.holes.size} Loch"
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

@Composable
private fun PlayScreen(
    course: CourseDef,
    hd: HoleDef,
    entry: HoleEntry,
    idx: Int,
    total: Int,
    status: String,
    opts: Options?,
    roundStartMs: Long?,
    onScore: (Int) -> Unit,
    onPutts: (Int) -> Unit,
    onPen: (Int) -> Unit,
    onDist: (Int) -> Unit,
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
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {

    // Live-Ticker für die Rundendauer (aktualisiert jede Sekunde)
    var nowMs by remember {
        mutableStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(roundStartMs) {
        if (roundStartMs != null) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000)
            }
        }
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

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                // Rundendauer direkt unter der Uhr
                if (roundStartMs != null) {
                    Text(
                        "⏱ ${fmtDur(nowMs - roundStartMs)}",
                        fontWeight =
                            FontWeight.SemiBold,
                        style =
                            MaterialTheme.typography.caption1,
                        color = GoldText
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
