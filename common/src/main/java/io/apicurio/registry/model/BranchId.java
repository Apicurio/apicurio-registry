package io.apicurio.registry.model;

import jakarta.validation.ValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;


@Getter
@EqualsAndHashCode
public class BranchId {

    /**
     * Pattern requirements:
     * - Must not contain reserved characters ":=,<>" (see VersionExpressionParser)
     * - Must accept semver string
     * - Must fit in the database column
     */
    private static final Pattern VALID_PATTERN = Pattern.compile("[a-zA-Z0-9._\\-+]{1,256}"); // TODO: UPGRADE INCOMPATIBILITY

    public static final BranchId LATEST = new BranchId("latest");

    private final String rawBranchId;


    public BranchId(String rawBranchId) {
        if (!isValid(rawBranchId)) {
            throw new ValidationException("Branch ID '" + rawBranchId + "' is invalid. " +
                    "It must consist of alphanumeric characters or '._-+', and have length 1..256 (inclusive).");
        }
        this.rawBranchId = rawBranchId;
    }


    @Override
    public String toString() {
        return rawBranchId;
    }


    public static boolean isValid(String rawBranchId) {
        return rawBranchId != null && VALID_PATTERN.matcher(rawBranchId).matches();
    }
}
