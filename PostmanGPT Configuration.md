# 📋 **PostmanGPT Configuration**

## **🚀 1. Server Configuration**
```yaml
server:
  port: ${SERVER_PORT:8080}
```
- **What it does:** Sets the port where your Spring Boot app runs
- **Default:** Port 8080 (like `localhost:8080`)
- **Environment override:** Use `SERVER_PORT=9000` to change it

---

## **🍃 2. MongoDB Database Setup**
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/postmangpt
      database: postmangpt
```
- **What it does:** Connects to MongoDB database
- **Purpose:** Stores API collections, requests, test results, and user data
- **Why MongoDB:** MongoDB provides flexible JSON document storage ideal for API data structures

---

## **🤖 3. AI Integration (Spring AI)**
```yaml
spring.ai:
  vectorstore:
    mongodb:
      collection-name: vector_embeddings
  openai:
    api-key: your-openai-api-key
```
- **What it does:** Enables AI-powered features within the application
- **Vector Store:** Stores AI embeddings in MongoDB for semantic search capabilities
- **OpenAI:** Powers intelligent API suggestions, code generation, and natural language processing

---

## **⚡ 4. Redis Caching**
```yaml
cache:
  type: redis
redis:
  host: localhost
  port: 6379
```
- **What it does:** Implements high-performance caching layer
- **Purpose:** Caches API responses, user sessions, and frequently accessed data
- **Benefit:** Faster response times, less database load

---

## **🔒 5. Security & Authentication**
```yaml
app:
  jwt:
    secret: ${JWT_SECRET:your-jwt-secret-here}  # ⚠️ Use environment variable!
    expiration: ${JWT_EXPIRATION:86400000}  # 24 hours
  cors:
    allowed-origins: http://localhost:3000
```
- **JWT:** Implements secure token-based authentication system
- **CORS:** Enables cross-origin requests from the frontend application
- **Purpose:** Protects API endpoints and manages user sessions

---

## **🌐 6. WebFlux (Reactive Programming)**
```yaml
webflux:
  base-path: /api
codec:
  max-in-memory-size: 10MB
```
- **What it does:** Handles high-concurrency requests efficiently using non-blocking I/O
- **Base path:** All APIs start with `/api` (like `/api/collections`)
- **Memory limit:** Limits request size to 10MB to prevent memory overflow

---

## **📊 7. Monitoring & Health Checks**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```
- **What it does:** Provides comprehensive health and performance monitoring
- **Endpoints:** 
  - `/actuator/health` - Application health status
  - `/actuator/info` - Application information
  - `/actuator/metrics` - Performance metrics
  - `/actuator/prometheus` - Prometheus-compatible metrics

---

## **📝 8. Logging Configuration**
```yaml
logging:
  level:
    es.alesqui.postmangpt: DEBUG
  file:
    name: logs/postmangpt.log
```
- **What it does:** Controls what gets logged and where
- **DEBUG level:** Shows detailed information for troubleshooting
- **Log file:** Saves logs to `logs/postmangpt.log`

---

## **⚙️ 9. PostmanGPT-Specific Settings**
```yaml
postmangpt:
  api:
    max-requests-per-minute: 100
    max-collection-size: 1000
  ai:
    enabled: true
    max-suggestions: 5
```
- **Rate limiting:** Restricts API usage to 100 requests per minute per user
- **Collection limits:** Maximum of 1000 requests allowed per collection
- **AI features:** Toggle to enable/disable AI-powered functionality
- **Suggestions:** Maximum of 5 AI-generated suggestions displayed to users

---
