package io.apicurio.registry.model;

import jakarta.validation.ValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode
public class ArtifactId {

    // TODO: Restrict artifact ID format?
    // /**
    //  * Pattern requirements:
    //  * - Must not contain reserved characters ":=,<>" (see VersionExpressionParser)
    //  * - Must accept valid Kafka topic name
    //  * - Must fit in the database column
    //  */
    // private static final Pattern VALID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,512}"); // TODO: UPGRADE INCOMPATIBILITY

    private static final Pattern VALID_PATTERN = Pattern.compile(".{1,512}");

    private final String rawArtifactId;


    public ArtifactId(String rawArtifactId) {
        if (!isValid(rawArtifactId)) {
            throw new ValidationException("Artifact ID '" + rawArtifactId + "' is invalid. " +
                    "It must consist of alphanumeric characters or '._-', and have length 1..512 (inclusive).");
        }
        this.rawArtifactId = rawArtifactId;
    }


    @Override
    public String toString() {
        return rawArtifactId;
    }


    public static boolean isValid(String rawArtifactId) {
        return rawArtifactId != null && VALID_PATTERN.matcher(rawArtifactId).matches();
    }
}
