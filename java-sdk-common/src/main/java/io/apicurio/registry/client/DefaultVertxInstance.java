package io.apicurio.registry.client;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import java.util.logging.Logger;

/**
 * Holder class for lazy initialization of a shared default Vertx instance.
 * This ensures the Vertx instance is only created when actually needed and is
 * shared across all clients that don't provide their own Vertx instance.
 *
 * <p><strong>Warning:</strong> Using the default Vertx instance is not recommended for
 * production use. Applications should manage their own Vertx instance and provide it
 * via {@link RegistryClientOptions#vertx(Vertx)} to ensure proper lifecycle management
 * and resource cleanup.</p>
 */
public final class DefaultVertxInstance {

    private static final Logger logger = Logger.getLogger(DefaultVertxInstance.class.getName());
    private static volatile boolean closed = false;

    private DefaultVertxInstance() {
        // Prevent instantiation
    }

    private static class Holder {
        private static final Vertx INSTANCE;

        static {
            logger.warning("Using default shared Vertx instance. For production use, " +
                    "it is recommended to manage your own Vertx instance and provide it " +
                    "via RegistryClientOptions.vertx() to ensure proper lifecycle management.");

            var options = new VertxOptions();
            options.setUseDaemonThread(true);
            INSTANCE = Vertx.vertx(options);
        }
    }

    /**
     * Returns the shared default Vertx instance, creating it lazily on first access.
     *
     * <p><strong>Warning:</strong> This default instance is shared across all clients
     * and may not be properly closed. It is recommended to provide your own Vertx
     * instance via {@link RegistryClientOptions#vertx(Vertx)}.</p>
     *
     * @return the shared Vertx instance
     * @throws IllegalStateException if the instance has been closed
     */
    public static Vertx get() {
        if (closed) {
            throw new IllegalStateException("Default Vertx instance has been closed");
        }
        return Holder.INSTANCE;
    }

    /**
     * Closes the default Vertx instance if it has been initialized.
     * This method should be called during application shutdown to properly
     * release resources.
     *
     * <p>This method is idempotent - calling it multiple times has no effect
     * after the first call.</p>
     *
     * <p><strong>Note:</strong> After calling this method, subsequent calls to
     * {@link #get()} will throw an {@link IllegalStateException}.</p>
     */
    public static synchronized void close() {
        if (closed) {
            return;
        }
        get().close();
        closed = true;
    }
}
