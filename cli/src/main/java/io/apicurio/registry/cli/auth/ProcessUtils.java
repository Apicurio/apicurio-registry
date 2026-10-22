package io.apicurio.registry.cli.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Executes OS commands with timeout handling, used by credential providers.
 */
final class ProcessUtils {

    private static final int COMMAND_TIMEOUT_SECONDS = 10;

    private ProcessUtils() {
    }

    static void execWithStdin(final String stdin, final String... command) {
        try {
            final Process process = new ProcessBuilder(command).start();
            process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new CredentialStoreException(command[0] + " timed out.");
            }
            if (process.exitValue() != 0) {
                final String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new CredentialStoreException(command[0] + " failed (exit code " + process.exitValue() + "): " + error);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CredentialStoreException("Failed to execute " + command[0] + ": " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new CredentialStoreException("Failed to execute " + command[0] + ": " + ex.getMessage(), ex);
        }
    }

    static String exec(final String... command) {
        try {
            final Process process = new ProcessBuilder(command).start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new CredentialStoreException("Command timed out: " + command[0]);
            }
            final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                final String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new CredentialStoreException(command[0] + " failed (exit code " + process.exitValue() + "): " + error);
            }
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CredentialStoreException("Failed to execute " + command[0] + ": " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new CredentialStoreException("Failed to execute " + command[0] + ": " + ex.getMessage(), ex);
        }
    }
}
