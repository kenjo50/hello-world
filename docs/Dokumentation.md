# Übung 2: Communication & The Web — Dokumentation

**Name:** Oliver Grabka
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

Die Tests stehen in `ThreadSafetyTest`. `@BeforeEach` startet für jeden Test
einen echten Server auf einem freien Port, der sich eine gemeinsame
`ConcurrentHashMap` mit beiden Services teilt.

- **`taskAnlegenUndAbhaken`** prüft die Grundfunktion: Der Boss-Client legt einen
  Task an, der Member-Client hakt ihn ab, und der Status ist danach `DONE`.
- **`mehrereClientsGleichzeitig`** zeigt die Thread-Safety: **5 Boss-Clients**
  legen **gleichzeitig** je 20 Tasks an (= 100 Tasks). Jeder Client läuft in einem
  eigenen `Thread`; alle werden mit `start()` gestartet und mit `join()`
  abgewartet.

```java
Thread[] threads = new Thread[anzahlThreads];
for (int i = 0; i < anzahlThreads; i++) {
    threads[i] = new Thread(new Runnable() {
        public void run() {
            // jeder Thread ist ein eigener Client mit eigener Verbindung
            for (int j = 0; j < tasksProThread; j++) {
                stub.createTask(request);
            }
        }
    });
}
for (int i = 0; i < anzahlThreads; i++) threads[i].start();
for (int i = 0; i < anzahlThreads; i++) threads[i].join();

// Genau 5 * 20 = 100 Tasks müssen in der Map liegen.
assertEquals(100, store.size());
```

Wäre der Server **nicht** thread safe, würden bei den parallelen Zugriffen Tasks
verloren gehen oder IDs doppelt vergeben — dann läge die Anzahl **unter** 100 und
der Test schlüge fehl. Dass am Ende genau 100 Tasks vorhanden sind, belegt den
korrekten Einsatz von `ConcurrentHashMap` (keine verlorenen Schreibzugriffe) und
`AtomicLong` (keine doppelten IDs). Zur Kontrolle wird dieselbe Zahl auch über die
Metriken-API (`getMetrics`) geprüft.

**Ergebnis:** `Tests run: 2, Failures: 0, Errors: 0`.

> _[Screenshot-Vorschlag: grüner Testlauf von `ThreadSafetyTest` (beide Tests).]_

---

## 2 HTTP-Client (5 Punkte)

### Gewählter Anwendungsfall

Ein **Wetter-Vergleich** (`WeatherClient`). Der Nutzer gibt mehrere Städte über
die Konsole ein; das Programm ermittelt für jede Stadt das aktuelle Wetter und
bestimmt am Ende die wärmste Stadt.

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
   und Durchschnitt** der Temperatur mit einer einfachen `for`-Schleife berechnet.
3. **Vergleich:** Über alle eingegebenen Städte hinweg wird per Schleife die
   Stadt mit der höchsten aktuellen Temperatur bestimmt.

```java
CityWeather summarize(GeoResult place, ForecastResponse forecast) {
    ArrayList<Double> temps = forecast.hourly.temperature_2m;
    double min = temps.get(0);
    double max = temps.get(0);
    double summe = 0;
    for (int i = 0; i < temps.size(); i++) {
        double t = temps.get(i);
        if (t < min) { min = t; }
        if (t > max) { max = t; }
        summe = summe + t;
    }
    double durchschnitt = summe / temps.size();
    // ... Werte in ein CityWeather-Objekt schreiben und zurückgeben
}
```

Und die wärmste Stadt über alle Aufrufe hinweg:

```java
CityWeather waermste = results.get(0);
for (int i = 1; i < results.size(); i++) {
    if (results.get(i).current > waermste.current) {
        waermste = results.get(i);
    }
}
```

Die Verarbeitungslogik ist zusätzlich durch den Offline-Test
`WeatherClientTest` (2 Tests, mit festen Beispiel-Antworten) abgesichert.

> _[Screenshot-Vorschlag: Konsolenausgabe mit den Temperaturen mehrerer Städte
> und der wärmsten Stadt.]_

---

## 3 TU User Page (2 Punkte)

**Link zur Webseite:** https://user.tu-berlin.de/oliver5/

### Inhalt und valides HTML (1P)

Die Seite stellt mein Übungsprojekt vor (gRPC-System und HTTP-Client,
Client-Server-Modell). Sie ist valides HTML5 (`<!DOCTYPE html>`, `lang="de"`,
korrekt geschachtelte Tags) und enthält echten inhaltlichen Text.

### JavaScript für Interaktivität (1P)

Die Seite nutzt JavaScript für zwei Funktionen:

1. Eine **Live-Uhr**, die mit `setInterval` jede Sekunde die aktuelle Uhrzeit
   aktualisiert.
2. Einen **Button mit Klick-Zähler**: Bei jedem Klick wird ein Zähler erhöht und
   der angezeigte Text auf der Seite aktualisiert.

> _[Code-Ausschnitt einfügen: den `<script>`-Teil aus deiner index.html — die
> Live-Uhr mit `setInterval(...)` und den Klick-Handler des Buttons.]_

> _[Screenshot-Vorschlag: die veröffentlichte Seite im Browser mit sichtbarer URL
> `user.tu-berlin.de/oliver5/`, Live-Uhr und Button-Zähler.]_

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
