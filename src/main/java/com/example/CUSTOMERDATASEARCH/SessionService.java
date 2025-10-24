package com.example.CUSTOMERDATASEARCH;

import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service class to manage session data and cleanup
 */
@Service
public class SessionService {

    // Use ConcurrentHashMap for thread safety
    private final Map<String, SecretKey> sessionStore = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionTimestamps = new ConcurrentHashMap<>();
    
    // Session timeout in milliseconds (30 minutes)
    private static final long SESSION_TIMEOUT = 30 * 60 * 1000;

    public void storeSession(String sessionId, SecretKey aesKey) {
        sessionStore.put(sessionId, aesKey);
        sessionTimestamps.put(sessionId, System.currentTimeMillis());
        System.out.println("Session stored: " + sessionId);
    }

    public SecretKey getSessionKey(String sessionId) {
        // Check if session exists and is not expired
        Long timestamp = sessionTimestamps.get(sessionId);
        if (timestamp == null) {
            System.out.println("Session not found: " + sessionId);
            return null;
        }
        
        if (System.currentTimeMillis() - timestamp > SESSION_TIMEOUT) {
            // Session expired, remove it
            removeSession(sessionId);
            System.out.println("Session expired and removed: " + sessionId);
            return null;
        }
        
        // Update timestamp for session activity
        sessionTimestamps.put(sessionId, System.currentTimeMillis());
        return sessionStore.get(sessionId);
    }

    public void removeSession(String sessionId) {
        sessionStore.remove(sessionId);
        sessionTimestamps.remove(sessionId);
        System.out.println("Session removed: " + sessionId);
    }

    public int getActiveSessionCount() {
        cleanupExpiredSessions();
        return sessionStore.size();
    }

    /**
     * Clean up expired sessions
     */
    public void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        sessionTimestamps.entrySet().removeIf(entry -> {
            boolean expired = currentTime - entry.getValue() > SESSION_TIMEOUT;
            if (expired) {
                sessionStore.remove(entry.getKey());
                System.out.println("Cleaned up expired session: " + entry.getKey());
            }
            return expired;
        });
    }
}