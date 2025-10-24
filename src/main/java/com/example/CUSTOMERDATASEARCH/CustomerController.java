package com.example.CUSTOMERDATASEARCH;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletResponse;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    @Value("${laserfiche.api.url}")
    private String laserFicheApiUrl;
    
    @Value("${laserfiche.api.username}")
    private String apiUsername;
    
    @Value("${laserfiche.api.password}")
    private String apiPassword;

    private final SessionController sessionController;
    private final RestTemplate restTemplate;

    public CustomerController(SessionController sessionController, RestTemplate restTemplate) {
        this.sessionController = sessionController;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/download/{encryptedId}")
    public void downloadCustomerPdf(
            @PathVariable String encryptedId,
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "false") boolean download,
            HttpServletResponse response
    ) {
        try {
            // Validate session
            SecretKey aesKey = sessionController.getSessionKey(sessionId);
            if (aesKey == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid session");
                return;
            }

            // Decrypt customer ID (this becomes the CaseID for Laserfiche)
            int customerId = decryptCustomerId(encryptedId, aesKey);
            if (customerId == -1) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid customer ID");
                return;
            }

            // Fetch PDF from Laserfiche API
            byte[] pdfBytes = fetchPdfFromLaserFiche(customerId);
            if (pdfBytes == null || pdfBytes.length == 0) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "PDF not found for customer " + customerId);
                return;
            }

            // Serve PDF directly
            servePdfBytes(customerId, pdfBytes, download, response);

        } catch (Exception e) {
            log.error("Error serving customer PDF for encrypted ID: {}", encryptedId, e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error");
            } catch (IOException ignored) {}
        }
    }

    private byte[] fetchPdfFromLaserFiche(int caseId) {
        try {
            log.info("Fetching PDF from Laserfiche API for CaseID: {}", caseId);
            
            // Create request headers with basic auth
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(apiUsername, apiPassword);
            
            // Create request body
            LaserFicheRequest requestBody = new LaserFicheRequest();
            requestBody.setCaseID(String.valueOf(caseId));
            requestBody.setRequestID("");  // Empty as shown in your example
            requestBody.setDocumentType("national id");  // As shown in your example
            
            HttpEntity<LaserFicheRequest> entity = new HttpEntity<>(requestBody, headers);
            
            // Call Laserfiche API
            ResponseEntity<LaserFicheResponse> response = restTemplate.postForEntity(
                laserFicheApiUrl, entity, LaserFicheResponse.class);
            
            if (response.getBody() != null) {
                LaserFicheResponse apiResponse = response.getBody();
                
                // Check response code
                if (!"200".equals(apiResponse.getResponseCode()) && !"0".equals(apiResponse.getResponseCode())) {
                    log.warn("Laserfiche API returned error code {} for CaseID {}: {}", 
                            apiResponse.getResponseCode(), caseId, apiResponse.getRespondMessage());
                    return null;
                }
                
                // Get StreamBytes (should be BASE64 PDF)
                String streamBytes = apiResponse.getStreamBytes();
                if (streamBytes != null && !streamBytes.trim().isEmpty()) {
                    try {
                        // Decode BASE64 to PDF bytes
                        byte[] pdfBytes = Base64.getDecoder().decode(streamBytes);
                        log.info("Successfully fetched PDF for CaseID {} ({} bytes)", caseId, pdfBytes.length);
                        return pdfBytes;
                    } catch (IllegalArgumentException e) {
                        log.error("Failed to decode BASE64 StreamBytes for CaseID {}: {}", caseId, e.getMessage());
                        return null;
                    }
                } else {
                    log.warn("Empty StreamBytes returned for CaseID {}", caseId);
                }
            } else {
                log.warn("Null response body from Laserfiche API for CaseID {}", caseId);
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch PDF from Laserfiche API for CaseID {}: {}", caseId, e.getMessage(), e);
        }
        
        return null;
    }

    private void servePdfBytes(int customerId, byte[] pdfBytes, boolean download, HttpServletResponse response) throws IOException {
        // Set PDF headers
        response.setContentType("application/pdf");
        String disposition = download ? "attachment" : "inline";
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, 
            disposition + "; filename=\"customer_" + customerId + "_document.pdf\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setContentLength(pdfBytes.length);

        // Stream PDF content to browser
        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(pdfBytes);
            outputStream.flush();
        }

        log.info("Successfully served PDF for customer {} ({} bytes, download={})", 
                customerId, pdfBytes.length, download);
    }

    @GetMapping("/{encryptedId}/info")
    public ResponseEntity<Map<String, Object>> getCustomerInfo(
            @PathVariable String encryptedId,
            @RequestParam String sessionId
    ) {
        try {
            SecretKey aesKey = sessionController.getSessionKey(sessionId);
            if (aesKey == null) {
                return ResponseEntity.status(403).body(Map.of("error", "Invalid session"));
            }

            int customerId = decryptCustomerId(encryptedId, aesKey);
            if (customerId == -1) {
                return ResponseEntity.status(400).body(Map.of("error", "Invalid customer ID"));
            }

            // Check if PDF exists by trying to fetch it
            byte[] pdfBytes = fetchPdfFromLaserFiche(customerId);
            boolean pdfExists = pdfBytes != null && pdfBytes.length > 0;
            
            Map<String, Object> info = new HashMap<>();
            info.put("customerId", customerId);
            info.put("caseId", String.valueOf(customerId));
            info.put("pdfExists", pdfExists);
            info.put("source", "laserfiche-api");
            info.put("apiEndpoint", laserFicheApiUrl);
            
            if (pdfExists) {
                info.put("pdfSize", pdfBytes.length);
            }

            return ResponseEntity.ok(info);

        } catch (Exception e) {
            log.error("Error getting customer info for encrypted ID: {}", encryptedId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @PostMapping("/{encryptedId}/test-api")
    public ResponseEntity<Map<String, Object>> testLaserFicheApi(
            @PathVariable String encryptedId,
            @RequestParam String sessionId
    ) {
        try {
            SecretKey aesKey = sessionController.getSessionKey(sessionId);
            if (aesKey == null) {
                return ResponseEntity.status(403).body(Map.of("error", "Invalid session"));
            }

            int customerId = decryptCustomerId(encryptedId, aesKey);
            if (customerId == -1) {
                return ResponseEntity.status(400).body(Map.of("error", "Invalid customer ID"));
            }

            // Test API call and return raw response
            Map<String, Object> testResult = new HashMap<>();
            testResult.put("customerId", customerId);
            testResult.put("caseId", String.valueOf(customerId));
            testResult.put("apiUrl", laserFicheApiUrl);
            
            try {
                byte[] pdfBytes = fetchPdfFromLaserFiche(customerId);
                testResult.put("success", pdfBytes != null);
                testResult.put("pdfSize", pdfBytes != null ? pdfBytes.length : 0);
                testResult.put("message", pdfBytes != null ? "PDF fetched successfully" : "No PDF data returned");
            } catch (Exception e) {
                testResult.put("success", false);
                testResult.put("error", e.getMessage());
            }

            return ResponseEntity.ok(testResult);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Test failed: " + e.getMessage()));
        }
    }

    // RSA/AES decryption - CRITICAL FOR SECURITY
    private int decryptCustomerId(String encryptedId, SecretKey aesKey) {
        try {
            byte[] combined = Base64.getUrlDecoder().decode(encryptedId);
            if (combined.length < 12) {
                log.warn("Encrypted ID too short: {} bytes", combined.length);
                return -1;
            }
            
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] cipherText = Arrays.copyOfRange(combined, 12, combined.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
            
            byte[] plainBytes = cipher.doFinal(cipherText);
            int customerId = Integer.parseInt(new String(plainBytes, StandardCharsets.UTF_8));
            
            log.debug("Successfully decrypted customer ID: {}", customerId);
            return customerId;
            
        } catch (Exception e) {
            log.error("Failed to decrypt customer ID: {}", encryptedId, e);
            return -1;
        }
    }

    // Laserfiche API Request class
    public static class LaserFicheRequest {
        private String CaseID;
        private String RequestID;
        private String DocumentType;

        // Getters and setters
        public String getCaseID() { return CaseID; }
        public void setCaseID(String caseID) { CaseID = caseID; }
        public String getRequestID() { return RequestID; }
        public void setRequestID(String requestID) { RequestID = requestID; }
        public String getDocumentType() { return DocumentType; }
        public void setDocumentType(String documentType) { DocumentType = documentType; }
    }

    // Laserfiche API Response class
    public static class LaserFicheResponse {
        private String EntryID;
        private String ResponseCode;
        private String RespondMessage;
        private String StreamBytes;

        // Getters and setters
        public String getEntryID() { return EntryID; }
        public void setEntryID(String entryID) { EntryID = entryID; }
        public String getResponseCode() { return ResponseCode; }
        public void setResponseCode(String responseCode) { ResponseCode = responseCode; }
        public String getRespondMessage() { return RespondMessage; }
        public void setRespondMessage(String respondMessage) { RespondMessage = respondMessage; }
        public String getStreamBytes() { return StreamBytes; }
        public void setStreamBytes(String streamBytes) { StreamBytes = streamBytes; }
    }
}