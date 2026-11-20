package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.agents.mcpimport.McpImportException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpImportTargetPolicyTest {

    private static final List<String> ANY_HOST = List.of("*");

    private static URI check(String url, List<String> allowed) {
        return McpImportTargetPolicy.check(url, allowed, false, false);
    }

    private static void assertDenied(String url, List<String> allowed, boolean insecureHttp, boolean loopback) {
        McpImportException e = assertRefused(
                () -> McpImportTargetPolicy.check(url, allowed, insecureHttp, loopback));
        // One message for every host refusal: the endpoint must not tell what exists or resolves.
        assertEquals(McpImportTargetPolicy.DENIED, e.getMessage());
    }

    /** Every refusal is a client error (400), counted as host-denied, with nothing contacted. */
    private static McpImportException assertRefused(Executable check) {
        McpImportException e = assertThrows(McpImportException.class, check);
        assertEquals(Reason.HOST_DENIED, e.getReason());
        assertEquals(400, e.getResponse().getStatus());
        return e;
    }

    @Test
    void emptyAllowListRefusesEveryHost() {
        assertDenied("https://10.1.2.3/mcp", List.of(), false, false);
        assertDenied("https://[2001:db8::1]/mcp", List.of(), false, false);
    }

    @Test
    void listedAddressAndRangeAreAllowed() {
        assertEquals(URI.create("https://10.1.2.3/mcp"), check("https://10.1.2.3/mcp", List.of("10.1.2.3")));
        assertEquals(URI.create("https://10.1.2.3:8443/mcp"),
                check("https://10.1.2.3:8443/mcp", List.of("10.0.0.0/8")));
        assertEquals(URI.create("https://[2001:db8::5]/mcp"),
                check("https://[2001:db8::5]/mcp", List.of("2001:db8::/32")));
        assertDenied("https://11.1.2.3/mcp", List.of("10.0.0.0/8"), false, false);
        assertDenied("https://10.1.2.3/mcp", List.of("10.1.2.4"), false, false);
    }

    @ParameterizedTest
    @CsvSource({
            "mcp.example.com, mcp.example.com, true",
            "MCP.Example.COM., mcp.example.com, true",
            "a.example.com, *.example.com, true",
            "a.b.example.com, *.example.com, true",
            "example.com, *.example.com, false",
            "evilexample.com, *.example.com, false",
            "mcp.example.com, *, true",
            "other.example.com, mcp.example.com, false",
            "mcp.example.com, 10.0.0.0/8, false"})
    void hostEntries(String host, String entry, boolean matches) {
        String normalized = host.toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
        assertEquals(matches, McpImportTargetPolicy.matchesHostEntry(normalized, entry));
    }

    // Refused even when every host is allowed: these are never reachable through an import.
    @ParameterizedTest
    @ValueSource(strings = {"https://169.254.169.254/latest/meta-data/", "https://100.100.100.200/",
            "https://[fd00:ec2::254]/", "https://[::ffff:169.254.169.254]/", "https://0.0.0.0/",
            "https://224.0.0.1/", "https://255.255.255.255/", "https://[fe80::1]/",
            "https://[64:ff9b::a9fe:a9fe]/"})
    void blockedAddressesAreRefusedWhateverTheAllowList(String url) {
        assertDenied(url, List.of("*", "0.0.0.0/0", "::/0"), true, true);
    }

    @Test
    void loopbackNeedsTheOptIn() {
        assertDenied("http://127.0.0.1:8080/mcp", List.of("*"), true, false);
        assertDenied("http://localhost:8080/mcp", List.of("*"), true, false);
        assertEquals(URI.create("http://127.0.0.1:8080/mcp"),
                McpImportTargetPolicy.check("http://127.0.0.1:8080/mcp", List.of("127.0.0.1"), true, true));
    }

    // Both name 127.0.0.1 to some resolvers. java.net.URI already rejects the second as a host,
    // so it is refused for having none; either way it is never resolved or connected to.
    @ParameterizedTest
    @ValueSource(strings = {"https://2130706433/", "https://0x7f.1/"})
    void ambiguousNumericHostsAreRefused(String url) {
        assertRefused(() -> McpImportTargetPolicy.check(url, ANY_HOST, false, true));
    }

    @Test
    void unresolvableHostGetsTheSameMessage() {
        assertDenied("https://does-not-exist.invalid/mcp", List.of("*.invalid"), false, false);
    }

    @Test
    void urlShapeIsChecked() {
        assertEquals("The MCP server URL must use https.",
                assertRefused(() -> check("http://10.1.2.3/mcp", ANY_HOST)).getMessage());
        assertEquals("The MCP server URL must use https.",
                assertRefused(() -> check("ftp://10.1.2.3/mcp", ANY_HOST)).getMessage());
        assertEquals("The MCP server URL must not contain user information.",
                assertRefused(() -> check("https://user:pass@10.1.2.3/mcp", ANY_HOST)).getMessage());
        assertEquals("The MCP server URL must be an absolute URI with a host.",
                assertRefused(() -> check("https:///mcp", ANY_HOST)).getMessage());
        assertEquals("The MCP server URL is not a valid URI.",
                assertRefused(() -> check("not a url", ANY_HOST)).getMessage());
        assertEquals("The MCP server URL is required.", assertRefused(() -> check(null, ANY_HOST)).getMessage());
    }
}
