package com.example.micro_reclamation.Entity;



import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "reclamation")
public class Reclamation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codeReclamation;

    private String adresse;

    private String typeReclamation;
    private Double latitude;
    private Double longitude;
    private LocalDate dateReclamation;      // date métier saisie

    @Column(updatable = false)
    private LocalDateTime dateCreation;     // horodatage technique @PrePersist

    private LocalDateTime dateCloture;      // rempli à la clôture
    private String motifCloture;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReclamationStatut statut = ReclamationStatut.OUVERTE;


    private Long pyloneId;      // rempli automatiquement par le service
    private String pyloneNom;
}