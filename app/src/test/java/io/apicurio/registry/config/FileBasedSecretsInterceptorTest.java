package io.apicurio.registry.config;

import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FileBasedSecretsInterceptor}.
 */
public class FileBasedSecretsInterceptorTest {

    @TempDir
    Path tempDir;

    private FileBasedSecretsInterceptor interceptor;
    private ConfigSourceInterceptorContext context;

    @BeforeEach
    void setUp() {
        interceptor = new FileBasedSecretsInterceptor();
        context = mock(ConfigSourceInterceptorContext.class);
    }

    /**
     * Tests that a valid secret file is read correctly and whitespace is trimmed.
     */
    @Test
    void testReadValidSecretFile() throws IOException {
        Path secretFile = tempDir.resolve("password.txt");
        Files.writeString(secretFile, "  mySecretPassword\n\n");

        // .file property returns a path
        when(context.proceed("apicurio.datasource.password.file"))
                .thenReturn(configValue("apicurio.datasource.password.file", secretFile.toString()));

        ConfigValue result = interceptor.getValue(context, "apicurio.datasource.password");

        assertNotNull(result);
        assertEquals("mySecretPassword", result.getValue());
        assertEquals("apicurio.datasource.password", result.getName());
    }

    /**
     * Tests that when no .file property exists, the interceptor passes through to normal resolution.
     */
    @Test
    void testPassthroughWhenNoFileProperty() {
        ConfigValue originalValue = configValue("apicurio.datasource.password", "directValue");

        // .file property returns null
        when(context.proceed("apicurio.datasource.password.file")).thenReturn(null);
        when(context.proceed("apicurio.datasource.password")).thenReturn(originalValue);

        ConfigValue result = interceptor.getValue(context, "apicurio.datasource.password");

        assertEquals("directValue", result.getValue());
    }

    /**
     * Tests that requesting a property ending in .file passes through without recursion.
     */
    @Test
    void testFilePropertyPassthrough() {
        ConfigValue filePathValue = configValue("apicurio.datasource.password.file",
                "/run/secrets/db_password");

        when(context.proceed("apicurio.datasource.password.file")).thenReturn(filePathValue);

        ConfigValue result = interceptor.getValue(context, "apicurio.datasource.password.file");

        assertEquals("/run/secrets/db_password", result.getValue());
        // Should NOT have tried to look up "apicurio.datasource.password.file.file"
        verify(context, times(1)).proceed("apicurio.datasource.password.file");
    }

    /**
     * Tests that missing file throws IllegalStateException with clear error message.
     */
    @Test
    void testMissingFileThrowsException() {
        Path nonExistentFile = tempDir.resolve("does-not-exist.txt");

        when(context.proceed("apicurio.datasource.password.file"))
                .thenReturn(configValue("apicurio.datasource.password.file",
                        nonExistentFile.toString()));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> interceptor.getValue(context, "apicurio.datasource.password"));

        assertTrue(exception.getMessage().contains("Secret file not found"));
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    /**
     * Tests that unreadable file throws IllegalStateException.
     */
    @Test
    void testUnreadableFileThrowsException() throws IOException {
        Path secretFile = tempDir.resolve("unreadable.txt");
        Files.writeString(secretFile, "secret");

        try {
            // Remove read permissions
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(secretFile);
            permissions.remove(PosixFilePermission.OWNER_READ);
            permissions.remove(PosixFilePermission.GROUP_READ);
            permissions.remove(PosixFilePermission.OTHERS_READ);
            Files.setPosixFilePermissions(secretFile, permissions);

            when(context.proceed("apicurio.datasource.password.file"))
                    .thenReturn(configValue("apicurio.datasource.password.file",
                            secretFile.toString()));

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> interceptor.getValue(context, "apicurio.datasource.password"));

            assertTrue(exception.getMessage().contains("not readable")
                    || exception.getMessage().contains("Failed to read"));
        } catch (UnsupportedOperationException e) {
            // POSIX permissions not supported (e.g., Windows), skip this test
        }
    }

    /**
     * Tests that whitespace trimming works on file contents.
     */
    @Test
    void testWhitespaceTrimming() throws IOException {
        Path secretFile = tempDir.resolve("whitespace.txt");
        Files.writeString(secretFile, "   \n  secretValue  \n\n  ");

        when(context.proceed("my.property.file"))
                .thenReturn(configValue("my.property.file", secretFile.toString()));

        ConfigValue result = interceptor.getValue(context, "my.property");

        assertNotNull(result);
        assertEquals("secretValue", result.getValue());
    }

    /**
     * Tests that empty .file property value is treated as no .file property.
     */
    @Test
    void testEmptyFilePropertyPassesThrough() {
        ConfigValue emptyFileValue = configValue("apicurio.datasource.password.file", "   ");
        ConfigValue originalValue = configValue("apicurio.datasource.password", "directValue");

        when(context.proceed("apicurio.datasource.password.file")).thenReturn(emptyFileValue);
        when(context.proceed("apicurio.datasource.password")).thenReturn(originalValue);

        ConfigValue result = interceptor.getValue(context, "apicurio.datasource.password");

        assertEquals("directValue", result.getValue());
    }

    /**
     * Tests that null .file ConfigValue passes through to normal resolution.
     */
    @Test
    void testNullFileValuePassesThrough() {
        ConfigValue nullValueConfig = ConfigValue.builder()
                .withName("apicurio.datasource.password.file")
                .withValue(null)
                .build();
        ConfigValue originalValue = configValue("apicurio.datasource.password", "directValue");

        when(context.proceed("apicurio.datasource.password.file")).thenReturn(nullValueConfig);
        when(context.proceed("apicurio.datasource.password")).thenReturn(originalValue);

        ConfigValue result = interceptor.getValue(context, "apicurio.datasource.password");

        assertEquals("directValue", result.getValue());
    }

    /**
     * Tests that the value is cached and the file is read only once for repeated lookups.
     */
    @Test
    void testCaching() throws IOException {
        Path secretFile = tempDir.resolve("cached-secret.txt");
        Files.writeString(secretFile, "cachedValue");

        when(context.proceed("my.cached.property.file"))
                .thenReturn(configValue("my.cached.property.file", secretFile.toString()));

        // Call getValue twice
        ConfigValue result1 = interceptor.getValue(context, "my.cached.property");
        ConfigValue result2 = interceptor.getValue(context, "my.cached.property");

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals("cachedValue", result1.getValue());
        assertEquals("cachedValue", result2.getValue());

        // Now modify the file to prove the cached value is used
        Files.writeString(secretFile, "newValue");

        ConfigValue result3 = interceptor.getValue(context, "my.cached.property");
        assertEquals("cachedValue", result3.getValue());
    }

    /**
     * Tests that multiple different properties can each have their own .file variant.
     */
    @Test
    void testMultipleFileProperties() throws IOException {
        Path passwordFile = tempDir.resolve("password.txt");
        Files.writeString(passwordFile, "password123");

        Path usernameFile = tempDir.resolve("username.txt");
        Files.writeString(usernameFile, "dbuser");

        when(context.proceed("apicurio.datasource.password.file"))
                .thenReturn(configValue("apicurio.datasource.password.file",
                        passwordFile.toString()));
        when(context.proceed("apicurio.datasource.username.file"))
                .thenReturn(configValue("apicurio.datasource.username.file",
                        usernameFile.toString()));

        ConfigValue passwordResult = interceptor.getValue(context, "apicurio.datasource.password");
        ConfigValue usernameResult = interceptor.getValue(context, "apicurio.datasource.username");

        assertEquals("password123", passwordResult.getValue());
        assertEquals("dbuser", usernameResult.getValue());
    }

    /**
     * Tests that a property with no value at all returns null.
     */
    @Test
    void testNoValueReturnsNull() {
        when(context.proceed("nonexistent.property.file")).thenReturn(null);
        when(context.proceed("nonexistent.property")).thenReturn(null);

        ConfigValue result = interceptor.getValue(context, "nonexistent.property");

        assertNull(result);
    }

    /**
     * Integration test that verifies UPPER_SNAKE_CASE environment variable names
     * (e.g. {@code APICURIO_DATASOURCE_PASSWORD_FILE}) are properly resolved through
     * SmallRye Config's {@link EnvConfigSource} and the interceptor. Uses
     * {@code EnvConfigSource} directly with a custom map to simulate Docker Compose
     * environment variables, exercising the full fuzzy name matching in
     * {@code EnvConfigSource.EnvName}.
     */
    @Test
    void testEnvironmentVariableResolution() throws IOException {
        Path secretFile = tempDir.resolve("env-secret.txt");
        Files.writeString(secretFile, "env-secret-value");

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withInterceptors(new FileBasedSecretsInterceptor())
                .withSources(new EnvConfigSource(
                        Map.of("APICURIO_DATASOURCE_PASSWORD_FILE", secretFile.toString()),
                        300))
                .build();

        // Query using the dotted lowercase property name — EnvConfigSource's fuzzy
        // EnvName matching resolves APICURIO_DATASOURCE_PASSWORD_FILE, and our
        // interceptor reads the file and returns its contents
        String value = config.getRawValue("apicurio.datasource.password");
        assertEquals("env-secret-value", value);
    }

    /**
     * Integration test that verifies multiple UPPER_SNAKE_CASE environment variables
     * resolve correctly through {@link EnvConfigSource} and the interceptor.
     */
    @Test
    void testMultipleEnvironmentVariablesResolution() throws IOException {
        Path passwordFile = tempDir.resolve("password.txt");
        Files.writeString(passwordFile, "db-password-123");

        Path usernameFile = tempDir.resolve("username.txt");
        Files.writeString(usernameFile, "db-user");

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withInterceptors(new FileBasedSecretsInterceptor())
                .withSources(new EnvConfigSource(
                        Map.of("APICURIO_DATASOURCE_PASSWORD_FILE", passwordFile.toString(),
                                "APICURIO_DATASOURCE_USERNAME_FILE", usernameFile.toString()),
                        300))
                .build();

        assertEquals("db-password-123", config.getRawValue("apicurio.datasource.password"));
        assertEquals("db-user", config.getRawValue("apicurio.datasource.username"));
    }

    /**
     * Helper to create a ConfigValue with the given name and value.
     */
    private ConfigValue configValue(String name, String value) {
        return ConfigValue.builder()
                .withName(name)
                .withValue(value)
                .build();
    }
}
