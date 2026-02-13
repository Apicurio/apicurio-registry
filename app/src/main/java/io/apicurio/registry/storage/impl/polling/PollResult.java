package io.apicurio.registry.storage.impl.polling;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Represents the result of polling a data source for changes.
 * Contains a marker (e.g., commit hash or resourceVersion) to track state,
 * a flag indicating whether there are changes, and the list of files to process.
 */
@Getter
public class PollResult {

    /**
     * A marker identifying the current state of the data source.
     * For Git, this is the commit hash. For Kubernetes, this is the resourceVersion.
     */
    private final Object marker;

    /**
     * Whether there are changes since the last poll.
     */
    private final boolean hasChanges;

    /**
     * The list of data files to process (only populated when hasChanges is true).
     */
    private final List<DataFile> files;

    private PollResult(Object marker, boolean hasChanges, List<DataFile> files) {
        this.marker = marker;
        this.hasChanges = hasChanges;
        this.files = files;
    }

    /**
     * Creates a PollResult indicating no changes were detected.
     */
    public static PollResult noChanges(Object marker) {
        return new PollResult(marker, false, Collections.emptyList());
    }

    /**
     * Creates a PollResult indicating changes were detected with the given files to process.
     */
    public static PollResult withChanges(Object marker, List<DataFile> files) {
        return new PollResult(marker, true, files != null ? files : Collections.emptyList());
    }
}
