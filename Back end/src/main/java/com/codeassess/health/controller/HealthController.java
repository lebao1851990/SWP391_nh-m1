package com.codeassess.health.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final DataSource dataSource;

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean dbOk = false;

        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                dbOk = conn.isValid(2);
            } catch (Exception e) {
                log.warn("Database health check probe failed: {}", e.getMessage());
            }
        }

        body.put("status", dbOk ? "UP" : "DOWN");
        body.put("database", dbOk ? "UP" : "DOWN");
        body.put("message", dbOk ? "CodeAssess API and Database are operational" : "Database connection is not available");

        if (dbOk) {
            return ResponseEntity.ok(body);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
