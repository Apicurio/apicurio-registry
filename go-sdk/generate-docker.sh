#!/usr/bin/env bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if ! which docker 2> /dev/null
then
  echo "Error: 'docker' executable not found"
  exit 1
fi

read -r -d '' SCRIPT <<- END
apt-get update &&
apt-get -y install unzip &&
apt-get -y install libicu-dev &&
chmod u+rw /registry &&
cd /registry/go-sdk &&
make generate &&
make format
END

docker run --rm -v $SCRIPT_DIR/..:/registry golang:1.21.6 /bin/bash -c "$SCRIPT"
