package com.example.micro_reclamation.Service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeocodingService {

    // API Photon - gratuite, sans clé, basée sur OSM
    private static final String PHOTON_URL = "https://photon.komoot.io/api/";

    public double[] getCoordinates(String adresse) {

        // Essai 1 : adresse complète
        double[] result = callPhoton(adresse + " Tunisia");
        if (result != null) return result;

        // Essai 2 : juste la ville
        if (adresse.contains(",")) {
            String ville = adresse.substring(adresse.lastIndexOf(",") + 1).trim();
            result = callPhoton(ville + " Tunisia");
            if (result != null) return result;
        }

        System.err.println("❌ Impossible de géocoder: " + adresse);
        return null;
    }

    private double[] callPhoton(String query) {
        try {
            Thread.sleep(500);

            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "micro-reclamation/1.0");

            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String url = PHOTON_URL + "?q=" + encodedQuery + "&limit=1&lang=fr&bbox=7.5,30,13,38";
            // bbox limite la recherche à la Tunisie uniquement

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body != null) {
                List<Map<String, Object>> features = (List<Map<String, Object>>) body.get("features");
                if (features != null && !features.isEmpty()) {
                    Map<String, Object> geometry = (Map<String, Object>) features.get(0).get("geometry");
                    List<Double> coordinates = (List<Double>) geometry.get("coordinates");
                    double lon = coordinates.get(0);
                    double lat = coordinates.get(1);
                    System.out.println("✅ Trouvé [" + query + "] -> " + lat + ", " + lon);
                    return new double[]{lat, lon};
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("⚠️ Erreur Photon pour [" + query + "]: " + e.getMessage());
        }

        return null;
    }

}
