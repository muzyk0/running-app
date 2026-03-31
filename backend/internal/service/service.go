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

type TrainingService struct {
	provider      provider.Generator
	promptBuilder prompt.Builder
	normalizer    schema.Normalizer
	logger        *slog.Logger
}

func NewTrainingService(generator provider.Generator, logger *slog.Logger) *TrainingService {
	if logger == nil {
		logger = slog.Default()
	}

	return &TrainingService{
		provider:      generator,
		promptBuilder: prompt.NewBuilder(),
		normalizer:    schema.NewNormalizer(),
		logger:        logger,
	}
}

func (s *TrainingService) GenerateTraining(ctx context.Context, request provider.GenerateRequest) (provider.TrainingEnvelope, error) {
	return s.GenerateTrainingStream(ctx, request, nil)
}

func (s *TrainingService) GenerateTrainingStream(
	ctx context.Context,
	request provider.GenerateRequest,
	report provider.ProgressReporter,
) (provider.TrainingEnvelope, error) {
	if s.provider == nil {
		return provider.TrainingEnvelope{}, ErrProviderNotConfigured
	}

	s.logger.Info("generating training", "provider", s.provider.Name(), "locale", request.Locale)

	providerRequest := s.promptBuilder.Build(request)

	rawResponse, err := s.generateProviderResponse(ctx, providerRequest, report)
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

func (s *TrainingService) generateProviderResponse(
	ctx context.Context,
	request provider.CompletionRequest,
	report provider.ProgressReporter,
) (provider.CompletionResponse, error) {
	if streamer, ok := s.provider.(provider.StreamGenerator); ok {
		return streamer.GenerateStream(ctx, request, report)
	}

	return s.provider.Generate(ctx, request)
}
