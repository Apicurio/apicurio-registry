package io.apicurio.registry.utils.impexp;

import static lombok.AccessLevel.PRIVATE;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.apicurio.registry.types.VersionState;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@ToString
@RegisterForReflection
@JsonIgnoreProperties({"isLatest"})
public class ArtifactVersionEntity extends Entity {

    public long globalId;
    public String groupId;
    public String artifactId;
    public String version;

    @JsonAlias({"versionId"})
    public int versionOrder;

    public String artifactType;
    public VersionState state;
    public String name;
    public String description;
    public String owner;
    public long createdOn;
    public Map<String, String> labels;
    public long contentId;

    /**
     * @see io.apicurio.registry.utils.impexp.Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.ArtifactVersion;
    }

}
