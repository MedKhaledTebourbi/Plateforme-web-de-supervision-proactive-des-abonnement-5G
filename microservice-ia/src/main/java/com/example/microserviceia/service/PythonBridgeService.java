package com.example.microserviceia.service;


import com.example.microserviceia.dto.ZoneFeatureVector;
import com.example.microserviceia.entity.SaturationRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PythonBridgeService {

    @Value("${ia.python.predict.script:src/main/python/predict.py}")
    private String predictScript;

    @Value("${ia.python.models.dir:src/main/python/models/}")
    private String modelsDir;

    @Value("${ia.python.executable:python}")
    private String pythonExe;

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> callPredict(ZoneFeatureVector features,
                                           List<SaturationRecord> historique) {
        try {

            Map<String, Object> input = new HashMap<>();
            input.put("zone_id", features.getZoneId());
            input.put("features", buildFeaturesMap(features));

            if (historique != null && !historique.isEmpty()) {
                input.put("historique", buildHistoriqueList(historique));
            }

            String inputJson = mapper.writeValueAsString(input);

            log.info("JSON SENT TO PYTHON: {}", inputJson);

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExe,
                    predictScript,
                    "--stdin",
                    "--models_dir", modelsDir
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream())
            );
            writer.write(inputJson);
            writer.flush();
            writer.close();

            String output = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            ).lines().collect(Collectors.joining("\n"));

            int exitCode = process.waitFor();

            log.info("PYTHON RAW OUTPUT: {}", output);

            if (exitCode != 0 || output == null || output.isBlank()) {
                log.warn("[Python] Exit={} ou output vide", exitCode);
                return null;
            }

            // 🔥 EXTRACTION ROBUSTE JSON
            String json = extractJson(output);

            if (json == null) {
                log.error("[Python] JSON introuvable dans output: {}", output);
                return null;
            }

            log.info("FINAL JSON CORRECT: {}", json);

            Map<String, Object> result = mapper.readValue(json, Map.class);

            if (result == null || !result.containsKey("prediction")) {
                log.error("[Python] prediction absente ou JSON invalide: {}", result);
                return null;
            }

            return result;

        } catch (Exception e) {
            log.error("[Python] Erreur zone {}: {}", features.getZoneId(), e.getMessage());
            return null;
        }
    }
    private String extractJson(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }

        // Parcourir ligne par ligne pour trouver le JSON complet
        String[] lines = output.split("\n");
        StringBuilder jsonBuilder = new StringBuilder();
        boolean inJson = false;
        int braceCount = 0;

        for (String line : lines) {
            line = line.trim();

            // Ignorer les lignes de debug qui ne contiennent pas {
            if (!line.contains("{") && !inJson) {
                continue;
            }

            // Parcourir chaque caractère de la ligne
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    inJson = true;
                    braceCount++;
                    jsonBuilder.append(c);
                } else if (c == '}') {
                    braceCount--;
                    jsonBuilder.append(c);
                    if (braceCount == 0 && inJson) {
                        // Fin du JSON trouvé
                        String json = jsonBuilder.toString();
                        // Vérifier que c'est le bon JSON (contient prediction)
                        if (json.contains("\"prediction\"") && json.contains("\"anomaly_detection\"")) {
                            return json;
                        } else {
                            // Réinitialiser pour chercher un autre JSON
                            jsonBuilder.setLength(0);
                            inJson = false;
                        }
                    }
                } else if (inJson) {
                    jsonBuilder.append(c);
                }
            }
        }

        log.error("[Python] Aucun JSON valide avec 'prediction' trouvé");
        return null;
    }
    private Map<String, Object> buildFeaturesMap(ZoneFeatureVector f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taux_utilisation", f.getTauxUtilisation());
        m.put("ratio_satures", f.getRatioSatures());
        m.put("tendance_6pts", f.getTendance6h());
        m.put("taux_lag1", f.getTauxUtilisation());
        m.put("taux_lag3", f.getTauxUtilisation());
        m.put("hour_of_day", LocalDateTime.now().getHour());
        m.put("day_of_week", LocalDateTime.now().getDayOfWeek().getValue());
        return m;
    }



    private List<Map<String, Object>> buildHistoriqueList(List<SaturationRecord> records) {
        return records.stream().map(r -> {
            Map<String, Object> h = new HashMap<>();
            h.put("timestamp", r.getTimestamp().toString());
            h.put("taux_utilisation", r.getTauxUtilisation());
            return h;
        }).collect(Collectors.toList());
    }
}