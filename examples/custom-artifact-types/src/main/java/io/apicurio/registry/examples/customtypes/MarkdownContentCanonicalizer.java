package io.apicurio.registry.examples.customtypes;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;

import java.util.Map;

/**
 * Canonical form of a MARKDOWN document: LF line endings, no trailing whitespace, single trailing
 * newline. The registry uses it to recognise equivalent content (for example when searching with
 * {@code canonical=true}).
 */
public class MarkdownContentCanonicalizer implements ContentCanonicalizer {

    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        String canonical = Markdown.canonicalize(Markdown.text(content));
        return TypedContent.create(ContentHandle.create(canonical), Markdown.CONTENT_TYPE);
    }
}
