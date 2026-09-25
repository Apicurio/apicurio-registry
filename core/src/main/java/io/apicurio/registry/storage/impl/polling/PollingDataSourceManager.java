package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.storage.RegistryStorage;

/**
 * Interface for managing a data source that provides registry configuration and artifacts.
 * This abstraction allows different data sources (Git repositories, Kubernetes ConfigMaps, etc.)
 * to be used with the same polling-based storage implementation.
 *
 * @param <MARKER> the type of the marker used to track data source state
 */
public interface PollingDataSourceManager<MARKER extends SourceMarker> {

    /**
     * Initializes the data source manager.
     * Called once during storage initialization.
     *
     * @throws Exception if initialization fails
     */
    void start() throws Exception;

    /**
     * Polls the data source for changes.
     * Returns a PollingResult indicating whether there are changes and containing
     * the files to process if there are changes.
     * <p>
     * The returned PollingResult includes a commit action that the caller must invoke
     * after the data has been successfully loaded and published.
     *
     * @return PollingResult with changes information
     * @throws Exception if polling fails
     */
    PollingResult<MARKER> poll() throws Exception;

    /**
     * Processes the data files and imports them into the provided storage.
     *
     * @param storage    the registry storage to import data into
     * @param pollResult the poll result containing files to process
     * @return PollingProcessingResult indicating success or failure with error details
     * @throws Exception if processing fails unexpectedly
     */
    PollingProcessingResult process(RegistryStorage storage, PollingResult<MARKER> pollResult) throws Exception;
}
