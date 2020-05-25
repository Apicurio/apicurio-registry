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
import "./content.css";
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import AceEditor from "react-ace";
import "ace-builds/src-noconflict/mode-json";
import "ace-builds/src-noconflict/mode-protobuf";
import "ace-builds/src-noconflict/mode-xml";
import "ace-builds/src-noconflict/mode-graphqlschema";
import "ace-builds/src-noconflict/theme-monokai";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ContentTabContentProps extends PureComponentProps {
    artifactContent: string;
    artifactType: string;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ContentTabContentState extends PureComponentState {
    editorWidth: string;
    editorHeight: string;
}


/**
 * Models the content of the Artifact Info tab.
 */
export class ContentTabContent extends PureComponent<ContentTabContentProps, ContentTabContentState> {

    constructor(props: Readonly<ContentTabContentProps>) {
        super(props);
    }

    public componentDidMount(): void {
        // TODO do this again whenever the browser is resized!
        const elem: HTMLElement|null = document.getElementById("ace-wrapper");
        if (elem) {
            const height: number|null = elem.clientHeight;
            if (height) {
                this.setSingleState("editorHeight", height + "px");
            }
        }
    }

    public render(): React.ReactElement {
        return (
            <div className="ace-wrapper" id="ace-wrapper">
                <AceEditor
                    data-testid="ace-content"
                    mode={this.editorMode()}
                    theme="monokai"
                    name="artifactContent"
                    className="artifactContent"
                    width={this.state.editorWidth}
                    height={this.state.editorHeight}
                    fontSize={14}
                    showPrintMargin={false}
                    showGutter={true}
                    highlightActiveLine={false}
                    value={this.props.artifactContent}
                    readOnly={true}
                    setOptions={{
                        enableBasicAutocompletion: false,
                        enableLiveAutocompletion: false,
                        enableSnippets: false,
                        showLineNumbers: true,
                        tabSize: 2,
                        useWorker: false
                    }}
                />
            </div>
        );
    }

    protected initializeState(): ContentTabContentState {
        return {
            editorHeight: "500px",
            editorWidth: "100%"
        };
    }

    private editorMode(): string {
        if (this.props.artifactType === "PROTOBUF") {
            return "protobuf";
        }
        if (this.props.artifactType === "WSDL" || this.props.artifactType === "XSD" || this.props.artifactType === "XML") {
            return "xml";
        }
        if (this.props.artifactType === "GRAPHQL") {
            return "graphqlschema";
        }
        return "json";
    }
}

