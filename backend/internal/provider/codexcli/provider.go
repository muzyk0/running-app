package codexcli

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"strings"

	"github.com/muzyk0/running-app/backend/internal/provider"
)

const (
	defaultBinaryPath = "codex"
	defaultSandbox    = "read-only"
)

type Config struct {
	BinaryPath string
	WorkingDir string
	Model      string
	Profile    string
	Sandbox    string
}

type Generator struct {
	binaryPath string
	workingDir string
	model      string
	profile    string
	sandbox    string
}

func New(config Config) *Generator {
	binaryPath := strings.TrimSpace(config.BinaryPath)
	if binaryPath == "" {
		binaryPath = defaultBinaryPath
	}

	sandbox := strings.TrimSpace(config.Sandbox)
	if sandbox == "" {
		sandbox = defaultSandbox
	}

	return &Generator{
		binaryPath: binaryPath,
		workingDir: strings.TrimSpace(config.WorkingDir),
		model:      strings.TrimSpace(config.Model),
		profile:    strings.TrimSpace(config.Profile),
		sandbox:    sandbox,
	}
}

func (g *Generator) Name() string {
	return "codex"
}

func (g *Generator) Generate(ctx context.Context, request provider.CompletionRequest) (provider.CompletionResponse, error) {
	promptText := combinePrompt(request)
	if promptText == "" {
		return provider.CompletionResponse{}, errors.New("prompt is empty")
	}

	outputFile, err := os.CreateTemp("", "running-app-codex-output-*.txt")
	if err != nil {
		return provider.CompletionResponse{}, fmt.Errorf("create codex output file: %w", err)
	}

	outputPath := outputFile.Name()
	if closeErr := outputFile.Close(); closeErr != nil {
		_ = os.Remove(outputPath)
		return provider.CompletionResponse{}, fmt.Errorf("close codex output file: %w", closeErr)
	}
	defer os.Remove(outputPath)

	args := g.buildArgs(outputPath)
	cmd := exec.CommandContext(ctx, g.binaryPath, args...)
	cmd.Stdin = strings.NewReader(promptText)

	var stdout bytes.Buffer
	var stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		if ctx.Err() != nil {
			return provider.CompletionResponse{}, ctx.Err()
		}

		message := strings.TrimSpace(stderr.String())
		if message == "" {
			message = strings.TrimSpace(stdout.String())
		}
		if message != "" {
			return provider.CompletionResponse{}, fmt.Errorf("run codex exec: %w: %s", err, message)
		}
		return provider.CompletionResponse{}, fmt.Errorf("run codex exec: %w", err)
	}

	rawOutput, err := os.ReadFile(outputPath)
	if err != nil {
		return provider.CompletionResponse{}, fmt.Errorf("read codex output: %w", err)
	}

	text := strings.TrimSpace(string(rawOutput))
	if text == "" {
		text = strings.TrimSpace(stdout.String())
	}
	if text == "" {
		return provider.CompletionResponse{}, errors.New("codex returned empty output")
	}

	return provider.CompletionResponse{RawOutput: text}, nil
}

func (g *Generator) buildArgs(outputPath string) []string {
	args := []string{
		"exec",
		"--skip-git-repo-check",
		"--ephemeral",
		"--sandbox", g.sandbox,
		"--color", "never",
		"--output-last-message", outputPath,
	}

	if g.model != "" {
		args = append(args, "--model", g.model)
	}
	if g.profile != "" {
		args = append(args, "--profile", g.profile)
	}
	if g.workingDir != "" {
		args = append(args, "--cd", g.workingDir)
	}

	args = append(args, "-")
	return args
}

func combinePrompt(request provider.CompletionRequest) string {
	var prompt strings.Builder

	if systemPrompt := strings.TrimSpace(request.SystemPrompt); systemPrompt != "" {
		prompt.WriteString("System instructions:\n")
		prompt.WriteString(systemPrompt)
		prompt.WriteString("\n\n")
	}

	if userPrompt := strings.TrimSpace(request.UserPrompt); userPrompt != "" {
		prompt.WriteString("User request:\n")
		prompt.WriteString(userPrompt)
	}

	return strings.TrimSpace(prompt.String())
}
