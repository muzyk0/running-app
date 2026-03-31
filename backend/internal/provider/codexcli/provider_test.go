package codexcli

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/muzyk0/running-app/backend/internal/provider"
)

func TestNewAppliesDefaultsAndTrimmedConfig(t *testing.T) {
	generator := New(Config{
		BinaryPath: " ",
		WorkingDir: " /tmp/workspace ",
		Model:      " gpt-5.4 ",
		Profile:    " backend ",
		Sandbox:    " ",
	})

	if generator.binaryPath != defaultBinaryPath {
		t.Fatalf("binaryPath = %q, want %q", generator.binaryPath, defaultBinaryPath)
	}
	if generator.workingDir != "/tmp/workspace" {
		t.Fatalf("workingDir = %q, want %q", generator.workingDir, "/tmp/workspace")
	}
	if generator.model != "gpt-5.4" {
		t.Fatalf("model = %q, want %q", generator.model, "gpt-5.4")
	}
	if generator.profile != "backend" {
		t.Fatalf("profile = %q, want %q", generator.profile, "backend")
	}
	if generator.sandbox != defaultSandbox {
		t.Fatalf("sandbox = %q, want %q", generator.sandbox, defaultSandbox)
	}
	if generator.Name() != "codex" {
		t.Fatalf("Name() = %q, want %q", generator.Name(), "codex")
	}
}

func TestBuildArgsIncludesOptionalFlags(t *testing.T) {
	generator := New(Config{
		BinaryPath: "/usr/local/bin/codex",
		WorkingDir: "/srv/running-app",
		Model:      "gpt-5.4",
		Profile:    "backend",
		Sandbox:    "workspace-write",
	})

	args := generator.buildArgs("/tmp/output.txt")
	joined := strings.Join(args, " ")

	requiredParts := []string{
		"exec",
		"--skip-git-repo-check",
		"--ephemeral",
		"--sandbox workspace-write",
		"--color never",
		"--output-last-message /tmp/output.txt",
		"--model gpt-5.4",
		"--profile backend",
		"--cd /srv/running-app",
	}
	for _, part := range requiredParts {
		if !strings.Contains(joined, part) {
			t.Fatalf("args %q do not contain %q", joined, part)
		}
	}
	if args[len(args)-1] != "-" {
		t.Fatalf("last arg = %q, want %q", args[len(args)-1], "-")
	}
}

func TestCombinePromptFormatsSystemAndUserSections(t *testing.T) {
	prompt := combinePrompt(provider.CompletionRequest{
		SystemPrompt: "  System block  ",
		UserPrompt:   "  User block  ",
	})

	if !strings.Contains(prompt, "System instructions:\nSystem block") {
		t.Fatalf("combinePrompt() = %q, want system section", prompt)
	}
	if !strings.Contains(prompt, "User request:\nUser block") {
		t.Fatalf("combinePrompt() = %q, want user section", prompt)
	}
}

func TestGenerateReturnsFileOutput(t *testing.T) {
	scriptPath := writeExecutableScript(t, `#!/bin/sh
set -eu
output=""
while [ "$#" -gt 0 ]; do
  if [ "$1" = "--output-last-message" ]; then
    output="$2"
    shift 2
    continue
  fi
  shift
done
cat >/dev/null
printf '%s\n' '{"training":{"title":"Тест","steps":[{"id":"step-1","type":"run","duration_sec":60,"voice_prompt":"Бег."}]}}' > "$output"
`)

	generator := New(Config{BinaryPath: scriptPath, Sandbox: "workspace-write"})
	response, err := generator.Generate(context.Background(), provider.CompletionRequest{
		SystemPrompt: "Верни JSON",
		UserPrompt:   "Собери тренировку",
	})
	if err != nil {
		t.Fatalf("Generate() error = %v", err)
	}
	if !strings.Contains(response.RawOutput, "\"title\":\"Тест\"") {
		t.Fatalf("RawOutput = %q, want generated payload", response.RawOutput)
	}
}

func TestGenerateReturnsCommandFailureMessage(t *testing.T) {
	scriptPath := writeExecutableScript(t, `#!/bin/sh
set -eu
echo "codex failed hard" >&2
exit 7
`)

	generator := New(Config{BinaryPath: scriptPath})
	_, err := generator.Generate(context.Background(), provider.CompletionRequest{
		SystemPrompt: "Верни JSON",
		UserPrompt:   "Собери тренировку",
	})
	if err == nil {
		t.Fatal("Generate() error = nil, want error")
	}
	if !strings.Contains(err.Error(), "codex failed hard") {
		t.Fatalf("Generate() error = %v, want stderr message", err)
	}
}

func TestGenerateReturnsContextCancellation(t *testing.T) {
	scriptPath := writeExecutableScript(t, `#!/bin/sh
set -eu
sleep 1
`)

	generator := New(Config{BinaryPath: scriptPath})
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Millisecond)
	defer cancel()

	_, err := generator.Generate(ctx, provider.CompletionRequest{
		SystemPrompt: "Верни JSON",
		UserPrompt:   "Собери тренировку",
	})
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("Generate() error = %v, want deadline exceeded", err)
	}
}

func TestGenerateRejectsEmptyPrompt(t *testing.T) {
	generator := New(Config{BinaryPath: "/bin/sh"})

	_, err := generator.Generate(context.Background(), provider.CompletionRequest{})
	if err == nil {
		t.Fatal("Generate() error = nil, want error")
	}
	if !strings.Contains(err.Error(), "prompt is empty") {
		t.Fatalf("Generate() error = %v, want empty prompt error", err)
	}
}

func writeExecutableScript(t *testing.T, body string) string {
	t.Helper()

	path := filepath.Join(t.TempDir(), "fake-codex.sh")
	if err := os.WriteFile(path, []byte(body), 0o755); err != nil {
		t.Fatalf("write script: %v", err)
	}
	return path
}
