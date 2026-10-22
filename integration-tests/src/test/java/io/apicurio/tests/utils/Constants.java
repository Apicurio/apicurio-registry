package io.apicurio.tests.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Non-tag constants used across integration tests.
 */
public interface Constants {
    long POLL_INTERVAL = Duration.ofSeconds(1).toMillis();
    long TIMEOUT_FOR_REGISTRY_START_UP = Duration.ofSeconds(25).toMillis();
    long TIMEOUT_FOR_REGISTRY_READY = Duration.ofSeconds(30).toMillis();
    long TIMEOUT_GLOBAL = Duration.ofSeconds(30).toMillis();

    Path LOGS_DIR = Paths.get("target/logs/");

    String NO_DOCKER_ENV_VAR = "NO_DOCKER";
    String CURRENT_ENV = "CURRENT_ENV";
    String CURRENT_ENV_MAS_REGEX = ".*mas.*";
}
