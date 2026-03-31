package service

import (
	"context"
	"errors"
	"log/slog"

	"github.com/muzyk0/running-app/backend/internal/prompt"
	"github.com/muzyk0/running-app/backend/internal/provider"
	"github.com/muzyk0/running-app/backend/internal/schema"
)

var ErrProviderNotConfigured = errors.New("training provider is not configured")

type TrainingGenerator interface {
	GenerateTraining(ctx context.Context, request provider.GenerateRequest) (provider.TrainingEnvelope, error)
}

type PromptBuilder interface {
	Build(request provider.GenerateRequest) provider.CompletionRequest
}

type TrainingNormalizer interface {
	Normalize(rawOutput string, request provider.GenerateRequest) (provider.TrainingEnvelope, error)
}

type TrainingService struct {
	provider      provider.Generator
	promptBuilder PromptBuilder
	normalizer    TrainingNormalizer
	logger        *slog.Logger
}

func NewTrainingService(generator provider.Generator, promptBuilder PromptBuilder, normalizer TrainingNormalizer, logger *slog.Logger) *TrainingService {
	if logger == nil {
		logger = slog.Default()
	}
	if promptBuilder == nil {
		defaultBuilder := prompt.NewBuilder()
		promptBuilder = defaultBuilder
	}
	if normalizer == nil {
		defaultNormalizer := schema.NewNormalizer()
		normalizer = defaultNormalizer
	}

	return &TrainingService{
		provider:      generator,
		promptBuilder: promptBuilder,
		normalizer:    normalizer,
		logger:        logger,
	}
}

func (s *TrainingService) GenerateTraining(ctx context.Context, request provider.GenerateRequest) (provider.TrainingEnvelope, error) {
	if s.provider == nil {
		return provider.TrainingEnvelope{}, ErrProviderNotConfigured
	}

	s.logger.Info("generating training", "provider", s.provider.Name(), "locale", request.Locale)

	providerRequest := s.promptBuilder.Build(request)

	rawResponse, err := s.provider.Generate(ctx, providerRequest)
	if err != nil {
		s.logger.Error("training generation failed", "provider", s.provider.Name(), "error", err)
		return provider.TrainingEnvelope{}, err
	}

	result, err := s.normalizer.Normalize(rawResponse.RawOutput, request)
	if err != nil {
		s.logger.Error("training normalization failed", "provider", s.provider.Name(), "error", err)
		return provider.TrainingEnvelope{}, err
	}

	s.logger.Info("training generation succeeded", "provider", s.provider.Name(), "steps", len(result.Training.Steps))
	return result, nil
}
