SHELL := /bin/bash

ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
SCRIPTS_DIR := $(ROOT_DIR)scripts

.PHONY: help format lint test android-smoke backend-smoke docker-smoke

help:
	@printf "Targets: format lint test android-smoke backend-smoke docker-smoke\n"

format:
	@$(SCRIPTS_DIR)/format.sh

lint:
	@$(SCRIPTS_DIR)/lint.sh

test:
	@$(SCRIPTS_DIR)/test-bootstrap.sh

android-smoke:
	@$(SCRIPTS_DIR)/android-gradle-smoke.sh

backend-smoke:
	@$(SCRIPTS_DIR)/backend-go-smoke.sh

docker-smoke:
	@$(SCRIPTS_DIR)/backend-docker-smoke.sh
