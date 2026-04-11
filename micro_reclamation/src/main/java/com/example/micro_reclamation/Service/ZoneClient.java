package com.example.micro_reclamation.Service;

import com.example.micro_reclamation.Entity.ZoneDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ZoneClient {

    private final RestTemplate restTemplate;

    public List<ZoneDTO> getZones() {
        return restTemplate.exchange(
                "http://localhost:8081/api/zones",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ZoneDTO>>() {}
        ).getBody();
    }
}