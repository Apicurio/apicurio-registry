package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.storage.RegistryStorage;

/**
 * Interface for managing a data source that provides registry configuration and artifacts.
 * This abstraction allows different data sources (Git repositories, Kubernetes ConfigMaps, etc.)
 * to be used with the same polling-based storage implementation.
 */
public interface DataSourceManager {

    /**
     * Initializes the data source manager.
     * Called once during storage initialization.
     *
     * @throws Exception if initialization fails
     */
    void start() throws Exception;

    /**
     * Polls the data source for changes.
     * Returns a PollResult indicating whether there are changes and containing
     * the files to process if there are changes.
     *
     * @return PollResult with changes information
     * @throws Exception if polling fails
     */
    PollResult poll() throws Exception;

    /**
     * Processes the data files and imports them into the provided storage.
     *
     * @param storage the registry storage to import data into
     * @param files the files to process
     * @return ProcessingResult indicating success or failure with error details
     * @throws Exception if processing fails unexpectedly
     */
    ProcessingResult process(RegistryStorage storage, PollResult pollResult) throws Exception;

    /**
     * Commits the change marker after successful processing and switch.
     * Called after the data has been successfully loaded and the storage has been switched.
     *
     * @param marker the marker from the PollResult
     */
    void commitChange(Object marker);

    /**
     * Returns the marker from the last successfully committed change.
     * Used to compare with the current state to detect changes.
     *
     * @return the previous marker, or null if no changes have been committed yet
     */
    Object getPreviousMarker();
}
