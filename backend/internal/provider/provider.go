package provider

import (
	"context"
)

const (
	SchemaVersionMVPv1 = "mvp.v1"
	DefaultDisclaimer  = "Приложение не является медицинской рекомендацией."
)

type StreamEventType string

const (
	StreamEventLog       StreamEventType = "log"
	StreamEventCompleted StreamEventType = "completed"
	StreamEventError     StreamEventType = "error"
)

type AdditionalPromptField struct {
	Label string `json:"label"`
	Value string `json:"value"`
}

type ProfileSnapshot struct {
	HeightCM               int                     `json:"height_cm"`
	WeightKG               int                     `json:"weight_kg"`
	Sex                    string                  `json:"sex"`
	Age                    int                     `json:"age"`
	TrainingDaysPerWeek    int                     `json:"training_days_per_week"`
	FitnessLevel           string                  `json:"fitness_level"`
	InjuriesAndLimitations string                  `json:"injuries_and_limitations"`
	TrainingGoal           string                  `json:"training_goal"`
	AdditionalPromptFields []AdditionalPromptField `json:"additional_prompt_fields,omitempty"`
}

type GenerateRequest struct {
	Profile  ProfileSnapshot
	Locale   string
	UserNote string
}

type CompletionRequest struct {
	SystemPrompt string
	UserPrompt   string
}

type CompletionResponse struct {
	RawOutput string
}

type ProgressChunk struct {
	Message string `json:"message"`
}

type ProgressReporter func(ProgressChunk)

type TrainingEnvelope struct {
	SchemaVersion string   `json:"schema_version"`
	Training      Training `json:"training"`
}

type Training struct {
	Title                string         `json:"title"`
	Summary              string         `json:"summary,omitempty"`
	Goal                 string         `json:"goal,omitempty"`
	EstimatedDurationSec int            `json:"estimated_duration_sec"`
	Disclaimer           string         `json:"disclaimer,omitempty"`
	Steps                []TrainingStep `json:"steps"`
}

type TrainingStep struct {
	ID          string `json:"id"`
	Type        string `json:"type"`
	DurationSec int    `json:"duration_sec"`
	VoicePrompt string `json:"voice_prompt"`
}

type Generator interface {
	Name() string
	Generate(ctx context.Context, request CompletionRequest) (CompletionResponse, error)
}

type StreamGenerator interface {
	Generator
	GenerateStream(ctx context.Context, request CompletionRequest, report ProgressReporter) (CompletionResponse, error)
}

type StaticGenerator struct{}

func NewStaticGenerator() StaticGenerator {
	return StaticGenerator{}
}

func (StaticGenerator) Name() string {
	return "static"
}

func (StaticGenerator) Generate(ctx context.Context, _ CompletionRequest) (CompletionResponse, error) {
	select {
	case <-ctx.Done():
		return CompletionResponse{}, ctx.Err()
	default:
	}

	return CompletionResponse{
		RawOutput: `{
  "schema_version": "mvp.v1",
  "training": {
    "title": "Базовая интервальная тренировка",
    "summary": "Чередование спокойного бега и ходьбы для безопасного старта тренировки.",
    "goal": "Постепенная адаптация к беговой нагрузке",
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
}`,
	}, nil
}
