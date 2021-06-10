#!/bin/bash

set -eo pipefail


PROJECT_NAME="multitenant-service-registry"
SKIP_TESTS=false
MVN_BUILD_COMMAND="mvn clean install -Pno-docker -Pprod -Psql -Pmultitenancy -am -pl storage/sql,multitenancy/tenant-manager-api -Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false -DskipTests=${SKIP_TESTS}"

display_usage() {
    cat <<EOT

###########################################################################################

 This script gets triggered by the automated CI/CD jobs of AppSRE. It builds and tests 
 '${PROJECT_NAME}' whenever a pull request is raised.


 Usage: $0 [options]
 Example: $0

 options include:

 -h, --help        This help message

#############################################################################################


EOT
}


build_project() {
    echo "#######################################################################################################"
    echo " Building Project '${PROJECT_NAME}'..."
    echo " Build Command: ${MVN_BUILD_COMMAND}"
    echo "#######################################################################################################"
    # AppSRE environments doesn't has maven, jdk11, node and yarn which are required depencies for building this project
    # Installing these dependencies is a tedious task and also since it's a shared instance, installing the required versions of these dependencies is not possible sometimes
    # Hence, using custom container that packs the required dependencies with the specific required versions
    # docker run --rm -t -u $(id -u):$(id -g) -w /home/user -v $(pwd):/home/user quay.io/riprasad/srs-project-builder:latest bash -c "${MVN_BUILD_COMMAND}"
    
    #TODO confirm we are ok with this, using this ci-tools image is the recomended way, but using this we don't control the java nor maven version...
    docker pull quay.io/app-sre/mk-ci-tools:latest
    docker run -v $(pwd):/opt/srs -w /opt/srs -e HOME=/tmp -u $(id -u) quay.io/app-sre/mk-ci-tools:latest ${MVN_BUILD_COMMAND}
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
          *)
            echo "Unknown argument: $1"
            display_usage
            exit 1
            ;;
        esac
        shift
    done

    # function calls
    build_project

}

main $*
