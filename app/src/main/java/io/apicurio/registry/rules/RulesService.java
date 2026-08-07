package io.apicurio.registry.rules;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.RuleType;

import java.util.List;
import java.util.Map;

/**
 * A service used to apply configured rules to a given content update. In other words, when artifact content
 * is being created or updated, this service is used to apply any rules configured for the artifact.
 */
public interface RulesService {

    /**
     * Primary entry point to apply all configured rules using a {@link RuleApplicationContext}.
     *
     * @param context the rule application context
     * @throws RuleViolationException if a rule violation occurs
     */
    public void applyRules(RuleApplicationContext context) throws RuleViolationException;

    /**
     * Primary entry point to apply a single specific rule using a {@link RuleApplicationContext}.
     *
     * @param context the rule application context (must specify {@code ruleType})
     * @throws RuleViolationException if a rule violation occurs
     */
    public void applyRule(RuleApplicationContext context) throws RuleViolationException;

    /**
     * Applies all configured rules to check whether a content update for an artifact is allowed.
     */
    public void applyRules(String groupId, String artifactId, String artifactType, TypedContent content,
            RuleApplicationType ruleApplicationType, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException;

    /**
     * Applies a single, specific rule to the content update for the given artifact.
     */
    public void applyRule(String groupId, String artifactId, String artifactType, TypedContent content,
            RuleType ruleType, String ruleConfiguration, RuleApplicationType ruleApplicationType,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException;

    /**
     * Applies configured rules to the content update, relative to ANY artifact version.
     */
    public void applyRules(String groupId, String artifactId, String artifactVersion, String artifactType,
            TypedContent updatedContent, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException;

    /**
     * Applies all configured rules using the provided storage instance.
     * This allows validation against a different storage (e.g., the inactive database
     * during GitOps blue-green loading).
     */
    public void applyRules(RegistryStorage storage, String groupId, String artifactId, String artifactType,
            TypedContent content, RuleApplicationType ruleApplicationType,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException;

    /**
     * Applies all configured rules using the provided storage instance and explicit
     * existing content for comparison (e.g., for compatibility checks during GitOps loading
     * where all versions are already imported into the same storage).
     */
    public void applyRules(RegistryStorage storage, String groupId, String artifactId, String artifactType,
            TypedContent content, List<TypedContent> existingContent,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException;
}
