/**
 * @license
 * Copyright 2020 JBoss Inc
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
import React from "react";
import "./artifactTypeIcon.css";

/**
 * Properties
 */
export interface ArtifactTypeIconProps {
    type: string;
}

/**
 * State
 */
export interface ArtifactTypeIconState {
}


/**
 * Models the list of artifacts.
 */
export class ArtifactTypeIcon extends React.PureComponent<ArtifactTypeIconProps, ArtifactTypeIconState> {

    constructor(props: Readonly<ArtifactTypeIconProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <div className={this.getClassNames()}/>
        );
    }

    private getClassNames(): string {
        let classes: string = "artifact-type-icon";
        switch (this.props.type) {
            case "AVRO":
                classes += " avro-icon24";
                break;
            case "PROTOBUF":
                classes += " protobuf-icon24";
                break;
            case "PROTOBUF_FD":
                classes += " protobuf-icon24";
                break;
            case "JSON":
                classes += " json-icon24";
                break;
            case "OPENAPI":
                classes += " oai-icon24";
                break;
            case "ASYNCAPI":
                classes += " aai-icon24";
                break;
            case "GRAPHQL":
                classes += " graphql-icon24";
                break;
            case "KCONNECT":
                classes += " kconnect-icon24";
                break;
        }
        return classes;
    }

}
