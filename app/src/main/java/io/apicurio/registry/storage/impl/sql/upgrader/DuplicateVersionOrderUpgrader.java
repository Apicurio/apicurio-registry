package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Runs before UQ_versions_3 (groupId, artifactId, versionOrder) is added and fails the upgrade if
 * existing rows would violate it. A registry that lost the concurrent first-version race can hold
 * such rows. It changes no data: renumbering would change the version numbers ccompat clients see,
 * and deleting would lose versions, so the operator decides which row to fix.
 */
@RegisterForReflection
public class DuplicateVersionOrderUpgrader implements IDbUpgrader {

    private static final String SELECT_DUPLICATES = "SELECT v.groupId, v.artifactId, v.version, v.versionOrder"
            + " FROM versions v JOIN (SELECT groupId, artifactId, versionOrder FROM versions"
            + " GROUP BY groupId, artifactId, versionOrder HAVING COUNT(*) > 1) d"
            + " ON v.groupId = d.groupId AND v.artifactId = d.artifactId AND v.versionOrder = d.versionOrder"
            + " ORDER BY v.groupId, v.artifactId, v.versionOrder, v.version";

    @Override
    public void upgrade(Handle handle) {
        List<String> duplicates = handle.createQuery(SELECT_DUPLICATES)
                .map(rs -> "groupId=" + rs.getString("groupId") + ", artifactId=" + rs.getString("artifactId")
                        + ", version=" + rs.getString("version") + ", versionOrder=" + rs.getInt("versionOrder"))
                .list();
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Cannot add the UQ_versions_3 constraint: " + duplicates.size()
                    + " versions share a versionOrder with another version of the same artifact."
                    + " Give each of them a distinct versionOrder or delete the extra ones, then restart."
                    + "\n  " + String.join("\n  ", duplicates));
        }
    }
}
