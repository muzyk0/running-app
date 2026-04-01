package prompt

import (
	"fmt"
	"strings"

	"github.com/muzyk0/running-app/backend/internal/provider"
)

type Builder struct {
}

func NewBuilder() Builder {
	return Builder{}
}

func (Builder) Build(request provider.GenerateRequest) provider.CompletionRequest {
	locale := provider.EffectiveLocale(request.Locale)
	copy := promptCopyForLocale(locale)
	disclaimer := provider.DisclaimerForLocale(locale)
	var userPrompt strings.Builder

	userPrompt.WriteString(copy.UserIntro + "\n")
	userPrompt.WriteString(copy.ProfileHeading + "\n")
	userPrompt.WriteString(fmt.Sprintf("- %s: %d %s\n", copy.HeightLabel, request.Profile.HeightCM, copy.HeightUnit))
	userPrompt.WriteString(fmt.Sprintf("- %s: %d %s\n", copy.WeightLabel, request.Profile.WeightKG, copy.WeightUnit))
	userPrompt.WriteString(fmt.Sprintf("- %s: %s\n", copy.SexLabel, request.Profile.Sex))
	userPrompt.WriteString(fmt.Sprintf("- %s: %d\n", copy.AgeLabel, request.Profile.Age))
	userPrompt.WriteString(fmt.Sprintf("- %s: %d\n", copy.TrainingDaysLabel, request.Profile.TrainingDaysPerWeek))
	userPrompt.WriteString(fmt.Sprintf("- %s: %s\n", copy.FitnessLevelLabel, request.Profile.FitnessLevel))
	userPrompt.WriteString(fmt.Sprintf("- %s: %s\n", copy.InjuriesLabel, request.Profile.InjuriesAndLimitations))
	userPrompt.WriteString(fmt.Sprintf("- %s: %s\n", copy.GoalLabel, request.Profile.TrainingGoal))

	if len(request.Profile.AdditionalPromptFields) > 0 {
		userPrompt.WriteString(copy.AdditionalContextHeading + "\n")
		for _, field := range request.Profile.AdditionalPromptFields {
			userPrompt.WriteString(fmt.Sprintf("- %s: %s\n", field.Label, field.Value))
		}
	}

	if note := strings.TrimSpace(request.UserNote); note != "" {
		userPrompt.WriteString(fmt.Sprintf("%s: %s\n", copy.UserNoteLabel, note))
	}

	userPrompt.WriteString(fmt.Sprintf("%s: %s\n", copy.ResponseLocaleLabel, locale))

	return provider.CompletionRequest{
		Locale: locale,
		SystemPrompt: strings.Join([]string{
			copy.SystemIntro,
			copy.JSONOnlyInstruction,
			fmt.Sprintf(copy.SchemaVersionInstruction, provider.SchemaVersionMVPv1),
			copy.ObjectShapeInstruction,
			copy.LanguageInstruction,
			fmt.Sprintf(copy.DisclaimerInstruction, disclaimer),
			copy.StepTypesInstruction,
			copy.StepRequirementsInstruction,
			copy.DurationInstruction,
			copy.StandaloneInstruction,
		}, "\n"),
		UserPrompt: strings.TrimSpace(userPrompt.String()),
	}
}

type promptCopy struct {
	UserIntro                   string
	ProfileHeading              string
	HeightLabel                 string
	HeightUnit                  string
	WeightLabel                 string
	WeightUnit                  string
	SexLabel                    string
	AgeLabel                    string
	TrainingDaysLabel           string
	FitnessLevelLabel           string
	InjuriesLabel               string
	GoalLabel                   string
	AdditionalContextHeading    string
	UserNoteLabel               string
	ResponseLocaleLabel         string
	SystemIntro                 string
	JSONOnlyInstruction         string
	SchemaVersionInstruction    string
	ObjectShapeInstruction      string
	LanguageInstruction         string
	DisclaimerInstruction       string
	StepTypesInstruction        string
	StepRequirementsInstruction string
	DurationInstruction         string
	StandaloneInstruction       string
}

func promptCopyForLocale(locale string) promptCopy {
	switch provider.EffectiveLocale(locale) {
	case provider.SupportedLocaleEnglish:
		return promptCopy{
			UserIntro:                   "Build one running workout from the user's profile.",
			ProfileHeading:              "Profile:",
			HeightLabel:                 "Height",
			HeightUnit:                  "cm",
			WeightLabel:                 "Weight",
			WeightUnit:                  "kg",
			SexLabel:                    "Sex",
			AgeLabel:                    "Age",
			TrainingDaysLabel:           "Training days per week",
			FitnessLevelLabel:           "Fitness level",
			InjuriesLabel:               "Injuries and limitations",
			GoalLabel:                   "Training goal",
			AdditionalContextHeading:    "Additional context:",
			UserNoteLabel:               "User note",
			ResponseLocaleLabel:         "Response locale",
			SystemIntro:                 "You are creating one safe MVP workout for a running app.",
			JSONOnlyInstruction:         "Return only a JSON object with no markdown, explanations, or prefixes.",
			SchemaVersionInstruction:    "Use schema_version %q.",
			ObjectShapeInstruction:      "The object must look like {\"schema_version\":\"mvp.v1\",\"training\":{...}}.",
			LanguageInstruction:         "All user-facing text fields and voice_prompt must be in English.",
			DisclaimerInstruction:       "training.disclaimer must equal %q.",
			StepTypesInstruction:        "Allowed step types: warmup, run, walk, cooldown, rest.",
			StepRequirementsInstruction: "Steps must have unique id values, a positive duration_sec, and a non-empty voice_prompt.",
			DurationInstruction:         "estimated_duration_sec must equal the sum of duration_sec across all steps.",
			StandaloneInstruction:       "Return one standalone workout only, with no weekly plan or extra sections.",
		}
	default:
		return promptCopy{
			UserIntro:                   "Собери одну беговую тренировку по профилю пользователя.",
			ProfileHeading:              "Профиль:",
			HeightLabel:                 "Рост",
			HeightUnit:                  "см",
			WeightLabel:                 "Вес",
			WeightUnit:                  "кг",
			SexLabel:                    "Пол",
			AgeLabel:                    "Возраст",
			TrainingDaysLabel:           "Тренировок в неделю",
			FitnessLevelLabel:           "Уровень подготовки",
			InjuriesLabel:               "Ограничения и травмы",
			GoalLabel:                   "Цель тренировок",
			AdditionalContextHeading:    "Дополнительный контекст:",
			UserNoteLabel:               "Пожелание пользователя",
			ResponseLocaleLabel:         "Локаль ответа",
			SystemIntro:                 "Ты составляешь одну безопасную MVP-тренировку для бегового приложения.",
			JSONOnlyInstruction:         "Верни только JSON-объект без markdown, без пояснений и без префиксов.",
			SchemaVersionInstruction:    "Используй schema_version %q.",
			ObjectShapeInstruction:      "Объект должен иметь вид {\"schema_version\":\"mvp.v1\",\"training\":{...}}.",
			LanguageInstruction:         "Все текстовые поля и voice_prompt должны быть на русском языке.",
			DisclaimerInstruction:       "Поле training.disclaimer должно быть равно %q.",
			StepTypesInstruction:        "Разрешенные типы шагов: warmup, run, walk, cooldown, rest.",
			StepRequirementsInstruction: "Шаги должны иметь уникальные id, положительный duration_sec и непустой voice_prompt.",
			DurationInstruction:         "estimated_duration_sec должен быть равен сумме duration_sec по всем шагам.",
			StandaloneInstruction:       "Нужна одна самостоятельная тренировка, без недельного плана и без дополнительных секций.",
		}
	}
}
