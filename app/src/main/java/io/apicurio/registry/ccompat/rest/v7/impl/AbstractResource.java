package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.ccompat.rest.v7.beans.SchemaReference;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import com.google.protobuf.DescriptorProtos;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import org.apache.avro.AvroTypeException;
import org.apache.avro.SchemaParseException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractResource {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rulesService;

    @Inject
    ApiConverter converter;

    @Inject
    CCompatConfig cconfig;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    RestConfig restConfig;

    protected String toSubjectWithGroupConcat(String groupId, String artifactId) {
        return (groupId == null ? "" : groupId) + cconfig.groupConcatSeparator + artifactId;
    }

    protected String toSubjectWithGroupConcat(SearchedArtifactDto dto) {
        return toSubjectWithGroupConcat(dto.getGroupId(), dto.getArtifactId());
    }

    private Pair<String, String> toGAFromGroupConcatSubject(String subject) {
        int sepIdx = subject.indexOf(cconfig.groupConcatSeparator);
        if (sepIdx < 1) {
            throw new BadRequestException("Invalid subject format.  Should be:  groupId"
                    + cconfig.groupConcatSeparator + "artifactId");
        }
        String groupId = subject.substring(0, sepIdx);
        String artifactId = subject.substring(sepIdx + cconfig.groupConcatSeparator.length());
        return Pair.of(groupId, artifactId);
    }

    protected GA getGA(String groupId, String artifactId) {
        String gid = groupId;
        String aid = artifactId;
        if (cconfig.groupConcatEnabled) {
            Pair<String, String> ga = toGAFromGroupConcatSubject(artifactId);
            gid = ga.getLeft();
            aid = ga.getRight();
        }
        return new GA(gid, aid);
    }

    protected ArtifactVersionMetaDataDto createOrUpdateArtifact(String artifactId, String schema,
                                                                String artifactType, List<SchemaReference> references, String groupId, boolean normalize) {
        ArtifactVersionMetaDataDto res;
        final List<ArtifactReferenceDto> parsedReferences = parseReferences(references, groupId);
        final List<ArtifactReference> artifactReferences = parsedReferences.stream()
                .map(dto -> ArtifactReference.builder().name(dto.getName()).groupId(dto.getGroupId())
                        .artifactId(dto.getArtifactId()).version(dto.getVersion()).build())
                .collect(Collectors.toList());
        final Map<String, TypedContent> resolvedReferences = RegistryContentUtils
                .recursivelyResolveReferences(parsedReferences, storage::getContentByReference);

        String owner = securityIdentity.getPrincipal().getName();

        try {
            ContentHandle schemaContent;
            schemaContent = ContentHandle.create(schema);
            String contentType = ContentTypes.APPLICATION_JSON;
            if (artifactType.equals(ArtifactType.PROTOBUF)) {
                contentType = ContentTypes.APPLICATION_PROTOBUF;
            }

            // Prepare content for rule application. If Protobuf, ensure it's in text format.
            TypedContent contentForRules = TypedContent.create(schemaContent, contentType);
            if (artifactType.equals(ArtifactType.PROTOBUF)) {
                try {
                    // Try parsing as text first
                    ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION, schemaContent.content());
                    // If successful, contentForRules is already correct (text format)
                } catch (Exception e) {
                    // If text parsing fails, assume it's binary Base64 encoded FileDescriptorSet
                    try {
                        byte[] decodedBytes = Base64.getDecoder().decode(schemaContent.content());
                        DescriptorProtos.FileDescriptorProto descriptorProto = DescriptorProtos.FileDescriptorProto.parseFrom(decodedBytes);
                        ProtoFileElement protoFileElement = FileDescriptorUtils.fileDescriptorToProtoFile(descriptorProto);
                        String textSchema = protoFileElement.toSchema(); // Convert binary to text
                        ContentHandle textContentHandle = ContentHandle.create(textSchema);
                        contentForRules = TypedContent.create(textContentHandle, ContentTypes.APPLICATION_PROTOBUF); // Use text for rules
                    } catch (Exception pe) {
                        // If binary parsing also fails, throw an exception
                        throw new UnprocessableEntityException(pe);
                    }
                }
            }

            if (!doesArtifactExist(artifactId, groupId)) {
                // Apply rules using the potentially converted text content
                rulesService.applyRules(groupId, artifactId, artifactType, contentForRules,
                        RuleApplicationType.CREATE, artifactReferences, resolvedReferences);

                EditableArtifactMetaDataDto artifactMetaData = EditableArtifactMetaDataDto.builder().build();
                EditableVersionMetaDataDto firstVersionMetaData = EditableVersionMetaDataDto.builder().build();
                // Store the ORIGINAL content (text or binary)
                ContentWrapperDto firstVersionContent = ContentWrapperDto.builder().content(schemaContent)
                        .contentType(contentType).references(parsedReferences).build();

                res = storage
                        .createArtifact(groupId, artifactId, artifactType, artifactMetaData, null,
                                firstVersionContent, firstVersionMetaData, null, false, false, owner)
                        .getValue();
            } else {
                // Apply rules using the potentially converted text content
                rulesService.applyRules(groupId, artifactId, artifactType, contentForRules,
                        RuleApplicationType.UPDATE, artifactReferences, resolvedReferences);
                // Store the ORIGINAL content (text or binary)
                ContentWrapperDto versionContent = ContentWrapperDto.builder().content(schemaContent)
                        .contentType(contentType).references(parsedReferences).build();
                res = storage.createArtifactVersion(groupId, artifactId, null, artifactType, versionContent,
                        EditableVersionMetaDataDto.builder().build(), List.of(), false, false, owner);
            }
        } catch (RuleViolationException ex) {
            if (ex.getRuleType() == RuleType.VALIDITY) {
                throw new UnprocessableEntityException(ex);
            } else {
                throw new ConflictException(ex);
            }
        }
        return res;
    }

    protected ArtifactVersionMetaDataDto lookupSchema(String groupId, String artifactId, String schema,
            List<SchemaReference> schemaReferences, String schemaType, boolean normalize) {
        try {
            final String type = schemaType == null ? ArtifactType.AVRO : schemaType;
            final String contentType = type.equals(ArtifactType.PROTOBUF) ? ContentTypes.APPLICATION_PROTOBUF
                : ContentTypes.APPLICATION_JSON;
            TypedContent typedSchemaContent = TypedContent.create(ContentHandle.create(schema), contentType);
            final List<ArtifactReferenceDto> artifactReferences = parseReferences(schemaReferences, groupId);
            ArtifactTypeUtilProvider artifactTypeProvider = factory.getArtifactTypeProvider(type);
            ArtifactVersionMetaDataDto amd;

            if (cconfig.canonicalHashModeEnabled.get() || normalize) {
                try {
                    amd = storage.getArtifactVersionMetaDataByContent(groupId, artifactId, true,
                            typedSchemaContent, artifactReferences);
                } catch (ArtifactNotFoundException ex) {
                    amd = avroDereferenceFallback(groupId, artifactId, schema, type,
                            artifactTypeProvider, ex);
                }
            } else {
                try {
                    amd = storage.getArtifactVersionMetaDataByContent(groupId, artifactId, false,
                            typedSchemaContent, artifactReferences);
                } catch (ArtifactNotFoundException ex) {
                    // Canonical hash fallback: when the raw hash doesn't match (e.g. due to whitespace or
                    // key ordering differences), try canonical hash comparison. This handles the case where
                    // the Confluent client re-serializes the schema before sending the lookup request.
                    // See https://github.com/Apicurio/apicurio-registry/issues/4831
                    try {
                        amd = storage.getArtifactVersionMetaDataByContent(groupId, artifactId, true,
                                typedSchemaContent, artifactReferences);
                    } catch (ArtifactNotFoundException ex2) {
                        amd = avroDereferenceFallback(groupId, artifactId, schema, type,
                                artifactTypeProvider, ex2);
                    }
                }
            }

            return amd;
        } catch (SchemaParseException | AvroTypeException ex) {
            throw new UnprocessableEntityException(ex.getMessage());
        }
    }

    /**
     * Avro dereference fallback: when comparing using content, sometimes the references might be inlined
     * into the content. Try to dereference the existing content and compare as a fallback. This approach only
     * works for Avro schemas (the only type with dereference support in the ccompat API). See
     * https://github.com/Apicurio/apicurio-registry/issues/3588 for more information.
     */
    private ArtifactVersionMetaDataDto avroDereferenceFallback(String groupId, String artifactId,
            String schema, String type, ArtifactTypeUtilProvider artifactTypeProvider,
            ArtifactNotFoundException originalException) {
        if (type.equals(ArtifactType.AVRO)) {
            return storage.getArtifactVersions(groupId, artifactId).stream().filter(version -> {
                StoredArtifactVersionDto artifactVersion = storage
                        .getArtifactVersionContent(groupId, artifactId, version);
                TypedContent typedArtifactVersion = TypedContent
                        .create(artifactVersion.getContent(), artifactVersion.getContentType());
                Map<String, TypedContent> artifactVersionReferences = RegistryContentUtils
                        .recursivelyResolveReferences(artifactVersion.getReferences(),
                                storage::getContentByReference);
                String dereferencedExistingContentSha = DigestUtils
                        .sha256Hex(artifactTypeProvider.getContentDereferencer()
                                .dereference(typedArtifactVersion, artifactVersionReferences)
                                .getContent().content());
                return dereferencedExistingContentSha.equals(DigestUtils.sha256Hex(schema));
            }).findAny().map(
                    version -> storage.getArtifactVersionMetaData(groupId, artifactId, version))
                    .orElseThrow(() -> originalException);
        } else {
            throw originalException;
        }
    }

    protected Map<String, TypedContent> resolveReferences(List<SchemaReference> references) {
        Map<String, TypedContent> resolvedReferences = Collections.emptyMap();
        if (references != null && !references.isEmpty()) {
            // Transform the given references into dtos.
            final List<ArtifactReferenceDto> referencesAsDtos = references.stream().map(schemaReference -> {
                final ArtifactReferenceDto artifactReferenceDto = new ArtifactReferenceDto();
                artifactReferenceDto.setArtifactId(schemaReference.getSubject());
                artifactReferenceDto.setVersion(String.valueOf(schemaReference.getVersion()));
                artifactReferenceDto.setName(schemaReference.getName());
                artifactReferenceDto.setGroupId(null);
                return artifactReferenceDto;
            }).collect(Collectors.toList());

            resolvedReferences = resolveReferenceDtos(referencesAsDtos);
        }

        return resolvedReferences;
    }

    protected Map<String, TypedContent> resolveReferenceDtos(List<ArtifactReferenceDto> referencesAsDtos) {
        Map<String, TypedContent> resolvedReferences = Collections.emptyMap();
        if (referencesAsDtos != null && !referencesAsDtos.isEmpty()) {
            resolvedReferences = RegistryContentUtils.recursivelyResolveReferences(referencesAsDtos,
                    storage::getContentByReference);

            if (referencesAsDtos.size() > resolvedReferences.size()) {
                // There are unresolvable references, which is not allowed.
                throw new UnprocessableEntityException("Unresolved reference");
            }
        }

        return resolvedReferences;
    }

    protected boolean isArtifactActive(String artifactId, String groupId) {
        long count = storage.countActiveArtifactVersions(groupId, artifactId);
        return count > 0;
    }

    protected String getLatestArtifactVersionForSubject(String artifactId, String groupId) {
        try {
            GAV latestGAV = storage.getBranchTip(new GA(groupId, artifactId), BranchId.LATEST,
                    RetrievalBehavior.ACTIVE_STATES);
            return latestGAV.getRawVersionId();
        } catch (ArtifactNotFoundException ex) {
            throw new VersionNotFoundException(groupId, artifactId, "latest");
        }
    }

    protected boolean shouldFilterState(boolean deleted, VersionState state) {
        if (deleted) {
            // if deleted is enabled, just return all states
            return true;
        } else {
            return state.equals(VersionState.ENABLED);
        }
    }

    protected boolean areAllSchemasDisabled(List<Long> globalIds) {
        return globalIds.stream().anyMatch(globalId -> {
            VersionState state = storage.getArtifactVersionMetaData(globalId).getState();
            return state.equals(VersionState.DISABLED);
        });
    }

    protected boolean doesArtifactExist(String artifactId, String groupId) {
        return storage.isArtifactExists(groupId, artifactId);
    }

    protected boolean doesArtifactRuleExist(String artifactId, RuleType type, String groupId) {
        try {
            storage.getArtifactRule(groupId, artifactId, type);
            return true;
        } catch (RuleNotFoundException | ArtifactNotFoundException ignored) {
            return false;
        }
    }

    protected boolean doesGlobalRuleExist(RuleType type) {
        try {
            storage.getGlobalRule(type);
            return true;
        } catch (RuleNotFoundException ignored) {
            return false;
        }
    }

    // Parse references and resolve the contentId. This will fail with ArtifactNotFound if a reference cannot
    // be found.
    protected List<ArtifactReferenceDto> parseReferences(List<SchemaReference> references, String groupId) {
        if (references != null) {
            return references.stream().map(schemaReference -> {
                // Try to get the artifact version. This will fail if not found with ArtifactNotFound or
                // VersionNotFound
                storage.getArtifactVersionMetaData(groupId, schemaReference.getSubject(),
                        String.valueOf(schemaReference.getVersion()));
                return new ArtifactReferenceDto(groupId, schemaReference.getSubject(),
                        String.valueOf(schemaReference.getVersion()), schemaReference.getName());
            }).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    protected boolean isCcompatManagedType(String artifactType) {
        return artifactType.equals(ArtifactType.AVRO) || artifactType.equals(ArtifactType.PROTOBUF)
                || artifactType.equals(ArtifactType.JSON);
    }

    /**
     * Given a version string: - if it's a <b>non-negative integer</b>, use that; - if it's a string "latest",
     * find out and use the subject's (artifact's) latest version; - if it's <b>-1</b>, do the same as
     * "latest", even though this behavior is undocumented. See
     * https://github.com/Apicurio/apicurio-registry/issues/2851 - otherwise throw an
     * IllegalArgumentException. On success, call the "then" function with the parsed version (MUST NOT be
     * null) and return it's result. Optionally provide an "else" function that will receive the exception
     * that would be otherwise thrown.
     */
    protected <T> T parseVersionString(String subject, String versionString, String groupId,
            Function<String, T> then) {
        String version;
        if ("latest".equals(versionString)) {
            version = getLatestArtifactVersionForSubject(subject, groupId);
        } else {
            try {
                var numericVersion = Integer.parseInt(versionString);
                if (numericVersion >= 0) {
                    version = versionString;
                } else if (numericVersion == -1) {
                    version = getLatestArtifactVersionForSubject(subject, groupId);
                } else {
                    throw new ArtifactNotFoundException("Illegal version format: " + versionString);
                }
            } catch (NumberFormatException e) {
                throw new VersionNotFoundException(groupId, subject, versionString);
            }
        }
        return then.apply(version);
    }
}
