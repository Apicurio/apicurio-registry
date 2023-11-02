#!/bin/sh

echo "----"
echo "Initializing development environment for UI-only development."
echo "----"

CONFIG_TYPE=$1

if [ "x$CONFIG_TYPE" = "x" ]
then
  CONFIG_TYPE="none"
fi

cp config/version.js version.js
cp config/config-$CONFIG_TYPE.js config.js

echo "Done.  Try:  'npm run dev'"
