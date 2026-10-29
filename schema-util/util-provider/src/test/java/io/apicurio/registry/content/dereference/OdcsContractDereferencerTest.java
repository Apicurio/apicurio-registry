package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class OdcsContractDereferencerTest extends ArtifactUtilProviderTestBase {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final String URL =
            "https://registry.example.com/apis/registry/v3/groups/g/artifacts/OrderEvent/versions/3/content?references=REWRITE";

    @Test
    public void testDereferenceInlinesSchemaDefinition() throws Exception {
        TypedContent content = resourceToTypedContentHandle("odcs-to-deref.yaml");
        TypedContent avro = resourceToTypedContentHandle("order-event.avsc");
        TypedContent result = new OdcsContractDereferencer().dereference(content,
                Map.of("orders/OrderEvent:3", avro));

        JsonNode tree = YAML_MAPPER.readTree(result.getContent().content());
        JsonNode schema = tree.get("schemas").get(0);
        Assertions.assertEquals("orders/OrderEvent:3", schema.get("location").asText());
        JsonNode schemaDefinition = schema.get("x-apicurio-schemaDefinition");
        Assertions.assertNotNull(schemaDefinition);
        Assertions.assertEquals("record", schemaDefinition.get("type").asText());
        Assertions.assertEquals("OrderEvent", schemaDefinition.get("name").asText());
        Assertions.assertEquals(content.getContentType(), result.getContentType());
    }

    @Test
    public void testDereferenceFallsBackToRawTextWhenUnparseable() throws Exception {
        TypedContent content = resourceToTypedContentHandle("odcs-to-deref.yaml");
        TypedContent avroIdl = TypedContent.create(ContentHandle.create("record OrderEvent { string orderId; }"),
                ContentTypes.APPLICATION_JSON);
        TypedContent result = new OdcsContractDereferencer().dereference(content,
                Map.of("orders/OrderEvent:3", avroIdl));

        JsonNode tree = YAML_MAPPER.readTree(result.getContent().content());
        JsonNode schemaDefinition = tree.get("schemas").get(0).get("x-apicurio-schemaDefinition");
        Assertions.assertTrue(schemaDefinition.isTextual());
        Assertions.assertEquals("record OrderEvent { string orderId; }", schemaDefinition.asText());
    }

    @Test
    public void testDereferenceDoesNotOverwriteExistingSchemaDefinition() throws Exception {
        TypedContent content = resourceToTypedContentHandle("odcs-with-existing-schema-definition.yaml");
        TypedContent avro = resourceToTypedContentHandle("order-event.avsc");
        TypedContent result = new OdcsContractDereferencer().dereference(content,
                Map.of("orders/OrderEvent:3", avro));

        JsonNode tree = YAML_MAPPER.readTree(result.getContent().content());
        JsonNode schemaDefinition = tree.get("schemas").get(0).get("x-apicurio-schemaDefinition");
        Assertions.assertTrue(schemaDefinition.isTextual());
        Assertions.assertEquals("user-supplied value, must not be overwritten", schemaDefinition.asText());
    }

    @Test
    public void testDereferenceNoMatchReturnsOriginal() {
        TypedContent content = resourceToTypedContentHandle("odcs-to-deref.yaml");
        Assertions.assertSame(content, new OdcsContractDereferencer().dereference(content,
                Map.of("other/Schema:1", resourceToTypedContentHandle("order-event.avsc"))));
        Assertions.assertSame(content, new OdcsContractDereferencer().dereference(content, Map.of()));
    }

    @Test
    public void testRewriteReferences() throws Exception {
        TypedContent content = resourceToTypedContentHandle("odcs-to-deref.yaml");
        TypedContent result = new OdcsContractDereferencer().rewriteReferences(content,
                Map.of("orders/OrderEvent:3", URL));

        JsonNode tree = YAML_MAPPER.readTree(result.getContent().content());
        Assertions.assertEquals(URL, tree.get("schemas").get(0).get("location").asText());
        Assertions.assertEquals(content.getContentType(), result.getContentType());
    }

    @Test
    public void testRewriteReferencesNoMatchReturnsOriginal() {
        TypedContent content = resourceToTypedContentHandle("odcs-to-deref.yaml");
        Assertions.assertSame(content, new OdcsContractDereferencer().rewriteReferences(content,
                Map.of("other/Schema:1", URL)));
        Assertions.assertSame(content, new OdcsContractDereferencer().rewriteReferences(content, Map.of()));
    }

    @Test
    public void testDereferenceWithJsonContentTypeProducesParseableJson() throws Exception {
        String json = "{\"apiVersion\":\"v3.1.0\",\"kind\":\"DataContract\",\"id\":\"orders-contract\","
                + "\"schemas\":[{\"name\":\"OrderEvent\",\"type\":\"avro\",\"location\":\"orders/OrderEvent:3\"}]}";
        TypedContent content = TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
        TypedContent avro = resourceToTypedContentHandle("order-event.avsc");
        TypedContent result = new OdcsContractDereferencer().dereference(content,
                Map.of("orders/OrderEvent:3", avro));

        Assertions.assertEquals(ContentTypes.APPLICATION_JSON, result.getContentType());
        JsonNode tree = new ObjectMapper().readTree(result.getContent().content());
        JsonNode schemaDefinition = tree.get("schemas").get(0).get("x-apicurio-schemaDefinition");
        Assertions.assertEquals("OrderEvent", schemaDefinition.get("name").asText());
    }

    @Test
    public void testRewriteReferencesWithJsonContentTypeProducesParseableJson() throws Exception {
        String json = "{\"apiVersion\":\"v3.1.0\",\"kind\":\"DataContract\",\"id\":\"orders-contract\","
                + "\"schemas\":[{\"name\":\"OrderEvent\",\"type\":\"avro\",\"location\":\"orders/OrderEvent:3\"}]}";
        TypedContent content = TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
        TypedContent result = new OdcsContractDereferencer().rewriteReferences(content,
                Map.of("orders/OrderEvent:3", URL));

        Assertions.assertEquals(ContentTypes.APPLICATION_JSON, result.getContentType());
        JsonNode tree = new ObjectMapper().readTree(result.getContent().content());
        Assertions.assertEquals(URL, tree.get("schemas").get(0).get("location").asText());
    }
}
