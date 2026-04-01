# Running App

Running App is a monorepo with two MVP deliverables:

- `android-app/`: a native Android client built with Kotlin, Compose, Room, Hilt, and Retrofit
- `backend/`: a stateless Go HTTP service that streams generation progress and finishes with one normalized workout envelope

The Android app owns local user data, saved workouts, and session history. The backend only validates requests, streams realtime generation progress, and emits the final workout envelope in the terminal `completed` event.

## Repository Layout

- `android-app/`: Gradle root, Android application module, JVM unit tests, and JaCoCo coverage reporting
- `backend/`: Go module, HTTP server, provider adapters, unit tests, and Docker packaging
- `docs/contracts/`: authoritative API and schema documentation shared by Android and backend
- `.github/workflows/ci.yml`: GitHub Actions workflow for Android and backend validation
- `scripts/`: shell entrypoints used by `make`, local smoke checks, and coverage gates

## Required Toolchains

- JDK 21 for Android Gradle tasks
- Android SDK Platform 36 and Build Tools 36.0.0
- Go 1.26.1
- Docker with a running daemon for backend image builds

On macOS, the repo scripts auto-detect JDK 21 when `JAVA_HOME` is unset. For direct Gradle commands, set `JAVA_HOME` yourself if your shell defaults to another JDK:

```bash
export JAVA_HOME="$(
  /usr/libexec/java_home -v 21
)"
```

## Common Commands

- `make format`: format Go sources
- `make lint`: shell syntax checks, Go format verification, `go vet`, and Android Gradle smoke validation
- `make test`: smoke-check the repo wiring, coverage tasks, and CI workflow presence
- `make coverage`: run Android non-UI coverage verification and backend package coverage gates
- `make ci`: run the same Android and backend checks used by GitHub Actions
- `make android-smoke`: verify the Android Gradle wrapper and module wiring with `./gradlew help`
- `make backend-smoke`: verify Go module resolution and run the backend test suite
- `make docker-smoke`: verify the backend Docker build context and daemon availability
- `make android-ci`: run `assembleDebug`, `testDebugUnitTest`, `lintDebug`, and Android JaCoCo verification
- `make backend-ci`: run backend coverage, `go vet`, and `docker build -t running-app-backend .`
- `make android-coverage`: run only the Android JVM coverage report and threshold gate
- `make backend-coverage`: run backend tests plus the per-package coverage threshold gate
- `make android-build`: build the debug APK from `android-app/`
- `make android-build-release`: build the signed release APK from `android-app/`
- `make android-install`: install the debug APK to a connected emulator/device
- `make android-install-release`: install the signed release APK to a connected device
- `make backend-build`: build `backend/bin/running-app-backend`
- `make backend-run`: start the backend from `backend/` with local `.env` support

## Backend: Local Run

Common local commands from the repo root:

```bash
make backend-build
make backend-run
make android-build
make android-install
make android-build-release
```

If you need a non-default Android backend URL:

```bash
make android-build TRAINING_API_BASE_URL=http://192.168.1.50:8080/
```

Release builds read the backend URL from one of these sources, in order:

- `TRAINING_API_BASE_URL` or `TRAINING_API_RELEASE_BASE_URL` passed to `make`
- `RUNNING_APP_RELEASE_TRAINING_API_BASE_URL` from the environment
- `runningAppReleaseTrainingApiBaseUrl` in `android-app/local.properties`

The `android-app/local.properties` file is already git-ignored, so it is the cleanest place to keep a private production endpoint. For example:

```properties
runningAppReleaseTrainingApiBaseUrl=https://api.example.com/
```

Release signing is read from `android-app/keystore.properties` or `RUNNING_APP_RELEASE_KEYSTORE_*` environment variables. A local `keystore.properties` file looks like this:

```properties
storeFile=running-app-release.jks
storePassword=change-me
keyAlias=runningapprelease
keyPassword=change-me
```

Then assemble the signed release APK from the repo root with:

```bash
make android-build-release
```

The backend defaults to `127.0.0.1:8080` and the `codex` provider, so fresh local runs stay bound to loopback instead of every interface. For repeatable local testing without the Codex CLI, use the static provider:

```bash
cd backend
export RUNNING_APP_PROVIDER=static
go run ./cmd/server
```

On startup the backend now automatically reads local variables from `backend/.env` when launched from the repo root, or `.env` when launched from the `backend/` directory. Explicit process environment variables still win over file values. Use `RUNNING_APP_ENV_FILE` to point at a custom env file path.

Required and optional backend environment variables:

- `RUNNING_APP_HTTP_ADDR`: listen address, default `127.0.0.1:8080`
- `RUNNING_APP_PROVIDER`: `codex` or `static`, default `codex`
- `RUNNING_APP_REQUEST_TIMEOUT`: generation timeout, default `90s`
- `RUNNING_APP_SHUTDOWN_TIMEOUT`: graceful shutdown timeout, default `10s`
- `RUNNING_APP_READ_HEADER_TIMEOUT`: default `5s`
- `RUNNING_APP_READ_TIMEOUT`: default `15s`
- `RUNNING_APP_WRITE_TIMEOUT`: default `2m` and must be greater than `RUNNING_APP_REQUEST_TIMEOUT`
- `RUNNING_APP_IDLE_TIMEOUT`: default `30s`
- `RUNNING_APP_LOG_LEVEL`: `DEBUG`, `INFO`, `WARN`, or `ERROR`, default `INFO`
- `RUNNING_APP_ENV_FILE`: optional explicit path to a dotenv file
- `RUNNING_APP_CODEX_BINARY`: Codex CLI binary path, default `codex`
- `RUNNING_APP_CODEX_WORKDIR`: optional working directory passed to `codex exec --cd`
- `RUNNING_APP_CODEX_MODEL`: optional model override
- `RUNNING_APP_CODEX_PROFILE`: optional Codex profile name
- `RUNNING_APP_CODEX_SANDBOX`: Codex sandbox mode, default `read-only`

Training generation success responses now use Server-Sent Events. For manual local checks, keep the connection open and wait for the terminal `completed` or `error` event instead of expecting one JSON success body. Validation failures still return ordinary JSON `4xx` responses before the stream starts.
Treat the request as successful only if the SSE stream is non-empty and ends with `completed`. A `200 OK` response with an empty body, malformed event payloads, or a stream that closes without `completed` or `error` is an invalid backend response.

Example local smoke test:

```bash
curl -N http://127.0.0.1:8080/v1/trainings/generate \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  --data-binary @request.json
```

Use the same request JSON shape documented in `docs/contracts/training-generation-api.md`. With `RUNNING_APP_PROVIDER=static`, the backend emits deterministic `log` events before the terminal `completed` event, which makes local Android and curl-based smoke tests easier to repeat.
Manual generation requests must set `request.locale` to `ru-RU` or `en-US`. Any other locale returns `400 invalid_request` before the SSE stream starts. The static provider returns deterministic workout copy in the requested locale, which makes it useful for smoke-testing both Russian and English responses from the same local backend.

## Android: Local Run

The Android client reads the backend base URL from the Gradle property `runningAppTrainingApiBaseUrl`. `debug` builds default to `http://10.0.2.2:8080/`, which matches an Android emulator talking to a backend running on the host machine. `release` builds read `runningAppReleaseTrainingApiBaseUrl` from `android-app/local.properties`, `RUNNING_APP_RELEASE_TRAINING_API_BASE_URL` from the environment, or an explicit Gradle property override. Cleartext traffic is allowed only in debug builds; release builds require an HTTPS backend URL.

Foreground session tracking features also require granting runtime location permission and, on Android 13+, notification permission.

Typical local flow:

```bash
cd android-app
./gradlew app:assembleDebug \
  -PrunningAppTrainingApiBaseUrl=http://10.0.2.2:8080/
./gradlew app:testDebugUnitTest
```

For a physical device, point the property at a reachable host, for example:

```bash
export RUNNING_APP_HTTP_ADDR=0.0.0.0:8080
./gradlew app:assembleDebug \
  -PrunningAppTrainingApiBaseUrl=http://192.168.1.50:8080/
```

For a production-oriented APK, configure a private HTTPS backend URL and local signing credentials, then build release:

```bash
cd android-app
printf '%s\n' 'runningAppReleaseTrainingApiBaseUrl=https://api.example.com/' >> local.properties
printf '%s\n' \
  'storeFile=running-app-release.jks' \
  'storePassword=change-me' \
  'keyAlias=runningapprelease' \
  'keyPassword=change-me' >> keystore.properties
./gradlew app:assembleRelease
```

The Android generation flow now keeps the successful request open as an SSE stream, appends `log` events to the realtime output card, and only reveals the generated workout after the terminal `completed` event arrives. Non-2xx HTTP responses and terminal stream `error` events both surface as generation failures in the app.
If the backend returns `200 OK` but the stream is empty, malformed, or closes before a terminal event, the Android client treats that as an invalid backend response instead of a successful generation.
Generation locale follows the active app or system locale. Russian locales resolve to `ru-RU`; all other locales currently resolve to `en-US` for MVP. TextToSpeech uses the same resolved locale so newly generated spoken cues stay aligned with the workout language.

## Coverage And Validation

Android coverage is generated from JVM unit tests and verified against the critical non-UI classes that contain reducer, repository, mapper, storage, startup, session, and tracking logic:

```bash
./scripts/android-coverage.sh
```

Backend coverage checks the critical packages at an 80% minimum and also writes `backend/coverage.out` for `go tool cover` consumers:

```bash
./scripts/backend-coverage.sh
```

Set `BACKEND_COVERAGE_THRESHOLD=<percent>` to override the default 80% minimum during local validation.

The GitHub Actions workflow runs the same repo-owned entrypoints:

- Android: `./scripts/android-ci.sh`
- Backend: `./scripts/backend-ci.sh`

## Backend Deployment Modes

Local binary:

```bash
cd backend
RUNNING_APP_PROVIDER=static go run ./cmd/server
```

Container image:

```bash
cd backend
docker build -t running-app-backend .
docker run --rm -p 8080:8080 \
  running-app-backend
```

The published container image defaults to the `static` provider because it does not bundle the Codex CLI binary. To run the `codex` provider in a container, bake the CLI into a custom image and override `RUNNING_APP_PROVIDER=codex`.

Systemd service:

- Full VPS walkthrough: `backend/deploy/README.md`
- Use `backend/deploy/systemd/running-app-backend.service`
- Supply environment variables through `/etc/running-app/backend.env`
- Install the built binary as `/usr/local/bin/running-app-backend`
- Debian/Ubuntu one-click install script: `backend/deploy/systemd/install-debian.sh`
- Full install walkthrough: `backend/deploy/systemd/README.md`
- Recommended reverse proxy: `backend/deploy/nginx/running-app-backend.conf`
- Debian/Ubuntu Nginx installer: `backend/deploy/nginx/install-debian.sh`
- Nginx walkthrough: `backend/deploy/nginx/README.md`

## Contract References

- API contract: `docs/contracts/training-generation-api.md`
- Training schema: `docs/contracts/training-schema-v1.md`
