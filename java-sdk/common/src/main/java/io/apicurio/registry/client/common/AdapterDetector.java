package io.apicurio.registry.client.common;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for detecting available HTTP adapters at runtime.
 * This enables the SDK to work with either Vert.x or JDK HTTP adapter
 * depending on which dependencies are available on the classpath.
 */
public final class AdapterDetector {

    private static final Logger log = Logger.getLogger(AdapterDetector.class.getName());

    private static final boolean VERTX_AVAILABLE;
    private static final boolean JDK_ADAPTER_AVAILABLE;

    static {
        VERTX_AVAILABLE = isClassAvailable("io.kiota.http.vertx.VertXRequestAdapter");
        JDK_ADAPTER_AVAILABLE = isClassAvailable("io.kiota.http.jdk.JDKRequestAdapter");

        log.log(Level.FINE, "Adapter detection: Vert.x={0}, JDK={1}",
                new Object[]{VERTX_AVAILABLE, JDK_ADAPTER_AVAILABLE});
    }

    private AdapterDetector() {
        // Prevent instantiation
    }

    /**
     * Checks if the Vert.x HTTP adapter is available on the classpath.
     *
     * @return true if kiota-http-vertx is available
     */
    public static boolean isVertxAvailable() {
        return VERTX_AVAILABLE;
    }

    /**
     * Checks if the JDK HTTP adapter is available on the classpath.
     *
     * @return true if kiota-http-jdk is available
     */
    public static boolean isJdkAdapterAvailable() {
        return JDK_ADAPTER_AVAILABLE;
    }

    /**
     * Resolves the actual adapter type to use based on the requested type
     * and available adapters on the classpath.
     *
     * @param requested the requested adapter type from options
     * @return the resolved adapter type to use
     * @throws IllegalStateException if AUTO is requested but no adapter is available,
     *                               or if a specific adapter is requested but not available
     */
    public static HttpAdapterType resolveAdapterType(HttpAdapterType requested) {
        if (requested == null) {
            requested = HttpAdapterType.AUTO;
        }

        switch (requested) {
            case AUTO:
                if (VERTX_AVAILABLE) {
                    log.fine("Auto-detected Vert.x adapter");
                    return HttpAdapterType.VERTX;
                }
                if (JDK_ADAPTER_AVAILABLE) {
                    log.fine("Auto-detected JDK adapter (Vert.x not available)");
                    return HttpAdapterType.JDK;
                }
                throw new IllegalStateException(
                        "No HTTP adapter available on classpath. " +
                        "Add either kiota-http-vertx or kiota-http-jdk dependency.");

            case VERTX:
                if (!VERTX_AVAILABLE) {
                    throw new IllegalStateException(
                            "Vert.x adapter requested but not available. " +
                            "Add kiota-http-vertx and vertx-auth-oauth2 dependencies.");
                }
                return HttpAdapterType.VERTX;

            case JDK:
                if (!JDK_ADAPTER_AVAILABLE) {
                    throw new IllegalStateException(
                            "JDK adapter requested but not available. " +
                            "Add kiota-http-jdk dependency.");
                }
                return HttpAdapterType.JDK;

            default:
                throw new IllegalArgumentException("Unknown adapter type: " + requested);
        }
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className, false, AdapterDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
