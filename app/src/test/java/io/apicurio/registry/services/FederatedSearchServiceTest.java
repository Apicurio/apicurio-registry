package io.apicurio.registry.services;

import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.SearchedArtifact;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

@QuarkusTest
class FederatedSearchServiceTest {

    @Inject
    FederatedSearchService federatedSearchService;

    @InjectMock
    PeerRegistryService peerRegistryService;

    @Test
    void testFederatedSearchNoPeers() {
        Mockito.when(peerRegistryService.getEnabledPeers()).thenReturn(Collections.emptyList());

        ArtifactSearchResults local = new ArtifactSearchResults();
        local.setCount(1);
        SearchedArtifact artifact = new SearchedArtifact();
        artifact.setArtifactId("local-agent");
        local.setArtifacts(Collections.singletonList(artifact));

        ArtifactSearchResults result = federatedSearchService.federateSearch(local, null, null, null);
        Assertions.assertEquals(1, result.getCount());
        Assertions.assertEquals("local-agent", result.getArtifacts().get(0).getArtifactId());
    }
}
