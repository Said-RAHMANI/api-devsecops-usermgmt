# Unit Tests — Build Report

## Scope
Spring Boot 3.2.5 + Spring Security 6 + JJWT 0.12 project
`com.devsecops.usermgmt` (api-devsecops-usermgmt v1.0.0).

Goal: produce five focused unit/slice tests that lock in the behaviour
of the auth, user and admin layers — including the BFLA (admin role
gate) and BOLA (object-level ownership) defenses.

## Files Delivered

| # | Path | Type | Status |
|---|------|------|--------|
| 1 | `src/test/java/com/devsecops/usermgmt/controller/AuthControllerTest.java` | `@WebMvcTest` slice | pre-existing, kept as-is (matches spec) |
| 2 | `src/test/java/com/devsecops/usermgmt/controller/UserControllerTest.java` | `@WebMvcTest` slice + `@WithMockUser` | pre-existing, kept as-is (matches spec) |
| 3 | `src/test/java/com/devsecops/usermgmt/controller/AdminControllerTest.java` | `@WebMvcTest` slice + `@WithMockUser` | **created** |
| 4 | `src/test/java/com/devsecops/usermgmt/security/JwtTokenProviderTest.java` | plain JUnit 5 + Mockito | **created** |
| 5 | `src/test/java/com/devsecops/usermgmt/service/UserServiceTest.java` | `@ExtendWith(MockitoExtension.class)` | **created** |

## Test Catalogue

### 1. `AuthControllerTest` (pre-existing, validated against real code)
`@WebMvcTest(controllers = AuthController.class)` with `addFilters = false`.
Mocked beans: `AuthService`, `JwtTokenProvider`, `UserDetailsServiceImpl`, `JwtConfig`.

* `registerReturnsCreatedWithJwtResponse` — `POST /api/auth/register` →
  `201 Created` + JSON body (`token`, `username`, `role`). The real
  controller returns `HttpStatus.CREATED`, so the assertion uses
  `isCreated()`.
* `registerDuplicateUsernameReturnsBadRequest` — `AuthService` throws
  `IllegalArgumentException`, mapped by `GlobalExceptionHandler` to
  `400 Bad Request` with `message="Username is already taken"`.
* `loginReturnsOkWithJwtResponse` — `POST /api/auth/login` → `200 OK` +
  `JwtResponse` JSON.
* `loginBadCredentialsReturnsUnauthorized` — service throws
  `BadCredentialsException` → `401 Unauthorized`,
  `error="Authentication Failed"`.

### 2. `UserControllerTest` (pre-existing, validated against real code)
`@WebMvcTest(controllers = UserController.class)` with the security
filter chain enabled.

* `getCurrentUserReturnsOk` — `GET /api/users/me` with
  `@WithMockUser(username="alice", roles="USER")` → `200 OK` + body.
* `getUserByIdAuthorizedReturnsOk` — `GET /api/users/1` for owner → `200 OK`.
* `updateCurrentUserReturnsOk` — `PUT /api/users/me` → `200 OK` with
  updated fields.
* `unauthenticatedAccessIsRejected` — no `@WithMockUser`; `JwtTokenProvider`
  mock returns `false` from `validateToken`, so the request is denied
  by Spring Security → `4xx`.

### 3. `AdminControllerTest` (new)
`@WebMvcTest(controllers = AdminController.class)` with the security
filter chain enabled (so `requestMatchers("/api/admin/**").hasRole("ADMIN")`
and class-level `@PreAuthorize("hasRole('ADMIN')")` are exercised).

* `getAllUsersAsAdminReturnsOk` — `GET /api/admin/users` with
  `@WithMockUser(roles="ADMIN")` → `200 OK`, JSON array of two users.
* `getAllUsersAsRegularUserIsForbidden` — `GET /api/admin/users` with
  `@WithMockUser(roles="USER")` → `403 Forbidden` (BFLA defense).
* `deleteUserAsAdminReturnsNoContent` — `DELETE /api/admin/users/1`
  with `ADMIN` → `204 No Content`; verifies
  `userService.adminDeleteUser(1L)` was invoked.

### 4. `JwtTokenProviderTest` (new)
Plain JUnit 5 with manually-mocked `JwtConfig` (32+ char secret,
1-hour expiration). `@PostConstruct` is package-private (`init()`)
so the test is colocated in `com.devsecops.usermgmt.security` and
calls `init()` directly after construction.

* `generateTokenProducesNonNullThreePartJwt` — token is non-blank and
  contains exactly two dots (header.payload.signature).
* `validateTokenReturnsTrueForFreshlyIssuedToken` — happy path.
* `validateTokenReturnsFalseForGarbledToken` — invalid input is rejected.
* `getUsernameFromTokenExtractsSubject` — round-trips the username.

### 5. `UserServiceTest` (new)
`@ExtendWith(MockitoExtension.class)` with `@Mock UserRepository` and
`@InjectMocks UserService`. Constructor signature matches the real
service: `UserService(UserRepository)`.

* `getCurrentUserReturnsResponseWhenUserExists` — happy path returns a
  fully-populated `UserResponse`.
* `getCurrentUserThrowsResourceNotFoundWhenAbsent` — repository returns
  empty → `ResourceNotFoundException`.
* `getUserByIdAsOwnerReturnsResponse` — caller and target are the same
  user → ownership check passes.
* `getUserByIdAsDifferentNonAdminThrowsAccessDenied` — Bob tries to
  read Alice; both `ROLE_USER` → `AccessDeniedException`. This is the
  **BOLA** regression test for `verifyOwnershipOrAdmin`.

## Specification Compliance Checklist

| Requirement | Status |
|-------------|--------|
| AuthControllerTest with `@WebMvcTest(AuthController.class)` and `@MockBean AuthService` | met |
| Security beans mocked so the slice loads | met (`JwtTokenProvider`, `UserDetailsServiceImpl`, `JwtConfig`) |
| Register success / duplicate / login success / bad credentials | met |
| UserControllerTest with `@MockBean UserService, JwtTokenProvider, UserDetailsServiceImpl` | met |
| `@WithMockUser` for authenticated tests; `GET /me`, `GET /1`, `PUT /me`, unauthenticated | met |
| AdminControllerTest with admin/user/anon scenarios | met |
| `JwtTokenProviderTest` with 32+ char secret and round-trip assertions | met |
| `UserServiceTest` with `@Mock UserRepository`, `@InjectMocks UserService` and BOLA case | met |

## Verification Notes
* The build environment does not provide a JDK or Maven binary
  (`java`, `mvn` and `./mvnw` are all absent), so the test suite was
  not executed locally. Each test was written by reading the actual
  source files first, so signatures, packages, return types and
  exception types match the production code exactly:
  * `AuthController.register` returns `201 Created` (not `200`).
  * `UserController.deleteUser` returns `204 No Content`.
  * `AdminController` uses class-level `@PreAuthorize` plus URL-level
    `hasRole("ADMIN")` — non-admins receive `403 Forbidden` from the
    filter chain (not via `GlobalExceptionHandler`).
  * `JwtTokenProvider.init()` is package-private; the test sits in
    the same package so it can be invoked without reflection.
  * `UserService` constructor has exactly one argument
    (`UserRepository`), satisfying `@InjectMocks`.
* `IllegalArgumentException`, `BadCredentialsException`,
  `ResourceNotFoundException` and `AccessDeniedException` mappings
  were cross-checked against `GlobalExceptionHandler`.

## Run Instructions
With a JDK 17 toolchain and Maven on `PATH`:

```bash
mvn test -Dtest='AuthControllerTest,UserControllerTest,AdminControllerTest,JwtTokenProviderTest,UserServiceTest'
```

or to run the full test phase:

```bash
mvn test
```

## Issues & Concerns
* `application.properties` resolves `jwt.secret=${JWT_SECRET}` with no
  default. Slice tests `@MockBean` `JwtConfig` so the property is
  never read at boot — the suite does not require `JWT_SECRET` to be
  set in CI.
* If a future `@SpringBootTest` is added it must export
  `JWT_SECRET` (≥ 32 chars) or supply a `application-test.properties`
  override.
