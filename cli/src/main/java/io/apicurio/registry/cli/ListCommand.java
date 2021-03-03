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

import picocli.CommandLine;

import java.io.UncheckedIOException;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;

/**
 * @author Ales Justin
 */
@CommandLine.Command(name = "list", description = "List artifacts in a specific group")
public class ListCommand extends GroupCommand {

    @CommandLine.Option(names = {"--offset"}, description = "Offset")
    Integer offset;
    @CommandLine.Option(names = {"--limit"}, description = "Limit")
    Integer limit;

    @Override
    public void run() {
        ArtifactSearchResults artifacts = getClient().listArtifactsInGroup(groupId, null, null, offset == null ? 0 : offset, limit == null ? 10 : limit);
        try {
            println(mapper.writeValueAsString(artifacts));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
