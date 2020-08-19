package io.apicurio.registry.utils.streams.ext;

/**
 * @author Ales Justin
 */
public interface Lifecycle {
    void start();
    boolean isRunning();
    void stop();
}
