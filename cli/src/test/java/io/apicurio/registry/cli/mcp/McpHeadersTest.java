package io.apicurio.registry.cli.mcp;

import io.apicurio.registry.cli.common.CliException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpHeadersTest {

    private static final String SECRET = "s3cr3t-value";

    private static Map<String, String> read(String file, List<String> env, Map<String, String> vars) {
        return McpHeaders.read(file, env, vars::get);
    }

    /** A refused header names the problem, exits with the validation code, and never shows a value. */
    private static void assertRefused(String file, List<String> env, Map<String, String> vars, String message) {
        assertThatThrownBy(() -> read(file, env, vars))
                .isInstanceOf(CliException.class)
                .hasMessageContaining(message)
                .hasMessageNotContaining(SECRET)
                .extracting(e -> ((CliException) e).getCode()).isEqualTo(VALIDATION_ERROR_RETURN_CODE);
    }

    @Test
    void readsFileAndEnvironmentInOrder() {
        String file = "# credentials\n\nAuthorization: Bearer " + SECRET + "\r\nX-Tenant:  acme \n";
        Map<String, String> headers = read(file, List.of("X-Api-Key=MCP_KEY"), Map.of("MCP_KEY", "k1"));

        assertThat(headers).containsExactly(
                Map.entry("Authorization", "Bearer " + SECRET),
                Map.entry("X-Tenant", "acme"),
                Map.entry("X-Api-Key", "k1"));
    }

    @Test
    void nothingGivenMeansNoHeaders() {
        assertThat(read(null, null, Map.of())).isEmpty();
        assertThat(read("\n# only comments\n", List.of(), Map.of())).isEmpty();
    }

    @Test
    void valueMayContainColons() {
        assertThat(read("X-Url: https://a:8443/x", null, Map.of())).containsEntry("X-Url", "https://a:8443/x");
    }

    @Test
    void lineWithoutAColonIsRefusedWithoutShowingIt() {
        assertRefused("Bearer " + SECRET, null, Map.of(), "Line 1 of the header file is not 'Name: value'.");
        assertRefused("ok: 1\n: " + SECRET, null, Map.of(), "Line 2");
    }

    @Test
    void missingEnvironmentVariableNamesTheVariable() {
        assertRefused(null, List.of("Authorization=MCP_TOKEN"), Map.of(),
                "Environment variable 'MCP_TOKEN' for header 'Authorization' is not set.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "=MCP_TOKEN", "Authorization="})
    void malformedEnvMappingIsRefused(String mapping) {
        assertRefused(null, List.of(mapping), Map.of(), "--header-env must be Name=VARIABLE.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"X(Test)", "X Test", "X=Test", "Ä"})
    void invalidNameIsRefused(String name) {
        assertRefused(name + ": " + SECRET, null, Map.of(), "is not a valid HTTP header name.");
    }

    @Test
    void emptyValueIsRefused() {
        assertRefused("Authorization:", null, Map.of(), "Header 'Authorization' has an empty value.");
        assertRefused(null, List.of("Authorization=EMPTY"), Map.of("EMPTY", ""), "has an empty value.");
    }

    @Test
    void lineBreakInAnEnvironmentValueIsRefused() {
        assertRefused(null, List.of("X-Test=V"), Map.of("V", SECRET + "\r\nInjected: x"),
                "Header 'X-Test' has an invalid value.");
    }

    @Test
    void sameNameTwiceIsRefusedWhateverTheCase() {
        assertRefused("Authorization: a\n", List.of("authorization=V"), Map.of("V", SECRET),
                "Header 'authorization' is given more than once.");
    }
}
