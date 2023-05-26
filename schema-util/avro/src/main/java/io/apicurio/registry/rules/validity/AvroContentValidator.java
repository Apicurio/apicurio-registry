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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.RuleType;

/**
 * A content validator implementation for the Avro content type.
 * @author eric.wittmann@gmail.com
 */
public class AvroContentValidator implements ContentValidator {
    
    private static final String DUMMY_AVRO_RECORD = "{\n"
            + "     \"type\": \"record\",\n"
            + "     \"namespace\": \"NAMESPACE\",\n"
            + "     \"name\": \"NAME\",\n"
            + "     \"fields\": [\n"
            + "       { \"name\": \"first\", \"type\": \"string\" },\n"
            + "       { \"name\": \"last\", \"type\": \"string\" }\n"
            + "     ]\n"
            + "}";

    /**
     * Constructor.
     */
    public AvroContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, ContentHandle, Map)
     */
    @Override
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                Schema.Parser parser = new Schema.Parser();
                for (ContentHandle schema : resolvedReferences.values()) {
                    parser.parse(schema.content());
                }
                parser.parse(artifactContent.content());
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Avro artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }
    
    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(io.apicurio.registry.content.ContentHandle, java.util.List)
     */
    @Override
    public void validateReferences(ContentHandle artifactContent, List<ArtifactReference> references) throws RuleViolationException {
        try {
            Schema.Parser parser = new Schema.Parser();
            references.forEach(ref -> {
                String refName = ref.getName();
                if (refName != null && refName.contains(".")) {
                    int idx = refName.lastIndexOf('.');
                    String ns = refName.substring(0, idx);
                    String name = refName.substring(idx+1);
                    parser.parse(DUMMY_AVRO_RECORD.replace("NAMESPACE", ns).replace("NAME", name));
                }
            });
            parser.parse(artifactContent.content());
        } catch (Exception e) {
            // This is terrible, but I don't know how else to detect if the reason for the parse failure
            // is because of a missing defined type or some OTHER parse exception.
            if (e.getMessage().contains("is not a defined name")) {
                RuleViolation violation = new RuleViolation("Missing reference detected.", e.getMessage());
                throw new RuleViolationException("Missing reference detected in Avro artifact.", RuleType.INTEGRITY, 
                        IntegrityLevel.ALL_REFS_MAPPED.name(), Collections.singleton(violation));
            }
        }
    }

}
