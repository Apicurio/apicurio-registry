import { NavigateFunction, useNavigate } from "react-router";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { effectiveNavPrefixPath as resolvePrefix } from "@services/useAppNavigation.utils.ts";

const effectiveNavPrefixPath = (config: ConfigService): string => {
    return resolvePrefix(config.uiNavPrefixPath() || "", config.uiContextPath() || "");
};

const navigateTo = (config: ConfigService, path: string, navigateFunc: NavigateFunction): void => {
    const prefix: string = effectiveNavPrefixPath(config);
    const to: string = `${prefix}${path}`;
    console.debug("[UseAppNavigation] Navigating to: ", to);
    setTimeout(() => {
        navigateFunc(to);
    }, 20);
};

export type AppNavigation = {
    navigateTo: (path: string) => void;
    createLink: (path: string) => string;
};

export const useAppNavigation: () => AppNavigation = (): AppNavigation => {
    const navigate: NavigateFunction = useNavigate();
    const config: ConfigService = useConfigService();

    return {
        navigateTo: (path: string) => {
            return navigateTo(config, path, navigate);
        },
        createLink: (path: string) => {
            const prefix: string = effectiveNavPrefixPath(config);
            return `${prefix}${path}`;
        },
    };
};
