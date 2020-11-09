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
public interface CloudEvent<T> {

    String id();

    String specVersion();

    String source();

    String subject();

    OffsetDateTime time();

    String type();

    String datacontenttype();

    String dataschema();

    T data();

    static <T> CloudEvent<T> from(io.cloudevents.CloudEvent cloudevent, String dataschema, T data) {
        return new CloudEventImpl<T>(cloudevent, dataschema, data);
    }

}
