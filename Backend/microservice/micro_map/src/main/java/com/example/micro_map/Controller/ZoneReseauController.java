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
        // ✅ syncChargeActuelle en mémoire uniquement pour l'affichage
        zones.forEach(service::syncChargeActuelle);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Simulation exécutée");
        result.put("zones_mises_a_jour", zones.size());
        result.put("timestamp", LocalDateTime.now().toString());

        List<Map<String, Object>> summary = zones.stream().map(z -> {
            Map<String, Object> m = new HashMap<>();
            m.put("zone",   z.getNom());
            m.put("charge", String.format("%.1f Mbps", z.getChargeActuelle()));
            m.put("taux",   String.format("%.1f%%", z.getTauxUtilisation()));
            return m;
        }).collect(Collectors.toList());
        result.put("summary", summary);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/zones/reset-load")
    public ResponseEntity<String> resetLoad() {
        List<ZoneReseau> zones = zoneRepo.findAll();
        Random rnd = new Random();

        for (ZoneReseau zone : zones) {
            if (zone.getPylones() != null) {
                for (Pylone p : zone.getPylones()) {
                    // ✅ Valeur absolue, pas de cumul
                    double reset = p.getCapaciteMax() * (0.30 + rnd.nextDouble() * 0.20);
                    p.setChargeActuelle(reset);
                }
                pyloneRepo.saveAll(zone.getPylones());
            }
            // ✅ Recalcul depuis pylônes, puis save une seule fois
            service.syncChargeActuelle(zone);
        }
        zoneRepo.saveAll(zones);
        return ResponseEntity.ok("Charges réinitialisées");
    }

    @PostMapping("/zones/{id}/force-load")
    public ResponseEntity<Map<String, Object>> forceLoad(
            @PathVariable Long id,
            @RequestParam double tauxPourcent) {

        ZoneReseau zone = zoneRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone introuvable: " + id));

        if (zone.getPylones() == null || zone.getPylones().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Aucun pylône dans cette zone"));
        }

        Random rnd = new Random();
        for (Pylone p : zone.getPylones()) {
            // ✅ Valeur absolue depuis capacité max
            double pyloneCharge = p.getCapaciteMax() * (tauxPourcent / 100.0)
                    * (0.90 + rnd.nextDouble() * 0.20);
            pyloneCharge = Math.min(pyloneCharge, p.getCapaciteMax());
            p.setChargeActuelle(pyloneCharge);
        }
        pyloneRepo.saveAll(zone.getPylones());

        // ✅ Recalcul en mémoire puis un seul save
        service.syncChargeActuelle(zone);
        zoneRepo.save(zone);

        return ResponseEntity.ok(Map.of(
                "zone",    zone.getNom(),
                "charge",  String.format("%.2f Mbps", zone.getChargeActuelle()),
                "taux",    String.format("%.1f%%", zone.getTauxUtilisation()),
                "pylones", zone.getPylones().stream()
                        .map(p -> Map.of(
                                "nom",    p.getNom(),
                                "charge", String.format("%.2f", p.getChargeActuelle())
                        )).collect(Collectors.toList())
        ));
    }
}