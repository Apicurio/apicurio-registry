package io.apicurio.registry.cli.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalUtilsTest {

    @Test
    public void testGetTerminalWidthIsPositiveAndCached() {
        var width = TerminalUtils.getTerminalWidth();
        assertThat(width).isPositive();
        assertThat(TerminalUtils.getTerminalWidth()).isEqualTo(width);
    }

    @Test
    public void testParseColumnsValidValue() {
        assertThat(TerminalUtils.parseColumns("80")).isEqualTo(80);
        assertThat(TerminalUtils.parseColumns(" 132 ")).isEqualTo(132);
    }

    @Test
    public void testParseColumnsInvalidValue() {
        assertThat(TerminalUtils.parseColumns(null)).isEqualTo(-1);
        assertThat(TerminalUtils.parseColumns("")).isEqualTo(-1);
        assertThat(TerminalUtils.parseColumns("   ")).isEqualTo(-1);
        assertThat(TerminalUtils.parseColumns("abc")).isEqualTo(-1);
        assertThat(TerminalUtils.parseColumns("12.5")).isEqualTo(-1);
    }
}
