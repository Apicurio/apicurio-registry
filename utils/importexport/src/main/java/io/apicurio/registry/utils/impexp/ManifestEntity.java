package io.apicurio.registry.utils.impexp;

import java.util.Date;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ManifestEntity extends Entity {

    public String systemVersion;
    public String systemName;
    public String systemDescription;
    public String exportVersion = "1.0";
    public Date exportedOn = new Date();
    public String exportedBy;

    /**
     * @see io.apicurio.registry.utils.impexp.Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.Manifest;
    }
}
