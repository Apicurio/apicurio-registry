package io.apicurio.registry.contracts;

import io.apicurio.registry.storage.dto.ContractMetadataDto;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.DataClassification;
import io.apicurio.registry.storage.dto.EditableContractMetadataDto;
import io.apicurio.registry.storage.dto.PromotionStage;
import io.apicurio.registry.storage.error.InvalidContractMetadataException;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validator for contract metadata fields.
 */
@ApplicationScoped
public class ContractMetadataValidator {

    // Simple email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    );

    /**
     * Validates the contract metadata.
     *
     * @param metadata the contract metadata to validate
     * @throws InvalidContractMetadataException if validation fails
     */
    public void validate(ContractMetadataDto metadata) throws InvalidContractMetadataException {
        if (metadata == null) {
            return;
        }

        List<String> errors = new ArrayList<>();

        validateSupportContact(metadata.getSupportContact(), errors);
        validateDateField(metadata.getStableDate(), "stableDate", errors);
        validateDateField(metadata.getDeprecatedDate(), "deprecatedDate", errors);
        validateDeprecationBusinessRules(metadata.getStatus(), metadata.getDeprecatedDate(),
                metadata.getDeprecationReason(), errors);

        if (!errors.isEmpty()) {
            throw new InvalidContractMetadataException(String.join("; ", errors));
        }
    }

    /**
     * Validates the editable contract metadata.
     *
     * @param metadata the editable contract metadata to validate
     * @throws InvalidContractMetadataException if validation fails
     */
    public void validate(EditableContractMetadataDto metadata) throws InvalidContractMetadataException {
        if (metadata == null) {
            return;
        }

        List<String> errors = new ArrayList<>();

        validateSupportContact(metadata.getSupportContact(), errors);
        validateDateField(metadata.getStableDate(), "stableDate", errors);
        validateDateField(metadata.getDeprecatedDate(), "deprecatedDate", errors);
        validateDeprecationBusinessRules(metadata.getStatus(), metadata.getDeprecatedDate(),
                metadata.getDeprecationReason(), errors);

        if (!errors.isEmpty()) {
            throw new InvalidContractMetadataException(String.join("; ", errors));
        }
    }

    /**
     * Checks if the given status string is a valid ContractStatus.
     *
     * @param status the status string to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        try {
            ContractStatus.valueOf(status);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks if the given classification string is a valid DataClassification.
     *
     * @param classification the classification string to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidClassification(String classification) {
        if (classification == null || classification.isBlank()) {
            return false;
        }
        try {
            DataClassification.valueOf(classification);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks if the given stage string is a valid PromotionStage.
     *
     * @param stage the stage string to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return false;
        }
        try {
            PromotionStage.valueOf(stage);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks if the given email is valid.
     *
     * @param email the email to validate
     * @return true if valid or null/empty, false otherwise
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return true; // Empty is valid (optional field)
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Checks if the given date string is a valid ISO-8601 date.
     *
     * @param date the date string to validate
     * @return true if valid or null/empty, false otherwise
     */
    public boolean isValidDate(String date) {
        if (date == null || date.isBlank()) {
            return true; // Empty is valid (optional field)
        }
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private void validateSupportContact(String supportContact, List<String> errors) {
        if (supportContact != null && !supportContact.isBlank() && !isValidEmail(supportContact)) {
            errors.add("Invalid email format for supportContact: " + supportContact);
        }
    }

    private void validateDateField(String date, String fieldName, List<String> errors) {
        if (date != null && !date.isBlank() && !isValidDate(date)) {
            errors.add("Invalid ISO-8601 date format for " + fieldName + ": " + date);
        }
    }

    private void validateDeprecationBusinessRules(ContractStatus status, String deprecatedDate,
            String deprecationReason, List<String> errors) {
        // If deprecatedDate is set, status should be DEPRECATED
        if (deprecatedDate != null && !deprecatedDate.isBlank()) {
            if (status != ContractStatus.DEPRECATED) {
                errors.add("deprecatedDate can only be set when status is DEPRECATED");
            }
        }

        // If deprecationReason is set, status should be DEPRECATED
        if (deprecationReason != null && !deprecationReason.isBlank()) {
            if (status != ContractStatus.DEPRECATED) {
                errors.add("deprecationReason can only be set when status is DEPRECATED");
            }
        }
    }
}
