/*
 * Copyright 2022 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.ccompat.dto.ExporterDto;
import io.apicurio.registry.ccompat.dto.ExporterStatus;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.rest.v7.ExporterResource;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;

import javax.interceptor.Interceptors;
import java.util.List;
import java.util.Map;

/**
 * @author Carles Arnal
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
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
