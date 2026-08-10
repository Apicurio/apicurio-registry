.PHONY: all build-local build-full test-integration run-postgres clean-postgres check-deps

all: build-full check-deps test-integration

define DOCKER_COMPOSE_YML
version: '3.1'

services:
  postgres:
    image: postgres
    environment:
      POSTGRES_USER: apicurio-registry
      POSTGRES_PASSWORD: password
  app:
    image: apicurio/apicurio-registry:latest-release
    ports:
      - 8080:8080
    environment:
      APICURIO_STORAGE_KIND: 'sql'
      APICURIO_STORAGE_SQL_KIND: 'postgresql'
      APICURIO_DATASOURCE_URL: 'jdbc:postgresql://postgres/apicurio-registry'
      APICURIO_DATASOURCE_USERNAME: apicurio-registry
      APICURIO_DATASOURCE_PASSWORD: password
endef
export DOCKER_COMPOSE_YML

build-local:
	./mvnw clean install -Dlocal -DskipTests

build-full:
	./mvnw clean install -Dfull -DskipTests -DcliSkipNative

test-integration:
	./mvnw verify -Pintegration-tests -Plocal-tests -pl integration-tests -am

run-postgres:
	@mkdir -p .work
	echo "$$DOCKER_COMPOSE_YML" > .work/test.yml
	docker compose -f .work/test.yml up

clean-postgres:
	docker compose -f .work/test.yml down -v
	rm -f .work/test.yml

check-deps:
	./mvnw -T1 install -pl app -am -DskipTests -Dfull -DcliSkipNative -q
	./mvnw -T1 -Pdependency-check package -DskipTests -Dfull -DcliSkipNative
