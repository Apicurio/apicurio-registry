package io.apicurio.registry.mcp;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.credential.Credential;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.Permission;
import java.security.Principal;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RegistryClientResolver} token extraction and fallback selection.
 */
class RegistryClientResolverTest {

    private RegistryClientResolver resolver;
    private TestHttp http;
    private TestAuth auth;
    private ResolvableInstance<SecurityIdentity> securityIdentity;
    private ResolvableInstance<JsonWebToken> jwt;

    @BeforeEach
    void setUp() {
        resolver = new RegistryClientResolver();
        resolver.rawBaseUrl = "http://localhost:8080";
        http = new TestHttp(true, true);
        auth = new TestAuth(false);
        resolver.config = new TestMcpConfig(http, auth);
        securityIdentity = new ResolvableInstance<>();
        jwt = new ResolvableInstance<>();
        resolver.securityIdentity = securityIdentity;
        resolver.jwt = jwt;
    }

    @Test
    void needsFallbackClientWhenHttpDisabled() {
        http.enabled = false;
        assertTrue(resolver.needsFallbackClient());
    }

    @Test
    void needsFallbackClientWhenAuthEnabledAlongsideHttp() {
        auth.enabled = true;
        assertTrue(resolver.needsFallbackClient());
    }

    @Test
    void needsFallbackClientWhenForwardTokenDisabled() {
        http.forwardToken = false;
        assertTrue(resolver.needsFallbackClient());
    }

    @Test
    void doesNotNeedFallbackClientWhenHttpForwardTokenOnly() {
        assertFalse(resolver.needsFallbackClient());
    }

    @Test
    void resolveInboundBearerTokenReturnsNullWhenHttpDisabled() {
        http.enabled = false;
        securityIdentity.set(new TestSecurityIdentity(false, "identity-token"));
        assertNull(resolver.resolveInboundBearerToken());
    }

    @Test
    void resolveInboundBearerTokenReturnsNullWhenForwardTokenDisabled() {
        http.forwardToken = false;
        securityIdentity.set(new TestSecurityIdentity(false, "identity-token"));
        assertNull(resolver.resolveInboundBearerToken());
    }

    @Test
    void resolveInboundBearerTokenFromSecurityIdentity() {
        securityIdentity.set(new TestSecurityIdentity(false, "identity-token"));
        jwt.set(new TestJsonWebToken("jwt-token"));

        assertEquals("identity-token", resolver.resolveInboundBearerToken());
    }

    @Test
    void resolveInboundBearerTokenFallsBackToJsonWebToken() {
        jwt.set(new TestJsonWebToken("jwt-token"));

        assertEquals("jwt-token", resolver.resolveInboundBearerToken());
    }

    @Test
    void resolveInboundBearerTokenIgnoresAnonymousSecurityIdentity() {
        securityIdentity.set(new TestSecurityIdentity(true, null));
        jwt.set(new TestJsonWebToken("jwt-token"));

        assertEquals("jwt-token", resolver.resolveInboundBearerToken());
    }

    @Test
    void resolveInboundBearerTokenIgnoresBlankTokens() {
        securityIdentity.set(new TestSecurityIdentity(false, "   "));
        jwt.set(new TestJsonWebToken("   "));

        assertNull(resolver.resolveInboundBearerToken());
    }

    @Test
    void resolveInboundBearerTokenReturnsNullWhenNoCredentialOrJwt() {
        securityIdentity.set(new TestSecurityIdentity(false, null));

        assertNull(resolver.resolveInboundBearerToken());
    }

    @Test
    void resolveInboundBearerTokenRejectsExpiredJsonWebToken() {
        long expired = Instant.now().getEpochSecond() - 60;
        jwt.set(new TestJsonWebToken(jwtWithExp(expired), expired));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                resolver::resolveInboundBearerToken);
        assertTrue(error.getMessage().contains("expired"));
    }

    @Test
    void resolveInboundBearerTokenRejectsExpiredRawJwtFromIdentity() {
        long expired = Instant.now().getEpochSecond() - 60;
        String raw = jwtWithExp(expired);
        securityIdentity.set(new TestSecurityIdentity(false, raw));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                resolver::resolveInboundBearerToken);
        assertTrue(error.getMessage().contains("expired"));
    }

    @Test
    void resolveInboundBearerTokenAllowsUnexpiredToken() {
        long future = Instant.now().getEpochSecond() + 3600;
        String raw = jwtWithExp(future);
        jwt.set(new TestJsonWebToken(raw, future));

        assertEquals(raw, resolver.resolveInboundBearerToken());
    }

    @Test
    void parseJwtExpirationEpochSecondsReadsExpClaim() {
        long exp = Instant.now().getEpochSecond() + 120;
        assertEquals(exp, RegistryClientResolver.parseJwtExpirationEpochSeconds(jwtWithExp(exp)));
    }

    @Test
    void parseJwtExpirationEpochSecondsReturnsNullForOpaqueToken() {
        assertNull(RegistryClientResolver.parseJwtExpirationEpochSeconds("not-a-jwt"));
    }

    @Test
    void getClientReturnsFallbackWhenNoInboundToken() throws Exception {
        RegistryClient fallback = markerClient();
        setFallbackClient(fallback);

        assertSame(fallback, resolver.getClient());
        assertTrue(resolver.hasFallbackClient());
    }

    @Test
    void getClientFailsWhenForwardingEnabledAuthenticatedButNoToken() throws Exception {
        setFallbackClient(null);
        securityIdentity.set(new TestSecurityIdentity(false, null));

        IllegalStateException error = assertThrows(IllegalStateException.class, resolver::getClient);
        assertTrue(error.getMessage().contains("no bearer access token was found"));
        assertTrue(error.getMessage().contains("Authorization: Bearer"));
    }

    @Test
    void getClientFailsWhenForwardingEnabledAndNoTokenOrFallback() throws Exception {
        setFallbackClient(null);

        IllegalStateException error = assertThrows(IllegalStateException.class, resolver::getClient);
        assertTrue(error.getMessage().contains("no bearer token was provided"));
        assertTrue(error.getMessage().contains("no fallback client credentials are configured"));
    }

    @Test
    void getClientAppliesBearerTokenWithoutMutatingCachedTransportOptions() throws Exception {
        RegistryClientOptions cached = RegistryClientOptions.create("http://localhost:8080")
                .httpAdapter(HttpAdapterType.JDK)
                .retry()
                .trustAll(true)
                .verifyHost(false);
        setTransportOptions(cached);
        securityIdentity.set(new TestSecurityIdentity(false, "per-request-token"));

        RegistryClient client = resolver.getClient();

        assertNotNull(client);
        assertEquals(RegistryClientOptions.AuthType.ANONYMOUS, cached.getAuthType());
        assertNull(cached.getBearerToken());
        assertTrue(cached.isTrustAll());
        assertFalse(cached.isVerifyHost());
    }

    private static String jwtWithExp(long expEpochSeconds) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"exp\":" + expEpochSeconds + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    private static RegistryClient markerClient() {
        RequestAdapter adapter = (RequestAdapter) Proxy.newProxyInstance(
                RequestAdapter.class.getClassLoader(),
                new Class<?>[] {RequestAdapter.class},
                (proxy, method, args) -> {
                    if ("getBaseUrl".equals(method.getName())) {
                        return "http://localhost:8080";
                    }
                    if (method.getReturnType().equals(void.class)) {
                        return null;
                    }
                    return null;
                });
        return new RegistryClient(adapter);
    }

    private void setFallbackClient(RegistryClient client) throws Exception {
        Field field = RegistryClientResolver.class.getDeclaredField("fallbackClient");
        field.setAccessible(true);
        field.set(resolver, client);
    }

    private void setTransportOptions(RegistryClientOptions options) throws Exception {
        Field field = RegistryClientResolver.class.getDeclaredField("transportOptions");
        field.setAccessible(true);
        field.set(resolver, options);
    }

    private static final class TestMcpConfig implements McpConfig {
        private final TestHttp http;
        private final TestAuth auth;
        private final TestTls tls = new TestTls();

        private TestMcpConfig(TestHttp http, TestAuth auth) {
            this.http = http;
            this.auth = auth;
        }

        @Override
        public boolean safeMode() {
            return true;
        }

        @Override
        public Paging paging() {
            return new Paging() {
                @Override
                public int limit() {
                    return 200;
                }

                @Override
                public boolean limitError() {
                    return true;
                }
            };
        }

        @Override
        public Auth auth() {
            return auth;
        }

        @Override
        public Tls tls() {
            return tls;
        }

        @Override
        public Http http() {
            return http;
        }
    }

    private static final class TestHttp implements McpConfig.Http {
        private boolean enabled;
        private boolean forwardToken;

        private TestHttp(boolean enabled, boolean forwardToken) {
            this.enabled = enabled;
            this.forwardToken = forwardToken;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public boolean forwardToken() {
            return forwardToken;
        }
    }

    private static final class TestAuth implements McpConfig.Auth {
        private boolean enabled;

        private TestAuth(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Optional<String> tokenEndpoint() {
            return Optional.empty();
        }

        @Override
        public Optional<String> clientId() {
            return Optional.empty();
        }

        @Override
        public Optional<String> clientSecret() {
            return Optional.empty();
        }

        @Override
        public Optional<String> scope() {
            return Optional.empty();
        }
    }

    private static final class TestTls implements McpConfig.Tls {
        @Override
        public boolean trustAll() {
            return false;
        }

        @Override
        public boolean verifyHost() {
            return true;
        }

        @Override
        public McpConfig.Truststore truststore() {
            return new McpConfig.Truststore() {
                @Override
                public Optional<String> type() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> path() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> password() {
                    return Optional.empty();
                }
            };
        }

        @Override
        public McpConfig.Keystore keystore() {
            return new McpConfig.Keystore() {
                @Override
                public Optional<String> type() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> path() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> password() {
                    return Optional.empty();
                }
            };
        }
    }

    private static final class ResolvableInstance<T> implements Instance<T> {
        private T value;

        void set(T value) {
            this.value = value;
        }

        @Override
        public Instance<T> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            return value == null;
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public Handle<T> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<T>> handles() {
            return Collections.emptyList();
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public Iterator<T> iterator() {
            return value == null ? Collections.emptyIterator() : Collections.singleton(value).iterator();
        }

        @Override
        public void destroy(T instance) {
            // no-op
        }
    }

    private static final class TestSecurityIdentity implements SecurityIdentity {
        private final boolean anonymous;
        private final AccessTokenCredential credential;

        private TestSecurityIdentity(boolean anonymous, String token) {
            this.anonymous = anonymous;
            this.credential = token == null ? null : new AccessTokenCredential(token);
        }

        @Override
        public Principal getPrincipal() {
            return () -> "test";
        }

        @Override
        public boolean isAnonymous() {
            return anonymous;
        }

        @Override
        public Set<String> getRoles() {
            return Collections.emptySet();
        }

        @Override
        public boolean hasRole(String role) {
            return false;
        }

        @Override
        public Set<Permission> getPermissions() {
            return Collections.emptySet();
        }

        @Override
        public <T> T getAttribute(String name) {
            return null;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Collections.emptyMap();
        }

        @Override
        public Uni<Boolean> checkPermission(Permission permission) {
            return Uni.createFrom().item(false);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Credential> T getCredential(Class<T> credentialType) {
            if (credential != null && credentialType.isInstance(credential)) {
                return (T) credential;
            }
            return null;
        }

        @Override
        public Set<Credential> getCredentials() {
            return credential == null ? Collections.emptySet() : Set.of(credential);
        }
    }

    private static final class TestJsonWebToken implements JsonWebToken {
        private final String rawToken;
        private final long expirationTime;

        private TestJsonWebToken(String rawToken) {
            this(rawToken, 0);
        }

        private TestJsonWebToken(String rawToken, long expirationTime) {
            this.rawToken = rawToken;
            this.expirationTime = expirationTime;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public Set<String> getClaimNames() {
            return Collections.emptySet();
        }

        @Override
        public <T> T getClaim(String claimName) {
            return null;
        }

        @Override
        public String getRawToken() {
            return rawToken;
        }

        @Override
        public long getExpirationTime() {
            return expirationTime;
        }
    }
}
