package io.apicurio.registry.rules;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

/**
 * @author Ales Justin
 */
public interface RulesService {

    public void applyRules(String artifactId, ArtifactType artifactType, String artifactContent,
            RuleApplicationType ruleApplicationType) throws RuleViolationException;

    public void applyRule(String artifactId, ArtifactType artifactType, String artifactContent,
            RuleType ruleType, String ruleConfiguration, RuleApplicationType ruleApplicationType)
            throws RuleViolationException;

}
