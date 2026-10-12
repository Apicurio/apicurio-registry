package io.apicurio.registry.storage;

import io.apicurio.registry.storage.dto.PeerDto;
import io.apicurio.registry.storage.error.InvalidPeerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PeerValidatorTest {

    private static PeerDto.PeerDtoBuilder validPeer() {
        return PeerDto.builder().peerId("eu-registry").url("https://registry.eu.example.com").enabled(true);
    }

    @Test
    void acceptsMinimalAndFullPeers() {
        assertDoesNotThrow(() -> PeerValidator.validate(validPeer().build()));
        assertDoesNotThrow(() -> PeerValidator.validate(validPeer().url("https://registry.example.com:8443/registry")
                .name("EU registry").description("Peer in the EU region").credentialSecretRef("eu-registry.token")
                .build()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "EU", "eu-Registry", "local", ".", "..", "eu/registry", "eu registry" })
    void rejectsInvalidPeerIds(String peerId) {
        assertThrows(InvalidPeerException.class, () -> PeerValidator.validatePeerId(peerId));
    }

    @Test
    void rejectsReservedLocalPeerIdWithSpecificMessage() {
        InvalidPeerException ex = assertThrows(InvalidPeerException.class,
                () -> PeerValidator.validatePeerId("local"));
        assertEquals("Peer id 'local' is reserved.", ex.getMessage());
    }

    @Test
    void rejectsUrlWithQuery() {
        InvalidPeerException ex = assertThrows(InvalidPeerException.class,
                () -> PeerValidator.validate(validPeer().url("https://registry.example.com/?tenant=eu").build()));
        assertEquals("Peer url must not contain a query.", ex.getMessage());
    }

    @Test
    void rejectsNonAsciiUrl() {
        InvalidPeerException ex = assertThrows(InvalidPeerException.class,
                () -> PeerValidator.validate(validPeer().url("https://registry.example.com/régistre").build()));
        assertEquals("Peer url must contain only ASCII characters.", ex.getMessage());
    }

    @Test
    void acceptsPunycodeHost() {
        assertDoesNotThrow(() -> PeerValidator.validate(validPeer().url("https://xn--rgistre-bya.example.com").build()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://registry.example.com:0", "https://registry.example.com:65536",
            "https://registry.example.com:99999" })
    void rejectsPortsOutsideTheTcpRange(String url) {
        InvalidPeerException ex = assertThrows(InvalidPeerException.class,
                () -> PeerValidator.validate(validPeer().url(url).build()));
        assertEquals("Peer url port must be between 1 and 65535.", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://registry.example.com:1", "https://registry.example.com:8443",
            "https://registry.example.com:65535", "https://registry.example.com" })
    void acceptsPortsInsideTheTcpRangeAndNoPort(String url) {
        assertDoesNotThrow(() -> PeerValidator.validate(validPeer().url(url).build()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "..data", "..2026_09_27_10_00_00.123456789", "...token", "..", ".", "../token",
            "dir/token", "" })
    void rejectsCredentialSecretRefsThatAreNotPlainKeyNames(String ref) {
        InvalidPeerException ex = assertThrows(InvalidPeerException.class,
                () -> PeerValidator.validate(validPeer().credentialSecretRef(ref).build()));
        assertEquals("Peer credential secret reference is invalid.", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "token", ".token", "eu.registry..token", "EU_Registry-1" })
    void acceptsCredentialSecretRefsThatAreKeyNames(String ref) {
        assertDoesNotThrow(() -> PeerValidator.validate(validPeer().credentialSecretRef(ref).build()));
    }
}
