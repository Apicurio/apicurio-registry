import { describe, expect, it } from "vitest";
import { VersionState, VersionStateObject } from "@sdk/lib/generated-client/models";
import { stateToLabel } from "./VersionStateUtils";


describe("stateToLabel", () => {
    const cases: { state: VersionState | undefined; label: string }[] = [
        { state: VersionStateObject.ENABLED, label: "Enabled" },
        { state: VersionStateObject.DISABLED, label: "Disabled" },
        { state: VersionStateObject.DEPRECATED, label: "Deprecated" },
        { state: VersionStateObject.DRAFT, label: "Draft" },
        { state: VersionStateObject.SUNSET, label: "Sunset" },
        { state: undefined, label: "Unknown" }
    ];

    it.each(cases)("labels $state as $label", ({ state, label }) => {
        expect(stateToLabel(state)).toBe(label);
    });
});
