# ADR-0019: The Kubernetes Operator

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-17.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** the operator module ([`operator/`](../operator/)), its documentation
  ([`operator/docs`](../operator/docs/modules/ROOT/pages/)).
- **Scope:** How the operator turns an `ApicurioRegistry3` custom resource into a running registry:
  the CR, the dependent resources it creates and owns, the feature modules that wire storage,
  security and observability into them, status, CR migrations, and installation. Not covered: the
  registry's own configuration (the other ADRs), federation peers in the CR
  ([ADR-0018](0018-federation.md), [#8578](https://github.com/Apicurio/apicurio-registry/issues/8578)),
  and the web console and console plugin themselves ([ADR-0027](0027-web-console.md)).

## Context

On Kubernetes, a registry is more than a container: a Deployment per component (API, web console,
OpenShift console plugin), Services, Ingresses, NetworkPolicies, PodDisruptionBudgets, autoscalers,
RBAC for KubernetesOps, Secrets mounted for TLS and credentials, and environment variables that must
agree with each other. Writing these by hand drifts with every release. An operator can own them,
keep them consistent with one declarative resource, upgrade them, and report problems where users
look: on the resource.

## Decision

1. **One custom resource, `ApicurioRegistry3` (`registry.apicur.io/v1`), per registry.** `spec.app`
   (the API) and `spec.ui` (the console) are components with the same shape: `env`, `ingress`,
   `replicas`, `autoscaling`, `podDisruptionBudget`, `networkPolicy`, `podTemplateSpec`. `spec.app`
   adds `storage`, `auth`, `tls`, `otel`, `searchIndex` and `features`. `spec.consolePlugin`
   (OpenShift) only has `enabled`. `spec: {}` is a valid registry (in-memory storage, console
   included).
2. **A Java Operator SDK workflow of dependent resources.** `ApicurioRegistry3Reconciler` declares one
   dependent per Kubernetes resource (`resource/app`, `resource/ui`, `resource/consoleplugin`). Optional
   ones (Ingress, PodDisruptionBudget, NetworkPolicy, HorizontalPodAutoscaler, KubernetesOps RBAC, the
   GitOps SSH service, the console) have activation conditions, so the operator creates them only when
   the CR asks for them, and deletes them when it stops asking. Every dependent has an owner reference
   to the CR: deleting the CR deletes them.
3. **Features are modules that edit the desired Deployment.** Each `feat/*` class (`SqlStorage`,
   `KafkaSql` and its TLS and OAuth parts, `KubernetesOps`, `GitOps`, `TLS`, `Cors`, `OTel`,
   `SearchIndex`, `security/*` for authentication, authorization and Kubernetes auth) translates its part
   of the spec into environment variables, volumes and mounts on the app container. Registry
   configuration stays registry configuration: the operator sets the same `APICURIO_*` variables a user
   would.
4. **Secrets by reference.** Passwords, keystores and client secrets are `SecretKeyRef`s in the CR and
   become `secretKeyRef` environment variables or mounted volumes; their values never enter the CR or
   the Deployment.
5. **The user has the last word.** `spec.app.env` is applied first and the operator never overrides a
   variable it contains (`addEnvVar` keeps the first value); `podTemplateSpec` is merged into the
   generated template. Anything the CR has no field for is still possible.
6. **Problems are status conditions.** `Ready` reflects the availability of the active Deployments;
   `OperatorError` and `ValidationError` carry the reason (for example a JDBC URL that does not match
   `storage.type`). The CRD schema rejects malformed CRs (an unknown `storage.type`, a wrong type) at
   admission; a schema-valid CR whose values do not fit together is accepted and reported by the
   operator in its status.
7. **The CR evolves without breaking users.** Deprecated fields (the 3.0 `sql` and `kafkasql`
   locations) are migrated by updaters (`SqlCRUpdater`, `KafkaSqlCRUpdater`, `IngressCRUpdater`)
   into the current shape.
8. **The operator decides the images.** The operand images (`REGISTRY_APP_IMAGE`,
   `REGISTRY_UI_IMAGE`, ...) are configuration of the operator Deployment, so upgrading the operator
   upgrades the registries it manages.
9. **Installed cluster-wide or per namespace.** A generated install file (CRD, RBAC, Deployment)
   watches every namespace, or a namespaced variant only its own
   (`apicurio.operator.watched-namespaces`); the same operator ships as an OLM bundle for OperatorHub.

### Why environment variables

The registry is configured by MicroProfile Config; environment variables are its universal input.
Generating them keeps the operator thin: a new registry setting needs no operator release (through
`env`), and a new CR field is a small translation.

### Why dependents with activation conditions

The SDK compares desired and actual state per resource, and garbage collection follows owner
references. Conditional dependents give "create when asked, delete when no longer asked" without
hand-written cleanup.

## Model

| CR | Creates (in the CR's namespace) |
|---|---|
| `spec: {}` | `registry-app-deployment`, `registry-ui-deployment`, a Service and a NetworkPolicy for each |
| `replicas` > 1 (or autoscaling above one) | A PodDisruptionBudget for that component (app or console); not in GitOps push mode |
| `ingress.host` | An Ingress for that component |
| `autoscaling` | A HorizontalPodAutoscaler; not in GitOps push mode |
| `storage.type: kubernetesops` | A ServiceAccount, Role and RoleBinding to read ConfigMaps |
| `storage.type: gitops` (push mode) | The SSH Service for `git push`; one replica enforced, `Recreate` strategy, no HPA or PDB |
| `ui.enabled: false` | No console |

| Storage | CR | Becomes |
|---|---|---|
| PostgreSQL, MySQL | `storage.sql.dataSource` | `APICURIO_STORAGE_KIND=sql`, `APICURIO_STORAGE_SQL_KIND`, `APICURIO_DATASOURCE_*` (password from a Secret) |
| KafkaSQL | `storage.kafkasql` (+ `tls`, `auth`) | `APICURIO_STORAGE_KIND=kafkasql`, bootstrap servers, mounted keystores |
| KubernetesOps, GitOps | `storage.kubernetesops`, `storage.gitops` | The polling settings, RBAC or the sync sidecar |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| CR model | [`operator/model`](../operator/model/src/main/java/io/apicurio/registry/operator/api/v1/) | `ApicurioRegistry3` and its spec, CRD generation |
| Reconciler | [`ApicurioRegistry3Reconciler`](../operator/controller/src/main/java/io/apicurio/registry/operator/ApicurioRegistry3Reconciler.java) | The workflow of dependents |
| Dependents | [`resource`](../operator/controller/src/main/java/io/apicurio/registry/operator/resource/) | One class per Kubernetes resource, activation conditions |
| Features | [`feat`](../operator/controller/src/main/java/io/apicurio/registry/operator/feat/) | Storage, security, TLS, observability, search |
| Status | [`status`](../operator/controller/src/main/java/io/apicurio/registry/operator/status/) | Conditions |
| Migrations | [`updater`](../operator/controller/src/main/java/io/apicurio/registry/operator/updater/) | Deprecated CR fields |
| Install, OLM | [`operator/install`](../operator/install/), [`operator/Makefile`](../operator/Makefile) | Install files per release, bundle and catalog |

## Limitations and open work

1. The operator does not create databases or Kafka; it connects the registry to them.
2. Inconsistencies the schema cannot express are reported after the CR is accepted; there is no
   validating webhook.
3. The generated install file names the operator Deployment with the version
   (`apicurio-registry-operator-v3.4.0-snapshot`), which Kubernetes warns about (dots in a name).
4. The install file pulls the operator image every time (`imagePullPolicy: Always`), which needs a
   change for images loaded into a local cluster.
5. Federation peers are not part of the CR yet
   ([#8578](https://github.com/Apicurio/apicurio-registry/issues/8578)).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Helm charts only | Not selected: templates cannot reconcile, migrate fields or report status; the install files are still plain YAML |
| One CR per component | Not selected: storage, TLS and auth span components; one CR keeps them consistent |
| A CR field for every registry property | Not selected: the operator would chase every release; `env` covers the rest |
| OpenShift templates | Replaced by the operator; the `openshift-template` example is removed |

## Consequences

- **Benefits:** a registry from a few lines of YAML; resources that follow the CR, including deletion;
  upgrades by upgrading the operator; problems visible on the CR.
- **Costs:** an operator to install and upgrade; CR fields to keep in step with the registry.

## Example and verification

- **Example:** [`examples/operator`](../examples/operator/): `./demo.sh` builds the operator from the
  checkout, creates a kind cluster, installs it, and checks the smallest CR (the resources it creates
  and owns, `spec.app.env`, deletion), PostgreSQL with a Secret and a pod restart, KafkaSQL with two
  replicas and a PodDisruptionBudget, and an invalid CR in the status. CI runs it with this commit's
  images.
- **User documentation:** the operator documentation
  ([source](../operator/docs/modules/ROOT/pages/)): installation, quickstart, configuration
  reference, storage.

| Concern | Tests to read (operator controller) |
|---|---|
| Smoke, multi-namespace | `SmokeITTest`, `MultiNamespaceSmokeITTest`, `RestrictedNamespaceITTest` |
| Storage | `PostgresqlDataSourceITTest`, `MysqlDataSourceITTest`, `KafkaSqlITTest`, `KafkaSqlTLSITTest`, `KafkaSqlOAuthITTest`, `GitOpsITTest`, `KubernetesOpsRbacITTest` |
| Security | `AuthITTest`, `AuthTLSITTest`, `AuthzITTest`, `TlsITTest` |
| Dependents | `IngressITTest`, `NetworkPolicyITTest`, `PodDisruptionBudgetITTest`, `HorizontalPodAutoscalerITTest`, `DisableUIITTest` |
| Configuration, status, migration | `EnvITTest`, `PodTemplateSpecITTest`, `StatusConditionsTest`, `CRUpdateITTest` |

## History

| Milestone | Result |
|---|---|
| 3.0 | `ApicurioRegistry3` and the Java operator |
| 3.x | Feature modules: KafkaSQL TLS and OAuth, KubernetesOps, GitOps, OTel, search index, autoscaling |
| This ADR | First written record, with a verified example; `openshift-template` removed |
