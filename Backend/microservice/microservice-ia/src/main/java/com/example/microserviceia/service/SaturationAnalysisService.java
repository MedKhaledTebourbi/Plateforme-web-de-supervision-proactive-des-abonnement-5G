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
        log.info("🔍 [DIAG] Zone {} | Python result: {} | Historique size: {}",
                zone.getZone_id(),
                pythonResult != null ? "OK" : "NULL ← PYTHON ÉCHOUE",
                historique.size());

        if (pythonResult != null) {
            prediction = parsePythonPrediction(pythonResult, features.getZoneId(), features);
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
                // ✅ FIX: toujours mapper, même si saturationPredite=false
                .heuresAvantSaturation(prediction.getHeuresAvantSaturation())
                .datePredicteSaturation(safeDate(prediction.getDatePredite()))
                .timestamp(LocalDateTime.now())
                .build();

        log.debug("saveRecord → zone={} | heuresAvantSaturation={} | datePredicteSaturation={}",
                zone.getZone_id(),
                record.getHeuresAvantSaturation(),
                record.getDatePredicteSaturation());

        return recordRepository.save(record);
    }

    private LocalDateTime safeDate(Object date) {
        if (date == null) return null;
        try {
            if (date instanceof LocalDateTime) return (LocalDateTime) date;
            if (date instanceof Long) {
                return java.time.Instant.ofEpochMilli((Long) date)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime();
            }
            if (date instanceof String) {
                String s = (String) date;
                return LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s);
            }
            log.warn("safeDate: type non géré {} → {}", date.getClass().getSimpleName(), date);
            return null;
        } catch (Exception e) {
            log.warn("safeDate: échec conversion '{}' → {}", date, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private PredictionResult parsePythonPrediction(Map<String, Object> result,
                                                   Long zoneId,
                                                   ZoneFeatureVector features) {
        try {
            // ✅ FIX: toujours passer features au fallback, jamais retourner null/vide
            if (result == null) return fallback(zoneId, features);

            Map<String, Object> pred = (Map<String, Object>) result.get("prediction");
            if (pred == null) return fallback(zoneId, features);

            boolean satPredite = Boolean.TRUE.equals(pred.get("saturation_predite"));

            Double confidence = pred.get("confidence") != null
                    ? ((Number) pred.get("confidence")).doubleValue() : 0.5;

            String message = (String) pred.getOrDefault("message", "");

            Double heures;
            LocalDateTime datePred;

            if (pred.get("heures_avant_saturation") != null) {
                // ✅ Python a une vraie prédiction basée sur tendance
                heures = ((Number) pred.get("heures_avant_saturation")).doubleValue();
                String datePredStr = (String) pred.get("date_predite");
                if (datePredStr != null) {
                    try {
                        datePred = LocalDateTime.parse(datePredStr.substring(0, 19));
                    } catch (Exception e) {
                        datePred = LocalDateTime.now().plusHours(heures.longValue());
                    }
                } else {
                    datePred = LocalDateTime.now().plusHours(heures.longValue());
                }
            } else {
                // ⚠️ Python dit "pas de tendance détectée"
                // → On calcule une estimation basée sur le taux instantané
                heures = calculerHeuresDepuisTaux(features.getTauxUtilisation());
                datePred = LocalDateTime.now().plusHours(heures.longValue());
                message = String.format("Estimation statique — saturation dans %.0fh (taux actuel: %.1f%%)",
                        heures, features.getTauxUtilisation());
                confidence = 0.3;
            }

            log.info("📊 Zone {} | taux={}% | heures={}h | date={} | model={}",
                    zoneId,
                    String.format("%.1f", features.getTauxUtilisation()),
                    String.format("%.0f", heures),
                    datePred,
                    pred.getOrDefault("model_type", "?"));

            return PredictionResult.builder()
                    .zoneId(zoneId)
                    // ✅ FIX: saturationPredite=true dès qu'on a une estimation valide
                    .saturationPredite(true)
                    .heuresAvantSaturation(heures)
                    .datePredite(datePred)
                    .confidence(confidence)
                    .message(message)
                    .build();

        } catch (Exception e) {
            log.warn("[Python] Erreur parsing: {}", e.getMessage());
            return fallback(zoneId, features);
        }
    }

    /**
     * Estimation du temps avant saturation (seuil 80%)
     * basée uniquement sur le taux instantané.
     */
    private double calculerHeuresDepuisTaux(double taux) {
        double SEUIL = 80.0;

        if (taux >= SEUIL)  return 0.0;
        if (taux >= 70.0)   return 24.0;
        if (taux >= 60.0)   return 72.0;
        if (taux >= 40.0)   return 168.0;
        if (taux >= 20.0)   return 336.0;
        if (taux >= 5.0)    return 504.0;
        return 720.0;
    }

    /**
     * ✅ FIX: fallback unique avec features — toujours retourner des valeurs non-null.
     * L'ancien fallback(Long zoneId) sans features a été supprimé car il causait des nulls en DB.
     */
    private PredictionResult fallback(Long zoneId, ZoneFeatureVector features) {
        double heures = features != null
                ? calculerHeuresDepuisTaux(features.getTauxUtilisation())
                : 720.0;

        LocalDateTime datePred = LocalDateTime.now().plusHours((long) heures);

        return PredictionResult.builder()
                .zoneId(zoneId)
                // ✅ FIX: true au lieu de false pour que l'UI affiche la prédiction
                .saturationPredite(true)
                .heuresAvantSaturation(heures)
                .datePredite(datePred)
                .confidence(0.2)
                .message(String.format("Estimation de base — %.0fh avant saturation potentielle", heures))
                .build();
    }
}