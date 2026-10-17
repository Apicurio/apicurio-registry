package io.apicurio.registry.cli;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.services.Client;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Verifies that a command blocked on a registry that accepts the connection but never answers
 * returns as soon as its thread is interrupted.
 * <p>
 * The CLI's Registry client has no request timeout, so this is what the {@code @Timeout} on
 * {@link AbstractCLITest} relies on: JUnit interrupts a test that runs too long, and a command
 * that ignored the interrupt would keep the test hanging until the Surefire fork timeout.
 */
@QuarkusTest
public class StalledRegistryTest {

    private static final String CONFIG_JSON = """
            {
              "installation-version": 1,
              "config": {
                "update.check-enabled": "false"
              },
              "context": {}
            }""";

    @TempDir
    Path acrHome;

    @Inject
    Config config;

    @Inject
    Client client;

    @Inject
    CommandLine.IFactory factory;

    private final List<Socket> acceptedConnections = new CopyOnWriteArrayList<>();
    private final CountDownLatch connected = new CountDownLatch(1);
    private final AtomicReference<IOException> acceptFailure = new AtomicReference<>();

    private ServerSocket stalledServer;
    private Thread acceptor;
    private CommandLine cmd;
    private StringWriter err;

    @BeforeEach
    public void setUp() throws Exception {
        // Bound to the IPv4 loopback address, and the context URL uses it too, so that "localhost"
        // resolving to ::1 cannot turn the stall into a fast "connection refused".
        stalledServer = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        final var server = stalledServer;
        acceptor = new Thread(() -> acceptAndNeverAnswer(server), "stalled-registry-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();

        Files.writeString(acrHome.resolve("config.json"), CONFIG_JSON);
        config.reset();
        config.setAcrCurrentHomePath(acrHome);
        client.reset();

        cmd = new CommandLine(new Acr(), factory);
        var out = new StringWriter();
        err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        config.setStdOut(value -> out.write(value));
        config.setStdErr(value -> err.write(value));

        assertThat(cmd.execute("context", "create", "test",
                "http://127.0.0.1:" + stalledServer.getLocalPort())).isZero();
    }

    @AfterEach
    public void tearDown() throws Exception {
        try {
            if (stalledServer != null) {
                stalledServer.close();
            }
            if (acceptor != null) {
                acceptor.join(TimeUnit.SECONDS.toMillis(5));
            }
            for (var connection : acceptedConnections) {
                connection.close();
            }
        } finally {
            config.reset();
            client.reset();
        }
    }

    @Test
    public void commandBlockedOnStalledRegistryReturnsWhenInterrupted() {
        var interrupted = new AtomicBoolean();
        var interruptedBeforeReturn = new AtomicBoolean();
        // Preemptive, so that a command which ignores the interrupt fails this test instead of
        // hanging it. It also runs the command on a thread of its own, so the interrupt cannot
        // leak into later tests.
        var exitCode = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            var commandThread = Thread.currentThread();
            // Interrupts once the registry has accepted the connection, so the command is
            // already waiting on the response, rather than after a fixed delay that a slow
            // machine could outrun.
            var interrupter = new Thread(() -> {
                try {
                    if (connected.await(20, TimeUnit.SECONDS)) {
                        interrupted.set(true);
                        commandThread.interrupt();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }, "stalled-registry-interrupter");
            interrupter.setDaemon(true);
            interrupter.start();
            try {
                var code = cmd.execute("group", "get", "stalled-group");
                // The flag is set before the interrupt is sent, so a command woken by the
                // interrupt always sees it.
                interruptedBeforeReturn.set(interrupted.get());
                return code;
            } finally {
                interrupter.join();
                Thread.interrupted();
            }
        });

        assertThat(acceptFailure.get())
                .as("The stalled registry should not fail to accept")
                .isNull();
        // One request, and the CLI's Registry client does not retry, so exactly one connection.
        assertThat(acceptedConnections)
                .as("The command should have connected to the stalled registry")
                .hasSize(1);
        // The command must have been waiting on the registry until the interrupt, not failed
        // for some unrelated reason before it.
        assertThat(interruptedBeforeReturn)
                .as("Command returned before it was interrupted. STDERR:\n" + err)
                .isTrue();
        assertThat(exitCode)
                .as("STDERR:\n" + err)
                .isEqualTo(APPLICATION_ERROR_RETURN_CODE);
        assertThat(err.toString())
                .contains("Unexpected error");
    }

    private void acceptAndNeverAnswer(final ServerSocket server) {
        try {
            while (!server.isClosed()) {
                acceptedConnections.add(server.accept());
                connected.countDown();
            }
        } catch (IOException ex) {
            // Closing the server socket in tearDown() ends the loop this way. Anything else is
            // a real failure, which the test asserts on.
            if (!server.isClosed()) {
                acceptFailure.set(ex);
            }
        }
    }
}
