package com.example.microserviceia.service;

import com.example.microserviceia.Repository.SaturationRecordRepository;
import com.example.microserviceia.dto.PyloneDTO;
import com.example.microserviceia.dto.ZoneFeatureVector;
import com.example.microserviceia.dto.ZoneReseauDTO;
import com.example.microserviceia.entity.SaturationRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureEngineeringService {

    private final SaturationRecordRepository recordRepository;

    /**
     * Calcule le vecteur de features pour une zone à partir des données temps réel
     * et de l'historique stocké.
     */
    public ZoneFeatureVector extractFeatures(ZoneReseauDTO zone) {
        List<PyloneDTO> pylones = zone.getPylones();

        // Feature 1 : taux d'utilisation global de la zone
        double tauxZone = zone.getTauxUtilisation();

        // Feature 2 : % de pylones saturés (charge > 80% capacité)
        long pylonesSatures = pylones.stream()
                .filter(p -> p.getTauxUtilisation() >= 80.0)
                .count();
        double ratioSatures = pylones.isEmpty() ? 0 :
                (double) pylonesSatures / pylones.size() * 100;

        // Feature 3 : taux moyen des pylones
        double tauxMoyenPylones = pylones.stream()
                .mapToDouble(PyloneDTO::getTauxUtilisation)
                .average().orElse(0.0);

        // Feature 4 : écart-type (mesure de déséquilibre de charge)
        double ecartType = calculateStdDev(pylones);

        // Feature 5 : tendance sur les 6 dernières heures (delta taux)
        double tendance = calculateTendance(zone.getZone_id(), 6);

        // Feature 6 : vitesse d'augmentation (dérivée seconde)
        double acceleration = calculateAcceleration(zone.getZone_id());

        // Feature 7 : nombre de pylones bloqués
        long nbBloques = pylones.stream()
                .filter(p -> Boolean.TRUE.equals(p.getEstBloque()))
                .count();

        return ZoneFeatureVector.builder()
                .zoneId(zone.getZone_id())
                .zoneNom(zone.getNom())
                .tauxUtilisation(tauxZone)
                .ratioSatures(ratioSatures)
                .tauxMoyenPylones(tauxMoyenPylones)
                .ecartTypePylones(ecartType)
                .tendance6h(tendance)
                .acceleration(acceleration)
                .nbPylonesBloques((int) nbBloques)
                .nbPylonesSatures((int) pylonesSatures)
                .nbPylonesTotal(pylones.size())
                .build();
    }

    private double calculateStdDev(List<PyloneDTO> pylones) {
        if (pylones.size() < 2) return 0.0;
        double mean = pylones.stream().mapToDouble(PyloneDTO::getTauxUtilisation).average().orElse(0);
        double variance = pylones.stream()
                .mapToDouble(p -> Math.pow(p.getTauxUtilisation() - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    private double calculateTendance(Long zoneId, int heures) {
        LocalDateTime since = LocalDateTime.now().minusHours(heures);
        List<SaturationRecord> history = recordRepository
                .findByZoneIdAndTimestampAfterOrderByTimestamp(zoneId, since);

        if (history.size() < 2) return 0.0;

        double first = history.get(0).getTauxUtilisation();
        double last = history.get(history.size() - 1).getTauxUtilisation();
        return last - first; // delta positif = tendance à la hausse
    }

    private double calculateAcceleration(Long zoneId) {
        double tendance1h = calculateTendance(zoneId, 1);
        double tendance3h = calculateTendance(zoneId, 3);
        return tendance1h - (tendance3h / 3); // accélération récente vs moyenne
    }
}