#!/bin/bash

./scripts/setup_cluster.sh

mvn -Dtest=SqlNoIAM test
