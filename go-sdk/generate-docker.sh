#!/usr/bin/env bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if ! which docker 2> /dev/null
then
  echo "Error: 'docker' executable not found"
  exit 1
fi


UID=`id -u`
GID=`id -g`


read -r -d '' SCRIPT <<- END
apt-get update &&
apt-get -y install unzip &&
apt-get -y install libicu-dev &&
chmod u+rw /registry &&
cd /registry/go-sdk &&
make generate &&
make format &&
groupadd -g $GID -o localuser &&
useradd -m -u $UID -g $GID -o -s /bin/bash localuser &&
chown -R $UID /registry/go-sdk &&
chgrp -R $GID /registry/go-sdk
END

docker run --rm -v $SCRIPT_DIR/..:/registry golang:1.21.6 /bin/bash -c "$SCRIPT"
