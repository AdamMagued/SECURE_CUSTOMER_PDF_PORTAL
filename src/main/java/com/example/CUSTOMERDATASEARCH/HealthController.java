package com.example.CUSTOMERDATASEARCH;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/health")
public class HealthController {

    private final SessionService sessionService;
    private final RestTemplate restTemplate;

    @Value("${laserfiche.api.url}")
    private String laserFicheApiUrl;
    
    @Value("${laserfiche.api.username}")
    private String apiUsername;
    
    @Value("${laserfiche.api.password}")
    private String apiPassword;

    public HealthController(SessionService sessionService, RestTemplate restTemplate) {
        this.sessionService = sessionService;
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("application", "NBK Customer Data Search");
        health.put("version", "2.0.0");
        health.put("integration", "Laserfiche LOS API");
        return ResponseEntity.ok(health);
    }

    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();

        try {
            // Basic health info
            health.put("status", "UP");
            health.put("timestamp", LocalDateTime.now());
            health.put("application", "NBK Customer Data Search");
            health.put("version", "2.0.0");

            // Runtime info
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> memory = new HashMap<>();
            memory.put("totalMB", runtime.totalMemory() / 1024 / 1024);
            memory.put("freeMB", runtime.freeMemory() / 1024 / 1024);
            memory.put("usedMB", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
            memory.put("maxMB", runtime.maxMemory() / 1024 / 1024);
            health.put("memory", memory);

            // Session info
            health.put("activeSessions", sessionService.getActiveSessionCount());

            // Laserfiche API info
            Map<String, Object> apiInfo = new HashMap<>();
            apiInfo.put("url", laserFicheApiUrl);
            apiInfo.put("username", apiUsername);
            apiInfo.put("connectivity", testLaserFicheConnectivity());
            health.put("laserFicheApi", apiInfo);

            // Java info
            Map<String, String> java = new HashMap<>();
            java.put("version", System.getProperty("java.version"));
            java.put("vendor", System.getProperty("java.vendor"));
            health.put("java", java);

        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(500).body(health);
        }

        return ResponseEntity.ok(health);
    }

    @GetMapping("/laserfiche-status")
    public ResponseEntity<Map<String, Object>> laserFicheStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            status.put("timestamp", LocalDateTime.now());
            status.put("apiUrl", laserFicheApiUrl);
            status.put("username", apiUsername);
            
            // Test API connectivity
            String connectivity = testLaserFicheConnectivity();
            status.put("connectivity", connectivity);
            status.put("status", connectivity.startsWith("Connected") ? "UP" : "DOWN");
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            status.put("error", e.getMessage());
            status.put("status", "ERROR");
            return ResponseEntity.status(500).body(status);
        }
    }

    private String testLaserFicheConnectivity() {
        try {
            // Create test request to Laserfiche API (using a test Case ID)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(apiUsername, apiPassword); // FIXED: Now uses actual password
            
            CustomerController.LaserFicheRequest testRequest = new CustomerController.LaserFicheRequest();
            testRequest.setCaseID("1"); // Test with Case ID 1
            testRequest.setRequestID("");
            testRequest.setDocumentType("national id");
            
            HttpEntity<CustomerController.LaserFicheRequest> entity = new HttpEntity<>(testRequest, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(laserFicheApiUrl, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return "Connected (HTTP " + response.getStatusCode().value() + ")";
            } else {
                return "Connected but returned " + response.getStatusCode();
            }
            
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 401/403 means we connected but auth failed
            if (e.getStatusCode().value() == 401) {
                return "Connected - Authentication failed";
            } else if (e.getStatusCode().value() == 404) {
                return "Connected - Endpoint not found";
            } else {
                return "Connected - HTTP " + e.getStatusCode().value();
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            return "Not accessible - Network error: " + e.getMessage();
        } catch (Exception e) {
            return "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }
}