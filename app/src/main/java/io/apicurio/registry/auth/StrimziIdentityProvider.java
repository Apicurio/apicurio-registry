package io.apicurio.registry.auth;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.Base64;
import java.util.Optional;

@Priority(IdentityProvider.SYSTEM_FIRST)
@ApplicationScoped
public class StrimziIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {
    @Inject
    Logger log;

    @Inject
    AuthConfig authConfig;

    public StrimziIdentityProvider() {
    }

    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    public Uni<SecurityIdentity> authenticate(final UsernamePasswordAuthenticationRequest request, AuthenticationRequestContext context) {
        if (authConfig.basicAuthWithStrimziUserEnabled.get()) {
            return context.runBlocking(() -> {
                String givenUsername = request.getUsername();
                String givenPassword = new String(request.getPassword().getPassword());
                boolean passwordCorrect = Optional.ofNullable(getPasswordOfKafkaUser(givenUsername))
                    .map(actualPassword -> actualPassword.equals(givenPassword))
                    .orElse(false);
                if (passwordCorrect) {
                    return QuarkusSecurityIdentity.builder()
                        .addCredential(request.getPassword())
                        .setPrincipal(new QuarkusPrincipal(givenUsername))
                        .build();
                } else {
                    throw new AuthenticationFailedException("Invalid username or password");
                }
            });
        } else {
            // return null instead of throwing an exception to allow other identity providers to try to authenticate
            return Uni.createFrom().nullItem();
        }
    }

    private String getPasswordOfKafkaUser(String username) {
        if (username == null) {
            return null;
        }
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            // get the password from the Kubernetes secret managed by the Strimzi User Operator
            return client.secrets()
                .inNamespace(authConfig.strimziKubernetesNamespace)
                .withLabel("strimzi.io/kind", "KafkaUser")
                .resources()
                .filter(secret -> secret.item().getMetadata().getName().equals(username))
                .findFirst()
                .map(secret -> secret.item().getData().get("password"))
                .map(Base64.getDecoder()::decode)
                .map(String::new)
                .orElse(null);
        }
    }
}
