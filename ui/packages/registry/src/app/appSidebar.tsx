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

interface AppSidebarProps extends NavProps {
  activeMenuGroup: string;
  activeMenuGroupItem: string;
}

export const AppSidebar: React.FunctionComponent<AppSidebarProps> = ({
  onSelect,
  activeMenuGroup,
  activeMenuGroupItem,
  ...props
}: AppSidebarProps) => {
  const sideBarNavigation = (
    <Nav onSelect={onSelect} aria-label="Nav">
      <NavList>
        <NavItem
          groupId="grp-1"
          itemId="grp-1_itm-1"
          isActive={activeMenuGroupItem === "grp-1_itm-1"}
        >
         <Link to="/dashboard"> Dashboard </Link>
        </NavItem>
        <NavExpandable
          title="APIs"
          groupId="grp-2"
          isActive={activeMenuGroup === "grp-2"}
        >
          <NavItem
            groupId="grp-2"
            itemId="grp-2_itm-1"
            isActive={activeMenuGroupItem === "grp-2_itm-1"}
          >
            <Link to="/apis">View All APIs</Link>
          </NavItem>
          <NavItem
            groupId="grp-2"
            itemId="grp-2_itm-2"
            isActive={activeMenuGroupItem === "grp-2_itm-2"}
          >
            <Link to="/apis/create">Create New API</Link>
          </NavItem>
          <NavItem
            groupId="grp-3"
            itemId="grp-2_itm-3"
            isActive={activeMenuGroupItem === "grp-2_itm-3"}
          >
            <Link to="/apis/import">Import API</Link>
          </NavItem>
        </NavExpandable>
        <NavExpandable
          title="Settings"
          groupId="grp-3"
          isActive={activeMenuGroup === "grp-3"}
        >
          <NavItem
            groupId="grp-3"
            itemId="grp-3_itm-1"
            isActive={activeMenuGroupItem === "grp-3_itm-1"}
          >
            <Link to="/settings/profile">User Profile</Link>
          </NavItem>
          <NavItem
            groupId="grp-3"
            itemId="grp-3_itm-2"
            isActive={activeMenuGroupItem === "grp-3_itm-2"}
          >
            <Link to="/settings/accounts">Linked Accounts</Link>
          </NavItem>
          <NavItem
            groupId="grp-3"
            itemId="grp-3_itm-3"
            isActive={activeMenuGroupItem === "grp-3_itm-3"}
          >
            <Link to="/settings/validation">Validation</Link>
          </NavItem>
        </NavExpandable>
      </NavList>
    </Nav>
  );
  return <PageSidebar nav={sideBarNavigation} />;
};

export default AppSidebar;
