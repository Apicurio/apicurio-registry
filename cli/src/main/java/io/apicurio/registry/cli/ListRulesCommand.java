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

import io.apicurio.registry.types.RuleType;
import picocli.CommandLine;

import java.io.UncheckedIOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * @author Ales Justin
 */
@CommandLine.Command(name = "listRules", description = "List artifact rules")
public class ListRulesCommand extends ArtifactCommand {
    @Override
    public void run() {
        List<RuleType> rules = getClient().listArtifactRules(groupId, artifactId);
        try {
            println(mapper.writeValueAsString(rules));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
