package prompt

import (
	"fmt"
	"strings"

	"github.com/muzyk0/running-app/backend/internal/provider"
)

type Builder struct {
	disclaimer string
}

func NewBuilder() Builder {
	return Builder{
		disclaimer: provider.DefaultDisclaimer,
	}
}

func (b Builder) Build(request provider.GenerateRequest) provider.CompletionRequest {
	var userPrompt strings.Builder

	userPrompt.WriteString("Собери одну беговую тренировку по профилю пользователя.\n")
	userPrompt.WriteString("Профиль:\n")
	userPrompt.WriteString(fmt.Sprintf("- Рост: %d см\n", request.Profile.HeightCM))
	userPrompt.WriteString(fmt.Sprintf("- Вес: %d кг\n", request.Profile.WeightKG))
	userPrompt.WriteString(fmt.Sprintf("- Пол: %s\n", request.Profile.Sex))
	userPrompt.WriteString(fmt.Sprintf("- Возраст: %d\n", request.Profile.Age))
	userPrompt.WriteString(fmt.Sprintf("- Тренировок в неделю: %d\n", request.Profile.TrainingDaysPerWeek))
	userPrompt.WriteString(fmt.Sprintf("- Уровень подготовки: %s\n", request.Profile.FitnessLevel))
	userPrompt.WriteString(fmt.Sprintf("- Ограничения и травмы: %s\n", request.Profile.InjuriesAndLimitations))
	userPrompt.WriteString(fmt.Sprintf("- Цель тренировок: %s\n", request.Profile.TrainingGoal))

	if len(request.Profile.AdditionalPromptFields) > 0 {
		userPrompt.WriteString("Дополнительный контекст:\n")
		for _, field := range request.Profile.AdditionalPromptFields {
			userPrompt.WriteString(fmt.Sprintf("- %s: %s\n", field.Label, field.Value))
		}
	}

	if note := strings.TrimSpace(request.UserNote); note != "" {
		userPrompt.WriteString(fmt.Sprintf("Пожелание пользователя: %s\n", note))
	}

	userPrompt.WriteString(fmt.Sprintf("Локаль ответа: %s\n", request.Locale))

	return provider.CompletionRequest{
		SystemPrompt: strings.Join([]string{
			"Ты составляешь одну безопасную MVP-тренировку для бегового приложения.",
			"Верни только JSON-объект без markdown, без пояснений и без префиксов.",
			fmt.Sprintf("Используй schema_version %q.", provider.SchemaVersionMVPv1),
			"Объект должен иметь вид {\"schema_version\":\"mvp.v1\",\"training\":{...}}.",
			"Все текстовые поля и voice_prompt должны быть на русском языке.",
			fmt.Sprintf("Поле training.disclaimer должно быть равно %q.", b.disclaimer),
			"Разрешенные типы шагов: warmup, run, walk, cooldown, rest.",
			"Шаги должны иметь уникальные id, положительный duration_sec и непустой voice_prompt.",
			"estimated_duration_sec должен быть равен сумме duration_sec по всем шагам.",
			"Нужна одна самостоятельная тренировка, без недельного плана и без дополнительных секций.",
		}, "\n"),
		UserPrompt: strings.TrimSpace(userPrompt.String()),
	}
}
