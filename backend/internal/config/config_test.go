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
	t.Setenv("RUNNING_APP_CODEX_BINARY", "")
	t.Setenv("RUNNING_APP_CODEX_WORKDIR", "")
	t.Setenv("RUNNING_APP_CODEX_MODEL", "")
	t.Setenv("RUNNING_APP_CODEX_PROFILE", "")
	t.Setenv("RUNNING_APP_CODEX_SANDBOX", "")

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
	if cfg.Codex.BinaryPath != defaultCodexBinaryPath {
		t.Fatalf("Codex.BinaryPath = %q, want %q", cfg.Codex.BinaryPath, defaultCodexBinaryPath)
	}
	if cfg.Codex.Sandbox != defaultCodexSandbox {
		t.Fatalf("Codex.Sandbox = %q, want %q", cfg.Codex.Sandbox, defaultCodexSandbox)
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
	t.Setenv("RUNNING_APP_CODEX_BINARY", "/usr/local/bin/codex")
	t.Setenv("RUNNING_APP_CODEX_WORKDIR", "/srv/running-app")
	t.Setenv("RUNNING_APP_CODEX_MODEL", "gpt-5.4")
	t.Setenv("RUNNING_APP_CODEX_PROFILE", "backend")
	t.Setenv("RUNNING_APP_CODEX_SANDBOX", "workspace-write")

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
	if cfg.ReadHeaderTimeout != 2*time.Second {
		t.Fatalf("ReadHeaderTimeout = %s, want %s", cfg.ReadHeaderTimeout, 2*time.Second)
	}
	if cfg.ReadTimeout != 4*time.Second {
		t.Fatalf("ReadTimeout = %s, want %s", cfg.ReadTimeout, 4*time.Second)
	}
	if cfg.WriteTimeout != 5*time.Second {
		t.Fatalf("WriteTimeout = %s, want %s", cfg.WriteTimeout, 5*time.Second)
	}
	if cfg.IdleTimeout != 6*time.Second {
		t.Fatalf("IdleTimeout = %s, want %s", cfg.IdleTimeout, 6*time.Second)
	}
	if cfg.LogLevel != slog.LevelDebug {
		t.Fatalf("LogLevel = %v, want %v", cfg.LogLevel, slog.LevelDebug)
	}
	if cfg.Codex.BinaryPath != "/usr/local/bin/codex" {
		t.Fatalf("Codex.BinaryPath = %q, want %q", cfg.Codex.BinaryPath, "/usr/local/bin/codex")
	}
	if cfg.Codex.WorkingDir != "/srv/running-app" {
		t.Fatalf("Codex.WorkingDir = %q, want %q", cfg.Codex.WorkingDir, "/srv/running-app")
	}
	if cfg.Codex.Model != "gpt-5.4" {
		t.Fatalf("Codex.Model = %q, want %q", cfg.Codex.Model, "gpt-5.4")
	}
	if cfg.Codex.Profile != "backend" {
		t.Fatalf("Codex.Profile = %q, want %q", cfg.Codex.Profile, "backend")
	}
	if cfg.Codex.Sandbox != "workspace-write" {
		t.Fatalf("Codex.Sandbox = %q, want %q", cfg.Codex.Sandbox, "workspace-write")
	}
}

func TestDefaultHTTPAddrBindsToLoopback(t *testing.T) {
	if defaultHTTPAddr != "127.0.0.1:8080" {
		t.Fatalf("defaultHTTPAddr = %q, want %q", defaultHTTPAddr, "127.0.0.1:8080")
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
