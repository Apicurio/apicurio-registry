#!/bin/sh

echo "----"
echo "Initializing development environment for UI-only development."
echo "----"

cp config/*.* packages/studio/src

echo "Done.  Try:  'yarn start:studio'"
