package io.apicurio.registry.cli.tags;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks tests that require Docker (e.g., Testcontainers).
 * These tests are skipped in environments where Docker is not available,
 * such as macOS GitHub Actions runners.
 *
 * @see <a href="https://github.com/douglascamata/setup-docker-macos-action/issues/35">
 *     Docker on macOS GitHub Actions runners</a>
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Tag("docker-required")
public @interface DockerRequired {
}
