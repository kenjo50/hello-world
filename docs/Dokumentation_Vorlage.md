# Übung 2: Communication & The Web — Dokumentation

> **So benutzt du diese Vorlage:** Ersetze jeden Text in `[eckigen Klammern]`
> durch deinen eigenen Inhalt. In den Klammern steht jeweils genau, **was**
> dort hin muss, um die Punkte zu bekommen. Lösche am Ende alle Klammer-Hinweise
> und diesen grauen Kasten. Exportiere das Dokument als **PDF (max. 10 Seiten)**.
>
> **Wichtig (Zitat aus der Aufgabe):** Wenn ein bewertungsrelevanter Aspekt nicht
> in der Doku steht, kann er nicht bewertet werden — auch wenn er im Code ist.
> Zeige also für **jeden** Punkt unten den Beleg (Code-Ausschnitt + Screenshot).

**Name:** [Dein vollständiger Name]
**Matrikelnummer:** [Deine Matrikelnummer]
**Modul:** Engineering verteilter Anwendungen — TU Berlin
**Ausführung:** Maven-Projekt, benötigt JDK 25 (`mvn test`). Alle Web-APIs sind frei und ohne API-Schlüssel nutzbar.

---

## 1 gRPC (8 Punkte)

### Anwendungsfall

[Beschreibe in 3–5 Sätzen deinen Anwendungsfall: Aufgabenverteilung im Team.
Nenne den Server als zentralen Knoten und die **zwei Client-Arten** (Boss-Client
und Member-Client) und was jede Art tun kann. Ein Satz dazu, warum die
Client-Arten **nicht unabhängig** sind — sie teilen sich die Tasks im Server.]

### Anforderung 1 — mehrere proto-Dateien mit eigenen messages/services (2P)

[Erkläre in 2–3 Sätzen, dass die Kommunikation auf **drei** .proto-Dateien
aufgeteilt ist: `common.proto` (gemeinsame Messages `Task`, `TaskList`, Enum
`TaskStatus`), `boss.proto` (`service BossService`) und `member.proto`
(`service MemberService`).]

[CODE-AUSSCHNITT EINFÜGEN: den Service-Block aus `boss.proto` und `member.proto`
sowie die `Task`-Message aus `common.proto`. → belegt „eigene messages/services".]

[SCREENSHOT EINFÜGEN: die drei .proto-Dateien im Projektbaum oder nebeneinander
im Editor.]

### Anforderung 2 — Clients und Server implementiert (2P)

[Erkläre in 2–3 Sätzen: `TaskServer` startet den gRPC-Server und registriert
**beide** Services; `BossClient` und `MemberClient` verbinden sich als die zwei
Client-Arten.]

[CODE-AUSSCHNITT EINFÜGEN: den Server-Start aus `TaskServer` (die
`addService(...)`-Zeilen für beide Services).]

[SCREENSHOT EINFÜGEN: Konsole mit laufendem Server + Ausgabe eines Client-Aufrufs
(z. B. „Task erstellt!" vom BossClient und/oder „Abgehakt: …" vom MemberClient).]

### Anforderung 3 — interne Datenhaltung, Clients nicht unabhängig (2P)

[Erkläre in 2–3 Sätzen: Die Datenhaltung ist **eine** gemeinsame
`ConcurrentHashMap<Long, Task>`, die beim Serverstart erzeugt und an **beide**
Service-Implementierungen übergeben wird; IDs werden über einen `AtomicLong`
vergeben. Betone: Was der Boss anlegt, sieht/verändert das Member — die
Client-Arten sind dadurch gekoppelt.]

[CODE-AUSSCHNITT EINFÜGEN: die Erzeugung der `ConcurrentHashMap` in `TaskServer`
und wie dieselbe Map an `new BossServiceImpl(tasks)` und
`new MemberServiceImpl(tasks)` übergeben wird. → belegt „nicht unabhängig".]

### Anforderung 4 — JUnit-Test für Thread-Safety (2P)

[Erkläre in 3–5 Sätzen die Tests in `ThreadSafetyTest`: Test 1
(`taskAnlegenUndAbhaken`) prüft die Grundfunktion (Task anlegen + abhaken →
Status `DONE`). Test 2 (`mehrereClientsGleichzeitig`) zeigt die Thread-Safety:
**5 Boss-Clients** legen **gleichzeitig** je 20 Tasks an (= 100 Tasks), jeder in
einem eigenen `Thread` (mit `start()` gestartet, mit `join()` abgewartet).
Nenne den Beweis: Am Ende liegen genau 100 Tasks in der Map — wäre der Server
nicht thread safe, gingen Tasks verloren oder IDs würden doppelt vergeben, dann
wäre die Anzahl kleiner. Das belegt `ConcurrentHashMap` (keine verlorenen
Schreibzugriffe) und `AtomicLong` (eindeutige IDs).]

[CODE-AUSSCHNITT EINFÜGEN: den Kern von Test 2 — die `new Thread(...)`-Schleife
mit `stub.createTask(...)`, die `start()`/`join()`-Schleifen und das
abschließende `assertEquals(100, store.size())`.]

[SCREENSHOT EINFÜGEN: der **grüne** Testlauf, auf dem
„Tests run: 2, Failures: 0, Errors: 0" für `ThreadSafetyTest` zu sehen ist.]

---

## 2 HTTP-Client (5 Punkte)

### Anwendungsfall

[Beschreibe in 2–4 Sätzen den Anwendungsfall „Wetter-Vergleich" (`WeatherClient`):
Der Nutzer gibt mehrere Städte über die Konsole ein; für jede Stadt werden die
Koordinaten ermittelt und daraus das Wetter geladen; am Ende wird die wärmste
Stadt bestimmt. Nenne die zwei APIs mit Namen:
Open-Meteo **Geocoding** und Open-Meteo **Forecast** (beide ohne API-Schlüssel).]

### Anforderung 1 & 2 — zwei APIs, mind. eine mehrfach aufgerufen (1P)

[Erkläre in 2–3 Sätzen: Für **jede** eingegebene Stadt werden **beide** APIs
aufgerufen; bei mehreren Städten also mehrfach (Schleife über die
Konsoleneingaben).]

[CODE-AUSSCHNITT EINFÜGEN: die `while`-Schleife aus `main`, in der pro Stadt
`client.geocode(city)` (API 1) und `client.forecast(...)` (API 2) aufgerufen
werden.]

### Anforderung 3 — nicht-statische Parameter (2P)

[Erkläre in 2–3 Sätzen, warum die Parameter **nicht statisch** sind: Die
Ortsnamen werden zur Laufzeit von der Konsole eingelesen (ändern sich pro Lauf);
die Koordinaten für API 2 stammen dynamisch aus der Antwort von API 1;
zusätzlich hängt das Ergebnis vom Ausführungszeitpunkt ab (aktuelle Temperatur).]

[SCREENSHOT 1 EINFÜGEN: die Konsole, in der nach Städten gefragt wird und du
mehrere Städte eintippst.]

[SCREENSHOT 2 (oder CODE-AUSSCHNITT) EINFÜGEN: die Codezeilen, in denen der
eingelesene Input **verwendet** wird — `scanner.nextLine()` und die Übergabe an
`client.geocode(city)`. → Genau das verlangt die Aufgabe für die volle Punktzahl:
„Screenshot der Konsole, wo nach Input gefragt wird, UND Screenshot der
Codezeilen, wo dieser Input später verwendet wird".]

### Anforderung 4 — Antworten auslesen und weiterverarbeiten (2P)

[Erkläre in 3–4 Sätzen die Weiterverarbeitung (nicht nur ausgeben!):
(1) **Verkettung** — die Koordinaten aus der Antwort von API 1 sind die
Eingabe für API 2;
(2) **Aggregation** — aus den Stundenwerten werden Min/Max/Durchschnitt mit einer
`for`-Schleife berechnet;
(3) **Vergleich** — über alle eingegebenen Städte hinweg wird per Schleife die
Stadt mit der höchsten aktuellen Temperatur bestimmt.]

[CODE-AUSSCHNITT EINFÜGEN: die Methode `summarize(...)` (Min/Max/Durchschnitt)
UND die Zeile, in der `place.latitude/longitude` aus API 1 an `client.forecast(...)`
für API 2 übergeben werden (Verkettung).]

[SCREENSHOT EINFÜGEN: die Konsolenausgabe mit den Temperaturen mehrerer Städte
und der Zeile „Am waermsten ist es gerade in …".]

---

## 3 TU User Page (2 Punkte)

**Link zur veröffentlichten Webseite:** https://user.tu-berlin.de/oliver5/

### Inhalt und valides HTML (1P)

[Beschreibe in 2–3 Sätzen, was die Seite inhaltlich zeigt (dein Übungsprojekt:
gRPC-System + HTTP-Client, Client-Server-Modell). Erwähne, dass es valides HTML5
ist (`<!DOCTYPE html>`, `lang="de"`, korrekt geschachtelte Tags).]

[SCREENSHOT EINFÜGEN: die **veröffentlichte** Seite im Browser (mit sichtbarer
URL in der Adressleiste — belegt, dass sie wirklich online ist).]

### JavaScript für Interaktivität (1P)

[Erkläre in 2–3 Sätzen die zwei JS-Funktionen: eine **Live-Uhr**, die mit
`setInterval` jede Sekunde die Uhrzeit aktualisiert, und einen **Button mit
Klick-Zähler**, der bei jedem Klick den Zähler erhöht und den Text aktualisiert.]

[CODE-AUSSCHNITT EINFÜGEN: den `<script>`-Teil aus deiner index.html — die
Live-Uhr mit `setInterval(...)` und den Klick-Handler des Buttons.]

[SCREENSHOT EINFÜGEN: die veröffentlichte Seite im Browser mit sichtbarer URL
`user.tu-berlin.de/oliver5/`, Live-Uhr und Button-Zähler.]

---

## Anhang — Ausführen (optional, aber hilfreich)

[Kurzer Hinweis, wie man das Projekt startet:
- `mvn test` — baut alles und führt beide Tests aus (gRPC-Thread-Safety + HTTP-Verarbeitung).
- `com.tub.TaskServer` aus der IDE starten, dann `BossClient` / `MemberClient`.
- `com.tub.web.WeatherClient` aus der IDE starten (fragt Städte über die Konsole ab).]
