package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.util.MysqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * On MySQL, names, labels and principals hold user text, so they must accept any character, and
 * IDs, which stay ASCII, must answer a non-ASCII lookup with "not found" rather than a database
 * error.
 */
@QuarkusTest
@TestProfile(MysqlTestProfile.class)
public class MysqlUnicodeTextTest extends AbstractResourceTestBase {

    private static final String SCHEMA = "{\"type\":\"string\"}";
    private static final String NAME = "Caf\u00e9 API \u65e5\u672c";

    @Inject
    HandleFactory handles;

    @Inject
    SqlStatements sqlStatements;

    private String createUnicodeArtifact() {
        String group = "unicode-" + UUID.randomUUID();
        given().contentType(CT_JSON).body(Map.of("artifactId", "a1", "artifactType", "JSON", "name", NAME,
                "labels", Map.of("team", "\u00e9quipe"), "firstVersion", Map.of("name", "premi\u00e8re",
                        "content", Map.of("content", SCHEMA, "contentType", CT_JSON))))
                .post(registryV3ApiUrl + "/groups/" + group + "/artifacts").then().statusCode(200);
        return group;
    }

    @Test
    void nonAsciiNamesAndLabelsAreStoredAndFound() {
        String group = createUnicodeArtifact();
        String artifact = registryV3ApiUrl + "/groups/" + group + "/artifacts/a1";

        given().get(artifact).then().statusCode(200)
                .body("name", equalTo(NAME))
                .body("labels.team", equalTo("\u00e9quipe"));
        given().get(artifact + "/versions/1").then().statusCode(200).body("name", equalTo("premi\u00e8re"));
        given().contentType(CT_JSON).body(Map.of("name", "\u00c9dition")).put(artifact).then().statusCode(204);
        given().get(artifact).then().statusCode(200).body("name", equalTo("\u00c9dition"));
    }

    @Test
    void searchingForNonAsciiTextMatchesNamesAndNeverIds() {
        String group = createUnicodeArtifact();
        given().queryParam("groupId", group).queryParam("name", NAME)
                .get(registryV3ApiUrl + "/search/artifacts").then().statusCode(200)
                .body("artifacts.artifactId", contains("a1"));
        given().queryParam("groupId", group).queryParam("name", "premi\u00e8re")
                .get(registryV3ApiUrl + "/search/versions").then().statusCode(200)
                .body("versions.artifactId", contains("a1"));
        given().queryParam("artifactId", "caf\u00e9").get(registryV3ApiUrl + "/search/artifacts").then()
                .statusCode(200).body("artifacts", empty());
        given().queryParam("groupId", "caf\u00e9").get(registryV3ApiUrl + "/search/versions").then()
                .statusCode(200).body("versions", empty());
    }

    @Test
    void nonAsciiIdsAreNotFoundRatherThanDatabaseErrors() {
        for (String path : List.of("/groups/caf\u00e9", "/groups/g/artifacts/caf\u00e9",
                "/groups/g/artifacts/caf\u00e9/versions", "/groups/g/artifacts/a/versions/v\u00e9")) {
            given().get(registryV3ApiUrl + path).then().statusCode(404)
                    .body(not(containsString("SQL")));
        }
        given().get("/ccompat/v7/subjects/caf\u00e9/versions").then().statusCode(404);
        given().contentType(CT_JSON).body(Map.of("name", "x")).put(registryV3ApiUrl
                + "/groups/g/artifacts/caf\u00e9").then().statusCode(400);
    }

    /**
     * Rebuilds the 113 form of the role-mapping table, runs the real 113-to-114 upgrade, and checks
     * every converted column.
     */
    @Test
    void upgradeFrom113KeepsDataAndConvertsTheColumns() {
        String suffix = UUID.randomUUID().toString();
        handles.withHandleNoException(handle -> {
            handle.createUpdate("ALTER TABLE acls MODIFY principalId VARCHAR(256) CHARACTER SET ascii "
                    + "COLLATE ascii_general_ci NOT NULL").execute();
            handle.createUpdate("INSERT INTO acls (principalId, role) VALUES (?, 'sr-admin')")
                    .bind(0, "alice-" + suffix).execute();
            handle.createUpdate("UPDATE apicurio SET propValue = '113' WHERE propName = 'db_version'").execute();
            return null;
        });

        List<String> upgrade = sqlStatements.databaseUpgrade(113, 114);
        handles.withHandleNoException(handle -> {
            upgrade.forEach(statement -> handle.createUpdate(statement).execute());
            return null;
        });

        handles.withHandleNoException(handle -> {
            assertEquals("114", handle.createQuery("SELECT propValue FROM apicurio WHERE propName = 'db_version'")
                    .mapTo(String.class).one());
            for (String column : List.of("groups.owner", "groups.modifiedBy", "artifacts.owner",
                    "artifacts.modifiedBy", "artifacts.name", "artifacts.labels", "versions.name", "versions.owner",
                    "versions.modifiedBy", "versions.labels", "branches.owner", "branches.modifiedBy",
                    "version_comments.owner", "acls.principalId")) {
                boolean principal = column.endsWith("owner") || column.endsWith("modifiedBy")
                        || column.endsWith("principalId");
                assertEquals(principal ? "utf8mb4_0900_as_ci" : "utf8mb4_unicode_ci", collation(handle, column),
                        column);
            }
            // The existing role mapping is kept, and principals still compare without regard to case
            assertEquals(List.of("sr-admin"), roles(handle, "ALICE-" + suffix));
            handle.createUpdate("INSERT INTO acls (principalId, role) VALUES (?, 'sr-developer')")
                    .bind(0, "jos\u00e9-" + suffix).execute();
            assertEquals(List.of("sr-developer"), roles(handle, "jos\u00e9-" + suffix));
            // An unaccented name is a different principal
            assertEquals(List.of(), roles(handle, "jose-" + suffix));
            return null;
        });
    }

    private static String collation(Handle handle, String column) {
        String[] parts = column.split("\\.");
        return handle.createQuery("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = "
                + "DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?").bind(0, parts[0]).bind(1, parts[1])
                .mapTo(String.class).one();
    }

    private static List<String> roles(Handle handle, String principalId) {
        return handle.createQuery("SELECT role FROM acls WHERE principalId = ?").bind(0, principalId)
                .mapTo(String.class).list();
    }
}
