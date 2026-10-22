package io.apicurio.registry.thrift.idl;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.thrift.content.canon.ThriftContentCanonicalizer;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class ThriftContentCanonicalizerTest {

    private final ThriftContentCanonicalizer canonicalizer = new ThriftContentCanonicalizer();

    @Test
    public void testStripsComments() {
        String input = "// comment\nnamespace java com.example\n/* block */\nstruct Foo {\n  1: string bar\n}\n";
        TypedContent content = TypedContent.create(ContentHandle.create(input),
                ContentTypes.APPLICATION_THRIFT);

        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());
        String canonicalized = result.getContent().content();

        Assertions.assertFalse(canonicalized.contains("comment"));
        Assertions.assertFalse(canonicalized.contains("block"));
        Assertions.assertTrue(canonicalized.contains("namespace java com.example"));
        Assertions.assertTrue(canonicalized.contains("struct Foo"));
    }

    @Test
    public void testNormalizesWhitespace() {
        String input = "namespace java com.example\n\n\n\nstruct Foo {\n  1: string bar\n}\n\n\n";
        TypedContent content = TypedContent.create(ContentHandle.create(input),
                ContentTypes.APPLICATION_THRIFT);

        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());
        String canonicalized = result.getContent().content();

        Assertions.assertFalse(canonicalized.contains("\n\n\n"));
    }

    @Test
    public void testSameContentProducesSameCanonical() {
        String v1 = "// version 1\nnamespace java com.example\nstruct Foo {\n  1: string bar\n}\n";
        String v2 = "# version 2\nnamespace java com.example\nstruct Foo {\n  1: string bar\n}\n";

        TypedContent c1 = TypedContent.create(ContentHandle.create(v1), ContentTypes.APPLICATION_THRIFT);
        TypedContent c2 = TypedContent.create(ContentHandle.create(v2), ContentTypes.APPLICATION_THRIFT);

        String r1 = canonicalizer.canonicalize(c1, Collections.emptyMap()).getContent().content();
        String r2 = canonicalizer.canonicalize(c2, Collections.emptyMap()).getContent().content();

        Assertions.assertEquals(r1, r2);
    }

    @Test
    public void testReturnsThriftContentType() {
        String input = "namespace java com.example\n";
        TypedContent content = TypedContent.create(ContentHandle.create(input),
                ContentTypes.APPLICATION_THRIFT);

        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());
        Assertions.assertEquals(ContentTypes.APPLICATION_THRIFT, result.getContentType());
    }

    @Test
    public void testHandlesInvalidContentGracefully() {
        String invalid = "this is not thrift at all";
        TypedContent content = TypedContent.create(ContentHandle.create(invalid),
                ContentTypes.APPLICATION_THRIFT);

        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());
        Assertions.assertNotNull(result);
    }

}
