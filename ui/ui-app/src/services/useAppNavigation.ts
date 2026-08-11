import { NavigateFunction, useNavigate } from "react-router";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";

// React Router's <Router basename={contextPath}> already prepends contextPath
// to every navigation automatically. If navPrefixPath is also configured with
// a value that overlaps with contextPath, applying both would double-prefix
// the resulting URL (e.g. "/registry/registry/dashboard"), which does not
// match any route. This resolves the effective prefix so it is only applied
// once.
const effectiveNavPrefixPath = (config: ConfigService): string => {
    const prefix: string = config.uiNavPrefixPath() || "";
    const basename: string = config.uiContextPath() || "";
    if (prefix !== "" && prefix === basename) {
        // Router's basename already accounts for this; avoid double-prefixing.
        return "";
    }
    return prefix;
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
