package com.alexxlpz.productapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * {@code GET /health}, para el healthcheck de Docker (no pide clave): 200 «UP» si la base de datos responde y 503 «DOWN»
 * si no. Open Beauty Facts no cuenta: si se cae, la API sigue sirviendo lo que ya tiene guardado.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    /** Segundos que se espera a la base de datos (el healthcheck de Docker corta a los 5). */
    static final int TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(TIMEOUT_SECONDS)) return ResponseEntity.ok(Map.of("status", "UP"));
            log.warn("Healthcheck: la base de datos no responde");
        } catch (SQLException | RuntimeException e) {
            log.warn("Healthcheck: no hay conexión con la base de datos: {}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
    }
}
