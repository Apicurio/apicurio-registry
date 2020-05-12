#!/bin/sh

echo "----"
echo "Initializing development environment for UI-only development."
echo "----"

cp config/*.* packages/registry/src

echo "Done.  Try:  'yarn start:registry'"
