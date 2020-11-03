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

package io.apicurio.registry.rules.validity;

import javax.enterprise.context.ApplicationScoped;

import org.apache.avro.Schema;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;

/**
 * A content validator implementation for the Avro content type.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class AvroContentValidator implements ContentValidator {
    
    /**
     * Constructor.
     */
    public AvroContentValidator() {
    }
    
    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(io.apicurio.registry.rules.validity.ValidityLevel, ContentHandle)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                Schema.Parser parser = new Schema.Parser();
                parser.parse(artifactContent.content());
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Avro artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }

}
