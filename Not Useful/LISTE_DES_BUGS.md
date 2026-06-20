# LISTE DES BUGS — `api-devsecops-usermgmt`

> Inventaire exhaustif des anomalies réellement constatées dans le dépôt (code, configuration, documentation, infrastructure). Chaque entrée précise : description, localisation exacte, impact, statut et correctif proposé ou déjà appliqué.
> Les "vulnérabilités injectables" du protocole pédagogique ([VULNERABILITIES.md](VULNERABILITIES.md), `INJ-01` à `INJ-10`) ne figurent **pas** ici : ce ne sont pas des bugs, mais des interrupteurs volontaires et documentés. Elles sont traitées séparément dans [AUDIT_COMPLET.md §2.2(c)](AUDIT_COMPLET.md).

---

## BUG #1 — Le filtre JWT bloquait l'intégralité des endpoints publics (CRITIQUE — corrigé)

- **Statut** : ✅ **Corrigé pendant cette mission**
- **Sévérité** : Critique (l'application entière était inutilisable, y compris la connexion)
- **Fichier** : [src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java)
- **Description** : `doFilterInternal` appelait `response.sendError(HttpServletResponse.SC_UNAUTHORIZED)` et retournait immédiatement dès qu'aucun Bearer token valide n'était présent dans la requête — *avant* que les règles `authorizeHttpRequests`/`permitAll` de `SecurityConfig` ne soient évaluées par la chaîne de filtres Spring Security.
- **Impact observé (tests `curl` directs sur le cluster réel, avant correctif)** :
  ```
  POST /api/auth/login          → 403  (devrait être 401 "mauvais identifiants" ou 200)
  GET  /actuator/health         → 403  (devrait être 200, endpoint déclaré permitAll)
  GET  /actuator/prometheus     → 403  (idem — bloque le scraping Prometheus)
  GET  /api/admin/users         → 403  (correct, mais pour la mauvaise raison)
  ```
  Concrètement : **personne ne pouvait se connecter à l'application**, ni un utilisateur final, ni un frontend, ni les sondes Kubernetes, ni Prometheus.
- **Pourquoi les tests ne l'ont pas détecté** : les tests "slice" (`@WebMvcTest`) utilisent `SecurityMockMvcRequestPostProcessors.user(...)` pour simuler un principal déjà authentifié — ce mécanisme **contourne le filtre JWT réel**, qui n'est donc jamais exercé avec une requête anonyme dans la suite de tests existante. Le bug n'a été détecté qu'en testant le cluster Kubernetes réel avec des requêtes HTTP brutes.
- **Correctif appliqué** ([lignes 38-82](src/main/java/com/devsecops/usermgmt/security/JwtAuthenticationFilter.java#L38)) : le filtre **ne fait plus que peupler** le `SecurityContext` lorsqu'un token *Bearer* valide est présent ; en son absence ou en cas de token invalide, il **journalise en `debug` et laisse passer la requête** vers la suite de la chaîne, qui applique alors `authorizeHttpRequests` comme seule autorité de décision d'accès — c'est le pattern standard recommandé pour un filtre d'authentification JWT avec Spring Security 6.
- **Vérification post-correctif (mêmes tests `curl`)** :
  ```
  POST /api/auth/login          → 401  (rejet métier "Invalid username or password" — normal sans identifiants valides)
  GET  /actuator/health         → 200
  GET  /actuator/prometheus     → 200
  GET  /api/admin/users         → 403  (toujours protégé, pour la bonne raison cette fois : BFLA actif)
  ```
- **Recommandation pour la suite** : ajouter un test d'intégration explicite qui envoie une requête **sans aucun en-tête `Authorization`** vers `/api/auth/login` et vérifie un statut `401` (et non `403`), ainsi qu'un test similaire pour `/actuator/health` → `200`. Cela aurait détecté la régression immédiatement, sans attendre un déploiement réel.

---

## BUG #2 — Endpoint `/health` documenté et utilisé partout, mais jamais implémenté

- **Statut** : ❌ Présent (non corrigé)
- **Sévérité** : Moyenne (impacte les healthchecks Docker, Kubernetes implicites, et le pipeline CI)
- **Fichiers concernés** :
  - [README.md:98](README.md#L98) — documente `GET /health` comme "Vérification de santé de l'application"
  - [Dockerfile:23](Dockerfile#L23) — `HEALTHCHECK ... CMD wget -qO- http://localhost:8080/health`
  - [docker-compose.yml:41](docker-compose.yml#L41) — `test: ["CMD", "wget", "-qO-", "http://localhost:8080/health"]`
  - [.github/workflows/devsecops-pipeline.yml:102](.github/workflows/devsecops-pipeline.yml#L102) — `curl -s http://localhost:8080/health` pour attendre que l'API soit prête avant le scan DAST
  - [SecurityConfig.java:52](src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java#L52) — `"/health"` figure dans la liste `permitAll`
- **Description** : Ces cinq fichiers font tous référence à un chemin `/health`. Or, en explorant l'intégralité de `src/main/java`, **aucun `@RestController` ni `@GetMapping` ne définit ce chemin**. Le seul endpoint de santé réellement fourni par l'application est celui de Spring Boot Actuator : `/actuator/health` (activé via `management.endpoints.web.exposure.include=health,...` dans [application.properties:38](src/main/resources/application.properties#L38), et explicitement `permitAll` sous `/actuator/health/**`).
- **Impact** :
  - Le `HEALTHCHECK` du Dockerfile et le healthcheck de `docker-compose.yml` interrogent une route qui renverra **404** (ou, avant le correctif du Bug #1, 403) — le conteneur sera probablement signalé `unhealthy` en continu.
  - Le job `dast` du pipeline CI boucle 30 fois sur `curl http://localhost:8080/health` ([ligne 101-108](.github/workflows/devsecops-pipeline.yml#L101)) : si la route ne répond jamais `200`, le pipeline attend inutilement 150 secondes avant de lancer le scan ZAP contre une application potentiellement déjà prête (sur un autre chemin).
  - Risque de confusion en soutenance si un membre du jury teste `curl http://localhost:8080/health`.
- **Correctif proposé (deux options, au choix)** :
  1. **Aligner la documentation et l'infrastructure sur la réalité** : remplacer toutes les occurrences de `/health` par `/actuator/health` dans `README.md`, `Dockerfile`, `docker-compose.yml`, `.github/workflows/devsecops-pipeline.yml` (et retirer `"/health"` de la liste `permitAll` si redondant avec `/actuator/health/**`).
  2. **Ou** ajouter un endpoint `GET /health` minimal (par exemple un `@RestController` qui délègue à `HealthEndpoint` ou renvoie simplement `{"status":"UP"}`), si l'on souhaite conserver un chemin "vitrine" indépendant d'Actuator.
  - L'option 1 est recommandée : elle ne crée aucun code superflu et s'appuie sur l'infrastructure de santé déjà robuste fournie par Actuator (`management.endpoint.health.probes.enabled=true`, états de liveness/readiness séparés).

---

## BUG #3 — Configuration de rate-limit pour un endpoint `forgot-password` qui n'existe pas

- **Statut** : ❌ Présent (non corrigé — code mort de configuration)
- **Sévérité** : Faible
- **Fichiers concernés** :
  - [src/main/resources/application.properties:12-14](src/main/resources/application.properties#L12) — bloc `rate-limit.forgot-password.*`
  - [src/main/java/com/devsecops/usermgmt/config/RateLimitProperties.java:17](src/main/java/com/devsecops/usermgmt/config/RateLimitProperties.java#L17) — propriété `forgotPassword`
  - [src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java:44,65,98-103](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java#L44) — référence au chemin `/api/auth/forgot-password`
- **Description** : Toute l'infrastructure de limitation de débit est prête pour un endpoint `/api/auth/forgot-password` (constante de chemin, propriétés de configuration, branche du `switch` de résolution de limite). Or, aucune classe `AuthController`/`AuthService` n'expose une route `forgot-password`, et aucune logique de réinitialisation de mot de passe n'existe dans le projet.
- **Impact** : Aucun à l'exécution — ce code ne s'exécute simplement jamais (la branche `default` du `switch` dans `resolveLimit` ([RateLimitingFilter.java:98-103](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java#L98)) ne sera jamais atteinte par une vraie requête puisque `shouldNotFilter` ([lignes 60-66](src/main/java/com/devsecops/usermgmt/filter/RateLimitingFilter.java#L60)) filtre déjà le chemin en amont). C'est uniquement une source de confusion pour la maintenance et la lecture du code.
- **Correctif proposé** : deux options :
  1. **Implémenter réellement** l'endpoint `POST /api/auth/forgot-password` (avec génération de token, envoi d'e-mail — réutilisable depuis l'infrastructure déjà présente pour `EmailVerificationToken`) si la fonctionnalité est souhaitée à terme.
  2. **Ou retirer** la configuration et les références orphelines (`rate-limit.forgot-password.*`, `FORGOT_PASSWORD_PATH`, propriété `forgotPassword`) si la fonctionnalité n'est pas prévue à court terme, pour éviter le code mort.

---

## BUG #4 — `monitoring/prometheus-config.yaml` n'est jamais utilisé par la stack de supervision réellement déployée

- **Statut** : ❌ Présent (fichier orphelin)
- **Sévérité** : Faible
- **Fichier** : [monitoring/prometheus-config.yaml](monitoring/prometheus-config.yaml)
- **Description** : Ce fichier définit une `ConfigMap` contenant une configuration Prometheus statique de type `scrape_configs`. Or, la stack de supervision réellement installée sur le cluster (`kube-prometheus-stack` via Helm, namespace `monitoring`, release `kube-prom`) est pilotée par l'**opérateur Prometheus**, qui découvre ses cibles exclusivement via des ressources personnalisées `ServiceMonitor`/`PodMonitor` (cf. [monitoring/api-servicemonitor.yaml](monitoring/api-servicemonitor.yaml), qui lui *est* effectivement reconnu — vérifié sur le cluster réel : statut des cibles `up`). Une `ConfigMap` de scrape config statique n'est prise en compte par cet opérateur que si elle est explicitement référencée dans la spec de la ressource `Prometheus`, ce qui n'est pas le cas ici.
- **Impact** : Aucun à l'exécution (le fichier n'est simplement jamais appliqué/consommé). Risque de confusion : un lecteur pourrait croire que la configuration de scraping passe par ce fichier, alors que c'est le `ServiceMonitor` qui fait tout le travail.
- **Correctif proposé** :
  - **Option recommandée** : supprimer `monitoring/prometheus-config.yaml` et documenter (par exemple dans un `README` du dossier `monitoring/`) que la découverte de cibles passe exclusivement par les CRD `ServiceMonitor`, conformément à l'architecture `kube-prometheus-stack`.
  - **Option alternative** : si l'on souhaite le conserver à des fins pédagogiques (illustrer "comment on configurait Prometheus avant les opérateurs"), ajouter un commentaire en tête de fichier précisant explicitement qu'il s'agit d'un exemple de référence non branché sur le déploiement actuel.

---

## BUG #5 — Le `README.md` annonce Java 17 comme prérequis alors que le projet exige Java 21

- **Statut** : ❌ Présent (incohérence documentaire)
- **Sévérité** : Faible (mais trompeuse pour un nouvel arrivant ou un membre du jury qui suivrait le README à la lettre)
- **Fichiers concernés** :
  - [README.md:13](README.md#L13) — tableau des prérequis : `| Java (JDK) | 17 |`
  - [pom.xml:23](pom.xml#L23) — `<java.version>21</java.version>`
  - [pom.xml:152-156](pom.xml#L152) — `maven-compiler-plugin` configuré avec `<release>${java.version}</release>` → release 21
  - [Dockerfile:5,12](Dockerfile#L5) — images de base `eclipse-temurin-21-*`
- **Description** : Le tableau de prérequis du README spécifie Java 17 comme version minimale, mais le projet est désormais configuré pour cibler et nécessiter Java 21 (le `maven-compiler-plugin` rejettera la compilation avec un JDK antérieur à 21, et l'image Docker embarque un JRE 21).
- **Impact** : Un développeur suivant le README à la lettre installerait un JDK 17, et `mvn clean package` échouerait avec une erreur de version de release. C'est précisément l'erreur rencontrée et corrigée pendant cette mission (`mvn test` échouait localement avec "this version of the Java Runtime only recognizes class file versions up to 61.0" tant que `JAVA_HOME` ne pointait pas vers un JDK 21).
- **Correctif proposé** : mettre à jour [README.md:13](README.md#L13) pour indiquer `Java (JDK) | 21` (et vérifier qu'aucune autre section du README ne référence la version 17).

---

## BUG #6 — Fonctionnalité de vérification d'e-mail incomplète : aucun envoi d'e-mail, aucune application de la vérification

- **Statut** : ⚠️ Présent, mais **documenté comme volontaire** dans le code source lui-même
- **Sévérité** : Moyenne (la fonctionnalité, telle qu'exposée par son contrôleur, ne peut pas être utilisée de bout en bout par un utilisateur réel)
- **Fichiers concernés** :
  - [EmailVerificationService.java:17-26](src/main/java/com/devsecops/usermgmt/service/EmailVerificationService.java#L17) — Javadoc explicite : *"The verification flow is architecturally complete but not enforced... In a full implementation, createToken would trigger an email via a JavaMailSender"*
  - [EmailVerificationController.java:21-23](src/main/java/com/devsecops/usermgmt/controller/EmailVerificationController.java#L21) — *"Current behaviour: verification is optional — accounts work whether or not the email has been confirmed"*
  - `pom.xml` — **aucune dépendance `spring-boot-starter-mail`** n'est déclarée
- **Description** : `EmailVerificationService.createToken(username)` génère et persiste un token, mais **le renvoie uniquement comme valeur de retour Java** — il n'est jamais transmis à l'utilisateur (pas d'e-mail, pas d'exposition via une réponse HTTP). Par ailleurs, aucun appelant de `createToken` n'a été trouvé dans le reste du code (ni dans `AuthService.register`, ni ailleurs) : la création de token n'est donc, en l'état, **jamais déclenchée** en conditions réelles. Seule la validation (`GET /api/auth/verify?token=...`) est exposée publiquement, mais sans un moyen d'obtenir un token valide, cette route est inatteignable utilement par un utilisateur normal.
- **Impact** : La fonctionnalité "vérification d'e-mail" est visible dans Swagger et dans le code, mais **ne peut pas être exercée de bout en bout**. Un membre du jury qui chercherait à tester ce flux serait bloqué dès la première étape (obtenir un token).
- **Correctif proposé** (si l'on souhaite achever la fonctionnalité) :
  1. Ajouter `spring-boot-starter-mail` au `pom.xml` et configurer un serveur SMTP (ou un service de test comme MailHog/Mailtrap pour la démonstration).
  2. Appeler `emailVerificationService.createToken(username)` à la fin de `AuthService.register`, et envoyer l'e-mail contenant le lien `/api/auth/verify?token=<rawToken>`.
  3. (Optionnel, pour activer réellement la vérification) Modifier `UserDetailsServiceImpl.loadUserByUsername` pour renvoyer un `UserDetails` avec `enabled=false` tant que `user.isEmailVerified()` est faux — comme suggéré par le commentaire de `EmailVerificationToken.java:22-26`.
  - **Alternative plus simple si la fonctionnalité n'est pas prioritaire pour la soutenance** : documenter clairement (README, présentation) que ce flux est une **démonstration d'architecture** ("voici comment on câblerait la vérification d'e-mail") plutôt qu'une fonctionnalité opérationnelle — ce que le code lui-même affirme déjà en Javadoc, donc il suffit de le relayer dans la documentation utilisateur.

---

## BUG #7 — `SPRING_PROFILES_ACTIVE=prod` configuré en Kubernetes alors qu'aucun `application-prod.properties` n'existe

- **Statut** : ❌ Présent (configuration silencieusement sans effet)
- **Sévérité** : Faible
- **Fichiers concernés** :
  - [k8s/api-deployment.yaml:17](k8s/api-deployment.yaml#L17) — `SPRING_PROFILES_ACTIVE: "prod"` dans la `ConfigMap api-config`
  - `src/main/resources/` — contient uniquement `application.properties` et `application-staging.properties` ; **aucun `application-prod.properties`**
- **Description** : Le `Deployment` Kubernetes de l'API active explicitement le profil Spring `prod`. Spring Boot ne lèvera **aucune erreur** si `application-prod.properties` est absent — il continuera simplement avec la configuration par défaut de `application.properties`. Le réglage est donc silencieusement sans effet : ni bug bloquant, ni message d'avertissement visible.
- **Impact** : Risque de confusion lors du débogage ("pourquoi mon override de profil 'prod' n'est pas pris en compte ?"). De plus, cela illustre une dérive potentielle : si quelqu'un ajoute par erreur un `application-prod.properties` contenant des réglages inattendus, ceux-ci s'activeraient silencieusement en cluster sans avoir été testés dans un autre environnement.
- **Correctif proposé** (deux options) :
  1. **Créer un `application-prod.properties`** minimal mais réel — par exemple avec `spring.jpa.hibernate.ddl-auto=validate` (comme le fait déjà `application-staging.properties`, pour éviter les modifications de schéma automatiques en production) et `spring.jpa.show-sql=false`.
  2. **Ou retirer** la variable `SPRING_PROFILES_ACTIVE` du `ConfigMap` si aucun comportement spécifique à la production n'est requis pour l'instant — la configuration par défaut suffira.
  - L'option 1 est recommandée : elle aligne la pratique avec celle déjà établie pour `staging`, et évite que `ddl-auto=update` (potentiellement risqué sur une base volumineuse) ne s'exécute sans contrôle en environnement "production".

---

## BUG #8 — Doublon historique de `jwt.expiration` (déjà corrigé lors d'une session précédente — mentionné pour traçabilité)

- **Statut** : ✅ **Corrigé** (avant le début de cette session d'audit — conservé ici pour la traçabilité demandée par le protocole d'audit "explorer tout, ne rien supposer")
- **Sévérité historique** : Moyenne
- **Fichier** : [src/main/resources/application.properties](src/main/resources/application.properties)
- **Description (état antérieur, déduit du contexte de session)** : la clé `jwt.expiration` apparaissait deux fois dans le fichier — une première fois avec la valeur voulue (`900000`, soit 15 minutes), une seconde fois plus bas avec `86400000` (24 heures), qui écrasait silencieusement la première en raison de l'ordre de chargement des propriétés Spring. La ligne en doublon a été supprimée.
- **État actuel vérifié** : une seule occurrence subsiste, à la [ligne 18](src/main/resources/application.properties#L18) : `jwt.expiration=900000`, cohérente avec la Javadoc de [JwtConfig.java:21](src/main/java/com/devsecops/usermgmt/config/JwtConfig.java#L21) (`@Value("${jwt.expiration:900000}")`) et avec le commentaire `# Access token: 15 minutes | Refresh token: 7 days` ([ligne 17](src/main/resources/application.properties#L17)).
- **Recommandation** : aucune action requise — mentionné ici uniquement pour que la documentation d'audit reste complète et traçable. Si une revue de code (`git log`) est faite en soutenance, ce correctif peut être cité comme exemple concret de "bug de configuration silencieux détecté par revue attentive".

---

## Synthèse — tableau récapitulatif

| # | Titre | Sévérité | Statut | Fichier(s) principal(aux) |
|---|---|---|---|---|
| 1 | Filtre JWT bloquant tous les endpoints publics | Critique | ✅ Corrigé | `JwtAuthenticationFilter.java` |
| 2 | Endpoint `/health` documenté mais non implémenté | Moyenne | ❌ Présent | `README.md`, `Dockerfile`, `docker-compose.yml`, pipeline CI, `SecurityConfig.java` |
| 3 | Configuration de rate-limit orpheline (`forgot-password`) | Faible | ❌ Présent | `application.properties`, `RateLimitingFilter.java`, `RateLimitProperties.java` |
| 4 | `prometheus-config.yaml` jamais consommé | Faible | ❌ Présent | `monitoring/prometheus-config.yaml` |
| 5 | README annonce Java 17 au lieu de 21 | Faible | ❌ Présent | `README.md` |
| 6 | Vérification d'e-mail incomplète (pas d'envoi) | Moyenne | ⚠️ Présent (assumé) | `EmailVerificationService.java`, `EmailVerificationController.java` |
| 7 | `SPRING_PROFILES_ACTIVE=prod` sans fichier associé | Faible | ❌ Présent | `k8s/api-deployment.yaml` |
| 8 | Doublon `jwt.expiration` (historique) | Moyenne | ✅ Corrigé | `application.properties` |

**Conclusion** : sur 8 anomalies recensées, **2 sont déjà corrigées** (dont la plus critique, qui rendait l'application entière inutilisable), et **6 restent ouvertes** — toutes de sévérité faible à moyenne, principalement des incohérences de documentation/configuration plutôt que des failles fonctionnelles ou de sécurité actives. Aucune des 6 anomalies restantes ne compromet la sécurité ni l'intégrité des données ; elles relèvent de la cohérence et de la finition du projet.
