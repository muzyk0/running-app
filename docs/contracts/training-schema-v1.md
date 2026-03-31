# Training Schema v1

This document defines the normalized workout envelope returned by the backend after provider output has been validated and repaired where safe.

## Schema Version

- Canonical version for MVP: `mvp.v1`
- The backend always returns `schema_version: "mvp.v1"` even if the provider emits an older or missing version marker

## Envelope

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

## Training Fields

- `title`: required, non-empty string
- `summary`: optional string
- `goal`: optional string; if missing, backend falls back to the user profile training goal
- `estimated_duration_sec`: backend-controlled integer equal to the sum of all step durations
- `disclaimer`: backend-controlled string; defaults to `Приложение не является медицинской рекомендацией.`
- `steps`: required non-empty array

## Step Fields

- `id`: required in the normalized result; backend repairs missing or duplicate IDs with stable `step-N` values
- `type`: required; allowed canonical values are `warmup`, `run`, `walk`, `cooldown`, `rest`
- `duration_sec`: required positive integer
- `voice_prompt`: required in the normalized result; backend can synthesize a fallback prompt when the provider omits it

## Normalization Rules

- Provider output may be either the full envelope or a bare `training` object
- Markdown code fences are stripped before JSON decoding if present
- `schema_version` is rewritten to `mvp.v1`
- `estimated_duration_sec` is recomputed from normalized steps
- Known step-type aliases are mapped to canonical values:
  - `warmup_walk` -> `warmup`
  - `cooldown_walk` -> `cooldown`
  - lightweight synonyms like `jog` -> `run`, `recovery_walk` -> `walk`
- Missing `goal`, `disclaimer`, `voice_prompt`, and duplicate or blank step IDs are repaired when safe

## Rejection Rules

The backend rejects provider output when any of these conditions remain after normalization:

- invalid JSON
- missing `training` object and no bare training object fields
- missing or empty `training.title`
- missing or empty `steps`
- unsupported step type that cannot be mapped safely
- non-positive or non-integer `duration_sec`

## Provider Prompt Expectations

The backend prompt instructs providers to:

- return only JSON
- produce Russian text for user-facing fields
- include the MVP disclaimer verbatim
- emit one workout only, not a weekly plan
- keep every step within the canonical MVP shape

## Client Persistence Notes

- Android stores its own local workout identifier and does not treat backend `step.id` values as database primary keys
- Android persists `schema_version` alongside saved workouts so normalized envelopes remain traceable after local edits
- `estimated_duration_sec` should be treated as backend-authored but still safe for local recomputation when the user edits a saved workout

## Verification Notes

CI-backed unit coverage verifies the normalization rules exercised by the backend implementation, including:

- bare `training` objects and full envelopes
- markdown-wrapped JSON extraction
- canonical step-type alias mapping
- duplicate or missing step ID repair
- fallback disclaimer, goal, and `voice_prompt` behavior
- rejection of malformed JSON and unsupported step types
