package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.TypedContent;

import java.util.Map;

/**
 * Dereference some content! This means replacing any reference inside the content by the full referenced
 * content. The result is an artifact content that can be used on its own.
 */
public interface ContentDereferencer {

    /**
     * Called to dereference the given content to its dereferenced form
     * 
     * @param content
     */
    TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences);

    /**
     * Called to rewrite any references in the content so that they point to valid Registry API URLs rather
     * than "logical" values. For example, if an OpenAPI document has a <code>$ref</code> property with a
     * value of <code>./common-types.json#/defs/FooType</code> this method will rewrite that property to
     * something like
     * <code>https://registry.example.com/apis/registry/v3/groups/Example/artifacts/CommonTypes/versions/1.0</code>.
     * 
     * @param content
     * @param resolvedReferenceUrls
     */
    TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls);
}
