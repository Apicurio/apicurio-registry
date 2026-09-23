package io.apicurio.registry.storage.impl.polling;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Represents the result of processing data files.
 * Contains success status and any errors encountered during processing.
 */
@Getter
public class PollingProcessingResult {

    /**
     * Whether the processing was successful (no errors).
     */
    private final boolean successful;

    /**
     * Structured errors encountered during processing.
     */
    private final List<PollingError> errors;

    private final int groupCount;
    private final int artifactCount;
    private final int versionCount;

    private PollingProcessingResult(boolean successful, List<PollingError> errors,
                                    int groupCount, int artifactCount, int versionCount) {
        this.successful = successful;
        this.errors = errors;
        this.groupCount = groupCount;
        this.artifactCount = artifactCount;
        this.versionCount = versionCount;
    }

    /**
     * Creates a successful processing result with load statistics.
     */
    public static PollingProcessingResult success(int groupCount, int artifactCount, int versionCount) {
        return new PollingProcessingResult(true, Collections.emptyList(),
                groupCount, artifactCount, versionCount);
    }

    /**
     * Creates a failed processing result with the given errors.
     */
    public static PollingProcessingResult failure(List<PollingError> errors) {
        return new PollingProcessingResult(false,
                errors != null ? errors : Collections.emptyList(),
                0, 0, 0);
    }
}
