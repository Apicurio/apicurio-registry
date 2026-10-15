package io.apicurio.registry.contracts;

import java.util.Map;

/**
 * Constants for reserved contract.* label keys.
 * These labels are used to store contract metadata in the artifact/version labels.
 *
 * Labels are namespaced per contract: contract.{contractId}.{suffix}
 * The SUFFIX constants define the part after the contractId.
 */
public final class ContractLabels {

    // Namespace prefix
    public static final String PREFIX = "contract.";

    // Label suffixes (appended to "contract.{contractId}.")
    public static final String SUFFIX_STATUS = "status";
    public static final String SUFFIX_OWNER_TEAM = "owner.team";
    public static final String SUFFIX_OWNER_DOMAIN = "owner.domain";
    public static final String SUFFIX_SUPPORT_CONTACT = "support.contact";
    public static final String SUFFIX_CLASSIFICATION = "classification";
    public static final String SUFFIX_STAGE = "stage";
    public static final String SUFFIX_STABLE_DATE = "lifecycle.stable-date";
    public static final String SUFFIX_DEPRECATED_DATE = "lifecycle.deprecated-date";
    public static final String SUFFIX_DEPRECATION_REASON = "lifecycle.deprecation-reason";

    // SLA suffixes (stored, not enforced by registry)
    public static final String SUFFIX_SLA_AVAILABILITY = "sla.availability";
    public static final String SUFFIX_SLA_LATENCY_P50 = "sla.latency.p50";
    public static final String SUFFIX_SLA_LATENCY_P99 = "sla.latency.p99";

    // Quality suffixes (stored, not enforced by registry)
    public static final String SUFFIX_QUALITY_FRESHNESS_MAX_STALENESS = "quality.freshness.maxStaleness";

    // Compatibility group suffix
    public static final String SUFFIX_COMPATIBILITY_GROUP = "compatibility.group";

    // ODCS source tracking suffixes
    public static final String SUFFIX_ID = "id";
    public static final String SUFFIX_VERSION = "version";

    /**
     * Builds the full prefix for a specific contract: "contract.{contractId}."
     */
    public static String contractPrefix(String contractId) {
        return PREFIX + contractId + ".";
    }

    /**
     * Builds the full label key for a contract: "contract.{contractId}.{suffix}"
     */
    public static String key(String contractId, String suffix) {
        return PREFIX + contractId + "." + suffix;
    }

    /**
     * Finds the contract ID stored in the given labels, or null if the labels do not
     * identify a contract. The contract ID is the value of the "contract.{contractId}.id"
     * label.
     */
    public static String findContractId(Map<String, String> labels) {
        if (labels == null) {
            return null;
        }
        String suffix = "." + SUFFIX_ID;
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(PREFIX) && key.endsWith(suffix)) {
                int start = PREFIX.length();
                int end = key.length() - suffix.length();
                if (end <= start) {
                    continue;
                }
                String middle = key.substring(start, end);
                if (!middle.contains(".")) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Returns the label prefix to use for the given contract ID, falling back to the
     * un-namespaced prefix when the contract ID is null.
     */
    public static String prefixFor(String contractId) {
        return contractId != null ? contractPrefix(contractId) : PREFIX;
    }

    private ContractLabels() {
    }
}
