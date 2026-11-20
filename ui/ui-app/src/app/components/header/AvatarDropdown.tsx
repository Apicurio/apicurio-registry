import React, { FunctionComponent, useEffect, useState } from "react";
import { Avatar, Dropdown, DropdownItem, DropdownList, MenuToggle, MenuToggleElement } from "@patternfly/react-core";
import { AuthService, useAuth } from "@apitomy/common-ui-components";
import { UserService, useUserService } from "@services/useUserService.ts";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { UserInfo } from "@sdk/lib/generated-client/models";


export type AvatarDropdownProps = object;

const displayName = (info: UserInfo | undefined): string => info?.displayName || info?.username || "";


export const AvatarDropdown: FunctionComponent<AvatarDropdownProps> = () => {
    const [isOpen, setIsOpen] = useState(false);

    const config: ConfigService = useConfigService();
    const auth: AuthService = useAuth();
    const user: UserService = useUserService();
    const [userName, setUserName] = useState<string>(displayName(user.currentUser()));

    useEffect(() => {
        // The masthead can render before the user is loaded, so it loads them itself
        user.updateCurrentUser().then(info => setUserName(displayName(info))).catch(() => {});
    }, []);

    const avatarSrc: string = `${config.uiContextPath() || "/"}avatar.png`;

    const onSelect = (): void => {
        setIsOpen(!isOpen);
    };

    const onToggle = (): void => {
        setIsOpen(!isOpen);
    };

    const icon = (
        <Avatar src={avatarSrc} alt="User" />
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
                        userName || "User"
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
                        auth.logout();
                        ev.preventDefault();
                    }}
                >
                    Logout
                </DropdownItem>
            </DropdownList>
        </Dropdown>
    );

};
