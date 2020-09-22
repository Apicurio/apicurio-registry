package io.apicurio.registry.storage.impl.panache.result;

import io.quarkus.runtime.annotations.RegisterForReflection;


import java.util.Date;

@RegisterForReflection
public class ArtifactMetaData {

    public ArtifactMetaData(String artifactId, String artifactType, Long globalId, Long version, String state, String name, String description, String labels, String properties, String modifiedBy, Date modifiedOn) {
        this.artifactId = artifactId;
        this.artifactType = artifactType;
        this.globalId = globalId;
        this.version = version;
        this.state = state;
        this.name = name;
        this.description = description;
        this.labels = labels;
        this.properties = properties;
        this.modifiedBy = modifiedBy;
        this.modifiedOn = modifiedOn;
    }

    public String artifactId;

    public String artifactType;

    public Long globalId;
    
    public Long version;
    
    public String state;
    
    public String name;

    public String description;

    public String labels;

    public String properties;

    public String modifiedBy;

    public Date modifiedOn;
}
