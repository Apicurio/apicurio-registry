package io.apicurio.registry.storage.impl.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.protobuf.content.extract.ProtobufStructuredContentExtractor;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtobufStructuredContentExtractorTest {

    private final ProtobufStructuredContentExtractor extractor = new ProtobufStructuredContentExtractor();

    @Test
    void testExtractPackage() {
        String proto = """
                syntax = "proto3";
                package com.example.events;

                message Empty {}
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(proto));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("package") && e.name().equals("com.example.events")));
    }

    @Test
    void testExtractMessages() {
        String proto = """
                syntax = "proto3";

                message User {
                  string name = 1;
                  int32 age = 2;
                }

                message Address {
                  string street = 1;
                  string city = 2;
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(proto));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("message") && e.name().equals("User")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("message") && e.name().equals("Address")));
    }

    @Test
    void testExtractFields() {
        String proto = """
                syntax = "proto3";

                message Order {
                  string order_id = 1;
                  string customer_id = 2;
                  double amount = 3;
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(proto));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("order_id")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("field") && e.name().equals("customer_id")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("amount")));
    }

    @Test
    void testExtractEnum() {
        String proto = """
                syntax = "proto3";

                enum Status {
                  UNKNOWN = 0;
                  ACTIVE = 1;
                  INACTIVE = 2;
                }

                message Dummy {
                  Status status = 1;
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(proto));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("enum") && e.name().equals("Status")));
    }

    @Test
    void testExtractService() {
        String proto = """
                syntax = "proto3";

                message GetUserRequest {
                  string id = 1;
                }

                message GetUserResponse {
                  string name = 1;
                }

                message ListUsersRequest {}

                message ListUsersResponse {
                  repeated GetUserResponse users = 1;
                }

                service UserService {
                  rpc GetUser (GetUserRequest) returns (GetUserResponse);
                  rpc ListUsers (ListUsersRequest) returns (ListUsersResponse);
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(proto));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("service") && e.name().equals("UserService")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("rpc") && e.name().equals("GetUser")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("rpc") && e.name().equals("ListUsers")));
    }

    @Test
    void testExtractComprehensive() {
        String proto = """
                syntax = "proto3";
                package com.example.api;

                enum Priority {
                  LOW = 0;
                  MEDIUM = 1;
                  HIGH = 2;
                }

                message Task {
                  string id = 1;
                  string title = 2;
                  Priority priority = 3;
                }

                message CreateTaskRequest {
                  Task task = 1;
                }

                message CreateTaskResponse {
                  Task task = 1;
                }

                service TaskService {
                  rpc CreateTask (CreateTaskRequest) returns (CreateTaskResponse);
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(proto));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("package")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("message")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("enum")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("service")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("rpc")));
    }

    @Test
    void testExtractFromInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not valid protobuf"));

        assertTrue(elements.isEmpty());
    }
}
