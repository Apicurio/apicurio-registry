package io.apicurio.registry.utils;

import io.apicurio.registry.model.VersionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionUtilTest {

    @Test
    void testPlusOnlyVersionIdsUseNonSemverFallback() {
        for (int length = 1; length <= 256; length++) {
            String version = "+".repeat(length);
            assertTrue(VersionId.isValid(version), version);
            assertEquals("NON_SEMVER_" + version, VersionUtil.generateVersionSortKey(version), version);
        }
    }

    @Test
    void testNonSemverFallbackPreservesOriginalVersion() {
        for (String version : new String[] { "release-main", "release+build", "+build" }) {
            assertEquals("NON_SEMVER_" + version, VersionUtil.generateVersionSortKey(version), version);
        }
    }

    @Test
    void testBuildMetadataDoesNotAffectSemverSortKey() {
        for (String build : new String[] { "", "+build.42", "+", "++" }) {
            assertEquals("0000000001.0000000002.0000000003-1",
                    VersionUtil.generateVersionSortKey("1.2.3" + build));
            assertEquals("0000000001.0000000002.0000000003-0-rc.0000000001",
                    VersionUtil.generateVersionSortKey("1.2.3-rc.1" + build));
        }
    }
}
