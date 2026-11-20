package io.apicurio.registry.http;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies the addresses the registry may open outbound connections to, for features that
 * connect to URLs supplied by users, such as federation peers and MCP server imports.
 * <p>
 * Link-local, unspecified, multicast, reserved and known cloud metadata addresses are never
 * allowed. Loopback is allowed only when the caller opts in. Each feature adds its own rules on
 * top, such as required schemes or host allow-lists, and reports refusals in its own terms.
 * <p>
 * {@link #classifyHost(String, boolean)} inspects a URL host without any DNS lookup, so a hostname
 * passes it; the addresses the hostname resolves to must be classified with
 * {@link #classify(InetAddress, boolean)} before connecting.
 */
public final class OutboundAddressPolicy {

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

    /**
     * A URL host that cannot be classified, such as an ambiguous numeric form. The message is a
     * reason phrase about the host, for the caller to prefix with what the URL is for.
     */
    public static class InvalidHostException extends RuntimeException {

        private static final long serialVersionUID = 4180925402185226651L;

        public InvalidHostException(String reason) {
            super(reason);
        }
    }

    // Cloud metadata endpoints outside the link-local ranges (169.254.169.254 is link-local).
    private static final Set<InetAddress> METADATA_ADDRESSES = Set.of(
            address(new byte[] { 100, 100, 100, (byte) 200 }),
            address(new byte[] { (byte) 0xfd, 0x00, 0x0e, (byte) 0xc2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02, 0x54 }));

    private static final byte[] NAT64_PREFIX = { 0x00, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0 };

    // A host whose last label is a number is parsed as an IPv4 address by URL parsers and resolvers.
    private static final Pattern NUMERIC_LABEL = Pattern.compile("^(0x[0-9a-f]*|\\d+)$");
    private static final String OCTET = "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)";
    private static final Pattern DOTTED_QUAD = Pattern.compile(
            "^" + OCTET + "\\." + OCTET + "\\." + OCTET + "\\." + OCTET + "$");

    private OutboundAddressPolicy() {
    }

    /**
     * Classifies the host component of a URL without any DNS lookup. IP literals and localhost
     * names are classified; any other hostname is {@link AddressVerdict#ALLOWED} here and must be
     * classified again by its resolved addresses when connecting.
     *
     * @throws InvalidHostException if the host is an ambiguous numeric form or a scoped IPv6 literal
     */
    public static AddressVerdict classifyHost(String host, boolean loopbackEnabled) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return classifyIpv6Literal(host.substring(1, host.length() - 1), loopbackEnabled);
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
            return classify(dottedQuad(name), loopbackEnabled);
        }

        return AddressVerdict.ALLOWED;
    }

    private static AddressVerdict classifyIpv6Literal(String literal, boolean loopbackEnabled) {
        if (literal.indexOf('%') >= 0) {
            throw new InvalidHostException("must not use a scoped IPv6 address.");
        }
        if (literal.indexOf(':') < 0) {
            throw new InvalidHostException("host is not a valid IPv6 address.");
        }
        try {
            // An IPv6 literal is parsed locally; no lookup takes place.
            return classify(InetAddress.getByName(literal), loopbackEnabled);
        } catch (UnknownHostException ex) {
            throw new InvalidHostException("host is not a valid IPv6 address.");
        }
    }

    /** The address of a host whose last label is numeric, which must be four dotted octets. */
    private static InetAddress dottedQuad(String name) {
        Matcher quad = DOTTED_QUAD.matcher(name);
        if (!quad.matches()) {
            throw new InvalidHostException("host is not a valid IPv4 address. "
                    + "Use four dotted decimal octets without leading zeros.");
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) Integer.parseInt(quad.group(i + 1));
        }
        return address(bytes);
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
        return effective instanceof Inet4Address ? classifyIpv4Range(effective) : AddressVerdict.ALLOWED;
    }

    /** 0.0.0.0/8 is "this network" and 240.0.0.0/4, including broadcast, is reserved. */
    private static AddressVerdict classifyIpv4Range(InetAddress address) {
        int firstOctet = address.getAddress()[0] & 0xff;
        if (firstOctet == 0) {
            return AddressVerdict.UNSPECIFIED;
        }
        return firstOctet >= 240 ? AddressVerdict.RESERVED : AddressVerdict.ALLOWED;
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
