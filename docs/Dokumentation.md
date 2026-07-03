# Übung 2: Communication & The Web — Dokumentation

**Name:** _[Dein Name]_
**Matrikelnummer:** _[Deine Matrikelnummer]_
**Modul:** Engineering verteilter Anwendungen — TU Berlin

> Hinweis zum Ausführen: Das Projekt ist ein Maven-Projekt und benötigt **JDK 25**
> (`mvn test` baut, generiert die gRPC-Klassen aus den `.proto`-Dateien und führt
> die Tests aus). Alle verwendeten Web-APIs sind frei und **ohne API-Schlüssel**
> nutzbar.

---

## 1 gRPC (8 Punkte)

### Gewählter Anwendungsfall

Eine Anwendung zur **Verteilung von Aufgaben in einem Team**. Ein zentraler
gRPC-Server verwaltet alle Tasks. Es gibt zwei Arten von Clients:

- **Boss-Client** (`BossService`): erstellt und weist Aufgaben zu und lässt sich
  Metriken (offen / in Arbeit / erledigt) ausgeben.
- **Member-Client** (`MemberService`): lässt sich die eigenen Aufgaben ausgeben
  und hakt sie ab.

Beide Client-Arten arbeiten auf **derselben** Datenhaltung im Server, sind also
nicht unabhängig voneinander: Was der Boss erstellt, sieht das Team-Mitglied;
was das Mitglied abhakt, taucht in den Metriken des Bosses auf.

### Anforderung 1 — mehrere proto-Dateien (2P)

Die Kommunikation ist auf drei `.proto`-Dateien aufgeteilt
(`src/main/proto/`):

- **`common.proto`** — gemeinsame Nachrichten und der `enum TaskStatus`
  (`OPEN`, `IN_PROGRESS`, `DONE`) sowie `Task` und `TaskList`.
- **`boss.proto`** — `service BossService` mit `CreateTask` und `GetMetrics`
  samt zugehöriger Request/Response-Messages.
- **`member.proto`** — `service MemberService` mit `GetMyTasks` und
  `CompleteTask`.

```proto
// common.proto (Ausschnitt)
enum TaskStatus { OPEN = 0; IN_PROGRESS = 1; DONE = 2; }

message Task {
  int64 taskID = 1;
  string title = 2;
  string description = 3;
  string memberID = 4;
  TaskStatus status = 5;
}
```

```proto
// boss.proto (Ausschnitt)
service BossService {
  rpc CreateTask (CreateTaskRequest) returns (Task);
  rpc GetMetrics (MetricsRequest) returns (MetricsResponse);
}
```

> _[Screenshot-Vorschlag: die drei .proto-Dateien nebeneinander.]_

### Anforderung 2 — Server und Clients implementiert (2P)

- **Server:** `TaskServer` startet einen gRPC-Server auf Port 8980 und
  registriert **beide** Services auf **einer** gemeinsamen `Map`.

```java
Map<Long, Task> tasks = new ConcurrentHashMap<>();
Server server = Grpc.newServerBuilderForPort(PORT, InsecureServerCredentials.create())
        .addService(new BossServiceImpl(tasks))
        .addService(new MemberServiceImpl(tasks))
        .build()
        .start();
```

- **Boss-Client** (`BossClient`) ruft `createTask` auf, **Member-Client**
  (`MemberClient`) ruft `getMyTasks` und `completeTask` auf.

> _[Screenshot-Vorschlag: Konsole mit laufendem Server + einem Client-Aufruf.]_

### Anforderung 3 — interne Datenhaltung, Clients nicht unabhängig (2P)

Die Datenhaltung ist eine `ConcurrentHashMap<Long, Task>`, die beim Serverstart
erzeugt und an **beide** Service-Implementierungen übergeben wird. Neue IDs
werden über einen `AtomicLong` vergeben.

```java
// BossServiceImpl
private Map<Long, Task> tasks;              // dieselbe Map wie im MemberService
private AtomicLong nextId = new AtomicLong(1);
```

Damit sind die Clients gekoppelt: Der Boss legt Tasks an, das Team-Mitglied liest
und verändert sie, und die Metriken des Bosses spiegeln die Änderungen der
Mitglieder wider.

### Anforderung 4 — JUnit-Test für Thread-Safety (2P)

Der Test `ThreadSafetyTest` startet einen echten Server (auf einem freien Port)
und lässt **gleichzeitig** viele Clients unterschiedlicher Art auf ihn los:

- **8 Boss-Clients** erstellen parallel je 50 Tasks (= 400 Tasks),
- **4 Member-Clients** haken parallel ihre Tasks ab,
- **4 Metrik-Clients** lesen währenddessen laufend über die Datenhaltung.

Alle Threads starten über eine `CyclicBarrier` gleichzeitig, um maximale
Nebenläufigkeit zu erzeugen.

```java
CyclicBarrier startR1 = new CyclicBarrier(BOSSES + METRIC_READERS);
...
startR1.await(); // alle Boss-Clients starten gleichzeitig
Task created = stub.createTask(...);
assertTrue(createdIds.add(created.getTaskID()),
        "Doppelte Task-ID vergeben (nicht thread safe)");
```

Der Test beweist die Thread-Safety über drei Zusicherungen:

1. **Keine ID-Kollisionen:** 400 erstellte Tasks ⇒ 400 eindeutige IDs
   (belegt den korrekten Einsatz von `AtomicLong`).
2. **Keine verlorenen Schreibzugriffe:** `store.size() == 400`
   (belegt den korrekten Einsatz der `ConcurrentHashMap`).
3. **Sichere parallele Iteration:** Die Metrik-Clients lesen ständig über die
   Map, während geschrieben wird — mit einer gewöhnlichen `HashMap` gäbe es hier
   eine `ConcurrentModificationException`.

**Ergebnis:** `Tests run: 1, Failures: 0, Errors: 0`.

> _[Screenshot-Vorschlag: grüner Testlauf von `ThreadSafetyTest`.]_

---

## 2 HTTP-Client (5 Punkte)

### Gewählter Anwendungsfall

Ein **Wetter-Vergleich** (`WeatherClient`). Der Nutzer gibt mehrere Städte über
die Konsole ein; das Programm ermittelt für jede Stadt das aktuelle Wetter und
erstellt am Ende eine Rangliste nach aktueller Temperatur.

Verwendete APIs (beide frei, ohne Schlüssel — [open-meteo.com](https://open-meteo.com)):

- **API 1 — Geocoding:** `https://geocoding-api.open-meteo.com/v1/search`
  wandelt einen Ortsnamen in Koordinaten um.
- **API 2 — Forecast:** `https://api.open-meteo.com/v1/forecast`
  liefert zu Koordinaten die aktuellen und stündlichen Temperaturen.

### Anforderung 1 & 2 — zwei APIs, mind. eine mehrfach aufgerufen (1P)

Für **jede** eingegebene Stadt werden **beide** APIs aufgerufen. Bei mehreren
Städten werden beide APIs also mehrfach aufgerufen (Schleife über die
Konsoleneingaben).

```java
while (true) {
    String city = scanner.nextLine().trim();
    if (city.isEmpty()) break;
    GeoResult place = client.geocode(city);            // API 1
    ForecastResponse fc = client.forecast(place.latitude, place.longitude); // API 2
    ...
}
```

### Anforderung 3 — nicht-statische Parameter (2P)

Die Parameter sind **nicht** statisch:

- Die **Ortsnamen** werden zur Laufzeit von der Konsole eingelesen und ändern
  sich bei jeder Ausführung (`Scanner` auf `System.in`).
- Die **Koordinaten** für API 2 stammen dynamisch aus der Antwort von API 1.
- Zusätzlich hängt das Ergebnis vom **Ausführungszeitpunkt** ab (aktuelle
  Temperatur bzw. `current`-Wert).

> _[Screenshot-Vorschlag: Konsole, in der nach Städten gefragt wird, plus die
> Codezeilen mit `scanner.nextLine()`, in denen der Input verwendet wird.]_

### Anforderung 4 — Antworten auslesen und weiterverarbeiten (2P)

Die Antworten werden **nicht nur ausgegeben**, sondern weiterverarbeitet:

1. **Verkettung (Output → Input):** Die Koordinaten aus der Antwort von API 1
   sind die Eingabeparameter für API 2.
2. **Aggregation:** Aus den Stundenwerten von API 2 werden **Minimum, Maximum
   und Durchschnitt** der Temperatur berechnet.
3. **Ranking:** Über alle Städte hinweg wird nach aktueller Temperatur sortiert
   und die wärmste Stadt bestimmt.

```java
CityWeather summarize(GeoResult place, ForecastResponse forecast) {
    List<Double> temps = forecast.hourly.temperature_2m;
    double min = temps.get(0), max = temps.get(0), sum = 0;
    for (double t : temps) { min = Math.min(min, t); max = Math.max(max, t); sum += t; }
    double avg = sum / temps.size();
    return new CityWeather(label, forecast.current.temperature_2m, min, max, avg);
}
```

Die Verarbeitungslogik ist zusätzlich durch den Offline-Test
`WeatherClientTest` (3 Tests, mit echten Beispiel-Responses) abgesichert.

> _[Screenshot-Vorschlag: Konsolenausgabe mit dem Ranking mehrerer Städte.]_

---

## 3 TU User Page (2 Punkte)

**Link zur Webseite:** _[https://www.user.tu-berlin.de/DEIN-KONTO/ hier eintragen]_

### Inhalt und valides HTML (1P)

Die Seite (`userpage/index.html`) stellt mein Übungsprojekt vor (gRPC-System und
HTTP-Client, Client-Server-Modell). Sie ist valides HTML5 (`<!DOCTYPE html>`,
`lang="de"`, korrekt geschachtelte Tags) und enthält echten inhaltlichen Text.

### JavaScript für Interaktivität (1P)

Die Seite nutzt JavaScript für zwei Funktionen:

1. Eine **Live-Uhr**, die jede Sekunde das aktuelle Datum und die Uhrzeit
   aktualisiert (`setInterval`).
2. Ein **interaktives Task-Board**: Man kann Aufgaben hinzufügen, per Klick als
   erledigt markieren und sieht einen live aktualisierten Zähler — komplett im
   Browser, als kleines Abbild des gRPC-Beispiels.

```javascript
form.addEventListener("submit", (event) => {
    event.preventDefault();
    tasks.push({ title: input.value.trim(), done: false });
    render();  // Liste + Zähler neu aufbauen
});
```

> _[Screenshot-Vorschlag: die veröffentlichte Seite mit Live-Uhr und Task-Board.]_

---

## Anhang — Ausführen

```bash
# Alle Tests bauen und ausführen (gRPC-Thread-Safety + HTTP-Verarbeitung):
mvn test
```

Die einzelnen Programme werden am einfachsten aus der IDE über ihre
`main`-Methode gestartet:

- `com.tub.TaskServer` — startet den gRPC-Server (danach `BossClient` /
  `MemberClient` starten).
- `com.tub.web.WeatherClient` — der HTTP-Client (fragt Städte über die
  Konsole ab).
