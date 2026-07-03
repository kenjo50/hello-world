package com.tub.web;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet die Weiterverarbeitung der API-Antworten (Anforderung 4) offline anhand
 * echter Beispiel-Responses der beiden Open-Meteo-APIs – ohne Netzwerkzugriff.
 */
public class WeatherClientTest {

    // Echte (gekürzte) Antwort der Geocoding-API für "Berlin".
    private static final String GEO_JSON = """
            {"results":[{"id":2950159,"name":"Berlin","latitude":52.52437,
            "longitude":13.41053,"country_code":"DE","country":"Germany",
            "admin1":"Land Berlin"}],"generationtime_ms":0.5}
            """;

    // Echte (gekürzte) Antwort der Forecast-API.
    private static final String FORECAST_JSON = """
            {"latitude":52.52,"longitude":13.42,
            "current":{"time":"2026-07-03T17:00","interval":900,"temperature_2m":21.4},
            "hourly":{"time":["2026-07-03T00:00","2026-07-03T01:00","2026-07-03T02:00"],
            "temperature_2m":[15.0,17.0,19.0]}}
            """;

    private final WeatherClient client = new WeatherClient(HttpClient.newHttpClient());

    @Test
    void geocodingWirdKorrektAusgelesen() {
        WeatherClient.GeoResponse geo = client.parseGeo(GEO_JSON);
        WeatherClient.GeoResult berlin = geo.results.get(0);
        assertEquals("Berlin", berlin.name);
        assertEquals("Germany", berlin.country);
        assertEquals(52.52437, berlin.latitude, 1e-6);
        assertEquals(13.41053, berlin.longitude, 1e-6);
    }

    @Test
    void leeresGeocodingErgebnisWirdErkannt() {
        WeatherClient.GeoResponse geo = client.parseGeo("{\"generationtime_ms\":0.1}");
        assertTrue(geo.results == null || geo.results.isEmpty());
    }

    @Test
    void antwortenWerdenVerarbeitet() {
        // Verkettung: Geocoding-Ergebnis + Forecast-Ergebnis werden zusammengeführt.
        WeatherClient.GeoResult place = client.parseGeo(GEO_JSON).results.get(0);
        WeatherClient.ForecastResponse fc = client.parseForecast(FORECAST_JSON);

        WeatherClient.CityWeather w = client.summarize(place, fc);

        assertEquals("Berlin, Germany", w.label());
        assertEquals(21.4, w.current(), 1e-9);   // aktuelle Temperatur
        assertEquals(15.0, w.min(), 1e-9);        // aus den Stundenwerten berechnet
        assertEquals(19.0, w.max(), 1e-9);
        assertEquals(17.0, w.average(), 1e-9);    // (15+17+19)/3
    }
}
