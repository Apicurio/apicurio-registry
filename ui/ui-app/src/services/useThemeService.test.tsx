import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";

let currentThemeState: any = undefined;
const themeSetter: any = vi.fn();
let layoutEffectCallback: any = undefined;

vi.mock("react", async (importOriginal) => {
    const actual = await importOriginal<typeof import("react")>();
    return {
        ...actual,
        useState: vi.fn((init: any) => {
            if (currentThemeState === undefined) {
                currentThemeState = typeof init === "function" ? init() : init;
            }
            return [currentThemeState, themeSetter];
        }),
        useLayoutEffect: vi.fn((effect: any) => {
            layoutEffectCallback = effect;
        })
    };
});

// Mock useLocalStorageService
const mockLocalStorage = {
    getConfigProperty: vi.fn(),
    setConfigProperty: vi.fn(),
};
vi.mock("./useLocalStorageService", () => ({
    useLocalStorageService: () => mockLocalStorage
}));

// Mock useConfigService
const mockConfigService = {
    uiContextPath: vi.fn().mockReturnValue("/"),
};
vi.mock("./useConfigService", () => ({
    useConfigService: () => mockConfigService
}));

// Mock document.documentElement
const mockClassList = {
    add: vi.fn(),
    remove: vi.fn(),
};
globalThis.document = {
    documentElement: {
        classList: mockClassList
    }
} as any;

import { ThemeProvider } from "./useThemeService";

describe("useThemeService & ThemeProvider", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        currentThemeState = undefined;
        themeSetter.mockImplementation((next: any) => {
            currentThemeState = typeof next === "function" ? next(currentThemeState) : next;
        });
        layoutEffectCallback = undefined;
    });

    afterEach(() => {
        delete (globalThis as any).window;
    });

    it("seeds default theme from localStorage when present", () => {
        mockLocalStorage.getConfigProperty.mockReturnValue("dark");
        const element: any = ThemeProvider({ children: null });
        expect(mockLocalStorage.getConfigProperty).toHaveBeenCalledWith("theme", undefined);
        expect(element.props.value.theme).toBe("dark");
        expect(element.props.value.isDark).toBe(true);
    });

    it("falls back to prefers-color-scheme: dark when localStorage is empty", () => {
        mockLocalStorage.getConfigProperty.mockReturnValue(undefined);
        globalThis.window = {
            matchMedia: vi.fn().mockReturnValue({ matches: true })
        } as any;

        const element: any = ThemeProvider({ children: null });
        expect(element.props.value.theme).toBe("dark");
        expect(element.props.value.isDark).toBe(true);
    });

    it("falls back to light when localStorage and prefers-color-scheme are not dark", () => {
        mockLocalStorage.getConfigProperty.mockReturnValue(undefined);
        globalThis.window = {
            matchMedia: vi.fn().mockReturnValue({ matches: false })
        } as any;

        const element: any = ThemeProvider({ children: null });
        expect(element.props.value.theme).toBe("light");
        expect(element.props.value.isDark).toBe(false);
    });

    it("toggles theme correctly and persists it", () => {
        mockLocalStorage.getConfigProperty.mockReturnValue("light");
        const element: any = ThemeProvider({ children: null });
        expect(element.props.value.theme).toBe("light");

        element.props.value.toggleTheme();
        expect(themeSetter).toHaveBeenCalledWith("dark");
        expect(mockLocalStorage.setConfigProperty).toHaveBeenCalledWith("theme", "dark");
    });

    it("applies class to document element on layout effect when theme is dark", () => {
        mockLocalStorage.getConfigProperty.mockReturnValue("dark");
        ThemeProvider({ children: null });
        expect(layoutEffectCallback).toBeDefined();

        layoutEffectCallback();
        expect(mockClassList.add).toHaveBeenCalledWith("pf-v6-theme-dark");
        expect(mockClassList.remove).not.toHaveBeenCalled();
    });

    it("removes class from document element on layout effect when theme is light", () => {
        mockLocalStorage.getConfigProperty.mockReturnValue("light");
        ThemeProvider({ children: null });
        expect(layoutEffectCallback).toBeDefined();

        layoutEffectCallback();
        expect(mockClassList.remove).toHaveBeenCalledWith("pf-v6-theme-dark");
        expect(mockClassList.add).not.toHaveBeenCalled();
    });
});
