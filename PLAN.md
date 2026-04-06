# Alesqui Intelligence - Plan de Evolutivos

> Roadmap de mejoras priorizadas en orden cronologico.
> Cada fase se construye sobre la anterior para minimizar riesgos y maximizar valor incremental.

---

## Fase 1 - Dashboard de usuario y mejora de la experiencia post-login

**Prioridad:** Alta | **Impacto:** Alto | **Complejidad:** Media
**Repos afectados:** `alesqui-intelligence-frontend`, `alesqui-intelligence-backend`

### Contexto

Actualmente el usuario aterriza directamente en el chat al iniciar sesion. Esto no ofrece contexto sobre el estado de su cuenta, las APIs disponibles, ni su actividad reciente. Un dashboard de bienvenida mejora la retencion y reduce la curva de aprendizaje.

### Tareas

#### Backend (`alesqui-intelligence-backend`)

- [ ] **Nuevo endpoint `GET /api/dashboard/summary`** que devuelva en una sola llamada:
  - Numero de conversaciones del usuario (total y ultimos 7 dias)
  - APIs a las que tiene acceso (nombre + estado de salud via `DiagnosticsController`)
  - Ultima conversacion activa (id, timestamp, resumen)
  - Dias restantes de trial (si aplica, reutilizar `TrialRegistrationService.getDaysRemaining`)
  - Estadisticas basicas: total de mensajes enviados, charts generados, excels exportados
- [ ] **Nuevo `DashboardService`** que agregue datos de `ConversationRecordRepository`, `UnifiedApiRepository`, `ApiGroupLinkRepository` y `UserService`
- [ ] **Tests unitarios e integracion** para el nuevo endpoint

#### Frontend (`alesqui-intelligence-frontend`)

- [ ] Nueva ruta `/dashboard` como pagina principal post-login
- [ ] Componentes: tarjetas de resumen, lista de APIs disponibles, accesos rapidos al chat, historial reciente
- [ ] Redirigir la ruta post-login de `/chat` a `/dashboard`
- [ ] Enlace rapido "Nueva conversacion" y "Continuar ultima conversacion"

### Por que primero

Es un cambio de alto impacto con complejidad moderada. Mejora inmediatamente la percepcion del producto y sienta las bases para las metricas de uso (Fase 4).

---

## Fase 2 - Login con Google (OAuth2 Social Login)

**Prioridad:** Alta | **Impacto:** Alto | **Complejidad:** Alta
**Repos afectados:** `alesqui-intelligence-backend`, `alesqui-intelligence-frontend`, `alesqui-intelligence-landing`

### Contexto

Los usuarios trial deben actualmente crear una cuenta con email y contrasena. Ofrecer "Sign in with Google" reduce drasticamente la friccion del registro (hasta un 50% menos de abandono segun benchmarks de conversion SaaS). El modelo de usuario (`User.java`) ya tiene campos para trial; hay que extenderlo para soportar identidades federadas.

### Tareas

#### Backend

- [ ] **Anadir dependencias**: `spring-boot-starter-oauth2-client` y `spring-boot-starter-oauth2-resource-server`
- [ ] **Nuevo campo en `User`**: `authProvider` (enum: `LOCAL`, `GOOGLE`, futuro `GITHUB`, `MICROSOFT`), `providerId` (ID externo del proveedor)
- [ ] **Nuevo endpoint `POST /api/auth/oauth2/google`**: recibe el `id_token` de Google, lo valida contra `https://oauth2.googleapis.com/tokeninfo`, busca o crea el usuario, devuelve JWT propio
- [ ] **Flujo de vinculacion**: si un usuario `LOCAL` ya existe con el mismo email, permitir vincular la cuenta de Google (requiere confirmacion)
- [ ] **Configuracion**: nuevas variables de entorno `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- [ ] **`SecurityConfig`**: permitir `/api/auth/oauth2/**` como ruta publica
- [ ] **Adaptar `JwtService`**: incluir `authProvider` en el payload del token
- [ ] **Tests** con WireMock simulando la respuesta de Google tokeninfo

#### Frontend

- [ ] Boton "Continuar con Google" en login y en pagina de registro trial
- [ ] Flujo con Google Identity Services (GSI) para obtener el `id_token` client-side
- [ ] Modal de vinculacion de cuenta si el email ya existe como usuario LOCAL

#### Landing

- [ ] Actualizar CTA de registro para mostrar opcion de Google

### Notas de seguridad

- Validar siempre el `id_token` server-side, nunca confiar en datos del client
- Verificar `aud` (audience) contra nuestro `GOOGLE_CLIENT_ID`
- Verificar `iss` sea `accounts.google.com` o `https://accounts.google.com`
- Los usuarios OAuth2 no necesitan `password` ni `activationToken`

---

## Fase 3 - Internacionalizacion del frontend (i18n: ES + EN)

**Prioridad:** Media | **Impacto:** Alto | **Complejidad:** Media
**Repos afectados:** `alesqui-intelligence-frontend`, `alesqui-intelligence-landing`

### Contexto

El frontend esta solo en ingles. La landing y el producto van dirigidos a un mercado hispanohablante ademas del internacional. El prompt del sistema ya dice "Always communicate in the user's language", por lo que el backend ya soporta respuestas multilingue. Falta la UI.

### Tareas

#### Frontend (`alesqui-intelligence-frontend`)

- [ ] **Integrar `react-i18next`** (o `next-intl` si se migra a Next.js)
- [ ] **Extraer todos los strings literales** a ficheros de traduccion JSON:
  - `locales/en/common.json`, `locales/en/chat.json`, `locales/en/dashboard.json`, `locales/en/admin.json`
  - `locales/es/common.json`, `locales/es/chat.json`, `locales/es/dashboard.json`, `locales/es/admin.json`
- [ ] **Selector de idioma** en la barra de navegacion (persistido en `localStorage`)
- [ ] **Deteccion automatica**: usar `navigator.language` para seleccionar idioma por defecto
- [ ] **Formateo de fechas y numeros** con `Intl.DateTimeFormat` y `Intl.NumberFormat` segun locale

#### Landing (`alesqui-intelligence-landing`)

- [ ] Soporte i18n con `next-intl` (Next.js ya lo soporta nativamente)
- [ ] Rutas localizadas: `/es/`, `/en/` con `hreflang` tags
- [ ] Contenido SEO traducido (titulos, meta descriptions, OpenGraph)

#### Backend (cambio menor)

- [ ] **Nuevo campo opcional `preferredLanguage` en `User`** (default: `en`)
- [ ] Incluir `preferredLanguage` en el JWT o en la respuesta de `/api/auth/login` para que el front pueda hidratar el idioma al cargar
- [ ] El prompt del sistema ya responde en el idioma del usuario, no hace falta cambiar `chat-system.txt`

### Idiomas futuros

La arquitectura i18n debe ser extensible. Anadir un nuevo idioma sera tan simple como crear un nuevo directorio `locales/{code}/`.

---

## Fase 4 - Panel de administracion: control de tokens consumidos

**Prioridad:** Alta | **Impacto:** Alto | **Complejidad:** Alta
**Repos afectados:** `alesqui-intelligence-backend`, `alesqui-intelligence-frontend`

### Contexto

No existe tracking de tokens consumidos por usuario ni grupo. El `ChatOrchestrationService` registra `totalTokens` en metadata del response, pero no se persiste en base de datos. Los `MeterRegistry` timers solo miden duracion, no tokens. Para un producto SaaS/enterprise esto es critico para control de costes y facturacion.

### Tareas

#### Backend

- [ ] **Nuevo documento MongoDB `TokenUsageRecord`**:
  ```
  {
    id, userId, username, groupIds[], conversationId,
    model, promptTokens, completionTokens, totalTokens,
    estimatedCostUsd, timestamp
  }
  ```
- [ ] **`TokenTrackingService`**: interceptar el `ChatResponse` de Spring AI para extraer `usage()` (promptTokens, completionTokens) y persistir el registro
- [ ] **Integrar en `AIChatService.chatWithTools()`**: tras cada llamada al modelo (initial call + cada iteracion del loop ReAct), acumular tokens y persistir al final
- [ ] **Nuevos endpoints de admin** (`ROLE_SUPERADMIN`):
  - `GET /api/admin/usage/summary` - resumen global: tokens totales, coste estimado, por periodo
  - `GET /api/admin/usage/by-user` - desglose por usuario con paginacion
  - `GET /api/admin/usage/by-group` - desglose por grupo
  - `GET /api/admin/usage/by-user/{userId}/history` - historial detallado de un usuario
  - `GET /api/admin/usage/export` - exportar a Excel
- [ ] **Limites de uso (opcional)**: permitir al admin configurar limites de tokens por usuario/grupo/mes, con alerta cuando se alcance el 80% y bloqueo al 100%
- [ ] **Indices MongoDB**: compound index en `(userId, timestamp)` y `(groupIds, timestamp)` para consultas eficientes
- [ ] **Tests**: unitarios para el tracking, integracion para los endpoints

#### Frontend

- [ ] **Nueva seccion en el panel de admin**: "Consumo de tokens" / "Token Usage"
- [ ] Graficos de barras: consumo mensual por usuario, por grupo
- [ ] Tabla con ranking de usuarios por consumo
- [ ] Filtros: por rango de fechas, usuario, grupo, modelo
- [ ] KPIs: total tokens mes actual, coste estimado, media por conversacion
- [ ] Boton de exportar a Excel

### Formula de coste estimado

Basada en pricing de OpenAI para `gpt-4o-mini`:
- Input: $0.15 / 1M tokens
- Output: $0.60 / 1M tokens
- Configurable via `application.properties` para que se adapte a cambios de precio o cambios de modelo

---

## Fase 5 - Mejora de SEO de la landing page

**Prioridad:** Media | **Impacto:** Medio-Alto | **Complejidad:** Media
**Repos afectados:** `alesqui-intelligence-landing`

### Contexto

La landing page es un proyecto Next.js separado. Las mejoras de SEO son fundamentales para la adquisicion organica de usuarios trial.

### Tareas

#### Tecnico (On-Page SEO)

- [ ] **Meta tags optimizados**: title, description, og:title, og:description, og:image, twitter:card en cada pagina
- [ ] **Schema.org structured data**: `SoftwareApplication`, `Organization`, `FAQPage`
- [ ] **Sitemap.xml** dinamico generado por Next.js (`next-sitemap`)
- [ ] **robots.txt** optimizado
- [ ] **Canonical URLs** para evitar contenido duplicado con i18n
- [ ] **`hreflang` tags** para versiones ES/EN (enlaza con Fase 3)
- [ ] **Optimizacion de imagenes**: formato WebP/AVIF, lazy loading, dimensiones explicitas
- [ ] **Core Web Vitals**: LCP < 2.5s, FID < 100ms, CLS < 0.1
  - Precargar fuentes criticas
  - Eliminar JS que bloquee el render
  - Usar `next/image` con `priority` para imagenes above the fold

#### Contenido (Content SEO)

- [ ] **Blog/Resources section**: articulos sobre AI conversacional, automatizacion de APIs, casos de uso
- [ ] **Pagina de FAQ** con schema `FAQPage`
- [ ] **Pagina de pricing** (aunque sea para trial vs enterprise)
- [ ] **Testimonios / casos de uso** con schema `Review`

#### Tecnico (Off-Page)

- [ ] Configurar Google Search Console y Bing Webmaster Tools
- [ ] Enviar sitemap
- [ ] Configurar analytics (Google Analytics 4 o Plausible para privacidad)

---

## Fase 6 - Evolutivos de alto valor sugeridos

> Mejoras adicionales identificadas tras el analisis del codigo fuente que pueden diferenciar significativamente el producto.

### 6.1 - Soporte multi-modelo de IA

**Impacto:** Muy Alto | **Complejidad:** Media

El backend esta acoplado a OpenAI `gpt-4o-mini`. Spring AI ya soporta multiples proveedores.

- [ ] **Abstraccion de modelo**: configuracion por tenant/grupo del modelo a usar
- [ ] **Soporte Anthropic Claude**: anadir `spring-ai-starter-model-anthropic`
- [ ] **Soporte modelos locales**: Ollama via `spring-ai-starter-model-ollama` para despliegues on-premise sin salida a Internet
- [ ] **Selector de modelo por API/grupo**: permitir al admin elegir que modelo usar para cada API (ej: modelos baratos para consultas simples, potentes para analisis complejos)
- [ ] **Fallback automatico**: si el modelo primario falla, intentar con un secundario

### 6.2 - Historial de conversaciones con busqueda semantica

**Impacto:** Alto | **Complejidad:** Alta

- [ ] **Busqueda full-text** en MongoDB Atlas Search sobre `userPrompt` y `responseText` de `ConversationRecord`
- [ ] **Embeddings + busqueda vectorial**: generar embeddings de cada conversacion y permitir busqueda semantica ("encuentra la conversacion donde hablamos de ventas Q3")
- [ ] **Exportar historial completo** a PDF

### 6.3 - Notificaciones y alertas en tiempo real

**Impacto:** Alto | **Complejidad:** Media

- [ ] **WebSocket/SSE para notificaciones push**: alertas de trial proximo a expirar, limites de tokens alcanzados, nuevas APIs disponibles
- [ ] **Email de resumen semanal** con estadisticas de uso (reutilizar `EmailService` existente)
- [ ] **Alertas de salud de APIs**: si `DiagnosticsController` detecta una API caida, notificar a IT y Superadmin

### 6.4 - API Keys para integracion programatica

**Impacto:** Alto | **Complejidad:** Media

- [ ] **Nuevo tipo de autenticacion `API_KEY`**: permitir a los usuarios generar API keys para integrar Alesqui Intelligence en sus propios sistemas
- [ ] **Documento `ApiKey`**: userId, key (hash), name, permissions, createdAt, expiresAt, lastUsedAt
- [ ] **Endpoint `POST /api/user/api-keys`**: crear, listar, revocar claves
- [ ] **`ApiKeyAuthenticationWebFilter`**: validar API keys en paralelo al JWT

### 6.5 - Plantillas de prompts reutilizables (Prompt Library)

**Impacto:** Medio-Alto | **Complejidad:** Baja

- [ ] **Documento `PromptTemplate`**: userId (o global), title, promptText, category, usageCount
- [ ] Los usuarios pueden guardar prompts frecuentes: "Dame las ventas del ultimo mes", "Exporta todos los pedidos de hoy"
- [ ] **Prompts globales**: el admin puede crear plantillas visibles para todos los usuarios de un grupo
- [ ] **Autocompletado** en el input del chat basado en plantillas guardadas

### 6.6 - Modo oscuro

**Impacto:** Medio | **Complejidad:** Baja

- [ ] Soporte de tema oscuro en el frontend
- [ ] Persistir preferencia en `User.preferences` o `localStorage`
- [ ] Seguir preferencia del sistema operativo por defecto (`prefers-color-scheme`)

### 6.7 - Rate limiting avanzado y quotas por plan

**Impacto:** Alto | **Complejidad:** Media

El `RateLimitingService` actual solo controla registros trial por IP.

- [ ] **Rate limiting por usuario en chat**: max mensajes/hora configurable por rol
- [ ] **Quotas mensuales de tokens** por plan (Trial: 100K tokens, Business: 1M, Enterprise: ilimitado)
- [ ] **`429 Too Many Requests`** con header `Retry-After`
- [ ] **Panel de admin** para visualizar y ajustar quotas

### 6.8 - Conversaciones compartidas y colaborativas

**Impacto:** Medio-Alto | **Complejidad:** Alta

- [ ] **Compartir conversacion por enlace** (read-only) con usuarios del mismo grupo
- [ ] **Hilos de discusion** dentro de una conversacion: un usuario puede "bifurcar" una respuesta para explorar una variante sin perder el hilo original
- [ ] **Mentions**: `@usuario` en el chat para compartir hallazgos

### 6.9 - Mejoras de seguridad

**Impacto:** Alto | **Complejidad:** Media

- [ ] **Autenticacion multifactor (MFA/2FA)**: TOTP via Google Authenticator / Authy
- [ ] **Bloqueo de cuenta** tras N intentos fallidos (actualmente `isAccountNonLocked()` siempre devuelve `true`)
- [ ] **Rotacion de JWT secret** sin downtime (soportar 2 secrets activos durante la transicion)
- [ ] **CSP headers** en el frontend

---

## Fase 7 - Migracion a Spring AI 2.0

**Prioridad:** Media (cuando este disponible para produccion) | **Impacto:** Alto | **Complejidad:** Alta
**Repos afectados:** `alesqui-intelligence-backend`

### Contexto

Spring AI 2.0 esta en desarrollo. La version actual es 1.0.3. La migracion debe hacerse cuando el equipo de Spring anuncie la GA (General Availability) y se confirme la estabilidad para produccion.

### Preparacion (se puede hacer ya)

- [ ] **Monitorizar releases**: seguir el [repositorio de Spring AI](https://github.com/spring-projects/spring-ai) y el blog de Spring
- [ ] **Leer la guia de migracion** cuando se publique
- [ ] **Identificar breaking changes** que afecten:
  - `ChatClient` API (builder pattern, prompt construction)
  - `ToolCallback` / `ToolCallingManager` (la API de tools ha cambiado significativamente entre 0.8 -> 1.0, es probable que cambie de nuevo)
  - `ChatMemory` interface
  - `BeanOutputConverter` y structured output
  - Propiedades de configuracion en `application.properties`

### Ejecucion (cuando Spring AI 2.0 sea GA)

- [ ] **Crear rama `feature/spring-ai-2.0`**
- [ ] **Actualizar BOM** en `pom.xml`: `spring-ai.version` a la nueva version
- [ ] **Adaptar `ChatClientConfig`**: reconstruir la configuracion del ChatClient segun la nueva API
- [ ] **Adaptar `AIChatService`**: ajustar el loop ReAct al nuevo modelo de tool calling
- [ ] **Adaptar tools** (`ApiDiscoveryTools`, `ApiInvocationTools`, `DataTools`, `ChartTools`, `ExportTools`): verificar que las anotaciones `@Tool` y signatures siguen siendo compatibles
- [ ] **Adaptar `ChatPromptBuilder`** y `HallucinationCorrector`
- [ ] **Revisar `ToolResponseProcessor`**: la estructura de respuestas de tools puede cambiar
- [ ] **Ejecutar toda la suite de tests** y corregir fallos
- [ ] **Test E2E** completo con conversaciones reales
- [ ] **Evaluar nuevas funcionalidades** de Spring AI 2.0 que puedan beneficiar al producto:
  - Soporte nativo de streaming con tools (actualmente se usa `.call()` sincrono en el loop)
  - Mejor soporte de multi-modal (imagenes en el chat)
  - Advisors mejorados
  - Evaluacion de respuestas integrada

---

## Resumen visual del roadmap

```
Fase 1 - Dashboard de usuario ..................... [Alta prioridad]
  |
Fase 2 - Login con Google ........................ [Alta prioridad]
  |
Fase 3 - Internacionalizacion (i18n) ............. [Media prioridad]
  |
Fase 4 - Panel de tokens consumidos .............. [Alta prioridad]
  |
Fase 5 - SEO de la landing ....................... [Media prioridad]
  |
Fase 6 - Evolutivos sugeridos (paralelo) ......... [Segun recursos]
  |   6.1 Multi-modelo IA
  |   6.2 Busqueda semantica en historial
  |   6.3 Notificaciones real-time
  |   6.4 API Keys
  |   6.5 Prompt Library
  |   6.6 Modo oscuro
  |   6.7 Rate limiting avanzado
  |   6.8 Conversaciones compartidas
  |   6.9 Mejoras de seguridad
  |
Fase 7 - Migracion Spring AI 2.0 ................. [Cuando sea GA]
```

---

## Criterios de decision

| Criterio | Peso |
|----------|------|
| Impacto en retencion de usuarios trial | Alto |
| Reduccion de friccion en onboarding | Alto |
| Diferenciacion competitiva | Alto |
| Facilidad de implementacion | Medio |
| Dependencias externas (Spring AI 2.0) | Bloqueante si aplica |
| ROI para clientes enterprise | Alto |

---

*Documento generado el 2026-04-06. Revisar trimestralmente o cuando haya cambios significativos en el stack.*
