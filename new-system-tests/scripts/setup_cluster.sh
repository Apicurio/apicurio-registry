#!/bin/bash

### INITIALIZATION OF VARIABLES ################################################

# Define name of cluster for tests
CLUSTER_NAME="apicurio-cluster"
# If CLUSTER_TYPE environment variable is not set, use "kind" as default value
: "${CLUSTER_TYPE:=kind}"
# Define command line tool for cluster
CLUSTER_CMD="kubectl"

# If cluster is OpenShift
if [ "${CLUSTER_TYPE}" == "openshift" ]
then
  # Set command line tool to oc
  CLUSTER_CMD="oc"
fi

### SCRIPT FUNCTIONS ###########################################################

# Logs info message to stdout
function log_info() {
  # Print all arguments to stdout
  echo "INFO: $*"
}

# Logs error message to stderr
function log_error() {
  # Print all arguments to stderr
  echo "ERROR: $*" >&2
}

# Checks if kind cluster for tests exists
function kind_cluster_exists() {
  # Log information about checking kind cluster existence
  log_info "Checking existence of kind cluster '${CLUSTER_NAME}'..."
  # Check that there is exactly one kind cluster with name CLUSTER_NAME
  [[ $(kind get clusters 2>&1 | grep "^${CLUSTER_NAME}$" -c) -eq 1 ]]
}

# Creates kind cluster for tests
function kind_cluster_create() {
  # Log information about creating kind cluster
  log_info "Creating kind cluster '${CLUSTER_NAME}'..."
  # Create kind cluster with name CLUSTER_NAME
  kind create cluster -n "${CLUSTER_NAME}" --config scripts/kind_config.yaml
}

# Deploys ingress-nginx-controller to cluster
function ingress_nginx_controller_deploy() {
  # Log information about deploying ingress-nginx-controller
  log_info "Deploying ingress-nginx-controller..."
  # Apply ingress-nginx deploy file
  $CLUSTER_CMD apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
}

# Patches ingress-nginx-controller deployment to enable SSL passthrough
function ingress_nginx_controller_patch() {
  # Log information about patching ingress-nginx-controller
  log_info "Patching ingress-nginx-controller..."
  # Patch ingress-nginx-controller deployment
  $CLUSTER_CMD patch deployment ingress-nginx-controller -n ingress-nginx --type=json -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--enable-ssl-passthrough"}]'
}

# Deploys OLM to cluster
function olm_deploy() {
  # Log information about deploying OLM
  log_info "Deploying OLM..."
  # Deploy CRDs of OLM
  $CLUSTER_CMD apply -f https://raw.githubusercontent.com/operator-framework/operator-lifecycle-manager/master/deploy/upstream/quickstart/crds.yaml || return 1
  # Deploy OLM
  $CLUSTER_CMD apply -f https://raw.githubusercontent.com/operator-framework/operator-lifecycle-manager/master/deploy/upstream/quickstart/olm.yaml || return 1
}

# Installs Keycloak operator
function keycloak_operator_install() {
  # Log information about installing Keycloak operator
  log_info "Installing Keycloak operator..."
  # Create namespace for Keycloak
  $CLUSTER_CMD apply -f resources/"${1}"/keycloak/00_namespace.yaml || return 1
  # Create operator group for Keycloak
  $CLUSTER_CMD apply -f resources/"${1}"/keycloak/01_operator_group.yaml || return 1
  # Create subscription for Keycloak operator
  $CLUSTER_CMD apply -f resources/"${1}"/keycloak/02_subscription.yaml || return 1
}

# Deploys Keycloak
function keycloak_deploy() {
  # Log information about deploying Keycloak
  log_info "Deploying Keycloak..."
  # Create Keycloak
  $CLUSTER_CMD apply -f resources/"${1}"/keycloak/03_keycloak.yaml || return 1
  # Create Keycloak realm
  $CLUSTER_CMD apply -f resources/"${1}"/keycloak/04_keycloak_realm.yaml || return 1
}

# Enables external access to Keycloak
function keycloak_external_access_enable() {
  # Log information about enabling external access
  log_info "Enabling external access to Keycloak..."

  # If cluster is Kubernetes implementation (kind, minikube,...)
  if [ "${1}" == "kubernetes" ]
  then
    # Create ingress
    $CLUSTER_CMD apply -f resources/"${1}"/keycloak/05_ingress.yaml || return 1
  # If cluster is OpenShift implementation
  elif [ "${1}" == "openshift" ]
  then
    # Create route
    $CLUSTER_CMD apply -f resources/"${1}"/keycloak/05_route.yaml || return 1
  # If cluster implementation is unknown
  else
    # Log error message
    log_error "Unknown cluster implementation '${1}'. Exiting..."
    # Exit with error code
    exit 1
  fi

  # Create service
  $CLUSTER_CMD apply -f resources/"${1}"/keycloak/06_service.yaml || return 1
}

# TODO: get Keycloak URL?

# TODO: cleanup function (delete everything)

### SETUP OF CLUSTER ###########################################################

# If CLUSTER_TYPE is "openshift"
if [ "$CLUSTER_TYPE" == "openshift" ]
then
  # Do pre-run setup of OpenShift cluster
  log_info "Preparing OpenShift cluster..."

  # Install Keycloak operator
  # If installation was successful
  if keycloak_operator_install "openshift"
  then
    # Log information about success
    log_info "Keycloak operator successfully installed."
  # If installation failed
  else
    # Log error message
    log_error "Installation of Keycloak operator failed. Exiting..."
    # Exit with error code
    exit 1
  fi

  # Wait for Keycloak operator readiness
  # TODO: Add waiting on resource(s)
  TIMEOUT=120
  # Log information about waiting for Keycloak operator readiness
  log_info "Waiting for ${TIMEOUT} seconds for readiness of Keycloak operator..."
  # Sleep for TIMEOUT seconds
  sleep $TIMEOUT

  # Deploy Keycloak
  # If deploy was successful
  if keycloak_deploy "openshift"
  then
    # Log information about success
    log_info "Keycloak successfully deployed."
  # If deploy failed
  else
    # Log error message
    log_error "Deploy of Keycloak failed. Exiting..."
    # Exit with error code
    exit 1
  fi

  # Enable external access to Keycloak
  # If enablement was successful
  if keycloak_external_access_enable "openshift"
  then
    # Log information about success
    log_info "External access to Keycloak successfully enabled."
  # If enablement failed
  else
    # Log error message
    log_error "Enablement of external access to Keycloak failed. Exiting..."
    # Exit with error code
    exit 1
  fi
# If CLUSTER_TYPE is "kind" (default)
elif [ "$CLUSTER_TYPE" == "kind" ]
then
  # Do pre-run setup of kind cluster
  log_info "Preparing kind cluster '${CLUSTER_NAME}'..."

  # If kind cluster already exists
  if kind_cluster_exists
  then
    # Do nothing
    log_info "kind cluster '${CLUSTER_NAME}' already exists."
  # If kind cluster does not exist
  else
    # Create kind cluster

    # If kind cluster was successfully created
    if kind_cluster_create
    then
      # Print info about successful creation of kind cluster
      log_info "kind cluster '${CLUSTER_NAME}' created."

      # Deploy ingress-nginx-controller
      # If deploy was successful
      if ingress_nginx_controller_deploy
      then
        # Log information about success
        log_info "ingress-nginx-controller successfully deployed."
      # If deploy failed
      else
        # Log error message
        log_error "Deploy of ingress-nginx-controller failed. Exiting..."
        # Exit with error code
        exit 1
      fi

      # Patch ingress-nginx-controller
      # If patch was successful
      if ingress_nginx_controller_patch
      then
        # Log information about success
        log_info "Patch of ingress-nginx-controller deployment successful."
      # If patch failed
      else
        # Log error message
        log_error "Patch of ingress-nginx-controller deployment failed. Exiting..."
        # Exit with error code
        exit 1
      fi

      # Deploy OLM
      # If deploy was successful
      if olm_deploy
      then
        # Log information about success
        log_info "OLM successfully deployed."
      # If deploy failed
      else
        # Log error message
        log_error "Deploy of OLM failed. Exiting..."
        # Exit with error code
        exit 1
      fi

      # Install Keycloak operator
      # If installation was successful
      if keycloak_operator_install "kubernetes"
      then
        # Log information about success
        log_info "Keycloak operator successfully installed."
      # If installation failed
      else
        # Log error message
        log_error "Installation of Keycloak operator failed. Exiting..."
        # Exit with error code
        exit 1
      fi

      # Wait for Keycloak operator readiness
      # TODO: Add waiting on resource(s)
      TIMEOUT=120
      # Log information about waiting for Keycloak operator readiness
      log_info "Waiting for ${TIMEOUT} seconds for readiness of Keycloak operator..."
      # Sleep for TIMEOUT seconds
      sleep $TIMEOUT

      # Deploy Keycloak
      # If deploy was successful
      if keycloak_deploy "kubernetes"
      then
        # Log information about success
        log_info "Keycloak successfully deployed."
      # If deploy failed
      else
        # Log error message
        log_error "Deploy of Keycloak failed. Exiting..."
        # Exit with error code
        exit 1
      fi

      # Enable external access to Keycloak
      # If enablement was successful
      if keycloak_external_access_enable "kubernetes"
      then
        # Log information about success
        log_info "External access to Keycloak successfully enabled."
      # If enablement failed
      else
        # Log error message
        log_error "Enablement of external access to Keycloak failed. Exiting..."
        # Exit with error code
        exit 1
      fi

      # TODO: Install cluster-wide Kafka operator
      # TODO: Install cluster-wide/namespaced Registry operator here?
    # If kind cluster creation failed
    else
      # Print error message
      log_error "Creation of kind cluster '${CLUSTER_NAME}' failed. Exiting..."
      # Exit with error code
      exit 1
    fi
  fi
# If CLUSTER_TYPE is "minikube"
elif [ "$CLUSTER_TYPE" == "minikube" ]
then
  # Do pre-run setup of minikube cluster
  log_info "Preparing minikube cluster..."
# If CLUSTER_TYPE is not known
else
  # Print error message
  log_error "Unknown CLUSTER_TYPE '${CLUSTER_TYPE}'. Exiting..."
  # Exit with error code
  exit 1
fi
