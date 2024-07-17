package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.v7.SchemasResource;
import io.apicurio.registry.ccompat.v7.beans.Schema;
import io.apicurio.registry.ccompat.v7.beans.SchemaString;
import io.apicurio.registry.ccompat.v7.beans.SubjectVersion;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.Interceptors;

import java.util.List;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SchemasResourceImpl implements SchemasResource {
    @Override
    public List<Schema> getSchemas(String subjectPrefix, Boolean deleted, Boolean latestOnly, Integer offset, Integer limit) {
        Errors.operationNotSupported();
        return List.of();
    }

    @Override
    public SchemaString getSchema(int id, String subject, String format, Boolean fetchMaxId) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public List<String> getSubjects(int id, String subject, Boolean deleted) {
        Errors.operationNotSupported();
        return List.of();
    }

    @Override
    public List<SubjectVersion> getVersions(int id, String subject, Boolean deleted) {
        Errors.operationNotSupported();
        return List.of();
    }

    @Override
    public List<String> getSchemaTypes() {
        Errors.operationNotSupported();
        return List.of();
    }

    @Override
    public String getSchemaOnly(int id, String subject, String format) {
        Errors.operationNotSupported();
        return "";
    }
}
