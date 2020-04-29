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

import java.math.BigDecimal;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_INTEGER_REQUIRED_FALSE_TO_TRUE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_INTEGER_REQUIRED_TRUE_TO_FALSE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_INTEGER_REQUIRED_UNCHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_FALSE_TO_TRUE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_TRUE_TO_FALSE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_UNCHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_FALSE_TO_TRUE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_TRUE_TO_FALSE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_UNCHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MAXIMUM_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MAXIMUM_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MAXIMUM_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MAXIMUM_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MINIMUM_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MINIMUM_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MINIMUM_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MINIMUM_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MULTIPLE_OF_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MULTIPLE_OF_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MULTIPLE_OF_UPDATED_IS_DIVISIBLE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NUMBER_TYPE_MULTIPLE_OF_UPDATED_IS_NOT_DIVISIBLE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffAddedRemoved;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffBooleanTransition;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffNumber;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffNumberOriginalMultipleOfUpdated;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class NumberSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final NumberSchema original;

    private NumberSchemaWrapper schema;

    public NumberSchemaDiffVisitor(DiffContext ctx, NumberSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitNumberSchema(NumberSchemaWrapper schema) {
        this.schema = schema;
        super.visitNumberSchema(schema);
    }

    @Override
    public void visitRequiredInteger(boolean requiresInteger) {
        boolean originalRequiresInteger = original.requiresInteger();

        if (original.getMultipleOf() != null) {
            BigDecimal multipleOf = new BigDecimal(original.getMultipleOf().toString()); // Not pretty but it works:/
            BigDecimal one = new BigDecimal("1");
            originalRequiresInteger = originalRequiresInteger || multipleOf.compareTo(one) == 0;
        }

        diffBooleanTransition(ctx.sub("requiresInteger"), originalRequiresInteger, requiresInteger, false,
            NUMBER_TYPE_INTEGER_REQUIRED_FALSE_TO_TRUE,
            NUMBER_TYPE_INTEGER_REQUIRED_TRUE_TO_FALSE,
            NUMBER_TYPE_INTEGER_REQUIRED_UNCHANGED);

        super.visitRequiredInteger(requiresInteger);
    }

    @Override
    public void visitMinimum(Number minimum) {
        boolean isOriginalMinimumExclusive = original.getExclusiveMinimumLimit() != null;
        Number originalMinimum = isOriginalMinimumExclusive ? original.getExclusiveMinimumLimit() : original.getMinimum();

        boolean isUpdatedMinimumExclusive = schema.getExclusiveMinimumLimit() != null;
        Number updatedMinimum = isUpdatedMinimumExclusive ? schema.getExclusiveMinimumLimit() : schema.getMinimum();

        if (diffNumber(ctx.sub("minimum"), originalMinimum, updatedMinimum,
            NUMBER_TYPE_MINIMUM_ADDED,
            NUMBER_TYPE_MINIMUM_REMOVED,
            NUMBER_TYPE_MINIMUM_INCREASED,
            NUMBER_TYPE_MINIMUM_DECREASED)) {

            diffBooleanTransition(ctx.sub("exclusiveMinimum"), isOriginalMinimumExclusive, isUpdatedMinimumExclusive, false,
                NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_FALSE_TO_TRUE,
                NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_TRUE_TO_FALSE,
                NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_UNCHANGED);
        }

        super.visitMinimum(minimum);
    }

    /**
     * This is for older draft, where exclusiveMinimum was a boolean
     */
    @Override
    public void visitExclusiveMinimum(boolean exclusiveMinimum) {
        diffBooleanTransition(ctx.sub("exclusiveMinimum"), original.isExclusiveMinimum(), exclusiveMinimum, false,
            NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_FALSE_TO_TRUE,
            NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_TRUE_TO_FALSE,
            NUMBER_TYPE_IS_MINIMUM_EXCLUSIVE_UNCHANGED);
        super.visitExclusiveMinimum(exclusiveMinimum);
    }

    @Override
    public void visitExclusiveMinimumLimit(Number exclusiveMinimumLimit) {
        // This is also handled by visitMinimum
        super.visitExclusiveMinimumLimit(exclusiveMinimumLimit);
    }

    @Override
    public void visitMaximum(Number maximum) {
        boolean isOriginalMaximumExclusive = original.getExclusiveMaximumLimit() != null;
        Number originalMaximum = isOriginalMaximumExclusive ? original.getExclusiveMaximumLimit() : original.getMaximum();

        boolean isUpdatedMaximumExclusive = schema.getExclusiveMaximumLimit() != null;
        Number updatedMaximum = isUpdatedMaximumExclusive ? schema.getExclusiveMaximumLimit() : schema.getMaximum();

        if (diffNumber(ctx.sub("maximum"), originalMaximum, updatedMaximum,
            NUMBER_TYPE_MAXIMUM_ADDED,
            NUMBER_TYPE_MAXIMUM_REMOVED,
            NUMBER_TYPE_MAXIMUM_INCREASED,
            NUMBER_TYPE_MAXIMUM_DECREASED)) {

            diffBooleanTransition(ctx.sub("exclusiveMaximum"), isOriginalMaximumExclusive, isUpdatedMaximumExclusive, false,
                NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_FALSE_TO_TRUE,
                NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_TRUE_TO_FALSE,
                NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_UNCHANGED);
        }

        super.visitMaximum(maximum);
    }

    @Override
    public void visitExclusiveMaximum(boolean exclusiveMaximum) {
        diffBooleanTransition(ctx.sub("exclusiveMaximum"), original.isExclusiveMaximum(), exclusiveMaximum, false,
            NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_FALSE_TO_TRUE,
            NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_TRUE_TO_FALSE,
            NUMBER_TYPE_IS_MAXIMUM_EXCLUSIVE_UNCHANGED);

        super.visitExclusiveMaximum(exclusiveMaximum);
    }

    @Override
    public void visitExclusiveMaximumLimit(Number exclusiveMaximumLimit) {
        // This is also handled by visitMaximum
        super.visitExclusiveMaximumLimit(exclusiveMaximumLimit);
    }

    @Override
    public void visitMultipleOf(Number multipleOf) {
        DiffContext subCtx = ctx.sub("multipleOf");
        if (diffAddedRemoved(subCtx, original.getMultipleOf(), multipleOf,
            NUMBER_TYPE_MULTIPLE_OF_ADDED,
            NUMBER_TYPE_MULTIPLE_OF_REMOVED)) {
            diffNumberOriginalMultipleOfUpdated(subCtx, original.getMultipleOf(), multipleOf,
                NUMBER_TYPE_MULTIPLE_OF_UPDATED_IS_DIVISIBLE,
                NUMBER_TYPE_MULTIPLE_OF_UPDATED_IS_NOT_DIVISIBLE);
        }
        super.visitMultipleOf(multipleOf);
    }
}
