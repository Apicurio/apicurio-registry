package io.apicurio.registry.maven;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloseVertxTest {

    @Test
    void waitsForTheCloseEvenWhenInterruptedAndKeepsTheFlag() {
        Vertx vertx = Vertx.vertx();
        Thread.currentThread().interrupt();
        try {
            new DownloadRegistryMojo().closeVertx(vertx);
            assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag is restored");
        } finally {
            Thread.interrupted();
        }
        // Closed: a closed Vert.x refuses new work
        assertThrows(RejectedExecutionException.class, () -> vertx.setTimer(1, id -> { }));
    }
}
