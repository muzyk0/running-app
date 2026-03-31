package provider

import (
	"context"
	"strings"
	"testing"
)

func TestStaticGeneratorGenerateStreamReportsDeterministicProgress(t *testing.T) {
	generator := NewStaticGenerator()

	var progress []ProgressChunk
	response, err := generator.GenerateStream(context.Background(), CompletionRequest{}, func(chunk ProgressChunk) {
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
