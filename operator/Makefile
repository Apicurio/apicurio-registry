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
IMAGE_NAME ?= apicurio-registry-operator
IMAGE_TAG ?= $(LC_VERSION)

IMAGE ?= $(IMAGE_REGISTRY)/$(IMAGE_NAME):$(IMAGE_TAG)
ADDITIONAL_IMAGE ?= $(IMAGE_REGISTRY)/$(IMAGE_NAME):$(ADDITIONAL_IMAGE_TAG)

REGISTRY_APP_IMAGE ?= quay.io/apicurio/apicurio-registry:latest-snapshot
REGISTRY_UI_IMAGE ?= quay.io/apicurio/apicurio-registry-ui:latest-snapshot

NAMESPACE ?= default

### Bundle

PACKAGE_NAME ?= apicurio-registry
PACKAGE ?= $(PACKAGE_NAME).v$(LC_VERSION)
# TODO Replaces:
# PREVIOUS_PACKAGE_VERSION =
# REPLACES = $(PACKAGE_NAME).v$(PREVIOUS_PACKAGE_VERSION)

CHANNELS ?= 3.x
DEFAULT_CHANNEL ?= $(CHANNELS)

BUNDLE_DIR ?= target/bundle/$(PACKAGE_NAME)/$(LC_VERSION)

BUNDLE_IMAGE_NAME ?= $(IMAGE_NAME)-bundle
BUNDLE_IMAGE_TAG ?= $(LC_VERSION)
BUNDLE_IMAGE ?= $(IMAGE_REGISTRY)/$(BUNDLE_IMAGE_NAME):$(BUNDLE_IMAGE_TAG)
ADDITIONAL_BUNDLE_IMAGE ?= $(IMAGE_REGISTRY)/$(BUNDLE_IMAGE_NAME):$(ADDITIONAL_BUNDLE_TAG)

### Catalog

CATALOG_NAMESPACE ?= openshift-marketplace

CATALOG_DIR ?= olm-tests/src/test/deploy/catalog
CATALOG_TARGET_DIR ?= target/catalog

CATALOG_IMAGE_NAME ?= $(IMAGE_NAME)-catalog
CATALOG_IMAGE_TAG ?= $(LC_VERSION)
CATALOG_IMAGE ?= $(IMAGE_REGISTRY)/$(CATALOG_IMAGE_NAME):$(CATALOG_IMAGE_TAG)
ADDITIONAL_CATALOG_IMAGE_TAG ?= latest$(LC_VERSION_SUFFIX)
ADDITIONAL_CATALOG_IMAGE ?= $(IMAGE_REGISTRY)/$(CATALOG_IMAGE_NAME):$(ADDITIONAL_CATALOG_IMAGE_TAG)

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

export PLACEHOLDER_DATE := $(DATE)

export PLACEHOLDER_PACKAGE := $(PACKAGE)
export PLACEHOLDER_BUNDLE_IMAGE := $(BUNDLE_IMAGE)

export PLACEHOLDER_CATALOG_NAMESPACE := $(CATALOG_NAMESPACE)
export PLACEHOLDER_CATALOG_IMAGE := $(CATALOG_IMAGE)



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


# Why do we need this?
# Because it was decided that I can't use operator-sdk to generate bundle files for some reason.
# I have to use opm, which only supports generating bundle annotations and dockerfile.
# As a result, I have to install this tool instead, as suggested in
# https://olm.operatorframework.io/docs/tasks/creating-operator-manifests/#installation-metadata-required .
FAQ_VERSION ?= 0.0.7
FAQ = $(CURRENT_DIR)/target/bin/faq
.PHONY: install-faq
install-faq: ## Install faq v0.0.7
	@{ \
		if [ ! -f $(FAQ) ]; \
		then \
			mkdir -p $(dir $(FAQ)); \
			curl -sSLo $(FAQ) "https://github.com/jzelinskie/faq/releases/download/$(FAQ_VERSION)/faq-$(OS)-$(ARCH)"; \
			chmod +x $(FAQ); \
		fi; \
	}


########## Targets


.PHONY: build
build: ## TODO
	mvn clean install $(BUILD_OPTS_EXT)


.PHONY: remote-tests-all
remote-tests-all: ## TODO
	mvn clean # Running `mvn clean` will delete controller/target/test-install.yaml, so it has to be run first, if needed.
	$(MAKE) INSTALL_FILE=controller/target/test-install.yaml dist-install-file
	$(eval BUILD_OPTS_EXT := $(subst "-pl controller -am",,$(BUILD_OPTS_EXT)))
	mvn verify $(BUILD_OPTS_EXT) -Dtest.operator.deployment=remote -Dtest.operator.catalog-image=$(CATALOG_IMAGE)


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
	kubectl kustomize controller/src/main/deploy/install | envsubst | kubectl -n $(NAMESPACE) delete -f -


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
	cp -t $(DIST_TARGET_DIR) target/generated-resources/licenses.xml
	sed -i '\|^.*<file>.*</file>.*$$|d' $(DIST_TARGET_DIR)/licenses.xml
	# Examples
	mkdir -p $(DIST_TARGET_DIR)/examples
	cp -t $(DIST_TARGET_DIR)/examples controller/src/main/deploy/examples/*
	rm $(DIST_TARGET_DIR)/examples/kustomization.yaml
	cp -rt $(DIST_TARGET_DIR)/examples controller/src/main/resources/k8s/examples/*
	cp -rt $(DIST_TARGET_DIR)/examples controller/src/test/resources/k8s/examples/*
	# Install
	cp $(INSTALL_FILE) $(DIST_TARGET_DIR)/install.yaml
	# Archive
	cd $(DIST_TARGET_DIR) && tar -zcf ../apicurio-registry-operator-$(VERSION).tar.gz *


.PHONY: quickstart
quickstart: build image-build image-push deploy ## TODO


# Why all this?
# Because it was decided that I can't use operator-sdk to generate bundle files for some reason.
# I have to use opm, which only supports generating bundle annotations and dockerfile.
# As a result, I had to invent a way to generate the manifests manually and this is the result.
.PHONY: bundle-build
bundle-build: install-opm install-faq install-yq ## Generate bundle files
	$(call target_dir,$(BUNDLE_DIR))
	mkdir -p $(BUNDLE_DIR)/manifests
	# Create CRD
	kubectl kustomize controller/src/main/deploy/crd | envsubst >$(BUNDLE_DIR)/manifests/registry.apicur.io_apicurioregistries3.yaml
	# Prepare tmp_* files with resources that will be included in the CSV
	cd $(BUNDLE_DIR)/manifests && \
	  kubectl kustomize $(CURRENT_DIR)/controller/src/main/deploy/csv | envsubst | \
	  $(YQ) --no-doc -s '"tmp_" + (.kind | downcase) + "_" + .metadata.name + ".yaml"' -
	# Set CSV name and version in "template" CSV
	cd $(BUNDLE_DIR)/manifests && \
	  $(YQ) '.metadata.name = "$(PACKAGE)"' -i tmp_clusterserviceversion_apicurio-registry.v0.0.0.yaml
	cd $(BUNDLE_DIR)/manifests && \
      $(YQ) '.spec.version = "$(LC_VERSION)"' -i tmp_clusterserviceversion_apicurio-registry.v0.0.0.yaml
	# Add examples to "template" CSV
	cd $(BUNDLE_DIR)/manifests && \
	  $(YQ) -o=json '[.]' tmp_apicurioregistry3_simple-apicurioregistry3.yaml >tmp_examples.json
	cd $(BUNDLE_DIR)/manifests && \
		$(YQ) '.metadata.annotations.alm-examples = load_str("tmp_examples.json")' -i tmp_clusterserviceversion_apicurio-registry.v0.0.0.yaml
	# Create CSV from the "template CSV" and tmp files
	cd $(BUNDLE_DIR)/manifests && \
	  $(FAQ) -f yaml -o yaml --slurp '.[0].spec.install = {strategy: "deployment", spec:{ deployments: [{name: .[1].metadata.name, label: .[1].metadata.labels, spec: .[1].spec }], clusterPermissions: [{serviceAccountName: .[3].metadata.name, rules: .[2].rules }]}} | .[0]' \
	  tmp_clusterserviceversion_apicurio-registry.v0.0.0.yaml tmp_deployment_apicurio-registry-operator-v$(LC_VERSION).yaml \
	  tmp_clusterrole_apicurio-registry-operator-clusterrole.yaml tmp_serviceaccount_apicurio-registry-operator.yaml \
	  >apicurio-registry.clusterserviceversion.yaml
	# Remove tmp_* files
	cd $(BUNDLE_DIR)/manifests && rm tmp_*
	$(OPM) alpha bundle generate -c $(CHANNELS) -e $(DEFAULT_CHANNEL) -p $(PACKAGE_NAME) -d $(BUNDLE_DIR)/manifests
	# Post-process annotations.yaml
	$(YQ) '.annotations."com.redhat.openshift.versions" = "v4.12"' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	$(YQ) 'sort_keys(..)' -i $(BUNDLE_DIR)/metadata/annotations.yaml
	# Post-process bundle.Dockerfile
	mv bundle.Dockerfile $(BUNDLE_DIR)
	sed -i 's|^FROM scratch$$|FROM scratch\n\nLABEL com.redhat.openshift.versions=v4.12|g' $(BUNDLE_DIR)/bundle.Dockerfile
	# No validation since opm does not support it, and I can't use operator-sdk.
	# $(OPERATOR_SDK) bundle validate $(BUNDLE_DIR)


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
	$(call target_dir,$(CATALOG_TARGET_DIR))
	# Create catalog.yaml
	mkdir -p $(CATALOG_TARGET_DIR)/configs
	cat $(CATALOG_DIR)/catalog.template.yaml | envsubst > $(CATALOG_TARGET_DIR)/catalog.yaml
	# Build
	cd $(CATALOG_TARGET_DIR) && $(OPM) alpha render-template basic catalog.yaml --output=yaml > configs/index.yaml
	cd $(CATALOG_TARGET_DIR) && $(OPM) generate dockerfile configs


.PHONY: catalog-image-build
catalog-image-build: ## TODO
	docker build -f $(CATALOG_TARGET_DIR)/configs.Dockerfile -t $(CATALOG_IMAGE) $(CATALOG_TARGET_DIR)
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

.PHONY: catalog-image-get
catalog-image-get: ## TODO
	@echo -n $(CATALOG_IMAGE)

.PHONY: catalog
catalog: catalog-build catalog-image-build catalog-image-push ## TODO


.PHONY: catalog-deploy
catalog-deploy: ## TODO
	kubectl create namespace $(CATALOG_NAMESPACE) || true
	cat $(CATALOG_DIR)/catalog-source.yaml | envsubst | kubectl -n $(CATALOG_NAMESPACE) apply -f -


.PHONY: catalog-undeploy
catalog-undeploy: ## TODO
	cat $(CATALOG_DIR)/catalog-source.yaml | envsubst | kubectl -n $(CATALOG_NAMESPACE) delete -f -


.PHONY: catalog-subscription-deploy
catalog-subscription-deploy: ## TODO
	kubectl create namespace $(NAMESPACE) || true
	cat $(CATALOG_DIR)/operator-group.yaml | envsubst | kubectl -n $(NAMESPACE) apply -f -
	cat $(CATALOG_DIR)/subscription.yaml | envsubst | kubectl -n $(NAMESPACE) apply -f -


.PHONY: catalog-subscription-undeploy
catalog-subscription-undeploy: ## TODO
	cat $(CATALOG_DIR)/subscription.yaml | envsubst | kubectl -n $(NAMESPACE) delete -f -
	kubectl -n $(NAMESPACE) delete clusterserviceversion $(PACKAGE)


.PHONY: dev
dev:
	mvn quarkus:dev
