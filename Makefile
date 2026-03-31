SHELL := /bin/bash

ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
SCRIPTS_DIR := $(ROOT_DIR)scripts
TRAINING_API_BASE_URL ?= http://10.0.2.2:8080/

.PHONY: help format lint test ci coverage android-smoke backend-smoke docker-smoke android-ci backend-ci android-coverage backend-coverage android-build android-install backend-build backend-run

help:
	@printf "%s\n" \
		"Targets:" \
		"  format            Format repo-owned sources" \
		"  lint              Run repo lint checks" \
		"  test              Run bootstrap test suite" \
		"  ci                Run Android and backend CI entrypoints" \
		"  coverage          Run Android and backend coverage entrypoints" \
		"  android-smoke     Verify Android Gradle wrapper and module wiring" \
		"  backend-smoke     Verify backend Go module resolution and tests" \
		"  docker-smoke      Verify backend Docker build context" \
		"  android-ci        Run Android CI script" \
		"  backend-ci        Run backend CI script" \
		"  android-coverage  Run Android coverage script" \
		"  backend-coverage  Run backend coverage script" \
		"  android-build     Build debug APK from android-app/" \
		"  android-install   Install debug APK via adb" \
		"  backend-build     Build backend binary to backend/bin/running-app-backend" \
		"  backend-run       Run backend server from backend/" \
		"" \
		"Variables:" \
		"  TRAINING_API_BASE_URL=$(TRAINING_API_BASE_URL)"

format:
	@$(SCRIPTS_DIR)/format.sh

lint:
	@$(SCRIPTS_DIR)/lint.sh

test:
	@$(SCRIPTS_DIR)/test-bootstrap.sh

ci:
	@$(SCRIPTS_DIR)/android-ci.sh
	@$(SCRIPTS_DIR)/backend-ci.sh

coverage:
	@$(SCRIPTS_DIR)/android-coverage.sh
	@$(SCRIPTS_DIR)/backend-coverage.sh

android-smoke:
	@$(SCRIPTS_DIR)/android-gradle-smoke.sh

backend-smoke:
	@$(SCRIPTS_DIR)/backend-go-smoke.sh

docker-smoke:
	@$(SCRIPTS_DIR)/backend-docker-smoke.sh

android-ci:
	@$(SCRIPTS_DIR)/android-ci.sh

backend-ci:
	@$(SCRIPTS_DIR)/backend-ci.sh

android-coverage:
	@$(SCRIPTS_DIR)/android-coverage.sh

backend-coverage:
	@$(SCRIPTS_DIR)/backend-coverage.sh

android-build:
	@TRAINING_API_BASE_URL="$(TRAINING_API_BASE_URL)" $(SCRIPTS_DIR)/android-build.sh

android-install:
	@TRAINING_API_BASE_URL="$(TRAINING_API_BASE_URL)" $(SCRIPTS_DIR)/android-install.sh

backend-build:
	@$(SCRIPTS_DIR)/backend-build.sh

backend-run:
	@$(SCRIPTS_DIR)/backend-run.sh
