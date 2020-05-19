/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.ccompat.dto.CompatibilityLevelDto;
import io.apicurio.registry.ccompat.rest.ConfigResource;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.metrics.RestMetricsApply;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.types.RuleType;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.interceptor.Interceptors;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT})
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT})
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS)
@Logged
public class ConfigResourceImpl extends AbstractResource implements ConfigResource {


    private CompatibilityLevelDto getCompatibilityLevel(Supplier<String> supplyLevel) {
        try {
            // We're assuming the configuration == compatibility level
            // TODO make it more explicit
            return CompatibilityLevelDto.create(Optional.of(
                    CompatibilityLevel.valueOf(
                            supplyLevel.get()
                    ))
            );
        } catch (RuleNotFoundException ex) {
            return CompatibilityLevelDto.create(Optional.empty());
        }
    }


    private void updateCompatibilityLevel(CompatibilityLevelDto.Level level,
                                          Consumer<RuleConfigurationDto> updater,
                                          Runnable deleter) {
        if (level == CompatibilityLevelDto.Level.NONE) {
            // delete the rule
            deleter.run();
        } else {
            String levelString = level.getStringValue();
            try {
                CompatibilityLevel.valueOf(levelString);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Illegal compatibility level: " + levelString);
            }
            updater.accept(RuleConfigurationDto.builder()
                    .configuration(levelString).build()); // TODO config should take CompatibilityLevel as param
        }
    }


    @Override
    public CompatibilityLevelDto getGlobalCompatibilityLevel() {
        return getCompatibilityLevel(() ->
                facade.getGlobalRule(RuleType.COMPATIBILITY).getConfiguration());
    }


    @Override
    public CompatibilityLevelDto updateGlobalCompatibilityLevel(
            CompatibilityLevelDto request) {

        updateCompatibilityLevel(request.getCompatibilityLevel(),
                dto -> facade.createOrUpdateGlobalRule(RuleType.COMPATIBILITY, dto),
                () -> facade.deleteGlobalRule(RuleType.COMPATIBILITY));
        return request;
    }


    @Override
    public CompatibilityLevelDto updateSubjectCompatibilityLevel(
            String subject,
            CompatibilityLevelDto request) {
        updateCompatibilityLevel(request.getCompatibilityLevel(),
                dto -> facade.createOrUpdateArtifactRule(subject, RuleType.COMPATIBILITY, dto),
                () -> facade.deleteArtifactRule(subject, RuleType.COMPATIBILITY));
        return request;
    }

    @Override
    public CompatibilityLevelDto getSubjectCompatibilityLevel(String subject) {
        return getCompatibilityLevel(() ->
                facade.getArtifactRule(subject, RuleType.COMPATIBILITY).getConfiguration());
    }
}
