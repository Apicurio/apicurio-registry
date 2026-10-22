import { describe, expect, it } from "vitest";
import { buildNonDraftVersionLink } from "./EditorPage.utils.ts";

describe("buildNonDraftVersionLink", () => {

    it("builds the version page path from groupId, artifactId, and version", () => {
        const link = buildNonDraftVersionLink("default", "my-artifact", "1");
        expect(link).toBe("/explore/default/my-artifact/versions/1");
    });

});
