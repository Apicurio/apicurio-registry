# used for mem, sql, mssql & kafkasql.
# 'override' keyword prevents the variable from being overrideen
override DOCKERFILE_LOCATION := ./distro/docker/target/docker

MEM_DOCKERFILE ?= Dockerfile.jvm
SQL_DOCKERFILE ?= Dockerfile.sql.jvm
MSSQL_DOCKERFILE ?= Dockerfile.mssql.jvm
KAFKASQL_DOCKERFILE ?= Dockerfile.kafkasql.jvm
DOCKER_BUILD_WORKSPACE ?= $(DOCKERFILE_LOCATION)

# Special variable that sets the default target
.DEFAULT_GOAL := help

# You can override these variables from the command line.
IMAGE_REPO ?= docker.io
IMAGE_GROUP ?= apicurio
IMAGE_TAG ?= latest
IMAGE_PLATFORMS ?= linux/amd64,linux/arm64,linux/s390x,linux/ppc64le
SKIP_TESTS ?= false
INTEGRATION_TESTS_PROFILE ?= ci
BUILD_FLAGS ?=
REGISTRY_IMAGE ?=


# Colour Codes for help message
override RED := \033[0;31m
override BLUE := \033[36m
override NC := \033[0m
override BGreen := \033[1;32m

# run 'make' or 'make help' to get a list of available targets and their description
.PHONY: help
help:
	@echo ""
	@echo "================================================================="
	@printf "$(BGreen)Please use 'make <target>', where target is one of:-$(NC)\n"
	@echo "================================================================="
	@grep -E '^\.PHONY: [a-zA-Z_-]+ .*?## .*$$' $(MAKEFILE_LIST)  | awk 'BEGIN {FS = "(: |##)"}; {printf "\033[36m%-42s\033[0m %s\n", $$2, $$3}'
	@echo ""
	@echo "================================================================="
	@printf "$(BGreen)Variables available for override:-$(NC)\n"		
	@echo "================================================================="
	@printf "$(BLUE)SKIP_TESTS$(NC)             Skips Tests. The Default value is '$(SKIP_TESTS)'\n"
	@printf "$(BLUE)BUILD_FLAGS$(NC)            Additional maven build flags. By Default, it doesn't pass any additional flags.\n"
	@printf "$(BLUE)IMAGE_REPO$(NC)             Image Repository of the image. Default is '$(IMAGE_REPO)'\n"
	@printf "$(BLUE)IMAGE_GROUP$(NC)            Image Group of the image. Default is '$(IMAGE_GROUP)'\n"
	@printf "$(BLUE)IMAGE_TAG$(NC)              Image tag. Default is '$(IMAGE_TAG)'\n"
	@printf "$(BLUE)IMAGE_PLATFORMS$(NC)        Supported Platforms for Multi-arch Images. Default platforms are '$(IMAGE_PLATFORMS)'\n"
	@printf "$(BLUE)DOCKERFILE_LOCATION$(NC)    Path to the dockerfile. Default is '$(DOCKERFILE_LOCATION)'\n"
	@printf "$(BLUE)DOCKER_BUILD_WORKSPACE$(NC) Image build workspace. Default is '$(DOCKER_BUILD_WORKSPACE)'\n"
	@echo ""



.PHONY: build-all ## Builds and test all modules. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-all:
	@echo "----------------------------------------------------------------------"
	@echo "                   Building All Modules                               "
	@echo "----------------------------------------------------------------------"
	./mvnw -T 1.5C clean install --no-transfer-progress -Pprod -Psql -Pmssql -Pkafkasql -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)

.PHONY: build-in-memory ## Builds and test in-memory module. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-in-memory:
	@echo "----------------------------------------------------------------------"
	@echo "                   Building In-memory Module                          "
	@echo "----------------------------------------------------------------------"
	./mvnw -T 1.5C clean install --no-transfer-progress -Pprod -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)

.PHONY: build-sql ## Builds and test sql module. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-sql:
	@echo "----------------------------------------------------------------------"
	@echo "                   Building SQL Module                                "
	@echo "----------------------------------------------------------------------"
	./mvnw -T 1.5C clean install --no-transfer-progress -Pprod -Psql -Pno-slow-tests -DskipAppTests -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)

.PHONY: build-kafkasql ## Builds and test kafkasql module. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-kafkasql:
	@echo "----------------------------------------------------------------------"
	@echo "                   Building Kafkasql Module                           "
	@echo "----------------------------------------------------------------------"
	./mvnw -T 1.5C clean install --no-transfer-progress -Pprod -Pkafkasql -Pno-slow-tests -DskipAppTests -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)

.PHONY: build-mem-native ## Builds mem storage variant native executable. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-mem-native:
	@echo "----------------------------------------------------------------------"
	@echo "             Building In-Memory Storage Variant Natively              "
	@echo "----------------------------------------------------------------------"
	./mvnw -T 1.5C package --no-transfer-progress -Pnative -Dquarkus.native.container-build=true -Pprod -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)

.PHONY: build-sql-native ## Builds sql storage variant native executable. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-sql-native:
	@echo "----------------------------------------------------------------------"
	@echo "             Building SQL Storage Variant Natively                    "
	@echo "----------------------------------------------------------------------"
	./mvnw -T 1.5C package --no-transfer-progress -Pnative -Dquarkus.native.container-build=true -Pprod -Psql -pl storage/sql -DskipAppTests -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)

.PHONY: build-kafkasql-native ## Builds kafkasql storage variant native executable. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-kafkasql-native:
	@echo "----------------------------------------------------------------------"
	@echo "             Building Kafkasql Storage Variant Natively               "
	@echo "----------------------------------------------------------------------"
	./mvnw -T 1.5C package --no-transfer-progress -Pnative -Dquarkus.native.container-build=true -Pprod -Pkafkasql -pl storage/kafkasql -DskipAppTests -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)



.PHONY: build-mem-image ## Builds docker image for 'in-memory' storage variant. Variables available for override [MEM_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
build-mem-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For In-Memory Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKERFILE_LOCATION)/$(MEM_DOCKERFILE) -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-mem:$(IMAGE_TAG) $(DOCKER_BUILD_WORKSPACE)


.PHONY: push-mem-image ## Pushes docker image for 'in-memory' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-mem-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For In-Memory Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-mem:$(IMAGE_TAG)


.PHONY: build-mem-native-image ## Builds native docker image for 'mem' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-mem-native-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For In-Memory Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKERFILE_LOCATION)/Dockerfile.native -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-mem-native:$(IMAGE_TAG) app/

.PHONY: build-mem-native-scratch-image ## Builds native docker image from scratch for 'mem' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-mem-native-scratch-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For In-Memory Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKERFILE_LOCATION)/Dockerfile.native-scratch -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-mem-native-scratch:$(IMAGE_TAG) ./


.PHONY: push-mem-native-image ## Pushes native docker image for 'mem' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-mem-native-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For In-Memory Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-mem-native:$(IMAGE_TAG)


.PHONY: build-sql-image ## Builds docker image for 'sql' storage variant. Variables available for override [SQL_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
build-sql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For SQL Storage Variant "
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKERFILE_LOCATION)/$(SQL_DOCKERFILE) -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-sql:$(IMAGE_TAG) $(DOCKER_BUILD_WORKSPACE)

.PHONY: push-sql-image ## Pushes docker image for 'sql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-sql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For SQL Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-sql:$(IMAGE_TAG)

.PHONY: build-sql-native-image ## Builds native docker image for 'sql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-sql-native-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For SQL Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKERFILE_LOCATION)/Dockerfile.native -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-sql-native:$(IMAGE_TAG) storage/sql

.PHONY: push-sql-native-image ## Pushes native docker image for 'sql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-sql-native-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For SQL Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-sql-native:$(IMAGE_TAG)

.PHONY: build-mssql-image ## Builds docker image for 'mssql' storage variant. Variables available for override [MSSQL_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
build-mssql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For MSSQL Storage Variant "
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKERFILE_LOCATION)/$(MSSQL_DOCKERFILE) -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-mssql:$(IMAGE_TAG) $(DOCKER_BUILD_WORKSPACE)

.PHONY: push-mssql-image ## Pushes docker image for 'mssql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-mssql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For MSSQL Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-mssql:$(IMAGE_TAG)

.PHONY: build-kafkasql-image ## Builds docker image for kafkasql storage variant. Variables available for override [KAFKASQL_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
build-kafkasql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For Kafkasql Storage Variant "
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKERFILE_LOCATION)/$(KAFKASQL_DOCKERFILE) -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-kafkasql:$(IMAGE_TAG) $(DOCKER_BUILD_WORKSPACE)

.PHONY: push-kafkasql-image ## Pushes docker image for 'kafkasql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-kafkasql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For Kafkasql Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-kafkasql:$(IMAGE_TAG)

.PHONY: build-kafkasql-native-image ## Builds native docker image for kafkasql storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-kafkasql-native-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For Kafkasql Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKERFILE_LOCATION)/Dockerfile.native -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-kafkasql-native:$(IMAGE_TAG) storage/kafkasql

.PHONY: push-kafkasql-native-image ## Pushes native docker image for 'kafkasql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-kafkasql-native-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For Kafkasql Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-kafkasql-native:$(IMAGE_TAG)

.PHONY: build-all-images ## Builds all the Images. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-all-images: build-mem-image build-sql-image build-mssql-image build-kafkasql-image

.PHONY: push-all-images ## Pushes all the Images. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-all-images: push-mem-image push-sql-image push-mssql-image push-kafkasql-image


.PHONY: mem-multiarch-images ## Builds and pushes multi-arch images for 'in-memory' storage variant. Variables available for override [MEM_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
mem-multiarch-images:
	@echo "------------------------------------------------------------------------"
	@echo " Building Multi-arch Images For In-Memory Storage Variant"
	@echo " Supported Platforms: $(IMAGE_PLATFORMS)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker buildx build --push -f $(DOCKERFILE_LOCATION)/$(MEM_DOCKERFILE) -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-mem:$(IMAGE_TAG) --platform $(IMAGE_PLATFORMS) $(DOCKER_BUILD_WORKSPACE)

.PHONY: sql-multiarch-images ## Builds and pushes multi-arch images for 'sql' storage variant. Variables available for override [SQL_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
sql-multiarch-images:
	@echo "------------------------------------------------------------------------"
	@echo " Building Multi-arch Images For SQL Storage Variant "
	@echo " Supported Platforms: $(IMAGE_PLATFORMS)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker buildx build --push -f $(DOCKERFILE_LOCATION)/$(SQL_DOCKERFILE) -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-sql:$(IMAGE_TAG) --platform $(IMAGE_PLATFORMS) $(DOCKER_BUILD_WORKSPACE)

.PHONY: mssql-multiarch-images ## Builds and pushes multi-arch images for 'mssql' storage variant. Variables available for override [MSSQL_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
mssql-multiarch-images:
	@echo "------------------------------------------------------------------------"
	@echo " Building Multi-arch Images For SQL Server Storage Variant "
	@echo " Supported Platforms: $(IMAGE_PLATFORMS)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker buildx build --push -f $(DOCKERFILE_LOCATION)/$(MSSQL_DOCKERFILE) -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-mssql:$(IMAGE_TAG) --platform $(IMAGE_PLATFORMS) $(DOCKER_BUILD_WORKSPACE)

.PHONY: kafkasql-multiarch-images ## Builds and pushes multi-arch images for kafkasql storage variant. Variables available for override [KAFKASQL_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
kafkasql-multiarch-images:
	@echo "------------------------------------------------------------------------"
	@echo " Building Multi-arch Images For Kafkasql Storage Variant "
	@echo " Supported Platforms: $(IMAGE_PLATFORMS)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker buildx build --push -f $(DOCKERFILE_LOCATION)/$(KAFKASQL_DOCKERFILE) -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-kafkasql:$(IMAGE_TAG) --platform $(IMAGE_PLATFORMS) $(DOCKER_BUILD_WORKSPACE)

.PHONY: mem-native-scratch-image ## Builds and pushes multi-arch images for mem storage variant based on scratch. Variables available for override [MEM_SCRATCH_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
mem-native-scratch-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Multi-arch Images For Mem Storage Variant on Scratch"
	@echo " Supported Platforms: $(IMAGE_PLATFORMS)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker buildx build --push -f $(DOCKERFILE_LOCATION)/$(MEM_SCRATCH_DOCKERFILE) -t $(IMAGE_REPO)/$(IMAGE_GROUP)/apicurio-registry-mem-native-scratch:$(IMAGE_TAG) --platform $(IMAGE_PLATFORMS) $(DOCKER_BUILD_WORKSPACE)

.PHONY: multiarch-registry-images ## Builds and pushes multi-arch registry images for all variants. Variables available for override [IMAGE_REPO, IMAGE_TAG]
multiarch-registry-images: mem-multiarch-images sql-multiarch-images mssql-multiarch-images kafkasql-multiarch-images mem-native-scratch-image


.PHONY: pr-check ## Builds and runs basic tests for multitenant registry pipelines
pr-check:
	CURRENT_ENV=mas mvn clean install -Pno-docker -Dskip.npm -Pprod -Psql -am -pl storage/sql \
		-Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false
	./scripts/clean-postgres.sh
	CURRENT_ENV=mas NO_DOCKER=true mvn verify -Pintegration-tests -Psql -am -pl integration-tests \
		-Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false

.PHONY: build-project ## Builds the components for multitenant registry pipelines
build-project:
# run unit tests for app module
	CURRENT_ENV=mas mvn clean install -Pno-docker -Dskip.npm -Pprod -Psql -am -pl app -Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false
# build everything without running tests in order to be able to build container images
	CURRENT_ENV=mas mvn clean install -Pprod -Pno-docker -Dskip.npm -Psql -Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false -DskipTests


.PHONY: run-ui-tests ## Runs ui e2e tests
run-ui-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                         Running UI Tests                             "
	@echo "----------------------------------------------------------------------"
	./mvnw -am verify --no-transfer-progress -Pintegration-tests -Pui -Premote-sql -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false


############################################# In-Memory Integration Tests #########################################################################




.PHONY: run-in-memory-integration-tests ## Runs mem e2e tests
run-in-memory-integration-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                 Running In Memory Integration Tests                        "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -P$(INTEGRATION_TESTS_PROFILE) $(REGISTRY_IMAGE) -Premote-mem -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress


.PHONY: run-in-memory-auth-tests ## Runs mem auth integration tests
run-in-memory-auth-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                  Running In Memory Auth Integration Tests                  "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Pauth $(REGISTRY_IMAGE) -Premote-mem -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress


############################################# SQL Integration Tests #########################################################################


.PHONY: run-sql-integration-tests ## Runs sql e2e tests
run-sql-integration-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                 Running Sql Integration Tests                        "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -P$(INTEGRATION_TESTS_PROFILE) $(REGISTRY_IMAGE) -Premote-sql -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-sql-upgrade-tests ## Runs sql e2e tests
run-sql-upgrade-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                 Running Sql Integration Tests                        "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Psqlit $(REGISTRY_IMAGE) -Premote-sql -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-sql-auth-tests ## Runs sql auth integration tests
run-sql-auth-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                  Running SQL Auth Integration Tests                  "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Pauth $(REGISTRY_IMAGE) -Premote-sql -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-sql-migration-integration-tests ## Runs sql migration integration tests
run-sql-migration-integration-tests:
	@echo "----------------------------------------------------------------------"
	@echo "               Running SQL Migration Integration Tests                "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Pmigration -Premote-sql -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-sql-multitenancy-integration-tests ## Runs multitenancy integration tests
run-sql-multitenancy-integration-tests:
	@echo "----------------------------------------------------------------------"
	@echo "               Running Multitenancy Integration Tests                 "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Pmultitenancy -Premote-sql -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false


############################################# KafkaSql Integration Tests #########################################################################


.PHONY: run-kafkasql-integration-tests ## Runs kafkasql integration tests
run-kafkasql-integration-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                 Running KafkaSql Integration Tests                        "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -P$(INTEGRATION_TESTS_PROFILE) -Premote-kafka -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-kafkasql-upgrade-tests ## Runs sql e2e tests
run-kafkasql-upgrade-tests :
	@echo "----------------------------------------------------------------------"
	@echo "                 Running KafkaSql Upgrade Integration Tests                        "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Pkafkasqlit -Premote-kafka -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-kafkasql-migration-integration-tests ## Runs kafkasql migration integration tests
run-kafkasql-migration-integration-tests:
	@echo "----------------------------------------------------------------------"
	@echo "             Running KafkaSQL Migration Integration Tests             "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Pmigration -Premote-kafka -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-kafkasql-auth-tests ## Runs kafkasql auth integration tests
run-kafkasql-auth-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                Running KafkaSQL Auth Integration Tests               "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Pauth -Premote-kafka -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

############################################# MSSQL Integration Tests #########################################################################

.PHONY: run-mssql-integration-tests ## Runs mssql integration tests
run-mssql-integration-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                 Running SQL Server Integration Tests                 "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -P$(INTEGRATION_TESTS_PROFILE) -Pmssql -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-mssql-clustered-integration-tests ## Runs mssql clustered integration tests
run-mssql-clustered-integration-tests:
	@echo "----------------------------------------------------------------------"
	@echo "               Running SQL Server clustered Integration Tests         "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Pclustered -Pmssql -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-mssql-legacy-tests ## Runs mssql legacy tests
run-mssql-legacy-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                     Running SQL Server Legacy Tests                  "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -P$(INTEGRATION_TESTS_PROFILE) -mssql -pl integration-tests/legacy-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-mssql-auth-integration-tests ## Runs mssql auth integration tests
run-mssql-auth-integration-tests:
	@echo "----------------------------------------------------------------------"
	@echo "                Running SQL Server Auth Integration Tests             "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Pauth -mssql -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress

.PHONY: run-mssql-migration-integration-tests ## Runs mssql migration integration tests
run-mssql-migration-integration-tests:
	@echo "----------------------------------------------------------------------"
	@echo "             Running SQL Server Migration Integration Tests           "
	@echo "----------------------------------------------------------------------"
	./mvnw verify -am --no-transfer-progress -Pintegration-tests -Pmigration -mssql -pl integration-tests -Dmaven.javadoc.skip=true --no-transfer-progress



.PHONY: integration-tests ## Runs all integration tests [SKIP_TESTS, BUILD_FLAGS]
integration-tests: build-all  run-ui-tests run-sql-integration-tests run-mssql-integration-tests run-mssql-clustered-integration-tests run-kafkasql-integration-tests run-multitenancy-integration-tests run-sql-migration-integration-tests run-mssql-migration-integration-tests run-kafkasql-migration-integration-tests run-sql-auth-integration-tests run-mssql-auth-integration-tests run-kafkasql-auth-integration-tests run-sql-legacy-tests run-mssql-legacy-tests run-kafkasql-legacy-tests

# Please declare your targets as .PHONY in the format shown below, so that the 'make help' parses the information correctly.
#
# .PHONY: <target-name>  ## Description of what target does
