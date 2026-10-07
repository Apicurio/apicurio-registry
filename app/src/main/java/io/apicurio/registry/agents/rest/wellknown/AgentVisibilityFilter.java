package io.apicurio.registry.agents.rest.wellknown;

import io.apicurio.registry.agents.a2a.A2AConfig;
import io.apicurio.registry.agents.a2a.A2AConstants;
import io.apicurio.registry.auth.AdminOverride;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.ReadAccessChecker;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the {@code apicurio.agent.visibility} rules to Agent Card search results.
 */
@ApplicationScoped
public class AgentVisibilityFilter {

    private static final Logger log = LoggerFactory.getLogger(AgentVisibilityFilter.class);

    @Inject
    A2AConfig a2aConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    AdminOverride adminOverride;

    @Inject
    AuthConfig authConfig;

    @Inject
    ReadAccessChecker readAccess;

    /**
     * Same notion of "authentication enabled" as the authorization interceptor: any mechanism (OIDC,
     * basic, proxy header, Kubernetes, form). Checking only OIDC and basic auth skipped visibility
     * filtering, private cards included, under the other mechanisms.
     */
    private boolean isAuthEnabled() {
        return authConfig.isAuthenticationEnabled();
    }

    /**
     * Filters artifact DTOs by visibility rules without performing expensive content conversion.
     * When no auth is enabled, all artifacts are returned. Otherwise, visibility is determined
     * by the {@code apicurio.agent.visibility} label (falling back to the configured default):
     * {@code public} for everyone, {@code entitled} for callers with read access (as decided by
     * {@link ReadAccessChecker}, which may include anonymous callers), {@code private} for the owner and
     * administrators.
     */
    List<SearchedArtifactDto> filterDtosByVisibility(List<SearchedArtifactDto> artifacts) {
        if (!isAuthEnabled()) {
            return new ArrayList<>(artifacts);
        }

        boolean isAuthenticated = !securityIdentity.isAnonymous();
        boolean isAdmin = isAuthenticated && adminOverride.isAdmin();
        // "entitled" means entitled to read the card. The discovery endpoint itself is open to
        // anonymous callers (for public cards), so the read check other endpoints get from
        // @Authorized(level = Read) is applied here, through the same shared rules. That includes
        // anonymous callers when anonymous read access is enabled.
        boolean canRead = readAccess.canRead();
        String currentUser = isAuthenticated
                ? securityIdentity.getPrincipal().getName() : null;

        List<SearchedArtifactDto> result = new ArrayList<>();
        for (SearchedArtifactDto artifact : artifacts) {
            String visibility = resolveVisibility(artifact.getLabels());
            if ("public".equals(visibility)) {
                result.add(artifact);
            } else if ("entitled".equals(visibility)) {
                if (canRead) {
                    result.add(artifact);
                }
            } else if ("private".equals(visibility)) {
                // Anonymous callers have no identity to match the owner, so never see private cards.
                String owner = artifact.getOwner();
                if (isAdmin || (owner != null && owner.equals(currentUser))) {
                    result.add(artifact);
                }
            } else {
                log.warn("Unrecognized visibility '{}' for artifact {}/{}, treating as private",
                        visibility, artifact.getGroupId(), artifact.getArtifactId());
                String owner = artifact.getOwner();
                if (isAdmin || (owner != null && owner.equals(currentUser))) {
                    result.add(artifact);
                }
            }
        }
        return result;
    }

    /**
     * Returns the effective visibility for an artifact. If the {@code apicurio.agent.visibility}
     * label is not set, falls back to the configured default visibility.
     * <p>
     * The label key is matched case-insensitively. Labels reach this method from the serialized
     * {@code labels} column, which preserves the case they were supplied with, whereas the
     * {@code artifact_labels} table used by label search filters is lowercased on insert. Matching
     * exactly here would let a card labelled {@code Apicurio.Agent.Visibility=private} resolve to
     * the configured default instead of to {@code private}.
     */
    private String resolveVisibility(Map<String, String> labels) {
        if (labels != null) {
            for (Map.Entry<String, String> label : labels.entrySet()) {
                if (A2AConstants.LABEL_AGENT_VISIBILITY.equalsIgnoreCase(label.getKey())
                        && label.getValue() != null) {
                    return label.getValue().toLowerCase(Locale.ROOT);
                }
            }
        }
        return a2aConfig.getDefaultVisibility().toLowerCase(Locale.ROOT);
    }
}
