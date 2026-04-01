# Running App Notes

## Repo Layout

- `android-app/`: single Android application module
- `backend/`: Go HTTP service with `cmd/server` and `internal/*`
- `docs/contracts/`: shared API and schema contracts
- `scripts/`: repo-owned format, lint, test, coverage, build, and deploy helpers

## Android Architecture

- Kotlin + Compose app with Hilt for DI
- Room stores profile, workouts, and activity history
- DataStore keeps lightweight app flags
- Transport DTOs stay separate from Room entities and domain models

## Backend Architecture

- Entry point lives in `backend/cmd/server`
- Runtime code stays under `backend/internal/{api,config,prompt,provider,schema,service}`
- The backend is stateless and only validates, streams progress, and returns one normalized workout envelope
- Local env files auto-load from `backend/.env` when launched from the repo root, or `.env` when launched from `backend/`

## Commands

- `make lint`: shell syntax checks, Go format verification, `go vet`, and Android Gradle smoke validation
- `make test`: repo bootstrap and smoke validation
- `make ci`: Android assemble/tests/lint/coverage plus backend coverage, `go vet`, and Docker build
- `make android-build` or `make android-install`: build or install the debug APK
- `make backend-build` or `make backend-run`: build or run the backend locally

## Streaming Generation Debugging

- `POST /v1/trainings/generate` uses `text/event-stream` for successful generations
- A valid success path ends with the terminal `completed` event
- Non-2xx validation failures stay on the JSON response path and do not start streaming
- `RUNNING_APP_PROVIDER=static` gives a deterministic local SSE stream for repeatable Android and curl checks
