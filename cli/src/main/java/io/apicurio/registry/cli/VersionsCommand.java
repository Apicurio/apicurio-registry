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

import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import picocli.CommandLine;

/**
 * @author Ales Justin
 */
@CommandLine.Command(name = "versions", description = "Get artifact versions")
public class VersionsCommand extends ArtifactCommand {

    @CommandLine.Option(names = {"--offset"}, description = "Offset")
    Integer offset;
    @CommandLine.Option(names = {"--limit"}, description = "Limit")
    Integer limit;

    @Override
    public void run() {
        VersionSearchResults versions = getClient().listArtifactVersions(groupId, artifactId, offset == null ? 0 : offset, limit == null ? 10 : limit);
        try {
            println(mapper.writeValueAsString(versions));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
