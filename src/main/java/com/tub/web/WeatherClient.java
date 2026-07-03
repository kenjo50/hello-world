package com.tub.web;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

/**
 * Aufgabe 2: HTTP-Client, der zwei unterschiedliche Web-APIs aufruft.
 *
 * Anwendungsfall "Wetter-Vergleich":
 *  - API 1 (Geocoding, geocoding-api.open-meteo.com): wandelt einen vom Nutzer
 *    eingegebenen Ortsnamen in Koordinaten um.
 *  - API 2 (Forecast, api.open-meteo.com): liefert zu diesen Koordinaten die
 *    Temperaturen. Die Koordinaten stammen aus der Antwort von API 1
 *    (-> Verkettung: Output der ersten API ist Input der zweiten).
 *
 * Erfüllte Anforderungen:
 *  1. Zwei unterschiedliche Web-APIs.
 *  2. Beide APIs werden je Stadt aufgerufen -> bei mehreren Städten mehrfach.
 *  3. Nicht-statische Parameter: Ortsnamen kommen von der Konsole und die
 *     "aktuelle Temperatur" hängt vom Zeitpunkt der Ausführung ab.
 *  4. Antworten werden weiterverarbeitet: Koordinaten (API 1) fließen in API 2,
 *     aus den Stundenwerten werden Min/Max/Durchschnitt berechnet und am Ende
 *     die Städte nach aktueller Temperatur sortiert (Ranking).
 *
 * Beide APIs sind frei und ohne API-Schlüssel nutzbar (open-meteo.com).
 */
public class WeatherClient {

    private static final String GEOCODING_API =
            "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_API =
            "https://api.open-meteo.com/v1/forecast";

    private final HttpClient http;
    private final Gson gson = new Gson();

    public WeatherClient(HttpClient http) {
        this.http = http;
    }

    // ----------------------------- HTTP-Aufrufe -----------------------------

    /** API 1: Ortsname -> Koordinaten. */
    public GeoResult geocode(String city) throws IOException, InterruptedException {
        String url = GEOCODING_API
                + "?name=" + URLEncoder.encode(city, StandardCharsets.UTF_8)
                + "&count=1&language=de&format=json";
        String body = get(url);
        GeoResponse response = parseGeo(body);
        if (response.results == null || response.results.isEmpty()) {
            return null;
        }
        return response.results.get(0);
    }

    /** API 2: Koordinaten -> Wetter. Die Parameter stammen aus dem Ergebnis von API 1. */
    public ForecastResponse forecast(double latitude, double longitude)
            throws IOException, InterruptedException {
        String url = FORECAST_API
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&current=temperature_2m"
                + "&hourly=temperature_2m"
                + "&forecast_days=1&timezone=auto";
        return parseForecast(get(url));
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Unerwarteter HTTP-Status " + response.statusCode()
                    + " von " + url);
        }
        return response.body();
    }

    // --------------------- reine (offline testbare) Logik -------------------

    GeoResponse parseGeo(String json) {
        return gson.fromJson(json, GeoResponse.class);
    }

    ForecastResponse parseForecast(String json) {
        return gson.fromJson(json, ForecastResponse.class);
    }

    /** Verarbeitet beide API-Antworten zu einer Zusammenfassung (Min/Max/Durchschnitt). */
    CityWeather summarize(GeoResult place, ForecastResponse forecast) {
        List<Double> temps = forecast.hourly.temperature_2m;
        double min = temps.get(0);
        double max = temps.get(0);
        double sum = 0;
        for (double t : temps) {
            min = Math.min(min, t);
            max = Math.max(max, t);
            sum += t;
        }
        double avg = sum / temps.size();
        double current = forecast.current.temperature_2m;

        String label = place.name + (place.country != null ? ", " + place.country : "");
        return new CityWeather(label, current, min, max, avg);
    }

    // ------------------------------ Programm --------------------------------

    public static void main(String[] args) {
        WeatherClient client = new WeatherClient(
                HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(15)).build());

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        System.out.println("=== Wetter-Vergleich (Stand: " + now + ") ===");
        System.out.println("Gib mehrere Städte ein (je eine pro Zeile). Leere Zeile beendet die Eingabe.");

        // Nicht-statische Parameter: die Städte kommen zur Laufzeit von der Konsole.
        List<CityWeather> results = new ArrayList<>();
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) break;
                String city = scanner.nextLine().trim();
                if (city.isEmpty()) break;

                try {
                    GeoResult place = client.geocode(city);          // API 1
                    if (place == null) {
                        System.out.println("  Ort '" + city + "' nicht gefunden.");
                        continue;
                    }
                    ForecastResponse fc = client.forecast(              // API 2 (mit Output aus API 1)
                            place.latitude, place.longitude);
                    CityWeather w = client.summarize(place, fc);
                    results.add(w);
                    System.out.printf("  %s: jetzt %.1f°C (heute min %.1f / max %.1f / Ø %.1f°C)%n",
                            w.label(), w.current(), w.min(), w.max(), w.average());
                } catch (IOException | InterruptedException e) {
                    System.out.println("  Fehler beim Abruf für '" + city + "': " + e.getMessage());
                }
            }
        }

        // Weiterverarbeitung über alle Aufrufe hinweg: Ranking nach aktueller Temperatur.
        if (results.isEmpty()) {
            System.out.println("Keine Daten abgerufen.");
            return;
        }
        results.sort(Comparator.comparingDouble(CityWeather::current).reversed());
        System.out.println("\n--- Ranking nach aktueller Temperatur ---");
        int rank = 1;
        for (CityWeather w : results) {
            System.out.printf("%d. %s (%.1f°C)%n", rank++, w.label(), w.current());
        }
        CityWeather warmest = results.get(0);
        System.out.println("Am wärmsten ist es gerade in " + warmest.label() + ".");
    }

    // ------------------------ Datenklassen (JSON-Mapping) -------------------

    /** Ergebnis der Verarbeitung. */
    public record CityWeather(String label, double current, double min, double max, double average) {}

    // Struktur der Geocoding-API (nur die benötigten Felder).
    static class GeoResponse {
        List<GeoResult> results;
    }
    static class GeoResult {
        String name;
        String country;
        double latitude;
        double longitude;
    }

    // Struktur der Forecast-API (nur die benötigten Felder).
    static class ForecastResponse {
        Current current;
        Hourly hourly;
    }
    static class Current {
        double temperature_2m;
        String time;
    }
    static class Hourly {
        List<String> time;
        List<Double> temperature_2m;
    }
}
