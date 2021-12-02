/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.storage.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * @author eric.wittmann@gmail.com
 */
public class ArtifactSearchResultsDto {
    
    private List<SearchedArtifactDto> artifacts = new ArrayList<SearchedArtifactDto>();
    private long count;
    
    /**
     * Constructor.
     */
    public ArtifactSearchResultsDto() {
    }

    /**
     * @return the artifacts
     */
    public List<SearchedArtifactDto> getArtifacts() {
        return artifacts;
    }

    /**
     * @param artifacts the artifacts to set
     */
    public void setArtifacts(List<SearchedArtifactDto> artifacts) {
        this.artifacts = artifacts;
    }

    /**
     * @return the count
     */
    public long getCount() {
        return count;
    }

    /**
     * @param count the count to set
     */
    public void setCount(long count) {
        this.count = count;
    }

}
