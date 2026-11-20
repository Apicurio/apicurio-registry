
.PHONY: clean
clean:
	rm -rf openapi.json apicurioregistrysdk/client kiota_tmp .venv dist

# Generates the Kiota client into apicurioregistrysdk/client (skipped when it already exists).
.PHONY: generate
generate:
	python3 kiota-gen.py

.PHONY: install
install: generate
	poetry install

.PHONY: test
test:
	poetry run pytest -s

.PHONY: lint-check
lint-check:
	poetry run black ./ --check

.PHONY: lint-apply
lint-apply:
	poetry run black ./

.PHONY: build
build: install
	rm -rf dist
	poetry build

# Fails unless the wheel is pure Python and contains the generated client: PyPI rejects
# platform-tagged wheels such as linux_x86_64.
.PHONY: verify-wheel
verify-wheel:
	ls dist/*-py3-none-any.whl
	python3 -c "import glob, sys, zipfile; \
	names = zipfile.ZipFile(glob.glob('dist/*-py3-none-any.whl')[0]).namelist(); \
	sys.exit(0 if 'apicurioregistrysdk/client/registry_client.py' in names else 'generated client missing from the wheel')"

.PHONY: publish
publish:
	poetry publish -u __token__ -p ${PYPI_TOKEN}

.PHONY: update
update:
	poetry add microsoft-kiota-abstractions@latest
	poetry add microsoft-kiota-http@latest
	poetry add microsoft-kiota-serialization-json@latest
	poetry add microsoft-kiota-serialization-text@latest
	poetry add microsoft-kiota-serialization-form@latest
	poetry add microsoft-kiota-serialization-multipart@latest
