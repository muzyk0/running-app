package config

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestLoadReadsBackendDotEnvFile(t *testing.T) {
	tempDir := t.TempDir()
	if err := os.Mkdir(filepath.Join(tempDir, "backend"), 0o755); err != nil {
		t.Fatalf("mkdir backend dir: %v", err)
	}
	if err := os.WriteFile(
		filepath.Join(tempDir, "backend", ".env"),
		[]byte("RUNNING_APP_PROVIDER=static\nRUNNING_APP_REQUEST_TIMEOUT=42s\nRUNNING_APP_WRITE_TIMEOUT=43s\n"),
		0o644,
	); err != nil {
		t.Fatalf("write .env: %v", err)
	}

	previousDir, err := os.Getwd()
	if err != nil {
		t.Fatalf("getwd: %v", err)
	}
	if err := os.Chdir(tempDir); err != nil {
		t.Fatalf("chdir temp dir: %v", err)
	}
	t.Cleanup(func() {
		if err := os.Chdir(previousDir); err != nil {
			t.Fatalf("restore cwd: %v", err)
		}
	})

	unsetEnv(t, "RUNNING_APP_PROVIDER")
	unsetEnv(t, "RUNNING_APP_REQUEST_TIMEOUT")
	unsetEnv(t, "RUNNING_APP_WRITE_TIMEOUT")
	unsetEnv(t, "RUNNING_APP_ENV_FILE")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.Provider != "static" {
		t.Fatalf("Provider = %q, want %q", cfg.Provider, "static")
	}
	if cfg.RequestTimeout != 42*time.Second {
		t.Fatalf("RequestTimeout = %s, want %s", cfg.RequestTimeout, 42*time.Second)
	}
	if cfg.WriteTimeout != 43*time.Second {
		t.Fatalf("WriteTimeout = %s, want %s", cfg.WriteTimeout, 43*time.Second)
	}
}

func TestLoadKeepsProcessEnvPriorityOverDotEnv(t *testing.T) {
	tempDir := t.TempDir()
	envFilePath := filepath.Join(tempDir, ".env")
	if err := os.WriteFile(
		envFilePath,
		[]byte("RUNNING_APP_PROVIDER=static\nRUNNING_APP_REQUEST_TIMEOUT=42s\nRUNNING_APP_WRITE_TIMEOUT=43s\n"),
		0o644,
	); err != nil {
		t.Fatalf("write .env: %v", err)
	}

	t.Setenv("RUNNING_APP_ENV_FILE", envFilePath)
	t.Setenv("RUNNING_APP_PROVIDER", "codex")
	t.Setenv("RUNNING_APP_REQUEST_TIMEOUT", "55s")
	t.Setenv("RUNNING_APP_WRITE_TIMEOUT", "56s")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.Provider != "codex" {
		t.Fatalf("Provider = %q, want %q", cfg.Provider, "codex")
	}
	if cfg.RequestTimeout != 55*time.Second {
		t.Fatalf("RequestTimeout = %s, want %s", cfg.RequestTimeout, 55*time.Second)
	}
	if cfg.WriteTimeout != 56*time.Second {
		t.Fatalf("WriteTimeout = %s, want %s", cfg.WriteTimeout, 56*time.Second)
	}
}

func TestLoadRejectsMalformedDotEnv(t *testing.T) {
	tempDir := t.TempDir()
	envFilePath := filepath.Join(tempDir, ".env")
	if err := os.WriteFile(envFilePath, []byte("RUNNING_APP_PROVIDER\n"), 0o644); err != nil {
		t.Fatalf("write malformed .env: %v", err)
	}

	t.Setenv("RUNNING_APP_ENV_FILE", envFilePath)

	_, err := Load()
	if err == nil {
		t.Fatal("Load() error = nil, want error")
	}
}

func unsetEnv(t *testing.T, key string) {
	t.Helper()

	previousValue, hadPreviousValue := os.LookupEnv(key)
	if err := os.Unsetenv(key); err != nil {
		t.Fatalf("unset env %q: %v", key, err)
	}

	t.Cleanup(func() {
		var err error
		if hadPreviousValue {
			err = os.Setenv(key, previousValue)
		} else {
			err = os.Unsetenv(key)
		}
		if err != nil {
			t.Fatalf("restore env %q: %v", key, err)
		}
	})
}
