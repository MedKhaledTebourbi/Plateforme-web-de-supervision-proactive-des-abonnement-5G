package com.example.micro_map.Entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonBackReference;
import java.util.List;

@Entity
@Table(name = "ZoneReseau")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@EntityListeners(AuditingEntityListener.class)
public class ZoneReseau {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long zone_id;

    private String nom;
    private String description;

    private Double bandePassanteMax;
    private Double chargeActuelle;

    private Double latitudeCentre;
    private Double longitudeCentre;
    private Double rayonCouverture;

    @OneToMany(mappedBy = "zoneReseau", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("zoneReseau")
    private List<Pylone> pylones;

    public Double getTauxUtilisation() {
        if (bandePassanteMax == 0) return 0.0;
        return (chargeActuelle / bandePassanteMax) * 100;
    }
    @Transient  // ← pas persisté, juste un calcul utilitaire
    public Double calculerChargeDepuisPylones() {
        if (pylones == null || pylones.isEmpty()) return 0.0;
        return pylones.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getEstBloque()))
                .mapToDouble(p -> p.getChargeActuelle() != null ? p.getChargeActuelle() : 0.0)
                .sum();
    }
    // Getters & Setters
}