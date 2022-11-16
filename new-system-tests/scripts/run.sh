#!/bin/bash

./scripts/setup_cluster.sh

mvn clean -Dtest=SqlNoIAM test
