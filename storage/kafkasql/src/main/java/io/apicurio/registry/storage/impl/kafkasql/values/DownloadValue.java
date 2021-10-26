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

import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
public class DownloadValue extends AbstractMessageValue {

    private DownloadContextDto downloadContext;

    /**
     * Creator method.
     * @param action
     * @param downloadContext
     */
    public static final DownloadValue create(ActionType action, DownloadContextDto downloadContext) {
        DownloadValue value = new DownloadValue();
        value.setAction(action);
        value.setDownloadContext(downloadContext);
        return value;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.values.MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Download;
    }

    /**
     * @return the downloadContext
     */
    public DownloadContextDto getDownloadContext() {
        return downloadContext;
    }

    /**
     * @param downloadContext the downloadContext to set
     */
    public void setDownloadContext(DownloadContextDto downloadContext) {
        this.downloadContext = downloadContext;
    }

}
