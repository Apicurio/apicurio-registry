package io.apicurio.registry.storage.impl.kafkasql.values;

/**
 * Base class for all message value classes.
 */
public abstract class AbstractMessageValue implements MessageValue {

    private ActionType action;

    /**
     * @return the action
     */
    public ActionType getAction() {
        return action;
    }

    /**
     * @param action the action to set
     */
    public void setAction(ActionType action) {
        this.action = action;
    }

}
