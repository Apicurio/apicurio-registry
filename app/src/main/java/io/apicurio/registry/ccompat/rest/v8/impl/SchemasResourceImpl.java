package io.apicurio.registry.ccompat.rest.v8.impl;

import io.apicurio.registry.ccompat.rest.v8.SchemasResource;
import io.apicurio.registry.ccompat.rest.v8.beans.Schema;
import io.apicurio.registry.ccompat.rest.v8.beans.SchemaReference;
import io.apicurio.registry.ccompat.rest.v8.beans.SubjectVersion;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

/**
 * v8 implementation of SchemasResource that delegates to v7 implementation.
 */
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SchemasResourceImpl implements SchemasResource {

    @Inject
    io.apicurio.registry.ccompat.rest.v7.impl.SchemasResourceImpl v7SchemasResource;

    @Override
    public Schema getSchemaById(BigInteger id, String format, String subject) {
        io.apicurio.registry.ccompat.rest.v7.beans.Schema v7Schema = v7SchemasResource.getSchemaById(id, format, subject);
        return convertSchema(v7Schema);
    }

    @Override
    public String getSchemaContentById(BigInteger id, String format, String subject) {
        return v7SchemasResource.getSchemaContentById(id, format, subject);
    }

    @Override
    public List<String> getSchemaTypes() {
        return v7SchemasResource.getSchemaTypes();
    }

    @Override
    public List<Schema> getSchemas(String subjectPrefix, Boolean deleted, Boolean latestOnly,
            BigInteger offset, BigInteger limit) {
        List<io.apicurio.registry.ccompat.rest.v7.beans.Schema> v7Schemas =
                v7SchemasResource.getSchemas(subjectPrefix, deleted, latestOnly, offset, limit);
        return v7Schemas.stream().map(this::convertSchema).collect(Collectors.toList());
    }

    @Override
    public List<String> getSubjectsBySchemaId(BigInteger id, Boolean deleted) {
        return v7SchemasResource.getSubjectsBySchemaId(id, deleted);
    }

    @Override
    public List<SubjectVersion> getSchemaVersionsById(BigInteger id, Boolean deleted) {
        List<io.apicurio.registry.ccompat.rest.v7.beans.SubjectVersion> v7Versions =
                v7SchemasResource.getSchemaVersionsById(id, deleted);
        return v7Versions.stream().map(this::convertSubjectVersion).collect(Collectors.toList());
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
                    .map(this::convertSchemaReference)
                    .collect(Collectors.toList()));
        }
        return schema;
    }

    private SchemaReference convertSchemaReference(io.apicurio.registry.ccompat.rest.v7.beans.SchemaReference v7Ref) {
        SchemaReference ref = new SchemaReference();
        ref.setName(v7Ref.getName());
        ref.setSubject(v7Ref.getSubject());
        ref.setVersion(v7Ref.getVersion());
        return ref;
    }

    private SubjectVersion convertSubjectVersion(io.apicurio.registry.ccompat.rest.v7.beans.SubjectVersion v7Sv) {
        SubjectVersion sv = new SubjectVersion();
        sv.setSubject(v7Sv.getSubject());
        sv.setVersion(v7Sv.getVersion());
        return sv;
    }
}
