package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for the advanced agent search endpoint (POST /.well-known/agents/search).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSearchRequest {

    @JsonProperty("query")
    private String query;

    @JsonProperty("filters")
    private AgentSearchFilters filters;

    @JsonProperty("limit")
    private int limit = 20;

    @JsonProperty("offset")
    private int offset = 0;

    public AgentSearchRequest() {
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public AgentSearchFilters getFilters() {
        return filters;
    }

    public void setFilters(AgentSearchFilters filters) {
        this.filters = filters;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
