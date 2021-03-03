/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.infinispan;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.StoredContent;
import io.apicurio.registry.types.ArtifactType;
import org.infinispan.util.function.SerializableFunction;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import static io.apicurio.registry.storage.impl.AbstractMapRegistryStorage.sha256Hash;

/**
 * Content function.
 *
 * @author Ales Justin
 */
public class ContentFn implements SerializableFunction<String, StoredContent> {

    private static final long serialVersionUID = 1L;

    private String contentHash;
    private ArtifactType artifactType;
    private byte[] bytes;

    private transient StorageHandle handle;

    public ContentFn(StorageHandle handle, String contentHash, ArtifactType artifactType, byte[] bytes) {
        this.handle = handle;
        this.contentHash = contentHash;
        this.artifactType = artifactType;
        this.bytes = bytes;
    }

    @SuppressWarnings("unchecked")
    private StorageHandle getHandle() {
        if (handle == null) {
            BeanManager bm = CDI.current().getBeanManager();
            Bean<StorageHandle> bean = (Bean<StorageHandle>) bm.resolve(bm.getBeans(StorageHandle.class));
            handle = (StorageHandle) bm.getReference(bean, bean.getBeanClass(), bm.createCreationalContext(bean));
        }
        return handle;
    }

    @Override
    public StoredContent apply(String key) {
        StoredContent content = new StoredContent();
        content.setContentId(getHandle().nextContentId());
        content.setContentHash(contentHash);
        content.setContent(bytes);
        String canonicalHash = sha256Hash(getHandle().canonicalizeContent(artifactType, ContentHandle.create(bytes)));
        content.setCanonicalHash(canonicalHash);
        return content;
    }
}
