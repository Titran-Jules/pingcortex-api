package com.titran.pingcortex.controller;

import com.titran.pingcortex.dto.response.HealthResponse;
import com.titran.pingcortex.util.ManagedConnection;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Statement;

@AllArgsConstructor
@RestController
public class HealthController {
    private final DataSource dataSource;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> checkHealth() {
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             Statement statement = connection.get().createStatement()
        ) {
            statement.execute("SELECT 1");
            HealthResponse response = new HealthResponse("UP", "CONNECTED");
            return  ResponseEntity.ok(response);
        } catch (Exception e) {
            HealthResponse response = new HealthResponse("DOWN", e.getMessage());
            return ResponseEntity.status(503).body(response);
        }
    }
}
