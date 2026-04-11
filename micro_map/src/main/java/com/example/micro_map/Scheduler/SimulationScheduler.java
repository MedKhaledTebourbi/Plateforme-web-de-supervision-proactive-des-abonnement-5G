package com.example.micro_map.Scheduler;

import com.example.micro_map.Entity.Pylone;
import com.example.micro_map.Entity.ZoneReseau;
import com.example.micro_map.Repository.PyloneRepository;
import com.example.micro_map.Repository.ZoneReseauRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class SimulationScheduler {

    private final ZoneReseauRepository zoneRepo;
    private final PyloneRepository pyloneRepo;
    private final Random random = new Random();

    // Toutes les 2 minutes
    @Scheduled(fixedRate = 120000)
    @Transactional
    public void simulateNetworkLoad() {
        List<ZoneReseau> zones = zoneRepo.findAll();
        int heure = LocalDateTime.now().getHour();
        double tendanceBase = getTendanceHoraire(heure);

        for (ZoneReseau zone : zones) {
            // Variation = tendance horaire + bruit gaussien
            double variation = tendanceBase + random.nextGaussian() * 2.5;

            double newCharge = zone.getChargeActuelle() + variation;

            // Clamp entre 5% et 99% de la bande passante max
            double min = zone.getBandePassanteMax() * 0.05;
            double max = zone.getBandePassanteMax() * 0.99;
            newCharge = Math.max(min, Math.min(max, newCharge));

            zone.setChargeActuelle(newCharge);

            // Mettre à jour les pylones proportionnellement
            if (zone.getPylones() != null) {
                for (Pylone p : zone.getPylones()) {
                    if (p.getCapaciteMax() == null || p.getCapaciteMax() == 0) continue;

                    // Ratio de charge de la zone appliqué au pylone + bruit individuel
                    double ratioZone = newCharge / zone.getBandePassanteMax();
                    double varPylone = random.nextGaussian() * 1.5;
                    double newPylone = p.getCapaciteMax() * ratioZone + varPylone;

                    newPylone = Math.max(0, Math.min(p.getCapaciteMax(), newPylone));
                    p.setChargeActuelle(newPylone);
                }
                pyloneRepo.saveAll(zone.getPylones());
            }
        }

        zoneRepo.saveAll(zones);
        log.info("[SIM] Charges réseau mises à jour — {}h — tendance: {}",
                heure, String.format("%.2f", tendanceBase));
    }

    /**
     * Profil de tendance horaire réaliste (réseau télécom Tunisie)
     * Valeur positive = charge monte, négative = charge descend
     */
    private double getTendanceHoraire(int heure) {
        return switch (heure) {
            case 0  -> -0.8;
            case 1  -> -1.0;
            case 2  -> -1.0;
            case 3  -> -0.9;
            case 4  -> -0.7;
            case 5  -> -0.3;
            case 6  ->  0.3;
            case 7  ->  0.9;
            case 8  ->  1.3;   // montée matin
            case 9  ->  1.0;
            case 10 ->  0.7;
            case 11 ->  0.5;
            case 12 ->  1.0;   // pic déjeuner
            case 13 ->  1.2;
            case 14 ->  0.4;
            case 15 ->  0.2;
            case 16 ->  0.6;
            case 17 ->  1.1;
            case 18 ->  1.6;   // pic soir
            case 19 ->  1.9;   // pic max
            case 20 ->  1.5;
            case 21 ->  1.0;
            case 22 ->  0.3;
            case 23 -> -0.3;
            default ->  0.0;
        };
    }
}