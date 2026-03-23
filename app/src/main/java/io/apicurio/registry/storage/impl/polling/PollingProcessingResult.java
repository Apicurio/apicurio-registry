package io.apicurio.registry.storage.impl.polling;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Represents the result of processing data files.
 * Contains success status and any error messages encountered during processing.
 */
@Getter
public class PollingProcessingResult {

    /**
     * Whether the processing was successful (no errors).
     */
    private final boolean successful;

    /**
     * List of error messages encountered during processing.
     */
    private final List<String> errors;

    private PollingProcessingResult(boolean successful, List<String> errors) {
        this.successful = successful;
        this.errors = errors;
    }

    /**
     * Creates a successful processing result.
     */
    public static PollingProcessingResult success() {
        return new PollingProcessingResult(true, Collections.emptyList());
    }

    /**
     * Creates a failed processing result with the given errors.
     */
    public static PollingProcessingResult failure(List<String> errors) {
        return new PollingProcessingResult(false, errors != null ? errors : Collections.emptyList());
    }
}
