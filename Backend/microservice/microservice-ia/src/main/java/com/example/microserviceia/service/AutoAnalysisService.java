package com.example.microserviceia.service;

import com.example.microserviceia.Client.MicroMapClient;
import com.example.microserviceia.Repository.SaturationRecordRepository;
import com.example.microserviceia.dto.SaturationReport;
import com.example.microserviceia.dto.ZoneFeatureVector;
import com.example.microserviceia.dto.ZoneReseauDTO;
import com.example.microserviceia.entity.SaturationRecord;
import com.example.microserviceia.entity.SaturationStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoAnalysisService {

    private final FeatureEngineeringService featureService;
    private final PythonBridgeService pythonBridge;
    private final SaturationRecordRepository recordRepository;
    private final AutoCacheService cacheService;
    private final MicroMapClient microMapClient;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    private volatile boolean isComputing = false;
    private volatile LocalDateTime lastComputeTime = null;

    @PostConstruct
    public void init() {
        startAutoRefresh();
        log.info("🤖 Service d'analyse automatique démarré");
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void autoRefresh() {
        if (!isComputing) {
            refreshDataAsync();
        }
    }

    @Async
    public void refreshDataAsync() {
        if (isComputing) {
            log.debug("Calcul déjà en cours, skip");
            return;
        }

        isComputing = true;
        long start = System.currentTimeMillis();

        try {
            log.info("🔄 Refresh automatique des données...");
            List<SaturationReport> reports = computeAllZones();
            cacheService.putAllZones(reports);
            lastComputeTime = LocalDateTime.now();
            log.info("✅ Refresh terminé en {} ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("❌ Erreur refresh: {}", e.getMessage());
        } finally {
            isComputing = false;
        }
    }

    private List<SaturationReport> computeAllZones() {
        List<ZoneReseauDTO> zones = microMapClient.getAllZones();

        if (zones == null || zones.isEmpty()) {
            log.warn("Aucune zone trouvée");
            return Collections.emptyList();
        }

        List<CompletableFuture<SaturationReport>> futures = zones.stream()
                .map(zone -> CompletableFuture.supplyAsync(() -> analyzeZoneAuto(zone)))
                .collect(Collectors.toList());

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Timeout global, retour des résultats partiels");
        }

        return futures.stream()
                .map(future -> {
                    try { return future.getNow(null); }
                    catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public SaturationReport analyzeZoneAuto(ZoneReseauDTO zone) {
        Long zoneId = zone.getZone_id();

        SaturationReport cached = cacheService.getReport(zoneId);
        if (cached != null && isCacheValid(cached)) {
            return cached;
        }

        try {
            List<SaturationRecord> historique = getHistoryAuto(zoneId);
            ZoneFeatureVector features = extractFeaturesAuto(zone, historique);
            Map<String, Object> pythonResult = callPythonWithAutoTimeout(features, historique);
            SaturationReport report = buildReportAuto(zone, features, pythonResult);
            cacheService.putReport(zoneId, report);
            return report;

        } catch (Exception e) {
            log.debug("Erreur zone {}: {}", zoneId, e.getMessage());
            return buildFallbackReportAuto(zone);
        }
    }

    private List<SaturationRecord> getHistoryAuto(Long zoneId) {
        int limit = isComputing ? 50 : 100;
        return recordRepository.findTop50ByZoneIdOrderByTimestampDesc(zoneId)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Map<String, Object> callPythonWithAutoTimeout(ZoneFeatureVector features,
                                                          List<SaturationRecord> historique) {
        CompletableFuture<Map<String, Object>> future =
                CompletableFuture.supplyAsync(() -> pythonBridge.callPredict(features, historique));

        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.debug("Timeout Python, fallback auto");
            future.cancel(true);
            return getAutoFallback(features);
        } catch (Exception e) {
            return getAutoFallback(features);
        }
    }

    private Map<String, Object> getAutoFallback(ZoneFeatureVector features) {
        Map<String, Object> fallback = new HashMap<>();
        double taux = features.getTauxUtilisation();

        // ✅ FIX: toujours calculer heures, quelle que soit la valeur du taux
        double heures = calculateAutoHours(taux);
        double confidence = taux > 85 ? 0.9 : (taux > 75 ? 0.7 : 0.5);

        fallback.put("prediction", Map.of(
                "saturation_predite", true,                    // ✅ FIX: toujours true
                "heures_avant_saturation", heures,             // ✅ FIX: toujours une valeur
                "confidence", confidence,
                "message", getAutoMessage(taux),
                "model_type", "auto_fallback"
        ));

        fallback.put("anomaly_detection", Map.of(
                "is_anomaly", taux > 80,
                "anomaly_score", -0.5,
                "source", "auto"
        ));

        return fallback;
    }

    // ✅ FIX: calcul étendu à toutes les valeurs de taux (plus seulement > 75)
    private double calculateAutoHours(double taux) {
        if (taux >= 90) return 0;
        if (taux >= 80) return (95 - taux) * 0.5;
        if (taux >= 70) return 24.0;
        if (taux >= 60) return 72.0;
        if (taux >= 40) return 168.0;
        if (taux >= 20) return 336.0;
        if (taux >= 5)  return 504.0;
        return 720.0;
    }

    private String getAutoMessage(double taux) {
        if (taux >= 90) return "⚠️ Saturation critique immédiate";
        if (taux >= 80) return "⚠️ Risque élevé de saturation";
        if (taux >= 70) return "📈 Surveillance renforcée";
        return "✅ Situation normale";
    }

    private boolean isCacheValid(SaturationReport report) {
        if (report == null || report.getTimestamp() == null) return false;
        return Duration.between(report.getTimestamp(), LocalDateTime.now()).getSeconds() < 30;
    }

    private ZoneFeatureVector extractFeaturesAuto(ZoneReseauDTO zone,
                                                  List<SaturationRecord> historique) {
        ZoneFeatureVector features = new ZoneFeatureVector();
        features.setZoneId(zone.getZone_id());
        features.setTauxUtilisation(zone.getTauxUtilisation());
        features.setRatioSatures(calculateRatioAuto(zone));
        features.setTendance6h(calculateTendanceAuto(historique));
        return features;
    }

    private double calculateRatioAuto(ZoneReseauDTO zone) {
        if (zone.getPylones() == null || zone.getPylones().isEmpty()) return 0;
        long totalPylones = zone.getPylones().size();
        long saturePylones = zone.getPylones().stream()
                .filter(p -> p.getEstBloque() != null && p.getEstBloque() || p.getTauxUtilisation() > 85)
                .count();
        if (totalPylones == 0) return 0;
        return saturePylones * 100.0 / totalPylones;
    }

    private int getNbPylonesSatures(ZoneReseauDTO zone) {
        if (zone.getPylones() == null) return 0;
        return (int) zone.getPylones().stream()
                .filter(p -> p.getEstBloque() != null && p.getEstBloque() || p.getTauxUtilisation() > 85)
                .count();
    }

    private SaturationReport buildFallbackReportAuto(ZoneReseauDTO zone) {
        SaturationReport report = new SaturationReport();
        report.setZoneId(zone.getZone_id());
        report.setZoneNom(zone.getNom());
        report.setTauxUtilisation(zone.getTauxUtilisation());
        report.setNbPylonesSatures(zone.getPylones() != null ?
                (int) zone.getPylones().stream()
                        .filter(p -> p.getEstBloque() != null && p.getEstBloque())
                        .count() : 0);
        report.setNbPylonesTotal(zone.getPylones() != null ? zone.getPylones().size() : 0);
        report.setRatioSatures(calculateRatioAuto(zone));
        report.setTendance6h(0.0);
        report.setStatut(determineStatusAuto(zone.getTauxUtilisation()));
        report.setTimestamp(LocalDateTime.now());

        // ✅ FIX: toujours calculer la prédiction, même en fallback
        double heures = calculateAutoHours(zone.getTauxUtilisation());
        report.setSaturationPredite(true);
        report.setHeuresAvantSaturation(heures);
        report.setDatePredicteSaturation(LocalDateTime.now().plusHours((long) heures));
        report.setConfidencePrediction(0.3);

        return report;
    }

    private double calculateTendanceAuto(List<SaturationRecord> historique) {
        if (historique == null || historique.size() < 2) return 0;
        SaturationRecord first = historique.get(0);
        SaturationRecord last = historique.get(historique.size() - 1);
        return last.getTauxUtilisation() - first.getTauxUtilisation();
    }

    // ✅ FIX PRINCIPAL : buildReportAuto — toujours remplir heures + date
    private SaturationReport buildReportAuto(ZoneReseauDTO zone,
                                             ZoneFeatureVector features,
                                             Map<String, Object> pythonResult) {
        SaturationReport report = new SaturationReport();
        report.setZoneId(zone.getZone_id());
        report.setZoneNom(zone.getNom());
        report.setTauxUtilisation(features.getTauxUtilisation());
        report.setRatioSatures(features.getRatioSatures());
        report.setTendance6h(features.getTendance6h());
        report.setTimestamp(LocalDateTime.now());

        double taux = features.getTauxUtilisation();

        if (pythonResult != null && pythonResult.containsKey("prediction")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pred = (Map<String, Object>) pythonResult.get("prediction");

            // ✅ FIX: toujours true
            report.setSaturationPredite(true);

            // ✅ FIX: heures — utiliser Python si dispo, sinon calcul auto
            Object heuresObj = pred.get("heures_avant_saturation");
            double heures = heuresObj instanceof Number
                    ? ((Number) heuresObj).doubleValue()
                    : calculateAutoHours(taux);          // jamais null
            report.setHeuresAvantSaturation(heures);

            // ✅ FIX: date — toujours calculée
            Object datePred = pred.get("date_predite");
            if (datePred instanceof String) {
                try {
                    String s = (String) datePred;
                    report.setDatePredicteSaturation(
                            LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s)
                    );
                } catch (Exception e) {
                    report.setDatePredicteSaturation(
                            LocalDateTime.now().plusHours((long) heures)
                    );
                }
            } else {
                // ✅ FIX: date toujours présente même si Python ne la renvoie pas
                report.setDatePredicteSaturation(
                        LocalDateTime.now().plusHours((long) heures)
                );
            }

            Object conf = pred.getOrDefault("confidence", 0.5);
            report.setConfidencePrediction(
                    conf instanceof Number ? ((Number) conf).doubleValue() : 0.5
            );

        } else {
            // ✅ FIX: fallback Java — toujours remplir les deux champs
            double heures = calculateAutoHours(taux);
            report.setSaturationPredite(true);
            report.setHeuresAvantSaturation(heures);
            report.setDatePredicteSaturation(LocalDateTime.now().plusHours((long) heures));
            report.setConfidencePrediction(taux > 85 ? 0.9 : (taux > 75 ? 0.7 : 0.4));
        }

        report.setStatut(determineStatusAuto(taux));
        return report;
    }

    private SaturationStatus determineStatusAuto(double taux) {
        if (taux >= 90) return SaturationStatus.CRITIQUE;
        if (taux >= 75) return SaturationStatus.SATURE;
        if (taux >= 60) return SaturationStatus.ATTENTION;
        return SaturationStatus.NORMAL;
    }

    private void startAutoRefresh() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isComputing) refreshDataAsync();
        }, 5, 30, TimeUnit.SECONDS);
    }

    public List<SaturationReport> getLatestReports() {
        List<SaturationReport> cached = cacheService.getAllZones();

        if (cached != null && !cached.isEmpty()) {
            log.info("📦 Cache hit: {} zones", cached.size());
            if (needRefresh()) refreshDataAsync();
            return cached;
        }

        log.info("🔄 Cache miss - Génération automatique...");
        List<ZoneReseauDTO> zones = microMapClient.getAllZones();

        if (zones == null || zones.isEmpty()) {
            log.warn("❌ Aucune zone disponible");
            return new ArrayList<>();
        }

        List<SaturationReport> reports = new ArrayList<>();
        for (ZoneReseauDTO zone : zones) {
            try {
                reports.add(generateReportFromZone(zone));
            } catch (Exception e) {
                log.error("Erreur zone {}: {}", zone.getZone_id(), e.getMessage());
            }
        }

        if (!reports.isEmpty()) {
            cacheService.putAllZones(reports);
            log.info("✅ {} zones générées automatiquement", reports.size());
        }

        return reports;
    }

    // ✅ FIX : generateReportFromZone — toujours calculer heures + date
    private SaturationReport generateReportFromZone(ZoneReseauDTO zone) {
        SaturationReport report = new SaturationReport();
        report.setZoneId(zone.getZone_id());
        report.setZoneNom(zone.getNom());
        report.setTimestamp(LocalDateTime.now());

        double taux = calculateTauxAutomatique(zone);
        report.setTauxUtilisation(taux);

        int[] pylonesData = calculatePylonesAutomatique(zone);
        report.setNbPylonesTotal(pylonesData[0]);
        report.setNbPylonesSatures(pylonesData[1]);
        report.setRatioSatures(pylonesData[0] > 0 ? (pylonesData[1] * 100.0 / pylonesData[0]) : 0);

        report.setStatut(determineStatutAutomatique(taux, pylonesData[1], pylonesData[0]));
        report.setTendance6h(calculateTendanceAutomatique(zone));

        // ✅ FIX: toujours remplir saturationPredite + heures + date
        double heures = calculateAutoHours(taux);
        report.setSaturationPredite(true);
        report.setHeuresAvantSaturation(heures);
        report.setDatePredicteSaturation(LocalDateTime.now().plusHours((long) heures));
        report.setConfidencePrediction(calculateConfianceAutomatique(zone, taux));

        return report;
    }

    private double calculateTauxAutomatique(ZoneReseauDTO zone) {
        if (zone.getTauxUtilisation() > 0) return zone.getTauxUtilisation();
        if (zone.getPylones() != null && !zone.getPylones().isEmpty()) {
            return zone.getPylones().stream()
                    .mapToDouble(p -> p.getTauxUtilisation() != null ? p.getTauxUtilisation() : 0)
                    .filter(t -> t > 0)
                    .average()
                    .orElse(50.0);
        }
        return 50.0;
    }

    private int[] calculatePylonesAutomatique(ZoneReseauDTO zone) {
        int total = 0;
        int satures = 0;

        if (zone.getPylones() != null && !zone.getPylones().isEmpty()) {
            total = zone.getPylones().size();
            satures = (int) zone.getPylones().stream()
                    .filter(p -> {
                        boolean estBloque = p.getEstBloque() != null && p.getEstBloque();
                        boolean tauxDepasse = p.getTauxUtilisation() != null && p.getTauxUtilisation() > 85;
                        return estBloque || tauxDepasse;
                    })
                    .count();
        } else {
            total = 10;
            double taux = zone.getTauxUtilisation() > 0 ? zone.getTauxUtilisation() : 50;
            satures = (int) (total * (Math.max(0, taux - 60) / 40));
        }

        return new int[]{total, satures};
    }

    private SaturationStatus determineStatutAutomatique(double taux, int pylonesSatures, int pylonesTotal) {
        double ratioSatures = pylonesTotal > 0 ? (pylonesSatures * 100.0 / pylonesTotal) : 0;
        if (taux >= 90 || ratioSatures >= 80) return SaturationStatus.CRITIQUE;
        if (taux >= 75 || ratioSatures >= 50) return SaturationStatus.SATURE;
        if (taux >= 60 || ratioSatures >= 30) return SaturationStatus.ATTENTION;
        return SaturationStatus.NORMAL;
    }

    private double calculateConfianceAutomatique(ZoneReseauDTO zone, double taux) {
        double confidence = 0.7;
        if (zone.getPylones() != null && zone.getPylones().size() > 10) confidence += 0.15;
        else if (zone.getPylones() != null && zone.getPylones().size() > 5) confidence += 0.1;
        if (taux > 85 || taux < 20) confidence += 0.1;
        return Math.min(confidence, 0.95);
    }

    private double calculateTendanceAutomatique(ZoneReseauDTO zone) {
        if (zone.getPylones() == null || zone.getPylones().isEmpty()) return 0.0;
        double tauxMoyen = zone.getPylones().stream()
                .mapToDouble(p -> p.getTauxUtilisation() != null ? p.getTauxUtilisation() : 50)
                .average()
                .orElse(50);
        return (tauxMoyen - 50) / 10;
    }

    private boolean needRefresh() {
        if (lastComputeTime == null) return true;
        return Duration.between(lastComputeTime, LocalDateTime.now()).getSeconds() > 25;
    }
}