import { NavigateFunction, useNavigate } from "react-router-dom";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";

const navigateTo = (config: ConfigService, path: string, navigateFunc: NavigateFunction): void => {
    const prefix: string = config.uiNavPrefixPath() || "";
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
            const prefix: string = config.uiNavPrefixPath() || "";
            return `${prefix}${path}`;
        },
    };
};
