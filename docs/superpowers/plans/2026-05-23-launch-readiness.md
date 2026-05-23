# Launch Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing Java Study app verifiable and deployable to ECS on port 8083.

**Architecture:** Keep the current React/Vite + Spring Boot split. Add a backend health endpoint, profile-aware security checks, a root verification script, and Spring Boot integration tests.

**Tech Stack:** React 19, Vite, TypeScript, Spring Boot 3.3.5, Java 21, JUnit 5, H2, Maven, Nginx, PM2/system process management on ECS.

---

### Task 1: Backend Integration Tests

**Files:**
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/test/java/com/javastudy/LaunchReadinessIntegrationTest.java`

- [ ] **Step 1: Add test profile configuration**

Create `backend/src/test/resources/application-test.yml` with H2, the repo content root, and a non-default JWT secret.

- [ ] **Step 2: Write failing integration tests**

Create `LaunchReadinessIntegrationTest` covering:
- `GET /api/health` returns 200 without auth.
- `GET /api/summary` returns 401 without auth.
- Registering a new user returns a token.
- Authenticated `GET /api/summary` returns total progress data.
- Authenticated `GET /api/chapters/1` returns chapter content.

- [ ] **Step 3: Run test to verify failure**

Run: `mvn -f backend/pom.xml -Dtest=LaunchReadinessIntegrationTest test`

Expected before implementation: health endpoint assertion fails because `/api/health` is not yet public and implemented.

### Task 2: Health And Security Implementation

**Files:**
- Create: `backend/src/main/java/com/javastudy/controller/HealthController.java`
- Modify: `backend/src/main/java/com/javastudy/security/SecurityConfig.java`
- Modify: `backend/src/main/java/com/javastudy/security/JwtService.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Add `HealthController`**

Expose `GET /api/health` returning `{"status":"ok","service":"java-study-backend"}`.

- [ ] **Step 2: Permit health endpoint**

Allow `/api/health` without authentication in the Spring Security filter chain.

- [ ] **Step 3: Make CORS configurable**

Read `app.cors-allowed-origins`, default it to `*` for local compatibility, and apply configured origins in `CorsConfigurationSource`.

- [ ] **Step 4: Reject unsafe JWT config outside local/test**

In `JwtService`, reject the default development JWT secret or secrets shorter than 32 chars when active profiles do not include `local` or `test`.

- [ ] **Step 5: Run integration tests**

Run: `mvn -f backend/pom.xml -Dtest=LaunchReadinessIntegrationTest test`

Expected after implementation: tests pass.

### Task 3: Root Verification Command

**Files:**
- Modify: `package.json`

- [ ] **Step 1: Add `verify` script**

Add `verify` that runs `npm run check && npm run build && mvn -f backend/pom.xml test`.

- [ ] **Step 2: Run full verification**

Run: `npm run verify`

Expected: frontend type check passes, frontend production build passes, backend tests pass.

### Task 4: ECS Deployment

**Files:**
- No repo file changes required.

- [ ] **Step 1: Package project**

Create a tarball excluding `node_modules`, `dist`, `.git`, `backend/target`, local data, and temporary files.

- [ ] **Step 2: Upload and build on ECS**

Upload to `/tmp/java-study-deploy.tar.gz`, extract to `/www/wwwroot/java-study`, run `npm install`, `npm run build`, and `mvn -f backend/pom.xml package -DskipTests`.

- [ ] **Step 3: Configure backend runtime**

Run backend on `127.0.0.1:18080` with production MySQL settings and a non-default JWT secret.

- [ ] **Step 4: Configure Nginx**

Serve `/www/wwwroot/java-study/dist` on public port `8083` and proxy `/api/` to `http://127.0.0.1:18080`.

- [ ] **Step 5: Verify deployment**

Run remote checks for `/`, `/api/health`, unauthenticated `/api/summary`, and browser-visible app shell.
