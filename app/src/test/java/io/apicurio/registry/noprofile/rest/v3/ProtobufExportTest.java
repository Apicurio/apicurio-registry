package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.DeletionEnabledProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Protobuf export endpoint that exports artifacts with their
 * transitive dependencies as a ZIP file with package-structured directories.
 */
@QuarkusTest
@TestProfile(DeletionEnabledProfile.class)
public class ProtobufExportTest extends AbstractResourceTestBase {

    private static final String GROUP = "ProtobufExportTest";

    // Proto files for testing
    private static final String MODE_PROTO = """
            syntax = "proto3";
            package sample;

            enum Mode {
              MODE_UNKNOWN = 0;
              RAW = 1;
              MERGE = 2;
            }
            """;

    private static final String TABLE_INFO_PROTO = """
            syntax = "proto3";
            package sample;

            import "mode.proto";

            message TableInfo {
              int32 winIndex = 1;
              Mode mode = 2;
              string id = 3;
            }
            """;

    private static final String TABLE_NOTIFICATION_PROTO = """
            syntax = "proto3";
            package sample;

            import "table_info.proto";

            message TableNotification {
              string user = 1;
              TableInfo table_info = 2;
            }
            """;

    private static final String SIMPLE_PROTO = """
            syntax = "proto3";
            package com.example.api;

            message SimpleMessage {
              string name = 1;
              int32 id = 2;
            }
            """;

    private static final String NESTED_PACKAGE_PROTO = """
            syntax = "proto3";
            package com.example.nested.deep;

            message DeepMessage {
              string value = 1;
            }
            """;

    @Test
    public void testExportSingleArtifactWithoutReferences() throws Exception {
        String artifactId = "simple-message";

        // Create a simple protobuf artifact without references
        createArtifact(GROUP, artifactId, ArtifactType.PROTOBUF, SIMPLE_PROTO, ContentTypes.APPLICATION_PROTOBUF);

        // Export the artifact
        byte[] zipBytes = given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "branch=latest")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/export")
                .then()
                .statusCode(200)
                .contentType("application/zip")
                .extract()
                .asByteArray();

        // Verify the ZIP contents
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            assertNotNull(entry, "ZIP should contain at least one entry");

            // The file should be at com/example/api/simple-message.proto
            assertEquals("com/example/api/simple-message.proto", entry.getName());

            // Read and verify content
            String content = new String(zis.readAllBytes());
            assertTrue(content.contains("package com.example.api;"));
            assertTrue(content.contains("message SimpleMessage"));
        }
    }

    @Test
    public void testExportArtifactWithReferences() throws Exception {
        // Create the dependency first (mode.proto)
        String modeArtifactId = "mode-enum";
        createArtifact(GROUP, modeArtifactId, ArtifactType.PROTOBUF, MODE_PROTO, ContentTypes.APPLICATION_PROTOBUF);

        // Create the main artifact with a reference to mode.proto
        String tableInfoArtifactId = "table-info";

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(tableInfoArtifactId);
        createArtifact.setArtifactType(ArtifactType.PROTOBUF);

        CreateVersion firstVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(TABLE_INFO_PROTO);
        content.setContentType(ContentTypes.APPLICATION_PROTOBUF);

        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(GROUP);
        ref.setArtifactId(modeArtifactId);
        ref.setVersion("1");
        ref.setName("mode.proto");
        content.setReferences(List.of(ref));

        firstVersion.setContent(content);
        createArtifact.setFirstVersion(firstVersion);

        clientV3.groups().byGroupId(GROUP).artifacts().post(createArtifact);

        // Export the artifact with its references
        byte[] zipBytes = given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", tableInfoArtifactId)
                .pathParam("versionExpression", "branch=latest")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/export")
                .then()
                .statusCode(200)
                .contentType("application/zip")
                .extract()
                .asByteArray();

        // Verify the ZIP contains both files with correct package structure
        int fileCount = 0;
        boolean foundMain = false;
        boolean foundDependency = false;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                String entryName = entry.getName();

                if (entryName.endsWith("table-info.proto")) {
                    foundMain = true;
                    // Should be at sample/table-info.proto
                    assertEquals("sample/table-info.proto", entryName);

                    // Verify the import is rewritten to use package path
                    String mainContent = new String(zis.readAllBytes());
                    assertTrue(mainContent.contains("package sample;"));
                    // Import should be rewritten to sample/mode.proto
                    assertTrue(mainContent.contains("import \"sample/mode.proto\"")
                               || mainContent.contains("import \"sample/mode-enum.proto\""),
                            "Import should be rewritten to use package path. Content: " + mainContent);
                }

                if (entryName.contains("mode")) {
                    foundDependency = true;
                    // Should be at sample/mode.proto or sample/mode-enum.proto
                    assertTrue(entryName.startsWith("sample/"), "Dependency should be in sample/ directory");
                }
            }
        }

        assertEquals(2, fileCount, "ZIP should contain exactly 2 files");
        assertTrue(foundMain, "ZIP should contain the main artifact");
        assertTrue(foundDependency, "ZIP should contain the dependency");
    }

    @Test
    public void testExportArtifactWithTransitiveReferences() throws Exception {
        // Create mode.proto (no dependencies)
        String modeArtifactId = "transitive-mode";
        createArtifact(GROUP, modeArtifactId, ArtifactType.PROTOBUF, MODE_PROTO, ContentTypes.APPLICATION_PROTOBUF);

        // Create table_info.proto (depends on mode.proto)
        String tableInfoArtifactId = "transitive-table-info";
        CreateArtifact createTableInfo = new CreateArtifact();
        createTableInfo.setArtifactId(tableInfoArtifactId);
        createTableInfo.setArtifactType(ArtifactType.PROTOBUF);

        CreateVersion tableInfoVersion = new CreateVersion();
        VersionContent tableInfoContent = new VersionContent();
        tableInfoContent.setContent(TABLE_INFO_PROTO);
        tableInfoContent.setContentType(ContentTypes.APPLICATION_PROTOBUF);

        ArtifactReference modeRef = new ArtifactReference();
        modeRef.setGroupId(GROUP);
        modeRef.setArtifactId(modeArtifactId);
        modeRef.setVersion("1");
        modeRef.setName("mode.proto");
        tableInfoContent.setReferences(List.of(modeRef));

        tableInfoVersion.setContent(tableInfoContent);
        createTableInfo.setFirstVersion(tableInfoVersion);

        clientV3.groups().byGroupId(GROUP).artifacts().post(createTableInfo);

        // Create table_notification.proto (depends on table_info.proto)
        String notificationArtifactId = "transitive-notification";
        CreateArtifact createNotification = new CreateArtifact();
        createNotification.setArtifactId(notificationArtifactId);
        createNotification.setArtifactType(ArtifactType.PROTOBUF);

        CreateVersion notificationVersion = new CreateVersion();
        VersionContent notificationContent = new VersionContent();
        notificationContent.setContent(TABLE_NOTIFICATION_PROTO);
        notificationContent.setContentType(ContentTypes.APPLICATION_PROTOBUF);

        ArtifactReference tableInfoRef = new ArtifactReference();
        tableInfoRef.setGroupId(GROUP);
        tableInfoRef.setArtifactId(tableInfoArtifactId);
        tableInfoRef.setVersion("1");
        tableInfoRef.setName("table_info.proto");
        notificationContent.setReferences(List.of(tableInfoRef));

        notificationVersion.setContent(notificationContent);
        createNotification.setFirstVersion(notificationVersion);

        clientV3.groups().byGroupId(GROUP).artifacts().post(createNotification);

        // Export the top-level artifact - should include all transitive dependencies
        byte[] zipBytes = given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", notificationArtifactId)
                .pathParam("versionExpression", "branch=latest")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/export")
                .then()
                .statusCode(200)
                .contentType("application/zip")
                .extract()
                .asByteArray();

        // Verify the ZIP contains all 3 files
        int fileCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                // All files should be in sample/ directory
                assertTrue(entry.getName().startsWith("sample/"),
                        "All files should be in sample/ directory, but got: " + entry.getName());
            }
        }

        assertEquals(3, fileCount, "ZIP should contain all 3 files (main + 2 transitive dependencies)");
    }

    @Test
    public void testExportNestedPackageStructure() throws Exception {
        String artifactId = "deeply-nested-message";

        // Create an artifact with deeply nested package
        createArtifact(GROUP, artifactId, ArtifactType.PROTOBUF, NESTED_PACKAGE_PROTO, ContentTypes.APPLICATION_PROTOBUF);

        byte[] zipBytes = given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "branch=latest")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/export")
                .then()
                .statusCode(200)
                .contentType("application/zip")
                .extract()
                .asByteArray();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            assertNotNull(entry);

            // Should have deeply nested directory structure
            assertEquals("com/example/nested/deep/deeply-nested-message.proto", entry.getName());
        }
    }

    @Test
    public void testExportNonProtobufArtifactReturns400() throws Exception {
        String jsonSchema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" }
                  }
                }
                """;

        String artifactId = "json-schema-artifact";
        createArtifact(GROUP, artifactId, "JSON", jsonSchema, ContentTypes.APPLICATION_JSON);

        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "branch=latest")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/export")
                .then()
                .statusCode(400);
    }

    @Test
    public void testExportNonExistentArtifactReturns404() throws Exception {
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "non-existent-artifact")
                .pathParam("versionExpression", "1")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/export")
                .then()
                .statusCode(404);
    }

    @Test
    public void testExportArtifactWithoutPackage() throws Exception {
        // Proto without package declaration should go to root
        String noPackageProto = """
                syntax = "proto3";

                message RootMessage {
                  string value = 1;
                }
                """;

        String artifactId = "no-package-message";
        createArtifact(GROUP, artifactId, ArtifactType.PROTOBUF, noPackageProto, ContentTypes.APPLICATION_PROTOBUF);

        byte[] zipBytes = given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "branch=latest")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/export")
                .then()
                .statusCode(200)
                .contentType("application/zip")
                .extract()
                .asByteArray();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            assertNotNull(entry);

            // Without package, file should be at root
            assertEquals("no-package-message.proto", entry.getName());
        }
    }
}
