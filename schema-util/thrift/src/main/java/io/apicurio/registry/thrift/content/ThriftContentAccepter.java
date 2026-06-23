package io.apicurio.registry.thrift.content;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.thrift.idl.ThriftIdlParser;

import java.util.Locale;
import java.util.Map;

public class ThriftContentAccepter implements ContentAccepter {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            if (contentType != null && !contentType.toLowerCase(Locale.ROOT).contains("thrift")) {
                return false;
            }
            return ThriftIdlParser.isThriftIdl(content.getContent().content());
        } catch (Exception e) {
            return false;
        }
    }

}
