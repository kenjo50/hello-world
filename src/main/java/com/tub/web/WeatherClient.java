package com.tub.web;

import com.google.gson.Gson;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;

// HTTP-Client fuer Aufgabe 2.
// Ruft nacheinander zwei APIs von open-meteo auf:
//   API 1 (Geocoding): Ortsname -> Koordinaten
//   API 2 (Forecast) : Koordinaten -> Temperaturen
// Die Koordinaten aus API 1 werden als Eingabe fuer API 2 benutzt (Verkettung).
// Beide APIs sind kostenlos und brauchen keinen API-Schluessel.
public class WeatherClient {

    // Die beiden API-Adressen
    static final String GEOCODING_API = "https://geocoding-api.open-meteo.com/v1/search";
    static final String FORECAST_API = "https://api.open-meteo.com/v1/forecast";

    // HttpClient und Gson einfach hier erzeugen
    HttpClient client = HttpClient.newHttpClient();
    Gson gson = new Gson();

    // API 1: Ortsname -> Koordinaten
    GeoResult geocode(String city) throws Exception {
        String url = GEOCODING_API
                + "?name=" + URLEncoder.encode(city, StandardCharsets.UTF_8)
                + "&count=1&language=de&format=json";

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Antwort (JSON) in unsere Klassen umwandeln
        GeoResponse antwort = parseGeo(response.body());
        if (antwort.results == null || antwort.results.size() == 0) {
            return null; // Ort wurde nicht gefunden
        }
        return antwort.results.get(0); // wir nehmen den ersten Treffer
    }

    // API 2: Koordinaten -> Wetter
    ForecastResponse forecast(double latitude, double longitude) throws Exception {
        String url = FORECAST_API
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&current=temperature_2m"
                + "&hourly=temperature_2m"
                + "&forecast_days=1&timezone=auto";

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return parseForecast(response.body());
    }

    // JSON mit Gson parsen (in eigene Methoden ausgelagert, damit man es testen kann)
    GeoResponse parseGeo(String json) {
        return gson.fromJson(json, GeoResponse.class);
    }

    ForecastResponse parseForecast(String json) {
        return gson.fromJson(json, ForecastResponse.class);
    }

    // Aus beiden Antworten die Min-, Max- und Durchschnittstemperatur berechnen
    CityWeather summarize(GeoResult place, ForecastResponse forecast) {
        ArrayList<Double> temps = forecast.hourly.temperature_2m;

        double min = temps.get(0);
        double max = temps.get(0);
        double summe = 0;
        for (int i = 0; i < temps.size(); i++) {
            double t = temps.get(i);
            if (t < min) {
                min = t;
            }
            if (t > max) {
                max = t;
            }
            summe = summe + t;
        }
        double durchschnitt = summe / temps.size();

        CityWeather ergebnis = new CityWeather();
        ergebnis.label = place.name + ", " + place.country;
        ergebnis.current = forecast.current.temperature_2m;
        ergebnis.min = min;
        ergebnis.max = max;
        ergebnis.average = durchschnitt;
        return ergebnis;
    }

    public static void main(String[] args) {
        WeatherClient client = new WeatherClient();
        Scanner scanner = new Scanner(System.in);
        ArrayList<CityWeather> results = new ArrayList<>();

        System.out.println("=== Wetter-Vergleich ===");
        System.out.println("Gib mehrere Staedte ein (eine pro Zeile). Leere Zeile beendet die Eingabe.");

        // Schleife: fuer jede eingegebene Stadt beide APIs aufrufen
        while (true) {
            System.out.print("> ");
            String city = scanner.nextLine().trim();
            if (city.isEmpty()) {
                break;
            }

            try {
                GeoResult place = client.geocode(city); // API 1
                if (place == null) {
                    System.out.println("  Ort '" + city + "' nicht gefunden.");
                    continue;
                }
                // Koordinaten aus API 1 als Eingabe fuer API 2
                ForecastResponse fc = client.forecast(place.latitude, place.longitude); // API 2
                CityWeather w = client.summarize(place, fc);
                results.add(w);
                System.out.println("  " + w.label + ": jetzt " + w.current
                        + " Grad (heute min " + w.min + " / max " + w.max
                        + " / Durchschnitt " + w.average + ")");
            } catch (Exception e) {
                System.out.println("  Fehler beim Abruf fuer '" + city + "': " + e.getMessage());
            }
        }

        // Weiterverarbeitung ueber alle Staedte: die waermste Stadt suchen
        if (results.size() == 0) {
            System.out.println("Keine Daten abgerufen.");
            return;
        }
        CityWeather waermste = results.get(0);
        for (int i = 1; i < results.size(); i++) {
            if (results.get(i).current > waermste.current) {
                waermste = results.get(i);
            }
        }
        System.out.println();
        System.out.println("Am waermsten ist es gerade in " + waermste.label
                + " (" + waermste.current + " Grad).");
    }

    // ---- einfache Klassen fuer das JSON. Gson fuellt die Felder automatisch. ----

    static class GeoResponse {
        ArrayList<GeoResult> results;
    }

    static class GeoResult {
        String name;
        String country;
        double latitude;
        double longitude;
    }

    static class ForecastResponse {
        Current current;
        Hourly hourly;
    }

    static class Current {
        double temperature_2m;
        String time;
    }

    static class Hourly {
        ArrayList<String> time;
        ArrayList<Double> temperature_2m;
    }

    // Ergebnis-Klasse mit einfachen Feldern
    static class CityWeather {
        String label;
        double current;
        double min;
        double max;
        double average;
    }
}
