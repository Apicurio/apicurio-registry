package io.apicurio.registry.cli;

import io.apicurio.registry.cli.utils.PlatformUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PlatformUtilsTest {

    private static final String OS_NAME_PROPERTY = "os.name";

    private String originalOsName;

    @BeforeEach
    public void rememberOsName() {
        originalOsName = System.getProperty(OS_NAME_PROPERTY);
    }

    @AfterEach
    public void restoreOsName() {
        System.setProperty(OS_NAME_PROPERTY, originalOsName);
    }

    @Test
    public void testDetectPlatformClassifier() {
        var classifier = PlatformUtils.detectPlatformClassifier();
        assertThat(classifier).matches("(linux|osx|windows)-(x86_64|aarch_64)");
    }

    @Test
    public void testDetectOsClassifier() {
        var os = PlatformUtils.detectOsClassifier();
        assertThat(os).isIn("linux", "osx", "windows");
    }

    @Test
    public void testDetectArchClassifier() {
        var arch = PlatformUtils.detectArchClassifier();
        assertThat(arch).isIn("x86_64", "aarch_64");
    }

    @Test
    public void testDetectWindowsOsClassifier() {
        System.setProperty(OS_NAME_PROPERTY, "Windows 11");

        assertThat(PlatformUtils.detectOsClassifier()).isEqualTo(PlatformUtils.OS_WINDOWS);
        assertThat(PlatformUtils.isWindows()).isTrue();
        assertThat(PlatformUtils.isMacOS()).isFalse();
    }

    @Test
    public void testDetectMacOsIsNotReportedAsWindows() {
        // "darwin" contains "win", so the Windows check must match the whole "windows" token.
        System.setProperty(OS_NAME_PROPERTY, "Darwin");

        assertThat(PlatformUtils.detectOsClassifier()).isEqualTo(PlatformUtils.OS_MAC);
        assertThat(PlatformUtils.isWindows()).isFalse();
        assertThat(PlatformUtils.isMacOS()).isTrue();
    }

    @Test
    public void testDetectUnsupportedOsClassifier() {
        System.setProperty(OS_NAME_PROPERTY, "OS/390");

        assertThatThrownBy(PlatformUtils::detectOsClassifier)
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
