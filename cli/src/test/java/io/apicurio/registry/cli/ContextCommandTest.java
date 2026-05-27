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
        testHelpCommand("context", "use");
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

    @Test
    public void testContextUse() {
        // Create a second context
        executeAndAssertSuccess("context", "create", "--no-switch-current", "second", registryUrl);

        // Switch to the second context
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("context", "use", "second");
        assertThat(out.toString())
                .as(withCliOutput("Should confirm context switch with previous context"))
                .contains("Switched to context 'second'")
                .contains("from 'test'");

        // Verify it is now the active context
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("context");
        assertThat(out.toString())
                .as(withCliOutput("Second context should be active"))
                .contains("second*");
    }

    @Test
    public void testContextUseAlreadyCurrent() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("context", "use", "test");
        assertThat(out.toString())
                .as(withCliOutput("Should indicate context is already active"))
                .contains("already the current context");
    }

    @Test
    public void testContextUseNonExistent() {
        executeAndAssertFailure("context", "use", "non-existent");
    }
}
