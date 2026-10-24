import { describe, expect, it, vi, beforeEach } from "vitest";

let useStateValue: any = undefined;
const setStateFn = vi.fn();

vi.mock("react", async (importOriginal) => {
    const actual = await importOriginal<typeof import("react")>();
    return {
        ...actual,
        useState: vi.fn((init: any) => {
            if (useStateValue === undefined) {
                useStateValue = typeof init === "function" ? init() : init;
            }
            return [useStateValue, setStateFn];
        }),
    };
});

const mockConfigService = {
    featureReadOnly: vi.fn(),
    featureReadOnlyReason: vi.fn(),
    featureReadOnlyBannerEnabled: vi.fn(),
    featureReadOnlyBannerText: vi.fn(),
    features: vi.fn(),
};
vi.mock("@services/useConfigService.ts", () => ({
    useConfigService: () => mockConfigService,
}));

const mockLocalStorage = {
    getConfigProperty: vi.fn(),
    setConfigProperty: vi.fn(),
};
vi.mock("@services/useLocalStorageService.ts", () => ({
    useLocalStorageService: () => mockLocalStorage,
}));

vi.mock("@patternfly/react-core", () => ({
    Alert: (props: any) => props,
    AlertActionCloseButton: (props: any) => props,
}));

import { ReadOnlyBanner } from "./ReadOnlyBanner";

describe("ReadOnlyBanner", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        useStateValue = undefined;
        mockLocalStorage.getConfigProperty.mockReturnValue(undefined);
        mockConfigService.featureReadOnlyBannerEnabled.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerText.mockReturnValue(undefined);
    });

    it("returns null when registry is not read-only", () => {
        mockConfigService.featureReadOnly.mockReturnValue(false);
        mockConfigService.featureReadOnlyReason.mockReturnValue(undefined);
        mockConfigService.features.mockReturnValue({});
        const result = ReadOnlyBanner({});
        expect(result).toBeNull();
    });

    it("shows GitOps message when readOnlyReason is storage and storageName is gitops", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyReason.mockReturnValue("storage");
        mockConfigService.features.mockReturnValue({ storageName: "gitops" });

        const result: any = ReadOnlyBanner({});
        expect(result.props.title).toBe(
            "This registry is in read-only mode. Changes are managed via GitOps — update your source Git repository."
        );
        expect(result.props.variant).toBe("info");
    });

    it("shows generic message when readOnlyReason is storage but storageName is sql", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyReason.mockReturnValue("storage");
        mockConfigService.features.mockReturnValue({ storageName: "sql" });

        const result: any = ReadOnlyBanner({});
        expect(result.props.title).toBe("This registry is in read-only mode.");
    });

    it("shows generic message when readOnlyReason is config", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyReason.mockReturnValue("config");
        mockConfigService.features.mockReturnValue({ storageName: undefined });

        const result: any = ReadOnlyBanner({});
        expect(result.props.title).toBe("This registry is in read-only mode.");
    });

    it("returns null when banner was previously dismissed for the same message", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyReason.mockReturnValue("config");
        mockConfigService.features.mockReturnValue({ storageName: undefined });
        mockLocalStorage.getConfigProperty.mockReturnValue("true");

        const result = ReadOnlyBanner({});
        expect(result).toBeNull();
    });

    it("shows banner when dismissed message differs from current message", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyReason.mockReturnValue("storage");
        mockConfigService.features.mockReturnValue({ storageName: "gitops" });
        mockLocalStorage.getConfigProperty.mockReturnValue(undefined);

        const result: any = ReadOnlyBanner({});
        expect(result).not.toBeNull();
        expect(result.props.title).toContain("GitOps");
    });

    it("persists dismissal to localStorage on close", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyReason.mockReturnValue("config");
        mockConfigService.features.mockReturnValue({ storageName: undefined });
        mockLocalStorage.getConfigProperty.mockReturnValue(undefined);

        const result: any = ReadOnlyBanner({});
        const closeButton = result.props.actionClose;
        closeButton.props.onClose();

        expect(mockLocalStorage.setConfigProperty).toHaveBeenCalledWith(
            expect.stringContaining("readOnlyBanner.dismissed."),
            "true"
        );
        expect(setStateFn).toHaveBeenCalledWith(true);
    });

    it("returns null when featureReadOnlyBannerEnabled is false", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerEnabled.mockReturnValue(false);
        mockConfigService.featureReadOnlyReason.mockReturnValue("config");
        mockConfigService.features.mockReturnValue({ storageName: undefined });

        const result = ReadOnlyBanner({});
        expect(result).toBeNull();
    });

    it("shows custom text when configured, overriding GitOps default message", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerEnabled.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerText.mockReturnValue("Custom banner text for all reasons");
        mockConfigService.featureReadOnlyReason.mockReturnValue("storage");
        mockConfigService.features.mockReturnValue({ storageName: "gitops" });

        const result: any = ReadOnlyBanner({});
        expect(result.props.title).toBe("Custom banner text for all reasons");
        expect(result.props.variant).toBe("info");
    });

    it("shows custom text when configured, overriding config default message", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerEnabled.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerText.mockReturnValue("Custom banner text for all reasons");
        mockConfigService.featureReadOnlyReason.mockReturnValue("config");
        mockConfigService.features.mockReturnValue({ storageName: undefined });

        const result: any = ReadOnlyBanner({});
        expect(result.props.title).toBe("Custom banner text for all reasons");
    });

    it("falls back to default message when custom text is unset or whitespace", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerEnabled.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerText.mockReturnValue("   ");
        mockConfigService.featureReadOnlyReason.mockReturnValue("config");
        mockConfigService.features.mockReturnValue({ storageName: undefined });

        const result: any = ReadOnlyBanner({});
        expect(result.props.title).toBe("This registry is in read-only mode.");
    });

    it("resets dismissal and shows banner when custom text changes", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerEnabled.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerText.mockReturnValue("New message text");
        mockLocalStorage.getConfigProperty.mockReturnValue(undefined);

        const result: any = ReadOnlyBanner({});
        expect(result).not.toBeNull();
        expect(result.props.title).toBe("New message text");
    });

    it("returns null when custom text was previously dismissed", () => {
        mockConfigService.featureReadOnly.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerEnabled.mockReturnValue(true);
        mockConfigService.featureReadOnlyBannerText.mockReturnValue("Custom banner text");
        mockLocalStorage.getConfigProperty.mockReturnValue("true");

        const result = ReadOnlyBanner({});
        expect(result).toBeNull();
    });
});
