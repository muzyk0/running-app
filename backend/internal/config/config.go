package config

import (
	"fmt"
	"log/slog"
	"os"
	"strings"
	"time"
)

const (
	defaultHTTPAddr          = ":8080"
	defaultProvider          = "static"
	defaultRequestTimeout    = 12 * time.Second
	defaultShutdownTimeout   = 10 * time.Second
	defaultReadHeaderTimeout = 5 * time.Second
	defaultReadTimeout       = 15 * time.Second
	defaultWriteTimeout      = 15 * time.Second
	defaultIdleTimeout       = 30 * time.Second
)

type Config struct {
	HTTPAddr          string
	Provider          string
	RequestTimeout    time.Duration
	ShutdownTimeout   time.Duration
	ReadHeaderTimeout time.Duration
	ReadTimeout       time.Duration
	WriteTimeout      time.Duration
	IdleTimeout       time.Duration
	LogLevel          slog.Level
}

func Load() (Config, error) {
	cfg := Config{
		HTTPAddr:          defaultHTTPAddr,
		Provider:          defaultProvider,
		RequestTimeout:    defaultRequestTimeout,
		ShutdownTimeout:   defaultShutdownTimeout,
		ReadHeaderTimeout: defaultReadHeaderTimeout,
		ReadTimeout:       defaultReadTimeout,
		WriteTimeout:      defaultWriteTimeout,
		IdleTimeout:       defaultIdleTimeout,
		LogLevel:          slog.LevelInfo,
	}

	if value := strings.TrimSpace(os.Getenv("RUNNING_APP_HTTP_ADDR")); value != "" {
		cfg.HTTPAddr = value
	}

	if value := strings.TrimSpace(os.Getenv("RUNNING_APP_PROVIDER")); value != "" {
		cfg.Provider = value
	}

	if err := loadDuration("RUNNING_APP_REQUEST_TIMEOUT", &cfg.RequestTimeout); err != nil {
		return Config{}, err
	}
	if err := loadDuration("RUNNING_APP_SHUTDOWN_TIMEOUT", &cfg.ShutdownTimeout); err != nil {
		return Config{}, err
	}
	if err := loadDuration("RUNNING_APP_READ_HEADER_TIMEOUT", &cfg.ReadHeaderTimeout); err != nil {
		return Config{}, err
	}
	if err := loadDuration("RUNNING_APP_READ_TIMEOUT", &cfg.ReadTimeout); err != nil {
		return Config{}, err
	}
	if err := loadDuration("RUNNING_APP_WRITE_TIMEOUT", &cfg.WriteTimeout); err != nil {
		return Config{}, err
	}
	if err := loadDuration("RUNNING_APP_IDLE_TIMEOUT", &cfg.IdleTimeout); err != nil {
		return Config{}, err
	}
	if err := loadLogLevel("RUNNING_APP_LOG_LEVEL", &cfg.LogLevel); err != nil {
		return Config{}, err
	}

	if cfg.HTTPAddr == "" {
		return Config{}, fmt.Errorf("RUNNING_APP_HTTP_ADDR must not be empty")
	}
	if cfg.Provider == "" {
		return Config{}, fmt.Errorf("RUNNING_APP_PROVIDER must not be empty")
	}

	return cfg, nil
}

func loadDuration(env string, target *time.Duration) error {
	value := strings.TrimSpace(os.Getenv(env))
	if value == "" {
		return nil
	}

	parsed, err := time.ParseDuration(value)
	if err != nil {
		return fmt.Errorf("%s: parse duration: %w", env, err)
	}
	if parsed <= 0 {
		return fmt.Errorf("%s must be greater than zero", env)
	}

	*target = parsed
	return nil
}

func loadLogLevel(env string, target *slog.Level) error {
	value := strings.TrimSpace(os.Getenv(env))
	if value == "" {
		return nil
	}

	var level slog.Level
	if err := level.UnmarshalText([]byte(strings.ToUpper(value))); err != nil {
		return fmt.Errorf("%s: parse log level: %w", env, err)
	}

	*target = level
	return nil
}
