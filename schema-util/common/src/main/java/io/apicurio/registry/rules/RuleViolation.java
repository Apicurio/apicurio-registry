package io.apicurio.registry.rules;

import java.util.Objects;

public class RuleViolation {

    private String description;
    private String context;

    /**
     * Constructor.
     */
    public RuleViolation() {
    }

    /**
     * Constructor.
     * 
     * @param description
     * @param context
     */
    public RuleViolation(String description, String context) {
        this.setDescription(description);
        this.setContext(context);
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
     * @return the context
     */
    public String getContext() {
        return context;
    }

    /**
     * @param context the context to set
     */
    public void setContext(String context) {
        this.context = context;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(context, description);
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RuleViolation other = (RuleViolation) obj;
        return Objects.equals(context, other.context) && Objects.equals(description, other.description);
    }

}
