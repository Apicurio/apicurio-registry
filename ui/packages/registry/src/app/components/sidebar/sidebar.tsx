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
import {
    Nav,
    NavExpandable,
    NavItem,
    NavList,
    NavProps,
    PageSidebar
} from "@patternfly/react-core";
import {Link} from 'react-router-dom';

interface IAppSidebarProps extends NavProps {
    activeMenuGroup: string;
    activeMenuGroupItem: string;
}

export const Sidebar: React.FunctionComponent<IAppSidebarProps> = (
    {
        onSelect,
        activeMenuGroup,
        activeMenuGroupItem,
        ...props
    }: IAppSidebarProps) => {
    const sideBarNavigation = (
        <Nav onSelect={onSelect} aria-label="Nav">
            <NavList>
                <NavItem
                    groupId="grp_main"
                    itemId="itm_artifacts"
                    isActive={activeMenuGroupItem === "itm_artifacts" || activeMenuGroupItem === ""}
                >
                    <Link to="/artifacts"> Artifacts </Link>
                </NavItem>
                <NavItem
                    groupId="grp_main"
                    itemId="itm_settings"
                    isActive={activeMenuGroupItem === "itm_settings"}
                >
                    <Link to="/settings"> Settings </Link>
                </NavItem>
            </NavList>
        </Nav>
    );
    return <PageSidebar nav={sideBarNavigation}/>;
};

export default Sidebar;
