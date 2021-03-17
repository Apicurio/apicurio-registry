#!/bin/bash
set -euxo pipefail

echo "Building Release Candidate Images"
mvn package -Pprod -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot -pl distro/docker
mvn package -Pprod -Pkafkasql -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot -pl distro/docker
mvn package -Pprod -Psql -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot -pl distro/docker
make CONTAINER_IMAGE_TAG=latest-snapshot tenant-manager-container
