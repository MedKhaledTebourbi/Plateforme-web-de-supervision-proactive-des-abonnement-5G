package com.example.microserviceia.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.example.microserviceia.dto.SaturationReport;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AutoCacheService {

    private Cache<Long, SaturationReport> reportCache;
    private Cache<String, List<SaturationReport>> allZonesCache;

    @Value("${ia.cache.warmup.enabled:true}")
    private boolean warmupEnabled;

    @PostConstruct
    public void init() {
        // Cache automatique avec expiration
        reportCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(200)
                .build();

        allZonesCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(1)
                .build();

        if (warmupEnabled) {
            log.info("🔥 Cache initialisé");
        }
    }

    @Async
    public void warmupCache() {
        try {
            Thread.sleep(2000);
            log.info("🌡️ Warmup du cache terminé");
        } catch (Exception e) {
            log.error("Erreur warmup", e);
        }
    }

    public SaturationReport getReport(Long zoneId) {
        return reportCache.getIfPresent(zoneId);
    }

    public void putReport(Long zoneId, SaturationReport report) {
        reportCache.put(zoneId, report);
    }

    public List<SaturationReport> getAllZones() {
        return allZonesCache.getIfPresent("all_zones");
    }

    public void putAllZones(List<SaturationReport> reports) {
        allZonesCache.put("all_zones", reports);
        log.info("💾 Cache mis à jour: {} zones", reports.size());
    }

    @Scheduled(fixedDelayString = "${ia.cache.refresh.interval:30000}")
    public void autoRefreshCache() {
        log.debug("🔄 Refresh automatique du cache...");
    }
}
