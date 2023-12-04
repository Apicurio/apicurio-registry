/*
 * Copyright 2022 Red Hat Inc
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

package io.apicurio.registry.storage;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * CDI event fired by the storage implementation.
 * Differs from {@see io.apicurio.registry.storage.impl.sql.SqlStorageEvent} because
 * this event is fired by non-SQL implementations as well.
 *
 */
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class StorageEvent {

    private StorageEventType type;
}
