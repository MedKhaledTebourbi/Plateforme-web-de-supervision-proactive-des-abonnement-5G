package com.example.micro_map.Entity;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContratEvent {

    private String typeEvenement; // "NOUVEAU_CLIENT" ou "ANNULATION"
    private Long contratId;       // ID unique du contrat (pour éviter doublons)
    private String adresse;
    private Double typeAbonnement;
}