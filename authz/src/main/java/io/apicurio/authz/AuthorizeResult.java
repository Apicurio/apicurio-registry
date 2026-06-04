package io.apicurio.authz;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public record AuthorizeResult(Subject subject, List<Action> allowed, List<Action> denied) {

    public AuthorizeResult(Subject subject, List<Action> allowed, List<Action> denied) {
        this.subject = Objects.requireNonNull(subject);
        this.allowed = List.copyOf(Objects.requireNonNull(allowed));
        this.denied = List.copyOf(Objects.requireNonNull(denied));
    }

    public List<String> allowed(ResourceType<?> operation) {
        return allowed.stream()
                .filter(a -> a.operation().equals(operation))
                .map(Action::resourceName)
                .toList();
    }

    public List<String> denied(ResourceType<?> operation) {
        return denied.stream()
                .filter(a -> a.operation().equals(operation))
                .map(Action::resourceName)
                .toList();
    }

    public Decision decision(ResourceType<?> operation, String resourceName) {
        return allowed.stream()
                .anyMatch(a -> a.operation().equals(operation)
                        && a.resourceName().equals(resourceName)) ? Decision.ALLOW : Decision.DENY;
    }

    public <T> Map<Decision, List<T>> partition(Collection<T> items, ResourceType<?> operation,
            Function<T, String> toName) {
        HashMap<Decision, List<T>> byDecision = items.stream().collect(Collectors.groupingBy(
                item -> decision(operation, toName.apply(item)),
                HashMap::new,
                Collectors.toList()));
        byDecision.putIfAbsent(Decision.ALLOW, List.of());
        byDecision.putIfAbsent(Decision.DENY, List.of());
        return byDecision;
    }
}
