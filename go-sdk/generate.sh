#!/usr/bin/env bash

# === Detect arch and OS

RAW_ARCH="$(uname -m)"
# See https://stackoverflow.com/a/45125525
if  [[ "$RAW_ARCH" == aarch64* || "$RAW_ARCH" == armv8* ]]; then
  ARCH=arm64
elif [[ "$RAW_ARCH" == x86_64* ]]; then
  ARCH=x64
elif [[ "$RAW_ARCH" == i*86 ]]; then
  ARCH=x86
else
  echo "ERROR: Detected processor architecture is not recognized or supported: $RAW_ARCH"
  exit 1
fi

if [[ "$OSTYPE" == linux* ]]; then
  OS=linux
elif [[ "$OSTYPE" == darwin* ]]; then
  OS=osx
else
  # Windows not supported yet.
  echo "ERROR: Detected OS is not recognized or supported: $OSTYPE"
  exit 1
fi

PACKAGE_NAME="$OS-$ARCH"

# ===

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

SED_NAME=sed
if [[ "$OS" == osx ]]; then
  SED_NAME=gsed
fi

# TODO move the kiota-version.csproj to it's own folder?
VERSION=$(cat $SCRIPT_DIR/go-sdk.csproj | grep Version | sed -n 's/.*Version="\([^"]*\)".*/\1/p')
URL="https://github.com/microsoft/kiota/releases/download/v${VERSION}/${PACKAGE_NAME}.zip"

if [[ ! -f $SCRIPT_DIR/target/kiota_tmp/kiota ]]
then
  echo "Local kiota could not be found, downloading"
  rm -rf $SCRIPT_DIR/target/kiota_tmp
  mkdir -p $SCRIPT_DIR/target/kiota_tmp
  curl -sL $URL > $SCRIPT_DIR/target/kiota_tmp/kiota.zip
  unzip $SCRIPT_DIR/target/kiota_tmp/kiota.zip -d $SCRIPT_DIR/target/kiota_tmp
  chmod u+x $SCRIPT_DIR/target/kiota_tmp/kiota
fi
COMMAND="$SCRIPT_DIR/target/kiota_tmp/kiota"

rm -rf $SCRIPT_DIR/pkg/registryclient-v2
mkdir -p $SCRIPT_DIR/pkg/registryclient-v2
rm -rf $SCRIPT_DIR/pkg/registryclient-v3
mkdir -p $SCRIPT_DIR/pkg/registryclient-v3

cp $SCRIPT_DIR/../common/src/main/resources/META-INF/openapi-v2.json $SCRIPT_DIR/v2.json
cp $SCRIPT_DIR/../common/src/main/resources/META-INF/openapi.json $SCRIPT_DIR/v3.json

$COMMAND generate \
  --language go \
  --openapi $SCRIPT_DIR/v2.json \
  --clean-output \
  -o $SCRIPT_DIR/pkg/registryclient-v2 \
  --namespace-name github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2

$COMMAND generate \
  --language go \
  --openapi $SCRIPT_DIR/v3.json \
  --clean-output \
  -o $SCRIPT_DIR/pkg/registryclient-v3 \
  --namespace-name github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3
