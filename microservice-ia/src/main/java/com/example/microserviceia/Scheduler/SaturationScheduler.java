package com.example.microserviceia.Scheduler;

import com.example.microserviceia.dto.SaturationReport;
import com.example.microserviceia.entity.SaturationStatus;
import com.example.microserviceia.service.SaturationAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SaturationScheduler {

    private final SaturationAnalysisService analysisService;

    // Analyse toutes les 5 minutes
    @Scheduled(fixedRateString = "${ia.scheduler.interval:300000}")
    public void scheduledAnalysis() {
        log.info("[SCHEDULER] Démarrage analyse automatique de saturation...");
        try {
            List<SaturationReport> reports = analysisService.analyzeAllZones();
            long saturees = reports.stream()
                    .filter(r -> r.getStatut() == SaturationStatus.SATURE
                            || r.getStatut() == SaturationStatus.CRITIQUE)
                    .count();
            log.info("[SCHEDULER] Terminé : {}/{} zones saturées/critiques",
                    saturees, reports.size());
        } catch (Exception e) {
            log.error("[SCHEDULER] Erreur lors de l'analyse automatique", e);
        }
    }

    // Nettoyage historique hebdomadaire (garde 30 jours)
    @Scheduled(cron = "0 0 2 * * SUN")
    public void cleanOldRecords() {
        log.info("[SCHEDULER] Nettoyage historique...");
        // recordRepository.deleteByTimestampBefore(LocalDateTime.now().minusDays(30));
    }
}