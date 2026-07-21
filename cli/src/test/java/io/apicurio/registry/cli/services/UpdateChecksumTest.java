package io.apicurio.registry.cli.services;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the SHA-256 download-integrity helpers used by {@link Update} to verify the
 * checksum of downloaded update archives.
 */
public class UpdateChecksumTest {

    @Test
    public void testSha256HexOfEmptyInput() {
        assertThat(Update.sha256Hex(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    public void testSha256HexOfKnownInput() {
        assertThat(Update.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    public void testParseChecksumDigestOnly() {
        assertThat(Update.parseChecksum("ABC123def\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("abc123def");
    }

    @Test
    public void testParseChecksumWithTrailingFileName() {
        // Maven checksum files may contain "<digest>  <filename>".
        var content = "9f86d081884c7d659a2feaa0c55ad015  apicurio-registry-cli-1.0.0-linux-x86_64.zip\n";
        assertThat(Update.parseChecksum(content.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("9f86d081884c7d659a2feaa0c55ad015");
    }
}
