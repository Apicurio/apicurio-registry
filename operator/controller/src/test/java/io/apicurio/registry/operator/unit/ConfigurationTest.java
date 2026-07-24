package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationTest {

    @Test
    public void testGetImageForVersion_swapsTag() {
        assertThat(Configuration.getImageForVersion("quay.io/apicurio/apicurio-registry:3.0.6", "3.0.3"))
                .isEqualTo("quay.io/apicurio/apicurio-registry:3.0.3");
    }

    @Test
    public void testGetImageForVersion_nullVersionReturnsBaseImage() {
        assertThat(Configuration.getImageForVersion("quay.io/apicurio/apicurio-registry:3.0.6", null))
                .isEqualTo("quay.io/apicurio/apicurio-registry:3.0.6");
    }

    @Test
    public void testGetImageForVersion_blankVersionReturnsBaseImage() {
        assertThat(Configuration.getImageForVersion("quay.io/apicurio/apicurio-registry:3.0.6", "  "))
                .isEqualTo("quay.io/apicurio/apicurio-registry:3.0.6");
    }

    @Test
    public void testGetImageForVersion_imageWithoutTag() {
        assertThat(Configuration.getImageForVersion("quay.io/apicurio/apicurio-registry", "3.0.3"))
                .isEqualTo("quay.io/apicurio/apicurio-registry:3.0.3");
    }

    @Test
    public void testGetImageForVersion_latestSnapshotTag() {
        assertThat(Configuration.getImageForVersion("quay.io/apicurio/apicurio-registry:latest-snapshot", "3.0.3"))
                .isEqualTo("quay.io/apicurio/apicurio-registry:3.0.3");
    }

    @Test
    public void testGetImageForVersion_uiImage() {
        assertThat(Configuration.getImageForVersion("quay.io/apicurio/apicurio-registry-ui:3.0.6", "3.0.3"))
                .isEqualTo("quay.io/apicurio/apicurio-registry-ui:3.0.3");
    }

    @Test
    public void testCompareVersions_equal() {
        assertThat(Configuration.compareVersions("3.0.3", "3.0.3")).hasValue(0);
    }

    @Test
    public void testCompareVersions_olderPatch() {
        assertThat(Configuration.compareVersions("3.0.3", "3.0.6").getAsInt()).isNegative();
    }

    @Test
    public void testCompareVersions_newerPatch() {
        assertThat(Configuration.compareVersions("3.0.6", "3.0.3").getAsInt()).isPositive();
    }

    @Test
    public void testCompareVersions_olderMinor() {
        assertThat(Configuration.compareVersions("3.0.3", "3.3.1").getAsInt()).isNegative();
    }

    @Test
    public void testCompareVersions_newerMajor() {
        assertThat(Configuration.compareVersions("4.0.0", "3.0.6").getAsInt()).isPositive();
    }

    @Test
    public void testCompareVersions_snapshotSuffix() {
        assertThat(Configuration.compareVersions("3.3.1-SNAPSHOT", "3.3.1")).hasValue(0);
    }

    @Test
    public void testCompareVersions_differentLengths() {
        assertThat(Configuration.compareVersions("3.0", "3.0.0")).hasValue(0);
    }

    @Test
    public void testCompareVersions_nonNumericReturnsEmpty() {
        assertThat(Configuration.compareVersions("latest", "3.0.3")).isEmpty();
    }

    @Test
    public void testCompareVersions_qualifierReturnsEmpty() {
        assertThat(Configuration.compareVersions("3.0.6.Final", "3.0.3")).isEmpty();
    }

    @Test
    public void testCompareVersions_alphaPartReturnsEmpty() {
        assertThat(Configuration.compareVersions("3.x", "3.0.3")).isEmpty();
    }
}
