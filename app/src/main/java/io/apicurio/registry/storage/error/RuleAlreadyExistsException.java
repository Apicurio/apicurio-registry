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

package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RuleType;
import lombok.Getter;

/**
 * @author eric.wittmann@gmail.com
 */
public class RuleAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = 2412206165461946827L;


    @Getter
    private final RuleType rule;


    public RuleAlreadyExistsException(RuleType rule) {
        super("A rule named '" + rule.name() + "' already exists.");
        this.rule = rule;
    }
}
