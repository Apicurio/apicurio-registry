package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

import java.util.Date;

@RegisterForReflection
@ToString
public class CommentValue extends AbstractMessageValue {

    private long globalId;
    private String createdBy;
    private Date createdOn;
    private String value;

    /**
     * Creator method.
     * 
     * @param action
     * @param globalId
     * @param createdBy
     * @param createdOn
     * @param value
     */
    public static final CommentValue create(ActionType action, long globalId, String createdBy,
            Date createdOn, String value) {
        CommentValue cv = new CommentValue();
        cv.setAction(action);
        cv.setGlobalId(globalId);
        cv.setCreatedBy(createdBy);
        cv.setCreatedOn(createdOn);
        cv.setValue(value);
        return cv;
    }

    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Comment;
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
    public Date getCreatedOn() {
        return createdOn;
    }

    /**
     * @param createdOn the createdOn to set
     */
    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * @return the globalId
     */
    public long getGlobalId() {
        return globalId;
    }

    /**
     * @param globalId the globalId to set
     */
    public void setGlobalId(long globalId) {
        this.globalId = globalId;
    }
}
