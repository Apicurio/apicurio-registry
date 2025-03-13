# Run `make` for a list of targets, and `make config-show` to show the current configuration.

########## Config

SHELL = /usr/bin/env bash -o pipefail
.SHELLFLAGS = -ec

VERSION ?= $(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
LC_VERSION ?= $(shell echo "$(VERSION)" | tr A-Z a-z)
LC_VERSION_SUFFIX ?= $(shell echo "$(LC_VERSION)" | sed -n 's/[^-]*\(-.*\)/\1/p')

BUILD_OPTS_EXT += $(BUILD_OPTS)

BUILD_OPTS_EXT += -pl controller -am

SKIP_TESTS ?= false
ifneq ($(SKIP_TESTS),true)
	override BUILD_OPTS_EXT += -DskipOperatorTests=false
endif

IMAGE_REGISTRY ?= quay.io/apicurio
IMAGE_NAME ?= apicurio-registry-3-operator
IMAGE_TAG ?= $(LC_VERSION)

IMAGE ?= $(IMAGE_REGISTRY)/$(IMAGE_NAME):$(IMAGE_TAG)
ADDITIONAL_IMAGE ?= $(IMAGE_REGISTRY)/$(IMAGE_NAME):$(ADDITIONAL_IMAGE_TAG)

OPERAND_IMAGE_TAG ?= latest-snapshot
STUDIO_UI_IMAGE_TAG ?= latest-snapshot

REGISTRY_APP_IMAGE ?= quay.io/apicurio/apicurio-registry:$(OPERAND_IMAGE_TAG)
REGISTRY_UI_IMAGE ?= quay.io/apicurio/apicurio-registry-ui:$(OPERAND_IMAGE_TAG)
STUDIO_UI_IMAGE ?= quay.io/apicurio/apicurio-studio-ui:$(STUDIO_UI_IMAGE_TAG)

NAMESPACE ?= default

### Bundle

PACKAGE_NAME ?= apicurio-registry-3
PACKAGE ?= $(PACKAGE_NAME).v$(LC_VERSION)

PREVIOUS_PACKAGE_VERSION ?=
PREVIOUS_PACKAGE ?= $(PACKAGE_NAME).v$(PREVIOUS_PACKAGE_VERSION)
# TODO: Handle `replaces` after first release

CHANNELS ?= 3.x
DEFAULT_CHANNEL ?= $(CHANNELS)

BUNDLE_OPTS ?= --package $(PACKAGE_NAME) --version $(LC_VERSION) --channels=$(CHANNELS) --default-channel=$(DEFAULT_CHANNEL)
BUNDLE_DIR ?= target/bundle/$(PACKAGE_NAME)/$(LC_VERSION)

BUNDLE_IMAGE_NAME ?= $(IMAGE_NAME)-bundle
BUNDLE_IMAGE_TAG ?= $(LC_VERSION)
BUNDLE_IMAGE ?= $(IMAGE_REGISTRY)/$(BUNDLE_IMAGE_NAME):$(BUNDLE_IMAGE_TAG)
ADDITIONAL_BUNDLE_IMAGE ?= $(IMAGE_REGISTRY)/$(BUNDLE_IMAGE_NAME):$(ADDITIONAL_BUNDLE_TAG)

AC_PACKAGE_NAME ?= apicurio-api-controller
AC_PACKAGE_VERSION ?= 0.0.1
AC_PACKAGE ?= $(AC_PACKAGE_NAME).v$(AC_PACKAGE_VERSION)

AC_CHANNELS ?= 0.x
AC_DEFAULT_CHANNEL ?= $(AC_CHANNELS)

AC_BUNDLE_OPTS ?= --package $(AC_PACKAGE_NAME) --version $(AC_PACKAGE_VERSION) --channels=$(AC_CHANNELS) --default-channel=$(AC_DEFAULT_CHANNEL)
AC_BUNDLE_DIR ?= target/bundle/$(AC_PACKAGE_NAME)/$(AC_PACKAGE_VERSION)

AC_BUNDLE_IMAGE ?= $(IMAGE_REGISTRY)/api-controller-operator-bundle:$(AC_PACKAGE_VERSION)

### Catalog

CATALOG_NAMESPACE ?= olm

CATALOG_DIR ?= olm-tests/src/test/deploy/catalog
CATALOG_TARGET_DIR ?= target/catalog

CATALOG_IMAGE_NAME ?= $(IMAGE_NAME)-catalog
CATALOG_IMAGE_TAG ?= $(LC_VERSION)
CATALOG_IMAGE ?= $(IMAGE_REGISTRY)/$(CATALOG_IMAGE_NAME):$(CATALOG_IMAGE_TAG)
ADDITIONAL_CATALOG_IMAGE ?= $(IMAGE_REGISTRY)/$(CATALOG_IMAGE_NAME):$(ADDITIONAL_CATALOG_IMAGE_TAG)

AC_CATALOG_DIR ?= olm-tests/src/test/deploy/api-controller-catalog
AC_CATALOG_TARGET_DIR ?= target/apicurio-api-controller-catalog

AC_CATALOG_IMAGE_TAG ?= 0.0.1
AC_CATALOG_IMAGE ?= $(IMAGE_REGISTRY)/apicurio-api-controller-operator-catalog:$(CATALOG_IMAGE_TAG)

### Install

INSTALL_NAMESPACE ?= PLACEHOLDER_NAMESPACE
INSTALL_FILE ?= install/apicurio-registry-operator-$(VERSION).yaml

DIST_TARGET_DIR ?= target/dist

### Other

CURRENT_DIR = $(shell pwd)

OS ?= linux
ARCH ?= amd64

DATE=$(shell date -Idate)

CC_RED = $(shell echo -e "\033[0;31m")
CC_YELLOW = $(shell echo -e "\033[0;33m")
CC_CYAN = $(shell echo -e "\033[0;36m")
CC_END = $(shell echo -e "\033[0m")

### Kustomize variables

export PLACEHOLDER_NAMESPACE := $(NAMESPACE)

export PLACEHOLDER_VERSION := $(VERSION)
export PLACEHOLDER_LC_VERSION := $(LC_VERSION)

export PLACEHOLDER_IMAGE := $(IMAGE)
export PLACEHOLDER_REGISTRY_APP_IMAGE := $(REGISTRY_APP_IMAGE)
export PLACEHOLDER_REGISTRY_UI_IMAGE := $(REGISTRY_UI_IMAGE)
export PLACEHOLDER_STUDIO_UI_IMAGE := $(STUDIO_UI_IMAGE)

export PLACEHOLDER_DATE := $(DATE)

export PLACEHOLDER_PACKAGE_NAME := $(PACKAGE_NAME)
export PLACEHOLDER_PACKAGE := $(PACKAGE)
export PLACEHOLDER_BUNDLE_IMAGE := $(BUNDLE_IMAGE)

export PLACEHOLDER_CATALOG_NAMESPACE := $(CATALOG_NAMESPACE)
export PLACEHOLDER_CATALOG_IMAGE := $(CATALOG_IMAGE)


export PLACEHOLDER_AC_PACKAGE_NAME := $(AC_PACKAGE_NAME)
export PLACEHOLDER_AC_PACKAGE := $(AC_PACKAGE)
export PLACEHOLDER_AC_BUNDLE_IMAGE := $(AC_BUNDLE_IMAGE)

export PLACEHOLDER_AC_CATALOG_IMAGE := $(AC_CATALOG_IMAGE)

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
	@echo "STUDIO_UI_IMAGE=$(STUDIO_UI_IMAGE)"
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
	@echo "OS=$(OS)"
	@echo "ARCH=$(ARCH)"
	@echo "DATE=$(DATE)"
	@echo "$(CC_YELLOW)==============================================$(CC_END)"


########## Tools


define target_dir
    @{ \
		if [ -d $(1) ]; \
		then \
			if  [[ $(1) == target/* ]]; \
			then \
				echo "Cleaning $(1)"; \
				rm -rf "$(1)"; \
			fi; \
		fi; \
		mkdir -p $(1); \
   	}
endef


OPERATOR_SDK_VERSION ?= 1.37.0
OPERATOR_SDK = $(shell pwd)/target/bin/operator-sdk
.PHONY: install-operator-sdk
install-operator-sdk: ## Install operator-sdk v1.37.0
	@{ \
		if [ ! -f $(OPERATOR_SDK) ]; \
		then \
			mkdir -p $(dir $(OPERATOR_SDK)); \
			curl -sSLo $(OPERATOR_SDK) "https://github.com/operator-framework/operator-sdk/releases/download/v$(OPERATOR_SDK_VERSION)/operator-sdk_$(OS)_$(ARCH)"; \
			chmod +x $(OPERATOR_SDK); \
		fi; \
	}


.PHONY: install-opm
OPM_VERSION ?= 1.48.0
OPM = $(CURRENT_DIR)/target/bin/opm
install-opm: ## Install opm v1.48.0
	@{ \
		if [ ! -f $(OPM) ]; \
		then \
			mkdir -p $(dir $(OPM)); \
			curl -sSLo $(OPM) "https://github.com/operator-framework/operator-registry/releases/download/v$(OPM_VERSION)/$(OS)-$(ARCH)-opm"; \
			chmod +x $(OPM); \
		fi; \
	}


YQ_VERSION ?= 4.27.5
YQ = $(CURRENT_DIR)/target/bin/yq
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


########## Targets


.PHONY: build
build: ## TODO
	mvn clean install $(BUILD_OPTS_EXT)


.PHONY: remote-tests-all
remote-tests-all: ## TODO
	# Do not run `mvn clean` when not necessary.
ifeq ($(REMOTE_TESTS_ALL_INSTALL_FILE),)
	$(MAKE) INSTALL_FILE=controller/target/test-install.yaml dist-install-file
else
	cp $(REMOTE_TESTS_ALL_INSTALL_FILE) controller/target/test-install.yaml
endif
	mvn verify $(subst -pl controller -am,,$(BUILD_OPTS_EXT)) -Dtest.operator.deployment=remote -Dtest.operator.catalog-image=$(CATALOG_IMAGE)


.PHONY: image-build
image-build: ## TODO
	docker build -f controller/src/main/docker/Dockerfile.jvm -t $(IMAGE) controller/target
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


.PHONY: image
image: image-build image-push ## TODO


.PHONY: deploy
deploy: ## Deploy the Operator to a cluster using kubectl
	kubectl create namespace $(NAMESPACE) || true
	kubectl kustomize controller/src/main/deploy/install | envsubst | kubectl -n $(NAMESPACE) apply -f -


.PHONY: undeploy
undeploy: ## TODO
	kubectl kustomize controller/src/main/deploy/install | envsubst | kubectl -n $(NAMESPACE) delete -f - || true


.PHONY: dist-install-file
dist-install-file: ## TODO
	mkdir -p $(dir $(INSTALL_FILE))
	export PLACEHOLDER_NAMESPACE=$(INSTALL_NAMESPACE) && \
	kubectl kustomize controller/src/main/deploy/install | envsubst >$(INSTALL_FILE)


.PHONY: dist
dist: dist-install-file ## TODO
	$(call target_dir,$(DIST_TARGET_DIR))
	cp -rt $(DIST_TARGET_DIR) controller/src/main/deploy/dist-template/*
	# Licenses
	cp -t $(DIST_TARGET_DIR) $(CURRENT_DIR)/../LICENSE
	mkdir -p $(DIST_TARGET_DIR)/licenses
	cp -t $(DIST_TARGET_DIR)/licenses target/generated-resources/licenses.xml
	sed -i '\|^.*<file>.*</file>.*$$|d' $(DIST_TARGET_DIR)/licenses/licenses.xml
	# Examples
	mkdir -p $(DIST_TARGET_DIR)/examples
	cp -t $(DIST_TARGET_DIR)/examples controller/src/main/deploy/examples/*
	rm $(DIST_TARGET_DIR)/examples/kustomization.yaml
	cp -rt $(DIST_TARGET_DIR)/examples controller/src/main/resources/k8s/examples/*
	cp -rt $(DIST_TARGET_DIR)/examples controller/src/test/resources/k8s/examples/*
	# Install
	mkdir -p $(DIST_TARGET_DIR)/install
	cp $(INSTALL_FILE) $(DIST_TARGET_DIR)/install/install.yaml
	# Archive
	cd $(DIST_TARGET_DIR) && tar -zcf ../apicurio-registry-operator-$(VERSION).tar.gz *


.PHONY: quickstart
quickstart: build image-build image-push deploy ## TODO


.PHONY: bundle-build
bundle-build: install-operator-sdk install-yq ## Generate bundle metadata
	$(call target_dir,$(BUNDLE_DIR))
	kubectl kustomize controller/src/main/deploy/csv | envsubst | $(OPERATOR_SDK) generate bundle -q --output-dir $(BUNDLE_DIR) $(BUNDLE_OPTS)
	# Post-process annotations.yaml
	$(YQ) '... comments = ""' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) '.annotations = (.annotations | to_entries | del(.[] | select(.key == "operators.operatorframework.io.metrics.*")) | from_entries)' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) '.annotations."com.redhat.openshift.versions" = "v4.12"' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) 'sort_keys(..)' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	# Post-process bundle.Dockerfile
	mv bundle.Dockerfile $(BUNDLE_DIR)
	sed -i '\|^# .*$$|d' $(BUNDLE_DIR)/bundle.Dockerfile
	sed -i '\|^LABEL operators\.operatorframework.io\.metrics\..*$$|d' $(BUNDLE_DIR)/bundle.Dockerfile
	sed -i 's|^FROM scratch$$|FROM scratch\n\nLABEL com.redhat.openshift.versions=v4.12|g' $(BUNDLE_DIR)/bundle.Dockerfile
	# Validate
	$(OPERATOR_SDK) bundle validate $(BUNDLE_DIR)


.PHONY: ac-bundle-build
ac-bundle-build: install-operator-sdk install-yq ## Generate bundle metadata
	$(call target_dir,$(AC_BUNDLE_DIR))
	kubectl kustomize controller/src/main/deploy/api-controller-csv | envsubst | $(OPERATOR_SDK) generate bundle -q --output-dir $(AC_BUNDLE_DIR) $(AC_BUNDLE_OPTS)
	# Post-process annotations.yaml
	$(YQ) '... comments = ""' -i $(AC_BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) '.annotations = (.annotations | to_entries | del(.[] | select(.key == "operators.operatorframework.io.metrics.*")) | from_entries)' -i $(AC_BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) '.annotations."com.redhat.openshift.versions" = "v4.12"' -i $(AC_BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) 'sort_keys(..)' -i $(AC_BUNDLE_DIR)/metadata/annotations.yaml
	# Post-process bundle.Dockerfile
	mv bundle.Dockerfile $(AC_BUNDLE_DIR)
	sed -i '\|^# .*$$|d' $(AC_BUNDLE_DIR)/bundle.Dockerfile
	sed -i '\|^LABEL operators\.operatorframework.io\.metrics\..*$$|d' $(AC_BUNDLE_DIR)/bundle.Dockerfile
	sed -i 's|^FROM scratch$$|FROM scratch\n\nLABEL com.redhat.openshift.versions=v4.12|g' $(AC_BUNDLE_DIR)/bundle.Dockerfile
	# Validate
	$(OPERATOR_SDK) bundle validate $(AC_BUNDLE_DIR)


.PHONY: bundle-image-build
bundle-image-build: ## TODO
	docker build -f $(BUNDLE_DIR)/bundle.Dockerfile -t $(BUNDLE_IMAGE) .
ifneq ($(ADDITIONAL_BUNDLE_IMAGE),)
ifneq ($(ADDITIONAL_BUNDLE_TAG),)
	docker tag $(BUNDLE_IMAGE) $(ADDITIONAL_BUNDLE_IMAGE)
endif
endif


.PHONY: ac-bundle-image-build
ac-bundle-image-build: ## TODO
	docker build -f $(AC_BUNDLE_DIR)/bundle.Dockerfile -t $(AC_BUNDLE_IMAGE) .


.PHONY: bundle-image-push
bundle-image-push: ## TODO
	docker push $(BUNDLE_IMAGE)
ifneq ($(ADDITIONAL_BUNDLE_IMAGE),)
ifneq ($(ADDITIONAL_BUNDLE_TAG),)
	docker push $(ADDITIONAL_BUNDLE_IMAGE)
endif
endif


.PHONY: ac-bundle-image-push
ac-bundle-image-push: ## TODO
	docker push $(AC_BUNDLE_IMAGE)


.PHONY: bundle
bundle: bundle-build bundle-image-build bundle-image-push ## TODO


.PHONY: ac-bundle
ac-bundle: ac-bundle-build ac-bundle-image-build ac-bundle-image-push ## TODO


.PHONY: catalog-build
catalog-build: install-opm ## TODO
	$(call target_dir,$(CATALOG_TARGET_DIR))
	# Create catalog.yaml
	mkdir -p $(CATALOG_TARGET_DIR)/configs
	cat $(CATALOG_DIR)/catalog.template.yaml | envsubst > $(CATALOG_TARGET_DIR)/catalog.yaml
	# Build
	cd $(CATALOG_TARGET_DIR) && $(OPM) alpha render-template basic catalog.yaml --output=yaml > configs/index.yaml
	cd $(CATALOG_TARGET_DIR) && $(OPM) generate dockerfile configs


.PHONY: ac-catalog-build
ac-catalog-build: install-opm ## TODO
	$(call target_dir,$(AC_CATALOG_TARGET_DIR))
	# Create catalog.yaml
	mkdir -p $(AC_CATALOG_TARGET_DIR)/configs
	cat $(AC_CATALOG_DIR)/catalog.template.yaml | envsubst > $(AC_CATALOG_TARGET_DIR)/catalog.yaml
	# Build
	cd $(AC_CATALOG_TARGET_DIR) && $(OPM) alpha render-template basic catalog.yaml --output=yaml > configs/index.yaml
	cd $(AC_CATALOG_TARGET_DIR) && $(OPM) generate dockerfile configs


.PHONY: catalog-image-build
catalog-image-build: ## TODO
	docker build -f $(CATALOG_TARGET_DIR)/configs.Dockerfile -t $(CATALOG_IMAGE) $(CATALOG_TARGET_DIR)
ifneq ($(ADDITIONAL_CATALOG_IMAGE),)
ifneq ($(ADDITIONAL_CATALOG_IMAGE_TAG),)
	docker tag $(CATALOG_IMAGE) $(ADDITIONAL_CATALOG_IMAGE)
endif
endif


.PHONY: ac-catalog-image-build
ac-catalog-image-build: ## TODO
	docker build -f $(AC_CATALOG_TARGET_DIR)/configs.Dockerfile -t $(AC_CATALOG_IMAGE) $(AC_CATALOG_TARGET_DIR)


.PHONY: catalog-image-push
catalog-image-push: ## TODO
	docker push $(CATALOG_IMAGE)
ifneq ($(ADDITIONAL_CATALOG_IMAGE),)
ifneq ($(ADDITIONAL_CATALOG_IMAGE_TAG),)
	docker push $(ADDITIONAL_CATALOG_IMAGE)
endif
endif


.PHONY: ac-catalog-image-push
ac-catalog-image-push: ## TODO
	docker push $(AC_CATALOG_IMAGE)


.PHONY: ac-catalog
ac-catalog: ac-catalog-build ac-catalog-image-build ac-catalog-image-push ## TODO


.PHONY: catalog
catalog: catalog-build catalog-image-build catalog-image-push ## TODO


.PHONY: catalog-deploy
catalog-deploy: ## TODO
	kubectl create namespace $(CATALOG_NAMESPACE) || true
	cat $(CATALOG_DIR)/catalog-source.yaml | envsubst | kubectl -n $(CATALOG_NAMESPACE) apply -f -


.PHONY: ac-catalog-deploy
ac-catalog-deploy: ## TODO
	kubectl create namespace $(CATALOG_NAMESPACE) || true
	cat $(AC_CATALOG_DIR)/catalog-source.yaml | envsubst | kubectl -n $(CATALOG_NAMESPACE) apply -f -


.PHONY: catalog-undeploy
catalog-undeploy: ## TODO
	cat $(CATALOG_DIR)/catalog-source.yaml | envsubst | kubectl -n $(CATALOG_NAMESPACE) delete -f - || true


.PHONY: ac-catalog-undeploy
ac-catalog-undeploy: ## TODO
	cat $(AC_CATALOG_DIR)/catalog-source.yaml | envsubst | kubectl -n $(CATALOG_NAMESPACE) delete -f - || true


.PHONY: catalog-subscription-deploy
catalog-subscription-deploy: ## TODO
	kubectl create namespace $(NAMESPACE) || true
	cat $(CATALOG_DIR)/operator-group.yaml | envsubst | kubectl -n $(NAMESPACE) apply -f -
	cat $(CATALOG_DIR)/subscription.yaml | envsubst | kubectl -n $(NAMESPACE) apply -f -


.PHONY: catalog-subscription-undeploy
catalog-subscription-undeploy: ## TODO
	cat $(CATALOG_DIR)/subscription.yaml | envsubst | kubectl -n $(NAMESPACE) delete -f - || true
	cat $(CATALOG_DIR)/operator-group.yaml | envsubst | kubectl -n $(NAMESPACE) delete -f - || true
	kubectl -n $(NAMESPACE) delete clusterserviceversion $(PACKAGE) || true


.PHONY: release-catalog-template-update
release-catalog-template-update: install-yq
	$(YQ) '(.entries[] | select(.schema == "olm.channel") | select(.name = "3.x") | .entries[] | select(.name = "$${PLACEHOLDER_PACKAGE}")).name = "$(PREVIOUS_PACKAGE)"' -i $(CATALOG_DIR)/catalog.template.yaml
	$(YQ) '(.entries[] | select(.schema == "olm.channel") | select(.name = "3.x") | .entries) |= [{"name": "$${PLACEHOLDER_PACKAGE}", "replaces": "$(PREVIOUS_PACKAGE)"}] + .' -i $(CATALOG_DIR)/catalog.template.yaml


.PHONY: dev
dev:
	mvn quarkus:dev


.PHONY: set-variable
set-variable:
	sed -i 's|^\( *$(VAR) *\??=\) *\([^ ]*\)\(.*\)$$|\1 $(VAL)\3|g' Makefile


.PHONY: get-variable
get-variable:
	@echo $($(VAR))
