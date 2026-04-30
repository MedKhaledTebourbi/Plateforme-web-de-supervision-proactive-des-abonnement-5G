package com.example.micro_map.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contrat_traite")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ContratTraite {

    @Id
    private Long contratId; // clé = ID unique du contrat

    private String typeEvenement;
    private LocalDateTime traiteLe;
}