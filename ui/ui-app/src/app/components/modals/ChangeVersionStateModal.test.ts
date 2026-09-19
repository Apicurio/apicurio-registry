import { describe, expect, it } from "vitest";
import { VersionStateObject } from "@sdk/lib/generated-client/models";
import { stateToLabel } from "./VersionStateUtils";


describe("stateToLabel", () => {
    it("labels the SUNSET state", () => {
        expect(stateToLabel(VersionStateObject.SUNSET)).toBe("Sunset");
    });
});
