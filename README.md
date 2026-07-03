# Spring Boot Starter

A reference Spring Boot service that hits the common layers. Auth, persistence, migrations, observability, tests, container, CI.

## Stack

- Spring Boot 3.4 on Java 21
- PostgreSQL + Flyway
- Spring Data JPA
- Spring Security 6 (JWT access tokens, HS256, users DB-backed)
- CORS, springdoc OpenAPI
- Bucket4j rate limiting on the registration endpoint
- JSON structured logging under the `prod` profile
- Actuator + Micrometer Prometheus, readiness/liveness probes
- Virtual threads
- Testcontainers integration tests
- Multi-stage Dockerfile (non-root)
- GitHub Actions CI, Dependabot
- Spotless (opt-in: `./mvnw spotless:apply`)

## File tour

```
spring-starter/
├── pom.xml
├── Dockerfile
├── compose.yml
├── .env.example
├── .github/
│   ├── workflows/ci.yml
│   └── dependabot.yml
└── src/
    ├── main/
    │   ├── java/com/example/starter/
    │   │   ├── StarterApplication.java
    │   │   ├── auth/
    │   │   │   ├── AuthController.java
    │   │   │   ├── TokenService.java
    │   │   │   ├── LoginRequest.java
    │   │   │   └── TokenResponse.java
    │   │   ├── config/
    │   │   │   ├── AppProperties.java
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── JwtConfig.java
    │   │   │   ├── RequestLoggingConfig.java
    │   │   │   └── RateLimitingFilter.java
    │   │   ├── common/
    │   │   │   ├── NotFoundException.java
    │   │   │   ├── DuplicateEmailException.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   ├── greeting/
    │   │   │   └── GreetingController.java
    │   │   └── user/
    │   │       ├── User.java
    │   │       ├── UserRepository.java
    │   │       ├── CreateUserRequest.java
    │   │       ├── UpdateUserRequest.java
    │   │       ├── UserResponse.java
    │   │       ├── UserService.java
    │   │       ├── UserController.java
    │   │       ├── JpaUserDetailsService.java
    │   │       └── AdminBootstrap.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/migration/V1__create_users_table.sql
    └── test/
        └── java/com/example/starter/
            ├── auth/
            │   ├── TokenServiceTest.java
            │   └── AuthControllerSliceTest.java
            ├── config/
            │   ├── JwtConfigTest.java
            │   └── RateLimitingFilterTest.java
            ├── greeting/GreetingControllerIT.java
            └── user/
                ├── UserServiceTest.java
                ├── AdminBootstrapTest.java
                ├── UserControllerSliceTest.java
                ├── UserControllerSecurityTest.java
                ├── UserControllerIT.java
                └── JwtAuthIT.java
```

## API

| Method | Path             | Auth          | Description                              |
|--------|------------------|---------------|------------------------------------------|
| POST   | /api/auth/login  | public        | Exchange email and password for a JWT    |
| POST   | /api/users       | public        | Register a new user                      |
| GET    | /api/users       | authenticated | Paginated list (`?page=0&size=20`)       |
| GET    | /api/users/{id}  | authenticated | Get one user                             |
| PATCH  | /api/users/{id}  | authenticated | Partial update. Own account only, ADMIN can update anyone |
| DELETE | /api/users/{id}  | ADMIN         | Delete a user                            |
| GET    | /api/greeting    | public        | Configured greeting                      |
| GET    | /swagger-ui.html | public (locked down under `prod`) | OpenAPI / Swagger UI |

## Authentication

Stateless JWT access tokens signed with HS256.

1. Register with `POST /api/users`, or use the bootstrapped admin.
2. `POST /api/auth/login` with email and password. The response carries `token` and `expiresAt`.
3. Send `Authorization: Bearer <token>` on every protected call.

Tokens carry the email in `sub` and USER or ADMIN in a `role` claim. `JwtConfig` maps that claim back to a `ROLE_*` authority, so `hasRole("ADMIN")` in `SecurityConfig` and the ownership check on PATCH both key off the token.

The signing key comes from `JWT_SECRET` and must be at least 32 bytes. Under the `prod` profile a missing or short secret refuses to start. Everywhere else the app falls back to an ephemeral random key and logs a warning, so tokens stop working on restart. Expiry is `JWT_EXPIRY_MINUTES`, default 60.

There are no refresh tokens, which keeps this template to a single short-lived credential and a re-login when it expires. Adding them would mean a second long-lived token, server-side storage so they can be revoked, and a `/api/auth/refresh` endpoint that rotates both.

## Configuration

| Variable                  | Default                                                              |
|---------------------------|----------------------------------------------------------------------|
| `DATABASE_URL`            | `jdbc:postgresql://localhost:5432/starter`                           |
| `DATABASE_USER`           | `postgres`                                                           |
| `DATABASE_PASSWORD`       | `postgres`                                                           |
| `PORT`                    | `8080`                                                               |
| `ADMIN_EMAIL`             | `admin@example.com`                                                  |
| `ADMIN_PASSWORD`          | `changeme` (warns at startup if unchanged, refuses to start under `prod`) |
| `CORS_ALLOWED_ORIGINS`    | `http://localhost:3000,http://localhost:5173,http://localhost:8080`  |
| `RATE_LIMIT_REGISTRATION` | `100` (per-IP, per minute, on POST /api/users)                       |
| `RATE_LIMIT_TRUST_FORWARDED_FOR` | `false`. Set `true` only behind a proxy you control that overwrites `X-Forwarded-For` |
| `BCRYPT_STRENGTH`         | `10`                                                                 |
| `JWT_SECRET`              | unset. HS256 signing key, 32 bytes minimum. Required under `prod`, ephemeral fallback with a warning elsewhere |
| `JWT_EXPIRY_MINUTES`      | `60`                                                                 |
| `SPRING_PROFILES_ACTIVE`  | unset. Set to `prod` for JSON logs and tighter actuator              |

## Running locally

Requires Java 21 and Docker.

```bash
docker compose up -d
./mvnw spring-boot:run
```

App on http://localhost:8080. Swagger UI at /swagger-ui.html.

```bash
curl -X POST http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","name":"Alice","password":"supersecret"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"changeme"}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users
curl -H "Authorization: Bearer $TOKEN" -X DELETE http://localhost:8080/api/users/1
curl http://localhost:8080/api/greeting
curl http://localhost:8080/actuator/health
```

`requests.http` has the same flow for the IntelliJ HTTP client, including a login step that stores the token for the calls below it.

## Building a container

```bash
./mvnw spring-boot:build-image -DskipTests
docker run -p 8080:8080 starter:0.0.1-SNAPSHOT
```

Or with the included Dockerfile: `docker build -t starter .`

## Tests

```bash
./mvnw test
```

Three tiers. `UserServiceTest`, `TokenServiceTest`, `JwtConfigTest`, `AdminBootstrapTest`, and `RateLimitingFilterTest` are plain unit tests and run in milliseconds. `UserControllerSliceTest` and `AuthControllerSliceTest` load only the web layer with `@WebMvcTest` and mocked collaborators, and `UserControllerSecurityTest` does the same with the security filter chain enabled, using spring-security-test's `jwt()` post-processor for authenticated requests. The `*IT` classes (`UserControllerIT`, `JwtAuthIT`, `GreetingControllerIT`) spin up real Postgres via Testcontainers, so Docker must be running. `JwtAuthIT` is the full round trip, login for a real signed token, then list, PATCH ownership, and admin DELETE over the wire.

## Deploying

```bash
brew install flyctl
fly launch
fly deploy
```

On Windows, install flyctl with PowerShell instead.

```powershell
iwr https://fly.io/install.ps1 -useb | iex
```

Set the env vars from `.env.example` in your platform's secrets UI.

## Using this as a template

1. Rename `com.example.starter` and `starter` to your package and project. Search-and-replace covers `pom.xml`, every `package` line, and every `import`.
2. Replace `V1__create_users_table.sql` with your domain's first migration. Drop the `user/` and `greeting/` packages. They're examples. Flyway checksums every applied migration, so editing or replacing V1 against a database that already ran it fails validation. That applies to this repo too. If your local database ran an older V1, run `docker compose down -v` after pulling and let Flyway start clean.
3. Keep `config/`, `common/`, `AdminBootstrap`, and the test setup.
4. Copy `.env.example` to `.env`, fill in real values.
5. Rewrite this README for your project.

## Moving to production

Auth here is self-issued HS256 access tokens, which fits a single service that owns its users. If an identity provider enters the picture, point `spring.security.oauth2.resourceserver.jwt.issuer-uri` at it, drop `JwtConfig`, `TokenService`, and the login endpoint, and let the issuer own the user store.

Gaps to close before going public.

- Source `JWT_SECRET` from a secrets manager and rotate it on a schedule. Rotation invalidates every outstanding token, which is the trade-off of a single symmetric key with no key id.
- Terminate TLS at your load balancer or reverse proxy. The embedded Tomcat here is HTTP-only, and bearer tokens travel in headers, so plain HTTP leaks both credentials and tokens.
- Swagger UI and the OpenAPI docs require authentication under `prod`. Everywhere else they stay public.
- Rate limiting is in-process, which is fine for one node. Multi-node needs distributed buckets via `bucket4j-redis` or a gateway. Behind a proxy, set `RATE_LIMIT_TRUST_FORWARDED_FOR=true` so limits key on the real client address. The login endpoint has no rate limit at all, so put one in front of it before exposing it publicly.
- Source `ADMIN_PASSWORD` and `DATABASE_PASSWORD` from a secrets manager.
- `/actuator/prometheus` is dropped from the public exposure under `prod`. Re-expose it on the management port and scrape it there.
