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

import org.junit.jupiter.api.Disabled;

import io.apicurio.tests.ApicurioV2BaseIT;

/**
 * Disabled because this is too flaky and it needs to be redesigned to align better
 * with common usage of the registry, which is frequent reads sporadic writes.
 *
 * @author Fabian Martinez
 *
 */
//@Tag(SMOKE)
@Disabled
public class LoadIT extends ApicurioV2BaseIT {

//    private static final Logger LOGGER = LoggerFactory.getLogger(LoadIT.class);
//
//    private String base = TestUtils.generateArtifactId();
//
//    @Test
//    void concurrentLoadTest() throws Exception {
//
//        Queue<String> artifactsQueue = new ConcurrentLinkedQueue<>();
//        AtomicBoolean deleteLoopFlag = new AtomicBoolean(true);
//        AtomicBoolean allCreatedFlag = new AtomicBoolean(false);
//
//        Future<Throwable> deleteingResult = CompletableFuture.supplyAsync(() -> {
//            Throwable result = null;
//            while (deleteLoopFlag.get()) {
//                String artifactId = artifactsQueue.poll();
//                try {
//                    if (artifactId != null) {
//                        LOGGER.info("Delete artifact {} START", artifactId);
//                        registryClient.deleteArtifact(artifactId);
//                        TestUtils.assertClientError(ArtifactNotFoundException.class.getSimpleName(), 404, () -> registryClient.getArtifactMetaData(artifactId), true, errorCodeExtractor);
//                        LOGGER.info("Delete artifact {} FINISH", artifactId);
//                    } else if (allCreatedFlag.get()) {
//                        return null;
//                    }
//                } catch (Exception e) {
//                    LOGGER.info("Requeue artifact {}", artifactId);
//                    result = e;
//                    artifactsQueue.offer(artifactId);
//                }
//            }
//            LOGGER.info("All artifacts deleted");
//            return result;
//        }, runnable -> new Thread(runnable).start());
//
//        try {
//            List<CompletionStage<Void>> createResults = IntStream.range(0, 250).mapToObj(i -> {
//                return createArtifactAsync(registryClient, i)
//                        .thenAccept(m ->
//                            artifactsQueue.offer(m.getId())
//                        );
//            }).collect(Collectors.toList());
//
//            CompletableFuture.allOf(createResults.toArray(new CompletableFuture[0]))
//                .get(60, TimeUnit.SECONDS);
//            LOGGER.info("All artifacts created");
//            allCreatedFlag.set(true);
//        } catch (Exception e) {
//            deleteLoopFlag.set(false);
//            LOGGER.error("Error creating artifacts", e);
//            throw e;
//        }
//
//        try {
//            Throwable result = deleteingResult.get(120, TimeUnit.SECONDS);
//            if (result != null) {
//                deleteLoopFlag.set(false);
//                throw new IllegalStateException("Error deleteing artifacts", result);
//            }
//        } catch (TimeoutException e) {
//            LOGGER.info("Artifacts not deleted are {}", registryClient.listArtifacts().toString());
//            throw e;
//        }
//
//        assertEquals(0, registryClient.listArtifacts().size());
//
//    }
//
//    CompletionStage<ArtifactMetaData> createArtifactAsync(RegistryRestClient client, int i) {
//        return CompletableFuture.supplyAsync(() -> {
//            String artifactId = base + i;
//
//            LOGGER.info("Create artifact {} START", artifactId);
//
//            String artifactDefinition = "{\"type\":\"INVALID\",\"config\":\"invalid\"}";
//            ByteArrayInputStream artifactData = new ByteArrayInputStream(artifactDefinition.getBytes(StandardCharsets.UTF_8));
//            try {
//                ArtifactMetaData amd = client.createArtifact(artifactId, ArtifactType.JSON, artifactData);
//
//                // Make sure artifact is fully registered
//                TestUtils.retry(() -> client.getArtifactMetaDataByGlobalId(amd.getGlobalId()));
//
//                LOGGER.info("Create artifact {} FINISH", amd.getId());
//                assertEquals(artifactId, amd.getId());
//                Thread.sleep(1);
//                return amd;
//            } catch (Exception e) {
//                LOGGER.error("Error creating artifact " + artifactId, e);
//                throw new CompletionException("Error creating artifact", e);
//            }
//        }, runnable -> new Thread(runnable).start());
//    }

}
