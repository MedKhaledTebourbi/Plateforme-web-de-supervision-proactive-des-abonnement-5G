package com.example.micro_map.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class GeocodingService {

    private final Map<String, double[]> cache = new HashMap<>();

    public double[] getCoordinatesFromAddress(String adresse) {
        // 1. Vérifier le cache
        if (cache.containsKey(adresse)) {
            System.out.println("📦 Utilisation du cache pour: " + adresse);
            return cache.get(adresse);
        }

        // 2. Essayer différentes stratégies
        String[] strategies = {
                adresse,                                                    // adresse complète
                adresse.replaceFirst("^\\d+\\s+", ""),                     // sans le numéro
                adresse.replaceAll("\\d+", "").replaceAll("\\s+", " ").trim(), // sans aucun numéro
                getStreetOnly(adresse),                                     // seulement le nom de rue
                getCityOnly(adresse)                                        // seulement la ville
        };

        for (String strategy : strategies) {
            if (strategy == null || strategy.isEmpty()) continue;

            System.out.println("🔍 Tentative: " + strategy);
            double[] result = tryGeocode(strategy);
            if (result != null) {
                cache.put(adresse, result);
                return result;
            }
        }

        // 3. Dernier recours : utiliser la ville + pays
        String[] parts = adresse.split(",");
        if (parts.length >= 2) {
            String villePays = parts[parts.length - 2].trim() + ", " + parts[parts.length - 1].trim();
            System.out.println("🔄 Dernier recours: " + villePays);
            double[] result = tryGeocode(villePays);
            if (result != null) {
                cache.put(adresse, result);
                return result;
            }
        }

        throw new RuntimeException("Impossible de géocoder l'adresse: " + adresse);
    }

    private String getStreetOnly(String adresse) {
        String[] parts = adresse.split(",");
        if (parts.length >= 1) {
            return parts[0].trim();
        }
        return null;
    }

    private String getCityOnly(String adresse) {
        String[] parts = adresse.split(",");
        if (parts.length >= 2) {
            return parts[parts.length - 2].trim() + ", " + parts[parts.length - 1].trim();
        }
        return null;
    }

    private double[] tryGeocode(String adresse) {
        try {
            Thread.sleep(800); // Pause pour respecter les limites

            String adresseEncodee = URLEncoder.encode(adresse, StandardCharsets.UTF_8);

            // Essayer avec différents paramètres
            String[] urls = {
                    "https://photon.komoot.io/api/?q=" + adresseEncodee + "&limit=1&lang=fr",
                    "https://photon.komoot.io/api/?q=" + adresseEncodee + "&limit=1&lang=fr&osm_tag=place",
                    "https://photon.komoot.io/api/?q=" + adresseEncodee + "&limit=1&lang=fr&osm_tag=highway"
            };

            for (String url : urls) {
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "PFE-5G/1.0");
                HttpEntity<String> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(response.getBody());

                    if (root.has("features") && root.get("features").size() > 0) {
                        JsonNode coordinates = root.get("features").get(0).get("geometry").get("coordinates");
                        double lon = coordinates.get(0).asDouble();
                        double lat = coordinates.get(1).asDouble();

                        String name = root.get("features").get(0).has("properties") &&
                                root.get("features").get(0).get("properties").has("name") ?
                                root.get("features").get(0).get("properties").get("name").asText() : "N/A";

                        System.out.println("✅ Trouvé: " + name);
                        System.out.println("✅ Coords: lat=" + lat + " lon=" + lon);
                        return new double[]{lat, lon};
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
        }
        return null;
    }
}