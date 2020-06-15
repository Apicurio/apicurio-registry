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

package io.apicurio.registry.storage;

import io.apicurio.registry.types.RuleType;

/**
 * @author eric.wittmann@gmail.com
 */
public class RuleNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -5024749463194169679L;
    
    private final RuleType rule;

    public RuleNotFoundException(RuleType rule) {
        this.rule = rule;
    }

    public RuleNotFoundException(RuleType rule, Throwable cause) {
        super(cause);
        this.rule = rule;
    }

    /**
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "No rule named '" + this.rule.name() + "' was found.";
    }

}
