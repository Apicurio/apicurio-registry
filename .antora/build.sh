#!/bin/sh
mkdir /work
cd /work

echo "Cloning git repository: $GIT_REPO"
git clone $GIT_REPO
cd /work/*
git checkout $GIT_BRANCH
cd $GIT_PATH

echo "Cleaning output directory."
rm -rf /antora-dist/*

echo "Running : antora $PLAYBOOK"
antora $PLAYBOOK
echo "Antora build completed successfully."

echo "Customizing output."
find ./dist -name '*.html' -exec sed -i 's/_images/assets-images/g' {} \;
find ./dist -name '_images' -execdir mv _images assets-images \;

echo "Copying files..."
cp -rf dist/* /antora-dist/.
echo "Done."
