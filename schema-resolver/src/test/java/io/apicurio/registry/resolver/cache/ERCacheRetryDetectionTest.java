package io.apicurio.registry.resolver.cache;

import com.microsoft.kiota.ApiException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the HTTP 429 retry-detection bug: {@code ERCache}'s retry loop checked
 * {@code e.getCause() instanceof ApiException}, but the real call path
 * ({@code RegistryClientFacadeImpl}) throws {@link ApiException} directly with no wrapping, so
 * {@code e} itself <strong>is</strong> the {@link ApiException} and {@code e.getCause()} is
 * {@code null}. As a result, a 429 from the registry was never actually retried by this layer.
 * <p>
 * Fixed by walking the cause chain starting at {@code e} itself ({@code isRetriable429}), which also
 * correctly handles exceptions wrapped by intermediate layers (e.g. {@code ExecutionException}).
 */
class ERCacheRetryDetectionTest {

    @Test
    void retriesDirectApiExceptionWith429() {
        // This is the real-world shape: RegistryClientFacadeImpl throws ApiException directly,
        // unwrapped. The old `e.getCause() instanceof ApiException` check could never match this.
        assertTrue(ERCache.isRetriable429(api(429)));
    }

    @Test
    void retriesWrapped429() {
        assertTrue(ERCache.isRetriable429(new RuntimeException(new ExecutionException(api(429)))));
        assertTrue(ERCache.isRetriable429(new RuntimeException(new RuntimeException(api(429)))));
    }

    @Test
    void doesNotRetryOtherApiStatuses() {
        assertFalse(ERCache.isRetriable429(api(404)));
        assertFalse(ERCache.isRetriable429(api(500)));
        assertFalse(ERCache.isRetriable429(api(503)));
    }

    @Test
    void doesNotRetryNonApiFailures() {
        assertFalse(ERCache.isRetriable429(new RuntimeException("boom")));
    }

    @Test
    void causeWalkHasDepthCapAgainstCycles() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b");
        a.initCause(b);
        b.initCause(a);
        assertFalse(ERCache.isRetriable429(a));
    }

    private static ApiException api(int status) {
        return new TestApiException(status);
    }

    private static final class TestApiException extends ApiException {
        TestApiException(int status) {
            super("HTTP " + status);
            setResponseStatusCode(status);
        }
    }
}
