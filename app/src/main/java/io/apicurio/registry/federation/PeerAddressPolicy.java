package io.apicurio.registry.federation;

import io.apicurio.registry.http.OutboundAddressPolicy;
import io.apicurio.registry.http.OutboundAddressPolicy.AddressVerdict;
import io.apicurio.registry.storage.error.InvalidPeerException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * Outbound network policy for peer registry URLs.
 * <p>
 * HTTPS is required unless plain HTTP is explicitly enabled. Private and cluster addresses are
 * allowed, since a registry reaching a sibling over service DNS is the normal internal deployment.
 * Link-local, unspecified, multicast, reserved and known cloud metadata addresses are always
 * refused, and loopback is refused unless explicitly enabled.
 * <p>
 * {@link #validateUrl(String)} runs when a peer is written. It inspects the URL only and does no
 * DNS lookup: a hostname is accepted here, and the address it resolves to must be checked with
 * {@link OutboundAddressPolicy#classify(InetAddress, boolean)} at the moment the client connects
 * to it, because an address checked at write time says nothing about the address a later lookup
 * returns.
 * <p>
 * This policy depends on configuration, so it is applied where a peer is written through the API
 * and where a GitOps or KubernetesOps peer is loaded, and never inside
 * {@link io.apicurio.registry.storage.PeerValidator}, which also runs when the KafkaSQL journal
 * is replayed.
 */
@ApplicationScoped
public class PeerAddressPolicy {

    private static final String INSECURE_HTTP_PROPERTY = "apicurio.federation.peer.insecure-http.enabled";
    private static final String LOOPBACK_PROPERTY = "apicurio.federation.peer.loopback.enabled";

    @Inject
    FederationConfig federationConfig;

    /**
     * Validates a peer URL against the configured policy.
     *
     * @throws InvalidPeerException if the URL is not allowed
     */
    public void validateUrl(String url) {
        validateUrl(url, federationConfig.isInsecureHttpEnabled(), federationConfig.isLoopbackEnabled());
    }

    /**
     * Validates a peer URL against the policy with the given opt-ins.
     *
     * @throws InvalidPeerException if the URL is not allowed
     */
    public static void validateUrl(String url, boolean insecureHttpEnabled, boolean loopbackEnabled) {
        if (url == null) {
            throw new InvalidPeerException("Peer url is required.");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new InvalidPeerException("Peer url is not a valid URI.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("http".equals(scheme)) {
            if (!insecureHttpEnabled) {
                throw new InvalidPeerException(
                        "Peer url must use https. Plain http requires " + INSECURE_HTTP_PROPERTY + "=true.");
            }
        } else if (!"https".equals(scheme)) {
            throw new InvalidPeerException("Peer url must use https.");
        }
        if (uri.getHost() == null) {
            throw new InvalidPeerException("Peer url must be an absolute URI with a host.");
        }
        AddressVerdict verdict;
        try {
            verdict = OutboundAddressPolicy.classifyHost(uri.getHost(), loopbackEnabled);
        } catch (OutboundAddressPolicy.InvalidHostException ex) {
            throw new InvalidPeerException("Peer url " + ex.getMessage());
        }
        if (verdict == AddressVerdict.LOOPBACK) {
            throw new InvalidPeerException("Peer url host must not be " + verdict.getDescription()
                    + " unless " + LOOPBACK_PROPERTY + "=true.");
        }
        if (verdict != AddressVerdict.ALLOWED) {
            throw new InvalidPeerException("Peer url host must not be " + verdict.getDescription() + ".");
        }
    }

    /**
     * Classifies an address with {@link OutboundAddressPolicy#classify(InetAddress, boolean)}.
     */
    public static AddressVerdict classify(InetAddress address, boolean loopbackEnabled) {
        return OutboundAddressPolicy.classify(address, loopbackEnabled);
    }
}
