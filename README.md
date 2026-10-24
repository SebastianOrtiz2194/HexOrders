# Order Management System (OMS)

A robust, enterprise-grade prototype for an Order Management System built with **Java 21**, **Spring Boot 3.x** and meticulously structured using **Hexagonal Architecture (Ports and Adapters)** principles optimized for Cloud-Native environments.

---

## Architecture Design

The system enforces a strict separation of concerns to maximize maintainability and testability:
- **Core Domain (`com.oms.domain`)**: Contains the pure business rules (`Order`, `OrderItem`). It is framework-agnostic and relies entirely on pure Java.
- **Use Cases & Ports (`com.oms.application`)**: The orchestration layer. Defines the interfaces (Contracts) specifying input requirements (REST Use Cases) and output necessities (Database, Cache, Messaging).
- **Infrastructure Adapters (`com.oms.infrastructure`)**: Pluggable technical modules. Relational storage via Spring Data JPA, caching via Redis, and asynchronous event distribution via Apache Kafka.

---

## Setup & Local Execution

To bootstrap the application locally, ensure your environment meets the following prerequisites:
- **Java 21**
- **Docker** & **Docker Compose**
- **Maven** (optional if utilizing the internal wrapper)

### 1. Environment Variables (.env)
The project adheres to the configuration standards of *12-Factor Apps*. Copy the template file to set your local credentials:
```bash
cp .env.example .env
```

### 2. Bootstrapping Attached Resources (Docker Compose)
Launch the required local infrastructure, comprising PostgreSQL, Redis, Kafka, Zookeeper, and Zipkin:
```bash
docker-compose up -d
```
> Quickly verify your containers' health statuses with `docker-compose ps`.

### 3. Run the Spring Boot Application
```bash
./mvnw clean install
./mvnw spring-boot:run
```

---

## Security

The API is secured as an **OAuth2 Resource Server** utilizing **JWT Bearer tokens** (HMAC-SHA256). Every request to `/api/v1/**` must present a valid token via the `Authorization: Bearer` header. Authentication is fully stateless — no server-side sessions are held.

### 1. Obtain an Access Token
Exchange the seeded admin credentials at the public token endpoint:
```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
-H "Content-Type: application/json" \
-d '{"username": "admin", "password": "admin123"}'
```
**Expected Response (200 OK):**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### 2. Authorize Requests
Attach the token to every subsequent call:
```bash
TOKEN=<paste access_token here>
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/orders
```

> **Configuration (12-Factor):** The seeded admin user, signing secret, issuer, and token TTL are fully externalized. Override them via `OMS_SECURITY_USER`, `OMS_SECURITY_PASS`, `OMS_JWT_SECRET` (min. 32 bytes), `OMS_JWT_ISSUER`, and `OMS_JWT_EXPIRATION_MINUTES`. The committed defaults exist for local development only; in production, delegate token issuance to a dedicated IdP (e.g., Keycloak, Auth0) and point the resource server's decoder at its JWK endpoint.

---

## Core API Endpoints

### 1. Create a New Order
**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/orders \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
  "customerName": "John Doe",
  "items": [
    {
      "productId": "PROD-001",
      "productName": "Laptop Mac",
      "quantity": 1,
      "unitPrice": 1200.50
    }
  ]
}'
```
**System Effects:** 
- Transacts and persists data immutably in PostgreSQL.
- Broadcasts the `OrderCreatedEvent` payload into the `order-events` Kafka topic.
- The downstream Kafka consumer intercepts the event to perform an automatic **Cache Warm-Up**, instantly storing the order entity within Redis.

**Expected Response (201 Created):**
```json
{
  "id": "313f5c26-e624-4959-af4e-538219477baf",
  "customerName": "John Doe",
  "items": [...],
  "status": "PENDING",
  "totalAmount": 1200.50,
  "createdAt": "2026-04-10T11:48:26.261",
  "updatedAt": "2026-04-10T11:48:26.261"
}
```

### 2. Fetch Order Details (by UUID)
Retrieves a specific order. Implements the _Cache-Aside_ pattern. It attempts to resolve from **Redis** first (which should be pre-warmed), falling back to PostgreSQL seamlessly if a cache-miss or disconnection occurs.
**Request:**
```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/orders/{UUID}
```

### 3. Update Order Status
Mutates an order's lifecycle utilizing the domain's strict logical state machine.
**Request:**
```bash
curl -X PATCH http://localhost:8080/api/v1/orders/{UUID}/status \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"status": "CONFIRMED"}'
```

---

## Observability, Telemetry & Tracing

The system is instrumented for advanced, production-ready observability using **Micrometer**, **Prometheus**, and **OpenTelemetry**.

- **Zipkin UI:** Visually inspect distributed traces and latencies across HTTP and Kafka context boundaries at `http://localhost:9411`
- **Structured Logs:** Logs automatically inject correlation contexts, outputting `traceId` and `spanId` globally (e.g., `[oms, 5f1... , a2b...]`).
- **Healthcheck & Status:** Check dependency statuses (DB, Redis, Kafka) at `http://localhost:8080/actuator/health`
- **Prometheus Metrics:** Designed for scraper aggregation, expose system insights at `http://localhost:8080/actuator/prometheus`
- **Kafka UI:** For deep exploration of topics and consumer groups, visit `http://localhost:8090`

---

## Validation & Testing Tools
### Postman Collection
A thoroughly documented Postman collection is anchored within the repository to streamline E2E testing workflows.
- **File Locator:** `oms_postman_collection.json`
- **Usage:** Simply import the file directly into your Postman client.
- **Smart Variables:** The collection leverages environment variables `{{baseUrl}}` and dynamically captures the generated UUIDs via `{{order_id}}` globally upon order creation.
- **Inherited Authentication:** Run the included **Get Access Token** request first — the issued JWT is captured automatically into `{{access_token}}` and injected as a `Bearer` token across all REST methods.

---
