# Training Generation API

This document is the authoritative MVP contract between the Android client and the Go backend.

## Base Principles

- Transport format: JSON request bodies over HTTP, `text/event-stream` for successful training generation responses, and JSON for non-streaming errors
- API versioning: path-based for endpoints and `schema_version` inside the training payload
- Supported locale for MVP generation requests: `ru-RU`
- Backend remains stateless: it generates and returns one normalized workout per request and does not persist user data

## Endpoints

### `GET /healthz`

Returns a simple health response for load balancers, smoke checks, and systemd/container readiness checks.

Example response:

```json
{
  "status": "ok"
}
```

### `POST /v1/trainings/generate`

Generates one workout from a profile snapshot and optional user note.

Request headers:

- `Content-Type: application/json`
- `Accept: text/event-stream`

Request body:

```json
{
  "profile": {
    "height_cm": 180,
    "weight_kg": 77,
    "sex": "male",
    "age": 31,
    "training_days_per_week": 4,
    "fitness_level": "beginner",
    "injuries_and_limitations": "none",
    "training_goal": "Build consistency",
    "additional_prompt_fields": [
      {
        "label": "Поверхность",
        "value": "Стадион или ровный асфальт"
      }
    ]
  },
  "request": {
    "locale": "ru-RU",
    "user_note": "Без интенсивных ускорений"
  }
}
```

Field requirements:

- `profile.height_cm`: positive integer
- `profile.weight_kg`: positive integer
- `profile.sex`: non-empty string
- `profile.age`: positive integer
- `profile.training_days_per_week`: positive integer
- `profile.fitness_level`: non-empty string
- `profile.injuries_and_limitations`: non-empty string
- `profile.training_goal`: non-empty string
- `profile.additional_prompt_fields[]`: optional array of `{label, value}` objects with non-empty strings
- `request.locale`: required and must be `ru-RU` for MVP
- `request.user_note`: optional string

Successful response:

Response headers:

- `Content-Type: text/event-stream; charset=utf-8`

The backend streams named server-sent events and closes the connection after the terminal event. `completed` and `error` are mutually exclusive terminal events.

Stream event payloads:

- `log`: realtime generator progress text
- `completed`: final normalized training payload
- `error`: terminal generation failure after streaming has started

Example stream:

```text
event: log
data: {"message":"Building training prompt"}

event: log
data: {"message":"Waiting for provider output"}

event: completed
data: {"schema_version":"mvp.v1","training":{"title":"Базовая интервальная тренировка","summary":"Чередование спокойного бега и ходьбы для безопасного старта тренировки.","goal":"Build consistency","estimated_duration_sec":1020,"disclaimer":"Приложение не является медицинской рекомендацией.","steps":[{"id":"step-1","type":"warmup","duration_sec":300,"voice_prompt":"Пять минут разминки быстрым шагом."},{"id":"step-2","type":"run","duration_sec":120,"voice_prompt":"Две минуты очень легкого бега."},{"id":"step-3","type":"walk","duration_sec":180,"voice_prompt":"Три минуты восстановления шагом."},{"id":"step-4","type":"run","duration_sec":120,"voice_prompt":"Еще две минуты легкого бега."},{"id":"step-5","type":"cooldown","duration_sec":300,"voice_prompt":"Пять минут заминки спокойным шагом."}]}}
```

The `completed` event payload reuses the existing JSON training envelope:

```json
{
  "schema_version": "mvp.v1",
  "training": {
    "title": "Базовая интервальная тренировка",
    "summary": "Чередование спокойного бега и ходьбы для безопасного старта тренировки.",
    "goal": "Build consistency",
    "estimated_duration_sec": 1020,
    "disclaimer": "Приложение не является медицинской рекомендацией.",
    "steps": [
      {
        "id": "step-1",
        "type": "warmup",
        "duration_sec": 300,
        "voice_prompt": "Пять минут разминки быстрым шагом."
      },
      {
        "id": "step-2",
        "type": "run",
        "duration_sec": 120,
        "voice_prompt": "Две минуты очень легкого бега."
      },
      {
        "id": "step-3",
        "type": "walk",
        "duration_sec": 180,
        "voice_prompt": "Три минуты восстановления шагом."
      },
      {
        "id": "step-4",
        "type": "run",
        "duration_sec": 120,
        "voice_prompt": "Еще две минуты легкого бега."
      },
      {
        "id": "step-5",
        "type": "cooldown",
        "duration_sec": 300,
        "voice_prompt": "Пять минут заминки спокойным шагом."
      }
    ]
  }
}
```

Android handling expectations:

- append each `log` event `message` to the realtime generation output while the request is still running
- keep the workout preview and save actions unavailable until a terminal `completed` event arrives
- treat the terminal `error` event as the same error envelope used by non-success HTTP responses and stop reading the stream after it
- treat a stream that closes without `completed` or `error` as an invalid backend response

Response guarantees for MVP:

- `schema_version` is always present
- `training.steps` is non-empty
- every step contains `id`, `type`, `duration_sec`, and `voice_prompt`
- `estimated_duration_sec` equals the sum of all step durations
- Android assigns its own local workout identifier when saving the response

Error response format:

```json
{
  "error": {
    "code": "invalid_request",
    "message": "profile.training_goal is required"
  }
}
```

Current error codes:

- `invalid_json`: malformed JSON or unexpected fields
- `invalid_request`: structurally valid JSON that fails field validation
- `service_unavailable`: backend service or provider wiring is unavailable
- `provider_error`: downstream generation failed
- `request_timeout`: generation exceeded the configured request timeout

Failure semantics:

- 4xx request validation failures happen before streaming starts and still return ordinary JSON error responses with the HTTP status codes documented above
- if the backend starts the SSE stream and generation later fails, the HTTP status remains `200 OK` and the terminal `error` event carries the JSON error envelope
- once the backend emits either `completed` or `error`, it closes the connection and sends no further events
- terminal stream failures are limited to generation-time backend issues such as `service_unavailable`, `provider_error`, and `request_timeout`; request-shape problems stay on the pre-stream JSON path
- Android should map non-success HTTP responses and terminal stream `error` events through the same error-code table so validation, provider, and timeout failures stay consistent in the UI
- terminal stream error example:

```text
event: error
data: {"error":{"code":"request_timeout","message":"generation timed out"}}
```

## Runtime Configuration

Backend runtime settings that affect this contract:

- `RUNNING_APP_HTTP_ADDR`: bind address, default `127.0.0.1:8080`
- `RUNNING_APP_PROVIDER`: `codex` or `static`
- `RUNNING_APP_REQUEST_TIMEOUT`: total request timeout, default `90s`
- `RUNNING_APP_WRITE_TIMEOUT`: response write timeout, default `2m`, must be greater than `RUNNING_APP_REQUEST_TIMEOUT`
- `RUNNING_APP_ENV_FILE`: optional explicit dotenv file path; otherwise the backend auto-loads `backend/.env` or `.env` from the working directory when present
- `RUNNING_APP_CODEX_BINARY`, `RUNNING_APP_CODEX_WORKDIR`, `RUNNING_APP_CODEX_MODEL`, `RUNNING_APP_CODEX_PROFILE`, `RUNNING_APP_CODEX_SANDBOX`: provider-specific Codex CLI settings

For Android builds, the backend base URL is passed through the Gradle property `runningAppTrainingApiBaseUrl`. The emulator-friendly `debug` default remains `http://10.0.2.2:8080/`. `release` builds must receive an HTTPS endpoint through `runningAppReleaseTrainingApiBaseUrl` in `android-app/local.properties`, `RUNNING_APP_RELEASE_TRAINING_API_BASE_URL` in the environment, or an explicit Gradle property override. Cleartext transport is allowed only in debug builds; release builds must use HTTPS.

## Deployment Modes

The same transport contract is used in all supported MVP deployment modes:

- local binary execution via `go run ./cmd/server`
- containerized execution from `backend/Dockerfile`
- Linux service deployment via `backend/deploy/systemd/running-app-backend.service`

The deployment mode changes process packaging and env injection only. The JSON request shape and the streamed success/error semantics above stay the same.
