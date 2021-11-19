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

package io.apicurio.registry.ccompat.store;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.dto.SubjectVersion;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.types.ArtifactType;

@Singleton
public class FacadeConverter {

    @Inject
    CCompatConfig cconfig;

    public int convertUnsigned(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Value out of unsigned integer range: " + value);
        }
        return (int) value;
    }

    public Schema convert(String subject, StoredArtifactDto storedArtifact) {
        return convert(subject, storedArtifact, null);
    }

    public Schema convert(String subject, StoredArtifactDto storedArtifact, ArtifactType artifactType) {
        return new Schema(
                convertUnsigned(cconfig.legacyIdModeEnabled ? storedArtifact.getGlobalId() : storedArtifact.getContentId()),
                subject,
                convertUnsigned(storedArtifact.getVersionId()),
                storedArtifact.getContent().content(),
                artifactType != null ? artifactType.value() : null
        );
    }

    public SchemaInfo convert(ContentHandle content, ArtifactType artifactType) {
        return new SchemaInfo(content.content(), artifactType.value());
    }

    public SubjectVersion convert(String artifactId, Number version) {
        return new SubjectVersion(artifactId, version.longValue());
    }
}
