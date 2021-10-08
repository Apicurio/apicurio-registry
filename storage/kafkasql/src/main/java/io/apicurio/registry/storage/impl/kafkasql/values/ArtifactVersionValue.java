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

import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.types.ArtifactState;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
@ToString
public class ArtifactVersionValue extends AbstractMessageValue {

    private ArtifactState state;
    private EditableArtifactMetaDataDto metaData;

    /**
     * Creator method.
     * @param action
     * @param state
     * @param metaData
     */
    public static final ArtifactVersionValue create(ActionType action, ArtifactState state, EditableArtifactMetaDataDto metaData) {
        ArtifactVersionValue value = new ArtifactVersionValue();
        value.setAction(action);
        value.setState(state);
        value.setMetaData(metaData);
        return value;
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.values.MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ArtifactVersion;
    }
    
    /**
     * @return the metaData
     */
    public EditableArtifactMetaDataDto getMetaData() {
        return metaData;
    }

    /**
     * @param metaData the metaData to set
     */
    public void setMetaData(EditableArtifactMetaDataDto metaData) {
        this.metaData = metaData;
    }

    /**
     * @return the state
     */
    public ArtifactState getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public void setState(ArtifactState state) {
        this.state = state;
    }

}
