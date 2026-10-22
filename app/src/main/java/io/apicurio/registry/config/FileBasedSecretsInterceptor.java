package io.apicurio.registry.config;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import jakarta.annotation.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link ConfigSourceInterceptor} that supports file-based secrets for any configuration property.
 * This enables Docker Compose and Kubernetes secret management patterns.
 *
 * <p>When any property {@code foo.bar} is requested, this interceptor checks whether
 * {@code foo.bar.file} has a value (across all config sources, including environment variables).
 * If it does, the file at that path is read and its contents are returned as the property value.
 *
 * <p>This approach works with all config sources — environment variables, system properties,
 * {@code application.properties}, etc. — because the interceptor delegates to
 * {@code context.proceed()} which resolves values through the full SmallRye Config chain,
 * including the automatic {@code UPPER_SNAKE_CASE} → {@code dotted.lowercase} conversion
 * for environment variables.
 *
 * <p>Common examples:
 * <ul>
 *   <li>Env var {@code APICURIO_DATASOURCE_PASSWORD_FILE=/run/secrets/db_password}
 *       → resolves {@code apicurio.datasource.password} from that file</li>
 *   <li>System property {@code apicurio.datasource.password.file=/run/secrets/db_password}
 *       → same behavior</li>
 * </ul>
 *
 * <p>Error handling:
 * <ul>
 *   <li>If a {@code .file} property is set but the file doesn't exist, an
 *       {@link IllegalStateException} is thrown</li>
 *   <li>If the file cannot be read, an {@link IllegalStateException} is thrown</li>
 *   <li>If file permissions are world-readable, a warning is logged</li>
 * </ul>
 *
 * @since 3.2.0
 */
@Priority(50)
public class FileBasedSecretsInterceptor implements ConfigSourceInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FileBasedSecretsInterceptor.class);

    private static final String FILE_SUFFIX = ".file";

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        // Avoid infinite recursion: if the property itself ends in ".file", just pass through
        if (name.endsWith(FILE_SUFFIX)) {
            return context.proceed(name);
        }

        // Check if there is a ".file" variant for this property
        String filePropertyName = name + FILE_SUFFIX;
        ConfigValue fileValue = context.proceed(filePropertyName);

        if (fileValue != null && fileValue.getValue() != null
                && !fileValue.getValue().trim().isEmpty()) {
            String secretValue = cache.computeIfAbsent(name,
                    key -> readSecretFromFile(fileValue.getValue().trim(), filePropertyName));
            log.info("Loaded secret from file for property '{}' (from {})", name, filePropertyName);
            return ConfigValue.builder()
                    .withName(name)
                    .withValue(secretValue)
                    .build();
        }

        return context.proceed(name);
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
}
