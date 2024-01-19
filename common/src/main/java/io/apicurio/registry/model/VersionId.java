package io.apicurio.registry.model;

import jakarta.validation.ValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode
public class VersionId {

    /**
     * Pattern requirements:
     * - Must not contain reserved characters ":=,<>" (see VersionExpressionParser)
     * - Must accept semver string
     * - Must fit in the database column
     */
    private static final Pattern VALID_PATTERN = Pattern.compile("[a-zA-Z0-9._\\-+]{1,256}"); // TODO: UPGRADE INCOMPATIBILITY

    private final String rawVersionId;


    public VersionId(String rawVersionId) {
        if (!isValid(rawVersionId)) {
            throw new ValidationException("Version ID '" + rawVersionId + "' is invalid. " +
                    "It must consist of alphanumeric characters or '._-+', and have length 1..256 (inclusive).");
        }
        this.rawVersionId = rawVersionId;
    }


    @Override
    public String toString() {
        return rawVersionId;
    }


    public static boolean isValid(String rawVersionId) {
        return rawVersionId != null && VALID_PATTERN.matcher(rawVersionId).matches();
    }
}
