package io.apicurio.registry.cli;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class ContextCommandTest extends AbstractCLITest {

    @Test
    public void testContextHelp() {
        testHelpCommand("context");
        testHelpCommand("context", "create");
        testHelpCommand("context", "update");
        testHelpCommand("context", "delete");
    }

    @Test
    public void testContextUpdateNoOptions() {
        executeAndAssertFailure("context", "update");
    }

    @Test
    public void testContextUpdateNonExistent() {
        executeAndAssertFailure("context", "update", "--group", "new-group", "non-existent");
    }

    @Test
    public void testContextUpdateEmptyRegistryUrl() {
        executeAndAssertFailure("context", "update", "--registry-url", "");
    }

    @Test
    public void testContextUpdateGroupId() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("context", "update", "--group", "updated-group");

        // Verify by listing contexts
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("context");
        assertThat(out.toString())
                .as(withCliOutput("Context should show updated group"))
                .contains("updated-group");
    }

    @Test
    public void testContextUpdateArtifactId() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("context", "update", "--artifact", "updated-artifact");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("context");
        assertThat(out.toString())
                .as(withCliOutput("Context should show updated artifact"))
                .contains("updated-artifact");
    }

    @Test
    public void testContextUpdateRegistryUrl() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("context", "update", "--registry-url", "http://new-url:8080/apis/registry/v3");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("context");
        assertThat(out.toString())
                .as(withCliOutput("Context should show updated registry URL"))
                .contains("new-url:8080");
    }

    @Test
    public void testContextUpdateByName() {
        // Create a second context
        executeAndAssertSuccess("context", "create", "--no-switch-current", "second", registryUrl);

        // Update the second context by name
        executeAndAssertSuccess("context", "update", "--group", "named-group", "second");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("context");
        assertThat(out.toString())
                .as(withCliOutput("Named context should show updated group"))
                .contains("named-group");
    }
}
