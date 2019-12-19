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

package io.apicurio.registry.search.client.hotrod;

import io.apicurio.registry.search.client.SearchResults;
import io.apicurio.registry.search.common.Search;

import java.util.List;

/**
 * @author Ales Justin
 */
public class HotRodSearchResults extends HotRodSearchResponse implements SearchResults {
    private final List<Search.Artifact> artifacts;

    public HotRodSearchResults(List<Search.Artifact> artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public int getTotalHits() {
        return artifacts.size();
    }

    @Override
    public List<Search.Artifact> getArtifacts() {
        return artifacts;
    }
}
