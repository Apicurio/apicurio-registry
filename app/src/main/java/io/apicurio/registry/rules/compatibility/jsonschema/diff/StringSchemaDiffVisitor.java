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
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.StringSchemaWrapper;
import org.everit.json.schema.StringSchema;

import java.util.Map;
import java.util.regex.Pattern;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_CONTENT_ENCODING_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_CONTENT_ENCODING_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_CONTENT_ENCODING_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_CONTENT_MEDIA_TYPE_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_CONTENT_MEDIA_TYPE_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.STRING_TYPE_CONTENT_MEDIA_TYPE_REMOVED;
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
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffInteger;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffObject;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffObjectDefault;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.getExceptionally;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
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

        // Process "contentEncoding" and "contentMediaType" which are at the moment stored as unprocessed properties
        Map<String, Object> originalUnprocessed = original.getUnprocessedProperties();
        Map<String, Object> updatedUnprocessed = stringSchema.getUnprocessedProperties();
        // "contentEncoding"
        DiffContext subCtx = ctx.sub("contentEncoding");
        diffObject(subCtx, getExceptionally(subCtx, () -> originalUnprocessed.get("contentEncoding")),
            getExceptionally(subCtx, () -> updatedUnprocessed.get("contentEncoding")),
            STRING_TYPE_CONTENT_ENCODING_ADDED,
            STRING_TYPE_CONTENT_ENCODING_REMOVED,
            STRING_TYPE_CONTENT_ENCODING_CHANGED);
        // "contentMediaType"
        subCtx = ctx.sub("contentMediaType");
        diffObject(subCtx, getExceptionally(subCtx, () -> originalUnprocessed.get("contentMediaType")),
            getExceptionally(subCtx, () -> updatedUnprocessed.get("contentMediaType")),
            STRING_TYPE_CONTENT_MEDIA_TYPE_ADDED,
            STRING_TYPE_CONTENT_MEDIA_TYPE_REMOVED,
            STRING_TYPE_CONTENT_MEDIA_TYPE_CHANGED);

        super.visitStringSchema(stringSchema);
    }

    @Override
    public void visitMinLength(Integer minLength) {
        ctx.log("Visiting minLength: " + minLength);
        diffInteger(ctx.sub("minLength"), original.getMinLength(), minLength,
            STRING_TYPE_MIN_LENGTH_ADDED,
            STRING_TYPE_MIN_LENGTH_REMOVED,
            STRING_TYPE_MIN_LENGTH_INCREASED,
            STRING_TYPE_MIN_LENGTH_DECREASED);
        super.visitMinLength(minLength);
    }

    @Override
    public void visitMaxLength(Integer maxLength) {
        ctx.log("Visiting maxLength " + maxLength);
        diffInteger(ctx.sub("maxLength"), original.getMaxLength(), maxLength,
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
        DiffContext subCtx = ctx.sub("pattern");
        diffObject(subCtx, getExceptionally(subCtx, () -> original.getPattern().pattern()), pattern.pattern(),
            STRING_TYPE_PATTERN_ADDED,
            STRING_TYPE_PATTERN_REMOVED,
            STRING_TYPE_PATTERN_CHANGED);
        super.visitPattern(pattern);
    }

    @Override
    public void visitFormat(String formatName) {
        ctx.log("Visiting formatValidator " + formatName);
        DiffContext subCtx = ctx.sub("format");
        diffObjectDefault(subCtx, getExceptionally(subCtx, () -> original.getFormatValidator().formatName()),
            formatName, "unnamed-format",
            STRING_TYPE_FORMAT_ADDED,
            STRING_TYPE_FORMAT_REMOVED,
            STRING_TYPE_FORMAT_CHANGED);
        super.visitFormat(formatName);
    }
}
