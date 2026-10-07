package io.apicurio.registry.cli.utils;

import io.apicurio.registry.cli.common.CliException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Exercises the real PowerShell round-trip. The installer is covered against a recording
 * alternative of this bean, which leaves the subprocess handling here — the charset the value is
 * decoded with, and the draining of the child's streams — without coverage otherwise.
 *
 * <p>A variable name unique to the run is used, and it is removed again afterwards, so nothing is
 * left behind in the environment of the user running the build.
 */
@EnabledOnOs(OS.WINDOWS)
public class UserEnvironmentTest {

    /** Well past the pipe buffer a child blocks on when its output is not drained. */
    private static final int LARGE_VALUE_LENGTH = 16_384;

    private static final Duration COMPLETES_WITHOUT_HANGING = Duration.ofSeconds(60);

    private UserEnvironment userEnvironment;
    private String variableName;

    @BeforeEach
    public void setUp() {
        userEnvironment = new UserEnvironment();
        variableName = "ACR_TEST_" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    public void tearDown() {
        userEnvironment.setUserVariable(variableName, null);
    }

    @Test
    public void testSetAndGetRoundTrip() {
        userEnvironment.setUserVariable(variableName, "C:\\Users\\Test\\bin;C:\\Other");

        assertThat(userEnvironment.getUserVariable(variableName))
                .isEqualTo("C:\\Users\\Test\\bin;C:\\Other");
    }

    @Test
    public void testGetReturnsNullWhenTheVariableIsNotSet() {
        assertThat(userEnvironment.getUserVariable(variableName)).isNull();
    }

    @Test
    public void testValueSurvivesNonAsciiCharacters() {
        // The console defaults to an OEM code page. Without forcing UTF-8 these come back mangled,
        // which would corrupt the Path of anyone whose user name is not plain ASCII.
        final String value = "C:\\Users\\Ünïcode Tést\\bin;C:\\日本語";
        userEnvironment.setUserVariable(variableName, value);

        assertThat(userEnvironment.getUserVariable(variableName)).isEqualTo(value);
    }

    @Test
    public void testValueIsNotTruncatedOrWrappedAtTheConsoleWidth() {
        // Long enough to exceed both the pipe buffer and any console line wrapping, and far past
        // the 1024 characters at which setx would silently truncate a value.
        final String value = "C:\\dir\\".repeat(LARGE_VALUE_LENGTH / "C:\\dir\\".length());
        userEnvironment.setUserVariable(variableName, value);

        assertTimeoutPreemptively(COMPLETES_WITHOUT_HANGING,
                () -> assertThat(userEnvironment.getUserVariable(variableName)).isEqualTo(value));
    }

    /**
     * An empty name makes SetEnvironmentVariable throw, so PowerShell writes an error record and
     * exits non-zero. Note this covers the failure path returning an exception promptly, not the
     * buffer-filling case itself — that error is only a few hundred bytes. Nothing has to fill a
     * pipe for the child to be safe here: the error stream is redirected away rather than left
     * connected, so there is no buffer to exhaust in the first place.
     */
    @Test
    public void testFailureIsReportedRatherThanHanging() {
        assertTimeoutPreemptively(COMPLETES_WITHOUT_HANGING,
                () -> assertThatThrownBy(() -> userEnvironment.setUserVariable("", "value"))
                        .isInstanceOf(CliException.class)
                        .hasMessageContaining("user environment variable"));
    }
}
