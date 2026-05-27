package io.apicurio.registry.cli;

import io.apicurio.registry.cli.utils.PlatformUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformUtilsTest {

    @Test
    public void testDetectPlatformClassifier() {
        var classifier = PlatformUtils.detectPlatformClassifier();
        assertThat(classifier).matches("(linux|osx)-(x86_64|aarch_64)");
    }

    @Test
    public void testDetectOsClassifier() {
        var os = PlatformUtils.detectOsClassifier();
        assertThat(os).isIn("linux", "osx");
    }

    @Test
    public void testDetectArchClassifier() {
        var arch = PlatformUtils.detectArchClassifier();
        assertThat(arch).isIn("x86_64", "aarch_64");
    }
}
