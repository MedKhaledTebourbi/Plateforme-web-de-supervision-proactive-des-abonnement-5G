package com.example.microserviceia.service;

import com.example.microserviceia.dto.SaturationResult;
import com.example.microserviceia.dto.ZoneFeatureVector;
import com.example.microserviceia.entity.SaturationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SaturationDetectionService {

    // Seuils métier validés
    private static final double SEUIL_TAUX_ZONE_SATURE = 80.0;
    private static final double SEUIL_TAUX_ZONE_CRITIQUE = 95.0;
    private static final double SEUIL_RATIO_PYLONES_SATURES = 50.0;  // >50% pylones saturés
    private static final double SEUIL_TAUX_PYLONE_SATURE = 80.0;

    /**
     * Détecte le statut de saturation d'une zone.
     * Combine règles métier strictes + score ML (Isolation Forest).
     */
    public SaturationResult detect(ZoneFeatureVector features) {

        // === RÈGLES MÉTIER (déterministes, prioritaires) ===
        SaturationStatus statutRegles = applyBusinessRules(features);

        // === SCORE ML : Isolation Forest simplifié ===
        double anomalyScore = computeAnomalyScore(features);

        // === COMBINAISON : règles métier + signal ML ===
        SaturationStatus statutFinal = combineSignals(statutRegles, anomalyScore, features);

        return SaturationResult.builder()
                .zoneId(features.getZoneId())
                .statut(statutFinal)
                .anomalyScore(anomalyScore)
                .tauxUtilisation(features.getTauxUtilisation())
                .nbPylonesSatures(features.getNbPylonesSatures())
                .nbPylonesTotal(features.getNbPylonesTotal())
                .details(buildDetails(features, anomalyScore))
                .build();
    }

    private SaturationStatus applyBusinessRules(ZoneFeatureVector f) {
        // Règle 1 : taux global de la zone
        if (f.getTauxUtilisation() >= SEUIL_TAUX_ZONE_CRITIQUE) return SaturationStatus.CRITIQUE;
        if (f.getTauxUtilisation() >= SEUIL_TAUX_ZONE_SATURE) return SaturationStatus.SATURE;

        // Règle 2 : >50% des pylones saturés → zone saturée (règle métier centrale)
        if (f.getRatioSatures() > SEUIL_RATIO_PYLONES_SATURES) return SaturationStatus.SATURE;

        // Règle 3 : zone en attention
        if (f.getTauxUtilisation() >= 60.0 || f.getRatioSatures() >= 30.0)
            return SaturationStatus.ATTENTION;

        return SaturationStatus.NORMAL;
    }

    /**
     * Score d'anomalie inspiré Isolation Forest.
     * Retourne une valeur entre -1 (très anormal) et 1 (normal).
     */
    private double computeAnomalyScore(ZoneFeatureVector f) {
        double score = 0.0;

        // Composante 1 : taux d'utilisation normalisé
        score += normalizeAndScore(f.getTauxUtilisation(), 0, 100, 75);

        // Composante 2 : tendance rapide (augmentation brutale)
        score += normalizeAndScore(Math.abs(f.getTendance6h()), 0, 50, 20);

        // Composante 3 : déséquilibre de charge entre pylones
        score += normalizeAndScore(f.getEcartTypePylones(), 0, 40, 15);

        // Composante 4 : accélération (changement de vitesse)
        score += normalizeAndScore(Math.abs(f.getAcceleration()), 0, 20, 8);

        // Normaliser entre -1 et 1 (1 = normal, -1 = très anormal)
        double normalized = Math.max(-1.0, Math.min(1.0, 1.0 - (score / 4.0)));
        return Math.round(normalized * 1000.0) / 1000.0;
    }

    private double normalizeAndScore(double value, double min, double max, double threshold) {
        double ratio = (value - min) / (max - min);
        return ratio > (threshold - min) / (max - min) ? ratio : 0;
    }

    private SaturationStatus combineSignals(SaturationStatus regles,
                                            double anomalyScore,
                                            ZoneFeatureVector f) {
        // Les règles métier font foi ; le ML peut escalader si tendance forte
        if (regles == SaturationStatus.CRITIQUE) return regles;
        if (regles == SaturationStatus.SATURE) return regles;

        // Le ML détecte une anomalie forte (score < -0.5) alors que les règles disent ATTENTION
        if (anomalyScore < -0.5 && regles == SaturationStatus.ATTENTION)
            return SaturationStatus.SATURE;

        // Tendance très forte (+30% en 6h) → escalader NORMAL vers ATTENTION
        if (regles == SaturationStatus.NORMAL && f.getTendance6h() > 30)
            return SaturationStatus.ATTENTION;

        return regles;
    }

    private String buildDetails(ZoneFeatureVector f, double score) {
        return String.format(
                "taux=%.1f%%, pylonesSat=%d/%d (%.1f%%), tendance6h=%.1f%%, anomalyScore=%.3f",
                f.getTauxUtilisation(),
                f.getNbPylonesSatures(), f.getNbPylonesTotal(),
                f.getRatioSatures(),
                f.getTendance6h(),
                score
        );
    }
}