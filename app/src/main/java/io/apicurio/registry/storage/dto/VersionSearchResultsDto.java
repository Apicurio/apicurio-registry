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
public class VersionSearchResultsDto {
    
    private long count;
    private List<SearchedVersionDto> versions = new ArrayList<SearchedVersionDto>();
    
    /**
     * Constructor.
     */
    public VersionSearchResultsDto() {
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

    /**
     * @return the versions
     */
    public List<SearchedVersionDto> getVersions() {
        return versions;
    }

    /**
     * @param versions the versions to set
     */
    public void setVersions(List<SearchedVersionDto> versions) {
        this.versions = versions;
    }

}
