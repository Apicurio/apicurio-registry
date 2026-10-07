package io.apicurio.registry.federation;

import java.util.concurrent.CompletableFuture;

/**
 * A call to a peer registry that is in flight. Cancelling it completes the future with
 * {@link PeerSearchException.Cancelled} and releases the connection.
 */
final class PeerCall {

    private final CompletableFuture<PeerSearchResponse> future;

    PeerCall(CompletableFuture<PeerSearchResponse> future) {
        this.future = future;
    }

    CompletableFuture<PeerSearchResponse> future() {
        return future;
    }

    void cancel() {
        future.completeExceptionally(new PeerSearchException.Cancelled());
    }
}
