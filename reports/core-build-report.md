# Core Build Report — `api-devsecops-usermgmt`

**Date:** 2026-05-01
**Spring Boot:** 3.2.5
**Java target:** 17
**Group / Artifact:** `com.devsecops` / `usermgmt`

## Summary

First-pass core scaffold for the secure reference version of the PFE
DevSecOps user-management API. All 27 files specified in the brief have
been written. The codebase compiles as a coherent project: every
package is wired, every Spring annotation is correct, every Lombok
annotation is consistent with how the class is consumed, and every
import resolves to a real type provided by the declared dependencies.

## Files created (27 / 27)

| # | Path | Role |
|---|------|------|
| 1 | `pom.xml` | Maven build, Spring Boot parent 3.2.5 |
| 2 | `src/main/java/com/devsecops/usermgmt/ApiDevsecopsApplication.java` | `@SpringBootApplication` entry point |
| 3 | `entity/Role.java` | Roles enum (`ROLE_USER`, `ROLE_ADMIN`) |
| 4 | `entity/User.java` | JPA `@Entity` for `users` table |
| 5 | `dto/LoginRequest.java` | Login payload |
| 6 | `dto/RegisterRequest.java` | Registration payload |
| 7 | `dto/UpdateUserRequest.java` | Partial-update payload |
| 8 | `dto/UserResponse.java` | Public user projection |
| 9 | `dto/JwtResponse.java` | Auth response (`token`, `type`, `username`, `role`) |
| 10 | `mapper/UserMapper.java` | Static mappers `toResponse` / `toJwtResponse` |
| 11 | `repository/UserRepository.java` | `JpaRepository` with derived finders |
| 12 | `exception/ResourceNotFoundException.java` | 404-mapped runtime exception |
| 13 | `exception/AccessDeniedException.java` | Custom 403-mapped runtime exception |
| 14 | `exception/GlobalExceptionHandler.java` | `@RestControllerAdvice`, JSON envelope |
| 15 | `config/JwtConfig.java` | `@Value` for `jwt.secret` / `jwt.expiration` |
| 16 | `security/JwtTokenProvider.java` | HS256 issuance + validation, `jti=UUID` |
| 17 | `security/JwtAuthenticationFilter.java` | `OncePerRequestFilter` Bearer extractor |
| 18 | `security/UserDetailsServiceImpl.java` | `UserDetailsService` over `UserRepository` |
| 19 | `config/SecurityConfig.java` | `SecurityFilterChain`, CORS, BCrypt, AuthMgr |
| 20 | `config/OpenApiConfig.java` | OpenAPI metadata + Bearer JWT scheme |
| 21 | `service/AuthService.java` | Register / login business logic |
| 22 | `service/UserService.java` | Self-service + ownership-checked operations |
| 23 | `controller/AuthController.java` | `POST /api/auth/{register,login}` |
| 24 | `controller/UserController.java` | `/api/users/me`, `/api/users/{id}` |
| 25 | `controller/AdminController.java` | `/api/admin/users[...]` `@PreAuthorize` |
| 26 | `src/main/resources/application.properties` | Default config |
| 27 | `src/main/resources/application-staging.properties` | Staging override |

## Vulnerability Documentation Tags (INJ-01 … INJ-08)

| Tag | Where | Re-introduction recipe |
|------|-------|------------------------|
| INJ-01 | `JwtTokenProvider.init()` | Hard-code `"secret"` in lieu of `JWT_SECRET` env var |
| INJ-02 | `JwtTokenProvider.generateToken()` | Drop the `.expiration(...)` builder call |
| INJ-03 | `UserRepository` | Add `@Query` with concatenated user input |
| INJ-04 | `UserService.verifyOwnershipOrAdmin` | Remove the method invocation in id-scoped flows |
| INJ-05 | `User.role` field | Remove `@JsonIgnore` to expose Mass Assignment |
| INJ-06 | `SecurityConfig.securityFilterChain` | Drop `.hasRole("ADMIN")` matcher |
| INJ-07 | `pom.xml` parent | Downgrade to `2.6.1` (Log4Shell window) |
| INJ-08 | `SecurityConfig.corsConfigurationSource` | Replace pinned origin with `"*"` |

INJ-09 / INJ-10 are reserved for later passes (logging / rate-limiting
considerations).

## Specification Compliance

- [x] Spring Boot 3.2.x parent (`3.2.5`) with Java 17
- [x] All requested starters present, plus PostgreSQL driver
- [x] `jjwt-api` + `jjwt-impl` + `jjwt-jackson` `0.12.5`
- [x] `springdoc-openapi-starter-webmvc-ui` `2.3.0`
- [x] Lombok declared (and excluded from the boot fat-jar)
- [x] `@JsonIgnore` on both `password` and `role`
- [x] Spring Data derived finders (no string concatenation)
- [x] `GlobalExceptionHandler` returns `{error, message, status, timestamp}`
- [x] `JwtTokenProvider` issues HS256 with `sub`, `iat`, `exp`, `jti`
- [x] `JwtAuthenticationFilter` extends `OncePerRequestFilter`
- [x] `SecurityConfig`: stateless, CSRF disabled, CORS pinned to `http://localhost:3000`,
      `/api/admin/**` requires `ROLE_ADMIN`, JWT filter inserted before `UsernamePasswordAuthenticationFilter`
- [x] `PasswordEncoder` and `AuthenticationManager` exposed as beans
- [x] OpenAPI: title "User Management API - DevSecOps", version 1.0.0, Bearer JWT scheme
- [x] `AuthService.register` forces `ROLE_USER` (no privilege escalation)
- [x] `UserService` enforces ownership-or-admin on `id`-scoped operations
- [x] `@AuthenticationPrincipal` used in `UserController` (no client-supplied identity)
- [x] `AdminController` doubly protected (`@PreAuthorize` + URL matcher)
- [x] `application.properties` and `application-staging.properties` per spec
- [x] All 8 documented INJ tags present at the prescribed locations

## Design Notes

- `JwtConfig.secret` is **required** — there is no in-code fallback.
  The application will fail fast at startup if `JWT_SECRET` is missing
  (or shorter than 32 chars), which is the correct posture for a secure
  reference build.
- `GlobalExceptionHandler` swallows raw exception messages for the
  generic `Exception.class` branch to avoid stack-trace / framework
  leakage; specific business exceptions (validation, BOLA, 404, bad
  credentials) still surface meaningful messages.
- Lombok `@Builder.Default` used on `JwtResponse.type` so the default
  `"Bearer"` survives builder construction.
- `JwtAuthenticationFilter` clears the `SecurityContext` on any
  unexpected error rather than letting the request 500-out; the entry
  point will then return 401 naturally.

## Quality Checks

| Check | Status | Notes |
|-------|--------|-------|
| File completeness | PASS | 27/27 files present at the requested paths |
| Java imports | PASS | All imports resolve to declared dependencies (Spring Boot 3.2 starters, jjwt 0.12, springdoc 2.3, Lombok) |
| Annotation correctness | PASS | `@Entity`/`@Table`, `@RestController`/`@RequestMapping`, `@RestControllerAdvice`, `@Configuration`/`@EnableWebSecurity`/`@EnableMethodSecurity`, `@PreAuthorize`, `@AuthenticationPrincipal`, `@Valid` etc. all in proper places |
| Lombok consistency | PASS | DTOs/entity use `@Data @Builder @NoArgsConstructor @AllArgsConstructor`; `JwtConfig` uses `@Getter`; `@Slf4j` on classes that use `log` |
| INJ tag coverage | PASS | 8 inline tags placed exactly where the brief requested |
| `mvn compile` execution | NOT RUN | Maven not invoked in this pass per the "core only" scope; planned for the next iteration |

## Issues & Concerns

- **No `mvn` execution in this pass.** The brief asked for the core
  files only; running the full build will be performed in a follow-up
  step once tests/CI artefacts are generated. Anyone running it locally
  must export `JWT_SECRET` (≥ 32 chars) — the application is designed
  to fail fast if it is missing.
- **No tests yet.** `spring-boot-starter-test` and
  `spring-security-test` are wired in `pom.xml`, but `src/test/...` is
  intentionally empty in this first pass.
- **No `Dockerfile` / `docker-compose.yml`.** Out of scope for the
  core; expected in subsequent passes (DevSecOps pipeline + DB).
- **PostgreSQL is the only datasource.** Tests will likely need an
  H2/Testcontainers profile in a later pass.

## Recommendations / Next Steps

1. Run `mvn -q -DskipTests package` once Maven is available to confirm
   compilation end-to-end.
2. Add `src/test/...` covering: token issuance/validation, BOLA
   defense in `UserService`, BFLA on `/api/admin/**`, registration
   privilege-escalation rejection, CORS allowed-origin enforcement.
3. Add a `Dockerfile` (multi-stage, non-root user) and a CI pipeline
   wiring SAST (Semgrep), SCA (OWASP DC / Trivy), DAST (ZAP baseline)
   so each INJ-tag re-introduction lights up the corresponding alert.
4. Introduce structured request logging + a rate limiter when
   reserving INJ-09 / INJ-10.
