package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.rest.v3.beans.GroupMetaData;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.SearchedGroup;
import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic smoke tests for the Apicurio Registry CLI.
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class GroupCommandTest extends AbstractCLITest {

    public final Logger log = Logger.getLogger(GroupCommandTest.class);

    @Test
    public void testGroupHelp() {
        testHelpCommand("group");
        testHelpCommand("group", "create");
        testHelpCommand("group", "get");
        testHelpCommand("group", "delete");
    }

    @Test
    @Order(0)
    public void testGroupCommandEmpty() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "--output-type", "json");
        var groups = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        // Then
        assertThat(groups.getGroups())
                .as(withCliOutput("There should not be any groups initially (`default` group is hidden)."))
                .isEmpty();
    }

    @Test
    @Order(1)
    public void testGroupCreateCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "create", "--output-type", "json",
                "--description", "Test group",
                "--label", "env=test",
                "--label", "color=pink",
                "first");
        var group = MAPPER.readValue(out.toString(), GroupMetaData.class);

        // Then
        assertThat(group.getGroupId())
                .as(withCliOutput("Created group should have the correct groupId"))
                .isEqualTo("first");
        assertThat(group.getDescription())
                .as(withCliOutput("Created group should have the correct description"))
                .isEqualTo("Test group");
        assertThat(group.getLabels())
                .as(withCliOutput("Created group should have the correct labels"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("env", "test", "color", "pink"));
    }

    @Test
    public void testGroupCreateCommandFails() {
        // Required groupId parameter is missing
        executeAndAssertFailure("group", "create", "--output-type", "json");
        // Unknown output type
        executeAndAssertFailure("group", "create", "--output-type", "foo");
    }

    @Test
    @Order(2)
    public void testGroupCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "--output-type", "json");
        var groups = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        // Then
        assertThat(groups.getGroups())
                .as(withCliOutput("There should be the one group we have just created (`default` group is hidden)."))
                .hasSize(1);
        assertThat(groups.getGroups())
                .as(withCliOutput("Created group should have the correct groupId"))
                .first()
                .extracting(SearchedGroup::getGroupId)
                .isEqualTo("first");

        // And when
        executeAndAssertSuccess("group", "create", "second");
        executeAndAssertSuccess("group", "create", "third");

        // Then
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "--output-type", "json", "-p", "2", "-s", "2");
        groups = MAPPER.readValue(out.toString(), GroupSearchResults.class);
        assertThat(groups.getGroups())
                .as(withCliOutput("There should be one group on the second page."))
                .hasSize(1);
    }

    @Test
    public void testGroupGetCommandFails() {
        // Unknown output type
        executeAndAssertFailure("group", "create", "--output-type", "foo");
        // Page must be greater than 0
        executeAndAssertFailure("group", "create", "-p", "-1");
        // Size must be greater than 0
        executeAndAssertFailure("group", "create", "-s", "0");
    }

    @Test
    @Order(3)
    public void testGroupDeleteCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "--output-type", "json");
        var groups = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        // Then
        assertThat(groups.getGroups())
                .as(withCliOutput("There should be three groups before deletion."))
                .hasSize(3);

        // When
        executeAndAssertSuccess("group", "delete", "second");

        // Then
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "--output-type", "json");
        groups = MAPPER.readValue(out.toString(), GroupSearchResults.class);
        assertThat(groups.getGroups())
                .as(withCliOutput("There should be two groups after deletion."))
                .hasSize(2);

        // TODO: Test `--force` when we have a way to create artifacts via the CLI.
    }

    /**
     * Issue #8025: the default group is implicit — the server reserves the name, never stores a row for it,
     * and reports it as a null group ID. The CLI must present it as "default" instead of leaking a
     * GroupNotFoundException, an NPE, or a raw "reserved" error from the server.
     */
    @Test
    @Order(4)
    public void testGroupGetDefault() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "get", "default", "--output-type", "json");
        var group = MAPPER.readValue(out.toString(), GroupMetaData.class);

        // Then
        assertThat(group.getGroupId())
                .as(withCliOutput("`group get default` should succeed and report the group ID as 'default'."))
                .isEqualTo("default");
    }

    @Test
    @Order(5)
    public void testGroupGetDefaultTableOutput() {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "get", "default");

        // Then
        assertThat(out.toString())
                .as(withCliOutput("The default group's table output should render, with empty metadata fields."))
                .contains("default");
    }

    @Test
    @Order(6)
    public void testGroupGetEmptyGroupIdIsTreatedAsDefault() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "get", "", "--output-type", "json");
        var group = MAPPER.readValue(out.toString(), GroupMetaData.class);

        // Then
        assertThat(group.getGroupId())
                .as(withCliOutput("`group get \"\"` should resolve to the default group, not throw an NPE."))
                .isEqualTo("default");
    }

    @Test
    @Order(7)
    public void testGroupCreateDefaultFailsGracefully() {
        // When
        err.getBuffer().setLength(0);
        executeAndAssertFailure("group", "create", "default");

        // Then
        assertThat(err.toString())
                .as(withCliOutput("Creating the default group should fail with a clear CLI message."))
                .contains("implicit")
                .doesNotContain("BadRequestException");
    }

    @Test
    @Order(8)
    public void testGroupDeleteDefaultFailsGracefully() {
        // When
        err.getBuffer().setLength(0);
        executeAndAssertFailure("group", "delete", "default");

        // Then
        assertThat(err.toString())
                .as(withCliOutput("Deleting the default group should fail with a clear CLI message."))
                .contains("implicit")
                .doesNotContain("GroupNotFoundException");
    }

    @Test
    @Order(9)
    public void testDisplayGroupIdHandlesNullFromServer() {
        // The server returns a null group ID for the default group in search results; show "default".
        assertThat(IdUtil.displayGroupId(null)).isEqualTo("default");
        assertThat(IdUtil.displayGroupId("")).isEqualTo("default");
        assertThat(IdUtil.displayGroupId("myGroup")).isEqualTo("myGroup");
    }
}
