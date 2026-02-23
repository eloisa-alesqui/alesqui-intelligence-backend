# Alesqui Intelligence — Backend

Enterprise self-hosted AI API assistant. Upload your API specifications (Postman collections or Swagger/OpenAPI docs) and interact with your APIs through a natural-language chat interface. The AI understands your API structure, selects the right endpoints, and executes HTTP calls on your behalf.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5.0 (reactive, WebFlux) |
| AI | Spring AI 1.0.3 + OpenAI GPT-4o-mini |
| Database | MongoDB (reactive streams) |
| Auth | Spring Security + JWT |
| API Parsing | Swagger Parser v3, Postman Collections |
| File Processing | Apache POI (Excel/Word export) |
| Email | Brevo SMTP |
| Monitoring | Spring Actuator + Prometheus |
| Runtime | Java 21, Maven 3.9.11 |

## Features

- **Natural-language API chat** — AI selects and invokes API endpoints based on user intent
- **API spec ingestion** — Import Postman Collections and Swagger/OpenAPI 3.x specs
- **Conversation history** — Persistent conversations with automatic cleanup of inactive sessions
- **Role-based access control** — Admin and user roles with group-based API access
- **Two deployment modes** — `CORPORATE` (admin-provisioned users) and `TRIAL` (self-registration)
- **Audit logging** — Comprehensive audit trail with configurable retention (default 2 years)
- **File export** — Export chat results to Excel/Word documents
- **Proxy support** — Optional HTTP proxy for outbound API calls
- **OAuth2 token management** — Handles OAuth2 flows for secured APIs

## Prerequisites

- Java 21+
- Maven 3.9+
- MongoDB instance
- OpenAI API key

## Quick Start

### 1. Clone and configure

```bash
git clone <repo-url>
cd alesqui-intelligence-backend
```

Create a `.env` file (or export environment variables) with the required secrets:

```env
# Required
MONGODB_URI=mongodb://localhost:27017/alesqui
OPENAI_API_KEY=sk-...
JWT_SECRET=your-secret-key-min-32-chars

# Email (required for user notifications)
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USER=your-brevo-user
SMTP_PASSWORD=your-brevo-key
MAIL_FROM_EMAIL=noreply@yourdomain.com

# Optional
DEPLOYMENT_MODE=CORPORATE          # CORPORATE (default) or TRIAL
COMPANY_NAME=Alesqui Intelligence
INITIAL_ADMIN_EMAIL=admin@company.com
INITIAL_ADMIN_PASSWORD=            # Leave empty to auto-generate
FRONTEND_URL=http://localhost:3000
```

### 2. Run locally

```bash
./mvnw spring-boot:run
```

The server starts on `http://localhost:8080`. On first boot in `CORPORATE` mode with an empty database, an admin account is created automatically — check the logs for the generated password.

### 3. Run with Docker

```bash
docker build -t alesqui-intelligence-backend .
docker run -p 8080:8080 --env-file .env alesqui-intelligence-backend
```

## Deployment Modes

| Mode | Description |
|---|---|
| `CORPORATE` | Admin creates and manages all users. Suitable for internal enterprise deployments. |
| `TRIAL` | Self-registration enabled. Users get a 14-day trial. Rate-limited to 1 registration per IP per 24 hours. |

Set via the `DEPLOYMENT_MODE` environment variable.

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `MONGODB_URI` | Yes | `mongodb://localhost:27017` | MongoDB connection string |
| `OPENAI_API_KEY` | Yes | — | OpenAI API key |
| `JWT_SECRET` | Yes | — | JWT signing secret (min 32 chars) |
| `SMTP_HOST` | Yes | — | SMTP server host |
| `SMTP_PORT` | Yes | — | SMTP server port |
| `SMTP_USER` | Yes | — | SMTP username |
| `SMTP_PASSWORD` | Yes | — | SMTP password |
| `MAIL_FROM_EMAIL` | No | `noreply@alesqui.com` | Sender email address |
| `DEPLOYMENT_MODE` | No | `CORPORATE` | `CORPORATE` or `TRIAL` |
| `COMPANY_NAME` | No | `Alesqui Intelligence` | Company name used in emails/UI |
| `INITIAL_ADMIN_EMAIL` | No | `admin@company.com` | Bootstrap admin email |
| `INITIAL_ADMIN_PASSWORD` | No | *(auto-generated)* | Bootstrap admin password |
| `FRONTEND_URL` | No | `https://intelligence.alesqui.com` | Frontend URL for CORS and email links |
| `CORS_ADDITIONAL_ORIGINS` | No | — | Extra CORS origins (comma-separated) |
| `PORT` | No | `8080` | HTTP server port |
| `JWT_EXPIRATION` | No | `900000` | Access token TTL in ms (15 min) |
| `JWT_REFRESH_EXPIRATION` | No | `604800000` | Refresh token TTL in ms (7 days) |
| `AUDIT_RETENTION_DAYS` | No | `730` | Audit log retention in days |
| `TRIAL_DURATION_DAYS` | No | `14` | Trial user access duration |
| `PROXY_HOST` | No | — | Outbound HTTP proxy host |
| `PROXY_PORT` | No | `0` | Outbound HTTP proxy port |

## API Endpoints

| Group | Base Path | Description |
|---|---|---|
| Auth | `/auth/**` | Login, refresh token, logout |
| Chat | `/chat/**` | Send messages, stream responses |
| Conversations | `/conversations/**` | List, get, delete conversations |
| Postman | `/postman/**` | Import Postman collections |
| Swagger | `/swagger/**` | Import Swagger/OpenAPI specs |
| API Unification | `/api-docs/**` | Manage unified API documents |
| Users | `/users/**` | User profile and management |
| Admin | `/admin/**` | User, group, and access management |
| Trial | `/trial/**` | Trial user self-registration |
| Files | `/files/**` | Download exported files |
| Deployment Info | `/deployment/**` | Deployment mode and config info |
| Diagnostics | `/diagnostics/**` | System diagnostics |
| Health | `/actuator/health` | Health check |
| Metrics | `/actuator/prometheus` | Prometheus metrics |

## Running Tests

```bash
# All tests
./mvnw verify

# Unit tests only
./mvnw test -P unit-tests-only

# Integration tests only
./mvnw verify -P integration-tests
```

## Deploy to Render.com

A `render.yaml` is included for one-click deployment to Render.com. Set the following secrets in the Render dashboard:

- `MONGODB_URI`
- `OPENAI_API_KEY`
- `JWT_SECRET`

Optionally set the remaining environment variables listed above.

## Docker Image

Images are published to Docker Hub as `alesquiintelligence/backend` via GitHub Actions on every push to `master` and on version tags.

```bash
docker pull alesquiintelligence/backend:latest
```

## Project Structure

```
src/
├── main/
│   ├── java/es/alesqui/intelligence/
│   │   ├── controller/       # REST API controllers
│   │   ├── service/          # Business logic
│   │   │   ├── chat/         # AI orchestration and tools
│   │   │   └── unification/  # Postman/Swagger spec processing
│   │   ├── model/            # MongoDB domain entities
│   │   ├── repository/       # Reactive MongoDB repositories
│   │   ├── dto/              # Request/response DTOs
│   │   ├── config/           # Spring configuration
│   │   └── security/         # JWT filter and utilities
│   └── resources/
│       ├── application.properties
│       ├── prompts/          # AI system prompts
│       └── templates/        # Email templates
└── test/
    └── java/                 # Unit and integration tests
```
