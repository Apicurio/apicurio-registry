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

import java.time.OffsetDateTime;

/**
 * @author Fabian Martinez
 */
public class CloudEventImpl<T> implements CloudEvent<T> {

    String dataschema;
    T data;

    final io.cloudevents.CloudEvent delegate;

    public CloudEventImpl(io.cloudevents.CloudEvent cloudevent, String dataschema, T data) {
        this.delegate = cloudevent;
        this.dataschema = dataschema;
        this.data = data;
    }

    @Override
    public String id() {
        return delegate.getId();
    }

    @Override
    public String specVersion() {
        return delegate.getSpecVersion().toString();
    }

    @Override
    public String source() {
        return delegate.getSource().toString();
    }

    @Override
    public String subject() {
        return delegate.getSubject();
    }

    @Override
    public OffsetDateTime time() {
        return delegate.getTime();
    }

    @Override
    public String type() {
        return delegate.getType();
    }

    @Override
    public String datacontenttype() {
        return delegate.getDataContentType();
    }

    @Override
    public T data() {
        return data;
    }

    @Override
    public String dataschema() {
        return dataschema;
    }

}
