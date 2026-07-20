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
            configureVerboseLogging();
            updateNotifier.checkAndNotify(getTopLevelCommandName());
            if (isInteractiveRequested() && supportsInteractive()) {
                runInteractive();
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
     * and the --interactive flag was passed. Bypasses the OutputBuffer
     * batch model entirely — this owns the terminal directly.
     */
    public void runInteractive() throws Exception {
        throw new UnsupportedOperationException("Interactive mode not implemented for this command.");
    }

    private boolean isInteractiveRequested() {
        return spec.commandLine().getParseResult().hasMatchedOption("--interactive");
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

    private void configureVerboseLogging() {
        var root = spec.root().userObject();
        if (root instanceof Acr acr && acr.isVerbose()) {
            // Set the root logger level to FINE (DEBUG equivalent)
            var rootLogger = java.util.logging.Logger.getLogger("");
            rootLogger.setLevel(java.util.logging.Level.FINE);
            // Also lower handler levels — Quarkus sets quarkus.log.level=WARN on the
            // console handler, which filters debug messages even when the logger allows them.
            for (var handler : rootLogger.getHandlers()) {
                handler.setLevel(java.util.logging.Level.FINE);
            }
            log.debug("Verbose logging enabled.");
        }
    }
}