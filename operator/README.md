# Apicurio Registry Operator

This Apicurio Registry subproject makes use of `make` to execute common tasks. To show an overview of the available
commands, run `make help`. To show the current configuration, run `make config-show`. Configuration is passed either as
an environment variable, or with the command, e.g. `make SKIP_TESTS=true build`. This README assumes you are in the same directory, unless stated otherwise.

## Prerequisites

### Build

| Tool            | Version |
|-----------------|---------|
| JDK             | 17      |
| Maven           | TODO    |
| Docker / Podman | TODO    |

### Test and Deploy

| Platform   | Version |
|------------|---------|
| Kubernetes | 1.25+   |
| OpenShift  | 4.12+   |

## Quickstart

### Published Version Quickstart (TODO)

You can install a published version of the Apicurio Registry Operator from the OperatorHub, or Operator Marketplace (on
OpenShift). Alternatively, you can use the following steps:

1. Log in to your Kubernetes or OpenShift cluster with `kubectl` or `oc`.
2. Choose a namespace where the operator will be deployed:
   ```shell
   export NAMESPACE=apicurio-registry
   ```
3. Choose a released version, e.g.:
   ```shell
   export VERSION=TODO
   ```
   You can use `main` to install the latest development version.
4. Run:
   ```shell
   curl -sSL "https://raw.githubusercontent.com/Apicurio/apicurio-registry/$VERSION/operator/install/install.yaml" | sed "s/PLACEHOLDER_NAMESPACE/$NAMESPACE/g" | kubectl -n $NAMESPACE apply -f -
   kubectl -n $NAMESPACE apply -f controller/src/main/deploy/examples/simple.apicurioregistry3.yaml
   ```

### Local Development Quickstart

For the fastest iteration during development, you can run the operator on your local machine, against a local or remote cluster.

The following steps have been tested for OpenShift:

1. Log in to your OpenShift cluster with `kubectl` or `oc`.
2. Choose a namespace where the operator will be deployed:
   ```shell
   export NAMESPACE=apicurio-registry
   ```
3. Build the operator:
   ```shell
   make SKIP_TESTS=true build
   ```
   *NOTE: This step only has to be repeated when the API model changes.*

4. Run:
   ```shell
   make dev
   ```
   This will run the operator in Quarkus development mode with live reload.

5. Apply an example Apicurio Registry CR:
   ```shell
   kubectl apply -f controller/src/main/deploy/examples/simple.apicurioregistry3.yaml
   ```

### On-cluster Development Quickstart

1. Create an image repository for your operator build, e.g. `quay.io/foo/apicurio-registry-operator`:
    ```shell
   export IMAGE_REGISTRY=quay.io/foo
    ```
2. Log in to your Kubernetes or OpenShift cluster with `kubectl` or `oc`.
3. Create a namespace where the operator will be deployed:
    ```shell
   export NAMESPACE=apicurio-registry
    ```
4. Run:
    ```shell
   make SKIP_TESTS=true quickstart
    ```
5. Deploy Apicurio Registry:
    ```shell
   kubectl apply -f controller/src/main/deploy/examples/simple.apicurioregistry3.yaml
    ```

After you're done, run `make undeploy`.

### Step-by-Step On-cluster Development Quickstart

To build the operator executable, run:

```shell
make build
```

Available options:

| Option     | Type             | Default value | Description               |
|------------|------------------|---------------|---------------------------|
| SKIP_TESTS | `true` / `false` | `false`       | -                         |
| BUILD_OPTS | string           | -             | Additional Maven options. |

*NOTE: The operator is part of Apicurio Registry within a single multi-module Maven project. You can skip this step if you
have built the entire project already.*

then, to build the operator image, run:

```shell
make image-build
```

Available options:

| Option               | Type   | Default value                  | Description                           |
|----------------------|--------|--------------------------------|---------------------------------------|
| IMAGE_REGISTRY       | string | `quay.io/apicurio`             | -                                     |
| IMAGE_NAME           | string | `apicurio-registry-operator`   | -                                     |
| IMAGE_TAG            | string | *(current version, lowercase)* | -                                     |
| ADDITIONAL_IMAGE_TAG | string | -                              | Tag the image with an additional tag. |

After the image is built, push it by running:

```shell
make image-push
```

*Options are the same as `image-build`.*

You can now deploy the operator to your current cluster (as configured by `kubectl`):

```shell
make deploy
```

Available options:

| Option             | Type   | Default value                                           | Description                                       |
|--------------------|--------|---------------------------------------------------------|---------------------------------------------------|
| NAMESPACE          | string | `default`                                               | Namespace to which the operator will be deployed. |
| REGISTRY_APP_IMAGE | string | `quay.io/apicurio/apicurio-registry:latest-snapshot`    | -                                                 |
| REGISTRY_UI_IMAGE  | string | `quay.io/apicurio/apicurio-registry-ui:latest-snapshot` | -                                                 |
| STUDIO_UI_IMAGE    | string | `quay.io/apicurio/apicurio-studio-ui:latest-snapshot`   | -                                                 |

To remove the operator from your cluster, run:

```shell
make undeploy
```

Available options:

| Option    | Type   | Default value | Description                                       |
|-----------|--------|---------------|---------------------------------------------------|
| NAMESPACE | string | `default`     | Namespace to which the operator will be deployed. |

## Testing

*NOTE: This section is specific to the `operator/controller` and `operator/olm-tests` modules, since the tests in the `operator/model` are very simple.*

There are 3 ways to run the operator tests:

- `local` runs the operator on the developer machine (**the default**).
- `remote` runs the operator in a cluster (requires additional prerequisites, see below).
- `olm` runs the OLM tests with the operator deployed in a cluster (these are located in a separate Maven module `operator/olm-tests`).

The Maven property `-DskipOperatorTests=false` is used to explicitly enable the testing of the operator modules, since they require a cluster to run against.

### Local Tests

1. Create a Minikube cluster, unless you already have a cluster available:
    ```shell
   minikube start
    ```

2. To enable testing of Ingresses on Minikube, run (in a separate terminal):
   ```shell
   minikube addons enable ingress
   minikube tunnel
   ```

3. Run:
   ```shell
   mvn clean verify -pl controller -am -DskipOperatorTests=false
   ```
   or
   ```shell
   make build
   ```

Available configuration options:

| Option                          | Type                      | Default value | Description                                                                                                                                             |
|---------------------------------|---------------------------|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| test.operator.deployment        | `local` / `remote`        | `local`       | Specifies the way that the operator is deployed for testing.                                                                                            |
| test.operator.ingress-skip      | `true` / `false`          | `false`       | Skip testing of Ingresses. Useful when testing on clusters without an Ingress controller or without an accessible base hostname.                        |
| test.operator.ingress-host      | string                    | -             | Used when testing Ingresses. For some clusters, you might need to provide the base hostname from where the applications on your cluster are accessible. |
| test.operator.cleanup           | `true` / `false`          | `true`        | Clean test namespaces from the cluster after the tests finish.                                                                                          |

### Remote Tests

1. Create a Minikube cluster, unless you already have a cluster available:
    ```shell
   minikube start
    ```

2. To enable testing of Ingresses on Minikube, run (in a separate terminal):
   ```shell
   minikube addons enable ingress
   minikube tunnel
   ```

3. Build and push the operator image:
   ```shell
   make SKIP_TESTS=true build image-build image-push
   ```

4. Generate operator test install file:
   ```shell
   make INSTALL_FILE=controller/target/test-install.yaml dist-install-file
   ```

5. Run:
   ```shell
   mvn verify -pl controller -am -DskipOperatorTests=false -Dtest.operator.deployment=remote
   ```

*NOTE: Running `mvn clean` will delete controller/target/test-install.yaml, so it has to be run before step 3, if needed.*

Configuration options for the remote tests are same as those for the local tests, but the following options are additionally available:

| Option                          | Type                      | Default value                                                 | Description                                                                                                                                                                                                                                                                       |
|---------------------------------|---------------------------|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| test.operator.deployment-target | `kubernetes` / `minikube` | `kubernetes`                                                  | Modify the deployment for the given cluster type. *NOTE: This should only be necessary for minikube with a shared docker daemon, but the OLM tests still require the bundle and catalog images to be pushed to a remote registry. Please report to us if you find out otherwise.* |
| test.operator.install-file      | string                    | `${projectRoot}/operator/controller/target/test-install.yaml` | The install file that is used to deploy the operator for testing, must be generated before testing. *NOTE: More information about the install file are below in the __Distribution and Release__ section.*                                                                        |
                                                                                |

### Remote Tests with OLM Tests

OLM tests are similar to the remote tests in that the operator is deployed into the target cluster. However, they are located in a separate Maven module `operator/olm-tests`, and require the bundle and catalog images to have been built. You can control whether they are executed by using Maven options `-pl` and `-am`. The following steps will run both the remote tests and the OLM tests:

1. Create a Minikube cluster, unless you already have a cluster available:
    ```shell
   minikube start
    ```

2. To enable testing of Ingresses and OLM on Minikube, run (in a separate terminal):
   ```shell
   minikube addons enable ingress
   minikube addons enable olm
   minikube tunnel
   ```

3. Build and push the operator image, bundle image, and catalog image:
   ```shell
   make SKIP_TESTS=true build image-build image-push bundle catalog
   ```

4. Run:
   ```shell
   make INSTALL_FILE=controller/target/test-install.yaml dist-install-file
   mvn verify -DskipOperatorTests=false -Dtest.operator.deployment=remote -Dtest.operator.catalog-image=$(make VAR=CATALOG_IMAGE get-variable)
   ```
   or
   ```shell
   make remote-tests-all
   ```
   for convenience.

Configuration options for the remote + OLM tests are same as those for the remote tests, but the following options are additionally available:

| Option                      | Type             | Default value | Description                                                             |
|-----------------------------|------------------|---------------|-------------------------------------------------------------------------|
| test.operator.catalog-image | string           | -             | Catalog image that is used to deploy the operator for testing with OLM. |

## Distribution and Release

### Install File

You can create an installation file with the resources required to run the operator as follows:

```shell
make dist-install-file
```

Available options:

| Option             | Type   | Default value                                                   | Description |
|--------------------|--------|-----------------------------------------------------------------|-------------|
| INSTALL_FILE       | string | `install/apicurio-registry-operator-`*(current version)*`.yaml` | -           |
| INSTALL_NAMESPACE  | string | `PLACEHOLDER_NAMESPACE`                                         | -           |
| IMAGE_REGISTRY     | string | `quay.io/apicurio`                                              | -           |
| IMAGE_NAME         | string | `apicurio-registry-operator`                                    | -           |
| IMAGE_TAG          | string | *(current version, lowercase)*                                  | -           |
| REGISTRY_APP_IMAGE | string | `quay.io/apicurio/apicurio-registry:latest-snapshot`            | -           |
| REGISTRY_UI_IMAGE  | string | `quay.io/apicurio/apicurio-registry-ui:latest-snapshot`         | -           |
| STUDIO_UI_IMAGE    | string | `quay.io/apicurio/apicurio-studio-ui:latest-snapshot`           | -           |

*NOTE: The CRD file must have been generated using `make build`.*

### Distribution Archive

You can also create a `tar.gz` archive that contains the installation file, installation instructions, examples, license
information, and other by running:

```shell
make dist
```

Available options:

| Option             | Type   | Default value                                           | Description |
|--------------------|--------|---------------------------------------------------------|-------------|
| IMAGE_REGISTRY     | string | `quay.io/apicurio`                                      | -           |
| IMAGE_NAME         | string | `apicurio-registry-operator`                            | -           |
| IMAGE_TAG          | string | *(current version, lowercase)*                          | -           |
| REGISTRY_APP_IMAGE | string | `quay.io/apicurio/apicurio-registry:latest-snapshot`    | -           |
| REGISTRY_UI_IMAGE  | string | `quay.io/apicurio/apicurio-registry-ui:latest-snapshot` | -           |
| STUDIO_UI_IMAGE    | string | `quay.io/apicurio/apicurio-studio-ui:latest-snapshot`   | -           |

*NOTE: The CRD file and licenses must have been generated using `make build`.*

## OLM

### Operator Bundle

You can create an OLM bundle files by running:

```shell
make bundle-build
```

Available options:

| Option                   | Type             | Default value                  | Description |
|--------------------------|------------------|--------------------------------|-------------|
| BUNDLE_CHANNEL           | string           | `3.x`                          | -           |
| BUNDLE_VERSION           | string           | *(current version, lowercase)* | -           |
| PREVIOUS_PACKAGE_VERSION | string           | **TODO**                       | -           |

*NOTE: The CRD file must have been generated using `make build`.*

Then, to create a bundle image, run:

```shell
make bundle-image-build
```

Available options:

| Option                | Type   | Default value                       | Description                           |
|-----------------------|--------|-------------------------------------|---------------------------------------|
| IMAGE_REGISTRY        | string | `quay.io/apicurio`                  | -                                     |
| BUNDLE_IMAGE_NAME     | string | `apicurio-registry-operator-bundle` | -                                     |
| BUNDLE_IMAGE_TAG      | string | *(current version, lowercase)*      | -                                     |
| ADDITIONAL_BUNDLE_TAG | string | -                                   | Tag the image with an additional tag. |

After the bundle image is built, push it by running:

```shell
make bundle-image-push
```

*Options are the same as `bundle-image-build`.*

### Operator Catalog

After you have built and pushed the bundle image, you can build a catalog to use with OLM:

*NOTE: We do not currently release our own upstream catalog image, we only build one for testing.*

```shell
make catalog-build
```

Then, to create a catalog image, run:

```shell
make catalog-image-build
```

Available options:

| Option                 | Type   | Default value                                                       | Description                           |
|------------------------|--------|---------------------------------------------------------------------|---------------------------------------|
| IMAGE_REGISTRY         | string | `quay.io/apicurio`                                                  | -                                     |
| CATALOG_IMAGE_NAME     | string | `apicurio-registry-operator-catalog`                                | -                                     |
| CATALOG_IMAGE_TAG      | string | *(current version, lowercase)*                                      | -                                     |
| ADDITIONAL_CATALOG_TAG | string | `latest` *(with version suffix, lowercase, e.g. `latest-snapshot`)* | Tag the image with an additional tag. |

After the catalog image is built, push it by running:

```shell
make catalog-image-push
```

*Options are the same as `catalog-image-build`.*

### OLM On-cluster Quickstart

After you have built and pushed the bundle and catalog images, to deploy the operator to the cluster using OLM, run:

```shell
make catalog-deploy # Use CATALOG_NAMESPACE=openshift-marketplace for OpenShift.
make catalog-subscription-deploy # Same here.
```

Available options:

| Option            | Type   | Default value | Description                                                                                                                       |
|-------------------|--------|---------------|-----------------------------------------------------------------------------------------------------------------------------------|
| NAMESPACE         | string | `default`     | Namespace to which the operator will be deployed.                                                                                 |
| CATALOG_NAMESPACE | string | `olm`         | Namespace to which the catalog will be deployed. Usually `olm` for Minikube/Kubernetes and `openshift-marketplace` for OpenShift. |

## Notes

### Watched Namespaces

Namespace that are watched by the operator are configured using `APICURIO_OPERATOR_WATCHED_NAMESPACES` environment variable. Its value is configured to reflect the OLM annotation `olm.targetNamespaces` by default. This means that if the operator is not installed by OLM (e.g. using the install file), the annotation is empty, which means the operator will watch **all namespaces**. Because of this, cluster-level RBAC resources are used by default. In the future, we may release additional install file with reduced permissions, intended to be used when the operator only manages its own namespace.
