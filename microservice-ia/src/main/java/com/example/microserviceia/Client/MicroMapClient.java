package com.example.microserviceia.Client;

import com.example.microserviceia.dto.PyloneDTO;
import com.example.microserviceia.dto.ZoneReseauDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;


@FeignClient(name = "micro-map", url = "${micro-map.url:http://localhost:8081}")
public interface MicroMapClient {

    @GetMapping("/api/zones")
    List<ZoneReseauDTO> getAllZones();

    @GetMapping("/api/zones/{id}")
    ZoneReseauDTO getZoneById(@PathVariable Long id);

    @GetMapping("/api/pylones")
    List<PyloneDTO> getAllPylones();
}