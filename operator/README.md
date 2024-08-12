# Apicurio Registry Operator

This Apicurio Registry subproject makes use of `make` to execute common tasks. To show an overview of the available
commands, run `make help`. To show the current configuration, run `make config-show`. Configuration is passed either as
an environment variable, or with the command, e.g. `make SKIP_TESTS=true build`.

## Prerequisites

### Build

| Tool            | Version |
|-----------------|---------|
| JDK             | 17      |
| Maven           | TODO    |
| Docker / Podman | TODO    |

### Deploy

| Platform   | Version |
|------------|---------|
| Kubernetes | 1.25+   |
| OpenShift  | 4.12+   |

## Published Version Quickstart (TODO)

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
   kubectl -n $NAMESPACE apply -f deploy/examples/simple-apicurioregistry3.yaml
   ```

## Local Development Quickstart

For the fastest iteration during development, you can run the operator on your local machine, against a local or remote
cluster.

The following steps have been tested for OpenShift:

1. Log in to your OpenShift cluster with `kubectl` or `oc`.
2. Choose a namespace where the operator will be deployed:
   ```shell
   oc project apicurio-registry
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

## On-cluster Development Quickstart

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
   kubectl -n $NAMESPACE apply -f deploy/examples/simple-apicurioregistry3.yaml
    ```

After you're done, run `make undeploy`.

## Step-by-Step On-cluster Development Quickstart

To build the operator executable, run:

```shell
make build
```

Available options:

| Option     | Type         | Default value | Description               |
|------------|--------------|---------------|---------------------------|
| SKIP_TESTS | true / false | false         | -                         |
| BUILD_OPTS | string       | -             | Additional Maven options. |

*NOTE: The operator is part of Apicurio Registry within a single multi-module Maven project. You can skip this step if you
have built the entire project already.*

then, to build the operator image, run:

```shell
make image-build
```

Available options:

| Option               | Type   | Default value                  | Description                           |
|----------------------|--------|--------------------------------|---------------------------------------|
| IMAGE_REGISTRY       | string | quay.io/apicurio               | -                                     |
| IMAGE_NAME           | string | apicurio-registry-operator     | -                                     |
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

| Option            | Type   | Default value                                         | Description                                       |
|-------------------|--------|-------------------------------------------------------|---------------------------------------------------|
| NAMESPACE         | string | default                                               | Namespace to which the operator will be deployed. |
| REGISTRY_IMAGE    | string | quay.io/apicurio/apicurio-registry:latest-snapshot    | -                                                 |
| REGISTRY_UI_IMAGE | string | quay.io/apicurio/apicurio-registry-ui:latest-snapshot | -                                                 |

To remove the operator from your cluster, run:

```shell
make undeploy
```

Available options:

| Option    | Type   | Default value | Description                                       |
|-----------|--------|---------------|---------------------------------------------------|
| NAMESPACE | string | default       | Namespace to which the operator will be deployed. |

## Install File

You can create an installation file with the resources required to run the operator as follows:

```shell
make dist-install-file
```

Available options:

| Option            | Type   | Default value                                               | Description |
|-------------------|--------|-------------------------------------------------------------|-------------|
| INSTALL_FILE      | string | install/apicurio-registry-operator-*(current version)*.yaml | -           |
| INSTALL_NAMESPACE | string | PLACEHOLDER_NAMESPACE                                       | -           |
| IMAGE_REGISTRY    | string | quay.io/apicurio                                            | -           |
| IMAGE_NAME        | string | apicurio-registry-operator                                  | -           |
| IMAGE_TAG         | string | *(current version, lowercase)*                              | -           |
| REGISTRY_IMAGE    | string | quay.io/apicurio/apicurio-registry:latest-snapshot          | -           |
| REGISTRY_UI_IMAGE | string | quay.io/apicurio/apicurio-registry-ui:latest-snapshot       | -           |

*NOTE: The CRD file must have been generated using `make build`.*

## Distribution Archive

You can also create a `tar.gz` archive that contains the installation file, installation instructions, examples, license
information, and other by running:

```shell
make dist
```

Available options:

| Option            | Type   | Default value                                              | Description |
|-------------------|--------|------------------------------------------------------------|-------------|
| DIST_FILE         | string | dist/apicurio-registry-operator-*(current version)*.tar.gz | -           |
| IMAGE_REGISTRY    | string | quay.io/apicurio                                           | -           |
| IMAGE_NAME        | string | apicurio-registry-operator                                 | -           |
| IMAGE_TAG         | string | *(current version, lowercase)*                             | -           |
| REGISTRY_IMAGE    | string | quay.io/apicurio/apicurio-registry:latest-snapshot         | -           |
| REGISTRY_UI_IMAGE | string | quay.io/apicurio/apicurio-registry-ui:latest-snapshot      | -           |

*NOTE: The CRD file must have been generated using `make build`.*

## Operator Bundle

You can create an OLM bundle files by running:

```shell
make bundle-build
```

Available options:

| Option                  | Type       | Default value                  | Description |
|-------------------------|------------|--------------------------------|-------------|
| BUNDLE_CHANNEL          | string     | 1.x                            | -           |
| BUNDLE_VERSION          | string     | *(current version, lowercase)* | -           |
| BUNDLE_REPLACES_VERSION | string     | **TODO**                       | -           |
| UPDATE_CATALOG          | true/false | **TODO**                       | -           |

*NOTE: The CRD file must have been generated using `make build`.*

Then, to create a bundle image, run:

```shell
make bundle-image-build
```

Available options:

| Option                | Type   | Default value                     | Description                           |
|-----------------------|--------|-----------------------------------|---------------------------------------|
| IMAGE_REGISTRY        | string | quay.io/apicurio                  | -                                     |
| BUNDLE_IMAGE_NAME     | string | apicurio-registry-operator-bundle | -                                     |
| BUNDLE_IMAGE_TAG      | string | *(current version, lowercase)*    | -                                     |
| ADDITIONAL_BUNDLE_TAG | string | -                                 | Tag the image with an additional tag. |

After the bundle image is built, push it by running:

```shell
make bundle-image-push
```

*Options are the same as `bundle-image-build`.*

## Operator Catalog

After you have built and pushed the bundle image, you can build a catalog to use with OLM:

```shell
make catalog-build
```

Then, to create a catalog image, run:

```shell
make catalog-image-build
```

Available options:

| Option                 | Type   | Default value                               | Description                           |
|------------------------|--------|---------------------------------------------|---------------------------------------|
| IMAGE_REGISTRY         | string | quay.io/apicurio                            | -                                     |
| CATALOG_IMAGE_NAME     | string | apicurio-registry-operator-catalog          | -                                     |
| CATALOG_IMAGE_TAG      | string | v3-latest *(and version suffix, lowercase)* | -                                     |
| ADDITIONAL_CATALOG_TAG | string | `date --utc +'%Y-%m-%d-%H-%M'`              | Tag the image with an additional tag. |

After the catalog image is built, push it by running:

```shell
make catalog-image-push
```

*Options are the same as `catalog-image-build`.*

## OLM On-cluster Quickstart

After you have built and pushed the bundle and catalog images, to deploy the operator to the cluster using OLM, run:

```shell
make catalog-deploy
make catalog-subscription-deploy
```

Available options:

| Option            | Type   | Default value         | Description |
|-------------------|--------|-----------------------|-------------|
| CATALOG_NAMESPACE | string | openshift-marketplace | -           |

*NOTE: If you encounter a pod security error on OpenShift, run with `NAMESPACE=openshift-marketplace`.*

## Additional Features (TODO)

- Run `make clean` to remove ignored files and reset automatically modified files.
