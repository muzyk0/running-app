SHELL := /bin/bash

ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
SCRIPTS_DIR := $(ROOT_DIR)scripts

.PHONY: help format lint test ci coverage android-smoke backend-smoke docker-smoke android-ci backend-ci android-coverage backend-coverage

help:
	@printf "Targets: format lint test ci coverage android-smoke backend-smoke docker-smoke android-ci backend-ci android-coverage backend-coverage\n"

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
