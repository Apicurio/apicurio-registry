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
import { Button, ButtonVariant, Dropdown, DropdownToggle, InputGroup, TextInput } from "@patternfly/react-core";
import { PureComponent, PureComponentProps, PureComponentState } from "../../../../components";
import { SearchIcon } from "@patternfly/react-icons";
import Moment from "react-moment";
import { Link } from "react-router-dom";
import { SearchedVersion } from "../../../../../models";
import { Services } from "../../../../../services";


/**
 * Properties
 */
export interface VersionSelectorProps extends PureComponentProps {
    groupId: string;
    artifactId: string;
    version: string;
    versions: SearchedVersion[];
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
                className={this.dropdownClasses()}
                toggle={<DropdownToggle data-testid="versions-toggle" onToggle={this.onToggle}>Version: { this.props.version }</DropdownToggle>}
                isOpen={this.state.isOpen}
            >
                <div className="version-filter" style={{display: "none"}}>
                    <InputGroup>
                        <TextInput name="filter" id="versionFilter" type="search" data-testid="versions-form-filter" aria-label="Version filter" />
                        <Button variant={ButtonVariant.control} data-testid="versions-form-btn-search" aria-label="search button for search input">
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
                    <Link key="latest"
                          data-testid="versions-lnk-latest"
                          to={this.linkTo(`/artifacts/${encodeURIComponent(this.props.groupId)}/${encodeURIComponent(this.props.artifactId)}/versions/latest`)}
                          className="version-item latest">
                        <span className="name">latest</span>
                        <span className="date" />
                    </Link>
                    {
                        this.props.versions.map((v, idx) =>
                            <Link key={v.version}
                                  data-testid={`versions-lnk-${idx}`}
                                  to={this.linkTo(`/artifacts/${encodeURIComponent(this.props.groupId)}/${encodeURIComponent(this.props.artifactId)}/versions/${v.version}`)}
                                  className="version-item">
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

    private dropdownClasses(): string {
        const classes: string[] = [ "version-selector-dropdown" ];
        if (Services.getConfigService().featureReadOnly()) {
            classes.push("dropdown-align-right");
        }
        return classes.join(' ');
    }

    private onToggle = (isOpen: boolean): void => {
        this.setSingleState("isOpen", isOpen);
    };
}
