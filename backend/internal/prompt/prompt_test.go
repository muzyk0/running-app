package prompt

import (
	"strings"
	"testing"

	"github.com/muzyk0/running-app/backend/internal/provider"
)

func TestBuilderBuildIncludesPromptRequirements(t *testing.T) {
	testCases := []struct {
		name                string
		request             provider.GenerateRequest
		requiredSystemParts []string
		requiredUserParts   []string
	}{
		{
			name: "russian",
			request: provider.GenerateRequest{
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
				Locale:   provider.SupportedLocaleRussian,
				UserNote: "Без интенсивных ускорений",
			},
			requiredSystemParts: []string{
				"Верни только JSON-объект",
				"schema_version \"mvp.v1\"",
				provider.DisclaimerForLocale(provider.SupportedLocaleRussian),
				"Все текстовые поля и voice_prompt должны быть на русском языке.",
			},
			requiredUserParts: []string{
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
			},
		},
		{
			name: "english",
			request: provider.GenerateRequest{
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
						{Label: "Surface", Value: "Track"},
					},
				},
				Locale:   provider.SupportedLocaleEnglish,
				UserNote: "Avoid hard accelerations",
			},
			requiredSystemParts: []string{
				"Return only a JSON object",
				"schema_version \"mvp.v1\"",
				provider.DisclaimerForLocale(provider.SupportedLocaleEnglish),
				"All user-facing text fields and voice_prompt must be in English.",
			},
			requiredUserParts: []string{
				"Profile:",
				"- Height: 180 cm",
				"- Weight: 77 kg",
				"- Sex: male",
				"- Age: 31",
				"- Training days per week: 4",
				"- Fitness level: beginner",
				"- Injuries and limitations: none",
				"- Training goal: Build consistency",
				"Additional context:",
				"- Surface: Track",
				"User note: Avoid hard accelerations",
				"Response locale: en-US",
			},
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			completion := NewBuilder().Build(testCase.request)

			if completion.Locale != testCase.request.Locale {
				t.Fatalf("Locale = %q, want %q", completion.Locale, testCase.request.Locale)
			}
			for _, part := range testCase.requiredSystemParts {
				if !strings.Contains(completion.SystemPrompt, part) {
					t.Fatalf("SystemPrompt %q does not contain %q", completion.SystemPrompt, part)
				}
			}
			for _, part := range testCase.requiredUserParts {
				if !strings.Contains(completion.UserPrompt, part) {
					t.Fatalf("UserPrompt %q does not contain %q", completion.UserPrompt, part)
				}
			}
		})
	}
}

func TestBuilderBuildOmitsOptionalSectionsWhenEmpty(t *testing.T) {
	testCases := []struct {
		name                   string
		locale                 string
		unexpectedContextTitle string
		unexpectedNoteTitle    string
		expectedLocaleLine     string
	}{
		{
			name:                   "russian",
			locale:                 provider.SupportedLocaleRussian,
			unexpectedContextTitle: "Дополнительный контекст:",
			unexpectedNoteTitle:    "Пожелание пользователя:",
			expectedLocaleLine:     "Локаль ответа: ru-RU",
		},
		{
			name:                   "english",
			locale:                 provider.SupportedLocaleEnglish,
			unexpectedContextTitle: "Additional context:",
			unexpectedNoteTitle:    "User note:",
			expectedLocaleLine:     "Response locale: en-US",
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
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
				Locale: testCase.locale,
			}

			completion := NewBuilder().Build(request)

			if strings.Contains(completion.UserPrompt, testCase.unexpectedContextTitle) {
				t.Fatalf("UserPrompt unexpectedly contains optional context section: %q", completion.UserPrompt)
			}
			if strings.Contains(completion.UserPrompt, testCase.unexpectedNoteTitle) {
				t.Fatalf("UserPrompt unexpectedly contains optional note section: %q", completion.UserPrompt)
			}
			if !strings.Contains(completion.UserPrompt, testCase.expectedLocaleLine) {
				t.Fatalf("UserPrompt %q does not contain locale", completion.UserPrompt)
			}
		})
	}
}
