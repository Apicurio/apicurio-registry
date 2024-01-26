
.PHONY: clean
clean:
	rm -rf kiota_tmp v2.json v3.json

.PHONY: generate
generate:
	./generate.sh

.PHONY: generate-docker
generate-docker:
	./generate-docker.sh

.PHONY: test
test:
	go test ./... -count=1

.PHONY: test
format:
	go fmt ./...
