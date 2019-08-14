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

package io.apicurio.registry.rest;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleConfigurationDto;

/**
 * Implementation of the @RulesResource JAX-RS interface.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class RulesResourceImpl implements RulesResource {

    @Inject
    RegistryStorage storage;
    
    /**
     * @see io.apicurio.registry.rest.RulesResource#listGlobalRules()
     */
    @Override
    public List<String> listGlobalRules() {
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
        storage.createGlobalRule(data.getName(), configDto);
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#deleteAllGlobalRules()
     */
    @Override
    public void deleteAllGlobalRules() {
        storage.deleteGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#getGlobalRuleConfig(java.lang.String)
     */
    @Override
    public Rule getGlobalRuleConfig(String ruleName) {
        RuleConfigurationDto dto = storage.getGlobalRule(ruleName);
        Rule rule = new Rule();
        rule.setName(ruleName);
        rule.setConfig(dto.getConfiguration());
        return rule;
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#updateGlobalRuleConfig(java.lang.String, io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public Rule updateGlobalRuleConfig(String ruleName, Rule data) {
        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        storage.updateGlobalRule(ruleName, configDto);
        Rule rule = new Rule();
        rule.setName(ruleName);
        rule.setConfig(data.getConfig());
        return rule;
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#deleteGlobalRule(java.lang.String)
     */
    @Override
    public void deleteGlobalRule(String ruleName) {
        storage.deleteGlobalRule(ruleName);
    }

}
