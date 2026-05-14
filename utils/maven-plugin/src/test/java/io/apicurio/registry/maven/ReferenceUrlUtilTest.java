package io.apicurio.registry.maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReferenceUrlUtilTest {

    @Test
    void testRegistryReferenceNameRemovesOriginAndPreservesFragment() {
        String fullReference = "http://localhost:8080/apis/registry/v3/groups/master/artifacts/"
                + "kafka-bindings.yml/versions/branch=latest/content#/components/messageTraits/"
                + "kafkaKeyString";

        assertEquals("/apis/registry/v3/groups/master/artifacts/kafka-bindings.yml/versions/"
                + "branch=latest/content#/components/messageTraits/kafkaKeyString",
                ReferenceUrlUtil.registryReferenceName(fullReference));
    }

    @Test
    void testDecodePathSegmentDecodesEncodedValues() {
        assertEquals("master group", ReferenceUrlUtil.decodePathSegment("master%20group"));
        assertEquals("branch=latest", ReferenceUrlUtil.decodePathSegment("branch%3Dlatest"));
    }

    @Test
    void testIsSameApicurioServerNormalizesCaseAndDefaultPort() {
        assertTrue(ReferenceUrlUtil.isSameApicurioServer(
                "http://localhost/apis/registry/v3",
                "http://LOCALHOST:80/apis/registry/v3/groups/master/artifacts/test/versions/1"));
        assertFalse(ReferenceUrlUtil.isSameApicurioServer(
                "http://localhost/apis/registry/v3",
                "http://otherhost/apis/registry/v3/groups/master/artifacts/test/versions/1"));
    }

    @Test
    void testIsAbsoluteUriRejectsRelativePaths() {
        assertTrue(ReferenceUrlUtil.isAbsoluteUri("https://example.com/schema.json"));
        assertFalse(ReferenceUrlUtil.isAbsoluteUri("./avro/shared.avsc"));
    }
}
