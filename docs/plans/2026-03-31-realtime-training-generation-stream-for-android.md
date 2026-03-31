# Realtime Training Generation Stream for Android

## Overview
Convert training generation from a one-shot JSON response into a streamed request so the Android screen can show raw Codex progress text/logs in real time and reveal the generated workout only after the terminal completion event. Keep the request payload unchanged; change the successful transport for `POST /v1/trainings/generate` to `text/event-stream` with terminal `completed` and `error` events.

## Context
- Files involved: `backend/internal/api/handler.go`, `backend/internal/api/handler_test.go`, `backend/internal/service/service.go`, `backend/internal/service/service_test.go`, `backend/internal/provider/provider.go`, `backend/internal/provider/codexcli/provider.go`, `backend/internal/provider/codexcli/provider_test.go`
- Files involved: `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/TrainingGenerationApiService.kt`, `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/RemoteTrainingGenerationRepository.kt`, `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/TrainingGenerationDtos.kt`, `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/TrainingGenerationMappers.kt`, `android-app/app/src/main/java/com/vladislav/runningapp/ai/domain/TrainingGenerationRepository.kt`, `android-app/app/src/main/java/com/vladislav/runningapp/ai/domain/TrainingGenerationModels.kt`, `android-app/app/src/main/java/com/vladislav/runningapp/ai/domain/GenerateWorkoutUseCase.kt`
- Files involved: `android-app/app/src/main/java/com/vladislav/runningapp/ai/ui/GenerationViewModel.kt`, `android-app/app/src/main/java/com/vladislav/runningapp/ai/ui/GenerationScreen.kt`, `android-app/app/src/main/res/values/strings.xml`, `android-app/app/src/main/res/values-ru/strings.xml`, matching unit tests under `android-app/app/src/test/java/com/vladislav/runningapp/ai/...`
- Related patterns: the current app uses a single Retrofit POST and shows the workout only after a full response; the backend normalizes provider output after `--output-last-message`; `ralphex-repo/pkg/executor/codex.go` demonstrates the same shape we need here, with realtime progress on stderr and final output handled separately
- Dependencies: reuse existing Retrofit + OkHttp raw response streaming and Go `net/http` flushing; no new library is required if SSE is parsed directly from `ResponseBody`

## Development Approach
- **Testing approach**: Regular
- Complete each task fully before moving to the next
- Prefer the smallest end-to-end contract change: keep request JSON unchanged, switch only the successful response path to SSE, and send the final normalized workout in the terminal `completed` event
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Define the streaming contract and backend orchestration

**Files:**
- Modify: `docs/contracts/training-generation-api.md`
- Modify: `backend/internal/api/handler.go`
- Modify: `backend/internal/api/handler_test.go`
- Modify: `backend/internal/service/service.go`
- Modify: `backend/internal/service/service_test.go`
- Modify: `backend/internal/provider/provider.go`

- [x] Change the success contract of `POST /v1/trainings/generate` from one JSON body to an SSE stream with named events such as `log`, `completed`, and terminal `error`, while keeping request validation and request body shape unchanged
- [x] Add backend progress/event types plus a service path that can emit provider progress chunks and still return the same normalized `TrainingEnvelope` on completion
- [x] Keep 4xx validation failures as ordinary JSON errors before the stream starts; once streaming begins, convert timeout/provider failures into terminal SSE `error` events and close the stream
- [x] Write or update tests for request validation, successful event ordering, final `completed` payload, and mid-stream failure mapping
- [x] run `cd backend && go test ./...`

### Task 2: Stream realtime output from generators without losing the final JSON payload

**Files:**
- Modify: `backend/internal/provider/provider.go`
- Modify: `backend/internal/provider/codexcli/provider.go`
- Modify: `backend/internal/provider/codexcli/provider_test.go`

- [x] Extend the generator contract so providers can emit progress chunks during execution
- [x] Refactor the Codex CLI provider to read stderr line-by-line in real time, forward those lines into the stream, and still keep `--output-last-message` as the authoritative final JSON for normalization
- [x] Give the static provider a minimal deterministic progress sequence so the stream contract stays testable without Codex
- [x] Write or update provider tests for streamed stderr forwarding, empty-prompt and error paths, and final output preservation
- [x] run `cd backend && go test ./...`

### Task 3: Replace the Android one-shot result API with streamed generation updates

**Files:**
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/TrainingGenerationApiService.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/RemoteTrainingGenerationRepository.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/TrainingGenerationDtos.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/data/remote/TrainingGenerationMappers.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/domain/TrainingGenerationRepository.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/domain/TrainingGenerationModels.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/domain/GenerateWorkoutUseCase.kt`
- Modify: `android-app/app/src/test/java/com/vladislav/runningapp/ai/data/remote/RemoteTrainingGenerationRepositoryTest.kt`
- Modify: `android-app/app/src/test/java/com/vladislav/runningapp/ai/data/remote/TrainingGenerationApiServiceTest.kt`

- [ ] Change the repository and use-case contract from a single `TrainingGenerationResult` into a streamed sequence of generation updates so UI can observe raw log chunks and the terminal workout separately
- [ ] Update the Retrofit service to read the successful response as a raw streamed body, parse SSE events incrementally, and map `completed` back into the existing workout DTO and domain mapper
- [ ] Preserve current HTTP error-body mapping for non-success responses so validation, network, and provider errors still surface with consistent domain errors
- [ ] Write or update tests with `MockWebServer` chunked SSE bodies covering log chunks, terminal completion, and terminal error events
- [ ] run `cd android-app && ./gradlew --no-daemon app:testDebugUnitTest`

### Task 4: Surface raw generation output in the Android screen and only reveal the workout on completion

**Files:**
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/ui/GenerationViewModel.kt`
- Modify: `android-app/app/src/main/java/com/vladislav/runningapp/ai/ui/GenerationScreen.kt`
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-ru/strings.xml`
- Modify: `android-app/app/src/test/java/com/vladislav/runningapp/ai/ui/GenerationViewModelTest.kt`

- [ ] Add UI state for streamed raw output, terminal stream errors, and the distinction between “generation still running” and “final workout is ready”
- [ ] Clear previous log and output state when a new request starts, append streamed chunks as they arrive, and keep save actions disabled until the terminal `completed` event delivers a valid workout
- [ ] Update `GenerationScreen` to show a dedicated realtime output card while generation is active and keep the workout preview hidden until completion
- [ ] Write or update unit tests for log accumulation, completion transition, error transition, and save-button gating
- [ ] run `cd android-app && ./gradlew --no-daemon app:testDebugUnitTest`

### Task 5: Verify acceptance criteria

- [ ] run `./scripts/backend-coverage.sh`
- [ ] run `./scripts/android-coverage.sh`
- [ ] run `make lint`
- [ ] verify test coverage meets 80%+

### Task 6: Update documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/contracts/training-generation-api.md`
- Move: `docs/plans/2026-03-31-realtime-training-generation-stream-for-android.md` to `docs/plans/completed/`

- [ ] Update README sections that describe the training-generation transport so local testing reflects SSE streaming instead of a one-shot JSON success response
- [ ] Ensure the contract doc includes example stream events and Android/backend failure semantics
- [ ] move this plan to `docs/plans/completed/`
