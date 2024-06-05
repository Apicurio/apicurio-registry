package io.apicurio.registry.rules;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Contains all of the information needed by a rule executor, including the rule-specific
 * configuration, current and updated content, and any other meta-data needed.
 *
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class RuleContext {
    private final String groupId;
    private final String artifactId;
    private final String artifactType;
    private final String configuration;
    private final List<TypedContent> currentContent;
    private final TypedContent updatedContent;
    private final List<ArtifactReference> references;
    private final Map<String, TypedContent> resolvedReferences;
}
