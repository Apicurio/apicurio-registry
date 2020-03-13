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
import {
    Brand,
    Button,
    ButtonVariant,
    PageHeader,
    Toolbar,
    ToolbarGroup,
    ToolbarItem
} from '@patternfly/react-core';
import brandImg from '../../../../assets/images/apicurio_logo_darkbkg_200px.png';
import {CogIcon} from "@patternfly/react-icons";

const showNavToogle: boolean = true;

const HeaderToolbar = (
    <Toolbar>
        <ToolbarGroup>
            <ToolbarItem>
                <Button id="settings-cog" aria-label="Settings actions" variant={ButtonVariant.plain}>
                    <CogIcon/>
                </Button>
            </ToolbarItem>
        </ToolbarGroup>
    </Toolbar>
);

export const Header: React.FunctionComponent<any> = (props) => {
    return (<PageHeader
        logo={<Brand src={ brandImg } alt="Apicurio" />}
        toolbar={HeaderToolbar}
        showNavToggle = {showNavToogle}
      />);
}

export default Header;
