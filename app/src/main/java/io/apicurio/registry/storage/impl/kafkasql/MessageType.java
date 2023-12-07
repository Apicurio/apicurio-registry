package io.apicurio.registry.storage.impl.kafkasql;

import java.util.HashMap;
import java.util.Map;

public enum MessageType {

    Bootstrap(0),
    GlobalRule(1),
    Content(2),
    Artifact(3),
    ArtifactRule(4),
    ArtifactVersion(5),
    Group(6),
//    LogConfig(7),
    GlobalId(8),
    ContentId(9),
    RoleMapping(10),
    GlobalAction(11),
    Download(12),
    ConfigProperty(13),
    ArtifactOwner(14),
    CommentId(15),
    Comment(16),
    ;

    private final byte ord;

    /**
     * Constructor.
     */
    private MessageType(int ord) {
        this.ord = (byte) ord;
    }

    public final byte getOrd() {
        return this.ord;
    }

    private static final Map<Byte, MessageType> ordIndex = new HashMap<>();
    static {
        for (MessageType mt : MessageType.values()) {
            if (ordIndex.containsKey(mt.getOrd())) {
                throw new IllegalArgumentException(String.format("Duplicate ord value %d for MessageType %s", mt.getOrd(), mt.name()));
            }
            ordIndex.put(mt.getOrd(), mt);
        }
    }
    public static final MessageType fromOrd(byte ord) {
        return ordIndex.get(ord);
    }

}
