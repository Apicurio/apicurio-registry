package io.apicurio.registry.federation;

import java.util.concurrent.CompletableFuture;

/**
 * A call to a peer registry that is in flight. Cancelling it fails the call with
 * {@link PeerSearchException.Cancelled} and releases the connection. The future completes after the
 * call has given back what it held, so whoever sees the result can start the next call at once.
 */
final class PeerCall {

    private final CompletableFuture<PeerSearchResponse> call;
    private final CompletableFuture<PeerSearchResponse> future;

    /**
     * @param call completed by the call itself, or by {@link #cancel()}
     * @param future completes with the same result as {@code call}, once the call has let go
     */
    PeerCall(CompletableFuture<PeerSearchResponse> call, CompletableFuture<PeerSearchResponse> future) {
        this.call = call;
        this.future = future;
    }

    CompletableFuture<PeerSearchResponse> future() {
        return future;
    }

    void cancel() {
        call.completeExceptionally(new PeerSearchException.Cancelled());
    }
}
