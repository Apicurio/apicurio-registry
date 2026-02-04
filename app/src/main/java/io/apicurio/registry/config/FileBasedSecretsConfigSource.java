package io.apicurio.registry.config;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Quarkus ConfigSource that supports file-based secrets for any configuration property.
 * This enables Docker Compose and Kubernetes secret management patterns.
 *
 * <p>For any configuration property ending in {@code .file}, this ConfigSource will:
 * <ul>
 *   <li>Read the file at the path specified by the property value</li>
 *   <li>Trim all leading and trailing whitespace from the file contents</li>
 *   <li>Provide the contents as a config property with the {@code .file} suffix removed</li>
 * </ul>
 *
 * <p>This works for <strong>any</strong> configuration property - simply add {@code .file} to the
 * property name and set the value to a file path. The file contents will be read and used as the
 * actual property value.
 *
 * <p>Common examples:
 * <ul>
 *   <li>{@code apicurio.datasource.username.file} → {@code apicurio.datasource.username}</li>
 *   <li>{@code apicurio.datasource.password.file} → {@code apicurio.datasource.password}</li>
 *   <li>{@code quarkus.oidc.client-secret.file} → {@code quarkus.oidc.client-secret}</li>
 *   <li>{@code apicurio.kafkasql.security.sasl.client-secret.file} → {@code apicurio.kafkasql.security.sasl.client-secret}</li>
 * </ul>
 *
 * <p>Properties can be set via system properties or environment variables (which Quarkus automatically
 * converts). For example:
 * <ul>
 *   <li>System property: {@code apicurio.datasource.password.file=/run/secrets/db_password}</li>
 *   <li>Environment variable: {@code APICURIO_DATASOURCE_PASSWORD_FILE=/run/secrets/db_password}</li>
 * </ul>
 *
 * <p>The ConfigSource has priority 350, which is higher than environment variables (300)
 * but lower than system properties (400). This means {@code .file} variants take precedence
 * over direct environment variables.
 *
 * <p>Error handling:
 * <ul>
 *   <li>If a {@code .file} property is set but the file doesn't exist, startup fails</li>
 *   <li>If the file cannot be read, startup fails with a clear error message</li>
 *   <li>If file permissions are world-readable, a warning is logged</li>
 * </ul>
 *
 * @since 3.2.0
 */
public class FileBasedSecretsConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(FileBasedSecretsConfigSource.class);

    private static final String FILE_SUFFIX = ".file";
    private static final int ORDINAL = 350;

    private final Map<String, String> properties;

    /**
     * Constructs the ConfigSource by scanning system properties and reading secret files.
     */
    public FileBasedSecretsConfigSource() {
        this.properties = new HashMap<>();
        loadFileBasedSecrets();
    }

    /**
     * Scans all system properties for properties ending in .file and reads the secret files.
     * This is a generic mechanism that works for any configuration property.
     */
    private void loadFileBasedSecrets() {
        // Scan all system properties for .file suffix
        for (String propertyName : System.getProperties().stringPropertyNames()) {
            if (propertyName.endsWith(FILE_SUFFIX)) {
                String filePath = System.getProperty(propertyName);

                if (filePath != null && !filePath.trim().isEmpty()) {
                    // Remove .file suffix to get the target property name
                    String targetPropertyName = propertyName.substring(0, propertyName.length() - FILE_SUFFIX.length());
                    String secretValue = readSecretFromFile(filePath, propertyName);
                    properties.put(targetPropertyName, secretValue);

                    log.info("Loaded secret from file for property '{}' (from {})",
                             targetPropertyName, propertyName);
                }
            }
        }
    }

    /**
     * Reads a secret from a file, trimming all leading and trailing whitespace.
     *
     * @param filePath the path to the secret file
     * @param propertyName the property name (for error messages)
     * @return the secret value with whitespace trimmed
     * @throws IllegalStateException if the file cannot be read
     */
    private String readSecretFromFile(String filePath, String propertyName) {
        Path path = Paths.get(filePath);

        // Check if file exists
        if (!Files.exists(path)) {
            String message = String.format(
                "Secret file not found for %s: %s does not exist",
                propertyName, filePath);
            log.error(message);
            throw new IllegalStateException(message);
        }

        // Check if file is readable
        if (!Files.isReadable(path)) {
            String message = String.format(
                "Secret file not readable for %s: %s (check file permissions)",
                propertyName, filePath);
            log.error(message);
            throw new IllegalStateException(message);
        }

        // Warn if file has overly permissive permissions (world-readable)
        checkFilePermissions(path, propertyName);

        // Read file contents
        try {
            String content = Files.readString(path);
            return content.trim();
        } catch (IOException e) {
            String message = String.format(
                "Failed to read secret file for %s: %s - %s",
                propertyName, filePath, e.getMessage());
            log.error(message, e);
            throw new IllegalStateException(message, e);
        }
    }

    /**
     * Checks file permissions and logs a warning if the file is world-readable.
     *
     * @param path the path to the secret file
     * @param propertyName the property name (for logging)
     */
    private void checkFilePermissions(Path path, String propertyName) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);

            if (permissions.contains(PosixFilePermission.OTHERS_READ)) {
                log.warn("Secret file for {} is world-readable: {} (permissions may be too open)",
                         propertyName, path);
            }
        } catch (UnsupportedOperationException e) {
            // POSIX permissions not supported (e.g., Windows)
            // Skip permission check
        } catch (IOException e) {
            log.debug("Could not check permissions for secret file: {}", path, e);
        }
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public Map<String, String> getProperties() {
        return new HashMap<>(properties);
    }

    @Override
    public String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return "FileBasedSecretsConfigSource";
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }
}
