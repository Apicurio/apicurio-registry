
.PHONY: clean
clean:
	rm -rf kiota_tmp v2.json v3.json

.PHONY: generate
generate:
	./generate.sh

.PHONY: test
test:
	go test ./... -count=1
