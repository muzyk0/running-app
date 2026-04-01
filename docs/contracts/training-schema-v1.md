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
    "title": "Base interval workout",
    "summary": "Alternating easy running and walking for a safe start to the session.",
    "goal": "Gradually adapt to running load",
    "estimated_duration_sec": 1680,
    "disclaimer": "This app does not provide medical advice.",
    "steps": [
      {
        "id": "step-1",
        "type": "warmup",
        "duration_sec": 300,
        "voice_prompt": "Warm up with brisk walking for 5 minutes."
      }
    ]
  }
}
```

The same envelope shape is used for both supported locales. For `ru-RU`, the backend returns Russian user-facing text. For `en-US`, the backend returns English user-facing text.

## Training Fields

- `title`: required, non-empty string
- `summary`: optional string
- `goal`: optional string; if missing, backend falls back to the user profile training goal
- `estimated_duration_sec`: backend-controlled integer equal to the sum of all step durations
- `disclaimer`: backend-controlled string; defaults to `Приложение не является медицинской рекомендацией.` for `ru-RU` and `This app does not provide medical advice.` for `en-US`
- `steps`: required non-empty array

## Step Fields

- `id`: required in the normalized result; backend repairs missing or duplicate IDs with stable `step-N` values
- `type`: required; allowed canonical values are `warmup`, `run`, `walk`, `cooldown`, `rest`
- `duration_sec`: required positive integer
- `voice_prompt`: required in the normalized result; backend can synthesize a locale-matched fallback prompt when the provider omits it

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
- Fallback disclaimer text and synthesized `voice_prompt` text use the accepted request locale rather than a hardcoded default language

## Locale-Dependent Fallbacks

- Supported request locales for normalized output are `ru-RU` and `en-US`
- If `training.disclaimer` is missing, the backend injects the locale-matched default disclaimer
- If a step `voice_prompt` is missing or blank, the backend generates a locale-matched prompt based on step type and duration
- The backend does not auto-translate provider-authored text across locales; it only repairs missing backend-owned text in the requested locale

Example fallback behavior:

- `ru-RU` disclaimer fallback: `Приложение не является медицинской рекомендацией.`
- `en-US` disclaimer fallback: `This app does not provide medical advice.`
- `ru-RU` warmup fallback prompt example: `Пять минут разминки быстрым шагом.`
- `en-US` warmup fallback prompt example: `Warm up with brisk walking for 5 minutes.`

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
- produce user-facing fields in the requested locale (`ru-RU` or `en-US`)
- include the locale-matched MVP disclaimer verbatim
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
