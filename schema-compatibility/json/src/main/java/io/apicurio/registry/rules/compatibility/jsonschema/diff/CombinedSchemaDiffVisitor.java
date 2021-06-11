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

package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.CombinedSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.Schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.COMBINED_TYPE_ALL_OF_SIZE_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.COMBINED_TYPE_ALL_OF_SIZE_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.COMBINED_TYPE_ANY_OF_SIZE_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.COMBINED_TYPE_ANY_OF_SIZE_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.COMBINED_TYPE_CRITERION_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.COMBINED_TYPE_CRITERION_EXTENDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.COMBINED_TYPE_CRITERION_NARROWED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.COMBINED_TYPE_ONE_OF_SIZE_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.COMBINED_TYPE_ONE_OF_SIZE_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.COMBINED_TYPE_SUBSCHEMA_NOT_COMPATIBLE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.UNDEFINED_UNUSED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffInteger;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffObjectIdentity;
import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.equalityWrap;
import static java.util.Comparator.comparingInt;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class CombinedSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final CombinedSchema original;

    public CombinedSchemaDiffVisitor(DiffContext ctx, CombinedSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitCombinedSchema(CombinedSchemaWrapper schema) {
        // Check if the criterion has changed
        if (diffObjectIdentity(ctx.sub("[criterion]"), original.getCriterion(), schema.getCriterion(),
            CombinedSchema.ANY_CRITERION, UNDEFINED_UNUSED, UNDEFINED_UNUSED, COMBINED_TYPE_CRITERION_EXTENDED,
            COMBINED_TYPE_CRITERION_NARROWED, COMBINED_TYPE_CRITERION_CHANGED)) {
            // prevent further analysis if it did
            super.visitCombinedSchema(schema);
        }
    }

    @Override
    public void visitOneOfCombinedSchema(CombinedSchemaWrapper schema) {
        processSubschemas(schema, COMBINED_TYPE_ONE_OF_SIZE_INCREASED, COMBINED_TYPE_ONE_OF_SIZE_DECREASED);
        super.visitOneOfCombinedSchema(schema);
    }

    @Override
    public void visitAnyOfCombinedSchema(CombinedSchemaWrapper schema) {
        processSubschemas(schema, COMBINED_TYPE_ANY_OF_SIZE_INCREASED, COMBINED_TYPE_ANY_OF_SIZE_DECREASED);
        super.visitAnyOfCombinedSchema(schema);
    }

    @Override
    public void visitAllOfCombinedSchema(CombinedSchemaWrapper schema) {
        processSubschemas(schema, COMBINED_TYPE_ALL_OF_SIZE_INCREASED, COMBINED_TYPE_ALL_OF_SIZE_DECREASED);
        super.visitAllOfCombinedSchema(schema);
    }

    private void processSubschemas(CombinedSchemaWrapper schema, DiffType sizeIncreased, DiffType sizeDecreased) {
        List<Schema> originalSubschemas = new ArrayList<>(original.getSubschemas());
        List<SchemaWrapper> updatedSubschemas = new LinkedList<>(schema.getSubschemas()); // better for insert/remove

        Map<SchemaWrapper, Set<SchemaWrapper>> compatibilityMap = new HashMap<>();

        diffInteger(ctx.sub("[size]"), originalSubschemas.size(), updatedSubschemas.size(),
            UNDEFINED_UNUSED,
            UNDEFINED_UNUSED,
            sizeIncreased,
            sizeDecreased);
        if (originalSubschemas.size() <= updatedSubschemas.size()) {
            // try to match them
            for (Schema o : originalSubschemas) {
                DiffContext rootCtx = null;

                if (!compatibilityMap.containsKey(equalityWrap(o)))
                    compatibilityMap.put(equalityWrap(o), new HashSet<>());

                for (SchemaWrapper u : updatedSubschemas) {
                    // create a new subschema root context
                    rootCtx = DiffContext.createRootContext("", ctx.visited);
                    new SchemaDiffVisitor(rootCtx, o).visit(u);
                    if (rootCtx.foundAllDifferencesAreCompatible()) {
                        compatibilityMap.get(equalityWrap(o)).add(u);
                    }
                }
            }

            Optional<Map.Entry<SchemaWrapper, Set<SchemaWrapper>>> first = compatibilityMap.entrySet().stream()
                .min(comparingInt(a -> a.getValue().size()));
            while (first.isPresent()) {
                // remove a value from the first set
                Optional<SchemaWrapper> val = first.get().getValue().stream().findAny();
                if (val.isPresent()) {
                    // ok
                    // remove it from all sets
                    compatibilityMap.values().forEach(s -> s.remove(val.get()));
                } else {
                    // bad
                    ctx.addDifference(COMBINED_TYPE_SUBSCHEMA_NOT_COMPATIBLE, first.get().getKey(), null);
                }
                if (first.get().getValue().isEmpty())
                    compatibilityMap.remove(first.get().getKey());

                first = compatibilityMap.entrySet().stream()
                    .min(comparingInt(a -> a.getValue().size()));
            }
        }
    }
}
