import React, { createContext, FunctionComponent, ReactNode, useCallback, useContext, useMemo, useState } from "react";
import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { getRegistryClient } from "@utils/rest.utils.ts";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { UserInfo } from "@sdk/lib/generated-client/models";

const DEFAULT_USER_INFO: UserInfo = {
    username: "",
    displayName: "",
    admin: false,
    developer: false,
    viewer: false
};

const isRbacEnabled = (config: ConfigService): boolean => {
    return config.authRbacEnabled();
};

const isObacEnabled = (config: ConfigService): boolean => {
    return config.authObacEnabled();
};

const isUserAdmin = (config: ConfigService, auth: AuthService, currentUserInfo: UserInfo): boolean => {
    if (!auth.isOidcAuthEnabled() && !auth.isBasicAuthEnabled()) {
        return true;
    }
    if (!isRbacEnabled(config) && !isObacEnabled(config)) {
        return true;
    }
    return currentUserInfo.admin || false;
};

const isUserDeveloper = (config: ConfigService, auth: AuthService, currentUserInfo: UserInfo, resourceOwner?: string): boolean => {
    if (!auth.isOidcAuthEnabled() && !auth.isBasicAuthEnabled()) {
        return true;
    }
    if (!isRbacEnabled(config) && !isObacEnabled(config)) {
        return true;
    }
    if (isUserAdmin(config, auth, currentUserInfo)) {
        return true;
    }
    if (isRbacEnabled(config) && !currentUserInfo.developer) {
        return false;
    }
    if (isObacEnabled(config) && resourceOwner && currentUserInfo.username !== resourceOwner) {
        return false;
    }
    return true;
};

const isUserId = (currentUserInfo: UserInfo, userId: string): boolean => {
    return currentUserInfo.username === userId;
};


export interface UserService {
    currentUser(): UserInfo;
    updateCurrentUser(): Promise<UserInfo>;
    isUserAdmin(): boolean;
    isUserDeveloper(resourceOwner?: string | null): boolean;
    isUserId(userId: string): boolean;
}

const UserContext = createContext<UserService | null>(null);

export type UserProviderProps = {
    children?: ReactNode;
};

export const UserProvider: FunctionComponent<UserProviderProps> = (props: UserProviderProps) => {
    const auth: AuthService = useAuth();
    const config: ConfigService = useConfigService();
    const [currentUserInfo, setCurrentUserInfo] = useState<UserInfo>(DEFAULT_USER_INFO);

    const updateCurrentUser = useCallback(async (): Promise<UserInfo> => {
        const isAuthenticated: boolean = await auth.isAuthenticated();
        if (isAuthenticated) {
            const userInfo = await getRegistryClient(config, auth).users.me.get();
            setCurrentUserInfo(userInfo!);
            return userInfo!;
        }
        return DEFAULT_USER_INFO;
    }, [auth, config]);

    const userService: UserService = useMemo(() => ({
        currentUser(): UserInfo {
            return currentUserInfo;
        },
        updateCurrentUser(): Promise<UserInfo> {
            return updateCurrentUser();
        },
        isUserId(userId: string): boolean {
            return isUserId(currentUserInfo, userId);
        },
        isUserAdmin(): boolean {
            return isUserAdmin(config, auth, currentUserInfo);
        },
        isUserDeveloper(resourceOwner?: string): boolean {
            return isUserDeveloper(config, auth, currentUserInfo, resourceOwner);
        }
    }), [auth, config, currentUserInfo, updateCurrentUser]);

    return React.createElement(
        UserContext.Provider,
        { value: userService },
        props.children
    );
};

export const useUserService: () => UserService = (): UserService => {
    const userService = useContext(UserContext);
    if (!userService) {
        throw new Error("useUserService must be used within a UserProvider");
    }
    return userService;
};
