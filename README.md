# security-scan

GitHub Actions workflows for SonarQube Cloud, Coverity Scan and Trivy, for a Maven app, a Gradle app and an npm frontend (JS or TS). You test them here, then copy them to each app's own repo.

```
.github/workflows/
  security-maven.yml      Sonar + Coverity for Maven,  calls trivy.yml   -> apps/maven-app
  security-gradle.yml     Sonar + Coverity for Gradle, calls trivy.yml   -> apps/gradle-app
  security-frontend.yml   Sonar + Coverity for npm,    calls trivy.yml   -> apps/frontend
  trivy.yml               reusable: builds the app's Docker image and scans it (input: app_dir)
```

The three jobs run in parallel, every time the workflow runs:

| Job | What it does | Fails when |
|---|---|---|
| `sonar` | SAST + code quality on the app folder. Builds without tests. | The quality gate fails |
| `coverity` | Builds without tests (Java) or captures the source with `cov-capture` (JS/TS), then uploads to Coverity Scan | The build or upload breaks. Defects never fail it: Coverity analyzes later on its own servers, and results arrive on the dashboard and by email. |
| `trivy` | Builds the app's Docker image and prints every vulnerability with its severity (base image packages + dependencies inside the image) | Anything HIGH or CRITICAL is found |

**For now all three workflows are manual only.** Start one from **Actions > Security (maven/gradle/frontend) > Run workflow** (a new run, not "Re-run": a re-run uses the workflow files from the original commit). To also run on PRs and pushes to `main`, uncomment `pull_request` and `push` at the top of each workflow. Then Coverity runs on PRs too: each run uses a build from the free Coverity Scan quota and replaces the project's previous results.

## Demo apps
`apps/` contains three small demo apps. Each is a tiny HTTP server on port 8080 with a self-contained (multi-stage) Dockerfile:
- `maven-app`: Java 21, Maven, one dependency (`commons-lang3`)
- `gradle-app`: Java 21, Gradle wrapper, Sonar plugin and `gradle.lockfile` already set up
- `frontend`: TypeScript with one plain `.js` file, npm. The runtime image drops npm/yarn/corepack, because their bundled packages have known vulns.

The Dockerfiles currently use **deliberately vulnerable base images**, to show the Trivy HIGH/CRITICAL block working: `eclipse-temurin:21.0.1_12-jre-jammy` (old) for both Java apps and `node:24.22-bookworm` (full Debian) for the frontend. Switch to `eclipse-temurin:21-jre-alpine` / `node:24-alpine` to make Trivy pass.

## Testing in this repo
- **Sonar** needs three projects, one per app. Their keys are set in `SONAR_PROJECT_KEY` at the top of each workflow: `BlagoTcherecharov_security-scan-pipeline`, `..._gradle-app` and `..._frontend`. See [SonarQube Cloud projects](#sonarqube-cloud-projects) below.
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

### SonarQube Cloud projects
For each project:
- **Create it yourself before the first run.** If an analysis arrives for a key that doesn't exist, SonarQube Cloud creates the project automatically, with main branch `master`. Your repo uses `main`, so the run then fails with `Organization is not allowed to access data from non main branches`.
- **Main branch must be `main`.** Check the branch dropdown on the project page. To fix a project stuck on `master`, rename the branch (branch dropdown > Manage branches) or delete and recreate the project with main branch `main`.
- **Automatic Analysis must be off** (Administration > Analysis Method). It's on for a project created by importing the repo from GitHub. It scans the whole repo without building (no Java), and Sonar rejects the CI analysis while it's on: `You are running CI analysis while Automatic Analysis is enabled`.

### App requirements
- **Dockerfile:** must be named `Dockerfile`, sit at the root of the app folder, and be **self-contained** (multi-stage, builds its own jar or bundle). Nothing is built before `docker build`.
- **Gradle:** add the Sonar plugin, `id("org.sonarqube") version "7.6.0.18749"`.
- **Frontend:** commit `package-lock.json`.

### Branch protection (once PR triggers are on)
A workflow can't block a push: by the time it runs, the commit is already on GitHub. To block code, work through PRs: protect `main`, require pull requests, and mark the checks as required (`sonar`, `coverity`, `trivy / trivy`). Any required check that fails then blocks the merge.

## Notes
- Java 21 / Node 24 are set in the `setup-java` / `setup-node` steps.
- Actions are pinned to commit SHAs (version in the comment next to each). Bump them by hand when you update.
