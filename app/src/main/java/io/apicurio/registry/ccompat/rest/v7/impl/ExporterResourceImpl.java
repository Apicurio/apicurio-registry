package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.registry.ccompat.dto.ExporterDto;
import io.apicurio.registry.ccompat.dto.ExporterStatus;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.interceptor.Interceptors;

import java.util.List;
import java.util.Map;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ExporterResourceImpl extends AbstractResource implements ExporterResource {

    @Override
    public List<String> getExporters() throws Exception {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public String createExporter(ExporterDto exporter) throws Exception {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public String getExporter(String exporterName) throws Exception {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public String updateExporter(String exporterName, ExporterDto exporter) throws Exception {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public String deleteExporter(String exporterName) throws Exception {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public String pauseExporter(String exporterName) throws Exception {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public String resetExporter(String exporterName) throws Exception {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public String resumeExporter(String exporterName) throws Exception {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public String updateExporterConfig(String exporterName, Map<String, String> config) throws Exception {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public ExporterStatus getExporterStatus(String exporterName) throws Exception {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Map<String, String> getExporterConfig(String exporterName) throws Exception {
        Errors.operationNotSupported();
        return null;
    }
}
