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

import io.apicurio.registry.search.common.Search;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * @author Ales Justin
 */
public interface SearchClient extends AutoCloseable {

    BiFunction<SearchResponse, SearchResponse, SearchResponse> SRFN = (sr1, sr2) -> sr2;

    /**
     * Create search client.
     * Check available clients in order: Kafka, Rest, Noop
     *
     * @param properties the properties
     * @return new search client instance
     */
    static SearchClient create(Properties properties) {
        return SearchClientFactory.create(properties);
    }

    /**
     * Initialize the client
     *
     * @param reset should we re-initialize things; if possible
     * @return true if initialization was successful, false otherwise
     * @throws Exception for any error
     */
    CompletionStage<Boolean> initialize(boolean reset) throws Exception;

    /**
     * Index the search artifact.
     *
     * @param artifact the search artifact
     * @throws Exception for any error
     */
    CompletionStage<SearchResponse> index(Search.Artifact artifact) throws Exception;

    /**
     * Bulk index operation.
     *
     * @param artifacts the search artifacts
     * @throws Exception for any error
     */
    default CompletionStage<SearchResponse> index(List<Search.Artifact> artifacts) throws Exception {
        if (artifacts == null || artifacts.size() == 0) {
            throw new IllegalArgumentException("Empty artifacts!");
        }
        CompletionStage<SearchResponse> cs = index(artifacts.get(0));
        for (int i = 1; i < artifacts.size(); i++) {
            cs = cs.thenCombine(index(artifacts.get(i)), SRFN);
        }
        return cs;
    }

    /**
     * Execute query.
     * $Artifact string is replaced with FQN.
     *
     * @param query the query
     * @return response
     */
    CompletionStage<SearchResults> search(String query);
}
