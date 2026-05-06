import { RefObject, useEffect, useRef, useState } from "react";
import { useLocalStorageService } from "@services/useLocalStorageService.ts";
import { useLoggerService } from "@services/useLoggerService.ts";
import {
    createEditorDraftSnapshot,
    createEditorDraftSnapshotKey,
    EditorDraftSnapshot,
    serializeEditorDraftContent
} from "./editorDraftSnapshot.ts";

type SnapshotContext = {
    content: unknown;
    isDirty: boolean;
    isDraftLoaded: boolean;
    isDraftContentLoaded: boolean;
    isDraftRecoveryModalOpen: boolean;
    groupId: string;
    draftId: string;
    version: string;
    contentType: string;
    snapshotKey: string;
};

export type SnapshotPersistenceResult = "saved" | "cleared" | "skipped" | "quota_exceeded";

export type UseEditorDraftRecoveryProps = {
    groupId: string;
    draftId: string;
    version: string;
    contentType: string;
    currentContent: unknown;
    originalContent: unknown;
    isDirty: boolean;
    isDraftLoaded: boolean;
    isDraftContentLoaded: boolean;
    isAuthRedirectInProgressRef: RefObject<boolean>;
    applyEditorContent: (content: string) => void;
};

export type UseEditorDraftRecovery = {
    snapshotKey: string;
    iframeEditorKey: number;
    draftRecoverySnapshot: EditorDraftSnapshot | undefined;
    isDraftRecoveryModalOpen: boolean;
    persistDraftRecoverySnapshot: () => SnapshotPersistenceResult;
    onRestoreDraftRecovery: () => void;
    onDiscardDraftRecovery: () => void;
    onDraftSaved: () => void;
};

export const useEditorDraftRecovery = ({
    groupId,
    draftId,
    version,
    contentType,
    currentContent,
    originalContent,
    isDirty,
    isDraftLoaded,
    isDraftContentLoaded,
    isAuthRedirectInProgressRef,
    applyEditorContent
}: UseEditorDraftRecoveryProps): UseEditorDraftRecovery => {
    const localStorage = useLocalStorageService();
    const logger = useLoggerService();

    const [isDraftRecoveryModalOpen, setDraftRecoveryModalOpen] = useState(false);
    const [draftRecoverySnapshot, setDraftRecoverySnapshot] = useState<EditorDraftSnapshot | undefined>();
    const [iframeEditorKey, setIframeEditorKey] = useState(0);

    const checkedSnapshotKeyRef = useRef<string | undefined>(undefined);
    const recoveryDecisionPendingRef = useRef(false);

    const snapshotKey = createEditorDraftSnapshotKey(groupId, draftId, version, contentType);
    const snapshotContextRef = useRef<SnapshotContext>({
        content: currentContent,
        isDirty,
        isDraftLoaded,
        isDraftContentLoaded,
        isDraftRecoveryModalOpen,
        groupId,
        draftId,
        version,
        contentType,
        snapshotKey
    });

    // This ref is consumed only by asynchronous callbacks, so it can be refreshed directly during render.
    snapshotContextRef.current = {
        content: currentContent,
        isDirty,
        isDraftLoaded,
        isDraftContentLoaded,
        isDraftRecoveryModalOpen,
        groupId,
        draftId,
        version,
        contentType,
        snapshotKey
    };

    const clearDraftRecoverySnapshot = (): void => {
        localStorage.clearSnapshot(snapshotKey);
    };

    const resetDraftRecoveryState = (): void => {
        recoveryDecisionPendingRef.current = false;
        setDraftRecoverySnapshot(undefined);
        setDraftRecoveryModalOpen(false);
    };

    const persistDraftRecoverySnapshot = (): SnapshotPersistenceResult => {
        const snapshotContext = snapshotContextRef.current;

        // Do not offer recovery while the page is still loading or while the recovery dialog is already active.
        if (!snapshotContext.isDraftLoaded || !snapshotContext.isDraftContentLoaded || snapshotContext.isDraftRecoveryModalOpen) {
            return "skipped";
        }

        if (!snapshotContext.isDirty) {
            localStorage.clearSnapshot(snapshotContext.snapshotKey);
            return "cleared";
        }

        const snapshot = createEditorDraftSnapshot({
            groupId: snapshotContext.groupId,
            draftId: snapshotContext.draftId,
            version: snapshotContext.version,
            contentType: snapshotContext.contentType,
            content: serializeEditorDraftContent(snapshotContext.content)
        });

        if (!localStorage.storeSnapshot(snapshotContext.snapshotKey, snapshot)) {
            logger.warn("Unable to persist draft recovery snapshot because browser storage is full.");
            return "quota_exceeded";
        }

        return "saved";
    };

    useEffect(() => {
        setDraftRecoverySnapshot(undefined);
        setDraftRecoveryModalOpen(false);
        checkedSnapshotKeyRef.current = undefined;
        recoveryDecisionPendingRef.current = false;
        setIframeEditorKey(0);
    }, [draftId, groupId, version]);

    useEffect(() => {
        const handleBeforeUnload = (event: BeforeUnloadEvent): void => {
            // Skip the browser prompt for the intentional auth redirect started from the re-auth modal.
            if (isAuthRedirectInProgressRef.current) {
                return;
            }

            persistDraftRecoverySnapshot();
            event.preventDefault();
        };

        if (isDirty) {
            window.addEventListener("beforeunload", handleBeforeUnload);
            return () => {
                window.removeEventListener("beforeunload", handleBeforeUnload);
            };
        }
    }, [isDirty, isAuthRedirectInProgressRef]);

    useEffect(() => {
        // Recovery is offered only after both metadata and server content are loaded for the current draft snapshot key.
        if (!isDraftLoaded || !isDraftContentLoaded) {
            return;
        }
        // Avoid reopening the same recovery modal after local state updates for the same draft/version/content type.
        if (checkedSnapshotKeyRef.current === snapshotKey) {
            return;
        }

        checkedSnapshotKeyRef.current = snapshotKey;

        const storedSnapshot = localStorage.loadSnapshot<EditorDraftSnapshot>(snapshotKey);
        if (!storedSnapshot) {
            recoveryDecisionPendingRef.current = false;
            return;
        }

        const serverContent = serializeEditorDraftContent(originalContent);
        if (storedSnapshot.content === serverContent) {
            recoveryDecisionPendingRef.current = false;
            clearDraftRecoverySnapshot();
            return;
        }

        recoveryDecisionPendingRef.current = true;
        setDraftRecoverySnapshot(storedSnapshot);
        setDraftRecoveryModalOpen(true);
    }, [isDraftContentLoaded, isDraftLoaded, localStorage, originalContent, snapshotKey]);

    useEffect(() => {
        // Once the draft is back in sync with the server and no recovery choice is pending, the local snapshot is stale.
        if (isDraftLoaded && isDraftContentLoaded && !isDraftRecoveryModalOpen && !recoveryDecisionPendingRef.current && !isDirty) {
            localStorage.clearSnapshot(snapshotKey);
        }
    }, [isDirty, isDraftContentLoaded, isDraftLoaded, isDraftRecoveryModalOpen, localStorage, snapshotKey]);

    const onRestoreDraftRecovery = (): void => {
        if (!draftRecoverySnapshot) {
            return;
        }

        applyEditorContent(draftRecoverySnapshot.content);
        // The iframe-based editors only consume their initial content during bootstrap,
        // so restoring local changes must remount them to rehydrate their internal state.
        setIframeEditorKey(previous => previous + 1);
        resetDraftRecoveryState();
    };

    const onDiscardDraftRecovery = (): void => {
        clearDraftRecoverySnapshot();
        resetDraftRecoveryState();
    };

    const onDraftSaved = (): void => {
        clearDraftRecoverySnapshot();
        resetDraftRecoveryState();
    };

    return {
        snapshotKey,
        iframeEditorKey,
        draftRecoverySnapshot,
        isDraftRecoveryModalOpen,
        persistDraftRecoverySnapshot,
        onRestoreDraftRecovery,
        onDiscardDraftRecovery,
        onDraftSaved
    };
};
