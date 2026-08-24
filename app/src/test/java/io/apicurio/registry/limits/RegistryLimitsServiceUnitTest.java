package io.apicurio.registry.limits;

import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

public class RegistryLimitsServiceUnitTest {

    private RegistryLimitsService limitsService;
    private RegistryLimitsConfiguration config;

    @BeforeEach
    public void setUp() throws Exception {
        limitsService = new RegistryLimitsService();
        config = new RegistryLimitsConfiguration();

        setField(config, "maxArtifactPropertiesCount", 1L);
        setField(config, "maxPropertyKeySizeBytes", 4L);
        setField(config, "maxPropertyValueSizeBytes", 4L);

        setField(limitsService, "registryLimitsConfiguration", config);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testDecoupledLabelCountAndSizeValidation() {
        // 2 labels (exceeds max properties count of 1) and keys of length 5 (exceeds key size limit of 4)
        Map<String, String> labels = Map.of(
                "key01", "val1",
                "key02", "val2"
        );

        EditableArtifactMetaDataDto meta = new EditableArtifactMetaDataDto();
        meta.setLabels(labels);

        LimitsCheckResult result = limitsService.checkMetaData(meta);
        Assertions.assertFalse(result.isAllowed());
        String msg = result.getMessage();
        // Should contain BOTH label count exceeded message AND label key size exceeded message
        Assertions.assertTrue(msg.contains("Maximum number of labels exceeded"), "Expected count message in: " + msg);
        Assertions.assertTrue(msg.contains("Maximum label key size exceeded"), "Expected key size message in: " + msg);
    }

    @Test
    public void testUtf8ByteLengthValidation() {
        // Single label (count = 1, allowed)
        // Key "key" length 3 chars / 3 bytes (allowed)
        // Value "€" (Euro symbol) is 1 char but 3 bytes in UTF-8.
        // "a€" is 2 chars, but 1 + 3 = 4 bytes (allowed if max is 4 bytes).
        // "ab€" is 3 chars, but 1 + 1 + 3 = 5 bytes (exceeds 4 bytes limit).
        Map<String, String> labels = Map.of("key", "ab€");

        EditableArtifactMetaDataDto meta = new EditableArtifactMetaDataDto();
        meta.setLabels(labels);

        LimitsCheckResult result = limitsService.checkMetaData(meta);
        Assertions.assertFalse(result.isAllowed());
        Assertions.assertTrue(result.getMessage().contains("Maximum label value size exceeded"));
    }
}
