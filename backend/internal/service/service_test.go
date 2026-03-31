package service

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/muzyk0/running-app/backend/internal/provider"
)

func TestGenerateTrainingBuildsPromptAndNormalizesResponse(t *testing.T) {
	rawOutput := readFixture(t, "happy-path.json")
	stub := &stubGenerator{
		response: provider.CompletionResponse{
			RawOutput: rawOutput,
		},
	}

	service := NewTrainingService(stub, testLogger())

	result, err := service.GenerateTraining(context.Background(), validRequest())
	if err != nil {
		t.Fatalf("GenerateTraining() error = %v", err)
	}

	if !strings.Contains(stub.request.SystemPrompt, "Верни только JSON-объект") {
		t.Fatalf("system prompt = %q, want JSON-only instruction", stub.request.SystemPrompt)
	}
	if !strings.Contains(stub.request.SystemPrompt, provider.DefaultDisclaimer) {
		t.Fatalf("system prompt = %q, want disclaimer guidance", stub.request.SystemPrompt)
	}
	if !strings.Contains(stub.request.UserPrompt, "Пожелание пользователя: Без интенсивных ускорений") {
		t.Fatalf("user prompt = %q, want user note", stub.request.UserPrompt)
	}
	if !strings.Contains(stub.request.UserPrompt, "Поверхность: Стадион или ровный асфальт") {
		t.Fatalf("user prompt = %q, want additional prompt fields", stub.request.UserPrompt)
	}
	if !strings.Contains(stub.request.UserPrompt, "Локаль ответа: ru-RU") {
		t.Fatalf("user prompt = %q, want locale", stub.request.UserPrompt)
	}

	if result.SchemaVersion != provider.SchemaVersionMVPv1 {
		t.Fatalf("SchemaVersion = %q, want %q", result.SchemaVersion, provider.SchemaVersionMVPv1)
	}
	if result.Training.EstimatedDurationSec != 1020 {
		t.Fatalf("EstimatedDurationSec = %d, want %d", result.Training.EstimatedDurationSec, 1020)
	}
	if len(result.Training.Steps) != 5 {
		t.Fatalf("len(steps) = %d, want %d", len(result.Training.Steps), 5)
	}
	if result.Training.Steps[0].Type != "warmup" {
		t.Fatalf("steps[0].Type = %q, want %q", result.Training.Steps[0].Type, "warmup")
	}
	if result.Training.Steps[4].Type != "cooldown" {
		t.Fatalf("steps[4].Type = %q, want %q", result.Training.Steps[4].Type, "cooldown")
	}
}

func TestGenerateTrainingRejectsMalformedJSON(t *testing.T) {
	service := NewTrainingService(
		&stubGenerator{response: provider.CompletionResponse{RawOutput: readFixture(t, "malformed-json.txt")}},
		testLogger(),
	)

	_, err := service.GenerateTraining(context.Background(), validRequest())
	if err == nil {
		t.Fatal("GenerateTraining() error = nil, want error")
	}
	if !strings.Contains(err.Error(), "not valid JSON") {
		t.Fatalf("GenerateTraining() error = %v, want JSON validation error", err)
	}
}

func TestGenerateTrainingRejectsMissingRequiredFields(t *testing.T) {
	service := NewTrainingService(
		&stubGenerator{response: provider.CompletionResponse{RawOutput: readFixture(t, "missing-fields.json")}},
		testLogger(),
	)

	_, err := service.GenerateTraining(context.Background(), validRequest())
	if err == nil {
		t.Fatal("GenerateTraining() error = nil, want error")
	}
	if !strings.Contains(err.Error(), "training.title is required") {
		t.Fatalf("GenerateTraining() error = %v, want missing title error", err)
	}
}

func TestGenerateTrainingRepairsDuplicateStepIDs(t *testing.T) {
	service := NewTrainingService(
		&stubGenerator{response: provider.CompletionResponse{RawOutput: readFixture(t, "duplicate-step-ids.json")}},
		testLogger(),
	)

	result, err := service.GenerateTraining(context.Background(), validRequest())
	if err != nil {
		t.Fatalf("GenerateTraining() error = %v", err)
	}

	if result.Training.EstimatedDurationSec != 600 {
		t.Fatalf("EstimatedDurationSec = %d, want %d", result.Training.EstimatedDurationSec, 600)
	}
	if result.Training.Disclaimer != provider.DefaultDisclaimer {
		t.Fatalf("Disclaimer = %q, want %q", result.Training.Disclaimer, provider.DefaultDisclaimer)
	}
	if result.Training.Steps[0].ID != "step-1" {
		t.Fatalf("steps[0].ID = %q, want %q", result.Training.Steps[0].ID, "step-1")
	}
	if result.Training.Steps[1].ID != "step-2" {
		t.Fatalf("steps[1].ID = %q, want %q", result.Training.Steps[1].ID, "step-2")
	}
	if result.Training.Steps[1].VoicePrompt == "" {
		t.Fatal("steps[1].VoicePrompt = empty, want generated fallback")
	}
}

func TestGenerateTrainingPropagatesProviderFailure(t *testing.T) {
	expectedErr := errors.New("provider unavailable")
	service := NewTrainingService(
		&stubGenerator{err: expectedErr},
		testLogger(),
	)

	_, err := service.GenerateTraining(context.Background(), validRequest())
	if !errors.Is(err, expectedErr) {
		t.Fatalf("GenerateTraining() error = %v, want %v", err, expectedErr)
	}
}

func TestGenerateTrainingRejectsMissingProvider(t *testing.T) {
	service := NewTrainingService(nil, testLogger())

	_, err := service.GenerateTraining(context.Background(), validRequest())
	if !errors.Is(err, ErrProviderNotConfigured) {
		t.Fatalf("GenerateTraining() error = %v, want %v", err, ErrProviderNotConfigured)
	}
}

func TestNewTrainingServiceUsesDefaultBuilderAndNormalizer(t *testing.T) {
	service := NewTrainingService(provider.NewStaticGenerator(), nil)

	result, err := service.GenerateTraining(context.Background(), validRequest())
	if err != nil {
		t.Fatalf("GenerateTraining() error = %v", err)
	}
	if result.SchemaVersion != provider.SchemaVersionMVPv1 {
		t.Fatalf("SchemaVersion = %q, want %q", result.SchemaVersion, provider.SchemaVersionMVPv1)
	}
	if result.Training.Title == "" {
		t.Fatal("Training.Title = empty, want populated title")
	}
	if len(result.Training.Steps) == 0 {
		t.Fatal("Training.Steps = empty, want normalized steps")
	}
}

type stubGenerator struct {
	request  provider.CompletionRequest
	response provider.CompletionResponse
	err      error
}

func (s *stubGenerator) Name() string {
	return "stub"
}

func (s *stubGenerator) Generate(_ context.Context, request provider.CompletionRequest) (provider.CompletionResponse, error) {
	s.request = request
	return s.response, s.err
}

func readFixture(t *testing.T, name string) string {
	t.Helper()

	path := filepath.Join("..", "..", "testdata", "provider-output", name)
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read fixture %s: %v", path, err)
	}

	return string(data)
}

func validRequest() provider.GenerateRequest {
	return provider.GenerateRequest{
		Profile: provider.ProfileSnapshot{
			HeightCM:               180,
			WeightKG:               77,
			Sex:                    "male",
			Age:                    31,
			TrainingDaysPerWeek:    4,
			FitnessLevel:           "beginner",
			InjuriesAndLimitations: "none",
			TrainingGoal:           "Build consistency",
			AdditionalPromptFields: []provider.AdditionalPromptField{
				{
					Label: "Поверхность",
					Value: "Стадион или ровный асфальт",
				},
			},
		},
		Locale:   "ru-RU",
		UserNote: "Без интенсивных ускорений",
	}
}

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}
