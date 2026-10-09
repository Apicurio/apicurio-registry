package io.apicurio.registry.promotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpPromotionSourceClientTest {

    @Test
    public void testNormalizeAddsRegistryPath() {
        assertEquals("https://staging-registry:8080/apis/registry/v3",
                HttpPromotionSourceClient.normalizeBaseUrl("https://staging-registry:8080"));
    }

    @Test
    public void testNormalizeKeepsExistingPath() {
        assertEquals("https://staging-registry:8080/apis/registry/v3",
                HttpPromotionSourceClient.normalizeBaseUrl("https://staging-registry:8080/apis/registry/v3/"));
    }

    @Test
    public void testLocalSourceDetection() {
        PromotionSourceDefinition source = new PromotionSourceDefinition("local", "local://", "none", null,
                null, null, null, null, null);
        assertTrue(source.isLocal());
    }
}
