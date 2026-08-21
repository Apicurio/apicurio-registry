package io.apicurio.registry.cli.common;

import io.apicurio.registry.cli.Acr;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.services.UpdateNotifier;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.OK_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.SERVER_ERROR_RETURN_CODE;

@Command
public abstract class AbstractCommand implements Callable<Integer> {

    private static final Logger log = Logger.getLogger(AbstractCommand.class);

    private static final String ERROR_PREFIX = "Error: ";

    @Spec
    protected CommandSpec spec;

    @Inject
    protected Config config;

    @Inject
    protected Client client;

    @Inject
    UpdateNotifier updateNotifier;

    @Override
    public Integer call() {
        var output = new OutputBuffer(config.getStdOut(), config.getStdErr());
        try {
            configureLogging();
            updateNotifier.checkAndNotify(getTopLevelCommandName());
            if (isInteractiveRequested() && supportsInteractive()) {
                runInteractive(output);
                return OK_RETURN_CODE;
            }
            run(output);
            return OK_RETURN_CODE;
        } catch (CliException ex) {
            handleCliException(output, ex);
            return ex.getCode();
        } catch (RuleViolationProblemDetails ex) {
            handleRuleViolation(output, ex);
            return SERVER_ERROR_RETURN_CODE;
        } catch (ProblemDetails ex) {
            handleProblemDetails(output, ex);
            return SERVER_ERROR_RETURN_CODE;
        } catch (Exception ex) {
            log.error("Unexpected error", ex);
            output.writeStdErrChunk(out -> out.append("Unexpected error: ").append(ex.getMessage()).append("\n"));
            return APPLICATION_ERROR_RETURN_CODE;
        } finally {
            output.print();
        }
    }

    public abstract void run(OutputBuffer output) throws Exception;

    /**
     * Commands that support --interactive should override this.
     * Default: interactive mode isn't available for this command.
     */
    public boolean supportsInteractive() {
        return false;
    }

    /**
     * Runs the TUI loop. Only called if supportsInteractive() is true
     * and the --interactive flag was passed.
     */
    public void runInteractive(OutputBuffer output) {
        throw new UnsupportedOperationException("Interactive mode not implemented for this command.");
    }

    private boolean isInteractiveRequested() {
        return InteractiveMixin.isRequested(spec.commandLine().getParseResult());
    }

    private static void handleCliException(final OutputBuffer output, final CliException ex) {
        if (!ex.isQuiet()) {
            output.writeStdErrChunk(out -> out.append(ERROR_PREFIX).append(ex.getMessage()).append("\n"));
        }
    }

    private static void handleRuleViolation(final OutputBuffer output, final RuleViolationProblemDetails ex) {
        output.writeStdErrChunk(err -> {
            err.append(ERROR_PREFIX).append(Optional.ofNullable(ex.getDetail()).orElse(ex.getMessage())).append('\n');
            Optional.ofNullable(ex.getCauses()).ifPresent(causes ->
                    causes.forEach(cause ->
                            err.append("  -> ").append(Optional.ofNullable(cause.getContext()).orElse(""))
                                    .append(": ").append(Optional.ofNullable(cause.getDescription()).orElse("")).append('\n')));
        });
    }

    protected static void handleProblemDetails(final OutputBuffer output, final ProblemDetails ex) {
        output.writeStdErrChunk(err -> {
            err.append(ERROR_PREFIX).append(Optional.ofNullable(ex.getDetail()).orElse(ex.getMessage())).append('\n');
        });
    }

    private String getTopLevelCommandName() {
        var current = spec;
        while (current.parent() != null && current.parent().parent() != null) {
            current = current.parent();
        }
        return current.name();
    }

    private static final String LOG_CATEGORY_PREFIX = "quarkus.log.category.\"";
    private static final String LOG_CATEGORY_SUFFIX = "\".level";

    /**
     * Configures logging for the current command.
     * <p>
     * Logging is only configured when {@code --verbose} is set. Verbose raises the root logger to
     * {@link Level#FINE}, and the per-package levels from the CLI config are then applied on top of
     * it, which is how a noisy package can be suppressed (for example {@code io.netty=WARNING}).
     * Without {@code --verbose} nothing is touched, whatever the config contains.
     * <p>
     * The levels are applied at runtime because the packaged CLI ships {@code quarkus.log.level=OFF}
     * and Quarkus resolves {@code quarkus.log.*} at build time, so per-package levels in the CLI
     * config are never picked up by Quarkus's own logging setup.
     */
    private void configureLogging() {
        var root = spec.root().userObject();
        if (!(root instanceof Acr acr) || !acr.isVerbose()) {
            return;
        }

        var rootLogger = java.util.logging.Logger.getLogger("");
        rootLogger.setLevel(Level.FINE);
        var finest = Level.FINE;

        for (var entry : readLogCategoryLevels().entrySet()) {
            Level level;
            try {
                level = toJulLevel(entry.getValue());
            } catch (IllegalArgumentException ex) {
                log.warnf("Ignoring invalid log level '%s' for category '%s'.", entry.getValue(), entry.getKey());
                continue;
            }
            java.util.logging.Logger.getLogger(entry.getKey()).setLevel(level);
            if (level.intValue() < finest.intValue()) {
                finest = level;
            }
        }

        // Handlers filter by their own level, so lower them to reveal the finest level we enabled.
        // Only ever lower, never raise, so we don't suppress unrelated channels the user didn't touch.
        for (var handler : rootLogger.getHandlers()) {
            handler.setLevel(loweredHandlerLevel(handler.getLevel(), finest));
        }
    }

    /**
     * Reads the per-package log levels from the CLI config, keyed by package name.
     * <p>
     * Reading is best-effort, because commands that run before install have no config yet.
     */
    private Map<String, String> readLogCategoryLevels() {
        Map<String, String> levels = new HashMap<>();
        try {
            config.read().getConfig().forEach((key, value) -> {
                if (key.startsWith(LOG_CATEGORY_PREFIX) && key.endsWith(LOG_CATEGORY_SUFFIX)
                        && key.length() > LOG_CATEGORY_PREFIX.length() + LOG_CATEGORY_SUFFIX.length()) {
                    levels.put(key.substring(LOG_CATEGORY_PREFIX.length(), key.length() - LOG_CATEGORY_SUFFIX.length()), value);
                }
            });
        } catch (CliException ex) {
            // Config not available yet, e.g. before install.
        }
        return levels;
    }

    /**
     * Maps Quarkus/JBoss level names (DEBUG, TRACE, WARN, ...) to plain JUL levels. The packaged CLI
     * runs on plain JUL, where {@code Level.parse} does not recognise DEBUG/TRACE.
     *
     * @throws IllegalArgumentException if the value is not a recognised level name
     */
    static Level toJulLevel(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "OFF" -> Level.OFF;
            case "FATAL", "ERROR", "SEVERE" -> Level.SEVERE;
            case "WARN", "WARNING" -> Level.WARNING;
            case "INFO" -> Level.INFO;
            case "CONFIG" -> Level.CONFIG;
            case "DEBUG", "FINE" -> Level.FINE;
            case "FINER" -> Level.FINER;
            case "TRACE", "FINEST" -> Level.FINEST;
            case "ALL" -> Level.ALL;
            default -> throw new IllegalArgumentException("Unknown log level: " + value);
        };
    }

    /**
     * Returns {@code floor} only when the handler is currently coarser (or unset), so a handler is
     * never raised, which would hide records from channels the user did not configure.
     */
    static Level loweredHandlerLevel(Level current, Level floor) {
        if (current == null || current.intValue() > floor.intValue()) {
            return floor;
        }
        return current;
    }
}
