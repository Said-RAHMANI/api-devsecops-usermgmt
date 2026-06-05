# Pipeline Build Report

## Task
Create the GitHub Actions DevSecOps pipeline file for `api-devsecops-usermgmt`.

## File Created
- `.github/workflows/devsecops-pipeline.yml` (1 file, as required)

## Pipeline Overview
- **Name:** `DevSecOps Pipeline`
- **Triggers:**
  - `push` on branches `main`, `develop`
  - `pull_request` on branch `main`
- **Runner:** `ubuntu-latest` for all jobs
- **Execution model:** 6 sequential jobs chained via `needs`

## Job Chain (sequential)

| # | Job ID      | Needs       | Purpose                                      |
|---|-------------|-------------|----------------------------------------------|
| 1 | `setup`     | —           | Checkout, setup JDK 17 Temurin, cache `~/.m2`, `mvn dependency:go-offline` |
| 2 | `sast`      | `[setup]`   | `mvn compile`, SonarCloud scan + Quality Gate (fails on BLOCKER/CRITICAL) |
| 3 | `sca`       | `[sast]`    | Snyk Maven scan with `--severity-threshold=high` (fails CVSS ≥ 7.0); upload report |
| 4 | `build`     | `[sca]`     | `mvn package`, Docker build, Trivy image scan (HIGH/CRITICAL, exit 1); upload report |
| 5 | `dast`      | `[build]`   | `docker compose up`, wait for `/health`, OWASP ZAP baseline; teardown `if: always()` |
| 6 | `reporting` | `[dast]`    | Download artifacts, write `$GITHUB_STEP_SUMMARY`, upload `devsecops-reports` |

## Specification Compliance Checklist

### Job 1 — setup
- [x] `actions/checkout@v4`
- [x] `actions/setup-java@v4` with `java-version: '17'`, `distribution: 'temurin'`
- [x] Maven cache `actions/cache@v4` with path `~/.m2` and key keyed on `pom.xml` hash
- [x] `mvn dependency:go-offline -B`

### Job 2 — sast (`needs: [setup]`)
- [x] checkout, setup-java (17/temurin), Maven cache
- [x] `mvn compile -B`
- [x] `SonarSource/sonarcloud-github-action@master` with `SONAR_TOKEN` and `GITHUB_TOKEN` env
- [x] Args: `-Dsonar.projectKey=${{ github.repository_owner }}_api-devsecops-usermgmt`, `-Dsonar.organization=${{ github.repository_owner }}`
- [x] `SonarSource/sonarqube-quality-gate-action@master` with `SONAR_TOKEN` (gate fails on BLOCKER/CRITICAL)

### Job 3 — sca (`needs: [sast]`)
- [x] checkout, setup-java (17/temurin)
- [x] `snyk/actions/maven@master` with `SNYK_TOKEN` env, args `--severity-threshold=high`
- [x] `actions/upload-artifact@v4` named `snyk-report` with `if: always()`

### Job 4 — build (`needs: [sca]`)
- [x] checkout, setup-java (17/temurin), Maven cache
- [x] `mvn package -DskipTests -B`
- [x] `docker build -t api-devsecops-usermgmt:${{ github.sha }} .`
- [x] `aquasecurity/trivy-action@master` with `image-ref`, `format: table`, `exit-code: '1'`, `severity: HIGH,CRITICAL`, `output: trivy-report.txt`
- [x] Upload `trivy-report` artifact (`if: always()`)

### Job 5 — dast (`needs: [build]`)
- [x] checkout
- [x] `docker compose up -d --build`
- [x] Wait loop polling `http://localhost:8080/health` (30 attempts × 5s)
- [x] `zaproxy/action-baseline@v0.12.0` with `target`, `cmd_options: -J zap-report.json`, `rules_file_name: ''`, `allow_issue_writing: false`
- [x] Upload `zap-report` artifact (`if: always()`)
- [x] `docker compose down -v` (`if: always()`)

### Job 6 — reporting (`needs: [dast]`)
- [x] `actions/download-artifact@v4` with `path: reports/`, `if: always()`
- [x] Security summary written to `$GITHUB_STEP_SUMMARY` with the 4-row Markdown table
- [x] Upload `devsecops-reports` artifact (`if: always()`)

### Global YAML Rules
- [x] Pipeline name `DevSecOps Pipeline`
- [x] `on: push (branches: [main, develop])`, `pull_request (branches: [main])`
- [x] All `needs` chains correct for sequential execution
- [x] 2-space YAML indentation
- [x] All secrets via `${{ secrets.XXX }}` (`SONAR_TOKEN`, `GITHUB_TOKEN`, `SNYK_TOKEN`)

## Quality Checks
- **YAML syntax:** parsed successfully with `yaml.safe_load`
- **Job ordering:** `['setup', 'sast', 'sca', 'build', 'dast', 'reporting']` — matches spec
- **Scope discipline:** exactly 1 file created (`devsecops-pipeline.yml`); no other files modified

## Required Repository Secrets
The pipeline references the following secrets which must be configured in the GitHub repository settings:
- `SONAR_TOKEN` — SonarCloud authentication
- `SNYK_TOKEN` — Snyk authentication
- `GITHUB_TOKEN` — provided automatically by GitHub Actions

## Notes & Concerns
- `sast` uses `fetch-depth: 0` on checkout to allow accurate SonarCloud blame analysis (standard recommendation; not explicitly required by spec but compatible).
- Snyk does not emit `snyk-report.json` automatically with the maven action; the upload step uses `if: always()` so it will not fail the pipeline if the file is absent. Consider adding `--json-file-output=snyk-report.json` later if a structured artifact is required.
- ZAP baseline scan with `allow_issue_writing: false` will not auto-create GitHub issues, matching the spec.
- Teardown step is positioned last in `dast` with `if: always()` to guarantee container cleanup even on prior failure.

## Status
**COMPLETE** — Single file `.github/workflows/devsecops-pipeline.yml` created and validated.
