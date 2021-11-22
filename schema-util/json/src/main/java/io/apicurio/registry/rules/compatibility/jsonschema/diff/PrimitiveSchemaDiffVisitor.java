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
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.EmptySchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.FalseSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.TrueSchemaWrapper;
import org.everit.json.schema.EmptySchema;
import org.everit.json.schema.FalseSchema;
import org.everit.json.schema.Schema;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.SUBSCHEMA_TYPE_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.SUBSCHEMA_TYPE_CHANGED_TO_EMPTY_OR_TRUE;

/**
 * This visitor deals with the following schemas:
 *
 * <pre>
 * {
 *   "type": "object",
 *   "properties": {
 *     "isEmpty": {} // EmptySchema - matches everything
 *     "isTrue": true, // TrueSchema - matches everything
 *     "isFalse": false, // FalseSchema - matches nothing
 *     "isNull": null // NOT VALID
 *   }
 * }
 * </pre>
 * <p>
 * "True" and "Empty" schemas are equivalent, each are not equivalent with "False" schema.
 *
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class PrimitiveSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final Schema original;

    /**
     * This visitor accepts any schema, so the checks
     * that would be otherwise done by the caller,
     * are made by this visitor.
     */
    public PrimitiveSchemaDiffVisitor(DiffContext ctx, Schema original) {
        this.ctx = ctx;
        this.original = original;
    }

    private void emptyTrueSchema(SchemaWrapper wrapper) {
        // This is spelled explicitly for clarity, and in case the library changes.
        if (!(EmptySchema.INSTANCE.equals(original) // ||
            // TrueSchema.INSTANCE.equals(original)
        )) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED_TO_EMPTY_OR_TRUE, original, wrapper);
            // Change to empty schema is backwards compatible
        }
    }

    @Override
    public void visitEmptySchema(EmptySchemaWrapper emptySchema) {
        emptyTrueSchema(emptySchema);
        super.visitEmptySchema(emptySchema);
    }

    @Override
    public void visitTrueSchema(TrueSchemaWrapper trueSchema) {
        emptyTrueSchema(trueSchema);
        super.visitTrueSchema(trueSchema);
    }

    @Override
    public void visitFalseSchema(FalseSchemaWrapper falseSchema) {
        if (!FalseSchema.INSTANCE.equals(original)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, falseSchema);
        }
        super.visitFalseSchema(falseSchema);
    }
}
