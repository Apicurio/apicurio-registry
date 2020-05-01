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
import {PureComponent, PureComponentProps, PureComponentState} from "../baseComponent";

/**
 * Properties
 */
export interface ArtifactTypeIconProps extends PureComponentProps {
    type: string;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactTypeIconState extends PureComponentState {
}


/**
 * Models the list of artifacts.
 */
export class ArtifactTypeIcon extends PureComponent<ArtifactTypeIconProps, ArtifactTypeIconState> {

    constructor(props: Readonly<ArtifactTypeIconProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <div className={this.getClassNames()} title={this.getTitle()} />
        );
    }

    protected initializeState(): ArtifactTypeIconState {
        return {};
    }

    private getTitle(): string {
        let title: string = this.props.type;
        switch (this.props.type) {
            case "AVRO":
                title = "Avro Schema";
                break;
            case "PROTOBUF":
                title = "Protobuf Schema";
                break;
            case "PROTOBUF_FD":
                title = "Protobuf Schema";
                break;
            case "JSON":
                title = "JSON Schema";
                break;
            case "OPENAPI":
                title = "OpenAPI Definition";
                break;
            case "ASYNCAPI":
                title = "AsyncAPI Definition";
                break;
            case "GRAPHQL":
                title = "GraphQL Definition";
                break;
            case "KCONNECT":
                title = "Kafka Connect Schema";
                break;
            case "WSDL":
                title = "WSDL";
                break;
            case "XSD":
                title = "XML Schema";
                break;
            case "XML":
                title = "XML";
                break;
        }
        return title;
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
            case "WSDL":
                classes += " wsdl-icon24";
                break;
            case "XSD":
                classes += " xsd-icon24";
                break;
            case "XML":
                classes += " xml-icon24";
                break;
        }
        return classes;
    }

}
