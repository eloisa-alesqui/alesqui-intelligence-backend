# Alesqui Intelligence — Backend

> **Transform your API docs into intelligent conversations.**
> Upload a Swagger/OpenAPI spec and get an AI agent that understands and invokes your APIs in natural language — deployable entirely on-premise.

This is the backend service of the Alesqui Intelligence platform. It is a reactive REST API built with Spring Boot and WebFlux, responsible for authentication, API spec ingestion, AI orchestration (ReAct pattern via Spring AI), and SSE streaming.

**Related repositories:**
- [alesqui-intelligence-frontend](https://github.com/eloisa-alesqui/alesqui-intelligence-frontend) — React 19 SPA
- [alesqui-intelligence-distribution](https://github.com/eloisa-alesqui/alesqui-intelligence-distribution) — Docker Compose deployment package
- [alesqui-intelligence-landing](https://github.com/eloisa-alesqui/alesqui-intelligence-landing) — Next.js landing page

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [API Reference](#api-reference)
- [Deployment](#deployment)
- [Running Tests](#running-tests)
- [Architecture](#architecture)

---

## Overview

Alesqui Intelligence allows organizations to expose their APIs as conversational AI agents. Users interact through a chat interface; the backend orchestrates an AI agent (ReAct pattern) that dynamically discovers and invokes the relevant API endpoints based on each query.

**Key capabilities:**

- **API ingestion** — Parse and store Swagger/OpenAPI (JSON/YAML) and Postman Collection v2.1 definitions
- **AI orchestration** — ReAct loop with OpenAI GPT-4o-mini and Spring AI tool calling
- **Streaming responses** — Server-Sent Events (SSE) with step-by-step reasoning events
- **Data tools** — Count, filter, group, aggregate, and export results to Excel (.xlsx)
- **Chart generation** — Bar, line, and pie chart data from query results
- **Enterprise auth** — JWT with refresh tokens, BCrypt, RBAC roles
- **Two deployment modes** — `CORPORATE` (self-hosted, admin-managed users) and `TRIAL` (public self-registration, 14-day limit)
- **Audit logging** — Full audit trail with configurable retention

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.0 + WebFlux (reactive/non-blocking) |
| Database | MongoDB 7.0 (Reactive Streams driver) |
| AI | Spring AI 1.0.3 + OpenAI GPT-4o-mini |
| Auth | JJWT 0.13, BCrypt, Spring Security |
| API Parsing | Swagger Parser 3, Apache POI 5 |
| Containerization | Docker (multi-stage build) |

---

## Prerequisites

- Java 21+
- Maven 3.9+
- MongoDB 7.0 (local or Atlas)
- OpenAI API key

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/eloisa-alesqui/alesqui-intelligence-backend.git
cd alesqui-intelligence-backend
```

### 2. Configure environment variables

Copy the example file and fill in your values:

```bash
cp .env.example .env
```

See [Environment Variables](#environment-variables) for details.

### 3. Run with Maven

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

On first startup in `CORPORATE` mode, the service automatically creates an initial admin user using `INITIAL_ADMIN_EMAIL` and `INITIAL_ADMIN_PASSWORD`. If no password is provided, one is auto-generated and printed to the logs.

---

## Environment Variables

### Required

| Variable | Description |
|----------|-------------|
| `MONGODB_URI` | MongoDB connection string (e.g. `mongodb://localhost:27017`) |
| `JWT_SECRET` | JWT signing secret — minimum 32 characters |
| `OPENAI_API_KEY` | OpenAI API key |

### Application

| Variable | Default | Description |
|----------|---------|-------------|
| `DEPLOYMENT_MODE` | `CORPORATE` | `CORPORATE` or `TRIAL` |
| `COMPANY_NAME` | `Alesqui Intelligence` | Company name shown in emails |
| `FRONTEND_URL` | `http://localhost:3000` | Frontend URL for CORS and email links |
| `CORS_ADDITIONAL_ORIGINS` | — | Comma-separated extra allowed origins |
| `PORT` | `8080` | Server port |

### Database (alternative to URI)

| Variable | Default | Description |
|----------|---------|-------------|
| `MONGODB_DATABASE` | `alesqui_intelligence` | Database name |
| `MONGODB_USER` | — | Username (if not in URI) |
| `MONGODB_PASSWORD` | — | Password (if not in URI) |
| `MONGODB_AUTH_DB` | `admin` | Auth database |

### JWT

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_EXPIRATION` | `900000` | Access token TTL in ms (15 min) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh token TTL in ms (7 days) |

### Email (SMTP)

| Variable | Default | Description |
|----------|---------|-------------|
| `SMTP_HOST` | — | SMTP server hostname |
| `SMTP_PORT` | — | SMTP port |
| `SMTP_USER` | — | SMTP username |
| `SMTP_PASSWORD` | — | SMTP password |
| `MAIL_FROM_EMAIL` | `noreply@alesqui.com` | Sender address |
| `MAIL_FROM_NAME` | `Alesqui Intelligence` | Sender display name |

### Initial Admin (CORPORATE mode)

| Variable | Default | Description |
|----------|---------|-------------|
| `INITIAL_ADMIN_EMAIL` | — | Email for the auto-created admin user |
| `INITIAL_ADMIN_PASSWORD` | — | Password (auto-generated if empty) |
| `APP_URL` | `http://localhost` | Base URL shown in startup logs |

### Chat & AI

| Variable | Default | Description |
|----------|---------|-------------|
| `AI_PROVIDER` | `openai` | AI provider: `openai` or `ollama` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL (when `AI_PROVIDER=ollama`) |
| `OLLAMA_MODEL` | `gemma4` | Ollama model tag (when `AI_PROVIDER=ollama`) |
| `AI_REACT_MAX_ITERATIONS` | `15` | Max ReAct tool-calling iterations before forced exit |
| `chat.processing-timeout` | `120` | AI processing timeout in seconds |
| `chat.max-conversation-history` | `20` | Max messages retained per conversation |

### Trial Mode

| Variable | Default | Description |
|----------|---------|-------------|
| `TRIAL_DURATION_DAYS` | `14` | Trial validity in days |
| `TRIAL_RATE_LIMIT_MAX_ATTEMPTS` | `1` | Max trial registrations per IP per window |
| `TRIAL_RATE_LIMIT_WINDOW_HOURS` | `24` | Rate limit window in hours |

### Miscellaneous

| Variable | Default | Description |
|----------|---------|-------------|
| `AUDIT_RETENTION_DAYS` | `730` | Audit log TTL in days |
| `PROXY_HOST` | — | HTTP proxy hostname (leave empty to disable) |
| `PROXY_PORT` | `0` | HTTP proxy port |

---

## API Reference

### Public (no authentication required)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/auth/login` | Obtain access + refresh tokens |
| `POST` | `/api/auth/refresh` | Refresh access token |
| `POST` | `/api/auth/logout` | Revoke refresh token |
| `POST` | `/api/auth/forgot-password` | Request password reset email |
| `POST` | `/api/auth/reset-password` | Reset password with token |
| `GET/POST` | `/api/auth/activate` | Activate account with email token |
| `POST` | `/api/public/trial-registration` | Register a trial account (TRIAL mode) |
| `GET` | `/api/public/deployment-info` | Returns deployment mode and company name |
| `GET` | `/actuator/health` | Health check |

### Chat

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/chat/stream` | Start AI chat session (SSE stream) |

SSE events: `STATUS` (reasoning step), `FINAL_RESPONSE` (complete answer), `ERROR`.

### API Specifications

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/swagger` | Upload a Swagger/OpenAPI spec |
| `GET` | `/api/swagger` | List imported Swagger specs |
| `DELETE` | `/api/swagger/{id}` | Delete a spec |
| `POST` | `/api/postman` | Upload a Postman Collection |
| `GET` | `/api/postman` | List imported Postman collections |
| `DELETE` | `/api/postman/{id}` | Delete a collection |
| `POST` | `/api/unification` | Unify specs into a single knowledge base |
| `GET` | `/api/unification/{id}` | Get unified API |
| `PUT` | `/api/unification/{id}/config` | Update API configuration (base URL, auth) |

### Conversations

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/conversations` | List all conversations for current user |
| `GET` | `/api/conversations/{id}` | Get conversation with messages |
| `DELETE` | `/api/conversations/{id}` | Delete a conversation |

### Administration (`ROLE_SUPERADMIN`)

| Method | Path | Description |
|--------|------|-------------|
| `GET/POST` | `/api/admin/users` | List / create users |
| `PUT/DELETE` | `/api/admin/users/{id}` | Update / delete user |
| `GET/POST` | `/api/admin/groups` | List / create groups |
| `PUT/DELETE` | `/api/admin/groups/{id}` | Update / delete group |
| `POST` | `/api/admin/apis/{id}/groups` | Assign groups to an API |
| `GET` | `/api/audit/logs` | Query audit logs |

### Diagnostics (`ROLE_IT`, `ROLE_SUPERADMIN`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/diagnostics` | List configured APIs with health status |
| `GET` | `/api/diagnostics/{id}` | Detailed health check for one API |

### Files

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/files/{filename}` | Download an exported file (Excel) |

---

## Deployment

### Docker Hub

Pre-built images are published automatically on every tag:

```bash
docker pull alesquiintelligence/backend:latest
docker pull alesquiintelligence/backend:1.0.0
```

### Self-Hosted (recommended)

Use the [distribution package](https://github.com/eloisa-alesqui/alesqui-intelligence-distribution) for a full Docker Compose deployment including the frontend and MongoDB:

```bash
curl -fsSL https://github.com/eloisa-alesqui/alesqui-intelligence-distribution/releases/latest/download/install.sh | bash
```

### Render.com

A `render.yaml` is included for one-click deployment on Render:

1. Fork this repository
2. Connect it to Render
3. Set the required environment variables in the Render dashboard (`MONGODB_URI`, `OPENAI_API_KEY`, `JWT_SECRET`)

---

## Running Tests

```bash
./mvnw test
```

Tests use embedded MongoDB and WireMock — no external services required.

```bash
# Run a specific test class
./mvnw test -Dtest=AuthenticationControllerTest

# Skip tests (build only)
./mvnw package -DskipTests
```

---

## Architecture

### AI Orchestration (ReAct pattern)

```
POST /api/chat/stream
  └─ ChatOrchestrationService
       └─ AIChatService (Spring AI + GPT-4o-mini)
            └─ Tool calling loop:
                 ├─ ApiDiscoveryTools    → find relevant endpoints
                 ├─ ApiInvocationTools   → execute real HTTP calls
                 ├─ DataTools            → filter, aggregate, compute
                 ├─ ChartTools           → generate chart data
                 └─ ExportTools          → export to Excel

SSE stream → STATUS events (reasoning) + FINAL_RESPONSE
```

**Supported providers:** The AI layer supports two providers, selectable via `AI_PROVIDER`:

- **`openai`** (default) — Uses the OpenAI API (`gpt-4o-mini`). Requires `OPENAI_API_KEY`.
- **`ollama`** — Uses a self-hosted Ollama instance for on-premise inference. Requires `OLLAMA_BASE_URL` and a pulled model (e.g. `ollama pull gemma4`).

Compare provider latency via Actuator metrics: `ai.chat.with.tools.duration` and `ai.extraction.duration` at `/actuator/metrics`.

### User Roles

| Role | Capabilities |
|------|-------------|
| `ROLE_SUPERADMIN` | Admin panel, user/group management, API setup, chat |
| `ROLE_IT` | API setup, diagnostics, chat |
| `ROLE_TRIAL` | API setup, chat (14-day limit) |
| `ROLE_BUSINESS` | Chat only |

### Deployment Modes

| Mode | Behavior |
|------|---------|
| `CORPORATE` | Admin creates and manages all users. No self-registration. |
| `TRIAL` | Public self-registration via `/api/public/trial-registration`. Accounts expire after `TRIAL_DURATION_DAYS`. |

---

## License

[AGPL-3.0](LICENSE)
