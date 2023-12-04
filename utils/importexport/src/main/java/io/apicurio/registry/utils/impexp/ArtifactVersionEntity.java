package io.apicurio.registry.utils.impexp;

import io.apicurio.registry.types.ArtifactState;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;


@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@ToString
@RegisterForReflection
public class ArtifactVersionEntity extends Entity {

    public long globalId;
    public String groupId;
    public String artifactId;
    public String version;
    public int versionId;
    public String artifactType;
    public ArtifactState state;
    public String name;
    public String description;
    public String createdBy;
    public long createdOn;
    public List<String> labels;
    public Map<String, String> properties;
    public boolean isLatest;
    public long contentId;

    /**
     * @see io.apicurio.registry.utils.impexp.Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.ArtifactVersion;
    }

}
