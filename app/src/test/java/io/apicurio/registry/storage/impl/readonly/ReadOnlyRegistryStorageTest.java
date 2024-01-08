package io.apicurio.registry.storage.impl.readonly;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.Functional.Runnable1Ex;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
public class ReadOnlyRegistryStorageTest {

    @Inject
    @Current
    RegistryStorage storage;

    private static final Map<String, State> EXPECTED_METHODS;
    private static final Set<String> CURRENT_METHODS;


    static {
        EXPECTED_METHODS = Map.<String, State>ofEntries(
                // Keep alphabetical
                entry("consumeDownload1", new State(true, s -> s.consumeDownload(null))),
                entry("contentIdFromHash1", new State(false, s -> s.contentIdFromHash(null))),
                entry("countArtifacts0", new State(false, RegistryStorage::countArtifacts)),
                entry("countArtifactVersions2", new State(false, s -> s.countArtifactVersions(null, null))),
                entry("countTotalArtifactVersions0", new State(false, RegistryStorage::countTotalArtifactVersions)),
                entry("createArtifact6", new State(true, s -> s.createArtifact(null, null, null, null, null, null))),
                entry("createArtifactRule4", new State(true, s -> s.createArtifactRule(null, null, null, null))),
                entry("createArtifactVersionComment4", new State(true, s -> s.createArtifactVersionComment(null, null, null, null))),
                entry("createArtifactVersionCommentRaw7", new State(true, s -> s.createArtifactVersionCommentRaw(null, null, null, null, null, null, null))),
                entry("createArtifactWithMetadata7", new State(true, s -> s.createArtifactWithMetadata(null, null, null, null, null, null, null))),
                entry("createArtifactWithMetadata9", new State(true, s -> s.createArtifactWithMetadata(null, null, null, null, null, null, null, null, null))),
                entry("createDownload1", new State(true, s -> s.createDownload(null))),
                entry("createGlobalRule2", new State(true, s -> s.createGlobalRule(null, null))),
                entry("createGroup1", new State(true, s -> s.createGroup(null))),
                entry("createOrUpdateArtifactBranch2", new State(true, s -> s.createOrUpdateArtifactBranch(null, null))),
                entry("createRoleMapping3", new State(true, s -> s.createRoleMapping(null, null, null))),
                entry("deleteAllExpiredDownloads0", new State(true, RegistryStorage::deleteAllExpiredDownloads)),
                entry("deleteAllUserData0", new State(true, RegistryStorage::deleteAllUserData)),
                entry("deleteArtifact2", new State(true, s -> s.deleteArtifact(null, null))),
                entry("deleteArtifactBranch2", new State(true, s -> s.deleteArtifactBranch(null, null))),
                entry("deleteArtifactRule3", new State(true, s -> s.deleteArtifactRule(null, null, null))),
                entry("deleteArtifactRules2", new State(true, s -> s.deleteArtifactRules(null, null))),
                entry("deleteArtifacts1", new State(true, s -> s.deleteArtifacts(null))),
                entry("deleteArtifactVersion3", new State(true, s -> s.deleteArtifactVersion(null, null, null))),
                entry("deleteArtifactVersionComment4", new State(true, s -> s.deleteArtifactVersionComment(null, null, null, null))),
                entry("deleteArtifactVersionMetaData3", new State(true, s -> s.deleteArtifactVersionMetaData(null, null, null))),
                entry("deleteConfigProperty1", new State(true, s -> s.deleteConfigProperty("test"))),
                entry("deleteGlobalRule1", new State(true, s -> s.deleteGlobalRule(null))),
                entry("deleteGlobalRules0", new State(true, RegistryStorage::deleteGlobalRules)),
                entry("deleteGroup1", new State(true, s -> s.deleteGroup(null))),
                entry("deleteRoleMapping1", new State(true, s -> s.deleteRoleMapping(null))),
                entry("exportData1", new State(false, s -> s.exportData(null))),
                entry("getArtifact2", new State(false, s -> s.getArtifact(null, null))),
                entry("getArtifact3", new State(false, s -> s.getArtifact(null, null, null))),
                entry("getArtifactByContentHash1", new State(false, s -> s.getArtifactByContentHash(null))),
                entry("getArtifactByContentId1", new State(false, s -> s.getArtifactByContentId(0))),
                entry("getArtifactBranch3", new State(false, s -> s.getArtifactBranch(null, null, null))),
                entry("getArtifactBranches1", new State(false, s -> s.getArtifactBranches(null))),
                entry("getArtifactBranchLeaf3", new State(false, s -> s.getArtifactBranchLeaf(null, null, null))),
                entry("getArtifactContentIds2", new State(false, s -> s.getArtifactContentIds(null, null))),
                entry("getArtifactIds1", new State(false, s -> s.getArtifactIds(null))),
                entry("getArtifactMetaData1", new State(false, s -> s.getArtifactMetaData(0))),
                entry("getArtifactMetaData2", new State(false, s -> s.getArtifactMetaData(null, null))),
                entry("getArtifactMetaData3", new State(false, s -> s.getArtifactMetaData(null, null, null))),
                entry("getArtifactRule3", new State(false, s -> s.getArtifactRule(null, null, null))),
                entry("getArtifactRules2", new State(false, s -> s.getArtifactRules(null, null))),
                entry("getArtifactVersion1", new State(false, s -> s.getArtifactVersion(0))),
                entry("getArtifactVersion3", new State(false, s -> s.getArtifactVersion(null, null, null))),
                entry("getArtifactVersionComments3", new State(false, s -> s.getArtifactVersionComments(null, null, null))),
                entry("getArtifactVersionMetaData3", new State(false, s -> s.getArtifactVersionMetaData(null, null, null))),
                entry("getArtifactVersionMetaData5", new State(false, s -> s.getArtifactVersionMetaData(null, null, false, null, null))),
                entry("getArtifactVersions2", new State(false, s -> s.getArtifactVersions(null, null))),
                entry("getArtifactVersions3", new State(false, s -> s.getArtifactVersions(null, null, RegistryStorage.ArtifactRetrievalBehavior.DEFAULT))),
                entry("getEnabledArtifactContentIds2", new State(false, s -> s.getEnabledArtifactContentIds(null, null))),
                entry("getArtifactVersionsByContentId1", new State(false, s -> s.getArtifactVersionsByContentId(0))),
                entry("getConfigProperties0", new State(false, DynamicConfigStorage::getConfigProperties)),
                entry("getConfigProperty1", new State(false, s -> s.getConfigProperty(null))),
                entry("getContentIdsReferencingArtifact3", new State(false, s -> s.getContentIdsReferencingArtifact(null, null, null))),
                entry("getGlobalIdsReferencingArtifact3", new State(false, s -> s.getGlobalIdsReferencingArtifact(null, null, null))),
                entry("getGlobalRule1", new State(false, s -> s.getGlobalRule(null))),
                entry("getGlobalRules0", new State(false, RegistryStorage::getGlobalRules)),
                entry("getGroupIds1", new State(false, s -> s.getGroupIds(null))),
                entry("getGroupMetaData1", new State(false, s -> s.getGroupMetaData(null))),
                entry("getInboundArtifactReferences3", new State(false, s -> s.getInboundArtifactReferences(null, null, null))),
                entry("getRawConfigProperty1", new State(false, s -> s.getRawConfigProperty(null))),
                entry("getRoleForPrincipal1", new State(false, s -> s.getRoleForPrincipal(null))),
                entry("getRoleMapping1", new State(false, s -> s.getRoleMapping(null))),
                entry("getRoleMappings0", new State(false, RegistryStorage::getRoleMappings)),
                entry("getStaleConfigProperties1", new State(false, s -> s.getStaleConfigProperties(null))),
                entry("importArtifactBranch1", new State(true, s -> s.importArtifactBranch(null))),
                entry("importArtifactRule1", new State(true, s -> s.importArtifactRule(null))),
                entry("importArtifactVersion1", new State(true, s -> s.importArtifactVersion(null))),
                entry("importComment1", new State(true, s -> s.importComment(null))),
                entry("importContent1", new State(true, s -> s.importContent(null))),
                entry("importData3", new State(true, s -> s.importData(null, false, false))),
                entry("importGlobalRule1", new State(true, s -> s.importGlobalRule(null))),
                entry("importGroup1", new State(true, s -> s.importGroup(null))),
                entry("initialize0", new State(false, RegistryStorage::initialize)),
                entry("isAlive0", new State(false, RegistryStorage::isAlive)),
                entry("isArtifactExists2", new State(false, s -> s.isArtifactExists(null, null))),
                entry("isArtifactRuleExists3", new State(false, s -> s.isArtifactRuleExists(null, null, null))),
                entry("isArtifactVersionExists3", new State(false, s -> s.isArtifactVersionExists(null, null, null))),
                entry("isContentExists1", new State(false, s -> s.isContentExists(null))),
                entry("isGlobalRuleExists1", new State(false, s -> s.isGlobalRuleExists(null))),
                entry("isGroupExists1", new State(false, s -> s.isGroupExists(null))),
                entry("isReadOnly0", new State(false, RegistryStorage::isReadOnly)),
                entry("isReady0", new State(false, RegistryStorage::isReady)),
                entry("isRoleMappingExists1", new State(false, s -> s.isRoleMappingExists(null))),
                entry("nextCommentId0", new State(true, RegistryStorage::nextCommentId)),
                entry("nextContentId0", new State(true, RegistryStorage::nextContentId)),
                entry("nextGlobalId0", new State(true, RegistryStorage::nextGlobalId)),
                entry("resetCommentId0", new State(true, RegistryStorage::resetCommentId)),
                entry("resetContentId0", new State(true, RegistryStorage::resetContentId)),
                entry("resetGlobalId0", new State(true, RegistryStorage::resetGlobalId)),
                entry("resolveReferences1", new State(false, s -> s.resolveReferences(null))),
                entry("searchArtifacts5", new State(false, s -> s.searchArtifacts(null, null, null, 0, 0))),
                entry("searchGroups5", new State(false, s -> s.searchGroups(null, null, null, null, null))),
                entry("searchVersions4", new State(false, s -> s.searchVersions(null, null, 0, 0))),
                entry("setConfigProperty1", new State(true, s -> {
                    var dto = new DynamicConfigPropertyDto();
                    dto.setName("test");
                    s.setConfigProperty(dto);
                })),
                entry("storageName0", new State(false, RegistryStorage::storageName)),
                entry("updateArtifact6", new State(true, s -> s.updateArtifact(null, null, null, null, null, null))),
                entry("updateArtifactMetaData3", new State(true, s -> s.updateArtifactMetaData(null, null, null))),
                entry("updateArtifactOwner3", new State(true, s -> s.updateArtifactOwner(null, null, null))),
                entry("updateArtifactRule4", new State(true, s -> s.updateArtifactRule(null, null, null, null))),
                entry("updateArtifactState3", new State(true, s -> s.updateArtifactState(null, null, null))),
                entry("updateArtifactState4", new State(true, s -> s.updateArtifactState(null, null, null, null))),
                entry("updateArtifactVersionComment5", new State(true, s -> s.updateArtifactVersionComment(null, null, null, null, null))),
                entry("updateArtifactVersionMetaData4", new State(true, s -> s.updateArtifactVersionMetaData(null, null, null, null))),
                entry("updateArtifactWithMetadata7", new State(true, s -> s.updateArtifactWithMetadata(null, null, null, null, null, null, null))),
                entry("updateArtifactWithMetadata9", new State(true, s -> s.updateArtifactWithMetadata(null, null, null, null, null, null, null, null, null))),
                entry("updateContentCanonicalHash3", new State(true, s -> s.updateContentCanonicalHash(null, 0, null))),
                entry("updateGlobalRule2", new State(true, s -> s.updateGlobalRule(null, null))),
                entry("updateGroupMetaData1", new State(true, s -> s.updateGroupMetaData(null))),
                entry("updateRoleMapping2", new State(true, s -> s.updateRoleMapping(null, null)))
        );

        CURRENT_METHODS = Arrays.stream(RegistryStorage.class.getMethods())
                .map(m -> m.getName() + m.getParameterCount())
                .collect(Collectors.toSet());
    }


    @Test
    void readOnlyTest() {
        notEnabled();
        var dto = new DynamicConfigPropertyDto();
        dto.setName("registry.storage.read-only");
        dto.setValue("true");
        storage.setConfigProperty(dto);
        enabled();
        dto.setValue("false");
        storage.setConfigProperty(dto);
        notEnabled();
    }


    private void notEnabled() {
        for (String method : CURRENT_METHODS) {
            State state = EXPECTED_METHODS.get(method);
            assertNotNull(state, "Method " + method + " in RegistryStorage interface is not covered by this test.");
            try {
                state.runnable.run(storage);
            } catch (Exception ex) {
                if (ex instanceof ReadOnlyStorageException) {
                    Assertions.fail("Unexpected ReadOnlyStorageException for method " + method + " (read-only is not enabled).", ex);
                }
            } finally {
                state.executed = true;
            }
        }
        reset();
    }


    private void enabled() {
        for (String method : CURRENT_METHODS) {
            State state = EXPECTED_METHODS.get(method);
            assertNotNull(state, "Method " + method + " in RegistryStorage interface is not covered by this test.");
            try {
                state.runnable.run(storage);
                if (state.writes) {
                    Assertions.fail("Expected ReadOnlyStorageException for method " + method + " (read-only is enabled).");
                }
            } catch (Exception ex) {
                if (ex instanceof ReadOnlyStorageException) {
                    if (!state.writes) {
                        Assertions.fail("Unexpected ReadOnlyStorageException for method " + method + " (read-only is enabled).", ex);
                    }
                } else {
                    if (state.writes) {
                        Assertions.fail("Expected ReadOnlyStorageException for method " + method + " (read-only is enabled).");
                    }
                }
            } finally {
                state.executed = true;
            }
        }
        reset();
    }


    private void reset() {
        var notExecuted = EXPECTED_METHODS.entrySet().stream()
                .filter(e -> !e.getValue().executed)
                .collect(Collectors.toSet());
        Assertions.assertEquals(Set.of(), notExecuted, "Some method(s) expected to be in the RegistryStorage interface " +
                "by this test are missing.");
        EXPECTED_METHODS.forEach((key, value) -> value.executed = false);
    }


    private static class State {

        private boolean writes;
        private Runnable1Ex<RegistryStorage, Exception> runnable;

        private boolean executed;

        public State(boolean writes, Runnable1Ex<RegistryStorage, Exception> runnable) {
            this.writes = writes;
            this.runnable = runnable;
        }
    }
}
