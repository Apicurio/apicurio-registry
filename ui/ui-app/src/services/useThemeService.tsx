/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, useLayoutEffect, ReactNode } from "react";
import { useLocalStorageService } from "./useLocalStorageService";
import { useConfigService } from "./useConfigService";

export type ThemeType = "light" | "dark";

export interface ThemeContextProps {
    theme: ThemeType;
    isDark: boolean;
    toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextProps | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    const localStorageService = useLocalStorageService();
    const [theme, setTheme] = useState<ThemeType>(() => {
        const stored = localStorageService.getConfigProperty("theme", undefined) as ThemeType | undefined;
        if (stored) {
            return stored;
        }
        if (typeof window !== "undefined" && window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
            return "dark";
        }
        return "light";
    });

    const toggleTheme = () => {
        const nextTheme = theme === "light" ? "dark" : "light";
        setTheme(nextTheme);
        localStorageService.setConfigProperty("theme", nextTheme);
    };

    useLayoutEffect(() => {
        const htmlElement = document.documentElement;
        if (theme === "dark") {
            htmlElement.classList.add("pf-v6-theme-dark");
        } else {
            htmlElement.classList.remove("pf-v6-theme-dark");
        }
    }, [theme]);

    return (
        <ThemeContext.Provider value={{ theme, isDark: theme === "dark", toggleTheme }}>
            {children}
        </ThemeContext.Provider>
    );
};

export const useThemeService = (): ThemeContextProps => {
    const context = useContext(ThemeContext);
    if (!context) {
        throw new Error("useThemeService must be used within a ThemeProvider");
    }
    return context;
};

export const useLogoSrc = (): string => {
    const config = useConfigService();
    const { isDark } = useThemeService();
    return `${config.uiContextPath() || "/"}${isDark ? "apicurio_registry_logo_reverse.svg" : "apicurio_registry_logo_default.svg"}`;
};
