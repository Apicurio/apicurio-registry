/*
 * Copyright 2023 Red Hat Inc
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

package io.apicurio.registry.maven.refs;

import java.nio.file.Path;
import java.util.Set;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author eric.wittmann@gmail.com
 */
public class IndexedResource {

    private final Path path;
    private final String type;
    private final String resourceName;
    private final ContentHandle content;
    private ArtifactMetaData registration;

    /**
     * Constructor.
     * @param path
     * @param type
     * @param resourceName
     * @param content
     */
    public IndexedResource(Path path, String type, String resourceName, ContentHandle content) {
        super();
        this.path = path;
        this.content = content;
        this.type = type;
        this.resourceName = resourceName;
    }
    
    /**
     * @return the content
     */
    public ContentHandle getContent() {
        return content;
    }
    
    /**
     * @return the type
     */
    public String getType() {
        return type;
    }
    
    /**
     * @return the resourceName
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * @return the path
     */
    public Path getPath() {
        return path;
    }

    public boolean matches(String resourceName, Path relativeToFile, Set<Path> schemaPaths) {
        // If this is a pre-registered reference, the match has to happen by resource name.
        if (this.path == null) {
            return this.resourceName.equals(resourceName);
        }

        // For Avro files the match can happen either via path (e.g. when referencing an Avro
        // file from an AsyncAPI file) or via resource name (e.g. from Avro to Avro).
        if (ArtifactType.AVRO.equals(this.type)) {
            if (this.resourceName.equals(resourceName)) {
                return true;
            }
        }

        // The resource name will otherwise be a relative path to the resource.
        Path resolvedPath = relativeToFile.getParent().resolve(resourceName);
        boolean resolves = this.path.normalize().equals(resolvedPath.normalize());

        // Protobuf can resolve relative to the "schema paths" (aka --proto-paths in protoc).
        if (!resolves && ArtifactType.PROTOBUF.equals(this.type)) {
            resolves = schemaPaths.parallelStream().anyMatch(path -> this.path.normalize().equals(path.resolve(resourceName).normalize()));
        }
        return resolves;
    }

    /**
     * @return the registration
     */
    public ArtifactMetaData getRegistration() {
        return registration;
    }

    /**
     * @param registration the registration to set
     */
    public void setRegistration(ArtifactMetaData registration) {
        this.registration = registration;
    }

    public boolean isRegistered() {
        return this.registration != null;
    }
}
