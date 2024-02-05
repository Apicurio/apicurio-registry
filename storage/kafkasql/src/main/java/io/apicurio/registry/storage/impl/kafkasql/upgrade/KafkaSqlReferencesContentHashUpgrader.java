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

package io.apicurio.registry.storage.impl.kafkasql.upgrade;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlCoordinator;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter;
import io.apicurio.registry.storage.impl.kafkasql.upgrade.KafkaSqlUpgraderManager.UpgraderManagerHandle;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.upgrader.AbstractReferencesContentHashUpgrader;
import io.apicurio.registry.utils.ConcurrentUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class KafkaSqlReferencesContentHashUpgrader extends AbstractReferencesContentHashUpgrader implements KafkaSqlUpgrader {


    @Inject
    KafkaSqlSubmitter submitter;

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    HandleFactory handles;

    private UpgraderManagerHandle upgraderManagerHandle;


    @Override
    protected void beforeEach() {
        upgraderManagerHandle.heartbeat();
    }


    @Override
    protected void applyUpdate(Handle handle, ExtendedContentEntity entity) {
        var uuid = submitter.submitContent(ActionType.CREATE_OR_UPDATE, entity.tenantId, entity.contentEntity.contentId,
                entity.contentEntity.contentHash, entity.contentEntity.canonicalHash,
                ContentHandle.create(entity.contentEntity.contentBytes), entity.contentEntity.serializedReferences);
        upgraderManagerHandle.heartbeat();
        coordinator.waitForResponse(ConcurrentUtil.get(uuid));
        upgraderManagerHandle.heartbeat();
    }


    @Override
    public boolean supportsVersion(int currentVersion) {
        return currentVersion == KafkaSqlUpgraderManager.BASE_KAFKASQL_TOPIC_VERSION;
    }


    @Override
    public void upgrade(UpgraderManagerHandle upgraderManagerHandle) {
        this.upgraderManagerHandle = upgraderManagerHandle;
        handles.withHandleNoException(handle -> {
            super.upgrade(handle);
            return null;
        });
    }
}
