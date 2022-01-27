/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.resolver.data;

/**
 * Record defines an object that is known as the data or the payload of the record and it's associated metadata.
 * A record can be message to be sent or simply an object that can be serialized and deserialized.
 *
 * @author Fabian Martinez
 */
public interface Record<T> {

    Metadata metadata();

    T payload();

}
