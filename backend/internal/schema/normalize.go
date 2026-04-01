package schema

import (
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"unicode"
	"unicode/utf8"

	"github.com/muzyk0/running-app/backend/internal/provider"
)

type Normalizer struct {
}

func NewNormalizer() Normalizer {
	return Normalizer{}
}

func (n Normalizer) Normalize(rawOutput string, request provider.GenerateRequest) (provider.TrainingEnvelope, error) {
	jsonBody, err := extractJSONBody(rawOutput)
	if err != nil {
		return provider.TrainingEnvelope{}, err
	}

	var decoded map[string]any
	if err := json.Unmarshal([]byte(jsonBody), &decoded); err != nil {
		return provider.TrainingEnvelope{}, fmt.Errorf("decode provider output: %w", err)
	}

	return n.normalizeEnvelope(decoded, request)
}

func (n Normalizer) normalizeEnvelope(decoded map[string]any, request provider.GenerateRequest) (provider.TrainingEnvelope, error) {
	locale := provider.EffectiveLocale(request.Locale)
	trainingObject := decoded
	if rawTraining, ok := decoded["training"]; ok {
		mapped, ok := rawTraining.(map[string]any)
		if !ok {
			return provider.TrainingEnvelope{}, errors.New("training must be a JSON object")
		}
		trainingObject = mapped
	} else if !looksLikeTrainingObject(decoded) {
		return provider.TrainingEnvelope{}, errors.New("provider output must contain training")
	}

	title := readOptionalString(trainingObject["title"])
	if title == "" {
		return provider.TrainingEnvelope{}, errors.New("training.title is required")
	}

	summary := readOptionalString(trainingObject["summary"])
	goal := readOptionalString(trainingObject["goal"])
	if goal == "" {
		goal = strings.TrimSpace(request.Profile.TrainingGoal)
	}

	disclaimer := readOptionalString(trainingObject["disclaimer"])
	if disclaimer == "" {
		disclaimer = provider.DisclaimerForLocale(locale)
	}

	rawSteps, ok := trainingObject["steps"].([]any)
	if !ok || len(rawSteps) == 0 {
		return provider.TrainingEnvelope{}, errors.New("training.steps must be a non-empty array")
	}

	usedIDs := make(map[string]struct{}, len(rawSteps))
	steps := make([]provider.TrainingStep, 0, len(rawSteps))
	totalDuration := 0

	for index, rawStep := range rawSteps {
		stepMap, ok := rawStep.(map[string]any)
		if !ok {
			return provider.TrainingEnvelope{}, fmt.Errorf("training.steps[%d] must be an object", index)
		}

		durationSec, err := readPositiveInt(stepMap["duration_sec"])
		if err != nil {
			return provider.TrainingEnvelope{}, fmt.Errorf("training.steps[%d].duration_sec %w", index, err)
		}

		stepType, err := normalizeStepType(readOptionalString(stepMap["type"]))
		if err != nil {
			return provider.TrainingEnvelope{}, fmt.Errorf("training.steps[%d].type %w", index, err)
		}

		stepID := ensureUniqueStepID(readOptionalString(stepMap["id"]), index, usedIDs)

		voicePrompt := readOptionalString(stepMap["voice_prompt"])
		if voicePrompt == "" {
			voicePrompt = defaultVoicePrompt(stepType, durationSec, locale)
		}

		steps = append(steps, provider.TrainingStep{
			ID:          stepID,
			Type:        stepType,
			DurationSec: durationSec,
			VoicePrompt: voicePrompt,
		})
		totalDuration += durationSec
	}

	return provider.TrainingEnvelope{
		SchemaVersion: provider.SchemaVersionMVPv1,
		Training: provider.Training{
			Title:                title,
			Summary:              summary,
			Goal:                 goal,
			EstimatedDurationSec: totalDuration,
			Disclaimer:           disclaimer,
			Steps:                steps,
		},
	}, nil
}

func extractJSONBody(raw string) (string, error) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return "", errors.New("provider output is empty")
	}

	candidates := []string{trimmed}

	if stripped, ok := stripMarkdownFence(trimmed); ok {
		candidates = append(candidates, stripped)
	}

	start := strings.Index(trimmed, "{")
	end := strings.LastIndex(trimmed, "}")
	if start >= 0 && end > start {
		candidates = append(candidates, trimmed[start:end+1])
	}

	seen := make(map[string]struct{}, len(candidates))
	for _, candidate := range candidates {
		candidate = strings.TrimSpace(candidate)
		if candidate == "" {
			continue
		}
		if _, ok := seen[candidate]; ok {
			continue
		}
		seen[candidate] = struct{}{}
		if json.Valid([]byte(candidate)) {
			return candidate, nil
		}
	}

	return "", errors.New("provider output is not valid JSON")
}

func stripMarkdownFence(raw string) (string, bool) {
	if !strings.HasPrefix(raw, "```") {
		return "", false
	}

	trimmed := strings.TrimPrefix(raw, "```")
	if newline := strings.Index(trimmed, "\n"); newline >= 0 {
		trimmed = trimmed[newline+1:]
	}

	if end := strings.LastIndex(trimmed, "```"); end >= 0 {
		return strings.TrimSpace(trimmed[:end]), true
	}

	return "", false
}

func looksLikeTrainingObject(decoded map[string]any) bool {
	_, hasTitle := decoded["title"]
	_, hasSteps := decoded["steps"]
	return hasTitle || hasSteps
}

func readOptionalString(value any) string {
	switch typed := value.(type) {
	case string:
		return strings.TrimSpace(typed)
	default:
		return ""
	}
}

func readPositiveInt(value any) (int, error) {
	switch typed := value.(type) {
	case float64:
		if typed <= 0 || typed != float64(int(typed)) {
			return 0, errors.New("must be a positive integer")
		}
		return int(typed), nil
	case string:
		parsed := strings.TrimSpace(typed)
		if parsed == "" {
			return 0, errors.New("must be a positive integer")
		}
		result, err := strconv.Atoi(parsed)
		if err != nil || result <= 0 {
			return 0, errors.New("must be a positive integer")
		}
		return result, nil
	default:
		return 0, errors.New("must be a positive integer")
	}
}

func normalizeStepType(raw string) (string, error) {
	switch canonicalStepTypeKey(raw) {
	case "warmup", "warmupwalk":
		return "warmup", nil
	case "run", "jog", "easyrun", "lightjog":
		return "run", nil
	case "walk", "recoverywalk":
		return "walk", nil
	case "cooldown", "cooldownwalk":
		return "cooldown", nil
	case "rest", "pause", "recover", "recovery":
		return "rest", nil
	default:
		return "", fmt.Errorf("uses unsupported value %q", raw)
	}
}

func canonicalStepTypeKey(raw string) string {
	replacer := strings.NewReplacer("_", "", "-", "", " ", "")
	return replacer.Replace(strings.ToLower(strings.TrimSpace(raw)))
}

func ensureUniqueStepID(raw string, index int, usedIDs map[string]struct{}) string {
	candidate := strings.TrimSpace(raw)
	if candidate == "" {
		candidate = fmt.Sprintf("step-%d", index+1)
	}

	if _, exists := usedIDs[candidate]; !exists {
		usedIDs[candidate] = struct{}{}
		return candidate
	}

	for suffix := index + 1; ; suffix++ {
		generated := fmt.Sprintf("step-%d", suffix)
		if _, exists := usedIDs[generated]; exists {
			continue
		}
		usedIDs[generated] = struct{}{}
		return generated
	}
}

func defaultVoicePrompt(stepType string, durationSec int, locale string) string {
	switch provider.EffectiveLocale(locale) {
	case provider.SupportedLocaleEnglish:
		duration := formatDurationEnglish(durationSec)
		switch stepType {
		case "warmup":
			return fmt.Sprintf("Warm up with brisk walking for %s.", duration)
		case "run":
			return fmt.Sprintf("Run easily for %s.", duration)
		case "walk":
			return fmt.Sprintf("Recover with walking for %s.", duration)
		case "cooldown":
			return fmt.Sprintf("Cool down with easy walking for %s.", duration)
		default:
			return fmt.Sprintf("Rest and recover for %s.", duration)
		}
	default:
		duration := formatDurationRussian(durationSec)
		switch stepType {
		case "warmup":
			return fmt.Sprintf("%s разминки быстрым шагом.", duration)
		case "run":
			return fmt.Sprintf("%s легкого бега.", duration)
		case "walk":
			return fmt.Sprintf("%s восстановления шагом.", duration)
		case "cooldown":
			return fmt.Sprintf("%s заминки спокойным шагом.", duration)
		default:
			return fmt.Sprintf("%s отдыха и восстановления.", duration)
		}
	}
}

func formatDurationRussian(durationSec int) string {
	minutes := durationSec / 60
	seconds := durationSec % 60

	parts := make([]string, 0, 2)
	if minutes > 0 {
		parts = append(parts, fmt.Sprintf("%d %s", minutes, russianPlural(minutes, "минута", "минуты", "минут")))
	}
	if seconds > 0 || len(parts) == 0 {
		parts = append(parts, fmt.Sprintf("%d %s", seconds, russianPlural(seconds, "секунда", "секунды", "секунд")))
	}

	if len(parts) == 1 {
		return capitalizeFirst(parts[0])
	}

	return capitalizeFirst(strings.Join(parts, " "))
}

func formatDurationEnglish(durationSec int) string {
	minutes := durationSec / 60
	seconds := durationSec % 60

	parts := make([]string, 0, 2)
	if minutes > 0 {
		parts = append(parts, fmt.Sprintf("%d %s", minutes, englishPlural(minutes, "minute", "minutes")))
	}
	if seconds > 0 || len(parts) == 0 {
		parts = append(parts, fmt.Sprintf("%d %s", seconds, englishPlural(seconds, "second", "seconds")))
	}

	if len(parts) == 1 {
		return capitalizeFirst(parts[0])
	}

	return capitalizeFirst(strings.Join(parts, " "))
}

func russianPlural(value int, one string, few string, many string) string {
	mod100 := value % 100
	if mod100 >= 11 && mod100 <= 14 {
		return many
	}

	switch value % 10 {
	case 1:
		return one
	case 2, 3, 4:
		return few
	default:
		return many
	}
}

func englishPlural(value int, singular string, plural string) string {
	if value == 1 {
		return singular
	}

	return plural
}

func capitalizeFirst(value string) string {
	if value == "" {
		return ""
	}

	r, size := utf8.DecodeRuneInString(value)
	if r == utf8.RuneError && size == 0 {
		return ""
	}

	return string(unicode.ToUpper(r)) + value[size:]
}
