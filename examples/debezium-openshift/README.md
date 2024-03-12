# Debezium and Apicurio Registry on OpenShift

This example contains a simple application that uses Debezium with Apicurio Registry, deployed on OpenShift.

## Prerequisites

1.&nbsp;Prepare or provision an OpenShift cluster.

2.&nbsp;Install the following operators:

- AMQ Streams (tested on `2.5.0-0` / Kafka `3.4`)
- Red Hat Integration - Service Registry Operator (tested on `2.2.2`)

3.&nbsp;Configure `oc`:

```shell
oc login #...
export NAMESPACE="example"
oc new-project $NAMESPACE
```

4.&nbsp;Prepare an image repository for example app images, and configure:

```shell
export APP_IMAGE_GROUP="quay.io/myorg"  
```

which will result in `quay.io/myorg/apicurio-registry-examples-debezium-openshift:latest` image name.

5.&nbsp;Prepare an image repository for customized Kafka Connect images, and configure:

```shell
export KAFKA_CONNECT_IMAGE="$APP_IMAGE_GROUP/kafka-connect-example:latest"
```

which will result in `quay.io/myorg/kafka-connect-example:latest` image name.

6.&nbsp;Create a pull secret for the customized Kafka Connect image repository. This example command creates it from
your local docker config file:

```shell
oc create secret generic example-components-pull-secret \
  --from-file=.dockerconfigjson=$HOME/.docker/config.json \
  --type=kubernetes.io/dockerconfigjson
```

## Deploy example components: MySQL, Kafka, and Debezium Kafka connector

Review the *example-components.yaml* template, then apply it:

```shell
oc process -f example-components.yaml \
  -p NAMESPACE=$NAMESPACE \
  -p KAFKA_CONNECT_IMAGE=$KAFKA_CONNECT_IMAGE \
  | oc apply -f -
```

Wait for all components to deploy (some pods may be failing for a short time).

After some time, you should be able to see the topics created by Debezium, for example:

```shell
oc get --no-headers -o custom-columns=":metadata.name" kafkatopic
```

```
connect-cluster-configs
connect-cluster-offsets
connect-cluster-status
consumer-offsets---84e7a678d08f4bd226872e5cdd4eb527fadc1c6a
example
example.inventory.addresses
example.inventory.customers
example.inventory.orders
example.inventory.products
example.inventory.products-on-hand---406eef91b4bed15190ce4cbe31cee9b5db4c0133
kafkasql-journal
schema-changes.inventory
strimzi-store-topic---effb8e3e057afce1ecf67c3f5d8e4e3ff177fc55
strimzi-topic-operator-kstreams-topic-store-changelog---b75e702040b99be8a9263134de3507fc0cc4017b
```

Apicurio Registry should contain the AVRO schemas registered by Debezium. Get and configure Apicurio Registry URL by
running `oc route`:

```shell
export REGISTRY_URL="http://example-components-registry.example.router-default.apps.mycluster.com"
```

Then, you can list the schemas using the following example command:

```shell
curl -s "$REGISTRY_URL/apis/registry/v2/search/artifacts?limit=50&order=asc&orderby=name" \
  | jq -r ".artifacts[] | .id" \
  | sort
```

```
event.block
example.inventory.addresses-key
example.inventory.addresses-value
example.inventory.addresses.Value
example.inventory.customers-key
example.inventory.customers-value
example.inventory.customers.Value
example.inventory.orders-key
example.inventory.orders-value
example.inventory.orders.Value
example.inventory.products-key
example.inventory.products_on_hand-key
example.inventory.products_on_hand-value
example.inventory.products_on_hand.Value
example.inventory.products-value
example.inventory.products.Value
example-key
example-value
io.debezium.connector.mysql.Source
io.debezium.connector.schema.Change
io.debezium.connector.schema.Column
io.debezium.connector.schema.Table
```

From the Apicurio Registry URL, we can extract the `INGRESS_ROUTER_CANONICAL_HOSTNAME` variable that will be used later:

```shell
export INGRESS_ROUTER_CANONICAL_HOSTNAME="router-default.apps.mycluster.com"
```

## Build the example application

```shell
mvn clean install \
  -Dregistry.url="$REGISTRY_URL" \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.group=$APP_IMAGE_GROUP \
  -Dquarkus.container-image.tag=latest
```

Push the application image:

```shell
docker push $APP_IMAGE_GROUP/apicurio-registry-examples-debezium-openshift:latest
```

Apply the application template:

```shell
oc process -f example-app.yaml \
  -p NAMESPACE=$NAMESPACE \
  -p APP_IMAGE_GROUP=$APP_IMAGE_GROUP \
  -p INGRESS_ROUTER_CANONICAL_HOSTNAME=$INGRESS_ROUTER_CANONICAL_HOSTNAME \
  | oc apply -f -
```

## Run the example:

```shell
curl -v -X POST -d 'start' http://example-app.$NAMESPACE.$INGRESS_ROUTER_CANONICAL_HOSTNAME/api/command
```
