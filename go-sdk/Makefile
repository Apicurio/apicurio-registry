MKFILE_PATH := $(abspath $(lastword $(MAKEFILE_LIST)))
PROJECT_PATH := $(patsubst %/,%,$(dir $(MKFILE_PATH)))
PROJECT_BIN := $(PROJECT_PATH)/bin
GO := $(PROJECT_BIN)/go1.20

bin/go:
	mkdir -p $(PROJECT_BIN)
	GOBIN=$(PROJECT_BIN) go install golang.org/dl/go1.20@latest
	$(PROJECT_BIN)/go1.20 download

.PHONY: clean
clean:
	rm -rf $(PROJECT_BIN) kiota_tmp v2.json v3.json

.PHONY: generate
generate:
	./generate.sh

.PHONY: test
test: bin/go
	GOBIN=$(PROJECT_BIN) ${GO} test ./... -count=1

.PHONY: test
format: bin/go
	GOBIN=$(PROJECT_BIN) ${GO} fmt ./...
