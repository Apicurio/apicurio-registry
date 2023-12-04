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


public class RuleNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -5024749463194169679L;


    @Getter
    private final RuleType rule;


    public RuleNotFoundException(RuleType rule) {
        super(message(rule));
        this.rule = rule;
    }

    public RuleNotFoundException(RuleType rule, Throwable cause) {
        super(message(rule), cause);
        this.rule = rule;
    }


    private static String message(RuleType rule) {
        return "No rule named '" + rule.name() + "' was found.";
    }

}
