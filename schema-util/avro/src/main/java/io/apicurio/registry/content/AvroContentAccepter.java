package io.apicurio.registry.content;

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
            final Schema.Parser parser = new Schema.Parser();
            final List<Schema> schemaRefs = new ArrayList<>();
            for (Map.Entry<String, TypedContent> referencedContent : resolvedReferences.entrySet()) {
                if (!parser.getTypes().containsKey(referencedContent.getKey())) {
                    Schema schemaRef = parser.parse(referencedContent.getValue().getContent().content());
                    schemaRefs.add(schemaRef);
                }
            }
            final Schema schema = parser.parse(removeQuotedBrackets(content.getContent().content()));
            schema.toString(schemaRefs, false);
            return true;
        } catch (Exception e) {
            // ignored
        }
        return false;
    }

}
