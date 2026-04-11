package com.example.micro_reclamation.Controller;

import com.example.micro_reclamation.Entity.Chantier;
import com.example.micro_reclamation.Service.ChantierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/chantiers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChantierController {

    private final ChantierService chantierService;

    @PostMapping
    public ResponseEntity<?> creer(@RequestBody Chantier chantier) {
        try {
            return ResponseEntity.ok(chantierService.creerChantier(chantier));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public List<Chantier> getAll() {
        return chantierService.getAllChantiers();
    }

    @GetMapping("/pylone/{pyloneId}")
    public List<Chantier> getByPylone(@PathVariable Long pyloneId) {
        return chantierService.getChantiersByPylone(pyloneId);
    }

    @GetMapping("/region/{region}")
    public List<Chantier> getByRegion(@PathVariable String region) {
        return chantierService.getChantiersByRegion(region);
    }

    @GetMapping("/technicien/{technicienId}")
    public List<Chantier> getByTechnicien(@PathVariable Long technicienId) {
        return chantierService.getChantiersByTechnicien(technicienId);
    }

    @PutMapping("/{id}/valider")
    public ResponseEntity<?> valider(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(chantierService.validerChantier(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/terminer")
    public ResponseEntity<?> terminer(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(chantierService.terminerChantier(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/annuler")
    public ResponseEntity<?> annuler(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(chantierService.annulerChantier(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ✅ Endpoint vérifié par micro_map avant ajout client/réclamation
    @GetMapping("/pylone/{pyloneId}/bloque")
    public ResponseEntity<Boolean> estBloque(@PathVariable Long pyloneId) {
        return ResponseEntity.ok(chantierService.pyloneEstBloque(pyloneId));
    }
    @GetMapping("/check/{pyloneId}")
    public ResponseEntity<Boolean> checkPyloneBloque(@PathVariable Long pyloneId) {
        boolean bloque = chantierService.pyloneEstBloque(pyloneId);
        return ResponseEntity.ok(bloque);
    }
}