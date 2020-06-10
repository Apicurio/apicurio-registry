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

/**
 * This interface is used to execute/apply a specific rule.  Each rule supported by
 * the registry will have an implementation of this interface, where the logic specific
 * to the rule is applied.  For example, the Validity rule will have an implementation.
 * @author eric.wittmann@gmail.com
 */
public interface RuleExecutor {
    
    /**
     * Executes the logic of the rule against the given context.  The context
     * contains all data and meta-data necessary to execute the rule logic.
     * @param context
     * @throws RuleViolationException
     */
    public void execute(RuleContext context) throws RuleViolationException;

}
