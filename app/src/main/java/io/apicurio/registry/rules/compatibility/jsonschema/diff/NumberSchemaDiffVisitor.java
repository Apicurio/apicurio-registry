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
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NumberSchemaWrapper;
import org.everit.json.schema.NumberSchema;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_EXCLUSIVE_MAXIMUM_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_EXCLUSIVE_MAXIMUM_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_EXCLUSIVE_MAXIMUM_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_EXCLUSIVE_MAXIMUM_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_EXCLUSIVE_MINIMUM_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_EXCLUSIVE_MINIMUM_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_EXCLUSIVE_MINIMUM_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_EXCLUSIVE_MINIMUM_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MAXIMUM_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MAXIMUM_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MAXIMUM_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MAXIMUM_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MINIMUM_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MINIMUM_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MINIMUM_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MINIMUM_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MULTIPLE_OF_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MULTIPLE_OF_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MULTIPLE_OF_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MULTIPLE_OF_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.UNDEFINED_UNUSED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffBoolean;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffNumber;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class NumberSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final NumberSchema original;

    public NumberSchemaDiffVisitor(DiffContext ctx, NumberSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitNumberSchema(NumberSchemaWrapper schema) {
        // There is a specific backwards compatible situation if a schema version is updated:
        // before:
        //   minimum: 10
        //   exclusiveMinimum: true
        // after:
        //   exclusiveMinimum: 10 (number added, boolean changed to false)
        // TODO Analyze these situations and decide if we want to support comparison across different drafts
        super.visitNumberSchema(schema);
    }

    @Override
    public void visitMinimum(Number minimum) {
        diffNumber(ctx.sub("minimum"), original.getMinimum(), minimum,
            NUMBER_TYPE_MINIMUM_ADDED,
            NUMBER_TYPE_MINIMUM_REMOVED,
            NUMBER_TYPE_MINIMUM_INCREASED,
            NUMBER_TYPE_MINIMUM_DECREASED);
        super.visitMinimum(minimum);
    }

    /**
     * This is for older draft, where exclusiveMinimum was a boolean
     */
    @Override
    public void visitExclusiveMinimum(boolean exclusiveMinimum) {
        diffBoolean(ctx.sub("exclusiveMinimum"), original.isExclusiveMinimum(), exclusiveMinimum,
            UNDEFINED_UNUSED,
            UNDEFINED_UNUSED,
            NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_CHANGED);
        super.visitExclusiveMinimum(exclusiveMinimum);
    }

    @Override
    public void visitExclusiveMinimumLimit(Number exclusiveMinimumLimit) {
        diffNumber(ctx.sub("exclusiveMinimum"), original.getExclusiveMinimumLimit(), exclusiveMinimumLimit,
            NUMBER_TYPE_EXCLUSIVE_MINIMUM_ADDED,
            NUMBER_TYPE_EXCLUSIVE_MINIMUM_REMOVED,
            NUMBER_TYPE_EXCLUSIVE_MINIMUM_INCREASED,
            NUMBER_TYPE_EXCLUSIVE_MINIMUM_DECREASED);
        super.visitExclusiveMinimumLimit(exclusiveMinimumLimit);
    }

    @Override
    public void visitMaximum(Number maximum) {
        diffNumber(ctx.sub("maximum"), original.getMaximum(), maximum,
            NUMBER_TYPE_MAXIMUM_ADDED,
            NUMBER_TYPE_MAXIMUM_REMOVED,
            NUMBER_TYPE_MAXIMUM_INCREASED,
            NUMBER_TYPE_MAXIMUM_DECREASED);
        super.visitMaximum(maximum);
    }

    @Override
    public void visitExclusiveMaximum(boolean exclusiveMaximum) {
        diffBoolean(ctx.sub("exclusiveMaximum"), original.isExclusiveMaximum(), exclusiveMaximum,
            UNDEFINED_UNUSED,
            UNDEFINED_UNUSED,
            NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_CHANGED);
        super.visitExclusiveMaximum(exclusiveMaximum);
    }

    @Override
    public void visitExclusiveMaximumLimit(Number exclusiveMaximumLimit) {
        diffNumber(ctx.sub("exclusiveMaximum"), original.getExclusiveMaximumLimit(), exclusiveMaximumLimit,
            NUMBER_TYPE_EXCLUSIVE_MAXIMUM_ADDED,
            NUMBER_TYPE_EXCLUSIVE_MAXIMUM_REMOVED,
            NUMBER_TYPE_EXCLUSIVE_MAXIMUM_INCREASED,
            NUMBER_TYPE_EXCLUSIVE_MAXIMUM_DECREASED);
        super.visitExclusiveMaximumLimit(exclusiveMaximumLimit);
    }

    @Override
    public void visitMultipleOf(Number multipleOf) {
        diffNumber(ctx.sub("multipleOf"), original.getMultipleOf(), multipleOf,
            NUMBER_TYPE_MULTIPLE_OF_ADDED,
            NUMBER_TYPE_MULTIPLE_OF_REMOVED,
            NUMBER_TYPE_MULTIPLE_OF_INCREASED,
            NUMBER_TYPE_MULTIPLE_OF_DECREASED);
        super.visitMultipleOf(multipleOf);
    }
}
