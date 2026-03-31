package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/joho/godotenv"
)

const envFileOverrideEnv = "RUNNING_APP_ENV_FILE"

func loadOptionalEnvFile() error {
	if explicitPath := strings.TrimSpace(os.Getenv(envFileOverrideEnv)); explicitPath != "" {
		return loadEnvFiles(explicitPath)
	}

	return loadEnvFiles(
		filepath.Join("backend", ".env"),
		".env",
	)
}

func loadEnvFiles(paths ...string) error {
	for _, path := range paths {
		if err := loadEnvFile(path); err != nil {
			return err
		}
	}

	return nil
}

func loadEnvFile(path string) error {
	if strings.TrimSpace(path) == "" {
		return nil
	}

	_, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return fmt.Errorf("stat env file %q: %w", path, err)
	}

	if err := godotenv.Load(path); err != nil {
		return fmt.Errorf("load env file %q: %w", path, err)
	}

	return nil
}
