package com.seatbook.controller;

import com.seatbook.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    /** Liveness: process is up, no dependencies. */
    @GetMapping("/healthz")
    public Map<String, String> live() {

        return Map.of("status", "ok");
    }

    /** Readiness: really checks the database; 503 when it is down (fails closed). */
    @GetMapping("/readyz")
    public ResponseEntity<Map<String, String>> ready() {

        if (healthService.isReady())
            return ResponseEntity.ok(Map.of("status", "ready", "db", "up"));

        return ResponseEntity.status(503).body(Map.of("status", "not-ready", "db", "down"));
    }
}
