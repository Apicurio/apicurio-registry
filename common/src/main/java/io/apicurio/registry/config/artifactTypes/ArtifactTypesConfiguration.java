
package io.apicurio.registry.config.artifactTypes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;


/**
 * Root Type for ArtifactTypesConfiguration
 * <p>
 * Describes a configuration file for configuring the artifact types that should be
 * supported by an Apicurio Registry instance.  Deployers can optionally provide a
 * configuration in this format to dynamically configure the list of artifact types
 * supported by the server.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "includeStandardArtifactTypes",
    "artifactTypes"
})
@io.quarkus.runtime.annotations.RegisterForReflection
public class ArtifactTypesConfiguration {

    @JsonProperty("includeStandardArtifactTypes")
    private Boolean includeStandardArtifactTypes;

    @JsonProperty("artifactTypes")
    private List<ArtifactTypeConfiguration> artifactTypes = new ArrayList<ArtifactTypeConfiguration>();


    @JsonProperty("includeStandardArtifactTypes")
    public Boolean getIncludeStandardArtifactTypes() {
        return includeStandardArtifactTypes;
    }

    @JsonProperty("includeStandardArtifactTypes")
    public void setIncludeStandardArtifactTypes(Boolean includeStandardArtifactTypes) {
        this.includeStandardArtifactTypes = includeStandardArtifactTypes;
    }

    @JsonProperty("artifactTypes")
    public List<ArtifactTypeConfiguration> getArtifactTypes() {
        return artifactTypes;
    }

    @JsonProperty("artifactTypes")
    public void setArtifactTypes(List<ArtifactTypeConfiguration> artifactTypes) {
        this.artifactTypes = artifactTypes;
    }

}
