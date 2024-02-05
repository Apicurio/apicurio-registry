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

package io.apicurio.registry.storage.impl.kafkasql.values;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

import java.util.Base64;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

@RegisterForReflection
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
// NOTE: Ensures that Jackson de/serializes using fields, and not getters and setters.
// This allows us to hide the base64Content field.
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class ContentV2Value extends AbstractMessageValue {

    private String contentHash;

    private String canonicalContentHash;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private ContentHandle content;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    private String base64Content;

    private String serializedReferences;


    public static ContentV2Value create(ActionType action, String contentHash, String canonicalContentHash, ContentHandle content, String serializedReferences) {
        ContentV2Value value = new ContentV2Value();
        value.setAction(action);
        value.setContentHash(contentHash);
        value.setCanonicalContentHash(canonicalContentHash);
        value.setContent(content);
        value.setSerializedReferences(serializedReferences);
        return value;
    }


    @Override
    public MessageType getType() {
        return MessageType.ContentV2;
    }


    public ContentHandle getContent() {
        if (content == null) {
            content = ContentHandle.create(Base64.getDecoder().decode(base64Content));
        }
        return content;
    }


    public void setContent(ContentHandle content) {
        base64Content = Base64.getEncoder().encodeToString(content.bytes());
        this.content = content;
    }
}
