package io.apicurio.registry.federation;

import io.apicurio.registry.rest.v3.beans.FederatedSearchFailureReason;
import io.apicurio.registry.rest.v3.beans.FederatedSearchOutcome;

/**
 * Why a call to a peer registry did not produce results. Each subclass maps to one source outcome
 * of the federated search response, and the subclasses are what the per-peer circuit breaker tells
 * apart: only {@link Unreachable}, {@link Timeout} and {@link PeerError} count as failures of the
 * peer. A refusal made locally, an answer the peer is not allowed or able to give, and a call this
 * registry cancelled or could not make do not.
 */
abstract class PeerSearchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient FederatedSearchOutcome outcome;
    private final transient FederatedSearchFailureReason reason;

    private PeerSearchException(FederatedSearchOutcome outcome, FederatedSearchFailureReason reason,
            String message, Throwable cause) {
        super(message, cause, false, cause != null);
        this.outcome = outcome;
        this.reason = reason;
    }

    FederatedSearchOutcome getOutcome() {
        return outcome;
    }

    /** Set only when the outcome is {@code failed}. */
    FederatedSearchFailureReason getReason() {
        return reason;
    }

    /** The peer could not be reached: no connection, a failed TLS handshake, a dropped connection. */
    static final class Unreachable extends PeerSearchException {
        private static final long serialVersionUID = 1L;

        Unreachable(String message, Throwable cause) {
            super(FederatedSearchOutcome.failed, FederatedSearchFailureReason.unreachable, message, cause);
        }
    }

    /** The peer's address is not one this registry may connect to. A local decision, not a peer failure. */
    static final class AddressRefused extends PeerSearchException {
        private static final long serialVersionUID = 1L;

        AddressRefused(String message) {
            super(FederatedSearchOutcome.failed, FederatedSearchFailureReason.unreachable, message, null);
        }
    }

    /** The peer did not answer within the per-peer timeout. */
    static final class Timeout extends PeerSearchException {
        private static final long serialVersionUID = 1L;

        Timeout(String message) {
            super(FederatedSearchOutcome.failed, FederatedSearchFailureReason.timeout, message, null);
        }
    }

    /** The peer answered with a server error or a redirect. */
    static final class PeerError extends PeerSearchException {
        private static final long serialVersionUID = 1L;

        PeerError(String message) {
            super(FederatedSearchOutcome.failed, FederatedSearchFailureReason.peer_error, message, null);
        }
    }

    /** The peer answered 401 or 403. */
    static final class Unauthorized extends PeerSearchException {
        private static final long serialVersionUID = 1L;

        Unauthorized(String message) {
            super(FederatedSearchOutcome.failed, FederatedSearchFailureReason.unauthorized, message, null);
        }
    }

    /** The peer's answer was malformed, too large or inconsistent. */
    static final class InvalidResponse extends PeerSearchException {
        private static final long serialVersionUID = 1L;

        InvalidResponse(String message, Throwable cause) {
            super(FederatedSearchOutcome.failed, FederatedSearchFailureReason.invalid_response, message, cause);
        }
    }

    /** This registry had no capacity left to make the call. */
    static final class CapacityExceeded extends PeerSearchException {
        private static final long serialVersionUID = 1L;

        CapacityExceeded(String message) {
            super(FederatedSearchOutcome.failed, FederatedSearchFailureReason.capacity_exceeded, message, null);
        }
    }

    /** The peer does not offer the public-only agent search, or does not confirm it. */
    static final class Unsupported extends PeerSearchException {
        private static final long serialVersionUID = 1L;

        Unsupported(String message) {
            super(FederatedSearchOutcome.unsupported, null, message, null);
        }
    }

    /** The search ran out of time, or failed locally, and this call was cancelled. */
    static final class Cancelled extends PeerSearchException {
        private static final long serialVersionUID = 1L;

        Cancelled() {
            super(FederatedSearchOutcome.deadline_exceeded, null, "The call was cancelled.", null);
        }
    }
}
