.PHONY: clean install test lint-check lint-apply build publish

clean:
	rm -rf .venv dist

install:
	poetry install --all-extras

test:
	poetry run pytest -s

lint-check:
	poetry run black ./ --check

lint-apply:
	poetry run black ./

build: install
	poetry build

publish:
	poetry publish --build -u __token__ -p $(PYPI_TOKEN)
