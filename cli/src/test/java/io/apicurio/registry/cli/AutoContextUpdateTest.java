package io.apicurio.registry.cli;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class AutoContextUpdateTest extends AbstractCLITest {

    @AfterEach
    public void cleanup() {
        var configModel = config.read();
        if (configModel.getConfig().containsKey("auto-context-update")) {
            configModel.getConfig().remove("auto-context-update");
            config.write(configModel);
        }
    }

    @Test
    public void testAutoContextUpdateDisabledByDefault() {
        // Initially auto-context-update should not be set (disabled by default)
        var autoUpdate = config.read().getConfig().get("auto-context-update");
        assertThat(autoUpdate).isNull();

        // Create a group
        executeAndAssertSuccess("group", "create", "disabled-group-1");

        // Verify context does not contain group ID
        var context = config.read().getContext().get("test");
        assertThat(context).isNotNull();
        assertThat(context.getGroupId()).isNull();

        // Get the group
        executeAndAssertSuccess("group", "get", "disabled-group-1");

        // Verify context still does not contain group ID
        context = config.read().getContext().get("test");
        assertThat(context.getGroupId()).isNull();
    }

    @Test
    public void testAutoContextUpdateEnabledGroup() {
        // Enable auto-context-update
        executeAndAssertSuccess("config", "set", "auto-context-update=true");

        // Create group-1
        executeAndAssertSuccess("group", "create", "group-1");

        // Verify context updated with group-1
        var context = config.read().getContext().get("test");
        assertThat(context.getGroupId()).isEqualTo("group-1");

        // Create group-2
        executeAndAssertSuccess("group", "create", "group-2");

        // Verify context updated with group-2
        context = config.read().getContext().get("test");
        assertThat(context.getGroupId()).isEqualTo("group-2");

        // Get group-1
        executeAndAssertSuccess("group", "get", "group-1");

        // Verify context updated back to group-1
        context = config.read().getContext().get("test");
        assertThat(context.getGroupId()).isEqualTo("group-1");
    }

    @Test
    public void testAutoContextUpdateEnabledArtifact() {
        // Enable auto-context-update
        executeAndAssertSuccess("config", "set", "auto-context-update=true");

        // Create a group first
        executeAndAssertSuccess("group", "create", "group-art");

        // Create artifact-1 in group-art
        executeAndAssertSuccess("artifact", "create", "--group", "group-art", "--type", "JSON", "artifact-1");

        // Verify context updated with group-art and artifact-1
        var context = config.read().getContext().get("test");
        assertThat(context.getGroupId()).isEqualTo("group-art");
        assertThat(context.getArtifactId()).isEqualTo("artifact-1");

        // Create group-art-2
        executeAndAssertSuccess("group", "create", "group-art-2");

        // Create artifact-2 in group-art-2
        executeAndAssertSuccess("artifact", "create", "--group", "group-art-2", "--type", "JSON", "artifact-2");

        // Verify context updated with group-art-2 and artifact-2
        context = config.read().getContext().get("test");
        assertThat(context.getGroupId()).isEqualTo("group-art-2");
        assertThat(context.getArtifactId()).isEqualTo("artifact-2");

        // Get artifact-1 metadata
        executeAndAssertSuccess("artifact", "get", "--group", "group-art", "artifact-1");

        // Verify context updated back to group-art and artifact-1
        context = config.read().getContext().get("test");
        assertThat(context.getGroupId()).isEqualTo("group-art");
        assertThat(context.getArtifactId()).isEqualTo("artifact-1");

        // Get artifact-2 metadata
        executeAndAssertSuccess("artifact", "get", "--group", "group-art-2", "artifact-2");

        // Verify context updated back to group-art-2 and artifact-2
        context = config.read().getContext().get("test");
        assertThat(context.getGroupId()).isEqualTo("group-art-2");
        assertThat(context.getArtifactId()).isEqualTo("artifact-2");
    }
}
