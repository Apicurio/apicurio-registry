package io.apicurio.registry.utils.export.mappers;

import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;

import javax.inject.Singleton;

@Singleton
public class ArtifactReferenceMapper implements Mapper<SchemaReference, ArtifactReference> {

    @Override
    public ArtifactReference map(SchemaReference entity) {
        ArtifactReference artifactReference = new ArtifactReference();
        artifactReference.setGroupId(null);
        artifactReference.setName(entity.getName());
        artifactReference.setVersion(entity.getVersion().toString());
        artifactReference.setArtifactId(entity.getSubject());
        return artifactReference;
    }
}
