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

package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@RegisterForReflection
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ContentV2Key extends AbstractMessageKey {


    private long contentId;


    public static ContentV2Key create(String tenantId, long contentId) {
        ContentV2Key key = new ContentV2Key();
        key.setTenantId(tenantId);
        key.setContentId(contentId);
        return key;
    }


    @Override
    public MessageType getType() {
        return MessageType.ContentV2;
    }


    @Override
    public String getPartitionKey() {
        return getTenantId() + contentId;
    }
}
