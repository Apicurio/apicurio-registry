/*
 * Copyright 2026 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.contracts.ContractMetadataMapper;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.EditableContractMetadataDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AbstractSqlRegistryStorageContractTransactionTest {

    private TransactionTrackingHandleFactory handles;
    private TestSqlRegistryStorage storage;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        handles = new TransactionTrackingHandleFactory();
        storage = new TestSqlRegistryStorage(handles);
        storage.contractMetadataMapper = new ContractMetadataMapper();
        storage.outboxEvent = mock(Event.class);
        doThrow(new IllegalStateException("Outbox insert failed"))
                .when(storage.outboxEvent).fire(any(SqlOutboxEvent.class));
    }

    @Test
    void shouldRollbackContractMetadataWhenOutboxInsertFails() {
        EditableContractMetadataDto metadata = EditableContractMetadataDto.builder()
                .status(ContractStatus.STABLE).ownerTeam("platform").build();

        assertThrows(IllegalStateException.class,
                () -> storage.updateContractMetadata("default", "artifact", null, metadata));

        assertEquals(1, storage.mergeCalls);
        assertEquals(Map.of(), handles.committedLabels());
    }

    @Test
    void shouldRollbackAllStatusLabelsWhenOutboxInsertFails() {
        assertThrows(IllegalStateException.class,
                () -> storage.transitionContractStatus("default", "artifact", null,
                        ContractStatus.DRAFT, ContractStatus.STABLE, "2026-08-01"));

        assertEquals(2, storage.mergeCalls);
        assertEquals(Map.of(), handles.committedLabels());
    }

    private static class TestSqlRegistryStorage extends AbstractSqlRegistryStorage {

        private final TransactionTrackingHandleFactory transactionHandles;
        private int mergeCalls;

        private TestSqlRegistryStorage(TransactionTrackingHandleFactory handles) {
            this.handles = handles;
            this.transactionHandles = handles;
        }

        @Override
        public void initialize() {
            // This focused unit test supplies only the collaborators used by the tested methods.
            // Production initialization would require unrelated SQL repositories and configuration.
        }

        @Override
        public void mergeArtifactLabels(String groupId, String artifactId, String prefix,
                Map<String, String> labels) throws RegistryStorageException {
            mergeCalls++;
            handles.withHandleNoException(
                    (HandleAction<RegistryStorageException>) handle -> transactionHandles.merge(labels));
        }
    }

    private static class TransactionTrackingHandleFactory implements HandleFactory {

        private final Handle handle = mock(Handle.class);
        private final Map<String, String> committedLabels = new HashMap<>();
        private Map<String, String> transactionLabels;
        private int level;

        @Override
        public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
            boolean outermost = level == 0;
            if (outermost) {
                transactionLabels = new HashMap<>(committedLabels);
            }
            level++;
            try {
                R result = callback.withHandle(handle);
                if (outermost) {
                    committedLabels.clear();
                    committedLabels.putAll(transactionLabels);
                }
                return result;
            } finally {
                level--;
                if (outermost) {
                    transactionLabels = null;
                }
            }
        }

        @Override
        public <R, X extends Exception> R withHandleNoException(HandleCallback<R, X> callback) {
            try {
                return withHandle(callback);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RegistryStorageException(e);
            }
        }

        @Override
        public <X extends Exception> void withHandleNoException(HandleAction<X> callback) {
            withHandleNoException(currentHandle -> {
                callback.withHandle(currentHandle);
                return null;
            });
        }

        private void merge(Map<String, String> labels) {
            transactionLabels.putAll(labels);
        }

        private Map<String, String> committedLabels() {
            return Map.copyOf(committedLabels);
        }
    }
}
