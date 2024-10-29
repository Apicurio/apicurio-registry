# Operator Controller

This is the module containing the actual reconciliation logic.

## Build

```
mvn clean install -DskipTests
```

## Test

There are 2 ways to run the operator in those tests and they should stay interchangeable.

- `local` runs the operator on the developer machine
- `remote` runs the operator in the cluster

the Maven property `-DskipOperatorTests` is used to explicitly enable the testing of this module.

to control the execution there are a few flags:

- `test.operator.deployment-target` the deployment file to be used `kubernetes` / `minikube` / `openshift`
- `test.operator.deployment` will be `local` or `remote`
- `test.operator.cleanup` a boolean to remove all of the created resources after the test finishes

To execute the tests in `remote` mode you need to perform the following steps:

- start `minikube`
- run `eval $(minikube -p minikube docker-env)` in the teerminal yuo are going to build the container image
- build the image `mvn clean package -Dquarkus.container-image.build=true -f operator/controller/pom.xml`
- run the tests `mvn verify -f operator/controller/pom.xml -Dtest.operator.deployment=remote -Dquarkus.kubernetes.deployment-target=minikube -DskipOperatorTests=false`

#### Testing of Ingresses

To allow the testing of Ingresses when using minikube, run:
  ```shell
  minikube addons enable ingress
  minikube tunnel
  ```

On other clusters, you might need to provide `test.operator.ingress-host` property that contains the base hostname from  where applications on your cluster are accessible.

If your cluster does not have an accessible ingress host, you can skip them using `test.operator.ingress-skip=true` (**not recommended**).

## PodTemplateSpec merging

Users can set the `spec.(app/ui).podTemplateSpec` fields in `ApicurioRegistry3` CR to greatly customize Apicurio registry Kubernetes deployment. The value of these fields is merged with values set by the operator and used in the resulting `Deployment` resources for the `app` or the `ui` components. In general, the `podTemplateSpec` field
is used as the base, and default values (as described below) are merged with it. Then any additional features that the operator provides (represented by other fields in the CR, such as persistence configuration) are applied on top. This means, for example, that the `spec.(app/ui).podTemplateSpec.spec.containers[name = ...].env` field has a lower priority than the `spec.(app/ui).env` field, and therefore the environment variables must be set using the `spec.(app/ui).env` field instead.

- `spec.(app/ui).podTemplateSpec.metadata.(labels/annotations)` user can add labels and annotations, but entries set by the operator cannot be overridden.
- `spec.(app/ui).podTemplateSpec.spec.containers` user can add containers, but to modify containers of the Registry components, container names `apicurio-registry-app` or `apicurio-registry-ui` must be used.
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].image` user can override the default image (not recommended)
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].env` this field must not be used. Use `spec.(app/ui).env` field instead.
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].ports` user can add or override the default ports configuration. Ports are matched by name.
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].readinessProbe` user override this field as a whole (subfields are not merged).
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].livenessProbe` user override this field as a whole (subfields are not merged).
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].resources` user override this field as a whole (subfields are not merged).
