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

package io.apicurio.registry.storage.impl.ksql.values;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.ksql.keys.MessageType;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author eric.wittmann@gmail.com
 */
public class ContentValue extends AbstractMessageValue {
    
    private ArtifactType artifactType;
    private ContentHandle content;

    /**
     * Creator method.
     * @param action
     * @param canonicalHash
     * @param content
     */
    public static final ContentValue create(ActionType action, ArtifactType artifactType, ContentHandle content) {
        ContentValue key = new ContentValue();
        key.setAction(action);
        key.setArtifactType(artifactType);
        key.setContent(content);
        return key;
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.ksql.values.MessageValue#getType()
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
     * @return the artifactType
     */
    public ArtifactType getArtifactType() {
        return artifactType;
    }

    /**
     * @param artifactType the artifactType to set
     */
    public void setArtifactType(ArtifactType artifactType) {
        this.artifactType = artifactType;
    }

}
