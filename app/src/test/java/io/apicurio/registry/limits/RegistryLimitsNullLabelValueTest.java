package io.apicurio.registry.limits;

import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

@QuarkusTest
@TestProfile(RegistryLimitsNullLabelValueTestProfile.class)
public class RegistryLimitsNullLabelValueTest {

    @Inject
    RegistryLimitsService limitsService;

    @Test
    public void testNullLabelValueIsRejected() {
        EditableArtifactMetaDataDto meta = new EditableArtifactMetaDataDto();
        Map<String, String> labels = new HashMap<>();
        labels.put("mykey", null);
        meta.setLabels(labels);

        LimitsCheckResult result = limitsService.checkMetaData(meta);

        Assertions.assertFalse(result.isAllowed(),
                "A null label value must be rejected, not throw NullPointerException");
        Assertions.assertTrue(result.getMessage().contains("Label value must not be null"),
                "Error message should indicate the label value must not be null");
    }

    @Test
    public void testNonNullLabelValueIsAllowed() {
        EditableArtifactMetaDataDto meta = new EditableArtifactMetaDataDto();
        meta.setLabels(Map.of("mykey", "short-value"));

        LimitsCheckResult result = limitsService.checkMetaData(meta);

        Assertions.assertTrue(result.isAllowed(),
                "A non-null label value within the limit must be allowed");
    }
}
