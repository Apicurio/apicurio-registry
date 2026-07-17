package io.apicurio.registry.cli.common;

import io.apicurio.registry.cli.Acr;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.services.UpdateNotifier;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.concurrent.Callable;
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

    // Applied at runtime because the packaged CLI ships quarkus.log.level=OFF and Quarkus
    // resolves quarkus.log.* at build time, so per-package levels in the CLI config are
    // never picked up by Quarkus's own logging setup.
    private void configureLogging() {
        var rootLogger = java.util.logging.Logger.getLogger("");
        java.util.logging.Level finest = null;

        var root = spec.root().userObject();
        if (root instanceof Acr acr && acr.isVerbose()) {
            rootLogger.setLevel(java.util.logging.Level.FINE);
            finest = java.util.logging.Level.FINE;
        }

        for (var entry : readLogCategoryLevels().entrySet()) {
            java.util.logging.Level level;
            try {
                level = toJulLevel(entry.getValue());
            } catch (IllegalArgumentException ex) {
                log.warnf("Ignoring invalid log level '%s' for category '%s'.", entry.getValue(), entry.getKey());
                continue;
            }
            java.util.logging.Logger.getLogger(entry.getKey()).setLevel(level);
            if (finest == null || level.intValue() < finest.intValue()) {
                finest = level;
            }
        }

        // Handlers filter by their own level, so open them to the finest level we enabled.
        if (finest != null) {
            for (var handler : rootLogger.getHandlers()) {
                handler.setLevel(finest);
            }
        }
    }

    private java.util.Map<String, String> readLogCategoryLevels() {
        java.util.Map<String, String> levels = new java.util.HashMap<>();
        try {
            config.read().getConfig().forEach((key, value) -> {
                if (key.startsWith(LOG_CATEGORY_PREFIX) && key.endsWith(LOG_CATEGORY_SUFFIX)
                        && key.length() > LOG_CATEGORY_PREFIX.length() + LOG_CATEGORY_SUFFIX.length()) {
                    levels.put(key.substring(LOG_CATEGORY_PREFIX.length(), key.length() - LOG_CATEGORY_SUFFIX.length()), value);
                }
            });
        } catch (CliException ex) {
            // Config not available yet (e.g. before install) — logging config is best-effort.
        }
        return levels;
    }

    // Maps Quarkus/JBoss level names (DEBUG, TRACE, WARN, ...) to plain JUL levels. The packaged
    // CLI runs on plain JUL, where Level.parse does not recognise DEBUG/TRACE.
    static java.util.logging.Level toJulLevel(String value) {
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "OFF" -> java.util.logging.Level.OFF;
            case "FATAL", "ERROR", "SEVERE" -> java.util.logging.Level.SEVERE;
            case "WARN", "WARNING" -> java.util.logging.Level.WARNING;
            case "INFO" -> java.util.logging.Level.INFO;
            case "CONFIG" -> java.util.logging.Level.CONFIG;
            case "DEBUG", "FINE" -> java.util.logging.Level.FINE;
            case "FINER" -> java.util.logging.Level.FINER;
            case "TRACE", "FINEST" -> java.util.logging.Level.FINEST;
            case "ALL" -> java.util.logging.Level.ALL;
            default -> throw new IllegalArgumentException("Unknown log level: " + value);
        };
    }
}
