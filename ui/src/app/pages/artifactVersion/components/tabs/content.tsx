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
import { CodeEditor, Language } from '@patternfly/react-code-editor';


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ContentTabContentProps extends PureComponentProps {
    artifactContent: string;
    artifactType: string;
    artifactName: string;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ContentTabContentState extends PureComponentState {
    content: string;
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
            <div className="code-editor-wrapper">
                <CodeEditor 
                    code={this.state.content} 
                    height="sizeToFit" 
                    language={this.editorMode()} 
                    isReadOnly 
                    isDownloadEnabled
                    downloadFileName={this.props.artifactName}
                />
            </div>
        );
    }

    protected initializeState(): ContentTabContentState {
        return {
            content: this.formatContent(),
            editorHeight: "500px",
            editorWidth: "100%"
        };
    }

    private editorMode(): Language {
        if (this.props.artifactType === "PROTOBUF") {
            return Language.plaintext;
        }
        if (this.props.artifactType === "WSDL" || this.props.artifactType === "XSD" || this.props.artifactType === "XML") {
            return Language.xml;
        }
        if (this.props.artifactType === "GRAPHQL") {
            return Language.graphql;
        }
        return Language.json;
    }

    private formatContent(): string {
        try {
            const pval: any = JSON.parse(this.props.artifactContent);
            if (pval) {
                return JSON.stringify(pval, null, 2);
            }
        } catch (e) {
            // Do nothing
        }
        return this.props.artifactContent;
    }

}

