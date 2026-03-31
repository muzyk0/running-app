# Running App

Running App is a monorepo for a native Android client and a stateless Go backend that generates structured running workouts.

## Repository Layout

- `android-app/`: Gradle-based Android root with a pinned wrapper and bootstrap verification task
- `backend/`: Go module for the backend service and future provider integrations
- `docs/`: product notes, contracts, and implementation plans
- `scripts/`: shared developer automation used by `make`

## Toolchains

- Gradle wrapper: `9.3.1`
- Android build JDK target: `17`
- Go toolchain: `go1.26.1`

The Android root is intentionally a bootstrap-only Gradle project in this task so `./gradlew help` can pass before the actual app module and Android SDK requirements land in the next iteration.

## Common Commands

- `make format`: format tracked Go sources
- `make lint`: syntax-check shell scripts, enforce Go formatting, run `go vet`, and validate the Gradle bootstrap
- `make test`: run bootstrap smoke checks for the Android and backend roots
- `make android-smoke`: run `cd android-app && ./gradlew help`
- `make backend-smoke`: run `cd backend && go list ./... && go test ./...`
- `make docker-smoke`: verify Docker is available and that the backend module resolves inside a container

## Local Bootstrap

1. Run `make test`
2. Run `cd android-app && ./gradlew help`
3. Run `cd backend && go test ./...`
