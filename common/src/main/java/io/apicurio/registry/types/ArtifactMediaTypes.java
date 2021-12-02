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

package io.apicurio.registry.types;

import javax.ws.rs.core.MediaType;

/**
 * @author eric.wittmann@gmail.com
 */
public final class ArtifactMediaTypes {

    public static final MediaType JSON = MediaType.APPLICATION_JSON_TYPE;
    public static final MediaType XML = MediaType.APPLICATION_XML_TYPE;
    public static final MediaType YAML = new MediaType("application", "x-yaml");
    public static final MediaType PROTO = new MediaType("application", "x-protobuf");
    public static final MediaType GRAPHQL = new MediaType("application", "graphql");
    public static final MediaType BINARY = new MediaType("application", "octet-stream");
    
}
