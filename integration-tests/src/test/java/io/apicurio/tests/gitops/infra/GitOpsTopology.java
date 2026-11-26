package io.apicurio.tests.gitops.infra;

import io.apicurio.registry.utils.tests.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;

public class GitOpsTopology implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitOpsTopology.class);

    private static final String SIDECAR_IMAGE = System.getProperty(
            "gitops-sync-image",
            "quay.io/apicurio/apicurio-registry-gitops-sync:latest-snapshot");

    private static final String REGISTRY_IMAGE = System.getProperty(
            "registry3-image",
            "quay.io/apicurio/apicurio-registry:latest-snapshot");

    private final Network network;
    private final GitServerContainer gitServer;
    private final GenericContainer<?> sidecar;
    private final GenericContainer<?> registry;
    private final Path sharedVolume;

    public GitOpsTopology() throws IOException {
        this.sharedVolume = Files.createTempDirectory("gitops-topology-");
        sharedVolume.toFile().setReadable(true, false);
        sharedVolume.toFile().setWritable(true, false);
        sharedVolume.toFile().setExecutable(true, false);
        this.network = Network.newNetwork();

        this.gitServer = new GitServerContainer(network);

        this.sidecar = new GenericContainer<>(SIDECAR_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("gitops-sync")
                .withEnv("APICURIO_GITOPS_MODE", "pull")
                .withEnv("APICURIO_GITOPS_REPO_URL", gitServer.getGitUrl())
                .withEnv("APICURIO_GITOPS_REPO_DIR", "default")
                .withEnv("APICURIO_GITOPS_REPO_BRANCH", "main")
                .withEnv("APICURIO_GITOPS_PULL_INTERVAL", "5")
                .withEnv("APICURIO_GITOPS_SECURITY", "dev")
                .withEnv("APICURIO_GITOPS_WORKSPACE", "/repos")
                .withFileSystemBind(sharedVolume.toString(), "/repos", BindMode.READ_WRITE)
                .waitingFor(Wait.forLogMessage(".*Clone complete.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(120)));

        this.registry = new GenericContainer<>(REGISTRY_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("registry")
                .withEnv("APICURIO_STORAGE_KIND", "gitops")
                .withEnv("APICURIO_STORAGE_SQL_KIND", "h2")
                .withEnv("APICURIO_FEATURES_EXPERIMENTAL_ENABLED", "true")
                .withEnv("APICURIO_GITOPS_WORKSPACE", "/repos")
                .withEnv("APICURIO_GITOPS_REPO_DIR", "/repos/default")
                .withEnv("APICURIO_GITOPS_REPO_BRANCH", "main")
                .withEnv("APICURIO_POLLING_STORAGE_ID", GitOpsTestFixture.REGISTRY_ID)
                .withEnv("APICURIO_POLLING_STORAGE_POLL_PERIOD", "PT5S")
                .withFileSystemBind(sharedVolume.toString(), "/repos", BindMode.READ_ONLY)
                .withExposedPorts(8080, 9000)
                .waitingFor(Wait.forHttp("/health/ready").forPort(9000)
                        .withStartupTimeout(Duration.ofSeconds(120)));
    }

    public void start() {
        final long startTime = System.currentTimeMillis();

        LOGGER.info("Starting git-server...");
        gitServer.start();
        gitServer.followOutput(new Slf4jLogConsumer(LOGGER).withPrefix("git-server"));

        LOGGER.info("Starting sidecar (pull mode, interval=5s)...");
        sidecar.start();
        sidecar.followOutput(new Slf4jLogConsumer(LOGGER).withPrefix("sidecar"));

        LOGGER.info("Starting registry (gitops mode)...");
        registry.start();
        registry.followOutput(new Slf4jLogConsumer(LOGGER).withPrefix("registry"));

        final long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("Full topology started in {} ms. Registry at {}", elapsed, getRegistryUrl());
    }

    public String getRegistryUrl() {
        return TestUtils.getRegistryV3ApiUrl(registry.getMappedPort(8080));
    }

    public String getHostGitUrl() {
        return gitServer.getHostGitUrl();
    }

    public GenericContainer<?> getRegistryContainer() {
        return registry;
    }

    @Override
    public void close() {
        LOGGER.info("Stopping topology...");
        try {
            registry.stop();
        } catch (Exception e) {
            LOGGER.warn("Error stopping registry", e);
        }
        try {
            sidecar.stop();
        } catch (Exception e) {
            LOGGER.warn("Error stopping sidecar", e);
        }
        try {
            gitServer.stop();
        } catch (Exception e) {
            LOGGER.warn("Error stopping git-server", e);
        }
        try {
            network.close();
        } catch (Exception e) {
            LOGGER.warn("Error closing network", e);
        }
        try {
            Files.walk(sharedVolume)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            LOGGER.trace("Could not delete {}", p, e);
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("Error cleaning shared volume", e);
        }
    }
}
