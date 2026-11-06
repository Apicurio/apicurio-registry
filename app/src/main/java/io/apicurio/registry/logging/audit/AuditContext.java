package io.apicurio.registry.logging.audit;

import jakarta.enterprise.context.RequestScoped;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-request audit state, filled by the request side of {@link AuditEventFilter} and read when the
 * single event for the request is built.
 */
@RequestScoped
public class AuditContext {

    private String auditId;
    private Instant requestReceived;
    private String sourceIp;
    private String forwardedFor;
    private String userAgent;
    private boolean emitted;

    void begin(String sourceIp, String forwardedFor, String userAgent) {
        this.requestReceived = Instant.now();
        this.sourceIp = sourceIp;
        this.forwardedFor = forwardedFor;
        this.userAgent = userAgent;
    }

    boolean hasBegun() {
        return requestReceived != null;
    }

    /**
     * Marks the event for this request as emitted and assigns its id. Returns false if it already was
     * emitted or the request side never ran, so that a request never produces two events.
     */
    boolean markEmitted() {
        if (emitted || requestReceived == null) {
            return false;
        }
        emitted = true;
        auditId = UUID.randomUUID().toString();
        return true;
    }

    String getAuditId() {
        return auditId;
    }

    Instant getRequestReceived() {
        return requestReceived;
    }

    String getSourceIp() {
        return sourceIp;
    }

    String getForwardedFor() {
        return forwardedFor;
    }

    String getUserAgent() {
        return userAgent;
    }
}
