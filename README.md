# Modern Spring Boot Starter

A reference project for someone modernizing from older Java/Apache stacks. Hits every layer you'll need to know.

## What's in here

- **Spring Boot 3.4** on **Java 21**
- **PostgreSQL** with **Flyway** migrations
- **Spring Data JPA** for persistence
- **Spring Security 6** (modern `SecurityFilterChain` style, HTTP Basic backed by the user DB)
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
    │   │   │   ├── AppProperties.java           # @ConfigurationProperties for `app.*`
    │   │   │   └── SecurityConfig.java          # SecurityFilterChain bean
    │   │   ├── common/
    │   │   │   ├── NotFoundException.java
    │   │   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice
    │   │   ├── greeting/
    │   │   │   └── GreetingController.java      # Example endpoint reading config
    │   │   └── user/
    │   │       ├── User.java                    # JPA entity (password JsonIgnored)
    │   │       ├── UserRepository.java          # Spring Data interface
    │   │       ├── CreateUserRequest.java       # POST payload
    │   │       ├── UpdateUserRequest.java       # PATCH payload (optional fields)
    │   │       ├── UserResponse.java            # API response DTO
    │   │       ├── UserService.java             # Business logic
    │   │       ├── UserController.java          # REST endpoints
    │   │       └── JpaUserDetailsService.java   # Spring Security auth source
    │   └── resources/
    │       ├── application.yml                  # Config (no XML)
    │       ├── application-dev.yml              # Dev-profile overrides
    │       └── db/migration/
    │           └── V1__create_users_table.sql   # Flyway migration
    └── test/
        └── java/com/example/starter/
            ├── greeting/GreetingControllerTest.java   # @WebMvcTest slice
            └── user/
                ├── UserServiceTest.java               # Mockito unit tests
                ├── UserControllerTest.java            # @WebMvcTest slice
                └── UserControllerIT.java              # Real Postgres via Testcontainers
```

## API

| Method | Path             | Auth          | Description                              |
|--------|------------------|---------------|------------------------------------------|
| POST   | /api/users       | public        | Register a new user                      |
| GET    | /api/users       | authenticated | Paginated list (`?page=0&size=20`)       |
| GET    | /api/users/{id}  | authenticated | Get one user                             |
| PATCH  | /api/users/{id}  | authenticated | Partial update (any subset of fields)    |
| DELETE | /api/users/{id}  | ADMIN role    | Delete a user                            |
| GET    | /api/greeting    | public        | Returns the configured greeting          |

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
# Register a user
curl -X POST http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","name":"Alice","password":"supersecret"}'

# List users (auth required)
curl -u alice@example.com:supersecret http://localhost:8080/api/users

# Greeting (public)
curl http://localhost:8080/api/greeting

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

Unit and slice tests run in milliseconds. Integration tests spin up real Postgres via Testcontainers (needs Docker running).

## Deploying

The fastest path to "running on the internet":

```bash
# Fly.io (recommended for first deployment)
brew install flyctl
fly launch        # reads the Dockerfile, sets up Postgres, gets you a URL
fly deploy
```
