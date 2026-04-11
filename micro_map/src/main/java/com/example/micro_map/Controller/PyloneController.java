package com.example.micro_map.Controller;

import com.example.micro_map.Entity.Pylone;
import com.example.micro_map.Repository.PyloneRepository;
import com.example.micro_map.Service.PyloneService;
import com.example.micro_map.Service.ReaffectationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pylones")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PyloneController {

    private final PyloneService service;
    private final PyloneRepository pyloneRepository;
    private final ReaffectationService reaffectationService;

    @GetMapping
    public List<Pylone> getAll() {
        return service.getAll();
    }

    /*@GetMapping("/{id}")
    public ResponseEntity<Pylone> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }*/

    @GetMapping("/zone/{zoneId}")
    public List<Pylone> getByZone(@PathVariable Long zoneId) {
        return service.getByZone(zoneId);
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Pylone> update(@PathVariable Long id, @RequestBody Pylone pylone) {
        return service.update(id, pylone)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<Pylone> create(@RequestBody Pylone pylone) {
        return ResponseEntity.ok(service.create(pylone));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (service.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    // Dans PyloneController.java du micro_map

    @GetMapping("/{id}")
    public ResponseEntity<?> getPyloneById(@PathVariable Long id) {
        return pyloneRepository.findById(id)
                .map(pylone -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", pylone.getId());
                    response.put("nom", pylone.getNom());
                    response.put("latitude", pylone.getLatitude());
                    response.put("longitude", pylone.getLongitude());
                    response.put("capaciteMax", pylone.getCapaciteMax());
                    response.put("chargeActuelle", pylone.getChargeActuelle());
                    response.put("zoneNom", pylone.getZoneReseau() != null ?
                            pylone.getZoneReseau().getNom() : null);
                    response.put("bloque", pylone.getEstBloque() != null ?
                            pylone.getEstBloque() : false);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> pyloneExists(@PathVariable Long id) {
        return ResponseEntity.ok(pyloneRepository.existsById(id));
    }


    @PutMapping("/{id}/bloquer")
    public ResponseEntity<?> bloquerPylone(
            @PathVariable Long id,
            @RequestParam boolean bloque) {

        return service.getById(id).map(pylone -> {
            pylone.setEstBloque(bloque);
            pyloneRepository.save(pylone);

            // ✅ Déblocage → réaffectation automatique
            if (!bloque) {
                reaffectationService.reaffecterClientsEnAttente(id);
            }

            return ResponseEntity.ok(Map.of(
                    "id", id,
                    "estBloque", bloque,
                    "message", bloque
                            ? "Pylône bloqué"
                            : "Pylône débloqué — clients réaffectés automatiquement"
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ✅ Vérifier si un pylône est bloqué (appelé par AffectationService)
    @GetMapping("/check/{id}")
    public ResponseEntity<Boolean> checkPyloneBloque(@PathVariable Long id) {
        return service.getById(id)
                .map(p -> ResponseEntity.ok(Boolean.TRUE.equals(p.getEstBloque())))
                .orElse(ResponseEntity.ok(false));
    }
}