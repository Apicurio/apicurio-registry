#!/bin/bash

set -eo pipefail


PROJECT_NAME="multitenant-service-registry"

SKIP_TESTS=true # skipping tests since tests require docker. fabian working to fix this
MVN_BUILD_COMMAND="mvn clean install -Pprod -Psql -Pkafkasql -Pmultitenancy -DskipTests=${SKIP_TESTS}"


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
    docker run --rm -t -u $(id -u):$(id -g) -w /home/user -v $(pwd):/home/user quay.io/riprasad/srs-project-builder:latest bash -c "${MVN_BUILD_COMMAND}"
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
