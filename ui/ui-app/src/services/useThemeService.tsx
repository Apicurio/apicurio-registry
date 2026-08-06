/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, useEffect, ReactNode } from "react";
import { useLocalStorageService } from "./useLocalStorageService";

export type ThemeType = "light" | "dark";

export interface ThemeContextProps {
    theme: ThemeType;
    isDark: boolean;
    toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextProps | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    const localStorageService = useLocalStorageService();
    const storedTheme = localStorageService.getConfigProperty("theme", "light") as ThemeType;
    const [theme, setTheme] = useState<ThemeType>(storedTheme);

    const toggleTheme = () => {
        const nextTheme = theme === "light" ? "dark" : "light";
        setTheme(nextTheme);
        localStorageService.setConfigProperty("theme", nextTheme);
    };

    useEffect(() => {
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
