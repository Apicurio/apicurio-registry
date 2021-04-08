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

    private static final Map<ArtifactType, Byte> atToOrd = new HashMap<>();
    private static final Map<Byte, ArtifactType> ordToAt = new HashMap<>();
    static {
        for (ArtifactType artifactType : ArtifactType.values()) {
            // Note:  the order of this list is important.  If the ArtifactType enum changes
            // we need to update this switch.  But make sure to *NOT* change the ordinal values
            // of any of the old types.
            switch (artifactType) {
                case ASYNCAPI:
                    index(artifactType, 1);
                    break;
                case AVRO:
                    index(artifactType, 2);
                    break;
                case GRAPHQL:
                    index(artifactType, 3);
                    break;
                case JSON:
                    index(artifactType, 4);
                    break;
                case KCONNECT:
                    index(artifactType, 5);
                    break;
                case OPENAPI:
                    index(artifactType, 6);
                    break;
                case PROTOBUF:
                    index(artifactType, 7);
                    break;
//                case PROTOBUF_FD:
//                    index(artifactType, 8);
//                    break;
                case WSDL:
                    index(artifactType, 9);
                    break;
                case XML:
                    index(artifactType, 10);
                    break;
                case XSD:
                    index(artifactType, 11);
                    break;
                default:
                    break;

            }
        }
    }

    public static byte artifactTypeToOrd(ArtifactType artifactType) {
        if (artifactType == null) {
            return 0;
        }
        return atToOrd.get(artifactType);
    }

    public static ArtifactType ordToArtifactType(byte ord) {
        if (ord == 0) {
            return null;
        }
        return ordToAt.get(ord);
    }

    private static void index(ArtifactType artifactType, int ord) {
        ordToAt.put((byte) ord, artifactType);
        atToOrd.put(artifactType, (byte) ord);
    }

}
