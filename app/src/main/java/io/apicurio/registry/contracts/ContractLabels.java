package io.apicurio.registry.contracts;

/**
 * Constants for reserved contract.* label keys.
 * These labels are used to store contract metadata in the artifact/version labels.
 */
public final class ContractLabels {

    // Namespace prefix
    public static final String PREFIX = "contract.";

    // Core contract labels
    public static final String STATUS = "contract.status";
    public static final String OWNER_TEAM = "contract.owner.team";
    public static final String OWNER_DOMAIN = "contract.owner.domain";
    public static final String SUPPORT_CONTACT = "contract.support.contact";
    public static final String CLASSIFICATION = "contract.classification";
    public static final String STAGE = "contract.stage";

    // Lifecycle labels
    public static final String STABLE_DATE = "contract.lifecycle.stable-date";
    public static final String DEPRECATED_DATE = "contract.lifecycle.deprecated-date";
    public static final String DEPRECATION_REASON = "contract.lifecycle.deprecation-reason";

    // SLA labels (stored, not enforced by registry)
    public static final String SLA_AVAILABILITY = "contract.sla.availability";
    public static final String SLA_LATENCY_P50 = "contract.sla.latency.p50";
    public static final String SLA_LATENCY_P99 = "contract.sla.latency.p99";

    // Quality labels (stored, not enforced by registry)
    public static final String QUALITY_FRESHNESS_MAX_STALENESS = "contract.quality.freshness.maxStaleness";

    // ODCS source tracking
    public static final String ODCS_CONTRACT_ID = "contract.odcs.contractId";
    public static final String ODCS_CONTRACT_VERSION = "contract.odcs.contractVersion";

    private ContractLabels() {
        // Prevent instantiation
    }
}
