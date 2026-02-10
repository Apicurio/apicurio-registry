package io.apicurio.registry.ccompat.rest.v8.impl;

import io.apicurio.registry.ccompat.rest.v8.SubjectsResource;
import io.apicurio.registry.ccompat.rest.v8.beans.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.rest.v8.beans.Schema;
import io.apicurio.registry.ccompat.rest.v8.beans.SchemaId;
import io.apicurio.registry.ccompat.rest.v8.beans.SchemaReference;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

/**
 * v8 implementation of SubjectsResource that delegates to v7 implementation.
 */
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SubjectsResourceImpl implements SubjectsResource {

    @Inject
    io.apicurio.registry.ccompat.rest.v7.impl.SubjectsResourceImpl v7SubjectsResource;

    @Override
    public List<String> getSubjects(String subjectPrefix, Boolean deleted, Boolean deletedOnly,
            BigInteger offset, BigInteger limit, String xRegistryGroupId) {
        return v7SubjectsResource.getSubjects(subjectPrefix, deleted, deletedOnly, offset, limit, xRegistryGroupId);
    }

    @Override
    public Schema lookupSchemaByContent(String subject, Boolean normalize, String format,
            String xRegistryGroupId, Boolean deleted, RegisterSchemaRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest v7Request = convertToV7Request(data);
        io.apicurio.registry.ccompat.rest.v7.beans.Schema v7Schema =
                v7SubjectsResource.lookupSchemaByContent(subject, normalize, format, xRegistryGroupId, deleted, v7Request);
        return convertSchema(v7Schema);
    }

    @Override
    public List<BigInteger> deleteSubject(String subject, Boolean permanent, String xRegistryGroupId) {
        return v7SubjectsResource.deleteSubject(subject, permanent, xRegistryGroupId);
    }

    @Override
    public List<BigInteger> getSubjectVersions(String subject, String xRegistryGroupId, Boolean deleted,
            Boolean deletedOnly, BigInteger offset, BigInteger limit) {
        return v7SubjectsResource.getSubjectVersions(subject, xRegistryGroupId, deleted, deletedOnly, offset, limit);
    }

    @Override
    public SchemaId registerSchemaUnderSubject(String subject, Boolean normalize, String format,
            String xRegistryGroupId, RegisterSchemaRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest v7Request = convertToV7Request(data);
        io.apicurio.registry.ccompat.rest.v7.beans.SchemaId v7SchemaId =
                v7SubjectsResource.registerSchemaUnderSubject(subject, normalize, format, xRegistryGroupId, v7Request);
        SchemaId schemaId = new SchemaId();
        schemaId.setId(v7SchemaId.getId());
        return schemaId;
    }

    @Override
    public Schema getSchemaVersion(String subject, String version, String format, String xRegistryGroupId,
            Boolean deleted) {
        io.apicurio.registry.ccompat.rest.v7.beans.Schema v7Schema =
                v7SubjectsResource.getSchemaVersion(subject, version, format, xRegistryGroupId, deleted);
        return convertSchema(v7Schema);
    }

    @Override
    public BigInteger deleteSchemaVersion(String subject, String version, Boolean permanent,
            String xRegistryGroupId) {
        return v7SubjectsResource.deleteSchemaVersion(subject, version, permanent, xRegistryGroupId);
    }

    @Override
    public String getSchemaVersionContent(String subject, String version, String format,
            String xRegistryGroupId, Boolean deleted) {
        return v7SubjectsResource.getSchemaVersionContent(subject, version, format, xRegistryGroupId, deleted);
    }

    @Override
    public List<BigInteger> getReferencedBy(String subject, String version, String xRegistryGroupId) {
        return v7SubjectsResource.getReferencedBy(subject, version, xRegistryGroupId);
    }

    @Override
    public Schema getSubjectMetadata(String subject, String key, String value, String format,
            Boolean deleted, String xRegistryGroupId) {
        io.apicurio.registry.ccompat.rest.v7.beans.Schema v7Schema =
                v7SubjectsResource.getSubjectMetadata(subject, key, value, format, deleted, xRegistryGroupId);
        return v7Schema != null ? convertSchema(v7Schema) : null;
    }

    private io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest convertToV7Request(RegisterSchemaRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest v7Request =
                new io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest();
        v7Request.setSchema(data.getSchema());
        v7Request.setSchemaType(data.getSchemaType());
        if (data.getReferences() != null) {
            v7Request.setReferences(data.getReferences().stream()
                    .map(ref -> {
                        io.apicurio.registry.ccompat.rest.v7.beans.SchemaReference v7Ref =
                                new io.apicurio.registry.ccompat.rest.v7.beans.SchemaReference();
                        v7Ref.setName(ref.getName());
                        v7Ref.setSubject(ref.getSubject());
                        v7Ref.setVersion(ref.getVersion());
                        return v7Ref;
                    })
                    .collect(Collectors.toList()));
        }
        return v7Request;
    }

    private Schema convertSchema(io.apicurio.registry.ccompat.rest.v7.beans.Schema v7Schema) {
        Schema schema = new Schema();
        schema.setId(v7Schema.getId());
        schema.setSchema(v7Schema.getSchema());
        schema.setSchemaType(v7Schema.getSchemaType());
        schema.setSubject(v7Schema.getSubject());
        schema.setVersion(v7Schema.getVersion());
        if (v7Schema.getReferences() != null) {
            schema.setReferences(v7Schema.getReferences().stream()
                    .map(v7Ref -> {
                        SchemaReference ref = new SchemaReference();
                        ref.setName(v7Ref.getName());
                        ref.setSubject(v7Ref.getSubject());
                        ref.setVersion(v7Ref.getVersion());
                        return ref;
                    })
                    .collect(Collectors.toList()));
        }
        return schema;
    }
}
