package io.apicurio.registry.promotion;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.rest.ParameterValidationUtils;
import io.apicurio.registry.rest.v3.beans.PromoteArtifact;
import io.apicurio.registry.rest.v3.beans.PromotionCompareResult;
import io.apicurio.registry.rest.v3.beans.PromotionCompatibility;
import io.apicurio.registry.rest.v3.beans.PromotionCompatibilityDifference;
import io.apicurio.registry.rest.v3.beans.PromotionCoordinate;
import io.apicurio.registry.rest.v3.beans.PromotionDiffLine;
import io.apicurio.registry.rest.v3.beans.PromotionResult;
import io.apicurio.registry.rest.v3.beans.PromotionSource;
import io.apicurio.registry.rest.v3.impl.V3ApiUtil;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 1 of cross-registry promotion: fetch a version from a configured source, compare it with the local
 * target, and copy it as an explicit promote.
 */
@ApplicationScoped
public class CrossRegistryPromotionService {

    static final String LABEL_SOURCE = "apicurio.promotion.source";
    static final String LABEL_SOURCE_GROUP = "apicurio.promotion.sourceGroup";
    static final String LABEL_SOURCE_ARTIFACT = "apicurio.promotion.sourceArtifact";
    static final String LABEL_SOURCE_VERSION = "apicurio.promotion.sourceVersion";

    @Inject
    PromotionConfig config;

    @Inject
    PromotionSourceClientFactory clients;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ArtifactTypeUtilProviderFactory artifactTypes;

    @Inject
    RulesService rulesService;

    @Inject
    SecurityIdentity securityIdentity;

    public List<PromotionSource> listSources() {
        List<PromotionSource> result = new ArrayList<>();
        for (PromotionSourceDefinition source : config.listSources()) {
            PromotionSource bean = new PromotionSource();
            bean.setName(source.name());
            bean.setUrl(source.url());
            bean.setAuth(source.auth());
            result.add(bean);
        }
        return result;
    }

    public PromotionCompareResult compare(String targetGroupId, String targetArtifactId, PromoteArtifact request) {
        requireEnabled();
        ParameterValidationUtils.requireParameter("groupId", targetGroupId);
        ParameterValidationUtils.requireParameter("artifactId", targetArtifactId);
        ParameterValidationUtils.requireParameter("body.source", request.getSource());

        RemoteArtifactVersion remote = fetchSource(targetGroupId, targetArtifactId, request);
        Optional<LocalVersion> local = latestTarget(targetGroupId, targetArtifactId);
        return buildCompare(remote, local, compatibilityLevel(request));
    }

    public PromotionResult promote(String targetGroupId, String targetArtifactId, Boolean dryRun,
            PromoteArtifact request) {
        requireEnabled();
        ParameterValidationUtils.requireParameter("groupId", targetGroupId);
        ParameterValidationUtils.requireParameter("artifactId", targetArtifactId);
        ParameterValidationUtils.requireParameter("body.source", request.getSource());

        String gid = new GroupId(targetGroupId).getRawGroupIdWithNull();
        RemoteArtifactVersion remote = fetchSource(targetGroupId, targetArtifactId, request);
        if (remote.artifactType() == null || remote.content() == null) {
            throw new PromotionRemoteException("Source version is missing artifactType or content");
        }
        Optional<LocalVersion> local = latestTarget(targetGroupId, targetArtifactId);
        CompatibilityLevel level = compatibilityLevel(request);
        PromotionCompareResult compare = buildCompare(remote, local, level);

        if (Boolean.TRUE.equals(compare.getIdentical()) && local.isPresent()) {
            PromotionResult result = new PromotionResult();
            result.setAlreadyPromoted(true);
            result.setCompare(compare);
            result.setVersion(V3ApiUtil.dtoToVersionMetaData(storage.getArtifactVersionMetaData(gid,
                    targetArtifactId, local.get().version())));
            return result;
        }

        boolean isDryRun = dryRun != null && dryRun;
        String owner = securityIdentity.getPrincipal().getName();
        ContentHandle content = ContentHandle.create(remote.content());
        ContentWrapperDto contentDto = ContentWrapperDto.builder().content(content)
                .contentType(remote.contentType()).references(Collections.emptyList()).build();
        Map<String, String> labels = promotionLabels(request.getSource(), remote);
        EditableVersionMetaDataDto versionMeta = EditableVersionMetaDataDto.builder().name(remote.name())
                .description(remote.description()).labels(labels).build();
        String version = Boolean.TRUE.equals(request.getPreserveVersion()) ? remote.version() : null;
        TypedContent typedContent = TypedContent.create(content, remote.contentType());

        boolean exists = storage.isArtifactExists(gid, targetArtifactId);
        rulesService.applyRules(gid, targetArtifactId, remote.artifactType(), typedContent,
                exists ? RuleApplicationType.UPDATE : RuleApplicationType.CREATE, Collections.emptyList(),
                Map.of());

        io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto created;
        if (!exists) {
            EditableArtifactMetaDataDto artifactMeta = EditableArtifactMetaDataDto.builder()
                    .name(remote.name()).description(remote.description()).labels(labels).build();
            Pair<io.apicurio.registry.storage.dto.ArtifactMetaDataDto, io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto> pair = storage
                    .createArtifact(gid, targetArtifactId, remote.artifactType(), artifactMeta, version,
                            contentDto, versionMeta, Collections.emptyList(), false, isDryRun, owner);
            created = pair.getRight();
        } else {
            created = storage.createArtifactVersion(gid, targetArtifactId, version, remote.artifactType(),
                    contentDto, versionMeta, Collections.emptyList(), false, isDryRun, owner);
        }

        PromotionResult result = new PromotionResult();
        result.setAlreadyPromoted(false);
        result.setCompare(compare);
        result.setVersion(V3ApiUtil.dtoToVersionMetaData(created));
        return result;
    }

    private void requireEnabled() {
        if (!config.isEnabled()) {
            throw new BadRequestException("Cross-registry promotion is disabled");
        }
    }

    private RemoteArtifactVersion fetchSource(String targetGroupId, String targetArtifactId,
            PromoteArtifact request) {
        String sourceGroup = request.getSourceGroupId() != null ? request.getSourceGroupId() : targetGroupId;
        String sourceArtifact = request.getSourceArtifactId() != null ? request.getSourceArtifactId()
                : targetArtifactId;
        String sourceVersion = request.getSourceVersion() != null && !request.getSourceVersion().isBlank()
                ? request.getSourceVersion()
                : "branch=latest";
        return clients.client(request.getSource()).fetch(sourceGroup, sourceArtifact, sourceVersion);
    }

    private Optional<LocalVersion> latestTarget(String groupId, String artifactId) {
        String gid = new GroupId(groupId).getRawGroupIdWithNull();
        if (!storage.isArtifactExists(gid, artifactId)) {
            return Optional.empty();
        }
        try {
            var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), "branch=latest",
                    (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));
            StoredArtifactVersionDto content = storage.getArtifactVersionContent(gav.getRawGroupIdWithNull(),
                    gav.getRawArtifactId(), gav.getRawVersionId());
            var meta = storage.getArtifactVersionMetaData(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                    gav.getRawVersionId());
            return Optional.of(new LocalVersion(gav.getRawGroupIdWithDefaultString(), artifactId,
                    meta.getVersion(), meta.getArtifactType(), content.getContentType(),
                    content.getContent().content()));
        } catch (VersionNotFoundException e) {
            return Optional.empty();
        }
    }

    private PromotionCompareResult buildCompare(RemoteArtifactVersion remote, Optional<LocalVersion> local,
            CompatibilityLevel level) {
        String sourceCanonical = canonicalize(remote.artifactType(), remote.contentType(), remote.content());
        String targetCanonical = local
                .map(v -> canonicalize(v.artifactType(), v.contentType(), v.content()))
                .orElse("");
        boolean identical = local.isPresent() && sourceCanonical.equals(targetCanonical);

        PromotionCompareResult result = new PromotionCompareResult();
        result.setSource(coordinate(remote.groupId(), remote.artifactId(), remote.version(),
                remote.artifactType()));
        result.setTarget(local.map(v -> coordinate(v.groupId(), v.artifactId(), v.version(), v.artifactType()))
                .orElse(null));
        result.setIdentical(identical);
        result.setContentDiff(toDiffLines(ContentLineDiff.diff(targetCanonical, sourceCanonical)));
        result.setCompatibility(checkCompatibility(remote, local, level));
        return result;
    }

    private PromotionCompatibility checkCompatibility(RemoteArtifactVersion remote, Optional<LocalVersion> local,
            CompatibilityLevel level) {
        PromotionCompatibility compatibility = new PromotionCompatibility();
        compatibility.setLevel(level.name());
        if (level == CompatibilityLevel.NONE || local.isEmpty()) {
            compatibility.setCompatible(true);
            compatibility.setDifferences(List.of());
            return compatibility;
        }
        ArtifactTypeUtilProvider provider = artifactTypes.getArtifactTypeProvider(remote.artifactType());
        TypedContent proposed = TypedContent.create(remote.content(), remote.contentType());
        TypedContent existing = TypedContent.create(local.get().content(), local.get().contentType());
        CompatibilityExecutionResult execution = provider.getCompatibilityChecker().testCompatibility(level,
                List.of(existing), proposed, Map.of());
        compatibility.setCompatible(execution.isCompatible());
        List<PromotionCompatibilityDifference> diffs = new ArrayList<>();
        if (execution.getIncompatibleDifferences() != null) {
            for (var difference : execution.getIncompatibleDifferences()) {
                var violation = difference.asRuleViolation();
                PromotionCompatibilityDifference item = new PromotionCompatibilityDifference();
                item.setDescription(violation.getDescription());
                item.setContext(violation.getContext());
                diffs.add(item);
            }
        }
        compatibility.setDifferences(diffs);
        return compatibility;
    }

    private String canonicalize(String artifactType, String contentType, String content) {
        if (artifactType == null || content == null) {
            return content == null ? "" : content;
        }
        try {
            ArtifactTypeUtilProvider provider = artifactTypes.getArtifactTypeProvider(artifactType);
            TypedContent canonical = provider.getContentCanonicalizer()
                    .canonicalize(TypedContent.create(content, contentType), Map.of());
            return canonical.getContent().content();
        } catch (RuntimeException e) {
            return content;
        }
    }

    private CompatibilityLevel compatibilityLevel(PromoteArtifact request) {
        if (request.getCompatibilityLevel() == null) {
            return CompatibilityLevel.BACKWARD;
        }
        String raw = request.getCompatibilityLevel().name();
        try {
            return CompatibilityLevel.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown compatibilityLevel: " + raw);
        }
    }

    private static List<PromotionDiffLine> toDiffLines(List<ContentLineDiff.Line> lines) {
        List<PromotionDiffLine> result = new ArrayList<>();
        for (ContentLineDiff.Line line : lines) {
            PromotionDiffLine bean = new PromotionDiffLine();
            bean.setOp(PromotionDiffLine.Op.fromValue(line.op()));
            bean.setText(line.text());
            result.add(bean);
        }
        return result;
    }

    private static PromotionCoordinate coordinate(String groupId, String artifactId, String version,
            String artifactType) {
        PromotionCoordinate coordinate = new PromotionCoordinate();
        coordinate.setGroupId(groupId);
        coordinate.setArtifactId(artifactId);
        coordinate.setVersion(version);
        coordinate.setArtifactType(artifactType);
        return coordinate;
    }

    private static Map<String, String> promotionLabels(String sourceName, RemoteArtifactVersion remote) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(LABEL_SOURCE, sourceName);
        labels.put(LABEL_SOURCE_GROUP, remote.groupId());
        labels.put(LABEL_SOURCE_ARTIFACT, remote.artifactId());
        labels.put(LABEL_SOURCE_VERSION, remote.version());
        return labels;
    }

    private record LocalVersion(String groupId, String artifactId, String version, String artifactType,
            String contentType, String content) {
    }
}
