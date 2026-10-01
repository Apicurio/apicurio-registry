package io.apicurio.registry.federation;

import io.apicurio.registry.storage.error.InvalidPeerException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * {@link #classify(InetAddress, boolean)} at the moment the client connects to it, because an
 * address checked at write time says nothing about the address a later lookup returns.
 * <p>
 * This policy depends on configuration, so it is applied where a peer is written through the API
 * and never inside {@link io.apicurio.registry.storage.PeerValidator}, which also runs when the
 * KafkaSQL journal is replayed.
 */
@ApplicationScoped
public class PeerAddressPolicy {

    /**
     * The outcome of classifying an address. Only {@link #ALLOWED} may be connected to.
     */
    public enum AddressVerdict {
        ALLOWED("an allowed address"),
        LOOPBACK("a loopback address"),
        LINK_LOCAL("a link-local address"),
        UNSPECIFIED("an unspecified address"),
        MULTICAST("a multicast address"),
        RESERVED("a reserved or broadcast address"),
        METADATA("a cloud metadata address");

        private final String description;

        AddressVerdict(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private static final String INSECURE_HTTP_PROPERTY = "apicurio.federation.peer.insecure-http.enabled";
    private static final String LOOPBACK_PROPERTY = "apicurio.federation.peer.loopback.enabled";

    // Cloud metadata endpoints outside the link-local ranges (169.254.169.254 is link-local).
    private static final Set<InetAddress> METADATA_ADDRESSES = Set.of(
            address(new byte[] { 100, 100, 100, (byte) 200 }),
            address(new byte[] { (byte) 0xfd, 0x00, 0x0e, (byte) 0xc2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02, 0x54 }));

    private static final byte[] NAT64_PREFIX = { 0x00, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0 };

    // A host whose last label is a number is parsed as an IPv4 address by URL parsers and resolvers.
    private static final Pattern NUMERIC_LABEL = Pattern.compile("^(0x[0-9a-f]*|[0-9]+)$");
    private static final String OCTET = "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final Pattern DOTTED_QUAD = Pattern.compile(
            "^" + OCTET + "\\." + OCTET + "\\." + OCTET + "\\." + OCTET + "$");

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
        AddressVerdict verdict = classifyHost(uri.getHost(), loopbackEnabled);
        if (verdict == AddressVerdict.LOOPBACK) {
            throw new InvalidPeerException("Peer url host must not be " + verdict.getDescription()
                    + " unless " + LOOPBACK_PROPERTY + "=true.");
        }
        if (verdict != AddressVerdict.ALLOWED) {
            throw new InvalidPeerException("Peer url host must not be " + verdict.getDescription() + ".");
        }
    }

    /**
     * Classifies the host component of a URL without any DNS lookup. IP literals and localhost
     * names are classified; any other hostname is {@link AddressVerdict#ALLOWED} here and must be
     * classified again by its resolved address when connecting.
     *
     * @throws InvalidPeerException if the host is an ambiguous numeric form or a scoped IPv6 literal
     */
    static AddressVerdict classifyHost(String host, boolean loopbackEnabled) {
        if (host.startsWith("[") && host.endsWith("]")) {
            String literal = host.substring(1, host.length() - 1);
            if (literal.indexOf('%') >= 0) {
                throw new InvalidPeerException("Peer url must not use a scoped IPv6 address.");
            }
            if (literal.indexOf(':') < 0) {
                throw new InvalidPeerException("Peer url host is not a valid IPv6 address.");
            }
            try {
                // An IPv6 literal is parsed locally; no lookup takes place.
                return classify(InetAddress.getByName(literal), loopbackEnabled);
            } catch (UnknownHostException ex) {
                throw new InvalidPeerException("Peer url host is not a valid IPv6 address.");
            }
        }

        String name = host.toLowerCase(Locale.ROOT);
        if (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        if ("localhost".equals(name) || name.endsWith(".localhost")) {
            return loopbackEnabled ? AddressVerdict.ALLOWED : AddressVerdict.LOOPBACK;
        }

        String lastLabel = name.substring(name.lastIndexOf('.') + 1);
        if (NUMERIC_LABEL.matcher(lastLabel).matches()) {
            Matcher quad = DOTTED_QUAD.matcher(name);
            if (!quad.matches()) {
                throw new InvalidPeerException("Peer url host is not a valid IPv4 address. "
                        + "Use four dotted decimal octets without leading zeros.");
            }
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                bytes[i] = (byte) Integer.parseInt(quad.group(i + 1));
            }
            return classify(address(bytes), loopbackEnabled);
        }

        return AddressVerdict.ALLOWED;
    }

    /**
     * Classifies an address. IPv6 addresses that embed an IPv4 address (IPv4-compatible,
     * IPv4-mapped and NAT64 well-known prefix) are classified by the embedded IPv4 address, so that
     * they cannot be used to reach an address the IPv4 form would be refused for.
     */
    public static AddressVerdict classify(InetAddress address, boolean loopbackEnabled) {
        if (address.isAnyLocalAddress()) {
            return AddressVerdict.UNSPECIFIED;
        }
        if (address.isLoopbackAddress()) {
            return loopbackEnabled ? AddressVerdict.ALLOWED : AddressVerdict.LOOPBACK;
        }
        InetAddress effective = unwrapEmbeddedIpv4(address);
        if (METADATA_ADDRESSES.contains(effective)) {
            return AddressVerdict.METADATA;
        }
        if (effective.isAnyLocalAddress()) {
            return AddressVerdict.UNSPECIFIED;
        }
        if (effective.isLoopbackAddress()) {
            return loopbackEnabled ? AddressVerdict.ALLOWED : AddressVerdict.LOOPBACK;
        }
        if (effective.isLinkLocalAddress()) {
            return AddressVerdict.LINK_LOCAL;
        }
        if (effective.isMulticastAddress()) {
            return AddressVerdict.MULTICAST;
        }
        if (effective instanceof Inet4Address) {
            int firstOctet = effective.getAddress()[0] & 0xff;
            if (firstOctet == 0) {
                return AddressVerdict.UNSPECIFIED;
            }
            if (firstOctet >= 240) {
                return AddressVerdict.RESERVED;
            }
        }
        return AddressVerdict.ALLOWED;
    }

    private static InetAddress unwrapEmbeddedIpv4(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return address;
        }
        byte[] bytes = address.getAddress();
        boolean firstTenZero = true;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                firstTenZero = false;
                break;
            }
        }
        boolean compatible = firstTenZero && bytes[10] == 0 && bytes[11] == 0;
        boolean mapped = firstTenZero && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
        boolean nat64 = Arrays.equals(bytes, 0, 12, NAT64_PREFIX, 0, 12);
        if (compatible || mapped || nat64) {
            return address(Arrays.copyOfRange(bytes, 12, 16));
        }
        return address;
    }

    private static InetAddress address(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Invalid address length: " + bytes.length, ex);
        }
    }
}
