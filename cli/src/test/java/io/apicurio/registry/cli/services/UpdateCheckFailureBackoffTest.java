package io.apicurio.registry.cli.services;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Covers what happens when an update check fails: that it is reported as a handled CLI error rather
 * than an unexpected one, that the failure is recorded, and that the recorded failure defers the next
 * check instead of letting the auto-check hook retry it on every command.
 *
 * <p>Each test builds its own CLI home, so nothing is shared between them and any one of them can be
 * run on its own.
 */
@QuarkusTest
public class UpdateCheckFailureBackoffTest {

    private static final String REPO_URL_KEY = "internal.update.repo-url";
    private static final String LAST_CHECK_KEY = "internal.update.last-check";
    private static final String LAST_FAILURE_KEY = "internal.update.last-failure";
    private static final String FAILURE_COUNT_KEY = "internal.update.failure-count";

    private static final String METADATA_XML = """
            <metadata>
              <versioning>
                <versions>
                  <version>3.0.0</version>
                  <version>3.1.0</version>
                </versions>
              </versioning>
            </metadata>
            """;

    private static WireMockServer wireMock;

    @Inject
    Config config;

    @Inject
    Update update;

    @Inject
    UpdateNotifier updateNotifier;

    @BeforeAll
    static void startRepository() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopRepository() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @AfterEach
    void tearDown() {
        wireMock.resetAll();
        config.reset();
    }

    // ---------------------------------------------------------------------------------------------
    // The backoff schedule
    // ---------------------------------------------------------------------------------------------

    @Test
    void testBackoffDoublesWithEachConsecutiveFailure() {
        assertThat(UpdateNotifier.failureBackoff(0))
                .as("Nothing has failed, so nothing is deferred")
                .isEqualTo(Duration.ZERO);
        assertThat(UpdateNotifier.failureBackoff(1)).isEqualTo(Duration.ofMinutes(15));
        assertThat(UpdateNotifier.failureBackoff(2)).isEqualTo(Duration.ofMinutes(30));
        assertThat(UpdateNotifier.failureBackoff(3)).isEqualTo(Duration.ofHours(1));
        assertThat(UpdateNotifier.failureBackoff(4)).isEqualTo(Duration.ofHours(2));
        assertThat(UpdateNotifier.failureBackoff(7)).isEqualTo(Duration.ofHours(16));
    }

    @Test
    void testFirstRetryComesSoonerThanAScheduledCheckWould() {
        assertThat(UpdateNotifier.failureBackoff(1))
                .as("A transient failure must recover well before the once-a-day check interval")
                .isLessThan(Duration.ofDays(1));
    }

    @Test
    void testBackoffIsCappedAtTheScheduledCheckInterval() {
        assertThat(UpdateNotifier.failureBackoff(8))
                .as("Doubling past a day must saturate, not keep growing")
                .isEqualTo(Duration.ofDays(1));
        assertThat(UpdateNotifier.failureBackoff(UpdateNotifier.MAX_FAILURE_COUNT))
                .isEqualTo(Duration.ofDays(1));
    }

    @Test
    void testFailureCountIsReadDefensivelyFromUserEditableConfig() {
        assertThat(UpdateNotifier.parseFailureCount(null)).isZero();
        assertThat(UpdateNotifier.parseFailureCount("")).isZero();
        assertThat(UpdateNotifier.parseFailureCount("not-a-number")).isZero();
        assertThat(UpdateNotifier.parseFailureCount("-5")).isZero();
        assertThat(UpdateNotifier.parseFailureCount(" 3 ")).isEqualTo(3);
        assertThat(UpdateNotifier.parseFailureCount("999"))
                .isEqualTo(UpdateNotifier.MAX_FAILURE_COUNT);
    }

    // ---------------------------------------------------------------------------------------------
    // Reporting and recording a failed check
    // ---------------------------------------------------------------------------------------------

    @Test
    void testMissingRepositoryUrlIsReportedAsAHandledCliError(@TempDir Path tempDir)
            throws IOException {
        useHome(tempDir, Map.of());

        var thrown = catchThrowable(() -> update.checkForUpdates(CliVersion.parse("3.0.0")));

        assertThat(thrown)
                .as("An unconfigured repository URL must not reach the command layer as an "
                        + "unexpected error, which is reported with a stack trace")
                .isInstanceOf(CliException.class)
                .isNotInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(REPO_URL_KEY);
        assertThat(((CliException) thrown).getCode())
                .isEqualTo(CliException.APPLICATION_ERROR_RETURN_CODE);
    }

    @Test
    void testFailedCheckRecordsWhenItFailedAndHowManyInARow(@TempDir Path tempDir)
            throws IOException {
        useHome(tempDir, Map.of());
        var before = Instant.now().minusSeconds(1);

        assertThat(catchThrowable(() -> update.checkForUpdates(CliVersion.parse("3.0.0"))))
                .as("The check has to fail for there to be a failure to record")
                .isInstanceOf(CliException.class);

        var props = config.read().getConfig();
        assertThat(props.get(FAILURE_COUNT_KEY)).isEqualTo("1");
        assertThat(Instant.parse(props.get(LAST_FAILURE_KEY)))
                .isBetween(before, Instant.now().plusSeconds(1));
        assertThat(props.get(LAST_CHECK_KEY))
                .as("A failed check is not a completed one, so it must not suppress checks for a "
                        + "full day the way a successful check does")
                .isNull();
    }

    @Test
    void testConsecutiveFailuresAccumulate(@TempDir Path tempDir) throws IOException {
        useHome(tempDir, Map.of(FAILURE_COUNT_KEY, "1",
                LAST_FAILURE_KEY, Instant.now().minus(Duration.ofDays(2)).toString()));

        assertThat(catchThrowable(() -> update.checkForUpdates(CliVersion.parse("3.0.0"))))
                .as("The check has to fail for the streak to continue")
                .isInstanceOf(CliException.class);

        assertThat(config.read().getConfig().get(FAILURE_COUNT_KEY)).isEqualTo("2");
    }

    @Test
    void testSuccessfulCheckClearsTheRecordedFailures(@TempDir Path tempDir)
            throws IOException {
        wireMock.stubFor(get(urlEqualTo("/maven-metadata.xml"))
                .willReturn(aResponse().withStatus(200).withBody(METADATA_XML)));
        useHome(tempDir, Map.of(
                REPO_URL_KEY, wireMock.baseUrl(),
                FAILURE_COUNT_KEY, "3",
                LAST_FAILURE_KEY, Instant.now().toString()));
        var before = Instant.now().minusSeconds(1);

        update.checkForUpdates(CliVersion.parse("3.0.0"));

        var props = config.read().getConfig();
        assertThat(props.get(FAILURE_COUNT_KEY))
                .as("A check that succeeds ends the failure streak, so the next one is due at the "
                        + "normal interval rather than after a backoff")
                .isNull();
        assertThat(props.get(LAST_FAILURE_KEY)).isNull();
        assertThat(Instant.parse(props.get(LAST_CHECK_KEY)))
                .isBetween(before, Instant.now().plusSeconds(1));
    }

    // ---------------------------------------------------------------------------------------------
    // What the auto-check hook does with a recorded failure
    // ---------------------------------------------------------------------------------------------

    @Test
    void testAutoCheckIsSkippedWhileBackingOff(@TempDir Path tempDir) throws IOException {
        var failedAt = Instant.now().minus(Duration.ofMinutes(1));
        useHome(tempDir, Map.of(
                REPO_URL_KEY, wireMock.baseUrl(),
                FAILURE_COUNT_KEY, "1",
                LAST_FAILURE_KEY, failedAt.toString()));
        var err = new StringBuilder();
        config.setStdErr(err::append);

        updateNotifier.checkAndNotify("artifact");

        assertThat(err.toString())
                .as("One minute after a failure the check is still deferred, so the command the "
                        + "user ran prints nothing about updates at all")
                .isEmpty();
        assertThat(wireMock.getAllServeEvents())
                .as("A deferred check must cost no network request")
                .isEmpty();
        assertThat(config.read().getConfig().get(LAST_FAILURE_KEY))
                .isEqualTo(failedAt.toString());
    }

    @Test
    void testAutoCheckResumesOnceTheBackoffHasElapsed(@TempDir Path tempDir)
            throws IOException {
        useHome(tempDir, Map.of(
                FAILURE_COUNT_KEY, "1",
                LAST_FAILURE_KEY, Instant.now().minus(Duration.ofMinutes(20)).toString()));
        var err = new StringBuilder();
        config.setStdErr(err::append);

        updateNotifier.checkAndNotify("artifact");

        assertThat(err.toString())
                .as("Twenty minutes is past the fifteen-minute backoff for a single failure, so the "
                        + "check is attempted again")
                .contains("Checking for updates...")
                .contains("Could not check for updates");
        assertThat(config.read().getConfig().get(FAILURE_COUNT_KEY))
                .as("The renewed attempt failed too, so the next one is deferred for longer")
                .isEqualTo("2");
    }

    @Test
    void testUnparseableFailureTimestampDoesNotDisableCheckingForGood(@TempDir Path tempDir)
            throws IOException {
        wireMock.stubFor(get(urlEqualTo("/maven-metadata.xml"))
                .willReturn(aResponse().withStatus(200).withBody(METADATA_XML)));
        useHome(tempDir, Map.of(
                REPO_URL_KEY, wireMock.baseUrl(),
                FAILURE_COUNT_KEY, "1",
                LAST_FAILURE_KEY, "yesterday"));
        var err = new StringBuilder();
        config.setStdErr(err::append);
        var before = Instant.now().minusSeconds(1);

        updateNotifier.checkAndNotify("artifact");

        assertThat(err.toString())
                .as("A marker that cannot be read must be ignored, not treated as a failure so "
                        + "recent that updates are never checked again")
                .contains("Checking for updates...");
        assertThat(Instant.parse(config.read().getConfig().get(LAST_CHECK_KEY)))
                .isBetween(before, Instant.now().plusSeconds(1));
    }

    /**
     * Points the CLI at a fresh home containing only the given properties, as a new installation with
     * no update history would be.
     */
    private void useHome(final Path tempDir, final Map<String, String> properties) throws IOException {
        final Path home = tempDir.resolve("home");
        Files.createDirectories(home);
        Files.writeString(home.resolve("config.json"), "{}");

        config.reset();
        config.setAcrCurrentHomePath(home);

        if (!properties.isEmpty()) {
            final var model = config.read();
            model.getConfig().putAll(properties);
            config.write(model);
        }
    }
}
