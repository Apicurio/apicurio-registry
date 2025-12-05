package io.apicurio.registry.graphql.content;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;

import java.util.Map;

public class GraphQLContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            if (contentType.toLowerCase().contains("graph")) {
                TypeDefinitionRegistry typeRegistry = new SchemaParser()
                        .parse(content.getContent().content());
                if (typeRegistry != null) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Must not be a GraphQL file
        }
        return false;
    }

}
