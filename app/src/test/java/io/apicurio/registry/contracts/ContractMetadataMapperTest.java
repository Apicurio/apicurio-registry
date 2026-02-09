package io.apicurio.registry.contracts;

import io.apicurio.registry.storage.dto.ContractMetadataDto;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.DataClassification;
import io.apicurio.registry.storage.dto.EditableContractMetadataDto;
import io.apicurio.registry.storage.dto.PromotionStage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for ContractMetadataMapper.
 */
public class ContractMetadataMapperTest {

    private ContractMetadataMapper mapper;

    @BeforeEach
    public void setUp() {
        mapper = new ContractMetadataMapper();
    }

    @Test
    public void testFromLabels_Complete() {
        Map<String, String> labels = new HashMap<>();
        labels.put(ContractLabels.STATUS, "STABLE");
        labels.put(ContractLabels.OWNER_TEAM, "Platform Team");
        labels.put(ContractLabels.OWNER_DOMAIN, "payments");
        labels.put(ContractLabels.SUPPORT_CONTACT, "support@example.com");
        labels.put(ContractLabels.CLASSIFICATION, "CONFIDENTIAL");
        labels.put(ContractLabels.STAGE, "PROD");
        labels.put(ContractLabels.STABLE_DATE, "2024-01-15");
        labels.put(ContractLabels.DEPRECATED_DATE, "2024-06-01");
        labels.put(ContractLabels.DEPRECATION_REASON, "Replaced by v2");

        ContractMetadataDto result = mapper.fromLabels(labels);

        Assertions.assertEquals(ContractStatus.STABLE, result.getStatus());
        Assertions.assertEquals("Platform Team", result.getOwnerTeam());
        Assertions.assertEquals("payments", result.getOwnerDomain());
        Assertions.assertEquals("support@example.com", result.getSupportContact());
        Assertions.assertEquals(DataClassification.CONFIDENTIAL, result.getClassification());
        Assertions.assertEquals(PromotionStage.PROD, result.getStage());
        Assertions.assertEquals("2024-01-15", result.getStableDate());
        Assertions.assertEquals("2024-06-01", result.getDeprecatedDate());
        Assertions.assertEquals("Replaced by v2", result.getDeprecationReason());
    }

    @Test
    public void testFromLabels_Partial() {
        Map<String, String> labels = new HashMap<>();
        labels.put(ContractLabels.STATUS, "DRAFT");
        labels.put(ContractLabels.OWNER_TEAM, "API Team");

        ContractMetadataDto result = mapper.fromLabels(labels);

        Assertions.assertEquals(ContractStatus.DRAFT, result.getStatus());
        Assertions.assertEquals("API Team", result.getOwnerTeam());
        Assertions.assertNull(result.getOwnerDomain());
        Assertions.assertNull(result.getSupportContact());
        Assertions.assertNull(result.getClassification());
        Assertions.assertNull(result.getStage());
        Assertions.assertNull(result.getStableDate());
        Assertions.assertNull(result.getDeprecatedDate());
        Assertions.assertNull(result.getDeprecationReason());
    }

    @Test
    public void testFromLabels_Null() {
        ContractMetadataDto result = mapper.fromLabels(null);

        Assertions.assertNotNull(result);
        Assertions.assertNull(result.getStatus());
        Assertions.assertNull(result.getOwnerTeam());
    }

    @Test
    public void testFromLabels_Empty() {
        ContractMetadataDto result = mapper.fromLabels(new HashMap<>());

        Assertions.assertNotNull(result);
        Assertions.assertNull(result.getStatus());
        Assertions.assertNull(result.getOwnerTeam());
    }

    @Test
    public void testFromLabels_InvalidEnumValues() {
        Map<String, String> labels = new HashMap<>();
        labels.put(ContractLabels.STATUS, "INVALID_STATUS");
        labels.put(ContractLabels.CLASSIFICATION, "NOT_A_CLASSIFICATION");
        labels.put(ContractLabels.STAGE, "UNKNOWN_STAGE");
        labels.put(ContractLabels.OWNER_TEAM, "Team A");

        ContractMetadataDto result = mapper.fromLabels(labels);

        Assertions.assertNull(result.getStatus());
        Assertions.assertNull(result.getClassification());
        Assertions.assertNull(result.getStage());
        Assertions.assertEquals("Team A", result.getOwnerTeam());
    }

    @Test
    public void testFromLabels_BlankEnumValues() {
        Map<String, String> labels = new HashMap<>();
        labels.put(ContractLabels.STATUS, "   ");
        labels.put(ContractLabels.OWNER_TEAM, "Team B");

        ContractMetadataDto result = mapper.fromLabels(labels);

        Assertions.assertNull(result.getStatus());
        Assertions.assertEquals("Team B", result.getOwnerTeam());
    }

    @Test
    public void testToLabels_Complete() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.DEPRECATED)
                .ownerTeam("Data Team")
                .ownerDomain("analytics")
                .supportContact("data@example.com")
                .classification(DataClassification.INTERNAL)
                .stage(PromotionStage.STAGE)
                .stableDate("2023-06-15")
                .deprecatedDate("2024-03-01")
                .deprecationReason("Migrating to new schema")
                .build();

        Map<String, String> labels = mapper.toLabels(metadata);

        Assertions.assertEquals("DEPRECATED", labels.get(ContractLabels.STATUS));
        Assertions.assertEquals("Data Team", labels.get(ContractLabels.OWNER_TEAM));
        Assertions.assertEquals("analytics", labels.get(ContractLabels.OWNER_DOMAIN));
        Assertions.assertEquals("data@example.com", labels.get(ContractLabels.SUPPORT_CONTACT));
        Assertions.assertEquals("INTERNAL", labels.get(ContractLabels.CLASSIFICATION));
        Assertions.assertEquals("STAGE", labels.get(ContractLabels.STAGE));
        Assertions.assertEquals("2023-06-15", labels.get(ContractLabels.STABLE_DATE));
        Assertions.assertEquals("2024-03-01", labels.get(ContractLabels.DEPRECATED_DATE));
        Assertions.assertEquals("Migrating to new schema", labels.get(ContractLabels.DEPRECATION_REASON));
    }

    @Test
    public void testToLabels_Partial() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.DRAFT)
                .ownerTeam("API Team")
                .build();

        Map<String, String> labels = mapper.toLabels(metadata);

        Assertions.assertEquals("DRAFT", labels.get(ContractLabels.STATUS));
        Assertions.assertEquals("API Team", labels.get(ContractLabels.OWNER_TEAM));
        Assertions.assertFalse(labels.containsKey(ContractLabels.OWNER_DOMAIN));
        Assertions.assertFalse(labels.containsKey(ContractLabels.SUPPORT_CONTACT));
        Assertions.assertFalse(labels.containsKey(ContractLabels.CLASSIFICATION));
        Assertions.assertFalse(labels.containsKey(ContractLabels.STAGE));
    }

    @Test
    public void testToLabels_Null() {
        Map<String, String> labels = mapper.toLabels((ContractMetadataDto) null);

        Assertions.assertNotNull(labels);
        Assertions.assertTrue(labels.isEmpty());
    }

    @Test
    public void testToLabels_BlankStringsNotIncluded() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.STABLE)
                .ownerTeam("   ")
                .ownerDomain("")
                .build();

        Map<String, String> labels = mapper.toLabels(metadata);

        Assertions.assertEquals("STABLE", labels.get(ContractLabels.STATUS));
        Assertions.assertFalse(labels.containsKey(ContractLabels.OWNER_TEAM));
        Assertions.assertFalse(labels.containsKey(ContractLabels.OWNER_DOMAIN));
    }

    @Test
    public void testToLabelsFromEditable_Complete() {
        EditableContractMetadataDto metadata = EditableContractMetadataDto.builder()
                .status(ContractStatus.STABLE)
                .ownerTeam("Integration Team")
                .ownerDomain("integrations")
                .supportContact("integrations@example.com")
                .classification(DataClassification.PUBLIC)
                .stage(PromotionStage.DEV)
                .stableDate("2024-02-01")
                .build();

        Map<String, String> labels = mapper.toLabels(metadata);

        Assertions.assertEquals("STABLE", labels.get(ContractLabels.STATUS));
        Assertions.assertEquals("Integration Team", labels.get(ContractLabels.OWNER_TEAM));
        Assertions.assertEquals("integrations", labels.get(ContractLabels.OWNER_DOMAIN));
        Assertions.assertEquals("integrations@example.com", labels.get(ContractLabels.SUPPORT_CONTACT));
        Assertions.assertEquals("PUBLIC", labels.get(ContractLabels.CLASSIFICATION));
        Assertions.assertEquals("DEV", labels.get(ContractLabels.STAGE));
        Assertions.assertEquals("2024-02-01", labels.get(ContractLabels.STABLE_DATE));
    }

    @Test
    public void testToLabelsFromEditable_Null() {
        Map<String, String> labels = mapper.toLabels((EditableContractMetadataDto) null);

        Assertions.assertNotNull(labels);
        Assertions.assertTrue(labels.isEmpty());
    }

    @Test
    public void testRoundTrip() {
        ContractMetadataDto original = ContractMetadataDto.builder()
                .status(ContractStatus.STABLE)
                .ownerTeam("Backend Team")
                .ownerDomain("core-services")
                .supportContact("backend@example.com")
                .classification(DataClassification.RESTRICTED)
                .stage(PromotionStage.PROD)
                .stableDate("2024-01-01")
                .build();

        Map<String, String> labels = mapper.toLabels(original);
        ContractMetadataDto restored = mapper.fromLabels(labels);

        Assertions.assertEquals(original, restored);
    }

    @Test
    public void testAllStatusValues() {
        for (ContractStatus status : ContractStatus.values()) {
            Map<String, String> labels = new HashMap<>();
            labels.put(ContractLabels.STATUS, status.name());

            ContractMetadataDto result = mapper.fromLabels(labels);
            Assertions.assertEquals(status, result.getStatus());
        }
    }

    @Test
    public void testAllClassificationValues() {
        for (DataClassification classification : DataClassification.values()) {
            Map<String, String> labels = new HashMap<>();
            labels.put(ContractLabels.CLASSIFICATION, classification.name());

            ContractMetadataDto result = mapper.fromLabels(labels);
            Assertions.assertEquals(classification, result.getClassification());
        }
    }

    @Test
    public void testAllStageValues() {
        for (PromotionStage stage : PromotionStage.values()) {
            Map<String, String> labels = new HashMap<>();
            labels.put(ContractLabels.STAGE, stage.name());

            ContractMetadataDto result = mapper.fromLabels(labels);
            Assertions.assertEquals(stage, result.getStage());
        }
    }
}
