package com.example.micro_map.Scheduler;

import com.example.micro_map.Entity.Client;
import com.example.micro_map.Entity.Pylone;
import com.example.micro_map.Entity.ZoneReseau;
import com.example.micro_map.Repository.PyloneRepository;
import com.example.micro_map.Repository.ZoneReseauRepository;
import com.example.micro_map.Service.ZoneReseauService;
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
    private final ZoneReseauService zoneService;
    private final Random random = new Random();

    @Scheduled(fixedRate = 120000)
    @Transactional
    public void simulateNetworkLoad() {

        List<ZoneReseau> zones = zoneRepo.findAll();

        for (ZoneReseau zone : zones) {

            if (zone.getPylones() == null || zone.getPylones().isEmpty()) continue;

            for (Pylone p : zone.getPylones()) {

                if (p.getCapaciteMax() == null || p.getCapaciteMax() == 0) continue;
                if (Boolean.TRUE.equals(p.getEstBloque())) continue;

                double chargeCalculee = 0;

                // ✅ Calcul réel basé sur les clients
                if (p.getClients() != null && !p.getClients().isEmpty()) {

                    for (Client client : p.getClients()) {

                        if (client.getTypeAbonnement() == null) continue;

                        // Mbps → Gbps
                        chargeCalculee += client.getTypeAbonnement() / 1000.0;
                    }
                }

                double ancienneCharge = p.getChargeActuelle() != null ? p.getChargeActuelle() : 0.0;

                // ✅ Mise à jour uniquement si augmentation (anti-bruit)
                if (chargeCalculee > ancienneCharge) {
                    p.setChargeActuelle(chargeCalculee);
                } else {
                    p.setChargeActuelle(ancienneCharge);
                }

                // ✅ Respect capacité max
                double capaciteGbps = p.getCapaciteMax();
                double chargeFinale = Math.min(p.getChargeActuelle(), capaciteGbps * 0.99);

                p.setChargeActuelle(chargeFinale);
            }

            pyloneRepo.saveAll(zone.getPylones());

            // ✅ Charge zone = somme des pylônes
            double totalZone = zone.getPylones().stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getEstBloque()))
                    .mapToDouble(p -> p.getChargeActuelle() != null ? p.getChargeActuelle() : 0.0)
                    .sum();

            zone.setChargeActuelle(totalZone);
        }

        zoneRepo.saveAll(zones);

        log.info("[SIM] {} zones mises à jour (mode SANS BRUIT)", zones.size());
    }
    /**
     * Taux de clients connectés selon l'heure (0.0 → 1.0)
     * Exemple : 0.88 = 88% des clients sont connectés
     */
    private double getTauxConnexionHoraire(int heure) {
        return switch (heure) {
            case 0  -> 0.20;
            case 1  -> 0.15;
            case 2  -> 0.12;
            case 3  -> 0.12;
            case 4  -> 0.14;
            case 5  -> 0.18;
            case 6  -> 0.28;
            case 7  -> 0.42;
            case 8  -> 0.58;
            case 9  -> 0.65;
            case 10 -> 0.62;
            case 11 -> 0.60;
            case 12 -> 0.70;
            case 13 -> 0.72;
            case 14 -> 0.60;
            case 15 -> 0.55;
            case 16 -> 0.60;
            case 17 -> 0.68;
            case 18 -> 0.78;
            case 19 -> 0.88; // pic max
            case 20 -> 0.80;
            case 21 -> 0.68;
            case 22 -> 0.48;
            case 23 -> 0.30;
            default -> 0.40;
        };
    }
}