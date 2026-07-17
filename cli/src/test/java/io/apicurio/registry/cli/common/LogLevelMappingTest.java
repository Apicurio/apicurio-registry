package io.apicurio.registry.cli.common;

import org.junit.jupiter.api.Test;

import java.util.logging.Level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain JUnit (no @QuarkusTest) so it runs on plain JUL, proving the Quarkus-style
 * level names resolve to portable JUL levels without relying on JBoss LogManager.
 */
public class LogLevelMappingTest {

    @Test
    public void mapsQuarkusStyleNamesToJulLevels() {
        assertThat(AbstractCommand.toJulLevel("DEBUG")).isEqualTo(Level.FINE);
        assertThat(AbstractCommand.toJulLevel("TRACE")).isEqualTo(Level.FINEST);
        assertThat(AbstractCommand.toJulLevel("WARN")).isEqualTo(Level.WARNING);
        assertThat(AbstractCommand.toJulLevel("ERROR")).isEqualTo(Level.SEVERE);
        assertThat(AbstractCommand.toJulLevel("FATAL")).isEqualTo(Level.SEVERE);
        assertThat(AbstractCommand.toJulLevel("INFO")).isEqualTo(Level.INFO);
        assertThat(AbstractCommand.toJulLevel("OFF")).isEqualTo(Level.OFF);
    }

    @Test
    public void acceptsNativeJulNamesAndIsCaseAndWhitespaceInsensitive() {
        assertThat(AbstractCommand.toJulLevel("FINE")).isEqualTo(Level.FINE);
        assertThat(AbstractCommand.toJulLevel("finest")).isEqualTo(Level.FINEST);
        assertThat(AbstractCommand.toJulLevel(" debug ")).isEqualTo(Level.FINE);
    }

    @Test
    public void rejectsUnknownLevel() {
        assertThatThrownBy(() -> AbstractCommand.toJulLevel("BOGUS"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
