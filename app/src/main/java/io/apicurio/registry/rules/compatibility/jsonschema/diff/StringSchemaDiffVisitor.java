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
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.StringSchemaWrapper;
import org.everit.json.schema.FormatValidator;
import org.everit.json.schema.StringSchema;

import java.util.regex.Pattern;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_FORMAT_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_FORMAT_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_FORMAT_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_MAX_LENGTH_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_MAX_LENGTH_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_MAX_LENGTH_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_MAX_LENGTH_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_MIN_LENGTH_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_MIN_LENGTH_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_MIN_LENGTH_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_MIN_LENGTH_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_PATTERN_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_PATTERN_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_PATTERN_REMOVED;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class StringSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final StringSchema original;

    public StringSchemaDiffVisitor(DiffContext ctx, StringSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitStringSchema(StringSchemaWrapper stringSchema) {
        ctx.log("Visiting " + stringSchema + " at " + stringSchema.getWrapped().getLocation());
        super.visitStringSchema(stringSchema);
    }

    @Override
    public void visitMinLength(Integer minLength) {
        ctx.log("Visiting minLength: " + minLength);
        DiffUtil.diffInteger(ctx.sub("minLength"), original.getMinLength(), minLength,
            STRING_TYPE_MIN_LENGTH_ADDED,
            STRING_TYPE_MIN_LENGTH_REMOVED,
            STRING_TYPE_MIN_LENGTH_INCREASED,
            STRING_TYPE_MIN_LENGTH_DECREASED);
        super.visitMinLength(minLength);
    }

    @Override
    public void visitMaxLength(Integer maxLength) {
        ctx.log("Visiting maxLength " + maxLength);
        DiffUtil.diffInteger(ctx.sub("maxLength"), original.getMaxLength(), maxLength,
            STRING_TYPE_MAX_LENGTH_ADDED,
            STRING_TYPE_MAX_LENGTH_REMOVED,
            STRING_TYPE_MAX_LENGTH_INCREASED,
            STRING_TYPE_MAX_LENGTH_DECREASED);
        super.visitMaxLength(maxLength);
    }

    @Override
    public void visitPattern(Pattern pattern) {
        ctx.log("Visiting pattern " + pattern);
        // careful with the pattern wrappers
        DiffUtil.diffObject(ctx.sub("pattern"), original.getPattern(), pattern,
            STRING_TYPE_PATTERN_ADDED,
            STRING_TYPE_PATTERN_REMOVED,
            STRING_TYPE_PATTERN_CHANGED);
        super.visitPattern(pattern);
    }

    @Override
    public void visitFormat(FormatValidator formatValidator) {
        ctx.log("Visiting formatValidator " + formatValidator);
        DiffUtil.diffObject(ctx.sub("format"), original.getFormatValidator(), formatValidator,
            STRING_TYPE_FORMAT_ADDED,
            STRING_TYPE_FORMAT_REMOVED,
            STRING_TYPE_FORMAT_CHANGED);
        super.visitFormat(formatValidator);
    }
}
