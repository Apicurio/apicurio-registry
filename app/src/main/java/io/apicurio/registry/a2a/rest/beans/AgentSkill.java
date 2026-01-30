package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a skill that an A2A agent can perform.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSkill {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("examples")
    private List<String> examples;

    public AgentSkill() {
    }

    public AgentSkill(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getExamples() {
        return examples;
    }

    public void setExamples(List<String> examples) {
        this.examples = examples;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentSkill skill = new AgentSkill();

        public Builder id(String id) {
            skill.setId(id);
            return this;
        }

        public Builder name(String name) {
            skill.setName(name);
            return this;
        }

        public Builder description(String description) {
            skill.setDescription(description);
            return this;
        }

        public Builder tags(List<String> tags) {
            skill.setTags(tags);
            return this;
        }

        public Builder examples(List<String> examples) {
            skill.setExamples(examples);
            return this;
        }

        public AgentSkill build() {
            return skill;
        }
    }
}
