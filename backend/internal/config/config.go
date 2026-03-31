package config

import (
	"fmt"
	"log/slog"
	"os"
	"strings"
	"time"
)

const (
	defaultHTTPAddr          = "127.0.0.1:8080"
	defaultProvider          = "codex"
	defaultRequestTimeout    = 90 * time.Second
	defaultShutdownTimeout   = 10 * time.Second
	defaultReadHeaderTimeout = 5 * time.Second
	defaultReadTimeout       = 15 * time.Second
	defaultWriteTimeout      = 2 * time.Minute
	defaultIdleTimeout       = 30 * time.Second
	defaultCodexBinaryPath   = "codex"
	defaultCodexSandbox      = "read-only"
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
	Codex             CodexConfig
}

type CodexConfig struct {
	BinaryPath string
	WorkingDir string
	Model      string
	Profile    string
	Sandbox    string
}

func Load() (Config, error) {
	if err := loadOptionalEnvFile(); err != nil {
		return Config{}, err
	}

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
		Codex: CodexConfig{
			BinaryPath: defaultCodexBinaryPath,
			Sandbox:    defaultCodexSandbox,
		},
	}

	if value := strings.TrimSpace(os.Getenv("RUNNING_APP_HTTP_ADDR")); value != "" {
		cfg.HTTPAddr = value
	}

	if value := strings.TrimSpace(os.Getenv("RUNNING_APP_PROVIDER")); value != "" {
		cfg.Provider = value
	}
	if value := strings.TrimSpace(os.Getenv("RUNNING_APP_CODEX_BINARY")); value != "" {
		cfg.Codex.BinaryPath = value
	}
	if value := strings.TrimSpace(os.Getenv("RUNNING_APP_CODEX_WORKDIR")); value != "" {
		cfg.Codex.WorkingDir = value
	}
	if value := strings.TrimSpace(os.Getenv("RUNNING_APP_CODEX_MODEL")); value != "" {
		cfg.Codex.Model = value
	}
	if value := strings.TrimSpace(os.Getenv("RUNNING_APP_CODEX_PROFILE")); value != "" {
		cfg.Codex.Profile = value
	}
	if value := strings.TrimSpace(os.Getenv("RUNNING_APP_CODEX_SANDBOX")); value != "" {
		cfg.Codex.Sandbox = value
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
	if cfg.Codex.BinaryPath == "" {
		return Config{}, fmt.Errorf("RUNNING_APP_CODEX_BINARY must not be empty")
	}
	if cfg.Codex.Sandbox == "" {
		return Config{}, fmt.Errorf("RUNNING_APP_CODEX_SANDBOX must not be empty")
	}
	if cfg.WriteTimeout <= cfg.RequestTimeout {
		return Config{}, fmt.Errorf("RUNNING_APP_WRITE_TIMEOUT must be greater than RUNNING_APP_REQUEST_TIMEOUT")
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
