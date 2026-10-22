# GitOps Operator Examples

Example `ApicurioRegistry3` custom resources for deploying Apicurio Registry in GitOps mode
using the Kubernetes operator. Each example includes deployment instructions in the file header.

For Docker Compose examples, see [`examples/gitops/`](../../../../../../../examples/gitops/).
For GitOps storage documentation, see the [GitOps README](../../../../../../../app/src/main/java/io/apicurio/registry/storage/impl/gitops/README.md).

## Examples

| Example | Description | Internet Required |
|---------|-------------|:-----------------:|
| [Pull HTTPS](example-pull-https.yaml) | Simplest setup — pulls from a public Git repo | Yes |
| [Pull SSH](example-pull-ssh.yaml) | Pulls from a private repo using SSH deploy key | Yes |
| [Push](example-push.yaml) | Accepts `git push` over SSH — no outbound Git access needed | No |
| [Multi-Repo](example-multi-repo.yaml) | Aggregates schemas from multiple repos/branches | Yes |
| [Local Volume](example-local-volume.yaml) | Self-contained using ConfigMap + init container | No |

## Quick Start

1. Deploy the operator (see [operator README](../../../../../../../operator/README.md))
2. Apply an example CR:
   ```bash
   kubectl apply -f example-pull-https.yaml
   ```
3. Wait for the registry to become ready:
   ```bash
   kubectl wait --for=condition=Ready pod -l app=gitops-pull-https --timeout=180s
   ```
4. Verify:
   ```bash
   kubectl port-forward svc/gitops-pull-https-app-service 8080:8080
   curl http://localhost:8080/apis/registry/v3/groups
   ```

## Registry ID Matching

The `registryId` field in the CR must match the `registryId` in your Git data files
(`registry.registry.yaml`). If they don't match, the registry will start but not load any data.

For example, if your CR has:
```yaml
gitops:
  registryId: prod
```

Then your `registry.registry.yaml` must contain:
```yaml
$type: registry-v0
registryId: prod
```

And your group/artifact files should include `prod` in their `registryIds` list:
```yaml
$type: group-v0
registryIds: [prod]
```

The default `registryId` when omitted from the CR is `default`.

## Test Data

The [gitops-test-data.configmap.yaml](gitops-test-data.configmap.yaml) provides a minimal
self-contained dataset for testing. It's used by the local volume example and the integration tests.
Deploy it before using the local volume example:

```bash
kubectl apply -f gitops-test-data.configmap.yaml
kubectl apply -f example-local-volume.yaml
```

## SSH Secrets

SSH secrets are configured via `secretRef` fields under `gitops.pull` and `gitops.push`.
The operator handles volume mounting and env var configuration automatically.

| Field | Secret Content | Default Key |
|-------|---------------|-------------|
| `pull.sshKeys` | SSH private key for authenticating to remote Git servers | `id_ed25519` |
| `pull.knownHosts` | known_hosts file for host verification | `known_hosts` |
| `push.authorizedKeys` | authorized_keys controlling push access | `authorized_keys` |
| `push.hostKey` | Persistent SSH host key for stable fingerprint | `ssh_host_key` |

Example:
```yaml
gitops:
  repos:
    - url: git@github.com:my-org/schemas.git
  pull:
    sshKeys:
      name: my-ssh-keys       # Secret name
      key: id_ed25519          # Key within the Secret (optional, uses default)
```

## What the Operator Does

When `storage.type: gitops` is set, the operator automatically:

- Sets `APICURIO_STORAGE_KIND=gitops` and `APICURIO_FEATURES_EXPERIMENTAL_ENABLED=true`
- Injects the GitOps sync sidecar container with a default image
- Creates an `emptyDir` shared volume mounted on both containers
- Sets repo configuration env vars on both the sidecar and registry containers
- Mounts SSH secrets from `pull`/`push` secretRef fields on the sidecar container
- In push mode (`mode: push`): creates an SSH service on port 2222 and opens the network policy

Additional configuration (extra env vars, custom sidecar image) is done via `podTemplateSpec`
overrides. The operator merges your overrides with its defaults.
