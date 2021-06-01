#!/bin/bash

set -eo pipefail


PROJECT_NAME="srs-service-registry"
MVN_BUILD_COMMAND="mvn clean install -Pprod -Psql -Pmultitenancy"


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
    ${MVN_BUILD_COMMAND}
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
