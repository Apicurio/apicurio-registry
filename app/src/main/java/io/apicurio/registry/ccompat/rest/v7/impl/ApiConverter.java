package io.apicurio.registry.ccompat.rest.v7.impl;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.dto.SchemaReference;
import io.apicurio.registry.ccompat.dto.SubjectVersion;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;

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
                convertUnsigned(cconfig.legacyIdModeEnabled.get() ? storedArtifact.getGlobalId() : storedArtifact.getContentId()),
                subject,
                convertUnsigned(storedArtifact.getVersionOrder()),
                storedArtifact.getContent().content(),
                artifactType,
                storedArtifact.getReferences().stream().map(this::convert).collect(Collectors.toList())
        );
    }

    public SchemaInfo convert(ContentHandle content, String artifactType, List<ArtifactReferenceDto> references) {
        return new SchemaInfo(content.content(), artifactType, references.stream().map(this::convert).collect(Collectors.toList()));
    }

    public SubjectVersion convert(String artifactId, Number version) {
        return new SubjectVersion(artifactId, version.longValue());
    }

    public SchemaReference convert(ArtifactReferenceDto reference) {
        return new SchemaReference(reference.getName(), reference.getArtifactId(), Integer.parseInt(reference.getVersion()));
    }
}
