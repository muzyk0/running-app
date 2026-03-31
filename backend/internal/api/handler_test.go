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

func TestGenerateTrainingSuccess(t *testing.T) {
	stub := &stubTrainingService{
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
	if !stub.sawDeadline {
		t.Fatal("service context did not include a deadline")
	}
	if stub.request.Profile.HeightCM != 180 {
		t.Fatalf("profile.height_cm = %d, want %d", stub.request.Profile.HeightCM, 180)
	}
	if stub.request.Locale != "ru-RU" {
		t.Fatalf("locale = %q, want %q", stub.request.Locale, "ru-RU")
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
}

func TestGenerateTrainingProviderError(t *testing.T) {
	router := NewRouter(testLogger(), &stubTrainingService{err: errors.New("boom")}, 2*time.Second)

	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(validGenerateRequestJSON))
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusBadGateway {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusBadGateway)
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
	router := NewRouter(testLogger(), &stubTrainingService{err: context.DeadlineExceeded}, 25*time.Millisecond)

	request := httptest.NewRequest(http.MethodPost, "/v1/trainings/generate", strings.NewReader(validGenerateRequestJSON))
	response := httptest.NewRecorder()

	router.ServeHTTP(response, request)

	if response.Code != http.StatusGatewayTimeout {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusGatewayTimeout)
	}

	var payload errorEnvelope
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Error.Code != "request_timeout" {
		t.Fatalf("error.code = %q, want %q", payload.Error.Code, "request_timeout")
	}
}

func TestGenerateTrainingReturnsProviderNotConfiguredError(t *testing.T) {
	router := NewRouter(testLogger(), &stubTrainingService{err: service.ErrProviderNotConfigured}, 2*time.Second)

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

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

type stubTrainingService struct {
	response    provider.TrainingEnvelope
	err         error
	request     provider.GenerateRequest
	sawDeadline bool
}

func (s *stubTrainingService) GenerateTraining(ctx context.Context, request provider.GenerateRequest) (provider.TrainingEnvelope, error) {
	s.request = request
	_, s.sawDeadline = ctx.Deadline()
	return s.response, s.err
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
