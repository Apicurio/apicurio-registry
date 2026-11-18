package io.apicurio.registry.resolver.client;

import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import io.apicurio.registry.rest.client.RegistryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The content-ID and content-hash lookups ask the registry to dereference only when requested (#11134).
 */
class RegistryClientFacadeDereferenceTest {

    private RequestAdapter adapter;
    private RegistryClientFacade facade;

    @BeforeEach
    void init() {
        adapter = mock(RequestAdapter.class);
        when(adapter.getBaseUrl()).thenReturn("http://registry/apis/registry/v3");
        when(adapter.sendPrimitive(any(), any(), any())).thenAnswer(
                i -> new ByteArrayInputStream("schema".getBytes(StandardCharsets.UTF_8)));
        facade = new RegistryClientFacadeImpl(new RegistryClient(adapter));
    }

    private String requestedUri() throws Exception {
        ArgumentCaptor<RequestInformation> request = ArgumentCaptor.forClass(RequestInformation.class);
        verify(adapter).sendPrimitive(request.capture(), any(), any());
        RequestInformation info = request.getValue();
        // The client sets the base URL on the adapter, not on the request; supply it to build the URI.
        info.pathParameters.put("baseurl", "http://registry/apis/registry/v3");
        return info.getUri().toString();
    }

    @Test
    void contentIdDereferencedAddsReferencesParameter() throws Exception {
        assertEquals("schema", facade.getSchemaByContentId(7L, true));
        assertEquals("http://registry/apis/registry/v3/ids/contentIds/7?references=DEREFERENCE", requestedUri());
    }

    @Test
    void contentIdNotDereferencedHasNoParameter() throws Exception {
        assertEquals("schema", facade.getSchemaByContentId(7L));
        assertEquals("http://registry/apis/registry/v3/ids/contentIds/7", requestedUri());
    }

    @Test
    void contentHashDereferencedAddsReferencesParameter() throws Exception {
        assertEquals("schema", facade.getSchemaByContentHash("abc", true));
        assertEquals("http://registry/apis/registry/v3/ids/contentHashes/abc?references=DEREFERENCE",
                requestedUri());
    }

    @Test
    void contentHashNotDereferencedHasNoParameter() throws Exception {
        assertEquals("schema", facade.getSchemaByContentHash("abc", false));
        assertEquals("http://registry/apis/registry/v3/ids/contentHashes/abc", requestedUri());
    }

    @Test
    void facadesWithoutSupportRejectDereferencing() {
        RegistryClientFacade v2 = new RegistryClientFacadeImpl_v2(null);
        assertThrows(UnsupportedOperationException.class, () -> v2.getSchemaByContentId(7L, true));
        assertThrows(UnsupportedOperationException.class, () -> v2.getSchemaByContentHash("abc", true));
    }
}
