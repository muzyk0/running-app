package prompt

import (
	"strings"
	"testing"

	"github.com/muzyk0/running-app/backend/internal/provider"
)

func TestBuilderBuildIncludesPromptRequirements(t *testing.T) {
	request := provider.GenerateRequest{
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
				{Label: "Поверхность", Value: "Стадион"},
			},
		},
		Locale:   "ru-RU",
		UserNote: "Без интенсивных ускорений",
	}

	completion := NewBuilder().Build(request)

	requiredSystemParts := []string{
		"Верни только JSON-объект",
		"schema_version \"mvp.v1\"",
		provider.DefaultDisclaimer,
		"Разрешенные типы шагов: warmup, run, walk, cooldown, rest.",
	}
	for _, part := range requiredSystemParts {
		if !strings.Contains(completion.SystemPrompt, part) {
			t.Fatalf("SystemPrompt %q does not contain %q", completion.SystemPrompt, part)
		}
	}

	requiredUserParts := []string{
		"Профиль:",
		"- Рост: 180 см",
		"- Вес: 77 кг",
		"- Пол: male",
		"- Возраст: 31",
		"- Тренировок в неделю: 4",
		"- Уровень подготовки: beginner",
		"- Ограничения и травмы: none",
		"- Цель тренировок: Build consistency",
		"Дополнительный контекст:",
		"- Поверхность: Стадион",
		"Пожелание пользователя: Без интенсивных ускорений",
		"Локаль ответа: ru-RU",
	}
	for _, part := range requiredUserParts {
		if !strings.Contains(completion.UserPrompt, part) {
			t.Fatalf("UserPrompt %q does not contain %q", completion.UserPrompt, part)
		}
	}
}

func TestBuilderBuildOmitsOptionalSectionsWhenEmpty(t *testing.T) {
	request := provider.GenerateRequest{
		Profile: provider.ProfileSnapshot{
			HeightCM:               170,
			WeightKG:               68,
			Sex:                    "female",
			Age:                    29,
			TrainingDaysPerWeek:    3,
			FitnessLevel:           "intermediate",
			InjuriesAndLimitations: "left knee sensitivity",
			TrainingGoal:           "Recover consistency",
		},
		Locale: "ru-RU",
	}

	completion := NewBuilder().Build(request)

	if strings.Contains(completion.UserPrompt, "Дополнительный контекст:") {
		t.Fatalf("UserPrompt unexpectedly contains optional context section: %q", completion.UserPrompt)
	}
	if strings.Contains(completion.UserPrompt, "Пожелание пользователя:") {
		t.Fatalf("UserPrompt unexpectedly contains optional note section: %q", completion.UserPrompt)
	}
	if !strings.Contains(completion.UserPrompt, "Локаль ответа: ru-RU") {
		t.Fatalf("UserPrompt %q does not contain locale", completion.UserPrompt)
	}
}
