package io.apicurio.authz;

import java.util.Set;

public record SearchFilterData(
        Set<String> allowedGroups,
        Set<String> allowedExactResources,
        Set<String> deniedExactResources,
        boolean allowAll) {

    public static SearchFilterData all() {
        return new SearchFilterData(Set.of(), Set.of(), Set.of(), true);
    }

    public static SearchFilterData none() {
        return new SearchFilterData(Set.of(), Set.of(), Set.of(), false);
    }

    public boolean hasFilters() {
        return !allowAll && (!allowedGroups.isEmpty() || !allowedExactResources.isEmpty());
    }

    public boolean hasDenyFilters() {
        return !deniedExactResources.isEmpty();
    }
}
