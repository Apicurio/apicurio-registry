package io.apicurio.registry.storage.impl.polling;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Represents the result of processing data files.
 * Contains success status and any error messages encountered during processing.
 */
@Getter
public class ProcessingResult {

    /**
     * Whether the processing was successful (no errors).
     */
    private final boolean successful;

    /**
     * List of error messages encountered during processing.
     */
    private final List<String> errors;

    private ProcessingResult(boolean successful, List<String> errors) {
        this.successful = successful;
        this.errors = errors;
    }

    /**
     * Creates a successful processing result.
     */
    public static ProcessingResult success() {
        return new ProcessingResult(true, Collections.emptyList());
    }

    /**
     * Creates a failed processing result with the given errors.
     */
    public static ProcessingResult failure(List<String> errors) {
        return new ProcessingResult(false, errors != null ? errors : Collections.emptyList());
    }
}
