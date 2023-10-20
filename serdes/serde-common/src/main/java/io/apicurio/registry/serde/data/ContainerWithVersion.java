/*
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


package io.apicurio.registry.serde.data;

public class ContainerWithVersion<U> {
    private final U container;
    private final String schemaVersion;

    public ContainerWithVersion(U container, String schemaVersion) {
        this.container = container;
        this.schemaVersion = schemaVersion;
    }

    public U container() {
        return container;
    }

    public Integer schemaVersionAsInteger() {
        return Integer.valueOf(schemaVersion);
    }

    public String schemaVersion() {
        return schemaVersion;
    }


}
