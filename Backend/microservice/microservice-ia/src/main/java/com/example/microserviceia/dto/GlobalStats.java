package com.example.microserviceia.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class GlobalStats {

    private Integer totalZones;
    private Integer zonesNormales;
    private Integer zonesEnAttention;
    private Integer zonesSaturees;
    private Integer zonesCritiques;
    private LocalDateTime timestamp;
}