package io.apicurio.registry.json.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.StringSchemaWrapper;
import org.everit.json.schema.StringSchema;

import java.util.Map;
import java.util.regex.Pattern;

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

        // Process "contentEncoding" and "contentMediaType" which are at the moment stored as unprocessed
        // properties
        Map<String, Object> originalUnprocessed = original.getUnprocessedProperties();
        Map<String, Object> updatedUnprocessed = stringSchema.getUnprocessedProperties();
        // "contentEncoding"
        DiffContext subCtx = ctx.sub("contentEncoding");
        DiffUtil.diffObject(subCtx, DiffUtil.getExceptionally(subCtx, () -> originalUnprocessed.get("contentEncoding")),
                DiffUtil.getExceptionally(subCtx, () -> updatedUnprocessed.get("contentEncoding")),
                DiffType.STRING_TYPE_CONTENT_ENCODING_ADDED, DiffType.STRING_TYPE_CONTENT_ENCODING_REMOVED,
                DiffType.STRING_TYPE_CONTENT_ENCODING_CHANGED);
        // "contentMediaType"
        subCtx = ctx.sub("contentMediaType");
        DiffUtil.diffObject(subCtx, DiffUtil.getExceptionally(subCtx, () -> originalUnprocessed.get("contentMediaType")),
                DiffUtil.getExceptionally(subCtx, () -> updatedUnprocessed.get("contentMediaType")),
                DiffType.STRING_TYPE_CONTENT_MEDIA_TYPE_ADDED, DiffType.STRING_TYPE_CONTENT_MEDIA_TYPE_REMOVED,
                DiffType.STRING_TYPE_CONTENT_MEDIA_TYPE_CHANGED);

        super.visitStringSchema(stringSchema);
    }

    @Override
    public void visitMinLength(Integer minLength) {
        ctx.log("Visiting minLength: " + minLength);
        DiffUtil.diffInteger(ctx.sub("minLength"), original.getMinLength(), minLength, DiffType.STRING_TYPE_MIN_LENGTH_ADDED,
                DiffType.STRING_TYPE_MIN_LENGTH_REMOVED, DiffType.STRING_TYPE_MIN_LENGTH_INCREASED,
                DiffType.STRING_TYPE_MIN_LENGTH_DECREASED);
        super.visitMinLength(minLength);
    }

    @Override
    public void visitMaxLength(Integer maxLength) {
        ctx.log("Visiting maxLength " + maxLength);
        DiffUtil.diffInteger(ctx.sub("maxLength"), original.getMaxLength(), maxLength, DiffType.STRING_TYPE_MAX_LENGTH_ADDED,
                DiffType.STRING_TYPE_MAX_LENGTH_REMOVED, DiffType.STRING_TYPE_MAX_LENGTH_INCREASED,
                DiffType.STRING_TYPE_MAX_LENGTH_DECREASED);
        super.visitMaxLength(maxLength);
    }

    @Override
    public void visitPattern(Pattern pattern) {
        ctx.log("Visiting pattern " + pattern);
        // careful with the pattern wrappers
        DiffContext subCtx = ctx.sub("pattern");
        DiffUtil.diffObject(subCtx, DiffUtil.getExceptionally(subCtx, () -> original.getPattern().pattern()), pattern.pattern(),
                DiffType.STRING_TYPE_PATTERN_ADDED, DiffType.STRING_TYPE_PATTERN_REMOVED, DiffType.STRING_TYPE_PATTERN_CHANGED);
        super.visitPattern(pattern);
    }

    @Override
    public void visitFormat(String formatName) {
        ctx.log("Visiting formatValidator " + formatName);
        DiffContext subCtx = ctx.sub("format");
        DiffUtil.diffObjectDefault(subCtx, DiffUtil.getExceptionally(subCtx, () -> original.getFormatValidator().formatName()),
                formatName, "unnamed-format", DiffType.STRING_TYPE_FORMAT_ADDED, DiffType.STRING_TYPE_FORMAT_REMOVED,
                DiffType.STRING_TYPE_FORMAT_CHANGED);
        super.visitFormat(formatName);
    }
}
