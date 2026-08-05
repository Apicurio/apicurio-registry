package io.apicurio.registry.cli.utils;

import io.apicurio.registry.cli.common.CliException;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jboss.logging.Logger;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

/**
 * Reads and writes persistent user-scope environment variables on Windows.
 *
 * <p>Windows has no equivalent of the shell configuration files the CLI appends to on Linux and
 * macOS: the location of a PowerShell profile depends on the PowerShell edition and on whether
 * the user's Documents folder is redirected to OneDrive, and a profile is not read by cmd.exe at
 * all. User environment variables are the one mechanism honoured by cmd, PowerShell and Windows
 * Terminal alike, so the installer persists {@code ACR_HOME} and the {@code Path} entry there.
 *
 * <p>The work is delegated to {@code [Environment]::SetEnvironmentVariable(..., 'User')}, which
 * writes the variable under {@code HKCU\Environment} and broadcasts {@code WM_SETTINGCHANGE} so
 * that newly started shells pick the change up without a sign-out. {@code setx.exe} is
 * deliberately not used: it silently truncates values at 1024 characters, which would corrupt a
 * user {@code Path}.
 *
 * <p>This class is a CDI bean so tests can replace it with an alternative and never mutate the
 * environment of the developer running the build.
 */
@ApplicationScoped
public class UserEnvironment {

    private static final Logger log = Logger.getLogger(UserEnvironment.class);

    private static final int COMMAND_TIMEOUT_SECONDS = 30;

    private static final String ENV_SYSTEM_ROOT = "SystemRoot";
    private static final String DEFAULT_SYSTEM_ROOT = "C:\\Windows";

    /**
     * Names of the environment variables used to hand the name and value to the PowerShell
     * process. Passing them through the child environment rather than interpolating them into
     * the command keeps a hostile value (for example an {@code ACR_INSTALL_PATH} containing a
     * quote) from being parsed as PowerShell syntax.
     */
    private static final String ARG_NAME = "ACR_ENV_ARG_NAME";
    private static final String ARG_VALUE = "ACR_ENV_ARG_VALUE";

    private static final String SET_COMMAND =
            "[Environment]::SetEnvironmentVariable($env:" + ARG_NAME + ", $env:" + ARG_VALUE + ", 'User')";

    // The output encoding is forced to UTF-8 to match the decoding below: the console defaults to
    // an OEM code page, which would mangle a Path containing non-ASCII characters (a user home
    // under an accented user name, for example). Write-Output is avoided because it would append
    // a line separator and wrap long values at the console width.
    private static final String GET_COMMAND =
            "[Console]::OutputEncoding = [Text.Encoding]::UTF8;"
                    + " $value = [Environment]::GetEnvironmentVariable($env:" + ARG_NAME + ", 'User');"
                    + " if ($null -ne $value) { [Console]::Out.Write($value) }";

    /**
     * Returns the user-scope value of the given variable, or {@code null} if it is not set.
     * Note that this is deliberately not {@code System.getenv}: that returns the value the
     * current process inherited, which is the machine and user values already merged together.
     * Writing such a merged value back would bake the machine entries into the user scope.
     */
    public String getUserVariable(final String name) {
        final String value = run(GET_COMMAND, "read", name, null);
        return Utils.isBlank(value) ? null : value;
    }

    /**
     * Sets the user-scope value of the given variable, persisting it for shells started later.
     */
    public void setUserVariable(final String name, final String value) {
        run(SET_COMMAND, "update", name, value);
        log.debugf("Set user environment variable %s", name);
    }

    /**
     * Reads a stream to the end on another thread, so the caller can keep waiting on the process
     * itself. An failure to read is carried to the caller by the returned future.
     */
    private static CompletableFuture<byte[]> readAsync(final InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return stream.readAllBytes();
            } catch (final IOException ex) {
                throw new CompletionException(ex);
            }
        });
    }

    /**
     * Absolute path of the Windows PowerShell executable. It is resolved from {@code SystemRoot}
     * rather than looked up by name, so the interpreter cannot be taken from a writable directory
     * that happens to precede System32 on the process {@code PATH}.
     */
    private static Path powershellExecutable() {
        final String systemRoot = System.getenv(ENV_SYSTEM_ROOT);
        final String root = Utils.isBlank(systemRoot) ? DEFAULT_SYSTEM_ROOT : systemRoot;
        return Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
    }

    private String run(final String command, final String operation, final String name, final String value) {
        final ProcessBuilder builder = new ProcessBuilder(
                powershellExecutable().toString(), "-NoProfile", "-NonInteractive", "-Command", command);
        builder.environment().put(ARG_NAME, name);
        builder.environment().put(ARG_VALUE, value == null ? "" : value);
        // Discard the error stream rather than leaving it undrained: a PowerShell error long
        // enough to fill the stderr pipe would block the child forever, so stdout would never
        // reach end of input and the read below would never return. It is discarded rather than
        // merged into stdout because the read command's output is the value that gets written
        // back to the user environment, and anything PowerShell wrote to stderr would corrupt it.
        // Nothing is lost: only the exit code is reported, never the error text.
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = null;
        try {
            process = builder.start();
            // The commands never read input. Closing the stream hands them end of input rather
            // than an open pipe, so a child that did try to read cannot wait on it forever.
            process.getOutputStream().close();
            // Drain the output on another thread rather than here. It has to be drained while the
            // child runs, since a value larger than the pipe buffer would otherwise block the
            // child before it could exit; but draining it on this thread would mean waiting on a
            // read that never returns if the child hangs, leaving the timeout below unreachable.
            // Doing it separately keeps the wait, and therefore the deadline, on this thread.
            final CompletableFuture<byte[]> output = readAsync(process.getInputStream());
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                // Killing the child closes the pipe, which releases the reader as well.
                process.destroyForcibly();
                throw new CliException("Timed out trying to " + operation
                        + " the user environment variable " + name + ".", APPLICATION_ERROR_RETURN_CODE);
            }
            if (process.exitValue() != 0) {
                throw new CliException("Failed to " + operation + " the user environment variable " + name
                        + " (PowerShell exited with code " + process.exitValue() + ").",
                        APPLICATION_ERROR_RETURN_CODE);
            }
            // The child has exited, so the remaining output is buffered and this returns at once.
            return new String(output.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    StandardCharsets.UTF_8).trim();
        } catch (final IOException | ExecutionException | TimeoutException ex) {
            throw new CliException("Failed to " + operation + " the user environment variable " + name
                    + ": " + ex.getClass().getSimpleName(), APPLICATION_ERROR_RETURN_CODE);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CliException("Interrupted while trying to " + operation
                    + " the user environment variable " + name + ".", APPLICATION_ERROR_RETURN_CODE);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
