package io.apicurio.registry.webhooks;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

public final class OutboundAddressPolicy {

    /** Four dot-separated groups of 1-3 digits; the octet range and leading zeros are checked in code. */
    private static final Pattern DOTTED_QUAD = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private static final Pattern NUMERIC_LABEL = Pattern.compile("^(\\d+|0[xX][0-9a-fA-F]*)$");

    private OutboundAddressPolicy() {
    }

    /**
     * Returns true if a webhook must never be delivered to this address.
     */
    public static boolean isForbidden(InetAddress address) {
        if (address == null) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            Optional<byte[]> embeddedV4 = embeddedIpv4(bytes);
            if (embeddedV4.isPresent()) {
                return isForbidden(toInet4(embeddedV4.get()));
            }
            // fc00::/7 unique-local; Java's isSiteLocalAddress only covers the deprecated
            // fec0::/10
            if ((bytes[0] & 0xFE) == 0xFC) {
                return true;
            }
        }
        return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    /**
     * Checks a URI host without any DNS lookup. Returns true if the host is an IP
     * literal that is
     * forbidden, or a numeric form that is not a canonical IPv4 dotted quad.
     * Returns false for an allowed
     * IP literal or for a host name; a host name must still pass
     * {@link #allResolvedAddressesAllowed(String, HostResolver)}.
     *
     * @param host the host as returned by {@link java.net.URI#getHost()}, IPv6
     *             literals in brackets
     */
    public static boolean isForbiddenLiteral(String host) {
        if (host == null || host.isEmpty()) {
            return true;
        }
        if (host.startsWith("[") || host.indexOf(':') >= 0) {
            String literal = host.startsWith("[") && host.endsWith("]")
                    ? host.substring(1, host.length() - 1)
                    : host;
            if (literal.indexOf(':') < 0) {
                return true;
            }
            try {
                // A string containing ':' is parsed as an IPv6 literal, never looked up in DNS.
                return isForbidden(InetAddress.getByName(literal));
            } catch (UnknownHostException e) {
                return true;
            }
        }
        if (isCanonicalDottedQuad(host)) {
            return isForbidden(toInet4(parseDottedQuad(host)));
        }
        return endsInNumber(host);
    }

    /**
     * Resolves the host and returns true only if it resolves to at least one
     * address and every resolved
     * address is allowed. Any resolution failure is a rejection.
     */
    public static boolean allResolvedAddressesAllowed(String host, HostResolver resolver) {
        if (isForbiddenLiteral(host)) {
            return false;
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException | SecurityException e) {
            return false;
        }
        if (addresses == null || addresses.length == 0) {
            return false;
        }
        for (InetAddress address : addresses) {
            if (isForbidden(address)) {
                return false;
            }
        }
        return true;
    }

    private static boolean endsInNumber(String host) {
        String trimmed = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        int lastDot = trimmed.lastIndexOf('.');
        String lastLabel = lastDot < 0 ? trimmed : trimmed.substring(lastDot + 1);
        return NUMERIC_LABEL.matcher(lastLabel).matches();
    }

    /**
     * Returns the IPv4 address embedded in an IPv4-mapped (::ffff:a.b.c.d) or
     * IPv4-compatible
     * (::a.b.c.d) IPv6 address, or empty if there is none.
     */
    private static Optional<byte[]> embeddedIpv4(byte[] v6) {
        if (v6.length != 16) {
            return Optional.empty();
        }
        for (int i = 0; i < 10; i++) {
            if (v6[i] != 0) {
                return Optional.empty();
            }
        }
        boolean mapped = v6[10] == (byte) 0xFF && v6[11] == (byte) 0xFF;
        boolean compatible = v6[10] == 0 && v6[11] == 0;
        if (!mapped && !compatible) {
            return Optional.empty();
        }
        byte[] v4 = { v6[12], v6[13], v6[14], v6[15] };
        // "::" and "::1" are IPv6 unspecified and loopback, not IPv4-compatible
        // addresses.
        if (compatible && v4[0] == 0 && v4[1] == 0 && v4[2] == 0 && (v4[3] == 0 || v4[3] == 1)) {
            return Optional.empty();
        }
        return Optional.of(v4);
    }

    /**
     * True for a canonical IPv4 dotted quad: four octets in 0-255, none with a leading zero. Anything
     * else that looks numeric (e.g. "0177.0.0.1", "256.1.1.1") falls through to {@link #endsInNumber}.
     */
    private static boolean isCanonicalDottedQuad(String host) {
        if (!DOTTED_QUAD.matcher(host).matches()) {
            return false;
        }
        for (String octet : host.split("\\.")) {
            if (octet.length() > 1 && octet.charAt(0) == '0') {
                return false;
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private static byte[] parseDottedQuad(String host) {
        String[] parts = host.split("\\.");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i]);
        }
        return bytes;
    }

    private static InetAddress toInet4(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            // Only thrown for an illegal length; callers always pass four bytes.
            throw new IllegalStateException(e);
        }
    }
}
