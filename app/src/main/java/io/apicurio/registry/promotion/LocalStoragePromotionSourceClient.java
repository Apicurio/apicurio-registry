package io.apicurio.registry.promotion;

import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;

/**
 * Treats this registry instance as the promotion source (same database, typically a different group).
 */
public class LocalStoragePromotionSourceClient implements PromotionSourceClient {

    private final RegistryStorage storage;

    public LocalStoragePromotionSourceClient(RegistryStorage storage) {
        this.storage = storage;
    }

    @Override
    public RemoteArtifactVersion fetch(String groupId, String artifactId, String versionExpression) {
        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));
        String rawGroup = gav.getRawGroupIdWithNull();
        ArtifactVersionMetaDataDto meta = storage.getArtifactVersionMetaData(rawGroup, gav.getRawArtifactId(),
                gav.getRawVersionId());
        StoredArtifactVersionDto content = storage.getArtifactVersionContent(rawGroup, gav.getRawArtifactId(),
                gav.getRawVersionId());
        String publicGroup = new GroupId(rawGroup).getRawGroupIdWithDefaultString();
        return new RemoteArtifactVersion(publicGroup, meta.getArtifactId(), meta.getVersion(),
                meta.getArtifactType(), content.getContentType(), content.getContent().content(),
                meta.getName(), meta.getDescription());
    }
}
