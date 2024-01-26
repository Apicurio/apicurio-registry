import { UserInfo } from "@models/userInfo.model.ts";
import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { createEndpoint, createHeaders, createOptions, httpGet } from "@utils/rest.utils.ts";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";

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
        // TODO cache the response for a few minutes to limit the # of times this is called per minute??
        const endpoint: string = createEndpoint(config.artifactsUrl(), "/users/me");
        const token: string | undefined = await auth.getToken();
        const options = createOptions(createHeaders(token));
        return httpGet<UserInfo>(endpoint, options).then(userInfo => {
            currentUserInfo = userInfo;
            return userInfo;
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
    if (!auth.isAuthEnabled()) {
        return true;
    }
    if (!isRbacEnabled(config) && !isObacEnabled(config)) {
        return true;
    }
    return currentUser().admin || false;
};

const isUserDeveloper = (config: ConfigService, auth: AuthService, resourceOwner?: string): boolean => {
    if (!auth.isAuthEnabled()) {
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
    isUserDeveloper(resourceOwner?: string): boolean;
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
