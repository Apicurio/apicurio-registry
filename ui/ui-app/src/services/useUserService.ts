import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { getRegistryClient } from "@utils/rest.utils.ts";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { UserInfo } from "@sdk/lib/generated-client/models";

let currentUserInfo: UserInfo = {
    username: "",
    displayName: "",
    admin: false,
    developer: false,
    viewer: false
};


const currentUser = (): UserInfo => {
    return currentUserInfo;
};

const updateCurrentUser = async (config: ConfigService, auth: AuthService): Promise<UserInfo> => {
    const isAuthenticated: boolean = await auth.isAuthenticated();
    if (isAuthenticated) {
        return getRegistryClient(config, auth).users.me.get().then(userInfo => {
            currentUserInfo = userInfo!;
            return userInfo!;
        });
    } else {
        return Promise.resolve(currentUserInfo);
    }
};

const isRbacEnabled = (config: ConfigService): boolean => {
    return config.authRbacEnabled();
};

const isObacEnabled = (config: ConfigService): boolean => {
    return config.authObacEnabled();
};

const isUserAdmin = (config: ConfigService, auth: AuthService): boolean => {
    if (!auth.isOidcAuthEnabled() && !auth.isBasicAuthEnabled()) {
        return true;
    }
    if (!isRbacEnabled(config) && !isObacEnabled(config)) {
        return true;
    }
    return currentUser().admin || false;
};

const isUserDeveloper = (config: ConfigService, auth: AuthService, resourceOwner?: string): boolean => {
    if (!auth.isOidcAuthEnabled() && !auth.isBasicAuthEnabled()) {
        return true;
    }
    if (!isRbacEnabled(config) && !isObacEnabled(config)) {
        return true;
    }
    if (isUserAdmin(config, auth)) {
        return true;
    }
    if (isRbacEnabled(config) && !currentUser().developer) {
        return false;
    }
    if (isObacEnabled(config) && resourceOwner && currentUser().username !== resourceOwner) {
        return false;
    }
    return true;
};

const isUserId = (userId: string): boolean => {
    return currentUser().username === userId;
};


export interface UserService {
    currentUser(): UserInfo;
    updateCurrentUser(): Promise<UserInfo>;
    isUserAdmin(): boolean;
    isUserDeveloper(resourceOwner?: string | null): boolean;
    isUserId(userId: string): boolean;
}


export const useUserService: () => UserService = (): UserService => {
    const auth: AuthService = useAuth();
    const config: ConfigService = useConfigService();

    return {
        currentUser,
        updateCurrentUser(): Promise<UserInfo> {
            return updateCurrentUser(config, auth);
        },
        isUserId(userId: string): boolean {
            return isUserId(userId);
        },
        isUserAdmin(): boolean {
            return isUserAdmin(config, auth);
        },
        isUserDeveloper(resourceOwner?: string): boolean {
            return isUserDeveloper(config, auth, resourceOwner);
        }
    };
};
