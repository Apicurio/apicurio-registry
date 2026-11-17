package io.apicurio.registry.contracts;

import io.apicurio.registry.extensions.FeatureLabelOwner;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Data contracts store their status, ownership, lifecycle and compatibility group in labels.
 */
@ApplicationScoped
public class ContractLabelOwner implements FeatureLabelOwner {

    @Override
    public Set<String> ownedLabelPrefixes() {
        return Set.of(ContractLabels.PREFIX);
    }
}
