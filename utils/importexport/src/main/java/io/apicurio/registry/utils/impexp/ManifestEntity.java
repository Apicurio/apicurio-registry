package io.apicurio.registry.utils.impexp;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Date;

@RegisterForReflection
public class ManifestEntity extends Entity {

    public String systemVersion;
    public String systemName;
    public String systemDescription;
    public String exportVersion = "3.0";
    public Date exportedOn = new Date();
    public String exportedBy;
    public String dbVersion;

    /**
     * @see Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.Manifest;
    }
}
