package io.apicurio.registry.cli;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
    public void testRoleMappingHelp() {
        testHelpCommand("role-mapping");
        testHelpCommand("role-mapping", "create");
        testHelpCommand("role-mapping", "get");
        testHelpCommand("role-mapping", "update");
        testHelpCommand("role-mapping", "delete");
    }

    // -- Validation / error cases --

    @Test
    public void testCreateMissingArgs() {
        executeAndAssertFailure("role-mapping", "create");
    }

    @Test
    public void testCreateInvalidRole() {
        executeAndAssertFailure("role-mapping", "create", TEST_PRINCIPAL, "INVALID_ROLE");
    }

    @Test
    public void testGetNonExistent() {
        executeAndAssertFailure("role-mapping", "get", "non-existent-principal");
    }

    @Test
    public void testUpdateNonExistent() {
        executeAndAssertFailure("role-mapping", "update", "non-existent-principal",
                "--role", TEST_ROLE);
    }

    @Test
    public void testUpdateInvalidRole() {
        executeAndAssertFailure("role-mapping", "update", TEST_PRINCIPAL,
                "--role", "INVALID_ROLE");
    }

    @Test
    public void testDeleteNonExistent() {
        executeAndAssertFailure("role-mapping", "delete", "non-existent-principal");
    }

    @Test
    @Order(9)
    public void testCreateDuplicate() {
        executeAndAssertSuccess("role-mapping", "create", "dup-user", TEST_ROLE);
        executeAndAssertFailure("role-mapping", "create", "dup-user", TEST_ROLE);
        executeAndAssertSuccess("role-mapping", "delete", "dup-user");
    }

    // -- CRUD flow --

    @Test
    @Order(0)
    public void testListEmpty() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role-mapping");
        assertThat(out.toString())
                .as(withCliOutput("Should indicate no role mappings"))
                .contains("No role mappings found");
    }

    @Test
    @Order(1)
    public void testCreate() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role-mapping", "create", TEST_PRINCIPAL, TEST_ROLE,
                "--name", TEST_NAME);
        assertThat(out.toString())
                .as(withCliOutput("Should confirm creation"))
                .contains("created successfully")
                .contains(TEST_PRINCIPAL)
                .contains(TEST_ROLE)
                .contains(TEST_NAME);
    }

    @Test
    @Order(2)
    public void testList() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role-mapping");
        assertThat(out.toString())
                .as(withCliOutput("Should list the created mapping"))
                .contains(TEST_PRINCIPAL)
                .contains(TEST_ROLE);
    }

    @Test
    @Order(3)
    public void testGet() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role-mapping", "get", TEST_PRINCIPAL);
        assertThat(out.toString())
                .as(withCliOutput("Should show the mapping details"))
                .contains(TEST_PRINCIPAL)
                .contains(TEST_ROLE)
                .contains(TEST_NAME);
    }

    @Test
    @Order(4)
    public void testUpdate() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role-mapping", "update", TEST_PRINCIPAL, "--role", TEST_UPDATED_ROLE);
        assertThat(out.toString())
                .as(withCliOutput("Should confirm update"))
                .contains("updated successfully");
    }

    @Test
    @Order(5)
    public void testGetAfterUpdate() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role-mapping", "get", TEST_PRINCIPAL);
        assertThat(out.toString())
                .as(withCliOutput("Should show updated role"))
                .contains(TEST_PRINCIPAL)
                .contains(TEST_UPDATED_ROLE);
    }

    @Test
    @Order(6)
    public void testDelete() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role-mapping", "delete", TEST_PRINCIPAL);
        assertThat(out.toString())
                .as(withCliOutput("Should confirm deletion"))
                .contains("deleted successfully");
    }

    @Test
    @Order(7)
    public void testListAfterDelete() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role-mapping");
        assertThat(out.toString())
                .as(withCliOutput("Should be empty after delete"))
                .contains("No role mappings found");
    }

    @Test
    @Order(8)
    public void testCreateWithoutName() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("role-mapping", "create", "no-name-user", TEST_ROLE);
        assertThat(out.toString())
                .as(withCliOutput("Should create without name"))
                .contains("created successfully")
                .contains("no-name-user")
                .contains(TEST_ROLE);

        executeAndAssertSuccess("role-mapping", "delete", "no-name-user");
    }
}
