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

import com.google.common.collect.ImmutableSet;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.UnprocessableSchemaException;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.Incompatibility;

import java.util.Collections;
import java.util.Map;
import java.util.Set;


public class AvroCompatibilityChecker extends AbstractCompatibilityChecker<Incompatibility> {

    @Override
    protected Set<Incompatibility> isBackwardsCompatibleWith(String existing, String proposed, Map<String, ContentHandle> resolvedReferences) {
        try {
            Schema.Parser existingParser = new Schema.Parser();
            for (ContentHandle schema : resolvedReferences.values()) {
                existingParser.parse(schema.content());
            }
            final Schema existingSchema = existingParser.parse(existing);

            Schema.Parser proposingParser = new Schema.Parser();
            for (ContentHandle schema : resolvedReferences.values()) {
                proposingParser.parse(schema.content());
            }
            final Schema proposedSchema = proposingParser.parse(proposed);

            var result = SchemaCompatibility.checkReaderWriterCompatibility(proposedSchema, existingSchema).getResult();
            switch (result.getCompatibility()) {
                case COMPATIBLE:
                    return Collections.emptySet();
                case INCOMPATIBLE: {
                    return ImmutableSet.<Incompatibility>builder().addAll(result.getIncompatibilities()).build();
                }
                default:
                    throw new IllegalStateException("Got illegal compatibility result: " + result.getCompatibility());
            }
        } catch (AvroRuntimeException ex) {
            throw new UnprocessableSchemaException("Could not execute compatibility rule on invalid Avro schema", ex);
        }
    }

    @Override
    protected CompatibilityDifference transform(Incompatibility original) {
        return new SimpleCompatibilityDifference(original.getMessage(), original.getLocation());
    }
}
