package io.apicurio.registry.storage.impl.kafkasql.serde;

import java.util.HashMap;
import java.util.Map;

import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage;
import io.apicurio.registry.storage.impl.kafkasql.messages.ConsumeDownload1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateArtifact6Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateArtifactVersion6Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateArtifactVersionComment4Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateArtifactVersionWithMetadata7Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateArtifactWithMetadata7Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateDownload1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateGlobalRule2Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateGroup1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateOrReplaceArtifactBranch3Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateOrUpdateArtifactBranch2Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateRoleMapping3Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteAllExpiredDownloads0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteAllUserData0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteArtifact2Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteArtifactBranch2Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteArtifactRule3Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteArtifactRules2Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteArtifactVersion3Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteArtifactVersionComment4Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteArtifacts1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteConfigProperty1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteGlobalRule1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteGlobalRules0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteGroup1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteRoleMapping1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.ImportArtifactBranch1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.ImportArtifactRule1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.ImportArtifactVersion1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.ImportComment1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.ImportContent1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.ImportGlobalRule1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.ImportGroup1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.NextCommentId0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.NextContentId0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.NextGlobalId0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.ResetCommentId0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.ResetContentId0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.ResetGlobalId0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.SetConfigProperty1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdateArtifactMetaData3Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdateArtifactRule4Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdateArtifactVersionComment5Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdateArtifactVersionMetaData4Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdateContentCanonicalHash3Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdateGlobalRule2Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdateGroupMetaData2Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdateRoleMapping2Message;
import jakarta.enterprise.context.ApplicationScoped;

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
                CreateArtifact6Message.class,
                CreateArtifactVersion6Message.class,
                CreateArtifactVersionComment4Message.class,
                CreateArtifactVersionWithMetadata7Message.class,
                CreateArtifactWithMetadata7Message.class,
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
