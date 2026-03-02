package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.openapi.content.extract.OpenApiStructuredContentExtractor;
import io.apicurio.registry.asyncapi.content.extract.AsyncApiStructuredContentExtractor;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.types.VersionState;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LuceneDocumentBuilder.
 */
public class LuceneDocumentBuilderTest {

    private LuceneDocumentBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new LuceneDocumentBuilder();
    }

    @Test
    void testBuildVersionDocument_BasicFields() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);

        // When
        Document doc = builder.buildVersionDocument(metadata, content);

        // Then
        assertNotNull(doc);
        assertEquals("123456", doc.get("globalId"));
        assertEquals("789", doc.get("contentId"));
        assertEquals("test-group", doc.get("groupId"));
        assertEquals("test-artifact", doc.get("artifactId"));
        assertEquals("1.0.0", doc.get("version"));
        assertEquals("OPENAPI", doc.get("artifactType"));
        assertEquals("ENABLED", doc.get("state"));
    }

    @Test
    void testBuildVersionDocument_MetadataFields() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);

        // When
        Document doc = builder.buildVersionDocument(metadata, content);

        // Then
        assertNotNull(doc.getField("name"));
        assertNotNull(doc.getField("description"));
        assertNotNull(doc.getField("owner"));
        assertNotNull(doc.getField("content"));
    }

    @Test
    void testBuildVersionDocument_WithLabels() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);

        // When
        Document doc = builder.buildVersionDocument(metadata, content);

        // Then
        IndexableField[] labelKeyFields = doc.getFields("label_key");
        IndexableField[] labelValueFields = doc.getFields("label_value");
        IndexableField[] labelFields = doc.getFields("label");

        assertEquals(2, labelKeyFields.length);
        assertEquals(2, labelValueFields.length);
        assertEquals(2, labelFields.length);

        // Check combined label format
        String[] labels = new String[labelFields.length];
        for (int i = 0; i < labelFields.length; i++) {
            labels[i] = labelFields[i].stringValue();
        }
        assertTrue(labels[0].equals("env=production") || labels[0].equals("team=backend"));
        assertTrue(labels[1].equals("env=production") || labels[1].equals("team=backend"));
    }

    @Test
    void testBuildVersionDocument_NullGroupId() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        metadata.setGroupId(null);
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);

        // When
        Document doc = builder.buildVersionDocument(metadata, content);

        // Then
        assertEquals("default", doc.get("groupId"));
    }

    @Test
    void testBuildVersionDocument_EmptyLabels() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        metadata.setLabels(new HashMap<>());
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);

        // When
        Document doc = builder.buildVersionDocument(metadata, content);

        // Then
        IndexableField[] labelFields = doc.getFields("label");
        assertEquals(0, labelFields.length);
    }

    @Test
    void testBuildVersionDocument_OpenApiContent_WithExtractor() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        metadata.setArtifactType("OPENAPI");
        StructuredContentExtractor extractor = new OpenApiStructuredContentExtractor();

        String openApiContent = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Test API",
                    "description": "Test API description"
                  },
                  "paths": {
                    "/users": {},
                    "/orders": {}
                  }
                }
                """;
        byte[] content = openApiContent.getBytes(StandardCharsets.UTF_8);

        // When
        Document doc = builder.buildVersionDocument(metadata, content, extractor);

        // Then - verify structure fields are present
        IndexableField[] structureFields = doc.getFields("structure");
        assertTrue(structureFields.length >= 2, "Should have indexed structured elements");

        // Check that paths were indexed as structure fields
        boolean hasUsersPath = false;
        boolean hasOrdersPath = false;
        for (IndexableField field : structureFields) {
            String value = field.stringValue();
            if ("openapi:path:/users".equals(value))
                hasUsersPath = true;
            if ("openapi:path:/orders".equals(value))
                hasOrdersPath = true;
        }
        assertTrue(hasUsersPath, "Should have indexed /users path as structure field");
        assertTrue(hasOrdersPath, "Should have indexed /orders path as structure field");

        // Verify structure_text fields are also present
        IndexableField[] structureTextFields = doc.getFields("structure_text");
        assertTrue(structureTextFields.length >= 2, "Should have structure_text fields");

        // Verify structure_kind fields are also present
        IndexableField[] structureKindFields = doc.getFields("structure_kind");
        assertTrue(structureKindFields.length >= 2, "Should have structure_kind fields");
    }

    @Test
    void testBuildVersionDocument_AsyncApiContent_WithExtractor() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        metadata.setArtifactType("ASYNCAPI");
        StructuredContentExtractor extractor = new AsyncApiStructuredContentExtractor();

        String asyncApiContent = """
                {
                  "asyncapi": "2.0.0",
                  "info": {
                    "title": "Test API"
                  },
                  "channels": {
                    "user.events": {},
                    "order.events": {}
                  }
                }
                """;
        byte[] content = asyncApiContent.getBytes(StandardCharsets.UTF_8);

        // When
        Document doc = builder.buildVersionDocument(metadata, content, extractor);

        // Then - verify structure fields are present
        IndexableField[] structureFields = doc.getFields("structure");
        assertTrue(structureFields.length >= 2, "Should have indexed structured elements");

        // Check that channels were indexed as structure fields
        boolean hasUserEvents = false;
        boolean hasOrderEvents = false;
        for (IndexableField field : structureFields) {
            String value = field.stringValue();
            if ("asyncapi:channel:user.events".equals(value))
                hasUserEvents = true;
            if ("asyncapi:channel:order.events".equals(value))
                hasOrderEvents = true;
        }
        assertTrue(hasUserEvents, "Should have indexed user.events channel as structure field");
        assertTrue(hasOrderEvents, "Should have indexed order.events channel as structure field");
    }

    @Test
    void testBuildVersionDocument_InvalidJsonContent() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        metadata.setArtifactType("OPENAPI");
        byte[] content = "not valid json".getBytes(StandardCharsets.UTF_8);

        // When
        Document doc = builder.buildVersionDocument(metadata, content);

        // Then - should not throw exception, just skip structured extraction
        assertNotNull(doc);
        assertEquals("test-artifact", doc.get("artifactId"));
    }

    @Test
    void testBuildVersionDocument_InvalidJsonContent_WithExtractor() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        metadata.setArtifactType("OPENAPI");
        StructuredContentExtractor extractor = new OpenApiStructuredContentExtractor();
        byte[] content = "not valid json".getBytes(StandardCharsets.UTF_8);

        // When
        Document doc = builder.buildVersionDocument(metadata, content, extractor);

        // Then - should not throw exception, no structure fields
        assertNotNull(doc);
        assertEquals("test-artifact", doc.get("artifactId"));
        IndexableField[] structureFields = doc.getFields("structure");
        assertEquals(0, structureFields.length, "Invalid content should produce no structure fields");
    }

    @Test
    void testBuildVersionDocument_NoExtractor() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        metadata.setArtifactType("OPENAPI");

        String openApiContent = """
                {
                  "openapi": "3.0.0",
                  "info": { "title": "Test" },
                  "paths": { "/test": {} }
                }
                """;
        byte[] content = openApiContent.getBytes(StandardCharsets.UTF_8);

        // When - calling without extractor
        Document doc = builder.buildVersionDocument(metadata, content);

        // Then - no structure fields since no extractor was provided
        assertNotNull(doc);
        IndexableField[] structureFields = doc.getFields("structure");
        assertEquals(0, structureFields.length,
                "Without extractor, no structure fields should be indexed");
    }

    @Test
    void testBuildVersionDocument_Timestamps() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);

        long beforeIndex = System.currentTimeMillis();

        // When
        Document doc = builder.buildVersionDocument(metadata, content);

        long afterIndex = System.currentTimeMillis();

        // Then
        assertNotNull(doc.getField("indexedAt"));
        assertNotNull(doc.getField("createdOn"));

        String indexedAtStr = doc.get("indexedAt");
        assertNotNull(indexedAtStr);
        long indexedAt = Long.parseLong(indexedAtStr);
        assertTrue(indexedAt >= beforeIndex && indexedAt <= afterIndex,
                "indexedAt should be between before and after test execution");
    }

    /**
     * Creates test metadata with standard values.
     */
    private ArtifactVersionMetaDataDto createTestMetadata() {
        ArtifactVersionMetaDataDto metadata = new ArtifactVersionMetaDataDto();
        metadata.setGlobalId(123456L);
        metadata.setContentId(789L);
        metadata.setGroupId("test-group");
        metadata.setArtifactId("test-artifact");
        metadata.setVersion("1.0.0");
        metadata.setArtifactType("OPENAPI");
        metadata.setState(VersionState.ENABLED);
        metadata.setName("Test Artifact");
        metadata.setDescription("Test Description");
        metadata.setOwner("test-user");
        metadata.setCreatedOn(System.currentTimeMillis());

        Map<String, String> labels = new HashMap<>();
        labels.put("env", "production");
        labels.put("team", "backend");
        metadata.setLabels(labels);

        return metadata;
    }
}
