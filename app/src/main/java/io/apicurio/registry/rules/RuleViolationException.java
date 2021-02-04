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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.RuleType;
import lombok.Getter;

/**
 * Exception thrown when a configured rule is violated, rejecting an artifact content
 * update.
 * @author Ales Justin
 */
public class RuleViolationException extends RegistryException {

    private static final long serialVersionUID = 8437151164241883773L;

    @Getter
    private final RuleType ruleType;

    @Getter
    private final Optional<String> ruleConfiguration;

    @Getter
    private final Set<RuleViolation> causes;

    /**
     * Constructor.
     * @param message
     * @param ruleType
     * @param ruleConfiguration
     * @param cause
     */
    public RuleViolationException(String message, RuleType ruleType, String ruleConfiguration, Throwable cause) {
        super(message, cause);
        this.ruleType = ruleType;
        this.ruleConfiguration = Optional.ofNullable(ruleConfiguration);
        this.causes = new HashSet<>();
    }

    /**
     * Constructor.
     * @param message
     * @param ruleType
     * @param ruleConfiguration
     * @param causes
     */
    public RuleViolationException(String message, RuleType ruleType, String ruleConfiguration, Set<RuleViolation> causes) {
        super(message);
        this.ruleType = ruleType;
        this.ruleConfiguration = Optional.ofNullable(ruleConfiguration);
        this.causes = causes;
    }

    /**
     * Constructor.
     * @param message
     * @param ruleType
     * @param ruleConfiguration
     * @param causes
     * @param cause
     */
    public RuleViolationException(String message, RuleType ruleType, String ruleConfiguration,
            Set<RuleViolation> causes, Throwable cause) {
        super(message, cause);
        this.ruleType = ruleType;
        this.ruleConfiguration = Optional.ofNullable(ruleConfiguration);
        this.causes = causes;
    }
}
