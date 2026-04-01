package api

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/muzyk0/running-app/backend/internal/provider"
	"github.com/muzyk0/running-app/backend/internal/service"
)

func TestHealthz(t *testing.T) {
	router := NewRouter(testLogger(), &stubTrainingService{}, 2*time.Second)

	request := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}

	var payload healthResponse
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}

	if payload.Status != "ok" {
		t.Fatalf("status payload = %q, want %q", payload.Status, "ok")
	}
}

func TestNewRouterUsesDefaultLoggerWhenNil(t *testing.T) {
	router := NewRouter(nil, &stubTrainingService{}, 2*time.Second)

	request := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
}

func TestGenerateTrainingSuccessStreamsEvents(t *testing.T) {
	stub := &stubTrainingService{
		progress: []provider.ProgressChunk{
			{Message: "building training prompt"},
			{Message: "normalizing final workout"},
		},
		response: provider.TrainingEnvelope{
			SchemaVersion: "mvp.v1",
			Training: provider.Training{
				Title:                "Тестовая тренировка",
				EstimatedDurationSec: 600,
				Steps: []provider.TrainingStep{
					{ID: "step-1", Type: "warmup", DurationSec: 300, VoicePrompt: "Разминка."},
					{ID: "step-2", Type: "run", DurationSec: 300, VoicePrompt: "Бег."},
				},
			},
		},
	}

	router := NewRouter(testLogger(), stub, 1500*time.Millisecond)

	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(validGenerateRequestJSON))
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	if got := response.Header().Get("Content-Type"); got != "text/event-stream; charset=utf-8" {
		t.Fatalf("Content-Type = %q, want %q", got, "text/event-stream; charset=utf-8")
	}
	if !stub.sawDeadline {
		t.Fatal("service context did not include a deadline")
	}
	if stub.request.Profile.HeightCM != 180 {
		t.Fatalf("profile.height_cm = %d, want %d", stub.request.Profile.HeightCM, 180)
	}
	if stub.request.Locale != "ru-RU" {
		t.Fatalf("locale = %q, want %q", stub.request.Locale, "ru-RU")
	}

	events := parseSSEEvents(t, response.Body.String())
	if len(events) != 3 {
		t.Fatalf("len(events) = %d, want %d", len(events), 3)
	}
	if events[0].Name != string(provider.StreamEventLog) {
		t.Fatalf("events[0].Name = %q, want %q", events[0].Name, provider.StreamEventLog)
	}
	if events[1].Name != string(provider.StreamEventLog) {
		t.Fatalf("events[1].Name = %q, want %q", events[1].Name, provider.StreamEventLog)
	}
	if events[2].Name != string(provider.StreamEventCompleted) {
		t.Fatalf("events[2].Name = %q, want %q", events[2].Name, provider.StreamEventCompleted)
	}

	var firstLog provider.ProgressChunk
	if err := json.Unmarshal([]byte(events[0].Data), &firstLog); err != nil {
		t.Fatalf("decode first log payload: %v", err)
	}
	if firstLog.Message != "building training prompt" {
		t.Fatalf("first log message = %q, want %q", firstLog.Message, "building training prompt")
	}

	var completed provider.TrainingEnvelope
	if err := json.Unmarshal([]byte(events[2].Data), &completed); err != nil {
		t.Fatalf("decode completed payload: %v", err)
	}
	if completed.Training.Title != "Тестовая тренировка" {
		t.Fatalf("completed training title = %q, want %q", completed.Training.Title, "Тестовая тренировка")
	}
}

func TestGenerateTrainingRejectsInvalidLocale(t *testing.T) {
	router := NewRouter(testLogger(), &stubTrainingService{}, 2*time.Second)
	body := strings.Replace(validGenerateRequestJSON, `"locale": "ru-RU"`, `"locale": "en-US"`, 1)

	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(body))
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
	}
	if got := response.Header().Get("Content-Type"); got != "application/json; charset=utf-8" {
		t.Fatalf("Content-Type = %q, want %q", got, "application/json; charset=utf-8")
	}
}

func TestGenerateTrainingRejectsInvalidJSONBeforeStreamStarts(t *testing.T) {
	testCases := []struct {
		name    string
		body    string
		wantMsg string
	}{
		{
			name:    "unknown field",
			body:    `{"extra":true}`,
			wantMsg: "unknown field",
		},
		{
			name:    "multiple objects",
			body:    `{} {}`,
			wantMsg: "exactly one JSON object",
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			router := NewRouter(testLogger(), &stubTrainingService{}, 2*time.Second)
			request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(testCase.body))
			response := httptest.NewRecorder()

			router.ServeHTTP(response, request)

			if response.Code != http.StatusBadRequest {
				t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
			}
			if got := response.Header().Get("Content-Type"); got != "application/json; charset=utf-8" {
				t.Fatalf("Content-Type = %q, want %q", got, "application/json; charset=utf-8")
			}

			var payload errorEnvelope
			if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
				t.Fatalf("decode response: %v", err)
			}
			if payload.Error.Code != "invalid_json" {
				t.Fatalf("error.code = %q, want %q", payload.Error.Code, "invalid_json")
			}
			if !strings.Contains(payload.Error.Message, testCase.wantMsg) {
				t.Fatalf("error.message = %q, want substring %q", payload.Error.Message, testCase.wantMsg)
			}
		})
	}
}

func TestGenerateTrainingProviderErrorStreamsTerminalErrorEvent(t *testing.T) {
	router := NewRouter(testLogger(), &stubTrainingService{
		progress: []provider.ProgressChunk{{Message: "generator started"}},
		err:      errors.New("boom"),
	}, 2*time.Second)

	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(validGenerateRequestJSON))
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}

	events := parseSSEEvents(t, response.Body.String())
	if len(events) != 2 {
		t.Fatalf("len(events) = %d, want %d", len(events), 2)
	}
	if events[0].Name != string(provider.StreamEventLog) {
		t.Fatalf("events[0].Name = %q, want %q", events[0].Name, provider.StreamEventLog)
	}
	if events[1].Name != string(provider.StreamEventError) {
		t.Fatalf("events[1].Name = %q, want %q", events[1].Name, provider.StreamEventError)
	}

	var payload errorEnvelope
	if err := json.Unmarshal([]byte(events[1].Data), &payload); err != nil {
		t.Fatalf("decode error payload: %v", err)
	}
	if payload.Error.Code != "provider_error" {
		t.Fatalf("error.code = %q, want %q", payload.Error.Code, "provider_error")
	}
}

func TestGenerateTrainingReturnsServiceUnavailableWithoutService(t *testing.T) {
	router := NewRouter(testLogger(), nil, 2*time.Second)

	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(validGenerateRequestJSON))
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusServiceUnavailable)
	}

	var payload errorEnvelope
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Error.Code != "service_unavailable" {
		t.Fatalf("error.code = %q, want %q", payload.Error.Code, "service_unavailable")
	}
}

func TestGenerateTrainingReturnsTimeoutError(t *testing.T) {
	router := NewRouter(testLogger(), &stubTrainingService{
		progress: []provider.ProgressChunk{{Message: "generator started"}},
		err:      context.DeadlineExceeded,
	}, 25*time.Millisecond)

	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(validGenerateRequestJSON))
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}

	events := parseSSEEvents(t, response.Body.String())
	if len(events) != 2 {
		t.Fatalf("len(events) = %d, want %d", len(events), 2)
	}
	if events[1].Name != string(provider.StreamEventError) {
		t.Fatalf("events[1].Name = %q, want %q", events[1].Name, provider.StreamEventError)
	}

	var payload errorEnvelope
	if err := json.Unmarshal([]byte(events[1].Data), &payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Error.Code != "request_timeout" {
		t.Fatalf("error.code = %q, want %q", payload.Error.Code, "request_timeout")
	}
}

func TestGenerateTrainingReturnsProviderNotConfiguredError(t *testing.T) {
	router := NewRouter(testLogger(), &stubTrainingService{
		progress: []provider.ProgressChunk{{Message: "generator started"}},
		err:      service.ErrProviderNotConfigured,
	}, 2*time.Second)

	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(validGenerateRequestJSON))
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}

	events := parseSSEEvents(t, response.Body.String())
	if len(events) != 2 {
		t.Fatalf("len(events) = %d, want %d", len(events), 2)
	}
	if events[1].Name != string(provider.StreamEventError) {
		t.Fatalf("events[1].Name = %q, want %q", events[1].Name, provider.StreamEventError)
	}

	var payload errorEnvelope
	if err := json.Unmarshal([]byte(events[1].Data), &payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Error.Code != "service_unavailable" {
		t.Fatalf("error.code = %q, want %q", payload.Error.Code, "service_unavailable")
	}
	if payload.Error.Message != "training provider is not configured" {
		t.Fatalf("error.message = %q, want %q", payload.Error.Message, "training provider is not configured")
	}
}

func TestGenerateTrainingRejectsBlankAdditionalPromptFieldValue(t *testing.T) {
	router := NewRouter(testLogger(), &stubTrainingService{}, 2*time.Second)
	body := strings.Replace(validGenerateRequestJSON, `"Интервалы"`, `"   "`, 1)

	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(body))
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
	}
}

func TestGenerateTrainingRequestToProviderRequestValidation(t *testing.T) {
	testCases := []struct {
		name      string
		mutate    func(*generateTrainingRequest)
		wantError string
	}{
		{
			name: "height must be positive",
			mutate: func(request *generateTrainingRequest) {
				request.Profile.HeightCM = 0
			},
			wantError: "profile.height_cm must be greater than zero",
		},
		{
			name: "weight must be positive",
			mutate: func(request *generateTrainingRequest) {
				request.Profile.WeightKG = 0
			},
			wantError: "profile.weight_kg must be greater than zero",
		},
		{
			name: "sex is required",
			mutate: func(request *generateTrainingRequest) {
				request.Profile.Sex = "   "
			},
			wantError: "profile.sex is required",
		},
		{
			name: "age must be positive",
			mutate: func(request *generateTrainingRequest) {
				request.Profile.Age = 0
			},
			wantError: "profile.age must be greater than zero",
		},
		{
			name: "training days must be positive",
			mutate: func(request *generateTrainingRequest) {
				request.Profile.TrainingDaysPerWeek = 0
			},
			wantError: "profile.training_days_per_week must be greater than zero",
		},
		{
			name: "fitness level is required",
			mutate: func(request *generateTrainingRequest) {
				request.Profile.FitnessLevel = "   "
			},
			wantError: "profile.fitness_level is required",
		},
		{
			name: "injuries are required",
			mutate: func(request *generateTrainingRequest) {
				request.Profile.InjuriesAndLimitations = "   "
			},
			wantError: "profile.injuries_and_limitations is required",
		},
		{
			name: "training goal is required",
			mutate: func(request *generateTrainingRequest) {
				request.Profile.TrainingGoal = "   "
			},
			wantError: "profile.training_goal is required",
		},
		{
			name: "additional prompt fields require both label and value",
			mutate: func(request *generateTrainingRequest) {
				request.Profile.AdditionalPromptFields = []promptFieldRequest{{
					Label: "   ",
					Value: "value",
				}}
			},
			wantError: "profile.additional_prompt_fields entries must include non-empty label and value",
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			request := validGenerateTrainingRequest(t)
			testCase.mutate(&request)

			_, err := request.toProviderRequest()
			if err == nil {
				t.Fatal("toProviderRequest() error = nil, want error")
			}
			if err.Error() != testCase.wantError {
				t.Fatalf("toProviderRequest() error = %q, want %q", err.Error(), testCase.wantError)
			}
		})
	}
}

func TestGenerateTrainingRequestToProviderRequestTrimsFields(t *testing.T) {
	request := validGenerateTrainingRequest(t)
	request.Profile.Sex = " male "
	request.Profile.FitnessLevel = " beginner "
	request.Profile.InjuriesAndLimitations = " none "
	request.Profile.TrainingGoal = " Build consistency "
	request.Profile.AdditionalPromptFields = []promptFieldRequest{{
		Label: " Любимый формат ",
		Value: " Интервалы ",
	}}
	request.Request.UserNote = " Без интенсивных ускорений "

	providerRequest, err := request.toProviderRequest()
	if err != nil {
		t.Fatalf("toProviderRequest() error = %v", err)
	}

	if providerRequest.Profile.Sex != "male" {
		t.Fatalf("Profile.Sex = %q, want %q", providerRequest.Profile.Sex, "male")
	}
	if providerRequest.Profile.FitnessLevel != "beginner" {
		t.Fatalf("Profile.FitnessLevel = %q, want %q", providerRequest.Profile.FitnessLevel, "beginner")
	}
	if providerRequest.Profile.InjuriesAndLimitations != "none" {
		t.Fatalf("Profile.InjuriesAndLimitations = %q, want %q", providerRequest.Profile.InjuriesAndLimitations, "none")
	}
	if providerRequest.Profile.TrainingGoal != "Build consistency" {
		t.Fatalf("Profile.TrainingGoal = %q, want %q", providerRequest.Profile.TrainingGoal, "Build consistency")
	}
	if providerRequest.UserNote != "Без интенсивных ускорений" {
		t.Fatalf("UserNote = %q, want %q", providerRequest.UserNote, "Без интенсивных ускорений")
	}
	if len(providerRequest.Profile.AdditionalPromptFields) != 1 {
		t.Fatalf("len(Profile.AdditionalPromptFields) = %d, want %d", len(providerRequest.Profile.AdditionalPromptFields), 1)
	}
	if providerRequest.Profile.AdditionalPromptFields[0].Label != "Любимый формат" {
		t.Fatalf("AdditionalPromptFields[0].Label = %q, want %q", providerRequest.Profile.AdditionalPromptFields[0].Label, "Любимый формат")
	}
	if providerRequest.Profile.AdditionalPromptFields[0].Value != "Интервалы" {
		t.Fatalf("AdditionalPromptFields[0].Value = %q, want %q", providerRequest.Profile.AdditionalPromptFields[0].Value, "Интервалы")
	}
}

func TestDecodeJSONBodyRejectsUnknownFieldsAndMultipleObjects(t *testing.T) {
	t.Run("unknown field", func(t *testing.T) {
		request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(`{"extra":true}`))
		recorder := httptest.NewRecorder()

		var payload generateTrainingRequest
		err := decodeJSONBody(recorder, request, &payload)
		if err == nil {
			t.Fatal("decodeJSONBody() error = nil, want error")
		}
		if !strings.Contains(err.Error(), "unknown field") {
			t.Fatalf("decodeJSONBody() error = %v, want unknown field", err)
		}
	})

	t.Run("multiple objects", func(t *testing.T) {
		request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(`{} {}`))
		recorder := httptest.NewRecorder()

		var payload generateTrainingRequest
		err := decodeJSONBody(recorder, request, &payload)
		if err == nil {
			t.Fatal("decodeJSONBody() error = nil, want error")
		}
		if !strings.Contains(err.Error(), "exactly one JSON object") {
			t.Fatalf("decodeJSONBody() error = %v, want exact object error", err)
		}
	})
}

func TestHandleGenerateTrainingReturnsStreamingUnsupportedWithoutFlusher(t *testing.T) {
	h := &handler{
		logger:         testLogger(),
		service:        &stubTrainingService{},
		requestTimeout: 2 * time.Second,
	}
	response := newNonFlushingResponseWriter()
	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(validGenerateRequestJSON))

	h.handleGenerateTraining(response, request)

	if response.statusCode != http.StatusInternalServerError {
		t.Fatalf("status = %d, want %d", response.statusCode, http.StatusInternalServerError)
	}

	var payload errorEnvelope
	if err := json.Unmarshal([]byte(response.body.String()), &payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Error.Code != "streaming_unsupported" {
		t.Fatalf("error.code = %q, want %q", payload.Error.Code, "streaming_unsupported")
	}
}

func TestNewRouterReturnsStreamingUnsupportedWithoutFlusher(t *testing.T) {
	router := NewRouter(testLogger(), &stubTrainingService{}, 2*time.Second)
	response := newNonFlushingResponseWriter()
	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(validGenerateRequestJSON))

	router.ServeHTTP(response, request)

	if response.statusCode != http.StatusInternalServerError {
		t.Fatalf("status = %d, want %d", response.statusCode, http.StatusInternalServerError)
	}

	var payload errorEnvelope
	if err := json.Unmarshal([]byte(response.body.String()), &payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Error.Code != "streaming_unsupported" {
		t.Fatalf("error.code = %q, want %q", payload.Error.Code, "streaming_unsupported")
	}
}

func TestGenerateTrainingCancelsProviderWhenStreamWriteFails(t *testing.T) {
	service := &cancelAwareTrainingService{}
	h := &handler{
		logger:         testLogger(),
		service:        service,
		requestTimeout: 2 * time.Second,
	}
	response := newFailingStreamResponseWriter(errors.New("client disconnected"))
	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(validGenerateRequestJSON))

	h.handleGenerateTraining(response, request)

	if response.statusCode != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.statusCode, http.StatusOK)
	}
	if !service.sawCancellation {
		t.Fatal("service context was not canceled after stream write failure")
	}
	if response.writeCalls != 1 {
		t.Fatalf("writeCalls = %d, want %d", response.writeCalls, 1)
	}
}

func TestWriteSSEEventReturnsMarshalError(t *testing.T) {
	recorder := httptest.NewRecorder()

	err := writeSSEEvent(recorder, recorder, provider.StreamEventLog, map[string]any{
		"bad": make(chan int),
	})
	if err == nil {
		t.Fatal("writeSSEEvent() error = nil, want error")
	}
}

func TestWriteSSEEventReturnsWriterError(t *testing.T) {
	writer := failingSSEWriter{err: errors.New("write failed")}

	err := writeSSEEvent(writer, writer, provider.StreamEventLog, provider.ProgressChunk{Message: "progress"})
	if err == nil {
		t.Fatal("writeSSEEvent() error = nil, want error")
	}
	if !strings.Contains(err.Error(), "write failed") {
		t.Fatalf("writeSSEEvent() error = %v, want write failure", err)
	}
}

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

type stubTrainingService struct {
	response    provider.TrainingEnvelope
	progress    []provider.ProgressChunk
	err         error
	request     provider.GenerateRequest
	sawDeadline bool
}

func (s *stubTrainingService) GenerateTrainingStream(
	ctx context.Context,
	request provider.GenerateRequest,
	report provider.ProgressReporter,
) (provider.TrainingEnvelope, error) {
	s.request = request
	_, s.sawDeadline = ctx.Deadline()
	for _, chunk := range s.progress {
		if report != nil {
			report(chunk)
		}
	}
	return s.response, s.err
}

type cancelAwareTrainingService struct {
	sawCancellation bool
}

func (s *cancelAwareTrainingService) GenerateTrainingStream(
	ctx context.Context,
	_ provider.GenerateRequest,
	report provider.ProgressReporter,
) (provider.TrainingEnvelope, error) {
	if report != nil {
		report(provider.ProgressChunk{Message: "building training prompt"})
	}
	<-ctx.Done()
	s.sawCancellation = errors.Is(ctx.Err(), context.Canceled)
	return provider.TrainingEnvelope{}, ctx.Err()
}

type sseEvent struct {
	Name string
	Data string
}

type nonFlushingResponseWriter struct {
	header     http.Header
	body       strings.Builder
	statusCode int
}

func newNonFlushingResponseWriter() *nonFlushingResponseWriter {
	return &nonFlushingResponseWriter{
		header: make(http.Header),
	}
}

func (w *nonFlushingResponseWriter) Header() http.Header {
	return w.header
}

func (w *nonFlushingResponseWriter) Write(payload []byte) (int, error) {
	if w.statusCode == 0 {
		w.statusCode = http.StatusOK
	}
	return w.body.Write(payload)
}

func (w *nonFlushingResponseWriter) WriteHeader(statusCode int) {
	w.statusCode = statusCode
}

type failingSSEWriter struct {
	err error
}

func (w failingSSEWriter) Write(_ []byte) (int, error) {
	return 0, w.err
}

func (failingSSEWriter) Flush() {}

type failingStreamResponseWriter struct {
	header     http.Header
	statusCode int
	writeCalls int
	err        error
}

func newFailingStreamResponseWriter(err error) *failingStreamResponseWriter {
	return &failingStreamResponseWriter{
		header: make(http.Header),
		err:    err,
	}
}

func (w *failingStreamResponseWriter) Header() http.Header {
	return w.header
}

func (w *failingStreamResponseWriter) Write(_ []byte) (int, error) {
	w.writeCalls++
	if w.statusCode == 0 {
		w.statusCode = http.StatusOK
	}
	return 0, w.err
}

func (w *failingStreamResponseWriter) WriteHeader(statusCode int) {
	w.statusCode = statusCode
}

func (w *failingStreamResponseWriter) Flush() {}

func parseSSEEvents(t *testing.T, raw string) []sseEvent {
	t.Helper()

	chunks := strings.Split(strings.TrimSpace(raw), "\n\n")
	events := make([]sseEvent, 0, len(chunks))
	for _, chunk := range chunks {
		chunk = strings.TrimSpace(chunk)
		if chunk == "" {
			continue
		}

		event := sseEvent{}
		for _, line := range strings.Split(chunk, "\n") {
			switch {
			case strings.HasPrefix(line, "event: "):
				event.Name = strings.TrimSpace(strings.TrimPrefix(line, "event: "))
			case strings.HasPrefix(line, "data: "):
				if event.Data != "" {
					event.Data += "\n"
				}
				event.Data += strings.TrimSpace(strings.TrimPrefix(line, "data: "))
			}
		}

		if event.Name == "" {
			t.Fatalf("stream chunk missing event name: %q", chunk)
		}
		if event.Data == "" {
			t.Fatalf("stream chunk missing data payload: %q", chunk)
		}

		events = append(events, event)
	}

	return events
}

const validGenerateRequestJSON = `{
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
        "label": "Любимый формат",
        "value": "Интервалы"
      }
    ]
  },
  "request": {
    "locale": "ru-RU",
    "user_note": "Без интенсивных ускорений"
  }
}`

func validGenerateTrainingRequest(t *testing.T) generateTrainingRequest {
	t.Helper()

	var request generateTrainingRequest
	if err := json.Unmarshal([]byte(validGenerateRequestJSON), &request); err != nil {
		t.Fatalf("unmarshal valid request: %v", err)
	}

	return request
}
