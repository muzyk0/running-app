# Training Generation API

This document is the authoritative MVP contract between the Android client and the Go backend.

## Base Principles

- Transport format: JSON over HTTP
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
- `Accept: application/json`

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
