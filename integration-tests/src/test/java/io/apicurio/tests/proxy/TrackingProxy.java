package io.apicurio.tests.proxy;

import io.apicurio.tests.utils.LimitingProxy;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple HTTP proxy for testing that tracks all requests passing through it.
 * Used in integration tests to verify proxy configuration is working correctly.
 * Unlike other limiting proxies, this one allows all requests through.
 */
public class TrackingProxy extends LimitingProxy {

    private final AtomicInteger requestCount = new AtomicInteger(0);

    public TrackingProxy(String destinationHost, int destinationPort) {
        super(destinationHost, destinationPort);
    }

    @Override
    protected boolean allowed() {
        requestCount.incrementAndGet();
        logger.info("Tracking proxy: Request #{} allowed", requestCount.get());
        return true; // Always allow requests through
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    public void resetRequestCount() {
        requestCount.set(0);
    }
}
