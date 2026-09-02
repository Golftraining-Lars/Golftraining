# Changelog-Archiv — Golf-PWA

> **Was hier steht.** Die älteren Einträge des Changelogs. Das aktuelle Changelog steht im
> devdocs-Block in `index.html`; die Prüfstand-Sperrklinke 24ct hält es bei höchstens 45 Einträgen,
> weil die Datei bei jedem Start geladen und geparst wird. Was darüber hinausgeht, wandert hierher —
> **die Begründungen gehen nicht verloren, sie stehen nur woanders.**
>
> **Reihenfolge: neueste zuerst**, wie im Changelog selbst.
>
> **Wie es hierher kommt:** `node changelog-archiv.js` (siehe Repo). Das Skript nimmt die ältesten
> Einträge aus `index.html`, hängt sie hier oben an und schreibt beide Dateien. Von Hand
> zusammenkopieren muss niemand etwas.

---

- **v5.11.0 · 2026-08-29** — **Der Caddy widersprach sich selbst — der Wachhund hatte die ganze Zeit
  recht.** Aus dem Protokoll: fünfzehnmal „spielt-wie uneinig: Kopfzeile 217 m · Kette 209 m", dazu
  „Schläger uneinig: Bewertung 3 Wood · Kette 6 Iron". **Zwei Teile derselben Ansicht empfahlen
  verschiedene Schläger** — auf der Bahn der schlimmste Fall, weil man nicht weiß, welchem man
  glauben soll.
  **Die Ursache war nicht die Rechnung, sondern die Zahl der Aufrufe.** `playAimChain()` wurde in
  `caddyFuerPunkt` **zweimal** gerufen: einmal für das **Ziel** (`_ch0`, gegen das `condFaktor`
  rechnet) und wenige Zeilen später noch einmal für den **Schläger** (`L0`). Dazwischen liegt ein
  Bildschirmaufbau, und die Ortung liefert im Sekundentakt neue Punkte. Fällt eine Neuberechnung
  dazwischen, stammen die beiden Zahlen aus verschiedenen Ketten — genau die 5 bis 14 Meter
  Unterschied aus dem Protokoll. Jetzt: **ein Aufruf, ein Ergebnis, beide Zahlen daraus.**
  **Zwei Aufrufe derselben Rechnung sind zwei Wahrheiten** — dasselbe Muster wie beim Lochzeiger
  (Zähler gegen Zeitstempel) und bei `MAX_ACC`/`GPS_MAX_ACC`. In dieser Woche zum dritten Mal.
  **Und die unbequeme Lehre:** v4.63 hat den Wachhund gebaut, der genau das meldet. Ich habe seine
  Meldung seither in jedem Protokoll gesehen und als „bekannt" abgehakt, statt ihr zu folgen. Eine
  Warnung, die man gewohnheitsmäßig überliest, ist so gut wie keine. Der Wachhund bleibt bewusst
  erhalten — abzuschalten, was unbequem meldet, wäre der falsche Schluss.
  **Prüfstand 24ev** hält die Regel fest; Gegenprobe gemacht: Mit der alten Fassung sind zwei der
  vier Prüfungen rot.

- **v5.10.0 · 2026-08-29** — **Meine eigene Regression: Die Sperre von gestern hat das Schlagtracking
  abgeschaltet.** Gemeldet einen Tag nach v5.09: „Das Schlagtracken auf der Uhr geht nicht. Es lässt
  sich nicht starten, und wenn es gestartet wurde, überträgt es keine Daten."
  **v5.09 hatte in `draftPush` ein `return false` gesetzt**, sobald eine Runde beendet ist und keine
  neue läuft — richtig gedacht gegen den Nachzügler, der die Runde wiederbelebte, aber **viel zu
  breit**: Durch `draftPush` läuft auch der **Schlagkanal**. `DRAFT_ACK` quittiert der Uhr die
  übernommenen Messungen, `DRAFT_SHOTS` gibt fremde Messungen unverändert zurück. Wer den ganzen
  Vorgang sperrt, sperrt die Schlagmessung mit — und zwar **dauerhaft**, denn `playEndedKey` bleibt
  nach jeder beendeten Runde stehen. Ohne Quittung sendet die Uhr endlos weiter und zeigt die Messung
  als offen.
  **Behoben:** Die Sperre ist so eng wie der Schaden. Nach dem Ende geht die **Runde** nicht mehr
  hinaus (`delete d.round; delete d.live`), Quittungen und fremde Messungen gehen mit. Der Grabstein
  hängt ohnehin an jeder Datei ohne Runde (v5.09), das Ende bleibt also bestehen. In `playSaveDraft`
  bleibt die Sperre unverändert — dort ist sie richtig, denn diese Funktion baut den Entwurf **der
  Runde**.
  **Die Rundensimulation hält es jetzt fest** (neuer Abschnitt „Schlagkanal — quittiert auch nach dem
  Rundenende", drei Prüfungen). Gegenprobe gemacht: Mit dem Stand von v5.09 sind zwei davon rot.
  **Lehre: Eine Sperre, die mehr abschaltet als den Fehler, ist ein neuer Fehler** — und sie fällt
  spät auf, weil sie „vorsichtig" aussieht.

- **v5.09.0 · 2026-08-29** — **„Beenden & schließen" beendet die Runde auf der Uhr jetzt zuverlässig.**
  Gemeldet. Ursache war ein Wettrennen: `playSaveDraft` stößt `draftPushSoon()` an — einen Zeitgeber
  mit **zwei Sekunden** Verzögerung. `playFinish` schreibt den Grabstein **ohne `await`**, der
  Zeitgeber zündet danach und schreibt den Entwurf zurück. Und ein Entwurf im Repo heißt für die Uhr:
  Die Runde läuft.
  **Dieses Wettrennen hatte die Rundensimulation längst gezeigt** — und ich habe damals die *Prüfung*
  geduldiger gemacht („warte, bis der Grabstein da ist"), statt das Wettrennen zu beenden. Die
  Beobachtung war richtig, die Folgerung falsch; der Fehler blieb im Produkt.
  **Drei Riegel, weil einer nicht reicht:** `draftPushAus()` bestellt den Zeitgeber an allen drei
  Enden einer Runde ab · `draftPush` und `playSaveDraft` schreiben nach dem Ende gar nichts mehr ·
  und `draftPushRaw` hängt den Grabstein an **jede** Entwurfsdatei ohne eigene Runde. Der letzte ist
  der verlässliche: Jeden einzelnen Schreiber zu suchen ist der falsche Weg — es gibt mehrere, und
  beim nächsten käme derselbe Fehler zurück. **Die Regel gehört an die eine Stelle, durch die alle
  müssen.**
  **Die Rundensimulation löst das Wettrennen jetzt absichtlich aus** statt es abzuwarten. Dabei
  gleich noch eine eigene Falle gefunden: Der Lochzeiger-Abschnitt am Dateiende schreibt selbst in
  `draft.json` — die neue Prüfung hielt seinen Schreibvorgang für den Fehler. Sie misst jetzt den
  eingefrorenen Stand ihres eigenen Abschnitts.

- **v5.08.0 · 2026-08-29** — **Der Start rechnet nichts mehr, was niemand angefordert hat.** Nach
  fünf behobenen Ursachen — Datei, Cache, Service Worker, Datenmenge, Rasterrechnung (Faktor 24
  schneller) — fror die App weiterhin ein. **Der Fehler war nicht, wie lange die Rechnung dauert,
  sondern dass sie überhaupt ungefragt läuft.**
  `setTimeout(gpAutoRefresh, 4000)` stieß **vier Sekunden nach dem Start** die Neuberechnung von vier
  Gameplans über je 18 Löcher an. Der Kommentar darüber sagte es selbst: „`planCourse` blockiert, und
  die erste Sekunde gehört der Ansicht." **Vier Sekunden später blockiert es genauso** — nur fällt es
  dann nicht mehr dem Start zur Last, sondern dem Benutzer, der gerade etwas tut. Das passt exakt zur
  Meldung: lädt, und friert dann ein.
  **Eine Rechnung, die niemand angefordert hat, darf den Benutzer nie blockieren.** Sie ist per
  Definition weniger wichtig als das, was er gerade vorhat — sonst hätte er sie angefordert. Auf
  einem Rechner fällt das nie auf; auf einem Handy ist es der Unterschied zwischen benutzbar und tot.
  **Nötig war der Startlauf ohnehin nicht:** `stratPlanSheet` rechnet beim Öffnen der Ansicht selbst,
  mit dem Hinweis „Rechne Gameplan …". Dort ist die Wartezeit erklärbar, weil sie einer Handlung
  folgt. Der stündliche Lauf bleibt, aber nur bei **sichtbarer** Seite, nicht während einer Runde und
  über `requestIdleCallback` — der Browser rechnet, wenn er ohnehin Luft hat.
  **Prüfstand 24eu** riegelt die Fehlerklasse ab, nicht den Einzelfall: Es zählt die Zeitgeber im
  Startpfad und führt die dort erlaubten Funktionen namentlich. Wer eine schwere hinzufügt, muss
  diese Liste anfassen — und stolpert dabei über die Frage.

- **v5.07.0 · 2026-08-29** — **Das Einfrieren nach 20 Sekunden: gemessen statt vermutet.** Ein
  CPU-Profil eines einzigen Rasteraufbaus hat es gezeigt — nach vier falschen Verdächtigen (Datei,
  Cache, Service Worker, Datenmenge):
  **Rasteraufbau EINES Lochs: 3.591 ms, davon 77 % in `haversine`/`geoDist`.** Raster 71 × 166 =
  11.786 Zellen, 250 Bäume in der Vorauswahl → **2,9 Millionen Haversine-Aufrufe je Loch**. Und
  `gameplanTick` rechnet beim Start **vier Gameplans × 18 Löcher** neu: 38 s je Plan auf dem Rechner,
  auf einem Handy ein Vielfaches. Das ist das Einfrieren — es hat mit Dateigröße, Cache und Service
  Worker **nichts** zu tun.
  **Die Ursache stand in einer Zeile:** `trees.some(t => geoDist(p, t.pt) < 3.5)`. Für jede Zelle
  gegen jeden Baum eine Kugelrechnung mit Sinus, Kosinus und Wurzel — um zu entscheiden, ob ein Punkt
  näher als 3,5 Meter liegt. **Haversine für 3,5 Meter ist mit Kanonen auf Spatzen geschossen:** Sie
  rechnet auf der Kugel und ist auf Hunderte Kilometer genau; auf dieser Entfernung ist die
  Erdkrümmung bedeutungslos.
  **Behoben:** ebene Rechnung mit den Metern je Grad, die das Raster ohnehin kennt, im Quadrat
  verglichen — zwei Subtraktionen, zwei Multiplikationen, ein Vergleich. Die Umrechnung passiert
  **einmal je Raster**, nicht je Zelle; Bäume außerhalb des Rasters fallen vorher raus; Ringe bekommen
  ein umfassendes Rechteck vor den Punkt-in-Ring-Test.
  **Ergebnis: Raster 3.591 ms → 79 ms, ein ganzer Gameplan 37.983 ms → 1.579 ms — Faktor 24.**
  Nachgeprüft: Von 11.786 Zellen weichen **3** ab, Randfälle genau auf der 3,5-m-Grenze.
  **Nebenbei entschärft:** Die Prüfung „Raster zählt Grünzellen" las `grid()` über ein festes
  14.000-Zeichen-Fenster und musste schon zweimal nachgezogen werden. Sie grenzt den Block jetzt an
  der nächsten Methode ab. **Ein Fenster, das man nachziehen muss, ist keine Grenze, sondern eine
  Verabredung auf Zeit.**

- **v5.06.0 · 2026-08-29** — **Zwei Megabyte für einen grauen Punkt.** Die entscheidende Auskunft
  kam von dir: **Im Inkognito-Fenster lud die App — und hängte sich nach 20 Sekunden auf.** Damit war
  klar, dass es weder die Datei noch der Cache ist, sondern etwas, das *nach* dem Laden passiert. Und
  20 Sekunden ist genau die Zeit, in der der Abgleich fertig wird.
  **Am echten Bestand gemessen:** 3,89 MB gesamt, davon `courses` **3,38 MB**. Ein einziger Platz
  (Brodauer Mühle) 2,31 MB. Davon 2,20 MB in 141 Elementen der Art `other`. **Ein einziges Element
  mit 86.840 Stützpunkten: 1,96 MB.** Und `other` wird an genau einer Stelle ausgewertet: ein grauer
  Punkt mit Radius 1,5 bei 30 % Deckkraft.
  **Die Ursache:** `thinRing` entfernt Punkte unter 0,8 m Abstand — eine **verhältnismäßige** Regel
  ohne absolute Schranke. Ein Umriss, dessen Punkte weiter auseinanderliegen, kommt unbegrenzt durch.
  **Eine relative Regel braucht eine absolute Schranke**, sonst hängt die Größe an der Beschaffenheit
  der Quelle — und die kennt niemand vorher.
  **Warum es den Start blockiert:** `cloudLoad` holt und parst den ganzen Bestand, `mergeDB`
  vereinigt ihn, `persist` serialisiert ihn erneut, `snapshot` noch einmal — **vier Durchläufe über
  knapp 4 MB.** Auf einem Rechner unauffällig, auf einem Handy die Last, die alles blockiert. Das
  erklärt auch, warum es am PC durchgehend lief.
  **Behoben, zweifach:** `geoBudget(f)` beim **Import** (der Riegel: 400 Punkte je Element, für
  `other` nur 120) und `geoAbspecken()` für den **Rückstand**, über den neuen Knopf „🗜 Platzdaten
  verschlanken". **Am echten Bestand: 3,08 MB → 0,87 MB, minus 72 %** — und nur 9 von 3160 Elementen
  mussten überhaupt ausgedünnt werden. Nicht gelöscht, sondern gedeckelt: Die graue Andeutung auf der
  Karte bleibt, nur nicht für zwei Megabyte.
  **Ehrlich dazu:** Ich habe für dieses Problem vier Ursachen genannt und dreimal gebaut, bevor die
  richtige Frage gestellt war. Der Inkognito-Test hat mehr geklärt als alles, was ich vorher geprüft
  habe.

- **v5.05.0 · 2026-08-29** — **Das Vorwärmen des Service Workers umging die eigene Prüfung.** v5.04
  hat die Vollständigkeitsprüfung im `fetch`-Zweig eingebaut — aber `install` legte die Hülle
  weiterhin mit `c.addAll(["./", "./index.html"])` ab. **`addAll` speichert, was mit Status 200
  zurückkommt**, auch einen Download, der mitten in den 2,7 MB abbricht. Damit hatte der frisch
  installierte Worker eine Lücke an genau der Stelle, die v5.04 gerade geschlossen hatte.
  **Und es ist der gefährlichere Weg:** Das Vorwärmen läuft bei der Installation, oft direkt nach
  einem Fassungswechsel, und legt die Hülle an, mit der die App danach startet. Jetzt holt `install`
  die Datei selbst und prüft sie; misslingt es, wird **nichts** abgelegt und der reguläre Abruf holt
  sie beim ersten Start nach. **Eine fehlende Hülle kostet einen Ladevorgang, eine halbe kostet die
  App.**
  **Fünfte Anwendung derselben Regel in einer Woche** — und die erste, bei der ich sie beim eigenen
  Nachziehen übersehen hatte: Ich habe den Riegel an einer Stelle gesetzt und die zweite Tür
  offengelassen.

- **v5.04.0 · 2026-08-29** — **Der Service Worker speicherte halbe Dateien.** Gemeldet: Nach einer
  Neuinstallation blieb die App im Startbild hängen; in Chrome erschien nur das **statische Gerüst** —
  Kopfzeile mit Platzhaltern („● lokal", „Start – → Ziel –") und Navigationsleiste. Das ist alles
  festes HTML: **Das Skript lief gar nicht.** Repo-Datei und lokale Datei waren syntaktisch
  fehlerfrei — also bekam das Handy eine **andere** Datei.
  **Die Ursache in `sw.js`:** `if (r && r.ok) c.put(...)` nahm jede Antwort mit Status 200 an — auch
  einen Download, der mitten in den 2,7 MB abbrach. Die halbe Datei wanderte in den Cache, und weil
  der Wettlauf um die Hülle fast immer der Cache gewinnt (2,7 MB kommen nie in 1,5 s an), bekam man
  sie danach bei **jedem** Start wieder. Eine abgeschnittene Datei ist ein Syntaxfehler: Gerüst
  sichtbar, nichts läuft. **Und sie überlebt eine Neuinstallation** — eine Verknüpfung vom
  Startbildschirm zu entfernen räumt weder Service Worker noch Caches ab. Deshalb half das Neu-
  Hinzufügen nicht.
  **Behoben in `sw.js`:** Eine Vollständigkeitsprüfung greift vor dem Ablegen **und** vor dem
  Ausliefern — plausible Größe und Abschluss des Dokuments. Eine kaputte Hülle wird verworfen statt
  weitergereicht; beides landet im Protokoll, wo es der Nachbericht aus v5.02 beim nächsten Start
  erzählt.
  `CACHE_VERSION` auf **v3**: der saubere Schnitt für Geräte, auf denen noch eine abgeschnittene
  Hülle liegt.
  **Vierte Anwendung derselben Regel in einer Woche:** erst prüfen, dann übernehmen — Archiv-Skript,
  Rückfallweg der Uhr, `swForceUpdate`, jetzt der Cache selbst.
  **Nebenbei entschärft:** Die Prüfung `eq("CACHE_VERSION erhöht", …, "v2")` nagelte die Nummer fest
  und wurde rot, sobald man sie erhöht — also genau dann, wenn man das Richtige tut. Sie prüft jetzt
  die **Form** statt des Werts. Dieselbe Lehre wie beim Worker.

- **v5.03.0 · 2026-08-29** — **Doku-Durchsicht nach dem Protokoll-Audit: `sw.js` bekommt ein
  Kapitel.** Auftrag war, nach den drei Etappen zu prüfen, ob die Doku noch stimmt. **Ergebnis:** Sie
  stimmt — mit einer Lücke. Geprüft habe ich, ob die Doku Funktionen verspricht, die es nicht mehr
  gibt (11 Treffer, **alle korrekt** — zehn stehen in Sätzen der Form „ersatzlos entfallen", einer
  ist `leseBegrenzt` der Uhr), ob die Neuerungen dieser Woche beschrieben sind (`logInfo`,
  Startvermerk, `__swlog`, `_logZustand`, `pruefeDaten`, `unwetterUrteil`, `wakeAppAn`, `caddyKette`,
  `teilAnteil`, `fremderZeigerZaehlt`, `poolQuote`, `dbJetzt` — **alle vorhanden**), und ob die
  Worker-Fassung noch gekoppelt ist (Datei v2.11, Abzug v2.11 — **stimmt überein**).
  **DIE LÜCKE:** `sw.js` wurde **13-mal erwähnt und nirgends beschrieben** — bis er diese Woche die
  App lahmlegte. Das ist dieselbe Lücke wie beim Worker vor zwei Tagen: eine Komponente, die über
  Start oder Nicht-Start entscheidet, ohne dass jemand nachlesen kann, wie sie es tut. Neues Kapitel
  **27b** mit den drei Strategien (Hülle mit 1,5-s-Zeitlimit, Kacheln cache-first, `isNeverCache`),
  der Erklärung, **warum eine neue Fassung erst beim übernächsten Start erscheint** (2,7 MB kommen
  nie in 1,5 s an — im Normalbetrieb gewinnt immer der Cache), dem Nachbericht über `./__swlog` und
  der Regel, die diese Woche **dreimal in drei verschiedenen Dateien** verletzt wurde: **erst den
  Ersatz sichern, dann das Alte wegwerfen.** Sie steht jetzt in allen dreien.
  **Prüfstand 24er** bindet das Kapitel an die Datei — dieselbe Bauart wie 24ca beim Worker: Das
  Zeitlimit wird aus `sw.js` gelesen und gegen die Doku gehalten, der Nachbericht muss auf beiden
  Seiten existieren. Eine abgeschriebene Zahl veraltet mit; eine verglichene kann es nicht.

- **v5.02.0 · 2026-08-29** — **Das Protokoll erzählt den Ausfall jetzt.** Gemeldet: „Von diesem
  ganzen Problem findet sich anscheinend nichts im Fehlerlog." Stimmte — und der Grund war
  strukturell, nicht Nachlässigkeit.
  **(1) DER STARTVERMERK.** Je Start eine `info`-Zeile: Fassung, Dauer bis zum ersten Bild, Herkunft
  der Hülle (`transferSize===0` heißt „aus dem Speicher"), Online-Status. Ein **Fassungswechsel**
  bekommt eine eigene Zeile — er ist der häufigste Auslöser für „seit heute geht etwas nicht mehr".
  **Damit wäre der Ausfall in einem Blick sichtbar gewesen:** letzter Start 04:42 mit 5.00.0, danach
  nichts mehr. **Die Lücke ist die Nachricht.**
  **(2) DER SERVICE WORKER MELDET SICH** (`sw.js` v3). Er war der größte blinde Fleck: Er entscheidet
  über Start oder Nicht-Start und schrieb nie eine Zeile. Jetzt legt er den Grund im Cache ab
  (`./__swlog`) — im Moment der Störung ist meist **kein** Fenster offen, dem er etwas schicken
  könnte — und die App holt ihn beim **nächsten erfolgreichen Start** ab. **Ein Ausfall kann sich
  nicht selbst melden, aber er kann sich melden, sobald es wieder geht.** Kein Fernprotokoll: Die
  Meldung bleibt auf dem Gerät.
  **(3) DRITTE STUFE `logInfo`.** Start, Fassungswechsel, Plan-Erneuerung, fehlende Höhendaten sind
  keine Warnungen. Sie liefen bisher als `warn` und verdrängten echte Warnungen aus einem Ring, der
  bei 40 endete — das ganze Protokoll bestand am 29.08. aus drei Routine-Zeilen. **Ein Ereignis, das
  bei jedem Aufruf eintritt und sich nie ändert, ist ein Zustand und keine Nachricht.** Ring auf 60,
  weil Kontext ohne Vorgeschichte keiner ist.
  **(4) HERKUNFT STATT „PROMISE".** `unhandledrejection` nennt jetzt den ersten Stapelrahmen. „Promise"
  allein ist keine Quelle, sondern die Bauart des Fehlers — deshalb stand am 29.08. ein anonymes
  „Promise · Failed to fetch" im Log, und ich habe die Zeile falsch gedeutet.
  **(5) ZUSTAND AM EINTRAG** (`_logZustand`): Fassung, Ansicht, laufende Runde, offline. Beantwortet
  die erste Rückfrage, bevor sie gestellt wird.
  **(6) DER SELBSTTEST LIEST DAS PROTOKOLL** — und dabei ein eigener Fund: Er prüfte auf das Feld
  `level`, **das es gar nicht gibt** (es heißt `lvl`). Die Zahl war damit **immer 0**, und er meldete
  brav „keine Fehler", während das Protokoll voll war. **Ein still falsches Grün ist schlimmer als
  ein Rot** — man verlässt sich darauf. Jetzt: Fehler seit dem letzten Start, häufigste Quelle,
  Zeitpunkt des letzten Starts, und ein fehlender Startvermerk ist selbst ein Befund.
  **Doku:** Der Abschnitt zum Fehlerprotokoll stand noch auf „letzte 40" und „nur Sitzung, nicht
  gespeichert" — beides seit Langem falsch. Jetzt vollständig, mit den drei Stufen und ihrer
  Abgrenzung.

- **v5.01.0 · 2026-08-29** — **„Neueste Fassung laden" konnte die App unerreichbar machen.**
  Gemeldet: „Die App lädt überhaupt nicht mehr" — auf dem Handy, während sie am PC weiterlief. Im
  Protokoll dazu: `Promise: Failed to fetch`. **Die Kette war hausgemacht:** `swForceUpdate` löschte
  den Hüllen-Cache **ganz vorn** — die gespeicherte Fassung war weg, **bevor** irgendetwas Neues da
  war. Dann `location.replace(...)`; der Service Worker versucht 2,7 MB aus dem Netz zu holen, und
  bei schwachem Funk misslingt das. Danach gibt es **weder Cache noch Netz** — und der Weg zurück
  führt über genau den Knopf, den man nicht mehr erreicht.
  **Dieselbe Fehlerklasse wie der Rückfallweg der Uhr (48)** und wie das Archiv-Skript (v4.85):
  **erst schreiben, dann löschen — nie umgekehrt.** Jetzt wird die neue Fassung zuerst geholt und auf
  Vollständigkeit geprüft (ein abgebrochener Download liefert oft 200 mit zu wenig Inhalt); erst dann
  wird der **eine** Hüllen-Eintrag ersetzt. Misslingt der Abruf, bleibt die alte Hülle unangetastet,
  die App startet weiter, und es kommt eine ehrliche Meldung statt eines weißen Bildschirms. Die
  Kartenkacheln werden nicht mehr mitgelöscht — sie neu zu laden kostet Funk, den man auf dem Platz
  nicht hat.
  **Zweiter Fund aus demselben Protokoll:** `unwetterTick` rief `unwetterHolen(...).then(...)` **ohne
  `.catch`**. Ein misslungener Wetterabruf wurde damit zur unbehandelten Zurückweisung und landete
  als „Promise: Failed to fetch" im Protokoll — ohne Hinweis darauf, **wer** sie ausgelöst hat. Genau
  diese Zeile hat mich zunächst in die falsche Richtung geschickt. Eine Prüfung fängt den Rückfall
  für alle `fetchMitFrist`-Aufrufe ab.

- **v5.00.0 · 2026-08-28** — **Gewitterwarnung auf dem Platz · Bildschirm bleibt an.**
  **(1) DIE WARNUNG.** Vorgabe: „Wetterwarnungen in den Spielmodus einbinden, ob sich Blitze oder
  Unwetter meiner Position nähern … ob es für mich auf dem Golfplatz gefährlich wird." **Das ist die
  einzige Funktion dieser App, bei der ein Fehler wehtun kann** — ein Golfplatz ist offenes Gelände,
  und ein Spieler mit einem Metallschläger ist der höchste Punkt weit und breit. Deshalb gelten hier
  vier Regeln, die im Rest der App nicht gelten:
  **Keine Warnung heißt NICHT „sicher".** Der Kasten bleibt auch bei Ruhe stehen und nennt Quelle und
  **Alter** der Daten. Ein leeres Feld liest sich als Entwarnung — und eine Entwarnung, die niemand
  ausgesprochen hat, wäre bei einer Warnfunktion die gefährlichste Anzeige.
  **Es ist eine Vorhersage, keine Blitzortung.** Der Satz steht auch im ruhigen Zustand da: „Wer
  Donner hört, ist bereits in Reichweite."
  **Im Zweifel warnen:** Fehlende Felder gelten als *unbekannt*, nie als harmlos; ein misslungener
  Abruf wird gemeldet, nicht verschwiegen.
  **Die 30/30-Regel** steht bei jeder Warnung — unter 30 s zwischen Blitz und Donner sofort Schutz,
  erst 30 min nach dem letzten Donner zurück. Sie hängt nicht an dieser App, genau deshalb steht sie
  da.
  Stufen: Gewitter ≤30 min → *Gefahr* (roter Kasten, Vibration, Protokoll) · ≤120 min → *Warnung*
  („Rückweg jetzt einplanen — von Loch 9 zum Clubhaus dauert es länger als die Vorwarnzeit") ·
  CAPE ≥1500 mit Regen oder Böen ≥17 m/s → *Warnung* · Starkregen → *Hinweis* („für die Sicherheit
  unkritisch"). Mehrere Signale, weil keines allein verlässlich ist und `lightning_potential`
  außerhalb Mitteleuropas gar nicht kommt. **Neu gezeichnet und gemeldet wird nur bei
  Stufenwechsel** — ein Kasten, der sich alle zehn Minuten selbst neu malt, zieht den Blick ab, ohne
  etwas zu sagen. Beim Rundenstart wird sofort geprüft.
  **Ehrlich gekennzeichnet:** Der Bausandkasten kommt nicht an `api.open-meteo.com`. Die Auswertung
  ist defensiv gebaut und mit 17 Prüfungen abgedeckt, aber **was das Gerät wirklich liefert, muss die
  erste Runde zeigen.**
  **(2) BILDSCHIRM BLEIBT AN.** Vorgabe: „Immer wenn die App an ist, soll das Handy always on
  bleiben." Die Sperre hing bisher an `gps` und `runde` — wer Schlägerlängen pflegte oder eine Runde
  nachtrug, saß nach 30 Sekunden im Dunkeln. Neu der Grund `app`, in derselben Zählweise:
  freigegeben wird erst, wenn **niemand** mehr will, damit keiner der drei dem anderen die Sperre
  wegnimmt. Nach jedem Wechsel wird neu angefordert — der Browser gibt sie beim Wegschalten frei und
  holt sie nicht von allein zurück. **Der Preis, ausdrücklich:** Ein dauerhaft helles Display ist der
  größte Einzelposten im Akkuverbrauch, größer als GPS.

- **v4.99.0 · 2026-08-28** — **Der Caddy begründet seine Empfehlung.** Vorgabe: „Wenn der Caddy im
  Spielmodus ausgeklappt wird, soll er eine detaillierte Begründung für seine Empfehlungen geben."
  Bis v4.98 sagte er nur das **was** — Schläger, Ziel, ES-Wert, Streubild-Prozente. Das **warum**
  fehlte: warum dieser Schläger und nicht der nächstlängere, und was den Ausschlag gab.
  **Konzept A — die Entscheidungskette** (`caddyKette`), fünf Zeilen in der Reihenfolge, in der ein
  Caddy denkt: *Lage* · *Spielt wie* · *In Frage* · *Ausschlag*. „Spielt wie" erscheint **nur**, wenn
  Wind und Höhe mindestens 2 m ausmachen — eine Zeile „spielt wie genauso weit" ist
  Platzverschwendung. Der *Ausschlag* nennt das Risiko, das in Kauf genommen wurde („bester Wert bei
  vertretbarem Risiko — Bunker 22 %"); Strafrisiko hat dabei Vorrang vor Bunker, Bunker vor Rough.
  **Konzept B — der Vergleich mit der Alternative** (`caddyVergleichHtml`), eingeklappt und einen
  Tipp entfernt: Erwartung, Fairway, Strafrisiko, Weite, Rest zum Grün — das Bessere fett, bei
  Gleichstand nichts hervorgehoben, denn eine erfundene Auszeichnung suggeriert einen Vorsprung, den
  es nicht gibt. **Automatisch gegen den nächstbesten Schläger:** Wer beim Ball steht, weiß nicht,
  welchen er zum Vergleich wählen soll. Die manuelle Auswahl „Warum nicht …?" bleibt daneben, sie
  beantwortet eine andere Frage. Gerechnet wird über `caddyWarumNicht` — **beide Seiten aus derselben
  Rechnung**, sonst ist der Vergleich wertlos.
  **`caddyKipppunkt` sagt, wie fest die Empfehlung steht:** unter 0,03 Schlägen „praktisch gleichauf
  — hier entscheidet das Gefühl, nicht die Rechnung", unter 0,10 „knapp, bei anderem Wind kippt das",
  darüber „deutlich". Der Satz benennt den **Abstand** und behauptet keine Ursache, die nicht
  gerechnet wurde — „ohne den Bunker wäre X besser" klänge klüger, wäre aber erfunden.
  Beides steht **vor** den Streubild-Prozenten: Die sind das Rohmaterial der Rechnung, nicht die
  Antwort auf „warum". `_restZumGruen` und `_spieltWieM` holen ihre Zahlen aus derselben Quelle wie
  die Zeile darüber — zwei Rechnungen für dieselbe Zahl laufen früher oder später auseinander, und
  genau das war schon einmal ein gemeldeter Fehler.
  **Beim Bauen:** Mein erster Anlauf las `ev.alts` (Mehrzahl). Das Feld heißt `ev.alt` — die
  Kandidaten-Zeile blieb stumm leer. Ein Feldname, den man rät, prüft sich nicht selbst.

- **v4.98.0 · 2026-08-28** — **Fahnenposition entfernt.** Entscheidung: „Die Fahnenposition soll aus
  dem Caddy entfernt werden. Die Funktion soll vollkommen entfernt werden." Weg sind `PIN_RAND`,
  `pinZeile`, `pinFuer`, `pinSetz`, `pinPunkt`, die Schalterzeile vorn/Mitte/hinten aus **beiden**
  Caddy-Ansichten, die Fahne im Cache-Schlüssel und `PLAY.pins`. **Ziel ist wieder durchgängig die
  Grünmitte** — derselbe Stand wie zwischen v1.90 und v3.87.
  **Was bewusst bleibt:** der Parameter `flag` in `STRAT.approach` sowie `pointESTo` und `shotEV`.
  Dort ist die Fahne kein Schalter, sondern der **Bezugspunkt jeder Erwartungsrechnung** — wer sie
  dort mit entfernte, nähme der ganzen Strokes-Gained-Rechnung ihren Nullpunkt. `greenDims` bleibt
  ebenfalls: Die Grüntiefe wird an drei weiteren Stellen gebraucht.
  **Prüfstand 24en gedreht statt gelöscht** — die Prüfungen verlangten genau das Gegenteil, und eine
  gedrehte Prüfung hält fest, dass die Umkehr gewollt war. Dabei selbst hereingefallen: Die
  Abwesenheitsprüfung fand zunächst ihre **eigene Begründung**, weil `codeOhneDoku` nur den
  devdocs-Block schneidet und nicht die Kommentare im Code — jetzt läuft sie durch beide Filter.

- **v4.97.0 · 2026-08-28** — **„Aus Teilrunden" steht jetzt dabei.** Die dritte Familie aus v4.96 —
  GIR-, Fairway- und Scrambling-Quote — ist je **Loch** definiert und damit aus einer Teilrunde
  rechenbar. Aber **die Auswahl der Löcher ist nicht zufällig:** Wer drei Löcher spielt, nimmt die
  nahe am Clubhaus oder gezielt die schweren, und über viele Teilrunden mittelt sich das nicht
  heraus — es zieht systematisch. **Zwei falsche Antworten standen zur Wahl:** ausschließen (verliert
  echte Daten, und bei überwiegend kurzen Übungsrunden bliebe die Quote leer) oder stillschweigend
  mitzählen (dann liest man eine Zahl als vergleichbar, die es nicht ist). **Die dritte ist die
  richtige: mitzählen und es dazuschreiben.** Neu `teilAnteil(liste)`; der Hinweis nennt Anzahl
  **und** Löcher („2 von 5 Runden unvollständig (12 Löcher) — die Loch-Auswahl ist dort nicht
  zufällig"), weil die Anzahl allein nicht sagt, wie schwer sie wiegen. Er erscheint **nur**, wenn
  wirklich Teilrunden dabei sind: Eine Kennzeichnung, die immer dasteht, liest man nach drei Tagen
  nicht mehr. Gekennzeichnet sind die Fünf-Runden-GIR-Kachel und, bei einer einzelnen Teilrunde, die
  Kacheln GIR und Fairways. **An den Putts steht bewusst nichts** — die entstehen je Loch aus der
  Situation und sind auch aus drei Löchern belastbar; genau das ist der Punkt der Unterscheidung.
  Die Korrelationsansicht filtert Teilrunden weiterhin ganz heraus und sagt es auch — dort werden
  Summen wie Strafschläge verglichen, und die sind über verschieden lange Runden nicht vergleichbar.

- **v4.96.0 · 2026-08-28** — **Score Differential aus drei Löchern: −47,5. Und was Teilrunden
  trotzdem taugen.** Gemeldet mit Bildschirmfoto. Das Tor war `counted>0` — damit wurden **15
  Schläge aus drei Löchern gegen eine CR von 71,8** gerechnet, also drei Löcher gegen die Vorgabe
  für achtzehn. Die Zahl war nicht ungenau, sie war sinnlos. **WHS/DGV kennt den Fall seit April
  2024:** Differential ab **14** gespielten Löchern (**7** bei einer Neun), fehlende werden mit
  **Netto-Par** (Par + eigene Vorgabeschläge) ergänzt, darunter gibt es keins. `sdGrund` sagt jetzt
  warum — ein leeres Feld ohne Erklärung sieht aus wie ein Fehler, hier ist es eine Regel. **Das
  Auffüllen ist der eigentliche Gewinn:** Eine abgebrochene 16-Loch-Runde ergibt regelkonform ein
  gültiges Differential, statt still zu verschwinden. Der **Course HCP** trägt bei Teilrunden jetzt
  „für 18 Löcher" — er ist die Vorgabe, die man bekommen *hätte*.
  **Der Einwand dazu war richtig und hat die Sache verbessert:** „Gibt es nicht Statistiken, für die
  auch Einzellöcher sinnvoll sind — Puttlängen, Approach Shots?" Ja. **Die Trennlinie ist nicht
  „Teilrunde ja/nein", sondern die Beobachtungseinheit: der Schlag oder die Runde.** Schlagbezogenes
  ist ab **einem** Schlag gültig — Puttlängen, Approach je Distanzklasse, Strokes Gained,
  Schlägerlängen aus GPS, Streuung, Up-and-down, Sand Save. Ein Schlag aus 140 m vom Fairway ist mit
  jedem anderen aus 140 m vergleichbar, egal ob drumherum 2 oder 18 Löcher gespielt wurden; das ist
  Broadies Argument hinter Strokes Gained. Rundenbezogenes braucht eine Runde: Differential, Brutto,
  Stableford, Course HCP, Doppelbogeys, Birdie-Serien. Dazwischen mit Vorbehalt: GIR- und
  Fairway-Quote — je Loch definiert, aber **die Auswahl der Löcher ist nicht zufällig**. Neu
  `istRundenStat(e)` als gemeinsame Kennung.
  **Und ein Fehler, der leicht untergeht:** Der Mittelwert aus „2,00 Putts/Loch über 3 Löcher" und
  „1,80 über 18" ist **nicht** 1,90 — die Dreiloch-Runde bekäme sechsmal zu viel Gewicht. Neu
  `poolQuote(liste, zähler, nenner)`; die Fünf-Runden-GIR-Quote wird damit gepoolt statt gemittelt,
  und die Doppelbogeys mitteln nur noch volle Runden (sie sind eine Summe, keine Quote).
  **Prüfstand 24ct2** stellt den gemeldeten Fall nach — 3, 13, 16 und 18 Löcher aus derselben Runde
  gestutzt — und prüft beide Familien: Putts zählen auch aus drei Löchern, `toPar` bleibt leer, das
  ergänzte Differential liegt nahe an der vollen Runde.

- **v4.95.0 · 2026-08-28** — **Selbstprüfung in fünf Teilen — mit Trockenlauf einer ganzen Runde.**
  Vorgabe: „deutlich detaillierter … vielleicht auch mit einer Rundensimulation koppeln … richtig
  intensiv und umfassend." **Der Anlass ist ein Befund über die Prüfung selbst:** Bis v4.94 las sie
  nur den Quelltext — und hat von dem, was in dieser Woche wirklich weh tat, **nichts** gefunden.
  Der pendelnde Lochzeiger, der Rückfallweg mit dem OutOfMemory, die Mitspieler, die nicht
  verschwanden: keine davon war eine Quelltext-Frage. Neu:
  **(2) Daten** — Widersprüche im eigenen Bestand: Runden ohne Kennung/Platz/Datum, Runden deren
  Platz es nicht mehr gibt, gewertete Löcher ohne auffindbares Par, mehr Putts als Schläge, doppelte
  Schlägereinträge, Messungen ohne Schläger oder ohne Neutralwert, ein Entwurf der älter ist als sein
  eigener Grabstein. **Streng lesend** — eine Prüfung, die etwas repariert, ist keine mehr.
  **(3) Rechnung** — Sätze, die unabhängig von jeder Kalibrierung gelten und deren Verletzung auf der
  Bahn Unsinn empfiehlt: Gegenwind macht länger und wirkt stärker als Rückenwind, näher ist nie
  teurer, Rough kostet mehr als Fairway, der höhere Zähler gewinnt den Lochzeiger, `null` löscht beim
  Zusammenführen nichts, ein Grabstein räumt ab.
  **(4) Trockenlauf** — eine **erfundene** 18-Loch-Runde durch `computeRound`, `roundCardHtml` und
  `roundShareText`. Das ist die Kopplung mit der Rundensimulation, so weit sie auf dem Gerät geht:
  `runde-simulation.js` fährt am Rechner eine echte Runde gegen einen nachgebauten Worker; hier gibt
  es nur **einen** Bestand, und der gehört dir — eine Prüfung, die eine Runde anlegt, um sie zu
  prüfen, hinterlässt eine Runde.
  **(5) Umgebung** — Auslieferung, Größe des Bestands, Abgleich, Uhr-Protokoll; sonst über drei
  Ansichten verstreut, jetzt an einem Ort, den man **vor** der Runde ansieht.
  **Nur Teil 1 braucht Netz.** Bis v4.94 brach die ganze Prüfung mit „Quelltext nicht ladbar" ab —
  ausgerechnet auf dem Platz, wo man sie am ehesten braucht. Ein Absturz in einem Teil reißt die
  anderen nicht mehr mit. Fehler stehen je Teil oben, grüne Zeilen unten: Wer die Prüfung öffnet,
  sucht Befunde, nicht Bestätigung.
  **Drei eigene Falschalarme beim Bauen abgestellt** — und das ist die wichtigste Lehre daraus:
  „Löcher ohne Par" (das Par steht am **Platz**, nicht am Loch), „Schläger doppelt" („SW 54° · 75 m"
  und „· 65 m" sind zwei echte Einträge, dieselbe Wedge mit verschiedenen Schwunglängen) und
  „Summe 0" im Trockenlauf (`computeRound` braucht den Platz). **Eine Prüfung, die richtige Daten
  anmeckert, bringt einem bei, sie zu ignorieren — und dann überliest man auch die echten Befunde.**
  Prüfstand 24cu bewacht das: Die fünf Teile müssen laufen, dürfen den Bestand **nicht verändern**,
  keinen Prüfplatz zurücklassen und auf gesunden Daten **nicht klagen**.

- **v4.94.0 · 2026-08-28** — **Etappe A und B des Folge-Audits · Uhr 49: kein großer Abruf mehr.**
  **(W-2, deine Entscheidung)** Der Big-File-Rückfall soll nicht bleiben. Uhr-Fassung 49 entfernt ihn
  aus `loadData` — damit fielen `readData`, `fetchRaw`, `fetchData`, `fullSha`, `DATA_URL` und
  `FRESH_URL` als toter Code mit. **Die Uhr liest nur noch `watch.json`, `draft.json` und
  `probe.json`**, zusammen wenige Kilobyte; scheitert das, greift der lokale Zwischenspeicher — der
  war immer die bessere Antwort, der große Rückfall stand nur davor. `leseBegrenzt` wurde **nicht**
  mitgelöscht, sondern auf **alle vier** verbliebenen Lesestellen gesetzt: Ein Riegel, der nur dort
  sitzt, wo es einmal knallte, schützt nur vor der Wiederholung.
  **(W-1)** `MAX_ACC` (Uhr) und `GPS_MAX_ACC` (Handy) beantworten dieselbe Frage — ab welcher
  Streuung ein Punkt für eine Schlagmessung unbrauchbar ist. Beide stehen auf 15 m, und **nichts
  hielt sie zusammen**. Das ist dasselbe Muster wie beim Lochzeiger, nur eine Ebene tiefer: keine
  Rechnung, sondern eine gemeinsame **Annahme** — und die braucht dieselbe Klammer. Prüfstand 24cv
  liest beide Zahlen aus den Quelltexten und vergleicht sie.
  **(W-3)** `dbJetzt()` / `playJetzt()` geben die **jeweils aktuelle** Bindung von `DB` und `PLAY`
  zurück. Beide sind `let` und werden komplett ersetzt (`DB=merged` an sieben Stellen,
  `PLAY=Object.assign(…)` bei jedem Rundenstart); eine `let`-Bindung im VM-Kontext ist keine
  Eigenschaft des Kontextobjekts, der Prüfstand hielt also Momentaufnahmen. **Das hat diese Woche
  dreimal einen halben Nachmittag gekostet** und jedes Mal wie ein Produktfehler ausgesehen. Der
  Prüfstand hat dafür `live(...)` und einen Deckel gegen neue Momentaufnahme-Stellen (80/20, nur
  senken).
  **(W-4)** Der **Lochzeiger** wird jetzt über den echten Weg geprüft — `playSyncTick`, echte
  Dateien, echter Merge: Die Uhr zieht das Handy mit, gleicher Zähler zieht es nicht zurück, eigenes
  Blättern hebt den Zähler, eine neu gestartete Uhr zieht nichts zurück. Das ist das am häufigsten
  wiederkehrende Thema des Projekts (fünf Anläufe über fünf Fassungen) und war bisher **nur an der
  reinen Funktion** geprüft. Der Abschnitt steht **abgeschottet am Dateiende mit eigener Runde**: Er
  stößt ein Dutzend Abgleich-Takte an, und `draftPushSoon` hängt an einem **Zeitgeber**, nicht an
  einem Takt — ein Nachzügler überschrieb sonst den Grabstein des folgenden Abschnitts.
  **(N-1)** 15 Prüfungen für `pointESTo`, `shotEV` und `grid` — die drei, die die Entscheidung auf
  der Bahn tragen (**34 → 37 von 43** STRAT-Methoden). Geprüft gegen golferische Wahrheiten, die
  unabhängig von jeder Kalibrierung gelten: Näher ist nie teurer, Wasser kostet rund einen Schlag
  mehr als daneben, mehr Streuung ist nie besser, zu kurz trifft das Grün nicht, und derselbe Schlag
  liefert zweimal dasselbe Ergebnis (die Halton-Folge ist fest, nicht zufällig).
  **Beim Bauen selbst gelernt:** Ein Schnitt per Textsuche in einer 1300-Zeilen-Datei traf das
  falsche von zwei gleichlautenden Vorkommen und duplizierte 69 Zeilen. Die Reparatur lief
  zeilenbasiert mit Gegenprobe vor jedem Schreiben. Und die Grabstein-Prüfung wartet jetzt **auf das
  Ergebnis statt auf eine Frist** — sie pollt bis zu drei Sekunden. Eine Prüfung, die auf eine
  Uhrzeit wartet, ist mal grün und mal rot, ohne dass sich etwas geändert hat.

- **v4.93.0 · 2026-08-28** — **Der Lochzeiger pendelte: zwei Regeln für dieselbe Frage.** Im
  Protokoll vom 28.08. sprang er Loch 8 ⇄ 1 ⇄ 2 innerhalb einer Minute — auf der Uhr stand
  „Handy-Loch verworfen · seq=33/eigen 33", am Handy „Uhr meldet Loch 8 · ÜBERNOMMEN". Beide hielten
  sich für im Recht, **und beide hatten nach ihrer eigenen Regel recht.** Uhr-Fassung (34) hatte den
  **Zähler** eingeführt, weil Zeitstempel zwischen zwei Geräten dreimal gescheitert waren; die Uhr
  entscheidet seither streng nach `seq > ownSeq`. **Das Handy hat den Zähler nur gelesen, nicht
  befragt** — es hob brav seinen eigenen Stand an (v4.79) und entschied weiter nach `lv.at >
  playLiveSeenAt`, also nach dem Schreibzeitpunkt, der bei einem Gerät im Sekundentakt praktisch
  immer frisch ist. Ergebnis: Das Handy folgte jedem Zeiger der Uhr, die Uhr verwarf jeden des
  Handys. **Besonders teuer nach einem Neustart der Uhr:** Ihr `ownHoleSeq` lebt nur im Speicher und
  fängt bei 0 an — eine frisch gestartete Uhr meldete Loch 1, und das Handy sprang mitten auf Loch 8
  zurück. Genau das steht im Protokoll unmittelbar nach dem OutOfMemory-Absturz. **Behoben:** neue
  Funktion `fremderZeigerZaehlt(fremderSeq, eigenerSeq)` — **derselbe Name wie auf der Uhr**, damit
  beim Lesen auffällt, wenn eine Seite abweicht. Höherer Zähler gewinnt, bei Gleichstand bleibt jeder
  stehen, ohne Zähler gilt weiter der Zeitvergleich (altes Netz für ältere Uhr-Fassungen). Ein
  **verworfener** Zeiger wird bewusst **nicht abgehakt**: Die Uhr sendet ihn weiter, und sobald ihr
  Zähler steigt, soll die Handlung wirken — häkte man ihn ab, ginge genau diese verloren. Das
  Protokoll nennt jetzt **beide Zählerstände** und „VERWORFEN (Zähler)"; bisher argumentierte die Uhr
  mit Zählern und das Handy mit Zeitstempeln, was den Vergleich der beiden Protokolle wertlos machte.
  **Prüfstand 24cw** prüft die Regel rein — `playAdoptRemoteHole` braucht `PLAY`, `DB` und einen
  Bildschirmwechsel, und `G("DB")` liefert nur eine Momentaufnahme. Zum dritten Mal diese Woche
  dieselbe Lehre: **Eine Entscheidung, die man nicht einzeln befragen kann, wird nicht geprüft.**

- **v4.92.0 · 2026-08-28** — **Ein Protokoll statt zwei · Uhr 48: der Rückfallweg hat die App
  getötet.** **(1) DER ABSTURZ.** Gemeldet: „Das Schlagtracken dauert sehr lange bis es startet, auch
  nach mehreren Anläufen nicht. Dann bricht es zwischendurch ab und die ganze App schließt sich."
  Das Uhr-Protokoll nannte die Ursache wörtlich: `OutOfMemoryError: Failed to allocate a 14784520
  byte allocation @Net.readData < Net.pushDraft`. In `pushDraft` stand ein Rückfallweg: Gelang der
  schlanke Schreibvorgang in `draft.json` nicht, holte er die **große `trainingsdaten.json`** —
  mehrere Megabyte —, parste sie und schickte alles zurück. **Er konnte seit Worker v2.9 gar nicht
  mehr gelingen** (ALT-Modus geschlossen, 426), er sprengte den Speicher, und er lief **genau dann,
  wenn es ohnehin klemmte** — ausgelöst vom Fehlschlag des schlanken Weges, also bei schlechtem Funk.
  Auf eine überlastete Leitung legte er Megabyte obendrauf; 37 von 60 Vorgängen misslangen. Ein
  Sicherheitsnetz, das bei jedem Auffangen reißt und den Springenden mitnimmt, ist keines. Entfernt,
  ebenso der gleichartige Rückfall in `fetchDraft`. Neu `leseBegrenzt()` — über 6 MB wird abgebrochen
  und gemeldet statt abzustürzen. **(2) EIN PROTOKOLL, ZWEI GERÄTE.** Vorgabe: „Nur ein großes
  Fehlerprotokoll, in dem aber das Uhr-eigene gut unterscheidbar ist." Der zweite Knopf
  (`showWatchLog`) ist weg — er zwang zu einer Entscheidung, die man vor dem Suchen nicht treffen
  kann. Unterscheidbar bleibt es doppelt: eigener Rahmen (`.uhr-log`) und ein ⌚ vor **jeder** Zeile —
  der Rahmen geht beim Kopieren verloren, das Zeichen nicht. Der eine Knopf trägt beide Zahlen, und
  im Uhr-Block steht der Nachlade-Knopf. Übernommen wird nichts: nur angezeigt, damit erkennbar
  bleibt, **wo** ein Fehler entstand. **(3) DIAGNOSE ERWEITERT** um die drei Größen, die am 28.08.
  gefehlt haben: **Speicher** (im Puls und Abzug, Warnung unter 20 MB Rest — eine Größe, die einen
  umbringen kann, gehört ins Protokoll, bevor sie es tut), **Fehlerarten** (Zeitablauf /
  Verbindungsabriss / Konflikt statt einer richtungslosen Zahl — ich habe sie von Hand ausgezählt)
  und die **Schlag-Spur** (das Schlagtracken ist der einzige Zweck der Uhr und kam im Protokoll
  überhaupt nicht vor; jetzt jeder Schritt mit Dauer). **Offen und nicht behoben:** Der Lochzeiger
  pendelte zwischen Uhr und Handy (Loch 8 ⇄ 1 ⇄ 2) — eigener Befund, eigene Fassung.

- **v4.91.0 · 2026-08-27** — **Ruhigeres Scrollen in der Eingabemaske · Uhr 46: drei Vibrationen,
  kein Wegwischen.** Gemeldet: „Gibt es eine Möglichkeit, dass das Scrollen in der App noch smoother
  wird" — gemeint war die Liste der Felder, die man während der Runde einträgt.
  **Nicht die Eingabe war die Ursache:** `playField()` speichert nur und zeichnet nichts neu. Die
  Kosten lagen im Compositing. **Drei Flächen mit `backdrop-filter: blur()`** liegen über oder um die
  scrollende Liste — die Fußleiste `nav` (12 px), die `.play-navbar` (9 px) und der `.backdrop`
  hinter dem Sheet (2 px). Ein Weichzeichner ist nicht gratis: Er liest bei **jedem Bild** neu, was
  hinter ihm liegt, und über einem scrollenden Bereich ändert sich das per Definition ständig — 60
  Gauß-Rechnungen je Sekunde über mehrere hundert Pixel Höhe, während der Browser ohnehin die Liste
  bewegt. **Die Lösung behält das Aussehen:** `scrollMarkeAn()` setzt `body.scrollt`, solange
  gescrollt wird; die drei Flächen schalten für diese Zeit auf eine deckende Farbe und bekommen
  140 ms nach dem letzten Ereignis ihre Tiefe zurück. Im Stand sieht alles aus wie vorher, und genau
  im Stand schaut man hin. **`passive:true` ist dabei keine Kleinigkeit:** Ein Scroll-Zuhörer ohne
  dieses Merkmal zwingt den Browser, vor jedem Bild abzuwarten, ob der Zuhörer die Bewegung abbricht
  — man baute sich mit dem Ruckel-Fix ein neues Ruckeln ein. Dazu `overscroll-behavior:contain` auf
  `#sheetBody`: Ohne sie reicht jede Bewegung über den Rand hinaus an die Seite dahinter weiter, und
  der Browser muss bei jeder Berührung erst entscheiden, wer scrollt — das fühlt sich am Listenanfang
  und -ende als Zähigkeit an. **Bewusst NICHT gemacht:** `content-visibility` auf den Feldern. Es
  wäre der nächste Hebel, braucht aber eine ehrliche `contain-intrinsic-size`; rät man sie falsch,
  springt die Bildlaufleiste, und man tauscht ein Ruckeln gegen ein Zucken. Erst am Gerät messen.
  **Nebenbei entschärft:** Die Prüfung „`_errLogGeholt` beim Schließen zurückgesetzt" nagelte die
  **erste Zeile** von `closeSheet` fest und brach bei der harmlosen Ergänzung davor. Sie prüft jetzt,
  **dass** es geschieht, nicht **wo**.

- **v4.90.0 · 2026-08-27** — **Eine beendete Runde sprang immer wieder auf.** Gemeldet: „Wenn ich
  auf dem Handy eine Runde verwerfe oder speichere und beende, wird sie danach ganz oft trotzdem
  wieder aufgerufen — ich springe grundlos wieder in den Spielmodus." **Zwei Ursachen, beide
  bestätigt. (1) Der Riegel wurde am Ende absichtlich gelöst:** `playFinish` und `playDiscard`
  setzten `watchAutoOpenedFor=""` — den Schutz gegen Wieder-Aufspringen — mit dem Kommentar „Runde
  ist durch, nächste darf wieder aufspringen". Die Absicht war richtig, der **Ort** falsch: Die Uhr
  meldet dieselbe Runde noch bis zu **vier Stunden** weiter (`WATCH_LIVE_MAX_AGE_MS`), und der
  Wächter läuft jede Minute plus bei jedem Aufwecken des Handys. Wer den Riegel beim Aufhören löst,
  lädt genau die Runde wieder ein, die er gerade beendet hat. **(2) Der Grabstein verglich
  Zeitstempel:** durchgelassen wurde alles mit `d.ts > draftDiscardedTs` — und die Uhr schreibt
  weiter mit frischem Zeitstempel, bis sie den Grabstein selbst gelesen hat. Ein Wettrennen, das der
  Grabstein nicht gewinnen kann. **Behebung — Identität statt Zeit:** `_playKey(r)` =
  „Platz|Datum|Umfang"; `playMarkEnded(r)` hält die beendete Runde in `ui.playEndedKey` fest
  (**persistent**, überlebt ein Neuladen — anders als die Variable), `playClearEnded()` gibt sie
  frei, und zwar in **`playBegin`**: Wer wieder anfängt, sagt damit, dass die alte Runde erledigt
  ist. Diese eine Runde springt nicht mehr auf, egal wie frisch die Uhr sie meldet; eine andere
  (anderer Tag, anderer Platz, andere Neun) ist unberührt. **Warum es so lange unbemerkt blieb:** Die
  Regel war nur über `watchLiveMaybeOpen` erreichbar — also nur zusammen mit `PLAY`, `DB` und einem
  echten Bildschirmwechsel. **Eine Entscheidung, die man nicht einzeln befragen kann, wird nicht
  geprüft.** Sie steckt jetzt in `watchLiveDarfOeffnen(d, ui, jetzt, maxAlter, weggetippt)` — rein,
  ohne eine einzige Globale, mit Begründung im Rückgabewert (`{ok, grund}`); `watchLiveMaybeOpen`
  führt nur noch die Nebenwirkung aus. **Prüfstand 24cx** stellt das Wettrennen nach (Grabstein eine
  Minute alt, Uhr-Meldung von jetzt), lässt den Wächter zehnmal klopfen und prüft, dass eine andere
  Runde weiterhin öffnen darf.

- **v4.89.0 · 2026-08-27** — **F/M/B zeigte 2,4 km, während die Zeile darunter „zu weit" sagte.**
  Gemeldet mit Bildschirmfoto: Loch 2, Par 5, 499 m — und in der Kopfzeile des Vollbilds stand
  **„F 2384 · M 2395 · B 2407"**. Das Handy lag zu Hause, zweieinhalb Kilometer vom Grün.
  **Gerechnet war nichts falsch:** Front zu Back waren 23 m auseinander, also eine ganz normale
  Grüntiefe; nur der Bezugspunkt war 2,4 km entfernt. Genau dafür gibt es `playTooFar()` seit v1.77,
  und `playInfoHtml` wie `pfCaddyKurz` befolgen sie — **die Kurzzeile im selben Bild sagte korrekt
  „2,4 km — zu weit · ab Tee Driver 237 m"**. Die dritte Anzeige, die Kopfzeile des Vollbilds, wurde
  beim Einbau übersehen. **Das ist die schlimmere Sorte Fehler: nicht falsch gerechnet, sondern
  inkonsistent angezeigt.** Zwei Felder desselben Bildschirms geben verschiedene Auskünfte über
  dieselbe Lage, und der Spieler muss raten, welchem er glaubt. Jetzt stehen dort drei gedämpfte
  Striche (`pf-fmb-fern`) — **nicht** nichts: Eine verschwundene Zeile sieht aus wie fehlendes GPS,
  drei Striche sagen „gemessen, aber hier ohne Aussage". **Prüfstand:** Die neue Prüfung zählt die
  Aufrufstellen von `greenFMB` in der Anzeige und verlangt, dass **jede einzelne** hinter
  `playTooFar` liegt — sie greift damit auch bei einer vierten Anzeige, die noch niemand gebaut hat.
  Ein Riegel gegen die Fehlerklasse, nicht gegen den Einzelfall.
  **Gegenstück auf der Uhr (Fassung 45):** Der Schlag-Knopf zeigt während der Aufnahme die
  **gelaufenen Meter** statt des Stopp-Rechtecks.

- **v4.88.0 · 2026-08-27** — **Etappe 3 des Audits: Testabdeckung mit Rückweg, Kalibrierung, echte
  Ladezeitmessung.**
  **(W-2) DIE ABDECKUNGS-SPERRKLINKE HATTE KEINEN RÜCKWEG.** Sie verhinderte NEUE ungetestete
  Funktionen — aber nichts drückte den Altbestand nach unten, und der bewegte sich seit Wochen nicht
  (202 reine Funktionen, 17 von 43 STRAT-Methoden ohne Verhaltenstest). Neu: **41 Prüfungen für acht
  STRAT-Methoden** (Abschnitt 24da2), gegen **bekannte Wahrheiten** statt gegen sich selbst — die
  Quantile der Normalverteilung stehen in jeder Tabelle, eine Halton-Folge hat bekannte erste
  Glieder, ein Punkt 100 m nach Norden liegt 0,000905° weiter nördlich. So findet die Prüfung einen
  Vorzeichenfehler auch dann, wenn die Funktion sich selbst gegenüber konsistent bleibt. Abgedeckt:
  `_invNorm`, `_halton`, `samples`, `_off`, `_segDist`, `_interp`, `esOffset`, `playingLevel` —
  **26 → 34 von 43**. Dazu `ABDECKUNG_DECKEL`, die Zahl der offenen Altlasten: **darf nur sinken**.
  Keine Quote, denn eine Prozentzahl steigt auch, wenn jemand getestete Funktionen hinzufügt — sie
  belohnt Wachstum statt Abdeckung.
  **(W-3) DIE KOEFFIZIENTEN WAREN GESETZT, NICHT GEMESSEN.** Gegenwind 1,4 %/m/s, Rückenwind 0,8 %,
  bergauf voll / bergab 0,75, Regen 3 % — plausibel, aber nie an den eigenen Schlägen geprüft, obwohl
  die Daten seit v4.80.1 dafür da sind. Neu `kalibrierBericht()`/`kalibrierText()`, Knopf
  „📏 Rechnung prüfen" unter Mehr → Daten. **Die prüfbare Behauptung:** Wenn `playsLike` stimmt,
  streuen die neutralisierten Werte je Schläger **weniger** als die rohen — das ist ihre Aufgabe.
  Wird die Streuung größer, ist ein Koeffizient falsch oder ein Vorzeichen verdreht, und der Bericht
  sagt das als **Warnung**, nicht als Zahl unter vielen. Gemessen mit dem Median der absoluten
  Abweichung, damit ein einzelner GPS-Ausreißer das Urteil nicht kippt; unter 6 Schlägen je Schläger
  bzw. 20 gesamt schweigt er, statt zu raten. **Und was man nicht aufschreibt, kann man nicht
  nachprüfen:** `schlagNeutral` speicherte nur das Ergebnis. Für eine echte Nachjustierung braucht es
  die Bedingungen JE SCHLAG — sie werden ab jetzt als `wx:{t,w,d,b}` mitgeschrieben (rund 40 Byte).
  Altdaten haben sie nicht, das lässt sich nicht nachholen, und der Bericht sagt es.
  **(W-4) LADEZEIT: GEMESSEN STATT GESCHÄTZT.** Ob die 2,5-MB-Datei komprimiert ankommt, lässt sich
  am Schreibtisch nicht beantworten — der Browser weiß es. `ladeBericht()` liest `transferSize` gegen
  `decodedBodySize` und meldet Klartext: komprimiert (dann ist nichts zu tun) oder nicht (dann sind
  zwei Drittel einsparbar). `transferSize === 0` heißt „aus dem Cache"; dann sagt die Messung nichts
  über die Leitung, und genau das steht da. Lokal gemessen: 2519 kB → **897 kB gzip (36 %)**, die
  Doku allein 353 → 148 kB.
  **Beim Bauen aufgefallen:** Meine ersten Kalibrier-Tests fütterten zwölf Schläge — der Bericht
  urteilte deshalb gar nicht und `madRoh` war `undefined`. Die Testdaten lesen die Schwelle jetzt
  aus der App, statt sie abzuschreiben. Und die Detail-Argumente von `ok(...)` werden **immer**
  ausgewertet, auch wenn die Prüfung besteht — ein `undefined.toFixed()` reißt dann den ganzen Lauf
  ab, statt eine Zeile rot zu färben.

- **v4.87.0 · 2026-08-27** — **Etappe 1 des Audits: Worker-Wahrheit, Funk-Fristen, geteilte Ortung.**
  Drei Befunde aus dem Audit vom 27.08., alle mit Riegel im Prüfstand statt nur mit Behebung an der
  Fundstelle.
  **(1) DER WORKER HATTE KEINE EINZIGE WAHRHEIT.** Bei Cloudflare lief v2.11, der devdocs-Abzug
  stand auf v2.8 und die Datei `worker.js` im Repo auf **v2.1** — mit `PATHS` nur für
  `trainingsdaten.json` und `wissen-bilder.json`. Dieser Worker hätte **jeden Rundenentwurf, jede
  Uhr-Datei und jeden Kopplungstest mit 403 abgewiesen**; dass der Abgleich trotzdem lief, war der
  Beleg, dass niemand mehr wusste, welcher Code dort arbeitet. Der echte Stand liegt jetzt im Repo
  und wörtlich in Abschnitt 28. **Prüfstand 24ca vergleicht ab jetzt zwei Artefakte statt Sätze zu
  suchen:** Die Fassungsnummer wird aus `worker.js` gelesen und gegen die Überschrift gehalten, und
  jeder Pfad, den App oder Uhr per `X-Path` senden, muss in `CFG.PATHS` stehen — nachgewiesen wirksam
  (Testlauf mit entferntem `draft.json` meldet „fehlt: draft.json"). Die alte Prüfung `/Fassung v2\.8/`
  war eine abgeschriebene Zahl und konnte gar nichts merken. Neue Arbeitsregel **0b**.
  **(2) SIEBEN WORKER-AUFRUFE OHNE FRIST.** `fetchMitFrist()` gibt es seit v4.83, benutzt wurde sie
  siebenmal — daneben standen sieben Aufrufe ohne. Der Kommentar an der Funktion beschreibt den
  Schaden genau: „drei Minuten Totenstille, während die Uhr 26 Aktionen mit HTTP 200 ablieferte."
  Behoben wurde damals die Fundstelle, nicht das Muster. Betroffen war unter anderem
  `watchFilePush` — der läuft **während der Runde**, also wenn ein Funkloch wahrscheinlich ist. Alle
  Aufrufe laufen jetzt über die Frist, und **24cy verbietet den nächsten ohne**.
  **(3) EIN GPS-WÄCHTER, ZWEI VERBRAUCHER, EIN RÜCKRUF.** `liveStart(cb)` setzte `LIVEPOS.cb`,
  **bevor** geprüft wurde, ob schon ein Wächter läuft. Wer als Zweiter startete, übernahm die Ortung
  stillschweigend; `liveStop()` aus einem der beiden hielt sie für **beide** an. Erreichbar ohne
  Zutun: `openCaddyPosition()` rief in seiner ersten Zeile `liveStop()` — **wer während einer
  laufenden Runde die On-Course-Ansicht öffnete, spielte danach ohne Live-Position weiter**, ohne
  Loch-Erkennung, ohne Positionsmeldung an die Uhr, ohne jede Meldung. Der Fehler machte kein
  Geräusch. Jetzt `liveStart(kennung, cb)` / `liveStop(kennung)` mit Verbraucherliste, `liveStopAll()`
  für „Blatt schließen", `liveVerbraucher()` für die Diagnose; der Wächter hält erst an, wenn der
  letzte gegangen ist. Kennungen: `play`, `caddypos`. **24cz prüft das als Verhalten**, nicht als
  Text: zwei Verbraucher anmelden, einen abmelden, nachsehen wer noch versorgt wird — inklusive des
  Falls, dass ein Rückruf sich während der Zustellung abmeldet (genau das tun sie, wenn ihre Ansicht
  weg ist). Ein Wächter für alle bleibt richtig: `watchPosition` mit `enableHighAccuracy` ist der
  teuerste Posten im Akkuhaushalt auf der Runde — er muss nur wissen, für wen er läuft.

- **v4.86.0 · 2026-08-27** — **Eine auf der Uhr begonnene Messung erscheint jetzt auch hier
  (Gegenstück zu Uhr-Fassung 44).** Gemeldet: „Wenn ein Schlagtracking auf der Uhr eingeleitet wurde,
  soll das auch auf dem Handy aufgerufen und angezeigt werden." **Zwei Ursachen. (1) Hier:**
  `watchRecBanner()` gibt es seit v1.68 und hatte **keinen einzigen Aufrufer** — nur das Vollbild
  (`pfBottom`) baute sich ein eigenes Band. In der Eingabemaske, wo man die halbe Runde verbringt,
  stand nichts. Eine geschriebene Funktion ohne Aufrufer sieht in der Doku aus wie eine Zusage und
  ist keine. Das Band steht jetzt **ganz oben** in `renderPlay()`, vor Lochnummer und Navigation:
  Eine laufende Messung ist ein Zustand, kein Detail — wer sie übersieht, lässt sie über das halbe
  Loch weiterlaufen, und der Endpunkt landet irgendwo. **(2) Auf der Uhr:** Der Live-Zeiger reiste
  erst, wenn ein Schläger gewählt war (behoben in Fassung 44). **Ohne Schläger wird jetzt trotzdem
  angezeigt** — „Uhr trackt · Schläger offen" —, nur **abschließen** geht dann nicht: Ein Schlag ohne
  Schläger ist für die gelernten Längen wertlos, und der Knopf wäre ein Angebot, das man bereut. Gilt
  für beide Bänder (Eingabemaske und Vollbild). **Prüfstand:** Die v2.08-Regel „Eingabemaske ohne
  Uhr-Aufnahmeband" ist **gedreht** — v2.08 warf Karte, Caddy und Distanzen heraus, weil sie doppelt
  und teuer waren (Monte-Carlo, Geo-Raster). Das Uhr-Band ist nichts davon: Es rechnet nicht und
  dupliziert nicht, es meldet den Zustand des **anderen** Geräts. Die eigene Aufnahmesteuerung
  (`shotRecHtml()`) bleibt draußen. **Zur Uhr-Seite:** Runde anlegen, abschließen und verwerfen gehen
  ab Fassung 44 **nur noch hier** — auf der Uhr sind Alleinstart, Platzauswahl, „Sichern &
  abschließen" und „Verwerfen" entfernt.

- **v4.85.1 · 2026-08-27** — **Uhr zeigte mehr Mitspieler als die PWA — Handy führt jetzt
  ausnahmslos (Uhr-Fassung 42).** Gemeldet und nachgestellt. **Zwei Ursachen, beide aus Uhr-Fassung
  39,** wo die Uhr selbst Plätze eröffnen durfte: **(1)** `Mitspieler.plaetze` lag in den Prefs und
  **wuchs nur** — wer einmal drei Plätze aufgemacht hatte, sah drei Zeilen, auch auf der nächsten
  Runde und auch ohne Namen im Handy. Ein Zähler, der nur eine Richtung kennt, ist kein Zustand,
  sondern eine Hochwassermarke. **(2)** Die Uhr übernahm die Namensliste nur, wenn sie **nicht leer**
  war — ein hier entfernter Mitspieler kam damit **nie** an. Entfernen war die einzige Änderung, die
  nicht reiste. **Behoben auf der Uhr:** Plätze, Chip „+ Mitspieler" und Pref `mitspielerN` sind weg,
  die Zeilen entstehen wieder aus den Namen; `RepoDraft.mitspieler` ist jetzt `List<String>?`, weil
  **`null` („nicht gesagt") und leere Liste („ausdrücklich keine") verschiedene Auskünfte sind** —
  das alte `?: emptyList()` machte aus dem einen das andere. Die Uhr **echot die Namen nicht mehr**:
  Die Rundensimulation (v4.84.2) hat gezeigt, dass ein fehlender Schlüssel beim Merge übergangen wird
  und die Namen bleiben, ein mitgeschickter veralteter Stand sie dagegen überschreibt — Schweigen ist
  hier der sichere Weg. **Auf dieser Seite unverändert**, aber jetzt tragend: `playRound()` schreibt
  `mitspieler` immer, auch leer. **Prüfstand:** 24db gedreht (die Prüfungen aus 39 verlangten das
  Gegenteil), Rundensimulation um „Entfernen muss auch reisen" ergänzt — mit dem Hinweis, dass der
  absichtlich in die Zukunft gestempelte Uhr-Entwurf im Mitspieler-Abschnitt jeden späteren Merge
  gewinnt und man danach besser mit einer frischen Runde weiterprüft. **Preis, ausdrücklich:**
  Uhr-Fassung 39 ist damit zurückgenommen — wer die Runde auf der Uhr beginnt und das Handy im Bag
  lässt, kann keinen Mitspieler erfassen. Das ist die Kehrseite von „eine Wahrheit" und billiger als
  zwei Listen, die auseinanderlaufen.

- **v4.85.0 · 2026-08-27** — **Das Changelog-Archiv gab es nicht — wiederhergestellt und
  automatisiert.** Befund: `changelog-archiv.md` existierte im Repo **nie**. Die Sperrklinke 24ct
  kürzt das Changelog seit Wochen auf 45 Einträge und verweist dabei auf ein Archiv — angelegt hat
  es niemand. **366 Fassungen an Begründungen waren weg**, von v1.0.0 bis v4.49.0.
  **Wiederhergestellt aus der Git-Historie des Repos**, nicht aus Gesprächsverläufen: 340 Commits
  von `index.html` (29.07.–27.08.), davon 35 Schnappschüsse gelesen und alle jemals vorhandenen
  Einträge nach Fassungsnummer vereinigt. Ein zusammengeschriebenes Archiv hätte Einträge erfunden
  oder verkürzt; die Herkunft steht im Kopf der Datei, samt der Lücke, die bleibt (die vier
  ältesten Schnappschüsse hatten noch gar kein Changelog).
  **Neu `changelog-archiv.js`** — Kürzen ist ab jetzt ein Befehl: liest den devdocs-Block, behält
  die neuesten 45, hängt den Rest oben ans Archiv, schreibt beide Dateien. **Wiederholbar** (nach
  Fassungsnummer, zweimal laufen ändert beim zweiten Mal nichts), **verlustfrei** (Gegenprobe vor
  dem Schreiben, Archiv zuerst — bricht es dazwischen ab, steht etwas doppelt statt zu fehlen),
  **ehrlich** (`--pruefen` ändert nichts). Legt das Archiv an, wenn es fehlt: Ein Werkzeug, das eine
  Datei voraussetzt, die es selbst erzeugen könnte, ist der Anfang derselben Lücke.
  **Prüfstand 24ct** nennt jetzt den Befehl in der Meldung und prüft, dass es das Skript gibt, dass
  das Archiv die älteren Einträge trägt, keine Fassung doppelt vorkommt und **nichts an beiden Orten
  steht**. Genau diese Prüfung hatte gefehlt: Der Verweis auf das Archiv war da, das Archiv nicht.
  **Arbeitsregel 0 erweitert:** Auch die Repo-**Historie** ist abrufbar (Commit-Liste über die API,
  alte Stände über `raw`), und sie ist die belastbarste Quelle für alles, was „mal dastand".

- **v4.84.2 · 2026-08-27** — **Rundensimulation ausgebaut: 81 → 124 Prüfungen.** Sie prüfte
  Distanzen, Caddy und Eingaben — also viel von dem, was die Uhr seit Fassung 38/40 **nicht mehr
  tut** — und ließ die Wege ungeprüft, an denen die teuersten Fehler dieses Projekts saßen. Neu:
  **(1) Schlagmessung der Uhr über die ganze Kette** — ein Schlag in exakt der Form, die
  `MainActivity.kt` schreibt, liegt in `draft.json`, dann läuft `draftPull()`: Rohwert bleibt roh,
  `distNeutral` wird beim Eintreffen gerechnet, gegen den Wind größer / mit dem Wind kleiner
  (Vorzeichen!), 3-h-Sperre bei später Ankunft, einmal gerechnet bleibt fest, doppelte Sendung
  verdoppelt nichts, Teilschwung wird gespeichert aber nicht gelernt, `shotAck` quittiert und der
  eigene Push löscht die Messungen der Uhr nicht. Das war die größte Lücke: der einzige Zweck der
  Uhr, gar nicht abgedeckt. **(2) Schreibkonflikt (409)** — der eigene Eintrag kommt an *und* die
  Messung des anderen Geräts überlebt. **(3) Netzausfall** — Eingaben laufen weiter, nichts Halbes
  landet im Repo, nach der Rückkehr kommt alles an. **(4) Rundenabschluss** — `playFinish` bis
  `DB.rounds`, Umdeutung zu „Front 9", `computeRound`, Scorekarte und Teilen-Text mit Mitspielern,
  Grabstein in `draft.json`. Das Verwerfen war doppelt geprüft, das Beenden gar nicht.
  **(5) Entwurfs-Merge mit leeren Listen** — hält einen beim Bau gefundenen Befund fest, siehe
  unten. Kopf der Datei erklärt jetzt, warum sie neben `tests.js` steht, und nennt die zwei Fallen,
  die den Bau je eine halbe Stunde gekostet haben (kein Zeitstempel in der Zukunft; asynchrones
  Austrudeln lassen). **BEFUND, nicht geändert:** Auf Loch-Ebene gilt „null löscht nichts" (v2.98).
  Auf Runden-Ebene nicht — `Object.assign({}, old.round, nw.round)` kopiert auch ein **leeres**
  Array, ein `mitspieler: []` des jüngeren Entwurfs löscht damit die Namen des älteren. Ein
  **fehlendes** Feld ist harmlos, und die Uhr schreibt `mitspieler` nur mit Namen — erreichbar ist
  der Fall über zwei PWA-Instanzen. Ein Riegel hätte einen Preis (der letzte Mitspieler ließe sich
  von der anderen Seite nicht mehr entfernen); das ist eine Abwägung, keine Fehlerbehebung, und
  deshalb festgehalten statt still geändert.

- **v4.84.1 · 2026-08-27** — **Arbeitsregel 0: Alle aktuellen Dateien liegen im Repo und werden
  bei Bedarf dort abgerufen.** Anlass: `runde-simulation.js` ließ sich nicht fahren, weil
  `runde-harness.js` nicht vorlag — der Lauf brach mit `MODULE_NOT_FOUND` ab, und **zwei echte rote
  Prüfungen blieben dadurch unentdeckt** (die Simulation forderte die Platzkarte in `watch.json`,
  die v4.84 gerade entfernt hatte). Nachgeholt: beide gedreht, plus neue Prüfungen, dass Name und
  Tees bleiben, keine Gameplans mitreisen und die Datei unter 60 kB bleibt — der Zweck der schlanken
  Datei ist ihre Größe, und ohne Schranke merkt niemand, wenn sie wieder wächst.
  `runde-simulation.js`: 81 ok, 0 fail. Die Regel steht in den Arbeitsregeln (devdocs), im Kopf von
  `tests.js` und in Kapitel 8 von `MainActivity.kt`, mit dem Zusatz: **nicht blind ziehen.** Erst
  `APP_VERSION`/`WATCH_APP` vergleichen — am 26.08. lag im Repo Uhr-Fassung (13), hochgeladen war
  (37). Regel 0 ist die einzige, die sich nicht technisch erzwingen lässt: Eine fehlende Datei sieht
  aus wie „geht hier nicht", und genau dann muss der Bearbeiter wissen, dass sie zu holen ist.

- **v4.84.0 · 2026-08-26** — **Gegenstück zum Rückbau der Uhr: keine Karte, keine Gameplans, keine
  Rechenfragen mehr.** Die Uhr (Fassung 40) hat Caddy, Wetter-Physik, Grün-/Gefahren-Geometrie,
  Karten-Parser und Gameplan-Ansicht gelöscht — rund 2000 Zeilen. Was sie nicht mehr liest, muss
  hier nicht mehr geschickt und nicht mehr abgefragt werden. **(1) `watch.json` ohne Geometrie und
  ohne `strat.gameplans`:** `watchPayload()` schickt Plätze nur noch mit Name und Tees. Die Karte
  war der Löwenanteil der Datei und kostete bei JEDEM Push eine Serialisierung des größten
  Datenteils; sie fällt von einigen hundert kB auf wenige. `watchGeo`/`watchElevProfil` bleiben —
  `schlagNeutral` braucht die Höhenprofile hier weiter, nur ihre Rolle als Zulieferer der Uhr
  entfällt. **(2) `probePlan()` fragt keine Rechnungen mehr ab:** Die Aufgaben `geo`, `caddy` und
  `lie` sind weg, samt ihren Auswertungszweigen in `koppelTest()`. Sie verglichen „rechnet
  dasselbe" — sinnvoll, solange beide Geräte rechneten. Jetzt wären sie unbeantwortet geblieben und
  der Prüflauf hätte drei Abweichungen gemeldet, wo keine sind; ein Prüfstand, der grundlos Alarm
  schlägt, bringt einem bei, den Alarm zu ignorieren. Übrig bleiben `club`, `clubs`, `liste`,
  `quelle` — **welche Daten** hat die Uhr, und genau das entscheidet noch, ob eine Runde auf ihr
  brauchbar wird. Der Plan braucht damit auch keinen eingezeichneten Platz mehr. Die Prüfung „Uhr
  hat eine Platzkarte" ist entfallen: Die Uhr meldet dort bewusst `false`, ein Haken darauf wäre ein
  Fehlalarm bei korrektem Verhalten. **(3) `probeFrage()` entfernt** — der Vorläufer von
  `probePlan`, eine einzelne Distanzfrage, ohne Aufrufer seit v3.09. **(4) Diagnose neu gefasst:**
  `cloudDiag` meldete, welche Plätze ihre Karte in der Uhr-Datei haben; jetzt meldet es, was zählt —
  Größe der Datei und ob die Schläger drin sind. Ohne sie kann die Aufnahmezeile auf der Uhr keinen
  zuordnen, und eine Messung ohne Schläger ist für die gelernten Längen wertlos.

- **v4.83.0 · 2026-08-26** — **Mitspieler stehen auf der Scorekarte · die Uhr eröffnet Plätze
  statt Namen zu brauchen.** Zwei gemeldete Lücken, eine gemeinsame Ursache. **(1) Auf der Karte
  fehlten sie:** `msc1..msc3` wurden seit v4.81 gespeichert und reisten über den Entwurf, aber
  `cardBlock` kannte die Felder nicht — und `playCardHtml` baute sich sogar ein Runden-Objekt
  **ohne** `mitspieler` zusammen. Achtzehnmal tippen und nichts dafür bekommen ist schlimmer als
  gar nicht zu erfassen. Jetzt hängt `cardBlock(hs,von,bis,lab,mitspieler)` je **belegtem** Platz
  eine `.sc-ms`-Zeile an (Name oder „Mitspieler 2", Summe rechts); leere Plätze bekommen keine
  Zeile (Regel wie `penAny`), fremde Scores **keine** Birdie/Bogey-Farben — die sind die Sprache
  der eigenen Zeile. `roundShareText` nennt je Platz die Summe; geteilt wird meist direkt nach der
  Runde, und dann sitzen genau die Leute daneben. **(2) Auf der Uhr war kein Mitspieler
  erfassbar:** Die Zeilen hingen an den NAMEN, und Namen vergibt allein das Handy — wer die Runde
  auf der Uhr begann (Handy im Bag), bekam gar keine Zeile. **Der Punkt: `msc1..msc3` sind Zahlen
  an festen PLÄTZEN.** Um Platz 1 zu füllen, braucht die Uhr keinen Namen, sondern eine Zeile; der
  Name ist Beschriftung und wirkt **rückwirkend**, weil nach Platz gespeichert wird und nicht nach
  Person. Die Uhr (Fassung 39) eröffnet deshalb Plätze („+ Mitspieler", Langdruck schließt, löscht
  nichts) und vergibt weiterhin **keine** Namen. **Kein neues Datenfeld:** Das Handy sieht die
  Belegung an den Daten (irgendwo ein `msc2` ⇒ Platz 2 in Gebrauch); ein eigenes Feld im Entwurf
  wäre eine zweite Wahrheit und liefe auseinander, sobald beide Seiten schreiben. **(3) Scharfe
  Kante entschärft:** Beim Entfernen rücken die Plätze auf — trägt die Uhr in derselben Minute auf
  Platz 2 ein, landen ihre Werte danach unter dem Namen des bisherigen Spielers 3, lautlos.
  `mitspielerName` fragt jetzt nach, wenn schon Zahlen im Rücken stehen, und benennt die Folge.
  Der saubere Riegel wäre eine Kennung je Person; dafür müsste die Uhr Namen kennen, und genau das
  soll sie nicht. **Prüfstand:** neuer Abschnitt 24db (Karte mit/ohne Namen, gemischt, leerer
  Platz, keine Farben, Plätze statt Namen auf der Uhr, kein zweites Feld, Compose-Spiegel). Dabei
  riss zum dritten Mal ein festes Zeichenfenster an einer harmlosen Ergänzung („Spielmodus baut
  über roundCardHtml", 400 Zeichen) — es misst jetzt den Block.

- **v4.82.3 · 2026-08-26** — **9-Loch-Spielvorgabe: das HI wird halbiert (WHS).** Befund von der
  Front-9-Runde am 26.08.: „Stableford 38 / Course HCP 24" bei +4 brutto — 14 Brutto plus 24
  Vorgabeschlaege. CR/Slope/Par kamen korrekt von der Neun, aber das (per Definition auf 18 Loch
  bezogene) HI ging voll in die Formel. Nach WHS gilt fuer 9 Loecher `HI/2 × Slope9/113 +
  (CR9 − Par9)` — korrekt ~12 Schlaege, ~26 Netto-Punkte. Behoben an BEIDEN Rechenstellen
  (`_computeRoundRoh` fuer die Auswertung, `playVorgabe` fuer den Live-Zaehler — auch das
  „31 Pkt nach 7" unterwegs war dieselbe Inflation). Das **Score-Differential war korrekt**
  (3,4 gespielt + 11,6 erwartete Haelfte = 15,0 — die erwartete Haelfte stammt laut WHS aus dem
  eigenen HI, deshalb wirkt ein starkes Neun-Loch-Ergebnis dort nur zur Haelfte). Neue
  Pruefgruppe: 9-Loch-Runde mit Seiten-Rating, Vorgabe halbiert, 18-Loch-Rechnung unveraendert.

- **v4.82.2 · 2026-08-26** — **Pruefstand-Runde: drei gefangene Fehler, Verträge nachgezogen,
  Changelog archiviert.** `node tests.js` gegen den Tagesstand — drei ECHTE Faenge: (1) `from`
  statt `teeP` in `tee()` — jede Abschlagsbewertung waere im Browser mit ReferenceError gestorben;
  (2) `N` statt `S.length` ebenda; (3) `caddyClubs` verlor durch die Driver-Leitplanke den Driver
  aus jeder positionslosen Liste — die Leitplanke kennt jetzt drei Zustaende (Abschlag /
  nachweislich Boden / unbekannt). Dazu: `DGM_KORRIDOR` 130 statt 160 — der Pruefstand wies ±80
  (17.496 Punkte) und ±70 (16.384) am 16.000er-Ladelauf-Deckel zurueck; ±65 traegt die
  Abtastgeometrie exakt, den Rest faengt die einseitige Differenz. `sigmaFor` baut die Rueckgabe
  vor der Deckel-Meldung. Changelog auf die 40 neuesten Fassungen gekuerzt (Regel v3.11), der
  Ueberhang liegt als `changelog-archiv-nachtrag.md` bereit. Neue Doku-Kapitel: Mitspieler,
  Neutralwerte, Lochzeiger-Zaehler & Rundenende. Neue Pruefgruppen fuer `schlagNeutral`,
  `neutralBasis`, `gpsShotsNachziehen`, `sicherheitsWahl`, `neigungUmZiel`, `ohneHoehe`;
  veraltete Vertraege (Streifenbreite, Rand-/Lueckenverhalten, Uhr-Kennung 37, Beenden-Regel der
  Uhr, Stummel-Fixture ohne Driver) aktualisiert.

- **v4.82.1 · 2026-08-26** — **Das Hoehenraster wird jetzt auch am Rand VERWENDET — der Download
  war nie das Problem.** Einwand vom 26.08., zu Recht: „Ich habe das Raster extra vorher
  heruntergeladen — das muss 100 % funktionieren." Die Kette Download -> IndexedDB -> Laden beim
  Rundenstart war intakt. Der Fehler sass im NACHSCHLAGEN: Der Streifen war ±60 m um die
  Ideallinie, die Engine zielt aber bis ±10° daneben (±40 m bei 230 m Carry), und die
  Neigungsmessung tastet weitere 2,5 Maschen (12-50 m) darueber hinaus — 90 m Bedarf gegen 60 m
  Streifen, konstruktionsbedingt. Und EIN fehlender Tastpunkt machte die GANZE Neigung null,
  eine fehlende Ecke die ganze Hoehe: Am Rand der Normalfall, nicht die Ausnahme. **Drei
  Reparaturen:** (1) Streifen ±80 m (`DGM_KORRIDOR` 160); bestehende Raster zeigen die neuen
  Randzellen als „fehlend" — einmal Nachladen fuellt sie, online holt `gpRasterBereit` sie beim
  naechsten Planlauf. (2) `dgmHoehe` randfest: halbe Masche Klemmtoleranz am Rahmen, und fehlt
  eine Ecke, tragen die vorhandenen mit neu normierten Gewichten (gleiche Quelle, weniger
  Stuetzen) — erst ohne jede Ecke null. (3) `dgmNeigung` einseitig statt gar nicht: je Achse
  zentrale Differenz wie bisher, sonst einseitige zur Mitte, markiert als `genaehert`; null nur
  noch, wenn keine Achse rechenbar ist — und dann sagt es die 4.82-Meldung. Damit gilt: Ein
  vollstaendig geladenes Raster liefert an jedem Punkt des Platzes eine Auskunft.

- **v4.82.0 · 2026-08-26** — **Caddy: Hang als Flaeche · fehlende Hoehendaten laut · Gleichstand
  entscheidet die Sicherheit.** Drei Befunde von der Nordplatz-Runde am 26.08., drei Ursachen:
  **(1) Loch 1, Eisen 6 auf den Abhang:** Der Hangterm (v3.95/v3.98) fragte EINEN Punkt — das
  Ziel. Ein Hang zehn Meter dahinter zaehlte null, obwohl die halbe Streuwolke dort landet.
  `neigungUmZiel` mittelt jetzt ueber drei Punkte entlang der Ziellinie (Ziel, ±0.8·σD).
  **Dazu die stumme Wurzel:** Die Runde begann direkt nach der DNS-Stoerung — ohne geladenes
  DGM-Raster wurden Hangterm UND Hoehe in spielt-wie still zu null ("kein stiller Ersatzwert"
  war richtig, STILL war falsch). Das erklaert Loch 1 (Hang unberuecksichtigt) und Loch 3
  (Eisen 8 statt 7 bergauf) mit derselben Wurzel; ab jetzt steht es einmal je Loch im Protokoll
  („Caddy ohne Höhendaten"). **(2) Loch 2, Driver an die Engstelle:** Hauptursache war die bis
  v4.81.2 verschmutzte Streuung — auf einer flach gewordenen Bewertungslandschaft gewinnt die
  reine Laenge. Zusaetzlich: Innerhalb von 0.06 ES (Rauschniveau bei N=100) ist „laenger" kein
  Argument mehr — dort gewinnt ab jetzt der Kandidat mit der hoeheren Fairway-/Gruenquote
  (`sicherheitsWahl`, markiert im Kandidaten). Ein echter ES-Vorteil darf weiterhin Risiko
  kaufen. **(3) Loch 3, ein Schlaeger zu kurz:** Neben der Hoehen-Wurzel aus (1) wirken hier die
  bis v4.80 unbereinigt gelernten Laengen (Bergab-/Rueckenwind-Messungen lehren zu lang — die
  Neutralwerte korrigieren das; nach ein paar Tagen lohnt ein Blick auf „Gepflegt vs. gemessen"
  und ggf. „gemessenen Wert übernehmen"). Erwartung fuer die naechste Runde: Taucht „Caddy ohne
  Höhendaten" auf, ist der Platz-Cache vor dem Start zu laden — die Meldung sagt es dann selbst.

- **v4.81.2 · 2026-08-26** — **Streuungs-Lerner filtert wie der Laengen-Lerner · Driver nur vom
  Abschlag · Deckelung wird gemeldet.** Befund von der Runde am 26.08.: Driver-/Holz-Ovale am
  Anschlag, „FW 21 %" vom Tee, und mitten auf Bahn 8 ein Driver-Vorschlag vom Fairway. **Drei
  Ursachen, drei Aenderungen:** (1) `learnFromGps` nahm — anders als `clubMeasured` — JEDEN Schlag
  mit dem Etikett: Teilschlaege, Vorlege-Driver, und bis Uhr-Fassung 35 jeden automatisch
  erfassten Schwung mit geerbtem Plan-Schlaeger. Jetzt: nur volle Schwuenge, und nichts unter 40 %
  der gepflegten Schlaegerlaenge (ein getoppter 110-m-Drive ist Streuung und bleibt; ein
  „Driver · 40 m" ist ein falsches Etikett und fliegt). Das 60-Tage-Fenster laesst die Altlast
  herauswachsen; wer nicht warten will: R10-Import ueberschreibt, oder falsch etikettierte
  GPS-Schlaege in der Schlagliste loeschen. (2) Die Leitplanke `teeOnly` vergass den DRIVER —
  sie prueft jetzt Driver UND Driving Iron (`!vomTee`). (3) Wird eine gelernte Streuung vom
  Deckel (13 %/11 % der Carry) gekappt, steht das jetzt EINMAL je Schlaeger und Sitzung im
  Protokoll, mit Rohwerten — der Deckel rettete die Rechnung, aber er versteckte den Befund
  wochenlang.

- **v4.81.1 · 2026-08-26** — **Der Takt starb im `closeSheet()` — behoben, plus Minutenwaechter.**
  **Gemessen auf der Runde vom 26.08. (08:29-10:58):** keine einzige Abgleich-Zeile im Protokoll —
  und genau das war der Befund: Der Takt lief nicht, und seine eigene Messung (`taktPruefen`)
  faehrt IM Takt mit, also blieb auch sie stumm. **Ursache:** `closeSheet()` loeschte den
  Sync-Zeitgeber bedingungslos (`playStopSync()`) — v4.77 hatte die `PLAY.live`-Verknuepfung nur
  IM Takt entfernt, die direkte Toetung beim Blatt-Schliessen blieb; neu gestartet wurde erst beim
  Oeffnen der Live-Ansicht. Auf der Runde (Karte im Vollbild, Blatt auf, Blatt zu) synchronisierten
  deshalb nur noch die Aufwach-Ausloeser: „Es ging nur, wenn das Handy schwarz war und aufgeweckt
  wurde." **Zwei Aenderungen:** (1) `closeSheet` stoppt den Takt nur noch OHNE laufende Runde —
  dieselbe Schutzbedingung wie beim Wake Lock direkt darueber. (2) **Minutenwaechter** (Wunsch):
  dieselbe Funktion, die beim Aufwecken greift, laeuft zusaetzlich einmal je Minute — beim Laden
  installiert, von keinem Lebenszyklus erreichbar, prueft auf neue Eintraege (`playSyncBusy`
  schuetzt vor Doppellauf) und traegt kuenftig die Messung hin: Steht der 2-s-Takt je wieder
  still, meldet „Takt stand still … ohne Zeitgeber" es binnen einer Minute, statt dass die Stille
  sich selbst verbirgt.

- **v4.81.0 · 2026-08-26** — **Mitspieler: bis zu drei, je Loch nur der Endscore, mit Namen.**
  Wunsch vom 26.08. Bereich „Mitspieler" in der Eingabemaske (unter Score/Putts): Name antippen =
  umbenennen oder entfernen (leerer Name; Scores des Entfernten werden mit entfernt, Nachfolgende
  ruecken auf — sonst stuenden fremde Scores unter falschem Namen), „+ Mitspieler" bis drei.
  Stepper wie beim eigenen Score (Start bei Par), laufende Summe im Etikett. **Daten:** Namen in
  `round.mitspieler`, Scores als `msc1..msc3` je Loch — sie reisen wie jedes Lochfeld ueber den
  Entwurf (Loch-Zeitstempel entscheidet, null loescht nichts), die Uhr (Fassung 37) zeigt dieselben
  Zeilen auf Seite 2 und uebernimmt die Namen aus dem Entwurf; vergeben werden sie nur hier (die
  Uhr hat keine Tastatur). In der gespeicherten Runde stehen Namen und Scores mit drin. **Bewusst
  nicht:** Putts, Statistik oder SG fuer Mitspieler — „nur der Endscore" ist die Vorgabe.

- **v4.80.1 · 2026-08-26** — **Uhr-Messungen werden beim Eintreffen umgerechnet — die Laengen-Logik
  sitzt allein im Handy.** Aufgabenteilung ausdruecklich festgeschrieben (Vorgabe vom 26.08.): Die
  **Uhr misst rohe Meter** und enthaelt keinerlei Anpassungslogik (kein `playsLike`; ihre
  Caddy-Werte kommen fertig gerechnet vom Handy — das war schon so und bleibt so, die Uhr-App ist
  unveraendert auf Fassung 35). Neu: `gpsShotsNachziehen` rechnet Uhr-Messungen am **Engpass ihres
  Eintreffens** (`draftPull`-Vereinigung) auf den Neutralwert um — waehrend der Runde treffen sie
  binnen Sekunden ein, das Wetter von JETZT ist das Wetter des Schlags, die volle Rechnung ist
  ehrlich. Spaete Ankuenfte (Gross-Datei-Merges in beiden Takten) laufen durch dieselbe Funktion;
  die 3-h-Sperre in `schlagNeutral` laesst dann von selbst nur die zeitlose Hoehe rechnen. Einmal
  berechnet, persistiert der Wert am Schlag; das Protokoll nennt die Anzahl („n Uhr-Schlaege auf
  Neutralwert umgerechnet"). `neutralBasis` bleibt Rueckfall fuer Altdaten ohne Kontext.

- **v4.80.0 · 2026-08-26** — **Gelernte Schlaegerlaengen auf Neutralbedingungen („spielt-wie" rueckwaerts).**
  Die spielt-wie-Rechnung (Wind, Temperatur, Hoehe, Regen) gab es laengst — aber nur auf der
  EMPFEHLUNGS-Seite. Die LERN-Seite frass rohe Messwerte: Ein Bergab-Rueckenwind-Drive lehrte
  „Driver = 260", ein Bergauf-Gegenwind-Eisen lernte systematisch zu kurz. **Die Umkehrung ist
  dieselbe Funktion:** Ein unter Bedingungen C gemessener M-Meter-Schlag hat die Aufgabe
  `playsLike(M, C)` geloest — das ist sein Neutralwert (`schlagNeutral`, gespeichert als
  `distNeutral` + `dElev` am Schlag). Berechnet an allen drei Entstehungsorten
  (`playRecStop`, `gpsSaveShot`, `watchRecFinish`). **Wetter nur zeitnah** (±3 h — WEATHER ist
  das Wetter JETZT; fuer spaet eingetroffene Uhr-Messungen waere es das falsche): danach rechnet
  nur die Hoehe, die ist zeitlos. **Altdaten:** `neutralBasis` bereinigt Schlaege mit Koordinaten
  beim Lernen wenigstens um die Hoehe (DGM/Cache, synchron); ohne Kontext bleibt der Rohwert —
  ehrlich, statt zu raten. `clubMeasured` lernt auf dieser Basis; der Vergleich „Gepflegt vs.
  gemessen" sagt es dazu. Die Empfehlungs-Seite bleibt unveraendert — sie rechnete schon richtig.

- **v4.79.2 · 2026-08-26** — **Frist auf jeden Abgleich-Fetch · Waechter fuer `busy` · Fehler sagen ihren Namen.**
  **Korrektur der Deutung von v4.79.1:** Das Handy war wach und im Vollbild — und tat ab
  05:35:06 trotzdem nichts, ohne eine Zeile zu schreiben. Diese Signatur hat genau eine
  Mechanik: Ein Abgleich-Fetch OHNE Frist blieb auf stockendem Netz HAENGEN (kein Fehler — er
  kam schlicht nie zurueck; dass das Heimnetz an diesem Morgen wacklig war, zeigen die zwoelf
  DNS-Fehler der Uhr bis 05:34). `await` wartete ewig, `playSyncBusy` blieb true, und jeder
  weitere Takt kehrte in der ersten Zeile still um — waehrend `taktPruefen` die Takte puenktlich
  kommen sah und deshalb auch nichts meldete. Drei Aenderungen: **`fetchMitFrist`** (12 s lesen,
  15 s senden, 20 s grosse Datei) auf draftPull, draftPush, draftPushRaw, freshRepoSha,
  freshRepoFetch; ein **Waechter**, der ein laenger als 30 s gehaltenes `busy` meldet und
  freigibt; und der Takt-`catch` **protokolliert** statt zu schlucken (das Protokoll
  dedupliziert Wiederholungen selbst). Die naechste Stille hat damit einen Namen im Protokoll —
  oder sie tritt nicht mehr ein.

- **v4.79.1 · 2026-08-26** — **Aufwach-Ausloeser haengt nur noch an `PLAY.active`.**
  Erster Testlauf nach Ritual (26.08., 05:35): **Der Zaehler haelt** — vier Zeiger-Pruefungen,
  alle „schon dort", **keine** falsche Uebernahme; die Rueckspruenge sind weg. Versagt hat
  Punkt (c), Handy-Haelfte: Runde aktiv, Blatt zu, Handy gesperrt — die Uhr schrieb 26 Aktionen
  mit HTTP 200 ins Repo („Handy sah #0, 26 offen"), das Handy schlief. Beim Aufwecken loeste der
  `visibilitychange`-Handler das Aufholen nicht aus: Er pruefte noch `PLAY.live` — dieselbe
  Verwechslung von Anzeige und Datenfluss, die v4.77 im Takt selbst schon behoben hatte, an
  dieser zweiten Stelle uebersehen. Jetzt zaehlt auch hier nur `PLAY.active`.
  **Fuers Urteil wichtig:** Aufholen ist ein Lesevorgang und laeuft nur am wachen Geraet. Nach dem
  Aufwecken dem Spielmodus ~10 s geben, ehe „fehlt" festgestellt wird — die Bilanzzeile
  („Aufholen", Anzahl) zeigt, wann er durch ist.

- **v4.79.0 · 2026-08-25** — **Zaehler statt Uhrzeit: der Lochwahl-Zaehler `holeSeq`.**
  v4.52, v4.56, v4.58 und v4.78 haben denselben Fehler in wechselnder Gestalt repariert: ein
  Lochabgleich in beide Richtungen, entschieden ueber **Zeitstempel zweier Geraete** — dazwischen
  Echos, Tab-Drosselung (Bilanz 25.08.: Median 47 s), CDN-Latenz. Diese Fassung beseitigt die
  **Klasse**: Nur eine **Benutzerhandlung** erhoeht den Zaehler (`playHoleStamp`, also
  `playPrev/Next` — Uebernahmen und der Automatik-Sprung beim Fortsetzen **nicht**), der Zeiger
  traegt ihn, und es gewinnt schlicht die hoehere Nummer. Ein Echo traegt nie eine hoehere Nummer
  als die, die es gesehen hat — wirkungslos, egal wie spaet oder wie frisch gestempelt. Fremde
  Staende werden **immer** angehoben (`playAdoptRemoteHole`, auch bei „schon dort"; ebenso bei der
  Rundenuebernahme von der Uhr), damit die naechste eigene Wahl auf dem Maximum aufsetzt.
  Dasselbe Prinzip meldet in der Eingabespur seit Tagen „keine Luecke" — der Lochzeiger bekommt es
  jetzt auch. **Erfordert Uhr-Fassung 34** fuer die neue Regel; `wahlAt` (v4.78) und die at-Regel
  bleiben als Netz fuer aeltere Uhren.
  **Pruefritual** (vor jedem Urteil „geht/geht nicht", 2 Minuten — ausfuehrlich im Uhr-Changelog
  bei Fassung 34): 1. Fassungen ablesen (hier 4.79.0 auf „Heute"; Pages-CDN cached — frisches
  `?v=` laden). 2. Skript: Uhr 1→2→3 blaettern, 10 s, Handy zeigt 3; Handy →4, 10 s, Uhr zeigt 4;
  Score auf L4. 3. Protokoll: Spur vollstaendig, genau EIN „Loch ⇐ Handy 4", kein ungewaehltes
  Loch, Puls „Handy sah #N ✓". Ein Durchgang, ein eindeutiges Urteil.

- **v4.78.0 · 2026-08-25** — **Die eigene Lochwahl reist im Zeiger mit (`wahlAt`).**
  **Gemessen mit der Spur der Uhr (deren Fassung 33):** Der gedrosselte Tab (Bilanz: Median 47 s)
  uebernahm das Uhr-Loch verspaetet und stempelte die Uebernahme **frisch** — die Uhr las daraus
  eine neue Handy-Wahl und sprang zurueck („Loch ⇐ Handy 2/3"). Dazu ein Automatik-Sprung beim
  Fortsetzen („Loch ⇐ Handy 14": erstes Loch ohne Score, Restdaten — von niemandem gewaehlt).
  **Ein Zeitstempel sagt, wann geschrieben wurde — nicht, wie alt die Information ist.** Deshalb
  traegt der Zeiger jetzt zusaetzlich `wahlAt` = `PLAY.holeAt`, die letzte Lochwahl des
  **Benutzers** an diesem Geraet. `playPrev/Next` stempeln sie; `playAdoptRemoteHole` und der
  Rundenstart **nicht** — Echos und Automatik sind damit als solche erkennbar. Die Uhr (ab 33)
  folgt nur noch einer Wahl, die juenger ist als die letzte Handlung auf der Uhr.
  **Eine Zeile Daten, keine Logikaenderung hier:** faellt `wahlAt` weg, gilt auf der Uhr die alte
  Regel weiter. Rueckwaertskompatibel in beide Richtungen.

- **v4.77.0 · 2026-08-25** — **GEFUNDEN: Der Takt hielt sich selbst an — und meine Messung nannte
  es „gedrosselt".**
  **Der Einwand war entscheidend:** „Die App war die ganze Zeit im Vordergrund, im Vollbild." Und
  doch stand im Protokoll „Takt gedrosselt: 152 s statt 2 s · **Bildschirm an**". Beides zugleich
  ist unmöglich — also war meine Meldung falsch.
  **Sie war es:** Der Takt wurde nicht gestreckt, **er lief gar nicht.** `playSyncTick` rief
  `playStopSync()` und löschte den Zeitgeber, sobald `PLAY.live` false war — und das setzt **jedes
  `closeSheet()`**, also jedes Schließen eines Blattes nach einer Eingabe.
  **Damit passt endlich alles zusammen:** Die erste Eingabe geht durch, weil der Takt noch läuft.
  Danach schließt sich das Blatt, der Abgleich hält sich selbst an, und nichts startet ihn wieder —
  bis ein `focus`-Ereignis das Aufholen auslöst. Genau das steht im Protokoll: **„Aufholen nach
  focus", dann 19 Aktionen auf einmal.** Das ist die Beschreibung „erst geht es, dann bricht alles
  zusammen", seit Tagen und in jeder Sitzung.
  **Die Reparatur:** Der Abgleich hängt nur noch an `PLAY.active` — an der **Runde**, nicht an der
  Anzeige. `PLAY.live` steuert weiterhin die Live-Ansicht, wofür es gedacht ist.
  **Dieselbe Verwechslung wie auf der Uhr bei `if (!Live.running)`** (Uhr-Fassung (25)): eine
  Bedingung, die von der Anzeige handelt, entschied über den Datenfluss. Zweimal derselbe Fehler an
  zwei Geräten — und beide Male sah er wie ein Übertragungsproblem aus.
  **Und die Messung unterscheidet jetzt:** „Takt stand still" (kein Zeitgeber) gegen „Takt
  gedrosselt" (Browser hat gestreckt). **Eine Messung, die zwei verschiedene Ursachen gleich
  benennt, schickt in die falsche Richtung** — sie hat mich eine Fassung gekostet.

- **v4.76.0 · 2026-08-25** — **Gefunden: Der Lochzeiger war zu alt — und die Quittung stand am
  falschen Ort.** (Uhr-Fassung 2026-08-25 (29).)
  **Der Puls hat den Widerspruch in eine Zeile gebracht:**
  „Loch 5/18 · 49 Vorgänge, 3 misslungen · HTTP 200 · **eigenes Loch 1**"
  Der Kontext wusste Loch 5, gesendet wurde Loch 1 — bei lückenlosen HTTP 200 im Sekundentakt.
  **Die Uhr sendet einwandfrei. Sie sendet nur das Falsche.**
  **Ursache 1:** `snapHole` wurde am **Anfang** von `syncNow` gelesen — vor der Entprellung
  (600 ms), vor dem Netzaufbau und vor bis zu vier Wiederholungen bei 409. Wer in dieser Zeit
  weiterblättert, sendet ein Loch, auf dem er nicht mehr steht; bei einer **Reihe** schneller
  Eingaben verschiebt sich **jeder** Zeiger um einen Schritt nach hinten. Das Handy folgte deshalb
  immer dem vorletzten Stand und wirkte, als folge es gar nicht.
  Der Zeiger wird jetzt unmittelbar vor dem Absenden gelesen. Die übrigen Aufnahmen bleiben früh:
  Sie beschreiben, **was** eingegeben wurde — der Zeiger beschreibt, **wo man jetzt steht**.
  **Ursache 2:** Die Quittung `seenAktion` stand nur im **Zeiger**. Den überschreibt aber jede Seite
  bei jedem Vorgang, und die Uhr sendet um ein Vielfaches öfter — also fand sie ihre eigene Quittung
  nie: „Handy sah #13", während das Handy längst bei #27 war. Sie steht jetzt auf der obersten
  Ebene, wo `mergeDraft` sie feldweise vereinigt.
  **Zum Mitnehmen:** Eine Momentaufnahme ist nur so gut wie der Abstand zu ihrer Verwendung. Und
  zwei Dinge, die verschieden oft geschrieben werden, gehören nicht in dasselbe Feld.

- **v4.75.0 · 2026-08-25** — **Drosselung messen statt vermuten — und was für Chrome wirklich
  hilft.**
  Auf der Uhr misst der Herzschlag seit (27), ob er länger stand als erlaubt. Hier fehlte das
  Gegenstück. **Ohne es haben wir zwei Tage lang über Drosselung geredet, ohne sie je gemessen zu
  haben.** `taktPruefen()` schließt das: Wir wissen, wie lange zwischen zwei Durchläufen liegen
  soll; dauert es das Dreifache (und mindestens 10 s), hat der Browser gestreckt.
  **Die Meldung nennt den Modus und den Bildschirmzustand** — denn ob und wie stark gedrosselt
  wird, hängt an drei Schaltern **am Gerät**: installiert oder als Tab, Bildschirm an oder aus,
  Akku-Optimierung für Chrome. Keiner davon steht im Code, und nur eine Messung sagt, ob sie
  richtig stehen.
  **Zu Periodic Background Sync, ausdrücklich:** Chrome hat die Schnittstelle, aber ihr
  Mindestabstand richtet sich nach der Nutzungshäufigkeit der Seite und liegt in der Praxis bei
  Stunden, nicht Minuten. **Für eine Golfrunde ist sie damit nutzlos** — sie würde einen
  Umbau rechtfertigen, der nichts einbringt. Deshalb bewusst nicht gebaut.

- **v4.74.0 · 2026-08-25** — **Der Umbau der Handy-Seite: Aufholen beim Wiedersehen.**
  **Der Befund war eindeutig:** Die Uhr sendet einwandfrei — 24 Vorgänge, HTTP 200, **kein einziges
  „Schleife stand"**. Das **Handy** liest nicht. Im Hintergrund streckt der Browser seine Zeitgeber
  auf ein Vielfaches des eingestellten Takts; aus 30 s wurden gemessene **144–207 s**.
  **Dagegen ist nicht anzukommen, und das ist richtig so.** Ein Browser im Hintergrund darf nicht
  pollen. Beeinflussbar ist der **Moment des Wiedersehens** — er muss sofort aufholen statt auf den
  nächsten Takt zu warten.
  **Drei Änderungen:**
  · **Nicht nur `visibilitychange`**, sondern auch `pageshow` und `focus`. Auf Android kommt beim
  Zurückholen je nach Browser das eine oder das andere — wer nur auf ein Ereignis hört, verpasst die
  Hälfte.
  · **Ohne die `PLAY.live`-Bedingung.** Das Aufholen wurde übersprungen, wenn der Live-Modus aus war
  — dabei ist genau dann etwas nachzuholen. Die Bedingung gehört zum **Takt**, nicht zum Aufholen.
  · **Entprellt (1,5 s)**, weil `pageshow` und `focus` zusammen kommen und sonst zwei Abgleiche
  gleichzeitig liefen. Das ist der **409**, der seit Tagen im Protokoll steht.
  Die Bildschirmsperre wird dabei neu angefordert — der Browser gibt sie beim Verstecken frei.
  **Was das nicht ändert:** Während das Handy in der Tasche ist, bleibt es still. Die Daten gehen
  aber nicht verloren — im Protokoll steht bei **jedem** Durchlauf „keine Lücke". Es ist eine Frage
  des Zeitpunkts, nicht der Vollständigkeit.

- **v4.73.0 · 2026-08-25** — **Gefunden: Der Sende-Auftrag der Uhr hing an der Anzeige.**
  (Uhr-Fassung 2026-08-25 (28) — nur die Uhr muss neu.)
  **Die Bilanz aus v4.72 hat es in zwei Zeilen gezeigt:**
  · beim Hinsehen: **4–5 s** bis zum Handy
  · mit gesenktem Arm: **119–208 s (Median 168)** — bei **„keine Lücke"**
  Es geht also nichts verloren, es kommt nur zu spät. Genau die Unterscheidung, für die diese
  Messung gebaut wurde.
  **Ursache:** `scheduleSync()` startete seinen Auftrag auf `rememberCoroutineScope()`. **Dieser
  Bereich gehört der Komposition.** Verlässt der Bildschirm die Anzeige, wird er abgebrochen — samt
  dem Sende-Auftrag und seiner 600-ms-Entprellung. Erst der Herzschlag holt es nach, daher die
  Minuten.
  **Und damit ist auch klar, warum (25) nicht reichte:** Der WakeLock hält den **Prozess** am Leben
  — 28 Minuten wurden zu drei. Der **Auftrag** starb trotzdem, weil er an der **Anzeige** hing.
  **Zwei verschiedene Lebensdauern, die ich für dieselbe gehalten habe.**
  Neu ist ein eigener Bereich mit `SupervisorJob`, der jeden Bildschirmwechsel überlebt und beim
  Verlassen sauber beendet wird. `SupervisorJob`, damit ein gescheiterter Vorgang nicht die
  folgenden mitreißt.
  **Zum Mitnehmen: Ein Auftrag, der etwas senden soll, gehört an die Lebensdauer des Prozesses —
  nicht an die eines Bildes.**

- **v4.72.0 · 2026-08-25** — **Zwei Messungen, die die Frage direkt beantworten — statt mehr
  Rohdaten.**
  **(1) Die Uhr misst sich selbst** (Fassung (27)). Der Verdacht aus (25) — Android friert den
  Prozess ein, wenn der Bildschirm ausgeht — ließ sich bisher nur aus **Ankunftszeiten** erschließen,
  und die sind zweideutig: „spät angekommen" kann auch Netz sein. Jetzt wird direkt gemessen: Wir
  wissen, wie lange ein Durchlauf schlafen **soll**. Dauert er länger als das Doppelte, hat jemand
  anders die Schleife angehalten — **und das kann nur der Prozess sein.** Gemeldet mit Zahlen
  („Schleife stand 340 s statt 10 s"), gezählt im Puls.
  **Das unterscheidet „Netz war weg" von „Prozess war eingefroren"** — genau diese Unterscheidung
  hat zwei Tage gefehlt, und ohne sie habe ich zwölf Fassungen an der falschen Stelle repariert.
  **(2) Das Handy rechnet die Bilanz** statt eine Liste auszugeben: „**12 Aktionen · Verzögerung
  5–27 s (Median 9 s) · keine Lücke**". Bei mehr als einer Minute Median kommt der Hinweis „die Uhr
  sendet verzögert — Bildschirm aus?" dazu.
  Beides stand vorher schon in den Zeilen — man musste es von Hand ausrechnen, und **genau das habe
  ich bei „#1 14:34:55 … empfangen 15:03:11" zweimal übersehen.** Eine Diagnose, die man erst
  auswerten muss, wird nicht ausgewertet.
  Die Uhrzeit der Uhr trägt kein Datum; ein Sprung über Mitternacht wird deshalb verworfen statt
  falsch gerechnet — **lieber keine Zahl als eine erfundene.**

- **v4.71.0 · 2026-08-25** — **Gefunden: Der Dienst der Uhr startete oft gar nicht.** (Uhr-Fassung
  2026-08-25 (25) — nur die Uhr muss neu.)
  **Die Eingabespur hat es mit Zahlen gezeigt**, nicht mit einer Vermutung: Die Aktionen #1–#10
  entstanden um 14:34:55 bis 14:35:45 und kamen um **15:03:11** beim Handy an — 28 Minuten später,
  alle auf einmal.
  **Ursache:** `svcStart` lief nur `if (!Live.running)`. Diese Bedingung verwechselt zwei Dinge —
  „läuft die Ortung" und „läuft der Dienst". Der Dienst hält aber nicht nur GPS, sondern den
  **PARTIAL_WAKE_LOCK**, und der hält den **Prozess** am Leben. Ohne ihn friert Android die App ein,
  sobald der Bildschirm ausgeht — samt der Schleife, die den Abgleich sendet.
  **Besonders tückisch bei GPS-Quelle „Handy":** Dann läuft drüben die Ortung, `Live.running` ist
  gesetzt, und der Dienst startet **nie**. Das erklärt, warum es mal ging und mal nicht.
  **Zum Mitnehmen:** Ein Dienst, der den Prozess am Leben hält, darf nicht an einer Bedingung
  hängen, die von etwas anderem handelt.
  **Und zur Einordnung:** Zwei Tage lang habe ich nach einem verlorenen Wert gesucht, wo es um einen
  angehaltenen Takt ging. Gefunden hat es nicht das Nachdenken, sondern die nummerierte Spur — die
  einzige Messung, die den **Weg** zeigt statt den Zustand.

- **v4.70.0 · 2026-08-25** — **Die Eingabespur hat geantwortet — und ein Lärmherd nebenbei.**
  **DER BEFUND, endlich mit Zahlen.** Das Protokoll zeigt um 15:03:11 die Aktionen **#1 bis #10**
  der Uhr — aufgezeichnet um **14:34:55 bis 14:35:45**. Sie lagen also **28 Minuten** auf der Uhr,
  bevor sie beim Handy ankamen, und trafen dann in einem Schwung ein.
  **Die Uhr zeichnet also alles auf und sendet es nicht.** Das ist ein anderer Fehler als alle, die
  ich seit gestern gesucht habe: Es geht nichts verloren, es kommt nur zu spät — und zwar genau
  dann, wenn man nicht hinschaut. Dazwischen: „eigene Quelle nicht erreichbar" (4×) und „Uhr meldet
  seit über 90 s keine Position" (3×).
  **Meine Vermutung, ausdrücklich als solche:** Die Sende-Schleife der Uhr lebt in einer
  Compose-`LaunchedEffect`. Geht der Bildschirm aus oder wandert das Handgelenk herunter, hält
  Android die Composition an — und mit ihr die Schleife. Der Vordergrunddienst hält nur die Ortung
  am Leben, nicht den Abgleich. Das passt auf alles: Es funktioniert, solange man hinsieht, und
  bricht ab, sobald man weitergeht. **Zu prüfen, bevor ich etwas ändere** — ich habe an dieser
  Stelle schon dreimal zu früh repariert.
  **Und behoben, was sofort zu beheben war:** `satMetaTest` schrieb **neunmal** „Unexpected token
  '<'" ins Protokoll. Der Metadatendienst antwortet je nach Punkt mit XML statt GeoJSON — das ist
  vorgesehen und wurde zwei Zeilen weiter sauber gemeldet; der `catch` meldete es zusätzlich als
  Fehler. **Wer einen Fall erwartet und abfängt, darf ihn nicht zusätzlich als Fehler melden.** Jede
  solche Zeile verdrängt eine, die etwas bedeutet — und genau daran ist die Fehlersuche der letzten
  Tage mehrfach gescheitert.

- **v4.69.0 · 2026-08-25** — **Luftbild SH: Bildflug-Datum abfragbar.** Neuer Knopf in der
  Kartenverwaltung (`mSatMeta`, nur bei SH-Quelle): fragt den Metadatendienst `WMS_SH_MD_DOP`
  an drei Punkten des Platzes ab und zeigt das Befliegungsdatum — scharf heisst nicht aktuell.
  Nebenbefund derselben Recherche: Der WMTS zum DOP20 wurde geprueft und VERWORFEN (nur
  UTM32-Kachelschema, feinste Cache-Stufe 0,30 m/Pixel); der WMS bleibt bewusst, Begruendung
  als Kommentar bei `SAT_SRC`. Betroffen: Abschnitt Satellit/Luftbild, Kap. 26.1, tests.js.

- **v4.68.0 · 2026-08-25** — **Die Eingabespur: Jede Handlung auf der Uhr bekommt eine Nummer, das
  Handy meldet zurück, bis zu welcher es gekommen ist.**
  **Warum das nötig war — und warum ich zehn Fassungen gebraucht habe:** Bis hierher ließen sich nur
  **Zustände** vergleichen. „Uhr auf Loch 9, Handy auf Loch 9" sah wie ein Erfolg aus und sagt in
  Wahrheit **nichts** darüber, ob die sechs Schritte dazwischen angekommen sind — ein Endstand kann
  auch zufällig übereinstimmen. Genau in dieser Lücke habe ich zehnmal geraten.
  **Die Uhr (Fassung (23))** nummeriert jetzt jede Handlung des Benutzers: beide Pfeilpaare und jede
  Score-Eingabe, mit Uhrzeit und Inhalt — „#7 13:42:11 Loch → 3", „#8 13:42:19 Eingabe L3 score=6".
  Die letzten zwanzig reisen mit dem Entwurf (rund 1 kB).
  **Das Handy** schreibt die neuen Zeilen ins Protokoll und quittiert mit `seenAktion`. Fehlt etwas,
  meldet es das ausdrücklich: **„LÜCKE: Uhr bei #12, hier zuletzt #8 — 4 Schritte nicht in der
  Liste"**. Und der Puls der Uhr zeigt dieselbe Rechnung von der anderen Seite: „Eingaben bis #12,
  Handy sah #8 ⚠ 4 offen".
  **Damit ist eine Lücke auf den Schritt genau sichtbar** statt „irgendwas kommt nicht an".
  **Eine Feinheit, die sonst wieder gekostet hätte:** Gelesen wird aus der **frischen** Fassung, nicht
  aus der vereinigten — `mergeDraft` behält unter Umständen den eigenen Entwurf, und dann sähe man
  die Eingaben der Uhr gar nicht.

- **v4.67.0 · 2026-08-25** — **Der Puls des Handys zeigte den Endzustand, nicht den Weg.**
  Im Protokoll vom 25.08. stand genau **eine** Zeile („Uhr meldet Loch 1 · schon dort", 13:26),
  während zwischen 13:26 und 13:32 **sechs Lochwechsel** lagen. Meine Fassung von v4.65 ersetzte
  sich vollständig selbst — gut gegen einen vollen Puffer, schlecht für die Fehlersuche.
  **Man sah, wo es endete, und nicht, wie es dorthin kam — und der Weg ist die Frage.**
  Jetzt bleiben die **letzten fünf** Entscheidungen stehen, nummeriert, damit eine Lücke auffällt.
  Reine Wiederholungen werden weiterhin nicht gedoppelt.
  **Was die vorliegenden Protokolle sagen:** Die Uhr meldet um 13:32:48 „eigenes Loch 9 · Handy-Loch
  9" — beide Geräte stehen also auf demselben Loch, und der Zeitstempel des Handys (13:28:20) passt
  auf die Sekunde zu dessen eigener Meldung „Loch 9" aus dem Kartenprüfer. **Für diesen Wechsel hat
  es funktioniert.** Ob es für alle sechs funktioniert hat, ist aus einer einzigen Pulszeile nicht
  zu beantworten — genau deshalb diese Änderung.

- **v4.66.0 · 2026-08-25** — **Die beiden Pulse nebeneinander zeigen: Der Austausch funktioniert —
  und der Zeiger des Handys alterte nicht mehr.**
  **Handy 13:13:51:** „Uhr meldet Loch 2 · eigenes Loch 1 · **ÜBERNOMMEN**".
  **Uhr 13:13:48:** „Handy-Loch 1 verworfen · ownHole=13:13:47" — die Uhr hält ihr eigenes Loch 2,
  weil der Benutzer es Sekunden zuvor gewählt hat.
  **Beide haben richtig entschieden.** Das Handy folgt der Uhr, die Uhr lässt sich nicht
  zurückdrängen. Genau so war es gedacht.
  **Ein echter Rest bleibt:** Die Uhr liest eine Minute später immer noch „Handy-Loch 1,
  at=13:13:41". Der Zeiger des Handys **alterte nicht mehr**. Ursache ist v4.57: Dort wird der
  Zeitstempel festgehalten, solange sich das Loch nicht ändert — richtig, sonst gewinnt beim
  Vereinigen immer, wer öfter sendet. **Zu breit war es aber:** Auch ein **übernommenes** Loch
  behielt den fremden Stempel.
  **Eine Übernahme ist eine neue Aussage** — „ich bin jetzt auch auf Loch 2", gesagt zu diesem
  Zeitpunkt. Sie gehört frisch gestempelt. Eingefroren bleibt nur die **Wiederholung des eigenen,
  unveränderten** Zeigers. Und ein **fremder** Stempel wird nie fortgeschrieben: Sonst trägt das
  Handy die Zeit der Uhr weiter, als wäre es seine eigene.
  **Zur Einordnung:** Diese Woche habe ich acht Fassungen lang an dieser Stelle gearbeitet und
  mehrfach danebengelegen. Die Ursache war jedes Mal nur mit **beiden** Protokollen zu sehen — die
  Aussage eines Geräts über den anderen reicht nicht.

- **v4.65.0 · 2026-08-25** — **Changelog-Regeln für die Uhr · Fassungsnotiz reist mit · und der
  Puls des Handys.**
  **(A) Die Uhr hatte die Doku, aber nicht den Prüfstand.** 1993 Zeilen Kopfkommentar mit 99
  Changelog-Einträgen — inhaltlich derselbe Standard wie hier. Was fehlte, war die Kontrolle
  darüber, und prompt fanden sich beim ersten Blick **vier doppelte Kennungen** (24.08. (5), 14.08.
  (2) dreifach, 09.08. (11)) und drei Einträge außerhalb der Reihenfolge. Hier wäre das seit v4.21.1
  sofort aufgefallen. **Kennungen bereinigt, Positionen nicht:** Verschieben würde Verweise brechen,
  und Verweise wie „siehe (9)/(10) vom 15.08." sind unser Gedächtnis. Vier Regeln prüfen das jetzt
  — sie kosten kein Byte auf der Uhr.
  **Und zwei Fehler in meiner eigenen Prüfung:** Sie verglich die Kennungen als **Text**, wo „(9)"
  größer ist als „(10)", und rechnete Verweise auf die PWA (`puttDiagnose()`) als fehlende
  Uhr-Funktionen. Beide gemeldet, beide behoben — ein Prüfstand, der falsch misst, ist schlimmer
  als keiner.
  **(B) `WATCH_NOTE`** — ein Absatz, der mit jedem Zeiger und jedem Protokoll mitreist. Die Kennung
  sagt **welche** Fassung läuft, die Notiz **was** sie geändert hat. Von Hand gepflegt,
  ausdrücklich: Ein Programm, das seine eigenen Kommentare ausliest, bricht beim nächsten Umbau
  lautlos.
  **(C) Der Puls des Handys — die fehlende Hälfte.** Der Puls der Uhr zeigt seit (15) **ihre** Seite;
  zuletzt „eigenes Loch 5 · Handy-Loch 2", also sendet sie korrekt. Was das **Handy** mit dem Zeiger
  macht, war weiterhin unsichtbar — ich habe seine Seite fünfmal erraten und dreimal danebengelegen.
  `playLiveRemote` gibt an **vier** Stellen `null` zurück, und von außen war nicht zu unterscheiden,
  an welcher. Jetzt nennt jede ihren Grund („Zeiger stammt vom Handy selbst", „zu alt (14 min)",
  „kein Zeiger im Entwurf"), und die Entscheidung steht dabei: **ÜBERNOMMEN** oder nicht.
  **Ohne diesen Gegenpart vergleicht man zwei Geräte anhand der Aussage von einem.**

- **v4.64.0 · 2026-08-25** — **„spielt-wie uneinig": Die Warnung war korrekt und trotzdem sinnlos.**
  Im Protokoll stand „Kopfzeile 2303 m · Kette 171 m" — auf einem 179-m-Loch. Die Erklärung ist
  einfach und war die ganze Zeit da: **`_aimBuild` baut die Kette ab ABSCHLAG**, absichtlich, damit
  der Lochplan auch von weit weg stimmt. Die Kopfzeile misst dagegen ab der **eigenen Position**.
  Beide sind für sich korrekt und liegen nur dann zusammen, wenn man auch am Abschlag steht — und
  das Handy lag 2,5 km entfernt auf dem Tisch.
  **Eine Warnung, die in dieser Lage immer angeht, verdeckt die Fälle, in denen sie etwas
  bedeutet.** Beide Caddy-Meldungen sind jetzt an `playTooFar()` gebunden, das diese Frage längst
  beantwortet — es fehlte nur, sie daran zu knüpfen.
  **Damit ist auch v4.63 richtig eingeordnet:** Die Vereinheitlichung des Zielpunkts war nötig und
  bleibt, aber sie war nicht der Grund für die Flut im Protokoll. Der Grund war, dass gar nicht
  gespielt wurde.
  **Auf der Uhr (Fassung 2026-08-25 (17)):** Die Standby-Seite zeigt jetzt Front, Mitte und Back in
  derselben Ordnung wie Seite 1 — dort schaut man im Gehen hin, und die Fahne steht selten mittig.
  Und **„spielt wie" steht direkt unter den drei Zahlen** statt weiter unten in der Caddy-Zeile: Es
  ist die Zahl, nach der man den Schläger zieht.

- **v4.63.0 · 2026-08-25** — **„spielt-wie uneinig": ein Aufbaufehler, kein Rechenfehler.** Die
  Warnung lief seit Tagen dutzendfach — Kopfzeile konstant 200–208 m, Kette zwischen 145 und 247 m.
  **Ursache:** v4.16 hat den **Schläger** vereinheitlicht; die Kopfzeile übernimmt ihn seither aus
  der Zielkette. Das **Ziel** blieb getrennt: Die Kopfzeile rechnete weiter gegen `ev.target` — den
  Punkt, den die Bewertung anpeilt —, die Kette gegen ihren eigenen Wegpunkt. **Zwei Bezugspunkte,
  zwei Zahlen**, und die Warnung verglich seither Äpfel mit Birnen. Deshalb war sie immer an.
  Die Warnung war also richtig gebaut und hat auf etwas Echtes gezeigt — **nur war das Echte kein
  Rechenfehler, sondern ein Aufbaufehler.**
  **Jetzt gilt: Gibt es eine Kette, ist ihr Ziel das Ziel.** `condFaktor` rechnet gegen denselben
  Punkt, aus dem auch Schläger und Distanz stammen. Die Kette hat Vorrang, weil sie gezogene
  Wegpunkte, Doglegs und die tatsächlich gespielte Distanz kennt — genau deshalb hat v4.16 schon
  den Schläger von dort genommen.
  **Warum der Platz-Durchlauf das nie gemeldet hat:** Er verglich `spieltWie` mit `L0.spielt` —
  aber `spieltWie` **wird aus der Kette übernommen**. Die Invariante verglich also eine Zahl mit
  sich selbst und konnte nie ausschlagen. Neu ist `spieltWieKopf` (die eigene Rechnung der
  Kopfzeile, ungefiltert) und eine Invariante darauf.
  **Offen und benannt:** Der Testplatz hat keine Doglegs und keine gezogenen Wegpunkte, deshalb
  fallen beide Ziele dort ohnehin zusammen — die neue Invariante schlägt auch bei zurückgebauter
  Reparatur nicht aus. Das ist eine Lücke des **Platzes**, nicht der Prüfung. Der Beweis wird das
  Feld sein: Bleibt „spielt-wie uneinig" im Protokoll aus, stimmt es.

- **v4.62.0 · 2026-08-25** — **Die Uhr übernahm dieselbe Runde immer wieder — und löschte dabei
  ihre eigenen Eingaben.** Endlich die Ursache für „bei Loch 1 geht es, beim Wechsel auf Loch 2
  bricht alles ab", und sie stand im Uhr-Protokoll: **„Runde übernommen · Loch 1" mehrfach** —
  08:06:32, 08:07:34, davor 07:30:16 und 07:32:03 (×2).
  **Dieser Zweig ist destruktiv:** Er macht `entries.clear()`, `measurements.clear()` und setzt das
  Loch auf das des **Handys**. Wer auf der Uhr zu Loch 2 wechselt und etwas einträgt, verliert beim
  nächsten Durchlauf beides — Eingabe weg, Loch zurück auf 1. Von außen sieht das aus, als sei der
  Abgleich tot.
  **Zum Mitnehmen: Ein Zweig, der Daten löscht, gehört hinter eine Bedingung, die genau einmal wahr
  ist.** Dieser stand hinter einer, die bei jedem Durchlauf wahr war. (Uhr-Fassung 2026-08-25 (14).)
  **Und der zweite gemeldete Punkt:** Kam ein Score von der Uhr, wurde der Spielbildschirm **nicht**
  neu gezeichnet — neu gezeichnet wurde nur beim Lochwechsel. Es erschien ein Hinweis „Eingaben
  übernommen", während die Anzeige oben rechts unverändert blieb. **Der Hinweis sagte „ist da", die
  Anzeige sagte „ist nicht da"** — und von beiden glaubt man der Anzeige, zu Recht. Behoben, und
  zwar an **beiden** Stellen: Dieselbe Logik stand zweimal im Code, und die eine zu reparieren und
  die andere zu übersehen ist der klassische Fall.

- **v4.61.0 · 2026-08-25** — **Der Diagnosebericht steht jetzt neben dem Protokoll, nicht darin.**
  Gemeldet: Nach dem Knopfdruck kam beim Handy nur „Runde übernommen" an, keine Diagnose.
  **Die Ursache ist der Aufbau, nicht ein einzelner Fehler.** Die Diagnose schrieb **in** den
  Ringpuffer — und räumte sich darin selbst auf (v4.60), damit sie ihn nicht verstopft. Ihre
  Zustellung hing damit an einem Puffer, den gleichzeitig Fehler, der Rundenentwurf und das
  Aufräumen bewegen: **drei Stellen, an denen ein Bericht verschwinden kann.**
  Nach vier Fassungen an derselben Sache war klar, dass das kein Fehler zum Reparieren ist, sondern
  ein Aufbau zum Ändern. `Diagnose.letzterBericht` ist jetzt eine **eigene Größe**, die nur der Knopf
  setzt und die nichts anderes anfasst. Sie reist als eigenes Feld `bericht` — auf **beiden** Wegen
  — und ist unabhängig davon, was im Puffer passiert. Im Puffer bleibt eine Zeile als Spur.
  **Zwei Dinge, zwei Wege.** Das ist der ganze Unterschied.
  **Dazu zwei Feinheiten, die sonst wieder gekostet hätten:** `logPut` vergleicht den
  Berichtszeitpunkt mit — sonst gilt „nichts Neues", obwohl gerade ein frischer Bericht entstanden
  ist. Und die App nimmt den **jüngeren Bericht**, nicht die jüngeren Zeilen: Der Rundenentwurf geht
  im Minutentakt raus und trüge sonst einen alten Bericht mit frischem Zeitstempel durch.
  **Angezeigt wird er ganz oben**, als eigener Block „Selbsttest der Uhr" — wer scrollen muss, liest
  ihn nicht.

- **v4.60.0 · 2026-08-25** — **Diagnose der Uhr kommt sofort an — und die App kann sie auf
  Knopfdruck holen.** Auf der Uhr wurde die Diagnose in den Puffer geschrieben, und der reiste nur
  mit dem Rundenentwurf oder im Fünf-Minuten-Takt. **Wer auf Diagnose drückt, will die Antwort
  jetzt** — meistens, weil gerade etwas klemmt. Die Uhr sendet nach dem Selbsttest deshalb sofort
  (`Net.logPut()`), und hier gibt es das Gegenstück: **„⟳ Von der Uhr neu laden"**.
  Der Knopf umgeht `_errLogGeholt` bewusst. Die Sperre ist gegen die Endlosschleife aus v4.58
  richtig, für einen Knopf namens „nochmal nachsehen" aber falsch — **kein Selbstaufruf, also auch
  keine Schleife**: Der Unterschied ist, dass hier der Mensch drückt und nicht die Funktion sich
  selbst.
  **Und die Diagnose ersetzt sich jetzt selbst.** Im echten Protokoll waren nach fünf Knopfdrücken
  **35 von 60 Zeilen** Diagnose — prompt meldete der Selbsttest „Protokoll fast voll". Sie
  verdrängte damit genau das, wozu sie da ist. Jetzt gibt es immer genau einen Stand; ein älterer
  beschreibt eine Lage, die vorbei ist. Echte Fehlermeldungen bleiben unangetastet.
  **Was der erste Durchlauf sonst gezeigt hat:** Worker v2.11 ✓ · Zeitversatz −2 s ✓ ·
  Schreibschlüssel ✓ · keine Konflikte ✓. **Der Verdacht auf Zeitdrift ist damit ausgeräumt** — und
  das ist auch ein Ergebnis.
  **Zum Prüfstand:** Meine neue Prüfung hat den richtigen Code als Fehler gemeldet, weil sie in
  einem geratenen Zeichenfenster suchte. **Das ist heute das vierte Mal** — jetzt wird bis zur Marke
  gescannt und der Abstand gemessen, statt ihn zu schätzen.

- **v4.59.0 · 2026-08-24** — **BUGFIX: Das Fehlerprotokoll riss die App mit — eine Endlosschleife
  aus v4.58.** Dort rief `showErrLog()` nach dem Nachladen **sich selbst** auf, um den frischen
  Stand zu zeigen. Der zweite Aufruf lud wieder nach und rief wieder sich selbst: Die Anzeige baute
  sich endlos neu auf, ließ sich nicht mehr schließen und nahm die App mit.
  Ich hatte auf „das Blatt ist offen" als Abbruch geprüft — **das ist beim zweiten Mal auch wahr.**
  **Eine Funktion, die sich selbst aufruft, um sich zu aktualisieren, braucht eine Bedingung, die
  beim zweiten Mal falsch ist.** `_errLogGeholt` ist sie; zurückgesetzt wird beim Schließen.
  **Und ein eigener Knopf für das Uhr-Protokoll** unter Daten → Diagnose, mit Anzahl und Alter im
  Text: „⌚ Uhr-Protokoll (12 · vor 3 min)". Vorher hing es unten am eigenen Protokoll — wer nach
  einem Uhr-Fehler sucht, musste erst das Handy-Protokoll öffnen und daran vorbeiscrollen. Ist
  nichts angekommen, sagt der Knopf das gleich, statt dass man tippt und Leere findet; dazu ein
  Hinweis auf den Worker-Stand, der die häufigste Ursache ist.
  **Nebenbei zwei fehlende `.catch`** an den neuen Zusagen — ausgerechnet beim Öffnen des
  Protokolls wäre ein unbehandelter Netzfehler bitter.
  **Zum Prüfstand selbst:** Meine erste Fassung der neuen Prüfung suchte in einem festen
  Zeichenfenster und übersah den Fehler — der Kommentar davor war länger als das Fenster. Jetzt
  wird bis zur Marke gescannt. **Ein festes Fenster ist die schwächste Art zu prüfen**, das ist
  heute das dritte Mal.

- **v4.58.0 · 2026-08-24** — **Das Uhr-Protokoll kam nie an — drei Ursachen.**
  **(1) Es hing am falschen Entwurf.** Ich hatte es an `val draft` gehängt — den Aufbau für die
  **große** Datei, also den Notweg, der praktisch nie läuft. Das Handy las `_draftRound.watchLog`
  aus `draft.json` und fand deshalb **nie** etwas. **Eingebaut und wirkungslos**, und ich habe es
  als fertig gemeldet, ohne den Weg zu verfolgen — dieselbe Nachlässigkeit wie bei den Saisonzielen.
  **(2) Selbst repariert erreicht es nur die Zeit während einer Runde.** `draft.json` gibt es sonst
  nicht — Fehler beim Start oder beim Platzladen kämen weiterhin nie an, und genau die sucht man
  beim Einrichten. Die Uhr schreibt jetzt zusätzlich **`watchlog.json`** (Worker v2.11) im
  Fünf-Minuten-Takt, der ohnehin läuft und auch ohne Runde läuft — **aber nur bei Änderung**: Bei
  fehlerfreiem Betrieb entsteht kein einziger zusätzlicher Vorgang. Die App liest beide Quellen und
  zeigt den **jüngeren** Stand, mit Angabe woher („aus Runde" / „aus Datei").
  **(3) Der Puffer war zu klein und zählte nicht.** 30 Zeilen füllte **ein** Vorgang — die
  409-Schleife von gestern schrieb vier je Versuch, und alles davor war weg. Die Vorgeschichte ist
  aber das, was man sucht. Jetzt 60 Zeilen, und **Wiederholungen werden gezählt statt gesammelt**
  („… ×5"), wie es die App seit je tut.
  **Beim Konzept aufgefallen und nicht nötig:** Ein Zeitstempel je Zeile fehlte gar nicht — die Uhr
  schreibt ihn seit Langem (`dd.MM. HH:mm:ss`), samt Ort und Faden. Ich hatte das behauptet, ohne
  nachzusehen.

- **v4.57.0 · 2026-08-24** — **Die Wurzel gefunden: Der Zeitstempel maß den Funkverkehr, nicht die
  Handlung.** Diesmal im Prüfstand nachgestellt statt geraten — mit zwei Befunden.
  **(1) Der Live-Zeiger des Handys wurde alle 10 s erneuert, auch ohne jede Änderung.**
  `caddyLivePush` schrieb ihn im Takt mit frischem `at`. `mergeDraft` behält beim Zeiger den mit dem
  **jüngeren** `at` — also war der eigene praktisch immer jünger, und der Zeiger der Uhr wurde beim
  Vereinigen **verworfen**. `playAdoptRemoteHole` bekam nie einen zu sehen. Daher „folgt fast nie".
  Und es war ein Zirkelschluss: v4.56 übernimmt den fremden Lochwert nur, wenn er im **lokalen**
  Entwurf steht — dorthin kam er nie.
  **Der Zeitstempel wird jetzt nur erneuert, wenn sich das Loch tatsächlich geändert hat.** Bleibt
  es gleich, bleibt auch `at` stehen. **Ein Zeitstempel, der bei jedem Senden hochzählt, misst den
  Funkverkehr, nicht die Handlung** — und beim Vereinigen gewinnt dann immer das Gerät, das öfter
  sendet, statt dem, das etwas getan hat.
  **(2) Eine fehlende Seitenangabe trennte die Runden.** `mergeDraft` verglich
  `date|course|side` — die Uhr setzt `side` nicht immer. Bei ungleichem Schlüssel fällt der Zweig
  auf „jüngerer Entwurf gewinnt **vollständig**" zurück: Der Score der Uhr auf Loch 3 war weg, ohne
  jede Meldung. Verglichen wird die Seite jetzt nur, wenn **beide** sie kennen; zwei echte Neuner am
  selben Tag bleiben getrennt, eine fehlende Angabe trennt nicht mehr.
  **An der Messung nachvollzogen:** vorher „Uhr-Score: undefined · Zeiger: phone Loch 1", nachher
  „Uhr-Score: 4 · Zeiger: watch Loch 3". Gegenprobe für beide Regeln einzeln: je eine Prüfung fällt.
  **Zur Einordnung:** vierte Fassung an dieser Stelle. Die drei davor haben Symptome behandelt — den
  Vorrang (v4.51/52), das Echo (v4.56). Erst das Nachstellen im Prüfstand hat gezeigt, dass beide
  Geräte korrekt arbeiteten und die **Vereinigung** die Information wegwarf. **Ich hätte drei
  Fassungen früher messen statt vermuten sollen.**

- **v4.56.0 · 2026-08-24** — **„Mal geht es, mal nicht" — ein Wettlauf, den ich in v4.52 selbst
  eingebaut habe.** Zwei Ursachen, beide auf der Handy-Seite.
  **(1) Das Handy schickte eine alte Kopie des Uhr-Zeigers zurück.** v4.52 gab den fremden Zeiger
  **unverändert** aus `_phoneLive` zurück — lokal war er damit gerettet. Aber die Aufrufer
  **schreiben und pushen** das Ergebnis: `caddyLivePush` alle 10 s, `playSaveDraft` bei jeder
  Eingabe. Das Handy schickte also eine **veraltete** Kopie ins Repo und überschrieb dort den
  frischen Zeiger, den die Uhr eine Sekunde zuvor geschrieben hatte. Wer zuletzt schrieb, gewann —
  daher „mal geht es, mal nicht".
  **Richtig ist: den Lochwert übernehmen, aber mit eigenem, frischem Zeitstempel senden.** Dann
  steht im Repo nie eine alte Kopie, beide Geräte sind sich über das Loch einig, und der nächste
  Vergleich rechnet mit einer Zeit, die stimmt.
  **(2) `playLiveSeenAt` wurde gesetzt, bevor feststand, ob das Loch abweicht.** Ein Zeiger, der beim
  Ankommen noch auf dasselbe Loch zeigte, galt damit als „gesehen" — und ein Lochwechsel im selben
  Zeitstempel war **bereits abgehakt, bevor er ausgewertet wurde.** Seit die Uhr sofort sendet
  (2026-08-24 (5)), kommen mehrere Zeiger je Sekunde; genau dort schlug das zu. Abgehakt wird jetzt
  nur, was **wirkt** — oder was zweifelsfrei nichts mehr bringt (Loch gibt es hier nicht).
  **Zur Einordnung:** Das ist die dritte Fassung an derselben Stelle. v4.51 hat die Uhr repariert,
  v4.52 das Handy — und dabei einen neuen Fehler eingeführt, der schwerer zu sehen war als der
  ursprüngliche, weil er nur unter Zeitdruck auftritt. **Eine Reparatur, die einen Wettlauf
  erzeugt, ist schlechter als der Fehler, den sie behebt**, denn sie versagt unregelmäßig.

- **v4.55.0 · 2026-08-24** — **Zusammenarbeit Uhr ↔ Handy: drei Lücken geschlossen, zwei entwarnt.**
  **(1) Welche Uhr-Fassung läuft?** `WATCH_APP` stand nur im Fehlerprotokoll — also nur sichtbar,
  wenn es Fehler gab. Wir haben diese Woche mehrfach geraten, ob eine Reparatur schon drüben ist.
  Die Uhr trägt ihre Kennung jetzt im **Live-Zeiger**, der bei jedem Herzschlag rausgeht; das Handy
  zeigt sie im Protokoll. **Nebenbefund:** Sie stand noch auf „2026-08-15 (13)", obwohl seither
  fünfmal geändert wurde. **Bewusst kein automatisches „veraltet":** Die Uhr wird von Hand gebaut,
  ein solches Urteil wäre oft falsch — die Kennung wird gezeigt, den Schluss zieht der Mensch.
  **(2) Das Handy quittiert die Schlagmessungen.** Die Uhr schickte sie bei jedem Vorgang mit, weil
  sie nie erfuhr, ob sie angekommen sind. `shotAck` nennt die Kennungen, die das Handy sicher hat;
  nur die räumt die Uhr aus. **Nur Kennungen, keine Messwerte:** Geht die Quittung verloren, schickt
  die Uhr noch einmal — der harmlose Fall. Umgekehrt wäre es Datenverlust.
  **(3) „Runde beendet" kommt jetzt bei der Uhr an.** Bis hierher kannte sie nur *verworfen*. Eine
  normal beendete Runde sah für sie aus wie eine laufende, die nur nichts mehr meldet: Sie funkte
  weiter Herzschläge für etwas, das es nicht mehr gab, und zeigte Loch 18, während am Handy die
  Karte schon gespeichert war. Übernommen wird nur bei **gleichem Platz** und nur, wenn die Marke
  **jünger** ist als die letzte eigene Eingabe — beides Lehren vom 15.08., wo eine alte Marke jede
  neue Runde sofort wieder beendet hat.
  **Zwei Befunde aus meiner eigenen Analyse haben sich beim Nachsehen erledigt:** Die Uhr holt ihr
  Wetter **selbst** (`Net.fetchWeather`, alle 20 min bei Bedarf) — meine Behauptung, sie hänge dafür
  am Handy, war falsch. Und die Schlagmessungen laufen über `draft.json` und werden in `draftPull`
  nach ID vereinigt, nicht über den stillgelegten Voll-Push. Zwei von fünf „Lücken" waren keine.

- **v4.54.0 · 2026-08-24** — **Der letzte Rest der Schieflage: Die Uhr wartete auch nach einer
  Eingabe auf ihren Takt.**
  **Zuerst eine Richtigstellung in eigener Sache:** v4.53 hatte die Takte bereits gedreht — ich habe
  das beim Messen übersehen und die alten Werte zugrunde gelegt (10 s Uhr-Push, 5 s Handy-Takt). Die
  **Entprellung 1500 → 600 ms stammt aus v4.53**, nicht von hier. Was dort noch fehlte, ist der
  eigentliche Punkt:
  **Das Handy sendet bei einer Eingabe sofort** — `playLivePush` ruft `draftPush()` direkt,
  ausdrücklich „nicht entprellt". **Die Uhr tat das nicht.** Ein Lochwechsel setzte den Zustand und
  wartete dann auf den nächsten Herzschlag. Selbst mit den kürzeren Takten aus v4.53 bleibt das ein
  Unterschied zwischen „sofort" und „beim nächsten Mal".
  **Die Regel, die gefehlt hat:** Eine Handlung des Benutzers sendet **sofort**, sie wartet nicht auf
  den Takt. Dieselbe Regel wie beim Zeiger-Vorrang in v4.51/v4.52 — nur beim Senden statt beim
  Übernehmen. Der Lochwechsel setzt jetzt `lastEditMs` und stößt `scheduleSync()` an.
  **Und eine Doppel-Sperre, damit der Gewinn nicht mit Akku bezahlt wird:** Der Herzschlag lässt
  einen Durchlauf aus, wenn der letzte Vorgang weniger als 5 s zurückliegt (`Net.letzterPushMs`).
  Ohne sie schickte jede Eingabe zweimal — einmal sofort, einmal im nächsten Takt.
  **Der Riegel aus v4.21.1 hat übrigens funktioniert:** Mein Eintrag trug zunächst dieselbe Nummer
  wie der bestehende, und der Prüfstand hat es gemeldet („keine Fassungsnummer doppelt vergeben").
  Genau dafür war er gedacht.

- **v4.53.0 · 2026-08-24** — **Abgleich beschleunigt — dort, wo es nichts kostet.** Erst gemessen,
  dann gedreht. Die vier Wege und ihre Verzögerung:
  · Uhr → Repo: 1,5 s Entprellung · Repo → Handy: bis zu 5 s Takt → **bis 6,5 s**
  · Handy → Repo: 2 s Entprellung · Repo → Uhr: bis zu 5 s Takt → **bis 7 s**
  Zusammen fühlt sich das an wie „reagiert nicht": Man blättert, schaut aufs andere Gerät und sieht
  noch das alte Loch.
  **Drei Stellschrauben, jede mit Begründung:**
  · **Handy-Takt vorn 5 s → 2 s.** Der Bildschirm ist an, jemand schaut hin. Die Abfrage ist billig
  — `?sha=1` liefert ein paar hundert Byte, und nur bei geänderter Kennung wird wirklich geladen.
  **Hinten bleiben 30 s**, dort schaut niemand hin.
  · **Uhr-Entprellung 1500 → 600 ms.** Sie fasst schnelle Taps zusammen, das bleibt richtig — aber
  ein Lochwechsel ist kein schneller Tap: Man drückt einmal und schaut dann hin.
  · **Uhr liest den Entwurf alle 2 s statt 5 s — nur bei angehobenem Arm.** Im Ambientmodus bleiben
  30 s. Der Akku merkt den Unterschied dort, wo die Uhr die meiste Zeit ist, und das ist der Arm
  unten.
  **Bewusst nicht schneller als 2 s:** Der Entwurfs-Push ist zwei Sekunden entprellt. Wer häufiger
  abfragt als geschrieben wird, erzeugt nur Verkehr — und im ungünstigen Fall genau die Konflikte,
  die beide Seiten danach aussitzen müssen. Der Prüfstand hält diese Kopplung jetzt fest.
  **Erwartung: 1–3 s statt 5–7 s.**

- **v4.52.0 · 2026-08-24** — **Der Spiegelfehler: Das Handy löschte den Zeiger der Uhr, bevor er
  gesehen wurde.** v4.51 hat die Uhr repariert — und das Umschalten funktionierte trotzdem nicht,
  weil **beide** Geräte denselben Fehler machten.
  **Auf dem Handy sitzt er in `playSaveDraft`:** Dort wird `DB._draftRound = {round, ts,
  live:_phoneLive(r)}` gebaut — der Zeiger wird also **vollständig ersetzt**, samt dem, was gerade
  von der Uhr kam. Und weil das Handy bei **jeder Eingabe und jedem GPS-Takt** speichert, war der
  Zeiger der Uhr weg, bevor `playAdoptRemoteHole` ihn überhaupt sehen konnte. Die Follower-Logik war
  da und richtig — sie kam nur nie zum Zug.
  **Wer öfter schreibt, gewinnt — das ist keine Regel, das ist ein Zufall.** Jetzt hält
  `playHoleStamp()` in `PLAY.holeAt` fest, wann auf **diesem** Gerät zuletzt geblättert wurde.
  `_phoneLive` übernimmt einen fremden Zeiger **unverändert**, wenn er jünger ist als diese Marke.
  Spiegelbildlich zu `ownHoleAt` auf der Uhr: **Eine Handlung des Benutzers wiegt schwerer als ein
  automatischer Zeiger — und das muss auf beiden Geräten gelten.**
  **Fünf Fälle im Prüfstand**, alle mit echten Werten gerechnet: frischer Uhr-Zeiger überlebt ·
  eigene jüngere Wahl gewinnt · fremde Runde wird ignoriert · veralteter Zeiger wird nicht
  wiederbelebt · ohne eigene Marke gewinnt die Uhr (sonst verlöre sie direkt nach dem Rundenstart).
  Gegenprobe mit zurückgebauter Regel: „erwartet 7, bekommen 1" — genau das gemeldete Symptom.

- **v4.51.0 · 2026-08-24** — **Zwei Sync-Fehler: Das Handy überstimmte immer, und der Abgleich
  brach ein. Beide gefunden, beide behoben.**
  **(1) Der Lochzeiger.** `ownLiveAt` wurde bei **jedem Push** gesetzt — verglichen wurde aber der
  Wert von **vorher**. Die Uhr sendet im Minutentakt, das Handy schreibt seinen Zeiger alle paar
  Sekunden: `at > ownLiveAt` war damit praktisch immer wahr. Und die zweite Bedingung machte es
  vollends verkehrt: `h != currentHole` traf ausgerechnet dann zu, wenn die Uhr-Eingabe **frisch**
  war — also wurde genau die verworfen.
  Neu merkt sich die Uhr die **Eingabe** (`ownHoleAt`, gesetzt beim Blättern). Das Handy übernimmt
  nur noch, wenn sein Zeiger jünger ist als die letzte Handlung auf der Uhr. **Eine Handlung des
  Benutzers wiegt schwerer als ein automatischer Zeiger** — diese Regel hat gefehlt.
  **(2) Der Einbruch des Abgleichs.** Nach einem **erfolgreichen** Schreibvorgang hat die Datei eine
  **neue** Kennung. Der Client kannte sie nicht und schickte beim nächsten Mal die alte: 409, neu
  lesen, neu senden — bei **jedem** Push. Schreibt parallel das andere Gerät, ist die
  Wiederholungsschleife der Uhr nach vier Versuchen erschöpft, und sie meldet „4× Konflikt (409) —
  Abgleich ausgesetzt". Genau der gemeldete Einbruch.
  **Worker v2.10** gibt die neue Kennung jetzt zurück (Kopf `X-Repo-Sha` und `sha` im Rumpf); App
  und Uhr übernehmen sie. GitHub liefert sie ohnehin in der PUT-Antwort mit — es hat nur niemand
  durchgereicht. Fehlt sie (älterer Worker), bleibt es beim alten Verhalten: ein Umlauf mehr, aber
  kein Abbruch.
  **Der Prüfstand vergleicht jetzt beide Bedingungen im Kt-Quelltext**, weil sich diese Klasse
  Fehler nur an der Struktur ablesen lässt — ausführen kann ich Kotlin hier nicht.

- **v4.50.0 · 2026-08-24** — **Der Lochwechsel ließ sich nicht übersetzen — und der Prüfstand hält
  jetzt fest, warum.** Der Compiler meldete `'val' cannot be reassigned` und
  `Unresolved reference 'cs'`: Mein Code schrieb `idx -= 1` und `cs.holes.lastIndex` **in**
  `PlayPager` — dort ist `idx` aber ein `val`-Parameter, und `cs` existiert gar nicht. Beides
  gehört der aufrufenden Composable.
  **Jetzt reicht `PlayPager` einen Rückruf `onHoleDelta(+1/−1)` durch**, und die Grenzen prüft die
  Stelle, die den Zustand auch **besitzt**. Ein Zielindex von der Anzeige aus wäre eine zweite
  Stelle, an der man sich verrechnen kann.
  **Warum ich es nicht sehen konnte:** Hier lässt sich kein Kotlin übersetzen. Die statische
  Durchsicht prüft Klammern — die stimmten (1211/1211). Sichtbarkeit von Namen und Veränderbarkeit
  prüft sie nicht; genau das steht seit dem 22.08. als Einschränkung im Kt-Changelog, und heute hat
  es zugeschlagen.
  **Was daraus folgt:** Der Prüfstand der App hält jetzt die **Struktur** fest, soweit sie sich am
  Text ablesen lässt — `PlayPager` darf `idx` nicht selbst zuweisen und die Kursdaten nicht kennen.
  Das ersetzt keinen Compiler, aber es fängt die Wiederholung genau dieses Griffs.

## Herkunft dieser Datei (27.08.2026)

Diese Datei existierte im Repo nicht — sie wurde beim Kürzen des Changelogs zwar genannt, aber nie
angelegt. Die archivierten Einträge waren damit **verloren**, obwohl der Prüfstand seit jeher auf
sie verweist.

**Wiederhergestellt aus der Git-Historie des Repos selbst**, nicht aus Erinnerung: 340 Commits von
`index.html` (29.07.–27.08.2026), davon 35 Schnappschüsse gelesen und alle jemals vorhandenen
Changelog-Einträge nach Fassungsnummer vereinigt. Das ist die einzige belastbare Quelle — ein aus
Gesprächsverläufen zusammengeschriebenes Archiv hätte Einträge erfunden oder verkürzt.

**Was das bedeutet:** 366 Fassungen von v1.0.0 bis v4.49.0. Die Schnappschüsse liegen ungefähr alle
zehn Commits; ein Eintrag, der zwischen zwei Schnappschüssen entstand *und* wieder aus dem
Changelog fiel, kann fehlen. Die vier ältesten Schnappschüsse (29.07.–05.08.) hatten noch gar kein
Changelog im devdocs-Block — was davor liegt, gibt es nicht mehr.

---


- **v4.49.0 · 2026-08-24** — **Uhr: Lochwechsel auf Seite 1 · Protokoll reist mit · Lagefaktoren
  angeglichen.**
  **(1) Loch vor/zurück auf der ersten Seite.** Bisher ging das nur über die Score-Seite — ein Wisch
  zu viel, wenn man mit Handschuh und Trolley dasteht und ohnehin auf Entfernung und Schläger
  schaut. **Die Übertragung ans Handy brauchte keinen eigenen Weg:** `idx` ist derselbe Zustand, den
  `pushDraft` als `live.hole` mitschickt — wer auf der Uhr blättert, blättert das Handy mit, wie
  umgekehrt seit Langem. Am ersten und letzten Loch wird der Pfeil **ausgegraut statt entfernt**;
  eine Schaltfläche, die verschwindet, lässt den Daumen ins Leere greifen.
  **(2) Das Fehlerprotokoll der Uhr reist mit.** Es war bisher nur auf der Uhr lesbar — rundes
  Display, kein Kopieren, mitten auf der Bahn. Ausgewertet wurde es damit praktisch nie. Jetzt hängt
  es als `watchLog` am Rundenentwurf: **keine neue Datei, kein neuer Worker-Pfad, kein zusätzlicher
  Funkverkehr** — der Entwurf geht ohnehin im Minutentakt raus, 30 Zeilen sind rund 3 kB. Die App
  zeigt es unter Mehr → Daten → Diagnose als eigenen Block, mit Gerät, Stand und Alter.
  **Bewusst NICHT in `ERRLOG` gemischt:** Das ist das Protokoll *dieses* Geräts; fremde Zeilen darin
  verlängerten es bei jedem Abgleich neu, und man könnte nicht mehr unterscheiden, **wo** ein Fehler
  aufgetreten ist.
  **(3) Der Abgleich, den du erfragt hast — mit einem Befund.** `playsLike` stimmt auf die Stelle
  überein (Temperatur 0,0022/°C, Gegenwind 0,014, Rückenwind 0,008, bergab 0,75). Die
  **Lagefaktoren nicht:** Sand 0,72 gegen 0,75 und **Recovery 0,58 gegen 0,80** — 22 Prozentpunkte.
  Aus demselben Erholungsschlag empfahl die Uhr einen deutlich kürzeren Schläger als das Handy.
  **Zwei Antworten auf dieselbe Frage sind schlimmer als eine falsche, weil sie das Vertrauen in
  beide kosten.** Angeglichen, dazu `penalty`/`ob` ergänzt, die es auf der Uhr gar nicht gab.
  **Was die Uhr bewusst nicht hat:** Erwartungswerte, `sigmaHang`, `sigmaLage`. Sie zeigt den vom
  Handy berechneten Gameplan und rechnet nur die Regel-Variante selbst — das ist die richtige
  Arbeitsteilung, und der Prüfstand hält jetzt fest, dass es so bleibt.
  **Neue Prüfgruppe „Gleichlauf Uhr ↔ App":** Sie liest `MainActivity.kt` und vergleicht die
  geteilten Zahlen direkt. Bis hierher stand nirgends geschrieben, dass die beiden dasselbe rechnen
  müssen — es wurde vorausgesetzt, und genau deshalb ist es auseinandergelaufen.

- **v4.48.0 · 2026-08-24** — **Der Absturz kam nach der Reparatur wieder — weil die Daten selbst
  beschädigt sind.** v4.47 hat den Riegel eingebaut, der Listen vor der Objekt-Vereinigung schützt.
  Was zwischen v4.41 und v4.46 bereits verformt wurde, liegt aber weiter im Bestand **und im Repo**,
  von wo es jedes Gerät wieder bekommt. Im Protokoll stand entsprechend unverändert
  `DB.approachBuckets.map is not a function`.
  **Eine Reparatur, die nur den Code heilt, heilt nichts.** `repairListenFormen()` wandelt die
  verformten Listen zurück: Ein Objekt mit den Schlüsseln 0, 1, 2… ist die Signatur genau dieser
  Verformung, `Object.values` in Schlüsselreihenfolge stellt sie wieder her.
  **Welche Felder Listen sind, sagt der SEED** — 27 Stück — und nicht eine von Hand gepflegte
  Aufzählung; die veraltet, sobald jemand ein Feld ergänzt. Ausdrücklich ergänzt sind nur `gear`,
  `tasks`, `gpsShots`, `notes`, `notesTrash` und `clubDistances`, die erst zur Laufzeit entstehen.
  **Vorsichtig, in eine Richtung:** Umgewandelt wird nur, was **ausschließlich** numerische
  Schlüssel trägt. Ein echtes Objekt bleibt unangetastet — lieber eine Verformung übersehen als
  fremde Daten zerlegen. Die Reparatur läuft **vor** allen anderen Startschritten, weil die sonst
  darüber stolpern.
  **Im Protokoll erscheint eine Zeile**, welche Listen zurückgewandelt wurden. Sie ist keine
  Warnung an dich, sondern der Beleg, dass es passiert ist — ein stiller Eingriff in gespeicherte
  Daten wäre das Falsche.

- **v4.47.0 · 2026-08-24** — **BUGFIX: Der Spielmodus stürzte beim Öffnen der Eingabe ab — meine
  Merge-Reparatur hat eine Liste in ein Objekt verwandelt.** Gemeldet mit dem Protokolleintrag
  `(arr || []).map is not a function` in `playSel`.
  **Ursache:** v4.41 nahm `approachBuckets` in die Objekt-Vereinigung auf. Die Liste ist aber ein
  **Array**. `_mergeObj` baut aus jedem Eingang ein `{}` mit den Schlüsseln 0, 1, 2… und gibt ein
  Objekt zurück. Danach hat `["80–110", …]` kein `.map` mehr, und die Eingabemaske zerbricht bei
  **jedem** Zeichnen — der Spielmodus wurde unbedienbar.
  **Zwei Reparaturen, nicht eine.** `approachBuckets` gehört nicht in die Objektliste — das behebt
  den Fall. Und **der Riegel sitzt jetzt in `_mergeObj` selbst**: Ist eine Seite ein Array, bleibt
  das Ergebnis ein Array. Wer künftig einen Bereich ergänzt, soll nicht wissen müssen, ob er Liste
  oder Objekt ist. Eine Reparatur, die nur den gemeldeten Fall behebt, wartet auf den nächsten.
  **Warum der Prüfstand es durchgelassen hat:** Er prüfte Verhalten **je Bereich** — und
  `approachBuckets` war nicht dabei. Die neue Gruppe prüft die **Form über den ganzen
  Datenbestand**: Was als Liste hineingeht, muss als Liste herauskommen, und die fünf Auswahllisten
  der Eingabemaske müssen danach benutzbar sein. Gegenprobe mit zurückgebautem Riegel: acht
  Prüfungen fallen, darunter wörtlich „approachBuckets: Liste → Objekt".
  **Und eine Lehre über den Prüfstand selbst:** Die erste Fassung der neuen Prüfung rief `.join()`
  direkt auf das Ergebnis. In der Gegenprobe **starb der ganze Prüfstand** an derselben
  TypeError-Klasse, statt einen roten Haken zu setzen. Ein Prüfstand, der abbricht, sagt weniger
  als einer, der meldet — jetzt wird erst die Form geprüft, dann der Inhalt.

- **v4.46.0 · 2026-08-24** — **Zwei ALT-Modus-Reste in der App gefunden, bevor der neue Worker
  hochgeladen wird.** Vor dem Ausrollen von Worker v2.9 systematisch geprüft, welche Stellen noch
  ohne `X-Path` schreiben. Zwei waren es — beide hätten still versagt.
  **(1) Der Bild-Abgleich** (`wikiImgPush`) schickte `{path, data}` im ALT-Format. Er hätte nach dem
  Hochladen 426 bekommen, und **„Bild-Sync fehlgeschlagen (426)" hätte niemand mit dem Worker in
  Verbindung gebracht.** Die Vereinigung geschieht ohnehin schon in der App; es fehlte nur die
  Kennung. Jetzt: erst `?sha=1&path=wissen-bilder.json`, dann schreiben mit dieser Basis.
  **(2) Der Haupt-Push hatte einen Rückfall auf den Alt-Worker** — bei HTTP 400 ein zweiter Versuch
  im ALT-Format. Der ist entfernt, und zwar aus zwei Gründen. Er greift nicht mehr, weil der neue
  Worker 426 statt 400 antwortet. **Schlimmer wäre gewesen, wenn er funktioniert hätte:** Der
  serverseitige Merge kannte weder Grabsteine noch Zeitstempel — gelöschte Runden wären wieder
  aufgetaucht, bearbeitete von ihrer älteren Fassung überschrieben worden. **Ein Rückfall, der
  stillschweigend schlechtere Regeln anwendet, ist kein Sicherheitsnetz, sondern eine Falle.**
  426 wird jetzt als das gemeldet, was es ist: „Worker erwartet den SHA-Modus — bitte App
  aktualisieren".
  **Prüfstand:** kein POST an den Worker ohne `X-Path`, und jeder nennt eine Basis-Kennung. Die
  erste Fassung dieser Prüfung hat übrigens einen Fehlalarm produziert, weil ihr Suchfenster über
  das Funktionsende hinausreichte — das Fenster ist jetzt eng genug, und der zweite Treffer war ein
  echter.

- **v4.45.0 · 2026-08-24** — **Der zweite Schreibweg ist geschlossen — Worker v2.9 liegt bei.**
  Statt die veraltete Worker-Kopie von `mergeDB` nachzuziehen, ist sie **ersatzlos entfernt**. Zwei
  Fassungen derselben Logik synchron zu halten ist teurer, als den zweiten Weg zu schließen — und
  die Regel „Äquivalenz prüfen" stand seit Worker v2.2 in dieser Doku und wurde **kein einziges Mal
  befolgt.** Eine Regel, die niemand einhält, ist keine Regel, sondern eine Beruhigung.
  **Was im Worker entfällt:** `mergeDB`, `_mergeArr`, `_mergeCourses`, `dataScore` und der ganze
  ALT-Modus samt Retry-Schleife. Rund 150 Zeilen weniger, und mit ihnen jede Möglichkeit, dass ein
  Merge nach veralteten Regeln stattfindet.
  **Wie er jetzt antwortet:** Ein POST ohne `X-Path`-Header bekommt **426 Upgrade Required** mit
  Begründung — der Wunsch ist verstanden, die Fassung des Clients ist das Problem. **Ein stiller
  Datenverlust ist die schlechteste aller Antworten**; eine klare Absage ist besser als ein Merge,
  der Löschungen rückgängig macht.
  **Für die Doku heißt das: Der Spiegelungs-Zwang ist aufgehoben.** `mergeDB` existiert nur noch
  einmal, nämlich in dieser Datei. Wer sie ändert, muss nichts mehr anderswo nachziehen — der
  Prüfstand kontrolliert jetzt das Gegenteil von vorher: dass **keine** zweite Fassung im Worker
  steht.
  **Zum Ausrollen:** `worker.js` in die Cloudflare-Konsole. Die Wear-OS-App wird ohnehin neu
  gebaut; sie muss von Anfang an den SHA-Modus sprechen (`X-Path` + `X-Base-Sha`, bei 409 frisch
  holen und lokal neu mergen).

- **v4.44.0 · 2026-08-24** — **Der ausgerollte Worker liegt jetzt im Klartext in der Doku — und die
  Abweichung ist beziffert statt vermutet.** In v4.30 hatte ich festgestellt, dass die Kopie in
  Abschnitt 28 veraltet ist, und bewusst nicht geraten, welchen Stand der Worker wirklich hat. Jetzt
  liegt er vor: **v2.8**, aus der Cloudflare-Konsole. Der Abschnitt stimmt damit wieder mit der
  Wirklichkeit überein.
  **Und er hinkt der App nach.** Verglichen mit `mergeDB` in dieser Datei fehlen dem Worker:
  · **Grabsteine** (`_mergeTomb`, `_tombFor`) — gelöschte Runden, Turniere, Tests und Plätze
  **erstehen wieder auf**
  · **Zeitstempel in `_mergeArr`** — statt „jüngerer gewinnt" gilt dort „längeres JSON gewinnt";
  eine **bearbeitete** Runde verliert damit gegen die ältere Fassung. Das ist genau der Fehler, den
  v2.41 in der App behoben hat.
  · `swingAnalyses` · `seasonGoals` · `tournaments` · `gear` · `tasks` · `periodization` ·
  `equipment` · `fitPlan` · `settings` · `lmTargets` — alle ohne Regel, also `Object.assign` mit
  „lokal gewinnt vollständig". In der App seit v4.41 behoben.
  · `MERGE_KEY` — die Schlüssel stehen inline und können auseinanderlaufen.
  **Im NEU-Modus wird `mergeDB` nie aufgerufen** — der Worker ist dort ein reiner SHA-Türsteher.
  Solange alle Geräte den NEU-Modus sprechen, ist der Code wirkungslos. **Die Gefahr liegt genau
  darin:** Er liefe erst beim Rückfall auf den ALT-Modus, also in einer Störung, wenn ohnehin etwas
  klemmt.
  **Empfehlung, und sie steht so in der Doku:** Den ALT-Modus **abschalten** statt nachzuziehen.
  Zwei Fassungen derselben Logik synchron zu halten ist auf Dauer teurer als den zweiten Weg zu
  schließen — und ein Client, der ihn braucht, ist ohnehin so alt, dass er aktualisiert gehört. Eine
  Warnung ohne Handlungsanweisung wird gelesen und weggeklickt; deshalb steht sie jetzt in drei
  Schritten da.

- **v4.43.0 · 2026-08-24** — **Bewertungsmatrix für die Schläger — und der Blocker, der ihr vorher
  im Weg stand.**
  **Zuerst der Blocker:** Gemessen an den echten Daten hieß **derselbe Schläger in drei Quellen
  verschieden** — „7 Iron" (Launch Monitor), „7 Iron 30°" (Schlägerliste), „7 Eisen G410" (Abschlag
  auf der Runde). Ebenso „Driver" gegen „Driver Aerojet", „3 Wood" gegen „3w Aerojet".
  **36 Namen bei 13 Schlägern.** Ursache: Die Muster in `_clubNormRoh` waren mit `$` **verankert** —
  ein angehängter Modellname ließ jeden Treffer scheitern, und der Name blieb unverändert stehen.
  Jede Auswertung je Schläger zerfiel damit in Bruchstücke. Jetzt wird am **Anfang** erkannt und der
  Rest verworfen: Der Schlägertyp steht vorn, das Modell hinten. **36 Namen → 15 Gruppen.**
  **Die Matrix stellt vier Fragen je Schläger**, alle aus vorhandenen Daten: **Lücke** zum Nachbarn
  (unter 8 m faktisch derselbe Schläger, über 18 m fehlt einer) · **Streuung** relativ zur Länge
  gegen den Durchschnitt des **eigenen** Bags · **Trefferqualität** (Smash gegen den Erwartungswert
  der Klasse) · **Einsatz** (wer in zehn Runden nie gezogen wurde, belegt einen der vierzehn Plätze).
  **An den echten Daten, die auffälligsten Befunde:** 29 m Lücke zwischen Driver und 3 Wood · nur
  7 m zwischen 7 Wood und 2 Iron („faktisch derselbe Schläger") · 23 m Lücke zwischen PW und GW ·
  das 7 Eisen streut mit 7,5 % deutlich mehr als der Bag-Schnitt von 5,2 %, obwohl es mit 54
  Messungen der bestbelegte ist · SW und LW mit Smash 1,11 und 1,04 gegen 1,20 üblich.
  **Verglichen wird gegen das eigene Bag, nicht gegen eine fremde Norm** — ein Schläger, der bei
  diesem Spieler auffällt, ist ein Befund; ein Vergleich mit Tour-Werten wäre nur eine Vorgabe.
  **Und ausdrücklich nicht bewertet wird, ob ein Schläger „zum Schwung passt"** im Sinne von Schaft,
  Lie-Winkel oder Kopfform. Dafür braucht es ein Fitting mit Kamera und Impact-Tape; aus Carry und
  Streuung lässt sich das nicht ableiten, und **eine Zahl, die so täte, wäre schlimmer als keine.**

- **v4.42.0 · 2026-08-24** — **„Die alten Sammeleinträge lassen sich nicht entfernen." — richtig,
  und schuld war meine Reparatur von gestern.** `_mergeObj` (v4.41) ließ „gefüllt schlägt leer"
  gelten, **ohne aufs Alter zu sehen**. Wer ein Feld leerte, bekam es beim nächsten Abgleich aus dem
  Repo zurück — die andere Seite hatte ja noch Inhalt. Genau die Fehlerklasse, vor der v1.74 und
  v2.84 warnen: **Ein Merge kann eine Löschung nicht ausdrücken, solange er nur Inhalte
  vergleicht.** Ich habe ein Sync-Problem behoben und ein zweites eingebaut.
  **Zwei Ergänzungen an `_mergeObj`:** (1) **Zeitstempel schlägt Inhalt** — tragen beide Seiten
  einen, entscheidet der jüngere, auch wenn er auf „leer" steht. Ohne Stempel bleibt es bei
  „gefüllt schlägt leer", damit Altbestände nichts verlieren. (2) **Kein Eintrag ist etwas anderes
  als ein leerer Eintrag** — was eine Seite gar nicht kennt, darf sie nicht löschen können.
  `equipSet` stempelt jetzt bei jeder Änderung.
  **Und die Sammelzeilen sind weg**, wie gewünscht: `equipAltRaeumen()` entfernt `woods`, `irons`,
  `wedges` und `i2` beim Start einmalig. **Mit Stempel, nicht mit `delete`:** Ein gelöschter
  Schlüssel ist für den Abgleich schlicht „unbekannt", und Unbekanntes füllt die andere Seite wieder
  auf. Eine **leere Hülle mit Zeitstempel** sagt dagegen aus „hier stand etwas, und es wurde am
  soundsovielten entfernt" — und das reist. `DB.ui.equipAltGeraeumt` verhindert, dass ein später
  bewusst angelegter Eintrag beim nächsten Start erneut geleert wird.
  **Prüfstand:** geleert-und-jünger gewinnt in beide Richtungen, ohne Stempel gewinnt weiter der
  Inhalt, unbekannter Schlüssel wird übernommen, echte Schläger überleben das Räumen, zweiter Lauf
  tut nichts.

- **v4.41.0 · 2026-08-24** — **BUGFIX: 30 von 44 Datenbereichen hatten gar keine Merge-Regel.**
  Gemeldet: „Die Synchronisation der Schlägerdaten funktioniert nicht über verschiedene Geräte."
  Nachgezählt: `mergeDB` behandelte **14 Listen** ausdrücklich. Alle übrigen Bereiche fielen unter
  `Object.assign({},R,L)` — und dort **gewinnt der lokale Stand vollständig**. Betroffen unter
  anderem `equipment` (die Ausrüstung), `seasonGoals` (130 Einträge), `tournaments`,
  `periodization`, `fitPlan`, `settings`, `lmTargets`, `gear`, `tasks`.
  **Der Verlustweg ist derselbe wie bei `gpsShots` in v2.90:** Gerät A trägt einen Schläger ein und
  pusht — im Repo steht er. Gerät B zieht, `Object.assign` nimmt **sein eigenes** `equipment`, der
  Eintrag ist auf B nicht da. Beim nächsten Push von B ist er auch im Repo weg. **Endgültig, ohne
  Meldung.** Die Doku führte „`tournaments` und `seasonGoals` laufen über `Object.assign`" sogar als
  bewusste Entscheidung — sie war keine, sie war eine Lücke.
  **Neu:** Listen (`seasonGoals`, `tournaments`, `gear`, `tasks`) über `_mergeArr` mit eigenen
  Merge-Schlüsseln; `periodization` als Objekt mit zwei Listen darin; Einstellungs- und
  Ausrüstungsobjekte über `_mergeObj` **je Schlüssel vereinigt**.
  **Die Regel dabei:** Wer auf einer Seite etwas stehen hat und auf der anderen nichts, gewinnt —
  unabhängig davon, welche Seite lokal ist. Nur wenn **beide** denselben Schlüssel gefüllt haben,
  entscheidet der lokale Stand. Verschachtelte Objekte gehen eine Ebene tiefer, damit bei
  `equipment` ein Schläger überlebt, den nur ein Gerät kennt.
  **Warum nicht nach Zeitstempel:** Diese Objekte tragen keinen. Einen zu erfinden hieße raten; die
  Vereinigung kann nichts verlieren, sondern im echten Konflikt nur eine der beiden Fassungen
  wählen.
  **Zweiter, kleinerer Fund:** Die beiden Wege „gemessenen Median übernehmen" schrieben
  `obj.total` **ohne Zeitstempel**. Ohne Stempel fällt `_mergeArr` auf „der vollständigere Eintrag
  gewinnt" zurück — und eine geänderte Zahl macht den Eintrag nicht länger. Eine übernommene
  Messung konnte damit vom älteren Repo-Stand überschrieben werden. Der Handeditor stempelte
  korrekt, diese beiden nicht.
  **Prüfstand:** der gemeldete Fall end-to-end durch das echte `mergeDB` — Ausrüstung beider Geräte
  überlebt, Saisonziele beider Geräte überleben. Gegenprobe mit entfernten Regeln: fünf Prüfungen
  fallen.

- **v4.40.0 · 2026-08-24** — **„9 von 60 Löchern" — die Fairwayquote suchte an einem Feld, das es
  nicht gibt.** Die Frage war berechtigt: GIR lässt sich berechnen, die Daten liegen vor. An den
  echten Runden nachgezählt und zwei Ursachen gefunden, beide meine.
  **(1) `par` steht nicht am Loch.** Von 144 Löchern der letzten zehn Runden tragen **9** ein
  eigenes `par`-Feld — der Rest holt es aus der Platzdefinition über `sgEnrich(round)`. Diese
  Auswertung las roh. Der Rohzugriff war beim Erscheinen dieser Fassung bereits behoben; **besonders
  tückisch daran:** `sgEnrich` ergänzt die Runde **an Ort und Stelle**, weshalb die Auswertung
  richtig aussah, sobald vorher irgendeine andere Ansicht die Runde einmal angefasst hatte. Der
  Fehler zeigte sich nur beim ersten Aufruf — und im Prüfstand nur isoliert.
  **(2) Erfundene Feldnamen.** Das Abschlagergebnis steht in `h.tee` („Hit", „Links", „Mis-hit",
  „Lang"), der Grüntreffer in `h.apprMiss` („Grün getroffen"). Ich hatte auf ein `h.fw` geprüft, das
  nirgends existiert — deshalb meldete die Fairwayquote **„0 von 40 Löchern", obwohl 66 Abschläge
  erfasst sind.** Scrambling nutzt jetzt `apprMiss` und fällt nur dort, wo es fehlt, auf die
  gröbere Rechnung aus Score und Putts zurück.
  **Ergebnis an den echten Daten — 14 von 14 Größen rechnen:** GIR 38,9 % · Fairways 65,7 % ·
  Scrambling 18,2 % · Putts/Loch 2,00 · Putts nach GIR 2,25 · 1-Putt 11,1 % · 3-Putts 1,5 ·
  Doppelbogeys 1,5 · über Par +8,5 · Par 3 +0,88 · Par 5 +0,75 · SI 1–6 +1,33.
  **Zwei davon sind bemerkenswert:** **65,7 % Fairways** ist für Vorgabe 20 sehr gut und passt zum
  langen Spiel mit +3,2 Strokes Gained. **18,2 % Scrambling** dagegen liegt weit unter dem
  Phasenziel von 26 % — und Scrambling ist bei diesem Niveau der größte einzelne Scoring-Faktor.
  Das ist die erste belastbare Aussage darüber, wo die Schläge wirklich liegen.

- **v4.39.0 · 2026-08-24** — **BUGFIX: „9 von 60 Löchern" — die Daten lagen vor, nur nicht am Loch.**
  Der Einwand war richtig: GIR **lässt** sich berechnen. An den echten Runden nachgezählt: Von
  **145 Löchern** der letzten zehn Runden tragen **neun** ein `par`-Feld. Die Scorekarte speichert
  es nicht am Loch, weil es in der **Platzdefinition** steht — und genau dafür gibt es
  `sgEnrich(round)`, das `par`, `len` und `si` aus `activeHoles` nachträgt.
  **Die Zielauswertung benutzte es nicht** und las `h.par` roh. Jede Größe, die Par braucht — GIR,
  Doppelbogeys, über Par, Par-Klassen, SI 1–6 —, fiel damit auf die Handvoll Löcher zurück, bei
  denen jemand das Par von Hand eingetragen hatte. Die Meldung „Datenbasis zu dünn" war formal
  korrekt und inhaltlich irreführend: Nicht die Erfassung war lückenhaft, sondern der Zugriff.
  **Vorher elf von vierzehn Rundengrößen ohne Wert, jetzt alle vierzehn:**
  GIR 38,9 % · Scrambling 18,8 % · Putts je Loch 2,00 · Putts nach GIR 2,25 · 1-Putt 11,1 % ·
  3-Putts 1,50 · Doppelbogeys 1,50 · über Par 8,5 · Par 3 +0,88 · Par 5 +0,75 · SI 1–6 +1,33.
  Nur die **Fairwayquote** bleibt bei null — das Feld wird tatsächlich nie ausgefüllt.
  **Dieselbe Fehlerklasse wie zweimal zuvor:** `sgHole` vor der Einführung von `sgEnrich`, und
  Rohzugriff auf `geo.holes` statt `holeRef()` in der Geometrie (v3.94, v4.4). **Wer die Rohform
  liest, liest zu wenig** — und merkt es nicht, weil das Ergebnis plausibel aussieht.
  **Prüfstand:** geprüft wird die **Abdeckung**, nicht der Wert. Vier Testlöcher reichen nie für
  eine Aussage, aber sie zeigen, ob Löcher überhaupt gefunden werden. Die Gegenprobe ohne
  Platzdefinition liefert null gefundene Löcher — **der Unterschied zwischen beiden Läufen ist der
  Fehler.**

- **v4.38.0 · 2026-08-24** — **Driving Iron als eigene Kategorie.** Mit 17° steht es zwischen den
  Hölzern und den Eisen und gehört zu keinem von beiden: Es **ersetzt ein Holz**, wird aber **wie
  ein Eisen geschlagen**. Unter „Eisen" geführt wirkte es wie der Anfang eines Satzes, der bei 2
  beginnt — das tut er nicht, die Eisen fangen bei 4 an. Jetzt eigene Gruppe zwischen Hölzern und
  Eisen.
  **Und die Zwischenfassung geht nicht verloren:** v4.37 hatte es kurzzeitig als `i2` unter den
  Eisen. Wer dort schon etwas eingetragen hat, findet es unter „Alte Sammeleinträge" mit der Bitte,
  es ins Driving Iron zu übernehmen — statt dass es stillschweigend verschwindet. Dieselbe Regel
  wie einen Tag zuvor: **eine Umstellung darf fremde Eingaben nicht verschlucken**, auch nicht die
  aus der eigenen letzten Fassung.

- **v4.37.0 · 2026-08-24** — **Jeder Schläger bekommt eine eigene Zeile — Hybride entfernt.**
  Bis hierher gab es je **eine** Zeile für „Hölzer & Hybride", „Eisen" und „Wedges". Damit ließ sich
  nur der **Satz** eintragen, nicht der Schläger — und der Schläger ist die Einheit, die man
  wechselt. Wer ein Wedge tauscht oder ein Eisen umschäftet, konnte das nirgends festhalten, und die
  **„seit wann"-Spalte, die der eigentliche Wert dieser Liste ist**, galt dann pauschal für den
  ganzen Satz und war damit wertlos.
  **Neu, gruppiert:** Hölzer (3 · 5 · 7 Wood) · Eisen (2 · 4 · 5 · 6 · 7 · 8 · 9) · Wedges (PW ·
  GW · SW · LW), jeweils mit dem **Loft aus deinem Bag als Platzhalter** — Vorschlag, keine
  Vorgabe. Wer ein 4 Eisen statt eines 7 Woods spielt, trägt es dort ein.
  **Hybride entfernt**, weil keine im Bag sind. Eine Zeile, die dauerhaft leer bleibt, kostet bei
  jedem Blick Aufmerksamkeit.
  **Kein Datenverlust bei der Umstellung.** Die drei abgelösten Sammelzeilen bleiben unter „Alte
  Sammeleinträge" sichtbar, **solange sie Text tragen** — mit dem Hinweis, den Inhalt auf die
  Einzelzeilen zu verteilen. Danach verschwinden sie von selbst. **Stilles Löschen fremder Eingaben
  wäre das Schlimmste, was eine Umstellung tun kann**, und eine Migration, die rät, wohin ein
  Sammeleintrag gehört, rät falsch.
  **Prüfstand:** keine doppelten Schlüssel (ein doppelter überschriebe beim Speichern
  stillschweigend den anderen), jede Gruppen-Überschrift zeigt auf ein vorhandenes Feld, und die
  alten Einträge erscheinen nur bei Inhalt.

- **v4.36.0 · 2026-08-23** — **Die Spielziele stehen jetzt in den vier Phasen — mit Werten für das
  jeweilige HCP-Niveau.** Eine Sammelphase „Spielziele" fragte „was willst du irgendwann"; die
  Phasen fragen „was ist bis Ende dieser Phase dran". Das ist der Unterschied zwischen einer Liste
  und einem Plan.
  **23 Messgrößen × 4 Phasen = 92 Spielziele**, gestaffelt nach den HCP-Zielen der Makrozyklen
  (18 · 10 · 3 · 0). Beispiele: GIR 32 → 42 → 55 → 62 % · Fairways 45 → 52 → 58 → 62 % ·
  Scrambling 26 → 34 → 45 → 52 % · 3-Putts 2,2 → 1,4 → 0,9 → 0,7 je Runde · Doppelbogeys
  3,0 → 1,5 → 0,5 → 0,2 · Strafschläge 1,0 → 0,6 → 0,3 → 0,2 · Schläge über Par 20 → 12 → 5 → 2.
  **Der Startwert einer Phase ist das Ziel der vorigen** — sonst entsteht eine Lücke oder eine
  Überschneidung im Fahrplan. Der Prüfstand hält beides fest: lückenlose Kette und **monotone
  Steigerung in die richtige Richtung**.
  **Dabei zwei Fehler in den alten Zielen gefunden:** „Swing Speed Driver ≥ 103 mph" in Phase 3
  gegen „≥ 102" in Phase 4 — eine Phase, die weniger fordert als die vorige, ist ein Tippfehler mit
  dem Anschein von Absicht. Und „Eisen Streubreite ≤ 18 m / ≤ 12 m" war gegen eine andere
  Definition gesetzt: An den echten Daten liegt die Streuung des 5 Eisen bereits bei ±9,9 m, das
  Ziel war also von Anfang an erfüllt. Beide Reihen sind durch die neue, gestaffelte ersetzt.
  **Und ein Befund, der leicht unbemerkt geblieben wäre:** Der **Anstellwinkel** hat je nach
  Schläger die entgegengesetzte Richtung — beim Eisen ist negativer besser (abwärts treffen), beim
  Driver positiver (aufwärts). Ein einziges Feld mit einer Richtung wäre für die eine Hälfte der
  Schläger zwangsläufig falsch herum, und **ein Ziel, das falsch herum zählt, belohnt genau den
  Fehler, den es abstellen soll.** Jetzt `attackIron` und `attackDriver` — zwei Messgrößen auf
  demselben Rohwert, mit gegenläufiger Richtung.

- **v4.35.0 · 2026-08-23** — **„Ich finde diese Punkte nicht unter Planung." — zu Recht: Sie
  konnten dort gar nicht ankommen.** `SEED.seasonGoals` galt ausschließlich für eine **leere**
  Datenbank. Wer die App schon benutzt, hat seine Ziele aus dem Bestand — und bekam von allem, was
  seit v4.32 an ihnen gebaut wurde, **nichts**: keine Zieldaten (also keine Ampel und keine
  Rückkopplung), keine Anbindung an Daten, keine der neuen Spielziele.
  Für `testDefs` gibt es `ensureSeedTests()` seit v3.10. Für die Ziele gab es nichts — drei
  Fassungen lang wurde an etwas gearbeitet, das den Benutzer nicht erreichte. **Dieselbe Klasse wie
  zweimal zuvor an diesem Tag** (`goalPlan` ohne Zieldatum, Konzept E hinter dem Deckel):
  eingebaut und wirkungslos, und wirkungslos ist schlimmer als fehlend, weil man sich darauf
  verlässt.
  **An den echten Daten nachgerechnet:** Der Bestand hatte **33 Ziele, davon 33 ohne Zieldatum** und
  8 ohne Datenquelle. Nach der Migration: **57 Ziele, 0 ohne Zieldatum, 1 ohne Datenquelle.** Der
  zweite Lauf ändert nichts.
  **Zwei Dinge, strikt getrennt:** (1) Fehlende Ziele ergänzen — aber nur, wenn sie nie da waren;
  `DB.ui.seedGoalsAdded` verhindert, dass ein gelöschtes bei jedem Start wiederkehrt. (2) Vorhandene
  Ziele **nur um fehlende Felder** anreichern. `start`, `target`, `note`, `phase` und `label`
  bleiben unangetastet — das sind Eingaben des Spielers, und **eine Nachzieh-Funktion, die eigene
  Werte überschreibt, ist ein Datenverlust mit Anlauf.** Der Prüfstand hält genau das fest: eigener
  Startwert, eigener Zielwert, eigene Notiz bleiben; nur das Zieldatum kommt dazu.
  Erkannt wird über **Phase + Beschriftung**, nicht über die `id` — Seed-Ziele haben keine, sie
  bekommen sie erst beim ersten Laden.

- **v4.34.0 · 2026-08-23** — **Zwölf Ziele hingen an Handpflege, obwohl die Daten dalagen — und
  elf neue Spielgrößen.**
  **Einheiten zuerst geprüft**, an den echten Launch-Monitor-Daten, weil ein Ziel wie „Eisen
  Streubreite ≤ 18 m" sonst gegen die falsche Einheit rechnet — genau der Fehler, der in v4.24 die
  Erwartungstabellen betroffen hat. Befund: **Geschwindigkeiten in mph, Längen in Metern**, gemischt
  im selben Datensatz, aber konsistent. Belegt über drei Schläger (7 Eisen: Ball 112,6 mph → 149,8;
  Gap Wedge: 76,8 → 85,8; Lob Wedge: 65,8 → 68,2 — alle als Meter plausibel, als Yards deutlich zu
  kurz). **Die Einheiten stehen jetzt in den Beschriftungen** — eine Zahl ohne Einheit ist der
  Anfang jedes Einheitenfehlers.
  **Angebunden:** Eisen Streubreite → `lm.streuung` mit Schlägerfilter · Swing Speed Driver →
  `lm.clubSpeed` · 9-Shot, Consecutive FW, Distanzkontrolle Wedge → die vorhandenen Tests ·
  GolfForever Streak → neue Quelle `fit`. Von 57 Zielen hängt jetzt genau **eines** noch an
  Handpflege (Pelz Quote — nicht sauber ableitbar).
  **Neue Messgrößen** (`runde`): Fairwayquote · Scrambling-Quote · Putts nach GIR · 1-Putt-Quote ·
  Doppelbogey-freie Runden · Ø über Par auf Par 3/4/5 · Ø über Par auf SI 1–6. (`lm`):
  Ballgeschwindigkeit · Smash-Faktor · Anstellwinkel. Neue Quelle `fit`: Trainingsserie und
  Einheiten je Woche.
  **Zwei Feinheiten, die leicht falsch werden:** Die **Fairwayquote lässt Par 3 aus** — sonst sinkt
  sie mit jedem Loch, auf dem es gar kein Fairway anzuspielen gibt. Und die **Trainingsserie zählt
  ab heute rückwärts**: Eine Serie, die vor drei Wochen endete, ist keine laufende Serie.
  **Abdeckung statt Rauschen.** Scrambling, Fairwayquote und Putts-nach-GIR hängen an Feldern, die
  real nur zu 30–55 % gefüllt sind. Jede Rundengröße hat deshalb eine Mindestabdeckung; darunter
  gibt es **keine Zahl**, sondern „Datenbasis zu dünn (9 von 60 Löchern)". Dieselbe Haltung wie bei
  `sgWarnHtml` — lieber keine Zahl als eine, der man nicht trauen kann.
  **An den echten Daten nachgerechnet:** Von 19 Spielzielen liefern 8 einen Wert, 11 melden zu
  dünne Datenbasis. Das ist kein Mangel der Ziele, sondern die ehrliche Auskunft über die Erfassung
  — und es benennt genau die Felder, deren Pflege sich lohnt.

- **v4.33.0 · 2026-08-23** — **Die Saisonziele hatten kein Zieldatum — damit war der ganze Fahrplan
  aus v4.32 wirkungslos.** Nachgeprüft, was die Konzepte A–C und E tatsächlich leisten. Ergebnis:
  eingebaut, aber ohne Wirkung — und das ist schlimmer als nicht eingebaut, weil man sich darauf
  verlässt.
  **(C) Kein einziges der 46 Ziele hatte `dateTo`.** `goalPlan()` gibt ohne Zieldatum `null` zurück
  — also keine Ampel, kein „hinterher", keine Rückkopplung. Jetzt hat jedes Ziel ein Zeitfenster,
  abgeleitet aus den Makrozyklen: Phase 1 bis 31.08.26, Phase 2 bis 31.03.27, Phase 3 bis 30.09.27,
  Phase 4 bis 31.03.28, Spielziele bis zum Saisonende 31.10.26. Ergebnis über alle Ziele:
  **19 grün · 2 gelb · 11 rot · 14 grau** (grau = Ist-Wert fehlt noch).
  **Nebenbei zwei Datenfehler:** Die Phasennamen waren gemischt geschrieben („Phase 1" gegen
  „PHASE 3") — die Gruppierung zerfiel damit in Gruppen, die gleich heißen sollten. Und
  `hcpTarget` stand als „HCP HCP 18" in allen vier Makrozyklen.
  **(E) Die Rückkopplung kam nie durch.** `trainingsEmpfehlung` sortierte nach Rang und schnitt bei
  vier Einträgen ab — bei drei Tests unter Zielniveau blieb für die Saisonziele (Rang 4) **nie ein
  Platz**. Und selbst ohne Ziele war eine Liste, die zu drei Vierteln aus „Test unter Zielniveau"
  besteht, keine Empfehlung, sondern eine Aufzählung. Jetzt **höchstens zwei je Quelle**, dann fünf
  Einträge. Die Rangfolge bleibt: **Messung vor Absicht** — ein Saisonziel darf nie vor Strokes
  Gained stehen, sonst trainiert man, was man sich vorgenommen hat, statt was fehlt.
  **Die Vielzahl der Ziele bleibt ausdrücklich bestehen** (46 statt der drei, die ich vorgeschlagen
  hatte). Sie macht sichtbar, was insgesamt zu erreichen ist; die Ampel sorgt dafür, dass daraus
  trotzdem eine Rangfolge entsteht, ohne dass man kürzen muss.
  **Und die Spielziele stehen jetzt neben den Übungszielen:** Strafschläge ≤ 0,5 · GIR ≥ 45 % ·
  3-Putts ≤ 1,5 · Doppelbogeys ≤ 2 · SG Putten ≥ −1,0 · Putts je Loch ≤ 1,90 · Index ≤ 18,0 ·
  Driver-Streuung ≤ 12 m — aus vier verschiedenen Datenquellen (`sg`, `runde`, `hcp`, `lm`).
  Der ursprüngliche Bestand war ausschließlich Drill-Ergebnisse, und **man optimiert, was man
  misst.**

- **v4.32.0 · 2026-08-23** — **Saisonziele: Spielziele, robuster Ist-Wert, Ampel — und Rückkopplung
  ins Training.** Vier Befunde aus der Durchsicht, alle behoben.
  **(A) Der Ist-Wert nahm den letzten Testwert, ungefiltert.** Ein schlechter Tag ließ den Balken
  einbrechen, und ein Wert von vor elf Monaten galt als „aktuell". Jetzt der **Median der letzten
  drei** Versuche — die Mitte, nicht der Mittelwert — und das Alter steht dabei; über 90 Tage wird
  „veraltet" angezeigt. Man steuert sonst nach einer Zahl, die nichts mehr über heute sagt.
  **(B) Ziele konnten nur an Tests binden** — damit waren alle acht Ziele **Übungsziele**, kein
  einziges ein Spielziel. Das ist der klassische Fehler: **Man optimiert, was man misst**, und wird
  gut im Drill statt auf der Bahn. Die App hat sogar eine „Transfer-Lücke"-Analyse, die den Effekt
  zeigt; die Ziele ignorierten sie. Jetzt binden Ziele an **fünf Quellen**: Test · Strokes Gained ·
  Rundenstatistik · Vorgabe · Launch Monitor.
  **Die Richtung steht bei der Messgröße, nicht beim Ziel.** „Weniger Putts ist besser" ist eine
  Eigenschaft der Größe; wer das dem Nutzer überlässt, bekommt irgendwann ein Ziel, das falsch
  herum zählt.
  **(C) Es gab kein Zieldatum** — „3 von 8 erreicht" sagte nicht, ob man im Plan liegt. Jetzt Start-
  und Zieldatum, daraus Soll-Fortschritt gegen Ist und eine **Ampel**: im Plan · knapp hinterher ·
  deutlich hinterher · Frist abgelaufen. **Ohne Datum bleibt es bei „kein Zieldatum"** statt eine
  Ampel zu erfinden. Und **Rückschritt ist jetzt sichtbar**: `Math.max(0,…)` klemmte alles unter dem
  Startwert auf 0 % — nicht unterscheidbar von „gerade angefangen".
  **(E) Rückkopplung ins Training.** `trainingsEmpfehlung()` las Strokes Gained, Putt-Diagnose und
  Tests — die eigenen Ziele ignorierte sie. Jetzt kommen **rote** Ziele dazu, aber nur mit
  Zieldatum (ohne Fahrplan gibt es kein „hinterher", nur einen Wunsch) und auf **Rang 4**: Ein
  selbst gesetztes Ziel ist eine Absicht, was auf der Runde Schläge kostet eine Messung. Wo beides
  zeigt, steht die Messung vorn — sonst trainiert man, was man sich vorgenommen hat, statt was
  fehlt. Genau der Fehler, den die Ziele selbst hatten.
  **Acht neue Spielziele** in einer eigenen Phase, aus den echten Daten abgeleitet: Strafschläge
  ≤ 0,5 je Runde (der größte belastbare Posten, aktuell −1,00) · GIR ≥ 45 % · 3-Putts ≤ 1,5 ·
  Doppelbogeys ≤ 2 · SG Putten ≥ −1,0 · Putts je Loch ≤ 1,90 · Index ≤ 18,0 · Driver-Streuung
  ≤ 12 m. Zwei davon sind bewusst **Gegenproben**: „Putts je Loch" zählt Fakten, „SG Putten" rechnet
  Erwartungswerte — weichen sie auseinander, liegt es an der Erfassung, nicht am Putten.
  **Keine Obergrenze für die Anzahl der Ziele** — bewusst so gelassen. Aus Trainersicht sind mehr
  als drei gleichzeitige Ziele keine Ziele, aber das ist eine Entscheidung des Spielers, und viele
  Ziele machen sichtbar, was insgesamt ansteht.
  **Alte Ziele bleiben unberührt:** `testKey` wird weiter gelesen, die Werte sind nicht angetastet.

- **v4.31.0 · 2026-08-23** — **Neue Kategorie „Zu Hause": sechs Tests für Puttmatte und Launch
  Monitor — bewusst ohne jede Auswertung.**
  **Wellputt-Matte (4,0 m):** *Tor aus 2 m* (Startrichtung isoliert — wer das Tor verfehlt, hat
  kein Leseproblem, sondern ein Flächenproblem) · *Distanzkontrolle 4 m* in die GOOD ZONE
  (Drei-Putts entstehen an der Länge, nicht an der Richtung) · *Startlinie über 4 m* in Zentimetern
  (das Tor zeigt **ob** die Fläche stimmt, dieser Test **wie weit** sie danebensteht).
  **Garmin R10 / Awesome Golf:** *Anstellwinkel 7 Eisen* im Band −5° bis −1° — die wiederkehrende
  Schwäche aus den Schwunganalysen, im Netz messbar, ohne den Ballflug zu sehen · *Teilschwung-
  Treffsicherheit* über drei angesagte Weiten, ±5 m — misst genau die Lücke zwischen PW und GW, die
  im Golfexperten-Durchgang aufgefallen ist, und beantwortet nebenbei, ob ein anderes Wedge die
  bessere Lösung wäre · *Startrichtung 7 Eisen* als Streuung, nicht als Mittelwert (wer konstant 3°
  links startet, hat eine ausgezeichnete Fläche und ein Ausrichtungsproblem — etwas völlig anderes).
  Alle sechs im gewohnten Format mit WOFÜR · AUFBAU · ABLAUF · ZÄHLUNG · FEHLERQUELLE, jeweils mit
  der Fehlerquelle, die den Test am ehesten wertlos macht: das Tor zu breit stellen, von weicher
  Matte schlagen, die Zielweite nachträglich anpassen, den Mittelwert mit der Streuung verwechseln.
  **`keineAuswertung:true` ist kein Nebenschalter, sondern eine Eigenschaft der Messung.** Diese
  Tests messen etwas Echtes, aber unter Bedingungen, die mit dem Platz nichts zu tun haben — kein
  Wind, kein Gefälle, ein Teppich statt eines Grüns, ein Netz statt einer Bahn.
  **Nicht zu verwechseln mit `weight`:** Ohne Gewicht zählt ein Test nicht für die Spielstärke,
  taucht aber weiterhin in Erinnerungen und Vorschlägen auf. Heim-Tests sollen **gar nicht erst**
  auftauchen. `testZaehltNicht()` fragt es, `testDefsAusgewertet()` liefert die bereinigte Liste,
  und alle fünf Auswertungspfade benutzen sie: Spielstärke, Benchmark-Übersicht,
  Trainingsempfehlung, „fällige Tests" und Testvorschläge. **An einer Stelle gefragt, überall
  geachtet** — sonst rutscht es beim nächsten Pfad wieder mit hinein, wie es bei den
  Beweglichkeitstests fast passiert wäre. Der Prüfstand hält jeden Pfad einzeln fest.

- **v4.30.0 · 2026-08-23** — **Die Worker-Kopie in der Doku ist veraltet — und sie ist die
  gefährlichste Sorte veralteter Doku.** Abschnitt 28 hält eine Kopie des Worker-Codes und
  bezeichnet sich selbst als „Quelle der Wahrheit für die Frage, was macht der Worker gerade".
  **Regel 5 dort verlangt ausdrücklich: App-Fassung und Worker-Fassung müssen dieselben Ergebnisse
  liefern.** Ein Zeilenvergleich zeigt: Sie tun es nicht — App 82 Zeilen, Kopie 68, und es fehlen
  drei Dinge:
  · **Grabsteine (`_mergeTomb`, `_tombFor`) vollständig** — ohne sie **erstehen gelöschte Einträge
  wieder auf**. Genau das, was v3.68 und v3.91 mühsam repariert haben.
  · **`swingAnalyses`** wird gar nicht abgeglichen.
  · **`MERGE_KEY`** fehlt; noch die alten Inline-Schlüssel, und `_mergeCourses` mit zwei statt drei
  Argumenten — also ohne Grabsteine für Plätze.
  **Warum das trotzdem niemandem aufgefallen ist:** Im NEU-Modus merged der Worker gar nicht, er
  ist ein reiner SHA-Türsteher. Der Code ist wirkungslos — **und genau darin liegt die Gefahr.**
  Er liefe erst, wenn man auf den ALT-Modus zurückfällt, also in einer Störung, wenn ohnehin etwas
  klemmt. Der schlechteste denkbare Zeitpunkt für wiederauferstehende Löschungen.
  **Bewusst NICHT stillschweigend nachgezogen.** Ich weiß nicht, welchen Stand der ausgerollte
  Worker hat. Die Kopie an die App anzugleichen würde die Abweichung unsichtbar machen, ohne sie zu
  beheben — und die nächste Frage „was macht der Worker gerade?" bekäme wieder eine Vermutung als
  Antwort. Genau davor warnt der Abschnitt selbst, weil es bei v2.84 schon einmal passiert ist.
  Erst den ausgerollten Stand prüfen, dann beide angleichen.
  **Der Prüfstand hält es fest, und zwar in BEIDE Richtungen:** Weicht die Kopie ab, muss die
  Warnung dastehen — mit dem, was fehlt, und mit der Folge, nicht nur mit dem Unterschied. Stimmen
  beide wieder überein, muss die Warnung **weg**. Sonst verrottet sie in die andere Richtung, und
  das ist derselbe Fehler noch einmal.

- **v4.29.0 · 2026-08-23** — **Die Lage verkürzte, aber sie streute nicht.** Dritter Durchgang aus
  Golfexperten-Sicht. `LAGE_FAKTOR` verkürzt seit je (Rough 0,90 · hohes Rough 0,80 · Sand 0,75) —
  **die Streuung blieb unverändert.** Ein Schlag aus 15 cm Rough hatte in der Rechnung dieselbe
  Präzision wie vom Bügelbrett, nur 10 % kürzer. Seit v4.0 verbreitert die **Standlage** beide
  Sigmas; die **Balllage** tat es nicht — dieselbe Überlegung, an der anderen Hälfte vergessen.
  **Wirkung:** Der Erholungsschlag sah zu gut aus. Wer aus dem Rough aufs Grün zielte, bekam
  dieselbe Trefferquote wie vom Fairway — und genau dort entstehen die Doppelbogeys.
  **Damit war es der vierte systematische Optimismus** nach ES-Tabelle (v4.24), fehlender
  Niveau-Skalierung (v4.25) und den Import-Dubletten (v4.27). Alle vier zeigten in dieselbe
  Richtung, und alle vier hatten dieselbe Ursache: **eine Zahl, die gesetzt statt gemessen war.**
  **Der Flyer erklärt die Asymmetrie.** Aus leichtem Rough kommt Gras zwischen Schlagfläche und
  Ball, der Spin fällt weg — der Ball kann **weiter** fliegen statt kürzer. Deshalb wächst dort die
  **Längs**streuung stärker (man weiß nicht, ob kurz oder lang). Aus hohem Rough und Sand gibt es
  keinen Flyer, dafür Richtungsverlust: dort wächst **quer** stärker. Der Prüfstand hält beide
  Richtungen fest — ein Vorzeichenfehler würde die Empfehlung genau falsch herum verschieben.
  **Die Zahlen sind Vorlieben, keine Messung**, wie bei `STRAT.HANG`: aus leichtem Rough etwa die
  Hälfte mehr Längsstreuung, aus hohem Rough und Sand rund zwei Drittel mehr Seitenstreuung. Sobald
  genug eigene Schläge **aus dem Rough** aufgezeichnet sind, gehören sie durch gemessene ersetzt.
  **Beim Durchgang bestätigt, nicht geändert:** `trainingsEmpfehlung()` priorisiert bereits Strokes
  Gained vor Testergebnissen — meine frühere Kritik an `focusList` war falsch, die Rangfolge steht
  eine Ebene höher und ist richtig.

- **v4.28.0 · 2026-08-23** — **BUGFIX: `A_sd` statt `A_std` — und der Riegel gegen diese ganze
  Fehlerklasse.** Die neue „Alle"-Ansicht aus v4.26 rief eine Funktion, die es nicht gibt; die
  Standardabweichung heißt `A_std`. Ergebnis: `ReferenceError` beim Öffnen, viermal im Protokoll.
  **Das ist heute der vierte Fehler derselben Art** — `lineDeg` (v3.91), `zielName` (v4.9),
  `satOn()` im Template-Literal (v3.96), jetzt `A_sd`. Alle vier hatten dasselbe gemeinsam: **Der
  Prüfstand sucht Muster im Quelltext, und ein falscher Name ist als Muster nicht von einem
  richtigen zu unterscheiden.** Gefunden wurden sie durch Ausführen — zweimal vom Platz-Durchlauf,
  zweimal erst auf dem Gerät.
  **Neue Prüfgruppe: jede Ansicht wird gerendert.** Alle `render*`-Funktionen werden mit
  Beispieldaten aufgerufen; eine Ausnahme lässt den Prüfstand fallen. Das ersetzt keine inhaltliche
  Prüfung, fängt aber die ganze Klasse „Name existiert nicht" auf einen Schlag.
  **Und dabei zum dritten Mal in dieselbe Falle getappt:** Der erste Rendertest lief durch, obwohl
  der Fehler noch drin war — `lmSelClub` ist ein `let` auf oberster Ebene, und `ctx.lmSelClub = …`
  setzt im Sandkasten nur eine **Namensvetterin**, während das Modul weiter seine eigene liest. Der
  Zweig wurde nie erreicht. Genau wie bei `DGM` (v3.99) und `WEATHER` (v4.16). `lmSetClub(c)` löst
  es; die Gegenprobe meldet jetzt sauber „`__alle__`: A_sd is not defined".
  **Merksatz in der Doku:** Jede Modulvariable, die eine **Ansicht** steuert, braucht einen Setzer —
  sonst ist der Zweig dahinter nicht prüfbar, und was nicht prüfbar ist, ist unbewacht.

- **v4.27.0 · 2026-08-23** — **Dubletten im Bestand werden gefunden und zum Löschen angeboten.**
  Der Schutz aus v4.26 greift beim Import — was vorher doppelt hereinkam, lag weiterhin da und
  verzerrte weiter die Streuung.
  **An den echten Daten geprüft: 72 von 245 Schlägen liegen doppelt.**
  · `2026-07-29 „ags-shots-2026-07-24"` — 7 von 7, vollständige Kopie von `2026-07-28`
  · `2026-07-29 „ags-shots-2026-07-25"` — 13 von 13, ebenso
  · `2026-07-29 „ags-shots-2026-07-27"` — 21 von 21, ebenso
  · `2026-08-23 „ags-shots-2026-08-23 (1)"` — 31 von 60, **teilweise**
  Die drei ersten sind ein Export, der einen Tag später noch einmal eingelesen wurde. Der vierte
  ist der interessante Fall: Er enthält 29 **neue** Schläge und darf deshalb nicht gelöscht werden.
  **Gezählt wird je Sitzung, nicht je Schlag.** Ein einzelner doppelter Schlag kann Zufall sein —
  zwei Bälle mit identischen Messwerten auf eine Nachkommastelle sind unwahrscheinlich, aber nicht
  unmöglich. Eine Sitzung, deren Schläge **alle** schon anderswo stehen, ist eindeutig. Die ältere
  gewinnt; sie ist die ursprüngliche.
  **Vollständige und teilweise Dubletten werden getrennt behandelt.** Nur vollständige bekommen
  einen Löschknopf; teilweise werden gezeigt und verlinkt („Ansehen"), **aber nicht zum Löschen
  angeboten** — man würde die neuen Schläge mitentfernen. Das ist der Unterschied zwischen einem
  hilfreichen und einem gefährlichen Vorschlag.
  **Nichts geschieht von selbst.** Vor dem Löschen wird erneut geprüft, ob die Sitzung noch
  vollständig doppelt ist (zwischen Anzeige und Klick kann die Quelle verschwunden sein), es gibt
  eine Rückfrage mit Zahlen, und gelöscht wird mit Grabstein, damit es auf anderen Geräten ankommt.
  Ein Automatismus, der Messdaten entfernt, wäre die Sorte Hilfsbereitschaft, die man später
  bereut.

- **v4.26.0 · 2026-08-23** — **Schlägerauswahl „Alle" · und derselbe Schlag zählt nur noch einmal.**
  **(1) Doppelter Import.** Gemeldet: Wird dieselbe CSV ein zweites Mal eingelesen — versehentlich,
  oder weil der Export den ganzen Tag enthält statt nur die neue Sitzung —, legte die App eine
  **zweite Sitzung mit denselben Schlägen** an. Danach zählte jeder Schlag doppelt. Die Mittelwerte
  bleiben dabei gleich, **die Streuung aber wird zu klein**: Dieselben Werte doppelt gesehen lassen
  einen Schläger künstlich sicher aussehen — und die Streuung entscheidet über jede Empfehlung.
  Dazu log die Schlagzahl, und der Sitzungsverlauf zeigte zwei Punkte, wo einer gehört.
  `lmShotKey(x)` bildet jetzt einen Fingerabdruck aus Schläger und den Messwerten auf eine
  Nachkommastelle. **Ausdrücklich ohne Datum** — derselbe Schlag darf nicht deshalb als neu gelten,
  weil die Datei an einem anderen Tag importiert wurde. **Und ausdrücklich nicht der Zeitstempel:**
  Den liefert nicht jeder Export, und er wird beim erneuten Speichern gern neu gesetzt; der Abdruck
  fragt danach, *was* gemessen wurde.
  `lmNurNeue()` entdoppelt auch **innerhalb** der Datei. Sind alle Schläge schon da, entsteht keine
  leere Sitzung — die wäre im Verlauf ein Punkt ohne Inhalt. Die Meldung nennt die übersprungenen
  Schläge, sonst sieht ein zweiter Import wie ein Fehlschlag aus.
  **(2) Schlägerauswahl „Alle".** Bisher ließ sich nur ein Schläger ansehen. Für die Fragen, die man
  tatsächlich hat — stimmen die Abstände, wo klafft eine Lücke, welcher Schläger streut am meisten
  — braucht man den Blick über alle. Die Ansicht zeigt je Schläger eine Zeile mit Carry, Streuung
  und dem **Abstand zum nächstlängeren**: unter 8 m sind zwei Schläger faktisch einer, über 18 m
  fehlt einer dazwischen, beides ist hervorgehoben.
  Damit steht die Gapping-Analyse zur Verfügung, die im Golfexperten-Durchgang gefehlt hat — an
  Lars' Bag: 23 m zwischen PW und GW, während 6i/7i nur 6 m auseinanderliegen.
  **Keine gemittelte „Streuung über alle Schläger":** Das wäre eine Zahl ohne Bedeutung. Für
  Anstellwinkel, Spin und Empfehlungen bleibt der einzelne Schläger zuständig.

- **v4.25.0 · 2026-08-22** — **Die Streuung war Tour-Niveau, die Spielvorgabe ohne Allowance.**
  Zweiter Durchgang aus Golfexperten-Sicht, zwei Befunde an den Grundlagen.
  **(1) `sigmaFor` skalierte nicht mit dem Spielniveau.** Die Anteile waren fest: Eisen 4,5 %
  Seitenstreuung — das ist **Tour-Niveau** (veröffentlicht: Tour ~4 %, Handicap 20 ~6–7 %; Driver
  Tour ~5 %, HCP 20 ~8–10 %). **Alles andere in der App skaliert mit der Spielstärke**, `esOffset`
  ausdrücklich; die Streuung war die einzige Größe, die es nicht tat — und ausgerechnet sie
  entscheidet über Trefferquoten, Ovalgröße und damit über die Empfehlung.
  **Wirkung:** „FW 68 %" und „Grün 33 %" fielen zu gut aus, die Ovale waren zu klein, und weil enge
  Streuung den langen Schläger sicherer aussehen lässt, verschob sich die Empfehlung nach aggressiv.
  **Dieselbe Richtung wie der Tabellenfehler aus v4.24** — zwei unabhängige Optimismen, die sich
  addierten.
  Jetzt an zwei veröffentlichten Ankern kalibriert: bei Spielstärke 0 bleibt die Grundtabelle, bei
  20 ergeben sich 9,5 % Driver und 6,1 % Eisen. Gedeckelt bei 36. **Nur der Heuristik-Pfad
  skaliert** — gemessene Werte (`n>=20`) bleiben, was sie sind; eine Messung enthält die
  Spielstärke bereits, ein Zuschlag darauf wäre Doppelzählung.
  **(2) Spielvorgabe jetzt mit 95 % Handicap-Allowance — immer.** `HI × Slope/113 + (CR − Par)` ist
  die Course Handicap; für Einzel-Zählspiel und Stableford sieht die Allowance 95 % vor. Bis v4.24
  rechnete die App mit 100 %: im Turnier bis zu ein Vorgabeschlag zu viel, und die angezeigten
  Stableford-Punkte lagen über der Karte. Für Lars konkret: **24 → 23**.
  **Bewusst ohne Kippschalter.** Ein Schalter „Wettspiel/Privatrunde" wäre technisch richtiger und
  praktisch schlechter — man vergisst ihn, und zwar zuverlässig in die falsche Richtung, nämlich im
  Turnier. Wer auf der Privatrunde mit der strengeren Zahl übt, erlebt im Wettspiel keine
  Überraschung; umgekehrt schon. Die Anzeige weist es aus: „SPV 23 (95 % von 24)".
  **Reihenfolge zählt:** erst die Course Handicap runden, dann 95 %, dann wieder runden. Am Rand
  macht die Reihenfolge einen ganzen Schlag aus.
  **Prüfstand:** Anker gegen die veröffentlichten Bänder statt gegen sich selbst, Monotonie über
  alle Spielstärken, Deckelung, und die Probe, dass gemessene Streuung **nicht** mitskaliert.

- **v4.24.0 · 2026-08-22** — **Die Putt-Tabelle stand in Fuß, beschriftet war sie in Metern.**
  Prüfung der Rechengrundlagen gegen die veröffentlichte Scratch-Grundlinie (Broadie):
  `ES_BASE.green` entsprach Fuß-Werten mit einem Umrechnungsfaktor von etwa **2 statt 3,28**. Ein
  **10-m-Putt wurde mit der Erwartung eines 6-m-Putts bewertet** — durchgehend 0,16 bis 0,18 zu
  niedrig ab 4 m.
  **Die Wirkung ist größer, als die Zahl aussieht.** Strokes Gained Putten = ES(Start) − ES(Ende)
  − 1. Ist ES zu niedrig, fällt **jedes Zweiputten** schlechter aus: Zwei Putts aus 10 m ergaben
  −0,13 statt korrekt +0,03. Das hat das Putten systematisch schlechtgerechnet.
  **An den echten Runden nachgerechnet** (letzte 10): Putten von **−3,84 auf −2,22** — eineinhalb
  Schläge je Runde, die nie am Putter lagen, sondern an der Tabelle. Der Rest der gestrigen
  Diagnose bleibt gültig: Die Erfassungsschieflage (kurze Putts werden seltener eingetragen)
  erklärt den verbleibenden Teil, und die Warnung dazu steht seit v4.19 über den Zahlen.
  **Fairway, Rough und Sand** stimmen im kurzen Bereich (Abweichung ≤ 0,03), wurden aber ab etwa
  180 m zu optimistisch — bei 240 m um 0,15. Dort waren Yard-Stützstellen als Meter übernommen.
  **Wirkung:** Lange Schläge sahen billiger aus, als sie sind; das begünstigte systematisch Driver
  und lange Layups gegenüber dem sicheren Schläger. `ES_BASE.tee` stimmte und bleibt unverändert.
  **Was ausdrücklich NICHT geändert wurde:** der kurze Bereich unter 150 m. Er war richtig, und
  eine Korrektur „der Ordnung halber" hätte funktionierende Zahlen bewegt.
  **Wichtig beim Vergleich mit früher:** Alle historischen SG-Werte verschieben sich — Putten nach
  oben, kurzes Spiel und Annäherung nach unten. **Das ist keine Verbesserung deines Spiels, sondern
  eine Korrektur des Maßstabs.** Wer alte und neue Zahlen nebeneinanderlegt, vergleicht zwei
  verschiedene Maßstäbe.
  **Prüfstand:** feste Anker gegen die veröffentlichte Grundlinie statt gegen sich selbst — zwölf
  Stützstellen mit Toleranz, dazu die Monotonieprobe für alle fünf Lagen (weiter weg darf nie
  billiger sein) und die Probe, dass zwei Putts aus 10 m einen Scratch-Spieler nicht mehr
  bestrafen. Eine Tabelle, die nur gegen sich selbst geprüft wird, kann in falschen Einheiten
  jahrelang konsistent bleiben.

- **v4.23.0 · 2026-08-22** — **Doku-Audit: Die Referenz versprach Werkzeuge, die es nicht gibt.**
  **(1) Vier Funktionen ohne Doku** — `aimUmwegHtml`, `aimZielZuruecksetzen` (v4.20),
  `gpRasterBereit` (v3.98) und `turnierNaehe` (v4.18). Nachgetragen, jeweils mit der Begründung
  statt nur dem Namen: warum ein gezogener Wegpunkt überstimmen **darf**, aber nicht unbemerkt;
  warum das Höhenraster **vor** der Planrechnung geholt werden muss; warum bei Turniernähe
  umgewidmet statt abgesagt wird.
  **(2) Der schwerere Fund: Die Referenz beschreibt `pfFacts()`, `pfVerify()` und `pfDiagShow()`
  als vorhandene Messwerkzeuge** — samt Menüweg „Mehr → Daten → Diagnose → 📐 Layout-Diagnose".
  **Alle drei sind mit v2.03 entfallen.** Die Korrektur stand als Nachtrag 200 Zeilen weiter unten;
  wer oben liest, sucht vergeblich. Dasselbe bei `pfSize()`, das an fünf Stellen im Präsens
  beschrieben wird und seit v1.87 nicht mehr existiert.
  Das ist die Prosa-Verrottung, vor der Regel 0 warnt — **nicht falsch geschrieben, sondern richtig
  geschrieben und dann überholt worden.** Die Abschnitte bleiben stehen, weil die Vorgehensweise
  (messen statt die sechste Hypothese raten) übertragbar ist; sie tragen jetzt einen Riegel davor,
  der sagt, dass hier Geschichte steht und kein Code.
  **Prüfstand:** in beide Richtungen. Keine Funktion ohne Doku — und **keine entfernte Funktion
  ohne Kennzeichnung**: Wird ein Name im Referenzteil erwähnt, den es im Code nicht gibt, muss in
  seiner Umgebung „historisch", „entfallen" oder „Nachtrag" stehen. Sonst liest man ihn als
  Anleitung.

- **v4.22.0 · 2026-08-22** — **Der Kopfraum war ein Schätzwert von v3.39 — und die Anzeige ist
  seitdem viermal gewachsen.** Gefragt: „Sehe ich das Grün samt Bereich dahinter noch, jetzt wo auch
  ‚spielt wie' im Overlay steht?" Die ehrliche Antwort war **nein, nicht zugesichert**.
  `KARTE_KOPF_ANTEIL` stand fest auf 28 % — ermittelt an der Anzeige, wie sie in v3.39 aussah.
  Seitdem sind dazugekommen: die spielt-wie-Zeile mit Bezugspunkt (v4.9/v4.11), die Grünmitte als
  zweite Zahl (v4.11), der Umweg-Hinweis (v4.20) und die Vorgabeschlag-Zeile. **Jede hat die
  Abdeckung erhöht, ohne dass die Konstante mitgewachsen wäre** — und jede hat damit ein Stück von
  dem weggenommen, was hinter dem Grün sichtbar bleiben sollte. Genau die 30 m, die v3.39
  ausdrücklich ins Bild geholt hat, weil dort Wasser, OB oder ein Abhang die Schlägerwahl
  mitbestimmen.
  **Eine Zahl, die eine Layout-Eigenschaft beschreibt, gehört nicht in den Quelltext.**
  `playKopfAnteil()` misst jetzt bei jedem Zeichnen die tatsächliche Unterkante von `#pfTop` und
  `.pf-caddy` gegen die Kartenhöhe. Das Band bleibt `a/(1−a)` — die Formel, die den bisherigen
  Inhalt vollständig unter die Abdeckung schiebt, bei **jedem** Anteil.
  **Gedeckelt auf 15–55 %:** Ist die Anzeige aufgeklappt, verdeckt sie fast alles; ohne Deckel wäre
  der Zuschlag riesig und die Bahn winzig. Wer aufklappt, will lesen, nicht schauen — und ein Tipp
  klappt wieder zu. Die Untergrenze fängt den Messfehler ab, wenn ein Element noch nicht gezeichnet
  ist.
  **Prüfstand:** nicht „die Konstante steht da", sondern die Sache — bei 15, 28, 40 und 55 % muss
  der bisherige Inhalt unter der Abdeckung beginnen, die Breite unberührt bleiben und die Höhe
  ausschließlich nach oben wachsen.

- **v4.21.1 · 2026-08-22** — **Zwei Änderungen trugen dieselbe Fassungsnummer.** Beim Nachsehen,
  ob der von Hand gezogene Zielpunkt schon behandelt ist, fiel auf: Der Umweg-Hinweis und die
  Korrektur der Linienrichtung stehen beide als **v4.20.0** im Changelog. Die Selbstprüfung fällt
  darauf nicht herein, weil sie nur fragt, ob die AKTUELLE Fassung einen Eintrag **hat**.
  **Eine doppelte Nummer ist schlimmer als eine fehlende:** Sie sieht vollständig aus, und man kann
  hinterher nicht mehr sagen, welche Änderung in welcher Fassung steckt — genau das macht den
  Changelog als Werkzeug wertlos. Die Linienrichtungs-Korrektur ist jetzt v4.21.0, `APP_VERSION`
  entsprechend nachgezogen.
  **Der Prüfstand prüft es jetzt:** keine Fassungsnummer zweimal. Dieselbe Lücke wie bei der
  übersprungenen v3.78 (aufgenommen in v3.92) — nur andersherum. Beide Male reichte die Prüfung
  „hat die aktuelle Version einen Eintrag" nicht aus, weil sie über die Reihe als Ganzes nichts
  sagt.

- **v4.21.0 · 2026-08-22** — **BUGFIX: Loch 1 Nordplatz zielte 64 Grad daneben — und es lag nicht
  an der Kartenbearbeitung.** Gemeldet mit der Vermutung, beim Bearbeiten etwas falsch gemacht zu
  haben. Die Lochdaten sind **einwandfrei**: Tee, Grün, 275 m, Linie mit zwei Punkten, keine
  fehlerhafte Korrektur. Nachgerechnet mit den echten Daten:
  `Peilung 42,7° statt 338,5° · Ziel 169 m im Rough · FW 0 %`
  **Zwei Ursachen, die zusammen zuschlugen:**
  **(1)** `hh.line` ist die **rohe** Linie. Ihre Richtung kommt aus OpenStreetMap und ist
  **beliebig** — auf diesem Loch läuft sie **Grün → Tee**. Die Suche nach dem Knickpunkt beginnt bei
  `i=1`, also am falschen Ende.
  **(2)** Der Rückfall `return L[L.length-1]` gab dann den letzten Punkt zurück — der lag **einen
  Meter** vom Abschlag entfernt. Eine Peilung über einen Meter ist reines Rauschen; heraus kamen
  54,9°, und der ±12°-Fächer landete bei 42,7°.
  Beide Hälften mussten repariert werden: durch `holeRef` lesen (das dreht die Linie auf Tee → Grün)
  **und** einen Mindestabstand von 40 m verlangen, bevor ein Punkt als Richtungsgeber taugt. Ohne
  die zweite Hälfte bliebe der Fehler auf jedem Loch bestehen, dessen Linie nur aus zwei nahen
  Punkten besteht.
  **Nachher:** `Peilung 338,9° · FW 69 % · Rough 27 %`.
  **Prüfstand:** dieselbe Bahn, einmal mit vorwärts und einmal mit rückwärts gespeicherter Linie —
  beide müssen dieselbe Zielrichtung ergeben. Dazu die Gegenprobe, dass eine Zwei-Meter-Linie gar
  keine Richtung vorgeben darf. **Die Richtung einer importierten Linie ist keine Information, und
  keine Rechnung darf daran hängen.**

- **v4.20.0 · 2026-08-22** — **Ein von Hand gezogenes Ziel überstimmt die Rechnung — jetzt sichtbar
  und prüfbar.** Gemeldet als Kartenfehler auf Loch 1 Nordplatz: `5 Iron 169 m` + `3 Wood 252 m` =
  **421 m auf einem 275-m-Loch**, quer über eine Wohnsiedlung.
  **Die Karte war unschuldig.** Mit den echten Daten nachgerechnet: `holeRef` liefert Tee und Grün
  275 m auseinander, `grid.green` stimmt damit auf 0 m überein, `tee()` empfiehlt **2 Iron, 339°,
  201 m, FW 69 %**, und die Kette ergibt `3 Wood 201 m` + `LW 74 m` — **Summe genau 275 m**. Auch
  keine überdimensionierte Fläche in der Nähe (größte 9.879 m², korrekt gelegen).
  Der Umweg kam von einem **von Hand gezogenen Wegpunkt**: `if(ov[k]!=null){ pts.push(ov[k]); … }`
  überstimmt die Bewertung vollständig und ungeprüft. Das ist sein Zweck — er darf nur nicht
  **unbemerkt** einen Plan erzeugen, den kein Mensch spielen würde. Und weil er in `PLAY.aim` und
  damit am Rundenentwurf hängt, überlebt er jeden Neustart der Runde; „seit einigen Versionen" war
  in Wahrheit „seit dieser Runde".
  **Drei Ergänzungen:**
  · **`moved` wird endlich benutzt.** Das Feld steckte seit je in den Kettendaten und wurde
  nirgends ausgewertet. Ein gezogener Punkt trägt jetzt **✋** auf der Karte — sonst hält man den
  eigenen Fingerzeig für eine Empfehlung der App.
  · **`aimUmweg()`** vergleicht die Kettenlänge mit der Luftlinie zum Grün. 25 % Zuschlag lässt ein
  echtes Dogleg durch (auf 275 m sind das 69 m Spielraum), fängt aber die 421 m sicher.
  · **Ein Knopf „Ziel zurücksetzen"**, der auch den Zielketten-Zwischenspeicher leert.
  Der Hinweis steht in **allen drei** Caddy-Zweigen und **über** den Zahlen — eine Warnung unter
  dem, wovor sie warnt, ist keine Warnung. Dieselbe Fehlerklasse wie der Rest dieses Tages: Die App
  zeigte etwas an, ohne zu prüfen, ob es sein kann.

- **v4.19.0 · 2026-08-22** — **Die schwächste Kategorie war ein Erfassungsartefakt.** An echten
  Runden gerechnet: Die Auswertung wies **„Putten −3,84"** als größte Schwäche aus. Die Gegenprobe
  passte nicht dazu — **2,01 Putts je Loch, 13 % Drei-Putts, 56 % GIR** sind für Vorgabe 20
  unauffällig; −3,84 wäre katastrophal. Die Verteilung erklärte es: Die **erfassten** ersten
  Puttdistanzen häuften sich bei 6 bis 9 m, kurze Putts standen kaum in den Daten. Die Zahl rechnete
  gegen einen Bestand, der die schweren Fälle systematisch bevorzugt.
  Dazu kam die zweite Hälfte: Die vier Kategorien stammten aus **65 von 144 Löchern** — und die
  Abdeckung stand als *aufklappbarer* Block **unter** dem Ergebnis. Eine Zahl aus 45 % der Löcher,
  mit erkennbarer Schieflage in den Eingaben, wurde ohne Warnung als „schwächste Kategorie"
  ausgewiesen.
  **Zwei Riegel, beide VOR der Zahl statt darunter:**
  · `sgAbdeckungsQuote(rs)` — unter 70 % auswertbarer Löcher steht es als Warnung über dem
  Ergebnis, nicht als Fußnote.
  · `sgPuttSchieflage(rs)` — misst den Anteil erster Putts unter 3 m. Wer über die Hälfte der Grüns
  trifft, hat reichlich kurze zweite Putts; stehen davon fast keine in den Daten, wurden sie nicht
  erfasst. Der Text sagt auch, **in welche Richtung** es verzerrt.
  **Bewusst KEINE automatische Korrektur.** Man kann eine Schieflage nicht herausrechnen, ohne zu
  wissen, wie die fehlenden Werte aussehen — jede Schätzung wäre eine Erfindung. Die App sagt, dass
  die Zahl schief steht, und überlässt den Schluss dem Leser.
  **Was die Rechnung außerdem zeigte** (nicht Code, sondern Befund): Das lange Spiel steht mit
  **+3,27** deutlich im Plus und passt zu 56 % GIR — dieser Teil der Auswertung ist belastbar.
  Belastbar ist auch **Strafschläge −1,00 je Runde**; die kosten unabhängig von jeder
  Erfassungsfrage. Und der schlaggenaue Pfad (`sgHoleShots`) lief auf **3 von 144 Löchern** — er
  ist gebaut, aber die GPS-Schlagdaten fehlen fast überall.

- **v4.18.0 · 2026-08-22** — **Die sechs offenen Punkte abgearbeitet.**
  **(1) `DB.savedAt` — der Stempel, den die Frischeprüfung nicht hatte.** Es gibt zwei Ablagen;
  `idbHydrate` entschied über `dataScore` und `exportedAt`, und **beides sieht Kartenarbeit nicht**.
  Lief localStorage über, lag eine reine Geo-Änderung nur in IndexedDB, der Start nahm die veraltete
  Fassung, und niemand korrigierte. Jeder Schreibvorgang stempelt jetzt — vor dem Schreiben, sonst
  tragen die Kopien verschiedene Stempel. `dataScore` bleibt Rückfall für Stände ohne Stempel; ein
  jüngerer, aber inhaltsärmerer Stand wird ins Protokoll gemeldet, weil er auf ein volles
  localStorage hindeutet. `standNeuer` unterscheidet „älter/jünger" von **„nicht entscheidbar"** —
  zwei verschiedene Auskünfte.
  **(2) Die Uhr** ließ sich hier nicht übersetzen (kein JDK, kein Android-SDK). Stattdessen die
  strengste statische Durchsicht, die ohne Compiler möglich ist — Klammernbilanz mit einem Scanner,
  der Strings und Kommentare überspringt (1201/1201, 3207/3207, 69/69), beide `Caddy.plan`-Aufrufe
  gegen die Signatur gezählt (10/10, `dElev` vor `lie`), `Locale` importiert. **Dabei gefunden:**
  `HoleGeo.dElev` rechnete die Achsenlänge zweimal. Kein Fehler, aber zwei Rechnungen derselben
  Größe — genau daraus entstehen später Abweichungen. `./gradlew assembleDebug` steht weiter aus.
  **(3) Die Fehlerklasse als Riegel.** Fünf Befunde des 21.08. folgten einem Muster: **falsche
  Zusicherungen**, keine Abstürze. Die neue Prüfgruppe hält alle fünf fest, damit keiner
  zurückkommt.
  **(4) Verlagerung zum Verhalten** — die neuen Prüfungen rechnen mit Werten statt Muster zu suchen,
  und der Platz-Durchlauf hat zwei weitere Invarianten.
  **(5) Eine Bildschirmsperre statt zwei.** `_wake` (Runde) und `GPS.wake` (Ortung) forderten
  dieselbe Browser-Sperre an und wussten nichts voneinander. Jetzt hält `_wakeGruende` fest, **wer**
  sie noch braucht; freigegeben wird erst, wenn niemand mehr will. Ohne den Zähler würde das Ende
  der Ortung die Sperre löschen, während die Runde läuft — mit zwei getrennten Sperren war das
  zufällig richtig, jetzt ist es ausdrücklich richtig.
  **(6a) RPE wird ausgewertet**, statt nur erfasst: Wochenlast als Sätze × Wdh. × RPE, **getrennt**
  vom Volumen. Ein Satz mit 40 kg bei RPE 9 belastet anders als einer mit 60 kg bei RPE 5. Sätze
  ohne RPE bleiben draußen — ein geschätzter Anstrengungswert wäre keine Messung.
  **(6b) Der Wochenplan kennt den Turnierkalender.** Zwei Tage vorher keine schwere Einheit; die
  Erinnerung mahnt dann zum **Gegenteil**. Nicht abgesagt, sondern umgewidmet — Mobilität und
  Aufwärmen bleiben. Und **kein automatisches Umräumen**: Die App begründet, sie entscheidet nicht.
  **(6c) Ein fehlendes `catch`** bei der Bestandsabfrage: Scheiterte sie, passierte gar nichts — kein
  Dialog, keine Meldung, der Knopf sah funktionsfähig aus. Dieselbe Klasse wie (3). Die Zahl der
  `.then` ohne `.catch` ist jetzt gedeckelt, damit sie nicht unbemerkt wächst.

- **v4.17.0 · 2026-08-21** — **Die Selbstprüfung hatte recht: 19 Funktionen ohne Doku, 2 leere
  catch-Blöcke.** Beides sind meine eigenen Regeln, und beides ist an einem Tag mit siebzehn
  Fassungen liegengeblieben. Der Riegel hat genau das getan, wofür er gebaut wurde — abgearbeitet
  statt weggeklickt.
  **Nachgetragen (Abschnitt 10, neue Tabelle):** `dgmGitter` · `dgmHangBasis` · `dgmRahmen` ·
  `dgmZelleMitte` · `dgmFuerRunde` · `dgmUiPaint/-Laden/-Loeschen` · `elevQuelle` ·
  `elevQuelleText` · `wetterSetzen` — mit den beiden Punkten, die man beim Anfassen wissen muss:
  `dgmIdx` und `dgmZelleMitte` müssen zueinander passen (sonst landen geholte Höhen an der falschen
  Stelle), und `elevQuelleText` liefert **fünf unterscheidbare** Auskünfte, weil „kein Raster
  geladen" und „außerhalb des Streifens" in verschiedene Richtungen schicken.
  **Nachgetragen (Fitness):** `_fitMedian` · `_fitVergleich` · `_fitEinheitenProMonat` ·
  `fitSpeedProMonat` · `fitLaengeProMonat` · `kraftVerlauf` · `kraftNorm` · `isoWoche` · `est1RM` ·
  `mobBaseline` · `mobBaselineHtml` — jeweils mit der Begründung statt nur dem Namen: warum Median
  und nicht Mittelwert, warum Volumen und e1RM **getrennt** stehen (Belastungs- gegen
  Leistungsgröße), warum Übungsnamen normalisiert werden, und warum die Beweglichkeitstests
  **kein `weight`** haben (`focusList` verlangt eines — ohne Gewicht fließen sie nicht in die
  Golf-Spielstärke ein, denn Beweglichkeit ist eine Voraussetzung, kein Können).
  **Die zwei leeren `catch`-Blöcke** haben jetzt eine Begründung statt gar nichts: der geleerte
  Gameplan-Zwischenspeicher (wird ohnehin neu gerechnet) und `preventDefault` in `geoEdKeinMenu`
  (ältere Browser kennen es dort nicht). Beide sind bewusst still — aber **still ohne Begründung
  ist von vergessen nicht zu unterscheiden**, und genau das prüft der Riegel.

- **v4.16.0 · 2026-08-21** — **Eine Empfehlung statt vier.** Gemeldet: Kopfzeile „7 Wood", Karte
  „3 Wood" — bei **identischem „Grün 33 %"**, also derselben Bewertung mit zwei Schlägernamen
  davor. Dazu „spielt wie 188" oben gegen „spielt wie 201" auf der Karte.
  **Schritt 0 — erst nachgemessen, nicht geraten.** `condFaktor` rechnet nachweislich richtig
  (189 m → 210 m bei 6,9 m/s Gegenwind). Der Fehler saß nicht in der Rechnung, sondern darin,
  **welche** Werte die Kopfzeile erreichen. Um das überhaupt prüfen zu können, gibt es jetzt
  `wetterSetzen()` — dieselbe Lehre wie `dgmSetzen` in v3.99: `let` auf oberster Ebene ist im
  Sandkasten keine Kontext-Eigenschaft, `ctx.WEATHER = …` setzt nur eine Namensvetterin.
  **Ein Fehler, den man nicht reproduzieren kann, wird auf Verdacht repariert — das ist keine
  Reparatur.**
  **Schritt 1 — eine Quelle.** Kopfzeile und Karte entschieden getrennt: Die Kopfzeile nahm
  `ev.best.club` unverändert, die Karte prüft seit v4.15, ob der Schläger die **gespielte** Distanz
  trägt, und tauscht ihn sonst. Beide Stellen waren für sich stimmig und widersprachen sich
  trotzdem — **v4.15 hat den Konflikt nicht erzeugt, sondern sichtbar gemacht.** Jetzt fällt die
  Entscheidung genau einmal, in der Zielkette; Kopfzeile und ausgeklappte Zeile **rendern** sie,
  statt sie nachzurechnen. Fehlt die Kette (keine Geodaten), bleibt es beim Bewertungs-Schläger —
  dann steht aber nur eine Zahl da und es kann nichts auseinanderlaufen.
  **Schritt 2 — Widersprüche werden gemeldet, nicht angezeigt.** Nennen Bewertung und Kette
  verschiedene Schläger oder unterscheiden sich die gespielten Distanzen um mehr als 5 m, geht das
  mit **beiden Zahlen** ins Protokoll. Denn dann ist es meistens kein Abwägen, sondern ein Fehler —
  so wurden heute drei gefunden.
  **Durchlauf:** zwei neue Invarianten über alle 90 Lagen („Kopfzeile und Karte nennen denselben
  Schläger" / „…dieselbe gespielte Distanz"). Gegenprobe mit zurückgebautem v4.16: **3 Verletzungen**,
  zuerst Loch 4 — Kopfzeile 6 Iron, Karte 4 Iron. Diesmal fängt die Regel den Fehler, für den sie
  geschrieben wurde.
  **Und eine wiederkehrende Dummheit abgestellt:** Backticks in Kommentaren, die in einem
  Template-Literal landen, beenden die Zeichenkette und machen die Datei unlesbar. Heute dreimal
  passiert (v3.96, v4.9, jetzt). Der Prüfstand prüft das jetzt, statt dass es jedes Mal beim ersten
  Lauf auffällt.

- **v4.15.0 · 2026-08-21** — **BUGFIX: Die Karte zeichnete Punkte, die der genannte Schläger nicht
  erreicht.** Gemeldet mit dem entscheidenden Satz: „Wenn sich das wie 240 spielt, komme ich mit dem
  3 Wood ja definitiv nie an die eingezeichnete Stelle." Genau so war es.
  **Ursache 1:** `d` in `_aimBuild` ist die **geometrische** Strecke zwischen zwei Kettenpunkten —
  und genau damit wurde der Schläger gewählt (`_aimClub(clubs,d,…)`). Wind, Temperatur und Höhe
  kamen darin nicht vor. Die Kopfzeile rechnete sie mit, die Karte nicht: zwei Antworten auf
  dieselbe Frage, und die auffälligere war die falsche. Gerechnet wird jetzt mit `dSpielt` — je
  Teilstrecke, in **deren** Richtung und mit **deren** Höhe (Gegenwind am Abschlag kann auf dem
  zweiten Schlag Rückenwind sein).
  **Ursache 2, und das war der Kern:** `evPasst` prüfte nur, ob ein Schläger **zu lang** ist
  (`d >= reach*0.85` — sonst schlüge man ihn nicht voll). **Zu kurz war nie ein Ausschluss.**
  Deshalb blieb der 3 Wood mit 213 m Reichweite am 240-m-Punkt hängen: Er war ja nicht zu lang.
  Wegpunkt und Bewertung entstehen unabhängig voneinander, und die Beschriftung hat beide
  zusammengespannt, ohne zu prüfen, ob sie zueinander passen. Jetzt gilt beides, an der gespielten
  Distanz. Trägt kein Schläger, wählt `_aimClub` den längsten — dann steht auf der Karte ehrlich,
  dass es nicht reicht.
  **Die Karte zeigt beide Zahlen:** „3 Wood · 217 m (spielt wie 240)", sobald sie um mindestens
  3 m auseinanderliegen.
  **Selbstanzeige zum Prüfstand:** Die neue Durchlauf-Invariante („der eingezeichnete Schläger
  trägt die gespielte Distanz") hat den Fehler in der Gegenprobe **nicht** gefangen. Erst der
  Diagnoselauf zeigte warum: Auf dem simulierten Platz greift durchgehend der EV-Zweig, und die
  mutierte Stelle wurde gar nicht durchlaufen. Zwei Konsequenzen: Die Wetterwerte des Durchlaufs
  stehen jetzt auf 6 m/s statt 3,9 (die Bedingungen aus der Meldung, an der Ostsee gewöhnlich) —
  mit 3,9 blieb die gespielte zu nah an der gemessenen Distanz. Und die Schwelle liegt bei 5 %
  statt 12 %: **Eine Schwelle muss unter der Wirkung liegen, die sie fangen soll**, sonst ist sie
  Zierde. Dass die Regel den Fehler in dieser Form trotzdem nicht reproduziert, steht hier, statt
  einen grünen Haken zu behaupten.

- **v4.14.0 · 2026-08-21** — **Beweglichkeit bekommt eine Messgröße: fünf Tests und eine
  Baseline.** Bis v4.13 war Beweglichkeitsarbeit die einzige Trainingsart ohne Rückmeldung. Die
  Wirkungsmessung hängt an der Schlägerkopfgeschwindigkeit — und auf die wirkt Mobilität nicht;
  sie am selben Maßstab zu messen ließ sie zwangsläufig wirkungslos aussehen. Es fehlte schlicht
  eine eigene Messgröße.
  **Fünf Tests, vier Minuten, alles zu Hause** — jeder auf genau eine Einschränkung gerichtet, die
  `GOLF_PROG` adressiert: Rumpfdrehung im Sitzen (Trennung Brustkorb/Becken) · Hüft-Innenrotation
  (Drehweg, Seitenunterschiede) · Schulter hinter dem Rücken (Überkopfbahn) · Einbeinstand mit
  geschlossenen Augen (Stand ohne Sicht — im Abschwung schaut man auf den Ball) · Rumpfbeuge
  (Beinrückseite und Beckenkippung). Alle im gewohnten Format mit WOFÜR · AUFBAU · ABLAUF ·
  ZÄHLUNG · FEHLERQUELLE, zu finden unter Training → Tests, Kategorie Beweglichkeit.
  **Bewusst OHNE Richtwerte.** Bei den Golf-Tests gibt es Vergleichszahlen nach Handicap; hier
  hätte ich sie erfinden müssen. Fremde Normen hängen an Alter, Körperbau und Messweise — **eine
  erfundene Zahl ist schlimmer als keine, weil sie nach Wissen aussieht.** Maßstab ist die eigene
  **Erstmessung**: Die Frage lautet nicht „bin ich beweglich genug", sondern „wird es besser als
  bei mir vorher".
  **Kein `weight`** — damit fließen die Tests nicht in die Golf-Spielstärke ein (`focusList`
  verlangt ein Gewicht). Beweglichkeit ist eine Voraussetzung, kein Können.
  **Beim Schultertest ist WENIGER besser**, und die Auswertung weiß das: `higherIsBetter:false`,
  die Pfeilrichtung folgt. Ein Vorzeichenfehler hätte dort Verschlechterung als Fortschritt
  angezeigt — der Prüfstand hat für alle drei Fälle eine Gegenprobe (mehr ist besser, weniger ist
  besser, Verschlechterung).
  **Takt: sechs Wochen.** Kürzere Abstände zeigen Tagesform, keine Anpassung; die Karte sagt, wann
  die nächste Messung fällig ist. Und der Hinweis, dass der **Seitenunterschied** oft
  aussagekräftiger ist als die Summe — bei Golfern ist er die Regel, nicht die Ausnahme.

- **v4.13.0 · 2026-08-21** — **Fitness: gemessen wird jetzt, was gemeint ist.** Drei der sechs
  Befunde aus der Durchsicht umgesetzt — die mit dem größten Verhältnis von Nutzen zu Aufwand.
  **(1) Die Wirkungsmessung maß das Falsche.** Verglichen wurde die **Driver-Länge auf dem Platz**
  (`gpsShots`) — das verrauschteste Signal im ganzen Datenbestand: Wind, Rollweite, Gefälle,
  Bodenhärte, Ball, dazu saisonal verzerrt. Der alte Kommentar räumte das ein und maß trotzdem so.
  Das saubere Signal lag die ganze Zeit daneben: **`clubSpeed` aus den Launch-Monitor-Sitzungen**,
  drinnen gemessen, ohne Wind und ohne Rollweite — und genau die Größe, die Krafttraining
  überhaupt beeinflussen kann. Die Platzlänge bleibt als **Rückfall**, wenn keine LM-Daten da sind,
  und wird dann ausdrücklich als schwächer gekennzeichnet statt kommentarlos gezeigt.
  Dazu getrennt gezählt: **Beweglichkeitsarbeit wirkt nicht auf Geschwindigkeit.** Sie am selben
  Maßstab zu messen ließ sie zwangsläufig wirkungslos aussehen; verglichen wird jetzt gegen
  **Kraft**einheiten, mit dem Hinweis, dass für Mobilität noch eine eigene Messgröße fehlt.
  **(2) Kraft hatte keinen Verlauf.** `kraftVolume` und `est1RM` wurden je Sitzung gerechnet und je
  Sitzung angezeigt — nie über die Zeit. Der einzige Verlauf im ganzen Reiter waren **Yoga-Minuten**,
  also ausgerechnet die Fleiß- statt der Fortschrittsgröße. Neu: `kraftVerlauf()` mit e1RM je Übung
  (Epley, ab drei Messungen, Namen normalisiert — sonst ergibt „Pallof Press" und „pallof  press"
  zwei zu kurze Kurven) und dem Wochenvolumen als eigene Kurve. **Getrennt, nicht zusammen:**
  Volumen ist eine Belastungs-, e1RM eine Leistungsgröße; beides zu vermischen ist der häufigste
  Fehler in solchen Auswertungen.
  **(5) Ohne Steigerungsregel war es Erhaltungstraining.** `GOLF_PROG` stand mit festen Zahlen da —
  3 × 10, für immer. Die Übungen waren gut gewählt, die Dosierung nicht. Jetzt **Spannen**
  (3 × 8–10) und eine Regel, die man sich merken kann: obere Zahl in allen Sätzen sauber erreicht →
  nächstes Mal Last hoch, Wiederholungen zurück auf die untere Grenze. Bei Mobilität wird nicht
  gesteigert, sondern gehalten.
  **Bewusst KEINE Automatik:** Die App könnte aus `sets/reps/weight` selbst ausrechnen, wann zu
  steigern ist. Sie tut es nicht — ob ein Satz **sauber** war, steht in keiner Zahl, und eine
  Automatik, die schlechte Technik belohnt, wäre schlimmer als keine.
  **Offen geblieben** (eigene Vorhaben): Testbatterie mit Baseline für die Beweglichkeit, Kopplung
  des Wochenplans an den Turnierkalender (keine schwere Einheit 48 h vor dem Start), und die Frage,
  ob das nie ausgewertete RPE-Feld eine Auswertung bekommt oder verschwindet.

- **v4.12.0 · 2026-08-21** — **Die Vollbild-Einblendung des Browsers loswerden.** Gemeldet: „…
  zum Beenden des Vollbildmodus: von oben ziehen" bei jedem Rundenstart, und es stört sehr.
  **Ehrlich zuerst: Diese Einblendung kommt vom Browser, nicht von der App, und kein JavaScript
  kann sie unterdrücken.** Sie ist vorgeschrieben, sobald eine Seite die Fullscreen-API benutzt —
  und das ist richtig so: Sonst könnte eine Seite den Bildschirm übernehmen, ohne dass man weiß,
  wie man wieder herauskommt. Wer etwas anderes verspricht, verspricht etwas, das es nicht gibt.
  **Der einzige Weg an ihr vorbei ist, die API gar nicht erst zu rufen** — und genau das geht,
  wenn die App vom Startbildschirm im Anzeigemodus `fullscreen` läuft: Dann ist sie bereits
  vollbildig, es gibt nichts zu verlassen, und die Einblendung entfällt. Das Manifest steht deshalb
  jetzt auf `display: "fullscreen"` mit `standalone` als Rückfall, und `pfFullscreenAuto` prüft
  `matchMedia("(display-mode: fullscreen)")` und kehrt dort sofort zurück.
  Der Standalone-Riegel war schon einmal da und wurde in v2.61.1 entfernt — zu Recht: In
  `standalone` blendet Vollbild weiterhin die Statuszeile aus. Für `fullscreen` gilt das nicht
  mehr, dort ist sie ohnehin weg. Der Riegel kommt also zurück, aber **nur für diesen einen Modus**.
  Der Hand-Knopf ⛶ sagt dort jetzt „Läuft bereits im Vollbild" statt eine Anfrage zu stellen, die
  nichts ändert außer der Einblendung.
  **Und die Einstellung erklärt es**, statt den Nutzer suchen zu lassen: Woher der Hinweis kommt,
  warum er sich von hier nicht abschalten lässt, und die zweitbeste Lösung („Nur ⛶") für den Fall,
  dass man die App im Tab betreibt.
  Nebenbei ein sprödes Zeichenfenster im Prüfstand entschärft: Die Prüfungen zu `pfFullscreenAuto`
  hingen an einem festen 1.500-Zeichen-Ausschnitt und brachen an der Ergänzung, ohne etwas über die
  Sache zu sagen.

- **v4.11.0 · 2026-08-21** — **„Wenn ich die Grünmitte angreifen will, passen die zwei Distanzen
  nicht zusammen."** Sie passten auch nicht — sie gehören zu **verschiedenen Punkten**:
  · Die große Zahl oben (95 m) ist die Entfernung zur **Grünmitte**.
  · Die spielt-wie-Zahl rechnet auf den **Zielpunkt des Caddys** — „12 m kurz der Fahne", also
  83 m roh, plus 8 m Wind und Höhe = 91.
  v4.9 hat den Bezugspunkt benannt („bis Ziel"). Das war nötig, aber nicht genug: **Wer die
  Grünmitte anspielen will, fand die Zahl dafür nirgends.** Sie fehlte genau dann, wenn man sie
  braucht — beim Widerspruch zum Vorschlag. Eine Empfehlung, der man nicht folgen kann, weil die
  Alternative unbeziffert bleibt, ist keine Empfehlung, sondern eine Vorschrift.
  **Jetzt steht die Grünmitte daneben**, als zweite gleichrangige Zahl unter der ersten — und nur
  dann, wenn der Caddy woanders hin zielt (sonst stünde dieselbe Zahl zweimal da). Damit sind beide
  Wege beziffert: dem Vorschlag folgen oder die Mitte anspielen.
  **Warum der Caddy kurz zielt:** Die Erwartungsrechnung mittelt über die Streuung. Liegt hinter der
  Fahne mehr Ärger als davor — oder ist das Grün flach und lang —, verschiebt sich das Optimum nach
  vorn, auch wenn die Mitte „richtiger" aussieht. Das ist Absicht und keine Zielungenauigkeit; mit
  beiden Zahlen kann man die Entscheidung jetzt selbst treffen.

- **v4.10.0 · 2026-08-21** — **„Vollständig geladen" und trotzdem „Höhe unbekannt": drei Ursachen,
  gemessen statt vermutet.** Nachgebaut wurde ein Raster, das mit dem alten Abtaster (bis v4.7)
  gefüllt und dann weitergeschleppt wurde: **25.170 gefüllte Zellen, 38.646 verlangte, 14.363
  wirklich fehlend — und 30 % der Punkte auf der Bahn ohne Höhe.** Genau das Bild.
  **(1) Der Bestand wurde über die falsche Menge gezählt.** `dgmStatus` zählte ALLE gefüllten Zellen
  des Feldes und verglich sie mit der LÄNGE der Sollliste. Zwei Zahlen über verschiedene Mengen: Ein
  Raster nach altem Abtastmuster kann mehr Zellen belegen als die neue Liste verlangt und trotzdem
  genau die fehlen lassen, auf die es ankommt. Die Anzeige meldete dann „✓ vollständig" — **und man
  klickt nicht auf „Fehlende laden", wenn nichts zu fehlen scheint.** Gezählt wird jetzt der
  Schnitt mit der Sollliste.
  **(2) Der Rahmenvergleich sah nur die Größe.** `nx`/`ny` wurden verglichen, **Ursprung und
  Maschenweite nicht**. Ändert sich der Ursprung — und das tut er bei jeder Änderung an der
  Bahnführung, etwa einem **Tee/Grün-Tausch**, weil die Bbox aus `holeRef` kommt —, während die
  Größe zufällig gleich bleibt, schreibt `dgmLadenFuer` neue Höhen mit Indizes des neuen Rahmens in
  ein Feld des alten. Jeder Wert landet verschoben, die verlangten Zellen bleiben leer, und Nachladen
  ändert nichts, weil der Zähler sie für gefüllt hält. Jetzt geht der ganze Rahmen in den Vergleich;
  passt er nicht, zieht die Logik aus v4.3 nach geografischer Lage um.
  **(3) Der nutzbare Streifen war schmaler als der versprochene.** `dgmHoehe` interpoliert bilinear
  und braucht vier Nachbarn; am Rand des abgetasteten Bereichs gibt es die nicht. Dieselbe
  Überlegung wie an den Enden (v4.6), nur quer — und dort übersehen. Jetzt **zwei Maschen** Reserve,
  nicht eine: Zellen werden über ihren Mittelpunkt aufgenommen, ein Randpunkt liegt also schon eine
  halbe Masche außerhalb der letzten Mitte. Gemessen: mit einer Masche blieben zwei von 570 Punkten
  bei ±58 m ohne Höhe, mit zweien keiner. Deckel je Lauf auf 16.000, damit ein Platz bei 10 m
  weiterhin in einem Durchgang fertig wird.
  **Für dich:** Der Bestand wird nach dem Einspielen ehrlicher aussehen — vermutlich deutlich unter
  100 %. Das ist keine Verschlechterung, sondern die Wahrheit, die vorher verdeckt war. Einmal
  „Fehlende laden"; steht dort „⚠ Raster passt nicht mehr zur Bahnführung", zieht derselbe Klick es
  vorher um.

- **v4.9.0 · 2026-08-21** — **„Spielt wie 104 m" — wohin eigentlich? Bis zum ZIEL, nicht zum Grün.**
  Die Frage war berechtigt und deckte eine echte Zweideutigkeit auf: Die große Zahl oben (107 m) ist
  die Entfernung zur **Grünmitte**, die spielt-wie-Zahl rechnet auf den **Zielpunkt des Caddys** —
  im gemeldeten Fall „12 m kurz der Fahne". Zwei verschiedene Bezugspunkte, unbeschriftet
  nebeneinander. Wer 104 neben 107 sieht, hält die Differenz für Wind und Höhe; tatsächlich stecken
  auch 12 m Zielversatz darin.
  **Jetzt steht der Bezugspunkt dabei** — „bis Ziel" oder „bis Grünmitte", je nachdem, worauf
  gerechnet wurde. `condZeile` bekommt ihn als Parameter, beide Aufrufer geben ihn mit.
  **Und die Zahl ist präsent, eingeklappt wie ausgeklappt.** Ausgeklappt eigener hervorgehobener
  Block mit Goldkante und 19 px statt kleingedruckt zwischen Wind und Quelle. Eingeklappt: Dort
  stand sie **überhaupt nicht** — `now.spieltWie` wurde in der Zeile abgefragt, aber nirgends
  gesetzt. Toter Code, die Anzeige blieb immer leer. `caddyFuerPunkt` liefert sie jetzt mitsamt
  Bezugspunkt.
  **Selbstanzeige:** Beim Bau bin ich in v3.91 gelaufen — `zielName` in `condZeile` benutzt und die
  Signatur nicht angefasst. Ein Name ohne Deklaration, exakt die Fehlerklasse, die ich heute schon
  zweimal repariert habe. Der Prüfstand meldete nichts (er sucht Muster im Quelltext), **der
  Platz-Durchlauf schon** — 66 von 90 Lagen ohne Ausgabe, weil dort wirklich gerendert wird. Das ist
  das Argument für den Durchlauf in einem Satz. Die Signatur ist jetzt festgenagelt.

- **v4.8.0 · 2026-08-21** — **Das Schachbrett: Auf schrägen Bahnen fehlte jede zweite Zelle.** Der
  Hinweis war richtig — das war kein Quotenproblem, sondern ein grundsätzlicher Fehler.
  `dgmZellen` **lief die Bahn ab**: Schritt für Schritt entlang der Linie, je Schritt quer versetzt,
  und rundete jede Position mit `dgmIdx` auf eine Zelle. Liegt die Bahn **schräg im Gitter**,
  springt so ein Schritt **diagonal** — von (x,y) nach (x+1,y+1) —, und (x+1,y) wie (x,y+1) werden
  nie getroffen. Heraus kommt ein **Schachbrett**. `dgmHoehe` interpoliert bilinear und braucht alle
  vier Nachbarn; auf einem Schachbrett fehlt immer mindestens einer. Gemessen auf einer 400-m-Bahn,
  Punkte auf der **eigenen Ideallinie**:
  `0° → 45/45 · 15° → 37/45 · 30° → 25/45 · 45° → 37/45 · 60° → 23/45 · 90° → 45/45`
  **Nur die achsenparallelen Fälle waren dicht — und genau so war mein Testplatz gebaut.** 18 Löcher
  exakt Nord-Süd. Die Prüfung konnte den Fehler nicht sehen, weil das Testgelände ihn nicht enthielt.
  Das ist die zweite Lehre des Tages nach v4.6: Ein Testfall, der die Wirklichkeit vereinfacht,
  prüft die Vereinfachung.
  **Jetzt wird gerastert statt abgelaufen:** über das umschließende Rechteck der Bahn laufen und
  jede Zelle aufnehmen, deren **Mittelpunkt** näher als der halbe Streifen an der Linie liegt
  (`_dgmAbstandZuStrecke`, Punkt-zu-Strecke). Von der Richtung der Bahn völlig unabhängig — es gibt
  keine Schritte mehr, die etwas überspringen können. Alle acht geprüften Richtungen: lückenlos,
  auch ±40 m quer. Die Scheiben um Tee und Grün aus v4.6 sind damit überflüssig und entfallen.
  **Und die Maschenweite ist jetzt deine Entscheidung.** Zwischen Feinheit und Ladbarkeit gibt es
  keine richtige Antwort, nur eine Abwägung — gemessen an 18 schrägen Bahnen:
  `10 m: 9.826 Punkte · 1 Lauf · ~33 min · 71 kB` gegen `5 m: 38.994 Punkte · 4 Läufe · ~2,2 h ·
  277 kB`. Beide Zahlen stehen im Bedienfeld, damit die Wahl nicht im Dunkeln getroffen wird.
  Voreinstellung bleibt 10 m; wer 5 m will, bekommt es. Die Neigungs-Grundlinie folgt der
  Maschenweite (2,5 Maschen, mindestens 12 m) — bei feinem Raster misst sie also näher am Stand,
  bei grobem die Geländeform.
  **Für dich:** Erst einspielen, dann einmal „Fehlende laden" — der Umzug aus v4.3 behält alles
  Vorhandene. Danach sollten die Lücken auf Bahn 2 weg sein, unabhängig davon, wie die Bahn liegt.

- **v4.7.0 · 2026-08-21** — **„Höhe unbekannt" auf Loch 2: Das Raster war schlicht nie fertig
  geladen — und konnte es auch nicht werden.** Statt zu raten, gemessen: Ein 18-Loch-Platz braucht
  im 5-m-Raster bei 120 m Streifenbreite **37.875 Punkte**. Bei 18.000 je Stunde sind das **zwei
  Stunden und vierzehn Klicks** auf „Fehlende laden". Praktisch heißt das: Das Raster ist immer halb
  voll, und weil die Zellen von Süd nach Nord gefüllt werden, haben immer dieselben Löcher nie
  Daten. Der Benutzer sieht dann nicht „noch nicht geladen", sondern „Höhe unbekannt".
  **Ein Raster, das man realistisch nicht fertig lädt, ist schlechter als ein gröberes, das fertig
  wird.** Maschenweite deshalb **10 m statt 5 m**: gemessen **9.919 Punkte** für 18 Löcher, ein
  einziger Ladelauf, ein gutes Drittel der Stundenquote, 38 statt 144 kB. Deckel je Lauf von 2.800
  auf 12.000 — damit ist ein Platz in einem Durchgang fertig, und es bleibt Quote für einen zweiten.
  **Was es kostet, offen gesagt:** Die „spielt wie"-Höhe verliert praktisch nichts — Gelände ist auf
  10 m glatt und die Quelle dezimetergenau. Die **Neigung** misst jetzt über 25 m Grundlinie statt
  12 (`DGM_HANG_BASIS`). Das ist die **Geländeneigung der Landezone**, nicht mehr der Stand am Ball.
  Ehrlicher als vorher: Dass 12 m den Stand treffen, war schon optimistisch, sobald man über
  Rasterstützpunkte interpoliert.
  **Ladbarkeit steht jetzt als Regel im Prüfstand**, nicht als Hoffnung: 18 Löcher müssen in einen
  Ladelauf passen und unter zwei Dritteln der Stundenquote bleiben. Wer den Streifen verbreitert
  oder das Raster verfeinert, sieht sofort, was es an Ladezeit kostet — genau die Rechnung, die ich
  bei v3.95 nicht aufgemacht habe.
  **Für dich:** Der Rahmen ändert sich erneut, die Umzugslogik aus v4.3 greift. Alte 5-m-Werte
  fallen in die neuen 10-m-Zellen, es geht also nichts verloren — und der Rest ist in einem Lauf da.

- **v4.6.0 · 2026-08-21** — **BUGFIX: „Höhe unbekannt" mitten auf dem Fairway — es fehlte das
  GRÜN.** Gemeldet mit dem Zusatz „nur teilweise im Höhenraster". Genau ein Punkt fehlte, und zwar
  nicht der, an dem man stand, sondern das Ziel. Zwei Ursachen, beide **an den Enden**:
  **(1) Der Streifen hatte keine Kappe.** Abgetastet wurde nur ENTLANG der Bahnlinie. Eine Linie
  aus OpenStreetMap endet häufig am Grünrand oder schon davor — die Grünmitte liegt dann jenseits
  des letzten Kettenpunkts, und quer abgetastet wird dort nichts mehr. Ausgerechnet der Punkt, auf
  den jede Rechnung zielt, lag außerhalb.
  **(2) Bilinear braucht vier Nachbarn.** `dgmHoehe` interpoliert zwischen vier Zellen; selbst ein
  Punkt genau am Rand des abgetasteten Bereichs fällt deshalb durch. Der Rand ist immer eine halbe
  Masche zu knapp — auch dann, wenn man „bis zum Grün" abgetastet hat.
  **Jetzt** wird die Kette an beiden Enden um `DGM_KAPPE` (45 m) verlängert, und um Abschlag und
  Grün kommt zusätzlich eine **Scheibe** desselben Radius. Das ist der Bereich, in dem man
  tatsächlich steht und auf den man zielt; er darf nicht der einzige sein, der fehlt. Der Rahmen
  wächst entsprechend mit — sonst fiele genau das heraus, was neu abgetastet werden soll.
  **Und die Auskunft sagt jetzt, WELCHER Punkt fehlt:** „der Zielpunkt liegt außerhalb des
  Höhenrasters" gegen „deine Position liegt außerhalb". „Nur teilweise im Höhenraster" war zwar
  wahr, aber unbrauchbar — es ließ offen, wo man suchen muss, und genau das hat die Diagnose
  gekostet.
  **Für dich:** Der Rahmen ändert sich, also greift die Umzugslogik aus v4.3 — die bereits
  geladenen Punkte bleiben, es fehlen nur die neuen Enden. Ein Klick auf „Fehlende laden".
  **Prüfstand:** Nicht „das Grün ist drin", sondern **mit Reserve** — 20 m hinter Grün und
  Abschlag, und alle vier bilinearen Nachbarzellen des Grüns. Gegenprobe mit zurückgebautem v4.6:
  drei Prüfungen fallen, „Grün ist abgetastet" allein hätte den Fehler **durchgelassen**.

- **v4.4.0 · 2026-08-21** — **Durchsuchung nach der v3.94-Fehlerklasse: zwei weitere Stellen
  gefunden.** Die Frage war, wo sonst noch Schwachstellen stecken. Statt zu raten, den Quelltext
  mechanisch nach den Mustern durchsucht, die diese Woche schon dreimal zugeschlagen haben.
  **(1) `gpFingerprint` konnte einen Tee/Grün-Tausch grundsätzlich nicht sehen.** Er las
  `geo.holes[k]` roh — und weil beide Punkte in dieselbe **Summe** laufen, kommt bei vertauschten
  Punkten zwangsläufig derselbe Wert heraus. Wer die Ausrichtung eines Lochs korrigierte, behielt
  den alten Plan mit der alten Orientierung, bis er nach 30 Tagen von selbst verfiel. Jetzt über
  `holeRef`, und die Marke geht **eigens** ein, damit auch ein symmetrisches Loch den Unterschied
  zeigt.
  **(2) `_aimApproachEv` nannte seine Variable `hr` — und war es nicht.** Überall sonst bezeichnet
  `hr` das Ergebnis von `holeRef()`; hier stand der rohe Eintrag darunter. Auf einem getauschten
  Loch zielte die Annäherungsbewertung damit auf den **Abschlag**. Der irreführende Name ist der
  Grund, warum die Stelle beim Lesen nicht auffiel — auch beim Schreiben von v3.94 nicht, obwohl
  ich damals genau danach gesucht habe. **Namen sind kein Beiwerk.**
  **Was die Durchsuchung sonst ergab** (aufgenommen, nicht repariert): `playMapInitView` liest
  ebenfalls roh, ist aber harmlos — es baut nur einen umschließenden Rahmen aus beiden Punkten, und
  der ist gegen Vertauschen unempfindlich. Zwei leere `catch`-Blöcke, beide begründet. 47 `.then`
  ohne `.catch`; die geprüften Stichproben hängen an `idbSet`, wo ein Fehlschlag folgenlos ist.
  Keine Listenänderung ohne Zeitstempel.

- **v4.3.0 · 2026-08-21** — **Streifen auf 120 m verbreitert — und ein Rahmenwechsel wirft keine
  Punkte mehr weg.** ±45 m um die Ideallinie reichten auf breiten Bahnen und in Doglegs nicht: Dann
  stand „außerhalb des geladenen Streifens", obwohl alles heruntergeladen war. ±60 m deckt auch
  einen sauber daneben gesetzten Abschlag ab. Kostet rund ein Drittel mehr Punkte (etwa 150 statt
  100 kB je Platz); bei einem Stundenlimit von 18.000 ist das der günstigere Fehler.
  **Der eigentliche Fund steckte in der Umstellung.** `dgmLadenFuer` legte bei jedem Rahmenwechsel
  schlicht ein **leeres Feld** an — diese Änderung allein hätte also jedes bereits geladene Raster
  vernichtet, und mit dem Stundenlimit im Rücken hätte man sie besser nie gemacht. Eine Konstante,
  die man nicht mehr anfassen kann, ist eine schlechte Konstante.
  Jetzt wird **umgezogen statt geleert**, und zwar nach **geografischer Lage**, nicht nach
  Feldindex: Jede belegte Zelle des alten Rasters wird über ihren Mittelpunkt in das neue
  eingeordnet. Das ist gegen jede Art von Rahmenwechsel robust — verschobener Ursprung, andere
  Größe, sogar andere Maschenweite. Nur was im neuen Rahmen gar nicht mehr vorkommt, fällt weg, und
  das ist richtig so. Stand und Quellenangabe ziehen mit, und das Protokoll meldet, wie viele
  Punkte übernommen wurden.
  **Für dich heißt das:** Nach dem Einspielen zeigt der Bestand nicht mehr 100 %, weil der Streifen
  breiter geworden ist — aber es fehlen nur die **neuen Randpunkte**, nicht die alten. Ein Klick
  auf „Fehlende laden" genügt.
  **Prüfstand:** Der Umzug wird nachgestellt (derselbe 15-m-Versatz, den diese Version auslöst) und
  jeder Punkt sowohl auf seinen Wert als auch auf seine **geografische Lage** geprüft — derselbe
  Wert an der falschen Stelle wäre der schlimmere Fehler. Die Korridorbreite wird an der Sache
  geprüft, nicht an der Zahl: 50 m neben der Linie muss abgedeckt sein, 70 m nicht mehr.

- **v4.2.0 · 2026-08-21** — **Es gab DREI Caddy-Zweige, nicht zwei — und das Raster überlebte
  keinen Neustart.** Zwei gemeldete Symptome, zwei getrennte Ursachen.
  **(1) „Ich sehe immer wieder kein spielt wie."** v3.97 hat die Zeile im EV-Zweig und im
  Regel-Zweig immer sichtbar gemacht — und den **dritten** übersehen: den Annäherungs-Zweig (B4,
  8–200 m). Ausgerechnet der ist auf einem **Par 3** und beim **zweiten Schlag** zuständig, also
  dort, wo man am häufigsten hinsieht. Dort fehlte auch die **Fahnenwahl** (vorn/Mitte/hinten) —
  dieselbe Ursache, dieselbe Zeile. Beides ergänzt.
  Der Prüfstand zählt jetzt die Zweige, statt sie einzeln aufzuzählen: `play-caddy`-Blöcke gegen
  Blöcke, die mit einer Bedingungszeile beginnen. Wer einen vierten Zweig baut, fällt sofort auf —
  eine Prüfung, die zwei benannte Stellen abhakt, hätte diesen Fehler wieder durchgelassen.
  **(2) „Höhe unbekannt, obwohl ich alles heruntergeladen habe."** Stimmte beides. Das Raster wurde
  ausschließlich in `playStartLive` geholt. Wer die App **mitten in einer Runde** neu lädt —
  Absturz, Neustart, Wechsel zur Uhr und zurück — hatte danach `DGM === null`, und zwar für den
  Rest der Runde. Jetzt holt `renderPlay()` es mit; der Aufruf kehrt sofort zurück, wenn es schon
  das richtige Raster ist, und ein Schloss (`_dgmLaeuft`) verhindert, dass gleichzeitige
  Zeichenläufe dieselbe Datenbankabfrage mehrfach anstoßen.
  **Dazu eine schärfere Auskunft:** „keine Höhendaten geladen" war in diesem Fall die *falsche*
  Diagnose und schickte in die falsche Richtung. Es sind zwei verschiedene Nichts —
  **„kein Höhenraster für diesen Platz geladen"** gegen **„außerhalb des geladenen Streifens (das
  Raster deckt nur die Bahnen ab)"**. Das zweite kann auch bei vollständigem Download auftreten,
  wenn man weit neben der Bahn steht, und ist dann kein Fehler, sondern die Wahrheit.

- **v4.1.0 · 2026-08-21** — **Die Uhr bekommt Höhen — und rechnet endlich dieselbe Formel.**
  `Wx.playsLike` auf der Uhr kannte Temperatur und Wind, aber **weder Höhe noch Regen** — die PWA
  hat beides seit langem. Zwei Rechnungen für dieselbe Sache: Handy und Uhr nannten für denselben
  Schlag verschiedene Zahlen, und am Ende glaubt man keiner von beiden.
  **Warum kein Raster:** Das DGM1 wiegt rund 100 kB je Platz — und die Uhr braucht keine Neigung,
  sondern **eine Zahl**: wie viel höher liegt das Ziel. Deshalb je Bahn ein **Profil** in
  `watch.json` (`watchElevProfil`): alle ~20 m ein Wert, in ganzen **Dezimetern relativ zum
  Abschlag**. Ein 480-m-Loch sind 25 kleine Zahlen, 18 Löcher unter 2 kB. Relativ, weil die Uhr nur
  Differenzen braucht — damit bleiben die Zahlen zweistellig, und die Bezugsflächen-Frage aus v3.95
  erledigt sich: Innerhalb eines Profils stammt alles aus einer Quelle.
  **Lückenhaft = gar nicht.** Fehlt ein Stützpunkt, wird kein Profil mitgeschickt und die Uhr
  übernimmt auch keins. Ein halbes wäre schlimmer als keines — sie könnte nicht erkennen, wo es
  endet, und würde den Rand fortschreiben.
  **Auf der Uhr** (`MainActivity.kt`, eigener Changelog-Eintrag dort): `ElevProfil` mit
  `beiMeter()`, `HoleGeo.elev`/`dElev()` (beide Punkte per Kosinussatz auf die Tee-Grün-Achse
  projiziert — das Profil kennt nur eine Dimension), `playsLike(…, dElev, nass)` wortgleich zur
  PWA, `Caddy.plan`/`planCore` reichen es durch, und `elevLabel` schreibt die Höhe **immer** in den
  Plan — auch mit 0, auch wenn sie fehlt. Wie in der PWA seit v3.97; auf der Uhr wiegt es schwerer,
  weil man dort nur die eine Zahl sieht.
  **Beim Bauen selbst hereingefallen, und daraus eine echte Reparatur:** Ich habe `geoInterp` mit
  einer Punktkette statt zwei Punkten gerufen. Der Prüfstand meldete lauter `null` im Profil — und
  beim Nachsehen war die Ursache nicht nur mein Aufruf: **`dgmHoehe` gab bei `NaN`-Koordinaten
  `NaN` zurück statt `null`.** Bei `NaN` sind alle Vergleiche falsch, die Bereichsprüfung lässt ihn
  also durch, `R.h[NaN]` ist `undefined`, und heraus kommt eine Zahl, die keine ist. Der Aufrufer
  prüft auf `null` und hält den Unsinn für eine Höhe. Jetzt mit `isFinite`-Riegel. Das Profil läuft
  außerdem entlang der **Kette**, nicht der Luftlinie — auf einem Dogleg ginge die gerade
  Verbindung durch den Wald.

- **v4.0.0 · 2026-08-21** — **Aus dem Hang schlägt man anders — jetzt rechnet die App das auch.**
  Bis v3.99 kannte sie die Neigung nur als **Ziel**eigenschaft: Ein Landepunkt am Hang bekam einen
  Aufschlag (v3.95/v3.98), weil der nächste Schlag von dort schwerer wird. Das war ein
  **Stellvertreter** — `pointES` kennt nur Entfernung und Lage, keine Neigung, und konnte den
  nächsten Schlag deshalb nicht selbst teurer machen. Jetzt kommt die andere Hälfte dazu: wie man
  **selbst** aus der Schräglage schlägt.
  `sigmaHang(sg, hang, carry)` passt drei Dinge an:
  · **Länge** — bergauf steht der Schläger effektiv offener: höher, kürzer. 10 % Lage ≈ 14 m auf
  einen 200-m-Schläger, also gut ein halber Schläger. Bergab umgekehrt.
  · **Seite** — der Ball geht dorthin, wo der Boden **fällt**. 10 % Querneigung ≈ 8 m Versatz.
  Steigt es nach rechts, zieht es nach links; die beiden Richtungen sind exakt spiegelbildlich.
  · **Streuung** — jede Schräglage vergrößert beide σ, bergab die **längs** zusätzlich: Dort kommen
  die dünnen und fetten Treffer her.
  **Gelesen wird die Neigung an der EIGENEN Position (`from`), nicht am Ziel.** Das sind zwei
  verschiedene Fragen — „wohin lege ich den Ball" gegen „wie gut treffe ich von hier" — und beide
  zählen. Am Abschlag entfällt es: Dort steht man eben.
  **Kein Doppelzählen, und das ist begründet, nicht behauptet:** Innerhalb eines `nextShot`-Aufrufs
  stammt `es` aus `pointES` (Tabelle, ohne Neigung) — der Aufschlag aus v3.98 bleibt dort das
  einzige Neigungssignal und ersetzt nichts. In der zusammengesetzten Kette treffen beide
  aufeinander, aber dort ist der Aufschlag nur **Rangfolge**, nicht Ergebnis: `planCourse` liest
  `es`, nicht `score`.
  **Die Zahlen sind Vorlieben, keine Messung.** Eine belastbare Tabelle „Neigung kostet x Meter"
  gibt es nicht; was es gibt, sind Größenordnungen, die jeder Trainer nennt, und die stehen in
  `STRAT.HANG` — mit Deckel bei 20 %, weil man eine 60-%-Böschung nicht mehr spielt, sondern
  hinunterfällt. Sobald genug eigene Schläge aus Schräglagen aufgezeichnet sind, gehören sie durch
  **gemessene** Werte ersetzt, wie bei `dispersion` auch.
  **Sichtbar:** Der σ-Chip nennt die Standlage („⛰ 7 % Standlage"), sobald sie wirkt. Ohne das
  wundert man sich, warum das Oval hier breiter ist als auf der letzten Bahn, und misstraut der
  Zahl. Ohne geladenes Höhenraster passiert weiterhin nichts.
  **Prüfstand:** 24 Prüfungen an gerechneten Werten statt an Quelltextmustern — Vorzeichen in beide
  Richtungen, Spiegelbildlichkeit der Querneigung, bergab streut längs stärker als bergauf, Deckel
  hält, und die Gegenprobe, dass `tee()` die Anpassung **nicht** anwendet.

- **v3.99.0 · 2026-08-21** — **`wetterCarry` maß die Höhe bis zum Grün statt bis zum Landepunkt.**
  Gefragt ist, wie weit **dieser Schläger** trägt — dafür zählt der Höhenunterschied bis dorthin,
  wo der Ball aufkommt, nicht bis zur Fahne. Auf einer Bahn, die erst fällt und dann zum Grün
  steigt, hat das oft das **falsche Vorzeichen**: Der Abschlag geht bergab, der Schläger deckt mehr
  Boden — gerechnet wurde bergauf, also weniger. Mit dem groben Online-Raster fiel das kaum auf, da
  wurde unter 1,5 m ohnehin alles zu 0. Seit v3.95 wird aus dem Näherungsfehler ein sichtbarer.
  **Am gebauten Gelände nachgerechnet** (Senke bei 200 m, Grün 300 m und 5 m höher): Höhe zum Grün
  +5,0 m, zum Landepunkt −10,0 m — entgegengesetzte Vorzeichen. Ein 200-m-Schläger deckte alt
  **195 m**, jetzt **207 m**. Zwölf Meter, also ein guter Schläger Unterschied, auf einem völlig
  gewöhnlichen Loch.
  **Henne und Ei** — der Landepunkt hängt von der Traglänge ab, die Traglänge vom
  Höhenunterschied zum Landepunkt — in **zwei Durchgängen** gelöst: erst mit roher Traglänge
  schätzen, dann mit der korrigierten nachsetzen. Mehr braucht es nicht, der ±25-%-Deckel begrenzt
  die Korrektur und ein dritter Durchgang bewegt den Punkt um weniger als die Maschenweite des
  Rasters. Ohne DGM1 bleibt es beim alten Weg über das Ziel — **das ist keine Notlösung**, sondern
  die richtige Näherung, solange die Auflösung nur große Geländehänge zeigt.
  **Nebenbei prüfbar gemacht:** `DGM` wurde an drei Stellen verstreut zugewiesen und war aus dem
  Prüfstand gar nicht erreichbar — `let` auf oberster Ebene wird im Sandkasten keine
  Kontext-Eigenschaft, `ctx.DGM = …` hätte nur eine Namensvetterin gesetzt, während das Modul
  weiter seine eigene liest. Ein kleiner Setzer (`dgmSetzen`) behebt beides: eine Zuweisungsstelle,
  und die Verhaltensprüfung oben ist überhaupt erst möglich. Deshalb steht dort jetzt eine echte
  Rechnung statt einer Quelltextsuche.

- **v3.98.0 · 2026-08-21** — **Die Hanglage erreichte den Gameplan gar nicht.** Auf die Frage, ob
  der Plan die neuen Höhen und Regeln nutzt, nachgesehen — und drei Lücken gefunden, von denen die
  dritte alles aushebelte.
  **Was schon lief:** Die feineren Höhen kommen im Plan an. `wetterCarry()` steckt in `tee()` **und**
  `nextShot()` und ruft `elevDelta`, das seit v3.95 zuerst DGM1 nimmt. Der Plan rechnet also mit
  Dezimetern statt grobem Raster, und offline.
  **(1) Der Neigungsterm stand nur in `tee()`.** Ausgerechnet dort fehlte er, wo er am schwersten
  wiegt: Der Layup auf Par 5 und der Zwischenschlag legen den Stand für den **Annäherungsschlag**
  fest. Ein Schrägstand am Abschlag kostet Fairwaybreite, einer vor dem Grün kostet den
  Grüntreffer. `nextShot()` hat ihn jetzt mit dem **1,5-fachen** Gewicht — und nur, wenn noch ein
  Schlag folgt (`restNach > 5`); wer von hier einlocht, steht danach nicht mehr schief.
  **(2) `gpFingerprint` kannte das Höhenraster nicht.** Ein frisch geladenes DGM1 machte **keinen
  einzigen** bestehenden Plan ungültig — er behielt seine Flachland-Empfehlungen, bis er nach
  `GP_MAXALTER_TAGE` von selbst verfiel. Der Abdruck ist die einzige Stelle, an der „die Eingaben
  haben sich geändert" entschieden wird, und **eine neue Datenquelle ist eine geänderte Eingabe**.
  Aufgenommen werden Anwesenheit und Größe, nicht der Inhalt: Das Raster ändert sich nur, wenn
  Punkte nachgeladen werden — und genau das soll den Plan erneuern.
  **(3) Und der Knackpunkt: Planrechnungen liefen ohne geladenes Raster.** `dgmNeigung()` liest die
  Modulvariable `DGM`, gefüllt nur von `dgmAktiv()` — und das wurde ausschließlich beim
  Rundenstart gerufen. Die stündliche Auffrischung und jedes `gpPlan(...,force)` sahen
  `DGM === null` und legten einen Hang-Term von 0 an. **Die Hanglage wirkte im Spielmodus und im
  vorberechneten Plan nicht, bei identischer Oberfläche** — die unangenehmste Sorte Fehler, weil
  nichts falsch aussieht. Selbst eine Reparatur von (2) allein hätte neu gerechnet und wieder flach
  ergeben. `gpAutoRefresh` ist jetzt `async` und holt das Raster **vor** dem Abdruckvergleich
  (andere Reihenfolge = jede Stunde eine gemeldete Änderung und endloses Neurechnen).
  `gpPlan` bleibt synchron — es wird aus Zeichenpfaden gerufen — stößt das Laden aber an und hält
  in `p.dgm` fest, womit gerechnet wurde. Die Selbstheilung macht der Abdruck: Ein ohne Raster
  gerechneter Plan passt nicht mehr zu den Eingaben, sobald es da ist.
  **Prüfstand:** neue Gruppe über die ganze Kette statt über einen Term — Hang in `nextShot`,
  Raster im Abdruck, Reihenfolge in der Auffrischung, `gpPlan` synchron, `wetterCarry` in beiden
  Zweigen. Nebenbei eine spröde Prüfung entschärft: „Aufschlag geht in den score" hing an der
  exakten Zeilenform `+ blk + lpAuf;` und brach an der Ergänzung — ein Test, der an der letzten
  Position klebt, sagt nichts über die Sache.

- **v3.97.0 · 2026-08-21** — **„Spielt wie" steht jetzt immer da — und die Höhe auch, wenn sie
  null ist.** Bisher schwieg die Zeile in drei Fällen, und alle drei sahen für den Leser gleich
  aus, nämlich nach „die App rechnet das nicht":
  · `condFaktor` stieg bei `!WEATHER && !dElev` ganz aus — wobei `dElev===0`, also **gemessen
  eben**, wegen des Wahrheitswerts genauso zählte wie „unbekannt".
  · `condZeile` schwieg unter 3 m Unterschied ohne Einzelteile.
  · `weatherEffectHtml` (Regel-Zweig) schwieg ohne Wetterdaten komplett, und die Höhe brauchte dort
  ≥ 2 m, um überhaupt genannt zu werden.
  **Die Schweigeschwelle war eine bewusste Entscheidung aus v3.33** („eine Zeile, die bei jedem
  Loch ±1 m meldet, liest niemand mehr") — und sie war falsch. Der Leser kann nicht unterscheiden,
  ob nichts dasteht, weil nichts ausmacht, oder weil nichts gemessen wurde. Das ist der teurere
  Fehler, und die Unterdrückung sparte genau eine Zeile. Besonders seit v3.95, wo es mit DGM1 eine
  Höhenquelle gibt, die man erst laden muss: Genau dann will man wissen, ob sie greift.
  **Jetzt gilt:** Die Zeile erscheint immer, außer es fehlt ein Von- oder Zielpunkt. Die Höhe steht
  immer in den Einzelteilen — als `⛰ eben (±0 m)`, `⛰ bergauf 3,4 m` oder `⛰ Höhe unbekannt`.
  Und die Zeile nennt ihre **Quelle**: Höhenraster DGM1 · grobe Online-Höhen · nur teilweise im
  Raster (gemischte Bezugsflächen ergeben keine Differenz) · keine Höhendaten geladen. Fehlt das
  Wetter, steht das als „ohne Wetter" dabei, statt die ganze Zeile verschwinden zu lassen.
  Schwelle für „eben" ist 0,5 m statt 1,5 m — die alte stammte von der groben Online-Quelle, mit
  DGM1 ist ein Meter eine Aussage. Unter 10 m mit einer Nachkommastelle.
  **Beide Zweige gleich behandelt**, EV wie Regel — der Prüfstand hält das fest, weil eine
  Reparatur an nur einer Stelle genau die Uneinheitlichkeit einbaut, gegen die dort schon einmal
  geschrieben wurde.

- **v3.96.0 · 2026-08-21** — **Das Höhenraster steht jetzt auch dort, wo man es sucht.** Bisher
  nur unter Mehr → Plätze; wer die Kartenverwaltung eines Platzes öffnet (Satellitenbild,
  Luftbild-Quelle, Detailgrad, „Ganzen Platz offline sichern"), sah nichts davon und hätte es
  schlicht vergessen. Jetzt steht derselbe Ladeknopf samt Bestandsanzeige direkt darunter.
  **Bewusst AUSSERHALB der `satOn()`-Bedingung:** Luftbild und Geländemodell haben nichts
  miteinander zu tun — wer das Satellitenbild ausschaltet, will trotzdem eine „spielt wie"-Distanz,
  die ohne Netz rechnet.
  **Beide Wege sind eine Sache, nicht zwei:** derselbe IndexedDB-Schlüssel (`dgmKey`), dieselbe
  Laufsperre (`DGMDL`). Zwei getrennte Sperren hätten zwei parallele Läufe für denselben Platz
  erlaubt, die dieselben Punkte doppelt holen und das Stundenlimit des Dienstes verheizen. Der
  Prüfstand hält beides fest.
  **Dabei selbst hereingefallen:** Der erklärende HTML-Kommentar stand im Markup und enthielt
  `satOn()` in Backticks — mitten in einem Template-Literal. Damit endete die Zeichenkette dort,
  und die ganze Datei ließ sich nicht mehr auswerten. Der Prüfstand meldete es sofort; er tut
  genau das, wofür er da ist.

- **v3.95.0 · 2026-08-21** — **Amtliches Höhenraster (DGM1): genauer, offline, und der Caddy sieht
  jetzt schräge Landezonen.** Zwei Dinge auf einmal, weil sie dieselbe Quelle brauchen.
  **(1) Die „spielt wie"-Distanz steht nicht mehr auf Sand.** Bisher kam die Höhe von Open-Meteo —
  einem globalen Gitter mit Stützpunkten im Bereich mehrerer Dutzend Meter, weshalb `elevDelta`
  alles unter **1,5 m** als Rauschen wegwerfen musste. Und ohne Netz fiel die Korrektur still ganz
  weg, also genau auf dem Platz. Die Landesvermessungsämter erheben das Gelände per Laserscan mit
  3–4 Messpunkten je Quadratmeter und geben es im 1-m-Gitter ab, Höhengenauigkeit im
  Dezimeterbereich, in flachem Gelände 15 cm — in Schleswig-Holstein als Open Data (CC BY 4.0).
  Bezogen über `hoehendaten.de`, das die Daten aller 16 Länder nach GeoTIFF konvertiert hat und
  eine CORS-fähige JSON-Schnittstelle anbietet. Genutzt wird die **GPX-Form**: sie nimmt viele
  Punkte auf einmal und rechnet in Lon/Lat — **damit entfällt die UTM-Umrechnung vollständig**, und
  mit ihr eine ganze Fehlerklasse. Rauschsperre jetzt **0,3 m** statt 1,5 m.
  **(2) Gespeichert wird nicht das Rohraster.** 1 m über einen Platz wären 1,4 Mio Werte. Gebraucht
  wird die Neigung über 10–20 m, dafür genügt ein 5-m-Gitter entlang der Bahnen: rund **100 kB je
  Platz** als Int16 in Dezimetern — weniger als eine einzige Satellitenkachel. Bedienung wie bei den
  Satellitenkarten (Mehr → Plätze, seit v3.96 auch in der Kartenverwaltung des Platzes), mit ehrlicher Ansage zu Punktzahl, Anfragen und Stundenlimit
  (der Dienst erlaubt 18.000 Punkte/Stunde, ein Lauf holt bis zu 2.800).
  **(3) Hanglage in der Bewertung.** `dgmNeigung()` misst längs und quer über 12 m Basis; `tee()`
  legt `hang * min(Neigung, 0,15)` auf den Kandidaten. **Die Zahl ist eine Vorliebe, keine
  Messung** — deshalb klein und je Spielweise verschieden (sicher 1,2 · normal 0,8 · offensiv 0,4),
  bei 5 % Neigung also 0,06 Schläge in „sicher". Sie bricht Gleichstände, sie kippt nichts. Steht
  in der Begründung mit drin: „Landezone geneigt (7 %, bergab) — kein ebener Stand".
  **Zwei Fallstricke, die Code UND Doku festhalten:**
  · **Bezugsflächen.** DGM1 rechnet in DHHN2016, Open-Meteo in einer anderen Fläche. Ein Wert aus
  der einen minus einem aus der anderen ist keine Höhendifferenz, sondern der Abstand zweier
  Bezugsflächen plus Zufall — und am Rand des geladenen Rasters wäre genau das der Normalfall.
  `elevDelta` **verweigert gemischte Paare** und gibt lieber `null` zurück.
  · **Aktualität.** Die SH-Daten reichen von 2005 bis 2023. Geländeformen stimmen — Hügel wandern
  nicht —, ein seither umgebautes Grün ist im Modell das alte. Steht so im Hinweistext.
  **Und die Grenze, die bleibt:** Auch 1 m sagt nicht, wie der Ball LIEGT, sondern wie das Gelände
  dort geneigt ist. Die Neigung wirkt außerdem auf die Bewertung des Zielpunkts, noch nicht auf die
  Streuung des Folgeschlags.
  **Prüfstand:** neue Gruppe mit konstruiertem 4-%-Hang — Index und Zellmitte müssen zueinander
  passen, die Gegenrichtung muss das Vorzeichen umkehren, ebenes Gelände 0 % ergeben, eine fehlende
  Rasterecke `null` statt 0. Dabei selbst hereingefallen: `const` auf oberster Ebene ist im
  Sandkasten keine Kontext-Eigenschaft, `G("DGM_GITTER")` war `undefined`, das Testgelände wurde
  mit NaN gefüllt und Int16Array machte daraus lauter Nullen. Jetzt Literale plus Gegenprobe gegen
  den Quelltext.

- **v3.94.0 · 2026-08-21** — **BUGFIX: Auf getauschten Löchern lag das Grün auf dem Abschlag.**
  Loch 1 Nordplatz machte weiter Unsinn — `5 Iron 169 m · FW 0 %`, danach `3 Wood 252 m`, zusammen
  **421 m auf einem 279-m-Loch**. Ursache gefunden und nachgestellt:
  `STRAT.grid()` las `geo.holes[n].green` **roh**. Auf einem Loch mit `swap:true` sind Tee und Grün
  darin vertauscht. `tee()` hatte das für den Abschlagspunkt schon in v2.49 korrigiert — **das
  Raster aber nicht**, und das Raster liefert `g.green`, also das Ziel, auf das die gesamte
  Bewertung zeigt. Ergebnis: `teeP` kam korrigiert (echter Abschlag), `g.green` unkorrigiert
  (ebenfalls echter Abschlag). **Beide zeigten auf denselben Punkt, Abstand 0 m.** `holeLen` war 0,
  der Zielfächer drehte ins Nichts, `FW 0 %` war die ehrliche Antwort auf eine unmögliche Frage.
  **Die Korrektur von v2.49 hat es sogar verschärft:** Solange beide Seiten roh lasen, war die
  Geometrie wenigstens in sich stimmig — nur seitenverkehrt. Halb korrigiert ist schlimmer als gar
  nicht korrigiert. Dieselbe Stelle in `planCourse` (`flag`, Par-3-Startpunkt) mitgezogen.
  **Warum die Selbstprüfung es nicht fand:** Punkt 14 sucht nach `geo.holes[...].tee|green`. Hier
  stand eine Zwischenvariable davor (`hh.green`) — das Muster greift nicht. **Der Riegel prüft die
  Schreibweise, nicht die Sache.** Der neue Prüfstand-Block prüft die Sache: Raster-Grün und
  holeRef-Grün müssen denselben Punkt ergeben, und getauscht wie normal abgelegt muss dieselbe
  Geometrie herauskommen.
  **Doku:** Abschnitt „Was der Caddy NICHT kennt" um die **Hanglage am Landepunkt** ergänzt —
  gerechnet wird nur die Höhendifferenz zwischen zwei Punkten, nie der Stand an der Landestelle.
  Mit der Begründung, warum die Daten dafür auch gar nicht reichen.

- **v3.93.0 · 2026-08-21** — **Vier Befunde aus deinen Screenshots — drei davon waren Fehler von
  mir.** Aus dem Spielmodus gemeldet: Pläne, die „nicht sinnvoll" aussahen. Sie waren es auch.
  **(1) Der Fahnen-Schalter stürzte bei jedem Tipp ab.** `pinSetz()` leerte die Zielketten-Ablage
  per Zuweisung — `_aimCache` ist aber `const`. `TypeError: Assignment to constant variable`, im
  Protokoll neunmal, und der Abbruch kam **vor** `playSaveDraft()`: vorn/Mitte/hinten wurde nie
  gespeichert und nie gerechnet. Der Schalter sah funktionsfähig aus und tat nichts.
  **(2) Ein toter Variablenname legte `STRAT.tee()` lahm — auf manchen Löchern.** In der
  Kandidatenschleife stand `lineDeg`; die Laufvariable heißt `dl`, und `lineDeg` gibt es dort
  nirgends. Weil `_fav` davorsteht, wurde der Ausdruck **nur ausgewertet, wenn für das Loch eine
  bevorzugte Seite gesetzt ist** — dort warf `tee()` einen ReferenceError und das Loch bekam gar
  keine Abschlagsbewertung. **Wörtlich die Fehlerklasse aus v3.89** (Zuweisung ersetzt, alten Namen
  weiter unten stehen gelassen), und die Prüfung auf „Namen ohne Deklaration", die ich damals
  eingebaut habe, hat ihn nicht gefunden — sie prüft nicht innerhalb von Objektmethoden.
  **(3) Der Layup von v3.51 war zurück.** Gemeldet: `Driver 237 · Layup 3 Wood 213 · Grün LW 10 m`
  auf Loch 8 (463 m) — bis auf 9 Meter derselbe Plan, den v3.51 als Fehler aufgenommen hat. Dazu
  Loch 15 mit einem dritten Schlag über **`LW · 0 m`** und Loch 11 mit 32 m Rest.
  v3.66 hatte die Rücknahme gestrichen (zu Recht: zweite Regelstelle) und ließ die Leitplanke
  `wedgeZone` übrig — Fenster 5–25 m, Aufschlag 0,15. **Beide Zahlen waren geraten, und beide
  falsch:** Loch 11 lag mit 32 m außerhalb des Fensters, die Regel sah den Fall gar nicht. Und wo
  sie griff, war sie zu schwach — die Erwartungstabelle gibt 14 m den Wert 2,27 und 100 m den Wert
  2,80, „näher" ist dort also **0,53 Schläge** wert. Mit 0,15 dagegenzuhalten konnte nicht wirken.
  **Jetzt wird der Aufschlag hergeleitet statt gesetzt:** Er ist genau der Vorsprung, den die
  Tabelle der Nähe gegenüber einem vollen Wedge einräumt (0,62 bei 10 m · 0,28 bei 32 m · 0 ab
  45 m). Er **neutralisiert** diese Verzerrung, statt sie zu überstimmen; danach entscheiden die
  Größen, die sich hier wirklich unterscheiden — die Streuung des langen Schlägers (auf Loch 15
  traf der zweite Schlag noch 38 % Fairway) und der Wedge-Bonus. Zwischen 50 und 85 m schweigt die
  Regel weiter: Dort hat der Platz-Durchlauf gemessen, dass „näher ist besser" **stimmt**, und
  diese Messung bleibt unangetastet. Der Unterschied zu v3.51 (Zielpunkt verschieben) und zu v3.63
  (erfundene Zahl gegen gemessene) steht als Begründung an der Regel.
  Dafür darf `wirkung` in `LEITPLANKEN` jetzt eine **Funktion** sein — eine feste Zahl kann nur
  einen Fall treffen.
  **(4) Der Angriffs-Schläger auf Par 5 war Zufall.** In `planCourse` stand `atkCl = pool[0]` —
  schlicht der erste Schläger im Beutel. Der Layup-Kandidat wird sauber auf `rest0-95` gesucht,
  sein Gegenspieler war beliebig. Jetzt der längste, den man vom Boden spielt.
  **Prüfstand:** neue Gruppe mit den drei gemeldeten Löchern als Einzelfälle und der Regel dahinter
  (Aufschlag = Tabellenvorsprung, Schweigen ab 50 m, kein Aufschlag ohne Folgeschlag).
  **Offen und NICHT hier behoben:** Loch 1 zeigt `5 Iron 169 m` + `3 Wood 252 m` auf einem
  **279-m-Loch** — nach dem Kosinussatz rund 80° Winkel zwischen beiden Schlägen, bei einem Fächer
  von ±12°. Das kann nur aus `base` kommen, und `_knick` liest `hh.line` **roh** statt über
  `holeRef()`. Loch 1 Nordplatz ist genau das Loch mit dem Tee/Grün-Tausch. Erst prüfen, ob der
  Tausch gesetzt ist (v3.91), dann den rohen Zugriff angehen — sonst repariert man das Symptom.

- **v3.92.0 · 2026-08-21** — **DOKU: 18 Funktionen nachgetragen, eine falsche Behauptung
  korrigiert.** Der Audit nach v3.91 fand 18 Funktionen, die in der ganzen Doku nicht vorkamen —
  `runSelfCheck()` meldet sie damit seit Längerem als „neu undokumentiert", und die Meldung war
  berechtigt. Jetzt beschrieben, jeweils dort, wo sie hingehören:
  · **Abschnitt 9** — Repo-Wiederherstellung und Referenzstand (`refreshRepoSection`,
  `loadRepoHistory`, `repoHardPull`, `loadRepoRef`), Datei-Zugriff (`fsSave`/`fsOpen`) und der
  Unterschied zu `doExport`/`doImport` (gemerktes `fileHandle` = zweimal Speichern ohne Dialog).
  · **Abschnitt 9, neuer Unterabschnitt** — die inneren Helfer von `renderRecords`
  (`bestRE`/`pickPool`/`bestQuote`, und warum es drei sind statt einem) und `openAddRound`
  (`fillTees`/`existingHole`/`paintFill`).
  · **Abschnitt 11** — `teeRisk`/`evalTee` in `caddyPlan`, mit der Klarstellung, dass deren
  Punktwerte KEINE zweite Gewichtstabelle im Sinne von Regel 0a sind.
  · **Abschnitt 25.6, neu** — der Bilder-Abgleich (`wikiLocalImages`, `wikiImgPull`,
  `wikiImgPush`, `wikiImgSchedulePush`, `wikiImgSyncNow`). Wichtig darin: Die Regel ist **UNION**,
  nicht „neuer gewinnt" — ein gelöschtes Bild kommt beim nächsten Abgleich zurück. Das ist Absicht
  und musste einmal aufgeschrieben werden.
  **KORREKTUR einer falschen Aussage:** Abschnitt 9 nannte den Wake Lock als eine Sache
  (`wakeAcquire` im Spielmodus). Es sind **zwei voneinander unabhängige** — `wakeAcquire`/`_wake`
  hängt an der Runde, `requestWake`/`releaseWake`/`GPS.wake` an der Ortung. Beide fordern dieselbe
  Browser-Sperre an und wissen nichts voneinander. Heute harmlos, weil beide sauber paaren; wer
  aufräumt, muss BEIDE anfassen. Genau die Sorte Satz, die Regel 0 meint.
  **Und eine Lücke sichtbar gemacht:** `v3.78` hat keinen Changelog-Eintrag — die Fassung wurde
  übersprungen. Als Zeile eingetragen, statt die Nummernfolge lügen zu lassen.

- **v3.91.0 · 2026-08-21** — **BUGFIX: Der Tee/Grün-Tausch wurde beim nächsten Abgleich
  weggeräumt.** Gemeldet als „er speichert es nicht, er überschreibt es mit alten Werten" — und
  genau das tat er. `geoEdSwapTeeGreen` war die **einzige** Kartenänderung ohne
  `geoEdSnapshot()`. Damit fehlte nicht nur das Rückgängig, sondern vor allem der
  **`geoAt`-Stempel**: Ohne ihn sind die Stempel beider Seiten gleich, `_mergeCourses` fällt auf
  „die längere Fassung gewinnt" zurück, und auf einem Platz, auf dem lokal etwas gelöscht wurde
  (Walderkennung, Uferlinien), ist die Repo-Fassung länger. Sie ersetzte die **ganze** `geo` —
  den Tausch mit ihr. **Derselbe Fehler wie v3.68, nur umging der Tausch dessen Reparatur, weil
  er nie stempelte.** Dritter Verlust obendrauf: ohne `watchFilePushSoon()` behielt die Uhr die
  alte Ausrichtung und zeigte auf getauschten Löchern weiter aufs falsche Ende.
  **Drei Fehler derselben Familie gleich mit:**
  · `applyGeoOverrides` konnte nur „getauscht" ausdrücken, nie „nicht mehr getauscht"
  (`if(o.swap)` ohne `else`). Ein aufgehobener Tausch kam über Rückgängig zurück, weil
  `geoEdUndo` die Korrekturen wiederherstellt, die Marke in `holes[n]` aber stehenblieb. Jetzt
  folgt `holes` den Korrekturen in BEIDE Richtungen.
  · Das Werkzeug „Grünmitte" setzte `overrides.holes[n]={green:pt}` und löschte damit eine
  gesetzte Tee-Korrektur UND die Tausch-Marke gleich mit, still und ohne Meldung. Das ist wörtlich
  der Fehler, den v3.85 im Zieh-Pfad behoben hat — dieser Zweig wurde damals übersehen. Beide
  nehmen jetzt `Object.assign`.
  · **Weg A des Imports stempelte gar nicht.** `geoFetchOSM` setzte weder `geoAt`, noch löschte es
  `geoDeletedAt`, noch rief es `watchFilePushSoon()` — Weg B (`geoImportText`) tut alle drei seit
  je. Eine frisch von OpenStreetMap geladene Karte galt im Abgleich damit als älter als ihre
  eigene Löschung und konnte sofort wieder verschwinden.
  **Merksatz:** Wer die Karte ändert, ruft `geoEdSnapshot()` — ohne Ausnahme. Die Doku behauptete
  seit v2.67 „vor JEDEM Eingriff"; der Satz stimmte fast. Ein Absatz, der halb stimmt, ist
  schlechter als keiner.
  **Doku:** Abschnitt 4 (bekannte Lücke in `idbHydrate`), 10 (Import-Pipeline, `overrides`-Felder,
  die drei Pflichten von `geoEdSnapshot`), 26.5 (`courses`-Sonderfall, die drei erlaubten
  `geoAt`-Stellen).

- **v3.90.0 · 2026-08-20** — **BUGFIX: „Verschieben" verschob nur die Karte, nie ein Objekt.**
  Dreimal gemeldet, und beim dritten Mal habe ich es gefunden — es lag nicht an den Griffen. Die
  gibt es, sie stehen im Bild und die Aufnahme-Logik funktioniert. **Das Handwerkzeug ✋ stieg aus,
  BEVOR es nach einem Objekt suchte.** Es schob ausschließlich das Kartenbild.
  Besonders ärgerlich: Ich habe dir zuletzt geraten, „mit ✋ Verschieben" zu ziehen — also
  ausgerechnet den einen Modus empfohlen, in dem es nicht gehen konnte. Der Hinweistext sagte es
  sogar ausdrücklich: „Nichts wird gesetzt, gezogen oder gelöscht." Ich habe ihn gelesen und nicht
  verstanden, dass er das Problem beschreibt.
  Der Grund stand in v2.86: Das Handwerkzeug sollte der verlässliche Weg sein, wenn Flächen dicht
  liegen — kein versehentliches Aufnehmen. **Richtig für Flächen, falsch für Griffe:** Einen Griff
  fasst man nur an, wenn man ihn genau trifft, und man greift ihn *nur*, um ihn zu bewegen. Jetzt
  wird ein Griff aufgenommen, alles andere schiebt weiter die Karte; das Halten (320 ms) bleibt,
  also wandert nichts aus Versehen.
  **Eine Ungleichheit dabei beseitigt:** Mein Abschlag-Griff aus v3.85 verlangte ein *gewähltes*
  Loch, der am Grün nicht. In der Gesamtansicht war das Grün also beweglich und der Abschlag nicht —
  zwei gleich aussehende Punkte, einer beweglich. Genau der Zustand, den v3.85 beseitigen sollte.
  **Merksatz:** Wenn ein Werkzeug „Verschieben" heißt und man etwas Verschiebbares anfasst, muss es
  sich bewegen. Alles andere ist ein gebrochenes Versprechen — und der Benutzer sucht den Fehler
  bei sich.

- **v3.89.0 · 2026-08-20** — **BUGFIX: Ein toter Variablenname legte den ganzen Editor lahm.**
  Im Protokoll stand es klar: `ReferenceError: mi is not defined` in `geoEdVertHandles`, bei **jeder**
  Einzelauswahl. In v3.75 habe ich `const mi=sel[0], m=(geo.mine||[])[mi]` durch `geoEdSelObj(...)`
  ersetzt — und `mi` weiter unten stehen gelassen. Weil der Fehler bis ins Neuzeichnen hochschlägt,
  **brach der Editor danach komplett ab**: Auswahl, Löschen, alles. Von außen sah es aus, als ließen
  sich Objekte „nicht auswählen" — der Grund war ein Absturz drei Zeilen weiter.
  **Beim Umbenennen einer Variablen reicht es nicht, die Zuweisung zu ersetzen.** Der Prüflauf hat
  es nicht gefunden, weil er den Quelltext liest und diese Funktion ein DOM braucht. Jetzt prüft er
  den Quelltext auf **Namen ohne Deklaration** — das geht ohne DOM und hätte den Fehler gefangen.
  **Gleich mitgenommen:** Die Eckpunkt-Griffe sprechen jetzt ebenfalls Schlüssel (`vert:m3:2`), also
  bekommen auch importierte Flächen Eckpunkte. Vorher hätte ein Eckpunkt einer importierten Fläche
  das gleichnamige eigene Objekt getroffen.
  **Und die zwei Punkte auf Bahn 1:** Das sind **Abschlag und Grünmitte**. Sie stehen in keiner
  Objektliste — sie **definieren** das Loch; ohne sie gibt es keine Länge, keinen Plan und keine
  Empfehlung. Löschen wäre also falsch. Bisher bekam man darauf aber dieselbe nichtssagende Antwort
  wie auf jeden Fehlgriff („Nichts in der Nähe"), und wer zweimal danebentippt, hält das Werkzeug
  für kaputt. Jetzt nennt die Meldung den Punkt beim Namen und sagt, was **stattdessen** geht:
  mit ✋ Verschieben bewegen.

- **v3.88.0 · 2026-08-18** — **Das Langdrück-Menü blockiert die Bearbeitung nicht mehr.**
  „Bild kopieren / herunterladen / teilen" legte sich über die Karte, sobald man länger drückte —
  und **Langdrücken ist ausgerechnet die Geste zum Verschieben**. Das Satellitenbild liegt
  eingebettet in der Karte, also greift der Browser zu, bevor der Editor die Geste überhaupt sieht.
  **Der Schutz existierte seit v2.60.1 — aber unvollständig.** Die Regel deckte `#geoedSvg` und das
  SVG ab, **nicht die Elemente darin**. Das Bild liegt als `<image>` im SVG, und der Browser fragt
  das **gedrückte** Element, nicht dessen Vorfahren. Jetzt gilt die Regel für alle Kinder, und das
  Bild fängt keine Berührungen mehr ab.
  **Dazu ein Abfangjäger für das Kontextmenü**, wie ihn die Spielkarte längst hatte: CSS allein
  reicht nicht, weil die Geräte das Menü unterschiedlich auslösen und die rechte Maustaste am
  Rechner ohnehin einen eigenen Weg nimmt. Zwei Karten, eine Geste — bisher war nur eine geschützt.
  **Bewusst nur im Editor:** Auf der Spielkarte darf man ein Bild weiterhin sichern. Dort zieht
  niemand an Objekten, und ein Verbot wäre reine Bevormundung.

- **v3.87.0 · 2026-08-18** — **Die Fahne steht jetzt vorn, in der Mitte oder hinten — Lücke 3.**
  Front, Mitte und Back stehen seit jeher in der Kopfzeile, aber der Caddy zielte **immer** auf die
  Grünmitte: Im Code stand `const F=null` mit dem Vermerk „Fahnensteuerung entfernt, v1.90".
  `STRAT.approach()` kann eine Fahne seitdem verarbeiten — sie wurde nur nie übergeben.
  **Warum das zählt:** Bei einer Fahne vorn über Wasser ist die Mitte die falsche Antwort, bei einer
  hinten lässt sie einen langen Putt übrig. Das sind die Löcher, auf denen ein Score entsteht.
  Die Position kommt aus der **Grüntiefe**: halbe Tiefe minus 4 m Rand — näher an den Rand steckt
  kein Greenkeeper. Nachgerechnet an einem 30 m tiefen Grün auf 300 m: vorn **291 m**, hinten
  **313 m**.
  **Je Loch gemerkt, nicht global.** Eine Fahnenposition ist eine Eigenschaft *dieses* Grüns an
  *diesem* Tag; ein globaler Schalter wäre nach dem dritten Loch falsch und würde still
  weiterwirken. Und die Fahne steht im Zwischenspeicher-Schlüssel — sonst zeigte die Kette nach dem
  Umschalten das alte Ergebnis.
  **Ohne Wirkung keine Einstellung:** Fehlt der Grünumriss oder ist das Grün flacher als 12 m, wird
  die Auswahl gar nicht erst angeboten. Ein Schalter, der nichts tut, ist schlimmer als keiner.
  **Beim Prüfen gestolpert — und daraus gelernt:** An den laufenden Spielzustand kommt der Prüfstand
  nicht heran (`playBegin` ersetzt das Objekt, die Prüftabelle hält eine Momentaufnahme). Statt die
  Prüfung zu verbiegen, nimmt `pinPunkt` die Fahne jetzt **optional als Argument** und ist damit
  reine Geometrie. Eine Funktion, die sich nur im vollständigen Zustand prüfen lässt, wird eben
  nicht geprüft.

- **v3.86.0 · 2026-08-18** — **Die Werkzeugleiste schiebt sich nicht mehr über die Karte.**
  Sie stand auf `position:sticky;bottom:0` — sie **soll** also am unteren Rand stehen bleiben,
  während der Inhalt darunter durchläuft. Gedacht war das als Hilfe (v2.68: „der einzige Teil, den
  man während der Arbeit ständig braucht"), und auf einem Blatt mit kurzer Karte war es auch eine.
  **Seit v3.84 ist die Karte aber 46 % der Bildschirmhöhe hoch.** Damit verdeckte die klebende
  Leiste dauerhaft deren unteres Drittel — ausgerechnet den Teil, auf dem man arbeitet, wenn man
  nach unten scrollt. **Zwei Maßnahmen, die einander bedingt haben, hoben sich gegenseitig auf**:
  Ich habe der Karte mehr Platz gegeben und ihn im selben Zug wieder zugedeckt.
  Jetzt steht die Leiste normal im Fluss. Nach der Karte ist sie mit einem kurzen Wisch erreichbar
  und verdeckt nichts mehr; auf breiten Schirmen klebte sie ohnehin nie.
  **Merksatz, jetzt im Code:** Ein Element, das etwas anderes überdeckt, muss sich rechtfertigen
  können — und „man braucht es oft" reicht nicht, wenn das Verdeckte das Werkstück ist.

- **v3.85.0 · 2026-08-18** — **Der Abschlag lässt sich jetzt verschieben.**
  Das Grün hatte seit jeher einen Griff zum Ziehen, der Abschlag nur einen **gezeichneten Punkt**.
  Beide sehen gleich aus, man fasst beide an — und nur einer bewegt sich. **Ein Bedienelement, das
  aussieht wie ein anderes und sich anders verhält, ist schlimmer als eines, das offensichtlich
  fehlt**: Man sucht den Fehler bei sich.
  Dabei ist der Abschlag die **wichtigere** der beiden Angaben. Von ihm hängen alle Distanzen und
  der ganze Lochplan ab; ein um 20 m falsch gesetzter Abschlag verschiebt jede Empfehlung auf der
  Bahn. Der Griff hat dieselbe unsichtbare Trefferfläche wie beim Grün — ein 4-px-Punkt ist auf dem
  Telefon nicht zu treffen.
  **Die Speicherung gab es längst:** `applyGeoOverrides` kennt `tee` seit v2.83, und der Wert
  überlebt jeden Neu-Import, weil er in den Korrekturen liegt und nicht in den importierten Daten.
  Es fehlte nur der Griff.
  **Ein stiller Datenverlust nebenbei behoben:** Der alte Zweig setzte beim Verschieben
  `{green:nll}` und löschte damit eine zuvor gesetzte Tee-Korrektur gleich mit. Bei nur einem
  beweglichen Punkt fiel das nie auf — mit zweien wäre es beim ersten Mal passiert.

- **v3.84.0 · 2026-08-18** — **Der Karteneditor gibt der Karte endlich den Platz.**
  Nachgemessen an deinem Bild: Die Lochauswahl umbricht bei 18 Löchern auf **drei Zeilen**, die
  Werkzeuge belegen zwei Reihen plus Hinweiszeile — **der Karte bleibt gut ein Drittel des
  Schirms**. Man arbeitet also auf dem kleinsten Teil und scrollt für jeden Handgriff.
  Das Ärgerliche daran: Genau davor warnt der Kommentar an `renderGeoEditor` schon seit dem letzten
  Umbau („über 400 Bildpunkte, bevor die Arbeitsfläche anfing"). Die Lochauswahl ist seither aber
  auf 18 Chips gewachsen, und damit war der Platz wieder weg. **Eine Regel, die nur zum Zeitpunkt
  des Schreibens stimmt, hält nicht** — deshalb steht sie jetzt als Prüfung im Prüfstand.
  **Drei Eingriffe, nur auf schmalen Schirmen:** Lochauswahl in **eine** seitlich scrollbare Zeile
  (Wischen ist schneller als Zielen, und es spart zwei Zeilen). Die Karte bekommt **46 % der
  Bildschirmhöhe** als Mindestmaß — sie ist das Werkstück, alles andere ist Werkzeug. Und die
  Werkzeuge stehen in einer scrollbaren Reihe statt in zwei Blöcken, flacher und schmaler.
  Auf breiten Schirmen bleibt das Raster: Dort ist Platz genug, und acht nebeneinander wären mit
  der Maus schlechter zu treffen.

- **v3.83.0 · 2026-08-18** — **„Spielt wie" steht oben · Eingabemaske umsortiert.**
  **(1)** Die „spielt wie"-Zeile stand unter Streubildern, Alternativen und Planzeile — also dort,
  wo man auf der Bahn nicht mehr hinsieht. Dabei ist es **die Zahl, mit der man den Schläger
  wählt**: Wind, Temperatur und Höhe ändern die Entscheidung, bevor irgendeine andere Zahl es tut.
  Sie steht jetzt ganz oben, und zwar in **beiden** Zweigen — Rechnung wie Regeln. Wäre sie nur in
  einem oben, müsste man sie je nach Lage an zwei verschiedenen Stellen suchen.
  **(2)** In der Eingabemaske tauschen Abschnitt 3 und 4: **Putten** vor **Details**. Die
  Reihenfolge folgt jetzt dem Ablauf — nach der Annäherung wird geputtet; Strafschläge,
  Bunkeranzahl und Shortsided trägt man nach, wenn überhaupt. Wer von oben nach unten ausfüllt,
  kommt ohne Sprung durch.
  **Und „Kurzes Spiel" heißt jetzt „Details".** Der alte Name war schlicht falsch: Im Golf sind das
  die Schläge um das Grün — Chip, Pitch, Bunkerschlag. In dem Abschnitt stehen aber Strafschläge und
  Zusatzangaben, also gerade **nicht** das kurze Spiel. Ein Name, der etwas anderes meint als das,
  was der Leser kennt, ist schlimmer als ein farbloser — dieselbe Überlegung wie bei „Puffer"
  in v3.40.

- **v3.82.0 · 2026-08-18** — **Löschen im Karteneditor sagt jetzt, was es geprüft hat.**
  Zum dritten Mal gemeldet: Importierte Objekte lassen sich nicht löschen. Nachgeprüft habe ich
  alles, was der Quelltext hergibt — die Suche findet Flächen (`pointInRing`), Linien (Abstand zum
  Segment) und seit v3.80 auch Punkte; im Prüflauf trifft sie eine Teebox als Fläche **und** als
  Punkt zuverlässig. Neu erzeugt werden die Objekte auch nicht: `finalizeGeo` läuft nur beim Import.
  **Damit ist der Quelltext erschöpft — also mache ich den Fehlschlag sichtbar.** „Nichts in der
  Nähe" ist die unbrauchbarste Auskunft, die ein Werkzeug geben kann: Sie sagt nicht, ob nichts
  **da** war, ob es zu **weit** weg lag oder ob gar nicht **gesucht** wurde.
  Die Meldung nennt jetzt das nächstgelegene importierte Objekt mit Art und Entfernung („nächstes
  importiertes Objekt: ‚bunker' in 34 m"), und dieselbe Zeile geht mit den Anzahlen ins Protokoll.
  Auch ein **erfolgreiches** Löschen wird protokolliert — sonst weiß man nicht einmal, ob der Stift
  überhaupt gefeuert hat.
  Beim nächsten Versuch steht damit in einem Blick, welcher der drei Fälle vorliegt.

- **v3.81.0 · 2026-08-18** — **BUGFIX: Der Tipp im Auswahl-Modus traf nur Selbstgezeichnetes.**
  v3.75 hat die Auswahl für importierte Objekte geöffnet — aber **nur das Rechteck**. Der einzelne
  Tipp suchte weiter ausschließlich nach dem Kennzeichen `data-drag`, und das tragen nur selbst
  gezeichnete Objekte. Man tippt auf einen Bunker aus OSM, es passiert nichts, und der Modus wirkt
  kaputt.
  **Eine halbe Fähigkeit ist schlimmer als keine:** Weil das Rechteck funktionierte, war die Ursache
  von außen nicht zu sehen — genau deshalb kam die Meldung „geht immer noch nicht", obwohl ich es
  für erledigt hielt.
  Der Tipp sucht jetzt zuerst das Kennzeichen (eindeutig getroffen), dann über die **Geometrie**:
  Fläche, in der der Punkt liegt → nahe Linie → naher Punkt. Lochlinien bleiben ausgenommen. Trifft
  nichts, sagt der Hinweis, dass ein Rechteck mehrere auf einmal wählt.
  **Zweiter Fehler im selben Zweig:** Übergeben wurde die **rohe Zahl**, obwohl die Auswahl seit
  v3.75 Schlüssel erwartet. Bei eigenen Objekten fand die Suche deshalb nie einen Treffer —
  **Abwählen war unmöglich**, und die Auswahl wuchs mit jedem Tipp weiter. Das ist die Sorte Fehler,
  die eine Umstellung hinterlässt, wenn man die Aufrufer nicht vollständig durchgeht.

- **v3.80.0 · 2026-08-18** — **BUGFIX: Lob Wedge aus 421 m · und Punkt-Objekte sind löschbar.**
  **(1) „Wasser quert bei 12–32 m. Sicher: davor ablegen — LW bis ~69 m."** Aus 421 m ein Lob Wedge,
  weil ein Graben 12 m vor dem Ball liegt. **Vor einem Hindernis abzulegen, dessen ferne Kante bei
  32 m liegt, ist unmöglich** — der kürzeste Schläger im Bag trägt weiter.
  Die Ursache: `goForIt` war in „sicher" **nie** wahr. Die Spielweise entschied über den Angriff,
  **bevor** jemand gefragt hatte, ob es überhaupt etwas zu entscheiden gibt. Vorsicht ist kein
  Selbstzweck; sie greift nur, wo ein echtes Risiko steht.
  Zwei Prüfungen fehlten: **Trivialer Carry** — trägt schon der kürzeste Schläger über die ferne
  Kante, ist die Gefahr für diesen Schlag keine. **Layup unmöglich** — liegt das Ziel vor der nahen
  Kante unter dem kürzesten Schläger, ist „davor ablegen" ein Widerspruch. In beiden Fällen entfällt
  die Sonderbehandlung, und der normale Plan greift — der die Gefahr über das Lie-Raster ohnehin
  kennt.
  Nachgerechnet: Aus 421 m mit Wasser bei 12–32 m sagt der Caddy jetzt **3 Wood 213 → 3 Wood 208** —
  genau das, was die Kette auf der Karte zeichnet. Bei Wasser bei 170–205 m legt er weiterhin vor.
  **(2) Punkt-Objekte ließen sich nicht löschen.** Der Löschstift prüfte nur Ringe und Linien. Aus
  OSM kommen aber viele Dinge als **Punkt** — eine Teebox, ein einzelner Baum, ein Schild. Man tippt
  genau darauf und bekommt „Nichts in der Nähe". Jetzt werden Punkte mitgesucht, mit 20 m Radius
  (ein Punkt hat keine Ausdehnung und ist auf dem Telefon nicht genauer zu treffen) und **zuletzt**:
  Wer auf einen Bunker mit einem Baum darin tippt, meint meistens den Bunker.

- **v3.79.0 · 2026-08-18** — **BUGFIX: Der Modus-Vergleich im Gameplan verglich Äpfel mit Birnen.**
  Im Bild stand „sicher **76,0** · normal **76,7 (+0,8)** · offensiv **76,0 (+0,0)**". Zwei Modi
  exakt gleich, der mittlere schlechter als beide Ränder — das passt zu keiner Strategie, und dein
  Verdacht war richtig.
  **Die Ursache liegt nicht in der Bewertung, sondern in der Bezugsmenge.** Nur der **angezeigte**
  Modus wird voll gerechnet; die anderen beiden nur bis zur Abschlagsebene, damit das Öffnen schnell
  bleibt. Und die grobe Rechnung lässt die **Par-3-Löcher** ohne Erwartungswert — dort entsteht er
  erst in der vollen Rechnung. Nachgemessen an einem Testplatz: grob **7** Löcher mit Wert, voll
  **9**. Auf deinem Platz mit vier Par 3 wurden also 14 Löcher gegen 18 verglichen, und die Klammer
  behauptete, Vorsicht koste 0,8 Schläge.
  **Jetzt zählt der Vergleich nur die Löcher, für die alle drei Spielweisen einen Wert haben** — die
  Schnittmenge. Die Überschrift nennt die Lochzahl („Erwartete Schläge über 14 Loch"), und wenn der
  angezeigte Plan mehr Löcher abdeckt, steht dabei, wie viele nicht in den Vergleich eingehen und
  warum.
  **Fehlt einem Modus die Grundlage ganz, wird der Vergleich abgeschaltet** statt geraten — lieber
  keine Zahl als eine falsche. Das ist derselbe Grundsatz wie beim Fehlerprotokoll.

- **v3.77.1 · 2026-08-18** — **Zwei Hilfsfunktionen nachdokumentiert.**
  Die Selbstprüfung meldete `geoEdSelObj` und `geoEdSelParse`. Im Referenzabschnitt stand nur
  `geoEdSelKey` — ich hatte die drei als Familie geschrieben, aber nur eine davon beim Namen
  genannt, und die Prüfung liest Namen, keine Familien. Jetzt stehen alle drei mit ihrer Aufgabe da:
  Schlüssel bauen, Schlüssel lesen, Objekt auflösen.

- **v3.77.0 · 2026-08-18** — **Eine falsche Warnung entfernt · und „Grün 0 %“ bekommt eine Zahl.**
  **(1) Die Linien-Warnung war seit v3.72 falsch.** Sie sagte: „Strafgebiet/Aus als LINIE gezeichnet
  — der Caddy wertet nur Flächen aus, diese Grenzen fließen NICHT in die Empfehlung ein. Als
  geschlossene Fläche neu zeichnen." Das stimmte bis v3.71. Seit v3.72 werden Aus-Linien in eine
  Halbebene und Strafgebiets-Linien in ein Band übersetzt — sie **wirken**. Die Warnung riet also
  dazu, etwas neu zu zeichnen, das längst funktioniert.
  **Eine falsche Warnung ist schlimmer als keine:** Sie kostet Arbeit und Vertrauen. Wer eine
  Fähigkeit nachrüstet, muss die Warnung mitnehmen, die ihr Fehlen erklärt hat — das ist mir hier
  entgangen, und der Prüfstand hält es jetzt fest.
  **(2) „Grün 0 %“ mit Ziel mitten auf dem Grün** — die Diagnose meldete 0 m vom Grünpunkt und 1 m
  vom Mittelpunkt der Fläche. Wenn das Ziel stimmt und die Quote trotzdem null ist, bleibt als
  Erklärung nur die **Größe** der gezeichneten Fläche: Ein zu kleines Grün trifft die Streuung fast
  nie. Neu ist deshalb `ringFlaecheM2()` — die Meldung nennt jetzt die Fläche in m², und unter
  **150 m²** warnt die App von sich aus („ein Grün hat 400–800 m²"). Nachgerechnet: 14 m Halbmesser
  ergeben 784 m², 5 m nur 100 m².
  Das gilt unabhängig von der auffälligen Null — ein zu klein gezeichnetes Grün verfälscht **jede**
  Trefferquote.

- **v3.76.0 · 2026-08-18** — **Vier Lücken der Caddy-Logik geschlossen.**
  **(1) Wind, Temperatur und Höhe wirken jetzt auf die Zielkette.** `playsLike()` gab es seit
  Langem, aber **nur der heuristische Caddy** hat es benutzt — die EV-Engine rechnete mit roher
  Traglänge. Auf dem Schirm stand „spielt wie 281 m (+7)" über einer Kette, die mit 274 m plante.
  Wieder zwei Rechnungen für dieselbe Sache.
  Umgerechnet wird die **Umkehrung**: `playsLike` sagt, wie weit sich eine Strecke anfühlt — für den
  Schläger brauchen wir, wie viel Boden er deckt (`carry² / playsLike`). Nachgerechnet mit 8 m/s:
  Rückenwind **225 m**, Gegenwind **189 m** statt 211. Gerechnet wird je Kandidat, nicht je Sample,
  und ohne Wetterdaten ändert sich **nichts**.
  **(2) Die eigene Lage verkürzt den Schlag.** Aus dem Rough plante die Engine mit voller Länge —
  der häufigste Fall einer Runde. Keine neue Eingabe nötig: Die App **weiß** über `lieCode`, wo du
  stehst. Rough 0,90 · Recovery 0,80 · Bunker 0,75. Ein Schalter wäre ein Bedienschritt auf jeder
  Bahn und eine Fehlerquelle dazu.
  **(4) Der verlorene Ball im Wald.** Nach den Regeln gilt dort Schlag und Distanz wie beim Aus;
  die Recovery-Kurve allein war zu günstig. Mit **25 %** Wahrscheinlichkeit ist er weg — aus 150 m
  kostet der Wald damit **4,64** statt 4,39 Schläge. Bewusst vorsichtig: Zu hoch angesetzt würde
  jeder Wald zur Sperrzone, und der Caddy empfähle Umwege, die kein Mensch spielt.
  **(5) Dogleg.** Die Zielrichtung zeigte auf `line[1]` — den zweiten Punkt der Lochlinie. Bei
  sauber gezeichneten Bahnen ist das der Knick, bei importierten oft ein **Stützpunkt 30 m vor dem
  Abschlag**; dann zielte die ganze Bewertung entlang der Tee-Box. Jetzt wird der erste Punkt
  **ab 120 m** genommen — auf einer geraden Bahn ist das unverändert das Grün.

- **v3.75.0 · 2026-08-18** — **Die Auswahl im Karteneditor erfasst jetzt auch importierte Objekte.**
  Auswahl und Löschen waren auf `geo.mine` beschränkt — also auf selbst Gezeichnetes. Man zieht ein
  Rechteck über falsche OSM-Objekte, **es passiert nichts**, und man hält den Editor für kaputt.
  Das ist dieselbe Lücke wie beim Löschen-Stift in v3.68, nur an der **zweiten** Stelle: wieder eine
  Fähigkeit, die es an zwei Orten geben muss und nur an einem gab.
  **Schlüssel statt Index:** Ein Eintrag heißt jetzt `"m3"` (selbst gezeichnet) oder `"f12"`
  (importiert). Zwei Zahlenräume in einer Liste wären ohne Kennzeichen nicht auseinanderzuhalten —
  und ein falsch aufgelöster Index löscht das falsche Objekt. Der Buchstabe kostet nichts und macht
  den Fehler unmöglich.
  **Gelöscht wird je Quelle absteigend.** Eine gemeinsame Sortierung wäre falsch: `m7` und `f7`
  meinen verschiedene Listen, und ein Löschen in der einen verschiebt die Indizes der anderen nicht.
  **Lochlinien bleiben ausgenommen** — sie sind das Gerüst des Platzes. Wer ein Rechteck über eine
  Bahn zieht, will Bäume und Bunker treffen, nicht das Loch selbst.
  **Sichtbar wird die Auswahl über `data-feat`**, das `courseSVG` **nur im Editor** setzt: Ohne
  Kennzeichen im SVG kann man eine Auswahl aus importierten Objekten nicht markieren — und einer
  unsichtbaren Auswahl traut niemand. Die Spielkarte bleibt unverändert.

- **v3.74.0 · 2026-08-18** — **Der Caddy kennt jetzt die Flughöhe — kein 2er Eisen mehr über den
  Wald.**
  Der Einwand war richtig: Die **Länge** reichte, die **Höhe** nicht. `blocked()` prüfte nur, ob
  etwas **in der Linie** steht; ob der Ball **darüber** fliegt, war nie eine Frage. Ein Wedge aus
  40 m wurde damit genauso geblockt wie ein langes Eisen — und ein langes Eisen genauso
  durchgelassen wie ein Wedge.
  **Was die Daten sagen:** Auf der Tour liegen die Scheitelhöhen aller Schläger erstaunlich dicht
  beieinander — zwischen Driver und Pitching Wedge nur wenige Meter. Das gilt aber **nur bei
  Tour-Geschwindigkeit**. Bei langsamerem Schlägerkopf fliegen die niedrig gelofteten Schläger
  deutlich flacher; genau deshalb ist das 2er Eisen für die meisten der schwerste Schläger im Bag —
  und stand schon als „nur vom Abschlag" in den Leitplanken.
  **Das Modell** ist bewusst einfach: Scheitelhöhe als Anteil der Traglänge je Loftklasse, die Bahn
  als Parabel mit dem Scheitel bei 55 %. Damit kommt ein Driver mit 211 m Carry auf **23 m**, ein
  2er Eisen mit 180 m auf **16 m**, ein Wedge mit 100 m auf **22 m** — das richtige Verhältnis, und
  darauf kommt es an.
  **Nachgerechnet am gemeldeten Fall:** Ein 15-m-Wald in 80 m Entfernung — das 2er Eisen kommt
  **nicht** darüber, Driver, Holz, Hybrid und alle Eisen ab dem 5er schon. Und bei einem 12-m-Baum
  in 60 m wird jetzt **nur** das 2er Eisen bestraft.
  **Ein eigener Fehler beim Bauen**, vom Prüfstand gefangen: „2 Driving Iron" fiel durch die
  Einordnung, weil zwischen Ziffer und „Iron" ein Wort steht — ausgerechnet der Schläger, um den es
  ging, wurde als Mitteleisen geführt und kam auf 27 m Scheitel statt 16.
  **Grenzen offengelegt** (Abschnitt 23b): Baumhöhen stehen selten in den Kartendaten — ohne Angabe
  gelten 12 m für einen Einzelbaum und 15 m für Wald. Und die Parabel ist eine Schätzung; echte
  Bahnen fallen hinten steiler ab, aber für „komme ich über den Baum" zählt der vordere Teil.

- **v3.73.0 · 2026-08-18** — **Das Caddy-Regelwerk steht jetzt an einer Stelle — mit Lückenliste.**
  Die Regeln lagen über `STRAT`, `caddyPlan`, `LEITPLANKEN` und `SPIELWEISE` verteilt. Wer prüfen
  wollte, ob eine Golfregel abgebildet ist, musste vier Stellen lesen — und genau deshalb ist
  jahrelang niemandem aufgefallen, dass Linien gar nicht mitzählten.
  **Neuer Abschnitt 23b** listet auf, was in die Bewertung eingeht: Lagen mit eigenen
  Erwartungskurven (auch **Wald** als `recovery`), Strafgebiet **+1** nach Regel 17, Aus **+2** nach
  Regel 18.2, Bäume in der Linie, Wind und Temperatur, eigene Streuung, Spielweise, Wedge-Zone,
  Leitplanken und die zweite Ebene am Abschlag.
  **Und — genauso wichtig — was er NICHT kennt:** rote gegen gelbe Strafgebiete werden gleich
  behandelt; Aus wird als „+2 am Ort" genähert statt als Schlag und Distanz vom vorigen Ort (für
  den Abschlag gut, für den zweiten Schlag zu günstig); ein **verlorener Ball außerhalb eines
  Strafgebiets** kostet nach den Regeln ebenfalls Schlag und Distanz, der Caddy rechnet dort nur mit
  der Recovery-Kurve; unspielbarer Ball, Wegerelief, Bunkerkante, Böen und Platzregeln fehlen ganz.
  Diese Liste verhindert, dass jemand eine Genauigkeit annimmt, die es nicht gibt.
  **Der Prüfstand vergleicht die Doku mit dem Code:** Die genannten 4,39 gegen 3,40 Schläge für
  Wald gegen Fairway aus 150 m werden gegen `STRAT.lookup` geprüft. Eine Doku, die etwas anderes
  sagt als der Code, ist schlimmer als keine.

- **v3.72.0 · 2026-08-18** — **REGELFEHLER BEHOBEN: Linien waren für den Caddy unsichtbar.**
  Der Einwand war fachlich richtig, und die Regeln sind eindeutig.
  **Aus (Regel 18.2) ist eine GRENZE, keine Fläche.** Alles außerhalb der Grenzlinie ist Aus —
  unabhängig davon, wo genau der Ball liegt. Wer die Linie überfliegt und dahinter landet, zahlt
  Schlag und Distanz. Eine Linie hat kein „Innen", also übersprang die Abtastung sie: Der Caddy
  plante an einem Platzrand entlang, als gäbe es ihn nicht.
  **Und ein Strafgebiet darf als Linie vorliegen.** Die Regeln kennen den Fall ausdrücklich: Ist der
  Rand eines Gewässers nicht markiert, gilt die **natürliche Grenze** — also der Bach selbst. Aus
  OSM kommt genau das: ein Wasserlauf als Linie.
  **Beide werden jetzt in Flächen übersetzt**, damit die vorhandene Punkt-in-Fläche-Prüfung
  unverändert greift und es weiterhin nur **einen** Weg gibt, auf dem Gefahren bewertet werden:
  Die Aus-Linie wird zur **Halbebene** — 120 m nach außen versetzt und geschlossen; welche Seite
  außen ist, entscheidet die Lage der Spiellinie, denn der Platz ist immer die Seite, auf der
  gespielt wird. Die Strafgebiets-Linie wird zu einem **Band** von 8 m beidseits, was einem Bach mit
  Böschung entspricht — die Regeln setzen den Rand dort, wo der Boden zur Mulde abfällt, nicht an
  der Wasserkante.
  **Dazu musste das Raster weiter reichen.** Es deckte 45 m neben der Bahn ab — genug für die
  Streuung (2σ ≈ 30 m quer), aber der Ball, der ins Aus fliegt, liegt **weiter** draußen, und
  Punkte außerhalb des Rasters werden nicht bewertet. Mit Aus in der Nähe reicht es jetzt bis 110 m.
  **Nachgerechnet, und die Wirkung ist deutlich:** Bei einem Aus 18 m rechts der Bahn zielt der
  Caddy jetzt 4° nach links und weist 2,7 % Risiko aus. Und bei einem Bach quer über die Bahn
  wählt er in allen drei Spielweisen das **7 Iron** und legt davor — vorher hätte er den Driver
  hineingespielt, weil die Linie für ihn nicht existierte.
  **Breiten bewusst klein gehalten:** Zu großzügig gezogen würde jede Empfehlung vom Platzrand
  weggedrängt. Wer es genauer braucht, zeichnet eine Fläche.

- **v3.71.0 · 2026-08-18** — **Der Gefahren-Knopf ließ Linien stehen — und die zählen für den Caddy
  gar nicht.**
  **(1) Der Filter fehlte an einer zweiten Stelle.** Flächen und Linien werden in **zwei getrennten
  Durchläufen** über dieselbe Liste gezeichnet; die Gefahrensperre stand nur am ersten. Der Knopf
  blendete also Wasserflächen aus, die selbst gezeichneten OB- und Penalty-**Linien** aber nicht —
  von außen sah es aus, als täte er nichts. **Merksatz:** Wer eine Liste an zwei Stellen durchläuft,
  muss beide Stellen filtern. Dieselbe Fehlerklasse wie „eine Regel an zwei Orten".
  **(2) Und die eigentliche Antwort auf deine Frage: Nein, Linien sind für den Caddy nicht
  vorhanden.** Die Risikorechnung fragt für jeden simulierten Ball, ob er **innerhalb** einer Fläche
  liegt. Eine Linie hat kein Innen.
  Nachgerechnet an einem 300-m-Loch mit Strafgebiet quer bei 200 m:
  · als zwei **Linien** gezeichnet → Empfehlung **Driver**, mitten hinein
  · als **Fläche** gezeichnet → Empfehlung **7 Iron**, davor gelegt
  Das ist keine Feinheit, sondern der Unterschied zwischen einer Warnung und einem Strafschlag. Wer
  eine Grenze zeichnet, meint eine Gefahr — also sagt die App jetzt, dass die Form nicht reicht:
  eine Warnung je Loch mit der Zahl der betroffenen Linien und der Abhilfe.

- **v3.70.0 · 2026-08-18** — **Die beiden Hinweise der Selbstprüfung abgeräumt.**
  **`simToggle` entfernt.** Ich hatte sie beim Bau des Simulationsmodus angelegt, dann aber
  `simFrage()` gebaut — die fragt vor dem **Start** nach und beendet **ohne** Rückfrage. Damit war
  `simToggle` nie angebunden. Eine Funktion ohne Aufrufer sieht bei der nächsten Durchsicht wie ein
  vergessener Anschluss aus und wird „wieder verdrahtet"; genau das war `playGoHole` in v3.37.
  **Die Platz-Modus-Familie steht jetzt in der Referenz** (`platzModus/setPlatzModus/
  applyPlatzModus`). Sie wurde gemeldet, seit der Schalter im Spielmodus in v3.41 entfiel — der
  Modus selbst gibt es weiter unter Mehr → Darstellung, nur stand er nirgends beschrieben.
  **Und drei Namen aus der Sperrklinke gestrichen**, die es nicht mehr gibt (die `pfWiz`-Familie und
  `playGoHole`) — in `index.html` **und** in `tests.js`, wo eine zweite Kopie derselben Liste liegt.
  Eine Sperrklinke, die auf Verschwundenes zeigt, wächst sonst still zu einer Liste, der niemand
  mehr traut.
  **Dabei kam ein stiller Formatfehler ans Licht:** Die Teilstücke der Liste waren teilweise **ohne
  Leerzeichen** aneinandergehängt. Dadurch klebte der letzte Name einer Zeile am ersten der
  nächsten, und **beide verschwanden aus der Liste** — 400 Namen im Quelltext, aber nur **356** in
  der ausgewerteten Fassung. Die 44 verlorenen wurden seither als „neu undokumentiert" gemeldet,
  obwohl sie längst eingetragen waren. Genau deshalb standen `_distToLine` und `_dl` in den
  Hinweisen. Die Liste ist neu aufgebaut, alphabetisch, ohne Doppelte — und der Merksatz steht
  darüber.

- **v3.69.0 · 2026-08-18** — **Vier Erfolge standen als Fehler im Protokoll.**
  Im Protokoll stand „✕ gpAutoRefresh · **Plan erneuert**" — mit Aufrufstapel, neben echten
  Abstürzen. Gemeldet wurde das über `logErr` mit einem eigens dafür gebauten `new Error`. Dasselbe
  bei drei einmaligen Umstellungen (Wiki-Kategorien, ergänzte Tests, Schwungdaten).
  **Das ist genau das Rauschen, das ein Protokoll unbrauchbar macht.** Wer zwischen fünf gemeldeten
  „Fehlern" erst prüfen muss, welche überhaupt welche sind, sieht beim sechsten Mal gar nicht mehr
  hin — und dann steht der echte Absturz mitten in der Menge und wird übersehen.
  Alle vier melden jetzt als **Warnung**: Sie bleiben im Protokoll, weil sie erklären können, warum
  sich Empfehlungen geändert haben, aber ohne ✕ und ohne Aufrufstapel. Der Merksatz steht bei
  `logWarn`: `logErr` ist für Dinge da, die **nicht funktioniert haben**.
  **Bewusst nicht geändert:** `draftPush` mit HTTP-Code. Dort ist wirklich etwas fehlgeschlagen,
  auch wenn ein Wiederholungsversuch folgt.

- **v3.68.0 · 2026-08-18** — **BUGFIX: Gelöschte Kartenobjekte kamen durch den Abgleich zurück —
  du hattest recht.**
  Ich hatte geantwortet, es sei kein Sync-Problem, sondern eine fehlende Fähigkeit. **Beides war
  falsch bzw. unvollständig** — es waren zwei Fehler übereinander, und der zweite ist genau der,
  den du vermutet hast.
  **(1) Der Abgleich machte jede Löschung rückgängig.** Ein Platz trägt keinen eigenen Zeitstempel,
  also fällt `_mergeArr` auf die Heuristik „der **vollständigere** Eintrag gewinnt" zurück — sie
  schützt sonst vor Datenverlust. Wer ein Objekt aus der Karte löscht, macht seinen Eintrag damit
  **kürzer** und verliert zuverlässig gegen die Fassung im Repo. Beim nächsten Abgleich standen die
  Linien wieder da.
  Das ist derselbe Fehler wie in v2.84, nur eine Ebene tiefer: dort die **ganze** Karte, hier
  **einzelne Objekte** darin. Und die Lösung ist dieselbe — die Löschung als **Datum** ausdrücken.
  `geoAt` gibt es längst, jede Bearbeitung stempelt; es wurde nur nie gefragt, wenn es darum ging,
  *welche* Karte gewinnt. Jetzt gewinnt die **jüngere**. Bei gleichen oder fehlenden Stempeln bleibt
  die alte Heuristik — sie schützt, wenn eine Seite nie bearbeitet wurde. Im Worker gespiegelt, wie
  es die Regel aus v2.84 verlangt.
  **(2) Importiertes ließ sich gar nicht löschen.** Der Löschen-Stift durchsuchte nur `geo.mine`,
  also Selbstgezeichnetes. Alles aus dem OSM-Import war unantastbar: Man tippt darauf, bekommt
  „Nichts in der Nähe". Jetzt erreicht er auch `geo.features` — Selbstgezeichnetes zuerst, weil man
  meistens die eigene Arbeit meint. Der Hinweis sagt dazu, dass ein neuer Import es zurückholt.
  **Fachlich zum Fluss:** Zwei Uferlinien sind für die Rechnung ohnehin **unsichtbar** — sie sammelt
  Gefahren über `f.ring`, also geschlossene Flächen, und prüft Treffer mit „liegt der Ball darin".
  Eine Linie hat kein Innen. Für ein Querhindernis gehört **eine Fläche von Ufer zu Ufer**.

- **v3.67.0 · 2026-08-18** — **Gefahren lassen sich ausblenden — nur im Bild, nicht in der Rechnung.**
  Wer Strafgebiete und Aus selbst eingezeichnet hat, hat schnell mehr Umrisse als Luftbild auf dem
  Schirm — und will beim Nachsehen die **Bahn** erkennen, nicht die eigenen Striche. Neuer Knopf 🌊
  in der Kartenleiste, gleiches Muster wie 🌲 für Wald und Bäume. Er greift auf **beide** Quellen:
  importierte Flächen und selbst gezeichnete. Wer ausblendet, will sie weghaben, nicht die halbe
  Menge.
  **Bewusst nur die Anzeige.** Caddy, Gameplan, Streuungsrechnung und Warnungen rechnen unverändert
  weiter. Ein Schalter, der die Bewertung mitveränderte, wäre ein Kartenfehler mit Ansage: Man
  blendet etwas aus, um besser zu sehen, und bekommt stillschweigend eine andere Empfehlung. Der
  Prüfstand hält fest, dass die Bewertung mit und ohne Anzeige **auf vier Nachkommastellen
  identisch** bleibt, und der Hinweistext sagt es dazu.

- **v3.66.0 · 2026-08-17** — **Zwei Befunde aus deinem Protokoll — einer davon war mein Fehler.**
  **(1) Die Grünflächen-Warnung war falsch.** Sie suchte die nächstgelegene Grünfläche im Umkreis
  von 120 m und meldete deren Abstand. Auf einem verschachtelten Platz ist die nächste Fläche aber
  oft die des **Nachbarlochs** — im Protokoll stand deshalb „Loch 9: Grünpunkt liegt 116 m neben der
  Grünfläche", während **zeitgleich** „das Grün von Loch 18 liegt nur 72 m entfernt" gemeldet wurde.
  Beide Zahlen beschrieben dasselbe: Ich hatte ein fremdes Grün erwischt. Sechs solcher Meldungen
  (Loch 6, 9, 11, 14, 16, 18) waren Fehlalarm.
  Richtig ist `greenRingFor()` — die Fläche, die den Punkt **enthält**, sonst die nächste innerhalb
  von 30 m, sonst keine. Gemeldet wird jetzt das **Fehlen** einer Zuordnung, denn das ist der Fall,
  der wirklich weh tut: keine Front/Back-Werte und keine Grün-Trefferquote („Grün 0 %").
  Die Nachbargrün-Meldung bleibt — sie war richtig und ist sogar der Schlüssel zu meinem Fehler.
  **(2) Die Layup-Rücknahme ist entfallen.** Sie stand seit v3.51 in der Zielkette und zog den
  Zielpunkt bis auf ~105 m Rest zurück. Zwei Gründe: Sie war eine **zweite Regelstelle** — genau
  davor warnt Abschnitt 0a, und seit v3.62 stehen die Regeln in `LEITPLANKEN`, die `nextShot()`
  ohnehin anwendet. Und sie **korrigierte zu weit**: Der Platz-Durchlauf hat in v3.64 gezeigt, dass
  „näher ist besser" zwischen 25 und 85 m messbar stimmt. Im Protokoll stand das als „Layup ließ nur
  9 m übrig — auf 112 m zurückgenommen", auf den Löchern 2, 13, 14 und 15.
  **Zu den HTTP-502-Meldungen:** Die kommen von GitHub, nicht aus der App — der Worker bekam die
  Datei zeitweise nicht. Die vier Anläufe mit Pause haben getan, was sie sollen; nach dem letzten
  wurde ehrlich gemeldet statt still verworfen.

- **v3.65.0 · 2026-08-17** — **Die Prozentzahlen im Plan sagen jetzt, was sie sind.**
  „FW 49 %" und „Grün 75 %" standen ohne Erklärung da. Beide sind **Trefferquoten**: Von 1000
  simulierten Bällen mit deiner Streuung landen so viele auf dem Fairway bzw. auf dem Grün.
  **Ohne diese Auskunft liest man sie leicht falsch** — nämlich als „zu 49 % der richtige Schläger",
  also als Vertrauen in die Empfehlung statt als Ergebnis des Schlags. Das ist ein großer
  Unterschied, und er führt in die falsche Richtung: **49 % Fairway ist für einen Driver ein
  normaler Wert, kein Warnzeichen.** Deshalb steht die Einordnung mit in der Legende; eine Zahl ohne
  Maßstab beunruhigt nur.
  Neu sind eine **Spaltenüberschrift** („Schlag · Schläger · Weite · Trefferquote") und eine
  **Legende** unter der Liste. Sie nennt nur die Spalten, die auch vorkommen — auf einem Par 3 gibt
  es keine Fairwayquote, und eine Überschrift für eine leere Spalte verwirrt mehr, als sie hilft.
  Beides steht **einmal** über bzw. unter der Liste, nicht in jeder Zeile: In jeder Zeile wäre es
  Lärm, und die Spalten sind ohnehin gleich.

- **v3.64.0 · 2026-08-17** — **Leitplanken justiert — und zweimal von den Daten widerlegt.**
  Die Architektur steht (v3.62): Regeln wirken **innerhalb** der Rechnung, nicht daneben — harte
  Regeln streichen einen Schläger, weiche schlagen Bruchteile eines Schlages auf, und heraus kommt
  **eine** Bewertung. Belegt ist sie durch die Praxis der anderen: Arccos zeigt eine Empfehlung aus
  einem gelernten Modell ohne Regelebene; DECADE gibt Regeln aus, aber ausdrücklich als
  **Stellvertreter** für eine Erwartungsrechnung, die ein Mensch auf der Bahn nicht anstellen kann.
  Eine Regel ist also die Kurzfassung der Rechnung — und wir haben den Rechner.
  **Zwei Korrekturen heute, beide vom Platz-Durchlauf erzwungen:**
  **(1) Die Regel maß etwas anderes als die Engine.** Sie schätzte den Rest aus dem reinen Carry,
  die Engine misst ihn vom **Ruhepunkt** — also nach dem Rollen. Zwei Fälle blieben deshalb
  unbemerkt, in denen der Ball 20 m vor dem Grün liegen blieb: Die Regel sah 30 m und schwieg. Eine
  Leitplanke muss dieselbe Größe messen wie die Rechnung, die sie korrigieren soll.
  **(2) Die Prüfung war falsch gestellt** — und das ist die wichtigere Lehre. Sie verlangte, dass
  nie ein Schlag mit Reststummel gewinnt, also ein **Veto**. Die Leitplanke ist aber ein **Anstoß**;
  „schwach genug, um eine echte Differenz nie zu kippen" steht in ihrer eigenen Begründung.
  Nachgerechnet: Loch 17, 213 m Rest — 3 Wood lässt 12 m, das 5 Iron 40 m. Das Grün ist unerreichbar,
  also entscheidet, von wo der nächste Schlag kommt, und **12 m schlagen 40 m deutlich**. Die
  Rechnung hat recht, meine Trainerregel nicht.
  Hätte ich den Aufschlag erhöht, bis die Prüfung grün wird, hätte ich eine **ausgedachte Zahl gegen
  eine gemessene** durchgesetzt. Geprüft wird jetzt die Architektur: Der Aufschlag wird angewandt,
  er trägt einen nachlesbaren Grund, und er kippt keine Differenz über 0,20 Schläge.

- **v3.61.0 · 2026-08-17** — **Die Gegenrechnung zählt die Folgeschläge jetzt richtig mit.**
  Die Frage „berücksichtigt das die Folgeschläge?" hat zwei Antworten. **Erstens** und schon vorher:
  `es` ist die erwartete Zahl der Schläge **bis ins Loch** ab dem Landepunkt — der ganze Rest steckt
  drin, allerdings generisch aus der Erwartungstabelle. **Zweitens:** Am Abschlag rechnet `tee()`
  eine **zweite Ebene** (`_ply2`), die den nächsten Schlag vom Landepunkt wirklich durchspielt — mit
  den Bunkern, dem Wasser und den Winkeln, die dort liegen — und die Differenz zur Tabelle als
  `ply2` aufschlägt (`score2`).
  **Und genau die hat die Gegenrechnung ignoriert:** Sie verglich `score`, nicht `score2`. In der
  Antwort fehlte damit ausgerechnet das, wonach gefragt war — und schlimmer: Sie verglich eine
  **andere Zahl als die, nach der die Engine ihre Empfehlung sortiert**. Zwei Rechnungen auf einem
  Bildschirm, wieder einmal.
  Nachgerechnet an einem 387-m-Par-4 mit Bunker in der Landezone: Driver gegen 2 Driving Iron kostete
  nach `score` **0,06 Schläge** — nach `score2` **0,88**. Der Grund steckt im zweiten Schlag
  (`ply2` −1,34 gegen −0,17), und das ist keine Rundungsfrage, sondern die Aussage.
  **Neu in der Antwort:** der Folgeschläger beider Varianten („Danach: 7 Wood statt 7 Wood —
  mitgerechnet") und der Folgeschlag als **eigener** Grund, wenn er den Unterschied macht.
  **Wo die zweite Ebene fehlt, steht das dabei:** `nextShot()` hat sie nicht, bei Folgeschlägen
  bleibt es bei der Tabelle. Lieber die Grenze benennen als eine Genauigkeit vorgeben, die die
  Rechnung nicht hat.

- **v3.60.1 · 2026-08-17** — **Nachtrag zu v3.60: „Warum nicht …?" gilt jetzt auch für
  Folgeschläge.**
  Die Gegenrechnung gab es nur **vom Abschlag** — im Code stand: „Für Folgeschläge fehlt die
  Vergleichsgrundlage, und dann behaupte ich lieber nichts." Ehrlich, aber unbefriedigend: **Am
  zweiten Schlag stellt man die Frage häufiger als am Tee.**
  Der Grund war technisch: `nextShot()` kannte keinen Schlägerfilter. Jetzt hat es denselben
  `nurClub`-Zweig wie `tee()` — wichtig, damit **beide Seiten des Vergleichs aus derselben Rechnung**
  kommen; ein Vergleich zwischen zwei Verfahren wäre wertlos. Der Filter greift **nach** den
  Vorauswahlen, aber **vor** der Deckelung auf acht Schläger, sonst fiele der gefragte Schläger der
  Rechenzeit zum Opfer und die Antwort hieße fälschlich „nicht spielbar".
  **Zwei ehrliche Antworten mehr:** Kommt der Schläger nicht in Frage, sagt die App jetzt **warum** —
  und unterscheidet dabei eine **Rechnung** („trägt zu weit oder zu kurz") von einer **Regel**: Ein
  Driving Iron ist absichtlich nur für den Abschlag vorgesehen (`TEE_ONLY`), weil er vom Boden für
  die meisten der schwerste Schläger im Bag ist. Das als Längenproblem zu melden wäre falsch.
  **Und eine Falschaussage beseitigt:** Das Ergebnis trug `amTee:true` **fest verdrahtet** — ein
  Rest daraus, dass nur der Abschlag diesen Zweig erreichte. Bei 187 m Rest stand dann „vom
  Abschlag". Jetzt wird die Lage mitgegeben und angezeigt („aus 188 m").
  **Nachtrag zur Arbeitsweise:** Ich habe die Funktion zunächst ein zweites Mal gebaut, obwohl sie
  schon existierte — die Selbstprüfung hat den doppelten Namen sofort gemeldet („die spätere gewinnt
  kommentarlos"). Beim Aufräumen habe ich zu viel gelöscht und die bestehende Fassung aus der
  Ausgabedatei zurückgeholt. Genau dafür ist die Prüfung da; erst nachsehen, dann bauen.

- **v3.60.0 · 2026-08-17** — **Den Caddy befragen: „Warum nicht das 2 Driving Iron?"**
  Eine Empfehlung ohne Begründung muss man glauben. Auf der Bahn hat man aber eine konkrete
  Gegenfrage — und wer darauf keine Antwort bekommt, folgt der Empfehlung blind oder gar nicht.
  Beides ist schlechter als verstehen.
  Im Caddy-Panel steht jetzt eine Auswahl **„Warum nicht …?"** mit allen Schlägern aus dem Bag. Die
  Antwort ist **kein Geschwätz, sondern die Rechnung**: Die EV-Engine bewertet den gewünschten
  Schläger mit **denselben Stichproben, derselben Gefahrenkarte und derselben Streuung** wie die
  Empfehlung (`STRAT.tee(..., nurClub)`). Ein zweiter, eigener Rechenweg wäre schneller gebaut und
  lieferte Zahlen, die man **nicht vergleichen kann** — deshalb ein Parameter und keine zweite
  Funktion.
  Beispiel aus dem Prüflauf (387 m, Par 4):
  > **2 Driving Iron** statt **Driver**: 5,21 statt 4,89 erwartete Schläge (+0,33).
  > Weite 181 m statt 212 m · Rest 208 m statt 181 m · Fairway 15 % statt 39 % · Strafrisiko 0 % / 0 %.
  > **Entscheidend:** der längere Rest zum Grün kostet mehr, als die Lage einbringt.
  Genannt wird **die** Größe, die den Unterschied macht — nicht alle vier. Und „**praktisch
  gleichwertig — nimm den, der dir liegt**" ist ein eigener Fall: Bei unter 0,05 Schlägen Unterschied
  wäre jede andere Aussage Scheingenauigkeit.
  **Der Caddy darf verlieren.** Ist die Alternative besser, sagt die Antwort genau das (mit 👍). Ein
  Caddy, der nie zugibt, dass die Gegenfrage recht hat, ist keiner.
  **Nur vom Abschlag.** Dort kennt die Rechnung die Ideallinie des Lochs; für Folgeschläge fehlt die
  Vergleichsgrundlage. Statt eine Zahl zu erfinden, sagt die App das — „dann behaupte ich lieber
  nichts".

- **v3.59.0 · 2026-08-17** — **Der rote Streifen ist weg · Panel und Karte widersprechen sich nicht
  mehr.**
  **(1) Der Streifen hat die Kopfzeile umgebaut, die er kommentierte.** `.pf-top` ist eine
  Flex-Zeile ohne Umbruch — ein zusätzliches Feld darin nimmt Breite, und im Bild stand
  „Par 4 · HCP 17 · 279 m" plötzlich vierzeilig. Jetzt trägt der **🧪-Knopf selbst** die Aussage: Er
  wird rot, und darunter hängt eine schmale Unterschrift „Simulation · beenden". Nichts verschiebt
  sich, und der Hinweis steht dort, wo auch die Abhilfe ist.
  Das `!important` ist nötig, weil die Regel für `.on` später im Stylesheet steht — ohne Vorrang
  sähe der Knopf grün aus wie jeder andere Schalter, und „an" heißt hier etwas anderes: die Ortung
  ist ausgesetzt.
  **(2) Die Detailansicht und die orange Kette sind zwei Rechnungen.** Das Panel zeigt den
  **heuristischen** Plan (Regeln: Layup-Grenze, Wedge-Zone, Gefahrenabstand) — dort landet es, wenn
  die EV-Engine für diese Lage kein Ergebnis liefert. Die Karte zeichnet die **Zielkette** der
  EV-Engine, aus der auch die Etiketten stammen. Im gemeldeten Fall: 5 Iron auf 104 m Rest gegen
  7 Wood 174 m → PW 100 m — **dieselbe Strategie, ein Schläger Unterschied**.
  Beide sind für sich richtig; nebeneinander und unerklärt sieht es wie ein Fehler aus, und man weiß
  nicht, welcher Zahl man folgen soll. Weicht der Schläger ab, steht die Kette jetzt **ausdrücklich
  dabei** — mit dem Satz, dass sie das Gezeichnete ist. Stimmen beide überein, erscheint keine
  Zeile; eine Zeile, die immer kommt, liest niemand.
  **Nicht gemacht:** eine der beiden Rechnungen abschalten. Die Heuristik greift genau dort, wo die
  EV-Engine aussteigt — sie wegzunehmen hieße, in diesen Lagen gar nichts zu sagen.

- **v3.56.0 · 2026-08-17** — **Die Simulation startet jetzt im Spielmodus — mit Rückfrage.**
  Der Schalter stand unter Mehr → Daten, und das war ein Denkfehler: **Aus einer laufenden Runde
  kommt man dort nicht hin, ohne sie zu verlassen.** Ein Werkzeug für den Spielmodus, das man nur
  außerhalb erreicht, ist keins.
  Er sitzt jetzt als **🧪** in der Kartenleiste rechts, wo die übrigen Kartenwerkzeuge leben, und
  zeigt im aktiven Zustand denselben Hervorhebungsstil wie Luftbild oder Zielkette.
  **Mit Rückfrage vor dem Start**, denn genau dort liegt das neue Risiko: Der Knopf sitzt zwischen
  zehn Schaltern, die man im Spiel dauernd antippt. Ein Fehltipp würde die echte Ortung
  stillstellen — mitten auf der Bahn fällt das erst auf, wenn die Distanzen nicht mehr wandern. Die
  Rückfrage nennt beides, was man wissen muss: dass nichts gespeichert wird und dass die Ortung bis
  zum Beenden ausgesetzt ist.
  **Beim Beenden keine Rückfrage.** Zurück in den Normalbetrieb ist immer richtig und muss sofort
  gehen — eine Rückfrage dort wäre nur eine Hürde vor dem Sicheren.

- **v3.55.0 · 2026-08-17** — **Korrektur: Die Prüfwerkzeuge gehören ins Repo.**
  In v3.54 hatte ich notiert, `runde-simulation.js` und `runde-harness.js` gehörten **nicht** ins
  Repo. Das war falsch gedacht, und zwar aus vier Gründen:
  **Sie müssen zur Fassung passen, die sie prüfen.** Ein Prüflauf gegen eine andere `index.html`
  prüft nichts; derselbe Commit ist die einzige verlässliche Kopplung — genau deshalb liegt
  `tests.js` seit je dort.
  **Sonst existieren sie nur in einer Sitzung.** Was nicht im Repo liegt, ist nach dem Schließen
  des Chats weg. Die sieben Invarianten des Platz-Durchlaufs wären dann Erinnerung statt Code — und
  die nächste Sitzung baut sie neu, vermutlich schwächer.
  **Sie kosten nichts.** Die App lädt sie nicht, sie sind wenige Kilobyte und enthalten keine
  Geheimnisse: Testcode gegen einen erfundenen Platz.
  **Sie sind über die Roh-URL abrufbar**, also am Sitzungsanfang sofort verfügbar — der praktische
  Hauptgewinn.
  Abschnitt 0b listet jetzt **fünf** Dateien im Root, nennt beide Roh-URLs und schreibt fest:
  **gleicher Ordner, kein Unterordner** — `runde-simulation.js` erwartet Harness und `index.html`
  daneben, und ein Unterordner wäre ein Pfad mehr, den man beim nächsten Mal falsch macht.

- **v3.54.0 · 2026-08-17** — **Beide Simulationen sind jetzt dokumentiert.**
  Eine Prüfebene, die niemand kennt, wird beim nächsten Umbau nicht benutzt — und findet dann
  nichts mehr. Neuer Abschnitt **7a–7c**:
  **7a** stellt die drei Ebenen gegenüber, mit einer Spalte „findet NICHT": `tests.js` (reine
  Funktionen, Quelltext-Muster), `runde-simulation.js` (ganze Runde, Sync, 18 Löcher),
  Simulationsmodus (alles, was nur das Auge beantwortet). Dort steht auch die **Falle**, die mich
  dreimal Zeit gekostet hat: Der Zugriffshelfer in `tests.js` liefert nicht immer dasselbe Objekt
  wie der Sandkasten, und `playBegin` ersetzt `PLAY` komplett — **Laufzeittests mit Spielzustand
  gehören deshalb in die Rundensimulation**.
  **7b** beschreibt den Platz-Durchlauf: die sieben Invarianten im Wortlaut, die Zählweise (pro
  Regel, nicht pro Fall) und die Anweisung, hier **neue Invarianten** zu ergänzen statt neuer
  Einzelfälle — eine Regel deckt 90 Lagen ab, ein Einzelfall genau einen.
  **7c** beschreibt den Simulationsmodus und die drei Eigenschaften, die bei jeder Änderung
  erhalten bleiben müssen — samt der Anweisung, bei einem **neuen Schreibweg** `simAktiv()`
  mitzuprüfen. Genau dort wäre die Sperre sonst als Erstes undicht.
  **Abschnitt 0 verweist darauf** und macht beide Skripte zur Pflicht vor dem Export. Der
  Prüfstand prüft, dass diese Absätze da sind — eine Doku, die man vergessen kann, ist keine.

- **v3.53.0 · 2026-08-17** — **Automatischer Platz-Durchlauf — und der erste Fund davon.**
  Die Rundensimulation fährt jetzt **alle 18 Löcher mit je fünf Lagen** ab (Tee, Landezone, 150 m,
  100 m, Grünrand) und prüft **sieben Invarianten** — Sätze, deren Verletzung immer ein Fehler ist,
  keine Soll-Werte, die man nachträglich anpasst:
  Kette endet auf dem Grün · beginnt am richtigen Startpunkt · kein Schlag über die längste
  Schlägerlänge · kein Layup mit Reststummel · nur Schläger aus dem Bag · keine kaputten Zahlen ·
  Restdistanz nimmt ab.
  **Gezählt wird pro Regel, nicht pro Fall.** 90 Lagen × 7 Regeln einzeln gemeldet wären eine
  unlesbare Wand; eine Regel gilt als bestanden, wenn sie **nirgends** verletzt wird, und die erste
  Verletzung steht mit Loch und Lage dabei.
  **Der erste Durchlauf hat sofort einen echten Fehler gefunden:** Auf Loch 3 (Par 5, 471 m) plante
  die Kette aus 251 m Rest „**8 m mit LW, dann 259 m mit 3 Wood**" — ein Schlag länger als jeder
  Schläger, und der erste ging sogar **rückwärts** (Rest wuchs von 251 auf 259).
  **Die Ursache:** `k===0` hieß „erster geplanter Schlag", und dafür wurde immer die
  **Abschlags**-Bewertung genommen. Steht man aber mitten auf der Bahn, ist der erste geplante
  Schlag kein Abschlag — `tee()` liefert dann den Landepunkt des Drives, gemessen **ab dem Tee**.
  Wer schon dort steht, bekommt einen Wegpunkt wenige Meter neben sich, und der Rest muss in einem
  Schlag zum Grün. Jetzt entscheidet `abschlagNah`: Nur wer am Abschlag steht, bekommt die
  Abschlags-Bewertung.
  **Ein eigener Fehler bei der Korrektur, sofort vom Prüfstand gefangen:** Zuerst gab ich der
  Abschlags-Bewertung die eigene Position immer mit — bei „zu weit" also einen Punkt 3 km entfernt,
  obwohl dort ausdrücklich **ab Tee** geplant wird. Der Plan wich dadurch von dem ab, den man am
  Abschlag sieht. Die Position geht jetzt nur ein, wenn sie auch gemeint ist.
  **Das ist genau der Zweck dieses Durchlaufs.** Der Fehler wäre auf der Bahn aufgefallen — nach
  dem zweiten Schlag auf einem Par 5, also einmal pro Runde, mit einer absurden Empfehlung. Gefunden
  hat ihn eine Maschine in zwei Sekunden.

- **v3.52.0 · 2026-08-17** — **Simulationsmodus: den Platz am Schreibtisch durchspielen.**
  Man kann den Spielmodus nicht prüfen, ohne auf dem Platz zu stehen — und auf dem Platz will man
  nicht prüfen, sondern spielen. Jeder Fehler der letzten Tage kostete deshalb einen Weg zum Platz
  und ein Foto.
  **Wie es geht, ohne viel Neues:** `PLAY.here` ist die **einzige** Quelle für die Position; Caddy,
  F/M/B, Streuungsoval, Zielkette und der Push zur Uhr lesen alle daraus. Und das Antippen der Karte
  wird für das Lineal längst in Koordinaten umgerechnet. Der Modus verbindet beides: **Tipp = „ich
  stehe hier"**, alles andere rechnet wie auf der Bahn.
  **Drei Regeln, damit er nie schadet.** (1) **Er schreibt nichts** — kein Rundenentwurf, keine
  Schlagerfassung, kein Abgleich mit der Uhr; sonst landet eine Fingerübung in den Statistiken oder
  als laufende Runde auf dem Handgelenk. Geprüft wird an **einer** Stelle (`simAktiv()`), damit kein
  Weg übersehen wird. (2) **Er ist unübersehbar** — roter Streifen über der Kopfzeile, der auch den
  Ausweg nennt; ein Testmodus, den man für den Normalbetrieb hält, ist schlimmer als keiner.
  (3) **Er endet beim Rundenstart** — und zwar ohne eigene Zeile: `playBegin` baut `PLAY` komplett
  neu, und in `playDefaults()` steht `simMode:false`. Der Zustand **ist** der Riegel.
  **Die echte Ortung wird verworfen, nicht angehalten:** Sie beim Umschalten zu stoppen und wieder
  zu starten wäre fehleranfälliger; ihr Ergebnis einfach zu ignorieren ist eine Zeile und kann nicht
  klemmen.
  **Nicht enthalten** sind Score-Eingabe und Rundenende. Wer eine Runde durchspielen will, spielt
  eine — ein „Testkennzeichen" an Runden wäre genau die Fehlerquelle, die v3.13/v3.14 gekostet hat.
  **Drei eigene Fehler beim Bauen, im Prüflauf gefunden:** `simStop` setzte die Position auf `null`
  (alles, was sie liest, stand dann leer da); der Riegel in `playBegin` änderte eine `PLAY`-Instanz,
  die gleich danach ersetzt wird; und der Laufzeittest lag im falschen Prüfstand — `G("PLAY")` ist
  dort nicht immer dasselbe Objekt wie im Sandkasten, weshalb Zuweisungen ins Leere gingen. Der
  Nachweis steht jetzt in der Rundensimulation, wo alles im Sandkasten läuft.

- **v3.51.0 · 2026-08-17** — **BUGFIX: Der „Layup" endete 13 m vor dem Grün.**
  Gemeldeter Plan (Loch 8, Par 5, **463 m**, Modus „sicher"):
  `Driver 237 m · Layup 3 Wood 213 m · Grün LW 19 m`. 237 + 213 = **450 m** — der „Layup" hört
  13 m vor dem Grün auf. Das ist kein Layup, sondern ein Angriff aufs Grün mit dem **zweitlängsten
  Schläger**, gefolgt von einem Chip. Im Modus „sicher" ist es das Gegenteil dessen, was der
  Schalter verspricht — dein Gefühl war völlig richtig.
  **Die Ursache:** Der **heuristische** Caddy legt seit je auf „~100 m Rest" (`layP5`, volle
  Wedge-Zone). Die **EV-Kette** kannte diese Regel nicht. Sie nimmt je Schlag den erwartungsbesten
  Punkt — und weil die Erwartungstabellen „näher ist besser" sagen, legte sie so weit wie möglich.
  Zwei Caddys, eine Regel, nur an einer Stelle: dieselbe Fehlerklasse wie bei der Scorekarte.
  **Jetzt gilt sie in beiden.** Lässt ein Zwischenschlag weniger als 85 m übrig, wird er auf den
  Zielrest zurückgenommen — 110 m bei „sicher", 105 normal, 95 offensiv. Nachgerechnet an deinem
  Loch: aus `Driver 237 · 3 Wood 213 · LW 19` wird **`Driver 226 · 8 Iron 134 · PW aus 105`**. Ein
  voller Wedge ist kontrollierbarer als ein Teilschlag, und genau das drückt der Wedge-Bonus an
  anderer Stelle längst aus.
  **Korrigiert wird nur der Stumpf-Fall.** Alles andere bleibt, wie die EV-Engine es rechnet — sie
  ist in der Sache besser als eine Regel, nur nicht in diesem einen Punkt. Von Hand gesetzte
  Wegpunkte bleiben unangetastet.
  **Ein eigener Fehler beim ersten Anlauf**, im Prüflauf gefunden: Ein Riegel gegen „letzten Schlag"
  verhinderte die Korrektur genau dort, wo sie gebraucht wurde — das Grün wird erst **nach** dieser
  Schleife angehängt, alle Punkte darin sind Zwischenziele.

- **v3.50.0 · 2026-08-17** — **Warum der Spielweisen-Schalter kaum wirkte — und was er jetzt sagt.**
  Nachgerechnet: Die drei Spielweisen werden **getrennt** gerechnet, ihre Bewertungen unterscheiden
  sich immer (der Prüfstand hält das fest). Nur die **Empfehlung** ist oft dieselbe — und das ist
  meistens richtig: Auf einem 387-m-Par-4 ohne Wasser gewinnt der längste sichere Schläger in jeder
  Spielweise, weil die Meter fehlen. Ein Schalter, der schweigend nichts tut, sieht aber kaputt aus.
  **Der Caddy sagt es jetzt:** „Alle Spielweisen empfehlen 3 Wood — auf diesem Loch gibt es nichts
  abzuwägen", oder bei echtem Unterschied „sicher 5 Iron · normal 3 Wood · offensiv Driver". Damit
  wird aus „der Schalter tut nichts" ein „hier gibt es nichts zu entscheiden". Billig zu rechnen,
  weil `_aimTeeEv` jede Spielweise je Loch und Position zwischenspeichert.
  **Und ein Gewicht war zu schwach:** `lie.rough` ist ein **Zusatz**gewicht — die echten Kosten des
  Roughs stecken schon in den Erwartungstabellen (nach Broadie rund 0,25 Schläge bei 150 m). Mit
  **0,12** lag der Zuschlag unter der Hälfte davon, und ein Schläger mit 25 m mehr Länge gewann
  immer, auch bei 45 % Fairwayquote. „Sicher" heißt aber: Ich zahle Meter für Kontrolle. Der
  Zuschlag steht jetzt bei **0,22** — bewusst nahe den echten Kosten, also eine gewollte
  Doppelzählung, und nicht mehr.
  **Was ausdrücklich NICHT geändert wurde:** die Erwartungstabellen. Sie sind die Messung, die
  Gewichte sind die Vorliebe. Wer an der Messung dreht, um eine Vorliebe auszudrücken, verliert
  beides.

- **v3.49.0 · 2026-08-17** — **DIE URSACHE: Karte und Zielkette lagen in zwei verschiedenen
  Maßstäben.**
  Es gibt **zwei** Aufrufe, die die Spielkarte bauen — den vollen Aufbau und den fürs Neuzeichnen.
  Beide setzen `PLAY.mapM`, die Projektion, mit der die **orange Zielkette** in die Overlay-Ebene
  gezeichnet wird. Seit **v3.38** stand `bufTopM:20` nur im **ersten**.
  Damit lagen das gebackene Luftbild und die Kette in zwei Maßstäben, und zwar um genau den
  Zuschlag auseinander — rund 18 %. Ein Maßstabsfehler **wächst mit der Entfernung**: Am Abschlag
  stimmte es fast, am Grün lag die Kette 60 m daneben.
  **Damit sind beide gemeldeten Beobachtungen dieselbe Ursache:** „der Caddy zielt daneben" und
  „die Linie startet nicht am Tee" — einmal am langen, einmal am kurzen Ende derselben Verschiebung
  gemessen. Nachgemessen an deinem Bild von Loch 5: Kette 3,55 px/m, Karte 2,92 px/m, Verhältnis
  **1,22** — genau die Größenordnung des Zuschlags.
  **Und es war mein Fehler aus v3.38**, dem Versuch, 20 m hinter dem Grün sichtbar zu machen. Ich
  habe damals einen von zwei Aufrufen geändert und die Wirkung an der falschen Ebene gemessen.
  **Lehre, jetzt im Prüfstand:** Zwei Aufrufe mit derselben Aufgabe müssen dieselbe Parameterliste
  haben. Der Prüfstand vergleicht die einpassungsrelevanten Parameter beider Aufrufe und schlägt an,
  wenn sie auseinanderlaufen — ein Unterschied darin ist von außen unsichtbar und äußert sich als
  etwas völlig anderes.

- **v3.48.0 · 2026-08-17** — **BUGFIX GEFUNDEN: Das Oval war ein Rest vom vorigen Zustand.**
  Du hattest recht, und ich habe dreimal an der falschen Stelle gesucht. Die Zielkette endet
  tatsächlich exakt auf dem Grün — **das Oval gehört nicht zu ihr**. Es gibt zwei Ovale: das der
  Schlagfolge (je Teilstrecke, an deren Endpunkt) und `PLAY.stratOval`, das **in die Karte gebacken**
  wird. Letzteres wird ausschließlich in `playCaddyNow()` genullt und neu gesetzt.
  **Und `playCaddyNow()` läuft bei „zu weit" nie** — die Caddy-Zeile kehrt vorher zurück. Das Oval
  blieb also stehen, wo es beim letzten Mal gerechnet wurde: beim vorigen Loch, bei der vorigen
  Position, mit dem vorigen Schläger. Auf einem Par 3 sah das aus, als solle man 50 m hinter das
  Grün spielen.
  **Ein Oval, das nicht zum aktuellen Zustand gehört, ist schlimmer als keines** — es sieht aus wie
  eine Empfehlung. Genullt wird es jetzt in beiden Fällen: im „zu weit"-Zweig und beim Lochwechsel.
  Der laufende Nachweis steht in der Rundensimulation: altes Oval setzen, Position 2,5 km entfernt,
  Caddy-Zeile bauen — Oval ist weg.
  **Was ich daraus lerne:** Ich habe die Kette geprüft (sie war richtig) und daraus geschlossen, die
  Anzeige sei richtig. Zwei Zeichnungswege für dieselbe Sache waren die Erklärung, und die hätte ich
  finden müssen, als die Kette zum dritten Mal stimmte.

- **v3.47.0 · 2026-08-17** — **Nachbargrüns werden gemeldet.**
  Nachgemessen am Bild von Loch 3 (Par 3, 150 m): Die Streuungsellipse hat einen Halbmesser von rund
  10 m längs (7 Iron: 6 % von 150 m) — das passt zur Zeichnung. Ihr **Mittelpunkt** liegt aber rund
  25 m hinter der abgebildeten Fahne. Da Fahne und Kettenende **dieselbe** Variable lesen
  (`holeRef(...).green`), kann das nur eines heißen: Die Fahne im Bild gehört zu einem **anderen
  Loch**. Diese Fassung stammt aus v3.45 — der Fix „nur eine Fahne" war darin noch nicht enthalten.
  Damit der Fall auch ohne fremde Fahne erkennbar bleibt, meldet die App jetzt Grüns anderer Löcher,
  die **näher als 80 m** am eigenen liegen — mit Lochnummern und Abstand. Dann muss man nicht raten,
  was man auf dem Bild sieht.
  **Geprüft und für richtig befunden:** Die Ellipsen-Achsen sind NICHT vertauscht. `sigL` ist die
  SEITLICHE Streuung (die Heuristik rechnet 4–7 % der Länge, und `biasL` folgt der Links/Rechts-
  Fehlerneigung), `sigD` die Streuung in der Länge. `dispRingPath` setzt sie richtig ein. Ich hatte
  das zunächst für den Fehler gehalten — die Namen legen es nahe, die Rechnung widerlegt es.

- **v3.46.0 · 2026-08-17** — **BUGFIX: Auf der Karte standen mehrere Fahnen — der Caddy zielte nie
  daneben.**
  Die Bedingung für die Fahne lautete `opt.flag && opt.hole` — also **„Spielmodus"**, nicht **„dieses
  Loch"**. Gezeichnet wird aber über **alle** Löcher des Platzes. Auf einer Bahn, die neben einer
  anderen liegt, standen damit zwei Fahnen im Bild, und die nähere gehörte oft zum **Nachbarloch**.
  Genau daraus entsteht der Eindruck, der Caddy ziele „50 m hinter das Grün": Die Zielkette endet
  korrekt auf dem **eigenen** Grün — das Auge nimmt aber die falsche Fahne als Bezug. Nachgerechnet
  am Bild von Loch 2 (Par 5, 499 m): 237 + 150 + 111 = 498 m, also **auf** dem Grün. Die Fahne, die
  50 m davor stand, gehörte zu einem anderen Loch.
  **Ein Zeichen an der falschen Stelle ist schlimmer als keines** — es sieht aus wie eine Antwort.
  Jetzt bekommt nur das gespielte Loch eine Fahne; fremde Grüns bleiben als kleiner Punkt sichtbar
  (Orientierung), fremde Abschläge ebenso. Sie ganz wegzulassen nähme die Übersicht, eine Fahne dort
  führt in die Irre.

- **v3.45.0 · 2026-08-17** — **Die Knopfspalte lag drei Fassungen lang an der falschen Klasse.**
  Seit v3.41 habe ich `.play-map-ctrls` verschoben, tiefer gesetzt und ausgeblendet — **im
  Spielmodus heißt die Spalte aber `#pfCtrls`**. `.play-map-ctrls` gehört zum alten Blatt-Layout.
  Alle drei Korrekturen liefen deshalb ins Leere, und dass sich auf dem Gerät nichts änderte, war
  völlig richtig. Jetzt an der Stelle, die wirklich zeichnet: Abstand aus der gemessenen
  Kopfzeilenhöhe statt fester 58 px, `z-index` unter dem Caddy, und ausgeblendet, solange er offen
  ist. Die alte Regel steht wieder auf ihrem Ursprungswert — sie gilt woanders und wurde von mir
  ohne Grund verändert.
  **Zum orangen Kreis** bleibt eine Zahl, die nicht zusammenpasst: „Grün **0 %**" bei vorhandener
  Grünfläche. Bei 100 m mit einem Wedge treffen nicht null von tausend simulierten Bällen die
  Fläche — solange das Ziel wirklich auf dem Grün liegt. Es bleiben zwei Erklärungen: Das Ziel liegt
  woanders, oder die Fläche liegt woanders.
  Die App hält jetzt **beide Abstände** fest — Ziel zum gespeicherten Grünpunkt und Ziel zum
  Schwerpunkt der Grünfläche. Aus zwei Zahlen liest man die Ursache ab; aus „sieht falsch aus"
  nicht.

- **v3.44.0 · 2026-08-17** — **Tyler Twist gegen Tennisarm — mit Anleitung, Dosierung und täglicher
  Erinnerung.**
  Aufgenommen in **Einheit B** (Beweglichkeit & Regeneration), sichtbar nur mit FlexBar in den
  Geräten.
  **Die Anleitung ist hier kein Beiwerk.** Wer den Tyler Twist ohne sie macht, macht die
  entscheidende Richtung falsch: Gearbeitet wird **nur in der nachgebenden Phase** — das langsame
  Zurückdrehen des betroffenen Handgelenks über vier Sekunden. Das Eindrehen mit der gesunden Hand
  ist keine Übung. Genau diese Unterscheidung entscheidet, ob die Übung wirkt.
  **Mit Dosierung**, weil hier weder „mehr hilft mehr" noch „darf nicht weh tun" stimmt: Ein Ziehen
  bis etwa 4 von 10 ist erwünscht und darf nachklingen; scharfer Schmerz heißt weicherer Stab oder
  weniger Wiederholungen. Und die Geduld gehört dazu — **Wirkung nach 4–6 Wochen**, nicht nach
  einer.
  **Auf der Heute-Seite statt nur im Wochenplan**, weil es keine Trainingseinheit ist, sondern eine
  **Behandlung**: Der Tennisarm ist eine Verschleißreizung der Sehne und heilt nicht durch Schonung,
  sondern durch dosierte Belastung. Zweimal die Woche bringt nichts, zwei Minuten täglich bringen
  alles.
  **Ohne FlexBar erscheint sie nirgends** — weder im Programm noch auf der Heute-Seite. Eine
  tägliche Erinnerung an etwas, das man nicht ausführen kann, schaltet man nach drei Tagen ab, und
  dann sind alle Erinnerungen weg.

- **v3.43.0 · 2026-08-17** — **Ein Kilometer als feste Grenze · und eine Erklärung für den orangen
  Kreis.**
  **(1)** Die Grenze zwischen „ab Tee" und „ab eigener Position" hing an der Lochlänge
  (`len + 150`, mindestens 650 m). Gut gemeint, aber **unvorhersehbar**: Auf einem Par 3 kippte die
  Rechnung bei 650 m, auf einem langen Par 5 erst bei 670 — und dazwischen wusste niemand mehr, was
  gerade gerechnet wird. Jetzt **ein Wert: 1000 m** (`CADDY_TEE_AB_M`). Ein Kilometer liegt auf
  jedem Platz weit außerhalb jeder Bahn; wer so weit weg ist, spielt das Loch nicht, sondern schaut
  es sich an. Und man kann sich die Regel merken — bei einer Anzeige, die unterwegs ihr Verhalten
  ändert, ist das die halbe Miete.
  **(2) Zum orangen Kreis** bleibt nach dem Fix von v3.42 nur noch **eine** Erklärung, und die
  liegt nicht in der Rechnung: Die Schlagfolge trifft den **gespeicherten Grünpunkt** auf den Meter
  — aber der muss nicht auf der gezeichneten **Grünfläche** liegen. Beim Import aus OSM endet die
  Lochlinie regelmäßig hinter dem Grün. Dann ist alles verschoben — Distanzen, Caddy, Zielpunkt —,
  aber **in sich stimmig**, und genau deshalb fällt es beim Rechnen nicht auf.
  Die App vergleicht das jetzt selbst und schreibt bei mehr als 25 m Versatz eine Warnung mit
  Lochnummer und Betrag ins Protokoll. 25 m, weil ein Grün selten länger ist und die Fahne abweichen
  darf — das Zentrum nicht.
  **(3) Der Ausweichwert für die Kopfzeilenhöhe war zu klein:** 58 px gelten für eine **einzeilige**
  Kopfzeile. Auf dem Gerät bricht „Par 4 · HCP 17 · 279 m" um und die Zeile wird rund 90 px hoch;
  griff die Messung einmal nicht, lag die Knopfspalte mitten im Score-Kasten. Jetzt **96 px** — ein
  Ausweichwert muss den häufigen Fall treffen, nicht den günstigsten. Der Caddy zieht **6 px ab**,
  weil die gemessene Höhe den Innenabstand der Kopfzeile enthält; ohne Abzug klaffte die Lücke.

- **v3.42.0 · 2026-08-17** — **BUGFIX: Der Lochplan rechnete aus der Ferne vom Wohnzimmer aus.**
  Der Plan im Bild verriet es selbst: „1. Abschlag Driver 218 m · **2. Grün 3 Wood 2694 m**". 2694 ist
  2912 minus 218 — also der Rest **ab der eigenen Position**, nicht ab dem Landepunkt.
  Die Ursache: `amTee` schaltete die **Rechnung** auf Tee-Strategie um, der **Startpunkt** blieb
  aber `PLAY.here`. Wenn man „wie am Abschlag" plant, muss man auch **am Abschlag anfangen** —
  beides gehört zusammen, getrennt ergibt es einen Plan, der zu keiner Wirklichkeit passt.
  **Und das erklärt den orangen Kreis:** Die Zielpunkte werden entlang der Linie Startpunkt→Grün
  abgetragen. Startet die Linie 3 km entfernt, liegt sie leicht schräg zur Bahn — das Oval landete
  systematisch **neben und hinter** dem Grün. Nachgerechnet: Aus „Driver 218 m + 3 Wood 2694 m"
  wird jetzt „**3 Wood 196 m + PW 86 m**", Start 0 m vom Tee, Ende 0 m vom Grün. Der Prüfstand hält
  fest, dass kein einzelner Schlag länger als 260 m sein darf — genau daran war der Fehler zu sehen.
  **Dazu drei Sachen an der Anzeige:** Die Höhenmessung der Kopfzeile stand nur in `pfRender` und
  fehlte damit bei Drehung, Schriftgröße oder längerem Platznamen — dann galt der Ausweichwert von
  58 px und die Knopfspalte lag wieder über dem Score-Kasten. Sie läuft jetzt bei jedem
  Kartenwechsel mit. Der Abstand zwischen Kopfzeile und Caddy geht von 6 auf **2 px**. Und der
  **aufgeklappte Caddy hat Vorfahrt**: Die Knopfspalte wird ausgeblendet, statt darüber zu liegen —
  während man den Caddy liest, braucht man keine Kartenknöpfe, und ein halb sichtbarer Knopf
  verleitet nur zum Danebentippen.

- **v3.41.0 · 2026-08-17** — **Zwei Knöpfe raus, Knopfleiste tiefer, Wache für die Schlagfolge.**
  **(1) „✔ Loch" und 🔎 entfernt**, samt Funktion: die `pfWiz*`-Familie (zwei Zahlenraster über der
  Karte), der Zustand `PLAY.wiz`, das Markup und `togglePlatzModus`. Der Platz-Modus selbst bleibt
  unter Mehr → Darstellung, wo man ihn einmal einstellt statt auf der Bahn zu suchen.
  **Entfernt statt auskommentiert:** Ein Knopf weniger macht die beiden verbliebenen Aktionen
  breiter (Raster von fünf auf vier Felder) — mit Handschuh der eigentliche Gewinn. Und toter Code
  sieht bei der nächsten Durchsicht wie ein vergessener Anschluss aus; genau das war `playGoHole`.
  **(2) Die Knopfleiste rechts** saß 8 px unter dem oberen Rand und damit unter dem Score-Kasten.
  Sie hängt jetzt an derselben **gemessenen** Höhe wie der Caddy-Kasten (`--pf-top-h`) — eine feste
  Zahl wäre hier derselbe Fehler wie zuvor.
  **(3) Zum Zielen:** Nachgerechnet trifft die Schlagfolge das Grün **auf den Meter** — der letzte
  Kettenpunkt liegt 0 m vom Grün entfernt, der Prüfstand hält das jetzt fest. Im gemeldeten Fall
  stand die Position **2,2 km** vom Loch entfernt; dann zeigt die App den **Lochplan ab Tee**, nicht
  ein Ziel ab der eigenen Position — das erklärt das Bild.
  Statt sich darauf zu verlassen, **misst die App es jetzt selbst**: Liegt der letzte Zielpunkt mehr
  als 25 m vom Grün entfernt, steht es mit Lochnummer und Abstand im Protokoll. 25 m, weil ein Ziel
  bewusst hinter der Fahne liegen darf (vorderer Bunker), aber nicht hinter dem Grün. Der nächste
  Fall ist damit belegbar statt erinnert.

- **v3.40.0 · 2026-08-17** — **Die Vorgabenzeile sagt jetzt, was sie meint · Kopfzeile und Caddy
  überlappen nicht mehr.**
  **(1)** „16 Pkt · Puffer +2 · hochgerechnet 41 · hier 2 Vorgabeschläge" hatte drei Schwächen, und
  die erste ist die schlimmste: **„Puffer" ist im Golf besetzt.** Im alten EGA-System war die
  Pufferzone der Bereich, in dem sich die Vorgabe *nicht* ändert. Gemeint war hier etwas ganz
  anderes — Punkte über dem, was die eigene Vorgabe verlangt. **Ein Wort, das der Leser schon kennt,
  aber anders, ist schlimmer als ein unbekanntes.**
  Dazu: „16 Pkt" ohne Bezugsgröße sagt nichts (16 nach vier Löchern ist stark, nach zwölf schwach),
  und „hier 2 Vorgabeschläge" klang nach einer Anweisung.
  Jetzt: **„16 Pkt nach 8 · 2 über Vorgabe · Ziel 41 · dieses Loch +2"**. Gleichstand heißt „genau
  auf Vorgabe" — „0 über Vorgabe" liest sich falsch.
  **(2) Die Überlappung** hatte eine feste Zahl als Ursache: Der Caddy-Kasten begann bei 58 px.
  Das stimmt, solange die Kopfzeile einzeilig bleibt — bricht sie um (schmales Gerät, große
  Schrift, langer Lochtext), wird sie höher und der Kasten schiebt sich darüber. Ein Wert, der von
  der Textlänge abhängt, gehört nicht in die Formatvorlage; dort kann man ihn nur raten.
  Die Höhe wird jetzt nach jedem Zeichnen **gemessen** und als `--pf-top-h` gesetzt. Gemessen wird
  im nächsten Bild (`requestAnimationFrame`) — direkt nach dem Schreiben stünde noch die vorige
  Höhe, weil der Umbruch erst danach feststeht.

- **v3.39.0 · 2026-08-16** — **Korrektur zu v3.38: Der Kopfraum lag auf der falschen Ebene.**
  Der Hinweis kam von der richtigen Stelle: Der Ausschnitt **zoomt beim Näherkommen mit**
  (`playAutoView`) — und v3.38 hat den Zuschlag in die **Basiseinpassung** gelegt. Was man sieht,
  bestimmt aber der **Anzeigeausschnitt**, und der wird von `playMapInitView` und `playAutoView`
  gebaut. Meine Messung („Grün von 9 % auf 23 %") galt für das Basisbild, nicht für das, was auf dem
  Schirm steht. Der Fehler war damit nicht behoben, sondern nur woanders gemessen.
  Beide Ausschnitte nahmen ohnehin schon 30 m hinter dem Grün mit — nur landete dieser Streifen
  anschließend **unter den Kopfzeilen**, weil der Inhalt mittig sitzt und die Anzeige das obere
  Drittel bedeckt. **Beim engen Ausschnitt ist es am schlimmsten**: Je näher man kommt, desto
  kleiner der Ausschnitt und desto größer der Anteil, den die Anzeige verdeckt.
  `playKopfraum()` erweitert den Ausschnitt jetzt **nach oben** — nur nach oben; zur Seite und nach
  unten ändert sich nichts. Das Band ist hergeleitet, nicht geraten: Die Kopfzeilen bedecken rund
  **28 %**, für „alles Bisherige darunter" braucht es `0,28/(1−0,28) ≈ 39 %` der bisherigen Höhe.
  **`bufTopM` aus v3.38 bleibt** — aber aus einem anderen Grund als dort behauptet: Es sorgt dafür,
  dass der Bereich hinter dem Grün im **Basisbild** überhaupt existiert, denn Luftbildkacheln
  entstehen nur für die eingepasste Fläche. Verfügbar machen und sichtbar machen sind zwei
  verschiedene Dinge; es braucht beides.

- **v3.38.0 · 2026-08-16** — **Mindestens 20 m hinter dem Grün sind jetzt immer im Bild.**
  Der Ausschnitt endete exakt am eingepassten Korridor — also am Grün. Zwei Folgen, und beide sind
  ärgerlich: **Fachlich** verschweigt ein Bild, das am Grünrand aufhört, genau das, was über die
  Schlägerwahl entscheidet — Bunker, Aus und Böschung dahinter. **Praktisch** liegen die Kopfzeilen
  des Spielmodus (Lochkopf, F/M/B, Caddy) über der Karte; endete das Bild am Grün, verdeckte die
  Anzeige genau das Grün.
  `_fitProject` kennt jetzt einen Zuschlag **nach oben** — bei gedrehter Karte also hinter dem
  Grün. Er ist das **Größere aus 20 m und 18 % der Bahnlänge**: Auf einem 110-m-Loch reichen 20 m,
  auf 500 m wären sie unsichtbar.
  Nachgemessen an einem 342-m-Loch: Das Grün saß vorher bei **9 %** der Bildhöhe — mitten unter der
  Kopfzeile. Jetzt bei **23 %**, also darunter. Der Prüfstand hält beide Grenzen fest: weit genug
  nach unten, aber nicht zu weit — sonst schrumpft die Bahn, um Wiese zu zeigen.

- **v3.37.0 · 2026-08-16** — **Die beiden Hinweise der Selbstprüfung abgeräumt.**
  **(1) `playGoHole` entfernt.** Sie hatte genau einen Aufrufer — den GPS-Lochvorschlag, der in
  v3.35 verschwunden ist. Eine Funktion ohne Aufrufer sieht bei der nächsten Durchsicht wie ein
  vergessener Anschluss aus und wird „wieder angebunden"; die Selbstprüfung hat sie zurecht
  gemeldet.
  **(2) Die Sperrklinke war zwei Wochen nicht nachgezogen worden** und meldete 82 Namen. Eine
  Warnung, die immer leuchtet, liest niemand mehr — sie war damit wertlos, und genau deshalb ist
  auch der abgehängte `playGoHole` so lange unbemerkt geblieben.
  Die Funktionen **dieser Woche** stehen jetzt im Referenzabschnitt, wo sie hingehören (Fitness:
  Wochenplan, Erinnerungen, Geräte, Programm · Ausrüstung im Bag · Caddy-Takt · Speicherprüfung).
  Der Altbestand wandert in die Sperrklinke: **333 → 400 Namen**. Die Selbstprüfung meldet damit
  wieder **null** Hinweise — und der nächste neue Name fällt sofort auf.

- **v3.36.0 · 2026-08-16** — **Protokolle führen jetzt auch Warnungen — in App und Uhr.**
  Ein **Fehler** sagt „etwas ist schiefgegangen". Eine **Warnung** sagt „es lief, aber das Ergebnis
  kann falsch sein" — und das ist auf der Bahn die wichtigere Auskunft. Ein Caddy ohne
  Schlägerdistanzen wirft keine Ausnahme; er nennt nur einen schlechteren Schläger. Eine Distanz
  aus einer Position mit 40 m Streuung sieht genauso aus wie eine gute und liegt zwei Schläger
  daneben.
  **Gleicher Speicher, eigene Stufe.** Warnungen liegen in derselben Liste, damit man **eine**
  Ansicht liest und die zeitliche Abfolge sieht: Ein Fehler nach drei Warnungen erklärt sich oft von
  selbst. In der App ✕ gegen ⚠, auf der Uhr Absturz rot, Warnung gold, Rest ruhig — ohne
  Unterscheidung liest man dreißig gleich aussehende Zeilen und findet die eine nicht.
  **Warnungen erzeugen nie eine Einblendung.** Sie sind Hintergrundwissen; über der Karte wären sie
  ein Ärgernis und würden genau das verdecken, weswegen man hinsieht.
  **In der App:** kein Schlägerbeutel · Platz ohne Karte · kein Wetter (Wind und Temperatur fließen
  dann nicht ein) · Uhr meldet seit 90 s keine Position · **Speicher über 85 % voll** (der
  Satellitenspeicher wächst auf hunderte MB; entzieht der Browser die Quote, gehen zuerst die
  Kacheln und dann der Rundenentwurf — eine Warnung davor ist unendlich viel wert als eine
  Fehlermeldung danach) · nach der Runde: Löcher mit Score ohne Par, Löcher ohne Putts, Runde ohne
  Tee. Diese drei jetzt, wo man sie noch korrigieren kann — in vier Wochen nicht mehr.
  **Auf der Uhr:** GPS-Genauigkeit schlechter als 25 m · Platz ohne Karte · leerer Schlägerbeutel ·
  veralteter Datenstand · GPS stumm · Abgleich stumm.
  **Nur beim Wechsel des Zustands**, und Einmaliges nur einmal (`logWarnEinmal` / `warnEinmal`):
  Im Takt gemeldet füllen sie das Protokoll und verdrängen die eine Zeile, auf die es ankommt.

- **v3.35.0 · 2026-08-16** — **Der GPS-Lochvorschlag ist vollständig raus.**
  „📍 GPS erkennt Loch x · wechseln" war der letzte Rest des automatischen Lochwechsels, der schon
  in **v1.98** entfernt wurde — weil er beim Warten am nächsten Tee, beim Ballsuchen und auf dem
  Rückweg umsprang. Der Vorschlag hatte **dieselbe Fehlerquelle**, nur mit einem Tipp dazwischen:
  Er erschien in genau denselben Momenten, also gerade dann, wenn man ihn nicht brauchte — und
  stand dabei über der Caddy-Empfehlung, die man in dem Moment lesen will.
  Entfernt sind der Vorschlag und seine Gestaltung (`.play-detect`, auch die Dark-Mode-Regel).
  **`nearestHole` bleibt:** Die Caddy-Position braucht es, um bei grober Abweichung auf das nähere
  Loch umzustellen. Ein Aufräumen, das nützliche Funktionen mitnimmt, ist kein Aufräumen.
  Gewechselt wird im Spielmodus über die Lochnavigation — wie überall sonst auch.

- **v3.34.0 · 2026-08-16** — **BUGFIX: Das Score Differential einer 9-Loch-Runde war nur die halbe
  Rechnung · Neun aus Achtzehn wird erkannt.**
  **(1)** Im Code stand `sd = is9 ? X : X` — **beide Zweige identisch**. Der 9-Loch-Wert ging damit
  als fertiges Differential durch, obwohl er nur die halbe Runde abdeckt: 44 Schläge über neun
  Löcher ergaben 6,8, eine Zahl, die einem Handicap um 7 entspricht. Das ist keine Rundungsfrage,
  sondern ein halbes Ergebnis.
  Richtig nach **DGV/WHS seit 1. April 2024**: Zum Differential der gespielten Neun kommt ein
  statistischer Wert für die nicht gespielten neun Löcher, `((HCPI × 1,04) + 2,4) / 2`. Er ist
  bewusst **platzunabhängig** — er steht für neun Löcher auf einem gemittelten Platz (Par 72,
  CR 72, Slope 113). Bei HCPI 20 sind das 11,6; aus 6,8 wird also **18,4**.
  Die Anzeige nennt jetzt beide Teile, damit die Zahl nachvollziehbar bleibt. **Ohne
  Handicap-Index** lässt sich die Ergänzung nicht bilden; dann bleibt der reine 9-Loch-Wert
  gekennzeichnet stehen — eine erfundene Ergänzung wäre schlechter als eine ehrliche Lücke.
  **(2)** Wer eine 18-Loch-Runde beginnt und nach neun aufhört, hatte bisher eine **unvollständige
  Achtzehn**: richtig aus allen Summen ausgeschlossen (v3.13/v3.14), aber eben auch keine Neun —
  obwohl sie genau das ist. Damit blieben Differential, Stableford und der Vergleich mit anderen
  Neunen ungenutzt. `playFinish` stellt sie jetzt um.
  **Nur bei einem klaren Bild:** genau die erste oder genau die zweite Hälfte durchgespielt, die
  andere vollständig leer. Sieben Löcher oder verstreute Scores bleiben eine abgebrochene Runde —
  das ist etwas anderes als eine Neun, und Raten wäre hier teuer. **Und es wird gesagt:** Eine
  stille Umdeutung der eigenen Runde wäre unheimlich; man fände später „Front 9" in der Liste und
  wüsste nicht, warum.

- **v3.33.0 · 2026-08-16** — **Caddy: ganz aufklappbar, Bedingungen sichtbar, 2 Iron nur vom Tee.**
  **(1) Aufgeklappt bis zum Bildschirmende.** Zugeklappt bleibt es eine schmale Zeile — sie darf die
  Bahn nicht verdecken, wegen der man hinschaut. Aufgeklappt gilt das Gegenteil: Dann **will** man
  alles sehen, und die Karte ist zweitrangig. Vorher: 52 vh Höhe und 66 px rechter Rand für die
  Knopfspalte, also eine schmale halbe Säule mit Scrollbalken. Jetzt volle Breite und
  **`bottom` statt `max-height`** — eine feste vh-Zahl passt auf keinem zweiten Gerät.
  **(2) Das Ein- und Ausklappen fühlte sich hängend an**, weil der Schließen-Knopf oben stand und
  mit dem Inhalt weggescrollt ist: Zum Zuklappen musste man erst wieder hochscrollen. Er klebt jetzt
  am oberen Rand des Kastens.
  **(3) Wind und Temperatur wurden nur selten angezeigt** — und der Grund ist systematisch: Der
  Einfluss wird längst gerechnet (`condFaktor`), aber nur im **heuristischen** Zweig angezeigt. Am
  Abschlag übernimmt seit v2.50 die EV-Engine, und deren Anzeige kannte die Bedingungen gar nicht.
  Man sah sie also ausschließlich dort, wo die EV-Engine **nicht** zuständig war. Jetzt in beiden.
  **Unter drei Metern Unterschied schweigt die Zeile:** Eine Meldung, die bei jedem Loch „±1 m"
  sagt, liest nach dem dritten Mal niemand — und sie verdeckt die Fälle, in denen es zwei Schläger
  sind.
  **(4) Neue Regel: 2 Iron nur vom Abschlag.** Vom Boden ist es ein Schlag mit sehr schmalem
  Fehlerfenster; die Empfehlung mag rechnerisch stimmen, gespielt wird sie trotzdem nicht. **Ein
  Vorschlag, den man ohnehin nicht ausführt, ist schlechter als ein etwas kürzerer, den man spielt**
  — er kostet Vertrauen in den Caddy insgesamt. `TEE_ONLY` greift in `clubPick` und `caddyClubs`,
  also für **Caddy und Gameplan** gemeinsam; sonst empfähle der eine, was der andere ausschließt.
  Erkannt werden „2 Iron", „2er Eisen", „2i" und „Driving Iron". Ausgeschlossen wird nur, **wenn
  etwas übrig bleibt** — ein leerer Beutel wäre schlimmer als ein unpassender Vorschlag.

- **v3.32.0 · 2026-08-16** — **BUGFIX: Der Abgleich riss nach wenigen Löchern ab — lautlos.**
  Gemeldet: Die ersten drei Löcher gehen, dann laufen Uhr und Handy auseinander, und im
  Fehlerprotokoll steht **nichts**. Das Fehlen des Eintrags war der entscheidende Hinweis: Es
  stürzte nichts ab, es **gab auf**.
  Beide Seiten behandelten einen Schreibkonflikt (HTTP 409) mit **einem** zweiten Anlauf und
  kehrten danach still zurück — mit der ausdrücklichen Begründung „der nächste Takt kommt in
  Sekunden". Das stimmte, solange selten geschrieben wurde. Seit das Handy während der Runde alle
  10 Sekunden schreibt (v3.19) und die Uhr bei Bewegung (2026-08-16 (5)), kollidiert auch der
  nächste Takt und der übernächste. Danach lief jedes Gerät für den Rest der Runde für sich.
  **Vier Anläufe mit zufälliger Pause**, auf beiden Seiten. Zufällig ist wesentlich: Zwei Geräte,
  die nach einem Konflikt gleichzeitig neu senden, kollidieren **synchron** wieder — dann hilft auch
  der zehnte Versuch nicht. Vor jedem Anlauf wird vereinigt, nie überschrieben; der andere ist
  gerade auf der Bahn.
  **Und es scheitert nicht mehr lautlos.** Ein stiller Fehlschlag sieht aus wie Erfolg — genau
  daran ist eine ganze Runde ohne Abgleich gelaufen. Die Uhr schreibt jedes Aufgeben ins
  Fehlerprotokoll (Seite 3), das Handy zählt Fehlschläge und meldet sich nach dem dritten.
  **Weniger Kollisionen an der Wurzel:** `caddyLivePush` schweigt, solange ohnehin ein
  Entwurfs-Push aussteht. Wer in eine entprellte Sendung hineinfunkt, erzeugt genau den Konflikt,
  den danach beide Seiten aussitzen müssen.

- **v3.31.0 · 2026-08-16** — **BUGFIX: Korrekturen erreichten die Uhr nie — das Handy schickte den
  Stand vom Rundenstart.**
  Gemeldet: Der erste Score kommt auf der Uhr an, eine **Änderung** desselben Score nicht. Genau so
  war es auch, und die Ursache liegt eine Ebene tiefer als vermutet — nicht in der Merge-Regel,
  sondern in dem, was überhaupt gesendet wurde.
  `draftPush()` schickt `DB._draftRound`. `playSaveDraft()` aktualisierte davon aber **nur `live`**;
  `round` und `ts` stammten unverändert vom **Rundenstart**. Die Uhr bekam also bei jedem Push
  denselben alten Stand. Dass der **erste** Wert trotzdem ankam, lag daran, dass die Uhr dort ein
  leeres Feld füllte — jede Korrektur dagegen wurde von ihr korrekt verworfen, weil der Zeitstempel
  nicht jünger war. **Die Uhr hat sich richtig verhalten; das Handy hat gelogen.**
  Warum es am Handy trotzdem stimmte: `saveDraft()` (lokal) war nie betroffen. Der Fehler war
  ausschließlich an der Uhr sichtbar — und dort sucht man ihn zuletzt.
  `playSaveDraft` schreibt jetzt `round`, `ts` und `live`. **Der Zeitstempel muss mit**, sonst hält
  die Gegenseite den Entwurf für alt und behält ihre eigenen Werte.
  **In die Rundensimulation aufgenommen:** Wert eintragen, senden, **ändern**, senden — und prüfen,
  dass die Korrektur im Repo steht, dass beide Zeitstempel erneuert wurden und dass die Uhr sie nach
  ihrer Regel übernehmen würde. Genau diese Kette hat bisher niemand geprüft.

- **v3.30.0 · 2026-08-16** — **Programm erweitert: Aufwärmen vor der Runde und zwölf weitere Übungen.**
  Recherchiert in öffentlich berichteten Routinen (PGA Tour, Golf.com, Golf Monthly, GolfForever-Blog).
  Genannt werden dort unter anderem Split-Stance-Rotation (auch in Ansprechposition), Einbeinstand
  mit Überkopf-Seitneigung, Rückwärtsschritt mit Drehung und Druck, seitlicher Ausfallschritt mit
  Rotation, Brustwirbelsäulen-Rotation im Halbkniestand und der zweifache Hüftbeuger-Dehnung.
  **Was übernommen wurde und was nicht:** Übernommen ist die **Auswahl** der Bewegungen — sie sind
  Allgemeingut der Golffitness und stehen so in mehreren unabhängigen Quellen. Beschreibungen und
  Begründungen sind eigene Formulierungen; die Anleitungstexte und Videos der Anbieter sind deren
  Werk und werden nicht wiedergegeben. Für die **genaue Ausführung** verweist die Ansicht auf die
  App, für die das Gerät gekauft wurde.
  **Die wichtigste Ergänzung ist Einheit W: Aufwärmen vor der Runde**, zehn Minuten. Das fehlte
  vollständig, obwohl die Heute-Seite es anbietet — und es ist der Teil mit dem besten Verhältnis
  von Aufwand zu Wirkung: Er macht aus dem ersten Abschlag einen Schlag statt eines Probeschlags.
  Zum Abschluss stehen dort zehn Schwünge mit dem schweren Stab — schwer vor leicht, damit sich der
  eigene Schläger danach schnell anfühlt.
  Dazu in **A** Dead Bug, Seitstütz mit Bandzug und Rückwärtsschritt zu hohem Knie; in **B**
  Schulterkreisen über Kopf, gehaltener Seitausfallschritt und Gesäß/Piriformis mit Ball; in **C**
  Seitausfallschritt mit Rotation und Seitschritt mit Drehung.

- **v3.29.0 · 2026-08-16** — **Geräteliste und ein Trainingsplan, der sie liest.**
  **(1) Geräte** unter Fitness: die vorhandene Ausrüstung als bearbeitbare Liste, einmalig befüllt
  aus dem, was fotografiert vorliegt — Swing Trainer, Widerstands- und Loop-Bänder, Türanker,
  Kurz- und Langhantel, Klimmzugstange, Faszienrolle, Massagebälle, Matte, Kissen, Thera-Band.
  **Nicht verfügbar statt gelöscht:** Geräte, die gerade verliehen oder im Keller sind, werden
  abgewählt — dann fallen sie aus dem Plan, ohne dass man sie später neu eintippt.
  **(2) Das Programm liest diese Liste.** Ein Plan, der Geräte voraussetzt, die man nicht hat, ist
  wertlos; einer, der vorhandene nicht nutzt, verschenkt sie. Fehlt ein Gerät, erscheint die Übung
  nicht — mit Zähler, damit man weiß, dass etwas fehlt.
  **Worauf der Plan zielt, und warum nicht auf „mehr Kraft":** Der Schwung ist eine **Rotation um
  eine stabile Mitte**, ausgelöst von unten. Begrenzt wird er selten durch Kraft, sondern durch die
  Trennung von Becken und Brustkorb, durch verkürzte Hüftbeuger, durch fehlende Rumpfstabilität —
  und erst dann durch Schnellkraft. In dieser Reihenfolge sind die drei Einheiten aufgebaut:
  **A Rotation & Rumpf**, **B Beweglichkeit & Regeneration**, **C Schnellkraft & Schwung**.
  **Drei Einheiten, nicht fünf:** Mehr hält neben Runden und Beruf niemand durch, und ein Plan, den
  man nicht hält, ist schlechter als ein kleinerer, den man hält. **Rotation immer beidseitig** —
  Golf ist einseitig; wer nur die Schwungrichtung trainiert, verstärkt die Schiefe. Die
  Gegenrichtung bekommt weniger Sätze, fällt aber nicht weg.
  **Jede Übung nennt ihr Warum.** Eine Übung ohne Begründung macht man falsch oder gar nicht.
  „In den Wochenplan übernehmen" trägt eine Einheit auf den ersten **freien** Tag ein — ein
  vorhandener Plan wird nie stillschweigend überschrieben.

- **v3.28.0 · 2026-08-16** — **Fitness wird eigener Reiter · Wochenplan mit Erinnerungen.**
  **(1) Navigation:** Fitness lag als vierter Reiter unter „Training" — erreichbar, aber nicht
  präsent, und damit genau so oft benutzt, wie es dort stand. „Turnier" wandert unter „Mehr".
  **Die Häufigkeit der Benutzung gehört in die Reihenfolge, nicht die Wichtigkeit des Themas** —
  ein Turnier ist wichtiger als eine Yoga-Einheit, aber man öffnet es ein paar Mal im Jahr.
  **(2) Wochenplan**, kein Kalender mit Datum: „montags Kraft" gilt weiter, auch wenn eine Woche
  ausfällt; ein Datumsplan wäre nach der ersten Verschiebung falsch und müsste gepflegt werden.
  Je Tag Art, Dauer und Schwerpunkt — Übungen gehören in die **erfasste** Einheit, sonst pflegt man
  zwei Listen. Der Fortschritt der Woche vergleicht die **Art**, nicht den Tag: Wer die
  Montags-Einheit am Dienstag macht, hat sie gemacht.
  **(3) Erinnerungen — und was dabei ehrlich gesagt werden muss:** Eine Web-App kann **keine**
  Benachrichtigung schicken, während sie geschlossen ist; dafür bräuchte es einen Push-Server, und
  hier gibt es nur eine statische Seite. Was geht: ein Hinweis, solange die App läuft oder im
  Hintergrund offen ist. Was **zuverlässig** geht: ein Termin im Kalender des Telefons — deshalb
  „📅 In den Kalender", das eine **wöchentliche Serie** mit 30-Minuten-Voralarm erzeugt. Alles
  andere wäre ein Versprechen, das die Technik nicht hält.
  Erinnert wird **einmal je Tag** (sonst schaltet man es nach dem zweiten Hinweis ab) und **nicht an
  Erledigtes**: Eine Erinnerung an etwas bereits Gemachtes ist der schnellste Weg, sie loszuwerden.

- **v3.27.0 · 2026-08-16** — **„Ich habe neu geladen und sehe es nicht" — jetzt sagt es die App.**
  Die App-Hülle läuft nach *stale-while-revalidate*: Sie startet **immer** aus dem Zwischenspeicher
  und holt die neue Fassung im Hintergrund. Das ist richtig — im Funkloch auf dem 14. Loch will
  niemand eine Fehlerseite —, hat aber eine unangenehme Folge: **Nach dem Einspielen zeigt das
  erste Neuladen noch die alte Fassung**, erst das zweite die neue. Wer das nicht weiß, hält eine
  eingebaute Neuerung für nicht vorhanden und sucht den Fehler an der falschen Stelle. Genau das
  ist mehrfach passiert — zuletzt beim Block „Heute geplant" aus v3.26.
  Jetzt meldet die App die neue Fassung selbst, mit einem Knopf, der das zweite Neuladen gleich
  erledigt. **Nur bei einer echten Aktualisierung:** Bei der ersten Installation gibt es nichts zu
  melden, unterschieden über `serviceWorker.controller`.
  Dazu steht die **Fassungsnummer am Fuß der Heute-Seite**. Ob eine Neuerung fehlt oder nur die
  alte Fassung läuft, war bisher nur über die Abgleich-Prüfung herauszufinden — drei Ebenen tief.

- **v3.26.0 · 2026-08-16** — **„Heute geplant": welche Tests und welches Sportprogramm heute dran
  sind.**
  Fällige Tests und Trainingsfokus standen bis v2.31 auf dieser Seite und wurden entfernt — zu
  Recht: Es war eine **Liste**, und Listen sind Planung, kein Tagesgeschäft. Der Wunsch ist ein
  anderer: nicht „was ist alles fällig", sondern „was mache ich **heute**". Das sind zwei bis drei
  Zeilen, und die gehören hierher.
  **Tests:** die überfälligsten zuerst (nie gemacht zählt als unendlich alt), aber **bevorzugt aus
  dem Schwerpunkt des laufenden Blocks** — ein Bunker-Test in einer Wedge-Woche ist zwar fällig,
  aber nicht dran. Erst ab 60 Tagen, und **höchstens zwei**: Wer drei Tests an einem Tag macht,
  macht keinen davon ordentlich.
  **Sportprogramm:** Es gibt keinen hinterlegten Wochenplan, nur erfasste Einheiten — die
  Empfehlung wird deshalb abgeleitet. **Am Turniertag und am Vortag kein Krafttraining**,
  Muskelkater am Abschlag hat noch nie geholfen; Mobilität ja. Sonst Kraft, wenn die letzte Einheit
  drei Tage her ist — im Wettkampfblock vier, dort steht Frische über Reiz. Sonst Mobilität ab zwei
  Tagen. Sonst ausdrücklich **Ruhetag**: Ein Ruhetag ist ein Programm, kein Ausfall — wer ihn nicht
  benennt, trainiert ihn weg.
  Die Regeln stehen als lesbarer Code, nicht in einer Tabelle: Es sind Faustregeln, und man soll sie
  ändern können. Jede Zeile führt direkt dorthin, wo man sie erledigt — Test öffnen, Kraft- oder
  Yoga-Eintrag anlegen.

- **v3.25.0 · 2026-08-16** — **Mehrere Bilder je Artikel: zwei Wege an der Android-Galerie vorbei.**
  Die App fordert seit jeher `multiple` an, und die Verarbeitung läuft über **alle** ausgewählten
  Dateien — der Code war also nie das Problem. Ob man mehrere wählen **kann**, entscheidet die
  Android-Auswahl: Manche Galerie-Apps geben trotz `multiple` nur ein Bild heraus, der
  Dateiauswähler dagegen mehrere. Darauf hat eine Web-App keinen Zugriff.
  Also zwei Wege, die ohne sie auskommen: **Bilder ins Textfeld ziehen** und **aus der
  Zwischenablage einfügen** — beide nehmen mehrere auf einmal. Beim Einfügen wird auch
  `clipboardData.items` ausgewertet, weil manche Systeme das Bild nur dort ablegen; ohne diesen
  Zweig käme nichts an.
  **Alle drei Wege laufen in dieselbe Verarbeitung** (`wikiAddImageFiles`) — sonst hätte jeder seine
  eigene Fehlerbehandlung und seinen eigenen Zähler, und einer davon wäre irgendwann anders.
  Gefiltert wird auf Bilder: Ein versehentlich gezogenes PDF darf nicht als Bild landen.
  Dazu der Hinweis im Editor, der am Handy hilft: **im Auswahlfenster „Dateien" statt „Fotos"**.

- **v3.24.0 · 2026-08-16** — **Mehrere Bilder auf einmal in einen Artikel.**
  Dem Bildfeld fehlte schlicht `multiple`, und der Handler las ohnehin nur `files[0]` — unter
  Android ließ sich damit gar nichts mehrfach auswählen. Wer eine Bildstrecke zu einem Drill anlegt,
  musste den ganzen Weg je Bild einmal gehen.
  **Nacheinander, nicht gleichzeitig:** Jedes Bild wird verkleinert und in die Datenbank
  geschrieben. Parallel liefe auf dem Telefon der Speicher voll, und die Reihenfolge im Text wäre
  zufällig — bei einer Bildstrecke ist die Reihenfolge aber die halbe Aussage.
  **Fehler einzeln:** Ein Bild, das nicht durchkommt (etwa HEIC ohne Decoder), nimmt die übrigen
  nicht mit; am Ende steht „2 von 3 Bildern eingefügt (1 fehlgeschlagen)". Der Repo-Push wird
  **einmal am Ende** angestoßen statt je Bild.
  **Videos bleiben bewusst einzeln:** Sie sind groß, und die Größenrückfrage je Datei wäre in Serie
  eine Zumutung.
  Gegengeprüft im Sandkasten mit drei Dateien, davon eine defekte: zwei eingefügt, Reihenfolge
  erhalten, Fehler gemeldet, Feld sofort geleert.

- **v3.23.0 · 2026-08-16** — **BUGFIX: Das „zweite Fenster", das sich nicht schließen ließ · klebende
  Elemente am Schreibtisch.**
  **(1) Mein Fehler aus v3.21.** Die Dialogregel setzte `top:50%` für **jedes** Blatt — auch für das
  **geschlossene**. Am Telefon schließt sich ein Blatt dadurch, dass es unten aus dem Bild fährt;
  mit `top:50%` stand es stattdessen leer in der Bildschirmmitte, mit sichtbarem ✕, das nichts tut,
  weil das Blatt ja bereits zu ist. Genau das leere Fenster im Bild. Die Dialoggeometrie gilt jetzt
  nur noch für `.sheet.open`.
  **(2) Die festen Elemente waren für ein 640-px-Fenster gebaut**, das unten an einer
  Navigationsleiste endet — mit der Seitennavigation stimmt keine dieser Annahmen mehr. `.toast`
  und `.fab` saßen 84 px über dem unteren Rand, wo jetzt nichts mehr ist, und waren waagerecht auf
  das **ganze Fenster** bezogen statt auf die Inhaltsspalte: Der Aktionsknopf landete am rechten
  Fensterrand statt neben dem Inhalt. Beide folgen jetzt der Spalte. `#subnav` bekommt den Einzug
  der Navigationsspalte, sonst beginnt es darunter.
  **(3) Im Dialog** ist der Geräterand kein Bezugspunkt mehr: Der Fußraum für die
  Handy-Sicherheitszone sah dort aus wie ein Fehler, und Kopfleiste wie Fuß standen über die
  abgerundeten Ecken hinaus.

- **v3.22.0 · 2026-08-16** — **Nur noch EINE Scorekarte im ganzen Programm.**
  Es gab **zwei** Darstellungen: das übliche Raster im Rundendetail (seit v3.12) und im Spielmodus
  noch die alte Liste mit einer Zeile je Loch. Dieselbe Sache zweimal gebaut heißt: Jede
  Verbesserung muss man zweimal machen — und beim zweiten Mal vergisst man es. Genau das war
  passiert: Pfeile, Fairway-Quote und GIR-Prozent aus v3.20 fehlten im Spielmodus vollständig.
  `playCardHtml()` baut jetzt über `roundCardHtml()` mit der laufenden Runde als Datensatz. Der
  **einzige** Unterschied bleibt der Hinweis darunter: Während der Runde fehlen zwangsläufig
  Scores, und darauf muss anders hingewiesen werden als bei einer abgeschlossenen Runde.
  **„Weit links/rechts" jetzt als `←←` und `→→`** statt `⇐`/`⇒`. Die Doppelpfeil-Zeichen
  unterscheiden sich auf 16 px kaum vom einfachen Pfeil; zwei Pfeile nebeneinander sofort. Die
  Aussage ist „dasselbe, nur mehr" — und genau so sieht es jetzt aus.

- **v3.21.0 · 2026-08-16** — **Die App nutzt jetzt große Bildschirme — Handy, Tablet, Desktop.**
  Bisher war alles auf **640 px** festgenagelt: eine Handy-Spalte in der Mitte, mit Leerraum links
  und rechts. In 78 kB CSS gab es genau **zwei** Media Queries, eine davon nur für den
  Karteneditor.
  **Gefragt wird nach Breite, Zeigergerät und Hover — nicht nach „welches Gerät".** Ein Tablet quer
  ist breiter als mancher Laptop, ein Handy quer verhält sich wie ein kleines Tablet; Erkennung
  über den User-Agent war nie verlässlich und ist es heute weniger denn je.
  **Stufe 1 — Breite und Dichte:** `--spalte` wächst in drei groben Stufen (900 → 860 px,
  1280 → 1100, 1800 → 1280). Drei Stufen statt einer stufenlosen Rechnung, damit jede einzeln
  prüfbar bleibt. **Weiter als 1280 px wächst die Textspalte nicht** — lange Zeilen liest niemand
  gern, die Breite gehört dann in mehrere Spalten. Mit Maus (`pointer:fine`) zusätzlich schlankere
  Abstände: Am Handy braucht der Daumen 44 px, mit dem Zeiger nicht.
  **Stufe 2 — mehrspaltig:** `.kachelgrid` setzt unabhängige Karten ab 1000 px in zwei, ab 1600 px
  in drei Spalten (Rekorde, Korrelation). `column-count` statt Grid, weil die Karten
  unterschiedlich hoch sind und ein Grid sonst Lücken lässt; `break-inside:avoid` verhindert, dass
  eine Karte mitten durchreißt.
  **Stufe 3 — Navigation links** ab 1280 px **mit Maus**. Die untere Leiste ist eine
  Handy-Konvention; am Bildschirm gibt die Seitenspalte die vertikale Höhe zurück.
  **Stufe 4 — Blatt als Dialog** in der Mitte statt von unten (eine Telefongeste), und die
  Scorekarte wird ab 1000 px größer gesetzt — achtzehn Löcher nebeneinander sind auf dem Telefon
  unlesbar, auf dem Bildschirm die natürliche Form.
  **Und ein Schalter, der alles übersteuert:** „Schmale Darstellung erzwingen" unter Mehr → Daten.
  Media Queries kennen die Fenstergröße, nicht die Absicht — im geteilten Bildschirm oder in einem
  breiten, aber niedrigen Fenster will man manchmal die schmale Spalte. Jede der Erweiterungen
  hängt an `body:not(.komp)`, der Schalter greift also überall.

- **v3.20.0 · 2026-08-16** — **Scorekarte: Pfeile beim Tee-Ergebnis, Quoten statt Anzahlen.**
  **Tee-Ergebnis als Pfeil:** `H` für Hit, `←`/`→` für links und rechts, `⇐`/`⇒` für „weit",
  `↑`/`↓` für lang und kurz, `×` für Mis-hit — ein Fehltreffer hat keine Richtung. In einer
  16-px-Spalte ist ein Pfeil lesbarer als jede Abkürzung, und die **Richtung ist die Aussage**:
  Dreimal `←` untereinander sieht man, ohne zu lesen.
  **Fairway-Quote am Ende der Tee-Zeile.** Im Nenner nur Par 4 und 5 — auf einem Par 3 gibt es kein
  Fairway, und es mitzuzählen würde die Quote je nach Platz um zehn Punkte drücken.
  **GIR als Prozent statt Anzahl.** „7" sagt nichts ohne die Zahl der gewerteten Löcher: Auf neun
  ist es die Hälfte, auf achtzehn ein Drittel. Nenner sind die Löcher, für die sich GIR überhaupt
  bestimmen lässt.
  Die Summenspalte trägt diese Quoten etwas kleiner — „100%" hätte sonst die feste Spaltenbreite
  gesprengt, und genau die hält die Karte lesbar.

- **v3.19.0 · 2026-08-16** — **Die Empfehlung zieht jetzt von selbst nach · deutsches Datum bei der
  Ausrüstung.**
  **(1) Die Kette hing an Eingaben — und damit an nichts.** Der Live-Zeiger mit der Caddy-Empfehlung
  entstand ausschließlich in `playSaveDraft`, also **nur beim Tippen**. Wer vom Abschlag zum Ball
  geht, tippt nichts: Die Uhr bekam die Empfehlung des letzten Eintrags, gerechnet für einen Punkt,
  an dem man längst nicht mehr steht. Auf dem Weg zum Ball ist das der ganze Weg — die Verbesserung
  aus v3.18 wäre praktisch nie zum Tragen gekommen.
  Auf der **Uhr** dasselbe Muster: Der Push-Takt hing an `lastEditMs` (10 s nach einer Eingabe,
  sonst 60 s). Ihre gemeldete Position war damit bis zu eine Minute alt.
  Jetzt gilt **Bewegung als Ereignis**: Die Uhr meldet sich, wenn sie mehr als 15 m seit der letzten
  Meldung gewandert ist (darunter bleibt die Schlägerwahl gleich, und jede Meldung kostet Funk), und
  das Handy rechnet während der Runde **alle 10 Sekunden** nach. Geschickt wird nur, wenn sich
  etwas geändert hat — anderes Loch, anderer Schläger, oder der Ort um mehr als 10 m verschoben.
  Ohne diese Bedingung wäre es Dauerfeuer auf eine Datei, die ohnehin jede Sekunde gelesen wird.
  **Damit steht die Empfehlung auf der Uhr spätestens 15–20 Sekunden nach dem Stehenbleiben** —
  vorher konnte sie eine Minute alt sein oder ganz ausbleiben.
  **(2) Deutsches Datum bei der Ausrüstung.** Gespeichert wird weiter ISO — nur so lässt sich
  sortieren und vergleichen —, **angezeigt und eingegeben** wird TT.MM.JJJJ. Angenommen werden
  „5.3.26", „05.03.2026" und weiterhin ISO. Nicht Erkanntes **bleibt stehen**, statt still zu
  verschwinden: Ein Datum, das beim Tippen weggeräumt wird, ist ärgerlicher als ein falsches. Nach
  der Eingabe wird neu gezeichnet, damit man am „05.03.2026" sieht, dass es angekommen ist.

- **v3.18.0 · 2026-08-16** — **Beste Rechnung am richtigen Ort: Die Uhr meldet die Position, das
  Handy rechnet dafür.**
  v3.17 hat das Handy zur Autorität gemacht — und damit einen Widerspruch geschaffen: Das Handy
  rechnet mit **seiner** Position, liegt aber oft im Trolley, während man am Ball steht. Zwanzig
  Meter Unterschied sind ein halber Schläger; die bessere Rechnung am falschen Ort ist nicht besser.
  Die Position ist deshalb ein **Parameter** geworden (`caddyFuerPunkt`). Die Uhr meldet mit dem
  Live-Zeiger, wo sie steht — nur mit brauchbarer Genauigkeit, denn eine Position mit 30 m Streuung
  würde die Rechnung verschlechtern statt sie zu verbessern. Das Handy rechnet **für diesen Punkt**
  und schickt das Ergebnis zurück. Die Uhr hat den richtigen Ort, das Handy das bessere Modell.
  **Eine veraltete Position wird verworfen** (über 90 s): Sie sieht aus wie eine gültige, führt aber
  zu einer Empfehlung für einen Ort, an dem niemand mehr steht — das ist schlimmer als gar keine.
  **`playCaddyNow()` ruft dieselbe Funktion** mit der eigenen Position; es gibt weiterhin genau
  **eine** Caddy-Logik, nur zwei Aufrufer. Das Streuungsoval auf der Handy-Karte bleibt an der
  Handy-Position — sonst zeigte es an einem Ort, an dem das Handy gar nicht ist.

- **v3.17.0 · 2026-08-16** — **Das Handy ist jetzt beim Caddy führend — vorher rechneten beide
  Geräte verschieden.**
  Auf die Frage „arbeiten die Caddys gleich?" war die ehrliche Antwort **nein**, und zwar auf einer
  tieferen Ebene als gedacht: Es waren nicht zwei Umsetzungen desselben Modells, sondern **zwei
  verschiedene Modelle**. Das Handy rechnet mit der EV-Engine — Monte Carlo über die gelernte
  Streuung, Lie-Raster, zwei Züge voraus. Die Uhr rechnet mit dem einfacheren „spielt-wie"-Modell
  aus Wind, Temperatur und Lagefaktor. Auf demselben Ball konnten sie verschiedene Schläger nennen,
  und dann glaubt man auf der Bahn keinem von beiden.
  **Die Uhr kann die EV-Engine nicht rechnen** — ihr fehlen die Streuungsdaten und die Rechenzeit.
  Sie rechnet deshalb nicht mit, sondern übernimmt das **Ergebnis**: Das Handy legt seine aktuelle
  Empfehlung in denselben Live-Zeiger, der ohnehin jede Sekunde reist.
  **Mit Position und Zeit**, denn eine Empfehlung ohne den Ort, für den sie gilt, ist gefährlich —
  nach dreißig Schritten stimmt sie nicht mehr. Die Uhr verwirft sie selbst bei anderem Loch, über
  90 Sekunden Alter oder mehr als 20 m Abstand und fällt dann auf ihre eigene Rechnung zurück.
  **Sichtbar gemacht:** „📱" für die übernommene Empfehlung, „🎯" für die eigene. Eine stille
  Vertauschung wäre schlimmer als der Unterschied selbst — man würde der Zahl vertrauen, ohne zu
  wissen, woher sie kommt.
  **Der Gameplan war nie betroffen:** Er wird auf dem Handy gerechnet und reist fertig zur Uhr.

- **v3.16.0 · 2026-08-16** — **Scorekarte um vier Zeilen · Caddy-Konstanten werden abgeglichen.**
  **(1) Neue Zeilen:** **Länge** (steht auf jeder Papierkarte und macht den Score lesbar — ein Bogey
  auf 430 m ist etwas anderes als eines auf 290 m), **Tee-Ergebnis** auf ein Zeichen verkürzt
  (F/L/R/K/W/B — dreimal „R" hintereinander ist eine Aussage, dreimal „Rough rechts"
  ausgeschrieben passt in keine 16-px-Spalte), **GIR** aus `holeGir` **gerechnet** statt getippt,
  und **Strafschläge**. Die letzten drei Zeilen erscheinen nur, wenn es dazu Daten gibt: Eine Zeile
  voller Nullen verdeckt die zwei Stellen, an denen die Runde gekippt ist.
  **(2) Der Kopplungstest vergleicht jetzt die Caddy-Konstanten.** Die Uhr hat einen **eigenen**
  Caddy — eine Portierung von `planCore` mit eigenen Lage-Faktoren. Dieselben Zahlen an zwei Orten
  laufen auseinander, sobald eine Seite geändert wird, und das fällt sonst erst auf der Bahn auf.
  Die Uhr meldet ihre `lieFactor`-Werte, die App vergleicht sie mit `LIE_F` und nennt jede
  Abweichung mit beiden Zahlen.

- **v3.15.0 · 2026-08-16** — **Rough bekommt eigene Erwartungswerte · Scorekarte direkt in der Runde.**
  **(1) Rough wurde als „Fairway plus 0,15…0,35" gerechnet**, linear über die Distanz. Die Richtung
  stimmte, der Verlauf nicht: Nach Broadies Daten ist der Aufschlag bei 20–40 m **kleiner** (der Ball
  liegt ohnehin nah am Grün) und steigt bis etwa 120 m auf rund 0,30, danach kaum noch. Die lineare
  Näherung unterschätzte ihn also **genau dort, wo die Layup-Entscheidung fällt**, und überschätzte
  ihn im langen Bereich. Jetzt eigene Stützstellen.
  **Sichtbare Folge im Prüfstand:** Auf einem Testloch wählt der sichere Modus jetzt 7 Wood, wo
  normal und offensiv 3 Wood spielen — vorher waren alle drei gleich. Genau das ist der Zweck des
  Umschalters; eine Prüfung, die „alle Modi liefern denselben Erwartungswert" verlangte, hat das
  zurecht gemeldet und vergleicht jetzt **dieselbe Option** statt der jeweils besten.
  **Stützstellen verlängert:** Fairway endete bei 210 m, Sand bei 100 m — darüber wurde
  extrapoliert. Auf einem Par 5 mit 240 m Rest rechnete die Engine außerhalb ihres Datenbereichs.
  Und **hohes Gras/Gebüsch** ist nicht mehr dasselbe wie Rough (+0,25): Der Ball liegt tiefer, oft
  ist nur ein Herauslegen möglich.
  **Herkunft im Code vermerkt** (Broadie, *Every Shot Counts* / Interfaces 2012) samt dem
  Einheiten-Vorbehalt: Die Werte sind in **Metern** geführt, Broadies Tabellen in Yards. Das bleibt
  bewusst so — `esOffset` gleicht den Versatz aus deinen echten Runden aus, und eine Umrechnung
  würde alle gelernten Offsets ungültig machen.
  **(2) Die Scorekarte** steht jetzt in der Rundenansicht **über** der Fehlerkosten-Analyse; der
  Knopf „🗒 Scorekarte Loch für Loch" entfällt. Sie ist das, was man nach einer Runde als Erstes
  sehen will — ein eigenes Blatt dafür war ein Umweg, den man zweimal geht und danach nicht mehr.
  Die Reihenfolge ist Absicht: erst das **Ergebnis**, dann die **Ursache**.

- **v3.14.0 · 2026-08-16** — **Nachtrag zu v3.13: Eine vollständige Neun ist vollständig — aber
  keine halbe Achtzehn.**
  v3.13 hat abgebrochene Runden aus den Auswertungen genommen. Der Maßstab war `voll`, und der
  bedeutet „so viele Löcher wie vorgesehen" — eine gespielte **Neun** ist damit vollständig. Für
  **Quoten** (GIR-Rate, Putts je Loch, Strokes Gained) ist das genau richtig. Für **Summen** ist es
  falsch, und drei Stellen haben genau das getan:
  **Putt-Verteilung:** 14 Putts aus neun Löchern standen zwischen 18-Loch-Runden wie eine
  Sternstunde am Grün. **Bestenliste:** „+3" über neun Löchern schlug jede 18-Loch-Runde — es ist
  keine bessere Leistung, sondern eine andere Aufgabe; dasselbe bei der GIR-**Anzahl**.
  **Korrelation:** Zielgröße `toPar` und die Hälfte der Kennzahlen (Strafschläge, Kostenanteile)
  sind Summen — Neun-Loch-Runden dazwischen verschieben **beide** Seiten der Rechnung und erzeugen
  einen Zusammenhang, den es nicht gibt.
  `istAchtzehn(e)` ist jetzt der Maßstab für alles Summierte. Die Korrelation sagt außerdem, **wie
  viele kürzere Runden draußen bleiben** — sonst wundert man sich, warum vier gespielte Runden
  nicht reichen.
  **Quoten bleiben bewusst bei `voll`:** Sie sind von der Rundenlänge unabhängig, und sie
  auszuschließen würde nur Daten wegwerfen — bei jemandem, der oft neun Löcher spielt, sogar die
  meisten.

- **v3.13.0 · 2026-08-16** — **BUGFIX: Eine abgebrochene Runde rechnete sich schön — um 44 Schläge.**
  `toPar` war `gross - PAR` mit dem Par der **ganzen Seite**. Wer sieben Löcher spielt und jedes in
  Par+1 abschließt, bekam **−37 statt +7**: 35 gespielte Schläge minus 72 Par der vollen Runde. Der
  Wert ging in **Rundenliste, Verlaufskurve, Korrelation, Bestenliste und Scoring-Average** — eine
  abgebrochene Trainingsrunde sah dort aus wie die Runde des Jahres und zog jede Auswertung mit.
  Die Trennung ist jetzt ausdrücklich:
  **`toParThrough`** (= `gross − parPlayed`) sagt, was auf den gespielten Löchern wirklich passiert
  ist — immer belastbar, und in Rundenliste, Detailansicht, Teilen-Text und Scorekarte mit dem
  Zusatz „durch N Löcher" sichtbar. Verschweigen wäre schlimmer als eine Zahl mit Fußnote.
  **`toPar`** gibt es nur noch bei **vollständiger** Runde, sonst `null` — damit fallen Teilrunden
  aus Verlauf, Korrelation und Bestenliste automatisch heraus, statt sie zu verfälschen. `voll` und
  `erwartet` sagen ausdrücklich, was gilt; vorher konnte man nicht einmal erkennen, dass etwas
  fehlt. Eine gespielte **Neun** zählt dabei als vollständig — nicht jede Runde hat 18 Löcher.
  **Zwei Folgefunde:** Der auf 18 normierte Schnitt rechnete `toPar × 18 ÷ counted` — schon der
  Ausgangswert war falsch. Er nimmt jetzt `toParThrough` und rechnet **erst ab neun Löchern** hoch;
  aus vier Löchern auf achtzehn zu schließen ist selbst mit richtigem Wert geraten. Und in der
  Verlässlichkeits-Verteilung stand `e.putts` — die **Summe** der Runde: 14 Putts aus sieben Löchern
  sahen zwischen 18-Loch-Runden aus wie eine Sternstunde am Grün. Nur noch volle Runden.
  **Was schon richtig war:** Der Handicap-Index nimmt ausschließlich Turniere, konnte also nie von
  einer abgebrochenen Trainingsrunde bewegt werden. Quoten je Loch (GIR, Fairways, Putts/Loch,
  Strokes Gained) sind von der Rundenlänge ohnehin unabhängig.

- **v3.12.0 · 2026-08-16** — **Scorekarte im gewohnten Raster · neue Ausrüstungs-Übersicht.**
  **(1) Die Scorekarte war eine Liste** — je Loch eine Zeile. Vollständig, aber nicht die
  Darstellung, die ein Golfer kennt, und die typische Frage „erste gegen zweite Neun" musste man
  im Kopf zusammenzählen. Jetzt das übliche Raster: Löcher nebeneinander, darunter Par, Vorgabe,
  Score und Putts, mit **OUT / IN / GESAMT** als eigene Spalten.
  **Zwei Blöcke untereinander** statt achtzehn Spalten — auf einem Telefon wären 21 Spalten je
  16 px unlesbar; bei einer 9-Loch-Runde entfällt der zweite Block ganz. Dazu die Zeichen der
  Papierkarte: **Birdie im Kreis, Doppelbogey doppelt gerahmt** — man liest die Runde, ohne die
  Zahlen zu vergleichen. `table-layout:fixed` und `tabular-nums`, damit die Spalten stehen; ohne
  beides zieht ein dreistelliger Summenwert die Zeile schief, und genau das macht eine Papierkarte
  sonst unlesbar.
  **(2) Ausrüstung** unter Schläger: Ball, Driver, Hölzer, Eisen, Wedges, Putter, Bag, Trolley,
  Schuhe, Handschuh, Entfernungsmesser, Sonstiges. Die Schlägerliste beantwortet „wie weit", nicht
  „womit".
  **Jeder Eintrag trägt „seit wann"** — und das ist der eigentliche Wert: Ändern sich deine Zahlen
  ab einem bestimmten Tag, siehst du, ob dort etwas gewechselt wurde. Besonders beim **Ball**, der
  Rollverhalten am Grün und Spin beim Wedge verändert; wer die Marke wechselt und den Grund in der
  Technik sucht, sucht Wochen. Das Datum wird **nur beim Modellwechsel** gesetzt, nicht beim
  Nachtragen einer Notiz — sonst wäre es wertlos.
  **Freie Felder statt Auswahllisten:** Golfausrüstung ändert sich schneller, als eine gepflegte
  Liste je sein könnte.

- **v3.11.0 · 2026-08-15** — **Changelog ausgelagert · Uhr funkte dauerhaft für den Kopplungstest.**
  **(1)** Das Changelog war auf **283 kB mit 234 Einträgen** gewachsen — ein Achtel dieser Datei,
  die bei jedem Start vollständig geladen und geparst wird. Die letzten **40** Fassungen bleiben
  hier, alles Ältere steht in `changelog-archiv.md` im Repo. **Die Begründungen bleiben vollständig
  erhalten**, sie stehen nur woanders; wer in der Geschichte sucht, sucht ohnehin gezielt. Die Datei
  schrumpft damit von 2,18 auf 1,96 MB.
  **Beim Sortieren fiel ein Fehler auf:** Ein Eintrag (v2.94) stand über neueren, weil er ans obere
  Ende geschoben statt einsortiert wurde. Der Prüfstand hält jetzt fest, dass die Reihenfolge der
  Fassungsnummern stimmt — sonst liest man beim nächsten Suchen an der falschen Stelle.
  **(2)** Der Beantworter des Kopplungstests auf der Uhr lief `while(true)` mit fünf Sekunden Takt —
  **dauerhaft**, auch während einer Runde und mit dem Arm unten. Für ein Werkzeug, das man einmal in
  der Woche benutzt, hätte das genau den Akku gekostet, den die Runde braucht. Er läuft jetzt nur
  auf dem Startbildschirm der Uhr und nicht im Ambientmodus; der Hinweis im Test sagt das.

- **v3.10.0 · 2026-08-15** — **BUGFIX: Neu angelegte Tests erreichten bestehende Datenbanken nie.**
  Die drei Chip-Tests aus v3.04 tauchten unter Training → Tests nicht auf — und der Grund ist
  grundsätzlicher, als er aussieht: **`SEED.testDefs` gilt nur für eine leere Datenbank.** Wer die
  App schon benutzt, hat seine eigene `DB.testDefs`, und die wurde **nie** mit dem Seed abgeglichen.
  Damit war **jeder** seit der ersten Installation hinzugefügte Test für bestehende Nutzer
  unsichtbar; die Chip-Tests haben es nur sichtbar gemacht.
  `ensureSeedTests()` gleicht jetzt beim Start ab, nach drei Regeln:
  **Nur ergänzen, nie überschreiben** — an einem Test kann etwas geändert worden sein, und der Seed
  weiß davon nichts. **`ui.seedTestsAdded` merkt sich, was schon einmal angelegt wurde** — ein
  gelöschter Test kommt damit nicht bei jedem Start zurück; die Löschung war eine Entscheidung.
  **Die Gewichte der Seed-Tests werden angeglichen**, denn sie sind kein Nutzerwert, sondern die
  Einteilung eines Ganzen: Kommen Tests dazu, müssen die übrigen anteilig Platz machen, sonst
  summieren sich die Prozente auf mehr als hundert.
  Gegengeprüft im Sandkasten gegen eine bestehende Datenbank: ergänzt, kein zweites Mal, keine
  Doppelten, gelöschter Test bleibt gelöscht, Summe der Gewichte bleibt bei rund 1.

- **v3.09.0 · 2026-08-15** — **Der Kopplungstest fährt jetzt einen ganzen Prüfplan ab.**
  Eine einzelne Distanz beweist wenig — sie kann auf einem harmlosen Loch zufällig stimmen. Der Test
  prüft deshalb dort, wo es weh tut: am **vertauschten** Loch (der Fehler vom Platz), am
  **längsten** (große Distanz, große Wirkung) und am **kürzesten** (andere Fehlerklasse), und zwar je
  Loch an **drei Positionen** — am Abschlag, auf halber Strecke, kurz vor dem Grün. Ein
  Vorzeichenfehler fällt nur an einer davon auf.
  Dazu: **Schlägertabelle** (Anzahl *und* ein konkreter Wert — eine leere oder veraltete Tabelle
  gleicher Länge fiele sonst nicht auf), **Auswahllisten** (Anzahl *und* erster Eintrag — die Zahl
  allein unterscheidet zwei verschiedene Listen gleicher Länge nicht; genau dieser Fehler ist in
  v2.99 aufgetreten), eine **Caddy-Empfehlung** und die **Datenquelle** der Uhr (`watch.json` oder
  große Datei).
  **Jede Aufgabe trägt die Erwartung dieser App mit.** Verglichen wird nicht „hat geantwortet",
  sondern „rechnet dasselbe"; am Ende steht „x von y Prüfungen bestanden" mit jeder Abweichung
  einzeln in Metern. Toleranz 2 m bei Distanzen (beidseitige Rundung), sonst exakt — wer großzügig
  prüft, prüft nichts.
  **Nebenbei eine Fehlmeldung behoben:** „✗ Schreiben auf draft.json abgelehnt" erschien, sobald
  **keine Runde lief** — seit v3.05 schreibt `draftPush` dann bewusst nichts, damit es die laufende
  Runde des anderen Geräts nicht überbügelt. Die Prüfung hielt das für einen Fehler und schickte auf
  die falsche Fährte. Sie prüft das Schreibrecht jetzt über die Testdatei und sagt ausdrücklich,
  dass `draft.json` unangetastet bleibt.

- **v3.08.0 · 2026-08-15** — **Kopplungstest: Die Uhr rechnet mit, die App vergleicht.**
  Die Rundensimulation prüft diese App gegen einen nachgebauten Worker — sie kann prinzipbedingt
  nicht prüfen, ob die **Uhr** dieselben Zahlen rechnet. Genau daran ist die letzte Runde
  gescheitert: Die Uhr zeigte 40 m, wo die App 300 m zeigte, und gemerkt hat man es erst auf der
  Bahn.
  Der Test legt eine Frage in `probe.json` — Platz, Loch, Testposition — und die Uhr antwortet mit
  dem, was **sie** daraus macht: Distanz zur Grünmitte, Front/Back, Schlägerzahl, Auswahllisten,
  eigene Fassung. Die App stellt beide Zahlen nebeneinander; abweichen darf nur der Rundungsfehler.
  **Gefragt wird bevorzugt ein Loch mit vertauschtem Tee/Grün** — dort ist der Fehler aufgetreten,
  also wird genau dort geprüft. Der Punkt liegt 150 m vor dem Grün auf der Spiellinie, also nah
  genug an der Wirklichkeit.
  **Eigene Datei, keine Spieldaten:** `probe.json` ist unter 1 kB groß und enthält ausschließlich
  Frage und Antwort. Die Uhr beantwortet sie, solange ihre App offen ist — ohne laufende Runde, und
  sie lädt dafür die Karte des **gefragten** Platzes, nicht die gerade geladene; sonst wäre der
  Vergleich wertlos.
  **Und der Test meldet beide Fassungen.** Zwei Geräte mit unterschiedlichem Stand sehen von außen
  gleich aus — dieser eine Wert erspart die halbe Fehlersuche.

- **v3.07.0 · 2026-08-15** — **Eine ganze Runde durchgespielt — zwei Fehler gefunden, die auf dem
  Platz teuer geworden wären.**
  Neu: `runde-simulation.js` (mit `runde-harness.js`) spielt eine vollständige Runde auf einem
  nachgebauten Nordplatz durch — 18 Löcher, zwei davon mit vertauschtem Tee/Grün, gegen einen
  **nachgebauten Worker** mit `draft.json` und `watch.json`. Geprüft werden Rundenstart, Distanzen,
  Caddy am Tee und vom Fairway, Eingaben, beide Sync-Richtungen mit einer simulierten Uhr,
  Lochwechsel, Karten-Auslieferung und Verwerfen. **43 Prüfungen.**
  **Fund 1 — der Loch-Zeitstempel kam nie im Entwurf an.** `playSaveDraft` rief
  `const r=playRound(); playTouchHole(...)` — erst die Runde bauen, dann stempeln. `playRound()`
  **kopiert** die Löcher (`{...h}`), also trug der Entwurf weiter die alte Marke. Damit war die
  ganze Neuerung aus v2.98 wirkungslos: Der Abgleich verglich veraltete Zeitstempel und fiel auf
  „nur leere Felder füllen" zurück — genau die Änderungen, die ankommen sollten, kamen wieder nicht
  an. Jetzt stempelt es **vor** dem Bauen.
  **Fund 2 — Uhrzeit-Versatz kippt jede Korrektur.** Geht die Uhr eine Minute vor, trägt jede ihrer
  Eingaben einen Zeitstempel aus der Zukunft; jede Korrektur am Handy gilt danach als „älter" und
  wird verworfen. Man korrigiert, und nichts passiert — auf dem Platz hält man das für einen
  Übertragungsfehler. `playTouchHole` setzt jetzt **mindestens eine Sekunde nach dem zuletzt
  bekannten Stand dieses Lochs**: Wer gerade tippt, hat das letzte Wort, unabhängig vom Gang der
  Uhren.
  **Was die Simulation bestätigt hat:** Distanzen stimmen auch auf den vertauschten Löchern (332 m
  statt der 4 m, die eine rohe Lesung ergäbe), der Caddy empfiehlt aus 120 m das 9er Eisen, die
  Uhr-Datei enthält den gespielten Platz **mit** aufgelöster Karte, der Lochwechsel schreibt sofort,
  und nach dem Verwerfen steht der Grabstein im Repo — während ein Gerät ohne eigene Runde nichts
  mehr überschreibt.

- **v3.06.0 · 2026-08-15** — **BUGFIX: Die Uhr zielte auf einigen Löchern auf den ABSCHLAG statt aufs
  Grün — 40 m statt 300 m.**
  Die gespeicherte Karte ist nicht das, was diese App anzeigt. `holeRef()` leistet beim **Lesen**
  dreierlei: Es wendet `swap` an (Tee und Grün vertauscht — `applyGeoOverrides` setzt nur die
  **Marke** `holes[n].swap`, die Punkte bleiben vertauscht liegen), ergänzt ein fehlendes Grün aus
  dem nächsten `green`-Feature, und dreht die Spiellinie in Spielrichtung.
  Die Uhr las `holes[n].green` **roh**. Auf jedem Loch mit gesetztem `swap` zielte sie damit auf den
  Abschlag: Wer am Tee steht, sieht dort rund 40 m statt 300 m — und weil nur einzelne Löcher eine
  Vertauschung tragen, **nur auf einigen Löchern**. Auf Löchern ohne gespeichertes Grün zeigte sie
  gar nichts. Der Caddy rechnete auf demselben falschen Ziel; seine Empfehlungen mussten Unsinn sein.
  **Nicht die Regeln auf der Uhr nachgebaut** — das wären zwei Wahrheiten, die auseinanderlaufen.
  Die Uhr bekommt die Karte jetzt so, wie diese App sie **sieht**: `watchGeo()` löst Tee und Grün
  über `holeRef` auf, entfernt die dann bereits angewendete Marke, rechnet `distM` neu und lässt die
  `overrides` weg. Für den Rückfallweg über die große Datei beachtet die Uhr `swap` zusätzlich
  selbst.

- **v3.05.0 · 2026-08-15** — **NOTFALL: Das Handy hat die laufende Runde der Uhr im Sekundentakt
  gelöscht.**
  Nach einer Runde gemeldet: Eingaben der Uhr kamen nicht an, der Caddy empfahl Unsinn, Distanzen
  stimmten nicht. Die Hauptursache ist ein Fehler, den **ich** in v3.02 eingebaut habe.
  **Der `!eigen`-Zweig.** Die Sperre „schreibe keinen verworfenen Entwurf zurück" prüfte
  `if(tomb && (!eigen || tomb>=eigen))`. Wer selbst **keine** Runde offen hat, aber irgendwann
  einmal eine verworfen hat, schrieb damit **alle fünf Sekunden** den Grabstein in `draft.json` —
  auch während die Uhr spielte. Deren Runde wurde also im Sekundentakt aus der gemeinsamen Datei
  gelöscht: Eingaben verschwanden, bevor das Handy sie lesen konnte, und die Uhr beendete ihre
  Runde, sobald der Grabstein jünger war als ihr Rundenbeginn. Der Caddy rechnete auf einem Stand,
  der ständig zerfiel.
  Richtig ist: Der Grabstein gehört nur in die Datei, wenn es einen **eigenen** Entwurf gibt, der
  älter ist als die Marke — dann hat man ihn selbst verworfen. **Ohne eigenen Entwurf wird gar
  nicht geschrieben.** Ebenso beim Lesen: Ohne eigene Runde ist die Marke nur die Notiz „hier lief
  mal etwas", kein Befehl. Auf der Uhr wird sie jetzt gegen die **letzte eigene Eingabe** geprüft
  statt gegen den Rundenbeginn — wer gerade tippt, hat die jüngere Aussage.
  **Zweite Ursache, unabhängig davon: fehlende Platzkarten.** `watchPayload` gab die Karte nur für
  die **drei zuletzt gespielten** Plätze mit. Das ist genau dann falsch, wenn es darauf ankommt:
  Beim ersten Mal auf einem Platz gibt es noch keine Runde, also stand die Uhr ohne Karte auf der
  Bahn. Jetzt: die letzten acht, **plus** der Platz der laufenden Runde, **plus** alle mit einer
  Runde von heute. Die Karte ist der Grund, warum die Uhr überhaupt Distanzen zeigt — dort zu sparen
  war die falsche Stelle.
  **Und ein Weg, das künftig vorher zu sehen:** „🔧 Abgleich prüfen" meldet jetzt, für welche Plätze
  die Uhr-Datei eine Karte enthält — und warnt ausdrücklich, wenn der Platz der laufenden Runde
  fehlt.

- **v3.04.0 · 2026-08-15** — **Drei neue Chip-Tests — jeder misst eine andere Ursache.**
  Bestand waren **ein** reiner Chip-Test (`chip3`, drei Lagen) und drei Kurzspiel-Tests, die etwas
  anderes messen: `scrambling` und `updown` das **Ergebnis** (Par gerettet oder nicht), `sgpressure`
  eine Mischung aus fünf Stationen. Keiner sagt, **warum** ein Chip danebengeht.
  **`chiplande` — Landepunkt /20:** Kreis von 1 m, 6 m und 12 m entfernt, je 10 Bälle; gezählt wird
  nur, ob der Ball IM Kreis aufkommt. Der Kern des Chippens ist nicht das Loch, sondern der
  Landepunkt — erst danach entscheidet das Rollverhältnis. Die vorhandenen Tests messen das Ende
  und verdecken damit, ob der Fehler in der Landung oder im Rollen lag.
  **`chiproll` — Rollverhältnis /15:** gleicher Landepunkt, drei Schläger (PW/9er/7er), je 5 Bälle,
  Carry und Gesamtstrecke. Misst weniger Können als **Wissen**: Er liefert die Zahlen, mit denen die
  Schlägerwahl am Grün eine Rechnung statt eines Gefühls wird. Bewertet wird die **Streuung** — ein
  Verhältnis nützt nur, wenn es sich wiederholen lässt.
  **`chipleiter` — Chip-Distanzleiter /16:** 5/10/15/20 m, je 4 Bälle, Restdistanz messen. Die
  Up-&-Down-Rate sagt „35 %", aber nicht, woher die Fehlversuche kommen; meist von jenseits 15 m —
  dann trainiert man die falschen Chips.
  **Gewichtung als Ganzes behandelt:** Drei Tests dazuzunehmen, ohne die anderen anzupassen, hätte
  die Summe von 0,97 auf 1,09 getrieben — die Oberfläche zeigt Gewichte als Prozent, und 109 % sind
  keine Prozente. Alle Werte wurden im selben Verhältnis skaliert (Faktor 0,894); die **Rangfolge
  unter den bisherigen Tests bleibt exakt erhalten**, sie geben nur anteilig Platz ab. Kurzspiel
  trägt damit rund 30 % der Gesamtgewichtung.
  **Altbefund nebenbei:** `R10 Dispersion (/30)` hat einen `weightKey`, aber **kein** `weight` — der
  Test läuft damit gar nicht in der Gewichtung mit, obwohl er so aussieht. Nicht stillschweigend
  geändert, weil das eine inhaltliche Entscheidung ist.

- **v3.03.0 · 2026-08-15** — **Wissensdatenbank: Verweise, Inhalt, Aufräumen — und eine Brücke zum
  eigenen Spiel.**
  **(1) Der Artikel endete in einer Sackgasse.** Tags, Bearbeiten, Löschen — kein Weg zu verwandten
  Inhalten; **kein einziger** der 84 Artikel enthält einen internen Verweis (nachgezählt), und von
  Hand pflegt das niemand. `wikiRelated` rechnet sie stattdessen: gemeinsame Tags ×3 (der stärkste
  Hinweis, weil bewusst vergeben), gleiche Kategorie ×2, gleicher Bereich ×1. Drei Vorschläge, und
  **unter einem Punkt lieber keine** — ein Kasten voller Zufallstreffer wird nach zweimal Lesen
  ignoriert.
  **(2) `grundpfeiler` steht auf allen 84 Artikeln.** Ein Merkmal, das alle tragen, unterscheidet
  nichts; es kostete nur in jeder Tag-Zeile Platz und stand in jeder Tag-Liste obenan. Es
  verschwindet aus der **Anzeige**, bleibt aber in den **Daten** — es ist die Herkunftsangabe, und
  `gpDiff` vergleicht normalisierte Tags: Wegschreiben hieße, alle 84 Artikel beim nächsten Import
  als „geändert" zu melden.
  **(3) 34 Kategorien auf 84 Artikel — die Hälfte mit genau einem.** Zusammengelegt nach **Inhalt**,
  nicht nach Zählung: „Rhythmus & Tempo" gehört zum Schwungablauf, ein Flop Shot ist ein Pitch,
  Hanglagen und Rough sind beides Lagen. Jetzt 24 Kategorien, keine mit nur einem Artikel.
  **Die Feinheit geht nicht verloren:** Die alte Kategorie bleibt als **Tag** (`green-reading`,
  `flop-shot` …). Angewandt auf `gplib` **und** — einmalig über `migrateWikiCats()` — auf die
  eigenen Artikel; beides, weil sonst der nächste Import alle betroffenen als „geändert" meldete.
  **(4) Die Bibliothek wusste nichts vom Spiel.** Die App kennt die schwächste SG-Kategorie, die
  Bibliothek die Übungen — beides berührte sich nicht, man musste die Verbindung im Kopf herstellen
  und dann selbst suchen. `wikiSGHtml` zeigt jetzt oben in der Bibliothek: „Schwächste Kategorie:
  Kurzes Spiel → das hier", Übungen zuerst. **Nur ohne aktiven Filter** — wer sucht, will
  Ergebnisse, keinen Vorschlag dazwischen.
  **(5) Lange Artikel ohne Einstieg** (der längste hat 5071 Zeichen, gut acht Bildschirme). Ein
  Inhaltsverzeichnis ab drei Überschriften; die Abschnitte gab es längst, die Frage-Suche arbeitet
  damit — sie waren nur nie sichtbar. Die Sprungmarken bildet `mdToHtmlWiki` nach **derselben Regel**
  wie `wikiTocId`, sonst zeigte das Verzeichnis ins Leere.

- **v3.02.0 · 2026-08-15** — **Eine verworfene Runde ist jetzt auf beiden Geräten verworfen.**
  Bisher schrieb das Verwerfen eine **leere** `draft.json` — und leer heißt für die Gegenseite nur
  „gerade keine Runde im Repo", nicht „diese Runde ist verworfen". Das andere Gerät spielte weiter,
  sein nächster Push legte die Runde wieder an, und weil der einen **jüngeren** Zeitstempel trug,
  kam sie auch auf dem ersten Gerät zurück. **Dieselbe Fehlerklasse wie bei den gelöschten
  Platzkarten in v2.84: Ein Fehlen lässt sich nicht übertragen, ein Datum schon.**
  Die kleine Datei trägt deshalb `discardedTs`. Wer sie liest und eine Runde hat, die **älter** ist
  als dieses Datum, beendet sie — **ohne Rückfrage**, denn die Entscheidung ist drüben schon
  gefallen; eine zweite Frage wäre nur eine Falle. Gespeichert wird nichts.
  **Nur eine jüngere Marke zählt** — sonst beendete eine alte Marke jede neue Runde sofort wieder.
  Und `draftPush()` schreibt einen verworfenen Entwurf **nie** zurück; ohne diese Sperre käme die
  Runde beim nächsten Takt aus dem eigenen Gerät zurück.
  Die Marke bleibt in der Datei stehen, bis eine neue Runde beginnt: Ein Gerät, das erst Stunden
  später online kommt, hätte sonst nichts mehr, woran es die Löschung erkennt.

- **v3.01.0 · 2026-08-15** — **Nachtrag: Der Fuß klebte formal korrekt und sichtbar gar nicht.**
  v3.00 hat die beiden Leisten zu einer zusammengefasst — die klebte aber weiterhin nicht am
  Bildschirmrand. Der Grund steht seit v1.55 in dieser Datei, ich habe ihn nur nicht auf den Fuß
  angewandt: **Ein klebendes Element hält sich nur innerhalb seines Elternelements.** Der Fuß saß
  am Ende des Formulars, sein Elter endet also genau dort — es gibt nichts, wovon es sich abheben
  könnte. `position:sticky` war formal korrekt und wirkungslos.
  Dieselbe Lösung wie damals beim ✕: **raus aus dem scrollenden Bereich.** `#sheetFoot` ist jetzt
  ein Geschwister von `#sheetBody` in der Flex-Spalte des Blatts; `#sheetBody` trägt `flex:1 1 auto`,
  also sitzt der Fuß zwangsläufig unten und scrollt nie mit — ganz ohne `sticky`.
  Damit entfällt auch der Fußraum-Zuschlag: Der Fuß nimmt dem Scrollbereich seine Höhe ohnehin weg.
  `openSheet` und `closeSheet` leeren ihn, sonst trüge das nächste Blatt die Score-Stepper des
  vorigen.

- **v3.00.0 · 2026-08-15** — **BUGFIX: Zwei klebende Leisten übereinander — der Score war nicht
  eintragbar.**
  Die Lochnavigation **und** die Score-Leiste klebten jede für sich am unteren Rand. Zwei Elemente
  am selben Anschlag liegen zwangsläufig übereinander: Die Navigation (z-index 12) verdeckte die
  Score-Stepper (z-index 4). Beim Scrollen wanderten beide mit und schnitten mitten durch das
  Formular — der Versatz im Bild —, und eintragen ließ sich der Score gar nicht.
  Entstanden ist das in zwei Schritten, die für sich jeweils richtig waren: die Navigation klebt
  seit v1.66, die Score-Leiste seit v2.53. Zusammen ergeben sie einen Widerspruch, den man erst auf
  dem Gerät sieht — mein Fußraum-Zuschlag aus v2.95 hat ihn zusätzlich verdeckt.
  Jetzt **ein** klebender Fuß (`.play-foot`), der beides enthält: oben Score und Putts, darunter die
  Navigation. Nichts kann mehr etwas anderes verdecken, weil es nur noch **einen** Anschlag gibt.
  Der Prüfstand hält jetzt fest, dass `.play-close` und `.play-navbar` nicht mehr selbst kleben —
  das ist die Bedingung, die beim nächsten Umbau als Erstes zurückrutschen würde.

- **v2.99.0 · 2026-08-15** — **BUGFIX: Approach-Distanz und 1.-Putt-Länge kamen als Fremdwörter an.**
  Gemeldet als „einige Werte werden nicht übertragen". Übertragen wurden sie — nur waren es die
  falschen. Ursache ist meine `watch.json` aus v2.96: Die Uhr liest ihre **Auswahllisten** aus der
  **obersten Ebene** der Datei (`strList(db, "approachBuckets")` und elf weitere). Die schlanke
  Fassung enthielt sie nicht, also fiel die Uhr auf ihre **eingebauten Vorgaben** zurück — und die
  lauten anders: „100–150m" statt „110–140", „<50m" statt „<20".
  Der auf der Uhr gewählte Wert kam damit am Handy an, stand dort aber in keiner Auswahlliste: Die
  Maske zeigte ihn nicht, die Auswertung ordnete ihn keinem Band zu. Von außen sieht das aus wie
  „wird nicht übertragen" — der unangenehmere Fall, weil die Daten scheinbar da sind.
  `watchPayload()` gibt die zwölf Listen jetzt mit; sie stammen **1:1 aus den `strList`-Aufrufen der
  Uhr**, nicht aus einer eigenen Einschätzung.
  **Die Lehre steht im Code:** Wer eine schlanke Fassung baut, muss alles mitnehmen, was der
  **Empfänger liest** — nicht das, was der Absender für wichtig hält. Beim ersten Bau habe ich nach
  „welche Daten braucht die Uhr fachlich?" ausgewählt und dabei übersehen, dass Auswahllisten
  ebenfalls Daten sind.
  **Zu prüfen nach dem Einspielen:** Werte, die zwischen dem 14. und 15. August auf der Uhr gesetzt
  wurden, können aus der falschen Liste stammen. Im Rundeneditor am Handy sind sie sichtbar und mit
  einem Tipp zu berichtigen.

- **v2.98.0 · 2026-08-15** — **Änderungen kommen jetzt auf dem anderen Gerät an.**
  Der Lochwechsel lief schnell, Eingaben nicht — und das lag nicht am Takt, sondern an der Regel:
  Der Abgleich füllte nur **leere** Felder („wer schon etwas drinstehen hat, behält es"). Das
  schützt vor gegenseitigem Überschreiben, macht aber das **Ändern** unmöglich: Wer den
  Tee-Schläger am Handy korrigiert, sah auf der Uhr weiter den alten Wert. Neue Eingaben kamen an,
  Korrekturen nie.
  Um zu entscheiden, wessen Wert gilt, braucht es ein Datum. Der Entwurf hatte bisher nur **einen**
  Zeitstempel für die ganze Runde — zu grob: Der sagt nur, welches **Gerät** zuletzt irgendetwas
  getan hat. Wer auf Loch 7 tippt, während das andere Gerät Loch 3 korrigiert hat, hätte dessen
  Korrektur überschrieben.
  Deshalb **je Loch** (`h.ts`): Wer ein Loch zuletzt bearbeitet hat, gewinnt für dieses Loch —
  feldweise. **`null` löscht weiterhin nie**; wer ein Feld leeren will, tut das am selben Gerät.
  Fehlt der Zeitstempel (Entwurf von vor dieser Fassung), bleibt es beim alten, sicheren Verhalten.
  **Nicht je Feld:** Das wäre genauer, kostet aber je Loch ein Objekt mit zwanzig Zeitstempeln. Zwei
  Geräte bearbeiten praktisch nie dasselbe Loch gleichzeitig — sie bearbeiten verschiedene, und
  genau das trennt der Loch-Zeitstempel sauber.
  Dazu: Nach dem Übernehmen wird die **ganze Eingabemaske** erneuert, wenn sie offen ist. Vorher
  wurden nur Score, Putts und Strafschläge nachgezogen — die übrigen Felder standen weiter auf dem
  alten Wert, obwohl die Daten längst stimmten.

- **v2.97.0 · 2026-08-15** — **An der Flugbahn steht jetzt Schläger und Schwungart — und was zählt.**
  Bisher stand an jeder Bahn nur die Meterzahl. Beim Nachbearbeiten ist aber genau die Frage:
  **womit** war das, und war es ein **voller** Schwung? Ohne beides musste man jeden Punkt einzeln
  antippen — bei zwölf Schlägen zwölfmal.
  Jetzt: Meterzahl, darunter „7 Iron · ¾". Ein **voller** Schwung wird bewusst **nicht**
  beschriftet — er ist der Normalfall, und eine Angabe, die bei neun von zehn Schlägen dasteht,
  liest niemand mehr.
  **Das ✓ markiert, was in die gelernten Schlägerlängen eingeht:** Schläger gesetzt, voller
  Schwung, Messung genau genug (`gpsGewicht>0`), jünger als ein Jahr. Gerechnet wird das in
  **einer** Funktion (`shotZaehlt`), die dieselben Bedingungen prüft wie `clubMeasured` — sonst
  sagt die Marke auf der Karte das eine und die Rechnung tut das andere. Wer die Regel ändert,
  ändert beides.
  **Altbestand:** Ein fehlendes `swing` bedeutete schon immer „voll", und die Rechnung liest es
  auch so. Für die **Anzeige** ist eine Leerstelle aber nicht dasselbe wie eine Angabe — man sieht
  einem Schlag ohne Feld nicht an, ob er als voll gilt oder ob nur niemand etwas eingetragen hat.
  `migrateSwingVoll()` schreibt es deshalb einmalig hin: alle `gpsShots`, alle Runden-Schläge und
  der laufende Entwurf. **An der Auswertung ändert das nichts** — leer und „Voll" sind dort
  gleichbedeutend. Einmalig über `ui.swingMigriert`, sonst liefe sie bei jedem Start über alle
  Runden.

- **v2.96.0 · 2026-08-14** — **`watch.json`: Die Uhr lädt 200 kB statt 3 MB.**
  Dieselbe Trennung wie bei `draft.json`, eine Ebene höher. Die Uhr liest aus der Datenbank nur
  Plätze, Schläger, Optionen, Handicap und Gameplans — Runden, Tests, Fitness,
  Launch-Monitor-Sitzungen, Notizen und die Wissensdatenbank machen den weitaus größten Teil der
  3 MB aus und interessieren sie nie. Trotzdem lud und **parste** sie beim Start alles, auf einer
  Wear-CPU.
  Die PWA schreibt jetzt `watch.json` mit genau dem Nötigen. **Die Geometrie nur für die zuletzt
  gespielten drei Plätze** — sie ist der Löwenanteil, und mehr braucht niemand auf der Bahn; wer
  einen vierten spielt, holt die große Datei wie bisher.
  **Geschrieben wird nur bei Änderung**, erkannt an einem Fingerabdruck über den Inhalt **ohne den
  Zeitstempel** — sonst wäre jeder Aufruf eine Änderung und die Datei bekäme im Minutentakt
  Commits. Angestoßen wird sie dort, wo sich ihr Inhalt ändern kann: Kartenbearbeitung, OSM-Import,
  Gameplan-Rechnung; dazu einmal nach dem Start, damit niemand etwas tun muss.
  Format identisch mit der großen Datei — die Uhr braucht keinen zweiten Leseweg, `parseData` liest
  ohnehin nur diese Felder. Fehlt die Datei oder kennt der Worker den Pfad nicht, lädt die Uhr die
  große; die Reihenfolge des Ausrollens ist also wieder frei.

- **v2.95.0 · 2026-08-14** — **Uhr und Handy laufen jetzt im Sekundentakt gleich · Score-Leiste
  verdeckt nichts mehr.**
  **(1) Der Lochwechsel ging über die große Datei.** `playLivePush()` rief `cloudSave()` — 3 MB
  lesen, mergen, 3 MB schreiben, für einen Zeiger von wenigen Byte. Genau das Ereignis, das drüben
  **sofort** ankommen soll, nahm den langsamsten Weg. Er läuft jetzt über `draftPush()` und wird
  **nicht entprellt**; der große Abgleich wird nur noch verzögert angestoßen, weil er nichts
  beiträgt, was die Uhr im selben Moment braucht.
  **(2) Der Abfrage-Takt stammte aus der 3-MB-Zeit.** 60 Sekunden waren richtig, solange jeder
  Abruf die ganze Datei zog; über `draft.json` sind es wenige Kilobyte. Jetzt **5 Sekunden, solange
  die App vorn liegt**, 30 Sekunden im Hintergrund — und beim Wechsel in den Vordergrund wird
  sofort einmal abgeglichen, statt bis zu 30 Sekunden zu warten. Maßstab ist die **Sichtbarkeit**:
  Wer hinschaut, erwartet Gleichlauf; wer das Handy in der Tasche hat, will kein Dauerfunken.
  **Auf der Uhr dieselbe Rechnung:** Push 30/180 s → 10/60 s, Pull 20/120 s → 5/30 s, und der
  Maßstab beim Pull ist der **Bildschirm** (`AmbientState.isAmbient`), nicht die letzte Eingabe —
  sonst bliebe die Uhr im Sparbetrieb, wenn der Lochwechsel am Handy passiert. Ein Lochwechsel
  zählt jetzt auch dort als Eingabe.
  Damit liegt beide Richtungen bei **höchstens fünf Sekunden** statt bis zu einer Minute.
  **(3) Die klebende Score-Leiste verdeckte die Eingabe.** Sie liegt beim Scrollen über dem Inhalt;
  ohne Fußraum verschwinden die letzten Felder und die Loch-Navigation dauerhaft dahinter —
  erreichbar nur ganz unten und selbst dort halb verdeckt. Der Blattinhalt bekommt jetzt 104 px
  plus Geräterand Fußraum, und zwar nur in der Eingabemaske: `openSheet` nimmt die Klasse bei jedem
  neuen Blatt zurück, sonst hätte das nächste Blatt unerklärliche Luft am Fuß.

- **v2.94.0 · 2026-08-14** — **Der Caddy rechnet ab der eigenen Position, nicht ab dem gespeicherten
  Abschlag.**
  Am Tee nahm die EV-Rechnung bisher `geo.holes[n].tee` als Startpunkt — **einen** Abschlag, meist
  den gelben. Wer von Weiß oder Blau spielt, steht 20–40 m dahinter, und genau diese Meter fehlten:
  Die Empfehlung galt für ein kürzeres Loch, die gezeichnete Linie und das Streuungsoval begannen am
  falschen Punkt. Bei 30 m Unterschied kippt die Schlägerwahl.
  `STRAT.tee` nimmt jetzt einen Startpunkt entgegen; beide Caddy-Wege — die kompakte Zeile und der
  aufgeklappte Caddy — geben die GPS-Position mit. Ohne Position gilt wie bisher der gespeicherte
  Abschlag, damit der Plan auch vom Schreibtisch aus rechenbar bleibt. Richtung und Lie-Raster
  bleiben am Loch verankert; **nur der Startpunkt wandert**.
  **Die Position geht auf ~10 m gerundet in den Zwischenspeicher-Schlüssel.** Ohne sie bliebe die
  erste Rechnung für immer stehen, obwohl man längst woanders steht; mit voller Genauigkeit wäre bei
  jedem GPS-Zucken eine neue Monte-Carlo-Rechnung fällig. Zehn Meter sind fein genug für die
  Schlägerwahl und grob genug, dass das Ergebnis ruhig bleibt.

- **v2.93.0 · 2026-08-14** — **„🔧 Abgleich prüfen" — Schluss mit Raten.**
  Anlass: „Die Synchronisation funktioniert überhaupt nicht mehr, und `draft.json` liegt nicht im
  Repo." Der Code war zu dem Zeitpunkt bereits korrigiert (v2.92) — die Frage war also nicht *was*
  kaputt ist, sondern **welche Fassung wo läuft**. Und genau das konnte niemand sehen: Der Abgleich
  hängt an vier Gliedern (Worker erreichbar · Worker-**Fassung** · kleine Datei lesbar · kleine
  Datei schreibbar), und fällt eines aus, sieht man an der Oberfläche immer dasselbe — nichts kommt
  an.
  Die Prüfung geht die Kette **einzeln** durch und benennt das fehlende Glied im Klartext, statt
  einen Statuscode zu zeigen: „Worker älter als v2.6 (er ignoriert `?path=` und schickt die große
  Datei)", „Schreiben auf draft.json abgelehnt — steht `draft.json` in `CFG.PATHS`?". Sie meldet
  außerdem die Fassung des Workers und der App — die beiden Angaben, ohne die jede Ferndiagnose
  Raten bleibt.
  **Sie schreibt nichts kaputt:** Der Schreibtest sendet genau das, was der nächste Takt ohnehin
  senden würde — den aktuellen Entwurf oder ein leeres Objekt. Nebenbei legt er `draft.json` an,
  falls sie noch fehlt.

- **v2.92.0 · 2026-08-14** — **Nachtrag: Der alte Worker wird jetzt am INHALT erkannt.**
  v2.91.1 hat den Takt schon so umgebaut, dass eine leere Antwort nicht mehr abkürzt. Die
  eigentliche Ursache lag aber eine Ebene tiefer und war damit nur halb entschärft:
  **Ein Worker VOR v2.6 kennt den `path`-Parameter nicht.** Er ignoriert ihn und liefert mit
  **Status 200 die GROSSE Datei**. Das sieht wie ein Erfolg aus — nur steht darin kein `round`, also
  galt „keine Runde läuft". Der Schreibversuch scheiterte am selben Worker mit 403, ohne Folge.
  Ergebnis: Der Abgleich stand still und `draft.json` wurde nie angelegt.
  **Ein Statuscode sagt nicht, WAS man bekommen hat.** Deshalb wird jetzt der Inhalt geprüft: Wer
  `testDefs`, `rounds` oder `_draftRound` mitbringt, ist die große Datei — dann ist der Worker alt,
  `DRAFT_MODE` wird für die Sitzung auf `false` gesetzt, und alles läuft wie vor v2.91. Ein 403 beim
  Schreiben sperrt den kleinen Weg ebenso, statt bei jedem Takt still zu scheitern.
  **Dieselbe Erkennung in der Uhr** (`Net.fetchDraftFile`): Dort war der Schaden größer, weil
  `fetchDraft()` bei „kein `round`" **null** zurückgab und gar nicht erst auf die große Datei
  zurückfiel — die Uhr übernahm also keine Handy-Eingaben mehr.
  **Lehre für den nächsten Umbau dieser Art:** Ein neuer Parameter, den eine alte Gegenstelle
  *ignoriert* statt abzulehnen, ist gefährlicher als einer, der einen Fehler wirft. Wer so etwas
  einführt, muss die Antwort auf Plausibilität prüfen — nicht auf den Statuscode.

- **v2.91.1 · 2026-08-14** — **BUGFIX: v2.91.0 hat den Abgleich zwischen Uhr und Handy lahmgelegt.**
  Mein Fehler, und ein grober: `draftPull()` liefert bei einer **fehlenden oder leeren**
  `draft.json` ein `{leer:true}` — und `playSyncTick` behandelte das als **erledigte Antwort** und
  brach den Takt ab. Da die Datei bis zum allerersten Push gar nicht existiert, hieß das: **gar
  kein Abgleich mehr**, in beide Richtungen. Auf der Uhr dasselbe Muster — `fetchDraft()` wertete
  die leere Datei als „keine Runde" und ging nicht mehr über die große Datei.
  Damit war die Lage selbsterhaltend: Ohne Abgleich schrieb niemand die Datei, und ohne Datei gab
  es keinen Abgleich. Deshalb stand auch nichts im Repo.
  **Die Regel lautet jetzt:** Nur ein Entwurf **in** der Datei beendet den Takt. Leer oder fehlend
  heißt „hier steht noch nichts" — dann geht es wie früher über die große Datei weiter, und die
  kleine wird nebenbei angelegt. Auf beiden Geräten gleich.
  **Nachgewiesen statt behauptet:** Ich habe den Takt mit einem gefälschten Netz durchgespielt.
  404 → fragt `draft.json`, fällt auf `?sha=1` und `?fresh=1` zurück. 403 (alter Worker) → ebenso.
  Gefüllte Datei → nur `draft.json`, keine große Anfrage. Genau so war es gedacht.
  **Lehre für die Doku:** Ein Rückfall, der nur im Code steht, ist keiner. Diese drei Fälle stehen
  jetzt als Prüfungen fest — vorher prüfte ich, dass die kleine Datei ZUERST gefragt wird, aber
  nicht, was bei leerer Antwort passiert.

- **v2.91.0 · 2026-08-14** — **Der Rundenentwurf läuft als eigene, kleine Datei.**
  **Das Problem war die Vermischung zweier Datensorten.** Die Trainingsdatenbank ist **kalt und
  groß** (~3 MB, ändert sich selten), der Rundenentwurf **heiß und winzig** (wenige kB, ändert sich
  alle paar Sekunden). Solange beide in derselben Datei liegen, muss jede heiße Änderung die ganze
  kalte Menge bewegen. Gemessen an der Uhr: 3 MB runter, mergen, 3 MB rauf — nach **jeder** Eingabe,
  dazu ein Pull-Takt mit weiteren 3 MB. Eine aktive Runde lag deutlich über einem halben Gigabyte.
  **`draft.json`** enthält nur `{round, ts, live}` — dieselbe Form wie `DB._draftRound`. Während
  der Runde lesen und schreiben beide Geräte nur diese Datei; die große bleibt unberührt, bis die
  Runde abgeschlossen wird. Aus 6 MB je Eingabe werden rund 10 kB. Nebenbei entfällt das
  serverseitige Parsen der 3 MB im ALT-Modus — ein latenter 502 im Free-Tier mit 10 ms CPU-Limit,
  bei dem ein Push still verlorenging.
  **Angelegt werden muss nichts:** `draft.json` ist eine gewöhnliche Repo-Datei, das erste PUT ohne
  SHA erzeugt sie. Im Worker sind es zwei Änderungen — Whitelist und ein `path`-Parameter bei GET;
  die Schreibsperre über `X-Base-Sha` gilt damit automatisch auch für sie.
  **Nicht Cloudflare KV:** KV ist nur eventuell konsistent, weltweit bis zu einer Minute. Bei zwei
  Geräten, die sich auf derselben Bahn abwechselnd eintragen, wäre das genau die falsche
  Eigenschaft. Das Repo mit SHA-Sperre ist hier verlässlicher.
  **`mergeDraft(a,b,tombTs)` herausgelöst:** Die Vereinigungsregel stand inline in `mergeDB`. Jetzt
  gibt es zwei Aufrufer — zwei Kopien wären zwei Wahrheiten darüber, wessen Eingabe gewinnt. Bei
  **409** (jemand war schneller) wird deshalb **vereint statt überschrieben**; der andere steht
  gerade auf der Bahn.
  **Rückfall in beide Richtungen:** Ein Worker ohne `draft.json` antwortet 403, ein leeres Repo 404
  — beides führt zurück auf den alten Weg über die große Datei. Worker, App und Uhr werden getrennt
  ausgerollt, also muss jede Reihenfolge funktionieren.
  **Und das Aufräumen:** `draftFinalize()` leert jetzt auch die kleine Datei. Ohne das zöge das
  andere Gerät die abgeschlossene Runde beim nächsten Takt wieder herein — der Grabstein in
  `ui.draftDiscardedTs` schützt nur den Weg über die große Datei.
  **Prüfstand:** 24 neue Prüfungen, darunter alle Regeln von `mergeDraft` (feldweise Vereinigung,
  `null` löscht nie, eigener Weg für den live-Zeiger, Verworfen-Marke) und beide Rückfälle.

- **v2.90.0 · 2026-08-14** — **BUGFIX: Die Schlagmessungen der Uhr gingen beim Abgleich verloren.**
  Beim Durchsehen von `MainActivity.kt` bestätigt — die Uhr-Doku führt es unter Punkt 9 selbst als
  offenen Punkt („einmalig nachziehen!"), und es war noch offen.
  **Der Ablauf des Verlusts:** Die Uhr misst jeden Schlag und legt ihn in `gpsShots` — das ist die
  Grundlage der gelernten Schlägerlängen und der Streuung. Sie pusht additiv, im Repo stehen die
  Messungen also. `mergeDB` hatte für diese Liste aber **keine Regel**: Sie fiel unter
  `Object.assign({},R,L)`, das lokale Array gewann **vollständig**. Das Handy lädt, die Messungen
  der Uhr sind lokal weg — und beim nächsten Push des Handys auch im Repo. Endgültig, ohne Meldung.
  Betroffen war ausgerechnet das, wofür die Uhr auf der Bahn dabei ist.
  Jetzt Vereinigung über die Schlag-ID, **mit Grabsteinen**: Seit die Liste vereinigt wird, käme ein
  gelöschter Fehltipp beim nächsten Abgleich zurück — `gpsDelShot` setzt deshalb einen Grabstein,
  und das Rückgängig-Band hebt ihn wieder auf. Im Worker gespiegelt (**v2.5**), denn die Uhr pusht
  über den ALT-Modus und lässt damit serverseitig mergen.

- **v2.89.0 · 2026-08-14** — **Der Spielbetrieb kostet jetzt einen Bruchteil: Vektorschicht geteilt,
  Sync fragt erst nach.**
  **(1) Jeder GPS-Tick baute die GESAMTE Vektorschicht neu.** Gemessen an einer Bahn mit 300
  erkannten Bäumen und 40 Waldringen: **79 kB SVG und 5,1 ms** Zeichenkettenbau — pro Sekunde, vier
  Stunden lang, dazu das Einhängen von mehreren hundert Knoten in den Baum. Zwischen zwei Ticks
  ändern sich aber nur **eigene Position, Distanzringe, Messlinie und Streuungsoval**. Die Bahn
  liegt still.
  `courseSVG` trennt das jetzt: alles Ortsabhängige sammelt `dyn`, der Rest `body`; `dynOnly`
  überspringt den statischen Teil ganz (die Projektion wird trotzdem gerechnet, die beweglichen
  Teile brauchen sie). Die Karte hat dafür zwei Gruppen, `playVecG` und `playDynG`; `playMapTick`
  schreibt nur noch die zweite, solange `playVecKey` gleich bleibt — Loch, Platz und die Schalter
  für Platzdaten/Vegetation/Luftbild. **Gemessen nach der Änderung: 2,8 kB und 0,88 ms** statt
  79 kB und 5,1 ms; auf dem Telefon ist der Unterschied größer, weil dort das Einhängen dominiert.
  Die Reihenfolge bleibt: `bodyNoSat = bodyStatic + bodyDyn`, das Bewegliche liegt weiter oben.
  **(2) Der Runden-Sync zog jede Minute die komplette Datei.** Bei ~3 MB sind das auf vier Stunden
  rund **700 MB** Mobilfunk und 240-mal Funkmodem auf Vollast. Dabei stand die Antwort auf die
  einzige Frage, die zählt, längst im Kopf der Antwort (`X-Repo-Sha`) — nur eben **nach** den 3 MB.
  Der Worker (**v2.4**) liefert die Kennung jetzt auch allein über `?sha=1`; `ghSha` liest dafür nur
  das Wurzel-Listing und fasst den Dateiinhalt nicht an. Ist sie unverändert, entfällt der Abruf.
  Das gilt auch für den **Uhr-Wächter**, der im Alltag noch häufiger läuft — immer wenn die App
  offen ist und keine Runde läuft.
  **Rückfall eingebaut:** Ein alter Worker kennt `?sha=1` nicht und antwortet ohne `sha`; dann lädt
  die App wie bisher voll. Worker und App werden getrennt ausgerollt, also muss beides einzeln
  funktionieren.
  **(3) Der Minutenvergleich lief über zwei vollständige Serialisierungen** der 3-MB-Datenbank, auf
  dem Hauptthread, während man spielt — nur um „hat sich etwas geändert?" zu beantworten. Auf der
  Runde ändert sich genau eines: der Rundenentwurf. `syncFinger` fasst ihn und die wenigen anderen
  relevanten Marken zusammen. Die übrigen Stellen behalten den vollen Vergleich — sie laufen nach
  der SHA-Prüfung nur noch, wenn wirklich etwas neu ist.

- **v2.88.0 · 2026-08-13** — **Zwei neue Luftbildquellen · PNG für die Erkennung · Quellen-Prüfung.**
  **(1) Bremen DOP10 (10 cm)** und **MV DOP20**, beide CC BY 4.0. Bremen trägt das Bildflugjahr im
  Layer-Namen (`dop10_2025_HB`) — wenn dort irgendwann „XML statt Bild" gemeldet wird, ist genau das
  der Grund. Bremerhaven liegt in einem eigenen Layer und ist nicht drin; dort greift wieder Esri.
  **MV hat auch DOP10 — bewusst nicht eingebaut:** Die Nutzungsbedingungen dieses Dienstes
  beschränken die externe Einbindung ausdrücklich und verlangen eine Beantragung. Eine Quelle, die
  man rechtlich nicht einbinden darf, gehört nicht in eine Auswahlliste, auch wenn sie technisch
  funktioniert.
  **(2) PNG für die Erkennung.** Die Anzeige holt weiter JPEG — kleiner, schneller, fürs Auge genug.
  Die Vegetationserkennung bewertet aber **einzelne Pixel** auf „gleichzeitig grün und dunkel", und
  genau daran vergreift sich JPEG: Chroma-Subsampling legt Farbe in 8×8-Blöcken zusammen und färbt
  die Kanten zwischen Krone und Fairway um. An einer Waldkante entstehen Pixel, die es nie gab.
  Beide Fassungen liegen unter **getrennten Speicherschlüsseln** — sonst würde je nachdem, wer
  zuerst da war, entweder die Anzeige langsamer oder die Auswertung falsch. Nur bei WMS; bei Esri
  ist das Format nicht wählbar.
  **(3) „🔍 Quelle prüfen"** in den Karten-Einstellungen. Grund, offen gesagt: Ich konnte die neuen
  Dienste von hier aus **nicht anfragen** — die Hosts sind in meiner Umgebung gesperrt. Adresse,
  Layer-Name und Gebiet stammen aus den amtlichen Metadaten, Bremen zusätzlich aus dem
  Capabilities-Auszug; getestet ist keiner davon. Statt das zu behaupten, holt der Knopf **eine
  echte Kachel** für die Platzmitte und sagt, was zurückkam: Bild mit Größe, Text/XML (dann stimmt
  der Layer-Name oder das Gebiet nicht — WMS antwortet auf falsche Layer mit XML statt mit einem
  Fehlercode) oder keine Verbindung. Fünf Sekunden statt Raten.

- **v2.87.0 · 2026-08-13** — **Sicherungskopien bezahlbar gemacht · ein Versionssprung verwirft
  nichts mehr · leere Stände überschreiben keine Skalare.**
  **(1) Die Sicherungen waren der teuerste Vorgang der App.** Der Ring hielt starr **zehn**
  vollständige Kopien: bei ~3 MB Daten rund 30 MB im Speicher, und bei jedem Schnappschuss wurde
  das **ganze Array** neu nach IndexedDB geschrieben — Schreibverstärkung Faktor zehn, alle drei
  Minuten. Dazu eine zweite Kopie nach `localStorage`: ein 30-MB-String, gebaut auf dem
  Haupt-Thread, für einen Speicher mit 5 MB Grenze. Der Versuch **musste** scheitern, still im
  `catch` — reine Rechenzeit für nichts, und `idbHydrate` räumt den Schlüssel ohnehin weg, worauf
  `snapshot` ihn sofort wieder hinschrieb.
  Neu richtet sich die Tiefe nach der **Größe** (`snapBehalten`: 10 · 6 · 3 · 2), der
  localStorage-Zweig ist ersatzlos entfallen. Und ein **fast leerer Stand kommt nicht mehr in den
  Ring**: Beim Start läuft `snapshot("Start")`, bevor `idbHydrate` die echten Daten geladen hat —
  ging dabei etwas schief, verdrängte der leere Stand die letzte gute Sicherung. Die Sicherung
  hätte also genau dann versagt, wenn man sie braucht.
  **(2) Ein Versionssprung verwarf den gespeicherten Stand** — und im Zusammenspiel mit dem
  Abgleich war das eine Kette: `load()` wirft die localStorage-Daten weg → `idbHydrate` lehnt die
  IndexedDB-Kopie **mit derselben Bedingung** ab und **überschreibt sie mit dem leeren SEED** →
  `cloudLoad` merged das Repo hinein, wobei `Object.assign({}, R, L)` Profil, Phasen und `ui` aus
  dem leeren L nimmt → beim nächsten Push landen die Leerwerte auf **allen** Geräten. Die Doku
  warnte davor, `SEED.version` zu erhöhen; eine Warnung ist aber keine Sicherung.
  Jetzt wird **migriert statt verworfen**: Der Stand wird übernommen und nur die Versionsnummer
  angehoben, `ensureDefaults()` legt Neues additiv an — dafür ist es gebaut. Verworfen wird nur,
  was strukturell unbrauchbar ist. `idbHydrate` prüft die Version nicht mehr und **überschreibt nie
  eine reichere Kopie**.
  **(3) Wer leer ist, gewinnt nichts.** In `mergeDB` waren die Listen geschützt (Union nach ID),
  die **Skalare nicht**: Ein frisch installiertes Gerät überschrieb Profil und Einstellungen im
  Repo. Ist der lokale Stand leer (`dataScore<5`, dieselbe Schwelle wie der Empty-Guard des
  Pushes) und der Repo-Stand nicht, drehen sich die Grundlagen um. Im Normalfall bleibt alles wie
  bisher — die lokale Korrektur gewinnt weiterhin.
  **Im Worker gespiegelt** (Abschnitt 28, Fassung **v2.3**), inklusive eines eigenen `dataScore`.
  **Und ein Warnhinweis, der aus einem eigenen Fehler stammt:** `mergeDB` steht seit v2.85 ZWEIMAL
  in der Datei — ausführbar und als Worker-Abzug. Eine Suche findet den **Abzug zuerst**, weil die
  Doku im Dokument vor dem Code steht. Genau dort landete diese Änderung zunächst und blieb
  wirkungslos, bis der Prüfstand es meldete. Der Hinweis steht jetzt über Abschnitt 28.
  **Prüfstand:** 26 neue Prüfungen, darunter die Ringtiefe je Größe, die Migration statt Verwerfen,
  „nie eine reichere Kopie überschreiben" und beide Merge-Richtungen mit echtem `mergeDB`.

- **v2.86.0 · 2026-08-13** — **✋ Hand-Werkzeug: die Karte schieben, ohne etwas anzufassen.**
  Bisher war Verschieben immer ein Nebenweg: Ziehen auf **freier** Fläche schob die Karte — aber im
  Zeichen-Werkzeug verbraucht die Maus den Zug für die Kontur, und über einem Objekt kann man es
  aufnehmen. Nach einer Erkennung liegen kaum noch freie Stellen. Das neue Werkzeug macht das
  Schieben zum Hauptweg: **Ziehen verschiebt die Karte, sonst passiert nichts** — kein Aufnehmen,
  kein Freihandzug, kein Setzen, kein Löschen.
  Geprüft wird es **vor** der Freihand- und vor der Objektbehandlung, sonst griffe die zuerst; der
  Klick ist ebenfalls stillgelegt. Zeiger als offene Hand, beim Ziehen geschlossen. Kürzel
  <kbd>H</kbd>.
  Mit Auswahl und Hand sind es acht Werkzeuge — das Raster geht deshalb auf **vier Spalten**; sechs
  nebeneinander wären auf dem Telefon 44 px breite Streifen.

- **v2.85.0 · 2026-08-13** — **`worker.js` liegt jetzt vollständig in der Doku (Abschnitt 28).**
  Der Worker ist der zweite Teil dieses Systems und lag ausschließlich im Cloudflare-Dashboard —
  bei jeder Merge-Frage also unsichtbar. Genau daran ist v2.84 zunächst gescheitert: Ich habe
  **behauptet**, der Worker merge serverseitig und müsse angepasst werden, ohne seinen Stand zu
  kennen. Tatsächlich merged er im benutzten Modus gar nicht — er ist ein SHA-Türsteher, der die
  gesendeten Bytes schreibt. **Eine Aussage über Code, den man nicht sieht, ist eine Vermutung.**
  Der vollständige Quelltext (Fassung v2.2, mit dem gespiegelten `_mergeCourses`) steht jetzt in
  **Abschnitt 28**, samt Kurzbeschreibung beider Modi und der Fassungsgeschichte. Verwiesen wird
  darauf an den drei Stellen, an denen man darüber stolpern muss: in der Sync-Architektur, in der
  STRAT-Merge-Invariante und in der **Doku-Pflicht (Regel 0)**.
  **Die Regel dazu:** Wer an `mergeDB`, den Sync-Pfaden oder synchronisierten Feldern arbeitet,
  liest den Abschnitt **zuerst**, prüft ob der ALT-Modus betroffen ist, ändert den Code dort bei
  Bedarf mit, erhöht die Fassungsnummer und ersetzt den Stand — der Abschnitt ist die Quelle der
  Wahrheit für „was macht der Worker gerade?".
  **Technisch:** Der Code steht in einem Zaun aus **vier** Backticks, weil er Template-Literale
  enthält — mit dreien risse der Block auseinander. Der Prüfstand hält beides fest: dass die
  tragenden Codestellen wirklich dastehen (nicht nur eine Beschreibung) und dass nichts im
  eingebetteten Code den Doku-Block sprengen kann.

- **v2.84.1 · 2026-08-13** — **Nachtrag zu v2.84: Der Merge veränderte seine Eingaben.**
  Beim Ende-zu-Ende-Test der Löschkette gefunden, bevor der Worker angepasst wurde. `_mergeArr`
  liefert die **Originalobjekte** zurück, keine Kopien — mein `delete c.geo` traf damit den lokalen
  `DB`-Eintrag beziehungsweise die frisch geladenen Repo-Daten. Gefährlich wird das, weil zwei
  Aufrufer das Ergebnis erst **bewerten und dann verwerfen** (`cloudLoadManual` rechnet
  `dataScore(merged)` und entscheidet danach): Die Karte wäre gelöscht gewesen, ohne dass jemand
  sie gelöscht hat — und ohne Spur. `_mergeCourses` arbeitet jetzt auf einer flachen Kopie.
  Zwei Prüfungen sichern es ab, je eine pro Seite.
  **Gemessene Wirkung der Kette** (simuliert über den echten `mergeDB`): Nach dem Löschen bleibt die
  Karte auf diesem Gerät in jedem Fall weg. Mit **altem** Worker kommt sie aber ins Repo zurück und
  damit auf **andere Geräte**; mit gespiegeltem Worker bleibt sie überall gelöscht. Ein Neu-Import
  nach dem Löschen überlebt beide Wege.

- **v2.84.0 · 2026-08-13** — **BUGFIX: Der endgültig gelöschte Platz kam nach dem Abgleich zurück.**
  Beobachtet und bestätigt — es ist ein Sync-Problem, und zwar dieselbe Fehlerklasse wie in v1.74:
  **ein Merge kann Löschungen nicht ausdrücken.** Diesmal mit einem Verstärker, der es unvermeidlich
  machte: Ein Platz trägt **keinen Zeitstempel** (`_mergeTs` liefert ""), also fällt `_mergeArr` auf
  „der **vollständigere** Eintrag gewinnt" zurück. Der Platz im Repo hat noch `geo` mit hunderten
  Flächen, der lokale hat keins mehr — die Repo-Fassung ist um Größenordnungen länger und gewinnt
  **immer**. Die Heuristik, die sonst Datenverlust verhindert, holte hier zuverlässig die gelöschte
  Karte zurück.
  **Lösung: die Löschung wird ein Datum statt eines Fehlens.** Der Platz trägt jetzt `geoAt` (Karte
  zuletzt geändert oder importiert) und `geoDeletedAt` (gelöscht). `_mergeCourses` führt beide
  Seiten zusammen und behält die Karte nur, wenn sie **jünger** ist als ihre Löschung. Damit
  funktionieren alle vier Richtungen: hier gelöscht, dort gelöscht, nach dem Löschen neu importiert,
  und Altbestand ohne Stempel (gilt als älter als jede Löschung).
  Gestempelt wird an drei Stellen: beim Löschen, beim Import/Wiederherstellen und bei **jeder**
  Änderung im Karteneditor.
  **Zum Worker (an `worker.js` v2.1 geprüft):** Im NEU-Modus — dem, den diese App benutzt — ist der
  Worker nur ein **SHA-Türsteher**: Er schreibt die gesendeten Bytes und merged **nicht**. Für diese
  App genügt die Änderung hier. Der server-seitige `mergeDB`-Port läuft nur im **ALT-Modus** (Clients,
  die `{data,force}` ohne `X-Path` senden, etwa eine ältere Uhr-Fassung); dort muss `_mergeCourses`
  gespiegelt werden. Äquivalenz beider Fassungen mit sechs Fällen geprüft.
  **Prüfstand:** 15 Prüfungen, darunter alle vier Richtungen und die Gegenprobe, dass der Normalfall
  ohne Löschdatum sich nicht ändert.

- **v2.83.0 · 2026-08-13** — **Grün und Teebox in EINEM Schritt — und ein Werkzeug für die Teebox.**
  Die Frage war berechtigt: Ein neues Grün anzulegen hieß **zwei** getrennte Handlungen — Fläche
  zeichnen, dann mit dem Punkt-Werkzeug die Mitte antippen und die Loch-Nummer eingeben. Das war
  nicht nur umständlich, sondern auch **ungenauer**: Der Schwerpunkt der gezeichneten Fläche trifft
  die Mitte besser als ein zweiter Fingertipp.
  Jetzt fragt der Abschluss einer **⛳ Grün**- oder **🟨 Teebox**-Fläche einmal nach dem Loch und
  setzt den Punkt selbst — Grünmitte bzw. Abschlag, beides in `overrides` und damit
  neu-import-fest. Leer lassen geht: Ein Übungsgrün gehört zu keiner Bahn.
  **Teebox war bisher gar nicht anlegbar** — weder als Fläche noch als Punkt; `applyGeoOverrides`
  kannte nur `green` und `swap`. Beides ergänzt, samt Darstellung und Zeichenreihenfolge.
  Die Punkt-Werkzeuge heißen jetzt „⛳ Grünmitte" und sind als das benannt, was sie sind: zum
  **Korrigieren** eines einzelnen Punktes, nicht zum Anlegen. Der Ablauf steht in der Klappe
  „Bedienung & Bestand" — er ist nicht zu erraten, und das war der eigentliche Fehler.
  **Bestehende Overrides bleiben erhalten:** Grün und Tee liegen im selben Objekt je Loch, ein
  blindes Überschreiben hätte das jeweils andere gelöscht.

- **v2.82.0 · 2026-08-13** — **„✔ Endgültig löschen" neben „↶ Löschen rückgängig".**
  Nach einem Löschvorgang stand nur die Rücknahme da. Die Sicherungskopie verfällt zwar beim
  Schließen der App von selbst — aber „verfällt irgendwann" ist kein Zustand, den man **sieht**.
  Wer bewusst löscht, will es abschließen können; wer unsicher ist, erkennt am stehengebliebenen
  Streifen, dass die Entscheidung noch offen ist.
  Der neue Knopf verwirft die Kopie nach **eigener** Rückfrage, die den Platz und die Zahl der
  Objekte nennt und sagt, was danach bleibt („nur noch ein neuer Import"). Er wirkt ausschließlich
  auf die Kopie **dieses** Platzes.

- **v2.81.0 · 2026-08-13** — **Kartenschirm: richtiger Ausschnitt, aufgeräumt, EIN Löschweg.**
  **(1) Zoom.** `renderCourseMap` fehlte `corridor:46` — gefittet wurde über das 55-m-Vorgabeband,
  die Bahn lag also kleiner im Bild als beim Spielen. Ohne gewähltes Loch fehlte zusätzlich
  `fitHoles`, der Ausschnitt umfasste damit den gesamten Datenbestand statt der Bahnen. Jetzt
  dasselbe Regelwerk wie im Spielmodus.
  **(2) Fünf Bedienelemente entfernt** — Distanz-Ringe, Live-Position anzeigen, Distanz zur
  Grünmitte, eigener GPS-Punkt und „Caddy ab Position", samt `mapToggleLive`, `mapLocate` und
  `mapAddPoint`. Diese Ansicht ist die **Kartenverwaltung**: Luftbild, Offline-Vorrat, Bearbeiten,
  Import, Löschen. Alles Ortsbezogene war eine zweite, schlechtere Ausgabe dessen, was der
  Spielmodus mit GPS, Caddy und Ringen ohnehin kann — und niemand steht auf der Bahn und öffnet
  dafür die Platzverwaltung.
  **(3) Der Löschknopf löschte nicht, was er versprach.** Hier stand ein **eigener** Weg: er
  entfernte `geo` und fragte „OSM-Geodaten dieses Platzes löschen?" — ohne Zahlen, ohne die
  Gameplans, ohne die Möglichkeit, es zurückzunehmen. Der in v2.78 gebaute vollständige Löschweg
  lag ausschließlich im Editor. Jetzt gibt es **einen** (`geoWipe(idx, ausEditor)`), den beide
  aufrufen: Rückfrage mit Zahlen, Gameplans dieses Platzes mit weg, vollständige Kopie für
  „↶ Löschen rückgängig". Zwei Löschwege hießen zwei Rückfragen, zwei Umfänge und zwei Wahrheiten.

- **v2.80.0 · 2026-08-13** — **Grün auch als Fläche · Freihand-Zeichnen am Rechner.**
  **(1) Grün gab es nur als PUNKT.** Der setzt die Grün-**Mitte** — das Ziel des Caddys — und das
  ist etwas anderes als die Grün-**Fläche**, die das Lie-Raster auswertet und aus der Front/Mitte/
  Back entstehen. Fehlte die Fläche in den OSM-Daten, konnte man sie gar nicht nachtragen. Jetzt
  sind **⛳ Grün** und **🟩 Fairway** eigene Flächenarten, und die Hinweise sagen den Unterschied.
  Dazu ein Fehler, der ohne die Erweiterung nie aufgefallen wäre: `greenRingFor` suchte
  ausschließlich in `geo.features`. Ein selbst gezeichnetes Grün wäre also **nie gefunden** worden —
  keine F/M/B-Werte, obwohl die Fläche dasteht. Sucht jetzt auch in `geo.mine`.
  **(2) Freihand statt Ecke für Ecke.** Ecken tippen ist eine Fingergeste: präzise, aber langsam,
  und für eine Waldkante mit dreißig Knicken unbrauchbar. Mit der Maus zieht man die Außenkontur in
  einem Zug. **Nur mit der Maus und nur im PC-Modus** — am Finger wäre jeder Wisch über die Karte
  ein Zeichenzug, und das Verschieben ginge verloren.
  Während des Ziehens wird **nur ein SVG-Pfad** aktualisiert, kein Neuaufbau der Karte, sonst
  ruckelt es. Punkte werden erst ab 3 Bildpunkten Abstand gesammelt — in viewBox-Einheiten
  umgerechnet, sonst sammelt starker Zoom tausend Punkte auf einem Meter. Beim Loslassen glättet
  Douglas–Peucker auf **~2 m** (Toleranz in Metern gedacht, nicht in Bildpunkten — sonst hinge die
  Glättung am Zoom), Notbremse bei 120 Stützpunkten. Ein roher Zug hat je nach Zoom 200–600 Punkte,
  und das Lie-Raster prüft **jeden** davon je Zelle; die Genauigkeit einer per Hand gezogenen Kante
  liegt ohnehin bei ein paar Metern.
  Das Ergebnis landet im gewohnten Zeichen-Entwurf — die Art wählt man danach wie bisher in der
  Leiste, und ein kurzer Zug (unter vier Punkten) gilt weiterhin als Klick, setzt also eine
  einzelne Ecke.

- **v2.79.0 · 2026-08-13** — **PC-Modus: Die Abschluss-Leiste rutscht zu den Werkzeugen.**
  Beim Zeichnen einer Fläche zählt eine Leiste die Ecken und bietet die Art an (Wasser · Penalty ·
  Bunker · Hohes Rough · Wald). Sie stand unter der Karte — am Telefon richtig, am Rechner falsch:
  Dort ist die Karte hoch, die Leiste liegt weit unterhalb des Blicks, und zum Abschließen einer
  Fläche musste man scrollen, obwohl die Werkzeuge rechts direkt danebenstehen.
  Im PC-Modus steht sie jetzt **in der rechten Spalte unter den Werkzeugen** — dort, wo der Blick
  beim Zeichnen ohnehin hin und her geht. Am Telefon bleibt alles wie bisher. Gerendert wird sie
  **genau einmal**, je nach Modus an einer der beiden Stellen; zwei Kopien wären zwei Zustände,
  von denen einer irgendwann veraltet.
  In der 380-px-Spalte dürfen die Art-Knöpfe umbrechen und die Breite nutzen — nebeneinander wären
  sie unleserlich schmal.

- **v2.78.0 · 2026-08-13** — **„Gesamten Kartenbestand löschen" — mit Zahlen in der Rückfrage.**
  Trifft alles: importierte OSM-Flächen (`features`), selbst Gezeichnetes und Erkanntes (`mine`),
  korrigierte Grünmitten (`overrides`) und die Bahn-Geometrie (`holes`). Danach ist der Platz wie
  vor dem ersten Import — **Runden, Scorekarte und Schläger bleiben unberührt**, nur die Karte ist
  weg. Wofür: ein Import mit dem falschen Platz, ein misslungener Erkennungslauf über alle Bahnen,
  eine Karte, die man von Grund auf neu aufbauen will. Bisher blieb nur Objekt für Objekt löschen —
  nach einem Platzlauf hunderte.
  **Die Rückfrage nennt Zahlen** („412 importierte Flächen, 137 eigene und erkannte Objekte,
  18 Bahn-Geometrien") und sagt ausdrücklich dazu, was NICHT betroffen ist. „Alles löschen?"
  beantwortet man nach zwei Stunden Zeichnen falsch, eine Aufzählung nicht.
  **Das Rückgängig des Editors reicht hier nicht** — es sichert `mine` und `overrides`, nicht
  `features` und `holes`. Deshalb legt der Löschvorgang eine **vollständige Kopie** in
  `GEOED.geoBackup` (nur Sitzung, bewusst nicht gespeichert) und bietet sie im Import-Schirm als
  „↶ Löschen rückgängig" an. Nach dem Schließen der App ist sie weg, und die Rückfrage sagt das.
  **Die Gameplans dieses Platzes gehen mit** — ein gespeicherter Plan ohne Geometrie wäre ein
  Rechenergebnis ohne Grundlage. Pläne anderer Plätze bleiben.
  Der Knopf sitzt unten in der Klappe „Bedienung & Bestand" und in Rot: Es ist der einzige im
  Editor, der alles trifft.

- **v2.77.0 · 2026-08-13** — **Sieben Erweiterungen für das Nachbessern am Rechner.**
  Alles zielt auf denselben Arbeitsgang: das Ergebnis einer Erkennung korrigieren. Bis hierher ging
  das nur Objekt für Objekt, und Flächen nur als Ganzes.
  **(1) Rechteck-Auswahl und Mehrfachlöschen.** Neues Werkzeug „▣ Auswahl": Rahmen aufziehen wählt
  alles darin, Klick schaltet einzeln um, **Entf** löscht. Nach einem Platzlauf waren das vorher 60
  Einzelklicks. Gewählt wird über den **Schwerpunkt** — ein Ring, dessen Mitte im Rechteck liegt,
  ist gemeint; einer, von dem nur eine Ecke hineinragt, nicht.
  **(2) Eckpunkte bearbeiten.** Bei genau **einer** ausgewählten Fläche/Linie erscheinen Griffe:
  ziehen verschiebt die Ecke, Alt-Klick löscht sie, der kleine Punkt auf der Kante fügt eine ein.
  Nur bei einer Auswahl, weil 60-Punkt-Ringe sonst unlesbar würden. Beim ersten Punkt wandert der
  Schlusspunkt mit, sonst reißt der Ring auf; unter 3 Ecken (Fläche) bzw. 2 Punkten (Linie) wird
  abgelehnt statt das Objekt zu zerstören.
  **(3) Erkennung im sichtbaren Ausschnitt.** „🔍 Nur im sichtbaren Ausschnitt suchen" — für die
  eine Ecke, die der Durchlauf verpasst hat, mit der Auflösung des aktuellen Zooms. Die Box kommt
  aus **vier** Ecken des Ausschnitts: Bei gedrehter Karte ist ein Rechteck im Bild keines auf der
  Erde. Über 1200 m Breite wird abgelehnt und auf den Bahnlauf verwiesen.
  **(4) Rechtsklick-Menü** an der Maus (am Finger weiterhin nur unterdrückt, dort kommt es vom
  Langdruck): Löschen, Auswählen, und **Art ändern** — Wald → Gebüsch → Hohes Rough → Wasser →
  Bunker. Bis hierher musste man eine Fläche löschen und neu zeichnen, obwohl die Kontur stimmte.
  **(5) Wiederherstellen** (⇧Z, Strg+Y). Ein neuer Eingriff verwirft den Stapel — sonst könnte man
  in einen Stand zurückspringen, den es nie gab.
  **(6) Zeigerform je Werkzeug:** Fadenkreuz beim Zeichnen, Greifhand über ziehbaren Objekten,
  Verbotszeichen beim Löschen. Am Telefon egal, am Rechner die halbe Rückmeldung darüber, was ein
  Klick gleich tut.
  **(7) Maßstab und Länge:** Balken unten links, auf 5/10/25/50/100 m gerundet, und während des
  Zeichnens die Länge der letzten Kante plus Gesamtlänge. Ohne sie schätzt man Größen aus dem
  Luftbild und liegt bei einer Waldkante regelmäßig 20 m daneben.
  **Heikel und deshalb festgeschrieben:** `mine` ist ein Array — jedes Löschen verschiebt die
  Nachfolger. Die Auswahl wird bei **jeder** Änderung geleert, und Mehrfachlöschen läuft
  absteigend. Lieber einmal neu auswählen als das falsche Objekt anfassen.

- **v2.76.0 · 2026-08-13** — **PC-Modus im Karteneditor.**
  Der Editor ist für den Daumen gebaut: 640 px Blattbreite, alles untereinander, Werkzeuge unten
  klebend, und ein Ziehen, das erst nach 320 ms Halten beginnt. Am Schreibtisch ist das die falsche
  Form — dort ist Platz nebeneinander, und eine Maus wackelt nicht.
  **Form:** Blatt bis 1500 px, **Karte links, Bedienung rechts**, die Karte bleibt beim Scrollen
  stehen. Werkzeuge in drei Spalten à 60 px statt sechs schmalen. Unter 1080 px Fensterbreite fällt
  alles auf eine Spalte zurück — sonst wäre die Karte am kleinen Fenster 380 px schmal.
  **Maus:** Ziehen **ohne Halten**. Das entscheidet `e.pointerType`, ausdrücklich **nicht** der
  Modus: Wer ein Touchgerät am großen Bildschirm benutzt, behält den Schutz vor versehentlichem
  Verschieben. Mausrad zoomt **auf den Zeiger**, nicht auf die Bildmitte — sonst läuft einem die
  Stelle, die man vergrößern will, aus dem Bild.
  **Tastatur:** <kbd>P</kbd> Punkt · <kbd>G</kbd> Grün · <kbd>B</kbd> Baum · <kbd>F</kbd> Fläche ·
  <kbd>L</kbd> Linie · <kbd>X</kbd> Löschen · <kbd>Z</kbd> rückgängig · <kbd>Esc</kbd> Zeichnen
  abbrechen · <kbd>+</kbd>/<kbd>−</kbd> Zoom · <kbd>0</kbd> ganze Bahn. Die Kürzel stehen in der
  Oberfläche, nicht nur hier. Der Horcher hängt **einmal** und meldet sich selbst ab, sobald der
  Editor zu ist — ein Kürzel, das in einer anderen Ansicht noch feuert, wäre ein Fehler mit Ansage.
  Eingabefelder sind ausgenommen.
  **Vorbelegung nach Gerät** (feiner Zeiger UND Fenster ≥1080 px), umschaltbar über 🖥/📱 neben der
  Loch-Auswahl. Die Wahl bleibt **lokal** in `DB.ui.geoPc`: Dasselbe Konto am Telefon und am
  Rechner soll nicht dieselbe Form erzwingen.

- **v2.75.0 · 2026-08-13** — **„alle" zeigt jetzt alle Bahnen — und erkennt Wald über den ganzen
  Platz.**
  **(1) Der Ausschnitt.** Ohne gewähltes Loch fittete die Karte auf den **gesamten Datenbestand**.
  Nach einem OSM-Import gehören dazu Clubhaus, Parkplatz, Zufahrt und angrenzende Äcker — der Platz
  lag als kleines Rechteck in der Mitte. `opt.fitHoles` nimmt stattdessen nur **Abschläge,
  Spiellinien und Grüns aller Löcher**; dieselbe Idee wie `tight`, eine Ebene höher. Alles andere
  wird weiterhin gezeichnet, es bestimmt nur den Ausschnitt nicht mehr — wer hier die Features
  filtern würde, nähme dem Editor die Objekte zum Bearbeiten.
  **(2) Erkennung über den ganzen Platz.** Neuer Knopf in der Übersicht: „🌲 Auf allen Bahnen
  suchen". Er rechnet **Bahn für Bahn**, nicht einmal über den ganzen Platz — und das ist der
  entscheidende Punkt: Die Auflösung hängt am Ausschnitt. Über 18 Bahnen gerechnet liegt sie bei
  2–3 m/px, ein Einzelbaum wäre dort zwei Bildpunkte groß und fiele durch jedes Raster. Je Bahn
  sind es 0,3–0,6 m/px. Der Lauf dauert länger und findet dafür das, wofür man ihn startet.
  **Eine Stapel-Nummer für den ganzen Lauf**, damit „↶ Letzte Erkennung zurücknehmen" den
  kompletten Durchlauf rückgängig macht und nicht nur die letzte Bahn — wer 18 Bahnen auf einmal
  erkennen lässt, will das Ergebnis auch auf einmal wieder loswerden können. Und **ein**
  Schnappschuss zu Beginn statt einer je Bahn: sonst wäre der Rückgängig-Verlauf (Tiefe 12) nach
  einem Lauf komplett mit Erkennungen gefüllt und alles davor herausgefallen.
  Rückfrage mit Zeitschätzung vorweg, Fortschritt am Knopf („Bahn 7 … (7/18)"), und zwischen den
  Bahnen wird die Schleife freigegeben, damit das Telefon nicht „App reagiert nicht" meldet. Eine
  Bahn ohne Luftbild bricht den Lauf nicht ab, sie wird am Ende gezählt.

- **v2.74.0 · 2026-08-13** — **Die Editoren rahmen die Bahn jetzt wie der Spielmodus.**
  Der Karteneditor rief `courseSVG` **ohne** `rotate`, `tight` und `corridor`. Folge: Die Bahn lag
  schräg im Bild (nach Norden statt in Spielrichtung), und der Ausschnitt wurde über **alle**
  Features im 55-m-Band gefittet — bei einer Bahn neben der Driving Range oder dem Parkplatz zog
  das Bild weit auf, und die Bahn selbst war ein schmaler Streifen darin. Genau das, was man beim
  Zeichnen nicht gebrauchen kann.
  Jetzt dieselben drei Angaben wie in der Spielkarte: **`rotate`** (Spiellinie zeigt nach oben),
  **`tight`** (gefittet wird nur auf Tee/Linie/Grün plus 32 m Puffer, nicht auf zufällige
  Nachbarflächen) und **`corridor:46`** (gezeichnet wird das 46-m-Band statt 55 m).
  **Nur bei gewähltem Loch** (`!!hole`) — bei „alle" gibt es keine Spielrichtung, und eine
  willkürliche Drehung machte die Übersicht unlesbar.
  Der **Schlag-Editor** hatte `rotate`/`tight` schon, aber das breitere Vorgabeband; er bekommt
  `corridor:46` dazu, damit alle drei Karten denselben Ausschnitt zeigen.
  Möglich ist das ohne weitere Änderung, weil die Rückrechnung die Drehung längst herausnimmt
  (`llFromVB`, `strkLL`) — Ziehen und Setzen rechnen unverändert richtig. Der Prüfstand hält beides
  jetzt fest, damit es beim nächsten Umbau nicht verlorengeht.

- **v2.73.0 · 2026-08-13** — **Vollprüfung der Doku: acht falsche Stellen berichtigt.**
  Nach der Regel aus v2.72 einmal komplett durchgegangen — 167 000 Zeichen Referenzteil,
  mechanisch geprüft (genannte IDs, CSS-Klassen, `DB`-Pfade, Funktionsnamen) und die
  Kernabschnitte 10/11/24 Satz für Satz gegen den Code gelesen. Gefunden und berichtigt:
  **(1) Spielmodus-Karte** stand noch auf `#playMapWrap`/`#playMapSvg` — Container ist seit v1.89
  `#pfMap`, und welcher gilt, bestimmt ausschließlich `playMapSlot()` (der Hinweis fehlte ganz,
  obwohl daran in v1.91 die Dauerlast hing).
  **(2) Messanzeige** (`pfFacts`/`pfDbgRender`/`DB.ui.pfDebug`) stand an zwei Stellen im Präsens
  als vorhanden — entfallen mit v2.03.
  **(3) Fahnensteuerung:** Der EV-Vorrang nannte weiter `pinPoint(geo,hole,pin.d)` als Ziel —
  seit v1.90 ist es durchgängig die Grünmitte.
  **(4) Smash Factor:** `smashAusLM()`/`smashUebernehmen()` gibt es nicht; die Werte bilden
  `lmSmashTag()`/`lmSpeedTag()`/`lmMittel()`.
  **(5) `sigmaFor`** ohne den Plausibilitätsdeckel aus v2.59.
  **(6) `tee()`** noch mit der alten Kurzformel „safe = ES+1.5·pen", ohne Gewichtstabelle,
  Vorlege-Option, zweiten Zug und bevorzugte Seite.
  **(7) `approach()`** ohne den Hinweis, dass es seit v2.59 dieselbe Tabelle benutzt.
  **(8) `grid()`/`planCourse()`** ohne `greenCells`, `troubleFeatures` und `rec.fracs`.
  Dazu: `renderPlay()` war noch als Schale mit Live-Block und Fahnen-Steuerung beschrieben, obwohl
  die Karte seit v1.89 in `pfRender` liegt und die Eingabemaske reine Eingabe ist.
  **Ergebnis der mechanischen Prüfung:** von 18 genannten DOM-IDs war eine tot, von 12 CSS-Klassen
  keine, von 23 `DB`-Pfaden einer. Die Prüfung „Behauptungen gegen den Quelltext" aus v2.72 hatte
  die Geister-Namen zwar gefunden, sie standen aber in meiner eigenen Ausnahmeliste — **eine
  Ausnahmeliste kann eine Prüfung blind machen.** Deshalb steht jetzt bei jedem Eintrag dort, WARUM
  er ausgenommen ist (erzählender Abschnitt vs. fremde Sprache), und die drei Namen, die im Präsens
  behauptet wurden, sind aus der Liste raus.

- **v2.72.0 · 2026-08-13** — **Doku-Pflicht als Regel — und maschinell abgesichert, soweit es geht.**
  Anlass: die Frage „steht das auch alles in der Doku?". Die Antwort war teilweise nein, und die
  Selbstprüfung konnte das nicht wissen — sie fragt nur, ob ein Funktionsname **irgendwo** in der
  Doku vorkommt. Ob ein SATZ noch stimmt, prüft sie nicht. Genau dort war die Doku verrottet: Der
  Karteneditor stand noch als „Pinch-Zoom + Zwei-Finger-Pan" beschrieben, das Sheet ohne
  `sheetCloseSmart`, der Gameplan ohne die stündliche Selbstprüfung — und „konkrete
  Wasser/Bunker/OB werden NICHT doppelt eingetragen" behauptete nach v2.71 **das Gegenteil des
  Codes**. Alle fünf Stellen sind berichtigt.
  **Regel 0 erweitert:** Statt zwei jetzt **drei Ebenen** — Changelog + `APP_VERSION`,
  Referenzabschnitt und **jeder Prosa-Abschnitt, der die geänderte Stelle beschreibt**. Letztere
  ist die Ebene, die reihenweise verrottet: Ein neuer Name wird brav nachgetragen, aber der Absatz
  drei Kapitel weiter behauptet weiter das Alte. Dazu die Gegenprobe vor dem Abliefern: im
  Doku-Block nach den Begriffen der geänderten Stelle suchen und die Fundstellen **lesen** — auch
  die, die man nicht selbst geschrieben hat. Ein Absatz, der halb stimmt, ist schlechter als keiner.
  Die drei belegten Fälle stehen als Begründung dabei; eine Regel ohne Schadensfall wird nicht
  befolgt.
  **Neu im Prüfstand: „Doku — Behauptungen gegen den Quelltext".** Der Referenzteil darf keine
  Funktion mehr als existierend anführen, die es nicht gibt — das fängt Umbenennungen und
  ersatzlose Streichungen, die häufigste Ursache für Sätze, die nicht mehr stimmen. Browser-APIs
  sind ausgenommen; bewusst als *entfallen* dokumentierte Namen (`pfFit`, `pinPoint`, …) stehen in
  einer Liste `GEISTER` — wer dort einträgt, behauptet „steht als Historie da, nicht als Gegenwart".
  Umgekehrt meldet der Lauf, wenn ein Geist wieder existiert; sonst deckt die Liste irgendwann
  echte Fehler zu. Geprüft werden aktuell 337 genannte Namen.
  **Was die Maschine nicht kann, steht als Regel dabei:** Ob ein Satz noch stimmt, bleibt
  Handarbeit — das ist der ehrliche Teil der Antwort.

- **v2.71.0 · 2026-08-13** — **„Hinweise je Loch" wirken jetzt wirklich — in Caddy und Gameplan.**
  Es gab einen Bruch: Der **Loch-Caddy** (im Rundenaufbau) bekam die Einträge als `tr` und wertete
  sie aus — der **Live-Caddy auf der Bahn** und der **Gameplan** nicht. Dort erschien nur die
  Textzeile „💡 Bevorzugt links", die in keine Rechnung eingeht. Was man einträgt, wirkte also
  ausgerechnet dort nicht, wo die Entscheidung fällt. Historischer Grund: Die Hinweise stammen aus
  der Zeit vor Geodaten und EV-Engine, beides wurde nie zusammengeführt.
  **Statt die Felder in jede Rechnung einzeln einzubauen, werden sie einmal in Geometrie
  übersetzt** (`troubleFeatures`) und dem **Lie-Raster** untergeschoben: „rechts, Wasser, ab 190 m"
  wird zu einer Strafzone 24 m seitlich der Ideallinie bei 190 m, „Gefahr vor dem Grün" zu einer
  Zone 16 m davor. Damit wirken sie automatisch in `tee()`, `nextShot()`, `approach()`, im Gameplan
  **und** in den ⚠-Warnungen — mit Streuungssimulation, wie jede echte Fläche auch.
  **Ehrlich zur Genauigkeit:** Aus „Seite + ab wie vielen Metern" lässt sich nur eine geschätzte
  Lage bauen. Das ist die Notlösung für Bahnen **ohne** gezeichnete Fläche; wo die Karte da ist,
  ist Einzeichnen genauer. Ohne Entfernungsangabe entsteht **keine** Zone — eine an der falschen
  Stelle wäre schlimmer als keine.
  **Die „bevorzugte Seite" ist keine Fläche**, sondern eine Vorliebe, und wirkt deshalb an anderer
  Stelle: als Zuschlag von 0,03 Schlägen auf Ziellinien der Gegenseite. Genug, um bei sonst
  gleichwertigen Linien den Ausschlag zu geben, zu wenig, um eine sachlich bessere zu überstimmen.
  **Nicht übersetzt:** Dogleg (wirkt weiter nur im Loch-Caddy) und der Freitext — der ist für den
  Menschen, nicht für den Sampler. **Und das steht jetzt im Editor dran**, aufgeteilt in „rechnet
  mit" und „wird nur angezeigt"; vorher trug man ein und wunderte sich.
  Der Gameplan-Fingerabdruck kennt die Hinweise jetzt, eine Änderung macht den gespeicherten Plan
  also ungültig — an **beiden** Stellen, stündlicher Prüfung wie Handrechnung.
  **Prüfstand:** 24 neue Prüfungen. Die Geometrie wird an einer nach Norden zeigenden Bahn
  nachgerechnet: „rechts" muss östlich landen, die Entfernung stimmen, der Ring geschlossen sein —
  und ohne Entfernungs- oder Seitenangabe darf nichts entstehen.

- **v2.70.0 · 2026-08-13** — **Objekte hängen nicht mehr am Finger — und die Karte lässt sich
  wieder schieben.**
  **(1) Versehentliches Verschieben.** Ein Objekt hing am Finger, **sobald** man es berührte, und
  schon 2 Bildpunkte Bewegung galten als Verschieben. Nach einer Erkennung liegen hunderte
  Trefferflächen von je 13 px über der Bahn — man kam gar nicht mehr an leere Fläche, um die Karte
  zu schieben, und jeder Schiebeversuch verrückte unbemerkt einen Baum.
  Jetzt gilt: **Berühren und halten nimmt auf** (320 ms, mit kurzer Vibration und einem Leuchten am
  Objekt). Wer vorher zieht, **verschiebt die Karte** — der häufigere Fall gewinnt. Wer nur tippt,
  löst weiter das Werkzeug aus. Die Wackelgrenze liegt bei **10 px statt 2**: Ein Finger auf Glas
  wackelt, und ein Wackeln ist keine Absicht.
  **(2) Dabei gefunden und mit repariert:** Der **Ein-Finger-Schub war ganz verschwunden**.
  `GEOED.pan` wurde gesetzt, aber nur noch über einem Objekt, und in `geoEdMove` gab es keinen
  Zweig mehr, der ihn ausführte — übrig blieb Zoomen mit zwei Fingern. Der Schub wird jetzt bei
  jedem Aufsetzen vorbereitet, auch auf leerer Fläche, und nach dem Verwerfen einer Aufnahme
  **ohne Umweg** fortgesetzt, damit die Karte nicht ein Ereignis hinterherhinkt.
  **(3) Kein `preventDefault` mehr auf pointerdown.** Es unterdrückt die abgeleiteten
  Maus-Ereignisse und damit `click` — und darüber laufen Setzen und Löschen. Dieselbe Falle wie im
  Schlag-Editor (v2.63). Gegen das Mitscrollen genügt `touch-action:none`; ein versehentlicher
  Klick nach echtem Schub wird über `gSuppressClick` abgefangen.
  Dazu Aufräumen, das vorher fehlte: Der Wecker der Aufnahme wird beim Loslassen gelöscht (sonst
  nimmt er ein längst losgelassenes Objekt auf), der Schub beendet und das Leuchten zurückgenommen.
  **Die Geste steht dran** — in der Bedienungs-Klappe und im Hinweis der betroffenen Werkzeuge.
  Eine Geste, die man erraten muss, ist keine.
  **Prüfstand:** 18 Prüfungen, darunter die Reihenfolge in `geoEdDown` (Schub vor der
  Objektprüfung) und die Zusicherung, dass kein `preventDefault` zurückkommt.

- **v2.69.0 · 2026-08-13** — **Man sieht wieder durch den Wald hindurch.**
  Nach einer Erkennung liegen schnell hundert gefüllte Kreise und Flächen über der Bahn — im Editor
  verschwindet damit das Luftbild, also genau das, wonach man beim Korrigieren schaut. Man
  korrigierte blind.
  Neu zeichnet `opt.vegFaint` dieselben Objekte als **Umriss**: Flächen ohne Füllung mit dünnem
  gestricheltem Rand, Bäume als **Ring statt Scheibe**. Sie bleiben vollständig sichtbar und
  anfassbar, aber man sieht hindurch.
  **Kleiner gezeichnet heißt nicht schwerer zu treffen** — die unsichtbare Trefferfläche bleibt bei
  13 px. Das wäre die naheliegende Verschlimmbesserung gewesen, und der Prüfstand hält sie fest.
  **Drei Stufen an der Karte** statt in einer Einstellung, weil man sie während der Arbeit mehrfach
  wechselt: **Voll** (Ergebnis der Erkennung beurteilen), **Umriss** (Standard, korrigieren) und
  **Aus** (an Grün, Bunkern und Linien arbeiten, ohne dass Vegetation stört). Der Zustand gilt für
  die offene Sitzung und wandert bewusst nicht in `DB.ui` — es ist eine Arbeitseinstellung, kein
  Geschmack, der über Geräte synchronisiert gehört.

- **v2.68.0 · 2026-08-13** — **Karteneditor neu geordnet: die Karte steht oben.**
  Der Aufbau war nach Erklärungsbedarf sortiert statt nach Benutzung. Vor der Karte lagen: Titel,
  Untertitel, Werkzeugleiste, ein Absatz Erklärung zum aktiven Werkzeug, der Rückgängig-Knopf und
  die komplette Vegetations-Karte samt Empfindlichkeitswahl und vierzeiliger Erläuterung — auf dem
  Telefon **über 400 Bildpunkte, bevor die Arbeitsfläche anfing**. Man scrollte zwischen Werkzeug
  und Karte hin und her und verlor bei jedem Wechsel den Blick auf das, was man gerade tut.
  **Neue Reihenfolge, nach Häufigkeit:** ① **Wo bin ich** — die Loch-Auswahl, eine Zeile ganz oben
  (vorher stand sie unter der Karte). ② **Woran arbeite ich** — die Karte, sofort sichtbar.
  ③ **Womit** — die Werkzeuge, **klebend am unteren Rand**, damit sie auch dann erreichbar
  bleiben, wenn man in der Karte gescrollt hat. Werkzeuge zweizeilig (Symbol über Wort), 52 px
  hoch, sechs gleich breite Spalten statt gequetschter Flex-Kästen.
  **Rückgängig sitzt jetzt an der Karte**, bei ＋/－/⟳ — dort, wo die Hand beim Ziehen ohnehin ist,
  statt in einer eigenen Zeile über der Karte. Es zeigt gedimmt an, wenn es nichts zurückzunehmen
  gibt, und nennt im Tooltip den Schritt.
  **Alles Seltene liegt in Klappen:** Vegetationserkennung, Loch-Feinheiten (Tee/Grün vertauscht)
  und Bestandszahlen. Sie sind einen Tipp entfernt und stehen nicht mehr zwischen Karte und
  Werkzeug.
  **Die Hinweise sind eine Zeile im Imperativ** statt eines Absatzes („Ecken tippen, dann unten die
  Art wählen. Ab 3 Ecken."). Was für alle Werkzeuge gilt — ziehen, zoomen, rückgängig — steht
  einmal in der Klappe „Bedienung" statt sechsmal in jedem Hinweis.
  **Prüfstand:** 16 Prüfungen auf die Reihenfolge selbst (Loch-Auswahl vor Karte, Karte vor
  Werkzeugen, Werkzeugleiste klebt) und darauf, dass die Hinweise kurz bleiben — Sortierung, die
  niemand prüft, rutscht beim nächsten Einschub zurück.

- **v2.67.0 · 2026-08-13** — **Karteneditor: kein Bildmenü, kein Springen, Rückgängig.**
  **(1) Das Browser-Bildmenü** („Bild kopieren / herunterladen / teilen") kam beim Ziehen über die
  Karte. Dieselbe Ursache wie in v2.60.1 auf der Spielkarte — die Luftbildkacheln sind
  `<image>`-Elemente, und ein langer Druck darauf ist für den Browser ein Bildkontext. Im **Editor**
  ist es sogar schlimmer, weil man dort beim Ziehen eines Baums den Finger noch länger stillhält.
  Karteneditor und Schlag-Editor bekommen jetzt dieselben Riegel: `-webkit-touch-callout:none`,
  `user-select:none` und abgefangene `contextmenu`/`dragstart`.
  **(2) Die Ansicht sprang nach jedem Verschieben.** `courseSVG` legt die Projektion bei jedem
  Neuaufbau **neu um die Features**. Zieht man einen Baum an den Rand, ändert sich die Bounding-Box
  und damit `M.W/M.H` — die Bedingung `GEOED.vw!==r.M.W` setzte die Ansicht auf die ganze Bahn
  zurück. Man zoomte heran, zog einen Punkt und war wieder ganz draußen. Selbst bei gleicher Breite
  verschiebt sich der Nullpunkt, der gespeicherte viewBox zeigte also eine andere Stelle.
  Der Ausschnitt wird jetzt **geografisch** gemerkt — Mittelpunkt als lat/lng, Breite in Metern —
  und nach dem Neuaufbau daraus zurückgerechnet. Beides hängt nicht von der Projektion ab und
  übersteht deshalb jede Änderung an ihr.
  **(3) „↶ Rückgängig"** in der Werkzeugleiste, beschriftet mit dem, was es zurücknimmt
  („↶ Rückgängig: Verschieben"). Gesichert wird vor **jedem** Eingriff — Verschieben, Baum, Punkt,
  Grünmitte, Löschen, Fläche, Linie und Erkennung — und zwar `mine` **und** `overrides` zusammen:
  Eine verschobene Grünmitte landet in `overrides`, alles andere in `mine`; wer nur eines sichert,
  stellt die Hälfte wieder her. Tiefe 12, bewusst **nicht** persistiert: Rückgängig gilt für die
  offene Sitzung; ein über Tage mitgeschleppter Verlauf im synchronisierten Datensatz wäre ein
  Sync-Konflikt mit Anlauf.
  **Prüfstand:** 25 neue Prüfungen, darunter die Klemmung des Ausschnitts an beiden Rändern, die
  Reihenfolge (Anker vor `courseSVG`) und eine Prüfung je Eingriff, dass er wirklich vorher
  sichert — ein vergessener Schnappschuss macht den Knopf unzuverlässig, und das ist schlimmer als
  kein Knopf.

- **v2.66.0 · 2026-08-13** — **Zweiter Ausgang: „🗑 Verwerfen" in der Eingabemaske.**
  Bis hierher gab es nur den speichernden Ausgang. Wer eine Runde abbrechen wollte — Regen, drei
  Löcher Schnuppern, versehentlich gestartet — musste sie **speichern und danach in den Runden
  wieder löschen**. Der zweite Weg gehört dorthin, wo man ihn braucht: neben „✔ Beenden &
  speichern", bewusst schmaler und in Rot, weil er der seltenere und der unwiderrufliche ist.
  **Die Rückfrage nennt Zahlen.** „Wirklich verwerfen?" beantwortet man nach drei Stunden auf dem
  Platz falsch; „Timmendorfer Strand · 11 Löcher erfasst (9 Scores) — wird gelöscht und NICHT
  gespeichert" nicht. Wer nichts erfasst hat, bekommt **keine** Rückfrage — dort ist nichts zu
  verlieren, und eine Warnung ohne Inhalt gewöhnt einem das Lesen ab; genau dann klickt man auch
  die wichtige weg.
  Technisch derselbe Weg wie der Abbruchzweig in `playFinish()`, nur gewollt: kein Eintrag in
  `DB.rounds`, `draftFinalize()` setzt den Grabstein, `pfRestoreView()` verlässt den Spielmodus.
  **Reihenfolge zählt** — erst `PLAY.active=false`, dann `pfRestoreView()`, dann `closeSheet()`;
  nur so greift dort `wakeRelease()` und der Bildschirm darf wieder abschalten. `flushCloudNow()`
  ist kein Beiwerk: Der Grabstein muss sofort ins Repo, sonst holt der nächste Merge den Entwurf
  vom anderen Gerät zurück — dieselbe Fehlerklasse wie in v1.60.
  **Prüfstand:** 14 Prüfungen, darunter die Zusicherung, dass nichts in `DB.rounds` landet, und
  beide Reihenfolge-Bedingungen.

- **v2.65.0 · 2026-08-13** — **🌲 Wald ausblenden · Gameplan hält sich selbst frisch.**
  **(1) Neuer Schalter 🌲** in der Kartensteuerung, direkt neben 🗺 und mit derselben Wirkungsweise.
  Auf einer Bahn mit erkanntem Wald liegt sonst so viel Grün über dem Luftbild, dass man die Bahn
  selbst nicht mehr sieht. Ausgeblendet werden Wald, Gebüsch, Hecken, Baumreihen und Baumpunkte —
  **aus beiden Quellen**, aus OSM wie aus den eigenen Zeichnungen (dort liegt auch die automatische
  Erkennung). Wer sie wegschaltet, will sie weghaben, nicht die eine Hälfte davon.
  **Ausdrücklich nur die Anzeige:** Lie-Raster, Blocker, Caddy, Gameplan und ⚠-Warnungen rechnen
  unverändert weiter mit ihnen; die Meldung sagt es beim Umschalten dazu. Ein Schalter, der die
  Bewertung mitveränderte, wäre ein Kartenfehler mit Ansage — und im Prüfstand steht die
  Gegenprobe, dass das Raster diesen Schalter nicht kennt.
  **(2) Der Gameplan frischt sich selbst auf.** Er lag gespeichert und wurde nur auf Knopfdruck neu
  gerechnet — er veraltete also genau dann, wenn er es nicht darf: nach einer Kartenänderung
  (erkannter Wald, verschobenes Grün), nach einem neuen Schläger, nach einem Handicap-Sprung. Daran
  denkt niemand.
  **Nicht „stündlich neu rechnen".** Ein Plan über 18 Löcher heißt 18-mal Monte-Carlo; im Leerlauf
  stündlich zu wiederholen verbrennt Akku für ein identisches Ergebnis — windneutral gerechnet
  ändert sich ohne geänderte Eingaben gar nichts. Stattdessen **stündlich prüfen**, ob sich eine
  Eingabe geändert hat: Fingerabdruck über Platzgeometrie (inklusive `mine`), Schlägersatz und
  Handicap. Nur dann wird gerechnet. Dazu ein Höchstalter von 30 Tagen für alles, was der Abdruck
  nicht erfasst.
  **Zwei Sicherungen:** nie während einer laufenden Runde (der Caddy braucht die Rechenzeit selbst,
  und ein Plan, der sich mitten auf der Bahn ändert, wäre das Gegenteil von Verlässlichkeit), und
  höchstens **ein** Plan je Durchgang — wer zehn Plätze gespeichert hat, soll nicht zehn Läufe am
  Stück bekommen.
  **Prüfstand:** 29 neue Prüfungen. Darunter je eine für die drei Änderungen, die den Plan ungültig
  machen müssen (erkannter Baum, verschobenes Grün, neuer Schläger), Altbestand ohne Abdruck, ein
  kaputter Zeitstempel und die Grenze des Höchstalters bei 29 und 31 Tagen.

- **v2.64.0 · 2026-08-12** — **Wald und Einzelbäume je Loch automatisch erkennen.**
  Neuer Block im Karteneditor: „🌲 Wald & Bäume auf Loch N erkennen". Die Luftbildkacheln liegen
  als Blob-URLs im Speicher, die Canvas ist damit auslesbar — die Analyse läuft **auf dem Gerät**,
  ohne Dienst und ohne Netz (die Kacheln müssen geladen sein).
  **Was es ist:** eine Farb- und Formanalyse, keine Bilderkennung im heutigen Sinn. Gesucht wird,
  was gleichzeitig **grün und dunkel** ist — über den Überschussgrün-Index 2G−R−B, das
  Standardmaß der Fernerkundung ohne Infrarotkanal. Baumkronen sind beides; gemähtes Fairway ist
  grün und hell, Wasser dunkel und nicht grün, Sand hell und nicht grün. Danach Öffnen und
  Schließen gegen Rauschen und Löcher im Kronendach, Zusammenhangskomponenten, Konturverfolgung
  und Douglas–Peucker — aus tausend Randpixeln werden ein paar Dutzend ziehbare Ecken.
  **Die Trennung Wald/Einzelbaum läuft über die Fläche in Quadratmetern**, nicht über Pixel:
  ab 200 m² eine Fläche, 4–140 m² und kompakt ein Baum. Nur so bedeutet dieselbe Form bei
  gröberer Auflösung auch dasselbe.
  **Nichts wird überschrieben.** Erkannte Flächen, deren Mitte in vorhandenem Wald liegt, und
  Bäume näher als 8 m an einem vorhandenen fallen weg — ebenso alles in Wasser, Bunker oder Grün,
  weil dunkles Wasser der häufigste Fehlgriff ist. Das Ergebnis landet in `geo.mine`, also dort,
  wo auch Handgezeichnetes liegt: ziehbar, löschbar und **beim nächsten OSM-Import erhalten**.
  **„↶ Letzte Erkennung zurücknehmen"** entfernt über eine Stapel-ID genau den letzten Durchlauf
  und nichts sonst.
  **Grenzen, ehrlich:** Schlagschatten sind dunkel und leicht grünstichig, dichtes hohes Rough
  sieht aus wie Unterholz, Herbstlaub ist nicht mehr grün. Dafür drei Empfindlichkeitsstufen und
  die Möglichkeit, einzeln zu löschen. Erlaubt eine Luftbildquelle kein CORS, ist die Canvas
  gesperrt — dann sagt die Meldung das und nennt den Weg (Plätze → Satellit).
  **Prüfstand:** 21 neue Prüfungen mit einem Kunstbild aus Rasen, Baumgruppe, Sand und Wasser —
  darunter die beiden Fehlgriffe, auf die es ankommt (Wasser ist dunkel, aber nicht grün; Rasen
  ist grün, aber hell), dass die Konturverfolgung terminiert und dass dieselbe Form je nach
  Auflösung als Baum oder als Fläche gilt.

- **v2.63.0 · 2026-08-12** — **Schläge BEARBEITEN statt tracken — und Tippen legt wieder welche an.**
  **(1) Der Tipp auf die Karte war lautlos tot.** Das Anlegen hing am `click`-Ereignis. Seit v2.43
  ein Finger auf freier Fläche die Karte **verschiebt**, ruft `strkDown` dort `preventDefault()` —
  und das unterdrückt die aus dem Zeiger abgeleiteten Maus-Ereignisse, `click` eingeschlossen.
  Seither passierte beim Tippen nichts, ohne Fehler und ohne Meldung, während die Anleitung
  darüber weiterhin „Tippe auf die Karte" sagte. Der Tipp wird jetzt in `strkUp` erkannt — kurz,
  ohne Bewegung, nicht direkt nach einem Zoom — statt sich auf ein Ereignis zu verlassen, das man
  selbst abgeschaltet hat.
  **(2) Der Name war falsch.** Die Maske wird aus dem **Rundeneditor** geöffnet; man trägt nach,
  was gespielt wurde. Aus „🎯 Schläge tracken" wird **„✏️ Schläge bearbeiten"**, die Überschrift
  ebenso, und der Untertitel erklärt jetzt die Sache statt der Bedienung: Punkt 1 ist der Abschlag,
  jeder weitere die Stelle, an der der Ball zur Ruhe kam — aus den Abständen entstehen die
  Schlagweiten.
  **(3) Der GPS-Knopf hängt am Datum der Runde.** „Ball hier aufzeichnen" ergibt beim Nacherfassen
  einer Runde von vorletzter Woche keinen Sinn — er zeichnet den Schreibtisch auf. Er erscheint
  jetzt nur noch, wenn die bearbeitete Runde **von heute** ist; dann kann man tatsächlich auf der
  Bahn stehen.
  **(4) Ein Schlag lässt sich jetzt auch ohne Kartentreffer anlegen** (`＋ Schlag anlegen`): erster
  Punkt aufs Tee, jeder weitere auf halbem Weg zum Grün — von dort zieht man ihn an die richtige
  Stelle. Fehlen der Bahn die Geodaten, sagt die Meldung das und nennt den Weg (Plätze → Karte),
  statt stumm nichts zu tun; vorher war die Maske ohne Karte ein reiner Betrachter.
  **Prüfstand:** 15 neue Prüfungen, darunter die Zusicherung, dass es keinen `click`-Horcher mehr
  gibt und dass ein Zug (>8 px) nicht als Tipp durchgeht.

- **v2.62.1 · 2026-08-12** — **BUGFIX: Das ✕ konnte in einer Ansicht ohne Bedienelemente enden.**
  Die Weiche aus v2.62.0 prüfte zusätzlich auf `_pfVorher`. Griff diese Bedingung nicht, lief das ✕
  in den Rückfallweg — und der endete an einem Ort, aus dem es keinen Ausweg gibt: Ansicht `play`
  aktiv, aber **ohne** `body.play-mode` und **ohne** offenes Blatt. `pfRender()` steigt bei
  `!PLAY.mapFocus` in der ersten Zeile aus, die Eingabemaske ist geschlossen — auf dem Bildschirm
  steht eine Karte von vorhin, an der nichts mehr reagiert, mit Kopfzeile und Navigation drumherum.
  Das sieht aus wie ein Absturz, ist aber eine Ansicht ohne Bedienelemente.
  Zwei Änderungen: Die Bedingung ist jetzt **schlicht** — läuft eine Runde und ist die Karte gerade
  nicht offen, führt ✕ dorthin zurück, mehr wird nicht geprüft. Und `pfEnsureUsable()` stellt den
  brauchbaren Zustand wieder her, statt ihn zu erklären: Karte gewünscht → Karte, sonst die
  Eingabemaske. Sie läuft an den drei Stellen, an denen der Zustand entstehen kann — nach dem ✕,
  nach einem Zurück-Druck und bei jedem Wechsel auf die Spielansicht — und steigt vorher aus, wenn
  ein Blatt offen ist, das Vollbild steht oder eine andere Ansicht aktiv ist; sonst würde sie in
  Zustände hineinregieren, die völlig in Ordnung sind.
  **Prüfstand:** 8 Prüfungen für die Wache, darunter alle drei Ausstiege und alle drei Aufrufer.

- **v2.62.0 · 2026-08-12** — **✕ führt zurück auf die Karte · Schlagart beim Nachtragen ·
  Karteneditor zoomt tief.**
  **(1) „✎ Eingabe → ✕" verlor das Vollbild.** `closeSheet()` baut seinen History-Eintrag über
  `history.back()` ab, und Android wertet eine **Rückwärtsnavigation als Verlassen des Vollbilds**.
  Man landete ohne Vollbild und dazu auf der nackten Spielansicht statt auf der Karte. Das ✕ hängt
  jetzt an `sheetCloseSmart()`: Läuft eine Runde und war die Karte der Ausgangspunkt, geht es über
  `pfOpen()` zurück — das setzt `_sheetHist=false` VOR dem Schließen, es gibt also gar kein
  `history.back()` und damit keinen Grund für den Browser, das Vollbild zu beenden. Blätter, die
  ÜBER der Karte liegen (Scorekarte, Details), bleiben unberührt; überall sonst schließt ✕ wie
  bisher.
  **(2) Schlagart auch beim nachträglichen Einzeichnen.** Die Live-Aufnahme kannte sie längst
  (`PLAY.rec.swing`), das Einzeichnen auf der Karte nicht — dabei entstehen so die meisten
  Schläge: nach der Runde, aus der Erinnerung. Ohne Schlagart ist ein Schlag für die
  Schlägerlängen nicht verwertbar oder, schlimmer, **falsch** verwertbar: Ein ¾-Pitching und ein
  voller landen im selben Topf und drücken die gelernte Länge. Jede Schlagzeile hat jetzt eine
  zweite Auswahl (Voll · ¾ · Halb · Viertel · Punch · Flop · Chip · Bunker, aus `DB.swingTypes`).
  Leer bedeutet weiterhin „Voll", damit Altbestand nicht plötzlich aus der Datenbasis fällt. Der
  **letzte Punkt** bekommt weder Schläger noch Schlagart — er ist die Ruhelage, kein Schlag.
  Das Setzen zeichnet die Karte nicht neu; ein Neuaufbau würde die gerade geöffnete Auswahl
  schließen.
  **(3) Der Karteneditor zoomt jetzt rund fünfzigfach statt achtfach.** Die Grenze lag bei 12 %
  der Bahnbreite, mindestens 40 m Bildbreite. Fürs Anschauen richtig, fürs **Zeichnen** nicht: Eine
  Bunkerkante liegt im Bereich weniger Meter, und bei 40 m Bildbreite deckt eine Fingerkuppe rund
  drei Meter ab — man setzt den Punkt blind. Neu 2 % der Bahnbreite, mindestens 3 m.
  **Die Formel stand doppelt im Code** — einmal für Knöpfe und Mausrad, einmal im Pinch-Zweig.
  Zwei Kopien derselben Grenze heißt früher oder später zwei verschiedene Grenzen, je nachdem ob
  man mit den Fingern oder dem Knopf zoomt; ein Unterschied, den man auf dem Gerät kaum bemerkt.
  Jetzt eine Quelle (`editMinW`), die sich der **Schlag-Editor teilt**: Wer einen Schlag auf die
  Bahn setzt, zielt auf denselben Meter wie beim Zeichnen einer Kante.
  **Grenze der Schärfe:** Das Luftbild wird für den Ausschnitt der Bahn geladen und beim
  Hineinzoomen nur vergrößert — die Kacheln ziehen nicht mit. Wer schärfer zeichnen will, hebt die
  Kachelauflösung an (Plätze → Satellit).
  **Prüfstand:** 19 neue Prüfungen, darunter die Zusicherung, dass es keine zweite Zoomgrenze mehr
  gibt, und dass die Ruhelage keine Schlagart bekommt.

- **v2.61.2 · 2026-08-12** — **Ein zweiter Fehlschlag ist eine Nachricht, kein Schweigen.**
  Nachtrag zu v2.61.1: Scheitert auch die **nachgerüstete** Anfrage — die mit garantiert frischer
  Fingergeste —, dann liegt es nicht mehr an der Geste, sondern am Browser oder an einer
  Richtlinie. Weitere Versuche helfen dann nicht, ein stiller Fehlschlag lässt einen aber ratlos
  zurück. Deshalb sagt die App genau **einmal je Runde**, was tatsächlich hilft: die App zum
  Startbildschirm hinzufügen. Der erste Fehlschlag bleibt weiterhin still (er ist der normale
  Fall und wird ja gleich nachgeholt), und der zweite Versuch stellt sich nicht erneut scharf —
  sonst hinge an jedem Fingertipp eine neue Anfrage, die aus demselben Grund wieder abgelehnt wird.

- **v2.61.1 · 2026-08-12** — **BUGFIX: Der Start ins Vollbild sprang nicht an.** Zwei Ursachen,
  beide am Aufrufort nicht erkennbar.
  **(1) Der Weg ist länger als gedacht.** Der Rundenstart läuft über `playBegin` → `renderPlay` →
  `pfOpen`, dazwischen liegen Blatt schließen, Ansichtswechsel und ein kompletter Neuaufbau.
  Manche Browser werten die Nutzergeste dann als verbraucht — die Anfrage war formal synchron und
  trotzdem zu spät.
  **(2) Die Antwort kommt als Promise.** Ob abgelehnt wurde, weiß man erst danach — zu spät, um im
  selben Zug etwas anderes zu versuchen.
  **Lösung: eine Nachrüstung.** Zusätzlich zum direkten Versuch wird eine Anfrage *scharfgestellt*;
  klappt der direkte Weg nicht, wiederholt sie der nächste Fingertipp irgendwo in der App — dann
  garantiert mit frischer, direkter Geste. Der erste Tipp auf der Karte genügt also. Einmal scharf,
  einmal ausgelöst, dann Ruhe. Das manuelle Schließen über ⛶ entschärft sie mit.
  **Der Standalone-Riegel ist gefallen.** Er war gut gemeint — in der installierten App gibt es
  keine Adressleiste — aber falsch: Vollbild blendet dort weiterhin die **Statuszeile** aus, und
  genau das will man auf der Runde. Außerdem meldet nicht jeder Browser `display-mode` zuverlässig;
  der Riegel könnte also auch im normalen Tab zugeschlagen haben und wäre damit die schlichteste
  Erklärung dafür, dass gar nichts passierte.
  Das Umschalten auf „Beim Start" in den Einstellungen löst das Vollbild jetzt **sofort** aus —
  der Tipp auf den Schalter ist die Geste, also kann man sie auch verwenden.

- **v2.61.0 · 2026-08-12** — **Die Karte öffnet im Vollbild.** Bisher musste man nach jedem
  Wechsel in die Kartenansicht ⛶ antippen, um die Adressleiste loszuwerden — auf der Runde jedes
  Mal aufs Neue. Jetzt fragt `pfOpen()` das Vollbild selbst an.
  **Warum synchron:** Die Fullscreen-API verlangt eine Nutzergeste. `pfOpen()` läuft fast immer in
  einer (Rundenstart, 🗺, Umschalten aus der Eingabemaske), aber nur, solange der Aufruf im selben
  Arbeitsschritt bleibt. Ein `setTimeout` davor kappt die Kette und die Anfrage scheitert still —
  deshalb steht sie **vor** dem verzögerten `playMapRender()`.
  **Drei Fälle, in denen bewusst nichts passiert:** Die Automatik ist abgeschaltet (neue
  Einstellung unter Daten → Darstellung); das Vollbild wurde in dieser Runde selbst verlassen
  (dagegen anzukämpfen wäre Bevormundung — der Merker fällt beim nächsten Rundenstart); oder die
  App läuft als installierte PWA, wo es gar keine Adressleiste auszublenden gibt.
  Abgelehnte automatische Anfragen bleiben **still**. Die manuelle über ⛶ meldet sich weiterhin,
  weil sie angefordert wurde — eine Ablehnung, die man nicht bestellt hat, ist nichts, worüber man
  am Abschlag lesen will.
  **Prüfstand:** 10 Prüfungen, darunter die Reihenfolge im Aufrufer (Vollbild vor dem verzögerten
  Kartenaufbau) — die Bedingung, an der die ganze Sache hängt und die man beim Umbauen zuerst
  zerstört.

- **v2.60.1 · 2026-08-12** — **BUGFIX: Beim Verschieben der Landezone öffnete der Browser sein
  Bildmenü.** Die Luftbildkacheln sind `<image>`-Elemente, und Chrome auf Android behandelt einen
  langen Druck darauf als Bildkontext („Bild herunterladen / in neuem Tab öffnen"). Beim Ziehen
  des Zielpunkts hält man den Finger genau so: aufsetzen, kurz halten, ziehen. Das Menü kam also
  ausgerechnet bei der Geste, die es am stärksten stört, und riss die Bewegung ab.
  Drei Riegel, weil ein einzelner nicht überall greift: Die Kacheln nehmen **keine Zeiger** mehr an
  (`#playSatG{pointer-events:none}` — die Ereignisse liegen ohnehin auf dem SVG), das Aufklappen
  ist per `-webkit-touch-callout:none` unterdrückt, und `contextmenu` sowie `dragstart` werden
  abgefangen — Ersteres kennt nur WebKit, Letzteres braucht es für die rechte Maustaste am
  Schreibtisch. Dazu `user-select:none`, sonst markiert derselbe Druck die Beschriftungen.
  **Prüfstand:** 5 Prüfungen, darunter die Gegenprobe, dass das SVG selbst weiter Zeiger annimmt —
  wer beim Stummschalten der Kacheln die Karte miterwischt, hat sie unbedienbar gemacht.

- **v2.60.0 · 2026-08-12** — **Messpunkt: ab Tee statt ab Sofa.** Gemessen wurde immer von der
  GPS-Position. Richtig, solange man auf der Bahn steht — unbrauchbar, sobald man es nicht tut:
  Beim Planen zu Hause stand am Messpunkt „2690 m bis dorthin", und die eigentlich gefragte Zahl
  (wie weit ist der Bunker **vom Abschlag**?) fehlte ganz. Jetzt greift dieselbe Schwelle wie beim
  Caddy (`playTooFar`): Ist die eigene Position unplausibel weit weg, ist der Bezugspunkt das Tee.
  **Beschriftet**, sowohl in der Meldung („237 m ab Tee · noch 305 m zur Fahne") als auch an der
  Messlinie auf der Karte — eine Zahl ab Tee, die aussieht wie eine ab Standort, wäre schlimmer als
  keine. Karte und Meldung holen den Bezug aus **einer** Funktion (`_measOrig`), damit sie nie
  auseinanderlaufen. Ohne Tee-Punkt bleibt es beim Standort (lieber eine unplausible Zahl als gar
  keine Messung), ohne GPS-Fix ist das Tee der Bezug.
  **Prüfstand:** 10 neue Prüfungen, inklusive der beiden Ausfallwege und der Zusicherung, dass die
  Beschriftung mitläuft.

- **v2.59.0 · 2026-08-12** — **Der Caddy denkt einen Schlag weiter · Spielweise wirkt auch am Grün ·
  σ-Deckel.** Ausgelöst durch zwei Beobachtungen auf der Runde: „warum Driver auf das enge Stück,
  wenn ich sicher spiele?" und „warum immer stumpf Grünmitte?".
  **(a) Die Spielweise wirkt jetzt auch beim Approach.** Dort stand eine ZWEITE, eigene Formel:
  `safe` erhöhte nur das Strafgewicht, **Sand kam gar nicht vor**. Derselbe Bunker wurde am
  Abschlag mit 0,40 gewichtet und am Grün mit 0,00 — zwei Stellen, zwei Modelle, und der
  Umschalter tat beim Approach fast nichts. Jetzt beide über `spielweise(mode).lie`.
  **(b) Der Abschlag wird zweizügig bewertet.** Nachgerechnet für ein 499-m-Par-5: 45 m mehr
  bringen **0,310** Schläge, der Rough-Anteil des Drivers kostet **0,167**, der Sicherheitszuschlag
  **0,120** — der Driver gewann um **0,02 Schläge**. Eine Entscheidung im Rauschen, getroffen ohne
  die Information, die sie kippt: `pointES` kennt vom Landepunkt nur Entfernung und Lage, nicht
  den zweiten Schlag von dort. `_ply2` liefert ihn — bester nächster Schlag mit demselben Sampler
  und denselben Gewichten; die Differenz zur generischen Tabelle ist die Korrektur.
  **Kosten begrenzt:** nur für die fünf besten Kandidaten, 40 statt 100 Samples, 2 Schläger ×
  3 Linien. **Dämpfung 0,7**, weil nur der mittlere Landepunkt bewertet wird — die Korrektur soll
  eine knappe Entscheidung kippen, keine klare überstimmen. Zusätzlich ist **eine Vorlege-Option
  unter 140 m** zugelassen; vorher war jeder kürzere Schläger vom Abschlag ausgeschlossen, „kurz
  vorlegen für einen freien zweiten Schlag" also gar nicht wählbar.
  Am Kunstloch geprüft (Par 5, breites Fairway, Querhindernis bei 400 m): Die zweite Ebene
  verschiebt die Kandidaten um **0,5 bis 1,9 Schläge** und ordnet sie neu — 3 Wood fällt von Rang 2
  auf 4, weil er direkt vor dem Wasser endet. Der gewählte Abschlag nennt jetzt seinen Folgeschlag
  („→ danach 7 Wood") und, wenn die Korrektur ≥ 0,15 ist, auch sie.
  **(c) „Grün 0 %" war zweideutig** — entweder trifft der Schläger nie, oder es gibt im Raster gar
  kein Grün, weil den Geodaten das Polygon fehlt. Im zweiten Fall ist jeder Zielpunkt gleich
  schlecht bewertet und die Zieloptimierung läuft blind: Sie kann nur noch nach Entfernung zur
  Fahne entscheiden — und landet zwangsläufig in der Mitte. `grid()` zählt jetzt die Grünzellen,
  `approach()` meldet `noGreen`, und die Karte schreibt **„kein Grün-Polygon"** statt einer Zahl,
  die keine ist.
  **Dazu der σ-Deckel:** `learnLateralFromRounds` rechnet die Seitenstreuung aus der
  Fairwaytrefferquote zurück (σ = W / Φ⁻¹((1+p)/2), feste halbe Breite W = 20 m) und klemmt erst
  bei 45 m — bei p = 0,5 sind das 30 m. Dieser Wert landet auf dem erfassten **Tee-Schläger**, und
  derselbe Schläger wird später für Annäherungen benutzt. Ein 7 Wood mit 26 m Seitenstreuung
  verschluckt das halbe Loch: die Grüntrefferquote fällt auf 0 und die Zieloptimierung hat nichts
  mehr zu unterscheiden. `sigmaFor` bindet gelernte Werte jetzt an die Schlaglänge — quer höchstens
  13 %, längs höchstens 11 % der Carry (Tour 4–6 %, HCP 20 etwa 8–10 %). Der **rohe Wert bleibt
  gespeichert und in der Schlägerliste sichtbar**; gekappt wird nur, was in die Rechnung geht, und
  die Quelle sagt „gedeckelt" dazu.
  **Prüfstand:** 21 neue Prüfungen — Approach nimmt die Gewichtstabelle und Sand geht ein, die
  zweite Ebene ist gedämpft und ersetzt die erste nicht, überhöhtes σ wird gedeckelt und
  gemessenes bleibt unangetastet, Grünzellen werden gezählt.

- **v2.58.0 · 2026-08-12** — **Streuung: beschriftet, sichtbarer, für jeden Schlag.** Drei Punkte
  aus der Praxis.
  **(1) Die Ringe sagen jetzt, was sie bedeuten:** „68 %" am inneren (1σ), „95 %" am äußeren (2σ).
  Die Beschriftung hängt am obersten Punkt des jeweiligen Rings — gelesen aus dem PFAD und nicht
  aus der Geometrie gerechnet, weil die Karte gedreht sein kann: „oben" ist dann die
  Bildschirmrichtung und nicht die Schlagrichtung, und nur der Pfad kennt beides zugleich
  (`pathTopPoint`). Beschriftet wird nur, wenn der Ring dafür breit genug ist — auf einem schmalen
  Oval wäre die Zahl größer als die Form, die sie erklärt.
  **(2) Der 2σ-Ring war kaum zu sehen.** Gold auf Gras ist kontraktarm, und eine dünne gestrichelte
  Linie hat kaum Fläche, die sich abheben könnte. Jetzt kräftigerer, dunklerer Strich, längere
  Striche mit runden Enden — und beide Ringe bekommen einen **hellen Saum** darunter, der sie auch
  auf dem Luftbild trägt.
  **(3) Alle Schläge statt nur der erste.** Der Plan besteht aus zwei bis vier Schlägen, und die
  Streuung des Layups entscheidet mit darüber, ob der Angriff danach überhaupt möglich ist. Der
  aktuelle Schlag bleibt kräftig, die folgenden treten auf 62 % zurück — sonst konkurrieren drei
  gleich laute Ovale um dieselbe Aufmerksamkeit.
  **Prüfstand:** 9 neue Prüfungen für `pathTopPoint` (kleinstes y gewinnt, erster Punkt zählt mit,
  negative Werte, Gleichstand bleibt stabil statt zu springen, unvollständige Pfade ergeben `null`)
  sowie die Gegenprobe am echten Ring.

- **v2.57.0 · 2026-08-12** — **Die Streuungsquote kommt jetzt aus dem Platzraster, nicht aus einem
  Ersatzmaß.** In v2.56 stand im Gameplan eine generische Quote („Anteil, der quer in einem
  30-m-Korridor bleibt"). Das war die schwächere Zahl — und überflüssig: `STRAT.tee()` und
  `approach()` tasten längst das **Lie-Raster des Lochs** ab, also die echten Fairway-, Grün-,
  Sand- und Strafflächen aus den Geodaten, mit der Streuung des Schlägers und 150 Streubildern.
  Lochgenau statt pauschal. Die Zahlen lagen bereits vor, standen aber **nur im `warum`-Fließtext
  der aufgeklappten Lochzeile** — also genau dort, wo man sie beim Vergleichen nicht sieht.
  Drei Änderungen: `planCourse` legt die Anteile jetzt **strukturiert** ab (`rec.fracs`) statt nur
  im Satz; die **Lochzeile** im Gameplan zeigt sie direkt („L1 · Par 4 · Driver · FW 61 %"); und
  die Streuungskarte führt je Schläger die **echte Quote aus dem Raster**, gemittelt über die
  Löcher, auf denen er geplant ist, samt Auflistung dieser Löcher. Das 30-m-Ersatzmaß bleibt nur
  für Schläger ohne Plandaten, und das Schema ist als das gekennzeichnet, was es ist: eine
  Veranschaulichung der Streuung, die an keinem Platz hängt.
  **`approx` wird durchgereicht:** Fehlt einem Loch die Fairwayfläche in den Geodaten, ersetzt das
  Raster sie durch einen ±20-m-Korridor um die Ideallinie. Die Quote ist dann eine Näherung — das
  steht jetzt dabei, statt eine Genauigkeit vorzutäuschen, die die Daten nicht hergeben.
  **Prüfstand-Fund nebenbei — dieselbe Fehlerklasse wie das 1500-Zeichen-Fenster, zwei Ebenen
  höher:** Die STRAT-Methoden wurden aus einem FESTEN Fenster von 60 000 Zeichen ab `const STRAT=`
  gelesen. Das war zugleich zu klein und zu groß. Zu klein: ein paar Kommentarzeilen mehr in
  `planCourse`, und die letzten Methoden fielen aus der Liste — sie standen dann in keiner der
  beiden Mengen, also weder Meldung noch Aufräumhinweis, die Sperrklinke hörte still auf, sie zu
  bewachen (58 statt 60). Zu groß: das Fenster reichte **17 000 Zeichen über das Objekt hinaus**,
  und alles, was dort zufällig `^  name(` schrieb, zählte als STRAT-Methode — die gemeldeten
  „60 Methoden" waren zur Hälfte Fremdcode. Tatsächlich sind es **31**. Jetzt klammerbegrenzt, mit
  Gegenproben in beide Richtungen (bekannte Methoden sind drin, `stratOn` von dahinter nicht).
  Die ehrliche Abdeckung lautet damit 14/31 statt 43/60.

- **v2.56.0 · 2026-08-12** — **Streuung ohne Karte: im Gameplan und in der Schlägerliste.**
  Bis v2.55 hing die Streuung an der Spielkarte — sie brauchte Geodaten, GPS und eine laufende
  Runde. Am Schreibtisch, also genau dann, wenn man den **Gameplan** macht und über Schlägerwahl
  nachdenkt, war sie unsichtbar. Die Zahl dahinter braucht davon aber nichts: σ steht je Schläger
  in `DB.strat.dispersion` (gelernt) oder folgt aus der Schlägerfamilie (Heuristik).
  **Im Gameplan** neu die Karte **„Streuung der geplanten Schläger"**: jeder im Plan verwendete
  Schläger mit σ quer/längs, Tendenz zur eigenen Fehlerseite, Quelle (gelernt/Heuristik) und —
  aufgeklappt — einer **schematischen Landezone** in Draufsicht: 1σ gefüllt, 2σ gestrichelt, ein
  30-m-Korridor als Vergleichsmaß, Maßstabsbalken. Ein Maßstab für beide Achsen; zwei würden die
  Form verfälschen, und die Form ist hier die Aussage.
  **Der eigentliche Nutzen ist `dispHitShare`:** σ allein ist abstrakt. „±14,8 m quer" sagt wenig —
  **„ein 30-m-Fairway triffst du damit in 68 % der Fälle"** ist eine Entscheidungsgrundlage.
  Gerechnet wird nur die Querachse (ein Korridor ist längs offen), `biasL` geht mit ein: Bei
  gleicher Streuung, aber Tendenz nach rechts, fällt die Quote messbar ab — das ist der Grund,
  warum die Ziellinie nicht für jeden Schläger dieselbe sein kann.
  **Berücksichtigt war sie schon:** `STRAT.planCourse` simuliert je Kandidat 150 Streubilder und
  wählt danach. Neu ist, dass die Grundlage dieser Wahl sichtbar wird — vorher rechnete der Plan
  mit Werten, die nirgends standen.
  **In der Schlägerliste** steht die Streuung jetzt bei **jedem** Schläger, nicht nur bei
  gemessenen. Ohne Messung greift die Heuristik nach Schlägerfamilie; sie ist eine Schätzung, aber
  eine begründete, und sie steht als solche dabei. Vorher blieb die Zeile leer, während der Caddy
  mit genau diesen Werten rechnete.
  **Prüfstand:** 29 neue Prüfungen. Darunter die Lehrbuchwerte (Korridor = 2σ → 68,3 %, 4σ →
  95,4 %), dass ein engerer Schläger öfter trifft, dass die Längenstreuung die Korridorquote NICHT
  ändert, dass die Tendenz symmetrisch wirkt, dass eine Tendenz unter 1 m verschwiegen wird (sie
  erschiene sonst als „Tendenz rechts 0 m"), und dass das Schema das Seitenverhältnis von σ
  einhält — die Prüfung, die einen zweiten Maßstab sofort auffliegen ließe.

- **v2.55.0 · 2026-08-12** — **BUGFIX: Der Streuungs-Schalter meldete „an", die Karte zeigte
  nichts.** Zwei Ursachen, beide in v2.54 übersehen.
  **(1) Das Oval entstand nur bei aufgeklapptem Caddy.** `PLAY.stratOval` wurde ausschließlich in
  `playCaddyHtml()` gesetzt — der Funktion hinter der AUFGEKLAPPTEN Caddy-Anzeige. Im Vollbild ist
  der Caddy standardmäßig zugeklappt; dort liefert `playCaddyNow()` die eine Zeile, und die setzte
  nichts. Ohne Mittelpunkt zeichnet `courseSVG` kein Oval — der Schalter schaltete also etwas ein,
  das es nicht gab. `playCaddyNow()` setzt den Wert jetzt selbst (Mittelpunkt aus `ev.target` bzw.
  `ev.best.tgt`) und nullt ihn, wenn es keine Bewertung gibt.
  **(2) Das gebackene Oval wäre stehen geblieben.** `courseSVG opt.oval` steckt im SVG-Rumpf und
  wird nur bei einem VOLLEN Kartenneubau erneuert. Der GPS-Tick zeichnet aus gutem Grund nur die
  Vektorebene neu (v1.91: sonst Dauerlast durch Monte-Carlo und Kachelabrufe) — die orange Linie
  wäre also mitgewandert und das Oval nicht. Deshalb wird die Streuung jetzt **dort gezeichnet, wo
  die Linie liegt**: in `playAimDraw`, über `dispRingPath`. Gleiche Ebene, gleiche Aktualisierung,
  gleicher Bezug — das Oval sitzt am Landepunkt des Schlags, den die Linie zeigt.
  Gezeichnet wird der **erste Schlag der Kette**, also der, den man jetzt spielt; zuerst, damit
  Linie und Beschriftung darüber liegen. Die Legs aus `_aimBuild` tragen dafür `sg` — aus der
  Bewertung, wenn es eine gibt, sonst aus `STRAT.sigmaFor` des gewählten Schlägers. Der zweite
  Fall ist der wichtige: bei Layups und von Hand gesetzten Wegpunkten gibt es kein `ev`, und ohne
  ihn bliebe die Karte trotz dastehendem Schläger leer.
  Damit nichts doppelt liegt, zeigt das gebackene `opt.oval` nur noch bei **ausgeschalteter**
  Zielkette (🎯). Nebenbei repariert: `_aimApproachEv` reichte den Zielpunkt `tgt` nicht durch —
  tee() und nextShot() legen ihn als `target` ab, approach() als `best.tgt`, und der Adapter
  ließ ihn fallen.
  **Prüfstand:** 14 neue Prüfungen für `dispRingPath` — geschlossener Pfad mit 37 Punkten,
  Halbachse quer = σ quer und längs = σ längs, 2σ doppelt so breit, bei 90° tauschen die Achsen,
  `biasL` verschiebt ohne zu verformen, und unvollständige Eingaben liefern einen LEEREN Pfad
  statt `MNaN NaN…` (ein NaN im `d`-Attribut lässt den Browser den ganzen Pfad verwerfen).

- **v2.54.0 · 2026-08-12** — **Streuung des empfohlenen Schlägers auf der Karte — mit Schalter.**
  Das Oval selbst gibt es seit Phase 1 (`courseSVG opt.oval`, 1σ kräftig, 2σ gestrichelt), es
  erschien aber **nur, wenn die EV-Engine gerechnet hat**: am Abschlag mit Geodaten oder im
  Approach zwischen 8 und 200 m, und nur bei eingeschaltetem STRAT-Modul. In allen anderen Lagen —
  Layup, Recovery, Platz ohne Geodaten, STRAT aus — empfahl der Caddy einen Schläger, und die
  Karte zeigte nur eine Linie. Das ist die falsche Hälfte der Information: **ob ein Bunker im
  Spiel ist, entscheidet nicht die Ziellinie, sondern die Streuung um sie herum.**
  Neu ein **Schalter** an zwei Stellen: als Chip im Caddy (📐 Streuung an/aus · ±17 m quer ·
  ±9 m lang) und als **📐** in der Kartenleiste, damit er auch im Vollbild ohne aufgeklappten
  Caddy erreichbar ist. Die Zahl steht auf dem Knopf — die Größenordnung soll auch ohne Blick auf
  die Karte ankommen. Zustand in `DB.ui.disp`, Standard **an** (das bisherige Verhalten).
  Neu außerdem ein **Fallback** (`dispOvalFrom`): Rechnet die EV-Engine nicht, wird das Oval aus
  der Heuristik-Empfehlung gebaut — eigene Position, Richtung zum Grün, **geplante** Schlagweite
  des ersten Vorschlags (die trägt den Lage-Faktor bereits mit) und σ des Schlägers. Der
  Mittelpunkt ist damit der erwartete Ruhepunkt, nicht die eigene Position; die Streuung liegt um
  das Ziel, nicht um die Füße.
  **σ-Quelle bleibt `STRAT.sigmaFor`:** gelernt ab n≥20 Schlägen aus `DB.strat.dispersion`, sonst
  Heuristik nach Schlägerfamilie. Welche von beiden gerade gilt, steht als Etikett daneben — eine
  gemessene Streuung ist etwas anderes als eine geschätzte, und der Unterschied gehört sichtbar.
  1σ ≈ 68 % der Schläge, 2σ ≈ 95 %; das Oval ist längs und quer verschieden breit, `biasL`
  verschiebt es zur eigenen Fehlerseite (aus den Tee-Ergebnissen gelernt, ab 8 Einträgen).
  **Reihenfolge repariert mitgeliefert:** `PLAY.stratOval` entsteht beim Zeichnen des Caddys, also
  muss die Karte DANACH neu gebaut werden — sonst zeigt sie beim Umschalten noch den vorigen
  Zustand. `caddyContext()` wird im Live-Caddy jetzt einmal statt zweimal berechnet.
  **Prüfstand:** 22 neue Prüfungen — Mittelpunkt liegt in Schlagweite (auch bei schräger
  Richtung), 0° zeigt nach Norden und 90° nach Osten, σ und Seitenversatz werden unverändert
  durchgereicht, unvollständige Eingaben liefern `null` statt eines halben Ovals (sonst malte
  courseSVG um `[undefined,undefined]` und die Karte bliebe leer), und der Chip verschwindet
  ohne belastbares σ.

- **v2.53.0 · 2026-08-12** — **Sechs UI/UX-Eingriffe: Loch-Abschluss, Rundeneditor, Startseite,
  Sprung, Platz-Modus, Zugänglichkeit.**
  (1) **Loch abschließen in zwei Tipps.** Am Handy fehlte der Weg, den die Uhr längst hat: Der
  Pfeil in der Aktionsleiste wechselte das Loch, ohne nach Score und Putts zu fragen. Wer sie
  führen wollte — und man führt sie auf JEDEM Loch — brauchte drei Ansichtswechsel für zwei
  Zahlen. Neu **„✔ Loch"**: Score-Raster (Par−2 bis Par+3, Par hervorgehoben, Namen Eagle/Birdie/
  Par/Bogey/Doppel), dann Putts (0–4, 2 vorgewählt), danach wird gewechselt. Abkürzer
  **„Par · 2 Putts"** erledigt das häufigste Loch in EINEM Tipp. Zustand in `PLAY.wiz`,
  Darstellung in `pfWizHtml` — rein und im Prüfstand abgesichert.
  In der Eingabemaske wandert Phase ⑤ in eine **klebende Leiste** (`playCloseBarHtml`): Score und
  Putts stehen fachlich am Ende der Spielreihenfolge und körperlich immer am Daumen. Die
  Stepper-IDs bleiben und kommen nur EINMAL im Dokument vor — doppelt hätte `playAdj` nur den
  ersten mitgezogen.
  (2) **Rundeneditor: zugeklappte Lochkarten + Lochstreifen.** Vorher standen 18 Karten mit je 21
  Bedienelementen in einem Scrollblatt — rund 380 Felder ohne Navigation und ohne Überblick,
  welche Löcher noch leer sind. Jetzt `<details>` je Loch mit Zusammenfassungszeile
  („5 · 2 Putts" / „5 · Putts fehlen" / „–"), darüber eine 1–18-Leiste, die zum Loch springt, es
  aufklappt und den Score fokussiert. Füllstand dreistufig **leer / teil / voll** aus derselben
  Quelle wie `collect()`, damit Zeile und Streifen nicht behaupten können, was nicht gespeichert
  wird. Die Grenze sind die **Putts**: ohne sie liefert Strokes Gained nur den Gesamtwert, ein
  Loch mit Score allein ist NICHT fertig.
  (3) **„Jetzt dran" auf der Startseite.** Heute war nach Zeitpunkt gegliedert — richtig sortiert,
  aber sieben gleich gewichtete Karten sind eine Liste, keine Antwort auf die Frage, mit der man
  die Seite öffnet. `heuteJetzt()` entscheidet nach Rangfolge: unterbrochene Runde → Abschlagzeit
  (taktet den Tag rückwärts: planen / dehnen / aufwärmen / spielen) → heute gespielt und nicht
  nachgedehnt → Turnier im Vorbereitungsfenster → lange keine Runde → sonst Spielmodus. Die
  Funktion ist **rein** (Uhrzeit kommt als Minuten herein), `heuteJetztHtml()` sammelt den
  Zustand — deshalb sind alle elf Zweige geprüft, samt der Zusicherung, dass jeder Knopf eine
  existierende Funktion aufruft.
  (4) **Kein Sprung mehr beim Gruppenwechsel.** `setView` blendete `#subnav` bei Gruppen mit nur
  einer Ansicht aus; der gesamte Inhalt sprang um deren Höhe — ausgerechnet in den drei täglich
  benutzten Gruppen (Heute, Runden, Turnier). Jetzt steht immer eine Zeile, bei einer einzigen
  Ansicht als Bezeichnung ohne Funktion (`.solo`), damit nichts Klickbares Leerlauf verspricht.
  (5) **Platz-Modus.** Nicht das dunkle Design (das gibt es) und nicht die Systemschrift (die
  zerlegt die Rasterhöhen), sondern gezielt die Stellen, die bei Sonne und mit Handschuh zu klein
  sind: Feldbeschriftungen, Auswahlfelder, Stepper, Aktionsleiste im Vollbild, Trefferflächen
  mindestens 48 px. Schalter in **Daten → Darstellung** und als **🔎** direkt in der
  Kartenleiste — dort fällt auf, dass man ihn braucht. Zustand in `DB.ui.platz`, gesetzt als
  `html[data-platz="1"]`, beim Start über `applyPlatzModus()` wiederhergestellt.
  (6) **Zugänglichkeit.** Symbolknöpfe trugen ihre Bedeutung allein (`⛶`, `🗒`, `◀`, `▶`, `✎`,
  `🔎`) — jetzt mit `aria-label`. Stepper melden „Score erhöhen" statt „＋", der Wert ist
  `aria-live`, die Zahlenraster des Loch-Abschlusses nennen „Score 5 – Bogey" bzw. „2 Putts", und
  Auswahlfelder in Spielmodus und Rundeneditor tragen ihre Beschriftung zusätzlich als
  `aria-label`. Hilft auch der Sprachsteuerung — auf der Runde die einzige Bedienung, die ohne
  Handschuhausziehen funktioniert.

- **v2.52.0 · 2026-08-12** — **Rundeneingabe in SPIELREIHENFOLGE statt nach Wichtigkeit.**
  Seit v2.36 waren Spielmodus und Rundeneditor nach Wichtigkeit sortiert: Score/Putts als Stepper
  oben, darunter die Sternfelder, darunter der WICHTIG-Block. Fachlich richtig, in der Bedienung
  falsch — eingetragen wird, was gerade passiert ist, und dabei lief man die Maske pro Loch
  mehrfach von oben nach unten ab (Tee-Ergebnis ganz unten, Approach in der Mitte, Putt-Felder
  wieder oben). Die Uhr hatte die richtige Abfolge bereits.
  Beide Masken zeigen jetzt **fünf Phasen mit Nummer-Etikett**, in der Reihenfolge des Lochs:
  **① Abschlag** (Tee-Ergebnis · Tee-Schläger) · **② Annäherung** (Approach-Distanz ⭐ ·
  Approach-Schläger ⭐ · Approach-Lage ⭐ · Approach-Fehler ⭐ · Rest zur Fahne) ·
  **③ Kurzes Spiel** (Strafschläge · Shortsided · Bunker · Strafart) · **④ Putten**
  (Länge des 1. Putts ⭐ · 1. Putt ging … · Rest nach 1. Putt) · **⑤ Loch beendet**
  (Score ⭐ · Putts ⭐).
  **Score und Putts stehen bewusst am Ende** — sie stehen erst beim Einlochen fest. Wer nur diese
  beiden führt, scrollt einmal durch; wer alles führt, tippt genau einmal von oben nach unten.
  **Kein Feld ist entfallen und keines hat die Bedeutung gewechselt** — es ist ausschließlich die
  Reihenfolge. `playWichtigHtml()` heißt jetzt `playFormHtml()` und rendert die ganze Maske statt
  nur eines Blocks; die Stepper (Score/Putts/Strafschläge) sind dorthin gewandert, ihre IDs
  (`play_score`/`play_putts`/`play_penN`) und damit `playAdj` bleiben unverändert.
  Im Rundeneditor heißt „Pen." jetzt **„Strafschläge"** (die Abkürzung stand nur da, weil das Feld
  in einer engen Spalte klemmte), und Par-3-Löcher zeigen das Tee-Ergebnis gesperrt statt gar
  nicht — so ist die Phasenfolge auf jedem Loch dieselbe.
  **Technisch:** `playSel/playNum` nehmen eine optionale Zusatzklasse (viertes Argument), damit
  volle Breite explizit gesetzt wird; die alte Regel `.play-quick2 .field:last-child` hätte bei
  gerader Feldzahl das zweispaltige Raster gesprengt.
  **Prüfstand (`tests.js`) nebenbei repariert:** Die Abdeckungs-Sperrklinke erkannte reine
  Funktionen an einem 1500-Zeichen-Fenster ab Funktionsbeginn — unabhängig davon, wo die Funktion
  endet. Bei kurzen Funktionen ragte das Fenster in die FOLGENDEN hinein, und ein `render…` dort
  ließ die reine Funktion als unrein durchfallen. Aufgefallen an `roundKPIs`: drei Zeilen ohne
  Seiteneffekt, vom Prüfstand aber als „nicht mehr vorhanden" gemeldet, weil der Kommentar von
  `sortedRounds` ins Fenster ragte — die Aufräumhilfe hätte also zum Löschen einer noch
  existierenden Zeile geraten. Neu `pureBody()`: klammert bis zur eigenen schließenden Klammer,
  hört als Fangnetz an der nächsten Deklaration auf (sonst lief `selfCheck` über 865 000 Zeichen
  weiter). Damit **343 statt 269 Kandidaten** — 74 reine Funktionen standen bisher gar nicht unter
  Aufsicht. Sperrklinke neu gesetzt (12 entfernt, 59 als Altbestand ergänzt), drei Gegenproben
  sichern den Klassifizierer selbst ab. Ergebnis: 1050 Tests grün, ehrliche Abdeckung 142/343.

- **v2.51.1 · 2026-08-12** — **BUGFIX: „Beenden & speichern" verließ den Spielmodus nicht.**
  Der Vollbildmodus hängt allein an der Body-Klasse `play-mode`. In `playFinish()` nahm nur der
  Abbruchzweig (keine Scores erfasst) sie über `pfClose()` weg — der normale Speicherzweig nicht.
  Die Runde landete also korrekt in `DB.rounds`, aber die Ansicht blieb stehen: von außen sah es
  aus, als würde der Knopf weder speichern noch schließen. Ursache war v1.72, wo ein „doppelter
  `pfClose()`-Aufruf" entfernt wurde — es war einer zu viel.
  Beide Zweige rufen jetzt `PLAY.mapFocus=false; pfRestoreView()`. Damit kommt auch die
  **Ausgangsansicht** zurück: `pfRestoreView()` war dokumentiert („kehrt am Rundenende zur
  Ausgangsansicht zurück"), hatte aber im ganzen File **keine einzige Aufrufstelle**, weshalb
  `_pfVorher` nach jeder Runde stehen blieb. Reihenfolge ist wesentlich — erst `PLAY.active=false`,
  dann `pfRestoreView()`, dann `closeSheet()`: nur so greift dort das `wakeRelease()`, das während
  einer laufenden Runde absichtlich unterdrückt wird, und der Bildschirm darf wieder abschalten.

- **v2.51.0 · 2026-08-12** — **Doku geprüft · Selbstprüfung um drei Logikprüfungen erweitert ·
  zwölf rohe Geo-Zugriffe behoben.**
  **Doku-Stand:** Changelog deckt die aktuelle Version, 163 Einträge. 276 der 788 Funktionen
  stehen nicht im Referenzabschnitt — es sind fast ausschließlich kleine Helfer (`wikiEsc`,
  `lmGet`, `testsFor`), die Selbstprüfung überwacht ohnehin, dass **neue** dazugehören. Kein
  Handlungsbedarf.
  **Selbstprüfung:** Sie fand bisher nur Strukturfehler — Doppelnamen, tote Funktionen, fehlende
  Doku. Die teuersten Fehler dieser Sitzung waren aber **Logikfehler**, und drei davon haben eine
  erkennbare Signatur. Neu: (12) Schreibstellen ohne `stamp()`, (13) eine zweite Gewichtstabelle
  neben `SPIELWEISE`, (14) rohe `geo.holes`-Zugriffe.
  **Prüfung 14 schlug sofort an — mit zwölf echten Funden.** `playBearing`, `greenFMB`,
  `greenDims`, `greenRingFor`, `nearestHole`, `playCaddyHtml` und fünf Stellen der Caddy-Position
  lasen die rohen Punkte. Bei Löchern mit `swap:true` zeigte die Richtung damit aufs Tee statt
  aufs Grün — der Wind wurde spiegelbildlich verrechnet, Front und Back verwechselt. Alle
  umgestellt auf `holeRef()`.
  **Zwei Fehlalarme der neuen Prüfungen selbst korrigiert:** Prüfung 13 meldete die gemeinsame
  Tabelle als Kopie, weil `indexOf` die Erwähnung im Prüfcode fand statt der Deklaration. Eine
  Prüfung, die den Sollzustand meldet, gewöhnt einem das Hinsehen ab.

- **v2.50.1 · 2026-08-12** — **Regel 0a in die Doku aufgenommen: „Caddy und Gameplan sind eine
  Einheit."** Die Verknüpfung war in v2.50 nur *beschrieben* — jetzt steht sie als
  **unverhandelbare Regel** ganz vorn, dort wo auch die Doku-Pflicht und die Namensprüfung
  stehen. Inhalt: Beide Wege (`caddyPlan` einerseits, `STRAT.tee`/`STRAT.nextShot` andererseits)
  beantworten dieselbe Frage und müssen dieselbe Antwort geben; alle Gewichte stehen in
  `SPIELWEISE`; eigene Gewichte in einer der drei Funktionen sind **verboten**. Dazu der
  Hinweis, dass neue Modus-Knöpfe `setCaddyMode()` aufrufen müssen, weil dort die Ziellinie
  verworfen wird.
  **Mit Begründung**, damit die Regel nicht beim ersten Widerspruch weggeräumt wird: `nextShot`
  blieb 45 Versionen lang bei einem einzigen Gewicht, weil die Korrektur aus v2.05 nur in
  `caddyPlan` landete. 7 Prüfungen sichern, dass die Regel in der Doku bleibt — mit Gegenprobe
  belegt: Wird sie entfernt, schlägt der Prüfstand an.

- **v2.50.0 · 2026-08-12** — **Eine Bewertung für Caddy und Gameplan.** Es gab **drei** getrennte:
  `caddyPlan` mit fünf Gewichten je Modus, `STRAT.tee` mit dreien, `STRAT.nextShot` mit **einem**
  — und das eine wirkte nur bei Wasser. Auf einem Loch ohne Strafgebiet waren „sicher", „normal"
  und „offensiv" damit rechnerisch **identisch**, und der Gameplan änderte sich beim Umschalten
  nicht. Genau dieser Fehler war für den Caddy schon in v2.05 behoben worden; `nextShot` hatte die
  Korrektur nie bekommen.
  Neu die Tabelle **`SPIELWEISE`** als einzige Quelle — Lage-Gewichte (Strafgebiet, Sand, Rough),
  Wedge-Bonus, Vorrück-Strafe, Layup-Schwelle und die Caddy-Punkte in einer Zeile je Modus. Alle
  drei Bewertungen lesen daraus; **wer eine Zeile ändert, ändert beide zugleich.** Eine Prüfung
  hält fest, dass keine zweite Gewichtstabelle im Quelltext zurückkehrt.
  **Der Wedge-Bonus ist hergeleitet, nicht geraten:** Die Erwartungstabelle gibt einem 71-m-Rest
  rund 0,075 Schläge Vorsprung vor 98 m — sie nimmt an, näher sei immer besser. Ein **voller**
  Wedge-Schlag ist aber kontrollierbarer als ein Teilschlag. Der Bonus muss diese Verzerrung
  überwinden und liegt deshalb in allen Modi über 0,075. Wirkung an Loch 2 (Par 5, 499 m):
  vorher in jedem Modus „Driver → 3 Wood 209 → LW 68 m", jetzt bei sicher und normal
  „Driver → 7 Wood 179 → GW 98 m" — ein volles Wedge statt eines krummen Rests.

- **v2.49.0 · 2026-08-12** — **BUGFIX gefunden: vertauschte Tee/Grün-Punkte bei `swap` ·
  Moduswechsel wirkt jetzt überall.**
  (1) **Die Ursache der unsinnigen Empfehlung auf Loch 1 Nordplatz.**
  `geo.holes[n].tee` ist der **rohe** Punkt aus dem Import. Bei Löchern mit `swap:true` sind Tee
  und Grün darin vertauscht; `holeRef()` dreht sie zurück, **`STRAT.tee` las aber direkt aus
  `geo`**. Die Schlagfolge startete damit **am Grün** und rechnete Richtung Tee: Der Zielpunkt
  lag 179 m vom falschen Ende — also 95 m vom echten Tee. Heraus kam „7 Wood · 95 m → GW · 179 m".
  Die Schlägerwahl war die ganze Zeit richtig, nur der Startpunkt war falsch. Weil nur Loch 1
  `swap` gesetzt hat, trat der Fehler ausschließlich dort auf. Jetzt: „7 Wood · 174 m weit →
  GW · 100 m Rest".
  (2) **Moduswechsel:** Die Bedingung lautete `PLAY.mapFocus` — Ziellinie und „Plan vom
  Abschlag" wurden nur im **Vollbild** neu gerechnet. Im normalen Spielmodus blieb der alte Plan
  stehen, während der Caddy darunter schon anders rechnete: **zwei Empfehlungen, die sich
  widersprachen**. Jetzt werden Kette und eingebettete Karte bei jedem Umschalten von
  sicher/normal/offensiv neu aufgebaut. Nachgemessen an Loch 5: „3 Wood" bei sicher, „Driver" bei
  normal und offensiv. 9 Prüfungen.

- **v2.48.0 · 2026-08-12** — **Caddy-Plan: Einheiten beschriftet · Plausibilitätssperre ·
  Fehlerprotokoll entrümpelt.**
  (1) **Der Plan zeigte „7 Wood · 95 m" und „GW · 179 m".** Schritt 1 nennt die **Schlagweite**,
  Schritt 2 die **Restdistanz** — beide standen nur als nackte Meterzahl da. Wer das nicht weiß,
  liest die zweite Zahl als Schlägerlänge und hält den Plan für kaputt. Jetzt steht die
  Bedeutung dabei: „170 m weit" und „109 m Rest". Eine Prüfung sichert, dass Weite plus Rest die
  Lochlänge ergibt.
  (2) **Plausibilitätssperre:** Steht kein Schläger über 140 m zur Verfügung, nahm der Rückfall
  `clubs[0]` — das ist nur dann der längste, wenn die Liste sortiert ankommt. Kam sie es nicht,
  empfahl der Caddy einen **Lob Wedge vom Abschlag eines Par 4**. Jetzt wird ausdrücklich der
  längste Schläger gewählt, und Wedges sind vom Abschlag ausgeschlossen, solange es eine
  Alternative gibt.
  (3) **Fehlerprotokoll:** 45 Einträge „catch · Failed to fetch" — nichtssagend und
  beunruhigend. Ein fehlgeschlagener Abruf auf dem Platz ist der **Normalfall** (Funkloch), die
  Daten liegen lokal und der nächste Takt versucht es erneut. Die beiden Stellen sind jetzt
  benannt („Sync: Repo nicht erreichbar") und melden **offline gar nicht mehr**. 8 Prüfungen.

- **v2.47.0 · 2026-08-11** — **Aufwärmblöcke abhakbar · Erfassung ehrlicher.** Beim Aufwärmen
  ließ sich nichts abhaken — die Blöcke waren reine Anzeige. Erfasst wurde stattdessen das
  **Festlegen von Plan oder Abschlagzeit**, und das hat die Quote geschönigt: Man legt die Zeit
  fest und fährt trotzdem ohne einen Ball zum ersten Tee.
  Jetzt sind die Blöcke antippbar (✓/○, abgehakte durchgestrichen), und es gilt **dieselbe Regel
  wie bei Preround und Post Round: ab der Hälfte** zählt es als erledigt. Dazu ein
  Zurücksetzen-Knopf und der Hinweis, wo die Quote zu finden ist.
  **Wo alles zusammenläuft:** Die Vorbereitungsquote steht im Reiter **Training · Fitness**,
  ganz oben — „🤸 Preround 7 von 10 · 🔥 Aufwärmen 9 von 10 · 🧘 Post Round 3 von 10". Auf den
  drei Knöpfen der Heute-Seite erscheint ein ✓, wenn heute bereits erledigt.
  Nebenbei behoben: Der Hinweis im Aufwärmblatt verwies noch auf den Plan „8 Minuten danach",
  den es seit v2.27 nicht mehr gibt — er zeigt jetzt auf das Post-Round-Blatt. 13 Prüfungen.

- **v2.46.0 · 2026-08-11** — **Geschwindigkeit: gemessen statt geraten · Logo verkleinert.**
  Mit 80 Runden, 1200 GPS-Schlägen und 25 Launch-Sitzungen gemessen. Drei Ursachen gefunden,
  alle drei behoben:
  (1) **`renderBag` löste 126.969 `clubNorm`-Aufrufe aus** — für rund 40 verschiedene
  Schlägernamen. Ursache: `clubMeasured` lief 46-mal über **alle** GPS-Schläge und **alle**
  LM-Sitzungen, also 55.000 Durchläufe für 46 Ergebnisse. Neu `shotsProKlasse()` gruppiert
  einmal vor. Dazu ein Speicher für `clubNorm` selbst — die Zuordnung Name→Klasse ist fest und
  **kann nicht veralten**. Ergebnis: **78 → 11 ms**, 5.469 statt 126.969 Aufrufe.
  (2) **`sgVerlauf` kostete 74 der 84 ms des Dashboards.** `sgSummary` lief für jede Runde über
  ein Fenster von fünf — bei 80 Runden 400 Auswertungen für 80 verschiedene. Jetzt wird jede
  Runde einmal ausgewertet und danach nur gemittelt, plus Zwischenspeicher. **84 → 23 ms.**
  Alle Speicher hängen an `crCacheClear()`, das in `persist()` und `renderAll()` läuft — eine
  Stelle, damit beim nächsten Einbau keiner vergessen wird.
  (3) **Logo verkleinert:** 256 statt 512 px als größte Fassung (mehr stellt kein
  Startbildschirm dar), Qualität 72 statt 88. **106 → 43 kB**, Datei um 129 kB kleiner. 13
  Prüfungen, darunter die Gegenprobe, dass die Gruppierung neue Schläge bemerkt.

- **v2.45.0 · 2026-08-11** — **App-Logo eingearbeitet.** Erscheint in der Kopfzeile neben dem
  Namen (ersetzt das ⛳-Zeichen), als Startsymbol beim Installieren und im Manifest.
  **Als WebP eingebettet, nicht als PNG:** dieselbe Darstellung bei einem Fünftel der Größe —
  512 px wären als PNG 418 kB base64, als WebP 81 kB. Die App muss offline vollständig
  funktionieren, jedes Kilobyte liegt dauerhaft im Cache. Die Vorlage mit 1,5 MB hätte die Datei
  fast verdoppelt.
  **Drei Größen** statt einer: 512 fürs Installationssymbol, 192 fürs Manifest, 64 für die
  Kopfzeile — ein 512-px-Bild in einer 26-px-Zeile kostet nur Rechenzeit. Zusammen **106 kB**.
  Außerhalb des Kreises transparent, damit auf dem dunkelgrünen Grund keine weißen Ecken stehen.
  Eigener `apple-touch-icon`-Eintrag, weil **iOS das Manifest fürs Startsymbol nicht auswertet**.
  13 Prüfungen.

- **v2.44.0 · 2026-08-11** — **GPS-Genauigkeit: vier Verbesserungen.** Bisher wurde **jede**
  gemeldete Position verwendet — auch eine mit 25 m Ungenauigkeit. Für die Distanz zum Grün
  verschmerzbar, für die Schlagmessung nicht: Zwei Punkte mit je 5 m Fehler ergeben bei einem
  150-m-Schlag bis zu 10 m Abweichung, und diese Zahl geht in die gelernte Schlägerlänge ein —
  also in jede Caddy-Empfehlung.
  (1) **Filtern:** Über 15 m gemeldeter Ungenauigkeit wird das Setzen von Anfangs- oder Endpunkt
  abgewiesen, mit Hinweis statt stiller Speicherung.
  (2) **Beste aus mehreren:** Beim Setzen wird der genaueste Punkt der letzten 4 Sekunden
  genommen statt des zuletzt gemeldeten.
  (3) **Mitteln im Stillstand:** Liegen mehrere Messungen eng beieinander, wird gemittelt — die
  Streuung ist zufällig, der Mittelwert liegt näher an der Wahrheit. Aus sechs Messungen mit
  ±6 m werden so ±2,4 m. **Bei Bewegung wird nicht gemittelt**, sonst bekäme man eine Position
  von vor Sekunden.
  (4) **Ehrlich rechnen:** `gpsGewicht()` bestimmt, wie stark ein Schlag beim Lernen der
  Schlägerlänge zählt — ±4 m voll, ±20 m ein Drittel, ab ±30 m gar nicht. Die Daten lagen in
  `accA`/`accB` längst vor, wurden aber nie genutzt.
  **Bewusst nicht gebaut:** Kalman-Filter oder Glättung über die Zeit. Beim Golf steht man, geht,
  steht wieder — eine Glättung hinkt beim Losgehen hinterher und schwingt beim Anhalten über,
  also genau in den Momenten, auf die es ankommt. 22 Prüfungen.

- **v2.43.0 · 2026-08-11** — **BUGFIX: Karte beim Nachtragen ließ sich nicht verschieben.**
  `strkDown` brach mit `if(!g) return;` ab, sobald man **nicht** auf einen Schlagpunkt tippte —
  ein Zug auf freier Fläche bewirkte nichts. Zwei Finger zoomten, ein Finger tat gar nichts.
  Beim Nachtragen zoomt man aber zuerst heran und muss dann zur nächsten Stelle der Bahn; ohne
  Verschieben blieb nur Herauszoomen und wieder Hineinzoomen.
  Jetzt zieht **ein Finger auf leerer Fläche die Karte**; auf einem Schlagpunkt bleibt es beim
  Verschieben **des Punktes** — der häufigere Fall darf nicht schwerer werden. Die Bewegung wird
  in Kartenkoordinaten umgerechnet (sonst liefe die Karte bei starkem Zoom unter dem Finger weg)
  und am Rand geklemmt (sonst sähe man leere Fläche und fände nicht zurück). Setzt der zweite
  Finger auf, endet das Ziehen und der Zoom übernimmt — ohne Sprung. 9 Prüfungen.

- **v2.42.0 · 2026-08-11** — **Smash Factor und Swing Speed entstehen automatisch · Verlauf
  detailliert.**
  Beides sind keine Tests im eigentlichen Sinn: Man führt sie nicht durch, man **misst** sie —
  und jede R10-Sitzung IST bereits diese Messung. `lmTestsSync()` erzeugt jetzt **je Sitzungstag
  einen Eintrag**, beim Import, beim Öffnen der Tests-Seite und beim Ab- oder Zuschalten einer
  Sitzung. Der manuelle Übernahme-Knopf ist entfallen — er bot etwas an, was ohnehin geschieht.
  **Vier Regeln:** Nur sichtbare Sitzungen (abgewählte verschwinden auch aus dem Testverlauf);
  mindestens 5 Schläge je Schläger; ab 8 Messungen getrimmtes Mittel; und **von Hand erfasste
  Einträge werden nie überschrieben** — wer einen Test bewusst eingetragen hat, soll ihn nicht
  verändert wiederfinden. Erzeugte Einträge sind als „automatisch" markiert. Swing Speed zählt
  **nur Driver-Schläge**: über alle Schläger gemittelt sänke der Wert mit jedem Wedge.
  **Der Verlauf** zeigt je Eintrag nicht mehr nur Datum, Werte und Summe, sondern die
  **Veränderung zur Summe davor** und **jeden Einzelwert mit seiner eigenen Veränderung** — „+3
  gesamt" sagt nicht, ob es am Driver oder am Wedge lag, und genau danach richtet sich das
  nächste Training. Automatische Einträge sind grau hinterlegt. 22 Prüfungen.

- **v2.41.0 · 2026-08-11** — **BUGFIX: Bearbeitete Runden wurden vom Sync überschrieben ·
  Dashboard neu geordnet.**
  (1) **Der Datenverlust:** `_mergeArr` entscheidet über `_mergeTs` (`updated`/`editedAt`).
  Notizen, Schläger und Schwunganalysen setzten diesen Stempel — **Runden, Turniere und Tests
  aber nicht**. Ohne Stempel fällt der Merge auf „der vollständigere Eintrag gewinnt" zurück, und
  die Repo-Fassung ist nach einer Runde im Spielmodus fast immer umfangreicher (Wetter, Lagen,
  Puttlängen). Die eigene Korrektur verlor. Neu `stamp(obj)` an **allen** Schreibstellen; eine
  Prüfung hält fest, dass keine vergessen wird. Mit Gegenprobe belegt: ohne Stempel geht die
  Korrektur verloren, mit Stempel bleibt sie.
  (2) **Dashboard:** Aus 13 Blöcken, 10 Karten und 4 Verlaufsdiagrammen — fünf bis sechs
  Bildschirme — wurden vier sichtbare Abschnitte in der Reihenfolge der Fragen, die man stellt:
  **wo stehe ich** (Ziel und Tempo), **was war zuletzt** (letzte Runde), **woran arbeiten**
  (eine Empfehlung statt dreier Blöcke), dann Strokes Gained. Alle Kurven, die Putt-Diagnose und
  der Top-Fokus wandern in einen Aufklappbereich „📈 Verläufe und Details" — sie beantworteten
  dieselbe Frage mehrfach und drängten das Wesentliche nach unten. 14 Prüfungen sichern die
  Reihenfolge ab.

- **v2.40.0 · 2026-08-11** — **Testverlauf mit Substanz statt Zahlenliste.** Der Verlauf zeigte
  Datum, Einzelwerte, Summe — damit sah man nicht, was die Frage beantwortet, mit der man
  hinschaut: Werde ich besser, wie schnell, und **wo genau**? Alles drei steckte bereits in den
  Daten.
  Neu: **Kurve** mit eingezeichneten Stufenschwellen (erst ab 3 Einträgen — zwei Punkte ergeben
  immer eine Gerade und suggerieren einen Trend, den es nicht gibt). **Kennzahlen:** aktueller
  Wert mit Bestwert-Markierung, Veränderung gegenüber den letzten fünf Einträgen, Bestwert und
  typischer Wert, **Tempo je Monat** aus linearer Regression (ab 4 Einträgen), und die
  HCP-Entwicklung seit dem ersten Eintrag.
  **Der wichtigste Teil: „Was sich verändert hat"** — `testFelderDelta()` vergleicht die
  Einzelwerte und zeigt, wo zugelegt und wo verloren wurde. „Gesamt +3" sagt nicht, ob es an der
  1,5-m-Distanz lag; genau danach richtet sich aber das nächste Training. Verglichen wird gegen
  den Schnitt der bis zu drei Vorgänger, nicht gegen den einen davor — ein einzelner schwacher
  Tag ließe sonst jede Veränderung als Fortschritt erscheinen. Unterschiede unter einem halben
  Punkt werden weggelassen.
  Zusätzlich ein Hinweis zur **Streuung**: Liegt sie über der Veränderung eines halben Jahres,
  steht dort ausdrücklich, dass einzelne Einträge wenig sagen und nur der Trend zählt.
  Alles richtungsabhängig — bei Tests, wo weniger besser ist, ist der Bestwert das Minimum.
  17 Prüfungen.

- **v2.39.0 · 2026-08-11** — **Testbewertung fein statt in fünf Kästchen.** Bei der
  Kurzputt-Präzision (Stufen 22/30/38/44/48) ergaben **30 und 37 Punkte dieselbe Stufe**
  „HCP 15" — sieben Punkte Unterschied, gleiche Anzeige. Gleichzeitig sprang 29 auf 30 eine
  ganze Kategorie: die Kehrseite derselben Medaille.
  Neu **`benchHcp()`** interpoliert zwischen den Stufen — 34 Punkte ergeben „HCP 11,5" statt
  „HCP 15", und der Wert bewegt sich bei jedem Fortschritt. **`benchRest()`** nennt zusätzlich
  den Weg zur nächsten Stufe **in der Einheit des Tests**: „+4 Pkt bis HCP 8". Das ist die
  handlungsleitende Angabe — sie sagt, was zu tun ist, nicht nur wo man steht. Beides erscheint
  im Testdetail; die Liste zeigt statt der Stufe ebenfalls den feinen Wert.
  **Drei bewusste Grenzen:** Über Scratch hinaus wird **nicht extrapoliert** („HCP −2" wäre frei
  erfunden), nach unten ist bei HCP 36 Schluss, und es wird auf **eine** Nachkommastelle
  gerundet — die Schwellenwerte sind Erfahrungswerte, keine Messungen. Ein Hinweis in der
  Anzeige sagt das ausdrücklich: für den Verlauf zählt die Richtung, nicht die Nachkommastelle.
  Funktioniert in beide Richtungen (mehr ist besser / weniger ist besser). 21 Prüfungen.

- **v2.38.0 · 2026-08-11** — **Vorbereitung wird nachgehalten.** Preround-Stretch, Aufwärmen und
  Post-Round-Stretch werden **automatisch** vermerkt — kein zusätzlicher „Erledigt"-Knopf, denn
  genau den vergisst man auf Loch 1. Wer im Stretch-Blatt **mindestens die Hälfte** der Übungen
  abhakt, hat es gemacht; beim Aufwärmen zählt das Festlegen des Ablaufs, weil es dort keine
  Haken gibt. Auf den drei Knöpfen erscheint ein ✓, wenn heute schon erledigt.
  Im Reiter **Fitness** die Quote über die letzten Rundentage: „7 von 10 · 70 %".
  **Vier bewusste Entscheidungen:** (1) An den **Tag** gebunden, nicht an die Runde — die drei
  Dinge passieren Stunden auseinander. (2) Nur **Rundentage** werden gezählt; an spielfreien
  Tagen ist „nicht gedehnt" keine Information. (3) **Dehnen nach einer späten Runde zählt auch
  am Folgetag** — sonst wäre die Quote systematisch zu niedrig und bestrafte genau das, was man
  fördern will. (4) **Kein Serienzähler und keine Verknüpfung mit dem Score:** Eine Kette bricht
  beim ersten verpassten Tag zusammen, und ein Score-Vergleich wäre bei zwei bis drei Runden im
  Monat statistisch wertlos. Unter 3 Rundentagen wird gar keine Quote gezeigt.

- **v2.37.0 · 2026-08-11** — **Smash Factor aus dem Launch Monitor übernehmen.** Der Test hat
  elf Schlägerfelder — und genau diese Werte liegen nach jedem R10-Import bereits vor. Elf Zahlen
  abzutippen ist Fleißarbeit mit Fehlerrisiko: Ein Vertipper erzeugt im Verlauf einen Sprung, den
  es nie gab.
  Neu `smashAusLM()` und ein Knopf **„Aus dem Launch Monitor übernehmen"** in der Testmaske. Die
  Zuordnung läuft über `clubNorm` — der R10 schreibt „7 Iron", das Testfeld heißt „7-Eisen".
  Mehrfachfelder wie „5W/7W" prüfen beide Varianten und nehmen die mit mehr Messungen.
  **Drei bewusste Grenzen:** Mindestens **5 Schläge** je Schläger, sonst gibt es keinen Wert —
  aus drei Schlägen einen Testwert zu bilden hieße, Tagesform als Messung auszugeben. Ab **8
  Messungen getrimmtes Mittel**, damit ein Fersentreffer den Schnitt nicht bestimmt. Und die
  Werte werden nur **eingetragen, nicht gespeichert**: Ob eine Sitzung einen Testeintrag wert
  ist, entscheidet der Spieler. Abgewählte Sitzungen aus dem Launch-Reiter bleiben außen vor.
  Der Knopf erscheint nur, wenn Daten vorliegen. 13 Prüfungen.

- **v2.36.1 · 2026-08-11** — **Erklärtext zur 1.-Putt-Länge entfernt** (er stand dauerhaft in
  der Eingabemaske und kostete dort Platz; die Bedeutung ist inzwischen bekannt).
  **Feld „Platz zwischen Ball und Fahne" umbenannt in „Shortsided"** — in PWA und Uhr. Der
  Fachbegriff ist hier vertretbar, weil die **Auswahlwerte** beschreiben, was gemeint ist
  („Viel Platz zur Fahne" / „Wenig Platz — Fahne nah am Rand"): Wer den Begriff nicht kennt,
  versteht ihn beim Aufklappen. Der Feldname darf kurz sein, solange die Auswahl eindeutig ist.

- **v2.36.0 · 2026-08-11** — **„Was jetzt messen?" ganz oben auf der Tests-Seite.** Die Seite
  listete Kategorien alphabetisch — man suchte sich selbst etwas aus, meist das, was man ohnehin
  gern macht. Ein Test lohnt sich aber dort, wo auf der **Runde** Schläge verlorengehen.
  Neu `testEmpfehlung()` mit drei gewichteten Kriterien: **Strokes Gained** (bis 100 Punkte, die
  teuerste Kategorie bekommt das volle Gewicht), **Messlage** (nie gemessen 60, überfällig bis
  40), **Zielniveau** (30, wenn der letzte Wert die Zielstufe verfehlt). Jede Empfehlung nennt
  ihre Gründe im Klartext.
  Kern ist die Tabelle **`SG_ZU_TESTKAT`** — die Brücke zwischen Strokes Gained und den
  Testkategorien. Beide Seiten hatten die Daten, sie sprachen nur nicht miteinander. Eine Prüfung
  gleicht ab, dass alle zugeordneten Kategorien wirklich existieren; ein Tippfehler ließe die
  Zuordnung sonst still ins Leere laufen.
  An deinen Daten: Putten kostet −4,92 je Runde, entsprechend stehen 3m-Putts, Lag Putting und
  Kurzputt-Präzision auf den ersten drei Plätzen. Ohne SG-Auswertung sagt die Tafel das
  ausdrücklich, statt Genauigkeit vorzutäuschen. 15 Prüfungen.

- **v2.35.0 · 2026-08-11** — **Vorgabenstand · Platz zur Fahne · Hinweis nach großem Loch ·
  beschriftete Wetterzeilen.**
  (1) **`playVorgabe()`** zeigt im Spielmodus den Stand gegenüber der Spielvorgabe:
  „14 Pkt · Puffer +2 · hochgerechnet 34 · hier 1 Vorgabeschlag". Das ist die Zahl, nach der in
  einem deutschen Turnier entschieden wird — „+7 brutto" ignoriert die eigenen Vorgabeschläge.
  Gerechnet wie in `computeRound`, damit beide dasselbe sagen; bei 9 Löchern werden die SI der
  gespielten Seite neu gerankt. Hochrechnung erst ab 3 Löchern.
  (2) **Neues Feld „Platz zwischen Ball und Fahne"** (`kurzseitig`). Der größte einzelne
  Score-Verlust bei mittleren Handicaps ist nicht das verfehlte Grün, sondern die falsche Seite
  davon. **Bewusst ohne Fachbegriff:** „short-sided" oder „kurzseitig" versteht kaum jemand —
  die Auswahl beschreibt, was man sieht („Wenig Platz — Fahne nah am Rand").
  (3) **`playRueckschlag()`**: Nach einem Doppelbogey ist das nächste Loch statistisch das
  gefährlichste. Eine Zeile, keine Warnfarbe, **abschaltbar** — ein Hinweis, den man nicht
  abstellen kann, wirkt bevormundend.
  (4) **Wetterzeilen beschriftet:** „Uhr · Temp · Wind · aus · Böen · Regen" jeweils am
  Zeilenanfang statt einer Legende darunter. Eine Legende zwingt zum Hin- und Herspringen — man
  sieht „21" und muss unten nachsehen, ob km/h oder Prozent gemeint sind.
  Auf der Uhr ebenfalls: neues Feld, und **Score und Putts stehen jetzt ganz unten** auf Seite 2,
  weil sie in der Reihenfolge des Spiels zuletzt entstehen. 22 Prüfungen.

- **v2.34.0 · 2026-08-11** — **Zwischenspeicher für abgeleitete Rundenwerte.** Gemessen: Ein
  Aufbau des Dashboards rief `computeRound` **18-mal** auf — bei 12 Runden. Die Werte einer
  abgeschlossenen Runde ändern sich aber nicht, solange die Runde unverändert bleibt. Neu ein
  Speicher mit **inhaltsbasiertem Schlüssel** (id plus Änderungsstempel, ersatzweise die
  JSON-Länge): 12 echte Berechnungen beim ersten Aufbau, **0 beim zweiten**, und nach einer
  Änderung an einer Runde genau **eine** Neuberechnung. Geleert wird in `persist()` und
  `renderAll()` — der zweite ist der wichtige, weil er auf jede der zwölf Stellen folgt, an denen
  `DB` ersetzt wird.
  **Ein erster Versuch, auch `sortedRounds` zu speichern, wurde verworfen:** Er lieferte veraltete
  Listen, sobald `DB.rounds` direkt geändert wurde — vier Prüfungen schlugen fehl. Ein
  Zwischenspeicher, dessen Gültigkeit von der Disziplin des Aufrufers abhängt, zeigt **still
  falsche Zahlen** und ist damit schlimmer als gar keiner. Die Sortierung kostet ohnehin fast
  nichts. Eine Prüfung hält fest, dass sie ohne Speicher bleibt. 10 Prüfungen.

- **v2.33.0 · 2026-08-11** — **Launch: mehrere Sitzungen zusammen auswerten.** Bisher ließ sich
  genau EINE Sitzung wählen. Für „wie schlage ich das Eisen 7 zurzeit?" ist das zu wenig — eine
  Range-Sitzung hat oft nur 10 bis 15 Schläge, und daraus einen Streukreis zu bilden heißt,
  **Tagesform für Können zu halten**.
  Jetzt umgekehrt gedacht: **Alle Sitzungen sind Grundlage, einzelne lassen sich abwählen.**
  Ein Tipp blendet eine aus (durchgestrichen dargestellt), ein zweiter wieder ein; „alle
  einblenden" setzt zurück. Der häufigste Fall braucht damit keinen einzigen Klick. Nützlich
  zum Ausblenden: ein Tag mit neuem Schläger, kranker Hand oder Sturm.
  `lmAktiveShots(club)` ist die **eine** Stelle, aus der Auswertung, Streuung und Verlauf ihre
  Daten ziehen — so können sie nicht auseinanderlaufen. Die Überschrift nennt jetzt den
  **Zeitraum** statt eines einzelnen Datums, sonst hält man die Zahlen für die eines Tages.
  Nachgemessen: 37 Schläge über drei Sitzungen ergeben 133,8 m Carry-Schnitt; ohne die schwache
  Sitzung 139,6 m aus 26 Schlägen. 12 Prüfungen.

- **v2.32.0 · 2026-08-11** — **CSV-Import: MIME-Filter ganz entfernt.** Die Erweiterung der
  Typliste in v2.31 reichte nicht — manche Android-Dateiwähler werten Endungen gar nicht aus und
  lassen alles ausgegraut, was nicht exakt als `text/csv` gemeldet wird. Die drei Dateifelder
  (Launch-Import, Bag-R10, Streuungs-Import) haben jetzt **kein `accept` mehr**: Jede Datei ist
  wählbar. Stattdessen prüft `lmImport` den **Inhalt** — enthält die erste Zeile kein Komma,
  Semikolon oder Tabulator, kommt die Meldung „Keine Tabellendatei erkannt" samt Hinweis, aus
  welcher App als CSV zu exportieren ist. Das ist ohnehin die verlässlichere Prüfung: Eine Datei
  kann jeden Typ melden und trotzdem etwas anderes enthalten. Mit einer echten Garmin-Datei und
  einer Falscheingabe durchgespielt. 9 Prüfungen.

- **v2.31.0 · 2026-08-11** — **CSV-Import auf dem Handy · Wetter für 6 Stunden · Heute-Seite
  nach Tagesablauf.**
  (1) **BUGFIX CSV-Import:** Die Dateifelder filterten auf `.csv,text/csv`. Android meldet
  heruntergeladene CSV-Dateien aber je nach Herkunft als `application/octet-stream` oder
  `application/vnd.ms-excel` — dann sind sie im Dateiwähler **ausgegraut und nicht auswählbar**.
  Alle drei Felder (Launch-Import, Bag-R10, Streuungs-Import) akzeptieren jetzt die gängigen
  Varianten.
  (2) **Wetter:** Bisher wurde nur der Momentanwert geholt. Für „hält der Wind die nächsten vier
  Stunden?" war das nutzlos — eine Runde dauert vier bis fünf Stunden, die Entscheidung fällt
  vorher. Neu `wxStunden()` / `wxStundenHtml(6)`: **sechs Stunden** als Spalten mit Temperatur,
  Wind, Richtung und Regenrisiko. Regenstunden ab 30 % blau hinterlegt, **Böen nur, wenn sie den
  mittleren Wind um mindestens 10 km/h übersteigen** — sonst steht dort eine Zahl, die nichts
  unterscheidet.
  (3) **Heute-Seite nach ZEITPUNKT gegliedert** statt nach Art der Sache. Das Wetter steht
  zuoberst (es trägt die erste Entscheidung des Tages). Dann **„Vor der Runde"** mit Preround,
  Aufwärmen und Spielmodus, dann **„Nach der Runde"** mit Post Round Stretch — der stand vorher
  VOR dem Spielmodus, also vor dem, wonach man überhaupt erst dehnt. **„Fällige Tests" und
  „Trainingsfokus" entfallen hier** — beides ist Trainingsplanung und steht seit v2.24 im
  Training bzw. im Dashboard, dort nach Wirkung sortiert. 19 Prüfungen.

- **v2.30.1 · 2026-08-11** — **Knopfbeschriftungen gekürzt** auf „🤸 Preround Stretch" und
  „🧘 Post Round Stretch". Die erklärenden Zusätze („volles Malaska-Programm", „nach der Runde")
  entfallen — was dahintersteckt, steht im Blatt selbst. Überschriften der Blätter und die drei
  Querverweise in den Aufwärmplänen mitgezogen, damit Knopf und Ansicht dieselbe Bezeichnung
  tragen.

- **v2.30.0 · 2026-08-11** — **Malaska-Video direkt in der App abspielbar.** Statt eines Links
  eine Abspielfläche am Ende des Pre-Round-Blatts. **Das iframe entsteht erst beim Antippen** —
  fest eingebettet würde es bei JEDEM Öffnen YouTube kontaktieren, auch wenn man nur die Liste
  abhaken will: Ladezeit, Daten und ein Fremdanbieter in der Seite ohne Not.
  `youtube-nocookie.com` statt `youtube.com`: dieselbe Wiedergabe, aber ohne Werbe-Cookies,
  solange nicht abgespielt wird. Ohne Verbindung wird das Antippen abgefangen und gemeldet
  statt eine leere Fläche zu zeigen; ein Ausweichlink öffnet die YouTube-App. Der Hinweis, dass
  das Video **als einziger Teil der App nicht offline verfügbar** ist, bleibt stehen.
  8 Prüfungen, darunter die Gegenprobe, dass im Blatt selbst kein iframe steht.

- **v2.29.0 · 2026-08-11** — **Schlägerwahl im Aufwärmen an die eigene Bag angepasst.**
  Chips laufen jetzt über das **Eisen 9** statt Eisen 8 — das deckt sich mit der
  Wissensdatenbank („Bester Chip-Schläger für mich ist das Eisen 9, SW ist Fallback") und mit
  dem Übungsfokus „Längenkontrolle Chips Eisen 9". Eingespielt wird auf der Range mit
  **Eisen 7 und Eisen 5** statt 8 und 6. Vier Textstellen in den drei Plänen geändert.
  Fünf Prüfungen halten das fest — sonst übt man beim Aufwärmen mit Schlägern, die auf der
  Runde in dieser Rolle gar nicht vorkommen.

- **v2.28.0 · 2026-08-11** — **Übungen wortgetreu zur Wissensdatenbank · Malaska-Video verlinkt.**
  Die Beschreibungen im Pre- und Post-Round-Programm sind gegen den Artikel „Dehnprogramm &
  Malaska-Routine" (18 Übungen) abgeglichen und korrigiert; die gefundenen Fehler stehen als
  Warnung im Code, damit sie nicht zurückkehren — allen voran der **Golfer's Grip**, der
  fälschlich „beide Hände an den Schläger" lautete. Tatsächlich greifen die Hände **ineinander,
  ohne Schläger**; da Arm Circles und Shoulder Stretch „mit Golfers Grip" ausgeführt werden,
  hätte ein Schläger in den Händen eine völlig andere Übung ergeben.
  Neu am **Ende** des Pre-Round-Blatts der Verweis auf **Mike Malaskas Video „A Better Way To
  Warm Up"** — die Vorlage des Programms. Bewusst ans Ende: Wer die Übungen kennt, arbeitet die
  Liste ab; wer eine Ausführung nachschlagen will, kommt dort ohnehin an. Es ist **derselbe
  Link wie in der Wissensdatenbank** — zwei Quellen liefen früher oder später auseinander. Mit
  ausdrücklichem Hinweis, dass das Video **Netz braucht** und nicht Teil des Offline-Vorrats ist;
  die App ist sonst vollständig offline-tauglich. 5 Prüfungen.

- **v2.27.0 · 2026-08-11** — **Aufwärmen ohne Dehnteil · neuer „Post Round Stretch".**
  Die Körperblöcke sind aus allen Aufwärmplänen entfallen — sie liefen doppelt, seit es „Pre
  Round Stretch" gibt. Die Pläne heißen jetzt entsprechend **9 / 21 / 39 Minuten** statt
  12 / 25 / 45 und enthalten nur noch Bälle, Kurzspiel und Putt-Speed. Eine Prüfung stellt
  sicher, dass die angegebene Dauer zur Summe der Blöcke passt — sonst rechnet der Zeitplan
  rückwärts an der Wirklichkeit vorbei.
  **Neu `POST_ROUND` mit 12 Übungen** und eigenem Knopf unter dem Aufwärmen. Grundlage sind die
  **sechs statischen Übungen aus dem Malaska-Programm** (Wissensdatenbank, „Dehnprogramm &
  Malaska-Routine"). Der Artikel „Nachbereitung der Golfrunde" verlangt 15 Minuten am Platz —
  dafür reichen sechs nicht, und drei golfspezifische Belastungen fehlten darin. **Ergänzt und
  als solche gekennzeichnet:** Hüftbeuger (verkürzt durch vier Stunden Gehen; bleibt er kurz,
  kippt das Becken und die Rotation im Rückschwung leidet), Latissimus und Brustöffner (leisten
  die Rotationsarbeit), Gesäß, Waden und Nacken. Die Herkunft steht je Übung dabei, damit
  erkennbar bleibt, was aus der Quelle stammt und was nicht.
  Dazu **12 weitere Skizzen** (`POST_SVG`), gleiche Machart: gezeichnet, offline, ohne externen
  Verweis. 13 Prüfungen.

- **v2.26.0 · 2026-08-11** — **Aufwärmen aus dem Dashboard entfernt · Übungsskizzen ergänzt.**
  Der geführte Ablauf und die Malaska-Liste standen am Anfang des Dashboards und verdrängten
  dort die Auswertung. Beides ist seit v2.25 über zwei Knöpfe ganz oben auf der Heute-Seite
  erreichbar — **zwei Momente, zwei Seiten**: vorbereiten vor der Runde, auswerten danach.
  Neu **`MALASKA_SVG`** — für jede der 13 Übungen eine schematische Strichfigur neben dem Text.
  **Was sie sind:** Erinnerungshilfen für Körperhaltung und Bewegungsrichtung. **Was sie nicht
  sind:** eine Anleitung — wer eine Übung zum ersten Mal macht, braucht das Video, dessen Link
  weiterhin bei der Aufwärmroutine steht.
  Gezeichnet statt fotografiert, aus zwei Gründen: Die App läuft **offline** (ein Bild, das erst
  geladen werden muss, ist im Funkloch wertlos), und Fotos wären entweder fremdes Material oder
  Megabytes im Cache. Alle 13 Skizzen zusammen: **rund 10 kB**, ohne einen einzigen externen
  Verweis, über `currentColor` an das Farbschema gebunden. 12 Prüfungen, darunter die Gegenprobe
  auf externe Verweise und die Größenschranke.

- **v2.25.0 · 2026-08-11** — **Aufwärmen ganz nach oben · neuer „Pre Round Stretch".**
  Die Aufwärm-Schaltfläche stand unter „Schnell erfassen" — man fand sie erst nach dem Scrollen,
  obwohl Aufwärmen das Erste ist, was auf dem Platz passiert. Jetzt stehen **zwei** Knöpfe ganz
  oben auf der Heute-Seite, noch vor dem Spielmodus:
  **🤸 Pre Round Stretch** — das **volle Malaska-Programm** mit allen 13 dynamischen Übungen,
  einzeln abhakbar, mit Zähler. Bisher steckten davon nur die ersten sechs als Kurzfassung in
  den Aufwärmplänen. Das Programm ist immer dasselbe, unabhängig davon, wie viel Zeit für Bälle
  bleibt — deshalb ein eigener Ablauf.
  **🔥 Aufwärmen** — unverändert: Bälle, Kurzspiel und Putt-Speed je nach verfügbarer Zeit.
  **Die statischen Dehnungen bleiben beim Cool-down nach der Runde.** Statisches Dehnen vor dem
  Spiel senkt kurzzeitig die Kraftentfaltung — genau das Gegenteil dessen, was am ersten Tee
  gebraucht wird. Eine Prüfung stellt sicher, dass `MALASKA_STAT` nicht in den Pre-Round-Ablauf
  wandert. 10 Prüfungen, darunter die Reihenfolge der Knöpfe.

- **v2.24.1 · 2026-08-11** — **BUGFIX: „fmtD is not defined" beim Öffnen der Scorekarte.**
  `openRoundCard` rief `fmtD(r.date)` auf — die Funktion heißt `fmtDate`. Der Aufruf stand in
  einem **Template-String** und fiel deshalb weder beim Laden noch bei einer Syntaxprüfung auf,
  sondern erst beim Antippen. Die Formatierer-Familie ist für solche Verwechslungen anfällig,
  weil sich die Namen ähneln: `fmtN`, `fmtDate`, `fmtDT`, `fmtDur`, `fmtBytes` — `fmtD` gibt es
  nicht. Neuer Prüfabschnitt **24ar**: Jeder Name, der aus einem `onclick`/`onchange`-Attribut
  aufgerufen wird, muss definiert sein (131 geprüft), plus eine gezielte Sperre gegen `fmtD`.
  Mit Gegenprobe verifiziert — der wieder eingebaute Fehler wird gemeldet.
  **Zum zweiten Eintrag im Protokoll:** „Failed to fetch" in `freshRepoFetch` ist **kein Fehler
  der App**, sondern ein Netzabbruch (10× zwischen 21:08 und 22:25). Der Abgleich versucht es
  beim nächsten Takt erneut; die Daten bleiben lokal gesichert.

- **v2.24.0 · 2026-08-10** — **Neun Ergänzungen in Analyse und Training — durchgängig
  Einordnung statt neuer Diagramme.** Die Lücken lagen nicht bei den Daten, sondern bei der
  Frage „was bedeutet die Zahl, und was tue ich morgen damit".
  **Analyse:** (1) **Zielabstand** im Dashboard — `hcpGapHtml` war vorhanden, ist jetzt oben
  verankert. (2) **`indexTempo()` / `zielPrognose()`**: lineare Regression über die Index-Punkte
  — „sinkt um 0,4 je Monat, Ziel etwa Februar". Ohne Bewegung in die richtige Richtung
  entsteht **keine** Prognose, sondern der Hinweis, das Training zu ändern statt zu verlängern.
  (3) **`verlaesslich()`** in den Rekorden: Bestwert, gutes Viertel, typisch — ein einmaliger
  Bestwert sagt nichts über das, was im Wettkampf abrufbar ist. (4) **`zielVergleichHtml()`** auf
  der Korrelationsseite: der SG-Vergleich zum Zielhandicap, also die Frage, die die Korrelation
  zu beantworten vorgibt, aber nicht kann. (5) **`stratRueckschau()`**: liefen geplante Löcher
  besser? Mit ausdrücklicher Warnung, dass geplante Löcher meist die schwierigen sind.
  **Training:** (6) **`trainingsEmpfehlung()`** — die Brücke von der Messung zur Übung, sortiert
  nach Wirkung: erst was auf der Runde Schläge kostet (SG), dann die Putt-Ursache, zuletzt Tests
  unter Zielniveau. (7) **`testFaellig()`**: „vor 94 Tagen · fällig" statt nur einem Datum, plus
  Sammelhinweis — ein Test ohne Wiederholung ergibt keinen Verlauf. (8) **`fitnessWirkung()`**:
  Monate mit viel gegen wenig Training, gemessen an der Driver-Länge — als **Beobachtung
  gekennzeichnet, nicht als Beweis**. (9) **`lmKurzfassungHtml()`**: die zwei wichtigsten Befunde
  ganz oben auf der umfangreichsten Seite der App.
  Alle acht neuen Tafeln gegen leere Datenlage geprüft. 19 Prüfungen.

- **v2.23.0 · 2026-08-10** — **„Quality (nach Approach)" aus der Eingabe entfernt.** Das Feld
  misst **dasselbe wie „Rest zur Fahne"**, nur in gröberen Bändern: den Abstand zum Loch nach dem
  Annäherungsschlag. Für die **Position B**, aus der SG Approach entsteht, wurde es **nie**
  herangezogen — dort zählt allein `distToPin`. Es diente ausschließlich als **dritter Rückfall**
  für die Puttlänge, hinter „Länge des 1. Putts" und „Rest zur Fahne". Wer eines von beiden
  pflegt, tippte es doppelt.
  Der Name lud zusätzlich zu falschen Einträgen ein: „Quality" klingt nach einer Bewertung des
  Schlags, ist aber eine Distanz.
  Entfernt aus **beiden** Eingabemasken (Spielmodus und Rundeneditor) sowie die nun unnötige
  Optionsliste. **Der Rückfall in `sgHole` bleibt** — Altrunden tragen das Feld und verlören
  sonst ihr Putt-SG; im Prüfstand an einer Altrunde nachgewiesen. Auf der Uhr war es bereits mit
  v2.20 entfallen. 7 Prüfungen, darunter die Gegenprobe, dass die Rechnung ohne jede der drei
  Quellen als unvollständig meldet statt eine Zahl zu erfinden.

- **v2.22.0 · 2026-08-10** — **Putt-Diagnose: zwei neue Felder und eine Auswertung, die eine
  Trainingsentscheidung liefert.** Bei Approaches gibt es `apprMiss` seit langem — beim Putten
  fehlte die Entsprechung, obwohl dort die größte Lücke liegt (SG Putten −4,73).
  **`puttMiss`** („1. Putt ging …"): Überwiegend kurz heißt Längenkontrolle oder zu zaghaft;
  systematisch eine Seite heißt Startlinie oder Aim-Point. Zwei völlig verschiedene Übungen.
  **`puttRest`** („Rest nach 1. Putt"): trennt Dreiputts nach Ursache — langer Rest = Lag-Problem,
  kurzer Rest = Kurzputt-Problem. Ohne dieses Feld nicht unterscheidbar; genau deshalb sind es
  zwei Felder und nicht eines.
  Neu `puttDiagnose()` mit Tafel im Dashboard: Verteilung kurz/lang/links/rechts, ein Befund im
  Klartext samt Übungsempfehlung und die Aufteilung der Dreiputts. **Unter 10 erfassten Putts
  wird bewusst KEINE Aussage getroffen** — drei Putts wären Rauschen mit dem Aussehen von
  Erkenntnis; stattdessen steht dort, wie viele noch fehlen. Auf der Uhr ebenfalls eingebaut
  (siehe MainActivity.kt), direkt unter der Puttlänge. 12 Prüfungen.

- **v2.20.0 · 2026-08-10** — **„1.-Putt-Distanz" war missverständlich beschriftet.** Gemeint ist
  die **Länge des ersten Putts** — der Abstand zum Loch, wenn man das Grün betritt —, nicht der
  Rest danach. Genau daraus berechnet `sgHole` das Putt-Ergebnis
  (`lookup(distanz,"green") − putts`). Wer den Rest nach dem Putt einträgt, bekommt systematisch
  stark negatives Putt-SG: Aus 0,5 m zwei Putts sind fast ein ganzer Schlag Verlust — je Loch.
  Feld umbenannt in **„Länge des 1. Putts ⭐"**, dazu ein erklärender Hinweis unter den
  SG-Pflichtfeldern. **Auf der Uhr ist die Quality-Eingabe entfallen** (siehe MainActivity.kt):
  Sie trug keine eigene Information, sondern diente nur als dritter Rückfall für dieselbe Größe —
  auf einem Loch mit Grüntreffer ist es dieselbe Zahl, zweimal eingetippt. Das Feld selbst bleibt
  im Datenmodell, damit Altrunden und der Rückfall weiter funktionieren.

- **v2.19.0 · 2026-08-10** — **Höhenraster verbessert · Prüfstand meldete „bestanden" für
  Gruppen, die nie liefen.**
  **Raster:** (1) `ELEV` lebte nur im Arbeitsspeicher — nach jedem Start war alles weg, auf dem
  Platz im Funkloch gab es gar keine Höhen. Jetzt in `localStorage`, gedeckelt auf 4000 Punkte,
  bewusst nicht im Sync-JSON. (2) Der Schlüssel verlangte einen exakten Treffer auf ~11 m; beim
  Gehen traf er praktisch nie und die Korrektur fiel **still** aus. Jetzt Interpolation aus den
  **vier nächsten** Punkten (inverse Distanzgewichtung, 60 m Umkreis) — nur vier, weil alle
  Punkte im Umkreis den Wert zu entfernten Stützpunkten hinziehen. Abweichung gegen einen
  linearen Anstieg unter 12 cm, die Gesamtdifferenz über die Bahn exakt. (3) `elevPrefetchHole()`
  legt beim Lochwechsel ein Profil entlang der Spiellinie an (alle ~25 m) — vorher waren nur Tee,
  Grün und Standort bekannt, ein Tal auf halber Bahn unsichtbar. `elevSave()` schreibt das Raster gebündelt nach jedem Abruf zurück. (4) Über 60 m liefert `elevGet`
  bewusst `null` statt eines erfundenen Werts.
  **Prüfstand:** Fehlt ein Name in der Übergabeliste, liefert `G(...)` `undefined`; die Gruppe
  steht hinter einem `typeof`-Wächter und wird kommentarlos übersprungen — gemeldet wird trotzdem
  Erfolg. So waren **48 Prüfungen stillgelegt**. Nach dem Nachtragen: 607 → 655. Neuer Abschnitt
  **24al** prüft das selbst und ist damit die wichtigste Prüfung der Datei — sie verhindert, dass
  alle anderen lügen.

- **v2.18.0 · 2026-08-10** — **Höhe, Wind und Regen wirken jetzt auf die SCHLÄGERWAHL.** Bisher
  nur auf die Anzeige — richtige Zahl, falscher Schläger. Neu skaliert `caddyClubs(cond)` die
  Reichweiten mit `condFaktor()` (`f = Distanz / playsLike(Distanz)`, gedeckelt auf ±25 %):
  Ein 150-m-Eisen deckt bergauf keine 150 m Boden mehr ab. Die Geometrie bleibt unverändert, nur
  die Reichweiten schrumpfen oder wachsen. Bei 150 m Rest: eben 6 Eisen, **10 m bergauf 4 Eisen,
  15 m bergauf 5 Wood**, 10 m bergab wieder 6 Eisen. Der Caddy **weist den Einfluss aus**
  („spielt wie 167 m · ⛰ bergauf 15 m · 🌬 Gegenwind 4 m/s"), sonst wäre die Empfehlung nicht
  nachvollziehbar. **Zur Höhenquelle:** nicht OSM (dort gibt es keine brauchbaren Höhen), sondern
  das Open-Meteo-Höhenraster; unter 1,5 m gilt als Rauschen, ein 3 m erhöhtes Grün erfasst das
  Raster nicht. `caddyClubs()` ohne Argument bleibt unverändert. 12 Prüfungen.

- **v2.17.0 · 2026-08-10** — **R10-Messungen kamen nie an: englische gegen deutsche
  Schlägernamen.** `clubMeasured` verglich die Namen **exakt**. Der Garmin R10 exportiert
  „7 Iron", die Bag steht auf „7-Eisen" — es passte nichts zusammen, die Spalte blieb leer,
  obwohl zahlreiche Messungen vorlagen. Kein Fehler war sichtbar, nur ein Strich.
  Neu `clubNorm(name)`: Loft und Trennzeichen weg, Englisch auf Deutsch, Gattung + Nummer —
  `7 Iron`/`7-Eisen`/`Eisen 7`/`7i` ergeben alle `eisen7`, ebenso `3 Wood`/`3 Holz`/`3W` und
  `Pitching Wedge`/`PW`. Angezeigt wird weiterhin der gepflegte Name. **Verschiedene Schläger
  bleiben getrennt** (7/6 Eisen, 3/5 Holz, PW/SW) — 16 Prüfungen sichern beide Richtungen ab.
  Zusätzlich listet die Schläger-Ansicht **Messungen ohne passenden Schläger** namentlich auf,
  damit eine leere Spalte nicht wieder rätselhaft bleibt.

- **v2.16.0 · 2026-08-10** — **Messwerte als Spalten in der Schlägertabelle.** Neben den
  gepflegten Werten stehen jetzt **⚙ R10** (gemessener Carry aus Launch-Monitor-Sitzungen) und
  **📡 GPS** (gemessene Gesamtlänge aus der Schlagaufnahme auf dem Platz) — bisher gab es sie nur
  in einem separaten Block darunter. `bagMessSpalte()` färbt Abweichungen ab 7 m: rot = kürzer
  als gepflegt, grün = länger; darunter bleibt es neutral, weil es Rauschen ist. **Unterhalb von
  8 Messungen** steht die bisherige Anzahl („n5") statt eines Strichs — sonst hält man die leere
  Spalte für einen Fehler, obwohl sich Daten sammeln. Die Legende erklärt zusätzlich, warum
  getrimmte Mittelwerte verwendet werden: Ein einzelner verzogener Ball verschiebt einen
  einfachen Mittelwert erheblich; der Caddy soll mit dem rechnen, was der Schläger normalerweise
  leistet. 10 Prüfungen.

- **v2.15.0 · 2026-08-10** — **Warum die Schlägerlisten auf Handy und Uhr abweichen.** Geprüft:
  Die Uhr hat **keine eigene Liste** — sie liest `clubDistances` aus denselben synchronisierten
  Daten, und der Runden-Entwurf trägt bewusst keine zweite Liste mit. Die Namen können also nicht
  auseinanderlaufen. **Die Abweichung hat eine andere Ursache:** Die Uhr überspringt beim
  Einlesen jeden Schläger ohne Distanz (`if (carry == null && total == null) continue`) — und
  dieselbe Liste treibt dort auch die Schlägerauswahl beim Schlagtracken. Ein frisch angelegter
  Schläger fehlt auf der Uhr also **vollständig**, bis Carry oder Gesamtlänge eingetragen ist.
  Die Schläger-Ansicht weist jetzt darauf hin und nennt die betroffenen Schläger namentlich,
  damit man den Fehler nicht beim Abgleich sucht. Fünf Prüfungen, davon vier gegen MainActivity.kt.

- **v2.14.0 · 2026-08-10** — **Umbenennen zieht die Historie mit — Uhr, R10 und Runden.**
  Schläger sind **ausschließlich über den Namen** verknüpft, es gibt keine id-Verknüpfung: GPS-
  Schlagaufnahme (Handy und Uhr), Garmin-R10-Sitzungen, Schwunganalysen, Rundendaten
  (`holes[].club`, `.apprClub`, Schlaglisten), die laufende Aufzeichnung und die Auswahllisten
  auf der Uhr — **die Uhr kennt gar keine ids.** Ein Umbenennen hätte deshalb die gesamte
  Historie abgehängt: Der Caddy lernt die Länge nicht mehr, „gepflegt vs. gemessen" zeigt nichts,
  die R10-Streuung ist verloren — und sichtbar wird das erst Wochen später. `clubRename(alt,neu)`
  überträgt jetzt alle Verweise, meldet ihre Zahl und verwirft den Gameplan-Zwischenspeicher
  (er enthält Schlägernamen). 14 Prüfungen über alle Datenquellen.

- **v2.13.0 · 2026-08-10** — **BUGFIX: Neue Schläger verschwanden oder verdoppelten sich.**
  Die Schlägerliste wird über den **Namen** geschlüsselt (`MERGE_KEY.clubDistances = x=>x.club`),
  nicht über die id — die Uhr schreibt Schläger ohne id. Daraus folgten zwei Fehler:
  (1) **Jeder neue Schläger hieß „Neuer Schläger".** Zwei davon verschmolzen beim Abgleich zu
  einem, der zweite war lautlos weg. Neu vergibt `bagFreiName()` sofort einen freien Namen
  („Neuer Schläger", „… 2", „… 3").
  (2) **Umbenennen ist beim Namensschlüssel ein Löschen plus Neuanlegen.** Ohne Grabstein auf den
  ALTEN Namen brachte der nächste Abgleich den Platzhalter aus dem Repo zurück — man legte einen
  Schläger an, benannte ihn in „5 Wood" um, und kurz darauf standen beide in der Liste. Jetzt
  setzt das Umbenennen einen Grabstein auf den alten und hebt den des neuen Namens auf.
  Zusätzlich werden **doppelte Namen abgelehnt** (mit Hinweis) statt sie später verschmelzen zu
  lassen, und ein leerer Name wird zurückgewiesen. Sieben Prüfungen decken den Ablauf ab.

- **v2.12.0 · 2026-08-10** — **Auch Wettkämpfe kamen nach dem Löschen zurück — und sechs weitere
  Listen.** Der Grabstein-Umbau von v2.11 hatte nur die Schläger und wenige andere Stellen
  erfasst; ein Prüflauf über alle Löschungen an synchronisierten Listen fand acht weitere ohne
  Grabstein. Ursache für die Lückenhaftigkeit war die **doppelte Pflege der Schlüssel**: In
  `mergeDB` stand die Formel je Liste, beim Löschen musste man sie von Hand nachbauen — ein
  Grabstein mit falschem Schlüssel greift ins Leere, ohne dass etwas auffällt. Neu die Tabelle
  **`MERGE_KEY`**, die `mergeDB` und `tombDel(bereich, obj)` gemeinsam nutzen. Angebunden:
  Wettkämpfe, Testergebnisse, Plätze (Definition und gespielt), Bucketlist, Notizen (inkl.
  Aufheben des Grabsteins beim Wiederherstellen). Bewusst ohne Grabstein bleiben `tournaments`
  und `seasonGoals` (laufen über `Object.assign`) sowie die Alters-Bereinigung des
  Notiz-Papierkorbs. 21 Prüfungen.

- **v2.11.0 · 2026-08-10** — **BUGFIX: Gelöschte Schläger kamen sofort zurück.** `_mergeArr`
  **vereinigt** lokale und Repo-Liste — ein lokal gelöschter Eintrag fehlt danach nur noch lokal,
  und die Repo-Fassung wird kommentarlos wieder hinzugefügt. Das betraf **alle 13
  synchronisierten Listen**, nicht nur die Schläger. Dieselbe Klasse wie v1.74: das Fehlen einer
  Sache ist keine Information, die ein Merge lesen kann. Neu **Grabsteine**
  (`DB.tomb[bereich][key] = Zeit`, `tombAdd`/`tombClear`/`_mergeTomb`): `_mergeArr` entfernt
  Einträge, deren Grabstein jünger ist als der Eintrag; die Grabsteine werden selbst
  mitsynchronisiert. **`tombClear()` ist dabei genauso wichtig** — Schläger werden über den Namen
  geschlüsselt, ohne Aufheben wäre „5 Wood" nach dem Löschen dauerhaft verbrannt. Angebunden an
  Schläger, Runden, Trainingseinheiten, Launch-Monitor-Sitzungen und Notizen. 13 Prüfungen.

- **v2.10.0 · 2026-08-10** — **Alle 27 Testbeschreibungen neu geschrieben.** Vorher zwischen 28
  und 535 Zeichen — die kürzeste wiederholte nur den Titel. Jetzt einheitlich gegliedert:
  **WOFÜR** (was gemessen wird und warum es Schläge kostet) · **AUFBAU** (Material, Distanzen,
  Markierungen) · **ABLAUF** (Schritt für Schritt, passend zu den echten Eingabefeldern) ·
  **ZÄHLUNG** · **RICHTWERTE** · **FEHLERQUELLE** (was den Test wertlos macht — meist wichtiger
  als der Ablauf). Median 866 statt 296 Zeichen. Die **Richtwerte werden aus `benchmark.levels`
  erzeugt**, nicht von Hand geschrieben: handgeschriebene Werte laufen von der echten
  Bewertungsskala weg, erzeugte können es nicht. Neun Prüfungen sichern Vollständigkeit und
  Übereinstimmung mit der Skala.

- **v2.09.0 · 2026-08-10** — **Unrunde Zahlen und verschobene Unternavigation.**
  (1) In „Gute gegen schlechte Runden" stand „38.888888888888886". Ursache: **`num()` ist ein
  PARSER** (Text → Zahl) und ignoriert ein zweites Argument — `num(v,1)` rundet also nicht.
  Zum Formatieren ist `fmtN(v,d)` da. Beide Stellen korrigiert; Quoten bekommen zusätzlich die
  Einheit „%", weil „38,9" sonst nicht verrät, ob Prozent, Schläge oder Meter gemeint sind. Am
  irreführenden Namen `num()` steht jetzt eine Warnung, und eine Prüfung verhindert die
  Wiederholung.
  (2) **Kopfzeile und Unternavigation klebten beide auf `top:0`** und stapelten sich damit
  übereinander. Sichtbar wurde es im Browser-Vollbild, wo `env(safe-area-inset-top)` wegfällt und
  die Kopfzeile schrumpft. Die Unternavigation klebt jetzt auf `top:var(--hh)`; `syncHeaderH()`
  misst die tatsächliche Kopfhöhe zur Laufzeit (bei Render, `resize`, `orientationchange` und
  `fullscreenchange`) — ein fester Wert wäre je nach Gerät, Schriftgröße und Vollbild wieder
  falsch. z-index der Kopfzeile 20, der Unternavigation 19.

- **v2.08.0 · 2026-08-10** — **Eingabemaske ist reine Eingabe.** Caddy, Lochplan, Distanzanzeige
  (F/M/B) und Schlagaufnahme sind dort vollständig entfallen — alles davon steht im Kartenmodus,
  und zwar besser: die Empfehlung ab der eigenen Position, der Plan fürs Loch, das Aufnahmeband
  mit Schwunglänge. In der Eingabemaske war es eine zweite, schlechtere Ausgabe derselben Daten.
  **Nebeneffekt:** `playInfoHtml()` stößt Monte-Carlo und Geo-Raster an und lief bisher bei
  JEDEM GPS-Tick über `playLiveRefresh()` — auch wenn niemand hinsah. Das entfällt außerhalb des
  Kartenmodus. Der Weg zurück zur Karte („🗺 Vollbild-Karte" ganz oben) bleibt und ist im
  Prüfstand abgesichert.

- **v2.07.1 · 2026-08-10** — **Nachgewiesen: Die Fairwaybreite fließt in die Schlägerwahl ein.**
  Auf Nachfrage überprüft — sie wird nicht als Zahl gemessen, sondern ergibt sich aus der
  Monte-Carlo-Simulation: Jeder Kandidat wird mit der Streuung des Schlägers gestreut, jeder
  Landepunkt über `lieCode` bewertet. Ein enges Fairway heißt automatisch mehr Rough-Treffer,
  höheren Erwartungswert und damit schlechtere Bewertung. Gemessen an einem 380-m-Par-4:
  ±30 m → 95 % Fairway, ±7 m → 30 %, Erwartungswert 4,49 → 4,81. **Der entscheidende Fall:**
  Verengt sich das Fairway erst in der Landezone des Drivers, wählt der Caddy den kürzeren
  Schläger, der davor bleibt (5 Wood statt Driver, 98 % statt 30 % Fairway). Fünf Prüfungen
  halten das fest — samt dem Hinweis, dass das Lageraster `geo.features` liest und **nicht**
  `holes[n].fairway`; diese Verwechslung ließ meinen ersten Prüfversuch fälschlich aussehen,
  als werde die Breite ignoriert.

- **v2.07.0 · 2026-08-10** — **BUGFIX: Distanzringe, Luftbild und Platzdaten ließen sich nicht
  umschalten.** Alle drei sind Teil des SVG und ändern sich erst, wenn die Karte neu **gebaut**
  wird. Die Knöpfe riefen aber nur `playMapTick()` (bewegt lediglich die Positionsmarke) bzw.
  `playMapCtrlsRefresh()` (zeichnet nur die Knopfleiste). Der Zustand kippte also, sichtbar
  änderte sich nichts — die Knöpfe wirkten funktionslos. Neu `playMapRedraw()`: baut die Karte
  neu und aktualisiert die Leiste. Sechs Prüfungen sichern ab, dass kein Umschalter sich wieder
  auf `playMapTick()` verlässt.

- **v2.06.0 · 2026-08-10** — **Der ⤢-Knopf wirkte funktionslos.** Er setzt den Kartenausschnitt
  zurück, damit die Karte wieder automatisch mitwandert. Wer nie von Hand gezoomt oder geschoben
  hat, sieht beim Tippen aber nichts passieren — der Ausschnitt war schon richtig. Jetzt ist der
  Knopf **deaktiviert und ausgegraut**, solange die Automatik ohnehin läuft, und gibt beim
  Tippen eine Rückmeldung („Ausschnitt zurückgesetzt" bzw. „Karte folgt bereits automatisch").
  Ein Knopf, der nichts bewirken kann, soll auch nicht bedienbar aussehen.

- **v2.05.0 · 2026-08-10** — **Spielweise wirkte nur auf Strafgebiete · Adressleiste ausblendbar.**
  (1) Die Modi unterschieden sich **ausschließlich** im Gewicht der Strafquote
  (`safe: es+1,5·pen`, `bal: es+0,5·pen`, `aggr: es`). Auf einem Loch ohne Wasser und Aus war
  `pen` für alle Kandidaten 0 — die drei Bewertungen waren rechnerisch identisch und der
  Umschalter tat sichtbar nichts. Das ist auch fachlich zu eng: „sicher" heißt auf den meisten
  Löchern nicht „Wasser meiden", sondern Bunker und Rough meiden. Jetzt gehen **Sand- und
  Rough-Anteil** mit ein, je Spielweise gewichtet; offensiv straft Strafgebiete weiterhin leicht
  (0,25) — sie ganz zu ignorieren wäre nicht mutig, sondern falsch. Der Erwartungswert selbst
  bleibt modusunabhängig, nur die Risikogewichtung verschiebt sich.
  **Wichtig zum Verständnis:** Bleibt die Empfehlung beim Umschalten gleich, gibt es auf diesem
  Loch kein Risiko, das eine andere Wahl rechtfertigen würde. Das steht jetzt als Hinweis in der
  aufgeklappten Caddy-Anzeige, statt wie ein Fehler zu wirken.
  (2) **⛶-Knopf** in der Kartenleiste: blendet über die Fullscreen-API die Adressleiste aus.
  Ein normaler Tab kann sie nicht dauerhaft verstecken — dauerhaft geht das nur, wenn die App
  zum Startbildschirm hinzugefügt wird (PWA im standalone-Modus).

- **v2.04.0 · 2026-08-10** — **BUGFIX: Scorekarte schließen landete in der Eingabemaske.**
  `closeSheet()` baut seinen History-Eintrag über `history.back()` ab — das löst ein popstate
  aus, und der Handler hielt es für die Zurück-Taste und wechselte in die Eingabe. Neu
  unterscheidet `_popEigen` das selbst ausgelöste Ereignis vom Nutzerdruck; ein Zeitgeber setzt
  die Markierung zurück, falls das popstate ausbleibt und sonst den nächsten echten Zurück-Druck
  schlucken würde. **Nebenbei:** `closeSheet()` gab weiterhin den Wake Lock frei — jedes Blatt
  im Spielmodus (Scorekarte, Details) hätte den Bildschirm einschlafen lassen. Jetzt bleibt er
  während einer laufenden Runde gehalten. Beide Abläufe simuliert: ✕ führt zurück auf die Karte,
  die echte Zurück-Taste weiterhin in die Eingabemaske.

- **v2.03.0 · 2026-08-10** — **Messanzeige im Spielmodus entfernt.** `pfFacts`, `pfDbgRender`,
  `pfDbgToggle`, das Element `#pfDbg`, dessen CSS, der ⓘ-Schalter in der Kartenleiste, der Knopf
  in der Diagnose und die Vorgabe `DB.ui.pfDebug` sind vollständig entfallen. Sie hat ihren Zweck
  erfüllt — sie fand die 56-px-Differenz zwischen `documentElement.clientHeight` und dem
  sichtbaren Bildschirm, an der zwölf Anläufe gescheitert waren — und verdeckte danach nur noch
  die Karte. Die **Versionsanzeige in der Diagnose bleibt**: Sie beantwortet die Frage „läuft
  überhaupt die neue Fassung?", die während der Fehlersuche mehrfach entscheidend war.

- **v2.02.0 · 2026-08-10** — **BUGFIX: Der Caddy baute aus großer Entfernung absurde Ketten.**
  Aus 3,2 km rechnete `_aimBuild()` von der eigenen Position: 3077 m Rest geteilt durch 215 m
  Schlägerlänge ergab **15 Schläge**, und die Karte stapelte vierzehn „Layup"-Beschriftungen
  übereinander. Der „zu weit"-Fall fehlte in der Bedingung für den Startpunkt. Jetzt gilt:
  **zu weit weg = wie am Abschlag planen** — also der Plan FÜRS LOCH mit Par−2 Schlägen, genau
  wie gewünscht. Zweites Sicherheitsnetz: die Schlagzahl ist auf 4 gedeckelt, mehr ist auf keinem
  Loch ein sinnvoller Plan. Nachgewiesen: aus 3,2 km identisch zum Abschlagsplan (Driver → PW),
  auf der Bahn weiterhin ab eigener Position.

- **v2.01.0 · 2026-08-10** — **Zielabstand und Strokes Gained schienen sich zu widersprechen.**
  SG rechnet gegen einen Spieler DEINES Niveaus („langes Spiel +4,04" = besser als ein typischer
  20er), der Zielabstand ist eine Modellrechnung über den Abstand ZWISCHEN zwei Niveaus („13,6
  Schläge") — beim Golf liegt der größte absolute Unterschied im langen Spiel, unabhängig vom
  Spieler. Beides stimmt, die Darstellung machte es nur nicht klar. Die Tafel zeigt jetzt beide
  Spalten nebeneinander („Weg dorthin" / „Bei dir offen") und leitet ab: **⚡ Schnellster Hebel**
  (größtes eigenes Defizit — Aufholen ohne neue Technik) und **🏔 Längste Strecke** (größter
  Modellanteil). Zusätzlich weist das SG-Feld jetzt aus, wenn die Kategorien sich nicht zum
  Gesamtwert addieren — das liegt an unterschiedlich vielen auswertbaren Löchern je Kategorie und
  sah bisher nach einem Rechenfehler aus.

- **v2.00.0 · 2026-08-10** — **BUGFIX „Unexpected end of input": Der Knopf „als Trainingsaufgabe"
  tat nichts.** Der Handler setzte `${JSON.stringify(sgDrillHint(kat))}` in ein `onclick="…"` —
  und JSON.stringify liefert **doppelte** Anführungszeichen. Die beenden das HTML-Attribut
  vorzeitig, der Rest des Handlers wird abgeschnitten, und der Browser meldet einen SyntaxError.
  Im Quelltext sieht dabei alles richtig aus. Behoben an der Wurzel: `taskAdd(kat)` holt den Text
  jetzt selbst aus `sgDrillHint(kat)`, es wird gar kein Text mehr durchgereicht (beide
  Aufrufstellen). **Selbstprüfung Nr. 8b** meldet die Fehlerklasse künftig als Fehler, drei
  Prüfungen im Prüfstand sichern sie ab.

- **v1.99.0 · 2026-08-10** — **KORREKTUR: Index ausschließlich aus Turnieren.** In v1.98 hatte
  ich die Anforderung falsch herum umgesetzt — statt EDS abzuschaffen ließ ich jede erfasste
  Runde für den Index zählen. Richtig ist das Gegenteil: `whsPool()` liest nur noch
  `DB.competitions`, `countHcp` an Runden wird nicht mehr ausgewertet und beim Speichern auf
  `false` gesetzt. Trainingsrunden bewegen den Index nicht — sonst verwässert jede Übungsrunde
  den Wert, der das Turnierniveau abbilden soll. Erklärtext im Wettkampf-Reiter korrigiert,
  fünf Prüfungen sichern es ab.

- **v1.98.0 · 2026-08-10** — **Acht Änderungen von der Runde.**
  Schwunglänge je Schlag (Voll/3-4/Halb/Punch …) — und `clubMeasured()` lernt nur noch aus
  VOLLEN Schwüngen, sonst zöge ein halber Wedge die Schlägerlänge nach unten · „Holed" in der
  1.-Putt-Distanz · **Auto-Loch (GPS) entfernt** · Caddy-Modus direkt auf der Karte umschaltbar ·
  Scorekarte über die Karte und bei jeder gespeicherten Runde · **EDS entfernt** (es werden nur
  Turniere gespielt) · **Regen im Caddy**: kostet Carry (3 %) und nimmt den Auslauf fast ganz
  (25 m → 2,5 m) — zusammen leicht zwei Schlägerlängen, vorher gar nicht berücksichtigt.
  14 neue Prüfungen für den Nässefaktor und seine Wirkung.

- **v1.97.0 · 2026-08-10** — **Scorekarte je Loch — nachvollziehbar, woher die Summe kommt.**
  „Par E" statt „+1" lässt sich nicht prüfen, solange man die Summanden nicht sieht.
  `openPlayCard()` (antippbar über die Par-Anzeige in beiden Modi) zeigt je Loch Par, Länge,
  Score und Differenz. Löcher mit Score aber ohne Par werden gesondert ausgewiesen — sie fehlen
  in der Summe. Unplausible Par/Länge-Kombinationen sind mit ⚠ markiert (Par 3 über 250 m,
  Par 4 unter 180 oder über 500 m, Par 5 unter 400 m), denn die häufigste Ursache einer um
  genau einen Schlag falschen Summe ist ein falsches Par in den Platzdaten, nicht ein falscher
  Score.

- **v1.96.0 · 2026-08-10** — **Absturz beim Wischen auf der Karte behoben.**
  (1) **`Cannot read properties of null (reading 'x')`** in `playMapBind` (59× protokolliert):
  Der Pan-Zweig las `PLAY.mapView.x` — und `mapView` ist zwischen Ansichtswechsel und erstem
  `playMapRender()` kurz `null`, außerdem setzt `playToggleFocusMap()` es bewusst zurück. Wischt
  man genau dann, warf jedes Bewegungsereignis erneut. Jetzt `if(!v) return;` plus eine Prüfung
  auf den vorherigen Zeigerpunkt.
  (2) **Kartenmodus heilt sich selbst:** `pfRender()` setzt `body.play-mode` und die aktive
  Ansicht bei jedem Zeichnen nach. Wird die Ansicht von außen gewechselt, kamen sonst Kopfzeile
  und Navigation zurück und die Karte steckte in einem Kasten.
  (3) **Fehlermeldungen verdecken die Karte nicht mehr:** Wiederholungen desselben Fehlers
  erscheinen nach dem dritten Mal höchstens noch einmal pro Minute. Ein Fehler in einem
  Bewegungsereignis feuert im Dutzend — die Einblendung stand dauerhaft über der Bahn.

- **v1.95.0 · 2026-08-10** — **Sechs Punkte von der Runde.**
  (1) **BUGFIX „E" trotz Schlägen über Par:** Fehlt bei einem Loch `par`, wird die Summe **NaN** —
  und NaN ist weder größer noch kleiner als 0, also erschien „E" (even). Ursache war
  `playContinue`: `Object.assign` übernahm auch NULL-Werte aus dem Entwurf und löschte damit
  par/si/len aus den Platzdaten. Diese drei kommen jetzt immer vom PLATZ, nie vom Entwurf;
  die Rechnung überspringt Löcher ohne Par und zählt sie separat.
  (2) **Schlagaufnahme abbrechen** — ✕ neben „Speichern" im gelben Band.
  (3) **Front/Mitte/Back im Kartenmodus** — kompakt in der Kopfzeile; dort schaut man hin, wenn
  man den Schläger wählt.
  (4) **Orange Linie wandert mit:** Der Zwischenspeicher-Schlüssel enthielt die eigene Position
  nicht, und `_aimBuild` startete immer am Abschlag. Jetzt geht der Standort (auf 15 m gerundet)
  in den Schlüssel ein, und die Kette beginnt ab 30 m vom Tee an der eigenen Position — mit
  entsprechend weniger Restschlägen.
  (5) **Landezone als schiefe Ellipse** statt Kreis: quer zur Spiellinie streut man mehr (`sigL`)
  als in der Länge (`sigD`), und die Ellipse ist in die Schlagrichtung gedreht. Maßstab aus der
  Pixellänge des Schlags geteilt durch seine Meter — stimmt in jeder Zoomstufe und ändert sich
  mit Schläger und Distanz.
  (6) **Bildschirm bleibt an:** Wake Lock hängt jetzt an der laufenden RUNDE statt am GPS —
  er greift auch, wenn die Ortung gerade keine Position liefert.

- **v1.94.0 · 2026-08-10** — **ZWEI Fehler auf der Runde behoben.**
  (1) **`pin is not defined`** in `playInfoHtml` — beim Entfernen der Fahnensteuerung (v1.90)
  blieben in der F/M/B-Anzeige Zugriffe auf `pin` stehen. Die Funktion warf bei JEDEM Zeichnen
  einen ReferenceError, der Spielmodus war unbenutzbar. Block auf Front/Mitte/Back ohne
  Fahnenbezug umgestellt.
  (2) **Caddy-Empfehlung passte sich nicht der Position an.** Die Kurzzeile zeigte
  `playAimChain().legs[0]` — den ersten Schlag der Kette, und die startet immer am ABSCHLAG.
  Es stand dauerhaft „Driver 219 m", egal wo man stand. Neu `playCaddyNow()`: bewertet ab
  `PLAY.here` — am Abschlag `tee()`, bei 8–200 m Rest `approach()`, sonst `nextShot()`.
  Nachgewiesen: 387 m/Driver → 172 m/5-Eisen → 87 m/SW, Restdistanz streng fallend.
  Elf Prüfungen sichern ab, dass Restdistanz UND Schläger mitwandern.

- **v1.93.0 · 2026-08-09** — **Platzbericht für die Turniervorbereitung.** `courseStats()`
  schlüsselt die eigenen Runden nach Platz auf: Problemlöcher, starke Löcher und
  Doppelbogey-Quote je Loch. Der Schnitt über alle Plätze hilft vor der Clubmeisterschaft nicht —
  entscheidend ist, wo man HIER Schläge verliert. `openCourseReport()` mit Platzauswahl, Einstieg
  auf „Heute" neben dem nächsten Turnier. Damit sind alle sieben Punkte der Vorschlagsliste außer
  Nr. 4 (Loch-Notizen, bewusst ausgelassen) umgesetzt.
  **Aufgeräumt:** Beim Bau hatte ich Teile doppelt angelegt — `roundShareText`/`roundShare` waren
  zweifach definiert, dazu vier ungenutzte Rechenkerne. Die Selbstprüfung meldete beides; entfernt.

- **v1.92.0 · 2026-08-09** — **Stabilitätsprüfung nach Kartenumbau · Uhr nachgezogen.**
  Systematisch geprüft: Verweise auf entfernte Elemente, `getElementById` auf nicht mehr
  vorhandene IDs, Ansichten ohne Render-Funktion, Handler auf fehlende Funktionen.
  **Ein echter Fund:** `playMapCtrlsRefresh()` suchte `#playMapCtrls` — das gab es nur im alten
  Blatt-Layout. Satellit, Ringe und Zielkette schalteten zwar um, die Knöpfe zeigten aber weiter
  den alten Zustand. Neu rendert **eine** Funktion `pfCtrlsRender()` die Leiste inklusive
  ⓘ-Schalter; sonst verschwand der bei jedem Refresh.
  **`mergeDB` führte Fahnen weiter zusammen** — damit hätte der nächste Sync die in
  `ensureDefaults` geleerten Werte aus dem Repo zurückgeholt. Jetzt `out.pins={}`.
  **MainActivity.kt nachgezogen (2026-08-09 Nr. 11):** `Geo.pinPoint`, `AppData.pins`, der
  Zustand `pinDepth` samt Parameter in `buildRoundJson`/`Loaded`, das Feld `"pins"` im
  Runden-JSON und drei Parser-Blöcke entfallen. `targetOf()` liefert immer die Grünmitte; die
  gesonderte „Fahne"-Distanz in `liveOf()` entfällt (identisch mit `mid` aus F/M/B).
  Vertrag geprüft: 24 von 24 Lochfeldern, Options 9 Felder = 9 `strList`, alle Klammertypen
  balanciert.

- **v1.91.1 · 2026-08-09** — **Stabilitätsdurchgang nach dem Kartenumbau.** Systematisch geprüft:
  keine verwaisten Element-Zugriffe mehr (`playMapSlot` hatte noch einen toten Rückfall auf das
  seit v1.89 nicht mehr existierende `#playMapSlot` — entfernt; die Funktion liefert außerhalb
  des Kartenmodus jetzt bewusst `null`, woran die Vermeidung der Dauerlast hängt). Fünf Abläufe
  simuliert: Rundenstart, drei Lochwechsel, Eingabe↔Karte↔Eingabe, 20 GPS-Ticks in der
  Eingabemaske (0 Kartenberechnungen) und Zurück-Taste — alle korrekt, kein Blatt im Kartenmodus,
  kein Doppelrendern. Die Schnittstelle zur Uhr ist unverändert; alle 23 Lochfelder werden
  geschrieben.

- **v1.91.0 · 2026-08-09** — **BUGFIX: Einfrieren beim Wechsel Karte → Eingabe.**
  Seit v1.89 hat die Eingabemaske keinen Kartencontainer mehr; `playMapSlot()` liefert dort
  `null`. Ohne Riegel fiel **jeder GPS-Tick** in den teuren Zweig und rief `playMapRender()` —
  Geo-Raster, Kachel-Abrufe und eine Netzabfrage für die Höhen, im Sekundentakt. `playMapTick()`
  bricht jetzt mit `if(!slot) return;` sofort ab, und `playMapRender()` prüft Container und Geo
  **vor** der Höhenabfrage (vorher stand die Prüfung dahinter, der Netzaufruf feuerte also auch
  ohne Karte). Redundante zweite Prüfung entfernt. Nebenbei: `playLiveRefresh()` aktualisiert
  wieder beide Anzeigen — `#pfCaddy` im Kartenmodus, `#playInfo` in der Eingabemaske.
  **Dieselbe Fehlerklasse wie v1.75** (dort fehlte der Slot im Vollbild); vier Prüfungen sichern
  sie jetzt ab.

- **v1.90.0 · 2026-08-09** — **Fahnensteuerung vollständig entfernt.** `pinPoint`,
  `greenAxisEdges`, `playPinRec`, `pinCtrlHtml`, `playSetPinDepth`/`-Side`, `playPinSlide`,
  `playPinCommit` und `playClearPin` sind entfallen; `DB.pins` wird nicht mehr geführt und
  vorhandener Altbestand einmalig **geleert** (nicht gelöscht — `mergeDB` kann Löschungen über
  `Object.assign` nicht ausdrücken). Als Ziel gilt durchgängig die **Grünmitte**: `F = null` ist
  für `STRAT.approach()` genau das. Begründung: eine Handeingabe pro Loch, die im Alltag nicht
  gepflegt wurde — und ungepflegte Werte verschlechtern die Rechnung, statt sie zu verbessern.
  Die Grünmaße (`greenDims`, `greenFMB`) bleiben unverändert. Elf Prüfungen sichern ab, dass
  keiner der Namen in den ausführbaren Code zurückkehrt.

- **v1.89.0 · 2026-08-09** — **Eingabemaske ohne Karte und Fahnensteuerung.** Beides stand in
  beiden Modi; seit der Spielmodus im Kartenmodus startet, ist das doppelt. Die Eingabemaske ist
  jetzt reine Eingabe — die Distanzanzeige (`playInfoHtml`) bleibt, weil man sie beim Eintragen
  braucht. Die Fahnensteuerung wandert in den Kartenmodus an die aufgeklappte Caddy-Anzeige;
  dort gehört sie hin, weil man die Fahnenlage im Bild einschätzt. **Nebeneffekt:** Der kleine
  Kartenausschnitt löste dieselbe teure Berechnung aus wie die große (Geo-Raster, Kacheln,
  Monte-Carlo) — der `playMapRender()`-Aufruf in der Eingabemaske entfällt mit.

- **v1.88.0 · 2026-08-09** — **Spielmodus ist jetzt eine normale Ansicht, kein Overlay.**
  Zwölf Anläufe mit `position:fixed`, `<dialog>` im Top Layer und gemessenen Höhen sind daran
  gescheitert, dass der Browser den Bezugsrahmen anders berechnet als erwartet (gemessen 506
  statt 649 px bei `offsetParent: null` und `position: fixed`). Neu: eigene Ansicht `#v-play`
  im Dokumentfluss, `body.play-mode` blendet Kopfzeile/Navigation/FAB aus, innen Flex-Spalte mit
  dehnbarer Karte und Aktionsleiste **im Fluss** — sie sitzt damit immer am Ende des Inhalts.
  Bleibt unten Platz, hat er dieselbe Farbe wie die Karte und fällt nicht auf.
  **Ersatzlos entfallen:** `pfFit`, `pfViewportH`, `pfApplyHeight`, `pfVerify`, `_pfMaxH`, die
  dialog-Sonderbehandlung und die popstate-Verrenkungen. Die Zurück-Taste erkennt den Spielmodus
  an der body-Klasse. `pfRestoreView()` kehrt am Rundenende zur Ausgangsansicht zurück.

- **v1.87.0 · 2026-08-09** — **Vollbild-Höhe: der gemessene Wert wird gesetzt.**
  Live-Messung bei offener Ebene: `dialog 360×506`, aber `innerH/clientH/vvH` alle **649** —
  `pos fixed`, `offsetParent null`, also Positionierung korrekt. Das `<dialog>` streckt sich mit
  `inset:0; height:auto` nicht auf die volle Höhe; der Grund bleibt offen, das Ziel ist aber
  eindeutig, weil alle drei Quellen übereinstimmen. `pfFit()` setzt die Höhe auf das **Maximum**
  der drei Maße — beim Öffnen, nach 350/1200 ms, bei `resize`/`orientationchange` und in
  `pfRender()` nach jeder Interaktion. Frühere Versuche scheiterten, weil sie mit `100dvh` bzw.
  `visualViewport.height` den kleinsten Wert nahmen. Im Prüfstand mit den Gerätezahlen belegt.

- **v1.86.0 · 2026-08-09** — **Messanzeige startet eingeschaltet.** v1.85 läuft nachweislich auf
  dem Gerät, der Fehler besteht fort — die CSS-Regel ist geprüft korrekt und steht auf oberster
  Ebene (der vermeintliche Treffer „Regel in einem Medienblock" war ein Artefakt meines
  Prüfskripts, das CSS-Kommentare nicht entfernt). Damit fehlen nur noch echte Zahlen: Die
  Live-Messung (`DB.ui.pfDebug`) ist beim ersten Start automatisch an, misst jede Sekunde nach
  (die interessanten Änderungen passieren NACH dem Zeichnen) und weist zusätzlich die
  **Lücke unten in Pixeln** sowie die Position der Kopfleiste aus. Der ⓘ-Schalter in der
  Kartenleiste blendet sie wieder aus.

- **v1.85.0 · 2026-08-09** — **Vermutlich testete das Gerät die ganze Zeit eine alte Version.**
  Der Service Worker lieferte `index.html` nach *stale-while-revalidate*: beim Start kommt die
  gespeicherte Fassung, die neue erst beim nächsten Start. Wer nach jedem Upload einmal neu lädt,
  testet dauerhaft eine Version zu alt. **`sw.js` v2:** Netz zuerst mit 1,5-s-Zeitlimit, Cache als
  Rückfall — die Startgarantie im Funkloch bleibt. **`swForceUpdate()`** (Mehr → Daten → Diagnose)
  leert den Hüllen-Cache und lädt frisch; daneben steht die laufende Versionsnummer.
  **Live-Messung im Vollbild** (`pfDbgRender`, Schalter ⓘ): Version, echte Rechtecke von Dialog,
  Karte und Aktionsleiste, Viewport-Maße, `position`, `offsetParent`, `open` — bei OFFENER Ebene.
  Die bisherigen Messungen liefen bei geschlossener Ebene und waren wertlos.

- **v1.84.0 · 2026-08-09** — **Heller Balken unten und Aufhängen nach „Eingabe" behoben.**
  (1) Die Dialog-Box wird jetzt über `inset:0` plus `width/height:auto` definiert statt über
  `width/height:100%`. Prozentwerte lösen gegen den Containing Block auf — dessen Höhe war genau
  das ursprüngliche Problem; die Ebene war ganzflächig, ihre absolut positionierten Kinder
  rechneten aber gegen eine kürzere Box, daher der Balken. `position:fixed` wird ausdrücklich
  gesetzt (UA-Vorgabe für `<dialog>` ist `absolute`).
  (2) **`_pfClosing`:** `el.close()` feuert `close`, dessen Handler die Eingabemaske zeichnet —
  und der Aufrufer zeichnete danach nochmal. Zweimal Rendern plus Kartenberechnung sah aus wie
  ein Aufhängen. Der Handler reagiert jetzt nur noch auf Escape. In der Simulation nachgewiesen:
  ein `openSheet`-Aufruf statt zwei.

- **v1.83.0 · 2026-08-09** — **Vollbild als `<dialog>` im Top Layer — nachhaltige Lösung.**
  Sieben Anläufe mit `position:fixed` sind an einer Eigenheit des Geräts gescheitert: `bottom:0`
  löst gegen das Initial Containing Block auf, das 56 px kürzer ist als der Bildschirm
  (clientH 649 vs innerH 705). Jede Höhenkorrektur wurde zum Wettlauf mit der Adressleiste.
  Ein modales `<dialog>` liegt im **Top Layer**, sein Bezugsrahmen ist der Viewport selbst —
  unabhängig von ICB, Vorfahren, Adressleiste und z-index. Browser-Vorgaben zurückgesetzt
  (vor allem `max-width`/`max-height`), `cancel`/`close` abgefangen (Escape schließt ein Dialog
  sonst selbst und die Runde wäre unsichtbar), popstate prüft `open` statt der Klasse, Fallback
  für Browser ohne `showModal`. **Ersatzlos entfallen:** `pfViewportH`, `pfApplyHeight`,
  `pfVerify`, `_pfMaxH` und alle resize-Listener. `pfFacts`/`pfDiagShow` bleiben als Diagnose.

- **v1.82.0 · 2026-08-09** — **Die Ebene schrumpfte bei jeder Berührung: die Adressleiste.**
  Chrome auf Android blendet bei Interaktionen die Adressleiste ein und aus; jedes `resize`
  lieferte einen kleineren Messwert, den `pfApplyHeight()` brav setzte. Neu: `_pfMaxH` hält den
  größten während einer Öffnung gemessenen Wert — die Höhe kann nur **wachsen**, nie schrumpfen.
  Zurückgesetzt nur beim Öffnen und beim Drehen. `pfRender()` ruft `pfApplyHeight()` mit auf,
  damit die Höhe auch nach jedem Knopfdruck hält. Im Prüfstand als Rechnung festgeschrieben.

- **v1.81.0 · 2026-08-09** — **Vollbild-Höhe gelöst: `clientHeight` ist 56 px kürzer als der Bildschirm.**
  Die Messung auf dem Gerät: `offsetParent: null` (kein Vorfahre kapert die Positionierung),
  `position: fixed` korrekt, aber **innerH 705 gegen clientH 649**. `bottom:0` löst gegen das
  Initial Containing Block auf, dessen Höhe `documentElement.clientHeight` ist — deshalb konnte
  keine reine CSS-Lösung funktionieren. `pfViewportH()` nimmt jetzt das **Maximum** der drei
  Maße; alle früheren Versuche nahmen mit `100dvh` bzw. `visualViewport.height` das Minimum.
  `pfApplyHeight()` setzt es beim Öffnen und bei jeder Größenänderung, `pfVerify()` bleibt als
  Nachkontrolle. Im Prüfstand als reine Rechnung festgeschrieben.

- **v1.80.0 · 2026-08-09** — **Messen statt raten.** Nach fünf erfolglosen Hypothesen zur
  Vollbild-Höhe jetzt eine Messung: `pfFacts()` liefert Rechteck, Viewport-Maße, berechnete
  `position` und vor allem **`offsetParent`** — bei `position:fixed` muss der null sein, sonst hat
  ein Vorfahre einen eigenen Bezugsrahmen aufgespannt. `pfVerify()` vergleicht Unterkante mit
  Bildschirmhöhe und korrigiert bei einer Lücke >24 px um genau die gemessene Differenz (400 ms
  und 1200 ms nach dem Öffnen, dazu bei `resize`/`orientationchange`); jede Korrektur landet im
  Fehlerprotokoll. Neuer Knopf „📐 Layout-Diagnose" unter Mehr → Daten → Diagnose zeigt die Werte
  mit Kopier-Funktion. Zeigt `offsetParent` ein Element, ist der nächste Schritt ein `<dialog>`
  mit `showModal()` (Top Layer, unabhängig von Vorfahren).

- **v1.79.0 · 2026-08-09** — **Die Ebene schrumpfte wegen meiner eigenen Absicherung.**
  Bei `position:fixed` mit `top:0` definieren top+height die Box — eine gesetzte `height`
  **ignoriert** das gepinnte `bottom:0`. Sowohl `height:100dvh` (der dynamische, also kleinste
  Viewport) als auch `pfSize()` setzten eine Höhe; beim Lochwechsel blieb zudem ein zu kleiner
  Messwert stehen. **`pfSize()` ersatzlos entfernt**, jede `height`-Angabe entfernt — nur noch
  alle vier Kanten auf 0. `pfOpen()` räumt alte Inline-Werte weg.
  **Schlagfolge nach „Eingabe" weg:** `playAimDraw()` hängt in `#playVecG`, das jeder Neuaufbau
  ersetzt — gezeichnet wurde sie nur über `playMapApply()`, also erst beim nächsten GPS-Tick.
  `playMapRender()` ruft sie jetzt selbst auf.
  **„FW undefined%":** `approach()` liefert `gruen`, `tee()`/`nextShot()` liefern `fw`. Beide
  Beschriftungen prüfen jetzt mit `isFinite()`; im Prüfstand festgeschrieben.

- **v1.78.1 · 2026-08-09** — **Nachgewiesen: Schlagfolge wird auch von weit weg gezeichnet.**
  Ende-zu-Ende geprüft — am Abschlag und aus 2 km Entfernung liefert `playAimChain()` dieselbe
  Folge mit dem Tee als Startpunkt; `playAimDraw()` hängt nicht an `PLAY.here`. Zusätzlich
  abgesichert, dass `playMapInitView()` die eigene Position NICHT in den Ausschnitt einbezieht
  (sonst spannte die Karte über 2 km und die Bahn wäre ein Strich) und `playAutoView()` erst bei
  echtem Fortschritt näher heranzoomt.

- **v1.78.0 · 2026-08-09** — **Vollbild: Höhe, Rückweg, Plan trotz Entfernung.**
  (1) `pfSize()` maß gegen `visualViewport.height` — den sichtbaren Ausschnitt, auf Android
  kleiner als das Initial Containing Block, gegen das `position:fixed` rechnet. Zu kleine Höhe
  plus `top:0` schlägt das gepinnte `bottom:0`; daher der ungenutzte Streifen unten. Jetzt das
  Maximum aus `documentElement.clientHeight`, `visualViewport.height` und `innerHeight`, plus
  Nachmessen 350 ms nach dem Öffnen (die Adressleiste animiert).
  (2) **Rückweg ergänzt:** „🗺 Vollbild-Karte" ganz oben in der Eingabemaske — „✎ Eingabe" hatte
  kein Gegenstück, man kam nicht mehr zurück.
  (3) **Der Riegel aus v1.77 war falsch:** `_aimBuild()` startet ohnehin am Abschlag, die
  Schlagfolge ist also auch von weit weg korrekt. Entfernt. Gezeigt wird jetzt der „zu weit"-Hinweis
  UND `playTeePlanHtml()` — der Plan fürs Loch, gekennzeichnet als „gerechnet ab Tee".

- **v1.77.0 · 2026-08-09** — **Caddy schweigt außerhalb des Platzes.** Bei 2 km Entfernung gab er
  weiter Empfehlungen („3 Wood, lässt 1962 m") und einen Erwartungswert von 14,0 — korrekt
  gerechnet, aber sinnlos: die ES-Tabelle wird dort weit außerhalb ihres Stützbereichs
  extrapoliert. Neu `playTooFar()`: Schwelle Lochlänge + 150 m, mindestens 650 m. Wirkt in
  `playInfoHtml`, `pfCaddyKurz` und `playAimChain` (dort wird gar keine Monte-Carlo-Runde mehr
  gestartet). Score-Eingabe, Karte und Lochwechsel bleiben nutzbar. Im Prüfstand gegen
  realistische Fälle abgesichert: am Abschlag und auf der Bahn erlaubt, 2 km unterdrückt.

- **v1.76.0 · 2026-08-09** — **Vollbild bedienbar: Caddy-Anzeige zweistufig, Kopfzeile einzeilig.**
  Die Caddy-Anzeige nahm bis zu 40 % des Bildschirms und verdeckte die Bahn. Jetzt zugeklappt
  EINE Zeile (`pfCaddyKurz`: Rest zur Fahne · empfohlener Schläger · Wind), ein Tipp klappt die
  vollständige Anzeige auf (`pfInfoToggle`, max. 52 vh, kleinere F/M/B-Zahlen). Kopfzeile
  einzeilig. **Höhe:** alle vier Kanten gepinnt plus `height:100dvh`; `pfSize()` setzt den
  gemessenen Wert, aber ohne `bottom:auto` — das ließ in v1.75 unten eine Lücke, statt die
  gepinnte Kante greifen zu lassen.

- **v1.75.0 · 2026-08-09** — **Einfrieren behoben · Vollbild deckt jetzt wirklich den Bildschirm.**
  (1) **URSACHE DES EINFRIERENS:** `playMapTick()` suchte nur `#playMapSlot`, im Vollbild zeichnet
  die Karte aber nach `#pfMap`. Der Slot war null, also fiel JEDER GPS-Tick in den teuren Zweig
  und rief `playMapRender()` — Monte-Carlo, Kachel-Abrufe, SVG-Neubau. Bei laufendem GPS
  Dauerlast. Neu bestimmt **eine** Funktion `playMapSlot()` den Container; `playMapRender`,
  `playMapTick`, `playAutoView` und `playMapFit` nutzen sie. Die beiden letzten maßen zudem das
  Seitenverhältnis am unsichtbaren Blatt-Container — der Ausschnitt war dadurch verzerrt.
  (2) **Dreiviertel-Bild:** `pfSize()` setzt Breite und Höhe per JS aus `visualViewport` bzw.
  `innerHeight`, statt sich auf `position:fixed; inset:0` zu verlassen — das kann von einem
  Vorfahren mit `transform`/`filter` oder von der dynamischen Adressleiste unterlaufen werden.
  Neu gemessen bei `resize`, `orientationchange` und `visualViewport.resize`.

- **v1.74.0 · 2026-08-09** — **Vollbild: die eigentliche Ursache lag im MERGE, nicht im Vollbildcode.**
  `mergeDB` führt `ui` über `Object.assign({}, R.ui, L.ui)` zusammen — **ein Object.assign-Merge
  kann Löschungen nicht ausdrücken.** Das von v1.69 fälschlich gespeicherte
  `DB.ui.playMapFocus=false` kam deshalb bei jedem Repo-Sync zurück, und das Vollbild ging nie
  auf, obwohl die Ursache im Code längst behoben war. Jetzt liest `playFocusDefault()` gar nichts
  mehr und liefert immer `true`; der Knopf „✎ Eingabe" wechselt nur innerhalb der laufenden Runde.
  Der Altwert wird einmal aktiv auf `true` gesetzt statt gelöscht, damit er auch aus dem Repo
  verschwindet. **Das 700-ms-Zeitfenster im popstate-Handler ist ersatzlos entfallen** — es
  schluckte den echten Zurück-Druck; seit `pfOpen()` das popstate an der Quelle verhindert, wird
  es nicht mehr gebraucht. Verhalten in `tests.js` festgeschrieben (351 Prüfungen), inklusive
  der Merge-Eigenschaft selbst.

- **v1.73.0 · 2026-08-09** — **Vollbild blitzte auf und schloss sich: Zeitfenster reichte nicht.**
  v1.70 fing das popstate von `closeSheet()` über ein 700-ms-Fenster ab. Das genügte nicht, weil
  `pfOpen()` anschließend `playMapRender()` aufruft — Monte-Carlo und Satellitenkacheln blockieren
  dort regelmäßig über eine Sekunde, das popstate liegt so lange in der Warteschlange und trifft
  NACH dem Fenster ein. Jetzt wird `_sheetHist=false` VOR `closeSheet()` gesetzt: dann unterlässt
  closeSheet das `history.back()` und es entsteht gar kein popstate. Der History-Eintrag bleibt
  liegen — richtig so, der nächste Druck auf Zurück schließt damit das Vollbild.
  Zusätzlich läuft `playMapRender()` über `setTimeout(...,0)`, damit die Ebene sofort erscheint
  statt erst nach der Kartenberechnung. Doppelter `pfClose()`-Aufruf in `playFinish` entfernt.

- **v1.72.0 · 2026-08-09** — **Approach-Schläger · Vollbild-Ursache gefunden · Fehlerzähler.**
  (1) **`apprClub`** in allen drei Eingaben (Spielmodus, manueller Editor, Uhr Seite 2) — bisher
  wurde nur der TEE-Schläger erfasst, womit sich die Streuung je Schläger nur für den Abschlag
  lernen ließ, nicht für Eisen und Wedges. Auf der Uhr brauchte das einen neuen Parameter
  `clubNames`, weil `opts.teeClubs` nur Kategorien enthält.
  (2) **Warum das Vollbild nie aufging:** Der popstate-Handler rief `playToggleFocusMap()` — und
  das SPEICHERT die Vorliebe. Da v1.69 das Vollbild wegen der `history.back()`-Falle sofort
  selbst wieder schloss, schrieb jedes Aufblitzen dauerhaft „Vollbild aus" in `DB.ui`. Auch nach
  dem Fix von v1.70 blieb es deshalb aus. Neu: `pfHide()` verlässt das Vollbild OHNE die
  Vorliebe zu ändern (nur der Knopf „✎ Eingabe" tut das), plus eine einmalige Bereinigung des
  fälschlich gespeicherten Werts (`DB.ui.pfFocusReset`).
  (3) **Fehlerzähler** im Knopf wird nach dem Leeren aktualisiert (`renderData()` fehlte).

- **v1.71.0 · 2026-08-09** — **BUGFIX `_aimBuild`: „Cannot read properties of undefined (toFixed)".**
  `STRAT.approach()` legt `es`/`pen` unter `best.ev` ab, `tee()`/`nextShot()` direkt auf `best`.
  Mein Adapter aus v1.68 hat das nicht angeglichen — `_aimBuild` griff auf `best.es` zu, bekam
  `undefined` und starb an `.toFixed`. Trat doppelt auf: 2× als TypeError und 3× als unbehandelte
  Promise-Ablehnung über `elevEnsure().then() → playLiveRefresh → playAimDraw`, dort ohne
  Funktionsnamen im Protokoll. Drei Korrekturen: Adapter bringt `best` in die Form von `tee()`;
  `_aimBuild` prüft mit `isFinite()` statt nur auf Vorhandensein (fängt die ganze Klasse ab);
  der `.then()` in `playMapRender` bekommt ein `.catch`. Prüfstand vergleicht die Formen jetzt
  direkt gegeneinander (346 Prüfungen).

- **v1.70.0 · 2026-08-09** — **Vollbild repariert · `PLAY.aim`-Verlust behoben.**
  (1) Das Vollbild öffnete und schloss sofort wieder: `closeSheet()` baut seinen History-Eintrag
  über `history.back()` ab, das ausgelöste popstate schloss die gerade geöffnete Ebene.
  Jetzt zuerst schließen, dann öffnen, plus 700-ms-Schutzfenster (`_pfAt`), weil popstate
  asynchron eintrifft. Zusätzlich würgte `closeSheet()` GPS und eine laufende Schlagaufnahme ab
  — im Vollbild läuft die Runde weiter (Guard `_pfAn`).
  (2) **`playDefaults()`**: `playBegin`/`playContinue` ersetzten das ganze `PLAY`-Objekt ohne
  `aim` — nach jedem Rundenstart war `PLAY.aim` undefined und jedes Ziehen eines Wegpunkts warf
  einen Fehler (144× protokolliert, in der Oberfläche unsichtbar). Beide Startpfade laufen jetzt
  über zentrale Vorgaben; ein neues Feld dort wirkt automatisch überall.
  Der dritte protokollierte Fehler („Failed to fetch" in `freshRepoFetch`, 7×) ist **kein Bug**:
  das ist der Sync ohne Netz und wird korrekt abgefangen.

- **v1.69.0 · 2026-08-09** — **Echte Vollbild-Karte.** v1.68 machte die Karte nur größer — sie
  steckte weiter in einem Blatt. Jetzt eine eigene Ebene `#playFull` außerhalb des Blatt-Systems:
  Karte über den ganzen Bildschirm, Kopfzeile/Navigation/FAB ausgeblendet, alles andere als
  Overlay darüber (Loch & Score oben, Caddy-Empfehlung darunter, Kartenschalter rechts,
  Aktionsleiste unten: ◀ · 🎯 Schlag · ✎ Eingabe · ▶). `playMapRender()` zeichnet im Vollbild
  nach `#pfMap` statt `#playMapSlot` — dadurch bleiben Pan, Zoom, Zielkette und Live-Position
  unverändert. `pfRender()` erneuert NUR die Overlays, sonst spränge der Ausschnitt bei jedem
  GPS-Tick zurück. `playAfterHole()` ersetzt `renderPlay()` an allen Lochwechseln, die
  Zurück-Taste schließt zuerst das Vollbild. **Nebenbei zwei doppelte Funktionsdefinitionen
  (`playToggleFocusMap`, `playFocusDefault`) aus v1.68 entfernt** — dieselbe Klasse wie damals
  `holeRef`, gefunden von der Selbstprüfung.

- **v1.68.0 · 2026-08-09** — **Kartenmodus · Schlagaufnahme über beide Geräte · Approach-Prozente.**
  (1) Der Spielmodus startet jetzt im **Kartenmodus** (`renderPlayMapMode`): große Karte,
  kompakte Kopfzeile, zwei Aktionsknöpfe, feste Lochnavigation. Die volle Eingabemaske ist einen
  Tipp entfernt (`playToggleFocusMap`), die Wahl wird gemerkt. Hintergrund: die Scorekarte führt
  die Uhr, das Handy dient der Umgebungsanzeige.
  (2) **`_draftRound.live.rec`** neu — beginnt die Uhr eine Schlagaufnahme, zeigt das Handy
  Schläger, gelaufene Distanz und Dauer und kann den Schlag selbst abschließen
  (`watchRecFinish`): Startpunkt von der Uhr, Endpunkt vom Handy. Die Uhr erkennt den Abschluss
  und beendet ihre Aufnahme — sonst würde derselbe Schlag zweimal erfasst.
  (3) **`_aimApproachEv`**: Der Schlag aufs Grün hatte KEINE Bewertung — `evs` bekommt einen
  Eintrag je Wegpunkt, der Grün-Punkt ist keiner. Deshalb erschienen die Prozentzahlen nur am
  Abschlag. Jetzt mit eigener Bewertung, und dort steht die passende Größe: **Grün getroffen**
  statt Fairwayquote.

- **v1.67.0 · 2026-08-09** — **Bedienbarkeit: sieben Befunde aus dem Oberflächen-Durchgang.**
  (1) BUGFIX Toast: Timer wurde nicht zurückgesetzt, die zweite von zwei schnellen Meldungen
  verschwand vorzeitig. (2) `undoBar`: Rückgängig für Löschungen — `gpsDelShot` löschte bisher
  ohne jede Rückfrage, obwohl aufgezeichnete Schläge die Grundlage des schlaggenauen SG sind;
  auch für Runden. (3) Leerzustände in Tests, Bag und Strategie. (4) Rundensuche mit
  Fokus-Rückholung, sonst schließt die Tastatur nach jedem Zeichen. (5) Feste Lochnavigation
  (`.play-navbar`) — spart 18× Scrollen pro Runde. (6) Speicher-Rückmeldung in beiden Editoren.
  (7) Malaska-Übungen wieder im WORTLAUT (`MALASKA_DYN` 13 dynamische vor der Runde,
  `MALASKA_STAT` 6 Halteübungen danach) als nummerierte Liste je Block — die Kurzfassung half
  am Platz nicht.

- **v1.66.0 · 2026-08-09** — **Reiter „Daten" aufgeräumt.** 11 gleichrangige Abschnitte → 5 sichtbare
  plus eine Klappe. Sortiert nach Nutzungshäufigkeit statt nach Thema: Sichern/Sync zuoberst,
  danach Sicherungen, Diagnose, Datenbestand, Darstellung. Einrichtung (Repo-Zugang), Repo-Historie,
  Bilder-Sync, Entwicklerdoku und der Notfallbereich liegen in „⚙ Einrichtung · Wiederherstellung ·
  Notfall" — Konfiguration, die man einmal setzt, verdeckt nicht mehr die täglichen Aktionen.
  **Zwei echte Doppelungen behoben:** Die Sicherungsliste wurde ZWEIMAL gerendert (inline und in
  `#backupList`) — jetzt nur noch einmal. Und die vier Dateiknöpfe (FileSystem-API *und*
  Download/Upload) standen immer alle nebeneinander, obwohl je nach Gerät nur zwei funktionieren;
  bei vorhandenem `showSaveFilePicker` sind Download/Upload jetzt in eine Unterklappe gewandert.
  Alle Element-IDs unverändert, damit die Handler weiter binden.

- **v1.65.0 · 2026-08-09** — **BUGFIX `F is not defined` · Fehlerprotokoll und Selbstprüfung erweitert.**
  (1) **`playCaddyHtml`:** `F` (Fahnenposition) wurde INNERHALB des Approach-Zweigs mit `const`
  deklariert, weiter unten in der Höhenrechnung aber außerhalb dieses Blocks gelesen —
  ReferenceError bei jedem Live-Refresh im Spielmodus. Der Fehler stammte aus der
  Höhen-Erweiterung (v1.50). Jetzt einmal auf Funktionsebene. **Statisch war er nicht auffindbar**
  (eine blockweit gültige `const`, außerhalb gelesen); gefunden hat ihn das Fehlerprotokoll,
  weil es seit v1.60 den Funktionsnamen aus dem Stapel nennt.
  (2) **Fehlerprotokoll:** bleibt jetzt über Neustarts erhalten (`localStorage`, letzte 40) —
  ein Fehler auf dem Platz war bisher nach dem nächsten Laden weg. Gleiche Fehler werden
  zusammengefasst (`n`, erstes und letztes Vorkommen) statt die Liste zu fluten. Neuer
  Kopier-Knopf (`errLogCopy`) legt das Protokoll samt Version in die Zwischenablage.
  (3) **Selbstprüfung** um zwei Prüfungen erweitert: **Laufzeitfehler** aus `ERRLOG` (statische
  Prüfung und Laufzeit gehören zusammen — die eine hätte den `F`-Fehler nie gefunden) und
  **tote Funktionen** (nach dem Entfernen des Bahn-Fokus blieben `focusOn`/`toggleFocus` als
  Leichen zurück, so etwas fällt sonst nie auf). Alle 14 neuen Hilfsfunktionen aus v1.62–1.64
  in der Referenz nachgetragen, die verbliebenen leeren catch-Blöcke begründet.

- **v1.64.0 · 2026-08-08** — **Schlaggenaues SG · ableitbare Felder berechnet · Eingaben nach Wichtigkeit.**
  (1) **`sgHoleShots`**: Liegen GPS-Schlagpositionen vor (`h.shots`), fallen ALLE drei Annäherungen
  des aggregierten Modells weg — Lage kommt aus dem Lie-Raster (`lieCode`), Distanz aus der echten
  Position, Phase aus der Restdistanz beim Schlagstart (≤20 m = kurzes Spiel, letzter voller
  Schlag = Annäherung, davor langes Spiel). `sgRound` bevorzugt das und zählt in `genau` mit, wie
  viele Löcher exakt gerechnet wurden. **Sicherheitsnetz:** Passt die Zahl aufgezeichneter Schläge
  nicht zu `Score − Putts − Strafschläge`, wird NICHT gerechnet, sondern zurückgefallen — eine
  unvollständige Kette wäre schlimmer als eine ehrliche Näherung.
  (2) **`holeGir`/`holeUpDown`/`holeSandSave`**: Diese drei stecken bereits in Score, Putts, Par
  und Bunkerzahl. Sie von Hand zu erfassen kostet auf dem Platz Zeit und erzeugt Widersprüche
  zwischen gesetztem und rechnerischem Wert. Werden jetzt berechnet; ein erfasster Wert schlägt
  die Ableitung (für Fälle wie den eingelochten Chip).
  (3) **Alle drei Eingaben nach WICHTIGKEIT sortiert statt nach Rubrik.** Sichtbar ohne Klappe:
  die vier Kernfelder (Score ⭐ · Putts ⭐ · Approach-Distanz ⭐ · 1.-Putt-Distanz ⭐) plus die
  sechs, die aus einer Näherung eine belastbare Auswertung machen — **Approach-Lage ⭐** (behebt
  die Fairway-Annahme), **Approach-Fehler ⭐** (trainingsrelevantestes Feld: „systematisch zu
  kurz"), Tee-Ergebnis, Tee-Schläger (Streuungslernen je Schläger), Bunkeranzahl und Strafart
  (trennen die Ursachen), dazu Rest zur Fahne und Quality. In der Klappe bleiben nur Bunker-Typ,
  Recovery und die drei Overrides. Auf der Uhr wandern Strafschläge zusätzlich auf Seite 2 —
  sie sind eine eigene SG-Kategorie und belasteten in den Details das kurze Spiel.

- **v1.63.0 · 2026-08-08** — **SG-Pflichtfelder aus den Details nach oben — in allen drei Eingaben.**
  Strokes Gained wird von genau vier Feldern getragen: **Score · Putts · Approach-Distanz ·
  1.-Putt-Distanz**. Die letzten beiden lagen in den „Weitere Details"-Klappen und blieben auf der
  Runde regelmäßig leer — ohne 1.-Putt-Distanz lassen sich Putten, Kurzspiel und Annäherung nicht
  trennen, es bleibt nur der Gesamtwert. Verschoben in **Spielmodus** (`play-quick2`),
  **manuellem Rundeneditor** (`openAddRound`, dazu „Rest zur Fahne" nach oben) und auf der
  **Uhr** (MainActivity 2026-08-08 Nr. 7: von Seite 3 auf Seite 2, direkt nach dem Putts-Stepper).
  Alle vier sind mit ⭐ markiert. In den Details bleiben nur noch die verfeinernden Felder
  (Lage, Approach-Fehler, Quality, Bunker, Strafart, Up&Down, Sand Save, Recovery).

- **v1.62.0 · 2026-08-08** — **Geführtes Aufwärmen mit Abschlagzeit · `apprMiss` in der Rundeneingabe.**
  (1) **`WARMUP_PLANS`** mit vier Varianten (12 / 25 / 45 Minuten + 8 Minuten Nachbereitung),
  alle nach demselben Gerüst **Körper → Kontakt → Tempo → Transfer → Kurzspiel → Putten**.
  `warmupSchedule(plan,teeTime)` plant RÜCKWÄRTS von der Abschlagzeit inkl. 2 min Weg zum Tee,
  markiert den laufenden Block und warnt, wenn die Zeit nicht reicht.
  **Vier inhaltliche Korrekturen am alten Protokoll:** Putten stand an zweiter von fünf Stellen —
  der Putt-Speed ist die verderblichste Information des Aufwärmens und steht jetzt IMMER zuletzt.
  Kurzspiel fehlte ganz, obwohl Chip und Bunker auf den ersten Löchern kommen. Der Transfer-Block
  (Schläger von Loch 1 mit voller PreShot-Routine) ist neu. Und die statischen Malaska-Halteübungen
  laufen jetzt NACH der Runde — davor senken sie die Schnellkraft; vor der Runde bleiben die
  dynamischen Anteile. Dazu ein wöchentlich wechselndes Korrektiv aus den eigenen Fehlerbildern
  (`warmupKorrektiv`) und eine Abbruchregel für schlecht laufende Aufwärmungen.
  (2) **`apprMiss` in `openAddRound`** ergänzt — der manuelle Rundeneditor war die einzige der drei
  Eingabestellen ohne dieses Feld. Spielmodus und Uhr hatten es bereits. Damit sind alle für
  Strokes Gained und die Strategie nötigen Felder in allen drei Wegen erfassbar.

- **v1.61.0 · 2026-08-08** — **BUGFIX: „Unterbrochene Runde fortsetzen?" erschien nach JEDER Runde.**
  `discardDraft()` setzte einen Tombstone, `playFinish()` aber nicht — dort lief nur
  `clearDraft()`. Der Entwurf verschwand damit lokal, lag aber weiter im Repo (letzter
  Checkpoint-Push). Beim nächsten `cloudLoad` holte `mergeDB` ihn über `dr = a || b` zurück, und
  der Spielmodus fragte erneut. Dauerhaft. Neu: gemeinsame Funktion `draftFinalize()` für beide
  Wege, plus `flushCloudNow()` nach dem Speichern, damit der Tombstone sofort im Repo landet.
  **Zweite Korrektur:** `discardDraft` nahm den Zeitstempel DES ENTWURFS als Tombstone — ein
  danach eintreffender Push (Heartbeat der Uhr, wenige Minuten später) hatte einen neueren ts
  und kam durch. Jetzt gilt immer der Zeitpunkt des Beendens. Gegenprobe simuliert: ein
  wirklich neuer Entwurf nach dem Tombstone wird korrekt NICHT unterdrückt.
  **Bahn-Fokus entfernt** (Schalter 🔦): dunkelte die Nachbarbahnen ab, half auf dem kleinen
  Display aber nicht beim Erkennen und kostete einen Platz in der engen Leiste.
  `focusOn`, `toggleFocus` und der `opt.focus`-Block in `courseSVG` sind vollständig entfallen.

- **v1.60.0 · 2026-08-08** — **Korrelation statistisch abgesichert · Schlagfolge lesbar · Fehler lokalisierbar.**
  (1) **Korrelation:** Die Ansicht zeigte Balken ab 4 Runden — bei n=4 muss |r| aber über **0,95**
  liegen, um bei p<0,05 überhaupt etwas zu bedeuten. Praktisch war fast jeder Balken Rauschen,
  sah aber nach Erkenntnis aus. Neu: `rKrit(n)` (kritischer Wert, df=n−2, mit Interpolation),
  nicht abgesicherte Zeilen ausgegraut samt nötiger Schwelle, Zählung „x von y abgesichert".
  Ausdrücklicher Hinweis, dass die „Kosten"-Zeilen AUS dem Ergebnis abgeleitet sind und deshalb
  zwangsläufig korrelieren — sie zerlegen den Score, sie erklären ihn nicht. Neu außerdem
  `medianSplit()`: die Runden am Median geteilt, Kennzahl in guter und schlechter Hälfte
  gegenübergestellt. Bei kleinem n aussagekräftiger als jeder Korrelationswert und ohne
  Statistikkenntnisse lesbar.
  (2) **Schlagfolge auf der Karte:** Die beiden Beschriftungszeilen waren zwei unabhängig
  positionierte `<text>`-Elemente und lagen übereinander. Jetzt EIN `<text>` mit `<tspan dy>`
  (Zeilenabstand stimmt immer), Ankerpunkt wandert je Schlag auf 35 / 55 / 75 % der Linie und
  die Seite des Versatzes wechselt — die Schläge eines Lochs liegen fast auf einer Geraden,
  bei gleichem Anteil kollidierten kurze Striche.
  (3) **Fehlerprotokoll:** `where` enthält jetzt den Funktionsnamen aus dem ersten Stapelrahmen.
  Ein Eintrag `:15501` war bei einer Inline-Datei nicht lokalisierbar; künftig steht dort
  `playCaddyHtml()  :15501`.

- **v1.59.0 · 2026-08-08** — **Gameplan: vollständige Strategie vom Abschlag bis ins Loch, mit Begründung.**
  `planCourse(...,full)` liefert je Loch `plan[]` mit einer Zeile pro Schlag (Abschlag · ggf.
  2. Schlag · Annäherung · Putten), jeweils Schläger, Ziel, Distanz und **warum**. Die Begründung
  kommt aus den Daten des Optimierers, nicht aus Textbausteinen: Fairway-/Grün-/Sand-/Straf-Anteile,
  die gewählte Ziellinie, und was die nächstbeste Alternative kosten würde (`tee().alt`).
  Anzeige je Loch aufklappbar mit der Kurzform „Driver → 7-Eisen → Putter" in der Kopfzeile.
  **Rechenzeit:** `approach()` prüft bis zu 3 Schläger × 42 Zielpunkte × 100 Samples je Loch —
  deshalb wird nur der ANGEZEIGTE Modus voll gerechnet (~2 s für 18 Loch), die anderen beiden
  nur bis zur Abschlagsebene für den ES-Vergleich (`p.full` merkt sich das).
  **Drei Korrekturen aus dem Funktionstest:** vom Boden wird kein Driver mehr vorgeschlagen
  (die Regel gab es in `nextShot`, nicht in `planCourse`); der zweite Schlag wird nach der
  verbleibenden Restdistanz benannt statt pauschal „Grün angreifen"; und Restdistanzen unter dem
  kürzesten vollen Schläger heißen jetzt ehrlich „Teilschlag — nach Wedge-Matrix spielen" statt
  „außerhalb des bewerteten Bereichs".

- **v1.58.0 · 2026-08-08** — **SG-Blocker behoben, Aufwärmroutine oben, Gameplan je Modus.**
  (1) **WARUM SG LEER BLIEB:** `sgBandMid` gab für Werte OHNE Ziffer `null` zurück — und die
  Optionslisten enthalten genau solche: **„Gimme"**, „Holed", „3-Putt+". Da die 1.-Putt-Distanz
  Pflichtfeld der Zerlegung ist, fiel damit **jedes Loch mit kurzem ersten Putt komplett aus der
  Auswertung** — bei einem 20er der häufigste Fall. Jetzt: Gimme = 0,5 m, Holed = 0,
  „3-Putt+" = keine Distanz. Dazu eine Rückfallkette für die Restdistanz (erfasste 1.-Putt-Distanz
  → bei GIR `distToPin` → Feld `quality`, das genau diese Information trägt) und die
  GIR-Ableitung VOR den Rückfällen. `sgEnrich` zieht fehlende `par`/`len`/`si` aus den
  Platzdaten nach — nötig für Runden, die auf der Uhr entstanden sind.
  (2) **Diagnose statt Rätselraten:** `sgCoverageHtml` zeigt aufklappbar, wie viele Löcher je
  Pflichtfeld verwertbar sind und was am häufigsten fehlt — in beiden Fällen, leer wie gefüllt.
  (3) Aufwärmroutine steht wieder ganz oben; die Positionierung läuft nicht mehr über
  `h.indexOf("</details>")`, sondern über eine saubere Trennung (`hWarm`).
  (4) **Gameplan je Modus:** Der Plan lag unter `Kurs|Tee` OHNE Modus — nach einem Moduswechsel
  zeigte die App den ALTEN Plan und behauptete in der Kopfzeile den NEUEN. Jetzt `Kurs|Tee|Modus`,
  Umschalter sicher/normal/offensiv, Vergleich der erwarteten Schläge über 18 Loch (⭐ = beste
  Strategie, Klammer = Kosten der anderen) und ↔-Hinweise, wo ein anderer Modus einen anderen
  Schläger wählt. Der angezeigte Modus wird nach `Kurs|Tee` gespiegelt, weil die Uhr
  (`parsePlans`) diesen Schlüssel liest. Inhaltlich war der Plan korrekt — `planCourse` nutzt
  denselben Optimierer wie der Live-Caddy, nur windneutral.

- **v1.57.1 · 2026-08-08** — **GIR-Ableitung für Uhr-Runden und Altdaten.** `sgHole` leitet GIR
  jetzt aus `Score − Putts ≤ Par − 2` ab, wenn `girDirect` fehlt. Nötig, weil die Uhr das Feld
  seit v1.55 bewusst nicht mehr abfragt und weil es in Altrunden fehlen kann — ohne Ableitung
  hätte jedes Loch als „nicht GIR" gegolten und dem kurzen Spiel Schläge angelastet, die es
  nicht gab. Ein erfasster Wert schlägt die Ableitung. Gegenstück in MainActivity.kt (2026-08-08
  Nr. 6): die Uhr schreibt jetzt `par`/`len`/`si` je Loch (ohne `par` lieferte `sgHole` für
  Uhr-Runden gar nichts), kennt `apprMiss` und liest `girDirect` statt `gir`.

- **v1.57.0 · 2026-08-08** — **Strokes Gained, Approach-Fehlerrichtung, gepflegt vs. gemessen.**
  (1) **SG neu** (`sgHole`/`sgRound`/`sgSummary`): Die App erfasste Tour-taugliche Rohdaten und
  wertete sie mit GIR-Quote aus. SG zerlegt jetzt in Langes Spiel / Annäherung / Kurzes Spiel /
  Putten / Strafschläge — die Kategorien teleskopieren **exakt** zum Gesamtwert (in tests.js
  über fünf Lochtypen geprüft). GIR-Sonderfall und Deckelung des kurzen Spiels verhindern, dass
  ein verzogener Abschlag oder ein Chip-Out dem Kurzspiel angelastet wird; Strafschläge separat.
  `sgDashHtml` führt das Dashboard an, `sgDisasterHtml` beantwortet „Woher kommen die Schläge
  über Bogey?", `sgDrillHint` verweist auf passende Übungen.
  (2) **`apprMiss`**: Approach-Fehlerrichtung (Kurz/Lang/Links/Rechts + Kombinationen) — beim
  Abschlag gab es das seit jeher, beim Approach fehlte es, obwohl „zu kurz" der häufigste
  Amateurfehler ist.
  (3) **`clubMeasured`/`clubCompareHtml`**: gepflegte gegen gemessene Schlägerlängen (Carry aus
  R10, Gesamt aus GPS, getrimmtes Mittel ab 8 Messungen), ⚠ ab 7 m, Übernahme per Knopf.
  Prüfstand 227 → 268; fünf gesetzte SG-Fehler alle erkannt.

- **v1.56.0 · 2026-08-08** — **Faules Zeichnen: nur noch die sichtbare Ansicht wird gebaut.**
  `renderAll()` erzeugte bisher alle 21 Ansichten neu — ~110 KB Code samt Korrelationsrechnung,
  STRAT-Auswertung und Diagrammbauern, obwohl immer nur eine sichtbar ist, und das an ~40
  Aufrufstellen mit wachsenden Kosten je Runde. Neu: `VIEW_RENDER` (direkte Funktionsreferenzen),
  `viewDirty`, `renderView`/`renderViewIfDirty`; `setView` zieht beim Wechsel nach.
  `renderView` fängt Fehler je Ansicht ab — eine kaputte Ansicht reißt nicht mehr alles mit.
  Vorher geprüft: keine der 18 Render-Funktionen schreibt in eine fremde Ansicht.
  **Selbstprüfung Nr. 8 neu:** meldet eine `<section class="view">` ohne `VIEW_RENDER`-Eintrag
  (bliebe sonst still leer) — gegen eine gesetzte Ansicht verifiziert.

- **v1.55.3 · 2026-08-08** — **Roh-URLs geprüft und fest eingetragen.** Alle drei
  `refs/heads/main`-Links funktionieren; `sw.js` und `tests.js` im Repo als aktueller Stand
  bestätigt (227 Prüfungen, `lmNum`-Fälle vorhanden), `index.html` mindestens v1.53 (Manifest
  im Kopf vorhanden). Neu dokumentiert: `index.html` NICHT abrufen — das Token-Limit greift bei
  >1 MB nicht zuverlässig und die Datei verbraucht den Arbeitsspeicher; hochladen ist besser,
  weil sie dann per grep/sed durchsuchbar ist. Dazu ein empfohlener Sitzungsablauf.

- **v1.55.2 · 2026-08-08** — **Abschnitt 0b präzisiert: Branch statt Commit-Hash.** Der Roh-Abruf
  wurde erfolgreich getestet — aber mit einem Commit-Hash, der dauerhaft auf einen alten Stand
  zeigt (geliefert wurde die Fassung vor dem `lmNum`-Fix, ohne dass das erkennbar war). Doku
  nennt jetzt alle drei Roh-URLs explizit und die Regel, den Branch zu verwenden. Ebenfalls
  festgehalten: selbst zusammengesetzte URLs lehnt das Abruf-Werkzeug ab, auch wenn dieselbe
  Datei unter anderer Ref schon geladen wurde — die URLs müssen vollständig im Chat stehen.

- **v1.55.1 · 2026-08-08** — **Neuer Doku-Abschnitt 0b: „Wo die Dateien liegen".** Repo, Pages-URL
  (geprüft erreichbar), Rolle der drei Dateien und das Roh-URL-Muster für den Quelltext-Abruf.
  Hintergrund: die Pages-URL liefert beim Abruf nur gerenderten Text; für die Quelle sind
  `raw.githubusercontent.com`-Links nötig, und das Abruf-Werkzeug akzeptiert nur vollständige,
  in der Unterhaltung genannte URLs. Regel 0e auf alle drei Dateien erweitert.

- **v1.55.0 · 2026-08-08** — **Prüfstand 154 → 227 Prüfungen; Bugfix `lmNum`.**
  Neu getestet: Geometrie-Primitive · `hazardsOnLine` · Kachelmathematik (`_tileX`/`_tileY`/
  `satTileUrl`/`satTileRange`) · `ringCentroid` · Launch-Monitor-Parser und Ausreißererkennung ·
  `clubSigma` · `playMapInitView` (30-m-Regel). Abdeckung 49/196 bzw. 42/62.
  **BUGFIX `lmNum`:** deutsche Zahlen mit Tausenderpunkt wurden um Faktor 1000 falsch gelesen
  (`"1.140,5"` → 1,1405), weil die Umstellung nur ohne Punkt griff — still, mit Auswirkung auf
  gelernte Streuungen. Beim Testschreiben gefunden.
  **Mutationsprobe:** ein Fehler entwischte zunächst, weil der Testausreißer alle drei Kriterien
  gleichzeitig riss; `lmMarkOut` wird jetzt kriterienweise geprüft. Lehre in der Doku vermerkt.

- **v1.54.0 · 2026-08-08** — **`tests.js` zum vollständigen Prüfstand ausgebaut: 61 → 154 Prüfungen.**
  Neue Abschnitte: **Merge** (`_mergeArr`/`mergeDB` — Vereinigung, Zeitstempel-Vorrang, Tombstones,
  Entwurfs-Verschmelzung zweier Geräte, live-Zeiger; die risikoreichste Logik der Datei, bisher
  komplett ungetestet) · **WHS-Index** · **Platzimport** (`classifyProps`, `holeRefFromTags` — der
  Pfad, der monatelang still kaputt war) · **Lie-Raster/Roll/Sichtlinie** an einem echten
  50×50-Raster · **R10-CSV** inkl. Semikolon-Trenner · Geometrie · Hilfsfunktionen ·
  Verhaltensgleichheit aller vier `clubPick`-Hüllen über 45 Distanzen.
  **Abdeckungs-Sperrklinke (Abschnitt 16):** neue reine Funktionen ohne Testfall lassen den Lauf
  fehlschlagen — der Prüfstand kann nicht mehr unbemerkt veralten. Quote wird bei jedem Lauf
  ausgegeben (aktuell 26/196 bzw. 38/62). **Mutationsprobe:** gegen fünf eingebaute Fehler
  gehalten, alle fünf erkannt. Regel 0d entsprechend präzisiert.

- **v1.53.1 · 2026-08-08** — **Regeln 0d/0e/0f ergänzt.** `tests.js` und `sw.js` standen bisher nur
  im Referenzteil, nicht als bindende Regel. Neu: `node tests.js` vor jeder Auslieferung
  (Exitcode 0 = Freigabe), bei geänderter rechnender Logik ein Testfall im selben Arbeitsgang,
  bei Fehlerbehebungen ZUERST der reproduzierende Fall; `sw.js` gehört zur Auslieferung samt
  `CACHE_VERSION`-Erhöhung und Pflege von `NEVER_CACHE`; regelmäßige Fehleranalyse aus den drei
  Quellen Tests / Selbstprüfung / Fehlerprotokoll, weil sie verschiedene Fehlerklassen abdecken.

- **v1.53.0 · 2026-08-08** — **Echte PWA, escapte Schlägernamen, Prüfstand, toter Rest weg.**
  (1) **`sw.js` neu** (zweite Datei, ins Repo-Root neben `index.html`) + Manifest als data:-URI im
  `<head>` + `swRegister()` beim Boot. Vorher: kein Manifest, kein Service Worker — die App-Hülle
  hatte KEINE Offline-Garantie, obwohl die Daten sicher lagen. Kartenkacheln jetzt cache-first
  (FIFO 400), Repo-/Wetter-/Worker-Antworten nie aus dem Cache. `swPrecacheTiles`/`swClearTiles`
  unter Mehr → Daten → Diagnose.
  (2) `escShort()` für HTML-Kontexte; 14 ungeschützte `${_short(...)}`-Einsetzungen umgestellt.
  `_short()` bleibt roh, weil an 11 Stellen bereits `esc(_short(...))` steht.
  (3) **`tests.js` neu** — 61 Prüfungen der reinen Funktionen in einer vm-Sandbox, mit Gegenproben.
  Die Doku forderte seit jeher einen „Node-Harness"; es gab ihn nie.
  (4) `playToggleLive` entfernt: einzige nirgends referenzierte Funktion der Datei. Recherche ergab,
  der Umschalter wurde bewusst entfernt (Live ist im Spielmodus immer an) — kein fehlendes
  Bedienelement, echter Restbestand. Doku-Verweis korrigiert.

- **v1.52.0 · 2026-08-08** — **Selbstprüfung + Doku-Pflicht als Regel.**
  `selfCheck(src)` (reine Funktion) + `runSelfCheck()` (lädt den eigenen Quelltext per
  `fetch(location.href)`, kein Build nötig) unter Mehr → Daten → Diagnose. Acht Prüfungen:
  doppelte Funktionsnamen · doppelte globale Deklarationen · neue undokumentierte Funktionen
  (Sperrklinke `SELFCHECK_BASELINE` mit den 335 Altbestands-Namen, gemeldet wird nur Neues) ·
  leere catch-Blöcke · `"use strict"` je Block · Changelog-Eintrag zur `APP_VERSION` ·
  Gültigkeit der JSON-Datenblöcke · `console.log`-Reste. Gegen vier gesetzte Fehler verifiziert.
  **Neue Regel 0/0b/0c** in den unverhandelbaren Regeln: keine Code-Änderung ohne Aktualisierung
  von Referenzabschnitt UND Changelog; Namensprüfung vor jedem neuen `function`; Selbstprüfung
  nach jeder Änderung.
  **BUGFIX aus v1.51:** dort war `"use strict"` versehentlich in den `application/json`-Block
  `gplib` geschrieben worden — eine Fehlmessung hatte den Datenblock für Code gehalten, die
  Übungsbibliothek (84 Einträge) wäre nicht mehr ladbar gewesen. Entfernt; Prüfung 7 fängt
  genau das künftig ab. Es gibt nur EINEN ausführbaren Script-Block, nicht zwei.

- **v1.51.1 · 2026-08-08** — **Doku nachgezogen.** Die Aenderungen v1.42–1.51 standen bisher NUR
  im Changelog, nicht in den Referenzabschnitten — 28 neue Funktionen fehlten dort komplett.
  Ergaenzt: STRAT (`playingLevel`, `_trimmedSd`, `learnLateralFromRounds`, `rollFor`/`applyRoll`,
  `blocked`, `nextShot`, `shotEV`-Signatur), Karte (`playMapBox`, 30 m hinter dem Gruen,
  `playAimChain`/`Draw`/`Hit`/`MoveTo` inkl. Cache-Regeln), Fahne (`pinPoint(geo,n,d,q)`,
  `playSetPinSide`), Wetter (`elevEnsure`/`elevDelta`, Hoehe in `playsLike`), Persistenz
  (`persistSoon`/`persistFlush`, Fehlerprotokoll `ERRLOG`/`logErr`/`showErrLog`), Schlaegerwahl
  (`clubPick`) und der komplette Live-Zeiger `_draftRound.live` inkl. Rollenverteilung
  Handy/Uhr. Neuer Abschnitt **Namensraum** mit der Regel, vor jedem neuen `function name(` auf
  Kollisionen zu pruefen (Anlass: `holeRef` war doppelt).

- **v1.51.0 · 2026-08-08** — **Wartbarkeit: Namenskollision, Fehlersichtbarkeit, Aufräumen.**
  (1) **BUGFIX `holeRef` war ZWEIMAL definiert** — `holeRef(p)` (Lochnummer aus OSM-Tags, Z. 12150)
  und `holeRef(geo,n)` (Tee/Gruen eines Lochs). Funktionsdeklarationen werden gehoistet, die
  spaetere gewinnt: die Aufrufe in `parseGeoJSONCourse` und `parseOverpassCourse` riefen in
  Wahrheit `holeRef(properties, undefined)` und bekamen immer `null`. Beim Import von GeoJSON/OSM
  erhielt damit **kein Feature eine Lochzuordnung**. Erste Funktion → `holeRefFromTags`.
  (2) **Fehler sind nicht mehr unsichtbar:** neuer Ringpuffer `ERRLOG` (letzte 40) + `logErr()`,
  globale Handler fuer `error` und `unhandledrejection` (Toast gedrosselt auf 1/4 s), und **alle
  55 leeren catch-Bloecke** protokollieren jetzt still statt zu verschlucken. Anzeige unter
  Mehr → Daten → Diagnose (`showErrLog`). Der catch IN `logErr` bleibt bewusst leer (Rekursion).
  Genau diese Luecke hat den `tapStart`-Fehler wochenlang verdeckt.
  (3) Der erste Script-Block (104 KB) laeuft jetzt ebenfalls unter `"use strict"` — vorher haette
  dort eine Tippfehler-Zuweisung stillschweigend eine globale Variable angelegt.
  (4) **Eine** Schlaegerwahl statt fuenf: neuer Kern `clubPick(clubs,d,{by,mustReach,allowDriver})`;
  `pickClub`, `_nearest`, `_reaching` und `_aimClub` sind duenne Huellen darum. Ueber 306
  Vergleichsfaelle (30–280 m, alle Modi) verhaltensgleich zur Altfassung.
  (5) `persistSoon()` buendelt schnelle Klickfolgen (Fahnen-Steuerung, Kartenschalter) zu EINEM
  Schreibvorgang; `persistFlush` auf `visibilitychange`/`pagehide`/`beforeunload`, damit nichts
  verlorengeht. `persist()` bleibt sonst ueberall sofort. ANMERKUNG: der urspruengliche Verdacht
  „97 persist()-Aufrufe = teuer" war ueberzogen — `snapshot()` ist bereits auf 1×/180 s gedrosselt;
  die reale Last ist allein der ~1-MB-`localStorage`-Schreibvorgang in `saveLocal`.

- **v1.50.0 · 2026-08-08** — **Caddy-Logik: sechs Kalibrierungsfehler behoben.**
  (1a) `_trimmedSd` — sigD wurde aus ALLEN GPS-Distanzen roh berechnet; halbe Schlaege, Punches und
  Wind steckten mit drin. Jetzt IQR-getrimmt (Ausreisser >1.5·IQR raus, Ruecknormierung ueber die
  gefilterte Menge). Testfall 7-Eisen: σ 21,5 m → 5,6 m, also **3,9-fache Ueberschaetzung** vorher.
  Gilt auch fuer `parseR10`. (1b) `learnLateralFromRounds()` — sigL kam bisher NUR aus einem
  R10-Import; ohne Launch-Monitor blieb es fuer immer bei der Heuristik. Neu invertiert aus der
  Fairwaytrefferquote: `σ = W / Φ⁻¹((1+p)/2)`, ab 25 erfassten Loechern. (2) `rollFor`/`applyRoll` —
  gesampelt wurde nur der Carry, der Landepunkt galt als Ruhepunkt. `caddyClubs` fuehrt carry UND
  dist, die Differenz IST der Roll; gewichtet je Lage (Fairway 1.0, Rough 0.35, Sand/Wasser 0).
  In `tee`, `nextShot` und `shotEV`. (3) `blocked()` + `grid().blockers` — Baeume galten nur als
  Landeflaeche; eine Ziellinie durch eine Baumgruppe wurde nicht bestraft. Jetzt Sichtlinientest
  (erste 25 m und letzte 20 m ausgenommen), Aufschlag bis 0,55 Schlaege in den Score beider
  Optimizer. (4) `playingLevel()` — `esHcp` nutzte den WHS-Index, also den Schnitt der besten 8
  aus 20 und damit das Potenzial. Jetzt Median der letzten Runden (Score ueber Par, auf 18
  hochgerechnet, ab 5 Runden); Index bleibt Rueckfall. (5) `pinPoint(geo,n,d,q)` — die Fahne war
  ein reiner Tiefenwert auf der Mittelachse, obwohl Short-Side seitlich ist. Neu mit `q` (−1…+1
  der halben Gruenbreite), `playSetPinSide`, Links/Mitte/Rechts in `pinCtrlHtml`. (6) `elevEnsure`/
  `elevDelta` + `playsLike(...,dElev)` — Hoehendifferenz fehlte ganz, obwohl 8 m Steigung bei
  150 m rund +8 m ergeben und der Temperatureffekt bei +5 °C nur +1,7 m. Bergauf voll, bergab
  0,75-fach; Hoehen gebuendelt ueber Open-Meteo Elevation, je Loch gecacht.

- **v1.49.0 · 2026-08-08** — **Caddy-Logik jetzt auch fuer die Folgeschlaege.** Neu
  `STRAT.nextShot(geo,course,hole,from,mode,hcp)` — derselbe Optimizer wie `tee()`, aber ab einem
  BELIEBIGEN Punkt: Kandidaten aus Schlaeger x Ziellinie (−10°…+10°), je 100 Samples mit der
  Streuung des Spielers, bewertet ueber die erwarteten Restschlaege vom Landepunkt (`pointES`)
  plus Strafanteil nach Modus. Layup-vs-Angriff faellt damit von selbst heraus; die frueheren
  Zwischenpunkte lagen stur auf der Geraden mit der Faustregel „lass ~95 m stehen" und kannten
  weder Bunker noch Wasser. Der Driver ist ausgeschlossen (nur der Abschlag darf ihn).
  `playAimChain` nutzt `tee()` fuer den Abschlag und `nextShot()` fuer alles danach; am Strich
  stehen jetzt zusaetzlich `FW %` und ab 5 % `Strafe %`, ab 8 % Strafrisiko ein ⚠.
  **Rechenaufwand:** die Kette wird ueber `PLAY.aimChainKey` zwischengespeichert (Loch, Tee,
  Modus, Overrides) — `playAimDraw` laeuft bei jedem GPS-Tick und jedem Pan-Bild und darf keine
  Simulation ausloesen. Waehrend des Ziehens baut `_aimDragging` bewusst die billige Variante,
  die richtige Bewertung folgt beim Loslassen. Bewertungen liegen zusaetzlich in `_aimCache`
  (Schluessel inkl. auf ~10 m gerundetem Ausgangspunkt, auf 200 Eintraege begrenzt).

- **v1.48.0 · 2026-08-08** — **Komplette Schlagfolge bis GIR auf der Karte, mit Schlaeger an jedem Schlag.**
  Neu `playAimChain()`: baut aus dem Tee-Ziel (`STRAT.tee().target`, weiterhin am Griff verschiebbar)
  und `caddyClubs()` die ganze Kette bis zum Gruen. Zwischenziele nach dem Prinzip aus `planCourse` —
  so weit wie moeglich, aber eine volle Wedge (`AIM_WEDGE=95` m) aufs Gruen uebrig lassen.
  `playAimDraw()` zeichnet jeden Schlag als eigene Linie mit Beschriftung **„n. Schlaeger · Distanz"**
  quer zur Linie versetzt, plus Fadenkreuz an jedem Zwischenziel.
  Zwei Korrekturen an der Empfehlungsqualitaet: (a) vom Fairway wird **kein Driver** mehr
  vorgeschlagen (`fairway`-Liste ohne /driver/i) — nur der Abschlag darf ihn nehmen;
  (b) die Kette prueft gegen **Par − 2**: reicht sie, ist der letzte Schlag gruen mit ⛳, reicht sie
  nicht, wird er rot mit „⛳ nicht in GIR" statt eine Empfehlung vorzutaeuschen, die nicht aufgeht.
  Ist das Tee-Ziel bereits hinter der eigenen Position, faellt es aus der Kette (kein Rueckwaertsschlag).

- **v1.47.0 · 2026-08-08** — **Karte: Verschieben repariert, 30 m hinter dem Gruen, Caddy-Ziel zum Ziehen.**
  (1) **Fix:** `playMapBind` benutzte `tapStart`, ohne es je zu deklarieren oder zu setzen. Der
  Zugriff auf die undeklarierte Variable warf in `pointermove` einen ReferenceError — und zwar
  VOR dem Pan-Code darunter. Dadurch war **Verschieben komplett tot** und das **Antippen zum
  Messen** loeste nie aus; beide Funktionen existierten, liefen aber nie. Jetzt korrekt als
  `let tapStart=null` deklariert und in `pointerdown` gesetzt.
  (2) `playMapInitView` und `playAutoView` nehmen einen Punkt **30 m hinter dem Gruen** (entlang
  der Achse Tee→Gruen, in Pixeln gerechnet) mit in die Bounding-Box — Hindernisse hinter dem Gruen
  sind damit immer sichtbar. Das Umfeld-Padding im mitwachsenden Ausschnitt sinkt dafuer von 30 m
  auf 18 m, damit die Bahn nicht insgesamt kleiner wird.
  (3) **Caddy-Ziel auf der Karte:** `STRAT.tee()` liefert mit `target` schon eine Zielkoordinate,
  die bisher nur als Text erschien. Neu zeichnet `playAimDraw()` sie als Fadenkreuz mit Linie
  Position→Ziel→Fahne, Schlaeger und beiden Distanzen; der Griff ist mit dem Finger verschiebbar
  (`playAimHit`/`playAimMoveTo`, Vorrang vor dem Karten-Pan). Der gezogene Punkt haengt am Loch
  (`PLAY.aim[course|hole]`, mit ✋ markiert), 🎯 blendet ihn ein/aus, ↺ setzt auf die Empfehlung
  zurueck. Gezeichnet wird in `#playVecG` wie `playHereEdge` — `courseSVG` bleibt unangetastet.

- **v1.46.0 · 2026-08-08** — **Fix: Bahn steht endlich mittig in der Spielmodus-Karte.**
  Ursache war `playMapClamp`: es presste den Ausschnitt zwingend in die Kachel (Groesse
  `<= b.W/b.H`, Position `[0, b.W-v.w]`). Zwei Faelle brachen damit die Zentrierung — eine auf
  das Kartenformat aufgezogene Bahn wurde breiter als die Kachel (`v.w=b.W`, `v.x=0`, also ganze
  Kachel statt Bahn), und eine Bahn nahe am Kachelrand wurde zurueckgeschoben. Gemessen an einer
  schmalen Randbahn: **376 px Versatz**. Zusaetzlich deckelte `playMapInitView` die Breite per
  `Math.min(M.W,v.w)`, ohne `x/y` nachzuziehen. Neu: `playMapClamp` laesst Ueberhang von einer
  halben Ausschnittsbreite zu (rechnerisch genug, um JEDEN Punkt der Kachel in die Bildmitte zu
  legen); neuer Helfer `playMapBox(cx,cy,w,h,aspect)` baut den automatischen Ausschnitt um einen
  festen Mittelpunkt, in der Reihenfolge Mindestgroesse → Seitenverhaeltnis → Obergrenze (vorher
  verzerrte die Mindestzoom-Grenze bei kleinen Bahnen das Format). `playMapInitView` und
  `playAutoView` nutzen ihn; Pan/Pinch/Zoom benutzen weiterhin `playMapClamp`.

- **v1.45.0 · 2026-08-08** — **Fix: Launch-Monitor · "Schlag nicht gefunden".** In der Einzelschlag-
  Tabelle wurde der Index per `sess.shots.indexOf(sh)` auf dem Ergebnis von `lmMarkOut` gesucht —
  das liefert aber **Kopien** (`Object.assign({},s,{_out})`), also fand `indexOf` per
  Referenzvergleich nie etwas und gab immer `-1` zurueck. Folge: jede Zeile rief
  `openLMshot(sid,-1)` auf (Detailansicht unmoeglich) und der ✕ rief `lmDelShot(sid,-1)`,
  das still nichts tat. Der echte Index wird jetzt beim Filtern mitgefuehrt (`srcIdx`), bevor
  markiert wird.

- **v1.44.0 · 2026-08-08** — **Fix: Uhr fand die gestartete Runde nicht.** `maybeCheckpointDraft`
  schrieb den Entwurf wegen `if(n>0 && …)` erst ab dem ersten erfassten Loch ins Repo — eine
  frisch gestartete Runde stand dort gar nicht. Neu: `playPublishStart()` (aus `playBegin` und
  `playContinue`) legt `_draftRound` inkl. `live` sofort an und pusht; `draftCloudMark` startet
  bei `-1`, damit auch der Stand mit 0 Loechern einmal hochgeht. `playLiveMark` aktualisiert jetzt
  `round` **und** `ts` statt nur `.live` zu ergaenzen. Neuer `cloudPushDraft()` faellt bei
  laufendem Sync (`cloudBusy`) auf `scheduleCloudSync()` zurueck, statt den Push still zu
  verschlucken. `playSaveDraft` haelt `live.at` bei jeder Eingabe frisch.

- **v1.43.0 · 2026-08-08** — **Sheet-Kopfleiste steht fest.** `.sheet` ist jetzt eine Flex-Spalte
  mit `overflow:hidden`; gescrollt wird nur noch `#sheetBody` (`flex:1;min-height:0;overflow-y:auto`,
  Innenabstand dorthin verschoben). `.sheet-x-bar` und `.grab` sind `flex:0 0 auto` und damit gar
  nicht mehr Teil des scrollenden Bereichs — vorher lag der ✕ nur auf `position:sticky` in einem
  Container, der selbst per `translateY` animiert wird, was unzuverlaessig haelt. `openSheet` setzt
  zusaetzlich `sheetBody.scrollTop=0`.

- **v1.42.0 · 2026-08-08** — **Live-Zeiger Uhr↔Handy.** Neu in `_draftRound.live`:
  `{src,hole,at,course,tee,date,side}`. Beide Geräte schreiben ihn beim Blättern
  (`playLivePush` in playPrev/playNext/playGoHole; Uhr in `pushDraft`), beide übernehmen den
  jeweils NEUEREN Fremdzeiger (`playAdoptRemoteHole` in `playSyncTick`). `mergeDB` behält den
  live-Zeiger jetzt getrennt vom Entwurf nach `.at` — sonst verliert das Gerät, das nur
  blättert, gegen das Gerät, das gerade tippt. **Auto-Start:** `watchLiveTick`/`watchLiveMaybeOpen`
  prüfen im 60-s-Takt (plus visibilitychange und einmal beim Boot), ob die Uhr eine Runde
  gestartet hat, und öffnen dann den Spielmodus automatisch auf dem Loch der Uhr.
  `watchAutoOpenedFor` verhindert Wieder-Aufspringen nach manuellem Schließen; Tombstone
  (`ui.draftDiscardedTs`) wird respektiert. `getDraftAny` akzeptiert eine Runde jetzt auch OHNE
  Score, wenn der live-Zeiger frisch ist (< 4 h) — die Uhr meldet den Start, bevor das erste
  Loch ein Ergebnis hat. Unbekannter Platz -> Toast statt stillem Nichtstun.

- **v1.41.0 · 2026-08-07** — **Fix: Bearbeitungen wurden vom Repo zurueckgesetzt · Entfernung antippen.**
  (1) **BUG mit Datenverlust.** `_mergeArr` entschied bei Schluesselgleichheit nach
  „laengerer JSON gewinnt". Eine Bearbeitung, die einen Eintrag KUERZER macht — Text straffen,
  Bild entfernen, Notiz zusammenfassen — verlor damit gegen den alten Repo-Stand und wurde beim
  naechsten Abgleich stillschweigend zurueckgesetzt. Auf der Pinnwand faellt das sofort auf,
  betroffen waren aber ALLE Listen (Runden, Turniere, Tests, LM-Sessions, Schwunganalysen).
  Neu: `_mergeArr(a,b,keyFn,tsFn)` vergleicht zuerst `updated` — der juengere Stand gewinnt;
  hat nur eine Seite einen Stempel, gewinnt diese. Die alte Heuristik greift nur noch, wenn
  BEIDE Seiten keinen Stempel haben (Altbestand). Notizen setzen `updated` jetzt beim
  Speichern, Anpinnen, Loeschen und Wiederherstellen (`noteTouch`).
  (2) **Entfernung messen** (`playMeasureAt`/`playMeasureClear`, `opt.measure`): kurzes Antippen
  der Karte setzt ein Fadenkreuz und zeigt **Strecke von der eigenen Position dorthin** plus
  **Reststrecke von dort zur Fahne** — die beiden Zahlen, die am Abschlag zaehlen („wie weit ist
  der Bunker, was bleibt danach"). Erneutes Antippen desselben Punktes loescht ihn, ebenso der
  📏-Knopf; beim Lochwechsel faellt er weg. Grundlage ist `mapLL(M,vx,vy)` — die Umkehrung von
  `M.map` INKLUSIVE Karten-Rotation (`strkLL`/`geoedLL` gelten weiter nur fuer die unrotierten
  Editoren). Antippen zaehlt bewusst NICHT als manuelle Bedienung: die Ausschnitt-Automatik
  bleibt an, `PLAY.mapManual` wird erst ab 8 px Bewegung gesetzt.

- **v1.40.0 · 2026-08-07** — **Spielmodus-Karte: Auto-Zoom, Bahn-Fokus, Fahne ueberall, Tee/Gruen-Tausch.**
  (1) **`holeRef(geo,n)`** ergaenzt fehlende Loch-Geometrie und ist ab sofort die EINZIGE Quelle
  fuer Rotation, Einpassung und Fahne: fehlt `green`, wird der Schwerpunkt der naechsten
  `green`-Flaeche am Linienende genommen (≤90 m), fehlt `tee` analog die `tee`-Flaeche; die
  Lochlinie wird immer auf Tee→Gruen gedreht. Damit steht auf JEDEM Gruen eine Fahne und Loecher
  ohne Gruenpunkt werden korrekt gedreht (war die Ursache fuer Loch 7 Nordplatz). Ergebnis
  gecacht ueber `WeakMap`. Im Geo-Editor wird bewusst weiter die ROHFASSUNG gezeigt — sonst
  liesse sich ein fehlendes Gruen nie setzen.
  (2) **`h.swap`** vertauscht Tee und Gruen dauerhaft (Fall Nordplatz Timmendorf, Loch 1).
  Knopf im Karten-Editor je Loch samt Statuskarte (gesetzt/abgeleitet/fehlt, Laenge Tee→Gruen)
  und Warnung, wenn der Abschlagpunkt auf einer Gruenflaeche liegt. Gespeichert in
  `geo.overrides.holes[n].swap`, damit die Korrektur einen Neu-Import der Platzkarte ueberlebt.
  (3) **Automatischer Ausschnitt** (`playAutoView`/`playAutoApply`): am Abschlag die ganze Bahn,
  ab ~8 % Fortschritt zieht der Ausschnitt mit — eingepasst auf **eigene Position + Gruen** mit
  ca. 30 m Luft, nie groesser als die ganze Bahn. Gemessen: 352 m sichtbar am Tee → 204 m bei
  145 m Rest → 95 m bei 22 m Rest, Gruen und eigene Position durchgehend im Bild. Uebernommen
  wird nur bei >3 % Aenderung, sonst zittert es im GPS-Rauschen. Eigenes Zoomen/Schieben setzt
  `PLAY.mapManual` und schaltet die Automatik ab; ⤢ (jetzt als Zustand markiert) und jeder
  Lochwechsel schalten sie wieder ein.
  (4) **Feature-Auswahl per Korridor statt Radius:** vorher „<55 m um die Stuetzpunkte der
  Lochlinie" — bei einer Zweipunkt-Linie also alles nahe Tee ODER Gruen (inkl. Nachbarbahn),
  waehrend ein Bunker in der Bahnmitte fehlte. Jetzt Abstand zur GESAMTEN Bahnlinie
  (`_distToLine`), Band ueber `opt.corridor` (Spielmodus 46 m).
  (5) **Bahn-Fokus ENTFERNT (v1.61)** — dunkelte alles seitlich des Korridors ab (Schalter 🔦).
  Ohne erkennbaren Nutzen im Feld: Auf dem kleinen Display half die Abdunklung nicht beim
  Erkennen, kostete aber einen Schalter in der ohnehin engen Leiste. `focusOn`/`toggleFocus`
  und der `opt.focus`-Block in `courseSVG` sind vollstaendig entfallen.
  standen sonst gleichberechtigt neben der eigenen. Bewusst abdunkeln statt abschneiden: wer
  nach links raushaut, muss trotzdem sehen, was dort liegt.

- **v1.39.0 · 2026-08-07** — **Fix: Spielmodus zoomte nicht auf die Bahn.**
  URSACHE 1 — `courseSVG` schob `opt.here` in `extraPts`, und `_fitProject` bezieht `extraPts`
  AUCH dann in die Bounding-Box ein, wenn `fitOnly` (Loch-Korridor) gesetzt ist. Stand man
  abseits der Bahn — Vorgruen des letzten Lochs, Clubhaus, ungenauer Erst-Fix — wuchs die
  Kachel auf diese Distanz, der Massstab brach ein und das Loch schrumpfte zum Streifen.
  Gemessen am Testplatz: Position 900 m entfernt → Bahn fuellte **28 % der Bildhoehe bei
  0,87 px/m** statt 82 % bei 3,0 px/m. Zusaetzlich wanderte der Ausschnitt bei jedem GPS-Tick,
  weil sich mit `here` auch `M` aenderte. Neu: `opt.fitHere` (Standard `true`); die
  Spielmodus-Karte uebergibt `fitHere:false` — die Position wird weiter GEZEICHNET, aber nicht
  eingepasst. `M` ist damit ueber die gesamte Bahn konstant (im Test ueber 6 Positionen und
  10 Ticks unveraendert).
  URSACHE 2 — `playMapInitView` gab seit v1.34.0 pauschal die ganze Kachel zurueck. Bei
  `preserveAspectRatio="xMidYMid meet"` blieb links und rechts ein Balken, die Bahn war
  kleiner als noetig. Der Sonderfall ist entfernt, die alte Einpassung auf Tee→Gruen mit
  Aufziehen auf das Rahmen-Seitenverhaeltnis ist wieder aktiv (`playMapInitViewLegacy` faellt
  weg). Ergebnis im Test: Bahn fuellt 97 % der Bildhoehe, Ausschnitt fuellt den Rahmen.
  Ausserdem: Korridor-Puffer 14 m → **32 m**, weil die Lochlinie nur die Mittellinie ist und
  Fairway, Gruen und Bunker seitlich daneben liegen.
  NEU dazu: `playHereEdge()` zeigt einen Pfeil am Bildrand samt Entfernung zum Abschlag, wenn
  die eigene Position ausserhalb des Ausschnitts liegt — sonst waere der blaue Punkt bei
  festem Ausschnitt einfach unsichtbar.

- **v1.38.0 · 2026-08-07** — **Schwunganalyse-Tracker (Training → Schwung).**
  Neue Ansicht `v-swing` in der Trainings-Gruppe, `DB.swingAnalyses[]`
  (`{id,date,tool,club,place,views[],faults[],good,findings,focus,rating,images[],videos[]}`),
  Merge additiv ueber `id`, FAB legt eine Analyse an.
  **Der Punkt ist nicht die Videoverwaltung, sondern der Verlauf:** `faults[]` kommt aus einer
  FESTEN Liste (`SW_FAULTS`, 24 Fehlerbilder, u. a. Reverse Spine Tilt, Take Away zu steil,
  Chicken Wing) — nur dadurch laesst sich ueber Monate zaehlen. Eigene Eintraege sind moeglich
  und werden ueber `swNormTag` gleich behandelt.
  Auswertung: `swFaultStats()` trennt „Aktuell dran" (in den letzten drei Analysen gesehen,
  Zaehler X/3) von „Laenger nicht mehr aufgetaucht" (≥2× erfasst, zuletzt aelter);
  `swMonthly()` fuellt Monatsluecken auf, damit das Balkenbild keine Regelmaessigkeit
  vortaeuscht; `swAvgGap()` zeigt den Ø-Abstand, und ab 35 Tagen ohne Analyse warnt die
  Kopfkarte. Tippen auf ein Fehlerbild listet alle betroffenen Analysen (`openSwingFault`).
  Videos liegen wie ueberall im Store `wikivid` (Warnung >60 MB, Ablehnung >250 MB) und
  **nur lokal**; beim Loeschen einer Analyse werden sie mit entfernt. Screenshots laufen ueber
  `resizeImage` wie bei den Notizen. `swExport()` schreibt CSV (Semikolon + BOM).

- **v1.37.0 · 2026-08-07** — **Satellitenkarte einmal je Platz herunterladen (Kachelraster).**
  URSACHE, warum der Cache vorher kaum griff: jede Ansicht holte EIN WMS-Bild fuer ihren
  eigenen Ausschnitt. Der Schluessel war die URL, und die enthielt BBOX plus WIDTH/HEIGHT —
  also je Ansicht, Zoom und Detailgrad eine andere. Praktisch nie ein Treffer.
  (1) **Festes Kachelraster** (`satTilePx` 512 fuer WMS / 256 fuer XYZ, `satZoomFor`,
  `satTileKey`, `satTileUrl`, `satTileRange`). Der Speicherschluessel ist jetzt
  `srcId/tilePx/z/x/y` und damit **ansichtsunabhaengig** — dieselbe Kachel gilt im
  Spielmodus, in der Platzkarte, beim Caddy und beim Schlag-Tracking. `_satWms`
  (Einzelbild je Ausschnitt) ist ersatzlos entfallen; `_satTiles` bedient beide Quelltypen.
  Live-Darstellung deckelt auf 54 Kacheln und geht sonst eine Zoomstufe runter.
  (2) **Kompletter Download je Platz** unter Mehr → Plätze: `satCoursePlan` rechnet einen
  Korridor (70 m Puffer) segmentweise entlang Abschlag → Spiellinie → Gruen jedes Lochs —
  bewusst NICHT die Bbox des Platzes, das waere ein Vielfaches an Daten fuer Parkplatz und
  Acker. `satDownloadCourse` laedt mit 4 parallelen Anfragen und meldet Fortschritt;
  `satCourseStatus` zeigt „✓ vollstaendig / X % gesichert / nicht gesichert" samt Groesse,
  `satCourseDelete` raeumt einen Platz wieder ab. UI: `satUiPaint(All)`, `satUiDownload`,
  `satUiDelete` mit Fortschrittsbalken; zweiter Tipp auf den Knopf bricht ab, bereits
  geladene Kacheln bleiben erhalten.
  (3) Schlaegt der Download komplett fehl (Dienst ohne CORS-Freigabe), sagt die App das
  klar und nennt den Ausweg (andere Luftbild-Quelle), statt still nichts zu tun.
  Speicherdeckel auf 600 MB angehoben (Ziel 450 MB), Aufbewahrung 365 Tage — heruntergeladene
  Plaetze sollen liegen bleiben. Hinweis beim Detailgrad: jede Stufe hat ihr eigenes Raster,
  nach dem Umstellen muss ein Platz neu geladen werden.

- **v1.36.0 · 2026-08-07** — **Spielmodus-Karte: Platzdaten ausblendbar, Fahne, Positionspunkt.**
  (1) `courseSVG` kennt `opt.osm` (Standard `true`). Bei `false` entfallen ALLE aus OSM
  abgeleiteten Elemente — Flaechen (`GEO_ORDER`), Loch-Linien, Hecken/Baumreihen/Grenzen/Wege
  und Baum-Punkte. Ausdruecklich NICHT betroffen: Luftbild, selbst gezeichnete Flaechen/Linien
  (`geo.mine`), Tee- und Gruen-Marker, Schlaege, Distanzringe, Dispersions-Oval und `hereDot`.
  Schalter 🗺 in der Kartensteuerung (`osmOn`/`toggleOsm`, `DB.ui.mapOsm`).
  (2) **Fahne in der Gruenmitte** (`_flagSvg`): Mast auf dem exakten Gruenpunkt, Tuch nach rechts,
  bewusst OHNE Kartenrotation gezeichnet, damit sie immer aufrecht steht. Weisse Kontur fuer
  Lesbarkeit auf hellem Sand wie dunklem Gruen. Aktiv ueber `opt.flag` (Spielmodus); im
  Geo-Editor bleibt der ziehbare Punkt, sonst waere die Gruenmitte nicht mehr verschiebbar.
  Die Lochnummer entfaellt dort, wo die Fahne steht (sonst Doppelbelegung).
  (3) **Eigene Position** deutlicher: kleiner blauer Punkt (r=5, #1a73e8) mit weissem Ring und
  weichem Schein (r=9, 16 %), `pointer-events:none`, als LETZTES gezeichnet — liegt damit immer
  oben und wird von keiner Flaeche verdeckt.
  (4) Kartensteuerung wird nach jedem Umschalten neu gezeichnet (`playMapCtrlsHtml`/
  `playMapCtrlsRefresh`) — vorher blieb der Zustand von ◎ und 🛰 optisch haengen.

- **v1.35.0 · 2026-08-07** — **Luftbilder werden zwischengespeichert statt jedes Mal geladen.**
  URSACHE der langen Wartezeit: die `<image>`-Elemente wurden bei JEDEM Neuzeichnen der Karte neu
  ins DOM gehaengt — im Spielmodus laeuft `playMapTick` mit dem GPS, also sekuendlich — und die
  WMS-Dienste liefern keine brauchbaren Cache-Header. Drei Massnahmen:
  (1) **Trennung Luftbild/Vektor:** `courseSVG` liefert jetzt zusaetzlich `satBody` und
  `bodyNoSat`. `playMapRender` baut `<g id="playSatG">` + `<g id="playVecG">`; `playMapTick`
  ersetzt NUR `playVecG`. Das Luftbild wird erst bei Loch-, Quellen- oder Platzwechsel neu
  gesetzt (`PLAY.satKey`). Damit fallen die Dauer-Downloads komplett weg.
  (2) **Persistenter Bild-Cache:** neuer IndexedDB-Store `satimg` (DB-Version 3→4), Schluessel ist
  die vollstaendige Bild-URL. `<image>` wird ohne `href` als Platzhalter mit `data-sat` erzeugt;
  `satHydrate` fuellt es aus dem Cache (Object-URL) oder holt es einmalig per `fetch` und legt den
  Blob ab (`satGetUrl`, Sammel-Promise gegen Doppelanfragen). Ein `MutationObserver` (`satWatch`)
  hydratisiert automatisch, damit kein Aufrufer es vergessen kann. **Faellt `fetch` aus (CORS,
  offline, fehlendes IndexedDB), wird die URL direkt als `href` gesetzt** — schlechtestenfalls
  also das alte Verhalten, nie ein leeres Bild. Ein Loch ist damit **offline** verfuegbar,
  sobald es einmal geladen wurde. Aufbewahrung 120 Tage, `satTrim` haelt den Cache unter 300 MB
  (aelteste zuerst), `satCacheStats`/`satCacheClear` fuer Anzeige und Leeren.
  (3) **Vorladen:** `satPrefetchCourse(geo)` holt alle Loecher eines Platzes nacheinander (Knopf
  im Karten-Sheet mit Fortschritt) — ideal vor der Runde im WLAN. Waehrend des Spiels laedt
  `playSatPrefetchNext()` still das naechste Loch vor.
  Zusaetzlich **Detailgrad** (`DB.ui.satDetail`, `SAT_DETAIL` schnell 2,0 / gut 1,25 / max 1,0 ×
  Grundaufloesung): steuert die angeforderte Pixelzahl beim WMS. Standard „gut" = ca. 25 cm je
  Pixel, rund 30 % weniger Daten als volle Aufloesung und weiterhin feiner als Esri.

- **v1.34.0 · 2026-08-07** — **Karte, Launch Monitor, Wissens-Suche, Schlagdaten.**
  (1) **Spielmodus-Karte wie die Einzelschlag-Ansicht:** `playMapRender`/`playMapTick` rufen
  `courseSVG` jetzt mit `tight:true` — die Kachel IST der Loch-Korridor (Abschlag unten, Gruen
  oben). `playMapInitView` gibt deshalb schlicht die ganze Kachel zurueck (Alt-Logik bleibt als
  `playMapInitViewLegacy`), und der Rahmen uebernimmt das Seitenverhaeltnis der Kachel, statt
  auf feste Hoehe zu zwingen — dadurch verschwindet die Ueberzeichnung der OSM-Flaechen.
  Rotation zusaetzlich abgesichert: fehlen Tee/Gruen, werden die Endpunkte der Lochlinie genommen.
  (2) **Luftbild in echt hoher Aufloesung:** neue Quellen-Tabelle `SAT_SRC` + `satSrcFor/satLayer`.
  Neben Esri-XYZ (z≤19, ~30 cm) jetzt amtliche **DOP** per WMS-GetMap als EIN Bild fuer die
  Loch-Bbox (`_satWms`, EPSG:3857, Pixelzahl aus `src.res`): Schleswig-Holstein DOP20 (20 cm,
  CC BY 4.0), NRW DOP10 (10 cm), Bayern DOP20. `auto` waehlt das feinste Bild, dessen Gebiet den
  Platz enthaelt. Umschalter im Karten-Sheet (`satSrcOptions/satSrcHint`, `DB.ui.satSrc`);
  Attribution wird aus der Quelle gezogen statt fest verdrahtet.
  (3) **Launch Monitor:** Einzelschlag-Tabelle je Schlaeger/Session (Carry/Total/Ball/Smash/
  Seitlich, Ausreisser markiert), `openLMshot` zeigt ALLE Messwerte eines Schlags, `lmDelShot`
  loescht einzeln (leere Session faellt weg), `lmExport('csv'|'json', onlyClub)` schreibt jeden
  gespeicherten Schlag aller Sessions — CSV mit Semikolon + BOM, damit deutsches Excel es direkt
  oeffnet.
  (4) **Wissensdatenbank beantwortet Fragen** (`qaAsk`/`qaSearch`/`qaIndex`): BM25 ueber die
  ABSCHNITTE aller Artikel (Ueberschriften trennen), deutsche Stoppwoerter, leichtes Stemming,
  Golf-Synonymtabelle `QA_SYN`, Abdeckungsfaktor, Bonus fuer Titel-/Ueberschrift-/Tag-Treffer,
  woertliche Phrasen und Wortpaare ("eisen 7"). Die Antwort ist immer **Originaltext** aus einem
  Artikel plus Quellenpfad — es wird nichts generiert. Laeuft vollstaendig offline.
  (5) **Schlag-GPS sammelt Rundenschlaege:** `roundShots()` rechnet `r.holes[].shots` (Punktkette)
  in echte Schlaege um, `roundShotsForStats()` filtert Putts/Chips heraus (Schlaeger gesetzt und
  ≥25 m). Quellen-Umschalter (`DB.ui.gpsSrc`: Messungen / Runden / beides) speist die
  Schlaglaengen-Datenbasis; Auswertungsblock (Runden, Loecher, Ø Abschlag, Ø letzter langer
  Schlag, laengster Schlag, Warnung bei fehlendem Schlaeger), `openRoundShotList()` als
  Detailliste und `gpsExportShots()` als CSV inklusive Start-/Zielkoordinaten.

- **v1.33.0 · 2026-08-07** — **Wissensdatenbank & Pinnwand: Videos, Links, Taxonomie, Grundpfeiler-Bibliothek.**
  (1) **Videos**: neuer IndexedDB-Store `wikivid` (DB-Version 2→3), Ablage als **Blob** (kein Base64).
  Im Artikeltext `[[vid:ID|Titel]]` (lokale Datei) und `[[yt:VIDEOID|Titel]]` (YouTube, Klick-zum-Laden
  via youtube-nocookie). Notizen bekommen `n.videos=[{id,name,size,type}]`. Funktionen `idbVidGet/Set/
  Del/Keys`, `wikiAddVideoFromInput`, `wikiHydrateVideos`, `noteHydrateVideos`, ObjectURL-Registry
  `_wikiObjUrls`/`_noteVidUrls` + `wikiRevokeUrls`/`noteRevokeUrls`. **UNVERHANDELBAR:** Video-Blobs
  gehen NIE in `trainingsdaten.json` und NIE in `wissen-bilder.json` — nur die Metadaten reisen mit.
  Uebertragung ausschliesslich ueber „Export inkl. Videos" (`wikiExport(true)`, Bundle-Version 2).
  Groessenwaechter: Warnung >60 MB, Ablehnung >250 MB.
  (2) **Links anklickbar**: `golfLinkify(escaped)` + `mkLink`/`linkifyText`; in `mdToHtml` eingehaengt
  (wirkt damit auch in dieser Doku) und in `renderNotes` fuer den Notiztext. Erkennt `[Text](URL)`,
  `<URL>`, nackte URLs, `www.`, E-Mail. **Nur** http/https/mailto/tel — `javascript:` ist ausgeschlossen;
  Eingabe muss bereits HTML-escaped sein (Platzhalter-Technik schuetzt erzeugte `<a>` vor Doppel-Ersatz).
  (3) **Taxonomie**: `WIKI_TAX` = 10 Bereiche → 34 Kategorien, `WIKI_CAT2GRP`/`wikiGroupOf` leiten den
  Bereich aus `a.cat` ab (freie Kategorien landen unter „Weitere"). Neue Filterebenen Bereich →
  Kategorie → Tags (Mehrfachauswahl, UND), Sortierung (`WIKI.sort`: cat/title/upd/new), Favoriten
  (`a.fav`), Gruppen-/Kategorie-Ueberschriften, Trefferzaehler, Filter-Reset. Tags werden ueber
  `wikiNormTag` normalisiert (klein, entdoppelt).
  (4) **Grundpfeiler-Bibliothek**: nicht-ausfuehrender Block `id="gplib"` (`type="application/json"`,
  84 Artikel aus `Grundpfeiler_Golf.docx`) mit **stabilen IDs `wa_gp_*`**; `gpLib/gpDiff/gpApply`
  zeigen neu/abweichend/identisch und uebernehmen nur Ausgewaehltes — beliebig oft wiederholbar,
  ohne Duplikate, ohne fremde Artikel anzufassen (`a.fav` bleibt erhalten, `a.src="grundpfeiler"`).
  Enthaelt u. a. die im Roh-Import verlorenen Tabellen: Schlaegerdistanzen Hoelzer/Eisen, Wedge-Matrix
  15–113 m, Ballpositions-Matrix (40 Situationen), Wind-Schlaegerwahl-Matrix, Driving-Iron-Tabelle,
  R10-Optimalwerte, Fitting-Ergebnisse.
  (5) **Aufraeumen & Tagging**: `WIKI_RULES` (34 Kategoriemuster) + `WIKI_TAGHINTS` (23 Tag-Heuristiken),
  `wikiSuggest(a)` schlaegt Kategorie/Tags vor, `openWikiRetagSheet`/`wikiApplyRetag` schreiben erst
  nach Bestaetigung. Schutzregeln: Artikel in einer Taxonomie-Kategorie werden **nie** umsortiert
  (`settled`), generische Kategorien („Allgemein", „Sonstiges", leer) genuegen schon Textfunde
  (`generic`), sonst braucht es einen Titeltreffer.
  Pinnwand zusaetzlich: Suchfeld + Kategorie-Chips (`NOTEF`), Papierkorb-Purge loescht jetzt auch
  die zugehoerigen Video-Blobs (`noteDropVideos`).

- **v1.32.0 · 2026-08-07** — **Strategie-Hub** (neue View `strat`, Gruppe „Analyse“ → Tab
  „Strategie“, `renderStrat`): (1) Status/Einstellungen — EV-Caddy an/aus (`DB.ui.strat`),
  ES-Basis auto/fest (`DB.strat.esHcp`, `stratHcpSet`); (2) **ES-Rechner** (Lie-Segmente +
  Distanz-Slider, Live-Update via `stratCalcSlide` ohne Re-Render, zeigt eigenen Wert, Scratch
  und Δ — `DB.ui.scDist/scLie`); (3) **Gameplan-Verwaltung**: Liste aller Pläne (ansehen/↻ neu),
  Erstellen für jeden Geo-Platz+Tee ohne Rundenstart (nutzt `stratPlanSheet`); (4) **Streuungs-
  Übersicht** aller gelernten Schläger + „Aus GPS lernen“- und R10-Import-Buttons (zweiter
  Einstieg neben Schläger-Setup); (5) **Plan-Bilanz über alle Runden** (`stratCommitTrendRows`,
  gleiche Aggregation wie im Rundendetail, Trend ab 10 Löchern). `renderStrat` in beiden
  renderAll-Pfaden registriert.

- **v1.31.0 · 2026-08-07** — **STRAT Phasen 3+4 (+5 Watch).** **B4 Approach:** `STRAT.approach`
  bewertet je passendem Schläger (±18 m) ein Ziel-Raster relativ zur FAHNE (quer ±12 m, längs
  −12…+8 m, 4-m-Schritt, 100 CRN-Samples via `shotEV`); Rest-Distanz je Sample zur Fahne
  (`pointESTo`) — Short-Side entsteht implizit aus Geometrie+Lage, kein Pauschal-Zuschlag.
  Fahne = Tages-Pin (playPinRec/pinPoint) oder Grünmitte. Im Spielmodus übernimmt der
  Approach-EV-Block (8–200 m, nicht auf dem Grün): „EV · PW · Ziel 4 m links der Fahne · 2,84 ES“
  + Streubild (Grün/Bunker/Penalty %) + Oval. **B5 Gameplan:** `STRAT.planCourse` windneutral je
  Kurs+Tee (Par 3: Distanz-Schläger; Par ≥4: stratTee; Par 5: Attack-vs-Layup-Vergleich via
  shotEV, Layup-Ziel ~95 m); Button im Spielmodus-Setup, Sheet mit Loch-Zeilen, 📋-Plan-Zeile in
  allen Caddy-Blöcken; Speicherung `DB.strat.gameplans["Kurs|Tee"]` (mergeDB byAt, seit v1.30).
  **B6 Commit:** Chips „Plan ✓/✗“ + „😐/🙂/💪“ in der Loch-Detail-Eingabe (Teilautomatik:
  Tee-Schläger ≠ Plan-Schläger belegt ✗ vor); Upsert in `DB.strat.commits` (roundId=PLAY.roundId);
  Rundendetail-Sektion „📋 Strategie & Commitment“ (Ø vs. Par je Gruppe, Trend ab 10 Löchern:
  „x Schläge/9 gespart“). **B7 Learn:** `STRAT.learnFromGps` (60-Tage-Fenster, n≥20, R10-Vorrang),
  R10-Session-CSV-Import (toleranter Parser: ,/; + Dezimalkomma, Spalten Club/Carry/Deviation)
  im Schläger-Setup + σ-Zeile je Schläger; `sigmaFor` bevorzugt Gelerntes (n≥20). **Phase 5:**
  Watch liest `strat.gameplans` (nur club+targetDesc, `parsePlans`) und zeigt die 📋-Zeile im
  Cockpit über der Caddy-Zeile.

- **v1.30.0 · 2026-08-07** — **STRAT-Modul Phase 1+2** (DECADE-Platzstrategie, Konzept v1.1):
  Expected-Strokes-Engine `STRAT.lookup(dist,lie,hcp)` (Scratch-Baseline + Offset-Funktion
  `esOffset` je Lie, HCP stufenlos via `esHcp:"auto"` → currentIndex); **Lie-RASTER** (~4-m-Zellen,
  Uint8Array, Cache 12 Löcher, Fingerprint-Invalidierung) mit Fairway-Korridor-Fallback ±20 m um
  h.line (Badge „EV ~“); **deterministische Halton-Samples** (150, Basen 2/3 → Acklam-InvNorm) mit
  CRN über alle Kandidaten; **Dispersions-Oval** (Familien-sigL 0.07/0.06/0.055/0.045/0.04,
  sigD aus clubSigma, biasL aus playerMissBias) als rotationsfester Punkt-Pfad in courseSVG
  (`opt.oval`, 1σ kräftig/2σ gestrichelt); **Tee-Optimizer `STRAT.tee`** (≤6 Schläger × 7 Linien
  −12°…+12°, Modi als Gewichte: safe=ES+1.5·Pen, bal=+0.5, aggr=ES); Spielmodus: am Abschlag
  (Par 4/5, <30 m am Tee) übernimmt die EV-Engine den Caddy-Block (Badge „EV“/„EV ~“, Streubild-
  Anteile, Alternative, Oval auf der Karte), sonst Heuristik-Badge; **ES-Einzeiler** unter der
  Distanzanzeige. `DB.strat` additiv (ensureDefaults; **SEED.version bewusst NICHT erhöht** —
  load() würde sonst den lokalen Stand verwerfen, Abweichung vom Konzeptpapier). mergeDB:
  strat-Regeln (byAt je Schlüssel, commits-Union roundId|hole|shot) + worker.js gespiegelt.
  Toggle `stratOn()` (DB.ui.strat). Phasen 3–5 (Approach/Gameplan/Commit/Learn/Watch) folgen.

- **v1.29.0 · 2026-08-07** — **Karten-Bedienung & Optik.** (1) `opt.tight` + `_fitProject(fitOnly,bufM)`:
  Loch-Ansichten passen das Bild jetzt NUR auf den Loch-Korridor (Tee/Linie/Grün + Schläge/Position,
  14 m Puffer) ein — das Loch füllt das Bild, Nachbar-Features laufen über den Rand (aktiv:
  Schlagkarten-Viewer, Schlag-Editor, Caddy-Karte, GPS-Ansicht bei Lochwahl; Spielkarte behält
  große Basis + engen Startausschnitt via playMapInitView padPx 14, damit Pan Raum hat).
  (2) `bindPanZoom`/`bindAllPanZoom` (svg.pzmap): Pointer-Events-Pan/Pinch/Rad für ALLE statischen
  Karten (Schlagkarten, GPS-Ansicht, Caddy). (3) Spielkarte: playMapBind von Touch- auf
  POINTER-Events umgestellt (Finger+Maus+Rad, gleiche Clamp-Logik). (4) OSM-Marker dezent:
  Bäume klein/randlos (r 2.4/3, Deckkraft 0.6) und auf SATELLIT ganz ausgeblendet (Bäume sind
  im Bild), „other"-Punkte minimal, Hecken/Baumreihen dünner (2 statt 3), Grün-/Tee-Punkte kleiner.

- **v1.28.0 · 2026-08-07** — **502-Fix + SYNC v2.1 + Referenz-Lebenszyklus.** URSACHE 502:
  trainingsdaten.json ist ~3 MB; die GitHub-Contents-API liefert bei JSON-Accept >1 MB KEIN
  content-Feld → Worker-v2-Read scheiterte. v2.1: Worker liest ROH (raw media type, bis 100 MB)
  und wird zum **SHA-Türsteher**: `GET ?fresh=1` liefert Stand + Header `X-Repo-Sha`; Schreiben
  nur mit `X-Base-Sha` (bei Parallel-Schreiber 409 → App holt frisch, merged LOKAL, versucht
  erneut, max 4×). Der Worker parst die Daten NIE mehr (Free-Tier 10-ms-CPU-sicher); der alte
  Server-Merge bleibt nur als Fallback für Alt-Clients. cloudSave zeigt jetzt den Worker-
  Fehlertext im Toast. **Referenz:** acks-System — jedes PWA-Gerät bestätigt automatisch, sobald
  es auf der Referenz aufbaut (`devId/devLabel`, Auto-Ack in paintRepoRef); Karte listet
  „Bestätigt von: …“; **„Referenz aufheben“** setzt einen cleared-Tombstone mit neuem Zeitstempel
  (mergeDB newer-wins verteilt ihn, alte Referenz kann nicht wiederauferstehen). mergeDB-Regel
  erweitert: reference mit GLEICHEM .at → Objekt-Union + acks-UNION (Worker-Port gespiegelt,
  Äquivalenz-Harness). 7-Tage-Historie markiert den Referenz-Commit mit 📌.

- **v1.27.0 · 2026-08-07** — **SYNC v2 (Neubau) + Karten-Fixes.** URSACHE der Überschreiber:
  Clients mergten vor dem Schreiben gegen die PAGES-Kopie (CDN bis ~10 min stale) und der Worker
  ersetzte die Datei komplett → Lost Updates. NEU: worker.js v2 macht den Merge SERVER-SEITIG
  gegen den frischen Stand (GitHub Contents API) mit Datei-SHA als Schreibsperre (409 → frisch
  neu mergen, max 5 Versuche); mergeDB ist 1:1 in den Worker portiert (Äquivalenz-Harness!),
  `force:true` im POST-Body = autoritativ ohne Merge (Zurücksetzen/Referenz). App: neuer
  Lesepfad `freshRepoFetch()` (Worker `GET ?fresh=1`, Fallback Pages) in cloudSave-Failsafe,
  cloudLoad(+Manual), playSyncTick, loadRepoRef, repoHardPull. **Karten:** Schläge/Position/
  Zeichnung fließen in die Bbox (`_fitProject(extraPts)`) — Schläge liegen nie mehr außerhalb
  des Ausschnitts; Schlag-Viewer UND Schlag-Editor jetzt Tee-unten/Grün-oben (Inversen strkLL/
  llFromVB drehen `M.rot` zurück); Rotations-Anker robust (verkehrte OSM-Linien werden am
  Grün erkannt und getauscht; Mindestlänge 20 m).

- **v1.26.0 · 2026-08-06** — **Karten-Upgrade Spielmodus/GPS:** (1) Karte richtet sich am Loch aus —
  Abschlag UNTEN, Grün OBEN (Rotation in `_fitProject(rotBeta)`, Winkel aus Tee→Grün; aktiv in
  Play-Karte, GPS-Ansicht mit Lochwahl und Caddy-Karte; Editoren GEOED/STRK bleiben Nord-oben,
  weil ihre Pixel→Koordinaten-Inverse keine Rotation kennt — NIE dort aktivieren). (2) Distanz-
  Ringe farbcodiert: 50 rot · 100 orange · 150 gelb · 200 türkis · 250 blau (`RING_COL`), Labels
  mit Halo. (3) **Satellitenbild** (Esri World Imagery, `_satTiles`): Kacheln als <image> mit
  Affin-Matrix in die rotierte Projektion; Flächen darüber halbtransparent, Spiellinie weiß,
  Pflicht-Attribution eingeblendet; offline laden Kacheln schlicht nicht → Vektor-Fallback.
  Umschalter: 🛰-Button auf der Play-Karte, „Satellitenbild"-Button in der GPS-Ansicht
  (`satOn/toggleSat`, `DB.ui.mapSat`, Standard AN). Auch in den Editoren als Zeichenhilfe aktiv.

- **v1.25.0 · 2026-08-06** — Putts-Standard-2 entschärft (Abgleich mit Wear-OS-App): Der Wert
  wird NICHT mehr beim bloßen Anzeigen eines Lochs in den Entwurf geschrieben (hätte beim
  Geräte-Merge echte Uhr-Eingaben überrollen können). Stattdessen: Anzeige zeigt 2, fest wird
  die 2 erst mit dem ersten Score des Lochs (playAdj), Putts-Stepper zählt von Basis 2.
  Wear-OS (MainActivity.kt) parallel überarbeitet: Driver-Sperre vom Boden (teePt-Check),
  MP-Modusparameter, Wasser-Carry-Entscheidung wie PWA, pushDraft merged Repo-Entwurf
  loch-genau + adoptHoles übernimmt Handy-Eingaben in leere Uhr-Felder, Putts-UI Standard 2.

- **v1.24.0 · 2026-08-06** — **Spielmodus-Paket:** Putts starten je Loch mit Standard **2**
  (nur bei Bedarf ändern; Statistik zählt Putts weiterhin nur bei gesetztem Score). Loch-Wechsel-
  Pfeile jetzt OBEN unter der Kopfzeile. GPS-Eigenposition: kleiner blauer Punkt (#1a73e8, r=4.5)
  statt großem grünen mit Ring. **Caddy repariert:** `caddyPositionPlan` schließt den Driver vom
  Boden GRUNDSÄTZLICH aus (harte Regel — Live-Caddy plant nie vom Tee); Modus-Parameter `MP`
  (Sicherheitsabstand vor Gefahren 18/12/8 m, Grün-Zuschlag, Wedge-Layup bei „Sicher“) machen
  Sicher/Ausgewogen/Offensiv in JEDEM Zweig spürbar. `caddyPlan` (Formular) ebenso: Par-3-Ziel &
  Schlägerwahl je Modus, kurzes Par 4 (Sicher legt IMMER, Offensiv geht IMMER), Par-5-Layup-
  Schwelle 165/195/235 m, Tee-Score mit Wedge-Zonen-Bonus + moduls-gewichteter Länge.
  **Uhr+Handy gleichzeitig:** mergeDB vereint Runden-Entwürfe derselben Runde jetzt LOCH-GENAU
  (Basis älterer, gesetzte Felder des neueren drüber — null löscht nichts); während einer
  Live-Runde zieht das Handy alle 60 s (`playSyncTick`, plus visibilitychange) den Repo-Stand,
  merged und übernimmt fremde Eingaben in die laufende Session (`playAdoptDraft`, nur leere Felder).

- **v1.23.0 · 2026-08-06** — **Repo-Wiederherstellung & Referenzstand** (Mehr → Daten):
  (1) Liste der automatischen Repo-Sicherungen der **letzten 7 Tage** — Commit-Historie von
  trainingsdaten.json über die öffentliche GitHub-API (`repoInfo()` leitet owner/repo aus der
  Pages-URL ab, kein Token, Worker unnötig zum Lesen; Cache 10 min, Stand-Abruf via
  raw.githubusercontent.com/<sha>). `restoreRepoPoint()` setzt EXAKT auf einen Punkt zurück
  (kein Merge, Schnappschuss vorher) und bietet an, den Stand per `cloudSave(force)` autoritativ
  für alle Geräte ins Repo zurückzuschreiben. (2) `repoHardPull()`: „Lokal verwerfen & Repo-Stand
  übernehmen“ — harter Pull ohne Merge, lokale Sicherungen werden ignoriert (Schnappschuss als
  Sicherheitsnetz bleibt). (3) **Referenzstand**: `DB.reference={at,score}` reist in den Daten mit;
  `markReference()` schreibt autoritativ; jedes Gerät zeigt via `loadRepoRef/paintRepoRef`, ob es
  auf der Referenz basiert. mergeDB-Ergänzung: reference = zeitlich neuerer gewinnt.

- **v1.22.0 · 2026-08-06** — Bedienungs-Tiefenpflege: **Sheet-System** überarbeitet (`openSheet/closeSheet`):
  neues Sheet startet immer oben (Scroll-Reset), Hintergrund-Seite ist gesperrt solange ein Sheet offen
  ist (`html.sheet-open`), **Android-/Browser-Zurück-Taste schließt das Sheet statt die App zu verlassen**
  (eigener History-Eintrag `_sheetHist` + popstate), **Escape** schließt am Desktop. **Spielmodus:**
  Screen **Wake Lock** (`wakeAcquire/wakeRelease`, Re-Acquire bei visibilitychange) — Display bleibt
  während der Runde an; kurze **Vibration** bei Score/Putts/Penalty-Steppern (`playAdj`, sofern Gerät
  unterstützt). Navigation: aktiver Tab als Pille hervorgehoben + `aria-current`; Toast als
  `role=status`-Live-Region. `lvlChip` mischt jetzt gegen `var(--surface)` statt `#fff` (Dark-Mode-tauglich).

- **v1.21.0 · 2026-08-06** — Design & Usability: **Dark Mode** (Auto/Hell/Dunkel, Umschalter unter
  Mehr → Daten → „Darstellung", `DB.ui.theme` + `applyTheme/setTheme`, folgt bei „Auto" der System-
  einstellung; Karten-SVG bleibt bewusst hell). CSS-Bugfix: `--muted` war nirgends definiert (Snippets/
  Meta-Texte erbten die Volltextfarbe) → jetzt Alias auf `--ink-faint`; Kontrast von `--ink-faint`
  angehoben (Lesbarkeit im Sonnenlicht). Barrierefreiheit: sichtbare `:focus-visible`-Ringe,
  `prefers-reduced-motion` respektiert, größere Tippflächen für Lösch-/Zurücksetzen-Links, keine
  Textauswahl auf Nav/Buttons bei Long-Press. Layout-Fix: `#subnav` klebt jetzt UNTER dem Header
  (CSS-Var `--hdr-h` via `trackHeaderHeight`/ResizeObserver) statt ihn beim Scrollen zu überlagern.
  `overscroll-behavior` gegen versehentliches Pull-to-Refresh im Spielmodus; iOS-PWA-Metatags
  (Standalone/Statusleiste), `color-scheme`-Meta.

- **v1.20.0 · 2026-08-06** — Spielmodus-Layout überarbeitet: oben Kennzahlen-Grid (Score/Putts/Penalty
  als Stepper + Tee-Ergebnis/Approach-Distanz/Pin-Dist nach Approach) und über-Par-Badge; Live-Distanzen
  IMMER an (Toggle entfernt, `playStartLive` in `renderPlay`, Watcher stoppt via `PLAY.live/active`);
  unten „Auto-Loch (GPS)"-Toggle (`playToggleAuto`) + Schläge-Tracken direkt über „Beenden & speichern".
  Karte größer (bis 72vh) und initial auf die ganze Bahn gefittet (`playMapInitView` ohne GPS-Punkt).
  Verschobene Felder aus `playDetailsHtml` entfernt.

- **v1.19.0 · 2026-08-06** — App-Versionsnummer eingeführt: `APP_VERSION` (getrennt von `SEED.version`!)
  wird auf „Heute" rechts neben dem Datum angezeigt. Changelog-Einträge tragen jetzt Versionsnummern;
  **bei jeder Änderung `APP_VERSION` erhöhen** (Minor für Features, Patch für Fixes) und Changelog-Eintrag
  mit dieser Version anlegen.

- **v1.18.0 · 2026-08-06** — Entwurf-Verwerfen dauerhaft (Bug: verworfener Watch-Entwurf kam nach Sync/Neuladen
  zurück). Tombstone `DB.ui.draftDiscardedTs`: `discardDraft` merkt den ts des verworfenen Entwurfs;
  `getDraftAny` blendet Entwürfe mit ts ≤ Tombstone aus; `mergeDB` unterdrückt sie beim Zusammenführen
  (auch aus dem Repo) und führt die Marke als spätere von beiden Geräten fort. `ui` wird im Merge sauber
  vereinigt.

- **v1.17.0 · 2026-08-06** — Sync fail-safe: `cloudSave` lädt/merged das Repo VOR dem Push; ist das Repo nicht
  lesbar (offline, falscher Ort, 404), wird NICHT gepusht (Status „blocked") statt lokal zu überschreiben
  – behebt „lokaler Stand überschreibt Repo mit alten Daten". Nur `force` (Erzwingen) pusht ohne Merge.
  Bild-Sync-Steuerung zusätzlich im Daten-Reiter (`renderData`): Checkbox `cfgWikiImg`, lokale Bildanzahl,
  Zeitstempel „zuletzt synchronisiert" (`golfdb_wikiImgLastSync`, gesetzt in `wikiImgPush/Pull`), Button.

- **v1.16.0 · 2026-08-06** — Wissen-Bilder optionaler Repo-Sync (Variante 2): eigene Datei `wissen-bilder.json`
  (unabhängig von trainingsdaten.json → Uhr bleibt schlank). `wikiImgPull` (Start, hydratisiert fehlende
  Bilder in IDB), `wikiImgPush`/`wikiImgSchedulePush` (Union repo∪lokal, nichts geht verloren),
  Toggle+Button in Mehr→Wissen (`wikiToggleImgSync`/`wikiImgSyncNow`, Flag `golfdb_wikiImgSync`). POST
  an bestehenden Worker mit `{path:"wissen-bilder.json",data}`. Erweiterter Worker (worker.js) schreibt
  beide Dateien per Whitelist, abwärtskompatibel.

- **v1.15.0 · 2026-08-06** — Wissensdatenbank („Wissen") eingebaut: Artikel (Titel/Kategorie/Tags/Markdown-Text
  + Bilder) unter Mehr → Wissen. `DB.wiki={cats,articles}` (Text synct via Merge mit); Bilder LOKAL in
  eigenem IndexedDB-Store `wikimg` (v2, nicht im Sync-JSON → schont trainingsdaten.json/Uhr), referenziert
  im Text als `[[img:ID]]`. Funktionen `renderWiki/openWikiArticle/openWikiEdit/wikiSaveArticle/
  wikiAddImageFromInput` (Kompression auf ~1000px JPEG), `wikiExport/wikiImportFile` (Bundle mit Bildern
  für Geräte-Übertragung), `mdToHtmlWiki`/`wikiHydrateImages`. Merge um `wiki` erweitert.

- **v1.14.0 · 2026-08-05** — Abschläge aus hochgeladenen Scorekarten ergänzt: Timmendorf Nord (Schwarz/Blau/Rot),
  Timmendorf Süd (Rot), Brodauer Mühle (Rot/Damen); Golf Club Fehmarn neu (Gelb/Rot/Orange).
  `EXTRA_TEES`/`NEW_COURSES2` + `seedCourseTees2()` (idempotent über `DB.ui.extraTeesSeed`), fügt nur
  fehlende Tees hinzu. Fehmarn-CR/Slope für Gelb aus DGV-Quelle (Karte ohne CR/Slope), Rot/Orange offen.

- **v1.13.0 · 2026-08-05** — Vier Ostsee-Plätze angelegt (offizielle DGV-Loch-Daten Par/SI/Länge/CR/Slope):
  GCC Hohwachter Bucht (A/B, Par 73), Golf-Club Curau (Par 72), GC Ostseebad Grömitz (Residenz, Par 73),
  Lübeck-Travemünder GK (A/B, Par 73). `EXTRA_COURSES` + `seedExtraCourses()` (idempotent über
  `DB.ui.extraCoursesSeed`), aufgerufen in `ensureDefaults`. Tee „Gelb" (Herren).

- **v1.12.0 · 2026-08-05** — Spielmodus-Karte interaktiv: Pinch-Zoom + Ziehen (Pan) + Zoom-/Fit-Buttons; öffnet
  initial eng aufs aktuelle Loch gefittet (`playMapInitView` an Box-Seitenverhältnis). Karte in eigenem
  persistentem Container (`#playMapWrap/#playMapSvg`), `courseSVG` liefert `body` separat, Live-Ticks
  aktualisieren nur den Inhalt (Zoom/Pan bleiben via `PLAY.mapView`). Funktionen `playMapRender/Tick/
  Zoom/Fit/Bind/Clamp`.

- **v1.11.0 · 2026-08-05** — Sync-Datenverlust behoben: Konfliktlösung von „neuerer Zeitstempel überschreibt"
  auf **Merge (Union nach ID)** umgestellt. `mergeDB`/`_mergeArr` vereinigen rounds/competitions/
  tests/fitness/lm/notes/courses/… nach ID (reicherer Eintrag gewinnt bei Konflikt), respektieren
  Notiz-Tombstones und behalten den neueren Pin/Draft. `cloudLoad`/`cloudLoadManual`/`cloudSave`
  mergen jetzt statt zu überschreiben – kein Stand verliert mehr Daten, unabhängig vom Zeitstempel.

- **v1.10.0 · 2026-08-05** — Spielmodus-Umbau: alle Loch-Detailfelder direkt im Spielmodus (ausklappbar,
  `playDetailsHtml`/`playField`/`playSel`/`playNum`); Live-Distanz-Bedienelemente erscheinen NUR bei
  aktivem Live (F/M/B, Fahne, Wetter, Caddy, Karte, Ringe – gated über `PLAY.live`); separater
  „GIR"-Schalter entfernt (jetzt in den Details); Schlagaufnahme in-place ohne Kartenwechsel
  (`playRecBegin/Club/Stop`, `PLAY.rec`, Live-Meter-Anzeige, Schläger-Auswahl beim Start).

- **v1.9.0 · 2026-08-05** — Distanz-Ringe zusätzlich im Spielmodus; Ringe global abschaltbar (`ringsOn`/
  `toggleRings`, Einstellung in `DB.ui.rings`). Caddy: Gebäude = Hindernis (Layup davor wie OB),
  „Hohes Rough" = schwerer Lie (`LIE_F.highrough`), automatische Lie-Erkennung `lieAt(geo,here)`.

- **v1.8.0 · 2026-08-05** — GeoJSON-Import behält jetzt ALLE Objekte (Gebäude/Wege/Parkplätze/„other");
  „Hohes Rough" als Editor-Fläche; Distanz-Ringe (50–250 m) um die Position als Entfernungsmesser-Overlay.

- **v1.7.0 · 2026-08-05** — Doku um Abschnitte 14–23 + Changelog erweitert (Versionen/Migration, Trennung
  Daten↔Berechnung, Integrität, Sync/Konfliktlösung, Export/Import-Garantie, Modul-, Performance-,
  Erweiterungs-, KI-Regeln, Wetter).

- **v1.6.0 · 2026-08-05** — Wetter: Open-Meteo-Abruf; Anzeige (Heute/Caddy/Runden-Detail); Wind & Temperatur
  im Caddy („spielt wie"); Böen-Warnung im Spielmodus; Speicherung je Runde.

- **v1.5.0 · 2026-08-05** — Caddy berücksichtigt Wald & Bäume; „Gefahren je Loch" → „Hinweise je Loch"
  (ergänzt Karte statt Dopplung: Tipp/bevorzugte Seite); Platz-Export in Plätze-Übersicht.

- **v1.4.0 · 2026-08-05** — Vegetation aus GeoJSON (Wald/Bäume/Hecken) sichtbar + selbst zeichenbar;
  Overpass-Import erweitert; Grün-Tiefe/Breite je Loch.

- **v1.3.0 · 2026-08-05** — Eingebettete Entwickler-/KI-Doku (STOP-Banner + `#devdocs` + In-App-Ansicht).

- **v1.2.0 · 2026-08-04** — Datensicherheit: globaler Sync-Status; IndexedDB als lokaler Speicher
  (localStorage-Limit entschärft) + sichtbare Speicheranzeige.

- **v1.1.0 · 2026-08-04** — Spielmodus (Loch-für-Loch, Live F/M/B, Caddy, GPS-Loch-Erkennung); tagesgenaue
  Fahne (Pin); Spielmodus-Einstieg auf „Heute".

- **v1.0.0 · 2026-08-04** — Turnier-Formular/Detail um alle Felder erweitert (Par/GBE/HCP/Stableford/Tees/18-9).
===DOC:END===

