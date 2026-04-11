package com.example.microserviceia.service;

import com.example.microserviceia.Client.MicroMapClient;
import com.example.microserviceia.Repository.SaturationRecordRepository;
import com.example.microserviceia.dto.*;
import com.example.microserviceia.entity.SaturationRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaturationAnalysisService {

    private final MicroMapClient microMapClient;
    private final FeatureEngineeringService featureService;
    private final SaturationDetectionService detectionService;
    private final PredictionService predictionService;
    private final SaturationRecordRepository recordRepository;
    private final PythonBridgeService pythonBridge;

    /**
     * Analyse complète de toutes les zones.
     * Appelé par le scheduler ET par l'API REST.
     */
    public List<SaturationReport> analyzeAllZones() {
        List<ZoneReseauDTO> zones = microMapClient.getAllZones();
        log.info("Analyse de {} zones en cours...", zones.size());

        return zones.stream()
                .map(this::analyzeZone)
                .collect(Collectors.toList());
    }

    /*public SaturationReport analyzeZone(ZoneReseauDTO zone) {
        // 1. Extraction des features
        ZoneFeatureVector features = featureService.extractFeatures(zone);

        // 2. Détection du statut actuel
        SaturationResult detection = detectionService.detect(features);

        // 3. Prédiction temporelle
        PredictionResult prediction = predictionService.predict(features);

        // 4. Persistence de l'historique
        SaturationRecord record = saveRecord(zone, features, detection, prediction);

        // 5. Construction du rapport final
        return SaturationReport.builder()
                .zoneId(zone.getZone_id())
                .zoneNom(zone.getNom())
                .tauxUtilisation(features.getTauxUtilisation())
                .statut(detection.getStatut())
                .anomalyScore(detection.getAnomalyScore())
                .nbPylonesSatures(detection.getNbPylonesSatures())
                .nbPylonesTotal(detection.getNbPylonesTotal())
                .ratioSatures(features.getRatioSatures())
                .tendance6h(features.getTendance6h())
                .saturationPredite(prediction.isSaturationPredite())
                .heuresAvantSaturation(prediction.getHeuresAvantSaturation())
                .datePredicteSaturation(prediction.getDatePredite())
                .confidencePrediction(prediction.getConfidence())
                .messagePrediction(prediction.getMessage())
                .details(detection.getDetails())
                .timestamp(record.getTimestamp())
                .build();
    }*/

    private SaturationRecord saveRecord(ZoneReseauDTO zone, ZoneFeatureVector features,
                                        SaturationResult detection, PredictionResult prediction) {
        SaturationRecord record = SaturationRecord.builder()
                .zoneId(zone.getZone_id())
                .zoneNom(zone.getNom())
                .tauxUtilisation(features.getTauxUtilisation())
                .nbPylonesSatures(detection.getNbPylonesSatures())
                .nbPylonesTotal(detection.getNbPylonesTotal())
                .statut(detection.getStatut())
                .anomalyScore(detection.getAnomalyScore())
                .heuresAvantSaturation(prediction.getHeuresAvantSaturation())
                .datePredicteSaturation(safeDate(prediction.getDatePredite()))

                // 🔥 FIX 2 : ajouter timestamp
                .timestamp(LocalDateTime.now())
                .build();
        return recordRepository.save(record);
    }
    private LocalDateTime safeDate(Object date) {
        try {
            if (date == null) return null;

            // si déjà LocalDateTime
            if (date instanceof LocalDateTime) {
                return (LocalDateTime) date;
            }

            // si timestamp long (millisecondes)
            if (date instanceof Long) {
                return java.time.Instant.ofEpochMilli((Long) date)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime();
            }

            return null;

        } catch (Exception e) {
            log.error("Erreur conversion datePredite: {}", date);
            return null;
        }
    }
    public SaturationReport analyzeZone(ZoneReseauDTO zone) {

        ZoneFeatureVector features = featureService.extractFeatures(zone);
        SaturationResult detection = detectionService.detect(features);

        // Récupérer l'historique pour Python
        List<SaturationRecord> historique = recordRepository
                .findByZoneIdAndTimestampAfterOrderByTimestamp(
                        zone.getZone_id(),
                        LocalDateTime.now().minusHours(24)
                );

        // Essayer Python en premier, fallback Java si erreur
        PredictionResult prediction;
        Map<String, Object> pythonResult = pythonBridge.callPredict(features, historique);

        if (pythonResult != null) {
            prediction = parsePythonPrediction(pythonResult, features.getZoneId());
            log.debug("[IA] Prédiction Python OK pour zone {}", zone.getZone_id());
        } else {
            prediction = predictionService.predict(features);
            log.debug("[IA] Fallback Java pour zone {}", zone.getZone_id());
        }

        SaturationRecord record = saveRecord(zone, features, detection, prediction);

        return SaturationReport.builder()
                .zoneId(zone.getZone_id())
                .zoneNom(zone.getNom())
                .tauxUtilisation(features.getTauxUtilisation())
                .statut(detection.getStatut())
                .anomalyScore(detection.getAnomalyScore())
                .nbPylonesSatures(detection.getNbPylonesSatures())
                .nbPylonesTotal(detection.getNbPylonesTotal())
                .ratioSatures(features.getRatioSatures())
                .tendance6h(features.getTendance6h())
                .saturationPredite(prediction.isSaturationPredite())
                .heuresAvantSaturation(prediction.getHeuresAvantSaturation())
                .datePredicteSaturation(prediction.getDatePredite())
                .confidencePrediction(prediction.getConfidence())
                .messagePrediction(prediction.getMessage())
                .details(detection.getDetails())
                .timestamp(record.getTimestamp())
                .build();
    }
    private PredictionResult fallback(Long zoneId) {
        return PredictionResult.builder()
                .zoneId(zoneId)
                .saturationPredite(false)
                .confidence(0.0)
                .message("Fallback Java (Python invalide)")
                .build();
    }

    @SuppressWarnings("unchecked")
    private PredictionResult parsePythonPrediction(Map<String, Object> result, Long zoneId) {
        try {

            if (result == null) {
                log.error("[Python] result est null");
                return fallback(zoneId);
            }

            Object predObj = result.get("prediction");
            Object anomObj = result.get("anomaly_detection");

            if (predObj == null) {
                log.error("[Python] prediction est NULL. Response = {}", result);
                return fallback(zoneId);
            }

            Map<String, Object> pred = (Map<String, Object>) predObj;
            Map<String, Object> anom = (Map<String, Object>) anomObj;

            boolean satPredite = Boolean.TRUE.equals(pred.get("saturation_predite"));

            Double heures = pred.get("heures_avant_saturation") != null
                    ? ((Number) pred.get("heures_avant_saturation")).doubleValue()
                    : null;

            Double confidence = pred.get("confidence") != null
                    ? ((Number) pred.get("confidence")).doubleValue()
                    : 0.5;

            String message = (String) pred.getOrDefault("message", "");

            String datePredStr = (String) pred.get("date_predite");

            LocalDateTime datePred = null;
            if (datePredStr != null) {
                try {
                    datePred = LocalDateTime.parse(datePredStr.substring(0, 19));
                } catch (Exception e) {
                    log.warn("Date parsing failed: {}", datePredStr);
                }
            }

            return PredictionResult.builder()
                    .zoneId(zoneId)
                    .saturationPredite(satPredite)
                    .heuresAvantSaturation(heures)
                    .datePredite(datePred)
                    .confidence(confidence)
                    .message(message)
                    .build();

        } catch (Exception e) {
            log.warn("[Python] Erreur parsing prédiction: {}", e.getMessage());
            return fallback(zoneId);
        }
    }
}