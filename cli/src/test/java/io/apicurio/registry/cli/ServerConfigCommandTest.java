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
public class ServerConfigCommandTest extends AbstractCLITest {

    private static final String TEST_PROPERTY = "apicurio.ccompat.max-subjects";
    private static final String TEST_VALUE = "500";

    // -- Help --

    @Test
    @Order(0)
    public void testServerConfigHelp() {
        testHelpCommand("server-config");
        testHelpCommand("server-config", "get");
        testHelpCommand("server-config", "set");
        testHelpCommand("server-config", "reset");
    }

    // -- CRUD flow --

    @Test
    @Order(1)
    public void testList() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("server-config", "--output-type", "json");
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.isArray())
                .as(withCliOutput("Should return an array"))
                .isTrue();
        assertThat(json.size())
                .as(withCliOutput("Should have properties"))
                .isGreaterThan(0);
    }

    @Test
    @Order(2)
    public void testListTable() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("server-config");
        assertThat(out.toString())
                .as(withCliOutput("Table should have column headers"))
                .contains("Name")
                .contains("Value");
    }

    @Test
    @Order(3)
    public void testGet() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("server-config", "get", "--output-type", "json", TEST_PROPERTY);
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.get("name").asText())
                .as(withCliOutput("Should return the correct property"))
                .isEqualTo(TEST_PROPERTY);
        assertThat(json.has("value"))
                .as(withCliOutput("Should have a value"))
                .isTrue();
    }

    @Test
    @Order(4)
    public void testSet() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("server-config", "set", TEST_PROPERTY, TEST_VALUE);
        assertThat(out.toString())
                .as(withCliOutput("Should confirm property set"))
                .contains("set to '500'");
    }

    @Test
    @Order(5)
    public void testSetJson() throws Exception {
        out.getBuffer().setLength(0);
        err.getBuffer().setLength(0);
        executeAndAssertSuccess("server-config", "set", TEST_PROPERTY, TEST_VALUE,
                "--output-type", "json");
        assertThat(err.toString())
                .as(withCliOutput("Success message should go to stderr for JSON"))
                .contains("set to '500'");
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.get("name").asText())
                .as(withCliOutput("JSON should contain the property name"))
                .isEqualTo(TEST_PROPERTY);
    }

    @Test
    @Order(6)
    public void testGetAfterSet() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("server-config", "get", "--output-type", "json", TEST_PROPERTY);
        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.get("value").asText())
                .as(withCliOutput("Should have the updated value"))
                .isEqualTo(TEST_VALUE);
    }

    @Test
    @Order(7)
    public void testReset() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("server-config", "reset", TEST_PROPERTY);
        String output = out.toString();
        assertThat(output)
                .as(withCliOutput("Should confirm reset"))
                .contains("reset to default");
        assertThat(output)
                .as(withCliOutput("Should show the default value"))
                .contains(TEST_PROPERTY);
    }

    // -- Error cases --

    @Test
    @Order(8)
    public void testGetMissingArg() {
        executeAndAssertFailure("server-config", "get");
    }

    @Test
    @Order(9)
    public void testSetMissingArgs() {
        executeAndAssertFailure("server-config", "set");
    }

    @Test
    @Order(10)
    public void testResetMissingArg() {
        executeAndAssertFailure("server-config", "reset");
    }

    @Test
    @Order(11)
    public void testGetNonExistent() {
        executeAndAssertFailure("server-config", "get", "non.existent.property");
    }
}
