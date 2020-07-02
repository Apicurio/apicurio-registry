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

package io.apicurio.registry.rest;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.metrics.RestMetricsApply;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import java.util.List;

import static io.apicurio.registry.metrics.MetricIDs.*;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * Implementation of the @RulesResource JAX-RS interface.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT})
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT})
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS)
@Logged
@Authenticated
public class RulesResourceImpl implements RulesResource {

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * @see io.apicurio.registry.rest.RulesResource#listGlobalRules()
     */
    @Override
    public List<RuleType> listGlobalRules() {
        return storage.getGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#createGlobalRule(io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public void createGlobalRule(Rule data) {
        // TODO validate the rule name (only support rules we have implemented)
        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        storage.createGlobalRule(data.getType(), configDto);
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#deleteAllGlobalRules()
     */
    @Override
    public void deleteAllGlobalRules() {
        storage.deleteGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#getGlobalRuleConfig(io.apicurio.registry.types.RuleType)
     */
    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        RuleConfigurationDto dto = storage.getGlobalRule(rule);
        Rule ruleBean = new Rule();
        ruleBean.setType(rule);
        ruleBean.setConfig(dto.getConfiguration());
        return ruleBean;
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#updateGlobalRuleConfig(io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        storage.updateGlobalRule(rule, configDto);
        Rule ruleBean = new Rule();
        ruleBean.setType(rule);
        ruleBean.setConfig(data.getConfig());
        return ruleBean;
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteGlobalRule(RuleType rule) {
        storage.deleteGlobalRule(rule);
    }

}
