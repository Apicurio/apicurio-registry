package io.apicurio.registry.cli;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class RoleMappingCommandTest extends AbstractCLITest {

    private static final String TEST_PRINCIPAL = "test-user";
    private static final String TEST_ROLE = "DEVELOPER";
    private static final String TEST_UPDATED_ROLE = "ADMIN";
    private static final String TEST_NAME = "Test User";

    // -- Help --

    @Test
    public void testRoleHelp() {
        testHelpCommand("role");
        testHelpCommand("role", "create");
        testHelpCommand("role", "get");
        testHelpCommand("role", "update");
        testHelpCommand("role", "delete");
    }

    // -- Validation / error cases --

    @Test
    public void testCreateMissingArgs() {
        executeAndAssertFailure("role", "create");
    }

    @Test
    public void testCreateInvalidRole() {
        executeAndAssertFailure("role", "create", TEST_PRINCIPAL, "INVALID_ROLE");
    }

    @Test
    public void testGetNonExistent() {
        executeAndAssertFailure("role", "get", "non-existent-principal");
    }

    @Test
    public void testUpdateNonExistent() {
        executeAndAssertFailure("role", "update", "non-existent-principal",
                "--role", TEST_ROLE);
    }

    @Test
    public void testUpdateInvalidRole() {
        executeAndAssertFailure("role", "update", TEST_PRINCIPAL,
                "--role", "INVALID_ROLE");
    }

    @Test
    public void testDeleteNonExistent() {
        executeAndAssertFailure("role", "delete", "non-existent-principal");
    }

    @Test
    @Order(9)
    public void testCreateDuplicate() {
        executeAndAssertSuccess("role", "create", "dup-user", TEST_ROLE);
        executeAndAssertFailure("role", "create", "dup-user", TEST_ROLE);
        executeAndAssertSuccess("role", "delete", "dup-user");
    }

    // -- CRUD flow --

    @Test
    @Order(0)
    public void testListEmpty() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role", "--output-type", "json");
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.isArray())
                .as(withCliOutput("Should be an array"))
                .isTrue();
        assertThat(json.size())
                .as(withCliOutput("Should be empty"))
                .isZero();
    }

    @Test
    @Order(1)
    public void testCreate() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role", "create", "--output-type", "json",
                TEST_PRINCIPAL, TEST_ROLE, "--name", TEST_NAME);
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.get("principalId").asText())
                .as(withCliOutput("Should have correct principal"))
                .isEqualTo(TEST_PRINCIPAL);
        assertThat(json.get("role").asText())
                .as(withCliOutput("Should have correct role"))
                .isEqualTo(TEST_ROLE);
        assertThat(json.get("principalName").asText())
                .as(withCliOutput("Should have correct name"))
                .isEqualTo(TEST_NAME);
    }

    @Test
    @Order(2)
    public void testList() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role", "--output-type", "json");
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.size())
                .as(withCliOutput("Should have one mapping"))
                .isEqualTo(1);
        assertThat(json.get(0).get("principalId").asText()).isEqualTo(TEST_PRINCIPAL);
    }

    @Test
    @Order(3)
    public void testGet() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role", "get", "--output-type", "json", TEST_PRINCIPAL);
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.get("principalId").asText()).isEqualTo(TEST_PRINCIPAL);
        assertThat(json.get("role").asText()).isEqualTo(TEST_ROLE);
        assertThat(json.get("principalName").asText()).isEqualTo(TEST_NAME);
    }

    @Test
    @Order(4)
    public void testUpdate() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role", "update", "--output-type", "json",
                TEST_PRINCIPAL, "--role", TEST_UPDATED_ROLE);
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.get("role").asText())
                .as(withCliOutput("Should have updated role"))
                .isEqualTo(TEST_UPDATED_ROLE);
    }

    @Test
    @Order(5)
    public void testGetAfterUpdate() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role", "get", "--output-type", "json", TEST_PRINCIPAL);
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.get("role").asText()).isEqualTo(TEST_UPDATED_ROLE);
    }

    @Test
    @Order(6)
    public void testDelete() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role", "delete", TEST_PRINCIPAL);
        assertThat(out.toString())
                .as(withCliOutput("Should confirm deletion"))
                .contains("deleted successfully");
    }

    @Test
    @Order(7)
    public void testListAfterDelete() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role", "--output-type", "json");
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.size())
                .as(withCliOutput("Should be empty after delete"))
                .isZero();
    }

    @Test
    @Order(8)
    public void testCreateWithoutName() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role", "create", "no-name-user", TEST_ROLE);
        assertThat(out.toString())
                .as(withCliOutput("Should create without name"))
                .contains("created successfully");

        executeAndAssertSuccess("role", "delete", "no-name-user");
    }
}
