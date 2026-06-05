# Protocole d'Injection de Vulnérabilités — DevSecOps PFE

## Objectif

Ce document décrit les **10 vulnérabilités intentionnelles** conçues pour valider chaque étape du pipeline DevSecOps (SAST, DAST, SCA). Chaque injection est activable par une modification isolée et réversible du code source, permettant de démontrer la capacité de détection des outils intégrés.

> **AVERTISSEMENT** : Ces injections sont strictement réservées à un environnement de test/staging. Ne jamais appliquer ces modifications en production.

---

## Tableau des Vulnérabilités

| ID | Vulnérabilité | Catégorie OWASP | Fichier | Modification | Outil de détection | CVSS estimé |
|----|--------------|----------------|---------|-------------|-------------------|-------------|
| INJ-01 | JWT Secret faible (hardcodé) | A02:2021 — Cryptographic Failures | `application.properties` + `JwtTokenProvider.java` | Hardcoder `jwt.secret=secret` au lieu de la variable d'environnement | SAST (SonarQube, Semgrep) | 7.5 |
| INJ-02 | Absence d'expiration JWT | A02:2021 — Cryptographic Failures | `JwtTokenProvider.java` | Supprimer l'appel `.setExpiration()` lors de la création du token | SAST (SonarQube, Semgrep) | 6.5 |
| INJ-03 | Injection SQL via JPQL | A03:2021 — Injection | `UserRepository.java` | Remplacer le paramètre bindé `:username` par une concaténation de chaîne dans `@Query` | SAST (SonarQube, Semgrep) | 9.8 |
| INJ-04 | BOLA (Broken Object Level Authorization) | A01:2021 — Broken Access Control | `UserService.java` | Supprimer la vérification de propriétaire (`isOwnerOrAdmin`) avant l'accès aux données utilisateur | DAST (OWASP ZAP) | 7.5 |
| INJ-05 | Mass Assignment sur le rôle | A01:2021 — Broken Access Control | `User.java` | Supprimer `@JsonIgnore` sur le champ `role`, permettant à un utilisateur de s'attribuer le rôle ADMIN | DAST (OWASP ZAP, Postman) | 6.5 |
| INJ-06 | BFLA (Broken Function Level Authorization) | A01:2021 — Broken Access Control | `SecurityConfig.java` | Supprimer `.hasRole("ADMIN")` sur `/api/admin/**`, exposant les endpoints d'administration | DAST (OWASP ZAP) | 8.0 |
| INJ-07 | CVE connue dans une dépendance Maven | A06:2021 — Vulnerable Components | `pom.xml` | Downgrader `spring-boot-starter-parent` de `3.2.5` à `2.6.1` (inclut Log4Shell CVE-2021-44228) | SCA (OWASP Dependency-Check, Trivy) | 10.0 |
| INJ-08 | CORS permissif (wildcard) | A05:2021 — Security Misconfiguration | `SecurityConfig.java` | Remplacer `allowedOrigins("http://localhost:3000")` par `allowedOrigins("*")` | SAST / Config Audit (Semgrep, ZAP) | 5.3 |
| INJ-09 | Image Docker avec CVE connues | A06:2021 — Vulnerable Components | `Dockerfile` | Remplacer `eclipse-temurin:17-jre-alpine` par `ubuntu:20.04` (contient des CVE connues) | SCA (Trivy, Grype) | 7.8 |
| INJ-10 | Combinaison INJ-01 + INJ-04 | A01+A02:2021 | Multiple fichiers | Appliquer INJ-01 (secret faible) et INJ-04 (BOLA) simultanément pour démontrer un scénario d'attaque chaîné | SAST + DAST | 9.0 |

---

## Détails des Injections

### INJ-01 — JWT Secret faible

**Fichiers concernés** : `src/main/resources/application.properties`, `JwtTokenProvider.java`

**Modification** :
```properties
# AVANT (sécurisé)
jwt.secret=${JWT_SECRET}

# APRÈS (vulnérable)
jwt.secret=secret
```

**Impact** : Un attaquant peut forger des tokens JWT valides avec un secret devinable, contournant l'authentification.

---

### INJ-02 — Absence d'expiration JWT

**Fichier** : `src/main/java/com/devsecops/usermgmt/security/JwtTokenProvider.java`

**Modification** : Supprimer la ligne `.setExpiration(...)` dans la méthode de génération du token.

**Impact** : Les tokens ne expirent jamais, permettant une réutilisation illimitée en cas de vol.

---

### INJ-03 — Injection SQL JPQL

**Fichier** : `src/main/java/com/devsecops/usermgmt/repository/UserRepository.java`

**Modification** :
```java
// AVANT (sécurisé — paramètre bindé)
@Query("SELECT u FROM User u WHERE u.username = :username")
Optional<User> findByUsername(@Param("username") String username);

// APRÈS (vulnérable — concaténation)
@Query("SELECT u FROM User u WHERE u.username = '" + "' + ?1 + '")
Optional<User> findByUsername(String username);
```

**Impact** : Permet l'exécution de requêtes SQL arbitraires, pouvant mener à l'exfiltration de données.

---

### INJ-04 — BOLA

**Fichier** : `src/main/java/com/devsecops/usermgmt/service/UserService.java`

**Modification** : Supprimer la vérification `isOwnerOrAdmin(id, username)` dans les méthodes `getUserById`, `updateUser`, `deleteUser`.

**Impact** : Tout utilisateur authentifié peut accéder, modifier ou supprimer les données de n'importe quel autre utilisateur.

---

### INJ-05 — Mass Assignment

**Fichier** : `src/main/java/com/devsecops/usermgmt/entity/User.java`

**Modification** : Supprimer l'annotation `@JsonIgnore` sur le champ `role`.

**Impact** : Un utilisateur peut envoyer `"role": "ADMIN"` dans une requête d'inscription ou de mise à jour pour s'élever en privilèges.

---

### INJ-06 — BFLA

**Fichier** : `src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java`

**Modification** :
```java
// AVANT (sécurisé)
.requestMatchers("/api/admin/**").hasRole("ADMIN")

// APRÈS (vulnérable)
.requestMatchers("/api/admin/**").authenticated()
```

**Impact** : Tout utilisateur authentifié (y compris `USER`) peut accéder aux endpoints d'administration.

---

### INJ-07 — CVE Maven (Log4Shell)

**Fichier** : `pom.xml`

**Modification** :
```xml
<!-- AVANT (sécurisé) -->
<version>3.2.5</version>

<!-- APRÈS (vulnérable — inclut Log4j 2.14.1) -->
<version>2.6.1</version>
```

**Impact** : Introduction de CVE-2021-44228 (Log4Shell), permettant une exécution de code à distance (RCE) via JNDI.

---

### INJ-08 — CORS permissif

**Fichier** : `src/main/java/com/devsecops/usermgmt/config/SecurityConfig.java`

**Modification** :
```java
// AVANT (sécurisé)
configuration.setAllowedOrigins(List.of("http://localhost:3000"));

// APRÈS (vulnérable)
configuration.setAllowedOrigins(List.of("*"));
```

**Impact** : N'importe quel site web peut effectuer des requêtes cross-origin vers l'API, facilitant les attaques CSRF et le vol de données.

---

### INJ-09 — Image Docker vulnérable

**Fichier** : `Dockerfile`

**Modification** :
```dockerfile
# AVANT (sécurisé)
FROM eclipse-temurin:17-jre-alpine

# APRÈS (vulnérable)
FROM ubuntu:20.04
```

**Impact** : L'image Ubuntu 20.04 contient de nombreuses CVE connues dans ses paquets système, augmentant la surface d'attaque.

---

### INJ-10 — Combinaison (INJ-01 + INJ-04)

**Fichiers** : Ceux de INJ-01 et INJ-04.

**Modification** : Appliquer les deux injections simultanément.

**Impact** : Un attaquant peut forger un token JWT (secret faible) puis exploiter l'absence de vérification de propriétaire pour accéder aux données de n'importe quel utilisateur. Ce scénario chaîné simule une attaque réaliste multi-vecteur.

---

## Protocole d'Injection par Commit Git

Pour chaque vulnérabilité, suivre ce protocole standardisé :

### Étapes

1. **Créer une branche dédiée** :
   ```bash
   git checkout -b vuln/INJ-XX-description
   ```

2. **Appliquer la modification** décrite dans le tableau ci-dessus.

3. **Commiter avec un message normalisé** :
   ```bash
   git commit -m "vuln(INJ-XX): <description courte de la vulnérabilité>"
   ```
   Exemples :
   - `vuln(INJ-01): hardcode weak JWT secret`
   - `vuln(INJ-03): introduce JPQL injection via string concatenation`
   - `vuln(INJ-07): downgrade Spring Boot to 2.6.1 for Log4Shell`

4. **Pousser la branche** et observer le pipeline CI/CD :
   ```bash
   git push origin vuln/INJ-XX-description
   ```

5. **Documenter les résultats** : capturer les alertes générées par les outils (SonarQube, OWASP ZAP, Trivy, Dependency-Check).

6. **Révoquer l'injection** : revenir sur `main` et supprimer la branche de test.

### Convention de nommage

| Élément | Format | Exemple |
|---------|--------|---------|
| Branche | `vuln/INJ-XX-slug` | `vuln/INJ-03-sql-injection` |
| Commit | `vuln(INJ-XX): description` | `vuln(INJ-07): downgrade to Spring Boot 2.6.1` |
| Tag (optionnel) | `vuln-INJ-XX` | `vuln-INJ-03` |

---

## Matrice Outils × Vulnérabilités

| Outil | Type | INJ-01 | INJ-02 | INJ-03 | INJ-04 | INJ-05 | INJ-06 | INJ-07 | INJ-08 | INJ-09 | INJ-10 |
|-------|------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|
| SonarQube | SAST | ✅ | ✅ | ✅ | — | — | — | — | ✅ | — | ✅ |
| Semgrep | SAST | ✅ | ✅ | ✅ | — | — | — | — | ✅ | — | ✅ |
| OWASP ZAP | DAST | — | — | — | ✅ | ✅ | ✅ | — | — | — | ✅ |
| Dependency-Check | SCA | — | — | — | — | — | — | ✅ | — | — | — |
| Trivy | SCA | — | — | — | — | — | — | ✅ | — | ✅ | — |
| Grype | SCA | — | — | — | — | — | — | ✅ | — | ✅ | — |
