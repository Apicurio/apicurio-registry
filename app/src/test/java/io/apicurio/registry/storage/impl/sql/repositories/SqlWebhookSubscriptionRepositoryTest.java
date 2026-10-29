package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.dto.WebhookSubscriptionSearchResultsDto;
import io.apicurio.registry.storage.error.WebhookSubscriptionNotFoundException;
import io.apicurio.registry.storage.error.WebhookSubscriptionRevisionConflictException;
import io.apicurio.registry.storage.impl.sql.DdlParser;
import io.apicurio.registry.storage.impl.sql.H2SqlStatements;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;
import io.apicurio.registry.storage.impl.sql.jdb.HandleImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the repository against a real H2 database created from the production {@code h2.ddl}.
 */
public class SqlWebhookSubscriptionRepositoryTest {

    private String jdbcUrl;
    private Connection keepAlive;
    private SqlWebhookSubscriptionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:webhook_repo_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        // Holds the in-memory database open for the whole test; closed (and the DB dropped) in tearDown.
        keepAlive = DriverManager.getConnection(jdbcUrl, "sa", "");
        try (InputStream ddl = getClass().getClassLoader()
                .getResourceAsStream("io/apicurio/registry/storage/impl/sql/h2.ddl");
                Statement st = keepAlive.createStatement()) {
            for (String sql : new DdlParser().parse(ddl)) {
                st.execute(sql);
            }
        }
        repository = new SqlWebhookSubscriptionRepository(new JdbcHandleFactory(jdbcUrl),
                new H2SqlStatements(), LoggerFactory.getLogger(getClass()));
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement st = keepAlive.createStatement()) {
            st.execute("DROP ALL OBJECTS");
        }
        keepAlive.close();
    }

    @Test
    void createStoresEveryFieldAndStartsAtRevisionOne() {
        WebhookSubscriptionDto input = subscription("11111111-1111-1111-1111-111111111111", 1000L);
        input.setRevision(42L);
        input.setDeletedOn(5L);

        WebhookSubscriptionDto created = repository.createWebhookSubscription(input);

        assertEquals("11111111-1111-1111-1111-111111111111", created.getSubscriptionId());
        assertEquals("orders", created.getName());
        assertEquals("alice", created.getOwnerId());
        assertEquals("https://hooks.example.com/registry", created.getEndpointUrl());
        assertEquals("[\"io.apicurio.registry.artifact-created\"]", created.getEventTypes());
        assertEquals("com.example", created.getGroupFilter());
        assertEquals("orders-schema", created.getArtifactIdFilter());
        assertEquals("AVRO", created.getArtifactTypeFilter());
        assertFalse(created.isEnabled());
        assertNull(created.getDeletedOn());
        assertEquals(1L, created.getRevision());
        assertEquals("orders-key", created.getSigningSecretRef());
        assertEquals(1000L, created.getCreatedOn());
        assertEquals(1000L, created.getModifiedOn());
        assertEquals(created, repository.getWebhookSubscription(created.getSubscriptionId()));
    }

    @Test
    void createKeepsNullOptionalFieldsNull() {
        WebhookSubscriptionDto input = WebhookSubscriptionDto.builder()
                .subscriptionId("22222222-2222-2222-2222-222222222222")
                .ownerId("bob")
                .endpointUrl("https://hooks.example.com/all")
                .eventTypes("[]")
                .enabled(true)
                .createdOn(2000L)
                .modifiedOn(2000L)
                .build();

        WebhookSubscriptionDto created = repository.createWebhookSubscription(input);

        assertNull(created.getName());
        assertNull(created.getGroupFilter());
        assertNull(created.getArtifactIdFilter());
        assertNull(created.getArtifactTypeFilter());
        assertNull(created.getSigningSecretRef());
        assertEquals("[]", created.getEventTypes());
        assertTrue(created.isEnabled());
    }

    @Test
    void getUnknownIdThrowsNotFoundWithoutEchoingTheId() {
        WebhookSubscriptionNotFoundException ex = assertThrows(WebhookSubscriptionNotFoundException.class,
                () -> repository.getWebhookSubscription("33333333-3333-3333-3333-333333333333"));
        assertEquals("No webhook subscription with this ID.", ex.getMessage());
    }

    @Test
    void searchOrdersByCreatedOnThenIdAndPages() {
        // Inserted out of order; b and a share createdOn so the ID breaks the tie.
        repository.createWebhookSubscription(subscription("cccccccc-0000-0000-0000-000000000000", 300L));
        repository.createWebhookSubscription(subscription("bbbbbbbb-0000-0000-0000-000000000000", 100L));
        repository.createWebhookSubscription(subscription("aaaaaaaa-0000-0000-0000-000000000000", 100L));

        WebhookSubscriptionSearchResultsDto all = repository.searchWebhookSubscriptions(0, 20);
        assertEquals(3L, all.getCount());
        assertEquals(List.of("aaaaaaaa-0000-0000-0000-000000000000", "bbbbbbbb-0000-0000-0000-000000000000",
                "cccccccc-0000-0000-0000-000000000000"), ids(all));

        WebhookSubscriptionSearchResultsDto page = repository.searchWebhookSubscriptions(1, 1);
        assertEquals(3L, page.getCount());
        assertEquals(List.of("bbbbbbbb-0000-0000-0000-000000000000"), ids(page));
    }

    @Test
    void updateReplacesMutableFieldsAndIncrementsRevision() {
        String id = "44444444-4444-4444-4444-444444444444";
        repository.createWebhookSubscription(subscription(id, 1000L));

        WebhookSubscriptionDto changes = WebhookSubscriptionDto.builder()
                .name("renamed")
                .ownerId("mallory")
                .endpointUrl("https://hooks.example.com/v2")
                .eventTypes("[]")
                .enabled(true)
                .signingSecretRef("other-key")
                .createdOn(9L)
                .build();
        WebhookSubscriptionDto updated = repository.updateWebhookSubscription(id, 1L, changes);

        assertEquals(2L, updated.getRevision());
        assertEquals("renamed", updated.getName());
        assertEquals("[]", updated.getEventTypes());
        assertTrue(updated.isEnabled());
        // PUT semantics: mutable fields not supplied are cleared.
        assertNull(updated.getGroupFilter());
        assertNull(updated.getArtifactIdFilter());
        assertNull(updated.getArtifactTypeFilter());
        // Immutable fields keep their stored values, whatever the update carries.
        assertEquals("https://hooks.example.com/registry", updated.getEndpointUrl());
        assertEquals("orders-key", updated.getSigningSecretRef());
        assertEquals("alice", updated.getOwnerId());
        assertEquals(1000L, updated.getCreatedOn());
        assertTrue(updated.getModifiedOn() > 1000L);
    }

    @Test
    void updateWithStaleRevisionThrowsConflictAndChangesNothing() {
        String id = "55555555-5555-5555-5555-555555555555";
        repository.createWebhookSubscription(subscription(id, 1000L));
        repository.updateWebhookSubscription(id, 1L, subscription(id, 1000L));

        assertThrows(WebhookSubscriptionRevisionConflictException.class,
                () -> repository.updateWebhookSubscription(id, 1L, subscription(id, 1000L)));

        assertEquals(2L, repository.getWebhookSubscription(id).getRevision());
    }

    @Test
    void updateUnknownIdThrowsNotFound() {
        assertThrows(WebhookSubscriptionNotFoundException.class,
                () -> repository.updateWebhookSubscription("66666666-6666-6666-6666-666666666666", 1L,
                        subscription("66666666-6666-6666-6666-666666666666", 1000L)));
    }

    @Test
    void softDeleteHidesTheSubscriptionButKeepsTheRowDisabled() throws SQLException {
        String id = "77777777-7777-7777-7777-777777777777";
        WebhookSubscriptionDto enabled = subscription(id, 1000L);
        enabled.setEnabled(true);
        repository.createWebhookSubscription(enabled);
        repository.createWebhookSubscription(subscription("88888888-8888-8888-8888-888888888888", 2000L));

        repository.softDeleteWebhookSubscription(id, 1L);

        assertThrows(WebhookSubscriptionNotFoundException.class, () -> repository.getWebhookSubscription(id));
        WebhookSubscriptionSearchResultsDto results = repository.searchWebhookSubscriptions(0, 20);
        assertEquals(1L, results.getCount());
        assertEquals(List.of("88888888-8888-8888-8888-888888888888"), ids(results));

        try (PreparedStatement ps = keepAlive.prepareStatement(
                "SELECT enabled, revision, deletedOn FROM webhook_subscriptions WHERE subscriptionId = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertFalse(rs.getBoolean("enabled"));
                assertEquals(2L, rs.getLong("revision"));
                assertTrue(rs.getLong("deletedOn") > 0L);
            }
        }
    }

    @Test
    void softDeleteWithStaleRevisionThrowsConflictAndKeepsTheSubscription() {
        String id = "99999999-9999-9999-9999-999999999999";
        repository.createWebhookSubscription(subscription(id, 1000L));

        assertThrows(WebhookSubscriptionRevisionConflictException.class,
                () -> repository.softDeleteWebhookSubscription(id, 7L));

        assertEquals(1L, repository.getWebhookSubscription(id).getRevision());
    }

    @Test
    void writesToAnAlreadyDeletedSubscriptionThrowNotFound() {
        String id = "aaaaaaaa-1111-1111-1111-111111111111";
        repository.createWebhookSubscription(subscription(id, 1000L));
        repository.softDeleteWebhookSubscription(id, 1L);

        // Revision 2 is the deleted row's current revision, so only the deletedOn check can reject these.
        assertThrows(WebhookSubscriptionNotFoundException.class,
                () -> repository.softDeleteWebhookSubscription(id, 2L));
        assertThrows(WebhookSubscriptionNotFoundException.class,
                () -> repository.updateWebhookSubscription(id, 2L, subscription(id, 1000L)));
    }

    private static WebhookSubscriptionDto subscription(String id, long createdOn) {
        return WebhookSubscriptionDto.builder()
                .subscriptionId(id)
                .name("orders")
                .ownerId("alice")
                .endpointUrl("https://hooks.example.com/registry")
                .eventTypes("[\"io.apicurio.registry.artifact-created\"]")
                .groupFilter("com.example")
                .artifactIdFilter("orders-schema")
                .artifactTypeFilter("AVRO")
                .enabled(false)
                .signingSecretRef("orders-key")
                .createdOn(createdOn)
                .modifiedOn(createdOn)
                .build();
    }

    private static List<String> ids(WebhookSubscriptionSearchResultsDto results) {
        return results.getSubscriptions().stream().map(WebhookSubscriptionDto::getSubscriptionId).toList();
    }

    /**
     * Minimal {@link HandleFactory} over plain JDBC: one connection per call, committed on success and
     * rolled back on any exception, which mirrors the transaction boundary of the production factory.
     */
    private static final class JdbcHandleFactory implements HandleFactory {

        private final String url;

        JdbcHandleFactory(String url) {
            this.url = url;
        }

        @Override
        public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                connection.setAutoCommit(false);
                try {
                    R result = callback.withHandle(new HandleImpl(connection));
                    connection.commit();
                    return result;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public <R, X extends Exception> R withHandleNoException(HandleCallback<R, X> callback) {
            try {
                return withHandle(callback);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public <X extends Exception> void withHandleNoException(HandleAction<X> callback) {
            withHandleNoException(handle -> {
                callback.withHandle(handle);
                return null;
            });
        }
    }
}
