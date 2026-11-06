package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceIndexTest {

    private final TypeDetector detector = new TypeDetector(new DefaultArtifactTypeUtilProviderImpl(true));

    @Test
    void lookupOrder() {
        SchemaSource order = InMemorySchemaSource.of("orders/Order.json", "{\"$schema\":\"x\"}",
                ContentTypes.APPLICATION_JSON);
        SchemaSource addr = InMemorySchemaSource.of("common/Address.json",
                "{\"$schema\":\"x\",\"$id\":\"https://ex.com/address\"}", ContentTypes.APPLICATION_JSON);
        SchemaSource avro = InMemorySchemaSource.of("Exchange.avsc",
                "{\"type\":\"enum\",\"name\":\"Exchange\",\"symbols\":[\"A\"]}", ContentTypes.APPLICATION_JSON);
        ExistingArtifact money = new ExistingArtifact("Money", "g", "money", "3");
        SourceIndex index = SourceIndex.build(SchemaSourceSet.of(order, addr, avro), List.of(money), detector);

        assertEquals(money, index.lookup("Money", order).orElseThrow().existing());
        assertEquals("Exchange.avsc", index.lookup("Exchange", order).orElseThrow().source().source().location());
        assertEquals("common/Address.json",
                index.lookup("https://ex.com/address", order).orElseThrow().source().source().location());
        assertEquals("common/Address.json",
                index.lookup("../common/Address.json", order).orElseThrow().source().source().location());
        assertTrue(index.lookup("missing.json", order).isEmpty());
    }

    @Test
    void avroWithoutNamespaceIsIndexedByName() {
        SchemaSource s = InMemorySchemaSource.of("A.avsc",
                "{\"type\":\"record\",\"name\":\"A\",\"fields\":[]}", ContentTypes.APPLICATION_JSON);
        SourceIndex index = SourceIndex.build(SchemaSourceSet.of(s), List.of(), detector);
        assertEquals("A", index.bySource(s).orElseThrow().avroFullName());
    }

    @Test
    void protobufResolvesThroughSearchRootAliases() {
        // "mode/mode.proto" is referenced from "sample/t.proto" via search-root semantics
        SchemaSource t = InMemorySchemaSource.of("sample/t.proto", "syntax = \"proto3\"; import \"mode/mode.proto\";",
                ContentTypes.APPLICATION_PROTOBUF);
        SchemaSource m = InMemorySchemaSource.of("mode/mode.proto", "syntax = \"proto3\"; enum M { A = 0; }",
                ContentTypes.APPLICATION_PROTOBUF);
        SourceIndex index = SourceIndex.build(SchemaSourceSet.of(t, m), List.of(), detector);
        assertEquals("mode/mode.proto", index.lookup("mode/mode.proto", t).orElseThrow().source().source().location());
    }

    @Test
    void duplicateNamesAndIdsAreFirstWinsWithWarnings() {
        SchemaSource avro1 = InMemorySchemaSource.of("a/E.avsc",
                "{\"type\":\"enum\",\"name\":\"E\",\"namespace\":\"n\",\"symbols\":[\"A\"]}",
                ContentTypes.APPLICATION_JSON);
        SchemaSource json1 = InMemorySchemaSource.of("a/x.json", "{\"$schema\":\"s\",\"$id\":\"urn:x#frag\"}",
                ContentTypes.APPLICATION_JSON);
        SchemaSource avro2 = InMemorySchemaSource.of("b/E.avsc",
                "{\"type\":\"enum\",\"name\":\"E\",\"namespace\":\"n\",\"symbols\":[\"B\"]}",
                ContentTypes.APPLICATION_JSON);
        SchemaSource json2 = InMemorySchemaSource.of("b/x.json", "{\"$schema\":\"s\",\"$id\":\"urn:x\"}",
                ContentTypes.APPLICATION_JSON);
        SourceIndex index = SourceIndex.build(SchemaSourceSet.of(avro1, json1, avro2, json2), List.of(), detector);

        assertEquals(List.of("Duplicate Avro name 'n.E' in b/E.avsc; using a/E.avsc",
                "Duplicate JSON Schema $id 'urn:x' in b/x.json; using a/x.json"), index.warnings());
        assertEquals("a/E.avsc", index.lookup("n.E", json2).orElseThrow().source().source().location());
        assertEquals("a/x.json", index.lookup("urn:x", avro2).orElseThrow().source().source().location());
    }

    @Test
    void noWarningsWithoutDuplicates() {
        SchemaSource s = InMemorySchemaSource.of("A.avsc",
                "{\"type\":\"record\",\"name\":\"A\",\"fields\":[]}", ContentTypes.APPLICATION_JSON);
        assertEquals(List.of(), SourceIndex.build(SchemaSourceSet.of(s), List.of(), detector).warnings());
    }
}
