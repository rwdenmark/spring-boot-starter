# Modern Spring Boot Starter

A reference project for someone modernizing from older Java/Apache stacks. Hits every layer you'll need to know.

## What's in here

- **Spring Boot 3.4** on **Java 21**
- **PostgreSQL** with **Flyway** migrations
- **Spring Data JPA** for persistence
- **Spring Security 6** (modern `SecurityFilterChain` style)
- **Bean validation** with Jakarta annotations
- **RFC 7807 problem details** for error responses
- **Actuator + Micrometer + Prometheus** for observability
- **Virtual threads** enabled (Java 21 feature)
- **Testcontainers** for integration tests against real Postgres
- **Multi-stage Dockerfile** producing a small non-root image
- **GitHub Actions** CI workflow
- **Docker Compose** for local Postgres

## File tour

```
spring-starter/
├── pom.xml                              # Dependencies (Maven)
├── Dockerfile                           # Multi-stage build, non-root runtime
├── compose.yml                          # Local Postgres
├── .github/workflows/ci.yml             # CI pipeline
└── src/
    ├── main/
    │   ├── java/com/example/starter/
    │   │   ├── StarterApplication.java          # @SpringBootApplication entry point
    │   │   ├── config/
    │   │   │   └── SecurityConfig.java          # SecurityFilterChain bean
    │   │   ├── common/
    │   │   │   ├── NotFoundException.java
    │   │   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice
    │   │   └── user/
    │   │       ├── User.java                    # JPA entity
    │   │       ├── UserRepository.java          # Spring Data interface
    │   │       ├── UserDtos.java                # Records for request/response
    │   │       ├── UserService.java             # Business logic
    │   │       └── UserController.java          # REST endpoints
    │   └── resources/
    │       ├── application.yml                  # Config (no XML)
    │       └── db/migration/
    │           └── V1__create_users_table.sql   # Flyway migration
    └── test/
        └── java/com/example/starter/user/
            └── UserControllerIT.java            # @SpringBootTest + Testcontainers
```

## Running locally

You need Java 21 (`brew install openjdk@21` on macOS, or use SDKMAN) and Docker.

```bash
# Start Postgres
docker compose up -d

# Run the app (generates the Maven wrapper on first run if needed)
./mvnw spring-boot:run
```

App is on http://localhost:8080.

### Try it

```bash
# Create a user
curl -X POST http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","name":"Alice","password":"supersecret"}'

# List users
curl http://localhost:8080/api/users

# Health check
curl http://localhost:8080/actuator/health
```

## Building a container

Spring Boot's buildpack support means **no Dockerfile needed** (though one is included for reference):

```bash
./mvnw spring-boot:build-image -DskipTests
docker run -p 8080:8080 starter:0.0.1-SNAPSHOT
```

Or with the included Dockerfile:

```bash
docker build -t starter .
```

## Running tests

```bash
./mvnw test
```

Integration tests spin up real Postgres via Testcontainers (needs Docker running).

## Deploying

The fastest path to "running on the internet":

```bash
# Fly.io (recommended for first deployment)
brew install flyctl
fly launch        # reads the Dockerfile, sets up Postgres, gets you a URL
fly deploy
```

## Things to explore next

In order of value:

1. **Add JWT auth.** Replace `permitAll()` in `SecurityConfig` with real authentication. Use `spring-boot-starter-oauth2-resource-server` and JWT tokens.
2. **Add `spring-boot-docker-compose`** dependency — Boot will auto-start Compose services when you run the app locally.
3. **Try a second Flyway migration** (`V2__...sql`) to feel the workflow.
4. **Switch to Gradle** if you want — Initializr can regenerate the same project with `build.gradle.kts`.
5. **Add OpenAPI docs** with `springdoc-openapi-starter-webmvc-ui` — auto-generates Swagger UI at `/swagger-ui.html`.
6. **Add Spring AI** if you want to play with LLM integration.

## Notes coming from older Spring

- No `web.xml`, no `applicationContext.xml`, no `dispatcher-servlet.xml`. All gone.
- `@Autowired` field injection works but constructor injection (shown here) is the convention now.
- `javax.*` imports → `jakarta.*`. Easy find-and-replace.
- `WebSecurityConfigurerAdapter` is deprecated; use the `SecurityFilterChain` bean style (shown here).
- `ddl-auto: validate` + Flyway is the production-grade combo. Never use `update` or `create-drop` outside of throwaway tests.
- Embedded Tomcat means the WAR is dead. Build a fat JAR or a container.
