package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/muzyk0/running-app/backend/internal/api"
	"github.com/muzyk0/running-app/backend/internal/config"
	"github.com/muzyk0/running-app/backend/internal/prompt"
	"github.com/muzyk0/running-app/backend/internal/provider"
	"github.com/muzyk0/running-app/backend/internal/provider/codexcli"
	"github.com/muzyk0/running-app/backend/internal/schema"
	"github.com/muzyk0/running-app/backend/internal/service"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintf(os.Stderr, "running-app backend: %v\n", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: cfg.LogLevel,
	}))

	generator, err := buildGenerator(cfg)
	if err != nil {
		return err
	}

	trainingService := service.NewTrainingService(
		generator,
		prompt.NewBuilder(),
		schema.NewNormalizer(),
		logger.With("component", "service"),
	)
	router := api.NewRouter(logger.With("component", "http"), trainingService, cfg.RequestTimeout)

	server := &http.Server{
		Addr:              cfg.HTTPAddr,
		Handler:           router,
		ReadHeaderTimeout: cfg.ReadHeaderTimeout,
		ReadTimeout:       cfg.ReadTimeout,
		WriteTimeout:      cfg.WriteTimeout,
		IdleTimeout:       cfg.IdleTimeout,
	}

	serverErr := make(chan error, 1)
	go func() {
		logger.Info("starting backend server", "addr", cfg.HTTPAddr, "provider", generator.Name())
		serverErr <- server.ListenAndServe()
	}()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	select {
	case err := <-serverErr:
		if errors.Is(err, http.ErrServerClosed) {
			return nil
		}
		return err
	case <-ctx.Done():
		logger.Info("shutdown signal received")
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), cfg.ShutdownTimeout)
	defer cancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		return fmt.Errorf("shutdown http server: %w", err)
	}

	if err := <-serverErr; err != nil && !errors.Is(err, http.ErrServerClosed) {
		return err
	}

	logger.Info("backend server stopped cleanly")
	return nil
}

func buildGenerator(cfg config.Config) (provider.Generator, error) {
	switch cfg.Provider {
	case "static":
		return provider.NewStaticGenerator(), nil
	case "codex":
		return codexcli.New(codexcli.Config{
			BinaryPath: cfg.Codex.BinaryPath,
			WorkingDir: cfg.Codex.WorkingDir,
			Model:      cfg.Codex.Model,
			Profile:    cfg.Codex.Profile,
			Sandbox:    cfg.Codex.Sandbox,
		}), nil
	default:
		return nil, fmt.Errorf("unsupported provider %q", cfg.Provider)
	}
}
