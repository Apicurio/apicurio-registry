package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.client.RegistryClientFacadeImpl;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Protobuf schema with references, resolved by content ID or content hash with deserializer dereferencing
 * on, is returned by the registry as a binary FileDescriptorProto and must not be corrupted by a text read.
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/11157">Issue #11157</a>
 */
public class ProtobufContentLookupDereferenceTest {

    private static final String DEP_PROTO_SCHEMA = """
            syntax = "proto3";
            package test;
            message Dep {
              string name = 1;
              int32 count = 200;
            }
            """;

    private static final String ROOT_PROTO_SCHEMA = """
            syntax = "proto3";
            package test;
            import "dep.proto";
            message Root {
              Dep d = 1;
              string id = 2;
            }
            """;

    private final List<String> requests = new ArrayList<>();

    @Test
    public void testResolveByContentIdDereferenced() {
        DefaultSchemaResolver<ProtobufSchema, DynamicMessage> resolver = resolver();

        SchemaLookupResult<ProtobufSchema> result = resolver
                .resolveSchemaByArtifactReference(ArtifactReference.builder().contentId(7L).build());

        assertEquals(List.of("{+baseurl}/ids/contentIds/{contentId}{?references*}"), requests);
        assertDescriptor(result.getParsedSchema().getParsedSchema().getFileDescriptor());
    }

    @Test
    public void testResolveByContentHashDereferenced() {
        DefaultSchemaResolver<ProtobufSchema, DynamicMessage> resolver = resolver();

        SchemaLookupResult<ProtobufSchema> result = resolver
                .resolveSchemaByArtifactReference(ArtifactReference.builder().contentHash("abc").build());

        assertEquals(List.of("{+baseurl}/ids/contentHashes/{contentHash}{?references*}"), requests);
        assertDescriptor(result.getParsedSchema().getParsedSchema().getFileDescriptor());
    }

    private static void assertDescriptor(Descriptors.FileDescriptor fileDescriptor) {
        assertEquals("test", fileDescriptor.getPackage());
        Descriptors.Descriptor root = fileDescriptor.findMessageTypeByName("Root");
        assertEquals(List.of("d", "id"), root.getFields().stream().map(Descriptors.FieldDescriptor::getName).toList());
        Descriptors.Descriptor dep = root.findFieldByName("d").getMessageType();
        assertEquals("test.Dep", dep.getFullName());
        assertEquals(List.of("name", "count"), dep.getFields().stream().map(Descriptors.FieldDescriptor::getName).toList());
        assertEquals(Descriptors.FieldDescriptor.Type.INT32, dep.findFieldByName("count").getType());
        // Field number 200 is a two-byte varint that is not valid UTF-8, so a text read corrupts it
        assertEquals(200, dep.findFieldByName("count").getNumber());
    }

    private DefaultSchemaResolver<ProtobufSchema, DynamicMessage> resolver() {
        // The same bytes the registry's ProtobufDereferencer returns for references=DEREFERENCE
        byte[] dereferenced = FileDescriptorUtils.toFileDescriptorProto(ROOT_PROTO_SCHEMA, "Root",
                Optional.of("test"), Map.of("dep.proto", DEP_PROTO_SCHEMA)).toByteArray();
        RequestAdapter adapter = (RequestAdapter) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { RequestAdapter.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "sendPrimitive":
                            RequestInformation info = (RequestInformation) args[0];
                            assertEquals("dereference", String.valueOf(info.getQueryParameters().get("references"))
                                    .toLowerCase(Locale.ROOT));
                            requests.add(info.urlTemplate);
                            return new ByteArrayInputStream(dereferenced);
                        case "getBaseUrl":
                            return "http://localhost";
                        case "setBaseUrl":
                        case "enableBackingStore":
                            return null;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });

        DefaultSchemaResolver<ProtobufSchema, DynamicMessage> resolver = new DefaultSchemaResolver<>();
        resolver.setClientFacade(new RegistryClientFacadeImpl(new RegistryClient(adapter)));
        resolver.configure(Map.of(SchemaResolverConfig.DESERIALIZER_DEREFERENCE_SCHEMA, true),
                new ProtobufSchemaParser<>());
        return resolver;
    }
}
