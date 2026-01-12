#!/bin/sh

if [ "x$DOCKER_CMD" = "x" ]
then
  DOCKER_CMD="docker"
fi

npm install
npm run clean
npm run build
npm run package

$DOCKER_CMD build -t="apicurio/apicurio-registry-ui:latest-snapshot" --rm .

