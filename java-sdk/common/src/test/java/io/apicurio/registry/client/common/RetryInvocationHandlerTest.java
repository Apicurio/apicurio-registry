package io.apicurio.registry.client.common;

import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import com.microsoft.kiota.serialization.SerializationWriterFactory;
import com.microsoft.kiota.serialization.ValuedEnumParser;
import com.microsoft.kiota.store.BackingStoreFactory;
import io.vertx.core.http.HttpClosedException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link RegistryClientRequestAdapterFactory.RetryInvocationHandler}.
 * Validates that retryable exceptions are correctly identified even when wrapped
 * in cause chains (e.g., RuntimeException → ExecutionException → HttpClosedException).
 */
class RetryInvocationHandlerTest {

    private static final int MAX_RETRIES = 3;
    private static final long NO_DELAY = 0;
    private static final double BACKOFF = 2.0;
    private static final long NO_MAX_DELAY = 0;

    /**
     * Reproduces issue #7844: HttpClosedException wrapped in RuntimeException → ExecutionException
     * should be retried.
     */
    @Test
    void testWrappedHttpClosedExceptionIsRetried() throws Throwable {
        AtomicInteger callCount = new AtomicInteger(0);
        RequestAdapter adapter = throwingAdapter(() -> {
            if (callCount.incrementAndGet() <= 1) {
                throw new RuntimeException(
                        new ExecutionException(new HttpClosedException("Connection was closed")));
            }
            return "success";
        });

        var handler = new RegistryClientRequestAdapterFactory.RetryInvocationHandler(
                adapter, MAX_RETRIES, NO_DELAY, BACKOFF, NO_MAX_DELAY);

        Method method = RequestAdapter.class.getMethod("getBaseUrl");
        handler.invoke(null, method, null);

        assertEquals(2, callCount.get(),
                "Should have retried once after wrapped HttpClosedException");
    }

    /**
     * A direct HttpClosedException (not wrapped) should still be retried.
     */
    @Test
    void testDirectHttpClosedExceptionIsRetried() throws Throwable {
        AtomicInteger callCount = new AtomicInteger(0);
        RequestAdapter adapter = throwingAdapter(() -> {
            if (callCount.incrementAndGet() <= 1) {
                throw new HttpClosedException("Connection was closed");
            }
            return "success";
        });

        var handler = new RegistryClientRequestAdapterFactory.RetryInvocationHandler(
                adapter, MAX_RETRIES, NO_DELAY, BACKOFF, NO_MAX_DELAY);

        Method method = RequestAdapter.class.getMethod("getBaseUrl");
        handler.invoke(null, method, null);

        assertEquals(2, callCount.get(),
                "Should have retried once after direct HttpClosedException");
    }

    /**
     * A retryable exception nested 4+ levels deep should still trigger a retry.
     */
    @Test
    void testDeeplyNestedRetryableExceptionIsRetried() throws Throwable {
        AtomicInteger callCount = new AtomicInteger(0);
        RequestAdapter adapter = throwingAdapter(() -> {
            if (callCount.incrementAndGet() <= 1) {
                throw new RuntimeException(
                        new RuntimeException(
                                new ExecutionException(
                                        new RuntimeException(
                                                new ConnectException("Connection refused")))));
            }
            return "success";
        });

        var handler = new RegistryClientRequestAdapterFactory.RetryInvocationHandler(
                adapter, MAX_RETRIES, NO_DELAY, BACKOFF, NO_MAX_DELAY);

        Method method = RequestAdapter.class.getMethod("getBaseUrl");
        handler.invoke(null, method, null);

        assertEquals(2, callCount.get(),
                "Should have retried once after deeply nested ConnectException");
    }

    /**
     * A non-retryable exception should be thrown immediately without retry.
     */
    @Test
    void testNonRetryableExceptionIsNotRetried() throws NoSuchMethodException {
        AtomicInteger callCount = new AtomicInteger(0);
        RequestAdapter adapter = throwingAdapter(() -> {
            callCount.incrementAndGet();
            throw new IllegalArgumentException("bad argument");
        });

        var handler = new RegistryClientRequestAdapterFactory.RetryInvocationHandler(
                adapter, MAX_RETRIES, NO_DELAY, BACKOFF, NO_MAX_DELAY);

        Method method = RequestAdapter.class.getMethod("getBaseUrl");
        assertThrows(IllegalArgumentException.class, () -> handler.invoke(null, method, null),
                "Non-retryable exception should propagate immediately");
        assertEquals(1, callCount.get(),
                "Should have been called exactly once with no retries");
    }

    /**
     * When all retry attempts are exhausted, the original exception should be thrown.
     */
    @Test
    void testExhaustedRetriesThrowsOriginalException() throws NoSuchMethodException {
        AtomicInteger callCount = new AtomicInteger(0);
        RequestAdapter adapter = throwingAdapter(() -> {
            callCount.incrementAndGet();
            throw new RuntimeException(
                    new ExecutionException(new HttpClosedException("Connection was closed")));
        });

        var handler = new RegistryClientRequestAdapterFactory.RetryInvocationHandler(
                adapter, MAX_RETRIES, NO_DELAY, BACKOFF, NO_MAX_DELAY);

        Method method = RequestAdapter.class.getMethod("getBaseUrl");
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> handler.invoke(null, method, null),
                "Should throw after exhausting retries");

        assertEquals(MAX_RETRIES + 1, callCount.get(),
                "Should have been called initial + MAX_RETRIES times");
        assertEquals(ExecutionException.class, thrown.getCause().getClass(),
                "Cause chain should be preserved");
    }

    /**
     * Wrapped SocketTimeoutException should be retried.
     */
    @Test
    void testWrappedSocketTimeoutExceptionIsRetried() throws Throwable {
        AtomicInteger callCount = new AtomicInteger(0);
        RequestAdapter adapter = throwingAdapter(() -> {
            if (callCount.incrementAndGet() <= 1) {
                throw new RuntimeException(new SocketTimeoutException("Read timed out"));
            }
            return "success";
        });

        var handler = new RegistryClientRequestAdapterFactory.RetryInvocationHandler(
                adapter, MAX_RETRIES, NO_DELAY, BACKOFF, NO_MAX_DELAY);

        Method method = RequestAdapter.class.getMethod("getBaseUrl");
        handler.invoke(null, method, null);

        assertEquals(2, callCount.get(),
                "Should have retried once after wrapped SocketTimeoutException");
    }

    /**
     * Wrapped IOException with "Connection reset" message should be retried.
     */
    @Test
    void testWrappedConnectionResetIsRetried() throws Throwable {
        AtomicInteger callCount = new AtomicInteger(0);
        RequestAdapter adapter = throwingAdapter(() -> {
            if (callCount.incrementAndGet() <= 1) {
                throw new RuntimeException(new IOException("Connection reset"));
            }
            return "success";
        });

        var handler = new RegistryClientRequestAdapterFactory.RetryInvocationHandler(
                adapter, MAX_RETRIES, NO_DELAY, BACKOFF, NO_MAX_DELAY);

        Method method = RequestAdapter.class.getMethod("getBaseUrl");
        handler.invoke(null, method, null);

        assertEquals(2, callCount.get(),
                "Should have retried once after wrapped Connection reset IOException");
    }

    /**
     * Simulates the production scenario where Method.invoke() does NOT wrap the exception in
     * InvocationTargetException (observed with JDK 21's DirectMethodHandleAccessor). The
     * RuntimeException with a retryable cause should still trigger retries.
     */
    @Test
    void testRuntimeExceptionWithRetryableCauseIsRetried() throws Throwable {
        AtomicInteger callCount = new AtomicInteger(0);
        RequestAdapter realAdapter = throwingAdapter(() -> {
            if (callCount.incrementAndGet() <= 1) {
                throw new RuntimeException(
                        new ExecutionException(new HttpClosedException("Connection was closed")));
            }
            return "success";
        });

        RequestAdapter proxy = (RequestAdapter) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{RequestAdapter.class},
                new RegistryClientRequestAdapterFactory.RetryInvocationHandler(
                        realAdapter, MAX_RETRIES, NO_DELAY, BACKOFF, NO_MAX_DELAY));

        proxy.getBaseUrl();

        assertEquals(2, callCount.get(),
                "Should have retried once after RuntimeException with retryable cause");
    }

    /**
     * A RuntimeException wrapping a non-retryable cause should propagate immediately.
     */
    @Test
    void testRuntimeExceptionWithNonRetryableCauseIsNotRetried() {
        AtomicInteger callCount = new AtomicInteger(0);
        RequestAdapter realAdapter = throwingAdapter(() -> {
            callCount.incrementAndGet();
            throw new RuntimeException(new IllegalStateException("not retryable"));
        });

        RequestAdapter proxy = (RequestAdapter) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{RequestAdapter.class},
                new RegistryClientRequestAdapterFactory.RetryInvocationHandler(
                        realAdapter, MAX_RETRIES, NO_DELAY, BACKOFF, NO_MAX_DELAY));

        assertThrows(RuntimeException.class, proxy::getBaseUrl,
                "Non-retryable RuntimeException should propagate immediately");
        assertEquals(1, callCount.get(),
                "Should have been called exactly once with no retries");
    }

    /**
     * End-to-end test through a real JDK Proxy to exercise the actual proxy dispatch path
     * with HttpClosedException wrapped in RuntimeException → ExecutionException.
     */
    @Test
    void testProxyBasedRetryWithHttpClosedException() throws Throwable {
        AtomicInteger callCount = new AtomicInteger(0);
        RequestAdapter realAdapter = throwingAdapter(() -> {
            if (callCount.incrementAndGet() <= 2) {
                throw new RuntimeException(
                        new ExecutionException(new HttpClosedException("Connection was closed")));
            }
            return "success";
        });

        RequestAdapter proxy = (RequestAdapter) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{RequestAdapter.class},
                new RegistryClientRequestAdapterFactory.RetryInvocationHandler(
                        realAdapter, MAX_RETRIES, NO_DELAY, BACKOFF, NO_MAX_DELAY));

        String result = proxy.getBaseUrl();

        assertEquals("success", result, "Should succeed after retries");
        assertEquals(3, callCount.get(),
                "Should have been called 3 times (1 initial + 2 retries)");
    }

    // ==================== Test Helpers ====================

    @FunctionalInterface
    interface ThrowingSupplier {
        Object get() throws Throwable;
    }

    /**
     * Creates a {@link RequestAdapter} whose {@code getBaseUrl()} delegates to the given supplier,
     * allowing tests to control when and what exceptions are thrown.
     */
    private static RequestAdapter throwingAdapter(ThrowingSupplier supplier) {
        return new StubRequestAdapter(supplier);
    }

    /**
     * Minimal {@link RequestAdapter} implementation for testing retry behavior.
     * Only {@code getBaseUrl()} is functional; all other methods are no-ops.
     */
    private static class StubRequestAdapter implements RequestAdapter {
        private final ThrowingSupplier supplier;

        StubRequestAdapter(ThrowingSupplier supplier) {
            this.supplier = supplier;
        }

        @Nonnull
        @Override
        public String getBaseUrl() {
            try {
                return (String) supplier.get();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        @Override
        public void enableBackingStore(@Nullable BackingStoreFactory backingStoreFactory) {
        }

        @Nonnull
        @Override
        public SerializationWriterFactory getSerializationWriterFactory() {
            return null;
        }

        @Nullable
        @Override
        public <ModelType extends Parsable> ModelType send(@Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull ParsableFactory<ModelType> factory) {
            return null;
        }

        @Nullable
        @Override
        public <ModelType extends Parsable> List<ModelType> sendCollection(
                @Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull ParsableFactory<ModelType> factory) {
            return null;
        }

        @Nullable
        @Override
        public <ModelType> ModelType sendPrimitive(@Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull Class<ModelType> targetClass) {
            return null;
        }

        @Nullable
        @Override
        public <ModelType> List<ModelType> sendPrimitiveCollection(
                @Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull Class<ModelType> targetClass) {
            return null;
        }

        @Nullable
        @Override
        public <ModelType extends Enum<ModelType>> ModelType sendEnum(
                @Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull ValuedEnumParser<ModelType> enumParser) {
            return null;
        }

        @Nullable
        @Override
        public <ModelType extends Enum<ModelType>> List<ModelType> sendEnumCollection(
                @Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull ValuedEnumParser<ModelType> enumParser) {
            return null;
        }

        @Override
        public void setBaseUrl(@Nonnull String baseUrl) {
        }

        @Nonnull
        @Override
        public <T> T convertToNativeRequest(@Nonnull RequestInformation requestInfo) {
            return null;
        }
    }
}
