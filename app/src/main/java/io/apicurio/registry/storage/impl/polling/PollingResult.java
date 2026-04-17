package io.apicurio.registry.storage.impl.polling;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Represents the result of polling a data source for changes.
 * Contains a marker for logging/identification, a flag indicating whether there are changes,
 * the list of files to process, and a commit action to finalize the change.
 *
 * @param <MARKER> the type of the marker (e.g., RevCommit for Git, String for Kubernetes resourceVersion)
 */
@Getter
public class PollingResult<MARKER> {

    /**
     * A marker identifying the current state of the data source.
     * For Git, this is the RevCommit. For Kubernetes, this is the resourceVersion string.
     * Used for logging and identification only.
     */
    private final MARKER marker;

    /**
     * Whether there are changes since the last poll.
     */
    private final boolean hasChanges;

    /**
     * The list of data files to process (only populated when hasChanges is true).
     */
    private final List<PollingDataFile> files;

    private final Runnable commitAction;

    private PollingResult(MARKER marker, boolean hasChanges, List<PollingDataFile> files, Runnable commitAction) {
        this.marker = marker;
        this.hasChanges = hasChanges;
        this.files = files;
        this.commitAction = commitAction;
    }

    /**
     * Creates a PollingResult indicating no changes were detected.
     */
    public static <M> PollingResult<M> noChanges(M marker) {
        return new PollingResult<>(marker, false, Collections.emptyList(), null);
    }

    /**
     * Creates a PollingResult indicating changes were detected with the given files to process.
     *
     * @param marker the marker identifying the state (for logging)
     * @param files the data files to process
     * @param commitAction action to execute when the change is committed (e.g., update the manager's internal state)
     */
    public static <M> PollingResult<M> withChanges(M marker, List<PollingDataFile> files, Runnable commitAction) {
        return new PollingResult<>(marker, true, files != null ? files : Collections.emptyList(), commitAction);
    }

    /**
     * Commits this change by executing the commit action provided by the data source manager.
     * Should be called after the data has been successfully loaded and the storage has been switched.
     */
    public void commit() {
        if (commitAction != null) {
            commitAction.run();
        }
    }
}
