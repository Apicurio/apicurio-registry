# Run `make` for a list of targets, and `make config-show` to show the current configuration.

########## Config

VERSION ?= $(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
LC_VERSION ?= $(shell echo "$(VERSION)" | tr A-Z a-z)
LC_VERSION_SUFFIX ?= $(shell echo "$(LC_VERSION)" | sed -n 's/[^-]*\(-.*\)/\1/p')

BUILD_OPTS ?=

SKIP_TESTS ?= false
ifneq ($(SKIP_TESTS),true)
	override BUILD_OPTS += -DskipOperatorTests=false
endif

BUILDX_PLATFORMS ?= linux/amd64,linux/arm64,linux/s390x,linux/ppc64le

IMAGE_REGISTRY ?= quay.io/apicurio
IMAGE_NAME ?= apicurio-registry-operator
IMAGE_TAG ?= $(LC_VERSION)
IMAGE ?= $(IMAGE_REGISTRY)/$(IMAGE_NAME):$(IMAGE_TAG)
ADDITIONAL_IMAGE ?= $(IMAGE_REGISTRY)/$(IMAGE_NAME):$(ADDITIONAL_IMAGE_TAG)

REGISTRY_APP_IMAGE ?= quay.io/apicurio/apicurio-registry:latest-snapshot
REGISTRY_UI_IMAGE ?= quay.io/apicurio/apicurio-registry-ui:latest-snapshot

NAMESPACE ?= default

### Install

INSTALL_NAMESPACE ?= PLACEHOLDER_NAMESPACE
INSTALL_FILE ?= install/apicurio-registry-operator-$(VERSION).yaml

### Bundle

BUNDLE_DIR ?= deploy/bundle/$(LC_VERSION)
PACKAGE_NAME ?= apicurio-registry
CHANNELS ?= 1.x
DEFAULT_CHANNEL ?= $(CHANNELS)
BUNDLE_OPTS ?= --package $(PACKAGE_NAME) --channels=$(CHANNELS) --default-channel=$(DEFAULT_CHANNEL)
# TODO Replaces:
# PREVIOUS_PACKAGE_VERSION =
# REPLACES = apicurio-registry.v$(PREVIOUS_PACKAGE_VERSION)

BUNDLE_IMAGE_NAME ?= $(IMAGE_NAME)-bundle
BUNDLE_IMAGE ?= $(IMAGE_REGISTRY)/$(BUNDLE_IMAGE_NAME):$(IMAGE_TAG)
ADDITIONAL_BUNDLE_IMAGE ?= $(IMAGE_REGISTRY)/$(BUNDLE_IMAGE_NAME):$(ADDITIONAL_BUNDLE_TAG)

### Catalog

CATALOG_NAMESPACE ?= openshift-marketplace

CATALOG_DIR ?= deploy/catalog
CATALOG_IMAGE_NAME ?= $(IMAGE_NAME)-catalog
CATALOG_IMAGE_TAG ?= v3-latest$(LC_VERSION_SUFFIX)
ADDITIONAL_CATALOG_IMAGE_TAG ?= $(shell date --utc +'%Y-%m-%d-%H-%M')
CATALOG_IMAGE ?= $(IMAGE_REGISTRY)/$(CATALOG_IMAGE_NAME):$(CATALOG_IMAGE_TAG)
ADDITIONAL_CATALOG_IMAGE ?= $(IMAGE_REGISTRY)/$(CATALOG_IMAGE_NAME):$(ADDITIONAL_CATALOG_IMAGE_TAG)

### Other

ifeq ($(OS),Windows_NT)
	OS = win32
	ifeq ($(PROCESSOR_ARCHITECTURE),AMD64)
		ARCH = amd64
	endif
	ifeq ($(PROCESSOR_ARCHITECTURE),x86)
		ARCH = ia32
	endif
else
	UNAME_S := $(shell uname -s)
	ifeq ($(UNAME_S),Linux)
		OS = linux
	endif
	ifeq ($(UNAME_S),Darwin)
		OS = osx
	endif
	UNAME_M := $(shell uname -m)
	ifeq ($(UNAME_M),x86_64)
		ARCH = amd64
	endif
	ifneq ($(filter %86,$(UNAME_M)),)
		ARCH = ia32
	endif
	ifneq ($(filter arm%,$(UNAME_M)),)
		ARCH = arm
	endif
endif

DATE=$(shell date -Idate)

SHELL = /usr/bin/env bash -o pipefail
.SHELLFLAGS = -ec

CC_RED = $(shell echo -e "\033[0;31m")
CC_YELLOW = $(shell echo -e "\033[0;33m")
CC_CYAN = $(shell echo -e "\033[0;36m")
CC_END = $(shell echo -e "\033[0m")


########## Help


.DEFAULT_GOAL = help
.PHONY: help
help: ## TODO
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "$(CC_CYAN)%-30s$(CC_END) %s\n", $$1, $$2}'



.PHONY: config-show
config-show: ## TODO
	@echo "$(CC_YELLOW)==============================================$(CC_END)"
	@echo "Configuration properties:"
	@echo ""
	@echo "$(CC_CYAN)Operator$(CC_END)"
	@echo "VERSION=$(VERSION)"
	@echo "LC_VERSION=$(LC_VERSION)"
ifneq ($(LC_VERSION_SUFFIX),)
	@echo "LC_VERSION_SUFFIX=$(LC_VERSION_SUFFIX)"
endif
	@echo "NAMESPACE=$(NAMESPACE)"
	@echo "SKIP_TESTS=$(SKIP_TESTS)"
	@echo "BUILD_OPTS=$(BUILDS_OPTS)"
	@echo "IMAGE=$(IMAGE)"
ifneq ($(ADDITIONAL_IMAGE),)
ifneq ($(ADDITIONAL_IMAGE_TAG),)
	@echo "ADDITIONAL_IMAGE=$(ADDITIONAL_IMAGE)"
endif
endif
	@echo ""
	@echo "$(CC_CYAN)Operand$(CC_END)"
	@echo "REGISTRY_APP_IMAGE=$(REGISTRY_APP_IMAGE)"
	@echo "REGISTRY_UI_IMAGE=$(REGISTRY_UI_IMAGE)"
	@echo ""
	@echo "$(CC_CYAN)Bundle$(CC_END)"
	@echo "PACKAGE_NAME=$(PACKAGE_NAME)"
	@echo "CHANNELS=$(CHANNELS)"
	@echo "DEFAULT_CHANNEL=$(DEFAULT_CHANNEL)"
	@echo "BUNDLE_IMAGE=$(BUNDLE_IMAGE)"
ifneq ($(ADDITIONAL_BUNDLE_IMAGE),)
ifneq ($(ADDITIONAL_BUNDLE_IMAGE_TAG),)
	@echo "ADDITIONAL_BUNDLE_IMAGE=$(ADDITIONAL_BUNDLE_IMAGE)"
endif
endif
	@echo ""
	@echo "$(CC_CYAN)Catalog$(CC_END)"
	@echo "CATALOG_IMAGE=$(CATALOG_IMAGE)"
ifneq ($(ADDITIONAL_CATALOG_IMAGE),)
ifneq ($(ADDITIONAL_CATALOG_IMAGE_TAG),)
	@echo "ADDITIONAL_CATALOG_IMAGE=$(ADDITIONAL_CATALOG_IMAGE)"
endif
endif
	@echo ""
	@echo "$(CC_CYAN)Other$(CC_END)"
	@echo "BUILDX_PLATFORMS=$(BUILDX_PLATFORMS)"
	@echo "OS=$(OS)"
	@echo "ARCH=$(ARCH)"
	@echo "DATE=$(DATE)"
	@echo "$(CC_YELLOW)==============================================$(CC_END)"


########## Tools


.PHONY: install-opm
OPM_VERSION ?= 1.29.0
OPM = $(shell pwd)/bin/opm
install-opm: ## Install opm v1.29.0
	@{ \
		if [ ! -f $(OPM) ]; \
		then \
			mkdir -p $(dir $(OPM)); \
			curl -sSLo $(OPM) "https://github.com/operator-framework/operator-registry/releases/download/v$(OPM_VERSION)/$(OS)-$(ARCH)-opm"; \
			chmod +x $(OPM); \
		fi; \
	}


YQ_VERSION ?= 4.27.5
YQ = $(shell pwd)/bin/yq
.PHONY: install-yq
install-yq: ## Install yq v4.27.5
	@{ \
		if [ ! -f $(YQ) ]; \
		then \
			mkdir -p $(dir $(YQ)); \
			curl -sSLo $(YQ) "https://github.com/mikefarah/yq/releases/download/v$(YQ_VERSION)/yq_$(OS)_$(ARCH)"; \
			chmod +x $(YQ); \
		fi; \
	}


OPERATOR_SDK_VERSION ?= 1.14.0
OPERATOR_SDK = $(shell pwd)/bin/operator-sdk
.PHONY: install-operator-sdk
install-operator-sdk: ## Install operator-sdk v1.14.0
	@{ \
		if [ ! -f $(OPERATOR_SDK) ]; \
		then \
			mkdir -p $(dir $(OPERATOR_SDK)); \
			curl -sSLo $(OPERATOR_SDK) "https://github.com/operator-framework/operator-sdk/releases/download/v$(OPERATOR_SDK_VERSION)/operator-sdk_$(OS)_$(ARCH)"; \
			chmod +x $(OPERATOR_SDK); \
		fi; \
	}


KUSTOMIZE_VERSION ?= 5.4.3
KUSTOMIZE = $(shell pwd)/bin/kustomize
.PHONY: install-kustomize
install-kustomize: ## Install kustomize v5.4.3
	@{ \
		if [ ! -f $(KUSTOMIZE) ]; \
		then \
			mkdir -p $(dir $(KUSTOMIZE)); \
			cd $(shell pwd)/bin; \
			curl -sSLo $(KUSTOMIZE).tar.gz "https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize/v$(KUSTOMIZE_VERSION)/kustomize_v$(KUSTOMIZE_VERSION)_$(OS)_$(ARCH).tar.gz"; \
			tar -xf $(KUSTOMIZE).tar.gz; \
			rm $(KUSTOMIZE).tar.gz; \
			chmod +x $(KUSTOMIZE); \
		fi; \
	}


KIND_VERSION ?= 0.23.0
KIND = $(shell pwd)/bin/kind
.PHONY: install-kind
install-kind: ## Install kind v0.23.0
	@{ \
		if [ ! -f $(KIND) ]; \
		then \
			mkdir -p $(dir $(KIND)); \
			curl -sSLo $(KIND) "https://kind.sigs.k8s.io/dl/v$(KIND_VERSION)/kind-$(OS)-$(ARCH)"; \
			chmod +x $(KIND); \
		fi; \
	}


########## Targets


.PHONY: build
build: ## TODO
	mvn clean install $(BUILD_OPTS)


.PHONY: image-buildx-build-push
image-buildx-build-push: ## TODO
ifneq ($(ADDITIONAL_IMAGE),)
ifneq ($(ADDITIONAL_IMAGE_TAG),)
	docker buildx build --push --platform $(BUILDX_PLATFORM) -f operator/src/main/docker/Dockerfile.jvm -t $(IMAGE) -t $(ADDITIONAL_IMAGE) operator/target
else
	docker buildx build --push --platform $(BUILDX_PLATFORM) -f operator/src/main/docker/Dockerfile.jvm -t $(IMAGE) operator/target
endif
else
	docker buildx build --push --platform $(BUILDX_PLATFORM) -f operator/src/main/docker/Dockerfile.jvm -t $(IMAGE) operator/target
endif


.PHONY: image-build
image-build: ## TODO
	docker build -f operator/src/main/docker/Dockerfile.jvm -t $(IMAGE) operator/target
ifneq ($(ADDITIONAL_IMAGE),)
ifneq ($(ADDITIONAL_IMAGE_TAG),)
	docker tag $(IMAGE) $(ADDITIONAL_IMAGE)
endif
endif


.PHONY: image-push
image-push: ## TODO
	docker push $(IMAGE)
ifneq ($(ADDITIONAL_IMAGE),)
ifneq ($(ADDITIONAL_IMAGE_TAG),)
	docker push $(ADDITIONAL_IMAGE)
endif
endif


.PHONY: prepare-resources
prepare-resources: install-yq install-kustomize
	$(YQ) '.commonLabels."app.kubernetes.io/version" = "$(VERSION)"' -i deploy/crd/kustomization.yaml
	$(YQ) '.commonLabels."app.kubernetes.io/version" = "$(VERSION)"' -i deploy/rbac/cluster/kustomization.yaml
	$(YQ) '.commonLabels."app.kubernetes.io/version" = "$(VERSION)"' -i deploy/rbac/single-namespace/kustomization.yaml
	# Operator
	cd deploy/operator && $(KUSTOMIZE) edit set image PLACEHOLDER_IMAGE=$(IMAGE)
	$(YQ) '.commonLabels."app.kubernetes.io/version" = "$(VERSION)"' -i deploy/operator/kustomization.yaml
	$(YQ) '.patches[0].patch = (.patches[0].patch | from_yaml | with(.[]; select(.value.name == "REGISTRY_VERSION") | .value.value = "$(VERSION)") | to_yaml)' -i deploy/operator/kustomization.yaml
	$(YQ) '.patches[0].patch = (.patches[0].patch | from_yaml | with(.[]; select(.value.name == "REGISTRY_APP_IMAGE") | .value.value = "$(REGISTRY_APP_IMAGE)") | to_yaml)' -i deploy/operator/kustomization.yaml
	$(YQ) '.patches[0].patch = (.patches[0].patch | from_yaml | with(.[]; select(.value.name == "REGISTRY_UI_IMAGE") | .value.value = "$(REGISTRY_UI_IMAGE)") | to_yaml)' -i deploy/operator/kustomization.yaml
	# Install
	cd deploy/install && $(KUSTOMIZE) edit set namespace $(NAMESPACE)


.PHONY: prepare-csv
prepare-csv: install-yq
	$(YQ) '.metadata.annotations.createdAt = "$(DATE)"' -i deploy/csv/apicurio-registry.clusterserviceversion.yaml
	$(YQ) '.metadata.annotations.containerImage = "$(IMAGE)"' -i deploy/csv/apicurio-registry.clusterserviceversion.yaml
	# TODO:
	#$(YQ) '.spec.replaces = "$(REPLACES)"' -i deploy/csv/apicurio-registry.clusterserviceversion.yaml


.PHONY: deploy
deploy: prepare-resources ## Deploy the Operator to a cluster using kubectl
	kubectl create namespace $(NAMESPACE) || true
	$(KUSTOMIZE) build deploy/install | kubectl -n $(NAMESPACE) apply -f -


.PHONY: undeploy
undeploy: prepare-resources ## TODO
	$(KUSTOMIZE) build deploy/install | kubectl -n $(NAMESPACE) delete -f -


.PHONY: dist-install-file
dist-install-file: prepare-resources ## TODO
	cd deploy/install && $(KUSTOMIZE) edit set namespace $(INSTALL_NAMESPACE)
	$(KUSTOMIZE) build deploy/install >$(INSTALL_FILE)


.PHONY: dist
dist: dist-install-file ## TODO
	mkdir -p dist
	cp -rt dist dist-template/*
	# TODO: Licenses
	# Examples
	cp -t dist/examples deploy/examples/*
	rm dist/examples/kustomization.yaml
	# Install
	cp $(INSTALL_FILE) dist/install.yaml
	# Archive
	tar -zcf apicurio-registry-operator-$(VERSION).tar.gz -C dist .


.PHONY: quickstart
quickstart: build image-build image-push deploy ## TODO


.PHONY: bundle-build
bundle-build: install-operator-sdk prepare-csv prepare-resources ## Generate bundle metadata
	$(KUSTOMIZE) build deploy/csv | $(OPERATOR_SDK) generate bundle -q --kustomize-dir deploy/csv --overwrite --version $(LC_VERSION) --output-dir $(BUNDLE_DIR) $(BUNDLE_OPTS)
	# Workaround for https://github.com/operator-framework/operator-lifecycle-manager/issues/1608
	# See https://github.com/operator-framework/operator-lifecycle-manager/issues/952#issuecomment-639657949
	$(YQ) '.spec.install.spec.deployments[0].name = .spec.install.spec.deployments[0].name + "-v$(LC_VERSION)"' -i $(BUNDLE_DIR)/manifests/apicurio-registry.clusterserviceversion.yaml
	# Post-process annotations.yaml
	$(YQ) '... comments = ""' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) '.annotations = (.annotations | to_entries | del(.[] | select(.key == "operators.operatorframework.io.metrics.*")) | from_entries)' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) '.annotations = (.annotations | to_entries | del(.[] | select(.key == "operators.operatorframework.io.test.*")) | from_entries)' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) '.annotations."com.redhat.openshift.versions" = "v4.12"' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) 'sort_keys(..)' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	# Post-process bundle.Dockerfile
	mv bundle.Dockerfile $(BUNDLE_DIR)
	sed -i '\|^# .*$$|d' $(BUNDLE_DIR)/bundle.Dockerfile
	sed -i '\|^LABEL operators\.operatorframework.io\.metrics\..*$$|d' $(BUNDLE_DIR)/bundle.Dockerfile
	sed -i '\|^LABEL operators\.operatorframework.io\.test\..*$$|d' $(BUNDLE_DIR)/bundle.Dockerfile
	sed -i '\|^COPY .*tests\/scorecard.*$$|d' $(BUNDLE_DIR)/bundle.Dockerfile
	sed -i 's|^FROM scratch$$|FROM scratch\n\nLABEL com.redhat.openshift.versions=v4.12|g' $(BUNDLE_DIR)/bundle.Dockerfile
	# Validate
	$(OPERATOR_SDK) bundle validate $(BUNDLE_DIR)


.PHONY: bundle-image-build
bundle-image-build: ## TODO
	docker build -f $(BUNDLE_DIR)/bundle.Dockerfile -t $(BUNDLE_IMAGE) .
ifneq ($(ADDITIONAL_BUNDLE_IMAGE),)
ifneq ($(ADDITIONAL_BUNDLE_TAG),)
	docker tag $(BUNDLE_IMAGE) $(ADDITIONAL_BUNDLE_IMAGE)
endif
endif


.PHONY: bundle-image-push
bundle-image-push: ## TODO
	docker push $(BUNDLE_IMAGE)
ifneq ($(ADDITIONAL_BUNDLE_IMAGE),)
ifneq ($(ADDITIONAL_BUNDLE_TAG),)
	docker push $(ADDITIONAL_BUNDLE_IMAGE)
endif
endif


.PHONY: bundle
bundle: bundle-build bundle-image-build bundle-image-push ## TODO


.PHONY: catalog-build
catalog-build: install-opm ## TODO
	# Create catalog.yaml
	mkdir -p $(CATALOG_DIR)/configs
	cp $(CATALOG_DIR)/catalog.template.yaml $(CATALOG_DIR)/catalog.yaml
	# Post-process catalog.yaml
	sed -i 's|PLACEHOLDER_LC_VERSION|$(LC_VERSION)|g' $(CATALOG_DIR)/catalog.yaml
	sed -i 's|PLACEHOLDER_BUNDLE_IMAGE|$(BUNDLE_IMAGE)|g' $(CATALOG_DIR)/catalog.yaml
	# Build
	cd $(CATALOG_DIR) && $(OPM) alpha render-template basic catalog.yaml --output=yaml >configs/index.yaml
	cd $(CATALOG_DIR) && $(OPM) generate dockerfile configs


.PHONY: catalog-image-build
catalog-image-build: ## TODO
	cd $(CATALOG_DIR) && docker build -f configs.Dockerfile -t $(CATALOG_IMAGE) .
ifneq ($(ADDITIONAL_CATALOG_IMAGE),)
ifneq ($(ADDITIONAL_CATALOG_IMAGE_TAG),)
	docker tag $(CATALOG_IMAGE) $(ADDITIONAL_CATALOG_IMAGE)
endif
endif


.PHONY: catalog-image-push
catalog-image-push: ## TODO
	docker push $(CATALOG_IMAGE)
ifneq ($(ADDITIONAL_CATALOG_IMAGE),)
ifneq ($(ADDITIONAL_CATALOG_IMAGE_TAG),)
	docker push $(ADDITIONAL_CATALOG_IMAGE)
endif
endif


.PHONY: catalog
catalog: catalog-build catalog-image-build catalog-image-push ## TODO


.PHONY: prepare-catalog-deploy
prepare-catalog-deploy: install-yq
	kubectl create namespace $(CATALOG_NAMESPACE) || true
	# Post-process
	$(YQ) '.metadata.namespace = "$(CATALOG_NAMESPACE)"' -i $(CATALOG_DIR)/catalog-source.yaml
	$(YQ) '.spec.image = "$(CATALOG_IMAGE)"' -i $(CATALOG_DIR)/catalog-source.yaml


.PHONY: catalog-deploy
catalog-deploy: prepare-catalog-deploy ## TODO
	kubectl -n $(CATALOG_NAMESPACE) apply -f $(CATALOG_DIR)/catalog-source.yaml


.PHONY: catalog-undeploy
catalog-undeploy: prepare-catalog-deploy ## TODO
	kubectl -n $(CATALOG_NAMESPACE) delete -f $(CATALOG_DIR)/catalog-source.yaml


.PHONY: prepare-catalog-subscription-deploy
prepare-catalog-subscription-deploy: install-yq
	kubectl create namespace $(NAMESPACE) || true
	# Post-process
	$(YQ) '.metadata.namespace = "$(NAMESPACE)"' -i $(CATALOG_DIR)/subscription.yaml
	$(YQ) '.spec.sourceNamespace = "$(CATALOG_NAMESPACE)"' -i $(CATALOG_DIR)/subscription.yaml


.PHONY: catalog-subscription-deploy
catalog-subscription-deploy: prepare-catalog-subscription-deploy ## TODO
	kubectl -n $(NAMESPACE) apply -f $(CATALOG_DIR)/subscription.yaml


.PHONY: catalog-subscription-undeploy
catalog-subscription-undeploy: prepare-catalog-subscription-deploy ## TODO
	kubectl -n $(NAMESPACE) delete -f $(CATALOG_DIR)/subscription.yaml
	kubectl -n $(NAMESPACE) delete clusterserviceversion apicurio-registry.v$(LC_VERSION)


.PHONY: clean
clean: ## TODO
	mvn clean
	# Remove ignored files
	rm apicurio-registry-operator-*.tar.gz || true
	rm -rf bin || true
	rm bundle.Dockerfile || true
	rm cluster.crt || true
	rm cluster.truststore || true
	rm -rf deploy/bundle || true
	rm deploy/catalog/catalog.yaml || true
	rm deploy/catalog/configs.Dockerfile || true
	rm -rf deploy/catalog/configs || true
	rm deploy/crd/apicurioregistries3.registry.apicur.io-v1.yml || true
	rm -rf dist || true
	rm install/*-SNAPSHOT.yaml || true
	# Reset automatic modifications
	git checkout HEAD -- deploy/catalog/catalog-source.yaml || true
	git checkout HEAD -- deploy/catalog/subscription.yaml || true
	git checkout HEAD -- deploy/csv/apicurio-registry.clusterserviceversion.yaml || true
	git checkout HEAD -- deploy/crd/kustomization.yaml || true
	git checkout HEAD -- deploy/install/kustomization.yaml || true
	git checkout HEAD -- deploy/operator/kustomization.yaml || true
	git checkout HEAD -- deploy/rbac/cluster/kustomization.yaml || true
	git checkout HEAD -- deploy/rbac/single-namespace/kustomization.yaml || true


.PHONY: prepare-dev-openshift
prepare-dev-openshift: install-kustomize
	$(KUSTOMIZE) build deploy/crd | kubectl -n $(NAMESPACE) apply -f -
	rm cluster.crt || true
	rm cluster.truststore || true
	kubectl -n openshift-kube-apiserver get secret external-loadbalancer-serving-certkey -o jsonpath='{.data.tls\.crt}' | base64 -d >cluster.crt
	keytool -import -alias cluster -file cluster.crt -storetype JKS -keystore cluster.truststore -noprompt -storepass password
	@echo "$(CC_YELLOW)==============================================$(CC_END)"
	@echo "Run:"
	@echo "$(CC_CYAN)java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -Djavax.net.ssl.trustStore=cluster.truststore -Djavax.net.ssl.trustStorePassword=password -Dapicurio.log.level=debug -jar operator/target/quarkus-app/quarkus-run.jar$(CC_END)"
	@echo "or"
	@echo "$(CC_CYAN)mvn -Djavax.net.ssl.trustStore=\$$(pwd)/cluster.truststore -Djavax.net.ssl.trustStorePassword=password -Dapicurio.log.level=debug quarkus:dev$(CC_END)"
	@echo "$(CC_YELLOW)==============================================$(CC_END)"


.PHONY: dev
dev: ## TODO
	#mvn -Djavax.net.ssl.trustStore=$(shell pwd)/cluster.truststore -Djavax.net.ssl.trustStorePassword=password -Dapicurio.log.level=debug quarkus:dev
	mvn quarkus:dev


.PHONY: kind-start
kind-start: install-kind ## TODO
	@echo "TODO"
	#$(KIND) create cluster --config deploy/kind/default-cluster.yaml
	#kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
	#kubectl -n ingress-nginx wait --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=90s


.PHONY: kind-stop
kind-stop: install-kind ## TODO
	@echo "TODO"
	#$(KIND) kind delete cluster --name default


.PHONY: format
format: ## TODO
	mvn spotless:apply
