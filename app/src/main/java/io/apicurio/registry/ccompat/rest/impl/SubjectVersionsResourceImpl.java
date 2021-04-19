/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.ccompat.rest.impl;

import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.dto.SchemaId;
import io.apicurio.registry.ccompat.rest.SubjectVersionsResource;
import io.apicurio.registry.ccompat.store.FacadeConverter;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.metrics.RestMetricsApply;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import java.util.List;
import javax.interceptor.Interceptors;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.container.AsyncResponse;

import static io.apicurio.registry.metrics.MetricIDs.*;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT})
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT})
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS)
@Logged
public class SubjectVersionsResourceImpl extends AbstractResource implements SubjectVersionsResource {


    @Override
    public List<Integer> listVersions(String subject) throws Exception {
        return facade.getVersions(subject);
    }

    @Override
    public void register(String subject, SchemaInfo request, AsyncResponse response) throws Exception {
        facade.createSchema(subject, request.getSchema(), request.getSchemaType())
                .thenApply(FacadeConverter::convertUnsigned)
                .whenComplete((id, t) -> {
                    if (t != null) {
                        response.resume(t);
                    } else {
                        response.resume(new SchemaId(id));
                    }
                });
    }

    @Override
    public Schema getSchemaByVersion(
            String subject,
            String version) throws Exception {

        return facade.getSchema(subject, version);
    }

    @Override
    public int deleteSchemaVersion(
            String subject,
            String version) throws Exception {

        try {
            return facade.deleteSchema(subject, version);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex); // TODO
        }
    }

    @Override
    public String getSchemaOnly(
            String subject,
            String version) throws Exception {

        return facade.getSchema(subject, version).getSchema();
    }

    @Override
    public List<Integer> getSchemasReferencedBy(String subject, Integer version) throws Exception {
        return facade.getVersions(subject);
    }
}
