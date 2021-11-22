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

package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.RuleViolation;

/**
 * @author eric.wittmann@gmail.com
 */
public class GenericCompatibilityDifference implements CompatibilityDifference {

    private final String message;

    /**
     * Constructor.
     * @param e
     */
    public GenericCompatibilityDifference(Exception e) {
        this.message = e.getMessage();
    }

    /**
     * Constructor.
     * @param message
     */
    public GenericCompatibilityDifference(String message) {
        this.message = message;
    }

    /**
     * @see io.apicurio.registry.rules.compatibility.CompatibilityDifference#asRuleViolation()
     */
    @Override
    public RuleViolation asRuleViolation() {
        RuleViolation violation = new RuleViolation();
        violation.setDescription(message);
        violation.setContext("/");
        return violation;
    }

}
