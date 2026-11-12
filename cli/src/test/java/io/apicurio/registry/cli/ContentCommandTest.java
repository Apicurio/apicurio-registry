package io.apicurio.registry.cli;

import tools.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;

import static io.apicurio.registry.cli.common.CliException.SERVER_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class ContentCommandTest extends AbstractCLITest {

    private static final String TEST_GROUP = "content-test-group";
    private static final String TEST_ARTIFACT = "content-test-artifact";
    private static final String TEST_CONTENT = "{\"type\": \"string\"}";

    // -- Help --

    @Test
    @Order(0)
    public void testContentHelp() {
        testHelpCommand("content");
    }

    // -- Setup --

    @Test
    @Order(1)
    public void testSetup() throws Exception {
        executeAndAssertSuccess("group", "create", TEST_GROUP);
        final Path tempFile = Files.createTempFile("content-test", ".json");
        Files.writeString(tempFile, TEST_CONTENT);
        try {
            assertThat(cmd.execute("artifact", "create", "-g", TEST_GROUP,
                    "--type", "JSON", "--file", tempFile.toString(), TEST_ARTIFACT))
                    .as(withCliOutput("Artifact creation should succeed"))
                    .isZero();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // -- Retrieval --

    @Test
    @Order(2)
    public void testGetByGlobalId() throws Exception {
        final long globalId = getVersionField("globalId");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("content", "--global-id", String.valueOf(globalId));
        assertThat(out.toString().trim())
                .as(withCliOutput("Should return artifact content"))
                .isEqualTo(TEST_CONTENT);
    }

    @Test
    @Order(3)
    public void testGetByContentId() throws Exception {
        final long contentId = getVersionField("contentId");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("content", "--content-id", String.valueOf(contentId));
        assertThat(out.toString().trim())
                .as(withCliOutput("Should return artifact content"))
                .isEqualTo(TEST_CONTENT);
    }

    @Test
    @Order(4)
    public void testGetByHash() throws Exception {
        final String hash = getContentHash();

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("content", "--hash", hash);
        assertThat(out.toString().trim())
                .as(withCliOutput("Should return artifact content"))
                .isEqualTo(TEST_CONTENT);
    }

    // -- JSON output --

    @Test
    @Order(5)
    public void testGetByGlobalIdJson() throws Exception {
        final long globalId = getVersionField("globalId");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("content", "--global-id", String.valueOf(globalId), "-o", "json");
        final JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.has("globalId"))
                .as(withCliOutput("JSON output should echo back the globalId"))
                .isTrue();
        assertThat(json.get("globalId").asLong())
                .as(withCliOutput("JSON globalId should match queried ID"))
                .isEqualTo(globalId);
        assertThat(json.get("content").asString())
                .as(withCliOutput("JSON content field should match artifact content"))
                .isEqualTo(TEST_CONTENT);
    }

    // -- Error cases --

    @Test
    @Order(6)
    public void testContentNoArgs() {
        assertThat(cmd.execute("content"))
                .as(withCliOutput("Command should fail with the expected exit code"))
                .isEqualTo(VALIDATION_ERROR_RETURN_CODE);
    }

    @Test
    @Order(7)
    public void testContentMultipleArgs() {
        assertThat(cmd.execute("content", "--global-id", "1", "--content-id", "1"))
                .as(withCliOutput("Command should fail with the expected exit code"))
                .isEqualTo(VALIDATION_ERROR_RETURN_CODE);
    }

    @Test
    @Order(8)
    public void testNegativeGlobalId() {
        assertThat(cmd.execute("content", "--global-id", "-1"))
                .as(withCliOutput("Command should fail with the expected exit code"))
                .isEqualTo(VALIDATION_ERROR_RETURN_CODE);
    }

    @Test
    @Order(9)
    public void testZeroContentId() {
        assertThat(cmd.execute("content", "--content-id", "0"))
                .as(withCliOutput("Command should fail with the expected exit code"))
                .isEqualTo(VALIDATION_ERROR_RETURN_CODE);
    }

    @Test
    @Order(10)
    public void testGetByNonExistentGlobalId() {
        assertThat(cmd.execute("content", "--global-id", "999999999"))
                .as(withCliOutput("Command should fail with the expected exit code"))
                .isEqualTo(SERVER_ERROR_RETURN_CODE);
    }

    @Test
    @Order(11)
    public void testGetByNonExistentContentId() {
        assertThat(cmd.execute("content", "--content-id", "999999999"))
                .as(withCliOutput("Command should fail with the expected exit code"))
                .isEqualTo(SERVER_ERROR_RETURN_CODE);
    }

    @Test
    @Order(12)
    public void testGetByNonExistentHash() {
        assertThat(cmd.execute("content", "--hash", "0".repeat(64)))
                .as(withCliOutput("Command should fail with the expected exit code"))
                .isEqualTo(SERVER_ERROR_RETURN_CODE);
    }

    // -- Helpers --

    private long getVersionField(final String fieldName) throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "get", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--output-type", "json", "1");
        final JsonNode json = MAPPER.readTree(out.toString());
        return json.get(fieldName).asLong();
    }

    private String getContentHash() throws Exception {
        final long globalId = getVersionField("globalId");
        final byte[] bytes;
        try (final var content = client.getRegistryClient().ids().globalIds().byGlobalId(globalId).get()) {
            bytes = content.readAllBytes();
        }
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] hash = digest.digest(bytes);
        final StringBuilder sb = new StringBuilder();
        for (final byte b : hash) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
