# GitOps Static Content Image Example

This example shows how to run Apicurio Registry with GitOps storage using a static, image-baked data source instead of a runtime Git sync sidecar.

## How it works

Apicurio Registry's GitOps storage reads local Git repositories via JGit, so it still requires a `.git` directory and commit metadata. In this pattern:

1. The `Dockerfile` copies `content/` (`*.registry.yaml` and schema files) into `/repo` and runs `git init` + `git commit` at **image build time**.
2. At runtime, the Kubernetes `Deployment` uses an `initContainer` to copy `/repo` into a shared `emptyDir` volume mounted at `/repos/default`.
3. Apicurio Registry starts with no Git sync sidecar and no `git` binary or network access at runtime.

> **Note:** On Kubernetes 1.31+ with the `ImageVolume` feature gate enabled (KEP-4639), you can mount the content image directly without an `initContainer`. A commented-out example is included in [`kubernetes/deployment.yaml`](kubernetes/deployment.yaml).

## Trade-offs

- **Immutable at runtime:** Data is fixed for the lifetime of the pod. Updating schemas requires building a new content image and rolling the Deployment.
- **No dry-run validation:** `POST /apis/registry/v3/admin/gitops/validate` requires the GitOps sync sidecar and will time out without it.
- **Status endpoint:** `GET /apis/registry/v3/admin/gitops/status` works in-process and reports the baked commit SHA in `sources`, but there is no live remote sync status.

## Running the example

1. Build the static content image:

   ```bash
   docker build -t apicurio-registry-gitops-content:latest .
   ```

2. Load the image into your cluster if running locally (for example, `kind load docker-image apicurio-registry-gitops-content:latest` or `minikube image load apicurio-registry-gitops-content:latest`), then apply the manifest:

   ```bash
   kubectl apply -f kubernetes/deployment.yaml
   ```

3. Port-forward and verify the loaded artifact and GitOps status:

   ```bash
   kubectl port-forward svc/apicurio-registry-gitops-static 8080:8080
   curl http://localhost:8080/apis/registry/v3/groups/orders/artifacts
   curl http://localhost:8080/apis/registry/v3/admin/gitops/status
   ```
