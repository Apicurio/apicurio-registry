package io.apicurio.registry.cli;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.config.ConfigModel;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class ConfigTest {

    @Inject
    Config config;

    private Path homeDir;
    private Path configFile;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) throws IOException {
        homeDir = tempDir;
        configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
                {
                  "installation-version": 1,
                  "config": {
                    "a": "1",
                    "update.check-enabled": "false"
                  },
                  "context": {}
                }
                """);

        config.reset();
        config.setEnvOverride(Config.ENV_ACR_CURRENT_HOME, homeDir.toString());
    }

    @AfterEach
    public void tearDown() {
        config.reset();
    }

    @Test
    public void testGetAcrCurrentHomePathUsesEnvOverride() {
        assertThat(config.getAcrCurrentHomePath())
                .isEqualTo(homeDir.normalize().toAbsolutePath());
    }

    @Test
    public void testReadReturnsLiveCacheNotDefensiveCopy() {
        var first = config.read();
        var second = config.read();
        assertThat(second).isSameAs(first);

        first.getConfig().put("live", "yes");
        assertThat(config.read().getConfig()).containsEntry("live", "yes");
    }

    @Test
    public void testGetProperty() {
        assertThat(config.getProperty("a")).isEqualTo("1");
        assertThat(config.getProperty("missing")).isNull();
    }

    @Test
    public void testHasProperty() {
        assertThat(config.hasProperty("a")).isTrue();
        assertThat(config.hasProperty("missing")).isFalse();
    }

    @Test
    public void testSetPropertyPersistsOnlyAfterFlush() throws IOException {
        config.setProperty("b", "2");

        assertThat(config.getProperty("b")).isEqualTo("2");
        assertThat(Files.readString(configFile)).doesNotContain("\"b\"");

        config.flush();
        assertThat(Files.readString(configFile)).contains("\"b\"").contains("\"2\"");
    }

    @Test
    public void testRemovePropertyReturnsPreviousValueAndPersistsOnFlush() throws IOException {
        assertThat(config.removeProperty("a")).isEqualTo("1");
        assertThat(config.hasProperty("a")).isFalse();
        assertThat(config.removeProperty("missing")).isNull();

        config.flush();
        assertThat(Files.readString(configFile)).doesNotContain("\"a\"");
    }

    @Test
    public void testIsDirtyReflectsPendingChanges() throws IOException {
        assertThat(config.isDirty()).isFalse();

        config.setProperty("b", "2");
        assertThat(config.isDirty()).isTrue();

        config.flush();
        assertThat(config.isDirty()).isFalse();
    }

    @Test
    public void testFlushIsNoOpWhenNotDirty() throws IOException {
        config.read();
        Files.delete(configFile);

        config.flush();
        assertThat(Files.exists(configFile)).isFalse();
    }

    @Test
    public void testFlushClearsDirtyFlag() throws IOException {
        config.setProperty("c", "3");
        config.flush();
        assertThat(Files.readString(configFile)).contains("\"c\"");

        Files.delete(configFile);
        config.flush();
        assertThat(Files.exists(configFile)).isFalse();
    }

    @Test
    public void testMarkDirtyCausesStructuralChangesToPersist() throws IOException {
        config.read().setCurrentContext("ctx-1");
        config.markDirty();
        config.flush();

        assertThat(Files.readString(configFile)).contains("ctx-1");
    }

    @Test
    public void testPutGetAndSetCurrentContext() throws IOException {
        assertThat(config.getContext("dev")).isNull();

        config.putContext("dev", ConfigModel.Context.builder().registryUrl("http://dev").build());
        config.setCurrentContext("dev");

        assertThat(config.getContext("dev").getRegistryUrl()).isEqualTo("http://dev");
        assertThat(config.getCurrentContext()).isEqualTo("dev");

        config.flush();
        assertThat(Files.readString(configFile)).contains("http://dev").contains("dev");
    }

    @Test
    public void testUpdateContextAppliesMutationWhenPresent() {
        config.putContext("dev", ConfigModel.Context.builder().registryUrl("http://dev").build());

        var applied = config.updateContext("dev", ctx -> ctx.setGroupId("g1"));

        assertThat(applied).isTrue();
        assertThat(config.getContext("dev").getGroupId()).isEqualTo("g1");
    }

    @Test
    public void testUpdateContextIsNoOpWhenAbsent() {
        var applied = config.updateContext("missing", ctx -> ctx.setGroupId("g1"));

        assertThat(applied).isFalse();
        assertThat(config.getContext("missing")).isNull();
    }

    @Test
    public void testRemoveContextReturnsRemovedAndClearContextsEmptiesAll() {
        config.putContext("dev", ConfigModel.Context.builder().registryUrl("http://dev").build());
        config.putContext("prod", ConfigModel.Context.builder().registryUrl("http://prod").build());

        var removed = config.removeContext("dev");
        assertThat(removed).isNotNull();
        assertThat(removed.getRegistryUrl()).isEqualTo("http://dev");
        assertThat(config.getContext("dev")).isNull();
        assertThat(config.getContext("prod")).isNotNull();

        config.clearContexts();
        assertThat(config.read().getContext()).isEmpty();
    }
}
