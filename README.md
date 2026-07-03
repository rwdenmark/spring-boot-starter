# Spring Boot Starter

A reference Spring Boot service that hits the common layers. Auth, persistence, migrations, observability, tests, container, CI.

## Stack

- Spring Boot 3.4 on Java 21
- PostgreSQL + Flyway
- Spring Data JPA
- Spring Security 6 (HTTP Basic, user DB-backed)
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
    │   │   ├── config/
    │   │   │   ├── AppProperties.java
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── RequestLoggingConfig.java
    │   │   │   └── RateLimitingFilter.java
    │   │   ├── common/
    │   │   │   ├── NotFoundException.java
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
            ├── greeting/GreetingControllerIT.java
            └── user/
                ├── UserServiceTest.java
                ├── UserControllerSliceTest.java
                └── UserControllerIT.java
```

## API

| Method | Path             | Auth          | Description                              |
|--------|------------------|---------------|------------------------------------------|
| POST   | /api/users       | public        | Register a new user                      |
| GET    | /api/users       | authenticated | Paginated list (`?page=0&size=20`)       |
| GET    | /api/users/{id}  | authenticated | Get one user                             |
| PATCH  | /api/users/{id}  | authenticated | Partial update                           |
| DELETE | /api/users/{id}  | ADMIN         | Delete a user                            |
| GET    | /api/greeting    | public        | Configured greeting                      |
| GET    | /swagger-ui.html | public        | OpenAPI / Swagger UI                     |

## Configuration

| Variable                  | Default                                                              |
|---------------------------|----------------------------------------------------------------------|
| `DATABASE_URL`            | `jdbc:postgresql://localhost:5432/starter`                           |
| `DATABASE_USER`           | `postgres`                                                           |
| `DATABASE_PASSWORD`       | `postgres`                                                           |
| `PORT`                    | `8080`                                                               |
| `ADMIN_EMAIL`             | `admin@example.com`                                                  |
| `ADMIN_PASSWORD`          | `changeme` (warns at startup if unchanged)                           |
| `CORS_ALLOWED_ORIGINS`    | `http://localhost:3000,http://localhost:5173,http://localhost:8080`  |
| `RATE_LIMIT_REGISTRATION` | `100` (per-IP, per minute, on POST /api/users)                       |
| `BCRYPT_STRENGTH`         | `10`                                                                 |
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

curl -u admin@example.com:changeme http://localhost:8080/api/users
curl -u admin@example.com:changeme -X DELETE http://localhost:8080/api/users/1
curl http://localhost:8080/api/greeting
curl http://localhost:8080/actuator/health
```

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

Three tiers. `UserServiceTest` is a plain Mockito unit test and runs in milliseconds. `UserControllerSliceTest` loads only the web layer with `@WebMvcTest` and a mocked service. The two `*IT` classes (`UserControllerIT`, `GreetingControllerIT`) spin up real Postgres via Testcontainers, so Docker must be running.

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
2. Replace `V1__create_users_table.sql` with your domain's first migration. Drop the `user/` and `greeting/` packages. They're examples. Replacing that migration on a database that already ran it will fail Flyway's checksum check, so wipe the `postgres_data` volume first.
3. Keep `config/`, `common/`, `AdminBootstrap`, and the test setup.
4. Copy `.env.example` to `.env`, fill in real values.
5. Rewrite this README for your project.

## Moving to production

HTTP Basic is in here so you can hit the API with `curl -u` on day one. For production, swap it for JWT/OAuth2.

1. Add `spring-boot-starter-oauth2-resource-server`.
2. In `SecurityConfig`, replace `.httpBasic(Customizer.withDefaults())` with `.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))`.
3. Set `spring.security.oauth2.resourceserver.jwt.issuer-uri` (or `jwk-set-uri`).
4. Add a token-issuing endpoint or use your issuer's.
5. Drop `JpaUserDetailsService` if your issuer owns the user store.

There are other gaps to close before going public.

- Rate limiting is in-process, which is fine for one node. Multi-node needs distributed buckets via `bucket4j-redis` or a gateway.
- Source `ADMIN_PASSWORD` and `DATABASE_PASSWORD` from a secrets manager.
- `/actuator/prometheus` is dropped from the public exposure under `prod`. Re-expose it on the management port and scrape it there.
- Terminate TLS at your load balancer or reverse proxy. The embedded Tomcat here is HTTP-only.
