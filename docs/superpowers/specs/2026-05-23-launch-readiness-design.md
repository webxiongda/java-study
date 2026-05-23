# Launch Readiness Design

## Goal

Prepare the Java Study workbench for ECS deployment by adding the minimum production readiness controls needed to verify, observe, and safely run the existing app.

## Scope

- Add one unauthenticated health endpoint for deployment checks.
- Add a single verification command that runs frontend type checking, frontend build, and backend tests.
- Add backend integration tests for auth and core learning API behavior.
- Tighten production security configuration for JWT secrets and CORS origins.
- Deploy the Vite frontend and Spring Boot backend behind Nginx on ECS port 8083.

## Architecture

The frontend remains a static Vite build served by Nginx. The backend remains a Spring Boot application on an internal port, with Nginx proxying `/api/` to it. The backend exposes `/api/health` without authentication so deployment scripts can distinguish "process is alive" from "auth is required".

## Security

Local development can keep permissive defaults. Non-local profiles must not use the development JWT secret, and CORS origins become configurable through `app.cors-allowed-origins`.

## Testing

Backend tests use the Spring Boot test profile with H2 and the existing Markdown content root. Tests cover unauthenticated health, unauthenticated rejection for protected APIs, registration/login, and authenticated access to summary and chapter metadata.

## Deployment

Use ECS project name `java-study`, public port `8083`, internal backend port `18080`, Nginx static hosting for `dist`, and Nginx proxying `/api/` to the backend.
