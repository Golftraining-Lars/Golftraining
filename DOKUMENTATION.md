# Golftraining PWA — Dokumentation

> Lebende Doku für die bestehende Web-App (`index.html`). Sie wird Bereich für Bereich
> ergänzt. **Teil 1** beschreibt Daten & Sicherung — den kritischsten Bereich.
> Für KI-Assistenten gilt: Die Regeln unter „Unverhandelbar" nie verletzen.

---

## Teil 1 — Daten & Datensicherung

### Wo die Daten liegen
- **Alles** steckt in einem JavaScript-Objekt `DB` (Runden, Turniere, Tests, Fitness,
  Plätze/Geodaten, Notizen, Profil, Schlagweiten …).
- **Lokaler Speicher:** `DB` wird als JSON unter dem localStorage-Schlüssel `golfdb`
  gespeichert (Funktion `persist()`).
- **Online-Sicherung (optional):** Über einen Cloudflare-Worker wird `DB` als Datei
  `trainingsdaten.json` ins GitHub-Repo geschrieben. Beim Start lädt die App diese Datei
  (`cloudLoad()`), wenn sie mehr/neuere Daten enthält als der lokale Stand.
- **Austauschformat:** Export/Import als JSON-Datei (`doExport` / `doImport`).

### Der Sicherungsstatus (Kopfzeile)
Oben rechts zeigt ein Status-Pill **immer sichtbar**, wie sicher deine Daten gerade sind.
Tippen öffnet den Bereich **Mehr → Daten**.

| Anzeige | Bedeutung |
|---|---|
| `● nur lokal` | Cloud nicht eingerichtet. Daten liegen lokal + in automatischen Backups. |
| `☁ ausstehend` | Lokal gespeichert, Cloud-Sicherung steht noch aus (läuft gleich). |
| `↑ sichert…` | Sicherung ins Repo läuft gerade. |
| `☁ HH:MM` | Erfolgreich ins Repo gesichert (Uhrzeit der letzten Sicherung). |
| `⚠ offline` | Keine Verbindung — wird beim nächsten Mal automatisch nachgeholt. |
| `⚠ nicht gesichert` | Server-Fehler beim Sichern. |
| `⚠ Konflikt` | **Schutz greift:** Repo hat mehr Daten — wurde NICHT überschrieben. Über „☁ Aus Repo laden" prüfen. |

### Schutz vor Datenverlust (die Guards)
Diese Schutzmechanismen verhindern das frühere Problem (ganze Datei überschrieben → Daten weg):

1. **Empty-Guard:** Ein fast leerer Stand wird nie automatisch ins Repo geschrieben.
2. **Repo-hat-mehr-Guard:** Ist der Online-Stand deutlich größer/neuer als der lokale, wird
   **nicht** überschrieben (Status `⚠ Konflikt`). Manuelles Speichern fragt dann nach.
3. **Automatische lokale Backups:** Bei jeder Änderung legt `snapshot()` einen Schnappschuss
   an (rollierend). Wiederherstellbar unter **Mehr → Daten → Automatische Sicherungen**.
4. **Snapshot vor riskanten Aktionen:** Vor „Aus Repo laden" wird der aktuelle Stand gesichert.

### Wiederherstellung — was tun, wenn etwas fehlt
1. **Mehr → Daten → Automatische Sicherungen** → gewünschten Schnappscheit „Wiederherstellen".
2. **Mehr → Daten → „☁ Aus Repo laden"** → holt den Online-Stand zurück (mit Vergleich +
   Rückfrage; sichert vorher den lokalen Stand).
3. **Git-Historie:** Da jede Sicherung ein Commit ist, liegen ältere `trainingsdaten.json`
   in der GitHub-Historie der Datei („History") und lassen sich dort wiederherstellen.
4. **Import:** Eine zuvor exportierte JSON-Datei über **Mehr → Daten → Import** einspielen.

### Empfohlene Routine
- Nach größeren Sessions einmal **Export** (Datei lokal ablegen) — unabhängiges Backup.
- Bei `⚠ Konflikt`: erst „Aus Repo laden", prüfen, dann bewusst speichern.
- Beim Wechsel zwischen Geräten: erst laden lassen, dann arbeiten.

### Unverhandelbar (für jede künftige Änderung / KI)
- **Nie** den gesamten Datenbestand ungeprüft ins Repo schreiben — die Guards in `cloudSave`
  müssen erhalten bleiben.
- **Nie** einen vollen/neueren Stand mit einem leeren/kleineren überschreiben.
- Löschen von Notizen erfolgt über den **Papierkorb** (90-Tage-Auto-Löschung), nicht hart.
- Jede datenverändernde Aktion ruft `persist()` (schreibt lokal, legt Backup an, plant Sync).

### Bekannte Grenzen (ehrlich)
- **localStorage-Limit:** Bilder (Notizen) und Geodaten können den Speicher füllen. Nächster
  geplanter Robustheits-Schritt: Auslagern großer Daten nach IndexedDB.
- Die Online-Sicherung braucht eine eingerichtete Worker-URL + Schlüssel (Mehr → Daten).
  Ohne diese läuft alles rein lokal (Status `● nur lokal`) — dann ist der regelmäßige
  **Export** dein wichtigstes Backup.

---

## Nächste Doku-Teile (geplant)
- Teil 2 — App-Aufbau (Views, Navigation, Rendering).
- Teil 3 — Runden, Spielmodus, Caddy & Geo/Distanzen.
- Teil 4 — Änderungs-Leitfaden für KI-Assistenten (was wo ändern, was nie anfassen).
