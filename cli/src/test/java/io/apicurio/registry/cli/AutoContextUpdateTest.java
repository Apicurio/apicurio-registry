package io.apicurio.registry.cli;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@code context.auto-update} configuration property, which automatically saves
 * the group/artifact ID to the current context after group and artifact create/get commands.
 */
@QuarkusTest
public class AutoContextUpdateTest extends AbstractCLITest {

    @AfterEach
    public void disableAutoContextUpdate() {
        executeAndAssertSuccess("config", "set", "context.auto-update=false");
    }

    @Test
    public void testDisabledByDefaultOnGroupCreate() {
        executeAndAssertSuccess("group", "create", "actx-disabled-group");

        var context = config.read().getContext().get("test");
        assertThat(context.getGroupId())
                .as(withCliOutput("Context should not be updated when auto-update is disabled"))
                .isNull();
    }

    @Test
    public void testDisabledByDefaultOnArtifactCreate() throws Exception {
        var tempFile = writeTempSchema("actx-disabled-schema");
        try {
            executeAndAssertSuccess("artifact", "create", "--group", "default", "--type", "JSON",
                    "--file", tempFile.toString(), "actx-disabled-artifact");

            var context = config.read().getContext().get("test");
            assertThat(context.getArtifactId())
                    .as(withCliOutput("Context should not be updated when auto-update is disabled"))
                    .isNull();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testGroupCreateUpdatesContext() {
        executeAndAssertSuccess("config", "set", "context.auto-update=true");

        executeAndAssertSuccess("group", "create", "actx-group-create");

        var context = config.read().getContext().get("test");
        assertThat(context.getGroupId())
                .as(withCliOutput("Context group ID should be updated after group create"))
                .isEqualTo("actx-group-create");
    }

    @Test
    public void testGroupGetUpdatesContext() {
        executeAndAssertSuccess("group", "create", "actx-group-get");
        executeAndAssertSuccess("config", "set", "context.auto-update=true");

        executeAndAssertSuccess("group", "get", "actx-group-get");

        var context = config.read().getContext().get("test");
        assertThat(context.getGroupId())
                .as(withCliOutput("Context group ID should be updated after group get"))
                .isEqualTo("actx-group-get");
    }

    @Test
    public void testArtifactCreateUpdatesContext() throws Exception {
        executeAndAssertSuccess("config", "set", "context.auto-update=true");
        var tempFile = writeTempSchema("actx-create-schema");
        try {
            executeAndAssertSuccess("artifact", "create", "--group", "default", "--type", "JSON",
                    "--file", tempFile.toString(), "actx-artifact-create");

            var context = config.read().getContext().get("test");
            assertThat(context.getGroupId())
                    .as(withCliOutput("Context group ID should be updated after artifact create"))
                    .isEqualTo("default");
            assertThat(context.getArtifactId())
                    .as(withCliOutput("Context artifact ID should be updated after artifact create"))
                    .isEqualTo("actx-artifact-create");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testArtifactGetUpdatesContext() throws Exception {
        var tempFile = writeTempSchema("actx-get-schema");
        try {
            executeAndAssertSuccess("artifact", "create", "--group", "default", "--type", "JSON",
                    "--file", tempFile.toString(), "actx-artifact-get");
            executeAndAssertSuccess("config", "set", "context.auto-update=true");

            executeAndAssertSuccess("artifact", "get", "--group", "default", "actx-artifact-get");

            var context = config.read().getContext().get("test");
            assertThat(context.getGroupId())
                    .as(withCliOutput("Context group ID should be updated after artifact get"))
                    .isEqualTo("default");
            assertThat(context.getArtifactId())
                    .as(withCliOutput("Context artifact ID should be updated after artifact get"))
                    .isEqualTo("actx-artifact-get");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testGroupChangeClearsArtifactFromContext() {
        executeAndAssertSuccess("group", "create", "actx-group-a");
        executeAndAssertSuccess("group", "create", "actx-group-b");
        executeAndAssertSuccess("context", "update", "--group", "actx-group-a", "--artifact", "stale-artifact");
        executeAndAssertSuccess("config", "set", "context.auto-update=true");

        executeAndAssertSuccess("group", "get", "actx-group-b");

        var context = config.read().getContext().get("test");
        assertThat(context.getGroupId())
                .as(withCliOutput("Context group ID should reflect the newly retrieved group"))
                .isEqualTo("actx-group-b");
        assertThat(context.getArtifactId())
                .as(withCliOutput("Switching to a different group should clear the stale artifact ID"))
                .isNull();
    }

    @Test
    public void testSameGroupGetPreservesArtifactInContext() {
        executeAndAssertSuccess("group", "create", "actx-group-same");
        executeAndAssertSuccess("context", "update", "--group", "actx-group-same", "--artifact", "kept-artifact");
        executeAndAssertSuccess("config", "set", "context.auto-update=true");

        executeAndAssertSuccess("group", "get", "actx-group-same");

        var context = config.read().getContext().get("test");
        assertThat(context.getArtifactId())
                .as(withCliOutput("Re-getting the same group should not clear the artifact ID"))
                .isEqualTo("kept-artifact");
    }

    private Path writeTempSchema(String prefix) throws Exception {
        var tempFile = Files.createTempFile(prefix, ".json");
        Files.writeString(tempFile, "{\"type\": \"string\"}");
        return tempFile;
    }
}
