package io.apicurio.registry.federation;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.NewPeer;
import io.apicurio.registry.rest.client.models.Peer;
import io.apicurio.registry.rest.client.models.UpdatePeer;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Peer management is restricted to administrators.
 */
@QuarkusTest
@TestProfile(FederationAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class PeersAuthTest extends AbstractResourceTestBase {

    private static final String PEER_ID = "auth-peer";

    @ConfigProperty(name = "quarkus.oidc.token-path")
    String authServerUrlConfigured;

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return clientFor(vertx, KeycloakTestContainerManager.ADMIN_CLIENT_ID);
    }

    private RegistryClient clientFor(Vertx vertx, String clientId) {
        return RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, clientId, "test1"));
    }

    private static NewPeer newPeer(String peerId) {
        NewPeer peer = new NewPeer();
        peer.setPeerId(peerId);
        peer.setUrl("https://registry.auth.example.com");
        return peer;
    }

    private static UpdatePeer update() {
        UpdatePeer update = new UpdatePeer();
        update.setUrl("https://registry.auth.example.com");
        update.setEnabled(false);
        return update;
    }

    private static List<Executable> everyOperation(RegistryClient client) {
        return List.of(
                () -> client.admin().peers().get(),
                () -> client.admin().peers().post(newPeer("denied-peer")),
                () -> client.admin().peers().byPeerId(PEER_ID).get(),
                () -> client.admin().peers().byPeerId(PEER_ID).put(update()),
                () -> client.admin().peers().byPeerId(PEER_ID).delete());
    }

    @Test
    void adminManagesPeers() throws Exception {
        clientV3.admin().peers().post(newPeer(PEER_ID));
        try {
            Peer peer = clientV3.admin().peers().byPeerId(PEER_ID).get();
            assertEquals("https://registry.auth.example.com", peer.getUrl());
            assertEquals(Boolean.TRUE, peer.getEnabled());

            clientV3.admin().peers().byPeerId(PEER_ID).put(update());
            assertEquals(Boolean.FALSE, clientV3.admin().peers().byPeerId(PEER_ID).get().getEnabled());
            assertEquals(1, clientV3.admin().peers().get().getCount());
        } finally {
            clientV3.admin().peers().byPeerId(PEER_ID).delete();
        }
        assertEquals(0, clientV3.admin().peers().get().getCount());
    }

    static Stream<String> nonAdminClients() {
        return Stream.of(KeycloakTestContainerManager.DEVELOPER_CLIENT_ID,
                KeycloakTestContainerManager.READONLY_CLIENT_ID);
    }

    @ParameterizedTest
    @MethodSource("nonAdminClients")
    void nonAdminsAreForbidden(String clientId) throws Exception {
        clientV3.admin().peers().post(newPeer(PEER_ID));
        try {
            RegistryClient client = clientFor(vertx, clientId);
            for (Executable operation : everyOperation(client)) {
                Exception exception = Assertions.assertThrows(Exception.class, operation);
                assertForbidden(exception);
            }
            // Nothing a non-admin sent reached storage
            Peer peer = clientV3.admin().peers().byPeerId(PEER_ID).get();
            assertEquals(Boolean.TRUE, peer.getEnabled());
            assertEquals(1, clientV3.admin().peers().get().getCount());
        } finally {
            clientV3.admin().peers().byPeerId(PEER_ID).delete();
        }
    }

    @Test
    void anonymousCallersAreNotAuthorized() throws Exception {
        clientV3.admin().peers().post(newPeer(PEER_ID));
        try {
            RegistryClient anonymous = RegistryClientFactory.create(
                    RegistryClientOptions.create(registryV3ApiUrl, vertx));
            for (Executable operation : everyOperation(anonymous)) {
                Exception exception = Assertions.assertThrows(Exception.class, operation);
                assertNotAuthorized(exception);
            }
            assertEquals(1, clientV3.admin().peers().get().getCount());
        } finally {
            clientV3.admin().peers().byPeerId(PEER_ID).delete();
        }
    }
}
