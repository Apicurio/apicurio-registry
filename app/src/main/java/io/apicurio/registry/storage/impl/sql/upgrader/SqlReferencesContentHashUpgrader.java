/*
 * Copyright 2024 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RegisterForReflection
public class SqlReferencesContentHashUpgrader extends AbstractReferencesContentHashUpgrader {

    private static final Logger log = LoggerFactory.getLogger(SqlReferencesContentHashUpgrader.class);


    @Override
    protected void beforeEach() {
        // NOOP
    }


    @Override
    protected void applyUpdate(Handle handle, ExtendedContentEntity entity) {
        String sql = "UPDATE content SET contentHash = ? WHERE tenantId = ? AND contentId = ?";
        int rowCount = handle.createUpdate(sql)
                .bind(0, entity.contentEntity.contentHash)
                .bind(1, entity.tenantId)
                .bind(2, entity.contentEntity.contentId)
                .execute();
        if (rowCount == 0) {
            log.warn("Failed to update content hash to {} for contentId {} and tenantId {}. Database row not found.", entity.contentEntity.contentHash, entity.contentEntity.contentId, entity.tenantId);
        } else {
            successCounter++;
        }
    }
}
