package io.apicurio.registry.cli.auth;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.ConfigModel;
import io.apicurio.registry.cli.utils.OutputBuffer;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

/**
 * Authenticates with the registry using Basic auth or OAuth2 client credentials.
 */
@Command(
        name = "login",
        description = "Log in to the current registry context"
)
public class LoginCommand extends AbstractCommand {

    private static final String UNSAFE_STORAGE_WARNING =
            "Warning: Credentials stored in a file. This is not recommended for production use.\n";

    @Option(
            names = {"-u", "--username"},
            description = "Username for basic authentication."
    )
    private String username;

    @Option(
            names = {"-p", "--password"},
            description = "Password for basic authentication. If not provided, you will be prompted.",
            interactive = true,
            arity = "0..1"
    )
    private String password;

    @Option(
            names = {"--token-endpoint"},
            description = "OAuth2 token endpoint URL."
    )
    private String tokenEndpoint;

    @Option(
            names = {"--client-id"},
            description = "OAuth2 client ID."
    )
    private String clientId;

    @Option(
            names = {"--client-secret"},
            description = "OAuth2 client secret. If not provided, you will be prompted.",
            interactive = true,
            arity = "0..1"
    )
    private String clientSecret;

    @Option(
            names = {"--scope"},
            description = "OAuth2 scope (optional)."
    )
    private String scope;

    @Option(
            names = {"--allow-unsafe-credential-storage"},
            description = "Allow storing credentials in a file when the OS keychain is not available.",
            defaultValue = "false"
    )
    private boolean allowUnsafeCredentialStorage;

    @Inject
    CredentialStore credentialStore;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var contextName = config.getCurrentContext();
        if (isBlank(contextName)) {
            throw new CliException("No current context is set. Create a context first with 'acr context create'.",
                    VALIDATION_ERROR_RETURN_CODE);
        }
        final var context = Optional.ofNullable(config.getContext(contextName))
                .orElseThrow(() -> new CliException(
                        "Context '" + contextName + "' not found. Create it with 'acr context create'.",
                        VALIDATION_ERROR_RETURN_CODE));

        if (!isBlank(username)) {
            loginBasic(output, context, contextName);
        } else if (!isBlank(tokenEndpoint)) {
            loginOAuth2(output, context, contextName);
        } else {
            throw new CliException("Specify --username for basic auth or --token-endpoint for OAuth2.",
                    VALIDATION_ERROR_RETURN_CODE);
        }
    }

    private void loginBasic(final OutputBuffer output,
                            final ConfigModel.Context context,
                            final String contextName) {
        var resolvedPassword = password;
        if (isBlank(resolvedPassword)) {
            resolvedPassword = readSecret("Password: ",
                    "No console available for interactive password input. Use --password.",
                    "Password cannot be empty.");
        }

        final var allowUnsafe = allowUnsafeCredentialStorage || context.isUnsafeCredentialStorage();
        final var usedFileFallback = credentialStore.store(contextName, ConfigModel.CREDENTIAL_KEY_PASSWORD,
                resolvedPassword, allowUnsafe);
        credentialStore.delete(contextName, ConfigModel.CREDENTIAL_KEY_CLIENT_SECRET);

        config.updateContext(contextName, ctx -> {
            ctx.clearAuth();
            ctx.setAuthType(ConfigModel.AUTH_TYPE_BASIC);
            ctx.setUsername(username);
            ctx.setUnsafeCredentialStorage(usedFileFallback);
        });

        if (usedFileFallback) {
            output.writeStdOutChunk(out ->
                    out.append(UNSAFE_STORAGE_WARNING));
        }

        client.reset();

        output.writeStdOutChunk(out -> {
            out.append("Logged in to context '").append(contextName)
                    .append("' as '").append(username).append("'.\n");
        });
    }

    private void loginOAuth2(final OutputBuffer output,
                             final ConfigModel.Context context,
                             final String contextName) {
        if (isBlank(clientId)) {
            throw new CliException("--client-id is required for OAuth2 authentication.",
                    VALIDATION_ERROR_RETURN_CODE);
        }
        var resolvedSecret = clientSecret;
        if (isBlank(resolvedSecret)) {
            resolvedSecret = readSecret("Client Secret: ",
                    "No console available for interactive input. Use --client-secret.",
                    "Client secret cannot be empty.");
        }

        final var allowUnsafe = allowUnsafeCredentialStorage || context.isUnsafeCredentialStorage();
        final var usedFileFallback = credentialStore.store(contextName,
                ConfigModel.CREDENTIAL_KEY_CLIENT_SECRET, resolvedSecret, allowUnsafe);
        credentialStore.delete(contextName, ConfigModel.CREDENTIAL_KEY_PASSWORD);

        config.updateContext(contextName, ctx -> {
            ctx.clearAuth();
            ctx.setAuthType(ConfigModel.AUTH_TYPE_OAUTH2);
            ctx.setTokenEndpoint(tokenEndpoint);
            ctx.setClientId(clientId);
            ctx.setScope(scope);
            ctx.setUnsafeCredentialStorage(usedFileFallback);
        });

        if (usedFileFallback) {
            output.writeStdOutChunk(out ->
                    out.append(UNSAFE_STORAGE_WARNING));
        }

        client.reset();

        output.writeStdOutChunk(out -> {
            out.append("Logged in to context '").append(contextName).append("' via OAuth2.\n");
        });
    }

    private static String readSecret(final String prompt, final String noConsoleMessage,
                                     final String emptyMessage) {
        final var console = Optional.ofNullable(System.console())
                .orElseThrow(() -> new CliException(noConsoleMessage, VALIDATION_ERROR_RETURN_CODE));
        final var chars = console.readPassword(prompt);
        try {
            if (chars == null || chars.length == 0) {
                throw new CliException(emptyMessage, VALIDATION_ERROR_RETURN_CODE);
            }
            return new String(chars);
        } finally {
            if (chars != null) {
                Arrays.fill(chars, '\0');
            }
        }
    }
}
