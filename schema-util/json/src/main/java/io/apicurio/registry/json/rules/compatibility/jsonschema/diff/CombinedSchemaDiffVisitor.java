package io.apicurio.registry.json.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.CombinedSchemaWrapper;
import io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
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

import static io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.WrapUtil.equalityWrap;
import static java.util.Comparator.comparingInt;

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
        if (DiffUtil.diffObjectIdentity(ctx.sub("[criterion]"), original.getCriterion(), schema.getCriterion(),
                CombinedSchema.ANY_CRITERION, DiffType.UNDEFINED_UNUSED, DiffType.UNDEFINED_UNUSED,
                DiffType.COMBINED_TYPE_CRITERION_EXTENDED, DiffType.COMBINED_TYPE_CRITERION_NARROWED,
                DiffType.COMBINED_TYPE_CRITERION_CHANGED)) {
            // prevent further analysis if it did
            super.visitCombinedSchema(schema);
        }
    }

    @Override
    public void visitOneOfCombinedSchema(CombinedSchemaWrapper schema) {
        processSubschemas(schema, DiffType.COMBINED_TYPE_ONE_OF_SIZE_INCREASED, DiffType.COMBINED_TYPE_ONE_OF_SIZE_DECREASED);
        super.visitOneOfCombinedSchema(schema);
    }

    @Override
    public void visitAnyOfCombinedSchema(CombinedSchemaWrapper schema) {
        processSubschemas(schema, DiffType.COMBINED_TYPE_ANY_OF_SIZE_INCREASED, DiffType.COMBINED_TYPE_ANY_OF_SIZE_DECREASED);
        super.visitAnyOfCombinedSchema(schema);
    }

    @Override
    public void visitAllOfCombinedSchema(CombinedSchemaWrapper schema) {
        processSubschemas(schema, DiffType.COMBINED_TYPE_ALL_OF_SIZE_INCREASED, DiffType.COMBINED_TYPE_ALL_OF_SIZE_DECREASED);
        super.visitAllOfCombinedSchema(schema);
    }

    private void processSubschemas(CombinedSchemaWrapper schema, DiffType sizeIncreased,
            DiffType sizeDecreased) {
        List<Schema> originalSubschemas = new ArrayList<>(original.getSubschemas());
        List<SchemaWrapper> updatedSubschemas = new LinkedList<>(schema.getSubschemas()); // better for
                                                                                          // insert/remove

        Map<SchemaWrapper, Set<SchemaWrapper>> compatibilityMap = new HashMap<>();

        DiffUtil.diffInteger(ctx.sub("[size]"), originalSubschemas.size(), updatedSubschemas.size(), DiffType.UNDEFINED_UNUSED,
                DiffType.UNDEFINED_UNUSED, sizeIncreased, sizeDecreased);
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

            Optional<Map.Entry<SchemaWrapper, Set<SchemaWrapper>>> first = compatibilityMap.entrySet()
                    .stream().min(comparingInt(a -> a.getValue().size()));
            while (first.isPresent()) {
                // remove a value from the first set
                Optional<SchemaWrapper> val = first.get().getValue().stream().findAny();
                if (val.isPresent()) {
                    // ok
                    // remove it from all sets
                    compatibilityMap.values().forEach(s -> s.remove(val.get()));
                } else {
                    // bad
                    ctx.addDifference(DiffType.COMBINED_TYPE_SUBSCHEMA_NOT_COMPATIBLE, first.get().getKey(), null);
                }
                if (first.get().getValue().isEmpty())
                    compatibilityMap.remove(first.get().getKey());

                first = compatibilityMap.entrySet().stream().min(comparingInt(a -> a.getValue().size()));
            }
        }
    }
}
