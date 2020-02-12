import React, { ReactNode } from 'react';
import {Button, ButtonVariant, Dropdown, DropdownToggle, Toolbar, ToolbarGroup, ToolbarItem} from '@patternfly/react-core';
import {UserDropdown} from './components/userDropDown'
import { CogIcon} from '@patternfly/react-icons';
//TODO: Need to add accessibility to the toolbar (see: http://patternfly-react.surge.sh/patternfly-4/demos/pagelayout)
 

let isDropdownOpen: boolean = false;

const userDropdownItems: ReactNode[] = [];

export const AppToolbar = (
    <Toolbar>
        <ToolbarGroup>
            <ToolbarItem> 
            <Button id="simple-example-uid-02" aria-label="Settings actions" variant={ButtonVariant.plain}>
                <CogIcon />
            </Button>
          </ToolbarItem>
        </ToolbarGroup>
        <ToolbarGroup>
        <ToolbarItem>
          <UserDropdown/>
        </ToolbarItem>
      </ToolbarGroup>
    </Toolbar>
  );

  export default AppToolbar;