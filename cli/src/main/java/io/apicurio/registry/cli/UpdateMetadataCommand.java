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

import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import picocli.CommandLine;

/**
 * @author Ales Justin
 */
@CommandLine.Command(name = "updateMetadata", description = "Update artifact metadata")
public class UpdateMetadataCommand<T> extends CUJsonCommand<EditableMetaData> {

    public UpdateMetadataCommand() {
        super(EditableMetaData.class);
    }

    @Override
    Object execute(EditableMetaData json) {
        if (version != null) {
            getClient().updateArtifactVersionMetaData(groupId, artifactId, version, json);
        } else {
            getClient().updateArtifactMetaData(groupId, artifactId, json);
        }
        return json;
    }
}
