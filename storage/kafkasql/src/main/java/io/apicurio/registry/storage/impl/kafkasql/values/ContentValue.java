/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import lombok.ToString;

/**
 * @author eric.wittmann@gmail.com
 */
@ToString
public class ContentValue extends AbstractMessageValue {

    private String canonicalHash;
    @ToString.Exclude
    private ContentHandle content;
    private String serializedReferences;

    /**
     * Creator method.
     * @param action
     * @param canonicalHash
     * @param content
     */
    public static final ContentValue create(ActionType action, String canonicalHash, ContentHandle content, String serializedReferences) {
        ContentValue value = new ContentValue();
        value.setAction(action);
        value.setCanonicalHash(canonicalHash);
        value.setContent(content);
        value.setSerializedReferences(serializedReferences);
        return value;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.values.MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Content;
    }


    /**
     * @return the content
     */
    public ContentHandle getContent() {
        return content;
    }

    /**
     * @param content the content to set
     */
    public void setContent(ContentHandle content) {
        this.content = content;
    }

    /**
     * @return the canonicalHash
     */
    public String getCanonicalHash() {
        return canonicalHash;
    }

    /**
     * @param canonicalHash the canonicalHash to set
     */
    public void setCanonicalHash(String canonicalHash) {
        this.canonicalHash = canonicalHash;
    }

    /**
     * @return the serialized references
     */
    public String getSerializedReferences() {
        return serializedReferences;
    }

    /**
     * @param serializedReferences
     */
    public void setSerializedReferences(String serializedReferences) {
        this.serializedReferences = serializedReferences;
    }
}
