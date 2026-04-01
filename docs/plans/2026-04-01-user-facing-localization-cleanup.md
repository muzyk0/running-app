---
# User-Facing Localization Cleanup For Android UI, Generation, And Voice Prompts

## Overview

Replace the current developer-oriented and duplicate Russian copy with real user-facing localization in the Android app, make runtime validation/errors/formatting locale-aware, and extend workout generation plus spoken cues so new English sessions work end to end instead of staying hardcoded to ru-RU.

## Context

- Files involved: `android-app/app/src/main/res/values/strings.xml`, `android-app/app/src/main/res/values-ru/strings.xml`
- Files involved: `android-app/app/src/main/java/com/vladislav/runningapp/profile/`, `android-app/app/src/main/java/com/vladislav/runningapp/training/ui/`, `android-app/app/src/main/java/com/vladislav/runningapp/ai/ui/`, `android-app/app/src/main/java/com/vladislav/runningapp/activity/`, `android-app/app/src/main/java/com/vladislav/runningapp/core/permissions/`
- Files involved: `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/`, `android-app/app/src/main/java/com/vladislav/runningapp/session/audio/`
- Files involved: `backend/internal/api/handler.go`, `backend/internal/prompt/prompt.go`, `backend/internal/schema/normalize.go`, `backend/internal/provider/provider.go`
- Files involved: `android-app/app/src/test/java/com/vladislav/runningapp/`, `backend/internal/*_test.go`, `backend/testdata/provider-output/`
- Files involved: `docs/contracts/training-generation-api.md`, `docs/contracts/training-schema-v1.md`, `README.md`, `CLAUDE.md`
- Related patterns: Compose screens already use `stringResource(...)` for many labels, so new UI copy should keep flowing through Android resources instead of inline Kotlin strings
- Related patterns: Backend locale validation is centralized in `backend/internal/api/handler.go`
- Related patterns: Backend language instructions and fallback text are centralized in `backend/internal/prompt/prompt.go` and `backend/internal/schema/normalize.go`
- Related patterns: Android already has JVM unit tests, Robolectric DAO tests, MockWebServer API tests, and Go package tests that can be extended without adding new tooling
- Dependencies: Existing Android resource system, system/app locale, TextToSpeech, SSE generation API contract, and current Gradle/Go test pipelines
- Dependencies: No new third-party libraries should be required
- Scope note: Localize static UI copy, runtime validation/error copy, locale-dependent formatters, newly generated workout text, and spoken prompts
- Scope note: Keep locale support limited to `ru-RU` and `en-US` for MVP
- Scope note: Do not add a separate in-app language settings flow; use the current Android app/system locale
- Scope note: Do not retro-translate already saved workouts in local storage

## Development Approach

- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- Keep base `values/strings.xml` as real English and `values-ru/strings.xml` as Russian with matching resource keys
- Prefer message identifiers and resource-backed formatting over hardcoded user-facing strings inside reducers, validators, repositories, and view models
- Keep backend locale handling explicit and small: accept only `ru-RU` and `en-US`, reject everything else clearly
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Rebuild The Android Resource Catalog With User-Facing RU/EN Copy

**Files:**
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-ru/strings.xml`
- Create or modify as needed: `android-app/app/src/main/res/values*/`
- Create: `android-app/app/src/test/java/com/vladislav/runningapp/core/i18n/LocalizationResourcesTest.kt`

- [x] Rewrite the existing resource text so English becomes the default/base locale and Russian becomes the localized override, with copy adapted for end users rather than developers
- [x] Add missing resource entries for validation errors, action failures, runtime statuses, locale labels, and formatter suffixes that are currently hardcoded in Kotlin
- [x] Keep `values` and `values-ru` key sets aligned so the app cannot silently fall back to the wrong language
- [x] Add a resource consistency test that compares locale key sets and guards against accidental Russian-only defaults returning to `values/strings.xml`
- [x] Run `cd android-app && ./gradlew app:testDebugUnitTest` - must pass before task 2

### Task 2: Remove Hardcoded Android Copy From Validators, ViewModels, And Formatters

**Files:**
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/profile/ProfileForm.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/profile/ProfileViewModel.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/training/ui/WorkoutEditorState.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/training/ui/TrainingViewModel.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/ui/GenerationViewModel.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/core/permissions/TrackingPermissionChecker.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/activity/ActivityTracker.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/activity/ActivityFormatters.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/ui/GenerationScreen.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/training/ui/TrainingScreen.kt`
- Create as needed: `android-app/app/src/main/java/com/vladislav/runningapp/core/i18n/`
- Modify or create tests under: `android-app/app/src/test/java/com/vladislav/runningapp/profile/`, `android-app/app/src/test/java/com/vladislav/runningapp/training/ui/`, `android-app/app/src/test/java/com/vladislav/runningapp/ai/ui/`, `android-app/app/src/test/java/com/vladislav/runningapp/activity/`

- [x] Replace inline Russian validation and failure strings with locale-independent message state that can be resolved through Android resources in the UI layer
- [x] Extract shared locale-aware duration, distance, and pace formatting instead of embedding `мин`, `с`, `км`, `м`, and similar Russian text directly in Kotlin
- [x] Update screens and view models so user-facing failures no longer expose raw developer/backend wording where a friendlier message should be shown
- [x] Add or update unit tests for validation states, formatter output, and view-model error mapping after the hardcoded strings are removed
- [x] Run `cd android-app && ./gradlew app:testDebugUnitTest` - must pass before task 3

### Task 3: Make Android Generation Requests And TTS Follow The Active Locale

**Files:**
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/TrainingGenerationMappers.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/RemoteTrainingGenerationRepository.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/ui/GenerationScreen.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/session/audio/AndroidSessionAudioAdapters.kt`
- Modify as needed: `android-app/app/src/main/java/com/vladislav/runningapp/core/i18n/`
- Modify: `android-app/app/src/test/java/com/vladislav/runningapp/ai/data/remote/TrainingGenerationMappersTest.kt`
- Modify: `android-app/app/src/test/java/com/vladislav/runningapp/ai/data/remote/TrainingGenerationApiServiceTest.kt`
- Modify: `android-app/app/src/test/java/com/vladislav/runningapp/ai/data/remote/RemoteTrainingGenerationRepositoryTest.kt`
- Create as needed: `android-app/app/src/test/java/com/vladislav/runningapp/session/audio/`

- [x] Derive the generation request locale from the current Android app/system locale instead of always sending `ru-RU`
- [x] Replace fixed-locale UI copy on the generation screen with user-facing locale-aware text that matches supported behavior
- [x] Initialize TextToSpeech with the active locale so newly generated English and Russian voice prompts are spoken with the correct voice settings
- [x] Add or update tests for locale selection, request serialization, and any new locale resolver/helper used by generation or TTS
- [x] Run `cd android-app && ./gradlew app:testDebugUnitTest` - must pass before task 4

### Task 4: Extend The Backend Locale Contract And Fallback Copy To `en-US`

**Files:**
- Modify: `backend/internal/api/handler.go`
- Modify: `backend/internal/prompt/prompt.go`
- Modify: `backend/internal/schema/normalize.go`
- Modify: `backend/internal/provider/provider.go`
- Modify: `backend/internal/api/handler_test.go`
- Modify: `backend/internal/prompt/prompt_test.go`
- Modify: `backend/internal/schema/normalize_test.go`
- Modify: `backend/internal/service/service_test.go`
- Modify: `backend/internal/provider/provider_test.go`
- Modify test fixtures as needed: `backend/testdata/provider-output/`

- [x] Update request validation so the backend accepts `ru-RU` and `en-US`, preserves the requested locale, and still rejects unsupported locales explicitly
- [x] Make prompt instructions locale-aware so provider output is requested in the correct language for user-facing fields and `voice_prompt`
- [x] Localize backend-owned fallback text such as disclaimer and synthesized voice prompts so English requests no longer receive Russian defaults
- [x] Update static provider output and backend fixtures so local smoke tests and deterministic tests cover both supported locales
- [x] Add or update backend tests for accepted locales, unsupported-locale rejection, prompt content, normalized fallback text, and fixture coverage
- [x] Run `cd backend && go test ./...` - must pass before task 5

### Task 5: Verify Acceptance Criteria

**Files:**
- Modify if coverage includes need adjustment: `android-app/app/build.gradle.kts`
- Modify CI scripts only if validation gaps are discovered: `scripts/`

- [ ] Run full test suite with repo CI entrypoints: `make ci`
- [ ] Run linter: `make lint`
- [ ] Run coverage verification and confirm the project still meets the 80% threshold: `make coverage`

### Task 6: Update Documentation

**Files:**
- Modify: `docs/contracts/training-generation-api.md`
- Modify: `docs/contracts/training-schema-v1.md`
- Modify: `README.md`
- Modify if needed: `CLAUDE.md`
- Move: `docs/plans/2026-04-01-user-facing-localization-cleanup.md` to `docs/plans/completed/`

- [ ] Update the API contract to document bilingual locale support, locale-dependent generation behavior, and streamed examples for both supported languages
- [ ] Update the training schema docs to describe locale-aware fallback disclaimer and `voice_prompt` behavior instead of Russian-only assumptions
- [ ] Update `README.md` anywhere local validation or generation expectations still imply Russian-only behavior
- [ ] Update `CLAUDE.md` if internal localization patterns or development workflow expectations change during implementation
- [ ] Move this plan to `docs/plans/completed/`
---
