package io.apicurio.registry.a2a.rest.beans;

import java.util.List;

/**
 * Search results for agent cards.
 */
public class AgentSearchResults {

    private long count;
    private List<AgentSearchResult> agents;

    public AgentSearchResults() {
    }

    public AgentSearchResults(long count, List<AgentSearchResult> agents) {
        this.count = count;
        this.agents = agents;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public List<AgentSearchResult> getAgents() {
        return agents;
    }

    public void setAgents(List<AgentSearchResult> agents) {
        this.agents = agents;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long count;
        private List<AgentSearchResult> agents;

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public Builder agents(List<AgentSearchResult> agents) {
            this.agents = agents;
            return this;
        }

        public AgentSearchResults build() {
            return new AgentSearchResults(count, agents);
        }
    }
}
