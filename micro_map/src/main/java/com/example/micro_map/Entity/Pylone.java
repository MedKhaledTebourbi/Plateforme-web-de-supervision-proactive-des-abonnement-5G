package com.example.micro_map.Entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonBackReference;

import java.util.List;

@Entity
@Table(name = "pylone")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "zoneReseau")
@EqualsAndHashCode(exclude = "zoneReseau")
@EntityListeners(AuditingEntityListener.class)
public class Pylone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;

    private Double latitude;
    private Double longitude;

    private Double capaciteMax;
    private Double chargeActuelle;
    private Double rayonCouverture;
    private Boolean estBloque = false;

    @ManyToOne
    @JoinColumn(name = "zone_id")
    @JsonBackReference
    private ZoneReseau zoneReseau;

    @OneToMany(mappedBy = "pylone")
    @JsonIgnoreProperties("pylone")
    private List<Client> clients;
    public Double getTauxUtilisation() {
        if (capaciteMax == null || capaciteMax == 0) return 0.0;
        return (chargeActuelle / capaciteMax) * 100;
    }
    @JsonProperty("zoneNom")
    public String getZoneNom() {
        return zoneReseau != null ? zoneReseau.getNom() : null;
    }
}