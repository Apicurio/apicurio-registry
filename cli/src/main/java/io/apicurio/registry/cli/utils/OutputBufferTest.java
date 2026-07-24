package io.apicurio.registry.cli.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OutputBufferTest {

    @AfterEach
    public void resetColorEnabled() {
        ColorUtil.setEnabled(true);
    }

    @Test
    public void testSeverityRoutingWithColorsEnabled() {
        var out = new StringBuilder();
        var err = new StringBuilder();
        var buffer = new OutputBuffer(out::append, err::append);

        buffer.writeStdOutChunk(sb -> sb.append("plain-out"));
        buffer.writeStdErrChunk(sb -> sb.append("plain-err"));
        buffer.writeSuccess(sb -> sb.append("success"));
        buffer.writeWarning(sb -> sb.append("warning"));
        buffer.writeError(sb -> sb.append("error"));

        buffer.print();

        String stdout = out.toString();
        String stderr = err.toString();

        assertThat(stdout).contains("plain-out");
        assertThat(stdout).contains("success");
        assertThat(stderr).contains("plain-err");
        assertThat(stderr).contains("warning");
        assertThat(stderr).contains("error");

        // Colorized severities should include ANSI prefix when enabled
        assertThat(stdout).contains("\u001B[");
        assertThat(stderr).contains("\u001B[");
    }

    @Test
    public void testSeverityRoutingWithColorsDisabled() {
        ColorUtil.setEnabled(false);

        var out = new StringBuilder();
        var err = new StringBuilder();
        var buffer = new OutputBuffer(out::append, err::append);

        buffer.writeSuccess(sb -> sb.append("success"));
        buffer.writeWarning(sb -> sb.append("warning"));
        buffer.writeError(sb -> sb.append("error"));

        buffer.print();

        assertThat(out.toString()).isEqualTo("success");
        assertThat(err.toString()).isEqualTo("warningerror");
        assertThat(out.toString()).doesNotContain("\u001B[");
        assertThat(err.toString()).doesNotContain("\u001B[");
    }

    @Test
    public void testPrintIsIncremental() {
        var out = new StringBuilder();
        var err = new StringBuilder();
        var buffer = new OutputBuffer(out::append, err::append);

        buffer.writeStdOutLine("one");
        buffer.print();
        buffer.print(); // should not print the same chunk again

        assertThat(out.toString()).isEqualTo("one\n");
        assertThat(err.toString()).isEmpty();
    }
}