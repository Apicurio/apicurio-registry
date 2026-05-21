package io.apicurio.authz;

import java.util.Optional;
import java.util.Set;

public record Subject(Set<Principal> principals) {

    private static final Subject ANONYMOUS = new Subject(Set.of());

    public static Subject anonymous() {
        return ANONYMOUS;
    }

    public Subject(Principal... principals) {
        this(Set.of(principals));
    }

    public Subject(Set<Principal> principals) {
        this.principals = Set.copyOf(principals);
    }

    public <P extends Principal> Optional<P> principalOfType(Class<P> type) {
        return principals.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    public boolean isAnonymous() {
        return principals.isEmpty();
    }
}
