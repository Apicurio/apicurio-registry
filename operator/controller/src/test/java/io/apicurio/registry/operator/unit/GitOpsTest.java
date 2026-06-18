package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ContainerNames;
import io.apicurio.registry.operator.feat.GitOps;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GitOpsTest {

    private static final ClassLoader CLASS_LOADER = GitOpsTest.class.getClassLoader();

    @Test
    public void testPullHttpsConfiguration() {
        var registry = deserialize("k8s/examples/gitops/example-pull-https.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();
        var deployment = createDeployment();

        GitOps.configureGitOps(registry, deployment, envVars);

        assertThat(envVars.get(EnvironmentVariables.APICURIO_STORAGE_KIND).getValue())
                .isEqualTo("gitops");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED).getValue())
                .isEqualTo("true");
        assertThat(envVars.get(EnvironmentVariables.APICURIO_POLLING_STORAGE_ID).getValue())
                .isEqualTo("prod");

        // Dir defaults to "default", no branch
        assertThat(envVars.get("APICURIO_GITOPS_REPOS_0_DIR").getValue()).isEqualTo("default");
        assertThat(envVars).doesNotContainKey("APICURIO_GITOPS_REPOS_0_BRANCH");

        // Sidecar has URL and default dir, no branch
        var sidecar = deployment.getSpec().getTemplate().getSpec().getContainers().stream()
                .filter(c -> ContainerNames.GITOPS_SYNC_CONTAINER_NAME.equals(c.getName()))
                .findFirst();
        assertThat(sidecar).isPresent();
        assertThat(sidecar.get().getEnv())
                .anyMatch(e -> "APICURIO_GITOPS_REPOS_0_URL".equals(e.getName()));
        assertThat(sidecar.get().getEnv())
                .anyMatch(e -> "APICURIO_GITOPS_REPOS_0_DIR".equals(e.getName())
                        && "default".equals(e.getValue()));
        assertThat(sidecar.get().getEnv())
                .noneMatch(e -> "APICURIO_GITOPS_REPOS_0_BRANCH".equals(e.getName()));
    }

    @Test
    public void testNoGitOpsSpec() {
        var registry = deserialize("k8s/examples/simple.apicurioregistry3.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();
        var deployment = createDeployment();

        GitOps.configureGitOps(registry, deployment, envVars);

        assertThat(envVars).isEmpty();
    }

    @Test
    public void testSidecarInjection() {
        var registry = deserialize("k8s/examples/gitops/example-multi-repo.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();
        var deployment = createDeployment();

        GitOps.configureGitOps(registry, deployment, envVars);

        var podSpec = deployment.getSpec().getTemplate().getSpec();

        // Verify volume was added
        assertThat(podSpec.getVolumes()).anyMatch(v -> "gitops-repos".equals(v.getName()));

        // Verify sidecar container was added
        var sidecar = podSpec.getContainers().stream()
                .filter(c -> ContainerNames.GITOPS_SYNC_CONTAINER_NAME.equals(c.getName()))
                .findFirst();
        assertThat(sidecar).isPresent();
        assertThat(sidecar.get().getImage()).contains("apicurio-registry-gitops-sync");

        // Verify sidecar has the volume mount
        assertThat(sidecar.get().getVolumeMounts())
                .anyMatch(vm -> "gitops-repos".equals(vm.getName()) && "/repos".equals(vm.getMountPath()));

        // Verify app container has the volume mount (writable for dry-run validation)
        var appContainer = podSpec.getContainers().stream()
                .filter(c -> "apicurio-registry-app".equals(c.getName()))
                .findFirst();
        assertThat(appContainer).isPresent();
        assertThat(appContainer.get().getVolumeMounts())
                .anyMatch(vm -> "gitops-repos".equals(vm.getName())
                        && "/repos".equals(vm.getMountPath()));
    }

    @Test
    public void testMultiRepoConfiguration() {
        var registry = deserialize("k8s/examples/gitops/example-multi-repo.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();
        var deployment = createDeployment();

        GitOps.configureGitOps(registry, deployment, envVars);

        // Registry env: both repos have dir set, registryId set
        assertThat(envVars.get(EnvironmentVariables.APICURIO_POLLING_STORAGE_ID).getValue())
                .isEqualTo("prod");
        assertThat(envVars.get("APICURIO_GITOPS_REPOS_0_DIR").getValue()).isEqualTo("platform");
        assertThat(envVars.get("APICURIO_GITOPS_REPOS_1_DIR").getValue()).isEqualTo("fulfillment");
        // Only repo 1 has branch
        assertThat(envVars).doesNotContainKey("APICURIO_GITOPS_REPOS_0_BRANCH");
        assertThat(envVars.get("APICURIO_GITOPS_REPOS_1_BRANCH").getValue()).isEqualTo("fulfillment");

        // Sidecar: both repos have URL and dir
        var sidecar = deployment.getSpec().getTemplate().getSpec().getContainers().stream()
                .filter(c -> ContainerNames.GITOPS_SYNC_CONTAINER_NAME.equals(c.getName()))
                .findFirst();
        assertThat(sidecar).isPresent();
        assertThat(sidecar.get().getEnv())
                .anyMatch(e -> "APICURIO_GITOPS_REPOS_0_URL".equals(e.getName()));
        assertThat(sidecar.get().getEnv())
                .anyMatch(e -> "APICURIO_GITOPS_REPOS_1_URL".equals(e.getName()));
        assertThat(sidecar.get().getEnv())
                .anyMatch(e -> "APICURIO_GITOPS_REPOS_1_BRANCH".equals(e.getName())
                        && "fulfillment".equals(e.getValue()));
    }

    @Test
    public void testPushMode() {
        var registry = deserialize("k8s/examples/gitops/example-push.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();
        var deployment = createDeployment();

        GitOps.configureGitOps(registry, deployment, envVars);

        assertThat(envVars.get(EnvironmentVariables.APICURIO_STORAGE_KIND).getValue())
                .isEqualTo("gitops");

        var sidecar = deployment.getSpec().getTemplate().getSpec().getContainers().stream()
                .filter(c -> ContainerNames.GITOPS_SYNC_CONTAINER_NAME.equals(c.getName()))
                .findFirst();
        assertThat(sidecar).isPresent();

        // Mode env var set to push
        assertThat(sidecar.get().getEnv())
                .anyMatch(e -> "APICURIO_GITOPS_MODE".equals(e.getName())
                        && "push".equals(e.getValue()));

        // SSH port exposed on sidecar
        assertThat(sidecar.get().getPorts())
                .anyMatch(p -> "ssh".equals(p.getName()) && p.getContainerPort() == 2222);

        // No repo env vars (no repos configured)
        assertThat(sidecar.get().getEnv())
                .noneMatch(e -> e.getName().startsWith("APICURIO_GITOPS_REPOS_"));
    }

    @Test
    public void testSidecarMergeWithExistingContainer() {
        var registry = deserialize("k8s/examples/gitops/example-pull-https.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();
        var deployment = createDeployment();

        // Pre-add a sidecar container (simulating user PTS) with custom image and env
        var existingSidecar = new ContainerBuilder()
                .withName(ContainerNames.GITOPS_SYNC_CONTAINER_NAME)
                .withImage("my-custom-sidecar:1.0")
                .addNewEnv().withName("CUSTOM_VAR").withValue("custom-value").endEnv()
                .addNewVolumeMount().withName("custom-volume").withMountPath("/custom").endVolumeMount()
                .build();
        deployment.getSpec().getTemplate().getSpec().getContainers().add(existingSidecar);

        GitOps.configureGitOps(registry, deployment, envVars);

        // Should have exactly one sidecar (not two)
        var sidecars = deployment.getSpec().getTemplate().getSpec().getContainers().stream()
                .filter(c -> ContainerNames.GITOPS_SYNC_CONTAINER_NAME.equals(c.getName()))
                .toList();
        assertThat(sidecars).hasSize(1);

        var sidecar = sidecars.get(0);

        // Custom image preserved (not overwritten)
        assertThat(sidecar.getImage()).isEqualTo("my-custom-sidecar:1.0");

        // Operator env vars prepended, user env vars preserved
        assertThat(sidecar.getEnv())
                .anyMatch(e -> "APICURIO_GITOPS_REPOS_0_URL".equals(e.getName()));
        assertThat(sidecar.getEnv())
                .anyMatch(e -> "CUSTOM_VAR".equals(e.getName()) && "custom-value".equals(e.getValue()));

        // Both volume mounts present (operator's + user's)
        assertThat(sidecar.getVolumeMounts())
                .anyMatch(vm -> "gitops-repos".equals(vm.getName()));
        assertThat(sidecar.getVolumeMounts())
                .anyMatch(vm -> "custom-volume".equals(vm.getName()));
    }

    @Test
    public void testPullSshSecretRef() {
        var registry = deserialize("k8s/examples/gitops/example-pull-ssh.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();
        var deployment = createDeployment();

        GitOps.configureGitOps(registry, deployment, envVars);

        var podSpec = deployment.getSpec().getTemplate().getSpec();

        // Verify SSH key secret volume was created with restricted permissions
        assertThat(podSpec.getVolumes())
                .anyMatch(v -> "gitops-ssh-keys-volume".equals(v.getName())
                        && v.getSecret() != null
                        && "gitops-ssh-keys".equals(v.getSecret().getSecretName())
                        && Integer.valueOf(0400).equals(v.getSecret().getDefaultMode()));

        // Verify sidecar has the SSH key volume mount and env var
        var sidecar = podSpec.getContainers().stream()
                .filter(c -> ContainerNames.GITOPS_SYNC_CONTAINER_NAME.equals(c.getName()))
                .findFirst().get();

        assertThat(sidecar.getVolumeMounts())
                .anyMatch(vm -> "gitops-ssh-keys-volume".equals(vm.getName()));

        assertThat(sidecar.getEnv())
                .anyMatch(e -> "APICURIO_GITOPS_PULL_SSH_KEYS".equals(e.getName())
                        && e.getValue().contains("id_ed25519"));
    }

    @Test
    public void testPushSecretRef() {
        var registry = deserialize("k8s/examples/gitops/example-push.yaml");
        var envVars = new LinkedHashMap<String, EnvVar>();
        var deployment = createDeployment();

        GitOps.configureGitOps(registry, deployment, envVars);

        var podSpec = deployment.getSpec().getTemplate().getSpec();

        // Verify authorized_keys secret volume was created (no restricted permissions)
        assertThat(podSpec.getVolumes())
                .anyMatch(v -> "gitops-push-authorized-keys-volume".equals(v.getName())
                        && v.getSecret() != null
                        && "gitops-push-authorized-keys".equals(v.getSecret().getSecretName()));

        // Verify sidecar has the authorized_keys env var
        var sidecar = podSpec.getContainers().stream()
                .filter(c -> ContainerNames.GITOPS_SYNC_CONTAINER_NAME.equals(c.getName()))
                .findFirst().get();

        assertThat(sidecar.getEnv())
                .anyMatch(e -> "APICURIO_GITOPS_PUSH_SSH_AUTHORIZED_KEYS".equals(e.getName())
                        && e.getValue().contains("authorized_keys"));
    }

    @Test
    void testIsEnabled() {
        var registry = deserialize("k8s/examples/gitops/example-pull-https.yaml");
        assertThat(GitOps.isEnabled(registry)).isTrue();
    }

    @Test
    void testIsNotEnabledForSimpleCR() {
        var registry = deserialize("k8s/examples/simple.apicurioregistry3.yaml");
        assertThat(GitOps.isEnabled(registry)).isFalse();
    }

    private ApicurioRegistry3 deserialize(String path) {
        return ResourceFactory.deserialize(path, ApicurioRegistry3.class, CLASS_LOADER);
    }

    private Deployment createDeployment() {
        var appContainer = new ContainerBuilder()
                .withName("apicurio-registry-app")
                .build();
        var deployment = new Deployment();
        deployment.setMetadata(new ObjectMeta());
        deployment.getMetadata().setName("test-deployment");
        deployment.setSpec(new DeploymentSpec());
        deployment.getSpec().setTemplate(new PodTemplateSpec());
        deployment.getSpec().getTemplate().setSpec(new PodSpec());
        deployment.getSpec().getTemplate().getSpec().setContainers(new ArrayList<>(List.of(appContainer)));
        return deployment;
    }
}
