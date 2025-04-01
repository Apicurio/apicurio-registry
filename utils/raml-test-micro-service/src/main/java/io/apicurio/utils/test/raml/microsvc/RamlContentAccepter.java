package io.apicurio.utils.test.raml.microsvc;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;

import java.util.Map;

public class RamlContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        boolean accepted = false;
        if (content.getContentType().equals("application/x-yaml")) {
            if (content.getContent().content().startsWith("#%RAML 1.0")) {
                accepted = true;
            }
        }
        return accepted;
    }

}
