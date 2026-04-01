package provider

import (
	"context"
	"errors"
	"strings"
	"testing"
)

func TestStaticGeneratorName(t *testing.T) {
	generator := NewStaticGenerator()

	if got := generator.Name(); got != "static" {
		t.Fatalf("Name() = %q, want %q", got, "static")
	}
}

func TestStaticGeneratorGenerateDelegatesToStream(t *testing.T) {
	generator := NewStaticGenerator()

	response, err := generator.Generate(context.Background(), CompletionRequest{Locale: SupportedLocaleRussian})
	if err != nil {
		t.Fatalf("Generate() error = %v", err)
	}
	if !strings.Contains(response.RawOutput, "\"schema_version\": \"mvp.v1\"") {
		t.Fatalf("RawOutput = %q, want static training payload", response.RawOutput)
	}
}

func TestStaticGeneratorGenerateStreamReportsDeterministicProgress(t *testing.T) {
	generator := NewStaticGenerator()

	var progress []ProgressChunk
	response, err := generator.GenerateStream(context.Background(), CompletionRequest{Locale: SupportedLocaleRussian}, func(chunk ProgressChunk) {
		progress = append(progress, chunk)
	})
	if err != nil {
		t.Fatalf("GenerateStream() error = %v", err)
	}

	if len(progress) != len(staticGeneratorProgress) {
		t.Fatalf("len(progress) = %d, want %d", len(progress), len(staticGeneratorProgress))
	}
	for index, chunk := range staticGeneratorProgress {
		if progress[index].Message != chunk.Message {
			t.Fatalf("progress[%d].Message = %q, want %q", index, progress[index].Message, chunk.Message)
		}
	}
	if !strings.Contains(response.RawOutput, "\"title\": \"Базовая интервальная тренировка\"") {
		t.Fatalf("RawOutput = %q, want static training payload", response.RawOutput)
	}
}

func TestStaticGeneratorGenerateStreamUsesEnglishPayload(t *testing.T) {
	generator := NewStaticGenerator()

	response, err := generator.GenerateStream(context.Background(), CompletionRequest{Locale: SupportedLocaleEnglish}, nil)
	if err != nil {
		t.Fatalf("GenerateStream() error = %v", err)
	}

	requiredParts := []string{
		"\"title\": \"Base interval workout\"",
		DefaultDisclaimerEnglish,
		"\"voice_prompt\": \"Warm up with brisk walking for 5 minutes.\"",
	}
	for _, part := range requiredParts {
		if !strings.Contains(response.RawOutput, part) {
			t.Fatalf("RawOutput = %q, want english static payload containing %q", response.RawOutput, part)
		}
	}
}

func TestStaticGeneratorGenerateStreamReturnsContextErrorBeforeWorkStarts(t *testing.T) {
	generator := NewStaticGenerator()
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := generator.GenerateStream(ctx, CompletionRequest{}, nil)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("GenerateStream() error = %v, want %v", err, context.Canceled)
	}
}

func TestStaticGeneratorGenerateStreamReturnsContextErrorMidProgress(t *testing.T) {
	generator := NewStaticGenerator()
	ctx, cancel := context.WithCancel(context.Background())

	var progress []ProgressChunk
	_, err := generator.GenerateStream(ctx, CompletionRequest{}, func(chunk ProgressChunk) {
		progress = append(progress, chunk)
		cancel()
	})
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("GenerateStream() error = %v, want %v", err, context.Canceled)
	}
	if len(progress) != 1 {
		t.Fatalf("len(progress) = %d, want %d", len(progress), 1)
	}
}

func TestNormalizeSupportedLocaleAndDisclaimer(t *testing.T) {
	testCases := []struct {
		name           string
		input          string
		wantLocale     string
		wantOK         bool
		wantDisclaimer string
	}{
		{
			name:           "russian",
			input:          SupportedLocaleRussian,
			wantLocale:     SupportedLocaleRussian,
			wantOK:         true,
			wantDisclaimer: DefaultDisclaimer,
		},
		{
			name:           "english",
			input:          SupportedLocaleEnglish,
			wantLocale:     SupportedLocaleEnglish,
			wantOK:         true,
			wantDisclaimer: DefaultDisclaimerEnglish,
		},
		{
			name:           "unsupported",
			input:          "fr-FR",
			wantLocale:     "",
			wantOK:         false,
			wantDisclaimer: DefaultDisclaimer,
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			locale, ok := NormalizeSupportedLocale(testCase.input)
			if locale != testCase.wantLocale || ok != testCase.wantOK {
				t.Fatalf("NormalizeSupportedLocale(%q) = (%q, %t), want (%q, %t)", testCase.input, locale, ok, testCase.wantLocale, testCase.wantOK)
			}
			if got := DisclaimerForLocale(testCase.input); got != testCase.wantDisclaimer {
				t.Fatalf("DisclaimerForLocale(%q) = %q, want %q", testCase.input, got, testCase.wantDisclaimer)
			}
		})
	}
}
