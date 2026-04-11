package com.example.micro_map.Controller;

import com.example.micro_map.Entity.Pylone;
import com.example.micro_map.Entity.ZoneReseau;
import com.example.micro_map.Repository.PyloneRepository;
import com.example.micro_map.Repository.ZoneReseauRepository;
import com.example.micro_map.Scheduler.SimulationScheduler;
import com.example.micro_map.Service.ZoneReseauService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.micro_map.Entity.ZoneReseau;
import com.example.micro_map.Service.ZoneReseauService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
@CrossOrigin
public class ZoneReseauController {

    private final ZoneReseauService service;
    private final SimulationScheduler simulationScheduler;
    private final ZoneReseauRepository zoneRepo;
    private final PyloneRepository pyloneRepo;

    @GetMapping
    public List<ZoneReseau> getAll() {
        return service.getAllZones();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZoneReseau> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ZoneReseau create(@RequestBody ZoneReseau zone) {
        return service.create(zone);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZoneReseau> update(@PathVariable Long id, @RequestBody ZoneReseau zone) {
        return service.update(id, zone)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (service.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    @PostMapping("/zones/simulate-load")
    public ResponseEntity<Map<String, Object>> simulateLoadManual() {
        simulationScheduler.simulateNetworkLoad();

        List<ZoneReseau> zones = zoneRepo.findAll();
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Simulation exécutée");
        result.put("zones_mises_a_jour", zones.size());
        result.put("timestamp", LocalDateTime.now().toString());

        // Résumé des taux
        List<Map<String, Object>> summary = zones.stream().map(z -> {
            Map<String, Object> m = new HashMap<>();
            m.put("zone", z.getNom());
            m.put("taux", String.format("%.1f%%", z.getTauxUtilisation()));
            return m;
        }).collect(Collectors.toList());
        result.put("summary", summary);

        return ResponseEntity.ok(result);
    }

    // Réinitialiser toutes les charges à leurs valeurs initiales
    @PostMapping("/zones/reset-load")
    public ResponseEntity<String> resetLoad() {
        List<ZoneReseau> zones = zoneRepo.findAll();
        // Remet chaque zone à 30-50% de sa capacité max aléatoirement
        Random rnd = new Random();
        for (ZoneReseau zone : zones) {
            double reset = zone.getBandePassanteMax() * (0.30 + rnd.nextDouble() * 0.20);
            zone.setChargeActuelle(reset);
            if (zone.getPylones() != null) {
                for (Pylone p : zone.getPylones()) {
                    double resetP = p.getCapaciteMax() * (0.25 + rnd.nextDouble() * 0.20);
                    p.setChargeActuelle(resetP);
                }
                pyloneRepo.saveAll(zone.getPylones());
            }
        }
        zoneRepo.saveAll(zones);
        return ResponseEntity.ok("Charges réinitialisées");
    }

    // Forcer une zone spécifique à un taux donné (pour tester la détection)
    @PostMapping("/zones/{id}/force-load")
    public ResponseEntity<String> forceLoad(
            @PathVariable Long id,
            @RequestParam double tauxPourcent) {

        ZoneReseau zone = zoneRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone non trouvée: " + id));

        double newCharge = zone.getBandePassanteMax() * (tauxPourcent / 100.0);
        zone.setChargeActuelle(newCharge);

        if (zone.getPylones() != null) {
            Random rnd = new Random();
            for (Pylone p : zone.getPylones()) {
                double pyloneCharge = p.getCapaciteMax() * (tauxPourcent / 100.0)
                        * (0.85 + rnd.nextDouble() * 0.30);
                pyloneCharge = Math.min(pyloneCharge, p.getCapaciteMax());
                p.setChargeActuelle(pyloneCharge);
            }
            pyloneRepo.saveAll(zone.getPylones());
        }
        zoneRepo.save(zone);

        return ResponseEntity.ok(String.format(
                "Zone '%s' forcée à %.1f%% (charge=%.2f Mbps)",
                zone.getNom(), tauxPourcent, newCharge));
    }
}