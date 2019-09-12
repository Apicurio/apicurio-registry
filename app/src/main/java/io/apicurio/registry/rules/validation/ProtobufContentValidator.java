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

package io.apicurio.registry.rules.validation;

import javax.enterprise.context.ApplicationScoped;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.ProtoParser;

/**
 * A content validator implementation for the Protobuf content type.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class ProtobufContentValidator implements ContentValidator {
    
    /**
     * Constructor.
     */
    public ProtobufContentValidator() {
    }
    
    /**
     * @see io.apicurio.registry.rules.validation.ContentValidator#validate(io.apicurio.registry.rules.validation.ValidationLevel, java.lang.String)
     */
    @Override
    public void validate(ValidationLevel level, String artifactContent) throws InvalidContentException {
        if (level == ValidationLevel.SYNTAX_ONLY || level == ValidationLevel.FULL) {
            try {
                ProtoParser.parse(Location.get(""), artifactContent);
            } catch (Exception e) {
                throw new InvalidContentException("Syntax violation for Protobuf artifact.", e);
            }
        }
    }

}
