package com.example.micro_map.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ChantierClient {

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isPyloneBloque(Long pyloneId) {
        try {
            String url = "http://localhost:8082/api/chantiers/pylone/" + pyloneId + "/bloque";
            return Boolean.TRUE.equals(
                    restTemplate.getForObject(url, Boolean.class)
            );
        } catch (Exception e) {
            throw new RuntimeException("Erreur appel micro_reclamation: " + e.getMessage());
        }
    }
}