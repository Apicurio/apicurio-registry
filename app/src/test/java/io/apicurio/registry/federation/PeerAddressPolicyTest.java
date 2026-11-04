package io.apicurio.registry.federation;

import io.apicurio.registry.federation.PeerAddressPolicy.AddressVerdict;
import io.apicurio.registry.storage.error.InvalidPeerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.Inet4Address;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PeerAddressPolicyTest {

    private static InvalidPeerException rejected(String url) {
        return assertThrows(InvalidPeerException.class, () -> PeerAddressPolicy.validateUrl(url, false, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://registry.eu.example.com",
            "https://registry.eu.example.com:8443/registry",
            "HTTPS://Registry.EU.example.com/",
            "https://registry.team-a.svc.cluster.local:8080",
            "https://registry.team-a.svc",
            "https://10.1.2.3",
            "https://172.16.0.10:8443",
            "https://192.168.1.20",
            "https://100.64.0.1",
            "https://8.8.8.8",
            "https://[fd12:3456:789a::1]",
            "https://[2001:db8::10]:8443",
            "https://face.bad",
            "https://1host.example.com"
    })
    void acceptsHttpsToPublicPrivateAndClusterHosts(String url) {
        assertDoesNotThrow(() -> PeerAddressPolicy.validateUrl(url, false, false));
    }

    @Test
    void rejectsPlainHttpUnlessEnabled() {
        String url = "http://registry.team-a.svc:8080";
        assertEquals("Peer url must use https. Plain http requires "
                + "apicurio.federation.peer.insecure-http.enabled=true.", rejected(url).getMessage());
        assertDoesNotThrow(() -> PeerAddressPolicy.validateUrl(url, true, false));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp://registry.example.com", "ws://registry.example.com", "file://registry/x",
            "gopher://registry.example.com" })
    void rejectsOtherSchemesEvenWithHttpEnabled(String url) {
        InvalidPeerException ex = assertThrows(InvalidPeerException.class,
                () -> PeerAddressPolicy.validateUrl(url, true, true));
        assertEquals("Peer url must use https.", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://127.0.0.1:8080", "https://127.10.20.30", "https://[::1]:8080",
            "https://localhost:8080", "https://LOCALHOST.", "https://registry.localhost",
            "https://[::7f00:1]", "https://[::ffff:127.0.0.1]" })
    void rejectsLoopbackUnlessEnabled(String url) {
        assertEquals("Peer url host must not be a loopback address unless "
                + "apicurio.federation.peer.loopback.enabled=true.", rejected(url).getMessage());
        assertDoesNotThrow(() -> PeerAddressPolicy.validateUrl(url, false, true));
    }

    @ParameterizedTest
    @CsvSource({
            "https://169.254.169.254, a link-local address",
            "https://169.254.10.1:8080, a link-local address",
            "https://[fe80::1], a link-local address",
            "https://[::ffff:169.254.169.254], a link-local address",
            "https://[::a9fe:a9fe], a link-local address",
            "https://[64:ff9b::a9fe:a9fe], a link-local address",
            "https://100.100.100.200, a cloud metadata address",
            "https://[fd00:ec2::254], a cloud metadata address",
            "https://0.0.0.0, an unspecified address",
            "https://0.1.2.3, an unspecified address",
            "https://[::], an unspecified address",
            "https://224.0.0.1, a multicast address",
            "https://[ff02::1], a multicast address",
            "https://240.0.0.1, a reserved or broadcast address",
            "https://255.255.255.255, a reserved or broadcast address"
    })
    void alwaysRejectsUnsafeLiterals(String url, String description) {
        String expected = "Peer url host must not be " + description + ".";
        assertEquals(expected, rejected(url).getMessage());
        InvalidPeerException withOptIns = assertThrows(InvalidPeerException.class,
                () -> PeerAddressPolicy.validateUrl(url, true, true));
        assertEquals(expected, withOptIns.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://2852039166", "https://0xA9FEA9FE", "https://0x7f000001:8080",
            "https://010.0.0.1", "https://169.254.169.254.0x1", "https://a.b.0x10" })
    void rejectsAmbiguousNumericHosts(String url) {
        assertThrows(InvalidPeerException.class, () -> PeerAddressPolicy.validateUrl(url, true, true));
    }

    @Test
    void ambiguousNumericHostHasSpecificMessage() {
        assertEquals("Peer url host is not a valid IPv4 address. "
                + "Use four dotted decimal octets without leading zeros.",
                rejected("https://2852039166").getMessage());
    }

    @Test
    void rejectsScopedIpv6Literal() {
        assertEquals("Peer url must not use a scoped IPv6 address.",
                rejected("https://[fe80::1%25eth0]").getMessage());
    }

    @Test
    void ipv4MappedLiteralIsParsedAsIpv4() throws Exception {
        // The JDK turns an IPv4-mapped IPv6 literal into an Inet4Address; the classifier relies on
        // either form reaching the IPv4 checks.
        InetAddress mapped = InetAddress.getByName("::ffff:169.254.169.254");
        assertInstanceOf(Inet4Address.class, mapped);
        assertEquals(AddressVerdict.LINK_LOCAL, PeerAddressPolicy.classify(mapped, true));
    }

    @ParameterizedTest
    @CsvSource({
            "10.0.0.1, ALLOWED",
            "192.168.10.10, ALLOWED",
            "fd12:3456:789a::1, ALLOWED",
            "127.0.0.1, LOOPBACK",
            "::1, LOOPBACK",
            "169.254.169.254, LINK_LOCAL",
            "::a9fe:a9fe, LINK_LOCAL",
            "64:ff9b::7f00:1, LOOPBACK",
            "64:ff9b::a64:101, ALLOWED",
            "100.100.100.200, METADATA",
            "fd00:ec2::254, METADATA",
            "0.0.0.0, UNSPECIFIED",
            "::, UNSPECIFIED",
            "239.1.1.1, MULTICAST",
            "250.1.1.1, RESERVED"
    })
    void classifiesResolvedAddresses(String literal, AddressVerdict expected) throws Exception {
        assertEquals(expected, PeerAddressPolicy.classify(InetAddress.getByName(literal), false));
    }

    @Test
    void loopbackOptInOnlyAffectsLoopback() throws Exception {
        assertEquals(AddressVerdict.ALLOWED, PeerAddressPolicy.classify(InetAddress.getByName("127.0.0.1"), true));
        assertEquals(AddressVerdict.LINK_LOCAL,
                PeerAddressPolicy.classify(InetAddress.getByName("169.254.169.254"), true));
    }
}
