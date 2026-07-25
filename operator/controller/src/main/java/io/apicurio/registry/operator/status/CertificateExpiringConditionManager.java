package io.apicurio.registry.operator.status;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import java.util.Map;
import java.util.TreeMap;

import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_CERTIFICATE_EXPIRING;

/**
 * Manages the condition that reports expiring TLS certificates.
 */
public class CertificateExpiringConditionManager extends AbstractConditionManager {

    private static final String REASON_EXPIRING = "CertificateExpiring";
    private static final String REASON_NOT_EXPIRING = "NoExpiringCertificates";

    private final Map<String, String> expiringCerts = new TreeMap<>(); // Deterministic ordering

    CertificateExpiringConditionManager() {
        resetCondition();
    }

    @Override
    void resetCondition() {
        current = new Condition();
        current.setType(TYPE_CERTIFICATE_EXPIRING);
        expiringCerts.clear();
    }

    /**
     * Record an expiring certificate warning.
     */
    public synchronized void recordExpiringCertificate(String secretName, String message) {
        expiringCerts.put(secretName, message);
    }

    @Override
    void updateCondition(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        if (!expiringCerts.isEmpty()) {
            current.setStatus(ConditionStatus.TRUE);
            current.setReason(REASON_EXPIRING);
            StringBuilder message = new StringBuilder("Found " + expiringCerts.size() + " expiring certificate(s):");
            for (Map.Entry<String, String> entry : expiringCerts.entrySet()) {
                message.append("\n- ").append(entry.getKey()).append(": ").append(entry.getValue());
            }
            current.setMessage(message.toString());
        } else {
            current.setStatus(ConditionStatus.FALSE);
            current.setReason(REASON_NOT_EXPIRING);
            current.setMessage("No expiring certificates found.");
        }
    }
}
