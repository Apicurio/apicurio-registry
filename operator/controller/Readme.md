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
