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

import React from 'react';
import "./header.css"
import {Brand, PageHeader, Toolbar, ToolbarGroup, ToolbarItem} from '@patternfly/react-core';
import brandImg from '../../../../assets/images/apicurio_logo_darkbkg_350px.png';
import {PureComponent, PureComponentProps, PureComponentState} from "../baseComponent";
import {CogIcon} from "@patternfly/react-icons";
import {Link} from "react-router-dom";


// tslint:disable-next-line:no-empty-interface
export interface AppHeaderProps extends PureComponentProps {
}

// tslint:disable-next-line:no-empty-interface
export interface AppHeaderState extends PureComponentState {
}


export class AppHeader extends PureComponent<AppHeaderProps, AppHeaderState> {

    constructor(props: Readonly<AppHeaderProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        const pageToolbar: React.ReactElement = (
            <Toolbar className="header-toolbar">
                <ToolbarGroup>
                    <ToolbarItem>
                        <Link data-testid="masthead-lnk-settings" className="header-icon" to={ `/rules` }>
                            <CogIcon />
                        </Link>
                    </ToolbarItem>
                </ToolbarGroup>
            </Toolbar>
        );

        return (<PageHeader
            logo={<Brand onClick={this.navigateTo("/artifacts")} src={brandImg} alt="Apicurio Registry"/>}
            showNavToggle={false}
            toolbar={pageToolbar}
        />);
    }

    protected initializeState(): AppHeaderState {
        return {};
    }

}
