package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ArtifactRulesKey implements MessageKey {

    private String groupId;
    private String artifactId;

    /**
     * Creator method.
     * @param groupId
     * @param artifactId
     * @param ruleType
     */
    public static final ArtifactRulesKey create(String groupId, String artifactId) {
        ArtifactRulesKey key = new ArtifactRulesKey();
        key.setGroupId(groupId);
        key.setArtifactId(artifactId);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ArtifactRules;
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
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "ArtifactRuleKey [groupId=" + groupId + ", artifactId=" + artifactId
                + "]";
    }

}
