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

package io.apicurio.registry.rules.validity;

import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.content.ContentHandle;

import javax.enterprise.context.ApplicationScoped;

/**
 * A content validator implementation for the Protobuf FD content type.
 * @author Ales Justin
 */
@ApplicationScoped
public class ProtobufFdContentValidator implements ContentValidator {

    /**
     * Constructor.
     */
    public ProtobufFdContentValidator() {
    }

    /**
     * @see ContentValidator#validate(ValidityLevel, ContentHandle)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent) throws InvalidContentException {
        if (level == ValidityLevel.FULL) {
            try {
                Serde.Schema.parseFrom(artifactContent.bytes());
            } catch (Exception e) {
                throw new InvalidContentException("Content violation for ProtobufFD artifact.", e);
            }
        }
    }

}
