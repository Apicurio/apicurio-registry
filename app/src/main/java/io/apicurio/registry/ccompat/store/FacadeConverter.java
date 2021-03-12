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

import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SubjectVersion;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.StoredArtifactDto;

public class FacadeConverter {

    public static int convertUnsigned(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Value out of unsigned integer range: " + value);
        }
        return (int) value;
    }

    public static Schema convert(String subject, StoredArtifactDto storedArtifact) {
        return new Schema(
                convertUnsigned(storedArtifact.getContentId()),
                subject,
                convertUnsigned(storedArtifact.getVersionId()),
                storedArtifact.getContent().content()
        );
    }

    public static SchemaContent convert(ContentHandle content) {
        return new SchemaContent(content.content());
    }

    public static SubjectVersion convert(String artifactId, Number version) {
        return new SubjectVersion(artifactId, version.longValue());
    }
}
