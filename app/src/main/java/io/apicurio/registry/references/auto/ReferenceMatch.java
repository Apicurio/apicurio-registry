package io.apicurio.registry.references.auto;

import io.apicurio.registry.storage.dto.ArtifactReferenceDto;

import java.util.List;

/**
 * Result of matching references to existing artifacts.
 *
 * @param matched the references that were matched
 * @param problems the references that could not be matched
 */
public record ReferenceMatch(List<ArtifactReferenceDto> matched, List<MatchProblem> problems) {

    /**
     * Indicates whether any reference could not be matched.
     *
     * @return true if there is at least one problem
     */
    public boolean hasProblems() {
        return !problems.isEmpty();
    }
}
