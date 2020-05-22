/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.tests.smokeTests.apicurio;

import static io.apicurio.tests.Constants.SMOKE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;

@Tag(SMOKE)
public class LoadTest extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadTest.class);

    private String base = TestUtils.generateArtifactId();

    @RegistryServiceTest(localOnly = false)
    void concurrentLoadTest(RegistryService apicurioService) throws Exception {

        Queue<String> artifactsQueue = new ConcurrentLinkedQueue<>();
        AtomicBoolean deleteLoopFlag = new AtomicBoolean(true);
        AtomicBoolean allCreatedFlag = new AtomicBoolean(false);

        Future<Throwable> deleteingResult = CompletableFuture.supplyAsync(() -> {
            Throwable result = null;
            while (deleteLoopFlag.get()) {
                String artifactId = artifactsQueue.poll();
                try {
                    if (artifactId != null) {
                        LOGGER.info("Delete artifact {} START", artifactId);
                        apicurioService.deleteArtifact(artifactId);
                        TestUtils.assertWebError(404, () -> apicurioService.getArtifactMetaData(artifactId), true);
                        LOGGER.info("Delete artifact {} FINISH", artifactId);
                    } else if (allCreatedFlag.get()) {
                        return null;
                    }
                } catch (Exception e) {
                    LOGGER.info("Requeue artifact {}", artifactId);
                    result = e;
                    artifactsQueue.offer(artifactId);
                }
            }
            LOGGER.info("All artifacts deleted");
            return result;
        }, runnable -> new Thread(runnable).start());

        try {
            List<CompletionStage<Void>> createResults = IntStream.range(0, 1000).mapToObj(i -> {
                return createArtifactAsync(apicurioService, i)
                        .thenAccept(m ->
                            artifactsQueue.offer(m.getId())
                        );
            }).collect(Collectors.toList());

            CompletableFuture.allOf(createResults.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
            LOGGER.info("All artifacts created");
            allCreatedFlag.set(true);
        } catch (Exception e) {
            deleteLoopFlag.set(false);
            LOGGER.error("Error creating artifacts", e);
            throw e;
        }

        try {
            Throwable result = deleteingResult.get(60, TimeUnit.SECONDS);
            if (result != null) {
                deleteLoopFlag.set(false);
                throw new IllegalStateException("Error deleteing artifacts", result);
            }
        } catch (TimeoutException e) {
            LOGGER.info("Artifacts not deleted are {}", apicurioService.listArtifacts().toString());
            throw e;
        }

        assertEquals(0, apicurioService.listArtifacts().size());

    }

    CompletionStage<ArtifactMetaData> createArtifactAsync(RegistryService apicurioService, int i) {
        return CompletableFuture.supplyAsync(() -> {
            String artifactId = base + i;

            LOGGER.info("Create artifact {} START", artifactId);

            String artifactDefinition = "{\"type\":\"INVALID\",\"config\":\"invalid\"}";
            ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
            try {
                CompletionStage<ArtifactMetaData> csResult = apicurioService.createArtifact(ArtifactType.JSON, artifactId, null, artifactData);

                // Make sure artifact is fully registered
                ArtifactMetaData amd = ConcurrentUtil.result(csResult);
                TestUtils.retry(() -> apicurioService.getArtifactMetaDataByGlobalId(amd.getGlobalId()));

                LOGGER.info("Create artifact {} FINISH", amd.getId());
                assertEquals(artifactId, amd.getId());
                Thread.sleep(1);
                return amd;
            } catch (Exception e) {
                throw new CompletionException("Error creating artifact", e);
            }
        }, runnable -> new Thread(runnable).start());
    }

}
