package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.Collections;
import java.util.List;

/**
 * Row-level helpers for the usage tests of storages backed by SQL, including KafkaSQL, which applies its
 * journal to an SQL store.
 */
public final class UsageRows {

    private UsageRows() {
    }

    /** Creates an artifact with one version in the given group and returns that version's globalId. */
    public static long createVersion(RegistryStorage storage, String group) {
        return storage.createArtifact(group, "usage", ArtifactType.JSON, null, null,
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create("{\"type\":\"string\",\"description\":\"" + group + "\"}"))
                        .references(Collections.emptyList()).build(),
                null, Collections.emptyList(), false, false, null).getValue().getGlobalId();
    }

    /**
     * One version's rows of the client ids with the given prefix, as clientId|day|count|first|last, sorted in
     * Java because each database orders client ids by its own collation.
     */
    public static List<String> rows(HandleFactory handles, long globalId, String clientPrefix) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery("SELECT clientId, usageDay, fetchCount, firstEventTimestamp, "
                    + "lastEventTimestamp FROM schema_usage_daily WHERE globalId = ? AND clientId LIKE ?")
                    .bind(0, globalId).bind(1, clientPrefix + "%")
                    .map(rs -> rs.getString("clientId") + "|" + rs.getLong("usageDay") + "|"
                            + rs.getLong("fetchCount") + "|" + rs.getLong("firstEventTimestamp") + "|"
                            + rs.getLong("lastEventTimestamp"))
                    .list().stream().sorted().toList();
        });
    }
}
