import { NavigateFunction, useNavigate } from "react-router-dom";
import { Services } from "@services/services.ts";

export const navigateTo: (path: string, navigateFunc: NavigateFunction) => void = (path: string, navigateFunc: NavigateFunction) => {
    const prefix: string = Services.getConfigService().uiNavPrefixPath() || "";
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

    return {
        navigateTo: (path: string) => {
            return navigateTo(path, navigate);
        },
        createLink: (path: string) => {
            const prefix: string = Services.getConfigService().uiNavPrefixPath() || "";
            return `${prefix}${path}`;
        },
    };
};
