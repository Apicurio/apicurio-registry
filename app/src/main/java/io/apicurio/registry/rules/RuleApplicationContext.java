package io.apicurio.registry.rules;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.RuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Context object containing input parameters for rule application requests passed to {@link RulesService}.
 */
@Getter
@Builder
@AllArgsConstructor
public class RuleApplicationContext {

    private final String groupId;
    private final String artifactId;
    private final String artifactType;
    private final TypedContent content;

    @Builder.Default
    private final RuleApplicationType ruleApplicationType = RuleApplicationType.CREATE;

    @Builder.Default
    private final List<ArtifactReference> references = Collections.emptyList();

    @Builder.Default
    private final Map<String, TypedContent> resolvedReferences = Collections.emptyMap();

    /**
     * Storage instance override. If null, {@link RulesService} uses its injected default storage.
     */
    private final RegistryStorage storage;

    /**
     * Explicit existing content list for comparison. If null, existing content is retrieved from storage.
     */
    private final List<TypedContent> existingContent;

    /**
     * Target artifact version for version-specific rule application.
     */
    private final String artifactVersion;

    /**
     * Specific rule type for single-rule application.
     */
    private final RuleType ruleType;

    /**
     * Configuration for single-rule application.
     */
    private final String ruleConfiguration;
}
