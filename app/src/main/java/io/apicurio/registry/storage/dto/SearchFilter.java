package io.apicurio.registry.storage.dto;

import org.apache.commons.lang3.tuple.Pair;

import io.apicurio.registry.types.VersionState;

public class SearchFilter {

    private SearchFilterType type;
    private Object value;
    private boolean not;

    /**
     * Constructor.
     */
    public SearchFilter() {
    }

    /**
     * Constructor.
     * @param type
     * @param value object
     */
    private SearchFilter(SearchFilterType type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static SearchFilter ofLabel(String labelKey, String labelValue) {
        return new SearchFilter(SearchFilterType.labels, Pair.<String, String>of(labelKey, labelValue));
    }

    public static SearchFilter ofLabel(String labelKey) {
        return new SearchFilter(SearchFilterType.labels, Pair.<String, String>of(labelKey, null));
    }

    public static SearchFilter ofGlobalId(Long value) {
        return new SearchFilter(SearchFilterType.globalId, value);
    }

    public static SearchFilter ofContentId(Long value) {
        return new SearchFilter(SearchFilterType.contentId, value);
    }

    public static SearchFilter ofName(String value) {
        return new SearchFilter(SearchFilterType.name, value);
    }

    public static SearchFilter ofDescription(String value) {
        return new SearchFilter(SearchFilterType.description, value);
    }

    public static SearchFilter ofGroup(String value) {
        return new SearchFilter(SearchFilterType.group, value);
    }

    public static SearchFilter ofCanonicalHash(String value) {
        return new SearchFilter(SearchFilterType.canonicalHash, value);
    }

    public static SearchFilter ofContentHash(String value) {
        return new SearchFilter(SearchFilterType.contentHash, value);
    }

    public static SearchFilter ofState(VersionState state) {
        return new SearchFilter(SearchFilterType.state, state.name());
    }

    @SuppressWarnings("unchecked")
    public Pair<String, String> getLabelFilterValue() {
        if (value == null) {
            return null;
        }
        if (this.value instanceof Pair) {
            return (Pair<String, String>) this.value;
        }
        throw new IllegalStateException("value is not of type pair");
    }

    /**
     * @return the string value
     */
    public String getStringValue() {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        throw new IllegalStateException("value is not of type string");
    }

    /**
     * @return the integer value
     */
    public Number getNumberValue() {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return (Number) value;
        }
        throw new IllegalStateException("value is not of type number");
    }


    /**
     * @param value the value to set
     */
    public void setStringValue(String value) {
        this.value = value;
    }

    /**
     * @return the type
     */
    public SearchFilterType getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(SearchFilterType type) {
        this.type = type;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "SearchFilter" + (isNot() ? " NOT" : "") + " [type=" + type + ", value=" + value + "]";
    }

    /**
     * @return the not
     */
    public boolean isNot() {
        return not;
    }

    /**
     * @param not the not to set
     */
    public void setNot(boolean not) {
        this.not = not;
    }
    
    public SearchFilter negated() {
        SearchFilter filter = new SearchFilter(type, value);
        filter.setNot(true);
        return filter;
    }

}
