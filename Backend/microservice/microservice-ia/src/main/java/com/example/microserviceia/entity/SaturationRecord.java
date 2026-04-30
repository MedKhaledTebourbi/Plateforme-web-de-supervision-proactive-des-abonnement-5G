package com.example.microserviceia.entity;
import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;


@Getter
@Setter


@Entity
@Table(name = "saturation_record")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SaturationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long zoneId;
    private String zoneNom;

    // Taux d'utilisation réel au moment du snapshot (0-100)
    private Double tauxUtilisation;

    // Nombre de pylones saturés dans la zone
    private Integer nbPylonesSatures;
    private Integer nbPylonesTotal;

    // Statut calculé
    @Enumerated(EnumType.STRING)
    private SaturationStatus statut; // NORMAL, ATTENTION, SATURE, CRITIQUE

    // Score d'anomalie (Isolation Forest : -1 = anomalie, 1 = normal)
    private Double anomalyScore;

    // Prédiction : dans combien d'heures la zone sera saturée
    private Double heuresAvantSaturation; // null si déjà saturée ou pas de risque

    // Date prévue de saturation
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime datePredicteSaturation;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        this.timestamp = LocalDateTime.now();
    }
}