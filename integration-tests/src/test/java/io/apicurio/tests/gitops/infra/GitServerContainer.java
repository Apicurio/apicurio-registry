package io.apicurio.tests.gitops.infra;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

public class GitServerContainer extends GenericContainer<GitServerContainer> {

    private static final String IMAGE = "alpine/git:v2.47.2";
    private static final int GIT_DAEMON_PORT = 9418;

    public GitServerContainer(Network network) {
        super(IMAGE);
        final String script = "apk add --no-cache git-daemon && "
                + GitOpsTestFixture.seedScript()
                + " && git daemon --verbose --export-all --enable=receive-pack"
                + " --base-path=/repos --port=" + GIT_DAEMON_PORT + " /repos";
        withNetwork(network);
        withNetworkAliases("git-server");
        withCreateContainerCmdModifier(cmd -> cmd
                .withEntrypoint("sh", "-c", script)
                .withUser("root"));
        withExposedPorts(GIT_DAEMON_PORT);
        waitingFor(Wait.forLogMessage(".*Ready to rumble.*", 1)
                .withStartupTimeout(Duration.ofSeconds(60)));
    }

    public String getGitUrl() {
        return "git://git-server:" + GIT_DAEMON_PORT + "/default";
    }

    public String getHostGitUrl() {
        return "git://localhost:" + getMappedPort(GIT_DAEMON_PORT) + "/default";
    }
}
