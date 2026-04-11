package com.example.micro_map.Controller;

import com.example.micro_map.Entity.Client;
import com.example.micro_map.Entity.Pylone;
import com.example.micro_map.Repository.ClientRepository;
import com.example.micro_map.Service.AffectationService;
import com.example.micro_map.Service.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/affectation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AffectationController {

    private final AffectationService affectationService;
    private final GeocodingService geocodingService;
    private final ClientRepository clientRepository;

    @PostMapping("/auto")
    public String lancerAffectation() {
        affectationService.affecterClientsAutomatiquement();
        return "Affectation terminée avec succès";
    }
    @GetMapping
    public List<Client> getAll() {
        return clientRepository.findAll();
    }
    @GetMapping("/test-geocoding")
    public ResponseEntity<String> testGeocoding() {
        try {
            double[] coords = geocodingService.getCoordinatesFromAddress(
                    "Avenue Habib Bourguiba, Tunis, Tunisie"
            );
            return ResponseEntity.ok("✅ lat=" + coords[0] + " lon=" + coords[1]);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Erreur: " + e.getMessage());
        }
    }
}