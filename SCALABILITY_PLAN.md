# Scalability Improvement Plan

## Current Architecture Summary

| Component | Technology | Status |
|-----------|-----------|--------|
| Framework | Spring Boot 3.5.0 + WebFlux | Reactive, non-blocking |
| Database | MongoDB 7.0 (Reactive driver) | Single instance, no replicas |
| AI Model | OpenAI GPT-4o-mini via Spring AI 1.0.3 | No response caching |
| Auth | JWT (stateless, HS256) | Ready for horizontal scaling |
| Streaming | Server-Sent Events (SSE) | Per-instance connections |
| Rate Limiting | In-memory ConcurrentHashMap | Single instance only |
| Caching | None | Not implemented |
| Deployment | Render (free tier, Docker) | Single instance |

---

## Phase 1: Quick Wins (Week 1-2)

### 1.1 MongoDB Connection Pool Tuning

**Problem:** Default MongoDB connection pool settings may be insufficient under load.

**Solution:** Configure connection pool parameters in the MongoDB URI.

```properties
# application.properties
spring.data.mongodb.uri=${MONGODB_URI}?maxPoolSize=50&minPoolSize=5&maxIdleTimeMS=60000&waitQueueTimeoutMS=5000
```

**Impact:** Better connection reuse, reduced latency under concurrent load.

---

### 1.2 HTTP Client Connection Pooling

**Problem:** `ApiExecutionService` creates HTTP connections without explicit pool configuration.

**Solution:** Configure a shared `HttpClient` with connection pooling for external API calls.

```java
@Bean
public HttpClient httpClient() {
    ConnectionProvider provider = ConnectionProvider.builder("api-pool")
        .maxConnections(100)
        .maxIdleTime(Duration.ofSeconds(30))
        .maxLifeTime(Duration.ofMinutes(5))
        .pendingAcquireTimeout(Duration.ofSeconds(10))
        .build();
    return HttpClient.create(provider);
}
```

**Impact:** Reduced connection overhead for external API invocations.

---

### 1.3 Chat Thread Pool Monitoring & Tuning

**Problem:** Fixed `chatScheduler` with 10 threads and 1000 queue may bottleneck.

**Solution:**
- Expose thread pool metrics via Micrometer/Actuator
- Make thread pool size configurable via properties
- Start with `2 * CPU cores` threads for I/O-bound work

```properties
# application.properties
chat.scheduler.thread-count=${CHAT_THREAD_COUNT:20}
chat.scheduler.queue-capacity=${CHAT_QUEUE_CAPACITY:2000}
```

**Impact:** Adaptive capacity for concurrent chat sessions.

---

## Phase 2: Distributed Caching with Redis (Week 3-4)

### 2.1 Add Redis Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

### 2.2 Cache User Objects

**Problem:** Every authenticated request triggers a MongoDB lookup for JWT validation.

**Solution:** Cache `User` objects in Redis with a short TTL.

```java
@Cacheable(value = "users", key = "#username")
public Mono<User> findByUsername(String username) {
    return userRepository.findByUsername(username);
}
```

**Configuration:**
- TTL: 15 minutes (matches access token lifetime)
- Eviction: On user update/delete

**Impact:** ~80% reduction in user-related DB queries.

---

### 2.3 Cache API Metadata

**Problem:** API specifications are read from MongoDB on every chat query.

**Solution:** Cache `UnifiedApiDocument` metadata in Redis.

- TTL: 1 hour (API specs change infrequently)
- Eviction: On API update/delete via `@CacheEvict`

**Impact:** Faster chat initialization, reduced MongoDB read load.

---

### 2.4 Distributed Rate Limiting

**Problem:** In-memory rate limiting (`ConcurrentHashMap`) doesn't work across multiple instances.

**Solution:** Replace with Redis-based rate limiting using sliding window counters.

```java
@Service
public class DistributedRateLimitingService {

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Boolean> isAllowed(String key, int maxAttempts, Duration window) {
        String redisKey = "rate_limit:" + key;
        return redisTemplate.opsForValue()
            .increment(redisKey)
            .flatMap(count -> {
                if (count == 1) {
                    return redisTemplate.expire(redisKey, window)
                        .thenReturn(true);
                }
                return Mono.just(count <= maxAttempts);
            });
    }
}
```

**Impact:** Consistent rate limiting across all application instances.

---

## Phase 3: AI & API Response Optimization (Week 5-6)

### 3.1 LLM Response Caching

**Problem:** Identical or similar queries to OpenAI are not cached, wasting tokens and increasing latency.

**Solution:** Implement semantic caching for AI responses.

- Hash the system prompt + user query + tool context
- Cache responses with a configurable TTL (e.g., 30 minutes)
- Use Redis for cache storage

**Impact:** Reduced OpenAI API costs, faster responses for repeated queries.

---

### 3.2 External API Response Caching

**Problem:** Tool calls to external APIs are never cached, even for idempotent GET requests.

**Solution:** Implement cache-aside pattern in `ApiExecutionService`.

```java
public Mono<String> executeApiCall(ApiCallRequest request) {
    if ("GET".equals(request.getMethod())) {
        String cacheKey = "api_response:" + hash(request);
        return cacheGet(cacheKey)
            .switchIfEmpty(
                executeActual(request)
                    .flatMap(response -> cachePut(cacheKey, response, Duration.ofMinutes(5)))
            );
    }
    return executeActual(request);
}
```

**Impact:** Fewer external API calls, faster tool execution, reduced rate limit risk.

---

### 3.3 Parallel Tool Execution

**Problem:** Tool calls within the ReAct loop may execute sequentially.

**Solution:** Where tool calls are independent, execute them in parallel using `Mono.zip()` or `Flux.merge()`.

**Impact:** Reduced chat response latency for multi-tool queries.

---

## Phase 4: Horizontal Scaling Readiness (Week 7-8)

### 4.1 Stateless Session Verification

**Current status:** Already stateless (JWT-based, no server sessions).

**Checklist:**
- [x] JWT authentication (no server-side sessions)
- [x] No sticky sessions required
- [ ] Rate limiting moved to Redis (Phase 2.4)
- [ ] Cache moved to Redis (Phase 2.2, 2.3)
- [ ] ChatMemory externalized or scoped per request

---

### 4.2 Externalize ChatMemory

**Problem:** Spring AI's `ChatMemory` stores conversation context in-memory. In multi-instance setups, a user's subsequent requests may hit different instances.

**Solution:** Implement a MongoDB-backed or Redis-backed `ChatMemory` implementation.

```java
public class RedisChatMemory implements ChatMemory {
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public void add(String conversationId, List<Message> messages) {
        // Store in Redis with TTL matching session duration
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        // Retrieve from Redis
    }
}
```

**Impact:** Chat sessions work correctly across multiple instances.

---

### 4.3 Load Balancer Configuration

**Solution:** Deploy behind a load balancer with:
- Health check: `GET /actuator/health`
- Algorithm: Round-robin (stateless app)
- SSE support: Ensure long-lived connections are supported
- Timeout: Match chat processing timeout (120s)

---

## Phase 5: Observability & Monitoring (Ongoing)

### 5.1 Prometheus Metrics

**Solution:** Enable and expose application metrics.

```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
management.metrics.tags.application=alesqui-intelligence
management.metrics.export.prometheus.enabled=true
```

**Key metrics to track:**
- `chat.scheduler.active` - Active chat processing threads
- `chat.scheduler.queued` - Queued chat requests
- `mongodb.connections.active` - Active DB connections
- `http.client.requests` - External API call latency
- `ai.tokens.used` - OpenAI token consumption
- `rate.limit.rejections` - Rate-limited requests

---

### 5.2 Custom AI Metrics

```java
@Service
public class AiMetricsService {
    private final MeterRegistry meterRegistry;

    public void recordTokenUsage(String model, int promptTokens, int completionTokens) {
        meterRegistry.counter("ai.tokens.prompt", "model", model).increment(promptTokens);
        meterRegistry.counter("ai.tokens.completion", "model", model).increment(completionTokens);
    }

    public void recordChatLatency(Duration duration) {
        meterRegistry.timer("chat.processing.duration").record(duration);
    }
}
```

---

### 5.3 Distributed Tracing (Optional)

Add Micrometer Tracing with Zipkin or Jaeger for end-to-end request tracing across services.

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

---

## Phase 6: Advanced Scaling (Future)

### 6.1 Message Queue for Chat Processing

Extract chat processing to a message queue (RabbitMQ or Kafka) to decouple request ingestion from AI processing.

**Benefits:**
- Back-pressure handling during traffic spikes
- Retry failed AI calls without client timeout
- Scale AI processing workers independently

---

### 6.2 MongoDB Replica Set

Configure MongoDB with read replicas for read-heavy operations.

- Primary: Writes (user creation, conversation saves, audit logs)
- Secondary: Reads (conversation history, API metadata, audit queries)

---

### 6.3 Multi-Region Deployment

- Deploy application to multiple regions
- Use MongoDB Atlas global clusters for geo-distributed data
- Implement geo-routing via DNS or CDN

---

## Priority Matrix

| Improvement | Effort | Impact | Priority |
|-------------|--------|--------|----------|
| MongoDB connection pool tuning | Low | Medium | P0 |
| HTTP client connection pooling | Low | Medium | P0 |
| Chat thread pool configuration | Low | Medium | P0 |
| Redis caching (users, APIs) | Medium | High | P1 |
| Distributed rate limiting | Medium | High | P1 |
| External API response caching | Medium | Medium | P1 |
| LLM response caching | Medium | High | P2 |
| Externalize ChatMemory | Medium | High | P2 |
| Prometheus metrics | Low | Medium | P2 |
| Parallel tool execution | Medium | Medium | P2 |
| Message queue integration | High | High | P3 |
| MongoDB replica set | Medium | Medium | P3 |
| Multi-region deployment | High | High | P3 |

---

## Estimated Resource Requirements

| Phase | Timeline | Additional Infrastructure |
|-------|----------|--------------------------|
| Phase 1 | Week 1-2 | None (configuration only) |
| Phase 2 | Week 3-4 | Redis instance (managed) |
| Phase 3 | Week 5-6 | None (uses Redis from Phase 2) |
| Phase 4 | Week 7-8 | Load balancer, 2+ app instances |
| Phase 5 | Ongoing | Prometheus + Grafana (or managed APM) |
| Phase 6 | Future | Message queue, MongoDB replicas, multi-region infra |
