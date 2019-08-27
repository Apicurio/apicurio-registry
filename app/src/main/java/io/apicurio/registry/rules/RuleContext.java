package io.apicurio.registry.rules;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.CompatibilityLevel;
import io.apicurio.registry.types.RuleType;

import java.util.Objects;

/**
 * @author Ales Justin
 */
public class RuleContext {
    private RuleType ruleType;
    private ArtifactType artifactType;
    private CompatibilityLevel level;
    private String artifactId;
    private String content;

    public RuleContext(RuleType ruleType, ArtifactType artifactType, CompatibilityLevel level, String artifactId, String content) {
        this.ruleType = Objects.requireNonNull(ruleType);
        this.artifactType = Objects.requireNonNull(artifactType);
        this.level = level;
        this.artifactId = Objects.requireNonNull(artifactId);
        this.content = Objects.requireNonNull(content);
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public ArtifactType getArtifactType() {
        return artifactType;
    }

    public CompatibilityLevel getLevel() {
        return level;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getContent() {
        return content;
    }
}
