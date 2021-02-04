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
 * Represents a single compatibility difference.  These are generated when doing compatibility checking
 * between two versions of an artifact.  A non-zero collection of these indicates a compatibility violation.
 *
 * @author eric.wittmann@gmail.com
 */
public interface CompatibilityDifference {

    /**
     * Converts the difference into a rule violation cause.
     */
    public RuleViolation asRuleViolation();

}
