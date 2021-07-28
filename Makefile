# used for mem, sql & kafkasql. Hardcoded for tenant manager api, since the build workspace is different
# 'override' keyword prevents the variable from being overrideen
override DISTRO_DOCKER_WORKSPACE := ./distro/docker/target/docker

DOCKER_BUILD_WORKSPACE ?= $(DISTRO_DOCKER_WORKSPACE)

MEM_DOCKERFILE ?= Dockerfile.jvm
SQL_DOCKERFILE ?= Dockerfile.sql.jvm
KAFKASQL_DOCKERFILE ?= Dockerfile.kafkasql.jvm

# Special variable that sets the default target
.DEFAULT_GOAL := help

# You can override these variables from the command line.
IMAGE_REPO ?= docker.io
IMAGE_TAG ?= latest
SKIP_TESTS ?= false
BUILD_FLAGS ?=


# run 'make' or 'make help' to get a list of available targets and their description
.PHONY: help
help:
	@echo ""
	@echo "Please use \`make <target>' where <target> is one of:-"
	@grep -E '^\.PHONY: [a-zA-Z_-]+ .*?## .*$$' $(MAKEFILE_LIST)  | awk 'BEGIN {FS = "(: |##)"}; {printf "\033[36m%-30s\033[0m %s\n", $$2, $$3}'
	@echo ""
	@echo "=> SKIP_TESTS: You can skip the tests for the builds by overriding the value of this variable to true. The Default value is 'false'"
	@echo "=> BUILD_FLAGS: You can pass additional build flags by overriding the value of this variable. By Default, it doesn't pass any additional flags."
	@echo "=> IMAGE_REPO: Default repository for image is 'docker.io'. You can change it by overriding the values of this variable."
	@echo "=> IMAGE_TAG: Default tag for image is 'latest'. You can change it by overriding the values of this variable."
	@echo ""



.PHONY: build-all ## Builds and test all modules. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-all:
	@echo "----------------------------------------------------------------------"
	@echo "                   Building All Modules                               "
	@echo "----------------------------------------------------------------------"
	./mvnw clean install -Pprod -Psql -Pkafkasql -Pmultitenancy -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)

.PHONY: build-sql-native ## Builds sql storage variant native executable. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-sql-native:
	./mvnw package -Pnative -Dquarkus.native.container-build=true -Pprod -Psql -pl storage/sql -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)

.PHONY: build-kafkasql-native ## Builds kafkasql storage variant native executable. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-kafkasql-native:
	./mvnw package -Pnative -Dquarkus.native.container-build=true -Pprod -Pkafkasql -pl storage/kafkasql -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)

.PHONY: build-tenant-manager ## Builds tenant manager natively [SKIP_TESTS, BUILD_FLAGS]
build-tenant-manager:
	@echo "----------------------------------------------------------------------"
	@echo "                 Building Tenant Manager                              "
	@echo "----------------------------------------------------------------------"
	./mvnw package -Pnative -Dquarkus.native.container-build=true -Pprod -Pmultitenancy -pl 'multitenancy/tenant-manager-api' -DskipTests=$(SKIP_TESTS) $(BUILD_FLAGS)



.PHONY: build-mem-image ## Builds docker image for 'in-memory' storage variant. Variables available for override [MEM_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
build-mem-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For In-Memory Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DISTRO_DOCKER_WORKSPACE)/$(MEM_DOCKERFILE) -t $(IMAGE_REPO)/apicurio/apicurio-registry-mem:$(IMAGE_TAG) $(DOCKER_BUILD_WORKSPACE)


.PHONY: push-mem-image ## Pushes docker image for 'in-memory' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-mem-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For In-Memory Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/apicurio/apicurio-registry-mem:$(IMAGE_TAG)



.PHONY: build-sql-image ## Builds docker image for 'sql' storage variant. Variables available for override [SQL_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
build-sql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For SQL Storage Variant "
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DISTRO_DOCKER_WORKSPACE)/$(SQL_DOCKERFILE) -t $(IMAGE_REPO)/apicurio/apicurio-registry-sql:$(IMAGE_TAG) $(DOCKER_BUILD_WORKSPACE)

.PHONY: push-sql-image ## Pushes docker image for 'sql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-sql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For SQL Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/apicurio/apicurio-registry-sql:$(IMAGE_TAG)

.PHONY: build-sql-native-image ## Builds docker image for 'sql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-sql-native-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For SQL Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DISTRO_DOCKER_WORKSPACE)/Dockerfile.native -t $(IMAGE_REPO)/apicurio/apicurio-registry-sql-native:$(IMAGE_TAG) storage/sql

.PHONY: push-sql-native-image ## Pushes docker image for 'sql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-sql-native-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For SQL Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/apicurio/apicurio-registry-sql-native:$(IMAGE_TAG)

.PHONY: build-kafkasql-image ## Builds docker image for kafkasql storage variant. Variables available for override [KAFKASQL_DOCKERFILE, IMAGE_REPO, IMAGE_TAG, DOCKER_BUILD_WORKSPACE]
build-kafkasql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For Kafkasql Storage Variant "
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DISTRO_DOCKER_WORKSPACE)/$(KAFKASQL_DOCKERFILE) -t $(IMAGE_REPO)/apicurio/apicurio-registry-kafkasql:$(IMAGE_TAG) $(DOCKER_BUILD_WORKSPACE)


.PHONY: push-kafkasql-image ## Pushes docker image for 'kafkasql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-kafkasql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For Kafkasql Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/apicurio/apicurio-registry-kafkasql:$(IMAGE_TAG)

.PHONY: build-kafkasql-native-image ## Builds docker image for kafkasql storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-kafkasql-native-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For Kafkasql Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DISTRO_DOCKER_WORKSPACE)/Dockerfile.native -t $(IMAGE_REPO)/apicurio/apicurio-registry-kafkasql-native:$(IMAGE_TAG) storage/kafkasql


.PHONY: push-kafkasql-native-image ## Pushes docker image for 'kafkasql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-kafkasql-native-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For Kafkasql Storage Variant (using Native Executable)"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/apicurio/apicurio-registry-kafkasql-native:$(IMAGE_TAG)

.PHONY: build-tenant-manager-image ## Builds native docker image for tenant manager. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-tenant-manager-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Native Image For Tenant Manager"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)-native"
	@echo "------------------------------------------------------------------------"
	docker build -f multitenancy/tenant-manager-api/src/main/docker/Dockerfile.native -t $(IMAGE_REPO)/apicurio/apicurio-registry-tenant-manager-api:$(IMAGE_TAG) ./multitenancy/tenant-manager-api/


.PHONY: push-tenant-manager-image ## Pushes docker image for tenant-manager-api. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-tenant-manager-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For Tenant Manager API"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/apicurio/apicurio-registry-tenant-manager-api:$(IMAGE_TAG)

.PHONY: build-all-images ## Builds all the Images. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-all-images: build-mem-image build-sql-image build-kafkasql-image

.PHONY: push-all-images ## Pushes all the Images. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-all-images: push-mem-image push-sql-image push-kafkasql-image


.PHONY: pr-check ## Builds and runs basic tests for multitenant registry pipelines
pr-check:
	mvn clean install -Pno-docker -Dskip.yarn -Pprod -Psql -Pmultitenancy -am -pl storage/sql,multitenancy/tenant-manager-api \
		-Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false
	NO_DOCKER=true mvn verify -Pintegration-tests -Pmultitenancy -Psql -am -pl integration-tests/testsuite \
		-Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false

.PHONY: build-project ## Builds the components for multitenant registry pipelines
build-project:
	mvn clean install -Pprod -Pno-docker -Dskip.yarn -Psql -Pmultitenancy -Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false

# Please declare your targets as .PHONY in the format shown below, so that the 'make help' parses the information correctly.
# 
# .PHONY: <target-name>  ## Description of what target does
