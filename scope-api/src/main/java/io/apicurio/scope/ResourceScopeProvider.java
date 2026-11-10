package io.apicurio.scope;

import java.util.concurrent.CompletionStage;

import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Subject;

/**
 * An optional companion to a Kroxylicious {@code Authorizer}: the set of resources a subject may
 * access with an operation, in a form that can be pushed into a query or evaluated in memory.
 * Consumers check {@code authorizer instanceof ResourceScopeProvider}.
 *
 * <p>Implementations must agree with their {@code Authorizer}: {@code scope.matches(name)} is
 * true if and only if {@code authorize()} allows {@code new Action(operation, name)} for the same
 * subject, including operations implied by other operations. The conformance tests in
 * {@code apicurio-scope-api-tck} check this.</p>
 */
public interface ResourceScopeProvider {

    /**
     * Resolves the scope of {@code operation} for {@code subject}. Fails closed: a resource type
     * the provider does not support resolves to {@link ResourceScope.Forbidden}.
     *
     * @return the scope; the stage fails if the scope cannot be resolved
     */
    CompletionStage<ResourceScope> resolveScope(Subject subject, ResourceType<?> operation);
}
