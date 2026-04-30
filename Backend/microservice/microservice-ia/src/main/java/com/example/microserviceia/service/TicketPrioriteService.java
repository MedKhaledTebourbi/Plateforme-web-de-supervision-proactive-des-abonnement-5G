package com.example.microserviceia.service;

import com.example.microserviceia.Client.ReclamationClient;
import com.example.microserviceia.dto.TicketPredictionRequest;
import com.example.microserviceia.dto.TicketPredictionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service qui appelle predict_ticket.py via subprocess Python.
 * Suit le même pattern que PythonBridgeService existant.
 */
@Service
@Slf4j
public class TicketPrioriteService {

    @Value("${ia.python.ticket.script:src/main/python/predict_ticket.py}")
    private String ticketPredictScript;

    @Value("${ia.python.models.dir:src/main/python/models/}")
    private String modelsDir;

    @Value("${ia.python.executable:python}")
    private String pythonExe;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReclamationClient reclamationClient;

    public TicketPrioriteService(ReclamationClient reclamationClient) {
        this.reclamationClient = reclamationClient;
    }

    public TicketPredictionResponse predirePriorite(TicketPredictionRequest request) {
        try {
            String inputJson = mapper.writeValueAsString(request);
            log.info("[TicketIA] Appel Python — ticketId={}", request.getTicketId());
            log.debug("[TicketIA] JSON envoyé : {}", inputJson);

            // ── Chemins absolus ──────────────────────────────────────
            String scriptAbsolu = new File(ticketPredictScript).getAbsolutePath();
            String modelsDirAbsolu = new File(modelsDir).getAbsolutePath();

            log.info("[TicketIA] Script : {}", scriptAbsolu);
            log.info("[TicketIA] Models : {}", modelsDirAbsolu);

            // ── Vérifier que le script existe ────────────────────────
            if (!new File(scriptAbsolu).exists()) {
                log.error("[TicketIA] Script introuvable : {}", scriptAbsolu);
                return fallbackResponse(request.getTicketId(),
                        "Script Python introuvable : " + scriptAbsolu);
            }

            // ── Vérifier que models/ contient model.pkl ──────────────
            File modelFile = new File(modelsDirAbsolu, "model.pkl");
            if (!modelFile.exists()) {
                log.error("[TicketIA] model.pkl introuvable dans : {}", modelsDirAbsolu);
                return fallbackResponse(request.getTicketId(),
                        "Modèle non entraîné — lancez train_ticket_model.py d'abord");
            }

            // ── Lancer Python ────────────────────────────────────────
            ProcessBuilder pb = new ProcessBuilder(
                    pythonExe,
                    scriptAbsolu,
                    "--stdin",
                    "--models_dir", modelsDirAbsolu
            );

            // Répertoire de travail = dossier du script
            pb.directory(new File(scriptAbsolu).getParentFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Envoyer JSON sur stdin
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), "UTF-8"))) {
                writer.write(inputJson);
                writer.flush();
            }

            // Lire stdout et stderr en parallèle pour éviter le blocage
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("[TicketIA] Erreur lecture stdout", e);
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("[TicketIA] Erreur lecture stderr", e);
                }
            });

            stdoutThread.start();
            stderrThread.start();

            // Timeout 30 secondes
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            stdoutThread.join(5000);
            stderrThread.join(5000);

            if (!finished) {
                process.destroyForcibly();
                log.error("[TicketIA] Timeout Python après 30s");
                return fallbackResponse(request.getTicketId(), "Timeout Python");
            }

            int exitCode = process.exitValue();
            String stdoutStr = stdout.toString().trim();
            String stderrStr = stderr.toString().trim();

            log.info("[TicketIA] Exit code : {}", exitCode);
            log.info("[TicketIA] Stdout : {}", stdoutStr);

            if (!stderrStr.isBlank()) {
                log.warn("[TicketIA] Stderr : {}", stderrStr);
            }

            // Extraire JSON depuis stdout
            String json = extractJson(stdoutStr);
            if (json == null) {
                log.error("[TicketIA] Aucun JSON dans stdout : {}", stdoutStr);
                return fallbackResponse(request.getTicketId(),
                        "Réponse Python invalide. Stderr: " + stderrStr.substring(0,
                                Math.min(200, stderrStr.length())));
            }

            Map<String, Object> result = mapper.readValue(json, Map.class);

            if (result.containsKey("error") && result.get("error") != null) {
                String errMsg = String.valueOf(result.get("error"));
                log.warn("[TicketIA] Erreur Python : {}", errMsg);
                return fallbackResponse(request.getTicketId(), errMsg);
            }

            log.info("[TicketIA] Prédiction OK : {}", result.get("prediction"));


            // ── Construire la réponse ──────────────────────────────
            TicketPredictionResponse response = mapToResponse(result, request.getTicketId());

// ── Sauvegarder la priorité dans le microservice réclamation ──
            if (!response.isFallback()
                    && response.getPrediction() != null
                    && request.getTicketId() != null) {
                reclamationClient.savePriorite(
                        request.getTicketId(),
                        response.getPrediction()
                );
                log.info("[TicketIA] Priorité '{}' sauvegardée pour ticket {}",
                        response.getPrediction(), request.getTicketId());
            }

            return response;



        } catch (Exception e) {
            log.error("[TicketIA] Exception : {}", e.getMessage(), e);
            return fallbackResponse(request.getTicketId(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private TicketPredictionResponse mapToResponse(Map<String, Object> result, Long ticketId) {
        return TicketPredictionResponse.builder()
                .prediction((String) result.getOrDefault("prediction", "MOYENNE"))
                .confidence(toDouble(result.get("confidence")))
                .probabilities((Map<String, Double>) result.get("probabilities"))
                .featuresUsed((Map<String, Object>) result.get("features_used"))
                .ticketId(ticketId)
                .modelVersion((String) result.getOrDefault("model_version", "1.0"))
                .fallback(false)
                .build();
    }

    private TicketPredictionResponse fallbackResponse(Long ticketId, String reason) {
        log.warn("[TicketIA] Fallback MOYENNE — raison : {}", reason);
        return TicketPredictionResponse.builder()
                .prediction("MOYENNE")
                .confidence(0.0)
                .ticketId(ticketId)
                .error(reason)
                .fallback(true)
                .modelVersion("1.0")
                .build();
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (Exception e) { return 0.0; }
    }

    private String extractJson(String output) {
        if (output == null || output.isBlank()) return null;
        // Chercher la première ligne qui commence par {
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith("{") && line.contains("\"prediction\"")) {
                return line;
            }
        }
        // Fallback : chercher dans tout le texte
        int start = output.indexOf("{");
        int end   = output.lastIndexOf("}");
        if (start >= 0 && end > start) {
            String candidate = output.substring(start, end + 1);
            if (candidate.contains("\"prediction\"")) return candidate;
        }
        return null;
    }
}