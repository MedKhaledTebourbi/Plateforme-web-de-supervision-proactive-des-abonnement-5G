package com.example.micro_reclamation.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_historique")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TicketHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ticket_id")
    @JsonIgnore
    private Ticket ticket;

    private String ancienStatut;
    private String nouveauStatut;
    private String action;
    private String description;
    private Long utilisateurId;
    private String utilisateurNom;
    private LocalDateTime dateAction;

    @Column(columnDefinition = "TEXT")
    private String detailsJson; // Pour stocker des détails supplémentaires en JSON

    @PrePersist
    protected void onCreate() {
        dateAction = LocalDateTime.now();
    }
}