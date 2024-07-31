package io.apicurio.registry.utils.impexp.v3;

import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityType;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Date;

@RegisterForReflection
public class ManifestEntity extends Entity {

    public String systemVersion;
    public String systemName;
    public String systemDescription;
    public String exportVersion = "1.0";
    public Date exportedOn = new Date();
    public String exportedBy;

    /**
     * @see Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.Manifest;
    }
}
