# NBK Customer Data Search Portal

A secure Spring Boot application that provides encrypted access to customer PDF documents stored in Laserfiche. The system implements end-to-end encryption using RSA and AES to ensure sensitive customer data remains protected.

## Overview

This project creates a secure portal where customers can retrieve their PDFs from a Laserfiche document management system. All communications between the client and server are encrypted, with customer IDs encrypted using AES-GCM before transmission.

**Key Features:**
- **End-to-End Encryption**: RSA-2048 key exchange followed by AES-256-GCM session encryption
- **Secure Session Management**: UUID-based session tokens with automatic key storage
- **Laserfiche Integration**: Direct API integration to fetch PDFs from Laserfiche LOS
- **PDF Streaming**: Support for both inline viewing and file download
- **CORS Support**: Cross-origin requests enabled for frontend flexibility
- **Health Monitoring**: Detailed health checks including Laserfiche API connectivity
- **Connection Pooling**: Optimized HTTP client with configurable connection limits

## Tech Stack

- **Backend**: Java 11+, Spring Boot 3.x
- **Security**: RSA-2048, AES-256-GCM encryption
- **HTTP Client**: Apache HttpComponents 5 with connection pooling
- **Frontend**: Vanilla JavaScript with Web Crypto API
- **API Integration**: Laserfiche LOS (Line of Business)

## Project Structure

```
src/main/java/com/example/CUSTOMERDATASEARCH/
├── CustomerdatasearchApplication.java      # Spring Boot entry point
├── CorsConfig.java                         # CORS configuration
├── KeyController.java                      # RSA key pair generation & serving
├── RestTemplateConfig.java                 # HTTP client configuration
├── SessionController.java                  # Session start & lifecycle
├── SessionService.java                     # Session storage & management
├── HealthController.java                   # Health check endpoints
└── CustomerController.java                 # PDF download & customer info

src/main/resources/
├── application.properties                  # Configuration
└── static/index.html                       # Web UI

```

## Getting Started

### Prerequisites

- Java 11 or higher
- Maven 3.6+
- Active Laserfiche API endpoint with credentials
- Modern web browser (Chrome, Firefox, Safari, Edge)

### Configuration

Create or update `application.properties`:

```properties
# Server configuration
server.port=8082
server.servlet.context-path=/customerdatasearch

# Laserfiche API configuration (REQUIRED)
laserfiche.api.url=https://your-laserfiche-api.com/api/endpoint
laserfiche.api.username=your_api_username
laserfiche.api.password=your_api_password

# HTTP Client configuration (optional)
laserfiche.api.timeout.connect=10000        # 10 seconds
laserfiche.api.timeout.read=60000           # 60 seconds
http.client.max.connections=50
http.client.max.connections.per.route=10
```

### Running the Application

**Development (Standalone JAR):**
```bash
mvn clean package
java -jar target/customerdatasearch-0.0.1-SNAPSHOT.jar
```

**Or with Maven:**
```bash
mvn spring-boot:run
```

The application will be available at `http://localhost:8082/customerdatasearch`

### Deployment (WAR file)

The application extends `SpringBootServletInitializer` for servlet container deployment:

```bash
mvn clean package -DskipTests
# Deploy the generated WAR file to your servlet container (Tomcat, etc.)
```

## API Endpoints

### Session Management

**Start Session (Initiate Encryption)**
```
POST /customerdatasearch/session/start
Content-Type: application/json

{
  "encryptedKey": "base64_encrypted_aes_key"
}

Response:
{
  "sessionId": "uuid-string"
}
```

**Close Session**
```
DELETE /customerdatasearch/session/{sessionId}
```

**Get Active Session Count**
```
GET /customerdatasearch/session/count

Response:
{
  "activeSessions": 5
}
```

### Customer Operations

**Download/View PDF**
```
GET /customerdatasearch/api/customers/download/{encryptedId}?sessionId={sessionId}&download=true|false

Parameters:
- encryptedId: AES-encrypted customer ID (URL-safe base64)
- sessionId: Session ID from /session/start
- download: true (attachment) | false (inline viewing)

Returns: PDF file (application/pdf)
```

**Get Customer Info**
```
GET /customerdatasearch/api/customers/{encryptedId}/info?sessionId={sessionId}

Response:
{
  "customerId": 123,
  "caseId": "123",
  "pdfExists": true,
  "pdfSize": 1024000,
  "source": "laserfiche-api",
  "apiEndpoint": "https://..."
}
```

**Test Laserfiche API Connection**
```
POST /customerdatasearch/api/customers/{encryptedId}/test-api?sessionId={sessionId}

Response:
{
  "customerId": 123,
  "caseId": "123",
  "success": true,
  "pdfSize": 1024000,
  "message": "PDF fetched successfully",
  "apiUrl": "https://..."
}
```

### Health Checks

**Basic Health**
```
GET /customerdatasearch/health

Response:
{
  "status": "UP",
  "timestamp": "2024-10-14T10:30:00",
  "application": "NBK Customer Data Search",
  "version": "2.0.0",
  "integration": "Laserfiche LOS API"
}
```

**Detailed Health**
```
GET /customerdatasearch/health/detailed

Response: Includes memory usage, active sessions, Java version, Laserfiche connectivity
```

**Laserfiche Status**
```
GET /customerdatasearch/health/laserfiche-status

Response:
{
  "timestamp": "2024-10-14T10:30:00",
  "apiUrl": "https://...",
  "connectivity": "Connected (HTTP 200)",
  "status": "UP"
}
```

## Security Architecture

### Encryption Flow

1. **Client** requests public RSA key from server
2. **Client** generates a random AES-256 key
3. **Client** encrypts AES key with RSA-2048 public key using OAEP (SHA-1)
4. **Client** sends encrypted AES key to `/session/start`
5. **Server** decrypts AES key with RSA-2048 private key
6. **Server** stores AES key in session storage (in-memory with UUID key)
7. **Client** encrypts customer ID with AES-256-GCM (12-byte IV + ciphertext)
8. **Server** decrypts customer ID, validates session, fetches PDF from Laserfiche
9. **Server** returns PDF with cache-control headers (no-cache, no-store)

### Key Details

- **RSA Keys**: Generated fresh on application startup (2048-bit)
- **AES Encryption**: 256-bit keys, GCM mode with 12-byte IVs
- **Session Storage**: In-memory Map<sessionId, SecretKey>
- **Customer ID Protection**: Encrypted during transit, never logged in plaintext
- **PDF Content**: Streamed directly from Laserfiche, not cached

### CORS Policy

All endpoints allow cross-origin requests:
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, HEAD
Access-Control-Allow-Headers: *
Max-Age: 3600
```

## Frontend Usage

The included `index.html` provides a user interface with four main operations:

1. **View PDF** - Opens PDF in a new browser tab
2. **Download PDF** - Downloads PDF as attachment
3. **Customer Info** - Retrieves and displays customer metadata
4. **Test API** - Tests Laserfiche connectivity with diagnostic info

All operations automatically:
- Fetch the public RSA key
- Generate a secure AES session key
- Establish an encrypted session
- Encrypt the customer ID
- Communicate securely with the backend

## Laserfiche Integration

### API Request Format

The application sends requests to your Laserfiche API in this format:

```json
{
  "CaseID": "12345",
  "RequestID": "",
  "DocumentType": "national id"
}
```

**Note:** The Customer ID from the UI becomes the `CaseID` in the Laserfiche API call.

### API Response Format

Expected response from Laserfiche:

```json
{
  "EntryID": "...",
  "ResponseCode": "200",
  "RespondMessage": "Success",
  "StreamBytes": "base64_encoded_pdf_content"
}
```

- Response codes `"200"` or `"0"` indicate success
- `StreamBytes` must contain BASE64-encoded PDF binary
- Application automatically decodes and serves the PDF

### Connection Details

- **Authentication**: HTTP Basic Auth (username + password)
- **Timeout**: Configurable (default: 10s connect, 60s read)
- **Connection Pooling**: Reuses connections for performance
- **Logging**: All API interactions logged at INFO level

## Logging

The application logs important events with SLF4J:

- Session creation/deletion
- Encryption/decryption operations (no sensitive data logged)
- Laserfiche API calls and responses
- PDF serving operations
- Health checks and connectivity tests

Configure logging level in `application.properties`:

```properties
logging.level.com.example.CUSTOMERDATASEARCH=DEBUG
logging.level.org.springframework.web=INFO
```

## Performance Optimization

- **Connection Pooling**: HTTP connections reused (up to 50 total, 10 per route)
- **Session Caching**: Encrypted keys stored in-memory for fast validation
- **PDF Streaming**: Large files streamed directly without buffering in memory
- **Request Timeouts**: Prevents hanging requests (configurable)

## Troubleshooting

**"Invalid session" error:**
- Session ID may have expired or been invalidated
- Initiate a new session via `/session/start`

**"PDF not found" error:**
- Customer ID does not exist in Laserfiche
- Verify the customer ID is correct
- Use the "Test API" button to diagnose

**"Cannot connect to server":**
- Backend service not running
- Verify URL is correct: `http://localhost:8082/customerdatasearch`
- Check firewall/network settings

**Laserfiche API connection fails:**
- Check credentials in `application.properties`
- Verify API endpoint URL
- Use `/health/laserfiche-status` endpoint for diagnostic info

## Development Notes

- **Stateless Sessions**: Each session is independent; sessions don't persist across restarts
- **Public Key Rotation**: New RSA keypair generated on each application restart
- **No Database**: Uses in-memory session storage (not suitable for distributed deployments)
- **Single Instance**: Designed for single-server deployment (session sharing requires distributed cache)

For production deployment with multiple instances, replace `SessionService` with a distributed cache (Redis, Memcached, etc.).


## Support

For issues or questions, contact your system administrator or development team.

---

**Version**: 2.0.0  
**Last Updated**: September 2025
