package io.apicurio.registry.storage.impl.kafkasql.serde;

import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage;
import io.apicurio.registry.storage.impl.kafkasql.messages.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class KafkaSqlMessageIndex {
    
    private static Map<String, Class<? extends KafkaSqlMessage>> index = new HashMap<>();
    private static void indexMessageClass(Class<? extends KafkaSqlMessage> mclass) {
        index.put(mclass.getSimpleName(), mclass);
    }
    @SafeVarargs
    private static void indexMessageClasses(Class<? extends KafkaSqlMessage> ... mclass) {
        for (Class<? extends KafkaSqlMessage> class1 : mclass) {
            indexMessageClass(class1);
        }
    }
    static {
        indexMessageClasses(
                ConsumeDownload1Message.class,
                CreateArtifact8Message.class,
                CreateArtifactVersion7Message.class,
                CreateArtifactVersionComment4Message.class,
                CreateArtifact8Message.class,
                CreateDownload1Message.class,
                CreateGlobalRule2Message.class,
                CreateGroup1Message.class,
                CreateOrReplaceArtifactBranch3Message.class,
                CreateOrUpdateArtifactBranch2Message.class,
                CreateRoleMapping3Message.class,
                DeleteAllExpiredDownloads0Message.class,
                DeleteAllUserData0Message.class,
                DeleteArtifact2Message.class,
                DeleteArtifactBranch2Message.class,
                DeleteArtifactRule3Message.class,
                DeleteArtifactRules2Message.class,
                DeleteArtifacts1Message.class,
                DeleteArtifactVersion3Message.class,
                DeleteArtifactVersionComment4Message.class,
                DeleteConfigProperty1Message.class,
                DeleteGlobalRule1Message.class,
                DeleteGlobalRules0Message.class,
                DeleteGroup1Message.class,
                DeleteRoleMapping1Message.class,
                ImportArtifactBranch1Message.class,
                ImportArtifactRule1Message.class,
                ImportArtifactVersion1Message.class,
                ImportComment1Message.class,
                ImportContent1Message.class,
                ImportGlobalRule1Message.class,
                ImportGroup1Message.class,
                NextCommentId0Message.class,
                NextContentId0Message.class,
                NextGlobalId0Message.class,
                ResetCommentId0Message.class,
                ResetContentId0Message.class,
                ResetGlobalId0Message.class,
                SetConfigProperty1Message.class,
                UpdateArtifactMetaData3Message.class,
                UpdateArtifactRule4Message.class,
                UpdateArtifactVersionComment5Message.class,
                UpdateArtifactVersionMetaData4Message.class,
                UpdateContentCanonicalHash3Message.class,
                UpdateGlobalRule2Message.class,
                UpdateGroupMetaData2Message.class,
                UpdateRoleMapping2Message.class
        );
    }
    
    public static Class<? extends KafkaSqlMessage> lookup(String name) {
        return index.get(name);
    }

}
