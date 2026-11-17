package io.apicurio.registry.client.refs;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.content.refs.resolve.PlannedArtifact;

/**
 * Thrown when registering an artifact of a plan fails. Carries the failed artifact, the registry problem (if
 * any) and everything registered before the failure.
 */
public class RegistrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient PlannedArtifact failed;
    private final transient ApiException problem;
    private final transient RegistrationResult partialResult;

    /**
     * Creates a new exception.
     *
     * @param message the message
     * @param failed the artifact whose registration failed
     * @param problem the registry problem, or {@code null}
     * @param partialResult the artifacts registered before the failure
     * @param cause the cause
     */
    public RegistrationException(String message, PlannedArtifact failed, ApiException problem,
            RegistrationResult partialResult, Throwable cause) {
        super(message, cause);
        this.failed = failed;
        this.problem = problem;
        this.partialResult = partialResult;
    }

    /**
     * Returns the artifact whose registration failed.
     *
     * @return the failed artifact
     */
    public PlannedArtifact failed() {
        return failed;
    }

    /**
     * Returns the registry problem ({@code ProblemDetails} or {@code RuleViolationProblemDetails}).
     *
     * @return the problem, or {@code null} when the failure was not a registry error response
     */
    public ApiException problem() {
        return problem;
    }

    /**
     * Returns everything registered before the failure.
     *
     * @return the partial result
     */
    public RegistrationResult partialResult() {
        return partialResult;
    }
}
