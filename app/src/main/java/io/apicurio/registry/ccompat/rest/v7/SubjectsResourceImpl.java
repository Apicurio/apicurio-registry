package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.v7.SubjectsResource;
import io.apicurio.registry.ccompat.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.v7.beans.RegisterSchemaResponse;
import io.apicurio.registry.ccompat.v7.beans.Schema;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.Interceptors;

import java.util.List;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SubjectsResourceImpl implements SubjectsResource {
    @Override
    public List<String> list(String subjectPrefix, Boolean deleted, Boolean deletedOnly) {
        Errors.operationNotSupported();
        return List.of();
    }

    @Override
    public Schema lookUpSchemaUnderSubject(String subject, Boolean normalize, Boolean deleted, RegisterSchemaRequest data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public List<Integer> deleteSubject(String subject, Boolean permanent) {
        Errors.operationNotSupported();
        return List.of();
    }

    @Override
    public List<Integer> listVersions(String subject, Boolean deleted, Boolean deletedOnly) {
        Errors.operationNotSupported();
        return List.of();
    }

    @Override
    public RegisterSchemaResponse register(String subject, Boolean normalize, RegisterSchemaRequest data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Schema getSchemaByVersion(String subject, String version, Boolean deleted) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public int deleteSchemaVersion(String subject, String version, Boolean permanent) {
        Errors.operationNotSupported();
        return 0;
    }

    @Override
    public List<Integer> getReferencedBy(String subject, String version) {
        Errors.operationNotSupported();
        return List.of();
    }

    @Override
    public String getSchemaOnly_2(String subject, String version, Boolean deleted) {
        Errors.operationNotSupported();
        return "";
    }

    @Override
    public Schema getLatestWithMetadata(String subject, List<String> key, List<String> value, Boolean deleted) {
        Errors.operationNotSupported();
        return null;
    }
}
