#!/usr/bin/env bash

TEST_NAMESPACE=apicurio-registry-test

kubectl create namespace $TEST_NAMESPACE

function waitForPodByLabel() {
    LABEL_SEARCH=$1
    while [[ $(kubectl get pods -n $TEST_NAMESPACE -l $LABEL_SEARCH -o 'jsonpath={..status.conditions[?(@.type=="Ready")].status}') != "True" ]]; do echo "waiting for pod $LABEL_SEARCH" && sleep 5; done
}

curl -sSL https://raw.githubusercontent.com/apicurio/apicurio-registry-operator/master/docs/resources/install.yaml | sed "s/{NAMESPACE}/$TEST_NAMESPACE/g" | kubectl apply -n $TEST_NAMESPACE -f -
waitForPodByLabel "name=apicurio-registry-operator"

curl -sSL https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.18.0/strimzi-cluster-operator-0.18.0.yaml | sed "s/namespace: .*/namespace: $TEST_NAMESPACE/g" | kubectl apply -n $TEST_NAMESPACE -f -
waitForPodByLabel "name=strimzi-cluster-operator"

kubectl apply -n $TEST_NAMESPACE -f .github/scripts/kubefiles/kafka
sleep 30
kubectl get pod -n $TEST_NAMESPACE

kubectl apply -n $TEST_NAMESPACE -f .github/scripts/kubefiles/apicurio/apicurio-registry-streams.yaml
sleep 5
kubectl get pod -n $TEST_NAMESPACE
waitForPodByLabel "app=apicurio-registry-streams"
kubectl get pod -n $TEST_NAMESPACE
kubectl get ingress -n $TEST_NAMESPACE

# set -a
# REGISTRY_HOST=localhost
# REGISTRY_PORT=80