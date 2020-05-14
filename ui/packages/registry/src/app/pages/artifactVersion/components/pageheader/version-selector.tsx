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
import "./version-selector.css";
import {Button, ButtonVariant, Dropdown, DropdownToggle, InputGroup, TextInput} from '@patternfly/react-core';
import {PureComponent, PureComponentProps, PureComponentState} from "../../../../components";
import {VersionMetaData} from "@apicurio/registry-models";
import {SearchIcon} from "@patternfly/react-icons";
import Moment from "react-moment";
import {Link} from "react-router-dom";


/**
 * Properties
 */
export interface VersionSelectorProps extends PureComponentProps {
    artifactId: string;
    version: string;
    versions: VersionMetaData[];
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface VersionSelectorState extends PureComponentState {
    isOpen: boolean;
}


/**
 * Models the page header for the Artifact page.
 */
export class VersionSelector extends PureComponent<VersionSelectorProps, VersionSelectorState> {

    constructor(props: Readonly<VersionSelectorProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Dropdown
                className="version-selector-dropdown"
                toggle={<DropdownToggle onToggle={this.onToggle}>Version: { this.props.version }</DropdownToggle>}
                isOpen={this.state.isOpen}
            >
                <div className="version-filter" style={{display: "none"}}>
                    <InputGroup>
                        <TextInput name="filter" id="versionFilter" type="search" aria-label="Version filter" />
                        <Button variant={ButtonVariant.control} aria-label="search button for search input">
                            <SearchIcon />
                        </Button>
                    </InputGroup>
                </div>
                <div className="version-header">
                    <div className="version-item">
                        <span className="name">Version</span>
                        <span className="date">Created On</span>
                    </div>
                </div>
                <div className="version-list">
                    <Link key="latest" to={`/artifacts/${encodeURIComponent(this.props.artifactId)}/versions/latest`} className="version-item latest">
                        <span className="name">latest</span>
                        <span className="date" />
                    </Link>
                    {
                        this.props.versions.map(v =>
                            <Link key={v.version} to={`/artifacts/${encodeURIComponent(this.props.artifactId)}/versions/${v.version}`} className="version-item">
                                <span className="name">{ v.version }</span>
                                <span className="date"><Moment date={v.createdOn} fromNow={true} /></span>
                            </Link>
                        )
                    }
                </div>
            </Dropdown>
        );
    }

    protected initializeState(): VersionSelectorState {
        return {
            isOpen: false
        };
    }

    private onToggle = (isOpen: boolean): void => {
        this.setSingleState("isOpen", isOpen);
    };
}
