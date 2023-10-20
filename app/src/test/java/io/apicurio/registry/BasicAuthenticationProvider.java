package io.apicurio.registry;

import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.authentication.AuthenticationProvider;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BasicAuthenticationProvider implements AuthenticationProvider {

    private final String username;
    private final String password;
    private final String encoded;

    public BasicAuthenticationProvider(String username, String password) {
        this.username = username;
        this.password = password;
        encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private final static String authorizationHeaderKey = "Authorization";
    public static final String BASIC = "Basic ";
    @Override
    public CompletableFuture<Void> authenticateRequest(@Nonnull final RequestInformation request, @Nullable final Map<String, Object> additionalAuthenticationContext) {
        request.headers.add(authorizationHeaderKey, BASIC + encoded);
        return CompletableFuture.completedFuture(null);
    }
}
