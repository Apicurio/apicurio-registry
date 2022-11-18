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

package io.apicurio.registry.storage.impl.kafkasql.serde;

import java.util.HashMap;
import java.util.Map;

import io.apicurio.registry.types.ArtifactType;

/**
 * Used to get an "ord" value from an ArtifactType.
 * @author eric.wittmann@gmail.com
 */
public class ArtifactTypeOrdUtil {

    private static final Map<String, Byte> atToOrd = new HashMap<>();
    private static final Map<Byte, String> ordToAt = new HashMap<>();
    static {
        index(ArtifactType.ASYNCAPI, 1);
        index(ArtifactType.AVRO, 2);
        index(ArtifactType.GRAPHQL, 3);
        index(ArtifactType.JSON, 4);
        index(ArtifactType.KCONNECT, 5);
        index(ArtifactType.OPENAPI, 6);
        index(ArtifactType.PROTOBUF, 7);
        index(ArtifactType.WSDL, 9);
        index(ArtifactType.XML, 10);
        index(ArtifactType.XSD, 11);
    }

    public static byte artifactTypeToOrd(String artifactType) {
        if (artifactType == null) {
            return 0;
        }
        return atToOrd.get(artifactType);
    }

    public static String ordToArtifactType(byte ord) {
        if (ord == 0) {
            return null;
        }
        return ordToAt.get(ord);
    }

    private static void index(String artifactType, int ord) {
        ordToAt.put((byte) ord, artifactType);
        atToOrd.put(artifactType, (byte) ord);
    }

}
