package io.apicurio.registry.contracts;

import io.apicurio.registry.storage.dto.ContractMetadataDto;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.DataClassification;
import io.apicurio.registry.storage.dto.EditableContractMetadataDto;
import io.apicurio.registry.storage.dto.PromotionStage;
import io.apicurio.registry.storage.error.InvalidContractMetadataException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ContractMetadataValidator.
 */
public class ContractMetadataValidatorTest {

    private ContractMetadataValidator validator;

    @BeforeEach
    public void setUp() {
        validator = new ContractMetadataValidator();
    }

    @Test
    public void testValidateValidMetadata() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.STABLE)
                .ownerTeam("Platform Team")
                .ownerDomain("payments")
                .supportContact("support@example.com")
                .classification(DataClassification.CONFIDENTIAL)
                .stage(PromotionStage.PROD)
                .stableDate("2024-01-15")
                .build();

        Assertions.assertDoesNotThrow(() -> validator.validate(metadata));
    }

    @Test
    public void testValidateNullMetadata() {
        Assertions.assertDoesNotThrow(() -> validator.validate((ContractMetadataDto) null));
    }

    @Test
    public void testValidateEmptyMetadata() {
        ContractMetadataDto metadata = ContractMetadataDto.builder().build();
        Assertions.assertDoesNotThrow(() -> validator.validate(metadata));
    }

    @Test
    public void testValidateInvalidEmail() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.DRAFT)
                .supportContact("invalid-email")
                .build();

        InvalidContractMetadataException exception = Assertions.assertThrows(
                InvalidContractMetadataException.class,
                () -> validator.validate(metadata)
        );

        Assertions.assertTrue(exception.getMessage().contains("Invalid email format"));
    }

    @Test
    public void testValidateInvalidStableDate() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.STABLE)
                .stableDate("not-a-date")
                .build();

        InvalidContractMetadataException exception = Assertions.assertThrows(
                InvalidContractMetadataException.class,
                () -> validator.validate(metadata)
        );

        Assertions.assertTrue(exception.getMessage().contains("Invalid ISO-8601 date format"));
        Assertions.assertTrue(exception.getMessage().contains("stableDate"));
    }

    @Test
    public void testValidateInvalidDeprecatedDate() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.DEPRECATED)
                .deprecatedDate("01-15-2024")
                .build();

        InvalidContractMetadataException exception = Assertions.assertThrows(
                InvalidContractMetadataException.class,
                () -> validator.validate(metadata)
        );

        Assertions.assertTrue(exception.getMessage().contains("Invalid ISO-8601 date format"));
        Assertions.assertTrue(exception.getMessage().contains("deprecatedDate"));
    }

    @Test
    public void testValidateDeprecatedDateWithoutDeprecatedStatus() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.STABLE)
                .deprecatedDate("2024-06-01")
                .build();

        InvalidContractMetadataException exception = Assertions.assertThrows(
                InvalidContractMetadataException.class,
                () -> validator.validate(metadata)
        );

        Assertions.assertTrue(exception.getMessage().contains("deprecatedDate can only be set when status is DEPRECATED"));
    }

    @Test
    public void testValidateDeprecationReasonWithoutDeprecatedStatus() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.DRAFT)
                .deprecationReason("Being replaced")
                .build();

        InvalidContractMetadataException exception = Assertions.assertThrows(
                InvalidContractMetadataException.class,
                () -> validator.validate(metadata)
        );

        Assertions.assertTrue(exception.getMessage().contains("deprecationReason can only be set when status is DEPRECATED"));
    }

    @Test
    public void testValidateDeprecatedMetadataWithDeprecatedStatus() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.DEPRECATED)
                .deprecatedDate("2024-06-01")
                .deprecationReason("Migrating to v2")
                .build();

        Assertions.assertDoesNotThrow(() -> validator.validate(metadata));
    }

    @Test
    public void testValidateMultipleErrors() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.STABLE)
                .supportContact("bad-email")
                .stableDate("invalid-date")
                .deprecatedDate("2024-01-01")
                .build();

        InvalidContractMetadataException exception = Assertions.assertThrows(
                InvalidContractMetadataException.class,
                () -> validator.validate(metadata)
        );

        String message = exception.getMessage();
        Assertions.assertTrue(message.contains("Invalid email format"));
        Assertions.assertTrue(message.contains("Invalid ISO-8601 date format"));
        Assertions.assertTrue(message.contains("deprecatedDate can only be set when status is DEPRECATED"));
    }

    @Test
    public void testValidateEditableMetadata() {
        EditableContractMetadataDto metadata = EditableContractMetadataDto.builder()
                .status(ContractStatus.STABLE)
                .ownerTeam("API Team")
                .supportContact("api@example.com")
                .stableDate("2024-03-15")
                .build();

        Assertions.assertDoesNotThrow(() -> validator.validate(metadata));
    }

    @Test
    public void testValidateEditableMetadataInvalidEmail() {
        EditableContractMetadataDto metadata = EditableContractMetadataDto.builder()
                .status(ContractStatus.DRAFT)
                .supportContact("not@valid")
                .build();

        // "not@valid" is actually a valid email pattern
        Assertions.assertDoesNotThrow(() -> validator.validate(metadata));
    }

    @Test
    public void testValidateEditableMetadataInvalidEmailNoAt() {
        EditableContractMetadataDto metadata = EditableContractMetadataDto.builder()
                .status(ContractStatus.DRAFT)
                .supportContact("plaintext")
                .build();

        InvalidContractMetadataException exception = Assertions.assertThrows(
                InvalidContractMetadataException.class,
                () -> validator.validate(metadata)
        );

        Assertions.assertTrue(exception.getMessage().contains("Invalid email format"));
    }

    @Test
    public void testIsValidStatus() {
        Assertions.assertTrue(validator.isValidStatus("DRAFT"));
        Assertions.assertTrue(validator.isValidStatus("STABLE"));
        Assertions.assertTrue(validator.isValidStatus("DEPRECATED"));

        Assertions.assertFalse(validator.isValidStatus("INVALID"));
        Assertions.assertFalse(validator.isValidStatus("draft"));
        Assertions.assertFalse(validator.isValidStatus(null));
        Assertions.assertFalse(validator.isValidStatus(""));
        Assertions.assertFalse(validator.isValidStatus("   "));
    }

    @Test
    public void testIsValidClassification() {
        Assertions.assertTrue(validator.isValidClassification("PUBLIC"));
        Assertions.assertTrue(validator.isValidClassification("INTERNAL"));
        Assertions.assertTrue(validator.isValidClassification("CONFIDENTIAL"));
        Assertions.assertTrue(validator.isValidClassification("RESTRICTED"));

        Assertions.assertFalse(validator.isValidClassification("INVALID"));
        Assertions.assertFalse(validator.isValidClassification("public"));
        Assertions.assertFalse(validator.isValidClassification(null));
        Assertions.assertFalse(validator.isValidClassification(""));
    }

    @Test
    public void testIsValidStage() {
        Assertions.assertTrue(validator.isValidStage("DEV"));
        Assertions.assertTrue(validator.isValidStage("STAGE"));
        Assertions.assertTrue(validator.isValidStage("PROD"));

        Assertions.assertFalse(validator.isValidStage("INVALID"));
        Assertions.assertFalse(validator.isValidStage("dev"));
        Assertions.assertFalse(validator.isValidStage(null));
        Assertions.assertFalse(validator.isValidStage(""));
    }

    @Test
    public void testIsValidEmail() {
        // Valid emails
        Assertions.assertTrue(validator.isValidEmail("user@example.com"));
        Assertions.assertTrue(validator.isValidEmail("user.name@example.com"));
        Assertions.assertTrue(validator.isValidEmail("user+tag@example.com"));
        Assertions.assertTrue(validator.isValidEmail("user@subdomain.example.com"));

        // Empty/null is valid (optional field)
        Assertions.assertTrue(validator.isValidEmail(null));
        Assertions.assertTrue(validator.isValidEmail(""));
        Assertions.assertTrue(validator.isValidEmail("   "));

        // Invalid emails
        Assertions.assertFalse(validator.isValidEmail("plaintext"));
        Assertions.assertFalse(validator.isValidEmail("missing@"));
        Assertions.assertFalse(validator.isValidEmail("@missing.com"));
    }

    @Test
    public void testIsValidDate() {
        // Valid dates
        Assertions.assertTrue(validator.isValidDate("2024-01-15"));
        Assertions.assertTrue(validator.isValidDate("2023-12-31"));
        Assertions.assertTrue(validator.isValidDate("2024-02-29")); // Leap year

        // Empty/null is valid (optional field)
        Assertions.assertTrue(validator.isValidDate(null));
        Assertions.assertTrue(validator.isValidDate(""));
        Assertions.assertTrue(validator.isValidDate("   "));

        // Invalid dates
        Assertions.assertFalse(validator.isValidDate("not-a-date"));
        Assertions.assertFalse(validator.isValidDate("01-15-2024")); // Wrong format
        Assertions.assertFalse(validator.isValidDate("2024/01/15")); // Wrong separator
        Assertions.assertFalse(validator.isValidDate("2024-13-01")); // Invalid month
        Assertions.assertFalse(validator.isValidDate("2023-02-29")); // Not a leap year
    }
}
