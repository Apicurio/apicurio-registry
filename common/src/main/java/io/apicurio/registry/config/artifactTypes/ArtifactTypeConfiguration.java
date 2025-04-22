
package io.apicurio.registry.config.artifactTypes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "artifactType",
    "name",
    "description",
    "contentTypes",
    "contentAccepter",
    "compatibilityChecker",
    "contentCanonicalizer",
    "contentValidator",
    "contentDereferencer",
    "referenceFinder",
    "supportsReferencesWithContext"
})
@io.quarkus.runtime.annotations.RegisterForReflection
public class ArtifactTypeConfiguration {

    @JsonProperty("artifactType")
    private String artifactType;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("contentTypes")
    private List<String> contentTypes = new ArrayList<String>();

    @JsonProperty("contentAccepter")
    @JsonPropertyDescription("")
    private Provider contentAccepter;

    @JsonProperty("compatibilityChecker")
    @JsonPropertyDescription("")
    private Provider compatibilityChecker;

    @JsonProperty("contentCanonicalizer")
    @JsonPropertyDescription("")
    private Provider contentCanonicalizer;

    @JsonProperty("contentValidator")
    @JsonPropertyDescription("")
    private Provider contentValidator;

    @JsonProperty("contentDereferencer")
    @JsonPropertyDescription("")
    private Provider contentDereferencer;

    @JsonProperty("referenceFinder")
    @JsonPropertyDescription("")
    private Provider referenceFinder;

    @JsonProperty("supportsReferencesWithContext")
    private Boolean supportsReferencesWithContext;


    @JsonProperty("artifactType")
    public String getArtifactType() {
        return artifactType;
    }

    @JsonProperty("artifactType")
    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("contentTypes")
    public List<String> getContentTypes() {
        return contentTypes;
    }

    @JsonProperty("contentTypes")
    public void setContentTypes(List<String> contentTypes) {
        this.contentTypes = contentTypes;
    }

    @JsonProperty("contentAccepter")
    public Provider getContentAccepter() {
        return contentAccepter;
    }

    @JsonProperty("contentAccepter")
    public void setContentAccepter(Provider contentAccepter) {
        this.contentAccepter = contentAccepter;
    }

    @JsonProperty("compatibilityChecker")
    public Provider getCompatibilityChecker() {
        return compatibilityChecker;
    }

    @JsonProperty("compatibilityChecker")
    public void setCompatibilityChecker(Provider compatibilityChecker) {
        this.compatibilityChecker = compatibilityChecker;
    }

    @JsonProperty("contentCanonicalizer")
    public Provider getContentCanonicalizer() {
        return contentCanonicalizer;
    }

    @JsonProperty("contentCanonicalizer")
    public void setContentCanonicalizer(Provider contentCanonicalizer) {
        this.contentCanonicalizer = contentCanonicalizer;
    }

    @JsonProperty("contentValidator")
    public Provider getContentValidator() {
        return contentValidator;
    }

    @JsonProperty("contentValidator")
    public void setContentValidator(Provider contentValidator) {
        this.contentValidator = contentValidator;
    }

    @JsonProperty("contentDereferencer")
    public Provider getContentDereferencer() {
        return contentDereferencer;
    }

    @JsonProperty("contentDereferencer")
    public void setContentDereferencer(Provider contentDereferencer) {
        this.contentDereferencer = contentDereferencer;
    }

    @JsonProperty("referenceFinder")
    public Provider getReferenceFinder() {
        return referenceFinder;
    }

    @JsonProperty("referenceFinder")
    public void setReferenceFinder(Provider referenceFinder) {
        this.referenceFinder = referenceFinder;
    }

    @JsonProperty("supportsReferencesWithContext")
    public Boolean getSupportsReferencesWithContext() {
        return supportsReferencesWithContext;
    }

    @JsonProperty("supportsReferencesWithContext")
    public void setSupportsReferencesWithContext(Boolean supportsReferencesWithContext) {
        this.supportsReferencesWithContext = supportsReferencesWithContext;
    }

}
