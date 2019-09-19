package io.apicurio.registry.streams.utils;

/**
 * @author Ales Justin
 */
public interface Lifecycle {
    void start();
    boolean isRunning();
    void stop();
}
