## devsecops-usermgmt-project

Built complete Spring Boot 3.2.x User Management API "api-devsecops-usermgmt" for PFE DevSecOps. Stack: Java 17, Spring Security 6 + JWT HS256, PostgreSQL 15, Spring Data JPA, OpenAPI 3.0, Maven, Docker multi-stage Alpine. 

Delivered in 3 agent passes (single agent failed twice due to size):
1. Core builder: 27 Java files (entities, DTOs, repos, services, controllers, security, config, mapper, exceptions)
2. Docker/docs builder: Dockerfile, docker-compose.yml, README.md, VULNERABILITIES.md
3. Tests builder: 5 test files (15 tests total) - AuthController, UserController, AdminController, JwtTokenProvider, UserService
4. Pipeline builder: .github/workflows/devsecops-pipeline.yml (6 jobs: setup→sast→sca→build→dast→reporting)

10 vulnerabilities documented (INJ-01 to INJ-10) as comments in code for injection protocol. Secure reference version is default. User communicates in French.