# Running App MVP Implementation Plan

## Overview

Implement a green field monorepo MVP with two deliverables: a native Android app in Kotlin and a stateless Go backend for AI workout generation. The app keeps profile, saved workouts, activity history, GPS routes, and execution state locally; the backend only generates, validates, and normalizes one workout per request through a provider abstraction that starts with `codex` CLI.

## Context

- Files involved: existing inputs in `docs/planning/ai-training-schema-draft.md`, `docs/planning/architecture-notes-draft.md`, `docs/planning/product-brief.md`, `docs/planning/post-mvp-roadmap.md`, `docs/ralphex.md`
- Files involved: new code roots `android-app/`, `backend/`, `docs/contracts/`, `.github/workflows/`
- Related patterns: no existing code patterns; use a simple monorepo split and keep contracts versioned
- Dependencies: Jetpack Compose, Navigation Compose, Room, Hilt, Retrofit/OkHttp, Fused Location Provider, Android `TextToSpeech`, Go `net/http`, Docker, `codex` CLI on the backend host
- Android architecture: one app module for MVP, package-by-feature (`core`, `profile`, `training`, `session`, `activity`, `ai`), explicit separation between transport DTOs, Room entities, and domain models
- Backend architecture: `cmd/server` plus `internal/{api,config,service,provider,prompt,schema}`, stateless HTTP service, provider interface ready for future OpenAI/Anthropic adapters
- Storage approach: Room as the main local store; DataStore only for lightweight flags such as disclaimer acceptance
- Runtime approach: one foreground active-session service reused by both saved-workout execution and free-run tracking

## Proposed Contract

- Endpoints: `GET /healthz`, `POST /v1/trainings/generate`
- Request shape:
- `profile`: `height_cm`, `weight_kg`, `sex`, `age`, `training_days_per_week`, `fitness_level`, `injuries_and_limitations`, `training_goal`, `additional_prompt_fields[]`
- `additional_prompt_fields[]`: array of `{ "label": "...", "value": "..." }`
- `request`: optional `user_note`, required `locale` with `ru-RU` for MVP
- Response shape: backend returns only the normalized training envelope; Android assigns its own local workout ID when saving

```json
{
  "schema_version": "mvp.v1",
  "training": {
    "title": "Интервальная тренировка",
    "summary": "Чередование легкого бега и ходьбы",
    "goal": "Адаптация к беговой нагрузке",
    "estimated_duration_sec": 1680,
    "disclaimer": "Приложение не является медицинской рекомендацией.",
    "steps": [
      {
        "id": "step-1",
        "type": "warmup",
        "duration_sec": 300,
        "voice_prompt": "Пять минут разминки быстрым шагом."
      }
    ]
  }
}
```

- Allowed step types for MVP: `warmup`, `run`, `walk`, `cooldown`, `rest`
- Normalization rules:
- `steps` must be non-empty
- every `duration_sec` must be positive
- step `id` values must be unique within one workout
- unknown step types must be mapped to the canonical list or rejected
- `estimated_duration_sec` must be recomputed by the backend from steps if needed
- `voice_prompt` is required because the client uses it for spoken cues

## Development Approach

- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- Keep Android as the source of truth for user data, workouts, and activity history
- Keep the backend stateless in MVP; do not add backend persistence unless a task proves it is necessary
- Use explicit mapper layers at the Android network/storage boundaries and in backend schema normalization
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Bootstrap Monorepo And Toolchains

**Files:**
- Create: `README.md`
- Modify: `.gitignore`
- Create: `Makefile`
- Create: `android-app/`
- Create: `backend/`

- [x] Create the monorepo skeleton with separate Android and backend roots plus shared developer commands
- [x] Initialize Gradle and Go toolchains with pinned runtime versions and reproducible local setup
- [x] Add root commands for `format`, `lint`, `test`, and backend docker smoke checks
- [x] Add bootstrap smoke scripts that validate Gradle sync and Go module resolution
- [x] Write tests or smoke checks for bootstrap scripts and toolchain entrypoints
- [x] Run `cd android-app && ./gradlew help`
- [x] Run `cd backend && go test ./...`

### Task 2: Android App Shell, Navigation, DI, And Permissions

**Files:**
- Create: `android-app/app/src/main/AndroidManifest.xml`
- Create: `android-app/app/src/main/java/<package>/MainActivity.kt`
- Create: `android-app/app/src/main/java/<package>/core/navigation/`
- Create: `android-app/app/src/main/java/<package>/core/di/`
- Create: `android-app/app/src/main/java/<package>/core/ui/`
- Create: `android-app/app/src/main/res/values-ru/strings.xml`

- [x] Set up a single-module Compose app with Russian-first resources, theme, and root navigation graph
- [x] Wire Hilt, app-level dependency graph, and app startup state
- [x] Add permission handling for location, notifications, and foreground tracking requirements
- [x] Define the top-level destinations for profile, workouts, generation, active session, free run, and history
- [x] Add JVM tests for navigation state, permission state reducers, and bootstrap wiring
- [x] Run `cd android-app && ./gradlew app:testDebugUnitTest`

### Task 3: Profile, Disclaimer, And Local User State

**Files:**
- Create: `android-app/app/src/main/java/<package>/profile/`
- Create: `android-app/app/src/main/java/<package>/core/storage/AppDatabase.kt`
- Create: `android-app/app/src/main/java/<package>/core/storage/ProfileDao.kt`
- Create: `android-app/app/src/main/java/<package>/core/datastore/`
- Create: `android-app/app/src/test/java/<package>/profile/`

- [x] Implement local profile storage with all mandatory fields plus free-form `additional_prompt_fields`
- [x] Store the profile in Room and keep disclaimer acceptance in DataStore as a lightweight app flag
- [x] Build first-run onboarding and profile edit screens with required-field validation
- [x] Surface the MVP medical disclaimer in a dedicated UX flow without adding hard safety blocking
- [x] Add tests for profile validation, Room converters, repository behavior, and disclaimer state
- [x] Run `cd android-app && ./gradlew app:testDebugUnitTest`

### Task 4: Workout Domain Model, Room Storage, And CRUD UI

**Files:**
- Create: `android-app/app/src/main/java/<package>/training/`
- Create: `android-app/app/src/main/java/<package>/training/data/local/`
- Create: `android-app/app/src/main/java/<package>/training/domain/`
- Create: `android-app/app/src/main/java/<package>/training/ui/`
- Create: `android-app/app/src/test/java/<package>/training/`

- [x] Define transport, domain, and storage models for the workout envelope with persisted `schemaVersion`
- [x] Create Room entities and DAOs for workouts and workout steps
- [x] Build workout list, detail, edit, delete, and duplicate-safe save flows
- [x] Keep the step model limited to `type`, `duration`, and `voice_prompt` in MVP
- [x] Recompute total workout duration from steps inside the domain layer and editor state
- [x] Add tests for mappers, DAOs, editor reducers, and duration calculations
- [x] Run `cd android-app && ./gradlew app:testDebugUnitTest`

### Task 5: Saved Workout Execution Engine, Timer, And Audio Cues

**Files:**
- Create: `android-app/app/src/main/java/<package>/session/`
- Create: `android-app/app/src/main/java/<package>/session/audio/`
- Create: `android-app/app/src/main/java/<package>/session/ui/`
- Create: `android-app/app/src/test/java/<package>/session/`

- [x] Implement a second-based workout execution engine that progresses through saved workout steps
- [x] Add active-workout UI with current step, remaining step time, total elapsed time, pause, resume, and stop
- [x] Implement audio cues using `TextToSpeech` for `voice_prompt` with a short-tone fallback for failures
- [x] Keep execution logic independent from GPS so it can be reused by the tracking layer
- [x] Add tests for timer math, step transitions, pause/resume behavior, and audio trigger rules
- [x] Run `cd android-app && ./gradlew app:testDebugUnitTest`

### Task 6: GPS Tracking, Foreground Session Service, Free Run, And History

**Files:**
- Create: `android-app/app/src/main/java/<package>/activity/`
- Create: `android-app/app/src/main/java/<package>/activity/data/local/`
- Create: `android-app/app/src/main/java/<package>/activity/service/`
- Create: `android-app/app/src/main/java/<package>/activity/ui/`
- Create: `android-app/app/src/test/java/<package>/activity/`

- [x] Implement one foreground `ActiveSessionService` with a persistent notification for both free runs and planned workouts
- [x] Integrate Fused Location Provider and store accepted route points, distance, duration, and average pace locally
- [x] Create Room entities for activity sessions and GPS points, including links to a workout when the session was planned
- [x] Build free-run start/stop flow and history screens for completed activities
- [x] Reuse the same session persistence model for planned and free activities to avoid duplicate tracking logic
- [x] Add tests for point filtering, distance calculation, pace calculation, and session persistence
- [x] Run `cd android-app && ./gradlew app:testDebugUnitTest`

### Task 7: Backend HTTP Service, Config, Packaging, And Contract Docs

**Files:**
- Create: `backend/cmd/server/main.go`
- Create: `backend/internal/api/`
- Create: `backend/internal/config/`
- Create: `backend/internal/service/`
- Create: `backend/internal/provider/provider.go`
- Create: `backend/Dockerfile`
- Create: `backend/deploy/systemd/running-app-backend.service`
- Create: `docs/contracts/training-generation-api.md`

- [x] Scaffold the Go service with `cmd/server` and compact `internal` packages
- [x] Implement `GET /healthz` and `POST /v1/trainings/generate` request routing with JSON input/output
- [x] Add config loading from environment, request timeouts, structured logs, and graceful shutdown
- [x] Support both local binary execution and containerized execution from the start
- [x] Write the authoritative client/backend contract doc with request and response examples
- [x] Add handler and config tests
- [x] Run `cd backend && go test ./...`

### Task 8: Codex CLI Provider, Prompt Assembly, And Schema Normalization

**Files:**
- Create: `backend/internal/provider/codexcli/`
- Create: `backend/internal/prompt/`
- Create: `backend/internal/schema/`
- Create: `docs/contracts/training-schema-v1.md`
- Create: `backend/testdata/`

- [x] Implement the provider interface and the first concrete adapter that invokes `codex` CLI with timeout and cancellation
- [x] Build prompt assembly from profile snapshot, optional user note, disclaimer guidance, and Russian output requirements
- [x] Normalize raw provider output into the canonical `mvp.v1` workout envelope
- [x] Validate required fields and reject or repair malformed outputs when it is safe to do so
- [x] Keep the provider boundary generic so API-key providers can be added later without changing the mobile contract
- [x] Add fixture-based tests for happy path, malformed JSON, missing fields, duplicate step IDs, and provider failures
- [x] Run `cd backend && go test ./...`

### Task 9: Android Backend Integration And AI Workout Generation Flow

**Files:**
- Create: `android-app/app/src/main/java/<package>/ai/`
- Create: `android-app/app/src/main/java/<package>/ai/data/remote/`
- Create: `android-app/app/src/main/java/<package>/ai/domain/`
- Create: `android-app/app/src/main/java/<package>/ai/ui/`
- Create: `android-app/app/src/test/java/<package>/ai/`

- [ ] Add Retrofit/OkHttp client, DTOs, repository, and use case for `POST /v1/trainings/generate`
- [ ] Create the generation UI that sends a profile snapshot, handles loading and error states, and previews the generated workout
- [ ] Save accepted generated workouts into Room and route them into the existing detail, edit, delete, and start flows
- [ ] Keep backend DTOs isolated from Room and domain models through explicit mappers
- [ ] Add tests for API client parsing, mapper behavior, repository error handling, and save flow integration
- [ ] Run `cd android-app && ./gradlew app:testDebugUnitTest`

### Task 10: Automated Checks, Smoke Validation, Coverage, And Docs

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify: `Makefile`
- Modify: `README.md`
- Modify: `docs/contracts/training-generation-api.md`
- Modify: `docs/contracts/training-schema-v1.md`

- [ ] Add CI jobs that run Android unit tests and lint plus backend tests, vet, and docker build
- [ ] Add coverage reporting for backend logic and Android non-UI modules
- [ ] Run `cd android-app && ./gradlew app:assembleDebug app:testDebugUnitTest app:lintDebug`
- [ ] Run `cd backend && go test ./... && go vet ./... && docker build -t running-app-backend .`
- [ ] Verify critical backend packages and Android non-UI modules reach 80%+ unit coverage
- [ ] Update `README.md` with local Android and backend run instructions, required env vars, and deployment modes
- [ ] Move this plan to `docs/plans/completed/` after implementation finishes
