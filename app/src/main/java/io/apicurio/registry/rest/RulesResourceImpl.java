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

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;

/**
 * Implementation of the @RulesResource JAX-RS interface.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class RulesResourceImpl implements RulesResource {

    @Inject
    RegistryStorage storage;
    @Inject
    ErrorFactory errorFactory;

    /**
     * @see io.apicurio.registry.rest.RulesResource#listGlobalRules()
     */
    @Override
    public List<Rule> listGlobalRules() {
        try {
            List<String> names = storage.getGlobalRules();
            List<Rule> rules = new ArrayList<>();
            for (String ruleName : names) {
                try {
                    RuleConfigurationDto ruleDto = storage.getGlobalRule(ruleName);
                    Rule rule = new Rule();
                    rule.setName(ruleName);
                    // TODO set the configuration data on the rule
                } catch (RuleNotFoundException rnfe) {
                    // Race condition most likely.  Can be ignored.
                    // TODO log this error
                }
            }
            return rules;
        } catch (RegistryStorageException e) {
            throw this.errorFactory.create(e);
        }
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#createGlobalRule(io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public void createGlobalRule(Rule data) {
        try {
            RuleConfigurationDto configDto = new RuleConfigurationDto();
            // TODO copy data from the jax-rs entity into the dto
            storage.createGlobalRule(data.getName(), configDto);
        } catch (RuleAlreadyExistsException e) {
            throw this.errorFactory.create(e);
        } catch (RegistryStorageException e) {
            throw this.errorFactory.create(e);
        }
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#deleteAllGlobalRules()
     */
    @Override
    public void deleteAllGlobalRules() {
        try {
            storage.deleteGlobalRules();
        } catch (RegistryStorageException e) {
            throw this.errorFactory.create(e);
        }
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#getGlobalRuleConfig(java.lang.String)
     */
    @Override
    public Rule getGlobalRuleConfig(String ruleName) {
        try {
            RuleConfigurationDto dto = storage.getGlobalRule(ruleName);
            Rule rule = new Rule();
            rule.setName(ruleName);
            // TODO also set the rule's config data
            return rule;
        } catch (RuleNotFoundException e) {
            throw this.errorFactory.create(e);
        } catch (RegistryStorageException e) {
            throw this.errorFactory.create(e);
        }
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#updateGlobalRuleConfig(java.lang.String, io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public Rule updateGlobalRuleConfig(String ruleName, Rule data) {
        try {
            RuleConfigurationDto configDto = new RuleConfigurationDto();
            // TODO set the inbound config info on the dto
            storage.updateGlobalRule(ruleName, configDto);
            Rule rule = new Rule();
            rule.setName(ruleName);
            rule.setConfig(data.getConfig());
            return rule;
        } catch (RuleNotFoundException e) {
            throw this.errorFactory.create(e);
        } catch (RegistryStorageException e) {
            throw this.errorFactory.create(e);
        }
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#deleteGlobalRule(java.lang.String)
     */
    @Override
    public void deleteGlobalRule(String ruleName) {
        try {
            storage.deleteGlobalRule(ruleName);
        } catch (RuleNotFoundException e) {
            throw this.errorFactory.create(e);
        } catch (RegistryStorageException e) {
            throw this.errorFactory.create(e);
        }
    }

}
