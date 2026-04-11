package com.example.micro_reclamation.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chantier")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Chantier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;
    private String description;

    // MAINTENANCE, INSTALLATION, REPARATION, EXTENSION
    private String typeChantier;

    // ✅ Référence au pylone dans micro_map (pas de relation JPA — microservice)
    private Long pyloneId;
    private String pyloneNom;

    // ✅ Région et coordonnées du pylone (copiées à la création)
    private String region;
    private Double latitude;
    private Double longitude;

    // PLANIFIE, EN_COURS, VALIDE, TERMINE, ANNULE
    private String statut;

    private Long technicienId;
    private String technicienNom;

    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private LocalDateTime dateCreation;
    private LocalDateTime dateValidation;

    // ✅ true quand chantier VALIDE → bloque client et réclamation
    private Boolean pyloneBloque = false;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "PLANIFIE";
        if (pyloneBloque == null) pyloneBloque = false;
    }
}