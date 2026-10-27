// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { shash } from "@utils/string.utils.ts";

const DISMISSED_KEY_PREFIX = "readOnlyBanner.dismissed.";
const GENERIC_MESSAGE = "This registry is in read-only mode.";
const GITOPS_MESSAGE =
    "This registry is in read-only mode. Changes are managed via GitOps — update your source Git repository.";

const mockConfigService = {
    featureReadOnly: vi.fn(),
    featureReadOnlyReason: vi.fn(),
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

import { ReadOnlyBanner } from "./ReadOnlyBanner";

function setupConfig(readOnly: boolean, reason?: string, storageName?: string): void {
    mockConfigService.featureReadOnly.mockReturnValue(readOnly);
    mockConfigService.featureReadOnlyReason.mockReturnValue(reason);
    mockConfigService.features.mockReturnValue({ storageName });
}

function dismissKeyFor(message: string): string {
    return DISMISSED_KEY_PREFIX + String(shash(message));
}

afterEach(() => {
    cleanup();
    vi.resetAllMocks();
});

describe("ReadOnlyBanner", () => {
    it("returns null when registry is not read-only", () => {
        setupConfig(false);
        const { container } = render(<ReadOnlyBanner />);
        expect(container.innerHTML).toBe("");
    });

    it("shows GitOps message when readOnlyReason is storage and storageName is gitops", () => {
        setupConfig(true, "storage", "gitops");
        render(<ReadOnlyBanner />);
        expect(screen.getByTestId("read-only-banner")).toBeInTheDocument();
        expect(screen.getByText(GITOPS_MESSAGE)).toBeInTheDocument();
    });

    it("shows generic message when readOnlyReason is storage but storageName is sql", () => {
        setupConfig(true, "storage", "sql");
        render(<ReadOnlyBanner />);
        expect(screen.getByText(GENERIC_MESSAGE)).toBeInTheDocument();
    });

    it("shows generic message when readOnlyReason is config", () => {
        setupConfig(true, "config");
        render(<ReadOnlyBanner />);
        expect(screen.getByText(GENERIC_MESSAGE)).toBeInTheDocument();
    });

    it("returns null when banner was previously dismissed for the same message", () => {
        setupConfig(true, "config");
        mockLocalStorage.getConfigProperty.mockReturnValue("true");
        const { container } = render(<ReadOnlyBanner />);
        expect(container.innerHTML).toBe("");
    });

    it("shows banner when generic message is dismissed but GitOps message is not", () => {
        setupConfig(true, "storage", "gitops");
        const genericKey = dismissKeyFor(GENERIC_MESSAGE);
        const gitopsKey = dismissKeyFor(GITOPS_MESSAGE);
        mockLocalStorage.getConfigProperty.mockImplementation((key: string) =>
            key === genericKey ? "true" : undefined
        );
        render(<ReadOnlyBanner />);
        expect(mockLocalStorage.getConfigProperty).toHaveBeenCalledWith(gitopsKey, undefined);
        expect(screen.getByText(GITOPS_MESSAGE)).toBeInTheDocument();
    });

    it("uses different dismissal keys for different messages", () => {
        const genericKey = dismissKeyFor(GENERIC_MESSAGE);
        const gitopsKey = dismissKeyFor(GITOPS_MESSAGE);
        expect(genericKey).not.toBe(gitopsKey);

        setupConfig(true, "config");
        render(<ReadOnlyBanner />);
        fireEvent.click(screen.getByRole("button"));
        expect(mockLocalStorage.setConfigProperty).toHaveBeenCalledWith(genericKey, "true");
        cleanup();
        vi.resetAllMocks();

        setupConfig(true, "storage", "gitops");
        render(<ReadOnlyBanner />);
        fireEvent.click(screen.getByRole("button"));
        expect(mockLocalStorage.setConfigProperty).toHaveBeenCalledWith(gitopsKey, "true");
    });

    it("hides banner and persists dismissal on close", () => {
        setupConfig(true, "config");
        render(<ReadOnlyBanner />);
        expect(screen.getByText(GENERIC_MESSAGE)).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button"));

        expect(mockLocalStorage.setConfigProperty).toHaveBeenCalledWith(
            dismissKeyFor(GENERIC_MESSAGE),
            "true"
        );
        expect(screen.queryByText(GENERIC_MESSAGE)).not.toBeInTheDocument();
    });
});
