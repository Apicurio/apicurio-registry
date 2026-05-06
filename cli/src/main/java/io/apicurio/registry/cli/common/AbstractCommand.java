package io.apicurio.registry.cli.common;

import io.apicurio.registry.cli.Acr;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.services.UpdateNotifier;
import io.apicurio.registry.cli.utils.OutputBuffer;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.OK_RETURN_CODE;

@Command
public abstract class AbstractCommand implements Callable<Integer> {

    private static final Logger log = Logger.getLogger(AbstractCommand.class);

    @Spec
    CommandSpec spec;

    @Inject
    protected Config config;

    @Inject
    protected Client client;

    @Inject
    UpdateNotifier updateNotifier;

    @Override
    public Integer call() {
        configureVerboseLogging();
        var output = new OutputBuffer(config.getStdOut(), config.getStdErr());
        try {
            run(output);
            return OK_RETURN_CODE;
        } catch (CliException ex) {
            if (!ex.isQuiet()) {
                if (log.isDebugEnabled()) {
                    log.error("Error", ex); // Force printing of stack trace.
                } else {
                    output.writeStdErrChunk(out -> {
                        out.append("Error: ")
                                .append(ex.getMessage())
                                .append("\n");
                    });
                }
            }
            return ex.getCode();
        } catch (Exception ex) {
            log.error("Unexpected error", ex); // Force printing of stack trace.
            return APPLICATION_ERROR_RETURN_CODE;
        } finally {
            output.print();
            checkForUpdates();
        }
        // TODO: Move handling of `ProblemDetails` exceptions here.
    }

    public abstract void run(OutputBuffer output) throws Exception;

    private void checkForUpdates() {
        try {
            var commandName = getTopLevelCommandName();
            log.debugf("Update check hook: command=%s (spec=%s)", commandName, spec.name());
            updateNotifier.checkAndNotify(commandName);
        } catch (Exception e) {
            log.debugf("Update notification failed: %s", e.getMessage());
        }
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
