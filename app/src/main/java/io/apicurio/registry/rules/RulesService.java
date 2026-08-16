/*
 * Copyright 2026 Red Hat
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

import io.apicurio.registry.rules.violation.RuleViolationException;

/**
 * A service used to apply configured rules to a given content update. In other words, when artifact content
 * is being created or updated, this service is used to apply any rules configured for the artifact.
 */
public interface RulesService {

    /**
     * Primary entry point to apply all configured rules using a {@link RuleApplicationContext}.
     *
     * @param context the rule application context
     * @throws RuleViolationException if a rule violation occurs
     */
    void applyRules(RuleApplicationContext context) throws RuleViolationException;

    /**
     * Primary entry point to apply a single specific rule using a {@link RuleApplicationContext}.
     *
     * @param context the rule application context (must specify {@code ruleType})
     * @throws RuleViolationException if a rule violation occurs
     */
    void applyRule(RuleApplicationContext context) throws RuleViolationException;
}
