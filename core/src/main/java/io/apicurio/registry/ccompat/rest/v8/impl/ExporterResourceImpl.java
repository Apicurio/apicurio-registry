package io.apicurio.registry.ccompat.rest.v8.impl;

import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.rest.v8.ExportersResource;
import io.apicurio.registry.ccompat.rest.v8.beans.Exporter;
import io.apicurio.registry.ccompat.rest.v8.beans.ExporterCreateRequest;
import io.apicurio.registry.ccompat.rest.v8.beans.ExporterStatus;
import io.apicurio.registry.ccompat.rest.v8.beans.ExporterUpdateRequest;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * v8 implementation of ExportersResource that delegates to v7 implementation.
 */
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ExporterResourceImpl implements ExportersResource {

    @Inject
    io.apicurio.registry.ccompat.rest.v7.impl.ExporterResourceImpl v7ExporterResource;

    @Override
    public List<String> getExporters() {
        // Exporters are not implemented, return empty list for compatibility
        return Collections.emptyList();
    }

    @Override
    public Response createExporter(ExporterCreateRequest data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Exporter getExporter(String name) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response updateExporter(String name, ExporterUpdateRequest data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public void deleteExporter(String name) {
        Errors.operationNotSupported();
    }

    @Override
    public Response pauseExporter(String name) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response resetExporter(String name) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response resumeExporter(String name) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response updateExporterConfig(String name, InputStream data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public ExporterStatus getExporterStatus(String name) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response getExporterConfig(String name) {
        Errors.operationNotSupported();
        return null;
    }
}
