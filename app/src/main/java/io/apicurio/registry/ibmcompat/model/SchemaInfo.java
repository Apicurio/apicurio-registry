/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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
package io.apicurio.registry.ibmcompat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class SchemaInfo extends SchemaSummary {

    private List<SchemaVersion> versions = new ArrayList<SchemaVersion>();

    /**
     *
     **/

    @JsonProperty("versions")
    @NotNull
    public List<SchemaVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<SchemaVersion> versions) {
        this.versions = versions;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) return false;
        SchemaInfo schemaInfo = (SchemaInfo) o;
        return Objects.equals(versions, schemaInfo.versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), versions);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class SchemaInfo {\n");
        sb.append(super.toString());
        sb.append("    versions: ").append(toIndentedString(versions)).append("\n");
        sb.append("}");
        return sb.toString();
    }
}

