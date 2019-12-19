/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.search.client;

import io.apicurio.registry.common.proto.Cmmn;
import io.apicurio.registry.search.common.Search;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * @author Ales Justin
 */
public abstract class SearchClientTestBase {

    abstract boolean isServerRunning();

    abstract SearchClient createSearchClient(Properties properties);

    abstract void clearSearchCache();

    abstract <T extends SearchResponse> T ok(CompletionStage<T> cs) throws Exception;

    private void query(SearchClient client, String query, int expectedHits) throws Exception {
        CompletionStage<SearchResults> search = client.search(query);
        SearchResults response = ok(search);
        Assertions.assertNotNull(response);
        Assertions.assertTrue(response.ok());
        List<Search.Artifact> artifacts = response.getArtifacts();
        Assertions.assertEquals(expectedHits, artifacts.size());
        System.out.println(query + " ==> " + artifacts.size() + "\n" + artifacts);
    }

    @Test
    public void testClient() throws Exception {
        Assumptions.assumeTrue(isServerRunning());

        SearchClient client = createSearchClient(System.getProperties());
        CompletionStage<Boolean> initialize = client.initialize(true);
        Assertions.assertTrue(initialize.toCompletableFuture().get());

        String artifactId = UUID.randomUUID().toString();
        List<Search.Artifact> artifacts = new ArrayList<>();

        artifacts.add(Search.Artifact.newBuilder()
                                     .setArtifactId(artifactId)
                                     .setType(Cmmn.ArtifactType.PROTOBUF)
                                     .setContent("{\"foo\":\"bar\"}")
                                     .setVersion(1)
                                     .setGlobalId(1)
                                     .setName("My first schema")
                                     .setDescription("Initial test schema")
                                     .setCreatedBy("alesj")
                                     .build());

        artifacts.add(Search.Artifact.newBuilder()
                                     .setArtifactId(artifactId)
                                     .setType(Cmmn.ArtifactType.AVRO)
                                     .setContent("{\"foo\":\"baz\"}")
                                     .setVersion(2)
                                     .setGlobalId(2)
                                     .setName("Twoooo")
                                     .setDescription("Dup hupp test schema")
                                     .setCreatedBy("alesj")
                                     .build());

        clearSearchCache();

        CompletionStage<SearchResponse> index = client.index(artifacts);
        ok(index);

        query(client, "from $Artifact", 2);
        query(client, "from $Artifact where type = 'PROTOBUF'", 1);
        query(client, "from $Artifact where version > 1", 1);
        query(client, "from $Artifact where content : 'bar'", 1);
        query(client, "from $Artifact where globalId = 2", 1);
        query(client, "from $Artifact where name : 'first'", 1);
        query(client, "from $Artifact where description : 'hupp'", 1);
        query(client, "from $Artifact where createdBy : 'alesj'", 2);
    }
}
