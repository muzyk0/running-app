package service

import (
	"context"
	"errors"
	"log/slog"

	"github.com/muzyk0/running-app/backend/internal/provider"
)

var ErrProviderNotConfigured = errors.New("training provider is not configured")

type TrainingGenerator interface {
	GenerateTraining(ctx context.Context, request provider.GenerateRequest) (provider.TrainingEnvelope, error)
}

type TrainingService struct {
	provider provider.Generator
	logger   *slog.Logger
}

func NewTrainingService(generator provider.Generator, logger *slog.Logger) *TrainingService {
	if logger == nil {
		logger = slog.Default()
	}

	return &TrainingService{
		provider: generator,
		logger:   logger,
	}
}

func (s *TrainingService) GenerateTraining(ctx context.Context, request provider.GenerateRequest) (provider.TrainingEnvelope, error) {
	if s.provider == nil {
		return provider.TrainingEnvelope{}, ErrProviderNotConfigured
	}

	s.logger.Info("generating training", "provider", s.provider.Name(), "locale", request.Locale)

	result, err := s.provider.Generate(ctx, request)
	if err != nil {
		s.logger.Error("training generation failed", "provider", s.provider.Name(), "error", err)
		return provider.TrainingEnvelope{}, err
	}

	s.logger.Info("training generation succeeded", "provider", s.provider.Name(), "steps", len(result.Training.Steps))
	return result, nil
}
