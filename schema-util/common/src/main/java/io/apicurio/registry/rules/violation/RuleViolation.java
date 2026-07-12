package io.apicurio.registry.rules.violation;

import io.apicurio.registry.rules.compatibility.CompatibilityFixSuggestion;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RuleViolation {

    private String description;
    private String context;
    private String type;
    private List<CompatibilityFixSuggestion> suggestions = Collections.emptyList();

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

    public RuleViolation(String description, String context, String type,
            List<CompatibilityFixSuggestion> suggestions) {
        this.setDescription(description);
        this.setContext(context);
        this.setType(type);
        this.setSuggestions(suggestions);
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
     * @return machine-readable incompatibility type when available (e.g. Avro
     *         {@code READER_FIELD_MISSING_DEFAULT_VALUE})
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return optional fix suggestions for this violation
     */
    public List<CompatibilityFixSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<CompatibilityFixSuggestion> suggestions) {
        this.suggestions = suggestions == null ? Collections.emptyList()
                : Collections.unmodifiableList(suggestions);
    }

    public boolean hasSuggestions() {
        return suggestions != null && !suggestions.isEmpty();
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(context, description, type, suggestions);
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
        return Objects.equals(context, other.context) && Objects.equals(description, other.description)
                && Objects.equals(type, other.type) && Objects.equals(suggestions, other.suggestions);
    }

}
