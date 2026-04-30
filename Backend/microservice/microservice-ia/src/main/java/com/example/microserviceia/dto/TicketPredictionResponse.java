package com.example.microserviceia.dto;

import lombok.*;
import java.util.Map;

/**
 * Réponse du modèle ML après prédiction de priorité.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketPredictionResponse {

    /** Priorité prédite : BASSE | MOYENNE | HAUTE | CRITIQUE */
    private String prediction;

    /** Score de confiance entre 0 et 1 */
    private double confidence;

    /** Probabilités par classe */
    private Map<String, Double> probabilities;

    /** Features utilisées pour la prédiction */
    private Map<String, Object> featuresUsed;

    /** ID du ticket concerné */
    private Long ticketId;
    private String priorite;

    /** Version du modèle utilisé */
    private String modelVersion;

    /** Message d'erreur si échec */
    private String error;

    /** true si la prédiction vient du fallback (erreur Python) */
    private boolean fallback;
}