package io.apicurio.registry.extensions;

import io.apicurio.registry.content.TypedContent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Applies every {@link ArtifactContentPolicy} bean to a write, for the APIs that do not run
 * {@link ArtifactVersionWriteHook}.
 */
@ApplicationScoped
public class ArtifactContentPolicies {

    @Inject
    Instance<ArtifactContentPolicy> policies;

    /**
     * @throws RuntimeException from the first policy that rejects the content
     */
    public void check(String artifactType, TypedContent content) {
        for (ArtifactContentPolicy policy : policies) {
            policy.check(artifactType, content);
        }
    }
}
