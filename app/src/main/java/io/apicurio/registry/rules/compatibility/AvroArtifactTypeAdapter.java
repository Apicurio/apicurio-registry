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

package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.avro.Schema;
import org.apache.avro.SchemaValidationException;
import org.apache.avro.SchemaValidator;
import org.apache.avro.SchemaValidatorBuilder;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 */
public class AvroArtifactTypeAdapter implements ArtifactTypeAdapter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final Comparator<JsonNode> fieldComparator = (n1, n2) -> {
        String name1 = n1.get("name").textValue();
        String name2 = n2.get("name").textValue();
        return name1.compareTo(name2);
    };

    @Override
    public String toCanonical(String schema) {
        try {
            JsonNode root = mapper.readTree(schema);

            // reorder "fields" property
            JsonNode fieldsNode = root.get("fields");
            if (fieldsNode != null) {
                Set<JsonNode> fields = new TreeSet<>(fieldComparator);
                Iterator<JsonNode> elements = fieldsNode.elements();
                while (elements.hasNext()) {
                    fields.add(elements.next());
                }
                ArrayNode array = new ArrayNode(mapper.getNodeFactory());
                fields.forEach(array::add);
                ObjectNode.class.cast(root).replace("fields", array);
            }

            return mapper.writeValueAsString(mapper.treeToValue(root, Object.class));
        } catch (Throwable t) {
            // best effort ?
            return new Schema.Parser().parse(schema).toString();
        }
    }

    /**
     * @see io.apicurio.registry.rules.compatibility.ArtifactTypeAdapter#isCompatibleWith(io.apicurio.registry.rules.compatibility.CompatibilityLevel, java.util.List, java.lang.String)
     */
    @Override
    public boolean isCompatibleWith(CompatibilityLevel compatibilityLevel, List<String> existingSchemaStrings, String proposedSchemaString) {
        SchemaValidator schemaValidator = validatorFor(compatibilityLevel);

        if (schemaValidator == null) {
            return true;
        }

        List<Schema> existingSchemas = existingSchemaStrings.stream().map(s -> new Schema.Parser().parse(s)).collect(Collectors.toList());
        Collections.reverse(existingSchemas); // the most recent must come first, i.e. reverse-chronological.
        Schema toValidate = new Schema.Parser().parse(proposedSchemaString);

        try {
            schemaValidator.validate(toValidate, existingSchemas);
            return true;
        } catch (SchemaValidationException e) {
            return false;
        }
    }

    private SchemaValidator validatorFor(CompatibilityLevel compatibilityLevel) {
        switch (compatibilityLevel) {
            case BACKWARD:
                return new SchemaValidatorBuilder().canReadStrategy().validateLatest();
            case BACKWARD_TRANSITIVE:
                return new SchemaValidatorBuilder().canReadStrategy().validateAll();
            case FORWARD:
                return new SchemaValidatorBuilder().canBeReadStrategy().validateLatest();
            case FORWARD_TRANSITIVE:
                return new SchemaValidatorBuilder().canBeReadStrategy().validateAll();
            case FULL:
                return new SchemaValidatorBuilder().mutualReadStrategy().validateLatest();
            case FULL_TRANSITIVE:
                return new SchemaValidatorBuilder().mutualReadStrategy().validateAll();
            default:
                return null;
        }

    }
}
