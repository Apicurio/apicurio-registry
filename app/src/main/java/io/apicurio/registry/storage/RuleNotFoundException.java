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

package io.apicurio.registry.storage;

/**
 * @author eric.wittmann@gmail.com
 */
public class RuleNotFoundException extends StorageException {

    private static final long serialVersionUID = -5024749463194169679L;
    
    private final String ruleName;

    /**
     * Constructor.
     */
    public RuleNotFoundException(String ruleName) {
        this.ruleName = ruleName;
    }

    /**
     * @return the ruleName
     */
    public String getRuleName() {
        return ruleName;
    }

    /**
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "No rule named '" + this.ruleName + "' was found.";
    }

}
