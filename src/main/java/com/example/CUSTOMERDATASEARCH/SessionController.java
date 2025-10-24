package com.example.CUSTOMERDATASEARCH;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "*")
public class SessionController {

    private final KeyController keyController;
    private final SessionService sessionService;

    public SessionController(KeyController keyController, SessionService sessionService) {
        this.keyController = keyController;
        this.sessionService = sessionService;
    }

    @PostMapping("/session/start")
    public ResponseEntity<?> startSession(@RequestBody Map<String, String> body) {
        try {
            System.out.println("Session start requested");
            
            String encryptedKey = body.get("encryptedKey");
            if (encryptedKey == null) {
                System.err.println("No encryptedKey provided in request body");
                return ResponseEntity.badRequest().body("No encryptedKey provided");
            }
            
            System.out.println("EncryptedKey received: " + encryptedKey.substring(0, Math.min(20, encryptedKey.length())) + "...");

            PrivateKey privateKey = keyController.getKeyPair().getPrivate();
            System.out.println("Private key obtained");
            
            byte[] encryptedBytes;
            try {
                encryptedBytes = Base64.getDecoder().decode(encryptedKey);
                System.out.println("Base64 decode successful, length: " + encryptedBytes.length);
            } catch (Exception e) {
                System.err.println("Base64 decode failed: " + e.getMessage());
                return ResponseEntity.badRequest().body("Invalid base64 encoding");
            }

            // Use default RSA/OAEP (SHA-1) to match JavaScript
            javax.crypto.Cipher cipher;
            try {
                cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding");
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey);
                System.out.println("Cipher initialized with default RSA/ECB/OAEPPadding (SHA-1)");
            } catch (Exception e) {
                System.err.println("Cipher initialization failed: " + e.getMessage());
                e.printStackTrace();
                return ResponseEntity.internalServerError().body("Cipher initialization failed: " + e.getMessage());
            }
            
            byte[] rawAes;
            try {
                rawAes = cipher.doFinal(encryptedBytes);
                System.out.println("RSA decryption successful, AES key length: " + rawAes.length);
            } catch (Exception e) {
                System.err.println("RSA decryption failed: " + e.getMessage());
                e.printStackTrace();
                return ResponseEntity.badRequest().body("RSA decryption failed");
            }

            SecretKey aesKey = new SecretKeySpec(rawAes, "AES");
            String sessionId = UUID.randomUUID().toString();
            
            // Use SessionService to store the session
            sessionService.storeSession(sessionId, aesKey);
            
            System.out.println("Session created successfully: " + sessionId);
            System.out.println("Active sessions: " + sessionService.getActiveSessionCount());

            Map<String, String> resp = new HashMap<>();
            resp.put("sessionId", sessionId);
            return ResponseEntity.ok(resp);
            
        } catch (Exception e) {
            System.err.println("Unexpected error in session start: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Internal server error: " + e.getMessage());
        }
    }

    public SecretKey getSessionKey(String sessionId) {
        return sessionService.getSessionKey(sessionId);
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<?> closeSession(@PathVariable String sessionId) {
        sessionService.removeSession(sessionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/session/count")
    public ResponseEntity<Map<String, Integer>> getSessionCount() {
        Map<String, Integer> response = new HashMap<>();
        response.put("activeSessions", sessionService.getActiveSessionCount());
        return ResponseEntity.ok(response);
    }
}