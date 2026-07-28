package io.apicurio.registry.cli.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractCommandColorDecisionTest {

    @Test
    public void testEnableWhenNoDisablersAndStdoutIsTty() {
        assertThat(AbstractCommand.shouldEnableColors(false, false, true)).isTrue();
    }

    @Test
    public void testDisableWhenNoColorFlagIsSet() {
        assertThat(AbstractCommand.shouldEnableColors(true, false, true)).isFalse();
    }

    @Test
    public void testDisableWhenNoColorEnvIsSet() {
        assertThat(AbstractCommand.shouldEnableColors(false, true, true)).isFalse();
    }

    @Test
    public void testDisableWhenStdoutIsNotTty() {
        assertThat(AbstractCommand.shouldEnableColors(false, false, false)).isFalse();
    }

    @Test
    public void testDisableWhenMultipleDisablersAreSet() {
        assertThat(AbstractCommand.shouldEnableColors(true, true, false)).isFalse();
    }
}