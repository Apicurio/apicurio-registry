package io.apicurio.registry.mcp;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link McpHttpGateFilter} request gating.
 */
class McpHttpGateFilterTest {

    private McpHttpGateFilter filter;
    private TestHttp http;
    private AtomicInteger statusCode;
    private AtomicBoolean ended;
    private AtomicBoolean nextCalled;
    private AtomicReference<String> responseBody;
    private AtomicReference<String> contentType;
    private RoutingContext context;

    @BeforeEach
    void setUp() {
        filter = new McpHttpGateFilter();
        http = new TestHttp(false);
        filter.config = new GateMcpConfig(http);
        statusCode = new AtomicInteger(-1);
        ended = new AtomicBoolean(false);
        nextCalled = new AtomicBoolean(false);
        responseBody = new AtomicReference<>();
        contentType = new AtomicReference<>();
        context = stubRoutingContext();
    }

    @Test
    void blocksWith503WhenHttpModeDisabled() {
        http.enabled = false;

        filter.blockUnlessEnabled(context);

        assertEquals(503, statusCode.get());
        assertTrue(ended.get());
        assertFalse(nextCalled.get());
        assertTrue(contentType.get().contains("text/plain"));
        assertTrue(responseBody.get().contains("MCP HTTP transport is disabled"));
        assertTrue(responseBody.get().contains("apicurio.mcp.http.enabled=true"));
    }

    @Test
    void continuesWhenHttpModeEnabled() {
        http.enabled = true;

        filter.blockUnlessEnabled(context);

        assertEquals(-1, statusCode.get());
        assertFalse(ended.get());
        assertTrue(nextCalled.get());
    }

    private RoutingContext stubRoutingContext() {
        HttpServerResponse response = (HttpServerResponse) Proxy.newProxyInstance(
                HttpServerResponse.class.getClassLoader(),
                new Class<?>[] {HttpServerResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setStatusCode".equals(name)) {
                        statusCode.set((Integer) args[0]);
                        return proxy;
                    }
                    if ("putHeader".equals(name)) {
                        if ("Content-Type".equals(String.valueOf(args[0]))) {
                            contentType.set(String.valueOf(args[1]));
                        }
                        return proxy;
                    }
                    if ("end".equals(name)) {
                        ended.set(true);
                        if (args != null && args.length == 1 && args[0] instanceof String body) {
                            responseBody.set(body);
                        }
                        return Future.succeededFuture();
                    }
                    if (method.getReturnType().equals(void.class)) {
                        return null;
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    return null;
                });

        return (RoutingContext) Proxy.newProxyInstance(
                RoutingContext.class.getClassLoader(),
                new Class<?>[] {RoutingContext.class},
                (proxy, method, args) -> {
                    if ("response".equals(method.getName())) {
                        return response;
                    }
                    if ("next".equals(method.getName())) {
                        nextCalled.set(true);
                        return null;
                    }
                    if (method.getReturnType().equals(void.class)) {
                        return null;
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    return null;
                });
    }

    private static final class TestHttp implements McpConfig.Http {
        private boolean enabled;

        private TestHttp(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public boolean forwardToken() {
            return true;
        }
    }

    private static final class GateMcpConfig implements McpConfig {
        private final TestHttp http;

        private GateMcpConfig(TestHttp http) {
            this.http = http;
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
            return new Auth() {
                @Override
                public boolean enabled() {
                    return false;
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
            };
        }

        @Override
        public Tls tls() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http http() {
            return http;
        }
    }
}
