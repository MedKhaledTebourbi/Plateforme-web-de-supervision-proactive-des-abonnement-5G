package com.example.microserviceia.controller;

import com.example.microserviceia.dto.GuideRequest;
import com.example.microserviceia.dto.GuideResponse;
import com.example.microserviceia.service.GuideTechnicienService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ia/guide")
@RequiredArgsConstructor

@Slf4j
public class GuideTechnicienController {

    private final GuideTechnicienService guideService;

    @PostMapping("/ticket")
    public ResponseEntity<GuideResponse> getGuide(@RequestBody GuideRequest request) {
        log.info("[Guide] ticketId={} typePanne={}", request.getTicketId(), request.getTypePanne());
        return ResponseEntity.ok(guideService.getGuide(request));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Guide IA service UP");
    }
}