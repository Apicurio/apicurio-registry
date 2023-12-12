package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.types.RuleType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ArtifactRuleKey implements MessageKey {

    private String groupId;
    private String artifactId;
    private RuleType ruleType;

    /**
     * Creator method.
     * 
     * @param groupId
     * @param artifactId
     * @param ruleType
     */
    public static final ArtifactRuleKey create(String groupId, String artifactId, RuleType ruleType) {
        ArtifactRuleKey key = new ArtifactRuleKey();
        key.setGroupId(groupId);
        key.setArtifactId(artifactId);
        key.setRuleType(ruleType);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ArtifactRule;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return groupId + "/" + artifactId;
    }

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @param groupId the groupId to set
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @param artifactId the artifactId to set
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @return the ruleType
     */
    public RuleType getRuleType() {
        return ruleType;
    }

    /**
     * @param ruleType the ruleType to set
     */
    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "ArtifactRuleKey [groupId=" + groupId + ", artifactId=" + artifactId + ", ruleType=" + ruleType
                + "]";
    }

}
