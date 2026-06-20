# AMÉLIORATIONS RECOMMANDÉES — `api-devsecops-usermgmt`

> Recommandations classées par thème et priorité, fondées exclusivement sur l'état réel du code observé dans ce dépôt. Chaque recommandation indique : le constat, le fichier concerné, la proposition concrète, et la priorité (🔴 Haute · 🟠 Moyenne · 🟢 Faible / confort).

---

## 1. Corrections directement liées aux bugs identifiés

Ces points reprennent — sous forme actionnable — les anomalies détaillées dans [LISTE_DES_BUGS.md](LISTE_DES_BUGS.md). Ils constituent la base d'un "plan de nettoyage avant soutenance".

| Priorité | Action | Fichier(s) | Effort estimé |
|---|---|---|---|
| 🔴 | Aligner toutes les références `/health` sur `/actuator/health` (README, Dockerfile, docker-compose, pipeline CI) — ou créer un vrai endpoint `/health` | `README.md`, `Dockerfile`, `docker-compose.yml`, `.github/workflows/devsecops-pipeline.yml`, `SecurityConfig.java` | 15 min |
| 🟠 | Corriger le tableau de prérequis du README : Java 17 → Java 21 | `README.md:13` | 2 min |
| 🟠 | Décider du sort de la fonctionnalité "vérification d'e-mail" : soit l'achever (SMTP + appel à `createToken` lors de l'inscription), soit documenter clairement qu'il s'agit d'une démonstration d'architecture non opérationnelle | `EmailVerificationService.java`, `AuthService.java`, `pom.xml` | Achever : 2-4h · Documenter : 10 min |
| 🟢 | Retirer ou implémenter la configuration `forgot-password` orpheline | `application.properties`, `RateLimitingFilter.java`, `RateLimitProperties.java` | 10 min (retrait) |
| 🟢 | Créer `application-prod.properties` (au minimum `ddl-auto=validate`) pour que `SPRING_PROFILES_ACTIVE=prod` ait un effet réel et cohérent avec `staging` | `src/main/resources/application-prod.properties` (à créer) | 5 min |
| 🟢 | Supprimer ou documenter `monitoring/prometheus-config.yaml` comme exemple de référence non branché | `monitoring/prometheus-config.yaml` | 5 min |

---

## 2. Sécurité — pistes pour aller au-delà du niveau déjà solide

Le code actuel constitue déjà une **bonne base de référence sécurisée** (BOLA, BFLA, Mass Assignment, CORS restrictif, rate limiting, JWT signé avec expiration et `jti`, etc. — voir [AUDIT_COMPLET.md §2.2](AUDIT_COMPLET.md)). Voici des pistes pour le renforcer encore, utiles à mentionner en soutenance comme preuve de recul critique sur son propre travail :

| Priorité | Constat | Recommandation |
|---|---|---|
| 🟠 | Le rate limiter stocke ses compteurs dans une `ConcurrentHashMap` locale ([RateLimitingFilter.java:51](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java#L51)) — chaque réplica de pod a donc **son propre compteur indépendant**. Avec 2 réplicas configurés ([k8s/api-deployment.yaml:49](k8s/api-deployment.yaml#L49)), un attaquant peut effectivement doubler la limite réelle en répartissant ses requêtes. | Migrer vers un magasin partagé (Redis avec `bucket4j-redis`, ou équivalent) pour un comptage cohérent entre réplicas — pertinent à mentionner même si non implémenté, car cela montre une compréhension des limites du choix actuel en environnement distribué. |
| 🟠 | Les messages d'erreur d'inscription distinguent "nom d'utilisateur déjà pris" de "e-mail déjà enregistré" ([AuthService.java:54-59](src/main/java/com/devsecops/usermgmt/service/AuthService.java#L54)) — cela permet à un attaquant d'énumérer les comptes existants (vérifier si une adresse e-mail est déjà inscrite). | Renvoyer un message générique ("Impossible de créer le compte avec ces informations") pour les deux cas, ou introduire un délai constant, afin de réduire la possibilité d'énumération — compromis classique entre UX et sécurité, à présenter comme un arbitrage assumé plutôt qu'un oubli. |
| 🟢 | Pas de politique de complexité du mot de passe au-delà de la longueur (`@Size(min = 8, max = 100)`, [RegisterRequest.java:30](src/main/java/com/devsecops/usermgmt/dto/RegisterRequest.java#L30)). | Ajouter une validation de complexité (regex exigeant majuscule/minuscule/chiffre/symbole) ou, mieux, vérifier le mot de passe contre une liste de mots de passe compromis (API "Have I Been Pwned" type *k-anonymity*) — solution moderne recommandée par l'OWASP plutôt que des règles de complexité arbitraires. |
| 🟢 | Pas de verrouillage de compte après un nombre d'échecs de connexion (seul le rate limiting par IP existe). | Envisager un compteur d'échecs par compte (avec verrouillage temporaire), en complément du rate limiting par IP — combine défense contre le bruteforce distribué (par IP) et ciblé (par compte). |
| 🟢 | Le `JWT_SECRET` par défaut documenté dans le README (`cHJvamVjdC1kZXZzZWNvcHMtcGZlLXNlY3JldC1rZXktMjAyNA==`) est identique à celui utilisé dans `docker-compose.yml`. | Pour un déploiement réel (au-delà de la démonstration locale), générer un secret unique par environnement avec `openssl rand -base64 64` (commande déjà documentée dans les commentaires de `k8s/api-deployment.yaml:27`) et ne jamais le committer. |
| 🟢 | Pas d'en-têtes de sécurité HTTP explicites (`Content-Security-Policy`, `X-Content-Type-Options`, `Strict-Transport-Security`, etc.) — Spring Security applique certains en-têtes par défaut, mais aucune personnalisation n'est visible. | Ajouter un bloc `.headers(...)` dans `SecurityConfig` pour renforcer/personnaliser ces en-têtes selon les besoins du frontend réel. |

---

## 3. Fonctionnalités à compléter ou clarifier

| Priorité | Constat | Recommandation |
|---|---|---|
| 🟠 | La méthode `UserService.getAllUsers()` ([lignes 79-85](src/main/java/com/devsecops/usermgmt/service/UserService.java#L79)) n'est appelée par aucun contrôleur ni, semble-t-il, par aucun test (le commentaire dit "kept for backward compatibility with existing tests" mais aucun appel n'a été localisé). | Vérifier avec `grep -r "getAllUsers()"` si elle est réellement utilisée quelque part ; si non, la supprimer pour réduire la surface de code à maintenir (« trois lignes similaires valent mieux qu'une abstraction prématurée », et inversement, du code mort ne vaut jamais mieux que son absence). |
| 🟠 | La fonctionnalité "mot de passe oublié" est à moitié préparée (rate limiting configuré) mais absente côté contrôleur/service. | Si elle fait partie du périmètre attendu pour la soutenance, l'implémenter en réutilisant le pattern déjà éprouvé d'`EmailVerificationToken` (token à usage unique, expiration, nettoyage planifié) — l'architecture est déjà prête à être dupliquée pour ce cas d'usage. |
| 🟢 | Aucun frontend n'est présent dans le dépôt, alors que la configuration CORS cible `http://localhost:3000`. | Si un frontend existe dans un dépôt séparé, ajouter un lien explicite dans le README pour que le jury comprenne immédiatement l'architecture globale "frontend séparé consommant cette API". Si aucun frontend n'existe, le mentionner clairement pour anticiper la question du jury. |

---

## 4. Tests — pistes de renforcement de la couverture

La suite de tests actuelle est déjà solide (tests unitaires, slice tests avec mocks, et — fait notable — de véritables **tests d'intégration avec Testcontainers** qui démarrent un vrai PostgreSQL). Voici où porter l'effort en priorité :

| Priorité | Constat | Recommandation |
|---|---|---|
| 🔴 | **Aucun test n'aurait détecté le Bug #1** (filtre JWT bloquant tout) car les tests "slice" contournent le filtre réel via `SecurityMockMvcRequestPostProcessors.user(...)`. | Ajouter, dans `AuthIntegrationTest` (qui démarre une vraie application avec Testcontainers, donc traverse le vrai filtre JWT), un scénario explicite : *"une requête `POST /api/auth/login` sans en-tête `Authorization` doit renvoyer 401 (pas 403)"*, et *"`GET /actuator/health` sans authentification doit renvoyer 200"*. C'est le test le plus précieux à ajouter — il transforme une régression critique en échec de pipeline immédiat plutôt qu'en panne de production. |
| 🟠 | Aucun test trouvé pour le flux de vérification d'e-mail (`EmailVerificationService`/`EmailVerificationController`). | Ajouter au minimum des tests unitaires sur `EmailVerificationService` (création, validation, expiration, double-usage d'un token) à l'image de ceux déjà écrits pour `RefreshTokenService`. |
| 🟢 | Pas de test automatisé sur `RequestLoggingFilter` (corrélation `traceId`/`requestId`/MDC). | Ajouter un test unitaire vérifiant que les en-têtes `X-Trace-Id`/`X-Request-Id` sont bien propagés dans la réponse, et que le MDC est nettoyé après chaque requête (`MDC.clear()`). |
| 🟢 | Pas de test de bout en bout sur le scénario "rotation de réplicas + rate limiting" (problème mentionné en §2). | Hors de portée des tests Spring classiques (nécessite un environnement multi-instances) — à documenter plutôt comme limite connue dans la présentation. |

---

## 5. Performance et fiabilité

| Priorité | Constat | Recommandation |
|---|---|---|
| 🟠 | `spring.jpa.hibernate.ddl-auto=update` actif en profil par défaut/production ([application.properties:25](src/main/resources/application.properties#L25)). | Adopter un outil de migration versionné (Flyway ou Liquibase) pour gérer l'évolution du schéma de façon traçable et reproductible, et passer `ddl-auto` à `validate` partout sauf en développement local — alignement avec ce que fait déjà `application-staging.properties`. |
| 🟢 | Pas de configuration explicite du pool de connexions HikariCP, alors que le dashboard Grafana fourni prévoit un panel dédié à ces métriques. | Documenter ou ajuster `spring.datasource.hikari.*` (taille du pool, timeouts) en fonction de la charge attendue — utile pour montrer une compréhension du dimensionnement, même si les valeurs par défaut conviennent pour une démo. |
| 🟢 | Pas de cache applicatif (par exemple sur `getCurrentUser`, appelé à chaque `GET /api/users/me`). | Pour une charge réelle, envisager `@Cacheable` avec une politique d'invalidation sur mise à jour — à ne mentionner que comme piste d'évolution, pas comme priorité actuelle (le projet est une démonstration, pas un service à fort trafic). |

---

## 6. Documentation et présentation pour la soutenance

| Priorité | Constat | Recommandation |
|---|---|---|
| 🔴 | Le bug du filtre JWT (corrigé pendant cette mission) est un **excellent exemple pédagogique** : il illustre concrètement comment un mécanisme de sécurité mal positionné peut casser une application entière sans qu'aucun test automatisé (basé sur des mocks) ne le détecte. | L'utiliser comme étude de cas dans la présentation : "voici un bug réel que j'ai trouvé et corrigé, voici pourquoi mes tests ne le voyaient pas, voici le test que j'ai ajouté pour qu'il ne revienne jamais". C'est précisément le genre de récit qui démontre une compréhension profonde (et pas seulement un copier-coller de bonnes pratiques). |
| 🟠 | Le protocole `VULNERABILITIES.md` est une pièce maîtresse du projet — bien conçu et bien documenté — mais **uniquement consultable comme texte** dans son état actuel. | Préparer une démonstration live d'au moins une injection (par exemple INJ-08, CORS permissif — la plus simple à montrer visuellement) sur une branche dédiée, et montrer le rapport de l'outil correspondant (Semgrep/ZAP) qui la détecte. Cela transforme un document statique en preuve vivante de la chaîne DevSecOps. |
| 🟢 | Mettre à jour la section "Structure du projet" du README pour refléter l'ajout des dossiers `k8s/`, `argocd/`, `monitoring/` (actuellement absents de l'arborescence documentée, [README.md:155-175](README.md#L155)). | Ajout simple qui montre que la documentation suit l'évolution réelle du projet — bon réflexe à illustrer devant un jury technique. |

---

## Synthèse — top 5 actions à fort impact / faible effort avant la soutenance

1. 🔴 **Ajouter un test d'intégration "requête anonyme → `/api/auth/login` doit renvoyer 401, pas 403"** — capitalise sur le bug critique trouvé et corrigé, et garantit qu'il ne reviendra jamais (effort : ~20 min, impact : démontre une vraie maturité d'ingénierie).
2. 🔴 **Corriger les références à `/health`** dans README/Dockerfile/docker-compose/pipeline (effort : ~15 min, impact : élimine une source de confusion visible dès le premier `docker compose up`).
3. 🟠 **Mettre à jour le README** (version Java, structure du projet avec `k8s/`/`argocd/`/`monitoring/`) (effort : ~15 min, impact : cohérence globale perçue par le jury).
4. 🟠 **Décider et documenter le sort de la vérification d'e-mail** — soit l'achever, soit affirmer clairement "c'est une démonstration d'architecture, pas une fonctionnalité active" (effort : 10 min à 4h selon l'option, impact : évite une question piège en soutenance).
5. 🟢 **Nettoyer le code mort** (`getAllUsers()` non appelée, configuration `forgot-password` orpheline, `prometheus-config.yaml` non branché) (effort : ~30 min, impact : un code plus court est un code plus facile à défendre question par question).
