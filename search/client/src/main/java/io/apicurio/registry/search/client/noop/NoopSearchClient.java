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

package io.apicurio.registry.search.client.noop;

import io.apicurio.registry.search.client.SearchClient;
import io.apicurio.registry.search.client.SearchResponse;
import io.apicurio.registry.search.client.SearchResults;
import io.apicurio.registry.search.common.Search;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author Ales Justin
 */
public class NoopSearchClient implements SearchClient {
    private static final SearchResults INSTANCE = new NoopSearchResults();

    public NoopSearchClient() {
    }

    public NoopSearchClient(Properties properties) {
        // to be used in config
    }

    @Override
    public CompletionStage<Boolean> initialize(boolean reset) {
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    @Override
    public CompletionStage<SearchResponse> index(Search.Artifact artifact) {
        return CompletableFuture.completedFuture(INSTANCE);
    }

    @Override
    public CompletionStage<SearchResults> search(String query) {
        return CompletableFuture.completedFuture(INSTANCE);
    }

    @Override
    public void close() {
    }

    private static class NoopSearchResults implements SearchResults {
        @Override
        public int getTotalHits() {
            return 0;
        }

        @Override
        public List<Search.Artifact> getArtifacts() {
            return Collections.emptyList();
        }

        @Override
        public boolean ok() {
            return false;
        }

        @Override
        public int status() {
            return 0;
        }
    }
}
