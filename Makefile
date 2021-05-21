# used for mem, sql & kafkasql. Hardcoded for tenant manager api, since the build workspace is different
# 'override' keyword prevents the variable from being overrideen
override DOCKER_BUILD_WORKSPACE := ./distro/docker/target/docker

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



.PHONY: build-tenant-manager ## Builds tenant-manager-api. Variables available for override [SKIP_TESTS, BUILD_FLAGS]
build-tenant-manager:
	@echo "----------------------------------------------------------------------"
	@echo "                     Building Tenant Manager                          "
	@echo "----------------------------------------------------------------------"
	./mvnw clean install -am -Pprod -Pmultitenancy -pl 'multitenancy/tenant-manager-api' -Dskiptests=$(SKIP_TESTS) $(BUILD_FLAGS)



.PHONY: build-tenant-manager-native ## Builds tenant manager natively
build-tenant-manager-native:
	@echo "----------------------------------------------------------------------"
	@echo "                 Building Tenant Manager Natively                     "
	@echo "----------------------------------------------------------------------"
	./mvnw clean install -am -Pprod -Pmultitenancy -pl 'multitenancy/tenant-manager-api' -Pnative -Dquarkus.native.container-build=true



.PHONY: build-mem-image ## Builds docker image for 'in-memory' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-mem-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For In-Memory Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKER_BUILD_WORKSPACE)/Dockerfile.jvm -t $(IMAGE_REPO)/apicurio/apicurio-registry-mem:$(IMAGE_TAG) $(DOCKER_BUILD_WORKSPACE)


.PHONY: push-mem-image ## Pushes docker image for 'in-memory' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-mem-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For In-Memory Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/apicurio/apicurio-registry-mem:$(IMAGE_TAG)



.PHONY: build-sql-image ## Builds docker image for 'sql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-sql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For SQL Storage Variant "
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKER_BUILD_WORKSPACE)/Dockerfile.sql.jvm -t $(IMAGE_REPO)/apicurio/apicurio-registry-sql:$(IMAGE_TAG) $(DOCKER_BUILD_WORKSPACE)


.PHONY: push-sql-image ## Pushes docker image for 'sql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-sql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For SQL Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/apicurio/apicurio-registry-sql:$(IMAGE_TAG)



.PHONY: build-kafkasql-image ## Builds docker image for kafkasql storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-kafkasql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For Kafkasql Storage Variant "
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f $(DOCKER_BUILD_WORKSPACE)/Dockerfile.kafkasql.jvm -t $(IMAGE_REPO)/apicurio/apicurio-registry-kafkasql:$(IMAGE_TAG) $(DOCKER_BUILD_WORKSPACE)


.PHONY: push-kafkasql-image ## Pushes docker image for 'kafkasql' storage variant. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-kafkasql-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For Kafkasql Storage Variant"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/apicurio/apicurio-registry-kafkasql:$(IMAGE_TAG)



.PHONY: build-tenant-manager-image ## Builds docker image for tenant-manager-api. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-tenant-manager-image:
	@echo "------------------------------------------------------------------------"
	@echo " Building Image For Tenant Manager API"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker build -f multitenancy/tenant-manager-api/src/main/docker/Dockerfile.jvm -t $(IMAGE_REPO)/apicurio/apicurio-registry-tenant-manager-api:$(IMAGE_TAG) ./multitenancy/tenant-manager-api/


.PHONY: push-tenant-manager-image ## Pushes docker image for tenant-manager-api. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-tenant-manager-image:
	@echo "------------------------------------------------------------------------"
	@echo " Pushing Image For Tenant Manager API"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)"
	@echo "------------------------------------------------------------------------"
	docker push $(IMAGE_REPO)/apicurio/apicurio-registry-tenant-manager-api:$(IMAGE_TAG)



.PHONY: build-tenant-manager-image-native ## Builds native docker image for tenant manager. Variables available for override [IMAGE_REPO, IMAGE_TAG]
# example image quay.io/famargon/apicurio-registry-tenant-manager-api:native
build-tenant-manager-image-native:
	@echo "------------------------------------------------------------------------"
	@echo " Building Native Image For Tenant Manager"
	@echo " Repository: $(IMAGE_REPO)"
	@echo " Tag: $(IMAGE_TAG)-native"
	@echo "------------------------------------------------------------------------"
	docker build -f multitenancy/tenant-manager-api/src/main/docker/Dockerfile.native -t $(IMAGE_REPO)/apicurio/apicurio-registry-tenant-manager-api:$(IMAGE_TAG)-native ./multitenancy/tenant-manager-api/


.PHONY: build-all-images ## Builds all the Images. Variables available for override [IMAGE_REPO, IMAGE_TAG]
build-all-images: build-mem-image build-sql-image build-kafkasql-image build-tenant-manager-image



.PHONY: push-all-images ## Pushes all the Images. Variables available for override [IMAGE_REPO, IMAGE_TAG]
push-all-images: push-mem-image push-sql-image push-kafkasql-image push-tenant-manager-image



# Please declare your targets as .PHONY in the format shown below, so that the 'make help' parses the information correctly.
# 
# .PHONY: <target-name>  ## Description of what target does
