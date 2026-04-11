package com.example.micro_reclamation.Entity;



import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

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

    private String adresse;

    private String typeReclamation;
    private Double latitude;
    private Double longitude;

    private LocalDate dateReclamation;
    private Long pyloneId;      // rempli automatiquement par le service
    private String pyloneNom;
}