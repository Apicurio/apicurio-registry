package io.apicurio.registry.noprofile.rest.a2a;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.CommitFailedException;
import io.apicurio.registry.utils.tests.PostgreSqlEmbeddedTestResource;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Includes the SQL safety cases and a coordinated real-database conditional publication race. */
@QuarkusTest
@TestProfile(OpenApiAgentCardPostgresqlTest.Profile.class)
class OpenApiAgentCardPostgresqlTest extends OpenApiAgentCardSafetyTest {
    public static class Profile extends OpenApiAgentCardSafetyTest.Profile {
        @Override
        public Map<String, String> getConfigOverrides() {
            var config = super.getConfigOverrides();
            config.put("apicurio.storage.sql.kind", "postgresql");
            return config;
        }

        @Override
        public List<TestResourceEntry> testResources() {
            return List.of(new TestResourceEntry(PostgreSqlEmbeddedTestResource.class));
        }
    }

    @Inject
    @Current
    RegistryStorage storage;
    @Inject
    @Named("application")
    AgroalDataSource datasource;

    @Test
    void concurrentConditionalPublicationsCannotBothCommit() throws Exception {
        String group = "race-" + UUID.randomUUID();
        String path = "/registry/v3/groups/" + group + "/artifacts";
        given().auth().preemptive().basic("alice","alice").contentType(CT_JSON)
                .body(Map.of("artifactId","card","artifactType","JSON","firstVersion",Map.of("version","1",
                        "content",Map.of("content","{}","contentType",CT_JSON))))
                .post(path).then().statusCode(200);
        ContentWrapperDto content = ContentWrapperDto.builder().content(ContentHandle.create("{}"))
                .contentType(CT_JSON).references(List.of()).build();
        var executor = Executors.newFixedThreadPool(2);
        try (var blocker = datasource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT versionOrder FROM versions WHERE groupId = ? AND artifactId = ? FOR UPDATE")) {
                lock.setString(1,group);
                lock.setString(2,"card");
                try (var result = lock.executeQuery()) {
                    result.next();
                    assertEquals(1,result.getInt(1));
                }
            }
            var first = executor.submit(() -> publishConditionally(group, content));
            var second = executor.submit(() -> publishConditionally(group, content));
            // Both writers must have entered storage before releasing the blocker. Old code queued
            // both on the old version row; fixed code queues one on the stable artifact row instead.
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                try (var connection = datasource.getConnection();
                        var statement = connection.createStatement();
                        var result = statement.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                                + " AND wait_event_type = 'Lock' AND (query LIKE '%UPDATE artifacts SET modifiedOn = modifiedOn%'"
                                + " OR query LIKE '%SELECT v.versionOrder FROM versions v%')")) {
                    result.next();
                    assertEquals(2,result.getInt(1));
                }
            });
            blocker.commit();
            int committed = (first.get(20,TimeUnit.SECONDS) ? 1 : 0) + (second.get(20,TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1,committed);
            assertEquals(2,storage.getArtifactVersions(group,"card").size());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(20,TimeUnit.SECONDS);
        }
    }

    private boolean publishConditionally(String group, ContentWrapperDto content) {
        var context = Arc.container().requestContext();
        context.activate();
        try {
            storage.createArtifactVersionIfLatest(group,"card",null,"JSON",content,
                    EditableVersionMetaDataDto.builder().build(),List.of(),false,"alice",1,null);
            return true;
        } catch (CommitFailedException expected) {
            return false;
        } finally {
            context.terminate();
        }
    }
}
