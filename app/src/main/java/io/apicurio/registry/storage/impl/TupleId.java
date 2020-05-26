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

    private String id;
    private Long version;

    public TupleId(String id, Long version) {
        this.id = id;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TupleId tupleId = (TupleId) o;
        return id.equals(tupleId.id) &&
                version.equals(tupleId.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
}
