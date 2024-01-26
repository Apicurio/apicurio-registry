#!/usr/bin/env bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

SED_NAME="sed"
PACKAGE_NAME="linux-x64"
if [[ $OSTYPE == 'darwin'* ]]; then
  SED_NAME="gsed"
  PACKAGE_NAME="osx-x64"
fi

# TODO move the kiota-version.csproj to it's own folder?
VERSION=$(cat $SCRIPT_DIR/kiota-version.csproj | grep Version | sed -n 's/.*Version="\([^"]*\)".*/\1/p')
URL="https://github.com/microsoft/kiota/releases/download/v${VERSION}/${PACKAGE_NAME}.zip"

# COMMAND="kiota"
# if ! command -v $COMMAND &> /dev/null
# then
#  echo "System wide kiota could not be found, using local version"
  if [[ ! -f $SCRIPT_DIR/kiota_tmp/kiota ]]
  then
    echo "Local kiota could not be found, downloading"
    rm -rf $SCRIPT_DIR/kiota_tmp
    mkdir -p $SCRIPT_DIR/kiota_tmp
    curl -sL $URL > $SCRIPT_DIR/kiota_tmp/kiota.zip
    unzip $SCRIPT_DIR/kiota_tmp/kiota.zip -d $SCRIPT_DIR/kiota_tmp

    chmod a+x $SCRIPT_DIR/kiota_tmp/kiota
  fi
  COMMAND="$SCRIPT_DIR/kiota_tmp/kiota"
# fi

rm -rf $SCRIPT_DIR/pkg/registryclient-v2
mkdir -p $SCRIPT_DIR/pkg/registryclient-v2
rm -rf $SCRIPT_DIR/pkg/registryclient-v3
mkdir -p $SCRIPT_DIR/pkg/registryclient-v3

cp $SCRIPT_DIR/../common/src/main/resources/META-INF/openapi-v2.json $SCRIPT_DIR/v2.json
cp $SCRIPT_DIR/../common/src/main/resources/META-INF/openapi.json $SCRIPT_DIR/v3.json

# Hask to overcome https://github.com/microsoft/kiota/issues/3920
$SED_NAME -i 's/NewComment/DTONewComment/' $SCRIPT_DIR/v2.json
$SED_NAME -i 's/NewComment/DTONewComment/' $SCRIPT_DIR/v3.json

$COMMAND generate \
  --language go \
  --openapi $SCRIPT_DIR/v2.json \
  --clean-output \
  -o $SCRIPT_DIR/pkg/registryclient-v2 \
  --namespace-name github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2

$COMMAND generate \
  --language go \
  --openapi $SCRIPT_DIR/v3.json \
  --clean-output \
  -o $SCRIPT_DIR/pkg/registryclient-v3 \
  --namespace-name github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3
