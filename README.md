# security-scan

GitHub Actions workflows for SonarQube Cloud, Coverity Scan and Trivy, for a Maven app, a Gradle app and an npm frontend (JS or TS). You test them here, then copy them to each app's own repo.

```
.github/workflows/
  security-maven.yml      Sonar + Coverity for Maven,  calls trivy.yml   -> apps/maven-app
  security-gradle.yml     Sonar + Coverity for Gradle, calls trivy.yml   -> apps/gradle-app
  security-frontend.yml   Sonar + Coverity for npm,    calls trivy.yml   -> apps/frontend
  trivy.yml               reusable: builds the app's Docker image and scans it (input: app_dir)
```

| Job | Runs on | What it does |
|---|---|---|
| `sonar` | PRs to `main` + push to `main` | SAST + code quality, PR decoration, fails on quality gate. Builds without tests. |
| `coverity` | push to `main` | Builds without tests (Java) or captures source (JS/TS) and uploads to Coverity Scan |
| `trivy` | push to `main` | Builds the app's Docker image and lists its vulnerabilities (base image packages + dependencies inside the image) in the job log. Doesn't fail the job and doesn't save anything. |

**For now all three workflows are manual only.** Start one from **Actions > Security (maven/gradle/frontend) > Run workflow**. A manual run does Sonar, Coverity and Trivy in one go. To switch to automatic runs, uncomment `pull_request` and `push` at the top of each workflow.

Coverity doesn't run on PRs because free Coverity Scan has weekly build caps and keeps only one snapshot per project. A PR build would overwrite `main`'s results. A green Coverity job only means the upload worked: the analysis runs later on Coverity's servers, and the results show up on the Coverity Scan dashboard and by email.

## Demo apps
`apps/` contains three small demo apps. Each is a tiny HTTP server on port 8080 with a self-contained (multi-stage) Dockerfile:
- `maven-app`: Java 21, Maven, one dependency (`commons-lang3`)
- `gradle-app`: Java 21, Gradle wrapper, Sonar plugin and `gradle.lockfile` already set up
- `frontend`: TypeScript with one plain `.js` file, npm. The runtime image drops npm/yarn/corepack, because their bundled packages have known vulns.

## Testing in this repo
- **Sonar** needs three projects, one per app. Their keys are set in `SONAR_PROJECT_KEY` at the top of each workflow: `BlagoTcherecharov_security-scan-pipeline`, `..._gradle-app` and `..._frontend`. Create the last two in Sonar with exactly those keys, or change the keys in the workflows.
- **Coverity:** all three apps share the same secrets, so they upload to the same Coverity Scan project, and each upload replaces the previous snapshot. Run one app, wait for its analysis email, check the results, then run the next.

## Moving to an app repo
1. Copy `trivy.yml` and that app's workflow (e.g. `security-maven.yml`) to the app repo's `.github/workflows/`.
2. In the workflow, set:
   - `APP_DIR: '.'`
   - `app_dir: '.'` in the `trivy` job at the bottom
   - `SONAR_PROJECT_KEY` to the key Sonar shows for that repo

## Setup

Repos must be **public**. The free Coverity Scan and Sonar Cloud tiers depend on it.

### GitHub secrets
**Settings > Secrets and variables > Actions > Secrets** (in this repo, and later in each app repo):

| Secret | Value |
|---|---|
| `SONAR_TOKEN` | Sonar Cloud: My Account > Security > Generate token |
| `SONAR_ORGANIZATION` | Your Sonar Cloud organization key (`blagotcherecharov`) |
| `COVERITY_SCAN_TOKEN` | That repo's Coverity Scan project token |
| `COVERITY_SCAN_EMAIL` | The email on your Coverity Scan account |
| `COVERITY_SCAN_PROJECT` | That repo's exact Coverity Scan project name, e.g. `BlagoTcherecharov/security-scan-pipeline` |

Trivy needs no secrets.

### App requirements
- **Dockerfile:** must be named `Dockerfile`, sit at the root of the app folder, and be **self-contained** (multi-stage, builds its own jar or bundle). Nothing is built before `docker build`.
- **Gradle:** add the Sonar plugin, `id("org.sonarqube") version "7.6.0.18749"`.
- **Frontend:** commit `package-lock.json`.

### Branch protection (once PR triggers are on)
Protect `main` and require the **`sonar`** check, so a failing quality gate blocks the merge.

## Notes
- Java 21 / Node 24 are set in the `setup-java` / `setup-node` steps.
- Actions are pinned to commit SHAs (version in the comment next to each). Bump them by hand when you update.
