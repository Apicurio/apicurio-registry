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
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../../components";
import {Tab} from "@patternfly/react-core";
// import AceEditor from "react-ace";
// import "ace-builds/src-noconflict/mode-json";
// import "ace-builds/src-noconflict/theme-tomorrow";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ContentTabContentProps extends PureComponentProps {
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ContentTabContentState extends PureComponentState {
}


/**
 * Models the content of the Artifact Info tab.
 */
export class ContentTabContent extends PureComponent<ContentTabContentProps, ContentTabContentState> {

    constructor(props: Readonly<ContentTabContentProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <h1>Content goes in here</h1>
        );
    }

    protected initializeState(): ContentTabContentState {
        return {};
    }
}

// {/*<AceEditor*/}
// {/*    mode="json"*/}
// {/*    theme="tomorrow"*/}
// {/*    name="artifactContent"*/}
// {/*    fontSize={14}*/}
// {/*    showPrintMargin={false}*/}
// {/*    showGutter={true}*/}
// {/*    highlightActiveLine={false}*/}
// {/*    value={this.getArtifactContent()}*/}
// {/*    setOptions={{*/}
// {/*        enableBasicAutocompletion: false,*/}
// {/*        enableLiveAutocompletion: false,*/}
// {/*        enableSnippets: false,*/}
// {/*        showLineNumbers: true,*/}
// {/*        tabSize: 2,*/}
// {/*    }}*/}
// {/*/>*/}
