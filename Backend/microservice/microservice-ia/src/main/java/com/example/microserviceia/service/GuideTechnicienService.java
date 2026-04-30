package com.example.microserviceia.service;

import com.example.microserviceia.dto.GuideRequest;
import com.example.microserviceia.dto.GuideResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class GuideTechnicienService {

    @Value("${ia.python.guide.script:src/main/python/predict_guide.py}")
    private String guideScript;

    @Value("${ia.python.models.dir:src/main/python/models/}")
    private String modelsDir;

    @Value("${ia.python.executable:python}")
    private String pythonExe;

    private final ObjectMapper mapper = new ObjectMapper();

    public GuideResponse getGuide(GuideRequest request) {
        try {
            // ✅ Valeurs par défaut si manquantes
            if (request.getZonePopulation() == null || request.getZonePopulation().isBlank()) {
                request.setZonePopulation("MOYENNE");
            }
            if (request.getNombreReclamations() <= 0) {
                request.setNombreReclamations(1);
            }
            if (request.getHeure() <= 0) {
                request.setHeure(java.time.LocalDateTime.now().getHour());
            }
            if (request.getDescription() == null) {
                request.setDescription("");
            }

            String inputJson = mapper.writeValueAsString(request);
            String scriptAbs = new File(guideScript).getAbsolutePath();
            String modelsAbs = new File(modelsDir).getAbsolutePath();

            log.info("[Guide] Script path: {}", scriptAbs);
            log.info("[Guide] Models path: {}", modelsAbs);
            log.info("[Guide] Script exists: {}", new File(scriptAbs).exists());
            log.info("[Guide] Models dir exists: {}", new File(modelsAbs).exists());
            log.info("[Guide] Input JSON: {}", inputJson);

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExe, scriptAbs, "--stdin", "--models_dir", modelsAbs
            );
            pb.directory(new File(scriptAbs).getParentFile());

            // ✅ Fix encodage Windows
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONLEGACYWINDOWSSTDIO", "0");

            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Écrire stdin
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), "UTF-8"))) {
                w.write(inputJson);
                w.flush();
            }

            // Lire stdout + stderr en parallèle
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();

            Thread t1 = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                    r.lines().forEach(l -> out.append(l).append("\n"));
                } catch (IOException e) { log.error("stdout error", e); }
            });
            Thread t2 = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), "UTF-8"))) {
                    r.lines().forEach(l -> err.append(l).append("\n"));
                } catch (IOException e) { log.error("stderr error", e); }
            });

            t1.start(); t2.start();
            boolean done = process.waitFor(30, TimeUnit.SECONDS);
            t1.join(3000); t2.join(3000);

            if (!done) {
                process.destroyForcibly();
                return fallback(request, "Timeout Python");
            }

            // ✅ Toujours logger stderr pour voir les erreurs Python
            if (!err.toString().isBlank()) {
                log.error("[Guide] stderr Python: {}", err);
            }

            log.info("[Guide] stdout: {}", out.toString());

            // Parser JSON — prendre la première ligne JSON valide
            String json = out.toString().trim();
            for (String line : json.split("\n")) {
                if (line.trim().startsWith("{")) {
                    json = line.trim();
                    break;
                }
            }

            if (json.isBlank()) {
                return fallback(request, "Réponse Python vide");
            }

            Map<String, Object> result = mapper.readValue(json, Map.class);

            // ✅ Si Python a retourné une erreur dans le JSON
            if (result.containsKey("error")) {
                log.error("[Guide] Erreur Python: {}", result.get("error"));
                return fallback(request, (String) result.get("error"));
            }

            return mapToResponse(result, request.getTicketId());

        } catch (Exception e) {
            log.error("[Guide] Exception: {}", e.getMessage(), e);
            return fallback(request, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private GuideResponse mapToResponse(Map<String, Object> r, Long ticketId) {
        return GuideResponse.builder()
                .ticketId(ticketId)
                .typePanne((String) r.get("typePanne"))
                .urgence((String) r.get("urgence"))
                .action((String) r.get("action"))
                .solution((String) r.get("solution"))
                .etapes((List<String>) r.get("etapes"))
                .prioriteAction((String) r.get("priorite_action"))
                .tempsEstime((String) r.get("temps_estime"))
                .automatisable(Boolean.TRUE.equals(r.get("automatisable")))
                .outils((List<String>) r.getOrDefault("outils", List.of()))
                .raison((String) r.get("raison"))
                .confidenceMl(toDouble(r.get("confidence_ml")))
                .modelVersion((String) r.getOrDefault("model_version", "1.0"))
                .fallback(false)
                .build();
    }

    private GuideResponse fallback(GuideRequest req, String reason) {
        return GuideResponse.builder()
                .ticketId(req.getTicketId())
                .typePanne(req.getTypePanne())
                .action("DIAGNOSTIC_MANUEL")
                .solution("Diagnostiquer manuellement selon procédures standard")
                .etapes(List.of(
                        "Analyser les logs système",
                        "Contacter le NOC pour support",
                        "Escalader si non résolu sous 1h"
                ))
                .prioriteAction("NORMALE")
                .tempsEstime("30-60 min")
                .automatisable(false)
                .outils(List.of("OMC", "NOC Dashboard"))
                .raison("Fallback — service IA indisponible")
                .confidenceMl(0.0)
                .fallback(true)
                .error(reason)
                .build();
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }
}