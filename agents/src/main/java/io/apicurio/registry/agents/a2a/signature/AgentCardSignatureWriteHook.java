package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.extensions.ArtifactVersionWriteHook;
import io.apicurio.registry.extensions.VersionWriteContext;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Applies {@code apicurio.a2a.signature-verification.mode} to every {@code AGENT_CARD} version published
 * through the v3 REST API: a new artifact with its first version, a new version, and a draft when it is
 * published (drafts themselves are not checked until then). The policy is server-wide and independent of
 * rules, so disabling or never enabling a rule does not bypass it. Dry runs are rejected exactly like real
 * writes.
 * <p>
 * A rejection is a {@link RuleViolationException} with one cause per signature, naming its claimed
 * {@code kid} and the reason; never key material or signature bytes.
 * </p>
 */
@ApplicationScoped
public class AgentCardSignatureWriteHook implements ArtifactVersionWriteHook {

    static final String REJECTED = "The Agent Card signatures do not satisfy the registry's signature policy.";
    private static final int MAX_KID_LENGTH = 128;

    @Inject
    AgentCardSignatureConfig config;

    @Inject
    AgentCardSignatureService signatureService;

    @Override
    public Runnable beforePublish(VersionWriteContext context, TypedContent content) {
        AgentCardSignatureConfig.Mode mode = config.getMode();
        if (mode == AgentCardSignatureConfig.Mode.OFF || !ArtifactType.AGENT_CARD.equals(context.getArtifactType())) {
            return null;
        }
        AgentCardSignatureVerifier.Result result = signatureService.verify(content.getContent().content());
        if (!result.signed()) {
            if (mode == AgentCardSignatureConfig.Mode.REQUIRE) {
                throw rejection(mode, Set.of(new RuleViolation(
                        "The Agent Card is not signed: no verifiable signature", "/signatures")));
            }
            return null;
        }
        if (result.anyVerified()) {
            return null;
        }
        Set<RuleViolation> causes = new LinkedHashSet<>();
        if (!result.canonicalizable()) {
            causes.add(new RuleViolation("The Agent Card cannot be canonicalized (RFC 8785): no verifiable signature",
                    "/signatures"));
        }
        for (AgentCardSignatureVerifier.SignatureOutcome outcome : result.outcomes()) {
            causes.add(new RuleViolation("Signature " + describeKid(outcome.kid()) + " rejected: "
                    + outcome.failure().getDescription(), "/signatures/" + outcome.index()));
        }
        throw rejection(mode, causes);
    }

    private static RuleViolationException rejection(AgentCardSignatureConfig.Mode mode, Set<RuleViolation> causes) {
        return new RuleViolationException(REJECTED, RuleType.VALIDITY, mode.getValue(), causes);
    }

    private static String describeKid(String kid) {
        if (kid == null) {
            return "without kid";
        }
        String shown = kid.length() > MAX_KID_LENGTH ? kid.substring(0, MAX_KID_LENGTH) + "..." : kid;
        return "by kid '" + shown + "'";
    }
}
