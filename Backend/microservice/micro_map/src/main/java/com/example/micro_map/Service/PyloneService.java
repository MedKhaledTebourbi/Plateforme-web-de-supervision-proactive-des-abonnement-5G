package com.example.micro_map.Service;

import com.example.micro_map.Entity.Pylone;
import com.example.micro_map.Repository.PyloneRepository;
import com.example.micro_map.Repository.ZoneReseauRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PyloneService {

    private final PyloneRepository repository;
    private final ZoneReseauRepository zoneRepository;

    public List<Pylone> getAll() {
        return repository.findAll();
    }

    public Optional<Pylone> getById(Long id) {
        return repository.findById(id);
    }

    public List<Pylone> getByZone(Long zoneId) {
        return repository.findByZoneId(zoneId);
    }

    public Pylone create(Pylone pylone) {
        if (pylone.getZoneReseau() != null && pylone.getZoneReseau().getZone_id() != null) {
            zoneRepository.findById(pylone.getZoneReseau().getZone_id())
                    .ifPresent(pylone::setZoneReseau);
        }
        return repository.save(pylone);
    }

    public Optional<Pylone> update(Long id, Pylone updated) {
        return repository.findById(id).map(existing -> {
            existing.setNom(updated.getNom());
            existing.setLatitude(updated.getLatitude());
            existing.setLongitude(updated.getLongitude());
            existing.setCapaciteMax(updated.getCapaciteMax());
            existing.setChargeActuelle(updated.getChargeActuelle());
            if (updated.getZoneReseau() != null && updated.getZoneReseau().getZone_id() != null) {
                zoneRepository.findById(updated.getZoneReseau().getZone_id())
                        .ifPresent(existing::setZoneReseau);
            }
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }
    // ✅ AJOUTER cette méthode seulement
    public Optional<Pylone> bloquer(Long id, boolean bloque) {
        return repository.findById(id).map(pylone -> {
            pylone.setEstBloque(bloque);
            return repository.save(pylone);
        });
    }
}