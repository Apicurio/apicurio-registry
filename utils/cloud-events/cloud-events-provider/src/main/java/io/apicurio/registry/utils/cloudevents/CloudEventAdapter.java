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
package io.apicurio.registry.utils.cloudevents;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;

/**
 * @author Fabian Martinez
 */
public class CloudEventAdapter implements CloudEvent {

    private io.apicurio.registry.utils.cloudevents.CloudEvent<?> delegate;

    public CloudEventAdapter(io.apicurio.registry.utils.cloudevents.CloudEvent<?> delegate) {
        this.delegate = delegate;
    }

    @Override
    public SpecVersion getSpecVersion() {
        return SpecVersion.parse(delegate.specVersion());
    }

    @Override
    public String getId() {
        return delegate.id();
    }

    @Override
    public String getType() {
        return delegate.type();
    }

    @Override
    public URI getSource() {
        return URI.create(delegate.source());
    }

    @Override
    public String getDataContentType() {
        return delegate.datacontenttype();
    }

    @Override
    public URI getDataSchema() {
        return URI.create(delegate.dataschema());
    }

    @Override
    public String getSubject() {
        return delegate.subject();
    }

    @Override
    public OffsetDateTime getTime() {
        return delegate.time();
    }

    @Override
    public Object getAttribute(String attributeName) throws IllegalArgumentException {
        return null; //TODO fix
    }

    @Override
    public Object getExtension(String extensionName) {
        return null; //TODO fix
    }

    @Override
    public Set<String> getExtensionNames() {
        return Collections.emptySet();
    }

    @Override
    public byte[] getData() {
        return null;
    }

}
