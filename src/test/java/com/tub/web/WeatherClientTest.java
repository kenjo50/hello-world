package com.tub.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Testet die Verarbeitung der API-Antworten (Aufgabe 2, Anforderung 4).
// Wir benutzen feste Beispiel-Antworten, damit der Test ohne Internet laeuft.
public class WeatherClientTest {

    // Beispiel-Antwort der Geocoding-API fuer "Berlin"
    static final String GEO_JSON =
            "{\"results\":[{\"name\":\"Berlin\",\"latitude\":52.52437," +
            "\"longitude\":13.41053,\"country\":\"Germany\"}]}";

    // Beispiel-Antwort der Forecast-API
    static final String FORECAST_JSON =
            "{\"current\":{\"temperature_2m\":21.4}," +
            "\"hourly\":{\"temperature_2m\":[15.0,17.0,19.0]}}";

    @Test
    void geocodingWirdAusgelesen() {
        WeatherClient client = new WeatherClient();
        WeatherClient.GeoResponse geo = client.parseGeo(GEO_JSON);
        WeatherClient.GeoResult berlin = geo.results.get(0);

        assertEquals("Berlin", berlin.name);
        assertEquals(52.52437, berlin.latitude, 0.0001);
    }

    @Test
    void wetterWirdVerarbeitet() {
        WeatherClient client = new WeatherClient();

        // Ergebnisse beider APIs zusammenfuehren
        WeatherClient.GeoResult place = client.parseGeo(GEO_JSON).results.get(0);
        WeatherClient.ForecastResponse fc = client.parseForecast(FORECAST_JSON);
        WeatherClient.CityWeather w = client.summarize(place, fc);

        assertEquals("Berlin, Germany", w.label);
        assertEquals(21.4, w.current, 0.0001);   // aktuelle Temperatur
        assertEquals(15.0, w.min, 0.0001);
        assertEquals(19.0, w.max, 0.0001);
        assertEquals(17.0, w.average, 0.0001);   // (15 + 17 + 19) / 3
    }
}
