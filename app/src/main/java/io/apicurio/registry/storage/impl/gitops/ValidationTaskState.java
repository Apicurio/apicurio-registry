package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.rest.v3.beans.GitOpsError;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Internal mutable state of a dry-run validation task. Tracks the task lifecycle
 * from creation through sidecar fetch, registry validation, and completion.
 *
 * <p>Note: individual fields are not synchronized. The {@link GitOpsValidationTaskManager}
 * ensures that only the scheduled poll thread mutates task state, while REST threads
 * only read via {@code toDto()}.
 */
@Data
public class ValidationTaskState {

    /** Unique task identifier (12 hex chars). */
    private ValidationTaskId taskId;

    /** Validation type: "pull" or "push". */
    private String type;

    /** Repository ID being validated. */
    private String repoId;

    /** Git ref being validated (branch, tag, or PR ref). */
    private String ref;

    /**
     * Current task state. Transitions:
     * pending → submitted → fetching → validating → completed/failed
     *                     → failed (sidecar error)
     *                                → submitted (storage busy, will retry)
     */
    private ValidationTaskStatus state;

    /** Validation result: "success" or "failure". Only set when state is "completed". */
    private String result;

    /** When the task was created. */
    private Instant createdAt;

    /** When the task completed (success or failure). */
    private Instant completedAt;

    /** Number of groups loaded during validation. */
    private int groupCount;

    /** Number of artifacts loaded during validation. */
    private int artifactCount;

    /** Number of versions loaded during validation. */
    private int versionCount;

    /** Validation errors. Empty/null if validation passed. */
    private List<GitOpsError> errors;

    /** Relative path to the checkout directory within the validate dir. Set by the sidecar. */
    private String checkoutPath;

    /** When the sidecar completed the fetch. */
    private Instant sidecarCompletedAt;

    /** Error message from the sidecar if the fetch failed. */
    private String sidecarError;

    /** Whether the checkout directory and request file have been cleaned from disk. */
    private boolean cleaned;
}
