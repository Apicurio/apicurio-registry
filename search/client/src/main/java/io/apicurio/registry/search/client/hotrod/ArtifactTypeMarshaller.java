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

import io.apicurio.registry.common.proto.Cmmn;
import org.infinispan.protostream.EnumMarshaller;

/**
 * @author Ales Justin
 */
public class ArtifactTypeMarshaller implements EnumMarshaller<Cmmn.ArtifactType> {
    @Override
    public Cmmn.ArtifactType decode(int enumValue) {
        return Cmmn.ArtifactType.forNumber(enumValue);
    }

    @Override
    public int encode(Cmmn.ArtifactType artifactType) throws IllegalArgumentException {
        return artifactType.getNumber();
    }

    @Override
    public Class<? extends Cmmn.ArtifactType> getJavaClass() {
        return Cmmn.ArtifactType.class;
    }

    @Override
    public String getTypeName() {
        return "io.apicurio.registry.common.proto.ArtifactType";
    }
}
