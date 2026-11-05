package io.apicurio.registry.avro.content;

import io.apicurio.registry.avro.util.AvroParserAccessor;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class AvroContentAccepter implements ContentAccepter {

    private static final Pattern QUOTED_BRACKETS = Pattern.compile(": *\"\\{}\"");

    /**
     * Given a content removes any quoted brackets. This is useful for some validation corner cases in avro
     * where some libraries detects quoted brackets as valid and others as invalid
     */
    private static String removeQuotedBrackets(String content) {
        return QUOTED_BRACKETS.matcher(content).replaceAll(":{}");
    }

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            if (content.getContentType() != null && content.getContentType().toLowerCase().contains("json")
                    && !ContentTypeUtil.isParsableJson(content.getContent())) {
                return false;
            }
            // Avro without quote
            // The seeded parser resolves interdependent references regardless of map iteration order
            // (#9188). Snapshot the types they define before parsing the main schema, so that only the
            // referenced types are treated as external when the schema is serialized below.
            final Schema.Parser parser = AvroParserAccessor.newParser(resolvedReferences);
            final List<Schema> schemaRefs = new ArrayList<>(parser.getTypes().values());
            final Schema schema = parser.parse(removeQuotedBrackets(content.getContent().content()));
            schema.toString(schemaRefs, false);
            return true;
        } catch (Exception e) {
            // ignored
        }
        return false;
    }

}
