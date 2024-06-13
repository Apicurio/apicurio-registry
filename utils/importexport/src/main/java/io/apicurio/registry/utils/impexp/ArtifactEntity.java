package io.apicurio.registry.utils.impexp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Map;

import static lombok.AccessLevel.PRIVATE;

@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@ToString
@RegisterForReflection
@JsonIgnoreProperties({"isLatest"})
public class ArtifactEntity extends Entity {

    public String groupId;
    public String artifactId;
    public String artifactType;
    public String name;
    public String description;
    public Map<String, String> labels;
    public String owner;
    public long createdOn;
    public String modifiedBy;
    public long modifiedOn;

    /**
     * @see Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.Artifact;
    }

}
