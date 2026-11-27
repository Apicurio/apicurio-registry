import { describe, expect, it } from "vitest";
import { buildContentMarkers } from "./marker.utils";

const PROPOSED: string = JSON.stringify({
    type: "object",
    properties: {
        name: { type: "string", maxLength: 5 },
        age: { type: "integer" }
    }
}, null, 2);

describe("buildContentMarkers", () => {
    it("places an exact issue on its keyword", () => {
        const result = buildContentMarkers(PROPOSED, [
            { pointer: "/properties/name/maxLength", message: "String type max length decreased" }
        ]);
        expect(result.unlocated).toEqual([]);
        expect(result.markers).toEqual([{
            startLineNumber: 6,
            startColumn: 7,
            endLineNumber: 6,
            endColumn: 21,
            message: "String type max length decreased"
        }]);
    });

    it("places a partial issue on the deepest resolved node and keeps the full pointer in the message", () => {
        const result = buildContentMarkers(PROPOSED, [
            { pointer: "/properties/name/minLength", message: "String type min length increased" }
        ]);
        expect(result.markers).toHaveLength(1);
        expect(result.markers[0].startLineNumber).toBe(4);
        expect(result.markers[0].message).toBe("String type min length increased (at /properties/name/minLength)");
    });

    it("lists an issue that resolves nowhere below the root instead of highlighting everything", () => {
        const issue = { pointer: "/pets/{id}", message: "Missing endpoint" };
        const result = buildContentMarkers(PROPOSED, [issue]);
        expect(result.markers).toEqual([]);
        expect(result.unlocated).toEqual([issue]);
    });

    it("lists an issue whose context is not a pointer at all", () => {
        const issue = { pointer: "MessageName", message: "Field removed" };
        const result = buildContentMarkers(PROPOSED, [issue]);
        expect(result.markers).toEqual([]);
        expect(result.unlocated).toEqual([issue]);
    });

    it("marks the whole document for an explicit root pointer", () => {
        const result = buildContentMarkers(PROPOSED, [{ pointer: "", message: "Type changed" }]);
        expect(result.unlocated).toEqual([]);
        expect(result.markers[0].startLineNumber).toBe(1);
        expect(result.markers[0].endLineNumber).toBe(PROPOSED.split("\n").length);
    });

    it("lists every issue when the text cannot be parsed", () => {
        const issue = { pointer: "/a", message: "x" };
        expect(buildContentMarkers("{ \"a\": ", [issue]).unlocated).toEqual([issue]);
    });
});
