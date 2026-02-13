package io.apicurio.registry.storage.impl.polling;

/**
 * Interface for recording errors during data file processing.
 * Used during parsing and processing of data files.
 */
public interface DataFileProcessingState {

    /**
     * Records an error message with optional format arguments.
     *
     * @param message the error message format string
     * @param params optional arguments to format into the message
     */
    void recordError(String message, Object... params);
}
