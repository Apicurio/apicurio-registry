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
 * Unit tests for {@link ContractMetadataValidator}.
 *
 * <p>Covers validation of contract metadata fields including:
 * <ul>
 *   <li>Email format validation (RFC-like pattern matching)</li>
 *   <li>ISO-8601 date format validation for lifecycle dates</li>
 *   <li>Business rule enforcement (deprecation fields require DEPRECATED status)</li>
 *   <li>Error accumulation (multiple validation errors in a single exception)</li>
 *   <li>Editable DTO validation (same rules as read-only DTO)</li>
 *   <li>Individual field validators ({@code isValidStatus}, {@code isValidEmail}, etc.)</li>
 * </ul>
 */
public class ContractMetadataValidatorTest {

    private ContractMetadataValidator validator;

    @BeforeEach
    public void setUp() {
        validator = new ContractMetadataValidator();
    }

    // ===== Full DTO validation (ContractMetadataDto) =====

    /**
     * Verifies that a fully populated, valid DTO passes validation without errors.
     */
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

    /**
     * Verifies that null input is accepted (no contract metadata to validate).
     */
    @Test
    public void testValidateNullMetadata() {
        Assertions.assertDoesNotThrow(() -> validator.validate((ContractMetadataDto) null));
    }

    /**
     * Verifies that an empty DTO (all fields null) is valid, since all fields are optional.
     */
    @Test
    public void testValidateEmptyMetadata() {
        ContractMetadataDto metadata = ContractMetadataDto.builder().build();
        Assertions.assertDoesNotThrow(() -> validator.validate(metadata));
    }

    // ===== Email validation =====

    /**
     * Verifies that a support contact without a valid email format is rejected.
     */
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

    // ===== Date validation =====

    /**
     * Verifies that a non-ISO-8601 stableDate is rejected with a clear error message.
     */
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

    /**
     * Verifies that a US-format date (MM-DD-YYYY) is rejected; only ISO-8601 (YYYY-MM-DD) is accepted.
     */
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

    // ===== Business rule: deprecation field coherence =====

    /**
     * Verifies that setting deprecatedDate on a non-DEPRECATED contract is rejected.
     * Deprecation fields are only meaningful when the contract status is DEPRECATED.
     */
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

    /**
     * Verifies that setting deprecationReason on a non-DEPRECATED contract is rejected.
     */
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

    /**
     * Verifies that deprecation fields are accepted when the contract status is DEPRECATED.
     */
    @Test
    public void testValidateDeprecatedMetadataWithDeprecatedStatus() {
        ContractMetadataDto metadata = ContractMetadataDto.builder()
                .status(ContractStatus.DEPRECATED)
                .deprecatedDate("2024-06-01")
                .deprecationReason("Migrating to v2")
                .build();

        Assertions.assertDoesNotThrow(() -> validator.validate(metadata));
    }

    // ===== Error accumulation =====

    /**
     * Verifies that multiple validation errors are accumulated into a single exception
     * rather than failing fast on the first error.
     */
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

        // All three errors should be present in the message
        String message = exception.getMessage();
        Assertions.assertTrue(message.contains("Invalid email format"));
        Assertions.assertTrue(message.contains("Invalid ISO-8601 date format"));
        Assertions.assertTrue(message.contains("deprecatedDate can only be set when status is DEPRECATED"));
    }

    // ===== Editable DTO validation =====

    /**
     * Verifies that the editable DTO variant passes the same validation rules.
     */
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

    /**
     * Verifies that "not@valid" passes the email regex (it matches user@domain),
     * demonstrating the deliberately permissive email pattern.
     */
    @Test
    public void testValidateEditableMetadataInvalidEmail() {
        EditableContractMetadataDto metadata = EditableContractMetadataDto.builder()
                .status(ContractStatus.DRAFT)
                .supportContact("not@valid")
                .build();

        Assertions.assertDoesNotThrow(() -> validator.validate(metadata));
    }

    /**
     * Verifies that an email without an '@' symbol is rejected.
     */
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

    // ===== Individual field validators =====

    /**
     * Verifies {@code isValidStatus} accepts all valid enum names (case-sensitive)
     * and rejects invalid, lowercase, null, empty, and blank strings.
     */
    @Test
    public void testIsValidStatus() {
        Assertions.assertTrue(validator.isValidStatus("DRAFT"));
        Assertions.assertTrue(validator.isValidStatus("STABLE"));
        Assertions.assertTrue(validator.isValidStatus("DEPRECATED"));

        Assertions.assertFalse(validator.isValidStatus("INVALID"));
        Assertions.assertFalse(validator.isValidStatus("draft")); // case-sensitive
        Assertions.assertFalse(validator.isValidStatus(null));
        Assertions.assertFalse(validator.isValidStatus(""));
        Assertions.assertFalse(validator.isValidStatus("   "));
    }

    /**
     * Verifies {@code isValidClassification} accepts all four classification levels
     * and rejects invalid or lowercase values.
     */
    @Test
    public void testIsValidClassification() {
        Assertions.assertTrue(validator.isValidClassification("PUBLIC"));
        Assertions.assertTrue(validator.isValidClassification("INTERNAL"));
        Assertions.assertTrue(validator.isValidClassification("CONFIDENTIAL"));
        Assertions.assertTrue(validator.isValidClassification("RESTRICTED"));

        Assertions.assertFalse(validator.isValidClassification("INVALID"));
        Assertions.assertFalse(validator.isValidClassification("public")); // case-sensitive
        Assertions.assertFalse(validator.isValidClassification(null));
        Assertions.assertFalse(validator.isValidClassification(""));
    }

    /**
     * Verifies {@code isValidStage} accepts all three promotion stages
     * and rejects invalid or lowercase values.
     */
    @Test
    public void testIsValidStage() {
        Assertions.assertTrue(validator.isValidStage("DEV"));
        Assertions.assertTrue(validator.isValidStage("STAGE"));
        Assertions.assertTrue(validator.isValidStage("PROD"));

        Assertions.assertFalse(validator.isValidStage("INVALID"));
        Assertions.assertFalse(validator.isValidStage("dev")); // case-sensitive
        Assertions.assertFalse(validator.isValidStage(null));
        Assertions.assertFalse(validator.isValidStage(""));
    }

    /**
     * Verifies {@code isValidEmail} behavior:
     * - Standard email addresses are accepted
     * - Null, empty, and blank are accepted (email is optional)
     * - Missing '@', missing local part, and missing domain are rejected
     */
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

    /**
     * Verifies {@code isValidDate} behavior:
     * - ISO-8601 dates (YYYY-MM-DD) are accepted, including leap year dates
     * - Null, empty, and blank are accepted (dates are optional)
     * - Non-date strings, US-format dates, slash-separated dates, invalid months,
     *   and non-leap-year Feb 29 are all rejected
     */
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
        Assertions.assertFalse(validator.isValidDate("01-15-2024")); // US format
        Assertions.assertFalse(validator.isValidDate("2024/01/15")); // Slash separator
        Assertions.assertFalse(validator.isValidDate("2024-13-01")); // Invalid month
        Assertions.assertFalse(validator.isValidDate("2023-02-29")); // Not a leap year
    }
}
