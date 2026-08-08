import { describe, expect, it } from "vitest";

// useConfigService.ts reads ApicurioRegistryConfig at module load time.
// We set up the global before the import so the module initialises correctly.
function makeGlobal(uiOverrides?: Record<string, unknown>) {
    const config = {
        artifacts: { url: "http://localhost:8080/apis/registry/v3/" },
        ...(uiOverrides !== undefined ? { ui: uiOverrides } : {})
    };
    (globalThis as any).ApicurioRegistryConfig = config;
    (globalThis as any).window = { ApicurioRegistryConfig: config };
    return config;
}

const DEFAULT_MAX = 5242880; // 5 MB

describe("ConfigServiceImpl.uiImportMaxContentLength()", () => {

    it("returns the 5 MB default when ui config is absent", async () => {
        makeGlobal();
        const { ConfigServiceImpl } = await import("./useConfigService.ts");
        const svc = new ConfigServiceImpl();
        expect(svc.uiImportMaxContentLength()).toBe(DEFAULT_MAX);
    });

    it("returns the 5 MB default when importMaxContentLength is undefined", async () => {
        makeGlobal({ importMaxContentLength: undefined });
        const { ConfigServiceImpl } = await import("./useConfigService.ts");
        const svc = new ConfigServiceImpl();
        expect(svc.uiImportMaxContentLength()).toBe(DEFAULT_MAX);
    });

    it("returns the 5 MB default when importMaxContentLength is null", async () => {
        makeGlobal({ importMaxContentLength: null });
        const { ConfigServiceImpl } = await import("./useConfigService.ts");
        const svc = new ConfigServiceImpl();
        expect(svc.uiImportMaxContentLength()).toBe(DEFAULT_MAX);
    });

    it("returns the 5 MB default when importMaxContentLength is 0", async () => {
        makeGlobal({ importMaxContentLength: 0 });
        const { ConfigServiceImpl } = await import("./useConfigService.ts");
        const svc = new ConfigServiceImpl();
        expect(svc.uiImportMaxContentLength()).toBe(DEFAULT_MAX);
    });

    it("returns the 5 MB default when importMaxContentLength is negative", async () => {
        makeGlobal({ importMaxContentLength: -1 });
        const { ConfigServiceImpl } = await import("./useConfigService.ts");
        const svc = new ConfigServiceImpl();
        expect(svc.uiImportMaxContentLength()).toBe(DEFAULT_MAX);
    });

    it("returns the 5 MB default when importMaxContentLength is NaN", async () => {
        makeGlobal({ importMaxContentLength: NaN });
        const { ConfigServiceImpl } = await import("./useConfigService.ts");
        const svc = new ConfigServiceImpl();
        expect(svc.uiImportMaxContentLength()).toBe(DEFAULT_MAX);
    });

    it("returns the 5 MB default when importMaxContentLength is a non-numeric string", async () => {
        makeGlobal({ importMaxContentLength: "not-a-number" });
        const { ConfigServiceImpl } = await import("./useConfigService.ts");
        const svc = new ConfigServiceImpl();
        expect(svc.uiImportMaxContentLength()).toBe(DEFAULT_MAX);
    });

    it("returns a configured positive number value", async () => {
        makeGlobal({ importMaxContentLength: 10485760 }); // 10 MB
        const { ConfigServiceImpl } = await import("./useConfigService.ts");
        const svc = new ConfigServiceImpl();
        expect(svc.uiImportMaxContentLength()).toBe(10485760);
    });

    it("coerces a numeric string to a number", async () => {
        makeGlobal({ importMaxContentLength: "10485760" }); // string from JSON
        const { ConfigServiceImpl } = await import("./useConfigService.ts");
        const svc = new ConfigServiceImpl();
        expect(svc.uiImportMaxContentLength()).toBe(10485760);
    });

});
