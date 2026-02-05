package io.apicurio.registry.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FileBasedSecretsConfigSource}.
 */
public class FileBasedSecretsConfigSourceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        // Clear any test system properties
        clearTestSystemProperties();
    }

    /**
     * Tests that a valid secret file is read correctly and whitespace is trimmed.
     */
    @Test
    void testReadValidSecretFile() throws IOException {
        // Create a secret file with whitespace
        Path secretFile = tempDir.resolve("password.txt");
        Files.writeString(secretFile, "  mySecretPassword\n\n");

        // Set system property
        System.setProperty("apicurio.datasource.password.file", secretFile.toString());

        // Create ConfigSource
        FileBasedSecretsConfigSource configSource = new FileBasedSecretsConfigSource();

        // Verify the secret is loaded with whitespace trimmed
        assertEquals("mySecretPassword", configSource.getValue("apicurio.datasource.password"));
        assertTrue(configSource.getProperties().containsKey("apicurio.datasource.password"));
    }

    /**
     * Tests that multiple .file properties are processed correctly.
     */
    @Test
    void testMultipleFileProperties() throws IOException {
        // Create secret files
        Path passwordFile = tempDir.resolve("password.txt");
        Files.writeString(passwordFile, "password123");

        Path usernameFile = tempDir.resolve("username.txt");
        Files.writeString(usernameFile, "dbuser");

        Path bluePasswordFile = tempDir.resolve("blue-password.txt");
        Files.writeString(bluePasswordFile, "bluepass");

        // Set system properties
        System.setProperty("apicurio.datasource.password.file", passwordFile.toString());
        System.setProperty("apicurio.datasource.username.file", usernameFile.toString());
        System.setProperty("apicurio.datasource.blue.password.file", bluePasswordFile.toString());

        // Create ConfigSource
        FileBasedSecretsConfigSource configSource = new FileBasedSecretsConfigSource();

        // Verify all secrets are loaded
        assertEquals("password123", configSource.getValue("apicurio.datasource.password"));
        assertEquals("dbuser", configSource.getValue("apicurio.datasource.username"));
        assertEquals("bluepass", configSource.getValue("apicurio.datasource.blue.password"));
    }

    /**
     * Tests that missing file throws IllegalStateException with clear error message.
     */
    @Test
    void testMissingFileThrowsException() {
        Path nonExistentFile = tempDir.resolve("does-not-exist.txt");

        System.setProperty("apicurio.datasource.password.file", nonExistentFile.toString());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            new FileBasedSecretsConfigSource();
        });

        assertTrue(exception.getMessage().contains("Secret file not found"));
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    /**
     * Tests that unreadable file throws IllegalStateException.
     */
    @Test
    void testUnreadableFileThrowsException() throws IOException {
        // This test may not work on all platforms (e.g., Windows)
        // Skip if POSIX permissions are not supported
        Path secretFile = tempDir.resolve("unreadable.txt");
        Files.writeString(secretFile, "secret");

        try {
            // Remove read permissions
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(secretFile);
            permissions.remove(PosixFilePermission.OWNER_READ);
            permissions.remove(PosixFilePermission.GROUP_READ);
            permissions.remove(PosixFilePermission.OTHERS_READ);
            Files.setPosixFilePermissions(secretFile, permissions);

            System.setProperty("apicurio.datasource.password.file", secretFile.toString());

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                new FileBasedSecretsConfigSource();
            });

            assertTrue(exception.getMessage().contains("not readable") ||
                      exception.getMessage().contains("Failed to read"));
        } catch (UnsupportedOperationException e) {
            // POSIX permissions not supported, skip this test
            // This can happen on Windows
        }
    }

    /**
     * Tests that empty file is treated as empty string (valid but unusual).
     */
    @Test
    void testEmptyFile() throws IOException {
        Path secretFile = tempDir.resolve("empty.txt");
        Files.writeString(secretFile, "");

        System.setProperty("apicurio.datasource.password.file", secretFile.toString());

        FileBasedSecretsConfigSource configSource = new FileBasedSecretsConfigSource();

        assertEquals("", configSource.getValue("apicurio.datasource.password"));
    }

    /**
     * Tests that file with only whitespace results in empty string after trimming.
     */
    @Test
    void testWhitespaceOnlyFile() throws IOException {
        Path secretFile = tempDir.resolve("whitespace.txt");
        Files.writeString(secretFile, "   \n\n  \t  ");

        System.setProperty("apicurio.datasource.password.file", secretFile.toString());

        FileBasedSecretsConfigSource configSource = new FileBasedSecretsConfigSource();

        assertEquals("", configSource.getValue("apicurio.datasource.password"));
    }

    /**
     * Tests that ConfigSource has correct ordinal (priority).
     */
    @Test
    void testOrdinal() throws IOException {
        Path secretFile = tempDir.resolve("password.txt");
        Files.writeString(secretFile, "password");

        System.setProperty("apicurio.datasource.password.file", secretFile.toString());

        FileBasedSecretsConfigSource configSource = new FileBasedSecretsConfigSource();

        assertEquals(350, configSource.getOrdinal());
    }

    /**
     * Tests that ConfigSource has correct name.
     */
    @Test
    void testName() throws IOException {
        Path secretFile = tempDir.resolve("password.txt");
        Files.writeString(secretFile, "password");

        System.setProperty("apicurio.datasource.password.file", secretFile.toString());

        FileBasedSecretsConfigSource configSource = new FileBasedSecretsConfigSource();

        assertEquals("FileBasedSecretsConfigSource", configSource.getName());
    }

    /**
     * Tests that all supported GitOps datasource properties work correctly.
     */
    @Test
    void testGitOpsDatasourceProperties() throws IOException {
        // Create secret files for GitOps datasources
        Path greenUsernameFile = tempDir.resolve("green-username.txt");
        Files.writeString(greenUsernameFile, "greenuser");

        Path greenPasswordFile = tempDir.resolve("green-password.txt");
        Files.writeString(greenPasswordFile, "greenpass");

        // Set system properties
        System.setProperty("apicurio.datasource.green.username.file", greenUsernameFile.toString());
        System.setProperty("apicurio.datasource.green.password.file", greenPasswordFile.toString());

        // Create ConfigSource
        FileBasedSecretsConfigSource configSource = new FileBasedSecretsConfigSource();

        // Verify GitOps secrets are loaded
        assertEquals("greenuser", configSource.getValue("apicurio.datasource.green.username"));
        assertEquals("greenpass", configSource.getValue("apicurio.datasource.green.password"));
    }

    /**
     * Tests that ANY property with .file suffix works (generic behavior).
     */
    @Test
    void testGenericFilePropertySupport() throws IOException {
        // Create a secret file for a custom property
        Path customSecretFile = tempDir.resolve("custom-secret.txt");
        Files.writeString(customSecretFile, "custom-value");

        // Set a .file property for a custom property (not in any predefined list)
        System.setProperty("my.custom.property.file", customSecretFile.toString());

        // Create ConfigSource
        FileBasedSecretsConfigSource configSource = new FileBasedSecretsConfigSource();

        // Verify the custom property is loaded (proves generic support)
        assertEquals("custom-value", configSource.getValue("my.custom.property"));
    }

    /**
     * Tests that multiple different types of properties work together.
     */
    @Test
    void testMultiplePropertyTypesWork() throws IOException {
        // Create secret files for different types of properties
        Path datasourcePasswordFile = tempDir.resolve("db-password.txt");
        Files.writeString(datasourcePasswordFile, "dbpass123");

        Path oidcClientSecretFile = tempDir.resolve("oidc-secret.txt");
        Files.writeString(oidcClientSecretFile, "oidc-secret-value");

        Path kafkaSecretFile = tempDir.resolve("kafka-secret.txt");
        Files.writeString(kafkaSecretFile, "kafka-secret-value");

        // Set different types of .file properties
        System.setProperty("apicurio.datasource.password.file", datasourcePasswordFile.toString());
        System.setProperty("quarkus.oidc.client-secret.file", oidcClientSecretFile.toString());
        System.setProperty("apicurio.kafkasql.security.sasl.client-secret.file", kafkaSecretFile.toString());

        // Create ConfigSource
        FileBasedSecretsConfigSource configSource = new FileBasedSecretsConfigSource();

        // Verify all different types work
        assertEquals("dbpass123", configSource.getValue("apicurio.datasource.password"));
        assertEquals("oidc-secret-value", configSource.getValue("quarkus.oidc.client-secret"));
        assertEquals("kafka-secret-value", configSource.getValue("apicurio.kafkasql.security.sasl.client-secret"));
    }

    /**
     * Tests that properties map contains all loaded secrets.
     */
    @Test
    void testGetProperties() throws IOException {
        Path passwordFile = tempDir.resolve("password.txt");
        Files.writeString(passwordFile, "pass123");

        Path usernameFile = tempDir.resolve("username.txt");
        Files.writeString(usernameFile, "user123");

        System.setProperty("apicurio.datasource.password.file", passwordFile.toString());
        System.setProperty("apicurio.datasource.username.file", usernameFile.toString());

        FileBasedSecretsConfigSource configSource = new FileBasedSecretsConfigSource();

        Map<String, String> properties = configSource.getProperties();

        assertEquals(2, properties.size());
        assertEquals("pass123", properties.get("apicurio.datasource.password"));
        assertEquals("user123", properties.get("apicurio.datasource.username"));
    }

    /**
     * Helper method to clear test system properties.
     */
    private void clearTestSystemProperties() {
        // Clear all test properties
        System.clearProperty("apicurio.datasource.password.file");
        System.clearProperty("apicurio.datasource.username.file");
        System.clearProperty("apicurio.datasource.blue.password.file");
        System.clearProperty("apicurio.datasource.blue.username.file");
        System.clearProperty("apicurio.datasource.green.password.file");
        System.clearProperty("apicurio.datasource.green.username.file");
        System.clearProperty("my.custom.property.file");
        System.clearProperty("quarkus.oidc.client-secret.file");
        System.clearProperty("apicurio.kafkasql.security.sasl.client-secret.file");
    }
}
