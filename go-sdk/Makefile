MKFILE_PATH := $(abspath $(lastword $(MAKEFILE_LIST)))
PROJECT_PATH := $(patsubst %/,%,$(dir $(MKFILE_PATH)))
PROJECT_BIN := $(PROJECT_PATH)/bin
GO := $(PROJECT_BIN)/go1.23.7

bin/go: check
	mkdir -p $(PROJECT_BIN)
	GOBIN=$(PROJECT_BIN) go install golang.org/dl/go1.23.7@latest
	$(PROJECT_BIN)/go1.23.7 download

.PHONY: check
check:
	@{ \
		V="$$(go version | sed -e 's|[^0-9.]*\([0-9.]*\).*|\1|')"; \
		if [ ! "$$V" \> 1.23 ]; then \
			echo "ERROR: Your go version $$V is not supported. Minimum required version is 1.23."; \
			exit 1; \
		fi \
	}

.PHONY: clean
clean:
	rm -rf $(PROJECT_BIN) kiota_tmp v2.json v3.json

.PHONY: generate
generate: check
	./generate.sh

.PHONY: test
test: bin/go
	GOBIN=$(PROJECT_BIN) ${GO} test ./... -count=1

.PHONY: test
format: bin/go
	GOBIN=$(PROJECT_BIN) ${GO} fmt ./...
