# Operator: step-by-step guide

On Kubernetes, the Apicurio Registry operator turns one custom resource, `ApicurioRegistry3`, into a
running registry: deployments, services, network policies, and the configuration that wires storage,
TLS and authentication. This guide runs it on a local kind cluster and checks what it creates for each
storage, and what happens when a CR is wrong.

**Time:** about 30 minutes (the first build takes a few more). **You will see:**

- installing the operator, and the smallest CR;
- what the operator creates and owns;
- PostgreSQL storage with a Secret, and KafkaSQL with two replicas;
- configuration the CR has no field for;
- an invalid CR, reported in its status.

The reference is the operator documentation
([source](../../operator/docs/modules/ROOT/pages/)); the design is
[ADR-0019](../../adr/0019-operator.md).

## Before you start

You need kind, `kubectl`, `curl`, `jq` and Docker (with Podman, set
`KIND_EXPERIMENTAL_PROVIDER=podman`). The first run builds the operator: JDK 21 and `envsubst`.

```bash
cd examples/operator
./demo.sh
```

`demo.sh` builds the operator if needed (`build.sh`), creates the kind cluster
`apicurio-operator-example`, runs the steps below, and deletes the cluster. `KEEP=1 ./demo.sh` keeps
it: `kubectl config use-context kind-apicurio-operator-example`.

## Step 1: Install the operator

`build.sh` builds the operator image and an install file, `target/install.yaml`: the CRD, the RBAC,
and the operator's Deployment, with the registry images it will deploy (`REGISTRY_APP_IMAGE` and
`REGISTRY_UI_IMAGE` in its environment). Install it into a namespace of its own:

```bash
kubectl create namespace apicurio-operator
kubectl -n apicurio-operator apply -f target/install.yaml
```

The operator watches every namespace. With a released operator, use
`operator/install/install.yaml` (or OperatorHub) instead.

## Step 2: The smallest CR

```yaml
apiVersion: registry.apicur.io/v1
kind: ApicurioRegistry3
metadata:
  name: registry
spec: {}
```

The operator creates, in the CR's namespace, `registry-app-deployment` and `registry-ui-deployment`,
a service for each, and a network policy for each, and reports `Ready: True` in the CR's status once
the deployments are available:

```bash
kubectl get apicurioregistry3 registry -o jsonpath='{.status.conditions}'
```

With a single pod there is no PodDisruptionBudget, for the app or the console: a budget for one pod
would allow no disruption at all, and block `kubectl drain` on its node. Step 4 shows one, with two
replicas.

Everything it creates is **owned** by the CR (an owner reference), so deleting the CR deletes it.
With no storage, the registry keeps its data in memory.

**Configuration without a field.** The CR has fields for storage, TLS, authentication, ingress,
replicas and more. Anything else goes in `spec.app.env`, which wins over what the operator sets:

```yaml
spec:
  app:
    env:
      - name: APICURIO_REST_DELETION_ARTIFACT_ENABLED
        value: "true"
```

`demo.sh` checks that deletes return `405` by default, applies this, waits for the rollout, and checks
that they work.

## Step 3: PostgreSQL

[`k8s/registry-postgresql.yaml`](k8s/registry-postgresql.yaml):

```yaml
spec:
  app:
    storage:
      type: postgresql
      sql:
        dataSource:
          url: jdbc:postgresql://postgres:5432/registry
          username: registry
          password: {name: registry-db, key: password}
```

The operator turns it into environment variables (`APICURIO_STORAGE_KIND=sql`,
`APICURIO_STORAGE_SQL_KIND=postgresql`, the URL and user), with the password as a reference to the
Secret, never its value. `demo.sh` stores an artifact, deletes the registry pod, and finds the
artifact again in the new pod.

## Step 4: KafkaSQL, two replicas

[`k8s/registry-kafkasql.yaml`](k8s/registry-kafkasql.yaml) sets `storage.type: kafkasql`, the
bootstrap servers, and `replicas: 2`. The operator adds a PodDisruptionBudget, since there is now
more than one pod. `demo.sh` writes through one pod and reads through the other: both rebuild their
state from the journal topic (see [ADR-0010](../../adr/0010-storage-architecture.md)).

## Step 5: An invalid CR

Kubernetes rejects a CR that breaks the CRD schema (an unknown `storage.type`, say) when you apply it.
A CR that is valid but whose values do not fit together is accepted, and the operator reports the
problem in its status.
`demo.sh` applies `storage.type: postgresql` with a MySQL JDBC URL and finds:

```
type: OperatorError, reason: OperatorException
message: JDBC URL mismatch: storage type is 'postgresql' but JDBC URL is 'jdbc:mysql://...'
type: Ready, status: False
```

Look at the CR's conditions first when a registry does not appear.

## Step 6: Clean up

`demo.sh` deletes the cluster. After `KEEP=1`: `kind delete cluster --name apicurio-operator-example`.

## Presenter notes

- **The story in one sentence per step:** one install (1); one CR becomes a registry, and owns what
  it creates (2); storage is a few lines, with Secrets (3, 4); mistakes show up in the CR (5).
- **Questions that usually come up:**
  - *Does the operator create the database or Kafka?* No: it connects the registry to them. Use
    your database, or Strimzi for Kafka.
  - *How do I upgrade?* Upgrade the operator; it rolls the registry to the images it was built
    with.
  - *OpenShift?* The same CR; on OpenShift, ingress and the console plugin have more options.

## Troubleshooting

| Symptom | What to do |
|---|---|
| The operator pod is in `ImagePullBackOff` | The install file pulls its image every time; load the image into kind and set `imagePullPolicy: IfNotPresent` (as `demo.sh` does) |
| The CR never becomes Ready | `kubectl get apicurioregistry3 registry -o yaml`: read `status.conditions` |
| `kind create cluster` fails with Podman | Set `KIND_EXPERIMENTAL_PROVIDER=podman`, on a rootful Podman machine |
