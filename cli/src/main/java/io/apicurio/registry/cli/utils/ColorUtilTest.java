package io.apicurio.registry.cli.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ColorUtilTest {

    @AfterEach
    public void resetColorEnabled() {
        ColorUtil.setEnabled(true);
    }

    @Test
    public void testColorizeSuccessWhenDisabled() {
        ColorUtil.setEnabled(false);
        assertThat(ColorUtil.colorizeSuccess("ok")).isEqualTo("ok");
    }

    @Test
    public void testColorizeErrorWhenDisabled() {
        ColorUtil.setEnabled(false);
        assertThat(ColorUtil.colorizeError("boom")).isEqualTo("boom");
    }

    @Test
    public void testColorizeWarningWhenDisabled() {
        ColorUtil.setEnabled(false);
        assertThat(ColorUtil.colorizeWarning("warn")).isEqualTo("warn");
    }

    @Test
    public void testColorizePassThroughForNullAndBlank() {
        ColorUtil.setEnabled(true);
        assertThat(ColorUtil.colorizeSuccess(null)).isNull();
        assertThat(ColorUtil.colorizeError("")).isEqualTo("");
        assertThat(ColorUtil.colorizeWarning("   ")).isEqualTo("   ");
    }

    @Test
    public void testColorizeSuccessWhenEnabled() {
        ColorUtil.setEnabled(true);
        String value = ColorUtil.colorizeSuccess("ok");
        assertThat(value).contains("ok");
        assertThat(value).contains("\u001B[");
    }

    @Test
    public void testColorizeErrorWhenEnabled() {
        ColorUtil.setEnabled(true);
        String value = ColorUtil.colorizeError("boom");
        assertThat(value).contains("boom");
        assertThat(value).contains("\u001B[");
    }

    @Test
    public void testColorizeWarningWhenEnabled() {
        ColorUtil.setEnabled(true);
        String value = ColorUtil.colorizeWarning("warn");
        assertThat(value).contains("warn");
        assertThat(value).contains("\u001B[");
    }
}