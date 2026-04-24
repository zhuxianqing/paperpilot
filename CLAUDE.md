# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PaperPilot (学术文献助手) is a two-part system: a Chrome extension (Vue 3 + TypeScript) and a Java backend (Spring Boot 3.2). It helps researchers search, AI-summarize, and export academic papers from Web of Science and ScienceDirect.

## Repository Layout

```
paperpilot/
  paperpilot-extension/   # Chrome Extension (Vue 3, Vite, TailwindCSS)
  paperpilot-backend/     # Spring Boot backend (Java 17, Maven)
  paperpilot-design.md    # Full design specification (Chinese)
```

## Build & Run Commands

### Extension (`paperpilot-extension/`)

```bash
npm run dev          # Vite dev server
npm run build        # Production build (vue-tsc --noEmit && vite build)
npm run build:dev    # Dev build (vite build --mode development)
npm run lint         # ESLint check (.vue, .ts, .tsx)
npm run lint:fix     # ESLint autofix
```

### Backend (`paperpilot-backend/`)

```bash
docker-compose up -d                          # Start MySQL 8.0 + Redis 7
mvn spring-boot:run                           # Run with default profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev  # Run with dev profile
mvn clean package                             # Package JAR
mvn test                                      # Run tests
mvn test -Dtest=ExportControllerTest           # Run single test class
```

Backend runs on port 8080. Requires `application-dev.yml` for local config (JWT secret, encryption key, AI API key, mail credentials).

**Local backend environment**
- Java version: `17`
- `JAVA_HOME`: `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`
- Maven settings: `/Users/zhuxianqing/maven/setting.xml`
- When running Maven locally, prefer using the above `JAVA_HOME` and Maven settings explicitly if the shell environment is inconsistent.

## Architecture

### Chrome Extension — Message-Passing Architecture

The extension operates across three execution contexts connected via Chrome's messaging:

1. **Hook Script** (`src/content/hook-inject.ts`) — Runs in MAIN world. IIFE that monkey-patches `window.fetch` to intercept WOS API calls (URLs containing "wosnx"), then sends intercepted data via `window.postMessage`.

2. **Content Script** (`src/content/index.ts`) — Runs in ISOLATED world. Receives postMessage from hook, parses WOS NDJSON response (`parser.ts`), stores results in `chrome.storage.local`, notifies background worker.

3. **Background Service Worker** (`src/background/index.ts`) — Central message router handling 18+ message types (LOGIN, REGISTER, ANALYZE_PAPERS, EXPORT_EXCEL, etc.). Delegates to `api-client.ts` for all backend HTTP calls. Manages JWT tokens in `chrome.storage.local`.

4. **Popup UI** (`src/popup/App.vue`) — Vue 3 SPA with Pinia store (`stores/app.ts`). Four views: login, papers, ai-config, export.

**Path aliases** (tsconfig): `@/*` → `src/*`, `@shared/*`, `@popup/*`, `@content/*`, `@background/*`, `@options/*`.

**Vite build** produces 5 separate entry bundles: popup, options, content, hook-inject, background.

**API base URL** is hardcoded in `src/shared/constants/api.ts` as `http://localhost:8080/api/v1`.

### Backend — Layered Spring Boot

Standard package structure under `com.paperpilot`:

- `controller/` — REST endpoints (Auth, AI, AIConfig, Export, Task, User)
- `service/impl/` — All business logic implementations
- `mapper/` — MyBatis-Plus data access
- `entity/` — Database entities
- `dto/` — Request DTOs and response VOs
- `security/` — Stateless JWT auth (JwtAuthenticationFilter, JwtTokenProvider)
- `exception/` — BusinessException + ErrorCode enum + GlobalExceptionHandler
- `config/` — SecurityConfig (CSRF disabled, CORS open), WebClient, MyBatis-Plus, JWT properties

**Key design decisions:**
- Stateless JWT authentication (24h access / 7d refresh tokens)
- User API keys encrypted with AES-GCM before storage (BYOK pattern)
- Redis-based rate limiting (5 req/min) and anti-abuse detection (SHA-256 duplicate request hashing)
- Quota system: free tier (3 daily) + paid quota, Redis-based counting with rollback on failure
- Excel export uses local file storage with 7-day auto-cleanup (`ExportCleanupTask`)
- Public endpoints: `/api/v1/auth/**`, `/api/v1/payment/alipay/notify`
- Default AI provider: MiniMax M2.7

### Manifest V3

`manifest.json` declares: permissions `storage`, `activeTab`, `scripting`; host permissions for webofscience.com, sciencedirect.com, api.paperpilot.com, localhost:8080. Content script runs at `document_start`; `hook-inject.js` is a web-accessible resource.
