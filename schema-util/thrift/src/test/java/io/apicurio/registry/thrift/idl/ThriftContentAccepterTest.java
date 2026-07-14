package io.apicurio.registry.thrift.idl;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.thrift.content.ThriftContentAccepter;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class ThriftContentAccepterTest {

    private final ThriftContentAccepter accepter = new ThriftContentAccepter();

    @Test
    public void testAcceptsValidThrift() {
        String schema = "namespace java com.example\nstruct Foo {\n  1: string bar\n}\n";
        TypedContent content = TypedContent.create(ContentHandle.create(schema),
                ContentTypes.APPLICATION_THRIFT);
        Assertions.assertTrue(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testAcceptsServiceOnly() {
        String schema = "service MyService {\n  void ping()\n}\n";
        TypedContent content = TypedContent.create(ContentHandle.create(schema),
                ContentTypes.APPLICATION_THRIFT);
        Assertions.assertTrue(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testRejectsPlainText() {
        String text = "This is not thrift at all";
        TypedContent content = TypedContent.create(ContentHandle.create(text),
                ContentTypes.APPLICATION_THRIFT);
        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testRejectsJson() {
        String json = "{\"type\": \"record\", \"name\": \"test\"}";
        TypedContent content = TypedContent.create(ContentHandle.create(json),
                ContentTypes.APPLICATION_JSON);
        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testRejectsNonThriftContentType() {
        String schema = "namespace java com.example\nstruct Foo {\n  1: string bar\n}\n";
        TypedContent content = TypedContent.create(ContentHandle.create(schema),
                ContentTypes.APPLICATION_JSON);
        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testRejectsEmptyContent() {
        TypedContent content = TypedContent.create(ContentHandle.create(""),
                ContentTypes.APPLICATION_THRIFT);
        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testAcceptsNullContentType() {
        String schema = "namespace java com.example\nstruct Foo {\n  1: string bar\n}\n";
        TypedContent content = TypedContent.create(ContentHandle.create(schema), null);
        Assertions.assertTrue(accepter.acceptsContent(content, Collections.emptyMap()));
    }

}
