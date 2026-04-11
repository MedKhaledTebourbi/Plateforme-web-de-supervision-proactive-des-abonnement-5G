package com.example.microserviceia.controller;

import com.example.microserviceia.Client.MicroMapClient;
import com.example.microserviceia.Repository.SaturationRecordRepository;
import com.example.microserviceia.dto.GlobalStats;
import com.example.microserviceia.dto.SaturationReport;
import com.example.microserviceia.dto.ZoneFeatureVector;
import com.example.microserviceia.dto.ZoneReseauDTO;
import com.example.microserviceia.entity.SaturationRecord;
import com.example.microserviceia.entity.SaturationStatus;
import com.example.microserviceia.service.AutoAnalysisService;  // ← Changer ici
import com.example.microserviceia.service.FeatureEngineeringService;
import com.example.microserviceia.service.PythonBridgeService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/saturation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class SaturationController {

    private final AutoAnalysisService analysisService;  // ← Changer de SaturationAnalysisService à AutoAnalysisService
    private final SaturationRecordRepository recordRepository;
    private final MicroMapClient microMapClient;
    private final PythonBridgeService pythonBridge;
    private final FeatureEngineeringService featureService;

    // Analyse complète de toutes les zones
    @GetMapping("/zones")
    public ResponseEntity<List<SaturationReport>> analyzeAllZones() {
        try {
            log.info("📊 Appel API /zones");
            List<SaturationReport> reports = analysisService.getLatestReports();  // ← Utiliser getLatestReports()

            if (reports == null || reports.isEmpty()) {
                log.warn("⚠️ Aucun rapport trouvé, retour liste vide");
                return ResponseEntity.ok(List.of());
            }

            log.info("✅ Retourne {} zones", reports.size());
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            log.error("❌ Erreur dans /zones", e);
            return ResponseEntity.status(500).build();
        }
    }

    // Analyse d'une zone spécifique
    @GetMapping("/zones/{zoneId}")
    public ResponseEntity<SaturationReport> analyzeZone(@PathVariable Long zoneId) {
        try {
            ZoneReseauDTO zone = microMapClient.getZoneById(zoneId);
            if (zone == null) {
                return ResponseEntity.notFound().build();
            }

            // Forcer un calcul frais pour cette zone
            SaturationReport report = analysisService.analyzeZoneAuto(zone);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Erreur zone {}", zoneId, e);
            return ResponseEntity.status(500).build();
        }
    }

    // Zones saturées uniquement
    @GetMapping("/zones/saturees")
    public ResponseEntity<List<SaturationReport>> getSaturatedZones() {
        try {
            List<SaturationReport> all = analysisService.getLatestReports();
            if (all == null) {
                return ResponseEntity.ok(List.of());
            }

            List<SaturationReport> saturees = all.stream()
                    .filter(r -> r.getStatut() == SaturationStatus.SATURE
                            || r.getStatut() == SaturationStatus.CRITIQUE)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(saturees);
        } catch (Exception e) {
            log.error("Erreur zones saturées", e);
            return ResponseEntity.status(500).build();
        }
    }

    // Historique d'une zone (pour graphiques)
    @GetMapping("/zones/{zoneId}/historique")
    public ResponseEntity<List<SaturationRecord>> getHistorique(
            @PathVariable Long zoneId,
            @RequestParam(defaultValue = "24") int heures) {
        try {
            List<SaturationRecord> history = recordRepository
                    .findByZoneIdAndTimestampAfterOrderByTimestamp(
                            zoneId, LocalDateTime.now().minusHours(heures));
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Erreur historique zone {}", zoneId, e);
            return ResponseEntity.status(500).build();
        }
    }

    // Statistiques globales
    @GetMapping("/stats")
    public ResponseEntity<GlobalStats> getStats() {
        try {
            List<SaturationReport> reports = analysisService.getLatestReports();

            if (reports == null || reports.isEmpty()) {
                return ResponseEntity.ok(GlobalStats.builder()
                        .totalZones(0)
                        .zonesNormales(0)
                        .zonesEnAttention(0)
                        .zonesSaturees(0)
                        .zonesCritiques(0)
                        .timestamp(LocalDateTime.now())
                        .build());
            }

            long normal = reports.stream().filter(r -> r.getStatut() == SaturationStatus.NORMAL).count();
            long attention = reports.stream().filter(r -> r.getStatut() == SaturationStatus.ATTENTION).count();
            long sature = reports.stream().filter(r -> r.getStatut() == SaturationStatus.SATURE).count();
            long critique = reports.stream().filter(r -> r.getStatut() == SaturationStatus.CRITIQUE).count();

            return ResponseEntity.ok(GlobalStats.builder()
                    .totalZones(reports.size())
                    .zonesNormales((int) normal)
                    .zonesEnAttention((int) attention)
                    .zonesSaturees((int) sature)
                    .zonesCritiques((int) critique)
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("Erreur stats", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/zones/{zoneId}/ia-result")
    public ResponseEntity<Map<String, Object>> getIaResult(@PathVariable Long zoneId) {
        try {
            ZoneReseauDTO zone = microMapClient.getZoneById(zoneId);
            if (zone == null) {
                return ResponseEntity.notFound().build();
            }

            ZoneFeatureVector features = featureService.extractFeatures(zone);
            List<SaturationRecord> historique = recordRepository
                    .findByZoneIdAndTimestampAfterOrderByTimestamp(
                            zoneId, LocalDateTime.now().minusHours(24));

            Map<String, Object> pythonResult = pythonBridge.callPredict(features, historique);

            if (pythonResult == null || pythonResult.isEmpty()) {
                return ResponseEntity.status(503)
                        .body(Map.of("error", "Python indisponible", "fallback", "java"));
            }

            return ResponseEntity.ok(pythonResult);
        } catch (Exception e) {
            log.error("Erreur IA zone {}", zoneId, e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Export CSV pour réentraînement
    @GetMapping("/export/csv")
    public void exportCsv(
            @RequestParam(defaultValue = "168") int heures,
            HttpServletResponse response) throws IOException {

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"historique_" +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv\"");

        List<SaturationRecord> records = recordRepository
                .findByTimestampAfterOrderByTimestamp(
                        LocalDateTime.now().minusHours(heures));

        PrintWriter writer = response.getWriter();
        writer.println("zone_id,zone_nom,taux_utilisation,nb_pylones_satures," +
                "nb_pylones_total,anomaly_score,statut,timestamp");

        for (SaturationRecord r : records) {
            writer.println(String.format(Locale.US,
                    "%d,%s,%.2f,%d,%d,%.4f,%s,%s",
                    r.getZoneId(),
                    escapeCSV(r.getZoneNom()),
                    r.getTauxUtilisation(),
                    r.getNbPylonesSatures(),
                    r.getNbPylonesTotal(),
                    r.getAnomalyScore() != null ? r.getAnomalyScore() : 1.0,
                    r.getStatut().name(),
                    r.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
        }
        writer.flush();
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}