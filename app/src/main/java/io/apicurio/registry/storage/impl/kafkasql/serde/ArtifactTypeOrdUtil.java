package io.apicurio.registry.storage.impl.kafkasql.serde;

import io.apicurio.registry.types.ArtifactType;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to get an "ord" value from an ArtifactType.
 */
public class ArtifactTypeOrdUtil {
// TODO: Make this a responsibility of ArtifactType

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
