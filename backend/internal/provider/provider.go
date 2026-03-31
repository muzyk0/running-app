package provider

import (
	"context"
	"fmt"
	"strings"
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
	Generate(ctx context.Context, request GenerateRequest) (TrainingEnvelope, error)
}

type StaticGenerator struct{}

func NewStaticGenerator() StaticGenerator {
	return StaticGenerator{}
}

func (StaticGenerator) Name() string {
	return "static"
}

func (StaticGenerator) Generate(ctx context.Context, request GenerateRequest) (TrainingEnvelope, error) {
	select {
	case <-ctx.Done():
		return TrainingEnvelope{}, ctx.Err()
	default:
	}

	goal := strings.TrimSpace(request.Profile.TrainingGoal)
	if goal == "" {
		goal = "Постепенная адаптация к беговой нагрузке"
	}

	title, steps := chooseWorkoutPlan(request.Profile.FitnessLevel)
	estimatedDuration := 0
	for _, step := range steps {
		estimatedDuration += step.DurationSec
	}

	return TrainingEnvelope{
		SchemaVersion: "mvp.v1",
		Training: Training{
			Title:                title,
			Summary:              "Чередование спокойного бега и ходьбы для безопасного старта тренировки.",
			Goal:                 goal,
			EstimatedDurationSec: estimatedDuration,
			Disclaimer:           "Приложение не является медицинской рекомендацией.",
			Steps:                steps,
		},
	}, nil
}

func chooseWorkoutPlan(fitnessLevel string) (string, []TrainingStep) {
	switch normalizeFitnessLevel(fitnessLevel) {
	case "advanced":
		return "Интервальная тренировка на выносливость", []TrainingStep{
			{ID: "step-1", Type: "warmup", DurationSec: 420, VoicePrompt: "Семь минут разминки быстрым шагом."},
			{ID: "step-2", Type: "run", DurationSec: 360, VoicePrompt: "Шесть минут легкого бега."},
			{ID: "step-3", Type: "walk", DurationSec: 120, VoicePrompt: "Две минуты восстановления шагом."},
			{ID: "step-4", Type: "run", DurationSec: 360, VoicePrompt: "Еще шесть минут ровного бега."},
			{ID: "step-5", Type: "cooldown", DurationSec: 300, VoicePrompt: "Пять минут заминки спокойным шагом."},
		}
	case "intermediate":
		return "Интервальная тренировка средней сложности", []TrainingStep{
			{ID: "step-1", Type: "warmup", DurationSec: 300, VoicePrompt: "Пять минут разминки быстрым шагом."},
			{ID: "step-2", Type: "run", DurationSec: 240, VoicePrompt: "Четыре минуты легкого бега."},
			{ID: "step-3", Type: "walk", DurationSec: 120, VoicePrompt: "Две минуты восстановления шагом."},
			{ID: "step-4", Type: "run", DurationSec: 240, VoicePrompt: "Еще четыре минуты спокойного бега."},
			{ID: "step-5", Type: "cooldown", DurationSec: 300, VoicePrompt: "Пять минут заминки спокойным шагом."},
		}
	default:
		return "Базовая интервальная тренировка", []TrainingStep{
			{ID: "step-1", Type: "warmup", DurationSec: 300, VoicePrompt: "Пять минут разминки быстрым шагом."},
			{ID: "step-2", Type: "run", DurationSec: 120, VoicePrompt: "Две минуты очень легкого бега."},
			{ID: "step-3", Type: "walk", DurationSec: 180, VoicePrompt: "Три минуты восстановления шагом."},
			{ID: "step-4", Type: "run", DurationSec: 120, VoicePrompt: "Еще две минуты легкого бега."},
			{ID: "step-5", Type: "cooldown", DurationSec: 300, VoicePrompt: "Пять минут заминки спокойным шагом."},
		}
	}
}

func normalizeFitnessLevel(level string) string {
	normalized := strings.ToLower(strings.TrimSpace(level))
	switch normalized {
	case "advanced", "опытный", "продвинутый":
		return "advanced"
	case "intermediate", "средний":
		return "intermediate"
	case "", "beginner", "новичок":
		return "beginner"
	default:
		return fmt.Sprintf("custom:%s", normalized)
	}
}
