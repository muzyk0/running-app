package config

import (
	"log/slog"
	"testing"
	"time"
)

func TestLoadDefaults(t *testing.T) {
	t.Setenv("RUNNING_APP_HTTP_ADDR", "")
	t.Setenv("RUNNING_APP_PROVIDER", "")
	t.Setenv("RUNNING_APP_REQUEST_TIMEOUT", "")
	t.Setenv("RUNNING_APP_SHUTDOWN_TIMEOUT", "")
	t.Setenv("RUNNING_APP_READ_HEADER_TIMEOUT", "")
	t.Setenv("RUNNING_APP_READ_TIMEOUT", "")
	t.Setenv("RUNNING_APP_WRITE_TIMEOUT", "")
	t.Setenv("RUNNING_APP_IDLE_TIMEOUT", "")
	t.Setenv("RUNNING_APP_LOG_LEVEL", "")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.HTTPAddr != defaultHTTPAddr {
		t.Fatalf("HTTPAddr = %q, want %q", cfg.HTTPAddr, defaultHTTPAddr)
	}
	if cfg.Provider != defaultProvider {
		t.Fatalf("Provider = %q, want %q", cfg.Provider, defaultProvider)
	}
	if cfg.RequestTimeout != defaultRequestTimeout {
		t.Fatalf("RequestTimeout = %s, want %s", cfg.RequestTimeout, defaultRequestTimeout)
	}
	if cfg.LogLevel != slog.LevelInfo {
		t.Fatalf("LogLevel = %v, want %v", cfg.LogLevel, slog.LevelInfo)
	}
}

func TestLoadOverrides(t *testing.T) {
	t.Setenv("RUNNING_APP_HTTP_ADDR", "127.0.0.1:9000")
	t.Setenv("RUNNING_APP_PROVIDER", "static")
	t.Setenv("RUNNING_APP_REQUEST_TIMEOUT", "3s")
	t.Setenv("RUNNING_APP_SHUTDOWN_TIMEOUT", "11s")
	t.Setenv("RUNNING_APP_READ_HEADER_TIMEOUT", "2s")
	t.Setenv("RUNNING_APP_READ_TIMEOUT", "4s")
	t.Setenv("RUNNING_APP_WRITE_TIMEOUT", "5s")
	t.Setenv("RUNNING_APP_IDLE_TIMEOUT", "6s")
	t.Setenv("RUNNING_APP_LOG_LEVEL", "debug")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.HTTPAddr != "127.0.0.1:9000" {
		t.Fatalf("HTTPAddr = %q, want %q", cfg.HTTPAddr, "127.0.0.1:9000")
	}
	if cfg.RequestTimeout != 3*time.Second {
		t.Fatalf("RequestTimeout = %s, want %s", cfg.RequestTimeout, 3*time.Second)
	}
	if cfg.ShutdownTimeout != 11*time.Second {
		t.Fatalf("ShutdownTimeout = %s, want %s", cfg.ShutdownTimeout, 11*time.Second)
	}
	if cfg.LogLevel != slog.LevelDebug {
		t.Fatalf("LogLevel = %v, want %v", cfg.LogLevel, slog.LevelDebug)
	}
}

func TestLoadInvalidDuration(t *testing.T) {
	t.Setenv("RUNNING_APP_REQUEST_TIMEOUT", "abc")

	_, err := Load()
	if err == nil {
		t.Fatal("Load() error = nil, want error")
	}
}

func TestLoadRejectsNonPositiveDuration(t *testing.T) {
	t.Setenv("RUNNING_APP_REQUEST_TIMEOUT", "0s")

	_, err := Load()
	if err == nil {
		t.Fatal("Load() error = nil, want error")
	}
}

func TestLoadRejectsInvalidLogLevel(t *testing.T) {
	t.Setenv("RUNNING_APP_LOG_LEVEL", "loud")

	_, err := Load()
	if err == nil {
		t.Fatal("Load() error = nil, want error")
	}
}
