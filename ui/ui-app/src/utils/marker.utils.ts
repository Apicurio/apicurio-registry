import { locatePointer, PointerLocation } from "@utils/pointer.utils.ts";

/**
 * A problem to show against a piece of content: a JSON Pointer into that content and a message.
 */
export type ContentIssue = {
    pointer: string;
    message: string;
};

/**
 * Editor-agnostic marker data.  The fields match Monaco's IMarkerData, minus the severity, which
 * the editor component fills in from its own Monaco instance.
 */
export type ContentMarker = {
    startLineNumber: number;
    startColumn: number;
    endLineNumber: number;
    endColumn: number;
    message: string;
};

export type ContentMarkers = {
    markers: ContentMarker[];
    // Issues whose pointer does not resolve below the document root.  They are shown in the list
    // only, since highlighting the whole document would say nothing.
    unlocated: ContentIssue[];
};

/**
 * Turns issues into markers on the given text, which must be the text as displayed.  An issue that
 * only partially resolves is placed on the deepest part that does, with the unresolved part of the
 * pointer added to its message.
 */
export function buildContentMarkers(text: string, issues: ContentIssue[]): ContentMarkers {
    const result: ContentMarkers = { markers: [], unlocated: [] };
    issues.forEach(issue => {
        const location: PointerLocation | undefined = locatePointer(text, issue.pointer);
        if (!location || location.resolvedPointer === "" && issue.pointer.replace(/^#/, "") !== "") {
            result.unlocated.push(issue);
            return;
        }
        result.markers.push({
            startLineNumber: location.range.startLine,
            startColumn: location.range.startColumn,
            endLineNumber: location.range.endLine,
            endColumn: location.range.endColumn,
            message: location.exact ? issue.message : `${issue.message} (at ${issue.pointer})`
        });
    });
    return result;
}
