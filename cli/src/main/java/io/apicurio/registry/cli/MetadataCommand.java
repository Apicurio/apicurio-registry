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

package io.apicurio.registry.cli;

import java.io.UncheckedIOException;

import com.fasterxml.jackson.core.JsonProcessingException;

import picocli.CommandLine;

/**
 * @author Ales Justin
 */
@CommandLine.Command(name = "metadata", description = "Get artifact metadata")
public class MetadataCommand extends ArtifactCommand {
    @Override
    public void run() {
        Object md;
        if (version != null) {
            md = getClient().getArtifactVersionMetaData(groupId, artifactId, version);
        } else {
            md = getClient().getArtifactMetaData(groupId, artifactId);
        }
        try {
            println(mapper.writeValueAsString(md));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
