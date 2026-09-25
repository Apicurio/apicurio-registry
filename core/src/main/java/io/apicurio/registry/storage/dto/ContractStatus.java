package io.apicurio.registry.storage.dto;

/**
 * Represents the lifecycle status of a data contract.
 *
 * <p>This is distinct from {@code VersionState} (ENABLED, DISABLED, DEPRECATED, DRAFT), which
 * tracks the registry-level visibility of a version. {@code ContractStatus} tracks the
 * <em>contractual</em> maturity of the schema:
 * <ul>
 *   <li>{@code DRAFT} - The contract is being developed and is not ready for consumers.</li>
 *   <li>{@code STABLE} - The contract is production-ready and consumers can rely on it.</li>
 *   <li>{@code DEPRECATED} - The contract is being phased out; consumers should migrate.</li>
 * </ul>
 *
 * <p>Both can coexist on the same version. For example, a version may be {@code VersionState.ENABLED}
 * (visible and usable) while its contract is still {@code ContractStatus.DRAFT} (not yet formally
 * approved for production). Conversely, a {@code STABLE} contract on a {@code DISABLED} version
 * means the contract was approved but the version has been temporarily hidden.
 */
public enum ContractStatus {
    DRAFT,
    STABLE,
    DEPRECATED
}
