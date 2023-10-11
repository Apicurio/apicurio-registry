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

package io.apicurio.registry.rules;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.apicurio.registry.rules.compatibility.CompatibilityRuleExecutor;
import io.apicurio.registry.rules.integrity.IntegrityRuleExecutor;
import io.apicurio.registry.rules.validity.ValidityRuleExecutor;
import io.apicurio.registry.types.RuleType;

/**
 * Creates a rule executor from a {@link RuleType}.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class RuleExecutorFactory {
    
    @Inject
    CompatibilityRuleExecutor compatibility;
    @Inject
    ValidityRuleExecutor validity;
    @Inject
    IntegrityRuleExecutor integrity;

    public RuleExecutor createExecutor(RuleType ruleType) {
        switch (ruleType) {
            case COMPATIBILITY:
                return compatibility;
            case VALIDITY:
                return validity;
            case INTEGRITY:
                return integrity;
            default:
                throw new RuntimeException("Rule type not supported");
        }
    }

}
