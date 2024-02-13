package io.apicurio.registry.storage.impl.kafkasql.values;

import java.util.Map;

import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

@RegisterForReflection
@ToString
public class GroupValue extends AbstractMessageValue {

    private String description;
    private String artifactsType;
    private String createdBy;
    private long createdOn;
    private String modifiedBy;
    private long modifiedOn;
    private Map<String, String> labels;

    private boolean onlyArtifacts;

    /**
     * Creator method.
     * @param action
     */
    public static final GroupValue create(ActionType action, boolean onlyArtifacts) {
        GroupValue value = new GroupValue();
        value.setAction(action);
        value.setOnlyArtifacts(onlyArtifacts);
        return value;
    }

    /**
     * Creator method.
     * @param action
     * @param group metadata
     */
    public static final GroupValue create(ActionType action, GroupMetaDataDto meta) {
        GroupValue value = new GroupValue();
        value.setAction(action);
        value.setDescription(meta.getDescription());
        value.setArtifactsType(meta.getArtifactsType());
        value.setCreatedBy(meta.getCreatedBy());
        value.setCreatedOn(meta.getCreatedOn());
        value.setModifiedBy(meta.getModifiedBy());
        value.setModifiedOn(meta.getModifiedOn());
        value.setLabels(meta.getLabels());
        return value;
    }

    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Group;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the artifactsType
     */
    public String getArtifactsType() {
        return artifactsType;
    }

    /**
     * @param artifactsType the artifactsType to set
     */
    public void setArtifactsType(String artifactsType) {
        this.artifactsType = artifactsType;
    }

    /**
     * @return the createdBy
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * @param createdBy the createdBy to set
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * @return the createdOn
     */
    public long getCreatedOn() {
        return createdOn;
    }

    /**
     * @param createdOn the createdOn to set
     */
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }

    /**
     * @return the modifiedBy
     */
    public String getModifiedBy() {
        return modifiedBy;
    }

    /**
     * @param modifiedBy the modifiedBy to set
     */
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    /**
     * @return the modifiedOn
     */
    public long getModifiedOn() {
        return modifiedOn;
    }

    /**
     * @param modifiedOn the modifiedOn to set
     */
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    /**
     * @return the onlyArtifacts
     */
    public boolean isOnlyArtifacts() {
        return onlyArtifacts;
    }

    /**
     * @param onlyArtifacts the onlyArtifacts to set
     */
    public void setOnlyArtifacts(boolean onlyArtifacts) {
        this.onlyArtifacts = onlyArtifacts;
    }

    /**
     * @return the labels
     */
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * @param labels the labels to set
     */
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

}
