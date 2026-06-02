package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.Configuration;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.GitOpsMode;
import io.apicurio.registry.operator.api.v1.spec.GitOpsPullSpec;
import io.apicurio.registry.operator.api.v1.spec.GitOpsPushSpec;
import io.apicurio.registry.operator.api.v1.spec.GitOpsRepoSpec;
import io.apicurio.registry.operator.api.v1.spec.GitOpsSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageType;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ExecActionBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.TCPSocketActionBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.operator.EnvironmentVariables.APICURIO_FEATURES_EXPERIMENTAL_ENABLED;
import static io.apicurio.registry.operator.EnvironmentVariables.APICURIO_GITOPS_MODE;
import static io.apicurio.registry.operator.EnvironmentVariables.APICURIO_GITOPS_REPOS_BRANCH_SUFFIX;
import static io.apicurio.registry.operator.EnvironmentVariables.APICURIO_GITOPS_REPOS_DIR_SUFFIX;
import static io.apicurio.registry.operator.EnvironmentVariables.APICURIO_GITOPS_REPOS_PREFIX;
import static io.apicurio.registry.operator.EnvironmentVariables.APICURIO_GITOPS_REPOS_URL_SUFFIX;
import static io.apicurio.registry.operator.EnvironmentVariables.APICURIO_POLLING_STORAGE_ID;
import static io.apicurio.registry.operator.EnvironmentVariables.APICURIO_STORAGE_KIND;
import static io.apicurio.registry.operator.api.v1.ContainerNames.GITOPS_SYNC_CONTAINER_NAME;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;

import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class GitOps {

    private static final Logger log = LoggerFactory.getLogger(GitOps.class);

    private static final String VOLUME_NAME = "gitops-repos";
    private static final String MOUNT_PATH = "/repos";
    public static final int SSH_PORT = 2222;
    public static final String SSH_PORT_NAME = "ssh";

    public static void configureGitOps(ApicurioRegistry3 primary, Deployment deployment,
            Map<String, EnvVar> env) {
        ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp).map(AppSpec::getStorage)
                .map(StorageSpec::getGitops).ifPresent(gitOps -> {

                    addEnvVar(env, APICURIO_STORAGE_KIND, "gitops");
                    addEnvVar(env, APICURIO_FEATURES_EXPERIMENTAL_ENABLED, "true");

                    if (!isBlank(gitOps.getRegistryId())) {
                        addEnvVar(env, APICURIO_POLLING_STORAGE_ID, gitOps.getRegistryId());
                    }

                    var repos = ofNullable(gitOps.getRepos()).orElse(List.of());
                    var mode = ofNullable(gitOps.getMode()).orElse(GitOpsMode.PULL);

                    configureRegistryRepoEnvVars(repos, env);
                    configureVolume(deployment);
                    configureSidecar(gitOps, repos, mode, deployment);

                    if (mode == GitOpsMode.PUSH && repos.isEmpty()) {
                        log.warn("GitOps push mode is configured without any repos. "
                                + "Make sure to configure SSH authorized keys and volume mounts "
                                + "via podTemplateSpec for the sidecar container.");
                    }

                    log.debug("GitOps storage configured: mode={}, registryId={}, {} repo(s)",
                            mode.getValue(), gitOps.getRegistryId(), repos.size());
                });
    }

    private static void configureRegistryRepoEnvVars(List<GitOpsRepoSpec> repos, Map<String, EnvVar> env) {
        for (int i = 0; i < repos.size(); i++) {
            var repo = repos.get(i);
            var prefix = APICURIO_GITOPS_REPOS_PREFIX + i;

            addEnvVar(env, prefix + APICURIO_GITOPS_REPOS_DIR_SUFFIX,
                    !isBlank(repo.getDir()) ? repo.getDir() : "default");

            if (!isBlank(repo.getBranch())) {
                addEnvVar(env, prefix + APICURIO_GITOPS_REPOS_BRANCH_SUFFIX, repo.getBranch());
            }
        }
    }

    private static void configureVolume(Deployment deployment) {
        var podSpec = deployment.getSpec().getTemplate().getSpec();

        if (podSpec.getVolumes() == null) {
            podSpec.setVolumes(new ArrayList<>());
        }

        boolean volumeExists = podSpec.getVolumes().stream()
                .anyMatch(v -> VOLUME_NAME.equals(v.getName()));
        if (!volumeExists) {
            podSpec.getVolumes().add(new VolumeBuilder()
                    .withName(VOLUME_NAME)
                    .withEmptyDir(new EmptyDirVolumeSource())
                    .build());
        }

        podSpec.getContainers().stream()
                .filter(c -> REGISTRY_APP_CONTAINER_NAME.equals(c.getName()))
                .findFirst()
                .ifPresent(appContainer -> {
                    if (appContainer.getVolumeMounts() == null) {
                        appContainer.setVolumeMounts(new ArrayList<>());
                    }
                    boolean mountExists = appContainer.getVolumeMounts().stream()
                            .anyMatch(vm -> VOLUME_NAME.equals(vm.getName()));
                    if (!mountExists) {
                        appContainer.getVolumeMounts().add(new VolumeMountBuilder()
                                .withName(VOLUME_NAME)
                                .withMountPath(MOUNT_PATH)
                                .withReadOnly(true)
                                .build());
                    }
                });
    }

    private static void configureSidecar(GitOpsSpec gitOps, List<GitOpsRepoSpec> repos,
            GitOpsMode mode, Deployment deployment) {
        var podSpec = deployment.getSpec().getTemplate().getSpec();

        var sidecarEnv = new ArrayList<EnvVar>();

        sidecarEnv.add(new EnvVarBuilder()
                .withName(APICURIO_GITOPS_MODE)
                .withValue(mode.getValue()).build());

        for (int i = 0; i < repos.size(); i++) {
            var repo = repos.get(i);
            var prefix = APICURIO_GITOPS_REPOS_PREFIX + i;

            sidecarEnv.add(new EnvVarBuilder()
                    .withName(prefix + APICURIO_GITOPS_REPOS_DIR_SUFFIX)
                    .withValue(!isBlank(repo.getDir()) ? repo.getDir() : "default").build());

            if (!isBlank(repo.getUrl())) {
                sidecarEnv.add(new EnvVarBuilder()
                        .withName(prefix + APICURIO_GITOPS_REPOS_URL_SUFFIX)
                        .withValue(repo.getUrl()).build());
            }

            if (!isBlank(repo.getBranch())) {
                sidecarEnv.add(new EnvVarBuilder()
                        .withName(prefix + APICURIO_GITOPS_REPOS_BRANCH_SUFFIX)
                        .withValue(repo.getBranch()).build());
            }
        }

        Container sidecar = podSpec.getContainers().stream()
                .filter(c -> GITOPS_SYNC_CONTAINER_NAME.equals(c.getName()))
                .findFirst()
                .orElse(null);

        if (sidecar == null) {
            sidecar = new ContainerBuilder()
                    .withName(GITOPS_SYNC_CONTAINER_NAME)
                    .build();
            podSpec.getContainers().add(sidecar);
        }

        if (isBlank(sidecar.getImage())) {
            sidecar.setImage(Configuration.getGitOpsSyncImage());
        }

        if (sidecar.getVolumeMounts() == null) {
            sidecar.setVolumeMounts(new ArrayList<>());
        }
        boolean mountExists = sidecar.getVolumeMounts().stream()
                .anyMatch(vm -> VOLUME_NAME.equals(vm.getName()));
        if (!mountExists) {
            sidecar.getVolumeMounts().add(new VolumeMountBuilder()
                    .withName(VOLUME_NAME)
                    .withMountPath(MOUNT_PATH)
                    .build());
        }

        if (mode == GitOpsMode.PUSH) {
            if (sidecar.getPorts() == null) {
                sidecar.setPorts(new ArrayList<>());
            }
            boolean sshPortExists = sidecar.getPorts().stream()
                    .anyMatch(p -> SSH_PORT_NAME.equals(p.getName()));
            if (!sshPortExists) {
                sidecar.getPorts().add(new ContainerPortBuilder()
                        .withName(SSH_PORT_NAME)
                        .withContainerPort(SSH_PORT)
                        .withProtocol("TCP")
                        .build());
            }
        }

        // Mount SSH secrets from pull/push spec
        configureSshSecrets(gitOps, deployment, sidecarEnv);

        if (sidecar.getReadinessProbe() == null) {
            if (mode == GitOpsMode.PUSH) {
                sidecar.setReadinessProbe(new ProbeBuilder()
                        .withTcpSocket(new TCPSocketActionBuilder()
                                .withPort(new IntOrString(SSH_PORT)).build())
                        .withInitialDelaySeconds(5)
                        .withPeriodSeconds(10)
                        .build());
            } else {
                var firstRepoDir = repos.isEmpty() ? "default"
                        : (!isBlank(repos.get(0).getDir()) ? repos.get(0).getDir() : "default");
                sidecar.setReadinessProbe(new ProbeBuilder()
                        .withExec(new ExecActionBuilder()
                                .withCommand("test", "-d",
                                        MOUNT_PATH + "/" + firstRepoDir + "/.git")
                                .build())
                        .withInitialDelaySeconds(5)
                        .withPeriodSeconds(10)
                        .build());
            }
        }

        if (sidecar.getEnv() == null) {
            sidecar.setEnv(new ArrayList<>());
        }
        sidecarEnv.addAll(sidecar.getEnv());
        sidecar.setEnv(sidecarEnv);
    }

    private static void configureSshSecrets(GitOpsSpec gitOps, Deployment deployment,
            List<EnvVar> sidecarEnv) {
        // Pull SSH keys
        var sshKeys = new SecretKeyRefTool(
                ofNullable(gitOps.getPull()).map(GitOpsPullSpec::getSshKeys).orElse(null),
                "id_ed25519").withDefaultMode(0400);
        if (sshKeys.isValid()) {
            sshKeys.applySecretVolume(deployment, GITOPS_SYNC_CONTAINER_NAME);
            sidecarEnv.add(new EnvVarBuilder()
                    .withName("APICURIO_GITOPS_PULL_SSH_KEYS")
                    .withValue(sshKeys.getSecretVolumeKeyPath()).build());
        }

        // Pull known_hosts
        var knownHosts = new SecretKeyRefTool(
                ofNullable(gitOps.getPull()).map(GitOpsPullSpec::getKnownHosts).orElse(null),
                "known_hosts");
        if (knownHosts.isValid()) {
            knownHosts.applySecretVolume(deployment, GITOPS_SYNC_CONTAINER_NAME);
            sidecarEnv.add(new EnvVarBuilder()
                    .withName("APICURIO_GITOPS_PULL_SSH_KNOWN_HOSTS")
                    .withValue(knownHosts.getSecretVolumeKeyPath()).build());
        }

        // Push authorized_keys
        var authorizedKeys = new SecretKeyRefTool(
                ofNullable(gitOps.getPush()).map(GitOpsPushSpec::getAuthorizedKeys).orElse(null),
                "authorized_keys");
        if (authorizedKeys.isValid()) {
            authorizedKeys.applySecretVolume(deployment, GITOPS_SYNC_CONTAINER_NAME);
            sidecarEnv.add(new EnvVarBuilder()
                    .withName("APICURIO_GITOPS_PUSH_SSH_AUTHORIZED_KEYS")
                    .withValue(authorizedKeys.getSecretVolumeKeyPath()).build());
        }

        // Push host key
        var hostKey = new SecretKeyRefTool(
                ofNullable(gitOps.getPush()).map(GitOpsPushSpec::getHostKey).orElse(null),
                "ssh_host_key").withDefaultMode(0400);
        if (hostKey.isValid()) {
            hostKey.applySecretVolume(deployment, GITOPS_SYNC_CONTAINER_NAME);
            sidecarEnv.add(new EnvVarBuilder()
                    .withName("APICURIO_GITOPS_PUSH_SSH_HOST_KEY")
                    .withValue(hostKey.getSecretVolumeKeyPath()).build());
        }
    }

    public static boolean isEnabled(ApicurioRegistry3 primary) {
        return ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getType)
                .map(type -> type == StorageType.GITOPS)
                .orElse(false);
    }

    public static boolean isPushMode(ApicurioRegistry3 primary) {
        return isEnabled(primary) && ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getGitops)
                .map(GitOpsSpec::getMode)
                .map(mode -> mode == GitOpsMode.PUSH)
                .orElse(false);
    }

    public static String getSshServiceName(ApicurioRegistry3 primary) {
        return primary.getMetadata().getName() + "-gitops-ssh-service";
    }
}
