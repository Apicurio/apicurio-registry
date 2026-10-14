package io.apicurio.registry.webhooks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundAddressPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // IPv4 loopback, private, link-local, unspecified, multicast
            "127.0.0.1", "127.255.255.254", "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1",
            "169.254.169.254", "0.0.0.0", "224.0.0.1", "239.255.255.255",
            // IPv6 loopback, unspecified, link-local, unique-local, multicast
            "[::1]", "[::]", "[fe80::1]", "[fc00::1]", "[fd12:3456:789a::1]", "[ff02::1]",
            // IPv4-mapped and IPv4-compatible IPv6
            "[::ffff:127.0.0.1]", "[::ffff:7f00:1]", "[::ffff:10.0.0.1]", "[::ffff:169.254.169.254]",
            "[::127.0.0.1]", "[::10.0.0.1]",
            // Ambiguous numeric forms
            "2130706433", "127.1", "0x7f.0.0.1", "0177.0.0.1", "127.0.0.1.", "017700000001", "1.2.3.4.5",
            // Malformed
            "", "[]", "[not-an-address]"
    })
    void forbiddenLiterals(String host) {
        assertTrue(OutboundAddressPolicy.isForbiddenLiteral(host), host);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "93.184.216.34", "8.8.8.8", "172.32.0.1", "192.169.0.1", "[2606:2800:220:1:248:1893:25c8:1946]",
            "[::ffff:8.8.8.8]", "ci.example.com", "abc1.de", "cafe.example", "1password.com", "xn--bcher-kva.example"
    })
    void allowedLiteralsAndHostNames(String host) {
        assertFalse(OutboundAddressPolicy.isForbiddenLiteral(host), host);
    }

    @Test
    void nullHostIsForbidden() {
        assertTrue(OutboundAddressPolicy.isForbiddenLiteral(null));
        assertTrue(OutboundAddressPolicy.isForbidden(null));
    }

    @Test
    void hostResolvingOnlyToPublicAddressesIsAllowed() {
        HostResolver resolver = host -> new InetAddress[] { address("93.184.216.34"),
                address("2606:2800:220:1:248:1893:25c8:1946") };
        assertTrue(OutboundAddressPolicy.allResolvedAddressesAllowed("hooks.example.com", resolver));
    }

    @Test
    void hostWithAnyPrivateAddressIsRejected() {
        HostResolver resolver = host -> new InetAddress[] { address("93.184.216.34"), address("10.1.2.3") };
        assertFalse(OutboundAddressPolicy.allResolvedAddressesAllowed("hooks.example.com", resolver));
    }

    @Test
    void hostResolvingToMappedLoopbackIsRejected() {
        HostResolver resolver = host -> new InetAddress[] { address("::ffff:127.0.0.1") };
        assertFalse(OutboundAddressPolicy.allResolvedAddressesAllowed("hooks.example.com", resolver));
    }

    @Test
    void unresolvableHostIsRejected() {
        HostResolver resolver = host -> {
            throw new UnknownHostException("no such host");
        };
        assertFalse(OutboundAddressPolicy.allResolvedAddressesAllowed("missing.example.com", resolver));
    }

    @Test
    void emptyResolutionIsRejected() {
        assertFalse(OutboundAddressPolicy.allResolvedAddressesAllowed("hooks.example.com",
                host -> new InetAddress[0]));
        assertFalse(OutboundAddressPolicy.allResolvedAddressesAllowed("hooks.example.com", host -> null));
    }

    @Test
    void forbiddenLiteralIsRejectedWithoutCallingResolver() {
        AtomicInteger calls = new AtomicInteger();
        HostResolver resolver = host -> {
            calls.incrementAndGet();
            return new InetAddress[] { address("93.184.216.34") };
        };
        assertFalse(OutboundAddressPolicy.allResolvedAddressesAllowed("127.0.0.1", resolver));
        assertEquals(0, calls.get());
    }

    private static InetAddress address(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
