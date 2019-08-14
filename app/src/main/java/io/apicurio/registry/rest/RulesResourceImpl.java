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

import io.apicurio.registry.rest.beans.Rule;

/**
 * Implementation of the @RulesResource JAX-RS interface.
 * @author eric.wittmann@gmail.com
 */
public class RulesResourceImpl implements RulesResource {

    /**
     * @see io.apicurio.registry.rest.RulesResource#listGlobalRules()
     */
    @Override
    public List<String> listGlobalRules() {
        // TODO provide a real implementation of this.
        List<String> dummyData = new ArrayList<String>();
        
        dummyData.add("Rule1");
        
        return dummyData;
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#createGlobalRule(io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public void createGlobalRule(Rule data) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#deleteAllGlobalRules()
     */
    @Override
    public void deleteAllGlobalRules() {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#getGlobalRuleConfig(java.lang.String)
     */
    @Override
    public Rule getGlobalRuleConfig(String rule) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#updateGlobalRuleConfig(java.lang.String, io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public Rule updateGlobalRuleConfig(String rule, Rule data) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @see io.apicurio.registry.rest.RulesResource#deleteGlobalRule(java.lang.String)
     */
    @Override
    public void deleteGlobalRule(String rule) {
        // TODO Auto-generated method stub
        
    }

}
