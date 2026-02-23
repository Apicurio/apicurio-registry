package io.apicurio.registry.storage.impl.search;

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
    void testBuildVersionDocument_OpenApiContent() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        metadata.setArtifactType("OPENAPI");

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
        Document doc = builder.buildVersionDocument(metadata, content);

        // Then
        IndexableField[] pathFields = doc.getFields("openapi_path");
        assertTrue(pathFields.length >= 1, "Should have indexed OpenAPI paths");

        // Check that paths were indexed
        boolean hasUsersPath = false;
        boolean hasOrdersPath = false;
        for (IndexableField field : pathFields) {
            String value = field.stringValue();
            if ("/users".equals(value))
                hasUsersPath = true;
            if ("/orders".equals(value))
                hasOrdersPath = true;
        }
        assertTrue(hasUsersPath, "Should have indexed /users path");
        assertTrue(hasOrdersPath, "Should have indexed /orders path");
    }

    @Test
    void testBuildVersionDocument_AsyncApiContent() {
        // Given
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        metadata.setArtifactType("ASYNCAPI");

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
        Document doc = builder.buildVersionDocument(metadata, content);

        // Then
        IndexableField[] channelFields = doc.getFields("asyncapi_channel");
        assertTrue(channelFields.length >= 1, "Should have indexed AsyncAPI channels");

        // Check that channels were indexed
        boolean hasUserEvents = false;
        boolean hasOrderEvents = false;
        for (IndexableField field : channelFields) {
            String value = field.stringValue();
            if ("user.events".equals(value))
                hasUserEvents = true;
            if ("order.events".equals(value))
                hasOrderEvents = true;
        }
        assertTrue(hasUserEvents, "Should have indexed user.events channel");
        assertTrue(hasOrderEvents, "Should have indexed order.events channel");
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
