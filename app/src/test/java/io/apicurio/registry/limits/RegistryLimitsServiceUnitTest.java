package io.apicurio.registry.limits;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class RegistryLimitsServiceUnitTest {

    private RegistryLimitsService limitsService;
    private RegistryLimitsConfiguration config;

    @BeforeEach
    public void setUp() {
        limitsService = new RegistryLimitsService();
        limitsService.log = LoggerFactory.getLogger(RegistryLimitsService.class);
        config = new RegistryLimitsConfiguration();

        config.setMaxArtifactPropertiesCount(1L);
        config.setMaxPropertyKeySizeBytes(4L);
        config.setMaxPropertyValueSizeBytes(4L);

        limitsService.registryLimitsConfiguration = config;
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

    @Test
    public void testDuplicateErrorMessageDeduplication() {
        // Multiple labels exceeding key size limit
        Map<String, String> labels = Map.of(
                "key001", "v1",
                "key002", "v2"
        );

        EditableArtifactMetaDataDto meta = new EditableArtifactMetaDataDto();
        meta.setLabels(labels);

        LimitsCheckResult result = limitsService.checkMetaData(meta);
        Assertions.assertFalse(result.isAllowed());
        String msg = result.getMessage();
        // Verify key size error is present exactly once and not duplicated
        int firstIndex = msg.indexOf("Maximum label key size exceeded");
        int lastIndex = msg.lastIndexOf("Maximum label key size exceeded");
        Assertions.assertNotEquals(-1, firstIndex, "Expected error message to be present");
        Assertions.assertEquals(firstIndex, lastIndex, "Expected error message to appear only once");
    }

    @Test
    public void testLabelCountExactErrorMessage() {
        Map<String, String> labels = Map.of(
                "k1", "v1",
                "k2", "v2"
        );
        EditableArtifactMetaDataDto meta = new EditableArtifactMetaDataDto();
        meta.setLabels(labels);

        LimitsCheckResult result = limitsService.checkMetaData(meta);
        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(
                "Maximum number of labels exceeded for this artifact (limit: 1, actual: 2)",
                result.getMessage()
        );
    }

    @Test
    public void testLabelKeyAndValueByteSizeExactErrorMessage() {
        config.setMaxArtifactPropertiesCount(10L);
        config.setMaxPropertyKeySizeBytes(4L);
        config.setMaxPropertyValueSizeBytes(4L);

        Map<String, String> labels = Map.of("keytoolong", "valtoolong");
        EditableArtifactMetaDataDto meta = new EditableArtifactMetaDataDto();
        meta.setLabels(labels);

        LimitsCheckResult result = limitsService.checkMetaData(meta);
        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(
                "Maximum label key size exceeded (limit: 4 bytes), " +
                "Maximum label value size exceeded (limit: 4 bytes)",
                result.getMessage()
        );
    }

    @Test
    public void testSchemaSizeExactErrorMessage() {
        config.setMaxSchemaSizeBytes(10L);

        ContentWrapperDto contentWrapper = ContentWrapperDto.builder()
                .content(ContentHandle.create("123456789012"))
                .build();

        LimitsCheckResult result = limitsService.canCreateArtifact(null, contentWrapper, null);
        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(
                "Maximum size of artifact version exceeded (limit: 10 bytes, actual: 12 bytes)",
                result.getMessage()
        );
    }

    @Test
    public void testNameAndDescriptionLengthExactErrorMessage() {
        config.setMaxArtifactNameLengthChars(5L);
        config.setMaxArtifactDescriptionLengthChars(10L);

        EditableArtifactMetaDataDto meta = new EditableArtifactMetaDataDto();
        meta.setName("toolong");
        meta.setDescription("descriptiontoolong");

        LimitsCheckResult result = limitsService.checkMetaData(meta);
        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(
                "Maximum artifact name length exceeded (limit: 5 characters, actual: 7 characters), " +
                "Maximum artifact description length exceeded (limit: 10 characters, actual: 18 characters)",
                result.getMessage()
        );
    }
}
