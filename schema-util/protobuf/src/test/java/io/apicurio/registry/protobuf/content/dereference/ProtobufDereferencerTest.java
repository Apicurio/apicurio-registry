package io.apicurio.registry.protobuf.content.dereference;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProtobufDereferencerTest {

    private final ProtobufDereferencer dereferencer = new ProtobufDereferencer();

    private TypedContent toTypedContent(String schema) {
        return TypedContent.create(ContentHandle.create(schema), ContentTypes.APPLICATION_PROTOBUF);
    }

    @Test
    void testDereferenceServiceOnlyProto() {
        String messageProto = """
                syntax = "proto3";
                package com.example;

                message HelloRequest {
                    string name = 1;
                }

                message HelloReply {
                    string message = 1;
                }
                """;

        String serviceProto = """
                syntax = "proto3";
                package com.example;
                import "messages.proto";

                service Greeter {
                    rpc SayHello (HelloRequest) returns (HelloReply);
                }
                """;

        TypedContent result = dereferencer.dereference(
                toTypedContent(serviceProto),
                Map.of("messages.proto", toTypedContent(messageProto))
        );

        assertNotNull(result);
        assertNotNull(result.getContent());
    }

    @Test
    void testDereferenceEnumOnlyProto() {
        String enumProto = """
                syntax = "proto3";
                package com.example;

                enum Status {
                    UNKNOWN = 0;
                    ACTIVE = 1;
                    INACTIVE = 2;
                }
                """;

        TypedContent result = dereferencer.dereference(
                toTypedContent(enumProto),
                Collections.emptyMap()
        );

        assertNotNull(result);
        assertNotNull(result.getContent());
    }
}
