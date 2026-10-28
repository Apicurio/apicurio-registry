#!/bin/bash

npm install --ignore-scripts http-server

./node_modules/http-server/bin/http-server

rm -r node_modules package-lock.json
