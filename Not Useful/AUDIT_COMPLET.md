# AUDIT COMPLET DU PROJET — `api-devsecops-usermgmt`

> Document généré par exploration exhaustive du dépôt présent dans `C:\Users\acer\Downloads\Projet pfe\repo`.
> Toutes les affirmations ci-dessous sont sourcées par un chemin de fichier et, lorsque c'est utile, un numéro de ligne. Aucune supposition n'a été faite sur du code non présent dans le dépôt.

---

# PHASE 1 — ANALYSE D'ARCHITECTURE

## 1.1 Vue d'ensemble

Le projet est une **API REST Spring Boot 3.2.5 / Java 21** appelée `api-devsecops-usermgmt`, qui implémente un module de **gestion d'utilisateurs sécurisé** (inscription, authentification JWT, rôles, administration). Il sert de **support pédagogique pour un PFE DevSecOps** : le code est volontairement écrit dans un état "sécurisé de référence", avec un protocole documenté ([VULNERABILITIES.md](VULNERABILITIES.md)) permettant de réintroduire 10 vulnérabilités ciblées (tags `INJ-01` à `INJ-10` directement visibles en commentaires dans le code) afin de tester une chaîne d'outils SAST/DAST/SCA.

Le dépôt contient également toute l'infrastructure **DevSecOps Cloud Native** associée : conteneurisation Docker, orchestration Kubernetes, GitOps ArgoCD, supervision Prometheus/Grafana, et un pipeline CI/CD GitHub Actions.

## 1.2 Arborescence complète du dépôt

```
repo/
├── .github/
│   ├── workflows/devsecops-pipeline.yml      # Pipeline CI/CD GitHub Actions
│   └── modernize/java-upgrade/...            # Traces d'un outil de migration Java (générées automatiquement)
├── argocd/
│   └── application.yaml                       # Application ArgoCD (GitOps)
├── k8s/
│   ├── 00-namespace.yaml                      # Namespace "usermgmt"
│   ├── postgres-deployment.yaml               # ConfigMap+Secret+PVC+StatefulSet PostgreSQL
│   ├── api-deployment.yaml                    # ConfigMap+Secret+Deployment API
│   ├── services.yaml                          # Services ClusterIP / NodePort
│   └── network-policies.yaml                  # NetworkPolicies (default-deny + règles ciblées)
├── monitoring/
│   ├── prometheus-config.yaml                 # ConfigMap de scrape config (statique)
│   ├── grafana-dashboard.json                 # Dashboard Grafana (JSON)
│   └── api-servicemonitor.yaml                # ServiceMonitor (Prometheus Operator)
├── logs/                                      # Logs applicatifs générés à l'exécution (JSON, gzip)
├── reports/                                   # Rapports générés par des outils externes (build, docker, tests, pipeline)
├── src/
│   ├── main/java/com/devsecops/usermgmt/
│   │   ├── config/        → SecurityConfig, JwtConfig, OpenApiConfig, RateLimitProperties
│   │   ├── controller/    → AuthController, UserController, AdminController, EmailVerificationController
│   │   ├── dto/           → 8 DTO (Request/Response)
│   │   ├── entity/        → User, Role, RefreshToken, EmailVerificationToken
│   │   ├── exception/     → GlobalExceptionHandler, AccessDeniedException, ResourceNotFoundException
│   │   ├── filter/        → RateLimitingFilter, RequestLoggingFilter
│   │   ├── mapper/        → UserMapper
│   │   ├── repository/    → UserRepository, RefreshTokenRepository, EmailVerificationTokenRepository
│   │   ├── security/      → JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl
│   │   ├── service/       → AuthService, UserService, RefreshTokenService, EmailVerificationService
│   │   └── ApiDevsecopsApplication.java       # Point d'entrée Spring Boot
│   ├── main/resources/
│   │   ├── application.properties             # Config principale (active)
│   │   ├── application-staging.properties     # Profil "staging" (overrides)
│   │   └── logback-spring.xml                 # Config de logs JSON structurés (ELK)
│   └── test/java/com/devsecops/usermgmt/
│       ├── controller/        → AuthControllerTest, UserControllerTest, AdminControllerTest (slice tests)
│       ├── filter/            → RateLimitingFilterTest (unitaire)
│       ├── integration/       → AbstractIntegrationTest + 4 suites Testcontainers
│       ├── security/          → JwtTokenProviderTest (unitaire)
│       └── service/           → UserServiceTest (unitaire Mockito)
├── Dockerfile                                  # Build multi-stage (Maven → JRE Alpine)
├── docker-compose.yml                          # Orchestration locale (PostgreSQL + API)
├── pom.xml                                     # Dépendances et configuration Maven
├── README.md                                   # Documentation utilisateur
├── VULNERABILITIES.md                          # Protocole d'injection de vulnérabilités (10 INJ)
└── MEMORY.md                                   # Mémoire d'agent (hors périmètre applicatif)
```

## 1.3 Pile technologique (telle que déclarée dans `pom.xml`)

| Catégorie | Technologie | Version | Élément de preuve |
|---|---|---|---|
| Langage | Java | 21 | [pom.xml:23](pom.xml#L23), `<release>${java.version}</release>` au [pom.xml:155](pom.xml#L155) |
| Framework | Spring Boot (starter-parent) | 3.2.5 | [pom.xml:12](pom.xml#L12) |
| Web | spring-boot-starter-web | (héritée du parent) | [pom.xml:32](pom.xml#L32) |
| Sécurité | spring-boot-starter-security | (héritée) | [pom.xml:49](pom.xml#L49) |
| Persistance | spring-boot-starter-data-jpa + PostgreSQL driver | (héritée) | [pom.xml:55](pom.xml#L55), [pom.xml:67](pom.xml#L67) |
| Validation | spring-boot-starter-validation (Jakarta Bean Validation) | (héritée) | [pom.xml:61](pom.xml#L61) |
| JWT | io.jsonwebtoken (jjwt-api / impl / jackson) | 0.12.5 | [pom.xml:24](pom.xml#L24), [pom.xml:73-88](pom.xml#L73) |
| Documentation API | springdoc-openapi-starter-webmvc-ui | 2.3.0 | [pom.xml:25](pom.xml#L25), [pom.xml:93-95](pom.xml#L93) |
| Rate limiting | com.bucket4j:bucket4j-core | 8.10.1 | [pom.xml:107-109](pom.xml#L107) |
| Logs structurés | net.logstash.logback:logstash-logback-encoder | 7.4 | [pom.xml:113-116](pom.xml#L113) |
| Observabilité | spring-boot-starter-actuator + micrometer-registry-prometheus | (héritée / runtime) | [pom.xml:38](pom.xml#L38), [pom.xml:42](pom.xml#L42) |
| Génération de code | Lombok | (héritée, optional) | [pom.xml:100](pom.xml#L100) |
| Tests | spring-boot-starter-test, spring-security-test, Testcontainers (junit-jupiter + postgresql) | (héritées) | [pom.xml:121-145](pom.xml#L121) |
| Compilateur | maven-compiler-plugin | 3.11.0 | [pom.xml:153](pom.xml#L153) |
| Tests unitaires | maven-surefire-plugin (exclut `*IntegrationTest`) | 3.1.2 | [pom.xml:161-167](pom.xml#L161) |
| Conteneurisation | Docker multi-stage (`maven:3.9-eclipse-temurin-21-alpine` → `eclipse-temurin:21-jre-alpine`) | 21 | [Dockerfile:5](Dockerfile#L5), [Dockerfile:12](Dockerfile#L12) |
| Orchestration locale | Docker Compose v3.9 | — | [docker-compose.yml:1](docker-compose.yml#L1) |
| Orchestration cluster | Kubernetes (manifests `k8s/`) | — | [k8s/](k8s/) |
| GitOps | ArgoCD (`Application` CRD) | — | [argocd/application.yaml](argocd/application.yaml) |
| Supervision | Prometheus Operator (ServiceMonitor) + Grafana (dashboard JSON) | — | [monitoring/](monitoring/) |
| CI/CD | GitHub Actions (build, SonarCloud, Trivy, OWASP ZAP) | — | [.github/workflows/devsecops-pipeline.yml](.github/workflows/devsecops-pipeline.yml) |

> Remarque : le `README.md` ([README.md:13](README.md#L13)) annonce encore "Java 17" comme prérequis minimal — **incohérence avec `pom.xml` qui exige désormais Java 21** (voir [LISTE_DES_BUGS.md](LISTE_DES_BUGS.md), bug n°1).

## 1.4 Dépendances Maven (vue détaillée)

Voir tableau ci-dessus + [pom.xml:28-145](pom.xml#L28). Aucune dépendance "cachée" ou non déclarée n'a été trouvée : toutes les classes importées dans `src/main/java` correspondent à une dépendance présente dans le POM.

Une remarque de configuration mérite d'être relevée : le commentaire `<!-- INJ-07: To inject, downgrade to spring-boot-starter-parent 2.6.1 for Log4Shell CVE-2021-44228 -->` au [pom.xml:8](pom.xml#L8) documente directement, dans le fichier de build réel, comment réintroduire une vulnérabilité connue (Log4Shell). C'est volontaire et cohérent avec l'objectif pédagogique du projet (cf. [VULNERABILITIES.md](VULNERABILITIES.md)).

## 1.5 Configuration et variables d'environnement

### `application.properties` (profil par défaut / production) — [src/main/resources/application.properties](src/main/resources/application.properties)

| Clé | Valeur | Rôle |
|---|---|---|
| `spring.application.name` | `api-devsecops-usermgmt` | Identifiant utilisé dans les logs JSON et les métriques Prometheus |
| `rate-limit.login.*` | capacity=5, refill=5/60s | Limite anti-bruteforce sur `/api/auth/login` |
| `rate-limit.register.*` | capacity=3, refill=3/60s | Limite anti-abus sur `/api/auth/register` |
| `rate-limit.forgot-password.*` | capacity=3, refill=3/60s | Limite prévue pour un futur endpoint `/api/auth/forgot-password` (non implémenté, voir §1.7) |
| `jwt.expiration` | `900000` (15 min) | Durée de vie du token d'accès JWT |
| `jwt.refresh-token-expiration` | `604800000` (7 jours) | Durée de vie du refresh token |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/usermgmt` | URL de connexion PostgreSQL (surchargée par `SPRING_DATASOURCE_URL` en conteneur) |
| `spring.datasource.username` / `.password` | `${DB_USERNAME:postgres}` / `${DB_PASSWORD:postgres}` | Identifiants externalisés via variables d'environnement, avec valeur par défaut `postgres` |
| `spring.jpa.hibernate.ddl-auto` | `update` | Le schéma de base est généré/maintenu automatiquement par Hibernate (voir audit qualité §2.3) |
| `jwt.secret` | `${JWT_SECRET}` | Secret de signature JWT, **strictement externalisé**, sans valeur par défaut |
| `server.port` | `8080` | Port d'écoute HTTP |
| `springdoc.*` | `/v3/api-docs`, `/swagger-ui.html` | Chemins de la documentation OpenAPI |
| `management.endpoints.web.exposure.include` | `health,prometheus,info` | Endpoints Actuator exposés (sécurité par minimisation de surface) |
| `management.endpoint.health.show-details` | `when-authorized` | Détails de santé masqués aux appels anonymes |
| `management.prometheus.metrics.export.enabled` | `true` | Active l'export `/actuator/prometheus` consommé par le ServiceMonitor |

### `application-staging.properties` — [src/main/resources/application-staging.properties](src/main/resources/application-staging.properties)

```properties
spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=validate
```
Ce profil bascule `ddl-auto` en mode `validate` (le schéma n'est plus modifié automatiquement, seulement vérifié) et active l'affichage des requêtes SQL — typique d'un environnement de pré-production où l'on veut figer le schéma et observer les requêtes générées.

### Variables d'environnement attendues (croisées entre `README.md`, `docker-compose.yml`, `Dockerfile`, `k8s/`)

| Variable | Origine / valeur par défaut | Utilisée par |
|---|---|---|
| `DB_USERNAME` | `postgres` (défaut) | `application.properties`, `docker-compose.yml`, `k8s/postgres-deployment.yaml`, `k8s/api-deployment.yaml` |
| `DB_PASSWORD` | `postgres` (défaut, **placeholder à changer**) | idem |
| `JWT_SECRET` | aucune valeur par défaut côté Spring (`${JWT_SECRET}` sans fallback) — README propose `cHJvamVjdC1kZXZzZWNvcHMtcGZlLXNlY3JldC1rZXktMjAyNA==` | `application.properties` ([ligne 29](src/main/resources/application.properties#L29)), `docker-compose.yml` ([ligne 36](docker-compose.yml#L36)), `k8s/api-deployment.yaml` (Secret) |
| `SPRING_DATASOURCE_URL` | construite (`jdbc:postgresql://postgres:5432/usermgmt`) | `docker-compose.yml`, `k8s/api-deployment.yaml` (ConfigMap) |
| `SPRING_PROFILES_ACTIVE` | `prod` (en K8s) | `k8s/api-deployment.yaml` (ConfigMap) — **remarque : aucun fichier `application-prod.properties` n'existe dans le dépôt** (voir §2.3 et bugs) |
| `SERVER_PORT` | `8080` | `k8s/api-deployment.yaml` (ConfigMap) |

## 1.6 Base de données

- **SGBD** : PostgreSQL 15 (image `postgres:15-alpine`), partout identique entre Compose, Kubernetes et les tests Testcontainers.
- **Schéma** : généré automatiquement par Hibernate (`ddl-auto=update`), à partir des entités JPA :
  - `users` ([User.java](src/main/java/com/devsecops/usermgmt/entity/User.java)) — id, username (unique), email (unique), password (haché BCrypt), role (enum), email_verified, created_at, updated_at
  - `refresh_tokens` ([RefreshToken.java](src/main/java/com/devsecops/usermgmt/entity/RefreshToken.java)) — relation `@ManyToOne` vers `User`, token unique, expires_at
  - `email_verification_tokens` ([EmailVerificationToken.java](src/main/java/com/devsecops/usermgmt/entity/EmailVerificationToken.java)) — relation `@OneToOne` vers `User`, token unique, expires_at, used
- **Accès aux données** : exclusivement via Spring Data JPA — `UserRepository`, `RefreshTokenRepository`, `EmailVerificationTokenRepository` ([repository/](src/main/java/com/devsecops/usermgmt/repository/)). Toutes les requêtes personnalisées utilisent des paramètres liés (`:param` / JPQL avec `@Param`/`@Query` paramétré), aucune concaténation de chaînes n'a été trouvée dans le code source réel (le seul exemple de concaténation figure en commentaire pédagogique pour illustrer INJ-03, [UserRepository.java:15](src/main/java/com/devsecops/usermgmt/repository/UserRepository.java#L15)).

## 1.7 API REST — endpoints réellement implémentés

| Méthode | Chemin | Contrôleur | Authentification | Élément de preuve |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | AuthController | Public | [AuthController.java:37-40](src/main/java/com/devsecops/usermgmt/controller/AuthController.java#L37) |
| `POST` | `/api/auth/login` | AuthController | Public | [AuthController.java:43-46](src/main/java/com/devsecops/usermgmt/controller/AuthController.java#L43) |
| `POST` | `/api/auth/refresh` | AuthController | Public (le refresh token fait office de preuve) | [AuthController.java:49-53](src/main/java/com/devsecops/usermgmt/controller/AuthController.java#L49) |
| `POST` | `/api/auth/logout` | AuthController | Authentifié | [AuthController.java:57-63](src/main/java/com/devsecops/usermgmt/controller/AuthController.java#L57) |
| `GET` | `/api/auth/verify?token=` | EmailVerificationController | Public | [EmailVerificationController.java:37-42](src/main/java/com/devsecops/usermgmt/controller/EmailVerificationController.java#L37) |
| `GET` | `/api/users/me` | UserController | Authentifié | [UserController.java:40-44](src/main/java/com/devsecops/usermgmt/controller/UserController.java#L40) |
| `PUT` | `/api/users/me` | UserController | Authentifié | [UserController.java:47-53](src/main/java/com/devsecops/usermgmt/controller/UserController.java#L47) |
| `GET` | `/api/users/{id}` | UserController | Authentifié + propriétaire ou admin | [UserController.java:56-61](src/main/java/com/devsecops/usermgmt/controller/UserController.java#L56) |
| `PUT` | `/api/users/{id}` | UserController | Authentifié + propriétaire ou admin | [UserController.java:64-71](src/main/java/com/devsecops/usermgmt/controller/UserController.java#L64) |
| `DELETE` | `/api/users/{id}` | UserController | Authentifié + propriétaire ou admin | [UserController.java:74-80](src/main/java/com/devsecops/usermgmt/controller/UserController.java#L74) |
| `GET` | `/api/admin/users?page&size&sort` | AdminController | `ROLE_ADMIN` (URL + `@PreAuthorize`) | [AdminController.java:56-62](src/main/java/com/devsecops/usermgmt/controller/AdminController.java#L56) |
| `DELETE` | `/api/admin/users/{id}` | AdminController | `ROLE_ADMIN` | [AdminController.java:65-69](src/main/java/com/devsecops/usermgmt/controller/AdminController.java#L65) |
| `GET` | `/actuator/health`, `/actuator/prometheus` | Spring Boot Actuator | Public (ouvert pour les sondes K8s/Prometheus) | [SecurityConfig.java:53-54](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L53) |
| `GET` | `/swagger-ui.html`, `/v3/api-docs/**` | springdoc-openapi | Public | [SecurityConfig.java:56-58](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L56) |

> ⚠️ **Écart README ↔ code** : le `README.md` ([lignes 91-98](README.md#L91)) documente un endpoint `GET /health` distinct ; ce chemin est bien autorisé dans `SecurityConfig` ([ligne 52](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L52)) mais **aucun contrôleur ne le définit** dans `src/main/java`. Le seul "santé" réellement exposé par le code applicatif est `/actuator/health` (Spring Boot Actuator). Le `Dockerfile` ([ligne 23](Dockerfile#L23)) et `docker-compose.yml` ([ligne 41](docker-compose.yml#L41)) pointent eux aussi vers `/health`, qui n'existe donc dans aucun contrôleur connu (voir [LISTE_DES_BUGS.md](LISTE_DES_BUGS.md)).
>
> ⚠️ Le rate-limiter référence un chemin `/api/auth/forgot-password` ([RateLimitingFilter.java:44](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java#L44) et `application.properties` lignes 12-14) **qui ne correspond à aucun endpoint implémenté** — la fonctionnalité "mot de passe oublié" n'existe pas dans `AuthController`.

## 1.8 Mécanismes d'authentification et d'autorisation

1. **Authentification** : JWT signé HS256 (clé ≥ 32 caractères, vérifiée au démarrage — [JwtTokenProvider.java:46-49](src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java#L46)). Les identifiants sont vérifiés par l'`AuthenticationManager` de Spring Security (BCrypt via `PasswordEncoder`).
2. **Filtre JWT** ([JwtAuthenticationFilter.java](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java)) : extrait le `Bearer <token>`, le valide, et **peuple uniquement** le `SecurityContext` — il ne rejette jamais lui-même la requête (cf. commentaire explicite [lignes 51-56](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java#L51), résultat d'une correction appliquée durant cette mission, voir §2.2).
3. **Autorisation** : déclarative via `authorizeHttpRequests` dans `SecurityConfig` ([lignes 46-64](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L46)) — règles d'URL — complétée par `@PreAuthorize("hasRole('ADMIN')")` au niveau classe dans `AdminController` ([ligne 30](src/main/java/com/devsecops/usermgmt/controller/AdminController.java#L30)), donc **défense en profondeur** (deux mécanismes redondants protègent `/api/admin/**`).
4. **Vérification de propriété (anti-BOLA)** : `UserService.verifyOwnershipOrAdmin` ([UserService.java:135-143](src/main/java/com/devsecops/usermgmt/service/UserService.java#L135)) compare l'identité authentifiée (jamais une valeur fournie par le client) à l'utilisateur cible.
5. **Rotation des refresh tokens** : un seul refresh token actif par utilisateur ; chaque rafraîchissement supprime l'ancien et en crée un nouveau ([RefreshTokenService.java:84-89](src/main/java/com/devsecops/usermgmt/service/RefreshTokenService.java#L84)).
6. **Rate limiting** : filtre `RateLimitingFilter` basé sur Bucket4j (algorithme "token bucket"), appliqué uniquement aux routes sensibles `/api/auth/{login,register,forgot-password,refresh}`, par adresse IP ([RateLimitingFilter.java:59-66](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java#L59)).
7. **CORS** : restreint à une seule origine `http://localhost:3000` ([SecurityConfig.java:75](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L75)), avec `allowCredentials(true)`.
8. **CSRF** : désactivé ([SecurityConfig.java:43](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L43)) — choix cohérent pour une API JWT *stateless* sans session de navigateur (le CSRF cible les sessions basées sur cookies).

## 1.9 Frontend

**Aucun frontend n'est présent dans ce dépôt.** Le projet est strictement une API backend. La référence à `http://localhost:3000` dans la configuration CORS ([SecurityConfig.java:75](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L75)) suggère qu'un frontend (probablement React/Next.js, port par défaut 3000) est censé exister séparément et consommer cette API, mais aucun code, sous-module ou référence à un dépôt frontend n'a été trouvé dans l'arborescence explorée.

## 1.10 Conteneurisation et déploiement

### Docker
- **Dockerfile** ([Dockerfile](Dockerfile)) : build multi-stage —
  1. Étage `build` : `maven:3.9-eclipse-temurin-21-alpine`, compile le jar (`mvn package -DskipTests`)
  2. Étage final : `eclipse-temurin:21-jre-alpine`, utilisateur système non-root `appuser` créé via `addgroup -S && adduser -S` ([ligne 13](Dockerfile#L13)), dossier `/app/logs` pré-créé avec les bons droits, `HEALTHCHECK` sur `wget http://localhost:8080/health`.
- **docker-compose.yml** ([docker-compose.yml](docker-compose.yml)) : deux services — `postgres` (avec `healthcheck` `pg_isready`) et `api` (dépend de `postgres` en état "healthy"), réseau implicite Compose, volume nommé `pgdata`.

### Kubernetes (`k8s/`)
| Fichier | Contenu | Points notables |
|---|---|---|
| [00-namespace.yaml](k8s/00-namespace.yaml) | Namespace `usermgmt` | Isolation logique du cluster |
| [postgres-deployment.yaml](k8s/postgres-deployment.yaml) | ConfigMap, Secret, PVC (`storageClassName: standard`, 1Gi), StatefulSet `postgres:15-alpine` | `securityContext` non-root (uid/gid 70), probes `pg_isready` |
| [api-deployment.yaml](k8s/api-deployment.yaml) | ConfigMap, Secret, Deployment (2 réplicas) | `imagePullSecrets: harbor-registry-secret`, `securityContext` non-root (uid 100/gid 101), `readOnlyRootFilesystem: true` avec volumes `emptyDir` pour `/app/logs` et `/tmp`, probes **TCP** sur le port 8080 (voir §2.2 pour le contexte de cette décision), annotations `prometheus.io/*` |
| [services.yaml](k8s/services.yaml) | Service `postgres` (ClusterIP headless) + `api-service` (NodePort 8080→30080) | Alternative Ingress documentée en commentaire |
| [network-policies.yaml](k8s/network-policies.yaml) | `default-deny-ingress`, `postgres-allow-from-api-only`, `api-allow-ingress-and-egress` | Modèle "moindre privilège réseau" |

### GitOps (ArgoCD)
- [argocd/application.yaml](argocd/application.yaml) : `Application` ArgoCD pointant vers `https://github.com/Said-RAHMANI/api-devsecops-usermgmt.git`, chemin `k8s`, synchronisation automatisée (`prune: true`, `selfHeal: true`), `CreateNamespace=true`.

### Supervision (Prometheus / Grafana)
| Fichier | Rôle |
|---|---|
| [monitoring/prometheus-config.yaml](monitoring/prometheus-config.yaml) | ConfigMap de scrape config statique — **non branchée** sur la stack `kube-prometheus-stack` réellement déployée (qui utilise des CRD `ServiceMonitor`, pas des ConfigMaps statiques) |
| [monitoring/api-servicemonitor.yaml](monitoring/api-servicemonitor.yaml) | `ServiceMonitor` (CRD `monitoring.coreos.com/v1`) ciblant `api-service`/`usermgmt`/port `http`/chemin `/actuator/prometheus`, label `release: kube-prom` requis par le sélecteur de l'opérateur Prometheus |
| [monitoring/grafana-dashboard.json](monitoring/grafana-dashboard.json) | Dashboard Grafana avec panels CPU, mémoire JVM, débit/latence HTTP, threads JVM, statut des pods, taux d'erreurs 5xx, connexions Hikari |

## 1.11 CI/CD — pipeline GitHub Actions

[.github/workflows/devsecops-pipeline.yml](.github/workflows/devsecops-pipeline.yml) définit 3 jobs séquentiels :

1. **`build`** (Build & Security Scans) : checkout → setup JDK 21 → cache Maven → `mvn clean compile -DskipTests` → analyse **SonarCloud** (SAST) → build de l'image Docker → connexion et push vers **Harbor** (registre privé, conditionné à un événement `push`) → scan **Trivy** (SCA, sévérités HIGH/CRITICAL) → upload du rapport.
2. **`dast`** (DAST - OWASP ZAP) : démarre l'application via `docker compose up`, attend `/health`, exécute un scan baseline **OWASP ZAP**, télécharge le rapport, arrête les conteneurs.
3. **`reporting`** (Security Reporting) : agrège tous les rapports (`download-artifact`), génère un résumé Markdown dans `$GITHUB_STEP_SUMMARY`, republie l'ensemble en artefact final.

Déclencheurs : `push` sur `main`/`develop`, et `pull_request` vers `main` ([lignes 3-7](.github/workflows/devsecops-pipeline.yml#L3-L7)).

> ⚠️ Le job `dast` attend `curl http://localhost:8080/health` ([ligne 102](.github/workflows/devsecops-pipeline.yml#L102)) — chemin qui, comme indiqué en §1.7, **n'est mappé à aucun contrôleur** dans le code source actuel. Si l'`HEALTHCHECK` Docker échoue de façon similaire, le pipeline DAST risque de ne jamais détecter l'application comme "prête" (timeout après 30 tentatives × 5s = 150s).

## 1.12 Logs et observabilité

- **Format** : JSON structuré compatible ELK via `logstash-logback-encoder` ([logback-spring.xml](src/main/resources/logback-spring.xml)), avec champs `traceId`, `requestId`, `path`, `method`, `duration` injectés dans le MDC par `RequestLoggingFilter` ([RequestLoggingFilter.java](src/main/java/com/devsecops/usermgmt/filter/RequestLoggingFilter.java)).
- **Rotation** : fichiers journaliers compressés (`logs/application.<date>.log.gz`), rétention 30 jours / 500 Mo max ([logback-spring.xml:49-53](src/main/resources/logback-spring.xml#L49)). Le dépôt contient déjà des artefacts de logs réels (`logs/application.log`, deux archives `.gz`), preuve d'exécutions locales antérieures.
- **Métriques** : exposées via Actuator + Micrometer au format Prometheus (`/actuator/prometheus`), consommées par le `ServiceMonitor` et visualisées dans le dashboard Grafana fourni.
- **Profils de logs** : profil `dev` → sortie texte lisible ; tout autre profil → JSON structuré + fichier ([logback-spring.xml:64-84](src/main/resources/logback-spring.xml#L64)).

---

# PHASE 2 — AUDIT DE QUALITÉ

## 2.1 Bugs fonctionnels identifiés

Détail complet et exhaustif dans [LISTE_DES_BUGS.md](LISTE_DES_BUGS.md). Synthèse :

| # | Résumé | Sévérité | Statut |
|---|---|---|---|
| 1 | `README.md` annonce Java 17 alors que `pom.xml` impose Java 21 | Faible (documentation) | Présent |
| 2 | Endpoint `/health` référencé partout (README, Dockerfile, docker-compose, pipeline CI, `SecurityConfig`) mais **non implémenté** dans aucun contrôleur — seul `/actuator/health` existe réellement | Moyenne → impacte healthchecks Docker/K8s/CI | Présent |
| 3 | Rate limit `forgot-password` configuré ([application.properties:12-14](src/main/resources/application.properties#L12), [RateLimitingFilter.java:44](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java#L44)) pour un endpoint `/api/auth/forgot-password` qui n'existe pas | Faible (code mort de configuration) | Présent |
| 4 | `monitoring/prometheus-config.yaml` n'est jamais consommé par la stack `kube-prometheus-stack` réellement déployée (qui utilise des `ServiceMonitor` CRD) | Faible (fichier orphelin) | Présent |
| 5 | (CORRIGÉ DURANT CETTE MISSION) `JwtAuthenticationFilter` court-circuitait `authorizeHttpRequests` et renvoyait 403 sur **tous** les endpoints publics (y compris `/api/auth/login`) en l'absence de token | Critique | **Corrigé** ([JwtAuthenticationFilter.java:51-56](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java#L51)) |
| 6 | Doublon de `jwt.expiration` dans `application.properties` (la seconde valeur écrasait silencieusement la première, transformant un token de 15 minutes en token de 24 heures) | Moyenne | **Corrigé** lors d'une session précédente (une seule occurrence subsiste, [ligne 18](src/main/resources/application.properties#L18)) |
| 7 | `EmailVerificationService` et son contrôleur génèrent/valident des tokens, mais **aucun mécanisme d'envoi d'e-mail** n'existe (pas de `JavaMailSender`, pas de configuration SMTP) ; le token n'est jamais transmis à l'utilisateur | Moyenne (fonctionnalité incomplète, documentée comme telle) | Présent (assumé, voir commentaire [EmailVerificationService.java:25-26](src/main/java/com/devsecops/usermgmt/service/EmailVerificationService.java#L25)) |
| 8 | `SPRING_PROFILES_ACTIVE=prod` configuré dans `k8s/api-deployment.yaml` ([ligne 17](k8s/api-deployment.yaml#L17)) alors qu'**aucun fichier `application-prod.properties` n'existe** — le profil retombe silencieusement sur la configuration par défaut | Faible | Présent |

## 2.2 Failles de sécurité (analyse OWASP)

> Cette section distingue (a) les protections **réellement en place** dans le code "sécurisé de référence", (b) la faille **critique trouvée et corrigée pendant cette mission**, et (c) les vulnérabilités **volontairement injectables** documentées dans [VULNERABILITIES.md](VULNERABILITIES.md) (qui ne sont PAS actives dans le code actuel, mais existent comme protocole pédagogique).

### (a) Protections effectivement présentes dans le code actuel

| Catégorie OWASP | Protection observée | Preuve |
|---|---|---|
| A02 — Cryptographic Failures | Mots de passe hachés en BCrypt ; secret JWT externalisé et vérifié ≥ 32 caractères au démarrage | [SecurityConfig.java:87-89](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L87), [JwtTokenProvider.java:46-49](src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java#L46) |
| A01 — Broken Access Control (BOLA) | Vérification explicite de propriétaire/admin avant tout accès à une ressource utilisateur par id | [UserService.java:135-143](src/main/java/com/devsecops/usermgmt/service/UserService.java#L135) |
| A01 — Broken Access Control (BFLA) | Double protection (`SecurityConfig` + `@PreAuthorize`) sur `/api/admin/**` | [SecurityConfig.java:62](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L62), [AdminController.java:30](src/main/java/com/devsecops/usermgmt/controller/AdminController.java#L30) |
| A01 — Mass Assignment | `@JsonIgnore` sur `password` et `role` dans l'entité `User` | [User.java:47-55](src/main/java/com/devsecops/usermgmt/entity/User.java#L47) |
| A01 — Mass Assignment (registration) | `AuthService.register` force toujours `Role.ROLE_USER`, ignorant toute valeur cliente | [AuthService.java:65](src/main/java/com/devsecops/usermgmt/service/AuthService.java#L65) |
| A04 — Insecure Design (rate limiting) | Bucket4j sur les routes sensibles, retour HTTP 429 avec `Retry-After` | [RateLimitingFilter.java](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java) |
| A05 — Security Misconfiguration (CORS) | Origine unique autorisée (`http://localhost:3000`), pas de wildcard | [SecurityConfig.java:75](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L75) |
| A05 — Security Misconfiguration (erreurs) | `GlobalExceptionHandler` renvoie un format uniforme et masque les détails internes pour les exceptions inattendues (`"An unexpected error occurred"`) | [GlobalExceptionHandler.java:61-66](src/main/java/com/devsecops/usermgmt/exception/GlobalExceptionHandler.java#L61) |
| A06 — Vulnerable Components | Versions récentes (Spring Boot 3.2.5, Java 21, jjwt 0.12.5) ; scans Trivy + SonarCloud + Dependency-Check intégrés au pipeline | [pom.xml](pom.xml), [devsecops-pipeline.yml](.github/workflows/devsecops-pipeline.yml) |
| A07 — Identification & Authentication Failures | JWT signé HS256, expiration et `jti` obligatoires et vérifiés ; rotation des refresh tokens | [JwtTokenProvider.java:74-88](src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java#L74), [RefreshTokenService.java:84-89](src/main/java/com/devsecops/usermgmt/service/RefreshTokenService.java#L84) |
| Container Security | Image Alpine minimale, utilisateur non-root, `securityContext` Kubernetes complet (`runAsNonRoot`, `readOnlyRootFilesystem`, `capabilities: drop ALL`) | [Dockerfile:13,21](Dockerfile#L13), [k8s/api-deployment.yaml:70-94](k8s/api-deployment.yaml#L70) |

### (b) Faille critique trouvée et corrigée pendant cette mission

**`JwtAuthenticationFilter` bloquait l'intégralité des endpoints publics (y compris `/api/auth/login`).**

- **Avant correction** : le filtre appelait `response.sendError(HttpServletResponse.SC_UNAUTHORIZED)` et interrompait la chaîne dès qu'un Bearer token était absent ou invalide — *avant* que les règles `authorizeHttpRequests`/`permitAll` de Spring Security ne soient évaluées. Conséquence vérifiée par tests `curl` directs sur le cluster réel : `login`, `/actuator/health`, `/actuator/prometheus` renvoyaient **HTTP 403** même sans tentative d'authentification — l'API était totalement inutilisable en pratique (un frontend ne pouvait même pas se connecter).
- **Après correction** ([JwtAuthenticationFilter.java:38-82](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java#L38)) : le filtre **ne fait plus que peupler** le `SecurityContext` lorsqu'un token valide est présent ; en son absence, il laisse passer la requête vers la chaîne suivante, qui applique alors `authorizeHttpRequests` comme seule autorité de décision. Vérifié de nouveau par tests directs : `login` → 401 (rejet métier "mauvais identifiants", normal), `/actuator/health` et `/actuator/prometheus` → 200, `/api/admin/users` sans token → 403 (protection BFLA toujours active).
- Cette correction est **la plus importante de tout l'audit** : sans elle, l'application entière — pas seulement le monitoring — était cassée en usage réel, alors que la suite de tests (qui simule l'authentification via `SecurityMockMvcRequestPostProcessors.user(...)`, contournant le filtre JWT réel) passait à 100 %. C'est un exemple concret de l'écart possible entre "les tests passent" et "l'application fonctionne".

### (c) Vulnérabilités injectables documentées (protocole pédagogique — INACTIVES dans le code actuel)

Référence complète : [VULNERABILITIES.md](VULNERABILITIES.md). Ces 10 vulnérabilités (`INJ-01` à `INJ-10`) sont **volontairement absentes** du code "sécurisé" mais **documentées et balisées par des commentaires `// INJ-XX` directement dans les fichiers concernés**, afin de pouvoir être réintroduites de façon isolée et réversible pour valider une chaîne d'outils SAST/DAST/SCA :

| ID | Vulnérabilité | OWASP | Fichier où le commentaire balise est présent |
|---|---|---|---|
| INJ-01 | Secret JWT faible/hardcodé | A02 | (à injecter — pas de balise dans le code actif, documenté dans VULNERABILITIES.md uniquement) |
| INJ-02 | Absence d'expiration JWT | A02 | [JwtTokenProvider.java:60](src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java#L60) |
| INJ-03 | Injection SQL via JPQL concaténé | A03 | [UserRepository.java:15](src/main/java/com/devsecops/usermgmt/repository/UserRepository.java#L15) |
| INJ-04 | BOLA — suppression de la vérification de propriétaire | A01 | [UserService.java:134](src/main/java/com/devsecops/usermgmt/service/UserService.java#L134) |
| INJ-05 | Mass Assignment — suppression de `@JsonIgnore` sur `role` | A01 | [User.java:51](src/main/java/com/devsecops/usermgmt/entity/User.java#L51) |
| INJ-06 | BFLA — suppression de `.hasRole("ADMIN")` | A01 | [SecurityConfig.java:61](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L61) |
| INJ-07 | CVE connue (Log4Shell via downgrade Spring Boot 2.6.1) | A06 | [pom.xml:8](pom.xml#L8) |
| INJ-08 | CORS permissif (wildcard `*`) | A05 | [SecurityConfig.java:74](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L74) |
| INJ-09 | Image Docker vulnérable (`ubuntu:20.04`) | A06 | (documenté uniquement dans VULNERABILITIES.md — pas de balise dans le Dockerfile actuel) |
| INJ-10 | Combinaison chaînée INJ-01 + INJ-04 | A01+A02 | (combinaison des deux ci-dessus) |

> **Important pour la soutenance** : ces 10 entrées ne sont **pas des failles présentes dans le code livré** — ce sont des "interrupteurs" documentés que l'on peut activer un par un, sur une branche dédiée, pour démontrer qu'un outil SAST/DAST/SCA donné les détecte bien. Le tableau ci-dessus ne doit donc pas être confondu avec la section (b), qui décrit une vraie faille trouvée et corrigée.

## 2.3 Performance

| Constat | Détail | Fichier |
|---|---|---|
| Pagination correctement implémentée côté admin | `getAllUsersPaged` borne la taille de page à 100 et valide l'index | [UserService.java:94-107](src/main/java/com/devsecops/usermgmt/service/UserService.java#L94) |
| Méthode `getAllUsers()` non paginée conservée "pour compatibilité" | Charge l'intégralité de la table `users` en mémoire — risque de dégradation si la base grossit, bien que non exposée par un contrôleur actuel | [UserService.java:79-85](src/main/java/com/devsecops/usermgmt/service/UserService.java#L79) |
| `ddl-auto=update` en profil par défaut/production | Pratique déconseillée en production : Hibernate modifie le schéma au démarrage, ce qui peut provoquer des verrous longs ou des migrations incontrôlées sur une base volumineuse. Le profil `staging` corrige ceci avec `validate`, mais ce correctif n'est pas appliqué en "prod" faute de fichier `application-prod.properties` | [application.properties:25](src/main/resources/application.properties#L25) vs [application-staging.properties:2](src/main/resources/application-staging.properties#L2) |
| Pool de connexions | Aucune configuration HikariCP personnalisée trouvée — valeurs par défaut de Spring Boot utilisées (le dashboard Grafana fourni inclut pourtant un panel "Hikari connections", suggérant que le sujet a été anticipé) | [application.properties](src/main/resources/application.properties), [grafana-dashboard.json](monitoring/grafana-dashboard.json) |
| Index de base de données | Contraintes `unique` sur `username`/`email`/`token` créent des index implicites ; aucun index supplémentaire personnalisé déclaré | [User.java:41-45](src/main/java/com/devsecops/usermgmt/entity/User.java#L41) |
| Nettoyage programmé | Deux tâches `@Scheduled` suppriment les tokens expirés (refresh et vérification d'e-mail) toutes les 24h, évitant la croissance indéfinie des tables | [RefreshTokenService.java:105-110](src/main/java/com/devsecops/usermgmt/service/RefreshTokenService.java#L105), [EmailVerificationService.java:100-105](src/main/java/com/devsecops/usermgmt/service/EmailVerificationService.java#L100) |

## 2.4 Maintenabilité

| Constat | Détail |
|---|---|
| Organisation en couches claire | `controller` → `service` → `repository`, DTOs dédiés, mapper statique explicite (`UserMapper`) séparant entités et représentations publiques |
| Documentation inline riche | Quasi tous les fichiers principaux portent des Javadoc expliquant le "pourquoi" (pas seulement le "quoi"), notamment les choix de sécurité |
| Gestion centralisée des erreurs | `GlobalExceptionHandler` couvre les cas attendus (`MethodArgumentNotValidException`, `ResourceNotFoundException`, `AccessDeniedException` interne et Spring, `BadCredentialsException`, `IllegalArgumentException`, fallback générique) |
| Code potentiellement mort | `UserService.getAllUsers()` ([ligne 79-85](src/main/java/com/devsecops/usermgmt/service/UserService.java#L79)) n'est appelée par aucun contrôleur (l'admin utilise `getAllUsersPaged`) — conservée "pour compatibilité avec les tests existants" selon son commentaire, mais aucun test ne semble l'invoquer directement (`grep` ne montre aucun appel hors définition) |
| Fonctionnalités partiellement câblées | La vérification d'e-mail (`EmailVerificationService`/`EmailVerificationController`) est *architecturalement complète* mais *fonctionnellement non活ivée* (pas d'envoi d'e-mail, pas de blocage de connexion tant que l'e-mail n'est pas vérifié) — ceci est *documenté comme volontaire* dans le code ([EmailVerificationService.java:20-23](src/main/java/com/devsecops/usermgmt/service/EmailVerificationService.java#L20)) |
| Cohérence README ↔ code | Quelques écarts mineurs identifiés (voir §2.1, bugs 1 et 2) — à corriger avant une présentation pour éviter les questions piège du jury |
| Dépendances inutilisées | Aucune dépendance manifestement inutilisée détectée — chaque entrée du POM correspond à un usage identifiable dans le code (sécurité, JPA, validation, JWT, OpenAPI, rate limiting, logs, tests, observabilité) |

---

# PHASE 3 — MATRICE DE TEST DES FONCTIONNALITÉS

> Statuts : ✅ Fonctionnel et testé · ⚠️ Fonctionnel mais avec réserve · ❌ Non fonctionnel / non implémenté

| Fonctionnalité | Statut | Risque | Commentaire (objectif, fichiers, risques, améliorations) |
|---|---|---|---|
| **Inscription** (`POST /api/auth/register`) | ✅ | Faible | *Objectif* : créer un compte avec rôle `USER` forcé. *Fichiers* : [AuthController.java](src/main/java/com/devsecops/usermgmt/controller/AuthController.java), [AuthService.java:52-78](src/main/java/com/devsecops/usermgmt/service/AuthService.java#L52). *Tests* : `AuthControllerTest` (slice), `AuthIntegrationTest.registerWithValidDataReturns201WithTokens/registerDuplicateUsernameReturns400/registerWithInvalidEmailReturns400`. *Risque* : aucun majeur — doublons et validation gérés. *Amélioration* : limiter les informations divulguées par "username already taken" / "email already registered" (énumération de comptes, voir [AMELIORATIONS_RECOMMANDEES.md](AMELIORATIONS_RECOMMANDEES.md)) |
| **Connexion** (`POST /api/auth/login`) | ✅ | Faible | *Objectif* : authentifier et émettre access+refresh tokens. *Fichiers* : [AuthService.java:80-96](src/main/java/com/devsecops/usermgmt/service/AuthService.java#L80). *Tests* : `AuthIntegrationTest.loginWithValidCredentialsReturns200WithTokens/loginWithWrongPasswordReturns401/loginWithUnknownUserReturns401`. *Risque* : avait été rendu **totalement inopérant** par le bug du filtre JWT (corrigé, voir §2.2b) — bien que les tests słice (mockés) ne l'aient jamais détecté |
| **Rafraîchissement de token** (`POST /api/auth/refresh`) | ✅ | Faible | *Objectif* : émettre un nouveau couple access/refresh avec rotation. *Fichiers* : [RefreshTokenService.java](src/main/java/com/devsecops/usermgmt/service/RefreshTokenService.java). *Tests* : `RefreshTokenIntegrationTest` (4 scénarios : émission, token invalide, non-réutilisation après rotation). *Risque* : faible, logique couverte de bout en bout par tests d'intégration réels (Testcontainers) |
| **Déconnexion** (`POST /api/auth/logout`) | ✅ | Faible | *Objectif* : révoquer le refresh token courant. *Fichiers* : [AuthService.java:126-130](src/main/java/com/devsecops/usermgmt/service/AuthService.java#L126). *Tests* : `RefreshTokenIntegrationTest.logoutRevokesRefreshToken`. *Risque* : aucun — testé en intégration |
| **Vérification d'e-mail** (`GET /api/auth/verify`) | ⚠️ | Moyen | *Objectif* : confirmer une adresse e-mail via un token à usage unique. *Fichiers* : [EmailVerificationService.java](src/main/java/com/devsecops/usermgmt/service/EmailVerificationService.java), [EmailVerificationController.java](src/main/java/com/devsecops/usermgmt/controller/EmailVerificationController.java). *Tests* : **aucun test trouvé** pour ce flux (`grep` ne montre aucune classe `EmailVerification*Test`). *Risque* : la génération/validation de token fonctionne isolément, mais (a) le token n'est jamais envoyé par e-mail (pas de `JavaMailSender`), (b) le compte reste utilisable sans vérification — la fonctionnalité est donc **incomplète par conception assumée**, mais pourrait surprendre un jury qui la testerait |
| **Profil utilisateur courant** (`GET/PUT /api/users/me`) | ✅ | Faible | *Objectif* : consulter/mettre à jour son propre profil. *Fichiers* : [UserController.java:39-53](src/main/java/com/devsecops/usermgmt/controller/UserController.java#L39), [UserService.java:39-50](src/main/java/com/devsecops/usermgmt/service/UserService.java#L39). *Tests* : `UserControllerTest.getCurrentUserReturnsOk/updateCurrentUserReturnsOk`. *Risque* : aucun — l'identité provient du `Principal` authentifié, jamais d'un paramètre client |
| **Gestion d'utilisateur par id** (`GET/PUT/DELETE /api/users/{id}`) | ✅ | Faible | *Objectif* : opérations ciblées avec contrôle de propriété (anti-BOLA). *Fichiers* : [UserService.java:54-75,135-143](src/main/java/com/devsecops/usermgmt/service/UserService.java#L54). *Tests* : `UserControllerTest.getUserByIdAuthorizedReturnsOk`, `UserServiceTest` (4 scénarios incluant le refus BOLA). *Risque* : faible — la vérification d'ownership est explicite et testée à deux niveaux (unitaire + slice) |
| **Liste paginée des utilisateurs (admin)** (`GET /api/admin/users`) | ✅ | Faible | *Objectif* : lister tous les comptes avec pagination/tri, réservé aux admins. *Fichiers* : [AdminController.java:50-62](src/main/java/com/devsecops/usermgmt/controller/AdminController.java#L50), [UserService.java:94-107](src/main/java/com/devsecops/usermgmt/service/UserService.java#L94). *Tests* : `AdminIntegrationTest` (5 scénarios : pagination par défaut, taille personnalisée, page négative → 400, accès USER → 403, accès sans token → 401), `AdminControllerTest`. *Risque* : très bien couvert, y compris les cas limites |
| **Suppression d'utilisateur (admin)** (`DELETE /api/admin/users/{id}`) | ✅ | Faible | *Objectif* : suppression directe par un administrateur, sans vérification de propriété (le rôle suffit). *Fichiers* : [AdminController.java:65-69](src/main/java/com/devsecops/usermgmt/controller/AdminController.java#L65), [UserService.java:109-114](src/main/java/com/devsecops/usermgmt/service/UserService.java#L109). *Tests* : `AdminControllerTest.deleteUserAsAdminReturnsNoContent`. *Risque* : faible |
| **Limitation de débit (rate limiting)** | ✅ | Faible | *Objectif* : limiter par IP les abus sur les routes sensibles d'authentification. *Fichiers* : [RateLimitingFilter.java](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java). *Tests* : `RateLimitingFilterTest` (5, unitaires), `RateLimitIntegrationTest` (4, intégration réelle avec Testcontainers). *Risque* : faible — couverture unitaire + intégration ; néanmoins le compteur est en mémoire locale (`ConcurrentHashMap`), donc **non partagé entre réplicas** (voir [AMELIORATIONS_RECOMMANDEES.md](AMELIORATIONS_RECOMMANDEES.md)) — pertinent vu que le déploiement K8s prévoit 2 réplicas |
| **Journalisation structurée / traçage** | ✅ | Faible | *Objectif* : produire des logs JSON exploitables par ELK avec corrélation de requêtes (`traceId`/`requestId`). *Fichiers* : [RequestLoggingFilter.java](src/main/java/com/devsecops/usermgmt/filter/RequestLoggingFilter.java), [logback-spring.xml](src/main/resources/logback-spring.xml). *Tests* : aucun test automatisé dédié trouvé, mais des artefacts de logs réels existent dans `logs/`, preuve d'un fonctionnement observé en conditions réelles. *Risque* : faible — fonctionnalité d'infrastructure, validée empiriquement |
| **Documentation interactive (Swagger/OpenAPI)** | ✅ | Faible | *Objectif* : exposer une UI Swagger avec schéma de sécurité Bearer JWT. *Fichiers* : [OpenApiConfig.java](src/main/java/com/devsecops/usermgmt/config/OpenApiConfig.java), annotations `@Tag`/`@Operation`/`@SecurityRequirement` sur les contrôleurs. *Tests* : aucun test automatisé (cohérent — il s'agit de métadonnées). *Risque* : aucun |
| **Observabilité (Actuator/Prometheus/Grafana)** | ✅ | Faible | *Objectif* : exposer des métriques applicatives et un dashboard prêt à l'emploi. *Fichiers* : `application.properties` (bloc `management.*`), [monitoring/](monitoring/). *Tests* : validation manuelle effectuée sur cluster réel pendant cette mission (ServiceMonitor "up", scraping `/actuator/prometheus` fonctionnel après correction du filtre JWT). *Risque* : faible désormais — était bloqué à 100% par le bug du filtre JWT avant correction |
| **Déploiement Kubernetes / GitOps / Monitoring** | ✅ | Faible | *Objectif* : orchestrer l'API et sa base sur un cluster avec synchronisation GitOps et supervision. *Fichiers* : [k8s/](k8s/), [argocd/](argocd/), [monitoring/](monitoring/). *Tests* : déploiement réel validé sur Minikube pendant cette mission (pods `Running`, ArgoCD `Synced`/`Healthy`, Prometheus targets `up`). *Risque* : faible sur le plan fonctionnel ; voir §2.1 (bug 4) pour l'incohérence du fichier `prometheus-config.yaml` orphelin |
| **Pipeline CI/CD DevSecOps** | ⚠️ | Moyen | *Objectif* : exécuter automatiquement build, SAST (SonarCloud), SCA (Trivy), DAST (OWASP ZAP), et publier un rapport consolidé. *Fichiers* : [devsecops-pipeline.yml](.github/workflows/devsecops-pipeline.yml). *Tests* : aucune exécution observable depuis ce dépôt local (nécessite GitHub Actions + secrets configurés : `SONAR_TOKEN`, `HARBOR_*`). *Risque* : le job `dast` dépend de `/health` qui n'existe pas dans le code (bug n°2) — risque de timeout lors de l'attente de disponibilité de l'application |
| **Mot de passe oublié** (`forgot-password`) | ❌ | — | *Objectif annoncé par la configuration* : limiter le débit d'un futur endpoint de réinitialisation de mot de passe. *Fichiers* : `application.properties` (rate-limit), [RateLimitingFilter.java:44](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java#L44). *Constat* : **aucun contrôleur, service, ni route n'implémente cette fonctionnalité** — il s'agit de configuration anticipée mais jamais concrétisée. *Risque* : aucun (le code mort ne s'exécute jamais) — mais source de confusion potentielle pour le jury |
