package io.apicurio.registry.rules;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

/**
 * A service used to apply configured rules to a given content update.  In other words,
 * when artifact content is being created or updated, this service is used to apply
 * any rules configured for the artifact.
 * @author Ales Justin
 */
public interface RulesService {

    /**
     * Applies all configured rules to check whether a content update for an artifact is allowed.
     * @param artifactId
     * @param artifactType
     * @param artifactContent
     * @param ruleApplicationType
     * @throws RuleViolationException
     */
    public void applyRules(String artifactId, ArtifactType artifactType, String artifactContent,
            RuleApplicationType ruleApplicationType) throws RuleViolationException;

    /**
     * Applies a single, specific rule to the content update for the given artifact.
     * @param artifactId
     * @param artifactType
     * @param artifactContent
     * @param ruleType
     * @param ruleConfiguration
     * @param ruleApplicationType
     * @throws RuleViolationException
     */
    public void applyRule(String artifactId, ArtifactType artifactType, String artifactContent,
            RuleType ruleType, String ruleConfiguration, RuleApplicationType ruleApplicationType)
            throws RuleViolationException;

}
