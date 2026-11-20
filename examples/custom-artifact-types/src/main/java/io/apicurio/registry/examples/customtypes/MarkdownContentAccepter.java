package io.apicurio.registry.examples.customtypes;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;

import java.util.Map;

/**
 * Lets the registry auto-detect the MARKDOWN artifact type when a client does not specify one:
 * the content type is {@code text/markdown}, or the document starts with a level-1 heading.
 */
public class MarkdownContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        if (Markdown.CONTENT_TYPE.equalsIgnoreCase(content.getContentType())) {
            return true;
        }
        return Markdown.title(Markdown.text(content)) != null;
    }
}
