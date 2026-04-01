package schema

import (
	"strings"
	"testing"

	"github.com/muzyk0/running-app/backend/internal/provider"
)

func TestNormalizerNormalizeRepairsBareTrainingObject(t *testing.T) {
	rawOutput := `
{
  "title": "Фартлек",
  "summary": "Плавное чередование бега и шага",
  "steps": [
    {
      "type": "warmup_walk",
      "duration_sec": "300",
      "voice_prompt": "Пять минут разминки."
    },
    {
      "id": "step-1",
      "type": "jog",
      "duration_sec": 120,
      "voice_prompt": ""
    },
    {
      "id": "step-1",
      "type": "recovery_walk",
      "duration_sec": 180
    },
    {
      "type": "cooldown_walk",
      "duration_sec": 240
    }
  ]
}
`

	result, err := NewNormalizer().Normalize(rawOutput, sampleGenerateRequest(provider.SupportedLocaleRussian))
	if err != nil {
		t.Fatalf("Normalize() error = %v", err)
	}

	if result.SchemaVersion != provider.SchemaVersionMVPv1 {
		t.Fatalf("SchemaVersion = %q, want %q", result.SchemaVersion, provider.SchemaVersionMVPv1)
	}
	if result.Training.Goal != "Build consistency" {
		t.Fatalf("Goal = %q, want fallback training goal", result.Training.Goal)
	}
	if result.Training.Disclaimer != provider.DisclaimerForLocale(provider.SupportedLocaleRussian) {
		t.Fatalf("Disclaimer = %q, want %q", result.Training.Disclaimer, provider.DisclaimerForLocale(provider.SupportedLocaleRussian))
	}
	if result.Training.EstimatedDurationSec != 840 {
		t.Fatalf("EstimatedDurationSec = %d, want %d", result.Training.EstimatedDurationSec, 840)
	}

	wantTypes := []string{"warmup", "run", "walk", "cooldown"}
	for index, step := range result.Training.Steps {
		if step.Type != wantTypes[index] {
			t.Fatalf("steps[%d].Type = %q, want %q", index, step.Type, wantTypes[index])
		}
		if step.ID == "" {
			t.Fatalf("steps[%d].ID is blank", index)
		}
		if strings.TrimSpace(step.VoicePrompt) == "" {
			t.Fatalf("steps[%d].VoicePrompt is blank", index)
		}
	}

	if result.Training.Steps[1].ID != "step-2" {
		t.Fatalf("steps[1].ID = %q, want %q", result.Training.Steps[1].ID, "step-2")
	}
	if result.Training.Steps[2].ID != "step-3" {
		t.Fatalf("steps[2].ID = %q, want generated unique ID", result.Training.Steps[2].ID)
	}
	if result.Training.Steps[3].ID != "step-4" {
		t.Fatalf("steps[3].ID = %q, want generated unique ID", result.Training.Steps[3].ID)
	}
	if !strings.Contains(result.Training.Steps[3].VoicePrompt, "заминки") {
		t.Fatalf("steps[3].VoicePrompt = %q, want cooldown fallback", result.Training.Steps[3].VoicePrompt)
	}
}

func TestNormalizerNormalizeAcceptsMarkdownFence(t *testing.T) {
	rawOutput := "```json\n" +
		"{\n" +
		"  \"schema_version\": \"legacy.v0\",\n" +
		"  \"training\": {\n" +
		"    \"title\": \"Легкий бег\",\n" +
		"    \"goal\": \"Поддержка\",\n" +
		"    \"steps\": [\n" +
		"      {\n" +
		"        \"id\": \"step-1\",\n" +
		"        \"type\": \"run\",\n" +
		"        \"duration_sec\": 60,\n" +
		"        \"voice_prompt\": \"Минута легкого бега.\"\n" +
		"      }\n" +
		"    ]\n" +
		"  }\n" +
		"}\n" +
		"```"

	result, err := NewNormalizer().Normalize(rawOutput, sampleGenerateRequest(provider.SupportedLocaleRussian))
	if err != nil {
		t.Fatalf("Normalize() error = %v", err)
	}

	if result.SchemaVersion != provider.SchemaVersionMVPv1 {
		t.Fatalf("SchemaVersion = %q, want %q", result.SchemaVersion, provider.SchemaVersionMVPv1)
	}
	if result.Training.EstimatedDurationSec != 60 {
		t.Fatalf("EstimatedDurationSec = %d, want %d", result.Training.EstimatedDurationSec, 60)
	}
}

func TestNormalizerNormalizeRejectsUnsupportedStepType(t *testing.T) {
	rawOutput := `{
  "training": {
    "title": "Ошибка",
    "steps": [
      {
        "id": "step-1",
        "type": "teleport",
        "duration_sec": 30,
        "voice_prompt": "Неверный шаг."
      }
    ]
  }
}`

	_, err := NewNormalizer().Normalize(rawOutput, sampleGenerateRequest(provider.SupportedLocaleRussian))
	if err == nil {
		t.Fatal("Normalize() error = nil, want error")
	}
	if !strings.Contains(err.Error(), "unsupported value") {
		t.Fatalf("Normalize() error = %v, want unsupported step type", err)
	}
}

func TestNormalizerNormalizeUsesEnglishFallbackCopy(t *testing.T) {
	rawOutput := `{
  "training": {
    "title": "Easy interval workout",
    "summary": "Short confidence-building session.",
    "steps": [
      {
        "type": "warmup",
        "duration_sec": 300
      },
      {
        "type": "run",
        "duration_sec": 120,
        "voice_prompt": ""
      },
      {
        "type": "walk",
        "duration_sec": 180
      },
      {
        "type": "cooldown",
        "duration_sec": 240
      }
    ]
  }
}`

	result, err := NewNormalizer().Normalize(rawOutput, sampleGenerateRequest(provider.SupportedLocaleEnglish))
	if err != nil {
		t.Fatalf("Normalize() error = %v", err)
	}

	if result.Training.Disclaimer != provider.DisclaimerForLocale(provider.SupportedLocaleEnglish) {
		t.Fatalf("Disclaimer = %q, want %q", result.Training.Disclaimer, provider.DisclaimerForLocale(provider.SupportedLocaleEnglish))
	}

	wantPrompts := []string{
		"Warm up with brisk walking for 5 minutes.",
		"Run easily for 2 minutes.",
		"Recover with walking for 3 minutes.",
		"Cool down with easy walking for 4 minutes.",
	}
	for index, want := range wantPrompts {
		if result.Training.Steps[index].VoicePrompt != want {
			t.Fatalf("steps[%d].VoicePrompt = %q, want %q", index, result.Training.Steps[index].VoicePrompt, want)
		}
	}
}

func TestExtractJSONBodyRejectsEmptyAndInvalidInput(t *testing.T) {
	cases := []string{"", "   ", "not-json"}

	for _, raw := range cases {
		if _, err := extractJSONBody(raw); err == nil {
			t.Fatalf("extractJSONBody(%q) error = nil, want error", raw)
		}
	}
}

func TestFormatDurationRussianUsesNaturalForms(t *testing.T) {
	cases := map[int]string{
		0:   "0 секунд",
		61:  "1 минута 1 секунда",
		125: "2 минуты 5 секунд",
	}

	for durationSec, want := range cases {
		if got := formatDurationRussian(durationSec); got != want {
			t.Fatalf("formatDurationRussian(%d) = %q, want %q", durationSec, got, want)
		}
	}
}

func TestFormatDurationEnglishUsesNaturalForms(t *testing.T) {
	cases := map[int]string{
		0:   "0 seconds",
		61:  "1 minute 1 second",
		125: "2 minutes 5 seconds",
	}

	for durationSec, want := range cases {
		if got := formatDurationEnglish(durationSec); got != want {
			t.Fatalf("formatDurationEnglish(%d) = %q, want %q", durationSec, got, want)
		}
	}
}

func sampleGenerateRequest(locale string) provider.GenerateRequest {
	return provider.GenerateRequest{
		Profile: provider.ProfileSnapshot{
			TrainingGoal: "Build consistency",
		},
		Locale: locale,
	}
}
