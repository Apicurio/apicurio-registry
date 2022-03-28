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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v1.beans.Rule;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.RulesProperties;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE_TYPE;

/**
 * Implementation of the @RulesResource JAX-RS interface.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
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
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public List<RuleType> listGlobalRules() {
        List<RuleType> rules = storage.getGlobalRules();
        List<RuleType> defaultRules = rulesProperties.getFilteredDefaultGlobalRules(rules);
        return Stream.concat(rules.stream(), defaultRules.stream())
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v1.RulesResource#createGlobalRule (io.apicurio.registry.rest.v1.v1.beans.Rule)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_RULE})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public void createGlobalRule(Rule data) {
        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        storage.createGlobalRule(data.getType(), configDto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.RulesResource#deleteAllGlobalRules()
     */
    @Override
    @Audited
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public void deleteAllGlobalRules() {
        storage.deleteGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.v1.RulesResource#getGlobalRuleConfig(io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
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
     * @see io.apicurio.registry.rest.v1.RulesResource#updateGlobalRuleConfig (io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.v1.v1.beans.Rule)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_RULE_TYPE, "1", KEY_RULE})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
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
    @Audited(extractParameters = {"0", KEY_RULE_TYPE})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
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
