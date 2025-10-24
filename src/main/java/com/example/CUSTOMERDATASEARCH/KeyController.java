package com.example.CUSTOMERDATASEARCH;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

@RestController
@CrossOrigin(origins = "*")
public class KeyController {

    private final KeyPair keyPair;

    public KeyController() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();
        System.out.println("RSA KeyPair generated successfully");
    }

    @GetMapping("/keys/public")
    public String getPublicKey() {
        try {
            PublicKey pub = keyPair.getPublic();
            String base64Key = Base64.getEncoder().encodeToString(pub.getEncoded());
            System.out.println("Public key requested, returning base64 key of length: " + base64Key.length());
            return base64Key;
        } catch (Exception e) {
            System.err.println("Error getting public key: " + e.getMessage());
            throw new RuntimeException("Failed to get public key", e);
        }
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }
}