package io.apicurio.registry.agents.mcpimport;

import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.net.ssl.SSLContext;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpImportTlsResolutionTest {

    private final TlsConfigurationRegistry registry = Mockito.mock(TlsConfigurationRegistry.class);

    @Test
    void noNameUsesTheJvmDefault() {
        assertNull(McpToolsClient.sslContext(registry, null));
        assertNull(McpToolsClient.sslContext(registry, " "));
        Mockito.verifyNoInteractions(registry);
    }

    @Test
    void namedConfigurationProvidesTheSslContext() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        TlsConfiguration tls = Mockito.mock(TlsConfiguration.class);
        Mockito.when(tls.createSSLContext()).thenReturn(context);
        Mockito.when(registry.get("internal-ca")).thenReturn(Optional.of(tls));

        assertSame(context, McpToolsClient.sslContext(registry, "internal-ca"));
    }

    @Test
    void unknownNameIsRefused() {
        Mockito.when(registry.get("missing")).thenReturn(Optional.empty());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> McpToolsClient.sslContext(registry, "missing"));

        assertEquals("apicurio.mcp-import.tls-configuration.name is 'missing', but no TLS configuration has "
                + "that name (quarkus.tls.missing.*)", e.getMessage());
    }
}
