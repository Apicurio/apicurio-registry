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

package io.apicurio.registry.rest.v1;

import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;

import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.metrics.RestMetricsApply;
import io.apicurio.registry.metrics.RestMetricsResponseFilteredNameBinding;
import io.apicurio.registry.rest.v1.beans.Rule;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.RulesProperties;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;

/**
 * Implementation of the @RulesResource JAX-RS interface.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@RestMetricsResponseFilteredNameBinding
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT})
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT})
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS)
@Logged
@Deprecated
public class RulesResourceImpl implements RulesResource {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesProperties rulesProperties;

    /**
     * @see io.apicurio.registry.rest.v1.RulesResource#listGlobalRules()
     */
    @Override
    public List<RuleType> listGlobalRules() {
        List<RuleType> rules = storage.getGlobalRules();
        List<RuleType> defaultRules = rulesProperties.getFilteredDefaultGlobalRules(rules);
        return Stream.concat(rules.stream(), defaultRules.stream())
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v1.RulesResource#createGlobalRule(io.apicurio.registry.rest.v1.v1.beans.Rule)
     */
    @Override
    public void createGlobalRule(Rule data) {
        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        storage.createGlobalRule(data.getType(), configDto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.RulesResource#deleteAllGlobalRules()
     */
    @Override
    public void deleteAllGlobalRules() {
        storage.deleteGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.v1.RulesResource#getGlobalRuleConfig(io.apicurio.registry.types.RuleType)
     */
    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        RuleConfigurationDto dto;
        try {
            dto = storage.getGlobalRule(rule);
        } catch (RuleNotFoundException ruleNotFoundException) {
            // Check if the rule exists in the default global rules
            dto = rulesProperties.getDefaultGlobalRuleConfiguration(rule);
            if (dto == null) {
                throw ruleNotFoundException;
            }
        }
        Rule ruleBean = new Rule();
        ruleBean.setType(rule);
        ruleBean.setConfig(dto.getConfiguration());
        return ruleBean;
    }

    /**
     * @see io.apicurio.registry.rest.v1.RulesResource#updateGlobalRuleConfig(io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.v1.v1.beans.Rule)
     */
    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        try {
            storage.updateGlobalRule(rule, configDto);
        } catch (RuleNotFoundException ruleNotFoundException) {
            // This global rule doesn't exist in artifactStore - if the rule exists in the default
            // global rules, override the default by creating a new global rule
            if (rulesProperties.isDefaultGlobalRuleConfigured(rule)) {
                storage.createGlobalRule(rule, configDto);
            } else {
                throw ruleNotFoundException;
            }
        }
        Rule ruleBean = new Rule();
        ruleBean.setType(rule);
        ruleBean.setConfig(data.getConfig());
        return ruleBean;
    }

    /**
     * @see io.apicurio.registry.rest.v1.RulesResource#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteGlobalRule(RuleType rule) {
        try {
            storage.deleteGlobalRule(rule);
        } catch (RuleNotFoundException ruleNotFoundException) {
            // This global rule doesn't exist in artifactStore - if the rule exists in
            // the default global rules, return a DefaultRuleDeletionException.
            // Otherwise, return the RuleNotFoundException
            if (rulesProperties.isDefaultGlobalRuleConfigured(rule)) {
                throw new DefaultRuleDeletionException(rule);
            } else {
                throw ruleNotFoundException;
            }
        }
    }

}
