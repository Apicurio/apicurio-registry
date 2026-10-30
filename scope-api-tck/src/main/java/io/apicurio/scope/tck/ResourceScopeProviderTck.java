package io.apicurio.scope.tck;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.apicurio.scope.ResourceScope;
import io.apicurio.scope.ResourceScopeProvider;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.Authorizer;
import io.kroxylicious.authorizer.service.Decision;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Subject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance tests for a {@link ResourceScopeProvider}. Extend this class in your tests and
 * supply an authorizer with rules, and the subjects, operations and resource names to check.
 *
 * <p>For every subject (plus an anonymous one), operation and name (plus near misses derived from
 * each name), the scope must select exactly the names the authorizer allows. Choose fixtures that
 * exercise your rules: overlapping and nested prefixes, denies inside allows, implied operations,
 * principals of several types.</p>
 *
 * @param <P> the provider, which is also the authorizer it must agree with
 */
// Assertions are the purpose of this class: it is published so implementations can run it as tests
@SuppressWarnings("java:S5960")
public abstract class ResourceScopeProviderTck<P extends Authorizer & ResourceScopeProvider> {

    /** A resource type no provider supports. */
    public enum UnsupportedResource implements ResourceType<UnsupportedResource> {
        READ
    }

    /** @return the provider under test, with its rules loaded */
    protected abstract P provider() throws Exception;

    /** @return the subjects to check; an anonymous subject is added */
    protected abstract List<Subject> subjects();

    /** @return the operations to check */
    protected abstract List<ResourceType<?>> operations();

    /** @return the resource names to check; near misses of each are added */
    protected abstract List<String> resourceNames();

    /**
     * @return {@code names} plus near misses of each: one character longer and shorter, other
     *         case, and a few edge cases
     */
    static List<String> withNearMisses(List<String> names) {
        Set<String> all = new LinkedHashSet<>(names);
        for (String name : names) {
            all.add(name + "x");
            all.add(name + "/");
            if (!name.isEmpty()) {
                all.add(name.substring(0, name.length() - 1));
            }
            all.add(name.toUpperCase(Locale.ROOT));
        }
        all.addAll(List.of("", "/", "%", "*", ".*"));
        return List.copyOf(all);
    }

    private static boolean allowed(Authorizer authorizer, Subject subject, ResourceType<?> op, String name) {
        return authorizer.authorize(subject, List.of(new Action(op, name))).toCompletableFuture().join()
                .decision(op, name) == Decision.ALLOW;
    }

    @Test
    void scopeSelectsExactlyWhatTheAuthorizerAllows() throws Exception {
        P provider = provider();
        List<Subject> subjects = new ArrayList<>(subjects());
        subjects.add(Subject.anonymous());
        List<String> names = withNearMisses(resourceNames());
        List<String> mismatches = new ArrayList<>();
        int allowed = 0;
        int checked = 0;
        for (Subject subject : subjects) {
            for (ResourceType<?> op : operations()) {
                allowed += compare(provider, subject, op, names, mismatches);
                checked += names.size();
            }
        }
        assertEquals(List.of(), mismatches);
        // A fixture where everything is allowed, or nothing is, proves nothing
        assertTrue(allowed > 0 && allowed < checked,
                "the fixture must allow some names and deny others; allowed " + allowed + " of " + checked);
    }

    /** @return how many of {@code names} the authorizer allows; mismatches are added to the list */
    private static int compare(Authorizer provider, Subject subject, ResourceType<?> op, List<String> names,
            List<String> mismatches) {
        ResourceScope scope = ((ResourceScopeProvider) provider).resolveScope(subject, op).toCompletableFuture()
                .join();
        int allowed = 0;
        for (String name : names) {
            boolean pointAccess = allowed(provider, subject, op, name);
            allowed += pointAccess ? 1 : 0;
            if (pointAccess != scope.matches(name)) {
                mismatches.add(subject.principals() + " " + op + " '" + name + "': authorize "
                        + (pointAccess ? "allows" : "denies") + ", scope " + scope);
            }
        }
        return allowed;
    }

    @Test
    void unsupportedResourceTypesAreForbidden() throws Exception {
        P provider = provider();
        List<Subject> subjects = new ArrayList<>(subjects());
        subjects.add(Subject.anonymous());
        for (Subject subject : subjects) {
            assertInstanceOf(ResourceScope.Forbidden.class,
                    provider.resolveScope(subject, UnsupportedResource.READ).toCompletableFuture().join());
            for (String name : resourceNames()) {
                assertEquals(false, allowed(provider, subject, UnsupportedResource.READ, name), name);
            }
        }
    }
}
