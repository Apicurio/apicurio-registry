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

import io.apicurio.registry.ccompat.dto.CompatibilityCheckResponse;
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.rest.CompatibilityResource;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.metrics.RestMetricsApply;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.enterprise.context.ApplicationScoped;
import javax.interceptor.Interceptors;

import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */

@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT})
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT})
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS)
@Logged
public class CompatibilityResourceImpl extends AbstractResource implements CompatibilityResource {

    @Override
    public CompatibilityCheckResponse testCompatibilityBySubjectName(
            String subject,
            String versionString,
            SchemaContent request) throws Exception {

        return facade.testCompatibilityBySubjectName(subject, versionString, request);
    }
}
