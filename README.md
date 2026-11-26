# security-scan

GitHub Actions workflows for SonarQube Cloud, Coverity Scan and Trivy, for a Maven app, a Gradle app and an npm frontend (JS or TS). You test them here, then copy them to each app's own repo.

```
.github/
  actions/
    sonar/action.yml        composite action: Sonar scan, any build tool
    coverity/action.yml     composite action: Coverity capture + upload, any build tool
    trivy/action.yml        composite action: Docker image build + Trivy scan
  workflows/
    security-maven.yml      Maven setup + the 3 scanners      -> apps/maven-app
    security-gradle.yml     Gradle setup + the 3 scanners     -> apps/gradle-app
    security-frontend.yml   npm setup + the 3 scanners        -> apps/frontend
```

The scanners are **composite actions**: steps that run inside the calling job. Each `security-*.yml` sets up the language and builds with plain steps (`setup-java` + `mvn compile`, `setup-node` + `npm ci`, ...), then calls the scanner. The scanners know nothing about Maven, Gradle or npm.

| Action | Inputs |
|---|---|
| `sonar` | `app_dir`, `project_key`, `organization`, `token`, `args` (extra scanner args, e.g. `-Dsonar.java.binaries=...`) |
| `coverity` | `app_dir`, `build_command` (the build Coverity records; default `true` = nothing to build, source files like JS/TS are read directly), `token`, `email`, `project` |
| `trivy` | `app_dir`, `image` (local image name, default `local/app:scan`) |

Secrets are passed to the actions explicitly as inputs (e.g. `token: ${{ secrets.SONAR_TOKEN }}`). The Coverity tool is downloaded on every run (not cached).

For Java, Sonar needs the compiled classes (`sonar.java.binaries`) and, for accurate rules, the dependency jars (`sonar.java.libraries`, pointed at the local Maven/Gradle cache). See `security-maven.yml` / `security-gradle.yml`.

The three jobs run in parallel, every time the workflow runs:

| Job | What it does | Fails when |
|---|---|---|
| `sonar` | SAST + code quality on the app folder. Builds without tests. | The quality gate fails |
| `coverity` | Records the build without tests (Java) or reads the source with `coverity capture` (JS/TS), then uploads to Coverity Scan | The build or upload breaks, or Coverity Scan rejects the upload (e.g. a previous build is still in its queue). Defects never fail it: Coverity analyzes later on its own servers, and results arrive on the dashboard and by email. |
| `trivy` | Builds the app's Docker image and prints every vulnerability with its severity (base image packages + dependencies inside the image) | Anything HIGH or CRITICAL is found |

**For now all three workflows are manual only.** Start one from **Actions > Security (maven/gradle/frontend) > Run workflow** (a new run, not "Re-run": a re-run uses the workflow files from the original commit). To also run on PRs and pushes to `main`, uncomment `pull_request` and `push` at the top of each workflow. Then Coverity runs on PRs too: each run uses a build from the free Coverity Scan quota and replaces the project's previous results.

## Demo apps
`apps/` contains three small demo apps. Each is a tiny HTTP server on port 8080 with a self-contained (multi-stage) Dockerfile:
- `maven-app`: Java 21, Maven, one dependency (`commons-lang3`)
- `gradle-app`: Java 21, Gradle wrapper, `gradle.lockfile`
- `frontend`: TypeScript with one plain `.js` file, npm. The runtime image drops npm/yarn/corepack, because their bundled packages have known vulns.

The Dockerfiles currently use **deliberately vulnerable base images**, to show the Trivy HIGH/CRITICAL block working: `eclipse-temurin:21.0.1_12-jre-jammy` (old) for both Java apps and `node:24.22-bookworm` (full Debian) for the frontend. Switch to `eclipse-temurin:21-jre-alpine` / `node:24-alpine` to make Trivy pass.

## Other languages and build tools
Language setup and build steps go in each app's main workflow as plain steps; the scanner actions stay the same. For C#, the main workflow can run the `dotnet sonarscanner` steps itself instead of using the `sonar` action.

| Language / tool | Sonar (generic scanner) | Coverity | Trivy |
|---|---|---|---|
| Java (Maven, Gradle), Kotlin, Scala | Yes, compile first and pass `sonar.java.binaries` / `sonar.java.libraries` | Yes, records the build | Yes |
| JS / TS (npm, yarn, pnpm) | Yes | Yes, reads the source | Yes |
| Python, PHP, Ruby | Yes, no build needed | Yes, reads the source | Yes |
| Go | Yes | Yes, records `go build` | Yes |
| **C# / .NET** | **No.** Needs SonarScanner for .NET (see below). | Yes, records `dotnet build` | Yes |
| **C / C++** | **Only with a compilation database** (see below) | Yes, records the build | Yes |

Trivy only needs a `Dockerfile`. Coverity needs a build command for compiled languages and none for scripting languages.

**Why C# doesn't work with the generic Sonar scanner.** Sonar's C# rules are Roslyn analyzers that run inside the C# compiler during `dotnet build`. The compiler gives them every type, every project and package reference from the `.csproj` files, and the full meaning of the code. The generic scanner has no C# compiler and doesn't understand `.csproj` / `.sln` files, so it skips C#. Use SonarScanner for .NET, which wraps the build:

```bash
dotnet tool install --global dotnet-sonarscanner
dotnet sonarscanner begin /k:<project key> /o:<organization> /d:sonar.token=<token> /d:sonar.host.url=https://sonarcloud.io
dotnet build
dotnet sonarscanner end /d:sonar.token=<token>
```

`begin` injects the analyzers and settings into the build, `dotnet build` runs the analysis while compiling, and `end` uploads the results.

**Ready-made template:** `.github/workflows/security-dotnet.yml.disabled` has a complete C# workflow: the Sonar .NET steps inline, plus the existing `coverity` (with `dotnet build`) and `trivy` actions. GitHub ignores it because of the `.disabled` extension. To use it, rename it to `security-dotnet.yml`, then set `APP_DIR` and the Sonar project key.

**Why C/C++ needs extra input.** The same `.c` / `.cpp` file can mean different things depending on how it's compiled: include paths (`-I`), macros (`-D`), the language standard (`-std=...`) and the target platform all change what the preprocessor produces. Without those flags Sonar can't find the headers or resolve the macros, so it refuses to analyze C/C++. Give it the compiler calls in one of two ways:
- a **compilation database**: CMake writes one with `-DCMAKE_EXPORT_COMPILE_COMMANDS=ON`. Pass `-Dsonar.cfamily.compile-commands=build/compile_commands.json` in the Sonar args.
- Sonar's **build wrapper**: run the build through it, then pass its output folder.

Coverity doesn't have either problem: for compiled languages it records the build itself, so it sees every compiler call with all its flags.

## Testing in this repo
- **Sonar** needs three projects, one per app. Their keys are set in `project_key` in each `security-*.yml`: `BlagoTcherecharov_security-scan-pipeline`, `..._gradle-app` and `..._frontend`. See [SonarQube Cloud projects](#sonarqube-cloud-projects) below.
- **Coverity:** all three apps share the same secrets, so they upload to the same Coverity Scan project. Coverity Scan takes one build at a time and each upload replaces the previous results, so run one app, wait for its analysis email, check the results, then run the next. Uploading while a build is still queued fails the `coverity` job.

## Moving to an app repo
1. Copy the `.github/actions/` folder and that app's workflow (e.g. `security-maven.yml` into `.github/workflows/`) to the app repo.
2. In the app's workflow, set:
   - `APP_DIR: '.'` in the `env` block at the top
   - `project_key` to the key Sonar shows for that repo

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
- **Frontend:** commit `package-lock.json`.

### Branch protection (once PR triggers are on)
A workflow can't block a push: by the time it runs, the commit is already on GitHub. To block code, work through PRs: protect `main`, require pull requests, and mark the checks as required (`sonar`, `coverity`, `trivy`). Any required check that fails then blocks the merge.

## Notes
- Java 21 / Node 24 are set in the `setup-java` / `setup-node` steps of each `security-*.yml`.
- Actions are pinned to commit SHAs (version in the comment next to each). Bump them by hand when you update.
