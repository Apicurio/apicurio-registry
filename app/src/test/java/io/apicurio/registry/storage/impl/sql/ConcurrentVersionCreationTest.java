package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.WithByteman;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Run with: ./mvnw test -pl :apicurio-registry-app -Pbyteman -Dtest=ConcurrentVersionCreationTest */
@QuarkusTest
@WithByteman
@EnabledIfSystemProperty(named = "byteman.agent", matches = "true")
public class ConcurrentVersionCreationTest {

    private static final String OPENAPI_V1 = """
            {"openapi": "3.0.2", "info": {"title": "Race V1", "version": "1.0.0"}}""";

    private static final String OPENAPI_V2 = """
            {"openapi": "3.0.2", "info": {"title": "Race V2", "version": "1.0.1"}}""";

    private static final String OPENAPI_V3 = """
            {"openapi": "3.0.2", "info": {"title": "Race V3", "version": "1.0.2"}}""";

    @Inject
    @Current
    RegistryStorage storage;

    @BeforeEach
    void clearBytemanState() {
        System.clearProperty("byteman.writerFrozen");
    }

    @Test
    @BMRules(rules = {
        @BMRule(name = "freeze first writer",
            targetClass = "io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage",
            targetMethod = "createArtifactVersion(String, String, String, String, ContentWrapperDto, EditableVersionMetaDataDto, java.util.List, boolean, boolean, String)",
            targetLocation = "AT ENTRY",
            condition = "NOT flagged(\"writer-entered\")",
            action = "flag(\"writer-entered\"); java.lang.System.setProperty(\"byteman.writerFrozen\", \"true\"); waitFor(\"versionOrder-race\", 10000)"),
        @BMRule(name = "release frozen writer",
            targetClass = "io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage",
            targetMethod = "createArtifactVersion(String, String, String, String, ContentWrapperDto, EditableVersionMetaDataDto, java.util.List, boolean, boolean, String)",
            targetLocation = "AT EXIT",
            condition = "flagged(\"writer-entered\") AND NOT flagged(\"writer-released\")",
            action = "flag(\"writer-released\"); signalWake(\"versionOrder-race\", true)")
    })
    public void testConcurrentVersionCreationGetsDifferentVersionOrder() throws Exception {
        String groupId = "ConcurrentVersionCreationTest";
        String artifactId = "testConcurrentVersionOrder-" + UUID.randomUUID();

        // Create the artifact with its first version (versionOrder = 1)
        storage.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, null, null,
                ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create(OPENAPI_V1))
                        .build(),
                null, Collections.emptyList(), false, false, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread A: will be frozen by Byteman rule at entry to createArtifactVersion
        Future<ArtifactVersionMetaDataDto> futureA = submitInRequestScope(executor,
                () -> storage.createArtifactVersion(
                        groupId, artifactId, null, ArtifactType.OPENAPI,
                        ContentWrapperDto.builder()
                                .contentType(ContentTypes.APPLICATION_JSON)
                                .content(ContentHandle.create(OPENAPI_V2))
                                .build(),
                        null, Collections.emptyList(), false, false, null));

        // Thread B: spin until Thread A is frozen, then create version
        Future<ArtifactVersionMetaDataDto> futureB = submitInRequestScope(executor, () -> {
            long deadline = System.currentTimeMillis() + 5000;
            while (!"true".equals(System.getProperty("byteman.writerFrozen"))) {
                Thread.sleep(50);
                if (System.currentTimeMillis() > deadline) {
                    throw new AssertionError("Timed out waiting for Byteman rule to fire");
                }
            }
            // Small delay to ensure Thread A is fully inside waitFor
            Thread.sleep(100);

            // Thread B creates its version; on exit, Byteman signals Thread A
            return storage.createArtifactVersion(
                    groupId, artifactId, null, ArtifactType.OPENAPI,
                    ContentWrapperDto.builder()
                            .contentType(ContentTypes.APPLICATION_JSON)
                            .content(ContentHandle.create(OPENAPI_V3))
                            .build(),
                    null, Collections.emptyList(), false, false, null);
        });

        ArtifactVersionMetaDataDto resultA = futureA.get(15, TimeUnit.SECONDS);
        ArtifactVersionMetaDataDto resultB = futureB.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert the Byteman rule fired (observable effect)
        Assertions.assertEquals("true", System.getProperty("byteman.writerFrozen"),
                "Byteman rule should have set the writerFrozen flag");

        // Both versions must have unique versionOrder values.
        // Without the SELECT FOR UPDATE fix, concurrent MAX(versionOrder) queries
        // could return the same value, producing duplicate versionOrder.
        Assertions.assertNotEquals(resultA.getVersionOrder(), resultB.getVersionOrder(),
                "Both versions must have different versionOrder values, but both got "
                        + resultA.getVersionOrder());
    }

    /** Submit a task on a thread with an active CDI request scope. */
    private <T> Future<T> submitInRequestScope(ExecutorService executor, Callable<T> task) {
        return executor.submit(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            try {
                return task.call();
            } finally {
                requestContext.deactivate();
            }
        });
    }
}
