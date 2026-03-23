package io.apicurio.registry.cli.common;

import io.apicurio.registry.cli.Acr;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.OutputBuffer;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.util.concurrent.Callable;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.OK_RETURN_CODE;

@CommandLine.Command
public abstract class AbstractCommand implements Callable<Integer> {

    private static final Logger log = Logger.getLogger(AbstractCommand.class);

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        configureVerboseLogging();
        var output = new OutputBuffer(Config.getInstance().getStdOut(), Config.getInstance().getStdErr());
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
        }
        // TODO: Move handling of `ProblemDetails` exceptions here.
    }

    public abstract void run(OutputBuffer output) throws Exception;

    private void configureVerboseLogging() {
        var root = spec.root().userObject();
        if (root instanceof Acr acr && acr.isVerbose()) {
            java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.FINE);
            log.debug("Verbose logging enabled.");
        }
    }
}
