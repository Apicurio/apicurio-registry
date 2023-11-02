import React, { FunctionComponent, useState } from "react";
import "./AvatarDropdown.css";
import {
    Avatar,
    Dropdown,
    DropdownItem,
    DropdownList,
    MenuToggle,
    MenuToggleElement
} from "@patternfly/react-core";
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

    return (
        <Dropdown
            isOpen={isOpen}
            onSelect={onSelect}
            onOpenChange={(isOpen: boolean) => setIsOpen(isOpen)}
            popperProps={{
                position: "right"
            }}
            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                <MenuToggle
                    ref={toggleRef}
                    aria-label="kebab dropdown toggle"
                    variant="plain"
                    onClick={onToggle}
                    isExpanded={isOpen}
                >
                    <Avatar src="/avatar.png" alt="User" />
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
