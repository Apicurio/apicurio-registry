package io.apicurio.registry.ccompat.rest.v7.impl;


import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.ccompat.rest.v7.beans.Schema;
import io.apicurio.registry.ccompat.rest.v7.beans.SchemaReference;
import io.apicurio.registry.ccompat.rest.v7.beans.SubjectVersion;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.types.ArtifactType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class ApiConverter {

    @Inject
    CCompatConfig cconfig;

    @Inject
    @Current
    RegistryStorage storage;

    public BigInteger convertUnsigned(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Value out of unsigned integer range: " + value);
        }
        return BigInteger.valueOf(value);
    }

    public Schema convert(String subject, StoredArtifactVersionDto storedArtifact) {
        return convert(subject, storedArtifact, null);
    }

    public Schema convert(String subject, StoredArtifactVersionDto storedArtifact, String artifactType) {
        Schema schema = new Schema();
        schema.setId(convertUnsigned(cconfig.legacyIdModeEnabled.get() ? storedArtifact.getGlobalId()
                : storedArtifact.getContentId()).intValue());
        if (artifactType != null && !artifactType.equalsIgnoreCase("AVRO")) {
            schema.setSchemaType(artifactType);
        }
        schema.setSubject(subject);
        schema.setVersion(convertUnsigned(storedArtifact.getVersionOrder()).intValue());
        schema.setSchema(compactIfAvro(storedArtifact.getContent().content(), artifactType));
        List<SchemaReference> refs = storedArtifact.getReferences().stream().map(this::convert).collect(Collectors.toList());
        schema.setReferences(refs.isEmpty() ? null : refs);
        return schema;
    }

    public Schema convert(ContentHandle content, String artifactType,
                          List<ArtifactReferenceDto> references) {
        Schema schema = new Schema();
        schema.setSchema(compactIfAvro(content.content(), artifactType));
        if (artifactType != null && !artifactType.equalsIgnoreCase("AVRO")) {
            schema.setSchemaType(artifactType);
        }
        List<SchemaReference> refs = references.stream().map(this::convert).collect(Collectors.toList());
        schema.setReferences(refs.isEmpty() ? null : refs);
        return schema;
    }

    private String compactIfAvro(String content, String artifactType) {
        if (content == null || !ArtifactType.AVRO.equals(artifactType)) {
            return content;
        }
        try {
            org.apache.avro.Schema parsed = new org.apache.avro.Schema.Parser().parse(content);
            return parsed.toString();
        } catch (Exception e) {
            return content;
        }
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
        ArtifactVersionMetaDataDto versionMetaData = storage.getArtifactVersionMetaData(
                reference.getGroupId(), reference.getArtifactId(), reference.getVersion());
        schemaReference.setVersion(versionMetaData.getVersionOrder());
        return schemaReference;
    }
}
