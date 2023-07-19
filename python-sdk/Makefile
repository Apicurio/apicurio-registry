
clean:
	rm -f openapi.json
	rm -rf apicurioregistrysdk/client

install:
	poetry install

test:
	poetry run pytest -s

lint-check:
	poetry run black ./ --check

lint-apply:
	poetry run black ./

publish:
	poetry publish --build -u ${PYPI_USERNAME} -p ${PYPI_PASSWORD}

update:
	poetry add microsoft-kiota-abstractions@latest
	poetry add microsoft-kiota-http@latest
	poetry add microsoft-kiota-serialization-json@latest
	poetry add microsoft-kiota-serialization-text@latest
