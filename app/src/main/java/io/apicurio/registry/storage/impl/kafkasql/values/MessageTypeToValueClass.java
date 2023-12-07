package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.exception.RuntimeAssertionFailedException;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides a mapping from a message type to the {@link MessageValue} for that message type.
 */
public class MessageTypeToValueClass {

    private static final MessageType[] types = MessageType.values();
    private static final Map<MessageType, Class<? extends MessageValue>> index = new HashMap<>();
    static {
        for (MessageType type : types) {
            switch (type) {
                case Bootstrap:
                    break;
                case Group:
                    index.put(type, GroupValue.class);
                    break;
                case Artifact:
                    index.put(type, ArtifactValue.class);
                    break;
                case ArtifactRule:
                    index.put(type, ArtifactRuleValue.class);
                    break;
                case Content:
                    index.put(type, ContentValue.class);
                    break;
                case GlobalRule:
                    index.put(type, GlobalRuleValue.class);
                    break;
                case ArtifactVersion:
                    index.put(type, ArtifactVersionValue.class);
                    break;
                case GlobalId:
                    index.put(type, GlobalIdValue.class);
                    break;
                case ContentId:
                    index.put(type, ContentIdValue.class);
                    break;
                case RoleMapping:
                    index.put(type, RoleMappingValue.class);
                    break;
                case GlobalAction:
                    index.put(type, GlobalActionValue.class);
                    break;
                case Download:
                    index.put(type, DownloadValue.class);
                    break;
                case ConfigProperty:
                    index.put(type, ConfigPropertyValue.class);
                    break;
                case ArtifactOwner:
                    index.put(type, ArtifactOwnerValue.class);
                    break;
                case CommentId:
                    index.put(type, CommentIdValue.class);
                    break;
                case Comment:
                    index.put(type, CommentValue.class);
                    break;
                case ArtifactBranch:
                    index.put(type, ArtifactBranchValue.class);
                    break;
                default:
                    throw new RuntimeAssertionFailedException("[MessageTypeToValueClass] Type not mapped: " + type);
            }
        }
    }

    public static final Class<? extends MessageValue> typeToValue(MessageType type) {
        return index.get(type);
    }

    public static final Class<? extends MessageValue> ordToValue(byte typeOrdinal) {
        MessageType type = MessageType.fromOrd(typeOrdinal);
        return typeToValue(type);
    }

}
