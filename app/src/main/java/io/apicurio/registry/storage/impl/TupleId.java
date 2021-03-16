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

package io.apicurio.registry.storage.impl;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author Ales Justin
 */
public class TupleId implements Serializable {
    private static final long serialVersionUID = 1L;

    private String groupId;
    private String id;
    private String version;
    private Long versionId;

    public TupleId(String groupId, String id, String version, Long versionId) {
        this.groupId = groupId;
        this.id = id;
        this.version = version;
        this.versionId = versionId;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public Long getVersionId() {
        return versionId;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupId, id, version, versionId);
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TupleId other = (TupleId) obj;
        return Objects.equals(groupId, other.groupId) && Objects.equals(id, other.id)
                && Objects.equals(version, other.version) && Objects.equals(versionId, other.versionId);
    }

}
