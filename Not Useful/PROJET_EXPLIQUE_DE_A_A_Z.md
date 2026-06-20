# LE PROJET EXPLIQUÉ DE A À Z — Guide de soutenance

> Ce document explique le projet `api-devsecops-usermgmt` **en langage simple**, comme si vous le présentiez à quelqu'un qui ne connaît pas la programmation, puis approfondit chaque point avec le niveau technique attendu par un jury. Tout ce qui est écrit ici correspond à des fichiers réellement présents dans le dépôt (aucune supposition).

---

## 1. Présentation du projet

### Explication simple

Imaginez un **grand carnet d'adresses numérique sécurisé**. Des gens peuvent :
- créer un compte (s'inscrire),
- se connecter avec leur mot de passe,
- consulter et modifier leurs informations personnelles,
- et un "responsable" (l'administrateur) peut voir et gérer tous les comptes.

Ce projet, c'est **le moteur** qui fait fonctionner ce carnet d'adresses : il reçoit les demandes ("je veux me connecter", "je veux voir mon profil"), vérifie que la personne a le droit de faire ce qu'elle demande, va chercher ou modifier les informations dans une base de données, puis renvoie une réponse.

Ce moteur s'appelle une **API** (Application Programming Interface) : un programme qui ne montre rien visuellement (pas de boutons, pas d'écrans) mais qui répond à des demandes envoyées par d'autres programmes (un site web, une application mobile, etc.).

Le projet s'appelle un projet **DevSecOps** : ce mot veut dire qu'on ne s'est pas contenté de faire fonctionner le programme, on a aussi mis en place tout un ensemble d'outils et de pratiques pour le **construire**, le **sécuriser**, le **tester automatiquement** et le **surveiller** une fois qu'il fonctionne — un peu comme on n'installerait pas seulement une porte dans une maison, mais aussi une serrure, une alarme, et des caméras de surveillance.

### Explication technique

Il s'agit d'une **API REST** développée avec **Spring Boot 3.2.5** sur **Java 21** ([pom.xml](pom.xml)), structurée selon une architecture en couches classique `Controller → Service → Repository`, avec persistance dans une base **PostgreSQL**. Le projet implémente :
- un système d'**authentification par JWT** (JSON Web Token) avec rotation de refresh tokens,
- des **mécanismes de défense OWASP** documentés et actifs (BOLA, BFLA, Mass Assignment, CORS strict, rate limiting),
- une suite de **tests** (unitaires, slice tests, et tests d'intégration avec Testcontainers/PostgreSQL réel),
- une **chaîne CI/CD complète** (GitHub Actions : build → SAST SonarCloud → SCA Trivy → DAST OWASP ZAP),
- un **déploiement Kubernetes** avec GitOps (ArgoCD) et supervision (Prometheus + Grafana via kube-prometheus-stack),
- et un protocole pédagogique [`VULNERABILITIES.md`](VULNERABILITIES.md) listant 10 vulnérabilités injectables (`INJ-01` à `INJ-10`) destinées à valider que les outils de sécurité (SAST/DAST/SCA) détectent bien les failles correspondantes.

C'est donc un projet conçu non seulement pour "fonctionner", mais pour **démontrer la maîtrise d'un cycle de développement logiciel sécurisé de bout en bout**.

---

## 2. Architecture globale : Utilisateur → Frontend → Backend → Base de données → Résultat

### Explication simple

Voici, en mots simples, ce qui se passe quand quelqu'un utilise ce système (par exemple pour se connecter) :

1. **L'utilisateur** ouvre une application (un site web ou une appli mobile — *ce frontend n'est pas dans ce dépôt*, il est séparé) et tape son nom d'utilisateur et son mot de passe.
2. **Le frontend** (l'écran que voit l'utilisateur) empaquette ces informations et les envoie à l'adresse de notre API, par exemple `http://localhost:8080/api/auth/login`.
3. **Le backend** (le projet que nous étudions ici) reçoit cette demande. Il vérifie : est-ce que ce nom d'utilisateur existe ? Est-ce que le mot de passe est correct ?
4. Pour vérifier, le backend interroge la **base de données** (PostgreSQL), qui est comme une grande armoire à fiches où sont rangées toutes les informations sur les utilisateurs.
5. Si tout est correct, le backend fabrique un "badge temporaire" numérique (le **token JWT**) et le renvoie au frontend.
6. **Le frontend** reçoit ce badge et l'utilisateur voit "Connexion réussie" — c'est **le résultat**.

Pour toutes les actions suivantes (voir son profil, etc.), l'utilisateur présente ce badge, et le backend vérifie qu'il est valide avant de répondre.

### Explication technique

```
┌──────────────┐        HTTPS/JSON         ┌────────────────────────────┐        SQL         ┌──────────────┐
│  Utilisateur │ ───────────────────────▶  │   Backend Spring Boot      │ ─────────────────▶ │  PostgreSQL   │
│ (navigateur/ │ ◀───────────────────────  │  (api-devsecops-usermgmt)  │ ◀───────────────── │  (table users,│
│  app mobile) │     JSON + JWT             │                            │     JDBC/Hibernate  │  refresh_     │
└──────────────┘                            │  Filtres ──▶ Contrôleurs   │                     │  tokens, ...) │
       ▲                                    │  ──▶ Services ──▶ Repos    │                     └──────────────┘
       │                                    └────────────────────────────┘
       │  CORS: http://localhost:3000 uniquement
┌──────┴───────┐
│   Frontend   │   (NON présent dans ce dépôt — seule la configuration CORS
│  (séparé)    │    dans SecurityConfig.java en atteste l'existence prévue)
└──────────────┘
```

Le flux détaillé d'une requête authentifiée traverse, dans l'ordre :
1. **`RequestLoggingFilter`** ([RequestLoggingFilter.java](src/main/java/com/devsecops/usermgmt/filter/RequestLoggingFilter.java)) — attribue un `traceId`/`requestId`, mesure la durée, peuple le MDC pour les logs JSON.
2. **`RateLimitingFilter`** ([RateLimitingFilter.java](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java)) — vérifie via Bucket4j que l'IP n'a pas dépassé son quota de requêtes.
3. **`JwtAuthenticationFilter`** ([JwtAuthenticationFilter.java](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java)) — extrait le token `Bearer`, le valide, et **peuple** (sans jamais rejeter) le `SecurityContextHolder`.
4. **Spring Security `SecurityFilterChain`** ([SecurityConfig.java](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java)) — applique les règles `permitAll`/`hasRole`/`authenticated` : c'est **la seule autorité** qui décide d'autoriser ou non la requête (401/403).
5. **Contrôleur** (ex. [AuthController.java](src/main/java/com/devsecops/usermgmt/controller/AuthController.java)) — reçoit la requête désérialisée en DTO.
6. **Service** (ex. [AuthService.java](src/main/java/com/devsecops/usermgmt/service/AuthService.java)) — contient la logique métier (vérifications, règles).
7. **Repository** (ex. [UserRepository.java](src/main/java/com/devsecops/usermgmt/repository/UserRepository.java)) — exécute des requêtes via Spring Data JPA/Hibernate vers PostgreSQL.
8. La réponse remonte, est transformée en DTO via [UserMapper.java](src/main/java/com/devsecops/usermgmt/mapper/UserMapper.java) (pour ne jamais exposer le mot de passe), puis renvoyée en JSON.

---

## 3. Analyse dossier par dossier

### Explication simple

Le projet est rangé comme une armoire avec des tiroirs bien étiquetés. Voici ce que contient chaque "tiroir" principal :

- **`src/main/java/...`** : tout le code qui fait fonctionner le programme.
- **`src/main/resources/`** : les fichiers de configuration (réglages, textes de logs).
- **`src/test/java/...`** : les "contrôles qualité" automatiques qui vérifient que le programme fonctionne comme prévu.
- **`k8s/`** : les instructions pour déployer le programme sur un orchestrateur de conteneurs (Kubernetes).
- **`argocd/`** : les instructions pour que le déploiement se fasse automatiquement à chaque changement de code (GitOps).
- **`monitoring/`** : les instructions pour surveiller le programme une fois qu'il tourne (tableaux de bord, alertes).
- **`.github/workflows/`** : la "chaîne de fabrication automatique" qui construit, teste et vérifie la sécurité du programme à chaque modification.
- Des fichiers à la racine (`Dockerfile`, `docker-compose.yml`, `pom.xml`, `README.md`, `VULNERABILITIES.md`...) : les "modes d'emploi" et les recettes de construction du projet.

### Explication technique

| Dossier | Contenu réel observé | Rôle |
|---|---|---|
| `src/main/java/com/devsecops/usermgmt/` | `controller/`, `service/`, `repository/`, `dto/`, `entity/`, `security/`, `filter/`, `config/`, `mapper/`, `exception/` | Code applicatif en couches |
| `src/main/resources/` | `application.properties`, `application-staging.properties`, `logback-spring.xml` | Configuration Spring (datasource, JWT, rate-limit, actuator) et logging JSON structuré |
| `src/test/java/...` | Tests unitaires (`*Test.java`), tests d'intégration (`*IntegrationTest.java`), `AbstractIntegrationTest.java` (base Testcontainers) | Vérification automatisée du comportement |
| `k8s/` | `namespace.yaml`, `api-deployment.yaml`, `services.yaml`, `postgres-*.yaml`, `secrets.yaml`, `configmap.yaml`, `ingress.yaml`, etc. | Manifestes de déploiement Kubernetes |
| `argocd/` | Application(s) ArgoCD pointant sur ce dépôt Git | Déploiement continu GitOps |
| `monitoring/` | `api-servicemonitor.yaml`, `prometheus-config.yaml`, dashboards Grafana | Supervision via Prometheus Operator (kube-prometheus-stack) |
| `.github/workflows/` | `devsecops-pipeline.yml` | Pipeline CI/CD : build, SAST (SonarCloud), SCA (Trivy), DAST (OWASP ZAP), reporting |
| Racine | `Dockerfile`, `docker-compose.yml`, `pom.xml`, `README.md`, `VULNERABILITIES.md` | Build, orchestration locale, dépendances, documentation, protocole pédagogique de vulnérabilités |

Pour le détail exhaustif de l'arborescence (avec tous les fichiers), voir [AUDIT_COMPLET.md §1.1](AUDIT_COMPLET.md).

---

## 4. Analyse fichier par fichier

> Pour chaque fichier important : **son rôle**, **quand il s'exécute**, **qui l'appelle**, et **ce qui se passerait s'il disparaissait**.

### 4.1 Point d'entrée

**[ApiDevsecopsApplication.java](src/main/java/com/devsecops/usermgmt/ApiDevsecopsApplication.java)**
- *Rôle* : la classe `@SpringBootApplication` qui démarre toute l'application.
- *Quand* : au lancement du programme (`mvn spring-boot:run`, ou démarrage du conteneur Docker).
- *Qui l'appelle* : la JVM, via la méthode `main`.
- *Si elle disparaît* : le programme ne peut plus démarrer du tout — c'est la clé de contact du moteur.

### 4.2 Sécurité (`security/`, `filter/`, `config/`)

**[SecurityConfig.java](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java)**
- *Rôle* : définit la chaîne de filtres de sécurité (`SecurityFilterChain`) — quelles routes sont publiques (`permitAll`), lesquelles nécessitent une authentification, lesquelles nécessitent le rôle `ADMIN`. Configure aussi le CORS (restreint à `http://localhost:3000`) et désactive le CSRF (justifié pour une API stateless en JWT).
- *Quand* : une seule fois au démarrage (configuration), puis à **chaque requête HTTP** (la chaîne de filtres est exécutée pour chaque appel).
- *Qui l'appelle* : le framework Spring Security lui-même.
- *Si elle disparaît* : Spring Security applique des règles par défaut beaucoup trop strictes (tout protégé) ou crash au démarrage — l'application devient inutilisable.

**[JwtAuthenticationFilter.java](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java)**
- *Rôle* : extrait le jeton `Bearer xxx` de l'en-tête `Authorization`, le valide via `JwtTokenProvider`, et **si valide**, place l'utilisateur authentifié dans le `SecurityContextHolder`. **Important** : ce filtre ne rejette jamais une requête lui-même — il se contente de peupler le contexte ; c'est `SecurityConfig` qui décide d'autoriser ou non.
- *Quand* : à chaque requête HTTP entrante, avant que Spring Security ne décide d'autoriser ou non l'accès.
- *Qui l'appelle* : la chaîne de filtres Servlet (`OncePerRequestFilter`), orchestrée par Spring Security.
- *Si il disparaît* : plus aucune requête authentifiée par JWT ne fonctionnerait — tout le monde serait traité comme un visiteur anonyme.
- ⚠️ **C'est le fichier au cœur du bug critique trouvé et corrigé pendant cet audit** (voir [LISTE_DES_BUGS.md — BUG #1](LISTE_DES_BUGS.md)) : une version antérieure de ce filtre **rejetait** la requête (renvoyait 403) dès qu'aucun jeton n'était présent — *avant même* que Spring Security ne puisse appliquer ses règles `permitAll`. Résultat : impossible de se connecter ou de s'inscrire, puisque ces routes publiques étaient bloquées par le filtre lui-même. La version actuelle (lignes 51-56, commentée explicitement) ne fait que peupler le contexte, jamais rejeter.

**[JwtTokenProvider.java](src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java)**
- *Rôle* : fabrique les jetons JWT (signature HS256), les valide, en extrait le nom d'utilisateur. Vérifie au démarrage (`@PostConstruct`) que la clé secrète fait au moins 32 caractères.
- *Quand* : à la connexion (création du token), au rafraîchissement, et à chaque requête authentifiée (validation).
- *Qui l'appelle* : `AuthService` (création), `JwtAuthenticationFilter` (validation/lecture).
- *Si il disparaît* : impossible de créer ou de vérifier un jeton — toute l'authentification s'effondre.
- Contient un commentaire `// INJ-02` marquant un point d'injection pédagogique possible sur l'expiration du token (voir [VULNERABILITIES.md](VULNERABILITIES.md)).

**[UserDetailsServiceImpl.java](src/main/java/com/devsecops/usermgmt/security/UserDetailsServiceImpl.java)**
- *Rôle* : fait le pont entre l'entité `User` de la base de données et l'objet `UserDetails` que Spring Security comprend.
- *Quand* : chaque fois que Spring Security a besoin de "charger" un utilisateur (connexion, validation de jeton).
- *Qui l'appelle* : `AuthenticationManager` (connexion), `JwtAuthenticationFilter` (validation de jeton).
- *Si il disparaît* : Spring Security ne peut plus retrouver les utilisateurs en base — connexion impossible.

**[RateLimitingFilter.java](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java)**
- *Rôle* : limite le nombre de requêtes par adresse IP grâce à la bibliothèque Bucket4j (algorithme de "seau à jetons"), stocké dans une `ConcurrentHashMap` en mémoire.
- *Quand* : à chaque requête HTTP, avant le filtre JWT.
- *Qui l'appelle* : la chaîne de filtres Servlet.
- *Si il disparaît* : plus aucune protection contre les attaques par force brute ou les abus de l'API — un attaquant pourrait essayer des milliers de mots de passe sans limite.

**[RequestLoggingFilter.java](src/main/java/com/devsecops/usermgmt/filter/RequestLoggingFilter.java)**
- *Rôle* : attribue (ou récupère) un `traceId`/`requestId`, mesure la durée de traitement, et place ces informations dans le MDC (Mapped Diagnostic Context) pour que les logs JSON contiennent ce contexte.
- *Quand* : à chaque requête, en tout début et fin de traitement.
- *Qui l'appelle* : la chaîne de filtres Servlet.
- *Si il disparaît* : les logs perdent toute capacité de corrélation — impossible de relier les lignes de logs d'une même requête entre plusieurs services (essentiel pour le debugging en production avec ELK).

### 4.3 Contrôleurs (`controller/`)

**[AuthController.java](src/main/java/com/devsecops/usermgmt/controller/AuthController.java)**
- *Rôle* : expose les routes `/api/auth/register`, `/login`, `/refresh`, `/logout`.
- *Quand* : à chaque appel HTTP sur ces routes.
- *Qui l'appelle* : le frontend (ou tout client HTTP — Postman, curl...).
- *Si il disparaît* : impossible de s'inscrire, se connecter, rafraîchir un jeton ou se déconnecter — porte d'entrée du système fermée.

**[UserController.java](src/main/java/com/devsecops/usermgmt/controller/UserController.java)**
- *Rôle* : expose les routes `/api/users/me`, `/api/users/{id}` (consultation/modification de profil).
- *Quand* : quand un utilisateur connecté consulte ou modifie son profil.
- *Qui l'appelle* : le frontend, après authentification.
- *Si il disparaît* : un utilisateur connecté ne pourrait plus consulter ni modifier ses informations.

**[AdminController.java](src/main/java/com/devsecops/usermgmt/controller/AdminController.java)**
- *Rôle* : expose les routes `/api/admin/users` (liste paginée de tous les utilisateurs, suppression). Protégé par `@PreAuthorize("hasRole('ADMIN')")`.
- *Quand* : quand un administrateur gère les comptes.
- *Qui l'appelle* : le frontend "back-office", uniquement pour les comptes `ROLE_ADMIN`.
- *Si il disparaît* : aucune fonctionnalité d'administration des utilisateurs ne serait disponible.

**[EmailVerificationController.java](src/main/java/com/devsecops/usermgmt/controller/EmailVerificationController.java)**
- *Rôle* : expose la route de vérification d'adresse e-mail via un jeton à usage unique.
- *Quand* : quand un utilisateur clique sur un lien de vérification reçu par e-mail (en théorie).
- *Qui l'appelle* : un navigateur suivant un lien envoyé par e-mail.
- *Si il disparaît* : aucun changement perceptible aujourd'hui car **le flux n'est pas activé** (voir §4.5 et [LISTE_DES_BUGS.md — BUG #6](LISTE_DES_BUGS.md)) — c'est une fonctionnalité architecturée mais non branchée (pas de `JavaMailSender`, pas d'appel à `createToken` lors de l'inscription).

### 4.4 Services (`service/`)

**[AuthService.java](src/main/java/com/devsecops/usermgmt/service/AuthService.java)**
- *Rôle* : logique métier de l'inscription (force toujours le rôle `ROLE_USER`, [ligne 65](src/main/java/com/devsecops/usermgmt/service/AuthService.java#L65) — défense Mass Assignment), de la connexion (vérifie identifiants, génère les jetons), du rafraîchissement et de la déconnexion (révocation du refresh token).
- *Quand* : appelé par `AuthController` à chaque requête sur `/api/auth/*`.
- *Qui l'appelle* : `AuthController`.
- *Si il disparaît* : aucune des opérations d'authentification ne pourrait s'exécuter, même si les routes existent encore.

**[UserService.java](src/main/java/com/devsecops/usermgmt/service/UserService.java)**
- *Rôle* : logique métier de gestion des profils utilisateurs ; contient la méthode `verifyOwnershipOrAdmin` ([lignes 135-143](src/main/java/com/devsecops/usermgmt/service/UserService.java#L135)) — la défense **BOLA** (Broken Object Level Authorization) qui vérifie qu'un utilisateur ne peut consulter/modifier que **son propre** profil, sauf s'il est admin.
- *Quand* : à chaque appel sur `/api/users/*` et `/api/admin/users/*`.
- *Qui l'appelle* : `UserController`, `AdminController`.
- *Si il disparaît* : plus aucune opération de gestion de profil possible.
- Contient une méthode `getAllUsers()` ([lignes 79-85](src/main/java/com/devsecops/usermgmt/service/UserService.java#L79)) qui semble **non utilisée** (code mort, voir [AMELIORATIONS_RECOMMANDEES.md §3](AMELIORATIONS_RECOMMANDEES.md)).

**[RefreshTokenService.java](src/main/java/com/devsecops/usermgmt/service/RefreshTokenService.java)**
- *Rôle* : gère le cycle de vie des jetons de rafraîchissement — un seul jeton actif par utilisateur, **rotation** à chaque utilisation (l'ancien est invalidé, un nouveau est émis), et nettoyage planifié des jetons expirés.
- *Quand* : à la connexion (création), au rafraîchissement (rotation), à la déconnexion (révocation), et périodiquement (tâche planifiée de nettoyage).
- *Qui l'appelle* : `AuthService`, et le planificateur Spring (`@Scheduled`).
- *Si il disparaît* : impossible de prolonger une session sans se reconnecter complètement — et la base accumulerait des jetons expirés indéfiniment.

**[EmailVerificationService.java](src/main/java/com/devsecops/usermgmt/service/EmailVerificationService.java)**
- *Rôle* : prévu pour créer/valider des jetons de vérification d'e-mail à usage unique. Sa Javadoc indique explicitement ([lignes 17-26](src/main/java/com/devsecops/usermgmt/service/EmailVerificationService.java#L17)) qu'il s'agit d'une architecture **complète mais non activée** ("architecturally complete but not enforced... no JavaMailSender").
- *Quand* : en théorie, à l'inscription (création du jeton) et au clic sur le lien de vérification.
- *Qui l'appelle* : en théorie `AuthService` et `EmailVerificationController` — en pratique, `AuthService.register` n'appelle jamais `createToken`.
- *Si il disparaît* : aucun changement perceptible dans le comportement actuel de l'application.

### 4.5 Entités et persistance (`entity/`, `repository/`)

**[User.java](src/main/java/com/devsecops/usermgmt/entity/User.java)**
- *Rôle* : représente un utilisateur en base de données (identifiant, nom d'utilisateur, e-mail, mot de passe haché, rôle, dates...). Les champs `password` et `role` sont annotés `@JsonIgnore` pour ne **jamais** être renvoyés dans les réponses JSON (défense Mass Assignment / fuite de données sensibles).
- *Quand* : chargée/sauvegardée à chaque opération impliquant un utilisateur.
- *Qui l'appelle* : `UserRepository` (Hibernate), tous les services.
- *Si elle disparaît* : impossible de représenter un utilisateur — toute l'application perd son sens.

**[Role.java](src/main/java/com/devsecops/usermgmt/entity/Role.java)**
- *Rôle* : énumération des rôles possibles (`ROLE_USER`, `ROLE_ADMIN`).
- *Quand* : utilisée partout où les droits sont vérifiés.
- *Qui l'appelle* : `User`, `SecurityConfig`, `AuthService`.
- *Si elle disparaît* : le système de rôles n'aurait plus de valeurs possibles — la compilation échouerait.

**[RefreshToken.java](src/main/java/com/devsecops/usermgmt/entity/RefreshToken.java)** / **[EmailVerificationToken.java](src/main/java/com/devsecops/usermgmt/entity/EmailVerificationToken.java)**
- *Rôle* : représentent respectivement un jeton de rafraîchissement et un jeton de vérification d'e-mail (valeur, expiration, utilisateur associé).
- *Quand* : créés/consultés lors des opérations correspondantes.
- *Qui l'appelle* : `RefreshTokenService`/`EmailVerificationService` et leurs repositories.
- *Si ils disparaissent* : la rotation des jetons de rafraîchissement (resp. la vérification d'e-mail) ne pourrait plus être persistée.

**[UserRepository.java](src/main/java/com/devsecops/usermgmt/repository/UserRepository.java)**, **[RefreshTokenRepository.java](src/main/java/com/devsecops/usermgmt/repository/RefreshTokenRepository.java)**, **[EmailVerificationTokenRepository.java](src/main/java/com/devsecops/usermgmt/repository/EmailVerificationTokenRepository.java)**
- *Rôle* : interfaces Spring Data JPA — fournissent les opérations de lecture/écriture en base sans écrire de SQL à la main (méthodes dérivées et requêtes liées par paramètres, donc pas de concaténation de chaînes — protection naturelle contre l'injection SQL).
- *Quand* : appelés par les services à chaque opération de persistance.
- *Qui les appelle* : `UserService`, `AuthService`, `RefreshTokenService`, `EmailVerificationService`.
- *Si ils disparaissent* : plus aucun accès à la base de données — l'application ne peut rien lire ni écrire.
- `UserRepository` contient un commentaire `// INJ-03` marquant un point d'injection SQL pédagogique possible (voir [VULNERABILITIES.md](VULNERABILITIES.md)) — **non actif** dans le code actuel.

### 4.6 DTOs, mapper, exceptions

**DTOs** ([RegisterRequest](src/main/java/com/devsecops/usermgmt/dto/RegisterRequest.java), [LoginRequest](src/main/java/com/devsecops/usermgmt/dto/LoginRequest.java), [UpdateUserRequest](src/main/java/com/devsecops/usermgmt/dto/UpdateUserRequest.java), [UserResponse](src/main/java/com/devsecops/usermgmt/dto/UserResponse.java), [JwtResponse](src/main/java/com/devsecops/usermgmt/dto/JwtResponse.java), [RefreshTokenRequest](src/main/java/com/devsecops/usermgmt/dto/RefreshTokenRequest.java), [TokenRefreshResponse](src/main/java/com/devsecops/usermgmt/dto/TokenRefreshResponse.java), [PageResponse](src/main/java/com/devsecops/usermgmt/dto/PageResponse.java))
- *Rôle* : objets de transfert qui définissent précisément la forme des données échangées en JSON — ce qui **entre** (validations `@NotBlank`, `@Size`, `@Email`...) et ce qui **sort** (jamais de mot de passe).
- *Quand* : à chaque requête/réponse HTTP.
- *Qui les appelle* : les contrôleurs (désérialisation des requêtes, sérialisation des réponses).
- *Si ils disparaissent* : on serait obligé d'exposer directement les entités JPA — risque élevé de fuite de données sensibles (mot de passe, rôle...).

**[UserMapper.java](src/main/java/com/devsecops/usermgmt/mapper/UserMapper.java)**
- *Rôle* : convertit les entités `User` en DTO `UserResponse` (méthode statique).
- *Quand* : à chaque réponse contenant des informations utilisateur.
- *Qui l'appelle* : `UserService`, `AuthService`, `AdminController`.
- *Si il disparaît* : chaque service devrait reconstruire la conversion à la main — duplication et risque d'oubli d'un champ sensible.

**[GlobalExceptionHandler.java](src/main/java/com/devsecops/usermgmt/exception/GlobalExceptionHandler.java)**, **[AccessDeniedException.java](src/main/java/com/devsecops/usermgmt/exception/AccessDeniedException.java)**, **[ResourceNotFoundException.java](src/main/java/com/devsecops/usermgmt/exception/ResourceNotFoundException.java)**
- *Rôle* : centralisent la transformation des erreurs internes en réponses JSON propres et cohérentes (`@ControllerAdvice`), avec les bons codes HTTP (404, 403, 400...).
- *Quand* : dès qu'une exception survient pendant le traitement d'une requête.
- *Qui les appelle* : le framework Spring (`@ExceptionHandler`).
- *Si ils disparaissent* : les erreurs remonteraient sous forme de pages d'erreur génériques (ou de stack traces exposées — fuite d'information).

### 4.7 Configuration (`config/`, `resources/`)

**[JwtConfig.java](src/main/java/com/devsecops/usermgmt/config/JwtConfig.java)** — lit les propriétés `jwt.*` (secret, expiration) depuis `application.properties` et les expose comme bean de configuration typée.

**[OpenApiConfig.java](src/main/java/com/devsecops/usermgmt/config/OpenApiConfig.java)** — configure la documentation interactive Swagger/OpenAPI.

**[RateLimitProperties.java](src/main/java/com/devsecops/usermgmt/config/RateLimitProperties.java)** — lit les propriétés `rate-limit.*` (capacité, durée de recharge), y compris une entrée orpheline pour `forgot-password` (voir [LISTE_DES_BUGS.md — BUG #3](LISTE_DES_BUGS.md)).

**[application.properties](src/main/resources/application.properties)**
- *Rôle* : fichier de configuration principal — connexion à la base de données, secret et expiration JWT, règles de rate limiting, exposition Actuator/Prometheus.
- *Quand* : lu une seule fois, au démarrage de l'application.
- *Qui l'appelle* : Spring Boot (chargement automatique de la configuration).
- *Si il disparaît* : l'application ne saurait pas comment se connecter à la base ni comment signer les jetons — elle ne démarrerait pas.

**[application-staging.properties](src/main/resources/application-staging.properties)**
- *Rôle* : surcharge de configuration pour l'environnement de "staging" (pré-production) — notamment `ddl-auto=validate` (au lieu de `update`) et `show-sql=true`.
- *Quand* : chargé uniquement quand le profil Spring `staging` est actif.
- *Qui l'appelle* : Spring Boot, si `SPRING_PROFILES_ACTIVE=staging`.
- *Si il disparaît* : le profil `staging` se comporterait comme le profil par défaut — perte d'une configuration plus prudente pour la pré-production.

**[logback-spring.xml](src/main/resources/logback-spring.xml)**
- *Rôle* : configure la sortie des logs au format JSON structuré (via `logstash-logback-encoder`), avec les champs `traceId`/`requestId`/`path`/`method`/`duration` issus du MDC — pensé pour être collecté par une stack ELK (Elasticsearch/Logstash/Kibana).
- *Quand* : initialisé au démarrage, utilisé à chaque ligne de log.
- *Qui l'appelle* : le framework de logging (SLF4J/Logback), sollicité par tout le code via `@Slf4j`.
- *Si il disparaît* : les logs reviendraient au format texte brut par défaut — beaucoup plus difficile à indexer/rechercher dans un outil de supervision centralisé.

### 4.8 Fichiers de build et d'exécution

**[pom.xml](pom.xml)** — la "liste de courses" Maven : déclare toutes les dépendances (Spring Boot, Spring Security, JJWT, Bucket4j, Testcontainers, etc.), la version de Java (21), et la configuration de build/tests (Surefire exclut les `*IntegrationTest` du build standard).

**[Dockerfile](Dockerfile)** — recette de construction d'une image Docker en plusieurs étapes (build avec Maven, puis exécution avec un utilisateur non-root `appuser`), avec un `HEALTHCHECK` qui interroge `/health` (route qui n'existe pas — voir [LISTE_DES_BUGS.md — BUG #2](LISTE_DES_BUGS.md)).

**[docker-compose.yml](docker-compose.yml)** — orchestration locale de deux services (l'API et PostgreSQL), avec un `healthcheck` pointant lui aussi vers `/health`.

**[README.md](README.md)** — documentation du projet pour les développeurs : prérequis (mentionne Java 17, alors que le `pom.xml` exige Java 21 — voir [LISTE_DES_BUGS.md — BUG #5](LISTE_DES_BUGS.md)), tableau des routes (mentionne `/health`), structure du projet (ne mentionne pas `k8s/`/`argocd/`/`monitoring/`).

**[VULNERABILITIES.md](VULNERABILITIES.md)** — le protocole pédagogique des 10 vulnérabilités injectables `INJ-01` à `INJ-10`, chacune documentée avec sa catégorie OWASP, son fichier, son score CVSS et la manière de l'activer pour valider les outils de sécurité (voir §5.6 plus bas).

### 4.9 Infrastructure (k8s/, argocd/, monitoring/, .github/)

**`k8s/*.yaml`** — manifestes Kubernetes (`namespace`, `api-deployment` avec 2 réplicas, `services`, `postgres-*`, `secrets`, `configmap`, `ingress`). *Si ils disparaissent* : impossible de déployer l'application sur un cluster Kubernetes.

**`argocd/*.yaml`** — définitions d'« Application » ArgoCD pointant vers ce dépôt Git, pour un déploiement continu automatique (GitOps). *Si ils disparaissent* : il faudrait redéployer manuellement à chaque changement.

**`monitoring/api-servicemonitor.yaml`** — branche l'opérateur Prometheus (kube-prometheus-stack) sur l'endpoint `/actuator/prometheus` de l'API (label `release: kube-prom` obligatoire pour être sélectionné). *Si il disparaît* : Prometheus ne collecterait plus les métriques de l'application.

**`monitoring/prometheus-config.yaml`** — fichier de configuration Prometheus "classique" (`scrape_configs`), mais **non utilisé** par le déploiement réel qui repose sur l'opérateur Prometheus et les CRD `ServiceMonitor` (voir [LISTE_DES_BUGS.md — BUG #4](LISTE_DES_BUGS.md)). *Si il disparaît* : aucun changement dans le comportement réel du système de supervision.

**[.github/workflows/devsecops-pipeline.yml](.github/workflows/devsecops-pipeline.yml)** — la chaîne CI/CD : 3 jobs (build + analyse statique SonarCloud + analyse de composants Trivy ; tests dynamiques OWASP ZAP ; reporting). *Si il disparaît* : plus aucune vérification automatique de sécurité/qualité à chaque modification du code — retour à des contrôles entièrement manuels.

---

## 5. Explication des technologies utilisées

### 5.1 Spring Boot / Java

#### Explication simple
Java est un langage de programmation très répandu, en particulier pour les "moteurs" de grandes applications. Spring Boot est une boîte à outils qui permet de construire ce genre de moteur sans repartir de zéro : elle fournit déjà des briques toutes faites pour recevoir des requêtes web, parler à une base de données, sécuriser l'accès, etc.

#### Explication technique
Le projet utilise **Spring Boot 3.2.5** sur **Java 21** (LTS) ([pom.xml](pom.xml)). Spring Boot fournit l'auto-configuration, l'inversion de contrôle (injection de dépendances via `@Autowired`/constructeurs), un serveur Tomcat embarqué, et l'intégration avec Spring Data JPA, Spring Security, Spring Validation, Actuator, etc.

### 5.2 API REST

#### Explication simple
Une API REST, c'est un ensemble d'"adresses" (URLs) auxquelles on peut envoyer des demandes (par exemple "donne-moi mon profil") et qui répondent avec des données structurées, généralement au format JSON — un peu comme remplir un formulaire et recevoir une réponse écrite dans un format que les programmes comprennent facilement.

#### Explication technique
Le projet expose 14 routes HTTP organisées par ressource (`/api/auth/*`, `/api/users/*`, `/api/admin/*`), suit les conventions REST (verbes HTTP `GET`/`POST`/`PUT`/`DELETE`, codes de statut sémantiques), et échange des représentations **JSON** sérialisées/désérialisées via Jackson, en s'appuyant sur des DTOs dédiés (voir §4.6) pour ne jamais exposer directement les entités JPA.

### 5.3 Base de données PostgreSQL et JPA/Hibernate

#### Explication simple
PostgreSQL, c'est l'endroit où sont rangées durablement toutes les informations (les comptes utilisateurs, les jetons...) — comme une grande armoire à fiches qui ne s'efface pas quand on éteint l'ordinateur. JPA/Hibernate est l'outil qui permet au programme Java de "parler" à cette armoire sans écrire lui-même le langage propre aux bases de données (le SQL) — il transforme automatiquement les objets Java en lignes de tables, et inversement.

#### Explication technique
La persistance repose sur **Spring Data JPA** avec **Hibernate** comme fournisseur JPA, connecté à **PostgreSQL** via JDBC. Le schéma comprend au minimum les tables `users`, `refresh_tokens`, `email_verification_tokens` (déduites des entités, voir §4.5). En profil par défaut, `spring.jpa.hibernate.ddl-auto=update` génère/adapte automatiquement le schéma — pratique en développement, mais déconseillé en production (voir [AMELIORATIONS_RECOMMANDEES.md §5](AMELIORATIONS_RECOMMANDEES.md), qui recommande Flyway/Liquibase). Les requêtes utilisent des méthodes dérivées ou des paramètres liés (`@Param`), ce qui élimine naturellement le risque d'injection SQL par concaténation de chaînes.

### 5.4 Authentification JWT et BCrypt

#### Explication simple
Quand vous vous connectez avec votre mot de passe, le système ne va pas vous redemander ce mot de passe à chaque fois que vous cliquez sur quelque chose — ce serait fastidieux et risqué. À la place, il vous donne un **badge temporaire** (le jeton JWT), que vous présentez ensuite à chaque demande. Ce badge est signé numériquement, comme un sceau officiel : impossible de le falsifier sans connaître le secret du système.

Le mot de passe, lui, n'est **jamais stocké en clair** : il est transformé par une fonction à sens unique (BCrypt) — un peu comme passer une feuille dans une déchiqueteuse dont on ne peut pas reconstituer l'original, mais dont on peut vérifier qu'une nouvelle feuille produirait les mêmes confettis.

#### Explication technique
- **JWT (JSON Web Token)**, signé en **HS256** via la bibliothèque **jjwt 0.12.5** ([JwtTokenProvider.java](src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java)). Le secret doit faire au moins 32 caractères (vérifié au démarrage via `@PostConstruct`). Le token contient le nom d'utilisateur, une date d'expiration (`jwt.expiration=900000` ms = 15 minutes, voir [application.properties](src/main/resources/application.properties)) et un identifiant unique (`jti`).
- **BCrypt** (`PasswordEncoder` de Spring Security) hache les mots de passe avec un sel aléatoire intégré — la comparaison se fait par re-hachage, jamais par déchiffrement (impossible par construction).
- **Refresh tokens** : un jeton de longue durée, stocké en base, **un seul actif par utilisateur**, avec **rotation** systématique à chaque utilisation — l'ancien jeton est immédiatement invalidé ([RefreshTokenService.java](src/main/java/com/devsecops/usermgmt/service/RefreshTokenService.java)). Cela limite fortement l'impact d'un vol de jeton (rejeu impossible une fois le jeton tourné).

### 5.5 Mécanismes de défense OWASP actifs

#### Explication simple
OWASP est une organisation qui recense les pièges de sécurité les plus fréquents dans les applications web (un peu comme une liste des "dix façons les plus courantes de se faire cambrioler une maison"). Ce projet a mis en place des protections concrètes contre plusieurs de ces pièges.

#### Explication technique

| Mécanisme | Catégorie OWASP | Où il est implémenté |
|---|---|---|
| **BOLA** (Broken Object Level Authorization) — un utilisateur ne peut accéder qu'à ses propres données | A01 Broken Access Control | `verifyOwnershipOrAdmin` ([UserService.java:135-143](src/main/java/com/devsecops/usermgmt/service/UserService.java#L135)) |
| **BFLA** (Broken Function Level Authorization) — seules les routes admin sont accessibles aux admins | A01 Broken Access Control | `@PreAuthorize("hasRole('ADMIN')")` + règles d'URL dans [SecurityConfig.java](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java) |
| **Mass Assignment** — un utilisateur ne peut pas s'auto-attribuer le rôle admin | A01 / A08 | `@JsonIgnore` sur `role`/`password` ([User.java](src/main/java/com/devsecops/usermgmt/entity/User.java)), rôle forcé à `ROLE_USER` à l'inscription ([AuthService.java:65](src/main/java/com/devsecops/usermgmt/service/AuthService.java#L65)) |
| **CORS restrictif** — seul le frontend légitime peut appeler l'API depuis un navigateur | A05 Security Misconfiguration | Origine unique `http://localhost:3000` dans [SecurityConfig.java](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java) |
| **Rate limiting** — protection contre le bruteforce et les abus | A07 Identification and Authentication Failures | [RateLimitingFilter.java](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java) (Bucket4j, par IP) |
| **Mots de passe hachés** — jamais stockés en clair | A02 Cryptographic Failures | BCrypt via `PasswordEncoder` |
| **Jetons signés et expirables** | A02 / A07 | JWT HS256 avec expiration et `jti` |

Pour l'analyse complète (avec citations précises), voir [AUDIT_COMPLET.md §2.2.a](AUDIT_COMPLET.md).

### 5.6 Le protocole `VULNERABILITIES.md` (INJ-01 à INJ-10)

#### Explication simple
Imaginez que, pour tester un détecteur de fumée, vous ayez besoin d'un peu de fumée contrôlée — sans déclencher un véritable incendie. C'est exactement le rôle de ce fichier : il documente **dix interrupteurs** que l'on peut activer un par un, chacun introduisant volontairement une faille de sécurité connue, pour vérifier que les outils automatiques (analyseurs de code, scanners de vulnérabilités) la détectent bien.

**Ces dix failles ne sont PAS actives dans le code aujourd'hui.** Ce sont des "interrupteurs éteints", clairement repérés par des commentaires `// INJ-XX` dans le code source, prêts à être activés pour une démonstration pédagogique.

#### Explication technique
[VULNERABILITIES.md](VULNERABILITIES.md) documente 10 vulnérabilités injectables (`INJ-01` à `INJ-10`), chacune associée à : une catégorie OWASP, un fichier et une ligne précis, un score CVSS, et la modification de code minimale qui l'activerait. Des commentaires-marqueurs sont déjà présents dans le code, par exemple :
- `// INJ-02` dans [JwtTokenProvider.java](src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java) (sur `.setExpiration()`)
- `// INJ-03` dans [UserRepository.java](src/main/java/com/devsecops/usermgmt/repository/UserRepository.java) (point d'injection SQL potentiel)
- `// INJ-04` dans [UserService.java](src/main/java/com/devsecops/usermgmt/service/UserService.java) (sur la défense BOLA)
- `// INJ-05` dans [User.java](src/main/java/com/devsecops/usermgmt/entity/User.java) (sur `@JsonIgnore`)
- `// INJ-06` et `// INJ-08` dans [SecurityConfig.java](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java) (CORS, règles d'accès)

Ce protocole est une **preuve de maîtrise** : il montre que l'auteur du projet comprend non seulement comment se défendre, mais aussi comment une faille apparaît concrètement dans le code — condition nécessaire pour configurer et valider une chaîne d'outils SAST/DAST/SCA (voir [AUDIT_COMPLET.md §2.2.c](AUDIT_COMPLET.md) pour le tableau complet).

### 5.7 Conteneurisation : Docker et Kubernetes

#### Explication simple
Un conteneur, c'est comme une boîte de transport standardisée : on y met le programme avec tout ce dont il a besoin pour fonctionner (un peu de système, ses bibliothèques...), et cette boîte fonctionnera de la même façon sur n'importe quel ordinateur compatible — fini le "ça marche sur ma machine mais pas ailleurs". Kubernetes est un "chef d'orchestre" qui sait déployer, surveiller et redémarrer automatiquement ces boîtes à grande échelle, sur plusieurs machines.

#### Explication technique
- **Docker** : [Dockerfile](Dockerfile) en *multi-stage build* (étape de compilation Maven, puis image d'exécution allégée), exécution en utilisateur non-root `appuser` (bonne pratique de sécurité), avec un `HEALTHCHECK`. [docker-compose.yml](docker-compose.yml) orchestre l'API et PostgreSQL pour un usage local.
- **Kubernetes** (`k8s/`) : manifestes pour `Namespace`, `Deployment` (2 réplicas pour l'API), `Service`, `Secret`, `ConfigMap`, `Ingress`, et le déploiement de PostgreSQL.

### 5.8 GitOps avec ArgoCD

#### Explication simple
Plutôt que de déployer "à la main" à chaque mise à jour, on peut dire à un robot : "surveille ce dépôt Git, et dès que son contenu change, applique automatiquement ces changements sur le serveur". C'est ce que fait ArgoCD — le dépôt Git devient la "source de vérité" unique.

#### Explication technique
Le dossier `argocd/` contient des manifestes `Application` ArgoCD qui pointent vers ce dépôt et synchronisent automatiquement l'état du cluster Kubernetes avec l'état déclaré dans Git (modèle **GitOps**) — traçabilité complète et possibilité de revenir en arrière via `git revert`.

### 5.9 Supervision : Prometheus, Grafana, Actuator

#### Explication simple
Une fois le programme en fonctionnement, il faut pouvoir vérifier qu'il va bien : combien de requêtes traite-t-il, est-il lent, plante-t-il parfois ? C'est le rôle des outils de supervision — un peu comme les capteurs et jauges du tableau de bord d'une voiture.

#### Explication technique
- **Spring Boot Actuator** + **Micrometer** exposent des métriques au format Prometheus sur `/actuator/prometheus` (configuré dans [application.properties](src/main/resources/application.properties)).
- **`ServiceMonitor`** ([monitoring/api-servicemonitor.yaml](monitoring/api-servicemonitor.yaml)) est une ressource personnalisée (CRD) qui indique à l'opérateur Prometheus (déployé via le chart Helm `kube-prometheus-stack`) où aller chercher ces métriques (avec le label obligatoire `release: kube-prom`).
- **Grafana** affiche ensuite ces métriques sous forme de tableaux de bord visuels.
- Le fichier `monitoring/prometheus-config.yaml` représente une configuration Prometheus "classique" mais n'est **pas branché** au déploiement réel (voir [LISTE_DES_BUGS.md — BUG #4](LISTE_DES_BUGS.md)).

### 5.10 Logs structurés (JSON) et corrélation (MDC/traceId)

#### Explication simple
Quand des milliers de requêtes arrivent en même temps, retrouver "que s'est-il passé pour CETTE demande précise" dans un océan de lignes de texte est très difficile. La solution : attribuer à chaque demande un **numéro de suivi unique** (un peu comme un numéro de colis), et écrire les journaux (logs) dans un format que les machines peuvent indexer et filtrer rapidement (le JSON), plutôt qu'en texte libre.

#### Explication technique
[RequestLoggingFilter.java](src/main/java/com/devsecops/usermgmt/filter/RequestLoggingFilter.java) génère/propage un `traceId`/`requestId`, mesure la durée et les place dans le **MDC** (Mapped Diagnostic Context) de SLF4J. [logback-spring.xml](src/main/resources/logback-spring.xml) configure `logstash-logback-encoder` pour produire des logs **JSON structurés** prêts à être ingérés par une stack **ELK** (Elasticsearch/Logstash/Kibana), avec ces champs de corrélation disponibles pour la recherche et le filtrage.

### 5.11 CI/CD : GitHub Actions, SonarCloud, Trivy, OWASP ZAP

#### Explication simple
Plutôt que de vérifier "à la main" que le code compile, que les tests passent, et qu'il n'y a pas de faille de sécurité évidente, on automatise tout cela : à chaque modification du code envoyée sur le dépôt, une chaîne d'outils se déclenche toute seule et produit un rapport.

#### Explication technique
[.github/workflows/devsecops-pipeline.yml](.github/workflows/devsecops-pipeline.yml) définit 3 jobs :
1. **build** : compilation Maven, exécution des tests, **analyse statique de code (SAST)** avec **SonarCloud**, **analyse de composition logicielle (SCA)** avec **Trivy** (recherche de vulnérabilités connues dans les dépendances et l'image Docker) ;
2. **dast** : tests dynamiques de sécurité avec **OWASP ZAP** contre l'application en cours d'exécution (attend que `/health` réponde — référence à corriger, voir [LISTE_DES_BUGS.md — BUG #2](LISTE_DES_BUGS.md)) ;
3. **reporting** : agrégation et publication des résultats.

### 5.12 Tests : JUnit, Mockito, MockMvc, Testcontainers

#### Explication simple
Avant de livrer un programme, on veut être sûr qu'il fait bien ce qu'on attend de lui — et qu'une modification future ne casse pas ce qui fonctionnait avant. Pour cela, on écrit des "mini-vérifications" automatiques (les tests), qui peuvent être rejouées à volonté en quelques secondes ou minutes.

#### Explication technique
La suite de tests combine plusieurs niveaux :
- **Tests unitaires** (JUnit 5 + Mockito) : vérifient une classe isolée (ex. `JwtTokenProviderTest`, `UserServiceTest`) en simulant ses dépendances.
- **Slice tests** (`@WebMvcTest` + `MockMvc` + `SecurityMockMvcRequestPostProcessors.user(...)`) : testent une couche contrôleur en isolant la sécurité — **mais contournent le vrai filtre JWT**, ce qui explique pourquoi le bug critique (§4.2, BUG #1) n'a pas été détecté par ces tests.
- **Tests d'intégration** (`*IntegrationTest`, basés sur [AbstractIntegrationTest.java](src/test/java/com/devsecops/usermgmt/AbstractIntegrationTest.java)) : démarrent une **vraie** application Spring avec un **vrai PostgreSQL** via **Testcontainers** (conteneur Docker éphémère) — ils traversent le vrai filtre JWT et auraient pu détecter le bug s'ils avaient testé un scénario "requête anonyme sur une route publique" (voir [AMELIORATIONS_RECOMMANDEES.md §4](AMELIORATIONS_RECOMMANDEES.md)).
- Le `pom.xml` configure Surefire pour **exclure** les `*IntegrationTest` du build standard (ils sont plus lents — démarrage de conteneurs).

---

## 6. Parcours pas à pas des fonctionnalités

### 6.1 Inscription (`POST /api/auth/register`)

1. L'utilisateur envoie un nom d'utilisateur, un e-mail et un mot de passe.
2. [AuthController](src/main/java/com/devsecops/usermgmt/controller/AuthController.java) reçoit la requête, la convertit en `RegisterRequest` (avec validations `@NotBlank`/`@Email`/`@Size`).
3. [AuthService.register](src/main/java/com/devsecops/usermgmt/service/AuthService.java) vérifie que le nom d'utilisateur et l'e-mail ne sont pas déjà pris, hache le mot de passe avec BCrypt, **force le rôle à `ROLE_USER`** (impossible de s'auto-promouvoir admin — défense Mass Assignment, [ligne 65](src/main/java/com/devsecops/usermgmt/service/AuthService.java#L65)), puis sauvegarde via `UserRepository`.
4. La réponse renvoie un `UserResponse` (jamais le mot de passe ni le rôle brut, grâce à `@JsonIgnore`).
5. *(Théoriquement, un e-mail de vérification serait envoyé ici — en pratique cette étape n'est pas câblée, voir §4.4 et BUG #6.)*

### 6.2 Connexion (`POST /api/auth/login`)

1. L'utilisateur envoie son nom d'utilisateur et son mot de passe.
2. Les filtres s'exécutent dans l'ordre : `RequestLoggingFilter` → `RateLimitingFilter` (vérifie le quota par IP) → `JwtAuthenticationFilter` (aucun jeton présent ici, donc rien à faire) → `SecurityConfig` autorise la route car elle est dans `permitAll`.
3. [AuthService.login](src/main/java/com/devsecops/usermgmt/service/AuthService.java) délègue à l'`AuthenticationManager` de Spring Security, qui appelle `UserDetailsServiceImpl` pour charger l'utilisateur, puis compare le mot de passe haché.
4. Si correct, [JwtTokenProvider](src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java) génère un jeton JWT (15 minutes de validité) et [RefreshTokenService](src/main/java/com/devsecops/usermgmt/service/RefreshTokenService.java) crée un refresh token (longue durée, stocké en base, ancien jeton — s'il existait — révoqué).
5. La réponse (`JwtResponse`) contient le jeton d'accès et le refresh token.

### 6.3 Accès à une ressource protégée (`GET /api/users/me`)

1. Le frontend envoie la requête avec l'en-tête `Authorization: Bearer <jeton>`.
2. `RequestLoggingFilter` puis `RateLimitingFilter` s'exécutent.
3. `JwtAuthenticationFilter` extrait le jeton, le valide via `JwtTokenProvider`, charge l'utilisateur via `UserDetailsServiceImpl`, et **peuple** le `SecurityContextHolder`.
4. `SecurityConfig` constate que le `SecurityContext` contient un utilisateur authentifié et autorise l'accès à la route `authenticated()`.
5. [UserController](src/main/java/com/devsecops/usermgmt/controller/UserController.java) appelle [UserService](src/main/java/com/devsecops/usermgmt/service/UserService.java), qui vérifie via `verifyOwnershipOrAdmin` que l'utilisateur demande bien **ses propres** informations (défense BOLA).
6. La réponse est transformée en `UserResponse` via `UserMapper` et renvoyée.

### 6.4 Rafraîchissement du jeton (`POST /api/auth/refresh`)

1. Le frontend envoie le refresh token (quand le jeton d'accès de 15 minutes a expiré).
2. [AuthService.refreshToken](src/main/java/com/devsecops/usermgmt/service/AuthService.java) délègue à `RefreshTokenService`, qui vérifie que le jeton existe, n'est pas expiré, et correspond au **dernier** jeton émis pour cet utilisateur.
3. **Rotation** : l'ancien refresh token est immédiatement invalidé, un nouveau est créé et stocké.
4. Un nouveau jeton d'accès JWT est généré.
5. La réponse (`TokenRefreshResponse`) renvoie les deux nouveaux jetons.

### 6.5 Déconnexion (`POST /api/auth/logout`)

1. Le frontend envoie le refresh token actuel.
2. `AuthService.logout` appelle `RefreshTokenService` pour **révoquer** ce jeton (suppression/invalidation en base).
3. Le jeton d'accès JWT, lui, reste valide jusqu'à son expiration naturelle (15 minutes maximum) — c'est une caractéristique connue des JWT stateless (pas de "liste noire" de jetons d'accès dans ce projet).

### 6.6 Administration des utilisateurs (`GET /api/admin/users`)

1. Un utilisateur avec le rôle `ROLE_ADMIN` envoie la requête avec son jeton.
2. `JwtAuthenticationFilter` peuple le contexte avec ses autorités (`ROLE_ADMIN`).
3. `SecurityConfig` vérifie que la route `/api/admin/**` exige `hasRole("ADMIN")` — défense **BFLA**, renforcée par `@PreAuthorize` sur le contrôleur.
4. [AdminController](src/main/java/com/devsecops/usermgmt/controller/AdminController.java) appelle `UserService.getAllUsersPaged`, qui plafonne la taille de page à 100 (protection contre les requêtes trop volumineuses).
5. La réponse est une `PageResponse<UserResponse>` paginée.

### 6.7 Tentative d'accès au profil d'un autre utilisateur (scénario de défense BOLA)

1. L'utilisateur A (non admin) envoie `GET /api/users/{id-de-B}` avec son propre jeton valide.
2. Les filtres et `SecurityConfig` autorisent la requête (l'utilisateur est bien authentifié).
3. **Mais** `UserService.verifyOwnershipOrAdmin` détecte que l'identifiant demandé ne correspond pas à celui de l'utilisateur authentifié, et qu'il n'a pas le rôle admin.
4. Une `AccessDeniedException` est levée, interceptée par `GlobalExceptionHandler`, qui renvoie un `403 Forbidden` — **sans jamais révéler si l'utilisateur B existe ou non**.

---

## 7. Schémas simples en mode texte

### 7.1 Vue d'ensemble du système

```
                 ┌─────────────┐
                 │ Utilisateur │
                 └──────┬──────┘
                        │ utilise
                 ┌──────▼──────┐
                 │  Frontend   │   (séparé, non présent dans ce dépôt)
                 │ (port 3000) │
                 └──────┬──────┘
                        │ HTTP + JSON + JWT
                 ┌──────▼─────────────────────────────┐
                 │   Backend (api-devsecops-usermgmt) │
                 │   port 8080                         │
                 │  Filtres → Sécurité → Contrôleurs   │
                 │   → Services → Repositories         │
                 └──────┬─────────────────────┬────────┘
                        │ SQL/JDBC            │ métriques /actuator/prometheus
                 ┌──────▼──────┐       ┌──────▼─────────┐
                 │ PostgreSQL  │       │ Prometheus +   │
                 │ (users, …)  │       │ Grafana        │
                 └─────────────┘       └────────────────┘
```

### 7.2 Chaîne de filtres pour chaque requête HTTP

```
Requête HTTP entrante
        │
        ▼
┌───────────────────────┐
│ RequestLoggingFilter  │  → attribue traceId/requestId, démarre le chrono
└──────────┬────────────┘
           ▼
┌───────────────────────┐
│ RateLimitingFilter    │  → vérifie le quota par IP (Bucket4j)
└──────────┬────────────┘
           ▼
┌───────────────────────┐
│ JwtAuthenticationFilter│ → si jeton valide : authentifie l'utilisateur
└──────────┬────────────┘    (ne rejette JAMAIS lui-même la requête)
           ▼
┌───────────────────────┐
│ SecurityFilterChain   │  → décide : autoriser / 401 / 403
│   (SecurityConfig)    │     selon permitAll / hasRole / authenticated
└──────────┬────────────┘
           ▼
   Contrôleur → Service → Repository → Base de données
           │
           ▼
   Réponse JSON (DTO, jamais le mot de passe)
```

### 7.3 Cycle de vie des jetons (access + refresh)

```
Connexion
   │
   ├──▶ JWT (access token)  — courte durée (15 min) — utilisé à chaque requête
   │
   └──▶ Refresh token — longue durée — stocké en base, UN SEUL actif par utilisateur
            │
            ├── utilisé pour /refresh → ROTATION : ancien invalidé, nouveau émis
            │                              + nouveau JWT généré
            │
            └── utilisé pour /logout → révoqué définitivement
```

### 7.4 Pipeline CI/CD (GitHub Actions)

```
Push / Pull Request
        │
        ▼
┌─────────────────────────────┐
│ Job "build"                 │
│  - compilation Maven        │
│  - exécution des tests      │
│  - SAST : SonarCloud        │
│  - SCA  : Trivy             │
└──────────────┬──────────────┘
               ▼
┌─────────────────────────────┐
│ Job "dast"                  │
│  - démarre l'application    │
│  - DAST : OWASP ZAP         │
└──────────────┬──────────────┘
               ▼
┌─────────────────────────────┐
│ Job "reporting"             │
│  - agrège et publie les     │
│    rapports                 │
└─────────────────────────────┘
```

---

## 8. Questions probables du jury (avec réponses simples et techniques)

> 56 questions couvrant l'architecture, la sécurité, les tests, l'infrastructure et les choix de conception — y compris les points faibles identifiés pendant cet audit (à assumer plutôt qu'à cacher : c'est ce qui démontre le recul critique).

### A. Architecture générale

**Question : Que fait ce projet, en une phrase ?**
- *Réponse simple* : C'est le moteur (l'API) d'un système de gestion de comptes utilisateurs : inscription, connexion, gestion de profil, administration — avec un accent fort mis sur la sécurité et l'automatisation.
- *Réponse technique* : Une API REST Spring Boot 3.2.5/Java 21 avec authentification JWT, persistance PostgreSQL, défenses OWASP actives, suite de tests à plusieurs niveaux, et chaîne complète CI/CD + déploiement Kubernetes/GitOps/supervision.

**Question : Pourquoi avoir choisi une architecture en couches (controller/service/repository) ?**
- *Réponse simple* : Pour que chaque partie du programme ait un rôle bien précis et que ce soit plus facile à comprendre, modifier et tester sans tout casser.
- *Réponse technique* : Cela respecte le principe de séparation des responsabilités (SRP) : les contrôleurs gèrent le protocole HTTP, les services la logique métier, les repositories la persistance — ce qui facilite les tests unitaires (mock d'une seule couche à la fois) et limite la propagation des changements.

**Question : Où est le frontend de cette application ?**
- *Réponse simple* : Il n'est pas dans ce dépôt. Ce projet est uniquement le "moteur" ; l'écran que verrait l'utilisateur serait une application séparée qui communique avec ce moteur.
- *Réponse technique* : Aucune trace de frontend dans l'arborescence ; la seule preuve de son existence prévue est la configuration CORS dans [SecurityConfig.java](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java) restreinte à `http://localhost:3000` (port typique d'un frontend React/Vue/Angular en développement).

**Question : Pourquoi l'API est-elle "stateless" (sans état) ?**
- *Réponse simple* : Pour que le serveur n'ait pas besoin de "se souvenir" de qui est connecté entre deux requêtes — chaque demande porte sa propre preuve d'identité (le jeton).
- *Réponse technique* : L'authentification JWT permet de ne stocker aucune session côté serveur (`SessionCreationPolicy.STATELESS`), ce qui simplifie le passage à l'échelle horizontale (plusieurs réplicas peuvent traiter indifféremment n'importe quelle requête sans synchronisation de session).

**Question : Comment les différentes couches communiquent-elles entre elles ?**
- *Réponse simple* : Chacune appelle la suivante directement, comme une chaîne de personnes qui se passent un dossier : le contrôleur passe au service, le service au repository, et chacun ne s'occupe que de sa partie.
- *Réponse technique* : Par injection de dépendances Spring (constructeurs `@Autowired` implicites via Lombok/Spring), chaque couche dépend d'une abstraction (interface de repository, classe de service) résolue au démarrage par le contexte d'application.

### B. Sécurité — JWT et authentification

**Question : Comment fonctionne l'authentification dans ce projet ?**
- *Réponse simple* : L'utilisateur prouve son identité une fois (nom d'utilisateur + mot de passe), reçoit un badge numérique temporaire (le jeton), et présente ce badge à chaque demande suivante.
- *Réponse technique* : Authentification stateless par JWT signé HS256 ([JwtTokenProvider.java](src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java)), validé à chaque requête par [JwtAuthenticationFilter](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java), qui peuple le `SecurityContextHolder` pour la durée de la requête.

**Question : Pourquoi le jeton JWT n'a-t-il une durée de vie que de 15 minutes ?**
- *Réponse simple* : Pour que si quelqu'un vole ce badge, il ne puisse l'utiliser que pendant un court moment.
- *Réponse technique* : `jwt.expiration=900000` ms (15 min, [application.properties](src/main/resources/application.properties)) limite la fenêtre d'exploitation d'un jeton volé ; le refresh token (longue durée, lui, stocké et révocable côté serveur) permet de renouveler l'accès sans réauthentification complète.

**Question : Comment les mots de passe sont-ils protégés ?**
- *Réponse simple* : Ils ne sont jamais stockés tels quels — ils passent par une transformation à sens unique impossible à inverser, et seule une nouvelle tentative de connexion peut être comparée à cette empreinte.
- *Réponse technique* : Hachage **BCrypt** (`PasswordEncoder` Spring Security), qui intègre un sel aléatoire et un facteur de coût ajustable, rendant les attaques par table arc-en-ciel et par force brute hors ligne très coûteuses.

**Question : Qu'est-ce qu'un "refresh token" et pourquoi en avoir un en plus du JWT ?**
- *Réponse simple* : C'est un second badge, valable plus longtemps, qui sert uniquement à obtenir un nouveau badge "court terme" sans avoir à retaper son mot de passe.
- *Réponse technique* : Le refresh token, stocké en base ([RefreshToken.java](src/main/java/com/devsecops/usermgmt/entity/RefreshToken.java)), permet de révoquer l'accès côté serveur (contrairement au JWT, qui est valide jusqu'à expiration une fois émis) — c'est le point de contrôle qui compense le caractère stateless du JWT.

**Question : Qu'est-ce que la "rotation" des refresh tokens, et pourquoi est-ce important ?**
- *Réponse simple* : Chaque fois qu'on utilise ce badge longue durée pour obtenir un nouveau badge court terme, l'ancien badge longue durée est immédiatement détruit et remplacé par un nouveau.
- *Réponse technique* : [RefreshTokenService](src/main/java/com/devsecops/usermgmt/service/RefreshTokenService.java) garantit qu'un seul refresh token est actif par utilisateur et le remplace à chaque `/refresh` : si un attaquant et l'utilisateur légitime tentent tous deux d'utiliser le même jeton volé, le second à se présenter échoue — signal de compromission détectable.

**Question : Que se passe-t-il si je perds mon jeton JWT (vol, fuite) ?**
- *Réponse simple* : Il reste valable au maximum 15 minutes — passé ce délai, il ne fonctionne plus, même si personne ne l'a "désactivé" à la main.
- *Réponse technique* : Comme le système est stateless, il n'existe pas de liste noire de jetons d'accès révoqués ; la seule mitigation est la courte durée de vie. C'est un compromis assumé typique des architectures JWT stateless (voir [AMELIORATIONS_RECOMMANDEES.md](AMELIORATIONS_RECOMMANDEES.md) pour les pistes de durcissement).

**Question : Pourquoi avoir choisi HS256 (signature symétrique) plutôt que RS256 (asymétrique) ?**
- *Réponse simple* : Parce qu'un seul service (cette API) émet et vérifie les jetons — pas besoin de partager une clé publique avec d'autres services.
- *Réponse technique* : HS256 utilise une clé secrète partagée pour signer et vérifier ; c'est suffisant et plus simple ici car l'émission et la validation sont effectuées par le même service. RS256 (clé privée/publique) serait pertinent si plusieurs services indépendants devaient vérifier les jetons sans pouvoir en émettre.

### C. Sécurité — autorisation et défenses OWASP

**Question : Qu'est-ce que la faille BOLA, et comment ce projet s'en protège-t-il ?**
- *Réponse simple* : C'est le risque qu'un utilisateur accède aux informations d'un autre simplement en changeant un numéro dans l'adresse (par exemple `/users/12` → `/users/13`). Ici, le système vérifie systématiquement que vous ne consultez que vos propres informations (sauf si vous êtes administrateur).
- *Réponse technique* : Défense implémentée dans `verifyOwnershipOrAdmin` ([UserService.java:135-143](src/main/java/com/devsecops/usermgmt/service/UserService.java#L135)) — compare l'identifiant de la ressource demandée à celui de l'utilisateur authentifié (issu du `SecurityContext`), et autorise l'accès cross-utilisateur uniquement pour `ROLE_ADMIN`. C'est l'OWASP API Security Top 10 #1 (BOLA/IDOR).

**Question : Qu'est-ce que la faille BFLA, et comment ce projet s'en protège-t-il ?**
- *Réponse simple* : C'est le risque qu'un utilisateur normal accède à des fonctions réservées aux administrateurs (par exemple supprimer un compte) simplement en appelant directement la bonne adresse.
- *Réponse technique* : Double défense — règles d'URL dans `SecurityFilterChain` (`.requestMatchers("/api/admin/**").hasRole("ADMIN")`) ET annotation `@PreAuthorize("hasRole('ADMIN')")` sur [AdminController](src/main/java/com/devsecops/usermgmt/controller/AdminController.java) — défense en profondeur (si l'une est mal configurée, l'autre reste active).

**Question : Qu'est-ce que le "Mass Assignment", et comment ce projet s'en protège-t-il ?**
- *Réponse simple* : C'est le risque qu'un utilisateur malicieux glisse, dans son formulaire d'inscription, un champ supplémentaire du genre "rôle: administrateur" pour s'auto-promouvoir.
- *Réponse technique* : Triple barrière — (1) `RegisterRequest` ne contient pas de champ `role` (impossible de l'envoyer), (2) `AuthService.register` force `Role.ROLE_USER` ([ligne 65](src/main/java/com/devsecops/usermgmt/service/AuthService.java#L65)) sans jamais lire un rôle venant du client, (3) `@JsonIgnore` sur `User.role`/`User.password` empêche toute fuite ou écrasement via (dé)sérialisation JSON directe.

**Question : Pourquoi le CSRF est-il désactivé ? N'est-ce pas dangereux ?**
- *Réponse simple* : Le CSRF est une protection utile quand le navigateur envoie automatiquement des cookies d'authentification. Ici, l'authentification se fait via un jeton explicitement ajouté à chaque requête par le code du frontend (pas un cookie automatique) — donc cette attaque ne s'applique pas de la même manière, et désactiver cette protection précise est un choix justifié, pas un oubli.
- *Réponse technique* : Le CSRF exploite l'envoi automatique de cookies de session par le navigateur. Cette API étant stateless et authentifiée par en-tête `Authorization: Bearer` (jamais par cookie), le vecteur CSRF classique ne s'applique pas — désactiver `csrf()` dans [SecurityConfig.java](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java) est donc une décision défendable et documentée, pas une négligence (à condition que le frontend ne stocke jamais le jeton dans un cookie automatique).

**Question : Pourquoi restreindre le CORS à une seule origine (`http://localhost:3000`) ?**
- *Réponse simple* : Pour empêcher n'importe quel site web sur Internet d'utiliser le navigateur d'un utilisateur connecté pour appeler notre API en son nom.
- *Réponse technique* : `CorsConfigurationSource` dans [SecurityConfig.java](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java) limite les origines autorisées à une liste explicite, contrairement à un `*` permissif (catégorie OWASP A05 Security Misconfiguration — c'est d'ailleurs le sujet de l'injection pédagogique `INJ-08`).

**Question : Comment ce projet se protège-t-il contre l'injection SQL ?**
- *Réponse simple* : Le programme ne "construit" jamais de requêtes en collant des morceaux de texte fournis par l'utilisateur — il utilise un outil (Hibernate/JPA) qui sépare strictement la requête des données fournies.
- *Réponse technique* : `UserRepository` utilise exclusivement des méthodes dérivées Spring Data JPA et des requêtes paramétrées (`@Param`), jamais de concaténation de chaînes — élimination structurelle du risque d'injection SQL (catégorie OWASP A03). Un commentaire `// INJ-03` marque où une version vulnérable pourrait être introduite à des fins pédagogiques (non actif).

**Question : Qu'est-ce que le rate limiting, et comment est-il implémenté ici ?**
- *Réponse simple* : C'est un compteur qui limite le nombre de demandes qu'une même adresse internet peut faire en peu de temps — pour empêcher quelqu'un d'essayer des milliers de mots de passe d'affilée.
- *Réponse technique* : [RateLimitingFilter](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java) utilise **Bucket4j** (algorithme de seau à jetons / token bucket) avec un compteur par adresse IP stocké dans une `ConcurrentHashMap` en mémoire locale — protection contre A07 (Authentication Failures) et les abus génériques.

**Question : Le rate limiting a-t-il une faiblesse connue dans ce projet ?**
- *Réponse simple* : Oui, et c'est volontairement assumé : comme le programme tourne sur deux copies (réplicas) en parallèle, chacune a son propre compteur — un attaquant un peu malin pourrait répartir ses tentatives entre les deux et doubler la limite réelle.
- *Réponse technique* : Le compteur est stocké dans une `ConcurrentHashMap` locale au processus ([RateLimitingFilter.java:51](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java#L51)), alors que [k8s/api-deployment.yaml:49](k8s/api-deployment.yaml#L49) déploie 2 réplicas — la limite réelle peut donc être doublée en répartissant les requêtes. La solution recommandée est un magasin partagé (Redis + `bucket4j-redis`), voir [AMELIORATIONS_RECOMMANDEES.md §2](AMELIORATIONS_RECOMMANDEES.md).

**Question : Que se passe-t-il si j'essaie d'accéder au profil d'un autre utilisateur ?**
- *Réponse simple* : Le système refuse poliment (erreur "Accès refusé"), sans jamais révéler si ce compte existe réellement — pour ne pas donner d'indices à un attaquant.
- *Réponse technique* : `verifyOwnershipOrAdmin` lève une `AccessDeniedException`, transformée par [GlobalExceptionHandler](src/main/java/com/devsecops/usermgmt/exception/GlobalExceptionHandler.java) en `403 Forbidden` générique — empêchant l'énumération de l'existence des comptes via cette route (contrairement, on le note honnêtement, au flux d'inscription — voir question suivante).

**Question : Existe-t-il malgré tout un risque d'énumération de comptes dans ce projet ?**
- *Réponse simple* : Oui — à l'inscription, le système indique précisément si c'est le nom d'utilisateur OU l'adresse e-mail qui est déjà pris, ce qui permet à quelqu'un de deviner quelles adresses e-mail sont déjà inscrites.
- *Réponse technique* : [AuthService.java:54-59](src/main/java/com/devsecops/usermgmt/service/AuthService.java#L54) renvoie des messages distincts ("nom d'utilisateur pris" vs "e-mail déjà enregistré"), facilitant l'énumération de comptes (sous-catégorie d'A07). La recommandation est un message générique unique — un compromis UX/sécurité assumé à présenter en soutenance, voir [AMELIORATIONS_RECOMMANDEES.md §2](AMELIORATIONS_RECOMMANDEES.md).

### D. Le bug critique trouvé et corrigé

**Question : Avez-vous trouvé des bugs réels dans ce projet, et si oui, lequel est le plus important ?**
- *Réponse simple* : Oui — j'ai trouvé un bug qui empêchait absolument tout le monde de se connecter ou de s'inscrire, y compris les nouveaux visiteurs ! La page de connexion elle-même renvoyait une erreur "Accès interdit" avant même de vérifier le mot de passe.
- *Réponse technique* : Une version antérieure de [JwtAuthenticationFilter](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java) court-circuitait la requête (renvoyait `403 Forbidden`) dès qu'aucun jeton Bearer n'était présent — *avant* que `SecurityFilterChain` ne puisse appliquer ses règles `permitAll`. Conséquence : même les routes publiques (`/api/auth/login`, `/register`, `/actuator/health`) devenaient inaccessibles sans jeton — un paradoxe puisqu'on a justement besoin de ces routes pour *obtenir* un jeton. Documenté en détail dans [LISTE_DES_BUGS.md — BUG #1](LISTE_DES_BUGS.md).

**Question : Pourquoi la suite de tests n'a-t-elle pas détecté ce bug avant qu'il n'arrive en production ?**
- *Réponse simple* : Parce que les tests automatiques "simulaient" un utilisateur déjà connecté pour tester les contrôleurs — ils ne passaient donc jamais par le vrai "videur" (le filtre JWT) qui, lui, était cassé.
- *Réponse technique* : Les slice tests (`@WebMvcTest`) utilisent `SecurityMockMvcRequestPostProcessors.user(...)`, qui injecte directement un `Authentication` dans le `SecurityContext`, **contournant** entièrement `JwtAuthenticationFilter`. Même les tests d'intégration Testcontainers existants ne couvraient pas le scénario précis "requête anonyme sur une route publique". Le bug ne pouvait donc se manifester qu'en conditions réelles.

**Question : Comment avez-vous corrigé ce bug, et comment évitez-vous qu'il revienne ?**
- *Réponse simple* : J'ai modifié le "videur" pour qu'il se contente de vérifier le badge **s'il y en a un**, sans jamais décider lui-même de claquer la porte — cette décision finale appartient uniquement au système de sécurité central, qui sait quelles portes sont publiques.
- *Réponse technique* : Le filtre corrigé ([lignes 51-56](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java#L51), commentaire explicite) ne fait que *peupler* le `SecurityContextHolder` quand un jeton valide est présent, et laisse systématiquement passer la requête vers `SecurityFilterChain` — seule autorité de décision `permitAll`/`hasRole`/`authenticated`. La recommandation pour blinder ce point est d'ajouter un test d'intégration explicite "requête anonyme sur `/api/auth/login` doit renvoyer 401, jamais 403" (voir [AMELIORATIONS_RECOMMANDEES.md §4](AMELIORATIONS_RECOMMANDEES.md), classé 🔴 priorité haute).

**Question : Quelle leçon générale tirez-vous de ce bug pour votre pratique de développeur ?**
- *Réponse simple* : Qu'un mécanisme de sécurité mal placé peut casser une application entière — et que des tests qui "simulent trop" peuvent donner une fausse impression de sécurité.
- *Réponse technique* : Que les tests "slice" avec sécurité simulée sont précieux pour isoler la logique métier, mais **insuffisants seuls** pour valider la chaîne de sécurité bout en bout — les tests d'intégration traversant le vrai pipeline de filtres (Testcontainers ici) sont indispensables pour ce type de garantie, et méritent d'être enrichis en priorité avec des scénarios "négatifs" (accès anonymes, jetons invalides/expirés).

### E. Les autres anomalies relevées

**Question : Le README est-il fiable à 100% ?**
- *Réponse simple* : Non, il contient quelques inexactitudes que j'ai relevées en le comparant au code réel — par exemple il indique Java 17 alors que le projet exige Java 21.
- *Réponse technique* : Voir [LISTE_DES_BUGS.md — BUG #5](LISTE_DES_BUGS.md) (incohérence de version Java, [README.md:13](README.md#L13) vs [pom.xml](pom.xml)) et BUG #2/#3 (références à des routes/configurations inexistantes). Ces écarts ont été identifiés par confrontation systématique documentation ↔ code, conformément à la consigne d'audit "aucune supposition".

**Question : Pourquoi le `Dockerfile`, le `docker-compose.yml`, le pipeline CI et le README font-ils tous référence à une route `/health` qui n'existe pas dans le code ?**
- *Réponse simple* : C'est une incohérence qui s'est probablement glissée pendant l'évolution du projet — la route prévue n'a jamais été codée, alors qu'une route équivalente (`/actuator/health`, fournie automatiquement par Spring Boot Actuator) existe bel et bien.
- *Réponse technique* : `/health` est référencé dans [README.md](README.md), [Dockerfile](Dockerfile) (`HEALTHCHECK`), [docker-compose.yml](docker-compose.yml), [.github/workflows/devsecops-pipeline.yml](.github/workflows/devsecops-pipeline.yml) et même listé dans `permitAll` de [SecurityConfig.java](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java) — mais aucun `@GetMapping("/health")` n'existe ; seul `/actuator/health` (exposé par Actuator, également dans `permitAll`) répond réellement. Détaillé dans [LISTE_DES_BUGS.md — BUG #2](LISTE_DES_BUGS.md), avec recommandation d'aligner toutes les références sur `/actuator/health`.

**Question : La fonctionnalité de vérification d'e-mail fonctionne-t-elle ?**
- *Réponse simple* : Non — elle est "construite" (le code existe) mais pas "branchée" : il manque la pièce qui enverrait réellement l'e-mail, et l'inscription ne déclenche jamais la création du jeton de vérification.
- *Réponse technique* : [EmailVerificationService](src/main/java/com/devsecops/usermgmt/service/EmailVerificationService.java) a une Javadoc qui l'affirme explicitement ("architecturally complete but not enforced... no JavaMailSender", [lignes 17-26](src/main/java/com/devsecops/usermgmt/service/EmailVerificationService.java#L17)) ; `AuthService.register` n'appelle jamais `createToken`. C'est documenté comme un choix assumé (démonstration d'architecture), pas un oubli caché — voir [LISTE_DES_BUGS.md — BUG #6](LISTE_DES_BUGS.md).

**Question : Existe-t-il du code mort dans le projet ?**
- *Réponse simple* : Oui, au moins une méthode qui ne semble appelée par personne — un peu comme une pièce de la maison qu'on a construite mais qu'on n'utilise jamais.
- *Réponse technique* : `UserService.getAllUsers()` ([lignes 79-85](src/main/java/com/devsecops/usermgmt/service/UserService.java#L79)) ne semble appelée par aucun contrôleur ni test malgré un commentaire affirmant qu'elle est "kept for backward compatibility with existing tests". Recommandation : vérifier avec `grep -r "getAllUsers()"` puis la supprimer si confirmé inutilisé — voir [AMELIORATIONS_RECOMMANDEES.md §3](AMELIORATIONS_RECOMMANDEES.md).

**Question : Pourquoi existe-t-il une configuration de rate limiting pour "forgot-password" alors que cette fonctionnalité n'existe pas ?**
- *Réponse simple* : C'est un réglage "orphelin" — préparé en avance pour une fonctionnalité qui n'a finalement jamais été développée.
- *Réponse technique* : [application.properties](src/main/resources/application.properties) et [RateLimitProperties.java](src/main/java/com/devsecops/usermgmt/config/RateLimitProperties.java) définissent une entrée `forgot-password`, et [RateLimitingFilter](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java) y fait référence — mais aucune route `/api/auth/forgot-password` ni service associé n'existe. Voir [LISTE_DES_BUGS.md — BUG #3](LISTE_DES_BUGS.md).

**Question : Le fichier `monitoring/prometheus-config.yaml` est-il utilisé ?**
- *Réponse simple* : Non — c'est un exemple de configuration "à l'ancienne" qui n'est pas connecté au système de supervision réellement déployé, lequel fonctionne différemment.
- *Réponse technique* : Le déploiement réel s'appuie sur l'opérateur Prometheus (kube-prometheus-stack) et la ressource `ServiceMonitor` ([monitoring/api-servicemonitor.yaml](monitoring/api-servicemonitor.yaml)), qui sélectionne dynamiquement les cibles via labels — un fichier `prometheus-config.yaml` "classique" (`scrape_configs` statiques) n'est jamais chargé par cette architecture. Voir [LISTE_DES_BUGS.md — BUG #4](LISTE_DES_BUGS.md).

**Question : Le profil Spring `prod` est-il pleinement configuré ?**
- *Réponse simple* : Pas tout à fait — le déploiement Kubernetes l'active, mais le fichier de réglages spécifique à la production n'existe pas, donc ce profil ne change en pratique rien.
- *Réponse technique* : [k8s/api-deployment.yaml](k8s/api-deployment.yaml) définit `SPRING_PROFILES_ACTIVE=prod`, mais aucun `application-prod.properties` n'existe (contrairement à `application-staging.properties`) — Spring se rabat silencieusement sur la configuration par défaut. Voir [LISTE_DES_BUGS.md — BUG #7](LISTE_DES_BUGS.md), avec recommandation de créer ce fichier (au minimum `ddl-auto=validate`).

### F. Tests

**Question : Quels types de tests existent dans ce projet ?**
- *Réponse simple* : Trois niveaux, du plus "isolé" au plus "réaliste" : des tests qui vérifient une seule pièce du moteur isolément, des tests qui vérifient un assemblage de pièces avec des doublures pour le reste, et des tests qui démarrent le moteur complet avec une vraie base de données pour vérifier que tout s'articule correctement.
- *Réponse technique* : (1) Tests unitaires JUnit5/Mockito (`JwtTokenProviderTest`, `UserServiceTest`...), (2) Slice tests `@WebMvcTest` avec `MockMvc` et sécurité simulée (`AuthControllerTest`, `UserControllerTest`, `AdminControllerTest`, `RateLimitingFilterTest`), (3) Tests d'intégration `*IntegrationTest` avec **Testcontainers** démarrant un vrai PostgreSQL via [AbstractIntegrationTest.java](src/test/java/com/devsecops/usermgmt/AbstractIntegrationTest.java) (ex. `AuthIntegrationTest`, `AdminIntegrationTest`, `RefreshTokenIntegrationTest`, `RateLimitIntegrationTest`).

**Question : Qu'est-ce que Testcontainers, et pourquoi est-ce remarquable de l'utiliser ici ?**
- *Réponse simple* : C'est un outil qui démarre une vraie base de données dans une "boîte" temporaire juste pour la durée des tests, puis la détruit — au lieu d'utiliser une fausse base de données simplifiée qui pourrait cacher des problèmes réels.
- *Réponse technique* : Testcontainers lance un conteneur Docker PostgreSQL éphémère pour chaque run de tests d'intégration ([AbstractIntegrationTest.java](src/test/java/com/devsecops/usermgmt/AbstractIntegrationTest.java)), garantissant un comportement fidèle au runtime de production (types SQL, contraintes, comportements transactionnels réels) — bien plus fiable qu'une base en mémoire (H2) qui peut masquer des incompatibilités.

**Question : Quelle est la différence entre un test unitaire et un test d'intégration dans ce projet ?**
- *Réponse simple* : Le test unitaire vérifie une seule pièce isolée (par exemple : "est-ce que la fonction qui crée un jeton fonctionne ?"), tandis que le test d'intégration démarre le moteur entier et vérifie que toutes les pièces fonctionnent bien ensemble, avec une vraie base de données.
- *Réponse technique* : Les tests unitaires utilisent Mockito pour simuler les dépendances d'une classe isolée ; les tests d'intégration démarrent un contexte Spring complet (`@SpringBootTest`) avec un vrai PostgreSQL via Testcontainers, traversant la vraie chaîne de filtres — seuls ces derniers auraient pu détecter le bug critique du filtre JWT s'ils avaient couvert le bon scénario.

**Question : Pourquoi les tests d'intégration sont-ils exclus du build standard Maven ?**
- *Réponse simple* : Parce qu'ils sont plus lents (ils doivent démarrer une vraie base de données à chaque fois) — on les réserve donc à des moments où l'on peut se permettre d'attendre un peu plus.
- *Réponse technique* : Le plugin Surefire est configuré dans [pom.xml](pom.xml) pour exclure le pattern `*IntegrationTest` du cycle `mvn test` standard (rapide, pour le feedback immédiat du développeur), tandis qu'un profil/plugin dédié (Failsafe, ou un job CI séparé) les exécuterait — pattern courant pour équilibrer rapidité de feedback et couverture réaliste.

**Question : Quel test ajouteriez-vous en priorité si vous aviez une heure de plus ?**
- *Réponse simple* : Un test qui vérifie que, sans aucun badge (jeton), la page de connexion répond bien "identifiants invalides" et non "porte fermée" — exactement le scénario du bug critique que j'ai trouvé et corrigé.
- *Réponse technique* : Un scénario dans `AuthIntegrationTest` : *"`POST /api/auth/login` sans en-tête `Authorization` doit renvoyer `401`/un code de logique métier — jamais `403`"* et *"`GET /actuator/health` sans authentification doit renvoyer `200`"*. Classé 🔴 priorité haute dans [AMELIORATIONS_RECOMMANDEES.md §4](AMELIORATIONS_RECOMMANDEES.md) car il transforme une régression critique potentielle en échec de pipeline immédiat.

### G. Infrastructure et déploiement

**Question : Pourquoi conteneuriser cette application avec Docker ?**
- *Réponse simple* : Pour être sûr qu'elle se comporte exactement de la même façon partout où on la déploie, sans surprises liées à l'environnement.
- *Réponse technique* : [Dockerfile](Dockerfile) utilise un *multi-stage build* (séparation de la phase de compilation Maven et de la phase d'exécution, image finale allégée), exécute le processus avec un utilisateur non-root `appuser` (réduction de la surface d'attaque en cas de compromission du conteneur), et expose un `HEALTHCHECK`.

**Question : Pourquoi exécuter le conteneur avec un utilisateur non-root ?**
- *Réponse simple* : Pour limiter les dégâts si quelqu'un parvenait quand même à s'introduire dans le conteneur — un utilisateur "normal" ne peut pas faire autant de mal qu'un utilisateur "tout-puissant".
- *Réponse technique* : Le `Dockerfile` crée et utilise l'utilisateur `appuser` (principe du moindre privilège) — limite les capacités d'une éventuelle exploitation (pas d'accès root au système de fichiers ou aux capacités du noyau du conteneur).

**Question : Qu'apporte Kubernetes par rapport à `docker-compose` ?**
- *Réponse simple* : `docker-compose` permet de faire tourner l'application sur une seule machine pour le développement ; Kubernetes permet de la déployer, surveiller et faire évoluer (plusieurs copies, redémarrage automatique en cas de panne) sur un vrai parc de serveurs.
- *Réponse technique* : Le dossier `k8s/` définit des `Deployment` (avec 2 réplicas pour la haute disponibilité), `Service` (équilibrage de charge interne), `Ingress` (exposition externe), `ConfigMap`/`Secret` (configuration externalisée), et le déploiement de PostgreSQL — `docker-compose.yml` reste limité au développement local mono-machine.

**Question : Pourquoi y a-t-il 2 réplicas pour l'API dans le déploiement Kubernetes ?**
- *Réponse simple* : Pour que l'application continue de répondre même si l'une des deux copies tombe en panne ou est en cours de mise à jour, et pour répartir la charge entre les deux.
- *Réponse technique* : [k8s/api-deployment.yaml:49](k8s/api-deployment.yaml#L49) configure `replicas: 2` — haute disponibilité et répartition de charge. **Conséquence directe à connaître** : cela rend le rate limiting local (en mémoire) potentiellement contournable (voir question section C ci-dessus et [AMELIORATIONS_RECOMMANDEES.md §2](AMELIORATIONS_RECOMMANDEES.md)).

**Question : Qu'est-ce que GitOps, et comment ArgoCD l'implémente-t-il ici ?**
- *Réponse simple* : C'est l'idée que le dépôt de code devient la "source de vérité unique" : on ne déploie jamais "à la main", on modifie le dépôt et un robot (ArgoCD) se charge d'appliquer ces changements automatiquement sur les serveurs.
- *Réponse technique* : Les manifestes `argocd/` définissent une ressource `Application` qui synchronise en continu l'état du cluster Kubernetes avec l'état déclaré dans ce dépôt Git — traçabilité complète (chaque déploiement correspond à un commit), rollback trivial (`git revert`), et élimination de la dérive de configuration ("configuration drift").

**Question : Comment la supervision (monitoring) de l'application est-elle organisée ?**
- *Réponse simple* : L'application publie en continu des chiffres sur sa santé (nombre de requêtes, temps de réponse...) à une adresse spéciale ; un outil (Prometheus) vient régulièrement collecter ces chiffres, et un autre (Grafana) les affiche sous forme de graphiques.
- *Réponse technique* : Spring Boot **Actuator** + **Micrometer** exposent les métriques au format Prometheus sur `/actuator/prometheus`. La ressource `ServiceMonitor` ([monitoring/api-servicemonitor.yaml](monitoring/api-servicemonitor.yaml)) indique à l'opérateur Prometheus (kube-prometheus-stack, label `release: kube-prom` obligatoire) où scraper ces métriques (intervalle 15s). **Grafana** affiche ensuite des tableaux de bord visuels.

**Question : Pourquoi les logs sont-ils au format JSON plutôt qu'en texte brut ?**
- *Réponse simple* : Parce qu'un format structuré peut être indexé et recherché automatiquement par des outils, alors que du texte libre est difficile à filtrer rapidement quand il y a des millions de lignes.
- *Réponse technique* : [logback-spring.xml](src/main/resources/logback-spring.xml) configure `logstash-logback-encoder` pour produire des logs JSON structurés avec champs `traceId`/`requestId`/`path`/`method`/`duration` (issus du MDC peuplé par [RequestLoggingFilter](src/main/java/com/devsecops/usermgmt/filter/RequestLoggingFilter.java)) — directement exploitables par une stack ELK pour la corrélation et la recherche.

**Question : À quoi sert un `traceId`/`requestId` dans les logs ?**
- *Réponse simple* : C'est un numéro de suivi unique attribué à chaque demande, qui permet de retrouver instantanément toutes les lignes de journal liées à cette demande précise, même au milieu de millions d'autres lignes.
- *Réponse technique* : `RequestLoggingFilter` génère/propage ces identifiants via le MDC SLF4J — en environnement distribué (plusieurs réplicas, plusieurs services), ils permettent de relier les traces d'une même requête de bout en bout, essentiel pour le diagnostic de problèmes en production (observabilité).

### H. Le protocole de vulnérabilités pédagogiques

**Question : Qu'est-ce que `VULNERABILITIES.md`, et pourquoi avoir délibérément documenté des failles ?**
- *Réponse simple* : C'est un mode d'emploi pour activer, à la demande, dix failles de sécurité connues — un peu comme un détecteur de fumée que l'on teste avec une fumée contrôlée. Le but n'est pas de rendre le projet vulnérable, mais de prouver que les outils de détection automatique fonctionnent vraiment.
- *Réponse technique* : Documente 10 vulnérabilités injectables (`INJ-01` à `INJ-10`), chacune cataloguée par catégorie OWASP, fichier:ligne précis, score CVSS, et modification minimale pour l'activer — avec des marqueurs `// INJ-XX` déjà présents dans le code (non actifs). Objectif : valider que la chaîne SAST (SonarCloud)/DAST (OWASP ZAP)/SCA (Trivy) détecte effectivement chaque catégorie de faille — démarche réaliste de "red teaming" appliquée à son propre pipeline.

**Question : Ces dix failles sont-elles actives dans le code actuel ?**
- *Réponse simple* : Non, absolument pas. Ce sont des interrupteurs éteints, clairement marqués, prêts à être activés un par un pour une démonstration — le code, tel qu'il est aujourd'hui, ne contient aucune de ces failles.
- *Réponse technique* : Chaque point d'injection est un commentaire `// INJ-XX` à un emplacement précis (ex. [SecurityConfig.java](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java) `// INJ-06`/`// INJ-08`, [JwtTokenProvider.java](src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java) `// INJ-02`, [UserRepository.java](src/main/java/com/devsecops/usermgmt/repository/UserRepository.java) `// INJ-03`, [UserService.java](src/main/java/com/devsecops/usermgmt/service/UserService.java) `// INJ-04`, [User.java](src/main/java/com/devsecops/usermgmt/entity/User.java) `// INJ-05`) — le code en l'état applique les bonnes pratiques ; voir [AUDIT_COMPLET.md §2.2.c](AUDIT_COMPLET.md) pour le tableau complet des 10 entrées.

**Question : Comment ce protocole pourrait-il être utilisé concrètement pendant la soutenance ?**
- *Réponse simple* : En activant en direct l'une de ces failles (la plus simple à montrer visuellement), puis en montrant que l'outil de sécurité automatique la détecte immédiatement — transformer un document théorique en démonstration vivante.
- *Réponse technique* : Recommandation : démontrer `INJ-08` (CORS permissif, modification d'une ligne dans `SecurityConfig.java`) sur une branche dédiée, puis montrer le rapport ZAP/Semgrep correspondant la signalant — preuve tangible que la chaîne DevSecOps fonctionne réellement (voir [AMELIORATIONS_RECOMMANDEES.md §6](AMELIORATIONS_RECOMMANDEES.md)).

### I. Choix de conception et perspective critique

**Question : Si vous deviez recommencer ce projet, que feriez-vous différemment ?**
- *Réponse simple* : Je corrigerais les petites incohérences entre la documentation et le code (la route `/health`, la version de Java) au fur et à mesure plutôt qu'à la fin, et j'écrirais le test "anti-régression" du bug critique dès que je l'aurais corrigé.
- *Réponse technique* : Mettre en place dès le début un test d'intégration "smoke test" couvrant le scénario "accès anonyme aux routes publiques doit fonctionner", utiliser un outil de migration de schéma versionné (Flyway/Liquibase) plutôt que `ddl-auto=update`, et maintenir le README en cohérence avec le code via une vérification automatisée (par exemple un test qui contrôle que chaque route documentée existe réellement).

**Question : Quelles sont, selon vous, les plus grandes forces de ce projet ?**
- *Réponse simple* : La sécurité est prise au sérieux à plusieurs niveaux (pas seulement "on espère que ça suffit"), et il y a un vrai effort d'automatisation — tests, construction, déploiement, surveillance — du début à la fin.
- *Réponse technique* : Défense en profondeur (BOLA + BFLA + Mass Assignment + CORS + rate limiting + JWT signé/expirable + BCrypt), tests à plusieurs niveaux incluant des tests d'intégration avec une vraie base de données (Testcontainers), et une chaîne DevSecOps complète et cohérente (CI/CD avec SAST/SCA/DAST, GitOps, supervision Prometheus/Grafana) — rare de voir tous ces éléments effectivement opérationnels dans un projet étudiant.

**Question : Quelles sont les limites ou points faibles que vous assumez ?**
- *Réponse simple* : Quelques incohérences de documentation, une fonctionnalité (vérification d'e-mail) construite mais non branchée, et un compteur de limitation de requêtes qui pourrait être contourné en environnement à plusieurs réplicas — tous des points que j'ai identifiés moi-même en auditant mon propre projet.
- *Réponse technique* : Synthétisés dans [LISTE_DES_BUGS.md](LISTE_DES_BUGS.md) (8 anomalies cataloguées, dont une critique déjà corrigée) et [AMELIORATIONS_RECOMMANDEES.md](AMELIORATIONS_RECOMMANDEES.md) (pistes priorisées 🔴/🟠/🟢) — présenter ces limites avec leur analyse et leur plan de correction démontre une compréhension plus mûre qu'un projet présenté comme "parfait".

**Question : Comment garantissez-vous que le code reste sécurisé au fil des évolutions futures ?**
- *Réponse simple* : Grâce à la chaîne automatique qui vérifie, à chaque modification, que le code compile, que les tests passent, et qu'aucune faille connue n'a été introduite — sans attendre qu'un humain s'en aperçoive (ou pas).
- *Réponse technique* : Le pipeline [.github/workflows/devsecops-pipeline.yml](.github/workflows/devsecops-pipeline.yml) exécute à chaque push/PR : build + tests, **SAST** (SonarCloud — analyse statique du code source), **SCA** (Trivy — recherche de vulnérabilités connues dans les dépendances et l'image), et **DAST** (OWASP ZAP — tests dynamiques contre l'application en cours d'exécution) — trois angles complémentaires de détection automatisée intégrés au cycle de développement (philosophie "shift-left security").

**Question : Pourquoi avoir choisi PostgreSQL plutôt qu'une autre base de données ?**
- *Réponse simple* : C'est une base de données robuste, gratuite, largement utilisée en entreprise, et bien supportée par l'écosystème Java/Spring.
- *Réponse technique* : PostgreSQL est un SGBDR open-source mature, conforme ACID, avec un excellent support JDBC/Hibernate et un écosystème d'outils riche (extensions, migrations, monitoring) — choix standard et pertinent pour une application transactionnelle de gestion d'utilisateurs.

**Question : Si l'on vous demandait d'ajouter une fonctionnalité "mot de passe oublié", comment procéderiez-vous ?**
- *Réponse simple* : Je réutiliserais le mécanisme déjà construit pour la vérification d'e-mail (un jeton à usage unique, limité dans le temps, envoyé par e-mail) — l'architecture est déjà prête à être copiée pour ce nouveau besoin.
- *Réponse technique* : Dupliquer le pattern `EmailVerificationToken`/`EmailVerificationService` (entité de jeton à usage unique avec expiration, nettoyage planifié), ajouter une route `POST /api/auth/forgot-password` (déjà anticipée côté rate limiting — voir BUG #3), et brancher un `JavaMailSender` (la pièce manquante identifiée pour la vérification d'e-mail elle-même). Voir [AMELIORATIONS_RECOMMANDEES.md §3](AMELIORATIONS_RECOMMANDEES.md).

**Question : Comment géreriez-vous le secret JWT en production, différemment de l'environnement de démo ?**
- *Réponse simple* : Je générerais un secret unique, long et aléatoire, pour chaque environnement, et je ne le mettrais jamais "en clair" dans le code ou les fichiers partagés.
- *Réponse technique* : Le README documente actuellement un `JWT_SECRET` par défaut identique à celui de `docker-compose.yml` — pour un déploiement réel, générer un secret unique par environnement (`openssl rand -base64 64`, commande déjà documentée en commentaire dans [k8s/api-deployment.yaml:27](k8s/api-deployment.yaml#L27)) et le gérer via un coffre-fort de secrets (Kubernetes `Secret` chiffré, Vault, etc.) plutôt que de le committer.

---

## 9. Résumés de présentation (2 / 5 / 10 minutes)

### 9.1 Présentation en 2 minutes

> "Ce projet est le moteur (l'API) d'un système de gestion de comptes utilisateurs, développé avec Spring Boot et Java 21. Il permet de s'inscrire, se connecter, gérer son profil, et — pour les administrateurs — gérer l'ensemble des comptes. Toute la sécurité d'accès repose sur un système de jetons (JWT) avec rotation des jetons de rafraîchissement, et le projet implémente activement plusieurs défenses contre les failles les plus courantes du web (accès aux données d'autrui, élévation de privilèges, limitation des tentatives de connexion). Au-delà du code lui-même, le projet inclut une chaîne complète d'automatisation : tests à plusieurs niveaux (dont des tests d'intégration avec une vraie base de données), construction et déploiement automatisés via Docker/Kubernetes/ArgoCD, et supervision en temps réel via Prometheus/Grafana. Pendant mon audit, j'ai d'ailleurs trouvé et corrigé un bug critique qui empêchait toute connexion — une excellente illustration de l'intérêt de tester sérieusement son propre travail."

### 9.2 Présentation en 5 minutes

> "Ce projet est une API REST de gestion de comptes utilisateurs développée en Spring Boot 3.2.5 / Java 21, organisée en couches classiques (contrôleurs → services → accès aux données), connectée à une base PostgreSQL.
>
> **Côté sécurité**, l'authentification repose sur des jetons JWT signés et limités dans le temps (15 minutes), complétés par des jetons de rafraîchissement avec rotation systématique stockés en base — ce qui permet de révoquer un accès côté serveur malgré une architecture sans état. Le projet implémente activement plusieurs défenses connues : protection contre l'accès aux données d'autrui (BOLA), contre l'élévation de privilèges via les routes admin (BFLA), contre l'auto-attribution de rôles à l'inscription (Mass Assignment), une politique CORS restrictive, et une limitation du nombre de requêtes par adresse IP.
>
> **Côté qualité**, la suite de tests combine trois niveaux : tests unitaires, tests de contrôleurs avec sécurité simulée, et — fait notable — de véritables tests d'intégration qui démarrent une vraie base de données PostgreSQL via Testcontainers.
>
> **Côté infrastructure**, le projet va au-delà du simple code : conteneurisation Docker, déploiement Kubernetes avec deux réplicas, déploiement continu automatisé via ArgoCD (GitOps), supervision via Prometheus/Grafana, et une chaîne CI/CD GitHub Actions qui exécute à chaque modification une analyse statique (SonarCloud), une analyse des dépendances (Trivy) et des tests de sécurité dynamiques (OWASP ZAP).
>
> Enfin, le projet contient un protocole pédagogique original : `VULNERABILITIES.md` documente dix vulnérabilités injectables, désactivées par défaut mais prêtes à être activées pour démontrer concrètement que la chaîne d'outils de sécurité détecte bien chaque type de faille.
>
> Pendant mon audit, j'ai exploré l'intégralité du projet et identifié un bug critique — un filtre de sécurité mal positionné qui bloquait toutes les connexions — que j'ai corrigé et documenté comme étude de cas, ainsi que sept autres anomalies mineures (incohérences de documentation, configurations orphelines), toutes cataloguées avec leur niveau de risque et leur correction recommandée."

### 9.3 Présentation en 10 minutes

> *(Reprend la trame de 5 minutes ci-dessus, en l'enrichissant des points suivants)*
>
> **1. Le parcours d'une requête** : "Quand un utilisateur se connecte, sa demande traverse une chaîne de filtres — d'abord la journalisation (qui attribue un identifiant de suivi unique), puis la limitation de débit (qui vérifie qu'il n'a pas dépassé son quota), puis le filtre JWT (qui authentifie si un jeton est présent), et enfin le système de sécurité central qui décide d'autoriser ou non l'accès. Cette chaîne garantit qu'aucune requête n'échappe aux contrôles."
>
> **2. L'étude de cas du bug critique** (le moment fort de la présentation) : "Pendant mon audit, j'ai découvert qu'une version du filtre d'authentification rejetait toute requête sans jeton — y compris les demandes de connexion elles-mêmes ! C'était un paradoxe total : impossible d'obtenir un badge sans déjà en avoir un. Et le plus instructif : ma suite de tests ne détectait pas ce problème, car elle simulait un utilisateur déjà connecté pour tester les contrôleurs, contournant ainsi le filtre défaillant. J'ai corrigé le filtre pour qu'il se contente de vérifier les jetons sans jamais décider lui-même de bloquer une requête — cette décision appartenant au système de sécurité central — et je recommande d'ajouter un test qui vérifie explicitement qu'un visiteur anonyme peut accéder aux pages publiques. Ce cas illustre parfaitement comment un mécanisme de sécurité mal placé peut casser une application entière, et pourquoi il faut tester les vrais scénarios, pas seulement des scénarios simplifiés."
>
> **3. Le protocole de vulnérabilités pédagogiques** : "J'ai documenté dix failles de sécurité connues, chacune avec sa catégorie OWASP, son emplacement précis dans le code et son niveau de gravité — comme un mode d'emploi pour 'allumer' une faille à la demande, sans jamais la rendre active par défaut. L'objectif est de prouver que ma chaîne d'outils de sécurité automatique (analyse de code, analyse des dépendances, tests dynamiques) détecte effectivement chaque type de problème — une démarche proche de celle des équipes de sécurité professionnelles qui testent leurs propres défenses."
>
> **4. Les limites assumées** : "Mon audit a aussi révélé des points perfectibles que j'assume pleinement : quelques incohérences entre la documentation et le code (par exemple une route `/health` documentée mais jamais implémentée), une fonctionnalité de vérification d'e-mail construite mais non activée faute de service d'envoi de courriels, et un système de limitation de requêtes qui pourrait théoriquement être contourné si l'on répartit les tentatives entre les deux copies de l'application qui tournent en parallèle. J'ai catalogué chacun de ces points avec son niveau de risque et la correction que je recommande — la transparence sur les limites d'un projet est, à mon sens, un signe de maturité plus convaincant qu'une façade parfaite."
>
> **5. Conclusion** : "Ce projet ne se limite donc pas à 'faire fonctionner une API' — il démontre une compréhension de bout en bout du cycle de vie d'un logiciel sécurisé : conception, codage défensif, tests à plusieurs niveaux, automatisation de la construction et du déploiement, supervision en production, et — point que je considère essentiel — la capacité à auditer son propre travail avec un regard critique pour en identifier honnêtement les forces et les limites."

---

## 10. Glossaire

| Terme | Explication simple |
|---|---|
| **API (REST)** | Un ensemble d'adresses auxquelles d'autres programmes peuvent envoyer des demandes et recevoir des réponses structurées (en JSON). |
| **Backend** | La partie "moteur" d'une application — celle qui traite les demandes, sans interface visuelle. |
| **Frontend** | La partie visible par l'utilisateur (écrans, boutons) — absente de ce dépôt, mais prévue (cf. configuration CORS). |
| **JWT (JSON Web Token)** | Un "badge" numérique signé, prouvant l'identité de son porteur pendant une durée limitée, sans que le serveur ait besoin de "se souvenir" de lui. |
| **Refresh token** | Un second "badge", de plus longue durée, qui sert uniquement à obtenir un nouveau badge court terme sans se reconnecter. |
| **Rotation (de jetons)** | Le remplacement systématique d'un jeton par un nouveau à chaque utilisation, pour empêcher sa réutilisation frauduleuse. |
| **BCrypt** | Une méthode de transformation à sens unique des mots de passe — impossible à inverser, seulement à comparer. |
| **Hachage** | Transformer une donnée en une empreinte impossible à "dé-transformer", mais reproductible de façon identique à partir de la même donnée d'origine. |
| **BOLA (Broken Object Level Authorization)** | La faille qui permettrait à un utilisateur d'accéder aux données d'un autre simplement en changeant un identifiant dans l'adresse. |
| **BFLA (Broken Function Level Authorization)** | La faille qui permettrait à un utilisateur normal d'utiliser des fonctions réservées aux administrateurs. |
| **Mass Assignment** | La faille qui permettrait à un utilisateur de s'attribuer des droits qu'il ne devrait pas avoir en glissant des champs supplémentaires dans une requête. |
| **CORS (Cross-Origin Resource Sharing)** | La règle qui définit quels sites web ont le droit d'appeler notre API depuis le navigateur d'un visiteur. |
| **CSRF (Cross-Site Request Forgery)** | Une attaque qui pousserait le navigateur d'un utilisateur connecté à envoyer, à son insu, une requête à notre API. |
| **Rate limiting** | La limitation du nombre de demandes qu'une même source peut faire en peu de temps, pour empêcher les abus. |
| **OWASP** | Une organisation qui recense les failles de sécurité web les plus fréquentes (le "Top 10"). |
| **SAST (analyse statique)** | Un outil qui examine le code source à la recherche de failles, sans jamais exécuter le programme. |
| **DAST (analyse dynamique)** | Un outil qui attaque l'application en cours d'exécution pour vérifier sa résistance réelle. |
| **SCA (analyse de composition logicielle)** | Un outil qui vérifie si les bibliothèques externes utilisées contiennent des failles connues. |
| **CI/CD** | L'automatisation de la construction, des tests et du déploiement d'un programme à chaque modification de son code. |
| **Conteneur (Docker)** | Une "boîte" autosuffisante qui transporte un programme et tout ce dont il a besoin, garantissant un comportement identique partout. |
| **Kubernetes** | Un "chef d'orchestre" qui déploie, surveille et redémarre automatiquement des conteneurs sur un parc de machines. |
| **GitOps / ArgoCD** | Une pratique où le dépôt de code Git devient la source de vérité unique, et où un robot applique automatiquement ses changements à l'infrastructure réelle. |
| **Prometheus / Grafana** | Des outils qui collectent (Prometheus) puis affichent (Grafana) des indicateurs de santé et de performance d'une application. |
| **Logs structurés (JSON)** | Des journaux d'activité écrits dans un format que les machines peuvent indexer et chercher rapidement. |
| **traceId / requestId** | Un numéro de suivi unique attribué à chaque demande, permettant de retrouver tout son parcours dans les journaux. |
| **Test unitaire** | Une vérification automatique d'une seule pièce du programme, isolée du reste. |
| **Test d'intégration** | Une vérification automatique du fonctionnement de plusieurs pièces ensemble, dans des conditions proches du réel. |
| **Testcontainers** | Un outil qui démarre une vraie base de données dans une "boîte" temporaire, juste pour la durée des tests. |
| **MDC (Mapped Diagnostic Context)** | Un espace de stockage temporaire propre à chaque requête, utilisé pour enrichir automatiquement les lignes de journal. |
| **Code mort** | Du code écrit mais qui n'est jamais réellement utilisé par le programme. |
| **Configuration orpheline** | Un réglage préparé pour une fonctionnalité qui, finalement, n'a jamais été développée. |

---

> **Documents complémentaires** : pour l'audit technique exhaustif, voir [AUDIT_COMPLET.md](AUDIT_COMPLET.md) ; pour la liste détaillée des bugs, voir [LISTE_DES_BUGS.md](LISTE_DES_BUGS.md) ; pour les pistes d'amélioration priorisées, voir [AMELIORATIONS_RECOMMANDEES.md](AMELIORATIONS_RECOMMANDEES.md).
