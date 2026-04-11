package com.example.micro_map.Service;


import com.example.micro_map.Entity.ZoneReseau;
import com.example.micro_map.Repository.ZoneReseauRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ZoneReseauService {

    private final ZoneReseauRepository repository;

    public List<ZoneReseau> getAllZones() {
        return repository.findAll();
    }

    public Optional<ZoneReseau> getById(Long id) {
        return repository.findById(id);
    }

    public ZoneReseau create(ZoneReseau zone) {
        return repository.save(zone);
    }

    public Optional<ZoneReseau> update(Long id, ZoneReseau updated) {
        return repository.findById(id).map(existing -> {
            existing.setNom(updated.getNom());
            existing.setDescription(updated.getDescription());
            existing.setBandePassanteMax(updated.getBandePassanteMax());
            existing.setChargeActuelle(updated.getChargeActuelle());
            existing.setLatitudeCentre(updated.getLatitudeCentre());
            existing.setLongitudeCentre(updated.getLongitudeCentre());
            existing.setRayonCouverture(updated.getRayonCouverture());
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
}