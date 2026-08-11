#!/bin/sh
echo "----"
echo "Initializing development environment for UI-only development."
echo "----"
CONFIG_TYPE=$1
if [ "x$CONFIG_TYPE" = "x" ]
then
  CONFIG_TYPE="local"
fi
cp configs/version.js version.js
cp configs/config-$CONFIG_TYPE.js config.js

SDK_DIR="../../typescript-sdk"
GENERATED_CLIENT_DIR="$SDK_DIR/lib/generated-client"

if [ ! -d "$GENERATED_CLIENT_DIR" ]
then
  echo "----"
  echo "typescript-sdk generated client not found (fresh clone detected)."
  echo "Building typescript-sdk now: npm install, generate-sources, build..."
  echo "----"
  (cd "$SDK_DIR" && npm install && npm run generate-sources && npm run build)
  if [ $? -ne 0 ]
  then
    echo "ERROR: Failed to build typescript-sdk. See errors above."
    exit 1
  fi
  echo "----"
  echo "typescript-sdk build complete."
  echo "----"
fi

echo "Done.  Try:  'npm run dev'"
