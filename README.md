# API DevSecOps — User Management

API REST sécurisée de gestion d'utilisateurs, développée avec **Spring Boot 3.2.x**, conçue comme application de référence pour un pipeline **DevSecOps** complet (PFE).

L'application implémente l'authentification JWT, le contrôle d'accès basé sur les rôles (RBAC), et intègre un [protocole d'injection de vulnérabilités](VULNERABILITIES.md) permettant de valider les outils SAST, DAST et SCA du pipeline CI/CD.

---

## Prérequis

| Outil | Version minimale |
|-------|-----------------|
| Java (JDK) | 21 |
| Maven | 3.9+ |
| Docker | 24+ |
| Docker Compose | 2.20+ |
| PostgreSQL | 15 (fourni via Docker) |

---

## Démarrage rapide (Docker Compose)

```bash
# Cloner le dépôt
git clone <url-du-repo> && cd api-devsecops-usermgmt

# Lancer l'ensemble des services (PostgreSQL + API)
docker compose up -d --build

# Vérifier que les conteneurs sont sains
docker compose ps
```

L'API est accessible sur **http://localhost:8080**.

### Variables d'environnement

Créer un fichier `.env` à la racine (optionnel — des valeurs par défaut sont fournies) :

```env
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=cHJvamVjdC1kZXZzZWNvcHMtcGZlLXNlY3JldC1rZXktMjAyNA==
```

> Le `JWT_SECRET` doit contenir au minimum **32 caractères** encodés en Base64.

---

## Démarrage manuel (sans Docker)

```bash
# 1. S'assurer que PostgreSQL est lancé et accessible sur localhost:5432
#    avec une base de données "usermgmt" créée.

# 2. Configurer les variables d'environnement
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=cHJvamVjdC1kZXZzZWNvcHMtcGZlLXNlY3JldC1rZXktMjAyNA==

# 3. Compiler et lancer
mvn clean package -DskipTests
java -jar target/*.jar
```

---

## Endpoints de l'API

### Authentification (publics)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `POST` | `/api/auth/register` | Créer un nouveau compte utilisateur |
| `POST` | `/api/auth/login` | S'authentifier et obtenir un token JWT |

### Utilisateurs (authentifié)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `GET` | `/api/users/me` | Obtenir le profil de l'utilisateur connecté |
| `PUT` | `/api/users/me` | Mettre à jour le profil de l'utilisateur connecté |
| `GET` | `/api/users/{id}` | Obtenir un utilisateur par ID (propriétaire ou admin) |
| `PUT` | `/api/users/{id}` | Mettre à jour un utilisateur par ID (propriétaire ou admin) |
| `DELETE` | `/api/users/{id}` | Supprimer un utilisateur par ID (propriétaire ou admin) |

### Administration (ADMIN uniquement)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `GET` | `/api/admin/users` | Lister tous les utilisateurs |
| `DELETE` | `/api/admin/users/{id}` | Supprimer un utilisateur par ID |

### Santé et documentation

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `GET` | `/health` | Vérification de santé de l'application |
| `GET` | `/swagger-ui.html` | Interface Swagger UI |
| `GET` | `/v3/api-docs` | Spécification OpenAPI 3.0 (JSON) |

---

## Documentation Swagger UI

Une fois l'application lancée, accéder à la documentation interactive :

**http://localhost:8080/swagger-ui.html**

L'interface permet de tester tous les endpoints directement depuis le navigateur avec authentification Bearer JWT.

---

## Pipeline DevSecOps

Ce projet est conçu pour être intégré dans un pipeline CI/CD DevSecOps comprenant les étapes suivantes :

```
Code → Build → SAST → Tests → DAST → SCA → Deploy
```

| Étape | Outil | Rôle |
|-------|-------|------|
| **SAST** | SonarQube, Semgrep | Analyse statique du code source |
| **DAST** | OWASP ZAP | Tests dynamiques sur l'application déployée |
| **SCA** | OWASP Dependency-Check, Trivy, Grype | Analyse des dépendances et images Docker |
| **Secrets** | GitLeaks, TruffleHog | Détection de secrets dans le code |

---

## Fonctionnalités de sécurité

- **Authentification JWT** avec expiration configurable et secret externalisé
- **Hachage BCrypt** des mots de passe
- **RBAC** (Role-Based Access Control) avec rôles `USER` et `ADMIN`
- **Vérification de propriétaire** (BOLA protection) sur les opérations utilisateur
- **Protection Mass Assignment** via `@JsonIgnore` sur les champs sensibles
- **CORS restrictif** limité à l'origine front-end autorisée
- **Image Docker minimale** basée sur Alpine avec utilisateur non-root
- **Healthcheck** intégré au Dockerfile et docker-compose
- **Validation des entrées** via Jakarta Bean Validation

---

## Protocole d'injection de vulnérabilités

Ce projet inclut un protocole documenté de **10 vulnérabilités injectables** couvrant les catégories OWASP Top 10 (2021). Chaque vulnérabilité est activable par une modification isolée et réversible.

Consulter le document complet : **[VULNERABILITIES.md](VULNERABILITIES.md)**

---

## Structure du projet

```
api-devsecops-usermgmt/
├── src/main/java/com/devsecops/usermgmt/
│   ├── config/          # SecurityConfig, JwtConfig, OpenApiConfig
│   ├── controller/      # AuthController, UserController, AdminController
│   ├── dto/             # Request/Response DTOs
│   ├── entity/          # User, Role (JPA)
│   ├── exception/       # GlobalExceptionHandler
│   ├── mapper/          # UserMapper
│   ├── repository/      # UserRepository (Spring Data JPA)
│   ├── security/        # JwtTokenProvider, JwtAuthenticationFilter
│   └── service/         # AuthService, UserService
├── src/main/resources/
│   ├── application.properties
│   └── application-staging.properties
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── VULNERABILITIES.md
└── README.md
```

---

## Licence

Projet académique — PFE DevSecOps.
