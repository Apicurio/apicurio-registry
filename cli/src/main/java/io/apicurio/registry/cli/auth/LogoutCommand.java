package io.apicurio.registry.cli.auth;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.ConfigModel;
import io.apicurio.registry.cli.utils.OutputBuffer;
import jakarta.inject.Inject;
import java.util.Optional;
import picocli.CommandLine.Command;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

/**
 * Clears credentials from the OS keychain and resets auth config for the current context.
 */
@Command(
        name = "logout",
        description = "Log out of the current registry context"
)
public class LogoutCommand extends AbstractCommand {

    @Inject
    CredentialStore credentialStore;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var contextName = config.getCurrentContext();
        if (isBlank(contextName)) {
            throw new CliException("No current context is set.", VALIDATION_ERROR_RETURN_CODE);
        }
        final var context = Optional.ofNullable(config.getContext(contextName))
                .orElseThrow(() -> new CliException(
                        "Context '" + contextName + "' not found.", VALIDATION_ERROR_RETURN_CODE));

        if (isBlank(context.getAuthType())) {
            output.writeStdOutChunk(out -> {
                out.append("Context '").append(contextName).append("' is not logged in.\n");
            });
            return;
        }

        credentialStore.delete(contextName, ConfigModel.CREDENTIAL_KEY_PASSWORD);
        credentialStore.delete(contextName, ConfigModel.CREDENTIAL_KEY_CLIENT_SECRET);

        config.updateContext(contextName, ConfigModel.Context::clearAuth);

        client.reset();

        output.writeStdOutChunk(out -> {
            out.append("Logged out of context '").append(contextName).append("'.\n");
        });
    }
}
