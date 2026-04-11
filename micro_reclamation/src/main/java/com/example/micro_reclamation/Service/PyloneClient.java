package com.example.micro_reclamation.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PyloneClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${map.service.url:http://localhost:8081}")
    private String mapServiceUrl;

    // ✅ Récupérer les infos d'un pylône depuis micro_map
    public Map<String, Object> getPylone(Long pyloneId) {
        if (pyloneId == null) {
            log.warn("Tentative de récupération d'un pylône avec ID null");
            return null;
        }
        try {
            String url = mapServiceUrl + "/api/pylones/" + pyloneId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Erreur récupération pylône {} : {}", pyloneId, e.getMessage());
            return null;
        }
    }

    // ✅ Récupérer TOUS les pylônes depuis micro_map
    public List<Map<String, Object>> getAllPylones() {
        try {
            String url = mapServiceUrl + "/api/pylones";
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException e) {
            log.error("Erreur récupération liste pylônes : {}", e.getMessage());
            throw new RuntimeException("Service micro_map indisponible : " + e.getMessage());
        }
    }

    // ✅ Bloquer / débloquer un pylône dans micro_map
    public void bloquerPylone(Long pyloneId, boolean bloque) {
        if (pyloneId == null) {
            log.warn("Tentative de blocage/déblocage d'un pylône avec ID null");
            return;
        }
        try {
            String url = mapServiceUrl + "/api/pylones/" + pyloneId + "/bloquer?bloque=" + bloque;
            restTemplate.put(url, null);
            log.info("✅ Pylône {} {}", pyloneId, bloque ? "bloqué" : "débloqué");
        } catch (RestClientException e) {
            log.error("❌ Impossible de {} le pylône {} : {}",
                    bloque ? "bloquer" : "débloquer", pyloneId, e.getMessage());
            throw new RuntimeException("Erreur de communication avec micro_map : " + e.getMessage());
        }
    }

    // ✅ Vérifier si un pylône existe
    public boolean pyloneExiste(Long pyloneId) {
        try {
            String url = mapServiceUrl + "/api/pylones/" + pyloneId + "/exists";
            ResponseEntity<Boolean> response = restTemplate.getForEntity(url, Boolean.class);
            return Boolean.TRUE.equals(response.getBody());
        } catch (RestClientException e) {
            log.error("Erreur vérification existence pylône {} : {}", pyloneId, e.getMessage());
            return false;
        }
    }
}
