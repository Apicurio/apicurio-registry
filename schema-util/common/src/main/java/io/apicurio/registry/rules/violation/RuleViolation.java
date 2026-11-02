package io.apicurio.registry.rules.violation;

import io.apicurio.registry.rest.v3.beans.DifferenceLocation;
import io.apicurio.registry.types.CompatibilityDifferenceDirection;

import java.util.Objects;

public class RuleViolation {

    private String description;
    private String context;
    private String code;
    private CompatibilityDifferenceDirection direction;
    private DifferenceLocation proposed;
    private DifferenceLocation existing;

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
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * @param code the code to set
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * @return the direction
     */
    public CompatibilityDifferenceDirection getDirection() {
        return direction;
    }

    /**
     * @param direction the direction to set
     */
    public void setDirection(CompatibilityDifferenceDirection direction) {
        this.direction = direction;
    }

    /**
     * @return the proposed location
     */
    public DifferenceLocation getProposed() {
        return proposed;
    }

    /**
     * @param proposed the proposed location to set
     */
    public void setProposed(DifferenceLocation proposed) {
        this.proposed = proposed;
    }

    /**
     * @return the existing location
     */
    public DifferenceLocation getExisting() {
        return existing;
    }

    /**
     * @param existing the existing location to set
     */
    public void setExisting(DifferenceLocation existing) {
        this.existing = existing;
    }

    /**
     * Equality stays on description and context only, so that adding the fields above does not change
     * how violations de-duplicate while nothing populates them yet. Widening it belongs with #10466,
     * which fills the fields and fixes the de-duplication that currently merges distinct differences.
     *
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
