CONTAINER_IMAGE_TAG ?= latest

tenant-manager-build:
	mvn clean install -am -Pprod -Pmultitenancy -pl 'multitenancy/tenant-manager-api'

tenant-manager-build-native:
	mvn clean install -am -Pprod -Pmultitenancy -pl 'multitenancy/tenant-manager-api' -Pnative -Dquarkus.native.container-build=true

tenant-manager-container:
	docker build -f multitenancy/tenant-manager-api/src/main/docker/Dockerfile.jvm -t apicurio/apicurio-registry-tenant-manager-api:$(CONTAINER_IMAGE_TAG) ./multitenancy/tenant-manager-api/

# example image quay.io/famargon/apicurio-registry-tenant-manager-api:native
tenant-manager-container-native:
	docker build -f multitenancy/tenant-manager-api/src/main/docker/Dockerfile.native -t apicurio/apicurio-registry-tenant-manager-api:$(CONTAINER_IMAGE_TAG) ./multitenancy/tenant-manager-api/

.PHONY: tenant-manager-build tenant-manager-container
