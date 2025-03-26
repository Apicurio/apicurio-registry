package io.apicurio.registry.ccompat.rest.v7.impl;


import io.apicurio.registry.ccompat.rest.v7.beans.Schema;
import io.apicurio.registry.ccompat.rest.v7.beans.SchemaReference;
import io.apicurio.registry.ccompat.rest.v7.beans.SubjectVersion;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class ApiConverter {

    @Inject
    CCompatConfig cconfig;

    public int convertUnsigned(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Value out of unsigned integer range: " + value);
        }
        return (int) value;
    }

    public Schema convert(String subject, StoredArtifactVersionDto storedArtifact) {
        return convert(subject, storedArtifact, null);
    }

    public Schema convert(String subject, StoredArtifactVersionDto storedArtifact, String artifactType) {
        return new Schema(
                convertUnsigned(cconfig.legacyIdModeEnabled.get() ? storedArtifact.getGlobalId()
                    : storedArtifact.getContentId()),
                subject, convertUnsigned(storedArtifact.getVersionOrder()),
                storedArtifact.getContent().content(), artifactType,
                storedArtifact.getReferences().stream().map(this::convert).collect(Collectors.toList()));
    }

    public Schema convert(ContentHandle content, String artifactType,
                          List<ArtifactReferenceDto> references) {
        Schema schema = new Schema();
        schema.setSchema(content.content());
        schema.setSchemaType(artifactType);
        return new Schema(content.content(), artifactType,
                references.stream().map(this::convert).collect(Collectors.toList()));
    }

    public SubjectVersion convert(String artifactId, Number version) {
        SubjectVersion subjectVersion = new SubjectVersion();
        subjectVersion.setSubject(artifactId);
        subjectVersion.setVersion(version.intValue());
        return subjectVersion;
    }

    public SchemaReference convert(ArtifactReferenceDto reference) {
        SchemaReference schemaReference = new SchemaReference();
        schemaReference.setName(reference.getName());
        schemaReference.setSubject(reference.getArtifactId());
        schemaReference.setVersion(Integer.parseInt(reference.getVersion()));
        return schemaReference;
    }
}
