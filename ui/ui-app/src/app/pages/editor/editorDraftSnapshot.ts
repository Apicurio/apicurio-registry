import { contentToString } from "@utils/content.utils.ts";

const EDITOR_DRAFT_SNAPSHOT_KEY_PREFIX = "editor.snapshot";

export type EditorDraftSnapshot = {
    groupId: string;
    draftId: string;
    version: string;
    contentType: string;
    content: string;
    savedOn: number;
};

type CreateEditorDraftSnapshotProps = Omit<EditorDraftSnapshot, "savedOn">;

export function createEditorDraftSnapshot(props: CreateEditorDraftSnapshotProps): EditorDraftSnapshot {
    return {
        ...props,
        savedOn: Date.now()
    };
}

export function createEditorDraftSnapshotKey(groupId: string, draftId: string, version: string, contentType: string): string {
    return [
        EDITOR_DRAFT_SNAPSHOT_KEY_PREFIX,
        encodeURIComponent(groupId),
        encodeURIComponent(draftId),
        encodeURIComponent(version),
        encodeURIComponent(contentType)
    ].join(".");
}

export function serializeEditorDraftContent(content: unknown): string {
    if (content === undefined || content === null) {
        return "";
    }
    return contentToString(content);
}
