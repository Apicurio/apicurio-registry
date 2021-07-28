#!/bin/bash

set -eo pipefail

# The version should be the short hash from git. This is what the deployent process expects.
VERSION="$(git log --pretty=format:'%h' -n 1)"

PROJECT_NAME="multitenant-service-registry"
IMAGE_REGISTRY="quay.io"
IMAGE_ORG="rhoas"
IMAGE_TAG="${VERSION}"

SERVICE_REGISTRY_IMAGE_NAME="srs-service-registry"
SERVICE_REGISTRY_DOCKER_BUILD_COMMAND="docker build -f ./distro/docker/target/docker/Dockerfile.sql.jvm -t ${IMAGE_REGISTRY}/${IMAGE_ORG}/${SERVICE_REGISTRY_IMAGE_NAME}:${IMAGE_TAG} ./distro/docker/target/docker"

TENANT_MANAGER_IMAGE_NAME="srs-tenant-manager"
TENANT_MANAGER_DOCKER_BUILD_COMMAND="docker build -f multitenancy/tenant-manager-api/src/main/docker/Dockerfile.jvm -t ${IMAGE_REGISTRY}/${IMAGE_ORG}/${TENANT_MANAGER_IMAGE_NAME}:${IMAGE_TAG} ./multitenancy/tenant-manager-api/"



display_usage() {
    cat <<EOT

##########################################################################################################################

 This script gets triggered by the automated CI/CD jobs of AppSRE. It builds and pushes '${PROJECT_NAME}' image to the
 'rhoas' organization in 'quay.io' registry(defaults). Quay-organization, Image name and tags are configurable.

 In order to work, it needs the following variables defined in the CI/CD configuration of the project:

 RHOAS_QUAY_USER - The name of the robot account
                   used to push images to 'quay.io'

 RHOAS_QUAY_TOKEN - The token of the robot account
                    used to push images to 'quay.io'

 The machines that run this script need to have access to internet, so that the built images can be pushed.


Usage: $0 [options]
Example: $0 --org rhoas --name srs-service-registry --tag 2.0.0.Final

options include:

-o, --org         The organization the container image will be part of. If not set defaults to 'rhoas'
-t, --tag         The tag of the container image. If not set defaults to 'latest'
-h, --help        This help message

##########################################################################################################################



EOT
}


build_project() {
    echo "#######################################################################################################"
    echo " Building Project '${PROJECT_NAME}'..."
    echo "#######################################################################################################"
    # AppSRE environments doesn't has maven, jdk11, node and yarn which are required depencies for building this project
    # Installing these dependencies is a tedious task and also since it's a shared instance, installing the required versions of these dependencies is not possible sometimes
    # Hence, using custom container that packs the required dependencies with the specific required versions
    # docker run --rm -t -u $(id -u):$(id -g) -w /home/user -v $(pwd):/home/user quay.io/riprasad/srs-project-builder:latest bash -c "${MVN_BUILD_COMMAND}"
    
    #TODO confirm we are ok with this, using this ci-tools image is the recomended way, but using this we don't control the java nor maven version...
    docker pull quay.io/app-sre/mk-ci-tools:latest
    docker run -v $(pwd):/opt/srs -w /opt/srs -e HOME=/tmp -u $(id -u) \
        -e LANG=en_US.UTF-8 \
        -e LANGUAGE=en_US:en \
        -e LC_ALL=en_US.UTF-8 \
        quay.io/app-sre/mk-ci-tools:latest make build-project
}


build_image() {
    local IMAGE_NAME="$1"
    local DOCKER_BUILD_COMMAND="$2"
    echo "#######################################################################################################"
    echo " Building Image ${IMAGE_REGISTRY}/${IMAGE_ORG}/${IMAGE_NAME}:${IMAGE_TAG}"
    echo " IMAGE_REGISTRY: ${IMAGE_REGISTRY}"
    echo " IMAGE_ORG: ${IMAGE_ORG}"
    echo " IMAGE_NAME: ${IMAGE_NAME}"
    echo " IMAGE_TAG: ${IMAGE_TAG}"
    echo " Build Command: ${DOCKER_BUILD_COMMAND}"
    echo "#######################################################################################################"
    ${DOCKER_BUILD_COMMAND}
}

quay_login() {
    echo "Logging to ${IMAGE_REGISTRY}..."
    echo "docker login -u ${RHOAS_QUAY_USER} -p ${RHOAS_QUAY_TOKEN} ${IMAGE_REGISTRY}"
    docker login -u "${RHOAS_QUAY_USER}" -p "${RHOAS_QUAY_TOKEN}" "${IMAGE_REGISTRY}"
    if [ $? -eq 0 ]
    then
      echo "Login to ${IMAGE_REGISTRY} Succeeded!"
    else
      echo "Login to ${IMAGE_REGISTRY} Failed!"
    fi
}

push_image() {
    local IMAGE_NAME="$1"
    echo "#######################################################################################################"
    echo " Pushing Image ${IMAGE_REGISTRY}/${IMAGE_ORG}/${IMAGE_NAME}:${IMAGE_TAG}"
    echo "#######################################################################################################"
    docker push "${IMAGE_REGISTRY}/${IMAGE_ORG}/${IMAGE_NAME}:${IMAGE_TAG}"
    docker tag "${IMAGE_REGISTRY}/${IMAGE_ORG}/${IMAGE_NAME}:${IMAGE_TAG}" "${IMAGE_REGISTRY}/${IMAGE_ORG}/${IMAGE_NAME}:latest"
    docker push "${IMAGE_REGISTRY}/${IMAGE_ORG}/${IMAGE_NAME}:latest"
    if [ $? -eq 0 ]
    then
      echo "Image successfully pushed to ${IMAGE_REGISTRY}"
    else
      echo "Image Push Failed!"
    fi
}






main() { 

    # Parse command line arguments
    while [ $# -gt 0 ]
    do
        arg="$1"

        case $arg in
          -h|--help)
            shift
            display_usage
            exit 0
            ;;
          -o|--org)
            shift
            IMAGE_ORG="$1"
            ;;
          -t|--tag)
            shift
            IMAGE_TAG="$1"
            ;;
          *)
            echo "Unknown argument: $1"
            display_usage
            exit 1
            ;;
        esac
        shift
    done
    
    # The credentials to quay.io will be provided during pipeline runtime and you should make sure that following environment variables are available
    if [[ ! -z "${RHOAS_QUAY_USER}" ]] && [[ ! -z "${RHOAS_QUAY_TOKEN}" ]]; then
       echo "==| RHOAS Quay.io user and token is set, will push images to RHOAS org |=="
    else
       echo "RHOAS Quay.io user and token is not set. Aborting the process..."
       exit 1
    fi

    # building multitenant service registry
    build_project

    # building images
    build_image "${SERVICE_REGISTRY_IMAGE_NAME}" "${SERVICE_REGISTRY_DOCKER_BUILD_COMMAND}"
    build_image "${TENANT_MANAGER_IMAGE_NAME}" "${TENANT_MANAGER_DOCKER_BUILD_COMMAND}"

    # logging to quay
    quay_login

    # pushing the images to quay
    push_image ${SERVICE_REGISTRY_IMAGE_NAME}
    push_image ${TENANT_MANAGER_IMAGE_NAME}

}

main $*
