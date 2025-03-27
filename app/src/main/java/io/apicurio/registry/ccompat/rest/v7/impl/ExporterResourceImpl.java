package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.rest.v7.ExportersResource;
import io.apicurio.registry.ccompat.rest.v7.beans.Exporter;
import io.apicurio.registry.ccompat.rest.v7.beans.ExporterCreateRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.ExporterStatus;
import io.apicurio.registry.ccompat.rest.v7.beans.ExporterUpdateRequest;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.util.List;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ExporterResourceImpl extends AbstractResource implements ExportersResource {

    //Exporters are not implemented.

    @Override
    public List<String> getExporters() {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response createExporter(ExporterCreateRequest exporter) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Exporter getExporter(String exporterName) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response updateExporter(String exporterName, ExporterUpdateRequest exporter) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public void deleteExporter(String exporterName) {
        Errors.operationNotSupported();
    }

    @Override
    public Response pauseExporter(String exporterName) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response resetExporter(String exporterName) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response resumeExporter(String exporterName) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response updateExporterConfig(String exporterName, InputStream config) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public ExporterStatus getExporterStatus(String exporterName) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Response getExporterConfig(String exporterName) {
        Errors.operationNotSupported();
        return null;
    }
}
