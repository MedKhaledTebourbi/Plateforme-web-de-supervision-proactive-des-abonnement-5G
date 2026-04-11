package com.example.micro_reclamation.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ticket")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long zoneId;
    private String zoneNom;
    private String region;
    private String typePanne;
    private int nombreReclamations;

    @Enumerated(EnumType.STRING)
    private TicketStatut statut;

    private LocalDateTime dateCreation;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime dateDebutTraitement;
    private LocalDateTime dateFinTraitement;

    private Long updatedBy;
    private String updatedByName;
    private LocalDateTime dateMaj;
    @JsonProperty("assignedTo")
   private Long assignedTo  ;   // ID du technicien affecté
    private String assignedToName;
    private String priorite;
    private String description;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<TicketHistorique> historique = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        dateMaj = LocalDateTime.now();
        if (statut == null) {
            statut = TicketStatut.OUVERT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateMaj = LocalDateTime.now();
    }
}