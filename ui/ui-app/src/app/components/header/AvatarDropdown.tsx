import React, { FunctionComponent, useState } from "react";
import { Avatar, Dropdown, DropdownItem, DropdownList, MenuToggle, MenuToggleElement } from "@patternfly/react-core";
import { Services } from "@services/services.ts";


export type AvatarDropdownProps = {
    // No props
};


export const AvatarDropdown: FunctionComponent<AvatarDropdownProps> = () => {
    const [isOpen, setIsOpen] = useState(false);

    const onSelect = (): void => {
        setIsOpen(!isOpen);
    };

    const onToggle = (): void => {
        setIsOpen(!isOpen);
    };

    const icon = (
        <Avatar src="/avatar.png" alt="User" />
    );

    return (
        <Dropdown
            isOpen={isOpen}
            onSelect={onSelect}
            onOpenChange={(isOpen: boolean) => setIsOpen(isOpen)}
            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                <MenuToggle
                    ref={toggleRef}
                    onClick={onToggle}
                    isFullHeight={true}
                    isExpanded={isOpen}
                    icon={icon}
                >
                    {
                        Services.getUsersService().currentUser().displayName || "User"
                    }
                </MenuToggle>
            )}
            shouldFocusToggleOnSelect
        >
            <DropdownList>
                <DropdownItem
                    key="link"
                    // Prevent the default onClick functionality for example purposes
                    onClick={(ev: any) => {
                        Services.getAuthService().doLogout();
                        ev.preventDefault();
                    }}
                >
                    Logout
                </DropdownItem>
            </DropdownList>
        </Dropdown>
    );

};
