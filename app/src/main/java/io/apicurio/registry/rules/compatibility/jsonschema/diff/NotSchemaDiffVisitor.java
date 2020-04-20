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

package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NotSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import org.everit.json.schema.NotSchema;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NOT_TYPE_SCHEMA_NOT_BACKWARD_COMPATIBLE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NOT_TYPE_SCHEMA_NOT_FORWARD_COMPATIBLE;
import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class NotSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private DiffContext ctx;
    private final NotSchema original;

    public NotSchemaDiffVisitor(DiffContext ctx, NotSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    public void visitNotSchema(NotSchemaWrapper notSchema) {
        super.visitNotSchema(notSchema);
    }

    public void visitSchemaMustNotMatch(SchemaWrapper mustNotMatch) {
        // We must not only make sure this schema is compatible one way,
        // but both ways, because of the negation.
        // *Updated* may match all of those which *original* matches,
        // but *updated* may then match some that *original* does not.
        // Therefore some schema may be rejected that previously was not.
        // TODO Put this into top level 'library' methods
        DiffContext rootCtx = DiffContext.createRootContext();
        new SchemaDiffVisitor(rootCtx, original)
            .visit(mustNotMatch);
        boolean forward = rootCtx.foundAllDifferencesAreCompatible();

        rootCtx = DiffContext.createRootContext();
        new SchemaDiffVisitor(rootCtx, mustNotMatch.getWrapped())
            .visit(wrap(original));
        boolean backward = rootCtx.foundAllDifferencesAreCompatible();

        DiffContext subCtx = ctx.sub("not");
        if (!forward) {
            subCtx.addDifference(NOT_TYPE_SCHEMA_NOT_FORWARD_COMPATIBLE, original, mustNotMatch);
        }
        if (!backward) {
            subCtx.addDifference(NOT_TYPE_SCHEMA_NOT_BACKWARD_COMPATIBLE, original, mustNotMatch);
        }

        super.visitSchemaMustNotMatch(mustNotMatch);
    }
}
