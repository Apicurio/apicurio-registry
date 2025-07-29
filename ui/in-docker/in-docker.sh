#!/bin/bash

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd $BASE_DIR

docker build -t apicurio-registry-ui-in-docker:latest .

docker run -it \
  --network=host \
  --name apicurio-registry-ui-in-docker \
  -v $BASE_DIR/..:/mnt/apicurio-registry-ui \
  --user $(id -u):$(id -g) \
  apicurio-registry-ui-in-docker:latest \
  bash

docker rm apicurio-registry-ui-in-docker
