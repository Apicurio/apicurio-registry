package io.apicurio.registry.utils.impexp.v3;

import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static lombok.AccessLevel.PRIVATE;

@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@ToString
@RegisterForReflection
public class CommentEntity extends Entity {

    public long globalId;
    public String commentId;
    public String owner;
    public long createdOn;
    public String value;

    /**
     * @see Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.Comment;
    }
}
